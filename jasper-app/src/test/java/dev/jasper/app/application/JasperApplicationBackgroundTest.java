package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.contributions.WorkspaceHost;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.restart.RestartMode;
import dev.jasper.app.startup.StartupTrace;
import java.awt.HeadlessException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

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
    /** Where Jasper stood for notifications at each Dock switch: the first switch happens inside the window's opening. */
    private final List<Delivery.Presence> presenceAtSwitch = new CopyOnWriteArrayList<>();
    private final AtomicInteger prepared = new AtomicInteger();
    private final AtomicBoolean poweringOff = new AtomicBoolean();
    /** What the bridge's prepare reports: false stands for a library that did not load or a missing power-off observer. */
    private final AtomicBoolean prepareSucceeds = new AtomicBoolean(true);

    @TempDir Path home;

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
            held[0].backgroundOnQuit(new DockControl(shown -> {
                presenceAtSwitch.add(held[0].notificationCenter().presence());
                dock.add(shown);
            }, () -> {
                prepared.incrementAndGet();
                return prepareSucceeds.get();
            }, poweringOff::get), visibleAtLaunch);
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
            assertThatThrownBy(() -> edt(() -> application.open(List.of(), "reopen"))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).containsExactly(true, false);
        } finally {
            edt(application::quitNow);
        }
    }

    /**
     * A {@code --background} launch whose setting is off but which holds a request (plan Ruling 20) began as an accessory
     * application: its window needs the Dock presence a resident's gets, and it keeps it, since it quits with its last window.
     */
    @Test void aHiddenLaunchThatIsNotResidentShowsItselfInTheDockBeforeItsFirstWindow() throws Exception {
        JasperApplication application = application(() -> { }, false);
        try {
            edt(() -> application.residency(false));
            assertThat(dock).as("nothing opened yet").isEmpty();
            assertThatThrownBy(() -> edt(() -> application.open(List.of(), "handoff"))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).as("in the Dock before the window is composed, and not hidden again").containsExactly(true);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aLaunchThatIsNotResidentAndWasNeverHiddenLeavesTheDockAlone() throws Exception {
        JasperApplication application = application(() -> { }, true);
        try {
            edt(() -> application.residency(false));
            assertThatThrownBy(() -> edt(() -> application.open(List.of(), "handoff"))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).as("it was in the Dock from the start").isEmpty();
        } finally {
            edt(application::quitNow);
        }
    }

    private static Delivery.Presence presence(JasperApplication application) throws Exception {
        Delivery.Presence[] held = new Delivery.Presence[1];
        edt(() -> held[0] = application.notificationCenter().presence());
        return held[0];
    }

    @Test void aResidentJasperWithNoWindowIsIdleExceptWhileOneIsBeingOpened() throws Exception {
        JasperApplication application = settled(() -> { }, false);
        try {
            assertThat(presence(application)).as("resident, nothing open, nothing opening: it may stay so for hours")
                .isEqualTo(Delivery.Presence.IDLE);
            // Headless, composing fails at the JFrame; the Dock switch inside the opening sees the open in progress.
            assertThatThrownBy(() -> edt(() -> application.open(List.of(), "reopen"))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(presenceAtSwitch).as("a failure raised while the window opens still waits for it")
                .startsWith(Delivery.Presence.NO_WINDOW);
            assertThat(presence(application)).as("the failed open is over: idle again").isEqualTo(Delivery.Presence.IDLE);
            assertThatThrownBy(() -> edt(() -> application.openFirstWindow(List.of()))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(presence(application)).as("every open, failed or not, ends its opening").isEqualTo(Delivery.Presence.IDLE);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aProcessThatIsNotResidentHasNoWindowButIsNeverIdle() throws Exception {
        JasperApplication application = application(() -> { }, true);
        try {
            assertThat(presence(application)).as("before residency is decided (start-up)").isEqualTo(Delivery.Presence.NO_WINDOW);
            edt(() -> application.residency(false));
            assertThat(presence(application)).as("a plain start before its first window").isEqualTo(Delivery.Presence.NO_WINDOW);
            assertThatThrownBy(() -> edt(() -> application.openFirstWindow(List.of()))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(presence(application)).isEqualTo(Delivery.Presence.NO_WINDOW);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aFailureQueuedDuringStartUpWaitsForTheFirstWindowAndOneAfterSettlingGoesToHistory() throws Exception {
        // A --background launch is resident with no window from the moment the application exists, and incidents are
        // recorded through invokeLater: one raised while the application was built runs only now. Before start-up has
        // settled it must still wait for the first window, as the plugin and configuration problems posted directly do.
        StartupTrace trace = StartupTrace.detached();
        JasperApplication application = application(trace, () -> { }, false);
        List<NotificationCenter.Change> changes = new ArrayList<>();
        try {
            edt(() -> {
                application.settleFallback(Duration.ofHours(1));
                application.residency(true);
                application.notificationCenter().onChange(changes::add);
                application.unexpected(Thread.currentThread(), new IllegalStateException("queued during start-up"));
            });
            edt(() -> { });
            assertThat(prepared).as("start-up has not settled").hasValue(0);
            assertThat(presence(application)).as("resident, no window, but start-up is still under way")
                .isEqualTo(Delivery.Presence.NO_WINDOW);
            trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
            edt(() -> { });
            assertThat(prepared).as("start-up has settled").hasValue(1);
            assertThat(presence(application)).isEqualTo(Delivery.Presence.IDLE);
            edt(() -> application.unexpected(Thread.currentThread(), new UnsupportedOperationException("after start-up")));
            edt(() -> { });
            edt(() -> {
                assertThat(application.notificationCenter().history()).extracting(entry -> entry.request().content())
                    .containsExactly("UnsupportedOperationException: after start-up", "IllegalStateException: queued during start-up");
                application.notificationCenter().focusReturned();
            });
            assertThat(changes).filteredOn(NotificationCenter.Change.CardRequested.class::isInstance)
                .extracting(change -> ((NotificationCenter.Change.CardRequested) change).notification().request().content())
                .as("the start-up failure waited for a window; the one after settling went to history")
                .containsExactly("IllegalStateException: queued during start-up");
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aWindowThatFailsToComposeLeavesNoDockIconWithNothingOpen() throws Exception {
        // A plain launch is in the Dock already: the failed window must not leave it there with nothing open.
        JasperApplication application = settled(() -> { }, true);
        try {
            assertThatThrownBy(() -> edt(() -> application.open(List.of(), "menu"))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).containsExactly(false);
            assertThatThrownBy(() -> edt(() -> application.openFirstWindow(List.of()))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).as("the second attempt shows Jasper, then hides it again").containsExactly(false, true, false);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aWindowFromAWarmedUpProcessIsLabelledMenuNotCold() throws Exception {
        // Headless, composing fails at the JFrame, after the window's timing began: that timing carries the trigger.
        StartupTrace cold = StartupTrace.detached();
        JasperApplication coldApplication = application(cold, () -> { }, true);
        try {
            assertThatThrownBy(() -> edt(() -> coldApplication.openFirstWindow(List.of()))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(cold.windows()).extracting(window -> window.trigger()).containsExactly("cold");
        } finally {
            edt(coldApplication::quitNow);
        }
        StartupTrace warmed = StartupTrace.detached();
        warmed.mark(StartupTrace.Milestone.WARMED_UP);
        JasperApplication warmedApplication = application(warmed, () -> { }, true);
        try {
            assertThatThrownBy(() -> edt(() -> warmedApplication.openFirstWindow(List.of()))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(warmed.windows()).extracting(window -> window.trigger()).containsExactly("menu");
        } finally {
            edt(warmedApplication::quitNow);
        }
    }

    @Test void aWindowThatFailsToComposeInANonResidentProcessNeverTouchesTheDock() throws Exception {
        JasperApplication application = application(() -> { }, true);
        try {
            edt(() -> application.residency(false));
            assertThatThrownBy(() -> edt(() -> application.openFirstWindow(List.of()))).hasRootCauseInstanceOf(HeadlessException.class);
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

    @Test void aContentTabThatRefusesToCloseKeepsEverythingOpenUntilItAgrees() throws Exception {
        var terminations = new AtomicInteger();
        var asked = new AtomicInteger();
        var allow = new AtomicBoolean();
        JasperApplication application = settled(terminations::incrementAndGet, true);
        try {
            edt(() -> {
                var content = application.contributions().tabs();
                UUID window = UUID.randomUUID();
                content.addHost(new WorkspaceHost(window, entry -> { }, entry -> { }, entry -> { }, () -> { }));
                content.open("dev.example.editor", "notes.txt", "notes.txt", window, new JLabel("unsaved"));
                content.guard("dev.example.editor", (entries, batch) -> {
                    asked.incrementAndGet();
                    return CompletableFuture.completedFuture(allow.get());
                });
            });
            edt(application::quitRequested);
            assertThat(asked).as("the editor was asked, as when its window closes").hasValue(1);
            assertThat(dock).as("nothing closed, so Jasper stays in the Dock").isEmpty();
            allow.set(true);
            edt(application::quitRequested);
            assertThat(asked).hasValue(2);
            assertThat(dock).as("it closes once the editor agrees").containsExactly(false);
            assertThat(terminations).as("and the process stays").hasValue(0);
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
                application.settleFallback(Duration.ofHours(1));
                application.residency(true);
                application.residency(true);
            });
            trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
            trace.mark(StartupTrace.Milestone.WARMED_UP);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            // Every settled step queued above has run once the EDT reaches this turn.
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

    @Test void aProcessThatIsNotResidentKeepsTheOrdinaryHandlerEvenWithAPresence() throws Exception {
        // --safe-mode, --config, another home or run_in_background = false: a presence is installed, nothing prepares it.
        var terminated = new CountDownLatch(1);
        var recorded = new Answer();
        var answer = new Answer();
        JasperApplication application = application(terminated::countDown, true);
        edt(() -> application.residency(false));
        assertThat(application.quitIsSystemQuit()).as("today's handler: cancel, then capture, then quit").isFalse();
        application.nativeQuitRequested(recorded, () -> true);
        edt(() -> { });
        assertThat(recorded.cancelled()).as("the Shortcuts recorder takes Cmd+Q").hasValue(1);
        assertThat(terminated.getCount()).as("and Jasper does not quit").isEqualTo(1);
        application.nativeQuitRequested(answer, () -> false);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(answer.cancelled()).as("cancelled first, then a real quit").hasValue(1);
        assertThat(dock).isEmpty();
    }

    @Test void aPresenceThatNeverBecameResidentAlsoKeepsTheOrdinaryHandler() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown, true);
        assertThat(application.quitIsSystemQuit()).isFalse();
        edt(application::quitRequested);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(dock).isEmpty();
    }

    @Test void aWindowRecordingShortcutsTakesCommandQBeforeTheBridgeIsPrepared() throws Exception {
        var terminations = new AtomicInteger();
        var answer = new Answer();
        JasperApplication application = unsettled(terminations::incrementAndGet, true);
        try {
            assertThat(application.quitIsSystemQuit()).isTrue();
            application.nativeQuitRequested(answer, () -> true);
            edt(() -> { });
            assertThat(answer.cancelled()).as("the recorder keeps the keystroke").hasValue(1);
            assertThat(terminations).hasValue(0);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aLogOutIsNeverCancelledWhileAWindowRecordsShortcuts() throws Exception {
        var terminated = new CountDownLatch(1);
        var answer = new Answer();
        JasperApplication application = settled(terminated::countDown, true);
        poweringOff.set(true);
        application.nativeQuitRequested(answer, () -> true);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(answer.cancelled()).as("the log-out goes on").hasValue(0);
    }

    @Test void anOrdinaryRequestIsCancelledAndTakenByAWindowRecordingShortcuts() throws Exception {
        var terminations = new AtomicInteger();
        var answer = new Answer();
        JasperApplication application = settled(terminations::incrementAndGet, true);
        try {
            assertThat(application.quitIsSystemQuit()).isFalse();
            application.nativeQuitRequested(answer, () -> true);
            edt(() -> { });
            assertThat(answer.cancelled()).hasValue(1);
            assertThat(terminations).hasValue(0);
            assertThat(dock).as("not even the background").isEmpty();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aBridgeThatCouldNotPrepareLeavesLogOutsUncancelled() throws Exception {
        // The library did not load, or loaded without a power-off observer: poweringOff would read false for ever.
        prepareSucceeds.set(false);
        var terminated = new CountDownLatch(1);
        var answer = new Answer();
        StartupTrace trace = StartupTrace.detached();
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        JasperApplication application = application(trace, terminated::countDown, true);
        edt(() -> application.residency(true));
        edt(() -> { });
        assertThat(prepared).as("start-up tried to prepare it").hasValue(1);
        assertThat(application.quitIsSystemQuit()).as("a resident process that never prepared").isTrue();
        application.nativeQuitRequested(answer, () -> false);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(answer.cancelled()).hasValue(0);
        assertThat(dock).isEmpty();
    }

    @Test void aBridgeThatCouldNotPrepareMakesJasperQuitRealInsteadOfGoingToTheBackground() throws Exception {
        prepareSucceeds.set(false);
        var terminated = new CountDownLatch(1);
        StartupTrace trace = StartupTrace.detached();
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        JasperApplication application = application(trace, terminated::countDown, true);
        edt(() -> application.residency(true));
        edt(() -> { });
        edt(application::quitRequested);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(dock).isEmpty();
    }

    @Test void withoutAPresenceTheNativeQuitRequestIsCancelledAndThenQuits() throws Exception {
        var terminated = new CountDownLatch(1);
        var answer = new Answer();
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            held[0].trace(StartupTrace.detached());
            held[0].residency(true);
        });
        held[0].nativeQuitRequested(answer);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(answer.cancelled()).as("as before: cancelled, then quit").hasValue(1);
    }

    @Test void aReopenAfterAnUpdateRelaunchesTheNewBuildFromTheBackground() throws Exception {
        var terminated = new CountDownLatch(1);
        var spawned = new CopyOnWriteArrayList<List<String>>();
        var modes = new CopyOnWriteArrayList<RestartMode>();
        JasperApplication application = application(terminated::countDown, false);
        edt(() -> {
            application.residency(true);
            application.restartPlanner = mode -> {
                modes.add(mode);
                return Optional.of(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper"));
            };
            application.spawner = spawned::add;
            application.staleBuild(() -> true);
        });
        edt(application::reopened);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("the old build exits").isTrue();
        assertThat(modes).containsExactly(RestartMode.SAME);
        assertThat(spawned).as("and starts the new one, waiting for this process's lock")
            .containsExactly(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper", "--replacing", Long.toString(ProcessHandle.current().pid())));
        assertThat(dock).as("no window of the old build was shown").isEmpty();
    }

    @Test void aReopenOfTheRunningBuildOpensAWindowAndRelaunchesNothing() throws Exception {
        var spawned = new CopyOnWriteArrayList<List<String>>();
        JasperApplication application = application(() -> { }, false);
        try {
            edt(() -> {
                application.residency(true);
                application.spawner = spawned::add;
                application.staleBuild(() -> false);
            });
            assertThatThrownBy(() -> edt(application::reopened)).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).as("it went for a window").containsExactly(true, false);
            assertThat(spawned).isEmpty();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void anUpdateThatCannotRelaunchStillOpensAWindowFromTheRunningBuild() throws Exception {
        var spawned = new CopyOnWriteArrayList<List<String>>();
        JasperApplication application = application(() -> { }, false);
        try {
            edt(() -> {
                application.residency(true);
                application.restartPlanner = mode -> Optional.empty();
                application.spawner = spawned::add;
                application.staleBuild(() -> true);
            });
            assertThatThrownBy(() -> edt(application::reopened)).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(dock).as("it went for a window").containsExactly(true, false);
            assertThat(spawned).isEmpty();
            assertThat(application.resident()).isTrue();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aPluginThatRefusesTheRelaunchStillGetsAWindowFromTheRunningBuild() throws Exception {
        // restart() returns true with nothing changed when a plugin's quit guard refuses. That is not a failure to plan a
        // relaunch, so the reopen has to notice that the process is still running and open the window itself.
        var spawned = new CopyOnWriteArrayList<List<String>>();
        var planned = new AtomicInteger();
        var asked = new AtomicInteger();
        var terminations = new AtomicInteger();
        JasperApplication application = application(terminations::incrementAndGet, false);
        try {
            edt(() -> {
                application.residency(true);
                application.contributions().quitGuards().add(() -> {
                    asked.incrementAndGet();
                    return CompletableFuture.completedFuture(false);
                });
                application.restartPlanner = mode -> {
                    planned.incrementAndGet();
                    return Optional.of(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper"));
                };
                application.spawner = spawned::add;
                application.staleBuild(() -> true);
            });
            // Composing the window is where a headless JVM fails. Reaching it proves the process was neither quitting nor
            // stopped, because open returns first thing in either state.
            assertThatThrownBy(() -> edt(application::reopened)).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(planned).as("a relaunch was planned").hasValue(1);
            assertThat(asked).as("and the plugin refused it").hasValue(1);
            assertThat(spawned).as("so nothing was started").isEmpty();
            assertThat(dock).as("the window was attempted from the running build").containsExactly(true, false);
            edt(() -> { });
            assertThat(terminations).as("and the process did not exit").hasValue(0);
            assertThat(application.resident()).isTrue();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test @DisabledOnOs(OS.WINDOWS) void aReopenWithAWindowOpenNeverRelaunchesTheBuild() throws Exception {
        // "An upgrade must never take a running terminal down": any open window, an auxiliary one included, keeps the
        // running build, and the reopen goes on as an ordinary one.
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var spawned = new CopyOnWriteArrayList<List<String>>();
        var planned = new AtomicInteger();
        var terminated = new CountDownLatch(1);
        JasperApplication application = settled(terminated::countDown, true);
        try {
            edt(() -> {
                application.startPlugins(dirs);
                application.auxiliary().window("test.window", "Window", new java.awt.Dimension(100, 100), false);
                application.restartPlanner = mode -> {
                    planned.incrementAndGet();
                    return Optional.of(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper"));
                };
                application.spawner = spawned::add;
                application.staleBuild(() -> true);
            });
            // An auxiliary window is open; no Jasper window can be composed headlessly, so the reopen ends there.
            assertThatThrownBy(() -> edt(application::reopened)).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(planned).as("the update was not acted on while a window is open").hasValue(0);
            assertThat(spawned).isEmpty();
            assertThat(terminated.getCount()).as("and the process runs on").isEqualTo(1);
        } finally {
            edt(application::quitNow);
            // The shutdown writes the plugin's and the window state under the temporary directory.
            assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test @DisabledOnOs(OS.WINDOWS) void anUpdatedLaunchWithAWindowOpenSaysSoAndRelaunchesNothing() throws Exception {
        // Spec 5.3: with windows open the owner carries the request out itself and says it was updated.
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var spawned = new CopyOnWriteArrayList<List<String>>();
        var planned = new AtomicInteger();
        var terminated = new CountDownLatch(1);
        JasperApplication application = settled(terminated::countDown, true);
        try {
            edt(() -> {
                application.startPlugins(dirs);
                application.auxiliary().window("test.window", "Window", new java.awt.Dimension(100, 100), false);
                application.restartPlanner = mode -> {
                    planned.incrementAndGet();
                    return Optional.of(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper"));
                };
                application.spawner = spawned::add;
                application.staleBuild(() -> true);
            });
            // An auxiliary window is open; the request then opens the main window, which cannot be composed headlessly.
            assertThatThrownBy(() -> edt(() -> application.handedOff(List.of(), List.of(), true))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(planned).as("an update never takes an open window down").hasValue(0);
            assertThat(spawned).isEmpty();
            edt(() -> assertThat(application.notificationCenter().history()).extracting(entry -> entry.request().title())
                .contains("Jasper was updated"));
        } finally {
            edt(application::quitNow);
            assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
        }
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
