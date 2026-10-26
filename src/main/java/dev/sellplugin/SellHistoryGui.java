package dev.sellplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.*;

/** Two paged GUIs: the list of past sales, and the "items sold" statistics. */
public class SellHistoryGui implements Listener {

    public enum Mode { HISTORY, STATS }

    private static final int PER_PAGE = 45;
    private static final int PREV = 45, SUMMARY = 47, BACK = 49, NEXT = 53;

    public static class Holder implements InventoryHolder {
        private Inventory inv;
        private Mode mode;
        private UUID owner;
        private int page = 0;
        private int pages = 1;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final SellPlugin plugin;

    public SellHistoryGui(SellPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Mode mode) {
        Holder h = new Holder();
        h.mode = mode;
        h.owner = player.getUniqueId();
        String title = mode == Mode.HISTORY ? "sell history" : "items sold";
        h.inv = Bukkit.createInventory(h, 54, Component.text(Text.sc(title)));
        render(h);
        player.openInventory(h.inv);
    }

    private void render(Holder h) {
        SellHistory.PlayerData d = plugin.history().get(h.owner);
        List<ItemStack> icons = new ArrayList<>();

        if (h.mode == Mode.HISTORY) {
            List<SellHistory.Entry> list = new ArrayList<>(d.history);
            Collections.reverse(list); // newest first
            for (int r = 0; r < list.size(); r++) {
                icons.add(historyIcon(list.get(r), Math.max(1, d.totalSales - r)));
            }
        } else {
            List<Map.Entry<Material, Integer>> list = new ArrayList<>(d.amount.entrySet());
            list.sort((a, b) -> {
                int c = Integer.compare(b.getValue(), a.getValue());
                return c != 0 ? c : Text.prettyName(a.getKey()).compareTo(Text.prettyName(b.getKey()));
            });
            for (Map.Entry<Material, Integer> en : list) {
                if (!en.getKey().isItem() || en.getKey().isAir()) continue;
                icons.add(statsIcon(en.getKey(), en.getValue(), d.earned.getOrDefault(en.getKey(), 0.0)));
            }
        }

        h.pages = Math.max(1, (int) Math.ceil(icons.size() / (double) PER_PAGE));
        h.page = Math.max(0, Math.min(h.page, h.pages - 1));

        Inventory inv = h.inv;
        inv.clear();

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = h.page * PER_PAGE + i;
            if (idx >= icons.size()) break;
            inv.setItem(i, icons.get(idx));
        }
        if (icons.isEmpty()) {
            String msg = h.mode == Mode.HISTORY ? "no sales yet" : "nothing sold yet";
            inv.setItem(22, button(Material.BARRIER, Text.sc(msg), NamedTextColor.RED, null));
        }

        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, null);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        if (h.page > 0) inv.setItem(PREV, button(Material.ARROW, Text.sc("previous page"), Text.BLUE, null));
        if (h.page < h.pages - 1) inv.setItem(NEXT, button(Material.ARROW, Text.sc("next page"), Text.BLUE, null));

        int totalItems = d.amount.values().stream().mapToInt(Integer::intValue).sum();
        List<Component> sum = List.of(
                Text.plain(Text.sc("total sales: "), NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(d.totalSales), Text.BLUE)),
                Text.plain(Text.sc("total earned: "), NamedTextColor.GRAY)
                        .append(Component.text("$", NamedTextColor.GREEN))
                        .append(Component.text(Text.money(Text.round(d.totalEarned)), Text.BLUE)),
                Text.plain(Text.sc("items sold: "), NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(totalItems), Text.BLUE)),
                Component.empty(),
                Text.plain(Text.sc("page " + (h.page + 1) + "/" + h.pages), NamedTextColor.DARK_GRAY));
        inv.setItem(SUMMARY, button(Material.GOLD_INGOT, Text.sc("summary"), Text.BLUE, sum));

        inv.setItem(BACK, button(Material.OAK_DOOR, Text.sc("back to sell"), NamedTextColor.GREEN, null));
    }

    private ItemStack historyIcon(SellHistory.Entry e, int number) {
        // icon = the item sold the most in that sale
        Material icon = Material.PAPER;
        int best = -1;
        for (Map.Entry<Material, Integer> en : e.items().entrySet()) {
            if (en.getValue() > best && en.getKey().isItem() && !en.getKey().isAir()) {
                best = en.getValue();
                icon = en.getKey();
            }
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Text.plain(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(e.time())), NamedTextColor.GRAY));
        lore.add(Text.plain(Text.sc("earned: "), NamedTextColor.GRAY)
                .append(Component.text("+$", NamedTextColor.GREEN))
                .append(Component.text(Text.money(e.total()), Text.BLUE)));
        lore.add(Component.empty());

        List<Map.Entry<Material, Integer>> items = new ArrayList<>(e.items().entrySet());
        items.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int shown = 0;
        for (Map.Entry<Material, Integer> en : items) {
            if (shown++ >= 8) break;
            lore.add(Text.plain(en.getValue() + "x ", Text.BLUE)
                    .append(Component.text(Text.prettyName(en.getKey()), NamedTextColor.WHITE)));
        }
        if (items.size() > 8) {
            lore.add(Text.plain(Text.sc("and " + (items.size() - 8) + " more..."), NamedTextColor.DARK_GRAY));
        }

        return button(icon, Text.sc("sale") + " #" + number, Text.BLUE, lore);
    }

    private ItemStack statsIcon(Material m, int amount, double earned) {
        List<Component> lore = List.of(
                Text.plain(Text.sc("sold: "), NamedTextColor.GRAY)
                        .append(Component.text(amount + "x", Text.BLUE)),
                Text.plain(Text.sc("earned: "), NamedTextColor.GRAY)
                        .append(Component.text("$", NamedTextColor.GREEN))
                        .append(Component.text(Text.money(Text.round(earned)), Text.BLUE)));
        return button(m, Text.prettyName(m), NamedTextColor.WHITE, lore);
    }

    private ItemStack button(Material mat, String name, net.kyori.adventure.text.format.TextColor color, List<Component> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.plain(name, color));
        if (lore != null) meta.lore(lore);
        plugin.prices().markNoWorth(meta);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        switch (e.getRawSlot()) {
            case PREV -> {
                if (h.page > 0) {
                    h.page--;
                    render(h);
                }
            }
            case NEXT -> {
                if (h.page < h.pages - 1) {
                    h.page++;
                    render(h);
                }
            }
            case BACK -> Bukkit.getScheduler().runTask(plugin, () -> plugin.sellGui().open(player));
            default -> {}
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
