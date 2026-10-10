package dev.jasper.ui.themeio;

import dev.jasper.ui.theme.GlassLevels;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.Seed;
import dev.jasper.ui.theme.SyntaxRole;
import dev.jasper.ui.theme.SyntaxStyle;
import dev.jasper.ui.theme.ThemeDocument;
import dev.jasper.ui.theme.ThemeFileException;
import dev.jasper.ui.theme.Token;
import dev.jasper.ui.theme.WorkspaceGlass;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

/** Reads a Jasper theme file. Every problem is a {@link ThemeFileException} naming the file and line. */
public final class ThemeToml {
    private static final Set<String> TOP = Set.of("name", "dark", "extends", "author", "license", "source", "commit",
        "ui_complete", "colors", "tokens", "semantic", "syntax", "ui", "icons", "workspace");
    private static final List<String> METADATA = List.of("author", "license", "source", "commit");
    private static final Set<String> STYLE_FIELDS = Set.of("color", "bold", "italic");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?");
    /** A full Java class name: color-only themes must not load vendor or application classes. */
    private static final Pattern CLASS_NAME = Pattern.compile("(?:[a-z_$][\\w$]*\\.)+[A-Z][\\w$]*");

    private ThemeToml() {}

    /** {@code #RRGGBB} or {@code #RRGGBBAA}, already checked against {@link #COLOR}. */
    static Color color(String text) {
        String hex = text.substring(1);
        int rgb = Integer.parseInt(hex.substring(0, 6), 16);
        int alpha = hex.length() == 8 ? Integer.parseInt(hex.substring(6), 16) : 255;
        return new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, alpha);
    }

    /** Parses native data, reporting invalid values with their source line. */
    public static ThemeDocument parse(String text, String origin) {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) {
            var error = toml.errors().getFirst();
            throw new ThemeFileException(origin, error.position().line(), error.getMessage());
        }
        var file = new Reader(toml, origin);
        for (String key : toml.keySet()) if (!TOP.contains(key) && !(toml.get(List.of(key)) instanceof String))
            throw file.error(List.of(key), "unknown key \"" + key + "\"");
        String name = file.string(List.of("name")).orElseThrow(() -> new ThemeFileException(origin, 0, "a theme needs a \"name\""));
        if (name.isBlank()) throw file.error(List.of("name"), "\"name\" must not be blank");
        Optional<String> parent = file.string(List.of("extends"));
        if (parent.isPresent() && parent.get().isBlank()) throw file.error(List.of("extends"), "\"extends\" must not be blank");
        var metadata = new LinkedHashMap<String, String>();
        for (String key : toml.keySet())
            if (METADATA.contains(key) || !TOP.contains(key))
                file.string(List.of(key)).ifPresent(value -> metadata.put(key, value));
        var seeds = new EnumMap<Seed, Color>(Seed.class);
        var ansi = new HashMap<Integer, Color>();
        TomlTable colors = file.table(List.of("colors"));
        if (colors != null) for (String key : colors.keySet()) {
            List<String> path = List.of("colors", key);
            if (key.equals("normal") || key.equals("bright")) {
                int offset = key.equals("normal") ? 0 : 8;
                for (String colour : file.table(path).keySet()) {
                    int index = ThemeDocument.ANSI_NAMES.indexOf(colour);
                    List<String> at = List.of("colors", key, colour);
                    if (index < 0) throw file.error(at, "unknown ANSI colour \"" + colour + "\"");
                    ansi.put(offset + index, file.color(at));
                }
            } else {
                Seed seed = Seed.of(key).orElseThrow(() -> file.error(path, "unknown colour \"" + key + "\" in [colors]"));
                seeds.put(seed, file.color(path));
            }
        }
        var tokens = new EnumMap<Token, Color>(Token.class);
        TomlTable tokenTable = file.table(List.of("tokens"));
        if (tokenTable != null) for (String key : tokenTable.keySet()) {
            List<String> path = List.of("tokens", key);
            tokens.put(Token.of(key).orElseThrow(() -> file.error(path, "unknown token \"" + key + "\"")), file.color(path));
        }
        var semantic = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        TomlTable semanticTable = file.table(List.of("semantic"));
        if (semanticTable != null) for (String key : semanticTable.keySet()) {
            List<String> path = List.of("semantic", key);
            semantic.put(SemanticColor.of(key).orElseThrow(() -> file.error(path, "unknown semantic color \"" + key + "\"")), file.color(path));
        }
        var syntax = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        TomlTable syntaxTable = file.table(List.of("syntax"));
        if (syntaxTable != null) for (String key : syntaxTable.keySet()) {
            List<String> path = List.of("syntax", key);
            SyntaxRole role = SyntaxRole.of(key).orElseThrow(() -> file.error(path, "unknown syntax role \"" + key + "\""));
            syntax.put(role, file.style(path));
        }
        var ui = new LinkedHashMap<String, Object>();
        TomlTable uiTable = file.table(List.of("ui"));
        if (uiTable != null) file.flatten(List.of("ui"), "", uiTable, ui);
        return new ThemeDocument(origin, name.strip(), file.bool(List.of("dark")), parent.map(String::strip),
            parent.isPresent() ? file.line(List.of("extends")) : 0, file.bool(List.of("ui_complete")), metadata, seeds,
            ansi, tokens, syntax, ui, file.icons(), file.workspace(), semantic);
    }

    /** A nonfatal authored-key warning with its source line and actionable canonical-role guidance. */
    public record Warning(String key, int line, String message) {}

    /** Inspect explicit [ui] keys only; callers parse/validate the document before requesting warnings. */
    public static List<Warning> warnings(String text, String origin) {
        var toml = Toml.parse(text);
        if (toml.hasErrors()) return List.of();
        var warnings = new ArrayList<Warning>();
        TomlTable ui = toml.getTable("ui");
        if (ui != null) warnings(ui, "", origin, warnings);
        return List.copyOf(warnings);
    }

    private static void warnings(TomlTable table, String prefix, String origin, List<Warning> result) {
        for (String part : table.keySet()) {
            String key = prefix + part;
            var warning = dev.jasper.ui.theme.UiColorKeys.nativeOverrideWarning(key);
            if (warning.isPresent()) {
                var position = table.inputPositionOf(List.of(part));
                result.add(new Warning(key, position == null ? 0 : position.line(), origin + ": " + warning.get()));
            } else if (table.get(List.of(part)) instanceof TomlTable child) warnings(child, key + ".", origin, result);
        }
    }

    /** Typed reads from one parsed file, each failure naming the key's line. */
    private record Reader(TomlParseResult toml, String origin) {
        int line(List<String> path) {
            TomlPosition position = toml.inputPositionOf(path);
            return position == null ? 0 : position.line();
        }

        ThemeFileException error(List<String> path, String message) { return new ThemeFileException(origin, line(path), message); }

        private static String label(List<String> path) { return path.getLast(); }

        TomlTable table(List<String> path) {
            Object value = toml.get(path);
            if (value == null) return null;
            if (!(value instanceof TomlTable table)) throw error(path, "\"" + label(path) + "\" must be a table");
            return table;
        }

        Optional<String> string(List<String> path) {
            Object value = toml.get(path);
            if (value == null) return Optional.empty();
            if (!(value instanceof String text)) throw error(path, "\"" + label(path) + "\" must be a string");
            return Optional.of(text);
        }

        Optional<Boolean> bool(List<String> path) {
            Object value = toml.get(path);
            if (value == null) return Optional.empty();
            if (!(value instanceof Boolean flag)) throw error(path, "\"" + label(path) + "\" must be true or false");
            return Optional.of(flag);
        }

        Optional<Double> number(List<String> path, double min, double max) {
            Object value = toml.get(path);
            if (value == null) return Optional.empty();
            double number;
            if (value instanceof Double fraction) number = fraction;
            else if (value instanceof Long whole) number = whole;
            else throw error(path, "\"" + label(path) + "\" must be a number");
            if (!(number >= min && number <= max))
                throw error(path, "\"" + label(path) + "\" must be from " + plain(min) + " to " + plain(max));
            return Optional.of(number);
        }

        private static String plain(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }

        WorkspaceGlass workspace() {
            TomlTable table = table(List.of("workspace"));
            if (table == null) return WorkspaceGlass.NONE;
            for (String key : table.keySet())
                if (!key.equals("opacity") && !key.equals("blur"))
                    throw error(List.of("workspace", key), "unknown key \"" + key + "\" in [workspace]");
            return new WorkspaceGlass(number(List.of("workspace", "opacity"), GlassLevels.MIN_OPACITY, 1),
                bool(List.of("workspace", "blur")));
        }

        Color color(List<String> path) {
            Object value = toml.get(path);
            if (!(value instanceof String text) || !COLOR.matcher(text).matches())
                throw error(path, "\"" + label(path) + "\" must be a colour like #1e1e2e or #1e1e2e80");
            return ThemeToml.color(text);
        }

        SyntaxStyle style(List<String> path) {
            Object value = toml.get(path);
            if (value instanceof String) return new SyntaxStyle(color(path), false, false);
            if (!(value instanceof TomlTable inline)) throw error(path, "\"" + label(path) + "\" must be a colour or { color, bold, italic }");
            for (String field : inline.keySet())
                if (!STYLE_FIELDS.contains(field)) throw error(path, "unknown field \"" + field + "\" in \"" + label(path) + "\"");
            if (!inline.contains(List.of("color"))) throw error(path, "\"" + label(path) + "\" needs a \"color\"");
            return new SyntaxStyle(color(with(path, "color")), bool(with(path, "bold")).orElse(false),
                bool(with(path, "italic")).orElse(false));
        }

        /** Nested tables become dotted keys; a table whose keys all start with {@code os.} stays one per-OS value. */
        void flatten(List<String> path, String prefix, TomlTable table, Map<String, Object> out) {
            for (String key : table.keySet()) {
                List<String> at = with(path, key);
                String name = prefix.isEmpty() ? key : prefix + "." + key;
                Object value = table.get(List.of(key));
                if (value instanceof TomlTable nested && !nested.isEmpty()
                        && nested.keySet().stream().allMatch(inner -> inner.startsWith("os."))) {
                    var perOs = new LinkedHashMap<String, Object>();
                    for (String os : nested.keySet()) perOs.put(os, scalar(with(at, os), name, nested.get(List.of(os))));
                    put(at, name, perOs, out);
                } else if (value instanceof TomlTable nested) {
                    flatten(at, name, nested, out);
                } else {
                    put(at, name, scalar(at, name, value), out);
                }
            }
        }

        Map<String, Object> icons() {
            TomlTable table = table(List.of("icons"));
            if (table == null) return Map.of();
            var result = new LinkedHashMap<String, Object>();
            int[] count = {0};
            for (String key : table.keySet())
                result.put(key, iconValue(List.of("icons", key), table.get(List.of(key)), 1, count));
            return result;
        }

        private Object iconValue(List<String> at, Object value, int depth, int[] count) {
            if (depth > 32) throw error(at, "icon data exceeds maximum nesting of 32");
            if (++count[0] > 100_000) throw error(at, "icon data exceeds 100000 values");
            if (value instanceof TomlTable table) {
                var result = new LinkedHashMap<String, Object>();
                for (String key : table.keySet())
                    result.put(key, iconValue(with(at, key), table.get(List.of(key)), depth + 1, count));
                return result;
            }
            if (value instanceof org.tomlj.TomlArray array) {
                var result = new ArrayList<Object>();
                for (int i = 0; i < array.size(); i++) result.add(iconValue(at, array.get(i), depth + 1, count));
                return result;
            }
            return scalar(at, label(at), value);
        }

        private Object scalar(List<String> at, String name, Object value) {
            if (value instanceof String text) {
                String stripped = text.strip();
                if (CLASS_NAME.matcher(stripped).matches())
                    throw error(at, "\"" + name + "\" names a Java class; themes cannot load classes");
                return text;
            }
            if (value instanceof Double number && !Double.isFinite(number))
                throw error(at, "\"" + name + "\" must be finite");
            if (value instanceof Long || value instanceof Double || value instanceof Boolean) return value;
            throw error(at, "\"" + name + "\" must be a string, number or boolean");
        }

        private void put(List<String> at, String name, Object value, Map<String, Object> out) {
            if (name.endsWith("UI")) throw error(at, "\"" + name + "\" is a Swing UI delegate; themes cannot set it");
            out.remove(name);
            out.put(name, value);
        }

        private static List<String> with(List<String> path, String key) {
            var result = new ArrayList<>(path);
            result.add(key);
            return List.copyOf(result);
        }
    }
}
