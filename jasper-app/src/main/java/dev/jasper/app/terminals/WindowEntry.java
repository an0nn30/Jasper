package dev.jasper.app.terminals;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One open terminal window, as functions over it. EDT only. {@code openTab} takes the directory to start in,
 * or empty for where New Tab would start, and returns the new tab's pane, or empty when the window is closing.
 */
public record WindowEntry(UUID id, Supplier<List<TabEntry>> tabs, Supplier<Optional<TabEntry>> selectedTab, BooleanSupplier active,
                          Runnable toFront, Function<Optional<Path>, Optional<PaneEntry>> openTab) {
    /** A window is its id. */
    @Override public boolean equals(Object other) { return other instanceof WindowEntry entry && entry.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
}
