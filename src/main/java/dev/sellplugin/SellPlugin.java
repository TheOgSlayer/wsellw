package dev.sellplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class SellPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private PriceManager prices;
    private SellGui sellGui;
    private WorthGui worthGui;
    private SellHistory history;
    private SellHistoryGui historyGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        prices = new PriceManager(this);
        prices.load();

        history = new SellHistory(this);
        historyGui = new SellHistoryGui(this);
        sellGui = new SellGui(this);
        worthGui = new WorthGui(this);
        getServer().getPluginManager().registerEvents(history, this);
        getServer().getPluginManager().registerEvents(historyGui, this);
        getServer().getPluginManager().registerEvents(sellGui, this);
        getServer().getPluginManager().registerEvents(worthGui, this);

        Objects.requireNonNull(getCommand("sell")).setExecutor(this);
        Objects.requireNonNull(getCommand("worth")).setExecutor(this);
        Objects.requireNonNull(getCommand("sellhistory")).setExecutor(this);
        Objects.requireNonNull(getCommand("worth")).setTabCompleter(this);

        if (economy() == null) {
            getLogger().warning("No Vault economy provider found yet. Install an economy plugin (e.g. EssentialsX).");
        }

        if (getConfig().getBoolean("show-worth-lore", true)) {
            if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
                WorthLoreListener.register(this);
                getLogger().info("ProtocolLib found: item tooltips will show their worth.");
            } else {
                getLogger().warning("ProtocolLib not found: item tooltips will NOT show worth. Install ProtocolLib to enable.");
            }
        }
    }

    @Override
    public void onDisable() {
        // give items back if someone has the sell GUI open while the server stops
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof SellGui.Holder) {
                p.closeInventory();
            }
        }
        if (history != null) history.saveAll();
    }

    public PriceManager prices() {
        return prices;
    }

    public SellHistory history() {
        return history;
    }

    public SellGui sellGui() {
        return sellGui;
    }

    public SellHistoryGui historyGui() {
        return historyGui;
    }

    public Economy economy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    // ------------------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);

        if (name.equals("sell") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("sellplugin.reload")) {
                sender.sendMessage(Text.error("you don't have permission"));
                return true;
            }
            reloadConfig();
            prices.load();
            sender.sendMessage(Text.error("prices reloaded").color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (name.equals("sell")) {
            sellGui.open(player);
            return true;
        }

        if (name.equals("sellhistory")) {
            boolean stats = args.length > 0 && args[0].equalsIgnoreCase("stats");
            historyGui.open(player, stats ? SellHistoryGui.Mode.STATS : SellHistoryGui.Mode.HISTORY);
            return true;
        }

        // /worth
        if (args.length == 0) {
            worthGui.open(player, prices.pricedMaterials(), null);
            return true;
        }

        String query = String.join(" ", args).trim();
        String key = query.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        Material exact = Material.getMaterial(key);

        if (exact != null && exact.isItem() && !exact.isAir()) {
            if (prices.hasPrice(exact)) {
                Text.send(player, Text.worthMessage(Text.prettyName(exact), prices.base(exact)));
            } else {
                Text.send(player, Text.unsellable(Text.prettyName(exact)));
            }
            return true;
        }

        // not an exact item -> search
        String[] terms = query.toLowerCase(Locale.ROOT).split("\\s+");
        List<Material> matches = prices.pricedMaterials().stream()
                .filter(m -> {
                    String n = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
                    for (String t : terms) if (!n.contains(t)) return false;
                    return true;
                })
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            Text.send(player, Text.error("no priced items found for " + query));
            return true;
        }
        worthGui.open(player, matches, query);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) return List.of();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        Set<String> words = new TreeSet<>();
        for (Material m : prices.pricedMaterials()) {
            for (String w : m.name().toLowerCase(Locale.ROOT).split("_")) {
                if (w.startsWith(last)) words.add(w);
            }
        }
        return new ArrayList<>(words);
    }
}
