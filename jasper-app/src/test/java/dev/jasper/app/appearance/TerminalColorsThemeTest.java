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
    @Test void moduleTerminalAdapterPreservesEveryFieldAndAlpha() {
        var ansi = java.util.stream.IntStream.range(0,16).mapToObj(i -> new java.awt.Color(i, i+20, i+40, i+100)).toList();
        var source = new dev.jasper.ui.theme.TerminalColors(new java.awt.Color(1,2,3,4), new java.awt.Color(5,6,7,8),
            new java.awt.Color(9,10,11,12), new java.awt.Color(13,14,15,16), ansi);
        var actual = TerminalPaletteAdapter.toTerminal(source);
        assertThat(new dev.jasper.ui.theme.TerminalColors(actual.background(), actual.foreground(), actual.cursor(), actual.selection(), actual.ansi())).isEqualTo(source);
    }

    @Test void aTerminalOnlyChangeNotifiesOnceWithoutReinstallingTheLookAndFeel() {
        var installs = new ArrayList<Theme>();
        var themes = new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, theme -> { installs.add(theme); return ThemeTestSupport.install(theme); });
        try {
            var chromeFlags = new ArrayList<Boolean>();
            var palettes = new ArrayList<Palette>();
            themes.subscribe((theme, chromeChanged) -> { chromeFlags.add(chromeChanged); palettes.add(theme.palette()); });
            installs.clear(); chromeFlags.clear(); palettes.clear();
            themes.configureTerminal(TerminalColors.DARK);
            assertThat(installs).isEmpty();
            assertThat(chromeFlags).containsExactly(false);
            assertThat(palettes).containsExactly(Theme.DARK.palette());
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(dev.jasper.app.appearance.ThemeManager.generated(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT), dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK)));
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
            themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, TerminalColors.DARK, UiFontConfig.defaults());
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(dev.jasper.app.appearance.ThemeManager.generated(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT), dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK)));
            assertThat(chromeFlags).containsExactly(true);
            themes.configure(Theme.DARK);
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK), dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK)));
            themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, TerminalColors.MATCH, UiFontConfig.defaults());
            assertThat(themes.current()).as("MATCH follows the configured theme")
                .isEqualTo(new ResolvedTheme(dev.jasper.app.appearance.ThemeManager.generated(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT), dev.jasper.app.appearance.ThemeManager.generated(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT)));
        } finally { new ThemeController(); }
    }


    @Test void themeOnlyAndFontOnlyChangesKeepANamedPalette() {
        var named = TerminalPaletteAdapter.toTerminal(ThemeManager.generated(dev.jasper.app.appearance.ThemeFixtures.DARCULA).terminal());
        var themes = new ThemeController(Theme.DARK, ThemeTestSupport::install);
        try {
            themes.configure(Theme.DARK, new dev.jasper.app.config.WorkspaceThemeChoice("Darcula"), dev.jasper.app.appearance.ThemeFixtures.DARCULA, UiFontConfig.defaults());
            assertThat(themes.current().palette()).isEqualTo(named);
            themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
            assertThat(themes.current().palette()).isEqualTo(named);
            themes.configure(Theme.DARK, UiFontConfig.defaults());
            assertThat(themes.current().palette()).isEqualTo(named);
        } finally { new ThemeController(); }
    }
}
