package dev.jasper.app;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/** Menus, toolbar and status all route through the window's shared actions. */
final class WindowChrome {
    private final WindowContent owner;
    private final JToolBar toolbar = new JToolBar();
    private final WindowStatusBar status = new WindowStatusBar();
    private final JMenuBar menuBar = new JMenuBar();
    private final ButtonGroup toolbarModes = new ButtonGroup();
    private final JCheckBoxMenuItem statusVisible = new JCheckBoxMenuItem("Status Bar", true);

    WindowChrome(WindowContent owner) {
        this.owner = owner;
        toolbar.setFloatable(false);
        JMenu file = menu("File", ActionId.NEW_TAB, ActionId.NEW_WINDOW, ActionId.CLOSE_TAB, ActionId.CLOSE_PANE,
            ActionId.OPEN_SETTINGS, ActionId.RELOAD_CONFIG, ActionId.QUIT);
        JMenu edit = menu("Edit", ActionId.COPY, ActionId.PASTE, ActionId.FIND, ActionId.FIND_NEXT,
            ActionId.FIND_PREVIOUS, ActionId.CLEAR_SCROLLBACK);
        JMenu view = menu("View", ActionId.COMMAND_PALETTE, ActionId.ZOOM_PANE, ActionId.FONT_BIGGER, ActionId.FONT_SMALLER, ActionId.FONT_RESET);
        view.insertSeparator(1);
        JMenu pane = menu("Pane", ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.FOCUS_PANE_LEFT,
            ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN,
            ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT);
        JMenu tab = menu("Tab", ActionId.NEXT_TAB, ActionId.PREVIOUS_TAB, ActionId.RENAME_TAB,
            ActionId.SELECT_TAB_1, ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3, ActionId.SELECT_TAB_4,
            ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6, ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9);
        JMenu tools = menu("Tools", ActionId.VAULT_MANAGER, ActionId.VAULT_LOCK);
        menuBar.add(file); menuBar.add(edit); menuBar.add(view); menuBar.add(pane); menuBar.add(tab); menuBar.add(tools);
        JMenu modes = new JMenu("Toolbar");
        for (WindowContent.ToolbarMode mode : WindowContent.ToolbarMode.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(owner.windowCommands().view("view.toolbar." + mode.name().toLowerCase(java.util.Locale.ROOT)));
            item.setActionCommand(mode.name());
            toolbarModes.add(item); modes.add(item);
        }
        view.addSeparator(); view.add(modes); view.add(statusVisible);
        statusVisible.setAction(owner.windowCommands().view("view.status_bar"));
        addButton(ActionId.NEW_TAB, "square-plus"); addButton(ActionId.NEW_WINDOW, "app-window");
        toolbar.add(new JToolBar.Separator());
        JButton split = addButton(ActionId.SPLIT_RIGHT, "columns-2");
        split.setAction(null); split.setText("Split"); split.setIcon(AppIcons.icon("columns-2"));
        split.setToolTipText("Split pane right or down"); split.getAccessibleContext().setAccessibleName("Split pane");
        split.addActionListener(event -> {
            owner.updateActions(); JPopupMenu popup = new JPopupMenu();
            popup.add(owner.action(ActionId.SPLIT_RIGHT)); popup.add(owner.action(ActionId.SPLIT_DOWN));
            popup.show(split, 0, split.getHeight());
        });
        owner.action(ActionId.SPLIT_RIGHT).addPropertyChangeListener(event -> {
            if (event.getPropertyName().equals("enabled")) split.setEnabled(owner.action(ActionId.SPLIT_RIGHT).isEnabled());
        });
        toolbar.add(Box.createHorizontalStrut(4));
        addButton(ActionId.ZOOM_PANE, "maximize"); addButton(ActionId.FIND, "search");
        toolbar.add(new JToolBar.Separator());
        toolbar.add(Box.createHorizontalGlue());
        addButton(ActionId.OPEN_SETTINGS, "settings"); addButton(ActionId.RELOAD_CONFIG, "refresh");
    }

    private JMenu menu(String label, ActionId... ids) {
        JMenu menu = new JMenu(label);
        for (ActionId id : ids) menu.add(owner.action(id));
        menu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent event) { owner.updateActions(); }
            @Override public void menuDeselected(MenuEvent event) {}
            @Override public void menuCanceled(MenuEvent event) {}
        });
        return menu;
    }

    private JButton addButton(ActionId id, String icon) {
        String label = switch (id) {
            case NEW_TAB -> "New tab"; case NEW_WINDOW -> "New window";
            case SPLIT_RIGHT -> "Split"; case ZOOM_PANE -> "Zoom pane";
            case RELOAD_CONFIG -> "Reload config"; default -> id.label();
        };
        JButton button = new JButton(owner.action(id));
        button.setText(label); button.putClientProperty("label", label);
        button.setIcon(AppIcons.icon(icon)); button.setFocusable(false);
        button.setIconTextGap(4);
        button.getAccessibleContext().setAccessibleName(id.label());
        if (button.getToolTipText() == null) button.setToolTipText(id.label());
        toolbar.add(button);
        return button;
    }

    JPopupMenu contextMenu() {
        owner.updateActions();
        JPopupMenu menu = new JPopupMenu();
        for (ActionId id : new ActionId[]{ActionId.COPY, ActionId.PASTE, ActionId.FIND, ActionId.SPLIT_RIGHT,
            ActionId.SPLIT_DOWN, ActionId.ZOOM_PANE, ActionId.CLOSE_PANE}) menu.add(owner.action(id));
        return menu;
    }

    void setToolbarMode(WindowContent.ToolbarMode mode) {
        toolbar.setVisible(mode != WindowContent.ToolbarMode.HIDDEN);
        for (var component : toolbar.getComponents()) if (component instanceof JButton button) {
            button.setText(mode == WindowContent.ToolbarMode.ICONS ? null : (String) button.getClientProperty("label"));
        }
        toolbarModes.getElements().asIterator().forEachRemaining(item -> item.setSelected(item.getActionCommand().equals(mode.name())));
    }
    void refreshTheme() {
        toolbar.revalidate(); toolbar.repaint(); status.refreshTheme();
    }
    void setStatusVisible(boolean visible) { status.setVisible(visible); statusVisible.setSelected(visible); }
    JToolBar toolbar() { return toolbar; }
    WindowStatusBar status() { return status; }
    JMenuBar menuBar() { return menuBar; }
}
