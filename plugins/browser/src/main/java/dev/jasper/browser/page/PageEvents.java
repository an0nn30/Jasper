package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import java.awt.EventQueue;
import java.util.function.Consumer;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;

/**
 * The client's one display handler and one load handler. JCEF keeps the first handler of each kind a client is given,
 * so these two serve every page: each callback goes to the {@link Page} of the browser it is about. Chromium calls them
 * on its own UI thread (on macOS, AppKit's main thread), never Swing's, so what a page shows changes on the UI thread.
 * The hover address goes straight to the canvas, which takes it from any thread.
 */
final class PageEvents {
    private static final Consumer<CefClient> INSTALL = PageEvents::install;

    private PageEvents() { }

    /** Has Chromium install the two handlers on its client before any page is made on it; once, however often asked. */
    static void prepare(Chromium chromium) {
        chromium.onClient(INSTALL);
    }

    private static void install(CefClient client) {
        client.addDisplayHandler(new Display());
        client.addLoadHandler(new Load());
    }

    /** The page {@code browser} shows; null for a browser that is not a page's. */
    private static Page page(CefBrowser browser) {
        return browser instanceof PageBrowser page ? page.owner() : null;
    }

    private static void later(CefBrowser browser, Consumer<Page> event) {
        Page page = page(browser);
        if (page != null) EventQueue.invokeLater(() -> event.accept(page));
    }

    /** The main frame's address and the page's title, and the address of the link under the pointer. */
    private static final class Display extends CefDisplayHandlerAdapter {
        @Override public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
            if (frame != null && frame.isMain()) later(browser, page -> page.addressChanged(url));
        }

        @Override public void onTitleChange(CefBrowser browser, String title) {
            later(browser, page -> page.titleChanged(title));
        }

        @Override public void onStatusMessage(CefBrowser browser, String value) {
            Page page = page(browser);
            if (page != null) page.hovered(value);
        }
    }

    /** Whether the page loads and where it can go, and each main-frame load's start. */
    private static final class Load extends CefLoadHandlerAdapter {
        @Override public void onLoadingStateChange(CefBrowser browser, boolean loading, boolean canGoBack, boolean canGoForward) {
            later(browser, page -> page.loadingChanged(loading, canGoBack, canGoForward));
        }

        @Override public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transition) {
            if (frame == null || !frame.isMain()) return;
            String url = frame.getURL();
            later(browser, page -> page.loadStarted(url));
        }
    }
}
