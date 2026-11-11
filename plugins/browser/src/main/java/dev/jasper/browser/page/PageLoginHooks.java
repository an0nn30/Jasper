package dev.jasper.browser.page;

import dev.jasper.browser.permissions.Origins;
import dev.jasper.sdk.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * What a page gives its password manager (plan 8), which its {@link Page} holds and drives, as {@link PageToolHooks}
 * holds the tools' part: the login script each of its main frames runs as an http or https document starts loading,
 * read on Chromium's thread, and the handler its {@link PageQuery}s go to, with the queries handed to it that are not
 * yet over, which the page's close cancels; and its HTTP sign-ins: who hears each {@link SignIn} Chromium asks for, the
 * page's top address they are judged against and the number of its main frame's navigation requests, noted on
 * Chromium's threads, and who hears each final response to a main-frame navigation. It reaches no browser. UI thread,
 * but where a method says otherwise.
 */
final class PageLoginHooks {
    /** What a query is told when the page has no password manager to hand it to. */
    static final String NO_MANAGER = "This page has no password manager.";
    private static final System.Logger LOG = System.getLogger(PageLoginHooks.class.getName());
    // The login script, from the handler's arrival until the page closes; read on Chromium's thread.
    private volatile String script;
    private Consumer<PageQuery> handler;
    private boolean handled, closed;
    // The queries handed to the handler; those over leave as the next comes, and the rest are cancelled at the close.
    private final List<PageQuery> open = new ArrayList<>();
    // The top address: the main frame's latest navigation request, after its redirects, or its latest commit, whichever
    // came last; and that latest commit. Null before the first. Written on Chromium's threads, read on any.
    private final AtomicReference<String> top = new AtomicReference<>();
    private volatile String committedTop;
    // The main frame's navigation requests so far, their redirects not counted (L31). Written on Chromium's threads.
    private final AtomicLong navigations = new AtomicLong();
    private final List<Consumer<SignIn>> signIns = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<String, Integer>> responses = new CopyOnWriteArrayList<>();

    /**
     * Has {@code handler} answer the page's queries, and each main frame run {@code script} as an http or https
     * document starts loading, from now on until the page closes. Once.
     *
     * @throws IllegalStateException when the page has been given a handler already
     */
    void handle(String script, Consumer<PageQuery> handler) {
        if (handled) throw new IllegalStateException("The page's logins have a handler already");
        handled = true;
        if (closed) return;
        this.handler = handler;
        this.script = script;
    }

    /** Hands {@code query} to the handler, or fails it {@link PageQuery#REFUSED} when there is none or it closed. */
    void query(PageQuery query) {
        if (handler == null) {
            query.fail(PageQuery.REFUSED, NO_MANAGER);
            return;
        }
        open.removeIf(PageQuery::over);
        open.add(query);
        handler.accept(query);
    }

    /**
     * The script a main frame runs as it starts loading the document at {@code address}: the page's login script, once
     * its handler came and until it closes, where {@link #runsIn} allows it. Chromium's thread.
     *
     * @return the script, or empty for none
     */
    Optional<String> script(String address) {
        String given = script;
        return given != null && runsIn(address) ? Optional.of(given) : Optional.empty();
    }

    /**
     * Whether a main frame at {@code address} runs the login script: an http or https document with a host, but not a
     * challenge provider's ({@link ChallengeFrames}), which Jasper's page scripts stay out of. Jasper's own pages, a
     * file's, {@code about:blank}, {@code data:}, {@code blob:} and every other scheme get none. Pure; any thread.
     *
     * @param address the main frame's address, as Chromium names it
     * @return whether it does; false for none
     */
    static boolean runsIn(String address) {
        return Origins.of(address).filter(origin -> origin.startsWith("http://") || origin.startsWith("https://")).isPresent()
            && !ChallengeFrames.is(address);
    }

    /**
     * The main frame starts a navigation request for {@code url}: a new navigation, and the top address from now on. A
     * null address is not noted, but the request counts. Any thread.
     */
    void mainFrameRequested(String url) {
        navigations.incrementAndGet();
        if (url != null) top.set(url);
    }

    /**
     * A request of the main frame's navigation is redirected to {@code url}: the same navigation, whose top address it
     * is from now on (F6). A null is ignored. Any thread.
     */
    void mainFrameRedirected(String url) {
        if (url != null) top.set(url);
    }

    /** The number of the main frame's navigation requests so far, their redirects not counted (L31). Any thread. */
    long navigation() {
        return navigations.get();
    }

    /**
     * The main frame committed {@code url}, a document of its own or a change within one: the top address from now on,
     * whatever was requested before it (L23). A null is ignored. Any thread.
     */
    void mainFrameCommitted(String url) {
        if (url == null) return;
        committedTop = url;
        top.set(url);
    }

    /**
     * The main frame's request for {@code url} ended without a commit: it failed, or became a download. When it is the
     * top address still, the commit before it is from now on; another address's end, such as that of a request the
     * next one replaced, changes nothing. A null is ignored. Any thread.
     */
    void mainFrameRequestEnded(String url) {
        String current = top.get();
        if (url != null && url.equals(current)) top.compareAndSet(current, committedTop);
    }

    /**
     * The top address: the main frame's latest navigation request, after its redirects, or its latest commit, whichever
     * came last, as Chromium's threads noted them; else {@code committed}, the commit the page last heard of. Any
     * thread.
     */
    String topAddress(String committed) {
        String latest = top.get();
        return latest != null ? latest : committed;
    }

    /** Tells {@code listener} each sign-in, until the page closes; nothing once it has. */
    Subscription onSignIn(Consumer<SignIn> listener) {
        if (closed) return () -> { };
        signIns.add(listener);
        return () -> signIns.remove(listener);
    }

    /**
     * Tells those who asked {@link #onSignIn} of {@code signIn}, or cancels it when none has asked or the page has
     * closed, so that Chromium's request waits on no one. One who throws is logged, with the exception alone, and the
     * sign-in is cancelled, unless answered already.
     */
    void signIn(SignIn signIn) {
        if (closed || signIns.isEmpty()) {
            signIn.cancel();
            return;
        }
        try {
            for (Consumer<SignIn> listener : signIns) listener.accept(signIn);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's sign-in listener failed", failure);
            signIn.cancel();
        }
    }

    /** Tells {@code listener} each final main-frame response, until the page closes; nothing once it has. */
    Subscription onMainFrameResponse(BiConsumer<String, Integer> listener) {
        if (closed) return () -> { };
        responses.add(listener);
        return () -> responses.remove(listener);
    }

    /**
     * Tells those who asked {@link #onMainFrameResponse} of a final main-frame response; nothing once the page has
     * closed.
     */
    void mainFrameResponded(String url, int status) {
        if (closed) return;
        for (BiConsumer<String, Integer> listener : responses) listener.accept(url, status);
    }

    /**
     * The page closed: no handler, no script and no listeners from now on, and each query handed over and not over is
     * cancelled. A sign-in that comes later is cancelled.
     */
    void close() {
        closed = true;
        handler = null;
        script = null;
        signIns.clear();
        responses.clear();
        List<PageQuery> left = List.copyOf(open);
        open.clear();
        for (PageQuery query : left) query.cancelled();
    }
}
