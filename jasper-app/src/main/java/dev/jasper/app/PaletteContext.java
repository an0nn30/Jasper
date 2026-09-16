package dev.jasper.app;

import java.util.List;
import java.util.Objects;

/**
 * What a scope is told about each query: the platform, the origin pane, how many rows it may return
 * and which commands are trivial enough to rank below real work (empty: none are).
 */
record PaletteContext(boolean macOs, PaletteTarget target, int maxResults, List<String> trivialCommands) {
    static final int DEFAULT_MAX_RESULTS = 5;
    static final int MIN_MAX_RESULTS = 1;
    static final int MAX_MAX_RESULTS = 20;

    PaletteContext {
        Objects.requireNonNull(target);
        trivialCommands = List.copyOf(trivialCommands);
        if (maxResults < MIN_MAX_RESULTS || maxResults > MAX_MAX_RESULTS)
            throw new IllegalArgumentException("Max results must be " + MIN_MAX_RESULTS + "\u2013" + MAX_MAX_RESULTS);
    }

    PaletteContext(boolean macOs, PaletteTarget target) { this(macOs, target, DEFAULT_MAX_RESULTS); }

    PaletteContext(boolean macOs, PaletteTarget target, int maxResults) {
        this(macOs, target, maxResults, ShellHistoryScope.DEFAULT_TRIVIAL);
    }
}
