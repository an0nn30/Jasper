package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.sdk.JasperSdk;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ConnectionEvent;
import dev.jasper.sdk.events.AppEvents;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import dev.jasper.app.terminals.TerminalRegistry;

/**
 * The application's single entry to plugins: discover, resolve, load and start them once at launch,
 * forward configuration and theme changes, show their activities on Buddy, and stop them at shutdown.
 * Install, enable, disable and update take effect at the next start. EDT only, except {@link #executing}
 * and {@link #maintain}.
 */
public final class PluginRuntime {
    /** The parent of plugins' pages in Settings. */
    public static final String TOOLS = "tools";

    /**
     * The id of a plugin's page in Settings.
     *
     * @param pluginId the plugin
     * @return {@code plugin.<id>}
     */
    public static String settingsPageId(String pluginId) { return "plugin." + pluginId; }

    /**
     * Where plugins and their state live.
     *
     * @param bundledDirectory plugins shipped with the application, or null
     * @param userDirectory plugins the user installed; need consent
     * @param developmentDirectory one plugin directory from {@code --plugin-dir}, or null
     * @param safeMode skip user plugins
     * @param stateFile {@code plugins.toml}
     * @param lockFile cross-process lock for the state file
     */
    public record Options(Path bundledDirectory, Path userDirectory, Path developmentDirectory, boolean safeMode,
                          Path stateFile, Path lockFile) { }

    /**
     * One plugin as the Plugins manager shows it: what is running, what the next launch would run,
     * and what the user may do about it.
     *
     * @param id the plugin id
     * @param name its display name
     * @param version the version the next launch would consider, or the running one when it is being removed
     * @param description the descriptor's description, possibly empty
     * @param vendor the descriptor's vendor, possibly empty
     * @param origin {@code Bundled}, {@code Installed} or {@code Development}
     * @param state {@code ACTIVE}, {@code DISABLED}, {@code NEEDS_CONSENT}, {@code SKIPPED} or {@code FAILED} in this process, or {@code NOT_LOADED} for a plugin found since launch that would load
     * @param reason why, possibly empty
     * @param capabilities what the plugin declares, sorted
     * @param unconsented the declared capabilities the user has not consented to, sorted
     * @param requires its dependencies, for display
     * @param errors contained failures of the plugin in this process
     * @param enabled whether the saved state enables it
     * @param needsConsent whether it needs a review before it can load
     * @param canToggle whether enabling and disabling is offered
     * @param canRemove whether removal is offered
     * @param pendingRemoval whether it is marked for removal
     * @param pendingInstall whether a staged version waits for the next launch
     * @param pending what a restart changes for this plugin, or empty
     * @param directory the plugin's folder under {@code plugins/}
     * @param settingsFile its settings file
     * @param dataDirectory its data directory
     * @param homepage the plugin's homepage, an http or https address; empty when it has none
     * @param exports the packages the plugin exports, sorted
     * @param icon the icon the plugin names in its manifest, as its jar holds it; empty when it names none or it cannot be read
     */
    public record Row(String id, String name, String version, String description, String vendor, String origin,
                      String state, String reason, List<String> capabilities, List<String> unconsented,
                      List<String> requires, int errors, boolean enabled, boolean needsConsent, boolean canToggle,
                      boolean canRemove, boolean pendingRemoval, boolean pendingInstall, String pending,
                      Path directory, Path settingsFile, Path dataDirectory, String homepage, List<String> exports,
                      Optional<PluginIcon> icon) {
        /** Copies the lists. */
        public Row {
            capabilities = List.copyOf(capabilities);
            unconsented = List.copyOf(unconsented);
            requires = List.copyOf(requires);
            exports = List.copyOf(exports);
            Objects.requireNonNull(directory, "directory"); Objects.requireNonNull(settingsFile, "settingsFile"); Objects.requireNonNull(dataDirectory, "dataDirectory");
            Objects.requireNonNull(homepage, "homepage"); Objects.requireNonNull(icon, "icon");
        }
    }

    /**
     * A plugin's icon as its jar holds it; the Plugins page decodes it.
     *
     * @param name the path in the jar, ending in {@code .svg} or {@code .png}
     * @param data the file's bytes
     */
    public record PluginIcon(String name, byte[] data) {
        /** Copies the bytes. */
        public PluginIcon {
            Objects.requireNonNull(name, "name");
            data = data.clone();
        }

        /** @return a copy of the bytes */
        @Override public byte[] data() { return data.clone(); }

        /** @return whether the icon is an SVG rather than a PNG */
        public boolean svg() { return name.toLowerCase(java.util.Locale.ROOT).endsWith(".svg"); }

        @Override public boolean equals(Object other) {
            return other instanceof PluginIcon icon && icon.name.equals(name) && java.util.Arrays.equals(icon.data, data);
        }

        @Override public int hashCode() { return 31 * name.hashCode() + java.util.Arrays.hashCode(data); }

        @Override public String toString() { return "PluginIcon[" + name + ", " + data.length + " bytes]"; }
    }

    /**
     * Every plugin, sorted by id.
     *
     * @param rows the plugins
     * @param restartNeeded whether the next launch would run a different plugin set than this process
     * @param safeMode whether this process runs without user plugins
     */
    public record Snapshot(List<Row> rows, boolean restartNeeded, boolean safeMode) {
        /** Copies the rows. */
        public Snapshot { rows = List.copyOf(rows); }
    }

    /**
     * A plugin zip that was unpacked and validated but not installed: what the consent dialog shows.
     *
     * @param staged the staging directory; pass the inspection back to {@link #install} or {@link #discard}
     * @param id the plugin id
     * @param name its display name
     * @param version its version
     * @param description the descriptor's description, possibly empty
     * @param vendor the descriptor's vendor, possibly empty
     * @param capabilities what it declares, sorted
     * @param update whether a plugin with this id is already installed or staged
     */
    public record Inspection(Path staged, String id, String name, String version, String description, String vendor,
                             List<String> capabilities, boolean update) {
        /** Copies the capabilities. */
        public Inspection { capabilities = List.copyOf(capabilities); }
    }

    /**
     * The result of a manager operation.
     *
     * @param ok whether it succeeded
     * @param message why not, written for the user; empty on success
     * @param snapshot the plugins afterwards, or null when the operation failed
     */
    public record Outcome(boolean ok, String message, Snapshot snapshot) { }

    /**
     * A plugin callback that failed and was contained.
     *
     * @param pluginId the plugin
     * @param operation what the host asked it to do, such as {@code start} or {@code panel <id>}
     * @param cause what it threw
     */
    public record Failure(String pluginId, String operation, Throwable cause) {
        public Failure {
            Objects.requireNonNull(pluginId, "pluginId");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(cause, "cause");
        }
    }

    /**
     * A plugin's notification that carries a cause, for the application to record the cause as the plugin's incident.
     *
     * @param pluginId the plugin
     * @param request the notification as the plugin asked for it
     * @param cause the failure behind it
     * @param posted hears the posted notification's id on the UI thread, or empty when its group dropped it
     */
    public record CausedNotice(String pluginId, NotificationRequest request, Throwable cause, Consumer<Optional<UUID>> posted) {
        public CausedNotice {
            Objects.requireNonNull(pluginId, "pluginId");
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(cause, "cause");
            Objects.requireNonNull(posted, "posted");
        }
    }

    /**
     * Where plugins' notifications go (spec section 6).
     *
     * @param center the application's notification center; the host reaches it on the UI thread
     * @param caused records a post's cause as the plugin's incident and posts it with Show more…; any thread
     * @param problem posts a problem with plugins, as a title and details, among the application's plugin problems;
     *     any thread
     */
    public record NotificationHost(NotificationCenter center, Consumer<CausedNotice> caused, BiConsumer<String, String> problem) {
        public NotificationHost {
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(caused, "caused");
            Objects.requireNonNull(problem, "problem");
        }

        /**
         * A host whose notifications nobody looks at, for a runtime without the application (previews and tests): a
         * cause is not recorded, and a problem is only logged.
         *
         * @return the host
         */
        public static NotificationHost detached() {
            var center = new NotificationCenter(SwingUtilities::invokeLater, Clock.systemDefaultZone(), () -> Delivery.Presence.NO_WINDOW);
            return new NotificationHost(center,
                caused -> SwingUtilities.invokeLater(() -> caused.posted().accept(center.post(caused.request()))),
                (title, details) -> LOG.log(System.Logger.Level.WARNING, "A plugin problem: " + title));
        }
    }

    /**
     * Where plugin settings problems go, on the UI thread.
     *
     * @param report adds one problem, attributed to the plugin's own settings file
     * @param clear forgets every problem in one settings file, before its plugin sees the reloaded values
     */
    public record SettingsReports(Consumer<dev.jasper.app.config.ConfigDiagnostic> report, Consumer<Path> clear) {
        /** Drops every report. */
        public static final SettingsReports IGNORED = new SettingsReports(diagnostic -> { }, file -> { });
    }

    /**
     * The shell profiles the application detected, as plugins see them through {@code Terminals.profiles()}. Both
     * functions are called on the UI thread.
     *
     * @param current the profiles now, in display order
     * @param onChanged registers a listener the catalog calls on the UI thread after a refresh; closing the result removes it
     */
    public record ShellProfiles(Supplier<List<ShellProfile>> current, Function<Runnable, dev.jasper.app.lifecycle.Subscription> onChanged) {
        /** No profiles and no changes, for a runtime built without the catalog. */
        public static final ShellProfiles NONE = new ShellProfiles(List::of, listener -> new dev.jasper.app.lifecycle.Subscription(() -> { }));

        /** Rejects nulls. */
        public ShellProfiles {
            Objects.requireNonNull(current, "current");
            Objects.requireNonNull(onChanged, "onChanged");
        }
    }

    private static final System.Logger LOG = System.getLogger(PluginRuntime.class.getName());
    private static final Duration DRAIN_GRACE = Duration.ofMillis(1500);
    private static final Duration LOCK_WAIT = Duration.ofSeconds(2);

    private final Options options;
    private final ActivityNotifier notifier;
    private final SettingsReports reports;
    private final Contributions contributions;
    private final AuxiliaryWindows windows;
    private final TerminalRegistry terminals;
    private final NotificationHost notifications;
    private final Consumer<Path> editor;
    private final ShellProfiles profiles;
    private java.util.function.Supplier<dev.jasper.app.appearance.ResolvedTheme> appearance;
    private java.util.function.Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> appearanceSubscriber;

    /** Supplies native appearance values before plugins start. Called on the UI thread.
     * @param current current immutable two-slot theme
     * @param subscribe subscription that immediately replays the current value
     */
    public void appearance(java.util.function.Supplier<dev.jasper.app.appearance.ResolvedTheme> current,
            java.util.function.Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> subscribe) {
        if (host != null) throw new IllegalStateException("Plugins have already started");
        appearance = Objects.requireNonNull(current);
        appearanceSubscriber = Objects.requireNonNull(subscribe);
    }

    private volatile boolean dark = true;
    private final List<PluginStatus> statuses = new ArrayList<>();
    private final List<PluginClassLoader> loaders = new ArrayList<>();
    private final PluginSettingsFiles settingsFiles;
    private java.util.concurrent.ScheduledExecutorService settingsPoller;
    private volatile Map<String, PluginCandidate> launched = Map.of();
    private volatile Consumer<Failure> failureSink = failure -> { };
    private volatile PluginHost host;
    private Subscription bridge, connectionBridge;
    private volatile PluginCatalog.Launch launch;
    private PluginAdmin admin;
    private ExecutorService adminWorker;

    /**
     * Creates an idle runtime.
     *
     * @param options locations and mode
     * @param notifier receives plugin activities for Buddy
     * @param reports receives plugin complaints about their settings, and clears them per file
     * @param contributions the application-wide model that plugin chrome contributions are written to
     * @param windows builds plugin windows and dialogs
     * @param terminals the application-wide directory of terminal windows, tabs and panes
     */
    public PluginRuntime(Options options, ActivityNotifier notifier, SettingsReports reports,
                         Contributions contributions, AuxiliaryWindows windows, TerminalRegistry terminals) {
        this(options, notifier, reports, contributions, windows, terminals, NotificationHost.detached(),
            new dev.jasper.app.platform.ConfigEditor()::open);
    }

    /**
     * As the six-argument constructor, plus where plugin notifications and editor requests go.
     *
     * @param options locations and mode
     * @param notifier receives plugin activities for Buddy
     * @param reports receives plugin complaints about their settings, and clears them per file
     * @param contributions the application-wide model that plugin chrome contributions are written to
     * @param windows builds plugin windows and dialogs
     * @param terminals the application-wide directory of terminal windows, tabs and panes
     * @param notifications where plugins' notifications and plugin problems go
     * @param editor opens a file in the user's editor, off the UI thread; may throw
     */
    public PluginRuntime(Options options, ActivityNotifier notifier, SettingsReports reports,
                         Contributions contributions, AuxiliaryWindows windows, TerminalRegistry terminals,
                         NotificationHost notifications, Consumer<Path> editor) {
        this(options, notifier, reports, contributions, windows, terminals, notifications, editor, ShellProfiles.NONE);
    }

    /**
     * As the eight-argument constructor, plus the shell profiles plugins may list and open.
     *
     * @param options locations and mode
     * @param notifier receives plugin activities for Buddy
     * @param reports receives plugin complaints about their settings, and clears them per file
     * @param contributions the application-wide model that plugin chrome contributions are written to
     * @param windows builds plugin windows and dialogs
     * @param terminals the application-wide directory of terminal windows, tabs and panes
     * @param notifications where plugins' notifications and plugin problems go
     * @param editor opens a file in the user's editor, off the UI thread; may throw
     * @param profiles the detected shell profiles
     */
    public PluginRuntime(Options options, ActivityNotifier notifier, SettingsReports reports,
                         Contributions contributions, AuxiliaryWindows windows, TerminalRegistry terminals,
                         NotificationHost notifications, Consumer<Path> editor, ShellProfiles profiles) {
        this.notifications = Objects.requireNonNull(notifications);
        this.editor = Objects.requireNonNull(editor);
        this.profiles = Objects.requireNonNull(profiles);
        this.options = Objects.requireNonNull(options);
        this.notifier = Objects.requireNonNull(notifier);
        this.reports = Objects.requireNonNull(reports);
        this.settingsFiles = new PluginSettingsFiles(options.userDirectory(), diagnostic -> onUi(() -> reports.report().accept(diagnostic)));
        this.contributions = Objects.requireNonNull(contributions);
        this.windows = Objects.requireNonNull(windows);
        this.terminals = Objects.requireNonNull(terminals);
    }

    /**
     * The bundled plugin directory: the {@code jasper.plugins.bundled} system property when set (Gradle
     * run and tests), otherwise {@code plugins} beside the application jar. A classes directory has none.
     *
     * @param codeSource the application's code source, or null
     * @return the directory, which need not exist, or null
     */
    public static Path bundledDirectory(Path codeSource) {
        String override = System.getProperty("jasper.plugins.bundled");
        if (override != null && !override.isBlank()) return Path.of(override);
        if (codeSource == null || !Files.isRegularFile(codeSource)) return null;
        return codeSource.toAbsolutePath().getParent().resolve("plugins");
    }

    /**
     * Carries out pending plugin removals and installs. Call once per launch, before {@link #start} and
     * never on the EDT: it waits, bounded, for the cross-process state lock. Never throws.
     *
     * @param userDirectory plugins the user installed
     * @param stateFile {@code plugins.toml}
     * @param lockFile cross-process lock for the state file
     */
    public static void maintain(Path userDirectory, Path stateFile, Path lockFile) { maintain(userDirectory, stateFile, lockFile, null); }

    /**
     * As {@link #maintain(Path, Path, Path)}, migrating the pre-2026-09-22 {@code plugin-data/} directory into
     * {@code plugins/<id>/data/} once.
     *
     * @param userDirectory plugins the user installed
     * @param stateFile {@code plugins.toml}
     * @param lockFile cross-process lock for the state file
     * @param legacyDataRootOrNull the old data root, or null
     */
    public static void maintain(Path userDirectory, Path stateFile, Path lockFile, Path legacyDataRootOrNull) {
        PluginMaintenance.apply(userDirectory, new PluginStateStore(stateFile, lockFile, LOCK_WAIT), legacyDataRootOrNull,
            Version.parse(JasperSdk.VERSION));
    }

    /**
     * Discovers, resolves, loads and starts plugins in dependency order. Call once, before the first window.
     *
     * @param pluginTables the {@code [plugins."<id>"]} tables of the configuration, used only to seed a missing settings file
     * @param dark whether the current look is dark
     */
    public void start(Map<String, Map<String, Object>> pluginTables, boolean dark) {
        if (host != null) throw new IllegalStateException("Plugins already started");
        this.dark = dark;
        long began = System.nanoTime();
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> candidates = new ArrayList<>(
            PluginDiscovery.scan(options.bundledDirectory(), PluginCandidate.Origin.BUNDLED, problems));
        candidates.addAll(PluginDiscovery.scan(options.userDirectory(), PluginCandidate.Origin.USER, problems));
        if (options.developmentDirectory() != null)
            PluginDiscovery.single(options.developmentDirectory(), PluginCandidate.Origin.DEV, problems).ifPresent(candidates::add);
                for (PluginCandidate candidate : candidates) {
                    try { PluginSettingsFiles.prepare(options.userDirectory(), candidate, pluginTables.get(candidate.id())); }
                    catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Could not prepare the settings of " + candidate.id(), failure); }
                }
        var byId = new LinkedHashMap<String, PluginCandidate>();
        for (PluginCandidate candidate : candidates) byId.putIfAbsent(candidate.id(), candidate);
        launched = Map.copyOf(byId);
        Map<String, PluginStateStore.Entry> state;
        try { state = new PluginStateStore(options.stateFile(), options.lockFile(), LOCK_WAIT).read(); }
        catch (IOException unreadable) {
            // Without readable consent, user plugins stay inert rather than running unreviewed.
            LOG.log(System.Logger.Level.WARNING, "Plugin state is unreadable; user plugins will need consent", unreadable);
            state = Map.of();
        }
        var resolution = PluginResolver.resolve(candidates, state, Version.parse(JasperSdk.VERSION), options.safeMode());
        statuses.addAll(resolution.rejected());
        PluginHost created = new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, id -> options.userDirectory().resolve(id).resolve("data"),
            reports.report(), DRAIN_GRACE, contributions, () -> this.dark, windows, terminals, notifications, editor, settingsFiles, reports.clear(), profiles,
            dev.jasper.app.platform.Browser::open, appearance, appearanceSubscriber, failure -> failureSink.accept(failure)));
        host = created;
        bridge = created.bus.subscribe(EventBus.APP, Activities.TOPIC, this::forward);
        connectionBridge = created.bus.subscribe(EventBus.APP, Activities.CONNECTIONS, this::forwardConnection);
                settingsPoller = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().daemon().name("jasper-plugin-settings").factory());
                settingsPoller.scheduleWithFixedDelay(() -> {
                    Set<String> changed = settingsFiles.refresh(false);
                    if (!changed.isEmpty()) SwingUtilities.invokeLater(() -> applySettings(changed));
                }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
        Map<String, PluginLoader.Loaded> loaded = new LinkedHashMap<>();
        for (PluginCandidate candidate : resolution.load()) {
            PluginLoader.Loaded unit;
            try { unit = PluginLoader.load(candidate, loaded, PluginRuntime.class.getClassLoader()); }
            catch (PluginLoader.LoadFailure failure) {
                statuses.add(PluginStatus.of(candidate, PluginStatus.State.FAILED, failure.getMessage()));
                continue;
            }
            loaded.put(candidate.id(), unit);
            loaders.add(unit.loader());
            Set<String> hard = new HashSet<>(), optional = new HashSet<>();
            for (var requirement : candidate.descriptor().requires())
                (requirement.optional() ? optional : hard).add(requirement.id());
            PluginHost.Outcome outcome = created.start(new HostedPlugin(candidate.descriptor().info(), hard, optional,
                candidate.descriptor().exports(), unit.loader(), unit::instantiate));
            statuses.add(PluginStatus.of(candidate, outcome.state(), outcome.reason()));
        }
        launch = new PluginCatalog.Launch(candidates, statuses, PluginCatalog.versions(resolution.load()), options.safeMode());
        admin = new PluginAdmin(options, Version.parse(JasperSdk.VERSION), () -> launch, created.containment::failures, LOCK_WAIT);
        adminWorker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("jasper-plugin-admin").factory());
        problems.forEach(problem -> LOG.log(System.Logger.Level.WARNING, "Plugin discovery: " + problem));
        statusLines().forEach(line -> LOG.log(System.Logger.Level.INFO, "Plugin " + line));
        LOG.log(System.Logger.Level.INFO, "Plugins started in " + (System.nanoTime() - began) / 1_000_000 + " ms");
    }

    /**
     * Re-reads every plugin's settings file, then announces the reload. The tables are no longer read.
     *
     * @param pluginTables ignored; kept for callers
     */
    public void configurationChanged(Map<String, Map<String, Object>> pluginTables) {
        PluginHost current = host;
        if (current == null) return;
        applySettings(settingsFiles.refresh(true));
        current.bus.publish(EventBus.APP, AppEvents.CONFIG_RELOADED, new AppEvents.ConfigReloaded());
    }

    /**
     * Announces a change of look.
     *
     * @param dark whether the new look is dark
     */
    public void themeChanged(boolean dark) {
        this.dark = dark;
        PluginHost current = host;
        if (current != null) current.bus.publish(EventBus.APP, AppEvents.THEME_CHANGED,
            new AppEvents.ThemeChanged(dark ? Variant.DARK : Variant.LIGHT));
    }

    /** Discovered plugin display names, without performing file I/O. */
    public Map<String, String> pluginNames() {
        var names = new java.util.LinkedHashMap<String, String>();
        launched.forEach((id, candidate) -> names.put(id, candidate.descriptor().name()));
        return Map.copyOf(names);
    }

    /**
     * Where contained plugin failures go after they are logged; the application records each as an incident.
     *
     * @param sink hears each failure on the thread it happened on
     */
    public void reportFailuresTo(Consumer<Failure> sink) { failureSink = Objects.requireNonNull(sink, "sink"); }

    /**
     * Discovered plugin versions, without performing file I/O.
     *
     * @return each plugin's version by id
     */
    public Map<String, String> pluginVersions() {
        var versions = new java.util.LinkedHashMap<String, String>();
        launched.forEach((id, candidate) -> versions.put(id, candidate.descriptor().version().toString()));
        return Map.copyOf(versions);
    }

    /**
     * One line per discovered plugin: id, version, origin, state and reason.
     *
     * @return the lines, sorted by id
     */
    public List<String> statusLines() {
        return statuses.stream().map(PluginStatus::formatted).sorted().toList();
    }

    /**
     * The plugin callback running on the EDT right now, for the exit deadline's log line. Any thread.
     *
     * @return for example {@code dev.example.tool (stop)}, or null
     */
    public String executing() {
        PluginHost current = host;
        return current == null ? null : current.executing();
    }

    /**
     * Stops plugins in reverse order. The futures complete when background work has drained and the
     * classloaders are closed; the caller bounds the wait.
     *
     * @return asynchronous remainders of the shutdown
     */
    public List<CompletableFuture<?>> stop() {
        if (settingsPoller != null) settingsPoller.shutdownNow();
        PluginHost current = host;
        if (current == null) return List.of();
        if (adminWorker != null) adminWorker.shutdown();
        List<CompletableFuture<?>> pending = new ArrayList<>(current.stop());
        List<PluginClassLoader> closing = List.copyOf(loaders);
        loaders.clear();
        pending.add(CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).whenComplete((done, failure) -> {
            for (PluginClassLoader loader : closing) {
                try { loader.close(); }
                catch (IOException ignored) { LOG.log(System.Logger.Level.DEBUG, "Could not close " + loader.getName()); }
            }
        }));
        return pending;
    }

    /** Runs one manager operation on the worker and answers on the EDT. A late answer after stop is dropped by the caller's window. */
    private void manage(Callable<Snapshot> work, Consumer<Outcome> done) {
        Objects.requireNonNull(done, "done");
        if (adminWorker == null || adminWorker.isShutdown()) { done.accept(new Outcome(false, "Plugins are not running", null)); return; }
        adminWorker.execute(() -> {
            Outcome outcome;
            try { outcome = new Outcome(true, "", work.call()); }
            catch (Exception failure) {
                LOG.log(System.Logger.Level.WARNING, "A plugin manager operation failed", failure);
                outcome = new Outcome(false, failure.getMessage() == null ? failure.toString() : failure.getMessage(), null);
            }
            Outcome result = outcome;
            SwingUtilities.invokeLater(() -> done.accept(result));
        });
    }

    /**
     * Lists every plugin from a fresh look at the disk and the saved state.
     *
     * @param done receives the outcome on the EDT
     */
    public void snapshot(Consumer<Outcome> done) { manage(() -> admin.snapshot(), done); }

    /**
     * Enables or disables a reviewed plugin from the next launch on.
     *
     * @param id the plugin
     * @param enabled the new saved state
     * @param done receives the outcome on the EDT
     */
    public void setEnabled(String id, boolean enabled, Consumer<Outcome> done) { manage(() -> admin.setEnabled(id, enabled), done); }

    /**
     * Records the user's consent to exactly the capabilities they reviewed, which also enables the plugin.
     *
     * @param id the plugin
     * @param reviewed the capabilities the user was shown
     * @param done receives the outcome on the EDT
     */
    public void consent(String id, List<String> reviewed, Consumer<Outcome> done) {
        List<String> shown = List.copyOf(reviewed);
        manage(() -> admin.consent(id, shown), done);
    }

    /**
     * Marks an installed plugin for removal at the next launch, or withdraws the mark.
     *
     * @param id the plugin
     * @param remove whether to remove it
     * @param done receives the outcome on the EDT
     */
    public void remove(String id, boolean remove, Consumer<Outcome> done) { manage(() -> admin.remove(id, remove), done); }

    /**
     * Unpacks and validates a plugin zip without installing it.
     *
     * @param zip the file the user chose
     * @param done receives, on the EDT, the inspection or else a message for the user
     */
    public void inspect(Path zip, java.util.function.BiConsumer<Inspection, String> done) {
        Objects.requireNonNull(done, "done");
        if (adminWorker == null || adminWorker.isShutdown()) { done.accept(null, "Plugins are not running"); return; }
        adminWorker.execute(() -> {
            Inspection inspection = null;
            String message = null;
            try { inspection = admin.inspect(zip); }
            catch (PluginInstaller.InstallFailure | RuntimeException failure) {
                message = failure.getMessage() == null ? failure.toString() : failure.getMessage();
            }
            Inspection found = inspection;
            String problem = message;
            SwingUtilities.invokeLater(() -> done.accept(found, problem));
        });
    }

    /**
     * Installs an inspected plugin at the next launch and records consent to the capabilities the user was shown.
     *
     * @param inspection what {@link #inspect} returned
     * @param done receives the outcome on the EDT
     */
    public void install(Inspection inspection, Consumer<Outcome> done) {
        manage(() -> admin.install(inspection.staged(), inspection.capabilities()), done);
    }

    /**
     * Forgets an inspected plugin the user declined.
     *
     * @param inspection what {@link #inspect} returned
     */
    public void discard(Inspection inspection) {
        if (adminWorker != null && !adminWorker.isShutdown()) adminWorker.execute(() -> admin.discard(inspection.staged()));
    }

    /**
     * Drops an install that waits for the next launch.
     *
     * @param id the plugin
     * @param done receives the outcome on the EDT
     */
    public void discardInstall(String id, Consumer<Outcome> done) { manage(() -> admin.discardInstall(id), done); }

    private void forward(ActivityEvent event) {
        String detail = event.fraction().isPresent()
            ? Math.round(event.fraction().getAsDouble() * 100) + "%" + (event.detail().isEmpty() ? "" : " · " + event.detail())
            : event.detail();
        Optional<Runnable> action = activation(event.sourcePluginId(), event.activateActionId());
        switch (event.state()) {
            case STARTED -> notifier.started(event.sourcePluginId(), event.id(), event.title(), detail, action);
            case PROGRESS -> notifier.progress(event.sourcePluginId(), event.id(), detail);
            case SUCCEEDED -> notifier.finished(event.sourcePluginId(), event.id(), event.title(), true, event.detail(), action);
            case FAILED -> notifier.finished(event.sourcePluginId(), event.id(), event.title(), false, event.detail(), action);
            case CANCELLED -> notifier.cancelled(event.sourcePluginId(), event.id());
        }
    }

    private void forwardConnection(ConnectionEvent event) {
        switch (event.state()) {
            case UP -> connection(event, ActivityNotifier.ConnectionState.UP);
            case DEGRADED -> connection(event, ActivityNotifier.ConnectionState.DEGRADED);
            case DOWN -> connection(event, ActivityNotifier.ConnectionState.DOWN);
            case CLOSED -> notifier.connectionClosed(event.sourcePluginId(), event.id());
        }
    }

    private void connection(ConnectionEvent event, ActivityNotifier.ConnectionState state) {
        notifier.connection(event.sourcePluginId(), event.id(), event.title(), state, event.detail(),
            activation(event.sourcePluginId(), event.activateActionId()));
    }

    /**
     * A card's own activation: the source plugin's action, invoked in the window the user used last. Empty when
     * the id names no action that plugin registered, so the card only raises the application.
     */
    private Optional<Runnable> activation(String source, Optional<String> actionId) {
        PluginHost current = host;
        return current == null ? Optional.empty() : current.activation(source, actionId);
    }

    /**
     * Whether a plugin's own settings file exists, so the old {@code [plugins."<id>"]} table in config.toml may go.
     *
     * @param id a plugin id
     * @return whether {@code plugins/<id>/<id>.toml} is a regular file
     */
    public boolean settingsFileExists(String id) {
        return java.nio.file.Files.isRegularFile(PluginSettingsFiles.file(options.userDirectory(), id));
    }

    /** Forgets each reloaded file's problems, then lets its plugin see the values again. */
    private void applySettings(Set<String> reloaded) {
        for (String id : reloaded) reports.clear().accept(PluginSettingsFiles.file(options.userDirectory(), id));
        PluginHost current = host;
        if (current != null) current.settingsChanged(reloaded);
    }

    /** The settings poller reads files off the UI thread; reports always reach the application on it. */
    private static void onUi(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task);
    }

    /** Test seam: what the settings poller does, now. */
    void pollSettingsNow() {
        Set<String> changed = settingsFiles.refresh(false);
        if (!changed.isEmpty()) applySettings(changed);
    }

        /**
         * Seeds the settings file and creates the data directory of a plugin the launch found, if they are missing;
         * for the manager's openers. Off the EDT.
         *
         * @param id the plugin
         */
        public void prepareSettings(String id) throws IOException {
            PluginCandidate candidate = launched.get(id);
            if (candidate == null) throw new IOException("No such plugin: " + id);
            PluginSettingsFiles.prepare(options.userDirectory(), candidate, null);
        }

    /**
     * Settings pages for plugins: one for each running plugin with declared settings or a page of its own, and a stub
     * for each plugin the user meant to run that is not running. Sorted by title. UI thread.
     *
     * @param context what the pages may ask of Settings
     * @return the pages, each under {@link #TOOLS}
     */
    public List<SettingsPage> settingsPages(PageContext context) {
        PluginHost current = host;
        PluginCatalog.Launch started = launch;
        if (current == null || started == null) return List.of();
        return withStubs(current.settingsPages(context), started.statuses(),
            status -> new PluginStubPage(status.id(), status.name(), status.reason(), () -> openSettingsFile(status.id())));
    }

    /**
     * The running plugins' pages and a stub for each other plugin the user meant to run, sorted by title: at most one
     * page per plugin, and none for a plugin that runs, whatever became of its other copies.
     */
    static List<SettingsPage> withStubs(List<SettingsPage> running, List<PluginStatus> statuses,
                                        java.util.function.Function<PluginStatus, SettingsPage> stub) {
        var pages = new ArrayList<SettingsPage>(running);
        var taken = new java.util.HashSet<String>();
        for (SettingsPage page : running) taken.add(page.id());
        for (PluginStatus status : statuses)
            if (status.state() == PluginStatus.State.ACTIVE) taken.add(settingsPageId(status.id()));
        for (PluginStatus status : statuses)
            if (stubbed(status) && taken.add(settingsPageId(status.id()))) pages.add(stub.apply(status));
        pages.sort(java.util.Comparator.comparing(SettingsPage::title, String.CASE_INSENSITIVE_ORDER));
        return pages;
    }

    /**
     * Whether a running plugin has a page in Settings. UI thread.
     *
     * @param pluginId the plugin
     * @return true when it declared settings or gave a page of its own
     */
    public boolean hasSettingsPage(String pluginId) {
        PluginHost current = host;
        return current != null && current.hasSettingsPage(pluginId);
    }

    /**
     * Not running although the user meant it to: waiting for review, skipped, failed, or held back by safe mode. A copy
     * that lost to another copy of the same plugin is not the plugin the user meant to run.
     */
    static boolean stubbed(PluginStatus status) {
        if (status.superseded()) return false;
        return switch (status.state()) {
            case NEEDS_CONSENT, SKIPPED, FAILED -> true;
            case DISABLED -> status.reason().equals("safe mode");
            case ACTIVE -> false;
        };
    }

    /** Seeds and opens a plugin's settings file off the UI thread; a failure is a plugin problem. */
    private void openSettingsFile(String id) {
        Thread.ofVirtual().name("jasper-plugin-settings-open").start(() -> {
            try {
                prepareSettings(id);
                editor.accept(PluginSettingsFiles.file(options.userDirectory(), id));
            } catch (IOException | RuntimeException failure) {
                String name = Optional.ofNullable(launched.get(id)).map(candidate -> candidate.descriptor().name()).orElse(id);
                String reason = failure.getMessage() == null ? failure.toString() : failure.getMessage();
                notifications.problem().accept("Could not open a plugin's settings file", name + ": " + reason);
            }
        });
    }
}
