package dev.jasper.sdk.activity;

import dev.jasper.sdk.events.Topic;
import java.util.List;

/**
 * Begins activities, announces connections and lists the running activities. Anyone may subscribe to
 * {@link #TOPIC} and {@link #CONNECTIONS}; only handles returned by {@link #begin} and {@link #connection}
 * publish to them, so each lifecycle is well-formed and its source is verified.
 */
public interface Activities {
    /** Every activity's events, from every plugin. */
    Topic<ActivityEvent> TOPIC = Topic.of("jasper.activity", ActivityEvent.class);

    /**
     * Every connection's events, from every plugin.
     *
     * @since 0.10.2
     */
    Topic<ConnectionEvent> CONNECTIONS = Topic.of("jasper.connection", ConnectionEvent.class);

    /**
     * Begins an activity and publishes its STARTED event. An activity still open when its plugin
     * stops is ended with FAILED by the runtime.
     *
     * @param spec what the activity is
     * @return the handle that reports progress and ends it
     * @throws IllegalStateException when the plugin's context is closed
     */
    ActivityHandle begin(ActivitySpec spec);

    /**
     * Announces a connection and publishes its first state. A connection still open when its plugin
     * stops is closed by the runtime.
     *
     * @param spec what the connection is
     * @return the handle that reports its state and closes it
     * @throws IllegalStateException when the plugin's context is closed
     * @since 0.10.2
     */
    ConnectionHandle connection(ConnectionSpec spec);

    /**
     * The latest event of each running activity, the one deliberate exception to "no replay", so a
     * consumer that starts late can show work already under way.
     *
     * @return an immutable snapshot
     */
    List<ActivityEvent> current();
}
