package dev.jasper.browser.engine;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefCompletionCallback;
import org.cef.handler.CefRequestContextHandler;

/**
 * One browser's request context object over the in-memory context Chromium keeps for a /dev/null group (plan 6's V10
 * and V12). JCEF disposes a browser's context object as the browser closes ({@code CefBrowser_N.onBeforeClose}), and
 * the module offers no context that shares another's storage, so each browser is handed one of these instead of the
 * kept one. It carries the kept context's native handle, which JCEF's native side reads as the browser's queued
 * creation runs, and takes no reference of its own on it; it does what is asked of it on the kept context. Its dispose,
 * once, takes its handle away and counts it off its {@link KeptContext}, which disposes the kept context only once its
 * group has closed and no object over it is left.
 */
final class BrowserContext extends CefRequestContext {
    // JCEF's name for a request context's native handle.
    private static final String NATIVE = "CefRequestContext";
    private final KeptContext kept;
    private final AtomicBoolean disposed = new AtomicBoolean();

    /**
     * A browser's object over {@code kept}'s context, which {@code kept} has counted.
     *
     * @param kept the context kept for the browser's group
     */
    BrowserContext(KeptContext kept) {
        this.kept = Objects.requireNonNull(kept, "kept");
        setNativeRef(NATIVE, kept.context().getNativeRef(NATIVE));
    }

    /**
     * Another browser's object in the same context as the browser {@code context} was handed, typed as a context so that
     * Chromium's code loads no Chromium class as the plugin starts: a new object over the same kept context, counted
     * the same way, for one of these; {@code context} itself otherwise, such as Chromium's global context.
     *
     * @param context a browser's context object, or null for the global context
     * @return the other browser's object
     */
    static CefRequestContext another(CefRequestContext context) {
        return context instanceof BrowserContext own ? own.kept.handOut() : context;
    }

    /** JCEF is done with the object, as its browser closed: it carries no handle any more, and its kept context hears. */
    @Override public void dispose() {
        if (!disposed.compareAndSet(false, true)) return;
        setNativeRef(NATIVE, 0);
        kept.handedBack();
    }

    @Override public boolean isGlobal() {
        return kept.context().isGlobal();
    }

    @Override public CefRequestContextHandler getHandler() {
        return kept.context().getHandler();
    }

    @Override public boolean hasPreference(String name) {
        return kept.context().hasPreference(name);
    }

    @Override public Object getPreference(String name) {
        return kept.context().getPreference(name);
    }

    @Override public Map<String, Object> getAllPreferences(boolean includeDefaults) {
        return kept.context().getAllPreferences(includeDefaults);
    }

    @Override public boolean canSetPreference(String name) {
        return kept.context().canSetPreference(name);
    }

    @Override public String setPreference(String name, Object value) {
        return kept.context().setPreference(name, value);
    }

    @Override public void ClearCertificateExceptions(CefCompletionCallback callback) {
        kept.context().ClearCertificateExceptions(callback);
    }

    @Override public void CloseAllConnections(CefCompletionCallback callback) {
        kept.context().CloseAllConnections(callback);
    }
}
