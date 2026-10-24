package dev.jasper.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppArgumentsTest {
    @TempDir Path cwd;

    private AppArguments parse(String... args) { return AppArguments.parse(args, cwd); }

    private void rejected(String[]... cases) {
        for (String[] args : cases)
            assertThatIllegalArgumentException().as(java.util.Arrays.toString(args))
                .isThrownBy(() -> AppArguments.parse(args, cwd)).withMessageContaining("Usage:");
    }

    @Test void absentOverrideAndHelpAreParsedWithoutCreatingFiles() {
        AppArguments plain = parse();
        assertThat(plain.configOverride()).isNull();
        assertThat(plain.help()).isFalse();
        assertThat(plain.paths()).isEmpty();
        assertThat(plain.replacing()).isEmpty();
        assertThat(plain.workingDirectory()).isEqualTo(cwd);
        assertThat(plain.given()).isEmpty();
        assertThat(parse("--help").help()).isTrue();
        assertThat(cwd.resolve("config.toml")).doesNotExist();
    }

    @Test void relativeAndAbsoluteOverridesNormalizeAgainstWorkingDirectory() {
        assertThat(parse("--config", "nested/../config.toml").configOverride()).isEqualTo(cwd.resolve("config.toml"));
        AppArguments absolute = parse("--config", cwd.resolve("a/../other.toml").toString(), "--help");
        assertThat(absolute.configOverride()).isEqualTo(cwd.resolve("other.toml"));
        assertThat(absolute.help()).isTrue();
        assertThat(cwd.resolve("other.toml")).doesNotExist();
    }

    @Test void invalidMissingAndDuplicateArgumentsFailClearlyEvenWithHelp() {
        rejected(new String[]{"--wat"}, new String[]{"--config"}, new String[]{"--config", "--help"}, new String[]{"--config", ""},
            new String[]{"--config", "a", "--config", "b"}, new String[]{"--help", "--help"}, new String[]{"--help", "--wat"},
            new String[]{""}, new String[]{" "}, new String[]{"--replacing"}, new String[]{"--replacing", "x"},
            new String[]{"--replacing", "0"}, new String[]{"--replacing", "-3"}, new String[]{"--replacing", "--help"},
            new String[]{"--replacing", "1", "--replacing", "2"});
    }

    @Test void anArgumentThatIsNotAnOptionIsAPathResolvedAgainstTheWorkingDirectory() {
        String absolute = cwd.resolve("x.md").toString();
        AppArguments launch = parse("notes.txt", "--safe-mode", "src/../docs", absolute);
        assertThat(launch.paths()).containsExactly(cwd.resolve("notes.txt"), cwd.resolve("docs"), cwd.resolve("x.md"));
        assertThat(launch.safeMode()).isTrue();
        assertThat(launch.given()).containsExactly("notes.txt", "--safe-mode", "src/../docs", absolute);
        assertThat(parse("-psn_0_42").paths()).as("one dash is a path, not an option").containsExactly(cwd.resolve("-psn_0_42"));
        assertThat(AppArguments.USAGE).endsWith("[<path>...]");
    }

    @Test void replacingNamesTheProcessARestartReplacesAndStaysOutOfTheUsage() {
        assertThat(parse("--replacing", "4242", "notes.txt").replacing()).isEqualTo(OptionalLong.of(4242));
        assertThat(AppArguments.USAGE).doesNotContain("--replacing");
    }

    @Test void backgroundIsOffByDefaultAndParsesOnceAlongsideOtherOptions() {
        assertThat(parse().background()).isFalse();
        AppArguments login = parse("--config", "c.toml", "--background");
        assertThat(login.background()).isTrue();
        assertThat(login.configOverride()).isEqualTo(cwd.resolve("c.toml"));
        rejected(new String[]{"--background", "--background"}, new String[]{"--background", "--wat"});
        assertThat(AppArguments.USAGE).contains("--background");
    }

    @Test void pluginFlagsParseOnce() {
        AppArguments plain = parse();
        assertThat(plain.safeMode()).isFalse();
        assertThat(plain.pluginDir()).isNull();
        AppArguments dev = parse("--plugin-dir", "build/../out", "--safe-mode");
        assertThat(dev.pluginDir()).isEqualTo(cwd.resolve("out"));
        assertThat(dev.safeMode()).isTrue();
        rejected(new String[]{"--safe-mode", "--safe-mode"}, new String[]{"--plugin-dir"},
            new String[]{"--plugin-dir", "--help"}, new String[]{"--plugin-dir", ""}, new String[]{"--plugin-dir", "a", "--plugin-dir", "b"});
        assertThat(AppArguments.USAGE).contains("--safe-mode", "--plugin-dir <path>");
    }

    @Test void theStartUpFlagsAreWhatASecondLaunchReportsAndResidencyWantsNone() {
        assertThat(parse().startupFlags()).isEmpty();
        assertThat(parse("notes.txt", "--background").startupFlags()).isEmpty();
        assertThat(parse("--plugin-dir", "p", "--config", "c.toml", "--safe-mode").startupFlags())
            .containsExactly("--safe-mode", "--config", "--plugin-dir");
        assertThat(parse().residencyAllowed()).isTrue();
        assertThat(parse("notes.txt").residencyAllowed()).as("paths are no start-up flag").isTrue();
        assertThat(parse("--safe-mode").residencyAllowed()).isFalse();
        assertThat(parse().withHomeOverride(true).residencyAllowed()).as("another home").isFalse();
        AppArguments overridden = parse("notes.txt", "--replacing", "7").withHomeOverride(true);
        assertThat(overridden.homeOverride()).isTrue();
        assertThat(overridden.paths()).containsExactly(cwd.resolve("notes.txt"));
        assertThat(overridden.replacing()).isEqualTo(OptionalLong.of(7));
        assertThat(parse().withHomeOverride(false)).isEqualTo(parse());
    }

    @Test void noWindowParsesOnceAndNeverWithBackground() {
        assertThat(parse().noWindow()).isFalse();
        assertThat(parse("--no-window").noWindow()).isTrue();
        rejected(new String[]{"--no-window", "--no-window"}, new String[]{"--no-window", "--background"});
        assertThat(AppArguments.USAGE).contains("--no-window");
        assertThat(parse("--no-window").withHomeOverride(true).noWindow()).isTrue();
    }
}
