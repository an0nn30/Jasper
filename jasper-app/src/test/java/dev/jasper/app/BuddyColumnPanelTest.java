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

class BuddyColumnPanelTest {
    private final BuddyDeck deck = new BuddyDeck();
    private final List<String> activated = new ArrayList<>();
    private int drawerOpened;
    private long now;
    private final BuddyColumnPanel panel = create();

    private BuddyColumnPanel create() {
        BuddyColumnPanel made = new BuddyColumnPanel(deck, () -> { }, () -> drawerOpened++);
        made.setClock(() -> now);
        return made;
    }

    private void post(String key, String title, BuddyNotice.State state) {
        deck.post(new BuddyNotice("terminal", key, BuddyNotice.Kind.TASK, title, state,
            () -> "detail", () -> activated.add(title)));
        panel.arrived();
    }

    private void layout() { panel.setSize(panel.getPreferredSize()); }

    private Point inBubble(int index) {
        int count = deck.column().size();
        Rectangle card = BuddyDeckLayout.column(index, count, BuddyCard.WIDTH,
            BuddyCard.height(panel), panel.below());
        return new Point(BuddyColumnPanel.MARGIN + 30, BuddyColumnPanel.MARGIN + card.y + 4);
    }

    @Test void anEmptyColumnAsksForNoRoomAtAll() {
        layout();

        assertThat(panel.getPreferredSize()).isEqualTo(new Dimension(0, 0));
    }

    /** The resting state: he has nothing to think about, so there is nothing above his head. */
    @Test void aColumnOfOnlyAcknowledgedFinishedThingsIsEmpty() {
        post("a", "one", BuddyNotice.State.DONE);
        deck.acknowledge("terminal", "a");
        layout();

        assertThat(panel.getPreferredSize()).isEqualTo(new Dimension(0, 0));
        assertThat(deck.notices()).as("still in the drawer").hasSize(1);
    }

    @Test void theColumnIsAsTallAsItsBubblesAndTail() {
        post("a", "one", BuddyNotice.State.RUNNING);
        post("b", "two", BuddyNotice.State.RUNNING);
        layout();

        assertThat(panel.getPreferredSize().height).isEqualTo(
            BuddyDeckLayout.columnHeight(2, BuddyCard.height(panel)) + 2 * BuddyColumnPanel.MARGIN);
    }

    @Test void pastThreeTheColumnStopsGrowingAndTheNewestCarriesTheCount() {
        for (int i = 0; i < 9; i++) post("k" + i, "card " + i, BuddyNotice.State.RUNNING);
        layout();

        assertThat(panel.getPreferredSize().height).isEqualTo(
            BuddyDeckLayout.columnHeight(3, BuddyCard.height(panel)) + 2 * BuddyColumnPanel.MARGIN);
        assertThat(paintDoesNotThrow()).isTrue();
    }

    @Test void clickingABubbleRunsItsActionAndSaysTheColumnShouldGo() {
        post("a", "one", BuddyNotice.State.DONE);
        post("b", "two", BuddyNotice.State.DONE);
        layout();

        assertThat(panel.handleClick(inBubble(0))).isFalse();

        assertThat(activated).containsExactly("two");
        assertThat(drawerOpened).isZero();
    }

    @Test void clickingAnOrphanDoesNothingAtAll() {
        post("a", "one", BuddyNotice.State.DONE);
        deck.orphan("terminal", "a");
        layout();

        assertThat(panel.handleClick(inBubble(0))).isTrue();

        assertThat(activated).isEmpty();
        assertThat(drawerOpened).isZero();
    }

    /** The gap and the tail are not bubbles; a click there opens the drawer instead. */
    @Test void clickingTheTailOpensTheDrawer() {
        post("a", "one", BuddyNotice.State.RUNNING);
        layout();
        int height = BuddyDeckLayout.columnHeight(1, BuddyCard.height(panel));

        panel.handleClick(new Point(BuddyColumnPanel.MARGIN + 30, BuddyColumnPanel.MARGIN + height - 3));

        assertThat(drawerOpened).isEqualTo(1);
        assertThat(activated).isEmpty();
    }

    @Test void thePointerPicksOutOneBubbleAndLeavingPicksNone() {
        post("a", "one", BuddyNotice.State.RUNNING);
        post("b", "two", BuddyNotice.State.RUNNING);
        layout();

        panel.handleMove(inBubble(1));
        assertThat(panel.hovered()).isEqualTo(1);

        panel.handleExit();
        assertThat(panel.hovered()).isEqualTo(-1);
    }

    @Test void flippingBelowHimReordersWhichBubbleIsWhere() {
        post("a", "older", BuddyNotice.State.RUNNING);
        post("b", "newer", BuddyNotice.State.RUNNING);
        layout();
        Point newestAbove = inBubble(0);

        panel.setBelow(true);
        layout();

        assertThat(panel.below()).isTrue();
        assertThat(inBubble(0)).as("the newest moved to the other end").isNotEqualTo(newestAbove);
        assertThat(panel.handleClick(inBubble(0))).isFalse();
        assertThat(activated).containsExactly("newer");
    }

    @Test void aNewNoticeRestartsTheArrivalBounce() {
        post("a", "one", BuddyNotice.State.RUNNING);
        now = BubbleMotion.IN_NANOS * 4;
        assertThat(panel.topScale()).isEqualTo(1f);

        post("b", "two", BuddyNotice.State.RUNNING);

        assertThat(panel.topScale()).isEqualTo(BubbleMotion.IN_FROM);
    }

    @Test void everyArrangementPaintsWithoutBlowingUp() {
        post("a", "running", BuddyNotice.State.RUNNING);
        post("b", "waiting", BuddyNotice.State.NEEDS_INPUT);
        post("c", "failed", BuddyNotice.State.FAILED);
        layout();
        assertThat(paintDoesNotThrow()).isTrue();

        panel.handleMove(inBubble(1));
        assertThat(paintDoesNotThrow()).isTrue();

        panel.setBelow(true);
        layout();
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
     * The bug this replaced: nothing repainted the column, so it painted one frame at elapsed zero —
     * scale 0.6 and opacity 0 — and the bubble was invisible until an unrelated repaint happened.
     * The panel has to be able to say it is still moving, or the window cannot drive the frames.
     */
    @Test void anArrivingBubbleKeepsAskingForFramesUntilItHasSettled() {
        assertThat(panel.animating()).as("an idle column is not mid-animation").isFalse();

        post("a", "one", BuddyNotice.State.RUNNING);

        assertThat(panel.animating()).isTrue();
        now = BubbleMotion.IN_NANOS / 2;
        assertThat(panel.animating()).isTrue();
        now = Math.max(BubbleMotion.IN_NANOS, BubbleMotion.SETTLE_NANOS) + 1;
        assertThat(panel.animating()).as("and stops once it has settled").isFalse();
    }

    @Test void hoveringAsksForFramesAndStopsWhenTheGrowthIsDone() {
        post("a", "one", BuddyNotice.State.RUNNING);
        post("b", "two", BuddyNotice.State.RUNNING);
        layout();
        now = Math.max(BubbleMotion.IN_NANOS, BubbleMotion.SETTLE_NANOS) * 4;
        assertThat(panel.animating()).isFalse();

        panel.handleMove(inBubble(1));
        assertThat(panel.animating()).isTrue();

        now += BubbleMotion.HOVER_NANOS + 1;
        assertThat(panel.animating()).isFalse();

        panel.handleExit();
        assertThat(panel.animating()).as("leaving animates back too").isTrue();
    }

    /** The bubble genuinely starts small and transparent, and genuinely ends full size and opaque. */
    @Test void theArrivalActuallyTravelsFromSmallAndInvisibleToFullSize() {
        post("a", "one", BuddyNotice.State.RUNNING);

        assertThat(panel.topScale()).isEqualTo(BubbleMotion.IN_FROM);
        assertThat(BubbleMotion.inOpacity(0)).isZero();

        now = BubbleMotion.IN_NANOS;
        assertThat(panel.topScale()).isEqualTo(1f);
        assertThat(BubbleMotion.inOpacity(BubbleMotion.IN_NANOS)).isEqualTo(1f);
    }

    /** The stack above a new arrival slides up over it rather than jumping. */
    @Test void theBubblesAboveANewArrivalStartOutOfPlaceAndArrive() {
        post("a", "older", BuddyNotice.State.RUNNING);
        now = BubbleMotion.SETTLE_NANOS * 4;
        layout();
        BufferedImage settled = paint();

        post("b", "newer", BuddyNotice.State.RUNNING);
        layout();
        BufferedImage arriving = paint();

        now += BubbleMotion.SETTLE_NANOS + 1;
        BufferedImage done = paint();

        assertThat(differs(arriving, done)).as("mid-settle the stack is not where it ends up").isTrue();
        assertThat(settled).isNotNull();
    }

    private BufferedImage paint() {
        Dimension size = panel.getPreferredSize();
        BufferedImage image = new BufferedImage(Math.max(1, size.width), Math.max(1, size.height),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { panel.paint(g); } finally { g.dispose(); }
        return image;
    }

    private static boolean differs(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return true;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) return true;
            }
        }
        return false;
    }
}
