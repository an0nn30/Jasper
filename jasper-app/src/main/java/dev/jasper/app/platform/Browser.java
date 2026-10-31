package dev.jasper.app.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import javax.swing.SwingUtilities;

/**
 * Web addresses: which text from outside Jasper is one, and opening one in the desktop's browser for plugins that ask
 * through the SDK.
 */
public final class Browser {
    private Browser() { }

    /**
     * {@code text} as a web address (browser plan 3): an absolute URI whose scheme is {@code http} or {@code https}, in
     * any case, with an authority, such as {@code https://example.com/a}. Anything else is empty: another scheme, an
     * opaque URI such as {@code https:example.com}, one without an authority such as {@code http:/notes}, a Windows path,
     * and text that does not parse. Any thread.
     *
     * @param text a {@code jasper} argument, a launch argument or an address the system handed Jasper
     * @return the address, or empty
     */
    public static Optional<URI> webAddress(String text) {
        try {
            var address = new URI(text);
            return isWebAddress(address) ? Optional.of(address) : Optional.empty();
        } catch (URISyntaxException notAnAddress) {
            return Optional.empty();
        }
    }

    /**
     * Whether {@code address} is a web address as {@link #webAddress(String)} reads one. Any thread.
     *
     * @param address any URI
     * @return true for an absolute http or https URI, in any case, with an authority
     */
    public static boolean isWebAddress(URI address) {
        String scheme = address.getScheme();
        boolean web = scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        return web && !address.isOpaque() && address.getRawAuthority() != null;
    }

    /**
     * Opens an address in the desktop's browser; blocks while the desktop takes it, so never on the EDT.
     *
     * @param uri the address, already checked by the caller
     * @throws IllegalStateException on the event dispatch thread
     * @throws UnsupportedOperationException when this desktop cannot open a browser
     * @throws UncheckedIOException when the desktop fails to open it
     */
    public static void open(URI uri) {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Opening a browser requires a worker thread");
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            throw new UnsupportedOperationException("This desktop cannot open a browser");
        try { Desktop.getDesktop().browse(uri); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
