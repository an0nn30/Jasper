package dev.jasper.editor.api;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileLocation;
import java.util.Optional;

/**
 * Turns a file picked in Jasper's file chooser from one of a plugin's file sources into a document, and tells the Editor
 * where one of its documents is. Called on the UI thread; no I/O.
 */
public interface LocationResolver {
    /**
     * The document for a file the user picked in this source: in Open in Editor…, which opens it, or in Save As, which
     * writes to it, so the file may not exist yet. A resolver that throws makes the Editor post a "Could not open a
     * file" or "Could not save a file" notification with the exception's message.
     *
     * @param location a file in one of the source's roots
     * @param owner the window the Editor opens it in, or saves it from
     * @return the document to open or write
     */
    EditorDocument document(FileLocation.Sourced location, WindowOwner owner);

    /**
     * Where an open document is in this source, so Open in Editor… and Save As can start in its folder. The Editor asks
     * every registered resolver; an answer counts only when it names this resolver's source, and a resolver that
     * throws counts as not knowing.
     *
     * @param documentKey the key of a document the Editor has open
     * @return where that document is in this source, or empty when it is not this source's
     */
    Optional<FileLocation.Sourced> location(String documentKey);
}
