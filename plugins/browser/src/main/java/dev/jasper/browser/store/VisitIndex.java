package dev.jasper.browser.store;

import dev.jasper.browser.store.HistoryStore.Visit;
import dev.jasper.sdk.Subscription;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * The visited sites as the UI thread reads them, for the address field's suggestions and the palette: the
 * {@value #MAX} rows of history most recently visited, so those are all they ever find. It holds none until its first
 * {@link #load}, which reads them in the background. From then on it takes each visit an item records
 * ({@link #recorded}), reads them again after a delete or a clear, and tells {@link #onChanged} each time it holds them
 * anew. When the database cannot run, it holds none. UI thread.
 */
public final class VisitIndex {
    /** The most rows the index holds. */
    public static final int MAX = 10_000;
    private static final System.Logger LOG = System.getLogger(VisitIndex.class.getName());
    /** Most recent first, as {@link HistoryStore#visits} orders them. */
    private static final Comparator<Visit> RECENT = Comparator.comparingLong(Visit::lastVisit).thenComparingLong(Visit::id).reversed();
    private final HistoryStore store;
    private final Executor ui;
    private final Changes changes = new Changes();
    private List<Visit> visits = List.of();
    private boolean asked;

    VisitIndex(HistoryStore store, Executor ui) {
        this.store = Objects.requireNonNull(store, "store");
        this.ui = Objects.requireNonNull(ui, "ui");
        // Told on the database's thread after a delete or a clear: the UI thread asks again.
        store.onChanged(() -> ui.execute(this::storeChanged));
    }

    /** Reads the rows in the background the first time it is called; later calls do nothing. */
    public void load() {
        if (asked) return;
        asked = true;
        read();
    }

    /**
     * The rows held.
     *
     * @return them, most recently visited first; none before the first load has read them
     */
    public List<Visit> all() {
        return visits;
    }

    /**
     * A visit an item recorded, or a title it gave one: the row takes its place among the most recent, in place of the
     * row it was, and the oldest row leaves when the index holds more than {@value #MAX}. Before the first load the index
     * leaves it to that load, which reads it.
     *
     * @param visit the row as the store answered it
     */
    public void recorded(Visit visit) {
        Objects.requireNonNull(visit, "visit");
        if (!asked) return;
        var next = new ArrayList<Visit>(visits.size() + 1);
        for (Visit held : visits) if (held.id() != visit.id()) next.add(held);
        int at = Collections.binarySearch(next, visit, RECENT);
        next.add(at < 0 ? -at - 1 : at, visit);
        if (next.size() > MAX) next.removeLast();
        visits = Collections.unmodifiableList(next);
        changes.tell();
    }

    /**
     * Tells {@code listener}, on the UI thread, each time the index holds the rows anew.
     *
     * @param listener the listener
     * @return ends the listening
     */
    public Subscription onChanged(Runnable listener) {
        return changes.add(listener);
    }

    private void storeChanged() {
        if (asked) read();
    }

    private void read() {
        store.visits(MAX).whenComplete((read, failure) -> ui.execute(() -> held(read, failure)));
    }

    private void held(List<Visit> read, Throwable failure) {
        if (failure != null) {
            // The database says once why it cannot run (BrowserDatabase's unavailable); a closed one answers no more.
            LOG.log(System.Logger.Level.DEBUG, "The browser's visited sites could not be read", failure);
            return;
        }
        visits = List.copyOf(read);
        changes.tell();
    }
}
