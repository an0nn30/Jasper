package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.presentation.AppPanels;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: under labelled stripes the Notifications panel is Event Log at Bottom Left; the default look keeps it. */
@ExtendWith(EdtTestExtension.class)
class EventLogTest {
    private static PanelEntry panel(Contributions contributions) {
        return contributions.panels().stream().filter(entry -> entry.id().equals(NotificationsFeature.PANEL_ID)).findFirst().orElseThrow();
    }

    @Test void underLabelledStripesTheNotificationsPanelIsTheEventLogAtBottomLeft() {
        var contributions = new Contributions();
        var center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
        try (var feature = new NotificationsFeature(center, contributions, KitTestSupport::context, new AppPanels("Event Log", Slot.BOTTOM_LEFT))) {
            var entry = panel(contributions);
            assertThat(entry.title()).isEqualTo("Event Log");
            assertThat(entry.defaultSlot()).isEqualTo(Slot.BOTTOM_LEFT);
            assertThat(entry.toggleActionId()).isEqualTo(NotificationsFeature.SHOW_HISTORY);
            assertThat(contributions.action(NotificationsFeature.SHOW_HISTORY)).as("the action keeps its title")
                .hasValueSatisfying(action -> assertThat(action.title()).isEqualTo("Notifications: Show History"));
            var view = entry.factory().apply(new PanelSite(UUID.randomUUID(), () -> { }, () -> { }, () -> true));
            assertThat(view.getAccessibleContext().getAccessibleName()).isEqualTo("Event Log");
        }
    }

    @Test void theDefaultLookKeepsNotificationsAtRightTop() {
        var contributions = new Contributions();
        var center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
        try (var feature = new NotificationsFeature(center, contributions, KitTestSupport::context)) {
            assertThat(panel(contributions).title()).isEqualTo("Notifications");
            assertThat(panel(contributions).defaultSlot()).isEqualTo(Slot.RIGHT_TOP);
        }
    }
}
