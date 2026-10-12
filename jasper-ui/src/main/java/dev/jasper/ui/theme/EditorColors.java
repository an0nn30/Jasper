package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.Objects;

/**
 * An editor scheme's surfaces.
 * @param background document background
 * @param foreground plain text
 * @param caret caret
 * @param selection selected text background
 * @param currentLine current line background
 * @param gutter line-number gutter background
 * @param lineNumbers line-number foreground
 */
public record EditorColors(Color background, Color foreground, Color caret, Color selection, Color currentLine,
                           Color gutter, Color lineNumbers) {
    public EditorColors {
        Objects.requireNonNull(background); Objects.requireNonNull(foreground); Objects.requireNonNull(caret);
        Objects.requireNonNull(selection); Objects.requireNonNull(currentLine); Objects.requireNonNull(gutter);
        Objects.requireNonNull(lineNumbers);
    }
}
