package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ViewMode;
import dev.jasper.app.testsupport.ConfigPollWorker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

class JasperApplicationViewModeTest {
    @TempDir Path home;

    @ParameterizedTest @EnumSource(ViewMode.class)
    void newWindowConfigurationKeepsStartupModeAndTakesOtherLiveSettings(ViewMode initial) throws Exception {
        Path file = home.resolve("config.toml");
        Files.writeString(file, "window.view_mode='" + initial.name().toLowerCase(java.util.Locale.ROOT) + "'\n");
        var worker = new ConfigPollWorker();
        var service = new ConfigService(file, true, worker, javax.swing.SwingUtilities::invokeLater);
        var stopped = new CountDownLatch(1); JasperApplication[] app = new JasperApplication[1];
        try {
            edt(() -> {
                app[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, stopped::countDown);
                assertThat(app[0].windowConfiguration().viewMode()).isEqualTo(initial);
                assertThat(app[0].windowConfiguration().statusBar()).isTrue();
            });
            ViewMode saved = initial == ViewMode.NORMAL ? ViewMode.COMPACT : ViewMode.NORMAL;
            Files.writeString(file, "window.view_mode='" + saved.name().toLowerCase(java.util.Locale.ROOT) + "'\nwindow.status_bar=false\n");
            assertThat(service.reload().get(5, TimeUnit.SECONDS).snapshot().viewMode()).isEqualTo(saved);
            edt(() -> {
                assertThat(app[0].windowConfiguration().viewMode()).isEqualTo(initial);
                assertThat(app[0].windowConfiguration().statusBar()).isFalse();
            });
        } finally {
            if (app[0] != null) { edt(app[0]::quit); assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue(); }
            service.close(); edt(() -> ThemeTestSupport.controller(true));
        }
    }

    @Test void noServiceRetainsDefaultWindowConfiguration() throws Exception {
        var stopped = new CountDownLatch(1); JasperApplication[] app = new JasperApplication[1];
        try {
            edt(() -> {
                app[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, stopped::countDown);
                assertThat(app[0].windowConfiguration()).isEqualTo(ConfigSnapshot.defaults());
            });
        } finally {
            if (app[0] != null) { edt(app[0]::quit); assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue(); }
            edt(() -> ThemeTestSupport.controller(true));
        }
    }
}
