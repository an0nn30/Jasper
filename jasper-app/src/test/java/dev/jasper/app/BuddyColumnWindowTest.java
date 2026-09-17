package dev.jasper.app;

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
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(400, 220, 84, 96), 220, SCREEN)).isTrue();
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(400, 219, 84, 96), 220, SCREEN)).isFalse();
    }

    @Test void aScreenWithAnOffsetOriginIsAccountedFor() {
        Rectangle second = new Rectangle(1512, 300, 1000, 800);
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(1600, 320, 84, 96), 220, second)).isFalse();
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(1600, 700, 84, 96), 220, second)).isTrue();
    }
}
