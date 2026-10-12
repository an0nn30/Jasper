package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationReportTest {
    @TempDir Path dir;

    @Test void reportedProblemsJoinTheDiagnosticsUntilTheNextState() throws Exception {
        Path file = dir.resolve("config.toml");
        Files.writeString(file, "[window]\nlines = 40\n");
        var worker = Executors.newSingleThreadScheduledExecutor();
        // The service reads its initial state here, off the EDT, as production does; the controller is EDT-owned.
        try (var service = new ConfigService(file, true, worker, SwingUtilities::invokeLater)) {
            edt(() -> {
                var controller = new ConfigurationController(new ThemeController(theme -> true), service, path -> { });
                assertThat(controller.shown().diagnostics()).isEmpty();
                controller.report("plugins.\"a.b\".port", "out of range");
                assertThat(controller.shown().diagnostics()).singleElement().satisfies(diagnostic -> {
                    assertThat(diagnostic.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
                    assertThat(diagnostic.key()).isEqualTo("plugins.\"a.b\".port");
                    assertThat(diagnostic.message()).isEqualTo("out of range");
                    assertThat(diagnostic.file()).isEqualTo(file);
                });
                controller.accept(service.initialState());
                assertThat(controller.shown().diagnostics()).as("a new state clears plugin reports").isEmpty();
                controller.close();
            });
        } finally { worker.shutdownNow(); }
    }
}
