package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.commands.CommandRegistry;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.palette.PaletteKeyRouter;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.ScopeRegistry;
import dev.jasper.app.palette.CommandsScope;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.ToolbarMode;


import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.SessionRequest;

/** Real window contents, independent of native JFrame construction for headless testing. */
public final class WindowContent extends JPanel implements AutoCloseable {
    private final ShellLauncher launcher;
    private final Consumer<Path> newWindow;
    private final Runnable quit;
    private final Runnable onEmpty;
    private final java.util.Map<Component, dev.jasper.app.contributions.ContentEntry> hosted = new IdentityHashMap<>();
    private dev.jasper.app.contributions.ContentRegistry contentRegistry;
    private Subscription contentHost;
    private Runnable bringToFront = () -> {};
    private final boolean detached;
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
    private boolean unifiedHeader;
    private KeyBindings bindings;
    private final UUID id = UUID.randomUUID();
    private KeyBindings baseBindings;
    private WindowContributions contributed;
    private final WorkspaceRegions regions;
    private final WindowRail rail;
    private UiState uiState = UiState.inMemory();
    private final WorkspaceConfiguration workspaceConfiguration = new WorkspaceConfiguration(this);
    private final WindowChrome chrome;
    private final ThemeController themes;
    private final Subscription themeRegistration;
    private JRootPane bindingRoot;
    private final Map<KeyStroke, String> contentBindings = new HashMap<>();
    private final Map<Component, dev.jasper.app.lifecycle.Subscription> contentListeners = new IdentityHashMap<>();
    Runnable onMinimumSizeChanged = () -> {};
    static final int DEFAULT_TAB_HEIGHT = 30;
    static final int MIN_TAB_HEIGHT = 20;
    static final int MAX_TAB_HEIGHT = 72;
    private int tabHeight = DEFAULT_TAB_HEIGHT;
    Runnable onTabHeightChanged = () -> {};
    java.util.function.ToIntFunction<JComponent> confirmTabHeight = control -> JOptionPane.showConfirmDialog(
        this, control, "Tab height", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    private int railWidth = ConfigSnapshot.DEFAULT_RAIL_WIDTH;
    java.util.function.ToIntFunction<JComponent> confirmRailWidth = control -> JOptionPane.showConfirmDialog(
        this, control, "Rail width", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    private int toolbarHeight = ConfigSnapshot.DEFAULT_TOOLBAR_HEIGHT;
    java.util.function.ToIntFunction<JComponent> confirmToolbarHeight = control -> JOptionPane.showConfirmDialog(
        this, control, "Toolbar height", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    private int statusBarHeight = ConfigSnapshot.DEFAULT_STATUS_BAR_HEIGHT;
    java.util.function.ToIntFunction<JComponent> confirmStatusBarHeight = control -> JOptionPane.showConfirmDialog(
        this, control, "Status bar height", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    private boolean closed;
    private boolean rearranging;
    private WindowTerminals terminals;
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
    private final java.util.List<Consumer<WorkspaceActivity.Event>> activityListeners = new ArrayList<>();
    /** Whether any Jasper window has focus; application-supplied, EDT-only. */
    public java.util.function.BooleanSupplier anyWindowActive = () -> true;

    /** Replays existing producers before returning; the listener receives later events until closed. */
    public Subscription activity(Consumer<WorkspaceActivity.Event> listener) {
        Objects.requireNonNull(listener);
        if (closed) return new Subscription(() -> {});
        activityListeners.add(listener);
        try {
            for (TerminalTab tab : terminalTabs())
                for (TerminalPane pane : tab.panes())
                    listener.accept(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.OPENED));
        } catch (RuntimeException | Error failure) { activityListeners.remove(listener); throw failure; }
        return new Subscription(() -> activityListeners.remove(listener));
    }

    private void emit(WorkspaceActivity.Event event) {
        for (var listener : java.util.List.copyOf(activityListeners)) listener.accept(event);
    }


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
        this(launcher, directory, newWindow, quit, onEmpty, themes, bindings,
            new CommandHistory(), System.getProperty("os.name").startsWith("Mac"));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs) {
        super(new BorderLayout());
        detached = directory == null;
        setFocusTraversalPolicyProvider(true);
        setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
            @Override public Component getDefaultComponent(Container root) {
                TerminalPane pane = currentPane();
                if (closed) return null;
                if (selectedContent() != null) return selectedContent().component();
                if (pane == null) return null;
                return pane.view() == null ? pane : pane.view();
            }
        });
        this.baseBindings = bindings;
        this.bindings = bindings;
        this.macOs = macOs;
        this.themes = themes;
        this.launcher = launcher; this.newWindow = newWindow; this.quit = quit; this.onEmpty = onEmpty;
        workspaceActions = new WorkspaceActions(this);
        windowCommands = new WindowCommands(this, commands);
        chrome = new WindowChrome(this);
        commandsScope = new CommandsScope(commands, history, macOs, this::dispatchCommand);
        scopes.register(commandsScope);
        commandPalette = new WindowCommandPalette(this, scopes, PaletteScope.ALL_ID, macOs);
        paletteKeys = new PaletteKeyRouter(commandPalette.controller(), commandPalette::open, () -> this.bindings, macOs,
            source -> !closed && active && bindingRoot != null && source != null
                && SwingUtilities.isDescendingFrom(this, bindingRoot)
                && SwingUtilities.isDescendingFrom(source, bindingRoot));
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) syncPaletteDispatcher();
        });
        windowTabs = new WindowTabs(this);
        // The tabs belong to the terminal column, so the rail and the side panels reach the toolbar.
        var terminalColumn = new JPanel(new BorderLayout());
        // The rounded host paints behind this layout-only container, including partial tab repaints.
        terminalColumn.setOpaque(false);
        terminalColumn.add(windowTabs, BorderLayout.NORTH); terminalColumn.add(tabs, BorderLayout.CENTER);
        regions = new WorkspaceRegions(terminalColumn);
        regions.setStatusVisible(chrome.status().isVisible());
        regions.setHeaderVisible(chrome.toolbar().isVisible());
        rail = new WindowRail();
        rail.setVisible(false);
        var body = new JPanel(new BorderLayout());
        body.add(rail, BorderLayout.WEST); body.add(regions, BorderLayout.CENTER);
        add(chrome.toolbar(), BorderLayout.NORTH); add(body); add(chrome.status(), BorderLayout.SOUTH);
        tabs.addChangeListener(event -> {
            if (!rearranging) {
                if (terminals != null) terminals.tabSelected();
                if (commandPalette != null) commandPalette.dismiss();
                update();
                focusSelected();
            }
        });
        themeRegistration = themes.subscribe(this::applyTheme);
        if (directory != null) newTab(directory);
        else setPreferredSize(new Dimension(900, 650));
    }

    /** Workspace adapter: wires title/theme values to the platform-only title bar. */
    static MacTitleBar installTitleBar(JRootPane root, WindowContent content, boolean supported,
                                      Consumer<String> nativeTitle) {
        var bar = MacTitleBar.install(root, content, () -> content.onMinimumSizeChanged.run(), supported);
        if (bar != null) {
            content.unifiedHeader = true;
            content.regions.setHeaderVisible(true);
            content.remove(content.toolbar());
            ((WindowToolbar) content.toolbar()).setTitleBar(true);
            bar.setToolbar(content.toolbar());
        }
        content.onTitle = value -> {
            TerminalPane pane = content.currentPane();
            var document = content.selectedContent();
            var context = WindowHeaderContext.from(pane == null ? null : pane.snapshot(),
                Path.of(System.getProperty("user.home")), pane == null ? "" : pane.programTitle(),
                pane == null ? "" : pane.commandLabel());
            if (document != null) context = new WindowHeaderContext(document.title() + (document.host().isEmpty() ? "" : " — " + document.host()), document.location(), document.host().isEmpty() ? "FILE" : "SERVER");
            nativeTitle.accept(context.label());
            if (bar != null) {
                bar.setContext(context.label(), context.tooltip(), dev.jasper.app.platform.AppIcons.named(context.icon()));
            }
        };
        if (bar != null) {
            content.onThemeChanged = theme -> bar.setLight(!theme.chrome().dark());
            bar.setLight(!content.theme().chrome().dark());
        }
        content.update();
        return bar;
    }

    void installRootBindings(JRootPane root) {
        removeRootBindings();
        bindingRoot = root;
        bindings.strokes().forEach((actionId, stroke) -> {
            root.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(stroke, actionId);
            root.getActionMap().put(actionId, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) {
                    dispatchShortcut(stroke, KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
                }
            });
        });
        syncContentBindings();
        commandPalette.install(root);
        syncPaletteDispatcher();
    }

    private boolean paletteKeysDispatch(KeyEvent event) {
        if (dev.jasper.app.platform.WindowInput.blocked(event.getComponent())) return false;
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

    private void syncContentBindings() {
        if (bindingRoot == null) return;
        var input = bindingRoot.getInputMap(WHEN_IN_FOCUSED_WINDOW);
        contentBindings.forEach((stroke, key) -> {
            input.remove(stroke); bindingRoot.getActionMap().remove(key);
            bindings.idFor(stroke).ifPresent(id -> input.put(stroke, id));
        });
        contentBindings.clear();
        var entry = selectedContent();
        if (entry == null) return;
        for (var command : entry.commands()) {
            var stroke = command.shortcut();
            if (stroke == null) continue;
            String key = "hosted:" + command.id();
            contentBindings.put(stroke, key); input.put(stroke, key);
            bindingRoot.getActionMap().put(key, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) {
                    dispatchShortcut(stroke, KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
                }
            });
        }
    }

    private void removeRootBindings() {
        if (bindingRoot == null) return;
        contentBindings.forEach((stroke, key) -> {
            bindingRoot.getInputMap(WHEN_IN_FOCUSED_WINDOW).remove(stroke);
            bindingRoot.getActionMap().remove(key);
        });
        contentBindings.clear();
        bindings.strokes().forEach((actionId, stroke) -> {
            bindingRoot.getInputMap(WHEN_IN_FOCUSED_WINDOW).remove(stroke);
            bindingRoot.getActionMap().remove(actionId);
        });
        bindingRoot = null;
    }

    void setBindings(KeyBindings replacement) {
        baseBindings = Objects.requireNonNull(replacement);
        rebind();
    }

    /** Effective bindings are the saved ones plus contributed actions in registration order. */
    void rebind() {
        JRootPane root = bindingRoot;
        removeRootBindings();
        bindings = contributed == null ? baseBindings : baseBindings.withExtensions(contributed.extensions()).bindings();
        for (ActionId id : ActionId.values())
            action(id).putValue(Action.ACCELERATOR_KEY, bindings.strokeFor(id).orElse(null));
        if (contributed != null) contributed.applyAccelerators();
        if (root != null) installRootBindings(root);
        toolbar().revalidate(); toolbar().repaint();
        refreshTabs();
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
    /** The application shortcut that opens a scope directly, shown in its tab's tooltip; null when none. */
    String scopeShortcut(String scopeId) {
        ActionId id = switch (scopeId) {
            case PaletteScope.ALL_ID -> ActionId.COMMAND_PALETTE;
            default -> null;
        };
        return id == null ? null : CommandsScope.shortcutText(action(id).getValue(Action.ACCELERATOR_KEY), macOs);
    }
    String tabShortcut(int index) {
        if (index < 0 || index >= 9) return "";
        ActionId id = ActionId.valueOf("SELECT_TAB_" + (index + 1));
        return CommandsScope.shortcutText(action(id).getValue(Action.ACCELERATOR_KEY), macOs);
    }
    WindowCommandPalette commandPalette() { return commandPalette; }
    WindowChrome chrome() { return chrome; }
    WindowCommands windowCommands() { return windowCommands; }
    ToolbarMode toolbarMode() { return toolbarMode; }
    public boolean isActiveAndOpen() { return active && !closed; }

    /** Stable identity of this window for extensions; never a Swing object. */
    public UUID id() { return id; }

    /** Connects with layout state that lives only as long as this window. */
    /** Connects this window to the application-wide terminal registry, once. {@code toFront} raises the native window. */
    public void connectTerminals(TerminalRegistry registry, Runnable toFront) {
        if (closed || terminals != null) return;
        bringToFront = toFront;
        terminals = new WindowTerminals(this, Objects.requireNonNull(registry), Objects.requireNonNull(toFront));
    }

    java.util.List<TerminalTab> terminalTabs() {
        var result = new ArrayList<TerminalTab>();
        for (int i = 0; i < tabs.getTabCount(); i++) if (tabs.getComponentAt(i) instanceof TerminalTab tab) result.add(tab);
        return result;
    }

    public void connectContributions(Contributions model) { connectContributions(model, UiState.inMemory()); }

    /**
     * Connects this window to the application-wide contributions model, once, remembering panel
     * placement and rail visibility in {@code state}.
     */
    public void connectContributions(Contributions model, UiState state) {
        if (closed || contributed != null) return;
        uiState = Objects.requireNonNull(state);
        contributed = new WindowContributions(this, Objects.requireNonNull(model), state);
        contentRegistry = model.content();
        contentHost = contentRegistry.addHost(new dev.jasper.app.contributions.ContentHost(id, detached,
            this::mountContent, this::unmountContent, entry -> selectTab(entry.component()), () -> bringToFront.run()));
        rebind();
    }

    private void mountContent(dev.jasper.app.contributions.ContentEntry entry) {
        hosted.put(entry.component(), entry);
        contentListeners.put(entry.component(), entry.onChanged(this::update));
        tabs.addTab(entry.title(), entry.component());
        selectTab(entry.component()); update();
    }
    private void unmountContent(dev.jasper.app.contributions.ContentEntry entry) {
        var listener = contentListeners.remove(entry.component());
        if (listener != null) listener.close();
        hosted.remove(entry.component()); tabs.remove(entry.component()); update();
        if (tabs.getTabCount() == 0 && !closed) onEmpty.run();
    }
    public dev.jasper.app.contributions.ContentEntry selectedContent() { return hosted.get(tabs.getSelectedComponent()); }
    Component selectedComponent() { return tabs.getSelectedComponent(); }
    String tabTitle(Component component) {
        var entry = hosted.get(component);
        return entry == null ? ((TerminalTab) component).title() : (entry.dirty() ? "\u25cf " : "") + entry.title() + (entry.detail().isEmpty() ? "" : "  " + entry.detail());
    }
    Icon tabIcon(Component component) { var entry = hosted.get(component); return entry == null ? ((TerminalTab)component).icon() : entry.icon(); }
    String tabTooltip(Component component) { var entry = hosted.get(component); return entry == null ? tabTitle(component) : entry.location(); }
    JPopupMenu tabMenu(Component component) {
        var menu = new JPopupMenu(); var entry = hosted.get(component);
        if (entry != null) {
            var move = new JMenuItem(entry.detached() ? "Move to Main Window" : "Open in Separate Window");
            move.addActionListener(event -> entry.move(!entry.detached())); menu.add(move);
            for (var command : entry.commands()) { var item = new JMenuItem(command.title()); item.putClientProperty("html.disable", true);
                item.setEnabled(command.enabled()); item.setAccelerator(command.shortcut()); item.addActionListener(event -> command.run().run()); menu.add(item); }
        }
        var close = new JMenuItem("Close Tab"); close.addActionListener(event -> closeTab(component)); menu.add(close); return menu;
    }
    public void focusSelected() { var entry = selectedContent(); if (entry != null) entry.focus(); else if (currentTab() != null) currentTab().focusTerminal(); }
    public boolean mayClose() { return contentRegistry == null || contentRegistry.mayClose(() -> contentRegistry.inWindow(id)); }

    WorkspaceRegions regions() { return regions; }
    WindowRail rail() { return rail; }
    boolean railVisible() { return uiState.railVisible(); }

    void setRailVisible(boolean value) {
        uiState.setRailVisible(value);
        uiState.save();
        syncRailVisibility();
        updateActions();
    }

    /** The rail shows only when it has contributed items and the user has not hidden it. */
    void syncRailVisibility() {
        boolean wanted = !rail.empty() && uiState.railVisible();
        regions.setRailVisible(wanted);
        if (rail.isVisible() != wanted) { rail.setVisible(wanted); revalidate(); repaint(); }
    }
    boolean updatingActions() { return workspaceActions.updating(); }
    Action action(ActionId id) { return workspaceActions.action(id); }
    KeyBindings bindings() { return bindings; }
    JToolBar toolbar() { return chrome.toolbar(); }
    WindowStatusBar status() { return chrome.status(); }
    JMenuBar menuBar() { return chrome.menuBar(); }
    JTabbedPane tabStrip() { return tabs; }
    WindowTabs windowTabs() { return windowTabs; }
    private void refreshTabs() { windowTabs.refresh(); }

    public TerminalTab currentTab() { return tabs.getSelectedComponent() instanceof TerminalTab tab ? tab : null; }
    public TerminalPane currentPane() { return currentTab() == null ? null : currentTab().focusedPane(); }
    Path directory() { return currentPane() == null ? Path.of(System.getProperty("user.home")) : currentPane().directory(); }

    public void newTab(Path directory) { openTab(directory); }

    TerminalTab openTab(Path directory) { return openTab(directory, null); }

    /** {@code requestOrNull} makes the tab's first pane a provided session instead of a local shell. */
    TerminalTab openTab(Path directory, SessionRequest requestOrNull) {
        if (closed) return null;
        TerminalTab tab = new TerminalTab(directory, launcher, requestOrNull);
        if (terminals != null) terminals.tabOpened(tab);
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
        return tab;
    }

    private void connectActivity(TerminalTab tab, TerminalPane pane) {
        emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.OPENED));
        report(new TerminalEvent.PaneOpened(tab.id(), pane.id()));
        pane.onCommandStarted = command -> {
            long startedAt = System.nanoTime();
            emit(new WorkspaceActivity.Started(pane.id(), command, () -> System.nanoTime() - startedAt,
                () -> { selectTab(tab); tab.focus(pane); pane.focusTerminal(); },
                pane.watched() && isActiveAndOpen() && tab == currentTab()));
            report(new TerminalEvent.CommandStarted(pane.id(), command));
        };
        pane.onTitleChanged = title -> {
            emit(new WorkspaceActivity.TitleChanged(pane.id(), title));
            report(new TerminalEvent.TitleChanged(pane.id(), title));
        };
        pane.onClosed = () -> {
            emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.CLOSED));
            report(new TerminalEvent.PaneClosed(tab.id(), pane.id()));
        };
        pane.onPaneFocused = () -> {
            emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.FOCUSED));
            report(new TerminalEvent.PaneFocused(tab.id(), pane.id()));
        };
        pane.onPaneBlurred = () -> emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.BLURRED));
        pane.onCommandFinished = (command, exitStatus, duration, workingDirectory, remote) -> {
            emit(new WorkspaceActivity.Finished(pane.id(), command, exitStatus, duration,
                new WorkspaceActivity.Origin(anyWindowActive.getAsBoolean(), isActiveAndOpen(),
                    tab == currentTab(), pane.view() != null && pane.view().isFocusOwner()),
                () -> { selectTab(tab); tab.focus(pane); pane.focusTerminal(); }));
            report(new TerminalEvent.CommandFinished(pane.id(), command, exitStatus, duration, workingDirectory, remote));
        };
        pane.onDirectoryChanged = (directory, remote) -> report(new TerminalEvent.DirectoryChanged(pane.id(), directory, remote));
        pane.onBell = () -> report(new TerminalEvent.Bell(tab.id(), pane.id()));
        pane.onStarted = () -> report(new TerminalEvent.SessionStarted(pane.id()));
        pane.onExited = status -> report(new TerminalEvent.SessionExited(pane.id(), status));
        pane.onConnecting = () -> report(new TerminalEvent.SessionConnecting(pane.id()));
    }

    private void report(TerminalEvent event) { if (terminals != null) terminals.publish(event); }

    private void configurePane(TerminalTab tab, TerminalPane pane) {
        pane.allowLaunchFocus = () -> commandPalette == null || !commandPalette.isOpen();
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

    void selectTab(Component tab) {
        if (tabs.indexOfComponent(tab) >= 0) { tabs.setSelectedComponent(tab); focusSelected(); }
    }

    void reorderTab(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= tabs.getTabCount() || to >= tabs.getTabCount()) return;
        Component selected = tabs.getSelectedComponent();
        Component tab = tabs.getComponentAt(from);
        String title = tabTitle(tab);
        rearranging = true;
        try {
            tabs.removeTabAt(from); tabs.insertTab(title, null, tab, null, to);
            tabs.setSelectedComponent(selected);
        } finally { rearranging = false; }
        update();
    }

    public void closeTab(Component tab) {
        var entry = hosted.get(tab);
        if (entry != null) { entry.requestClose(); return; }
        if (!(tab instanceof TerminalTab terminal)) return;
        int index = tabs.indexOfComponent(tab);
        if (index < 0) return;
        Runnable change = () -> {
            terminal.close();
            if (terminals != null) terminals.tabClosed(terminal);
            tabs.removeTabAt(index); update();
        };
        if (terminals != null) terminals.atomically(change); else change.run();
        if (tabs.getTabCount() == 0 && !closed) onEmpty.run();
    }

    boolean dispatchShortcut(KeyStroke stroke, Component source) {
        if (dev.jasper.app.platform.WindowInput.blocked(source == null ? this : source)) return true;
        if (paletteKeys.dispatchShortcut(stroke, source == null ? this : source)) return true;
        var document = selectedContent();
        // Text controls retain their native selection/clipboard behavior (including find fields).
        if (source instanceof JTextComponent && document != null && document.commands().stream().anyMatch(command ->
            stroke.equals(command.shortcut()) && (command.id().equals("copy") || command.id().equals("paste")))) return false;
        if (document != null) for (var command : document.commands()) if (stroke.equals(command.shortcut())) {
            if (command.enabled()) command.run().run(); return true;
        }
        Optional<String> found = bindings.idFor(stroke);
        if (found.isEmpty()) return false;
        Optional<ActionId> builtIn = ActionId.forId(found.get());
        if (builtIn.isEmpty()) {
            // A recognized contributed shortcut is an app key, never terminal input, even while disabled.
            if (contributed != null) contributed.invoke(found.get());
            return true;
        }
        ActionId id = builtIn.get();
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
            Component tab = tabs.getComponentAt(i);
            tabs.setTitleAt(i, tabTitle(tab));
            if (tab instanceof TerminalTab terminal) terminal.setActive(active && tab == currentTab());
            else { var entry = hosted.get(tab); if (entry != null) entry.active(tab == selectedComponent()); }
        }
        TerminalPane pane = currentPane();
        String size = pane == null || pane.session() == null ? "Starting terminal" :
            pane.session().columns() + " \u00d7 " + pane.session().rows();
        chrome.status().setMetadata(pane == null ? null : pane.snapshot(), pane == null ? "" : size, pane != null && pane.running());
        chrome.status().setContent(selectedContent());
        onTitle.accept(selectedComponent() == null ? "Jasper" : tabTitle(selectedComponent()));
        syncContentBindings();
        updateActions(); refreshTabs(); onMinimumSizeChanged.run();
        if (terminals != null) terminals.refresh();
    }

    ResolvedTheme theme() { return themes.current(); }

    /** Applies {@code theme} to every window, as the saved configuration applies its theme. */
    void selectTheme(Theme theme) {
        if (closed) return;
        try { themes.configure(theme); }
        catch (ThemeController.InstallationFailure failure) { onError.accept(failure.getMessage()); }
        finally { chrome.refreshTheme(); chrome.status().refreshTheme(); }
    }

    TerminalColors terminalColors() { return themes.terminalColors(); }

    /** A session-only terminal palette choice shared by every window; the chrome is unchanged. */
    void selectTerminalColors(TerminalColors colors) {
        if (closed) return;
        themes.selectTerminalColors(colors);
        chrome.refreshTheme();
    }

    /** Updates all retained panes without reparenting them; the native boundary hooks in last. */
    void applyTheme(ResolvedTheme theme, boolean updateDelegates) {
        if (closed) return;
        var retained = terminalTabs();
        retained.forEach(TerminalTab::beginThemeUpdate);
        try {
            if (updateDelegates) SwingUtilities.updateComponentTreeUI(bindingRoot == null ? this : bindingRoot);
            if (updateDelegates && (bindingRoot == null || menuBar().getParent() == null)) SwingUtilities.updateComponentTreeUI(menuBar());
            rail.refreshTheme();
            regions.refreshTheme();
            regions.setTerminalBackground(theme.palette().background());
            if (updateDelegates && contributed != null) contributed.refreshTheme();
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

    int railWidth() { return railWidth; }

    int toolbarHeight() { return toolbarHeight; }

    int statusBarHeight() { return statusBarHeight; }

    /** Rejects a size outside its range, naming the setting and its limits. */
    private static void requireSize(String setting, int value, int min, int max) {
        if (value < min || value > max)
            throw new IllegalArgumentException(setting + " must be between " + min + " and " + max + " pixels");
    }

    void setToolbarHeight(int height) {
        requireSize("Toolbar height", height, ConfigSnapshot.MIN_TOOLBAR_HEIGHT, ConfigSnapshot.MAX_TOOLBAR_HEIGHT);
        if (closed || toolbarHeight == height) return;
        toolbarHeight = height;
        chrome.setToolbarHeight(height);
        revalidate(); repaint();
        onMinimumSizeChanged.run();
    }

    void setStatusBarHeight(int height) {
        requireSize("Status bar height", height, ConfigSnapshot.MIN_STATUS_BAR_HEIGHT, ConfigSnapshot.MAX_STATUS_BAR_HEIGHT);
        if (closed || statusBarHeight == height) return;
        statusBarHeight = height;
        chrome.status().setBarHeight(height);
        revalidate(); repaint();
        onMinimumSizeChanged.run();
    }

    void setRailWidth(int width) {
        requireSize("Rail width", width, ConfigSnapshot.MIN_RAIL_WIDTH, ConfigSnapshot.MAX_RAIL_WIDTH);
        if (closed || railWidth == width) return;
        railWidth = width;
        rail.setRailWidth(width);
        revalidate(); repaint();
        onMinimumSizeChanged.run();
    }

    void setTabHeight(int height) {
        requireSize("Tab height", height, MIN_TAB_HEIGHT, MAX_TAB_HEIGHT);
        if (closed || tabHeight == height) return;
        tabHeight = height;
        if (windowTabs != null) { windowTabs.revalidate(); windowTabs.repaint(); }
        onTabHeightChanged.run();
        revalidate(); repaint();
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) { root.revalidate(); root.repaint(); }
        onMinimumSizeChanged.run();
    }

    void setActive(boolean value) {
        if (!value && commandPalette != null) commandPalette.dismiss();
        active = value; if (value && contentRegistry != null) contentRegistry.activated(id); if (windowTabs != null) windowTabs.setActive(value); update();
    }
    void setToolbarMode(ToolbarMode mode) {
        toolbarMode = mode; chrome.setToolbarMode(mode);
        regions.setHeaderVisible(unifiedHeader || mode != ToolbarMode.HIDDEN);
        updateActions(); revalidate(); onMinimumSizeChanged.run();
    }
    void setStatusVisible(boolean visible) {
        chrome.setStatusVisible(visible); regions.setStatusVisible(visible);
        updateActions(); revalidate(); onMinimumSizeChanged.run();
    }

    @Override public void close() {
        if (closed) return;
        paletteKeys.close();
        if (contributed != null) { contributed.close(); contributed = null; }
        commandPalette.close(); windowCommands.close(); commands.close();
        scopes.close();
        closed = true;
        if (contentHost != null) { contentHost.close(); contentHost = null; }
        syncPaletteDispatcher();
        unregisterConfiguration.run(); disconnectConfiguration();
        showConfigDiagnostics = control -> {};
        if (windowTabs != null) windowTabs.close();
        themeRegistration.close();
        Runnable closeTabs = () -> {
            for (TerminalTab tab : terminalTabs()) { tab.close(); if (terminals != null) terminals.tabClosed(tab); }
        };
        if (terminals != null) terminals.atomically(closeTabs); else closeTabs.run();
        tabs.removeAll(); removeRootBindings();
        if (terminals != null) { terminals.close(); terminals = null; }
        workspaceActions.disable();
        refreshTabs();
        onThemeChanged = theme -> {};
        onTabHeightChanged = () -> {};
        confirmTabHeight = control -> JOptionPane.CANCEL_OPTION;
        confirmRailWidth = control -> JOptionPane.CANCEL_OPTION;
        confirmToolbarHeight = control -> JOptionPane.CANCEL_OPTION;
        confirmStatusBarHeight = control -> JOptionPane.CANCEL_OPTION;
        onTitle = title -> {}; onError = message -> {}; onMinimumSizeChanged = () -> {};
        onToggleBuddy = () -> {}; buddyEnabled = () -> false;
        activityListeners.clear();
        anyWindowActive = () -> false;
    }
}
