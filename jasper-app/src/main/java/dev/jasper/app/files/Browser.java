package dev.jasper.app.files;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * File access for one root. The model calls every method except {@link #abort()}, {@link #parent} and {@link #name}
 * on its worker, one call at a time; those three are safe on any thread. Two implementations: {@link LocalBrowser} and
 * the plugin adapter in {@code dev.jasper.app.plugins}.
 */
public interface Browser extends AutoCloseable {
    /** @return the folder a root opens at */
    String home() throws IOException;

    /** Lists a folder; each entry's path is absolute. */
    void list(String folder, Consumer<ChooserEntry> visitor) throws IOException;

    /** @return the entry at {@code path}, or empty when nothing is there */
    Optional<ChooserEntry> stat(String path) throws IOException;

    /** @return at most {@code maxBytes} from the start of a file */
    byte[] read(String path, int maxBytes) throws IOException;

    /** Creates a folder whose parent exists. */
    void mkdir(String path) throws IOException;

    /** @return the parent folder, or empty at a root; pure */
    Optional<String> parent(String path);

    /** @return the last path element, or the whole path at a root; pure */
    String name(String path);

    /**
     * Resolves typed text against the folder shown: {@code ~}, {@code ~/...}, absolute or relative.
     *
     * @throws IllegalArgumentException when the text cannot be a path here
     */
    String resolve(String folder, String text) throws IOException;

    /** Interrupts a call in progress; any thread. */
    void abort();

    @Override void close();
}
