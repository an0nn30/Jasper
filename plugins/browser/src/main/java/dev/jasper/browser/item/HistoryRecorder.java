package dev.jasper.browser.item;

import dev.jasper.browser.page.LoadFailure;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.store.Addresses;
import dev.jasper.browser.store.HistoryStore.Recorded;
import dev.jasper.browser.store.Reached;
import dev.jasper.browser.store.Stores;
import java.awt.EventQueue;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * One browser item's history (spec section 10). Each document its page's main frame starts loading, at an address
 * history keeps, is recorded with how the load reached it, as one entry, and the visit index hears of it. The entry then
 * takes the page's titles: the first change waits {@value #TITLE_WAIT_MILLIS} ms, the changes meanwhile coalesce, and the
 * latest lands when the wait ends, when the next document starts loading, when the item closes or when the plugin stops
 * ({@link #flush}). Each title is queued behind its load's record, so the titles land in order whether or not the record
 * has answered, and those given at the plugin's stop land before the stores close. A document that fails before its load
 * ends, with an error Jasper shows its error page for, leaves no entry: a failure of the address it started at deletes
 * its entry, and the store takes its visit off its row. One that stops loading, as Stop makes it ({@code ERR_ABORTED}),
 * keeps its entry. Answers reach the recorder on the UI thread; a store that cannot answer, closed or unavailable,
 * records nothing. UI thread.
 */
final class HistoryRecorder {
    /** How long a title waits for the changes after it. */
    static final int TITLE_WAIT_MILLIS = 1_000;
    private static final Duration TITLE_WAIT = Duration.ofMillis(TITLE_WAIT_MILLIS);
    private static final Runnable NOTHING = () -> { };
    private final Page page;
    private final Stores stores;
    private final BiFunction<Duration, Runnable, Runnable> later;
    // The visit of the document the page shows; null before the first load start, and for an address history skips.
    private Visited visit;
    // Ends the title's wait.
    private Runnable titleWait = NOTHING;

    /** A visit, its entry and its titles. UI thread. */
    private static final class Visited {
        final String address;
        // The store's record of the load, which each title is stored behind.
        final CompletableFuture<Optional<Recorded>> record;
        // Whether the record has answered on the UI thread, and the entry it made, for a failure to delete; empty for none.
        boolean answered;
        OptionalLong entry = OptionalLong.empty();
        // The title given last, and whether it waits to be stored.
        String title = "";
        boolean pending;
        // Whether the load failed before its document loaded: the entry goes.
        boolean failed;

        Visited(String address, CompletableFuture<Optional<Recorded>> record) {
            this.address = address;
            this.record = record;
        }
    }

    /**
     * The history of the item whose page is {@code page}, in {@code stores}; {@code later} runs a task on the UI thread
     * after a delay and answers what cancels it.
     */
    HistoryRecorder(Page page, Stores stores, BiFunction<Duration, Runnable, Runnable> later) {
        this.page = Objects.requireNonNull(page, "page");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.later = Objects.requireNonNull(later, "later");
        page.onVisit(this::visited);
        page.onLoadFailure(this::failed);
    }

    /** The page's title is now {@code title}: the visit takes it once the wait ends. A blank title, no title, is not stored. */
    void titled(String title) {
        Visited current = visit;
        if (current == null || title.isBlank() || title.equals(current.title)) return;
        current.title = title;
        current.pending = true;
        if (titleWait == NOTHING) titleWait = later.apply(TITLE_WAIT, this::waited);
    }

    /** The item closes, or the plugin stops: the visit's latest title is stored now, or once its entry exists. */
    void flush() {
        titleWait.run();
        titleWait = NOTHING;
        if (visit != null) store(visit);
    }

    /** The main frame started loading {@code address}, reached {@code reached}: the visit before it gets its title first. */
    private void visited(String address, Reached reached) {
        flush();
        visit = null;
        if (Addresses.storable(address).isEmpty()) return; // Jasper's own pages, about:blank and the like: no entry
        CompletableFuture<Optional<Recorded>> record = stores.history().record(address, reached, System.currentTimeMillis());
        var started = new Visited(address, record);
        visit = started;
        // A failed record, as from a closed or unavailable store, answers no entry.
        record.whenComplete((recorded, failure) -> EventQueue.invokeLater(() ->
            answered(started, failure == null ? recorded : Optional.empty())));
    }

    /** The store recorded {@code started}'s load as {@code recorded}, or nothing. */
    private void answered(Visited started, Optional<Recorded> recorded) {
        started.answered = true;
        if (recorded.isEmpty()) return;
        started.entry = OptionalLong.of(recorded.get().entry());
        if (started.failed) {
            delete(started);
            return;
        }
        stores.visitIndex().recorded(recorded.get().visit());
    }

    /** The title's wait ended: the visit's latest title is stored. */
    private void waited() {
        titleWait = NOTHING;
        if (visit != null) store(visit);
    }

    /**
     * Stores {@code titled}'s latest title on its entry, queued behind its record, so the titles land in the order given,
     * whether or not the record has answered.
     */
    private void store(Visited titled) {
        if (!titled.pending || titled.failed) return;
        titled.pending = false;
        stores.history().retitle(titled.record, titled.title).whenComplete((row, failure) -> EventQueue.invokeLater(() -> {
            if (failure == null && !titled.failed) row.ifPresent(stores.visitIndex()::recorded);
        }));
    }

    /**
     * A load of the page's main frame failed: when it is the document whose visit this is, failing before it loaded with
     * an error Jasper shows its error page for, the visit was none. A document that stops loading, as Stop makes it,
     * fails with {@code ERR_ABORTED}, which shows none, and keeps its visit.
     */
    private void failed(LoadFailure failure) {
        Visited current = visit;
        if (current == null || ErrorPage.title(failure.code()).isEmpty() || page.documentLoaded()
            || !current.address.equals(failure.url())) return;
        current.failed = true;
        if (current.answered) delete(current);
    }

    private void delete(Visited failed) {
        OptionalLong entry = failed.entry;
        failed.entry = OptionalLong.empty();
        if (entry.isPresent()) stores.history().delete(entry.getAsLong());
    }
}
