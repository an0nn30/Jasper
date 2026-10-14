package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.settingsui.InstalledThemes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Appearance page's choices over the library. */
class SettingsAppearanceTest {
    @TempDir Path home;

    @Test void theChoicesNameEveryInstalledThemeEvenOneWithoutAnyUiVariant() throws Exception {
        var themes = Files.createDirectories(home.resolve("themes"));
        Files.writeString(themes.resolve("ocean.json"), ThemeTestSupport.uiThemeJson("Ocean"));
        Files.writeString(themes.resolve("zenburn.json"), ThemeTestSupport.terminalThemeJson("Zenburn"));
        Files.writeString(themes.resolve("steel-ii.json"), ThemeTestSupport.terminalThemeJson("Slate Steel"));
        var choices = SettingsAppearance.themeChoices(ThemeLibrary.read(themes, ThemeLibrary.bundledOnly()));
        assertThat(choices.dark()).extracting(InstalledThemes.ThemeOption::id)
            .containsExactly("slate-steel", "catppuccin-frappe", "catppuccin-macchiato", "catppuccin-mocha", "ocean");
        assertThat(choices.light()).extracting(InstalledThemes.ThemeOption::id).containsExactly("slate-steel", "catppuccin-latte", "ocean");
        assertThat(choices.names()).containsOnly(Map.entry("slate-steel", "Slate Steel (slate-steel)"), Map.entry("steel-ii", "Slate Steel (steel-ii)"),
            Map.entry("catppuccin-latte", "Catppuccin Latte"), Map.entry("catppuccin-frappe", "Catppuccin Frapp\u00e9"),
            Map.entry("catppuccin-macchiato", "Catppuccin Macchiato"), Map.entry("catppuccin-mocha", "Catppuccin Mocha"),
            Map.entry("ocean", "Ocean"), Map.entry("zenburn", "Zenburn"));
    }
}
