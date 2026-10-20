package dev.jasper.app.plugins;

import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.presentation.AttemptStore;
import dev.jasper.app.presentation.Presentation;
import dev.jasper.app.presentation.PresentationAttempt;
import dev.jasper.app.presentation.Projections;
import dev.jasper.sdk.JasperSdk;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The bootstrap pre-pass (spec 4.8): plugins discovered once, every presentation that can be elected and the attempt record read,
 * resolved, and the winner's presentation built, before Swing and before the configuration is read. This one selection
 * feeds the configuration's kit defaults, the application and {@link PluginRuntime#start(PluginSelection, Map, boolean)},
 * so a {@code plugins.toml} edited during start-up cannot load a plugin set that disagrees with the chrome already
 * committed. Immutable; touches no AWT; any thread.
 */
public final class PluginSelection {
    private static final System.Logger LOG = System.getLogger(PluginSelection.class.getName());

    /**
     * A look the attempt record skipped at this start.
     *
     * @param name its provider's name
     * @param attempt the handle whose {@link PresentationAttempt#clear()} is Try Again
     */
    public record SkippedLook(String name, PresentationAttempt attempt) {
        /** Both parts are required. */
        public SkippedLook { Objects.requireNonNull(name); Objects.requireNonNull(attempt); }
    }

    private final PluginRuntime.Options options;
    private final String build;
    private final List<PluginCandidate> candidates;
    private final List<String> problems;
    private final PluginResolver.Resolution resolution;
    private final Presentation presentation;
    private final PresentationAttempt attempt;
    private final Optional<SkippedLook> skipped;
    private final Presentations presentations;
    private final AttemptStore store;
    private final dev.jasper.app.appearance.PluginThemes themes;

    private PluginSelection(PluginRuntime.Options options, String build, List<PluginCandidate> candidates, List<String> problems,
                            PluginResolver.Resolution resolution, Presentation presentation, PresentationAttempt attempt,
                            Optional<SkippedLook> skipped, Presentations presentations, AttemptStore store,
                            dev.jasper.app.appearance.PluginThemes themes) {
        this.presentations = presentations; this.store = store; this.themes = themes;
        this.options = options; this.build = build; this.candidates = List.copyOf(candidates); this.problems = List.copyOf(problems);
        this.resolution = resolution; this.presentation = presentation; this.attempt = attempt; this.skipped = skipped;
    }

    /**
     * The selection for this launch.
     *
     * @param dirs the home's folders
     * @param codeLocation the application's code location, which locates the bundled plugins; may be null
     * @param build the code location and its modification time, the identity the attempt record compares; may be empty
     * @param developmentDirectory the {@code --plugin-dir} folder, or null
     * @param safeMode whether this is a {@code --safe-mode} launch
     * @return the selection
     */
    public static PluginSelection of(AppDirs dirs, Path codeLocation, String build, Path developmentDirectory, boolean safeMode) {
        var options = new PluginRuntime.Options(PluginRuntime.bundledDirectory(codeLocation), dirs.plugins(), developmentDirectory, safeMode,
            dirs.pluginState(), dirs.pluginLock());
        return scan(options, build, Version.parse(JasperSdk.VERSION), host());
    }

    /** No plugins and the default look: for an application made without the bootstrap. */
    public static PluginSelection none() {
        return new PluginSelection(null, "", List.of(), List.of(), new PluginResolver.Resolution(List.of(), List.of()),
            Presentation.standard(host()), PresentationAttempt.NONE, Optional.empty(), Presentations.NONE, null,
            dev.jasper.app.appearance.PluginThemes.NONE);
    }

    static PluginSelection scan(PluginRuntime.Options options, String build, Version sdk, Projections.Host host) {
        Objects.requireNonNull(options, "options"); Objects.requireNonNull(build, "build");
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> candidates = new ArrayList<>(
            PluginDiscovery.scan(options.bundledDirectory(), PluginCandidate.Origin.BUNDLED, problems));
        candidates.addAll(PluginDiscovery.scan(options.userDirectory(), PluginCandidate.Origin.USER, problems));
        if (options.developmentDirectory() != null)
            PluginDiscovery.single(options.developmentDirectory(), PluginCandidate.Origin.DEV, problems).ifPresent(candidates::add);
        Map<String, PluginStateStore.Entry> state;
        try { state = new PluginStateStore(options.stateFile(), options.lockFile(), Duration.ofSeconds(2)).read(); }
        catch (IOException unreadable) {
            // Without readable consent, user plugins stay inert rather than running unreviewed.
            LOG.log(System.Logger.Level.WARNING, "Plugin state is unreadable; user plugins will need consent", unreadable);
            state = Map.of();
        }
        var store = new AttemptStore(options.stateFile().toAbsolutePath().getParent());
        // Only a provider that can reach the election has its files read; safe mode admits none (spec 4.5, Rulings 13).
        var attempts = store.read();
        var presentations = Presentations.read(PluginResolver.admitted(candidates, state, sdk, options.safeMode()), attempts, build);
        var resolution = PluginResolver.resolve(candidates, state, sdk, options.safeMode(), presentations);
        Presentation presentation = Presentation.standard(host);
        PresentationAttempt attempt = PresentationAttempt.NONE;
        if (resolution.presentation().isPresent()) {
            PluginCandidate winner = resolution.presentation().get();
            var facts = presentations.of(winner).orElseThrow();
            var descriptor = winner.descriptor();
            presentation = Presentation.of(new Presentation.Provider(winner.id(), descriptor.name(), descriptor.version().toString(), facts.digest()),
                facts.parsed().orElseThrow(), host);
            attempt = new PresentationAttempt(store, winner.id(), descriptor.version().toString(), facts.digest(), build);
        }
        Optional<SkippedLook> skipped = Optional.empty();
        for (PluginStatus status : resolution.rejected()) {
            if (status.state() != PluginStatus.State.SKIPPED || !status.reason().equals(PluginStatus.ATTEMPT_SKIP)) continue;
            for (PluginCandidate candidate : candidates)
                if (candidate.id().equals(status.id()) && candidate.origin() == status.origin())
                    skipped = Optional.of(new SkippedLook(candidate.descriptor().name(), new PresentationAttempt(store, candidate.id(),
                        candidate.descriptor().version().toString(), presentations.of(candidate).orElseThrow().digest(), build)));
        }
        return new PluginSelection(options, build, candidates, problems, resolution, presentation, attempt, skipped, presentations, store,
            themes(candidates, resolution));
    }

    /** The platform, and whether the unified macOS title bar is available, read without AWT. */
    static Projections.Host host() {
        var platform = Projections.Platform.of(System.getProperty("os.name", ""));
        return new Projections.Host(platform, platform == Projections.Platform.MAC && com.formdev.flatlaf.util.SystemInfo.isMacFullWindowContentSupported);
    }

    /** The presentation this launch runs. */
    public Presentation presentation() { return presentation; }

    /** The attempt record of that presentation's look; {@link PresentationAttempt#NONE} for the default look. */
    public PresentationAttempt attempt() { return attempt; }

    /** The look the attempt record skipped at this start, if one was. */
    public Optional<SkippedLook> skipped() { return skipped; }

    /** The build identity this selection compared the attempt record with. */
    public String build() { return build; }

    /**
     * Where plugins and their state live.
     *
     * @throws IllegalStateException for {@link #none()}
     */
    public PluginRuntime.Options options() {
        if (options == null) throw new IllegalStateException("No plugins were selected");
        return options;
    }

    /** Whether this is {@link #none()}. */
    public boolean isNone() { return options == null; }

    /** The names of the plugins this selection starts, in start order: the splash's plugin steps (spec 10.11). */
    public List<String> loading() { return resolution.load().stream().map(candidate -> candidate.descriptor().name()).toList(); }

    /**
     * Spec 4.7: this selection after the kit was blamed at start: the provider is skipped, so no service of it is
     * published, and the presentation is the fallback, which keeps the defaults read at start. The attempt is the same.
     */
    public PluginSelection blamed() {
        var provider = resolution.presentation();
        if (provider.isEmpty()) return this;
        return new PluginSelection(options, build, candidates, problems,
            PluginResolver.without(resolution, provider.get(), PluginStatus.LOOK_FAILED), presentation.fallback(host()), attempt, skipped,
            presentations, store, themes);
    }

    /**
     * Whether the invalid kit of {@code id} from {@code origin} is news at this start: its digest was not announced before.
     * Records it as announced. Spec 5.9's "once per digest".
     */
    boolean announceInvalid(String id, PluginCandidate.Origin origin) {
        if (store == null) return true;
        for (PluginCandidate candidate : candidates)
            if (candidate.id().equals(id) && candidate.origin() == origin)
                return presentations.of(candidate).map(facts -> PresentationAttempt.announce(store, id, facts.digest())).orElse(true);
        return true;
    }

    /** The plugin theme tier (spec 7.4): read once here and kept for the whole session, a blamed look's included. */
    public dev.jasper.app.appearance.PluginThemes themes() { return themes; }

    /** Every discovered plugin's declared theme files; only loadable plugins' files are read. */
    private static dev.jasper.app.appearance.PluginThemes themes(List<PluginCandidate> candidates, PluginResolver.Resolution resolution) {
        var declared = new ArrayList<dev.jasper.app.appearance.PluginThemes.Declared>();
        for (PluginCandidate candidate : candidates) {
            boolean loadable = resolution.load().stream().anyMatch(loaded -> loaded.id().equals(candidate.id()) && loaded.origin() == candidate.origin());
            var reader = PluginResources.of(candidate);
            var paths = candidate.descriptor().themes();
            var ids = candidate.descriptor().themeIds();
            int rank = switch (candidate.origin()) { case DEV -> 0; case BUNDLED -> 1; case USER -> 2; };
            for (int i = 0; i < paths.size(); i++)
                declared.add(new dev.jasper.app.appearance.PluginThemes.Declared(candidate.id(), candidate.descriptor().name(), rank, loadable,
                    ids.get(i), paths.get(i), candidate.directory(),
                    loadable ? reader.read(paths.get(i), dev.jasper.app.presentation.PresentationFacts.MAX_THEME_BYTES) : java.util.Optional.empty()));
        }
        return dev.jasper.app.appearance.PluginThemes.of(declared);
    }

    List<PluginCandidate> candidates() { return candidates; }
    List<String> problems() { return problems; }
    PluginResolver.Resolution resolution() { return resolution; }
}
