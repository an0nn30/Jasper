package dev.jasper.snippets.api;

import java.util.List;
import java.util.Objects;

/**
 * One saved snippet as other plugins see it.
 *
 * @param name     the display name, unique without regard to case
 * @param command  the command text, possibly with {@code {{placeholders}}}
 * @param keywords extra search words
 */
public record SnippetView(String name, String command, List<String> keywords) {
    /** Copies the keywords and rejects nulls. */
    public SnippetView {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(command, "command");
        keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
    }
}
