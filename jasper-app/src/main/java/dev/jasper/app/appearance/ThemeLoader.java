package dev.jasper.app.appearance;

import com.formdev.flatlaf.json.Json;
import com.formdev.flatlaf.json.ParseException;
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Color;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Resolves an IntelliJ {@code .theme.json} and its {@code parentTheme} chain into one flat theme for
 * FlatLaf. Parent entries come first and a child's entries replace them in the child's order, as in
 * IntelliJ, so FlatLaf's in-order wildcard handling gives IntelliJ's precedence. Named colours are
 * resolved; the explicit colours are also returned because FlatLaf skips IntelliJ-only namespaces.
 */
final class ThemeLoader {
    /** A resolved theme: FlatLaf's JSON input and every explicit colour, keyed as the theme names it. */
    record Resolved(String name, boolean dark, String json, Map<String, Color> colors) {
        Resolved { colors = Map.copyOf(colors); }
    }

    /** A theme that cannot be resolved; the message names the theme file and the offending key. */
    static final class ThemeException extends IllegalArgumentException {
        ThemeException(String message) { super(message); }
        ThemeException(String message, Throwable cause) { super(message, cause); }
    }

    private record Raw(String name, boolean dark, String author,
                       LinkedHashMap<String, Object> colors, LinkedHashMap<String, Object> ui, LinkedHashMap<String, Object> icons) {
        Raw over(Raw parent) {
            return new Raw(name, dark, author, layered(parent.colors, colors), layered(parent.ui, ui), layered(parent.icons, icons));
        }

        private static LinkedHashMap<String, Object> layered(Map<String, Object> parent, Map<String, Object> child) {
            var result = new LinkedHashMap<>(parent);
            child.forEach((key, value) -> { result.remove(key); result.put(key, value); });
            return result;
        }
    }

    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})|#[0-9a-fA-F]{3}");

    private final Function<String, String> read;
    private final Map<String, String> filesByName;

    /**
     * @param read returns a theme file's JSON text, or null when the file does not exist
     * @param filesByName the theme file for each theme {@code name} a {@code parentTheme} may name
     */
    ThemeLoader(Function<String, String> read, Map<String, String> filesByName) {
        this.read = Objects.requireNonNull(read);
        this.filesByName = Map.copyOf(filesByName);
    }

    Resolved load(String file) {
        Raw theme = resolve(file, new ArrayList<>());
        Map<String, String> colors = resolveColors(file, theme.colors);
        var ui = new LinkedHashMap<String, Object>();
        theme.ui.forEach((key, value) -> { if (!intellijClass(value)) ui.put(key, named(value, colors)); });
        var json = new LinkedHashMap<String, Object>();
        json.put("name", theme.name);
        json.put("dark", theme.dark);
        // FlatLaf 3.7 fails on a theme without an author.
        json.put("author", theme.author);
        json.put("colors", new LinkedHashMap<String, Object>(colors));
        json.put("ui", ui);
        if (!theme.icons.isEmpty()) json.put("icons", theme.icons);
        return new Resolved(theme.name, theme.dark, write(json), explicitColors(ui));
    }

    private Raw resolve(String file, List<String> chain) {
        if (chain.contains(file)) throw new ThemeException("Theme parents form a cycle: " + String.join(" -> ", chain) + " -> " + file);
        chain.add(file);
        String text = read.apply(file);
        if (text == null) throw new ThemeException("Theme file not found: " + file);
        Map<String, Object> json;
        try {
            if (!(Json.parse(new StringReader(text)) instanceof Map<?, ?> map)) throw new ThemeException(file + ": a theme must be a JSON object");
            json = strings(map);
        } catch (IOException | ParseException failure) {
            throw new ThemeException(file + ": " + failure.getMessage(), failure);
        }
        // FlatLaf's parser returns every scalar, booleans included, as a String.
        var own = new Raw(json.get("name") instanceof String name ? name : file, "true".equals(String.valueOf(json.get("dark"))),
            json.get("author") instanceof String author ? author : "",
            flatten(object(json, "colors", file)), flatten(object(json, "ui", file)), new LinkedHashMap<>(object(json, "icons", file)));
        if (!(json.get("parentTheme") instanceof String parent)) return own;
        String parentFile = filesByName.get(parent);
        if (parentFile == null) throw new ThemeException(file + ": unknown parentTheme \"" + parent + "\"");
        return own.over(resolve(parentFile, chain));
    }

    /** Resolves every entry of the merged colour table to a literal; names may refer to other names. */
    private static Map<String, String> resolveColors(String file, Map<String, Object> table) {
        var resolved = new LinkedHashMap<String, String>();
        for (String name : table.keySet()) resolved.put(name, resolveColor(file, table, name, new ArrayList<>()));
        return resolved;
    }

    private static String resolveColor(String file, Map<String, Object> table, String name, List<String> path) {
        if (path.contains(name)) throw new ThemeException(file + ": colours form a cycle: " + String.join(" -> ", path) + " -> " + name);
        path.add(name);
        Object value = table.get(name);
        if (!(value instanceof String text)) throw new ThemeException(file + ": colour \"" + name + "\" is not a string");
        if (HEX.matcher(text).matches()) return normalized(text);
        if (!table.containsKey(text)) throw new ThemeException(file + ": colour \"" + name + "\" refers to unknown colour \"" + text + "\"");
        return resolveColor(file, table, text, path);
    }

    /** IntelliJ's own UI, border and painter classes, which Jasper cannot load; FlatLaf would try to. */
    private static boolean intellijClass(Object value) {
        return value instanceof String text ? text.startsWith("com.intellij.")
            : value instanceof Map<?, ?> perOs && perOs.values().stream().anyMatch(ThemeLoader::intellijClass);
    }

    /** A value naming a colour becomes that colour; other values, including IntelliJ palette names, pass through. */
    private static Object named(Object value, Map<String, String> colors) {
        if (value instanceof String text && colors.containsKey(text)) return colors.get(text);
        if (value instanceof Map<?, ?> perOs) {
            var result = new LinkedHashMap<String, Object>();
            perOs.forEach((key, inner) -> result.put(key.toString(), named(inner, colors)));
            return result;
        }
        return value;
    }

    /**
     * The colour of every explicit key, applied in order. A {@code *.suffix} wildcard recolours the keys
     * recorded so far with that suffix, the way IntelliJ applies it to the defaults present at that point.
     */
    private static Map<String, Color> explicitColors(Map<String, Object> ui) {
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

    /** Nested objects become dotted keys in document order; per-OS objects ({@code os.*} keys) stay values. */
    private static LinkedHashMap<String, Object> flatten(Map<String, Object> source) {
        var result = new LinkedHashMap<String, Object>();
        flatten("", source, result);
        return result;
    }

    private static void flatten(String prefix, Map<String, Object> source, Map<String, Object> result) {
        source.forEach((key, value) -> {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof Map<?, ?> nested && !perOs(nested)) flatten(path, strings(nested), result);
            else { result.remove(path); result.put(path, value); }
        });
    }

    private static boolean perOs(Map<?, ?> map) {
        return !map.isEmpty() && map.keySet().stream().allMatch(key -> key.toString().startsWith("os."));
    }

    private static Map<String, Object> object(Map<String, Object> json, String key, String file) {
        Object value = json.get(key);
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw new ThemeException(file + ": \"" + key + "\" must be an object");
        return strings(map);
    }

    private static Map<String, Object> strings(Map<?, ?> map) {
        var result = new LinkedHashMap<String, Object>();
        map.forEach((key, value) -> result.put(key.toString(), value));
        return result;
    }

    private static String write(Object value) {
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
