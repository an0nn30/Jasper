package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.Objects;

/**
 * A terminal scheme's colours: the terminal palette and the foreground for bold or emphasized text.
 * @param colors background, foreground, cursor, selection and the 16 ANSI colours
 * @param boldForeground the default foreground for bold text
 */
public record SchemeColors(TerminalColors colors, Color boldForeground) {
    public SchemeColors { Objects.requireNonNull(colors, "colors"); Objects.requireNonNull(boldForeground, "boldForeground"); }
}
