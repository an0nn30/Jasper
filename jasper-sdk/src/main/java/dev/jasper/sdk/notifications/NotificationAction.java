package dev.jasper.sdk.notifications;

import java.util.Objects;

/**
 * A link on a notification's card and history row. It runs on the UI thread inside the plugin's containment, so what
 * it throws becomes an incident attributed to the plugin.
 *
 * @param label the link's text: visible, at most 40 characters
 * @param run what it does
 * @param expires whether the notification expires after it runs, so its links stop working
 */
public record NotificationAction(String label, Runnable run, boolean expires) {
    static final int MAX_LABEL = 40;

    /** Validates the label. */
    public NotificationAction {
        Texts.requireVisible(label, MAX_LABEL, "action label");
        Objects.requireNonNull(run, "run");
    }

    /**
     * An action that expires its notification after running.
     *
     * @param label the link's text
     * @param run what it does
     * @return the action
     */
    public static NotificationAction of(String label, Runnable run) { return new NotificationAction(label, run, true); }
}
