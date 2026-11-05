package dev.jasper.editor.ui;

import dev.jasper.browser.api.MarkdownPreview;
import dev.jasper.browser.api.MarkdownPreviews;
import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.api.PreviewTheme;
import dev.jasper.editor.markdown.MarkdownRenderer;
import dev.jasper.editor.markdown.MarkdownResult;
import dev.jasper.editor.markdown.resources.ResourceSession;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.SyntaxPalette;
import java.awt.Dimension;
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
 * installs nothing. The page is opaque, also in a translucent window. Made by {@link ChromiumPreviews}; with it, the only
 * class of the Editor that names the Browser's API. UI thread.
 */
final class ChromiumPreview implements PreviewSurface {
    /** Task 7 replaces it: a page that never scrolls, so scroll sync leaves it alone. */
    private static final ScrollLink.Scroller STILL = new ScrollLink.Scroller() {
        @Override public int y() { return 0; }
        @Override public int max() { return 0; }
        @Override public Dimension extent() { return new Dimension(); }
        @Override public Dimension size() { return new Dimension(); }
        @Override public void move(int y) { }
        @Override public Subscription onMoved(Consumer<Boolean> byUser) { return () -> { }; }
    };

    private final DocumentArea area;
    private final MarkdownPreview preview;
    /** The view's commands go to the preview when it had the focus last, as they go to Basic's. */
    private final FocusListener focus = new FocusAdapter() {
        @Override public void focusGained(FocusEvent event) { area.focusSource(false); }
    };
    private final List<Runnable> installedListeners = new ArrayList<>();
    private Runnable beforeReplace = () -> { }, unavailable = () -> { };
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

    /** @param action runs when Chromium cannot draw this preview any more, for the rest of Jasper's run */
    void onUnavailable(Runnable action) { unavailable = Objects.requireNonNull(action); }

    private void event(PreviewEvent event) {
        if (closed) return;
        switch (event) {
            case PreviewEvent.Shown shown -> shown(shown.revision());
            case PreviewEvent.LinkActivated link -> activated(link.id());
            case PreviewEvent.Unavailable _ -> unavailable.run();
            // Task 7: the page's geometry and scrolling drive scroll sync.
            case PreviewEvent.Geometry _ -> { }
            case PreviewEvent.Scrolled _ -> { }
        }
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

    // Task 7 replaces these stand-ins with the page's geometry, scrolling and Find. The futures are complete: FindBar
    // joins them on the UI thread.
    @Override public OptionalDouble blockY(String blockId) { return OptionalDouble.empty(); }
    @Override public double endY() { return Double.NaN; }
    @Override public ScrollLink.Scroller scroller() { return STILL; }
    @Override public CompletableFuture<Integer> markAll(SearchContext context) { return CompletableFuture.completedFuture(0); }
    @Override public CompletableFuture<Boolean> find(SearchContext context) { return CompletableFuture.completedFuture(false); }
    @Override public CompletableFuture<String> selectedText() { return CompletableFuture.completedFuture(""); }

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
        installedListeners.clear();
        preview.component().removeFocusListener(focus);
        preview.close();
    }
}
