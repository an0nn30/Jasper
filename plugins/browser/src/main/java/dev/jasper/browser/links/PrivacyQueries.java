package dev.jasper.browser.links;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageBrowser;
import dev.jasper.browser.page.PagePrivacy;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Origins;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.permissions.PermissionRequest;
import java.awt.EventQueue;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

/**
 * The page scripts' privacy channel to Java (spec section 12): the pages' client's own message router, whose page
 * function is {@value #QUERY} and whose cancel is {@value #CANCEL}, beside the opener bridge's and Jasper's pages'. The
 * {@code page} package's {@code notifications.js}, which stands in for a page's {@code window.Notification}, asks
 * through it, and each answer comes from the asking page's {@link PagePrivacy}: its item's stored decisions, its
 * item's prompt and its item's notifications.
 *
 * <p>It is a router of its own because a permission request can wait minutes for the user's answer. The opener bridge
 * trusts each op separately, from the frames the op names, but keeps only a few of a page's requests waiting, and then
 * refuses the page's next as busy, so there a request would hold the page's favicon, {@code window.open} and messages
 * behind it.
 *
 * <p>CEF adds the page function to every frame of every page, and the script runs in the page's own world, so every
 * request is untrusted (R38): a page's own scripts can call the function, or change the stand-in, and can tell from
 * the function that the page runs in Jasper. A request is one flat JSON object of at most {@value #LONGEST_REQUEST}
 * characters, {@code {"op": …}} with only its op's members. On Chromium's thread a query that stays open for more
 * answers, one from a browser that is no page's or from no frame, one from a sub-frame, where the stand-in never runs,
 * and more than {@value #WAITING_LIMIT} of one page's waiting are refused, with {@value #REFUSED}, and one too long or
 * none is malformed, with {@value #MALFORMED}, there and then; the rest goes to the UI thread with the address of the
 * frame that asked, read as Chromium names it for the query, whose origin is the site the request is judged for (ruling
 * V16: the main frame's is the page's top-level site), and with whether a sandbox made that document's origin opaque,
 * as the client's {@link OpenerBridge} recorded the document then. A document has no site that may have notifications
 * in a /dev/null page (ruling V20), outside a secure context, a {@code file:} page included (ruling V21), or with an
 * opaque origin (ruling V22). There an unknown op or member is malformed and a request the page may not make is
 * refused. Nothing a page sends makes it throw. The ops:
 * <ul>
 *   <li>{@code notification.request}: a document without a site that may is {@code "denied"}, asking nothing; the
 *   decision the item's store holds answers at once, {@code "granted"} or {@code "denied"}; otherwise the item is
 *   asked for {@link Permission#NOTIFICATIONS}, through its prompt and its rules for dismissals and floods, and answers
 *   {@code "granted"}, {@code "denied"} once Blocked is stored, or {@code "default"}, as for a dismissed prompt.</li>
 *   <li>{@code notification.show {id, title, body}}: shown only when the item's store holds Allowed for the site, and
 *   the item shows it; otherwise refused, whatever the script claims.</li>
 *   <li>{@code notification.close {id}}: the item takes away the notification the document named so.</li>
 * </ul>
 * A query its page cancels, or that ends as its document goes, is never answered, and none is once Chromium has
 * stopped (ruling F43). {@link PageNavigation}'s installer adds the router to the pages' client, never the preview
 * client, with the client's bridge.
 */
final class PrivacyQueries {
    /** The page function the router adds to every frame, and the one that cancels a query. */
    static final String QUERY = "jasperPrivacyQuery", CANCEL = "jasperPrivacyCancel";
    /** The longest request taken, in characters. */
    static final int LONGEST_REQUEST = 65_536;
    /** The most of one page's requests that wait for their answers at once. */
    static final int WAITING_LIMIT = 16;
    /** Why a request failed, as the page's script is told: not a request Jasper knows, or not allowed. */
    static final int MALFORMED = 1, REFUSED = 2;
    /** What a request that succeeds with nothing to say is answered. */
    static final Answer DONE = new Answer(0, "");
    static final Answer MALFORMED_REQUEST = new Answer(MALFORMED, "Jasper does not understand this request.");
    private static final String GRANTED = "granted", DENIED = "denied", DEFAULT = "default";
    // The longest name a script gives a notification.
    private static final int LONGEST_ID = 64;
    private static final Answer NOT_A_PAGE = new Answer(REFUSED, "Jasper answers only a page's single requests."),
        SUB_FRAME = new Answer(REFUSED, "Only a page's top frame can ask Jasper about notifications."),
        TOO_MANY = new Answer(REFUSED, "Too many of this page's requests are waiting."),
        NOT_ALLOWED = new Answer(REFUSED, "This site may not show notifications."),
        NOT_SHOWN = new Answer(REFUSED, "Jasper did not show the notification."),
        UNANSWERED = new Answer(REFUSED, "Jasper could not answer this request.");
    private static final System.Logger LOG = System.getLogger(PrivacyQueries.class.getName());
    /** The ops, which a router hands each request it takes. */
    PrivacyQueries() { }

    /**
     * Adds the router to {@code client}, the pages' client, before any page is made on it, reading what {@code bridge}
     * records of each page's document: {@link PageNavigation}'s installer adds it, once per client, with the client's
     * bridge.
     *
     * @param client the pages' client
     * @param bridge the client's opener bridge
     * @param chromium the plugin's Chromium, whose stop ends every answer
     */
    static void install(CefClient client, OpenerBridge bridge, Chromium chromium) {
        client.addMessageRouter(CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig(QUERY, CANCEL),
            new Router(new PrivacyQueries(), bridge, PrivacyQueries::page, EventQueue::invokeLater,
                () -> chromium.state() == Chromium.State.STOPPED)));
    }

    /** The page {@code browser} shows; null for a browser that is not a page's. Chromium's thread. */
    private static Page page(CefBrowser browser) {
        return browser instanceof PageBrowser shown ? shown.owner() : null;
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

    /** What a request asks, by its {@code op}. */
    private enum Op {
        REQUEST("notification.request"), SHOW("notification.show"), CLOSE("notification.close");

        private final String name;

        Op(String name) {
            this.name = name;
        }

        /** The op {@code request} names, or null for none Jasper knows. */
        static Op of(Map<String, Object> request) {
            Object name = request.get("op");
            for (Op op : values()) if (op.name.equals(name)) return op;
            return null;
        }
    }

    /**
     * Answers a request of {@code page}'s frame: {@code reply} is told the answer once, on the UI thread, at once for
     * every request but a {@code notification.request} the item asks the user about. UI thread; nothing a page sent
     * makes it throw.
     *
     * @param page the page that asked
     * @param mainFrame whether its main frame asked; the router refuses a sub-frame's itself and always passes true, so
     *     false comes from tests only
     * @param address the address of the frame that asked, as Chromium's thread read it
     * @param opaque whether a sandbox made that document's origin opaque, as the opener bridge recorded it then
     * @param text the request, as the page's script sent it
     * @param reply told the answer
     */
    void request(Page page, boolean mainFrame, String address, boolean opaque, String text, Consumer<Answer> reply) {
        Consumer<Answer> once = once(reply);
        try {
            if (!mainFrame) {
                once.accept(SUB_FRAME);
                return;
            }
            Map<String, Object> request = text == null || text.length() > LONGEST_REQUEST ? null
                : BridgeJson.object(text).orElse(null);
            Op op = request == null ? null : Op.of(request);
            if (op == null) {
                once.accept(MALFORMED_REQUEST);
                return;
            }
            Optional<String> origin = site(page, address, opaque);
            PagePrivacy privacy = page.privacy();
            switch (op) {
                case REQUEST -> permission(privacy, origin, request, once);
                case SHOW -> once.accept(show(privacy, origin, request));
                case CLOSE -> once.accept(close(privacy, request));
            }
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's privacy request could not be answered", failure);
            once.accept(UNANSWERED);
        }
    }

    /**
     * The site a document at {@code address} of {@code page} asks for: its address's origin, but none, so that it is
     * denied and shows nothing, for a /dev/null page, as Chrome's Incognito denies notifications (ruling V20), outside a
     * secure context, as in Chromium, a {@code file:} page included (ruling V21), and for a document whose origin a
     * sandbox made opaque (ruling V22).
     */
    private static Optional<String> site(Page page, String address, boolean opaque) {
        if (page.inMemory() || opaque) return Optional.empty();
        return Origins.of(address).filter(Origins::secure);
    }

    /** {@code notification.request}: the stored decision, or the item's answer. */
    private static void permission(PagePrivacy privacy, Optional<String> origin, Map<String, Object> request, Consumer<Answer> reply) {
        if (!only(request)) {
            reply.accept(MALFORMED_REQUEST);
            return;
        }
        if (origin.isEmpty()) {
            reply.accept(new Answer(0, DENIED));
            return;
        }
        Optional<Decision> stored = privacy.storedDecision(origin.get(), Permission.NOTIFICATIONS);
        if (stored.isPresent()) {
            reply.accept(new Answer(0, stored.get() == Decision.ALLOWED ? GRANTED : DENIED));
            return;
        }
        privacy.request(new PermissionRequest(origin.get(), Permission.NOTIFICATIONS), allowed -> {
            try {
                // Not allowed: denied once the user's Block is stored, and otherwise as dismissed.
                boolean blocked = !allowed
                    && privacy.storedDecision(origin.get(), Permission.NOTIFICATIONS).filter(Decision.BLOCKED::equals).isPresent();
                reply.accept(new Answer(0, allowed ? GRANTED : blocked ? DENIED : DEFAULT));
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "A page's notification permission could not be answered", failure);
                reply.accept(UNANSWERED);
            }
        });
    }

    /** {@code notification.show}: shown only with a stored Allow for the site. */
    private static Answer show(PagePrivacy privacy, Optional<String> origin, Map<String, Object> request) {
        if (!only(request, "id", "title", "body") || !(request.get("id") instanceof String id) || !named(id)
                || !(request.get("title") instanceof String title) || !(request.get("body") instanceof String body))
            return MALFORMED_REQUEST;
        if (origin.isEmpty() || privacy.storedDecision(origin.get(), Permission.NOTIFICATIONS).filter(Decision.ALLOWED::equals).isEmpty())
            return NOT_ALLOWED;
        return privacy.showNotification(new PagePrivacy.Notice(origin.get(), id, title, body)) ? DONE : NOT_SHOWN;
    }

    /** {@code notification.close}: the item takes the notification away. */
    private static Answer close(PagePrivacy privacy, Map<String, Object> request) {
        if (!only(request, "id") || !(request.get("id") instanceof String id) || !named(id)) return MALFORMED_REQUEST;
        privacy.closeNotification(id);
        return DONE;
    }

    /** Whether {@code request} has no member but {@code op} and those named, and has each of those. */
    private static boolean only(Map<String, Object> request, String... names) {
        if (request.size() != names.length + 1) return false;
        for (String name : names) if (!request.containsKey(name)) return false;
        return true;
    }

    /** Whether {@code id} is a name a script may give a notification: 1 to {@value #LONGEST_ID} characters. */
    private static boolean named(String id) {
        return !id.isEmpty() && id.length() <= LONGEST_ID;
    }

    /** {@code reply}, told the first answer only. */
    private static Consumer<Answer> once(Consumer<Answer> reply) {
        var answered = new AtomicBoolean();
        return answer -> { if (answered.compareAndSet(false, true)) reply.accept(answer); };
    }

    /**
     * The client's one handler for the page function: on Chromium's thread it answers at once what it refuses, as the
     * class says, and hands the rest to the UI thread. Every query is answered once, unless it is cancelled first or
     * Chromium has stopped.
     */
    static final class Router extends CefMessageRouterHandlerAdapter {
        private final PrivacyQueries queries;
        private final OpenerBridge bridge;
        private final Function<CefBrowser, Page> pages;
        private final Executor ui;
        private final BooleanSupplier stopped;
        // The queries not yet answered or cancelled, by Chromium's id; and how many of each page's wait.
        private final Map<Long, Asked> waiting = new ConcurrentHashMap<>();
        private final Map<Page, Integer> counts = new ConcurrentHashMap<>();

        /**
         * A router handing its queries to {@code queries}.
         *
         * @param queries the ops
         * @param bridge the client's opener bridge, whose record of each page's document says whether a sandbox made
         *     its origin opaque
         * @param pages the page a browser shows, or null for a browser that is no page's; Chromium's thread
         * @param ui runs a task on the UI thread
         * @param stopped whether Chromium has stopped; any thread
         */
        Router(PrivacyQueries queries, OpenerBridge bridge, Function<CefBrowser, Page> pages, Executor ui,
               BooleanSupplier stopped) {
            this.queries = queries;
            this.bridge = bridge;
            this.pages = pages;
            this.ui = ui;
            this.stopped = stopped;
        }

        @Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent,
                                         CefQueryCallback callback) {
            var asked = new Asked(callback, stopped);
            try {
                Page page = browser == null ? null : pages.apply(browser);
                if (page == null || persistent || frame == null) {
                    asked.reply(NOT_A_PAGE);
                    return true;
                }
                if (request == null || request.length() > LONGEST_REQUEST) {
                    asked.reply(MALFORMED_REQUEST);
                    return true;
                }
                if (!frame.isMain()) {
                    asked.reply(SUB_FRAME);
                    return true;
                }
                if (!admit(page)) {
                    asked.reply(TOO_MANY);
                    return true;
                }
                asked.settles(() -> settled(page));
                // As Chromium names it for this query, with what the bridge knows of the document then, read together:
                // the request is judged by the document that asked.
                OpenerBridge.Sender from = bridge.sender(page, frame.getIdentifier(), frame::getURL);
                String address = from.address();
                boolean opaque = from.document().sandbox().opaque();
                waiting.put(queryId, asked);
                ui.execute(() -> queries.request(page, true, address, opaque, request, answer -> {
                    waiting.remove(queryId, asked);
                    asked.reply(answer);
                }));
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.DEBUG, "A page's privacy request could not be taken", failure);
                waiting.remove(queryId, asked);
                asked.reply(UNANSWERED);
            }
            return true;
        }

        /** The page cancelled the query, or it ended with its document: it is never answered. */
        @Override public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
            Asked asked = waiting.remove(queryId);
            if (asked != null) asked.cancel();
        }

        /** Whether {@code page} may have one more query waiting, which then counts. */
        private boolean admit(Page page) {
            boolean[] admitted = new boolean[1];
            counts.compute(page, (key, count) -> {
                int now = count == null ? 0 : count;
                if (now >= WAITING_LIMIT) return count;
                admitted[0] = true;
                return now + 1;
            });
            return admitted[0];
        }

        /** One of {@code page}'s queries no longer waits; the page leaves once none does. */
        private void settled(Page page) {
            counts.computeIfPresent(page, (key, count) -> count <= 1 ? null : count - 1);
        }
    }

    /** One query: answered once, unless it was cancelled first, and never once Chromium has stopped. */
    private static final class Asked {
        private final CefQueryCallback callback;
        private final BooleanSupplier stopped;
        // Guarded by this: whether it was answered or cancelled, and what then no longer counts it as waiting.
        private boolean over;
        private Runnable settle = () -> { };

        Asked(CefQueryCallback callback, BooleanSupplier stopped) {
            this.callback = callback;
            this.stopped = stopped;
        }

        /** Once it is over, {@code settle} runs, once. */
        synchronized void settles(Runnable settle) {
            this.settle = settle;
        }

        void cancel() {
            end();
        }

        void reply(Answer answer) {
            if (!end() || stopped.getAsBoolean()) return;
            try {
                if (answer.ok()) callback.success(answer.text());
                else callback.failure(answer.code(), answer.text());
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "A page's privacy request could not be answered", failure);
            }
        }

        /** Ends it: false when it was over already. */
        private boolean end() {
            Runnable settled;
            synchronized (this) {
                if (over) return false;
                over = true;
                settled = settle;
            }
            settled.run();
            return true;
        }
    }
}
