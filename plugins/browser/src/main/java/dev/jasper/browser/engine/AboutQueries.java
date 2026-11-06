package dev.jasper.browser.engine;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

/**
 * Jasper's pages' channel to Java (plan 5): the pages' client's own message router, whose page function is
 * {@value #QUERY} and whose cancel is {@value #CANCEL}, kept apart from the opener bridge's. A Jasper page's script asks
 * through it, and {@link AboutScheme#query} answers.
 *
 * <p>CEF adds the function to every frame of every browser on the client, web pages' too, so every query is untrusted
 * and {@link AboutScheme#query} alone decides which it answers: the main frame of the page that has the queries, with
 * that page's secret. On Chromium's thread the router decides only what it can without reading the request: a
 * persistent query, one longer than {@link AboutScheme#LONGEST_REQUEST}, one from a browser {@code jasper-about:} does
 * not answer ({@link Chromium#answersAbout}: a page's DevTools, or none), and one from a sub-frame or from a frame that
 * shows no {@code jasper-about:} address, as a web page's, are refused there and then. It reads the asking frame's
 * address, whether it is the main frame, and the browser's identifier, as Chromium names them for the query, and hands
 * the rest to the plugin's background executor: the secret, the page and the answer.
 *
 * <p>A query refused fails with {@value #REFUSED} and {@value #NOT_ITS_OWN}; an answer that fails, with
 * {@value #ANSWER_FAILED} and its reason. A query its page cancels, or that ends as the page goes, has its answer
 * cancelled and is never answered.
 */
public final class AboutQueries {
    /** The page function the router adds to every frame, and the one that cancels a query. */
    static final String QUERY = "jasperAboutQuery", CANCEL = "jasperAboutCancel";
    /** Why a query failed, as the page's script is told: its answer failed, or it was refused. */
    static final int ANSWER_FAILED = 1, REFUSED = 2;
    /** What a refused query is told. */
    static final String NOT_ITS_OWN = "Jasper's pages answer only themselves.";
    private static final String UNANSWERED = "Jasper could not answer this request.";
    private static final System.Logger LOG = System.getLogger(AboutQueries.class.getName());
    // The Chromiums given the router, held weakly: each client gets one router, however often a page asks.
    private static final Set<Chromium> PREPARED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private AboutQueries() { }

    /**
     * Has Chromium install the router on its pages' client, never its preview client, before any page is made on it;
     * once, however often asked. Call it when a page first asks for Chromium, never at the plugin's start: this class
     * names Chromium's types.
     *
     * @param chromium the plugin's Chromium
     * @param about the pages whose queries the router answers, those Chromium serves
     * @param background the plugin's background executor, where each query is read and answered
     */
    public static void prepare(Chromium chromium, AboutScheme about, Executor background) {
        Objects.requireNonNull(chromium, "chromium");
        Objects.requireNonNull(about, "about");
        Objects.requireNonNull(background, "background");
        if (PREPARED.add(chromium)) chromium.onClient(client -> install(client, about, chromium, background));
    }

    /** Adds the router to {@code client}, answering for the browsers {@code chromium} serves {@code jasper-about:} to. */
    static void install(CefClient client, AboutScheme about, Chromium chromium, Executor background) {
        client.addMessageRouter(CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig(QUERY, CANCEL),
            new Router(about, chromium::answersAbout, background)));
    }

    /** The router's handler: what Chromium hands it, decided as the class says. */
    static final class Router extends CefMessageRouterHandlerAdapter {
        private final AboutScheme about;
        private final Predicate<CefBrowser> pageBrowsers;
        private final Executor background;
        // The queries not yet answered or cancelled, by Chromium's id.
        private final Map<Long, Asked> waiting = new ConcurrentHashMap<>();

        /**
         * A router answering from {@code about}.
         *
         * @param about the pages' registry
         * @param pageBrowsers whether a browser is a page's, which {@code jasper-about:} answers; asked on Chromium's thread
         * @param background where each query is read and answered
         */
        Router(AboutScheme about, Predicate<CefBrowser> pageBrowsers, Executor background) {
            this.about = about;
            this.pageBrowsers = pageBrowsers;
            this.background = background;
        }

        @Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent,
                                         CefQueryCallback callback) {
            var asked = new Asked(callback);
            try {
                if (persistent || request == null || request.length() > AboutScheme.LONGEST_REQUEST || browser == null || frame == null
                        || !pageBrowsers.test(browser)) {
                    asked.fail(REFUSED, NOT_ITS_OWN);
                    return true;
                }
                // As Chromium names them for this query: the frame may show another document by the time it is read.
                String address = frame.getURL();
                boolean main = frame.isMain();
                if (!main || AboutScheme.page(address).isEmpty()) { // a sub-frame's, or a web page's: nothing to read
                    asked.fail(REFUSED, NOT_ITS_OWN);
                    return true;
                }
                int identifier = browser.getIdentifier();
                waiting.put(queryId, asked);
                background.execute(() -> answer(queryId, asked, address, main, identifier, request));
            } catch (RuntimeException failure) { // the background no longer takes work, as the plugin stops
                LOG.log(System.Logger.Level.DEBUG, "A Jasper page's query could not be answered", failure);
                waiting.remove(queryId, asked);
                asked.fail(ANSWER_FAILED, UNANSWERED);
            }
            return true;
        }

        /** The page cancelled the query, or it ended with its page: its answer is cancelled, and it is never answered. */
        @Override public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
            Asked asked = waiting.remove(queryId);
            if (asked != null) asked.cancel();
        }

        /** Reads and answers one query. The background executor. */
        private void answer(long queryId, Asked asked, String address, boolean main, int browser, String request) {
            if (asked.over()) return;
            Optional<CompletableFuture<String>> answer;
            try {
                answer = about.query(address, main, browser, request);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "A Jasper page's query failed", failure);
                answer = Optional.of(CompletableFuture.failedFuture(failure));
            }
            if (answer.isEmpty()) {
                waiting.remove(queryId, asked);
                asked.fail(REFUSED, NOT_ITS_OWN);
                return;
            }
            if (!asked.follow(answer.get())) return;
            answer.get().whenComplete((answered, failure) -> {
                waiting.remove(queryId, asked);
                if (failure == null && answered != null) asked.succeed(answered);
                else asked.fail(ANSWER_FAILED, reason(failure));
            });
        }

        /** Why an answer failed, as its page is told. */
        private static String reason(Throwable failure) {
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            if (cause == null || cause instanceof CancellationException || cause.getMessage() == null || cause.getMessage().isBlank())
                return UNANSWERED;
            return cause.getMessage();
        }
    }

    /** One query: answered once, unless it was cancelled first, which also cancels the answer it follows. */
    private static final class Asked {
        private final CefQueryCallback callback;
        // Guarded by this.
        private CompletableFuture<String> answer;
        private boolean over;

        Asked(CefQueryCallback callback) {
            this.callback = callback;
        }

        synchronized boolean over() {
            return over;
        }

        /** The query's answer is {@code future}: false, and the future cancelled, when the query was cancelled already. */
        boolean follow(CompletableFuture<String> future) {
            synchronized (this) {
                if (!over) {
                    answer = future;
                    return true;
                }
            }
            future.cancel(true);
            return false;
        }

        void cancel() {
            CompletableFuture<String> following;
            synchronized (this) {
                if (over) return;
                over = true;
                following = answer;
            }
            if (following != null) following.cancel(true);
        }

        void succeed(String answered) {
            reply(callback -> callback.success(answered));
        }

        void fail(int code, String reason) {
            reply(callback -> callback.failure(code, reason));
        }

        private void reply(Consumer<CefQueryCallback> reply) {
            synchronized (this) {
                if (over) return;
                over = true;
            }
            try {
                reply.accept(callback);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "A Jasper page could not be answered", failure);
            }
        }
    }
}
