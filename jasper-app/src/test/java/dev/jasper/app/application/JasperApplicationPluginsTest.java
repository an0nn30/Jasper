package dev.jasper.app.application;

import dev.jasper.app.history.CommandHistory;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.testsupport.PluginJars;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.restart.RestartMode;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class JasperApplicationPluginsTest {
    private static final String FIXTURE = """
        package fix.life;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        public final class Main implements Plugin {
            private PluginContext context;
            @Override public void start(PluginContext context) throws Exception {
                this.context = context;
                Files.writeString(context.dataDirectory().resolve("started"), "yes");
            }
            @Override public void stop() {
                try { Files.writeString(context.dataDirectory().resolve("stopped"), "yes"); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
        """;

    @TempDir Path home;

    @Test void pluginsStartBeforeWindowsAndStopBeforeTermination() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "life.jar", PluginJars.descriptor("dev.example.life", "1.0.0", "fix.life.Main"),
            Map.of("fix.life.Main", FIXTURE), List.of());
        var stoppedBeforeExit = new boolean[1];
        var terminated = new CountDownLatch(1);
        Path data = dirs.pluginData().resolve("dev.example.life");
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> {
                stoppedBeforeExit[0] = java.nio.file.Files.exists(data.resolve("stopped"));
                terminated.countDown();
            });
            application[0].startPlugins(null, dev, false, dirs);
            application[0].startPlugins(null, dev, false, dirs);
        });
        assertThat(data.resolve("started")).exists();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(stoppedBeforeExit[0]).isTrue();
    }

    private static final String BINDING_FIXTURE = """
        package fix.keys;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import dev.jasper.sdk.ui.ActionSpec;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.actions().register(ActionSpec.of("dev.example.keys.palette", "Steal The Palette Key").withDefaultBinding("cmd+k"), invoked -> { });
                context.actions().register(ActionSpec.of("dev.example.keys.bound", "Bound By The User"), invoked -> { });
            }
        }
        """;

    @Test void bindingProblemsSeparateUnknownUserIdsFromDroppedPluginDefaults() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        java.nio.file.Files.writeString(dirs.configFile(), """
            [keybindings]
            "dev.example.keys.bound" = "cmd+alt+b"
            "dev.example.gone.action" = "cmd+alt+g"
            """);
        Path dev = home.resolve("keys-plugin");
        PluginJars.build(dev, "keys.jar", PluginJars.descriptor("dev.example.keys", "1.0.0", "fix.keys.Main"),
            Map.of("fix.keys.Main", BINDING_FIXTURE), List.of());
        boolean mac = System.getProperty("os.name").startsWith("Mac");
        var worker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        var service = new dev.jasper.app.config.ConfigService(dirs.configFile(), mac, worker, javax.swing.SwingUtilities::invokeLater);
        JasperApplication[] application = new JasperApplication[1];
        var terminated = new CountDownLatch(1);
        try {
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                application[0].startPlugins(null, dev, false, dirs);
                assertThat(application[0].bindingProblems()).extracting(problem -> problem.kind() + " " + problem.actionId())
                    .containsExactlyInAnyOrder("UNKNOWN_ACTION dev.example.gone.action", "DEFAULT_DROPPED dev.example.keys.palette");
            });
        } finally {
            edt(application[0]::quit);
            // Shutdown writes the layout state into the temporary home; let it finish before JUnit deletes that.
            assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            worker.shutdownNow();
        }
    }

    @Test void layoutStateIsLoadedForPluginsAndSavedAtShutdown() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        java.nio.file.Files.writeString(dirs.uiState(), "version = 1\nrail_visible = false\n");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, null, false, dirs);
        });
        java.nio.file.Files.delete(dirs.uiState());
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(dirs.uiState()).as("saved again at shutdown, keeping what was loaded").exists()
            .content().contains("rail_visible = false");
    }

    @Test void theManagerIsAContributedActionInTheFileMenu() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, null, false, dirs);
            assertThat(application[0].contributions().action("plugins.manage")).get()
                .satisfies(action -> assertThat(action.title()).isEqualTo("Manage Plugins…"));
            assertThat(application[0].contributions().menus()).anySatisfy(section -> {
                assertThat(section.target()).isEqualTo(MenuTarget.standard(MenuTarget.Slot.FILE));
                assertThat(section.entries()).containsExactly(new MenuEntry.Item("plugins.manage"));
            });
            assertThat(application[0].bindingProblems()).isEmpty();
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test void restartQuitsThenStartsTheReplacementAfterProcessCleanup() throws Exception {
        List<String> order = new CopyOnWriteArrayList<>();
        var terminated = new CountDownLatch(1);
        edt(() -> {
            var application = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null,
                () -> { order.add("terminate"); terminated.countDown(); });
            application.restartPlanner = mode -> Optional.of(List.of("jasper", mode.name()));
            application.spawner = command -> order.add("spawn " + command);
            application.residency(true);
            application.onShutdown(() -> order.add("endpoint released"));
            assertThat(application.restart(RestartMode.SAME)).isTrue();
            assertThat(application.restart(RestartMode.NORMAL)).as("already on its way out").isTrue();
        });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(order).containsExactly("endpoint released", "spawn [jasper, SAME]", "terminate");
    }

    @Test void aReplacementForAnEndpointOwnerWhoseCleanupHangsCanNeverHandOff() throws Exception {
        List<String> spawned = new CopyOnWriteArrayList<>();
        var terminated = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            edt(() -> {
                var application = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                application.restartPlanner = mode -> Optional.of(List.of("jasper"));
                application.spawner = command -> spawned.add(String.join(" ", command));
                application.residency(true);
                application.onShutdown(() -> { try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } });
                application.restart(RestartMode.SAME);
            });
            assertThat(terminated.await(10, TimeUnit.SECONDS)).as("the shutdown grace bounds the wait").isTrue();
            assertThat(spawned).containsExactly("jasper --standalone");
        } finally { release.countDown(); }
    }

    @Test void anUnknownCommandLineLeavesJasperRunning() throws Exception {
        List<String> spawned = new CopyOnWriteArrayList<>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].restartPlanner = mode -> Optional.empty();
            application[0].spawner = command -> spawned.add(String.join(" ", command));
            assertThat(application[0].restart(RestartMode.SAME)).isFalse();
        });
        edt(() -> { });
        assertThat(terminated.getCount()).as("still running").isEqualTo(1);
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(spawned).as("an ordinary quit starts nothing").isEmpty();
    }

    private static final String TERMINALS_FIXTURE = """
        package fix.terms;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) throws Exception {
                Files.writeString(context.dataDirectory().resolve("windows"),
                    context.terminals().windows().size() + " " + context.terminals().activePane().isPresent());
            }
        }
        """;

    @Test void pluginsSeeTheApplicationsTerminalRegistry() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("terms-plugin");
        PluginJars.build(dev, "terms.jar", PluginJars.descriptor("dev.example.terms", "1.0.0", "fix.terms.Main"),
            Map.of("fix.terms.Main", TERMINALS_FIXTURE), List.of());
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, dev, false, dirs);
            assertThat(application[0].terminals().windows()).isEmpty();
        });
        assertThat(dirs.pluginData().resolve("dev.example.terms").resolve("windows")).hasContent("0 false");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
