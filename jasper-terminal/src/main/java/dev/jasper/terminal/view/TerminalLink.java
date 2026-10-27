package dev.jasper.terminal.view;

import java.util.Objects;
import java.util.Optional;

/**
 * A web link that a Cmd-click (Ctrl-click on Linux and Windows) named in terminal output, reported to
 * {@link TerminalView#setLinkOpener}. The view resolves nothing: whether the address opens, and where, is the
 * application's decision. The address and the target are unauthenticated program output: an OSC 8 link's address can
 * differ from the text shown.
 *
 * <p>The gesture follows the modifier keys at the press:
 * <ul>
 *   <li>macOS: Cmd-click is {@link Gesture#OPEN} and Cmd+Shift-click is {@link Gesture#SPLIT}, whether or not a
 *       program asks for mouse reports.</li>
 *   <li>Linux and Windows: Ctrl-click is {@link Gesture#OPEN} and Ctrl+Shift-click is {@link Gesture#SPLIT} while no
 *       program asks for mouse reports. While one does, Shift is what keeps the click local, so Ctrl+Shift-click is
 *       {@link Gesture#OPEN}.</li>
 * </ul>
 *
 * @param url the link as the program wrote it, which may not parse as a URI: an {@code http}, {@code https} or
 *            {@code ftp} URL written in the output, or the address of an OSC 8 link whose scheme is {@code http},
 *            {@code https}, {@code ftp} or {@code mailto}
 * @param gesture how the click asks for the link to open
 * @param target the OSC 8 {@code target=} parameter the program named, 1 to 256 characters with no control
 *               character; empty for a URL written in the output and for an OSC 8 link without one
 */
public record TerminalLink(String url, Gesture gesture, Optional<String> target) {
    /** How a click asks for a link to open. */
    public enum Gesture {
        /** A Cmd-click (Ctrl-click on Linux and Windows): open the link where the application opens links. */
        OPEN,
        /** A Cmd+Shift-click (Ctrl+Shift-click on Linux and Windows): open the link beside the terminal, in a split. */
        SPLIT
    }

    /**
     * Validates the components.
     *
     * @throws NullPointerException when {@code url}, {@code gesture} or {@code target} is null
     * @throws IllegalArgumentException when {@code url} is empty
     */
    public TerminalLink {
        Objects.requireNonNull(url, "url");
        if (url.isEmpty()) throw new IllegalArgumentException("A terminal link needs a url");
        Objects.requireNonNull(gesture, "gesture");
        Objects.requireNonNull(target, "target");
    }
}
