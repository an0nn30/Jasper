package dev.jasper.ui.theme;

import java.util.Objects;

/** Atomic chrome/workspace appearance selection. */
public record ThemePair(GeneratedTheme chrome, GeneratedTheme workspace) {
    public ThemePair { Objects.requireNonNull(chrome); Objects.requireNonNull(workspace); }
}
