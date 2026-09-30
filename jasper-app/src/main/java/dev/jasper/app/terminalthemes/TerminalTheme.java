package dev.jasper.app.terminalthemes;

import dev.jasper.terminal.config.Palette;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * One terminal theme.
 *
 * @param name its display name
 * @param palette its colours
 * @param file the user's file it comes from; empty for a bundled theme
 */
public record TerminalTheme(String name, Palette palette, Optional<Path> file) {
    public TerminalTheme { Objects.requireNonNull(name); Objects.requireNonNull(palette); Objects.requireNonNull(file); }

    /** Whether it ships with Jasper. */
    public boolean bundled() { return file.isEmpty(); }
}
