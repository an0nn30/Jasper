package dev.jasper.browser.page;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.engine.PreviewScheme;
import dev.jasper.browser.store.CapturedLog;
import dev.jasper.browser.store.Reached;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chromium's {@code DoClose} for a page's browser, decided without Chromium: a close Java asked for goes ahead, and a
 * close the page asked for itself, a script's {@code window.close()}, is held back and closes the page's item instead.
 * A browser whose close goes ahead without Jasper hearing of it would leave its item showing a dead page. A page whose
 * downloads still run keeps its browser until they end (plan 7's F1); its browser is a recording stand-in here.
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

    /**
     * A page keeps the zoom level it is given until its browser is made, which then takes it; a level that is not finite is
     * refused, as Chromium cannot show it, and a closed page takes none.
     */
    @Test void aPageKeepsItsZoomLevelAndRefusesOneThatIsNotFinite() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var page = new Page(EXAMPLE, () -> { }, NO_FAVICONS, Runnable::run, PageContext.persistent());
            assertThat(page.zoomLevel()).as("100%").isEqualTo(0.0);
            page.setZoomLevel(-1.5);
            assertThat(page.zoomLevel()).isEqualTo(-1.5);
            for (double level : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY })
                assertThatThrownBy(() -> page.setZoomLevel(level)).as(Double.toString(level))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(page.zoomLevel()).as("unchanged").isEqualTo(-1.5);
            page.close();
            page.setZoomLevel(2);
            assertThat(page.zoomLevel()).as("closed").isEqualTo(-1.5);
        });
    }

    /** The title of the document before is gone once the main frame starts loading, whatever Chromium then reports. */
    @Test void aMainFrameLoadStartClearsTheTitle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var changes = new AtomicInteger();
            var page = new Page(EXAMPLE, changes::incrementAndGet, NO_FAVICONS, Runnable::run, PageContext.persistent());
            page.titleChanged("Example Domain");
            assertThat(page.title()).isEqualTo("Example Domain");
            var started = new ArrayList<String>();
            page.onLoadStart(started::add);
            int before = changes.get();
            page.loadStarted("https://example.org/", Reached.LINK);
            assertThat(page.title()).isEmpty();
            assertThat(changes.get()).as("told once").isEqualTo(before + 1);
            assertThat(started).containsExactly("https://example.org/");
            page.loadStarted("https://example.org/again", Reached.LINK);
            assertThat(changes.get()).as("already blank: nothing to tell").isEqualTo(before + 1);
            page.titleChanged("Example Org");
            assertThat(page.title()).isEqualTo("Example Org");
            page.close();
        });
    }

    /**
     * Plan 7's F1: a page whose downloads still run closes at once but for its browser, which first loads
     * {@code about:blank}, ending the page's scripts and sound but not its downloads, and closes once they end, once.
     */
    @Test void aPageWithRunningDownloadsKeepsItsBrowserUntilTheyEnd() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Page page = page();
            var browser = new ArrayList<String>();
            var hold = new CompletableFuture<Void>();
            page.useBrowserEnding(blanked -> browser.add("about:blank"), closing -> browser.add("close"));
            page.setCloseHold(closing -> hold);
            var told = new AtomicInteger();
            page.onClose(told::incrementAndGet);
            page.close();
            assertThat(told).as("the page closed").hasValue(1);
            assertThat(page.dialogPlace()).isEmpty();
            assertThat(browser).as("its browser stays open").containsExactly("about:blank");
            hold.complete(null);
            assertThat(browser).containsExactly("about:blank", "close");
            page.close();
            assertThat(browser).as("once").containsExactly("about:blank", "close");
        });
    }

    /** Plan 7's F1: the page's close asks its hold first, before its browser loads or closes anything. */
    @Test void theHoldIsAskedBeforeTheBrowserLoadsOrClosesAnything() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var steps = new ArrayList<String>();
            Page held = page(), idle = page();
            for (Page page : List.of(held, idle)) page.useBrowserEnding(blanked -> steps.add("about:blank"), closing -> steps.add("close"));
            var hold = new CompletableFuture<Void>();
            held.setCloseHold(closing -> {
                steps.add("hold");
                return hold;
            });
            idle.setCloseHold(closing -> {
                steps.add("hold");
                return CompletableFuture.completedFuture(null);
            });
            held.close();
            assertThat(steps).containsExactly("hold", "about:blank");
            steps.clear();
            idle.close();
            assertThat(steps).as("a page with no download closes its browser at once").containsExactly("hold", "close");
        });
    }

    /**
     * A hold completed on another thread, as Chromium's completes it for a start it could not be given, closes the
     * browser on the UI thread; a hold that fails, or that throws, lets the browser close too.
     */
    @Test void aHoldCompletedElsewhereClosesTheBrowserOnTheUiThreadAndAFailedOneClosesItToo() throws Exception {
        var closes = new CopyOnWriteArrayList<String>();
        var hold = new CompletableFuture<Void>();
        var failing = new CompletableFuture<Void>();
        try (var log = new CapturedLog(Page.class.getName())) {
            SwingUtilities.invokeAndWait(() -> {
                Page page = page(), failed = page(), broken = page();
                page.useBrowserEnding(blanked -> { },
                    closing -> closes.add("closed on the UI thread: " + SwingUtilities.isEventDispatchThread()));
                page.setCloseHold(closing -> hold);
                page.close();
                failed.useBrowserEnding(blanked -> { }, closing -> closes.add("failed"));
                failed.setCloseHold(closing -> failing);
                failed.close();
                broken.useBrowserEnding(blanked -> closes.add("broken blanked"), closing -> closes.add("broken"));
                broken.setCloseHold(closing -> { throw new IllegalStateException("broken"); });
                broken.close();
            });
            assertThat(closes).as("a hold that throws closes at once").containsExactly("broken");
            assertThat(log.records()).extracting(record -> record.getThrown().getMessage()).as("logged").containsExactly("broken");
        }
        var other = new Thread(() -> {
            hold.complete(null);
            failing.completeExceptionally(new IllegalStateException("failed"));
        }, "chromium-stand-in");
        other.start();
        other.join();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(closes).containsExactly("broken", "closed on the UI thread: true", "failed");
    }

    /**
     * A held browser whose close throws is logged, as a hold that throws is: whether its hold completes on the UI thread,
     * which closes it in that turn, or elsewhere, which posts the close; neither failure reaches who completed the hold.
     */
    @Test void aHeldBrowserWhoseCloseThrowsIsLoggedOnEitherPath() throws Exception {
        var inline = new CompletableFuture<Void>();
        var posted = new CompletableFuture<Void>();
        try (var log = new CapturedLog(Page.class.getName())) {
            SwingUtilities.invokeAndWait(() -> {
                Page first = page(), second = page();
                first.useBrowserEnding(blanked -> { }, closing -> { throw new IllegalStateException("inline close"); });
                first.setCloseHold(closing -> inline);
                first.close();
                inline.complete(null);
                second.useBrowserEnding(blanked -> { }, closing -> { throw new IllegalStateException("posted close"); });
                second.setCloseHold(closing -> posted);
                second.close();
            });
            var other = new Thread(() -> posted.complete(null), "chromium-stand-in");
            other.start();
            other.join();
            SwingUtilities.invokeAndWait(() -> { });
            assertThat(log.records()).extracting(record -> record.getThrown().getMessage()).containsExactly("inline close", "posted close");
        }
    }

    /**
     * Plan 6's "no native call after stop": a held browser whose hold completes once Chromium has stopped is not closed
     * again, as Chromium's close has ended every browser.
     */
    @Test void aHeldBrowserIsNotClosedOnceChromiumHasStopped(@TempDir Path data) throws Exception {
        var chromium = new Chromium(data, () -> { }, task -> { throw new AssertionError("Chromium was started"); }, new AboutScheme(),
            new PreviewScheme(Runnable::run), cause -> { });
        SwingUtilities.invokeAndWait(() -> {
            var browser = new ArrayList<String>();
            var hold = new CompletableFuture<Void>();
            Page page = page();
            page.useBrowserEnding(blanked -> browser.add("about:blank"), closing -> browser.add("close"));
            page.setCloseHold(closing -> hold);
            chromium.close();
            PageTestAccess.make(page, chromium, null);
            page.close();
            hold.complete(null);
            assertThat(browser).containsExactly("about:blank");
        });
    }

    /**
     * Plan 7's F28: a page told that its first navigation became a download tells its item while it has committed no
     * document, and not after it closed.
     */
    @Test void aDownloadBeforeTheFirstDocumentTellsTheItemOnlyWhileThePageHasNone() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var told = new AtomicInteger();
            Page page = page();
            page.onFirstNavigationDownload(told::incrementAndGet);
            page.firstNavigationDownloaded();
            assertThat(told).hasValue(1);
            PageTestAccess.addressChanged(page, "https://example.com/");
            page.firstNavigationDownloaded();
            assertThat(told).as("a document committed").hasValue(1);
            Page fresh = page();
            fresh.onFirstNavigationDownload(told::incrementAndGet);
            fresh.close();
            fresh.firstNavigationDownloaded();
            assertThat(told).as("closed").hasValue(1);
        });
    }

    /**
     * Plan 7's F1.3: a save chooser cancelled for the page tells its downloads, after the page closed too, as a tab that
     * closes cancels the chooser it shows.
     */
    @Test void aCancelledSaveChooserIsToldEvenAfterThePageClosed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var told = new AtomicInteger();
            Page page = page();
            page.onSaveCancelled(told::incrementAndGet);
            page.saveCancelled();
            page.close();
            page.saveCancelled();
            assertThat(told).hasValue(2);
        });
    }

    private Page page() {
        var page = new Page(EXAMPLE, () -> { }, NO_FAVICONS, Runnable::run, PageContext.persistent());
        page.onCloseItem(itemCloses::incrementAndGet);
        return page;
    }
}
