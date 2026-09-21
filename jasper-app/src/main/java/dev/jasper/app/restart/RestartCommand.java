package dev.jasper.app.restart;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The replacement's command line, derived from this process's own. Pure except {@link #current} and {@link #spawn}. */
public final class RestartCommand {
    private RestartCommand() { }

    /**
     * Plans the replacement from a command and its arguments as the operating system reports them.
     *
     * @param command the executable, when known
     * @param arguments everything after it, when known; for a Java launch that includes JVM options and the main class
     * @param mode which flags the replacement keeps
     * @return the full command line, or empty when it cannot be determined
     */
    public static Optional<List<String>> plan(Optional<String> command, Optional<String[]> arguments, RestartMode mode) {
        if (command.isEmpty() || command.get().isBlank() || arguments.isEmpty()) return Optional.empty();
        List<String> line = new ArrayList<>();
        line.add(command.get());
        for (String argument : arguments.get()) {
            // Jasper's flags are whole arguments, and no JVM option is spelled like one of them.
            if (argument.equals("--background")) continue;
            if (mode != RestartMode.SAME && argument.equals("--safe-mode")) continue;
            line.add(argument);
        }
        return Optional.of(mode == RestartMode.STANDALONE ? standalone(line) : List.copyOf(line));
    }

    /**
     * The replacement for the running process.
     *
     * @param mode which flags the replacement keeps
     * @return the full command line, or empty when the platform does not report it
     */
    public static Optional<List<String>> current(RestartMode mode) {
        ProcessHandle.Info info = ProcessHandle.current().info();
        return plan(info.command(), info.arguments(), mode);
    }

    /**
     * Makes a planned command line standalone.
     *
     * @param command a planned command line
     * @return the same line with {@code --standalone} exactly once
     */
    public static List<String> standalone(List<String> command) {
        if (command.contains("--standalone")) return List.copyOf(command);
        List<String> line = new ArrayList<>(command);
        line.add("--standalone");
        return List.copyOf(line);
    }

    /**
     * Starts the replacement, detached from this process's streams. Never the EDT.
     *
     * @param command a planned command line
     * @throws IOException when the process cannot be started
     */
    public static void spawn(List<String> command) throws IOException {
        new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
}
