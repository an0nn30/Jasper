package dev.jasper.sdk.activity;

import dev.jasper.sdk.events.Topic;
import java.util.List;

/**
 * Begins activities and lists the running ones. Anyone may subscribe to {@link #TOPIC}; only handles
 * returned by {@link #begin} publish to it, so the lifecycle is well-formed and the source is verified.
 */
public interface Activities {
    /** Every activity's events, from every plugin. */
    Topic<ActivityEvent> TOPIC = Topic.of("jasper.activity", ActivityEvent.class);

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
     * The latest event of each running activity, the one deliberate exception to "no replay", so a
     * consumer that starts late can show work already under way.
     *
     * @return an immutable snapshot
     */
    List<ActivityEvent> current();
}
