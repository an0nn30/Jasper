package dev.jasper.app.plugins;

import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ConnectionEvent;
import dev.jasper.sdk.activity.ConnectionHandle;
import dev.jasper.sdk.activity.ConnectionSpec;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns connection lifecycles: every state change delivered in order, a detail-only report joined to the delivery
 * still queued for that state, exactly one CLOSED, the source stamped here rather than by the plugin. Handles are
 * safe from any thread.
 */
final class ConnectionHub {
    private final EventBus bus;
    private final ConcurrentHashMap<UUID, Handle> handles = new ConcurrentHashMap<>();

    ConnectionHub(EventBus bus) {
        this.bus = bus;
        bus.checkType(Activities.CONNECTIONS);
    }

    ConnectionHandle open(String pluginId, ConnectionSpec spec) {
        var handle = new Handle(pluginId, Objects.requireNonNull(spec, "spec"));
        handles.put(handle.id, handle);
        handle.report(spec.state(), spec.detail());
        return handle;
    }

    /** Closes every connection the plugin left open. */
    void closeAll(String pluginId) {
        for (Handle handle : List.copyOf(handles.values()))
            if (handle.pluginId.equals(pluginId)) handle.close();
    }

    private final class Handle implements ConnectionHandle {
        private final UUID id = UUID.randomUUID();
        private final String pluginId;
        private final ConnectionSpec spec;
        private boolean closed;
        /** The last delivery queued; running empties it, so a report can only join one still waiting. */
        private AtomicReference<ConnectionEvent> queued;

        Handle(String pluginId, ConnectionSpec spec) { this.pluginId = pluginId; this.spec = spec; }

        @Override public UUID id() { return id; }
        @Override public void up(String detail) { report(ConnectionEvent.State.UP, detail); }
        @Override public void degraded(String detail) { report(ConnectionEvent.State.DEGRADED, detail); }
        @Override public void down(String detail) { report(ConnectionEvent.State.DOWN, detail); }

        @Override public void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
                handles.remove(id);
                queue(event(ConnectionEvent.State.CLOSED, ""));
            }
        }

        void report(ConnectionEvent.State state, String detail) {
            ConnectionEvent event = event(state, Objects.requireNonNull(detail, "detail"));
            synchronized (this) {
                if (closed) return;
                AtomicReference<ConnectionEvent> last = queued;
                ConnectionEvent waiting = last == null ? null : last.get();
                // The same state still waiting: only the detail changed, so the newer text replaces it.
                if (waiting != null && waiting.state() == state && last.compareAndSet(waiting, event)) return;
                queue(event);
            }
        }

        private void queue(ConnectionEvent event) {
            var slot = new AtomicReference<>(event);
            queued = slot;
            bus.enqueue(() -> {
                ConnectionEvent due = slot.getAndSet(null);
                if (due != null) bus.deliver(Activities.CONNECTIONS.id(), due);
            });
        }

        private ConnectionEvent event(ConnectionEvent.State state, String detail) {
            return new ConnectionEvent(id, pluginId, spec.title(), state, detail, spec.activateActionId());
        }
    }
}
