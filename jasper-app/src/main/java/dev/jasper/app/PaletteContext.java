package dev.jasper.app;

import java.util.Objects;

/**
 * What a scope is told about each query: the platform, the origin pane, how many rows it may return
 * and whether trivial commands should be ranked below real work.
 */
record PaletteContext(boolean macOs, PaletteTarget target, int maxResults, boolean deprioritizeTrivial) {
    static final int DEFAULT_MAX_RESULTS = 5;
    static final int MIN_MAX_RESULTS = 1;
    static final int MAX_MAX_RESULTS = 20;

    PaletteContext {
        Objects.requireNonNull(target);
        if (maxResults < MIN_MAX_RESULTS || maxResults > MAX_MAX_RESULTS)
            throw new IllegalArgumentException("Max results must be " + MIN_MAX_RESULTS + "\u2013" + MAX_MAX_RESULTS);
    }

    PaletteContext(boolean macOs, PaletteTarget target) { this(macOs, target, DEFAULT_MAX_RESULTS); }

    PaletteContext(boolean macOs, PaletteTarget target, int maxResults) { this(macOs, target, maxResults, true); }
}
