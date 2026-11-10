package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefDownloadItemCallback;
import org.cef.handler.CefDownloadHandler;
import org.cef.handler.CefDownloadHandlerAdapter;

/**
 * Chromium's downloads (spec section 10): the pages' client's one download handler, which hands each download Chromium
 * starts, and each of its updates, to the {@code downloads} package in plain values, on Chromium's thread. As Chromium
 * asks where a download goes ({@code onBeforeDownload}), the handler reads a {@link DownloadStart}: the browser's
 * identifier and Chromium's id for it (ruling F21), the suggested name, the file's address, the address the browser
 * shows, and for a page's own browser the page, whether the page has a user activation, read without consuming it, as
 * a download is no popup, how many documents its main frame has started (ruling D1), and whether the download asks
 * where it goes, as Save Link As… and Save Image As… ask, taking the page's {@link DownloadAsk}. A browser that is no
 * page's, as a page's DevTools' is, starts a download that holds no page (ruling F20). The answer refuses it, and the
 * handler returns false, which CEF's off-screen browsers take as a cancel, or names a {@link DownloadTarget}, and the
 * handler continues the download into it once and returns true: JCEF continues a download never answered into
 * Chromium's own folder once its callback is collected, so the handler never returns true without continuing. A start
 * whose answer throws, or cannot be continued, is refused too, and abandoned, so what was kept for it is given back; an
 * abandon that throws as well is logged. A load Jasper starts for the user gives the page's downloads a new document
 * (ruling D20).
 *
 * <p>Each update ({@code onDownloadUpdated}) reaches the {@code downloads} package as a {@link DownloadProgress} with a
 * cancel of its item's own, which cancels nothing once Chromium has stopped (plan 6's "no native call after stop"); an
 * item that is no longer valid tells nothing. JCEF calls neither for a browser that has gone. JCEF keeps a client's
 * first download handler, so this is the only one; the Markdown previews' client has none, and CEF cancels their
 * downloads.
 */
public final class PageDownloads {
    private static final System.Logger LOG = System.getLogger(PageDownloads.class.getName());
    // The Chromiums given the handler, held weakly: each client gets it once, however often a page starts.
    private static final Set<Chromium> PREPARED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private PageDownloads() { }

    /**
     * Has Chromium install the download handler on its client before any page is made on it; once, however often asked,
     * with the hooks of the first call. Call it when a page first asks for Chromium, never at the plugin's start: this
     * class names Chromium's types.
     *
     * @param chromium the plugin's Chromium
     * @param before answers a download Chromium starts, on Chromium's thread: where it goes, or empty to refuse it
     * @param updated hears each update of a download it gave a target, with what cancels it, on Chromium's thread
     * @param abandoned hears a start whose answer threw, or could not be given to Chromium, which then cancels it, on
     *     Chromium's thread: what {@code before} kept for it is given back
     */
    public static void prepare(Chromium chromium, Function<DownloadStart, Optional<DownloadTarget>> before,
                               BiConsumer<DownloadProgress, Runnable> updated, Consumer<DownloadStart> abandoned) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(updated, "updated");
        Objects.requireNonNull(abandoned, "abandoned");
        if (PREPARED.add(chromium)) chromium.onClient(client -> client.addDownloadHandler(handler(before, updated, abandoned,
            () -> chromium.state() == Chromium.State.STOPPED)));
    }

    /**
     * The handler, with its hooks.
     *
     * @param before answers each start
     * @param updated hears each update
     * @param abandoned hears each start whose answer threw or could not be given
     * @param stopped whether Chromium has stopped, after which a kept cancel touches its callback no more; any thread
     * @return the handler
     */
    static CefDownloadHandler handler(Function<DownloadStart, Optional<DownloadTarget>> before, BiConsumer<DownloadProgress, Runnable> updated,
                                      Consumer<DownloadStart> abandoned, BooleanSupplier stopped) {
        return new Handler(before, updated, abandoned, stopped);
    }

    /**
     * A download Chromium starts in {@code browser}, as {@code downloads} reads it. Chromium's thread.
     *
     * @param browser the browser that started it
     * @param item the download
     * @param suggestedName the name Chromium suggests for its file
     * @param nowMillis the time now, for the page's activation
     * @return the start
     */
    static DownloadStart start(CefBrowser browser, CefDownloadItem item, String suggestedName, long nowMillis) {
        return start(browser.getIdentifier(), browser.getURL(), page(browser), inMemory(browser), item.getId(), item.getURL(), suggestedName,
            nowMillis);
    }

    /**
     * A download a browser starts, from plain values: for a page's own browser, whether the page is activated now, read
     * without consuming the activation, the document its downloads count in ({@link Page#downloadDocument}), and whether
     * it asks where it goes, which takes the page's ask ({@link DownloadAsk#take}). Chromium's thread.
     *
     * @param browser the browser's identifier
     * @param pageUrl the address the browser shows
     * @param page the page whose own browser it is, if any
     * @param inMemory whether the browser keeps its data in memory
     * @param id Chromium's id for the download
     * @param url the file's address
     * @param suggestedName the name Chromium suggests
     * @param nowMillis the time now
     * @return the start
     */
    static DownloadStart start(int browser, String pageUrl, Optional<Page> page, boolean inMemory, int id, String url, String suggestedName,
                               long nowMillis) {
        boolean activated = page.map(shown -> shown.activation().active(nowMillis)).orElse(false);
        long document = page.map(Page::downloadDocument).orElse(0L);
        boolean ask = page.map(shown -> shown.downloadAsk().take(url, nowMillis)).orElse(false);
        return new DownloadStart(browser, id, suggestedName, url, pageUrl, page, ask, activated, document, inMemory);
    }

    /**
     * The page whose own browser {@code browser} is; none for a page's DevTools and any other browser (ruling F20).
     *
     * @param browser the browser
     * @return the page
     */
    static Optional<Page> page(CefBrowser browser) {
        return browser instanceof PageBrowser shown ? Optional.ofNullable(shown.owner()) : Optional.empty();
    }

    /**
     * Whether {@code browser} keeps its data in memory: a /dev/null page's, or its DevTools'.
     *
     * @param browser the browser
     * @return whether it does
     */
    static boolean inMemory(CefBrowser browser) {
        return browser instanceof PageBrowser shown && shown.inMemory();
    }

    /**
     * Answers {@code start} through {@code before}: continues it once into the target given and returns true; returns
     * false and never continues it for no target; and for a hook or a continue that throws returns false, which CEF
     * takes as a cancel, and tells {@code abandoned}, so what was kept for the start is given back; an {@code abandoned}
     * that throws too is logged.
     *
     * @param start the start
     * @param callback Chromium's callback for it
     * @param before the hook
     * @param abandoned hears the start whose answer threw
     * @return whether the download goes on
     */
    static boolean answer(DownloadStart start, CefBeforeDownloadCallback callback, Function<DownloadStart, Optional<DownloadTarget>> before,
                          Consumer<DownloadStart> abandoned) {
        try {
            Optional<DownloadTarget> target = before.apply(start);
            if (target == null || target.isEmpty()) return false;
            callback.Continue(target.get().file().toString(), target.get().ask());
            return true;
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A download could not be answered", failure);
            try {
                abandoned.accept(start);
            } catch (RuntimeException alsoFailed) {
                LOG.log(System.Logger.Level.WARNING, "A download that could not be answered could not be abandoned", alsoFailed);
            }
            return false;
        }
    }

    /** The one handler; Chromium's thread. */
    private static final class Handler extends CefDownloadHandlerAdapter {
        private final Function<DownloadStart, Optional<DownloadTarget>> before;
        private final BiConsumer<DownloadProgress, Runnable> updated;
        private final Consumer<DownloadStart> abandoned;
        private final BooleanSupplier stopped;

        Handler(Function<DownloadStart, Optional<DownloadTarget>> before, BiConsumer<DownloadProgress, Runnable> updated,
                Consumer<DownloadStart> abandoned, BooleanSupplier stopped) {
            this.before = before;
            this.updated = updated;
            this.abandoned = abandoned;
            this.stopped = stopped;
        }

        @Override public boolean onBeforeDownload(CefBrowser browser, CefDownloadItem item, String suggestedName, CefBeforeDownloadCallback callback) {
            return answer(start(browser, item, suggestedName, System.currentTimeMillis()), callback, before, abandoned);
        }

        @Override public void onDownloadUpdated(CefBrowser browser, CefDownloadItem item, CefDownloadItemCallback callback) {
            if (!item.isValid()) return;
            var progress = new DownloadProgress(browser.getIdentifier(), item.getId(), item.isInProgress(), item.isComplete(), item.isCanceled(),
                item.getReceivedBytes(), item.getTotalBytes(), item.getFullPath());
            try {
                updated.accept(progress, () -> {
                    if (!stopped.getAsBoolean()) callback.cancel();
                });
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "A download's update could not be handled", failure);
            }
        }
    }
}
