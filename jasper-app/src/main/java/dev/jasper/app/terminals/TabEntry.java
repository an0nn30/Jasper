package dev.jasper.app.terminals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** One open tab, as functions over it. EDT only. */
public record TabEntry(UUID id, UUID windowId, Supplier<List<PaneEntry>> panes, Supplier<Optional<PaneEntry>> focusedPane,
                       Supplier<String> title, Runnable select) {
    /** Entries are recreated on every query; a tab is its id. */
    @Override public boolean equals(Object other) { return other instanceof TabEntry entry && entry.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
}
