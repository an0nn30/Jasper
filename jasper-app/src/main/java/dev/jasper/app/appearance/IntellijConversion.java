package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a resolved IntelliJ theme and its terminal palette into a complete ({@code ui_complete}) Jasper theme
 * document: the palette becomes the seeds, the theme's keys its {@code [ui]}. The theme's named colours lead the
 * {@code [ui]} as {@code ColorPalette.<name>} keys, the keys FlatLaf makes of an IntelliJ theme's {@code colors}
 * table, so FlatLaf reads them (its accent included) exactly as it reads the theme file. Plan 4 adds tokens and syntax
 * and moves this to the importer.
 */
final class IntellijConversion {
    /** The prefix FlatLaf gives an IntelliJ theme's named colours. */
    private static final String PALETTE = "ColorPalette.";
    /** Where an IntelliJ theme keeps its accent, most specific first; FlatLaf reads its accent from the first two. */
    private static final List<String> ACCENT_KEYS = List.of(PALETTE + "accent", PALETTE + "accentColor", "Component.focusColor",
        "Component.accentColor", "EditorTabs.underlineColor", "Button.default.startBackground", "Component.linkColor");

    private IntellijConversion() {}

    static ThemeDocument convert(ThemeLoader.Resolved resolved, Palette palette, String origin) {
        var seeds = new EnumMap<Seed, Color>(Seed.class);
        seeds.put(Seed.BACKGROUND, palette.background());
        seeds.put(Seed.FOREGROUND, palette.foreground());
        seeds.put(Seed.CURSOR, palette.cursor());
        seeds.put(Seed.SELECTION, palette.selection());
        Color accent = ACCENT_KEYS.stream().map(key -> key.startsWith(PALETTE)
                ? ThemeLoader.color(resolved.namedColors().get(key.substring(PALETTE.length()))) : resolved.colors().get(key))
            .filter(Objects::nonNull).findFirst().orElse(palette.ansi().get(4));
        seeds.put(Seed.ACCENT, new Color(accent.getRGB() & 0xffffff));
        var ansi = new HashMap<Integer, Color>();
        for (int i = 0; i < 16; i++) ansi.put(i, palette.ansi().get(i));
        var ui = new LinkedHashMap<String, Object>();
        resolved.namedColors().forEach((name, value) -> ui.put(PALETTE + name, value));
        resolved.ui().forEach((key, value) -> { ui.remove(key); ui.put(key, value); });
        return new ThemeDocument(origin, resolved.name(), Optional.of(resolved.dark()), Optional.empty(), 0,
            Optional.of(true), Map.of(), seeds, ansi, Map.of(), Map.of(), ui, resolved.icons());
    }
}
