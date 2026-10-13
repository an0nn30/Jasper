package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.settingsui.ThemeImports;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** spec 4.1: the Imported themes group lists each imported theme with what it offers. */
class SettingsAppearanceTest {
    @TempDir Path home;

    @Test void eachImportedThemeSaysWhatItOffers() throws Exception {
        var themes = Files.createDirectories(home.resolve("themes"));
        Files.writeString(themes.resolve("ocean.json"), ThemeTestSupport.uiThemeJson("Ocean"));
        Files.writeString(themes.resolve("dusk.json"), ThemeTestSupport.uiThemeJson("Dusk")
            .replaceAll("(?s),\\s*\"(light|high-contrast)\": \\{[^}]*}", ""));
        Files.writeString(themes.resolve("zenburn.json"), ThemeTestSupport.terminalThemeJson("Zenburn"));
        var library = ThemeLibrary.read(themes, ThemeLibrary.bundledOnly());
        assertThat(library.problems()).isEmpty();
        assertThat(SettingsAppearance.imported(library)).containsExactly(
            new ThemeImports.Imported("dusk", "Dusk", "UI theme · Dark"),
            new ThemeImports.Imported("ocean", "Ocean", "UI theme · Dark and Light, 3 terminal schemes"),
            new ThemeImports.Imported("zenburn", "Zenburn", "Terminal scheme"));
    }
}
