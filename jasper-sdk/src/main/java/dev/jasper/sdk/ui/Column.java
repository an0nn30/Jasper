package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable column descriptor of a {@link Nodes.Table} or a {@link Nodes.Tree}. Clicking a column header reports
 * {@link UiEvent.Sorted}; the view's owner orders the rows. In a tree the first column titles the items and each later
 * one a {@link TreeItem#trailing()} value.
 *
 * @param id the column's id, unique among its table's or tree's columns; a table row's cells are keyed by it
 * @param title the header's literal text; not blank
 * @param role the text role of the column's plain cells, or of a tree's trailing values under it
 * @param numeric true right-aligns the header and the column's text, and keeps a table column at least as wide as its
 *     widest plain cell
 * @param weight the column's share of a table's width relative to the other columns' weights; finite and positive
 * @param sorted present shows the sort indicator on the column the rows are sorted by: true ascending, false
 *     descending
 */
public record Column(String id, String title, Nodes.TextRole role, boolean numeric, double weight, Optional<Boolean> sorted) {
    public Column {
        ModelChecks.id(id);
        ModelChecks.label(title, "title"); Objects.requireNonNull(role);
        if (!Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Column weight must be positive");
        Objects.requireNonNull(sorted);
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
        this(id, title, role, numeric, 1, Optional.empty());
    }
}
