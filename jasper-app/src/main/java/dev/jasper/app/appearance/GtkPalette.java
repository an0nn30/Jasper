package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.function.Function;

/** Terminal colours from the installed GTK theme's text view; the ANSI set follows its brightness. */
final class GtkPalette {
    static final String BACKGROUND = "Jasper.gtkTextBackground";
    static final String FOREGROUND = "Jasper.gtkTextForeground";
    static final String CARET = "Jasper.gtkTextCaret";
    static final String SELECTION = "Jasper.gtkTextSelectionBackground";
    private GtkPalette() {}

    static Palette from(Function<String, Color> colors) {
        Color background = colors.apply(BACKGROUND);
        Palette base = background != null && dark(background) ? Palette.jasperDark() : Palette.jasperLight();
        Color foreground = orElse(colors.apply(FOREGROUND), base.foreground());
        return new Palette(plain(foreground), plain(orElse(background, base.background())),
            plain(orElse(colors.apply(CARET), foreground)), plain(orElse(colors.apply(SELECTION), base.selection())), base.ansi());
    }

    /** WCAG relative luminance below one half. */
    static boolean dark(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue()) < 0.5;
    }

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
    private static Color orElse(Color color, Color fallback) { return color != null ? color : fallback; }
    // The terminal compares colours by value; drop UIResource subclasses and alpha.
    private static Color plain(Color color) { return new Color(color.getRed(), color.getGreen(), color.getBlue()); }
}
