package dev.jasper.app.application;

import dev.jasper.app.bootstrap.BootstrapTestSupport;
import dev.jasper.app.bootstrap.ApplicationBootstrap;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.residency.LaunchRequest;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.workspace.DesktopTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

/** Residency changes exactly one thing: the last window closing no longer ends the process. */
@DisabledOnOs(OS.WINDOWS)
class JasperApplicationResidencyTest {

    private JasperApplication application(Runnable terminate) throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()),
            new CommandHistory(), null, terminate));
        return held[0];
    }

    @Test void closingTheLastWindowOfAResidentApplicationDoesNotTerminate() throws Exception {
        AtomicInteger terminations = new AtomicInteger();
        JasperApplication application = application(terminations::incrementAndGet);
        edt(() -> application.residency(true));
        assertThat(application.resident()).isTrue();
        // windowClosed(null) is how this suite says "the last window just closed" without
        // constructing a native window; the branch under test runs when the window set empties.
        edt(() -> application.windowClosed(null));
        edt(() -> { });
        assertThat(terminations.get()).as("still resident").isZero();
        // And it is still usable: a second close is not a shutdown either.
        edt(() -> application.windowClosed(null));
        edt(() -> { });
        assertThat(terminations.get()).isZero();
    }

    @Test void quitTerminatesAResidentApplicationJustTheSame() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        edt(() -> application.residency(true));
        edt(application::quit);
        assertThat(terminated.await(2, TimeUnit.SECONDS)).as("Quit always exits").isTrue();
    }

    @Test void withoutResidencyTheLastWindowStillEndsTheProcess() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        assertThat(application.resident()).isFalse();
        edt(() -> application.windowClosed(null));
        assertThat(terminated.await(2, TimeUnit.SECONDS)).as("unchanged behaviour").isTrue();
    }

    @Test void residencyCanBeTurnedOffAgainBeforeTheLastWindowCloses() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        edt(() -> application.residency(true));
        edt(() -> application.residency(false));
        edt(() -> application.windowClosed(null));
        assertThat(terminated.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test void warmingUpFinishesWithoutAWindow() throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        var finished = new AtomicInteger();
        var trace = StartupTrace.detached();
        try {
            edt(() -> {
                held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { });
                held[0].trace(trace);
            });
            // Headless there is no window to rehearse with: the font set is built and the warm-up reports it finished.
            edt(() -> held[0].warmUp(finished::incrementAndGet));
            edt(() -> held[0].warmUp(finished::incrementAndGet));
            assertThat(finished).hasValue(2);
            // The warm-up marks nothing itself (its caller marks WARMED_UP when it finishes), and it opens no window.
            assertThat(trace.windows()).isEmpty();
            assertThat(trace.millis(StartupTrace.Milestone.FIRST_PAINT)).isEmpty();
            assertThat(trace.millis(StartupTrace.Milestone.OUTPUT_PAINTED)).isEmpty();
            assertThat(trace.millis(StartupTrace.Milestone.WARMED_UP)).isEmpty();
        } finally {
            edt(() -> { if (held[0] != null) held[0].quit(); });
        }
    }

    @Test void warmingUpStartsOneCollectionAfterDoneOffTheEdt() throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        var events = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var collected = new CountDownLatch(1);
        var onEdt = new java.util.concurrent.atomic.AtomicBoolean(true);
        try {
            edt(() -> {
                held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { });
                held[0].trace(StartupTrace.detached());
                held[0].collector = () -> {
                    onEdt.set(javax.swing.SwingUtilities.isEventDispatchThread());
                    events.add("collect");
                    collected.countDown();
                };
            });
            edt(() -> held[0].warmUp(() -> events.add("done")));
            assertThat(collected.await(5, TimeUnit.SECONDS)).as("the collection follows the warm-up").isTrue();
            edt(() -> { });
            assertThat(events).as("after done, once").containsExactly("done", "collect");
            assertThat(onEdt).as("never on the EDT").isFalse();
        } finally {
            edt(() -> { if (held[0] != null) held[0].quit(); });
        }
    }

    @Test void theLoginItemFollowsTheSettingWhileResidencyDoesNot(@org.junit.jupiter.api.io.TempDir
                                                                 java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = dir.resolve("config.toml");
        java.nio.file.Files.writeString(file, "[system]\nrun_in_background = true\n");
        ConfigService service = new ConfigService(file, true);
        java.util.List<Boolean> reconciled = new java.util.concurrent.CopyOnWriteArrayList<>();
        JasperApplication[] held = new JasperApplication[1];
        try {
            edt(() -> held[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(),
                null, () -> { }));
            // Constructing the application registers its own configuration listener, which schedules
            // ConfigService's first, same-value publish onto the EDT queue behind this turn. Draining it
            // now, before loginItems is installed, lets that harmless replay land on the still-default
            // no-op consumer instead of reconciled -- otherwise it would double-count below.
            edt(() -> { });
            edt(() -> held[0].loginItems(reconciled::add));
            // Registering a listener replays the current snapshot, so the first value arrives at once.
            edt(() -> { });
            assertThat(reconciled).as("the setting as loaded").containsExactly(true);
            java.nio.file.Files.writeString(file, "[system]\nrun_in_background = false\n");
            edt(() -> service.reload());
            DesktopTestSupport.until(() -> reconciled.size() == 2);
            assertThat(reconciled).last().as("a saved change applies without a restart").isEqualTo(false);
            // Residency is startup-scoped and must not have moved with it.
            assertThat(held[0].resident()).isFalse();
        } finally {
            edt(() -> { if (held[0] != null) held[0].quit(); });
            service.close();
        }
    }

    @Test void openOrRaiseDoesNotThrowOnceTheApplicationHasQuit() throws Exception {
        JasperApplication application = application(() -> { });
        edt(() -> application.residency(true));
        edt(application::quit);
        // Only that a late request is harmless: with no windows open this reaches openWindow, which
        // has its own quitting guard. The branch that openOrRaise's own guard protects -- raising an
        // existing window during shutdown -- needs a live window and cannot be covered headlessly.
        // It is on the manual checklist instead.
        edt(() -> application.openOrRaise(DesktopTestSupport.HOME, "handoff"));
        edt(() -> { });
    }

    @Test void aStaleRequestEndsResidencyAndShutsDownWhenNoWindowsRemain() throws Exception {
        java.util.concurrent.atomic.AtomicInteger terminations = new java.util.concurrent.atomic.AtomicInteger();
        JasperApplication application = application(terminations::incrementAndGet);
        edt(() -> application.residency(true));
        var handler = BootstrapTestSupport.handoffHandler(application, Path.of("/old.jar"), 1L);
        assertThat(handler.apply(new LaunchRequest("t", Path.of("/new.jar"), 2L)))
            .isEqualTo(LaunchRequest.Response.STALE);
        edt(() -> { });
        assertThat(application.resident()).isFalse();
        // With no windows left there is nothing to wait for. Previously this process exited only
        // when the JVM starved after the accept thread ended, skipping every close.
        DesktopTestSupport.until(() -> terminations.get() == 1);
    }

    @Test void aMatchingRequestIsAcceptedAndLeavesResidencyAlone() throws Exception {
        JasperApplication application = application(() -> { });
        edt(() -> application.residency(true));
        edt(application::quit);   // So the queued openOrRaise is inert and no window is created.
        var handler = BootstrapTestSupport.handoffHandler(application, Path.of("/same.jar"), 7L);
        assertThat(handler.apply(new LaunchRequest("t", Path.of("/same.jar"), 7L)))
            .isEqualTo(LaunchRequest.Response.OK);
        edt(() -> { });
        // Unlike a stale request, a matching one never touches residency -- this is the assertion
        // the test's name actually promises.
        assertThat(application.resident()).as("a matching request does not touch residency").isTrue();
    }
}
