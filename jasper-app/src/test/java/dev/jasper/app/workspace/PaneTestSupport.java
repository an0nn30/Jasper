package dev.jasper.app.workspace;

import dev.jasper.app.launch.ShellLauncher;
import java.nio.file.Path;

/** Package-local test access, excluded from production artifacts. */
public final class PaneTestSupport {
    public static TerminalPane start(Path path, ShellLauncher launcher, Runnable ready) {
        var pane = new TerminalPane(path, launcher); pane.onReady = view -> ready.run(); pane.start(); return pane;
    }
}
