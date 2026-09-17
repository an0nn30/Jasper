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
        JasperApplication application = application(() -> { });
        // The point of warm-up is that it pays the first window's costs and creates nothing:
        // it must be safe to call, repeatedly, with no configuration and no window.
        edt(application::warmUp);
        edt(application::warmUp);
        edt(() -> { });
        edt(application::quit);
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

    @Test void openOrRaiseIsInertOnceTheApplicationHasQuit() throws Exception {
        JasperApplication application = application(() -> { });
        edt(() -> application.residency(true));
        edt(application::quit);
        // A request arriving during shutdown must not resurrect a window.
        edt(() -> application.openOrRaise(DesktopTestSupport.HOME));
        edt(() -> { });
    }
}
