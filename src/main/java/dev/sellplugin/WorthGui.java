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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class WorthGui implements Listener {

    private static final int PER_PAGE = 45;
    private static final int PREV = 45, SORT = 49, NEXT = 53;

    public enum Sort {
        ALPHABETICAL("Alphabetical"),
        HIGH_LOW("Highest to lowest"),
        LOW_HIGH("Lowest to highest");

        final String label;

        Sort(String label) {
            this.label = label;
        }

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static class Holder implements InventoryHolder {
        private Inventory inv;
        private List<Material> items;
        private Sort sort = Sort.ALPHABETICAL;
        private int page = 0;
        private int pages = 1;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final SellPlugin plugin;

    public WorthGui(SellPlugin plugin) {
        this.plugin = plugin;
    }

    /** @param query null for the full list, otherwise the search text (shown in the title) */
    public void open(Player player, Collection<Material> materials, String query) {
        Holder h = new Holder();
        h.items = new ArrayList<>(materials);
        String title = "worth" + (query == null || query.isBlank() ? "" : ": " + query);
        if (title.length() > 40) title = title.substring(0, 40);
        h.inv = Bukkit.createInventory(h, 54, Component.text(Text.sc(title)));
        render(h);
        player.openInventory(h.inv);
    }

    private void render(Holder h) {
        PriceManager prices = plugin.prices();
        Comparator<Material> byName = Comparator.comparing(Text::prettyName);
        List<Material> sorted = new ArrayList<>(h.items);
        switch (h.sort) {
            case ALPHABETICAL -> sorted.sort(byName);
            case HIGH_LOW -> sorted.sort(Comparator.comparingDouble((Material m) -> prices.base(m)).reversed().thenComparing(byName));
            case LOW_HIGH -> sorted.sort(Comparator.comparingDouble((Material m) -> prices.base(m)).thenComparing(byName));
        }

        h.pages = Math.max(1, (int) Math.ceil(sorted.size() / (double) PER_PAGE));
        h.page = Math.max(0, Math.min(h.page, h.pages - 1));

        Inventory inv = h.inv;
        inv.clear();

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = h.page * PER_PAGE + i;
            if (idx >= sorted.size()) break;
            Material m = sorted.get(idx);
            ItemStack it = new ItemStack(m);
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.lore(List.of(Text.worthLore(prices.base(m))));
                it.setItemMeta(meta);
            }
            inv.setItem(i, it);
        }

        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        if (h.page > 0) inv.setItem(PREV, named(Material.ARROW, Text.sc("previous page"), null));
        if (h.page < h.pages - 1) inv.setItem(NEXT, named(Material.ARROW, Text.sc("next page"), null));

        List<Component> lore = new ArrayList<>();
        for (Sort s : Sort.values()) {
            boolean cur = s == h.sort;
            lore.add(Text.plain((cur ? "▶ " : "  ") + Text.sc(s.label), cur ? Text.BLUE : NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(Text.plain(Text.sc("page " + (h.page + 1) + "/" + h.pages), NamedTextColor.DARK_GRAY));
        lore.add(Text.plain(Text.sc("click to change sorting"), NamedTextColor.DARK_GRAY));
        ItemStack sortBtn = named(Material.HOPPER, Text.sc("sort"), lore);
        inv.setItem(SORT, sortBtn);
    }

    private ItemStack named(Material mat, String name, List<Component> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.plain(name, name.isBlank() ? NamedTextColor.GRAY : Text.BLUE));
        if (lore != null) meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

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
            case SORT -> {
                h.sort = h.sort.next();
                h.page = 0;
                render(h);
            }
            default -> {}
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
