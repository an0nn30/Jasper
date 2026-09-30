package dev.jasper.sdk.files;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.terminal.PaneHandle;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Places a plugin adds to Jasper's file chooser, such as its saved hosts. Every method runs on the UI thread and does
 * no I/O; connecting happens behind the stage {@link #connect} returns. The host contains every call: a failing method
 * shows no roots, recognises no pane or fails to connect.
 *
 * @since 0.10.0
 */
public interface FileSource {
    /**
     * Read once, at registration.
     *
     * @return the source's id and heading
     */
    FileSourceSpec spec();

    /**
     * The roots to list now.
     *
     * @return roots in display order
     */
    List<FileRoot> roots();

    /**
     * Registers a listener for changes to the roots or their connected state.
     *
     * @param listener called on the UI thread
     * @return removes the listener
     */
    Subscription onChanged(Runnable listener);

    /**
     * Where a terminal pane is, when this source can reach it; a chooser opened from that pane starts there.
     *
     * @param pane the owner's focused pane
     * @return a location in one of this source's roots, or empty
     */
    default Optional<FileLocation.Sourced> locate(PaneHandle pane) { return Optional.empty(); }

    /**
     * Connects to a root, possibly asking the user to sign in. When the stage is a {@code CompletableFuture}, the host
     * cancels it to abandon the attempt.
     *
     * @param rootId one of {@link #roots()}
     * @param owner  the window the chooser belongs to, for sign-in prompts
     * @return completes, on any thread, with a browser the host owns and closes
     */
    CompletionStage<FileBrowser> connect(String rootId, WindowOwner owner);
}
