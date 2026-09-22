package dev.jasper.sdk.ui;

import java.nio.file.Path;

/**
 * Desktop integration the application already does for its own files. Implemented by the application
 * and by the testkit.
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
}
