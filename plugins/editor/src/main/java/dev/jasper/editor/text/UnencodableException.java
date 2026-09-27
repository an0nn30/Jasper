package dev.jasper.editor.text;

import java.io.IOException;
import java.nio.charset.Charset;

/** The text holds a character its charset cannot store. */
public final class UnencodableException extends IOException {
    private final Charset charset;
    private final int line;

    /**
     * @param charset the charset that cannot store it
     * @param line the first line holding such a character, from 1
     */
    public UnencodableException(Charset charset, int line) {
        super("Line " + line + " has a character " + charset.name() + " cannot store");
        this.charset = charset;
        this.line = line;
    }

    /** @return the charset */
    public Charset charset() { return charset; }

    /** @return the first line holding such a character, from 1 */
    public int line() { return line; }
}
