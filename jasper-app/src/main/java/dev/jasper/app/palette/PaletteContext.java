package dev.jasper.app.palette;

import dev.jasper.app.config.PaletteSettings;

import java.util.List;
import java.util.Objects;

/**
 * What a scope is told about each query: the platform, the origin pane and how many rows it may return.
 * The All tab asks for one row more than it shows, to learn whether a scope has more.
 */
public record PaletteContext(boolean macOs, PaletteTarget target, int maxResults) {

    public PaletteContext {
        Objects.requireNonNull(target);
        if (maxResults < 1 || maxResults > PaletteResults.MAX_ROWS)
            throw new IllegalArgumentException("Max results must be 1\u2013" + PaletteResults.MAX_ROWS);
    }

    public PaletteContext(boolean macOs, PaletteTarget target) { this(macOs, target, PaletteSettings.DEFAULT_MAX_RESULTS); }

}
