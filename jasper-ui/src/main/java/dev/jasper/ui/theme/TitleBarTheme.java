package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.EnumMap;

/**
 * The window header's theme: the theme itself, unless its variant gives the title bar its own colours
 * ({@code surface.titlebar} other than {@code surface.app}, or {@code text.titlebar} other than {@code text.primary}).
 * Then the roles the header paints with follow those two: its surface, the text levels, the hover and pressed fills and
 * the search field's fill and borders, and the theme is dark when the title bar is. Everything else is the theme's,
 * the derived roles it authors included.
 */
public final class TitleBarTheme {
    private static GeneratedTheme lastIn, lastOut;

    private TitleBarTheme() { }

    /** {@code theme}'s header theme; the same object when the variant leaves the title bar to the window chrome. */
    public static synchronized GeneratedTheme of(GeneratedTheme theme) {
        if (theme == lastIn) return lastOut;
        lastOut = derive(theme);
        lastIn = theme;
        return lastOut;
    }

    /** Whether the variant gives the title bar colours of its own. */
    public static boolean own(SemanticPalette palette) {
        return !palette.color(SemanticColor.SURFACE_TITLEBAR).equals(palette.color(SemanticColor.SURFACE_APP))
            || !palette.color(SemanticColor.TEXT_TITLEBAR).equals(palette.color(SemanticColor.TEXT_PRIMARY));
    }

    private static GeneratedTheme derive(GeneratedTheme theme) {
        var palette = theme.semantic();
        if (!own(palette)) return theme;
        Color surface = palette.color(SemanticColor.SURFACE_TITLEBAR), ink = palette.color(SemanticColor.TEXT_TITLEBAR);
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) colors.put(role, palette.color(role));
        colors.put(SemanticColor.SURFACE_APP, surface);
        colors.put(SemanticColor.TEXT_PRIMARY, ink);
        colors.put(SemanticColor.TEXT_STRONG_SECONDARY, ColorMath.mix(ink, surface, .85));
        colors.put(SemanticColor.TEXT_SECONDARY, ColorMath.mix(ink, surface, .72));
        colors.put(SemanticColor.TEXT_TERTIARY, ColorMath.mix(ink, surface, .5));
        colors.put(SemanticColor.TEXT_PLACEHOLDER, ColorMath.mix(ink, surface, .5));
        colors.put(SemanticColor.TEXT_FIELD_RAISED, ink);
        colors.put(SemanticColor.HUE_NEUTRAL, ColorMath.mix(ink, surface, .85));
        colors.put(SemanticColor.HOVER_OVERLAY, ink);
        colors.put(SemanticColor.SURFACE_CONTROL_SELECTED, ColorMath.mix(ink, surface, .16));
        colors.put(SemanticColor.SURFACE_FIELD_RAISED, ColorMath.mix(ink, surface, .10));
        colors.put(SemanticColor.BORDER_FIELD_RAISED, ColorMath.mix(ink, surface, .16));
        colors.put(SemanticColor.BORDER_FIELD_RAISED_HOVER, ColorMath.mix(ink, surface, .26));
        colors.put(SemanticColor.BORDER_SUBTLE, ColorMath.opacity(ink, .06));
        boolean dark = ColorMath.lightness(surface) < 50;
        return new GeneratedTheme(theme.name(), dark, theme.author(), new SemanticPalette(colors), theme.terminal(), theme.editor(),
            theme.syntax(), theme.glass(), theme.derivedOverrides());
    }
}
