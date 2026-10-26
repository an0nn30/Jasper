package dev.jasper.app.terminals;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One open tab, as functions over it (workspace tabs spec 9.4): a terminal tab, whose {@code terminal} is its one pane,
 * or a content tab, whose {@code terminal} is empty. {@code namePieces} gives the pieces a terminal tab's automatic name
 * is made from; empty for a content tab. EDT only.
 */
public record TabEntry(UUID id, UUID windowId, Kind kind, Supplier<Optional<PaneEntry>> terminal, Supplier<String> title,
                       Runnable select, Supplier<Map<String, String>> namePieces) {
    /** What a tab holds; the plugin runtime reports it to plugins as the SDK's tab kind. */
    public enum Kind {
        /** One terminal pane. */
        TERMINAL,
        /** Anything else: a plugin's view, an editor, a tool view. */
        CONTENT
    }

    /** A tab with no name pieces, as a content tab has. */
    public TabEntry(UUID id, UUID windowId, Kind kind, Supplier<Optional<PaneEntry>> terminal, Supplier<String> title,
                    Runnable select) {
        this(id, windowId, kind, terminal, title, select, Map::of);
    }

    /** Entries are recreated on every query; a tab is its id. */
    @Override public boolean equals(Object other) { return other instanceof TabEntry entry && entry.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
}
