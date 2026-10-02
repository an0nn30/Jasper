package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.notificationui.CardLayer;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.WindowContent;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class NotificationsFeatureTest {
    private Delivery.Presence presence = Delivery.Presence.FOCUSED;
    private final NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> presence);
    private final Contributions contributions = new Contributions();

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    private static WindowContent window() { return DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>())); }

    private PanelEntry panel() {
        return contributions.panels().stream().filter(entry -> entry.id().equals(NotificationsFeature.PANEL_ID)).findFirst().orElseThrow();
    }

    @Test void registersTheGroupsThePanelAndItsActions() {
        new NotificationsFeature(center, contributions);
        assertThat(center.groups()).extracting(NotificationGroup::id).containsExactly("jasper.general", "jasper.config");
        assertThat(center.mode("jasper.config")).isEqualTo(DisplayMode.STICKY);
        assertThat(panel().defaultRegion()).isEqualTo(PanelRegion.RIGHT);
        assertThat(panel().toggleActionId()).isEqualTo(NotificationsFeature.SHOW_HISTORY);
        assertThat(contributions.action(NotificationsFeature.SHOW_HISTORY))
            .hasValueSatisfying(action -> assertThat(action.title()).isEqualTo("Notifications: Show History"));
        assertThat(contributions.action(NotificationsFeature.DISMISS_CARDS))
            .hasValueSatisfying(action -> assertThat(action.title()).isEqualTo("Notifications: Dismiss Cards"));
        var requests = new ArrayList<Contributions.PanelRequest>();
        contributions.onPanelRequest(requests::add);
        UUID window = UUID.randomUUID();
        contributions.action(NotificationsFeature.SHOW_HISTORY).orElseThrow().invoke(new Contributions.Invocation(window, Optional.empty()));
        assertThat(requests).containsExactly(new Contributions.PanelRequest(window, NotificationsFeature.PANEL_ID, Contributions.PanelRequest.Op.TOGGLE));
    }

    @Test void theBadgeCountsUnreadAndTurnsRedForErrors() {
        var feature = new NotificationsFeature(center, contributions);
        assertThat(panel().badge()).isEmpty();
        feature.info("Migrate Config", "Nothing to migrate");
        assertThat(panel().badge()).contains(new PanelBadge(1, PanelBadge.Tone.ACCENT, "1 unread notification"));
        feature.error("Could not open config.toml", "denied");
        assertThat(panel().badge()).contains(new PanelBadge(2, PanelBadge.Tone.ERROR, "2 unread notifications, 1 error"));
        center.markAllRead();
        assertThat(panel().badge()).isEmpty();
    }

    @Test void cardsGoToTheirTargetOrTheWindowUsedLast() {
        var feature = new NotificationsFeature(center, contributions);
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.activated(second);
        feature.info("Last used", "");
        assertThat(feature.layer(second.id()).showing()).hasSize(1);
        assertThat(feature.layer(first.id()).showing()).isEmpty();
        center.post(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.INFO, "Targeted").withTarget(first.id()));
        assertThat(feature.layer(first.id()).showing()).hasSize(1);
    }

    @Test void aWindowsOwnMessagesShowInThatWindowEvenWhenAnotherWasUsedLast() {
        var feature = new NotificationsFeature(center, contributions);
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.activated(second);
        feature.error("From first", "", first.id());
        assertThat(feature.layer(first.id()).showing()).hasSize(1);
        assertThat(feature.layer(second.id()).showing()).isEmpty();
        feature.info("Also from first", "", first.id());
        assertThat(feature.layer(first.id()).showing()).hasSize(2);
        assertThat(feature.layer(second.id()).showing()).isEmpty();
    }

    @Test void aClosedWindowsCardsGoAndLaterOnesFindAnotherWindow() {
        var feature = new NotificationsFeature(center, contributions);
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.activated(first);
        feature.error("Before", "");
        CardLayer closing = feature.layer(first.id());
        assertThat(closing.showing()).hasSize(1);
        feature.detach(first);
        assertThat(feature.layer(first.id())).isNull();
        assertThat(closing.showing()).isEmpty();
        feature.error("After", "");
        assertThat(feature.layer(second.id()).showing()).hasSize(1);
        assertThat(center.history()).hasSize(2);
    }

    @Test void notificationsFromBeforeTheFirstWindowShowWhenItIsFocused() {
        presence = Delivery.Presence.NO_WINDOW;
        var feature = new NotificationsFeature(center, contributions);
        feature.error("Startup problem", "details");
        center.post(NotificationRequest.of(NotificationsFeature.CONFIG.id(), Severity.WARNING, "config.toml has warnings"));
        presence = Delivery.Presence.FOCUSED;
        var window = window();
        feature.attach(window);
        assertThat(feature.layer(window.id()).showing()).isEmpty();
        feature.activated(window);
        assertThat(feature.layer(window.id()).showing()).hasSize(2);
    }

    @Test void dismissCardsClosesTheInvokingWindowsCardsAndReadsThem() {
        var feature = new NotificationsFeature(center, contributions);
        var window = window();
        feature.attach(window);
        feature.info("One", "");
        feature.info("Two", "");
        contributions.action(NotificationsFeature.DISMISS_CARDS).orElseThrow()
            .invoke(new Contributions.Invocation(window.id(), Optional.empty()));
        assertThat(feature.layer(window.id()).showing()).isEmpty();
        assertThat(center.unread()).isZero();
    }

    @Test void errorsWithoutDetailsStillPost() {
        var feature = new NotificationsFeature(center, contributions);
        feature.error("Could not open config.toml", null);
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().title()).isEqualTo("Could not open config.toml");
            assertThat(entry.request().content()).isEmpty();
            assertThat(entry.request().severity()).isEqualTo(Severity.ERROR);
        });
    }
}
