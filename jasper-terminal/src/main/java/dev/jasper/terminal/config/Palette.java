package dev.jasper.terminal.config;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/**
 * Immutable colors: theme defaults, 16 ANSI entries and the derived xterm 256-color table.
 * @param foreground default text color
 * @param background default screen color
 * @param cursor cursor color
 * @param selection selected-cell background
 * @param ansi exactly 16 nonnull colors, copied on construction
 */
public record Palette(Color foreground, Color background, Color cursor, Color selection, List<Color> ansi) {

    /** Checks nonnull colors and exactly 16 ANSI entries, then copies the list. */
    public Palette {
        Objects.requireNonNull(foreground, "foreground");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(cursor, "cursor");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(ansi, "ansi");
        if (ansi.size() != 16) {
            throw new IllegalArgumentException("ansi must have 16 colors, got " + ansi.size());
        }
        for (int i = 0; i < ansi.size(); i++) {
            Objects.requireNonNull(ansi.get(i), "ansi[" + i + "]");
        }
        ansi = List.copyOf(ansi);
    }

    /** Returns the standalone dark palette (ported from the TermLab Dark xterm.js theme). */
    public static Palette jasperDark() {
        return new Palette(
            new Color(0xf8f8f2), new Color(0x282a36), new Color(0xf8f8f2), new Color(0x44475a),
            List.of(
                new Color(0x21222c), new Color(0xff5555), new Color(0x50fa7b), new Color(0xf1fa8c),
                new Color(0xbd93f9), new Color(0xff79c6), new Color(0x8be9fd), new Color(0xf8f8f2),
                new Color(0x6272a4), new Color(0xff6e6e), new Color(0x69ff94), new Color(0xffffa5),
                new Color(0xd6acff), new Color(0xff92df), new Color(0xa4ffff), new Color(0xffffff)));
    }

    /** Returns the standalone light palette. */
    public static Palette jasperLight() {
        return new Palette(
            new Color(0x383a42), new Color(0xfafafa), new Color(0x526fff), new Color(0xd5def5),
            List.of(
                new Color(0x383a42), new Color(0xe45649), new Color(0x50a14f), new Color(0xc18401),
                new Color(0x4078f2), new Color(0xa626a4), new Color(0x0184bc), new Color(0xa0a1a7),
                new Color(0x696c77), new Color(0xca4035), new Color(0x3d7d3b), new Color(0x986801),
                new Color(0x315fc4), new Color(0x87218b), new Color(0x006b96), new Color(0xffffff)));
    }

    /** xterm color index 0–255. */
    public Color indexed(int index) {
        if (index < 16) {
            return ansi.get(index);
        }
        if (index < 232) {
            int i = index - 16;
            return new Color(cubeLevel(i / 36), cubeLevel((i / 6) % 6), cubeLevel(i % 6));
        }
        int gray = 8 + 10 * (index - 232);
        return new Color(gray, gray, gray);
    }

    private static int cubeLevel(int n) {
        return n == 0 ? 0 : 55 + 40 * n;
    }
}
