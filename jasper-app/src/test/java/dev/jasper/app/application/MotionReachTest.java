package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.platform.SystemAppearance;
import dev.jasper.app.presentation.Motion;
import dev.jasper.ui.theme.ThemeRoute;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 10.9: the one supplier reaches the kit contexts, and a saved setting survives a still look. */
class MotionReachTest {
    @TempDir Path dir;

    boolean[] run(Motion motion) throws Exception {
        Path file = dir.resolve(motion + ".toml");
        Files.writeString(file, "[ui.animations]\nenabled = true\n");
        var worker = Executors.newSingleThreadScheduledExecutor();
        boolean[] seen = new boolean[3];
        try (var service = new ConfigService(file, true, worker, SwingUtilities::invokeLater)) {
            edt(() -> {
                var themes = ThemeTestSupport.controller(true, theme -> true);
                var controller = new ConfigurationController(themes, service, path -> { },
                    SystemAppearance.Observation.unavailable("none in tests"), null, new LookMotion(motion));
                seen[0] = themes.animationsEnabled();
                seen[1] = themes.appearance().context(ThemeRoute.CHROME).motionPolicy().enabled();
                seen[2] = controller.snapshot().animations().enabled();
                controller.close(); themes.close();
            });
        } finally { worker.shutdownNow(); }
        return seen;
    }

    @Test void noMotionWinsOverTheSettingAndTheSettingStaysSaved() throws Exception {
        assertThat(run(Motion.NONE)).containsExactly(false, false, true);
        assertThat(run(Motion.STANDARD)).as("the default look moves as the setting says").containsExactly(true, true, true);
    }
}
