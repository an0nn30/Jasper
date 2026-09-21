package dev.jasper.app.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class AppArgumentsTest {
    @TempDir Path cwd;

    @Test void absentOverrideAndHelpAreParsedWithoutCreatingFiles() {
        assertThat(AppArguments.parse(new String[0], cwd)).isEqualTo(new AppArguments(null, false));
        assertThat(AppArguments.parse(new String[]{"--help"}, cwd).help()).isTrue();
        assertThat(cwd.resolve("config.toml")).doesNotExist();
    }

    @Test void relativeAndAbsoluteOverridesNormalizeAgainstWorkingDirectory() {
        assertThat(AppArguments.parse(new String[]{"--config", "nested/../config.toml"}, cwd).configOverride())
            .isEqualTo(cwd.resolve("config.toml"));
        assertThat(AppArguments.parse(new String[]{"--config", cwd.resolve("a/../other.toml").toString(), "--help"}, cwd))
            .isEqualTo(new AppArguments(cwd.resolve("other.toml"), true));
        assertThat(cwd.resolve("other.toml")).doesNotExist();
    }

    @Test void invalidMissingAndDuplicateArgumentsFailClearlyEvenWithHelp() {
        for (String[] args : new String[][]{{"--wat"}, {"file.toml"}, {"--config"}, {"--config", "--help"},
                {"--config", ""}, {"--config", "a", "--config", "b"}, {"--help", "--help"}, {"--help", "--wat"}}) {
            assertThatIllegalArgumentException().as(java.util.Arrays.toString(args))
                .isThrownBy(() -> AppArguments.parse(args, cwd)).withMessageContaining("Usage:");
        }
    }

    @Test void backgroundIsOffByDefaultAndParsesOnceAlongsideOtherOptions() {
        assertThat(AppArguments.parse(new String[0], cwd).background()).isFalse();
        assertThat(AppArguments.parse(new String[]{"--background"}, cwd))
            .isEqualTo(new AppArguments(null, false, true));
        assertThat(AppArguments.parse(new String[]{"--config", "c.toml", "--background"}, cwd))
            .isEqualTo(new AppArguments(cwd.resolve("c.toml"), false, true));
        for (String[] args : new String[][]{{"--background", "--background"}, {"--background", "--wat"}}) {
            assertThatIllegalArgumentException().as(java.util.Arrays.toString(args))
                .isThrownBy(() -> AppArguments.parse(args, cwd)).withMessageContaining("Usage:");
        }
        assertThat(AppArguments.USAGE).contains("--background");
    }

    @Test void pluginFlagsParseOnceAndMakeTheLaunchStandalone() {
        AppArguments plain = AppArguments.parse(new String[0], cwd);
        assertThat(plain.safeMode()).isFalse();
        assertThat(plain.pluginDir()).isNull();
        assertThat(plain.standalone()).isFalse();
        assertThat(plain.standaloneLaunch()).isFalse();

        assertThat(AppArguments.parse(new String[]{"--safe-mode"}, cwd).standaloneLaunch()).isTrue();
        assertThat(AppArguments.parse(new String[]{"--standalone"}, cwd).standaloneLaunch()).isTrue();
        assertThat(AppArguments.parse(new String[]{"--config", "c.toml"}, cwd).standaloneLaunch()).isTrue();
        AppArguments dev = AppArguments.parse(new String[]{"--plugin-dir", "build/../out", "--safe-mode"}, cwd);
        assertThat(dev.pluginDir()).isEqualTo(cwd.resolve("out"));
        assertThat(dev.safeMode()).isTrue();
        assertThat(dev.standaloneLaunch()).isTrue();

        for (String[] args : new String[][]{{"--safe-mode", "--safe-mode"}, {"--standalone", "--standalone"},
                {"--plugin-dir"}, {"--plugin-dir", "--help"}, {"--plugin-dir", ""}, {"--plugin-dir", "a", "--plugin-dir", "b"}}) {
            assertThatIllegalArgumentException().as(java.util.Arrays.toString(args))
                .isThrownBy(() -> AppArguments.parse(args, cwd)).withMessageContaining("Usage:");
        }
        assertThat(AppArguments.USAGE).contains("--safe-mode", "--plugin-dir <path>", "--standalone");
    }
}
