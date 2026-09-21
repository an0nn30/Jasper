package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;

/**
 * A menu, or a plugin's section of a menu, that the plugin may change at any time on the UI thread:
 * a host list can be rebuilt with {@link #clear()} and {@link #add}. Closing a section removes only
 * this plugin's entries; closing a submenu removes it from its parent.
 */
public interface PluginMenu extends Subscription {
    /**
     * Appends an action.
     *
     * @param actionId an action this plugin registered
     * @return removes this entry
     * @throws IllegalArgumentException when this plugin has not registered the action
     */
    Subscription add(String actionId);

    /**
     * Appends a separator.
     *
     * @return removes this separator
     */
    Subscription addSeparator();

    /**
     * Appends a submenu.
     *
     * @param title non-blank title
     * @return the submenu
     */
    PluginMenu submenu(String title);

    /** Removes every entry, including submenus. */
    void clear();
}
