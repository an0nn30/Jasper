package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class CardLayerTest {
    private static final NotificationGroup CARDS = new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD);
    private static final NotificationGroup STICKY = new NotificationGroup("test.sticky", "Sticky", "jasper", DisplayMode.STICKY);
    private record Scheduled(Duration delay, Runnable task, boolean[] cancelled) {}

    private final NotificationCenter center = new NotificationCenter(Runnable::run,
        Clock.fixed(Instant.parse("2026-10-02T14:05:00Z"), ZoneOffset.UTC), () -> Delivery.Presence.FOCUSED);
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final List<String> opened = new ArrayList<>();
    private long now;

    private CardLayer layer(boolean focused) {
        center.register(CARDS);
        center.register(STICKY);
        return new CardLayer(center, focused, () -> opened.add("history"), (delay, task) -> {
            boolean[] cancelled = {false};
            scheduled.add(new Scheduled(delay, task, cancelled));
            return () -> cancelled[0] = true;
        }, () -> now);
    }

    private List<Scheduled> live() { return scheduled.stream().filter(item -> !item.cancelled()[0]).toList(); }

    private Notification post(NotificationGroup group, String title) {
        return center.find(center.post(NotificationRequest.of(group.id(), Severity.INFO, title)).orElseThrow()).orElseThrow();
    }

    @Test void aCardShowsInsideATransparentUnfocusableLayer() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        layer.show(a);
        assertThat(layer.showing()).containsExactly(a.id());
        assertThat(layer.isVisible()).isTrue();
        assertThat(layer.getMouseListeners()).as("clicks beside a card reach the terminal").isEmpty();
        assertThat(layer.isFocusable()).isFalse();
        int margin = UIScale.scale(CardLayer.MARGIN);
        assertThat(layer.getPreferredSize().width).isEqualTo(UIScale.scale(NotificationCard.WIDTH) + 2 * margin);
        assertThat(layer.getPreferredSize().height).isEqualTo(layer.card(a.id()).getPreferredSize().height + 2 * margin);
    }

    @Test void theNewestCardSitsAtTheBottom() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        var b = post(CARDS, "b");
        layer.show(a);
        layer.show(b);
        layer.setSize(layer.getPreferredSize());
        layer.doLayout();
        assertThat(layer.card(b.id()).getY()).isGreaterThan(layer.card(a.id()).getY());
        assertThat(layer.card(b.id()).getY() + layer.card(b.id()).getHeight()).isEqualTo(layer.getHeight() - UIScale.scale(CardLayer.MARGIN));
    }

    @Test void aFourthCardPushesOutTheOldestAutoHidingOne() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        var s = post(STICKY, "s");
        var b = post(CARDS, "b");
        var c = post(CARDS, "c");
        for (var entry : List.of(a, s, b, c)) layer.show(entry);
        assertThat(layer.showing()).containsExactly(s.id(), b.id(), c.id());
        assertThat(center.find(a.id()).orElseThrow().read()).as("it stays unread in history").isFalse();
    }

    @Test void aFullStackOfStickyCardsQueuesTheRestBehindAChip() {
        var layer = layer(true);
        var s1 = post(STICKY, "s1");
        var s2 = post(STICKY, "s2");
        var s3 = post(STICKY, "s3");
        var s4 = post(STICKY, "s4");
        for (var entry : List.of(s1, s2, s3, s4)) layer.show(entry);
        assertThat(layer.showing()).containsExactly(s1.id(), s2.id(), s3.id());
        assertThat(layer.waitingIds()).containsExactly(s4.id());
        assertThat(layer.moreButton().isVisible()).isTrue();
        assertThat(layer.moreButton().getText()).isEqualTo("+1 more");
        layer.moreButton().doClick();
        assertThat(opened).containsExactly("history");
        center.markRead(s1.id());
        assertThat(layer.showing()).containsExactly(s2.id(), s3.id(), s4.id());
        assertThat(layer.moreButton().isVisible()).isFalse();
    }

    @Test void autoHideCountsTenFocusedSeconds() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        layer.show(a);
        assertThat(live()).singleElement().satisfies(item -> assertThat(item.delay()).isEqualTo(CardLayer.AUTO_HIDE));
        live().getFirst().task().run();
        assertThat(layer.showing()).isEmpty();
        assertThat(center.find(a.id()).orElseThrow().read()).isFalse();
    }

    @Test void theCountdownPausesWhileUnfocusedOrHovered() {
        var layer = layer(false);
        var a = post(CARDS, "a");
        layer.show(a);
        assertThat(live()).isEmpty();
        layer.windowFocusChanged(true);
        assertThat(live()).singleElement().satisfies(item -> assertThat(item.delay()).isEqualTo(Duration.ofSeconds(10)));
        now += Duration.ofSeconds(4).toNanos();
        layer.windowFocusChanged(false);
        assertThat(live()).isEmpty();
        layer.windowFocusChanged(true);
        assertThat(live()).singleElement().satisfies(item -> assertThat(item.delay()).isEqualTo(Duration.ofSeconds(6)));
        now += Duration.ofSeconds(1).toNanos();
        layer.card(a.id()).setHovered(true);
        assertThat(live()).isEmpty();
        layer.card(a.id()).setHovered(false);
        assertThat(live()).singleElement().satisfies(item -> assertThat(item.delay()).isEqualTo(Duration.ofSeconds(5)));
    }

    @Test void stickyCardsNeverCountDown() {
        var layer = layer(true);
        layer.show(post(STICKY, "s"));
        assertThat(live()).isEmpty();
    }

    @Test void cardsCloseWhenReadExpiredRemovedOrCleared() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        var b = post(CARDS, "b");
        var c = post(CARDS, "c");
        for (var entry : List.of(a, b, c)) layer.show(entry);
        center.markRead(a.id());
        assertThat(layer.showing()).containsExactly(b.id(), c.id());
        center.expire(b.id());
        assertThat(layer.showing()).containsExactly(c.id());
        center.remove(c.id());
        assertThat(layer.showing()).isEmpty();
        layer.show(post(CARDS, "d"));
        layer.show(post(CARDS, "e"));
        center.clear();
        assertThat(layer.showing()).isEmpty();
        assertThat(layer.isVisible()).isFalse();
    }

    @Test void aRepeatRefreshesTheSameCardAndRestartsItsCountdown() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        layer.show(a);
        now += Duration.ofSeconds(7).toNanos();
        var again = post(CARDS, "a");
        assertThat(again.id()).isEqualTo(a.id());
        layer.show(again);
        assertThat(layer.showing()).containsExactly(a.id());
        assertThat(layer.card(a.id()).countText()).isEqualTo("×2");
        assertThat(live()).singleElement().satisfies(item -> assertThat(item.delay()).isEqualTo(CardLayer.AUTO_HIDE));
    }

    @Test void dismissAllReadsEveryCardShowingOrWaiting() {
        var layer = layer(true);
        List<UUID> ids = new ArrayList<>();
        for (String title : List.of("s1", "s2", "s3", "s4")) {
            var entry = post(STICKY, title);
            ids.add(entry.id());
            layer.show(entry);
        }
        layer.dismissAll();
        assertThat(layer.showing()).isEmpty();
        assertThat(layer.waitingIds()).isEmpty();
        assertThat(ids).allSatisfy(id -> assertThat(center.find(id).orElseThrow().read()).isTrue());
    }

    @Test void aCardForAReadEntryNeverShows() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        center.markRead(a.id());
        layer.show(a);
        assertThat(layer.showing()).isEmpty();
    }

    @Test void aClosedLayerIgnoresTheCenter() {
        var layer = layer(true);
        layer.show(post(CARDS, "a"));
        layer.close();
        assertThat(layer.showing()).isEmpty();
        assertThat(live()).isEmpty();
        layer.show(post(CARDS, "b"));
        assertThat(layer.showing()).isEmpty();
    }
}
