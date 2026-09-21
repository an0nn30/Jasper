package dev.jasper.app.terminals;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

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
 */
public record SessionRequest(String providerId, String title, boolean closeOnExit, Consumer<SessionAttempt> connector, Executor cleanup) {
    public SessionRequest {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(cleanup, "cleanup");
    }
}
