package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Data-only FlatLaf adapter: a generated theme's UI keys as FlatLaf's JSON input and as the colours it sets. */
final class FlatLafThemeData {
    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})|#[0-9a-fA-F]{3}");
    private FlatLafThemeData() {}

    /**
     * The colour of every explicit key, applied in order. A {@code *.suffix} wildcard recolours the keys
     * recorded so far with that suffix, the way IntelliJ applies it to the defaults present at that point.
     */
    static Map<String, Color> explicitColors(Map<String, Object> ui) {
        var colors = new LinkedHashMap<String, Color>();
        ui.forEach((key, raw) -> {
            if (key.startsWith("@")) return;
            Color color = color(forThisOs(raw));
            if (color == null) return;
            if (key.startsWith("*.")) {
                String suffix = key.substring(1);
                colors.replaceAll((existing, previous) -> existing.endsWith(suffix) ? color : previous);
            } else colors.put(key, color);
        });
        return colors;
    }

    private static Object forThisOs(Object value) {
        if (!(value instanceof Map<?, ?> perOs)) return value;
        String os = SystemInfo.isMacOS ? "os.mac" : SystemInfo.isWindows ? "os.windows" : SystemInfo.isLinux ? "os.linux" : "";
        return perOs.containsKey(os) ? perOs.get(os) : perOs.get("os.default");
    }

    /** A {@code #RGB}, {@code #RRGGBB} or {@code #RRGGBBAA} colour (the {@code #} optional for the long forms), or null. */
    static Color color(Object value) {
        if (!(value instanceof String text) || !HEX.matcher(text).matches()) return null;
        String hex = normalized(text).substring(1);
        long rgba = Long.parseLong(hex.length() == 6 ? hex + "ff" : hex, 16);
        return new Color((int) (rgba >> 24) & 0xff, (int) (rgba >> 16) & 0xff, (int) (rgba >> 8) & 0xff, (int) rgba & 0xff);
    }

    private static String normalized(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() == 3) hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        return "#" + hex.toLowerCase(Locale.ROOT);
    }

    /** FlatLaf's JSON input for already-resolved {@code ui} entries (no named colours left). */
    static String json(String name, boolean dark, String author, Map<String, Object> ui) {
        var json = new LinkedHashMap<String, Object>();
        json.put("name", name);
        json.put("dark", dark);
        json.put("author", author);
        json.put("colors", new LinkedHashMap<String, Object>());
        json.put("ui", ui);
        return write(json);
    }

    private static String write(Map<String, Object> value) {
        var out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                write(entry.getKey().toString(), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) { if (i > 0) out.append(','); write(list.get(i), out); }
            out.append(']');
        } else if (value instanceof String text) {
            out.append('"');
            for (char c : text.toCharArray()) {
                if (c == '"' || c == '\\') out.append('\\').append(c);
                else if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                else out.append(c);
            }
            out.append('"');
        } else out.append(value);
    }
}
