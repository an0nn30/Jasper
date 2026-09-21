package dev.jasper.app;

import dev.jasper.terminal.config.Palette;

import java.util.Objects;

record ResolvedTheme(BuiltinTheme chrome, Palette palette) {
    ResolvedTheme { Objects.requireNonNull(chrome); Objects.requireNonNull(palette); }
}
