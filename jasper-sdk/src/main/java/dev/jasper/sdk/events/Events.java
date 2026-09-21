package dev.jasper.sdk.events;

import dev.jasper.sdk.Subscription;
import java.util.function.Consumer;

/**
 * The central bus. Publication always enqueues; handlers run later on the UI thread in one global
 * first-in, first-out order, never synchronously and never re-entrantly. There is no replay: read
 * current state from its owner, then listen.
 */
public interface Events {
    /**
     * Subscribes on the UI thread. A handler that throws is logged against its plugin and does not
     * affect other subscribers.
     *
     * @param topic the topic; its payload type must agree with every other use of the same id
     * @param handler runs on the UI thread for each later publication
     * @param <T> payload type
     * @return the registration
     * @throws IllegalArgumentException when the id is already in use with another payload type
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    <T> Subscription subscribe(Topic<T> topic, Consumer<? super T> handler);

    /**
     * Publishes from any thread to a topic the caller owns.
     *
     * @param topic a topic whose id starts with the publishing plugin's id followed by a dot
     * @param payload non-null instance of the topic's payload type
     * @param <T> payload type
     * @throws IllegalArgumentException when the caller does not own the topic or the type disagrees
     * @throws IllegalStateException when the plugin's context is closed
     */
    <T> void publish(Topic<T> topic, T payload);
}
