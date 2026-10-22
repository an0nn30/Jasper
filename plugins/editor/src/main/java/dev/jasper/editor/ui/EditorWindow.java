package dev.jasper.editor.ui;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.OpenRequest;
import dev.jasper.editor.api.LocationResolver;
import dev.jasper.editor.open.Locations;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.local.LocalWatcher;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.EditorModel;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimHost;
import dev.jasper.editor.vim.Vimrc;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.ContentSpec;
import dev.jasper.sdk.ui.ContentPresentation;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;

/** Document sessions and commands for app-owned tabs, including closing and quitting. UI thread only. */
public final class EditorWindow {
    /** The restorer kind of a document tab, and the key its restore state names the document by (workspace tabs spec 13). */
    public static final String RESTORE_KIND = "document", RESTORE_KEY = "key";
    // Revert to Saved too: the tab strip's menu, its other place, hides with one tab (workspace plan 8b Rulings 20). Split
    // File Right and Down too (pane flexibility spec 6).
    private static final Set<String> POPUP = Set.of("save", "find", "go-to-line", "toggle-comment", "revert", "split-file-right",
        "split-file-down");

    private final EditorEnvironment environment;
    private final dev.jasper.editor.markdown.resources.ImageLoader imageLoader;
    private final boolean ownsImageLoader;
    private final PluginContext context;
    private final Runnable onClosed;
    private final java.util.function.Consumer<EditorDocument> opened;
    private final Predicate<OpenRequest> openers;
    private final Locations locations;
    private final EditorModel model = new EditorModel();
    private final java.util.Map<EditorTab, ContentTab> hosted = new java.util.LinkedHashMap<>();
    /** Every view, the most recently active first (pane flexibility spec 6): a file's first here is the one an open selects. */
    private final List<EditorTab> recent = new ArrayList<>();
    /** Each file's question owner: the tab of its most recently active view, over which its prompts show. */
    private final java.util.Map<DocumentSession, java.util.concurrent.atomic.AtomicReference<WindowOwner>> questionOwners = new java.util.HashMap<>();
    /** What {@link #closed} handed out for a file, completed when its last view closes. */
    private final java.util.Map<DocumentSession, List<CompletableFuture<Void>>> lastViewClosed = new java.util.HashMap<>();
    /** Each view's listener on its file's session, which goes with the view: the session outlives it while others show it. */
    private final java.util.Map<EditorTab, Runnable> sessionListeners = new java.util.HashMap<>();
    private final java.util.Map<EditorTab, Boolean> markdownCommandsReady = new java.util.HashMap<>();
    private EditorTab selectedTab;
    private WindowOwner origin;
    private WindowOwner promptOwner;
    private dev.jasper.sdk.Subscription closeRegistration;
    private boolean disposed;
    private final Function<Question, String> ask;
    private final BiFunction<String, CompletableFuture<Boolean>, Boolean> await;
    private final FindBar findBar;
    private final MarkdownNavigation navigation;
    private final StatusLine status;
    private final List<Command> commands;
    private final LocalWatcher watcher;
    private final Timer ticker;
    private final Timer noticeTimer;
    private final String fileIcon, serverIcon;
    private boolean showingEmpty = true;
    private boolean guarding;
    private final Vim vim;
    /** Each file's Vim buffer number (Vim spec 6.2), kept while it is open, however many views show it. */
    private final java.util.Map<DocumentSession, Integer> numbers = new java.util.HashMap<>();
    private int nextNumber = 1;
    /** The file selected before the current one, another than it: Vim's alternate buffer. */
    private DocumentSession previous;
    /** Untitled tabs {@code :e} opened for files that do not exist yet: their first save creates the file. */
    private final java.util.Map<DocumentSession, Path> newFiles = new java.util.HashMap<>();
    /** Tabs {@code :q!} closes: their changes go without asking. */
    private final Set<EditorTab> discarding = new java.util.HashSet<>();
    /** The sessions whose document key is too long to restore, each warned of once (workspace plan 6b's review). */
    private final Set<DocumentSession> unrestorable = new java.util.HashSet<>();
    /** Vim mode as toggled in this session, until the settings file says the same; null to follow the settings. */
    private Boolean vimToggled;
    /** Whether the vimrc has been read into {@link #vim}: the first time Vim turns on, and again on Reload vimrc. */
    private boolean vimrcRead;

    /**
     * Creates the window, not yet shown, reporting opened documents to nobody.
     *
     * @param environment what the window needs from the plugin
     * @param onClosed runs once the window has closed
     */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed) { this(environment, onClosed, document -> { }); }

    /**
     * Creates the window, not yet shown.
     *
     * @param environment what the window needs from the plugin
     * @param onClosed runs once the window has closed
     * @param opened told a document once its new tab's first read succeeds, and each time its loaded tab is opened again
     */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed, java.util.function.Consumer<EditorDocument> opened) {
        this(environment, onClosed, opened, request -> false);
    }

    /**
     * Creates the window, not yet shown.
     *
     * @param environment what the window needs from the plugin
     * @param onClosed runs once the window has closed
     * @param opened told a document once its new tab's first read succeeds, and each time its loaded tab is opened again
     * @param openers asked before the chooser by Open in Editor…; true when one handled the request
     */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed, java.util.function.Consumer<EditorDocument> opened,
                        Predicate<OpenRequest> openers) {
        this(environment, onClosed, opened, openers, new Locations());
    }

    /**
     * Creates the window, not yet shown.
     *
     * @param environment what the window needs from the plugin
     * @param onClosed runs once the window has closed
     * @param opened told a document once its new tab's first read succeeds, and each time its loaded tab is opened again
     * @param openers asked before the chooser by Open in Editor…; true when one handled the request
     * @param locations the file sources whose files Open in Editor… opens, by source id
     */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed, java.util.function.Consumer<EditorDocument> opened,
                        Predicate<OpenRequest> openers, Locations locations) {
        this(environment, onClosed, opened, openers, locations,
            new dev.jasper.editor.markdown.resources.ImageLoader(new dev.jasper.editor.markdown.resources.ImageBudget(64L * 1024 * 1024)), true);
    }

    /** Uses the plugin-wide image loader across every hosted window and tab. */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed, java.util.function.Consumer<EditorDocument> opened,
                        Predicate<OpenRequest> openers, Locations locations, dev.jasper.editor.markdown.resources.ImageLoader imageLoader) {
        this(environment, onClosed, opened, openers, locations, imageLoader, false);
    }
    private EditorWindow(EditorEnvironment environment, Runnable onClosed, java.util.function.Consumer<EditorDocument> opened,
                         Predicate<OpenRequest> openers, Locations locations, dev.jasper.editor.markdown.resources.ImageLoader imageLoader, boolean ownsImageLoader) {
        this.imageLoader = java.util.Objects.requireNonNull(imageLoader);
        this.ownsImageLoader = ownsImageLoader;
        this.locations = java.util.Objects.requireNonNull(locations);
        this.environment = environment;
        this.context = environment.context();
        this.onClosed = onClosed;
        this.opened = java.util.Objects.requireNonNull(opened);
        this.openers = java.util.Objects.requireNonNull(openers);
        fileIcon = "file";
        serverIcon = "server";
        ask = question -> environment.askers().apply(owner()).apply(question);
        await = (message, saves) -> environment.waits().apply(owner()).apply(message, saves);
        navigation = new MarkdownNavigation(this, context.platform()::browse);
        findBar = new FindBar(() -> selected().flatMap(EditorTab::findTarget), this::refreshFind, id -> selected().ifPresent(tab -> { tab.view().focus(id); tab.view().selectAll(id); }), () -> selected().ifPresent(EditorTab::revealSource));
        status = new StatusLine(this::chooseLanguage, this::toggleWrap, this::toggleVim);
        commands = List.of(
            command("save", "Save", KeyEvent.VK_S, 0, this::save),
            command("save-as", "Save As…", KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK, this::saveAs),
            command("save-all", "Save All", KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK, () -> saveAll()),
            new Command("open", "Open…", Optional.empty(), false, this::openChooser),
            command("new", "New File", KeyEvent.VK_N, 0, this::newUntitled),
            new Command("close-pane", "Close Pane", Optional.empty(), false, this::closeCurrent),
            command("find", "Find…", KeyEvent.VK_F, 0, () -> findBar.open(false)),
            command("replace", "Replace…", KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK, () -> { selected().ifPresent(EditorTab::revealSource); findBar.open(true); }),
            new Command("find-next", "Find Next", Optional.of(KeyNames.findAgain(false)), true, findBar::next),
            new Command("find-previous", "Find Previous", Optional.of(KeyNames.findAgain(true)), true, findBar::previous),
            command("go-to-line", "Go to Line…", KeyEvent.VK_L, 0, this::goToLine),
            command("toggle-comment", "Toggle Comment", KeyEvent.VK_SLASH, 0, this::toggleComment),
            new Command("markdown.source", "Markdown: Source", Optional.empty(), false, () -> markdownMode(dev.jasper.editor.markdown.MarkdownMode.SOURCE)),
            new Command("markdown.split", "Markdown: Source and Preview", Optional.empty(), false, () -> markdownMode(dev.jasper.editor.markdown.MarkdownMode.SPLIT)),
            new Command("markdown.preview", "Markdown: Preview", Optional.empty(), false, () -> markdownMode(dev.jasper.editor.markdown.MarkdownMode.PREVIEW)),
            new Command("revert", "Revert to Saved", Optional.empty(), false, this::revert),
            new Command("split-file-right", "Split File Right", Optional.empty(), false, () -> splitFile(dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT)),
            new Command("split-file-down", "Split File Down", Optional.empty(), false, () -> splitFile(dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN)),
            new Command("language", "Language…", Optional.empty(), false, this::chooseLanguage),
            new Command("wrap", "Toggle Soft Wrap", Optional.empty(), false, this::toggleWrap),
            new Command("toggle-vim", "Toggle Vim Mode", Optional.empty(), false, this::toggleVim),
            new Command("reload-vimrc", "Reload vimrc", Optional.empty(), false, this::reloadVimrc));
        vim = new Vim(new VimHost(SystemClipboard::read, SystemClipboard::write, documents(), actions()));
        watcher = new LocalWatcher(context.background(), environment.ui(), model::sessions);
        ticker = new Timer(2000, event -> watcher.tick());
        noticeTimer = new Timer(3000, event -> selected().ifPresent(tab -> {tab.navigationNotice("");tab.session().clearNotice();}));
        noticeTimer.setRepeats(false);
        closeRegistration = context.windows().onTabsClosing(this::tabsClosing);
        if (environment.timers()) ticker.start();
    }

    /**
     * The closing guard Jasper asks before content tabs close: a close the user asked for ({@code batch} false), of one
     * content tab or, by Close Tab, of every pane of a tab at once (the app's {@code TabRegistry.requestClose} of a
     * list), or a window's, a quit's or a restart's batch. One content tab's own close asks about its file; several ask
     * once about each file every view of which is among them. A view of a file another view still shows closes without
     * a question (pane flexibility spec 6).
     *
     * @param handles the closing tabs, the Editor's among them
     * @param batch false for a close the user asked for, of one content tab or of a tab's panes; true for a window, a
     *     quit or a restart
     * @return whether they may close
     */
    CompletableFuture<Boolean> tabsClosing(List<ContentTab> handles, boolean batch) {
        var closing = hosted.entrySet().stream().filter(e -> handles.contains(e.getValue())).map(java.util.Map.Entry::getKey).toList();
        if (closing.isEmpty()) return CompletableFuture.completedFuture(true);
        if (!batch && closing.size() == 1) return approveTab(closing.getFirst());
        promptOwner = handles.getFirst();
        try { return CompletableFuture.completedFuture(mayClose(closing)); }
        finally { promptOwner = null; }
    }

    /**
     * Opens a document Jasper restores with its windows (workspace tabs spec 13): its tab is placed where it was saved, not
     * selected. A document on another host waits until its tab is first active, or Load is pressed, and then reads without
     * asking anything ({@code EditorDocument.readQuietly}); Retry then reads it as usual, which may sign in. Recent Files
     * does not record it: coming back is not an open (workspace plan 6b's review). A document already open comes back as
     * another view of it (pane flexibility spec 6), which reads nothing: a file's views share one read, which waits for
     * the first of them to be active when the file is on another host.
     *
     * @param document the saved document
     * @param owner the window being restored
     */
    public void openRestored(EditorDocument document, WindowOwner owner) {
        Optional<DocumentSession> open = model.find(document.key());
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        DocumentSession created = open.isPresent() ? null : new DocumentSession(document, sessionEnvironment(ref));
        // The tab opens in the window being restored, which the user did not choose: the Editor's prompts and its own
        // open command keep the window they used before (workspace plan 6b's final review, M4).
        WindowOwner chosen = origin;
        origin = owner;
        try {
            if (created == null) addView(open.get(), dev.jasper.sdk.ui.TabPlacement.NEW_TAB, views(open.get()).getFirst());
            else add(created, ref);
        }
        finally { origin = chosen; }
        if (created == null) return;
        if (document.host().isPresent()) created.defer(); else created.load();
    }

    /** @param document opened in a new tab, or its open tab selected, of several the one focused last; the window comes to the front */
    public void open(EditorDocument document) { open(document, null); }

    /**
     * Opens a file in the window the request came from; null chooses the main window. A file already open has its view
     * selected, of several the one focused last (pane flexibility spec 6).
     */
    public void open(EditorDocument document, WindowOwner owner) {
        origin = owner;
        Optional<DocumentSession> existing = model.find(document.key());
        if (existing.isPresent()) {
            selectOpen(existing.get());
            return;
        }
        openSession(document, dev.jasper.sdk.ui.TabPlacement.NEW_TAB);
    }

    /**
     * Open File Right and Open File Down, and the File menu's Open in Editor Pane… (pane flexibility spec 3; workspace plan
     * 9a Rulings 2): the Editor's chooser for one file, local or in a file source, starting in the folder of the Editor
     * file active in {@code owner}'s window, else where the host locates its focused pane; then
     * {@link #openDocumentBeside} with the file chosen. Cancelling the chooser opens nothing.
     *
     * @param owner the window the command ran in
     * @param placement {@code SPLIT_RIGHT} or {@code SPLIT_DOWN}
     * @throws IllegalArgumentException for another placement, before the chooser shows
     */
    public void openBeside(WindowOwner owner, dev.jasper.sdk.ui.TabPlacement placement) {
        requireBeside(placement);
        java.util.UUID shown = owner instanceof WindowHandle window ? window.id() : null;
        Optional<FileLocation> near = hosted.entrySet().stream()
            .filter(entry -> entry.getValue().isActive() && entry.getValue().window().id().equals(shown))
            .findFirst().flatMap(entry -> folderOf(entry.getKey().session()));
        var choice = new FileChoice(owner, "Open in Editor Pane", FileChoice.Mode.OPEN, false, near, Optional.empty(), locations.sources());
        context.windows().choose(choice).stream().findFirst()
            .ifPresent(chosen -> withDocument(chosen, owner, document -> openDocumentBeside(document, owner, placement)));
    }

    /**
     * Opens {@code document} in a new pane at {@code placement} beside the focused pane of {@code owner}'s window, which
     * the new pane then has, as Split Right with Editor opens an untitled one (pane flexibility spec 3). A document that is
     * open already shows there in another view of it (pane flexibility spec 6), reported again as {@link #open} reports it.
     *
     * @param document the document
     * @param owner the window
     * @param placement {@code SPLIT_RIGHT} or {@code SPLIT_DOWN}
     * @throws IllegalArgumentException for another placement
     */
    public void openDocumentBeside(EditorDocument document, WindowOwner owner, dev.jasper.sdk.ui.TabPlacement placement) {
        requireBeside(placement);
        origin = owner;
        Optional<DocumentSession> existing = model.find(document.key());
        if (existing.isPresent()) {
            // Pane flexibility spec 6: another view of the file, beside the focused pane.
            openView(existing.get(), owner, placement);
            return;
        }
        openSession(document, placement);
    }

    /** A file opens in a pane to the right or below: {@code placement} is {@code SPLIT_RIGHT} or {@code SPLIT_DOWN}. */
    private static void requireBeside(dev.jasper.sdk.ui.TabPlacement placement) {
        if (placement != dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT && placement != dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN)
            throw new IllegalArgumentException("A file opens in a pane to the right or below, not " + placement);
    }

    /**
     * Shows {@code session}, a document that is open already, as Open in Editor… does: its pane is selected, and a document
     * that has loaded is reported opened again, so Recent Files records it.
     */
    private void selectOpen(DocumentSession session) {
        if (session.state() == DocumentSession.State.READY) session.document().ifPresent(opened);
        select(session);
    }

    /** A new session of {@code document}, its pane where {@code placement} puts it, reported once its first read succeeds, loaded and selected. */
    private void openSession(EditorDocument document, dev.jasper.sdk.ui.TabPlacement placement) {
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        var created = new DocumentSession(document, sessionEnvironment(ref));
        add(created, ref, placement); reportWhenLoaded(created); created.load();
        select(created);
    }

    EditorTab openLinked(EditorTab originTab, EditorDocument document) {
        open(document, hosted.get(originTab));
        return views(model.find(document.key()).orElseThrow()).getFirst();
    }

    /** Reports a new tab's document the first time it becomes ready: a failed read reports nothing, a successful retry does. */
    private void reportWhenLoaded(DocumentSession session) {
        var reported = new AtomicBoolean();
        session.addListener(() -> {
            if (session.state() == DocumentSession.State.READY && reported.compareAndSet(false, true)) session.document().ifPresent(opened);
        });
    }
    private SessionEnvironment sessionEnvironment(java.util.concurrent.atomic.AtomicReference<WindowOwner> owner) {
        return new SessionEnvironment(context.background(), environment.ui(),
            question -> owner.get() instanceof ContentTab tab && tab.isClosed() ? question.dismissed() : environment.askers().apply(owner.get()).apply(question), () -> environment.settings().get().maxFileBytes());
    }
    private WindowOwner owner() { return promptOwner != null ? promptOwner : selectedTab == null ? origin : hosted.get(selectedTab); }
    private WindowOwner owner(DocumentSession session) { return views(session).stream().findFirst().map(hosted::get).map(WindowOwner.class::cast).orElseGet(this::owner); }
    /**
     * The owner another plugin's code is handed: the window, never the Editor's tab, whose handle carries the Editor's
     * authority over the tab, its {@code terminal.open} for Terminal Right and Down included (workspace plan 8b's final
     * review). Jasper's own picker and prompts keep the tab.
     */
    private static WindowOwner forOthers(WindowOwner owner) { return owner instanceof ContentTab tab ? tab.window() : owner; }
    public void openLocal(Path file) { openLocal(file, null); }
    public void openLocal(Path file, WindowOwner owner) { open(new LocalDocument(file, context.background()), owner); }

    /**
     * Opens a local file for {@code jasper editor}, or selects the tab already showing it, and puts the caret on
     * {@code line} and {@code column} (1-based; 0 leaves it where loading put it) once the text is there.
     *
     * @return completes with the tab's session once it is ready or has failed
     */
    public CompletableFuture<DocumentSession> openAt(Path file, int line, int column, WindowOwner owner) {
        open(new LocalDocument(file, context.background()), owner);
        DocumentSession session = model.find(LocalDocument.key(file)).orElseThrow();
        var settled = new CompletableFuture<DocumentSession>();
        // The session replaces the text before it reports READY, so the caret set here is not reset by the load.
        Runnable check = () -> {
            if (settled.isDone()) return;
            if (session.state() == DocumentSession.State.READY) {
                moveCaret(session, line, column);
                settled.complete(session);
            } else if (session.state() == DocumentSession.State.FAILED) {
                settled.complete(session);
            }
        };
        check.run();
        if (!settled.isDone()) {
            session.addListener(check);
            // Closing the tab cancels the read, which leaves the session loading for good: its closing settles the call too.
            closed(session).thenRun(() -> settled.complete(session));
        }
        return settled;
    }

    /**
     * A tab for {@code file}, which does not exist yet: its first save creates it, as Vim's {@code :e newfile} does.
     * Selects the tab already open for that path.
     */
    public DocumentSession openNew(Path file, WindowOwner owner) {
        Path target = file.toAbsolutePath().normalize();
        Optional<DocumentSession> open = model.find(LocalDocument.key(target));
        if (open.isEmpty()) {
            open = newFiles.entrySet().stream().filter(entry -> entry.getValue().equals(target))
                .map(Map.Entry::getKey).findFirst();
        }
        if (open.isPresent()) {
            select(open.get());
            return open.get();
        }
        return newFile(target, owner);
    }

    /**
     * Opens a local file for the default editor ({@code FileEditor.edit}): an existing file with the caret on
     * {@code line}, a missing one whose folder exists as a tab its first save creates. The tab opens in {@code window}
     * when that is a Jasper window, else in the main window.
     *
     * @return false for a folder or a file whose folder does not exist, which the desktop then opens or reports
     */
    public boolean edit(Path file, java.util.OptionalInt line, Optional<WindowOwner> window) {
        Path target = file.toAbsolutePath().normalize();
        WindowOwner owner = window.filter(WindowHandle.class::isInstance).orElse(null);
        if (java.nio.file.Files.isRegularFile(target)) {
            openAt(target, line.orElse(0), 0, owner);
            return true;
        }
        Path folder = target.getParent();
        if (java.nio.file.Files.exists(target) || folder == null || !java.nio.file.Files.isDirectory(folder)) return false;
        openNew(target, owner);
        return true;
    }

    /**
     * Completes once the session's last view has closed: when the user closes it, its window closes or the Editor stops
     * (pane flexibility spec 6).
     */
    public CompletableFuture<Void> closed(DocumentSession session) {
        var done = new CompletableFuture<Void>();
        if (views(session).isEmpty()) done.complete(null);
        else lastViewClosed.computeIfAbsent(session, waiting -> new ArrayList<>()).add(done);
        return done;
    }

    private void moveCaret(DocumentSession session, int line, int column) {
        if (line <= 0) return;
        views(session).stream().findFirst().ifPresent(tab -> {
            var area = tab.area();
            int index = Math.min(line, area.getLineCount()) - 1;
            try {
                int start = area.getLineStartOffset(index);
                // A line's end offset counts its newline (the last line's, the document's implicit one).
                int length = Math.max(0, area.getLineEndOffset(index) - start - 1);
                int offset = start + Math.min(Math.max(column, 1) - 1, length);
                area.setCaretPosition(Math.min(offset, area.getDocument().getLength()));
            } catch (BadLocationException impossible) {
                // The line came from getLineCount, so it exists.
            }
        });
    }

    public void openLocalFiles(List<Path> files) { openLocalFiles(files, owner()); }
    public void openLocalFiles(List<Path> files, WindowOwner owner) { for (Path file : files) openLocal(file, owner); }
    public void newUntitled() { newUntitled(owner()); }
    public void newUntitled(WindowOwner owner) { newUntitled(owner, dev.jasper.sdk.ui.TabPlacement.NEW_TAB); }
    /** A new untitled document whose tab opens where {@code placement} puts it: New Editor Window's is a new child window. */
    public void newUntitled(WindowOwner owner, dev.jasper.sdk.ui.TabPlacement placement) {
        origin = owner;
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        var session = DocumentSession.untitled(model.nextUntitledName(), sessionEnvironment(ref));
        add(session, ref, placement); select(session);
    }
    public void show() { if (selectedTab == null) newUntitled(); else hosted.get(selectedTab).select(); }
    public void dispose() {
        if (disposed) return; disposed = true;
        for (var handle : List.copyOf(hosted.values())) handle.close();
        closeRegistration.close(); shutDown();
    }

    /** Settings or the theme changed: applies them to every tab. */
    public void apply() {
        if (disposed) return;
        EditorSettings settings = environment.settings().get();
        if (vimToggled != null && vimToggled == settings.vim()) vimToggled = null;
        for (EditorTab tab : editorTabs()) {
            tab.settings(settings);
            tab.typography(environment.typography());
            tab.vim(vimOn() ? Optional.of(vim) : Optional.empty());
        }
        readVimrcOnce();
        refreshStatus();
    }

    /** Reapplies effective typography to every tab after saved terminal typography changes. */
    public void typography() {
        if (disposed) return;
        var font = environment.typography();
        for (EditorTab tab : editorTabs()) tab.typography(font);
    }

    /** @return whether Vim mode is on: as toggled here, or as the settings say */
    public boolean vimOn() { return vimToggled != null ? vimToggled : environment.settings().get().vim(); }

    /**
     * Toggle Vim Mode and the Vim status item: switches every tab at once and saves {@code vim} through the declared
     * setting so the choice lasts (Vim spec 1.2). A failed save keeps the choice for this session and says so.
     */
    public void toggleVim() {
        boolean on = !vimOn();
        vimToggled = on;
        for (EditorTab tab : editorTabs()) tab.vim(on ? Optional.of(vim) : Optional.empty());
        readVimrcOnce();
        refreshStatus();
        CompletableFuture<Void> saved;
        try { saved = environment.saveVim().apply(on); }
        catch (RuntimeException failed) { saved = CompletableFuture.failedFuture(failed); }
        saved.whenComplete((done, failed) -> {
            if (failed == null) return;
            Throwable cause = failed instanceof java.util.concurrent.CompletionException && failed.getCause() != null ? failed.getCause() : failed;
            String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            environment.ui().execute(() -> ask.apply(new Question("Vim mode", "Vim mode is " + (on ? "on" : "off")
                + " until the Editor closes: the setting could not be saved (" + reason + ").", List.of("OK"))));
        });
    }

    /**
     * Reload vimrc: mappings, options, {@code sethandler} entries and variables go back to their defaults, then the
     * vimrc is read again, now if a tab has Vim on and otherwise when Vim next turns on; registers and marks stay
     * (Vim spec 7.1).
     */
    public void reloadVimrc() {
        vim.reset();
        vimrcRead = false;
        readVimrcOnce();
    }

    /**
     * Reload vimrc as an action run in {@code window} does it, from the palette or a key: as {@link #reloadVimrc()}, its
     * lines running in the file {@link #revert(WindowHandle)} would revert; nothing while the window shows no file.
     *
     * @param window the window the action ran in
     */
    public void reloadVimrc(WindowHandle window) { fileIn(window).ifPresent(tab -> { activate(tab); reloadVimrc(); }); }

    /** @return the vimrc: {@code vimrc} in the Editor's data folder, {@code <home>/plugins/dev.jasper.editor/data} */
    public Path vimrcFile() { return context.dataDirectory().resolve("vimrc"); }

    /**
     * Reads the vimrc the first time a tab has Vim on (Vim spec 1.2), running its lines in that tab (the selected one
     * if it can). Lines it skipped and a vimrc it cannot read are reported as a notice; a missing vimrc is not.
     */
    private void readVimrcOnce() {
        if (vimrcRead) return;
        Optional<dev.jasper.editor.vim.VimEditor> editor = selected().flatMap(EditorTab::vimEditor)
            .or(() -> editorTabs().stream().flatMap(tab -> tab.vimEditor().stream()).findFirst());
        if (editor.isEmpty()) return;
        vimrcRead = true;
        Path file;
        try { file = vimrcFile(); }
        catch (RuntimeException unavailable) {
            context.notifications().group().error("Could not read the vimrc",
                "The Editor's data folder is not available; Vim runs with its defaults.", unavailable);
            return;
        }
        Vimrc.Result read = Vimrc.read(editor.get(), file);
        if (read.error() != null)
            context.notifications().group().warning("Could not read the vimrc", file + ": " + read.error() + ". Vim runs with its defaults.");
        else if (read.skipped() > 0)
            context.notifications().group().warning("Some vimrc lines were skipped",
                read.skipped() + (read.skipped() == 1 ? " line was" : " lines were") + " skipped; see :messages.");
    }

    /** The Editor's commands for Vim's {@code <Action>(id)}, {@code :action} and {@code :actionlist}, run for the tab Vim is in. */
    private VimHost.Actions actions() {
        return new VimHost.Actions(
            () -> commands.stream().map(command -> new VimHost.Action(command.id(), command.title())).toList(),
            (editor, id) -> {
                Optional<Command> command = commands.stream().filter(c -> c.id().equals(id)).findFirst();
                if (command.isEmpty()) return false;
                tabOf(editor).ifPresent(this::activate);
                command.get().run().run();
                return true;
            });
    }

    /** Checks open local files for outside changes; the window's timer calls this every 2 s. */
    public void tick() { watcher.tick(); }

    public void save() { selected().ifPresent(tab -> saveSession(tab.session())); }

    public void saveAs() { selected().ifPresent(tab -> saveAsSession(tab.session())); }

    /** @return completes with true when every tab with unsaved changes was saved */
    public CompletableFuture<Boolean> saveAll() {
        return saveSessions(model.sessions().stream().filter(DocumentSession::dirty).toList(), new AtomicBoolean());
    }

    /** The Editor's own "open" command (Vim's {@code :action open}): Open in Editor… for the window the Editor was last used from. */
    public void openChooser() { openFrom(lastWindow(), Optional.empty()); }

    /**
     * Open in Editor…: the openers first, then Jasper's chooser, which offers local places and the file sources that
     * have a resolver, starting in the active document's folder or, from a terminal pane, where that pane is.
     *
     * @param window the workspace window it was invoked in; null skips the openers
     * @param pane the focused terminal pane
     */
    public void openFrom(WindowHandle window, Optional<PaneHandle> pane) {
        Optional<EditorTab> active = hosted.entrySet().stream().filter(entry -> entry.getValue().isActive()).map(java.util.Map.Entry::getKey).findFirst();
        Optional<String> key = pane.isPresent() ? Optional.empty() : active.flatMap(tab -> tab.session().document()).map(EditorDocument::key);
        if (window != null && openers.test(new OpenRequest(window, pane, key))) return;
        Optional<FileLocation> near = openStart(pane.isPresent(), active.or(this::selected).flatMap(tab -> folderOf(tab.session())));
        WindowOwner owner = window != null ? window : owner();
        var choice = new FileChoice(owner, "Open in Editor", FileChoice.Mode.OPEN, true, near, Optional.empty(), locations.sources());
        openLocations(context.windows().choose(choice), owner);
    }

    /** Where Open in Editor… starts: the document's folder, except in a focused terminal pane, which the host locates. */
    static Optional<FileLocation> openStart(boolean paneFocused, Optional<FileLocation> documentFolder) {
        return paneFocused ? Optional.empty() : documentFolder;
    }

    /** The folder a document is in: a local file's folder, or its file source's folder. */
    private Optional<FileLocation> folderOf(DocumentSession session) {
        Optional<FileLocation> local = localFile(session).map(Path::getParent).map(FileLocation.Local::new);
        if (local.isPresent()) return local;
        return session.document().map(EditorDocument::key).flatMap(locations::location)
            .map(found -> new FileLocation.Sourced(found.sourceId(), found.rootId(), parentOf(found.path())));
    }

    /** The folder containing a {@code /} path; {@code /} for a top-level file. */
    static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    /** Opens what the chooser returned: local files directly, a source's files through its resolver. */
    public void openLocations(List<FileLocation> chosen, WindowOwner owner) {
        for (FileLocation location : chosen) withDocument(location, owner, document -> open(document, owner));
    }

    /**
     * Runs {@code use} with the document at {@code location}: a local file's, or a file source's through its resolver,
     * which is handed the window, never the Editor's tab; a resolver's failure, or {@code use}'s on a source's file, is
     * reported, and a source with no resolver gives nothing.
     */
    private void withDocument(FileLocation location, WindowOwner owner, java.util.function.Consumer<EditorDocument> use) {
        switch (location) {
            case FileLocation.Local local -> use.accept(new LocalDocument(local.path(), context.background()));
            case FileLocation.Sourced sourced -> locations.resolver(sourced.sourceId()).ifPresent(resolver -> {
                try {
                    use.accept(resolver.document(sourced, forOthers(owner)));
                } catch (RuntimeException failure) {
                    context.notifications().group().error("Could not open a file", sourced.path() + ": "
                        + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()), failure);
                }
            });
        }
    }

    /** @return the Jasper window the Editor was last opened from, else the active one; null when there is none */
    private WindowHandle lastWindow() {
        if (origin instanceof WindowHandle window && window.isOpen()) return window;
        return context.terminals().activeWindow().orElse(null);
    }

    public void closeCurrent() { selected().ifPresent(this::closeTab); }

    /** @param tab closed after asking about unsaved changes; a tab that is saving closes when its save succeeds */
    public void closeTab(EditorTab tab) { var handle = hosted.get(tab); if (handle != null) handle.requestClose(); }

    private CompletableFuture<Boolean> approveTab(EditorTab tab) {
        if (!hosted.containsKey(tab)) return CompletableFuture.completedFuture(true);
        if (discarding.remove(tab)) return CompletableFuture.completedFuture(true);
        DocumentSession session = tab.session();
        // Pane flexibility spec 6: the file's other views keep its changes, so only its last view asks.
        if (views(session).size() > 1) return CompletableFuture.completedFuture(true);
        var pending = session.pendingSave();
        if (pending.isPresent()) return pending.get().thenCompose(saved -> saved ? approveTab(tab) : CompletableFuture.completedFuture(false));
        if (!session.dirty()) return CompletableFuture.completedFuture(true);
        String answer = environment.askers().apply(hosted.get(tab)).apply(new Question("Unsaved changes", "Save changes to " + session.name() + "?", List.of("Save", "Don't Save", "Cancel")));
        if ("Don't Save".equals(answer)) return CompletableFuture.completedFuture(true);
        if (!"Save".equals(answer)) return CompletableFuture.completedFuture(false);
        return saveSession(session).thenCompose(saved -> saved ? approveTab(tab) : CompletableFuture.completedFuture(false));
    }

    private void markdownMode(dev.jasper.editor.markdown.MarkdownMode mode) {
        selected().ifPresent(tab -> { tab.markdownMode(mode, true); refreshStatus(); });
    }

    public void goToLine() {
        selected().filter(tab -> tab.session().state() == DocumentSession.State.READY).ifPresent(tab -> {
            tab.revealSource();
            RSyntaxTextArea area = tab.area();
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Go to Line", owner(), true));
            var panel = new GoToLinePanel(context.kit(), area.getCaretLineNumber() + 1, area.getLineCount(), line -> {
                dialog.close();
                try { area.setCaretPosition(area.getLineStartOffset(line - 1)); }
                catch (BadLocationException impossible) { return; }
                area.requestFocusInWindow();
            }, dialog::close);
            dialog.onClosed(panel::close);
            dialog.setContent(panel.view());
            dialog.show();
        });
    }

    public void chooseLanguage() {
        selected().ifPresent(tab -> {
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Language", owner(), true));
            var picker = new LanguagePicker(context.kit(), tab.session().style(), style -> { dialog.close(); tab.session().setStyle(style); }, dialog::close);
            dialog.onClosed(picker::close);
            dialog.setContent(picker.view());
            dialog.show();
        });
    }

    public void toggleWrap() { selected().ifPresent(tab -> tab.session().setWrap(!tab.session().wrap())); }

    public void toggleComment() {
        selected().ifPresent(tab -> {
            tab.revealSource();
            Action action = tab.area().getActionMap().get(RSyntaxTextAreaEditorKit.rstaToggleCommentAction);
            if (action != null) action.actionPerformed(new ActionEvent(tab.area(), ActionEvent.ACTION_PERFORMED, "toggle-comment"));
        });
    }

    public void revert() { selected().ifPresent(tab -> { tab.revealSource(); tab.session().revert(); }); }

    /**
     * Revert to Saved as an action run in {@code window} does it, from the palette or a key, so that its question shows
     * there (workspace plan 8b Task 6 review): the window's focused file, else the Editor's selected file while that
     * window shows it; nothing otherwise.
     *
     * @param window the window the action ran in
     */
    public void revert(WindowHandle window) { fileIn(window).ifPresent(tab -> { activate(tab); revert(); }); }

    /** The file an action run in {@code window} acts on: its focused file, else the selected file while it shows it. */
    private Optional<EditorTab> fileIn(WindowHandle window) {
        var shown = hosted.entrySet().stream()
            .filter(entry -> !entry.getValue().isClosed() && entry.getValue().window().id().equals(window.id())).toList();
        return shown.stream().filter(entry -> entry.getValue().isActive()).map(Map.Entry::getKey).findFirst()
            .or(() -> shown.stream().map(Map.Entry::getKey).filter(tab -> tab == selectedTab).findFirst());
    }

    public List<DocumentSession> sessions() { return model.sessions(); }

    public Optional<EditorTab> selected() {
        return Optional.ofNullable(selectedTab);
    }

    String headerTitle(int index) { var session = editorTabs().get(index).session(); return (session.dirty() ? "\u25cf " : "") + session.name(); }
    String headerDetail(int index) { return model.detail(editorTabs().get(index).session()); }
    boolean showingEmpty() { return showingEmpty; }

    /**
     * The closing guard (spec 2.3) for several tabs, asked when a window closes, when Jasper quits and by Close Tab:
     * waits for running saves, then asks about unsaved tabs, and saves them all when asked to. Re-checks after each
     * wait, since edits made during it can leave a tab saving again or dirty again, rather than trusting a single pass
     * to still hold.
     * Refuses while it is already asking or waiting: a quit or close that arrives during its prompt or wait
     * would otherwise wait on saves that cannot finish until the outer prompt returns. It asks about a file only when
     * every view of it closes (pane flexibility spec 6), and then once.
     */
    private boolean mayClose(List<EditorTab> closing) {
        if (guarding) {
            if (selectedTab != null) hosted.get(selectedTab).select();
            return false;
        }
        guarding = true;
        try {
            while (true) {
                List<DocumentSession> running = lastViews(closing).stream().filter(DocumentSession::saving).toList();
                if (!running.isEmpty()) {
                    // Waits only on the saves already in flight; it starts none of its own, so a tab whose save
                    // finishes and is then edited again is not silently written without asking.
                    List<CompletableFuture<Boolean>> inFlight = running.stream().map(session -> session.pendingSave().orElseThrow()).toList();
                    if (!await.apply(saving(running.size()), allSucceeded(inFlight))) return false;
                    continue;
                }
                List<DocumentSession> dirty = lastViews(closing).stream().filter(DocumentSession::dirty).toList();
                if (dirty.isEmpty()) return true;
                String answer = ask.apply(new Question("Unsaved changes", unsaved(dirty), List.of("Save All", "Discard", "Cancel")));
                if ("Discard".equals(answer)) return true;
                if (!"Save All".equals(answer)) return false;
                var cancelled = new AtomicBoolean();
                if (!await.apply(saving(dirty.size()), saveSessions(dirty, cancelled))) {
                    // Saves already running finish; the ones still queued do not start.
                    cancelled.set(true);
                    return false;
                }
                // Loops back rather than returning true: edits typed during the non-modal wait may have left a
                // tab dirty again, and that must be asked about too.
            }
        } finally {
            guarding = false;
        }
    }

    /** The open files every view of which is among {@code closing}, each once: only they lose their changes. */
    private List<DocumentSession> lastViews(List<EditorTab> closing) {
        return closing.stream().map(EditorTab::session).distinct().filter(model.sessions()::contains)
            .filter(session -> closing.containsAll(views(session))).toList();
    }

    private void shutDown() {
        ticker.stop();
        noticeTimer.stop();
        for (DocumentSession session : model.sessions()) session.cancelLoad();
        if (ownsImageLoader) imageLoader.close();
        onClosed.run();
    }

    private CompletableFuture<Boolean> saveSession(DocumentSession session) {
        Path created = newFiles.get(session);
        if (created != null && session.untitled())
            return session.saveAs(new LocalDocument(created, context.background())).thenApply(saved -> {
                if (saved) newFiles.remove(session);
                return saved;
            });
        return session.untitled() ? saveAsSession(session) : session.save();
    }

    /**
     * The open files as Vim's buffers (Vim spec 6.2), their views as its windows (pane flexibility spec 6): numbered, and
     * saved, closed, selected and opened the Editor's way.
     */
    private VimHost.Documents documents() {
        return new VimHost.Documents(this::buffers,
            number -> tabNumbered(number).map(tab -> saveSession(tab.session())).orElse(CompletableFuture.completedFuture(false)),
            (number, file) -> tabNumbered(number).map(tab -> tab.session().saveAs(new LocalDocument(file, context.background())))
                .orElse(CompletableFuture.completedFuture(false)),
            (number, discard) -> tabNumbered(number).ifPresent(tab -> { for (EditorTab view : views(tab.session())) close(view, discard); }),
            (editor, discard) -> tabOf(editor).ifPresent(tab -> close(tab, discard)),
            number -> tabNumbered(number).ifPresent(tab -> select(tab.session())),
            editor -> tabOf(editor).ifPresent(tab -> { activate(tab); hosted.get(tab).select(); }),
            number -> tabNumbered(number).ifPresent(tab -> tab.session().reload()),
            (editor, file) -> {
                WindowOwner owner = tabOf(editor).map(hosted::get).map(WindowOwner.class::cast).orElseGet(this::owner);
                if (file.isEmpty()) newUntitled(owner);
                else if (java.nio.file.Files.exists(file.get())) openLocal(file.get(), owner);
                else newFile(file.get(), owner);
            },
            EditorWindow::readText);
    }

    /** Closes a view for Vim; {@code discard} lets its file's changes go without asking when it is the file's last view. */
    private void close(EditorTab tab, boolean discard) {
        if (discard) discarding.add(tab);
        closeTab(tab);
    }

    /**
     * The open files as Vim's buffers, one each however many views show it, as Vim shows one buffer in many windows
     * (pane flexibility spec 6): the current one is {@code editor}'s, and its line is the caret's in {@code editor}'s view,
     * another's in its most recently active view.
     */
    private List<VimHost.Buffer> buffers(dev.jasper.editor.vim.VimEditor editor) {
        var current = tabOf(editor).orElse(null);
        DocumentSession here = current == null ? null : current.session();
        return model.sessions().stream().filter(session -> !views(session).isEmpty()).map(session -> {
            Optional<Path> file = localFile(session).or(() -> Optional.ofNullable(newFiles.get(session)));
            EditorTab view = session == here ? current : views(session).getFirst();
            return new VimHost.Buffer(numbers.get(session), session.location(), file, session == here, session == previous && session != here,
                session.dirty(), session.untitled() && !newFiles.containsKey(session), view.area().getCaretLineNumber() + 1,
                views(session).size());
        }).toList();
    }

    /** @return the most recently active view of the file with buffer {@code number} */
    private Optional<EditorTab> tabNumbered(int number) {
        return model.sessions().stream().filter(session -> numbers.get(session) == number).findFirst()
            .flatMap(session -> views(session).stream().findFirst());
    }

    private Optional<EditorTab> tabOf(dev.jasper.editor.vim.VimEditor editor) {
        return editorTabs().stream().filter(tab -> tab.vimEditor().orElse(null) == editor).findFirst();
    }

    /** {@code :e newfile}: an empty tab named after the file, which its first save creates. */
    private DocumentSession newFile(Path file, WindowOwner owner) {
        origin = owner;
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        var session = DocumentSession.untitled(String.valueOf(file.getFileName()), sessionEnvironment(ref));
        newFiles.put(session, file);
        add(session, ref);
        select(session);
        return session;
    }

    /** {@code :r}: a local file's text, decoded as the Editor decodes it; empty when it cannot be read. */
    private static Optional<String> readText(Path file) {
        try {
            return Optional.of(dev.jasper.editor.text.TextCodec.decode(java.nio.file.Files.readAllBytes(file)).text());
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * Save As: Jasper's chooser in Save mode, offering local places and the file sources that have a resolver, starting
     * in the document's folder with its name filled in. A file source's location is saved through its resolver's
     * document; a file there that does not exist yet is created by the first write.
     */
    private CompletableFuture<Boolean> saveAsSession(DocumentSession session) {
        WindowOwner owner = owner(session);
        var choice = new FileChoice(owner, "Save As", FileChoice.Mode.SAVE, false, folderOf(session), Optional.of(session.name()), locations.sources());
        Optional<FileLocation> chosen = context.windows().choose(choice).stream().findFirst();
        if (chosen.isEmpty()) return CompletableFuture.completedFuture(false);
        Optional<EditorDocument> target = switch (chosen.get()) {
            case FileLocation.Local local -> Optional.<EditorDocument>of(new LocalDocument(local.path(), context.background()));
            case FileLocation.Sourced sourced -> sourcedDocument(sourced, owner);
        };
        if (target.isEmpty()) return CompletableFuture.completedFuture(false);
        if (model.find(target.get().key()).filter(other -> other != session).isPresent()) {
            ask.apply(new Question("Already open", target.get().location() + " is open in another tab.", List.of("OK")));
            return CompletableFuture.completedFuture(false);
        }
        return session.saveAs(target.get());
    }

    /** The resolver's document for a file source's location; empty, with a notice, when it cannot make one. */
    private Optional<EditorDocument> sourcedDocument(FileLocation.Sourced location, WindowOwner owner) {
        Optional<LocationResolver> resolver = locations.resolver(location.sourceId());
        if (resolver.isEmpty()) return Optional.empty();
        try {
            return Optional.of(resolver.get().document(location, forOthers(owner)));
        } catch (RuntimeException failure) {
            context.notifications().group().error("Could not save a file", location.path() + ": "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()), failure);
            return Optional.empty();
        }
    }

    /**
     * One after another, so each conflict prompt appears on its own; true only when every save succeeded. Once
     * {@code cancelled} is set, or for a tab closed meanwhile, a step starts no save; a step that throws fails.
     */
    private CompletableFuture<Boolean> saveSessions(List<DocumentSession> pending, AtomicBoolean cancelled) {
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
        for (DocumentSession session : pending)
            chain = chain.thenCompose(ok -> {
                var step = new CompletableFuture<Boolean>();
                environment.ui().execute(() -> {
                    if (cancelled.get() || !model.sessions().contains(session)) {
                        step.complete(ok);
                        return;
                    }
                    try {
                        session.pendingSave()
                            .orElseGet(() -> session.dirty() ? saveSession(session) : CompletableFuture.completedFuture(true))
                            .whenComplete((saved, error) -> step.complete(ok && error == null && Boolean.TRUE.equals(saved)));
                    } catch (RuntimeException failure) {
                        // Completes the step so the chain, and the wait on it, cannot hang.
                        step.complete(false);
                    }
                });
                return step;
            });
        return chain;
    }

    /** Combines saves already in flight without starting any new ones; true only when every one succeeds. */
    private static CompletableFuture<Boolean> allSucceeded(List<CompletableFuture<Boolean>> saves) {
        CompletableFuture<Boolean> combined = CompletableFuture.completedFuture(true);
        for (CompletableFuture<Boolean> save : saves)
            combined = combined.thenCombine(save.handle((saved, error) -> error == null && Boolean.TRUE.equals(saved)), (a, b) -> a && b);
        return combined;
    }

    private void add(DocumentSession session, java.util.concurrent.atomic.AtomicReference<WindowOwner> ownerRef) {
        add(session, ownerRef, dev.jasper.sdk.ui.TabPlacement.NEW_TAB);
    }

    /** A newly open file and its first view, where {@code placement} puts it; {@code ownerRef} is its question owner. */
    private void add(DocumentSession session, java.util.concurrent.atomic.AtomicReference<WindowOwner> ownerRef,
                     dev.jasper.sdk.ui.TabPlacement placement) {
        session.setWrap(environment.settings().get().softWrap());
        model.add(session);
        numbers.put(session, nextNumber++);
        questionOwners.put(session, ownerRef);
        addView(session, placement, null);
    }

    /**
     * A view of an open file where {@code placement} puts it: its first, or with {@code sharing} another, which shows the
     * same text and undo history (pane flexibility spec 6).
     *
     * @param sharing an open view of the file, or null for its first
     * @return the new view
     */
    private EditorTab addView(DocumentSession session, dev.jasper.sdk.ui.TabPlacement placement, EditorTab sharing) {
        EditorSettings settings = environment.settings().get();
        var tab = new EditorTab(context.kit(), session, this::closeTab, environment.timers(), sharing);
        tab.previews(context.background(), environment.ui(), imageLoader);
        tab.markdownPreferences(environment.markdownPreferences(), failed -> environment.ui().execute(() -> {
            if (!disposed) context.notifications().group().error("Could not save the Markdown default view", failed);
        }));
        tab.modeChanged(() -> activate(tab));
        tab.noticeChanged(this::refreshStatus);
        tab.surface().previewCallbacks(id -> {
            // Resolve IDs only against the currently installed, generation-validated controller result.
            if (tab.markdown() == null || tab.closed()) return;
            tab.markdown().currentResult().stream().flatMap(result -> result.links().stream())
                .filter(link -> link.id() == id).findFirst()
                .ifPresent(link -> navigation.follow(tab, dev.jasper.editor.markdown.resources.Destinations.parse(link.destination())));
        }, () -> {});
        tab.appearance(context.appearance());
        tab.settings(settings);
        tab.typography(environment.typography());
        tab.vim(vimOn() ? Optional.of(vim) : Optional.empty());
        var tabCommands = commands.stream().map(command -> new Command(command.id(), command.title(), command.stroke(), command.bound(),
            () -> { activate(tab); command.run().run(); })).toList();
        tab.bind(tabCommands);
        tab.addToPopup(tabCommands.stream().filter(command -> POPUP.contains(command.id())).toList());
        tab.area().setTransferHandler(new FileDrop(tab.area().getTransferHandler(), this::openLocalFiles));
        tab.area().addCaretListener(event -> { if (selected().orElse(null) == tab) refreshStatus(); });
        Runnable refreshing = () -> {
            if (!hosted.containsKey(tab)) return;
            refreshCommands(tab);
            refreshHeaders();
            if (selected().orElse(null) == tab) refreshStatus();
        };
        session.addListener(refreshing);
        sessionListeners.put(tab, refreshing);
        var spec = new ContentSpec("document:" + java.util.UUID.randomUUID(), session.name());
        var handle = context.windows().openTab(restoreState(session).map(spec::withRestore).orElse(spec), origin, tab.view(), placement);
        hosted.put(tab, handle); recent.addFirst(tab); questionOwners.get(session).set(handle);
        handle.setFocusTarget(tab::focusContent);
        // A restored document on another host reads when its tab is first active (workspace tabs spec 13).
        handle.onActive(active -> { if (active) { activate(tab); if (session.deferred()) session.load(); } });
        // The preview follows whether its view shows, not which pane has the focus: it is active while hosted, and its
        // controller suspends it, releasing its pixels, while its view does not show, as when another tab of its window shows.
        tab.active(true);
        handle.onClosed(() -> remove(tab));
        // Tabs and panes spec 2.10 and 9: the right-click menu's Split submenu and Move Pane to New Tab.
        PaneItems.install(tab.area().getPopupMenu(), context.windows()::splitChoices, handle);
        activate(tab);
        readVimrcOnce();
        refresh();
        return tab;
    }

    /**
     * Split File Right and Split File Down (pane flexibility spec 6): the selected file in another view, in a new pane
     * beside its own, which it selects first, so the new pane opens there though another pane had the focus. The new view
     * starts at its source's caret, selection and scroll position.
     *
     * @param placement {@code SPLIT_RIGHT} or {@code SPLIT_DOWN}
     */
    public void splitFile(dev.jasper.sdk.ui.TabPlacement placement) {
        selected().ifPresent(tab -> {
            ContentTab handle = hosted.get(tab);
            if (handle == null || handle.isClosed()) return;
            handle.select();
            origin = handle.window();
            EditorTab view = addView(tab.session(), placement, tab);
            // Rulings 19: the new view starts where its source is, as VS Code's Split Editor and IntelliJ's Split Vertically do.
            var caret = tab.area().getCaret();
            view.area().getCaret().setDot(caret.getMark());
            view.area().getCaret().moveDot(caret.getDot());
            java.awt.Point shown = tab.scroll().getViewport().getViewPosition();
            javax.swing.SwingUtilities.invokeLater(() -> { if (!view.closed()) view.scroll().getViewport().setViewPosition(shown); });
        });
    }

    /**
     * Split File Right or Down as an action run in {@code window} does it, from the palette or a key: as
     * {@link #splitFile(dev.jasper.sdk.ui.TabPlacement)}, for the file {@link #revert(WindowHandle)} would revert; nothing
     * while the window shows no file.
     *
     * @param window the window the action ran in
     * @param placement {@code SPLIT_RIGHT} or {@code SPLIT_DOWN}
     */
    public void splitFile(WindowHandle window, dev.jasper.sdk.ui.TabPlacement placement) {
        fileIn(window).ifPresent(tab -> { activate(tab); splitFile(placement); });
    }

    /**
     * Open File Right or Down of a file that is already open (pane flexibility spec 6): another view of it, in a new pane
     * beside the focused pane of {@code owner}'s window, as Open in Editor… of an open file reports it again.
     *
     * @param session the open file
     * @param owner the window or tab it was asked in
     * @param placement {@code SPLIT_RIGHT} or {@code SPLIT_DOWN}
     */
    private void openView(DocumentSession session, WindowOwner owner, dev.jasper.sdk.ui.TabPlacement placement) {
        origin = owner;
        addView(session, placement, views(session).getFirst());
        if (session.state() == DocumentSession.State.READY) session.document().ifPresent(opened);
    }

    /** A file's open views, the most recently active first (pane flexibility spec 6). */
    private List<EditorTab> views(DocumentSession session) {
        return recent.stream().filter(tab -> tab.session() == session).toList();
    }

    private void refreshCommands(EditorTab tab) {
        var handle = hosted.get(tab);
        if (handle == null || handle.isClosed()) return;
        boolean ready = tab.showingText() && tab.markdownEligible();
        if (Boolean.valueOf(ready).equals(markdownCommandsReady.put(tab, ready))) return;
        handle.setCommands(contentCommands(tab));
    }

    /** Publishes document actions; the host supplies Close Pane, Close Tab and tab navigation. */
    List<dev.jasper.sdk.ui.ContentCommand> contentCommands(EditorTab tab) {
        boolean ready = tab.showingText() && tab.markdownEligible();
        var descriptions = new ArrayList<dev.jasper.sdk.ui.ContentCommand>();
        for (var command : commands) {
            if (command.id().equals("close-pane")) continue;
            descriptions.add(new dev.jasper.sdk.ui.ContentCommand(command.id(), command.title(), command.stroke().orElse(null),
                !command.id().startsWith("markdown.") || ready,
                () -> runHosted(tab, command.run())));
        }
        descriptions.add(new dev.jasper.sdk.ui.ContentCommand("copy", "Copy", KeyNames.stroke(KeyEvent.VK_C, 0), true, () -> runHosted(tab, () -> selected().ifPresent(EditorTab::copy))));
        descriptions.add(new dev.jasper.sdk.ui.ContentCommand("paste", "Paste", KeyNames.stroke(KeyEvent.VK_V, 0), true, () -> runHosted(tab, () -> selected().ifPresent(EditorTab::paste))));
        descriptions.add(new dev.jasper.sdk.ui.ContentCommand("select-all", "Select All", null, true,
            () -> runHosted(tab, () -> selected().ifPresent(EditorTab::selectAll))));
        descriptions.add(new dev.jasper.sdk.ui.ContentCommand("cut", "Cut", null, true,
            () -> runHosted(tab, () -> selected().ifPresent(value -> { value.revealSource(); value.area().cut(); }))));
        descriptions.add(new dev.jasper.sdk.ui.ContentCommand("delete", "Delete", null, true,
            () -> runHosted(tab, () -> selected().ifPresent(value -> { value.revealSource(); value.area().replaceSelection(""); }))));
        descriptions.add(new dev.jasper.sdk.ui.ContentCommand("undo", "Undo", null, true,
            () -> runHosted(tab, () -> selected().ifPresent(value -> { value.revealSource(); value.area().undoLastAction(); }))));
        descriptions.add(new dev.jasper.sdk.ui.ContentCommand("redo", "Redo", null, true,
            () -> runHosted(tab, () -> selected().ifPresent(value -> { value.revealSource(); value.area().redoLastAction(); }))));
        return descriptions;
    }

    private void runHosted(EditorTab captured, Runnable action) {
        var owner = hosted.get(captured);
        if (owner == null || owner.isClosed()) return;
        if (!owner.isActive()) return;
        activate(captured); action.run();
    }

    private void remove(EditorTab tab) {
        if (hosted.remove(tab) == null) return;
        markdownCommandsReady.remove(tab);
        recent.remove(tab);
        discarding.remove(tab);
        DocumentSession session = tab.session();
        Runnable refreshing = sessionListeners.remove(tab);
        if (refreshing != null) session.removeListener(refreshing);
        tab.dispose();
        if (views(session).isEmpty()) forget(session);
        else questionOwners.get(session).set(hosted.get(views(session).getFirst()));
        // The view active before it, not the first opened: recent stays most recent first (pane flexibility spec 6).
        if (selectedTab == tab) selectedTab = recent.stream().findFirst().orElse(null);
        if (selectedTab != null) activate(selectedTab);
        refresh();
    }

    /** A file's last view has closed (pane flexibility spec 6): the file is no longer open. */
    private void forget(DocumentSession session) {
        session.cancelLoad(); model.remove(session);
        numbers.remove(session);
        newFiles.remove(session);
        unrestorable.remove(session);
        questionOwners.remove(session);
        if (previous == session) previous = null;
        var waiting = lastViewClosed.remove(session);
        if (waiting != null) waiting.forEach(done -> done.complete(null));
    }

    /** Selects the file's most recently active view (pane flexibility spec 6). */
    private void select(DocumentSession session) {
        views(session).stream().findFirst().ifPresent(tab -> { activate(tab); hosted.get(tab).select(); });
    }
    private void activate(EditorTab tab) {
        if (!hosted.containsKey(tab)) return;
        if (selectedTab != tab && selectedTab != null) {
            selectedTab.find(null);
            if (selectedTab.session() != tab.session()) previous = selectedTab.session();
        }
        recent.remove(tab);
        recent.addFirst(tab);
        questionOwners.get(tab.session()).set(hosted.get(tab));
        selectedTab = tab; tab.find(findBar); refreshStatus();
    }
    private void refreshFind() { if (!disposed && selectedTab != null) selectedTab.render(); }
    private List<EditorTab> editorTabs() { return List.copyOf(hosted.keySet()); }
    private void refresh() { showingEmpty = hosted.isEmpty(); refreshHeaders(); refreshStatus(); }

    private void refreshHeaders() {
        for (var entry : hosted.entrySet()) {
            DocumentSession session = entry.getKey().session();
            entry.getValue().setPresentation(new ContentPresentation(session.name(), model.detail(session),
                session.host().isPresent() ? serverIcon : fileIcon, session.dirty(), session.location(), session.host().orElse("")));
            // A document saved under a new name, or an untitled one saved for the first time, comes back as that file.
            entry.getValue().setRestore(restoreState(session));
        }
    }

    private void refreshStatus() {
        status.display(selected(), environment.settings().get().tabSize(), vimOn());
        if (selectedTab != null && hosted.containsKey(selectedTab)) {
            var tab = selectedTab;
            refreshCommands(tab);
            hosted.get(tab).setStatus(status.items().stream().map(item -> new dev.jasper.sdk.ui.ContentStatus(
                item.text(), item.tooltip(), item.run() == null ? null : () -> {
                    if (!hosted.containsKey(tab)) return;
                    activate(tab); item.run().run();
                })).toList());
        }
        if (environment.timers() && selected().flatMap(tab -> tab.navigationNotice().or(() -> tab.session().notice())).isPresent()) noticeTimer.restart();
    }

    private static Command command(String id, String title, int keyCode, int extraModifiers, Runnable run) {
        return new Command(id, title, Optional.of(KeyNames.stroke(keyCode, extraModifiers)), true, run);
    }

    /**
     * How a document's tab comes back when Jasper restores its windows (workspace tabs spec 13): by its document's key, as
     * Recent Files reopens it. An untitled document has none: an unsaved untitled buffer is not kept. A key too long for a
     * restore state (a path of thousands of characters) gives none either, with one warning that names its length: opening
     * or saving a file never fails because of its restore state (workspace plan 6b's review).
     */
    private Optional<dev.jasper.sdk.RestoreState> restoreState(DocumentSession session) {
        Optional<EditorDocument> document = session.document();
        if (document.isEmpty()) return Optional.empty();
        String key = document.get().key();
        try {
            return Optional.of(dev.jasper.sdk.RestoreState.of(RESTORE_KIND, RESTORE_KEY, key));
        } catch (IllegalArgumentException | NullPointerException refused) {
            if (unrestorable.add(session)) context.log().log(System.Logger.Level.WARNING, "A document whose key holds "
                + (key == null ? 0 : key.length()) + " characters does not come back at the next start: a restore state holds at most "
                + dev.jasper.sdk.RestoreState.MAX_CHARACTERS);
            return Optional.empty();
        }
    }

    private static Optional<Path> localFile(DocumentSession session) {
        return session.document().filter(LocalDocument.class::isInstance).map(document -> ((LocalDocument) document).file());
    }

    private static String unsaved(List<DocumentSession> dirty) {
        if (dirty.size() == 1) return dirty.getFirst().name() + " has unsaved changes.";
        StringBuilder message = new StringBuilder("These files have unsaved changes:");
        for (DocumentSession session : dirty.subList(0, Math.min(10, dirty.size()))) message.append("\n  ").append(session.name());
        if (dirty.size() > 10) message.append("\n  and ").append(dirty.size() - 10).append(" more");
        return message.toString();
    }

    private static String saving(int count) { return count == 1 ? "Saving 1 file…" : "Saving " + count + " files…"; }

}
