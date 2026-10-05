package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeFixtures;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import java.awt.Color;
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

class JasperApplicationThemesTest {
    @TempDir Path home;

    @Test void applicationPollAppliesNativeEditsWithoutChangingConfiguration() throws Exception {
        Path file = home.resolve("themes/live.toml"); Files.createDirectories(file.getParent());
        Files.writeString(file, "name='Live'\nextends='Jasper Dark'\n[ui]\n'Panel.background'='#112233'\n");
        Path config = home.resolve("config.toml"); String text = "ui.theme.name='Live'\n"; Files.writeString(config, text);
        var worker = new dev.jasper.app.testsupport.ConfigPollWorker();
        var service = new ConfigService(config, false, worker, javax.swing.SwingUtilities::invokeLater);
        var terminated = new CountDownLatch(1); JasperApplication[] app = new JasperApplication[1];
        try {
            edt(() -> app[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown));
            Files.writeString(file, Files.readString(file).replace("#112233", "#445566")); worker.poll();
            edt(() -> assertThat(UIManager.getColor("Panel.background")).isEqualTo(new Color(0x445566)));
            assertThat(Files.readString(config)).isEqualTo(text);
        } finally {
            if (app[0] != null) { edt(app[0]::quit); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); }
            service.close(); edt(() -> new ThemeController());
        }
    }

    /** From a fresh look and feel (no Jasper snapshot), start-up installs its theme before any chrome reads the snapshot. */
    @Test void aFreshProcessInstallsItsThemeBeforeAnyChromeReadsTheSnapshot() throws Exception {
        Files.writeString(home.resolve("config.toml"), "ui.theme.name = 'Jasper Light'\n");
        var service = new ConfigService(home.resolve("config.toml"), false);
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                try { UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel()); }
                catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
                org.assertj.core.api.Assertions.assertThatIllegalStateException().isThrownBy(dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                var light = dev.jasper.app.appearance.ThemeManager.generated(dev.jasper.app.appearance.Theme.JASPER_LIGHT);
                assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme()).isSameAs(light);
                assertThat(dev.jasper.app.platform.ThemeZone.theme(null)).isSameAs(light);
            });
        } finally {
            if (application[0] != null) { edt(application[0]::quit); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); }
            service.close();
            edt(() -> new ThemeController());
        }
    }

    @Test void theApplicationStartsInTheInstalledThemeItsConfigurationNames() throws Exception {
        Files.writeString(home.resolve("config.toml"), "ui.theme.name = 'Fixture Dark'\n");
        ThemeFixtures.plugin(home.resolve("themes/fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        var problems = ThemeUpgrade.prepare(home.resolve("config.toml"), false);
        assertThat(problems).isEmpty();
        var service = new ConfigService(home.resolve("config.toml"), false);
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(),
                    null, terminated::countDown);
                assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Fixture Dark");
                assertThat(UIManager.getColor("Panel.background")).isEqualTo(new Color(0x123456));
            });
        } finally {
            if (application[0] != null) { edt(application[0]::quit); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); }
            service.close();
            edt(() -> new ThemeController());
        }
    }
}
