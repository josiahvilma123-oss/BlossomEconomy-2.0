package com.blossomsmp.economy.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Colour codes and money formatting helpers. */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final String[] SUFFIXES = {"", "K", "M", "B", "T"};
    private static final double MAX_AMOUNT = 1_000_000_000_000_000D; // 1 quadrillion

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(ChatColor.COLOR_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private Text() {
    }

    /** Turns "&d&lHello &#FF69B4world" into a coloured chat component. */
    public static Component component(String input) {
        return LEGACY.deserialize(color(input));
    }

    /** Translates &-codes and &#RRGGBB hex colours. */
    public static String color(String input) {
        if (input == null) {
            return "";
        }
        Matcher matcher = HEX.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder().append(ChatColor.COLOR_CHAR).append('x');
            for (char c : matcher.group(1).toCharArray()) {
                replacement.append(ChatColor.COLOR_CHAR).append(c);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(out);
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    /** Full money format, e.g. $1,234.5 */
    public static synchronized String money(double amount) {
        return (amount < 0 ? "-$" : "$") + MONEY.format(Math.abs(amount));
    }

    /** Short money format, e.g. $1.2K, $3.5M */
    public static String shortMoney(double amount) {
        double abs = Math.abs(amount);
        int index = 0;
        while (abs >= 1000 && index < SUFFIXES.length - 1) {
            abs /= 1000;
            index++;
        }
        if (index == 0) {
            return money(amount);
        }
        String number = String.format(Locale.US, "%.1f", abs);
        if (number.endsWith(".0")) {
            number = number.substring(0, number.length() - 2);
        }
        return (amount < 0 ? "-$" : "$") + number + SUFFIXES[index];
    }

    /**
     * Parses amounts like "100", "2.5k", "1m", "$1,000".
     * Returns null if invalid or not positive.
     */
    public static Double parseAmount(String input) {
        if (input == null) {
            return null;
        }
        String s = input.toLowerCase(Locale.ROOT).replace(",", "").replace("$", "").trim();
        if (s.isEmpty()) {
            return null;
        }
        double multiplier = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k') {
            multiplier = 1_000;
        } else if (last == 'm') {
            multiplier = 1_000_000;
        } else if (last == 'b') {
            multiplier = 1_000_000_000;
        }
        if (multiplier != 1) {
            s = s.substring(0, s.length() - 1);
        }
        // Only allow plain digits and one dot (blocks "NaN", "Infinity", hex, exponents)
        if (!s.matches("\\d+(\\.\\d+)?|\\.\\d+")) {
            return null;
        }
        try {
            double value = Math.round(Double.parseDouble(s) * multiplier * 100.0) / 100.0;
            if (value <= 0 || value > MAX_AMOUNT) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 93784000 -> "1d 2h" */
    public static String duration(long millis) {
        long minutes = Math.max(0, millis) / 60000L;
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return Math.max(1, mins) + "m";
    }

    /** DIAMOND_SWORD -> Diamond Sword */
    public static String itemName(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
