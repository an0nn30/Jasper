package dev.jasper.app.palette;

import java.util.Objects;

/**
 * One thing a scope can do with a row. The first verb is Enter, the second Cmd/Ctrl+Enter. A SPLIT verb is the
 * split-pane variant of the first; a scope has at most one, never first.
 */
public record PaletteVerb(String id, String label, Role role) {
    /** How a verb relates to its scope's first verb. */
    public enum Role { STANDARD, SPLIT }

    public PaletteVerb {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Invalid verb ID");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Verb needs a label");
        Objects.requireNonNull(role, "role");
    }

    /** A verb of its own. */
    public PaletteVerb(String id, String label) { this(id, label, Role.STANDARD); }
}
