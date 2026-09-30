package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TerminalColorsValueTest {
    @Test void builtInsParseInAnyCaseAndOtherTextNamesATheme() {
        assertThat(TerminalColors.parse(" DARK ")).isEqualTo(TerminalColors.DARK).satisfies(c -> assertThat(c.named()).isFalse());
        assertThat(TerminalColors.parse("Match")).isEqualTo(TerminalColors.MATCH);
        assertThat(TerminalColors.parse("")).isEqualTo(TerminalColors.MATCH);
        assertThat(TerminalColors.parse("  Dracula ")).isEqualTo(new TerminalColors("Dracula"))
            .satisfies(c -> { assertThat(c.named()).isTrue(); assertThat(c.name()).isEqualTo("Dracula"); });
        assertThat(TerminalColors.builtIns()).containsExactly(TerminalColors.MATCH, TerminalColors.LIGHT, TerminalColors.DARK);
        assertThat(TerminalColors.reserved(" Light ")).isTrue();
        assertThat(TerminalColors.reserved("Gruvbox Dark")).isFalse();
        assertThat(TerminalColors.normalize(" LIGHT ")).isEqualTo("light");
        assertThat(TerminalColors.normalize(" Nord ")).isEqualTo("Nord");
        assertThatThrownBy(() -> new TerminalColors("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void theSettingKeepsTheNameAndTheSnapshotParsesIt() {
        var snapshot = ConfigLoader.parse(java.nio.file.Path.of("config.toml"), "[terminal]\ncolors = ' Gruvbox Dark '\n", true);
        assertThat(snapshot.values().get(CoreSettings.TERMINAL_COLORS)).isEqualTo("Gruvbox Dark");
        assertThat(snapshot.snapshot().terminalColors()).isEqualTo(new TerminalColors("Gruvbox Dark"));
        assertThat(ConfigLoader.parse(java.nio.file.Path.of("config.toml"), "[terminal]\ncolors = 'DARK'\n", true)
            .snapshot().terminalColors()).isEqualTo(TerminalColors.DARK);
    }
}
