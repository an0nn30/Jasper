package dev.jasper.buddy.internal.presentation;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyBubblePlacementTest {
    private static final Rectangle SCREEN = new Rectangle(0, 25, 1512, 887);
    private static final Dimension BUBBLE = new Dimension(160, 46);

    @Test void theBubbleSitsToTheRightOfTheAnchorVerticallyCentred() {
        Rectangle anchor = new Rectangle(400, 300, 84, 96);
        assertThat(BuddyBubblePlacement.beside(anchor, BUBBLE, SCREEN))
            .isEqualTo(new Point(400 + 84 + 8, 300 + (96 - 46) / 2));
    }

    @Test void theBubbleFlipsToTheLeftWhenTheRightSideDoesNotFit() {
        Rectangle anchor = new Rectangle(1512 - 84 - 24, 300, 84, 96); // the default bottom-right perch
        assertThat(BuddyBubblePlacement.beside(anchor, BUBBLE, SCREEN))
            .isEqualTo(new Point(1512 - 84 - 24 - 8 - 160, 325));
    }

    @Test void theBubbleIsClampedInsideTheScreenVertically() {
        assertThat(BuddyBubblePlacement.beside(new Rectangle(100, 25, 84, 96), BUBBLE, SCREEN))
            .isEqualTo(new Point(192, 50));
        assertThat(BuddyBubblePlacement.beside(new Rectangle(100, 20, 84, 20), BUBBLE, SCREEN))
            .isEqualTo(new Point(192, 25));
        assertThat(BuddyBubblePlacement.beside(new Rectangle(100, 25 + 887 - 40, 84, 96), BUBBLE, SCREEN))
            .isEqualTo(new Point(192, 25 + 887 - 46));
    }

    @Test void aBubbleThatFitsOnNeitherSideIsClampedHorizontally() {
        Rectangle narrow = new Rectangle(0, 0, 200, 400);
        assertThat(BuddyBubblePlacement.beside(new Rectangle(60, 100, 84, 96), BUBBLE, narrow))
            .isEqualTo(new Point(0, 125));
    }

    @Test void aboveCentresTheBubbleOverTheAnchor() {
        Rectangle anchor = new Rectangle(400, 300, 84, 96);
        assertThat(BuddyBubblePlacement.above(anchor, BUBBLE, SCREEN))
            .isEqualTo(new Point(400 + (84 - 160) / 2, 300 - 10 - 46));
    }

    @Test void aboveIsClampedInsideTheScreen() {
        assertThat(BuddyBubblePlacement.above(new Rectangle(4, 40, 84, 96), BUBBLE, SCREEN))
            .isEqualTo(new Point(0, 25));
        assertThat(BuddyBubblePlacement.above(new Rectangle(1512 - 90, 400, 84, 96), BUBBLE, SCREEN))
            .isEqualTo(new Point(1512 - 160, 400 - 10 - 46));
    }
}
