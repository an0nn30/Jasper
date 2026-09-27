package dev.jasper.editor.text;

import java.util.Objects;

/**
 * A file's text with {@code \n} line breaks, the format to save it in, and whether the file mixed line endings.
 *
 * @param text the text
 * @param format how to save it
 * @param mixed whether the file had more than one kind of line ending
 */
public record DecodedText(String text, TextFormat format, boolean mixed) {
    /** Rejects nulls. */
    public DecodedText {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(format, "format");
    }
}
