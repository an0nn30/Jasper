package dev.jasper.app;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.jasper.app.DesktopTestSupport.edt;
import static dev.jasper.app.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

/** Residency changes exactly one thing: the last window closing no longer ends the process. */
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

    @Test void warmingUpBuildsTheFontSetAndRefreshesHistoryWithoutAWindow() throws Exception {
        ShellHistoryIndex shellHistory = new ShellHistoryIndex(java.util.List.of());
        AtomicInteger refreshes = new AtomicInteger();
        JasperApplication[] held = new JasperApplication[1];
        try {
            edt(() -> {
                held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(),
                    null, () -> { }, shellHistory);
                shellHistory.onChanged(refreshes::incrementAndGet);
                // onChanged also starts the index's own once-a-second background poll (it exists so a
                // window need not be open for history to stay current). Stopping it here means the
                // only refresh this test can observe is the one warmUp() explicitly triggers below --
                // otherwise the poll alone would satisfy the wait even with warmUp() gutted.
                shellHistory.pollTimer().stop();
            });
            // The point of warm-up is that it pays the first window's costs and creates nothing. The
            // history half is verified for real here: refresh() hands off to a worker thread and
            // delivers back to the EDT, so completion is polled for, not assumed from the call
            // returning. Calling warmUp() twice also exercises refresh()'s own coalescing (a second
            // call while one is in flight queues rather than double-dispatching) without a window.
            // (The font-set half has no equivalent external signal without a production test seam,
            // which this round does not add; a thrown exception here would still fail the test, but
            // deleting the FontSet construction itself would not.)
            edt(held[0]::warmUp);
            edt(held[0]::warmUp);
            DesktopTestSupport.until(() -> refreshes.get() >= 1);
        } finally {
            edt(() -> { if (held[0] != null) held[0].quit(); });
        }
    }

    @Test void theLoginItemFollowsTheSettingWhileResidencyDoesNot(@org.junit.jupiter.api.io.TempDir
                                                                 java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = dir.resolve("config.toml");
        java.nio.file.Files.writeString(file, "[background]\nenabled = true\n");
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
            java.nio.file.Files.writeString(file, "[background]\nenabled = false\n");
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
        // Only that a late request is harmless: with no windows open this reaches newWindow, which
        // has its own quitting guard. The branch that openOrRaise's own guard protects -- raising an
        // existing window during shutdown -- needs a live window and cannot be covered headlessly.
        // It is on the manual checklist instead.
        edt(() -> application.openOrRaise(DesktopTestSupport.HOME));
        edt(() -> { });
    }
}
