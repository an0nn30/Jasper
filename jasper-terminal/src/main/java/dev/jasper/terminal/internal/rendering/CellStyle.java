package dev.jasper.terminal.internal.rendering;

import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.internal.text.CellAttributes;

import java.awt.Color;

/** A cell's style with colors fully resolved (inverse, dim and hidden already applied). */
public record CellStyle(Color foreground, Color background, boolean bold, boolean italic, boolean underline) {

    public static CellStyle resolve(CellAttributes style, Palette palette) {
        Color fg = resolve(style.foreground(), palette.foreground(), palette);
        Color bg = resolve(style.background(), palette.background(), palette);
        if (style.has(CellAttributes.INVERSE)) {
            Color swap = fg;
            fg = bg;
            bg = swap;
        }
        if (style.has(CellAttributes.DIM)) {
            fg = blend(fg, bg, 0.5f);
        }
        if (style.has(CellAttributes.HIDDEN)) {
            fg = bg;
        }
        return new CellStyle(fg, bg,
            style.has(CellAttributes.BOLD), style.has(CellAttributes.ITALIC),
            style.has(CellAttributes.UNDERLINE));
    }

    private static Color resolve(int encoded, Color fallback, Palette palette) {
        if (encoded == -1) return fallback;
        return (encoded & 0x01000000) != 0 ? new Color(encoded & 0x00ffffff) : palette.indexed(encoded);
    }

    public static Color blend(Color from, Color to, float amount) {
        return new Color(
            Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
            Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
            Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }
}
