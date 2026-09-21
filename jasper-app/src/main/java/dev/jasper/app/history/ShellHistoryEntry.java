package dev.jasper.app.history;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/** One remembered command. {@code timestamp} is epoch seconds, 0 when unknown; directory and exit status come from live capture. */
public record ShellHistoryEntry(String command, long timestamp, Set<String> shells, Path directory, Integer exitStatus) {
    public ShellHistoryEntry {
        Objects.requireNonNull(command);
        if (command.isBlank()) throw new IllegalArgumentException("History entry needs a command");
        shells = Set.copyOf(shells);
    }

    public static ShellHistoryEntry of(String command, long timestamp, String shell) {
        return new ShellHistoryEntry(command, timestamp, Set.of(shell), null, null);
    }
}
