package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.ui.theme.SlateSteel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

/** spec 5.4: start-up parses the bundled theme once and installs the configured Slate Steel variant. */
class JasperApplicationThemesTest {
    @TempDir Path home;

    private void start(String config, boolean dark) throws Exception {
        Files.writeString(home.resolve("config.toml"), config);
        var service = new ConfigService(home.resolve("config.toml"), false);
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                try { UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel()); }
                catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
                org.assertj.core.api.Assertions.assertThatIllegalStateException().isThrownBy(dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                var expected = SlateSteel.bundled().generated(dark);
                assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme()).isSameAs(expected);
                assertThat(dev.jasper.app.platform.ThemeZone.theme(null)).isSameAs(expected);
            });
            assertThat(Files.exists(home.resolve("themes"))).as("no themes folder").isFalse();
        } finally {
            if (application[0] != null) { edt(application[0]::quit); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); }
            service.close();
            edt(() -> ThemeTestSupport.controller(true));
        }
    }

    /** From a fresh look and feel (no Jasper snapshot), start-up installs its variant before any chrome reads the snapshot. */
    @Test void aFreshProcessInstallsTheConfiguredVariantBeforeAnyChromeReadsTheSnapshot() throws Exception {
        start("[ui]\nappearance = 'light'\n[terminal]\nscheme = 'high-contrast'\n", false);
    }

    /** The default follows the operating system; a headless run has none, so it starts Dark. */
    @Test void withNoAppearanceAHeadlessStartIsDark() throws Exception {
        start("", true);
    }
}
