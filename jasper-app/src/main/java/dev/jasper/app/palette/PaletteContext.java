package dev.jasper.app.palette;

import dev.jasper.app.config.PaletteSettings;

import java.util.List;
import java.util.Objects;

/**
 * What a scope is told about each query: the platform, the origin pane and how many rows it may return.
 */
public record PaletteContext(boolean macOs, PaletteTarget target, int maxResults) {

    public PaletteContext {
        Objects.requireNonNull(target);
        if (maxResults < PaletteSettings.MIN_MAX_RESULTS || maxResults > PaletteSettings.MAX_MAX_RESULTS)
            throw new IllegalArgumentException("Max results must be " + PaletteSettings.MIN_MAX_RESULTS + "\u2013" + PaletteSettings.MAX_MAX_RESULTS);
    }

    public PaletteContext(boolean macOs, PaletteTarget target) { this(macOs, target, PaletteSettings.DEFAULT_MAX_RESULTS); }

}
