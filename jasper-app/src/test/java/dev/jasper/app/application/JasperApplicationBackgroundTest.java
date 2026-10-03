package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.contributions.ContentHost;
import dev.jasper.app.startup.StartupTrace;
import java.awt.HeadlessException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A resident macOS Jasper with Dock presence: Quit goes to the background, a window brings it back, and only Quit
 * Jasper Completely or macOS logging out ends the process. The Dock switch is a recording stand-in for the bridge.
 * Until start-up has settled the bridge is not prepared, and a quit then is a real quit that never cancels.
 */
class JasperApplicationBackgroundTest {
    private final List<Boolean> dock = new CopyOnWriteArrayList<>();
    private final AtomicInteger prepared = new AtomicInteger();
    private final AtomicBoolean poweringOff = new AtomicBoolean();

    /** How macOS's quit request was answered. */
    private record Answer(AtomicInteger cancelled, AtomicInteger performed) implements java.awt.desktop.QuitResponse {
        Answer() { this(new AtomicInteger(), new AtomicInteger()); }
        @Override public void cancelQuit() { cancelled.incrementAndGet(); }
        @Override public void performQuit() { performed.incrementAndGet(); }
    }

    private JasperApplication application(StartupTrace trace, Runnable terminate, boolean visibleAtLaunch) throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminate);
            held[0].trace(trace);
            held[0].backgroundOnQuit(new DockControl(dock::add, prepared::incrementAndGet, poweringOff::get), visibleAtLaunch);
        });
        return held[0];
    }

    private JasperApplication application(Runnable terminate, boolean visibleAtLaunch) throws Exception {
        return application(StartupTrace.detached(), terminate, visibleAtLaunch);
    }

    /** Resident, with start-up settled: the first output has painted, so the bridge is prepared. */
    private JasperApplication settled(Runnable terminate, boolean visibleAtLaunch) throws Exception {
        StartupTrace trace = StartupTrace.detached();
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        JasperApplication application = application(trace, terminate, visibleAtLaunch);
        edt(() -> application.residency(true));
        edt(() -> { });
        assertThat(prepared).as("start-up has settled").hasValue(1);
        return application;
    }

    /** Resident, with start-up still to settle: nothing has painted output and the fallback is far away. */
    private JasperApplication unsettled(Runnable terminate, boolean visibleAtLaunch) throws Exception {
        JasperApplication application = application(terminate, visibleAtLaunch);
        edt(() -> {
            application.settleFallback(Duration.ofHours(1));
            application.residency(true);
        });
        edt(() -> { });
        assertThat(prepared).as("not prepared before start-up settles").hasValue(0);
        return application;
    }

    @Test void quitOnAResidentProcessClosesEverythingAndKeepsItInTheBackground() throws Exception {
        var terminations = new AtomicInteger();
        JasperApplication application = settled(terminations::incrementAndGet, true);
        try {
            edt(application::quitRequested);
            edt(() -> { });
            assertThat(dock).as("hidden from the Dock and Cmd+Tab").containsExactly(false);
            assertThat(terminations).as("the process stays").hasValue(0);
            assertThat(application.resident()).isTrue();
            edt(application::quitRequested);
            assertThat(dock).as("already in the background").containsExactly(false);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void theLastWindowClosingAlsoGoesToTheBackground() throws Exception {
        JasperApplication application = settled(() -> { }, true);
        try {
            // windowClosed(null) is how these suites say "the last window just closed" without a native window.
            edt(() -> application.windowClosed(null));
            assertThat(dock).containsExactly(false);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aWindowInTheBackgroundShowsJasperBeforeItIsComposed() throws Exception {
        JasperApplication application = settled(() -> { }, false);
        try {
            // Headless, composing the window fails at its JFrame. By then Jasper must already be back in the Dock (macOS
            // gives a window of a hidden application neither the menu bar nor focus), and it hides again because
            // nothing opened.
            assertThatThrownBy(() -> edt(() -> application.openOrRaise("reopen"))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).containsExactly(true, false);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aWindowThatFailsToComposeLeavesNoDockIconWithNothingOpen() throws Exception {
        // A plain launch is in the Dock already: the failed window must not leave it there with nothing open.
        JasperApplication application = settled(() -> { }, true);
        try {
            assertThatThrownBy(() -> edt(() -> application.openOrRaise("menu"))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).containsExactly(false);
            assertThatThrownBy(() -> edt(() -> application.newWindow())).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).as("the second attempt shows Jasper, then hides it again").containsExactly(false, true, false);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aWindowThatFailsToComposeInANonResidentProcessNeverTouchesTheDock() throws Exception {
        JasperApplication application = application(() -> { }, true);
        try {
            edt(() -> application.residency(false));
            assertThatThrownBy(() -> edt(() -> application.newWindow())).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).isEmpty();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void quitJasperCompletelyEndsTheProcessWithoutTouchingTheDock() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = settled(terminated::countDown, true);
        edt(application::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(dock).isEmpty();
    }

    @Test void withResidencyOffQuitQuitsAsBefore() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown, true);
        edt(() -> application.residency(false));
        edt(application::quitRequested);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(dock).isEmpty();
    }

    @Test void withoutDockPresenceAResidentProcessStillQuits() throws Exception {
        // Windows and development runs: residency keeps a windowless process, but Quit exits for good.
        var terminated = new CountDownLatch(1);
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            held[0].trace(StartupTrace.detached());
        });
        edt(() -> held[0].residency(true));
        assertThat(held[0].quitIsSystemQuit()).as("the handler keeps cancelling, then quitting").isFalse();
        edt(held[0]::quitRequested);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test void aContentTabThatRefusesToCloseKeepsEverythingOpen() throws Exception {
        var asked = new AtomicInteger();
        JasperApplication application = settled(() -> { }, true);
        try {
            edt(() -> {
                var content = application.contributions().content();
                content.addHost(new ContentHost(UUID.randomUUID(), false, entry -> { }, entry -> { }, entry -> { }, () -> { }));
                content.open("dev.example.editor", "notes.txt", "notes.txt", null, new JLabel("unsaved"));
                content.guard("dev.example.editor", (entries, batch) -> {
                    asked.incrementAndGet();
                    return CompletableFuture.completedFuture(false);
                });
            });
            edt(application::quitRequested);
            assertThat(asked).as("the editor was asked, as when its window closes").hasValue(1);
            assertThat(dock).as("nothing closed, so Jasper stays in the Dock").isEmpty();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void goingToTheBackgroundDoesNotAskPluginsWhetherJasperMayQuit() throws Exception {
        var terminations = new AtomicInteger();
        var asked = new AtomicInteger();
        JasperApplication application = settled(terminations::incrementAndGet, true);
        try {
            edt(() -> application.contributions().quitGuards().add(() -> {
                asked.incrementAndGet();
                return CompletableFuture.completedFuture(false);
            }));
            edt(application::quitRequested);
            assertThat(asked).as("plugins keep running in the background").hasValue(0);
            assertThat(dock).containsExactly(false);
            edt(application::quit);
            assertThat(asked).as("quitting completely asks them").hasValue(1);
            edt(() -> { });
            assertThat(terminations).as("and they may refuse").hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void whenMacOsAsksEveryAppToQuitJasperQuitsWithoutCancellingTheRequest() throws Exception {
        var terminated = new CountDownLatch(1);
        var answer = new Answer();
        JasperApplication application = settled(terminated::countDown, true);
        edt(() -> application.quitForSystem(answer));
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(answer.cancelled()).as("a cancelled request would interrupt the log-out").hasValue(0);
        assertThat(dock).isEmpty();
    }

    @Test void aPluginThatRefusesTheSystemsQuitCancelsTheRequest() throws Exception {
        var answer = new Answer();
        var terminations = new AtomicInteger();
        JasperApplication application = settled(terminations::incrementAndGet, true);
        try {
            edt(() -> application.contributions().quitGuards().add(() -> CompletableFuture.completedFuture(false)));
            edt(() -> application.quitForSystem(answer));
            assertThat(answer.cancelled()).hasValue(1);
            edt(() -> { });
            assertThat(terminations).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void theBridgeLoadsOnlyOnceStartUpHasSettled() throws Exception {
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(trace, () -> { }, true);
        try {
            edt(() -> {
                application.settleFallback(Duration.ofHours(1));
                application.residency(true);
            });
            edt(() -> { });
            assertThat(prepared).as("never on the first window's critical path").hasValue(0);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            edt(() -> { });
            assertThat(prepared).as("the first paint comes before the first output is shown").hasValue(0);
            trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
            edt(() -> { });
            assertThat(prepared).hasValue(1);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void theBridgeIsPreparedExactlyOnceWhenTheFirstOutputAndTheWarmUpBothSettleStartUp() throws Exception {
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(trace, () -> { }, true);
        try {
            edt(() -> {
                application.settleFallback(Duration.ofMillis(100));
                application.residency(true);
                application.residency(true);
            });
            trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
            trace.mark(StartupTrace.Milestone.WARMED_UP);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            until(() -> prepared.get() >= 1);
            Thread.sleep(400); // past the fallback, which must have been cancelled or found nothing to do
            edt(() -> application.residency(true));
            edt(() -> { });
            assertThat(prepared).hasValue(1);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void theBridgeIsPreparedExactlyOnceWhenTheFallbackSettlesStartUpFirst() throws Exception {
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(trace, () -> { }, true);
        try {
            edt(() -> {
                application.settleFallback(Duration.ofMillis(50));
                application.residency(true);
                application.residency(true);
            });
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            until(() -> prepared.get() == 1);
            trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
            trace.mark(StartupTrace.Milestone.WARMED_UP);
            edt(() -> application.residency(true));
            edt(() -> { });
            assertThat(prepared).as("the later triggers find start-up settled already").hasValue(1);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void beforeTheBridgeIsPreparedQuitIsARealQuit() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = unsettled(terminated::countDown, true);
        assertThat(application.quitIsSystemQuit()).as("a log-out cannot be told from Quit yet").isTrue();
        edt(application::quitRequested);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("never goes to the background").isTrue();
        assertThat(dock).isEmpty();
    }

    @Test void beforeTheBridgeIsPreparedTheNativeQuitRequestIsNeverCancelled() throws Exception {
        var terminated = new CountDownLatch(1);
        var answer = new Answer();
        JasperApplication application = unsettled(terminated::countDown, true);
        application.nativeQuitRequested(answer);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(answer.cancelled()).as("a cancelled log-out would be interrupted").hasValue(0);
        assertThat(dock).isEmpty();
    }

    @Test void afterTheBridgeIsPreparedQuitGoesToTheBackgroundUnlessMacOsIsPoweringOff() throws Exception {
        var terminations = new AtomicInteger();
        JasperApplication application = settled(terminations::incrementAndGet, true);
        try {
            poweringOff.set(false);
            assertThat(application.quitIsSystemQuit()).as("an ordinary Cmd+Q").isFalse();
            edt(application::quitRequested);
            assertThat(dock).containsExactly(false);
            assertThat(terminations).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void afterTheBridgeIsPreparedAPowerOffIsASystemQuit() throws Exception {
        var terminated = new CountDownLatch(1);
        var answer = new Answer();
        JasperApplication application = settled(terminated::countDown, true);
        poweringOff.set(true);
        assertThat(application.quitIsSystemQuit()).isTrue();
        application.nativeQuitRequested(answer);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(answer.cancelled()).as("the log-out goes on").hasValue(0);
        assertThat(dock).isEmpty();
    }

    @Test void anOrdinaryNativeQuitRequestIsCancelledAndGoesToTheBackground() throws Exception {
        var terminations = new AtomicInteger();
        var answer = new Answer();
        JasperApplication application = settled(terminations::incrementAndGet, true);
        try {
            application.nativeQuitRequested(answer);
            assertThat(answer.cancelled()).as("Jasper answers it itself").hasValue(1);
            until(() -> dock.size() == 1);
            assertThat(dock).containsExactly(false);
            assertThat(terminations).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }
}
