package dev.jasper.sdk.ui;

/**
 * Who asked Jasper to open a web address, as {@link WebBrowser#open} hears it.
 *
 * @since 0.23.0
 */
public enum LinkOrigin {
    /**
     * The user asked for it: a link Cmd-clicked (Ctrl-clicked on Linux and Windows) in a terminal pane, one of Jasper's
     * own web links such as Settings' Help, a plugin's
     * {@link Platform#openInBrowser(java.net.URI) Platform.openInBrowser}, or {@code jasper <address>}.
     */
    CLICK,
    /**
     * A program asked for it, through {@code jasper open-url <address>}, such as the sign-in page a command line tool
     * opens, which usually works only in the system browser.
     */
    PROGRAM
}
