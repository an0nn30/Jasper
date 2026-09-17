package dev.jasper.app;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The drawer: the full list, always. The column above his head is {@link BuddyColumnPanelTest}. */
class BuddyDeckPanelTest {
    private final BuddyDeck deck = new BuddyDeck();
    private final List<String> activated = new ArrayList<>();
    private int layouts;
    private long now;
    private final BuddyDeckPanel panel = panel();

    private BuddyDeckPanel panel() {
        BuddyDeckPanel created = new BuddyDeckPanel(deck, () -> layouts++);
        created.setClock(() -> now);
        return created;
    }

    private void post(String key, String title) {
        deck.post(new BuddyNotice("terminal", key, BuddyNotice.Kind.TASK, title,
            BuddyNotice.State.DONE, () -> "Finished in 1m 12s", () -> activated.add(title)));
        panel.refresh();
    }

    /** Lays the panel out at its preferred size, the way the window does before showing it. */
    private void layout() {
        panel.setSize(panel.getPreferredSize());
    }

    private Point inCard(int index) {
        Rectangle card = BuddyDeckLayout.expanded(index, BuddyCard.WIDTH, BuddyCard.height(panel), panel.scroll());
        return new Point(BuddyDeckPanel.MOTION_MARGIN + 30, BuddyDeckPanel.MOTION_MARGIN + card.y + 4);
    }

    private Point dismissOf(int index) {
        Rectangle card = BuddyDeckLayout.expanded(index, BuddyCard.WIDTH, BuddyCard.height(panel), panel.scroll());
        Rectangle target = BuddyDeckLayout.dismissTarget(card);
        return new Point(BuddyDeckPanel.MOTION_MARGIN + (int) target.getCenterX(),
            BuddyDeckPanel.MOTION_MARGIN + (int) target.getCenterY());
    }

    @Test void anEmptyDrawerAsksForNoRoomAndPaintsNothing() {
        layout();

        assertThat(panel.getPreferredSize()).isEqualTo(new Dimension(0, 0));
        assertThat(paintDoesNotThrow()).isTrue();
    }

    /** A card that grows on hover must not be clipped by the window it lives in. */
    @Test void thePanelReservesEnoughMarginForTheLargestScale() {
        int overflow = (int) Math.ceil(BuddyCard.WIDTH * (BubbleMotion.HOVER_SCALE - 1f) / 2f);

        assertThat(BuddyDeckPanel.MOTION_MARGIN).isGreaterThanOrEqualTo(overflow);
    }

    @Test void clickingACardRunsItsActionAndClosesTheDrawer() {
        post("a", "one");
        post("b", "two");
        layout();

        assertThat(panel.handleClick(inCard(1))).isFalse();

        assertThat(activated).containsExactly("one");
    }

    @Test void anOrphanedCardReadsButDoesNotClick() {
        post("a", "one");
        deck.orphan("terminal", "a");
        layout();

        assertThat(panel.handleClick(inCard(0))).isTrue();

        assertThat(activated).isEmpty();
        assertThat(deck.size()).as("it stays in the drawer").isEqualTo(1);
    }

    @Test void theDismissTargetRemovesItsOwnCardAndNotTheOneBehindIt() {
        post("a", "older");
        post("b", "newer");
        layout();

        assertThat(panel.handleClick(dismissOf(0))).as("the drawer stays open to dismiss more").isTrue();

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("older");
        assertThat(activated).as("dismissing is not activating").isEmpty();
    }

    @Test void clearAllEmptiesTheDrawerAndClosesIt() {
        post("a", "one");
        post("b", "two");
        post("c", "three");
        layout();
        Rectangle row = BuddyDeckLayout.clearRow(3, BuddyCard.WIDTH, BuddyCard.height(panel), panel.scroll());

        assertThat(panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20,
            BuddyDeckPanel.MOTION_MARGIN + (int) row.getCenterY()))).isFalse();

        assertThat(deck.isEmpty()).isTrue();
    }

    @Test void dismissingTheLastCardClosesTheDrawer() {
        post("a", "only");
        layout();

        assertThat(panel.handleClick(dismissOf(0))).isFalse();

        assertThat(deck.isEmpty()).isTrue();
    }

    @Test void theWheelScrollsTheListAndStopsAtBothEnds() {
        for (int i = 0; i < 40; i++) post("k" + i, "card " + i);
        panel.setMaxListHeight(() -> 400);
        layout();

        panel.handleWheel(-5);
        assertThat(panel.scroll()).as("cannot scroll above the first card").isZero();

        panel.handleWheel(3);
        int scrolled = panel.scroll();
        assertThat(scrolled).isPositive();

        panel.handleWheel(9_999);
        assertThat(panel.scroll()).isGreaterThan(scrolled);
        int end = panel.scroll();
        panel.handleWheel(9_999);
        assertThat(panel.scroll()).as("cannot scroll past the last card").isEqualTo(end);
    }

    @Test void closingForgetsTheScrollSoTheListReopensAtTheTop() {
        for (int i = 0; i < 40; i++) post("k" + i, "card " + i);
        panel.setMaxListHeight(() -> 400);
        layout();
        panel.handleWheel(6);

        panel.reset();

        assertThat(panel.scroll()).isZero();
    }

    @Test void thePointerPicksOutOneCardAtATimeAndLeavingPicksNone() {
        post("a", "one");
        post("b", "two");
        layout();

        panel.handleMove(inCard(1));
        assertThat(panel.hovered()).isEqualTo(1);

        panel.handleMove(inCard(0));
        assertThat(panel.hovered()).isZero();

        panel.handleExit();
        assertThat(panel.hovered()).isEqualTo(-1);
    }

    @Test void aRunningCardTicksWithoutTheDeckBeingRePosted() {
        long[] elapsed = {0};
        deck.post(new BuddyNotice("terminal", "a", BuddyNotice.Kind.TASK, "sleep 600",
            BuddyNotice.State.RUNNING, () -> "Running · " + elapsed[0] + "s", () -> { }));
        panel.refresh();
        layout();

        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Running · 0s");
        elapsed[0] = 72;
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Running · 72s");
        assertThat(paintDoesNotThrow()).isTrue();
    }

    /** A producer's supplier is arbitrary code; a broken one must not take the drawer down. */
    @Test void aDetailSupplierThatThrowsDoesNotStopTheDrawerPainting() {
        deck.post(new BuddyNotice("terminal", "a", BuddyNotice.Kind.TASK, "broken",
            BuddyNotice.State.RUNNING, () -> { throw new IllegalStateException("boom"); }, () -> { }));
        panel.refresh();
        layout();

        assertThat(paintDoesNotThrow()).isTrue();
    }

    @Test void everyCardPaintsWithoutBlowingUp() {
        for (int i = 0; i < 12; i++) post("k" + i, "card " + i);
        layout();
        assertThat(paintDoesNotThrow()).isTrue();

        panel.handleMove(inCard(2));
        assertThat(paintDoesNotThrow()).isTrue();
    }

    private boolean paintDoesNotThrow() {
        Dimension size = panel.getPreferredSize();
        BufferedImage image = new BufferedImage(Math.max(1, size.width), Math.max(1, size.height),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { panel.paint(g); } finally { g.dispose(); }
        return true;
    }
}
