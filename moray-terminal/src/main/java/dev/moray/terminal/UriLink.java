package dev.moray.terminal;

import com.jediterm.terminal.model.hyperlinks.LinkInfo;

/** The target of an OSC 8 hyperlink; JediTerm keeps it in the cell's HyperlinkStyle. The view opens it. */
final class UriLink extends LinkInfo {
    private final String uri;

    UriLink(String uri) {
        super(() -> {
            // Opening is the view's job (it knows the platform); JediTerm never calls this.
        });
        this.uri = uri;
    }

    String uri() {
        return uri;
    }
}
