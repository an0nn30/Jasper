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

    /** Spec 10.1 and 10.9: a kit with the system title bar, menus in each window and no motion. */
    static final String STILL_CLASSIC = LookFixtures.KIT + "[chrome]\ntitle_bar = \"native\"\nmenu_bar = \"window\"\nmotion = \"none\"\n";

    /** A provider of {@code kit} that loads, so {@link JasperApplication#startPlugins(AppDirs)} can start it. */
    PluginSelection selection(String kit) throws Exception {
        Path dev = LookFixtures.loadable(home.resolve("dev"), "dev.example.look", LookFixtures.descriptor("dev.example.look", "Example")
            + LookFixtures.provider("look.kit.toml"), Map.of("look.kit.toml", kit));
        return PluginSelection.of(dirs(), null, "", dev, false);
    }

    AppDirs dirs() { return new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")); }

    /**
     * The wiring between the presentation and its consumers (spec 10.1, 10.9): the still look keeps everything still
     * without a configuration, the windows and the shells take its looks, and About joins Help.
     */
    @Test void theApplicationHandsThePresentationsProjectionsToItsConsumers() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var selection = selection(STILL_CLASSIC);
        var projections = selection.presentation().projections();
        assertThat(projections.motion()).isEqualTo(dev.jasper.app.presentation.Motion.NONE);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
                assertThat(application[0].themes().animationsEnabled()).as("no configuration: the look still decides").isFalse();
                assertThat(application[0].windowLook()).as("Jasper windows and the rehearsal's")
                    .isEqualTo(dev.jasper.app.workspace.WindowLook.of(projections))
                    .isNotEqualTo(dev.jasper.app.workspace.WindowLook.standard());
                application[0].startPlugins(dirs());
                assertThat(application[0].nativeShells().chrome()).as("auxiliary windows, dialogs and the chooser")
                    .isEqualTo(dev.jasper.app.windows.ShellChrome.of(projections.shell()))
                    .isNotEqualTo(dev.jasper.app.windows.ShellChrome.standard());
                // Spec 4.9: the auxiliary windows are made with the look's dialog mapping (plan 6b).
                assertThat(application[0].auxiliary().dialogs()).as("the auxiliary windows' dialog mapping").isEqualTo(projections.dialogs());
                // With the screen menu bar off, About is in Help on every platform; on macOS it otherwise stays in the application menu.
                assertThat(help(application[0])).contains("app.about");
            });
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            ApplicationPluginCleanup.await(application[0]);
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
        }
    }

    /**
     * Plan 15a: a started plugin reads the presentation and the look's defaults for its settings that the application
     * handed the plugin runtime.
     */
    @Test void theApplicationHandsTheLooksPluginDefaultsToThePlugins() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        String main = """
            package dev.example.look;
            public final class Main implements dev.jasper.sdk.plugin.Plugin {
                @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                    var size = context.settings().declare(dev.jasper.sdk.settings.SettingSpec.of("size",
                        dev.jasper.sdk.settings.SettingKind.integer(8, 48), 13).label("Size"));
                    try {
                        java.nio.file.Files.writeString(context.dataDirectory().resolve("read"),
                            context.appearance().presentation().classic() + " " + size.get());
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                }
            }
            """;
        Path folder = home.resolve("dev").resolve("dev.example.look");
        Path jar = dev.jasper.app.testsupport.PluginJars.build(folder.resolve("jars"), "dev.example.look.jar",
            LookFixtures.descriptor("dev.example.look", "Example") + LookFixtures.provider("look.kit.toml"),
            Map.of("dev.example.look.Main", main), java.util.List.of());
        dev.jasper.app.testsupport.PluginJars.addResource(jar, "look.kit.toml", STILL_CLASSIC + "[defaults.\"dev.example.look\"]\nsize = 15\n");
        var selection = PluginSelection.of(dirs(), null, "", folder, false);
        boolean classic = selection.presentation().projections().plugins().classic();
        assertThat(selection.presentation().defaults().plugin("dev.example.look")).containsEntry("size", 15L);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
                application[0].startPlugins(dirs());
            });
            Path read = dirs().plugins().resolve("dev.example.look/data/read");
            dev.jasper.app.workspace.DesktopTestSupport.until(() -> java.nio.file.Files.exists(read));
            assertThat(java.nio.file.Files.readString(read)).as("the look's classic flag and its default for the plugin's setting")
                .isEqualTo(classic + " 15");
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            ApplicationPluginCleanup.await(application[0]);
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
        }
    }

    /** Spec 10.9: a still look keeps everything still though the saved setting says otherwise. */
    @Test void aStillLookWinsOverTheSavedAnimationSetting() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var selection = selection(STILL_CLASSIC);
        java.nio.file.Files.writeString(home.resolve("config.toml"), "[ui.animations]\nenabled = true\n");
        var service = new dev.jasper.app.config.ConfigService(home.resolve("config.toml"), true);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
                assertThat(application[0].themes().animationsEnabled()).isFalse();
                application[0].startPlugins(dirs());
                assertThat(help(application[0])).as("macOS, the screen menu bar off: About is in Help").contains("app.about");
            });
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            ApplicationPluginCleanup.await(application[0]);
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
            service.close();
        }
    }

    /** The action ids of the Help menu's entries. */
    static java.util.List<String> help(JasperApplication application) {
        return application.contributions().menus().stream()
            .filter(section -> section.target().equals(dev.jasper.app.contributions.MenuTarget.topLevel(dev.jasper.app.contributions.MenuTarget.HELP, "Help")))
            .flatMap(section -> section.entries().stream())
            .filter(entry -> entry instanceof dev.jasper.app.contributions.MenuEntry.Item)
            .map(entry -> ((dev.jasper.app.contributions.MenuEntry.Item) entry).actionId()).toList();
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
            ApplicationPluginCleanup.await(application[0]);
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
        }
    }

    /** Spec 4.7 and 16.1.8: a kit that cannot be installed runs the fallback value, and its provider never starts. */
    @Test void aKitThatCannotBeInstalledRunsTheFallbackValueAndSkipsItsProvider() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path devPlugins = LookFixtures.loadable(home.resolve("dev"), "dev.example.look", LookFixtures.descriptor("dev.example.look", "Example")
            + LookFixtures.provider("look.kit.toml"), Map.of("look.kit.toml", "kit = 1\nbase = \"default\"\n[metrics]\ncontrol_height = 26\n"
                + "[chrome]\ntitle_bar = \"native\"\nmenu_bar = \"window\"\nmotion = \"none\"\n"));
        var selection = PluginSelection.of(dirs, null, "B", devPlugins, false);
        var kit = selection.presentation().kit();
        assertThat(kit).isNotEqualTo(Kit.DEFAULT);
        JasperApplication[] application = new JasperApplication[1];
        var trace = dev.jasper.app.startup.StartupTrace.current();
        String look = trace.look();
        try {
            edt(() -> {
                trace.look(selection.presentation().look());
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null,
                    selection, chosen -> theme -> {
                        if (chosen == kit) throw new IllegalStateException("cannot install under " + chosen.id());
                        return ThemeController.INSTALLERS.apply(chosen).test(theme);
                    });
                assertThat(trace.look()).as("the start-up line names the fallback's look").isEqualTo("default");
                assertThat(application[0].presentation()).as("the fallback value").isEqualTo(selection.blamed().presentation());
                assertThat(application[0].presentation().provider()).isEmpty();
                assertThat(application[0].themes().kit()).isSameAs(Kit.DEFAULT);
                assertThat(application[0].themes().appearance().context(ThemeRoute.CHROME).kit()).isSameAs(Kit.DEFAULT);
                assertThat(application[0].themes().animationsEnabled()).as("the fallback moves: its motion is the default look's").isTrue();
                var steps = dev.jasper.app.startup.StartupSteps.of("Protean", selection.loading(), step -> {}, () -> {});
                steps.configuration();
                application[0].startupSteps(steps);
                assertThat(steps.queue()).containsExactlyElementsOf(dev.jasper.app.startup.StartupSteps.queue("Protean", selection.blamed().loading()));
                assertThat(steps.current().count()).isEqualTo(4 + selection.blamed().loading().size());
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
            ApplicationPluginCleanup.await(application[0]);
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
            trace.look(look);
        }
    }
}
