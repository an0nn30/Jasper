package dev.jasper.app.appearance;

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Data-only FlatLaf adapter shared by native runtime themes and legacy conversion. */
public final class FlatLafThemeData {
    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})|#[0-9a-fA-F]{3}");
    private FlatLafThemeData() {}

    /**
     * Whether {@code defaults} define {@code key}, without building a lazily created value: a border or icon built here
     * reads the installed look and feel, which for never-installed defaults is another theme's or none (at start-up).
     * Keys outside the table still go through {@code get}, which FlatLaf also answers from its getters and Metal.
     */
    static boolean defines(javax.swing.UIDefaults defaults, String key) {
        return defaults.containsKey(key) || defaults.get(key) != null;
    }

    /**
     * The colour of every explicit key, applied in order. A {@code *.suffix} wildcard recolours the keys
     * recorded so far with that suffix, the way IntelliJ applies it to the defaults present at that point.
     */
    public static Map<String, Color> explicitColors(Map<String, Object> ui) {
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
    public static Color color(Object value) {
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

    /** FlatLaf's JSON input for already-resolved {@code ui} and {@code icons} entries (no named colours left). */
    public static String json(String name, boolean dark, String author, Map<String, Object> ui, Map<String, Object> icons) {
        var json = new LinkedHashMap<String, Object>();
        json.put("name", name);
        json.put("dark", dark);
        json.put("author", author);
        json.put("colors", new LinkedHashMap<String, Object>());
        json.put("ui", ui);
        if (!icons.isEmpty()) json.put("icons", icons);
        return write(json);
    }

    /** Encodes a resolved legacy theme object for the FlatLaf adapter. */
    public static String write(Map<String, Object> value) {
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
