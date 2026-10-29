package dev.jasper.ui.model;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Immutable column descriptor. {@code weight} is the column's relative share of the row width; a present
 * {@code sorted} shows the kit's sort indicator (true ascending, false descending). Clicking a column header reports
 * {@link UiEvent.Sorted}; the host owns the order. A present {@code width} is the column's unscaled width in pixels: a
 * table column with one takes it and the last column absorbs the rest, and a tree's trailing column with one takes it
 * instead of fitting its values; dragging a header's edge reports {@link UiEvent.ColumnsResized}.
 */
public record Column(String id, String title, Nodes.TextRole role, boolean numeric, double weight, Optional<Boolean> sorted, OptionalInt width) {
    public Column {
        ModelChecks.id(id);
        ModelChecks.label(title, "title"); Objects.requireNonNull(role);
        if (!Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Column weight must be positive");
        Objects.requireNonNull(sorted); Objects.requireNonNull(width);
        if (width.isPresent() && width.getAsInt() <= 0) throw new IllegalArgumentException("Column width must be positive");
    }
    /** A column without a width. */
    public Column(String id, String title, Nodes.TextRole role, boolean numeric, double weight, Optional<Boolean> sorted) {
        this(id, title, role, numeric, weight, sorted, OptionalInt.empty());
    }
    /** An equal-share column without a sort indicator. */
    public Column(String id, String title, Nodes.TextRole role, boolean numeric) {
        this(id, title, role, numeric, 1, Optional.empty(), OptionalInt.empty());
    }
    /** This column with the given width. */
    public Column withWidth(OptionalInt width) { return new Column(id, title, role, numeric, weight, sorted, width); }
    /** This column with the given sort indicator. */
    public Column withSorted(Optional<Boolean> sorted) { return new Column(id, title, role, numeric, weight, sorted, width); }
}
