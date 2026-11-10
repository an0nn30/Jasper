package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import java.awt.EventQueue;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A page's own part of its downloads (plan 7), which its {@link Page} holds and drives: whether its next download asks
 * where it goes ({@link DownloadAsk}, ruling F32), how a download starts in its browser, and its close hold (ruling F1),
 * which keeps the page's browser, showing {@code about:blank}, while one of its downloads runs, and closes it once they
 * end, on the UI thread, once, unless Chromium has stopped. How a download starts and how the browser is blanked and
 * closed are seams a test replaces. It reaches the browser only through its page, and logs as its page.
 */
final class PageDownloadState {
    private static final System.Logger LOG = System.getLogger(Page.class.getName());
    // Plan 7's F32: whether the page's next download asks where it goes; armed on the UI thread, taken on Chromium's.
    private final DownloadAsk ask = new DownloadAsk();
    // Given the page and an address, has Chromium download it: through its browser, or a test's.
    private BiConsumer<Page, String> downloader = PageDownloadState::downloadInBrowser;
    // Plan 7's F1: given the page as it closes, what its browser waits for before it closes: its downloads' end.
    private Function<Page, CompletableFuture<Void>> hold = page -> CompletableFuture.completedFuture(null);
    // Given the page, loads about:blank in its browser, and closes its browser: Chromium's, or a test's recording ones.
    private Consumer<Page> blanker = PageDownloadState::blankBrowser, closer = PageDownloadState::closeBrowser;
    // The plugin's Chromium, once the page's client and context are ready; read as a held browser closes, as Chromium
    // may have stopped by then.
    private Chromium engine;

    /** Whether the page's next download asks where it goes, which the download handler takes. Any thread. */
    DownloadAsk ask() {
        return ask;
    }

    /** Has the page's next download of {@code url} ask where it goes, from now. */
    void askNext(String url) {
        ask.arm(url, System.currentTimeMillis());
    }

    /** Has Chromium download {@code url} in {@code page}'s browser. */
    void start(Page page, String url) {
        downloader.accept(page, url);
    }

    /** Has {@code replacement} decide when the page's browser closes, from now on. */
    void hold(Function<Page, CompletableFuture<Void>> replacement) {
        hold = Objects.requireNonNull(replacement, "hold");
    }

    /** The page's client and context are ready, from {@code chromium}, which a held browser's close asks whether it stopped. */
    void madeBy(Chromium chromium) {
        engine = chromium;
    }

    /** What {@code page}'s close hold answers, asked once as the page closes; complete when the hold throws. */
    CompletableFuture<Void> held(Page page) {
        try {
            return Objects.requireNonNull(hold.apply(page), "held");
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's downloads could not hold its browser's close", failure);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * The closed {@code page}'s browser ends: at once when its hold, {@code held}, is complete; else it loads
     * {@code about:blank} now, and closes once the hold completes.
     */
    void end(Page page, CompletableFuture<Void> held) {
        if (held.isDone()) closer.accept(page);
        else {
            blanker.accept(page);
            // Downloads complete a hold on the UI thread, or on Chromium's for a start it could not be given.
            held.whenComplete((done, failure) -> onUiThread(() -> closeHeld(page)));
        }
    }

    /**
     * The page's close hold completed: its browser closes, unless Chromium has stopped, whose close ended every browser,
     * so no native call is made after it (plan 6). A close that throws is logged, as a hold that throws is: run in the
     * turn that completed the hold, its failure would otherwise vanish into the hold's dependent. UI thread.
     */
    private void closeHeld(Page page) {
        Chromium chromium = engine;
        if (chromium != null && chromium.state() == Chromium.State.STOPPED) return;
        try {
            closer.accept(page);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's held browser could not be closed", failure);
        }
    }

    /** Runs {@code task} now on the UI thread, else posts it there. */
    private static void onUiThread(Runnable task) {
        if (EventQueue.isDispatchThread()) task.run();
        else EventQueue.invokeLater(task);
    }

    /** Loads {@code about:blank} in the page's browser, a held one; nothing before it is ready. */
    private static void blankBrowser(Page page) {
        PageBrowser ready = page.readyBrowser();
        if (ready != null) ready.loadURL("about:blank");
    }

    /** Closes the page's browser, which ends only this page; nothing before it is made. */
    private static void closeBrowser(Page page) {
        PageBrowser made = page.browser();
        if (made != null) made.close(true);
    }

    /** Has Chromium download {@code url} in the page's browser; nothing before it is ready. */
    private static void downloadInBrowser(Page page, String url) {
        PageBrowser ready = page.readyBrowser();
        if (ready != null) ready.startDownload(url);
    }

    /** Has the page start its downloads with {@code replacement} from now on, as a test's recording one. */
    void useDownloader(BiConsumer<Page, String> replacement) {
        downloader = Objects.requireNonNull(replacement, "replacement");
    }

    /**
     * Has the page load {@code about:blank} in its browser with {@code blank}, and close its browser with {@code close},
     * from now on, as a test's recording ones.
     */
    void useBrowserEnding(Consumer<Page> blank, Consumer<Page> close) {
        blanker = Objects.requireNonNull(blank, "blank");
        closer = Objects.requireNonNull(close, "close");
    }
}
