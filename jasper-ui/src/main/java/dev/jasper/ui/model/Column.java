package dev.jasper.ui.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable column descriptor. {@code weight} is the column's relative share of the row width; a present
 * {@code sorted} shows the kit's sort indicator (true ascending, false descending). Clicking a column header reports
 * {@link UiEvent.Sorted}; the host owns the order.
 */
public record Column(String id, String title, Nodes.TextRole role, boolean numeric, double weight, Optional<Boolean> sorted) {
    public Column {
        ModelChecks.id(id);
        ModelChecks.label(title, "title"); Objects.requireNonNull(role);
        if (!Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Column weight must be positive");
        Objects.requireNonNull(sorted);
    }
    /** An equal-share column without a sort indicator. */
    public Column(String id, String title, Nodes.TextRole role, boolean numeric) {
        this(id, title, role, numeric, 1, Optional.empty());
    }
}
