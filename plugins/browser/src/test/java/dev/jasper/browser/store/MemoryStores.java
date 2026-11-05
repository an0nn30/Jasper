package dev.jasper.browser.store;

import java.awt.EventQueue;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Browsing stores in memory for one test, which closes them in its {@code @AfterEach}: none opens a file, and each
 * closes its database and its thread. Their answers reach the UI thread as the plugin's do, through
 * {@link EventQueue#invokeLater}, so an item that hears them hears them on the UI thread.
 */
public final class MemoryStores {
    private final List<Made> made = new CopyOnWriteArrayList<>();

    private record Made(BrowserDatabase database, Stores stores) { }

    /** Creates the test's stores; none is made yet. */
    public MemoryStores() { }

    /**
     * New stores in memory, closed with the others.
     *
     * @return the stores, their database not yet opened
     */
    public Stores make() {
        var database = BrowserDatabase.memory();
        var stores = new Stores(database, EventQueue::invokeLater);
        made.add(new Made(database, stores));
        return stores;
    }

    /**
     * Waits until the work each store's database was given has run and the UI thread has run what the answers handed it,
     * three times over, for answers that give the database more work and so on. Not on the UI thread.
     *
     * @throws Exception if a wait fails or takes longer than 10 seconds
     */
    public void settle() throws Exception {
        for (int round = 0; round < 3; round++) {
            for (Made one : made)
                if (one.database().started()) one.database().call(connection -> null).handle((done, failure) -> null).get(10, TimeUnit.SECONDS);
            EventQueue.invokeAndWait(() -> { });
        }
    }

    /**
     * Whether {@code stores}' database has started its thread, which only its first call does.
     *
     * @param stores stores
     * @return whether it started
     */
    public static boolean started(Stores stores) {
        return stores.started();
    }

    /**
     * Closes every store made and waits until each has closed.
     *
     * @throws Exception if a wait fails or takes longer than 30 seconds
     */
    public void close() throws Exception {
        for (Made one : made) one.stores().close();
        for (Made one : made) one.stores().closed().get(30, TimeUnit.SECONDS);
        made.clear();
    }
}
