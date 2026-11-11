package dev.jasper.browser.links;

import dev.jasper.browser.adblock.FilterList;
import dev.jasper.browser.adblock.RuleSet;
import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.links.LinkGesture.Where;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageBrowser;
import dev.jasper.browser.page.PageView;
import dev.jasper.browser.page.ServerCertificate;
import dev.jasper.browser.page.SignIn;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.EventQueue;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefAuthCallback;
import org.cef.callback.CefCallback;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
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
 * <p>The request handler also hands a page each HTTP sign-in Chromium asks for one of its requests
 * ({@code getAuthCredentials}, plan 8), on Chromium's IO thread, or perhaps its UI thread (F12), and never blocks
 * there: the challenge becomes a {@link SignIn} over Chromium's callback, judged against the page's top address
 * ({@link Page#topAddress()}), which is posted to Swing's thread for the page's item to answer once, later
 * ({@link Page#signIn}), and Chromium is told at once that Jasper answers. A browser that is no page's, DevTools'
 * among them, gets none, and CEF cancels its challenge. The address of each main-frame navigation request of a page,
 * and of each redirect of it, is the page's top address until the next request or commit (F6, L23), and each request
 * notes whether the page had a user activation as it started (plan 8's L35).
 *
 * <p>The request handler also serves {@code jasper-about:} to the pages of an in-memory context, a /dev/null group's:
 * Chromium's scheme handler factory serves the global context alone, so such a page's main-frame {@code jasper-about:}
 * request is answered through {@link Chromium#aboutResource}, as the factory would answer it. And while ad blocking's
 * filter list has a rule set active, every request of a page but its main frame's meets it ({@link AdBlockRequests}),
 * a /dev/null page's too (spec section 12). A request's handler is one composite ({@code PageRequests}) of what each
 * part asks of it: the opener bridge's for a main frame's navigation, its redirects and its response, ad blocking's,
 * and this.
 *
 * <p>JCEF keeps a client's first request handler, so this is the only one: what else a request handler is asked goes to
 * its defaults. Life span handlers are a list, which JetBrains' runtime adds its own to, so this adds one and removes
 * none. The same installer wires the {@link OpenerBridge}, which a script's {@code window.open} goes through instead of
 * {@code onBeforePopup}, so the popup keeps its {@code window.opener}, and the page scripts' privacy router
 * ({@link PrivacyQueries}), which reads what the bridge records of each page's document.
 */
public final class PageNavigation {
    private static final boolean MAC_OS = System.getProperty("os.name", "").startsWith("Mac");
    private static final System.Logger LOG = System.getLogger(PageNavigation.class.getName());
    // Whether this run's first sign-in has been logged: the thread Chromium asks on, and the form of its address (F12).
    static final AtomicBoolean SIGN_IN_LOGGED = new AtomicBoolean();
    // The Chromiums given the handlers, held weakly: each client gets them once, however often a page asks.
    private static final Set<Chromium> PREPARED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private PageNavigation() { }

    /**
     * Has Chromium install the handlers and the opener bridge on its client before any page is made on it; once, however
     * often asked, with one installer for the plugin's run. Call it when a page first asks for Chromium, never at the
     * plugin's start: this class names Chromium's types.
     *
     * @param chromium the plugin's Chromium
     * @param filters the plugin's filter list, whose rule set the request handler applies while one is active
     */
    public static void prepare(Chromium chromium, FilterList filters) {
        if (PREPARED.add(chromium)) chromium.onClient(client -> install(client, chromium, filters));
    }

    private static void install(CefClient client, Chromium chromium, FilterList filters) {
        // Once Chromium has stopped, neither router answers a query, nor a sign-in its callback (plan 6's F43, L15).
        BooleanSupplier stopped = () -> chromium.state() == Chromium.State.STOPPED;
        OpenerBridge bridge = OpenerBridge.install(client, stopped);
        // The page scripts' privacy router reads what the bridge records of each page's document.
        PrivacyQueries.install(client, bridge, stopped);
        client.addRequestHandler(new Links(bridge, chromium, filters, stopped));
        client.addLifeSpanHandler(new Popups());
    }

    /**
     * Links Chromium would open in a new tab or window, or download: Jasper places them itself. A main frame's navigation
     * response also goes to the opener bridge, which keeps the limits its opener and content security policies set, and
     * every other request of a page meets the filter list's rule set while one is active ({@link AdBlockRequests}).
     */
    private static final class Links extends CefRequestHandlerAdapter {
        private final OpenerBridge bridge;
        private final Chromium chromium;
        private final FilterList filters;
        private final BooleanSupplier stopped;

        Links(OpenerBridge bridge, Chromium chromium, FilterList filters, BooleanSupplier stopped) {
            this.bridge = bridge;
            this.chromium = chromium;
            this.filters = filters;
            this.stopped = stopped;
        }

        @Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request,
                                                                             boolean isNavigation, boolean isDownload, String initiator,
                                                                             BoolRef disableDefaultHandling) {
            return requests(browser, request, isNavigation, bridge::responses, chromium, filters::active);
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

        @Override public boolean getAuthCredentials(CefBrowser browser, String originUrl, boolean isProxy, String host, int port,
                                                    String realm, String scheme, CefAuthCallback callback) {
            return signIn(browser, originUrl, isProxy, host, port, realm, scheme, callback, stopped, EventQueue::invokeLater);
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
     * Chromium asks for a sign-in for a request of {@code browser}'s, which {@code host} challenged (F12): for a page's
     * browser, a {@link SignIn} over {@code callback}, with the page's navigation, whether the page's user activation
     * began it, and its top address, is handed to {@code ui} for the page ({@link Page#signIn}), which answers it once,
     * later. It never blocks, takes no lock and reads only volatile state, as Chromium's thread waits on it. The first
     * sign-in of Jasper's run while DEBUG is on logs, at DEBUG, the thread it came on and whether {@code originUrl} is
     * a full address or an origin alone, for Task 8, and nothing more. An answer that comes once Chromium has stopped
     * touches {@code callback} no more (plan 6's F43). A failure inside is logged, with the exception alone, and
     * answers false. Chromium's IO thread, or its UI thread.
     *
     * @return true when Jasper answers {@code callback} later; false, and {@code callback} left alone, for a browser
     *     that is no page's and on a failure, as CEF then cancels the challenge itself
     */
    static boolean signIn(CefBrowser browser, String originUrl, boolean isProxy, String host, int port, String realm, String scheme,
                          CefAuthCallback callback, BooleanSupplier stopped, Consumer<Runnable> ui) {
        try {
            Page page = browser instanceof PageBrowser shown ? shown.owner() : null;
            if (page == null || callback == null) return false;
            // The navigation first: a request that starts between the two reads then never passes for the one asked in.
            Page.Navigation navigation = page.latestNavigation();
            BiConsumer<String, String> proceed = (user, password) -> {
                if (!stopped.getAsBoolean()) callback.Continue(user, password);
            };
            Runnable cancel = () -> {
                if (!stopped.getAsBoolean()) callback.cancel();
            };
            var signIn = new SignIn(originUrl, isProxy, host, port, realm, scheme, page.topAddress(),
                navigation.number(), navigation.activated(), proceed, cancel);
            if (LOG.isLoggable(System.Logger.Level.DEBUG) && SIGN_IN_LOGGED.compareAndSet(false, true))
                LOG.log(System.Logger.Level.DEBUG, "Chromium asked for a sign-in on the thread \"{0}\", naming {1}",
                    Thread.currentThread().getName(), signIn.originOnly() ? "an origin alone" : "a full address");
            ui.accept(() -> page.signIn(signIn));
            return true;
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's sign-in could not be asked", failure);
            return false;
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

    /**
     * One request's handler, the composite of its parts: the opener bridge's reader of a main frame's response and its
     * redirects, null when the request has none; whether a {@code jasper-about:} request of an in-memory context is
     * answered here; and ad blocking's part, which cancels what the filter list blocks, null when the request has none.
     * Chromium's IO thread.
     */
    static final class PageRequests extends CefResourceRequestHandlerAdapter {
        final CefResourceRequestHandler responses;
        final boolean about;
        final AdBlockRequests blocking;
        private final Chromium chromium;

        PageRequests(CefResourceRequestHandler responses, boolean about, AdBlockRequests blocking, Chromium chromium) {
            this.responses = responses;
            this.about = about;
            this.blocking = blocking;
            this.chromium = chromium;
        }

        @Override public boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request) {
            return blocking != null && blocking.onBeforeResourceLoad(browser, frame, request);
        }

        @Override public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
            return about ? chromium.aboutResource(browser, request) : null;
        }

        @Override public void onResourceRedirect(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response,
                                                 StringRef newUrl) {
            if (responses != null) responses.onResourceRedirect(browser, frame, request, response, newUrl);
        }

        @Override public boolean onResourceResponse(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response) {
            return responses != null && responses.onResourceResponse(browser, frame, request, response);
        }
    }

    /**
     * The handler of {@code browser}'s {@code request}: the composite of what applies, or null when nothing does. A
     * page's main frame gets its navigation's response read by the opener bridge, and in an in-memory context (F1),
     * whose pages Chromium's scheme handler factory does not reach, its {@code jasper-about:} request answered here as
     * the factory would answer it; it is never blocked (the plan's call). Every other request of a page's browser, a
     * /dev/null page's too, gets ad blocking's part while the filter list has a rule set active, and nothing else: a
     * {@code jasper-about:} one of a sub-frame or a sub-resource goes as Chromium takes it, as no Jasper page loads one,
     * and in an in-memory context nothing serves it. A page's DevTools get none of ad blocking. Each main-frame
     * navigation request of a page is a new navigation, begun with the page's user activation or not, and its address
     * the page's top address until the next request, redirect or commit ({@link Page#mainFrameRequested}, F6, L31,
     * L35). The request's address is read only for a main frame, once: a page's navigation, or an in-memory browser's,
     * as reading it copies it across JNI and a {@code data:} address can run to megabytes. Chromium's IO thread.
     *
     * @param browser the browser whose request it is
     * @param request the request
     * @param isNavigation whether it is a navigation
     * @param responses the opener bridge's reader of a main frame's response
     * @param chromium serves {@code jasper-about:}
     * @param rules the filter list's rule set while one is active
     * @return the handler, or null
     */
    static PageRequests requests(CefBrowser browser, CefRequest request, boolean isNavigation,
                                 Supplier<CefResourceRequestHandler> responses, Chromium chromium, Supplier<Optional<RuleSet>> rules) {
        if (!(browser instanceof PageBrowser page)) return null;
        CefRequest.ResourceType type = request.getResourceType();
        if (type == CefRequest.ResourceType.RT_MAIN_FRAME) {
            Page owner = isNavigation ? page.owner() : null;
            String url = owner != null || page.inMemory() ? request.getURL() : null;
            if (owner != null) owner.mainFrameRequested(url);
            boolean about = page.inMemory() && AboutScheme.page(url).isPresent();
            if (!isNavigation && !about) return null;
            return new PageRequests(isNavigation ? responses.get() : null, about, null, chromium);
        }
        if (page.owner() == null || AdBlockRequests.kinds(type).isEmpty()) return null;
        Optional<RuleSet> active = rules.get();
        return active.isEmpty() ? null : new PageRequests(null, false, new AdBlockRequests(active.get()), chromium);
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
