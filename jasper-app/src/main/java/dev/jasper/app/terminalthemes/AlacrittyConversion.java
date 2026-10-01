package dev.jasper.app.terminalthemes;

import dev.jasper.app.appearance.Seed;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeDocument;
import dev.jasper.app.appearance.ThemeGenerator;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Pure transitional conversion of a legacy terminal palette into a native workspace theme. */
public final class AlacrittyConversion {
    private AlacrittyConversion() {}

    /** Preserves every terminal color and derives workspace colors from the palette's background, foreground and blue. */
    public static Theme convert(TerminalTheme theme) {
        var palette = theme.palette();
        var ansi = new HashMap<Integer, Color>();
        for (int i = 0; i < 16; i++) ansi.put(i, palette.ansi().get(i));
        String id = "legacy-alacritty:" + theme.file().map(path -> path.toAbsolutePath().normalize().toString()).orElse(theme.name());
        var document = new ThemeDocument(id, theme.name(), Optional.empty(), Optional.empty(), 0, Optional.of(false),
            Map.of(), Map.of(Seed.BACKGROUND, palette.background(), Seed.FOREGROUND, palette.foreground(),
                Seed.CURSOR, palette.cursor(), Seed.SELECTION, palette.selection(),
                Seed.ACCENT, accent(palette.ansi().get(4), palette.background())),
            ansi, Map.of(), Map.of(), Map.of(), Map.of());
        var generated = ThemeGenerator.generate(document);
        return new Theme(id, generated.name(), generated.dark(), palette, new Theme.LegacyPalette(theme.file(), document));
    }

    private static Color accent(Color blue, Color background) {
        if (contrast(blue, background) >= 3) return blue;
        Color target = contrast(Color.WHITE, background) >= contrast(Color.BLACK, background) ? Color.WHITE : Color.BLACK;
        for (int step = 1; step <= 20; step++) {
            double amount = step / 20.0;
            var candidate = new Color((int) Math.round(blue.getRed() * (1 - amount) + target.getRed() * amount),
                (int) Math.round(blue.getGreen() * (1 - amount) + target.getGreen() * amount),
                (int) Math.round(blue.getBlue() * (1 - amount) + target.getBlue() * amount));
            if (contrast(candidate, background) >= 3) return candidate;
        }
        return target;
    }

    private static double contrast(Color first, Color second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    private static double luminance(Color color) {
        double[] rgb = { color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0 };
        for (int i = 0; i < rgb.length; i++)
            rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }
}
