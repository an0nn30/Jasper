package dev.jasper.browser.store;

import dev.jasper.sdk.Subscription;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Change listeners: a store's, told on the database's thread once a change has been written, and an index's, told on the
 * UI thread once it holds the change.
 */
final class Changes {
    private static final System.Logger LOG = System.getLogger(Changes.class.getName());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /**
     * Adds a listener.
     *
     * @param listener told after each change, on the owner's thread
     * @return ends the listening
     */
    Subscription add(Runnable listener) {
        // A wrapper of its own, so that ending one of two subscriptions of the same listener ends that one.
        Runnable entry = Objects.requireNonNull(listener, "listener")::run;
        listeners.add(entry);
        return () -> listeners.remove(entry);
    }

    /** Tells every listener. One that throws is logged; the change stands, and the others still hear of it. */
    void tell() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "A listener of the browser's history or bookmarks failed", failure);
            }
        }
    }
}
