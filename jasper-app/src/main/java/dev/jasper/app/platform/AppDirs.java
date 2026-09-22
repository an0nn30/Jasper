package dev.jasper.app.platform;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves application locations without touching the filesystem. {@code overridden} is true when the
 * root came from the {@code jasper.home} system property or the {@code JASPER_HOME} variable rather
 * than the OS default: a development or test home that must stay apart from the installed app's.
 */
public record AppDirs(Path root, Path configFile, Path logs, boolean overridden) {
    /** The OS-default form. */
    public AppDirs(Path root, Path configFile, Path logs) { this(root, configFile, logs, false); }

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

    /** Application-owned layout state: panel placement, rail visibility and auxiliary window bounds. */
    public Path uiState() {
        return root.resolve("ui-state.toml");
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

    /** As {@link #resolve(String, Map, Path, String)} with only the environment override. */
    public static AppDirs resolve(String osName, Map<String, String> env, Path home) {
        return resolve(osName, env, home, null);
    }

    /**
     * The root is {@code homeProperty} ({@code jasper.home}) when set, else {@code JASPER_HOME} from the
     * environment, else the OS default under the user's configuration directory. A relative override
     * resolves from the working directory; a blank one is ignored.
     */
    public static AppDirs resolve(String osName, Map<String, String> env, Path home, String homeProperty) {
        for (String override : new String[]{homeProperty, env.get("JASPER_HOME")}) {
            if (override == null || override.isBlank()) continue;
            try {
                Path root = Path.of(override.strip()).toAbsolutePath().normalize();
                return new AppDirs(root, root.resolve("config.toml"), root.resolve("logs"), true);
            } catch (InvalidPathException ignored) {
                // An unusable override falls through to the next source, as an unusable XDG root does.
            }
        }
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
