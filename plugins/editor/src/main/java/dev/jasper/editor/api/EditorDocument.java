package dev.jasper.editor.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A file the editor reads and writes through whoever provides it. The editor calls these methods on the
 * UI thread; none may block. Futures may complete on any thread.
 */
public interface EditorDocument {
    /**
     * The document's stable identity. Opening a document whose key is already open selects that tab; Recent Files
     * records the key, and {@link Editor#reopener} resolves it by its prefix. Start it with a prefix of the plugin's
     * own, the one it passes to {@link Editor#reopener} (for example {@code "remote:"}), never {@code "local:"}, which
     * the Editor uses for local files.
     *
     * @return stable identity: two documents with equal keys are the same file
     */
    String key();

    /**
     * The file name: the tab title and the name in Recent Files and prompts. With the first line of the text, it
     * decides the syntax highlighting.
     *
     * @return the file name, used as the tab title
     */
    String name();

    /**
     * Where the file is, in full: shown in tooltips, the status line and Recent Files. For a document with a host,
     * the status line shows only the part from the first {@code :/} on, without the colon.
     *
     * @return the full location for tooltips and the status line, for example {@code dustin@prod-web:/etc/nginx/nginx.conf}
     */
    String location();

    /**
     * The host a remote document lives on. A document with a host gets the server icon on its tab, a host tag in
     * Recent Files and the host's name in its loading text and in the window title.
     *
     * @return the host a remote document lives on, shown dimmed after the name; empty for a local document
     */
    Optional<String> host();

    /**
     * Resolves a resource against this file's parent, retaining this handle's storage and authentication context.
     * Called on the UI thread; performs no I/O and does not open a tab. Since Editor 0.11.0.
     *
     * @param path a nonempty, already decoded relative path with {@code /} separators; dot segments are allowed
     * @return the resource handle, or empty when this provider has no relative resolution support
     * @throws IllegalArgumentException when {@link RelativePaths#requireValid(String)} rejects the path
     */
    default Optional<EditorDocument> resolveRelative(String path) {
        RelativePaths.requireValid(path);
        return Optional.empty();
    }

    /**
     * Reads the whole file. Fails with {@link TooLargeException}, before transferring content, when it is
     * larger than {@code limitBytes}. Cancelling the future aborts the transfer. The Editor words a
     * {@link TooLargeException}, {@link java.nio.file.NoSuchFileException} or
     * {@link java.nio.file.AccessDeniedException} itself and shows any other failure's message.
     *
     * @param limitBytes the largest size the editor accepts: its {@code max_file_mib} setting, in bytes
     * @return the content and the revision it was read at
     */
    CompletableFuture<Content> read(long limitBytes);

    /**
     * Reads the whole file as {@link #read} does, for a restore, which asks the user nothing. The Editor reads a document
     * this way when Jasper has restored its tab and the tab is first active or Load is pressed; Retry then reads it with
     * {@link #read}. A document whose read can ask the user anything (a sign-in, a prompt) must override this so that it
     * fails, saying why, instead of asking. The default reads as {@link #read} does.
     *
     * @param limitBytes the largest size the editor accepts
     * @return the content and the revision it was read at
     */
    default CompletableFuture<Content> readQuietly(long limitBytes) { return read(limitBytes); }

    /**
     * The file's current revision, which the Editor asks for before each Save (not Save As). When it differs from the
     * revision last read or written, the user chooses whether to overwrite or reload; when the file no longer exists,
     * whether to save anyway.
     *
     * @return the file's current revision, or empty when it no longer exists
     */
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
