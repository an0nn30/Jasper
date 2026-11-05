package dev.jasper.browser.store;

import dev.jasper.browser.store.BookmarkStore.Bookmark;
import dev.jasper.sdk.Subscription;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * The bookmarks as the UI thread reads them, for the menu button, the address field's suggestions and the palette. It
 * holds none until its first {@link #load}, which reads them all in the background; from then on it reads them again
 * after each change the store writes, and tells {@link #onChanged} each time it holds them anew. When the database cannot
 * run, it holds none. UI thread.
 */
public final class BookmarkIndex {
    private static final System.Logger LOG = System.getLogger(BookmarkIndex.class.getName());
    private final BookmarkStore store;
    private final Executor ui;
    private final Changes changes = new Changes();
    private List<Bookmark> all = List.of();
    private Map<String, Bookmark> byAddress = Map.of();
    private boolean asked;

    BookmarkIndex(BookmarkStore store, Executor ui) {
        this.store = Objects.requireNonNull(store, "store");
        this.ui = Objects.requireNonNull(ui, "ui");
        // Told on the database's thread: the UI thread asks again.
        store.onChanged(() -> ui.execute(this::storeChanged));
    }

    /** Reads the bookmarks in the background the first time it is called; later calls do nothing. */
    public void load() {
        if (asked) return;
        asked = true;
        read();
    }

    /**
     * Whether an address is bookmarked.
     *
     * @param address a page's address
     * @return whether its bookmark is held; false for an address bookmarks never keep
     */
    public boolean has(String address) {
        return get(address).isPresent();
    }

    /**
     * An address's bookmark.
     *
     * @param address a page's address, as stored or with a user name and password, which bookmarks never keep
     * @return its bookmark; empty when none is held
     */
    public Optional<Bookmark> get(String address) {
        return Addresses.storable(address).map(byAddress::get);
    }

    /**
     * The bookmarks held.
     *
     * @return them, newest first; none before the first load has read them
     */
    public List<Bookmark> all() {
        return all;
    }

    /**
     * Tells {@code listener}, on the UI thread, each time the index holds the bookmarks anew.
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
        store.all().whenComplete((read, failure) -> ui.execute(() -> held(read, failure)));
    }

    private void held(List<Bookmark> read, Throwable failure) {
        if (failure != null) {
            // The database says once why it cannot run (BrowserDatabase's unavailable); a closed one answers no more.
            LOG.log(System.Logger.Level.DEBUG, "The browser's bookmarks could not be read", failure);
            return;
        }
        var addresses = new HashMap<String, Bookmark>();
        for (Bookmark bookmark : read) addresses.put(bookmark.url(), bookmark);
        all = List.copyOf(read);
        byAddress = Map.copyOf(addresses);
        changes.tell();
    }
}
