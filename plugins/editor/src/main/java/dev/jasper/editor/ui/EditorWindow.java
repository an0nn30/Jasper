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
    private static final Set<String> POPUP = Set.of("save", "find", "go-to-line", "toggle-comment");

    private final EditorEnvironment environment;
    private final PluginContext context;
    private final Runnable onClosed;
    private final java.util.function.Consumer<EditorDocument> opened;
    private final Predicate<OpenRequest> openers;
    private final Locations locations;
    private final EditorModel model = new EditorModel();
    private final java.util.Map<EditorTab, ContentTab> hosted = new java.util.LinkedHashMap<>();
    private EditorTab selectedTab;
    private WindowOwner origin;
    private WindowOwner promptOwner;
    private dev.jasper.sdk.Subscription closeRegistration;
    private boolean disposed;
    private final Function<Question, String> ask;
    private final BiFunction<String, CompletableFuture<Boolean>, Boolean> await;
    private final FindBar findBar;
    private final StatusLine status;
    private final List<Command> commands;
    private final LocalWatcher watcher;
    private final Timer ticker;
    private final Timer noticeTimer;
    private final String fileIcon, serverIcon;
    private boolean showingEmpty = true;
    private boolean guarding;
    private final Vim vim;
    /** Each tab's Vim buffer number (Vim spec 6.2), kept while it is open. */
    private final java.util.Map<DocumentSession, Integer> numbers = new java.util.HashMap<>();
    private int nextNumber = 1;
    /** The tab selected before the current one: Vim's alternate buffer. */
    private EditorTab previous;
    /** Untitled tabs {@code :e} opened for files that do not exist yet: their first save creates the file. */
    private final java.util.Map<DocumentSession, Path> newFiles = new java.util.HashMap<>();
    /** Tabs {@code :q!} closes: their changes go without asking. */
    private final Set<EditorTab> discarding = new java.util.HashSet<>();
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
        findBar = new FindBar(() -> selected().map(EditorTab::area), this::refreshFind, id -> selected().ifPresent(tab -> { tab.view().focus(id); tab.view().selectAll(id); }));
        status = new StatusLine(this::chooseLanguage, this::toggleWrap, this::toggleVim);
        commands = List.of(
            command("save", "Save", KeyEvent.VK_S, 0, this::save),
            command("save-as", "Save As…", KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK, this::saveAs),
            command("save-all", "Save All", KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK, () -> saveAll()),
            new Command("open", "Open…", Optional.empty(), false, this::openChooser),
            command("new", "New File", KeyEvent.VK_N, 0, this::newUntitled),
            new Command("close-tab", "Close Tab", Optional.of(KeyNames.stroke(KeyEvent.VK_W, 0)), false, this::closeCurrent),
            command("find", "Find…", KeyEvent.VK_F, 0, () -> findBar.open(false)),
            command("replace", "Replace…", KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK, () -> findBar.open(true)),
            command("find-next", "Find Next", KeyEvent.VK_G, 0, findBar::next),
            command("find-previous", "Find Previous", KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK, findBar::previous),
            command("go-to-line", "Go to Line…", KeyEvent.VK_L, 0, this::goToLine),
            command("toggle-comment", "Toggle Comment", KeyEvent.VK_SLASH, 0, this::toggleComment),
            new Command("revert", "Revert to Saved", Optional.empty(), false, this::revert),
            new Command("language", "Language…", Optional.empty(), false, this::chooseLanguage),
            new Command("wrap", "Toggle Soft Wrap", Optional.empty(), false, this::toggleWrap),
            new Command("toggle-vim", "Toggle Vim Mode", Optional.empty(), false, this::toggleVim),
            new Command("reload-vimrc", "Reload vimrc", Optional.empty(), false, this::reloadVimrc));
        vim = new Vim(new VimHost(SystemClipboard::read, SystemClipboard::write, documents(), actions()));
        watcher = new LocalWatcher(context.background(), environment.ui(), model::sessions);
        ticker = new Timer(2000, event -> watcher.tick());
        noticeTimer = new Timer(3000, event -> selected().ifPresent(tab -> tab.session().clearNotice()));
        noticeTimer.setRepeats(false);
        closeRegistration = context.windows().onTabsClosing((handles, batch) -> {
            var closing = hosted.entrySet().stream().filter(e -> handles.contains(e.getValue())).map(java.util.Map.Entry::getKey).toList();
            if (closing.isEmpty()) return CompletableFuture.completedFuture(true);
            if (!batch && closing.size() == 1) return approveTab(closing.getFirst());
            promptOwner = handles.getFirst();
            try { return CompletableFuture.completedFuture(mayClose(closing)); }
            finally { promptOwner = null; }
        });
        if (environment.timers()) ticker.start();
    }

    /** @param document opened in a new tab, or its open tab selected; the window comes to the front */
    public void open(EditorDocument document) { open(document, null); }

    /** Opens a file in the originating workspace; null chooses the last main workspace. */
    public void open(EditorDocument document, WindowOwner owner) {
        origin = owner;
        Optional<DocumentSession> existing = model.find(document.key());
        DocumentSession session = existing.orElseGet(() -> {
            var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
            var created = new DocumentSession(document, sessionEnvironment(ref));
            add(created, ref); reportWhenLoaded(created); created.load(); return created;
        });
        if (existing.isPresent() && session.state() == DocumentSession.State.READY) session.document().ifPresent(opened);
        select(session);
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
    private WindowOwner owner(DocumentSession session) { return hosted.entrySet().stream().filter(e -> e.getKey().session() == session).map(e -> (WindowOwner)e.getValue()).findFirst().orElseGet(this::owner); }
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

    /** Completes once the session's tab has closed: when the user closes it, its window closes or the Editor stops. */
    public CompletableFuture<Void> closed(DocumentSession session) {
        var done = new CompletableFuture<Void>();
        var handle = hosted.entrySet().stream().filter(entry -> entry.getKey().session() == session)
            .map(Map.Entry::getValue).findFirst();
        if (handle.isEmpty() || handle.get().isClosed()) done.complete(null);
        else handle.get().onClosed(() -> done.complete(null));
        return done;
    }

    private void moveCaret(DocumentSession session, int line, int column) {
        if (line <= 0) return;
        hosted.keySet().stream().filter(tab -> tab.session() == session).findFirst().ifPresent(tab -> {
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
    public void newUntitled(WindowOwner owner) { newUntitled(owner, dev.jasper.sdk.ui.TabPlacement.ACTIVE_GROUP); }
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
            tab.vim(vimOn() ? Optional.of(vim) : Optional.empty());
        }
        readVimrcOnce();
        refreshStatus();
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
        for (FileLocation location : chosen) {
            switch (location) {
                case FileLocation.Local local -> openLocal(local.path(), owner);
                case FileLocation.Sourced sourced -> locations.resolver(sourced.sourceId()).ifPresent(resolver -> {
                    try {
                        open(resolver.document(sourced, owner), owner);
                    } catch (RuntimeException failure) {
                        context.notifications().group().error("Could not open a file", sourced.path() + ": "
                            + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()), failure);
                    }
                });
            }
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
        var pending = session.pendingSave();
        if (pending.isPresent()) return pending.get().thenCompose(saved -> saved ? approveTab(tab) : CompletableFuture.completedFuture(false));
        if (!session.dirty()) return CompletableFuture.completedFuture(true);
        String answer = environment.askers().apply(hosted.get(tab)).apply(new Question("Unsaved changes", "Save changes to " + session.name() + "?", List.of("Save", "Don't Save", "Cancel")));
        if ("Don't Save".equals(answer)) return CompletableFuture.completedFuture(true);
        if (!"Save".equals(answer)) return CompletableFuture.completedFuture(false);
        return saveSession(session).thenCompose(saved -> saved ? approveTab(tab) : CompletableFuture.completedFuture(false));
    }

    public void goToLine() {
        selected().filter(tab -> tab.session().state() == DocumentSession.State.READY).ifPresent(tab -> {
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
            Action action = tab.area().getActionMap().get(RSyntaxTextAreaEditorKit.rstaToggleCommentAction);
            if (action != null) action.actionPerformed(new ActionEvent(tab.area(), ActionEvent.ACTION_PERFORMED, "toggle-comment"));
        });
    }

    public void revert() { selected().ifPresent(tab -> tab.session().revert()); }

    public List<DocumentSession> sessions() { return model.sessions(); }

    public Optional<EditorTab> selected() {
        return Optional.ofNullable(selectedTab);
    }

    String headerTitle(int index) { var session = editorTabs().get(index).session(); return (session.dirty() ? "\u25cf " : "") + session.name(); }
    String headerDetail(int index) { return model.detail(editorTabs().get(index).session()); }
    boolean showingEmpty() { return showingEmpty; }

    /**
     * The closing guard (spec 2.3), asked when the window closes and when Jasper quits: waits for running saves,
     * then asks about unsaved tabs, and saves them all when asked to. Re-checks after each wait, since edits made
     * during it can leave a tab saving again or dirty again, rather than trusting a single pass to still hold.
     * Refuses while it is already asking or waiting: a quit or close that arrives during its prompt or wait
     * would otherwise wait on saves that cannot finish until the outer prompt returns.
     */
    boolean mayClose() { return mayClose(editorTabs()); }
    private boolean mayClose(List<EditorTab> closing) {
        if (guarding) {
            if (selectedTab != null) hosted.get(selectedTab).select();
            return false;
        }
        guarding = true;
        try {
            while (true) {
                List<DocumentSession> running = closing.stream().map(EditorTab::session).filter(model.sessions()::contains).filter(DocumentSession::saving).toList();
                if (!running.isEmpty()) {
                    // Waits only on the saves already in flight; it starts none of its own, so a tab whose save
                    // finishes and is then edited again is not silently written without asking.
                    List<CompletableFuture<Boolean>> inFlight = running.stream().map(session -> session.pendingSave().orElseThrow()).toList();
                    if (!await.apply(saving(running.size()), allSucceeded(inFlight))) return false;
                    continue;
                }
                List<DocumentSession> dirty = closing.stream().map(EditorTab::session).filter(model.sessions()::contains).filter(DocumentSession::dirty).toList();
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

    private void shutDown() {
        ticker.stop();
        noticeTimer.stop();
        for (DocumentSession session : model.sessions()) session.cancelLoad();
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

    /** The tabs as Vim's buffers (Vim spec 6.2): numbered, and saved, closed, selected and opened the Editor's way. */
    private VimHost.Documents documents() {
        return new VimHost.Documents(this::buffers,
            number -> tabNumbered(number).map(tab -> saveSession(tab.session())).orElse(CompletableFuture.completedFuture(false)),
            (number, file) -> tabNumbered(number).map(tab -> tab.session().saveAs(new LocalDocument(file, context.background())))
                .orElse(CompletableFuture.completedFuture(false)),
            (number, discard) -> tabNumbered(number).ifPresent(tab -> {
                if (discard) discarding.add(tab);
                closeTab(tab);
            }),
            number -> tabNumbered(number).ifPresent(tab -> select(tab.session())),
            number -> tabNumbered(number).ifPresent(tab -> tab.session().reload()),
            (editor, file) -> {
                WindowOwner owner = tabOf(editor).map(hosted::get).map(WindowOwner.class::cast).orElseGet(this::owner);
                if (file.isEmpty()) newUntitled(owner);
                else if (java.nio.file.Files.exists(file.get())) openLocal(file.get(), owner);
                else newFile(file.get(), owner);
            },
            EditorWindow::readText);
    }

    private List<VimHost.Buffer> buffers(dev.jasper.editor.vim.VimEditor editor) {
        var current = tabOf(editor).orElse(null);
        return editorTabs().stream().map(tab -> {
            DocumentSession session = tab.session();
            Optional<Path> file = localFile(session).or(() -> Optional.ofNullable(newFiles.get(session)));
            return new VimHost.Buffer(numbers.get(session), session.location(), file, tab == current, tab == previous && tab != current,
                session.dirty(), session.untitled() && !newFiles.containsKey(session), tab.area().getCaretLineNumber() + 1);
        }).toList();
    }

    private Optional<EditorTab> tabNumbered(int number) {
        return editorTabs().stream().filter(tab -> numbers.get(tab.session()) == number).findFirst();
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
            return Optional.of(resolver.get().document(location, owner));
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
        add(session, ownerRef, dev.jasper.sdk.ui.TabPlacement.ACTIVE_GROUP);
    }

    private void add(DocumentSession session, java.util.concurrent.atomic.AtomicReference<WindowOwner> ownerRef,
                     dev.jasper.sdk.ui.TabPlacement placement) {
        EditorSettings settings = environment.settings().get();
        session.setWrap(settings.softWrap());
        var tab = new EditorTab(context.kit(), session, this::closeTab, environment.timers());
        tab.appearance(context.appearance());
        tab.settings(settings);
        tab.vim(vimOn() ? Optional.of(vim) : Optional.empty());
        var tabCommands = commands.stream().map(command -> new Command(command.id(), command.title(), command.stroke(), command.bound(),
            () -> { activate(tab); command.run().run(); })).toList();
        tab.bind(tabCommands);
        tab.addToPopup(tabCommands.stream().filter(command -> POPUP.contains(command.id())).toList());
        tab.area().setTransferHandler(new FileDrop(tab.area().getTransferHandler(), this::openLocalFiles));
        tab.area().addCaretListener(event -> { if (selected().orElse(null) == tab) refreshStatus(); });
        session.addListener(() -> {
            if (!hosted.containsKey(tab)) return;
            refreshHeaders();
            if (selected().orElse(null) == tab) refreshStatus();
        });
        model.add(session);
        numbers.put(session, nextNumber++);
        var handle = context.windows().openTab(new ContentSpec("document:" + java.util.UUID.randomUUID(), session.name()), origin, tab.view(), placement);
        hosted.put(tab, handle); ownerRef.set(handle);
        handle.setFocusTarget(tab::focusText);
        handle.onActive(active -> { if (active) activate(tab); });
        handle.onClosed(() -> remove(tab));
        var descriptions = new ArrayList<dev.jasper.sdk.ui.ContentCommand>();
        for (var command : commands) {
            if (command.id().equals("next-tab") || command.id().equals("previous-tab")) continue;
            descriptions.add(new dev.jasper.sdk.ui.ContentCommand(command.id(), command.title(), command.stroke().orElse(null), true,
                () -> { activate(tab); command.run().run(); }));
        }
        descriptions.add(new dev.jasper.sdk.ui.ContentCommand("copy", "Copy", KeyNames.stroke(KeyEvent.VK_C, 0), true, tab.area()::copy));
        descriptions.add(new dev.jasper.sdk.ui.ContentCommand("paste", "Paste", KeyNames.stroke(KeyEvent.VK_V, 0), true, tab.area()::paste));
        handle.setCommands(descriptions);
        activate(tab);
        readVimrcOnce();
        refresh();
    }

    private void remove(EditorTab tab) {
        if (hosted.remove(tab) == null) return;
        tab.dispose(); tab.session().cancelLoad(); model.remove(tab.session());
        numbers.remove(tab.session());
        newFiles.remove(tab.session());
        discarding.remove(tab);
        if (previous == tab) previous = null;
        if (selectedTab == tab) selectedTab = hosted.keySet().stream().findFirst().orElse(null);
        if (selectedTab != null) activate(selectedTab);
        refresh();
    }
    private void select(DocumentSession session) {
        for (var entry : hosted.entrySet()) if (entry.getKey().session() == session) { activate(entry.getKey()); entry.getValue().select(); return; }
    }
    private void activate(EditorTab tab) {
        if (!hosted.containsKey(tab)) return;
        if (selectedTab != tab && selectedTab != null) {
            selectedTab.find(null);
            previous = selectedTab;
        }
        selectedTab = tab; tab.find(findBar); refreshStatus();
    }
    private void refreshFind() { if (!disposed && selectedTab != null) selectedTab.render(); }
    private void step(int delta) {
        var all = editorTabs(); if (all.size() > 1) select(all.get(Math.floorMod(all.indexOf(selectedTab) + delta, all.size())).session());
    }
    private List<EditorTab> editorTabs() { return List.copyOf(hosted.keySet()); }
    private void refresh() { showingEmpty = hosted.isEmpty(); refreshHeaders(); refreshStatus(); }

    private void refreshHeaders() {
        for (var entry : hosted.entrySet()) {
            DocumentSession session = entry.getKey().session();
            entry.getValue().setPresentation(new ContentPresentation(session.name(), model.detail(session),
                session.host().isPresent() ? serverIcon : fileIcon, session.dirty(), session.location(), session.host().orElse("")));
        }
    }

    private void refreshStatus() {
        status.display(selected(), environment.settings().get().tabSize(), vimOn());
        if (selectedTab != null && hosted.containsKey(selectedTab)) {
            var tab = selectedTab;
            hosted.get(tab).setStatus(status.items().stream().map(item -> new dev.jasper.sdk.ui.ContentStatus(
                item.text(), item.tooltip(), item.run() == null ? null : () -> {
                    if (!hosted.containsKey(tab)) return;
                    activate(tab); item.run().run();
                })).toList());
        }
        if (environment.timers() && selected().flatMap(tab -> tab.session().notice()).isPresent()) noticeTimer.restart();
    }

    private static Command command(String id, String title, int keyCode, int extraModifiers, Runnable run) {
        return new Command(id, title, Optional.of(KeyNames.stroke(keyCode, extraModifiers)), true, run);
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
