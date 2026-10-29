package dev.jasper.browser.links;

import dev.jasper.browser.page.Activation;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageBrowser;
import dev.jasper.browser.page.PageEvents;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.EventQueue;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

/**
 * Popups that keep a working {@code window.opener} (spec section 8). JCEF lets Chromium make a popup or cancels it, and
 * cannot host one beside its opener, so Jasper opens a page's {@code window.open} as an item of its own, next to the
 * page's tab, and links the two pages. {@code opener-bridge.js}, which runs in each page's top frame as each document
 * starts loading, stands in for the window each page would hold of the other, and this class relays between them
 * through a message router whose page function is {@code jasperBridgeQuery}: it opens the popup, delivers messages,
 * closes the popup, shows either page's item, and tells each page when the other closes. A link lives until both of its
 * pages have closed; after one has, the other's stand-in reports it closed and its messages go nowhere.
 *
 * <p>Every frame and every script of a page can call the page function, so each request is untrusted. Only a page's
 * top frame may ask, as the bridge lives there and nowhere else (spec section 5 keeps page scripts out of challenge
 * frames). An {@code open} needs the page's own {@link Activation}, which Jasper keeps from what it forwarded to the
 * page, and consumes it, as a popup does, and opens only an address of a scheme the item loads. Link ids are random,
 * issued here, and every other request must come from one of the link's two pages. A message's data is at most
 * {@value #MESSAGE_LIMIT} characters of JSON, reaches the other page's script only as an escaped string literal, and is
 * delivered only when its target origin is {@code "*"} or the receiving page's origin at that moment.
 *
 * <p>Chromium asks on its own thread; each request is answered on the UI thread, where items open. A page's load
 * start is answered at once on Chromium's thread, so the script runs before Swing would hear of the load.
 */
final class OpenerBridge {
    /** The page function the router adds to every frame, and the one that cancels a request. */
    static final String QUERY = "jasperBridgeQuery", CANCEL = "jasperBridgeCancel";
    /** The most characters of JSON one message may hold: 1 MiB. */
    static final int MESSAGE_LIMIT = 1 << 20;
    /** Why a request failed, as the page's script is told: not a request, not allowed, the other page closed, too long. */
    static final int MALFORMED = 1, REFUSED = 2, GONE = 3, TOO_LARGE = 4;
    /** The page script: a function of its configuration. */
    static final String SOURCE = source();
    // The longest request a message of MESSAGE_LIMIT makes: the script escapes JSON's quotes and backslashes once more.
    private static final int REQUEST_LIMIT = 2 * MESSAGE_LIMIT + 4_096;
    // What every page that is no popup of a live link runs: window.open's stand-in.
    private static final String PAGE = SOURCE + "\n({});";
    // The name the script goes by in a page's console and stack traces.
    private static final String SCRIPT_URL = "jasper-about:opener-bridge.js";
    private static final String BRIDGE = "window[Symbol.for(\"dev.jasper.browser.opener-bridge\")]";
    private static final Pattern WEB = Pattern.compile("(?i)(https?)://(?:[^/?#]*@)?(\\[[^\\]/?#]*\\]|[^:/?#\\[\\]@]+)(?::([0-9]*))?(?:[/?#].*)?",
        Pattern.DOTALL);
    private static final Answer DONE = new Answer(0, ""),
        MALFORMED_REQUEST = new Answer(MALFORMED, "Jasper does not understand this request."),
        TOO_LONG = new Answer(TOO_LARGE, "Jasper delivers messages of up to 1 MiB of JSON."),
        NOT_PARTY = new Answer(REFUSED, "This page is not part of that link."),
        OTHER_CLOSED = new Answer(GONE, "The other page is closed.");
    private static final System.Logger LOG = System.getLogger(OpenerBridge.class.getName());

    private final SecureRandom random = new SecureRandom();
    // Guards the links and every link's state: requests and closes come on the UI thread, load starts on Chromium's.
    private final Object lock = new Object();
    private final Map<Long, Link> links = new HashMap<>();

    /** A bridge with no links, which no page reaches until {@link #install} wires it to a client. */
    OpenerBridge() { }

    /**
     * Wires a bridge to {@code client} before any page is made on it: a message router with the page function
     * {@value #QUERY}, and a hook on each main frame's load start that runs the page script there. Once per client.
     *
     * @param client the client every page is made on
     */
    static void install(CefClient client) {
        var bridge = new OpenerBridge();
        client.addMessageRouter(CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig(QUERY, CANCEL), new Queries(bridge)));
        PageEvents.onMainFrameLoadStart(client, bridge::loadStarting);
    }

    /**
     * A page as the bridge reaches it: the page, its activation, how a script reaches the document its main frame shows
     * now, and where that document is. A page's browser gives one; a test gives a recording one.
     *
     * @param page the page, which is one end of a link or asks to be
     * @param activation the page's user activation, as Jasper forwarded the user's presses and keys to it
     * @param scripts runs a script in the main frame's current document
     * @param address the main frame's current address
     */
    record Party(Page page, Activation activation, Consumer<String> scripts, Supplier<String> address) {
        Party {
            Objects.requireNonNull(page, "page");
            Objects.requireNonNull(activation, "activation");
            Objects.requireNonNull(scripts, "scripts");
            Objects.requireNonNull(address, "address");
        }

        /** The page {@code browser} shows, which has its page. Any thread. */
        static Party of(PageBrowser browser) {
            return new Party(browser.owner(), browser.view().activation(), script -> browser.executeJavaScript(script, SCRIPT_URL, 0),
                browser::getURL);
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

    /** One popup and the page whose {@code window.open} opened it; all but the pages guarded by the bridge's lock. */
    private static final class Link {
        final long id;
        final Page opener, popup;
        // How each page's document is reached, once known: the opener's from its request, the popup's from its load.
        Party openerParty, popupParty;
        boolean openerClosed, popupClosed;

        Link(long id, Party opener, Page popup) {
            this.id = id;
            this.opener = opener.page();
            this.popup = popup;
            openerParty = opener;
        }
    }

    /**
     * Answers a page's request. UI thread; it never throws for what a page sent.
     *
     * @param sender the page that asked
     * @param mainFrame whether its main frame asked, rather than a sub-frame
     * @param senderAddress the address of the document that asked, as Chromium gave it with the request
     * @param text the request, as the page's script sent it
     * @param nowMillis when it asked, in milliseconds since the epoch
     * @return the answer
     */
    Answer request(Party sender, boolean mainFrame, String senderAddress, String text, long nowMillis) {
        if (!mainFrame) return new Answer(REFUSED, "Only a page's top frame can use Jasper's opener bridge.");
        if (text != null && text.length() > REQUEST_LIMIT) return TOO_LONG;
        Map<String, Object> request = BridgeJson.object(text).orElse(null);
        if (request == null) return MALFORMED_REQUEST;
        return switch (request.get("op") instanceof String op ? op : "") {
            case "open" -> open(sender, request, nowMillis);
            case "post" -> post(sender, senderAddress, request);
            case "close" -> close(sender, request);
            case "focus" -> focus(sender, request, nowMillis);
            default -> MALFORMED_REQUEST;
        };
    }

    /**
     * The script a page's main frame runs as it starts loading a document: in a popup of a live link, the opener's
     * stand-in, reporting whether the opener has closed; in every other page, only {@code window.open}'s. A popup's
     * document is reached from then on. Any thread.
     *
     * @param page the page that starts loading
     * @return the script
     */
    String loadStarted(Party page) {
        synchronized (lock) {
            for (Link link : links.values()) {
                if (link.popup != page.page() || link.popupClosed) continue;
                link.popupParty = page;
                return SOURCE + "\n({\"opener\":" + BridgeJson.quote(Long.toString(link.id)) + ",\"closed\":" + link.openerClosed + "});";
            }
        }
        return PAGE;
    }

    /**
     * The links that live: those with at least one page open.
     *
     * @return how many
     */
    int links() {
        synchronized (lock) {
            return links.size();
        }
    }

    /**
     * The origin of a web address: its scheme, host and port, the scheme's own port left out, as a page's
     * {@code location.origin} gives it for the canonical address Chromium reports.
     *
     * @param url an address
     * @return the origin; empty for an address of another scheme, whose origin no target origin but {@code "*"} matches
     */
    static Optional<String> origin(String url) {
        if (url == null) return Optional.empty();
        Matcher web = WEB.matcher(url);
        if (!web.matches()) return Optional.empty();
        String scheme = web.group(1).toLowerCase(Locale.ROOT), port = web.group(3);
        boolean usual = port == null || port.isEmpty() || port.equals(scheme.equals("http") ? "80" : "443");
        return Optional.of(scheme + "://" + web.group(2).toLowerCase(Locale.ROOT) + (usual ? "" : ":" + port));
    }

    /**
     * {@code window.open}: with the page's activation, which it consumes, opens the address in a new tab next to the
     * page's and links the two, answering the link's id; with {@code noopener}, opens it without a link.
     */
    private Answer open(Party sender, Map<String, Object> request, long nowMillis) {
        if (!only(request, "url", "noopener") || !(request.get("url") instanceof String url)
                || !(request.getOrDefault("noopener", Boolean.FALSE) instanceof Boolean detached))
            return MALFORMED_REQUEST;
        if (!LinkRoute.opens(url)) return new Answer(REFUSED, "Jasper opens only http, https, file, about and jasper-about addresses.");
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
        }
        link.opener.onClose(() -> closed(link, true));
        link.popup.onClose(() -> closed(link, false));
        return new Answer(0, Long.toString(link.id));
    }

    /**
     * {@code postMessage}: delivers the data to the other page of the link, as a message from the sender's origin, when
     * the target origin is {@code "*"} or that page's origin now. A message to a popup that has not started loading
     * is lost, as one to a popup's first empty page is, and the answer never tells whether the origin matched.
     */
    private Answer post(Party sender, String senderAddress, Map<String, Object> request) {
        if (!only(request, "id", "targetOrigin", "data") || !(request.get("targetOrigin") instanceof String target)
                || !(request.get("data") instanceof String data))
            return MALFORMED_REQUEST;
        long id = id(request);
        if (id == 0) return MALFORMED_REQUEST;
        if (data.length() > MESSAGE_LIMIT) return TOO_LONG;
        Link link;
        boolean fromOpener;
        Party receiver;
        synchronized (lock) {
            link = links.get(id);
            if (!party(link, sender.page())) return NOT_PARTY;
            if (link.openerClosed || link.popupClosed) return OTHER_CLOSED;
            fromOpener = link.opener == sender.page();
            receiver = fromOpener ? link.popupParty : link.openerParty;
        }
        if (receiver == null) return DONE;
        Optional<String> origin = origin(receiver.address().get());
        if (!target.equals("*") && !origin.map(target::equals).orElse(false)) return DONE;
        receiver.scripts().accept(call("deliver", Long.toString(link.id), fromOpener ? "opener" : "popup", data,
            origin(senderAddress).orElse("null"), target));
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
            if (!party(link, sender.page())) return NOT_PARTY;
            if (link.popupClosed || (link.opener == sender.page() && link.openerClosed)) return OTHER_CLOSED;
            popup = link.popup;
        }
        popup.closeItem();
        return DONE;
    }

    /**
     * {@code focus()}: shows the other page's item. Chromium lets a window's opener focus it at any time, and anything
     * else only with the user's activation, so the popup shows its opener only while its own activation holds; focusing
     * consumes none.
     */
    private Answer focus(Party sender, Map<String, Object> request, long nowMillis) {
        if (!only(request, "id")) return MALFORMED_REQUEST;
        long id = id(request);
        if (id == 0) return MALFORMED_REQUEST;
        Page other;
        boolean fromPopup;
        synchronized (lock) {
            Link link = links.get(id);
            if (!party(link, sender.page())) return NOT_PARTY;
            if (link.openerClosed || link.popupClosed) return OTHER_CLOSED;
            fromPopup = link.popup == sender.page();
            other = fromPopup ? link.opener : link.popup;
        }
        if (fromPopup && !sender.activation().active(nowMillis))
            return new Answer(REFUSED, "A page can bring its opener forward only after a click or key press in it.");
        other.showItem();
        return DONE;
    }

    /** Whether {@code link} exists and {@code page} is one of its two pages: only they may use it. */
    private static boolean party(Link link, Page page) {
        return link != null && (link.opener == page || link.popup == page);
    }

    /** One page of {@code link} closed: the other's stand-in reports it closed, and the link goes once both have. UI thread. */
    private void closed(Link link, boolean openerClosed) {
        Party other;
        synchronized (lock) {
            if (openerClosed ? link.openerClosed : link.popupClosed) return;
            if (openerClosed) {
                link.openerClosed = true;
                link.openerParty = null;
                other = link.popupClosed ? null : link.popupParty;
            } else {
                link.popupClosed = true;
                link.popupParty = null;
                other = link.openerClosed ? null : link.openerParty;
            }
            if (link.openerClosed && link.popupClosed) links.remove(link.id);
        }
        if (other != null) other.scripts().accept(call("closed", Long.toString(link.id)));
    }

    /** The page {@code browser} shows starts loading a document in its main frame: the page script runs there. */
    private void loadStarting(PageBrowser browser, CefFrame frame) {
        if (browser.owner() == null) return;
        try {
            frame.executeJavaScript(loadStarted(Party.of(browser)), SCRIPT_URL, 0);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The opener bridge could not reach a page", failure);
        }
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
     * The client's one handler for the page function: a page's single requests, answered on the UI thread. A request
     * from anything but a page's browser, or one that stays open for more answers, is refused at once.
     */
    private static final class Queries extends CefMessageRouterHandlerAdapter {
        private final OpenerBridge bridge;

        Queries(OpenerBridge bridge) {
            this.bridge = bridge;
        }

        @Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent,
                                         CefQueryCallback callback) {
            if (!(browser instanceof PageBrowser page) || page.owner() == null || persistent) {
                callback.failure(REFUSED, "Jasper's opener bridge answers only a page's single requests.");
                return true;
            }
            boolean main = frame != null && frame.isMain();
            String address = main ? frame.getURL() : null;
            long now = System.currentTimeMillis();
            Party sender = Party.of(page);
            EventQueue.invokeLater(() -> {
                Answer answer;
                try {
                    answer = bridge.request(sender, main, address, request, now);
                } catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.WARNING, "The opener bridge could not answer a page", failure);
                    answer = new Answer(REFUSED, "Jasper could not answer this request.");
                }
                if (answer.ok()) callback.success(answer.text());
                else callback.failure(answer.code(), answer.text());
            });
            return true;
        }
    }
}
