package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

class JasperApplicationNotificationsTest {
    @TempDir Path home;

    private static Optional<PanelBadge> badge(JasperApplication application) {
        return application.contributions().panels().stream()
            .filter(entry -> entry.id().equals(NotificationsFeature.PANEL_ID)).findFirst().orElseThrow().badge();
    }

    @Test void theApplicationOffersTheNotificationsPanelAndActions() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            var contributions = application[0].contributions();
            assertThat(contributions.panels()).extracting(PanelEntry::id).contains(NotificationsFeature.PANEL_ID);
            assertThat(contributions.action(NotificationsFeature.SHOW_HISTORY)).isPresent();
            assertThat(contributions.action(NotificationsFeature.DISMISS_CARDS)).isPresent();
            application[0].quitNow();
        });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test void anErrorBecomesAnUnreadErrorAndShutdownStopsTheBadge() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            assertThat(badge(application[0])).isEmpty();
            application[0].notificationCenter().submit(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.ERROR, "Could not connect"));
        });
        edt(() -> assertThat(badge(application[0]))
            .contains(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 unread notification, 1 error")));
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        edt(() -> application[0].notificationCenter().submit(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.ERROR, "After shutdown")));
        edt(() -> assertThat(badge(application[0])).as("the feature stopped listening at shutdown")
            .contains(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 unread notification, 1 error")));
    }

    @Test void configurationProblemsAtStartupPostOneUnreadNotification() throws Exception {
        Path file = home.resolve("config.toml");
        Files.writeString(file, "font.future = 1\n");
        var service = new ConfigService(file, false);
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            assertThat(service.initialState().diagnostics()).as("the fixture really has a problem").isNotEmpty();
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                assertThat(badge(application[0])).contains(new PanelBadge(1, PanelBadge.Tone.ACCENT, "1 unread notification"));
            });
        } finally {
            if (application[0] != null) {
                edt(application[0]::quit);
                assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            }
            service.close();
        }
    }
}
