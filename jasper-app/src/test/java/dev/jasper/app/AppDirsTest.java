package dev.jasper.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class AppDirsTest {
    @TempDir Path home;

    @Test void macUsesHomeConfigRegardlessOfXdg() {
        var dirs = AppDirs.resolve("Mac OS X", Map.of("XDG_CONFIG_HOME", "/elsewhere"), home);
        assertThat(dirs.root()).isEqualTo(home.resolve(".config/jasper"));
        assertThat(dirs.configFile()).isEqualTo(dirs.root().resolve("config.toml"));
        assertThat(dirs.themes()).isEqualTo(dirs.root().resolve("themes"));
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
        var dirs = new AppDirs(root, home.resolve("elsewhere/custom.toml"),
            root.resolve("themes"), root.resolve("logs"));

        assertThat(dirs.commandHistory()).isEqualTo(root.resolve("command-history.toml"));
    }

    @Test void buddyStateLivesBesideCommandHistory() {
        AppDirs dirs = AppDirs.resolve("Mac OS X", Map.of(), Path.of("/Users/example"));
        assertThat(dirs.buddyState())
            .isEqualTo(Path.of("/Users/example/.config/jasper/buddy.toml"));
    }
}
