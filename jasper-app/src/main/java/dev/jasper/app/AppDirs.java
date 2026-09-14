package dev.jasper.app;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Resolves application locations without touching the filesystem. */
record AppDirs(Path root, Path configFile, Path themes, Path logs) {
    Path commandHistory() {
        return root.resolve("command-history.toml");
    }

    Path buddyState() {
        return root.resolve("buddy.toml");
    }

    static AppDirs resolve(String osName, Map<String, String> env, Path home) {
        String os = osName.toLowerCase(Locale.ROOT);
        Path base;
        if (os.startsWith("windows")) {
            base = absoluteRoot(env.get("APPDATA"), home.resolve("AppData/Roaming"));
        } else if (os.contains("mac") || os.contains("darwin")) {
            base = home.resolve(".config");
        } else {
            base = absoluteRoot(env.get("XDG_CONFIG_HOME"), home.resolve(".config"));
        }
        Path root = base.resolve("jasper").toAbsolutePath().normalize();
        return new AppDirs(root, root.resolve("config.toml"), root.resolve("themes"), root.resolve("logs"));
    }

    private static Path absoluteRoot(String value, Path fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            Path path = Path.of(value);
            return path.isAbsolute() ? path : fallback;
        } catch (InvalidPathException ignored) {
            return fallback;
        }
    }
}
