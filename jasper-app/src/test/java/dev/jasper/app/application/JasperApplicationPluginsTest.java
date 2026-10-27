package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.testsupport.PluginJars;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
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
        var savedBeforeExit = new boolean[1];
        var terminated = new CountDownLatch(1);
        Path data = dirs.plugins().resolve("dev.example.life/data");
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> {
                stoppedBeforeExit[0] = java.nio.file.Files.exists(data.resolve("stopped"));
                savedBeforeExit[0] = java.nio.file.Files.exists(dirs.workspaceState());
                terminated.countDown();
            });
            application[0].startPlugins(null, dev, false, dirs);
            application[0].startPlugins(null, dev, false, dirs);
            // A stand-in frame opens the main window headless, so Quit has a window to save (workspace plan 6a Task 9).
            application[0].frames = window -> new dev.jasper.app.workspace.WindowShell(() -> { }, () -> { }, () -> { }, () -> { },
                () -> null, component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            application[0].openFirstWindow(List.of()).orElseThrow();
        });
        assertThat(data.resolve("started")).exists();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
        assertThat(stoppedBeforeExit[0]).isTrue();
        assertThat(savedBeforeExit[0]).as("the windows saved in the home's workspace.toml").isTrue();
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
        // Free on both keymaps: off macOS cmd means ctrl+shift, so cmd+alt+g would be Find Previous.
        java.nio.file.Files.writeString(dirs.configFile(), """
            [keybindings]
            "dev.example.keys.bound" = "cmd+alt+b"
            "dev.example.gone.action" = "cmd+alt+j"
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
            ApplicationPluginCleanup.await(application[0]);
            worker.shutdownNow();
        }
    }

    @Test void layoutStateIsLoadedForPluginsAndSavedAtShutdown() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        java.nio.file.Files.writeString(dirs.uiState(), "version = 3\n[windows.\"dev.x.manager\"]\nx = 1\ny = 2\nwidth = 300\nheight = 200\n");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, null, false, dirs);
        });
        java.nio.file.Files.delete(dirs.uiState());
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
        assertThat(dirs.uiState()).as("saved again at shutdown, keeping what was loaded").exists()
            .content().contains("[windows.\"dev.x.manager\"]");
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
            assertThat(application[0].contributions().action("app.shortcuts")).isPresent();
            assertThat(application[0].contributions().action("app.memory")).get().satisfies(action -> {
                assertThat(action.title()).isEqualTo("Change Memory Settings…");
                assertThat(action.keywords()).contains("memory", "heap", "xmx", "jvm", "java", "vm options");
            });
            assertThat(application[0].contributions().action("app.vmoptions")).get().satisfies(action -> {
                assertThat(action.title()).isEqualTo("Edit Custom VM Options…");
                assertThat(action.keywords()).contains("memory", "heap", "xmx", "jvm", "java", "vm options");
            });
            assertThat(application[0].contributions().action(MemoryIndicator.COLLECT)).get()
                .satisfies(action -> assertThat(action.title()).isEqualTo("Run Garbage Collection"));
            assertThat(application[0].contributions().status()).anySatisfy(entry -> {
                assertThat(entry.id()).isEqualTo(MemoryIndicator.STATUS_ID);
                assertThat(entry.visible()).as("off by default").isFalse();
            });
            assertThat(application[0].contributions().action(AboutWindows.ABOUT)).get()
                .satisfies(action -> assertThat(action.title()).isEqualTo("About Jasper"));
            assertThat(application[0].contributions().menus()).anySatisfy(section -> {
                assertThat(section.target().title()).isEqualTo("Help");
                assertThat(section.entries()).isEqualTo(AboutWindows.helpEntries(System.getProperty("os.name").startsWith("Mac")));
            });
            assertThat(application[0].bindingProblems()).isEmpty();
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    @Test void anOutOfMemoryErrorWarnsThatJasperIsLowOnMemory() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, null, false, dirs);
            application[0].unexpected(Thread.currentThread(), new RuntimeException("wrapped", new OutOfMemoryError("Java heap space")));
            assertThat(application[0].notificationCenter().history())
                .anySatisfy(entry -> assertThat(entry.request().title()).isEqualTo("Jasper is running low on memory"));
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    @Test void quitReleasesTheHeapWatchAndTheMemoryIndicatorsAction() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        // The old generation: the heap pool that supports both usage thresholds, with the largest maximum.
        MemoryPoolMXBean oldGeneration = ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(pool -> pool.getType() == MemoryType.HEAP && pool.isUsageThresholdSupported() && pool.isCollectionUsageThresholdSupported())
            .max(java.util.Comparator.comparingLong(pool -> pool.getUsage().getMax())).orElseThrow();
        var trace = StartupTrace.detached();
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].trace(trace);
            application[0].startPlugins(null, null, false, dirs);
        });
        edt(() -> { });
        assertThat(application[0].contributions().action(MemoryIndicator.COLLECT)).as("the indicator's click action").isPresent();
        assertThat(oldGeneration.getCollectionUsageThreshold()).as("the low-memory watch, installed when start-up settled").isPositive();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
        assertThat(application[0].contributions().action(MemoryIndicator.COLLECT)).as("closed with the application").isEmpty();
        assertThat(oldGeneration.getCollectionUsageThreshold()).as("the threshold is released").isZero();
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
        assertThat(order).containsExactly("endpoint released", "spawn [jasper, SAME, --replacing, " + ProcessHandle.current().pid() + "]",
            "terminate");
    }

    @Test void aReplacementStartsEvenWhenCleanupHangsAndWaitsForTheLock() throws Exception {
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
            assertThat(spawned).containsExactly("jasper --replacing " + ProcessHandle.current().pid());
        } finally { release.countDown(); }
    }

    private static final String QUIT_FIXTURE = """
        package fix.quit;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        import java.util.concurrent.CompletableFuture;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.windows().onQuitting(() -> {
                    try { Files.writeString(context.dataDirectory().resolve("asked"), "yes"); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return CompletableFuture.completedFuture(Files.exists(context.dataDirectory().resolve("allow")));
                });
            }
        }
        """;

    @Test void quitAndRestartAskQuitGuardsAndARefusalKeepsJasperRunning() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "quit.jar", PluginJars.descriptor("dev.example.quit", "1.0.0", "fix.quit.Main"),
            Map.of("fix.quit.Main", QUIT_FIXTURE), List.of());
        Path data = dirs.plugins().resolve("dev.example.quit/data");
        List<String> spawned = new CopyOnWriteArrayList<>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].restartPlanner = mode -> Optional.of(List.of("jasper"));
            application[0].spawner = command -> spawned.add(String.join(" ", command));
            application[0].startPlugins(null, dev, false, dirs);
        });
        edt(application[0]::quit);
        edt(() -> assertThat(application[0].restart(RestartMode.SAME)).as("handled: the guard said no").isTrue());
        edt(() -> { });
        assertThat(data.resolve("asked")).exists();
        assertThat(terminated.getCount()).as("still running").isEqualTo(1);
        java.nio.file.Files.writeString(data.resolve("allow"), "yes");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
        assertThat(spawned).as("the refused restart left no relaunch behind").isEmpty();
    }

    @Test void quitNowDoesNotAskTheQuitGuards() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "quit.jar", PluginJars.descriptor("dev.example.quit", "1.0.0", "fix.quit.Main"),
            Map.of("fix.quit.Main", QUIT_FIXTURE), List.of());
        Path data = dirs.plugins().resolve("dev.example.quit/data");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, dev, false, dirs);
        });
        edt(application[0]::quitNow);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
        assertThat(data.resolve("asked")).as("the guard was never consulted").doesNotExist();
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
        assertThat(dirs.plugins().resolve("dev.example.terms/data").resolve("windows")).hasContent("0 false");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /** The installed LAF is FlatLaf's IntelliJ theme LAF carrying the saved theme's module snapshot; its identity. */
    private static String installedLightLaf() {
        var laf = javax.swing.UIManager.getLookAndFeel();
        assertThat(laf).isInstanceOf(com.formdev.flatlaf.IntelliJTheme.ThemeLaf.class);
        var light = dev.jasper.ui.theme.JasperTheme.builtIn("jasper-meadow").orElseThrow().generated(false);
        assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme()).isSameAs(light);
        assertThat(laf.getDefaults().get("Jasper.installedTheme")).as("the snapshot travels with the LAF itself").isSameAs(light);
        assertThat(laf.getName()).isEqualTo(light.name());
        return laf.getClass().getName() + "\n" + System.identityHashCode(laf) + "\n" + laf.getName();
    }

    private static final String APPEARANCE_FIXTURE = """
        package fix.look;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        import javax.swing.JButton;
        import javax.swing.UIManager;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) throws Exception {
                Files.writeString(context.dataDirectory().resolve("appearance"),
                    context.appearance().variant().name() + "\\n" +
                    UIManager.getLookAndFeel().getClass().getName() + "\\n" +
                    System.identityHashCode(UIManager.getLookAndFeel()) + "\\n" +
                    UIManager.getLookAndFeel().getName() + "\\n" +
                    new JButton().getUI().getClass().getName());
            }
        }
        """;

    @Test void pluginsStartInTheSavedVariantBeforeAnyWindowExists() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        java.nio.file.Files.writeString(dirs.configFile(), "ui.appearance='light'\n");
        Path dev = home.resolve("look-plugin");
        PluginJars.build(dev, "look.jar", PluginJars.descriptor("dev.example.look", "1.0.0", "fix.look.Main"),
            Map.of("fix.look.Main", APPEARANCE_FIXTURE), List.of());
        var service = new dev.jasper.app.config.ConfigService(dirs.configFile(), false);
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        String[] installed = new String[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(),
                    null, terminated::countDown);
                application[0].startPlugins(null, dev, false, dirs);
                assertThat(application[0].terminals().windows()).isEmpty();
                assertThat(com.formdev.flatlaf.FlatLaf.isLafDark()).isFalse();
                installed[0] = installedLightLaf();
            });
            assertThat(dirs.plugins().resolve("dev.example.look/data/appearance")).as("the plugin started under that same LAF")
                .hasContent("LIGHT\n" + installed[0] + "\ncom.formdev.flatlaf.ui.FlatButtonUI");
        } finally {
            if (application[0] != null) { edt(application[0]::quit); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); ApplicationPluginCleanup.await(application[0]); }
            service.close();
            edt(() -> ThemeTestSupport.controller(true));
        }
    }

    @Test void menusShowTheCurrentBindingOfAnActionAndNothingForAnUnknownOne() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, null, false, dirs);
            boolean mac = System.getProperty("os.name").startsWith("Mac");
            var expected = dev.jasper.app.config.KeyBindings.display(dev.jasper.app.config.KeyBindings.defaults(mac).strokeFor(dev.jasper.app.commands.ActionId.NEW_TAB).orElseThrow(), mac);
            assertThat(application[0].shortcutLabel("new_tab")).isEqualTo(expected).isNotEmpty();
            assertThat(application[0].shortcutLabel("dev.example.unknown")).isEmpty();
            assertThat(application[0].themes().appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME).shortcut("new_tab")).isEqualTo(expected);
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    private static final String LINE_EDITOR = """
        package fix.lines;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.platform().editor((file, line, window) -> {
                    try {
                        Files.writeString(context.dataDirectory().resolve("edited"),
                            file.getFileName() + "|" + (line.isPresent() ? String.valueOf(line.getAsInt()) : "-") + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return true;
                });
            }
        }
        """;

    /** Review Focus 1, the application's half: a clicked file opens at its line when it is an existing regular file. */
    @Test void aClickedFileOpensAtItsLineOnlyWhenItIsAnExistingRegularFile() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("lines-plugin");
        PluginJars.build(dev, "lines.jar", PluginJars.descriptor("dev.example.lines", "1.0.0", "fix.lines.Main")
            + "capabilities = [\"editor.provide\"]\n", Map.of("fix.lines.Main", LINE_EDITOR), List.of());
        Path file = java.nio.file.Files.writeString(home.resolve("Foo.java"), "class Foo { }\n");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, dev, false, dirs);
            java.util.UUID window = java.util.UUID.randomUUID();
            application[0].openFileAt(home.resolve("Missing.java"), 3, window);
            application[0].openFileAt(home, 4, window);
            application[0].openFileAt(file, 42, window);
            application[0].openFileAt(file, 0, window);
        });
        Path edited = dirs.plugins().resolve("dev.example.lines/data/edited");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline
            && (!java.nio.file.Files.exists(edited) || java.nio.file.Files.readAllLines(edited).size() < 2)) Thread.sleep(10);
        assertThat(java.nio.file.Files.readAllLines(edited)).as("not the missing file, nor the directory")
            .containsExactlyInAnyOrder("Foo.java|42", "Foo.java|-");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * Ruling 6: the file's path is program output, so the desktop fallback edits or reveals it and never opens it, which
     * would run a script or program: before the plugins start, and when no plugin provides an editor.
     */
    @Test void aClickedFilesDesktopFallbackEditsOrRevealsAndNeverOpens() throws Exception {
        Path script = java.nio.file.Files.writeString(home.resolve("run.command"), "#!/bin/sh\n");
        var calls = new CopyOnWriteArrayList<String>();
        var desktop = new dev.jasper.app.platform.ConfigEditor(
            path -> { calls.add("edit:" + path.getFileName()); throw new UnsupportedOperationException("no edit action"); },
            path -> calls.add("open:" + path.getFileName()), path -> calls.add("reveal:" + path.getFileName()));
        java.util.UUID window = java.util.UUID.randomUUID();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].openFileAt(script, 5, window, desktop);
        });
        awaitCalls(calls, 2);
        assertThat(calls).as("plugins not started").containsExactly("edit:run.command", "reveal:run.command");

        calls.clear();
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("life-plugin");
        PluginJars.build(dev, "life.jar", PluginJars.descriptor("dev.example.life", "1.0.0", "fix.life.Main"),
            Map.of("fix.life.Main", FIXTURE), List.of());
        edt(() -> {
            application[0].startPlugins(null, dev, false, dirs);
            application[0].openFileAt(script, 0, window, desktop);
        });
        awaitCalls(calls, 2);
        assertThat(calls).as("plugins running, none provides an editor").containsExactly("edit:run.command", "reveal:run.command");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    @Test void aRequestedFilesDesktopFallbackOnlyReveals() throws Exception {
        Path notes = java.nio.file.Files.writeString(home.resolve("notes.txt"), "notes\n");
        var calls = new CopyOnWriteArrayList<String>();
        var desktop = new dev.jasper.app.platform.ConfigEditor(path -> calls.add("edit:" + path.getFileName()),
            path -> calls.add("open:" + path.getFileName()), path -> calls.add("reveal:" + path.getFileName()));
        java.util.UUID window = java.util.UUID.randomUUID();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].openRequestedFile(notes, window, desktop);
        });
        awaitCalls(calls, 1);
        assertThat(calls).as("plugins not started: the desktop's edit action could hand the file back to Jasper")
            .containsExactly("reveal:notes.txt");

        calls.clear();
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("life-plugin");
        PluginJars.build(dev, "life.jar", PluginJars.descriptor("dev.example.life", "1.0.0", "fix.life.Main"),
            Map.of("fix.life.Main", FIXTURE), List.of());
        edt(() -> {
            application[0].startPlugins(null, dev, false, dirs);
            application[0].openRequestedFile(notes, window, desktop);
        });
        awaitCalls(calls, 1);
        assertThat(calls).as("plugins running, none provides an editor").containsExactly("reveal:notes.txt");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private static void awaitCalls(List<String> calls, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && calls.size() < count) Thread.sleep(10);
    }
}
