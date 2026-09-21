package dev.jasper.app.bootstrap;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Startup options parsed before any desktop initialization. */
record AppArguments(Path configOverride, boolean help, boolean background) {
    static final String USAGE = "Usage: jasper [--config <path>] [--background] [--help]";

    /** The form that predates residency: an ordinary foreground launch. */
    AppArguments(Path configOverride, boolean help) {
        this(configOverride, help, false);
    }

    static AppArguments parse(String[] args, Path workingDirectory) {
        Path override = null;
        boolean help = false;
        boolean background = false;
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
                case "--config" -> {
                    if (override != null) throw invalid("Duplicate --config option.");
                    if (++i == args.length || args[i].isBlank() || args[i].startsWith("--")) {
                        throw invalid("--config requires a file path.");
                    }
                    try {
                        override = workingDirectory.resolve(Path.of(args[i])).toAbsolutePath().normalize();
                    } catch (InvalidPathException ignored) {
                        throw invalid("--config requires a valid file path.");
                    }
                }
                default -> throw invalid("Unknown argument.");
            }
        }
        return new AppArguments(override, help, background);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message + "\n" + USAGE);
    }
}
