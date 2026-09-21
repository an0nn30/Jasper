package dev.jasper.sdk.activity;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * One step in an activity's life, stamped by the runtime with the plugin that owns it.
 *
 * @param id the activity's identity
 * @param sourcePluginId the owning plugin, set by the runtime
 * @param title the title given when the activity began
 * @param state the step
 * @param fraction last reported fraction, absent while indeterminate
 * @param detail current detail line
 * @param activateActionId the spec's activation action, if any
 */
public record ActivityEvent(UUID id, String sourcePluginId, String title, State state,
                            OptionalDouble fraction, String detail, Optional<String> activateActionId) {
    /** The steps of an activity. */
    public enum State {
        /** The activity began. */
        STARTED,
        /** Progress or detail changed. */
        PROGRESS,
        /** Ended successfully. */
        SUCCEEDED,
        /** Ended in failure, or its plugin stopped while it was open. */
        FAILED,
        /** Ended by cancellation. */
        CANCELLED
    }

    /** Rejects null parts and a fraction outside 0 to 1. */
    public ActivityEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourcePluginId, "sourcePluginId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(fraction, "fraction");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(activateActionId, "activateActionId");
        if (fraction.isPresent()) {
            double value = fraction.getAsDouble();
            if (Double.isNaN(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("An activity fraction must be from 0 to 1: " + value);
        }
    }

    /**
     * Whether this event ends the activity.
     *
     * @return true for SUCCEEDED, FAILED and CANCELLED
     */
    public boolean terminal() {
        return state == State.SUCCEEDED || state == State.FAILED || state == State.CANCELLED;
    }
}
