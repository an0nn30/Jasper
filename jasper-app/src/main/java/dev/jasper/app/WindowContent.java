package dev.jasper.app;

import dev.jasper.terminal.TerminalView;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/** Real window contents, independent of native JFrame construction for headless testing. */
final class WindowContent extends JPanel implements AutoCloseable {
    enum ToolbarMode { ICONS_AND_LABELS, ICONS, HIDDEN }
    private final ShellLauncher launcher;
    private final Consumer<Path> newWindow;
    private final Runnable quit;
    private final Runnable onEmpty;
    private final JTabbedPane tabs = new TerminalDeck();
    private final WindowTabs windowTabs;
    private final EnumMap<ActionId, Action> actions = new EnumMap<>(ActionId.class);
    private final CommandRegistry commands = new CommandRegistry();
    private final WindowCommands windowCommands;
    private final WindowCommandPalette commandPalette;
    // EDT-owned roster gives a held sequence priority over every window's new shortcuts,
    // regardless of the order in which KeyboardFocusManager registered their dispatchers.
    private static final java.util.List<WindowContent> PALETTE_KEY_OWNERS = new ArrayList<>();
    private final PaletteKeyRouter paletteKeys;
    private final KeyEventDispatcher paletteDispatcher = this::paletteKeysDispatch;
    private KeyboardFocusManager paletteFocusManager;
    private javax.swing.Timer paletteTailCleanup;
    private boolean updatingActions;
    private ToolbarMode toolbarMode = ToolbarMode.ICONS_AND_LABELS;
    private KeyBindings bindings;
    private ConfigSnapshot configured;
    private float configuredFontSize = TerminalPane.DEFAULT_FONT_SIZE;
    private final WindowChrome chrome;
    private final ThemeController themes;
    private JRootPane bindingRoot;
    Runnable onMinimumSizeChanged = () -> {};
    static final int DEFAULT_TAB_HEIGHT = 38;
    static final int MIN_TAB_HEIGHT = 28;
    static final int MAX_TAB_HEIGHT = 72;
    private int tabHeight = DEFAULT_TAB_HEIGHT;
    private boolean closed;
    private boolean rearranging;
    private boolean active = true;
    Consumer<ResolvedTheme> onThemeChanged = theme -> {};
    private Runnable openSettings, reloadConfiguration;
    private Runnable unregisterConfiguration = () -> {};
    Consumer<JComponent> showConfigDiagnostics = control -> JOptionPane.showMessageDialog(
        this, control, "Configuration", JOptionPane.PLAIN_MESSAGE);
    Consumer<String> onTitle = title -> {};
    Consumer<String> onError = message -> JOptionPane.showMessageDialog(this, message, "Jasper", JOptionPane.ERROR_MESSAGE);

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
        super(new BorderLayout());
        this.bindings = bindings;
        this.themes = themes;
        this.launcher = launcher; this.newWindow = newWindow; this.quit = quit; this.onEmpty = onEmpty;
        for (ActionId id : ActionId.values()) {
            Action action = new AbstractAction(id.label()) {
                @Override public void actionPerformed(ActionEvent event) { invoke(id); }
            };
            bindings.strokeFor(id).ifPresent(stroke -> {
                action.putValue(Action.ACCELERATOR_KEY, stroke);

            });
            actions.put(id, action);
        }
        String unavailable = "Configuration is not connected";
        action(ActionId.OPEN_SETTINGS).putValue(Action.SHORT_DESCRIPTION, unavailable);
        action(ActionId.RELOAD_CONFIG).putValue(Action.SHORT_DESCRIPTION, unavailable);
        windowCommands = new WindowCommands(this, commands);
        chrome = new WindowChrome(this);
        commandPalette = new WindowCommandPalette(this, commands, history, macOs);
        paletteKeys = new PaletteKeyRouter(commandPalette, () -> this.bindings, macOs,
            source -> !closed && active && bindingRoot != null && source != null
                && SwingUtilities.isDescendingFrom(this, bindingRoot)
                && SwingUtilities.isDescendingFrom(source, bindingRoot));
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) syncPaletteDispatcher();
        });
        windowTabs = new WindowTabs(this);
        var north = new JPanel(new BorderLayout());
        north.add(chrome.toolbar(), BorderLayout.CENTER);
        add(north, BorderLayout.NORTH); add(tabs); add(chrome.status(), BorderLayout.SOUTH);
        tabs.addChangeListener(event -> {
            if (!rearranging) {
                if (commandPalette != null) commandPalette.dismiss();
                update();
                if (currentTab() != null) currentTab().focusTerminal();
            }
        });
        themes.register(this);
        newTab(directory);
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
    }

    void connectConfiguration(Runnable settings, Runnable reload, Runnable unregister) {
        if (closed) return;
        openSettings = settings; reloadConfiguration = reload; unregisterConfiguration = unregister;
        action(ActionId.OPEN_SETTINGS).putValue(Action.SHORT_DESCRIPTION, "Open configuration file");
        action(ActionId.RELOAD_CONFIG).putValue(Action.SHORT_DESCRIPTION, "Reload configuration file");
        updateActions();
    }

    void disconnectConfiguration() {
        openSettings = null; reloadConfiguration = null; unregisterConfiguration = () -> {};
        status().onConfigurationDetails = () -> {};
        status().configButton().setEnabled(false);
        updateActions();
    }

    void setConfigurationState(ConfigService.State state) {
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

    void applyConfiguration(ConfigSnapshot next, boolean macOs) {
        if (closed) return;
        ConfigSnapshot previous = configured;
        configured = next;
        if (previous == null || previous.tabHeight() != next.tabHeight()) setTabHeight(next.tabHeight());
        if (previous == null || previous.toolbar() != next.toolbar()) setToolbarMode(next.toolbar());
        if (previous == null || previous.statusBar() != next.statusBar()) setStatusVisible(next.statusBar());
        boolean sizeChanged = previous == null || previous.fontSize() != next.fontSize();
        boolean optionsChanged = previous == null || !previous.font().equals(next.font())
            || liveBehaviorChanged(previous.terminal(), next.terminal());
        boolean dimChanged = previous == null
            || previous.terminal().dimInactivePanes() != next.terminal().dimInactivePanes();
        boolean exitChanged = previous == null || previous.terminal().onExit() != next.terminal().onExit();
        configuredFontSize = next.fontSize();
        if (optionsChanged || dimChanged || exitChanged) {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                for (TerminalPane pane : ((TerminalTab) tabs.getComponentAt(i)).panes()) {
                    if (optionsChanged && pane.view() != null) {
                        float size = sizeChanged ? next.fontSize() : pane.view().fontSize();
                        pane.view().applyOptions(next.viewOptions(size, pane.view().palette()));
                    }
                    if (dimChanged) pane.setConfiguredDim(next.terminal().dimInactivePanes());
                    if (exitChanged) pane.setShellExitBehavior(next.terminal().onExit());
                }
            }
        }
        if (previous == null || !previous.keybindings().equals(next.keybindings())) setBindings(next.bindings(macOs));
    }

    private static boolean liveBehaviorChanged(TerminalConfig previous, TerminalConfig next) {
        return previous.optionAsMeta() != next.optionAsMeta()
            || previous.cursorShape() != next.cursorShape()
            || previous.cursorBlink() != next.cursorBlink()
            || previous.copyOnSelect() != next.copyOnSelect()
            || previous.bell() != next.bell();
    }

    CommandRegistry commands() { return commands; }
    WindowCommandPalette commandPalette() { return commandPalette; }
    WindowChrome chrome() { return chrome; }
    WindowCommands windowCommands() { return windowCommands; }
    ToolbarMode toolbarMode() { return toolbarMode; }
    boolean isActiveAndOpen() { return active && !closed; }
    boolean updatingActions() { return updatingActions; }
    Action action(ActionId id) { return actions.get(id); }
    KeyBindings bindings() { return bindings; }
    JToolBar toolbar() { return chrome.toolbar(); }
    WindowStatusBar status() { return chrome.status(); }
    JMenuBar menuBar() { return chrome.menuBar(); }
    JTabbedPane tabStrip() { return tabs; }
    TerminalTab currentTab() { return (TerminalTab) tabs.getSelectedComponent(); }
    TerminalPane currentPane() { return currentTab() == null ? null : currentTab().focusedPane(); }
    Path directory() { return currentPane() == null ? Path.of(System.getProperty("user.home")) : currentPane().directory(); }

    void newTab(Path directory) {
        if (closed) return;
        TerminalTab tab = new TerminalTab(directory, launcher);
        tab.onChanged = () -> {
            for (TerminalPane pane : tab.panes()) if (pane.view() == null) pane.applyTheme(themes.current().palette());
            update();
        };
        tab.onEmpty = () -> closeTab(tab);
        tab.onError = message -> onError.accept(message);
        tab.configure = pane -> configurePane(tab, pane);
        tab.setBackground(themes.current().palette().background());
        for (TerminalPane pane : tab.panes()) pane.applyTheme(themes.current().palette());
        tabs.addTab(tab.title(), tab);
        tabs.setSelectedComponent(tab); update(); tab.start();
    }

    private void configurePane(TerminalTab tab, TerminalPane pane) {
        pane.allowLaunchFocus = () -> commandPalette == null || !commandPalette.isOpen();
        pane.applyTheme(themes.current().palette());
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

    void closeTab(TerminalTab tab) {
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

    void invoke(ActionId id) {
        if (commandPalette != null && commandPalette.isOpen() && id != ActionId.COMMAND_PALETTE) return;
        updateActions();
        if (!action(id).isEnabled()) return;
        TerminalTab tab = currentTab();
        TerminalPane pane = currentPane();
        TerminalView view = pane == null ? null : pane.view();
        switch (id) {
            case COMMAND_PALETTE -> commandPalette.toggle();
            case NEW_TAB -> newTab(directory());
            case NEW_WINDOW -> newWindow.accept(directory());
            case QUIT -> quit.run();
            case CLOSE_TAB -> closeTab(tab);
            case CLOSE_PANE -> tab.closePane(pane);
            case SPLIT_RIGHT -> tab.split(SplitTree.Axis.RIGHT);
            case SPLIT_DOWN -> tab.split(SplitTree.Axis.DOWN);
            case ZOOM_PANE -> tab.toggleZoom();
            case FOCUS_PANE_LEFT -> tab.navigate(SplitTree.Direction.LEFT);
            case FOCUS_PANE_RIGHT -> tab.navigate(SplitTree.Direction.RIGHT);
            case FOCUS_PANE_UP -> tab.navigate(SplitTree.Direction.UP);
            case FOCUS_PANE_DOWN -> tab.navigate(SplitTree.Direction.DOWN);
            case NEXT_TAB -> selectRelative(1);
            case PREVIOUS_TAB -> selectRelative(-1);
            case SELECT_TAB_1, SELECT_TAB_2, SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5,
                 SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 -> {
                int index = id.ordinal() - ActionId.SELECT_TAB_1.ordinal();
                if (index < tabs.getTabCount()) tabs.setSelectedIndex(index);
            }
            case RENAME_TAB -> {
                String name = JOptionPane.showInputDialog(this, "Tab name (leave blank for automatic):", tab.title());
                if (name != null) tab.rename(name);
            }
            case FIND -> pane.findBar().open();
            case FIND_NEXT -> pane.findBar().next();
            case FIND_PREVIOUS -> pane.findBar().previous();
            case PREVIOUS_PROMPT -> view.scrollToPreviousPrompt();
            case NEXT_PROMPT -> view.scrollToNextPrompt();
            case COPY -> view.copySelection();
            case PASTE -> view.pasteClipboard();
            case CLEAR_SCROLLBACK -> view.clearScrollback();
            case FONT_BIGGER -> view.setFontSize(view.fontSize() + 1);
            case FONT_SMALLER -> view.setFontSize(view.fontSize() - 1);
            case FONT_RESET -> view.setFontSize(configuredFontSize);
            case OPEN_SETTINGS -> openSettings.run();
            case RELOAD_CONFIG -> reloadConfiguration.run();
        }
        update();
    }

    private void selectRelative(int delta) {
        if (tabs.getTabCount() > 0) tabs.setSelectedIndex(Math.floorMod(tabs.getSelectedIndex() + delta, tabs.getTabCount()));
    }

    void updateActions() {
        updatingActions = true;
        try {
            TerminalPane pane = currentPane();
            boolean present = pane != null;
            boolean ready = present && pane.view() != null;
            boolean running = present && pane.running();
            for (ActionId id : ActionId.values()) {
                boolean enabled = !closed && switch (id) {
                    case OPEN_SETTINGS -> openSettings != null;
                    case RELOAD_CONFIG -> reloadConfiguration != null;
                    case COMMAND_PALETTE, NEW_TAB, NEW_WINDOW, QUIT -> true;
                    case SPLIT_RIGHT, SPLIT_DOWN, PASTE -> running;
                    case COPY -> ready && pane.view().hasSelection();
                    case FIND, FIND_NEXT, FIND_PREVIOUS, PREVIOUS_PROMPT, NEXT_PROMPT,
                         CLEAR_SCROLLBACK, FONT_BIGGER, FONT_SMALLER, FONT_RESET -> ready;
                    case SELECT_TAB_1, SELECT_TAB_2, SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5,
                         SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 ->
                        id.ordinal() - ActionId.SELECT_TAB_1.ordinal() < tabs.getTabCount();
                    default -> present;
                };
                action(id).setEnabled(enabled);
            }
            if (windowCommands != null && chrome != null) windowCommands.refresh();
        } finally { updatingActions = false; }
        if (commandPalette != null) commandPalette.refreshIfChanged();
    }

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
            pane == null ? "" : size, pane != null && pane.running());
        onTitle.accept(currentTab() == null ? "Jasper" : currentTab().title());
        updateActions(); windowTabs.refresh(); onMinimumSizeChanged.run();
    }

    ResolvedTheme theme() { return themes.current(); }

    void selectLaf(UiLookAndFeel laf) {
        if (closed) return;
        String warning = themes.selectLaf(laf);
        if (!warning.isEmpty()) onError.accept(warning);
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
            setBackground(UIManager.getColor("Panel.background"));
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
        revalidate(); repaint();
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) { root.revalidate(); root.repaint(); }
        onMinimumSizeChanged.run();
    }

    void setActive(boolean value) {
        if (!value && commandPalette != null) commandPalette.dismiss();
        active = value; update();
    }
    void setToolbarMode(ToolbarMode mode) {
        toolbarMode = mode; chrome.setToolbarMode(mode); updateActions(); revalidate(); onMinimumSizeChanged.run();
    }
    void setStatusVisible(boolean visible) {
        chrome.setStatusVisible(visible); updateActions(); revalidate(); onMinimumSizeChanged.run();
    }

    @Override public void close() {
        if (closed) return;
        paletteKeys.close();
        commandPalette.close(); windowCommands.close(); commands.close();
        closed = true;
        syncPaletteDispatcher();
        unregisterConfiguration.run(); disconnectConfiguration();
        showConfigDiagnostics = control -> {};
        windowTabs.close();
        themes.unregister(this);
        for (int i = 0; i < tabs.getTabCount(); i++) ((TerminalTab) tabs.getComponentAt(i)).close();
        tabs.removeAll(); removeRootBindings();
        actions.values().forEach(action -> action.setEnabled(false));
        windowTabs.refresh();
        onThemeChanged = theme -> {};
        onTitle = title -> {}; onError = message -> {}; onMinimumSizeChanged = () -> {};
    }
}
