package dev.jasper.app.notifications;

import java.util.Objects;

/**
 * A labelled link on a card and history row. {@code run} runs on the EDT; with {@code expires} the notification
 * expires afterwards, so its other actions stop working.
 */
public record NotificationAction(String label, Runnable run, boolean expires) {
    static final int MAX_LABEL = 40;

    public NotificationAction {
        label = NotificationText.line(Objects.requireNonNull(label, "label"), MAX_LABEL);
        if (label.isEmpty()) throw new IllegalArgumentException("An action needs a label");
        Objects.requireNonNull(run, "run");
    }

    /** An action that expires its notification after running. */
    public static NotificationAction of(String label, Runnable run) {
        return new NotificationAction(label, run, true);
    }
}
