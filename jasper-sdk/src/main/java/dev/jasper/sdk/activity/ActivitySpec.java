package dev.jasper.sdk.activity;

import java.util.Objects;
import java.util.Optional;

/**
 * What an activity is when it begins.
 *
 * @param title non-blank title shown to the user
 * @param detail initial detail line, possibly empty
 * @param activateActionId one of the plugin's own actions that brings the user to this work, run in the window used
 *                         last when the user clicks its card or its outcome notification; any other id only raises
 *                         the application
 * @param cancel stops the work when the user asks; absent when it cannot be cancelled. Jasper's activity cards offer
 *               no way to cancel, so the host never runs it
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
