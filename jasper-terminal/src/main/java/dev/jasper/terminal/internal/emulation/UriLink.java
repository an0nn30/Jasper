package dev.jasper.terminal.internal.emulation;

import com.jediterm.terminal.model.hyperlinks.LinkInfo;

/**
 * An OSC 8 hyperlink's URI and the {@code target} its program named; JediTerm keeps it in the cell's HyperlinkStyle.
 * The view opens it.
 */
final class UriLink extends LinkInfo {
    private final String uri;
    private final String target;

    /** @param target the OSC 8 {@code target=} name, or null when the link has none */
    UriLink(String uri, String target) {
        super(() -> {
            // Opening is the view's job (it knows the platform); JediTerm never calls this.
        });
        this.uri = uri;
        this.target = target;
    }

    String uri() {
        return uri;
    }

    /** The OSC 8 {@code target=} name, or null when the link has none. */
    String target() {
        return target;
    }
}
