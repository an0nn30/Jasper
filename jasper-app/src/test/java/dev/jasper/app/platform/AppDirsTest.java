package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class AppDirsTest {
    @TempDir Path home;

    @Test void macUsesHomeConfigRegardlessOfXdg() {
        var dirs = AppDirs.resolve("Mac OS X", Map.of("XDG_CONFIG_HOME", "/elsewhere"), home);
        assertThat(dirs.root()).isEqualTo(home.resolve(".config/jasper"));
        assertThat(dirs.configFile()).isEqualTo(dirs.root().resolve("config.toml"));
        assertThat(dirs.logs()).isEqualTo(dirs.root().resolve("logs"));
        assertThat(dirs.commandHistory()).isEqualTo(dirs.root().resolve("command-history.toml"));
        assertThat(dirs.root()).doesNotExist();
    }

    @Test void linuxUsesAbsoluteXdgAndIgnoresBlankOrRelativeRoots() {
        var configured = AppDirs.resolve("Linux",
            Map.of("XDG_CONFIG_HOME", home.resolve("xdg/../settings").toString()), home);
        assertThat(configured.root()).isEqualTo(home.resolve("settings/jasper"));
        assertThat(configured.commandHistory())
            .isEqualTo(home.resolve("settings/jasper/command-history.toml"));
        for (var env : java.util.List.of(Map.<String,String>of(), Map.of("XDG_CONFIG_HOME", ""),
                Map.of("XDG_CONFIG_HOME", "relative"))) {
            assertThat(AppDirs.resolve("Linux", env, home).root()).isEqualTo(home.resolve(".config/jasper"));
        }
        assertThat(home.resolve("settings")).doesNotExist();
    }

    @Test void windowsUsesAppDataWithHomeFallback() {
        var configured = AppDirs.resolve("Windows 11",
            Map.of("APPDATA", home.resolve("roaming").toString()), home);
        assertThat(configured.root()).isEqualTo(home.resolve("roaming/jasper"));
        assertThat(configured.commandHistory())
            .isEqualTo(home.resolve("roaming/jasper/command-history.toml"));
        for (var env : java.util.List.of(Map.<String,String>of(), Map.of("APPDATA", ""))) {
            assertThat(AppDirs.resolve("Windows 11", env, home).root())
                .isEqualTo(home.resolve("AppData/Roaming/jasper"));
        }
        assertThat(home.resolve("AppData")).doesNotExist();
    }

    @Test void commandHistoryUsesRootWhenConfigFileIsOverridden() {
        Path root = home.resolve("root");
        var dirs = new AppDirs(root, home.resolve("elsewhere/custom.toml"), root.resolve("logs"));

        assertThat(dirs.commandHistory()).isEqualTo(root.resolve("command-history.toml"));
    }

    @Test void buddyStateLivesBesideCommandHistory() {
        AppDirs dirs = AppDirs.resolve("Mac OS X", Map.of(), home);
        assertThat(dirs.buddyState())
            .isEqualTo(home.resolve(".config/jasper/buddy.toml"));
    }

    @Test void daemonFilesShareOwnTheirOwnSubdirectoryOfTheRoot() {
        AppDirs dirs = AppDirs.resolve("Mac OS X", Map.of(), home);
        Path daemon = home.resolve(".config/jasper/daemon");
        // Its own directory, not the root: the root also holds config.toml and the endpoint
        // needs its parent to be owner-only.
        assertThat(dirs.daemonDir()).isEqualTo(daemon);
        assertThat(dirs.daemonSocket()).isEqualTo(daemon.resolve("socket"));
        assertThat(dirs.daemonToken()).isEqualTo(daemon.resolve("token"));
        assertThat(dirs.daemonLock()).isEqualTo(daemon.resolve("lock"));
        assertThat(daemon).doesNotExist();
    }


    @Test void theCommandSocketAndTheSubcommandListLiveInTheDaemonFolder(@TempDir Path home) {
        AppDirs dirs = AppDirs.resolve("Mac OS X", Map.of(), home);
        Path daemon = dirs.daemonSocket().getParent();
        assertThat(dirs.commandSocket()).isEqualTo(daemon.resolve("commands"));
        assertThat(dirs.commandSocket(42)).isEqualTo(daemon.resolve("commands-42"));
        assertThat(dirs.subcommandList()).isEqualTo(daemon.resolve("subcommands"));
    }

    /** The native client (packaging/cli) resolves the same socket from the same inputs. */
    @Test void theCommandSocketMatchesTheNativeClientsVectors() throws Exception {
        boolean windowsHost = System.getProperty("os.name").startsWith("Windows");
        Path vectors = Path.of(System.getProperty("jasper.projectDir"), "packaging", "cli", "test", "home-vectors.txt");
        int checked = 0;
        for (String line : Files.readAllLines(vectors, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] field = line.split("\\|", -1);
            assertThat(field).as(line).hasSize(8);
            // JASPER_COMMAND_SOCKET is the client's own shortcut, and a host resolves only its own family's paths.
            if (!field[1].equals("-") || field[0].equals("windows") != windowsHost) continue;
            var env = new HashMap<String, String>();
            vector(env, "JASPER_HOME", field[2]);
            vector(env, "APPDATA", field[3]);
            vector(env, "XDG_CONFIG_HOME", field[4]);
            String os = switch (field[0]) {
                case "mac" -> "Mac OS X";
                case "linux" -> "Linux";
                default -> "Windows 11";
            };
            Path socket = AppDirs.resolve(os, env, Path.of(field[5])).commandSocket();
            assertThat(socket.toString().replace('\\', '/')).as(line).isEqualTo(field[7]);
            checked++;
        }
        assertThat(checked).isPositive();
    }

    private static void vector(Map<String, String> env, String name, String value) {
        if (!value.equals("-")) env.put(name, value.equals("_") ? " " : value);
    }

    @Test void aHomeOverrideMovesTheWholeRootPropertyFirstThenEnvironmentThenTheOsDefault() {
        var byProperty = AppDirs.resolve("Mac OS X", Map.of("JASPER_HOME", home.resolve("env-home").toString()), home,
            home.resolve("prop-home").toString());
        assertThat(byProperty.root()).isEqualTo(home.resolve("prop-home"));
        assertThat(byProperty.overridden()).isTrue();
        assertThat(byProperty.configFile()).isEqualTo(home.resolve("prop-home/config.toml"));
        assertThat(byProperty.plugins()).isEqualTo(home.resolve("prop-home/plugins"));
        assertThat(byProperty.themes()).isEqualTo(home.resolve("prop-home/themes"));
        assertThat(byProperty.pluginState()).isEqualTo(home.resolve("prop-home/plugins.toml"));
        assertThat(byProperty.daemonSocket()).isEqualTo(home.resolve("prop-home/daemon/socket"));
        assertThat(byProperty.logs()).isEqualTo(home.resolve("prop-home/logs"));

        var byEnvironment = AppDirs.resolve("Mac OS X", Map.of("JASPER_HOME", home.resolve("env-home").toString()), home, " ");
        assertThat(byEnvironment.root()).isEqualTo(home.resolve("env-home"));
        assertThat(byEnvironment.overridden()).isTrue();

        var plain = AppDirs.resolve("Mac OS X", Map.of("JASPER_HOME", ""), home, null);
        assertThat(plain.root()).isEqualTo(home.resolve(".config/jasper"));
        assertThat(plain.overridden()).isFalse();
        assertThat(AppDirs.resolve("Mac OS X", Map.of(), home).overridden()).isFalse();

        var relative = AppDirs.resolve("Linux", Map.of(), home, "build/dev-home");
        assertThat(relative.root()).as("a relative override resolves from the working directory").isAbsolute()
            .isEqualTo(Path.of("build/dev-home").toAbsolutePath().normalize());
    }

    @Test void theNotificationIconLivesInTheRoot() {
        AppDirs dirs = AppDirs.resolve("Linux", Map.of(), home);
        assertThat(dirs.notificationIcon()).isEqualTo(dirs.root().resolve("notification-icon.png"));
    }
}
