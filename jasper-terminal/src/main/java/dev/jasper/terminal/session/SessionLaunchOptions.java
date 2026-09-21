package dev.jasper.terminal.session;

import dev.jasper.terminal.config.GridSize;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable process inputs. Builders perform validation but never start a process. */
public record SessionLaunchOptions(List<String> command, Map<String, String> environment,
        Path workingDirectory, GridSize grid, int scrollback) {
    /** Copies collections and rejects invalid arguments before process creation. */
    public SessionLaunchOptions {
        command = List.copyOf(command);
        environment = Map.copyOf(environment);
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(grid, "grid");
        if (command.isEmpty() || command.getFirst().isBlank())
            throw new IllegalArgumentException("command must name an executable");
        if (command.stream().anyMatch(value -> value.indexOf('\0') >= 0))
            throw new IllegalArgumentException("command must contain no NUL");
        environment.forEach((key, value) -> {
            if (key.isEmpty() || key.indexOf('=') >= 0 || key.indexOf('\0') >= 0
                    || value.indexOf('\0') >= 0)
                throw new IllegalArgumentException("invalid environment entry");
        });
        if (scrollback < 0 || scrollback > 1_000_000)
            throw new IllegalArgumentException("scrollback must be within 0–1000000");
    }
    /** Starts a builder requiring an explicit command, environment, and working directory. */
    public static Builder builder() { return new Builder(); }
    /** Copies this launch description into an independent builder. */
    public Builder toBuilder() {
        return new Builder().command(command).environment(environment)
            .workingDirectory(workingDirectory).grid(grid).scrollback(scrollback);
    }
    /** Thread-confined builder with an 80 by 24 grid and 10,000 history lines. */
    public static final class Builder {
        private List<String> command;
        private Map<String,String> environment;
        private Path workingDirectory;
        private GridSize grid = new GridSize(80, 24);
        private int scrollback = 10_000;
        private Builder() { }
        /** Copies the executable and arguments; no shell parsing is performed. */
        public Builder command(List<String> v) { command = List.copyOf(v); return this; }
        /** Copies the complete child environment; terminal capability variables are enforced at launch. */
        public Builder environment(Map<String,String> v) { environment = Map.copyOf(v); return this; }
        /** Sets the child working directory without checking the filesystem. */
        public Builder workingDirectory(Path v) { workingDirectory = v; return this; }
        /** Sets the initial terminal dimensions in cells. */
        public Builder grid(GridSize v) { grid = v; return this; }
        /** Sets retained history capacity, from zero through 1,000,000 lines. */
        public Builder scrollback(int v) { scrollback = v; return this; }
        /** Validates and creates an immutable launch description without process I/O. */
        public SessionLaunchOptions build() {
            return new SessionLaunchOptions(command, environment, workingDirectory, grid, scrollback);
        }
    }
}
