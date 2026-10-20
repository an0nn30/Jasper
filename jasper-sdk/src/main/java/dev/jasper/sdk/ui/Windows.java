package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileLocation;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Application-built windows and dialogs with consistent chrome. */
public interface Windows {
    /** Create a nonblocking popup attached to a host-issued live capability.
     * @param anchor attachment authority from this host @return initially hidden popup */
    PluginPopover popover(UiAnchor anchor);
    /**
     * Opens an owned view as a content tab at the end of the active group of the owner's window's shown parent tab
     * ({@link TabPlacement#ACTIVE_GROUP}); moving retains it, final closure disposes it.
     *
     * @param spec the tab's key and title
     * @param owner the window the tab opens in, or a content tab whose window it opens in; null, or a closed window, for
     *              the main window
     * @param content the plugin's owned view
     * @return the new tab, or the open tab with the same key, selected
     */
    default ContentTab openTab(ContentSpec spec, WindowOwner owner, UiView content) {
        return openTab(spec, owner, content, TabPlacement.ACTIVE_GROUP);
    }

    /**
     * Opens an owned view as a content tab where {@code placement} puts it; moving retains it, final closure disposes it.
     *
     * @param spec the tab's key and title
     * @param owner the window the tab opens in, or a content tab whose window it opens in; null, or a closed window, for
     *              the main window
     * @param content the plugin's owned view
     * @param placement where a new tab goes; the open tab with the same key is selected where it is
     * @return the new tab, or the open tab with the same key, selected
     * @throws IllegalArgumentException for a foreign owner or a closed content tab
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread, or while the
     *                               main window closes or Jasper quits
     * @since 0.20.0
     */
    ContentTab openTab(ContentSpec spec, WindowOwner owner, UiView content, TabPlacement placement);

    /** Registers this plugin's batch closing guard. No requested content closes unless every guard accepts.
     * A pending decision blocks moves; failures refuse close. Runs for tab, window, quit and restart closes.
     * @param guard requested subset and true for window/quit batch closing, returning its asynchronous decision
     * @return removable registration
     * @since 0.8.5
     */
    default dev.jasper.sdk.Subscription onTabsClosing(java.util.function.BiFunction<java.util.List<ContentTab>,Boolean,
            java.util.concurrent.CompletableFuture<Boolean>> guard) {
        throw new UnsupportedOperationException("Workspace content is unavailable");
    }

    /**
     * Registers a guard asked when the user quits or restarts Jasper, after content tabs' guards
     * and before anything closes. Guards are asked one at a time; one whose future completes with {@code false}
     * cancels the quit. A guard that throws, returns {@code null} or whose future fails allows the quit. The guard
     * runs on the UI thread and may show a modal dialog before completing its future.
     *
     * <p>It is asked on a real quit or restart only. A resident macOS Jasper answers Cmd+Q, Jasper &rsaquo; Quit, the Dock's
     * Quit and a Jasper window's File &rsaquo; Quit by closing its windows through the content tabs'
     * closing guards and going to the background. That does not ask this guard, and the plugin keeps running until Jasper
     * really quits (Quit Jasper Completely, a log-out or a restart).
     *
     * @param guard returns the decision, possibly after asking the user
     * @return the registration; closing it removes the guard
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     * @throws UnsupportedOperationException if the host predates quit guards
     * @since 0.11.0
     */
    default Subscription onQuitting(Supplier<CompletableFuture<Boolean>> guard) {
        throw new UnsupportedOperationException("Quit guards are unavailable");
    }

    /**
     * Creates a dialog on the UI thread; it is not shown until {@link WindowSurface#show()}.
     *
     * @param spec what the dialog is
     * @return the dialog
     * @throws IllegalArgumentException when the owner is a closed window or not one this application created
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    PluginDialog dialog(DialogSpec spec);
    /**
     * Creates centered content inside a Jasper window. Show returns immediately and blocks owner
     * input, while native prompts remain usable. Escape and outside clicks do not dismiss it;
     * provide an explicit Cancel action that calls close. The owner and plugin lifetime close it.
     * @param spec accessible title and owning Jasper window
     * @return the initially hidden overlay, using the dialog content/lifetime handle
     * @throws IllegalArgumentException if the owner is closed or belongs to another host
     * @throws IllegalStateException if the owner already has an overlay, the context is closed,
     *         or the caller is off the UI thread
     * @since 0.7.3
     */
    PluginDialog overlay(OverlaySpec spec);

    /**
     * Selects existing local files in an owner-attached picker. The synchronous call pumps UI
     * events; closing the owner or stopping the plugin cancels and discards the result.
     * @param owner an open Jasper window, or a content tab of this plugin
     * @param title picker title
     * @param initial initial location, or empty for the platform default
     * @return absolute normalized selected files, or an empty immutable list on cancel
     * @throws IllegalArgumentException for a foreign, unshown or dead owner
     * @throws IllegalStateException for a closed context or off-UI call
     * @throws UnsupportedOperationException if the host lacks this picker
     * @since 0.7.5
     */
    default List<Path> chooseFiles(WindowOwner owner, String title, Optional<Path> initial) {
        throw new UnsupportedOperationException("File selection is unavailable");
    }

    /**
     * Selects one local directory with the same lifetime rules as {@link #chooseFiles}.
     * @param owner an open Jasper window, or a content tab of this plugin
     * @param title picker title
     * @param initial initial location, or empty for the platform default
     * @return absolute normalized directory, or empty on cancel
     * @throws IllegalArgumentException for a foreign, unshown or dead owner
     * @throws IllegalStateException for a closed context or off-UI call
     * @throws UnsupportedOperationException if the host lacks this picker
     * @since 0.7.5
     */
    default Optional<Path> chooseDirectory(WindowOwner owner, String title, Optional<Path> initial) {
        throw new UnsupportedOperationException("Directory selection is unavailable");
    }

    /**
     * Asks for a local file to save to, in the platform's save dialog, which confirms replacing an existing
     * file. The same owner, lifetime and UI-thread rules as {@link #chooseFiles} apply.
     * @param owner an open Jasper window, or a content tab of this plugin
     * @param title dialog title
     * @param initial a directory to start in, or a suggested file whose folder and name are filled in; empty for the platform default
     * @return an absolute normalized path, or empty on cancellation or owner closure
     * @throws IllegalArgumentException for a foreign, unshown or dead owner
     * @throws IllegalStateException for a closed context or off-UI call
     * @throws UnsupportedOperationException if the host lacks this picker
     * @since 0.8.4
     */
    default Optional<Path> chooseSaveFile(WindowOwner owner, String title, Optional<Path> initial) {
        throw new UnsupportedOperationException("Save selection is unavailable");
    }

    /**
     * Shows Jasper's file chooser: local places plus the roots of the sources {@code choice} names. The synchronous
     * call pumps UI events; closing the owner or stopping the plugin cancels it.
     *
     * @param choice what to choose
     * @return the chosen locations, or an empty immutable list on cancel
     * @throws IllegalArgumentException for a foreign, unshown or dead owner
     * @throws IllegalStateException for a closed context or off-UI call
     * @throws UnsupportedOperationException if the host lacks this chooser
     * @since 0.10.0
     */
    default List<FileLocation> choose(FileChoice choice) {
        throw new UnsupportedOperationException("The file chooser is unavailable");
    }
}
