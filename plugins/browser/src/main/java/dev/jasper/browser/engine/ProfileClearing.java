package dev.jasper.browser.engine;

import dev.jasper.browser.json.StrictJson;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.time.Duration;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsrWithHandler;
import org.cef.browser.CefDevToolsClient;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefRenderHandlerAdapter;
import org.cef.network.CefCookieManager;

/**
 * The JCEF side of a live Clear Cookies and Clear Website Data (plan 6 Task 8), in Chromium's global context, the
 * persistent store's; a /dev/null group's in-memory context is never touched. Clear Cookies reads every cookie's
 * domain, then deletes every cookie, then flushes the store, whose callback says the deletion is done, as the cookie
 * manager answers its calls in order. Clear Website Data then has a helper browser of its own, on the clearing client
 * {@link ChromiumApp} makes for it alone (F20), so no page's handler sees it: an off-screen browser at
 * {@code about:blank} with a 1 by 1 view, which clears the browser cache through its DevTools, then each origin's
 * storage ({@code Storage.clearDataForOrigin}, every storage type), with at most 8 calls in flight, each bounded by 5
 * seconds as {@code PageText}'s are, and closes once done; a site whose storage cannot be cleared blocks no other, and is
 * left to the next start's wipe (V26). Every call is made on the UI thread while Chromium runs, and
 * none once it has stopped. Loaded only while Chromium runs.
 */
final class ProfileClearing {
    /** How long each DevTools call may take before it fails. */
    static final Duration CALL_LIMIT = Duration.ofSeconds(5);
    /** The most DevTools calls in flight at once. */
    static final int IN_FLIGHT = 8;
    private static final System.Logger LOG = System.getLogger(ProfileClearing.class.getName());
    // A cookie's domain as a web site's host: letters, digits, hyphens and underscores in dot-separated labels.
    private static final Pattern DOMAIN = Pattern.compile("[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*");

    private ProfileClearing() { }

    /**
     * Deletes every cookie of the global context: reads their domains, deletes them, then flushes the store. Any thread.
     *
     * @param cookies the global cookie manager, or null before JCEF has initialized
     * @return the domains the cookies had, as written, once the store has flushed; failed when the manager refuses
     */
    static CompletableFuture<Set<String>> clearCookies(CefCookieManager cookies) {
        if (cookies == null) return CompletableFuture.failedFuture(new IllegalStateException("Chromium's cookies cannot be reached yet"));
        Set<String> domains = ConcurrentHashMap.newKeySet();
        var done = new CompletableFuture<Set<String>>();
        try {
            // With no cookie, the visitor is never called: the flush's callback, which follows the visits, ends the read.
            cookies.visitAllCookies((cookie, count, total, delete) -> {
                if (cookie != null && cookie.domain != null) domains.add(cookie.domain);
                return true;
            });
            if (!cookies.deleteCookies("", "")) throw new IllegalStateException("Chromium could not delete its cookies");
            if (!cookies.flushStore(() -> done.complete(Set.copyOf(domains))))
                throw new IllegalStateException("Chromium could not save its cookies' deletion");
        } catch (RuntimeException failure) {
            done.completeExceptionally(failure);
        }
        return done;
    }

    /**
     * Has the cookie domains' sites cleared too: each domain, without its leading dot, in lower case, as an https and an
     * http origin. A domain no web site's cookie could have, such as one with a space, a path or an IPv6 address, is
     * left out: the next start's wipe covers it.
     *
     * @param domains the cookies' domains, as Chromium wrote them
     * @return the origins
     */
    static Set<String> cookieOrigins(Collection<String> domains) {
        var origins = new LinkedHashSet<String>();
        for (String domain : domains) {
            String host = (domain.startsWith(".") ? domain.substring(1) : domain).toLowerCase(Locale.ROOT);
            if (!DOMAIN.matcher(host).matches()) continue;
            origins.add("https://" + host);
            origins.add("http://" + host);
        }
        return origins;
    }

    /**
     * The clearing client's one life span handler, installed as the client is made: tells each helper browser it is made.
     * UI thread.
     *
     * @param client the clearing client
     */
    static void install(CefClient client) {
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override public void onAfterCreated(CefBrowser browser) {
                if (browser instanceof Helper helper) helper.made();
            }
        });
    }

    /**
     * Clears the browser cache, then the storage of each of {@code origins}, through a helper browser on
     * {@code client}, which closes once done or given up. UI thread, while Chromium runs.
     *
     * @param client the clearing client
     * @param origins the origins, as {@code scheme://host[:port]}
     * @param running whether Chromium still runs; asked on the UI thread before each call
     * @return completes once every origin is cleared; fails as {@link #clearOrigins} does. Completing it otherwise, as a
     *     caller that gives up does, makes no further call
     */
    static CompletableFuture<Void> clearStorage(CefClient client, List<String> origins, BooleanSupplier running) {
        Helper helper;
        try {
            helper = new Helper(client);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        var cleared = new CompletableFuture<Void>();
        helper.made.thenRun(() -> EventQueue.invokeLater(() -> {
            if (cleared.isDone()) return;
            if (!running.getAsBoolean()) {
                cleared.completeExceptionally(new IllegalStateException(Chromium.ONCE));
                return;
            }
            CefDevToolsClient devTools = helper.getDevToolsClient();
            if (devTools == null) {
                cleared.completeExceptionally(new IllegalStateException("The clearing browser has no DevTools"));
                return;
            }
            CompletableFuture<Void> calls = clearOrigins((method, parameters) -> parameters == null ? devTools.executeDevToolsMethod(method)
                : devTools.executeDevToolsMethod(method, parameters), origins, EventQueue::invokeLater, running, CALL_LIMIT, IN_FLIGHT);
            calls.whenComplete((done, failure) -> {
                if (failure == null) cleared.complete(null);
                else cleared.completeExceptionally(failure);
            });
            // Given up: the calls left are not made.
            cleared.whenComplete((done, failure) -> calls.cancel(false));
        }));
        // Whether it ends, fails or is given up, the helper closes, on the UI thread, unless Chromium has stopped: its
        // dispose closes every client's browsers then.
        cleared.whenComplete((done, failure) -> EventQueue.invokeLater(() -> {
            if (running.getAsBoolean()) helper.close(true);
        }));
        try {
            helper.createImmediately();
        } catch (RuntimeException failure) {
            cleared.completeExceptionally(failure);
        }
        return cleared;
    }

    /**
     * Clears the browser cache, then each origin's storage, through {@code devTools}: every call is made through
     * {@code ui}, while {@code running}, with at most {@code inFlight} in flight, each failing past {@code limit}.
     *
     * @param devTools calls a DevTools method with its parameters, a JSON object or null for none, and answers what
     *     DevTools answers
     * @param origins the origins, in the order cleared
     * @param ui runs a task on the UI thread
     * @param running whether Chromium still runs
     * @param limit how long each call may take
     * @param inFlight the most calls in flight
     * @return completes once every call has answered, a site's that failed, was late, threw or answered nothing logged for
     *     debugging and left to the next start's wipe (V26); fails when the cache's call does, and once Chromium has
     *     stopped; completed by the caller, it makes no further call
     */
    static CompletableFuture<Void> clearOrigins(BiFunction<String, String, CompletableFuture<String>> devTools, List<String> origins,
                                                Executor ui, BooleanSupplier running, Duration limit, int inFlight) {
        var run = new Run(devTools, List.copyOf(origins).iterator(), ui, running, limit, inFlight);
        ui.execute(run::begin);
        return run.cleared;
    }

    /** One clearing's calls, made and counted on the UI thread. */
    private static final class Run {
        private final BiFunction<String, String, CompletableFuture<String>> devTools;
        private final Iterator<String> left;
        private final Executor ui;
        private final BooleanSupplier running;
        private final Duration limit;
        private final int most;
        private final CompletableFuture<Void> cleared = new CompletableFuture<>();
        // UI thread.
        private int inFlight;

        Run(BiFunction<String, String, CompletableFuture<String>> devTools, Iterator<String> left, Executor ui, BooleanSupplier running,
            Duration limit, int most) {
            this.devTools = Objects.requireNonNull(devTools, "devTools");
            this.left = left;
            this.ui = Objects.requireNonNull(ui, "ui");
            this.running = Objects.requireNonNull(running, "running");
            this.limit = Objects.requireNonNull(limit, "limit");
            this.most = most;
        }

        /** The browser cache first, then the origins. */
        void begin() {
            if (stopped()) return;
            call("Network.clearBrowserCache", null).whenComplete((answer, failure) -> ui.execute(() -> {
                if (failure != null) cleared.completeExceptionally(failure);
                else next();
            }));
        }

        /** Makes calls until {@link #most} are in flight or none is left; completes once the last has answered. */
        private void next() {
            while (!cleared.isDone() && inFlight < most && left.hasNext()) {
                if (stopped()) return;
                String origin = left.next();
                inFlight++;
                call("Storage.clearDataForOrigin", "{\"origin\":" + StrictJson.quote(origin) + ",\"storageTypes\":\"all\"}")
                    .whenComplete((answer, failure) -> ui.execute(() -> {
                        inFlight--;
                        // V26: one site blocks no other; the next start's wipe clears it. The site is not logged.
                        if (failure != null)
                            LOG.log(System.Logger.Level.DEBUG, "A site's storage could not be cleared; the next start's wipe clears it", failure);
                        next();
                    }));
            }
            if (inFlight == 0 && !left.hasNext()) cleared.complete(null);
        }

        /** Whether no call may be made now: the clearing is over, or Chromium has stopped, which fails it. */
        private boolean stopped() {
            if (cleared.isDone()) return true;
            if (running.getAsBoolean()) return false;
            cleared.completeExceptionally(new IllegalStateException(Chromium.ONCE));
            return true;
        }

        /** One DevTools call, failed when it throws, answers nothing or is late. */
        private CompletableFuture<String> call(String method, String parameters) {
            CompletableFuture<String> answer;
            try {
                answer = devTools.apply(method, parameters);
            } catch (RuntimeException failure) {
                return CompletableFuture.failedFuture(failure);
            }
            if (answer == null) return CompletableFuture.failedFuture(new IllegalStateException("DevTools gave no answer to " + method));
            return answer.orTimeout(limit.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * The helper browser: off screen at {@code about:blank} in the global context, drawn into a 1 by 1 view nobody sees,
     * on the clearing client. Its close goes ahead at once, without JCEF's question to a window it has none of; one
     * asked before Chromium has made it closes as it is made.
     */
    private static final class Helper extends CefBrowserOsrWithHandler {
        private final CompletableFuture<Void> made = new CompletableFuture<>();
        // Guarded by this.
        private boolean closeAsked, created;

        Helper(CefClient client) {
            super(client, "about:blank", null, new OnePixel());
        }

        /** Chromium has made the native browser. Chromium's thread. */
        void made() {
            boolean closeNow;
            synchronized (this) {
                created = true;
                closeNow = closeAsked;
            }
            if (closeNow) super.close(true);
            else made.complete(null);
        }

        @Override public void close(boolean force) {
            boolean closeNow;
            synchronized (this) {
                if (closeAsked) return;
                closeAsked = true;
                closeNow = created;
            }
            if (closeNow) super.close(force);
        }

        @Override public boolean doClose() {
            return false;
        }
    }

    /** A view of 1 by 1 pixels, whose frames are dropped. */
    private static final class OnePixel extends CefRenderHandlerAdapter {
        @Override public Rectangle getViewRect(CefBrowser browser) {
            return new Rectangle(0, 0, 1, 1);
        }
    }
}
