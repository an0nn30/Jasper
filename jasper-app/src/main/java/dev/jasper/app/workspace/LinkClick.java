package dev.jasper.app.workspace;

import dev.jasper.terminal.view.TerminalLink;
import java.util.Objects;

/**
 * A link clicked in one of a window's panes, as the window's {@code onOpenLink} hears it (browser plan 3b; browser spec
 * 9): the link with its gesture and OSC 8 target, as the terminal read it, and the shape of the pane it was clicked in.
 * The application decides where it opens: a split click opens beside the pane, to the right when the pane is wide and
 * below otherwise.
 *
 * @param link the link, its gesture and its target
 * @param paneWide whether the clicked pane is wider than it is tall; a square pane is not
 */
public record LinkClick(TerminalLink link, boolean paneWide) {
    /**
     * Validates the link.
     *
     * @throws NullPointerException when {@code link} is null
     */
    public LinkClick {
        Objects.requireNonNull(link, "link");
    }
}
