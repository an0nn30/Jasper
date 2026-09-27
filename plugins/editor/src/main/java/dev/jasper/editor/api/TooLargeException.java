package dev.jasper.editor.api;

import java.io.IOException;

/** The file is larger than the limit the editor asked for. */
public final class TooLargeException extends IOException {
    private final long size;
    private final long limit;

    /**
     * @param size the file's size in bytes
     * @param limit the limit in bytes
     */
    public TooLargeException(long size, long limit) {
        super("The file is " + size + " bytes; the limit is " + limit + " bytes");
        this.size = size;
        this.limit = limit;
    }

    /** @return the file's size in bytes */
    public long size() { return size; }

    /** @return the limit in bytes */
    public long limit() { return limit; }
}
