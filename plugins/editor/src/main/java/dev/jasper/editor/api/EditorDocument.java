package dev.jasper.editor.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A file the editor reads and writes through whoever provides it. The editor calls these methods on the
 * UI thread; none may block. Futures may complete on any thread.
 */
public interface EditorDocument {
    /** @return stable identity: two documents with equal keys are the same file */
    String key();

    /** @return the file name, used as the tab title */
    String name();

    /** @return the full location for tooltips and the status line, for example {@code dustin@prod-web:/etc/nginx/nginx.conf} */
    String location();

    /** @return the host a remote document lives on, shown dimmed after the name; empty for a local document */
    Optional<String> host();

    /**
     * Reads the whole file. Fails with {@link TooLargeException}, before transferring content, when it is
     * larger than {@code limitBytes}. Cancelling the future aborts the transfer.
     *
     * @param limitBytes the largest size the editor accepts
     * @return the content and the revision it was read at
     */
    CompletableFuture<Content> read(long limitBytes);

    /**
     * Reads the whole file as {@link #read} does, without asking the user anything: a document that would have to (a sign-in
     * prompt) fails instead and says so. The Editor reads a document this way when Jasper has restored its tab and it is
     * first shown; Retry then reads it with {@link #read}. Reads as {@link #read} does unless the document asks.
     *
     * @param limitBytes the largest size the editor accepts
     * @return the content and the revision it was read at
     */
    default CompletableFuture<Content> readQuietly(long limitBytes) { return read(limitBytes); }

    /** @return the file's current revision, or empty when it no longer exists */
    CompletableFuture<Optional<Revision>> revision();

    /**
     * Replaces the file's content: resolve symbolic links to the file they name; write a hidden sibling, give
     * it the target's permissions and atomically replace the target; write in place instead when the sibling
     * cannot be created, would change the owner or group, when the target has more than one hard link, or
     * when an atomic replacement is unavailable; create a missing target; then stat it.
     *
     * @param content the new bytes; the receiver may keep the array
     * @return the new revision
     */
    CompletableFuture<Revision> write(byte[] content);
}
