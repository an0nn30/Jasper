package dev.jasper.browser.links;

import dev.jasper.browser.adblock.BlockerJson;
import dev.jasper.browser.adblock.ContentRules;
import dev.jasper.browser.adblock.RuleSet;
import dev.jasper.browser.page.LoadFailure;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageBrowser;
import dev.jasper.browser.page.PageContext;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.page.ServerCertificate;
import dev.jasper.browser.page.SignIn;
import dev.jasper.browser.store.CapturedLog;
import java.awt.Point;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.cef.browser.CefFrame;
import org.cef.callback.CefAuthCallback;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefRequest.ResourceType;
import org.cef.network.CefResponse;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 14: what the client's request handler does with a certificate error of a page's main frame; where a link
 * a page's DevTools open goes; where a link Chromium would open elsewhere comes from; and, for plan 8, a page's HTTP
 * sign-ins and the top address they are judged against. Chromium's side is played by the test: the certificate it
 * reports, the browser it calls about (made without Chromium, {@code PageTestAccess.devToolsBrowser}), its requests,
 * responses and sign-in callback, and its thread, whose hand-offs to the UI thread the test runs. No Chromium.
 */
class PageNavigationTest {
    private static final String URL = "https://self-signed.badssl.com/";
    private static final ServerCertificate CERTIFICATE = new ServerCertificate("self-signed.badssl.com",
        "d53a1b43dbe103e328aa1f2e50c9ee35d35d116b19c8912f86aeff71eda21e61", List.of("CN=*.badssl.com"), true);

    @Test void anAllowedCertificateGoesOnAndTellsThePageNothing() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.allowCertificates(certificate -> certificate.equals(CERTIFICATE));
            var handedOff = new ArrayList<Runnable>();
            assertThat(PageNavigation.goesOn(page, URL, "ERR_CERT_AUTHORITY_INVALID", Optional.of(CERTIFICATE), handedOff::add)).isTrue();
            assertThat(handedOff).isEmpty();
        });
    }

    @Test void anyOtherIsCancelledAndThePageShowsItWithTheFailureThatFollows() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var failures = new ArrayList<LoadFailure>();
            page.onLoadFailure(failures::add);
            var handedOff = new ArrayList<Runnable>();
            assertThat(PageNavigation.goesOn(page, URL, "ERR_CERT_AUTHORITY_INVALID", Optional.of(CERTIFICATE), handedOff::add)).as("none allowed yet").isFalse();
            assertThat(handedOff).hasSize(1);
            handedOff.forEach(Runnable::run);
            PageTestAccess.loadFailed(page, URL, "ERR_CERT_AUTHORITY_INVALID");
            assertThat(failures).containsExactly(new LoadFailure(URL, "ERR_CERT_AUTHORITY_INVALID", Optional.of(CERTIFICATE)));
            handedOff.clear();
            assertThat(PageNavigation.goesOn(page, URL, "ERR_CERT_AUTHORITY_INVALID", Optional.empty(), handedOff::add)).as("no certificate to allow").isFalse();
            assertThat(handedOff).isEmpty();
        });
    }

    /** As Chrome: a revoked, invalid or malformed certificate has no way past it, even one allowed before. */
    @Test void aFatalCertificateErrorGoesOnPastNoCertificateEvenAnAllowedOne() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.allowCertificates(certificate -> true);
            var failures = new ArrayList<LoadFailure>();
            page.onLoadFailure(failures::add);
            for (String fatal : List.of("ERR_CERT_REVOKED", "ERR_CERT_INVALID", "ERR_CERT_CONTAINS_ERRORS")) {
                var handedOff = new ArrayList<Runnable>();
                assertThat(PageNavigation.goesOn(page, URL, fatal, Optional.of(CERTIFICATE), handedOff::add)).as(fatal).isFalse();
                handedOff.forEach(Runnable::run);
                PageTestAccess.loadFailed(page, URL, fatal);
            }
            assertThat(failures).as("each shows its certificate on the error page").allSatisfy(failure ->
                assertThat(failure.certificate()).contains(CERTIFICATE));
        });
    }

    @Test void aClosedPageAllowsNothing() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.allowCertificates(certificate -> true);
            page.close();
            assertThat(PageNavigation.goesOn(page, URL, "ERR_CERT_AUTHORITY_INVALID", Optional.of(CERTIFICATE), task -> { })).isFalse();
        });
    }

    /**
     * A link or popup a page's DevTools open, as a URL clicked in their Console, goes to the page they inspect, in the tab
     * next to its own, when that page could open it itself (4a's R36); a page's own links are not DevTools'.
     */
    @Test void aLinkThePagesDevToolsOpenGoesNextToThePagesTabWhenThePageCouldOpenIt() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var opened = new ArrayList<String>();
            page.onOpenItem((url, placement) -> {
                opened.add(placement + " " + url);
                return Optional.empty();
            });
            var handedOff = new ArrayList<Runnable>();
            var devTools = PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page, new Point()));
            assertThat(PageNavigation.fromDevTools(devTools, "https://example.org/docs", handedOff::add)).isTrue();
            assertThat(PageNavigation.fromDevTools(devTools, "file:///etc/hosts", handedOff::add)).isTrue();
            assertThat(PageNavigation.fromDevTools(devTools, "jasper-about:welcome", handedOff::add)).isTrue();
            assertThat(PageNavigation.fromDevTools(PageTestAccess.pageBrowser(page), "https://example.org/", handedOff::add))
                .as("the page's own browser").isFalse();
            assertThat(opened).as("on the UI thread").isEmpty();
            handedOff.forEach(Runnable::run);
            assertThat(opened).as("an https page opens no local address").containsExactly("NEXT_TAB https://example.org/docs");
        });
    }

    /**
     * Plan 4b's W6: the page opens a DevTools link with Jasper's rights only when the document it shows could open it:
     * its committed address decides, not one it was only asked to load.
     */
    @Test void aDevToolsLinkOpensFromTheDocumentThePageShows() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var opened = new ArrayList<String>();
            page.onOpenItem((url, placement) -> {
                opened.add(placement + " " + url);
                return Optional.empty();
            });
            PageTestAccess.addressChanged(page, "https://example.com/");
            page.load(URI.create("file:///tmp/a.html"));
            page.openFromDevTools("file:///etc/hosts");
            assertThat(opened).as("asked to load a file: page, still showing a web page").isEmpty();
            PageTestAccess.addressChanged(page, "file:///tmp/a.html");
            page.openFromDevTools("file:///etc/hosts");
            assertThat(opened).containsExactly("NEXT_TAB file:///etc/hosts");
        });
    }

    /**
     * Plan 4b's P48: a link Chromium would open elsewhere comes from the address of the frame it names, or, without one,
     * the page's committed address, never one the page was only asked to load.
     */
    @Test void aLinkComesFromItsFramesAddressOrElseThePagesCommittedOne() throws Exception {
        onUiThread(() -> {
            Page page = page();
            PageBrowser browser = PageTestAccess.pageBrowser(page);
            assertThat(PageNavigation.sender(browser, frame("file:///tmp/a.html"))).isEqualTo("file:///tmp/a.html");
            assertThat(PageNavigation.sender(browser, null)).as("before a navigation commits").isEmpty();
            page.load(URI.create("file:///tmp/a.html"));
            assertThat(PageNavigation.sender(browser, null)).as("asked to load, not committed").isEmpty();
            PageTestAccess.addressChanged(page, "file:///tmp/a.html");
            assertThat(PageNavigation.sender(browser, null)).isEqualTo("file:///tmp/a.html");
            assertThat(PageNavigation.sender(browser, frame(""))).isEqualTo("file:///tmp/a.html");
            PageBrowser devTools = PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page, new Point()));
            assertThat(PageNavigation.sender(devTools, null)).as("no page's").isEmpty();
        });
    }

    /**
     * F1: Chromium's scheme handler factory serves its global context alone, so the client's request handler serves
     * {@code jasper-about:} itself to an in-memory page's main frame; the opener bridge reads each page's main-frame
     * navigation's response; and every other request gets no handler. The request's address is read only for a main
     * frame, once: a page's navigation notes it as the page's top address (plan 8's F6), and an in-memory page's main
     * frame may be {@code jasper-about:}. Reading it copies it across JNI, and a {@code data:} address can run to
     * megabytes.
     */
    @Test void eachRequestGetsTheCompositeOfItsPartsAndOnlyAMainFrameHasItsAddressReadOnce() throws Exception {
        onUiThread(() -> {
            PageBrowser persistent = PageTestAccess.pageBrowser(page()), inMemory = PageTestAccess.pageBrowser(page(PageContext.memory()));
            var responses = new org.cef.handler.CefResourceRequestHandlerAdapter() { };

            var welcome = new Request("jasper-about:welcome", ResourceType.RT_MAIN_FRAME);
            var both = PageNavigation.requests(inMemory, welcome, true, () -> responses, null, Optional::empty);
            assertThat(both).isNotNull();
            assertThat(both.responses).as("the opener bridge's").isSameAs(responses);
            assertThat(both.about).as("jasper-about: through the request handler").isTrue();
            assertThat(welcome.urlReads).isEqualTo(1);

            var web = new Request("https://example.com/", ResourceType.RT_MAIN_FRAME);
            var bridged = PageNavigation.requests(inMemory, web, true, () -> responses, null, Optional::empty);
            assertThat(bridged.responses).isSameAs(responses);
            assertThat(bridged.about).isFalse();

            var typed = new Request("JASPER-ABOUT://history/?q=1", ResourceType.RT_MAIN_FRAME);
            var aboutOnly = PageNavigation.requests(inMemory, typed, false, () -> responses, null, Optional::empty);
            assertThat(aboutOnly.responses).as("no navigation, no response to read").isNull();
            assertThat(aboutOnly.about).isTrue();

            var persistentWelcome = new Request("jasper-about:welcome", ResourceType.RT_MAIN_FRAME);
            var global = PageNavigation.requests(persistent, persistentWelcome, true, () -> responses, null, Optional::empty);
            assertThat(global.responses).isSameAs(responses);
            assertThat(global.about).as("the global context's factory serves it").isFalse();
            assertThat(persistentWelcome.urlReads).as("a persistent page's navigation, for its top address").isEqualTo(1);
            var persistentLoad = new Request("jasper-about:welcome", ResourceType.RT_MAIN_FRAME);
            assertThat(PageNavigation.requests(persistent, persistentLoad, false, () -> responses, null, Optional::empty)).isNull();
            assertThat(persistentLoad.urlReads).as("a persistent page's main frame that is no navigation").isZero();
            assertThat(web.urlReads + typed.urlReads).as("an in-memory page's, once").isEqualTo(2);

            for (PageBrowser browser : List.of(persistent, inMemory)) {
                var image = new Request("jasper-about:welcome", ResourceType.RT_IMAGE);
                var frame = new Request("jasper-about:welcome", ResourceType.RT_SUB_FRAME);
                assertThat(PageNavigation.requests(browser, image, false, () -> responses, null, Optional::empty)).isNull();
                assertThat(PageNavigation.requests(browser, frame, true, () -> responses, null, Optional::empty)).isNull();
                assertThat(image.urlReads + frame.urlReads).as("no address read for what is no main frame").isZero();
            }
            var none = new Request("jasper-about:welcome", ResourceType.RT_MAIN_FRAME);
            assertThat(PageNavigation.requests(null, none, true, () -> responses, null, Optional::empty)).isNull();
            assertThat(none.urlReads).isZero();
        });
    }

    /**
     * Plan 6 Task 10: while a rule set is active, every request of a page's browser but its main frame's gets the
     * composite with ad blocking's part, a /dev/null page's too, as blocking is a protection; a main frame's never does,
     * as ad blocking never blocks one. A page's DevTools, and a browser that is no page's, get none. No request's address
     * is read to choose its handler.
     */
    @Test void whileARuleSetIsActiveEveryRequestButAMainFramesGetsAdBlocking() throws Exception {
        onUiThread(() -> {
            Page persistent = page(), devNull = page(PageContext.memory());
            var responses = new org.cef.handler.CefResourceRequestHandlerAdapter() { };
            RuleSet rules = RuleSet.of(ContentRules.compile(BlockerJson.read(
                "[{\"trigger\": {\"url-filter\": \"ads.example/\"}, \"action\": {\"type\": \"block\"}}]")).rules());
            Supplier<Optional<RuleSet>> active = () -> Optional.of(rules);
            for (PageBrowser browser : List.of(PageTestAccess.pageBrowser(persistent), PageTestAccess.pageBrowser(devNull))) {
                var image = new Request("https://ads.example/banner.png", ResourceType.RT_IMAGE);
                var frame = new Request("https://ads.example/frame", ResourceType.RT_SUB_FRAME);
                var blocked = PageNavigation.requests(browser, image, false, () -> responses, null, active);
                assertThat(blocked).isNotNull();
                assertThat(blocked.responses).as("no response to read").isNull();
                assertThat(blocked.about).isFalse();
                assertThat(blocked.blocking).as("ad blocking's part").isNotNull();
                assertThat(PageNavigation.requests(browser, frame, true, () -> responses, null, active).blocking).isNotNull();
                assertThat(image.urlReads + frame.urlReads).as("no address read to choose the handler").isZero();
                var main = PageNavigation.requests(browser, new Request("https://ads.example/", ResourceType.RT_MAIN_FRAME), true,
                    () -> responses, null, active);
                assertThat(main.blocking).as("a main frame is never blocked").isNull();
                assertThat(main.responses).isSameAs(responses);
                assertThat(PageNavigation.requests(browser, new Request("https://ads.example/", ResourceType.RT_NAVIGATION_PRELOAD_MAIN_FRAME),
                    true, () -> responses, null, active)).as("nor its preload").isNull();
                assertThat(PageNavigation.requests(browser, image, false, () -> responses, null, Optional::empty)).as("no rule set").isNull();
            }
            PageBrowser tools = PageTestAccess.devToolsBrowser(PageTestAccess.devTools(persistent, new Point(1, 1)));
            assertThat(PageNavigation.requests(tools, new Request("https://ads.example/a.png", ResourceType.RT_IMAGE), false,
                () -> responses, null, active)).as("a page's DevTools").isNull();
            assertThat(PageNavigation.requests(null, new Request("https://ads.example/a.png", ResourceType.RT_IMAGE), false,
                () -> responses, null, active)).as("no page's browser").isNull();
        });
    }

    /** The composite's ad blocking part answers whether a request is cancelled, and the composite without it cancels none. */
    @Test void theCompositeCancelsWhatItsAdBlockingPartBlocks() throws Exception {
        onUiThread(() -> {
            RuleSet rules = RuleSet.of(ContentRules.compile(BlockerJson.read(
                "[{\"trigger\": {\"url-filter\": \"ads.example/\"}, \"action\": {\"type\": \"block\"}}]")).rules());
            PageBrowser browser = PageTestAccess.pageBrowser(page());
            var handler = PageNavigation.requests(browser, new Request("https://ads.example/a.png", ResourceType.RT_IMAGE), false,
                () -> null, null, () -> Optional.of(rules));
            var page = (org.cef.browser.CefBrowser) Proxy.newProxyInstance(org.cef.browser.CefBrowser.class.getClassLoader(),
                new Class<?>[] {org.cef.browser.CefBrowser.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getURL")) return "https://news.example/";
                    throw new UnsupportedOperationException(method.getName());
                });
            assertThat(handler.onBeforeResourceLoad(page, null, new Request("https://ads.example/a.png", ResourceType.RT_IMAGE))).isTrue();
            assertThat(handler.onBeforeResourceLoad(page, null, new Request("https://cdn.example/a.png", ResourceType.RT_IMAGE))).isFalse();
            var main = PageNavigation.requests(browser, new Request("https://ads.example/", ResourceType.RT_MAIN_FRAME), true,
                () -> null, null, () -> Optional.of(rules));
            assertThat(main.onBeforeResourceLoad(page, null, new Request("https://ads.example/", ResourceType.RT_MAIN_FRAME))).isFalse();
        });
    }

    /**
     * A browser is an in-memory one as its page's store says, not as JCEF's context object says: a persistent page's
     * DevTools browser, which JCEF gives the global context's object, is not; a /dev/null page's DevTools browser is.
     */
    @Test void aBrowserIsInMemoryAsItsPagesStoreSaysDevToolsToo() throws Exception {
        onUiThread(() -> {
            Page persistent = page(), devNull = page(PageContext.memory());
            assertThat(PageTestAccess.pageBrowser(persistent).inMemory()).isFalse();
            assertThat(PageTestAccess.pageBrowser(devNull).inMemory()).isTrue();
            assertThat(PageTestAccess.devToolsBrowser(PageTestAccess.devTools(persistent, new Point(1, 1))).inMemory()).isFalse();
            assertThat(PageTestAccess.devToolsBrowser(PageTestAccess.devTools(devNull, new Point(1, 1))).inMemory()).isTrue();
        });
    }

    /**
     * Plan 8's F6: a page's main-frame navigation request is its top address, and so is each redirect of it, which the
     * composite hands to the opener bridge's reader; the final response's status then reaches the page on the UI
     * thread. So {@code http://intranet.example} redirected to {@code https://intranet.example/} before its 401 is
     * judged against the address that challenged, not the one before the redirect. Chromium's IO thread is the test's.
     */
    @Test void aMainFrameNavigationAndItsRedirectAreThePagesTopAddressAndItsResponseIsTold() throws Exception {
        var made = new AtomicReference<Page>();
        var responses = new CopyOnWriteArrayList<String>();
        onUiThread(() -> {
            made.set(page());
            made.get().onMainFrameResponse((url, status) ->
                responses.add(status + " " + url + " " + SwingUtilities.isEventDispatchThread()));
        });
        Page page = made.get();
        PageBrowser browser = PageTestAccess.pageBrowser(page);
        var navigation = new Request("http://intranet.example/", ResourceType.RT_MAIN_FRAME);
        long before = page.navigation();
        PageTestAccess.activate(page, System.currentTimeMillis()); // the user's click on a link
        var handler = PageNavigation.requests(browser, navigation, true, new OpenerBridge()::responses, null, Optional::empty);
        assertThat(page.topAddress()).isEqualTo("http://intranet.example/");
        assertThat(page.latestNavigation()).as("L31: a new navigation; L35: the click began it")
            .isEqualTo(new Page.Navigation(before + 1, true));
        handler.onResourceRedirect(browser, null, navigation, new Response(301), new StringRef("https://intranet.example/"));
        assertThat(page.topAddress()).as("after the redirect").isEqualTo("https://intranet.example/");
        assertThat(page.latestNavigation()).as("L31: a redirect is the same navigation, and keeps its activation")
            .isEqualTo(new Page.Navigation(before + 1, true));
        assertThat(handler.onResourceResponse(browser, null, new Request("https://intranet.example/", ResourceType.RT_MAIN_FRAME),
            new Response(401))).as("nothing about the load changes").isFalse();
        onUiThread(() -> { }); // the response is posted to the UI thread
        assertThat(responses).containsExactly("401 https://intranet.example/ true");
    }

    /**
     * F6: no other request is a page's top address: a sub-frame's, a main frame's that is no navigation, or DevTools'.
     */
    @Test void onlyAPagesMainFrameNavigationIsItsTopAddress() throws Exception {
        onUiThread(() -> {
            Page page = page();
            PageBrowser browser = PageTestAccess.pageBrowser(page);
            Supplier<org.cef.handler.CefResourceRequestHandler> responses = () -> new OpenerBridge().responses();
            PageNavigation.requests(browser, new Request("https://frame.example/", ResourceType.RT_SUB_FRAME), true, responses, null,
                Optional::empty);
            PageNavigation.requests(browser, new Request("https://download.example/", ResourceType.RT_MAIN_FRAME), false, responses,
                null, Optional::empty);
            PageBrowser tools = PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page, new Point(1, 1)));
            PageNavigation.requests(tools, new Request("devtools://devtools/inspector.html", ResourceType.RT_MAIN_FRAME), true,
                () -> null, null, Optional::empty);
            assertThat(page.topAddress()).as("still none").isEmpty();
            assertThat(page.navigation()).as("L31: no navigation of the page's").isZero();
            var redirected = new PageNavigation.PageRequests(new OpenerBridge().responses(), false, null, null);
            redirected.onResourceRedirect(tools, null, new Request("https://a.example/", ResourceType.RT_MAIN_FRAME),
                new Response(302), new StringRef("https://b.example/"));
            redirected.onResourceRedirect(browser, null, new Request("https://a.example/", ResourceType.RT_SUB_FRAME),
                new Response(302), new StringRef("https://b.example/"));
            assertThat(page.topAddress()).as("no page's redirect, nor a sub-frame's").isEmpty();
        });
    }

    /** F6: the composite hands a redirect to the opener bridge's reader when it has one, and otherwise ignores it. */
    @Test void aPagesRequestHandsARedirectToItsResponsesReaderOnly() throws Exception {
        onUiThread(() -> {
            PageBrowser browser = PageTestAccess.pageBrowser(page());
            var redirects = new ArrayList<String>();
            var reader = new org.cef.handler.CefResourceRequestHandlerAdapter() {
                @Override public void onResourceRedirect(org.cef.browser.CefBrowser shown, CefFrame frame, CefRequest request,
                                                         CefResponse response, StringRef newUrl) {
                    redirects.add(newUrl.get());
                }
            };
            var request = new Request("http://intranet.example/", ResourceType.RT_MAIN_FRAME);
            PageNavigation.requests(browser, request, true, () -> reader, null, Optional::empty)
                .onResourceRedirect(browser, null, request, new Response(301), new StringRef("https://intranet.example/"));
            assertThat(redirects).containsExactly("https://intranet.example/");
            new PageNavigation.PageRequests(null, false, null, null)
                .onResourceRedirect(browser, null, request, new Response(301), new StringRef("https://elsewhere.example/"));
            assertThat(redirects).as("no reader, nothing handed").hasSize(1);
        });
    }

    /**
     * Plan 8's F12: Chromium asks for a page's sign-in on a thread of its own, which must never block: the sign-in is
     * made over Chromium's callback with the page's top address, handed to the UI thread, and Chromium is told at once
     * that Jasper answers later. The page then hands it to its listener, which answers once.
     */
    @Test void aPagesSignInIsHandedToItsPageAndAnsweredOnceLater() throws Exception {
        onUiThread(() -> {
            Page page = page();
            PageTestAccess.activate(page, System.currentTimeMillis()); // the user's click on a link
            page.mainFrameRequested("https://intranet.example/start");
            long navigation = page.navigation();
            var heard = new ArrayList<SignIn>();
            page.onSignIn(heard::add);
            var handedOff = new ArrayList<Runnable>();
            var callback = new Callback();
            assertThat(PageNavigation.signIn(PageTestAccess.pageBrowser(page), "https://intranet.example/", false,
                "intranet.example", 443, "Corp", "Basic", callback, () -> false, handedOff::add)).isTrue();
            assertThat(heard).as("on the UI thread").isEmpty();
            handedOff.forEach(Runnable::run);
            assertThat(heard).singleElement().satisfies(signIn -> {
                assertThat(signIn.originUrl()).isEqualTo("https://intranet.example/");
                assertThat(signIn.host()).isEqualTo("intranet.example");
                assertThat(signIn.port()).isEqualTo(443);
                assertThat(signIn.realm()).isEqualTo("Corp");
                assertThat(signIn.scheme()).isEqualTo("basic");
                assertThat(signIn.proxy()).isFalse();
                assertThat(signIn.topAddress()).isEqualTo("https://intranet.example/start");
                assertThat(signIn.navigation()).as("L31: the navigation asked in").isEqualTo(navigation);
                assertThat(signIn.activated()).as("L35: the click began it").isTrue();
            });
            assertThat(callback.told).as("answered later").isEmpty();
            assertThat(heard.getFirst().proceed("u", "pw")).isTrue();
            assertThat(heard.getFirst().cancel()).isFalse();
            assertThat(callback.told).containsExactly("continue u pw");
        });
    }

    /**
     * A page that no one answers for cancels its sign-in at once, on the UI thread, so Chromium's request waits on
     * nothing.
     */
    @Test void aSignInOfAPageNoOneListensOnIsCancelled() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var handedOff = new ArrayList<Runnable>();
            var callback = new Callback();
            assertThat(PageNavigation.signIn(PageTestAccess.pageBrowser(page), "https://intranet.example/", true,
                "proxy.example", 8118, "", "basic", callback, () -> false, handedOff::add)).isTrue();
            handedOff.forEach(Runnable::run);
            assertThat(callback.told).containsExactly("cancel");
        });
    }

    /** Plan 6's F43: an answer that comes once Chromium has stopped touches Chromium's callback no more. */
    @Test void aSignInAnsweredOnceChromiumHasStoppedTouchesTheCallbackNoMore() throws Exception {
        onUiThread(() -> {
            var stopped = new java.util.concurrent.atomic.AtomicBoolean();
            var handedOff = new ArrayList<Runnable>();
            var callback = new Callback();
            Page proceeding = page(), cancelling = page();
            var heard = new ArrayList<SignIn>();
            proceeding.onSignIn(heard::add);
            for (Page page : List.of(proceeding, cancelling))
                assertThat(PageNavigation.signIn(PageTestAccess.pageBrowser(page), "https://intranet.example/", false,
                    "intranet.example", 443, "Corp", "basic", callback, stopped::get, handedOff::add)).isTrue();
            stopped.set(true);
            handedOff.forEach(Runnable::run);
            heard.getFirst().proceed("u", "pw");
            assertThat(heard.getFirst().answered()).isTrue();
            assertThat(callback.told).as("neither the proceed nor the cancel of the page no one listens on").isEmpty();
        });
    }

    /**
     * F12: a browser that is no page's, DevTools' among them, gets no sign-in, and CEF cancels its challenge itself.
     */
    @Test void aBrowserThatIsNoPagesGetsNoSignIn() throws Exception {
        onUiThread(() -> {
            var handedOff = new ArrayList<Runnable>();
            var callback = new Callback();
            PageBrowser tools = PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page(), new Point(1, 1)));
            for (org.cef.browser.CefBrowser browser : java.util.Arrays.asList(null, tools))
                assertThat(PageNavigation.signIn(browser, "https://intranet.example/", false, "intranet.example", 443, "Corp",
                    "basic", callback, () -> false, handedOff::add)).isFalse();
            assertThat(PageNavigation.signIn(PageTestAccess.pageBrowser(page()), "https://intranet.example/", false,
                "intranet.example", 443, "Corp", "basic", null, () -> false, handedOff::add)).as("no callback").isFalse();
            assertThat(handedOff).isEmpty();
            assertThat(callback.told).isEmpty();
        });
    }

    /**
     * A failure inside is logged, with the exception alone, and answers false, so CEF cancels; the callback is left
     * alone, as CEF answers it then.
     */
    @Test void aFailureAnswersFalseAndLeavesTheCallbackAlone() throws Exception {
        onUiThread(() -> {
            try (var log = new CapturedLog(PageNavigation.class.getName())) {
                var callback = new Callback();
                java.util.function.Consumer<Runnable> failing = task -> { throw new IllegalStateException("No event queue"); };
                assertThat(PageNavigation.signIn(PageTestAccess.pageBrowser(page()), "https://intranet.example/", false,
                    "intranet.example", 443, "Corp", "basic", callback, () -> false, failing)).isFalse();
                assertThat(callback.told).isEmpty();
                assertThat(log.records()).filteredOn(record -> record.getLevel() == java.util.logging.Level.WARNING).singleElement()
                    .satisfies(record -> {
                        assertThat(record.getThrown()).isInstanceOf(IllegalStateException.class);
                        assertThat(text(record)).doesNotContain("Corp", "intranet.example");
                    });
            }
        });
    }

    /**
     * F12, for Task 8: one DEBUG line in Jasper's run names the thread Chromium asks for a sign-in on, and whether it
     * named the request's full address or only an origin; never a user name, password, realm, host or address. A
     * sign-in while DEBUG is off spends nothing, so the line comes with the first sign-in once DEBUG is on.
     */
    @Test void oneDebugLineNamesTheSignInsThreadAndTheFormOfItsAddressOnly() throws Exception {
        var logger = java.util.logging.Logger.getLogger(PageNavigation.class.getName());
        java.util.logging.Level level = logger.getLevel();
        boolean logged = PageNavigation.SIGN_IN_LOGGED.getAndSet(false);
        logger.setLevel(java.util.logging.Level.FINE);
        try (var log = new CapturedLog(PageNavigation.class.getName())) {
            var made = new AtomicReference<PageBrowser>();
            onUiThread(() -> made.set(PageTestAccess.pageBrowser(page())));
            logger.setLevel(java.util.logging.Level.INFO);
            PageNavigation.signIn(made.get(), "https://intranet.example/", false, "intranet.example", 443, "Corp", "basic",
                new Callback(), () -> false, task -> { });
            assertThat(PageNavigation.SIGN_IN_LOGGED).as("DEBUG off").isFalse();
            logger.setLevel(java.util.logging.Level.FINE);
            var askedOn = new AtomicReference<String>();
            Thread chromium = new Thread(() -> {
                askedOn.set(Thread.currentThread().getName());
                PageNavigation.signIn(made.get(), "https://user:pw@intranet.example/private/page", false, "intranet.example", 443,
                    "Corp", "basic", new Callback(), () -> false, task -> { });
                PageNavigation.signIn(made.get(), "https://intranet.example/", false, "intranet.example", 443, "Corp", "basic",
                    new Callback(), () -> false, task -> { });
            }, "Chromium IO");
            chromium.start();
            chromium.join();
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.FINE);
                assertThat(text(record)).contains(askedOn.get(), "a full address")
                    .doesNotContain("Corp", "intranet.example", "user", "pw", "private");
            });
        } finally {
            logger.setLevel(level);
            PageNavigation.SIGN_IN_LOGGED.set(logged);
        }
    }

    /** A record's text as it would be written, its parameters filled in, and its exception's message. */
    private static String text(java.util.logging.LogRecord record) {
        Object[] parameters = record.getParameters();
        String message = parameters == null || parameters.length == 0 ? record.getMessage()
            : java.text.MessageFormat.format(record.getMessage(), parameters);
        return message + (record.getThrown() == null ? "" : " " + record.getThrown().getMessage());
    }

    /** Chromium's sign-in callback, recording what it is told. */
    private static final class Callback implements CefAuthCallback {
        final List<String> told = new CopyOnWriteArrayList<>();

        @Override public void Continue(String user, String password) {
            told.add("continue " + user + " " + password);
        }

        @Override public void cancel() {
            told.add("cancel");
        }
    }

    /** A response that answers only its status and no headers. */
    private static final class Response extends CefResponse {
        private final int status;

        Response(int status) {
            this.status = status;
        }

        @Override public int getStatus() { return status; }
        @Override public String getHeaderByName(String name) { return null; }
        @Override public void getHeaderMap(java.util.Map<String, String> headers) { }
        @Override public void dispose() { }
        @Override public boolean isReadOnly() { return true; }
        @Override public org.cef.handler.CefLoadHandler.ErrorCode getError() { throw new UnsupportedOperationException(); }
        @Override public void setError(org.cef.handler.CefLoadHandler.ErrorCode error) { throw new UnsupportedOperationException(); }
        @Override public void setStatus(int value) { throw new UnsupportedOperationException(); }
        @Override public String getStatusText() { throw new UnsupportedOperationException(); }
        @Override public void setStatusText(String value) { throw new UnsupportedOperationException(); }
        @Override public String getMimeType() { throw new UnsupportedOperationException(); }
        @Override public void setMimeType(String value) { throw new UnsupportedOperationException(); }
        @Override public void setHeaderByName(String name, String value, boolean overwrite) {
            throw new UnsupportedOperationException();
        }
        @Override public void setHeaderMap(java.util.Map<String, String> headers) { throw new UnsupportedOperationException(); }
    }

    /** A frame that answers only its address. */
    private static CefFrame frame(String url) {
        return (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(), new Class<?>[] {CefFrame.class}, (proxy, method, args) -> {
            if (method.getName().equals("getURL")) return url;
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static Page page() {
        return page(PageContext.persistent());
    }

    private static Page page(PageContext context) {
        return new Page(URI.create("https://example.com/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run, context);
    }

    /** A request that answers only its address, counting each read, and its resource type, as Chromium's IO thread asks. */
    private static final class Request extends CefRequest {
        private final String url;
        private final ResourceType type;
        int urlReads;

        Request(String url, ResourceType type) {
            this.url = url;
            this.type = type;
        }

        @Override public String getURL() {
            urlReads++;
            return url;
        }

        @Override public ResourceType getResourceType() { return type; }
        @Override public void dispose() { }
        @Override public long getIdentifier() { throw new UnsupportedOperationException(); }
        @Override public boolean isReadOnly() { return true; }
        @Override public void setURL(String value) { throw new UnsupportedOperationException(); }
        @Override public String getMethod() { return "GET"; }
        @Override public void setMethod(String value) { throw new UnsupportedOperationException(); }
        @Override public void setReferrer(String value, ReferrerPolicy policy) { throw new UnsupportedOperationException(); }
        @Override public String getReferrerURL() { throw new UnsupportedOperationException(); }
        @Override public ReferrerPolicy getReferrerPolicy() { throw new UnsupportedOperationException(); }
        @Override public org.cef.network.CefPostData getPostData() { throw new UnsupportedOperationException(); }
        @Override public void setPostData(org.cef.network.CefPostData data) { throw new UnsupportedOperationException(); }
        @Override public String getHeaderByName(String name) { throw new UnsupportedOperationException(); }
        @Override public void setHeaderByName(String name, String value, boolean overwrite) { throw new UnsupportedOperationException(); }
        @Override public void getHeaderMap(java.util.Map<String, String> headers) { throw new UnsupportedOperationException(); }
        @Override public void setHeaderMap(java.util.Map<String, String> headers) { throw new UnsupportedOperationException(); }
        @Override public void set(String value, String method, org.cef.network.CefPostData data, java.util.Map<String, String> headers) {
            throw new UnsupportedOperationException();
        }
        @Override public int getFlags() { throw new UnsupportedOperationException(); }
        @Override public void setFlags(int flags) { throw new UnsupportedOperationException(); }
        @Override public String getFirstPartyForCookies() { throw new UnsupportedOperationException(); }
        @Override public void setFirstPartyForCookies(String value) { throw new UnsupportedOperationException(); }
        @Override public TransitionType getTransitionType() { throw new UnsupportedOperationException(); }
    }

    private static void onUiThread(Runnable test) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                test.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
    }
}
