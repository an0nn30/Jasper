package dev.moray.terminal;

import com.jediterm.terminal.TerminalColor;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/** Terminal colors: theme defaults, the 16 ANSI colors, and the xterm 256-color table. */
public record Palette(Color foreground, Color background, Color cursor, Color selection, List<Color> ansi) {

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

    public static Palette morayDark() {
        return new Palette(
            new Color(0xabb2bf), new Color(0x292c34), new Color(0xb3bbc7), new Color(0x3e4451),
            List.of(
                new Color(0x282c34), new Color(0xe06c75), new Color(0xa8c58d), new Color(0xe5c07b),
                new Color(0x80b4df), new Color(0xc678dd), new Color(0x56b6c2), new Color(0xabb2bf),
                new Color(0x5c6370), new Color(0xef7b85), new Color(0xa9d48a), new Color(0xf0cc8c),
                new Color(0x74bff8), new Color(0xd68bee), new Color(0x67c7d3), new Color(0xe6e9ef)));
    }

    public static Palette morayLight() {
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

    Color foreground(TerminalColor color) {
        return color == null ? foreground : resolve(color);
    }

    Color background(TerminalColor color) {
        return color == null ? background : resolve(color);
    }

    private Color resolve(TerminalColor color) {
        if (color.isIndexed()) {
            return indexed(color.getColorIndex());
        }
        com.jediterm.core.Color c = color.toColor();
        return new Color(c.getRed(), c.getGreen(), c.getBlue());
    }

    private static int cubeLevel(int n) {
        return n == 0 ? 0 : 55 + 40 * n;
    }
}
