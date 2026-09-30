package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.terminalthemes.TerminalThemeCatalog;
import dev.jasper.terminal.config.Palette;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerminalThemeResolutionTest {
    @TempDir Path dir;

    @Test void aNamedThemeColoursTheTerminalAndAnUnknownOneWarnsAndMatches() throws Exception {
        Path file = dir.resolve("config.toml");
        Files.writeString(file, "[terminal]\ncolors = 'dracula'\n");
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
                assertThat(themes.terminalColors().name()).isEqualTo("dracula");
                assertThat(controller.shown().diagnostics()).singleElement()
                    .satisfies(problem -> assertThat(problem.file().getFileName().toString()).isEqualTo("broken.toml"));
                controller.close();
            });
        } finally { worker.shutdownNow(); }

        Files.writeString(file, "[terminal]\ncolors = 'No Such Theme'\n");
        var worker2 = Executors.newSingleThreadScheduledExecutor();
        try (var service = new ConfigService(file, true, worker2, SwingUtilities::invokeLater)) {
            edt(() -> {
                var themes = new ThemeController(theme -> true);
                var controller = new ConfigurationController(themes, service, ThemeCatalog.builtIn(), terminalThemes, path -> { });
                assertThat(themes.current().palette()).as("unknown: match").isEqualTo(themes.choice().palette());
                assertThat(controller.shown().diagnostics()).anySatisfy(problem -> {
                    assertThat(problem.key()).isEqualTo("terminal.colors");
                    assertThat(problem.message()).contains("No Such Theme", "Alacritty Themes");
                });
                controller.close();
            });
        } finally { worker2.shutdownNow(); }
    }
}
