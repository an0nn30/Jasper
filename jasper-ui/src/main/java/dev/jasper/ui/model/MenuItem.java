package dev.jasper.ui.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable menuitem descriptor; {@code shortcut} names the action whose current binding the host shows beside the
 * label ("" for none). An item with {@code children} is a submenu, which reports no action and shows no shortcut.
 */
public record MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator, Nodes.Hue hue, String shortcut,
                       List<MenuItem> children) {
    /** Neutral artwork and no shortcut. */
    public MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator) {
        this(id, label, icon, action, enabled, separator, Nodes.Hue.NEUTRAL, "");
    }
    /** No shortcut. */
    public MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator, Nodes.Hue hue) {
        this(id, label, icon, action, enabled, separator, hue, "");
    }
    /** An item that opens no submenu. */
    public MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator, Nodes.Hue hue, String shortcut) {
        this(id, label, icon, action, enabled, separator, hue, shortcut, List.of());
    }
    public MenuItem {
        ModelChecks.id(id); Objects.requireNonNull(hue); Objects.requireNonNull(shortcut, "shortcut");
        Objects.requireNonNull(label); Objects.requireNonNull(icon); Objects.requireNonNull(action);
        children = List.copyOf(children); ModelChecks.unique(children, MenuItem::id);
        if (separator && !children.isEmpty()) throw new IllegalArgumentException("A separator opens no submenu");
        if (!separator) ModelChecks.label(label, "label");
        if (!children.isEmpty()) {
            if (!action.isBlank()) throw new IllegalArgumentException("A submenu reports no action");
            if (!shortcut.isEmpty()) throw new IllegalArgumentException("A submenu shows no shortcut");
        } else if (!separator) ModelChecks.label(action, "action");
        if (!shortcut.isEmpty()) ModelChecks.id(shortcut);
    }
    /** Whether the item opens a submenu of its children. */
    public boolean submenu() { return !children.isEmpty(); }
    /**
     * This item showing {@code actionId}'s current binding: an app action ({@code new_tab}) or a contributed one
     * ({@code dev.jasper.remote.connect}).
     */
    public MenuItem withShortcut(String actionId) { return new MenuItem(id, label, icon, action, enabled, separator, hue, actionId, children); }
}
