package dev.jasper.terminal.session;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.internal.shell.DirectoryProvenance;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Immutable process inputs. Builders validate but perform no I/O.
 * Command, environment and working directory are explicit; no shell or environment is inferred.
 * @param command executable followed by literal arguments; copied, no shell parsing
 * @param environment complete copied child environment; Windows hidden names may begin with {@code =}; TERM and COLORTERM are forced at launch
 * @param workingDirectory child directory; existence is checked by process creation
 * @param grid initial terminal size in cells
 * @param scrollback history capacity from 0 through 1,000,000 lines
 * @param localHostNames names this machine is known by, for classifying reported working directories; resolve them off the EDT, empty when unknown
 * @param localPathMapper turns the path of a working-directory report classified as local into a directory on this machine;
 *        empty ignores the report. It runs on the session's reader thread and must not block
 */
public record SessionLaunchOptions(List<String> command, Map<String, String> environment,
        Path workingDirectory, GridSize grid, int scrollback, Set<String> localHostNames,
        Function<String, Optional<Path>> localPathMapper) {
    /**
     * The default local path mapping: the reported path read as a {@code file} URI path on this machine, so
     * {@code /home/me} becomes {@code \home\me} on Windows. Empty for a path that is not one.
     */
    public static final Function<String, Optional<Path>> DEFAULT_LOCAL_PATHS = DirectoryProvenance::fileUriPath;

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
        // Match the JDK's Windows environment rules: cmd.exe supplies hidden entries such as
        // =C: (the current directory on that drive). Only the leading '=' is permitted.
        int firstEqualsIndex = System.getProperty("os.name").startsWith("Windows") ? 1 : 0;
        environment.forEach((key, value) -> {
            if (key.isEmpty() || key.indexOf('=', firstEqualsIndex) >= 0 || key.indexOf('\0') >= 0
                    || value.indexOf('\0') >= 0)
                throw new IllegalArgumentException("invalid environment entry");
        });
        if (scrollback < 0 || scrollback > 1_000_000)
            throw new IllegalArgumentException("scrollback must be within 0–1000000");
        localHostNames = Set.copyOf(localHostNames);
        if (localHostNames.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("a local host name must not be blank");
        Objects.requireNonNull(localPathMapper, "localPathMapper");
    }
    /**
     * A launch description that reads local reports with {@link #DEFAULT_LOCAL_PATHS}.
     * @param command executable followed by literal arguments
     * @param environment complete child environment
     * @param workingDirectory child directory
     * @param grid initial terminal size in cells
     * @param scrollback history capacity
     * @param localHostNames names this machine is known by
     */
    public SessionLaunchOptions(List<String> command, Map<String, String> environment, Path workingDirectory,
            GridSize grid, int scrollback, Set<String> localHostNames) {
        this(command, environment, workingDirectory, grid, scrollback, localHostNames, DEFAULT_LOCAL_PATHS);
    }
    /** Starts a builder requiring an explicit command, environment, and working directory. */
    public static Builder builder() { return new Builder(); }
    /** Copies this launch description into an independent builder. */
    public Builder toBuilder() {
        return new Builder().command(command).environment(environment).workingDirectory(workingDirectory).grid(grid)
            .scrollback(scrollback).localHostNames(localHostNames).localPathMapper(localPathMapper);
    }
    /** Thread-confined builder with an 80 by 24 grid and 10,000 history lines. */
    public static final class Builder {
        private List<String> command;
        private Map<String,String> environment;
        private Path workingDirectory;
        private GridSize grid = new GridSize(80, 24);
        private int scrollback = 10_000;
        private Set<String> localHostNames = Set.of();
        private Function<String, Optional<Path>> localPathMapper = DEFAULT_LOCAL_PATHS;
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
        /** Names this machine is known by; a working directory reported under any other host is not local. */
        public Builder localHostNames(Set<String> v) { localHostNames = Set.copyOf(v); return this; }
        /** How a local working-directory report's path becomes a directory; empty ignores the report. */
        public Builder localPathMapper(Function<String, Optional<Path>> v) { localPathMapper = v; return this; }
        /** Validates and creates an immutable launch description without process I/O. */
        public SessionLaunchOptions build() {
            return new SessionLaunchOptions(command, environment, workingDirectory, grid, scrollback, localHostNames, localPathMapper);
        }
    }
}
