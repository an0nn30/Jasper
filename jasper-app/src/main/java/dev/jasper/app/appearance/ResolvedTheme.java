package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;

import java.util.Objects;

/** Immutable chrome and terminal-palette resolution published on EDT; owns no Swing components or native resources. */
public record ResolvedTheme(BuiltinTheme chrome, Palette palette) {
    public ResolvedTheme { Objects.requireNonNull(chrome); Objects.requireNonNull(palette); }
}
