package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
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

/**
 * The application's single entry to plugins: discover, resolve, load and start them once at launch,
 * forward configuration and theme changes, show their activities on Buddy, and stop them at shutdown.
 * Install, enable, disable and update take effect at the next start. EDT only, except {@link #executing}.
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
     * @param dataRoot parent of each plugin's data directory
     */
    public record Options(Path bundledDirectory, Path userDirectory, Path developmentDirectory, boolean safeMode,
                          Path stateFile, Path lockFile, Path dataRoot) { }

    private static final System.Logger LOG = System.getLogger(PluginRuntime.class.getName());
    private static final Duration DRAIN_GRACE = Duration.ofMillis(1500);
    private static final Duration LOCK_WAIT = Duration.ofSeconds(2);

    private final Options options;
    private final ActivityNotifier notifier;
    private final BiConsumer<String, String> configReport;
    private final Contributions contributions;
    private volatile boolean dark = true;
    private final List<PluginStatus> statuses = new ArrayList<>();
    private final List<PluginClassLoader> loaders = new ArrayList<>();
    private volatile Map<String, Map<String, Object>> tables = Map.of();
    private volatile PluginHost host;
    private Subscription bridge;

    /**
     * Creates an idle runtime.
     *
     * @param options locations and mode
     * @param notifier receives plugin activities for Buddy
     * @param configReport receives plugin complaints about their settings as key and message
     * @param contributions the application-wide model that plugin chrome contributions are written to
     */
    public PluginRuntime(Options options, ActivityNotifier notifier, BiConsumer<String, String> configReport,
                         Contributions contributions) {
        this.options = Objects.requireNonNull(options);
        this.notifier = Objects.requireNonNull(notifier);
        this.configReport = Objects.requireNonNull(configReport);
        this.contributions = Objects.requireNonNull(contributions);
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
            SwingUtilities::isEventDispatchThread, id -> options.dataRoot().resolve(id),
            id -> tables.getOrDefault(id, Map.of()), configReport, DRAIN_GRACE, contributions, () -> this.dark));
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
