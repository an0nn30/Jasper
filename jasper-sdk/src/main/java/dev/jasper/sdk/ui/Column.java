package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Immutable column descriptor of a {@link Nodes.Table} or a {@link Nodes.Tree}. Clicking a column header reports
 * {@link UiEvent.Sorted}; the view's owner orders the rows. In a tree the first column titles the items and each later
 * one a {@link TreeItem#trailing()} value. A table column with a {@code width} takes that width and the last column
 * absorbs the rest; a tree's trailing column with a width takes it instead of fitting its values. Dragging a header's
 * edge reports {@link UiEvent.ColumnsResized}; the view's owner stores the widths and passes them back.
 *
 * @param id the column's id, unique among its table's or tree's columns; a table row's cells are keyed by it
 * @param title the header's literal text; not blank
 * @param role the text role of the column's plain cells, or of a tree's trailing values under it
 * @param numeric true right-aligns the header and the column's text, and keeps a table column at least as wide as its
 *     widest plain cell
 * @param weight the column's share of a table's width relative to the other columns' weights; finite and positive
 * @param sorted present shows the sort indicator on the column the rows are sorted by: true ascending, false
 *     descending
 * @param width present is the column's unscaled width in pixels and positive; empty leaves the width to the view (since
 *     0.25.0)
 */
public record Column(String id, String title, Nodes.TextRole role, boolean numeric, double weight, Optional<Boolean> sorted, OptionalInt width) {
    public Column {
        ModelChecks.id(id);
        ModelChecks.label(title, "title"); Objects.requireNonNull(role);
        if (!Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Column weight must be positive");
        Objects.requireNonNull(sorted); Objects.requireNonNull(width);
        if (width.isPresent() && width.getAsInt() <= 0) throw new IllegalArgumentException("Column width must be positive");
    }
    /**
     * A column without a width.
     *
     * @param id the column's id, unique among its table's or tree's columns
     * @param title the header's literal text; not blank
     * @param role the text role of the column's plain cells
     * @param numeric true right-aligns the header and the column's text
     * @param weight the column's share of a table's width; finite and positive
     * @param sorted present shows the sort indicator: true ascending, false descending
     */
    public Column(String id, String title, Nodes.TextRole role, boolean numeric, double weight, Optional<Boolean> sorted) {
        this(id, title, role, numeric, weight, sorted, OptionalInt.empty());
    }
    /**
     * An equal-share column without a sort indicator.
     *
     * @param id the column's id, unique among its table's or tree's columns
     * @param title the header's literal text; not blank
     * @param role the text role of the column's plain cells
     * @param numeric true right-aligns the header and the column's text
     */
    public Column(String id, String title, Nodes.TextRole role, boolean numeric) {
        this(id, title, role, numeric, 1, Optional.empty(), OptionalInt.empty());
    }
    /**
     * This column with the given width.
     *
     * @param width present is the unscaled width in pixels and positive; empty leaves the width to the view
     * @return the column with that width
     * @since 0.25.0
     */
    public Column withWidth(OptionalInt width) { return new Column(id, title, role, numeric, weight, sorted, width); }
    /**
     * This column with the given sort indicator.
     *
     * @param sorted present shows the indicator: true ascending, false descending
     * @return the column with that indicator
     * @since 0.25.0
     */
    public Column withSorted(Optional<Boolean> sorted) { return new Column(id, title, role, numeric, weight, sorted, width); }
}
