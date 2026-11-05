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
import java.util.function.Supplier;
import java.util.regex.PatternSyntaxException;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.text.BadLocationException;
import org.fife.ui.rtextarea.SearchContext;

/**
 * The Basic preview: Swing's view of the SWING dialect ({@link PreviewPane}) in the area's preview scroll pane, which
 * screen readers can read. It lives as long as its area. Its Find answers are ready when asked.
 */
final class BasicPreview implements PreviewSurface {
    private final PreviewPane pane;
    private final JScrollPane scroll;
    private final PreviewSearch search;
    private final ScrollLink.ViewportScroller scroller;

    /**
     * @param pane the Swing document view
     * @param scroll the scroll pane showing {@code pane}'s editor
     */
    BasicPreview(PreviewPane pane, JScrollPane scroll) {
        this.pane = Objects.requireNonNull(pane);
        this.scroll = Objects.requireNonNull(scroll);
        search = new PreviewSearch(pane);
        scroller = new ScrollLink.ViewportScroller(scroll.getViewport());
    }

    @Override public MarkdownRenderer.Dialect dialect() { return MarkdownRenderer.Dialect.SWING; }
    @Override public Prepared prepare(String source, SyntaxPalette syntax) {
        var result = new MarkdownRenderer().render(source, syntax, MarkdownRenderer.Dialect.SWING);
        return new Prepared(result, PreviewPane.prepare(result));
    }
    @Override public JComponent component() { return scroll; }
    @Override public void install(Prepared prepared, DocumentArea.PreviewStyle style, Font body, double opacity) {
        pane.install(prepared.swing(), style.typography(), style.palette(), style.syntax(), body, opacity);
    }
    @Override public void restyle(DocumentArea.PreviewStyle style, Font body, double opacity) {
        pane.restyle(style.typography(), style.palette(), style.syntax(), body, opacity);
    }
    @Override public void bodyFont(Font body) { pane.bodyFont(body); }
    @Override public boolean installing() { return pane.installing(); }
    @Override public Optional<RuntimeException> installFailure() { return pane.installFailure(); }
    @Override public Optional<MarkdownResult> installedResult() { return pane.installedResult(); }
    @Override public void cancelInstall() { pane.cancelInstall(); }
    @Override public void onInstalled(Runnable listener) { pane.onInstalled(listener); }
    @Override public void removeInstalled(Runnable listener) { pane.removeInstalled(listener); }
    @Override public void beforeReplace(Runnable action) { pane.beforeReplace = Objects.requireNonNull(action); }
    @Override public void images(ResourceSession.Update update) { pane.images(update); }

    @Override public boolean reveal(String name) {
        var offset = pane.anchor(name);
        if (offset.isEmpty()) return false;
        var editor = pane.editor();
        editor.setCaretPosition(offset.getAsInt());
        try {
            var bounds = editor.modelToView2D(offset.getAsInt());
            if (bounds != null) editor.scrollRectToVisible(bounds.getBounds());
        } catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
        return true;
    }
    @Override public OptionalDouble blockY(String blockId) {
        var offset = pane.anchor(blockId);
        if (offset.isEmpty()) return OptionalDouble.empty();
        double y = top(offset.getAsInt());
        return Double.isNaN(y) ? OptionalDouble.empty() : OptionalDouble.of(y);
    }
    @Override public double endY() { return top(pane.editor().getDocument().getLength()); }
    /** @return the top of the character at {@code offset}; NaN while the editor has no size to lay it out in */
    private double top(int offset) {
        try {
            var bounds = pane.editor().modelToView2D(offset);
            return bounds == null ? Double.NaN : bounds.getY();
        } catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
    }
    @Override public ScrollLink.Scroller scroller() { return scroller; }

    @Override public CompletableFuture<Integer> markAll(SearchContext context) { return answer(() -> search.markAll(context)); }
    @Override public CompletableFuture<Boolean> find(SearchContext context) { return answer(() -> search.find(context)); }
    @Override public void clearFind() { search.clear(); }
    @Override public CompletableFuture<String> selectedText() {
        return CompletableFuture.completedFuture(Objects.requireNonNullElse(pane.editor().getSelectedText(), ""));
    }
    private static <T> CompletableFuture<T> answer(Supplier<T> search) {
        try { return CompletableFuture.completedFuture(search.get()); }
        catch (PatternSyntaxException refused) { return CompletableFuture.failedFuture(refused); }
    }

    @Override public void copy() { pane.editor().copy(); }
    @Override public void selectAll() { pane.editor().selectAll(); }
    @Override public void focus() { pane.editor().requestFocusInWindow(); }
    @Override public boolean focused() { return pane.editor().isFocusOwner(); }
    @Override public boolean closed() { return pane.closed(); }
    @Override public void close() { pane.close(); }
}
