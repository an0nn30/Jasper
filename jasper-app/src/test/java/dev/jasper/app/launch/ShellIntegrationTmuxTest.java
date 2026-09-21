package dev.jasper.app.launch;

import dev.jasper.app.testsupport.ShellRun;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * tmux parses every escape its panes emit and forwards almost none of them: measured against tmux
 * 3.5a, not one OSC 133 mark reaches the outer terminal, so shell integration is silently dead
 * inside tmux. The only way through is tmux's own passthrough wrapper, which the scripts must emit
 * when they can see they are inside a pane.
 */
@DisabledOnOs(OS.WINDOWS)
class ShellIntegrationTmuxTest {
    @TempDir Path dir;

    /** ESC P tmux ; &lt;payload, every ESC doubled&gt; ESC \ */
    private static String wrapped(String sequence) {
        return "\033Ptmux;" + sequence.replace("\033", "\033\033") + "\033\\";
    }

    private Path scripts() throws Exception {
        return ShellIntegrationScripts.install(dir.resolve("shell-integration"));
    }

    private Map<String, String> environment(Path home, boolean inTmux) {
        var env = new HashMap<String, String>();
        env.put("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
        env.put("HOME", home.toString());
        env.put("TERM", "xterm-256color");
        env.put("TERM_PROGRAM", "Jasper");
        env.put("LANG", "en_US.UTF-8");
        // What tmux sets in every pane: socket path, server pid, session id.
        if (inTmux) env.put("TMUX", "/private/tmp/tmux-501/default,12345,0");
        return env;
    }

    private String runZsh(boolean inTmux) throws Exception {
        Path home = Files.createDirectories(dir.resolve(inTmux ? "tmux-home" : "plain-home"));
        Files.writeString(home.resolve(".zshrc"),
            "PROMPT='rc%% '\nsource \"" + scripts() + "/jasper.zsh\"\n");
        var env = environment(home, inTmux);
        env.put("ZDOTDIR", home.toString());
        return ShellRun.run(List.of("/bin/zsh", "-i"), env, home, "false\nexit\n");
    }

    private String runBash(boolean inTmux) throws Exception {
        Path home = Files.createDirectories(dir.resolve(inTmux ? "tmux-bash" : "plain-bash"));
        var env = environment(home, inTmux);
        return ShellRun.run(List.of("/bin/bash", "--rcfile", scripts() + "/jasper.bash", "-i"),
            env, home, "false\nexit\n");
    }

    @Test void zshWrapsItsMarksForTmuxSoTheySurviveThePane() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")), "zsh is not installed");

        String output = runZsh(true);

        assertThat(output).as("the command-start mark must be wrapped").contains(wrapped(ShellRun.C));
        assertThat(output).as("the exit-status mark must be wrapped").contains(wrapped(ShellRun.D(1)));
        assertThat(output).as("the prompt mark must be wrapped").contains(wrapped(ShellRun.A));
    }

    /** Wrapping outside tmux would put a stray DCS on every prompt of every ordinary shell. */
    @Test void zshEmitsPlainMarksWhenItIsNotInsideTmux() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")), "zsh is not installed");

        String output = runZsh(false);

        assertThat(output).contains(ShellRun.C);
        assertThat(output).doesNotContain("\033Ptmux;");
    }

    @Test void bashWrapsItsMarksForTmuxSoTheySurviveThePane() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")), "bash is not installed");

        String output = runBash(true);

        assertThat(output).contains(wrapped(ShellRun.C));
        assertThat(output).contains(wrapped(ShellRun.D(1)));
    }

    @Test void bashEmitsPlainMarksWhenItIsNotInsideTmux() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")), "bash is not installed");

        String output = runBash(false);

        assertThat(output).contains(ShellRun.C);
        assertThat(output).doesNotContain("\033Ptmux;");
    }

    /**
     * The command line travels as base64 on the private OSC 1341 channel; wrapping must not disturb
     * the payload, or Jasper falls back to scraping the command off the screen.
     */
    @Test void theCommandPayloadSurvivesWrappingIntact() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")), "zsh is not installed");

        String output = runZsh(true);

        assertThat(output).contains(wrapped(ShellRun.CMD("false")));
    }
}
