package dev.jasper.app.launch;

import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Captures request settings before dispatch; every completion is delivered on the EDT. */
public final class ShellLauncher {
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");
    private final Executor executor;
    private final Function<Optional<String>, LaunchSettings> settings;
    private final BiFunction<Optional<String>, LaunchSettings, LaunchSettings> confirm;
    private final Supplier<SessionDefaults> defaults;
    private final BiFunction<Path, LaunchSettings, TerminalSession> start;
    private final Function<UUID, Map<String, String>> variables;
    private final String label;

    /**
     * What a launch request started with.
     *
     * @param label the pane's shell label
     * @param profile the profile id the settings resolved to; empty when the launcher captures no settings
     */
    public record Launch(String label, Optional<String> profile) { }

    public ShellLauncher(Executor executor, Function<Path, TerminalSession> start, String label) {
        this.executor = executor;
        this.settings = null;
        this.confirm = (requested, captured) -> captured;
        this.defaults = null;
        this.start = (directory, captured) -> start.apply(directory);
        this.variables = pane -> Map.of();
        this.label = label;
    }

    /** Every request gets {@code settings}, whatever profile it asks for. */
    public ShellLauncher(Executor executor, Supplier<LaunchSettings> settings,
                  BiFunction<Path, LaunchSettings, TerminalSession> start) {
        this(executor, profileless(Objects.requireNonNull(settings)), start);
    }

    /** {@code settings} resolves a requested profile id (empty: the user's default) on the EDT when a launch is requested. */
    public ShellLauncher(Executor executor, Function<Optional<String>, LaunchSettings> settings,
                  BiFunction<Path, LaunchSettings, TerminalSession> start) {
        this(executor, settings, (requested, captured) -> captured, null, start);
    }

    /**
     * As above, and before starting, off the EDT on {@code executor}, {@code confirm} gets the requested id and the
     * captured settings and returns the settings to start (the same ones, or a replacement, for example once detection
     * shows a trusted profile is missing); it may block for a bounded time. {@code defaults} gives
     * {@link #sessionDefaults} on the EDT; null derives them from {@code settings}.
     */
    public ShellLauncher(Executor executor, Function<Optional<String>, LaunchSettings> settings,
                  BiFunction<Optional<String>, LaunchSettings, LaunchSettings> confirm, Supplier<SessionDefaults> defaults,
                  BiFunction<Path, LaunchSettings, TerminalSession> start) {
        this(executor, settings, confirm, defaults, start, pane -> Map.of());
    }

    /** As above; a pane's shell also gets {@code variables} for that pane, last, over everything else. */
    public ShellLauncher(Executor executor, Function<Optional<String>, LaunchSettings> settings,
                  BiFunction<Optional<String>, LaunchSettings, LaunchSettings> confirm, Supplier<SessionDefaults> defaults,
                  BiFunction<Path, LaunchSettings, TerminalSession> start, Function<UUID, Map<String, String>> variables) {
        this.executor = executor;
        this.settings = Objects.requireNonNull(settings);
        this.confirm = Objects.requireNonNull(confirm);
        this.defaults = defaults;
        this.start = start;
        this.variables = Objects.requireNonNull(variables);
        this.label = "shell";
    }

    private static Function<Optional<String>, LaunchSettings> profileless(Supplier<LaunchSettings> settings) {
        return requested -> settings.get();
    }

    public String label() { return label; }

    /** The grid and scrollback a session starts with when no view has measured the pane yet. */
    public record SessionDefaults(int columns, int lines, int scrollback) { }

    /** From the current launch settings; a conventional terminal when there are none. EDT, like {@link #launch}. */
    public SessionDefaults sessionDefaults() {
        try {
            if (defaults != null) return Objects.requireNonNull(defaults.get());
            LaunchSettings captured = settings == null ? null : settings.apply(Optional.empty());
            if (captured != null) return new SessionDefaults(captured.columns(), captured.lines(), captured.scrollback());
        } catch (RuntimeException unavailable) { /* fall through */ }
        return new SessionDefaults(80, 24, 10_000);
    }

    /** The user's default profile in {@code directory}; returns the pane's label. */
    public String launch(Path directory, BiConsumer<TerminalSession, Throwable> completion) {
        return launch(directory, Optional.empty(), completion).label();
    }

    /** Starts {@code profile} (empty: the user's default) in {@code directory}; settings are captured now, on the EDT. */
    public Launch launch(Path directory, Optional<String> profile, BiConsumer<TerminalSession, Throwable> completion) {
        return launch(Optional.of(directory), profile, replaced -> { }, completion);
    }

    /**
     * Starts {@code profile} (empty: the user's default) in {@code directory}, where empty means nobody asked for one:
     * the shell starts where a new shell starts (the user's home, which a WSL profile reads as its Linux home).
     * Settings are captured now, on the EDT. When confirmation replaces them, {@code replaced} hears what now starts,
     * on the EDT and before {@code completion}.
     */
    public Launch launch(Optional<Path> directory, Optional<String> profile, Consumer<Launch> replaced,
                         BiConsumer<TerminalSession, Throwable> completion) {
        return launch(directory, profile, Optional.empty(), replaced, completion);
    }

    /** As above for {@code pane}, whose shell also gets the pane's variables. */
    public Launch launch(Optional<Path> directory, Optional<String> profile, Optional<UUID> pane, Consumer<Launch> replaced,
                         BiConsumer<TerminalSession, Throwable> completion) {
        return launch(directory, profile, Optional.empty(), pane, replaced, completion);
    }

    /**
     * As above; a present {@code program} runs instead of the shell, with the profile's environment and the pane's
     * variables, in {@code directory} (empty: the user's home), which must exist; a WSL profile's {@code --cd} is not
     * added. The launch's label is the program's file name. A launcher without settings cannot run a program:
     * {@code completion} gets an {@link IllegalStateException}. On Windows a {@code directory} on another host counts as
     * empty, as {@link LaunchSettings#startable} describes, so nothing touches it.
     */
    public Launch launch(Optional<Path> directory, Optional<String> profile, Optional<List<String>> program, Optional<UUID> pane,
                         Consumer<Launch> replaced, BiConsumer<TerminalSession, Throwable> completion) {
        return launch(directory, false, profile, program, pane, replaced, completion);
    }

    /**
     * As above; with {@code anyDirectory}, a {@code directory} that is not there once the shell starts counts as empty, so
     * the shell starts where a new shell starts instead of failing: a restored terminal tab's (workspace tabs spec 13). A
     * WSL share is exempt: it is never probed here, and {@code LaunchSettings.at} decides where the profile's shell starts.
     */
    public Launch launch(Optional<Path> directory, boolean anyDirectory, Optional<String> profile, Optional<List<String>> program,
                         Optional<UUID> pane, Consumer<Launch> replaced, BiConsumer<TerminalSession, Throwable> completion) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(replaced, "replaced");
        if (program.isPresent() && settings == null) {
            SwingUtilities.invokeLater(() -> completion.accept(null, new IllegalStateException("This window cannot run a program")));
            return new Launch(label, Optional.empty());
        }
        final LaunchSettings captured;
        try {
            captured = settings == null ? null : Objects.requireNonNull(settings.apply(profile));
        } catch (RuntimeException failure) {
            SwingUtilities.invokeLater(() -> completion.accept(null, failure));
            return new Launch(label, Optional.empty());
        }
        Launch launched = captured == null ? new Launch(label, Optional.empty()) : launchOf(captured, program);
        Path home = Path.of(System.getProperty("user.home"));
        try {
            executor.execute(() -> {
                TerminalSession session = null;
                Throwable failure = null;
                try {
                    if (captured == null) {
                        Path target = directory.filter(path -> !WINDOWS || !UncHosts.namesForeignHost(path.toString(), false)).orElse(home);
                        if (anyDirectory && !Files.isDirectory(target)) target = home;
                        if (!Files.isDirectory(target)) {
                            throw new IllegalArgumentException("Directory is unavailable: " + target);
                        }
                        session = start.apply(target, null);
                    } else {
                        LaunchSettings chosen = Objects.requireNonNull(confirm.apply(profile, captured));
                        Launch now = launchOf(chosen, program);
                        if (!now.equals(launched)) SwingUtilities.invokeLater(() -> replaced.accept(now));
                        Optional<Path> requested = chosen.startable(directory, WINDOWS);
                        // A WSL share is never touched here: the profile's own start decides what to do with it.
                        if (anyDirectory) requested = requested.filter(path -> WslPaths.distroOf(path).isPresent() || Files.isDirectory(path));
                        Path where;
                        LaunchSettings resolved;
                        if (program.isPresent()) {
                            // Never at(): a WSL profile's --cd belongs to wsl.exe, not to the program.
                            where = requested.orElse(home);
                            if (!Files.isDirectory(where)) throw new IllegalArgumentException("Directory is unavailable: " + where);
                            resolved = chosen.withCommand(program.get());
                        } else {
                            LaunchSettings.Start target = chosen.at(requested, home, Files::isDirectory);
                            where = target.directory();
                            resolved = target.settings();
                        }
                        LaunchSettings started = pane.isEmpty() ? resolved : resolved.withVariables(variables.apply(pane.get()));
                        session = start.apply(where, started);
                    }
                } catch (Exception e) { failure = e; }
                TerminalSession result = session;
                Throwable error = failure;
                SwingUtilities.invokeLater(() -> completion.accept(result, error));
            });
        } catch (RuntimeException failure) {
            SwingUtilities.invokeLater(() -> completion.accept(null, failure));
        }
        return launched;
    }

    private static Launch launchOf(LaunchSettings settings, Optional<List<String>> program) {
        String name = program.map(command -> LaunchSettings.programName(command.getFirst())).orElseGet(settings::label);
        return new Launch(name, Optional.of(settings.profile().id()));
    }
}
