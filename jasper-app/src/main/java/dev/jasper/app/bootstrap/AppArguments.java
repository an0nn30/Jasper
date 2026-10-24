package dev.jasper.app.bootstrap;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * Startup options parsed before any desktop initialization. An argument that does not start with {@code --} is a path
 * to open (workspace-tabs spec 5.4), resolved against the working directory.
 *
 * @param configOverride {@code --config}, or null
 * @param help {@code --help}
 * @param background {@code --background}: the login item's windowless start
 * @param safeMode {@code --safe-mode}
 * @param pluginDir {@code --plugin-dir}, or null
 * @param homeOverride whether the home came from {@code jasper.home} or {@code JASPER_HOME}
 * @param noWindow {@code --no-window}: the {@code jasper} command's windowless start
 * @param replacing {@code --replacing}: the process a restart's replacement waits for; not in the usage
 * @param workingDirectory the directory the launch ran in
 * @param paths the paths to open, absolute and normalized, in order
 * @param given the arguments as {@code main} received them, which a restart replays
 */
record AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir,
                    boolean homeOverride, boolean noWindow, OptionalLong replacing, Path workingDirectory,
                    List<Path> paths, List<String> given) {
    static final String USAGE = "Usage: jasper [--config <path>] [--background] [--no-window] [--safe-mode] "
        + "[--plugin-dir <path>] [--help] [<path>...]";

    AppArguments {
        Objects.requireNonNull(replacing, "replacing");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        paths = List.copyOf(paths);
        given = List.copyOf(given);
    }

    /** Records whether the application home was overridden ({@code jasper.home} or {@code JASPER_HOME}). */
    AppArguments withHomeOverride(boolean value) {
        return new AppArguments(configOverride, help, background, safeMode, pluginDir, value, noWindow, replacing,
            workingDirectory, paths, given);
    }

    /**
     * The start-up flags this launch carries: what a second launch reports to the owner as ignored (spec 5.2).
     *
     * @return {@code --safe-mode}, {@code --config} and {@code --plugin-dir}, those present, in that order
     */
    List<String> startupFlags() {
        return Stream.of(safeMode ? "--safe-mode" : null, configOverride != null ? "--config" : null,
            pluginDir != null ? "--plugin-dir" : null).filter(Objects::nonNull).toList();
    }

    /** Residency applies only to the default home launched without start-up flags (spec 5.2). */
    boolean residencyAllowed() {
        return !homeOverride && startupFlags().isEmpty();
    }

    static AppArguments parse(String[] args, Path workingDirectory) {
        Path override = null;
        Path pluginDir = null;
        boolean help = false, background = false, safeMode = false, noWindow = false;
        OptionalLong replacing = OptionalLong.empty();
        Stream.Builder<Path> paths = Stream.builder();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help" -> {
                    if (help) throw invalid("Duplicate --help option.");
                    help = true;
                }
                case "--background" -> {
                    if (background) throw invalid("Duplicate --background option.");
                    background = true;
                }
                case "--no-window" -> {
                    if (noWindow) throw invalid("Duplicate --no-window option.");
                    noWindow = true;
                }
                case "--safe-mode" -> {
                    if (safeMode) throw invalid("Duplicate --safe-mode option.");
                    safeMode = true;
                }
                case "--config" -> {
                    if (override != null) throw invalid("Duplicate --config option.");
                    override = path(args, ++i, "--config", "a file path", workingDirectory);
                }
                case "--plugin-dir" -> {
                    if (pluginDir != null) throw invalid("Duplicate --plugin-dir option.");
                    pluginDir = path(args, ++i, "--plugin-dir", "a directory path", workingDirectory);
                }
                case "--replacing" -> {
                    if (replacing.isPresent()) throw invalid("Duplicate --replacing option.");
                    replacing = OptionalLong.of(processId(args, ++i));
                }
                default -> {
                    if (args[i].startsWith("--")) throw invalid("Unknown argument.");
                    paths.add(positional(args[i], workingDirectory));
                }
            }
        }
        if (noWindow && background) throw invalid("--no-window and --background cannot be combined.");
        return new AppArguments(override, help, background, safeMode, pluginDir, false, noWindow, replacing,
            workingDirectory, paths.build().toList(), List.of(args));
    }

    private static Path path(String[] args, int index, String option, String what, Path workingDirectory) {
        if (index >= args.length || args[index].isBlank() || args[index].startsWith("--"))
            throw invalid(option + " requires " + what + ".");
        try { return workingDirectory.resolve(Path.of(args[index])).toAbsolutePath().normalize(); }
        catch (InvalidPathException ignored) { throw invalid(option + " requires a valid path."); }
    }

    /** A path to open, as the launch names it (spec 5.4). */
    private static Path positional(String text, Path workingDirectory) {
        if (text.isBlank()) throw invalid("A path cannot be blank.");
        try { return workingDirectory.resolve(text).toAbsolutePath().normalize(); }
        catch (InvalidPathException ignored) { throw invalid("Invalid path: " + text); }
    }

    private static long processId(String[] args, int index) {
        if (index < args.length) {
            try {
                long pid = Long.parseLong(args[index]);
                if (pid > 0) return pid;
            } catch (NumberFormatException ignored) {
                // Reported below.
            }
        }
        throw invalid("--replacing requires a process id.");
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message + "\n" + USAGE);
    }
}
