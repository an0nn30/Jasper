package dev.jasper.editor.text;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * How a file's text is stored.
 *
 * @param charset the charset
 * @param bom whether a byte-order mark starts the file
 * @param separator the line separator written between lines
 */
public record TextFormat(Charset charset, boolean bom, LineSeparator separator) {
    /** UTF-8 without a byte-order mark, with LF: new and empty files. */
    public static final TextFormat DEFAULT = new TextFormat(StandardCharsets.UTF_8, false, LineSeparator.LF);

    /** Rejects nulls. */
    public TextFormat {
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(separator, "separator");
    }

    /** @return the status line's encoding label, such as {@code UTF-8} or {@code UTF-16LE BOM} */
    public String label() { return charset.name() + (bom ? " BOM" : ""); }
}
