package dev.jasper.browser.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefCompletionCallback;
import org.cef.handler.CefRequestContextHandler;

/**
 * A request context no Chromium made, for tests: it stands for a native context at {@code nativeRef}, counts its
 * disposals and its connections' closes, keeps the preferences set on it, with the thread each was set on, and refuses
 * them with a reason, or throws, once told to. Nothing native is touched.
 */
public final class RecordingContext extends CefRequestContext {
    private final AtomicInteger disposals = new AtomicInteger(), closes = new AtomicInteger();
    private final Map<String, Object> preferences = new ConcurrentHashMap<>();
    private final Map<String, String> setOn = new ConcurrentHashMap<>();
    private volatile String refusal;
    private volatile RuntimeException thrown;

    /**
     * A context standing for the native one at {@code nativeRef}.
     *
     * @param nativeRef the native handle it carries, as JCEF stores one
     */
    public RecordingContext(long nativeRef) {
        setNativeRef("CefRequestContext", nativeRef);
    }

    /**
     * Has every preference set from now on refused with {@code reason}, as Chromium answers a refused one.
     *
     * @param reason the reason
     * @return this context
     */
    public RecordingContext refusing(String reason) {
        refusal = reason;
        return this;
    }

    /**
     * Has every preference set from now on throw {@code failure}, as a broken context might.
     *
     * @param failure what it throws
     * @return this context
     */
    public RecordingContext throwing(RuntimeException failure) {
        thrown = failure;
        return this;
    }

    /**
     * How often it was disposed.
     *
     * @return the count
     */
    public int disposals() {
        return disposals.get();
    }

    /**
     * How often its connections were closed, each with a callback, which it calls at once.
     *
     * @return the count
     */
    public int connectionsClosed() {
        return closes.get();
    }

    /**
     * The name of the thread {@code name} was last set on.
     *
     * @param name the preference
     * @return the thread's name, or null when it was never set
     */
    public String setOn(String name) {
        return setOn.get(name);
    }

    @Override public void dispose() { disposals.incrementAndGet(); }
    @Override public boolean isGlobal() { return false; }
    @Override public CefRequestContextHandler getHandler() { return null; }
    @Override public boolean hasPreference(String name) { return preferences.containsKey(name); }
    @Override public Object getPreference(String name) { return preferences.get(name); }
    @Override public Map<String, Object> getAllPreferences(boolean defaults) { return Map.copyOf(preferences); }
    @Override public boolean canSetPreference(String name) { return true; }

    @Override public String setPreference(String name, Object value) {
        RuntimeException failure = thrown;
        if (failure != null) throw failure;
        String refused = refusal;
        if (refused != null) return refused;
        preferences.put(name, value);
        setOn.put(name, Thread.currentThread().getName());
        return null;
    }

    @Override public void ClearCertificateExceptions(CefCompletionCallback callback) { callback.onComplete(); }

    @Override public void CloseAllConnections(CefCompletionCallback callback) {
        closes.incrementAndGet();
        callback.onComplete();
    }
}
