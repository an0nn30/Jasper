package dev.jasper.browser.engine;

import java.util.Map;
import java.util.Objects;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefCompletionCallback;
import org.cef.handler.CefRequestContextHandler;

/**
 * One browser's request context object over the in-memory context Chromium keeps for a /dev/null group (plan 6's V10).
 * JCEF disposes a browser's context object as the browser closes ({@code CefBrowser_N.onBeforeClose}), and the
 * module offers no context that shares another's storage, so each browser is handed one of these instead of the kept
 * one: it carries the kept context's native handle, which JCEF's native side reads to make the browser in that
 * context, takes no reference of its own on it, so its dispose does nothing, and does what is asked of it on the kept
 * context. Only {@link Chromium} releases the kept context, once.
 */
final class BrowserContext extends CefRequestContext {
    // JCEF's name for a request context's native handle.
    private static final String NATIVE = "CefRequestContext";
    private final CefRequestContext kept;

    /**
     * A browser's object over {@code kept}.
     *
     * @param kept the context kept for the browser's group
     */
    BrowserContext(CefRequestContext kept) {
        this.kept = Objects.requireNonNull(kept, "kept");
        setNativeRef(NATIVE, kept.getNativeRef(NATIVE));
    }

    /**
     * A new browser's object over {@code kept}, typed as a context: naming this class's own type where Chromium's code
     * expects a context would have the verifier load Chromium's classes as the plugin starts.
     *
     * @param kept the context kept for the browser's group
     * @return the object
     */
    static CefRequestContext over(CefRequestContext kept) {
        return new BrowserContext(kept);
    }

    /** The browser closed: the kept context stays, for the group's other browsers. */
    @Override public void dispose() { }

    @Override public boolean isGlobal() {
        return kept.isGlobal();
    }

    @Override public CefRequestContextHandler getHandler() {
        return kept.getHandler();
    }

    @Override public boolean hasPreference(String name) {
        return kept.hasPreference(name);
    }

    @Override public Object getPreference(String name) {
        return kept.getPreference(name);
    }

    @Override public Map<String, Object> getAllPreferences(boolean includeDefaults) {
        return kept.getAllPreferences(includeDefaults);
    }

    @Override public boolean canSetPreference(String name) {
        return kept.canSetPreference(name);
    }

    @Override public String setPreference(String name, Object value) {
        return kept.setPreference(name, value);
    }

    @Override public void ClearCertificateExceptions(CefCompletionCallback callback) {
        kept.ClearCertificateExceptions(callback);
    }

    @Override public void CloseAllConnections(CefCompletionCallback callback) {
        kept.CloseAllConnections(callback);
    }
}
