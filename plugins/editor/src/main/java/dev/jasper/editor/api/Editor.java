package dev.jasper.editor.api;

import java.nio.file.Path;

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
}
