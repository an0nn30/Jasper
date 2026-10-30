package dev.jasper.browser.page;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chromium's {@code DoClose} for a page's browser, decided without Chromium: a close Java asked for goes ahead, and a
 * close the page asked for itself, a script's {@code window.close()}, is held back and closes the page's item instead.
 * A browser whose close goes ahead without Jasper hearing of it would leave its item showing a dead page.
 */
class PageCloseTest {
    private static final URI EXAMPLE = URI.create("https://example.com");
    private static final Favicons NO_FAVICONS = PageTestAccess.noFavicons();
    private final List<Runnable> posted = new ArrayList<>();
    private final AtomicInteger itemCloses = new AtomicInteger();

    @Test void aCloseJavaAskedForGoesAheadAndClosesNoItem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Page page = page();
            assertThat(Page.holdsClose(true, page, posted::add)).as("Chromium destroys the browser").isFalse();
            assertThat(posted).isEmpty();
            assertThat(itemCloses).hasValue(0);
        });
    }

    @Test void aScriptsCloseIsHeldBackAndClosesTheItemOnTheUiThread() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Page page = page();
            assertThat(Page.holdsClose(false, page, posted::add)).as("Chromium waits").isTrue();
            assertThat(itemCloses).as("not on Chromium's thread").hasValue(0);
            assertThat(posted).hasSize(1);
            posted.getFirst().run();
            assertThat(itemCloses).hasValue(1);
            page.close();
            assertThat(Page.holdsClose(false, page, posted::add)).isTrue();
            posted.getLast().run();
            assertThat(itemCloses).as("nothing after the page's close").hasValue(1);
        });
    }

    @Test void aBrowserWithNoPageYetIsHeldBackAndClosesNothing() {
        assertThat(Page.holdsClose(false, null, posted::add)).isTrue();
        assertThat(posted).isEmpty();
        assertThat(Page.holdsClose(true, null, posted::add)).isFalse();
    }

    /** The title of the document before is gone once the main frame starts loading, whatever Chromium then reports. */
    @Test void aMainFrameLoadStartClearsTheTitle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var changes = new AtomicInteger();
            var page = new Page(EXAMPLE, changes::incrementAndGet, NO_FAVICONS);
            page.titleChanged("Example Domain");
            assertThat(page.title()).isEqualTo("Example Domain");
            var started = new ArrayList<String>();
            page.onLoadStart(started::add);
            int before = changes.get();
            page.loadStarted("https://example.org/");
            assertThat(page.title()).isEmpty();
            assertThat(changes.get()).as("told once").isEqualTo(before + 1);
            assertThat(started).containsExactly("https://example.org/");
            page.loadStarted("https://example.org/again");
            assertThat(changes.get()).as("already blank: nothing to tell").isEqualTo(before + 1);
            page.titleChanged("Example Org");
            assertThat(page.title()).isEqualTo("Example Org");
            page.close();
        });
    }

    private Page page() {
        var page = new Page(EXAMPLE, () -> { }, NO_FAVICONS);
        page.onCloseItem(itemCloses::incrementAndGet);
        return page;
    }
}
