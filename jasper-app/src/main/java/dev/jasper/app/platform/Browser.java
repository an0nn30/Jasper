package dev.jasper.app.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import javax.swing.SwingUtilities;

/** Opens web addresses in the desktop's browser, for plugins that ask through the SDK. */
public final class Browser {
    private Browser() { }

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
