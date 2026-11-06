package dev.jasper.sdk.ui;

/**
 * A content tab that takes inserted text, as a command palette query names it ({@code PaletteQuery.textTarget()}): the
 * window's current item, when it is a content tab whose plugin set an insertion ({@link ContentTab#setTextInsertion})
 * and no terminal pane is the query's origin. The handle is bound to the scope's plugin and to that tab: it does not
 * follow the window to another item. Once the plugin's context is closed, both methods throw
 * {@code IllegalStateException}. Implemented by the application and by the testkit.
 *
 * @since 0.29.0
 */
public interface TextTarget {
    /**
     * Inserts text into the tab: its plugin's insertion receives it, on the UI thread and contained, and puts it where
     * the user is typing, pressing Return afterwards when {@link InsertedText#submit()} says so. Does nothing once the
     * tab has closed or stopped taking text ({@link #isOpen()} is false). Needs {@code terminal.inject}. UI thread
     * only; elsewhere it throws {@code IllegalStateException}.
     *
     * @param text what to insert
     * @throws NullPointerException when {@code text} is null
     * @throws dev.jasper.sdk.MissingCapabilityException without {@code terminal.inject}, checked before the tab's state
     */
    void insert(InsertedText text);

    /**
     * Whether the tab still takes text: it is open and its insertion is set. UI thread only; elsewhere it throws
     * {@code IllegalStateException}.
     *
     * @return whether {@link #insert} reaches the tab
     */
    boolean isOpen();
}
