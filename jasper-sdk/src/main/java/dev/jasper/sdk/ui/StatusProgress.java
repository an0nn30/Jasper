package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;

/**
 * A host-rendered progress control in every window. Mutators run on the UI thread and
 * do nothing after close. Closing is idempotent and safe from any thread.
 * @since 0.7.5
 */
public interface StatusProgress extends Subscription {
    /**
     * Replaces the entire display atomically.
     * @param state progress and optional owned actions
     * @throws IllegalArgumentException if an action belongs to another plugin or is absent
     * @throws IllegalStateException if called off the UI thread
     */
    void update(StatusProgressState state);
    /**
     * Shows or hides the control.
     * @param visible whether to display it
     * @throws IllegalStateException if called off the UI thread
     */
    void setVisible(boolean visible);
}
