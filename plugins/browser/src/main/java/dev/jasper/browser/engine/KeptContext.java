package dev.jasper.browser.engine;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.cef.browser.CefRequestContext;

/**
 * The in-memory request context Chromium keeps for a /dev/null group, and the browsers' objects over it (plan 6's
 * V12). A browser's creation, queued for Chromium's thread whenever it is asked for off it, reads its context object's
 * handle only when it runs, and JCEF disposes that object as the browser closes ({@code CefBrowser_N.onBeforeClose}),
 * which always comes after the creation has run. So each object handed out counts against the kept context, and the
 * kept context is disposed, once, when both its group has closed (or Chromium's close has swept it) and every object
 * over it has been disposed: whichever comes last disposes it. Nothing is disposed once Chromium has ended. Any thread:
 * Swing's for the group's close, Chromium's for JCEF's disposals.
 */
final class KeptContext {
    private final CefRequestContext context;
    private final Consumer<CefRequestContext> dispose;
    private final BooleanSupplier ended;
    private final Runnable forget;
    // Guarded by this.
    private int handedOut;
    private boolean released, swept, disposed;

    /**
     * The record of {@code context}, a context kept for a group.
     *
     * @param context the kept context
     * @param dispose disposes it, once
     * @param ended whether Chromium has ended, after which nothing native is touched
     * @param forget told once it has been disposed, so Chromium forgets it
     */
    KeptContext(CefRequestContext context, Consumer<CefRequestContext> dispose, BooleanSupplier ended, Runnable forget) {
        this.context = Objects.requireNonNull(context, "context");
        this.dispose = Objects.requireNonNull(dispose, "dispose");
        this.ended = Objects.requireNonNull(ended, "ended");
        this.forget = Objects.requireNonNull(forget, "forget");
    }

    /** A context Chromium does not keep, such as a test's: its objects are counted, and nothing ever disposes it. */
    static KeptContext untracked(CefRequestContext context) {
        return new KeptContext(context, unused -> { }, () -> true, () -> { });
    }

    /** The kept context. */
    CefRequestContext context() {
        return context;
    }

    /**
     * A new object over the kept context, for one browser, counted until JCEF disposes it.
     *
     * @return the object
     * @throws IllegalStateException once the kept context has been disposed
     */
    CefRequestContext handOut() {
        synchronized (this) {
            if (disposed) throw new IllegalStateException("The browsing group's context has been released");
            handedOut++;
        }
        return new BrowserContext(this);
    }

    /** An object over the kept context was disposed, as its browser closed or went unused. Once for each object. */
    void handedBack() {
        boolean now;
        synchronized (this) {
            handedOut--;
            now = due();
        }
        if (now) disposeNow();
    }

    /** The group has closed: the kept context goes once no browser's object holds it. */
    void release() {
        boolean now;
        synchronized (this) {
            released = true;
            now = due();
        }
        if (now) disposeNow();
    }

    /** Chromium's close sweeps it: it goes as soon as no browser's object holds it, its group closed or not. */
    void sweep() {
        boolean now;
        synchronized (this) {
            swept = true;
            now = due();
        }
        if (now) disposeNow();
    }

    /** Whether it is to be disposed now, which it then counts as. Under this lock. */
    private boolean due() {
        if (disposed || handedOut > 0 || !(released || swept) || ended.getAsBoolean()) return false;
        disposed = true;
        return true;
    }

    private void disposeNow() {
        try {
            dispose.accept(context);
        } finally {
            forget.run();
        }
    }
}
