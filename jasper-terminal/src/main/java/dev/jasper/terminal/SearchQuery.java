package dev.jasper.terminal;

/** Search request; malformed regular expressions are reported by the search operation. */
public record SearchQuery(String text, boolean regex, boolean caseSensitive) {
    public SearchQuery { java.util.Objects.requireNonNull(text, "text"); }
}
