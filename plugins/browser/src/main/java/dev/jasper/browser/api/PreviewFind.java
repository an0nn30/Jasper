package dev.jasper.browser.api;

import java.util.Objects;

/**
 * What {@link MarkdownPreview#markAll} and {@link MarkdownPreview#select} look for in the document's text.
 *
 * @param query the text, or with {@code regex} a JavaScript regular expression; not empty
 * @param matchCase whether letters match only in the same case
 * @param wholeWord whether a match must be a whole word
 * @param regex whether the query is a regular expression
 */
public record PreviewFind(String query, boolean matchCase, boolean wholeWord, boolean regex) {
    /** Rejects a null or empty query. */
    public PreviewFind {
        Objects.requireNonNull(query, "query");
        if (query.isEmpty()) throw new IllegalArgumentException("A preview's find query must not be empty");
    }
}
