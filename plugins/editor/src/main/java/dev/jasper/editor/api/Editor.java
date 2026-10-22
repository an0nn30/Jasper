package dev.jasper.editor.api;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Published by the Editor plugin with {@code Services.publish}; documents open as tabs in Jasper windows. UI thread
 * only.
 */
public interface Editor {
    /**
     * Opens a local file in a new tab of the main window, or selects the tab already showing it (of several, the one
     * focused last), and brings that tab's window to the front.
     *
     * @param file a local file
     */
    void open(Path file);

    /**
     * Opens a document another plugin provides in a new tab of the main window, or selects the tab whose document has the
     * same key (of several, the one focused last), and brings that tab's window to the front.
     *
     * @param document the document
     */
    void open(EditorDocument document);
    /** Opens a local file in a new tab of the window the request came from, or of the main window when {@code owner} is
     * null; a file already open is selected in its tab, wherever that is (of several, the one focused last). The
     * interface default ignores {@code owner}.
     * @param file local file
     * @param owner the Jasper window or content tab the request came from, or null
     */
    default void open(Path file, WindowOwner owner) { open(file); }

    /** Opens a document in a new tab of the window the request came from, or of the main window when {@code owner} is
     * null; a document whose key is already open is selected in its tab, wherever that is (of several, the one focused
     * last). The interface default ignores {@code owner}.
     * @param document document to open
     * @param owner the Jasper window or content tab the request came from, or null
     */
    default void open(EditorDocument document, WindowOwner owner) { open(document); }

    /**
     * Makes recent files under a key prefix reopenable. The resolver runs on the UI thread and must do no I/O. It
     * returns empty when the key cannot be reopened now, for example because its host was deleted, and otherwise a
     * factory that builds the document for the window reopening it (null when there is none). A later registration
     * of the same prefix replaces the earlier one; the longest matching prefix wins. A resolver that throws or answers
     * null cannot reopen the key.
     *
     * @param keyPrefix the start of the keys this plugin's documents use, for example {@code "remote:"}; not blank and
     *     not starting with {@code "local:"}, which the Editor keeps for local files ({@link IllegalArgumentException}
     *     otherwise)
     * @param resolve resolves a recorded key
     * @return removes the reopener
     */
    default Subscription reopener(String keyPrefix, Function<String, Optional<Function<WindowOwner, EditorDocument>>> resolve) {
        return () -> { };
    }

    /**
     * Lets a plugin answer Open in Editor… in its own way, for example by browsing a remote host. Handlers run on the
     * UI thread in registration order before the local file chooser; the first to return true has handled the
     * request. A handler that throws counts as false.
     *
     * @param handler answers a request; true when it handled it
     * @return removes the handler
     */
    default Subscription opener(Predicate<OpenRequest> handler) {
        return () -> { };
    }

    /**
     * Lets Open in Editor… and Save As offer a file source's places and open or save what the user picks there. The
     * chooser offers the source's roots only while a resolver is registered; a later registration for the same source
     * replaces the earlier one.
     *
     * @param sourceId the id of a file source the plugin registered; not blank ({@link IllegalArgumentException}
     *     otherwise)
     * @param resolver builds documents for the source's files
     * @return removes the resolver
     */
    default Subscription locationDocuments(String sourceId, LocationResolver resolver) {
        return () -> { };
    }
}
