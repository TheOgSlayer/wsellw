package dev.sellplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Per-player sell history + item statistics, stored in plugins/SellPlugin/data/<uuid>.yml */
public class SellHistory implements Listener {

    public record Entry(long time, double total, Map<Material, Integer> items) {}

    public static class PlayerData {
        public final UUID id;
        public final List<Entry> history = new ArrayList<>();           // oldest -> newest
        public final Map<Material, Integer> amount = new EnumMap<>(Material.class);
        public final Map<Material, Double> earned = new EnumMap<>(Material.class);
        public double totalEarned;
        public int totalSales;

        PlayerData(UUID id) {
            this.id = id;
        }
    }

    private final SellPlugin plugin;
    private final File dir;
    private final Map<UUID, PlayerData> cache = new HashMap<>();
    private final Object io = new Object();

    public SellHistory(SellPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "data");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    public PlayerData get(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    private PlayerData load(UUID id) {
        PlayerData d = new PlayerData(id);
        File f = file(id);
        if (!f.exists()) return d;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        d.totalEarned = cfg.getDouble("total-earned");
        d.totalSales = cfg.getInt("total-sales");

        ConfigurationSection st = cfg.getConfigurationSection("stats");
        if (st != null) {
            for (String key : st.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m == null) continue;
                d.amount.put(m, st.getInt(key + ".amount"));
                d.earned.put(m, st.getDouble(key + ".earned"));
            }
        }

        ConfigurationSection h = cfg.getConfigurationSection("history");
        if (h != null) {
            for (String key : h.getKeys(false)) {
                ConfigurationSection s = h.getConfigurationSection(key);
                if (s == null) continue;
                Map<Material, Integer> items = new EnumMap<>(Material.class);
                ConfigurationSection is = s.getConfigurationSection("items");
                if (is != null) {
                    for (String mk : is.getKeys(false)) {
                        Material m = Material.matchMaterial(mk);
                        if (m != null) items.put(m, is.getInt(mk));
                    }
                }
                d.history.add(new Entry(s.getLong("time"), s.getDouble("total"), items));
            }
        }
        return d;
    }

    /** Records one completed sale. */
    public void record(UUID id, double total, Map<Material, Integer> amounts, Map<Material, Double> earned) {
        if (total <= 0 || amounts.isEmpty()) return;
        PlayerData d = get(id);

        d.history.add(new Entry(System.currentTimeMillis(), total, new EnumMap<>(amounts)));
        int max = Math.max(1, plugin.getConfig().getInt("history-max-entries", 500));
        while (d.history.size() > max) d.history.remove(0);

        d.totalSales++;
        d.totalEarned += total;
        for (Map.Entry<Material, Integer> en : amounts.entrySet()) {
            d.amount.merge(en.getKey(), en.getValue(), Integer::sum);
        }
        for (Map.Entry<Material, Double> en : earned.entrySet()) {
            d.earned.merge(en.getKey(), Text.round(en.getValue()), Double::sum);
        }
        save(d, true);
    }

    private void save(PlayerData d, boolean async) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("total-earned", d.totalEarned);
        cfg.set("total-sales", d.totalSales);
        for (Map.Entry<Material, Integer> en : d.amount.entrySet()) {
            String base = "stats." + en.getKey().name();
            cfg.set(base + ".amount", en.getValue());
            cfg.set(base + ".earned", d.earned.getOrDefault(en.getKey(), 0.0));
        }
        int i = 0;
        for (Entry e : d.history) {
            String base = "history." + (i++);
            cfg.set(base + ".time", e.time());
            cfg.set(base + ".total", e.total());
            for (Map.Entry<Material, Integer> it : e.items().entrySet()) {
                cfg.set(base + ".items." + it.getKey().name(), it.getValue());
            }
        }

        File f = file(d.id);
        Runnable write = () -> {
            synchronized (io) {
                try {
                    cfg.save(f);
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not save " + f.getName() + ": " + ex.getMessage());
                }
            }
        };
        if (async && plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        else write.run();
    }

    public void saveAll() {
        for (PlayerData d : cache.values()) save(d, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cache.remove(e.getPlayer().getUniqueId()); // already saved after every sale
    }
}
