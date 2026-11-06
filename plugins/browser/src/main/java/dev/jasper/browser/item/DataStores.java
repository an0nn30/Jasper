package dev.jasper.browser.item;

import dev.jasper.browser.page.PageContext;
import dev.jasper.browser.store.BrowserDatabase;
import dev.jasper.browser.store.Stores;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The plugin's data stores (spec section 12): the persistent one, over the history and bookmarks the plugin makes in
 * {@code browser.sqlite}, which still open only on their first use, and a new one for each group of /dev/null items,
 * with an in-memory database and an in-memory context, which closes with its last item. Making it, and making a store,
 * opens nothing and asks nothing of Chromium. It also knows, from any thread, which store each page's browser is in, as
 * Chromium names the browser ({@link #forBrowser}), so Jasper's pages answer their queries from the asking browser's
 * store. UI thread, except where a method says otherwise.
 */
public final class DataStores implements AutoCloseable {
    /** Why a query from a browser with no open store fails. */
    static final String NO_STORE = "Jasper could not answer this request.";
    private final Stores plugins;
    private final Supplier<Stores> memory;
    private final Supplier<PageContext> contexts;
    private final BooleanSupplier devNullMode;
    // Filled and emptied on Chromium's thread or the UI thread, read on the background executor.
    private final Map<Integer, DataStore> browsers = new ConcurrentHashMap<>();
    private final List<DataStore> devNull = new ArrayList<>();
    // Made the first time it is asked for.
    private DataStore persistent;
    // Read by Chromium's thread as it names a browser.
    private volatile boolean closed;

    /**
     * The stores whose persistent one holds {@code persistent}, and whose /dev/null ones each hold a new database in
     * memory and a new in-memory context.
     *
     * @param persistent the history and bookmarks in the plugin's data folder, which these close
     * @param devNullMode whether Use /dev/null mode is on now, which {@link #forNewItem()} reads each time an item opens;
     *     UI thread
     */
    public DataStores(Stores persistent, BooleanSupplier devNullMode) {
        this(persistent, () -> new Stores(BrowserDatabase.memory(), EventQueue::invokeLater), PageContext::memory, devNullMode);
    }

    /**
     * As {@link #DataStores(Stores, BooleanSupplier)}, each /dev/null store's history and bookmarks made by {@code memory}
     * and its context by {@code contexts}: a test's, which keeps them in memory and records the contexts' release.
     */
    DataStores(Stores persistent, Supplier<Stores> memory, Supplier<PageContext> contexts, BooleanSupplier devNullMode) {
        this.devNullMode = Objects.requireNonNull(devNullMode, "devNullMode");
        plugins = Objects.requireNonNull(persistent, "persistent");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    /**
     * The persistent store: Chromium's global context and the plugin's history and bookmarks, which open on their first
     * use, not now.
     *
     * @return the store, the same each time; closed once these have closed
     */
    public DataStore persistent() {
        if (persistent == null) {
            persistent = new DataStore(false, PageContext.persistent(), plugins, store -> { });
            if (closed) persistent.close();
        }
        return persistent;
    }

    /**
     * A new /dev/null store, for a new group of /dev/null items: a database and a context of its own, in memory, neither
     * made until used. It closes as the last item that retained it releases it.
     *
     * @return the store
     * @throws IllegalStateException once these have closed
     */
    public DataStore newDevNull() {
        if (closed) throw new IllegalStateException("The browser's data stores have closed");
        var store = new DataStore(true, contexts.get(), memory.get(), this::ended);
        devNull.add(store);
        return store;
    }

    /**
     * The store a new item opens in: a new /dev/null store while Use /dev/null mode is on, else the persistent one. For
     * New Browser Tab, New Browser Window, the split item, the links Jasper opens to the Browser and the palette's rows;
     * not for a page's links and popups, which open in their opener's store, nor for a restored item, which comes back in
     * the mode it was saved in. Turning the setting on or off changes no item that is open.
     *
     * @return the store
     * @throws IllegalStateException once these have closed, while the setting is on
     */
    public DataStore forNewItem() {
        return devNullMode.getAsBoolean() ? newDevNull() : persistent();
    }

    /**
     * The stores open now: the persistent one, once its database has opened, not merely because a restore or an item
     * asked for the store, then every /dev/null one still open, in the order they were made. Asking opens nothing.
     *
     * @return the stores
     */
    public List<DataStore> live() {
        var live = new ArrayList<DataStore>(devNull.size() + 1);
        if (!closed && plugins.opened()) live.add(persistent());
        live.addAll(devNull);
        return List.copyOf(live);
    }

    /**
     * The store {@code browser} is in: the store of the item whose page's browser Chromium gave that identifier, from
     * the moment Chromium made it until it closed it. Any thread.
     *
     * @param browser a browser's identifier, as Chromium gives it
     * @return the store; empty for a browser no page has, or whose store has closed
     */
    public Optional<DataStore> forBrowser(int browser) {
        return Optional.ofNullable(browsers.get(browser)).filter(store -> !store.closed());
    }

    /**
     * The answer {@code answer} gives from {@code browser}'s store, as Jasper's pages are answered from the asking
     * browser's: a /dev/null item's pages from its group's history and bookmarks. Any thread.
     *
     * @param browser the asking browser's identifier
     * @param answer the answer from a store; it returns at once
     * @param <T> what the answer gives
     * @return the answer; failed for a browser with no open store
     */
    public <T> CompletableFuture<T> answer(int browser, Function<DataStore, CompletableFuture<T>> answer) {
        Objects.requireNonNull(answer, "answer");
        return forBrowser(browser).map(answer).orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException(NO_STORE)));
    }

    /**
     * Chromium made {@code browser} for a page in {@code store}; once these have closed, at the plugin's stop, it is
     * forgotten at once. Any thread: Chromium's, before any of its documents asks.
     */
    void browserMade(int browser, DataStore store) {
        Objects.requireNonNull(store, "store");
        browsers.put(browser, store);
        if (closed) browsers.remove(browser, store); // named as these closed: close() clears after setting closed
    }

    /** Chromium closed {@code browser}, a page's in {@code store}. Any thread. */
    void browserClosed(int browser, DataStore store) {
        browsers.remove(browser, store);
    }

    /**
     * The plugin stops, after its items: every store closes, the persistent one too, whether asked for or not, and each
     * /dev/null store releases its context, which touches nothing of Chromium's once Chromium has stopped. Once.
     */
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (persistent != null) persistent.close();
        else plugins.close();
        for (DataStore store : List.copyOf(devNull)) store.close();
        browsers.clear();
    }

    /** How many browsers' stores these know: for tests. Any thread. */
    int browsersKnown() {
        return browsers.size();
    }

    /** A /dev/null store closed: it is no longer live, and no browser is in it. */
    private void ended(DataStore store) {
        devNull.remove(store);
        browsers.values().removeIf(each -> each == store);
    }
}
