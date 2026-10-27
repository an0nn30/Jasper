package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.dock.Slot;
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
        new NotificationsFeature(center, contributions, dev.jasper.app.testsupport.KitTestSupport::context);
        assertThat(center.groups()).extracting(NotificationGroup::id).containsExactly("jasper.general", "jasper.config");
        assertThat(center.mode("jasper.config")).isEqualTo(DisplayMode.STICKY);
        assertThat(panel().defaultSlot()).isEqualTo(Slot.RIGHT_TOP);
        assertThat(panel().toggleActionId()).isEqualTo(NotificationsFeature.SHOW_HISTORY);
        assertThat(contributions.action(NotificationsFeature.SHOW_HISTORY))
            .hasValueSatisfying(action -> assertThat(action.title()).isEqualTo("Notifications: Show History"));
        assertThat(contributions.action(NotificationsFeature.DISMISS_CARDS))
            .hasValueSatisfying(action -> assertThat(action.title()).isEqualTo("Notifications: Dismiss Cards"));
        var requests = new ArrayList<Contributions.PanelRequest>();
        contributions.onPanelRequest(requests::add);
        UUID window = UUID.randomUUID();
        contributions.action(NotificationsFeature.SHOW_HISTORY).orElseThrow().invoke(new Contributions.Invocation(window, Optional.empty()));
        // Plan 12 Rulings 18: the View toggle action is a user gesture.
        assertThat(requests).containsExactly(new Contributions.PanelRequest(window, NotificationsFeature.PANEL_ID, Contributions.PanelRequest.Op.ACTIVATING_TOGGLE));
    }

    @Test void theBadgeCountsUnreadAndTurnsRedForErrors() {
        var feature = new NotificationsFeature(center, contributions, dev.jasper.app.testsupport.KitTestSupport::context);
        assertThat(panel().badge()).isEmpty();
        feature.info("Migrate Config", "Nothing to migrate");
        assertThat(panel().badge()).contains(new PanelBadge(1, PanelBadge.Tone.ACCENT, "1 unread notification"));
        feature.error("Could not open config.toml", "denied");
        assertThat(panel().badge()).contains(new PanelBadge(2, PanelBadge.Tone.ERROR, "2 unread notifications, 1 error"));
        center.markAllRead();
        assertThat(panel().badge()).isEmpty();
    }

    private NotificationsFeature feature() {
        return new NotificationsFeature(center, contributions, dev.jasper.app.testsupport.KitTestSupport::context);
    }

    @Test void everyCardShowsInEveryWindow() {
        var feature = feature();
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.activated(second);
        feature.info("Untargeted", "");
        feature.error("From first", "", first.id());
        assertThat(feature.layer(first.id()).showing()).hasSize(2);
        assertThat(feature.layer(second.id()).showing()).isEqualTo(feature.layer(first.id()).showing());
    }

    @Test void readingACardInOneWindowRemovesItFromAll() {
        var feature = feature();
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.info("One", "");
        feature.info("Two", "");
        var ids = feature.layer(first.id()).showing();
        center.markRead(ids.get(0));
        center.expire(ids.get(1));
        assertThat(feature.layer(first.id()).showing()).isEmpty();
        assertThat(feature.layer(second.id()).showing()).isEmpty();
    }

    @Test void aRepeatKeepsOneCardInEveryWindow() {
        var feature = feature();
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.error("Could not save", "same details", first.id());
        feature.error("Could not save", "same details", second.id());
        assertThat(center.history()).hasSize(1);
        var id = center.history().getFirst().id();
        assertThat(feature.layer(first.id()).showing()).containsExactly(id);
        assertThat(feature.layer(second.id()).showing()).containsExactly(id);
    }

    @Test void aWindowAttachedWhileCardsShowTakesThem() {
        var feature = feature();
        var first = window();
        feature.attach(first);
        feature.info("One", "");
        feature.info("Two", "");
        var third = window();
        feature.attach(third);
        assertThat(feature.layer(first.id()).showing()).hasSize(2);
        assertThat(feature.layer(third.id()).showing()).isEqualTo(feature.layer(first.id()).showing());
    }

    @Test void aWindowAttachedWhileStickyCardsWaitTakesThemInTheSameOrder() {
        var feature = feature();
        var first = window();
        feature.attach(first);
        for (int i = 1; i <= CardLayer.VISIBLE + 2; i++)
            center.post(NotificationRequest.of(NotificationsFeature.CONFIG.id(), Severity.WARNING, "Problem " + i));
        assertThat(feature.layer(first.id()).waiting()).as("sticky cards overflow behind the chip").hasSize(2);
        var second = window();
        feature.attach(second);
        assertThat(feature.layer(second.id()).showing()).isEqualTo(feature.layer(first.id()).showing());
        assertThat(feature.layer(second.id()).waiting()).isEqualTo(feature.layer(first.id()).waiting());
    }

    @Test void closingTheWindowACardCameFromKeepsItElsewhere() {
        var feature = feature();
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.error("Before", "", first.id());
        CardLayer closing = feature.layer(first.id());
        feature.detach(first);
        assertThat(feature.layer(first.id())).isNull();
        assertThat(closing.showing()).isEmpty();
        assertThat(feature.layer(second.id()).showing()).hasSize(1);
        assertThat(center.history()).hasSize(1);
    }

    @Test void aCardThatTimesOutInOneWindowLeavesEveryWindow() {
        var tasks = new ArrayList<Runnable>();
        var feature = new NotificationsFeature(center, contributions, dev.jasper.app.testsupport.KitTestSupport::context,
            new dev.jasper.app.presentation.AppPanels("Notifications", Slot.RIGHT_TOP),
            id -> { },
            (content, raise, timedOut) -> dev.jasper.app.notificationui.CardLayerTestAccess.layer(dev.jasper.app.testsupport.KitTestSupport.context(), center, true,
                () -> { }, raise, timedOut, (delay, task) -> {
                    tasks.add(task);
                    return () -> { };
                }, () -> 0L));
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.info("Slow", "");
        assertThat(feature.layer(first.id()).showing()).hasSize(1);
        assertThat(feature.layer(second.id()).showing()).hasSize(1);
        assertThat(tasks).hasSize(2);
        tasks.getFirst().run();
        assertThat(feature.layer(first.id()).showing()).isEmpty();
        assertThat(feature.layer(second.id()).showing()).isEmpty();
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(entry.read()).isFalse());
    }

    @Test void notificationsFromBeforeTheFirstWindowShowWhenItIsFocused() {
        presence = Delivery.Presence.NO_WINDOW;
        var feature = new NotificationsFeature(center, contributions, dev.jasper.app.testsupport.KitTestSupport::context);
        feature.error("Startup problem", "details");
        center.post(NotificationRequest.of(NotificationsFeature.CONFIG.id(), Severity.WARNING, "config.toml has warnings"));
        presence = Delivery.Presence.FOCUSED;
        var window = window();
        feature.attach(window);
        assertThat(feature.layer(window.id()).showing()).isEmpty();
        feature.activated(window);
        assertThat(feature.layer(window.id()).showing()).hasSize(2);
    }

    @Test void notificationsFromWhileJasperWasAwayReachEachWindowOnceWhenOneIsFocused() {
        presence = Delivery.Presence.NO_WINDOW;
        var feature = feature();
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.error("While away", "details");
        center.post(NotificationRequest.of(NotificationsFeature.CONFIG.id(), Severity.WARNING, "config.toml has warnings"));
        assertThat(feature.layer(first.id()).showing()).isEmpty();
        presence = Delivery.Presence.FOCUSED;
        feature.activated(first);
        var ids = center.history().stream().map(dev.jasper.app.notifications.Notification::id).toList();
        assertThat(feature.layer(first.id()).showing()).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(feature.layer(second.id()).showing()).isEqualTo(feature.layer(first.id()).showing());
        feature.activated(second);
        assertThat(feature.layer(second.id()).showing()).as("shown once: focusing another window replays nothing")
            .isEqualTo(feature.layer(first.id()).showing()).hasSize(2);
    }

    @Test void aCardPostedWhileAWindowOpensWaitsForItsFirstActivation() {
        // The window is attached but the native activation event has not arrived yet: the application reports NO_WINDOW.
        presence = Delivery.Presence.NO_WINDOW;
        var feature = new NotificationsFeature(center, contributions, dev.jasper.app.testsupport.KitTestSupport::context);
        var window = window();
        feature.attach(window);
        feature.error("Could not start /bin/zsh", "No such file");
        assertThat(feature.layer(window.id()).showing()).isEmpty();
        assertThat(center.history()).hasSize(1);
        presence = Delivery.Presence.FOCUSED;
        feature.activated(window);
        assertThat(feature.layer(window.id()).showing()).hasSize(1);
    }

    @Test void anErrorPostedWhileResidentAndIdleIsHistoryOnlyButAConfigProblemWaitsForTheNextWindow() {
        presence = Delivery.Presence.IDLE;
        var feature = new NotificationsFeature(center, contributions, dev.jasper.app.testsupport.KitTestSupport::context);
        feature.error("Background plugin failed", "details");
        UUID configProblem = center.post(NotificationRequest.of(NotificationsFeature.CONFIG.id(), Severity.WARNING,
            "config.toml has warnings")).orElseThrow();
        assertThat(center.history()).hasSize(2);
        presence = Delivery.Presence.NO_WINDOW;
        var window = window();
        feature.attach(window);
        assertThat(feature.layer(window.id()).showing()).isEmpty();
        presence = Delivery.Presence.FOCUSED;
        feature.activated(window);
        assertThat(feature.layer(window.id()).showing())
            .as("only the sticky one waited; the ordinary card is in the history").containsExactly(configProblem);
    }

    @Test void dismissCardsClosesTheInvokingWindowsCardsAndReadsThem() {
        var feature = new NotificationsFeature(center, contributions, dev.jasper.app.testsupport.KitTestSupport::context);
        var window = window();
        feature.attach(window);
        feature.info("One", "");
        feature.info("Two", "");
        contributions.action(NotificationsFeature.DISMISS_CARDS).orElseThrow()
            .invoke(new Contributions.Invocation(window.id(), Optional.empty()));
        assertThat(feature.layer(window.id()).showing()).isEmpty();
        assertThat(center.unread()).isZero();
    }

    @Test void dismissCardsFromOneWindowClearsEveryWindow() {
        var feature = feature();
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.info("One", "");
        center.post(NotificationRequest.of(NotificationsFeature.CONFIG.id(), Severity.WARNING, "Sticky"));
        contributions.action(NotificationsFeature.DISMISS_CARDS).orElseThrow()
            .invoke(new Contributions.Invocation(first.id(), Optional.empty()));
        assertThat(feature.layer(first.id()).showing()).isEmpty();
        assertThat(feature.layer(second.id()).showing()).isEmpty();
        assertThat(center.unread()).isZero();
    }

    @Test void dismissCardsFromAWindowWithoutALayerClosesEveryCard() {
        // Dismiss Cards run from a window without a layer closes every window's cards.
        var feature = new NotificationsFeature(center, contributions, dev.jasper.app.testsupport.KitTestSupport::context);
        var window = window();
        feature.attach(window);
        feature.info("One", "");
        contributions.action(NotificationsFeature.DISMISS_CARDS).orElseThrow()
            .invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        assertThat(feature.layer(window.id()).showing()).isEmpty();
        assertThat(center.unread()).isZero();
    }

    @Test void errorsWithoutDetailsStillPost() {
        var feature = new NotificationsFeature(center, contributions, dev.jasper.app.testsupport.KitTestSupport::context);
        feature.error("Could not open config.toml", null);
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().title()).isEqualTo("Could not open config.toml");
            assertThat(entry.request().content()).isEmpty();
            assertThat(entry.request().severity()).isEqualTo(Severity.ERROR);
        });
    }

    @Test void showHistoryShowsThePanelInAnAttachedWindowOnly() {
        var feature = new NotificationsFeature(center, contributions, dev.jasper.app.testsupport.KitTestSupport::context);
        var requests = new ArrayList<Contributions.PanelRequest>();
        contributions.onPanelRequest(requests::add);
        var window = window();
        feature.attach(window);
        feature.showHistory(window.id());
        feature.showHistory(UUID.randomUUID());
        assertThat(requests).containsExactly(
            new Contributions.PanelRequest(window.id(), NotificationsFeature.PANEL_ID, Contributions.PanelRequest.Op.SHOW));
    }
}
