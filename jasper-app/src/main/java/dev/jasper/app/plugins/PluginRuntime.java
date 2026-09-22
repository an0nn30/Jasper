package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.sdk.JasperSdk;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.events.AppEvents;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import dev.jasper.app.terminals.TerminalRegistry;

/**
 * The application's single entry to plugins: discover, resolve, load and start them once at launch,
 * forward configuration and theme changes, show their activities on Buddy, and stop them at shutdown.
 * Install, enable, disable and update take effect at the next start. EDT only, except {@link #executing}
 * and {@link #maintain}.
 */
public final class PluginRuntime {
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
     */
    public record Row(String id, String name, String version, String description, String vendor, String origin,
                      String state, String reason, List<String> capabilities, List<String> unconsented,
                      List<String> requires, int errors, boolean enabled, boolean needsConsent, boolean canToggle,
                      boolean canRemove, boolean pendingRemoval, boolean pendingInstall, String pending) {
        /** Copies the lists. */
        public Row {
            capabilities = List.copyOf(capabilities);
            unconsented = List.copyOf(unconsented);
            requires = List.copyOf(requires);
        }
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

    private static final System.Logger LOG = System.getLogger(PluginRuntime.class.getName());
    private static final Duration DRAIN_GRACE = Duration.ofMillis(1500);
    private static final Duration LOCK_WAIT = Duration.ofSeconds(2);

    private final Options options;
    private final ActivityNotifier notifier;
    private final BiConsumer<String, String> configReport;
    private final Contributions contributions;
    private final AuxiliaryWindows windows;
    private final TerminalRegistry terminals;
    private final Consumer<String> notice;
    private final Consumer<Path> editor;
    private volatile boolean dark = true;
    private final List<PluginStatus> statuses = new ArrayList<>();
    private final List<PluginClassLoader> loaders = new ArrayList<>();
    private volatile Map<String, Map<String, Object>> tables = Map.of();
    private volatile PluginHost host;
    private Subscription bridge;
    private volatile PluginCatalog.Launch launch;
    private PluginAdmin admin;
    private ExecutorService adminWorker;

    /**
     * Creates an idle runtime.
     *
     * @param options locations and mode
     * @param notifier receives plugin activities for Buddy
     * @param configReport receives plugin complaints about their settings as key and message
     * @param contributions the application-wide model that plugin chrome contributions are written to
     * @param windows builds plugin windows and dialogs
     * @param terminals the application-wide directory of terminal windows, tabs and panes
     */
    public PluginRuntime(Options options, ActivityNotifier notifier, BiConsumer<String, String> configReport,
                         Contributions contributions, AuxiliaryWindows windows, TerminalRegistry terminals) {
        this(options, notifier, configReport, contributions, windows, terminals,
            message -> LOG.log(System.Logger.Level.WARNING, "Plugin notice: " + message), new dev.jasper.app.platform.ConfigEditor()::open);
    }

    /**
     * As the six-argument constructor, plus where plugin notices and editor requests go.
     *
     * @param options locations and mode
     * @param notifier receives plugin activities for Buddy
     * @param configReport receives plugin complaints about their settings as key and message
     * @param contributions the application-wide model that plugin chrome contributions are written to
     * @param windows builds plugin windows and dialogs
     * @param terminals the application-wide directory of terminal windows, tabs and panes
     * @param notice shows a plugin's error notice to the user, on the UI thread
     * @param editor opens a file in the user's editor, off the UI thread; may throw
     */
    public PluginRuntime(Options options, ActivityNotifier notifier, BiConsumer<String, String> configReport,
                         Contributions contributions, AuxiliaryWindows windows, TerminalRegistry terminals,
                         Consumer<String> notice, Consumer<Path> editor) {
        this.notice = Objects.requireNonNull(notice);
        this.editor = Objects.requireNonNull(editor);
        this.options = Objects.requireNonNull(options);
        this.notifier = Objects.requireNonNull(notifier);
        this.configReport = Objects.requireNonNull(configReport);
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
     * @param pluginTables the {@code [plugins."<id>"]} tables of the current configuration
     * @param dark whether the current look is dark
     */
    public void start(Map<String, Map<String, Object>> pluginTables, boolean dark) {
        if (host != null) throw new IllegalStateException("Plugins already started");
        this.dark = dark;
        long began = System.nanoTime();
        tables = Map.copyOf(pluginTables);
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> candidates = new ArrayList<>(
            PluginDiscovery.scan(options.bundledDirectory(), PluginCandidate.Origin.BUNDLED, problems));
        candidates.addAll(PluginDiscovery.scan(options.userDirectory(), PluginCandidate.Origin.USER, problems));
        if (options.developmentDirectory() != null)
            PluginDiscovery.single(options.developmentDirectory(), PluginCandidate.Origin.DEV, problems).ifPresent(candidates::add);
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
            id -> tables.getOrDefault(id, Map.of()), configReport, DRAIN_GRACE, contributions, () -> this.dark, windows, terminals, notice, editor));
        host = created;
        bridge = created.bus.subscribe(EventBus.APP, Activities.TOPIC, this::forward);
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
     * Applies reloaded plugin tables, then announces the reload.
     *
     * @param pluginTables the new tables
     */
    public void configurationChanged(Map<String, Map<String, Object>> pluginTables) {
        PluginHost current = host;
        if (current == null) return;
        tables = Map.copyOf(pluginTables);
        current.settingsChanged();
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
        switch (event.state()) {
            case STARTED -> notifier.started(event.sourcePluginId(), event.id(), event.title(), detail);
            case PROGRESS -> notifier.progress(event.sourcePluginId(), event.id(), detail);
            case SUCCEEDED -> notifier.finished(event.sourcePluginId(), event.id(), event.title(), true, event.detail());
            case FAILED -> notifier.finished(event.sourcePluginId(), event.id(), event.title(), false, event.detail());
            case CANCELLED -> notifier.cancelled(event.sourcePluginId(), event.id());
        }
    }
}
