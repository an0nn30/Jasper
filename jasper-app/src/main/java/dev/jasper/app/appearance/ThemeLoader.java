package dev.jasper.app.appearance;

import com.formdev.flatlaf.json.Json;
import com.formdev.flatlaf.json.ParseException;
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Color;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    /**
     * A resolved theme: FlatLaf's JSON input, every explicit colour keyed as the theme names it, the problems met, the
     * resolved {@code ui} and {@code icons} entries (named colours replaced) for conversion, and the theme's named colour
     * table ({@code colors}, parents' entries included) with every name resolved to a {@code #rrggbb} or
     * {@code #rrggbbaa} literal, in table order.
     */
    record Resolved(String name, boolean dark, String json, Map<String, Color> colors, List<String> warnings,
                    Map<String, Object> ui, Map<String, Object> icons, Map<String, String> namedColors) {
        Resolved {
            colors = Map.copyOf(colors); warnings = List.copyOf(warnings);
            ui = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(ui));
            icons = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(icons));
            namedColors = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(namedColors));
        }
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
        var warnings = new ArrayList<String>();
        Raw theme = resolve(file, new ArrayList<>(), warnings);
        Map<String, String> colors = resolveColors(file, theme.colors);
        var ui = new LinkedHashMap<String, Object>();
        theme.ui.forEach((key, value) -> {
            // A Swing UI delegate is never a legitimate theme setting for Jasper; FlatLaf and Swing would load it.
            if (key.endsWith("UI")) return;
            Object kept = withoutUnusableValues(file, key, value, warnings);
            if (kept != null) ui.put(key, named(kept, colors));
        });
        var icons = new LinkedHashMap<String, Object>();
        theme.icons.forEach((key, value) -> icons.put(key, named(value, colors)));
        var json = new LinkedHashMap<String, Object>();
        json.put("name", theme.name);
        json.put("dark", theme.dark);
        // FlatLaf 3.7 fails on a theme without an author.
        json.put("author", theme.author);
        json.put("colors", new LinkedHashMap<String, Object>(colors));
        json.put("ui", ui);
        if (!icons.isEmpty()) json.put("icons", icons);
        return new Resolved(theme.name, theme.dark, write(json), explicitColors(ui), warnings, ui, icons, colors);
    }

    private Raw resolve(String file, List<String> chain, List<String> warnings) {
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
        Object parent = json.get("parentTheme");
        if (parent == null) return own;
        if (!(parent instanceof String parentName)) throw new ThemeException(file + ": \"parentTheme\" must be a string");
        String parentFile = filesByName.get(parentName);
        if (parentFile == null) {
            // A parent Jasper does not ship (another plugin's theme) becomes the classic base.
            String base = own.dark ? "Darcula" : "IntelliJ";
            parentFile = filesByName.get(base);
            if (parentFile == null) throw new ThemeException(file + ": unknown parentTheme \"" + parentName + "\"");
            warnings.add(file + ": parentTheme \"" + parentName + "\" is not a theme Jasper has; using " + base + " instead");
        }
        return own.over(resolve(parentFile, chain, warnings));
    }

    /**
     * Resolves every entry of the merged colour table to a literal; names may refer to other names. Each
     * name is walked at most once: a name already resolved by an earlier chain short-circuits the walk, so
     * the whole table resolves in time linear in its size, not cubic in the chain length.
     */
    private static Map<String, String> resolveColors(String file, Map<String, Object> table) {
        var resolved = new HashMap<String, String>();
        for (String name : table.keySet()) resolveColor(file, table, name, resolved);
        var ordered = new LinkedHashMap<String, String>();
        for (String name : table.keySet()) ordered.put(name, resolved.get(name));
        return ordered;
    }

    /** Walks {@code name}'s chain, memoizing every name on the walked path into {@code resolved}. */
    private static void resolveColor(String file, Map<String, Object> table, String name, Map<String, String> resolved) {
        if (resolved.containsKey(name)) return;
        var path = new LinkedHashSet<String>();
        String current = name;
        while (true) {
            if (!path.add(current)) throw new ThemeException(file + ": colours form a cycle: " + String.join(" -> ", path) + " -> " + current);
            String cached = resolved.get(current);
            if (cached != null) { path.forEach(link -> resolved.put(link, cached)); return; }
            Object value = table.get(current);
            if (!(value instanceof String text)) throw new ThemeException(file + ": colour \"" + current + "\" is not a string");
            if (HEX.matcher(text).matches()) {
                String literal = normalized(text);
                path.forEach(link -> resolved.put(link, literal));
                return;
            }
            if (!table.containsKey(text)) throw new ThemeException(file + ": colour \"" + current + "\" refers to unknown colour \"" + text + "\"");
            current = text;
        }
    }

    /** A string that names a Java class in full, for example {@code com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI}. */
    private static final Pattern CLASS_NAME = Pattern.compile("(?:[a-z_$][\\w$]*\\.)+[A-Z][\\w$]*");

    /**
     * The value without blank or {@code null} entries, and without class names Jasper cannot load (FlatLaf
     * or Swing would try to instantiate them) other than FlatLaf's own icon and painter classes; a per-OS
     * value keeps its other branches. Null when nothing is left. A dropped blank or {@code null} entry adds
     * a warning naming {@code key}; a dropped class name does not, matching the existing IntelliJ-class rule.
     */
    private static Object withoutUnusableValues(String file, String key, Object value, List<String> warnings) {
        if (value instanceof String text) return usable(file, key, text, warnings) ? value : null;
        if (!(value instanceof Map<?, ?> byOs) || !perOs(byOs)) return value;
        var kept = new LinkedHashMap<String, Object>();
        byOs.forEach((os, branch) -> {
            if (!(branch instanceof String text) || usable(file, key, text, warnings)) kept.put(os.toString(), branch);
        });
        return kept.isEmpty() ? null : kept;
    }

    /** Whether one scalar value survives: not blank or {@code null}, and not an unloadable class name. */
    private static boolean usable(String file, String key, String text, List<String> warnings) {
        String stripped = text.strip();
        if (stripped.isEmpty() || stripped.equalsIgnoreCase("null")) {
            warnings.add(file + ": \"" + key + "\" has an empty value; ignoring it");
            return false;
        }
        // FlatLaf trims a value before reading it, so a class name with surrounding spaces is still one.
        return !CLASS_NAME.matcher(stripped).matches() || stripped.startsWith("com.formdev.flatlaf.");
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

    /** FlatLaf's JSON input for already-resolved {@code ui} and {@code icons} entries (no named colours left). */
    static String json(String name, boolean dark, String author, Map<String, Object> ui, Map<String, Object> icons) {
        var json = new LinkedHashMap<String, Object>();
        json.put("name", name);
        json.put("dark", dark);
        json.put("author", author);
        json.put("colors", new LinkedHashMap<String, Object>());
        json.put("ui", ui);
        if (!icons.isEmpty()) json.put("icons", icons);
        return write(json);
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
