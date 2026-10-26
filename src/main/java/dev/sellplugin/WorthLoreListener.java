package dev.sellplugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds "Worth: $X" to item tooltips by editing packets (client side only), so the real items
 * never get modified lore. Needs ProtocolLib.
 */
public final class WorthLoreListener {

    private WorthLoreListener() {}

    public static void register(SellPlugin plugin) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        final SellPlugin sell = plugin; // PacketAdapter has its own 'plugin' field (typed Plugin), so use a different name

        pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGH,
                PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    PacketContainer packet = event.getPacket().deepClone();
                    PriceManager prices = sell.prices();

                    if (packet.getType() == PacketType.Play.Server.SET_SLOT) {
                        ItemStack item = packet.getItemModifier().read(0);
                        packet.getItemModifier().write(0, prices.withWorthLore(item));
                    } else {
                        List<ItemStack> items = packet.getItemListModifier().read(0);
                        List<ItemStack> out = new ArrayList<>(items.size());
                        for (ItemStack it : items) out.add(prices.withWorthLore(it));
                        packet.getItemListModifier().write(0, out);

                        if (packet.getItemModifier().size() > 0) { // cursor item
                            ItemStack carried = packet.getItemModifier().read(0);
                            packet.getItemModifier().write(0, prices.withWorthLore(carried));
                        }
                    }
                    event.setPacket(packet);
                } catch (Exception ex) {
                    sell.getLogger().fine("Worth lore skipped: " + ex.getMessage());
                }
            }
        });

        // Creative clients send items back with the tooltip line; strip it so it never becomes real lore.
        pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Client.SET_CREATIVE_SLOT) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                try {
                    PacketContainer packet = event.getPacket();
                    ItemStack item = packet.getItemModifier().read(0);
                    packet.getItemModifier().write(0, PriceManager.stripWorthLore(item));
                } catch (Exception ex) {
                    sell.getLogger().fine("Creative strip skipped: " + ex.getMessage());
                }
            }
        });
    }
}
