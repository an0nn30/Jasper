package dev.jasper.browser.links;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.links.LinkGesture.Where;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageBrowser;
import dev.jasper.browser.page.PageView;
import dev.jasper.browser.page.ServerCertificate;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.EventQueue;
import java.util.Optional;
import java.util.function.Consumer;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCallback;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;
import org.cef.security.CefSSLInfo;

/**
 * Where a page's links and popups go (spec section 8): the client's one request handler, for a link Chromium would open
 * elsewhere ({@code onOpenURLFromTab}: a modified or middle click), and a life span handler, for a popup
 * ({@code onBeforePopup}: a {@code target=_blank} link, or a {@code window.open} that reaches Chromium). Chromium gives
 * neither a disposition nor, for a popup, whether the user asked for it, so {@link LinkRoute} decides from the press the
 * page's {@link PageView} last forwarded and the page's activation, which a popup consumes. Chromium calls both on its
 * own UI thread, and each answers at once that Chromium makes nothing itself; what Jasper opens instead is posted to
 * Swing's: the link loaded in its page, or an item of its own that the page's item opens beside itself. A link or
 * popup of a page's DevTools browser, as a URL clicked in their Console, goes to the page they inspect, in the tab next
 * to its own when that page could open it ({@link #fromDevTools}). The hover address, from {@code onStatusMessage}, is
 * the {@code page} package's display handler's.
 *
 * <p>The request handler also hears each certificate Chromium cannot verify for a page's load
 * ({@code onCertificateError}): Chromium goes on past it only when the page's item allows that host with that
 * certificate, and otherwise the page is told the certificate, which its item's error page shows with the failure that
 * follows (spec section 14). Chromium checks certificates against the system's trust store.
 *
 * <p>JCEF keeps a client's first request handler, so this is the only one: what else a request handler is asked goes to
 * its defaults. Life span handlers are a list, which JetBrains' runtime adds its own to, so this adds one and removes
 * none. The same installer wires the {@link OpenerBridge}, which a script's {@code window.open} goes through instead of
 * {@code onBeforePopup}, so the popup keeps its {@code window.opener}.
 */
public final class PageNavigation {
    private static final boolean MAC_OS = System.getProperty("os.name", "").startsWith("Mac");
    private static final Consumer<CefClient> INSTALL = PageNavigation::install;

    private PageNavigation() { }

    /**
     * Has Chromium install the handlers and the opener bridge on its client before any page is made on it; once, however
     * often asked. Call it when a page first asks for Chromium, never at the plugin's start: this class names Chromium's
     * types.
     *
     * @param chromium the plugin's Chromium
     */
    public static void prepare(Chromium chromium) {
        chromium.onClient(INSTALL);
    }

    private static void install(CefClient client) {
        client.addRequestHandler(new Links(OpenerBridge.install(client)));
        client.addLifeSpanHandler(new Popups());
    }

    /**
     * Links Chromium would open in a new tab or window, or download: Jasper places them itself. A main frame's navigation
     * response also goes to the opener bridge, which keeps the limits its opener and content security policies set.
     */
    private static final class Links extends CefRequestHandlerAdapter {
        private final OpenerBridge bridge;

        Links(OpenerBridge bridge) {
            this.bridge = bridge;
        }

        @Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request,
                                                                             boolean isNavigation, boolean isDownload, String initiator,
                                                                             BoolRef disableDefaultHandling) {
            return isNavigation && browser instanceof PageBrowser && request.getResourceType() == CefRequest.ResourceType.RT_MAIN_FRAME
                ? bridge.responses() : null;
        }

        @Override public boolean onCertificateError(CefBrowser browser, CefLoadHandler.ErrorCode error, String url, CefSSLInfo info,
                                                    CefCallback callback) {
            Page page = browser instanceof PageBrowser shown ? shown.owner() : null;
            String code = error == null ? "" : error.name();
            // False, not callback.cancel(): CEF then fails the load with the certificate's own error, such as
            // ERR_CERT_AUTHORITY_INVALID, which the error page needs to say why.
            if (page == null || !goesOn(page, url, code, certificate(url, info), EventQueue::invokeLater)) return false;
            callback.Continue();
            return true;
        }

        @Override public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String url, boolean userGesture) {
            if (fromDevTools(browser, url, EventQueue::invokeLater)) return true;
            if (browser instanceof PageBrowser page)
                LinkRoute.link(sender(page, frame), url, page.view().lastPress(), userGesture, System.currentTimeMillis(), MAC_OS)
                    .ifPresent(where -> EventQueue.invokeLater(() -> go(page, url, where)));
            return true;
        }
    }

    /**
     * The address a link Chromium would open elsewhere comes from, for the scheme rule (plan 4b's P48): that of
     * {@code frame}, which Chromium names as the navigation's source, or without one that of the document the page shows
     * ({@link Page#committedAddress()}). Chromium's thread.
     */
    static String sender(PageBrowser browser, CefFrame frame) {
        String url = frame == null ? null : frame.getURL();
        if (url != null && !url.isEmpty()) return url;
        Page page = browser.owner();
        return page == null ? "" : page.committedAddress();
    }

    /** Popups: Jasper opens the ones the user asked for as items, and Chromium makes none. */
    private static final class Popups extends CefLifeSpanHandlerAdapter {
        @Override public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String url, String name) {
            if (fromDevTools(browser, url, EventQueue::invokeLater)) return true;
            if (browser instanceof PageBrowser page) {
                PageView view = page.view();
                LinkRoute.popup(url, view.lastPress(), view.activation(), System.currentTimeMillis(), MAC_OS)
                    .ifPresent(where -> EventQueue.invokeLater(() -> go(page, url, where)));
            }
            return true;
        }
    }

    /**
     * Whether {@code browser} is a page's DevTools browser, whose link or popup to {@code url} then goes to the page they
     * inspect, handed to {@code ui}: in the tab next to the page's, when the page could open it ({@link Page#openFromDevTools}).
     * Chromium makes nothing itself for it. Chromium's thread.
     */
    static boolean fromDevTools(CefBrowser browser, String url, Consumer<Runnable> ui) {
        Page inspected = browser instanceof PageBrowser shown ? shown.inspected() : null;
        if (inspected == null) return false;
        if (url != null) ui.accept(() -> inspected.openFromDevTools(url));
        return true;
    }

    /**
     * Whether Chromium goes on past {@code certificate}, which it could not verify for the load of {@code url} in
     * {@code page} with the error {@code code}: only when the error is one Chrome lets the user past
     * ({@link ServerCertificate#overridable}) and the page allows the certificate. Otherwise {@code ui} is handed the
     * page's refusal, for the failure that follows, and Chromium cancels the load. Without a certificate there is
     * nothing to allow. Chromium's thread.
     */
    static boolean goesOn(Page page, String url, String code, Optional<ServerCertificate> certificate, Consumer<Runnable> ui) {
        if (certificate.isEmpty()) return false;
        if (ServerCertificate.overridable(code) && page.allowsCertificate(certificate.get())) return true;
        ServerCertificate refused = certificate.get();
        ui.accept(() -> page.certificateRefused(url, refused));
        return false;
    }

    /** The certificate {@code info} reports for the load of {@code url}; empty when it reports none Jasper can read. */
    private static Optional<ServerCertificate> certificate(String url, CefSSLInfo info) {
        try {
            return info == null || info.certificate == null ? Optional.empty()
                : ServerCertificate.of(url, info.certificate.getCertificatesChain());
        } catch (RuntimeException unreadable) {
            return Optional.empty();
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
