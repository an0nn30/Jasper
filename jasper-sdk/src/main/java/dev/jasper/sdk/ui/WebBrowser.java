package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.net.URI;
import java.util.Optional;

/**
 * Opens the web addresses Jasper opens, once a plugin registers it with {@link Platform#browser}.
 *
 * @since 0.23.0
 */
@FunctionalInterface
public interface WebBrowser {
    /**
     * Opens a web address. UI thread; it must not block.
     *
     * @param address an absolute {@code http} or {@code https} address, as the link or the caller gave it
     * @param origin who asked for it: the user, or a program
     * @param window the Jasper window the address came from, as this plugin's handle; empty when it came from elsewhere
     * @return true when it opened the address; false hands it to the system browser
     */
    boolean open(URI address, LinkOrigin origin, Optional<WindowOwner> window);
}
