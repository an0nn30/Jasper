package dev.jasper.ui.themeimport;

import dev.jasper.ui.theme.TerminalColors;

import dev.jasper.ui.theme.Seed;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.ThemeDocument;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Pure transitional conversion of a legacy terminal palette into a native workspace theme. */
public final class AlacrittyConversion {
    private AlacrittyConversion() {}

    /** Preserves every terminal color and derives workspace colors from the palette's background, foreground and blue. */
    public static ThemeDocument convert(String name, TerminalColors palette, String origin) {
        var ansi = new HashMap<Integer, Color>();
        for (int i = 0; i < 16; i++) ansi.put(i, palette.ansi().get(i));
        var document = new ThemeDocument(origin, name, Optional.empty(), Optional.empty(), 0, Optional.of(false),
            Map.of(), Map.of(Seed.BACKGROUND, palette.background(), Seed.FOREGROUND, palette.foreground(),
                Seed.CURSOR, palette.cursor(), Seed.SELECTION, palette.selection(),
                Seed.ACCENT, accent(palette.ansi().get(4), palette.background())),
            ansi, Map.of(), Map.of(), Map.of(), Map.of());
        return document;
    }

    private static Color accent(Color blue, Color background) {
        if (ColorMath.contrast(blue, background) >= 3) return blue;
        Color target = ColorMath.contrast(Color.WHITE, background) >= ColorMath.contrast(Color.BLACK, background) ? Color.WHITE : Color.BLACK;
        for (int step = 1; step <= 20; step++) {
            double amount = step / 20.0;
            var candidate = new Color((int) Math.round(blue.getRed() * (1 - amount) + target.getRed() * amount),
                (int) Math.round(blue.getGreen() * (1 - amount) + target.getGreen() * amount),
                (int) Math.round(blue.getBlue() * (1 - amount) + target.getBlue() * amount));
            if (ColorMath.contrast(candidate, background) >= 3) return candidate;
        }
        return target;
    }

}
