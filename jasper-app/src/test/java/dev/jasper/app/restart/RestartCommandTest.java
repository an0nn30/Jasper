package dev.jasper.app.restart;

import static dev.jasper.app.restart.RestartMode.NORMAL;
import static dev.jasper.app.restart.RestartMode.SAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class RestartCommandTest {
    private static final String JASPER = "/Applications/Jasper.app/Contents/MacOS/Jasper";

    /** A packaged launcher: the operating system reports exactly the arguments main received. */
    private static Optional<List<String>> plan(RestartMode mode, String... arguments) {
        return RestartCommand.plan(Optional.of(JASPER), Optional.of(arguments), List.of(arguments), mode);
    }

    @Test void theSameLaunchKeepsItsFlagsButNeverComesBackAsABackgroundProcess() {
        assertThat(plan(SAME, "--config", "/tmp/my config.toml", "--background", "--plugin-dir", "/dev/plugin")).contains(
            List.of(JASPER, "--config", "/tmp/my config.toml", "--plugin-dir", "/dev/plugin"));
        assertThat(plan(SAME, "--safe-mode")).contains(List.of(JASPER, "--safe-mode"));
        assertThat(plan(SAME)).contains(List.of(JASPER));
    }

    @Test void restartingNormallyLeavesSafeMode() {
        assertThat(plan(NORMAL, "--safe-mode", "--background")).contains(List.of(JASPER));
    }

    @Test void theLaunchsPathsAndAnEarlierReplacingAreNotRepeated() {
        assertThat(plan(SAME, "--config", "notes.txt", "/tmp/a.txt", "src", "--replacing", "4242"))
            .as("a config file may be named like a path; the paths after it are the launch's").contains(List.of(JASPER, "--config", "notes.txt"));
    }

    @Test void aJavaCommandLineIsReplayedWholeWithItsOwnArgumentsRewrittenAfterIt() {
        var java = RestartCommand.plan(Optional.of("/jbr/bin/java"), Optional.of(new String[]{"-Xmx1g", "-classpath", "/lib/*",
            "dev.jasper.app.Main", "notes.txt", "--safe-mode"}), List.of("notes.txt", "--safe-mode"), NORMAL);
        assertThat(java).contains(List.of("/jbr/bin/java", "-Xmx1g", "-classpath", "/lib/*", "dev.jasper.app.Main"));
    }

    @Test void argumentsThatDoNotEndWithTheLaunchsLoseOnlyTheFlagsBySpelling() {
        var other = RestartCommand.plan(Optional.of("/app/Jasper"), Optional.of(new String[]{"-Dx=1", "Main", "--background",
            "--replacing", "7", "--safe-mode", "kept"}), List.of("missing"), NORMAL);
        assertThat(other).contains(List.of("/app/Jasper", "-Dx=1", "Main", "kept"));
    }

    @Test void theReplacementWaitsForThisProcessAndOpensThePaths() {
        Path notes = Path.of("/tmp/notes.txt").toAbsolutePath();
        assertThat(RestartCommand.replacing(List.of(JASPER, "--safe-mode"), 4242, List.of(notes)))
            .containsExactly(JASPER, "--safe-mode", "--replacing", "4242", notes.toString());
        assertThat(RestartCommand.replacing(List.of(JASPER), 7, List.of())).containsExactly(JASPER, "--replacing", "7");
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test void theRunningJvmReportsItsOwnCommandLine() {
        assertThat(RestartCommand.current(SAME, List.of())).as("macOS and Linux report a process's own arguments")
            .hasValueSatisfying(line -> assertThat(line).hasSizeGreaterThan(1).doesNotContain("--background"));
    }

    @Test void anUnknownCommandLineCannotBeRestarted() {
        assertThat(RestartCommand.plan(Optional.empty(), Optional.of(new String[0]), List.of(), SAME)).isEmpty();
        assertThat(RestartCommand.plan(Optional.of("/bin/jasper"), Optional.empty(), List.of(), SAME)).isEmpty();
        assertThat(RestartCommand.plan(Optional.of(" "), Optional.of(new String[0]), List.of(), SAME)).isEmpty();
    }

    @Test void aRestartComesBackWithAWindow() {
        assertThat(plan(SAME, "--no-window")).contains(List.of(JASPER));
    }
}
