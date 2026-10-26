package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.TerminalScheme;
import java.awt.Color;
import java.util.Objects;

/** The immutable chrome and workspace values and the terminal palette, published together on the EDT. */
public record ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace, Palette palette) {
    public ResolvedTheme { Objects.requireNonNull(chrome); Objects.requireNonNull(workspace); Objects.requireNonNull(palette); }

    /** The theme {@code selection} shows with the Dark or Light appearance, for chrome and workspace, and its terminal scheme's palette. */
    public static ResolvedTheme of(boolean dark, ThemeSelection selection) {
        return of(dark, selection, JasperTheme.bundled().generated(dark));
    }

    /** Resolves a dynamic pick against the complete generated desktop palette for this appearance. */
    public static ResolvedTheme of(boolean dark, ThemeSelection selection, GeneratedTheme desktop) {
        var theme = selection.theme(dark).generated(dark, desktop);
        return new ResolvedTheme(theme, theme, TerminalPaletteAdapter.toTerminal(selection.terminal(dark)));
    }

    /** Slate Cobalt: one variant for chrome and workspace, and {@code scheme}'s palette. */
    public static ResolvedTheme slateCobalt(boolean dark, TerminalScheme scheme) {
        var theme = JasperTheme.bundled().generated(dark);
        return new ResolvedTheme(theme, theme, TerminalPaletteAdapter.toTerminal(scheme));
    }

    public dev.jasper.ui.theme.ThemePair pair() { return new dev.jasper.ui.theme.ThemePair(chrome, workspace); }
    public boolean matching() { return chrome == workspace; }

    /** The workspace surface around terminal panes and under tabs; a terminal scheme colours only the text area. */
    public Color surface() { return workspace.semantic().color(SemanticColor.SURFACE_TERMINAL); }
}
