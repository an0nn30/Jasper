package dev.jasper.app.launch;

import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.app.config.ConfigSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Captures request settings before dispatch; every completion is delivered on the EDT. */
public final class ShellLauncher {
    private final Executor executor;
    private record Request(LaunchSettings preview, Supplier<LaunchSettings> resolve) { }
    private final Supplier<Request> settings;
    private final BiFunction<Path, LaunchSettings, TerminalSession> start;
    private final String label;

    public ShellLauncher(Executor executor, Function<Path, TerminalSession> start, String label) {
        this.executor = executor;
        this.settings = null;
        this.start = (directory, captured) -> start.apply(directory);
        this.label = label;
    }

    public ShellLauncher(Executor executor, Supplier<LaunchSettings> settings,
                  BiFunction<Path, LaunchSettings, TerminalSession> start) {
        this(executor, () -> {
            LaunchSettings captured = Objects.requireNonNull(settings.get());
            return new Request(captured, () -> captured);
        }, start, "shell");
    }

    private ShellLauncher(Executor executor, Supplier<Request> settings,
                          BiFunction<Path, LaunchSettings, TerminalSession> start, String label) {
        this.executor = executor;
        this.settings = settings;
        this.start = start;
        this.label = label;
    }

    /** Captures configuration on the EDT; resolves the current account shell on each launch worker. */
    public static ShellLauncher configured(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                           String osName, Map<String, String> inherited,
                                           int columns, int lines, Path integrationDir,
                                           BiFunction<Path, LaunchSettings, TerminalSession> start) {
        return configured(executor, snapshots, osName, inherited, columns, lines, integrationDir,
            start, DefaultShell::macAccountShell);
    }

    static ShellLauncher configured(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                    String osName, Map<String, String> inherited,
                                    int columns, int lines, Path integrationDir,
                                    BiFunction<Path, LaunchSettings, TerminalSession> start,
                                    Supplier<Optional<String>> accountShell) {
        Map<String, String> environment = Map.copyOf(inherited);
        return new ShellLauncher(executor, () -> {
            ConfigSnapshot snapshot = snapshots.get();
            LaunchSettings preview = LaunchSettings.resolve(snapshot, osName, environment, columns, lines, integrationDir);
            return new Request(preview, () -> LaunchSettings.resolve(snapshot, osName,
                DefaultShell.loginEnvironment(osName, environment, accountShell), columns, lines, integrationDir));
        }, start, "shell");
    }

    public String label() { return label; }

    /** The grid and scrollback a session starts with when no view has measured the pane yet. */
    public record SessionDefaults(int columns, int lines, int scrollback) { }

    /** From the current launch settings; a conventional terminal when there are none. EDT, like {@link #launch}. */
    public SessionDefaults sessionDefaults() {
        try {
            LaunchSettings captured = settings == null ? null : settings.get().preview();
            if (captured != null) return new SessionDefaults(captured.columns(), captured.lines(), captured.scrollback());
        } catch (RuntimeException unavailable) { /* fall through */ }
        return new SessionDefaults(80, 24, 10_000);
    }

    public String launch(Path directory, BiConsumer<TerminalSession, Throwable> completion) {
        return launch(directory, completion, ignored -> { });
    }

    /** Delivers the resolved shell label on the EDT immediately before completion. */
    public String launch(Path directory, BiConsumer<TerminalSession, Throwable> completion, Consumer<String> resolvedLabel) {
        final Request captured;
        final String capturedLabel;
        try {
            captured = settings == null ? null : Objects.requireNonNull(settings.get());
            capturedLabel = captured == null ? label : captured.preview().label();
        } catch (RuntimeException failure) {
            SwingUtilities.invokeLater(() -> completion.accept(null, failure));
            return label;
        }
        try {
            executor.execute(() -> {
                TerminalSession session = null;
                Throwable failure = null;
                String actualLabel = capturedLabel;
                try {
                    if (!Files.isDirectory(directory)) {
                        throw new IllegalArgumentException("Directory is unavailable: " + directory);
                    }
                    LaunchSettings resolved = captured == null ? null : captured.resolve().get();
                    if (resolved != null) actualLabel = resolved.label();
                    session = start.apply(directory, resolved);
                } catch (Exception e) { failure = e; }
                TerminalSession result = session;
                Throwable error = failure;
                String finalLabel = actualLabel;
                SwingUtilities.invokeLater(() -> {
                    resolvedLabel.accept(finalLabel);
                    completion.accept(result, error);
                });
            });
        } catch (RuntimeException failure) {
            SwingUtilities.invokeLater(() -> completion.accept(null, failure));
        }
        return capturedLabel;
    }
}
