package dev.jasper.sdk.ui;

import java.util.Objects;

/** Immutable column descriptor. */
public record Column(String id, String title, Nodes.TextRole role, boolean numeric) {
    public Column {
        ModelChecks.id(id);
        ModelChecks.label(title, "title"); Objects.requireNonNull(role);
    }
}
