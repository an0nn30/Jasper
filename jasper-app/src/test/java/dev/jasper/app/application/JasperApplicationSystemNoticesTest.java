package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.SystemNotifier;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * When Jasper hands a notification to the operating system, tested through a headless application with no window: not
 * while a login start in the background finishes starting, not once it quits, and otherwise as usual.
 */
class JasperApplicationSystemNoticesTest {
    private final CountDownLatch terminated = new CountDownLatch(1);
    /** What the application asked the operating system for: the titles of the {@code SystemRequested} changes. */
    private final List<String> requested = new CopyOnWriteArrayList<>();
    /** What the operating system's backend was given to show. */
    private final List<String> banners = new CopyOnWriteArrayList<>();
    private JasperApplication application;

    private void start() throws Exception {
        edt(() -> {
            application = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application.systemNotifier(SystemNotifier.using(Runnable::run, clicks -> new SystemNotifier.Backend(
                message -> banners.add(message.title()), id -> { }, () -> { }, () -> { }, () -> { })));
            application.notificationCenter().onChange(change -> {
                if (change instanceof NotificationCenter.Change.SystemRequested asked)
                    requested.add(asked.notification().request().title());
            });
        });
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
     * A plugin that fails to start is recorded through {@code invokeLater}, so the record runs after the start-up
     * task that warms the resident Jasper up. Nobody is away from a Jasper that is still starting: the card waits and
     * the operating system is left alone (spec section 7).
     */
    @Test void aNotificationRecordedWhileAResidentJasperWarmsUpDoesNotReachTheOperatingSystem() throws Exception {
        start();
        edt(() -> {
            SwingUtilities.invokeLater(() -> application.notificationCenter().post(request("Remote failed to start")));
            application.warmUp();
        });
        edt(() -> { });
        int[] recorded = new int[1];
        edt(() -> recorded[0] = application.notificationCenter().history().size());
        assertThat(recorded[0]).as("it is recorded").isEqualTo(1);
        assertThat(requested).as("nothing asked of the operating system while starting").isEmpty();
        assertThat(banners).isEmpty();
        post("Later failure");
        assertThat(requested).as("a resident Jasper that has started is away").containsExactly("Later failure");
        assertThat(banners).containsExactly("Later failure");
        quit();
    }

    @Test void whileJasperQuitsNothingReachesTheOperatingSystem() throws Exception {
        start();
        edt(application::warmUp);
        edt(() -> { });
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
}
