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
}
