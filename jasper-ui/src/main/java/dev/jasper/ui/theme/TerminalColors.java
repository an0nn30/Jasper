package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/**
 * Immutable terminal theme colors, independent of any terminal implementation.
 * @param background default screen color
 * @param foreground default text color
 * @param cursor cursor color
 * @param selection selected-cell background
 * @param ansi exactly 16 nonnull colors, copied on construction
 */
public record TerminalColors(Color background, Color foreground, Color cursor, Color selection, List<Color> ansi) {
    /** Checks nonnull colors and exactly 16 ANSI entries, then copies the list. */
    public TerminalColors {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(foreground, "foreground");
        Objects.requireNonNull(cursor, "cursor");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(ansi, "ansi");
        if (ansi.size() != 16) {
            throw new IllegalArgumentException("ansi must have 16 colors, got " + ansi.size());
        }
        ansi = List.copyOf(ansi);
    }
}
