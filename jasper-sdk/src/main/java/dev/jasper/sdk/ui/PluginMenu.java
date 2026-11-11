package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import java.util.List;
import java.util.function.Consumer;

/**
 * A menu, or a plugin's section of a menu, that the plugin may change at any time on the UI thread:
 * a host list can be rebuilt with {@link #clear()} and {@link #add}, and things that are not actions, such as
 * downloads, listed with {@link #addEntries}. Closing a section removes only this plugin's entries; closing a submenu
 * removes it from its parent.
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
     * Appends menu entries that are not actions, such as a list of downloads. Each item shows its label and enabled
     * state, a separator shows as one, and an item with {@code children} is a submenu titled by its label. An item's
     * icon, hue and {@code action} are not shown, so a leaf's {@code action}, which {@link MenuItem} requires, is best
     * its id. Entries are never in the command palette or the keymap, and they show no key. A label is plain text: one
     * that starts with {@code <html>} shows as typed.
     *
     * <p>Choosing an enabled leaf calls {@code chosen} with its id on the UI thread; a throw is contained and recorded as
     * the plugin's incident. A disabled submenu does not open. {@link MenuEntries#set(List)} replaces the entries where
     * they stand, between this menu's other entries.
     *
     * @param items the entries, their ids unique across the whole tree, children included, and none with a shortcut
     * @param chosen hears the id of the leaf chosen
     * @return the entries; closing them removes them
     * @throws IllegalArgumentException when an id repeats anywhere in the tree, or an item has a shortcut
     * @throws IllegalStateException when this menu is a {@link Menus#terminalContext()} section or a submenu of one
     * @throws UnsupportedOperationException if the host predates menu entries
     * @since 0.31.0
     */
    default MenuEntries addEntries(List<MenuItem> items, Consumer<String> chosen) {
        throw new UnsupportedOperationException("Menu entries are unavailable");
    }

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
