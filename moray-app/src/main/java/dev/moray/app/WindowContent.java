package dev.moray.app;

import dev.moray.terminal.TerminalView;
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
    private final KeyBindings bindings = KeyBindings.defaults(System.getProperty("os.name").startsWith("Mac"));
    private final WindowChrome chrome;
    private final ThemeController themes;
    private JRootPane bindingRoot;
    Runnable onMinimumSizeChanged = () -> {};
    private boolean closed;
    private boolean rearranging;
    private boolean active = true;
    Consumer<BuiltinTheme> onThemeChanged = theme -> {};
    Consumer<String> onTitle = title -> {};
    Consumer<String> onError = message -> JOptionPane.showMessageDialog(this, message, "Moray", JOptionPane.ERROR_MESSAGE);

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty) {
        this(launcher, directory, newWindow, quit, onEmpty, new ThemeController());
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes) {
        super(new BorderLayout());
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
        String unavailable = "Configuration support is not available yet";
        action(ActionId.OPEN_SETTINGS).putValue(Action.SHORT_DESCRIPTION, unavailable);
        action(ActionId.RELOAD_CONFIG).putValue(Action.SHORT_DESCRIPTION, unavailable);
        chrome = new WindowChrome(this);
        windowTabs = new WindowTabs(this);
        var north = new JPanel(new BorderLayout());
        north.add(windowTabs, BorderLayout.NORTH); north.add(chrome.toolbar(), BorderLayout.CENTER);
        add(north, BorderLayout.NORTH); add(tabs); add(chrome.status(), BorderLayout.SOUTH);
        tabs.addChangeListener(event -> {
            if (!rearranging) { update(); if (currentTab() != null) currentTab().focusTerminal(); }
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
    }

    private void removeRootBindings() {
        if (bindingRoot == null) return;
        for (ActionId id : ActionId.values()) {
            bindings.strokeFor(id).ifPresent(stroke -> bindingRoot.getInputMap(WHEN_IN_FOCUSED_WINDOW).remove(stroke));
            bindingRoot.getActionMap().remove(id.id());
        }
        bindingRoot = null;
    }

    Action action(ActionId id) { return actions.get(id); }
    KeyBindings bindings() { return bindings; }
    JToolBar toolbar() { return chrome.toolbar(); }
    WindowStatusBar status() { return chrome.status(); }
    JMenuBar menuBar() { return chrome.menuBar(); }
    JTabbedPane tabStrip() { return tabs; }
    WindowTabs windowTabs() { return windowTabs; }
    TerminalTab currentTab() { return (TerminalTab) tabs.getSelectedComponent(); }
    TerminalPane currentPane() { return currentTab() == null ? null : currentTab().focusedPane(); }
    Path directory() { return currentPane() == null ? Path.of(System.getProperty("user.home")) : currentPane().directory(); }

    void newTab(Path directory) {
        if (closed) return;
        TerminalTab tab = new TerminalTab(directory, launcher);
        tab.onChanged = this::update;
        tab.onEmpty = () -> closeTab(tab);
        tab.onError = message -> onError.accept(message);
        tab.configure = pane -> configurePane(tab, pane);
        tabs.addTab(tab.title(), tab);
        tabs.setSelectedComponent(tab); update(); tab.start();
    }

    private void configurePane(TerminalTab tab, TerminalPane pane) {
        pane.applyTheme(themes.current());
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
        updateActions();
        if (!action(id).isEnabled()) return;
        TerminalTab tab = currentTab();
        TerminalPane pane = currentPane();
        TerminalView view = pane == null ? null : pane.view();
        switch (id) {
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
            case FONT_RESET -> view.setFontSize(TerminalPane.DEFAULT_FONT_SIZE);
            case OPEN_SETTINGS, RELOAD_CONFIG -> { /* visibly disabled until configuration exists */ }
        }
        update();
    }

    private void selectRelative(int delta) {
        if (tabs.getTabCount() > 0) tabs.setSelectedIndex(Math.floorMod(tabs.getSelectedIndex() + delta, tabs.getTabCount()));
    }

    void updateActions() {
        TerminalPane pane = currentPane();
        boolean present = pane != null;
        boolean ready = present && pane.view() != null;
        boolean running = present && pane.running();
        for (ActionId id : ActionId.values()) {
            boolean enabled = !closed && switch (id) {
                case OPEN_SETTINGS, RELOAD_CONFIG -> false;
                case NEW_TAB, NEW_WINDOW, QUIT -> true;
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
        onTitle.accept(currentTab() == null ? "Moray" : currentTab().title());
        updateActions(); windowTabs.refresh(); onMinimumSizeChanged.run();
    }

    BuiltinTheme theme() { return themes.current(); }

    void selectTheme(BuiltinTheme theme) {
        if (closed) return;
        try { themes.select(theme); }
        catch (IllegalStateException failure) { onError.accept(failure.getMessage()); }
        finally { chrome.refreshTheme(); chrome.status().refreshTheme(); }
    }

    /** Updates all retained panes without reparenting them; the native boundary hooks in last. */
    void applyTheme(BuiltinTheme theme) {
        if (closed) return;
        var retained = new ArrayList<TerminalTab>();
        for (int i = 0; i < tabs.getTabCount(); i++) retained.add((TerminalTab) tabs.getComponentAt(i));
        retained.forEach(TerminalTab::beginThemeUpdate);
        try {
            SwingUtilities.updateComponentTreeUI(bindingRoot == null ? this : bindingRoot);
            if (bindingRoot == null || menuBar().getParent() == null) SwingUtilities.updateComponentTreeUI(menuBar());
            for (TerminalTab tab : retained) {
                for (TerminalPane pane : tab.panes()) {
                    // Zoom detaches sibling panes from the visible component hierarchy.
                    if (!SwingUtilities.isDescendingFrom(pane, this)) SwingUtilities.updateComponentTreeUI(pane);
                    pane.applyTheme(theme);
                }
            }
            chrome.refreshTheme(); chrome.status().refreshTheme(); onThemeChanged.accept(theme); update();
            revalidate(); repaint();
        } finally { retained.forEach(TerminalTab::endThemeUpdate); }
    }

    void setActive(boolean value) { active = value; windowTabs.setActive(value); update(); }
    void setToolbarMode(ToolbarMode mode) { chrome.setToolbarMode(mode); revalidate(); onMinimumSizeChanged.run(); }
    void setStatusVisible(boolean visible) { chrome.setStatusVisible(visible); revalidate(); onMinimumSizeChanged.run(); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        themes.unregister(this);
        for (int i = 0; i < tabs.getTabCount(); i++) ((TerminalTab) tabs.getComponentAt(i)).close();
        tabs.removeAll(); removeRootBindings();
        actions.values().forEach(action -> action.setEnabled(false));
        windowTabs.refresh();
        onThemeChanged = theme -> {};
        onTitle = title -> {}; onError = message -> {}; onMinimumSizeChanged = () -> {};
    }
}
