package dev.jasper.sdk.ui;

import java.util.Objects;

/**
 * Text one plugin inserts into another plugin's content tab through a {@link TextTarget}, such as a snippet a palette
 * scope puts where the user is typing in that tab. The tab's insertion ({@link ContentTab#setTextInsertion}) receives
 * it.
 *
 * @param text   the text, possibly empty
 * @param submit whether Return is pressed after the text, as the palette's Paste and run does
 * @since 0.29.0
 */
public record InsertedText(String text, boolean submit) {
    /** Requires the text. */
    public InsertedText { Objects.requireNonNull(text, "text"); }
}
