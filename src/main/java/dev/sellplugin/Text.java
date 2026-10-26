package dev.sellplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Text helpers: small caps, colours, money formatting and all player-facing messages. */
public final class Text {

    public static final TextColor BLUE = TextColor.fromHexString("#00a2ff");

    private static final String NORMAL = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private Text() {}

    /** Converts letters to small caps (digits and symbols are untouched). */
    public static String sc(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            int i = NORMAL.indexOf(Character.toLowerCase(c));
            sb.append(i >= 0 ? SMALL.charAt(i) : c);
        }
        return sb.toString();
    }

    public static String money(double v) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)).format(v);
    }

    public static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** OAK_PLANKS -> Oak Planks */
    public static String prettyName(Material m) {
        String[] parts = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    /** +$<amount> */
    public static Component sold(double amount) {
        return Component.text("+$", NamedTextColor.GREEN)
                .append(Component.text(money(amount), BLUE));
    }

    /** <item> is unsellable */
    public static Component unsellable(String itemName) {
        return Component.text(sc(itemName), BLUE)
                .append(Component.text(sc(" is "), NamedTextColor.WHITE))
                .append(Component.text(sc("unsellable"), NamedTextColor.RED));
    }

    /** <item> contains unsellable items */
    public static Component blockedShulker(String itemName) {
        return Component.text(sc(itemName), BLUE)
                .append(Component.text(sc(" contains unsellable items"), NamedTextColor.RED));
    }

    /** <item> is worth $<price> */
    public static Component worthMessage(String itemName, double price) {
        return Component.text(sc(itemName), BLUE)
                .append(Component.text(sc(" is worth "), NamedTextColor.WHITE))
                .append(Component.text("$", NamedTextColor.GREEN))
                .append(Component.text(money(price), NamedTextColor.WHITE));
    }

    public static Component error(String msg) {
        return Component.text(sc(msg), NamedTextColor.RED);
    }

    /** Worth: $<price>  (tooltip line) */
    public static Component worthLore(double price) {
        return noItalic(Component.text("Worth: ", BLUE)
                .append(Component.text("$", NamedTextColor.GREEN))
                .append(Component.text(money(price), NamedTextColor.WHITE)));
    }

    public static Component plain(String s, TextColor color) {
        return noItalic(Component.text(s, color));
    }

    /** Sends to chat and action bar. */
    public static void send(Player p, Component c) {
        p.sendMessage(c);
        p.sendActionBar(c);
    }
}
