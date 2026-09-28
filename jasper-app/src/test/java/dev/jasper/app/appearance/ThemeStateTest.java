package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalColors;
import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ThemeStateTest {
    private static final Palette SCHEME = new Palette(Color.WHITE, new Color(0x101820), Color.WHITE, Color.GRAY,
        Collections.nCopies(16, Color.RED));
    private static final Theme INSTALLED = new Theme("installed:fixture", "Fixture", true, SCHEME,
        new Theme.Resource("jasper-dark.theme.json"));

    @Test void theConfiguredThemeResolvesWithItsOwnPalette() {
        assertThat(ThemeState.defaults().resolve()).isEqualTo(new ResolvedTheme(Theme.DARK, Theme.DARK.palette()));
        assertThat(ThemeState.defaults().configure(Theme.LIGHT).resolve()).isEqualTo(new ResolvedTheme(Theme.LIGHT, Palette.jasperLight()));
        assertThat(ThemeState.defaults().configure(INSTALLED).resolve()).isEqualTo(new ResolvedTheme(INSTALLED, SCHEME));
    }

    @Test void terminalChoiceResolvesThePaletteIndependentlyOfTheChrome() {
        for (Theme chrome : List.of(Theme.LIGHT, Theme.DARK, INSTALLED)) {
            var base = ThemeState.defaults().configure(chrome);
            assertThat(base.configureTerminal(TerminalColors.MATCH).resolve()).isEqualTo(new ResolvedTheme(chrome, chrome.palette()));
            assertThat(base.configureTerminal(TerminalColors.LIGHT).resolve()).isEqualTo(new ResolvedTheme(chrome, Palette.jasperLight()));
            assertThat(base.configureTerminal(TerminalColors.DARK).resolve()).isEqualTo(new ResolvedTheme(chrome, Palette.jasperDark()));
        }
    }

    @Test void theTerminalChoiceIsTheConfiguredOneAndSurvivesThemeChanges() {
        var configured = ThemeState.defaults().configureTerminal(TerminalColors.LIGHT);
        assertThat(configured.terminalChoice()).isEqualTo(TerminalColors.LIGHT);
        assertThat(configured.configure(Theme.LIGHT).terminalChoice())
            .as("a theme change keeps the terminal choice").isEqualTo(TerminalColors.LIGHT);
        assertThat(configured.configureTerminal(TerminalColors.DARK).terminalChoice()).isEqualTo(TerminalColors.DARK);
    }
}
