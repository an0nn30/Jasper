package dev.jasper.editor.markdown.resources;

import java.net.URI;
import java.util.Optional;

/** A classified Markdown link or image destination; parsing never resolves a provider or performs I/O. */
public sealed interface Destination {
    /**
     * An absolute HTTP(S) destination.
     * @param uri the original URI
     */
    record Web(URI uri) implements Destination { }
    /**
     * A decoded provider-relative file.
     * @param path relative path
     * @param fragment decoded heading, if supplied
     */
    record Relative(String path, Optional<String> fragment) implements Destination { }
    /**
     * A heading in this document.
     * @param id decoded heading identifier
     */
    record Fragment(String id) implements Destination { }
    /**
     * A destination the preview cannot follow.
     * @param reason a user-facing explanation
     */
    record Unsupported(String reason) implements Destination { }
}
