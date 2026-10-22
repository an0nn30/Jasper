package dev.jasper.app.plugins;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.LinkOrigin;
import dev.jasper.sdk.ui.WebBrowser;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The web browser a plugin provides ({@code Platform.browser}), which Jasper's web addresses ask before the system
 * browser (browser plugin spec 6). One at a time: a bundled plugin's registration replaces a non-bundled one's; any
 * other later registration, the same plugin's included, throws. The browser runs on the UI thread inside its plugin's
 * containment and must not block, so nothing waits for it.
 */
final class WebBrowsers {
    private static final System.Logger LOG = System.getLogger(WebBrowsers.class.getName());

    /** A registration: the plugin, its browser, and its own handle for an open Jasper window's id. */
    private record Holder(String pluginId, WebBrowser browser, Function<UUID, Optional<WindowOwner>> windows) { }

    private final Containment containment;
    private final Predicate<String> bundled;
    private volatile Holder holder;

    /**
     * @param containment runs the browser inside its plugin's containment
     * @param bundled whether a plugin ships with Jasper, as {@code FileEditors.bundled(String)} answers
     */
    WebBrowsers(Containment containment, Predicate<String> bundled) {
        this.containment = Objects.requireNonNull(containment, "containment");
        this.bundled = Objects.requireNonNull(bundled, "bundled");
    }

    /**
     * Makes {@code pluginId}'s browser the one Jasper opens web addresses with. UI thread.
     *
     * @param windows the registering plugin's own handle for an open Jasper window's id
     * @return removes the browser, unless a bundled plugin's has replaced it since
     * @throws IllegalStateException when a plugin provides the browser already, unless this is a bundled plugin replacing
     *     a non-bundled one's
     */
    dev.jasper.app.lifecycle.Subscription register(String pluginId, WebBrowser browser, Function<UUID, Optional<WindowOwner>> windows) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(browser, "browser");
        Objects.requireNonNull(windows, "windows");
        Holder current = holder;
        if (current != null) {
            if (!bundled.test(pluginId) || bundled.test(current.pluginId()))
                throw new IllegalStateException("The browser is already provided by " + current.pluginId());
            LOG.log(System.Logger.Level.INFO, "The bundled plugin " + pluginId + " provides the browser instead of " + current.pluginId());
        }
        var registered = new Holder(pluginId, browser, windows);
        holder = registered;
        return new dev.jasper.app.lifecycle.Subscription(() -> { if (holder == registered) holder = null; });
    }

    /**
     * Asks the provided browser to open {@code address}. UI thread.
     *
     * @param windowId the Jasper window the address came from; the browser gets its plugin's handle while it is open
     * @return true when it opened the address; false when no plugin provides a browser, or it declined or threw
     */
    boolean open(URI address, LinkOrigin origin, Optional<UUID> windowId) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(windowId, "windowId");
        Holder current = holder;
        if (current == null) return false;
        Optional<WindowOwner> window = windowId.flatMap(current.windows());
        var opened = new AtomicBoolean();
        Throwable failure = containment.attempt(current.pluginId(), "web browser", () -> {
            opened.set(current.browser().open(address, origin, window));
            return null;
        });
        return failure == null && opened.get();
    }
}
