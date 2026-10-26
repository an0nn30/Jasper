package dev.jasper.sdk.terminal;

import dev.jasper.sdk.Subscription;

/**
 * A plugin's piece of Terminal tab names, from {@link Terminals#addTabNamePiece}. Closing it removes the piece, and its
 * value in every tab; it closes with its plugin too.
 *
 * @since 0.22.0
 */
public interface TabNamePiece extends Subscription {
    /**
     * The piece's name.
     *
     * @return the name users write between braces
     */
    String name();

    /**
     * Sets this piece's value for a terminal tab. The tab keeps it while it moves to another window or into another
     * tab. Line breaks and other control characters become spaces, and the value is trimmed; a blank value clears it.
     * Ignored for a content tab, and once the tab or this piece has closed. Any thread; Jasper applies it on the UI
     * thread.
     *
     * @param tab a terminal tab
     * @param value what the piece shows for that tab
     */
    void set(TabHandle tab, String value);
}
