package dev.jasper.app.workspace;

import dev.jasper.app.appearance.BuiltinTheme;
import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.commands.CommandRegistry;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.history.CommandHistory;
import dev.jasper.app.history.ShellHistoryIndex;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.palette.PaletteKeyRouter;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.ScopeRegistry;
import dev.jasper.app.palette.builtin.CommandsScope;
import dev.jasper.app.palette.builtin.ShellHistoryScope;
import dev.jasper.app.palette.builtin.SnippetsScope;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.snippets.SnippetStore;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.config.ToolbarMode;


import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/** Real window contents, independent of native JFrame construction for headless testing. */
public final class WindowContent extends JPanel implements AutoCloseable {
    private final ShellLauncher launcher;
    private final Consumer<Path> newWindow;
    private final Runnable quit;
    private final Runnable onEmpty;
    private final JTabbedPane tabs = new TerminalDeck();
    private final WindowTabs windowTabs;
    private final WorkspaceActions workspaceActions;
    private final CommandRegistry commands = new CommandRegistry();
    private final WindowCommands windowCommands;
    private final ScopeRegistry scopes = new ScopeRegistry();
    private final CommandsScope commandsScope;
    private final boolean macOs;
    private final WindowCommandPalette commandPalette;
    // EDT-owned roster gives a held sequence priority over every window's new shortcuts,
    // regardless of the order in which KeyboardFocusManager registered their dispatchers.
    private static final java.util.List<WindowContent> PALETTE_KEY_OWNERS = new ArrayList<>();
    private final PaletteKeyRouter paletteKeys;
    private final KeyEventDispatcher paletteDispatcher = this::paletteKeysDispatch;
    private KeyboardFocusManager paletteFocusManager;
    private javax.swing.Timer paletteTailCleanup;
    private ToolbarMode toolbarMode = ToolbarMode.ICONS_AND_LABELS;
    private KeyBindings bindings;
    private final WorkspaceConfiguration workspaceConfiguration = new WorkspaceConfiguration(this);
    private final WindowChrome chrome;
    private final ThemeController themes;
    private final Subscription themeRegistration;
    private JRootPane bindingRoot;
    Runnable onMinimumSizeChanged = () -> {};
    static final int DEFAULT_TAB_HEIGHT = 38;
    static final int MIN_TAB_HEIGHT = 28;
    static final int MAX_TAB_HEIGHT = 72;
    private int tabHeight = DEFAULT_TAB_HEIGHT;
    Runnable onTabHeightChanged = () -> {};
    java.util.function.ToIntFunction<JComponent> confirmTabHeight = control -> JOptionPane.showConfirmDialog(
        this, control, "Tab height", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    private boolean closed;
    private boolean rearranging;
    private boolean active = true;
    Consumer<ResolvedTheme> onThemeChanged = theme -> {};
    private Runnable openSettings, reloadConfiguration;
    private Runnable unregisterConfiguration = () -> {};
    Consumer<JComponent> showConfigDiagnostics = control -> JOptionPane.showMessageDialog(
        this, control, "Configuration", JOptionPane.PLAIN_MESSAGE);
    Consumer<String> onTitle = title -> {};
    public Consumer<String> onError = message -> JOptionPane.showMessageDialog(this, message, "Jasper", JOptionPane.ERROR_MESSAGE);
    /** Application-owned buddy toggle; the window only forwards and displays state. */
    public Runnable onToggleBuddy = () -> {};
    public java.util.function.BooleanSupplier buddyEnabled = () -> false;
    private boolean historyEnabled = true;
    private final java.util.List<Consumer<WorkspaceActivity.Event>> activityListeners = new ArrayList<>();
    /** Whether any Jasper window has focus; application-supplied, EDT-only. */
    public java.util.function.BooleanSupplier anyWindowActive = () -> true;

    /** Replays existing producers before returning; the listener receives later events until closed. */
    public Subscription activity(Consumer<WorkspaceActivity.Event> listener) {
        Objects.requireNonNull(listener);
        if (closed) return new Subscription(() -> {});
        activityListeners.add(listener);
        try {
            for (int i = 0; i < tabs.getTabCount(); i++)
                for (TerminalPane pane : ((TerminalTab) tabs.getComponentAt(i)).panes())
                    listener.accept(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.OPENED));
        } catch (RuntimeException | Error failure) { activityListeners.remove(listener); throw failure; }
        return new Subscription(() -> activityListeners.remove(listener));
    }

    private void emit(WorkspaceActivity.Event event) {
        for (var listener : java.util.List.copyOf(activityListeners)) listener.accept(event);
    }

    private ShellHistoryIndex shellHistory;
    private Subscription historyRegistration;
    private SnippetStore snippets;
    private Subscription snippetsRegistration;

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty) {
        this(launcher, directory, newWindow, quit, onEmpty, new ThemeController());
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes) {
        this(launcher, directory, newWindow, quit, onEmpty, themes,
            KeyBindings.defaults(System.getProperty("os.name").startsWith("Mac")));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings) {
        this(launcher, directory, newWindow, quit, onEmpty, themes, bindings, System::nanoTime);
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, java.util.function.LongSupplier animationClock) {
        this(launcher, directory, newWindow, quit, onEmpty, themes, bindings, animationClock,
            new CommandHistory(), System.getProperty("os.name").startsWith("Mac"));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, java.util.function.LongSupplier animationClock,
                  CommandHistory history, boolean macOs) {
        this(launcher, directory, newWindow, quit, onEmpty, themes, bindings, animationClock, history, macOs, null);
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, java.util.function.LongSupplier animationClock,
                  CommandHistory history, boolean macOs, ShellHistoryIndex shellHistory) {
        this(launcher, directory, newWindow, quit, onEmpty, themes, bindings, animationClock, history, macOs,
            shellHistory, null);
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, java.util.function.LongSupplier animationClock,
                  CommandHistory history, boolean macOs, ShellHistoryIndex shellHistory, SnippetStore snippets) {
        super(new BorderLayout());
        this.bindings = bindings;
        this.macOs = macOs;
        this.themes = themes;
        this.launcher = launcher; this.newWindow = newWindow; this.quit = quit; this.onEmpty = onEmpty;
        workspaceActions = new WorkspaceActions(this);
        windowCommands = new WindowCommands(this, commands);
        chrome = new WindowChrome(this);
        commandsScope = new CommandsScope(commands, history, macOs, this::dispatchCommand);
        scopes.register(commandsScope);
        commandPalette = new WindowCommandPalette(this, scopes, PaletteScope.COMMANDS_ID, macOs);
        this.shellHistory = shellHistory;
        this.snippets = snippets;
        syncHistoryScope();
        if (snippets != null) snippetsRegistration = scopes.register(new SnippetsScope(snippets, message -> onError.accept(message)));
        paletteKeys = new PaletteKeyRouter(commandPalette.controller(), commandPalette::open, () -> this.bindings, macOs,
            source -> !closed && active && bindingRoot != null && source != null
                && SwingUtilities.isDescendingFrom(this, bindingRoot)
                && SwingUtilities.isDescendingFrom(source, bindingRoot));
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) syncPaletteDispatcher();
        });
        windowTabs = new WindowTabs(this, animationClock);
        var north = new JPanel(new BorderLayout());
        north.add(windowTabs, BorderLayout.NORTH); north.add(chrome.toolbar(), BorderLayout.CENTER);
        add(north, BorderLayout.NORTH); add(tabs); add(chrome.status(), BorderLayout.SOUTH);
        tabs.addChangeListener(event -> {
            if (!rearranging) {
                if (commandPalette != null) commandPalette.dismiss();
                update();
                if (currentTab() != null) currentTab().focusTerminal();
            }
        });
        themeRegistration = themes.subscribe(this::applyTheme);
        newTab(directory);
    }

    /** Workspace adapter: wires title/theme values to the platform-only title bar. */
    static MacTitleBar installTitleBar(JRootPane root, WindowContent content, boolean supported,
                                      Consumer<String> nativeTitle) {
        var bar = MacTitleBar.install(root, content, content.windowTabs(), content::tabHeight,
            () -> content.onMinimumSizeChanged.run(), supported);
        content.onTitle = value -> {
            String display = TerminalTitle.windowTitle(value);
            nativeTitle.accept(display);
            if (bar != null) bar.setTitle(display, content.tabStrip().getTabCount() <= 1);
        };
        if (bar != null) {
            content.onThemeChanged = theme -> bar.setLight(theme.chrome() == BuiltinTheme.LIGHT);
            content.onTabHeightChanged = bar::refreshHeight;
            bar.setLight(content.theme().chrome() == BuiltinTheme.LIGHT);
        }
        content.update();
        return bar;
    }

    void installRootBindings(JRootPane root) {
        removeRootBindings();
        bindingRoot = root;
        for (ActionId id : ActionId.values()) bindings.strokeFor(id).ifPresent(stroke -> {
            root.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(stroke, id.id());
            root.getActionMap().put(id.id(), new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) {
                    dispatchShortcut(stroke, KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
                }
            });
        });
        commandPalette.install(root);
        syncPaletteDispatcher();
    }

    private boolean paletteKeysDispatch(KeyEvent event) {
        boolean tail = false;
        for (WindowContent owner : java.util.List.copyOf(PALETTE_KEY_OWNERS)) {
            tail |= owner.paletteKeys.dispatchTail(event);
            if (owner.paletteKeys.drained() && (owner.closed || !owner.paletteAttached()))
                owner.removePaletteDispatcher();
        }
        return tail || paletteKeys.dispatch(event);
    }

    private boolean paletteAttached() {
        return bindingRoot != null && SwingUtilities.isDescendingFrom(this, bindingRoot);
    }

    private void syncPaletteDispatcher() {
        if (!closed && paletteAttached()) {
            if (paletteTailCleanup != null) { paletteTailCleanup.stop(); paletteTailCleanup = null; }
            if (paletteFocusManager == null) {
                paletteFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                PALETTE_KEY_OWNERS.add(this);
                paletteFocusManager.addKeyEventDispatcher(paletteDispatcher);
            }
        } else {
            if (!closed) commandPalette.dismiss();
            if (paletteKeys.drained()) removePaletteDispatcher();
            else if (paletteTailCleanup == null) {
                paletteTailCleanup = new javax.swing.Timer(2000, event -> {
                    paletteKeys.reset(); removePaletteDispatcher();
                });
                paletteTailCleanup.setRepeats(false); paletteTailCleanup.start();
            }
        }
    }

    private void removePaletteDispatcher() {
        if (paletteTailCleanup != null) { paletteTailCleanup.stop(); paletteTailCleanup = null; }
        if (paletteFocusManager != null) {
            paletteFocusManager.removeKeyEventDispatcher(paletteDispatcher); paletteFocusManager = null;
            PALETTE_KEY_OWNERS.remove(this);
        }
    }

    private void removeRootBindings() {
        if (bindingRoot == null) return;
        for (ActionId id : ActionId.values()) {
            bindings.strokeFor(id).ifPresent(stroke -> bindingRoot.getInputMap(WHEN_IN_FOCUSED_WINDOW).remove(stroke));
            bindingRoot.getActionMap().remove(id.id());
        }
        bindingRoot = null;
    }

    void setBindings(KeyBindings replacement) {
        JRootPane root = bindingRoot;
        removeRootBindings();
        bindings = Objects.requireNonNull(replacement);
        for (ActionId id : ActionId.values())
            action(id).putValue(Action.ACCELERATOR_KEY, bindings.strokeFor(id).orElse(null));
        if (root != null) installRootBindings(root);
        toolbar().revalidate(); toolbar().repaint();
        windowTabs.refresh();
    }

    public void connectConfiguration(Runnable settings, Runnable reload, Runnable unregister) {
        if (closed) return;
        openSettings = settings; reloadConfiguration = reload; unregisterConfiguration = unregister;
        action(ActionId.OPEN_SETTINGS).putValue(Action.SHORT_DESCRIPTION, "Open configuration file");
        action(ActionId.RELOAD_CONFIG).putValue(Action.SHORT_DESCRIPTION, "Reload configuration file");
        updateActions();
    }

    public void disconnectConfiguration() {
        openSettings = null; reloadConfiguration = null; unregisterConfiguration = () -> {};
        status().onConfigurationDetails = () -> {};
        status().configButton().setEnabled(false);
        updateActions();
    }

    public void setConfigurationState(ConfigService.State state) {
        if (closed) return;
        status().setConfiguration(state);
        status().onConfigurationDetails = () -> {
            String details = state.file() + "\n\n" + (state.diagnostics().isEmpty()
                ? (state.present() ? "Configuration loaded successfully." : "Using built-in defaults. Open Settings to create this file.")
                : String.join("\n", state.diagnostics().stream().map(ConfigDiagnostic::formatted).toList()));
            JTextArea text = new JTextArea(details, 12, 64);
            text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true); text.setCaretPosition(0);
            text.getAccessibleContext().setAccessibleName("Configuration details");
            showConfigDiagnostics.accept(new JScrollPane(text));
        };
    }

    public void applyConfiguration(ConfigSnapshot next, boolean macOs) { workspaceConfiguration.apply(next, macOs); }
    float configuredFontSize() { return workspaceConfiguration.configuredFontSize(); }
    boolean closed() { return closed; }
    boolean configurationConnected() { return openSettings != null && reloadConfiguration != null; }
    void openSettings() { openSettings.run(); }
    void reloadConfiguration() { reloadConfiguration.run(); }
    void requestNewWindow() { newWindow.accept(directory()); }
    void requestQuit() { quit.run(); }

    CommandRegistry commands() { return commands; }
    ScopeRegistry scopes() { return scopes; }
    CommandsScope commandsScope() { return commandsScope; }
    void dispatchCommand(Command command) {
        command.action().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command.id()));
    }
    /** The application shortcut that opens a scope directly, for the picker's trailing tag; null when none. */
    String scopeShortcut(String scopeId) {
        ActionId id = switch (scopeId) {
            case PaletteScope.COMMANDS_ID -> ActionId.COMMAND_PALETTE;
            case PaletteScope.HISTORY_ID -> ActionId.HISTORY_PALETTE;
            case PaletteScope.SNIPPETS_ID -> ActionId.SNIPPETS_PALETTE;
            default -> null;
        };
        return id == null ? null : CommandsScope.shortcutText(action(id).getValue(Action.ACCELERATOR_KEY), macOs);
    }
    String tabShortcut(int index) {
        if (index < 0 || index >= 9) return "";
        ActionId id = ActionId.valueOf("SELECT_TAB_" + (index + 1));
        return CommandsScope.shortcutText(action(id).getValue(Action.ACCELERATOR_KEY), macOs);
    }
    SnippetStore snippets() { return snippets; }
    WindowCommandPalette commandPalette() { return commandPalette; }
    WindowChrome chrome() { return chrome; }
    WindowCommands windowCommands() { return windowCommands; }
    ToolbarMode toolbarMode() { return toolbarMode; }
    public boolean isActiveAndOpen() { return active && !closed; }
    boolean updatingActions() { return workspaceActions.updating(); }
    Action action(ActionId id) { return workspaceActions.action(id); }
    KeyBindings bindings() { return bindings; }
    JToolBar toolbar() { return chrome.toolbar(); }
    WindowStatusBar status() { return chrome.status(); }
    JMenuBar menuBar() { return chrome.menuBar(); }
    JTabbedPane tabStrip() { return tabs; }
    WindowTabs windowTabs() { return windowTabs; }
    public TerminalTab currentTab() { return (TerminalTab) tabs.getSelectedComponent(); }
    public TerminalPane currentPane() { return currentTab() == null ? null : currentTab().focusedPane(); }
    Path directory() { return currentPane() == null ? Path.of(System.getProperty("user.home")) : currentPane().directory(); }

    public void newTab(Path directory) {
        if (closed) return;
        TerminalTab tab = new TerminalTab(directory, launcher);
        tab.onChanged = () -> {
            for (TerminalPane pane : tab.panes()) if (pane.view() == null) pane.applyTheme(themes.current().palette());
            update();
        };
        tab.onEmpty = () -> closeTab(tab);
        tab.onError = message -> onError.accept(message);
        tab.configure = pane -> configurePane(tab, pane);
        tab.onPaneCreated = pane -> connectActivity(tab, pane);
        for (TerminalPane pane : tab.panes()) connectActivity(tab, pane);
        tab.setBackground(themes.current().palette().background());
        for (TerminalPane pane : tab.panes()) pane.applyTheme(themes.current().palette());
        tabs.addTab(tab.title(), tab);
        tabs.setSelectedComponent(tab); update(); tab.start();
    }

    private void connectActivity(TerminalTab tab, TerminalPane pane) {
        emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.OPENED));
        pane.onCommandStarted = command -> {
            long startedAt = System.nanoTime();
            emit(new WorkspaceActivity.Started(pane.id(), command, () -> System.nanoTime() - startedAt,
                () -> { selectTab(tab); tab.focus(pane); pane.focusTerminal(); },
                pane.watched() && isActiveAndOpen() && tab == currentTab()));
        };
        pane.onTitleChanged = title -> emit(new WorkspaceActivity.TitleChanged(pane.id(), title));
        pane.onClosed = () -> emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.CLOSED));
        pane.onPaneFocused = () -> emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.FOCUSED));
        pane.onPaneBlurred = () -> emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.BLURRED));
        pane.onCommandFinished = (command, exitStatus, duration) -> emit(new WorkspaceActivity.Finished(
            pane.id(), command, exitStatus, duration,
            new WorkspaceActivity.Origin(anyWindowActive.getAsBoolean(), isActiveAndOpen(),
                tab == currentTab(), pane.view() != null && pane.view().isFocusOwner()),
            () -> { selectTab(tab); tab.focus(pane); pane.focusTerminal(); }));
    }

    private void configurePane(TerminalTab tab, TerminalPane pane) {
        pane.allowLaunchFocus = () -> commandPalette == null || !commandPalette.isOpen();
        pane.onCommandExecuted = entry -> { if (shellHistory != null) shellHistory.record(entry); };
        pane.applyTheme(themes.current().palette());
        ConfigSnapshot configured = workspaceConfiguration.snapshot();
        float configuredFontSize = workspaceConfiguration.configuredFontSize();
        if (configured == null) {
            pane.view().setFontSize(configuredFontSize);
        } else {
            pane.view().applyOptions(configured.viewOptions(configuredFontSize, themes.current().palette()));
            pane.setConfiguredDim(configured.terminal().dimInactivePanes());
            pane.setShellExitBehavior(configured.terminal().onExit());
        }
        pane.view().setShortcutHandler(event -> dispatchShortcut(KeyStroke.getKeyStrokeForEvent(event), pane.view()));
        pane.view().setContextMenuHandler(event -> {
            selectTab(tab); tab.focus(pane); pane.focusTerminal(); update();
            JPopupMenu popup = chrome.contextMenu();
            popup.show(pane.view(), event.getX(), event.getY());
        });
    }

    void selectTab(TerminalTab tab) {
        if (tabs.indexOfComponent(tab) >= 0) { tabs.setSelectedComponent(tab); tab.focusTerminal(); }
    }

    void reorderTab(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= tabs.getTabCount() || to >= tabs.getTabCount()) return;
        Component selected = tabs.getSelectedComponent();
        TerminalTab tab = (TerminalTab) tabs.getComponentAt(from);
        rearranging = true;
        try {
            tabs.removeTabAt(from); tabs.insertTab(tab.title(), null, tab, null, to);
            tabs.setSelectedComponent(selected);
        } finally { rearranging = false; }
        update();
    }

    public void closeTab(TerminalTab tab) {
        int index = tabs.indexOfComponent(tab);
        if (index < 0) return;
        tab.close(); tabs.removeTabAt(index); update();
        if (tabs.getTabCount() == 0 && !closed) onEmpty.run();
    }

    boolean dispatchShortcut(KeyStroke stroke, Component source) {
        if (paletteKeys.dispatchShortcut(stroke, source == null ? this : source)) return true;
        Optional<ActionId> found = bindings.actionFor(stroke);
        if (found.isEmpty()) return false;
        ActionId id = found.get();
        if (source instanceof JTextComponent && (id == ActionId.COPY || id == ActionId.PASTE)) return false;
        updateActions();
        // A recognized but unavailable command is still an app key, never terminal input.
        if (action(id).isEnabled()) invoke(id);
        return true;
    }

    void invoke(ActionId id) { workspaceActions.invoke(id); }
    public void updateActions() { workspaceActions.update(); }

    void update() {
        if (closed) return;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            TerminalTab tab = (TerminalTab) tabs.getComponentAt(i);
            tabs.setTitleAt(i, tab.title());
            tab.setActive(active && tab == currentTab());
        }
        TerminalPane pane = currentPane();
        String size = pane == null || pane.session() == null ? "Starting terminal" :
            pane.session().columns() + " \u00d7 " + pane.session().rows();
        chrome.status().setMetadata(pane == null ? "" : pane.shellLabel(), pane == null ? "" : pane.directory().toString(),
            pane == null ? "" : size, pane != null && pane.running(), pane != null && pane.shellIntegrationDetected());
        onTitle.accept(currentTab() == null ? "Jasper" : currentTab().title());
        updateActions(); windowTabs.refresh(); onMinimumSizeChanged.run();
    }

    ResolvedTheme theme() { return themes.current(); }

    Appearance appearance() { return themes.choice(); }

    void selectTheme(BuiltinTheme theme) {
        selectAppearance(theme == BuiltinTheme.LIGHT ? Appearance.LIGHT : Appearance.DARK);
    }

    void selectAppearance(Appearance appearance) {
        if (closed) return;
        try { themes.selectAppearance(appearance); }
        catch (ThemeController.InstallationFailure failure) { onError.accept(failure.getMessage()); }
        finally { chrome.refreshTheme(); chrome.status().refreshTheme(); }
    }

    /** Updates all retained panes without reparenting them; the native boundary hooks in last. */
    void applyTheme(ResolvedTheme theme, boolean updateDelegates) {
        if (closed) return;
        var retained = new ArrayList<TerminalTab>();
        for (int i = 0; i < tabs.getTabCount(); i++) retained.add((TerminalTab) tabs.getComponentAt(i));
        retained.forEach(TerminalTab::beginThemeUpdate);
        try {
            if (updateDelegates) SwingUtilities.updateComponentTreeUI(bindingRoot == null ? this : bindingRoot);
            if (updateDelegates && (bindingRoot == null || menuBar().getParent() == null)) SwingUtilities.updateComponentTreeUI(menuBar());
            for (TerminalTab tab : retained) {
                tab.setBackground(theme.palette().background());
                for (TerminalPane pane : tab.panes()) {
                    // Zoom detaches sibling panes from the visible component hierarchy.
                    if (updateDelegates && !SwingUtilities.isDescendingFrom(pane, this)) SwingUtilities.updateComponentTreeUI(pane);
                    pane.applyTheme(theme.palette());
                }
            }
            setBackground(theme.palette().background());
            tabs.setBackground(theme.palette().background());
            chrome.status().applyPalette(theme.palette());
            chrome.refreshTheme();
            if (commandPalette != null) commandPalette.refreshTheme();
            onThemeChanged.accept(theme); update();
            revalidate(); repaint();
        } finally { retained.forEach(TerminalTab::endThemeUpdate); }
    }

    int tabHeight() { return tabHeight; }

    void setTabHeight(int height) {
        if (height < MIN_TAB_HEIGHT || height > MAX_TAB_HEIGHT)
            throw new IllegalArgumentException("Tab height must be between 28 and 72 pixels");
        if (closed || tabHeight == height) return;
        tabHeight = height;
        windowTabs.revalidate(); windowTabs.repaint();
        onTabHeightChanged.run();
        revalidate(); repaint();
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) { root.revalidate(); root.repaint(); }
        onMinimumSizeChanged.run();
    }

    void setActive(boolean value) {
        if (!value && commandPalette != null) commandPalette.dismiss();
        active = value; windowTabs.setActive(value); update();
    }
    void setToolbarMode(ToolbarMode mode) {
        toolbarMode = mode; chrome.setToolbarMode(mode); updateActions(); revalidate(); onMinimumSizeChanged.run();
    }
    void setStatusVisible(boolean visible) {
        chrome.setStatusVisible(visible); updateActions(); revalidate(); onMinimumSizeChanged.run();
    }
    public void setHistoryEnabled(boolean value) { historyEnabled = value; syncHistoryScope(); updateActions(); }
    boolean historyEnabled() { return historyEnabled; }

    private void syncHistoryScope() {
        boolean wanted = shellHistory != null && historyEnabled && !closed;
        if (wanted && historyRegistration == null)
            historyRegistration = scopes.register(new ShellHistoryScope(shellHistory, snippets, AppIcons.icon("history")));
        else if (!wanted && historyRegistration != null) { historyRegistration.close(); historyRegistration = null; }
    }

    @Override public void close() {
        if (closed) return;
        paletteKeys.close();
        commandPalette.close(); windowCommands.close(); commands.close();
        if (historyRegistration != null) { historyRegistration.close(); historyRegistration = null; }
        if (snippetsRegistration != null) { snippetsRegistration.close(); snippetsRegistration = null; }
        scopes.close();
        closed = true;
        syncPaletteDispatcher();
        unregisterConfiguration.run(); disconnectConfiguration();
        showConfigDiagnostics = control -> {};
        windowTabs.close();
        themeRegistration.close();
        for (int i = 0; i < tabs.getTabCount(); i++) ((TerminalTab) tabs.getComponentAt(i)).close();
        tabs.removeAll(); removeRootBindings();
        workspaceActions.disable();
        windowTabs.refresh();
        onThemeChanged = theme -> {};
        onTabHeightChanged = () -> {};
        confirmTabHeight = control -> JOptionPane.CANCEL_OPTION;
        onTitle = title -> {}; onError = message -> {}; onMinimumSizeChanged = () -> {};
        onToggleBuddy = () -> {}; buddyEnabled = () -> false;
        activityListeners.clear();
        anyWindowActive = () -> false;
    }
}
