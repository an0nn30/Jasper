package dev.jasper.app.application;

import dev.jasper.app.bootstrap.BootstrapTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.residency.LaunchRequest;
import dev.jasper.app.workspace.DesktopTestSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

/** The resident process shows an indicator (the Windows tray icon) exactly while it is resident. */
class ResidentIndicatorTest {

    private record Indicator(AtomicInteger shown, AtomicInteger removed,
                             AtomicReference<Runnable> open, AtomicReference<Runnable> quit) {
        Indicator() { this(new AtomicInteger(), new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>()); }

        java.util.function.BinaryOperator<Runnable> installer() {
            return (openAction, quitAction) -> {
                shown.incrementAndGet();
                open.set(openAction);
                quit.set(quitAction);
                return removed::incrementAndGet;
            };
        }
    }

    private JasperApplication application(Runnable terminate, Indicator indicator) throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminate);
            held[0].residentIndicator(indicator.installer());
        });
        return held[0];
    }

    @Test void aResidentProcessShowsTheIndicatorOnce() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator);
        try {
            edt(() -> application.residency(true));
            edt(() -> application.residency(true));
            assertThat(indicator.shown()).hasValue(1);
            assertThat(indicator.removed()).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aProcessThatIsNotResidentShowsNothing() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator);
        try {
            edt(() -> application.residency(false));
            assertThat(indicator.shown()).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void quitFromTheIndicatorExitsAndRemovesIt() throws Exception {
        var indicator = new Indicator();
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown, indicator);
        edt(() -> application.residency(true));
        edt(() -> indicator.quit().get().run());
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("Quit from the tray always exits").isTrue();
        assertThat(indicator.removed()).hasValue(1);
    }

    @Test void losingTheEndpointRemovesTheIndicator() throws Exception {
        var indicator = new Indicator();
        var terminations = new AtomicInteger();
        JasperApplication application = application(terminations::incrementAndGet, indicator);
        edt(() -> application.residency(true));
        var handler = BootstrapTestSupport.handoffHandler(application, Path.of("/old.jar"), 1L);
        assertThat(handler.apply(new LaunchRequest("t", Path.of("/new.jar"), 2L))).isEqualTo(LaunchRequest.Response.STALE);
        edt(() -> { });
        assertThat(indicator.removed()).as("not resident any more").hasValue(1);
        DesktopTestSupport.until(() -> terminations.get() == 1);
        assertThat(indicator.removed()).as("removed once").hasValue(1);
    }

    @Test void openAfterQuitIsHarmless() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator);
        edt(() -> application.residency(true));
        edt(application::quit);
        // A late Open from the tray, queued before the icon was removed, must not create a window.
        edt(() -> indicator.open().get().run());
        edt(() -> { });
        assertThat(indicator.removed()).hasValue(1);
    }

    @Test void nothingIsShownOnceQuitting() throws Exception {
        var indicator = new Indicator();
        JasperApplication application = application(() -> { }, indicator);
        edt(application::quitNow);
        edt(() -> application.residency(true));
        assertThat(indicator.shown()).hasValue(0);
    }
}
