package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.plugins.PluginSelection;
import dev.jasper.app.startup.StartupSteps;
import dev.jasper.app.testsupport.BrandingFixtures;
import dev.jasper.app.testsupport.LookFixtures;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 10.11: the application reports each plugin to a branded look's splash; a look without branding ends the steps. */
class StartupStepsApplicationTest {
    @TempDir Path home;

    AppDirs dirs() { return new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")); }

    @Test void aBrandedLookReportsEachPluginAsItStarts() throws Exception {
        var selection = PluginSelection.of(dirs(), null, "", BrandingFixtures.plugin(home.resolve("dev"), "dev.example.look", "Example"), false);
        assertThat(selection.presentation().branding().present()).isTrue();
        var shown = new ArrayList<StartupSteps.Step>();
        var closed = new AtomicInteger();
        var steps = StartupSteps.of("JasperOps", selection.loading().size(), shown::add, closed::incrementAndGet);
        var original = UIManager.getLookAndFeel();
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
                application[0].startupSteps(steps);
                application[0].startPlugins(dirs());
            });
            assertThat(shown).containsExactly(new StartupSteps.Step("Loading plugin: Example", 3, 5));
            assertThat(closed).hasValue(0);
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            UIManager.setLookAndFeel(original);
        }
    }

    /** Review Focus 3: the presentation of a look that fell back at start has no branding, so its splash closes. */
    @Test void aLookWithoutBrandingEndsTheStepsAtOnce() throws Exception {
        Path dev = LookFixtures.plugin(home.resolve("dev"), "dev.example.look", LookFixtures.descriptor("dev.example.look", "Example")
            + LookFixtures.provider("look.kit.toml"), Map.of("look.kit.toml", LookFixtures.KIT));
        var selection = PluginSelection.of(dirs(), null, "", dev, false);
        assertThat(selection.presentation().branding().present()).isFalse();
        var closed = new AtomicInteger();
        var steps = StartupSteps.of("JasperOps", selection.loading().size(), step -> { }, closed::incrementAndGet);
        var original = UIManager.getLookAndFeel();
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
                application[0].startupSteps(steps);
            });
            assertThat(closed).hasValue(1);
            assertThat(steps.active()).isFalse();
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            UIManager.setLookAndFeel(original);
        }
    }
}
