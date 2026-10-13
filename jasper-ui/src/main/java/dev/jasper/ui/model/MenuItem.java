package dev.jasper.ui.model;

import java.util.Objects;

/**
 * Immutable menuitem descriptor; {@code shortcut} names the action whose current binding the host shows beside the
 * label ("" for none).
 */
public record MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator, Nodes.Hue hue, String shortcut) {
    /** Neutral artwork and no shortcut. */
    public MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator) {
        this(id, label, icon, action, enabled, separator, Nodes.Hue.NEUTRAL, "");
    }
    /** No shortcut. */
    public MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator, Nodes.Hue hue) {
        this(id, label, icon, action, enabled, separator, hue, "");
    }
    public MenuItem {
        ModelChecks.id(id); Objects.requireNonNull(hue); Objects.requireNonNull(shortcut, "shortcut");
        Objects.requireNonNull(label); Objects.requireNonNull(icon); Objects.requireNonNull(action); if (!separator) { ModelChecks.label(label, "label"); ModelChecks.label(action, "action"); }
        if (!shortcut.isEmpty()) ModelChecks.id(shortcut);
    }
    /**
     * This item showing {@code actionId}'s current binding: an app action ({@code new_tab}) or a contributed one
     * ({@code dev.jasper.remote.connect}).
     */
    public MenuItem withShortcut(String actionId) { return new MenuItem(id, label, icon, action, enabled, separator, hue, actionId); }
}
