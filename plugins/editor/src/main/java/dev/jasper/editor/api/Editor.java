package dev.jasper.editor.api;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

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

    /**
     * Makes recent files under a key prefix reopenable. The resolver runs on the UI thread and must do no I/O. It
     * returns empty when the key cannot be reopened now, for example because its host was deleted, and otherwise a
     * factory that builds the document for the window reopening it (null when there is none). A later registration
     * of the same prefix replaces the earlier one; the longest matching prefix wins. Since Editor 0.4.0.
     *
     * @param keyPrefix the start of the keys this plugin's documents use, for example {@code "remote:"}; not blank and
     *     not {@code "local:"}
     * @param resolve resolves a recorded key
     * @return removes the reopener
     */
    default Subscription reopener(String keyPrefix, Function<String, Optional<Function<WindowOwner, EditorDocument>>> resolve) {
        return () -> { };
    }

    /**
     * Lets a plugin answer Open in Editor… in its own way, for example by browsing a remote host. Handlers run on the
     * UI thread in registration order before the local file chooser; the first to return true has handled the
     * request. A handler that throws counts as false. Since Editor 0.5.0.
     *
     * @param handler answers a request; true when it handled it
     * @return removes the handler
     */
    default Subscription opener(Predicate<OpenRequest> handler) {
        return () -> { };
    }
}
