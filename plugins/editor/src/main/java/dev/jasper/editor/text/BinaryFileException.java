package dev.jasper.editor.text;

import java.io.IOException;

/** The bytes look like a binary file: a NUL byte near the start and no UTF-16 byte-order mark. */
public final class BinaryFileException extends IOException {
    /** Creates the exception. */
    public BinaryFileException() { super("This looks like a binary file"); }
}
