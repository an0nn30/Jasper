package dev.jasper.app.palette;

import java.util.List;
import java.util.Objects;

/** What a scope is told about each query: the platform, the origin pane and how many rows it may return. */
public record PaletteContext(boolean macOs, PaletteTarget target, int maxResults) {

    public PaletteContext {
        Objects.requireNonNull(target);
        if (maxResults < 1 || maxResults > PaletteResults.MAX_ROWS)
            throw new IllegalArgumentException("Max results must be 1\u2013" + PaletteResults.MAX_ROWS);
    }

    /** Every row a scope has, up to the list's {@link PaletteResults#MAX_ROWS}; the list scrolls to reach them. */
    public PaletteContext(boolean macOs, PaletteTarget target) { this(macOs, target, PaletteResults.MAX_ROWS); }

}
