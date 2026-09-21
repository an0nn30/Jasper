package dev.jasper.terminal.search;

/**
 * Immutable search request. Malformed regex is reported by the search operation, not this constructor.
 * @param text nonnull text; empty text clears matches
 * @param regex true to interpret text as a regular expression
 * @param caseSensitive true for case-sensitive matching
 */
public record SearchQuery(String text, boolean regex, boolean caseSensitive) {
    /** Rejects null query text; regex syntax is validated when searching. */
    public SearchQuery { java.util.Objects.requireNonNull(text, "text"); }
}
