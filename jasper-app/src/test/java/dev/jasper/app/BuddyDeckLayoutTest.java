package dev.jasper.app;

import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyDeckLayoutTest {
    private static final int WIDTH = 300;
    private static final int CARD = 58;
    private static final int PITCH = CARD + BuddyDeckLayout.ROW_GAP;

    @Test void theExpandedListRunsDownTheWindowAtAConstantPitch() {
        assertThat(BuddyDeckLayout.expanded(0, WIDTH, CARD, 0)).isEqualTo(new Rectangle(0, 0, WIDTH, CARD));
        assertThat(BuddyDeckLayout.expanded(2, WIDTH, CARD, 0)).isEqualTo(new Rectangle(0, 2 * PITCH, WIDTH, CARD));
    }

    @Test void scrollingMovesEveryRowUpByTheSameAmount() {
        assertThat(BuddyDeckLayout.expanded(2, WIDTH, CARD, 30).y).isEqualTo(2 * PITCH - 30);
    }

    @Test void oneCardNeedsNoClearAllRow() {
        assertThat(BuddyDeckLayout.expandedHeight(0, CARD)).isZero();
        assertThat(BuddyDeckLayout.expandedHeight(1, CARD)).isEqualTo(CARD);
        assertThat(BuddyDeckLayout.clearRow(1, WIDTH, CARD, 0)).isNull();
    }

    @Test void moreThanOneCardGetsAClearAllRowUnderTheLast() {
        assertThat(BuddyDeckLayout.expandedHeight(3, CARD))
            .isEqualTo(3 * PITCH - BuddyDeckLayout.ROW_GAP
                + BuddyDeckLayout.ROW_GAP + BuddyDeckLayout.CLEAR_ROW_HEIGHT);
        assertThat(BuddyDeckLayout.clearRow(3, WIDTH, CARD, 0))
            .isEqualTo(new Rectangle(0, 3 * PITCH, WIDTH, BuddyDeckLayout.CLEAR_ROW_HEIGHT));
    }

    @Test void theScrollOffsetCanNeitherRunPastTheLastCardNorAboveTheFirst() {
        assertThat(BuddyDeckLayout.clampScroll(-40, 900, 600)).isZero();
        assertThat(BuddyDeckLayout.clampScroll(5_000, 900, 600)).isEqualTo(300);
        assertThat(BuddyDeckLayout.clampScroll(120, 900, 600)).isEqualTo(120);
    }

    @Test void aListShorterThanItsViewportDoesNotScrollAtAll() {
        assertThat(BuddyDeckLayout.clampScroll(200, 300, 600)).isZero();
    }

    @Test void aPointInACardNamesThatCardAndAPointInTheGapNamesNone() {
        assertThat(BuddyDeckLayout.cardAt(5, 4, CARD, 0)).isZero();
        assertThat(BuddyDeckLayout.cardAt(CARD + 2, 4, CARD, 0)).isEqualTo(-1);
        assertThat(BuddyDeckLayout.cardAt(PITCH + 5, 4, CARD, 0)).isEqualTo(1);
    }

    @Test void aPointBelowTheLastCardOrAboveTheFirstNamesNone() {
        assertThat(BuddyDeckLayout.cardAt(4 * PITCH, 4, CARD, 0)).isEqualTo(-1);
        assertThat(BuddyDeckLayout.cardAt(-3, 4, CARD, 0)).isEqualTo(-1);
    }

    @Test void scrollingChangesWhichCardIsUnderThePointer() {
        assertThat(BuddyDeckLayout.cardAt(5, 4, CARD, PITCH)).isEqualTo(1);
    }

    @Test void theDismissTargetSitsInsideItsOwnCardsTopRightCorner() {
        Rectangle card = new Rectangle(10, 20, WIDTH, CARD);
        Rectangle target = BuddyDeckLayout.dismissTarget(card);

        assertThat(card.contains(target)).isTrue();
        assertThat(target.width).isEqualTo(BuddyDeckLayout.DISMISS_SIZE);
        assertThat(target.x + target.width).isEqualTo(card.x + card.width - BuddyDeckLayout.DISMISS_INSET);
        assertThat(target.y).isEqualTo(card.y + BuddyDeckLayout.DISMISS_INSET);
    }

    @Test void atMostThreeBubblesAreDrawnAndTheRestBecomeACount() {
        assertThat(BuddyDeckLayout.visibleInColumn(0)).isZero();
        assertThat(BuddyDeckLayout.visibleInColumn(2)).isEqualTo(2);
        assertThat(BuddyDeckLayout.visibleInColumn(9)).isEqualTo(BuddyDeckLayout.MAX_IN_COLUMN);
    }

    /** They rest on each other rather than sitting apart, so the column reads as a stack. */
    @Test void theBubblesOverlapRatherThanSittingApart() {
        assertThat(BuddyDeckLayout.COLUMN_GAP).isNegative();
        Rectangle newest = BuddyDeckLayout.column(0, 2, WIDTH, CARD, false);
        Rectangle older = BuddyDeckLayout.column(1, 2, WIDTH, CARD, false);

        assertThat(newest.y).as("the newest starts before the older one ends").isLessThan(older.y + older.height);
        assertThat(newest.y).isGreaterThan(older.y);
        assertThat(Math.abs(BuddyDeckLayout.COLUMN_GAP)).as("never into the text").isLessThan(BuddyCard.PAD_Y);
    }

    @Test void theColumnIsItsBubblesPlusTheTail() {
        assertThat(BuddyDeckLayout.columnHeight(0, CARD)).isZero();
        assertThat(BuddyDeckLayout.columnHeight(1, CARD)).isEqualTo(CARD + BuddyDeckLayout.TAIL_HEIGHT);
        assertThat(BuddyDeckLayout.columnHeight(3, CARD)).isEqualTo(
            3 * CARD + 2 * BuddyDeckLayout.COLUMN_GAP + BuddyDeckLayout.TAIL_HEIGHT);
        assertThat(BuddyDeckLayout.columnHeight(20, CARD))
            .as("past the cap the column stops growing").isEqualTo(BuddyDeckLayout.columnHeight(3, CARD));
    }

    /** Above him, the newest sits at the bottom of the column: nearest his head. */
    @Test void aboveHimTheNewestIsLowestAndOlderOnesRiseAwayFromHim() {
        Rectangle newest = BuddyDeckLayout.column(0, 3, WIDTH, CARD, false);
        Rectangle middle = BuddyDeckLayout.column(1, 3, WIDTH, CARD, false);
        Rectangle oldest = BuddyDeckLayout.column(2, 3, WIDTH, CARD, false);

        assertThat(newest.y).isGreaterThan(middle.y);
        assertThat(middle.y).isGreaterThan(oldest.y);
        assertThat(oldest.y).isZero();
        assertThat(newest.y + newest.height)
            .isEqualTo(BuddyDeckLayout.columnHeight(3, CARD) - BuddyDeckLayout.TAIL_HEIGHT);
        assertThat(newest.x).isZero();
        assertThat(newest.width).isEqualTo(WIDTH);
    }

    /** Flipped below him the order inverts, so the newest is still the one nearest his head. */
    @Test void belowHimTheNewestIsHighestSoItStaysNearestHisHead() {
        Rectangle newest = BuddyDeckLayout.column(0, 3, WIDTH, CARD, true);
        Rectangle oldest = BuddyDeckLayout.column(2, 3, WIDTH, CARD, true);

        assertThat(newest.y).isLessThan(oldest.y);
        assertThat(newest.y).isEqualTo(BuddyDeckLayout.TAIL_HEIGHT);
    }

    @Test void everyBubbleSitsInsideTheColumn() {
        for (boolean below : new boolean[] {false, true}) {
            int height = BuddyDeckLayout.columnHeight(3, CARD);
            for (int index = 0; index < 3; index++) {
                Rectangle card = BuddyDeckLayout.column(index, 3, WIDTH, CARD, below);
                assertThat(card.y).as("below=%s index=%d", below, index).isNotNegative();
                assertThat(card.y + card.height).isLessThanOrEqualTo(height);
            }
        }
    }

    /** Without the tail it is a floating list, not a thought. */
    @Test void theTailRunsFromHisHeadTowardsTheNearestBubble() {
        int height = BuddyDeckLayout.columnHeight(2, CARD);
        Rectangle[] above = BuddyDeckLayout.tail(WIDTH, height, false);

        assertThat(above).hasSize(2);
        assertThat(above[0].width).isGreaterThan(above[1].width);
        assertThat(above[1].y).as("the small one is nearest him, at the bottom").isGreaterThan(above[0].y);
        for (Rectangle circle : above) {
            assertThat(circle.y).isGreaterThanOrEqualTo(height - BuddyDeckLayout.TAIL_HEIGHT);
            assertThat(circle.y + circle.height).isLessThanOrEqualTo(height);
            assertThat(circle.x).isGreaterThan(0);
        }
    }

    @Test void flippedBelowHimTheTailIsAtTheTopAndStillPointsAtHim() {
        int height = BuddyDeckLayout.columnHeight(2, CARD);
        Rectangle[] below = BuddyDeckLayout.tail(WIDTH, height, true);

        assertThat(below[1].y).as("the small one is nearest him, at the top").isLessThan(below[0].y);
        for (Rectangle circle : below) {
            assertThat(circle.y).isNotNegative();
            assertThat(circle.y + circle.height).isLessThanOrEqualTo(BuddyDeckLayout.TAIL_HEIGHT);
        }
    }

    @Test void aPointInAColumnBubbleNamesIt() {
        assertThat(BuddyDeckLayout.columnAt(
            BuddyDeckLayout.column(1, 3, WIDTH, CARD, false).y + 4, 3, CARD, false)).isEqualTo(1);
        assertThat(BuddyDeckLayout.columnAt(
            BuddyDeckLayout.columnHeight(3, CARD) - 2, 3, CARD, false)).as("in the tail").isEqualTo(-1);
    }
}
