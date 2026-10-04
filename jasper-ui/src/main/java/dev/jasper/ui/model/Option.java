package dev.jasper.ui.model;

import java.util.Optional;
import java.util.Objects;

/** Immutable option descriptor. */
public record Option(String id, String label, Optional<Nodes.Hue> hue) {
    public Option {
        ModelChecks.id(id);
        ModelChecks.label(label, "label"); Objects.requireNonNull(hue);
    }
}
