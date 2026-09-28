package dev.jasper.editor.ui;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.local.LocalWatcher;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.EditorModel;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimHost;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.ContentSpec;
import dev.jasper.sdk.ui.ContentPresentation;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
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
    private final EditorModel model = new EditorModel();
    private final EditorTheme theme = new EditorTheme();
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
    private final Icon fileIcon, serverIcon;
    private boolean showingEmpty = true;
    private boolean guarding;
    private final Vim vim = new Vim(new VimHost(SystemClipboard::read, SystemClipboard::write));
    /** Vim mode as toggled in this session, until the settings file says the same; null to follow the settings. */
    private Boolean vimToggled;

    /**
     * Creates the window, not yet shown.
     *
     * @param environment what the window needs from the plugin
     * @param onClosed runs once the window has closed
     */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed) {
        this.environment = environment;
        this.context = environment.context();
        this.onClosed = onClosed;
        fileIcon = context.appearance().icon(IconName.FILE);
        serverIcon = context.appearance().icon(IconName.SERVER);
        ask = question -> environment.askers().apply(owner()).apply(question);
        await = (message, saves) -> environment.waits().apply(owner()).apply(message, saves);
        findBar = new FindBar(() -> selected().map(EditorTab::area));
        status = new StatusLine(this::chooseLanguage, this::toggleWrap, this::toggleVim);
        commands = List.of(
            command("save", "Save", KeyEvent.VK_S, 0, this::save),
            command("save-as", "Save As…", KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK, this::saveAs),
            command("save-all", "Save All", KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK, () -> saveAll()),
            command("open", "Open…", KeyEvent.VK_O, 0, this::openChooser),
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
            new Command("toggle-vim", "Toggle Vim Mode", Optional.empty(), false, this::toggleVim));
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
        DocumentSession session = model.find(document.key()).orElseGet(() -> {
            var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
            var created = new DocumentSession(document, sessionEnvironment(ref));
            add(created, ref); created.load(); return created;
        });
        select(session);
    }
    private SessionEnvironment sessionEnvironment(java.util.concurrent.atomic.AtomicReference<WindowOwner> owner) {
        return new SessionEnvironment(context.background(), environment.ui(),
            question -> owner.get() instanceof ContentTab tab && tab.isClosed() ? question.dismissed() : environment.askers().apply(owner.get()).apply(question), () -> environment.settings().get().maxFileBytes());
    }
    private WindowOwner owner() { return promptOwner != null ? promptOwner : selectedTab == null ? origin : hosted.get(selectedTab); }
    private WindowOwner owner(DocumentSession session) { return hosted.entrySet().stream().filter(e -> e.getKey().session() == session).map(e -> (WindowOwner)e.getValue()).findFirst().orElseGet(this::owner); }
    public void openLocal(Path file) { openLocal(file, null); }
    public void openLocal(Path file, WindowOwner owner) { open(new LocalDocument(file, context.background()), owner); }
    public void openLocalFiles(List<Path> files) { openLocalFiles(files, owner()); }
    public void openLocalFiles(List<Path> files, WindowOwner owner) { for (Path file : files) openLocal(file, owner); }
    public void newUntitled() { newUntitled(owner()); }
    public void newUntitled(WindowOwner owner) {
        origin = owner;
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        var session = DocumentSession.untitled(model.nextUntitledName(), sessionEnvironment(ref));
        add(session, ref); select(session);
    }
    public void show() { if (selectedTab == null) newUntitled(); else hosted.get(selectedTab).select(); }
    public void dispose() {
        if (disposed) return; disposed = true;
        for (var handle : List.copyOf(hosted.values())) handle.close();
        closeRegistration.close(); closed();
    }

    /** Settings or the theme changed: applies them to every tab. */
    public void apply() {
        EditorSettings settings = environment.settings().get();
        if (vimToggled != null && vimToggled == settings.vim()) vimToggled = null;
        for (EditorTab tab : editorTabs()) {
            tab.apply(settings, theme, context.appearance().variant(), context.appearance().editorPalette());
            tab.vim(vimOn() ? Optional.of(vim) : Optional.empty());
        }
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

    /** Checks open local files for outside changes; the window's timer calls this every 2 s. */
    public void tick() { watcher.tick(); }

    public void save() { selected().ifPresent(tab -> saveSession(tab.session())); }

    public void saveAs() { selected().ifPresent(tab -> saveAsSession(tab.session())); }

    /** @return completes with true when every tab with unsaved changes was saved */
    public CompletableFuture<Boolean> saveAll() {
        return saveSessions(model.sessions().stream().filter(DocumentSession::dirty).toList(), new AtomicBoolean());
    }

    public void openChooser() {
        Optional<Path> near = selected().flatMap(tab -> localFile(tab.session())).map(Path::getParent);
        openLocalFiles(context.windows().chooseFiles(owner(), "Open", near));
    }

    public void closeCurrent() { selected().ifPresent(this::closeTab); }

    /** @param tab closed after asking about unsaved changes; a tab that is saving closes when its save succeeds */
    public void closeTab(EditorTab tab) { var handle = hosted.get(tab); if (handle != null) handle.requestClose(); }

    private CompletableFuture<Boolean> approveTab(EditorTab tab) {
        if (!hosted.containsKey(tab)) return CompletableFuture.completedFuture(true);
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
            dialog.setContent(new GoToLinePanel(area.getCaretLineNumber() + 1, area.getLineCount(), line -> {
                dialog.close();
                try { area.setCaretPosition(area.getLineStartOffset(line - 1)); }
                catch (BadLocationException impossible) { return; }
                area.requestFocusInWindow();
            }, dialog::close));
            dialog.show();
        });
    }

    public void chooseLanguage() {
        selected().ifPresent(tab -> {
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Language", owner(), true));
            dialog.setContent(new LanguagePicker(tab.session().style(), style -> { dialog.close(); tab.session().setStyle(style); }));
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

    JPopupMenu menu() {
        var menu = new JPopupMenu();
        for (Command command : commands) menu.add(EditorTab.menuItem(command));
        return menu;
    }

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

    private void closed() {
        ticker.stop();
        noticeTimer.stop();
        for (DocumentSession session : model.sessions()) session.cancelLoad();
        onClosed.run();
    }

    private CompletableFuture<Boolean> saveSession(DocumentSession session) {
        return session.untitled() ? saveAsSession(session) : session.save();
    }

    private CompletableFuture<Boolean> saveAsSession(DocumentSession session) {
        Optional<Path> initial = localFile(session).or(() -> Optional.of(Path.of(System.getProperty("user.home"), session.name())));
        Optional<Path> chosen = context.windows().chooseSaveFile(owner(session), "Save As", initial);
        if (chosen.isEmpty()) return CompletableFuture.completedFuture(false);
        if (model.find(LocalDocument.key(chosen.get())).filter(other -> other != session).isPresent()) {
            ask.apply(new Question("Already open", chosen.get() + " is open in another tab.", List.of("OK")));
            return CompletableFuture.completedFuture(false);
        }
        return session.saveAs(new LocalDocument(chosen.get(), context.background()));
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
        EditorSettings settings = environment.settings().get();
        session.setWrap(settings.softWrap());
        var tab = new EditorTab(session, this::closeTab, environment.timers());
        tab.apply(settings, theme, context.appearance().variant(), context.appearance().editorPalette());
        tab.vim(vimOn() ? Optional.of(vim) : Optional.empty());
        var tabCommands = commands.stream().map(command -> new Command(command.id(), command.title(), command.stroke(), command.bound(),
            () -> { activate(tab); command.run().run(); })).toList();
        tab.bind(tabCommands);
        tab.addToPopup(tabCommands.stream().filter(command -> POPUP.contains(command.id())).toList());
        tab.area().setTransferHandler(new FileDrop(tab.area().getTransferHandler(), this::openLocalFiles));
        tab.area().addCaretListener(event -> { if (selected().orElse(null) == tab) refreshStatus(); });
        session.addListener(() -> {
            refreshHeaders();
            if (selected().orElse(null) == tab) refreshStatus();
        });
        model.add(session);
        var handle = context.windows().openTab(new ContentSpec("document:" + java.util.UUID.randomUUID(), session.name()), origin, tab);
        hosted.put(tab, handle); ownerRef.set(handle);
        handle.setFocusTarget(tab.area()::requestFocusInWindow);
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
        refresh();
    }

    private void remove(EditorTab tab) {
        if (hosted.remove(tab) == null) return;
        tab.dispose(); tab.session().cancelLoad(); model.remove(tab.session());
        if (selectedTab == tab) selectedTab = hosted.keySet().stream().findFirst().orElse(null);
        if (selectedTab != null) activate(selectedTab);
        refresh();
    }
    private void select(DocumentSession session) {
        for (var entry : hosted.entrySet()) if (entry.getKey().session() == session) { activate(entry.getKey()); entry.getValue().select(); return; }
    }
    private void activate(EditorTab tab) {
        if (!hosted.containsKey(tab)) return;
        if (selectedTab != tab && selectedTab != null) selectedTab.remove(findBar);
        selectedTab = tab; tab.add(findBar, BorderLayout.SOUTH); tab.revalidate(); refreshStatus();
    }
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
