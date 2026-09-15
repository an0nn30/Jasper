package dev.jasper.app;

import java.util.Objects;

record PaletteContext(boolean macOs, PaletteTarget target) {
    PaletteContext { Objects.requireNonNull(target); }
}
