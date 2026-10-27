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
     * Appends an action that the terminal's right-click menu shows only while the right-clicked pane's selection is one
     * web address: without leading and trailing whitespace, an absolute address whose scheme is {@code http} or
     * {@code https}, in either letter case, with a host and no whitespace inside, such as {@code https://example.com/a}.
     * The action chosen from this item receives the address as {@link ActionContext#webAddress()}. Jasper reads the
     * selection itself while it builds the menu, and runs no plugin code then; the item needs no capability, since the
     * user chose it with that selection.
     *
     * @param actionId an action this plugin registered
     * @return removes this entry
     * @throws IllegalStateException when this menu is not a {@link Menus#terminalContext()} section or a submenu of one
     * @throws IllegalArgumentException when this plugin has not registered the action
     * @since 0.23.0
     */
    Subscription addForWebAddress(String actionId);

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
