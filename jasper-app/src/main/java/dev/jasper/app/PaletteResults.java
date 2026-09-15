package dev.jasper.app;

import java.util.List;

/** What a scope answers a query with. {@code sectionLabel} heads the empty-query list ("Recent", "Most recent"). */
record PaletteResults(List<PaletteRow> rows, String sectionLabel, String initialSelectionId) {
    static final int MAX_ROWS = 200;

    PaletteResults {
        rows = List.copyOf(rows);
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("At most " + MAX_ROWS + " rows");
        sectionLabel = sectionLabel == null || sectionLabel.isBlank() ? null : sectionLabel;
        if (initialSelectionId == null && !rows.isEmpty()) initialSelectionId = rows.getFirst().id();
    }

    static PaletteResults none() { return new PaletteResults(List.of(), null, null); }
}
