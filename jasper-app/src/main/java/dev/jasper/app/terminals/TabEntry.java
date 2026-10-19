package dev.jasper.app.terminals;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One open tab, as functions over it (workspace tabs spec 9.4): a terminal tab, whose {@code terminal} is its one pane,
 * or a content tab, whose {@code terminal} is empty. EDT only.
 */
public record TabEntry(UUID id, UUID windowId, Kind kind, Supplier<Optional<PaneEntry>> terminal, Supplier<String> title,
                       Runnable select) {
    /** What a tab holds; the plugin runtime reports it to plugins as the SDK's tab kind. */
    public enum Kind {
        /** One terminal pane. */
        TERMINAL,
        /** Anything else: a plugin's view, an editor, a tool view. */
        CONTENT
    }

    /** Entries are recreated on every query; a tab is its id. */
    @Override public boolean equals(Object other) { return other instanceof TabEntry entry && entry.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
}
