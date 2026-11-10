package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.testsupport.LogRecorder;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.app.workspace.JasperWindow;
import dev.jasper.app.workspace.LinkClick;
import dev.jasper.terminal.view.TerminalLink;
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
            // A stand-in frame opens a window headless, so Quit has a window to save (workspace plan 6a Task 9).
            application[0].frames = window -> new dev.jasper.app.workspace.WindowShell(() -> { }, () -> { }, () -> { }, () -> { },
                () -> null, component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            application[0].openFirstWindow(List.of(), List.of()).orElseThrow();
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
        java.nio.file.Files.writeString(dirs.configFile(), "ui.appearance='light'\n[ui.theme]\ndark='jasper-meadow'\nlight='jasper-meadow'\n");
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

    private static final String ORIGIN_EDITOR = """
        package fix.origin;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import dev.jasper.sdk.ui.FileEditor;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.platform().editor(new FileEditor() {
                    @Override public boolean edit(java.nio.file.Path file, java.util.OptionalInt line,
                                                  java.util.Optional<dev.jasper.sdk.WindowOwner> window) {
                        throw new AssertionError("Jasper calls the four-argument edit");
                    }
                    @Override public boolean edit(java.nio.file.Path file, java.util.OptionalInt line,
                                                  java.util.Optional<dev.jasper.sdk.WindowOwner> window, dev.jasper.sdk.ui.FileOrigin origin) {
                        try {
                            Files.writeString(context.dataDirectory().resolve("edited"), file.getFileName() + "|" + origin + "\\n",
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                        return true;
                    }
                });
            }
        }
        """;

    /** Plan 1 Task 3: openRequestedFile, the funnel for files other programs hand over, is OUTSIDE; Jasper's own opens are JASPER. */
    @Test void handedInFilesCarryTheOutsideOrigin() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("origin-plugin");
        PluginJars.build(dev, "origin.jar", PluginJars.descriptor("dev.example.origin", "1.0.0", "fix.origin.Main")
            + "capabilities = [\"editor.provide\"]\n", Map.of("fix.origin.Main", ORIGIN_EDITOR), List.of());
        Path handed = java.nio.file.Files.writeString(home.resolve("Handed.java"), "class Handed { }\n");
        Path picked = java.nio.file.Files.writeString(home.resolve("Picked.java"), "class Picked { }\n");
        Path clicked = java.nio.file.Files.writeString(home.resolve("Clicked.java"), "class Clicked { }\n");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        java.util.UUID window = java.util.UUID.randomUUID();
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, dev, false, dirs);
            application[0].openRequestedFile(handed, window);
            application[0].openFileAt(picked, 0, window);
            application[0].openClickedFile(clicked, 0, window);
        });
        assertThat(awaitLines(dirs.plugins().resolve("dev.example.origin/data/edited"), 3))
            .containsExactlyInAnyOrder("Handed.java|OUTSIDE", "Picked.java|JASPER", "Clicked.java|JASPER");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /** The origin fixture with one change: it declines Jasper's own opens, as the Editor does with Default editor "system". */
    private static final String DECLINING_EDITOR = ORIGIN_EDITOR.replace("fix.origin", "fix.loop")
        .replace("return true;", "return origin != dev.jasper.sdk.ui.FileOrigin.JASPER;");

    /**
     * Plan 2 Task 8, the loop check plan 1 left over: with Jasper the system's handler, Jasper's own open is declined by
     * the editor (as with Default editor system), the desktop's edit action hands the file back as an outside open, and
     * the editor takes that one. Once each, then nothing more.
     */
    @Test void theLoopEndsInTheJasperEditorOnce() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("loop-plugin");
        PluginJars.build(dev, "loop.jar", PluginJars.descriptor("dev.example.loop", "1.0.0", "fix.loop.Main")
            + "capabilities = [\"editor.provide\"]\n", Map.of("fix.loop.Main", DECLINING_EDITOR), List.of());
        Path file = java.nio.file.Files.writeString(home.resolve("Note.txt"), "text\n");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        var desktop = new dev.jasper.app.platform.ConfigEditor(
            path -> javax.swing.SwingUtilities.invokeLater(() -> application[0].filesOpened(List.of(path.toFile()))),
            path -> { throw new AssertionError("never opened"); }, path -> { });
        java.util.UUID window = java.util.UUID.randomUUID();
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, dev, false, dirs);
            framesHeadless(application[0]);
            application[0].openFirstWindow(List.of(), List.of()).orElseThrow();
            application[0].openFileAt(file, 0, window, desktop);
        });
        Path edited = dirs.plugins().resolve("dev.example.loop/data/edited");
        List<String> lines = awaitLines(edited, 2);
        assertThat(lines).containsExactly("Note.txt|JASPER", "Note.txt|OUTSIDE");
        Thread.sleep(2000);
        assertThat(java.nio.file.Files.readAllLines(edited)).as("no third open").containsExactly("Note.txt|JASPER", "Note.txt|OUTSIDE");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /** Plan 1 final review: every route a program can hand a file over (launch, open-file event, open-URI, second launch) is OUTSIDE. */
    @Test void everyHandedInRouteCarriesTheOutsideOrigin() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("origin-routes-plugin");
        PluginJars.build(dev, "origin.jar", PluginJars.descriptor("dev.example.origin", "1.0.0", "fix.origin.Main")
            + "capabilities = [\"editor.provide\"]\n", Map.of("fix.origin.Main", ORIGIN_EDITOR), List.of());
        Path launched = java.nio.file.Files.writeString(home.resolve("Launched.java"), "class Launched { }\n");
        Path opened = java.nio.file.Files.writeString(home.resolve("Opened.java"), "class Opened { }\n");
        Path uri = java.nio.file.Files.writeString(home.resolve("Uri.java"), "class Uri { }\n");
        Path second = java.nio.file.Files.writeString(home.resolve("Second.java"), "class Second { }\n");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, dev, false, dirs);
            framesHeadless(application[0]);
            application[0].openFirstWindow(List.of(launched), List.of()).orElseThrow();
            application[0].filesOpened(List.of(opened.toFile()));
            application[0].uriOpened(uri.toUri());
            application[0].handedOff(List.of(second), List.of(), List.of(), false);
        });
        assertThat(awaitLines(dirs.plugins().resolve("dev.example.origin/data/edited"), 4)).containsExactlyInAnyOrder(
            "Launched.java|OUTSIDE", "Opened.java|OUTSIDE", "Uri.java|OUTSIDE", "Second.java|OUTSIDE");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

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

    /**
     * A web browser that appends {@code <address>|<origin>|<window id or ->} to {@code data/opened} and
     * {@code <address>|<placement>|<target or ->} to {@code data/placed}, and declines {@code /declined}.
     */
    private static final String WEB_BROWSER = """
        package fix.web;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.platform().browser(link -> {
                    try {
                        Files.writeString(context.dataDirectory().resolve("opened"),
                            link.address() + "|" + link.origin() + "|"
                                + link.window().map(owner -> ((dev.jasper.sdk.terminal.WindowHandle) owner).id().toString()).orElse("-") + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        Files.writeString(context.dataDirectory().resolve("placed"),
                            link.address() + "|" + link.placement() + "|" + link.target().orElse("-") + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return !link.address().getPath().equals("/declined");
                });
            }
        }
        """;

    /**
     * Browser plan 3, Review Focus 1 to 3: a clicked web link goes to the browser a plugin provides, as a click; one it
     * declines, any other link the terminal opens and every link before the plugins start go to the system browser, and a
     * link that is no URI opens nothing.
     */
    @Test void aClickedWebLinkGoesToTheProvidedBrowserAndAnyOtherLinkToTheSystemBrowser() throws Exception {
        var system = new CopyOnWriteArrayList<String>();
        java.util.UUID window = java.util.UUID.randomUUID();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
            application[0].openLink(clicked("https://example.com/early"), window);
        });
        assertThat(system).as("before the plugins start").containsExactly("https://example.com/early");

        system.clear();
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        edt(() -> {
            application[0].startPlugins(null, dev, false, dirs);
            application[0].openLink(clicked("https://example.com/a"), window);
            application[0].openLink(clicked("HTTPS://Example.com/declined"), window);
            application[0].openLink(clicked("mailto:someone@example.com"), window);
            application[0].openLink(clicked("ftp://files.example.com/x"), window);
            application[0].openLink(clicked("https://example.com/a b"), window);
        });
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened")))
            .as("as clicks; a window that is not open is no window")
            .containsExactly("https://example.com/a|CLICK|-", "HTTPS://Example.com/declined|CLICK|-");
        assertThat(system).as("the declined address, mailto and ftp; nothing for a link that is no URI")
            .containsExactly("HTTPS://Example.com/declined", "mailto:someone@example.com", "ftp://files.example.com/x");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * Browser plan 3b (R4): an address with an authority that is no host, such as {@code http://my_host/}, is no
     * {@code WebLink}; it never reaches the provided browser and never throws, and the system browser opens it as before.
     */
    @Test void anAddressNoWebLinkTakesGoesToTheSystemBrowserWithoutAskingTheProvidedBrowser() throws Exception {
        var system = new CopyOnWriteArrayList<String>();
        java.util.UUID window = java.util.UUID.randomUUID();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
            application[0].startPlugins(null, dev, false, dirs);
            application[0].openLink(clicked("http://my_host/"), window);
            application[0].openLink(clicked("https://example.com/a"), window);
        });
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened")))
            .as("only the address with a host reached the browser").containsExactly("https://example.com/a|CLICK|-");
        assertThat(system).containsExactly("http://my_host/");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * Browser plan 3, the user's ruling ("route Help and About through it too"): Jasper's own web links, which Settings'
     * Help button, the Licenses window and the plugin manager open through {@code browse}, go to the browser a plugin
     * provides as clicks from the active window, else to the system browser.
     */
    @Test void jaspersOwnWebLinksGoToTheProvidedBrowserAndOtherwiseTheSystemBrowser() throws Exception {
        var system = new CopyOnWriteArrayList<String>();
        java.net.URI help = java.net.URI.create(dev.jasper.app.settingsui.SettingsDialog.HELP_URL + "keybindings");
        java.net.URI declined = java.net.URI.create("https://example.com/declined");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        java.util.UUID[] active = new java.util.UUID[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
            application[0].browse(help);
        });
        assertThat(system).as("before the plugins start").containsExactly(help.toString());

        system.clear();
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        edt(() -> {
            application[0].startPlugins(null, dev, false, dirs);
            application[0].browse(help);
            // A stand-in frame opens a window headless, as pluginsStartBeforeWindowsAndStopBeforeTermination does.
            application[0].frames = window -> new dev.jasper.app.workspace.WindowShell(() -> { }, () -> { }, () -> { }, () -> { },
                () -> null, component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            application[0].openFirstWindow(List.of(), List.of()).orElseThrow();
            active[0] = application[0].terminals().windows().getFirst().id();
            application[0].terminals().windowActivated(active[0]);
            application[0].browse(help);
            application[0].browse(declined);
        });
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened")))
            .as("as clicks: with no window active, then from the active one")
            .containsExactly(help + "|CLICK|-", help + "|CLICK|" + active[0], declined + "|CLICK|" + active[0]);
        assertThat(system).as("only the address the browser declined").containsExactly(declined.toString());
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * A web browser that appends {@code link:} and whether Jasper is the default browser, as it decides each link, to
     * {@code data/default}, and {@code changed:} and the new answer each time it changes.
     */
    private static final String DEFAULT_ASKING_BROWSER = """
        package fix.web;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.platform().browser(link -> {
                    write(context, "link:" + context.platform().isDefaultBrowser());
                    return true;
                });
                context.platform().onDefaultBrowserChanged(jasper -> write(context, "changed:" + jasper));
            }
            private static void write(PluginContext context, String line) {
                try {
                    Files.writeString(context.dataDirectory().resolve("default"), line + "\\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
        """;

    /**
     * Browser plan 4c Task 2: a plugin's {@code Platform.isDefaultBrowser} gives the application's last answer to whether
     * Jasper is the system's default browser, an answer that came after the plugins started included, and its
     * {@code onDefaultBrowserChanged} handler hears that answer change (ruling Q7).
     */
    @Test void pluginsSeeTheApplicationsAnswerToWhetherJasperIsTheDefaultBrowser() throws Exception {
        var answer = new java.util.concurrent.atomic.AtomicBoolean(false);
        var clock = new dev.jasper.app.testsupport.MutableClock();
        var check = new dev.jasper.app.platform.DefaultBrowser(Runnable::run, clock, () -> Optional.of(answer.get()));
        java.util.UUID window = java.util.UUID.randomUUID();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", DEFAULT_ASKING_BROWSER), List.of());
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> { };
            application[0].defaultBrowser(check);
            application[0].startPlugins(null, dev, false, dirs);
            application[0].openLink(clicked("https://example.com/a"), window);
            answer.set(true);
            clock.advance(java.time.Duration.ofSeconds(30));
            check.refresh();
            application[0].openLink(clicked("https://example.com/b"), window);
        });
        edt(() -> { });
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/default")))
            .as("no, then yes once a check answers it, which the change handler then hears on the EDT")
            .containsExactly("link:false", "link:true", "changed:true");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * Browser plan 3b: {@code jasper <address>} reaches the provided browser as an external address, as a tab with no
     * target, in the window of the pane that ran the command (R9), or in none when the call names no pane.
     */
    @Test void aWebAddressGivenToTheCommandIsExternal() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        var system = new CopyOnWriteArrayList<String>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        java.util.UUID[] pane = new java.util.UUID[1];
        java.util.UUID[] calling = new java.util.UUID[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
            application[0].startPlugins(null, dev, false, dirs);
            // A stand-in frame opens a window headless, as pluginsStartBeforeWindowsAndStopBeforeTermination does.
            application[0].frames = window -> new dev.jasper.app.workspace.WindowShell(() -> { }, () -> { }, () -> { }, () -> { },
                () -> null, component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            application[0].openFirstWindow(List.of(), List.of()).orElseThrow();
            calling[0] = application[0].terminals().windows().getFirst().id();
            pane[0] = application[0].terminals().windows().getFirst().selectedTab().get()
                .flatMap(tab -> tab.terminal().get()).orElseThrow().id();
        });
        var commands = application[0].commands();
        var fromAPane = commands.dispatch(new dev.jasper.app.commandline.Invocation(List.of("https://example.com/c", "https://example.com/d"),
            home, Optional.of(pane[0]), text -> { }, text -> { }));
        assertThat(fromAPane.toCompletableFuture().get(5, TimeUnit.SECONDS)).isZero();
        var fromElsewhere = commands.dispatch(new dev.jasper.app.commandline.Invocation(List.of("https://example.com/sign-in"),
            home, Optional.empty(), text -> { }, text -> { }));
        assertThat(fromElsewhere.toCompletableFuture().get(5, TimeUnit.SECONDS)).isZero();
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened"))).containsExactly(
            "https://example.com/c|EXTERNAL|" + calling[0], "https://example.com/d|EXTERNAL|" + calling[0], "https://example.com/sign-in|EXTERNAL|-");
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/placed"))).as("each in a tab, with no target")
            .containsExactly("https://example.com/c|TAB|-", "https://example.com/d|TAB|-", "https://example.com/sign-in|TAB|-");
        assertThat(system).as("the browser took each one").isEmpty();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * Browser plan 4c Task 3, Review Focus 1: web addresses from outside that arrive before the plugins start wait for
     * them, and then reach the provided browser as external addresses, in arrival order; none reaches the system browser.
     * Each call is answered once its addresses have been handed on.
     */
    @Test void webAddressesFromOutsideBeforeThePluginsStartWaitForThemAndThenReachTheBrowser() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        var system = new CopyOnWriteArrayList<String>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
        });
        var first = dispatch(application[0], "https://example.com/first", "https://example.com/second");
        var second = dispatch(application[0], "https://example.com/third");
        edt(() -> { });
        assertThat(system).as("waiting for the plugins").isEmpty();
        assertThat(first).as("answered once they have been handed on").isNotDone();
        assertThat(second).isNotDone();
        edt(() -> application[0].startPlugins(null, dev, false, dirs));
        assertThat(first.get(5, TimeUnit.SECONDS)).isZero();
        assertThat(second.get(5, TimeUnit.SECONDS)).isZero();
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened"))).containsExactly(
            "https://example.com/first|EXTERNAL|-", "https://example.com/second|EXTERNAL|-", "https://example.com/third|EXTERNAL|-");
        assertThat(system).isEmpty();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * Browser plan 4c Task 3: at most 32 web addresses from outside wait for the plugins; the 33rd is dropped, its call
     * says so on its error stream and exits with 1, and the log says so without the address.
     */
    @Test void theThirtyThirdWebAddressWaitingForThePluginsIsDropped() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        var system = new CopyOnWriteArrayList<String>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
        });
        List<String> addresses = java.util.stream.IntStream.rangeClosed(1, 33).mapToObj(n -> "https://example.com/" + n).toList();
        var firstErr = new StringBuffer();
        var secondErr = new StringBuffer();
        java.util.concurrent.CompletableFuture<Integer> first;
        java.util.concurrent.CompletableFuture<Integer> second;
        try (var log = LogRecorder.of(ExternalAddresses.class.getName())) {
            first = dispatch(application[0], firstErr, addresses.subList(0, 30).toArray(String[]::new));
            second = dispatch(application[0], secondErr, addresses.subList(30, 33).toArray(String[]::new));
            edt(() -> { });
            assertThat(log.warnings()).containsExactly("A web address from outside was dropped: 32 were already waiting for Jasper's browser");
        }
        edt(() -> application[0].startPlugins(null, dev, false, dirs));
        assertThat(first.get(5, TimeUnit.SECONDS)).isZero();
        assertThat(second.get(5, TimeUnit.SECONDS)).as("one of its addresses was dropped").isOne();
        assertThat(firstErr).isEmpty();
        assertThat(secondErr).hasToString("jasper: too many web addresses are waiting; https://example.com/33 was not opened\n");
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened")))
            .containsExactlyElementsOf(addresses.subList(0, 32).stream().map(address -> address + "|EXTERNAL|-").toList());
        assertThat(system).as("the dropped address goes nowhere").isEmpty();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * Browser plan 4c Task 4: a launch's web addresses wait, as any from outside, for the first answer to whether Jasper
     * is the default browser, and then open once each, as external addresses, in the launch's first window; no other
     * window opens and none reaches the system browser.
     */
    @Test void aLaunchsWebAddressesWaitForTheBrowserAndOpenOnceInItsFirstWindow() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path plugin = home.resolve("web-plugin");
        PluginJars.build(plugin, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        var system = new CopyOnWriteArrayList<String>();
        var checks = new CopyOnWriteArrayList<Runnable>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        JasperWindow[] first = new JasperWindow[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
            application[0].defaultBrowser(dev.jasper.app.platform.DefaultBrowserTestAccess.withFirstAnswerWithin(checks::add,
                new dev.jasper.app.testsupport.MutableClock(), () -> Optional.of(false), java.time.Duration.ofHours(1)));
            application[0].startPlugins(null, plugin, false, dirs);
            framesHeadless(application[0]);
            first[0] = application[0].openFirstWindow(List.of(),
                List.of(java.net.URI.create("https://example.com/a"), java.net.URI.create("HTTP://Example.com/b"))).orElseThrow();
        });
        Path opened = dirs.plugins().resolve("dev.example.web/data/opened");
        edt(() -> { });
        assertThat(opened).as("waiting for the first answer").doesNotExist();
        checks.getFirst().run();
        assertThat(awaitLines(opened, 2)).containsExactly("https://example.com/a|EXTERNAL|" + first[0].id(),
            "HTTP://Example.com/b|EXTERNAL|" + first[0].id());
        edt(() -> { });
        edt(() -> { });
        assertThat(java.nio.file.Files.readAllLines(opened)).as("once each").hasSize(2);
        edt(() -> assertThat(application[0].terminals().windows()).as("no other window").hasSize(1));
        assertThat(system).isEmpty();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * Browser plan 4c Task 4, Review Focus 3: a second launch's web addresses, handed over while the owner still waits for
     * its first answer, open once each, after its paths, in the most recently active window; a later one opens at once,
     * and one handed over while Jasper quits opens nothing.
     */
    @Test void aSecondLaunchsWebAddressesOpenOnceInTheMostRecentlyActiveWindow() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path plugin = home.resolve("web-plugin");
        PluginJars.build(plugin, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        var system = new CopyOnWriteArrayList<String>();
        var checks = new CopyOnWriteArrayList<Runnable>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        JasperWindow[] first = new JasperWindow[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
            application[0].defaultBrowser(dev.jasper.app.platform.DefaultBrowserTestAccess.withFirstAnswerWithin(checks::add,
                new dev.jasper.app.testsupport.MutableClock(), () -> Optional.of(false), java.time.Duration.ofHours(1)));
            application[0].startPlugins(null, plugin, false, dirs);
            framesHeadless(application[0]);
            first[0] = application[0].openFirstWindow(List.of(), List.of()).orElseThrow();
        });
        Path source = Path.of("/same.jar");
        var handler = dev.jasper.app.bootstrap.BootstrapTestSupport.handoffHandler(application[0], source, 7L);
        assertThat(handler.apply(dev.jasper.app.residency.LaunchRequest.of(source, 7L, home, List.of(home),
            List.of(java.net.URI.create("https://example.com/a"), java.net.URI.create("https://example.com/b")), List.of())))
            .isEqualTo(dev.jasper.app.residency.LaunchRequest.Response.OK);
        edt(() -> { });
        edt(() -> assertThat(first[0].content().terminalTabs()).as("its path opened at once").hasSize(2));
        Path opened = dirs.plugins().resolve("dev.example.web/data/opened");
        assertThat(opened).as("waiting for the first answer").doesNotExist();
        checks.getFirst().run();
        assertThat(awaitLines(opened, 2)).containsExactly("https://example.com/a|EXTERNAL|" + first[0].id(),
            "https://example.com/b|EXTERNAL|" + first[0].id());
        assertThat(handler.apply(dev.jasper.app.residency.LaunchRequest.of(source, 7L, home, List.of(),
            List.of(java.net.URI.create("https://example.com/later")), List.of())))
            .isEqualTo(dev.jasper.app.residency.LaunchRequest.Response.OK);
        assertThat(awaitLines(opened, 3)).endsWith("https://example.com/later|EXTERNAL|" + first[0].id());
        edt(() -> { });
        assertThat(java.nio.file.Files.readAllLines(opened)).as("once each").hasSize(3);
        assertThat(system).isEmpty();
        edt(() -> {
            application[0].quit();
            application[0].handedOff(List.of(), List.of(java.net.URI.create("https://example.com/quitting")), List.of(), false);
        });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
        assertThat(java.nio.file.Files.readAllLines(opened)).as("nothing once Jasper quits").hasSize(3);
        assertThat(system).isEmpty();
    }

    /**
     * Browser plan 4c Task 4, Review Focus 2: the macOS open-URI event sends a web address to the browser as an external
     * address in the most recently active window, and a {@code file:} URL, even one for a page the browser could show, to
     * the editor as a file, in every form RFC 8089 gives it ({@code localhost}, a query, a fragment). One naming another
     * host opens nothing, and the log says why; nothing opens once Jasper quits.
     */
    @Test void theOpenUriEventSendsWebAddressesToTheBrowserAndFileUrlsToTheEditor() throws Exception {
        Path page = java.nio.file.Files.writeString(home.resolve("index.html"), "<p>hi</p>\n");
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var terminated = new CountDownLatch(1);
        JasperApplication application = startedWithBrowserAndEditor(dirs, terminated);
        Path browsed = dirs.plugins().resolve("dev.example.both/data/browsed");
        Path edited = dirs.plugins().resolve("dev.example.both/data/edited");
        JasperWindow[] window = new JasperWindow[1];
        try (var log = LogRecorder.of(JasperApplication.class.getName())) {
            edt(() -> {
                framesHeadless(application);
                window[0] = application.openFirstWindow(List.of(), List.of()).orElseThrow();
                application.uriOpened(java.net.URI.create("https://example.com/from-the-system"));
                application.uriOpened(page.toUri());
                application.uriOpened(java.net.URI.create("file://localhost" + page.toUri().getRawPath() + "?a=1#top"));
                application.uriOpened(java.net.URI.create("file://server/share/index.html"));
                application.uriOpened(java.net.URI.create("mailto:someone@example.com"));
            });
            assertThat(awaitLines(browsed, 1)).containsExactly("https://example.com/from-the-system|EXTERNAL|TAB|" + window[0].id());
            assertThat(awaitLines(edited, 2)).containsExactly("index.html|-", "index.html|-");
            edt(() -> { });
            assertThat(java.nio.file.Files.readAllLines(browsed)).as("a file: URL is a file, not a page").hasSize(1);
            assertThat(log.records()).extracting(java.util.logging.LogRecord::getMessage)
                .filteredOn(message -> message.startsWith("The system handed Jasper")).containsExactly(
                "The system handed Jasper a file: URL that names no local file; it opens nothing",
                "The system handed Jasper an address that is neither a web address nor a file: URL; it opens nothing");
        }
        edt(() -> {
            application.quit();
            application.uriOpened(java.net.URI.create("https://example.com/quitting"));
            application.uriOpened(page.toUri());
        });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application);
        assertThat(java.nio.file.Files.readAllLines(browsed)).hasSize(1);
        assertThat(java.nio.file.Files.readAllLines(edited)).hasSize(2);
    }

    /**
     * Browser plan 4c Task 4, ruling Q12: with no window open, a web address from the system, and one a second launch
     * hands over alone, first restore a window, as every outside request does, timed under its own trigger, and then
     * open in it; no other window opens and nothing reaches the system browser.
     */
    @Test void withNoWindowOpenAWebAddressFromOutsideRestoresAWindowAndOpensInIt() throws Exception {
        for (String trigger : List.of(JasperApplication.OPEN_URI, JasperApplication.HANDOFF)) {
            Path root = home.resolve(trigger);
            AppDirs dirs = new AppDirs(root, root.resolve("config.toml"), root.resolve("logs"));
            Path plugin = root.resolve("web-plugin");
            PluginJars.build(plugin, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
                + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
            var system = new CopyOnWriteArrayList<String>();
            var terminated = new CountDownLatch(1);
            var trace = StartupTrace.detached();
            JasperApplication[] application = new JasperApplication[1];
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                application[0].trace(trace);
                application[0].systemBrowser = address -> system.add(address.toString());
                application[0].startPlugins(null, plugin, false, dirs);
                framesHeadless(application[0]);
            });
            java.net.URI address = java.net.URI.create("https://example.com/" + trigger);
            edt(() -> {
                assertThat(application[0].terminals().windows()).as("no window is open").isEmpty();
                if (trigger.equals(JasperApplication.OPEN_URI)) application[0].uriOpened(address);
                else application[0].handedOff(List.of(), List.of(address), List.of(), false);
            });
            List<String> opened = awaitLines(dirs.plugins().resolve("dev.example.web/data/opened"), 1);
            java.util.UUID[] window = new java.util.UUID[1];
            edt(() -> {
                assertThat(application[0].terminals().windows()).as(trigger).hasSize(1);
                window[0] = application[0].terminals().windows().getFirst().id();
            });
            assertThat(opened).as(trigger).containsExactly(address + "|EXTERNAL|" + window[0]);
            assertThat(trace.windows()).as(trigger).extracting(dev.jasper.app.startup.WindowTrace::trigger).containsExactly(trigger);
            assertThat(system).isEmpty();
            edt(application[0]::quit);
            assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            ApplicationPluginCleanup.await(application[0]);
        }
    }

    /** A stand-in frame, so a window opens headless, as {@code pluginsStartBeforeWindowsAndStopBeforeTermination} does. EDT. */
    private static void framesHeadless(JasperApplication application) {
        application.frames = window -> new dev.jasper.app.workspace.WindowShell(() -> { }, () -> { }, () -> { }, () -> { },
            () -> null, component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
    }

    /**
     * Browser plan 4c Task 3, Review Focus 1 and rulings Q1, Q2 and Q6: with no browser provided while Jasper is the
     * system's default browser, a web address from outside waits for the first answer to whether it is, then posts
     * "Web links can’t open in Jasper", as a clicked one and Jasper's own links do, and none reaches the system browser,
     * which would hand it back. Other schemes still go to the system. Copy Link copies the address; Turn On Browser
     * without the Browser plugin says it could not.
     */
    @Test void withNoBrowserWhileJasperIsTheDefaultAWebAddressPostsTheNoticeAndNeverReachesTheSystem() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var desktop = new CopyOnWriteArrayList<String>();
        var checks = new CopyOnWriteArrayList<Runnable>();
        var copied = new java.awt.datatransfer.Clipboard("test");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].desktop = address -> desktop.add(address.toString());
            application[0].clipboard = new dev.jasper.app.platform.HostClipboard(() -> copied, (delay, task) -> () -> { });
            // A bound the test never reaches: only the held-back check below gives the first answer.
            application[0].defaultBrowser(dev.jasper.app.platform.DefaultBrowserTestAccess.withFirstAnswerWithin(checks::add,
                new dev.jasper.app.testsupport.MutableClock(), () -> Optional.of(true), java.time.Duration.ofHours(1)));
            application[0].startPlugins(null, null, false, dirs);
        });
        var call = dispatch(application[0], "https://example.com/sign-in");
        edt(() -> { });
        assertThat(notices(application[0])).as("the first answer has not come").isEmpty();
        assertThat(call).isNotDone();
        checks.getFirst().run();
        assertThat(call.get(5, TimeUnit.SECONDS)).isZero();
        assertThat(notices(application[0])).singleElement().satisfies(notice -> {
            assertThat(notice.request().group()).isEqualTo("jasper.general");
            assertThat(notice.request().severity()).isEqualTo(dev.jasper.app.notifications.Severity.WARNING);
            assertThat(notice.request().title()).isEqualTo("Web links can’t open in Jasper");
            assertThat(notice.request().content()).isEqualTo("The Browser plugin is off. Turn it on to open web links in Jasper, "
                + "or choose another default browser in your system settings.");
            assertThat(notice.request().actions()).extracting(dev.jasper.app.notifications.NotificationAction::label)
                .containsExactly("Turn On Browser", "Copy Link");
        });
        java.util.UUID window = java.util.UUID.randomUUID();
        edt(() -> {
            application[0].openLink(clicked("https://example.com/clicked"), window);
            application[0].browse(java.net.URI.create(dev.jasper.app.settingsui.SettingsDialog.HELP_URL + "keybindings"));
            application[0].openLink(clicked("mailto:someone@example.com"), window);
        });
        awaitCalls(desktop, 1);
        assertThat(notices(application[0])).singleElement().satisfies(notice -> assertThat(notice.count()).isEqualTo(3));
        edt(() -> notices(application[0]).getFirst().request().actions().get(1).run().run());
        assertThat(copied.getData(java.awt.datatransfer.DataFlavor.stringFlavor)).as("the newest address")
            .isEqualTo(dev.jasper.app.settingsui.SettingsDialog.HELP_URL + "keybindings");
        try (var log = LogRecorder.of(PluginRuntime.class.getName())) {
            edt(() -> notices(application[0]).getFirst().request().actions().get(0).run().run());
            dev.jasper.app.workspace.DesktopTestSupport.until(() -> application[0].notificationCenter().history().stream()
                .anyMatch(notice -> notice.request().title().equals("Could not turn on the Browser")));
            assertThat(log.warnings()).containsExactly("A plugin manager operation failed");
        }
        assertThat(notices(application[0]).getFirst().request().content()).isEqualTo("No such plugin: dev.jasper.browser");
        assertThat(desktop).as("only the mailto link").containsExactly("mailto:someone@example.com");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
        assertThat(desktop).containsExactly("mailto:someone@example.com");
    }

    /**
     * Browser plan 4c Task 3: with no browser provided while Jasper is not the default, a web address from outside, one
     * that arrived before the plugins started included, opens the system browser as before, with no notice.
     */
    @Test void withNoBrowserWhileJasperIsNotTheDefaultAWebAddressOpensTheSystemBrowser() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var desktop = new CopyOnWriteArrayList<String>();
        var check = new dev.jasper.app.platform.DefaultBrowser(Runnable::run, new dev.jasper.app.testsupport.MutableClock(), () -> Optional.of(false));
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].desktop = address -> desktop.add(address.toString());
            application[0].defaultBrowser(check);
        });
        var early = dispatch(application[0], "https://example.com/early");
        edt(() -> application[0].startPlugins(null, null, false, dirs));
        assertThat(early.get(5, TimeUnit.SECONDS)).isZero();
        assertThat(external(application[0], "https://example.com/later")).isZero();
        awaitCalls(desktop, 2);
        // Each desktop open runs on a thread of its own, so they may land in either order.
        assertThat(desktop).containsExactlyInAnyOrder("https://example.com/early", "https://example.com/later");
        assertThat(notices(application[0])).isEmpty();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * A web browser that appends {@code <address>|<origin>|<window id or ->} to {@code data/opened}, and declines
     * {@code /declined}, and that hands {@code /own} back with {@code Platform.browse} as it starts.
     */
    private static final String HANDING_BACK_BROWSER = """
        package fix.web;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.platform().browser(link -> {
                    try {
                        Files.writeString(context.dataDirectory().resolve("opened"),
                            link.address() + "|" + link.origin() + "|"
                                + link.window().map(owner -> ((dev.jasper.sdk.terminal.WindowHandle) owner).id().toString()).orElse("-") + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return !link.address().getPath().equals("/declined");
                });
                context.platform().browse(java.net.URI.create("https://example.com/own"));
            }
        }
        """;

    /** A plugin that asks {@code Platform.browse} for {@code /browsed} and {@code /declined} as it starts. */
    private static final String BROWSING = """
        package fix.browsing;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.platform().browse(java.net.URI.create("https://example.com/browsed"));
                context.platform().browse(java.net.URI.create("https://example.com/declined"));
            }
        }
        """;

    /**
     * Browser plan 4c Task 3, rulings Q2 and Q9: while Jasper is the system's default browser, which would hand an address
     * back, another plugin's {@code Platform.browse} reaches the provided browser as a click from the active window; the
     * provider's own {@code browse}, its hand-back, never comes back to it; and neither one the browser declines nor that
     * hand-back reaches the system browser or posts a notice.
     */
    @Test void whileJasperIsTheDefaultAnotherPluginsBrowseReachesTheProvidedBrowserAndItsOwnNever() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", HANDING_BACK_BROWSER), List.of());
        // The other plugin is bundled: plugins beside the application jar.
        Path code = java.nio.file.Files.createDirectories(home.resolve("app")).resolve("jasper-app.jar");
        java.nio.file.Files.writeString(code, "");
        PluginJars.build(home.resolve("app/plugins/dev.example.browsing"), "browsing.jar",
            PluginJars.descriptor("dev.example.browsing", "1.0.0", "fix.browsing.Main"), Map.of("fix.browsing.Main", BROWSING), List.of());
        var desktop = new CopyOnWriteArrayList<String>();
        var check = new dev.jasper.app.platform.DefaultBrowser(Runnable::run, new dev.jasper.app.testsupport.MutableClock(), () -> Optional.of(true));
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try (var log = LogRecorder.of(ExternalAddresses.class.getName())) {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                application[0].desktop = address -> desktop.add(address.toString());
                application[0].defaultBrowser(check);
                application[0].startPlugins(code, dev, false, dirs);
            });
            assertThat(application[0].pluginRuntime().pluginNames()).containsKeys("dev.example.web", "dev.example.browsing");
            assertThat(awaitLines(dirs.plugins().resolve("dev.example.web/data/opened"), 2)).as("never the provider's own")
                .containsExactly("https://example.com/browsed|CLICK|-", "https://example.com/declined|CLICK|-");
            // The declined address and the hand-back each reach the fallback, which opens nothing.
            awaitRecords(log, 2);
            assertThat(log.records()).extracting(java.util.logging.LogRecord::getMessage).containsOnly(OPENS_NOTHING);
        }
        assertThat(notices(application[0])).isEmpty();
        assertThat(desktop).isEmpty();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
        edt(() -> { });
        assertThat(desktop).as("neither one goes anywhere").isEmpty();
    }

    /**
     * Browser plan 4c Task 3, ruling Q1: while Jasper is the system's default browser, a clicked link and a
     * {@code jasper <url>} that the provided browser declines (as the Browser does once Chromium could not start) open
     * nothing, post no notice, and never reach the system browser.
     */
    @Test void whileJasperIsTheDefaultALinkTheProvidedBrowserDeclinesOpensNothing() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        var desktop = new CopyOnWriteArrayList<String>();
        var check = new dev.jasper.app.platform.DefaultBrowser(Runnable::run, new dev.jasper.app.testsupport.MutableClock(), () -> Optional.of(true));
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        java.util.UUID window = java.util.UUID.randomUUID();
        try (var log = LogRecorder.of(ExternalAddresses.class.getName())) {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                application[0].desktop = address -> desktop.add(address.toString());
                application[0].defaultBrowser(check);
                application[0].startPlugins(null, dev, false, dirs);
                application[0].openLink(clicked("https://example.com/declined"), window);
            });
            assertThat(external(application[0], "https://example.com/declined")).isZero();
            assertThat(log.records()).extracting(java.util.logging.LogRecord::getMessage).containsExactly(OPENS_NOTHING, OPENS_NOTHING);
        }
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened")))
            .containsExactly("https://example.com/declined|CLICK|-", "https://example.com/declined|EXTERNAL|-");
        assertThat(notices(application[0])).isEmpty();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
        assertThat(desktop).isEmpty();
    }

    /**
     * Browser plan 4c's final review, ruling Q17: while Jasper is the system's default browser, an address with an
     * internationalized host, in Unicode or percent-encoded, reaches the provided browser with its host in ASCII, from
     * outside and as a click; one no {@code WebLink} takes, such as {@code http://my_host/}, never reached the browser, so
     * "Jasper’s browser can’t open this address" shows it, without its user info, and Copy Link copies it whole. None
     * reaches the system browser.
     */
    @Test void whileJasperIsTheDefaultAnIdnAddressReachesTheBrowserAndOneItCannotTakeSaysSo() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path plugin = home.resolve("web-plugin");
        PluginJars.build(plugin, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        var desktop = new CopyOnWriteArrayList<String>();
        var copied = new java.awt.datatransfer.Clipboard("test");
        var check = new dev.jasper.app.platform.DefaultBrowser(Runnable::run, new dev.jasper.app.testsupport.MutableClock(), () -> Optional.of(true));
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        java.util.UUID window = java.util.UUID.randomUUID();
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].desktop = address -> desktop.add(address.toString());
            application[0].clipboard = new dev.jasper.app.platform.HostClipboard(() -> copied, (delay, task) -> () -> { });
            application[0].defaultBrowser(check);
            application[0].startPlugins(null, plugin, false, dirs);
        });
        assertThat(external(application[0], "https://b\u00fccher.de/a", "https://b%C3%BCcher.de/b")).isZero();
        edt(() -> {
            application[0].openLink(clicked("https://b\u00fccher.de/c"), window);
            application[0].openLink(clicked("http://my_host/"), window);
        });
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened"))).containsExactly(
            "https://xn--bcher-kva.de/a|EXTERNAL|-", "https://xn--bcher-kva.de/b|EXTERNAL|-", "https://xn--bcher-kva.de/c|CLICK|-");
        assertThat(notices(application[0])).singleElement().satisfies(notice -> {
            assertThat(notice.request().group()).isEqualTo("jasper.general");
            assertThat(notice.request().severity()).isEqualTo(dev.jasper.app.notifications.Severity.WARNING);
            assertThat(notice.request().title()).isEqualTo("Jasper’s browser can’t open this address");
            assertThat(notice.request().content()).isEqualTo("http://my_host/");
            assertThat(notice.request().actions()).extracting(dev.jasper.app.notifications.NotificationAction::label)
                .containsExactly("Copy Link");
        });
        assertThat(external(application[0], "http://user:secret@my_service:8080/x")).isZero();
        edt(() -> { });
        var notice = notices(application[0]).getFirst().request();
        assertThat(notice.content()).as("without its user info").isEqualTo("http://my_service:8080/x");
        edt(() -> notice.actions().getFirst().run().run());
        assertThat(copied.getData(java.awt.datatransfer.DataFlavor.stringFlavor)).isEqualTo("http://user:secret@my_service:8080/x");
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened"))).hasSize(3);
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
        assertThat(desktop).isEmpty();
    }

    /**
     * Browser plan 4c Task 3, ruling Q10: a web address that comes back from outside within 10 seconds of Jasper handing
     * it to the system browser, as when Jasper is the default without the check seeing it, never goes back to the system:
     * with no browser provided it posts the notice. Another address still goes to the system browser.
     */
    @Test void aWebAddressThatComesBackFromTheSystemBrowserIsNeverHandedToItAgain() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var desktop = new CopyOnWriteArrayList<String>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        java.util.UUID window = java.util.UUID.randomUUID();
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].desktop = address -> desktop.add(address.toString());
            application[0].startPlugins(null, null, false, dirs);
            application[0].openLink(clicked("https://example.com/loop"), window);
        });
        awaitCalls(desktop, 1);
        // The system hands it back: Jasper is its default browser, which the check did not see.
        assertThat(external(application[0], "https://example.com/loop")).isZero();
        assertThat(notices(application[0])).extracting(notice -> notice.request().title()).containsExactly("Web links can’t open in Jasper");
        assertThat(external(application[0], "https://example.com/other")).isZero();
        awaitCalls(desktop, 2);
        assertThat(desktop).containsExactlyInAnyOrder("https://example.com/loop", "https://example.com/other");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
        assertThat(desktop).hasSize(2);
    }

    /** What the fallback logs for an address the provided browser had and did not open while Jasper is the default. */
    private static final String OPENS_NOTHING =
        "Jasper's browser did not open a web address, and the system would hand it back, so nothing opens";

    /** Waits up to ten seconds for {@code log} to hold {@code count} records. */
    private static void awaitRecords(LogRecorder log, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && log.records().size() < count) Thread.sleep(10);
    }

    /**
     * Browser plan 4c Task 3: Turn On Browser turns the Browser plugin on from the next launch and offers the restart;
     * a restart that cannot work out how Jasper started says so.
     */
    @Test void turnOnBrowserTurnsTheBrowserPluginOnAndOffersTheRestart() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path offBrowser = home.resolve("browser-plugin");
        // A plugin with the Browser's id that provides no browser: the Browser as Jasper sees it while it is off.
        PluginJars.build(offBrowser, "browser.jar", PluginJars.descriptor("dev.jasper.browser", "1.0.0", "fix.life.Main"),
            Map.of("fix.life.Main", FIXTURE), List.of());
        var desktop = new CopyOnWriteArrayList<String>();
        var check = new dev.jasper.app.platform.DefaultBrowser(Runnable::run, new dev.jasper.app.testsupport.MutableClock(), () -> Optional.of(true));
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].desktop = address -> desktop.add(address.toString());
            application[0].restartPlanner = mode -> Optional.empty();
            application[0].defaultBrowser(check);
            application[0].startPlugins(null, offBrowser, false, dirs);
        });
        assertThat(external(application[0], "https://example.com/sign-in")).isZero();
        edt(() -> { });
        assertThat(notices(application[0])).extracting(notice -> notice.request().title()).containsExactly("Web links can’t open in Jasper");
        edt(() -> notices(application[0]).getFirst().request().actions().get(0).run().run());
        dev.jasper.app.workspace.DesktopTestSupport.until(() -> application[0].notificationCenter().history().stream()
            .anyMatch(notice -> notice.request().title().equals("Restart Jasper to turn on the Browser")));
        var offer = notices(application[0]).getFirst().request();
        assertThat(offer.content()).isEqualTo("The Browser plugin starts the next time Jasper starts.");
        assertThat(offer.actions()).extracting(dev.jasper.app.notifications.NotificationAction::label).containsExactly("Restart Now");
        edt(() -> offer.actions().getFirst().run().run());
        assertThat(notices(application[0]).getFirst().request().title()).isEqualTo("Jasper could not restart");
        assertThat(desktop).isEmpty();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /** Dispatches {@code jasper <addresses>} from no pane; the answer comes once they have been handed on. */
    private java.util.concurrent.CompletableFuture<Integer> dispatch(JasperApplication application, String... addresses) {
        return dispatch(application, new StringBuffer(), addresses);
    }

    /** As above, its error stream written to {@code err}. */
    private java.util.concurrent.CompletableFuture<Integer> dispatch(JasperApplication application, StringBuffer err, String... addresses) {
        return application.commands().dispatch(new dev.jasper.app.commandline.Invocation(List.of(addresses), home, Optional.empty(),
            text -> { }, err::append)).toCompletableFuture();
    }

    /** As {@link #dispatch}, waiting for the exit status. */
    private int external(JasperApplication application, String... addresses) throws Exception {
        return dispatch(application, addresses).get(5, TimeUnit.SECONDS);
    }

    /** The notifications posted so far, newest first. Any thread but the EDT, or on it. */
    private static List<dev.jasper.app.notifications.Notification> notices(JasperApplication application) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) return application.notificationCenter().history();
        var read = new java.util.concurrent.atomic.AtomicReference<List<dev.jasper.app.notifications.Notification>>();
        try { edt(() -> read.set(application.notificationCenter().history())); }
        catch (Exception failure) { throw new AssertionError(failure); }
        return read.get();
    }

    /**
     * Browser plan 3b: a link clicked with the split gesture reaches the provided browser as a click that asks for a split
     * of its pane, with its OSC 8 target; a browser that declines it, or a target no {@code WebLink} holds, leaves it to the
     * system browser.
     */
    @Test void aSplitClickReachesTheBrowserAsSplitRight() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        var system = new CopyOnWriteArrayList<String>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        java.util.UUID window = java.util.UUID.randomUUID();
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
            application[0].startPlugins(null, dev, false, dirs);
            var runtime = application[0].pluginRuntime();
            runtime.openLink(java.net.URI.create("https://example.com/r"), PluginRuntime.Placement.SPLIT_RIGHT,
                Optional.of("t"), Optional.of(window), application[0].systemBrowser);
            runtime.openLink(java.net.URI.create("https://example.com/d"), PluginRuntime.Placement.SPLIT_DOWN,
                Optional.empty(), Optional.empty(), application[0].systemBrowser);
            runtime.openLink(java.net.URI.create("https://example.com/declined"), PluginRuntime.Placement.SPLIT_RIGHT,
                Optional.of("t"), Optional.empty(), application[0].systemBrowser);
            runtime.openLink(java.net.URI.create("https://example.com/long"), PluginRuntime.Placement.SPLIT_RIGHT,
                Optional.of("t".repeat(257)), Optional.empty(), application[0].systemBrowser);
        });
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/placed")))
            .as("the long target never reached the browser")
            .containsExactly("https://example.com/r|SPLIT_RIGHT|t", "https://example.com/d|SPLIT_DOWN|-", "https://example.com/declined|SPLIT_RIGHT|t");
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened")))
            .as("as clicks; a window that is not open is no window")
            .containsExactly("https://example.com/r|CLICK|-", "https://example.com/d|CLICK|-", "https://example.com/declined|CLICK|-");
        assertThat(system).containsExactly("https://example.com/declined", "https://example.com/long");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * Browser plan 3's final review, I2: {@code composeContent} wires each window's terminal link opener to
     * {@code openLink} with that window, so a clicked web link reaches the provided browser as a click from it.
     */
    @Test void aWindowsTerminalLinkOpenerSendsClicksToTheBrowserWithThatWindow() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        var system = new CopyOnWriteArrayList<String>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        dev.jasper.app.workspace.JasperWindow[] first = new dev.jasper.app.workspace.JasperWindow[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
            application[0].startPlugins(null, dev, false, dirs);
            // A stand-in frame opens a window headless, as pluginsStartBeforeWindowsAndStopBeforeTermination does.
            application[0].frames = window -> new dev.jasper.app.workspace.WindowShell(() -> { }, () -> { }, () -> { }, () -> { },
                () -> null, component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            first[0] = application[0].openFirstWindow(List.of(), List.of()).orElseThrow();
        });
        edt(() -> first[0].content().onOpenLink.accept(clicked("https://example.com/e")));
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened")))
            .containsExactly("https://example.com/e|CLICK|" + first[0].id());
        assertThat(system).as("the browser took it").isEmpty();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * Browser plan 3b: a window's terminal link opener sends a Cmd-click (Ctrl-click elsewhere) to the provided browser in
     * a tab, and a split click in a split of its pane, to the right of a pane wider than it is tall and below any other,
     * each with its OSC 8 target and that window; a link that is no web address goes to the system browser whatever the
     * gesture.
     */
    @Test void aTerminalClickReachesTheBrowserAsATabOrASplitByItsPanesShape() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        var system = new CopyOnWriteArrayList<String>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        dev.jasper.app.workspace.JasperWindow[] first = new dev.jasper.app.workspace.JasperWindow[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
            application[0].startPlugins(null, dev, false, dirs);
            // A stand-in frame opens a window headless, as pluginsStartBeforeWindowsAndStopBeforeTermination does.
            application[0].frames = window -> new dev.jasper.app.workspace.WindowShell(() -> { }, () -> { }, () -> { }, () -> { },
                () -> null, component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            first[0] = application[0].openFirstWindow(List.of(), List.of()).orElseThrow();
        });
        edt(() -> {
            var opener = first[0].content().onOpenLink;
            opener.accept(new LinkClick(new TerminalLink("https://example.com/open", TerminalLink.Gesture.OPEN, Optional.of("docs")), true));
            opener.accept(new LinkClick(new TerminalLink("https://example.com/wide", TerminalLink.Gesture.SPLIT, Optional.empty()), true));
            opener.accept(new LinkClick(new TerminalLink("https://example.com/tall", TerminalLink.Gesture.SPLIT, Optional.of("t")), false));
            opener.accept(new LinkClick(new TerminalLink("mailto:someone@example.com", TerminalLink.Gesture.SPLIT, Optional.empty()), true));
            opener.accept(new LinkClick(new TerminalLink("ftp://files.example.com/x", TerminalLink.Gesture.SPLIT, Optional.of("t")), false));
            opener.accept(new LinkClick(new TerminalLink("http:/notes", TerminalLink.Gesture.SPLIT, Optional.empty()), true));
        });
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/placed"))).containsExactly(
            "https://example.com/open|TAB|docs", "https://example.com/wide|SPLIT_RIGHT|-", "https://example.com/tall|SPLIT_DOWN|t");
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened"))).containsExactly(
            "https://example.com/open|CLICK|" + first[0].id(), "https://example.com/wide|CLICK|" + first[0].id(),
            "https://example.com/tall|CLICK|" + first[0].id());
        assertThat(system).as("mailto, ftp and an http link with no authority")
            .containsExactly("mailto:someone@example.com", "ftp://files.example.com/x", "http:/notes");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * One plugin that provides both roles. Its browser appends {@code <address>|<origin>|<placement>|<window id or ->} to
     * {@code data/browsed} and declines a file named {@code declined.html}; its editor appends {@code <file name>|<line or ->}
     * to {@code data/edited}.
     */
    private static final String BROWSER_AND_EDITOR = """
        package fix.both;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.platform().browser(link -> {
                    append(context, "browsed", link.address() + "|" + link.origin() + "|" + link.placement() + "|"
                        + link.window().map(owner -> ((dev.jasper.sdk.terminal.WindowHandle) owner).id().toString()).orElse("-"));
                    return !link.address().getPath().endsWith("/declined.html");
                });
                context.platform().editor((file, line, window) -> {
                    append(context, "edited", file.getFileName() + "|" + (line.isPresent() ? String.valueOf(line.getAsInt()) : "-"));
                    return true;
                });
            }
            private static void append(PluginContext context, String name, String line) {
                try { Files.writeString(context.dataDirectory().resolve(name), line + "\\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
        """;

    /** Starts an application whose one plugin is {@link #BROWSER_AND_EDITOR}; no window opens. */
    private JasperApplication startedWithBrowserAndEditor(AppDirs dirs, CountDownLatch terminated) throws Exception {
        Path dev = home.resolve("both-plugin");
        PluginJars.build(dev, "both.jar", PluginJars.descriptor("dev.example.both", "1.0.0", "fix.both.Main")
            + "capabilities = [\"browser.provide\", \"editor.provide\"]\n", Map.of("fix.both.Main", BROWSER_AND_EDITOR), List.of());
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, dev, false, dirs);
        });
        return application[0];
    }

    /**
     * Browser plan 3b, Task 7: a file clicked in a terminal that a browser can display (any case of the extension) goes to
     * the provided browser first, as a click in a tab on its {@code file:} link, and the editor is not asked; a file no
     * browser displays goes to the editor with its line, and a folder or a missing file named like a web page opens nothing.
     */
    @Test void aClickedLocalWebFileIsOfferedToTheBrowserFirst() throws Exception {
        Path page = java.nio.file.Files.writeString(home.resolve("index.HTML"), "<p>hi</p>\n");
        Path notes = java.nio.file.Files.writeString(home.resolve("notes.txt"), "notes\n");
        java.nio.file.Files.createDirectory(home.resolve("site.html"));
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var terminated = new CountDownLatch(1);
        JasperApplication application = startedWithBrowserAndEditor(dirs, terminated);
        Path browsed = dirs.plugins().resolve("dev.example.both/data/browsed");
        Path edited = dirs.plugins().resolve("dev.example.both/data/edited");
        java.util.UUID window = java.util.UUID.randomUUID();
        // Every open runs on a thread of its own, so each sub-case waits for the threads it started before it looks.
        var opens = new CopyOnWriteArrayList<Thread>();
        application.fileOpener = task -> opens.add(Thread.ofVirtual().name("jasper-open-file").start(task));

        edt(() -> application.openClickedFile(page, 7, window));
        joinAll(opens);
        assertThat(awaitLines(browsed, 1)).as("a click in a tab; a window that is not open is no window")
            .containsExactly(page.toUri() + "|CLICK|TAB|-");
        assertThat(edited).as("the browser took it, so the editor was not asked").doesNotExist();

        // The editor is asked after the browser by the same thread, so its line shows what each click reached.
        edt(() -> application.openClickedFile(notes, 3, window));
        joinAll(opens);
        assertThat(awaitLines(edited, 1)).as("the editor heard only the file no browser displays, at its line")
            .containsExactly("notes.txt|3");

        edt(() -> {
            application.openClickedFile(home.resolve("site.html"), 0, window);
            application.openClickedFile(home.resolve("gone.html"), 0, window);
            application.openClickedFile(notes, 0, window);
        });
        joinAll(opens);
        assertThat(java.nio.file.Files.readAllLines(edited)).as("neither a folder nor a missing file opened")
            .containsExactlyInAnyOrder("notes.txt|3", "notes.txt|-");
        assertThat(java.nio.file.Files.readAllLines(browsed)).as("nor was the browser asked about them").hasSize(1);
        edt(application::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application);
    }

    /** Browser plan 3b, Task 7: a web file the browser declines opens in the Default editor at its line, as every file does. */
    @Test void aDeclinedLocalWebFileOpensInTheDefaultEditor() throws Exception {
        Path declined = java.nio.file.Files.writeString(home.resolve("declined.html"), "<p>no</p>\n");
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var terminated = new CountDownLatch(1);
        JasperApplication application = startedWithBrowserAndEditor(dirs, terminated);
        Path browsed = dirs.plugins().resolve("dev.example.both/data/browsed");
        Path edited = dirs.plugins().resolve("dev.example.both/data/edited");

        edt(() -> application.openClickedFile(declined, 9, java.util.UUID.randomUUID()));

        assertThat(awaitLines(edited, 1)).containsExactly("declined.html|9");
        assertThat(java.nio.file.Files.readAllLines(browsed)).as("the browser was asked first").containsExactly(declined.toUri() + "|CLICK|TAB|-");
        edt(application::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application);
    }

    /**
     * Browser plan 3b, Task 7 and spec 9: only a terminal click asks the browser. {@code jasper index.html}, the open-file
     * event and a launch's files ({@code openRequestedFile}) and File › Open File… ({@code openFileAt}) open the file in the
     * editor although a browser that would take it is provided.
     */
    @Test void jasperFileHtmlStillOpensInTheEditor() throws Exception {
        Path page = java.nio.file.Files.writeString(home.resolve("index.html"), "<p>hi</p>\n");
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var terminated = new CountDownLatch(1);
        JasperApplication application = startedWithBrowserAndEditor(dirs, terminated);
        Path browsed = dirs.plugins().resolve("dev.example.both/data/browsed");
        Path edited = dirs.plugins().resolve("dev.example.both/data/edited");
        java.util.UUID window = java.util.UUID.randomUUID();

        edt(() -> {
            application.openRequestedFile(page, window);
            application.openFileAt(page, 5, window);
        });

        assertThat(awaitLines(edited, 2)).containsExactlyInAnyOrder("index.html|-", "index.html|5");
        assertThat(browsed).as("the editor is asked in place of a browser, not after one").doesNotExist();
        edt(application::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application);
    }

    /**
     * Browser plan 3b, Task 7 (R8): {@code composeContent} wires a window's {@code onOpenClickedFile}, which a terminal click
     * uses, to the browser first with that window, and its {@code onOpenFile}, which File › Open File… uses, to the editor.
     */
    @Test void aWindowsTerminalFileClickOffersAWebFileToTheBrowserAndOpenFileDoesNot() throws Exception {
        Path page = java.nio.file.Files.writeString(home.resolve("index.html"), "<p>hi</p>\n");
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var terminated = new CountDownLatch(1);
        JasperApplication application = startedWithBrowserAndEditor(dirs, terminated);
        Path browsed = dirs.plugins().resolve("dev.example.both/data/browsed");
        Path edited = dirs.plugins().resolve("dev.example.both/data/edited");
        dev.jasper.app.workspace.JasperWindow[] first = new dev.jasper.app.workspace.JasperWindow[1];
        edt(() -> {
            // A stand-in frame opens a window headless, as pluginsStartBeforeWindowsAndStopBeforeTermination does.
            application.frames = window -> new dev.jasper.app.workspace.WindowShell(() -> { }, () -> { }, () -> { }, () -> { },
                () -> null, component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            first[0] = application.openFirstWindow(List.of(), List.of()).orElseThrow();
        });

        edt(() -> first[0].content().onOpenClickedFile.accept(page, 0));
        assertThat(awaitLines(browsed, 1)).as("a terminal click, from that window").containsExactly(page.toUri() + "|CLICK|TAB|" + first[0].id());
        edt(() -> first[0].content().onOpenFile.accept(page, 0));
        assertThat(awaitLines(edited, 1)).as("File › Open File…").containsExactly("index.html|-");
        assertThat(java.nio.file.Files.readAllLines(browsed)).as("the browser was not asked again").hasSize(1);
        edt(application::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application);
    }

    /** Browser plan 3b, Task 7: before the plugins start no browser is asked, and a web file opens as every clicked file does. */
    @Test void aClickedLocalWebFileBeforeThePluginsStartOpensWithTheDesktop() throws Exception {
        Path page = java.nio.file.Files.writeString(home.resolve("index.html"), "<p>hi</p>\n");
        var calls = new CopyOnWriteArrayList<String>();
        var desktop = new dev.jasper.app.platform.ConfigEditor(path -> calls.add("edit:" + path.getFileName()),
            path -> calls.add("open:" + path.getFileName()), path -> calls.add("reveal:" + path.getFileName()));
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].openClickedFile(page, 0, java.util.UUID.randomUUID(), desktop);
        });
        awaitCalls(calls, 1);
        assertThat(calls).containsExactly("edit:index.html");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    /** Waits for every thread in {@code threads}, which an open's executor collected, to finish, and forgets them. */
    private static void joinAll(List<Thread> threads) throws InterruptedException {
        for (Thread thread : List.copyOf(threads)) {
            assertThat(thread.join(java.time.Duration.ofSeconds(10))).as("%s finished", thread.getName()).isTrue();
            threads.remove(thread);
        }
    }

    /** The lines of {@code file} once it has {@code count}, or the lines it has when ten seconds have passed. */
    private static List<String> awaitLines(Path file, int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        List<String> lines = List.of();
        while (System.nanoTime() < deadline && lines.size() < count) {
            if (java.nio.file.Files.exists(file)) lines = java.nio.file.Files.readAllLines(file);
            if (lines.size() < count) Thread.sleep(10);
        }
        return lines;
    }

    /** A Cmd-click (Ctrl-click elsewhere) on {@code url} in a pane, as a window's {@code onOpenLink} hears it. */
    private static LinkClick clicked(String url) {
        return new LinkClick(new TerminalLink(url, TerminalLink.Gesture.OPEN, Optional.empty()), false);
    }

    /**
     * Browser plan 3's final review, I1: a Jasper that the {@code jasper} command started without a window, and that is
     * not resident, quits once the system browser has taken the command's address, not while it is still opening.
     */
    @Test void aSystemBrowserOpenStartedByACommandFinishesBeforeShutdownCompletes() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var desktop = new CopyOnWriteArrayList<String>();
        var release = new CountDownLatch(1);
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                application[0].desktop = address -> {
                    desktop.add(address.toString());
                    try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                };
                application[0].startPlugins(null, null, false, dirs);
            });
            // No plugin provides a browser and Jasper is not the default, so the command's address goes to the default systemBrowser.
            edt(() -> { });
            var opened = application[0].commands().dispatch(new dev.jasper.app.commandline.Invocation(List.of("https://example.com/s"),
                home, Optional.empty(), text -> { }, text -> { }));
            assertThat(opened.toCompletableFuture().get(5, TimeUnit.SECONDS)).isZero();
            edt(application[0]::quitNow);
            assertThat(terminated.await(500, TimeUnit.MILLISECONDS)).as("still opening, within the shutdown's grace").isFalse();
            release.countDown();
            assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(desktop).as("the address reached the desktop's browser").containsExactly("https://example.com/s");
        } finally { release.countDown(); }
        ApplicationPluginCleanup.await(application[0]);
    }

    private static void awaitCalls(List<String> calls, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && calls.size() < count) Thread.sleep(10);
    }
}
