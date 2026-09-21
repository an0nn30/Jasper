package dev.jasper.app.history;

import java.nio.file.Path;
import java.util.List;

/** Package-local test access, excluded from production artifacts. */
public final class HistoryTestSupport {
    public static void stopPolling(ShellHistoryIndex index) { index.pollTimer().stop(); }
    public static List<String> readCommands(Path path) throws java.io.IOException { return CommandHistoryFile.read(path); }
}
