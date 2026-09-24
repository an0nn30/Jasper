package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.terminal.config.Palette;

import java.util.Objects;

/** Immutable chrome and terminal-palette resolution published on EDT; owns no Swing components or native resources. */
public record ResolvedTheme(BuiltinTheme chrome, Palette palette) {
    public ResolvedTheme { Objects.requireNonNull(chrome); Objects.requireNonNull(palette); }

    /** The built-in theme's own brightness; GTK's follows its terminal background. */
    public Appearance appearance() {
        if (chrome != BuiltinTheme.GTK) return chrome.appearance();
        return GtkPalette.dark(palette.background()) ? Appearance.DARK : Appearance.LIGHT;
    }

    public boolean dark() { return appearance() == Appearance.DARK; }
}
