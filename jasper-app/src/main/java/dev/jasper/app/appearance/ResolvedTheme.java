package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SlateSteel;
import dev.jasper.ui.theme.TerminalScheme;
import java.awt.Color;
import java.util.Objects;

/** The immutable chrome and workspace values and the terminal palette, published together on the EDT. */
public record ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace, Palette palette) {
    public ResolvedTheme { Objects.requireNonNull(chrome); Objects.requireNonNull(workspace); Objects.requireNonNull(palette); }

    /** A legacy fixture pair: the terminal palette is the workspace theme's own, bold text in its foreground. */
    public ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace) {
        this(chrome, workspace, TerminalPaletteAdapter.toTerminal(workspace.terminal()));
    }

    /** Slate Steel: one variant for chrome and workspace, and the terminal scheme's palette. */
    public static ResolvedTheme slateSteel(boolean dark, TerminalScheme scheme) {
        var theme = SlateSteel.bundled().generated(dark);
        return new ResolvedTheme(theme, theme, TerminalPaletteAdapter.toTerminal(SlateSteel.bundled().terminal(scheme)));
    }

    public dev.jasper.ui.theme.ThemePair pair() { return new dev.jasper.ui.theme.ThemePair(chrome, workspace); }
    public boolean matching() { return chrome == workspace; }

    /** The workspace surface around terminal panes and under tabs; a terminal scheme colours only the text area. */
    public Color surface() { return workspace.semantic().color(SemanticColor.SURFACE_TERMINAL); }
}
