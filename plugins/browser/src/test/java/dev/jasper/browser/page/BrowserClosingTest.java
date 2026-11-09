package dev.jasper.browser.page;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * V13: when Java's close of a page's browser can go to Chromium. JCEF's own way for a browser whose native part is not
 * made yet, a handler that closes it after creation, does nothing (its {@code close} returns at once, the browser
 * already closing), so the page decides itself: a browser never asked for is closed at once and refuses its creation
 * from then on; one whose creation is under way is closed as Chromium makes it; one made is closed at once.
 */
class BrowserClosingTest {
    @Test void aBrowserNeverAskedForClosesAtOnceAndIsNeverMade() {
        var closing = new BrowserClosing();
        assertThat(closing.close()).isEqualTo(BrowserClosing.Close.NEVER_MADE);
        assertThat(closing.mayCreate()).as("its creation is refused from then on").isFalse();
        assertThat(closing.created()).as("made anyway, by a creation no one here asked for: closed at once").isTrue();
    }

    @Test void aBrowserClosedWhileItsCreationIsUnderWayIsClosedAsChromiumMakesIt() {
        var closing = new BrowserClosing();
        assertThat(closing.mayCreate()).isTrue();
        assertThat(closing.close()).as("its native part is not made yet").isEqualTo(BrowserClosing.Close.ON_CREATION);
        assertThat(closing.created()).as("made: closed now").isTrue();
        assertThat(closing.close()).isEqualTo(BrowserClosing.Close.NOW);
    }

    @Test void aBrowserMadeClosesAtOnceAndItsCreationClosesNothing() {
        var closing = new BrowserClosing();
        assertThat(closing.mayCreate()).isTrue();
        assertThat(closing.mayCreate()).as("asked again, as the view shows again").isTrue();
        assertThat(closing.created()).as("nobody asked it to close").isFalse();
        assertThat(closing.close()).isEqualTo(BrowserClosing.Close.NOW);
        assertThat(closing.mayCreate()).as("closed").isFalse();
    }
}
