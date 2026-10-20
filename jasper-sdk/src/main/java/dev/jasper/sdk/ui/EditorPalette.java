package dev.jasper.sdk.ui;

import java.awt.Color;
import java.util.Objects;

/** Host-owned editor surface colors; syntax token classification remains the plugin's responsibility.
 * @param background document background
 * @param foreground plain text
 * @param caret caret color
 * @param selection selected text background
 * @param currentLine current line background
 * @param gutter line-number gutter background
 * @param lineNumbers line-number foreground
 * @since 0.8.5
 */
public record EditorPalette(Color background, Color foreground, Color caret, Color selection,
                            Color currentLine, Color gutter, Color lineNumbers) {
    /** Validates that every semantic color is present. */
    public EditorPalette {
        Objects.requireNonNull(background); Objects.requireNonNull(foreground); Objects.requireNonNull(caret);
        Objects.requireNonNull(selection); Objects.requireNonNull(currentLine); Objects.requireNonNull(gutter);
        Objects.requireNonNull(lineNumbers);
    }

}
