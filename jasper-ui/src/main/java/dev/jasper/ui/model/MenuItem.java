package dev.jasper.ui.model;

import java.util.Objects;

/** Immutable menuitem descriptor. */
public record MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator) {
    public MenuItem {
        ModelChecks.id(id);
        Objects.requireNonNull(label); Objects.requireNonNull(icon); Objects.requireNonNull(action); if (!separator) { ModelChecks.label(label, "label"); ModelChecks.label(action, "action"); }
    }
}
