package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import dev.jasper.ui.theme.TerminalColors;
import dev.jasper.ui.theme.TerminalScheme;
import java.awt.Color;

/** Lossless module-to-terminal value boundary; constructor arguments are mapped by meaning. */
public final class TerminalPaletteAdapter {
    private TerminalPaletteAdapter() {}
    /** A theme's terminal scheme, its bold foreground included. */
    public static Palette toTerminal(TerminalScheme scheme) { return toTerminal(scheme.colors(), scheme.boldForeground()); }

    /** Terminal colours and the foreground for bold text. */
    public static Palette toTerminal(TerminalColors colors, Color boldForeground) {
        return new Palette(colors.foreground(), colors.background(), colors.cursor(), colors.selection(), colors.ansi(), boldForeground);
    }
}
