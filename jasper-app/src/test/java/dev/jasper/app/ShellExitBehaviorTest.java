package dev.jasper.app;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.session.SessionLaunchOptions;

import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.view.TerminalView;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

/** Real config-to-owner lifecycle with controlled, non-login child processes. */
@DisabledOnOs(OS.WINDOWS)
class ShellExitBehaviorTest {
    @TempDir Path directory;
    final Queue<Runnable> pending = new ArrayDeque<>();
    final AtomicInteger empty = new AtomicInteger();
    final List<String> failures = new ArrayList<>();
    final List<TerminalSession> sessions = new ArrayList<>();
    ConfigService service;
    ConfigurationController controller;
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
        TerminalTab tab = owner.currentTab();
        TerminalView view = pane.view();
        edt(() -> exit(pane, code));
        if (closes) until(() -> empty.get() == 1);
        edt(() -> {
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(closes ? 0 : 1);
            assertThat(tab.panes()).hasSize(closes ? 0 : 1);
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

    @Test void hiddenZoomedAndInactiveExitsPreserveSiblingSessionsAndSelection() throws Exception {
        start("keep_open", false, false); deliver();
        TerminalTab first = owner.currentTab(); TerminalPane hidden = owner.currentPane();
        edt(() -> first.split(SplitTree.Axis.RIGHT)); deliver();
        TerminalPane sibling = first.focusedPane();
        edt(() -> { first.toggleZoom(); owner.newTab(directory); }); deliver();
        TerminalTab selected = owner.currentTab(); TerminalPane selectedPane = owner.currentPane();
        reload("close");
        edt(() -> exit(hidden, 0));
        until(() -> first.panes().size() == 1);
        edt(() -> {
            assertThat(first.panes()).containsExactly(sibling);
            assertThat(sibling.running()).isTrue();
            assertThat(selectedPane.running()).isTrue();
            assertThat(owner.currentTab()).isSameAs(selected);
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
            assertThat(empty).hasValue(0);
            exit(sibling, 7);
        });
        until(() -> owner.tabStrip().getTabCount() == 1);
        edt(() -> {
            assertThat(owner.currentTab()).isSameAs(selected);
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
        edt(() -> { running.view().setFontSize(23f); running.applyTheme(BuiltinTheme.LIGHT.palette()); });
        var options = running.view().options(); var session = running.session();
        reload("close_on_success");
        edt(() -> {
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
            assertThat(retained.view()).isSameAs(retainedView);
            assertThat(running.view().options()).isSameAs(options);
            assertThat(running.session()).isSameAs(session);
            assertThat(running.view().fontSize()).isEqualTo(23f);
            assertThat(running.view().palette()).isEqualTo(BuiltinTheme.LIGHT.palette());
            exit(running, 0);
        });
        until(() -> owner.tabStrip().getTabCount() == 1);
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
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
                assertThat(pane.view()).isSameAs(view);
                assertThat(pane.findBar()).isNotNull();
                configure.accept(view); ready.incrementAndGet();
            };
        });
        deliver();
        edt(() -> {}); // The already-completed exit is queued after launch delivery.
        edt(() -> {
            assertThat(ready).hasValue(1);
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(tabs);
            assertThat(empty).hasValue(tabs == 0 ? 1 : 0);
        });
    }

    @Test void newlyRequestedPaneReceivesExistingPolicyWhenItsEarlyExitIsDelivered() throws Exception {
        start("close", true, false);
        edt(() -> owner.newTab(directory));
        deliver(); deliver();
        until(() -> empty.get() == 1);
        edt(() -> assertThat(owner.tabStrip().getTabCount()).isZero());
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

    @Test void launchFailureStillReportsErrorAndClosesFailedPane() throws Exception {
        start("keep_open", false, true); deliver();
        edt(() -> {
            assertThat(failures).singleElement().asString().contains("controlled launch failure");
            assertThat(empty).hasValue(1);
            assertThat(owner.tabStrip().getTabCount()).isZero();
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
            ThemeController themes = new ThemeController();
            controller = new ConfigurationController(themes, service);
            ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
                if (launchFailure) throw new IllegalStateException("controlled launch failure");
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
            owner.onError = failures::add;
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
}
