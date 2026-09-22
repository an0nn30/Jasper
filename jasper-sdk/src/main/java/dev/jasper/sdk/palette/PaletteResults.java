package dev.jasper.sdk.palette;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a scope answers a query with.
 *
 * @param rows               at most 200 rows, in display order; the palette shows the first {@code maxResults}
 * @param sectionLabel       a heading for the list, used for the empty query ("Recent", "Most recent")
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
     * Derives a value.
     *
     * @param rows the rows
     *  @return those rows with no section label */
    public static PaletteResults of(List<PaletteRow> rows) { return new PaletteResults(rows, Optional.empty(), Optional.empty()); }

    /**
     * No rows.
     *
     * @return no rows
     */
    public static PaletteResults none() { return new PaletteResults(List.of(), Optional.empty(), Optional.empty()); }
}
