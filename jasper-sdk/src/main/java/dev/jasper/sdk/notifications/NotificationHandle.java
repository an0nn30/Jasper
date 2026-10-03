package dev.jasper.sdk.notifications;

/** A posted notification, for the plugin to withdraw once it no longer applies. Safe from any thread. */
public interface NotificationHandle {
    /** Expires the notification: its card closes and its links stop working; it stays in history. Idempotent. */
    void expire();

    /**
     * Whether it has expired: through {@link #expire()}, an action that expires it, the user removing or clearing it,
     * its group dropping it, or the plugin stopping.
     *
     * @return true once expired
     */
    boolean expired();
}
