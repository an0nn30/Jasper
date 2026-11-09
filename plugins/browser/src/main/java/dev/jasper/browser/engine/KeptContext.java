package dev.jasper.browser.engine;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.cef.browser.CefRequestContext;

/**
 * The in-memory request context Chromium keeps for a /dev/null group, and the browsers' objects over it (plan 6's
 * V12). A browser's creation, queued for Chromium's thread whenever it is asked for off it, reads its context object's
 * handle only when it runs, and JCEF disposes that object as the browser closes ({@code CefBrowser_N.onBeforeClose}),
 * which always comes after the creation has run. So each object handed out counts against the kept context, and the
 * kept context is disposed, once, when both its group has closed (or Chromium's close has swept it) and every object
 * over it is back: whichever comes last disposes it. Once the sweep has run, CEF may shut down as soon as JCEF's
 * browsers have closed, so from then on only a browser's close disposes it natively; any other last hand-back, and a
 * group's close, let it go without a native call. Nothing is disposed once Chromium has ended. A kept context disposed,
 * or let go, is refused from then on: an object over it would carry handle 0, which JCEF resolves to Chromium's global
 * context. Any thread: Swing's for the group's close, Chromium's for JCEF's disposals.
 */
final class KeptContext {
    private final CefRequestContext context;
    private final Consumer<CefRequestContext> dispose;
    private final BooleanSupplier ended;
    // Guarded by this.
    private int handedOut;
    private boolean released, swept, disposed;

    /**
     * The record of {@code context}, a context kept for a group.
     *
     * @param context the kept context
     * @param dispose disposes it, once
     * @param ended whether Chromium has ended, after which nothing native is touched
     */
    KeptContext(CefRequestContext context, Consumer<CefRequestContext> dispose, BooleanSupplier ended) {
        this.context = Objects.requireNonNull(context, "context");
        this.dispose = Objects.requireNonNull(dispose, "dispose");
        this.ended = Objects.requireNonNull(ended, "ended");
    }

    /** A context Chromium does not keep, a test's stand-in: its objects are counted, and nothing ever disposes it. */
    static KeptContext untracked(CefRequestContext context) {
        return new KeptContext(context, unused -> { }, () -> true);
    }

    /** The kept context. */
    CefRequestContext context() {
        return context;
    }

    /**
     * What {@code work} gives done with the kept context, unless it has been disposed or let go: under this record's lock,
     * so no dispose of it runs meanwhile, as one may on Swing's thread while Chromium's sets the context's preferences.
     *
     * @param work what is done with the kept context; it must return quickly
     * @param <T> what it gives
     * @return what it gave; empty once the kept context has been disposed or let go
     */
    <T> Optional<T> ifKept(Function<CefRequestContext, T> work) {
        synchronized (this) {
            return disposed ? Optional.empty() : Optional.of(work.apply(context));
        }
    }

    /**
     * A new object over the kept context, for one browser, counted until it comes back.
     *
     * @return the object
     * @throws IllegalStateException once the kept context has been disposed or let go
     */
    CefRequestContext handOut() {
        synchronized (this) {
            if (disposed) throw new IllegalStateException("The browsing group's context has been released");
            handedOut++;
        }
        return new BrowserContext(this);
    }

    /**
     * An object over the kept context came back: once for each object.
     *
     * @param byBrowserClose whether JCEF disposed it as its browser closed, rather than Jasper handing back one no
     *     browser was made with
     */
    void handedBack(boolean byBrowserClose) {
        Due due;
        synchronized (this) {
            handedOut--;
            due = due(!swept || byBrowserClose);
        }
        act(due);
    }

    /** The group has closed: the kept context goes once every object over it is back. */
    void release() {
        Due due;
        synchronized (this) {
            released = true;
            due = due(!swept);
        }
        act(due);
    }

    /**
     * Chromium's close sweeps it, while CEF still runs: it goes now when no object over it is out, its group closed or
     * not, and otherwise as the last browser over it closes.
     */
    void sweep() {
        Due due;
        synchronized (this) {
            swept = true;
            due = due(true);
        }
        act(due);
    }

    /** What is due now, which then counts as done. Under this lock. */
    private Due due(boolean nativeAllowed) {
        if (disposed || handedOut > 0 || !(released || swept)) return Due.NOTHING;
        disposed = true;
        return nativeAllowed && !ended.getAsBoolean() ? Due.DISPOSE : Due.LET_GO;
    }

    private void act(Due due) {
        if (due == Due.DISPOSE) dispose.accept(context);
    }

    /** What a change makes of the kept context. */
    private enum Due {
        /** It stays. */
        NOTHING,
        /** It is disposed now. */
        DISPOSE,
        /** It goes without a native call: CEF may be shutting down, or has. */
        LET_GO
    }
}
