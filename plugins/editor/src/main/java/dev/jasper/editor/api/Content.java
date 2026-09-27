package dev.jasper.editor.api;

import java.util.Objects;

/**
 * A file's bytes and the revision they were read at. The array passes to the receiver; the provider must not reuse it.
 *
 * @param bytes the file's content
 * @param revision when it was read
 */
public record Content(byte[] bytes, Revision revision) {
    /** Rejects nulls. */
    public Content {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(revision, "revision");
    }
}
