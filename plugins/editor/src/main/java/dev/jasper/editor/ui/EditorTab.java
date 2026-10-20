package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.markdown.MarkdownMode;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
import dev.jasper.editor.vim.EditorDefaults;
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimEditor;
import dev.jasper.editor.vim.VimTextArea;
import dev.jasper.editor.vim.ui.VimBar;
import dev.jasper.sdk.ui.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import org.fife.ui.rtextarea.RTextScrollPane;

/** One document's descriptor controller. Only {@link DocumentArea} owns specialized Swing document content. */
public final class EditorTab {
    static final String DOCUMENT = "document", CONTENT = "document.content", STATE = "document.state", BANNER = "document.banner";
    static final String RETRY = "document.retry", DISMISS = "document.dismiss", FIRST = "document.banner.first", SECOND = "document.banner.second";
    static final String FAILED_TITLE = "Could Not Open File";
    private final DocumentSession session;
    private final DocumentArea document = new DocumentArea();
    private final UiView view;
    private final Consumer<EditorTab> close;
    private final Timer edits;
    private final boolean debounce;
    private final DocumentListener editListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { edited(); }
        @Override public void removeUpdate(DocumentEvent event) { edited(); }
        @Override public void changedUpdate(DocumentEvent event) { }
    };
    private boolean replacing, closed, focusPending;
    private Runnable focusArea = () -> area().requestFocusInWindow();
    private VimEditor vim;
    private final VimBar vimBar = new VimBar(this::render, this::reveal);
    private int tabSize = 4;
    private FindBar find;
    private MarkdownController markdown;
    private MarkdownPreferences markdownPreferences;
    private Consumer<Throwable> markdownSaveFailed = failure -> {};
    private MarkdownMode retainedMarkdownMode;
    private boolean wasMarkdown;
    private boolean linkedScrolling = true;
    private Runnable modeChanged = () -> {};
    void modeChanged(Runnable value) { modeChanged = value; }
    private Presentation.Icons icons = Presentation.Icons.NEW_UI;
    void markdownPreferences(MarkdownPreferences value) { markdownPreferences(value, failure -> {}); }
    void markdownPreferences(MarkdownPreferences value, Consumer<Throwable> failed) {
        markdownPreferences = value; markdownSaveFailed = failed; updateMarkdownEligibility(); render();
    }
    boolean markdownEligible() { return SYNTAX_STYLE_MARKDOWN.equals(session.style()); }
    boolean linkedScrolling() { return linkedScrolling; }
    void markdownMode(MarkdownMode mode, boolean explicit) {
        if (closed || !showingText() || !markdownEligible() || markdown == null) return;
        retainedMarkdownMode = mode;
        markdown.mode(mode);
        if (explicit && markdownPreferences != null) markdownPreferences.select(mode).whenComplete((ignored, failure) -> {
            if (failure != null) markdownSaveFailed.accept(failure);
        });
        document.focusSource(mode != MarkdownMode.PREVIEW);
        focusContent(); render(); modeChanged.run();
    }
    /** Reveals the existing source for an explicit command without changing the saved default. */
    public void revealSource() {
        if (markdown != null && markdown.mode() == MarkdownMode.PREVIEW) markdownMode(MarkdownMode.SPLIT, false);
        document.focusSource(true); focusContent();
    }
    private void updateMarkdownEligibility() {
        if (markdown == null) return;
        boolean eligible = markdownEligible();
        if (eligible && (!wasMarkdown || retainedMarkdownMode == null && markdownPreferences != null)) {
            if (retainedMarkdownMode == null && markdownPreferences != null) retainedMarkdownMode = markdownPreferences.current();
            markdown.mode(retainedMarkdownMode == null ? MarkdownMode.SOURCE : retainedMarkdownMode);
        } else if (!eligible && wasMarkdown) { markdown.mode(MarkdownMode.SOURCE); document.focusSource(true); }
        wasMarkdown = eligible;
    }
    javax.swing.text.JTextComponent focusedContent() { return document.focusedContent(); }
    void copy() { focusedContent().copy(); }
    void selectAll() { focusedContent().selectAll(); }
    void paste() { revealSource(); area().paste(); }

    void markdown(MarkdownController value) { markdown = value; updateMarkdownEligibility(); }
    MarkdownController markdown() { return markdown; }
    void previews(java.util.concurrent.Executor worker, java.util.concurrent.Executor ui) {
        new MarkdownController(this, worker, ui, this::render);
    }
    void previews(java.util.concurrent.Executor worker, java.util.concurrent.Executor ui,
                  dev.jasper.editor.markdown.resources.ImageLoader loader) {
        new MarkdownController(this, worker, ui, this::render, loader);
    }
    void active(boolean value) { if (markdown != null) markdown.active(value); }

    /** Creates an owned kit view; closing the hosted view ends document refreshes as well. */
    public EditorTab(UiKit kit, DocumentSession session, Consumer<EditorTab> close, boolean debounce) {
        this.session = session;
        this.close = close;
        this.debounce = debounce;
        edits = new Timer(300, event -> { if (!closed) session.textEdited(); });
        edits.setRepeats(false);
        area().getDocument().addDocumentListener(editListener);
        session.attach(area()::getText, this::replaceText);
        view = kit.create(spec(), this::event);
        view.registerContent(new CustomContent(CONTENT, Nodes.CustomKind.EDITOR, document, this::shutdown));
        session.addListener(this::refresh);
        refresh();
    }

    public UiView view() { return view; }
    public DocumentSession session() { return session; }
    public VimTextArea area() { return document.area; }
    public RTextScrollPane scroll() { return document.scroll; }
    DocumentArea surface() { return document; }

    /** Stops typing notifications, palette subscriptions and Vim on final disposal, never on a move. */
    public void dispose() { shutdown(); view.close(); }
    private void shutdown() {
        if (closed) return;
        closed = true;
        focusPending = false;
        edits.stop();
        if (markdown != null) markdown.close();
        area().getDocument().removeDocumentListener(editListener);
        if (vim != null) { vimBar.detach(); vim.close(); vim = null; }
        document.close();
        find = null;
    }

    void appearance(Appearance appearance) { icons = appearance.presentation().icons(); document.appearance(appearance); render(); }
    void settings(EditorSettings settings) {
        if (closed) return;
        tabSize = settings.tabSize();
        document.settings(settings);
        if (vim != null) vim.editorDefaults(defaults());
    }
    void typography(TerminalTypography value) { if (!closed) document.typography(value); }
    void find(FindBar value) { if (closed) return; find = value; render(); }
    void render() { if (!closed) view.update(spec()); }
    private void reveal(String id) { if (!closed) view.reveal(id); }

    /** Focuses the document immediately or once its pending read succeeds. A failure drops the request. */
    public void focusText() { focusContent(); }
    /** Restores the last valid source/preview content focus after selection, activation or moves. */
    public void focusContent() {
        if (closed) return;
        if (showingText()) { if (document.focusedContent() == area()) focusArea.run(); else document.focusedContent().requestFocusInWindow(); }
        else focusPending = true;
    }
    void focusAction(Runnable value) { focusArea = value; }

    /** Toggles Vim without replacing the text or undo history. The bar is an ordinary kit descriptor subtree. */
    public void vim(Optional<Vim> global) {
        if (closed || global.isPresent() == (vim != null)) return;
        if (global.isPresent()) {
            vim = new VimEditor(global.get(), area(), defaults());
            vim.documentInfo(session::location, session::dirty);
            vimBar.attach(vim);
        } else { vimBar.detach(); vim.close(); vim = null; }
        render();
    }
    public Optional<VimEditor> vimEditor() { return Optional.ofNullable(vim); }

    /** Applies explicit document values; hosted tabs use their appearance subscription and settings instead. */
    public void apply(EditorSettings settings, EditorTheme theme, EditorPalette editor, SyntaxPalette syntax) {
        apply(settings, theme, editor, syntax, 1.0);
    }
    public void apply(EditorSettings settings, EditorTheme theme, EditorPalette editor, SyntaxPalette syntax, double opacity) {
        if (closed) return;
        document.apply(settings, theme, editor, syntax, opacity);
        tabSize = settings.tabSize();
        if (vim != null) vim.editorDefaults(defaults());
    }
    public void bind(List<Command> commands) { document.bind(commands); }
    public void addToPopup(List<Command> commands) { document.addToPopup(commands); }
    boolean showingText() { return session.state() == DocumentSession.State.READY; }
    boolean vimBarVisible() { return vim != null; }

    private void edited() {
        if (closed || replacing) return;
        if (markdown != null) markdown.sourceChanged();
        if (debounce) edits.restart(); else session.textEdited();
    }
    private void replaceText(String text, boolean keepPosition) {
        if (closed) return;
        int line = keepPosition ? area().getCaretLineNumber() : 0;
        replacing = true;
        try { area().setText(text); } finally { replacing = false; }
        area().discardAllEdits();
        try { area().setCaretPosition(area().getLineStartOffset(Math.min(line, area().getLineCount() - 1))); }
        catch (BadLocationException impossible) { area().setCaretPosition(0); }
        if (vim != null) vim.textReplaced();
        if (markdown != null) markdown.sourceReloaded();
    }
    private EditorDefaults defaults() { return new EditorDefaults(tabSize, !session.tabs(), true, session.wrap()); }
    private void refresh() {
        if (closed) return;
        if (session.state() == DocumentSession.State.FAILED) focusPending = false;
        if (!area().getSyntaxEditingStyle().equals(session.style())) area().setSyntaxEditingStyle(session.style());
        area().setLineWrap(session.wrap());
        area().setWrapStyleWord(vim == null);
        area().setTabsEmulated(!session.tabs());
        if (vim != null) vim.editorDefaults(defaults());
        if (markdown != null && wasMarkdown && !markdownEligible()) retainedMarkdownMode = markdown.mode();
        if (markdown != null) markdown.sessionChanged();
        updateMarkdownEligibility();
        render();
        if (showingText() && focusPending) { focusPending = false; focusContent(); }
    }
    private void event(UiEvent event) {
        if (closed) return;
        if (find != null && find.event(event)) return;
        if (!(event instanceof UiEvent.Action action)) return;
        switch (action.id()) {
            case "document.preview.retry" -> { if (markdown != null) markdown.retry(); }
            case "document.preview.source", "markdown.source" -> markdownMode(MarkdownMode.SOURCE, true);
            case "markdown.split" -> markdownMode(MarkdownMode.SPLIT, true);
            case "markdown.preview" -> markdownMode(MarkdownMode.PREVIEW, true);
            case "markdown.images.allow" -> { if (markdown != null) markdown.allowImages(); }
            case "markdown.images.retry" -> { if (markdown != null) markdown.retryImages(); }
            case "markdown.scroll-sync" -> { if (showingText() && markdownEligible()) { linkedScrolling = !linkedScrolling; render(); } }
            case RETRY -> { if (session.state() == DocumentSession.State.FAILED && session.retryable()) session.load(); }
            case DISMISS -> { session.cancelLoad(); close.accept(this); }
            case FIRST -> { if (session.banner() == DocumentSession.Banner.DELETED) close.accept(this); else if (session.banner() == DocumentSession.Banner.CHANGED) session.reloadFromBanner(); }
            case SECOND -> { if (session.banner() == DocumentSession.Banner.DELETED) session.keepDeleted(); else if (session.banner() == DocumentSession.Banner.CHANGED) session.keepMine(); }
            default -> { }
        }
    }
    private ViewSpec spec() {
        boolean ready = showingText(), failed = session.state() == DocumentSession.State.FAILED;
        var children = new ArrayList<Nodes.Node>();
        children.add(MarkdownToolbar.node(markdown == null ? MarkdownMode.SOURCE : markdown.mode(), linkedScrolling, ready, markdownEligible(), icons));
        if (markdown != null) children.add(MarkdownToolbar.resources(markdown.mode(), markdown.needsImageConsent(), markdown.imageFailures()));
        if (markdown != null && markdown.failure().isPresent() && markdown.mode() != dev.jasper.editor.markdown.MarkdownMode.SOURCE) {
            children.add(new Nodes.Stack("document.preview.failure", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
                new Nodes.Text("document.preview.message", ControlState.READY, markdown.failure().orElseThrow(), Nodes.TextRole.BODY),
                button("document.preview.source", "Source", true), button("document.preview.retry", "Retry", true))));
        }
        boolean banner = session.banner() != DocumentSession.Banner.NONE;
        boolean deleted = session.banner() == DocumentSession.Banner.DELETED;
        children.add(new Nodes.Stack(BANNER, visible(banner), Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Text("document.banner.text", ControlState.READY, session.name() + (deleted ? " was deleted." : " changed on disk."), Nodes.TextRole.BODY),
            button(FIRST, deleted ? "Close" : "Reload", true), button(SECOND, deleted ? "Keep" : "Keep Mine", true))));
        children.add(new Nodes.Custom(DOCUMENT, visible(ready), CONTENT, Nodes.CustomKind.EDITOR, "Document"));
        // The canonical full-body empty state: a short title over one sentence, the secondary action, then the one primary.
        children.add(new Nodes.Empty(STATE, visible(!ready), new Nodes.Icon("document.state.icon", ControlState.READY,
            failed ? "error" : "file", Nodes.Hue.NONE, Nodes.IconSize.EMPTY, failed ? "Load failed" : "Loading"),
            failed ? FAILED_TITLE : "Loading " + session.name() + session.host().map(host -> " from " + host).orElse("") + "…", failed ? session.failure() : "",
            List.of(button(DISMISS, failed ? "Close" : "Cancel", true),
                new Nodes.Button(RETRY, visible(failed && session.retryable()), "Retry", "", Nodes.ButtonKind.PRIMARY, RETRY, ""))));
        children.add(vimBar.node(ready));
        if (find != null) children.add(find.node());
        return new ViewSpec(new Nodes.Split("document.tab", ControlState.READY, Nodes.SplitKind.DOCUMENT, children));
    }
    private static ControlState visible(boolean value) { return new ControlState(value, true, false, ""); }
    private static Nodes.Button button(String id, String label, boolean visible) {
        return new Nodes.Button(id, visible(visible), label, "", Nodes.ButtonKind.SECONDARY, id, "");
    }
}
