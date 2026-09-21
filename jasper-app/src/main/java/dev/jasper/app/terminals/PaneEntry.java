package dev.jasper.app.terminals;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One open pane, as functions over it; nothing here retains a Swing type. Every function is EDT-only and
 * tolerates a pane that closed meanwhile. {@code foregroundJob} starts the query on a worker and returns
 * its future. {@code split} returns the new pane, or empty when this pane cannot be split now.
 */
public record PaneEntry(UUID id, UUID tabId, Supplier<PaneSnapshot> snapshot,
                        Supplier<CompletableFuture<Optional<String>>> foregroundJob, Consumer<byte[]> write, Consumer<String> paste,
                        Supplier<Optional<String>> selection, Runnable focus,
                        BiFunction<SplitAxis, Optional<Path>, Optional<PaneEntry>> split) {
    /** Entries are recreated on every query; a pane is its id. */
    @Override public boolean equals(Object other) { return other instanceof PaneEntry entry && entry.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
}
