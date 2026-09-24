package dev.jasper.sdk.terminal;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Icon;

/**
 * A session your plugin provides. The pane appears first, waiting; {@code connector} is then called on the
 * event thread, once for the first connect and once for every Reconnect, each time with a fresh
 * {@link PendingSession}. It must not block: start the connection on {@code context.background()}.
 *
 * @param title the pane's title until the remote program sets one
 * @param icon the tab's icon (since 0.7.5), drawn in a 16 &times; 16 logical-pixel slot; empty shows
 *     Jasper's terminal icon
 * @param onExit what happens to the pane when the session ends
 * @param connector starts one connection attempt
 */
public record SessionSpec(String title, Optional<Icon> icon, ExitPolicy onExit, Consumer<PendingSession> connector) {
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
