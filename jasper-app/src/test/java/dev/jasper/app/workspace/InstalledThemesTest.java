package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeFixtures;
import dev.jasper.app.application.ConfigurationTestSupport;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class InstalledThemesTest {
    @TempDir Path home;
    private ConfigService service;
    private ConfigurationTestSupport controller;

    @AfterEach void cleanup() throws Exception {
        edt(() -> { if (controller != null) controller.close(); new ThemeController(); });
        if (service != null) service.close();
    }

    private Path config() { return home.resolve("config.toml"); }
    private Path themes() { return home.resolve("themes"); }

    /** As start-up: the first scan on the configuration worker, then the controller on the EDT. */
    private ThemeController start(String text) throws Exception {
        Files.writeString(config(), text);
        service = new ConfigService(config(), false);
        var catalog = ThemeCatalog.beside(config());
        service.watchWith(catalog::scan).get();
        ThemeController[] themes = new ThemeController[1];
        edt(() -> {
            themes[0] = new ThemeController(catalog.latest().find(service.initialState().snapshot().theme()).orElse(Theme.DARK));
            controller = new ConfigurationTestSupport(themes[0], service, catalog);
        });
        return themes[0];
    }

    private void reload(String text) throws Exception {
        Files.writeString(config(), text);
        reload();
    }

    private void reload() throws Exception {
        service.reload().get();
        edt(() -> {});
    }

    @Test void aThemeInstalledInTheFolderAppliesByNameWithItsConsoleColours() throws Exception {
        ThemeFixtures.plugin(themes().resolve("fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        var themes = start("[ui.theme]\nname = 'fixture dark'\n");
        edt(() -> {
            assertThat(themes.current().chrome().name()).isEqualTo("Fixture Dark");
            assertThat(themes.current().palette().background()).isEqualTo(new Color(0x101820));
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(new Color(0x123456));
            assertThat(controller.shown().diagnostics()).isEmpty();
        });
        reload("[ui.theme]\nname = 'fixture dark'\nterminal = 'light'\n");
        edt(() -> {
            assertThat(themes.current().chrome().name()).isEqualTo("Fixture Dark");
            assertThat(themes.current().palette()).isEqualTo(Palette.jasperLight());
        });
    }

    @Test void anUnknownNameKeepsTheCurrentThemeAndListsWhatIsAvailable() throws Exception {
        ThemeFixtures.plugin(themes().resolve("fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        var themes = start("[ui.theme]\nname = 'Fixture Dark'\n");
        reload("[ui.theme]\nname = 'Nope'\n");
        edt(() -> {
            assertThat(themes.current().chrome().name()).isEqualTo("Fixture Dark");
            assertThat(controller.shown().diagnostics()).singleElement().satisfies(problem -> {
                assertThat(problem.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
                assertThat(problem.key()).isEqualTo("ui.theme.name");
                assertThat(problem.message()).contains("\"Nope\"").contains("keeping Fixture Dark")
                    .contains("Available: Fixture Dark, IntelliJ Light, Jasper Dark.");
            });
        });
    }

    @Test void anUnknownNameAtStartUpUsesJasperDark() throws Exception {
        var themes = start("[ui.theme]\nname = ''\n");
        edt(() -> {
            assertThat(themes.current().chrome()).isEqualTo(Theme.DARK);
            assertThat(controller.shown().diagnostics()).singleElement().satisfies(problem ->
                assertThat(problem.message()).contains("Unknown theme \"\"").contains("keeping Jasper Dark"));
        });
    }

    @Test void reloadRescansTheFolderSoThemesCanBeAddedAndRemovedLive() throws Exception {
        var themes = start("[ui.theme]\nname = 'Fixture Dark'\n");
        edt(() -> assertThat(themes.current().chrome()).isEqualTo(Theme.DARK));
        ThemeFixtures.plugin(themes().resolve("fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        reload();
        edt(() -> {
            assertThat(themes.current().chrome().name()).isEqualTo("Fixture Dark");
            assertThat(controller.shown().diagnostics()).isEmpty();
        });
        Files.delete(themes().resolve("fixture.jar"));
        Files.writeString(themes().resolve("broken.jar"), "not a zip");
        reload();
        edt(() -> {
            assertThat(themes.current().chrome().name()).as("the theme showing stays").isEqualTo("Fixture Dark");
            assertThat(controller.shown().diagnostics()).extracting(ConfigDiagnostic::message)
                .anySatisfy(message -> assertThat(message).contains("broken.jar").contains("cannot be read"))
                .anySatisfy(message -> assertThat(message).contains("Unknown theme \"Fixture Dark\""));
        });
    }
}
