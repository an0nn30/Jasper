package dev.jasper.browser.item;

import dev.jasper.browser.page.PageContext;
import dev.jasper.browser.store.Stores;
import dev.jasper.sdk.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * What one browsing group of items shares (spec section 12): the request context their pages are made with, their
 * history and bookmarks ({@link Stores}) and the certificates the user went on past in them. The persistent store is
 * Chromium's global context and the database in the plugin's data folder, which every item shares that opened in no
 * other. A /dev/null store holds an in-memory context and an in-memory database, which the items a /dev/null item opens
 * share; it counts its open items ({@link #retain}, {@link #release}) and closes as the last of them closes: its stores
 * close, its allowances go and its context is released. The persistent store closes only with the plugin's
 * {@link DataStores}. UI thread, except where a method says otherwise.
 */
public final class DataStore {
    private final boolean devNull;
    private final PageContext context;
    private final Stores stores;
    private final CertificateAllowance allowance = new CertificateAllowance();
    // Told as the store closes, so its DataStores forget it.
    private final Consumer<DataStore> ended;
    private final List<Runnable> closes = new ArrayList<>();
    private int items;
    // Read on any thread, by the queries of Jasper's pages.
    private volatile boolean closed;

    /**
     * A store over {@code context} and {@code stores}, which it closes as it closes.
     *
     * @param devNull whether it is a /dev/null group's, which closes with its last item
     * @param context the context its pages are made with
     * @param stores its history and bookmarks
     * @param ended told once, as it closes
     */
    DataStore(boolean devNull, PageContext context, Stores stores, Consumer<DataStore> ended) {
        this.devNull = devNull;
        this.context = Objects.requireNonNull(context, "context");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.ended = Objects.requireNonNull(ended, "ended");
    }

    /**
     * Whether it is a /dev/null group's store, which keeps nothing past its last item.
     *
     * @return true for a /dev/null store
     */
    public boolean devNull() {
        return devNull;
    }

    /**
     * The request context its items' pages are made with.
     *
     * @return the context
     */
    public PageContext context() {
        return context;
    }

    /**
     * Its history and bookmarks, and the indexes of them. Any thread.
     *
     * @return the stores
     */
    public Stores stores() {
        return stores;
    }

    /** The certificates the user went on past in its items, which last as long as it does. Any thread. */
    CertificateAllowance allowance() {
        return allowance;
    }

    /**
     * Completes once what an item's first page load reads of the store is ready, so the item makes its page's browser
     * only then: at once for now.
     *
     * @return the future
     */
    public CompletableFuture<Void> ready() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * An item opens in the store, which counts it.
     *
     * @throws IllegalStateException once the store has closed
     */
    public void retain() {
        if (closed) throw new IllegalStateException("A closed data store holds no items");
        items++;
    }

    /**
     * An item in the store closes: a /dev/null store closes as its last item does. Once the store has closed, as the
     * plugin's stop closes every store before its items' tabs close, nothing happens.
     *
     * @throws IllegalStateException for an open store that holds no item
     */
    public void release() {
        if (closed) return;
        if (items == 0) throw new IllegalStateException("The data store holds no item to release");
        items--;
        if (items == 0 && devNull) close();
    }

    /**
     * Tells {@code listener} once, as the store closes; at once when it has closed already.
     *
     * @param listener told on the UI thread
     * @return the registration
     */
    public Subscription onClosed(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) {
            listener.run();
            return () -> { };
        }
        closes.add(listener);
        return () -> closes.remove(listener);
    }

    /**
     * Whether the store has closed. Any thread.
     *
     * @return whether it closed
     */
    public boolean closed() {
        return closed;
    }

    /**
     * Closes the store, once: its stores close, as their database does (the work given before still runs), its allowances
     * go, its context is released, and those who asked {@link #onClosed} are told.
     */
    void close() {
        if (closed) return;
        closed = true;
        stores.close();
        allowance.clear();
        context.close();
        ended.accept(this);
        List<Runnable> told = List.copyOf(closes);
        closes.clear();
        for (Runnable listener : told) listener.run();
    }
}
