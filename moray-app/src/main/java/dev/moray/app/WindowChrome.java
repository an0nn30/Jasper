package dev.moray.app;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Window;
import java.awt.Dimension;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/** Menus, toolbar and status all route through the window's shared actions. */
final class WindowChrome {
    private final WindowContent owner;
    private final JToolBar toolbar = new JToolBar();
    private final JLabel status = new JLabel() {
        @Override public Dimension getMinimumSize() {
            return new Dimension(0, super.getMinimumSize().height);
        }
        @Override public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(Math.min(640, size.width), size.height);
        }
    };
    private final JMenuBar menuBar = new JMenuBar();
    private final ButtonGroup toolbarModes = new ButtonGroup();
    private final JCheckBoxMenuItem statusVisible = new JCheckBoxMenuItem("Status Bar", true);

    WindowChrome(WindowContent owner) {
        this.owner = owner;
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        status.setToolTipText("Using built-in defaults. Configuration files are not loaded yet.");
        JMenu file = menu("File", ActionId.NEW_TAB, ActionId.NEW_WINDOW, ActionId.CLOSE_TAB, ActionId.CLOSE_PANE,
            ActionId.OPEN_SETTINGS, ActionId.RELOAD_CONFIG, ActionId.QUIT);
        JMenu edit = menu("Edit", ActionId.COPY, ActionId.PASTE, ActionId.FIND, ActionId.FIND_NEXT,
            ActionId.FIND_PREVIOUS, ActionId.CLEAR_SCROLLBACK);
        JMenu view = menu("View", ActionId.ZOOM_PANE, ActionId.FONT_BIGGER, ActionId.FONT_SMALLER, ActionId.FONT_RESET);
        JMenu pane = menu("Pane", ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.FOCUS_PANE_LEFT,
            ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN,
            ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT);
        JMenu tab = menu("Tab", ActionId.NEXT_TAB, ActionId.PREVIOUS_TAB, ActionId.RENAME_TAB,
            ActionId.SELECT_TAB_1, ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3, ActionId.SELECT_TAB_4,
            ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6, ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9);
        menuBar.add(file); menuBar.add(edit); menuBar.add(view); menuBar.add(pane); menuBar.add(tab);
        JMenu modes = new JMenu("Toolbar");
        for (WindowContent.ToolbarMode mode : WindowContent.ToolbarMode.values()) {
            String label = switch (mode) { case ICONS_AND_LABELS -> "Icons and Labels"; case ICONS -> "Icons Only"; case HIDDEN -> "Hidden"; };
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, mode == WindowContent.ToolbarMode.ICONS_AND_LABELS);
            item.setActionCommand(mode.name()); item.addActionListener(event -> owner.setToolbarMode(mode));
            toolbarModes.add(item); modes.add(item);
        }
        view.addSeparator(); view.add(modes); view.add(statusVisible);
        statusVisible.addActionListener(event -> owner.setStatusVisible(statusVisible.isSelected()));
        JMenu appearance = new JMenu("Appearance");
        ButtonGroup themes = new ButtonGroup();
        for (String name : new String[]{"Light", "Dark"}) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(name,
                UIManager.getLookAndFeel().isNativeLookAndFeel() ? name.equals("Dark") :
                    UIManager.getLookAndFeel() instanceof FlatLightLaf == name.equals("Light"));
            item.addActionListener(event -> {
                if (name.equals("Light")) FlatLightLaf.setup(); else FlatDarkLaf.setup();
                for (Window window : Window.getWindows()) {
                    SwingUtilities.updateComponentTreeUI(window);
                    if (window instanceof JFrame frame && frame.getContentPane() instanceof WindowContent content) {
                        content.update();
                    }
                }
            });
            themes.add(item); appearance.add(item);
        }
        view.add(appearance);
        addButton(ActionId.NEW_TAB, "square-plus"); addButton(ActionId.NEW_WINDOW, "app-window");
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
        addButton(ActionId.ZOOM_PANE, "maximize"); addButton(ActionId.FIND, "search");
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
        JButton button = toolbar.add(owner.action(id));
        button.setIcon(AppIcons.icon(icon)); button.setFocusable(false);
        button.setVerticalTextPosition(SwingConstants.BOTTOM); button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.getAccessibleContext().setAccessibleName(id.label());
        if (button.getToolTipText() == null) button.setToolTipText(id.label());
        button.putClientProperty("label", id == ActionId.SPLIT_RIGHT ? "Split" : id.label());
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
    void setStatusVisible(boolean visible) { status.setVisible(visible); statusVisible.setSelected(visible); }
    JToolBar toolbar() { return toolbar; }
    JLabel status() { return status; }
    JMenuBar menuBar() { return menuBar; }
}
