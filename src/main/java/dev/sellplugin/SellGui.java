package dev.sellplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SellGui implements Listener {

    public static final int HISTORY_SLOT = 45; // bottom-left
    public static final int STATS_SLOT = 49;   // bottom-middle
    public static final int SELL_SLOT = 53;    // bottom-right

    private final SellPlugin plugin;

    public SellGui(SellPlugin plugin) {
        this.plugin = plugin;
    }

    public static class Holder implements InventoryHolder {
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static boolean isButton(int slot) {
        return slot == HISTORY_SLOT || slot == STATS_SLOT || slot == SELL_SLOT;
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(Text.sc("place items here to sell")));
        holder.inv = inv;
        setSideButtons(inv, player);
        refresh(inv);
        player.openInventory(inv);
    }

    // ---- buttons ------------------------------------------------------------------------

    private ItemStack button(Material mat, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        plugin.prices().markNoWorth(meta);
        it.setItemMeta(meta);
        return it;
    }

    /** Bottom-left (history) and bottom-middle (items sold). */
    private void setSideButtons(Inventory inv, Player player) {
        SellHistory.PlayerData d = plugin.history().get(player.getUniqueId());

        List<Component> historyLore = new ArrayList<>();
        historyLore.add(Text.plain(Text.sc("see everything you sold"), NamedTextColor.GRAY));
        historyLore.add(Component.empty());
        historyLore.add(Text.plain(Text.sc("total sales: "), NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(d.totalSales), Text.BLUE)));
        historyLore.add(Text.plain(Text.sc("total earned: "), NamedTextColor.GRAY)
                .append(Component.text("$", NamedTextColor.GREEN))
                .append(Component.text(Text.money(Text.round(d.totalEarned)), Text.BLUE)));
        historyLore.add(Component.empty());
        historyLore.add(Text.plain(Text.sc("click to open"), NamedTextColor.DARK_GRAY));
        inv.setItem(HISTORY_SLOT, button(Material.CLOCK, Text.plain(Text.sc("sell history"), Text.BLUE), historyLore));

        int types = d.amount.size();
        List<Component> statsLore = List.of(
                Text.plain(Text.sc("how many of each item you sold"), NamedTextColor.GRAY),
                Component.empty(),
                Text.plain(Text.sc("different items sold: "), NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(types), Text.BLUE)),
                Component.empty(),
                Text.plain(Text.sc("click to open"), NamedTextColor.DARK_GRAY));
        inv.setItem(STATS_SLOT, button(Material.BOOK, Text.plain(Text.sc("items sold"), Text.BLUE), statsLore));
    }

    /** Recalculates the payout and rewrites the green pane's lore. */
    private void refresh(Inventory inv) {
        PriceManager prices = plugin.prices();
        double total = 0;
        int unsellable = 0;
        for (int i = 0; i < 54; i++) {
            if (isButton(i)) continue;
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            PriceManager.Valuation v = prices.value(item);
            if (v.sellable()) total += v.total();
            else unsellable++;
        }
        inv.setItem(SELL_SLOT, sellPane(Text.round(total), unsellable));
    }

    private ItemStack sellPane(double total, int unsellable) {
        List<Component> lore = new ArrayList<>();
        lore.add(Text.plain(Text.sc("you will get: "), NamedTextColor.GRAY)
                .append(Component.text("+$", NamedTextColor.GREEN))
                .append(Component.text(Text.money(total), Text.BLUE)));
        if (unsellable > 0) {
            lore.add(Text.plain(Text.sc(unsellable + " unsellable item" + (unsellable == 1 ? "" : "s") + " will stay"),
                    NamedTextColor.RED));
        }
        lore.add(Component.empty());
        lore.add(Text.plain(Text.sc("click to sell all items"), NamedTextColor.DARK_GRAY));
        return button(Material.LIME_STAINED_GLASS_PANE, Text.plain(Text.sc("sell"), NamedTextColor.GREEN), lore);
    }

    /** Inventory events fire before the change is applied, so refresh one tick later. */
    private void refreshNextTick(Holder holder) {
        Bukkit.getScheduler().runTask(plugin, () -> refresh(holder.getInventory()));
    }

    // ---- events -------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        refreshNextTick(holder);

        // stop "double click to gather" from pulling a button onto the cursor
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR
                && e.getCursor() != null && isButtonMaterial(e.getCursor().getType())) {
            e.setCancelled(true);
            return;
        }

        int raw = e.getRawSlot();
        if (!isButton(raw)) return;
        e.setCancelled(true);

        switch (raw) {
            case SELL_SLOT -> sell(player, holder);
            case HISTORY_SLOT -> Bukkit.getScheduler().runTask(plugin,
                    () -> plugin.historyGui().open(player, SellHistoryGui.Mode.HISTORY));
            case STATS_SLOT -> Bukkit.getScheduler().runTask(plugin,
                    () -> plugin.historyGui().open(player, SellHistoryGui.Mode.STATS));
            default -> {}
        }
    }

    private static boolean isButtonMaterial(Material m) {
        return m == Material.LIME_STAINED_GLASS_PANE || m == Material.CLOCK || m == Material.BOOK;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        for (int slot : e.getRawSlots()) {
            if (isButton(slot)) {
                e.setCancelled(true);
                return;
            }
        }
        refreshNextTick(holder);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        if (!(e.getPlayer() instanceof Player player)) return;
        returnItems(player, e.getInventory());
    }

    /** Gives back whatever is still in the GUI (everything except the buttons). */
    public void returnItems(Player player, Inventory inv) {
        for (int i = 0; i < 54; i++) {
            if (isButton(i)) continue;
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            inv.setItem(i, null);
            player.getInventory().addItem(item).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private void sell(Player player, Holder holder) {
        Economy eco = plugin.economy();
        if (eco == null) {
            player.sendMessage(Text.error("no economy plugin found"));
            return;
        }

        Inventory inv = holder.getInventory();
        PriceManager prices = plugin.prices();

        double total = 0;
        Set<Component> problems = new LinkedHashSet<>();
        Map<Material, Integer> amounts = new EnumMap<>(Material.class);
        Map<Material, Double> earned = new EnumMap<>(Material.class);

        for (int i = 0; i < 54; i++) {
            if (isButton(i)) continue;
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            PriceManager.Valuation v = prices.value(item);
            if (v.sellable()) {
                total += v.total();
                prices.breakdown(item, amounts, earned);
                inv.setItem(i, null);
            } else if (v.blocked() && v.total() > 0) {
                problems.add(Text.blockedShulker(Text.prettyName(item.getType())));
            } else {
                problems.add(Text.unsellable(Text.prettyName(item.getType())));
            }
        }

        for (Component c : problems) Text.send(player, c);

        total = Text.round(total);
        if (total > 0) {
            eco.depositPlayer(player, total);
            plugin.history().record(player.getUniqueId(), total, amounts, earned);
            Text.send(player, Text.sold(total));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            setSideButtons(inv, player); // refresh the totals on the history/stats buttons
        }
        refresh(inv);
    }
}
