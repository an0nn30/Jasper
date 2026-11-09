package dev.jasper.browser.links;

import dev.jasper.browser.adblock.ResourceKind;
import dev.jasper.browser.adblock.RuleSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.network.CefRequest;

/**
 * Ad blocking's part of the client's request handler (spec section 12): whether Chromium cancels a page's request, as
 * the filter list's {@link RuleSet} decides for its address, the WebKit kinds its resource type loads ({@link #kinds})
 * and the address of the page that makes it, its browser's main frame's. A main frame's load loads no kind, so it is
 * never blocked (the plan's call), and neither is a request of a Jasper page ({@code jasper-about:}) or one whose
 * address is not the web's ({@code http}, {@code https}, {@code ws} or {@code wss}). Its addresses are read across JNI
 * only for a request that may be blocked: its kinds are checked first, then its scheme. The rule set is immutable and
 * bounds its own work (ruling V30), so this is lock-free. Chromium's IO thread; it never throws.
 */
final class AdBlockRequests extends CefResourceRequestHandlerAdapter {
    private static final Set<ResourceKind> NONE = Set.of(), DOCUMENT = only(ResourceKind.DOCUMENT), IMAGE = only(ResourceKind.IMAGE),
        STYLE_SHEET = only(ResourceKind.STYLE_SHEET), SCRIPT = only(ResourceKind.SCRIPT), FONT = only(ResourceKind.FONT),
        FETCH = only(ResourceKind.RAW, ResourceKind.FETCH), MEDIA = only(ResourceKind.MEDIA), PING = only(ResourceKind.PING),
        OTHER = only(ResourceKind.RAW, ResourceKind.OTHER);
    private static final String JASPER_ABOUT = "jasper-about:";
    private static final System.Logger LOG = System.getLogger(AdBlockRequests.class.getName());
    private static final AtomicBoolean LOGGED = new AtomicBoolean();
    private final RuleSet rules;

    /**
     * Blocks as {@code rules} decide.
     *
     * @param rules the filter list's rule set, active as the request is asked about
     */
    AdBlockRequests(RuleSet rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * The WebKit kinds a request of Chromium's {@code type} loads: a sub-frame's {@code document}; an image's or a
     * favicon's {@code image}; a style sheet's {@code style-sheet}; a script's and each worker's {@code script}; a
     * font's {@code font}; an XMLHttpRequest's or a {@code fetch}'s {@code raw} and {@code fetch}; audio and video's
     * {@code media}; a ping's and a CSP report's {@code ping}; any other sub-resource's {@code raw} and {@code other}.
     *
     * @param type the request's resource type, as Chromium gives it
     * @return the kinds; none for a main frame's load or its navigation preload, and for a type JCEF could not name,
     *     so neither is ever blocked
     */
    static Set<ResourceKind> kinds(CefRequest.ResourceType type) {
        if (type == null) return NONE;
        return switch (type) {
            case RT_MAIN_FRAME, RT_NAVIGATION_PRELOAD_MAIN_FRAME -> NONE;
            case RT_SUB_FRAME -> DOCUMENT;
            case RT_IMAGE, RT_FAVICON -> IMAGE;
            case RT_STYLESHEET -> STYLE_SHEET;
            case RT_SCRIPT, RT_WORKER, RT_SHARED_WORKER, RT_SERVICE_WORKER -> SCRIPT;
            case RT_FONT_RESOURCE -> FONT;
            case RT_XHR -> FETCH;
            case RT_MEDIA -> MEDIA;
            case RT_PING, RT_CSP_REPORT -> PING;
            default -> OTHER;
        };
    }

    /**
     * Whether Chromium cancels {@code request}: true when the rules block it, for its kinds, as {@code browser}'s page
     * makes it. Chromium's IO thread; a request or browser that cannot be read is not blocked, logged once.
     */
    @Override public boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request) {
        try {
            Set<ResourceKind> kinds = kinds(request.getResourceType());
            if (kinds.isEmpty()) return false;
            String url = request.getURL();
            if (!web(url)) return false;
            String page = browser.getURL();
            if (page != null && page.regionMatches(true, 0, JASPER_ABOUT, 0, JASPER_ABOUT.length())) return false;
            return rules.blocks(url, kinds, page);
        } catch (RuntimeException unreadable) {
            if (LOGGED.compareAndSet(false, true))
                LOG.log(System.Logger.Level.WARNING, "A request could not be checked against the filter list, so it was not blocked; "
                    + "this is logged once", unreadable);
            return false;
        }
    }

    /** Whether {@code url} is the web's: an {@code http}, {@code https}, {@code ws} or {@code wss} address, in either case. */
    private static boolean web(String url) {
        return url != null && (url.regionMatches(true, 0, "http://", 0, 7) || url.regionMatches(true, 0, "https://", 0, 8)
            || url.regionMatches(true, 0, "ws://", 0, 5) || url.regionMatches(true, 0, "wss://", 0, 6));
    }

    private static Set<ResourceKind> only(ResourceKind first, ResourceKind... rest) {
        return Collections.unmodifiableSet(EnumSet.of(first, rest));
    }
}
