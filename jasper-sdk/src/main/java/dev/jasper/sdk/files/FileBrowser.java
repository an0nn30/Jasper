package dev.jasper.sdk.files;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Browses one root of a {@link FileSource}. The host calls {@link #home}, {@link #list}, {@link #stat}, {@link #read},
 * {@link #mkdir}, {@link #resolve} and {@link #close} on one of its worker threads, one call at a time, never on the UI
 * thread; they may block. {@link #abort} may be called from any thread. {@link #parent} and {@link #name} must be pure
 * and may be called on any thread. Paths are absolute and use {@code /}.
 *
 * @since 0.10.0
 */
public interface FileBrowser extends AutoCloseable {
    /**
     * The folder to open when nothing else is known, such as the login's home.
     *
     * @return an absolute path
     * @throws IOException when it cannot be found
     */
    String home() throws IOException;

    /**
     * Lists a folder.
     *
     * @param directory an absolute path
     * @param visitor   receives each entry except {@code .} and {@code ..}
     * @throws IOException when the folder cannot be listed
     */
    void list(String directory, Consumer<FileEntry> visitor) throws IOException;

    /**
     * Looks up one path.
     *
     * @param path an absolute path
     * @return its entry, or empty when nothing is there
     * @throws IOException when it cannot be checked
     */
    Optional<FileEntry> stat(String path) throws IOException;

    /**
     * Reads the start of a file for the preview pane.
     *
     * @param path     an absolute path of a file
     * @param maxBytes at most this many bytes are returned
     * @return the first bytes
     * @throws IOException when the file cannot be read
     */
    byte[] read(String path, int maxBytes) throws IOException;

    /**
     * Creates a folder whose parent exists.
     *
     * @param path an absolute path
     * @throws IOException for example when something already has that name
     */
    void mkdir(String path) throws IOException;

    /** Interrupts a call in progress; safe from any thread. */
    void abort();

    /** Releases the browser; the host calls it once, when the chooser closes. */
    @Override void close();

    /**
     * The folder containing a path.
     *
     * @param path an absolute path
     * @return its parent, or empty for {@code /}
     */
    default Optional<String> parent(String path) {
        String clean = normalize(path);
        if (clean.equals("/")) return Optional.empty();
        int slash = clean.lastIndexOf('/');
        return Optional.of(slash == 0 ? "/" : clean.substring(0, slash));
    }

    /**
     * The last element of a path.
     *
     * @param path an absolute path
     * @return its name, or {@code /} for the root
     */
    default String name(String path) {
        String clean = normalize(path);
        return clean.equals("/") ? "/" : clean.substring(clean.lastIndexOf('/') + 1);
    }

    /**
     * Resolves what the user typed against the folder shown: an absolute path, {@code ~} or {@code ~/...} against
     * {@code home}, or a relative path; {@code .}, {@code ..} and repeated slashes are removed.
     *
     * @param folder the absolute folder shown
     * @param text   what the user typed
     * @param home   this browser's {@link #home()}
     * @return an absolute path
     * @throws IllegalArgumentException when the text cannot be a path here
     */
    default String resolve(String folder, String text, String home) {
        String joined = text.equals("~") ? home : text.startsWith("~/") ? home + "/" + text.substring(2)
            : text.startsWith("/") ? text : folder + "/" + text;
        return normalize(joined);
    }

    /**
     * Normalizes a {@code /} path.
     *
     * @param path a path using {@code /}
     * @return it absolute, without empty, {@code .} or {@code ..} elements
     */
    static String normalize(String path) {
        var parts = new ArrayDeque<String>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (!parts.isEmpty()) parts.removeLast(); }
            else parts.addLast(part);
        }
        return "/" + String.join("/", parts);
    }
}
