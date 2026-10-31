package dev.jasper.browser.page;

import java.awt.Point;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A page's DevTools ask Chromium for their browser only once their canvas is in a window and every DevTools made for the
 * page before them have ended (P44): Chromium keeps one DevTools per page and, asked again while they are open, only
 * shows them, which would leave JCEF matching the next browser Chromium makes to the one it was asked for. Chromium's
 * side, the ask, the close and the end, is played by the test through the page's seam. Headless, a lightweight parent
 * given a peer is in a window as far as the canvas can tell. No Chromium.
 */
class DevToolsTest {
    private static final URI PAGE = URI.create("https://example.com/");
    private final List<String> asked = new ArrayList<>(), closing = new ArrayList<>();

    /** The review's case: Inspect Element three times before Chromium has closed the first DevTools. */
    @Test void devToolsAskOnlyOnceEveryEarlierOneHasEndedAndOnesClosedWhileWaitingNeverAsk() throws Exception {
        onUiThread(() -> {
            Page page = page();
            DevTools first = shown(page.devTools(new Point(1, 1)));
            assertThat(asked).containsExactly("1,1");
            DevTools second = shown(page.devTools(new Point(2, 2)));
            assertThat(asked).as("the first are open").containsExactly("1,1");
            first.close(); // as the item shows the second in their place
            assertThat(closing).containsExactly("1,1");
            var secondEnded = new AtomicInteger();
            second.onEnd(secondEnded::incrementAndGet);
            DevTools third = page.devTools(new Point(3, 3));
            second.close(); // as the item shows the third in their place, before Chromium closed the first
            assertThat(secondEnded).as("closed while waiting, they end only once those before them have").hasValue(0);
            shown(third);
            assertThat(asked).as("the first are still closing").containsExactly("1,1");
            first.ended(); // Chromium has closed the first DevTools' browser
            assertThat(secondEnded).hasValue(1);
            assertThat(asked).as("the second never ask; the third ask at their own point").containsExactly("1,1", "3,3");
            assertThat(closing).containsExactly("1,1");
        });
    }

    @Test void devToolsAskOnceTheirCanvasIsInAWindowAndOnlyOnce() throws Exception {
        onUiThread(() -> {
            Page page = page();
            DevTools tools = page.devTools(new Point(5, 6));
            assertThat(asked).as("not in a window").isEmpty();
            JPanel parent = shownIn(tools);
            assertThat(asked).containsExactly("5,6");
            parent.remove(tools.view());
            parent.add(tools.view()); // as a pane moves to another tab or window
            assertThat(asked).containsExactly("5,6");
        });
    }

    @Test void devToolsAPageCannotOpenYetAskWhenTheirCanvasIsNextInAWindow() throws Exception {
        onUiThread(() -> {
            var ready = new java.util.concurrent.atomic.AtomicBoolean();
            Page page = page();
            page.useDevToolsOpener((tools, at) -> {
                if (!ready.get()) return Optional.empty();
                asked.add(at.x + "," + at.y);
                return Optional.of(() -> { });
            });
            DevTools tools = page.devTools(new Point(7, 7));
            JPanel parent = shownIn(tools);
            assertThat(asked).isEmpty();
            ready.set(true);
            parent.remove(tools.view());
            parent.add(tools.view());
            assertThat(asked).containsExactly("7,7");
        });
    }

    @Test void devToolsChromiumMadeEndOnlyOnceChromiumHasClosedThem() throws Exception {
        onUiThread(() -> {
            Page page = page();
            DevTools tools = shown(page.devTools(new Point(1, 2)));
            var ended = new AtomicInteger();
            tools.onEnd(ended::incrementAndGet);
            tools.close();
            tools.close();
            assertThat(closing).as("asked once").containsExactly("1,2");
            assertThat(ended).hasValue(0);
            tools.ended();
            assertThat(ended).hasValue(1);
            var late = new AtomicInteger();
            tools.onEnd(late::incrementAndGet);
            assertThat(late).as("told at once once they have ended").hasValue(1);
        });
    }

    /** Ended DevTools are forgotten by their page, so new ones ask at once; and the page's close closes the ones it has. */
    @Test void aPageForgetsDevToolsThatEndedAndClosesTheOnesItHasWhenItCloses() throws Exception {
        onUiThread(() -> {
            Page page = page();
            DevTools first = shown(page.devTools(new Point(1, 1)));
            first.close();
            first.ended();
            DevTools second = shown(page.devTools(new Point(2, 2)));
            assertThat(asked).as("nothing left to wait for").containsExactly("1,1", "2,2");
            var ended = new AtomicInteger();
            second.onEnd(ended::incrementAndGet);
            page.close();
            assertThat(closing).containsExactly("1,1", "2,2");
            second.ended();
            assertThat(ended).hasValue(1);
            DevTools afterClose = page.devTools(new Point(3, 3));
            var afterEnded = new AtomicInteger();
            afterClose.onEnd(afterEnded::incrementAndGet);
            assertThat(afterEnded).as("a closed page's DevTools end at once").hasValue(1);
            assertThat(page.inspect(new Point(4, 4))).isEmpty();
        });
    }

    /** DevTools Chromium could not open end at once, and those after them ask. */
    @Test void devToolsThatCouldNotOpenEndAndThoseAfterThemAsk() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.useDevToolsOpener((tools, at) -> {
                if (at.x == 1) throw new IllegalStateException("no DevTools");
                asked.add(at.x + "," + at.y);
                return Optional.of(() -> closing.add(at.x + "," + at.y));
            });
            DevTools failed = page.devTools(new Point(1, 1));
            var ended = new AtomicInteger();
            failed.onEnd(ended::incrementAndGet);
            shown(failed);
            assertThat(ended).hasValue(1);
            shown(page.devTools(new Point(2, 2)));
            assertThat(asked).containsExactly("2,2");
        });
    }

    /** A page whose DevTools Chromium is asked for through the test, which records each ask and each close at its point. */
    private Page page() {
        Page page = new Page(PAGE, () -> { }, PageTestAccess.noFavicons());
        page.useDevToolsOpener((tools, at) -> {
            String point = at.x + "," + at.y;
            asked.add(point);
            return Optional.of(() -> closing.add(point));
        });
        return page;
    }

    /** {@code tools} with their canvas in a window. */
    private static DevTools shown(DevTools tools) {
        shownIn(tools);
        return tools;
    }

    /** Puts the DevTools' canvas in a lightweight parent with a peer, as their item's view in a window. */
    private static JPanel shownIn(DevTools tools) {
        var parent = new JPanel();
        parent.addNotify();
        parent.add(tools.view());
        return parent;
    }

    private static void onUiThread(Runnable test) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                test.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
    }
}
