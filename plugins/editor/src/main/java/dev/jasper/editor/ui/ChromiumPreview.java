package dev.jasper.editor.ui;

import dev.jasper.browser.api.MarkdownPreview;
import dev.jasper.browser.api.MarkdownPreviews;
import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.api.PreviewFind;
import dev.jasper.browser.api.PreviewTheme;
import dev.jasper.editor.markdown.MarkdownRenderer;
import dev.jasper.editor.markdown.MarkdownResult;
import dev.jasper.editor.markdown.resources.ResourceSession;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.SyntaxPalette;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JComponent;
import org.fife.ui.rtextarea.SearchContext;

/**
 * The Chromium preview: the Browser's {@link MarkdownPreview}, whose page draws the WEB dialect in GitHub's look. An
 * install ends when the page says it shows that document; a document shown for an older install, or for one cancelled,
 * installs nothing. Scroll sync reads the page's layout of the installed document and where the page scrolled; Find asks
 * the page, which answers later, and its regular expressions are JavaScript's. The page is opaque, also in a translucent
 * window. Made by {@link ChromiumPreviews}; with it, the only class of the Editor that names the Browser's API. UI thread.
 */
final class ChromiumPreview implements PreviewSurface {
    private final DocumentArea area;
    private final MarkdownPreview preview;
    /** The view's commands go to the preview when it had the focus last, as they go to Basic's. */
    private final FocusListener focus = new FocusAdapter() {
        @Override public void focusGained(FocusEvent event) { area.focusSource(false); }
    };
    private final List<Runnable> installedListeners = new ArrayList<>();
    private final PageScroller scroller = new PageScroller();
    private Runnable beforeReplace = () -> { }, unavailable = () -> { };
    /** The page's last layout of the installed document, or of the one installed before it; null before the first. */
    private PreviewEvent.Geometry geometry;
    private DocumentArea.PreviewStyle style;
    private PreviewTheme theme;
    /** Revisions: the last given to the page, the install's, the installed document's and the last the page showed; 0 is none. */
    private long revision, installingRevision, installedRevision, shownRevision;
    private MarkdownResult installing, installed;
    private boolean closed;

    /**
     * @param area the view the preview belongs to, whose link callback and geometry hook it runs
     * @param previews the Browser's previews, which make its page
     */
    ChromiumPreview(DocumentArea area, MarkdownPreviews previews) {
        this.area = Objects.requireNonNull(area);
        preview = previews.create(this::event);
        preview.component().addFocusListener(focus);
    }

    /**
     * @param action runs, on a later UI turn and unless the preview has closed by then, when Chromium cannot draw this
     *     preview any more, for the rest of Jasper's run
     */
    void onUnavailable(Runnable action) { unavailable = Objects.requireNonNull(action); }

    private void event(PreviewEvent event) {
        if (closed) return;
        switch (event) {
            case PreviewEvent.Shown shown -> shown(shown.revision());
            case PreviewEvent.LinkActivated link -> activated(link.id());
            // A page whose client already failed says so from inside the add that first shows its canvas: the view
            // falls back to Basic once that layout is done, not in the middle of it.
            case PreviewEvent.Unavailable _ -> EventQueue.invokeLater(() -> { if (!closed) unavailable.run(); });
            case PreviewEvent.Geometry layout -> laidOut(layout);
            case PreviewEvent.Scrolled scrolled -> scroller.scrolled(scrolled.y(), scrolled.user());
        }
    }

    /**
     * The page laid out the installed document: scroll sync hears its new sizes, which are not the user's doing, then
     * the area's geometry hook runs. A layout of another revision is dropped.
     */
    private void laidOut(PreviewEvent.Geometry layout) {
        if (installed == null || layout.revision() != installedRevision) return;
        geometry = layout;
        scroller.tell(false);
        area.previewGeometry();
    }

    /** @return the page's layout of the installed document, or null when it has none yet */
    private PreviewEvent.Geometry installedLayout() {
        return installed != null && geometry != null && geometry.revision() == installedRevision ? geometry : null;
    }

    /** The page shows revision {@code number}: when it is the install waiting, that document is installed. */
    private void shown(long number) {
        shownRevision = number;
        if (installing == null || number != installingRevision) return;
        beforeReplace.run();
        installed = installing;
        installedRevision = number;
        installing = null;
        installingRevision = 0;
        List.copyOf(installedListeners).forEach(Runnable::run);
        area.previewGeometry();
    }

    /** A link the user followed in the page, as a click on one in Basic: only the installed document's, while it shows. */
    private void activated(int id) {
        if (installed == null || shownRevision != installedRevision) return;
        if (installed.links().stream().anyMatch(link -> link.id() == id)) area.linkActivated(id);
    }

    @Override public MarkdownRenderer.Dialect dialect() { return MarkdownRenderer.Dialect.WEB; }
    @Override public Prepared prepare(String source, SyntaxPalette syntax) {
        return new Prepared(new MarkdownRenderer().render(source, syntax, MarkdownRenderer.Dialect.WEB), null);
    }
    @Override public JComponent component() { return preview.component(); }

    /** Sends the theme, when the style or the body font changed, then the document. The page is opaque: no opacity. */
    @Override public void install(Prepared prepared, DocumentArea.PreviewStyle style, Font body, double opacity) {
        Objects.requireNonNull(prepared);
        if (closed) return;
        cancelInstall();
        theme(style, body);
        long next = revision + 1;
        preview.show(next, prepared.result().html());
        revision = next;
        installing = prepared.result();
        installingRevision = next;
    }
    @Override public void restyle(DocumentArea.PreviewStyle style, Font body, double opacity) {
        if (!closed && body != null) theme(style, body);
    }
    @Override public void bodyFont(Font body) { if (!closed && style != null && body != null) theme(style, body); }
    private void theme(DocumentArea.PreviewStyle style, Font body) {
        this.style = Objects.requireNonNull(style);
        var next = new PreviewTheme(style.variant(), style.palette(), style.syntax(), body.getFamily(), style.typography());
        if (next.equals(theme)) return;
        theme = next;
        preview.theme(next);
    }

    @Override public boolean installing() { return installing != null; }
    /** A page that cannot show its document reports {@link PreviewEvent.Unavailable}, and the view draws on Basic. */
    @Override public Optional<RuntimeException> installFailure() { return Optional.empty(); }
    @Override public Optional<MarkdownResult> installedResult() { return Optional.ofNullable(installed); }
    @Override public void cancelInstall() { installing = null; installingRevision = 0; }
    @Override public void onInstalled(Runnable listener) { installedListeners.add(Objects.requireNonNull(listener)); }
    @Override public void removeInstalled(Runnable listener) { installedListeners.remove(listener); }
    @Override public void beforeReplace(Runnable action) { beforeReplace = Objects.requireNonNull(action); }

    @Override public void images(ResourceSession.Update update) {
        if (closed) return;
        if (update.clear()) preview.clearImages();
        update.images().forEach((id, state) -> {
            if (state.image() != null) preview.image(id, state.image());
            else preview.imageReason(id, state.reason());
        });
    }

    @Override public boolean reveal(String name) {
        if (closed || installed == null || !installed.headings().containsKey(name)) return false;
        preview.reveal(name);
        return true;
    }

    /** @return block {@code b<n>}'s top in the page's layout of the installed document */
    @Override public OptionalDouble blockY(String blockId) {
        var layout = installedLayout();
        if (layout == null || !blockId.startsWith("b")) return OptionalDouble.empty();
        try { return layout.top(Integer.parseInt(blockId, 1, blockId.length(), 10)); }
        catch (NumberFormatException notABlock) { return OptionalDouble.empty(); }
    }
    /** @return the installed document's height in the page's layout of it */
    @Override public double endY() {
        var layout = installedLayout();
        return layout == null ? Double.NaN : layout.height();
    }
    @Override public ScrollLink.Scroller scroller() { return scroller; }

    /** Asks the page; its answer comes later, on the UI thread, and a pattern JavaScript refuses fails it. */
    @Override public CompletableFuture<Integer> markAll(SearchContext context) {
        var find = question(context);
        return find == null ? CompletableFuture.completedFuture(0) : preview.markAll(find);
    }
    /** Asks the page to select the next match in the context's direction, wrapping at the document's end. */
    @Override public CompletableFuture<Boolean> find(SearchContext context) {
        var find = question(context);
        return find == null ? CompletableFuture.completedFuture(false) : preview.select(find, context.getSearchForward());
    }
    /** @return what the page looks for, or null for an empty query, which finds nothing */
    private static PreviewFind question(SearchContext context) {
        String query = context.getSearchFor();
        if (query == null || query.isEmpty()) return null;
        return new PreviewFind(query, context.getMatchCase(), context.getWholeWord(), context.isRegularExpression());
    }
    @Override public CompletableFuture<String> selectedText() { return preview.selectedText(); }

    @Override public void clearFind() { preview.clearFind(); }
    @Override public void copy() { preview.copy(); }
    @Override public void selectAll() { preview.selectAll(); }
    @Override public void focus() { preview.focus(); }
    @Override public boolean focused() { return preview.component().isFocusOwner(); }
    @Override public boolean closed() { return closed; }

    /** Closes the page, so the Browser forgets it and its origin. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        installing = installed = null;
        geometry = null;
        installedListeners.clear();
        scroller.listeners.clear();
        preview.component().removeFocusListener(focus);
        preview.close();
    }

    /**
     * Where the page shows its document: where it last said it scrolled to, rounded, and the sizes of its last layout. A
     * move asks the page to scroll, and the position is where it was asked to go until the page says where it went.
     */
    private final class PageScroller implements ScrollLink.Scroller {
        private final List<Consumer<Boolean>> listeners = new ArrayList<>();
        private int y;
        @Override public int y() { return y; }
        @Override public int max() { return geometry == null ? 0 : (int) Math.max(0, Math.round(geometry.height() - geometry.viewport())); }
        @Override public Dimension extent() { return new Dimension(0, geometry == null ? 0 : (int) Math.round(geometry.viewport())); }
        @Override public Dimension size() { return new Dimension(0, geometry == null ? 0 : (int) Math.round(geometry.height())); }
        @Override public void move(int y) {
            if (closed) return;
            this.y = y;
            preview.scrollTo(y);
        }
        @Override public Subscription onMoved(Consumer<Boolean> byUser) {
            Objects.requireNonNull(byUser);
            listeners.add(byUser);
            return () -> listeners.remove(byUser);
        }
        /** The page scrolled to {@code to}: by the user, or as {@link #move} asked. */
        void scrolled(double to, boolean user) {
            y = (int) Math.round(to);
            tell(user);
        }
        void tell(boolean user) { List.copyOf(listeners).forEach(listener -> listener.accept(user)); }
    }
}
