package dev.jasper.sdk.terminal;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One pane. Handles keep an id and no Swing object; two handles for the same pane are equal. A pane can
 * close at any moment: commands to a closed pane are ignored, queries return the last known value, and
 * {@link #isOpen()} tells. A missing capability always throws. EDT only, except the three sending methods.
 */
public interface PaneHandle {
    /**
     * The pane's stable id.
     *
     * @return the id
     */
    UUID id();

    /**
     * The tab the pane was last seen in.
     *
     * @return its handle
     */
    TabHandle tab();

    /**
     * A snapshot of the pane. Needs {@code terminal.observe}.
     *
     * @return the snapshot; the last known one once the pane is closed
     */
    PaneInfo info();

    /**
     * The name of the program in the foreground, which has to be asked of the operating system off the
     * event thread. Needs {@code terminal.observe}. The future completes on an application worker thread.
     *
     * @return the future name, empty when it is unknown or the pane is closed
     */
    CompletableFuture<Optional<String>> foregroundJob();

    /**
     * Types text into the pane, as UTF-8, adding nothing: end a command with a newline yourself.
     * Needs {@code terminal.inject}. Callable from any thread; calls from one thread keep their order.
     *
     * @param text what to type
     */
    void sendText(String text);

    /**
     * Writes raw bytes to the pane. Needs {@code terminal.inject}. Callable from any thread.
     *
     * @param bytes what to write; copied before this returns
     */
    void sendBytes(byte[] bytes);

    /**
     * Pastes text the way the user's paste does, bracketed when the program asked for that.
     * Needs {@code terminal.inject}. Callable from any thread.
     *
     * @param text what to paste
     */
    void paste(String text);

    /**
     * The text selected in the pane. Needs {@code terminal.selection}.
     *
     * @return the selection, or empty when there is none or the pane is closed
     */
    Optional<String> selection();

    /** Selects the pane's tab and gives the pane keyboard focus. Ignored once it is closed. */
    void focus();

    /**
     * Whether the pane still exists. A pane whose program exited may still be open.
     *
     * @return true while it is open
     */
    boolean isOpen();
}
