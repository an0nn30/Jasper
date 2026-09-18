package dev.jasper.app;

import java.awt.Dimension;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The window itself is a {@link javax.swing.JWindow} and the test JVM is headless, so it cannot be
 * built here at all. Its one decision is extracted and tested instead; the shell around that
 * decision is covered by the desktop checks handed to the user.
 */
class BuddyDeckWindowTest {
    private static final Rectangle ANCHOR = new Rectangle(400, 300, 84, 96);
    private static final Dimension SIZE = new Dimension(320, 78);

    @Test void aDrawerWithSomethingInItAndSomewhereToSitIsShown() {
        assertThat(BuddyDeckWindow.shows(false, ANCHOR, false, SIZE)).isTrue();
    }

    @Test void anEmptyDrawerIsNotShown() {
        assertThat(BuddyDeckWindow.shows(false, ANCHOR, true, SIZE)).isFalse();
    }

    /** The buddy has not said where he is yet, so there is nowhere to put it. */
    @Test void aDrawerWithNoAnchorIsNotShown() {
        assertThat(BuddyDeckWindow.shows(false, null, false, SIZE)).isFalse();
    }

    @Test void aDisposedDrawerIsNeverShown() {
        assertThat(BuddyDeckWindow.shows(true, ANCHOR, false, SIZE)).isFalse();
    }

    /** An empty panel asks for nothing; a zero-sized window is a Swing error, not a small drawer. */
    @Test void aDrawerWithNoSizeIsNotShown() {
        assertThat(BuddyDeckWindow.shows(false, ANCHOR, false, new Dimension(0, 0))).isFalse();
        assertThat(BuddyDeckWindow.shows(false, ANCHOR, false, new Dimension(320, 0))).isFalse();
    }
}
