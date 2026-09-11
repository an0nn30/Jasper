package dev.moray.app;

import dev.moray.terminal.TerminalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/** The application-owned launch executor; every completion is delivered on the EDT. */
record ShellLauncher(Executor executor, Function<Path, TerminalSession> start, String label) {
    void launch(Path directory, BiConsumer<TerminalSession, Throwable> completion) {
        try {
            executor.execute(() -> {
                TerminalSession session = null;
                Throwable failure = null;
                try {
                    if (!Files.isDirectory(directory)) {
                        throw new IllegalArgumentException("Directory is unavailable: " + directory);
                    }
                    session = start.apply(directory);
                } catch (Exception e) { failure = e; }
                TerminalSession result = session;
                Throwable error = failure;
                SwingUtilities.invokeLater(() -> completion.accept(result, error));
            });
        } catch (RuntimeException e) {
            SwingUtilities.invokeLater(() -> completion.accept(null, e));
        }
    }
}
