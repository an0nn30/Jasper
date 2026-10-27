package dev.jasper.sdk.ui;

/**
 * Opens the web addresses Jasper opens, once a plugin registers it with {@link Platform#browser}.
 *
 * @since 0.23.0
 */
@FunctionalInterface
public interface WebBrowser {
    /**
     * Opens a link. UI thread; it must not block.
     *
     * @param link the address and who asked for it, where it asks to open, its OSC 8 target and the window it came from
     * @return true when it opened the link; false hands it back to Jasper: the system browser opens an
     *     {@code http} or {@code https} address, the Default editor a {@code file:} one
     */
    boolean open(WebLink link);
}
