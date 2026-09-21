package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.events.Topic;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Publication always enqueues on the UI executor, so delivery is one global first-in, first-out order
 * and never re-entrant. Ownership: {@code jasper.*} is the application's, {@code <plugin id>.*} that
 * plugin's. Subscription and publication are safe from any thread; thread rules for plugins are
 * enforced by their context.
 */
final class EventBus {
    static final String APP = "jasper";

    private record Entry(String pluginId, String topicId, Consumer<Object> handler, AtomicBoolean closed) { }

    private final Consumer<Runnable> ui;
    private final Containment containment;
    private final ConcurrentHashMap<String, Class<?>> types = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Entry>> subscribers = new ConcurrentHashMap<>();
    private final AtomicInteger pending = new AtomicInteger();

    EventBus(Consumer<Runnable> ui, Containment containment) { this.ui = ui; this.containment = containment; }

    <T> Subscription subscribe(String pluginId, Topic<T> topic, Consumer<? super T> handler) {
        Objects.requireNonNull(handler, "handler");
        checkType(topic);
        @SuppressWarnings("unchecked")
        var entry = new Entry(pluginId, topic.id(), (Consumer<Object>) handler, new AtomicBoolean());
        var list = subscribers.computeIfAbsent(topic.id(), id -> new CopyOnWriteArrayList<>());
        list.add(entry);
        return () -> { entry.closed().set(true); list.remove(entry); };
    }

    <T> void publish(String owner, Topic<T> topic, T payload) {
        Objects.requireNonNull(payload, "payload");
        boolean owns = owner.equals(APP) ? topic.id().startsWith("jasper.") : topic.id().startsWith(owner + ".");
        if (!owns) throw new IllegalArgumentException(owner + " may not publish to " + topic.id());
        checkType(topic);
        if (!topic.payloadType().isInstance(payload))
            throw new IllegalArgumentException("Payload is not a " + topic.payloadType().getName());
        enqueue(() -> deliver(topic.id(), payload));
    }

    void checkType(Topic<?> topic) {
        Class<?> known = types.putIfAbsent(topic.id(), topic.payloadType());
        if (known != null && known != topic.payloadType())
            throw new IllegalArgumentException("Topic " + topic.id() + " already carries " + known.getName());
    }

    /** Queues work behind every delivery already queued; counted until it has run. */
    void enqueue(Runnable delivery) {
        pending.incrementAndGet();
        ui.accept(() -> {
            try { delivery.run(); }
            finally { pending.decrementAndGet(); }
        });
    }

    /** UI thread: dispatches to the subscribers present now; each handler is contained. */
    void deliver(String topicId, Object payload) {
        var list = subscribers.get(topicId);
        if (list == null) return;
        for (Entry entry : list) {
            if (entry.closed().get()) continue;
            if (entry.pluginId().equals(APP)) entry.handler().accept(payload);
            else containment.run(entry.pluginId(), "handler for " + topicId, () -> entry.handler().accept(payload));
        }
    }

    void removeAll(String pluginId) {
        for (var list : subscribers.values())
            list.removeIf(entry -> { boolean mine = entry.pluginId().equals(pluginId); if (mine) entry.closed().set(true); return mine; });
    }

    int pending() { return pending.get(); }
}
