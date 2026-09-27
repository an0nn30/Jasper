package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.terminal.config.Palette;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class TerminalColorsThemeTest {
    @Test void aTerminalOnlyChangeNotifiesOnceWithoutReinstallingTheLookAndFeel() {
        var installs = new ArrayList<Theme>();
        var themes = new ThemeController(Theme.LIGHT, theme -> { installs.add(theme); return ThemeTestSupport.install(theme); });
        try {
            var chromeFlags = new ArrayList<Boolean>();
            var palettes = new ArrayList<Palette>();
            themes.subscribe((theme, chromeChanged) -> { chromeFlags.add(chromeChanged); palettes.add(theme.palette()); });
            installs.clear(); chromeFlags.clear(); palettes.clear();
            themes.selectTerminalColors(TerminalColors.DARK);
            assertThat(installs).isEmpty();
            assertThat(chromeFlags).containsExactly(false);
            assertThat(palettes).containsExactly(Palette.jasperDark());
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(Theme.LIGHT, Palette.jasperDark()));
            assertThat(themes.terminalColors()).isEqualTo(TerminalColors.DARK);
            themes.selectTerminalColors(TerminalColors.DARK);
            assertThat(chromeFlags).as("repeating the same choice is quiet").hasSize(1);
        } finally { new ThemeController(); }
    }

    @Test void changingTheThemeKeepsAFixedTerminalPalette() {
        var themes = new ThemeController(Theme.DARK, ThemeTestSupport::install);
        try {
            themes.configure(Theme.DARK, TerminalColors.DARK, UiFontConfig.defaults());
            var chromeFlags = new ArrayList<Boolean>();
            themes.subscribe((theme, chromeChanged) -> chromeFlags.add(chromeChanged));
            chromeFlags.clear();
            themes.configure(Theme.LIGHT, TerminalColors.DARK, UiFontConfig.defaults());
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(Theme.LIGHT, Palette.jasperDark()));
            assertThat(chromeFlags).containsExactly(true);
            themes.configure(Theme.DARK);
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(Theme.DARK, Palette.jasperDark()));
            themes.configure(Theme.LIGHT, TerminalColors.MATCH, UiFontConfig.defaults());
            assertThat(themes.current()).as("MATCH follows the configured theme")
                .isEqualTo(new ResolvedTheme(Theme.LIGHT, Palette.jasperLight()));
        } finally { new ThemeController(); }
    }

}
