package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import java.util.List;

/**
 * The entries {@link PluginMenu#addEntries(List, java.util.function.Consumer)} appended to a menu: menu items that are not
 * actions. Closing them removes them; {@link #set(List)} replaces them. UI thread, except {@link #close()}.
 *
 * @since 0.31.0
 */
public interface MenuEntries extends Subscription {
    /**
     * Replaces the entries where they stand, with {@link PluginMenu#addEntries(List, java.util.function.Consumer)}'s
     * checks; a refused list leaves them as they were. A change of labels and enabled states alone, with the same ids,
     * separators and nesting in the same order, updates what each window shows in place, an open menu included, so a
     * progress label can count up while the user reads it. Any other change waits while a window has one of its menus
     * open, and shows there once that menu closes. Once closed, or once their menu was cleared, the entries show nothing,
     * whatever is set.
     *
     * @param items the entries, their ids unique across the whole tree, children included, and none with a shortcut
     * @throws IllegalArgumentException when an id repeats anywhere in the tree, or an item has a shortcut
     */
    void set(List<MenuItem> items);
}
