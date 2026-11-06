package dev.jasper.browser.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefCompletionCallback;
import org.cef.handler.CefRequestContextHandler;

/**
 * A request context no Chromium made, for tests: it stands for a native context at {@code nativeRef}, counts its
 * disposals and keeps the preferences set on it. Nothing native is touched.
 */
public final class RecordingContext extends CefRequestContext {
    private final AtomicInteger disposals = new AtomicInteger();
    private final Map<String, Object> preferences = new ConcurrentHashMap<>();

    /**
     * A context standing for the native one at {@code nativeRef}.
     *
     * @param nativeRef the native handle it carries, as JCEF stores one
     */
    public RecordingContext(long nativeRef) {
        setNativeRef("CefRequestContext", nativeRef);
    }

    /**
     * How often it was disposed.
     *
     * @return the count
     */
    public int disposals() {
        return disposals.get();
    }

    @Override public void dispose() { disposals.incrementAndGet(); }
    @Override public boolean isGlobal() { return false; }
    @Override public CefRequestContextHandler getHandler() { return null; }
    @Override public boolean hasPreference(String name) { return preferences.containsKey(name); }
    @Override public Object getPreference(String name) { return preferences.get(name); }
    @Override public Map<String, Object> getAllPreferences(boolean defaults) { return Map.copyOf(preferences); }
    @Override public boolean canSetPreference(String name) { return true; }

    @Override public String setPreference(String name, Object value) {
        preferences.put(name, value);
        return null;
    }

    @Override public void ClearCertificateExceptions(CefCompletionCallback callback) { callback.onComplete(); }
    @Override public void CloseAllConnections(CefCompletionCallback callback) { callback.onComplete(); }
}
