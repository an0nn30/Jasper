package dev.jasper.app.restart;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RestartCommandTest {
    private static Optional<List<String>> plan(RestartMode mode, String... arguments) {
        return RestartCommand.plan(Optional.of("/Applications/Jasper.app/Contents/MacOS/Jasper"), Optional.of(arguments), mode);
    }

    @Test void theSameLaunchKeepsItsFlagsButNeverComesBackAsABackgroundProcess() {
        assertThat(plan(RestartMode.SAME, "--config", "/tmp/my config.toml", "--background", "--plugin-dir", "/dev/plugin")).contains(
            List.of("/Applications/Jasper.app/Contents/MacOS/Jasper", "--config", "/tmp/my config.toml", "--plugin-dir", "/dev/plugin"));
        assertThat(plan(RestartMode.SAME, "--safe-mode")).contains(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper", "--safe-mode"));
        assertThat(plan(RestartMode.SAME)).contains(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper"));
    }

    @Test void restartingNormallyLeavesSafeModeAndStandaloneIsAddedOnce() {
        assertThat(plan(RestartMode.NORMAL, "--safe-mode", "--background")).contains(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper"));
        assertThat(plan(RestartMode.STANDALONE, "--safe-mode")).contains(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper", "--standalone"));
        assertThat(plan(RestartMode.STANDALONE, "--standalone", "--safe-mode"))
            .contains(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper", "--standalone"));
        assertThat(RestartCommand.standalone(List.of("jasper", "--standalone"))).containsExactly("jasper", "--standalone");
        assertThat(RestartCommand.standalone(List.of("jasper"))).containsExactly("jasper", "--standalone");
    }

    @Test void aJavaCommandLineIsReplayedWholeWithTheFlagsRewrittenAfterIt() {
        var java = RestartCommand.plan(Optional.of("/jbr/bin/java"), Optional.of(new String[]{"-Xmx1g", "-classpath", "/lib/*",
            "dev.jasper.app.Main", "--safe-mode"}), RestartMode.NORMAL);
        assertThat(java).contains(List.of("/jbr/bin/java", "-Xmx1g", "-classpath", "/lib/*", "dev.jasper.app.Main"));
    }

    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    @Test void theRunningJvmReportsItsOwnCommandLine() {
        assertThat(RestartCommand.current(RestartMode.SAME)).as("macOS and Linux report a process's own arguments")
            .hasValueSatisfying(line -> assertThat(line).hasSizeGreaterThan(1).doesNotContain("--background"));
    }

    @Test void anUnknownCommandLineCannotBeRestarted() {
        assertThat(RestartCommand.plan(Optional.empty(), Optional.of(new String[0]), RestartMode.SAME)).isEmpty();
        assertThat(RestartCommand.plan(Optional.of("/bin/jasper"), Optional.empty(), RestartMode.SAME)).isEmpty();
        assertThat(RestartCommand.plan(Optional.of(" "), Optional.of(new String[0]), RestartMode.SAME)).isEmpty();
    }
}
