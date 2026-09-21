package dev.jasper.app.platform;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Resolves application locations without touching the filesystem. */
public record AppDirs(Path root, Path configFile, Path logs) {
    public Path commandHistory() {
        return root.resolve("command-history.toml");
    }

    public Path buddyState() {
        return root.resolve("buddy.toml");
    }

    public Path snippets() {
        return root.resolve("snippets.toml");
    }

    public Path shellIntegration() {
        return root.resolve("shell-integration");
    }

    /** User-installed plugins, one directory per plugin id. */
    public Path plugins() {
        return root.resolve("plugins");
    }

    /** Enabled flags, consented capabilities and pending removals. */
    public Path pluginState() {
        return root.resolve("plugins.toml");
    }

    /** Cross-process lock held around every change to {@link #pluginState()}. */
    public Path pluginLock() {
        return root.resolve("plugins.lock");
    }

    /** Parent of each plugin's private data directory. */
    public Path pluginData() {
        return root.resolve("plugin-data");
    }

    /**
     * The handoff endpoint's own directory. It is a subdirectory rather than {@link #root()}
     * because binding sets its parent to owner-only, and the root also holds the user's config.
     */
    Path daemonDir() {
        return root.resolve("daemon");
    }

    public Path daemonSocket() {
        return daemonDir().resolve("socket");
    }

    public Path daemonToken() {
        return daemonDir().resolve("token");
    }

    public Path daemonLock() {
        return daemonDir().resolve("lock");
    }

    public static AppDirs resolve(String osName, Map<String, String> env, Path home) {
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
        return new AppDirs(root, root.resolve("config.toml"), root.resolve("logs"));
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
