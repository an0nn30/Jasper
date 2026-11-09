package dev.jasper.browser.engine;

import java.awt.EventQueue;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.cef.browser.CefRequestContext;

/**
 * The network preferences Jasper sets on Chromium's request contexts (plan 6's Task 11), and the proxy they carry
 * (ruling V46): the HTTP proxy, as the dictionary preference {@code proxy} Chrome's own proxy settings write, given to
 * Chromium's global context and to each in-memory context kept for a /dev/null group, never to a browser's own object
 * over one. JCEF's {@code setPreference} hands a Java {@link Map} to CEF as a dictionary, its strings as strings
 * ({@code GetCefValueFromJNIObject} in {@code libjcef}), and answers null once the preference is set, or Chromium's
 * reason: {@code "called on invalid thread"} off Chromium's UI thread, among others. What Chromium then reads back is
 * logged at DEBUG (F38). Each refusal of a proxy server is told on the UI thread; one of the system's settings, which every
 * start gives while the proxy is off, is logged. {@link Chromium} decides when the proxy is given.
 */
final class NetworkPreferences {
    /** The preference's name. */
    static final String PROXY = "proxy";
    private static final System.Logger LOG = System.getLogger(NetworkPreferences.class.getName());
    // The proxy as host:port, empty for the system's settings; Chromium's global context, given as its context
    // initializes; and who is told, on the UI thread, Chromium's reason when it refuses the proxy.
    private volatile Optional<String> server = Optional.empty();
    private volatile CefRequestContext global;
    private volatile Consumer<String> told = reason -> { };

    /**
     * Remembers {@code server} as the proxy given from now on. Any thread.
     *
     * @param server the proxy as {@code host:port}, an IPv6 address in brackets; empty for the system's proxy settings
     */
    void remember(Optional<String> server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    /**
     * Has {@code told} hear, on the UI thread, Chromium's reason whenever it refuses a proxy server.
     *
     * @param told told the reason
     */
    void onRefused(Consumer<String> told) {
        this.told = Objects.requireNonNull(told, "told");
    }

    /**
     * Chromium's context has initialized: {@code global}, its global context, is given the proxy from now on.
     * Chromium's thread.
     *
     * @param global the global context
     */
    void globalContext(CefRequestContext global) {
        this.global = global;
    }

    /**
     * Gives the proxy to the global context, once it is known, and to each of {@code kept} still kept, under its record's
     * lock (V12), and closes their connections; the first refusal is told, as {@link #refused} says. Chromium's thread.
     *
     * @param kept the in-memory contexts Chromium keeps, disposed ones among them
     */
    void give(List<KeptContext> kept) {
        Optional<String> on = server;
        Map<String, Object> value = proxy(on);
        CefRequestContext started = global;
        Optional<String> refused = started == null ? Optional.empty() : apply(started, value);
        for (KeptContext each : kept) {
            Optional<String> refusal = each.ifKept(context -> apply(context, value)).flatMap(reason -> reason);
            if (refused.isEmpty()) refused = refusal;
        }
        refused.ifPresent(reason -> refused(on, reason));
    }

    /**
     * {@code made}, an in-memory context just made, given the proxy before anything is made over it. A refusal is told,
     * as {@link #refused} says, and while the proxy is on, the context is disposed and fails (F4), so no page of its group
     * goes direct; while it is off, the context has the system's settings, as asked. Chromium's thread.
     *
     * @param made the context
     * @param dispose disposes a context that is not kept
     * @return the context
     * @throws IllegalStateException while the proxy is on, when the context refuses it
     */
    CefRequestContext given(CefRequestContext made, Consumer<CefRequestContext> dispose) {
        Optional<String> on = server;
        Optional<String> refused = apply(made, proxy(on));
        if (refused.isEmpty()) return made;
        refused(on, refused.get());
        if (on.isEmpty()) return made;
        dispose.accept(made);
        throw new IllegalStateException(Chromium.PROXY_REFUSED + ": " + refused.get());
    }

    /**
     * Tells, on the UI thread, Chromium's reason for refusing the proxy {@code server}; a refusal of the system's
     * settings, while the proxy is off, is logged as a warning instead and told to no one (final review A's item 2). Any
     * thread.
     *
     * @param server the proxy refused, as {@link #remember} takes it; empty for the system's settings
     * @param reason the reason
     */
    void refused(Optional<String> server, String reason) {
        if (server.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "Chromium refused the system's proxy settings: " + reason);
            return;
        }
        Consumer<String> hearing = told;
        EventQueue.invokeLater(() -> hearing.accept(reason));
    }

    /**
     * The proxy preference for {@code server}: a fixed server, which Chromium asks with {@code CONNECT} for https, while
     * the proxy is on, and the system's proxy settings while it is off (the plan's call, as WebKit uses them).
     *
     * @param server the proxy as {@code host:port}, an IPv6 address in brackets; empty while the proxy is off
     * @return {@code {"mode": "fixed_servers", "server": server}}, or {@code {"mode": "system"}}
     */
    static Map<String, Object> proxy(Optional<String> server) {
        return server.<Map<String, Object>>map(fixed -> Map.of("mode", "fixed_servers", "server", fixed))
            .orElse(Map.of("mode", "system"));
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
