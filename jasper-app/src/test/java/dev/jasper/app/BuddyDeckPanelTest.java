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
        deck.post(new BuddyNotice("terminal", key, title, BuddyNotice.State.DONE,
            () -> "Finished in 1m 12s", () -> activated.add(title)));
        panel.arrived();
    }

    /** Lays the panel out at its preferred size, the way the window does before showing it. */
    private void layout() {
        Dimension size = panel.getPreferredSize();
        panel.setSize(size);
    }

    private void expand() {
        panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20, BuddyDeckPanel.MOTION_MARGIN + 10));
        layout();
    }

    private Point inCard(int index) {
        Rectangle card = BuddyDeckLayout.expanded(index, BuddyDeckPanel.CARD_WIDTH, panel.cardHeight(), panel.scroll());
        return new Point(BuddyDeckPanel.MOTION_MARGIN + 30, BuddyDeckPanel.MOTION_MARGIN + card.y + 4);
    }

    private Point dismissOf(int index) {
        Rectangle card = BuddyDeckLayout.expanded(index, BuddyDeckPanel.CARD_WIDTH, panel.cardHeight(), panel.scroll());
        Rectangle target = BuddyDeckLayout.dismissTarget(card);
        return new Point(BuddyDeckPanel.MOTION_MARGIN + (int) target.getCenterX(),
            BuddyDeckPanel.MOTION_MARGIN + (int) target.getCenterY());
    }

    @Test void anEmptyDrawerAsksForNoRoomAndPaintsNothing() {
        layout();

        assertThat(panel.getPreferredSize()).isEqualTo(new Dimension(0, 0));
        assertThat(paintDoesNotThrow()).isTrue();
    }

    @Test void theCollapsedDeckIsAsTallAsItsStackPlusRoomToGrow() {
        post("a", "one");
        post("b", "two");
        layout();

        assertThat(panel.expanded()).isFalse();
        assertThat(panel.getPreferredSize().height).isEqualTo(
            BuddyDeckLayout.collapsedHeight(2, panel.cardHeight()) + 2 * BuddyDeckPanel.MOTION_MARGIN);
    }

    /** A card that grows on hover must not be clipped by the window it lives in. */
    @Test void thePanelReservesEnoughMarginForTheLargestScale() {
        float widest = Math.max(BubbleMotion.IN_PEAK, BubbleMotion.HOVER_SCALE);
        int overflow = (int) Math.ceil(BuddyDeckPanel.CARD_WIDTH * (widest - 1f) / 2f);

        assertThat(BuddyDeckPanel.MOTION_MARGIN).isGreaterThanOrEqualTo(overflow);
    }

    @Test void clickingTheCollapsedDeckExpandsItInsteadOfActivatingTheTopCard() {
        post("a", "one");
        post("b", "two");
        layout();

        assertThat(panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20,
            BuddyDeckPanel.MOTION_MARGIN + 10))).isTrue();

        assertThat(panel.expanded()).isTrue();
        assertThat(activated).isEmpty();
    }

    @Test void clickingACardInTheExpandedListRunsItsActionAndCollapses() {
        post("a", "one");
        post("b", "two");
        layout();
        expand();

        panel.handleClick(inCard(1));

        assertThat(activated).containsExactly("one");
        assertThat(panel.expanded()).isFalse();
    }

    @Test void anOrphanedCardReadsButDoesNotClick() {
        post("a", "one");
        deck.orphan("terminal", "a", "Stopped after 1m 12s");
        layout();
        expand();

        panel.handleClick(inCard(0));

        assertThat(activated).isEmpty();
        assertThat(deck.size()).as("it stays in the drawer").isEqualTo(1);
    }

    @Test void theDismissTargetRemovesItsOwnCardAndNotTheOneBehindIt() {
        post("a", "older");
        post("b", "newer");
        layout();
        expand();

        panel.handleClick(dismissOf(0));

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("older");
        assertThat(activated).as("dismissing is not activating").isEmpty();
        assertThat(panel.expanded()).as("the list stays open to dismiss more").isTrue();
    }

    @Test void clearAllEmptiesTheDrawerAndClosesIt() {
        post("a", "one");
        post("b", "two");
        post("c", "three");
        layout();
        expand();
        Rectangle row = BuddyDeckLayout.clearRow(3, BuddyDeckPanel.CARD_WIDTH, panel.cardHeight(), panel.scroll());

        assertThat(panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20,
            BuddyDeckPanel.MOTION_MARGIN + (int) row.getCenterY()))).isFalse();

        assertThat(deck.isEmpty()).isTrue();
    }

    @Test void dismissingTheLastCardClosesTheDrawer() {
        post("a", "only");
        layout();
        expand();

        assertThat(panel.handleClick(dismissOf(0))).isFalse();

        assertThat(deck.isEmpty()).isTrue();
    }

    @Test void theWheelScrollsTheExpandedListAndStopsAtBothEnds() {
        for (int i = 0; i < 40; i++) post("k" + i, "card " + i);
        panel.setMaxListHeight(() -> 400);
        layout();
        expand();

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

    @Test void collapsingForgetsTheScrollSoTheListReopensAtTheTop() {
        for (int i = 0; i < 40; i++) post("k" + i, "card " + i);
        panel.setMaxListHeight(() -> 400);
        layout();
        expand();
        panel.handleWheel(6);

        panel.collapse();

        assertThat(panel.scroll()).isZero();
    }

    @Test void thePointerPicksOutOneCardAtATimeAndLeavingPicksNone() {
        post("a", "one");
        post("b", "two");
        layout();
        expand();

        panel.handleMove(inCard(1));
        assertThat(panel.hovered()).isEqualTo(1);

        panel.handleMove(inCard(0));
        assertThat(panel.hovered()).isZero();

        panel.handleExit();
        assertThat(panel.hovered()).isEqualTo(-1);
        assertThat(panel.expanded()).as("leaving closes the list").isFalse();
    }

    @Test void aRunningCardTicksWithoutTheDeckBeingRePosted() {
        long[] elapsed = {0};
        deck.post(new BuddyNotice("terminal", "a", "sleep 600", BuddyNotice.State.ACTIVE,
            () -> "Running · " + elapsed[0] + "s", () -> { }));
        panel.arrived();
        layout();

        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Running · 0s");
        elapsed[0] = 72;
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Running · 72s");
        assertThat(paintDoesNotThrow()).isTrue();
    }

    @Test void aNewNoticeRestartsTheArrivalBounce() {
        post("a", "one");
        now = BubbleMotion.IN_NANOS * 4;
        layout();
        assertThat(panel.topScale()).isEqualTo(1f);

        post("b", "two");

        assertThat(panel.topScale()).isEqualTo(BubbleMotion.IN_FROM);
    }

    /** A producer's supplier is arbitrary code; a broken one must not take the drawer down. */
    @Test void aDetailSupplierThatThrowsDoesNotStopTheDrawerPainting() {
        deck.post(new BuddyNotice("terminal", "a", "broken", BuddyNotice.State.ACTIVE,
            () -> { throw new IllegalStateException("boom"); }, () -> { }));
        panel.arrived();
        layout();

        assertThat(paintDoesNotThrow()).isTrue();
    }

    @Test void everyCardPaintsCollapsedAndExpandedWithoutBlowingUp() {
        for (int i = 0; i < 12; i++) post("k" + i, "card " + i);
        layout();
        assertThat(paintDoesNotThrow()).isTrue();

        expand();
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

    /**
     * The top card's fill is translucent, so anything drawn on the cards behind reads straight
     * through it. What is in the stack must therefore not change what the top card looks like.
     */
    @Test void whatIsBehindTheTopCardNeverShowsThroughIt() {
        post("a", "a very long buried title that would ghost through the card above it");
        post("b", "another long buried title, also quite capable of bleeding through");
        post("c", "top");
        layout();
        BufferedImage withLongTitles = paint();

        deck.clear();
        post("a", "x");
        post("b", "y");
        post("c", "top");
        layout();
        BufferedImage withShortTitles = paint();

        Rectangle top = BuddyDeckLayout.collapsed(0, BuddyDeckPanel.CARD_WIDTH, panel.cardHeight());
        for (int y = top.y + 2; y < top.y + top.height - 2; y++) {
            for (int x = top.x + 2; x < top.x + top.width - 2; x++) {
                assertThat(withLongTitles.getRGB(x + BuddyDeckPanel.MOTION_MARGIN, y + BuddyDeckPanel.MOTION_MARGIN))
                    .as("pixel %d,%d inside the top card", x, y)
                    .isEqualTo(withShortTitles.getRGB(x + BuddyDeckPanel.MOTION_MARGIN, y + BuddyDeckPanel.MOTION_MARGIN));
            }
        }
    }

    private BufferedImage paint() {
        Dimension size = panel.getPreferredSize();
        BufferedImage image = new BufferedImage(Math.max(1, size.width), Math.max(1, size.height),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { panel.paint(g); } finally { g.dispose(); }
        return image;
    }
}
