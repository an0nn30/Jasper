package dev.jasper.browser.page;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.engine.PreviewScheme;
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
 * pages are made with; an in-memory one is asked of Chromium once, by the first page that needs it, and released once,
 * as its store closes, while Chromium has not stopped.
 */
class PageContextTest {
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
            CefRequestContext standIn = PageTestAccess.standInContext();
            made.complete(standIn);
            assertThat(first).isCompletedWithValue(standIn);
            assertThat(second).isCompletedWithValue(standIn);
            context.close();
            context.close();
            assertThat(releases).containsExactly(standIn);
            assertThat(context.context(chromium)).as("a closed context makes no browser").isCompletedExceptionally();
            assertThat(asks).hasValue(1);
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
            CefRequestContext standIn = PageTestAccess.standInContext();
            made.complete(standIn);
            assertThat(releases).as("released as it is made").containsExactly(standIn);

            var never = new PageContext(true, chromium -> { throw new AssertionError("asked"); }, (chromium, released) -> releases.add(released));
            never.close();
            assertThat(releases).containsExactly(standIn);
        });
    }

    @Test void aContextThatFailedOrWhoseChromiumStoppedReleasesNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<CefRequestContext> releases = new ArrayList<>();
            var failed = new PageContext(true, chromium -> CompletableFuture.failedFuture(new IllegalStateException("closed")),
                (chromium, released) -> releases.add(released));
            failed.context(chromium());
            failed.close();
            var stopped = new PageContext(true, chromium -> CompletableFuture.completedFuture(PageTestAccess.standInContext()),
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
