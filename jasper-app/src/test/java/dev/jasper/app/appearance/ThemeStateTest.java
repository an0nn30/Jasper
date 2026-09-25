package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.terminal.config.Palette;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeStateTest {
    @Test void savedVariantResolvesToItsBuiltInThemeAndPalette() {
        assertThat(ThemeState.defaults().resolve()).isEqualTo(new ResolvedTheme(BuiltinTheme.DARK, Palette.jasperDark()));
        assertThat(ThemeState.defaults().configure(Appearance.LIGHT).resolve())
            .isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, Palette.jasperLight()));
    }

    @Test void temporaryChoiceOverridesSavedUntilTheSavedVariantChanges() {
        var chosen = ThemeState.defaults().choose(Appearance.LIGHT);
        assertThat(chosen.resolve().chrome()).isEqualTo(BuiltinTheme.LIGHT);
        assertThat(chosen.configure(Appearance.DARK).choice()).isEqualTo(Appearance.LIGHT);
        assertThat(chosen.configure(Appearance.LIGHT).override()).isNull();
        assertThat(chosen.configure(Appearance.LIGHT).choose(Appearance.DARK).resolve().palette())
            .isEqualTo(Palette.jasperDark());
    }

    @Test void terminalChoiceResolvesThePaletteIndependentlyOfTheChrome() {
        for (Appearance ui : Appearance.values()) {
            var base = ThemeState.defaults().configure(ui);
            BuiltinTheme chrome = BuiltinTheme.of(ui);
            assertThat(base.configureTerminal(TerminalColors.MATCH).resolve()).isEqualTo(new ResolvedTheme(chrome, chrome.palette()));
            assertThat(base.configureTerminal(TerminalColors.LIGHT).resolve()).isEqualTo(new ResolvedTheme(chrome, Palette.jasperLight()));
            assertThat(base.configureTerminal(TerminalColors.DARK).resolve()).isEqualTo(new ResolvedTheme(chrome, Palette.jasperDark()));
        }
    }

    @Test void temporaryTerminalChoiceOverridesSavedUntilTheSavedValueChanges() {
        var chosen = ThemeState.defaults().chooseTerminal(TerminalColors.LIGHT);
        assertThat(chosen.terminalChoice()).isEqualTo(TerminalColors.LIGHT);
        assertThat(chosen.configureTerminal(TerminalColors.MATCH).terminalChoice())
            .as("rewriting the same saved value keeps the session choice").isEqualTo(TerminalColors.LIGHT);
        assertThat(chosen.configureTerminal(TerminalColors.DARK).terminalOverride()).isNull();
        assertThat(chosen.configureTerminal(TerminalColors.DARK).terminalChoice()).isEqualTo(TerminalColors.DARK);
        assertThat(chosen.configure(Appearance.LIGHT).terminalChoice())
            .as("a UI variant change keeps the terminal choice").isEqualTo(TerminalColors.LIGHT);
    }
}
