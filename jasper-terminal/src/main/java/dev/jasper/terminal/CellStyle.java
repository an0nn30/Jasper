package dev.jasper.terminal;

import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TextStyle.Option;

import java.awt.Color;

/** A cell's style with colors fully resolved (inverse, dim and hidden already applied). */
record CellStyle(Color foreground, Color background, boolean bold, boolean italic, boolean underline) {

    static CellStyle resolve(TextStyle style, Palette palette) {
        Color fg = palette.foreground(style.getForeground());
        Color bg = palette.background(style.getBackground());
        if (style.hasOption(Option.INVERSE)) {
            Color swap = fg;
            fg = bg;
            bg = swap;
        }
        if (style.hasOption(Option.DIM)) {
            fg = blend(fg, bg, 0.5f);
        }
        if (style.hasOption(Option.HIDDEN)) {
            fg = bg;
        }
        return new CellStyle(fg, bg,
            style.hasOption(Option.BOLD), style.hasOption(Option.ITALIC),
            style.hasOption(Option.UNDERLINED) || style instanceof HyperlinkStyle);
    }

    static Color blend(Color from, Color to, float amount) {
        return new Color(
            Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
            Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
            Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }
}
