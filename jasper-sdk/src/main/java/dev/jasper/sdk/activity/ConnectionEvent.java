package dev.jasper.sdk.activity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One step in a connection's life, stamped by the runtime with the plugin that owns it. A connection is up until
 * it is not and never completes, so it travels on its own topic, {@link Activities#CONNECTIONS}, rather than as
 * activity states.
 *
 * @param id the connection's identity
 * @param sourcePluginId the owning plugin, set by the runtime
 * @param title the title given when the connection was announced
 * @param state the step
 * @param detail current detail line
 * @param activateActionId the spec's activation action, if any
 * @since 0.10.2
 */
public record ConnectionEvent(UUID id, String sourcePluginId, String title, State state, String detail,
                              Optional<String> activateActionId) {
    /** The steps of a connection. */
    public enum State {
        /** Available. */
        UP,
        /** Available with a problem that needs attention, or trying to recover. */
        DEGRADED,
        /** Unavailable. */
        DOWN,
        /** No longer reported: its plugin closed it or stopped. Always the last event. */
        CLOSED
    }

    /** Rejects null parts. */
    public ConnectionEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourcePluginId, "sourcePluginId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(activateActionId, "activateActionId");
    }
}
