package dev.jasper.ui.model;

import java.util.Objects;

/** Immutable menuitem descriptor. */
public record MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator, Nodes.Hue hue) {
    /** Compatibility constructor with neutral artwork. */
    public MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator) {
        this(id, label, icon, action, enabled, separator, Nodes.Hue.NEUTRAL);
    }
    public MenuItem {
        ModelChecks.id(id); Objects.requireNonNull(hue);
        Objects.requireNonNull(label); Objects.requireNonNull(icon); Objects.requireNonNull(action); if (!separator) { ModelChecks.label(label, "label"); ModelChecks.label(action, "action"); }
    }
}
