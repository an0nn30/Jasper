package dev.jasper.editor.api;

import java.io.IOException;

/** The file is larger than the limit the editor asked for. */
public final class TooLargeException extends IOException {
    /** The file's size in bytes. */
    private final long size;
    /** The limit in bytes. */
    private final long limit;

    /**
     * Creates the exception with the message "The file is <i>size</i> bytes; the limit is <i>limit</i> bytes".
     *
     * @param size the file's size in bytes
     * @param limit the limit in bytes
     */
    public TooLargeException(long size, long limit) {
        super("The file is " + size + " bytes; the limit is " + limit + " bytes");
        this.size = size;
        this.limit = limit;
    }

    /**
     * The file's size; the Editor shows it with the limit when it refuses the file.
     *
     * @return the file's size in bytes
     */
    public long size() { return size; }

    /**
     * The limit the read was given.
     *
     * @return the limit in bytes
     */
    public long limit() { return limit; }
}
