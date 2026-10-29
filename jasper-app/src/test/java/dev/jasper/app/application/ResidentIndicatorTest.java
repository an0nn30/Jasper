package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.platform.ResidentTray;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.workspace.DesktopTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The resident process shows an indicator (the macOS menu bar item, the Windows/Linux tray icon) exactly while
 * it is resident, from the moment start-up has settled: the first output painted, a finished warm-up, or 2 s after
 * the first paint.
 */
class ResidentIndicatorTest {

    private record Indicator(AtomicInteger shown, AtomicInteger removed, AtomicReference<ResidentTray.Actions> actions) {
        Indicator() { this(new AtomicInteger(), new AtomicInteger(), new AtomicReference<>()); }

        Function<ResidentTray.Actions, Runnable> installer() {
            return given -> {
                shown.incrementAndGet();
                actions.set(given);
                return removed::incrementAndGet;
            };
        }
    }

    /** A trace whose first shell output has painted: start-up has settled. */
    private static StartupTrace settled() {
        StartupTrace trace = StartupTrace.detached();
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        return trace;
    }

    private JasperApplication application(Runnable terminate, Indicator indicator, StartupTrace trace) throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminate);
            held[0].trace(trace);
            held[0].residentIndicator(indicator.installer());
        });
        return held[0];
    }

    /** Residency, then one more EDT turn for the settled step it queues. */
    private static void resident(JasperApplication application) throws Exception {
        edt(() -> application.residency(true));
        edt(() -> { });
    }

    /** The fallback that settles a shell printing nothing; set before residency. */
    private static void fallback(JasperApplication application, Duration delay) throws Exception {
        edt(() -> application.settleFallback(delay));
    }

    @Test void aResidentProcessShowsTheIndicatorOnce() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator, settled());
        try {
            resident(application);
            resident(application);
            assertThat(indicator.shown()).hasValue(1);
            assertThat(indicator.removed()).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void theFirstPaintAloneShowsNothingAndTheFirstOutputShowsTheIndicator() throws Exception {
        var indicator = new Indicator();
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(() -> { }, indicator, trace);
        try {
            fallback(application, Duration.ofHours(1));
            resident(application);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            edt(() -> { });
            assertThat(indicator.shown()).as("never on the first window's critical path").hasValue(0);
            trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
            edt(() -> { });
            assertThat(indicator.shown()).hasValue(1);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aShellThatPrintsNothingStillGetsTheIndicatorAfterTheFallback() throws Exception {
        var indicator = new Indicator();
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(() -> { }, indicator, trace);
        try {
            fallback(application, Duration.ofMillis(50));
            resident(application);
            assertThat(indicator.shown()).as("the fallback starts at the first paint").hasValue(0);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            DesktopTestSupport.until(() -> indicator.shown().get() == 1);
            assertThat(indicator.removed()).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aBackgroundLaunchShowsTheIndicatorOnceWarmedUp() throws Exception {
        var indicator = new Indicator();
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(() -> { }, indicator, trace);
        try {
            fallback(application, Duration.ofHours(1));
            resident(application);
            assertThat(indicator.shown()).hasValue(0);
            trace.mark(StartupTrace.Milestone.WARMED_UP);
            edt(() -> { });
            assertThat(indicator.shown()).hasValue(1);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void noIconDiscoveryStartsOnlyAfterActualWarmUpAndQuitCleansOnce() throws Exception {
        var indicator = new Indicator();
        var terminated = new CountDownLatch(1);
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(terminated::countDown, indicator, trace);
        try {
            fallback(application, Duration.ofHours(1));
            resident(application);
            assertThat(indicator.shown()).hasValue(0);
            edt(() -> application.warmUp(() -> trace.mark(StartupTrace.Milestone.WARMED_UP)));
            edt(() -> { });
            assertThat(trace.millis(StartupTrace.Milestone.WARMED_UP)).isPresent();
            assertThat(indicator.shown()).as("discovery begins after warm-up even when it creates no icon").hasValue(1);
            assertThat(application.resident()).isTrue();
            assertThat(application.acceptsRequests()).isTrue();
            assertThat(trace.windows()).isEmpty();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> edt(application::reopened))
                .as("a resident without an icon still accepts reopen; only composing a real window is unavailable headlessly")
                .hasRootCauseInstanceOf(java.awt.HeadlessException.class);
            edt(() -> indicator.actions().get().quit().run());
            assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            edt(application::quitNow);
            assertThat(indicator.removed()).hasValue(1);
        } finally { edt(application::quitNow); }
    }

    @Test void everyTriggerAtOnceStillInstallsOnce() throws Exception {
        var indicator = new Indicator();
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(() -> { }, indicator, trace);
        try {
            fallback(application, Duration.ofMillis(50));
            resident(application);
            resident(application);
            trace.mark(StartupTrace.Milestone.WARMED_UP);
            trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            DesktopTestSupport.until(() -> indicator.shown().get() >= 1);
            Thread.sleep(200); // past the fallback, which must have been cancelled or found nothing to do
            edt(() -> { });
            assertThat(indicator.shown()).hasValue(1);
            assertThat(indicator.removed()).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aBridgeThatThrowsWhilePreparingStillGetsTheIndicator() throws Exception {
        var indicator = new Indicator();
        var prepares = new AtomicInteger();
        var uncaught = new java.util.concurrent.CopyOnWriteArrayList<Throwable>();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        // Settling does its work once and nothing retries it, so the indicator must not depend on the bridge having loaded.
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> uncaught.add(failure));
        JasperApplication application = application(() -> { }, indicator, settled());
        try {
            edt(() -> application.backgroundOnQuit(new DockControl(visible -> { }, () -> {
                prepares.incrementAndGet();
                throw new IllegalStateException("the bridge could not be loaded");
            }, () -> false), true));
            resident(application);
            DesktopTestSupport.until(() -> indicator.shown().get() == 1 && !uncaught.isEmpty());
            assertThat(prepares).as("start-up tried to prepare it").hasValue(1);
            assertThat(indicator.removed()).hasValue(0);
            assertThat(application.quitIsSystemQuit()).as("a bridge that never prepared: quits stay real").isTrue();
            assertThat(uncaught).as("the failure propagated as before").singleElement()
                .isInstanceOf(IllegalStateException.class);
            resident(application);
            assertThat(prepares).as("settling does not run again").hasValue(1);
            assertThat(indicator.shown()).hasValue(1);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
            edt(application::quitNow);
        }
    }

    @Test void aProcessThatIsNotResidentShowsNothing() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator, settled());
        try {
            edt(() -> application.residency(false));
            edt(() -> { });
            assertThat(indicator.shown()).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aProcessThatStopsBeingResidentBeforeTheFallbackShowsNothing() throws Exception {
        var indicator = new Indicator();
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(() -> { }, indicator, trace);
        try {
            fallback(application, Duration.ofMillis(250));
            resident(application);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            edt(() -> { });
            edt(() -> application.residency(false));
            Thread.sleep(500);
            edt(() -> { });
            assertThat(indicator.shown()).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void quitFromTheIndicatorExitsAndRemovesIt() throws Exception {
        var indicator = new Indicator();
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown, indicator, settled());
        resident(application);
        edt(() -> indicator.actions().get().quit().run());
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("Quit Jasper Completely always exits").isTrue();
        assertThat(indicator.removed()).hasValue(1);
    }

    @Test void openAfterQuitIsHarmless() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator, settled());
        resident(application);
        edt(application::quit);
        // A late Open from the tray, queued before the icon was removed, must not create a window.
        edt(() -> indicator.actions().get().open().run());
        edt(() -> { });
        assertThat(indicator.removed()).hasValue(1);
    }

    @Test void theMenuBarActionsAreHarmlessOnceQuitting() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator, settled());
        resident(application);
        edt(application::quit);
        edt(() -> indicator.actions().get().newWindow().run());
        edt(() -> indicator.actions().get().settings().run());
        edt(() -> { });
        assertThat(indicator.removed()).hasValue(1);
    }

    @Test void nothingIsShownOnceQuitting() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator, settled());
        edt(application::quitNow);
        resident(application);
        assertThat(indicator.shown()).hasValue(0);
    }
}
