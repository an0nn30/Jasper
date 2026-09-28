package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.settings.Applies;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShellProfileSettingTest {
    @Test void theProfileIsReadIntoTheSnapshotAndAppliesToNewPanes() {
        var chosen = ConfigLoader.parse(Path.of("config.toml"), "[terminal.shell]\nprofile = 'wsl:Ubuntu-22.04'\n", false);
        assertThat(chosen.diagnostics()).isEmpty();
        assertThat(chosen.snapshot().terminal().shell().profile()).isEqualTo("wsl:Ubuntu-22.04");
        assertThat(ConfigLoader.parse(Path.of("config.toml"), "", false).snapshot().terminal().shell().profile()).isEmpty();
        assertThat(CoreSettings.TERMINAL_SHELL_PROFILE.applies()).isEqualTo(Applies.NEW_PANES);
        assertThat(CoreSettings.TERMINAL_SHELL_PROFILE.name()).isEqualTo("terminal.shell.profile");
    }

    @Test void aMalformedIdIsReportedAndLeavesTheDefault() {
        for (String bad : new String[]{"'wsl:two words'", "'PowerShell'", "'wsl:'", "7"}) {
            var result = ConfigLoader.parse(Path.of("config.toml"), "[terminal.shell]\nprofile = " + bad + "\n", false);
            assertThat(result.diagnostics()).as(bad).hasSize(1);
            assertThat(result.snapshot().terminal().shell().profile()).as(bad).isEmpty();
        }
    }

    @Test void theOldProgramAndArgsSettingsMeanCustomOnlyWhileNoProfileIsChosen() {
        assertThat(new TerminalConfig.Shell("", List.of()).custom()).isFalse();
        assertThat(new TerminalConfig.Shell("/bin/fish", List.of()).custom()).isTrue();
        assertThat(new TerminalConfig.Shell("", List.of("-i")).custom()).as("args alone kept working before profiles").isTrue();
        assertThat(new TerminalConfig.Shell("/bin/fish", List.of(), "wsl:Ubuntu").custom()).isFalse();
        assertThat(new TerminalConfig.Shell("", List.of(), "custom").custom()).isTrue();
        assertThat(new TerminalConfig.Shell("", List.of())).isEqualTo(new TerminalConfig.Shell("", List.of(), ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new TerminalConfig.Shell("", List.of(), "a" + (char) 0));
    }
}
