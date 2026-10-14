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
     * Opens a web address in the user's browser. Runs in the background; a failure is posted in the plugin's
     * notification group, never thrown.
     *
     * @param uri an absolute {@code http} or {@code https} address
     * @throws IllegalArgumentException when the address is not an absolute http or https one
     * @since 0.10.2
     */
    void browse(URI uri);

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
     * options file and every {@link #openInEditor} go to {@code editor} first, on the UI thread. One at a time: a bundled
     * plugin's registration replaces a plugin's that is not bundled; otherwise the first stays and any later one, this
     * plugin's own included, throws. When the editor returns false or throws, when no plugin provides one, and when
     * Jasper waited 30 seconds for the UI thread's answer, the desktop opens the file as before. Needs
     * {@code editor.provide}. UI thread only. The registration ends with the plugin.
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
}
