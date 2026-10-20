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
        Files.writeString(themes.resolve("cobalt-ii.json"), ThemeTestSupport.terminalThemeJson("Slate Cobalt"));
        var choices = SettingsAppearance.themeChoices(ThemeLibrary.read(themes, ThemeLibrary.bundledOnly()));
        assertThat(choices.dark()).extracting(InstalledThemes.ThemeOption::id)
            .containsExactly("slate-cobalt", "catppuccin-frappe", "catppuccin-macchiato", "catppuccin-mocha", "ubuntu-yaru", "ocean");
        assertThat(choices.light()).extracting(InstalledThemes.ThemeOption::id).containsExactly("slate-cobalt", "catppuccin-latte", "ubuntu-yaru", "jasper-meadow", "ocean");
        assertThat(choices.names()).containsOnly(Map.entry("slate-cobalt", "Slate Cobalt (slate-cobalt)"), Map.entry("cobalt-ii", "Slate Cobalt (cobalt-ii)"),
            Map.entry("catppuccin-latte", "Catppuccin Latte"), Map.entry("catppuccin-frappe", "Catppuccin Frapp\u00e9"),
            Map.entry("catppuccin-macchiato", "Catppuccin Macchiato"), Map.entry("catppuccin-mocha", "Catppuccin Mocha"),
            Map.entry("ubuntu-yaru", "Ubuntu Yaru"), Map.entry("jasper-meadow", "Jasper Meadow Light"), Map.entry("ocean", "Ocean"), Map.entry("zenburn", "Zenburn"));
    }
}
