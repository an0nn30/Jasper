package dev.jasper.browser.page;

import dev.jasper.browser.permissions.Origins;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What a page gives its password manager (plan 8), which its {@link Page} holds and drives, as {@link PageToolHooks}
 * holds the tools' part: the login script each of its main frames runs as an http or https document starts loading,
 * read on Chromium's thread, and the handler its {@link PageQuery}s go to, with the queries handed to it that are not
 * yet over, which the page's close cancels. It reaches no browser. UI thread, but where a method says otherwise.
 */
final class PageLoginHooks {
    /** What a query is told when the page has no password manager to hand it to. */
    static final String NO_MANAGER = "This page has no password manager.";
    // The login script, from the handler's arrival until the page closes; read on Chromium's thread.
    private volatile String script;
    private Consumer<PageQuery> handler;
    private boolean handled, closed;
    // The queries handed to the handler; those over leave as the next comes, and the rest are cancelled at the close.
    private final List<PageQuery> open = new ArrayList<>();

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

    /** The page closed: no handler and no script from now on, and each query handed over and not over is cancelled. */
    void close() {
        closed = true;
        handler = null;
        script = null;
        List<PageQuery> left = List.copyOf(open);
        open.clear();
        for (PageQuery query : left) query.cancelled();
    }
}
