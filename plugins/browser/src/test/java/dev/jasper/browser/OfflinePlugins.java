package dev.jasper.browser;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Browser plugin as its tests make it (F9): its filter list's client fails the test if it is asked for, and its
 * address is on a reserved name no host of the web has, so no test downloads a list. Every test that starts the plugin
 * makes it here ({@link OfflinePluginsTest}).
 */
public final class OfflinePlugins {
    /** The filter list's address in the tests. */
    public static final URI LIST = URI.create("https://filters.test/list.json");
    private static final AtomicInteger ASKED = new AtomicInteger();

    private OfflinePlugins() { }

    /**
     * A plugin whose filter list fails the test if it asks for its client.
     *
     * @return the plugin, not started
     */
    public static BrowserPlugin plugin() {
        return new BrowserPlugin(OfflinePlugins::refused, settings -> LIST);
    }

    /**
     * How many times any of these plugins' lists asked for a client in this run: none, unless a test failed.
     *
     * @return the count
     */
    public static int clientsAsked() {
        return ASKED.get();
    }

    private static HttpClient refused() {
        ASKED.incrementAndGet();
        throw new AssertionError("A test must not download a filter list");
    }
}
