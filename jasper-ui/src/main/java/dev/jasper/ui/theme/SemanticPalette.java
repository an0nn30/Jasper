package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Complete, immutable canonical colors; owns no UI state. */
public final class SemanticPalette {
    private final Map<SemanticColor, Color> values;
    /** Copies the input and rejects missing or null roles. */
    public SemanticPalette(Map<SemanticColor, Color> values) {
        Objects.requireNonNull(values, "values");
        var copy = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) {
            Color color = values.get(role);
            if (color == null) throw new IllegalArgumentException("Missing semantic color: " + role.key());
            copy.put(role, color);
        }
        this.values = Map.copyOf(copy);
    }
    /** Returns a color for every standard role. */
    public Color color(SemanticColor role) { return values.get(Objects.requireNonNull(role, "role")); }
    @Override public boolean equals(Object other) { return other instanceof SemanticPalette p && values.equals(p.values); }
    @Override public int hashCode() { return values.hashCode(); }
    @Override public String toString() { return "SemanticPalette" + values; }
}
