package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.ui.ToolbarItem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A single-threaded plugin runtime for tests. It follows the same rules as the application: queued
 * ordered delivery, topic ownership, coalesced activity progress, services committed on a successful
 * start, rollback and a closed context on failure. It enforces no thread rules.
 */
public final class FakePluginHost implements AutoCloseable {
    private static final String APP = "jasper";

    private record Entry(String pluginId, Consumer<Object> handler, AtomicBoolean closed) { }
    private record Provider(String pluginId, Function<PluginInfo, ?> factory) { }

    private final Queue<Runnable> events = new ConcurrentLinkedQueue<>();
    final Queue<Runnable> background = new ConcurrentLinkedQueue<>();
    private final Map<String, Class<?>> topicTypes = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Entry>> subscribers = new ConcurrentHashMap<>();
    private final Map<UUID, ActivityEvent> running = new ConcurrentHashMap<>();
    private final List<ActivityEvent> activityLog = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, Provider> committed = new LinkedHashMap<>();
    private final Map<String, Map<Class<?>, Provider>> staged = new HashMap<>();
    private final Map<String, FakePluginContext> contexts = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> presets = new HashMap<>();
    private final List<String> failures = new CopyOnWriteArrayList<>();
    final List<String> reports = new CopyOnWriteArrayList<>();
    private Path dataRoot;
    private volatile Variant variant = Variant.DARK;

    /**
     * Starts a plugin the way the application would. A plugin whose hard requirement is not active is
     * not started; a throwing {@code start} is rolled back and recorded in {@link #failures()}.
     *
     * @param info the plugin's identity
     * @param requires ids of hard dependencies
     * @param optional ids of optional dependencies
     * @param plugin the plugin under test
     * @return the context the plugin received, closed when the plugin was skipped or failed
     */
    public FakePluginContext start(PluginInfo info, Set<String> requires, Set<String> optional, Plugin plugin) {
        if (contexts.containsKey(info.id())) throw new IllegalArgumentException("Already started: " + info.id());
        var all = new java.util.HashSet<>(requires);
        all.addAll(optional);
        var context = new FakePluginContext(this, info, Set.copyOf(all), Objects.requireNonNull(plugin));
        context.table = presets.getOrDefault(info.id(), Map.of());
        contexts.put(info.id(), context);
        for (String required : requires) {
            if (!active(required)) {
                context.state = FakePluginContext.State.CLOSED;
                failures.add(info.id() + " skipped: requires " + required);
                return context;
            }
        }
        for (var provider : committed.entrySet()) {
            if (!context.requires.contains(provider.getValue().pluginId())) continue;
            try { context.services.put(provider.getKey(), provider.getValue().factory().apply(info)); }
            catch (RuntimeException | LinkageError failure) { failures.add(provider.getValue().pluginId() + " factory: " + failure); }
        }
        try {
            plugin.start(context);
            committed.putAll(staged.getOrDefault(info.id(), Map.of()));
            context.state = FakePluginContext.State.ACTIVE;
        } catch (Exception | LinkageError failure) {
            failures.add(info.id() + " start: " + failure);
            teardown(context, "Plugin failed to start");
        } finally {
            staged.remove(info.id());
        }
        return context;
    }

    /**
     * Whether the plugin started and has not stopped.
     *
     * @param pluginId the plugin
     * @return true while active
     */
    public boolean active(String pluginId) {
        FakePluginContext context = contexts.get(pluginId);
        return context != null && context.state == FakePluginContext.State.ACTIVE;
    }

    /** Delivers every queued event, including those queued by the deliveries themselves. */
    public void flush() {
        Runnable delivery;
        while ((delivery = events.poll()) != null) delivery.run();
    }

    /**
     * Runs the background tasks queued so far on the calling thread.
     *
     * @return how many tasks ran
     */
    public int runBackground() {
        int count = 0;
        Runnable task;
        while ((task = background.poll()) != null) {
            try { task.run(); } catch (RuntimeException failure) { failures.add("background: " + failure); }
            count++;
        }
        return count;
    }

    /**
     * Publishes as the application, for example {@code AppEvents.THEME_CHANGED}.
     *
     * @param topic a topic in the {@code jasper.} namespace
     * @param payload the payload
     * @param <T> payload type
     */
    public <T> void publishApp(Topic<T> topic, T payload) { publish(APP, topic, payload); }

    /**
     * Replaces a plugin's configuration table. Before the plugin starts this is its initial table;
     * afterwards its change listeners run.
     *
     * @param pluginId the plugin
     * @param table values of type String, Long, Boolean, List of String, or nested Map
     */
    public void setConfig(String pluginId, Map<String, Object> table) {
        presets.put(pluginId, Map.copyOf(table));
        FakePluginContext context = contexts.get(pluginId);
        if (context == null) return;
        context.table = Map.copyOf(table);
        for (Runnable listener : context.configListeners) listener.run();
    }

    /**
     * Activity events delivered so far.
     *
     * @return the events in delivery order
     */
    public List<ActivityEvent> activityLog() { return List.copyOf(activityLog); }

    /**
     * Contained failures: throwing starts, stops, handlers, factories and background tasks.
     *
     * @return one line per failure
     */
    public List<String> failures() { return List.copyOf(failures); }

    /**
     * Configuration problems plugins reported.
     *
     * @return lines of the form {@code pluginId: key: message}
     */
    public List<String> reports() { return List.copyOf(reports); }

    /** Stops every active plugin in reverse start order. */
    public void stopAll() {
        var order = new ArrayList<>(contexts.values());
        for (int i = order.size() - 1; i >= 0; i--) {
            FakePluginContext context = order.get(i);
            if (context.state != FakePluginContext.State.ACTIVE) continue;
            context.state = FakePluginContext.State.STOPPING;
            try { context.plugin.stop(); }
            catch (RuntimeException | LinkageError failure) { failures.add(context.plugin().id() + " stop: " + failure); }
            teardown(context, "Plugin stopped");
        }
    }

    @Override public void close() {
        stopAll();
        flush();
        if (dataRoot == null) return;
        try (var files = Files.walk(dataRoot)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // A leftover temporary directory is harmless.
        }
    }

    Variant variant() { return variant; }

    void recordFailure(String line) { failures.add(line); }

    boolean actionExists(String actionId) {
        for (FakePluginContext context : contexts.values()) if (context.ui.actions.containsKey(actionId)) return true;
        return false;
    }

    /**
     * Changes the look and announces it.
     *
     * @param next the new variant
     */
    public void setVariant(Variant next) {
        variant = Objects.requireNonNull(next, "next");
        publishApp(AppEvents.THEME_CHANGED, new AppEvents.ThemeChanged(next));
    }

    /**
     * Registered actions in registration order.
     *
     * @return lines of the form {@code id|title|enabled}
     */
    public List<String> actions() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values())
            for (FakeUi.Action action : context.ui.actions.values())
                lines.add(action.spec.id() + "|" + action.title + "|" + action.enabled);
        return lines;
    }

    /**
     * Invokes an action as a window would.
     *
     * @param actionId the action
     * @param windowId the invoking window
     * @param paneIdOrNull the pane the action concerns, or null
     * @return whether an enabled action ran
     */
    public boolean invoke(String actionId, UUID windowId, UUID paneIdOrNull) {
        for (FakePluginContext context : contexts.values())
            if (context.ui.actions.containsKey(actionId)) return context.ui.invoke(actionId, windowId, paneIdOrNull);
        return false;
    }

    /**
     * The toolbar's plugin section.
     *
     * @return {@code button:id} or {@code menu:title:id,id}; closed actions are omitted
     */
    public List<String> toolbar() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values()) {
            for (ToolbarItem item : context.ui.toolbar) {
                switch (item) {
                    case ToolbarItem.Button button -> { if (actionExists(button.actionId())) lines.add("button:" + button.actionId()); }
                    case ToolbarItem.Dropdown dropdown -> {
                        List<String> live = dropdown.actionIds().stream().filter(this::actionExists).toList();
                        if (!live.isEmpty()) lines.add("menu:" + dropdown.title() + ":" + String.join(",", live));
                    }
                }
            }
        }
        return lines;
    }

    /**
     * One menu target's contributed entries.
     *
     * @param target {@code FILE}, {@code EDIT}, {@code VIEW}, {@code PANE}, {@code TAB}, {@code context} or {@code top:<menuId>}
     * @return {@code item:id}, {@code ---} and {@code submenu:title} lines, children indented, sections separated by {@code ===}
     */
    public List<String> menu(String target) {
        List<String> lines = new ArrayList<>();
        boolean first = true;
        for (FakePluginContext context : contexts.values()) {
            for (FakeUi.Menu section : context.ui.sections) {
                if (!section.target.equals(target)) continue;
                if (!first) lines.add("===");
                first = false;
                section.render(lines, "");
            }
        }
        return lines;
    }

    /**
     * Visible status items, ascending priority.
     *
     * @return lines of the form {@code id|side|text|tooltip|actionId}
     */
    public List<String> status() {
        List<FakeUi.Status> items = new ArrayList<>();
        for (FakePluginContext context : contexts.values()) items.addAll(context.ui.status);
        return items.stream().filter(item -> item.visible)
            .sorted(java.util.Comparator.comparingInt((FakeUi.Status item) -> item.spec.priority()))
            .map(item -> item.spec.id() + "|" + item.spec.side() + "|" + item.text + "|"
                + (item.tooltip == null ? "" : item.tooltip) + "|" + (item.actionId == null ? "" : item.actionId))
            .toList();
    }

    Path dataRoot() {
        if (dataRoot == null) {
            try { dataRoot = Files.createTempDirectory("jasper-fake-plugins"); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        return dataRoot;
    }

    private void teardown(FakePluginContext context, String reason) {
        context.state = FakePluginContext.State.CLOSED;
        context.closeOwned();
        context.ui.closeAll();
        String id = context.plugin().id();
        for (var list : subscribers.values()) list.removeIf(entry -> entry.pluginId().equals(id));
        for (ActivityEvent open : List.copyOf(running.values())) {
            if (!open.sourcePluginId().equals(id)) continue;
            running.remove(open.id());
            enqueue(new ActivityEvent(open.id(), id, open.title(), ActivityEvent.State.FAILED,
                open.fraction(), reason, open.activateActionId()));
        }
    }

    <T> Subscription subscribe(String pluginId, Topic<T> topic, Consumer<? super T> handler) {
        Objects.requireNonNull(handler, "handler");
        checkType(topic);
        @SuppressWarnings("unchecked")
        var entry = new Entry(pluginId, (Consumer<Object>) handler, new AtomicBoolean());
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
        events.add(() -> deliver(topic.id(), payload));
    }

    private void checkType(Topic<?> topic) {
        Class<?> known = topicTypes.putIfAbsent(topic.id(), topic.payloadType());
        if (known != null && known != topic.payloadType())
            throw new IllegalArgumentException("Topic " + topic.id() + " already carries " + known.getName());
    }

    private void enqueue(ActivityEvent event) {
        checkType(Activities.TOPIC);
        events.add(() -> deliver(Activities.TOPIC.id(), event));
    }

    private void deliver(String topicId, Object payload) {
        if (payload instanceof ActivityEvent event && topicId.equals(Activities.TOPIC.id())) activityLog.add(event);
        for (Entry entry : subscribers.getOrDefault(topicId, new CopyOnWriteArrayList<>())) {
            if (entry.closed().get()) continue;
            try { entry.handler().accept(payload); }
            catch (RuntimeException | LinkageError failure) { failures.add(entry.pluginId() + " handler: " + failure); }
        }
    }

    void stage(String pluginId, Class<?> api, Function<PluginInfo, ?> factory) {
        Objects.requireNonNull(factory, "factory");
        if (!api.isInterface()) throw new IllegalArgumentException("A service API must be an interface: " + api.getName());
        var mine = staged.computeIfAbsent(pluginId, id -> new LinkedHashMap<>());
        if (committed.containsKey(api) || mine.containsKey(api))
            throw new IllegalArgumentException("Service already published: " + api.getName());
        mine.put(api, new Provider(pluginId, factory));
    }

    List<ActivityEvent> currentActivities() { return List.copyOf(running.values()); }

    ActivityHandle begin(String pluginId, ActivitySpec spec) {
        var activity = new FakeActivity(pluginId, spec);
        var started = activity.event(ActivityEvent.State.STARTED, OptionalDouble.empty(), spec.detail());
        running.put(activity.id, started);
        enqueue(started);
        return activity;
    }

    private final class FakeActivity implements ActivityHandle {
        private final UUID id = UUID.randomUUID();
        private final String pluginId;
        private final ActivitySpec spec;
        private boolean ended;
        private OptionalDouble fraction = OptionalDouble.empty();
        private ActivityEvent waiting;

        FakeActivity(String pluginId, ActivitySpec spec) { this.pluginId = pluginId; this.spec = spec; }

        ActivityEvent event(ActivityEvent.State state, OptionalDouble value, String detail) {
            return new ActivityEvent(id, pluginId, spec.title(), state, value, detail, spec.activateActionId());
        }

        @Override public UUID id() { return id; }

        @Override public void progress(double value, String detail) {
            report(OptionalDouble.of(value), detail);
        }

        @Override public void detail(String detail) {
            OptionalDouble last;
            synchronized (this) { last = fraction; }
            report(last, detail);
        }

        private void report(OptionalDouble value, String detail) {
            ActivityEvent event = event(ActivityEvent.State.PROGRESS, value, Objects.requireNonNull(detail, "detail"));
            boolean schedule;
            synchronized (this) {
                if (ended || !running.containsKey(id)) return;
                fraction = value;
                running.put(id, event);
                schedule = waiting == null;
                waiting = event;
            }
            if (schedule) events.add(() -> {
                ActivityEvent latest;
                synchronized (this) { latest = waiting; waiting = null; }
                if (latest != null) deliver(Activities.TOPIC.id(), latest);
            });
        }

        @Override public void succeed(String detail) { end(ActivityEvent.State.SUCCEEDED, detail); }
        @Override public void fail(String detail) { end(ActivityEvent.State.FAILED, detail); }
        @Override public void cancelled() { end(ActivityEvent.State.CANCELLED, ""); }

        private void end(ActivityEvent.State state, String detail) {
            OptionalDouble last;
            synchronized (this) {
                if (ended || running.remove(id) == null) { ended = true; return; }
                ended = true;
                waiting = null;
                last = fraction;
            }
            enqueue(event(state, last, Objects.requireNonNull(detail, "detail")));
        }
    }
}
