package dev.jasper.app.palette;

/** One thing a scope can do with a row. The first verb is Enter, the second Cmd/Ctrl+Enter. */
public record PaletteVerb(String id, String label) {
    public PaletteVerb {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Invalid verb ID");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Verb needs a label");
    }
}
