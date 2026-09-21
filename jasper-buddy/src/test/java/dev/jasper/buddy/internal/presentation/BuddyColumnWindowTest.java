package dev.jasper.buddy.internal.presentation;

import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The window is a JWindow and the test JVM is headless, so only its decisions are testable here.
 * The shell around them is covered by the desktop checks handed to the user.
 */
class BuddyColumnWindowTest {
    private static final Rectangle SCREEN = new Rectangle(0, 0, 1512, 944);

    @Test void thereIsRoomAboveHimInTheMiddleOfTheScreen() {
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(400, 600, 84, 96), 220, SCREEN)).isTrue();
    }

    @Test void aBuddyNearTheTopHasToFlipBelow() {
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(400, 40, 84, 96), 220, SCREEN)).isFalse();
    }

    /** Exactly enough room is still room; the boundary must not flip for a single pixel. */
    @Test void theBoundaryIsInclusive() {
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(400, 218, 84, 96), 220, SCREEN)).isTrue();
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(400, 217, 84, 96), 220, SCREEN)).isFalse();
    }

    @Test void theBodyGapDoesNotIncludeItsTransparentShadowAndAnimationRoom() {
        Rectangle anchor = new Rectangle(600, 500, 84, 96);
        var size = new java.awt.Dimension(BuddyCard.WIDTH + 2 * BuddyColumnPanel.MARGIN,
            BuddyCard.HEIGHT + 2 * BuddyColumnPanel.MARGIN + BuddyColumnPanel.ARRIVAL_ROOM);
        var above = BuddyColumnWindow.place(anchor, size, SCREEN, false);
        assertThat(anchor.y - (above.y + size.height - BuddyColumnPanel.MARGIN)).isEqualTo(30);
        var below = BuddyColumnWindow.place(anchor, size, SCREEN, true);
        assertThat(below.y + BuddyColumnPanel.MARGIN - anchor.y - anchor.height).isEqualTo(30);
    }

    @Test void aScreenWithAnOffsetOriginIsAccountedFor() {
        Rectangle second = new Rectangle(1512, 300, 1000, 800);
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(1600, 320, 84, 96), 220, second)).isFalse();
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(1600, 700, 84, 96), 220, second)).isTrue();
    }
}
