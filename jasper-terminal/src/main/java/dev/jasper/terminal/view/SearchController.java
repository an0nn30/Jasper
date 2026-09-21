package dev.jasper.terminal.view;

import dev.jasper.terminal.internal.text.TerminalSearch;
import dev.jasper.terminal.search.FindResult;
import dev.jasper.terminal.search.SearchQuery;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.regex.PatternSyntaxException;
import javax.swing.SwingUtilities;

/** Owns EDT search results and bounded worker admission with generation-checked publication. */
final class SearchController {
    private final Function<SearchQuery,List<TerminalSearch.Match>> search;
    private final LongConsumer reveal;
    private final LongSupplier rowEpoch;
    private final Runnable repaint;
    private static final long SEARCH_IDLE_MILLIS = 1_000;
    private List<TerminalSearch.Match> matches = List.of();
    private int currentMatch = -1;
    private final Object searchLock = new Object();
    private long searchGeneration;
    private ThreadPoolExecutor searchExecutor;
    private Future<?> pendingSearch;

    SearchController(Function<SearchQuery,List<TerminalSearch.Match>> search, LongSupplier rowEpoch,
                     LongConsumer reveal, Runnable repaint) {
        this.search = Objects.requireNonNull(search);
        this.rowEpoch = Objects.requireNonNull(rowEpoch);
        this.reveal = Objects.requireNonNull(reveal);
        this.repaint = Objects.requireNonNull(repaint);
    }
    List<TerminalSearch.Match> matches() { return matches; }
    int currentIndex() { return currentMatch; }
    /** Finds matches in the scrollback and screen and shows the newest one. */
    public FindResult find(SearchQuery query) {
        cancelPending();
        try {
            matches = List.copyOf(search.apply(query));
        } catch (PatternSyntaxException invalid) {
            matches = List.of();
            currentMatch = -1;
            repaint.run();
            return new FindResult(0, 0, invalid.getDescription());
        }
        currentMatch = matches.size() - 1;
        revealCurrentMatch();
        return findResult();
    }

    /**
     * Searches away from the Event Dispatch Thread. At most one running and one queued request are retained; only the
     * latest generation may change highlights or invoke its callback, and that callback always runs on the EDT.
     */
    public void findAsync(SearchQuery query, Consumer<FindResult> callback) {
        Consumer<FindResult> completion = callback == null ? result -> { } : callback;
        long generation;
        long requestedEpoch = rowEpoch.getAsLong();
        ThreadPoolExecutor executor;
        synchronized (searchLock) {
            generation = ++searchGeneration;
            if (pendingSearch != null) {
                pendingSearch.cancel(true);
            }
            executor = searchExecutor();
            executor.getQueue().clear();
            pendingSearch = executor.submit(() -> calculateFind(generation, requestedEpoch, query, completion));
        }
    }

    /** Moves to the next newer match, wrapping around. */
    public FindResult next() {
        return stepMatch(1);
    }

    /** Moves to the next older match, wrapping around. */
    public FindResult previous() {
        return stepMatch(-1);
    }

    public void clear() {
        cancelPending();
        matches = List.of();
        currentMatch = -1;
        repaint.run();
    }

    private void calculateFind(long generation, long requestedEpoch, SearchQuery query,
                               Consumer<FindResult> callback) {
        List<TerminalSearch.Match> found;
        FindResult result;
        try {
            found = List.copyOf(search.apply(query));
            result = new FindResult(found.size(), found.size(), null);
        } catch (PatternSyntaxException invalid) {
            found = List.of();
            result = new FindResult(0, 0, invalid.getDescription());
        }
        List<TerminalSearch.Match> completedMatches = found;
        FindResult completedResult = result;
        SwingUtilities.invokeLater(() -> applyFind(generation, requestedEpoch, completedMatches, completedResult, callback));
    }

    private void applyFind(long generation, long requestedEpoch, List<TerminalSearch.Match> found, FindResult result,
                           Consumer<FindResult> callback) {
        synchronized (searchLock) {
            if (generation != searchGeneration) {
                return;
            }
            pendingSearch = null;
            // Reset notifications are reconciled on EDT, possibly behind this queued completion.
            // Read the atomic row epoch directly so obsolete coordinates never publish first.
            if (requestedEpoch != rowEpoch.getAsLong()) return;
        }
        matches = found;
        currentMatch = found.size() - 1;
        revealCurrentMatch();
        callback.accept(result);
    }

    private ThreadPoolExecutor searchExecutor() {
        if (searchExecutor == null || searchExecutor.isShutdown()) {
            searchExecutor = new ThreadPoolExecutor(1, 1, SEARCH_IDLE_MILLIS, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), runnable -> {
                    Thread thread = new Thread(runnable, "jasper-terminal-search");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardOldestPolicy());
            searchExecutor.allowCoreThreadTimeOut(true);
        }
        return searchExecutor;
    }

    void cancelPending() {
        synchronized (searchLock) {
            searchGeneration++;
            if (pendingSearch != null) {
                pendingSearch.cancel(true);
                pendingSearch = null;
            }
            if (searchExecutor != null) {
                searchExecutor.getQueue().clear();
            }
        }
    }

    private FindResult stepMatch(int direction) {
        if (matches.isEmpty()) {
            return findResult();
        }
        currentMatch = Math.floorMod(currentMatch + direction, matches.size());
        revealCurrentMatch();
        return findResult();
    }

    private void revealCurrentMatch() {
        if (currentMatch >= 0) {
            reveal.accept(matches.get(currentMatch).row());
        }
        repaint.run();
    }

    private FindResult findResult() {
        return new FindResult(matches.size(), currentMatch + 1, null);
    }
}
