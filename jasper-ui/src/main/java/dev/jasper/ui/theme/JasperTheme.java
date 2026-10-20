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
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A Jasper theme file ({@code docs/themes.md}): a name, an optional author, a Dark and/or Light UI variant, terminal
 * schemes and an editor scheme for each UI variant. The built-in themes are {@code <id>.json} beside this class: Slate
 * Cobalt, Jasper Meadow Light, Catppuccin's flavours ({@code tools/themes/catppuccin}), and Ubuntu Yaru. A UI
 * variant lists every role except those that follow fixed rules, never colour values: {@code border.focus} is
 * {@code accent}, {@code surface.status} is {@code surface.app}, {@code border.identity} is {@code border.control} and
 * each {@code hue.<name>.surface} is its hue (the kit applies the tile alpha). A variant may leave out the title bar's
 * own colours, {@code surface.titlebar} and {@code text.titlebar}: they are then {@code surface.app} and
 * {@code text.primary}. A variant may also list the {@linkplain DerivedColor#overridable() overridable} derived roles,
 * opaque ({@link GeneratedTheme#derivedOverrides()}). A variant's matching terminal scheme is
 * the scheme with its key, {@code dark} or {@code light}. A missing, unknown or malformed entry fails loading with a
 * {@link ThemeFileException} naming the file and the entry. Immutable.
 */
public final class JasperTheme {
    /** Slate Cobalt's id; the default Dark appearance. */
    public static final String BUNDLED_ID = "slate-cobalt";
    /** The default Light appearance; Slate Cobalt remains the default Dark appearance. */
    public static final String LIGHT_DEFAULT_ID = "jasper-meadow";
    /** The format this Jasper reads: {@code "jasper-theme": 1}. */
    public static final long FORMAT = 1;
    static final String RESOURCE = "/dev/jasper/ui/theme/slate-cobalt.json";
    /** The built-in themes after Slate Cobalt, in the order Settings lists them. */
    static final List<String> CONVERTED = List.of("catppuccin-latte", "catppuccin-frappe", "catppuccin-macchiato", "catppuccin-mocha");
    static final Pattern WORDS = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern RGB = Pattern.compile("#[0-9A-Fa-f]{6}"), RGBA = Pattern.compile("#[0-9A-Fa-f]{8}"),
        SHARE = Pattern.compile("\\d{1,3}%");
    static final String DARK = "dark", LIGHT = "light";
    private static final List<String> TOP = List.of("jasper-theme", "name", "author", "ui", "terminal", "editor");
    private static final Set<SemanticColor> RULED = Set.of(SemanticColor.BORDER_FOCUS, SemanticColor.SURFACE_STATUS,
        SemanticColor.BORDER_IDENTITY, SemanticColor.HUE_BLUE_SURFACE, SemanticColor.HUE_GREEN_SURFACE,
        SemanticColor.HUE_AMBER_SURFACE, SemanticColor.HUE_PURPLE_SURFACE, SemanticColor.HUE_NEUTRAL_SURFACE);
    /** Roles a variant may leave out, and the role each then is: the title bar's own colours ({@link TitleBarTheme}). */
    private static final Map<SemanticColor, SemanticColor> OPTIONAL = Map.of(SemanticColor.SURFACE_TITLEBAR, SemanticColor.SURFACE_APP,
        SemanticColor.TEXT_TITLEBAR, SemanticColor.TEXT_PRIMARY);
    /** The override keys a variant may list, read before the roles: the overridable derived roles, by key. */
    static final Map<String, DerivedColor> DERIVED_OVERRIDES = java.util.Arrays.stream(DerivedColor.values())
        .filter(DerivedColor::overridable).collect(java.util.stream.Collectors.toUnmodifiableMap(DerivedColor::key, role -> role));

    /** One UI variant with every value filled in. */
    private record Variant(SemanticPalette palette, EditorColors editor, Map<SyntaxRole, SyntaxStyle> syntax, GeneratedTheme generated) { }
    /** A variant's roles and the derived roles it authors, as read. */
    private record Colours(SemanticPalette palette, Map<DerivedColor, Color> overrides) { }

    private final String id, origin, name, author;
    private final Map<String, Object> ui, editor;
    private final Map<String, TerminalScheme> schemes;
    private final List<TerminalScheme> schemeList;
    private final Variant dark, light;

    private JasperTheme(Map<String, Object> root, String id) {
        this.id = id;
        origin = id + ".json";
        if (!root.containsKey("jasper-theme")) throw error("Not a Jasper theme: \"jasper-theme\": " + FORMAT + " is missing");
        if (!(root.get("jasper-theme") instanceof Long format)) throw error("jasper-theme: Expected the number " + FORMAT);
        if (format != FORMAT) throw error("jasper-theme: Format " + format + " is not one this Jasper reads; expected " + FORMAT);
        for (String key : root.keySet()) if (!TOP.contains(key)) throw error("Unknown entry \"" + key + "\"");
        name = text(root, "name");
        if (root.containsKey("author") && !(root.get("author") instanceof String)) throw error("author: Expected a string");
        author = (String) root.getOrDefault("author", "");
        var terminal = object(root, "terminal");
        if (terminal.isEmpty()) throw error("terminal: Expected at least one scheme");
        var parsed = new LinkedHashMap<String, TerminalScheme>();
        terminal.forEach((key, value) -> parsed.put(key, scheme(key, value)));
        schemes = parsed;
        schemeList = List.copyOf(parsed.values());
        ui = root.containsKey("ui") ? object(root, "ui") : Map.of();
        if (root.containsKey("ui") && ui.isEmpty()) throw error("ui: Expected \"dark\", \"light\" or both");
        for (var entry : ui.entrySet()) {
            String variant = entry.getKey();
            if (!variant.equals(DARK) && !variant.equals(LIGHT)) throw error("ui: Unknown variant \"" + variant + "\"");
            if (!(entry.getValue() instanceof Map)) throw error("ui." + variant + ": Expected an object");
            if (!schemes.containsKey(variant)) throw error("ui." + variant + ": Missing its matching scheme \"terminal." + variant + "\"");
        }
        if (ui.isEmpty()) {
            if (root.containsKey("editor")) throw error("editor: A theme without ui has no editor schemes");
            editor = Map.of();
        } else {
            editor = object(root, "editor");
            exactly(editor.keySet(), ui.keySet(), "editor", "variant");
            for (String variant : editor.keySet())
                if (!(editor.get(variant) instanceof Map)) throw error("editor." + variant + ": Expected an object");
        }
        dark = ui.containsKey(DARK) ? build(true) : null;
        light = ui.containsKey(LIGHT) ? build(false) : null;
    }

    /** The default theme, Slate Cobalt, read once. */
    public static JasperTheme bundled() { return Bundled.THEME; }

    /** Every built-in theme, Slate Cobalt first, read once on first use; no installed theme may use their ids. */
    public static List<JasperTheme> builtIns() { return BuiltIns.THEMES; }

    /** The built-in theme with this id. */
    public static Optional<JasperTheme> builtIn(String id) { return builtIns().stream().filter(theme -> theme.id().equals(id)).findFirst(); }

    private static final class Bundled {
        static final JasperTheme THEME = load(RESOURCE, BUNDLED_ID);
    }

    private static final class BuiltIns {
        static final List<JasperTheme> THEMES = read();
        private static List<JasperTheme> read() {
            var themes = new ArrayList<JasperTheme>(List.of(bundled()));
            for (String id : CONVERTED) themes.add(load("/dev/jasper/ui/theme/" + id + ".json", id));
            themes.add(load("/dev/jasper/ui/theme/ubuntu-yaru.json", "ubuntu-yaru"));
            themes.add(load("/dev/jasper/ui/theme/jasper-meadow.json", LIGHT_DEFAULT_ID));
            return List.copyOf(themes);
        }
    }

    private static JasperTheme load(String resource, String id) {
        try (var in = JasperTheme.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing built-in theme " + resource);
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), id);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** The theme in {@code json}, whose file is {@code <id>.json}; errors name that file. */
    public static JasperTheme parse(String json, String id) {
        if (!validId(id)) throw new IllegalArgumentException("Not a theme id: " + id);
        return new JasperTheme(ThemeJson.parse(json, id + ".json"), id);
    }

    /** A theme id: lowercase letters and digits in dash-separated words, at most 64 characters. */
    public static boolean validId(String id) { return id != null && id.length() <= 64 && WORDS.matcher(id).matches(); }

    /** The id: the file name without {@code .json}. */
    public String id() { return id; }
    /** The display name. */
    public String name() { return name; }
    /** The author, or empty. */
    public String author() { return author; }
    /** Whether the theme has the Dark or Light UI variant; a terminal-only theme has neither. */
    public boolean has(boolean dark) { return (dark ? this.dark : light) != null; }

    /** The Dark or Light UI palette; the variant must exist. */
    public SemanticPalette palette(boolean dark) { return variant(dark).palette(); }
    /** The Dark or Light editor scheme's surfaces; the variant must exist. */
    public EditorColors editor(boolean dark) { return variant(dark).editor(); }
    /** The Dark or Light editor scheme's syntax styles, one for every role; the variant must exist. */
    public Map<SyntaxRole, SyntaxStyle> syntax(boolean dark) { return variant(dark).syntax(); }
    /** The Dark or Light variant as a complete generated theme; the variant must exist. */
    public GeneratedTheme generated(boolean dark) { return variant(dark).generated(); }
    /** The Dark or Light variant's matching terminal scheme; the variant must exist. */
    public TerminalScheme matching(boolean dark) { variant(dark); return schemes.get(dark ? DARK : LIGHT); }
    /** The terminal schemes, in the file's order. */
    public List<TerminalScheme> schemes() { return schemeList; }
    /** The scheme with this key. */
    public Optional<TerminalScheme> scheme(String key) { return Optional.ofNullable(schemes.get(key)); }

    @Override public String toString() { return "JasperTheme[" + id + "]"; }

    private Variant variant(boolean dark) {
        var variant = dark ? this.dark : light;
        if (variant == null) throw new IllegalStateException(name + " has no " + (dark ? "Dark" : "Light") + " variant");
        return variant;
    }

    private Variant build(boolean dark) {
        var read = colours(dark ? DARK : LIGHT);
        var palette = read.palette();
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        var colors = editor(dark, palette, styles);
        var syntax = Map.copyOf(styles);
        var scheme = schemes.get(dark ? DARK : LIGHT);
        var generated = new GeneratedTheme(name, dark, author, palette, scheme.colors(), colors, syntax, GlassLevels.OPAQUE,
            read.overrides());
        return new Variant(palette, colors, syntax, generated);
    }

    private Colours colours(String variant) {
        @SuppressWarnings("unchecked") var values = (Map<String, Object>) ui.get(variant);
        String where = "ui." + variant;
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        var overrides = new EnumMap<DerivedColor, Color>(DerivedColor.class);
        values.forEach((key, value) -> {
            var derived = DERIVED_OVERRIDES.get(key);
            if (derived != null) {
                if (!(value instanceof String text)) throw error(where + "." + key + ": Expected a colour");
                if (!RGB.matcher(text).matches()) throw error(where + "." + key + ": Expected #RRGGBB, got \"" + text + "\"");
                overrides.put(derived, color(text, origin, where + "." + key));
                return;
            }
            var role = SemanticColor.of(key).filter(r -> !RULED.contains(r))
                .orElseThrow(() -> error(where + ": Unknown colour \"" + key + "\""));
            if (!(value instanceof String text)) throw error(where + "." + key + ": Expected a colour");
            colors.put(role, color(text, origin, where + "." + key));
        });
        for (var role : SemanticColor.values()) {
            if (RULED.contains(role) || OPTIONAL.containsKey(role)) continue;
            if (!colors.containsKey(role)) throw error(where + ": Missing colour \"" + role.key() + "\"");
        }
        OPTIONAL.forEach((role, otherwise) -> colors.putIfAbsent(role, colors.get(otherwise)));
        colors.put(SemanticColor.BORDER_FOCUS, colors.get(SemanticColor.ACCENT));
        colors.put(SemanticColor.SURFACE_STATUS, colors.get(SemanticColor.SURFACE_APP));
        colors.put(SemanticColor.BORDER_IDENTITY, colors.get(SemanticColor.BORDER_CONTROL));
        colors.put(SemanticColor.HUE_BLUE_SURFACE, colors.get(SemanticColor.HUE_BLUE));
        colors.put(SemanticColor.HUE_GREEN_SURFACE, colors.get(SemanticColor.HUE_GREEN));
        colors.put(SemanticColor.HUE_AMBER_SURFACE, colors.get(SemanticColor.HUE_AMBER));
        colors.put(SemanticColor.HUE_PURPLE_SURFACE, colors.get(SemanticColor.HUE_PURPLE));
        colors.put(SemanticColor.HUE_NEUTRAL_SURFACE, colors.get(SemanticColor.HUE_NEUTRAL));
        return new Colours(new SemanticPalette(colors), overrides);
    }

    static final List<String> ANSI = List.of("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white");
    static final List<String> TERMINAL_KEYS = terminalKeys();
    private static List<String> terminalKeys() {
        var keys = new ArrayList<>(List.of("bg", "fg", "fg.bold", "cursor", "selection"));
        keys.addAll(ANSI);
        for (String name : ANSI) keys.add("bright." + name);
        return List.copyOf(keys);
    }

    private TerminalScheme scheme(String key, Object value) {
        if (!SchemeId.validKey(key))
            throw error("terminal: Scheme key \"" + key + "\" must be lowercase letters, digits and dashes, at most 32 characters");
        String where = "terminal." + key;
        if (!(value instanceof Map<?, ?> map)) throw error(where + ": Expected an object");
        @SuppressWarnings("unchecked") var values = (Map<String, Object>) map;
        if (!values.containsKey("name")) throw error(where + ": Missing entry \"name\"");
        if (!(values.get("name") instanceof String schemeName) || schemeName.isBlank())
            throw error(where + ".name: Expected a non-empty string");
        var colours = new HashSet<>(values.keySet());
        colours.remove("name");
        exactly(colours, TERMINAL_KEYS, where, "colour");
        Function<String, Color> get = colour -> {
            if (!(values.get(colour) instanceof String text)) throw error(where + "." + colour + ": Expected a colour");
            return color(text, origin, where + "." + colour);
        };
        var ansi = new ArrayList<Color>(16);
        for (String name : ANSI) ansi.add(get.apply(name));
        for (String name : ANSI) ansi.add(get.apply("bright." + name));
        return new TerminalScheme(new SchemeId(id, key), schemeName,
            new TerminalColors(get.apply("bg"), get.apply("fg"), get.apply("cursor"), get.apply("selection"), ansi), get.apply("fg.bold"));
    }

    static final List<String> EDITOR_SURFACES = List.of("background", "foreground", "caret", "selection", "currentLine", "gutter", "lineNumbers");

    private EditorColors editor(boolean dark, SemanticPalette palette, Map<SyntaxRole, SyntaxStyle> syntax) {
        String variant = dark ? DARK : LIGHT, where = "editor." + variant;
        @SuppressWarnings("unchecked") var values = (Map<String, Object>) editor.get(variant);
        var expected = new HashSet<>(EDITOR_SURFACES);
        for (var role : SyntaxRole.values()) expected.add(role.key());
        exactly(values.keySet(), expected, where, "entry");
        Function<String, String> text = key -> {
            if (!(values.get(key) instanceof String value)) throw error(where + "." + key + ": Expected a reference");
            return value;
        };
        BiFunction<String, Color, Color> surface = (key, under) -> {
            var resolved = reference(text.apply(key), dark, palette, under, where + "." + key);
            if (resolved.bold() || resolved.italic()) throw error(where + "." + key + ": A surface takes no style");
            return resolved.color();
        };
        Color background = surface.apply("background", null);
        for (var role : SyntaxRole.values())
            syntax.put(role, reference(text.apply(role.key()), dark, palette, background, where + "." + role.key()));
        return new EditorColors(background, surface.apply("foreground", background), surface.apply("caret", background),
            surface.apply("selection", background), surface.apply("currentLine", background), surface.apply("gutter", background),
            surface.apply("lineNumbers", background));
    }

    /**
     * {@code <colour>[ @ <N>%][, italic][, bold]} resolved for a variant, where the colour is {@code #RRGGBB}, a
     * {@code terminal.} reference to the matching scheme or a role ({@link SyntaxStyle} doubles as the resolved value: a
     * colour and style flags); a share lays the colour over {@code background}.
     */
    private SyntaxStyle reference(String value, boolean dark, SemanticPalette palette, Color background, String where) {
        String[] parts = value.split(",", -1);
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
        Color base;
        if (key.startsWith("#")) {
            if (!RGB.matcher(key).matches()) throw error(where + ": Expected #RRGGBB or a reference, got \"" + key + "\"");
            base = color(key, origin, where);
        } else if (key.startsWith("terminal.")) {
            base = terminalReference(key, dark, where);
        } else {
            base = SemanticColor.of(key).map(palette::color).orElseThrow(() -> error(where + ": Unknown reference \"" + key + "\""));
        }
        if (shared.length == 2) {
            String share = shared[1].strip();
            if (background == null) throw error(where + ": The background cannot be laid over itself");
            if (!SHARE.matcher(share).matches()) throw error(where + ": Expected a share such as 5%, got \"" + share + "\"");
            int percent = Integer.parseInt(share.substring(0, share.length() - 1));
            if (percent < 1 || percent > 100) throw error(where + ": A share is from 1% to 100%, got " + share);
            base = ColorMath.mix(base, background, percent / 100.0);
        }
        return new SyntaxStyle(base, bold, italic);
    }

    private Color terminalReference(String key, boolean dark, String where) {
        var colors = schemes.get(dark ? DARK : LIGHT);
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

    /** {@code #RRGGBB}, or {@code #RRGGBBAA} with alpha; {@code where} names the entry in errors. */
    static Color color(String text, String origin, String where) {
        if (RGB.matcher(text).matches()) return new Color(Integer.parseInt(text.substring(1), 16));
        if (RGBA.matcher(text).matches()) {
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

    /** The object at {@code key} of the top level, which must be present. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Map<String, Object> root, String key) {
        if (!root.containsKey(key)) throw error("Missing object \"" + key + "\"");
        if (!(root.get(key) instanceof Map<?, ?> map)) throw error(key + ": Expected an object");
        return (Map<String, Object>) map;
    }

    /** The non-empty string at {@code key} of the top level, which must be present. */
    private String text(Map<String, Object> root, String key) {
        if (!root.containsKey(key)) throw error("Missing entry \"" + key + "\"");
        if (!(root.get(key) instanceof String value) || value.isBlank()) throw error(key + ": Expected a non-empty string");
        return value;
    }

    private ThemeFileException error(String message) { return new ThemeFileException(origin, 0, Objects.requireNonNull(message)); }
}
