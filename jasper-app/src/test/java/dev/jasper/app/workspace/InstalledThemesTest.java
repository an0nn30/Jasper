package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeFixtures;
import dev.jasper.app.appearance.ThemeTestSupport;
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

    @Test void themeOnlyPollUpdatesEveryOwnerAndAppearanceObserverWithoutReinstallingUnchangedThemes() throws Exception {
        Path file = themes().resolve("fixture.toml"); Files.createDirectories(file.getParent());
        String original = "name='Live'\nextends='Jasper Dark'\n[colors]\nbackground='#112233'\n";
        Files.writeString(file, original);
        String configText = "ui.theme.workspace='Live'\n"; Files.writeString(config(), configText);
        var worker = new dev.jasper.app.testsupport.ConfigPollWorker();
        service = new ConfigService(config(), false, worker, javax.swing.SwingUtilities::invokeLater);
        var catalog = ThemeCatalog.beside(config());
        service.watchChanges(() -> { var previous = catalog.latest(); return !catalog.scan().equals(previous); }).get();
        ThemeController[] appearance = new ThemeController[1]; WindowContent[] owners = new WindowContent[2];
        int[] events = {0}; dev.jasper.app.lifecycle.Subscription[] listening = new dev.jasper.app.lifecycle.Subscription[1];
        try {
            edt(() -> {
                appearance[0] = new ThemeController(); controller = new ConfigurationTestSupport(appearance[0], service, catalog);
                for (int i = 0; i < owners.length; i++) {
                    owners[i] = DesktopTestSupport.content(DesktopTestSupport.launcher(new java.util.ArrayDeque<>()), appearance[0]);
                    controller.register(owners[i]);
                }
                listening[0] = appearance[0].subscribe((resolved, refresh) -> events[0]++);
            });
            edt(() -> events[0] = 0);
            var stamp = Files.getLastModifiedTime(file);
            Files.writeString(file, original.replace("#112233", "#445566")); Files.setLastModifiedTime(file, stamp);
            worker.poll(); edt(() -> {
                for (var owner : owners) assertThat(owner.theme().workspace().terminal().background()).isEqualTo(new Color(0x445566));
                assertThat(events[0]).isEqualTo(1);
            });
            worker.poll(); edt(() -> assertThat(events[0]).isEqualTo(1));
            Files.writeString(file, "name='Live'\nextends='Missing'\n"); worker.poll();
            edt(() -> {
                assertThat(controller.shown().diagnostics()).anyMatch(problem -> problem.file().equals(file) && problem.line() == 2);
                for (var owner : owners) assertThat(owner.theme().matching()).isTrue();
            });
            Files.delete(file); worker.poll(); Files.writeString(file, original); worker.poll();
            edt(() -> {
                for (var owner : owners) assertThat(owner.theme().workspace().terminal().background()).isEqualTo(new Color(0x112233));
                assertThat(controller.snapshot().workspaceTheme().name()).isEqualTo("Live");
                assertThat(controller.shown().diagnostics()).isEmpty();
            });
            assertThat(Files.readString(config())).isEqualTo(configText);
        } finally { edt(() -> { if (listening[0] != null) listening[0].close(); for (var owner : owners) if (owner != null) owner.close(); }); }
    }

    @Test void aThemeInstalledInTheFolderAppliesByNameWithItsConsoleColours() throws Exception {
        ThemeFixtures.nativePlugin(themes().resolve("fixture.toml"), "Fixture Dark", true, "#123456", "101820");
        var themes = start("[ui.theme]\nname = 'fixture dark'\n");
        edt(() -> {
            assertThat(themes.current().chrome().name()).isEqualTo("Fixture Dark");
            assertThat(themes.current().palette().background()).isEqualTo(new Color(0x101820));
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(new Color(0x123456));
            assertThat(controller.shown().diagnostics()).isEmpty();
        });
        reload("[ui.theme]\nname = 'fixture dark'\n[terminal]\ncolors = 'light'\n");
        edt(() -> {
            assertThat(themes.current().chrome().name()).isEqualTo("Fixture Dark");
            assertThat(themes.current().palette()).isEqualTo(Palette.jasperLight());
        });
    }

    @Test void anUnknownNameUsesSafeDarkFallbackAndListsWhatIsAvailable() throws Exception {
        ThemeFixtures.nativePlugin(themes().resolve("fixture.toml"), "Fixture Dark", true, "#123456", "101820");
        var themes = start("[ui.theme]\nname = 'Fixture Dark'\n");
        reload("[ui.theme]\nname = 'Nope'\n");
        edt(() -> {
            assertThat(themes.choice()).isSameAs(Theme.DARK);
            assertThat(controller.shown().diagnostics()).singleElement().satisfies(problem -> {
                assertThat(problem.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
                assertThat(problem.key()).isEqualTo("ui.theme.name");
                assertThat(problem.message()).contains("\"Nope\"").contains("using Jasper Dark")
                    .contains("Available:", "Fixture Dark", "IntelliJ Light", "Jasper Dark", "Jasper Light");
            });
        });
    }

    @Test void missingLightChromeAndWorkspaceKeepRequestsAndRecoverAfterRepair() throws Exception {
        ThemeFixtures.nativePlugin(themes().resolve("fixture.toml"), "Fixture Light", false, "#f1f2f3", "eeeeee");
        var themes = start("ui.theme.name='Fixture Light'\nui.theme.workspace='Darcula'\n");
        Files.writeString(themes().resolve("fixture.toml"), "broken archive");
        ThemeFixtures.nativePlugin(themes().resolve("shadow.toml"), "Jasper Light", true, "#123456", "101820");
        reload("ui.theme.name='Fixture Light'\nui.theme.workspace='Missing'\n");
        edt(() -> {
            assertThat(themes.choice()).isSameAs(Theme.JASPER_LIGHT);
            assertThat(themes.current().workspace()).isSameAs(themes.current().chrome());
            assertThat(themes.workspaceChoice().name()).isEqualTo("Missing");
            assertThat(controller.snapshot().theme()).isEqualTo("Fixture Light");
            assertThat(controller.snapshot().workspaceTheme().name()).isEqualTo("Missing");
            assertThat(controller.shown().diagnostics()).extracting(ConfigDiagnostic::key)
                .contains("ui.theme.name", "ui.theme.workspace");
            assertThat(controller.shown().diagnostics()).anySatisfy(problem -> {
                assertThat(problem.file().getFileName().toString()).isEqualTo("fixture.toml");
                assertThat(problem.message()).contains("Unexpected");
            });
        });
        ThemeFixtures.nativePlugin(themes().resolve("fixture.toml"), "Fixture Light", false, "#f1f2f3", "eeeeee");
        ThemeFixtures.nativePlugin(themes().resolve("workspace.toml"), "Missing", true, "#123456", "101820");
        reload();
        edt(() -> {
            assertThat(themes.choice().name()).isEqualTo("Fixture Light");
            assertThat(themes.current().workspace().name()).isEqualTo("Missing");
            assertThat(controller.shown().diagnostics()).isEmpty();
        });
    }

    @Test void canonicalLightIsANativeNameRatherThanTheOldBuiltInAlias() throws Exception {
        ThemeFixtures.nativePlugin(themes().resolve("fixture.toml"), "Light", true, "#123456", "101820");
        var themes = start("ui.theme.workspace='  Light  '\n");
        edt(() -> {
            assertThat(themes.current().workspace().name()).isEqualTo("Light");
            assertThat(themes.current().palette().background()).isEqualTo(new Color(0x101820));
            assertThat(controller.shown().diagnostics()).isEmpty();
        });
    }

    @Test void anUnknownNameAtStartUpUsesJasperDark() throws Exception {
        var themes = start("[ui.theme]\nname = ''\n");
        edt(() -> {
            assertThat(themes.current().chrome()).isEqualTo(dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK));
            assertThat(controller.shown().diagnostics()).singleElement().satisfies(problem ->
                assertThat(problem.message()).contains("Unknown theme \"\"").contains("using Jasper Dark"));
        });
    }

    @Test void aThemeThatFailsToInstallNeverStopsConfigurationAndIsReportedInstead() throws Exception {
        ThemeFixtures.nativePlugin(themes().resolve("fixture.toml"), "Fixture Dark", true, "#123456", "101820");
        Files.writeString(config(), "[ui.theme]\nname = 'Fixture Dark'\n");
        service = new ConfigService(config(), false);
        var catalog = ThemeCatalog.beside(config());
        service.watchWith(catalog::scan).get();
        edt(() -> {
            var themes = new ThemeController(theme -> !theme.name().equals("Fixture Dark") && ThemeTestSupport.install(theme));
            controller = new ConfigurationTestSupport(themes, service, catalog);
            assertThat(themes.current().chrome()).isEqualTo(dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK));
            assertThat(controller.shown().diagnostics()).singleElement().satisfies(problem -> {
                assertThat(problem.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
                assertThat(problem.key()).isEqualTo("ui.theme.name");
                assertThat(problem.message()).contains("Could not apply theme \"Fixture Dark\"").contains("keeping Jasper Dark");
            });
        });
    }

    @Test void reloadRescansTheFolderSoThemesCanBeAddedAndRemovedLive() throws Exception {
        var themes = start("[ui.theme]\nname = 'Fixture Dark'\n");
        edt(() -> assertThat(themes.current().chrome()).isEqualTo(dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK)));
        ThemeFixtures.nativePlugin(themes().resolve("fixture.toml"), "Fixture Dark", true, "#123456", "101820");
        reload();
        edt(() -> {
            assertThat(themes.current().chrome().name()).isEqualTo("Fixture Dark");
            assertThat(controller.shown().diagnostics()).isEmpty();
        });
        Files.delete(themes().resolve("fixture.toml"));
        Files.writeString(themes().resolve("broken.toml"), "not a zip");
        reload();
        edt(() -> {
            assertThat(themes.choice()).isSameAs(Theme.DARK);
            assertThat(controller.shown().diagnostics()).extracting(ConfigDiagnostic::message)
                .anySatisfy(message -> assertThat(message).contains("broken.toml").contains("Unexpected"))
                .anySatisfy(message -> assertThat(message).contains("Unknown theme \"Fixture Dark\""));
        });
    }
}
