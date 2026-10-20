package dev.jasper.app.notificationui;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.notifications.Capture;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;
import static org.assertj.core.api.Assertions.assertThat;

class ThemeMigrationEvidenceTest {
    @Test void capturesRealErrorsEmptyPopulatedAndCleared() throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            var controller = ThemeTestSupport.controller(true);
            controller.configure(theme.equals("dark"), dev.jasper.app.config.TerminalSchemeChoice.MATCH);
            var kit = controller.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME);
            var center = new dev.jasper.app.notifications.NotificationCenter(Runnable::run, Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC), () -> dev.jasper.app.notifications.Delivery.Presence.FOCUSED);
            var group = new dev.jasper.app.notifications.NotificationGroup("fixture.cards", "Fixture", "jasper", dev.jasper.app.notifications.DisplayMode.CARD);
            center.register(group);
            var request = dev.jasper.app.notifications.NotificationRequest.of(group.id(), dev.jasper.app.notifications.Severity.WARNING, "Fixture notification").withContent("A fixture operation needs attention.")
                .withAction(dev.jasper.app.notifications.NotificationAction.of("Review", () -> { }));
            var notification = center.find(center.post(request).orElseThrow()).orElseThrow();
            var card = new NotificationCard(kit, center, notification, true);
            save("notifications", theme, "warning-card", card, 360, 160);
            var incidents = new Incidents(Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC));
            var view = new ErrorsView(kit, incidents, new ErrorsView.Hooks(id -> "Example plugin", id -> "1.0.0",
                new IncidentReport.Environment("baseline", "Headless", "JBR 25"), text -> { }, id -> { }, () -> { }), ZoneOffset.UTC, () -> { });
            try {
                save("notification-errors", theme, "empty", view, 900, 540);
                incidents.record("dev.jasper.remote", "Fixture operation", "FixtureThread", Capture.of(new IllegalStateException("Fixture failure")));
                assertThat(view.showingEmpty()).isFalse();
                save("notification-errors", theme, "incident", view, 900, 540);
                incidents.clear();
                save("notification-errors", theme, "cleared", view, 900, 540);
            } finally { view.close(); card.close(); controller.close(); }
        });
    }
}
