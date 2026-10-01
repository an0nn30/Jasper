package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.util.Objects;

/** The immutable chrome and workspace values published together on the EDT. */
public record ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace) {
    public ResolvedTheme { Objects.requireNonNull(chrome); Objects.requireNonNull(workspace); }
    public Palette palette() { return workspace.terminal(); }
    public boolean matching() { return chrome == workspace; }
}
