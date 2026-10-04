package dev.jasper.ui.themeimport;

import dev.jasper.ui.theme.*;

import dev.jasper.ui.theme.TerminalColors;
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
 * schemes (the schemes IntelliJ bundles, vendored), and only then from Jasper's palette. Scheme
 * colours are used as the theme author chose them: no contrast floor applies.
 */
final class EditorScheme {
    /** A complete palette and the problems met building it. */
    record Result(TerminalColors palette, List<String> warnings, Map<SyntaxRole, SyntaxStyle> syntax) {
        Result(TerminalColors palette, List<String> warnings) { this(palette, warnings, Map.of()); }
        Result { warnings = List.copyOf(warnings); syntax = Map.copyOf(syntax); }
    }

    private record Attribute(String base, Map<String, String> values) {}
    private record Scheme(String name, String parent, Map<String, String> colors, Map<String, Attribute> attributes) {}

    private static final String BASE = "dev/jasper/ui/themeimport/intellij/";
    /** IntelliJ's Default and Darcula, then each scheme it bundles, one per file. */
    private static final List<String> PARENT_FILES = List.of(
        "DefaultColorSchemesManager.xml", "Light.xml", "expUI/expUI_lightScheme.xml", "expUI/expUI_darkScheme.xml",
        "expUI/expUI_darculaContrastScheme.xml", "islands/highContrastScheme.xml", "islands/IslandSchemeDark.xml");
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

    static boolean hasBundledParent(String name) { return Parents.SCHEMES.containsKey(name); }

    /** The palette of the one scheme in {@code xml}; {@code label} names its file in warnings. */
    static Result read(byte[] xml, String label, boolean dark) { return read(xml, label, dark, name -> null); }

    static Result read(byte[] xml, String label, boolean dark, java.util.function.Function<String, byte[]> localParents) {
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
        return palette(schemes.getFirst(), label, dark, warnings, localParents);
    }

    /** The palette of a scheme IntelliJ ships, named by a theme's {@code editorScheme} instead of a path. */
    static Result named(String name, String label, boolean dark) {
        Scheme scheme = Parents.SCHEMES.get(name);
        if (scheme == null)
            return new Result(fallback(dark), List.of(label + ": editor scheme \"" + name + "\" is not one Jasper has; the terminal uses Jasper's palette"));
        return palette(scheme, label, dark, new ArrayList<>(), key -> null);
    }

    private static Result palette(Scheme leaf, String label, boolean dark, List<String> warnings, java.util.function.Function<String, byte[]> localParents) {
        var lookup = new Lookup(chain(leaf, label, warnings, localParents), label, warnings);
        TerminalColors base = fallback(dark);
        Color background = first(lookup.color("CONSOLE_BACKGROUND_KEY"), lookup.attribute("TEXT", "BACKGROUND"), base.background());
        Color foreground = first(lookup.attribute("CONSOLE_NORMAL_OUTPUT", "FOREGROUND"), lookup.attribute("TEXT", "FOREGROUND"), base.foreground());
        Color cursor = first(lookup.color("CARET_COLOR"), base.cursor());
        Color selection = first(lookup.color("SELECTION_BACKGROUND"), base.selection());
        var ansi = new ArrayList<Color>();
        for (int i = 0; i < ANSI.size(); i++) ansi.add(first(lookup.attribute(ANSI.get(i), "FOREGROUND"), base.ansi().get(i)));
        var missing = new ArrayList<String>();
        if (lookup.color("CONSOLE_BACKGROUND_KEY") == null) missing.add("CONSOLE_BACKGROUND_KEY");
        if (lookup.attribute("CONSOLE_NORMAL_OUTPUT", "FOREGROUND") == null) missing.add("CONSOLE_NORMAL_OUTPUT");
        for (String key : ANSI) if (lookup.attribute(key, "FOREGROUND") == null) missing.add(key);
        if (!missing.isEmpty()) warnings.add(label + ": missing console colours (" + String.join(", ", missing)
            + "); used editor/Jasper " + (dark ? "Dark" : "Light") + " fallbacks");
        var syntax = syntax(lookup);
        return new Result(new TerminalColors(background, foreground, cursor, selection, ansi), warnings, syntax);
    }

    private static Map<SyntaxRole, SyntaxStyle> syntax(Lookup lookup) {
        var priorities = new java.util.EnumMap<SyntaxRole, List<String>>(SyntaxRole.class);
        priorities.put(SyntaxRole.KEYWORD, List.of("DEFAULT_KEYWORD"));
        priorities.put(SyntaxRole.TYPE, List.of("DEFAULT_CLASS_NAME"));
        priorities.put(SyntaxRole.FUNCTION, List.of("DEFAULT_FUNCTION_DECLARATION", "DEFAULT_FUNCTION_CALL"));
        priorities.put(SyntaxRole.VARIABLE, List.of("DEFAULT_IDENTIFIER"));
        priorities.put(SyntaxRole.CONSTANT, List.of("DEFAULT_CONSTANT"));
        priorities.put(SyntaxRole.NUMBER, List.of("DEFAULT_NUMBER"));
        priorities.put(SyntaxRole.STRING, List.of("DEFAULT_STRING"));
        priorities.put(SyntaxRole.COMMENT, List.of("DEFAULT_LINE_COMMENT", "DEFAULT_BLOCK_COMMENT"));
        priorities.put(SyntaxRole.DOC_COMMENT, List.of("DEFAULT_DOC_COMMENT"));
        priorities.put(SyntaxRole.ANNOTATION, List.of("DEFAULT_METADATA"));
        priorities.put(SyntaxRole.OPERATOR, List.of("DEFAULT_OPERATION_SIGN"));
        priorities.put(SyntaxRole.PUNCTUATION, List.of("DEFAULT_COMMA", "DEFAULT_SEMICOLON"));
        priorities.put(SyntaxRole.TAG, List.of("HTML_TAG", "XML_TAG"));
        priorities.put(SyntaxRole.ATTRIBUTE, List.of("HTML_ATTRIBUTE_NAME", "XML_ATTRIBUTE_NAME"));
        var regex = new java.util.LinkedHashSet<>(List.of("REGEXP_CHAR_CLASS", "REGEXP_CHARACTER"));
        lookup.chain.stream().flatMap(scheme -> scheme.attributes().keySet().stream()).filter(key -> key.startsWith("REGEXP_"))
            .sorted().forEach(regex::add);
        priorities.put(SyntaxRole.REGEX, List.copyOf(regex));
        priorities.put(SyntaxRole.ERROR, List.of("ERRORS_ATTRIBUTES"));
        var result = new java.util.EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        priorities.forEach((role, keys) -> {
            for (String key : keys) {
                Color color = lookup.attribute(key, "FOREGROUND");
                if (color == null) continue;
                String font = lookup.field(key, "FONT_TYPE", new java.util.HashSet<>());
                int flags = 0;
                if (font != null) try { flags = Integer.parseInt(font); }
                catch (NumberFormatException invalid) { lookup.warnings.add(lookup.label + ": invalid FONT_TYPE for " + key); }
                result.put(role, new SyntaxStyle(color, (flags & 1) != 0, (flags & 2) != 0));
                break;
            }
        });
        return result;
    }

    /** The scheme and its parents. A missing {@code parent_scheme} means Default, as in IntelliJ. */
    private static List<Scheme> chain(Scheme leaf, String label, List<String> warnings, java.util.function.Function<String, byte[]> localParents) {
        var chain = new ArrayList<Scheme>();
        Scheme scheme = leaf;
        var visited = new java.util.HashSet<String>();
        while (scheme != null) {
            if (!visited.add(scheme.name()) || chain.size() >= 32)
                throw new IllegalArgumentException(label + ": editor scheme parent cycle or excessive nesting at " + scheme.name());
            chain.add(scheme);
            String parent = scheme.parent() != null ? scheme.parent() : "Default".equals(scheme.name()) ? null : "Default";
            if (parent == null) break;
            Scheme next = Parents.SCHEMES.get(parent);
            if (next == null) {
                byte[] bytes = localParents.apply(parent);
                if (bytes != null) {
                    var read = schemes(SafeXml.parse(bytes));
                    if (read.size() != 1 || !read.getFirst().name().equals(parent))
                        throw new IllegalArgumentException(label + ": editor parent file must contain scheme " + parent);
                    next = read.getFirst();
                }
            }
            if (next == null) warnings.add(label + ": parent scheme \"" + parent + "\" is not one Jasper has; missing colours come from Jasper's palette");
            scheme = next;
        }
        return chain;
    }

    private static Color first(Color... colors) {
        for (Color color : colors) if (color != null) return color;
        throw new IllegalStateException("No colour");
    }

    private static TerminalColors fallback(boolean dark) { return dark ? ImportPalettes.dark() : ImportPalettes.light(); }

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
                if (value == null || value.isBlank()) continue;
                Color color = hex(value, key);
                if (color != null) return color;
            }
            return null;
        }

        Color attribute(String key, String field) { return attribute(key, field, 0); }

        private Color attribute(String key, String field, int depth) {
            for (Scheme scheme : chain) {
                Attribute attribute = scheme.attributes().get(key);
                if (attribute == null) continue;
                String value = attribute.values().get(field);
                if (value != null && !value.isBlank()) {
                    Color color = hex(value, key);
                    if (color != null) return color;
                    // An invalid value is ignored: the parent scheme's value for this key comes next.
                    continue;
                }
                // baseAttributes redirects to another key, looked up from the start of the chain.
                if (attribute.base() != null && depth < 16) return attribute(attribute.base(), field, depth + 1);
            }
            return null;
        }

        String field(String key, String field, java.util.Set<String> visited) {
            if (!visited.add(key)) return null;
            for (Scheme scheme : chain) {
                Attribute attribute = scheme.attributes().get(key);
                if (attribute == null) continue;
                String value = attribute.values().get(field);
                if (value != null && !value.isBlank()) return value;
                if (attribute.base() != null) return field(attribute.base(), field, visited);
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
        var schemes = new HashMap<String, Scheme>();
        for (String file : PARENT_FILES) {
            try (InputStream in = EditorScheme.class.getClassLoader().getResourceAsStream(BASE + file)) {
                if (in == null) throw new IllegalStateException("Missing " + BASE + file);
                for (Scheme scheme : schemes(SafeXml.parse(in.readAllBytes()))) schemes.put(scheme.name(), scheme);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        return Map.copyOf(schemes);
    }
}
