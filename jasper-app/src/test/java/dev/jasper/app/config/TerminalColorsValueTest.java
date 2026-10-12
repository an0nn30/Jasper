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
}
