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
        var themes = new ThemeController(Theme.INTELLIJ_LIGHT, theme -> { installs.add(theme); return ThemeTestSupport.install(theme); });
        try {
            var chromeFlags = new ArrayList<Boolean>();
            var palettes = new ArrayList<Palette>();
            themes.subscribe((theme, chromeChanged) -> { chromeFlags.add(chromeChanged); palettes.add(theme.palette()); });
            installs.clear(); chromeFlags.clear(); palettes.clear();
            themes.configureTerminal(TerminalColors.DARK);
            assertThat(installs).isEmpty();
            assertThat(chromeFlags).containsExactly(false);
            assertThat(palettes).containsExactly(Theme.DARK.palette());
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(dev.jasper.app.appearance.ThemeManager.generated(Theme.INTELLIJ_LIGHT), dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK)));
            assertThat(themes.workspaceChoice().name()).isEqualTo("Jasper Dark");
            themes.configureTerminal(TerminalColors.DARK);
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
            themes.configure(Theme.INTELLIJ_LIGHT, TerminalColors.DARK, UiFontConfig.defaults());
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(dev.jasper.app.appearance.ThemeManager.generated(Theme.INTELLIJ_LIGHT), dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK)));
            assertThat(chromeFlags).containsExactly(true);
            themes.configure(Theme.DARK);
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK), dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK)));
            themes.configure(Theme.INTELLIJ_LIGHT, TerminalColors.MATCH, UiFontConfig.defaults());
            assertThat(themes.current()).as("MATCH follows the configured theme")
                .isEqualTo(new ResolvedTheme(dev.jasper.app.appearance.ThemeManager.generated(Theme.INTELLIJ_LIGHT), dev.jasper.app.appearance.ThemeManager.generated(Theme.INTELLIJ_LIGHT)));
        } finally { new ThemeController(); }
    }


    @Test void themeOnlyAndFontOnlyChangesKeepANamedPalette() {
        var named = ThemeManager.generated(Theme.DARCULA).terminal();
        var themes = new ThemeController(Theme.DARK, ThemeTestSupport::install);
        try {
            themes.configure(Theme.DARK, new dev.jasper.app.config.WorkspaceThemeChoice("Darcula"), Theme.DARCULA, UiFontConfig.defaults());
            assertThat(themes.current().palette()).isEqualTo(named);
            themes.configure(Theme.INTELLIJ_LIGHT);
            assertThat(themes.current().palette()).isEqualTo(named);
            themes.configure(Theme.DARK, UiFontConfig.defaults());
            assertThat(themes.current().palette()).isEqualTo(named);
        } finally { new ThemeController(); }
    }
}
