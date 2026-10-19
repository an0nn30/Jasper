package dev.jasper.sdk.palette;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a scope answers a query with.
 *
 * @param rows               at most 200 rows, in display order; the palette lists them all, scrolling as needed
 * @param sectionLabel       a heading over the rows, such as "Recent", shown while this scope is chosen and the answer
 *                           has rows; with no scope chosen the palette heads the rows itself
 * @param initialSelectionId the row selected first; defaults to the first row
 */
public record PaletteResults(List<PaletteRow> rows, Optional<String> sectionLabel, Optional<String> initialSelectionId) {
    /** The hard cap on rows in one answer. */
    public static final int MAX_ROWS = 200;

    /** Copies the rows and defaults the selection to the first row. */
    public PaletteResults {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("At most " + MAX_ROWS + " rows");
        sectionLabel = Objects.requireNonNull(sectionLabel, "sectionLabel").filter(text -> !text.isBlank());
        Objects.requireNonNull(initialSelectionId, "initialSelectionId");
        if (initialSelectionId.isEmpty() && !rows.isEmpty()) initialSelectionId = Optional.of(rows.getFirst().id());
    }

    /**
     * Rows with no section label, the first selected.
     *
     * @param rows the rows
     * @return the results
     */
    public static PaletteResults of(List<PaletteRow> rows) { return new PaletteResults(rows, Optional.empty(), Optional.empty()); }

    /**
     * No rows.
     *
     * @return no rows
     */
    public static PaletteResults none() { return new PaletteResults(List.of(), Optional.empty(), Optional.empty()); }
}
