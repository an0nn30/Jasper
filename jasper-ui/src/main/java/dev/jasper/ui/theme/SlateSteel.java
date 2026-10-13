package dev.jasper.ui.theme;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Jasper's theme, Slate Steel, from its bundled file {@code slate-steel.json} (the colour reference
 * {@code docs/design/theme-engine/slate-steel.md}'s machine-readable block): the Dark and Light UI palettes, the
 * terminal schemes and the derived editor schemes. Roles the file does not list follow fixed rules, never colour
 * values: {@code border.focus} is {@code accent}, {@code surface.status} is {@code surface.app}, {@code border.identity}
 * is {@code border.control} and each {@code hue.<name>.surface} is its hue (the kit applies the tile alpha). A role with
 * neither a value nor a rule, an unknown key or a malformed colour fails loading. Immutable.
 */
public final class SlateSteel {
    static final String RESOURCE = "/dev/jasper/ui/theme/slate-steel.json";
    static final String DARK = "slate-steel-dark", LIGHT = "slate-steel-light";
    private static final Set<SemanticColor> RULED = Set.of(SemanticColor.BORDER_FOCUS, SemanticColor.SURFACE_STATUS,
        SemanticColor.BORDER_IDENTITY, SemanticColor.HUE_BLUE_SURFACE, SemanticColor.HUE_GREEN_SURFACE,
        SemanticColor.HUE_AMBER_SURFACE, SemanticColor.HUE_PURPLE_SURFACE, SemanticColor.HUE_NEUTRAL_SURFACE);

    private final String origin;
    private final Map<String, Object> ui, terminal, editor;
    private final SemanticPalette dark, light;
    private final Map<TerminalScheme, SchemeColors> schemes;
    private final EditorColors darkEditor, lightEditor;
    private final Map<SyntaxRole, SyntaxStyle> darkSyntax, lightSyntax;
    private final GeneratedTheme darkTheme, lightTheme;

    private SlateSteel(Map<String, Object> root, String origin) {
        this.origin = origin;
        objects(root, "", List.of("ui", "terminal", "editor"));
        ui = objects(root.get("ui"), "ui", List.of(DARK, LIGHT));
        terminal = objects(root.get("terminal"), "terminal", List.of(DARK, LIGHT, "high-contrast"));
        var parsed = new EnumMap<TerminalScheme, SchemeColors>(TerminalScheme.class);
        for (var scheme : TerminalScheme.values()) parsed.put(scheme, scheme(scheme));
        schemes = Map.copyOf(parsed);
        editor = objects(root.get("editor"), "editor", List.of(DARK, LIGHT));
        dark = palette(DARK);
        light = palette(LIGHT);
        var darkStyles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        darkEditor = editor(true, darkStyles);
        darkSyntax = Map.copyOf(darkStyles);
        var lightStyles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        lightEditor = editor(false, lightStyles);
        lightSyntax = Map.copyOf(lightStyles);
        darkTheme = generate(true);
        lightTheme = generate(false);
    }

    /** The bundled theme, read once. */
    public static SlateSteel bundled() { return Bundled.THEME; }

    private static final class Bundled {
        static final SlateSteel THEME = load();
        private static SlateSteel load() {
            try (var in = SlateSteel.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("Missing bundled theme " + RESOURCE);
                return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "slate-steel.json");
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }

    /** A theme file in the bundled file's shape; {@code origin} names it in errors. */
    static SlateSteel parse(String json, String origin) { return new SlateSteel(ThemeJson.parse(json, origin), origin); }

    /** The Dark or Light UI palette. */
    public SemanticPalette palette(boolean dark) { return dark ? this.dark : light; }

    private SemanticPalette palette(String variant) {
        @SuppressWarnings("unchecked") var values = (Map<String, Object>) ui.get(variant);
        String where = "ui." + variant;
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        values.forEach((key, value) -> {
            var role = SemanticColor.of(key).filter(r -> !RULED.contains(r))
                .orElseThrow(() -> error(where + ": Unknown colour \"" + key + "\""));
            if (!(value instanceof String text)) throw error(where + "." + key + ": Expected a colour");
            colors.put(role, color(text, origin, where + "." + key));
        });
        for (var role : SemanticColor.values()) {
            if (RULED.contains(role)) continue;
            if (!colors.containsKey(role)) throw error(where + ": Missing colour \"" + role.key() + "\"");
        }
        colors.put(SemanticColor.BORDER_FOCUS, colors.get(SemanticColor.ACCENT));
        colors.put(SemanticColor.SURFACE_STATUS, colors.get(SemanticColor.SURFACE_APP));
        colors.put(SemanticColor.BORDER_IDENTITY, colors.get(SemanticColor.BORDER_CONTROL));
        colors.put(SemanticColor.HUE_BLUE_SURFACE, colors.get(SemanticColor.HUE_BLUE));
        colors.put(SemanticColor.HUE_GREEN_SURFACE, colors.get(SemanticColor.HUE_GREEN));
        colors.put(SemanticColor.HUE_AMBER_SURFACE, colors.get(SemanticColor.HUE_AMBER));
        colors.put(SemanticColor.HUE_PURPLE_SURFACE, colors.get(SemanticColor.HUE_PURPLE));
        colors.put(SemanticColor.HUE_NEUTRAL_SURFACE, colors.get(SemanticColor.HUE_NEUTRAL));
        return new SemanticPalette(colors);
    }

    static final List<String> ANSI = List.of("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white");
    static final List<String> TERMINAL_KEYS = terminalKeys();
    private static List<String> terminalKeys() {
        var keys = new ArrayList<>(List.of("bg", "fg", "fg.bold", "cursor", "selection"));
        keys.addAll(ANSI);
        for (String name : ANSI) keys.add("bright." + name);
        return List.copyOf(keys);
    }

    /** A terminal scheme's colours. */
    public SchemeColors terminal(TerminalScheme scheme) { return schemes.get(Objects.requireNonNull(scheme)); }

    private SchemeColors scheme(TerminalScheme scheme) {
        @SuppressWarnings("unchecked") var values = (Map<String, Object>) terminal.get(scheme.key());
        String where = "terminal." + scheme.key();
        exactly(values.keySet(), TERMINAL_KEYS, where, "colour");
        Function<String, Color> get = key -> {
            if (!(values.get(key) instanceof String text)) throw error(where + "." + key + ": Expected a colour");
            return color(text, origin, where + "." + key);
        };
        var ansi = new ArrayList<Color>(16);
        for (String name : ANSI) ansi.add(get.apply(name));
        for (String name : ANSI) ansi.add(get.apply("bright." + name));
        return new SchemeColors(new TerminalColors(get.apply("bg"), get.apply("fg"), get.apply("cursor"), get.apply("selection"), ansi),
            get.apply("fg.bold"));
    }

    static final List<String> EDITOR_SURFACES = List.of("background", "foreground", "caret", "selection", "currentLine", "gutter", "lineNumbers");

    /** The Dark or Light editor scheme's surfaces. */
    public EditorColors editor(boolean dark) { return dark ? darkEditor : lightEditor; }

    /** The Dark or Light editor scheme's syntax styles, one for every role. */
    public Map<SyntaxRole, SyntaxStyle> syntax(boolean dark) { return dark ? darkSyntax : lightSyntax; }

    private EditorColors editor(boolean dark, Map<SyntaxRole, SyntaxStyle> syntax) {
        String variant = dark ? DARK : LIGHT, where = "editor." + variant;
        @SuppressWarnings("unchecked") var values = (Map<String, Object>) editor.get(variant);
        var expected = new HashSet<>(EDITOR_SURFACES);
        for (var role : SyntaxRole.values()) expected.add(role.key());
        exactly(values.keySet(), expected, where, "entry");
        Function<String, String> text = key -> {
            if (!(values.get(key) instanceof String value)) throw error(where + "." + key + ": Expected a reference");
            return value;
        };
        Color background = reference(text.apply("background"), dark, null, where + ".background").color();
        Function<String, Color> surface = key -> {
            var resolved = reference(text.apply(key), dark, background, where + "." + key);
            if (resolved.bold() || resolved.italic()) throw error(where + "." + key + ": A surface takes no style");
            return resolved.color();
        };
        for (var role : SyntaxRole.values()) syntax.put(role, reference(text.apply(role.key()), dark, background, where + "." + role.key()));
        return new EditorColors(background, surface.apply("foreground"), surface.apply("caret"), surface.apply("selection"),
            surface.apply("currentLine"), surface.apply("gutter"), surface.apply("lineNumbers"));
    }

    /**
     * {@code <reference>[ @ <N>%][, italic][, bold]} resolved for a variant ({@link SyntaxStyle} doubles as the resolved
     * reference: a colour and style flags); a share lays the reference over {@code background}.
     */
    private SyntaxStyle reference(String value, boolean dark, Color background, String where) {
        String[] parts = value.split(",");
        boolean bold = false, italic = false;
        for (int i = 1; i < parts.length; i++) {
            switch (parts[i].strip()) {
                case "italic" -> italic = true;
                case "bold" -> bold = true;
                default -> throw error(where + ": Unknown style \"" + parts[i].strip() + "\"");
            }
        }
        String[] shared = parts[0].split("@", -1);
        if (shared.length > 2) throw error(where + ": Expected one share, got \"" + value + "\"");
        String key = shared[0].strip();
        Color base = key.startsWith("terminal.") ? terminalReference(key, dark, where)
            : SemanticColor.of(key).map(role -> palette(dark).color(role))
                .orElseThrow(() -> error(where + ": Unknown reference \"" + key + "\""));
        if (shared.length == 2) {
            String share = shared[1].strip();
            if (background == null) throw error(where + ": The background cannot be laid over itself");
            if (!share.matches("\\d{1,3}%")) throw error(where + ": Expected a share such as 5%, got \"" + share + "\"");
            int percent = Integer.parseInt(share.substring(0, share.length() - 1));
            if (percent < 1 || percent > 100) throw error(where + ": A share is from 1% to 100%, got " + share);
            base = ColorMath.mix(base, background, percent / 100.0);
        }
        return new SyntaxStyle(base, bold, italic);
    }

    private Color terminalReference(String key, boolean dark, String where) {
        var colors = terminal(TerminalScheme.matching(dark));
        String name = key.substring("terminal.".length());
        switch (name) {
            case "bg": return colors.colors().background();
            case "fg": return colors.colors().foreground();
            case "fg.bold": return colors.boldForeground();
            case "cursor": return colors.colors().cursor();
            case "selection": return colors.colors().selection();
            default:
        }
        if (name.startsWith("ansi.bright.") && ANSI.contains(name.substring("ansi.bright.".length())))
            return colors.colors().ansi().get(8 + ANSI.indexOf(name.substring("ansi.bright.".length())));
        if (name.startsWith("ansi.") && ANSI.contains(name.substring("ansi.".length())))
            return colors.colors().ansi().get(ANSI.indexOf(name.substring("ansi.".length())));
        throw error(where + ": Unknown reference \"" + key + "\"");
    }

    /** The Dark or Light variant as a complete generated theme. */
    public GeneratedTheme generated(boolean dark) { return dark ? darkTheme : lightTheme; }

    private GeneratedTheme generate(boolean dark) {
        var palette = palette(dark);
        var scheme = terminal(TerminalScheme.matching(dark));
        return new GeneratedTheme("Slate Steel", dark, "Jasper", palette, scheme.colors(), editor(dark), syntax(dark),
            uiKeys(palette, scheme), GlassLevels.OPAQUE);
    }

    /** FlatLaf's colour keys from the variant's roles; FlatLafInstaller adds the metric keys and the popup shadow. */
    private static Map<String, Object> uiKeys(SemanticPalette palette, SchemeColors scheme) {
        return UiColorKeys.flatLaf(palette, scheme.colors().selection());
    }

    /** {@code #RRGGBB}, or {@code #RRGGBBAA} with alpha; {@code where} names the entry in errors. */
    static Color color(String text, String origin, String where) {
        if (text.matches("#[0-9A-Fa-f]{6}")) return new Color(Integer.parseInt(text.substring(1), 16));
        if (text.matches("#[0-9A-Fa-f]{8}")) {
            long value = Long.parseLong(text.substring(1), 16);
            return new Color((int) (value >> 24) & 0xFF, (int) (value >> 16) & 0xFF, (int) (value >> 8) & 0xFF, (int) value & 0xFF);
        }
        throw new ThemeFileException(origin, 0, where + ": Expected #RRGGBB or #RRGGBBAA, got \"" + text + "\"");
    }

    /** {@code keys} exactly: names the first missing, then the first unknown entry. */
    private void exactly(Set<?> actual, Collection<String> keys, String where, String noun) {
        for (String key : keys) if (!actual.contains(key)) throw error(where + ": Missing " + noun + " \"" + key + "\"");
        for (Object key : actual) if (!keys.contains(key)) throw error(where + ": Unknown " + noun + " \"" + key + "\"");
    }

    /** The object at {@code value}, whose keys must be exactly {@code keys}, each an object. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> objects(Object value, String where, List<String> keys) {
        if (!(value instanceof Map<?, ?> map)) throw error((where.isEmpty() ? "" : where + ": ") + "Expected the objects " + keys);
        exactly(map.keySet(), keys, where.isEmpty() ? "slate-steel.json" : where, "object");
        for (String key : keys)
            if (!(map.get(key) instanceof Map)) throw error((where.isEmpty() ? key : where + "." + key) + ": Expected an object");
        return (Map<String, Object>) map;
    }

    ThemeFileException error(String message) { return new ThemeFileException(origin, 0, Objects.requireNonNull(message)); }
}
