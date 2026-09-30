package dev.jasper.editor.api;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileLocation;
import java.util.Optional;

/**
 * Turns a file picked in Jasper's file chooser from one of a plugin's file sources into a document, and tells the Editor
 * where one of its documents is. Called on the UI thread; no I/O. Since Editor 0.6.0.
 */
public interface LocationResolver {
    /**
     * @param location a file in one of the source's roots
     * @param owner the window the Editor opens it in
     * @return the document to open
     */
    EditorDocument document(FileLocation.Sourced location, WindowOwner owner);

    /**
     * @param documentKey the key of a document the Editor has open
     * @return where that document is in this source, or empty when it is not this source's
     */
    Optional<FileLocation.Sourced> location(String documentKey);
}
