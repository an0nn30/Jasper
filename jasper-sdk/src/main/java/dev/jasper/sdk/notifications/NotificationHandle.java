package dev.jasper.sdk.notifications;

/** A posted notification, for the plugin to withdraw once it no longer applies. Safe from any thread. */
public interface NotificationHandle {
    /** Expires the notification: its card closes and its links stop working; it stays in history. Idempotent. */
    void expire();

    /**
     * Removes the notification: its card closes, its links stop working and it leaves history. A removed notification
     * also reads as expired. Idempotent; safe from any thread.
     *
     * <p>Jasper counts a post with the same text as one made less than a minute before on that earlier notification, so
     * one notification can have several handles: it leaves history once each of them has been removed, and each removed
     * handle reads as expired from then on.
     *
     * @since 0.30.0
     */
    void remove();

    /**
     * Whether it has expired: through {@link #expire()} or {@link #remove()}, an action that expires it, the user
     * removing or clearing it, its group dropping it, or the plugin stopping.
     *
     * @return true once expired
     */
    boolean expired();
}
