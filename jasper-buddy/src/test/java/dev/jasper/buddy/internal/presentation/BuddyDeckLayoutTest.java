package dev.jasper.buddy.internal.presentation;

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

    @Test void capsulesHaveAGapAndNoThoughtTail() {
        assertThat(BuddyDeckLayout.columnHeight(0, CARD)).isZero();
        assertThat(BuddyDeckLayout.columnHeight(1, CARD)).isEqualTo(CARD);
        assertThat(BuddyDeckLayout.columnHeight(3, CARD)).isEqualTo(3 * CARD + 2 * BuddyDeckLayout.COLUMN_GAP);
        assertThat(BuddyDeckLayout.columnHeight(20, CARD)).isEqualTo(BuddyDeckLayout.columnHeight(3, CARD));
        Rectangle newest = BuddyDeckLayout.column(0, 3, WIDTH, CARD, false);
        Rectangle middle = BuddyDeckLayout.column(1, 3, WIDTH, CARD, false);
        assertThat(newest.y - middle.y - middle.height).isEqualTo(BuddyDeckLayout.COLUMN_GAP).isPositive();
    }

    @Test void flippingKeepsNewestNearestTheBuddy() {
        assertThat(BuddyDeckLayout.column(0, 3, WIDTH, CARD, false).y).isEqualTo(2 * (CARD + BuddyDeckLayout.COLUMN_GAP));
        assertThat(BuddyDeckLayout.column(0, 3, WIDTH, CARD, true).y).isZero();
        for (boolean below : new boolean[] {false, true}) {
            for (int i = 0; i < 3; i++) {
                Rectangle card = BuddyDeckLayout.column(i, 3, WIDTH, CARD, below);
                assertThat(BuddyDeckLayout.columnAt(card.y + 4, 3, CARD, below)).isEqualTo(i);
                assertThat(card.y + card.height).isLessThanOrEqualTo(BuddyDeckLayout.columnHeight(3, CARD));
            }
        }
    }
}
