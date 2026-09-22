package dev.jasper.snippets.api;

import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Saved commands, published by the Snippets plugin through {@code Services}. Both methods are called
 * on the UI thread; {@code append} answers on the UI thread later.
 */
public interface SnippetService {
    /** The Snippets palette scope, for {@code PaletteStep.Result.reopen}. */
    String SCOPE_ID = "dev.jasper.snippets.scope";

    /**
     * @param name a snippet name, compared without regard to case
     * @return the snippet, if one has that name in the last successful read
     */
    Optional<SnippetView> byName(String name);

    /**
     * Appends a snippet to the file. The completion receives the saved snippet, or a message such as
     * "A snippet named X exists" or a file error, exactly one of the two present.
     *
     * @param name    the name, 1 to 128 printable characters
     * @param command the command text
     * @param done    called on the UI thread
     */
    void append(String name, String command, BiConsumer<Optional<SnippetView>, Optional<String>> done);

    /**
     * The palette row id of a snippet, so a consumer can reopen the scope on it.
     *
     * @param name the snippet name
     * @return the row id the Snippets scope uses for that name
     */
    static String rowId(String name) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        return "snippet." + Integer.toHexString(key.hashCode()) + "." + key.length();
    }
}
