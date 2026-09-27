package dev.jasper.editor.ui;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.local.LocalWatcher;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.EditorModel;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginWindow;
import dev.jasper.sdk.ui.WindowSpec;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;

/** The Editor window (spec 2): tabs, commands, find, the status line, and closing and quitting. UI thread only. */
public final class EditorWindow {
    /** The window's id; it is a singleton. */
    public static final String WINDOW_ID = "dev.jasper.editor.window";
    private static final Set<String> POPUP = Set.of("save", "find", "go-to-line", "toggle-comment");

    private final EditorEnvironment environment;
    private final PluginContext context;
    private final Runnable onClosed;
    private final EditorModel model = new EditorModel();
    private final EditorTheme theme = new EditorTheme();
    private final PluginWindow window;
    private final Function<Question, String> ask;
    private final BiFunction<String, CompletableFuture<Boolean>, Boolean> await;
    private final SessionEnvironment sessions;
    private final JPanel root = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final JLabel empty = new JLabel();
    private final FindBar findBar;
    private final StatusLine status;
    private final List<Command> commands;
    private final LocalWatcher watcher;
    private final Timer ticker;
    private final Timer noticeTimer;
    private final Icon fileIcon, serverIcon, closeIcon;
    private boolean showingEmpty = true;
    private boolean guarding;

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
        closeIcon = context.appearance().icon(IconName.CLOSE);
        window = context.windows().create(new WindowSpec(WINDOW_ID, "Editor", new Dimension(900, 650), true));
        ask = environment.askers().apply(window);
        await = environment.waits().apply(window);
        sessions = new SessionEnvironment(context.background(), environment.ui(), ask, () -> environment.settings().get().maxFileBytes());
        findBar = new FindBar(() -> selected().map(EditorTab::area));
        status = new StatusLine(this::chooseLanguage, this::toggleWrap);
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
            command("next-tab", "Next Tab", KeyEvent.VK_CLOSE_BRACKET, InputEvent.SHIFT_DOWN_MASK, () -> step(1)),
            command("previous-tab", "Previous Tab", KeyEvent.VK_OPEN_BRACKET, InputEvent.SHIFT_DOWN_MASK, () -> step(-1)),
            new Command("revert", "Revert to Saved", Optional.empty(), false, this::revert),
            new Command("language", "Language…", Optional.empty(), false, this::chooseLanguage),
            new Command("wrap", "Toggle Soft Wrap", Optional.empty(), false, this::toggleWrap));
        watcher = new LocalWatcher(context.background(), environment.ui(), model::sessions);
        ticker = new Timer(2000, event -> watcher.tick());
        noticeTimer = new Timer(3000, event -> selected().ifPresent(tab -> tab.session().clearNotice()));
        noticeTimer.setRepeats(false);
        layOut();
        window.setContent(root);
        window.onClosing(this::mayClose);
        window.onCloseCommand(this::closeCommand);
        window.onClosed(this::closed);
        if (environment.timers()) ticker.start();
    }

    /** @param document opened in a new tab, or its open tab selected; the window comes to the front */
    public void open(EditorDocument document) {
        DocumentSession session = model.find(document.key()).orElseGet(() -> {
            var created = new DocumentSession(document, sessions);
            add(created);
            created.load();
            return created;
        });
        select(session);
        show();
    }

    public void openLocal(Path file) { open(new LocalDocument(file, context.background())); }

    public void openLocalFiles(List<Path> files) { for (Path file : files) openLocal(file); }

    public void newUntitled() {
        var session = DocumentSession.untitled(model.nextUntitledName(), sessions);
        add(session);
        select(session);
        show();
    }

    public void show() {
        window.show();
        window.toFront();
    }

    /** Closes the window at once, without asking; the plugin is stopping. */
    public void dispose() { window.close(); }

    /** Settings or the theme changed: applies them to every tab. */
    public void apply() {
        EditorSettings settings = environment.settings().get();
        for (EditorTab tab : editorTabs()) tab.apply(settings, theme, context.appearance().variant());
        refreshStatus();
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
        openLocalFiles(context.windows().chooseFiles(window, "Open", near));
    }

    public void closeCurrent() { selected().ifPresent(this::closeTab); }

    /** @param tab closed after asking about unsaved changes; a tab that is saving closes when its save succeeds */
    public void closeTab(EditorTab tab) {
        DocumentSession session = tab.session();
        Optional<CompletableFuture<Boolean>> pending = session.pendingSave();
        if (pending.isPresent()) {
            // Re-enters closeTab rather than removing outright: DocumentSession adopts the save's snapshot as
            // the saved text, so edits typed during the save leave the tab dirty, and closing must ask again.
            pending.get().thenAccept(saved -> environment.ui().execute(() -> { if (saved) closeTab(tab); }));
            return;
        }
        if (session.dirty()) {
            String answer = ask.apply(new Question("Unsaved changes", "Save changes to " + session.name() + "?", List.of("Save", "Don't Save", "Cancel")));
            if ("Don't Save".equals(answer)) remove(tab);
            else if ("Save".equals(answer)) saveSession(session).thenAccept(saved -> environment.ui().execute(() -> { if (saved) closeTab(tab); }));
            return;
        }
        remove(tab);
    }

    public void goToLine() {
        selected().filter(tab -> tab.session().state() == DocumentSession.State.READY).ifPresent(tab -> {
            RSyntaxTextArea area = tab.area();
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Go to Line", window, true));
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
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Language", window, true));
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
        return tabs.getSelectedComponent() instanceof EditorTab tab ? Optional.of(tab) : Optional.empty();
    }

    String headerTitle(int index) { return ((TabHeader) tabs.getTabComponentAt(index)).name.getText(); }
    String headerDetail(int index) { return ((TabHeader) tabs.getTabComponentAt(index)).detail.getText(); }
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
    boolean mayClose() {
        if (guarding) {
            window.toFront();
            return false;
        }
        guarding = true;
        try {
            while (true) {
                List<DocumentSession> running = model.sessions().stream().filter(DocumentSession::saving).toList();
                if (!running.isEmpty()) {
                    // Waits only on the saves already in flight; it starts none of its own, so a tab whose save
                    // finishes and is then edited again is not silently written without asking.
                    List<CompletableFuture<Boolean>> inFlight = running.stream().map(session -> session.pendingSave().orElseThrow()).toList();
                    if (!await.apply(saving(running.size()), allSucceeded(inFlight))) return false;
                    continue;
                }
                List<DocumentSession> dirty = model.sessions().stream().filter(DocumentSession::dirty).toList();
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

    private boolean closeCommand() {
        if (tabs.getTabCount() == 0) return false;
        closeCurrent();
        return true;
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
        Optional<Path> chosen = context.windows().chooseSaveFile(window, "Save As", initial);
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

    private void add(DocumentSession session) {
        EditorSettings settings = environment.settings().get();
        session.setWrap(settings.softWrap());
        var tab = new EditorTab(session, this::closeTab, environment.timers());
        tab.apply(settings, theme, context.appearance().variant());
        tab.bind(commands);
        tab.addToPopup(commands.stream().filter(command -> POPUP.contains(command.id())).toList());
        tab.area().setTransferHandler(new FileDrop(tab.area().getTransferHandler(), this::openLocalFiles));
        tab.area().addCaretListener(event -> { if (selected().orElse(null) == tab) refreshStatus(); });
        session.addListener(() -> {
            refreshHeaders();
            if (selected().orElse(null) == tab) refreshStatus();
        });
        model.add(session);
        tabs.addTab(session.name(), tab);
        tabs.setTabComponentAt(tabs.getTabCount() - 1, new TabHeader(tab));
        refresh();
    }

    private void remove(EditorTab tab) {
        int index = tabs.indexOfComponent(tab);
        if (index < 0) return;
        tab.session().cancelLoad();
        model.remove(tab.session());
        tabs.removeTabAt(index);
        refresh();
    }

    private void select(DocumentSession session) {
        int index = model.indexOf(session);
        if (index >= 0) tabs.setSelectedIndex(index);
    }

    private void step(int delta) {
        int count = tabs.getTabCount();
        if (count > 1) tabs.setSelectedIndex(Math.floorMod(tabs.getSelectedIndex() + delta, count));
    }

    private List<EditorTab> editorTabs() {
        List<EditorTab> out = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) if (tabs.getComponentAt(i) instanceof EditorTab tab) out.add(tab);
        return out;
    }

    private void layOut() {
        empty.setHorizontalAlignment(SwingConstants.CENTER);
        empty.putClientProperty("html.disable", true);
        empty.setText("Open a file with " + KeyNames.describe(KeyNames.stroke(KeyEvent.VK_O, 0)) + ", or double-click one in the SFTP panel.");
        empty.setForeground(UIManager.getColor("Label.disabledForeground"));
        tabs.putClientProperty("JTabbedPane.trailingComponent", moreButton());
        tabs.addChangeListener(event -> refreshStatus());
        center.add(empty, "empty");
        center.add(tabs, "tabs");
        var south = new JPanel(new BorderLayout());
        south.add(findBar, BorderLayout.NORTH);
        south.add(status, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        for (Command command : commands) {
            if (!command.bound() || command.stroke().isEmpty()) continue;
            root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(command.stroke().get(), command.id());
            root.getActionMap().put(command.id(), EditorTab.action(command));
        }
        var drop = new FileDrop(null, this::openLocalFiles);
        for (JComponent target : List.of(root, empty, tabs)) target.setTransferHandler(drop);
        refresh();
    }

    private JButton moreButton() {
        var more = new JButton("⋯");
        more.setToolTipText("Editor commands");
        more.getAccessibleContext().setAccessibleName("Editor commands");
        more.putClientProperty("JButton.buttonType", "borderless");
        more.addActionListener(event -> menu().show(more, 0, more.getHeight()));
        return more;
    }

    private void refresh() {
        showingEmpty = tabs.getTabCount() == 0;
        cards.show(center, showingEmpty ? "empty" : "tabs");
        refreshHeaders();
        refreshStatus();
    }

    private void refreshHeaders() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (!(tabs.getComponentAt(i) instanceof EditorTab tab) || !(tabs.getTabComponentAt(i) instanceof TabHeader header)) continue;
            DocumentSession session = tab.session();
            header.update((session.dirty() ? "● " : "") + session.name(), model.detail(session), session.host().isPresent() ? serverIcon : fileIcon);
            tabs.setToolTipTextAt(i, session.location());
        }
    }

    private void refreshStatus() {
        status.display(selected(), environment.settings().get().tabSize());
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

    /** A tab's title: its icon, name, dim detail and a close button. */
    private final class TabHeader extends JPanel {
        private final JLabel name = new JLabel();
        private final JLabel detail = new JLabel();

        TabHeader(EditorTab tab) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setOpaque(false);
            name.putClientProperty("html.disable", true);
            detail.putClientProperty("html.disable", true);
            detail.setForeground(UIManager.getColor("Label.disabledForeground"));
            var close = new JButton(closeIcon);
            close.setToolTipText("Close tab");
            close.getAccessibleContext().setAccessibleName("Close tab");
            close.putClientProperty("JButton.buttonType", "toolBarButton");
            close.setFocusable(false);
            close.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
            close.addActionListener(event -> closeTab(tab));
            add(name);
            add(detail);
            add(close);
        }

        void update(String title, String detailText, Icon icon) {
            name.setText(title);
            name.setIcon(icon);
            detail.setText(detailText);
            detail.setVisible(!detailText.isEmpty());
        }
    }
}
