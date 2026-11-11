package dev.jasper.browser.page;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.engine.EngineTestAccess;
import dev.jasper.browser.engine.PreviewScheme;
import dev.jasper.browser.engine.RecordingContext;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

    /**
     * V12: a context object handed out for a page that then makes no browser, as its page closed meanwhile or Chromium
     * stopped, goes back at once, so it holds its group's kept context no longer; no browser is made then.
     */
    @Test void aContextObjectForABrowserNeverMadeGoesBackAtOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Chromium chromium = chromium();
            var kept = new RecordingContext(11);
            var context = new PageContext(true, any -> CompletableFuture.completedFuture(kept), (any, released) -> { });
            var closedPage = new Page(java.net.URI.create("https://example.com/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run, context);
            CefRequestContext unused = context.context(chromium).join();
            closedPage.close();
            PageTestAccess.make(closedPage, chromium, unused);
            assertThat(unused.getNativeRef(NATIVE)).as("handed back").isZero();
            assertThat(PageTestAccess.hasBrowser(closedPage)).isFalse();

            var openPage = new Page(java.net.URI.create("https://example.com/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run, context);
            CefRequestContext late = context.context(chromium).join();
            chromium.close();
            PageTestAccess.make(openPage, chromium, late);
            assertThat(late.getNativeRef(NATIVE)).as("Chromium has stopped: handed back").isZero();
            assertThat(PageTestAccess.hasBrowser(openPage)).isFalse();
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

    /** Ruling V44: a context Chromium failed to make is not kept, so the group's next page asks for one again. */
    @Test void aContextChromiumFailedToMakeIsAskedForAgainByTheNextPage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var asks = new AtomicInteger();
            var kept = new RecordingContext(7);
            var context = new PageContext(true, chromium -> asks.incrementAndGet() == 1
                ? CompletableFuture.failedFuture(new IllegalStateException(Chromium.PROXY_REFUSED + ": refused"))
                : CompletableFuture.completedFuture(kept), (chromium, released) -> { });
            Chromium chromium = chromium();
            assertThat(context.context(chromium)).isCompletedExceptionally();
            assertThat(context.context(chromium).join().getNativeRef(NATIVE)).as("asked again").isEqualTo(7);
            assertThat(context.context(chromium).join().getNativeRef(NATIVE)).isEqualTo(7);
            assertThat(asks).as("once made, kept").hasValue(2);
        });
    }

    /**
     * Rulings F4 and V44 through Chromium, played without starting it: while the proxy is on, an in-memory context that
     * refuses it fails its page, which stays blank, the plugin's notification saying why; once the proxy is off, the
     * group's next page asks again and gets a context.
     */
    @Test void aGroupWhoseContextRefusedTheProxyGetsOneForItsNextPageOnceTheProxyIsOff() throws Exception {
        Chromium chromium = chromium();
        var refusing = new RecordingContext(3).refusing("no request context");
        var made = new RecordingContext(4);
        var queue = new ArrayDeque<>(List.of(refusing, made));
        EngineTestAccess.makeContextsWith(chromium, queue::poll);
        chromium.setProxy(Optional.of("127.0.0.1:8118"));
        PageContext group = PageContext.memory();
        var first = new AtomicReference<CompletableFuture<CefRequestContext>>();
        SwingUtilities.invokeAndWait(() -> first.set(group.context(chromium)));
        EngineTestAccess.contextInitialized(chromium, new RecordingContext(1));
        assertThat(first.get()).isCompletedExceptionally();
        assertThat(refusing.disposals()).isEqualTo(1);
        var handed = new ConcurrentLinkedQueue<Runnable>();
        EngineTestAccess.handOverWith(chromium, handed::add);
        chromium.setProxy(Optional.empty());
        handed.poll().run();
        var second = new AtomicReference<CompletableFuture<CefRequestContext>>();
        SwingUtilities.invokeAndWait(() -> second.set(group.context(chromium)));
        handed.poll().run();
        CefRequestContext page = second.get().get(10, TimeUnit.SECONDS);
        assertThat(page.getNativeRef(NATIVE)).as("the next page's, over the context made now").isEqualTo(4);
        assertThat(made.getPreference("proxy")).isEqualTo(java.util.Map.of("mode", "system"));
        page.dispose();
        SwingUtilities.invokeAndWait(group::close);
        chromium.close();
    }

    /**
     * Plan 7's F7: an in-memory context tells those who asked once, as its group's store closes, whether or not a page
     * ever asked Chromium for it; one that has closed tells a later listener at once, and a listener whose registration
     * was closed hears nothing. The persistent context never closes, so it tells nobody.
     */
    @Test void anInMemoryContextTellsItsListenersOnceAsItClosesAndThePersistentOneNever() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var told = new ArrayList<String>();
            PageContext memory = PageContext.memory();
            memory.onClosed(() -> told.add("first"));
            memory.onClosed(() -> told.add("second"));
            memory.onClosed(() -> told.add("withdrawn")).close();
            assertThat(told).isEmpty();
            memory.close();
            memory.close();
            assertThat(told).as("once each, in order, never asked of Chromium").containsExactly("first", "second");
            memory.onClosed(() -> told.add("late"));
            assertThat(told).as("closed already: at once").containsExactly("first", "second", "late");

            var asked = new PageContext(true, chromium -> CompletableFuture.completedFuture(new RecordingContext(5)), (chromium, released) -> { });
            asked.context(chromium());
            asked.onClosed(() -> told.add("asked"));
            asked.close();
            assertThat(told).endsWith("asked");

            PageContext persistent = PageContext.persistent();
            persistent.onClosed(() -> told.add("persistent"));
            persistent.close();
            assertThat(told).doesNotContain("persistent");
        });
    }

    /** Plan 7's F7: a page names its browsing group's context, for the downloads of a /dev/null group. */
    @Test void aPageNamesItsGroupsContext() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PageContext memory = PageContext.memory();
            var page = new Page(java.net.URI.create("https://example.com/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run, memory);
            assertThat(page.context()).isSameAs(memory);
            assertThat(page.inMemory()).isTrue();
            page.close();
        });
    }

    /** A Chromium that never starts: nothing here asks for its client. */
    private Chromium chromium() {
        return new Chromium(data, () -> { }, task -> { throw new AssertionError("Chromium was started"); }, new AboutScheme(),
            new PreviewScheme(Runnable::run), cause -> { });
    }
}
