package dev.jasper.app.notificationui;

import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class HistoryPanelTest {
    private static final NotificationGroup GENERAL = new NotificationGroup("jasper.general", "Jasper", "jasper", DisplayMode.CARD);
    private final NotificationCenter center = new NotificationCenter(Runnable::run,
        Clock.fixed(Instant.parse("2026-10-02T14:05:00Z"), ZoneOffset.UTC), () -> Delivery.Presence.FOCUSED);
    private final boolean[] visible = {false};
    private PanelSite site;

    private HistoryPanel panel(boolean focused) {
        center.register(GENERAL);
        site = new PanelSite(UUID.randomUUID(), () -> {}, () -> {}, () -> visible[0]);
        return new HistoryPanel(center, site, focused);
    }

    private void show() { visible[0] = true; site.notifyVisibility(true); }
    private void hide() { visible[0] = false; site.notifyVisibility(false); }

    private UUID post(String title) {
        center.register(GENERAL);
        return center.post(NotificationRequest.of(GENERAL.id(), Severity.INFO, title)).orElseThrow();
    }

    @Test void anEmptyHistorySaysSo() {
        var panel = panel(true);
        show();
        assertThat(panel.showingEmpty()).isTrue();
        assertThat(panel.clearAllButton().isEnabled()).isFalse();
    }

    @Test void showingInAFocusedWindowReadsEverythingAndKeepsItUnderNewUntilHidden() {
        UUID a = post("a");
        UUID b = post("b");
        var panel = panel(true);
        show();
        assertThat(panel.newSection()).containsExactly(b, a);
        assertThat(panel.earlierSection()).isEmpty();
        assertThat(center.unread()).isZero();
        assertThat(panel.row(a).titleBold()).isFalse();
        hide();
        show();
        assertThat(panel.newSection()).isEmpty();
        assertThat(panel.earlierSection()).containsExactly(b, a);
    }

    @Test void anUnfocusedWindowReadsNothingUntilFocused() {
        UUID a = post("a");
        var panel = panel(false);
        show();
        assertThat(center.unread()).isEqualTo(1);
        assertThat(panel.earlierSection()).containsExactly(a);
        assertThat(panel.row(a).titleBold()).isTrue();
        panel.windowFocusChanged(true);
        assertThat(center.unread()).isZero();
        assertThat(panel.newSection()).containsExactly(a);
    }

    @Test void aNotificationArrivingWhileLookingIsReadAndNew() {
        var panel = panel(true);
        show();
        UUID c = post("c");
        assertThat(center.find(c).orElseThrow().read()).isTrue();
        assertThat(panel.newSection()).containsExactly(c);
    }

    @Test void aHiddenPanelReadsNothing() {
        panel(true);
        post("unseen");
        assertThat(center.unread()).isEqualTo(1);
    }

    @Test void clearAllAndRowRemoval() {
        UUID a = post("a");
        UUID b = post("b");
        var panel = panel(true);
        show();
        panel.row(a).removeButton().doClick();
        assertThat(center.history()).extracting(entry -> entry.id()).containsExactly(b);
        assertThat(panel.row(a)).isNull();
        panel.clearAllButton().doClick();
        assertThat(center.history()).isEmpty();
        assertThat(panel.showingEmpty()).isTrue();
    }

    @Test void rowsShowGroupTimeCountAndPlainTitles() {
        UUID id = post("<html><i>x</i>");
        var panel = panel(true);
        show();
        assertThat(panel.row(id).titleText()).isEqualTo("<html><i>x</i>");
        assertThat(panel.row(id).metaText()).isEqualTo("Jasper · 14:05");
        assertThat(panel.row(id).countText()).isEmpty();
        post("<html><i>x</i>");
        assertThat(panel.row(id).countText()).isEqualTo("×2");
    }

    @Test void expiredActionsAreDisabled() {
        center.register(GENERAL);
        var ran = new ArrayList<String>();
        UUID id = center.post(NotificationRequest.of(GENERAL.id(), Severity.INFO, "act")
            .withAction(NotificationAction.of("Fix", () -> ran.add("fix")))).orElseThrow();
        var panel = panel(true);
        show();
        assertThat(panel.row(id).actionButtons()).singleElement().satisfies(button -> assertThat(button.isEnabled()).isTrue());
        panel.row(id).actionButtons().getFirst().doClick();
        assertThat(ran).containsExactly("fix");
        assertThat(panel.row(id).actionButtons().getFirst().isEnabled()).isFalse();
    }

    @Test void aClosedSiteStopsListening() {
        var panel = panel(true);
        show();
        site.notifyClosed();
        UUID late = post("late");
        assertThat(panel.row(late)).isNull();
        assertThat(center.find(late).orElseThrow().read()).isFalse();
    }
}
