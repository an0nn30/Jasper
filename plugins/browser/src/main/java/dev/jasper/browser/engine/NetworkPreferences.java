package dev.jasper.browser.engine;

import java.util.Map;
import java.util.Optional;
import org.cef.browser.CefRequestContext;

/**
 * The network preferences Jasper sets on Chromium's request contexts (plan 6's Task 11): the HTTP proxy, as the
 * dictionary preference {@code proxy} Chrome's own proxy settings write. JCEF's {@code setPreference} hands a Java
 * {@link Map} to CEF as a dictionary, its strings as strings ({@code GetCefValueFromJNIObject} in {@code libjcef}), and
 * answers null once the preference is set, or Chromium's reason: {@code "called on invalid thread"} off Chromium's UI
 * thread, among others. What Chromium then reads back is logged at DEBUG (F38).
 */
final class NetworkPreferences {
    /** The preference's name. */
    static final String PROXY = "proxy";
    private static final System.Logger LOG = System.getLogger(NetworkPreferences.class.getName());

    private NetworkPreferences() { }

    /**
     * The proxy preference for {@code server}: a fixed server, which Chromium asks with {@code CONNECT} for https, while
     * the proxy is on, and the system's proxy settings while it is off (the plan's call, as WebKit uses them).
     *
     * @param server the proxy as {@code host:port}, an IPv6 address in brackets; empty while the proxy is off
     * @return {@code {"mode": "fixed_servers", "server": server}}, or {@code {"mode": "system"}}
     */
    static Map<String, Object> proxy(Optional<String> server) {
        return server.<Map<String, Object>>map(fixed -> Map.of("mode", "fixed_servers", "server", fixed)).orElse(Map.of("mode", "system"));
    }

    /**
     * Sets {@code value} as {@code context}'s proxy, then, once set, closes the context's connections, so nothing goes on
     * the way it went before; their completion callback does nothing, as JCEF holds the callback it is given (F38).
     * Chromium's UI thread, while Chromium runs.
     *
     * @param context a request context Chromium made: its global one, or one kept for a /dev/null group, never a
     *     browser's own object over one
     * @param value the preference, as {@link #proxy} gives it
     * @return empty once the proxy is set and the connections' close asked for; otherwise Chromium's reason, or what a
     *     call threw
     */
    static Optional<String> apply(CefRequestContext context, Map<String, Object> value) {
        try {
            String refused = context.setPreference(PROXY, value);
            if (refused != null) return Optional.of(refused);
            // Read back only when logged; the server it names carries no credentials, as Proxy Server takes none.
            LOG.log(System.Logger.Level.DEBUG, () -> "Chromium's proxy preference reads " + context.getPreference(PROXY));
            context.CloseAllConnections(() -> { });
            return Optional.empty();
        } catch (RuntimeException failure) {
            return Optional.of(failure.getMessage() == null ? failure.toString() : failure.getMessage());
        }
    }
}
