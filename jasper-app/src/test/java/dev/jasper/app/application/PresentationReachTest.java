package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.plugins.PluginSelection;
import dev.jasper.app.testsupport.LookFixtures;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.ThemeRoute;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Map;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 4.9 and 16.1.8: every KitContext construction site carries the presentation's kit, the fallback's included. */
class PresentationReachTest {
    @TempDir Path home;

    /** A provider whose kit differs from the default one in a metric every context exposes. */
    PluginSelection selection() throws Exception {
        Path dev = LookFixtures.plugin(home.resolve("dev"), "dev.example.look", LookFixtures.descriptor("dev.example.look", "Example")
            + LookFixtures.provider("look.kit.toml"), Map.of("look.kit.toml", "kit = 1\nbase = \"default\"\n[metrics]\ncontrol_height = 26\n"));
        return PluginSelection.of(new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")), null, "", dev, false);
    }

    @Test void theControllerInstallsAndHandsOutItsKitThroughEveryConfigure() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var kit = selection().presentation().kit();
        assertThat(kit).isNotEqualTo(Kit.DEFAULT);
        try {
            edt(() -> {
                try (var controller = ThemeController.startup(true, ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH), kit,
                        failure -> { throw new AssertionError(failure); })) {
                    assertThat(controller.kit()).isSameAs(kit);
                    assertThat(controller.appearance().context(ThemeRoute.CHROME).kit()).isSameAs(kit);
                    assertThat(FlatLafInstaller.installedKit()).isSameAs(kit);
                    controller.configure(false, TerminalSchemeChoice.MATCH);
                    assertThat(controller.appearance().context(ThemeRoute.WORKSPACE).kit()).isSameAs(kit);
                    assertThat(FlatLafInstaller.installedKit()).as("every install of the controller uses its kit").isSameAs(kit);
                    var icons = new dev.jasper.app.platform.PresentationIcons();
                    assertThat(icons.context(controller.appearance().context(ThemeRoute.CHROME)).kit()).isSameAs(kit);
                    // Another kit installed under the controller: its contexts still carry the presentation's kit.
                    FlatLafInstaller.install(controller.current().chrome(), Kit.DEFAULT);
                    try {
                        assertThat(controller.appearance().context(ThemeRoute.CHROME).kit()).as("not the installed kit").isSameAs(kit);
                        assertThat(icons.context(controller.appearance().context(ThemeRoute.WORKSPACE)).kit()).as("not the installed kit").isSameAs(kit);
                    } finally { FlatLafInstaller.install(controller.current().chrome(), kit); }
                    icons.close();
                }
            });
        } finally { edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } }); }
    }

    @Test void theApplicationRunsTheSelectionsPresentation() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var selection = selection();
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
                assertThat(application[0].presentation()).isSameAs(selection.presentation());
                assertThat(application[0].themes().kit()).isSameAs(selection.presentation().kit());
                assertThat(application[0].themes().appearance().context(ThemeRoute.CHROME).kit()).isSameAs(selection.presentation().kit());
            });
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
        }
    }

    /** Spec 4.7 and 16.1.8: a kit that cannot be installed runs the fallback value, and its provider never starts. */
    @Test void aKitThatCannotBeInstalledRunsTheFallbackValueAndSkipsItsProvider() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path devPlugins = LookFixtures.loadable(home.resolve("dev"), "dev.example.look", LookFixtures.descriptor("dev.example.look", "Example")
            + LookFixtures.provider("look.kit.toml"), Map.of("look.kit.toml", "kit = 1\nbase = \"default\"\n[metrics]\ncontrol_height = 26\n"));
        var selection = PluginSelection.of(dirs, null, "B", devPlugins, false);
        var kit = selection.presentation().kit();
        assertThat(kit).isNotEqualTo(Kit.DEFAULT);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null,
                    selection, chosen -> theme -> {
                        if (chosen == kit) throw new IllegalStateException("cannot install under " + chosen.id());
                        return ThemeController.INSTALLERS.apply(chosen).test(theme);
                    });
                assertThat(application[0].presentation()).as("the fallback value").isEqualTo(selection.blamed().presentation());
                assertThat(application[0].presentation().provider()).isEmpty();
                assertThat(application[0].themes().kit()).isSameAs(Kit.DEFAULT);
                assertThat(application[0].themes().appearance().context(ThemeRoute.CHROME).kit()).isSameAs(Kit.DEFAULT);
                application[0].startPlugins(dirs);
                assertThat(application[0].pluginRuntime().statusLines()).anySatisfy(line -> assertThat(line)
                    .contains("dev.example.look", "SKIPPED", "its look could not be installed; Jasper uses its standard look"));
            });
            assertThat(dirs.plugins().resolve("dev.example.look/data/started")).as("the provider never started, so it published no service")
                .doesNotExist();
            dev.jasper.app.workspace.DesktopTestSupport.until(() -> new dev.jasper.app.presentation.AttemptStore(home).read()
                .of("dev.example.look").state().equals(java.util.Optional.of(dev.jasper.app.presentation.AttemptStore.State.FAILED)));
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
        }
    }
}
