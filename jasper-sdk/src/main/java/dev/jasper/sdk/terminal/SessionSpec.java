package dev.jasper.sdk.terminal;

import dev.jasper.sdk.RestoreState;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A session your plugin provides. The pane appears first, waiting; {@code connector} is then called on the
 * event thread, once for the first connect and once for every Reconnect, each time with a fresh
 * {@link PendingSession}. It must not block: start the connection on {@code context.background()}.
 *
 * @param title the pane's title until the remote program sets one
 * @param icon the tab's icon (since 0.7.6), drawn in a 16 &times; 16 logical-pixel slot; empty shows
 *     Jasper's terminal icon
 * @param onExit what happens to the pane when the session ends
 * @param connector starts one connection attempt
 * @param restore how the session's tab comes back when Jasper restores its windows (since 0.21.0); empty, and the tab is
 *     not saved
 */
public record SessionSpec(String title, Optional<String> icon, ExitPolicy onExit, Consumer<PendingSession> connector,
                          Optional<RestoreState> restore) {
    /** Rejects nulls and a blank title. */
    public SessionSpec {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(onExit, "onExit");
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(restore, "restore");
        if (title.isBlank()) throw new IllegalArgumentException("A session needs a title");
    }

    /**
     * A session whose tab does not come back when Jasper restores its windows.
     *
     * @param title the pane's title until the remote program sets one
     * @param icon the tab's icon; empty shows Jasper's terminal icon
     * @param onExit what happens to the pane when the session ends
     * @param connector starts one connection attempt
     */
    public SessionSpec(String title, Optional<String> icon, ExitPolicy onExit, Consumer<PendingSession> connector) {
        this(title, icon, onExit, connector, Optional.empty());
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

    /**
     * This session, whose tab comes back as {@code state} says when Jasper restores its windows: the plugin's restorer
     * for that kind opens it again, waiting until its tab is first shown.
     *
     * @param state the restorer kind and what it rebuilds the session from
     * @return the spec
     * @since 0.21.0
     */
    public SessionSpec withRestore(RestoreState state) { return new SessionSpec(title, icon, onExit, connector, Optional.of(state)); }
}
