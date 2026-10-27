package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.SystemNotifier;
import dev.jasper.app.startup.StartupTrace;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * When Jasper hands a notification to the operating system, tested through a headless resident application with no
 * window: not before start-up has settled (a login start in the background is still starting), not once it quits, and
 * otherwise as usual, since a resident Jasper with every window closed is idle.
 */
class JasperApplicationSystemNoticesTest {
    private final CountDownLatch terminated = new CountDownLatch(1);
    private final StartupTrace trace = StartupTrace.detached();
    /** What the application asked the operating system for: the titles of the {@code SystemRequested} changes. */
    private final List<String> requested = new CopyOnWriteArrayList<>();
    /** What the operating system's backend was given to show. */
    private final List<String> banners = new CopyOnWriteArrayList<>();
    private JasperApplication application;

    /** A resident Jasper with no window whose start-up has not settled yet: nothing but the trace settles it. */
    private void start() throws Exception {
        edt(() -> {
            application = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application.trace(trace);
            application.collector = () -> { };
            application.settleFallback(Duration.ofHours(1));
            application.systemNotifier(SystemNotifier.using(Runnable::run, clicks -> new SystemNotifier.Backend(
                message -> banners.add(message.title()), id -> { }, () -> { }, () -> { }, () -> { })));
            application.notificationCenter().onChange(change -> {
                if (change instanceof NotificationCenter.Change.SystemRequested asked)
                    requested.add(asked.notification().request().title());
            });
            application.residency(true);
        });
    }

    /** Start-up settles as it does once the first output is painted: the resident Jasper with no window is idle. */
    private void settle() throws Exception {
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        edt(() -> { });
        assertThat(presence()).isEqualTo(Delivery.Presence.IDLE);
    }

    private Delivery.Presence presence() throws Exception {
        Delivery.Presence[] held = new Delivery.Presence[1];
        edt(() -> held[0] = application.notificationCenter().presence());
        return held[0];
    }

    private static NotificationRequest request(String title) {
        return NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.ERROR, title);
    }

    private void post(String title) throws Exception {
        edt(() -> application.notificationCenter().submit(request(title)));
    }

    private void quit() throws Exception {
        edt(application::quitNow);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("Jasper terminated").isTrue();
    }

    /**
     * A plugin that fails to start is recorded through {@code invokeLater}, so the record runs after the start-up task
     * that warms the resident Jasper up, but before the settling the warm-up queues. Nobody is away from a Jasper that
     * is still starting: until start-up has settled there is no window yet ({@code NO_WINDOW}), the card waits, and the
     * operating system is left alone (spec section 7); a Jasper idle once settled is away.
     */
    @Test void aNotificationRecordedBeforeAResidentJasperSettlesDoesNotReachTheOperatingSystem() throws Exception {
        start();
        edt(() -> {
            SwingUtilities.invokeLater(() -> application.notificationCenter().post(request("Remote failed to start")));
            application.warmUp(() -> trace.mark(StartupTrace.Milestone.WARMED_UP));
        });
        edt(() -> { });
        int[] recorded = new int[1];
        edt(() -> recorded[0] = application.notificationCenter().history().size());
        assertThat(recorded[0]).as("it is recorded").isEqualTo(1);
        assertThat(requested).as("nothing asked of the operating system before start-up settled").isEmpty();
        assertThat(banners).isEmpty();
        assertThat(presence()).as("the warm-up settled start-up behind the record").isEqualTo(Delivery.Presence.IDLE);
        post("Later failure");
        assertThat(requested).as("a resident Jasper that has settled is away").containsExactly("Later failure");
        assertThat(banners).containsExactly("Later failure");
        quit();
    }

    @Test void untilStartUpSettlesNothingReachesTheOperatingSystem() throws Exception {
        start();
        post("While starting");
        assertThat(presence()).isEqualTo(Delivery.Presence.NO_WINDOW);
        assertThat(requested).isEmpty();
        assertThat(banners).isEmpty();
        settle();
        post("Once settled");
        assertThat(requested).containsExactly("Once settled");
        assertThat(banners).containsExactly("Once settled");
        quit();
    }

    @Test void whileJasperQuitsNothingReachesTheOperatingSystem() throws Exception {
        start();
        settle();
        post("Before quitting");
        edt(() -> {
            application.quitNow();
            // An incident raised while windows close, or a plugin posting from its stop().
            application.notificationCenter().submit(request("While quitting"));
        });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("Jasper terminated").isTrue();
        assertThat(requested).as("the center still routes it").containsExactly("Before quitting", "While quitting");
        assertThat(banners).containsExactly("Before quitting");
    }

    /** The rule behind {@code JasperApplication.systemNotificationsEnabled()}, as a table. */
    @Test void theOperatingSystemHearsOnlyWhileTheSettingIsOnAndJasperIsNeitherQuittingNorStopped() {
        assertThat(JasperApplication.systemNotificationsEnabled(true, false, false)).isTrue();
        assertThat(JasperApplication.systemNotificationsEnabled(false, false, false)).as("switched off").isFalse();
        assertThat(JasperApplication.systemNotificationsEnabled(true, true, false)).as("quitting").isFalse();
        // Stopping begins before plugins stop and the notices close: a plugin may still post from its stop().
        assertThat(JasperApplication.systemNotificationsEnabled(true, false, true)).as("stopped").isFalse();
        assertThat(JasperApplication.systemNotificationsEnabled(true, true, true)).isFalse();
    }

    @Test void clickingASystemNotificationRaisesThePertainingWindowNotTheLastActive() throws Exception {
        var fixture = new PertainingWindowFixture();
        try {
            edt(() -> {
                fixture.open();
                UUID id = fixture.finished(fixture.paneOfA(), () -> { });
                fixture.b.events().activated().accept(true);
                assertThat(fixture.application.windows().lastActive()).as("B was active last").contains(fixture.b);
                fixture.frames.clear();
                fixture.application.systemNotices().clicked(id);
                assertThat(fixture.frames).as("A comes forward, not B").containsExactly("front w0");
            });
        } finally {
            fixture.close();
        }
    }

    /** Full windows 2, final review I1: what the click's action opens in the most recently active window lands in the raised one. */
    @Test void aSystemNotificationsActionOpensItsTabInTheWindowTheClickRaised() throws Exception {
        var fixture = new PertainingWindowFixture();
        try {
            edt(() -> {
                fixture.open();
                fixture.b.events().activated().accept(true);
                var opened = new ArrayDeque<dev.jasper.app.contributions.Tab>();
                var request = NotificationRequest.of(NotificationsFeature.GENERAL.id(),
                        Severity.ERROR, "For A")
                    .withActivate(() -> opened.add(fixture.application.contributions().tabs().open(
                        dev.jasper.app.contributions.TabRegistry.APP_OWNER, "probe", "Probe", null, new javax.swing.JPanel())))
                    .withTarget(fixture.a.id());
                UUID id = fixture.application.notificationCenter().post(request).orElseThrow();
                fixture.frames.clear();
                fixture.application.systemNotices().clicked(id);
                assertThat(fixture.frames).as("A comes forward (and again as its new tab is selected); B never does").containsOnly("front w0");
                assertThat(opened).singleElement().satisfies(tab -> assertThat(tab.window()).as("the tab opens in A")
                    .isEqualTo(fixture.a.id()));
            });
        } finally {
            fixture.close();
        }
    }
}
