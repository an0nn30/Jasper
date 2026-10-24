package dev.jasper.app.terminals;

import dev.jasper.app.layout.TabRestore;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.swing.Icon;

/**
 * A pane whose session somebody else provides. The pane appears first; {@code connector} is called on the EDT
 * once per attempt, first connect and every reconnect alike, and must not block. {@code cleanup} runs the
 * closes of connections and the cancellation handlers, never on the EDT.
 *
 * @param providerId who provides the session, for display and diagnostics
 * @param title the pane's title until the program sets one
 * @param closeOnExit close the pane when the session ends instead of offering Reconnect
 * @param connector starts one connection attempt
 * @param cleanup where closes and cancellation handlers run
 * @param icon the provider's tab icon, or {@code null} for the host terminal icon; carried, never painted here
 * @param restore how the session's tab comes back when the windows are restored; empty when it does not
 */
public record SessionRequest(String providerId, String title, boolean closeOnExit, Consumer<SessionAttempt> connector,
                             Executor cleanup, Icon icon, Optional<TabRestore> restore) {
    public SessionRequest {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(cleanup, "cleanup");
        Objects.requireNonNull(restore, "restore");
    }

    /**
     * A request whose tab does not come back when the windows are restored.
     *
     * @param providerId who provides the session, for display and diagnostics
     * @param title the pane's title until the program sets one
     * @param closeOnExit close the pane when the session ends instead of offering Reconnect
     * @param connector starts one connection attempt
     * @param cleanup where closes and cancellation handlers run
     * @param icon the provider's tab icon, or {@code null} for the host terminal icon
     */
    public SessionRequest(String providerId, String title, boolean closeOnExit, Consumer<SessionAttempt> connector,
                          Executor cleanup, Icon icon) {
        this(providerId, title, closeOnExit, connector, cleanup, icon, Optional.empty());
    }

    /** A request whose tab shows the host terminal icon. */
    public SessionRequest(String providerId, String title, boolean closeOnExit, Consumer<SessionAttempt> connector,
                          Executor cleanup) {
        this(providerId, title, closeOnExit, connector, cleanup, null);
    }
}
