package dev.jasper.app.bootstrap;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Startup options parsed before any desktop initialization. */
record AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir,
                    boolean standalone, boolean homeOverride) {
    static final String USAGE = "Usage: jasper [--config <path>] [--background] [--safe-mode] "
        + "[--plugin-dir <path>] [--standalone] [--help]";

    /** The form that predates residency: an ordinary foreground launch. */
    AppArguments(Path configOverride, boolean help) {
        this(configOverride, help, false);
    }

    /** The form that predates plugins: every plugin option off. */
    AppArguments(Path configOverride, boolean help, boolean background) {
        this(configOverride, help, background, false, null, false);
    }

    /** The form that predates the home override: the OS-default home. */
    AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir, boolean standalone) {
        this(configOverride, help, background, safeMode, pluginDir, standalone, false);
    }

    /** Records whether the application home was overridden ({@code jasper.home} or {@code JASPER_HOME}). */
    AppArguments withHomeOverride(boolean value) {
        return new AppArguments(configOverride, help, background, safeMode, pluginDir, standalone, value);
    }

    /**
     * A standalone launch never hands off to a resident process, never binds the shared endpoint and
     * is never resident: it is a different Jasper (another home, config or plugin set) or a recovery
     * launch that must not reopen the process it is escaping.
     */
    boolean standaloneLaunch() {
        return configOverride != null || safeMode || pluginDir != null || standalone || homeOverride;
    }

    static AppArguments parse(String[] args, Path workingDirectory) {
        Path override = null;
        Path pluginDir = null;
        boolean help = false, background = false, safeMode = false, standalone = false;
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
                case "--safe-mode" -> {
                    if (safeMode) throw invalid("Duplicate --safe-mode option.");
                    safeMode = true;
                }
                case "--standalone" -> {
                    if (standalone) throw invalid("Duplicate --standalone option.");
                    standalone = true;
                }
                case "--config" -> {
                    if (override != null) throw invalid("Duplicate --config option.");
                    override = path(args, ++i, "--config", "a file path", workingDirectory);
                }
                case "--plugin-dir" -> {
                    if (pluginDir != null) throw invalid("Duplicate --plugin-dir option.");
                    pluginDir = path(args, ++i, "--plugin-dir", "a directory path", workingDirectory);
                }
                default -> throw invalid("Unknown argument.");
            }
        }
        return new AppArguments(override, help, background, safeMode, pluginDir, standalone);
    }

    private static Path path(String[] args, int index, String option, String what, Path workingDirectory) {
        if (index >= args.length || args[index].isBlank() || args[index].startsWith("--"))
            throw invalid(option + " requires " + what + ".");
        try { return workingDirectory.resolve(Path.of(args[index])).toAbsolutePath().normalize(); }
        catch (InvalidPathException ignored) { throw invalid(option + " requires a valid path."); }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message + "\n" + USAGE);
    }
}
