package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.activity.ConnectionEvent;
import dev.jasper.sdk.activity.ConnectionHandle;
import dev.jasper.sdk.activity.ConnectionSpec;
import java.util.concurrent.atomic.AtomicReference;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.ui.ActionSpec;
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
 * start, rollback and a closed context on failure. Appearance palettes and workspace
 * subscriptions accept the creating test thread or the real Swing EDT, rejecting background workers.
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
    private final Map<UUID, FakeConnection> connections = new ConcurrentHashMap<>();
    private final List<ConnectionEvent> connectionLog = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, Provider> committed = new LinkedHashMap<>();
    private final Map<String, Map<Class<?>, Provider>> staged = new HashMap<>();
    final FakeWorkspace workspace = new FakeWorkspace(this);
    private final Map<String, FakePluginContext> contexts = new LinkedHashMap<>();
    final Map<String, FakeSettingsPanelHost> panelHosts = new java.util.concurrent.ConcurrentHashMap<>();
        final Map<String, FakePalette.Registered> scopes = new LinkedHashMap<>();
    final Map<String, FakeFileSources.Registered> fileSources = new LinkedHashMap<>();
    /** A {@code [[subcommands]]} declaration. */
    record Declared(String pluginId, String name, String summary) { }
    final Map<String, Declared> subcommands = new LinkedHashMap<>();
    final Map<String, FakeCommandLine.Registered> subcommandHandlers = new LinkedHashMap<>();
    /** A subcommand call a handler took and has not completed. */
    private record RunningCall(FakePluginContext context, String name, FakeCommandCall call) { }
    private final List<RunningCall> runningCalls = new CopyOnWriteArrayList<>();
    private final java.util.ArrayDeque<List<dev.jasper.sdk.files.FileLocation>> fileChoiceQueue = new java.util.ArrayDeque<>();
    private final List<dev.jasper.sdk.files.FileChoice> fileChoices = new ArrayList<>();
        final List<String> paletteOpens = new ArrayList<>();
        final List<Path> openedInEditor = new ArrayList<>();
    final List<java.net.URI> browsed = new CopyOnWriteArrayList<>();
    final FakeClipboard clipboard = new FakeClipboard();
    final FakeNotifications notifications = new FakeNotifications(this);
        private final Map<String, Map<String, PaletteRow>> lastRows = new HashMap<>();
    private final List<String> failures = new CopyOnWriteArrayList<>();
    final Map<String, Map<String, Object>> settingFiles = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<String, List<String>> settingsReports = new java.util.concurrent.ConcurrentHashMap<>();
    /** The look's defaults for plugins' settings, by plugin id and then dotted key, as a kit's tables give them. */
    private final Map<String, Map<String, Object>> kitDefaults = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, List<String>> lookDefaultProblems = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<UUID, FakeUi.FakeWindow> overlays = new HashMap<>();
    private Path dataRoot;
    private final boolean ownsRoot;
    private final Thread appearanceThread = Thread.currentThread();
    record Workspace(Variant variant, dev.jasper.sdk.ui.WorkspacePalette palette,
                     dev.jasper.sdk.ui.EditorPalette editor, dev.jasper.sdk.ui.SyntaxPalette syntax) {}
    private volatile Workspace appearance = initialWorkspace();
    private static Workspace initialWorkspace(){
        var bg=new java.awt.Color(0x202124);var fg=new java.awt.Color(0xdedede);var accent=new java.awt.Color(0x7faaff);
        var palette=new dev.jasper.sdk.ui.WorkspacePalette(bg,bg,bg,fg,fg,fg,accent,fg,bg,accent,fg,fg,fg);
        var editor=new dev.jasper.sdk.ui.EditorPalette(bg,fg,fg,accent,bg,bg,fg);
        var styles=new java.util.EnumMap<dev.jasper.sdk.ui.SyntaxRole,dev.jasper.sdk.ui.SyntaxStyle>(dev.jasper.sdk.ui.SyntaxRole.class);
        for(var role:dev.jasper.sdk.ui.SyntaxRole.values())styles.put(role,new dev.jasper.sdk.ui.SyntaxStyle(fg,false,role==dev.jasper.sdk.ui.SyntaxRole.COMMENT || role==dev.jasper.sdk.ui.SyntaxRole.DOC_COMMENT));
        return new Workspace(Variant.DARK,palette,editor,new dev.jasper.sdk.ui.SyntaxPalette(styles));
    }
    private Workspace requestedAppearance = appearance;
    private boolean workspaceMatches = true;
    private volatile double workspaceOpacity = 1.0;
    private double requestedOpacity = 1.0;
    private final List<Entry> appearanceHandlers = new CopyOnWriteArrayList<>();
    private final List<Entry> workspaceHandlers = new CopyOnWriteArrayList<>();

    private Map<dev.jasper.sdk.ui.SemanticColor,java.awt.Color> semantic = initialSemantic();
    private static Map<dev.jasper.sdk.ui.SemanticColor,java.awt.Color> initialSemantic() {
        var values=new java.util.EnumMap<dev.jasper.sdk.ui.SemanticColor,java.awt.Color>(dev.jasper.sdk.ui.SemanticColor.class);
        for(var role:dev.jasper.sdk.ui.SemanticColor.values()) values.put(role,role.name().startsWith("SURFACE_")?new java.awt.Color(0x202124):new java.awt.Color(0xdedede));
        return Map.copyOf(values);
    }
    java.awt.Color semanticColor(dev.jasper.sdk.ui.SemanticColor role) {return semantic.get(Objects.requireNonNull(role));}
    /** Queue a complete canonical palette and announce even same-variant changes. */
    public void setSemanticColors(Map<dev.jasper.sdk.ui.SemanticColor,java.awt.Color> values) {
        requireAppearanceThread();var copy=Map.copyOf(values);if(copy.size()!=dev.jasper.sdk.ui.SemanticColor.values().length)throw new IllegalArgumentException("Incomplete semantic palette");
        events.add(() -> {if(!semantic.equals(copy)){semantic=copy;announceAppearance();}});
    }

    void requireAppearanceThread() {
        if (Thread.currentThread() != appearanceThread && !javax.swing.SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Appearance requires the UI thread");
    }
    Workspace workspaceAppearance() { return appearance; }
    double workspaceOpacity() { return workspaceOpacity; }
    Subscription subscribeAppearance(String pluginId, Consumer<Variant> handler) {
        requireAppearanceThread();Objects.requireNonNull(handler);var entry=new Entry(pluginId,value -> handler.accept((Variant)value),new AtomicBoolean());appearanceHandlers.add(entry);
        return () -> {entry.closed().set(true);appearanceHandlers.remove(entry);};
    }
    private void announceAppearance() {for(var entry:appearanceHandlers)if(!entry.closed().get())try{entry.handler().accept(variant);}catch(RuntimeException | LinkageError failure){recordFailure(entry.pluginId()+" appearance callback failed");}}
    Subscription subscribeWorkspace(String pluginId, Consumer<Variant> handler) {
        requireAppearanceThread(); Objects.requireNonNull(handler, "handler");
        var entry = new Entry(pluginId, value -> handler.accept((Variant) value), new AtomicBoolean());
        workspaceHandlers.add(entry);
        return () -> { entry.closed().set(true); workspaceHandlers.remove(entry); };
    }

    /** Queues an independent workspace appearance, including same-variant color changes.
     * Workspace getters expose the last flushed snapshot until {@link #flush()} publishes this change.
     * All workspace getters agree with each notification during its delivery.
     * @param variant workspace variant
     * @param palette workspace control colors
     * @param editor editor colors
     * @param syntax syntax styles
     * @since 0.12.0
     */
    public void setWorkspace(Variant variant, dev.jasper.sdk.ui.WorkspacePalette palette,
            dev.jasper.sdk.ui.EditorPalette editor, dev.jasper.sdk.ui.SyntaxPalette syntax) {
        requireAppearanceThread();
        var next = new Workspace(Objects.requireNonNull(variant), Objects.requireNonNull(palette),
            Objects.requireNonNull(editor), Objects.requireNonNull(syntax));
        workspaceMatches = false;
        publishWorkspace(next, requestedOpacity);
    }

    /** Sets the opacity every component reports through {@code Appearance.workspaceOpacity} and announces it as a
     * workspace change on the next {@link #flush()} (no announcement when unchanged). Like the other workspace values,
     * components report the last flushed opacity until then.
     * @param opacity from 0.1 to 1
     * @since 0.14.1
     */
    public void setWorkspaceOpacity(double opacity) {
        requireAppearanceThread();
        if (!(opacity >= 0.1 && opacity <= 1)) throw new IllegalArgumentException("opacity must be from 0.1 to 1");
        publishWorkspace(requestedAppearance, opacity);
    }

    private void publishWorkspace(Workspace next, double opacity) {
        if (requestedAppearance.equals(next) && requestedOpacity == opacity) return;
        boolean themeChanged = !workspaceMatches && !requestedAppearance.equals(next);
        requestedAppearance = next;
        requestedOpacity = opacity;
        events.add(() -> {
            appearance = next;
            workspaceOpacity = opacity;
            if(themeChanged)announceAppearance();
            // Subscription lifetime is judged when the state is published, including plugins started since enqueue.
            for (var entry : workspaceHandlers) {
                if (!entry.closed().get()) {
                    try { entry.handler().accept(next.variant()); }
                    catch (RuntimeException | LinkageError failure) { recordFailure(entry.pluginId() + " workspace appearance: " + failure); }
                }
            }
        });
    }

    private volatile Variant variant = Variant.DARK;
    private volatile dev.jasper.sdk.ui.Presentation presentation = FakePresentations.STANDARD;
    /** Set by the first {@link #start} and never cleared, even when every plugin has stopped since. */
    private boolean started;

    /** A host whose plugins' data directories live in a temporary directory this host deletes on close. */
    public FakePluginHost() { this.ownsRoot = true; }

    /**
     * A host whose plugins' data directories live under {@code dataRoot}, which the caller owns and this
     * host never deletes.
     *
     * @param dataRoot the parent of each plugin's data directory
     */
    public FakePluginHost(Path dataRoot) { this.dataRoot = java.util.Objects.requireNonNull(dataRoot, "dataRoot"); this.ownsRoot = false; }

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
        started = true;
        var all = new java.util.HashSet<>(requires);
        all.addAll(optional);
        var context = new FakePluginContext(this, info, Set.copyOf(all), Objects.requireNonNull(plugin));
        contexts.put(info.id(), context);
        for (String required : requires) {
            if (!active(required)) {
                context.state = FakePluginContext.State.CLOSED;
                failures.add(info.id() + " skipped: requires " + required);
                return context;
            }
        }
        notifications.declareDefaults(info.id(), info.name());
        for (var provider : committed.entrySet()) {
            if (!context.requires.contains(provider.getValue().pluginId())) continue;
            try { context.services.put(provider.getKey(), provider.getValue().factory().apply(info)); }
            catch (RuntimeException | LinkageError failure) { failures.add(provider.getValue().pluginId() + " factory: " + failure); }
        }
        try {
            plugin.start(context);
            committed.putAll(staged.getOrDefault(info.id(), Map.of()));
            context.state = FakePluginContext.State.ACTIVE;
            context.settings.complete();
        } catch (Exception | LinkageError failure) {
            failures.add(info.id() + " start: " + failure);
            teardown(context, "Plugin failed to start");
        } finally {
            staged.remove(info.id());
        }
        return context;
    }

    /**
     * Sets the presentation every plugin's appearance reports, as the application chooses its look before plugins start.
     *
     * @param presentation the look this host runs with
     * @throws IllegalStateException once any plugin has started, even if it has stopped since
     * @since 0.18.2
     */
    public void setPresentation(dev.jasper.sdk.ui.Presentation presentation) {
        Objects.requireNonNull(presentation, "presentation");
        if (started) throw new IllegalStateException("Plugins have already started");
        this.presentation = presentation;
    }

    /**
     * Gives a plugin's setting a look default, as a kit's {@code [defaults."<plugin id>"]} table does in the
     * application: when the plugin declares the key, a value its kind accepts replaces the declared default, and a
     * saved value still wins. A value the kind rejects, and a key the plugin never declares, leave the plugin's own
     * default and are reported in {@link #lookDefaultProblems}.
     *
     * @param pluginId the plugin
     * @param key the dotted setting key
     * @param rawValue a String, Long, Double, Boolean or List of String, as TOML reads them
     * @throws IllegalStateException once any plugin has started, even if it has stopped since
     * @since 0.18.2
     */
    public void setKitDefault(String pluginId, String key, Object rawValue) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(rawValue, "rawValue");
        if (!(rawValue instanceof String || rawValue instanceof Long || rawValue instanceof Double || rawValue instanceof Boolean
                || rawValue instanceof List<?> items && items.stream().allMatch(String.class::isInstance)))
            throw new IllegalArgumentException("Not a raw TOML value: " + rawValue);
        if (started) throw new IllegalStateException("Plugins have already started");
        kitDefaults.computeIfAbsent(pluginId, id -> new java.util.LinkedHashMap<>())
            .put(key, rawValue instanceof List<?> items ? List.copyOf(items) : rawValue);
    }

    /**
     * The look defaults a plugin could not use, in the order reported. A rejected value reads
     * {@code <plugin id>: the look's default for <key> is not used: <reason>}, and a key the plugin never declares
     * {@code <plugin id>: the look gives a default for <key>, which the plugin does not declare}; the reason's wording
     * may differ from the application's.
     *
     * @param pluginId the plugin
     * @return the problems, empty when there are none
     * @since 0.18.2
     */
    public List<String> lookDefaultProblems(String pluginId) { return List.copyOf(lookDefaultProblems.getOrDefault(pluginId, List.of())); }

    /** One plugin's look defaults by dotted key; empty when the look gives none. */
    Map<String, Object> kitDefaults(String pluginId) { return kitDefaults.getOrDefault(pluginId, Map.of()); }

    void lookDefaultProblem(String pluginId, String message) {
        lookDefaultProblems.computeIfAbsent(pluginId, id -> new CopyOnWriteArrayList<>()).add(pluginId + ": " + message);
    }

    /**
     * The presentation every plugin's appearance reports; safe from any thread.
     *
     * @return the presentation set, or {@link FakePresentations#STANDARD}
     * @since 0.18.2
     */
    public dev.jasper.sdk.ui.Presentation presentation() { return presentation; }

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

    Map<String, Object> settingFile(String pluginId) {
        return settingFiles.computeIfAbsent(pluginId, id -> new java.util.LinkedHashMap<>());
    }

    /**
     * Sets a raw value in a plugin's settings file, as a user editing the file would. Before the plugin starts this
     * is part of the file it starts with; afterwards its declared settings reload and their listeners run.
     *
     * @param pluginId the plugin
     * @param key the dotted key
     * @param rawValue a String, Long, Double, Boolean or List of String, as TOML reads them
     */
    public void setSetting(String pluginId, String key, Object rawValue) {
        settingFile(pluginId).put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(rawValue, "rawValue"));
        FakePluginContext context = contexts.get(pluginId);
        if (context != null && context.state == FakePluginContext.State.ACTIVE) context.settings.reload();
    }

    /**
     * The raw values in a plugin's settings file, including what the plugin saved.
     *
     * @param pluginId the plugin
     * @return the values by dotted key
     */
    public Map<String, Object> savedSettings(String pluginId) { return Map.copyOf(settingFile(pluginId)); }

    /**
     * The problems the last read of a plugin's settings found.
     *
     * @param pluginId the plugin
     * @return lines of the form {@code key: message}
     */
    public List<String> settingsReports(String pluginId) { return settingsReports.getOrDefault(pluginId, List.of()); }

    /**
     * The keys a plugin declared.
     *
     * @param pluginId the plugin
     * @return the keys in declaration order
     */
    public List<String> declared(String pluginId) {
        FakePluginContext context = contexts.get(pluginId);
        return context == null ? List.of() : context.settings.keys();
    }

    /**
     * Builds a plugin's own settings page as Settings would, over a host whose rows are labelled placeholders.
     *
     * @param pluginId the plugin
     * @return the page's component; empty when the plugin gave no page or is not running
     */
    public java.util.Optional<javax.swing.JComponent> settingsPanel(String pluginId) {
        FakePluginContext context = contexts.get(pluginId);
        if (context == null) return java.util.Optional.empty();
        var existing=panelHosts.get(pluginId);
        if(existing!=null)return java.util.Optional.ofNullable(existing.component);
        return context.settings.panel().map(panel -> {
            var rows = new FakeSettingsPanelHost(context.settings, this::takeFileSelection);
            panelHosts.put(pluginId, rows);
            try {
            var owned=java.util.Objects.requireNonNull(panel.create(rows),"Settings view");
            var view=context.kit().requireOwned(owned);view.mount(rows);rows.view=view;view.hostSpec=rows::descriptor;
            view.hostEvents=rows::event;
            rows.component=view.component;return rows.component;
            }catch(Throwable failure){recordFailure("Settings callback failed");rows.component=new javax.swing.JLabel("This page could not be shown.");return rows.component;}
        });
    }

    /** The owned descriptor view of a plugin Settings page, if that page has been opened. */
    public java.util.Optional<FakeKitView> settingsPanelView(String pluginId) {
        var host = panelHosts.get(pluginId);
        return host == null || host.closed ? java.util.Optional.empty() : java.util.Optional.ofNullable(host.view);
    }

    /** Mirrors Settings search on an open plugin page, including its host-owned descriptor rows. */
    public void highlightSettingsPanel(String pluginId, String query) {
        var rows = panelHosts.get(pluginId);
        var context = contexts.get(pluginId);
        if (rows == null || rows.closed || context == null) throw new IllegalStateException("Settings page unavailable");
        context.settings.panel().orElseThrow().highlight(query);
        rows.highlight(query);
    }

    /** Applies the open settings draft; invalid rows reject Apply. */
    public void applySettingsPanel(String pluginId) {
        var rows=panelHosts.get(pluginId);var context=contexts.get(pluginId);
        if(rows==null || rows.closed || !rows.valid())throw new IllegalStateException("Settings page unavailable or invalid");
        rows.saveDraft();
        try{context.settings.panel().orElseThrow().apply();}
        catch(Throwable failure){recordFailure("Settings callback failed");throw new IllegalStateException("Settings callback failed");}
        rows.applied=true;
    }
    /** Cancels unapplied changes and releases the open page exactly once. */
    public void closeSettingsPanel(String pluginId) {
        var rows=panelHosts.remove(pluginId);if(rows==null || rows.closed)return;rows.closeDraft();
        var context=contexts.get(pluginId);if(context!=null)context.settings.panel().ifPresent(panel -> {
            try{if(!rows.applied)panel.reset();}catch(Throwable failure){recordFailure("Settings callback failed");}
            try{panel.dispose();}catch(Throwable failure){recordFailure("Settings callback failed");}
        });
        if(rows.view!=null)rows.view.close();
    }
    /** Values staged since settingsPanel built the page. */
    public Map<String, Object> pendingSettings(String pluginId) {
        FakeSettingsPanelHost rows = panelHosts.get(pluginId);
        return rows == null ? Map.of() : rows.pendingValues();
    }

    /**
     * How often a plugin's own settings page said its state changed.
     *
     * @param pluginId the plugin
     * @return the count since {@link #settingsPanel} built it
     */
    public int settingsPanelChanges(String pluginId) {
        FakeSettingsPanelHost rows = panelHosts.get(pluginId);
        return rows == null ? 0 : rows.changes;
    }

    /**
     * Activity events delivered so far.
     *
     * @return the events in delivery order
     */
    public List<ActivityEvent> activityLog() { return List.copyOf(activityLog); }

    /**
     * Connection events delivered so far.
     *
     * @return the events in delivery order
     */
    public List<ConnectionEvent> connectionLog() { return List.copyOf(connectionLog); }

    /**
     * What plugins posted and the groups declared for them.
     *
     * @return the host's notifications
     * @since 0.14.0
     */
    public FakeNotifications notifications() { return notifications; }

    /**
     * Contained failures: throwing starts, stops, handlers, factories and background tasks.
     *
     * @return one line per failure
     */
    public List<String> failures() { return List.copyOf(failures); }

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
        if (dataRoot == null || !ownsRoot) return;
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
        Objects.requireNonNull(next, "next");
        variant = next;
        if (workspaceMatches) publishWorkspace(new Workspace(next, appearance.palette(), appearance.editor(), appearance.syntax()), requestedOpacity);
        publishApp(AppEvents.THEME_CHANGED, new AppEvents.ThemeChanged(next));
        events.add(this::announceAppearance);
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
     * The spec an action was registered with, including its shortcut context and platform defaults.
     *
     * @param actionId the action
     * @return the spec, or empty when no started plugin registered the action
     * @since 0.11.0
     */
    public Optional<ActionSpec> actionSpec(String actionId) {
        for (FakePluginContext context : contexts.values()) {
            FakeUi.Action action = context.ui.actions.get(actionId);
            if (action != null) return Optional.of(action.spec);
        }
        return Optional.empty();
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
     * @return {@code button:id}, {@code menu:title:id,id} or {@code view}; closed actions are omitted
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
                    case ToolbarItem.View view -> lines.add("view");
                }
            }
        }
        return lines;
    }

    /**
     * UI thread: the toolbar views a terminal window shows, in toolbar order. The window builds each registered
     * {@code ToolbarItem.View} once, the first time this is asked for it, as a window builds its views when it opens; a
     * view whose factory threw or whose tree the toolbar does not admit is left out and recorded in {@link #failures()}.
     * A window that is not open, or whose views {@link #closeToolbarViews closed}, shows none and asks no factory.
     *
     * @param windowId a terminal window
     * @return the live views
     * @since 0.18.0
     */
    public List<FakeKitView> toolbarViews(UUID windowId) {
        if (closedToolbarWindows.contains(windowId) || workspace.window(windowId).isEmpty()) return List.of();
        List<FakeKitView> views = new ArrayList<>();
        for (FakePluginContext context : contexts.values()) views.addAll(context.ui.toolbarViews(windowId));
        return views;
    }

    /**
     * UI thread: closes a window's toolbar views, as the window does when it closes; a fake window does when its last
     * pane closes.
     *
     * @param windowId a terminal window
     * @since 0.18.0
     */
    public void closeToolbarViews(UUID windowId) {
        closedToolbarWindows.add(windowId);
        for (FakePluginContext context : contexts.values()) context.ui.closeToolbarViews(windowId);
    }

    /** The windows whose toolbar views closed, which build none again. */
    private final java.util.Set<UUID> closedToolbarWindows = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * One menu target's contributed entries.
     *
     * @param target {@code FILE}, {@code EDIT}, {@code VIEW}, {@code PANE}, {@code TAB}, {@code TOOLS}, {@code SESSION},
     *     {@code TRANSFER}, {@code context} or {@code top:<menuId>}
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

    /**
     * Visible progress controls keyed by their contribution id.
     * @return immutable current progress states
     * @since 0.7.5
     */
    public Map<String, dev.jasper.sdk.ui.StatusProgressState> progress() {
        var result = new java.util.LinkedHashMap<String, dev.jasper.sdk.ui.StatusProgressState>();
        for (var context : contexts.values()) for (var item : context.ui.status)
            if (item.visible && item.progress != null) result.put(item.spec.id(), item.progress);
        return Map.copyOf(result);
    }

    Path dataRoot() {
        if (dataRoot == null) {
            try { dataRoot = Files.createTempDirectory("jasper-fake-plugins"); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        return dataRoot;
    }

    /**
     * Declares a subcommand as a plugin's {@code [[subcommands]]} would; before that plugin starts. The first
     * declaration of a name keeps it. The application keeps a contested name for a bundled plugin first, then for the
     * lowest plugin id, so declare in that order to match it.
     *
     * @param pluginId the plugin that answers it
     * @param name the subcommand
     * @param summary its help line
     * @since 0.17.0
     */
    public void declareSubcommand(String pluginId, String name, String summary) {
        subcommands.putIfAbsent(name, new Declared(pluginId, name, summary));
    }

    /**
     * Calls a subcommand as {@code jasper name arguments...} would from outside Jasper. The handler has run when this
     * returns.
     *
     * @param name the subcommand
     * @param arguments the words after it
     * @param workingDirectory the caller's working directory
     * @return the call
     * @since 0.17.0
     */
    public FakeCommandCall call(String name, List<String> arguments, Path workingDirectory) {
        return call(name, arguments, workingDirectory, null);
    }

    /**
     * As {@link #call(String, List, Path)}, run in the pane {@code paneId}: the call's window is that pane's window.
     *
     * @param name the subcommand
     * @param arguments the words after it
     * @param workingDirectory the caller's working directory
     * @param paneId the pane, or null
     * @return the call
     * @since 0.17.0
     */
    public FakeCommandCall call(String name, List<String> arguments, Path workingDirectory, UUID paneId) {
        var call = new FakeCommandCall(arguments, workingDirectory);
        Declared declared = subcommands.get(name);
        if (declared == null) {
            call.hostError("jasper: unknown command " + name + "; run jasper --help for the list\n");
            call.complete(2);
            return call;
        }
        FakeCommandLine.Registered registered = subcommandHandlers.get(name);
        if (registered == null) {
            call.hostError("jasper: " + name + " is unavailable (the " + declared.pluginId() + " plugin is off)\n");
            call.complete(1);
            return call;
        }
        if (paneId != null) {
            call.window(workspace.pane(paneId).flatMap(pane -> registered.context().terminals.window(pane.tab.window.id))
                .map(window -> (dev.jasper.sdk.WindowOwner) window));
        }
        java.util.concurrent.CompletionStage<Integer> stage;
        try {
            stage = Objects.requireNonNull(registered.handler().run(call.view()), "the handler's stage");
        } catch (RuntimeException failure) {
            failures.add(declared.pluginId() + " subcommand " + name + ": " + failure);
            call.fail(name, failure);
            return call;
        }
        stage.whenComplete((code, failure) -> {
            if (failure == null) call.complete(code == null || code < 0 || code > 255 ? 1 : code);
            else call.fail(name, failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure);
        });
        if (!call.exit().isDone()) {
            // As the application: a call still running when its plugin stops ends with 1.
            var running = new RunningCall(registered.context(), name, call);
            runningCalls.add(running);
            call.exit().whenComplete((code, failure) -> runningCalls.remove(running));
        }
        return call;
    }

    private void teardown(FakePluginContext context, String reason) {
        closeSettingsPanel(context.plugin().id());
        context.state = FakePluginContext.State.CLOSED;
        workspace.sessions.stopping(context);
        context.closeOwned();
        context.settings.close();
        context.ui.closeAll();
        context.palette.closeAll();
        context.fileSources.closeAll();
        context.commandLine.closeAll();
        shellContributors.removeIf(entry -> entry.context() == context);
        ProvidedEditor provided = providedEditor;
        if (provided != null && provided.context() == context) providedEditor = null;
        for (RunningCall running : List.copyOf(runningCalls))
            if (running.context() == context) running.call().stopped(running.name());
        notifications.stopped(context.plugin().id());
        clipboard.ownerStopped(context.plugin().id());
        String id = context.plugin().id();
        for (var list : subscribers.values()) list.removeIf(entry -> entry.pluginId().equals(id));
        for (ActivityEvent open : List.copyOf(running.values())) {
            if (!open.sourcePluginId().equals(id)) continue;
            running.remove(open.id());
            enqueue(new ActivityEvent(open.id(), id, open.title(), ActivityEvent.State.FAILED,
                open.fraction(), reason, open.activateActionId()));
        }
        for (FakeConnection open : List.copyOf(connections.values()))
            if (open.pluginId.equals(id)) open.close();
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
        if (owner.equals(APP) && topic.equals(TerminalEvents.WINDOW_CLOSED)
                && payload instanceof TerminalEvents.WindowEvent gone) {
            var overlay = overlays.get(gone.windowId());
            if (overlay != null) overlay.close();
        }
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
        if (payload instanceof ActivityEvent event && topicId.equals(Activities.TOPIC.id())) {
            activityLog.add(event);
            outcome(event);
        }
        if (payload instanceof ConnectionEvent event && topicId.equals(Activities.CONNECTIONS.id())) connectionLog.add(event);
        for (Entry entry : subscribers.getOrDefault(topicId, new CopyOnWriteArrayList<>())) {
            if (entry.closed().get()) continue;
            try { entry.handler().accept(payload); }
            catch (RuntimeException | LinkageError failure) { failures.add(entry.pluginId() + " handler: " + failure); }
        }
    }

    /**
     * As the host does: an activity that succeeded or failed is posted in its plugin's activities group, unless the
     * plugin is no longer running.
     */
    private void outcome(ActivityEvent event) {
        FakePluginContext owner = contexts.get(event.sourcePluginId());
        if (owner == null || !active(event.sourcePluginId())) return;
        // Like the host: only the source plugin's own action, run in the window used last.
        Optional<Runnable> activate = event.activateActionId().filter(owner.ui.actions::containsKey)
            .<Runnable>map(id -> () -> {
                var window = workspace.lastActive;
                if (window != null) invoke(id, window.id, workspace.activePane().map(pane -> pane.id).orElse(null));
            });
        try { notifications.outcome(event, activate); }
        catch (IllegalArgumentException unshowable) { failures.add(event.sourcePluginId() + " activity outcome: " + unshowable); }
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

    ConnectionHandle connection(String pluginId, ConnectionSpec spec) {
        var connection = new FakeConnection(pluginId, Objects.requireNonNull(spec, "spec"));
        connections.put(connection.id, connection);
        connection.report(spec.state(), spec.detail());
        return connection;
    }

    private final class FakeConnection implements ConnectionHandle {
        private final UUID id = UUID.randomUUID();
        private final String pluginId;
        private final ConnectionSpec spec;
        private boolean closed;
        /** The last delivery queued; running empties it, so a report can only join one still waiting. */
        private AtomicReference<ConnectionEvent> queued;

        FakeConnection(String pluginId, ConnectionSpec spec) { this.pluginId = pluginId; this.spec = spec; }

        @Override public UUID id() { return id; }
        @Override public void up(String detail) { report(ConnectionEvent.State.UP, detail); }
        @Override public void degraded(String detail) { report(ConnectionEvent.State.DEGRADED, detail); }
        @Override public void down(String detail) { report(ConnectionEvent.State.DOWN, detail); }

        @Override public void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
                connections.remove(id);
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
            checkType(Activities.CONNECTIONS);
            var slot = new AtomicReference<>(event);
            queued = slot;
            events.add(() -> {
                ConnectionEvent due = slot.getAndSet(null);
                if (due != null) deliver(Activities.CONNECTIONS.id(), due);
            });
        }

        private ConnectionEvent event(ConnectionEvent.State state, String detail) {
            return new ConnectionEvent(id, pluginId, spec.title(), state, detail, spec.activateActionId());
        }
    }

    /**
     * Registered panels, registration order.
     *
     * @return lines of the form {@code id|title|LEFT|RIGHT|BOTTOM} (the default anchor, with {@code +split} when the
     *         panel starts in its side's secondary half), followed by {@code |actionId} when the panel names its own
     *         toggle action
     */
    public List<String> panels() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values())
            for (FakeUi.Panel panel : context.ui.panels) lines.add(panel.spec.id() + "|" + panel.spec.title() + "|" + panel.spec.defaultAnchor()
                + (panel.spec.split() ? "+split" : "") + panel.spec.toggleActionId().map(toggle -> "|" + toggle).orElse(""));
        return lines;
    }

    /** Returns the currently published header of an open panel instance.
     * @param panelId panel identifier
     * @param windowId owning window
     * @return header metadata, or an empty header if no instance exists
     */
    public dev.jasper.sdk.ui.PanelHeader panelHeader(String panelId, UUID windowId) {
        for (var context : contexts.values()) for (var panel : context.ui.panels) {
            if (!panel.spec.id().equals(panelId)) continue;
            var instance = panel.instances.get(windowId);
            if (instance != null) return instance.header;
        }
        return dev.jasper.sdk.ui.PanelHeader.EMPTY;
    }

    /**
     * Discards an open panel instance as its window would when the window closes: its header clears, retained header
     * callbacks become inert and its close handlers run. Nothing happens when no such instance is open.
     *
     * @param panelId panel identifier
     * @param windowId owning window
     */
    public void closePanel(String panelId, UUID windowId) {
        for (var context : contexts.values()) for (var panel : context.ui.panels) {
            if (!panel.spec.id().equals(panelId)) continue;
            var instance = panel.instances.remove(windowId);
            if (instance != null) instance.close();
        }
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

    /** The mounted owned view of an opened panel, when its content is a kit view. */
    public java.util.Optional<FakeKitView> panelView(String panelId, UUID windowId) {
        for (var context : contexts.values()) for (var panel : context.ui.panels) {
            if (!panel.spec.id().equals(panelId)) continue;
            var instance = panel.instances.get(windowId);
            if (instance == null || instance.done) return java.util.Optional.empty();
            return context.kit().views().stream().filter(view -> view.component == instance.content).findFirst();
        }
        return java.util.Optional.empty();
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

    private final java.util.ArrayDeque<Optional<Path>> fileSelections = new java.util.ArrayDeque<>();

    /**
     * Queues a response for the next native file chooser, including a settings page row's Browse. With no queued response,
     * it cancels.
     *
     * @param selection chosen file or empty for cancellation
     */
    public void queueFileSelection(Optional<Path> selection) {
        fileSelections.addLast(java.util.Objects.requireNonNull(selection).map(path -> path.toAbsolutePath().normalize()));
    }

    private final java.util.ArrayDeque<List<Path>> pathSelections = new java.util.ArrayDeque<>();
    private Runnable duringPathSelection = () -> {};

    /**
     * Queues multiple files, one directory or one save target (chooseFiles, chooseDirectory, chooseSaveFile); an empty list scripts cancellation.
     * @param selection selected local paths
     * @since 0.7.5
     */
    public void queuePathSelection(List<Path> selection) {
        pathSelections.addLast(selection.stream().map(path -> path.toAbsolutePath().normalize()).toList());
    }
    /**
     * Runs once during the next owner-based chooser, simulating its nested UI event loop.
     * @param action event such as owner close or plugin stop
     * @since 0.7.5
     */
    public void duringPathSelection(Runnable action) { duringPathSelection = java.util.Objects.requireNonNull(action); }

    List<Path> takePathSelection() {
        var paths = pathSelections.isEmpty() ? List.<Path>of() : pathSelections.removeFirst();
        var action = duringPathSelection; duringPathSelection = () -> {}; action.run();
        return paths;
    }

    Optional<Path> takeFileSelection() {
        return fileSelections.isEmpty() ? Optional.empty() : fileSelections.removeFirst();
    }

    /**
     * Inspects the actual Swing content of an open plugin surface for headless form tests.
     * @param pluginId surface owner
     * @param title exact surface title
     * @return content while open, empty after close or if absent
     */
    public java.util.Optional<javax.swing.JComponent> windowContent(String pluginId, String title) {
        FakePluginContext context = contexts.get(pluginId);
        if (context == null) return java.util.Optional.empty();
        return context.ui.windows.stream().filter(w -> !w.closed && w.title.equals(title))
            .map(w -> w.content).filter(java.util.Objects::nonNull).findFirst();
    }

    /**
     * The owned view mounted on an open plugin window or dialog, for driving {@code UiView} content in headless tests
     * (secrets through {@link FakeKitView#setSecret}, intents through {@link FakeKitView#emit}). Call it on the UI thread.
     * @param pluginId surface owner
     * @param title exact surface title
     * @return the mounted view while the surface is open, empty after close, for raw content or if absent
     */
    public java.util.Optional<FakeKitView> windowView(String pluginId, String title) {
        FakePluginContext context = contexts.get(pluginId);
        if (context == null) return java.util.Optional.empty();
        return context.ui.windows.stream().filter(w -> !w.closed && w.title.equals(title))
            .map(w -> w.mounted).filter(java.util.Objects::nonNull).findFirst();
    }

    /**
     * How an open plugin dialog is presented, as its {@code DialogSpec} asked. Call it on the UI thread.
     * @param pluginId dialog owner
     * @param title exact dialog title
     * @return the presentation while the dialog is open; empty for windows, overlays, closed or absent dialogs
     * @since 0.15.4
     */
    public java.util.Optional<dev.jasper.sdk.ui.DialogSpec.Presentation> dialogPresentation(String pluginId, String title) {
        FakePluginContext context = contexts.get(pluginId);
        if (context == null) return java.util.Optional.empty();
        return context.ui.windows.stream().filter(w -> !w.closed && w.title.equals(title))
            .map(w -> w.presentation).filter(java.util.Objects::nonNull).findFirst();
    }

    /**
     * The hue of the square an open plugin dialog draws before its title, as its {@code DialogSpec} asked. Call it on the
     * UI thread.
     * @param pluginId dialog owner
     * @param title exact dialog title
     * @return the swatch while the dialog (any presentation, {@code OVERLAY} dialogs included) is open; empty for plugin
     *     windows, {@code Windows.overlay} surfaces and closed or absent dialogs
     * @since 0.18.0
     */
    public java.util.Optional<dev.jasper.sdk.ui.Nodes.Hue> dialogSwatch(String pluginId, String title) {
        FakePluginContext context = contexts.get(pluginId);
        if (context == null) return java.util.Optional.empty();
        return context.ui.windows.stream().filter(w -> !w.closed && w.title.equals(title) && w.presentation != null)
            .map(w -> w.swatch).findFirst();
    }

    /** Shown producer-owned popover views, in creation order, for deterministic input/lifetime tests.
     * @param pluginId content producer @return live mounted views */
    public List<FakeKitView> popoverViews(String pluginId){var context=contexts.get(pluginId);if(context==null)return List.of();return context.ui.popovers.stream().filter(p->p.shown&&!p.closed&&p.content!=null).map(p->p.content).toList();}
    /** The currently shown plugin windows. */
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
     * Runs the Close Window command of the first open window with this id, as File → Close Window or Cmd/Ctrl+W
     * would: its close-command handlers newest first, then, when none handled it, its closing guards.
     *
     * @param windowId the window id
     * @return true when the window closed; false when a handler handled the command, a guard vetoed or there is no such window
     * @since 0.8.4
     */
    public boolean closeCommand(String windowId) {
        for (FakePluginContext context : contexts.values())
            for (FakeUi.FakeWindow window : List.copyOf(context.ui.windows)) if (window.id.equals(windowId)) return window.closeCommand();
        return false;
    }

    /**
     * Asks the closing guards of every open plugin window, then every quit guard, as quitting or restarting Jasper
     * does, and closes nothing. Dialogs and overlays are not asked. A quit guard whose future is still pending counts
     * as a refusal here: the fake does not pump events.
     *
     * @return whether the quit may go ahead
     * @since 0.8.4; quit guards since 0.11.0
     */
    public boolean requestQuit() {
        for (FakePluginContext context : contexts.values()) if (!context.ui.closeContent(List.copyOf(context.ui.contents), false).getNow(false)) return false;
        for (FakePluginContext context : contexts.values())
            for (FakeUi.FakeWindow window : List.copyOf(context.ui.windows))
                if (!window.id.equals("dialog") && !window.id.equals("overlay") && !window.mayClose()) return false;
        for (FakePluginContext context : contexts.values())
            for (var guard : List.copyOf(context.ui.quitGuards)) {
                java.util.concurrent.CompletableFuture<Boolean> decision;
                try { decision = guard.get(); }
                catch (RuntimeException failure) { recordFailure(context.plugin().id() + " quit guard: " + failure); continue; }
                if (decision == null) continue;
                if (!decision.handle((allowed, error) -> error != null || !Boolean.FALSE.equals(allowed)).getNow(false)) return false;
            }
        return true;
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
     * @return lines of the form {@code tab|<window id>|<directory or ->}, {@code split|<pane id>|<RIGHT or DOWN>|<directory or ->} (each followed by {@code |<profile id>} when the request named a shell profile, then by {@code |run:<the command's words joined by spaces>} when it runs a program) or {@code front|<window id>}
     */
    public List<String> openRequests() { return List.copyOf(workspace.openRequests); }

    /**
     * Replaces the shell profiles the application detected, as {@code Terminals.profiles()} returns them, and runs every
     * plugin's {@code onProfilesChanged} listener at once. A throwing listener is recorded in {@link #failures()}.
     *
     * @param profiles the profiles, in display order
     */
    public void setShellProfiles(List<dev.jasper.sdk.terminal.ShellProfileInfo> profiles) {
        workspace.profiles = List.copyOf(profiles);
        for (Runnable listener : workspace.profileListeners) {
            try { listener.run(); }
            catch (RuntimeException failure) { failures.add("profiles listener: " + failure); }
        }
    }

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

    /**
     * Registered file sources as {@code id|heading}, in registration order.
     *
     * @return the sources
     * @since 0.10.0
     */
    public List<String> fileSources() {
        return fileSources.values().stream().map(registered -> registered.spec().id() + "|" + registered.spec().heading()).toList();
    }

    /**
     * A source's roots as {@code id|label|connected}.
     *
     * @param sourceId a registered source
     * @return its roots now
     * @since 0.10.0
     */
    public List<String> fileRoots(String sourceId) {
        return registeredSource(sourceId).source().roots().stream().map(root -> root.id() + "|" + root.label() + "|" + root.connected()).toList();
    }

    /**
     * Asks a source where a pane is, as a chooser opened from that pane would.
     *
     * @param sourceId a registered source
     * @param paneId   a scripted pane
     * @return the source's answer
     * @since 0.10.0
     */
    public Optional<dev.jasper.sdk.files.FileLocation.Sourced> locateFile(String sourceId, UUID paneId) {
        var registered = registeredSource(sourceId);
        return registered.source().locate(registered.context().terminals.paneHandle(paneId));
    }

    /**
     * Connects to a source's root as the chooser would, running scripted background work until it has connected (at most 30 seconds).
     *
     * @param sourceId a registered source
     * @param rootId   one of its roots
     * @param windowId the terminal window the chooser belongs to
     * @return the browser; the caller closes it
     * @since 0.10.0
     */
    public dev.jasper.sdk.files.FileBrowser connectFileSource(String sourceId, String rootId, UUID windowId) {
        var registered = registeredSource(sourceId);
        var owner = registered.context().terminals.windowHandle(windowId);
        var pending = registered.source().connect(rootId, owner).toCompletableFuture();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (!pending.isDone()) {
            if (runBackground() > 0) continue;
            if (System.nanoTime() > deadline) throw new IllegalStateException("The file source did not connect within 30 seconds: " + sourceId);
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
        }
        return pending.join();
    }

    private FakeFileSources.Registered registeredSource(String sourceId) {
        var registered = fileSources.get(sourceId);
        if (registered == null) throw new IllegalArgumentException("No such file source: " + sourceId);
        return registered;
    }

    /**
     * Queues what the next {@code Windows.choose} returns; an empty list scripts cancellation.
     *
     * @param chosen the locations
     * @since 0.10.0
     */
    public void queueFileChoice(List<dev.jasper.sdk.files.FileLocation> chosen) { fileChoiceQueue.addLast(List.copyOf(chosen)); }

    /**
     * Every choice plugins asked for, in order.
     *
     * @return the choices
     * @since 0.10.0
     */
    public List<dev.jasper.sdk.files.FileChoice> fileChoices() { return List.copyOf(fileChoices); }

    List<dev.jasper.sdk.files.FileLocation> takeFileChoice(dev.jasper.sdk.files.FileChoice choice) {
        fileChoices.add(choice);
        var chosen = fileChoiceQueue.isEmpty() ? List.<dev.jasper.sdk.files.FileLocation>of() : fileChoiceQueue.removeFirst();
        var action = duringPathSelection; duringPathSelection = () -> {}; action.run();
        return chosen;
    }

        /** Registered scopes as {@code id|label|verbIds}, in registration order. */
        public List<String> scopes() {
            return scopes.values().stream().map(registered -> registered.spec().id() + "|" + registered.spec().label() + "|"
                + String.join(",", registered.spec().verbs().stream().map(PaletteVerb::id).toList())).toList();
        }

        /**
         * How a registered scope presents its rows where a presentation colours and labels them by scope, as
         * {@code HUE|itemNoun|ROLE,ROLE}: its hue, NONE read as NEUTRAL (the fake reads no manifest {@code icon_hue});
         * its item noun, or its label when it names none; and each verb's role, in order.
         *
         * @param scopeId a registered scope
         * @return the presentation
         * @since 0.18.0
         */
        public String scopePresentation(String scopeId) {
            var spec = scope(scopeId).spec();
            return (spec.hue() == dev.jasper.sdk.ui.Nodes.Hue.NONE ? dev.jasper.sdk.ui.Nodes.Hue.NEUTRAL : spec.hue()).name() + "|"
                + (spec.itemNoun().isEmpty() ? spec.label() : spec.itemNoun()) + "|"
                + String.join(",", spec.verbs().stream().map(verb -> verb.role().name()).toList());
        }

        /** The ids of registered scopes the palette's All tab searches, in registration order. */
        public List<String> scopesInAll() {
            return scopes.values().stream().filter(registered -> registered.spec().inAll()).map(registered -> registered.spec().id()).toList();
        }

        /** The ids of registered scopes the palette may offer before anything is typed, in registration order (since 0.9.2). */
        public List<String> scopesInRecents() {
            return scopes.values().stream().filter(registered -> registered.spec().inRecents()).map(registered -> registered.spec().id()).toList();
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
            for (PaletteRow row : results.rows()) {row.icon().ifPresent(registered.context().kit()::validateIcon);rows.put(row.id(), row);}
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

        /** Every file a plugin asked to open in the editor that no provided editor opened (the desktop's share). */
        public List<Path> openedInEditor() { return List.copyOf(openedInEditor); }

    /**
     * Every web address a plugin asked to open in the browser.
     *
     * @return the addresses in request order
     */
    public List<java.net.URI> browsed() { return List.copyOf(browsed); }

    /**
     * The clipboard every plugin's {@code Platform} writes to.
     *
     * @return the fake clipboard
     */
    public FakeClipboard clipboard() { return clipboard; }
    /** @return hosted content in registration order, for contract assertions */
    public List<dev.jasper.sdk.ui.ContentTab> contentTabs() {
        return contexts.values().stream().flatMap(c -> c.ui.contents.stream()).map(c -> (dev.jasper.sdk.ui.ContentTab)c).toList();
    }
    /** @param tab handle from this host
     * @return the actual hosted component */
    public javax.swing.JComponent contentComponent(dev.jasper.sdk.ui.ContentTab tab) { return ((FakeContent)tab).component; }
    /** @param tab handle from this host
     * @return recorded presentation */
    public dev.jasper.sdk.ui.ContentPresentation contentPresentation(dev.jasper.sdk.ui.ContentTab tab) { return ((FakeContent)tab).presentation; }
    /** @param tab handle from this host
     * @return originating owner */
    public dev.jasper.sdk.WindowOwner contentOwner(dev.jasper.sdk.ui.ContentTab tab) { return ((FakeContent)tab).owner; }
    /** @param tab hosted content handle
     * @return published status descriptors */
    public List<dev.jasper.sdk.ui.ContentStatus> contentStatus(dev.jasper.sdk.ui.ContentTab tab) { return ((FakeContent)tab).status; }

    /** A {@code Terminals.environment} contributor and the plugin that registered it. */
    record ShellContributor(FakePluginContext context, Function<Map<String, String>, Map<String, String>> contributor) { }
    final List<ShellContributor> shellContributors = new CopyOnWriteArrayList<>();
    private static final java.util.regex.Pattern SHELL_NAME = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> RESERVED_SHELL_NAMES = Set.of("TERM", "COLORTERM", "TERM_PROGRAM", "TERM_PROGRAM_VERSION");

    /** Keeps a contributor until its subscription closes or its plugin stops. */
    Subscription contributeShell(FakePluginContext context, Function<Map<String, String>, Map<String, String>> contributor) {
        var entry = new ShellContributor(context, contributor);
        shellContributors.add(entry);
        Subscription subscription = () -> shellContributors.removeIf(registered -> registered == entry);
        context.own(subscription);
        return subscription;
    }

    /**
     * What plugins' {@code Terminals.environment} contributors add to a new local shell, merged as the application merges
     * them: contributors run plugin by plugin in id order, then in registration order, each given a read-only copy of
     * {@code jasperEnvironment}; one that throws adds nothing (recorded in {@link #failures()}) and a null map counts as
     * empty. A name outside {@code [A-Za-z_][A-Za-z0-9_]*}, {@code TERM}, {@code COLORTERM}, {@code TERM_PROGRAM},
     * {@code TERM_PROGRAM_VERSION}, a name starting {@code JASPER_}, a null value and a value holding NUL are dropped, and
     * the first contributor of a name wins.
     *
     * @param jasperEnvironment Jasper's own environment
     * @return the variables, which the application puts after its own and before {@code [terminal.env]}
     * @since 0.17.1
     */
    public Map<String, String> shellEnvironment(Map<String, String> jasperEnvironment) {
        Map<String, String> environment = Map.copyOf(jasperEnvironment);
        List<ShellContributor> ordered = new ArrayList<>(shellContributors);
        ordered.sort(Comparator.comparing((ShellContributor entry) -> entry.context().plugin().id()));
        Map<String, String> merged = new LinkedHashMap<>();
        for (ShellContributor entry : ordered) {
            String pluginId = entry.context().plugin().id();
            List<Map.Entry<String, String>> offered = new ArrayList<>();
            try {
                Map<String, String> added = entry.contributor().apply(environment);
                if (added != null) added.forEach((name, value) -> offered.add(new java.util.AbstractMap.SimpleImmutableEntry<>(name, value)));
            } catch (RuntimeException failure) {
                failures.add(pluginId + " shell environment: " + failure);
                continue;
            }
            for (Map.Entry<String, String> variable : offered)
                if (keptShellVariable(variable.getKey(), variable.getValue())) merged.putIfAbsent(variable.getKey(), variable.getValue());
        }
        return Map.copyOf(merged);
    }

    /** Whether the application keeps a contributed entry. */
    private static boolean keptShellVariable(String name, String value) {
        return name != null && SHELL_NAME.matcher(name).matches() && !RESERVED_SHELL_NAMES.contains(name) && !name.startsWith("JASPER_")
            && value != null && value.indexOf('\0') < 0;
    }

    /** The editor a plugin provides ({@code Platform.editor}) and the plugin that registered it. */
    record ProvidedEditor(FakePluginContext context, dev.jasper.sdk.ui.FileEditor editor) { }
    private volatile ProvidedEditor providedEditor;
    private final Set<String> bundledPlugins = ConcurrentHashMap.newKeySet();

    /**
     * Marks a plugin as one that ships with Jasper, before it starts: its {@code Platform.editor} registration replaces
     * a non-bundled plugin's, as the application's bundled plugins' do.
     *
     * @param pluginId the plugin
     * @since 0.17.1
     */
    public void declareBundled(String pluginId) { bundledPlugins.add(Objects.requireNonNull(pluginId, "pluginId")); }

    /** Makes a plugin's editor the one files open with, under the application's one-at-a-time rule. */
    Subscription provideEditor(FakePluginContext context, dev.jasper.sdk.ui.FileEditor editor) {
        String pluginId = context.plugin().id();
        ProvidedEditor current = providedEditor;
        if (current != null && (!bundledPlugins.contains(pluginId) || bundledPlugins.contains(current.context().plugin().id())))
            throw new IllegalStateException("The editor is already provided by " + current.context().plugin().id());
        var provided = new ProvidedEditor(context, editor);
        providedEditor = provided;
        Subscription subscription = () -> { if (providedEditor == provided) providedEditor = null; };
        context.own(subscription);
        return subscription;
    }

    /** Asks the provided editor on the caller's thread; false when there is none, or it declined or threw. */
    boolean editProvided(Path file, OptionalInt line, Optional<dev.jasper.sdk.WindowOwner> window) {
        ProvidedEditor provided = providedEditor;
        return provided != null && editWith(provided, file, line, window);
    }

    private boolean editWith(ProvidedEditor provided, Path file, OptionalInt line, Optional<dev.jasper.sdk.WindowOwner> window) {
        try {
            return provided.editor().edit(file, line, window);
        } catch (RuntimeException failure) {
            failures.add(provided.context().plugin().id() + " file editor: " + failure);
            return false;
        }
    }

    /**
     * Opens a file as Jasper's own opens do (Open in Editor, the settings files): with the editor a plugin provides
     * through {@code Platform.editor}, given that plugin's handle for {@code windowId} while the window is open.
     *
     * @param file the file
     * @param line the line to show, or empty
     * @param windowId the terminal window the open came from, or empty
     * @return what the editor answered; false when no plugin provides one, or it threw, which {@link #failures()} records
     * @since 0.17.1
     */
    public boolean edit(Path file, OptionalInt line, Optional<UUID> windowId) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(windowId, "windowId");
        ProvidedEditor provided = providedEditor;
        if (provided == null) return false;
        Optional<dev.jasper.sdk.WindowOwner> window = windowId.flatMap(id -> provided.context().terminals.window(id))
            .map(handle -> (dev.jasper.sdk.WindowOwner) handle);
        return editWith(provided, file, line, window);
    }

    /**
     * Which plugin provides the editor now.
     *
     * @return its id, or empty
     * @since 0.17.1
     */
    public Optional<String> editorPlugin() { return Optional.ofNullable(providedEditor).map(provided -> provided.context().plugin().id()); }

    volatile Optional<Path> commandClient = Optional.empty();

    /**
     * Sets the {@code jasper} command {@code CommandLine.client()} names, as the application names its installed or built
     * client; until then it is empty.
     *
     * @param client the client's path
     * @since 0.17.1
     */
    public void setCommandClient(Path client) { commandClient = Optional.of(Objects.requireNonNull(client, "client")); }
}
