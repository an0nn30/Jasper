package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Desktop integration the application already does for its own files and links. Implemented by the
 * application and by the testkit.
 */
public interface Platform {
    /**
     * Opens a file in the user's editor: the editor a plugin provides (see {@link #editor}), then the desktop's edit or
     * open action, then revealing the file. Runs in the background; a failure is posted in the plugin's notification group,
     * never thrown.
     *
     * @param file the file
     */
    void openInEditor(Path file);

    /**
     * Opens a web address in the system browser, never asking the web browser a plugin provides
     * ({@link #openInBrowser} asks that one first, with a {@link WebLink}). Runs in the background; a failure is posted
     * in the plugin's notification group, never thrown.
     *
     * @param uri an absolute {@code http} or {@code https} address
     * @throws IllegalArgumentException when the address is not an absolute http or https one
     * @since 0.10.2
     */
    void browse(URI uri);

    /**
     * Opens a web address the user asked for, such as a link clicked in the plugin's view: the web browser a plugin
     * provides (see {@link #browser}), asked on the UI thread with a {@link WebLink} of origin {@link LinkOrigin#CLICK},
     * placement {@link LinkPlacement#TAB}, no target and the active Jasper window, then the system browser, as
     * {@link #browse} opens it. Returns at once; a failure is posted in the plugin's notification group, never thrown.
     *
     * @param uri an absolute {@code http} or {@code https} address with a host
     * @throws IllegalArgumentException when the address is not an absolute http or https address with a host
     * @since 0.23.0
     */
    void openInBrowser(URI uri);

    /**
     * Puts text on the system clipboard. UI thread only; elsewhere it throws {@code IllegalStateException}. When the
     * clipboard cannot be written, for example while another program holds it, a failure is posted in the plugin's
     * notification group, never thrown.
     *
     * @param text the text, possibly empty
     * @return whether the text is on the clipboard
     * @throws NullPointerException when {@code text} is null
     * @since 0.16.1
     */
    boolean copy(String text);

    /**
     * As {@link #copy}, for text that should not linger, such as a password or a decrypted value. Jasper clears the
     * clipboard 30 seconds later (retrying briefly while another program holds it), and tries once when the plugin
     * stops and once when Jasper quits, in each case only if the clipboard still holds this text. A later copy, by
     * the user or by any plugin, is left alone.
     *
     * @param text the text, possibly empty
     * @return whether the text is on the clipboard
     * @throws NullPointerException when {@code text} is null
     * @since 0.16.1
     */
    boolean copySecret(String text);

    /**
     * The clipboard's text. Needs {@code clipboard.read}. UI thread only; elsewhere it throws
     * {@code IllegalStateException}.
     *
     * @return the text, or empty when the clipboard holds no text or cannot be read
     * @throws dev.jasper.sdk.MissingCapabilityException without {@code clipboard.read}
     * @since 0.16.1
     */
    Optional<String> clipboardText();

    /**
     * Makes this plugin the editor Jasper opens files with: {@code config.toml}, the plugin settings files, the custom VM
     * options file, every {@link #openInEditor} and a Cmd-click (Ctrl-click on Linux and Windows) on a file path in a
     * local terminal pane go to {@code editor} first, on the UI thread; a click brings the path's line and the clicked
     * window. A clicked path comes from program output, and an OSC 8 link's target can differ from the text shown, so
     * an editor edits the file and never runs it. One at a time: a bundled plugin's registration replaces a plugin's
     * that is not bundled; otherwise the first stays and any later one, this plugin's own included, throws. When the
     * editor returns false or throws, when no plugin provides one, and when Jasper waited 30 seconds for the UI
     * thread's answer, the desktop opens the file as before; for a click it edits the file or shows it in the file
     * manager instead, and never opens it. Needs {@code editor.provide}. UI thread only. The registration ends with
     * the plugin.
     *
     * @param editor opens a file
     * @return the registration
     * @throws IllegalStateException when another plugin already provides the editor
     *     ({@code The editor is already provided by <plugin id>}), and off the UI thread
     * @throws dev.jasper.sdk.MissingCapabilityException without {@code editor.provide}
     * @since 0.17.1
     */
    default Subscription editor(FileEditor editor) {
        java.util.Objects.requireNonNull(editor, "editor");
        return () -> { };
    }

    /**
     * Makes this plugin the web browser Jasper opens web addresses with: an {@code http} or {@code https} link
     * Cmd-clicked (Ctrl-clicked on Linux and Windows) in a terminal pane, with that window, its placement and its OSC 8
     * {@code target=} name; every plugin's {@link #openInBrowser} and Jasper's own web links, such as Settings' Help,
     * with the active window; and the addresses given to the {@code jasper} command, with the window of the pane it ran
     * in, go to {@code browser} first, on the UI thread, as a {@link WebLink} that says who asked
     * ({@link LinkOrigin}). A local file the user clicked in a terminal also arrives, as a {@code file:} link.
     * A clicked link comes from program output, and an OSC 8 link's target can differ from the text shown, so a browser
     * should treat the address as untrusted input and show it before acting on it. Other
     * schemes, such as {@code mailto}, and {@link #browse} always go to the system browser. One at a time: a bundled
     * plugin's registration replaces a plugin's that is not bundled; otherwise the first stays and any later one, this
     * plugin's own included, throws. When the browser returns false or throws, and when no plugin provides one, the
     * system browser opens an {@code http} or {@code https} address and the Default editor a {@code file:} one. Needs
     * {@code browser.provide}. UI thread only. The registration ends with the plugin.
     *
     * @param browser opens a web address
     * @return the registration
     * @throws IllegalStateException when another plugin already provides the browser
     *     ({@code The browser is already provided by <plugin id>}), and off the UI thread
     * @throws dev.jasper.sdk.MissingCapabilityException without {@code browser.provide}
     * @since 0.23.0
     */
    default Subscription browser(WebBrowser browser) {
        java.util.Objects.requireNonNull(browser, "browser");
        return () -> { };
    }
}
