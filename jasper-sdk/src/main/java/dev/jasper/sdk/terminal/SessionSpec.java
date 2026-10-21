package dev.jasper.sdk.terminal;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A session your plugin provides. The pane appears first, showing "Connecting…" and Cancel; {@code connector} is
 * then called on the event thread, before {@code openTab} or {@code split} returns, and again for every Retry or
 * Reconnect the user chooses, each time with a fresh {@link PendingSession}. It must not block: start the connection
 * on {@code context.background()}. A connector that throws fails that attempt with the exception's message.
 *
 * @param title the pane's title until the remote program sets one
 * @param icon a semantic icon name for the tab (since 0.7.6), drawn in a 16 &times; 16 logical-pixel slot; empty
 *     shows Jasper's terminal icon, and a name the host does not know makes {@code openTab} or {@code split} throw
 *     {@link IllegalArgumentException}
 * @param onExit what happens to the pane when the session ends
 * @param connector starts one connection attempt
 */
public record SessionSpec(String title, Optional<String> icon, ExitPolicy onExit, Consumer<PendingSession> connector) {
    /** Rejects nulls and a blank title. */
    public SessionSpec {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(onExit, "onExit");
        Objects.requireNonNull(connector, "connector");
        if (title.isBlank()) throw new IllegalArgumentException("A session needs a title");
    }

    /**
     * A session whose pane stays open when it ends.
     *
     * @param title the pane's title until the remote program sets one
     * @param connector starts one connection attempt
     * @return the spec
     */
    public static SessionSpec of(String title, Consumer<PendingSession> connector) {
        return new SessionSpec(title, Optional.empty(), ExitPolicy.KEEP_OPEN, connector);
    }
}
