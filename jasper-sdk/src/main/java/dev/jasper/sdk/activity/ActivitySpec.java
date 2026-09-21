package dev.jasper.sdk.activity;

import java.util.Objects;
import java.util.Optional;

/**
 * What an activity is when it begins.
 *
 * @param title non-blank title shown to the user
 * @param detail initial detail line, possibly empty
 * @param activateActionId id of an action that brings the user to this work; resolved once the
 *                         actions API exists, ignored before then
 * @param cancel invoked when the user asks to stop the work; absent when it cannot be cancelled
 */
public record ActivitySpec(String title, String detail, Optional<String> activateActionId, Optional<Runnable> cancel) {
    /** Rejects a blank title and null parts. */
    public ActivitySpec {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("An activity needs a title");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(activateActionId, "activateActionId");
        Objects.requireNonNull(cancel, "cancel");
    }

    /**
     * An activity with only a title.
     *
     * @param title non-blank title
     * @return the spec
     */
    public static ActivitySpec of(String title) {
        return new ActivitySpec(title, "", Optional.empty(), Optional.empty());
    }
}
