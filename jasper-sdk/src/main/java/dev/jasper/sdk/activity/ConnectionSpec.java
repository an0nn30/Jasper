package dev.jasper.sdk.activity;

import java.util.Objects;
import java.util.Optional;

/**
 * What a connection is when it is announced.
 *
 * @param title non-blank title shown to the user
 * @param detail initial detail line, possibly empty
 * @param state the first state: {@code UP}, {@code DEGRADED} or {@code DOWN}
 * @param activateActionId one of the plugin's own actions that brings the user to this connection, run when the
 *                         user clicks its card; absent when there is none
 * @since 0.10.2
 */
public record ConnectionSpec(String title, String detail, ConnectionEvent.State state, Optional<String> activateActionId) {
    /** Rejects a blank title, a first state of {@code CLOSED} and null parts. */
    public ConnectionSpec {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A connection needs a title");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(state, "state");
        if (state == ConnectionEvent.State.CLOSED) throw new IllegalArgumentException("A connection cannot begin closed");
        Objects.requireNonNull(activateActionId, "activateActionId");
    }

    /**
     * A connection with only a title and its first state.
     *
     * @param title non-blank title
     * @param state {@code UP}, {@code DEGRADED} or {@code DOWN}
     * @return the spec
     */
    public static ConnectionSpec of(String title, ConnectionEvent.State state) {
        return new ConnectionSpec(title, "", state, Optional.empty());
    }
}
