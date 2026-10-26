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
 * Owns activity lifecycles: one STARTED, ordered phases, coalesced reports, exactly one terminal event, the source
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
        synchronized (handle) {
            running.put(handle.id, started);
            handles.put(handle.id, handle);
            bus.enqueue(() -> bus.deliver(Activities.TOPIC.id(), started));
        }
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
        private boolean input;
        private long deliveryToken;

        Handle(String pluginId, ActivitySpec spec) { this.pluginId = pluginId; this.spec = spec; }

        ActivityEvent event(ActivityEvent.State state, OptionalDouble value, String detail) {
            return new ActivityEvent(id, pluginId, spec.title(), state, value, detail, spec.activateActionId());
        }

        @Override public UUID id() { return id; }

        @Override public synchronized void progress(double value, String detail) { report(OptionalDouble.of(value), detail); }

        @Override public synchronized void detail(String detail) { report(fraction, detail); }

        private void report(OptionalDouble value, String detail) {
            ActivityEvent next = event(input ? ActivityEvent.State.NEEDS_INPUT : ActivityEvent.State.PROGRESS,
                value, Objects.requireNonNull(detail, "detail"));
            if (ended) return;
            fraction = value;
            running.put(id, next);
            boolean schedule = waiting == null;
            waiting = next;
            if (schedule) {
                long token = ++deliveryToken;
                bus.enqueue(() -> {
                    ActivityEvent latest;
                    synchronized (this) {
                        if (token != deliveryToken) return;
                        latest = waiting; waiting = null;
                    }
                    if (latest != null) bus.deliver(Activities.TOPIC.id(), latest);
                });
            }
        }

        @Override public void needsInput(String detail) { phase(true, detail); }
        @Override public void resume(String detail) { phase(false, detail); }

        private synchronized void phase(boolean needsInput, String detail) {
            Objects.requireNonNull(detail, "detail");
            if (ended) return;
            if (input == needsInput) { report(fraction, detail); return; }
            input = needsInput;
            waiting = null;
            deliveryToken++;
            ActivityEvent next = event(input ? ActivityEvent.State.NEEDS_INPUT : ActivityEvent.State.RESUMED, fraction, detail);
            running.put(id, next);
            bus.enqueue(() -> bus.deliver(Activities.TOPIC.id(), next));
        }

        @Override public void succeed(String detail) { end(ActivityEvent.State.SUCCEEDED, detail); }
        @Override public void fail(String detail) { end(ActivityEvent.State.FAILED, detail); }
        @Override public void cancelled() { end(ActivityEvent.State.CANCELLED, ""); }

        private synchronized void end(ActivityEvent.State state, String detail) {
            Objects.requireNonNull(detail, "detail");
            if (ended) return;
            ended = true;
            waiting = null;
            deliveryToken++;
            ActivityEvent next = event(state, fraction, detail);
            running.remove(id);
            handles.remove(id);
            bus.enqueue(() -> bus.deliver(Activities.TOPIC.id(), next));
        }

    }
}
