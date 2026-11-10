package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Opens the files Jasper opens for editing, once a plugin registers it with {@link Platform#editor}.
 *
 * @since 0.17.1
 */
@FunctionalInterface
public interface FileEditor {
    /**
     * Opens a file. UI thread; it must not block.
     *
     * @param file the file
     * @param line the line to show, from 1; empty for the file's start
     * @param window the Jasper window the open came from, as this plugin's handle; empty when it came from elsewhere
     * @return true when it opened the file; false hands the file to the desktop
     */
    boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window);

    /**
     * Opens a file and says where the open came from. Jasper calls this one; an editor overrides it to tell the
     * {@link FileOrigin origins} apart. UI thread; it must not block.
     *
     * @param file the file
     * @param line the line to show, from 1; empty for the file's start
     * @param window the Jasper window the open came from, as this plugin's handle; empty when it came from elsewhere
     * @param origin where the open came from
     * @return true when it opened the file; false hands the file to the desktop
     * @throws NullPointerException when {@code origin} is null
     * @since 0.31.0
     */
    default boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window, FileOrigin origin) {
        Objects.requireNonNull(origin, "origin");
        return edit(file, line, window);
    }
}
