package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;

import java.util.Objects;

public record ResolvedTheme(BuiltinTheme chrome, Palette palette) {
    public ResolvedTheme { Objects.requireNonNull(chrome); Objects.requireNonNull(palette); }
}
