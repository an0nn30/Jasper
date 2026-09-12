package dev.moray.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class AppDirsTest {
    @TempDir Path home;

    @Test void macUsesHomeConfigRegardlessOfXdg() {
        var dirs = AppDirs.resolve("Mac OS X", Map.of("XDG_CONFIG_HOME", "/elsewhere"), home);
        assertThat(dirs.root()).isEqualTo(home.resolve(".config/moray"));
        assertThat(dirs.configFile()).isEqualTo(dirs.root().resolve("config.toml"));
        assertThat(dirs.themes()).isEqualTo(dirs.root().resolve("themes"));
        assertThat(dirs.logs()).isEqualTo(dirs.root().resolve("logs"));
        assertThat(dirs.root()).doesNotExist();
    }

    @Test void linuxUsesAbsoluteXdgAndIgnoresBlankOrRelativeRoots() {
        assertThat(AppDirs.resolve("Linux", Map.of("XDG_CONFIG_HOME", home.resolve("xdg/../settings").toString()), home).root())
            .isEqualTo(home.resolve("settings/moray"));
        for (var env : java.util.List.of(Map.<String,String>of(), Map.of("XDG_CONFIG_HOME", ""),
                Map.of("XDG_CONFIG_HOME", "relative"))) {
            assertThat(AppDirs.resolve("Linux", env, home).root()).isEqualTo(home.resolve(".config/moray"));
        }
        assertThat(home.resolve("settings")).doesNotExist();
    }

    @Test void windowsUsesAppDataWithHomeFallback() {
        assertThat(AppDirs.resolve("Windows 11", Map.of("APPDATA", home.resolve("roaming").toString()), home).root())
            .isEqualTo(home.resolve("roaming/moray"));
        for (var env : java.util.List.of(Map.<String,String>of(), Map.of("APPDATA", ""))) {
            assertThat(AppDirs.resolve("Windows 11", env, home).root())
                .isEqualTo(home.resolve("AppData/Roaming/moray"));
        }
        assertThat(home.resolve("AppData")).doesNotExist();
    }
}
