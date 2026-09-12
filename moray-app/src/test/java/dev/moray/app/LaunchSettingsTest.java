package dev.moray.app;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LaunchSettingsTest {
    static ConfigSnapshot snapshot(String program, List<String> args, Map<String, String> env, int scrollback,
                                   int columns, int lines) {
        var d = ConfigSnapshot.defaults(); var t = d.terminal();
        return new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.theme(), d.keybindings(),
            columns, lines, new TerminalConfig(new TerminalConfig.Shell(program, args), env, scrollback,
            t.optionAsMeta(), t.cursorShape(), t.cursorBlink(), t.dimInactivePanes(), t.copyOnSelect(), t.bell()));
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

    @Test void directConstructionCopiesCollectionsAndBadInheritedPathStillHasAnErrorLabel() {
        var command = new ArrayList<>(List.of("/bin/custom")); var environment = new HashMap<>(Map.of("A", "one"));
        var settings = new LaunchSettings(command, environment, 5, 2, 0);
        command.set(0, "changed"); environment.put("A", "changed");
        assertThat(settings.command()).containsExactly("/bin/custom");
        assertThat(settings.environment()).containsEntry("A", "one");
        var malformed = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Linux", Map.of("SHELL", "bad\0path"), 150, 45);
        assertThat(malformed.label()).isNotBlank().doesNotContain("\0");
    }
}
