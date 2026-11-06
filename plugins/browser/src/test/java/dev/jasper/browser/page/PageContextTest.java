package dev.jasper.browser.page;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.engine.PreviewScheme;
import dev.jasper.browser.engine.RecordingContext;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.cef.browser.CefRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A browsing group's request context, without Chromium: the persistent one is Chromium's global context, which 4a's
 * pages are made with; an in-memory one is kept for the group, asked of Chromium once, by the first page that needs it,
 * and released once, as its store closes, while Chromium has not stopped. Each page is handed a context object of its
 * own over the kept one, as JCEF disposes a browser's context object as the browser closes (plan 6's V10).
 */
class PageContextTest {
    private static final String NATIVE = "CefRequestContext";
    @TempDir Path data;

    @Test void thePersistentContextIsTheGlobalOneAtOnceAndAsksNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PageContext persistent = PageContext.persistent();
            assertThat(persistent.inMemory()).isFalse();
            CompletableFuture<CefRequestContext> context = persistent.context(chromium());
            assertThat(context).isCompletedWithValue(null);
            persistent.close();
            assertThat(persistent.context(chromium())).as("the global context outlives its store").isCompletedWithValue(null);
        });
    }

    @Test void anInMemoryContextIsAskedForOnceAndReleasedOnceAsItCloses() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var made = new CompletableFuture<CefRequestContext>();
            var asks = new AtomicInteger();
            var releases = new ArrayList<CefRequestContext>();
            var context = new PageContext(true, chromium -> {
                asks.incrementAndGet();
                return made;
            }, (chromium, released) -> releases.add(released));
            assertThat(context.inMemory()).isTrue();
            Chromium chromium = chromium();
            CompletableFuture<CefRequestContext> first = context.context(chromium), second = context.context(chromium);
            assertThat(asks).as("asked of Chromium once").hasValue(1);
            assertThat(first).isNotDone();
            var kept = new RecordingContext(42);
            made.complete(kept);
            assertThat(first).isCompleted();
            assertThat(second).isCompleted();
            context.close();
            context.close();
            assertThat(releases).as("the kept context, once").containsExactly(kept);
            assertThat(kept.disposals()).as("released through Chromium, never disposed here").isZero();
            assertThat(context.context(chromium)).as("a closed context makes no browser").isCompletedExceptionally();
            assertThat(asks).hasValue(1);
        });
    }

    /**
     * V10: two pages of one group are never handed the same context object, and neither is the kept one; each object
     * carries the kept context's native handle, and a closed page's object, which JCEF disposes, leaves the kept context
     * as it was for the next page.
     */
    @Test void eachPageIsHandedAContextObjectOfItsOwnOverTheGroupsKeptOne() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var kept = new RecordingContext(42);
            var releases = new ArrayList<CefRequestContext>();
            var context = new PageContext(true, chromium -> CompletableFuture.completedFuture(kept), (chromium, released) -> releases.add(released));
            Chromium chromium = chromium();
            CefRequestContext first = context.context(chromium).join(), second = context.context(chromium).join();
            assertThat(first).isNotSameAs(second).isNotSameAs(kept);
            assertThat(second).isNotSameAs(kept);
            assertThat(first.getNativeRef(NATIVE)).isEqualTo(42);
            assertThat(second.getNativeRef(NATIVE)).isEqualTo(42);
            first.dispose();
            first.dispose();
            assertThat(kept.disposals()).as("a closing browser's dispose leaves the kept context").isZero();
            CefRequestContext third = context.context(chromium).join();
            assertThat(third).isNotSameAs(first).isNotSameAs(second);
            assertThat(third.getNativeRef(NATIVE)).as("the kept context, still there for the next page").isEqualTo(42);
            second.dispose();
            third.dispose();
            context.close();
            assertThat(releases).containsExactly(kept);
            assertThat(kept.disposals()).isZero();
        });
    }

    @Test void aContextClosedBeforeChromiumMadeItIsReleasedOnceMadeAndOneNeverAskedForReleasesNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var made = new CompletableFuture<CefRequestContext>();
            List<CefRequestContext> releases = new ArrayList<>();
            var context = new PageContext(true, chromium -> made, (chromium, released) -> releases.add(released));
            context.context(chromium());
            context.close();
            assertThat(releases).isEmpty();
            var kept = new RecordingContext(7);
            made.complete(kept);
            assertThat(releases).as("released as it is made").containsExactly(kept);

            var never = new PageContext(true, chromium -> { throw new AssertionError("asked"); }, (chromium, released) -> releases.add(released));
            never.close();
            assertThat(releases).containsExactly(kept);
        });
    }

    @Test void aContextThatFailedOrWhoseChromiumStoppedReleasesNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<CefRequestContext> releases = new ArrayList<>();
            var failed = new PageContext(true, chromium -> CompletableFuture.failedFuture(new IllegalStateException("closed")),
                (chromium, released) -> releases.add(released));
            failed.context(chromium());
            failed.close();
            var stopped = new PageContext(true, chromium -> CompletableFuture.completedFuture(new RecordingContext(9)),
                (chromium, released) -> releases.add(released));
            Chromium chromium = chromium();
            stopped.context(chromium);
            chromium.close();
            stopped.close();
            assertThat(releases).isEmpty();
        });
    }

    /** A Chromium that never starts: nothing here asks for its client. */
    private Chromium chromium() {
        return new Chromium(data, () -> { }, task -> { throw new AssertionError("Chromium was started"); }, new AboutScheme(),
            new PreviewScheme(), cause -> { });
    }
}
