package dev.jasper.app;

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
import static org.assertj.core.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class ShellIntegrationScriptTest {
    @TempDir Path dir;

    private Path scripts() throws Exception { return ShellIntegrationScripts.install(dir.resolve("shell-integration")); }

    private Map<String, String> environment(Path home) {
        var env = new HashMap<String, String>();
        env.put("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
        env.put("HOME", home.toString());
        env.put("TERM", "xterm-256color");
        env.put("TERM_PROGRAM", "Jasper");
        env.put("LANG", "en_US.UTF-8");
        return env;
    }

    private static int indexAfter(String output, String needle, int from) {
        int at = output.indexOf(needle, from);
        assertThat(at).as("expected %s after offset %d in:%n%s", needle.replace("\033", "ESC").replace("\007", "BEL"), from, output).isGreaterThanOrEqualTo(0);
        return at + needle.length();
    }

    @Test void zshEmitsMarksDirectoryAndTheExactCommandLine() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")));
        Path home = Files.createDirectories(dir.resolve("home with space"));
        Path work = Files.createDirectories(home.resolve("my dir"));
        Files.writeString(home.resolve(".zshrc"), "export RC_RAN=yes\nPROMPT='rc%% '\nsource \"" + scripts() + "/jasper.zsh\"\n");
        var env = environment(home);
        env.put("ZDOTDIR", home.toString());
        String output = ShellRun.run(List.of("/bin/zsh", "-i"), env, work, "echo $RC_RAN\ncd ..\nfalse\necho \"one\ntwo\"\n\nexit\n");
        String host = ShellRun.hostname();
        // The shell has no PWD in its environment, so it reports getcwd(): the JUnit temp
        // directory is under macOS's /var -> private/var symlink, hence toRealPath() here.
        String encodedWork = work.toRealPath().toString().replace(" ", "%20");
        int at = indexAfter(output, ShellRun.CWD(host, encodedWork), 0);
        at = indexAfter(output, ShellRun.A, at);
        at = indexAfter(output, "rc% ", at);
        at = indexAfter(output, ShellRun.B, at);
        at = indexAfter(output, ShellRun.CMD("echo $RC_RAN"), at);
        at = indexAfter(output, ShellRun.C, at);
        at = indexAfter(output, "yes", at);
        at = indexAfter(output, ShellRun.D(0), at);
        at = indexAfter(output, ShellRun.CMD("cd .."), at);
        at = indexAfter(output, ShellRun.D(0), at);
        at = indexAfter(output, ShellRun.CWD(host, home.toRealPath().toString().replace(" ", "%20")), at);
        at = indexAfter(output, ShellRun.CMD("false"), at);
        at = indexAfter(output, ShellRun.D(1), at);
        at = indexAfter(output, ShellRun.CMD("echo \"one\ntwo\""), at);
        at = indexAfter(output, ShellRun.C, at);
        at = indexAfter(output, ShellRun.D(0), at);
        // The empty Enter yields a prompt with A and B but no D between them.
        int emptyA = indexAfter(output, ShellRun.A, at);
        int nextD = output.indexOf("\033]133;D;", emptyA);
        int nextC = output.indexOf(ShellRun.C, emptyA);
        assertThat(nextC).isGreaterThanOrEqualTo(0);
        assertThat(nextD == -1 || nextD > nextC).isTrue();
        assertThat(output.split(java.util.regex.Pattern.quote(ShellRun.A), -1).length - 1).isEqualTo(6);
        // "dquote> " is zsh's stock PROMPT2 and appears without the script too; what must not
        // happen is a second C mark for the continuation line.
        assertThat(output).doesNotContain("dquote> " + ShellRun.C);
    }

    @Test void zshDoesNotDoubleLoadOrRunOutsideJasper() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")));
        Path home = Files.createDirectories(dir.resolve("home"));
        Files.writeString(home.resolve(".zshrc"), "source \"" + scripts() + "/jasper.zsh\"\nsource \"" + scripts() + "/jasper.zsh\"\n");
        var env = environment(home);
        env.put("ZDOTDIR", home.toString());
        String twice = ShellRun.run(List.of("/bin/zsh", "-i"), env, home, "true\nexit\n");
        // One C per command, not one per load: preexec fires for `exit` as well as for `true`.
        assertThat(twice.split(java.util.regex.Pattern.quote(ShellRun.C), -1).length - 1).isEqualTo(2);
        env.put("JASPER_INTEGRATION_LOADED", "1");
        assertThat(ShellRun.run(List.of("/bin/zsh", "-i"), env, home, "true\nexit\n")).doesNotContain(ShellRun.C);
        env.remove("JASPER_INTEGRATION_LOADED");
        env.put("TERM_PROGRAM", "iTerm.app");
        assertThat(ShellRun.run(List.of("/bin/zsh", "-i"), env, home, "true\nexit\n")).doesNotContain(ShellRun.A);
    }

    @Test void bashEmitsMarksDirectoryAndTheExactCommandLine() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        Path home = Files.createDirectories(dir.resolve("home with space"));
        Path work = Files.createDirectories(home.resolve("my dir"));
        Files.writeString(home.resolve(".bashrc"), "export RC_RAN=yes\nPS1='rc$ '\nPROMPT_COMMAND='export PC_RAN=yes'\ntrap 'export TRAP_RAN=yes; echo USERTRAP >> \"$HOME/ut\"' DEBUG\nsource \"" + scripts() + "/jasper.bash\"\n");
        var env = environment(home);
        String output = ShellRun.run(List.of("/bin/bash", "-i"), env, work,
            "echo $RC_RAN $PC_RAN $TRAP_RAN\ncd ..\nfalse\necho \"one\ntwo\"\nrm -f \"$HOME/ut\"; cat \"$HOME/ut\"\n\nexit\n");
        String host = ShellRun.hostname();
        int at = indexAfter(output, ShellRun.CWD(host, work.toRealPath().toString().replace(" ", "%20")), 0);
        at = indexAfter(output, ShellRun.A, at);
        at = indexAfter(output, "rc$ ", at);
        at = indexAfter(output, ShellRun.B, at);
        at = indexAfter(output, ShellRun.CMD("echo $RC_RAN $PC_RAN $TRAP_RAN"), at);
        at = indexAfter(output, ShellRun.C, at);
        at = indexAfter(output, "yes yes yes", at);
        at = indexAfter(output, ShellRun.D(0), at);
        at = indexAfter(output, ShellRun.CMD("cd .."), at);
        at = indexAfter(output, ShellRun.D(0), at);
        at = indexAfter(output, ShellRun.CWD(host, home.toRealPath().toString().replace(" ", "%20")), at);
        at = indexAfter(output, ShellRun.CMD("false"), at);
        at = indexAfter(output, ShellRun.D(1), at);
        at = indexAfter(output, "\033]1341;jasper;cmd;", at);
        at = indexAfter(output, ShellRun.C, at);
        at = indexAfter(output, ShellRun.D(0), at);
        // The rc file's own DEBUG trap must still run for every command, not just until Jasper
        // installs its own: it re-creates the file the same line just deleted, so cat succeeds.
        at = indexAfter(output, ShellRun.CMD("rm -f \"$HOME/ut\"; cat \"$HOME/ut\""), at);
        at = indexAfter(output, ShellRun.C, at);
        at = indexAfter(output, "USERTRAP", at);
        at = indexAfter(output, ShellRun.D(0), at);
        // The empty Enter yields a prompt with A and B but no D between them.
        int emptyA = indexAfter(output, ShellRun.A, at);
        int nextD = output.indexOf("\033]133;D;", emptyA);
        int nextC = output.indexOf(ShellRun.C, emptyA);
        assertThat(nextC).isGreaterThanOrEqualTo(0);
        assertThat(nextD == -1 || nextD > nextC).isTrue();
        assertThat(output.split(java.util.regex.Pattern.quote(ShellRun.A), -1).length - 1).isEqualTo(7);
        assertThat(output).doesNotContain("> \033]133;C");
    }

    @Test void bashFlattensAnArrayPromptCommandWhereTheShellRunsOnlyItsFirstElement() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        Path home = Files.createDirectories(dir.resolve("home"));
        Files.writeString(home.resolve(".bashrc"), "PS1='$ '\nPROMPT_COMMAND=('export PC=1')\nsource \"" + scripts() + "/jasper.bash\"\n");
        String output = ShellRun.run(List.of("/bin/bash", "-i"), environment(home), home, "echo pc=$PC\nexit\n");
        int at = indexAfter(output, ShellRun.A, 0);
        at = indexAfter(output, ShellRun.B, at);
        at = indexAfter(output, ShellRun.CMD("echo pc=$PC"), at);
        at = indexAfter(output, ShellRun.C, at);
        at = indexAfter(output, "pc=1", at);
        indexAfter(output, ShellRun.D(0), at);
    }

    @Test void bashKeepsAPromptCommandArrayAndHidesSpaceHiddenHistoryFromJasperTooOnBash5() throws Exception {
        Path bash5 = Path.of("/opt/homebrew/bin/bash");
        Assumptions.assumeTrue(Files.isExecutable(bash5));
        Path home = Files.createDirectories(dir.resolve("home"));
        Files.writeString(home.resolve(".bashrc"), "PS1='$ '\nPROMPT_COMMAND=('export PC_ONE=1' 'export PC_TWO=2')\nHISTCONTROL=ignorespace\nsource \"" + scripts() + "/jasper.bash\"\n");
        String output = ShellRun.run(List.of(bash5.toString(), "-i"), environment(home), home, "echo $PC_ONE$PC_TWO\n echo hidden\nexit\n");
        int at = indexAfter(output, ShellRun.CMD("echo $PC_ONE$PC_TWO"), 0);
        at = indexAfter(output, "12", at);
        // The user hid this line from bash's own history, so Jasper reports neither the text nor a
        // C mark: emitting C would let the screen read recapture what HISTCONTROL was meant to hide.
        // Three commands run; only the visible one and `exit` are marked.
        assertThat(output).doesNotContain(ShellRun.CMD("echo hidden"));
        assertThat(output.split(java.util.regex.Pattern.quote(ShellRun.C), -1).length - 1).isEqualTo(2);
        indexAfter(output, ShellRun.D(0), at);
    }

    /** The rc body is sourced before Jasper's script, exactly as a real user's rc file would be. */
    private ShellRun.Result interactive(String shell, String rcBody, String input) throws Exception {
        return interactive(shell, rcBody, input, List.of("-i"));
    }

    private ShellRun.Result interactive(String shell, String rcBody, String input, List<String> arguments)
            throws Exception {
        String flavour = shell.endsWith("zsh") ? "zsh" : "bash";
        Path home = Files.createDirectories(dir.resolve(
            "home-" + flavour + "-" + Math.abs((rcBody + arguments).hashCode())));
        Files.writeString(home.resolve("." + flavour + "rc"),
            rcBody + "source \"" + scripts() + "/jasper." + flavour + "\"\n");
        var env = environment(home);
        if (flavour.equals("zsh")) env.put("ZDOTDIR", home.toString());
        var command = new java.util.ArrayList<>(List.of(shell));
        command.addAll(arguments);
        return ShellRun.runSeparate(command, env, home, input + "exit\n");
    }

    @Test void scriptsStaySilentUnderNounset() throws Exception {
        for (String shell : List.of("/bin/zsh", "/bin/bash")) {
            if (!Files.isExecutable(Path.of(shell))) continue;
            String rc = shell.endsWith("zsh") ? "setopt nounset\n" : "set -u\n";
            ShellRun.Result result = interactive(shell, rc, "printf 'done\\n'\n");
            assertThat(result.errors()).as("%s under nounset", shell)
                .doesNotContain("unbound variable").doesNotContain("parameter not set");
            assertThat(result.output()).contains(ShellRun.C);
        }
    }

    @Test void aReadonlyPromptDoesNotBreakTheShellOrTheMarks() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        ShellRun.Result result = interactive("/bin/bash", "readonly PS1='P> '\n", "printf 'done\\n'\n");
        assertThat(result.errors()).doesNotContain("readonly variable");
        assertThat(result.output()).contains(ShellRun.C).contains(ShellRun.CMD("printf 'done\\n'"));
    }

    @Test void aCommandNamingJasperOwnFunctionsIsStillReported() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        ShellRun.Result result = interactive("/bin/bash", "PS1='$ '\n", "echo __jasper_probe\n");
        assertThat(result.output()).contains(ShellRun.CMD("echo __jasper_probe")).contains(ShellRun.C);
    }

    /** A `zsh -c` the user runs from an already-integrated shell is a plain child: no Jasper ZDOTDIR. */
    @Test void aNonInteractiveZshBelowAnIntegratedShellLeavesZdotdirAlone() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")));
        Path home = Files.createDirectories(dir.resolve("home-zdotdir"));
        var env = environment(home);
        env.put("ZDOTDIR", scripts().resolve("zsh").toString());
        env.put("JASPER_INTEGRATION_LOADED", "1");
        String reported = ShellRun.run(List.of("/bin/zsh", "-c", "printf %s \"$ZDOTDIR\""), env, home, "");
        assertThat(reported).isEmpty();
    }

    /**
     * tmux runs its default-command as `$SHELL -c ...`, so the interactive shell is the child of a
     * non-interactive one. Stripping ZDOTDIR in that outer shell strands the inner one with no
     * integration — the exact shape of a `set -g default-command ${SHELL}` tmux configuration.
     */
    @Test void aNonInteractiveZshBeforeIntegrationHandsZdotdirToItsChild() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")));
        Path home = Files.createDirectories(dir.resolve("home-nested"));
        Files.writeString(home.resolve(".zshrc"), "PROMPT='rc%% '\n");
        var env = environment(home);
        env.put("ZDOTDIR", scripts().resolve("zsh").toString());
        env.put("JASPER_SHELL_INTEGRATION", scripts().toString());
        env.remove("JASPER_INTEGRATION_LOADED");
        String reported = ShellRun.run(List.of("/bin/zsh", "-c", "printf %s \"$ZDOTDIR\""), env, home, "");
        assertThat(reported).isEqualTo(scripts().resolve("zsh").toString());
    }

    @Test void fishReWrapsAPromptDefinedAfterTheIntegrationLoaded() throws Exception {
        Path fish = Path.of("/opt/homebrew/bin/fish");
        Assumptions.assumeTrue(Files.isExecutable(fish), "fish is not installed");
        Path home = Files.createDirectories(dir.resolve("home-fish"));
        Path config = Files.createDirectories(home.resolve(".config/fish"));
        Files.writeString(config.resolve("config.fish"), "function fish_prompt\n    printf 'mine> '\nend\n");
        var env = environment(home);
        env.put("XDG_DATA_DIRS", scripts().resolve("fish") + ":/usr/local/share:/usr/share");
        env.put("JASPER_SHELL_INTEGRATION", scripts().toString());
        String output = ShellRun.run(List.of(fish.toString(), "-i"), env, home, "printf 'done\\n'\nexit\n");
        assertThat(output).contains("mine> ").contains(ShellRun.B);
    }

    /** ignoreboth is ignorespace plus ignoredups; a repeat is not something the user hid. */
    @Test void ignorebothStillReportsARepeatedCommand() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        ShellRun.Result result = interactive("/bin/bash", "PS1='$ '\nHISTCONTROL=ignoreboth\n",
            "echo same\necho same\n");
        assertThat(result.output().split(java.util.regex.Pattern.quote(ShellRun.CMD("echo same")), -1).length - 1)
            .as("both runs of the repeated command are reported").isEqualTo(2);
    }

    /** With history off, "the history number did not advance" says nothing about privacy. */
    @Test void aShellWithHistoryDisabledStillReportsItsCommands() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        ShellRun.Result result = interactive("/bin/bash", "PS1='$ '\nHISTCONTROL=ignorespace\nHISTSIZE=0\n",
            "echo visible\n");
        assertThat(result.output()).contains(ShellRun.CMD("echo visible")).contains(ShellRun.C);
    }

    /** The wrapper files run before the rc file, so -u has to be set on the command line to reach them. */
    @Test void wrapperFilesStaySilentUnderNounsetSetBeforeTheyRun() throws Exception {
        if (Files.isExecutable(Path.of("/bin/bash"))) {
            ShellRun.Result bash = interactive("/bin/bash", "export RC_RAN=yes\nPS1='$ '\n",
                "echo rc=$RC_RAN\n", List.of("-u", "-i"));
            assertThat(bash.errors()).doesNotContain("unbound variable");
            assertThat(bash.output()).contains("rc=yes");
        }
        if (Files.isExecutable(Path.of("/bin/zsh"))) {
            ShellRun.Result zsh = interactiveZshWrappers();
            assertThat(zsh.errors()).doesNotContain("parameter not set");
            assertThat(zsh.output()).contains("rc=yes");
        }
    }

    /** zsh through the real ZDOTDIR wrappers, with nounset on before any of them runs. */
    private ShellRun.Result interactiveZshWrappers() throws Exception {
        Path home = Files.createDirectories(dir.resolve("home-zsh-wrappers"));
        Files.writeString(home.resolve(".zshrc"), "export RC_RAN=yes\nPROMPT='rc%% '\n");
        var env = environment(home);
        env.put("ZDOTDIR", scripts().resolve("zsh").toString());
        env.put("JASPER_SHELL_INTEGRATION", scripts().toString());
        return ShellRun.runSeparate(List.of("/bin/zsh", "-u", "-i"), env, home, "echo rc=$RC_RAN\nexit\n");
    }

    /**
     * The case a user protecting a secret actually hits: open a terminal and immediately type
     * " export TOKEN=...". History is still empty then, so "the number did not advance" cannot be
     * read as "history is off". Asserts on the real payload — base64 of the whole line.
     */
    @Test void ignorespaceHidesEvenTheFirstCommandOfTheSession() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        ShellRun.Result result = interactive("/bin/bash", "PS1='$ '\nHISTCONTROL=ignorespace\n",
            " echo SECRET\n");
        assertThat(result.output()).doesNotContain(ShellRun.CMD(" echo SECRET"))
            .doesNotContain(ShellRun.CMD("echo SECRET"));
        // Only `exit` is marked; the hidden line gets no command-start mark to capture it by.
        assertThat(result.output().split(java.util.regex.Pattern.quote(ShellRun.C), -1).length - 1).isEqualTo(1);
        // The prompt carries A and B, and bash writes its prompt to stderr.
        assertThat(result.errors()).contains(ShellRun.A);
    }

    /** Inside tmux, TERM_PROGRAM says "tmux"; only Jasper's own marker identifies the terminal. */
    @Test void theScriptsLoadUnderTmuxWhichOverwritesTermProgram() throws Exception {
        for (String shell : List.of("/bin/zsh", "/bin/bash")) {
            if (!Files.isExecutable(Path.of(shell))) continue;
            String flavour = shell.endsWith("zsh") ? "zsh" : "bash";
            Path home = Files.createDirectories(dir.resolve("home-tmux-" + flavour));
            Files.writeString(home.resolve("." + flavour + "rc"),
                "source \"" + scripts() + "/jasper." + flavour + "\"\n");
            var env = environment(home);
            env.put("TERM_PROGRAM", "tmux");
            env.put("JASPER_TERMINAL", "1");
            if (flavour.equals("zsh")) env.put("ZDOTDIR", home.toString());
            assertThat(ShellRun.run(List.of(shell, "-i"), env, home, "true\nexit\n")).as(shell).contains(ShellRun.C);
        }
    }

    @Test void theScriptsStayQuietWithNeitherMarker() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")));
        Path home = Files.createDirectories(dir.resolve("home-nomarker"));
        Files.writeString(home.resolve(".zshrc"), "source \"" + scripts() + "/jasper.zsh\"\n");
        var env = environment(home);
        env.put("TERM_PROGRAM", "iTerm.app");
        env.put("ZDOTDIR", home.toString());
        assertThat(ShellRun.run(List.of("/bin/zsh", "-i"), env, home, "true\nexit\n")).doesNotContain(ShellRun.A);
    }
}
