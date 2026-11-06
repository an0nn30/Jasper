package dev.jasper.browser.store;

import java.awt.EventQueue;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
     * Parks {@code stores}' database thread until the answer is run, so that the work given meanwhile queues behind it.
     * It starts the database. Not on the UI thread.
     *
     * @param stores stores this made
     * @return releases the thread
     * @throws Exception if the thread does not park within 10 seconds
     */
    public Runnable hold(Stores stores) throws Exception {
        BrowserDatabase database = made.stream().filter(one -> one.stores() == stores).findFirst().orElseThrow().database();
        var parked = new CountDownLatch(1);
        var released = new CountDownLatch(1);
        database.call(connection -> {
            parked.countDown();
            try {
                if (!released.await(30, TimeUnit.SECONDS)) throw new SQLException("The test never released the database");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new SQLException("The hold was interrupted", interrupted);
            }
            return null;
        });
        if (!parked.await(10, TimeUnit.SECONDS)) throw new AssertionError("The database's thread did not park");
        return released::countDown;
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
