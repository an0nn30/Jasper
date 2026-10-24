package dev.jasper.sdk.palette;

import java.util.Objects;

/**
 * One thing a scope can do with a row. A scope's verbs are bound in order to Enter, Cmd+Enter (Ctrl+Enter on Linux
 * and Windows) and Shift+Enter.
 *
 * @param id    lower-case identifier, unique within the scope: {@code [a-z][a-z0-9_.-]{0,127}}
 * @param label what the hint bar shows next to this verb's key, such as "Paste and run"
 * @param role  STANDARD, or SPLIT for the split-pane variant of the scope's first verb, which a presentation may offer
 *              as an option of that verb ("Open in a split pane"); a scope has at most one SPLIT verb, never its first
 *              (since 0.18.0)
 */
public record PaletteVerb(String id, String label, Role role) {
    /**
     * How a verb relates to its scope's first verb.
     *
     * @since 0.18.0
     */
    public enum Role {
        /** A verb of its own. */
        STANDARD,
        /** The split-pane variant of the scope's first verb. */
        SPLIT
    }

    /** Validates every part. */
    public PaletteVerb {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Invalid verb id: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A verb needs a label");
        Objects.requireNonNull(role, "role");
    }

    /**
     * A verb of its own.
     *
     * @param id    the verb id
     * @param label the hint bar's text
     */
    public PaletteVerb(String id, String label) { this(id, label, Role.STANDARD); }
}
