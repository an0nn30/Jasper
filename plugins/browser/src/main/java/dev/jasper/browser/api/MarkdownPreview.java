package dev.jasper.browser.api;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;

/**
 * One Markdown preview: a canvas on which Chromium draws, in a page of its own, the HTML its caller wrote. Lengths are
 * CSS pixels from the top of the document. UI thread only, and every future it returns completes on the UI thread.
 * After {@link #close()}, every call does nothing, every future that waits is cancelled, as is any future a later call
 * returns, and no event follows.
 */
public interface MarkdownPreview extends AutoCloseable {
    /**
     * The canvas, the same {@code JComponent} each time, for its caller to lay out. Chromium starts the first time it
     * shows.
     *
     * @return the canvas
     */
    JComponent component();

    /**
     * Sets the look. The page takes it before the next document it shows.
     *
     * @param theme the colours and fonts
     */
    void theme(PreviewTheme theme);

    /**
     * Shows a document in place of the one shown, keeping the scroll position. A {@link PreviewEvent.Shown} with its
     * revision follows once it is in the page and laid out, unless a later document replaced it first.
     *
     * @param revision the document's revision, greater than the last one given
     * @param html the document: the Editor's WEB markup, at most 16 MiB in UTF-8
     * @throws IllegalArgumentException for a revision no greater than the last one given, or longer HTML
     */
    void show(long revision, String html);

    /**
     * Shows an image in the document's image element with this id, and in each document after until
     * {@link #clearImages()}.
     *
     * @param id the element's {@code data-image} id
     * @param image the image
     */
    void image(int id, BufferedImage image);

    /**
     * Shows why the document's image with this id is not shown, in its place, with its alternative text.
     *
     * @param id the element's {@code data-image} id
     * @param reason the reason, as the user reads it
     */
    void imageReason(int id, String reason);

    /**
     * Forgets every image and image reason given, for the documents after: the document shown keeps its images until the
     * next replaces it.
     */
    void clearImages();

    /**
     * Scrolls the page. The {@link PreviewEvent.Scrolled} events that follow it are not the user's.
     *
     * @param y the top of the view
     */
    void scrollTo(double y);

    /**
     * Scrolls the document's element with this id to the top and puts the caret at its start, so Tab moves to the next
     * link after it. An id the document lacks does nothing.
     *
     * @param elementId the element's id
     */
    void reveal(String elementId);

    /**
     * Marks every match of {@code find} in the document, up to 10,000. A later {@code markAll} or {@link #select}
     * cancels this one if it still waits.
     *
     * @param find what to find
     * @return the number of matches, on the UI thread; a pattern JavaScript refuses fails it with a
     *     {@link java.util.regex.PatternSyntaxException} naming the pattern
     */
    CompletableFuture<Integer> markAll(PreviewFind find);

    /**
     * Selects the next or previous match of {@code find} from the selection, wrapping at the document's end, and scrolls
     * it into view. A later {@link #markAll} or {@code select} cancels this one if it still waits.
     *
     * @param find what to find
     * @param forward towards the document's end; false for towards its start
     * @return whether a match was selected, on the UI thread; a pattern JavaScript refuses fails it with a
     *     {@link java.util.regex.PatternSyntaxException} naming the pattern
     */
    CompletableFuture<Boolean> select(PreviewFind find, boolean forward);

    /** Removes the marks of {@link #markAll}. */
    void clearFind();

    /**
     * The page's selected text, for Find to start from. A later {@code selectedText} cancels this one if it still waits.
     *
     * @return the text, on the UI thread, when it is one line of at most 1,000 characters; else empty, as when nothing
     *     is selected
     */
    CompletableFuture<String> selectedText();

    /** Copies the page's selection to the clipboard. */
    void copy();

    /** Selects the whole document. */
    void selectAll();

    /** Gives the canvas the keyboard focus. */
    void focus();

    /** Closes the preview and its page; once, however often called. */
    @Override void close();
}
