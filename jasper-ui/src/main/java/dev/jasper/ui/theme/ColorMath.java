package dev.jasper.ui.theme;

import java.awt.Color;

/** Pure legacy contrast and color arithmetic, independent of UI defaults. */
public final class ColorMath {
    private ColorMath() {}

    /** {@code color}, moved toward {@code toward} in 5% steps until its contrast with {@code background} reaches {@code minimum}. */
    public static Color readable(Color color, Color background, double minimum, Color toward) {
        for (int step = 0; step <= 20; step++) {
            Color candidate = mix(toward, color, step / 20.0);
            if (contrast(candidate, background) >= minimum) return candidate;
        }
        return toward;
    }

    /** {@code amount} of {@code top} over {@code bottom}, opaque. */
    public static Color mix(Color top, Color bottom, double amount) {
        return new Color((int) Math.round(top.getRed() * amount + bottom.getRed() * (1 - amount)),
            (int) Math.round(top.getGreen() * amount + bottom.getGreen() * (1 - amount)),
            (int) Math.round(top.getBlue() * amount + bottom.getBlue() * (1 - amount)));
    }

    /** The WCAG contrast ratio of two opaque colours. */
    public static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }

    /** WCAG relative luminance of an opaque colour, 0 (black) to 1 (white). */
    public static double luminance(Color color) {
        double[] rgb = {color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0};
        for (int i = 0; i < rgb.length; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }

    /** CIE L* of an opaque colour: perceived lightness, 0 (black) to 100 (white), even across dark and light colours. */
    public static double lightness(Color color) {
        double y = luminance(color);
        return 116 * (y > 216.0 / 24389 ? Math.cbrt(y) : (24389.0 / 27 * y + 16) / 116) - 16;
    }

    /** Applies one paint treatment to the base alpha, preserving authored translucency. */
    public static Color opacity(Color color, double amount) {
        if (!(amount >= 0 && amount <= 1)) throw new IllegalArgumentException("opacity must be from 0 to 1");
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.round(color.getAlpha() * amount));
    }

    /** Native hexadecimal representation with authored alpha when present. */
    public static String hex(Color color) {
        String rgb = String.format("#%06x", color.getRGB() & 0xffffff);
        return color.getAlpha() == 255 ? rgb : rgb + String.format("%02x", color.getAlpha());
    }
}
