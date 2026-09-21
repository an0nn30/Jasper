package dev.jasper.app;

import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Captures request settings before dispatch; every completion is delivered on the EDT. */
final class ShellLauncher {
    private final Executor executor;
    private final Supplier<LaunchSettings> settings;
    private final BiFunction<Path, LaunchSettings, TerminalSession> start;
    private final String label;

    ShellLauncher(Executor executor, Function<Path, TerminalSession> start, String label) {
        this.executor = executor;
        this.settings = null;
        this.start = (directory, captured) -> start.apply(directory);
        this.label = label;
    }

    ShellLauncher(Executor executor, Supplier<LaunchSettings> settings,
                  BiFunction<Path, LaunchSettings, TerminalSession> start) {
        this.executor = executor;
        this.settings = Objects.requireNonNull(settings);
        this.start = start;
        this.label = "shell";
    }

    String label() { return label; }

    String launch(Path directory, BiConsumer<TerminalSession, Throwable> completion) {
        final LaunchSettings captured;
        final String capturedLabel;
        try {
            captured = settings == null ? null : Objects.requireNonNull(settings.get());
            capturedLabel = captured == null ? label : captured.label();
        } catch (RuntimeException failure) {
            SwingUtilities.invokeLater(() -> completion.accept(null, failure));
            return label;
        }
        try {
            executor.execute(() -> {
                TerminalSession session = null;
                Throwable failure = null;
                try {
                    if (!Files.isDirectory(directory)) {
                        throw new IllegalArgumentException("Directory is unavailable: " + directory);
                    }
                    session = start.apply(directory, captured);
                } catch (Exception e) { failure = e; }
                TerminalSession result = session;
                Throwable error = failure;
                SwingUtilities.invokeLater(() -> completion.accept(result, error));
            });
        } catch (RuntimeException failure) {
            SwingUtilities.invokeLater(() -> completion.accept(null, failure));
        }
        return capturedLabel;
    }
}
