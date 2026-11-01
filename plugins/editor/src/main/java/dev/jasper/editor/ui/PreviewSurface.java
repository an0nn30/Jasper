package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownRenderer;
import dev.jasper.editor.markdown.MarkdownResult;
import dev.jasper.editor.markdown.resources.ResourceSession;
import dev.jasper.sdk.ui.SyntaxPalette;
import java.awt.Font;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;
import org.fife.ui.rtextarea.SearchContext;

/**
 * What a view's Markdown preview draws on: the rendered document, its images, the block tops scroll sync reads, Find,
 * and the commands the view sends it. Each {@link DocumentArea} has one. Everything but {@link #prepare} runs on the UI
 * thread.
 */
sealed interface PreviewSurface extends AutoCloseable permits BasicPreview {
    /**
     * A rendered document, ready to install.
     *
     * @param result what the renderer wrote, in this surface's {@link #dialect()}
     * @param swing Swing's parse of it, for the Basic preview; null for the WEB dialect
     */
    record Prepared(MarkdownResult result, PreviewPane.Prepared swing) {
        public Prepared { Objects.requireNonNull(result); }
    }

    /** @return the markup this surface shows */
    MarkdownRenderer.Dialect dialect();

    /**
     * Renders {@code source} for this surface, on the render worker; it touches no Swing.
     *
     * @param source the Markdown text
     * @param syntax the colours of highlighted code
     * @return the document to {@link #install}
     */
    Prepared prepare(String source, SyntaxPalette syntax);

    /** @return what the area lays out for the preview */
    JComponent component();

    /**
     * Starts showing {@code prepared}. When it shows, {@link #installedResult()} is its result and the installed
     * listeners run; when it cannot, {@link #installFailure()} says why. Either way the area's geometry hook runs.
     */
    void install(Prepared prepared, DocumentArea.PreviewStyle style, Font body, double opacity);

    /** Shows the installed document in a new style, keeping its content, selection and images. */
    void restyle(DocumentArea.PreviewStyle style, Font body, double opacity);

    /** Shows the installed document with the area's new body font. */
    void bodyFont(Font body);

    /** @return whether an install has started and not yet ended */
    boolean installing();

    /** @return why the last install failed, until another starts or {@link #cancelInstall()} runs */
    Optional<RuntimeException> installFailure();

    /** @return the result of the document shown */
    Optional<MarkdownResult> installedResult();

    /** Abandons an install in progress and forgets the last failure; the document shown stays. */
    void cancelInstall();

    /** @param listener runs each time an installed document shows */
    void onInstalled(Runnable listener);

    /** @param listener a listener {@link #onInstalled} added, which runs no more */
    void removeInstalled(Runnable listener);

    /** @param action runs just before a newly installed document replaces the one shown */
    void beforeReplace(Runnable action);

    /** Shows the images, and the reasons for those not shown, that the resource session has for the document. */
    void images(ResourceSession.Update update);

    /**
     * Moves the caret to the anchor {@code name}, a heading or footnote, and scrolls it into sight.
     *
     * @return false, changing nothing, when the installed document has no such anchor
     */
    boolean reveal(String name);

    /**
     * @param blockId a block's id, {@code b<n>}
     * @return the top of the block in the document, in pixels; empty when the document has no such block or no layout
     */
    OptionalDouble blockY(String blockId);

    /** @return the y of the document's end, in pixels; NaN while the document has no layout */
    double endY();

    /** @return where the preview shows its document, for scroll sync */
    ScrollLink.Scroller scroller();

    /**
     * Highlights every match of {@code context} in the visible text, replacing earlier highlights.
     *
     * @return the number of matches; a pattern the surface refuses fails it with a {@code PatternSyntaxException}
     */
    CompletableFuture<Integer> markAll(SearchContext context);

    /**
     * Selects the next match of {@code context} in its direction from the selection, wrapping as it asks.
     *
     * @return whether there was one; a pattern the surface refuses fails it with a {@code PatternSyntaxException}
     */
    CompletableFuture<Boolean> find(SearchContext context);

    /** Removes the highlights {@link #markAll} made. */
    void clearFind();

    /** @return the selected text, or the empty text when nothing is selected */
    CompletableFuture<String> selectedText();

    /** Copies the selection to the clipboard. */
    void copy();

    /** Selects the whole document. */
    void selectAll();

    /** Gives the preview the keyboard focus. */
    void focus();

    /** @return whether the preview has the keyboard focus */
    boolean focused();

    /** @return whether {@link #close()} has run */
    boolean closed();

    /** Ends the surface: it shows nothing more and tells no listener. */
    @Override void close();
}
