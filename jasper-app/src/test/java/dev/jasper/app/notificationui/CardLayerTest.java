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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.UIManager;
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

    /** Paints the layer at the given device scale (1 = a plain screen, 2 = Retina) into a transparent image. */
    private static BufferedImage paint(CardLayer layer, double scale) {
        var image = new BufferedImage((int) Math.ceil(layer.getWidth() * scale), (int) Math.ceil(layer.getHeight() * scale),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(scale, scale);
            layer.paint(g);
        } finally { g.dispose(); }
        return image;
    }

    private static int alpha(BufferedImage image, int x, int y) { return image.getRGB(x, y) >>> 24; }

    /** A layer larger than its stack, so there is room beyond the shadow's margin; the first card is the one returned. */
    private CardLayer showingTwo(UUID[] ids) {
        var layer = layer(true);
        var a = post(CARDS, "a");
        var b = post(CARDS, "b");
        layer.show(a);
        layer.show(b);
        ids[0] = a.id();
        ids[1] = b.id();
        layer.setSize(layer.getPreferredSize().width + UIScale.scale(60), layer.getPreferredSize().height + UIScale.scale(60));
        layer.doLayout();
        return layer;
    }

    @Test void eachCardsShadowIsRenderedOnceNoMatterHowOftenTheLayerPaints() {
        var ids = new UUID[2];
        var layer = showingTwo(ids);
        assertThat(layer.shadowRenders()).isZero();
        paint(layer, 1);
        assertThat(layer.shadowRenders()).as("one image per card").isEqualTo(2);
        paint(layer, 1);
        paint(layer, 1);
        assertThat(layer.shadowRenders()).as("later paints draw the cached images").isEqualTo(2);
    }

    @Test void aShadowIsRenderedAgainWhenItsCardChangesSizeOrItsColourOrTheScaleChanges() {
        var ids = new UUID[2];
        var layer = showingTwo(ids);
        paint(layer, 1);
        assertThat(layer.shadowRenders()).isEqualTo(2);
        var card = layer.card(ids[1]);
        card.setSize(card.getWidth(), card.getHeight() + 10);
        paint(layer, 1);
        assertThat(layer.shadowRenders()).as("only the card that changed size").isEqualTo(3);
        paint(layer, 1);
        assertThat(layer.shadowRenders()).isEqualTo(3);
        paint(layer, 2);
        assertThat(layer.shadowRenders()).as("a crisp image for the new device scale, for both cards").isEqualTo(5);
        paint(layer, 2);
        assertThat(layer.shadowRenders()).isEqualTo(5);
        Color before = UIManager.getColor("Popup.dropShadowColor");
        try {
            UIManager.put("Popup.dropShadowColor", new Color(0x203040));
            paint(layer, 2);
            assertThat(layer.shadowRenders()).as("the shadow colour is part of the key").isEqualTo(7);
        } finally { UIManager.put("Popup.dropShadowColor", before); }
    }

    @Test void theShadowSurroundsACardAndStopsAtTheMargin() {
        var ids = new UUID[2];
        var layer = showingTwo(ids);
        int margin = UIScale.scale(CardLayer.MARGIN);
        var top = layer.card(ids[0]);
        int middle = top.getY() + top.getHeight() / 2;
        for (double scale : new double[] {1, 2}) {
            var image = paint(layer, scale);
            int left = top.getX() - 1, right = top.getX() + top.getWidth();
            assertThat(alpha(image, (int) (left * scale), (int) (middle * scale))).as("beside the left edge at %sx", scale).isPositive();
            assertThat(alpha(image, (int) (right * scale), (int) (middle * scale))).as("beside the right edge at %sx", scale).isPositive();
            assertThat(alpha(image, (int) ((right + margin + 2) * scale), (int) (middle * scale)))
                .as("beyond the margin at %sx", scale).isZero();
            assertThat(alpha(image, (int) ((top.getX() + top.getWidth() / 2) * scale), (int) ((top.getY() - margin - 2) * scale)))
                .as("above the margin at %sx", scale).isZero();
        }
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
