package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
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
 * a download is no popup, and how many documents its main frame has started (ruling D1). A browser that is no page's,
 * as a page's DevTools' is, starts a download that holds no page (ruling F20). The answer refuses it, and the handler
 * returns false, which CEF's off-screen browsers take as a cancel, or names a {@link DownloadTarget}, and the handler
 * continues the download into it once and returns true: JCEF continues a download never answered into Chromium's own
 * folder once its callback is collected, so the handler never returns true without continuing.
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
     */
    public static void prepare(Chromium chromium, Function<DownloadStart, Optional<DownloadTarget>> before,
                               BiConsumer<DownloadProgress, Runnable> updated) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(updated, "updated");
        if (PREPARED.add(chromium))
            chromium.onClient(client -> client.addDownloadHandler(handler(before, updated, () -> chromium.state() == Chromium.State.STOPPED)));
    }

    /**
     * The handler, with its hooks.
     *
     * @param before answers each start
     * @param updated hears each update
     * @param stopped whether Chromium has stopped, after which a kept cancel touches its callback no more; any thread
     * @return the handler
     */
    static CefDownloadHandler handler(Function<DownloadStart, Optional<DownloadTarget>> before, BiConsumer<DownloadProgress, Runnable> updated,
                                      BooleanSupplier stopped) {
        return new Handler(before, updated, stopped);
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
        Page page = null;
        boolean inMemory = false, activated = false;
        long document = 0;
        if (browser instanceof PageBrowser shown) {
            page = shown.owner();
            inMemory = shown.inMemory();
            if (page != null) {
                activated = shown.view().activation().active(nowMillis);
                document = page.privacy().documents();
            }
        }
        return new DownloadStart(browser.getIdentifier(), item.getId(), suggestedName, item.getURL(), browser.getURL(), Optional.ofNullable(page),
            false, activated, document, inMemory);
    }

    /**
     * Answers {@code start} through {@code before}: continues it once into the target given and returns true, or, for
     * no target, and for a hook that throws, returns false and never continues it.
     *
     * @param start the start
     * @param callback Chromium's callback for it
     * @param before the hook
     * @return whether the download goes on
     */
    static boolean answer(DownloadStart start, CefBeforeDownloadCallback callback, Function<DownloadStart, Optional<DownloadTarget>> before) {
        Optional<DownloadTarget> target;
        try {
            target = before.apply(start);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A download could not be answered", failure);
            return false;
        }
        if (target == null || target.isEmpty()) return false;
        callback.Continue(target.get().file().toString(), target.get().ask());
        return true;
    }

    /** The one handler; Chromium's thread. */
    private static final class Handler extends CefDownloadHandlerAdapter {
        private final Function<DownloadStart, Optional<DownloadTarget>> before;
        private final BiConsumer<DownloadProgress, Runnable> updated;
        private final BooleanSupplier stopped;

        Handler(Function<DownloadStart, Optional<DownloadTarget>> before, BiConsumer<DownloadProgress, Runnable> updated, BooleanSupplier stopped) {
            this.before = before;
            this.updated = updated;
            this.stopped = stopped;
        }

        @Override public boolean onBeforeDownload(CefBrowser browser, CefDownloadItem item, String suggestedName, CefBeforeDownloadCallback callback) {
            return answer(start(browser, item, suggestedName, System.currentTimeMillis()), callback, before);
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
