package dev.jasper.sdk.ui;

/**
 * Who asked Jasper to open a web address, as {@link WebBrowser#open} hears it.
 *
 * @since 0.23.0
 */
public enum LinkOrigin {
    /**
     * The user asked for it: a link Cmd-clicked (Ctrl-clicked on Linux and Windows) in a terminal pane, a local web file
     * clicked there, which arrives as a {@code file:} link, one of Jasper's own web links such as Settings' Help, or a
     * plugin's {@link Platform#openInBrowser(java.net.URI) Platform.openInBrowser}.
     */
    CLICK,
    /**
     * An address from outside Jasper: the {@code jasper <address>} command, or the system. It reaches the browser
     * without the routing prompt a click can show.
     */
    EXTERNAL
}
