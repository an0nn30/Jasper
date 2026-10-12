package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import dev.jasper.ui.theme.TerminalColors;

/** Lossless module-to-terminal value boundary; constructor arguments are mapped by meaning. */
public final class TerminalPaletteAdapter {
    private TerminalPaletteAdapter() {}
    public static Palette toTerminal(TerminalColors colors) {
        return new Palette(colors.foreground(), colors.background(), colors.cursor(), colors.selection(), colors.ansi(), colors.foreground());
    }

    /** A Slate Steel terminal scheme, its bold foreground included. */
    public static Palette toTerminal(dev.jasper.ui.theme.SchemeColors scheme) {
        var colors = scheme.colors();
        return new Palette(colors.foreground(), colors.background(), colors.cursor(), colors.selection(), colors.ansi(), scheme.boldForeground());
    }
}
