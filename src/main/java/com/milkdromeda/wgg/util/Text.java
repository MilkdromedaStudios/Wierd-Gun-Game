package com.milkdromeda.wgg.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** MiniMessage helpers. Everything player-facing goes through here. */
public final class Text {

    public static final String PREFIX = "<gradient:#ff8a3d:#ff4fd8><bold>WGG</bold></gradient> <dark_gray>|</dark_gray> ";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    /** Parse MiniMessage. Italics are stripped so item lore renders cleanly. */
    public static Component mm(String input) {
        return MM.deserialize(input).decoration(TextDecoration.ITALIC, false);
    }

    /** Parse with the plugin chat prefix in front. */
    public static Component msg(String input) {
        return mm(PREFIX + input);
    }


    /**
     * A little unicode meter, e.g. {@code ▉▉▉▉▉▉░░░░}.
     *
     * @param value current value
     * @param max   value that fills the bar
     * @param width number of segments
     */
    public static String bar(double value, double max, int width, String filledColor, String emptyColor) {
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, value / max)) * width);
        StringBuilder sb = new StringBuilder();
        sb.append(filledColor);
        sb.append("▉".repeat(filled));
        sb.append(emptyColor);
        sb.append("░".repeat(Math.max(0, width - filled)));
        return sb.toString();
    }

    /** Trim a double to one decimal place, dropping a trailing ".0". */
    public static String num(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format("%.1f", value);
    }
}
