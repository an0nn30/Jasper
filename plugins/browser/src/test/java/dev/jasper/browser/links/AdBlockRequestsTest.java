package dev.jasper.browser.links;

import dev.jasper.browser.adblock.BlockerJson;
import dev.jasper.browser.adblock.ContentRules;
import dev.jasper.browser.adblock.ResourceKind;
import dev.jasper.browser.adblock.RuleSet;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cef.browser.CefBrowser;
import org.cef.network.CefRequest;
import org.cef.network.CefRequest.ResourceType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ad blocking's part of the client's request handler (spec section 12, plan 6 Task 10): which of WebKit's kinds each
 * of Chromium's resource types is, a main frame's load none, so it is never blocked; and what a request is answered,
 * its page's address read from the browser, a Jasper page's and anything but the web's never blocked. Chromium's side
 * is played by the test: the request it asks about and the browser it names. No Chromium.
 */
class AdBlockRequestsTest {
    private static final String LIST = """
        [{"trigger": {"url-filter": "ads.example/"}, "action": {"type": "block"}},
         {"trigger": {"url-filter": "tracker.example/", "resource-type": ["script"]}, "action": {"type": "block"}},
         {"trigger": {"url-filter": "ads.example/allowed"}, "action": {"type": "ignore-previous-rules"}}]
        """;
    private static final RuleSet RULES = RuleSet.of(ContentRules.compile(BlockerJson.read(LIST)).rules());

    /** The plan's table: each of Chromium's types and the WebKit kinds it loads. */
    @Test void eachResourceTypeIsTheWebKitKindsItLoadsAndAMainFrameNone() {
        var expected = new EnumMap<ResourceType, Set<ResourceKind>>(ResourceType.class);
        expected.put(ResourceType.RT_MAIN_FRAME, Set.of());
        expected.put(ResourceType.RT_NAVIGATION_PRELOAD_MAIN_FRAME, Set.of());
        expected.put(ResourceType.RT_SUB_FRAME, Set.of(ResourceKind.DOCUMENT));
        expected.put(ResourceType.RT_IMAGE, Set.of(ResourceKind.IMAGE));
        expected.put(ResourceType.RT_FAVICON, Set.of(ResourceKind.IMAGE));
        expected.put(ResourceType.RT_STYLESHEET, Set.of(ResourceKind.STYLE_SHEET));
        expected.put(ResourceType.RT_SCRIPT, Set.of(ResourceKind.SCRIPT));
        expected.put(ResourceType.RT_WORKER, Set.of(ResourceKind.SCRIPT));
        expected.put(ResourceType.RT_SHARED_WORKER, Set.of(ResourceKind.SCRIPT));
        expected.put(ResourceType.RT_SERVICE_WORKER, Set.of(ResourceKind.SCRIPT));
        expected.put(ResourceType.RT_FONT_RESOURCE, Set.of(ResourceKind.FONT));
        expected.put(ResourceType.RT_XHR, Set.of(ResourceKind.RAW, ResourceKind.FETCH));
        expected.put(ResourceType.RT_MEDIA, Set.of(ResourceKind.MEDIA));
        expected.put(ResourceType.RT_PING, Set.of(ResourceKind.PING));
        expected.put(ResourceType.RT_CSP_REPORT, Set.of(ResourceKind.PING));
        for (ResourceType other : List.of(ResourceType.RT_SUB_RESOURCE, ResourceType.RT_OBJECT, ResourceType.RT_PREFETCH,
                ResourceType.RT_PLUGIN_RESOURCE, ResourceType.RT_NAVIGATION_PRELOAD_SUB_FRAME, ResourceType.RT_NUM_VALUES))
            expected.put(other, Set.of(ResourceKind.RAW, ResourceKind.OTHER));
        assertThat(expected.keySet()).as("every type Chromium names").containsExactlyInAnyOrder(ResourceType.values());
        for (Map.Entry<ResourceType, Set<ResourceKind>> type : expected.entrySet())
            assertThat(AdBlockRequests.kinds(type.getKey())).as(type.getKey().name()).isEqualTo(type.getValue());
        assertThat(AdBlockRequests.kinds(null)).as("a type JCEF could not name loads nothing that is blocked").isEmpty();
    }

    /** A main frame's load is never blocked, whatever its address: the plan's call, as RuleSet.blocks refuses no kind. */
    @Test void aMainFrameIsNeverBlocked() {
        var blocking = new AdBlockRequests(RULES);
        for (ResourceType main : List.of(ResourceType.RT_MAIN_FRAME, ResourceType.RT_NAVIGATION_PRELOAD_MAIN_FRAME)) {
            var request = new Request("https://ads.example/landing", main);
            assertThat(blocking.onBeforeResourceLoad(browser("https://news.example/"), null, request)).as(main.name()).isFalse();
            assertThat(request.urlReads).as("its address is not read").isZero();
        }
        assertThat(blocking.onBeforeResourceLoad(browser("https://news.example/"), null,
            new Request("https://ads.example/frame", ResourceType.RT_SUB_FRAME))).as("a sub-frame's document is").isTrue();
    }

    @Test void aRequestIsBlockedAsTheRulesSayForItsKindsAndItsPage() {
        var blocking = new AdBlockRequests(RULES);
        var pages = new ArrayList<String>();
        CefBrowser news = browser("https://news.example/today", pages);
        assertThat(blocking.onBeforeResourceLoad(news, null, new Request("https://ads.example/banner.png", ResourceType.RT_IMAGE))).isTrue();
        assertThat(blocking.onBeforeResourceLoad(news, null, new Request("https://ads.example/allowed.png", ResourceType.RT_IMAGE)))
            .as("a later ignore rule").isFalse();
        assertThat(blocking.onBeforeResourceLoad(news, null, new Request("https://tracker.example/t.js", ResourceType.RT_SCRIPT))).isTrue();
        assertThat(blocking.onBeforeResourceLoad(news, null, new Request("https://tracker.example/t.gif", ResourceType.RT_IMAGE)))
            .as("a script rule does not block an image").isFalse();
        assertThat(blocking.onBeforeResourceLoad(news, null, new Request("https://cdn.example/app.js", ResourceType.RT_SCRIPT))).isFalse();
        assertThat(blocking.onBeforeResourceLoad(news, null, new Request("wss://ads.example/socket", ResourceType.RT_SUB_RESOURCE)))
            .as("a WebSocket's address is the web's").isTrue();
        assertThat(pages).as("the page's address is read from its browser").containsOnly("https://news.example/today");
    }

    /** A Jasper page's requests, and those of no web address, are never blocked, and the rules are not asked. */
    @Test void aJasperPageAndAnAddressThatIsNotTheWebsAreNeverBlocked() {
        var blocking = new AdBlockRequests(RULES);
        assertThat(blocking.onBeforeResourceLoad(browser("jasper-about:welcome"), null,
            new Request("https://ads.example/icon.png", ResourceType.RT_IMAGE))).isFalse();
        assertThat(blocking.onBeforeResourceLoad(browser("JASPER-ABOUT://history/"), null,
            new Request("https://ads.example/icon.png", ResourceType.RT_IMAGE))).isFalse();
        var pages = new ArrayList<String>();
        for (String other : List.of("data:image/png;base64,ads.example/", "blob:https://news.example/ads.example/", "file:///ads.example/x.png",
                "chrome-extension://ads.example/x.png", "jasper-about:ads.example/"))
            assertThat(blocking.onBeforeResourceLoad(browser("https://news.example/", pages), null, new Request(other, ResourceType.RT_IMAGE)))
                .as(other).isFalse();
        assertThat(pages).as("the page is not read for an address that is not the web's").isEmpty();
    }

    /** On Chromium's IO thread nothing may throw: a request or browser that fails is not blocked. */
    @Test void aRequestThatCannotBeReadIsNotBlocked() {
        var blocking = new AdBlockRequests(RULES);
        CefBrowser broken = (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(), new Class<?>[] {CefBrowser.class},
            (proxy, method, args) -> { throw new IllegalStateException("the browser has gone"); });
        assertThat(blocking.onBeforeResourceLoad(broken, null, new Request("https://ads.example/a.png", ResourceType.RT_IMAGE))).isFalse();
        assertThat(blocking.onBeforeResourceLoad(browser(null), null, new Request("https://ads.example/a.png", ResourceType.RT_IMAGE)))
            .as("a browser without an address yet is no page's site, and the rules decide").isTrue();
    }

    private static CefBrowser browser(String url) {
        return browser(url, new ArrayList<>());
    }

    /** A browser that answers only its address, recording each read. */
    private static CefBrowser browser(String url, List<String> reads) {
        return (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(), new Class<?>[] {CefBrowser.class}, (proxy, method, args) -> {
            if (method.getName().equals("getURL")) {
                reads.add(String.valueOf(url));
                return url;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    /** A request that answers only its address, counting each read, and its resource type, as Chromium's IO thread asks. */
    static final class Request extends CefRequest {
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
}
