package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Objects;

/**
 * Immutable menu item descriptor: one item of a {@link Nodes.MenuButton}'s menu, a {@link Nodes.Button}'s hold menu, a
 * {@link Nodes.Header}'s overflow menu, the context menu of a list, table, row or crumb, or a content tab's right-click
 * menu ({@link ContentTab#showContextMenu}).
 *
 * <p>An item with {@code children} is a submenu: it shows its label and an arrow, and pointing at it, or
 * Right or Enter on it, opens its children beside it. A submenu reports nothing itself, so its {@code action} is
 * blank and it shows no shortcut; a disabled one does not open.
 *
 * @param id the item's id, unique in its menu
 * @param label the item's literal text; not blank unless {@code separator}
 * @param icon an icon name drawn on a tile before the label; blank for none. When any item of a menu has an icon,
 *     every label starts after the icon column
 * @param action the action key choosing the item reports as {@link UiEvent.Action}; not blank unless {@code separator}
 *     or a submenu, which is blank. A content tab's right-click menu ignores it and reports the item's id
 * @param enabled false shows the item disabled; it reports nothing
 * @param separator true makes the item a separator line, which shows no label, icon or shortcut, reports nothing and has
 *     no children
 * @param hue the category colour of the icon's tile
 * @param shortcut the id of the action whose current binding shows at the item's end, in the platform's notation
 *     (nothing while the action is unbound); empty for none, and for a submenu
 * @param children the items of the submenu the item opens, their ids unique among them; copied. Empty for an item that
 *     opens none
 */
public record MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator, Nodes.Hue hue, String shortcut,
                       List<MenuItem> children) {
    /**
     * Neutral artwork and no shortcut.
     *
     * @param id the item's id, unique in its menu
     * @param label the item's literal text; not blank unless {@code separator}
     * @param icon an icon name drawn before the label; blank for none
     * @param action the action key choosing the item reports; not blank unless {@code separator}
     * @param enabled false shows the item disabled
     * @param separator true makes the item a separator line
     */
    public MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator) {
        this(id, label, icon, action, enabled, separator, Nodes.Hue.NEUTRAL, "");
    }
    /**
     * No shortcut.
     *
     * @param id the item's id, unique in its menu
     * @param label the item's literal text; not blank unless {@code separator}
     * @param icon an icon name drawn before the label; blank for none
     * @param action the action key choosing the item reports; not blank unless {@code separator}
     * @param enabled false shows the item disabled
     * @param separator true makes the item a separator line
     * @param hue the category colour of the icon's tile
     */
    public MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator, Nodes.Hue hue) {
        this(id, label, icon, action, enabled, separator, hue, "");
    }
    /**
     * An item that opens no submenu.
     *
     * @param id the item's id, unique in its menu
     * @param label the item's literal text; not blank unless {@code separator}
     * @param icon an icon name drawn before the label; blank for none
     * @param action the action key choosing the item reports; not blank unless {@code separator}
     * @param enabled false shows the item disabled
     * @param separator true makes the item a separator line
     * @param hue the category colour of the icon's tile
     * @param shortcut the id of the action whose current binding shows at the item's end; empty for none
     */
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
    /**
     * This item showing {@code actionId}'s current binding: an app action ({@code new_tab}) or a contributed one
     * ({@code dev.jasper.remote.connect}).
     *
     * @param actionId the id of the action whose binding the item shows; empty for none, otherwise not blank
     * @return a copy of this item with that shortcut
     */
    public MenuItem withShortcut(String actionId) { return new MenuItem(id, label, icon, action, enabled, separator, hue, actionId, children); }
}
