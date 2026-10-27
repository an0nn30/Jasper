package dev.jasper.terminal.internal.text;

import java.util.Objects;
import java.util.Optional;

/**
 * The web link a click names, before the view reports it: an OSC 8 hyperlink's URI and the {@code target} parameter its
 * program named, or a URL written in the text, which has no target.
 *
 * @param uri the link as the program wrote it, which may not parse as a URI
 * @param target the OSC 8 {@code target=} name, 1 to 256 characters with no control character; empty when the link
 *     has none
 */
public record LinkHit(String uri, Optional<String> target) {
    public LinkHit {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(target, "target");
    }
}
