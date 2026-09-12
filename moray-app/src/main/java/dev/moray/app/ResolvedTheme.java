package dev.moray.app;

import dev.moray.terminal.Palette;

import java.util.Objects;

record ResolvedTheme(BuiltinTheme chrome, Palette palette) {
    ResolvedTheme { Objects.requireNonNull(chrome); Objects.requireNonNull(palette); }
}
