package dev.jasper.editor.api;

import java.nio.file.Path;
import dev.jasper.sdk.WindowOwner;

/** Published by the Editor plugin with {@code Services.publish}. UI thread only. */
public interface Editor {
    /**
     * Opens a local file in a tab, or selects the tab already showing it, and brings the Editor window to the front.
     *
     * @param file a local file
     */
    void open(Path file);

    /**
     * Opens a document another plugin provides, or selects the tab whose document has the same key.
     *
     * @param document the document
     */
    void open(EditorDocument document);
    /** Opens in the originating workspace, or the last main workspace when null.
     * @param file local file
     * @param owner originating workspace
     */
    default void open(Path file, WindowOwner owner) { open(file); }

    /** Opens in the originating workspace, preserving an already open document's host.
     * @param document document to open
     * @param owner originating workspace, or null
     */
    default void open(EditorDocument document, WindowOwner owner) { open(document); }
}
