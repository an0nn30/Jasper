package dev.jasper.ui.themeimport;

import dev.jasper.ui.theme.TerminalColors;
import java.awt.Color;
import java.util.List;

/** Exact standalone Jasper terminal fallbacks used by legacy import formats. */
final class ImportPalettes {
    private ImportPalettes() {}

    /** Returns the standalone dark palette (ported from the TermLab Dark xterm.js theme). */
    static TerminalColors dark() {
        return new TerminalColors(
            new Color(0x282a36), new Color(0xf8f8f2), new Color(0xf8f8f2), new Color(0x44475a),
            List.of(
                new Color(0x21222c), new Color(0xff5555), new Color(0x50fa7b), new Color(0xf1fa8c),
                new Color(0xbd93f9), new Color(0xff79c6), new Color(0x8be9fd), new Color(0xf8f8f2),
                new Color(0x6272a4), new Color(0xff6e6e), new Color(0x69ff94), new Color(0xffffa5),
                new Color(0xd6acff), new Color(0xff92df), new Color(0xa4ffff), new Color(0xffffff)));
    }

    /** Returns the standalone light palette. */
    static TerminalColors light() {
        return new TerminalColors(
            new Color(0xfafafa), new Color(0x383a42), new Color(0x526fff), new Color(0xd5def5),
            List.of(
                new Color(0x383a42), new Color(0xe45649), new Color(0x50a14f), new Color(0xc18401),
                new Color(0x4078f2), new Color(0xa626a4), new Color(0x0184bc), new Color(0xa0a1a7),
                new Color(0x696c77), new Color(0xca4035), new Color(0x3d7d3b), new Color(0x986801),
                new Color(0x315fc4), new Color(0x87218b), new Color(0x006b96), new Color(0xffffff)));
    }

}
