package dev.jasper.ui.theme;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    private SlateSteel(Map<String, Object> root, String origin) {
        this.origin = origin;
        objects(root, "", List.of("ui", "terminal", "editor"));
        ui = objects(root.get("ui"), "ui", List.of(DARK, LIGHT));
        terminal = objects(root.get("terminal"), "terminal", List.of(DARK, LIGHT, "high-contrast"));
        editor = objects(root.get("editor"), "editor", List.of(DARK, LIGHT));
        dark = palette(DARK);
        light = palette(LIGHT);
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

    /** {@code #RRGGBB}, or {@code #RRGGBBAA} with alpha; {@code where} names the entry in errors. */
    static Color color(String text, String origin, String where) {
        if (text.matches("#[0-9A-Fa-f]{6}")) return new Color(Integer.parseInt(text.substring(1), 16));
        if (text.matches("#[0-9A-Fa-f]{8}")) {
            long value = Long.parseLong(text.substring(1), 16);
            return new Color((int) (value >> 24) & 0xFF, (int) (value >> 16) & 0xFF, (int) (value >> 8) & 0xFF, (int) value & 0xFF);
        }
        throw new ThemeFileException(origin, 0, where + ": Expected #RRGGBB or #RRGGBBAA, got \"" + text + "\"");
    }

    /** The object at {@code value}, whose keys must be exactly {@code keys}, each an object. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> objects(Object value, String where, List<String> keys) {
        if (!(value instanceof Map<?, ?> map) || !map.keySet().equals(Set.copyOf(keys)))
            throw error((where.isEmpty() ? "" : where + ": ") + "Expected the objects " + keys);
        for (String key : keys) if (!(map.get(key) instanceof Map)) throw error(where + "." + key + ": Expected an object");
        return (Map<String, Object>) map;
    }

    ThemeFileException error(String message) { return new ThemeFileException(origin, 0, Objects.requireNonNull(message)); }
}
