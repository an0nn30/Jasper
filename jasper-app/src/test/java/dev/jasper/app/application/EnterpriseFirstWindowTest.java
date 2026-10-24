package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.plugins.PluginSelection;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spec 5.8 and 16.1.8: the first window of a fresh home under Enterprise opens with the kit's defaults; under the landed
 * dock (plan 7, spec 10.7) the application titles the Notifications panel Event Log at Bottom Left and loads its dock
 * layout under the look's id.
 */
class EnterpriseFirstWindowTest {
    @TempDir Path home;

    @Test void theFirstWindowOfAFreshHomeUnderEnterpriseUsesTheKitsDefaults() throws Exception {
        var dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path enterprise = Path.of(System.getProperty("jasper.stagedPlugins")).resolve("dev.jasper.enterprise");
        var selection = PluginSelection.of(dirs, null, "", enterprise, false);
        assertThat(selection.presentation().look()).isEqualTo("dev.jasper.enterprise");
        var service = new ConfigService(home.resolve("config.toml"), false, selection.presentation().defaults());
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown,
                    null, null, selection);
                var first = application[0].windowConfiguration();
                assertThat(first.visibleRows()).isEqualTo(8);
                // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar.
                assertThat(first.toolbarHeight()).isEqualTo(28);
                assertThat(first.lightTheme()).isEqualTo("jasper-meadow");
                assertThat(first.darkTheme()).isEqualTo("jasper-meadow");
                assertThat(application[0].themes().current().chrome().name()).as("the window's theme is the kit's pick").startsWith("Jasper Meadow");
                assertThat(application[0].themes().kit()).isSameAs(selection.presentation().kit());
                var log = application[0].contributions().panels().stream()
                    .filter(entry -> entry.id().equals(NotificationsFeature.PANEL_ID)).findFirst().orElseThrow();
                assertThat(log.title()).as("the Notifications panel under Enterprise").isEqualTo("Event Log");
                assertThat(log.defaultSlot()).isEqualTo(dev.jasper.app.dock.Slot.BOTTOM_LEFT);
                assertThat(application[0].presentation().projections().dockLayout().key()).as("the key UiState loads the dock under")
                    .isEqualTo("dev.jasper.enterprise");
            });
        } finally {
            if (application[0] != null) { edt(application[0]::quit); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); }
            service.close();
            edt(() -> dev.jasper.app.appearance.ThemeTestSupport.controller(true));
        }
    }
}
