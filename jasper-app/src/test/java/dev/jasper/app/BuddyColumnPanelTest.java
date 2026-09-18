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
        panel.refresh();
        now += BubbleMotion.IN_NANOS;
    }

    private void layout() { panel.setSize(panel.getPreferredSize()); }

    private Point inBubble(int index) {
        Rectangle card = panel.cardBounds(index);
        return new Point(card.x + card.width / 2, card.y + card.height / 2);
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
            BuddyDeckLayout.columnHeight(2, BuddyCard.height(panel)) + 2 * BuddyColumnPanel.MARGIN + BuddyColumnPanel.ARRIVAL_ROOM);
    }

    @Test void pastThreeTheColumnStopsGrowingAndTheNewestCarriesTheCount() {
        for (int i = 0; i < 9; i++) post("k" + i, "card " + i, BuddyNotice.State.RUNNING);
        layout();

        assertThat(panel.getPreferredSize().height).isEqualTo(
            BuddyDeckLayout.columnHeight(3, BuddyCard.height(panel)) + 2 * BuddyColumnPanel.MARGIN + BuddyColumnPanel.ARRIVAL_ROOM);
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
    @Test void clickingOutsideTheCapsulesOpensTheDrawer() {
        post("a", "one", BuddyNotice.State.RUNNING);
        layout();
        int height = BuddyDeckLayout.columnHeight(1, BuddyCard.height(panel));

        panel.handleClick(new Point(1, 1));

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
        assertThat(inBubble(0)).as("the flip starts at its current painted position").isEqualTo(newestAbove);
        now += BubbleSpring.SETTLE_NANOS;
        assertThat(inBubble(0)).as("the newest moved to the other end").isNotEqualTo(newestAbove);
        assertThat(panel.handleClick(inBubble(0))).isFalse();
        assertThat(activated).containsExactly("newer");
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

    @Test void anArrivingBubbleKeepsAskingForFramesUntilItHasSettled() {
        assertThat(panel.animating()).isFalse();
        deck.post(new BuddyNotice("terminal", "a", BuddyNotice.Kind.TASK, "one",
            BuddyNotice.State.RUNNING, () -> "detail", () -> { }));
        panel.refresh();
        assertThat(panel.animating()).isTrue();
        now = BubbleMotion.IN_NANOS / 2;
        assertThat(panel.animating()).isTrue();
        now = BubbleMotion.IN_NANOS + 1;
        assertThat(panel.animating()).isFalse();
        assertThat(panel.needsDetailUpdates()).as("elapsed time must still tick after settling").isTrue();
        assertThat(panel.needsAnimationFrames()).as("running subtext keeps shimmering").isTrue();
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

    @Test void updatingTheSameNoticeKeepsItsPositionAndDoesNotReplayArrival() {
        post("a", "one", BuddyNotice.State.RUNNING);
        Rectangle before = panel.cardBounds(0);
        deck.post(new BuddyNotice("terminal", "a", BuddyNotice.Kind.TASK, "one",
            BuddyNotice.State.DONE, () -> "Finished", () -> { }));
        panel.refresh();
        assertThat(panel.cardBounds(0)).isEqualTo(before);
        assertThat(panel.animating()).isFalse();
        assertThat(panel.needsDetailUpdates()).isFalse();
    }

    @Test void resizingForANewArrivalDoesNotTeleportTheExistingCapsuleOnScreen() {
        post("a", "older", BuddyNotice.State.RUNNING);
        int before = panel.cardBounds(0).y - panel.getPreferredSize().height;
        deck.post(new BuddyNotice("terminal", "b", BuddyNotice.Kind.TASK, "newer",
            BuddyNotice.State.RUNNING, () -> "d", () -> { }));
        panel.refresh();
        assertThat(panel.cardBounds(1).y - panel.getPreferredSize().height).isEqualTo(before);
        now += BubbleMotion.IN_NANOS / 6;
        int interrupted = panel.cardBounds(1).y - panel.getPreferredSize().height;
        deck.post(new BuddyNotice("terminal", "c", BuddyNotice.Kind.TASK, "newest",
            BuddyNotice.State.RUNNING, () -> "d", () -> { }));
        panel.refresh();
        assertThat(panel.cardBounds(2).y - panel.getPreferredSize().height).isEqualTo(interrupted);
    }

    @Test void inputFollowsTheMovingCapsuleAndIgnoresItsTransparentCorners() {
        deck.post(new BuddyNotice("terminal", "a", BuddyNotice.Kind.TASK, "one",
            BuddyNotice.State.RUNNING, () -> "d", () -> activated.add("one")));
        panel.refresh();
        Rectangle bounds = panel.cardBounds(0);
        panel.handleMove(new Point(bounds.x + 1, bounds.y + 1));
        assertThat(panel.hovered()).isEqualTo(-1);
        assertThat(panel.handleClick(inBubble(0))).isFalse();
        assertThat(activated).containsExactly("one");
        now += BubbleMotion.IN_NANOS;
        assertThat(panel.cardBounds(0).y - bounds.y).isEqualTo((int) BubbleMotion.IN_TRAVEL);
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

    @Test void refreshingWithoutAChangeDoesNotRestartMotion() {
        post("a", "one", BuddyNotice.State.RUNNING);
        panel.refresh();
        assertThat(panel.animating()).isFalse();
    }
}
