package dev.sellplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/** Loads prices.yml and values items (durability + shulker contents). */
public class PriceManager {

    public record Valuation(double total, boolean sellable, boolean blocked) {}

    private final SellPlugin plugin;
    private final NamespacedKey noWorthKey;
    private volatile Map<Material, Double> prices = Map.of();

    public PriceManager(SellPlugin plugin) {
        this.plugin = plugin;
        this.noWorthKey = new NamespacedKey(plugin, "noworth");
    }

    /** Items marked with this never get a "Worth: $X" tooltip line (used for GUI buttons/icons). */
    public void markNoWorth(ItemMeta meta) {
        meta.getPersistentDataContainer().set(noWorthKey, PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * Splits a stack into per-material amounts and earnings (shulker box + each item inside it).
     * Used for the sell history / statistics.
     */
    public void breakdown(ItemStack item, Map<Material, Integer> amounts, Map<Material, Double> earned) {
        Map<Material, Double> p = prices;
        Double base = p.get(item.getType());
        if (base != null) {
            amounts.merge(item.getType(), item.getAmount(), Integer::sum);
            earned.merge(item.getType(), base * durabilityFactor(item) * item.getAmount(), Double::sum);
        }
        if (isShulker(item.getType()) && item.getItemMeta() instanceof BlockStateMeta bsm
                && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack c : box.getInventory().getContents()) {
                if (c == null || c.getType().isAir()) continue;
                Double cp = p.get(c.getType());
                if (cp == null) continue;
                int n = c.getAmount() * item.getAmount();
                amounts.merge(c.getType(), n, Integer::sum);
                earned.merge(c.getType(), cp * durabilityFactor(c) * n, Double::sum);
            }
        }
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "prices.yml");
        if (!file.exists()) plugin.saveResource("prices.yml", false);

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        Map<Material, Double> map = new EnumMap<>(Material.class);
        ConfigurationSection section = yml.getConfigurationSection("prices");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m == null || m.isAir() || !m.isItem()) {
                    plugin.getLogger().warning("prices.yml: unknown item '" + key + "' (skipped)");
                    continue;
                }
                double price = section.getDouble(key, -1);
                if (price <= 0) {
                    plugin.getLogger().warning("prices.yml: invalid price for '" + key + "' (skipped)");
                    continue;
                }
                map.put(m, price);
            }
        }
        this.prices = Collections.unmodifiableMap(map);
        plugin.getLogger().info("Loaded " + map.size() + " prices.");
    }

    public boolean hasPrice(Material m) {
        return prices.containsKey(m);
    }

    /** Price at 100% durability, 0 if not priced. */
    public double base(Material m) {
        return prices.getOrDefault(m, 0.0);
    }

    public Set<Material> pricedMaterials() {
        return prices.keySet();
    }

    private static boolean isShulker(Material m) {
        return m.name().endsWith("SHULKER_BOX");
    }

    /** 1.0 = full durability, 0.5 = half durability. */
    public static double durabilityFactor(ItemStack item) {
        int max = item.getType().getMaxDurability();
        if (max <= 0) return 1.0;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable d) {
            return Math.max(0.0, (max - d.getDamage()) / (double) max);
        }
        return 1.0;
    }

    /** Value of the whole stack (amount, durability and shulker contents included). */
    public Valuation value(ItemStack item) {
        if (item == null || item.getType().isAir()) return new Valuation(0, false, false);

        Map<Material, Double> p = prices;
        double total = 0;
        boolean blocked = false;

        Double base = p.get(item.getType());
        if (base != null) total += base * durabilityFactor(item) * item.getAmount();

        if (isShulker(item.getType()) && item.getItemMeta() instanceof BlockStateMeta bsm
                && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack c : box.getInventory().getContents()) {
                if (c == null || c.getType().isAir()) continue;
                Double cp = p.get(c.getType());
                if (cp == null) {
                    blocked = true;
                    continue;
                }
                total += cp * durabilityFactor(c) * c.getAmount() * item.getAmount();
            }
        }

        total = Text.round(total);
        boolean allowBlocked = plugin.getConfig().getBoolean("sell-shulkers-with-unsellable-items", false);
        boolean sellable = total > 0 && (!blocked || allowBlocked);
        return new Valuation(total, sellable, blocked);
    }

    // ---- tooltip lore -------------------------------------------------------------------

    private static boolean isWorthLine(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c).startsWith("Worth: $");
    }

    /** Returns a copy of the item with a "Worth: $X" line added (or the same item if not priced). */
    public ItemStack withWorthLore(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;
        Valuation v = value(item);
        if (v.total() <= 0) return item;

        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return item;
        if (meta.getPersistentDataContainer().has(noWorthKey, PersistentDataType.BYTE)) return item;

        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        if (!lore.isEmpty() && isWorthLine(lore.get(lore.size() - 1))) return item; // already there

        lore.add(Text.worthLore(Text.round(v.total() / item.getAmount())));
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    /** Removes our tooltip line (used when creative clients send items back to the server). */
    public static ItemStack stripWorthLore(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.lore() == null) return item;
        List<Component> lore = new ArrayList<>(meta.lore());
        if (lore.isEmpty() || !isWorthLine(lore.get(lore.size() - 1))) return item;
        lore.remove(lore.size() - 1);
        ItemStack copy = item.clone();
        ItemMeta m = copy.getItemMeta();
        m.lore(lore.isEmpty() ? null : lore);
        copy.setItemMeta(m);
        return copy;
    }
}
