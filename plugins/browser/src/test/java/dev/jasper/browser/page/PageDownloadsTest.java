package dev.jasper.browser.page;

import dev.jasper.browser.permissions.AudioMode;
import dev.jasper.browser.store.CapturedLog;
import java.awt.Point;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefDownloadItemCallback;
import org.cef.handler.CefDownloadHandler;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The client's one download handler (spec section 10), without Chromium: its item and callbacks are test implementations
 * of JCEF's interfaces, and its browsers the {@code page} package's stand-ins, so nothing starts. A start Jasper refuses
 * returns false and is never continued, which CEF cancels; one it accepts is continued once, into the file and with the
 * ask it was given, and returns true, as CEF continues an unanswered download into its own folder once the callback is
 * collected; a start whose answer throws is also abandoned, so what was kept for it is given back. Each update reaches
 * the {@code downloads} package in plain values with a cancel of its own. No native method of a stand-in browser is
 * called.
 */
class PageDownloadsTest {
    private static final Path FILE = Path.of("/tmp/jasper-downloads-test/report.pdf");
    private final List<DownloadStart> abandoned = new CopyOnWriteArrayList<>();

    @Test void aRefusedStartReturnsFalseAndIsNeverContinued() {
        var callback = new BeforeCallback();
        var starts = new ArrayList<DownloadStart>();
        boolean handled = PageDownloads.answer(start(Optional.empty()), callback, start -> {
            starts.add(start);
            return Optional.empty();
        }, abandoned::add);
        assertThat(handled).as("false: CEF cancels it").isFalse();
        assertThat(starts).hasSize(1);
        assertThat(callback.calls).as("never continued").isEmpty();
        assertThat(abandoned).as("refused, not abandoned: nothing was kept").isEmpty();
    }

    @Test void anAcceptedStartIsContinuedOnceWithThePathAndTheAskAndReturnsTrue() {
        var callback = new BeforeCallback();
        assertThat(PageDownloads.answer(start(Optional.empty()), callback, start -> Optional.of(new DownloadTarget(FILE, false)), abandoned::add))
            .isTrue();
        assertThat(callback.calls).containsExactly(FILE + "|false");
        var asking = new BeforeCallback();
        assertThat(PageDownloads.answer(start(Optional.empty()), asking, start -> Optional.of(new DownloadTarget(FILE, true)), abandoned::add))
            .isTrue();
        assertThat(asking.calls).containsExactly(FILE + "|true");
        assertThat(abandoned).isEmpty();
    }

    /**
     * Review finding 9: a start whose answer throws, before or after the downloads kept it, is refused, never continued,
     * and abandoned, so what was kept for it, its name and its page's count, is given back.
     */
    @Test void aStartWhoseAnswerOrContinueThrowsIsRefusedAndAbandoned() {
        try (var log = new CapturedLog(PageDownloads.class.getName())) {
            var callback = new BeforeCallback();
            Function<DownloadStart, Optional<DownloadTarget>> broken = start -> { throw new IllegalStateException("broken"); };
            DownloadStart first = start(Optional.empty());
            assertThat(PageDownloads.answer(first, callback, broken, abandoned::add)).isFalse();
            assertThat(callback.calls).isEmpty();
            CefBeforeDownloadCallback refusing = (path, showDialog) -> { throw new IllegalStateException("no native callback"); };
            DownloadStart second = start(Optional.empty());
            assertThat(PageDownloads.answer(second, refusing, start -> Optional.of(new DownloadTarget(FILE, false)), abandoned::add)).isFalse();
            assertThat(abandoned).containsExactly(first, second);
            assertThat(log.records()).extracting(record -> record.getThrown().getMessage()).as("each logged")
                .containsExactly("broken", "no native callback");
        }
    }

    /**
     * Task 3's re-review: an {@code abandoned} hook that throws too is logged, and the start is still refused, so CEF
     * cancels it.
     */
    @Test void aStartWhoseAbandonThrowsTooIsStillRefused() {
        try (var log = new CapturedLog(PageDownloads.class.getName())) {
            var callback = new BeforeCallback();
            assertThat(PageDownloads.answer(start(Optional.empty()), callback, start -> { throw new IllegalStateException("broken"); },
                start -> { throw new IllegalStateException("not abandoned"); })).isFalse();
            assertThat(callback.calls).isEmpty();
            assertThat(log.records()).extracting(record -> record.getThrown().getMessage()).containsExactly("broken", "not abandoned");
        }
    }

    /**
     * Spec section 10 and ruling F32: Save Link As… arms the page's ask with its address, and the page's start of that
     * address asks, once; a page that armed nothing, and a browser that is no page's, go without asking.
     */
    @Test void aPagesStartOfTheAddressItsSaveArmedAsksOnce() throws Exception {
        long now = System.currentTimeMillis();
        Page page = onUi(() -> new Page(URI.create("https://example.com/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run,
            PageContext.persistent()));
        String link = "https://example.com/report.pdf";
        assertThat(PageDownloads.start(1, "", Optional.of(page), false, 1, link, "report.pdf", now).ask()).as("nothing armed").isFalse();
        onUi(() -> {
            page.askNextDownload(link);
            return null;
        });
        DownloadStart saved = PageDownloads.start(1, "https://example.com/", Optional.of(page), false, 2, link, "report.pdf",
            System.currentTimeMillis());
        assertThat(saved.ask()).isTrue();
        assertThat(PageDownloads.start(1, "", Optional.of(page), false, 3, link, "report.pdf", System.currentTimeMillis()).ask())
            .as("once").isFalse();
        assertThat(PageDownloads.start(1, "", Optional.empty(), false, 4, link, "report.pdf", now).ask()).as("no page's").isFalse();
        onUi(() -> {
            page.close();
            page.askNextDownload(link);
            return null;
        });
        assertThat(PageDownloads.start(1, "", Optional.of(page), false, 5, link, "report.pdf", System.currentTimeMillis()).ask())
            .as("a closed page arms nothing").isFalse();
    }

    /**
     * Rulings F21, D1 and F7: a page's start carries the browser's identifier and the item's id, the names and
     * addresses, its page, whether the page is activated now, which it reads without consuming the activation, the
     * document its main frame shows, and whether its group keeps its data in memory. Its own browser names the page and
     * the group; the values are read without a native call.
     */
    @Test void aPagesStartCarriesItsPageItsActivationItsDocumentAndItsStore() throws Exception {
        long now = System.currentTimeMillis();
        Page page = onUi(() -> new Page(URI.create("https://example.com/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run,
            PageContext.memory()));
        page.privacy().documentStarted(AudioMode.ALLOW);
        page.privacy().documentStarted(AudioMode.ALLOW);
        PageBrowser browser = PageTestAccess.pageBrowser(page);
        assertThat(PageDownloads.page(browser)).containsSame(page);
        assertThat(PageDownloads.inMemory(browser)).isTrue();
        DownloadStart quiet = PageDownloads.start(7, "https://example.com/", Optional.of(page), true, 7, "https://example.com/report.pdf",
            "report.pdf", now);
        assertThat(quiet).isEqualTo(new DownloadStart(7, 7, "report.pdf", "https://example.com/report.pdf", "https://example.com/",
            Optional.of(page), false, false, 2, true));
        Activation activation = ((PageView) page.view()).activation();
        activation.activate(now - 1_000);
        DownloadStart clicked = PageDownloads.start(7, "", Optional.of(page), true, 8, "https://example.com/b.zip", "b.zip", now);
        assertThat(clicked.activated()).isTrue();
        assertThat(activation.active(now)).as("read, not consumed: a download is no popup").isTrue();
        assertThat(PageDownloads.start(7, "", Optional.of(page), true, 9, "https://example.com/c.zip", "c.zip", now + 5_000).activated())
            .as("Chromium's 5 s window").isFalse();
        onUi(() -> {
            page.close();
            return null;
        });
    }

    /**
     * Ruling D20: a load Jasper starts for the user, as the address field's, gives the page's downloads a new document,
     * as a document the main frame starts does; nothing else the page does changes it.
     */
    @Test void aLoadJasperStartsGivesThePagesDownloadsANewDocument() throws Exception {
        long now = System.currentTimeMillis();
        Page page = onUi(() -> new Page(URI.create("https://example.com/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run,
            PageContext.persistent()));
        long before = PageDownloads.start(1, "", Optional.of(page), false, 1, "https://example.com/a", "a", now).document();
        assertThat(PageDownloads.start(1, "", Optional.of(page), false, 2, "https://example.com/b", "b", now).document()).isEqualTo(before);
        onUi(() -> {
            page.load(URI.create("https://example.com/report.pdf"));
            return null;
        });
        long loaded = PageDownloads.start(1, "", Optional.of(page), false, 3, "https://example.com/c", "c", now).document();
        assertThat(loaded).isGreaterThan(before);
        page.privacy().documentStarted(AudioMode.ALLOW);
        assertThat(PageDownloads.start(1, "", Optional.of(page), false, 4, "https://example.com/d", "d", now).document()).isGreaterThan(loaded);
        onUi(() -> {
            page.close();
            return null;
        });
    }

    /**
     * Ruling F20: a browser that is no page's starts a download that holds no page: a page's DevTools, whose group is
     * the page's, and any other browser, which keeps nothing in memory.
     */
    @Test void aDevToolsOrAnyOtherBrowsersStartHoldsNoPage() throws Exception {
        long now = System.currentTimeMillis();
        Page page = onUi(() -> new Page(URI.create("https://example.com/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run,
            PageContext.memory()));
        DevTools tools = onUi(() -> PageTestAccess.devTools(page, new Point(1, 1)));
        PageBrowser devTools = PageTestAccess.devToolsBrowser(tools);
        assertThat(PageDownloads.page(devTools)).isEmpty();
        assertThat(PageDownloads.inMemory(devTools)).as("the inspected page's group").isTrue();
        DownloadStart saved = PageDownloads.start(3, "", Optional.empty(), true, 3, "blob:devtools://x", "a.har", now);
        assertThat(saved.activated()).isFalse();
        assertThat(saved.document()).isZero();
        CefBrowser other = browser(42, "https://other.example/");
        assertThat(PageDownloads.page(other)).isEmpty();
        assertThat(PageDownloads.inMemory(other)).isFalse();
        DownloadStart start = PageDownloads.start(other, new Item(4, "https://other.example/x.bin"), "x.bin", now);
        assertThat(start).isEqualTo(new DownloadStart(42, 4, "x.bin", "https://other.example/x.bin", "https://other.example/", Optional.empty(),
            false, false, 0, false));
        onUi(() -> {
            page.close();
            return null;
        });
    }

    /** The handler answers a start through the hook, and gives the start the browser and the item it was asked for. */
    @Test void theHandlerAnswersAStartThroughItsHook() {
        var starts = new CopyOnWriteArrayList<DownloadStart>();
        CefDownloadHandler handler = PageDownloads.handler(start -> {
            starts.add(start);
            return start.id() == 1 ? Optional.of(new DownloadTarget(FILE, false)) : Optional.empty();
        }, (progress, cancel) -> { }, abandoned::add, () -> false);
        var accepted = new BeforeCallback();
        assertThat(handler.onBeforeDownload(browser(5, "https://example.com/"), new Item(1, "https://example.com/r.pdf"), "r.pdf", accepted))
            .isTrue();
        var refused = new BeforeCallback();
        assertThat(handler.onBeforeDownload(browser(5, "https://example.com/"), new Item(2, "https://example.com/s.pdf"), "s.pdf", refused))
            .isFalse();
        assertThat(accepted.calls).containsExactly(FILE + "|false");
        assertThat(refused.calls).isEmpty();
        assertThat(starts).extracting(DownloadStart::browser, DownloadStart::id, DownloadStart::suggestedName)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(5, 1, "r.pdf"), org.assertj.core.groups.Tuple.tuple(5, 2, "s.pdf"));
    }

    /**
     * Ruling F21: an update reaches its hook as plain values, keyed by the browser's identifier and the item's id, with a
     * cancel that cancels the item's own callback each time it runs; an item that is no longer valid tells nothing.
     */
    @Test void anUpdateReachesItsHookInPlainValuesWithACancelOfItsOwn() {
        var updates = new CopyOnWriteArrayList<DownloadProgress>();
        var cancels = new CopyOnWriteArrayList<Runnable>();
        CefDownloadHandler handler = PageDownloads.handler(start -> Optional.empty(), (progress, cancel) -> {
            updates.add(progress);
            cancels.add(cancel);
        }, abandoned::add, () -> false);
        var item = new Item(6, "https://example.com/r.pdf");
        item.inProgress = true;
        item.received = 42;
        item.total = 100;
        item.fullPath = "/tmp/r.pdf.crdownload";
        var callback = new ItemCallback();
        handler.onDownloadUpdated(browser(9, "https://example.com/"), item, callback);
        assertThat(updates).containsExactly(new DownloadProgress(9, 6, true, false, false, 42, 100, "/tmp/r.pdf.crdownload"));
        assertThat(callback.cancels).isZero();
        cancels.getFirst().run();
        assertThat(callback.cancels).isEqualTo(1);
        var gone = new Item(6, "https://example.com/r.pdf");
        gone.valid = false;
        handler.onDownloadUpdated(browser(9, "https://example.com/"), gone, new ItemCallback());
        assertThat(updates).as("an invalid item tells nothing").hasSize(1);
        var done = new Item(6, "https://example.com/r.pdf");
        done.complete = true;
        done.received = 100;
        done.total = 100;
        done.fullPath = "/tmp/r.pdf";
        handler.onDownloadUpdated(browser(9, "https://example.com/"), done, new ItemCallback());
        assertThat(updates.getLast()).isEqualTo(new DownloadProgress(9, 6, false, true, false, 100, 100, "/tmp/r.pdf"));
    }

    /** Plan 6's "no native call after stop": once Chromium has stopped, a kept cancel touches the callback no more. */
    @Test void onceChromiumHasStoppedAKeptCancelTouchesNothing() {
        var stopped = new AtomicBoolean();
        var cancels = new CopyOnWriteArrayList<Runnable>();
        CefDownloadHandler handler = PageDownloads.handler(start -> Optional.empty(), (progress, cancel) -> cancels.add(cancel), abandoned::add,
            stopped::get);
        var item = new Item(1, "https://example.com/r.pdf");
        item.inProgress = true;
        var callback = new ItemCallback();
        handler.onDownloadUpdated(browser(1, ""), item, callback);
        stopped.set(true);
        cancels.getFirst().run();
        assertThat(callback.cancels).isZero();
    }

    private static DownloadStart start(Optional<Page> page) {
        return new DownloadStart(1, 2, "report.pdf", "https://example.com/report.pdf", "https://example.com/", page, false, false, 1, false);
    }

    /** A browser that is no page's, as JCEF's own would be: only its identifier and its address answer. */
    private static CefBrowser browser(int identifier, String url) {
        return (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(), new Class<?>[] { CefBrowser.class }, (proxy, method, args) ->
            switch (method.getName()) {
                case "getIdentifier" -> identifier;
                case "getURL" -> url;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "browser " + identifier;
                default -> throw new AssertionError("A download handler asked the browser for " + method.getName());
            });
    }

    private static <T> T onUi(Callable<T> task) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Exception>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Exception thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() != null) throw failure.get();
        return result.get();
    }

    /** What the continue callback was told, as {@code path|ask}. */
    private static final class BeforeCallback implements CefBeforeDownloadCallback {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override public void Continue(String path, boolean showDialog) {
            calls.add(path + "|" + showDialog);
        }
    }

    /** How often the item's callback was cancelled. */
    private static final class ItemCallback implements CefDownloadItemCallback {
        volatile int cancels;

        @Override public void cancel() {
            cancels++;
        }

        @Override public void pause() {
            throw new AssertionError("paused");
        }

        @Override public void resume() {
            throw new AssertionError("resumed");
        }
    }

    /** A download item of plain fields. */
    private static final class Item implements CefDownloadItem {
        final int id;
        final String url;
        boolean valid = true, inProgress, complete, canceled;
        long received, total;
        String fullPath = "";

        Item(int id, String url) {
            this.id = id;
            this.url = url;
        }

        @Override public boolean isValid() { return valid; }
        @Override public boolean isInProgress() { return inProgress; }
        @Override public boolean isComplete() { return complete; }
        @Override public boolean isCanceled() { return canceled; }
        @Override public long getCurrentSpeed() { return 0; }
        @Override public int getPercentComplete() { return total > 0 ? (int) (received * 100 / total) : -1; }
        @Override public long getTotalBytes() { return total; }
        @Override public long getReceivedBytes() { return received; }
        @Override public Date getStartTime() { return new Date(0); }
        @Override public Date getEndTime() { return new Date(0); }
        @Override public String getFullPath() { return fullPath; }
        @Override public int getId() { return id; }
        @Override public String getURL() { return url; }
        @Override public String getSuggestedFileName() { return "suggested"; }
        @Override public String getContentDisposition() { return ""; }
        @Override public String getMimeType() { return "application/octet-stream"; }
    }
}
