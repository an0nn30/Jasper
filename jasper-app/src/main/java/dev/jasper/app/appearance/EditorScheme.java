package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

/**
 * Turns an IntelliJ editor colour scheme into a terminal palette: the console colours, then the
 * editor text colours IntelliJ's console falls back to. Keys a scheme leaves out come from its parent
 * scheme (IntelliJ's own Default or Darcula, vendored), and only then from Jasper's palette. Scheme
 * colours are used as the theme author chose them: no contrast floor applies.
 */
final class EditorScheme {
    /** A complete palette and the problems met building it. */
    record Result(Palette palette, List<String> warnings) {
        Result { warnings = List.copyOf(warnings); }
    }

    private record Attribute(String base, Map<String, String> values) {}
    private record Scheme(String name, String parent, Map<String, String> colors, Map<String, Attribute> attributes) {}

    private static final String PARENTS_FILE = "dev/jasper/app/themes/intellij/DefaultColorSchemesManager.xml";
    private static final List<String> ANSI = List.of(
        "CONSOLE_BLACK_OUTPUT", "CONSOLE_RED_OUTPUT", "CONSOLE_GREEN_OUTPUT", "CONSOLE_YELLOW_OUTPUT",
        "CONSOLE_BLUE_OUTPUT", "CONSOLE_MAGENTA_OUTPUT", "CONSOLE_CYAN_OUTPUT", "CONSOLE_GRAY_OUTPUT",
        "CONSOLE_DARKGRAY_OUTPUT", "CONSOLE_RED_BRIGHT_OUTPUT", "CONSOLE_GREEN_BRIGHT_OUTPUT", "CONSOLE_YELLOW_BRIGHT_OUTPUT",
        "CONSOLE_BLUE_BRIGHT_OUTPUT", "CONSOLE_MAGENTA_BRIGHT_OUTPUT", "CONSOLE_CYAN_BRIGHT_OUTPUT", "CONSOLE_WHITE_OUTPUT");

    /** IntelliJ's own schemes by name, read once. */
    private static final class Parents {
        static final Map<String, Scheme> SCHEMES = readParents();
    }

    private EditorScheme() {}

    /** The palette of the one scheme in {@code xml}; {@code label} names its file in warnings. */
    static Result read(byte[] xml, String label, boolean dark) {
        var warnings = new ArrayList<String>();
        List<Scheme> schemes;
        try { schemes = schemes(SafeXml.parse(xml)); }
        catch (IllegalArgumentException failure) {
            warnings.add(label + " is " + failure.getMessage() + "; the terminal uses Jasper's palette");
            return new Result(fallback(dark), warnings);
        }
        if (schemes.size() != 1) {
            warnings.add(label + ": expected one <scheme>, found " + schemes.size() + "; the terminal uses Jasper's palette");
            return new Result(fallback(dark), warnings);
        }
        return palette(schemes.getFirst(), label, dark, warnings);
    }

    /** The palette of a scheme IntelliJ ships, named by a theme's {@code editorScheme} instead of a path. */
    static Result named(String name, String label, boolean dark) {
        Scheme scheme = Parents.SCHEMES.get(name);
        if (scheme == null)
            return new Result(fallback(dark), List.of(label + ": editor scheme \"" + name + "\" is not one Jasper has; the terminal uses Jasper's palette"));
        return palette(scheme, label, dark, new ArrayList<>());
    }

    private static Result palette(Scheme leaf, String label, boolean dark, List<String> warnings) {
        var lookup = new Lookup(chain(leaf, label, warnings), label, warnings);
        Palette base = fallback(dark);
        Color background = first(lookup.color("CONSOLE_BACKGROUND_KEY"), lookup.attribute("TEXT", "BACKGROUND"), base.background());
        Color foreground = first(lookup.attribute("CONSOLE_NORMAL_OUTPUT", "FOREGROUND"), lookup.attribute("TEXT", "FOREGROUND"), base.foreground());
        Color cursor = first(lookup.color("CARET_COLOR"), base.cursor());
        Color selection = first(lookup.color("SELECTION_BACKGROUND"), base.selection());
        var ansi = new ArrayList<Color>();
        for (int i = 0; i < ANSI.size(); i++) ansi.add(first(lookup.attribute(ANSI.get(i), "FOREGROUND"), base.ansi().get(i)));
        return new Result(new Palette(foreground, background, cursor, selection, ansi), warnings);
    }

    /** The scheme and its parents. A missing {@code parent_scheme} means Default, as in IntelliJ. */
    private static List<Scheme> chain(Scheme leaf, String label, List<String> warnings) {
        var chain = new ArrayList<Scheme>();
        Scheme scheme = leaf;
        while (scheme != null && !chain.contains(scheme)) {
            chain.add(scheme);
            String parent = scheme.parent() != null ? scheme.parent() : "Default".equals(scheme.name()) ? null : "Default";
            if (parent == null) break;
            Scheme next = Parents.SCHEMES.get(parent);
            if (next == null) warnings.add(label + ": parent scheme \"" + parent + "\" is not one Jasper has; missing colours come from Jasper's palette");
            scheme = next;
        }
        return chain;
    }

    private static Color first(Color... colors) {
        for (Color color : colors) if (color != null) return color;
        throw new IllegalStateException("No colour");
    }

    private static Palette fallback(boolean dark) { return dark ? Palette.jasperDark() : Palette.jasperLight(); }

    /** Finds a key along the scheme chain, following {@code baseAttributes}. */
    private static final class Lookup {
        private final List<Scheme> chain;
        private final String label;
        private final List<String> warnings;

        Lookup(List<Scheme> chain, String label, List<String> warnings) {
            this.chain = chain; this.label = label; this.warnings = warnings;
        }

        Color color(String key) {
            for (Scheme scheme : chain) {
                String value = scheme.colors().get(key);
                if (value != null && !value.isBlank()) return hex(value, key);
            }
            return null;
        }

        Color attribute(String key, String field) { return attribute(key, field, 0); }

        private Color attribute(String key, String field, int depth) {
            for (Scheme scheme : chain) {
                Attribute attribute = scheme.attributes().get(key);
                if (attribute == null) continue;
                String value = attribute.values().get(field);
                if (value != null && !value.isBlank()) return hex(value, key);
                // baseAttributes redirects to another key, looked up from the start of the chain.
                if (attribute.base() != null && depth < 16) return attribute(attribute.base(), field, depth + 1);
            }
            return null;
        }

        /** IntelliJ writes {@code Integer.toHexString(rgb)}: hexadecimal RGB without {@code #}, leading zeros dropped. */
        private Color hex(String value, String key) {
            String digits = value.startsWith("#") ? value.substring(1) : value;
            if (digits.isEmpty() || digits.length() > 8 || !digits.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
                warnings.add(label + ": " + key + " has colour \"" + value + "\", which is not hexadecimal RGB; ignoring it");
                return null;
            }
            return new Color((int) (Long.parseLong(digits, 16) & 0xffffff));
        }
    }

    private static List<Scheme> schemes(Element root) {
        if (root.getTagName().equals("scheme")) return List.of(scheme(root));
        return SafeXml.children(root, "scheme").stream().map(EditorScheme::scheme).toList();
    }

    private static Scheme scheme(Element element) {
        var colors = new LinkedHashMap<String, String>();
        for (Element section : SafeXml.children(element, "colors"))
            for (Element option : SafeXml.children(section, "option")) colors.put(option.getAttribute("name"), option.getAttribute("value"));
        var attributes = new LinkedHashMap<String, Attribute>();
        for (Element section : SafeXml.children(element, "attributes"))
            for (Element option : SafeXml.children(section, "option")) {
                var values = new LinkedHashMap<String, String>();
                for (Element value : SafeXml.children(option, "value"))
                    for (Element field : SafeXml.children(value, "option")) values.put(field.getAttribute("name"), field.getAttribute("value"));
                String base = option.getAttribute("baseAttributes");
                attributes.put(option.getAttribute("name"), new Attribute(base.isEmpty() ? null : base, values));
            }
        String parent = element.getAttribute("parent_scheme");
        return new Scheme(element.getAttribute("name"), parent.isEmpty() ? null : parent, colors, attributes);
    }

    private static Map<String, Scheme> readParents() {
        try (InputStream in = EditorScheme.class.getClassLoader().getResourceAsStream(PARENTS_FILE)) {
            if (in == null) throw new IllegalStateException("Missing " + PARENTS_FILE);
            var schemes = new HashMap<String, Scheme>();
            for (Scheme scheme : schemes(SafeXml.parse(in.readAllBytes()))) schemes.put(scheme.name(), scheme);
            return Map.copyOf(schemes);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
