package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;

/** Plain action buttons on the rail, after the panel icons: for example a button that opens a plugin window. */
public interface Rail {
    /**
     * Adds a button for an action on the UI thread. The action should have an icon.
     *
     * @param actionId an action this plugin registered
     * @return the registration
     * @throws IllegalArgumentException when this plugin has not registered the action
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    Subscription add(String actionId);
}
