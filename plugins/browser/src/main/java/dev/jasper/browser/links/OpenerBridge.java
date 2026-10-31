package dev.jasper.browser.links;

import dev.jasper.browser.http.WebFetch;
import dev.jasper.browser.links.Isolation.OpenerPolicy;
import dev.jasper.browser.links.Isolation.Sandbox;
import dev.jasper.browser.page.Activation;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageBrowser;
import dev.jasper.browser.page.PageEvents;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.EventQueue;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

/**
 * Popups that keep a working {@code window.opener} (spec section 8). JCEF lets Chromium make a popup or cancels it, and
 * cannot host one beside its opener, so Jasper opens a page's {@code window.open} as an item of its own, next to the
 * page's tab, and links the two pages. {@code opener-bridge.js}, which runs in each page's top frame as each document
 * starts loading, stands in for the window each page would hold of the other, and this class relays between them
 * through a message router whose page function is {@code jasperBridgeQuery}: it opens the popup, delivers messages,
 * closes the popup, shows either page's item, and tells each page when the other has gone.
 *
 * <p>The router is the one way Jasper's page scripts reach Jasper, so it serves the {@code page} package's scripts too:
 * a request's {@link Op op} says what it asks, and which of the page's frames may ask it. {@code favicon}, from
 * {@code detect-favicon.js}, tells the page the icon its document names (spec section 5).
 *
 * <p>Every frame and every script of a page can call the page function, so each request is untrusted, and the bridge
 * keeps the limits Chromium would:
 * <ul>
 *   <li>Each op is taken only from the frames it names. Every op so far is taken from a page's top frame alone, as the
 *   scripts live there and nowhere else (spec section 5 keeps page scripts out of challenge frames). A request from a
 *   frame its op does not take, one too long, and more than {@value #WAITING_LIMIT} waiting from one page are refused at
 *   once, on Chromium's thread.</li>
 *   <li>An {@code open} opens only what the page could open itself ({@link LinkRoute#opensFrom}), not from a document
 *   whose sandbox blocks popups, and needs and consumes the page's own {@link Activation}, which Jasper keeps from what
 *   it forwarded to the page.</li>
 *   <li>Link ids are random, issued here, and every other request must come from one of the link's two pages.
 *   {@code focus} also needs the asking page's activation while it shows, so no page pulls the keyboard away from
 *   where the user types.</li>
 *   <li>A message's data is at most {@value #MESSAGE_LIMIT} characters of JSON, reaches the other page's script only as
 *   an escaped string literal, and is delivered only when its target origin is {@code "*"} or the receiving document's
 *   origin at that moment. A document sandboxed without {@code allow-same-origin} has an opaque origin: its messages
 *   come from "null", and only messages for {@code "*"} reach it.</li>
 *   <li>A {@code favicon}'s icon is an absolute http or https address of at most {@value #ICON_LIMIT} characters,
 *   without a user name or password; the page takes it only from the document it still shows, and only its first.</li>
 * </ul>
 *
 * <p>A link ends as Chromium's window groups do: when the popup closes, when the user sends either page elsewhere
 * (address field, Back, Forward), and when a page's new document's {@code Cross-Origin-Opener-Policy} would move it to
 * a window group of its own ({@link Isolation#severs}). Both stand-ins then report closed. When the opener closes, the
 * link keeps only what the popup's later documents need: its id, and that the opener has closed.
 *
 * <p>Chromium asks on its own thread; each request is answered on the UI thread, where items open. A main frame's
 * response is noted on Chromium's IO thread, and its load start is answered at once on Chromium's UI thread, so the
 * script runs before Swing would hear of the load.
 */
final class OpenerBridge {
    /** The page function the router adds to every frame, and the one that cancels a request. */
    static final String QUERY = "jasperBridgeQuery", CANCEL = "jasperBridgeCancel";
    /** The most characters of JSON one message may hold: 1 MiB. */
    static final int MESSAGE_LIMIT = 1 << 20;
    /** The most requests one page may have waiting for the UI thread. */
    static final int WAITING_LIMIT = 8;
    /** The longest icon address a {@code favicon} request may name. */
    static final int ICON_LIMIT = 8_192;
    /**
     * Why a request failed, as the page's script is told: not a request, not allowed, the other page closed, too long,
     * too many waiting.
     */
    static final int MALFORMED = 1, REFUSED = 2, GONE = 3, TOO_LARGE = 4, BUSY = 5;
    /** The page script: a function of its configuration. */
    static final String SOURCE = source();
    // The longest request a message of MESSAGE_LIMIT makes: the script escapes JSON's quotes and backslashes once more.
    private static final int REQUEST_LIMIT = 2 * MESSAGE_LIMIT + 4_096;
    // The longest request whose op is read for a sub-frame: an op a sub-frame may send carries only a little.
    private static final int SUB_FRAME_LIMIT = 4_096;
    // What every page that is no popup of a live link runs: window.open's stand-in.
    private static final String PAGE = SOURCE + "\n({});";
    // The name the script goes by in a page's console and stack traces.
    private static final String SCRIPT_URL = "jasper-about:opener-bridge.js";
    private static final String BRIDGE = "window[Symbol.for(\"dev.jasper.browser.opener-bridge\")]";
    private static final String OPENER_POLICY = "Cross-Origin-Opener-Policy", CONTENT_POLICY = "Content-Security-Policy";
    private static final Answer DONE = new Answer(0, ""),
        SUB_FRAME = new Answer(REFUSED, "Only a page's top frame can use Jasper's opener bridge."),
        MALFORMED_REQUEST = new Answer(MALFORMED, "Jasper does not understand this request."),
        TOO_LONG = new Answer(TOO_LARGE, "Jasper delivers messages of up to 1 MiB of JSON."),
        TOO_MANY = new Answer(BUSY, "Too many of this page's requests are waiting."),
        NOT_WEB_ICON = new Answer(REFUSED, "Jasper shows only icons at http and https addresses."),
        NOT_PARTY = new Answer(REFUSED, "This page is not part of that link."),
        OTHER_CLOSED = new Answer(GONE, "The other page is closed.");
    private static final System.Logger LOG = System.getLogger(OpenerBridge.class.getName());

    private final SecureRandom random = new SecureRandom();
    // Guards the links, every link's state and the pages' documents: requests and closes come on the UI thread, load
    // starts on Chromium's UI thread and responses on its IO thread.
    private final Object lock = new Object();
    private final Map<Long, Link> links = new HashMap<>();
    // Each page's main frame, held weakly: its latest response until a document commits, and the document it shows.
    private final Map<Page, Frame> frames = new WeakHashMap<>();
    // How many of each page's requests wait for the UI thread; a page leaves once none does.
    private final Map<Page, Integer> waiting = new ConcurrentHashMap<>();

    /** A bridge with no links, which no page reaches until {@link #install} wires it to a client. */
    OpenerBridge() { }

    /**
     * Wires a bridge to {@code client} before any page is made on it: a message router with the page function
     * {@value #QUERY}, and a hook on each main frame's load start that runs the page script there. The client's one
     * request handler hands the bridge each main frame's response, through {@link #responses()}. Once per client.
     *
     * @param client the client every page is made on
     * @return the bridge
     */
    static OpenerBridge install(CefClient client) {
        var bridge = new OpenerBridge();
        client.addMessageRouter(CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig(QUERY, CANCEL), new Queries(bridge)));
        PageEvents.onMainFrameLoadStart(client, bridge::loadStarting);
        return bridge;
    }

    /**
     * A page as the bridge reaches it: the page, its activation, whether it shows, how a script reaches the document
     * its main frame shows now, and where that document is. A page's browser gives one; a test gives a recording one.
     *
     * @param page the page, which is one end of a link or asks to be
     * @param activation the page's user activation, as Jasper forwarded the user's presses and keys to it
     * @param showing whether the page shows; UI thread
     * @param scripts runs a script in the main frame's current document
     * @param address the main frame's current address
     */
    record Party(Page page, Activation activation, BooleanSupplier showing, Consumer<String> scripts, Supplier<String> address) {
        Party {
            Objects.requireNonNull(page, "page");
            Objects.requireNonNull(activation, "activation");
            Objects.requireNonNull(showing, "showing");
            Objects.requireNonNull(scripts, "scripts");
            Objects.requireNonNull(address, "address");
        }

        /** The page {@code browser} shows, {@code page}. Any thread. */
        static Party of(PageBrowser browser, Page page) {
            return new Party(page, browser.view().activation(), browser.view()::isShowing,
                script -> browser.executeJavaScript(script, SCRIPT_URL, 0), browser::getURL);
        }
    }

    /**
     * What a request is answered: {@code code} 0 and the text the script is given, or a failure's code and why.
     *
     * @param code 0, or why the request failed
     * @param text the answer, or why
     */
    record Answer(int code, String text) {
        /** Whether the request succeeded. */
        boolean ok() {
            return code == 0;
        }
    }

    /**
     * What a request asks, by its {@code op}, and whether a page's sub-frames may ask it as well as its top frame. An op
     * a sub-frame may send is read on Chromium's thread from a request of at most {@value #SUB_FRAME_LIMIT} characters.
     */
    enum Op {
        /** {@code window.open}. */
        OPEN("open", false),
        /** {@code postMessage} to the other page of a link. */
        POST("post", false),
        /** Closes a link's popup. */
        CLOSE("close", false),
        /** Shows the other page of a link. */
        FOCUS("focus", false),
        /** The icon the document names. */
        FAVICON("favicon", false);

        private final String name;
        private final boolean subFrames;

        Op(String name, boolean subFrames) {
            this.name = name;
            this.subFrames = subFrames;
        }

        /** The op {@code request} names, or empty for none Jasper knows. */
        static Optional<Op> of(Map<String, Object> request) {
            Object name = request.get("op");
            for (Op op : values()) if (op.name.equals(name)) return Optional.of(op);
            return Optional.empty();
        }

        /** Whether a sub-frame may send {@code text}: a short request whose op a sub-frame may send. */
        static boolean fromSubFrame(String text) {
            return text != null && text.length() <= SUB_FRAME_LIMIT
                && BridgeJson.object(text).flatMap(Op::of).map(op -> op.subFrames).orElse(false);
        }
    }

    /** What a main-frame response said that matters here, until its document commits. */
    private record Response(String url, OpenerPolicy policy, Sandbox sandbox) { }

    /**
     * A document a page's main frame shows: its origin, empty when opaque; its opener policy; its sandbox; and whether
     * it is a page's first, empty document.
     */
    record Document(Optional<String> origin, OpenerPolicy policy, Sandbox sandbox, boolean initial) {
        static final Document FIRST = new Document(Optional.empty(), OpenerPolicy.UNSAFE_NONE, Sandbox.NONE, true);
    }

    /**
     * The document a request came from as Chromium's thread saw it when it took the request: its address, and what the
     * bridge knew of it then. A request is judged by this, never by what the page shows by the time the UI thread
     * answers, so a document cannot send as the one that replaces it.
     *
     * @param address the document's address
     * @param document what the bridge knew of it: its sandbox, opener policy and origin
     */
    record Sender(String address, Document document) {
        Sender {
            Objects.requireNonNull(document, "document");
        }
    }

    /** One page's main frame. Guarded by the bridge's lock. */
    private static final class Frame {
        Response pending;
        Document document = Document.FIRST;
    }

    /**
     * One popup and the page whose {@code window.open} opened it. All but its id is guarded by the bridge's lock. When
     * the opener closes, its page, party and registrations are let go; when the link ends, everything is.
     */
    private static final class Link {
        final long id;
        Page opener, popup;
        // How each page's document is reached, once known: the opener's from its request, the popup's from its load.
        Party openerParty, popupParty;
        // What the bridge asked each page: to hear it close, and to hear Jasper send it elsewhere.
        Subscription openerClose, openerNavigate, popupClose, popupNavigate;

        Link(long id, Party opener, Page popup) {
            this.id = id;
            this.opener = opener.page();
            this.popup = popup;
            openerParty = opener;
        }

        /** Whether {@code page} is one of the link's pages that has not closed. */
        boolean has(Page page) {
            return page == opener || page == popup;
        }

        /** Lets everything go, answering the registrations to end. */
        List<Subscription> clear() {
            List<Subscription> watched = Stream.of(openerClose, openerNavigate, popupClose, popupNavigate).filter(Objects::nonNull).toList();
            opener = popup = null;
            openerParty = popupParty = null;
            openerClose = openerNavigate = popupClose = popupNavigate = null;
            return watched;
        }
    }

    /**
     * Whether a page's query may go on to the UI thread: one from a sub-frame whose op only a top frame may send, one too
     * long, and one beyond the {@value #WAITING_LIMIT} the page may have waiting are answered at once. An admitted query
     * is {@link #settled} once answered. Chromium's thread.
     *
     * @param page the page that asks
     * @param mainFrame whether its main frame asks
     * @param text the request
     * @return the answer that refuses it, or empty when it may go on
     */
    Optional<Answer> admit(Page page, boolean mainFrame, String text) {
        if (!mainFrame && !Op.fromSubFrame(text)) return Optional.of(SUB_FRAME);
        if (text == null) return Optional.of(MALFORMED_REQUEST);
        if (text.length() > REQUEST_LIMIT) return Optional.of(TOO_LONG);
        boolean[] admitted = new boolean[1];
        waiting.compute(page, (key, count) -> {
            int now = count == null ? 0 : count;
            if (now >= WAITING_LIMIT) return count;
            admitted[0] = true;
            return now + 1;
        });
        return admitted[0] ? Optional.empty() : Optional.of(TOO_MANY);
    }

    /**
     * An admitted query of {@code page} has been answered. Any thread.
     *
     * @param page the page that asked
     */
    void settled(Page page) {
        waiting.computeIfPresent(page, (key, count) -> count <= 1 ? null : count - 1);
    }

    /**
     * How many admitted queries wait.
     *
     * @return the count, over every page
     */
    int waiting() {
        return waiting.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * The document {@code page}'s main frame shows now, at the address {@code address} gives, read together under the
     * lock a load start takes to record a new document. Chromium's thread, as it takes a request.
     *
     * @param page the page that asks
     * @param address the main frame's address
     * @return the sender
     */
    Sender sender(Page page, Supplier<String> address) {
        synchronized (lock) {
            return new Sender(address.get(), document(page));
        }
    }

    /**
     * Answers a page's request from the document it shows now: as {@link #request(Party, boolean, Sender, String, long)},
     * with the sender taken at once.
     *
     * @param sender the page that asked
     * @param mainFrame whether its main frame asked, rather than a sub-frame
     * @param senderAddress the address of the document that asked
     * @param text the request, as the page's script sent it
     * @param nowMillis when it asked, in milliseconds since the epoch
     * @return the answer
     */
    Answer request(Party sender, boolean mainFrame, String senderAddress, String text, long nowMillis) {
        return request(sender, mainFrame, sender(sender.page(), () -> senderAddress), text, nowMillis);
    }

    /**
     * Answers a page's request. UI thread; it never throws for what a page sent.
     *
     * @param sender the page that asked
     * @param mainFrame whether its main frame asked, rather than a sub-frame
     * @param from the document that asked, as Chromium's thread saw it when it took the request
     * @param text the request, as the page's script sent it
     * @param nowMillis when it asked, in milliseconds since the epoch
     * @return the answer
     */
    Answer request(Party sender, boolean mainFrame, Sender from, String text, long nowMillis) {
        if (!mainFrame && !Op.fromSubFrame(text)) return SUB_FRAME;
        if (text != null && text.length() > REQUEST_LIMIT) return TOO_LONG;
        Map<String, Object> request = BridgeJson.object(text).orElse(null);
        if (request == null) return MALFORMED_REQUEST;
        return switch (Op.of(request).orElse(null)) {
            case OPEN -> open(sender, from, request, nowMillis);
            case POST -> post(sender, from, request);
            case CLOSE -> close(sender, request);
            case FOCUS -> focus(sender, request, nowMillis);
            case FAVICON -> favicon(sender, from, request);
            case null -> MALFORMED_REQUEST;
        };
    }

    /**
     * A page's main frame got a response: what it says matters once its document commits. Chromium's IO thread.
     *
     * @param page the page
     * @param url the response's address
     * @param openerPolicy its {@code Cross-Origin-Opener-Policy} header, or null or empty for none
     * @param contentPolicies its {@code Content-Security-Policy} headers
     */
    void responded(Page page, String url, String openerPolicy, Collection<String> contentPolicies) {
        var response = new Response(url, OpenerPolicy.of(openerPolicy, url), Sandbox.of(contentPolicies));
        synchronized (lock) {
            frames.computeIfAbsent(page, any -> new Frame()).pending = response;
        }
    }

    /**
     * A page's main frame starts loading the document at {@code url}: the script it runs. A popup of a live link gets the
     * opener's stand-in, reporting whether the opener has closed, and is reached from then on; every other page gets
     * only {@code window.open}'s. The document's opener policy and sandbox come from the main frame's response for that
     * address, if it had one; when the opener policy moves the page to a window group of its own, its links end first.
     * Any thread.
     *
     * @param page the page that starts loading
     * @param url the document's address
     * @return the script
     */
    String loadStarted(Party page, String url) {
        List<Link> ended = new java.util.ArrayList<>();
        String script = PAGE;
        synchronized (lock) {
            Frame frame = frames.computeIfAbsent(page.page(), any -> new Frame());
            Response response = frame.pending;
            frame.pending = null;
            if (response == null || !withoutFragment(response.url()).equals(withoutFragment(url)))
                response = new Response(url, OpenerPolicy.UNSAFE_NONE, Sandbox.NONE);
            Document previous = frame.document;
            Document next = new Document(response.sandbox().opaque() ? Optional.empty() : Isolation.origin(url), response.policy(),
                response.sandbox(), false);
            frame.document = next;
            boolean severs = Isolation.severs(previous.initial(), previous.policy(), previous.origin(), next.policy(), next.origin());
            for (Link link : links.values()) {
                if (!link.has(page.page())) continue;
                if (severs) {
                    ended.add(link);
                } else if (link.popup == page.page()) {
                    link.popupParty = page;
                    script = SOURCE + "\n({\"opener\":" + BridgeJson.quote(Long.toString(link.id)) + ",\"closed\":" + (link.opener == null) + "});";
                }
            }
        }
        for (Link link : ended) end(link, page.page());
        return script;
    }

    /**
     * The links that live: those whose popup is open.
     *
     * @return how many
     */
    int links() {
        synchronized (lock) {
            return links.size();
        }
    }

    /**
     * Whether any link still holds {@code page}, or its party.
     *
     * @param page a page
     * @return whether one does
     */
    boolean holds(Page page) {
        synchronized (lock) {
            return links.values().stream().anyMatch(link -> link.has(page) || (link.openerParty != null && link.openerParty.page() == page)
                || (link.popupParty != null && link.popupParty.page() == page));
        }
    }

    /**
     * Chromium's handler for a main frame's resources, which hands the bridge each response's opener policy and content
     * security policies. Chromium's IO thread.
     *
     * @return a handler for a main frame's navigation
     */
    CefResourceRequestHandler responses() {
        return new Responses(this);
    }

    /**
     * Answers an admitted query and settles it: {@code reply} is told the answer once, whatever happens. A failure inside
     * is answered as one; an {@link Error} is answered and then thrown on. UI thread.
     *
     * @param sender the page that asked
     * @param from the document that asked, as Chromium's thread saw it
     * @param text the request
     * @param nowMillis when it asked
     * @param reply told the answer
     */
    void answer(Party sender, Sender from, String text, long nowMillis, Consumer<Answer> reply) {
        Answer answer = new Answer(REFUSED, "Jasper could not answer this request.");
        try {
            answer = request(sender, true, from, text, nowMillis);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The opener bridge could not answer a page", failure);
        } finally {
            settled(sender.page());
            reply.accept(answer);
        }
    }

    /**
     * {@code window.open}: with the page's activation, which it consumes, opens the address in a new tab next to the
     * page's and links the two, answering the link's id; with {@code noopener}, opens it without a link. Only what the
     * page could open itself, and nothing from a document whose sandbox blocks popups; either refusal keeps the
     * activation.
     */
    private Answer open(Party sender, Sender asker, Map<String, Object> request, long nowMillis) {
        if (!only(request, "url", "noopener") || !(request.get("url") instanceof String url)
                || !(request.getOrDefault("noopener", Boolean.FALSE) instanceof Boolean detached))
            return MALFORMED_REQUEST;
        if (!LinkRoute.opensFrom(asker.address(), url))
            return new Answer(REFUSED, "A page can open only http, https and about:blank addresses, and local ones only from a local page.");
        Document from = asker.document();
        if (from.sandbox().blocksPopups()) return new Answer(REFUSED, "This page's sandbox does not allow popups.");
        if (!sender.activation().consume(nowMillis)) return new Answer(REFUSED, "A popup opens only after a click or key press in the page.");
        Optional<Page> opened = sender.page().openItem(url, TabPlacement.NEXT_TAB);
        if (opened.isEmpty()) return new Answer(GONE, "The popup could not open.");
        if (detached) return DONE;
        Link link;
        synchronized (lock) {
            long id;
            do id = random.nextLong() & Long.MAX_VALUE; while (id == 0 || links.containsKey(id));
            link = new Link(id, sender, opened.get());
            links.put(id, link);
            // Until it loads, the popup shows its first, empty document, which takes the opener's origin and policy.
            Optional<String> origin = from.sandbox().opaque() ? Optional.empty() : Isolation.origin(asker.address());
            frames.computeIfAbsent(link.popup, any -> new Frame()).document = new Document(origin, from.policy(), Sandbox.NONE, true);
        }
        watch(link);
        return new Answer(0, Long.toString(link.id));
    }

    /**
     * {@code postMessage}: delivers the data to the other page of the link, as a message from the sender's origin, when
     * the target origin is {@code "*"} or the receiving document's origin now. A message to a popup that has not started
     * loading is lost, as one to a popup's first empty page is, and the answer never tells whether the origin matched.
     */
    private Answer post(Party sender, Sender asker, Map<String, Object> request) {
        if (!only(request, "id", "targetOrigin", "data") || !(request.get("targetOrigin") instanceof String target)
                || !(request.get("data") instanceof String data))
            return MALFORMED_REQUEST;
        long id = id(request);
        if (id == 0) return MALFORMED_REQUEST;
        if (data.length() > MESSAGE_LIMIT) return TOO_LONG;
        boolean fromOpener;
        Party receiver;
        Document received;
        synchronized (lock) {
            Link link = links.get(id);
            if (link == null || !link.has(sender.page())) return NOT_PARTY;
            if (link.opener == null) return OTHER_CLOSED;
            fromOpener = link.opener == sender.page();
            receiver = fromOpener ? link.popupParty : link.openerParty;
            received = document(fromOpener ? link.popup : link.opener);
        }
        if (receiver == null) return DONE;
        Optional<String> origin = received.sandbox().opaque() ? Optional.empty() : Isolation.origin(receiver.address().get());
        if (!target.equals("*") && !origin.map(target::equals).orElse(false)) return DONE;
        String from = asker.document().sandbox().opaque() ? "null" : Isolation.origin(asker.address()).orElse("null");
        tell(receiver, call("deliver", Long.toString(id), fromOpener ? "opener" : "popup", data, from, target));
        return DONE;
    }

    /**
     * {@code close()}, from the opener's stand-in, or the popup's own {@code window.close()}, which closes it even after
     * its opener has closed: closes the popup's item.
     */
    private Answer close(Party sender, Map<String, Object> request) {
        if (!only(request, "id")) return MALFORMED_REQUEST;
        long id = id(request);
        if (id == 0) return MALFORMED_REQUEST;
        Page popup;
        synchronized (lock) {
            Link link = links.get(id);
            if (link == null || !link.has(sender.page())) return NOT_PARTY;
            popup = link.popup;
        }
        popup.closeItem();
        return DONE;
    }

    /**
     * {@code focus()}: shows the other page's item, which raises its window and takes the keyboard. Unlike Chromium, which
     * lets a window's opener focus it at any time, both ends need their own activation, unconsumed, and must show: a page
     * that loops {@code focus()} after one click could otherwise pull the keyboard away from a terminal as the user types.
     */
    private Answer focus(Party sender, Map<String, Object> request, long nowMillis) {
        if (!only(request, "id")) return MALFORMED_REQUEST;
        long id = id(request);
        if (id == 0) return MALFORMED_REQUEST;
        Page other;
        synchronized (lock) {
            Link link = links.get(id);
            if (link == null || !link.has(sender.page())) return NOT_PARTY;
            if (link.opener == null) return OTHER_CLOSED;
            other = link.opener == sender.page() ? link.popup : link.opener;
        }
        if (!sender.activation().active(nowMillis) || !sender.showing().getAsBoolean())
            return new Answer(REFUSED, "A page can bring another forward only while it shows, after a click or key press in it.");
        other.showItem();
        return DONE;
    }

    /**
     * {@code favicon}: the icon the asking document names, as {@code detect-favicon.js} reads it from the document's
     * links. Any script of the page could send it, so its address is only a request: an absolute http or https address
     * of at most {@value #ICON_LIMIT} characters, without a user name or password. The page fetches it, if it still shows
     * the document that asked and that document has named none before.
     */
    private Answer favicon(Party sender, Sender asker, Map<String, Object> request) {
        if (!only(request, "url") || !(request.get("url") instanceof String url)) return MALFORMED_REQUEST;
        Optional<URI> icon = webAddress(url);
        if (icon.isEmpty()) return NOT_WEB_ICON;
        if (asker.address() != null) sender.page().faviconDeclared(icon.get(), asker.address());
        return DONE;
    }

    /** {@code url}, of at most {@value #ICON_LIMIT} characters, as an address Jasper fetches favicons from. */
    private static Optional<URI> webAddress(String url) {
        if (url.length() > ICON_LIMIT) return Optional.empty();
        try {
            return Optional.of(new URI(url)).filter(WebFetch::fetches);
        } catch (URISyntaxException notAUri) {
            return Optional.empty();
        }
    }

    /** The document {@code page}'s main frame shows, as far as the bridge has heard. The bridge's lock is held, or not needed. */
    private Document document(Page page) {
        synchronized (lock) {
            Frame frame = frames.get(page);
            return frame == null ? Document.FIRST : frame.document;
        }
    }

    /**
     * Has the link hear each page close, and Jasper send either elsewhere. A registration that comes after the link
     * ended, or after the opener closed, is ended at once. UI thread.
     */
    private void watch(Link link) {
        Page opener, popup;
        synchronized (lock) {
            opener = link.opener;
            popup = link.popup;
        }
        if (opener == null || popup == null) return;
        Subscription openerClose = opener.onClose(() -> openerClosed(link)), openerNavigate = opener.onNavigate(() -> end(link, null)),
            popupClose = popup.onClose(() -> end(link, popup)), popupNavigate = popup.onNavigate(() -> end(link, null));
        synchronized (lock) {
            if (links.get(link.id) == link) {
                if (link.opener != null) {
                    link.openerClose = openerClose;
                    link.openerNavigate = openerNavigate;
                    openerClose = openerNavigate = null;
                }
                link.popupClose = popupClose;
                link.popupNavigate = popupNavigate;
                popupClose = popupNavigate = null;
            }
        }
        Stream.of(openerClose, openerNavigate, popupClose, popupNavigate).filter(Objects::nonNull).forEach(OpenerBridge::cancel);
    }

    /**
     * The opener closed: the popup's {@code window.opener} reports closed, and the link lets the opener go, keeping only
     * its id for the popup's later documents. UI thread.
     */
    private void openerClosed(Link link) {
        Party popup;
        Subscription navigate;
        synchronized (lock) {
            if (links.get(link.id) != link || link.opener == null) return;
            popup = link.popupParty;
            navigate = link.openerNavigate;
            link.opener = null;
            link.openerParty = null;
            link.openerClose = link.openerNavigate = null;
        }
        cancel(navigate);
        tell(popup, call("closed", Long.toString(link.id)));
    }

    /**
     * The link ends, because the popup closed or either page went to a window group of its own: both stand-ins report
     * closed, but in {@code gone}, and the bridge lets both pages go. Any thread.
     */
    private void end(Link link, Page gone) {
        Party opener, popup;
        List<Subscription> watched;
        synchronized (lock) {
            if (!links.remove(link.id, link)) return;
            opener = link.opener == gone ? null : link.openerParty;
            popup = link.popup == gone ? null : link.popupParty;
            watched = link.clear();
        }
        watched.forEach(OpenerBridge::cancel);
        String closed = call("closed", Long.toString(link.id));
        tell(opener, closed);
        tell(popup, closed);
    }

    /** The page script runs in the main frame of the page {@code browser} shows, which starts loading a document. */
    private void loadStarting(PageBrowser browser, CefFrame frame) {
        Page page = browser.owner();
        if (page == null) return;
        try {
            frame.executeJavaScript(loadStarted(Party.of(browser, page), frame.getURL()), SCRIPT_URL, 0);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The opener bridge could not reach a page", failure);
        }
    }

    /** Runs {@code script} in a party's document; a failure there, such as a browser that has gone, is only logged. */
    private static void tell(Party party, String script) {
        if (party == null) return;
        try {
            party.scripts().accept(script);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The opener bridge could not reach a page", failure);
        }
    }

    private static void cancel(Subscription subscription) {
        if (subscription != null) subscription.close();
    }

    /** {@code url} without its fragment, which no response carries. */
    private static String withoutFragment(String url) {
        if (url == null) return "";
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    /** Whether {@code request} has no member but {@code op} and those named. */
    private static boolean only(Map<String, Object> request, String... names) {
        Set<String> allowed = Arrays.stream(names).collect(Collectors.toSet());
        return request.keySet().stream().allMatch(name -> name.equals("op") || allowed.contains(name));
    }

    /** The link id a request names: digits Jasper issued, with no leading zero; 0 for anything else. */
    private static long id(Map<String, Object> request) {
        if (!(request.get("id") instanceof String text) || !text.matches("[1-9][0-9]{0,18}")) return 0;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException tooLarge) {
            return 0;
        }
    }

    /** A script that calls the page's bridge's {@code method} with {@code arguments}, each an escaped string literal. */
    private static String call(String method, String... arguments) {
        return "(function(b){if(b)b." + method + "(" + Arrays.stream(arguments).map(BridgeJson::quote).collect(Collectors.joining(","))
            + ");})(" + BRIDGE + ");";
    }

    private static String source() {
        try (InputStream in = OpenerBridge.class.getResourceAsStream("opener-bridge.js")) {
            if (in == null) throw new IllegalStateException("The Browser plugin has no opener-bridge.js");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /**
     * The client's one handler for the page function. On Chromium's thread it answers at once what it refuses (a request
     * from anything but a page, one that stays open for more answers, and what {@link #admit} refuses), and hands the
     * rest to the UI thread. Every query is answered, once.
     */
    private static final class Queries extends CefMessageRouterHandlerAdapter {
        private final OpenerBridge bridge;

        Queries(OpenerBridge bridge) {
            this.bridge = bridge;
        }

        @Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent,
                                         CefQueryCallback callback) {
            Page page = null;
            boolean admitted = false;
            try {
                PageBrowser shown = browser instanceof PageBrowser candidate ? candidate : null;
                page = shown == null ? null : shown.owner();
                if (page == null || persistent) {
                    reply(callback, new Answer(REFUSED, "Jasper's opener bridge answers only a page's single requests."));
                    return true;
                }
                boolean main = frame != null && frame.isMain();
                Optional<Answer> refused = bridge.admit(page, main, request);
                if (refused.isPresent()) {
                    reply(callback, refused.get());
                    return true;
                }
                admitted = true;
                Party sender = Party.of(shown, page);
                Sender from = bridge.sender(page, frame::getURL);
                long now = System.currentTimeMillis();
                EventQueue.invokeLater(() -> bridge.answer(sender, from, request, now, answer -> reply(callback, answer)));
                return true;
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "The opener bridge could not take a page's request", failure);
                if (admitted) bridge.settled(page);
                reply(callback, new Answer(REFUSED, "Jasper could not answer this request."));
                return true;
            }
        }

        private static void reply(CefQueryCallback callback, Answer answer) {
            try {
                if (answer.ok()) callback.success(answer.text());
                else callback.failure(answer.code(), answer.text());
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "The opener bridge could not answer a page", failure);
            }
        }
    }

    /**
     * A main frame's navigation request: its response's opener policy and content security policies go to the bridge,
     * and nothing about the load changes. Chromium's IO thread.
     */
    private static final class Responses extends CefResourceRequestHandlerAdapter {
        private final OpenerBridge bridge;

        Responses(OpenerBridge bridge) {
            this.bridge = bridge;
        }

        @Override public boolean onResourceResponse(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response) {
            try {
                Page page = browser instanceof PageBrowser shown ? shown.owner() : null;
                if (page != null && request.getResourceType() == CefRequest.ResourceType.RT_MAIN_FRAME)
                    bridge.responded(page, request.getURL(), response.getHeaderByName(OPENER_POLICY), contentPolicies(response));
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "The opener bridge could not read a page's response", failure);
            }
            return false;
        }

        /**
         * The response's {@code Content-Security-Policy} headers. JCEF gives the first of a repeated header by name and,
         * in its header map, one of them per name, so both are read.
         */
        private static List<String> contentPolicies(CefResponse response) {
            var found = new java.util.ArrayList<String>();
            String first = response.getHeaderByName(CONTENT_POLICY);
            if (first != null && !first.isEmpty()) found.add(first);
            Map<String, String> headers = new HashMap<>();
            response.getHeaderMap(headers);
            headers.forEach((name, value) -> {
                if (CONTENT_POLICY.equalsIgnoreCase(name) && value != null) found.add(value);
            });
            return found;
        }
    }
}
