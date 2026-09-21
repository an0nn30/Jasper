package dev.jasper.app.plugins;

import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns activity lifecycles: one STARTED, coalesced PROGRESS, exactly one terminal event, the source
 * stamped here rather than by the plugin. Handles are safe from any thread.
 */
final class ActivityHub {
    private final EventBus bus;
    private final ConcurrentHashMap<UUID, ActivityEvent> running = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Handle> handles = new ConcurrentHashMap<>();

    ActivityHub(EventBus bus) {
        this.bus = bus;
        bus.checkType(Activities.TOPIC);
    }

    ActivityHandle begin(String pluginId, ActivitySpec spec) {
        var handle = new Handle(pluginId, Objects.requireNonNull(spec, "spec"));
        ActivityEvent started = handle.event(ActivityEvent.State.STARTED, OptionalDouble.empty(), spec.detail());
        running.put(handle.id, started);
        handles.put(handle.id, handle);
        bus.enqueue(() -> bus.deliver(Activities.TOPIC.id(), started));
        return handle;
    }

    List<ActivityEvent> current() { return List.copyOf(running.values()); }

    /** Ends every activity the plugin left open. */
    void failAll(String pluginId, String reason) {
        for (Handle handle : List.copyOf(handles.values()))
            if (handle.pluginId.equals(pluginId)) handle.fail(reason);
    }

    private final class Handle implements ActivityHandle {
        private final UUID id = UUID.randomUUID();
        private final String pluginId;
        private final ActivitySpec spec;
        private boolean ended;
        private OptionalDouble fraction = OptionalDouble.empty();
        private ActivityEvent waiting;

        Handle(String pluginId, ActivitySpec spec) { this.pluginId = pluginId; this.spec = spec; }

        ActivityEvent event(ActivityEvent.State state, OptionalDouble value, String detail) {
            return new ActivityEvent(id, pluginId, spec.title(), state, value, detail, spec.activateActionId());
        }

        @Override public UUID id() { return id; }

        @Override public void progress(double value, String detail) { report(OptionalDouble.of(value), detail); }

        @Override public void detail(String detail) {
            OptionalDouble last;
            synchronized (this) { last = fraction; }
            report(last, detail);
        }

        private void report(OptionalDouble value, String detail) {
            ActivityEvent event = event(ActivityEvent.State.PROGRESS, value, Objects.requireNonNull(detail, "detail"));
            boolean schedule;
            synchronized (this) {
                if (ended) return;
                fraction = value;
                running.put(id, event);
                schedule = waiting == null;
                waiting = event;
            }
            // One queued delivery carries whatever is latest when it runs.
            if (schedule) bus.enqueue(() -> {
                ActivityEvent latest;
                synchronized (this) { latest = waiting; waiting = null; }
                if (latest != null) bus.deliver(Activities.TOPIC.id(), latest);
            });
        }

        @Override public void succeed(String detail) { end(ActivityEvent.State.SUCCEEDED, detail); }
        @Override public void fail(String detail) { end(ActivityEvent.State.FAILED, detail); }
        @Override public void cancelled() { end(ActivityEvent.State.CANCELLED, ""); }

        private void end(ActivityEvent.State state, String detail) {
            ActivityEvent event;
            synchronized (this) {
                if (ended) return;
                ended = true;
                waiting = null;
                event = event(state, fraction, Objects.requireNonNull(detail, "detail"));
                running.remove(id);
                handles.remove(id);
            }
            bus.enqueue(() -> bus.deliver(Activities.TOPIC.id(), event));
        }
    }
}
