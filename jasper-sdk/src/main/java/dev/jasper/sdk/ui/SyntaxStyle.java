package dev.jasper.sdk.ui;

import java.awt.Color;
import java.util.Objects;

/** The appearance of one syntax role.
 * @param color text color
 * @param bold whether text is bold
 * @param italic whether text is italic
 * @since 0.12.0
 */
public record SyntaxStyle(Color color, boolean bold, boolean italic) {
    /** Validates the text color. */
    public SyntaxStyle { Objects.requireNonNull(color, "color"); }
}
