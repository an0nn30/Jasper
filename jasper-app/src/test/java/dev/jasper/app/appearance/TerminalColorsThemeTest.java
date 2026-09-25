package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.ThemeStyle;
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
        var installs = new ArrayList<BuiltinTheme>();
        var themes = new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT, theme -> { installs.add(theme); return ThemeTestSupport.install(theme); });
        try {
            var chromeFlags = new ArrayList<Boolean>();
            var palettes = new ArrayList<Palette>();
            themes.subscribe((theme, chromeChanged) -> { chromeFlags.add(chromeChanged); palettes.add(theme.palette()); });
            installs.clear(); chromeFlags.clear(); palettes.clear();
            themes.selectTerminalColors(TerminalColors.DARK);
            assertThat(installs).isEmpty();
            assertThat(chromeFlags).containsExactly(false);
            assertThat(palettes).containsExactly(Palette.jasperDark());
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, Palette.jasperDark()));
            assertThat(themes.terminalColors()).isEqualTo(TerminalColors.DARK);
            themes.selectTerminalColors(TerminalColors.DARK);
            assertThat(chromeFlags).as("repeating the same choice is quiet").hasSize(1);
        } finally { new ThemeController(); }
    }

    @Test void changingTheUiVariantKeepsAFixedTerminalPalette() {
        var themes = new ThemeController(ThemeStyle.MODERN, Appearance.DARK, ThemeTestSupport::install);
        try {
            themes.configure(Appearance.DARK, TerminalColors.DARK, UiFontConfig.defaults());
            var chromeFlags = new ArrayList<Boolean>();
            themes.subscribe((theme, chromeChanged) -> chromeFlags.add(chromeChanged));
            chromeFlags.clear();
            themes.configure(Appearance.LIGHT, TerminalColors.DARK, UiFontConfig.defaults());
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, Palette.jasperDark()));
            assertThat(chromeFlags).containsExactly(true);
            themes.selectAppearance(Appearance.DARK);
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(BuiltinTheme.DARK, Palette.jasperDark()));
            themes.configure(Appearance.LIGHT, TerminalColors.MATCH, UiFontConfig.defaults());
            assertThat(themes.current()).as("MATCH follows the effective chrome, including the View override")
                .isEqualTo(new ResolvedTheme(BuiltinTheme.DARK, Palette.jasperDark()));
        } finally { new ThemeController(); }
    }

    @Test void retroKeepsItsPaletteForEveryTerminalChoice() {
        var themes = new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT, ThemeTestSupport::install);
        try {
            for (var choice : TerminalColors.values()) {
                themes.configure(Appearance.DARK, choice, UiFontConfig.defaults());
                themes.selectTerminalColors(TerminalColors.LIGHT);
                assertThat(themes.current().palette()).isEqualTo(BuiltinTheme.RETRO.palette());
                assertThat(themes.terminalColors()).isEqualTo(TerminalColors.MATCH);
            }
        } finally { new ThemeController(); }
    }
}
