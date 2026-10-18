package dev.jasper.app.restart;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The replacement's command line, derived from this process's own. Pure except {@link #current} and {@link #spawn}. */
public final class RestartCommand {
    private RestartCommand() { }

    /**
     * Plans the replacement from a command and its arguments as the operating system reports them. When they end with
     * {@code launch}, the arguments Jasper's {@code main} received, those are replayed: {@code --config} and
     * {@code --plugin-dir} with their values, and {@code --safe-mode} when {@code mode} keeps it; {@code --background},
     * {@code --no-window}, an earlier {@code --replacing} and the launch's paths are dropped. Otherwise (a platform that
     * reports something else) the flags are dropped by their spelling alone, since nothing there tells a path from a JVM
     * option.
     *
     * @param command the executable, when known
     * @param arguments everything after it, when known; for a Java launch that includes JVM options and the main class
     * @param launch the arguments this process's {@code main} received
     * @param mode which flags the replacement keeps
     * @return the full command line, or empty when it cannot be determined
     */
    public static Optional<List<String>> plan(Optional<String> command, Optional<String[]> arguments, List<String> launch,
                                              RestartMode mode) {
        if (command.isEmpty() || command.get().isBlank() || arguments.isEmpty()) return Optional.empty();
        List<String> reported = List.of(arguments.get());
        int split = reported.size() - launch.size();
        boolean tail = !launch.isEmpty() && split >= 0 && reported.subList(split, reported.size()).equals(launch);
        List<String> line = new ArrayList<>();
        line.add(command.get());
        if (tail) {
            line.addAll(reported.subList(0, split));
            line.addAll(kept(launch, mode, true));
        } else {
            line.addAll(kept(reported, mode, false));
        }
        return Optional.of(List.copyOf(line));
    }

    /** {@code arguments} without what a replacement must not repeat; {@code own}: they are all Jasper's, so the rest are paths. */
    private static List<String> kept(List<String> arguments, RestartMode mode, boolean own) {
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            switch (argument) {
                case "--config", "--plugin-dir" -> {
                    kept.add(argument);
                    if (i + 1 < arguments.size()) kept.add(arguments.get(++i));
                }
                case "--replacing" -> i++;
                case "--background", "--no-window", "--help" -> { }
                case "--safe-mode" -> {
                    if (mode == RestartMode.SAME) kept.add(argument);
                }
                default -> {
                    if (!own) kept.add(argument);
                }
            }
        }
        return kept;
    }

    /**
     * The replacement for the running process.
     *
     * @param mode which flags the replacement keeps
     * @param launch the arguments this process's {@code main} received
     * @return the full command line, or empty when the platform does not report it
     */
    public static Optional<List<String>> current(RestartMode mode, List<String> launch) {
        ProcessHandle.Info info = ProcessHandle.current().info();
        return plan(info.command(), info.arguments(), launch, mode);
    }

    /**
     * A planned command line as the replacement for process {@code pid} (workspace-tabs spec 5.3): it waits for that
     * process's instance lock, then opens {@code paths}.
     *
     * @param planned a planned command line
     * @param pid the process it replaces
     * @param paths absolute paths the replacement opens
     * @return the command line to start
     */
    public static List<String> replacing(List<String> planned, long pid, List<Path> paths) {
        List<String> line = new ArrayList<>(planned);
        line.add("--replacing");
        line.add(Long.toString(pid));
        for (Path path : paths) line.add(path.toString());
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
