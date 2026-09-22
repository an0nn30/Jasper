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
import dev.jasper.sdk.terminal.TerminalEvents;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteStep;
import dev.jasper.sdk.palette.PaletteVerb;

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
    final FakeWorkspace workspace = new FakeWorkspace(this);
    private final Map<String, FakePluginContext> contexts = new LinkedHashMap<>();
        final Map<String, FakePalette.Registered> scopes = new LinkedHashMap<>();
        final List<String> paletteOpens = new ArrayList<>();
        final List<String> notices = new ArrayList<>();
        final List<Path> openedInEditor = new ArrayList<>();
        private final Map<String, Map<String, PaletteRow>> lastRows = new HashMap<>();
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
        workspace.sessions.pump();
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
        workspace.sessions.stopping(context);
        context.closeOwned();
        context.ui.closeAll();
        context.palette.closeAll();
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

    /**
     * Registered panels, registration order.
     *
     * @return lines of the form {@code id|title|LEFT|RIGHT|BOTTOM}, the last field being the default anchor
     */
    public List<String> panels() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values())
            for (FakeUi.Panel panel : context.ui.panels) lines.add(panel.spec.id() + "|" + panel.spec.title() + "|" + panel.spec.defaultAnchor());
        return lines;
    }

    /**
     * Invokes a panel's factory as a window showing it would. A factory failure is recorded.
     *
     * @param panelId the panel to build
     * @param windowId the window the panel host reports
     * @return the component, or null when there is no such panel or its factory failed
     */
    public javax.swing.JComponent openPanel(String panelId, UUID windowId) {
        for (FakePluginContext context : contexts.values()) {
            javax.swing.JComponent built = context.ui.openPanel(panelId, windowId);
            if (built != null) return built;
        }
        return null;
    }

    /**
     * Rail buttons in placement order; buttons whose action has closed are omitted.
     *
     * @return action ids
     */
    public List<String> rail() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values())
            for (String[] placed : context.ui.railActions) if (actionExists(placed[0])) lines.add(placed[0]);
        return lines;
    }

    /**
     * Every open plugin window and dialog, creation order.
     *
     * @return lines of the form {@code id|title|shown}, where a dialog's id is {@code dialog}
     */
    public List<String> windows() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values())
            for (FakeUi.FakeWindow window : context.ui.windows) lines.add(window.id + "|" + window.title + "|" + window.shown);
        return lines;
    }

    /**
     * Closes the first open window with this id as the user would, consulting its closing guards.
     *
     * @param windowId the window id, or {@code dialog}
     * @return false when a guard vetoed the close or there is no such window
     */
    public boolean requestClose(String windowId) {
        for (FakePluginContext context : contexts.values())
            for (FakeUi.FakeWindow window : List.copyOf(context.ui.windows)) if (window.id.equals(windowId)) return window.requestClose();
        return false;
    }

    /**
     * Adds a terminal window. Windows, tabs and panes announce themselves on the terminal topics like the
     * application's, delivered by {@link #flush()}.
     *
     * @return the new window's id
     */
    public UUID addTerminalWindow() { return workspace.addWindow().id; }

    /**
     * Adds and selects a tab.
     *
     * @param windowId an open window
     * @param title the tab's title
     * @return the new tab's id
     */
    public UUID addTerminalTab(UUID windowId, String title) {
        return workspace.addTab(workspace.window(windowId).orElseThrow(() -> new IllegalArgumentException("No such window: " + windowId)), title).id;
    }

    /**
     * Adds a pane; a tab's first pane is its focused pane.
     *
     * @param tabId an open tab
     * @param info what the pane reports
     * @return the new pane's id
     */
    public UUID addTerminalPane(UUID tabId, dev.jasper.sdk.terminal.PaneInfo info) {
        return workspace.addPane(workspace.tab(tabId).orElseThrow(() -> new IllegalArgumentException("No such tab: " + tabId)),
            Objects.requireNonNull(info, "info")).id;
    }

    /**
     * The user turned to a window.
     *
     * @param windowId the window
     */
    public void activateTerminalWindow(UUID windowId) { workspace.window(windowId).ifPresent(workspace::activate); }
    /**
     * The user focused a pane, which also selects its tab.
     *
     * @param paneId the pane
     */
    public void focusTerminalPane(UUID paneId) { workspace.pane(paneId).ifPresent(workspace::focus); }
    /**
     * Closes a pane; the last pane takes its tab with it, and the last tab its window.
     *
     * @param paneId the pane
     */
    public void closeTerminalPane(UUID paneId) { workspace.pane(paneId).ifPresent(workspace::close); }
    /**
     * Replaces what a pane reports, without an event.
     *
     * @param paneId the pane
     * @param info the new snapshot
     */
    public void setPaneInfo(UUID paneId, dev.jasper.sdk.terminal.PaneInfo info) { workspace.pane(paneId).ifPresent(pane -> pane.info = Objects.requireNonNull(info, "info")); }
    /**
     * Sets the text selected in a pane.
     *
     * @param paneId the pane
     * @param textOrNull the selection, or null for none
     */
    public void setSelection(UUID paneId, String textOrNull) { workspace.pane(paneId).ifPresent(pane -> pane.selection = textOrNull); }
    /**
     * Sets the program a pane reports in the foreground.
     *
     * @param paneId the pane
     * @param nameOrNull the program, or null for unknown
     */
    public void setForegroundJob(UUID paneId, String nameOrNull) { workspace.pane(paneId).ifPresent(pane -> pane.job = nameOrNull); }

    /**
     * What plugins sent to a pane, in order; still readable after the pane closed.
     *
     * @param paneId the pane
     * @return lines of the form {@code write:<the bytes as UTF-8 text>} or {@code paste:<text>}
     */
    public List<String> sent(UUID paneId) {
        synchronized (workspace) { return workspace.anyPane(paneId).map(pane -> List.copyOf(pane.sent)).orElse(List.of()); }
    }

    /**
     * What plugins asked to open or raise, in order.
     *
     * @return lines of the form {@code tab|<window id>|<directory or ->}, {@code split|<pane id>|<RIGHT or DOWN>|<directory or ->} or {@code front|<window id>}
     */
    public List<String> openRequests() { return List.copyOf(workspace.openRequests); }

    private static dev.jasper.sdk.terminal.PaneInfo with(dev.jasper.sdk.terminal.PaneInfo info, String title, Optional<Path> directory,
            dev.jasper.sdk.terminal.SessionState state, OptionalInt exitStatus) {
        return new dev.jasper.sdk.terminal.PaneInfo(title, directory, info.remoteDirectory(), info.columns(), info.rows(), info.shellIntegration(),
            info.kind(), info.providerPluginId(), state, exitStatus, info.shell());
    }

    /**
     * Publishes that a command began in an open pane.
     *
     * @param paneId the pane
     * @param command the command line
     */
    public void commandStarted(UUID paneId, String command) {
        workspace.pane(paneId).ifPresent(pane -> publishApp(TerminalEvents.COMMAND_STARTED, new TerminalEvents.CommandStarted(paneId, command)));
    }

    /**
     * Publishes that a command finished in an open pane, in the directories the pane reports.
     *
     * @param paneId the pane
     * @param command the command line
     * @param exitStatus its exit status, when known
     * @param duration how long it ran
     */
    public void commandFinished(UUID paneId, String command, OptionalInt exitStatus, Duration duration) {
        workspace.pane(paneId).ifPresent(pane -> publishApp(TerminalEvents.COMMAND_FINISHED, new TerminalEvents.CommandFinished(paneId, command,
            exitStatus, duration, pane.info.workingDirectory(), pane.info.remoteDirectory())));
    }

    /**
     * Changes an open pane's title and publishes it.
     *
     * @param paneId the pane
     * @param title the new title
     */
    public void titleChanged(UUID paneId, String title) {
        workspace.pane(paneId).ifPresent(pane -> {
            pane.info = with(pane.info, title, pane.info.workingDirectory(), pane.info.state(), pane.info.exitStatus());
            publishApp(TerminalEvents.TITLE_CHANGED, new TerminalEvents.TitleChanged(paneId, title));
        });
    }

    /**
     * Changes an open pane's local working directory and publishes it.
     *
     * @param paneId the pane
     * @param directory the new directory, or null for unknown
     */
    public void cwdChanged(UUID paneId, Path directory) {
        workspace.pane(paneId).ifPresent(pane -> {
            // A local report replaces a remote one: at most one of the two directories is present.
            pane.info = new dev.jasper.sdk.terminal.PaneInfo(pane.info.title(), Optional.ofNullable(directory), Optional.empty(), pane.info.columns(),
                pane.info.rows(), pane.info.shellIntegration(), pane.info.kind(), pane.info.providerPluginId(), pane.info.state(), pane.info.exitStatus(), pane.info.shell());
            publishApp(TerminalEvents.CWD_CHANGED, new TerminalEvents.CwdChanged(paneId, Optional.ofNullable(directory), Optional.empty()));
        });
    }

    /**
     * Ends an open pane's session and publishes it; the pane stays open.
     *
     * @param paneId the pane
     * @param exitStatus the exit status, when known
     */
    public void sessionExited(UUID paneId, OptionalInt exitStatus) {
        workspace.pane(paneId).ifPresent(pane -> {
            pane.info = with(pane.info, pane.info.title(), pane.info.workingDirectory(), dev.jasper.sdk.terminal.SessionState.EXITED, exitStatus);
            publishApp(TerminalEvents.SESSION_STATE_CHANGED, new TerminalEvents.SessionStateChanged(paneId,
                dev.jasper.sdk.terminal.SessionState.EXITED, exitStatus));
        });
    }

    /**
     * Publishes that an open pane rang the bell.
     *
     * @param paneId the pane
     */
    public void bell(UUID paneId) {
        workspace.pane(paneId).ifPresent(pane -> publishApp(TerminalEvents.BELL, new TerminalEvents.PaneEvent(pane.tab.id, paneId)));
    }

    /**
     * A provided session's state.
     *
     * @param paneId the pane
     * @return {@code CONNECTING|<status>}, {@code RUNNING|}, {@code EXITED|<how it ended>}, {@code LOCAL|} for a pane that is not a provided session, or {@code CLOSED|} for a pane that is gone
     */
    public String sessionState(UUID paneId) { return workspace.sessions.state(workspace.anyPane(paneId).orElse(null)); }

    /**
     * The user pressed Cancel. A pane that never showed a session closes; otherwise it returns to the disconnected state.
     *
     * @param paneId the pane
     */
    public void cancelSession(UUID paneId) { workspace.pane(paneId).ifPresent(workspace.sessions::cancel); }

    /**
     * The user pressed Reconnect or Retry on a session that ended: the connector runs again with a fresh attempt.
     *
     * @param paneId the pane
     */
    public void reconnectSession(UUID paneId) { workspace.pane(paneId).ifPresent(workspace.sessions::reconnect); }

    /**
     * Writes to an attached connection's input and flushes, on the calling thread.
     *
     * @param paneId the pane
     * @param text what the user typed, as UTF-8
     */
    public void typeIntoSession(UUID paneId, String text) { workspace.pane(paneId).ifPresent(pane -> workspace.sessions.type(pane, text)); }

    /**
     * Reads what an attached connection's output has available, without blocking.
     *
     * @param paneId the pane
     * @return the bytes as UTF-8 text, possibly empty
     */
    public String sessionOutput(UUID paneId) { return workspace.pane(paneId).map(workspace.sessions::output).orElse(""); }

    /** Notices attached sessions whose exit future completed: closes them, reports the exit and applies the exit policy. {@link #flush()} does this first. */
    public void pumpSessions() { workspace.sessions.pump(); }

    /**
     * Every open pane, in creation order.
     *
     * @return their ids
     */
    public List<UUID> terminalPanes() { return workspace.everyOpenPane().stream().map(pane -> pane.id).toList(); }

    /**
     * Reports a working directory that is not on this machine: the pane's local directory becomes empty.
     *
     * @param paneId the pane
     * @param host the reported host; empty when the program named none
     * @param path the reported path
     */
    public void remoteCwdChanged(UUID paneId, String host, String path) {
        workspace.pane(paneId).ifPresent(pane -> {
            var remote = Optional.of(new dev.jasper.sdk.terminal.RemoteDirectory(host, path));
            pane.info = new dev.jasper.sdk.terminal.PaneInfo(pane.info.title(), Optional.empty(), remote, pane.info.columns(), pane.info.rows(),
                pane.info.shellIntegration(), pane.info.kind(), pane.info.providerPluginId(), pane.info.state(), pane.info.exitStatus(), pane.info.shell());
            publishApp(TerminalEvents.CWD_CHANGED, new TerminalEvents.CwdChanged(paneId, Optional.empty(), remote));
        });
    }

        /** Registered scopes as {@code id|label|verbIds}, in registration order. */
        public List<String> scopes() {
            return scopes.values().stream().map(registered -> registered.spec().id() + "|" + registered.spec().label() + "|"
                + String.join(",", registered.spec().verbs().stream().map(PaletteVerb::id).toList())).toList();
        }

        private FakePalette.Registered scope(String scopeId) {
            FakePalette.Registered registered = scopes.get(scopeId);
            if (registered == null) throw new IllegalArgumentException("No such scope: " + scopeId);
            return registered;
        }

        private PaletteQuery query(FakePalette.Registered registered, UUID windowId, UUID paneIdOrNull) {
            return new PaletteQuery(registered.context().terminals.windowHandle(windowId),
                Optional.ofNullable(paneIdOrNull).map(registered.context().terminals::paneHandle), 5, true);
        }

        private PaletteRow row(String scopeId, String rowId) {
            PaletteRow row = lastRows.getOrDefault(scopeId, Map.of()).get(rowId);
            if (row == null) throw new IllegalArgumentException("Search the scope first; no such row: " + rowId);
            return row;
        }

        private static PaletteVerb verb(FakePalette.Registered registered, String verbId) {
            return registered.spec().verbs().stream().filter(verb -> verb.id().equals(verbId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such verb: " + verbId));
        }

        /** Searches a scope as the palette would, as {@code rowId|title|enabled} lines; later row lookups use this answer. */
        public List<String> searchScope(String scopeId, String query, UUID windowId, UUID paneIdOrNull) {
            var registered = scope(scopeId);
            PaletteResults results = registered.scope().search(query, query(registered, windowId, paneIdOrNull));
            var rows = new LinkedHashMap<String, PaletteRow>();
            for (PaletteRow row : results.rows()) rows.put(row.id(), row);
            lastRows.put(scopeId, rows);
            return results.rows().stream().map(row -> row.id() + "|" + row.title() + "|" + row.enabled()).toList();
        }

        /** {@code available} for a row of the last search. */
        public boolean availableInScope(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull) {
            var registered = scope(scopeId);
            return registered.scope().available(row(scopeId, rowId), verb(registered, verbId), query(registered, windowId, paneIdOrNull));
        }

        /** The title of the step a verb would show first, if any. */
        public Optional<String> stepInScope(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull) {
            var registered = scope(scopeId);
            return registered.scope().step(row(scopeId, rowId), verb(registered, verbId), query(registered, windowId, paneIdOrNull)).map(PaletteStep::title);
        }

        /**
         * Completes the step a verb shows with {@code values}: {@code done}, {@code error:<message>} or
         * {@code reopen:<scopeId>:<rowId or ->:<query or ->}.
         */
        public String completeStep(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull, Map<String, String> values) {
            var registered = scope(scopeId);
            PaletteStep step = registered.scope().step(row(scopeId, rowId), verb(registered, verbId), query(registered, windowId, paneIdOrNull))
                .orElseThrow(() -> new IllegalArgumentException("That verb shows no step"));
            var answer = new java.util.concurrent.atomic.AtomicReference<String>();
            step.complete().accept(Map.copyOf(values), result -> answer.set(describe(result)));
            return Objects.requireNonNull(answer.get(), "the step did not answer");
        }

        static String describe(PaletteStep.Result result) {
            if (result.error().isPresent()) return "error:" + result.error().get();
            if (result.reopenScopeId().isPresent())
                return "reopen:" + result.reopenScopeId().get() + ":" + result.reopenRowId().orElse("-") + ":" + result.reopenQuery().orElse("-");
            return "done";
        }

        /** Runs a verb on a row of the last search, without the availability recheck. */
        public void executeInScope(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull) {
            var registered = scope(scopeId);
            registered.scope().execute(row(scopeId, rowId), verb(registered, verbId), query(registered, windowId, paneIdOrNull));
        }

        /** Every {@code Palette.open} call as {@code windowId scopeId query rowId}, with {@code -} for an absent value. */
        public List<String> paletteOpens() { return List.copyOf(paletteOpens); }

        /** Every error notice as {@code pluginId: message}. */
        public List<String> notices() { return List.copyOf(notices); }

        /** Every file a plugin asked to open in the editor. */
        public List<Path> openedInEditor() { return List.copyOf(openedInEditor); }
}
