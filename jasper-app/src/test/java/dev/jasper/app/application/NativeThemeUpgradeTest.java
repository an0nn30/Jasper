package dev.jasper.app.application;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.ConfigService;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;

class NativeThemeUpgradeTest {
    @TempDir Path home;
    @Test void convertedSelectionAndFailedInputSurviveNativeOnlyStartup() throws Exception {
        Path config = home.resolve("config.toml"); Files.writeString(config, "ui.theme.name='Example'\nui.theme.workspace='Jasper Light'\n");
        ThemeFixtures.plugin(home.resolve("themes/a.jar"), "Example", true, "#123456", "010203");
        Files.writeString(home.resolve("themes/z.toml"), "name='Example'\nextends='Jasper Light'\n");
        Path bad = Files.writeString(home.resolve("themes/bad.jar"), "invalid");
        var warnings = ThemeUpgrade.prepare(config, false);
        assertThat(warnings).anyMatch(p -> p.file().equals(bad));
        var catalog = ThemeCatalog.beside(config); catalog.scan();
        assertThat(catalog.latest().find("Nord")).isPresent();
        try (var service = new ConfigService(config, false)) {
            assertThat(service.initialState().snapshot().theme()).isEqualTo("Example 2");
            edt(() -> {
                var themes = new ThemeController(t -> true);
                var controller = new ConfigurationController(themes, service, catalog);
                assertThat(themes.choice().palette().background()).isEqualTo(new java.awt.Color(0x010203));
                assertThat(themes.current().palette()).isEqualTo(Theme.JASPER_LIGHT.palette()); controller.close();
            });
        }
        Files.writeString(bad, "changed after marker");
        assertThat(ThemeUpgrade.prepare(config, false)).anyMatch(p -> p.message().contains("Import"));
        assertThat(catalog.scan().find("Example 2").orElseThrow().palette().background()).isEqualTo(new java.awt.Color(0x010203));
    }
}
