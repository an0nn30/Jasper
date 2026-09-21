package dev.jasper.app;

import dev.jasper.app.config.PaletteSettings;
import dev.jasper.app.config.HistorySettings;

import java.util.List;
import java.util.Objects;

/**
 * What a scope is told about each query: the platform, the origin pane, how many rows it may return
 * and which commands are trivial enough to rank below real work (empty: none are).
 */
record PaletteContext(boolean macOs, PaletteTarget target, int maxResults, List<String> trivialCommands) {

    PaletteContext {
        Objects.requireNonNull(target);
        trivialCommands = List.copyOf(trivialCommands);
        if (maxResults < PaletteSettings.MIN_MAX_RESULTS || maxResults > PaletteSettings.MAX_MAX_RESULTS)
            throw new IllegalArgumentException("Max results must be " + PaletteSettings.MIN_MAX_RESULTS + "\u2013" + PaletteSettings.MAX_MAX_RESULTS);
    }

    PaletteContext(boolean macOs, PaletteTarget target) { this(macOs, target, PaletteSettings.DEFAULT_MAX_RESULTS); }

    PaletteContext(boolean macOs, PaletteTarget target, int maxResults) {
        this(macOs, target, maxResults, HistorySettings.defaults().trivialCommands());
    }
}
