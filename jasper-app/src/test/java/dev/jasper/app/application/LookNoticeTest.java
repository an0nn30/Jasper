package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.plugins.PluginSelection;
import dev.jasper.app.testsupport.LookFixtures;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Map;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 5.8, 5.9 and 15: a look whose fonts or default themes did not load still starts, and says so once. */
class LookNoticeTest {
    @TempDir Path home;

    @Test void aLookMissingFilesPostsOneWarning() throws Exception {
        Path dev = LookFixtures.loadable(home.resolve("dev"), "dev.example.look", LookFixtures.descriptor("dev.example.look", "Example")
            + LookFixtures.provider("look.kit.toml") + "themes = [\"themes/look.json\"]\n", Map.of(
                "look.kit.toml", "kit = 1\nbase = \"default\"\n[type.ui]\nfamily = \"Example Sans\"\nregular = \"fonts/a.ttf\"\n"
                    + "medium = \"fonts/a.ttf\"\nsemibold = \"fonts/a.ttf\"\n[defaults]\n\"ui.theme.dark\" = \"look\"\n",
                "fonts/a.ttf", "not a font", "themes/look.json", "{ not a theme"));
        var selection = PluginSelection.of(new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")), null, "", dev, false);
        assertThat(selection.presentation().look()).as("the look stays").isEqualTo("dev.example.look");
        var original = UIManager.getLookAndFeel();
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
                application[0].startPlugins(new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")));
                var notices = application[0].notificationCenter().history().stream().map(entry -> entry.request())
                    .filter(request -> request.title().equals("The Example look is missing some of its files")).toList();
                assertThat(notices).singleElement().satisfies(request -> {
                    assertThat(request.severity()).isEqualTo(Severity.WARNING);
                    assertThat(request.content()).isEqualTo(String.join("\n",
                        "fonts/a.ttf: its names cannot be read; that text uses the platform font",
                        "fonts/a.ttf: its names cannot be read; that text uses the platform font",
                        "fonts/a.ttf: its names cannot be read; that text uses the platform font",
                        "[defaults].\"ui.theme.dark\": its theme did not load, so the setting keeps its core default"));
                });
            });
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
        }
    }
}
