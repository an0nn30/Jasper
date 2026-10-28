package dev.jasper.browser.links;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.links.LinkGesture.Where;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageBrowser;
import dev.jasper.browser.page.PageView;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.EventQueue;
import java.util.function.Consumer;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;

/**
 * Where a page's links and popups go (spec section 8): the client's one request handler, for a link Chromium would open
 * elsewhere ({@code onOpenURLFromTab}: a modified or middle click), and a life span handler, for a popup
 * ({@code onBeforePopup}: a {@code target=_blank} link, or a {@code window.open} that reaches Chromium). Chromium gives
 * neither a disposition nor, for a popup, whether the user asked for it, so {@link LinkRoute} decides from the press the
 * page's {@link PageView} last forwarded and the page's activation, which a popup consumes. Chromium calls both on its
 * own UI thread, and each answers at once that Chromium makes nothing itself; what Jasper opens instead is posted to
 * Swing's: the link loaded in its page, or an item of its own that the page's item opens beside itself. The hover
 * address, from {@code onStatusMessage}, is the {@code page} package's display handler's.
 *
 * <p>JCEF keeps a client's first request handler, so this is the only one: what else a request handler is asked goes to
 * its defaults. Life span handlers are a list, which JetBrains' runtime adds its own to, so this adds one and removes
 * none.
 */
public final class PageNavigation {
    private static final boolean MAC_OS = System.getProperty("os.name", "").startsWith("Mac");
    private static final Consumer<CefClient> INSTALL = PageNavigation::install;

    private PageNavigation() { }

    /**
     * Has Chromium install the handlers on its client before any page is made on it; once, however often asked. Call it
     * when a page first asks for Chromium, never at the plugin's start: this class names Chromium's types.
     *
     * @param chromium the plugin's Chromium
     */
    public static void prepare(Chromium chromium) {
        chromium.onClient(INSTALL);
    }

    private static void install(CefClient client) {
        client.addRequestHandler(new Links());
        client.addLifeSpanHandler(new Popups());
    }

    /** Links Chromium would open in a new tab or window, or download: Jasper places them itself. */
    private static final class Links extends CefRequestHandlerAdapter {
        @Override public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String url, boolean userGesture) {
            if (browser instanceof PageBrowser page)
                LinkRoute.link(url, page.view().lastPress(), userGesture, System.currentTimeMillis(), MAC_OS)
                    .ifPresent(where -> EventQueue.invokeLater(() -> go(page, url, where)));
            return true;
        }
    }

    /** Popups: Jasper opens the ones the user asked for as items, and Chromium makes none. */
    private static final class Popups extends CefLifeSpanHandlerAdapter {
        @Override public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String url, String name) {
            if (browser instanceof PageBrowser page) {
                PageView view = page.view();
                LinkRoute.popup(url, view.lastPress(), view.activation(), System.currentTimeMillis(), MAC_OS)
                    .ifPresent(where -> EventQueue.invokeLater(() -> go(page, url, where)));
            }
            return true;
        }
    }

    /** Sends {@code url} where it goes, from the page {@code browser} shows. UI thread. */
    private static void go(PageBrowser browser, String url, Where where) {
        Page page = browser.owner();
        if (page == null) return;
        switch (where) {
            case SAME_ITEM -> page.follow(url);
            case NEXT_TAB -> page.openItem(url, TabPlacement.NEXT_TAB);
            case SPLIT -> {
                PageView view = browser.view();
                page.openItem(url, LinkRoute.split(view.getWidth(), view.getHeight()));
            }
        }
    }
}
