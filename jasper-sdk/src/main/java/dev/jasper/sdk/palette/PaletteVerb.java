package dev.jasper.sdk.palette;

/**
 * One thing a scope can do with a row. A scope's verbs are bound in order to Enter, Cmd/Ctrl+Enter
 * and Shift+Enter.
 *
 * @param id    lower-case identifier, unique within the scope: {@code [a-z][a-z0-9_.-]{0,127}}
 * @param label what the hint bar shows next to this verb's key, such as "Paste and run"
 */
public record PaletteVerb(String id, String label) {
    /** Validates both parts. */
    public PaletteVerb {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Invalid verb id: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A verb needs a label");
    }
}
