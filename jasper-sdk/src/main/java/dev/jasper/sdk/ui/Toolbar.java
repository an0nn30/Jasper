package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;

/** The main toolbar's plugin section, which follows the built-in buttons in every window. */
public interface Toolbar {
    /**
     * Adds an item on the UI thread.
     *
     * @param item the item
     * @return the registration
     * @throws IllegalArgumentException when the item names an action this plugin has not registered
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    Subscription add(ToolbarItem item);
}
