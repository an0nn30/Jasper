package dev.jasper.browser.store;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The browsing stores of one database: its history and bookmarks, and the indexes of them the UI thread reads. Making
 * them does no I/O and starts no thread; the database opens at its first call ({@link BrowserDatabase}).
 */
public final class Stores implements AutoCloseable {
    private final BrowserDatabase database;
    private final HistoryStore history;
    private final BookmarkStore bookmarks;
    private final BookmarkIndex bookmarkIndex;
    private final VisitIndex visitIndex;

    /**
     * The stores of {@code database}.
     *
     * @param database the database, which they close
     * @param ui runs a task on the UI thread, where the indexes hear the database's answers
     */
    public Stores(BrowserDatabase database, Executor ui) {
        this.database = Objects.requireNonNull(database, "database");
        Objects.requireNonNull(ui, "ui");
        history = new HistoryStore(database);
        bookmarks = new BookmarkStore(database);
        bookmarkIndex = new BookmarkIndex(bookmarks, ui);
        visitIndex = new VisitIndex(history, ui);
    }

    /**
     * The history.
     *
     * @return the history
     */
    public HistoryStore history() {
        return history;
    }

    /**
     * The bookmarks.
     *
     * @return the bookmarks
     */
    public BookmarkStore bookmarks() {
        return bookmarks;
    }

    /**
     * The bookmarks as the UI thread reads them.
     *
     * @return the index
     */
    public BookmarkIndex bookmarkIndex() {
        return bookmarkIndex;
    }

    /**
     * The visited sites as the UI thread reads them.
     *
     * @return the index
     */
    public VisitIndex visitIndex() {
        return visitIndex;
    }

    /** Closes the database as {@link BrowserDatabase#close} does: at once, the work given before it still running. */
    @Override public void close() {
        database.close();
    }

    /**
     * Completes once the database has closed ({@link BrowserDatabase#closed}).
     *
     * @return the future
     */
    public CompletableFuture<Void> closed() {
        return database.closed();
    }

    /**
     * Whether the database has opened: its first call opens it, and starts its thread. Asking opens nothing.
     *
     * @return whether it has had a call
     */
    public boolean opened() {
        return database.started();
    }
}
