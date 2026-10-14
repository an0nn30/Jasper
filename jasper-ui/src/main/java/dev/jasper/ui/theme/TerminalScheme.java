package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.Objects;

/**
 * One of a theme's terminal schemes.
 * @param id its theme's id and its key
 * @param name the name Settings and the command palette show
 * @param colors background, foreground, cursor, selection and the 16 ANSI colours
 * @param boldForeground the default foreground for bold text
 */
public record TerminalScheme(SchemeId id, String name, TerminalColors colors, Color boldForeground) {
    public TerminalScheme {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(colors, "colors"); Objects.requireNonNull(boldForeground, "boldForeground");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A terminal scheme needs a name");
    }
}
