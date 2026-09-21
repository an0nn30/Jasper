package dev.jasper.app.launch;

import dev.jasper.app.testsupport.ConfigTestSupport;
import static dev.jasper.app.testsupport.ConfigTestSupport.snapshot;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ShellIntegrationMode;
import dev.jasper.app.config.TerminalConfig;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LaunchSettingsTest {


    private static ConfigSnapshot withShell(String program, List<String> args, ShellIntegrationMode mode) {
        var d = ConfigSnapshot.defaults();
        var t = d.terminal();
        var terminal = new TerminalConfig(new TerminalConfig.Shell(program, args), t.env(), t.scrollback(), t.optionAsMeta(),
            t.cursorShape(), t.cursorBlink(), t.dimInactivePanes(), t.copyOnSelect(), t.bell(), t.onExit(), mode);
        return new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.variant(), Map.of(), d.columns(),
            d.lines(), terminal, d.buddyEnabled(), d.historyEnabled(), d.maxResults());
    }

    @Test void termProgramIsAlwaysJasperAndOnlyAutoInjects() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        var off = LaunchSettings.resolve(withShell("/bin/zsh", List.of("-l"), ShellIntegrationMode.OFF), "Mac OS X", Map.of(), 150, 45, dir);
        assertThat(off.environment()).containsEntry("TERM_PROGRAM", "Jasper").doesNotContainKey("JASPER_SHELL_INTEGRATION").doesNotContainKey("ZDOTDIR");
        var manual = LaunchSettings.resolve(withShell("/bin/zsh", List.of("-l"), ShellIntegrationMode.MANUAL), "Mac OS X", Map.of(), 150, 45, dir);
        assertThat(manual.environment()).containsEntry("JASPER_SHELL_INTEGRATION", dir.toString()).doesNotContainKey("ZDOTDIR");
        assertThat(manual.command()).containsExactly("/bin/zsh", "-l");
        var auto = LaunchSettings.resolve(withShell("/bin/zsh", List.of("-l"), ShellIntegrationMode.AUTO), "Mac OS X",
            Map.of("ZDOTDIR", "/Users/me/dots"), 150, 45, dir);
        assertThat(auto.environment()).containsEntry("ZDOTDIR", dir.resolve("zsh").toString())
            .containsEntry("JASPER_ORIGINAL_ZDOTDIR", "/Users/me/dots").containsEntry("JASPER_SHELL_INTEGRATION", dir.toString());
        assertThat(auto.command()).containsExactly("/bin/zsh", "-l");
        var noDir = LaunchSettings.resolve(withShell("/bin/zsh", List.of(), ShellIntegrationMode.AUTO), "Mac OS X", Map.of(), 150, 45, null);
        assertThat(noDir.environment()).containsEntry("TERM_PROGRAM", "Jasper").doesNotContainKey("JASPER_SHELL_INTEGRATION");
        assertThat(LaunchSettings.resolve(withShell("/bin/zsh", List.of(), ShellIntegrationMode.AUTO), "Mac OS X", Map.of(), 150, 45).environment())
            .doesNotContainKey("ZDOTDIR");
    }

    @Test void injectHandlesEachShellAndLeavesOthersAlone() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        var zsh = new java.util.ArrayList<>(List.of("/usr/local/bin/zsh")); var zshEnv = new HashMap<String, String>();
        LaunchSettings.inject(zsh, zshEnv, dir);
        assertThat(zshEnv).containsEntry("ZDOTDIR", dir.resolve("zsh").toString()).doesNotContainKey("JASPER_ORIGINAL_ZDOTDIR");
        var bash = new java.util.ArrayList<>(List.of("/bin/bash", "-l", "--login")); var bashEnv = new HashMap<String, String>();
        LaunchSettings.inject(bash, bashEnv, dir);
        assertThat(bash).containsExactly("/bin/bash", "--rcfile", dir.resolve("bash/rc.bash").toString());
        assertThat(bashEnv).containsEntry("JASPER_LOGIN_SHELL", "1");
        // --noprofile asked for no profile files; rc.bash reads them whenever JASPER_LOGIN_SHELL is
        // set, so the two must not both be in force.
        var noProfile = new java.util.ArrayList<>(List.of("/bin/bash", "-l", "--noprofile")); var noProfileEnv = new HashMap<String, String>();
        LaunchSettings.inject(noProfile, noProfileEnv, dir);
        assertThat(noProfile).containsExactly("/bin/bash", "--rcfile", dir.resolve("bash/rc.bash").toString(), "--noprofile");
        assertThat(noProfileEnv).doesNotContainKey("JASPER_LOGIN_SHELL");
        var bashPlain = new java.util.ArrayList<>(List.of("bash")); var plainEnv = new HashMap<String, String>();
        LaunchSettings.inject(bashPlain, plainEnv, dir);
        assertThat(bashPlain).containsExactly("bash", "--rcfile", dir.resolve("bash/rc.bash").toString());
        assertThat(plainEnv).doesNotContainKey("JASPER_LOGIN_SHELL");
        var fish = new java.util.ArrayList<>(List.of("/opt/homebrew/bin/fish")); var fishEnv = new HashMap<String, String>(Map.of("XDG_DATA_DIRS", "/x:/y"));
        LaunchSettings.inject(fish, fishEnv, dir);
        assertThat(fishEnv).containsEntry("XDG_DATA_DIRS", dir.resolve("fish") + ":/x:/y");
        var fishDefault = new java.util.ArrayList<>(List.of("fish")); var fishDefaultEnv = new HashMap<String, String>();
        LaunchSettings.inject(fishDefault, fishDefaultEnv, dir);
        assertThat(fishDefaultEnv).containsEntry("XDG_DATA_DIRS", dir.resolve("fish") + ":/usr/local/share:/usr/share");
        var other = new java.util.ArrayList<>(List.of("/bin/sh", "-l")); var otherEnv = new HashMap<String, String>();
        LaunchSettings.inject(other, otherEnv, dir);
        assertThat(other).containsExactly("/bin/sh", "-l");
        assertThat(otherEnv).isEmpty();
    }

    /**
     * The scripts export JASPER_INTEGRATION_LOADED and return early when they see it, so a Jasper
     * launched from an integrated pane would otherwise get no marks in any pane.
     */
    @Test void jasperOwnMarkersNeverReachTheChildFromTheParentEnvironment() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        var inherited = Map.of("JASPER_INTEGRATION_LOADED", "1", "JASPER_LOGIN_SHELL", "1",
            "JASPER_ORIGINAL_ZDOTDIR", "/stale", "KEEP", "yes");
        var settings = LaunchSettings.resolve(withShell("/bin/zsh", List.of(), ShellIntegrationMode.AUTO),
            "Mac OS X", inherited, 150, 45, dir);
        assertThat(settings.environment()).doesNotContainKey("JASPER_INTEGRATION_LOADED")
            .doesNotContainKey("JASPER_LOGIN_SHELL").doesNotContainKey("JASPER_ORIGINAL_ZDOTDIR")
            .containsEntry("JASPER_SHELL_INTEGRATION", dir.toString()).containsEntry("KEEP", "yes");
    }

    /** bash honours only the last --rcfile and ignores it under --norc or -c, so Jasper stays out. */
    @Test void bashKeepsItsOwnStartupWhenTheUserAlreadyChoseAnRcFile() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        for (List<String> args : List.of(List.of("-l", "--norc"), List.of("-l", "--rcfile", "/my/rc"),
                List.of("-l", "--init-file", "/my/rc"), List.of("-c", "echo hi"), List.of("-lc", "echo hi"))) {
            var command = new java.util.ArrayList<>(List.of("/bin/bash"));
            command.addAll(args);
            var env = new HashMap<String, String>();
            LaunchSettings.inject(command, env, dir);
            assertThat(command).as("%s", args).doesNotContain(dir.resolve("bash/rc.bash").toString());
            assertThat(command.subList(1, command.size())).as("%s", args).isEqualTo(args);
            assertThat(env).as("%s", args).doesNotContainKey("JASPER_LOGIN_SHELL");
        }
    }

    @Test void bashLoginFlagsAreRecognisedInsideAShortCluster() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        var command = new java.util.ArrayList<>(List.of("/bin/bash", "-il"));
        var env = new HashMap<String, String>();
        LaunchSettings.inject(command, env, dir);
        assertThat(command).containsExactly("/bin/bash", "--rcfile", dir.resolve("bash/rc.bash").toString(), "-i");
        assertThat(env).containsEntry("JASPER_LOGIN_SHELL", "1");
    }

    /** A -l after -c or -- is the command's own argument, not a request for a login shell. */
    /** -o and -O take a following word; it is an option argument, not the end of the options. */
    @Test void anOptionArgumentDoesNotEndTheOptionScan() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        for (List<String> args : List.of(List.of("-o", "vi", "-l"), List.of("-O", "extglob", "-l"),
                List.of("+o", "vi", "-l"))) {
            var command = new java.util.ArrayList<>(List.of("/bin/bash"));
            command.addAll(args);
            var env = new HashMap<String, String>();
            LaunchSettings.inject(command, env, dir);
            assertThat(command).as("%s", args).doesNotContain("-l");
            assertThat(env).as("%s", args).containsEntry("JASPER_LOGIN_SHELL", "1");
        }
    }

    @Test void aLoginFlagPastTheOptionsIsLeftWhereItIs() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        var command = new java.util.ArrayList<>(List.of("/bin/bash", "--", "-l"));
        LaunchSettings.inject(command, new HashMap<>(), dir);
        assertThat(command).containsExactly("/bin/bash", "--rcfile", dir.resolve("bash/rc.bash").toString(), "--", "-l");
    }

    @Test void repeatedLaunchesDoNotAccumulateTheFishDataDirectory() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        var env = new HashMap<>(Map.of("XDG_DATA_DIRS", dir.resolve("fish") + ":/x"));
        LaunchSettings.inject(new java.util.ArrayList<>(List.of("fish")), env, dir);
        assertThat(env).containsEntry("XDG_DATA_DIRS", dir.resolve("fish") + ":/x");
    }

    @Test void userEnvOverlayCanStillOverrideTheIntegrationDirectory() {
        var d = ConfigSnapshot.defaults(); var t = d.terminal();
        var terminal = new TerminalConfig(new TerminalConfig.Shell("/bin/zsh", List.of()),
            Map.of("JASPER_SHELL_INTEGRATION", "/my/own"), t.scrollback(), t.optionAsMeta(), t.cursorShape(),
            t.cursorBlink(), t.dimInactivePanes(), t.copyOnSelect(), t.bell(), t.onExit(), ShellIntegrationMode.AUTO);
        var snapshot = new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.variant(), Map.of(),
            d.columns(), d.lines(), terminal, d.buddyEnabled(), d.historyEnabled(), d.maxResults());
        var settings = LaunchSettings.resolve(snapshot, "Mac OS X", Map.of(), 150, 45, Path.of("/opt/jasper/si"));
        assertThat(settings.environment()).containsEntry("JASPER_SHELL_INTEGRATION", "/my/own")
            .containsEntry("ZDOTDIR", "/my/own/zsh");
    }

    @Test void userEnvOverlayCanStillOverrideTermProgram() {
        var d = ConfigSnapshot.defaults(); var t = d.terminal();
        var terminal = new TerminalConfig(t.shell(), Map.of("TERM_PROGRAM", "Other"), t.scrollback(), t.optionAsMeta(), t.cursorShape(),
            t.cursorBlink(), t.dimInactivePanes(), t.copyOnSelect(), t.bell(), t.onExit(), ShellIntegrationMode.AUTO);
        var snapshot = new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.variant(), Map.of(), d.columns(),
            d.lines(), terminal, d.buddyEnabled(), d.historyEnabled(), d.maxResults());
        assertThat(LaunchSettings.resolve(snapshot, "Linux", Map.of(), 150, 45, Path.of("/tmp/si")).environment())
            .containsEntry("TERM_PROGRAM", "Other");
    }

    @Test void explicitExecutableKeepsArgumentBoundariesAndCopiesEnvironment() {
        var inherited = new HashMap<>(Map.of("SHELL", "/bin/zsh", "KEEP", "yes", "TERM", "bad", "COLORTERM", "bad"));
        var snapshot = snapshot("/path with spaces/custom", List.of("", "two words", "'quoted'", "$HOME"),
            Map.of("KEEP", "override", "ADD", "new"), 17, 80, 24);
        var settings = LaunchSettings.resolve(snapshot, "Mac OS X", inherited, 150, 45);
        assertThat(settings.command()).containsExactly("/path with spaces/custom", "", "two words", "'quoted'", "$HOME");
        assertThat(settings.label()).isEqualTo("custom");
        assertThat(settings.environment()).containsEntry("KEEP", "override").containsEntry("ADD", "new")
            .containsEntry("TERM", "xterm-256color").containsEntry("COLORTERM", "truecolor");
        assertThat(inherited).containsEntry("KEEP", "yes").doesNotContainKey("ADD");
        inherited.put("KEEP", "changed");
        assertThat(settings.environment()).containsEntry("KEEP", "override");
        assertThat(settings.columns()).isEqualTo(150); assertThat(settings.lines()).isEqualTo(45);
        assertThat(settings.scrollback()).isEqualTo(17);
        assertThatThrownBy(() -> settings.command().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> settings.environment().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void defaultShellUsesInheritedChoiceBeforeOverlayAndAppendsArguments() {
        var config = snapshot("", List.of("-x", "two words"), Map.of("SHELL", "/override"), 10_000, 150, 45);
        var settings = LaunchSettings.resolve(config, "Mac OS X", Map.of("SHELL", "/bin/zsh", "KEEP", "yes"), 150, 45);
        assertThat(settings.command()).containsExactly("/bin/zsh", "-l", "-x", "two words");
        assertThat(settings.environment()).containsEntry("SHELL", "/override").containsEntry("KEEP", "yes");
        assertThat(LaunchSettings.resolve(config, "Mac OS X", Map.of(), 150, 45).command())
            .containsExactly("/bin/zsh", "-l", "-x", "two words");
        assertThat(LaunchSettings.resolve(config, "Linux", Map.of(), 150, 45).command())
            .containsExactly("/bin/bash", "-l", "-x", "two words");
        assertThat(LaunchSettings.resolve(config, "Windows 11", Map.of(), 150, 45).command())
            .containsExactly("powershell.exe", "-NoLogo", "-x", "two words");
    }

    @Test void dropsInheritedTerminalIdentityButKeepsExplicitOverrides() {
        var inherited = new HashMap<>(Map.of("TERM_PROGRAM", "iTerm.app", "TERM_PROGRAM_VERSION", "1",
            "TERM_SESSION_ID", "old", "TMUX", "socket", "TMUX_PANE", "%1",
            "ITERM_SESSION_ID", "old", "ITERM_PROFILE", "old", "PATH", "/bin"));
        var result = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Mac OS X", inherited, 80, 24);
        assertThat(result.environment()).doesNotContainKeys("TERM_PROGRAM_VERSION",
            "TERM_SESSION_ID", "TMUX", "TMUX_PANE", "ITERM_SESSION_ID", "ITERM_PROFILE");
        assertThat(result.environment()).containsEntry("TERM_PROGRAM", "Jasper");
        assertThat(result.environment()).containsEntry("PATH", "/bin");
        assertThat(inherited).containsEntry("TMUX", "socket").containsEntry("ITERM_PROFILE", "old");
        var config = snapshot("", List.of(), Map.of("TERM_PROGRAM", "custom", "ITERM_PROFILE", "chosen"), 100, 80, 24);
        assertThat(LaunchSettings.resolve(config, "Mac OS X", inherited, 80, 24).environment())
            .containsEntry("TERM_PROGRAM", "custom").containsEntry("ITERM_PROFILE", "chosen")
            .containsEntry("TERM", "xterm-256color").containsEntry("COLORTERM", "truecolor");
    }

    @Test void macLocaleFallbackPreservesExplicitLocaleAndDoesNotAffectOtherPlatforms() {
        var defaults = ConfigSnapshot.defaults();
        for (var inherited : List.of(Map.<String,String>of(), Map.of("LANG", " "))) {
            assertThat(LaunchSettings.resolve(defaults, "Mac OS X", inherited, 80, 24).environment())
                .containsEntry("LANG", "en_US.UTF-8");
        }
        var config = snapshot("", List.of(), Map.of("LANG", "fr_FR.UTF-8"), 100, 80, 24);
        assertThat(LaunchSettings.resolve(config, "Mac OS X", Map.of("LANG", "de_DE.UTF-8", "LC_ALL", "C"), 80, 24).environment())
            .containsEntry("LANG", "fr_FR.UTF-8").containsEntry("LC_ALL", "C");
        var blankConfig = snapshot("", List.of(), Map.of("LANG", " "), 100, 80, 24);
        assertThat(LaunchSettings.resolve(blankConfig, "Mac OS X", Map.of("LANG", "de_DE.UTF-8"), 80, 24).environment())
            .containsEntry("LANG", "en_US.UTF-8");
        assertThat(LaunchSettings.resolve(defaults, "Mac OS X", Map.of("LANG", "C", "LC_CTYPE", "UTF-8"), 80, 24).environment())
            .containsEntry("LANG", "C").containsEntry("LC_CTYPE", "UTF-8");
        for (String os : List.of("Windows 11", "Linux")) {
            assertThat(LaunchSettings.resolve(defaults, os, Map.of(), 80, 24).environment()).doesNotContainKey("LANG");
        }
    }

    @Test void directConstructionCopiesCollectionsAndBadInheritedPathStillHasAnErrorLabel() {
        var command = new ArrayList<>(List.of("/bin/custom")); var environment = new HashMap<>(Map.of("A", "one"));
        var settings = new LaunchSettings(command, environment, 5, 2, 0);
        command.set(0, "changed"); environment.put("A", "changed");
        assertThat(settings.command()).containsExactly("/bin/custom");
        assertThat(settings.environment()).containsEntry("A", "one");
        var malformed = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Linux", Map.of("SHELL", "bad\0path"), 150, 45);
        assertThat(malformed.label()).isNotBlank().doesNotContain("\0");
    }

    /** tmux overwrites TERM_PROGRAM in every pane, so the scripts need a marker it leaves alone. */
    @Test void aMarkerTmuxCannotOverwriteIsExportedInEveryMode() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        for (ShellIntegrationMode mode : ShellIntegrationMode.values()) {
            var settings = LaunchSettings.resolve(withShell("/bin/zsh", List.of(), mode), "Mac OS X",
                Map.of("JASPER_TERMINAL", "stale"), 150, 45, dir);
            assertThat(settings.environment()).as("%s", mode).containsEntry("JASPER_TERMINAL", "1");
        }
    }

    /**
     * tmux runs the user's $SHELL per pane and passes its own environment down, so the injection that
     * reaches that shell is the environment kind. Measured: a pane of a server Jasper started does
     * inherit ZDOTDIR and JASPER_SHELL_INTEGRATION.
     */
    @Test void tmuxGetsTheInjectionItsOwnShellWillUse() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        var zsh = new java.util.ArrayList<>(List.of("/opt/homebrew/bin/tmux"));
        var zshEnv = new HashMap<>(Map.of("SHELL", "/bin/zsh"));
        LaunchSettings.inject(zsh, zshEnv, dir);
        assertThat(zsh).as("the tmux command line is never rewritten").containsExactly("/opt/homebrew/bin/tmux");
        assertThat(zshEnv).containsEntry("ZDOTDIR", dir.resolve("zsh").toString());

        var fish = new java.util.ArrayList<>(List.of("tmux"));
        var fishEnv = new HashMap<>(Map.of("SHELL", "/usr/local/bin/fish"));
        LaunchSettings.inject(fish, fishEnv, dir);
        assertThat(fishEnv).containsEntry("XDG_DATA_DIRS", dir.resolve("fish") + ":/usr/local/share:/usr/share");

        // bash's mechanism is --rcfile, an argument tmux never sees, so there is nothing to set.
        var bash = new java.util.ArrayList<>(List.of("tmux"));
        var bashEnv = new HashMap<>(Map.of("SHELL", "/bin/bash"));
        LaunchSettings.inject(bash, bashEnv, dir);
        assertThat(bash).containsExactly("tmux");
        assertThat(bashEnv).containsOnlyKeys("SHELL");

        // No SHELL, or one that is not a path, leaves everything alone rather than guessing.
        var unknown = new java.util.ArrayList<>(List.of("tmux"));
        var unknownEnv = new HashMap<String, String>();
        LaunchSettings.inject(unknown, unknownEnv, dir);
        assertThat(unknownEnv).isEmpty();
    }
}
