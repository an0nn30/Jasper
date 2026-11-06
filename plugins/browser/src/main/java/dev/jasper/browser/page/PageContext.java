package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.cef.browser.CefRequestContext;

/**
 * The Chromium request context a browsing group's pages are made with: where their cookies, cache and storage live. The
 * persistent one is Chromium's global context, in the plugin's data folder, which 4a's pages are made with. An in-memory
 * one is a /dev/null group's own ({@link Chromium#inMemoryContext}), kept for the group: asked of Chromium once, by the
 * group's first page as it starts, and released once, as its group's store closes, unless Chromium has stopped by then.
 * Each page is handed a context object of its own over the kept one ({@link Chromium#browserContext}), never the kept
 * one, as JCEF disposes a browser's context object as the browser closes (plan 6's V10). Its public methods name no
 * Chromium type, and making one loads no Chromium class: nothing here names one until a page asks for its context. UI
 * thread.
 */
public final class PageContext {
    private static final String CLOSED = "This browsing group's data store has closed";
    private final boolean inMemory;
    // A test's in place of Chromium's own; null for Chromium's. A function naming the context's type, made at once, would
    // load Chromium's classes as an item opens.
    private final Function<Chromium, CompletableFuture<CefRequestContext>> make;
    private final BiConsumer<Chromium, CefRequestContext> release;
    // The context asked of Chromium, and the Chromium asked; null until a page first asks.
    private CompletableFuture<CefRequestContext> made;
    private Chromium from;
    private boolean closed;

    /**
     * A context that {@code make} asks of a Chromium, and {@code release} releases: Chromium's in-memory ones, or a test's.
     *
     * @param inMemory whether it is a context of its own, in memory; the global context otherwise
     * @param make asks Chromium for an in-memory context, once; null for {@link Chromium#inMemoryContext}
     * @param release releases the context made, once; null for {@link Chromium#release}
     */
    PageContext(boolean inMemory, Function<Chromium, CompletableFuture<CefRequestContext>> make,
                BiConsumer<Chromium, CefRequestContext> release) {
        this.inMemory = inMemory;
        this.make = make;
        this.release = release;
    }

    /**
     * Chromium's global context: the persistent group's.
     *
     * @return the context
     */
    public static PageContext persistent() {
        return new PageContext(false, null, null);
    }

    /**
     * A context of its own, in memory: a /dev/null group's, which nothing asks of Chromium until its first page starts.
     *
     * @return the context
     */
    public static PageContext memory() {
        return new PageContext(true, null, null);
    }

    /**
     * Whether it is a context of its own, in memory.
     *
     * @return true for a /dev/null group's
     */
    public boolean inMemory() {
        return inMemory;
    }

    /**
     * The context a page of the group is made with: for the persistent group null at once, Chromium's global context, as
     * 4a passes it; for a /dev/null group a new object of the page's own over the context kept for the group, which
     * {@link Chromium#inMemoryContext} makes the first time only. Ask once for each browser made.
     *
     * @param chromium the plugin's Chromium
     * @return the page's context object; for a /dev/null group failed once it has closed, or as Chromium fails to make it
     */
    CompletableFuture<CefRequestContext> context(Chromium chromium) {
        Objects.requireNonNull(chromium, "chromium");
        if (!inMemory) return CompletableFuture.completedFuture(null);
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException(CLOSED));
        if (made == null) {
            from = chromium;
            made = make == null ? chromium.inMemoryContext() : make.apply(chromium);
        }
        return made.thenApply(chromium::browserContext);
    }

    /**
     * The group's store closes: an in-memory context asked for is released once, as soon as Chromium has made it, unless
     * Chromium has stopped by then, and no page is made with it afterwards. The persistent one, Chromium's global context,
     * outlives its store: nothing happens.
     */
    public void close() {
        if (!inMemory || closed) return;
        closed = true;
        CompletableFuture<CefRequestContext> asked = made;
        Chromium owner = from;
        if (asked == null) return;
        // Made on Chromium's thread: released there, or here when made already.
        asked.thenAccept(context -> released(owner, context));
    }

    /** Releases {@code context}, which {@code owner} made, unless Chromium has stopped since. */
    private void released(Chromium owner, CefRequestContext context) {
        if (context == null || owner.state() == Chromium.State.STOPPED) return;
        if (release == null) owner.release(context);
        else release.accept(owner, context);
    }
}
