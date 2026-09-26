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

    @Test void theApplicationStartsInTheInstalledThemeItsConfigurationNames() throws Exception {
        Files.writeString(home.resolve("config.toml"), "ui.theme.name = 'Fixture Dark'\n");
        ThemeFixtures.plugin(home.resolve("themes/fixture.jar"), "Fixture Dark", true, "#123456", "101820");
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
