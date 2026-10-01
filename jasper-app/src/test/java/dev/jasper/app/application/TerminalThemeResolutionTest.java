package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.terminalthemes.TerminalThemeCatalog;
import dev.jasper.terminal.config.Palette;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TerminalThemeResolutionTest {
    @TempDir Path dir;

    @ParameterizedTest
    @ValueSource(strings = {"IntelliJ_Light", "iNtElLiJ_lIgHt"})
    void nativeWorkspaceNameWinsAndWarnsAboutTheShadowedLegacySource(String legacyName) throws Exception {
        Path file = dir.resolve("config.toml");
        Files.writeString(file, "[ui.theme]\nworkspace = 'IntelliJ Light'\n");
        Files.createDirectories(dir.resolve("terminal-themes"));
        Path legacyFile = dir.resolve("terminal-themes/" + legacyName + ".toml");
        Files.writeString(legacyFile,
            "[colors.primary]\nbackground = '#010203'\nforeground = '#eeeeee'\n");
        var legacy = TerminalThemeCatalog.beside(file);
        assertThat(legacy.scan().find("IntelliJ Light")).isPresent();
        var worker = Executors.newSingleThreadScheduledExecutor();
        try (var service = new ConfigService(file, true, worker, SwingUtilities::invokeLater)) {
            edt(() -> {
                var themes = new ThemeController(theme -> true);
                var controller = new ConfigurationController(themes, service, ThemeCatalog.builtIn(),
                    legacy, path -> {});
                assertThat(themes.current().palette()).isEqualTo(dev.jasper.app.appearance.Theme.INTELLIJ_LIGHT.palette());
                assertThat(controller.shown().diagnostics()).singleElement().satisfies(problem -> {
                    assertThat(problem.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
                    assertThat(problem.file()).isEqualTo(legacyFile);
                    assertThat(problem.key()).isEqualTo("ui.theme.workspace");
                    assertThat(problem.message()).contains("Alacritty", legacyFile.toString(),
                        "IntelliJ Light", "native", "using");
                });
                controller.close();
            });
        } finally { worker.shutdownNow(); }
    }

    @Test void nativeWorkspaceWithoutALegacyCollisionDoesNotWarn() throws Exception {
        Path file = dir.resolve("config.toml");
        Files.writeString(file, "[ui.theme]\nworkspace = 'IntelliJ Light'\n");
        var legacy = TerminalThemeCatalog.bundledOnly();
        assertThat(legacy.latest().find("IntelliJ Light")).isEmpty();
        var worker = Executors.newSingleThreadScheduledExecutor();
        try (var service = new ConfigService(file, true, worker, SwingUtilities::invokeLater)) {
            edt(() -> {
                var themes = new ThemeController(theme -> true);
                var controller = new ConfigurationController(themes, service, ThemeCatalog.builtIn(),
                    legacy, path -> {});
                assertThat(themes.current().palette()).isEqualTo(dev.jasper.app.appearance.Theme.INTELLIJ_LIGHT.palette());
                assertThat(controller.shown().diagnostics()).isEmpty();
                controller.close();
            });
        } finally { worker.shutdownNow(); }
    }

    @Test void aNamedThemeColoursTheTerminalAndAnUnknownOneWarnsAndMatches() throws Exception {
        Path file = dir.resolve("config.toml");
        Files.writeString(file, "[ui.theme]\nworkspace = 'dracula'\n");
        Files.createDirectories(dir.resolve("terminal-themes"));
        Files.writeString(dir.resolve("terminal-themes/broken.toml"), "[colors.primary\n");
        var terminalThemes = TerminalThemeCatalog.beside(file);
        terminalThemes.scan();
        var worker = Executors.newSingleThreadScheduledExecutor();
        try (var service = new ConfigService(file, true, worker, SwingUtilities::invokeLater)) {
            edt(() -> {
                var themes = new ThemeController(theme -> true);
                var controller = new ConfigurationController(themes, service, ThemeCatalog.builtIn(), terminalThemes, path -> { });
                Palette dracula = terminalThemes.latest().find("Dracula").orElseThrow().palette();
                assertThat(themes.current().palette()).isEqualTo(dracula);
                assertThat(themes.workspaceChoice().name()).isEqualTo("dracula");
                assertThat(controller.shown().diagnostics()).singleElement()
                    .satisfies(problem -> assertThat(problem.file().getFileName().toString()).isEqualTo("broken.toml"));
                controller.close();
            });
        } finally { worker.shutdownNow(); }

        Files.writeString(file, "[ui.theme]\nworkspace = 'No Such Theme'\n");
        var worker2 = Executors.newSingleThreadScheduledExecutor();
        try (var service = new ConfigService(file, true, worker2, SwingUtilities::invokeLater)) {
            edt(() -> {
                var themes = new ThemeController(theme -> true);
                var controller = new ConfigurationController(themes, service, ThemeCatalog.builtIn(), terminalThemes, path -> { });
                assertThat(themes.current().palette()).as("unknown: match").isEqualTo(themes.choice().palette());
                assertThat(controller.shown().diagnostics()).anySatisfy(problem -> {
                    assertThat(problem.key()).isEqualTo("ui.theme.workspace");
                    assertThat(problem.message()).contains("No Such Theme", "matching the chrome theme");
                });
                controller.close();
            });
        } finally { worker2.shutdownNow(); }
    }
}
