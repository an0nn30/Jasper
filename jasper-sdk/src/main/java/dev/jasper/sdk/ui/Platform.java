package dev.jasper.sdk.ui;

import java.net.URI;
import java.nio.file.Path;

/**
 * Desktop integration the application already does for its own files and links. Implemented by the
 * application and by the testkit.
 */
public interface Platform {
    /**
     * Opens a file in the user's editor: the configured editor, then the desktop's edit or open
     * action, then revealing the file. Runs in the background; a failure is reported through
     * {@link Notices}, never thrown.
     *
     * @param file the file
     */
    void openInEditor(Path file);

    /**
     * Opens a web address in the user's browser. Runs in the background; a failure is reported
     * through {@link Notices}, never thrown.
     *
     * @param uri an absolute {@code http} or {@code https} address
     * @throws IllegalArgumentException when the address is not an absolute http or https one
     * @since 0.10.2
     */
    void browse(URI uri);
}
