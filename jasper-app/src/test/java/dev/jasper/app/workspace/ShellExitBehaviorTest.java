package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigLoader;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.application.ConfigurationTestSupport;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.workspace.TerminalPane;
import dev.jasper.app.workspace.TerminalContent;
import dev.jasper.app.workspace.WindowContent;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.session.SessionLaunchOptions;

import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.view.TerminalView;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

/** Real config-to-owner lifecycle with controlled, non-login child processes. */
@DisabledOnOs(OS.WINDOWS)
class ShellExitBehaviorTest {
    @TempDir Path directory;
    final Queue<Runnable> pending = new ArrayDeque<>();
    final AtomicInteger empty = new AtomicInteger();
    final AtomicInteger launches = new AtomicInteger();
    /** How many launches succeed before a failing launcher starts failing. */
    int launchesBeforeFailure;
    final List<String> failures = new ArrayList<>();
    final List<TerminalSession> sessions = new ArrayList<>();
    ConfigService service;
    ConfigurationTestSupport controller;
    WindowContent owner;

    @AfterEach void close() throws Exception {
        edt(() -> {
            if (owner != null) owner.close();
            if (controller != null) controller.close();
        });
        sessions.forEach(TerminalSession::close);
        if (service != null) service.close();
    }

    @ParameterizedTest @CsvSource({
        "keep_open,0,false", "keep_open,7,false", "close_on_success,0,true",
        "close_on_success,7,false", "close,0,true", "close,7,true"
    })
    void configuredPolicyClosesOnlyEligibleExitsAndOtherwiseRetainsOutput(String policy, int code, boolean closes)
            throws Exception {
        start(policy, false, false);
        deliver();
        TerminalPane pane = owner.currentPane();
        TerminalContent tab = owner.currentTerminal();
        TerminalView view = pane.view();
        edt(() -> exit(pane, code));
        if (closes) until(() -> empty.get() == 1);
        edt(() -> {
            assertThat(owner.tabCount()).isEqualTo(closes ? 0 : 1);
            assertThat(tab.closed()).isEqualTo(closes);
            assertThat(empty).hasValue(closes ? 1 : 0);
            if (!closes) {
                assertThat(pane.running()).isFalse();
                assertThat(view.find(new SearchQuery("READY", false, true)).count()).isEqualTo(1);
                assertThat(view.find(new SearchQuery("[process exited with code " + code + "]", false, true)).count()).isEqualTo(1);
            }
            pane.onClose.run(); // A repeated close request cannot empty the owner twice.
        });
        edt(() -> assertThat(empty).hasValue(1));
    }

    @Test void hiddenMaximizedAndInactiveExitsPreserveOtherSessionsAndSelection() throws Exception {
        start("keep_open", false, false); deliver();
        TerminalPane hidden = owner.currentPane();
        edt(() -> owner.invoke(dev.jasper.app.commands.ActionId.SPLIT_RIGHT)); deliver();
        TerminalPane sibling = owner.currentPane();
        edt(() -> { owner.invoke(dev.jasper.app.commands.ActionId.ZOOM_PANE); owner.newTab(directory); }); deliver();
        TerminalContent selected = owner.currentTerminal(); TerminalPane selectedPane = owner.currentPane();
        reload("close");
        edt(() -> exit(hidden, 0));
        until(() -> owner.tabCount() == 2);
        edt(() -> {
            assertThat(owner.terminalOf(hidden)).as("the hidden group's tab closed").isNull();
            assertThat(sibling.running()).isTrue();
            assertThat(selectedPane.running()).isTrue();
            assertThat(owner.currentTerminal()).isSameAs(selected);
            assertThat(empty).hasValue(0);
            exit(sibling, 7);
        });
        until(() -> owner.tabCount() == 1);
        edt(() -> {
            assertThat(owner.currentTerminal()).isSameAs(selected);
            assertThat(selectedPane.running()).isTrue();
            assertThat(empty).hasValue(0);
            exit(selectedPane, 0);
        });
        until(() -> empty.get() == 1);
    }

    @Test void reloadChangesFutureExitsWithoutRebuildingViewOrClosingRetainedOutput() throws Exception {
        start("keep_open", false, false); deliver();
        TerminalPane retained = owner.currentPane(); TerminalView retainedView = retained.view();
        edt(() -> { exit(retained, 0); owner.newTab(directory); }); deliver();
        TerminalPane running = owner.currentPane();
        edt(() -> { running.view().setFontSize(23f); running.applyTheme(ThemeTestSupport.controller(false).current()); });
        var options = running.view().options(); var session = running.session();
        reload("close_on_success");
        edt(() -> {
            assertThat(owner.tabCount()).isEqualTo(2);
            assertThat(retained.view()).isSameAs(retainedView);
            assertThat(running.view().options()).isSameAs(options);
            assertThat(running.session()).isSameAs(session);
            assertThat(running.view().fontSize()).isEqualTo(23f);
            assertThat(running.view().palette()).isEqualTo(ThemeTestSupport.terminal(false));
            exit(running, 0);
        });
        until(() -> owner.tabCount() == 1);
        edt(() -> {
            assertThat(owner.currentPane()).isSameAs(retained);
            assertThat(retainedView.find(new SearchQuery("[process exited with code 0]", false, true)).count()).isEqualTo(1);
            assertThat(empty).hasValue(0);
        });
    }

    @ParameterizedTest @CsvSource({"keep_open,close,0", "close,keep_open,1"})
    void alreadyExitedPendingLaunchUsesLatestPolicyAfterReadySetup(String initial, String latest, int tabs)
            throws Exception {
        start(initial, true, false);
        reload(latest);
        AtomicInteger ready = new AtomicInteger();
        edt(() -> {
            TerminalPane pane = owner.currentPane();
            var configure = pane.onReady;
            pane.onReady = view -> {
                assertThat(owner.tabCount()).isEqualTo(1);
                assertThat(pane.view()).isSameAs(view);
                assertThat(pane.findBar()).isNotNull();
                configure.accept(view); ready.incrementAndGet();
            };
        });
        deliver();
        edt(() -> {}); // The already-completed exit is queued after launch delivery.
        edt(() -> {
            assertThat(ready).hasValue(1);
            assertThat(owner.tabCount()).isEqualTo(tabs);
            assertThat(empty).hasValue(tabs == 0 ? 1 : 0);
        });
    }

    @Test void newlyRequestedPaneReceivesExistingPolicyWhenItsEarlyExitIsDelivered() throws Exception {
        start("close", true, false);
        edt(() -> owner.newTab(directory));
        deliver(); deliver();
        until(() -> empty.get() == 1);
        edt(() -> assertThat(owner.tabCount()).isZero());
    }

    @Test void exitDeliveryUsesPolicyReloadedWhileEdtWasHandlingExit() throws Exception {
        start("keep_open", false, false); deliver();
        edt(() -> {
            exit(owner.currentPane(), 0);
            owner.applyConfiguration(ConfigLoader.parse(directory.resolve("config.toml"),
                "terminal.on_exit='close'", false).snapshot(), false);
        });
        until(() -> empty.get() == 1);
    }

    @ParameterizedTest @CsvSource({"true", "false"})
    void ownerOrPaneClosedBeforeQueuedExitIgnoresLateCallback(boolean closeOwner) throws Exception {
        start("close", false, false); deliver();
        TerminalPane pane = owner.currentPane(); AtomicInteger lateClose = new AtomicInteger();
        edt(() -> {
            pane.onClose = lateClose::incrementAndGet;
            exit(pane, 0);
            if (closeOwner) owner.close(); else pane.close();
        });
        edt(() -> {});
        edt(() -> { assertThat(lateClose).hasValue(0); assertThat(empty).hasValue(0); });
    }

    @ParameterizedTest @CsvSource({"keep_open,false", "close_on_success,false", "close,true"})
    void exceptionalExitCompletionIsNotSuccessful(String policy, boolean closes) throws Exception {
        start(policy, false, false); deliver();
        // Real reader completion is numeric and exitFuture returns a defensive copy. Inject only the
        // otherwise-unreachable exceptional result, keeping the real pane subscription and owner wiring.
        var accessField = TerminalSession.class.getDeclaredField("access");
        accessField.setAccessible(true);
        var access = accessField.get(owner.currentPane().session());
        var engineField = access.getClass().getDeclaredField("engine");
        engineField.setAccessible(true);
        var engine = engineField.get(access);
        var exitField = engine.getClass().getDeclaredField("exit");
        exitField.setAccessible(true);
        var exit = (CompletableFuture<?>) exitField.get(engine);
        edt(() -> exit.completeExceptionally(new IllegalStateException("exit failed")));
        edt(() -> assertThat(empty).hasValue(closes ? 1 : 0));
    }

    /** Closing the window's only pane would close the window and, by default, quit before anyone read the error. */
    @Test void launchFailureInTheWindowsOnlyPaneReportsErrorAndKeepsItOpen() throws Exception {
        start("keep_open", false, true); deliver();
        edt(() -> {
            assertThat(failures).singleElement().asString().contains("controlled launch failure");
            assertThat(empty).hasValue(0);
            assertThat(owner.tabCount()).isEqualTo(1);
            assertThat(owner.currentTerminal().closed()).isFalse();
            var label = (JLabel) owner.currentPane().getComponent(0);
            assertThat(label.getText()).startsWith("Could not start terminal: ").contains("controlled launch failure");
        });
    }

    @Test void launchFailureInASecondTabReportsErrorAndClosesFailedPaneKeepingTheWindow() throws Exception {
        launchesBeforeFailure = 1;
        start("keep_open", false, true); deliver();
        TerminalContent first = owner.currentTerminal();
        edt(() -> owner.newTab(directory));
        deliver();
        edt(() -> {
            assertThat(failures).singleElement().asString().contains("controlled launch failure");
            assertThat(empty).hasValue(0);
            assertThat(owner.tabCount()).isEqualTo(1);
            assertThat(owner.currentTerminal()).isSameAs(first);
            assertThat(first.closed()).isFalse();
            assertThat(first.pane().running()).isTrue();
        });
    }

    @Test void wrongKnownTypeKeepsLastGoodPolicy() throws Exception {
        start("close", false, false); deliver();
        Files.writeString(directory.resolve("config.toml"), "terminal.on_exit=7");
        service.reload().get(5, TimeUnit.SECONDS); edt(() -> {});
        edt(() -> exit(owner.currentPane(), 0));
        until(() -> empty.get() == 1);
    }

    private void start(String policy, boolean earlyExit, boolean launchFailure) throws Exception {
        Files.writeString(directory.resolve("config.toml"), "terminal.on_exit='" + policy + "'");
        service = new ConfigService(directory.resolve("config.toml"), false);
        edt(() -> {
            ThemeController themes = ThemeTestSupport.controller(true);
            controller = new ConfigurationTestSupport(themes, service);
            ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
                if (launchFailure && launches.incrementAndGet() > launchesBeforeFailure) throw new IllegalStateException("controlled launch failure");
                try {
                    var session = TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/sh", "-c", earlyExit
                        ? "printf 'READY\\n'; exit 0" : "stty -echo; printf 'READY\\n'; read code; exit \"$code\"")).environment(System.getenv()).workingDirectory(path).grid(new GridSize(80, 24)).scrollback(100).build());
                    sessions.add(session);
                    if (earlyExit) assertThat(session.exitFuture().get(5, TimeUnit.SECONDS)).isZero();
                    return session;
                } catch (Exception failure) { throw new CompletionException(failure); }
            }, "controlled-sh");
            owner = new WindowContent(launcher, directory, path -> {}, () -> {}, () -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); empty.incrementAndGet();
            }, themes);
            owner.onError = (title, message) -> failures.add(message);
            controller.register(owner);
        });
    }

    private void deliver() throws Exception { pending.remove().run(); edt(() -> {}); }

    private void reload(String policy) throws Exception {
        Files.writeString(directory.resolve("config.toml"), "terminal.on_exit='" + policy + "'");
        service.reload().get(5, TimeUnit.SECONDS); edt(() -> {});
    }

    /** Hold the EDT through process completion so policy delivery cannot happen reentrantly. */
    private static void exit(TerminalPane pane, int code) {
        try {
            pane.session().write(code + "\n");
            assertThat(pane.session().exitFuture().get(5, TimeUnit.SECONDS)).isEqualTo(code);
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static final String PROGRAM_SCRIPT = "stty -echo; printf 'READY\\n'; read code; exit \"$code\"";
    /** A program tab's command; the trailing word is only the script's $0, which tells the launches apart. */
    private static final List<String> PROGRAM = List.of("/bin/sh", "-c", PROGRAM_SCRIPT, "program");
    final List<dev.jasper.app.launch.LaunchSettings> started = new CopyOnWriteArrayList<>();

    /** As {@link #start}, with a launcher that resolves settings, so a tab can run a program; the shell runs the same script. */
    private void startWithSettings(String policy) throws Exception {
        Files.writeString(directory.resolve("config.toml"), "terminal.on_exit='" + policy + "'");
        service = new ConfigService(directory.resolve("config.toml"), false);
        var shell = dev.jasper.app.testsupport.ConfigTestSupport.snapshot("/bin/sh", List.of("-c", PROGRAM_SCRIPT, "shell"), Map.of(), 100, 80, 24);
        edt(() -> {
            ThemeController themes = ThemeTestSupport.controller(true);
            controller = new ConfigurationTestSupport(themes, service);
            ShellLauncher launcher = new ShellLauncher(pending::add,
                requested -> dev.jasper.app.launch.LaunchSettings.resolve(shell, "Linux", System.getenv(), 80, 24),
                (requested, captured) -> captured, null, (path, settings) -> {
                    started.add(settings);
                    try {
                        var session = TerminalSession.start(SessionLaunchOptions.builder().command(settings.command())
                            .environment(settings.environment()).workingDirectory(path).grid(new GridSize(80, 24)).scrollback(100).build());
                        sessions.add(session);
                        return session;
                    } catch (Exception failure) { throw new CompletionException(failure); }
                });
            owner = new WindowContent(launcher, directory, path -> {}, () -> {}, () -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); empty.incrementAndGet();
            }, themes);
            owner.onError = (title, message) -> failures.add(message);
            controller.register(owner);
        });
    }

    @Test void aProgramTabClosesWhenItsProgramSucceedsWhateverOnExitSays() throws Exception {
        startWithSettings("keep_open"); deliver();
        TerminalContent shellTab = owner.currentTerminal();
        edt(() -> owner.openTab(directory, null, Optional.empty(), Optional.of(PROGRAM))); deliver();
        TerminalPane program = owner.currentPane();
        assertThat(started.getLast().command()).isEqualTo(PROGRAM);
        edt(() -> exit(program, 0));
        until(() -> owner.tabCount() == 1);
        edt(() -> {
            assertThat(owner.currentTerminal()).isSameAs(shellTab);
            assertThat(empty).hasValue(0);
        });
    }

    @Test void aFailedProgramStaysOpenAndAnOnExitChangeDoesNotUnpinIt() throws Exception {
        startWithSettings("keep_open"); deliver();
        TerminalPane shell = owner.currentPane();
        edt(() -> owner.openTab(directory, null, Optional.empty(), Optional.of(PROGRAM))); deliver();
        TerminalPane program = owner.currentPane();
        reload("close");
        edt(() -> exit(program, 1));
        edt(() -> { });
        edt(() -> {
            assertThat(owner.tabCount()).as("terminal.on_exit = close does not reach a program tab").isEqualTo(2);
            assertThat(program.running()).isFalse();
            assertThat(program.view().find(new SearchQuery("[process exited with code 1]", false, true)).count()).isEqualTo(1);
        });
        edt(() -> exit(shell, 1));
        until(() -> owner.tabCount() == 1);
        edt(() -> assertThat(owner.currentPane()).as("the reload did reach the shell tab").isSameAs(program));
    }

    @Test void pluginRequestsRunProgramsInTabsAndSplitsThroughTheRegistry() throws Exception {
        startWithSettings("keep_open");
        var registry = new java.util.concurrent.atomic.AtomicReference<dev.jasper.app.terminals.TerminalRegistry>();
        edt(() -> { registry.set(new dev.jasper.app.terminals.TerminalRegistry()); owner.connectTerminals(registry.get(), () -> { }); });
        deliver();
        List<String> split = List.of("/bin/sh", "-c", PROGRAM_SCRIPT, "split");
        edt(() -> assertThat(registry.get().window(owner.id()).orElseThrow().openTab()
            .apply(new dev.jasper.app.terminals.OpenSpec.Local(Optional.of(directory), Optional.empty(), Optional.of(PROGRAM)))).isPresent());
        deliver();
        edt(() -> {
            var pane = registry.get().window(owner.id()).orElseThrow().selectedTab().get().orElseThrow().focusedPane().get().orElseThrow();
            assertThat(pane.split().apply(dev.jasper.app.layout.Side.RIGHT,
                new dev.jasper.app.terminals.OpenSpec.Local(Optional.empty(), Optional.empty(), Optional.of(split)))).isPresent();
        });
        deliver();
        assertThat(started).extracting(dev.jasper.app.launch.LaunchSettings::command)
            .containsExactly(List.of("/bin/sh", "-c", PROGRAM_SCRIPT, "shell"), PROGRAM, split);
    }
}
