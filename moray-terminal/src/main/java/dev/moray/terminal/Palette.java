package dev.moray.terminal;

import com.jediterm.terminal.TerminalColor;

import java.awt.Color;
import java.util.List;

/** Terminal colors: theme defaults, the 16 ANSI colors, and the xterm 256-color table. */
public record Palette(Color foreground, Color background, Color cursor, Color selection, List<Color> ansi) {

    public Palette {
        if (ansi.size() != 16) {
            throw new IllegalArgumentException("ansi must have 16 colors, got " + ansi.size());
        }
        ansi = List.copyOf(ansi);
    }

    public static Palette morayDark() {
        return new Palette(
            new Color(0xd7dae0), new Color(0x1e2127), new Color(0xd7dae0), new Color(0x3e4451),
            List.of(
                new Color(0x282c34), new Color(0xe06c75), new Color(0x98c379), new Color(0xe5c07b),
                new Color(0x61afef), new Color(0xc678dd), new Color(0x56b6c2), new Color(0xabb2bf),
                new Color(0x5c6370), new Color(0xef7b85), new Color(0xa9d48a), new Color(0xf0cc8c),
                new Color(0x74bff8), new Color(0xd68bee), new Color(0x67c7d3), new Color(0xe6e9ef)));
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
