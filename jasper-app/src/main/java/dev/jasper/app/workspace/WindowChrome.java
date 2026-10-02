package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuSection;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.ToolbarMode;

import java.awt.*;
import com.formdev.flatlaf.util.UIScale;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/** Menus, toolbar and status all route through the window's shared actions. */
final class WindowChrome {
    private final WindowContent owner;
    private final WindowToolbar toolbar;
    private final TitleSearchField search;
    private final WindowHeader header;
    private final WindowStatusBar status = new WindowStatusBar();
    private final CollapsingMenuBar menuBar = new CollapsingMenuBar();
    private final JMenu uiThemes = new JMenu("UI Theme");
    private final JMenu workspaceThemes = new JMenu("Workspace Theme");
    private dev.jasper.app.appearance.ThemeCatalog.Snapshot displayedThemes;
    private String displayedUi, displayedWorkspace;
    private final MenuListener themeMenuListener = new MenuListener() {
        @Override public void menuSelected(MenuEvent event) { rebuildThemeMenus(); }
        @Override public void menuDeselected(MenuEvent event) {}
        @Override public void menuCanceled(MenuEvent event) {}
    };
    private final ButtonGroup toolbarModes = new ButtonGroup();
    private final JCheckBoxMenuItem statusVisible = new MenuBarItems.CheckBox("Status Bar", true);
    private final JCheckBoxMenuItem buddyVisible = new MenuBarItems.CheckBox("Show Jasper", false);
    private final JCheckBoxMenuItem railVisible = new MenuBarItems.CheckBox("Rail", true);
    private final java.util.EnumMap<MenuTarget.Slot, JMenu> standardMenus = new java.util.EnumMap<>(MenuTarget.Slot.class);
    private final List<Component> contributedToolbar = new ArrayList<>();
    private final Map<JMenu, List<Component>> contributedSections = new LinkedHashMap<>();
    private final List<JMenu> contributedMenus = new ArrayList<>();
    private final ToolbarSeparator pluginSeparator = new ToolbarSeparator();
    private WindowContributions contributions;
    private boolean renderingMenus;
    private boolean menusDirty;

    WindowChrome(WindowContent owner) {
        this.owner = owner;
        toolbar = new WindowToolbar(owner::updateActions);
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder());
        search = new TitleSearchField(() -> owner.action(ActionId.COMMAND_PALETTE).actionPerformed(
            new ActionEvent(owner, ActionEvent.ACTION_PERFORMED, "titleSearch")),
            owner::paletteShortcut);
        header = new WindowHeader(search, toolbar);
        header.setOpaque(true);
        // In the Windows and Linux title bar the toolbar's blank space drags the window; its buttons stay clickable.
        toolbar.putClientProperty(com.formdev.flatlaf.FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION,
            (java.util.function.Function<Point, Boolean>) point -> null);
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
        menuBar.add(file); menuBar.add(edit); menuBar.add(view); menuBar.add(pane); menuBar.add(tab);
        standardMenus.put(MenuTarget.Slot.FILE, file); standardMenus.put(MenuTarget.Slot.EDIT, edit);
        standardMenus.put(MenuTarget.Slot.VIEW, view); standardMenus.put(MenuTarget.Slot.PANE, pane);
        standardMenus.put(MenuTarget.Slot.TAB, tab);
        JMenu modes = new JMenu("Toolbar");
        for (ToolbarMode mode : ToolbarMode.values()) {
            JRadioButtonMenuItem item = new MenuBarItems.Radio(owner.windowCommands().view("view.toolbar." + mode.name().toLowerCase(java.util.Locale.ROOT)));
            item.setActionCommand(mode.name());
            toolbarModes.add(item); modes.add(item);
        }
        view.addSeparator(); view.add(modes); view.add(statusVisible); view.add(railVisible); view.add(buddyVisible);
        statusVisible.setAction(owner.windowCommands().view("view.status_bar"));
        buddyVisible.setAction(owner.windowCommands().view("view.buddy"));
        railVisible.setAction(owner.windowCommands().view("view.rail"));
        view.add(uiThemes); view.add(workspaceThemes);
        view.add(new MenuBarItems.Item(owner.windowCommands().view("view.themes")));
        view.addMenuListener(themeMenuListener);
        uiThemes.addMenuListener(themeMenuListener); workspaceThemes.addMenuListener(themeMenuListener);
        view.add(new MenuBarItems.Item(owner.windowCommands().view("view.window_settings")));
        pluginSeparator.setName("pluginSeparator");
        buildToolbar();
        owner.action(ActionId.COMMAND_PALETTE).addPropertyChangeListener(event -> {
            if (Action.ACCELERATOR_KEY.equals(event.getPropertyName())) search.repaint();
        });
    }

    /** IntelliJ grouping: tab and window, pane, plugin items, then Find and Settings, all packed right. */
    private void buildToolbar() {
        addButton(ActionId.NEW_TAB, "square-plus"); addButton(ActionId.NEW_WINDOW, "app-window");
        toolbar.add(new ToolbarSeparator());
        addSplitButton();
        addButton(ActionId.ZOOM_PANE, "maximize");
        pluginSeparator.setVisible(false);
        toolbar.add(pluginSeparator);
        toolbar.add(new ToolbarSeparator());
        addButton(ActionId.FIND, "search");
        addButton(ActionId.OPEN_SETTINGS, "settings");
        toolbar.finish();
    }

    private void addSplitButton() {
        JButton split = addButton(ActionId.SPLIT_RIGHT, "columns-2");
        split.setAction(null); split.setText("Split"); split.setIcon(AppIcons.icon("columns-2"));
        split.setToolTipText("Split pane right or down"); split.getAccessibleContext().setAccessibleName("Split pane");
        split.putClientProperty("keepLabel", true);
        WindowToolbar.entry(split, 90, () -> {
            var menu = new JMenu("Split");
            menu.add(WindowToolbar.menuItem(owner.action(ActionId.SPLIT_RIGHT)));
            menu.add(WindowToolbar.menuItem(owner.action(ActionId.SPLIT_DOWN)));
            return menu;
        });
        split.addActionListener(event -> {
            owner.updateActions(); JPopupMenu popup = new JPopupMenu();
            popup.add(owner.action(ActionId.SPLIT_RIGHT)); popup.add(owner.action(ActionId.SPLIT_DOWN));
            popup.show(split, 0, split.getHeight());
        });
        owner.action(ActionId.SPLIT_RIGHT).addPropertyChangeListener(event -> {
            if (event.getPropertyName().equals("enabled")) split.setEnabled(owner.action(ActionId.SPLIT_RIGHT).isEnabled());
        });
    }

    void connect(WindowContributions source) {
        contributions = source;
        renderContributedToolbar();
        renderContributedMenus();
    }

    private ReferenceButton contributedButton(Action action, String label, Icon icon) {
        ReferenceButton button = new ReferenceButton(action, null);
        button.setText(owner.toolbarMode() == ToolbarMode.ICONS ? null : label);
        button.putClientProperty("label", label);
        button.setIcon(icon != null ? icon : AppIcons.icon("command"));
        button.setFocusable(false);
        button.setBorder(BorderFactory.createEmptyBorder()); button.setContentAreaFilled(false);
        button.setIconTextGap(UIScale.scale(4));
        button.getAccessibleContext().setAccessibleName(label);
        button.setToolTipText(label);
        button.setFont(button.chromeFont());
        if (action != null) WindowToolbar.entry(button, 30, () -> WindowToolbar.menuItem(action));
        return button;
    }

    /** Rebuilds the plugin section after the built-in controls. */
    void renderContributedToolbar() {
        contributedToolbar.forEach(toolbar::remove);
        contributedToolbar.clear();
        if (contributions != null) {
            for (ToolbarEntry entry : contributions.model().toolbar()) {
                switch (entry) {
                    case ToolbarEntry.Button placed -> {
                        Action action = contributions.action(placed.actionId());
                        if (action != null) contributedToolbar.add(contributedButton(action,
                            (String) action.getValue(Action.NAME), (Icon) action.getValue(Command.ICON)));
                    }
                    case ToolbarEntry.Dropdown dropdown -> {
                        List<Action> live = new ArrayList<>();
                        for (String id : dropdown.actionIds()) if (contributions.action(id) != null) live.add(contributions.action(id));
                        if (live.isEmpty()) break;
                        ReferenceButton button = contributedButton(null, dropdown.title(), dropdown.icon());
                        button.chevron = true;
                        button.putClientProperty("keepLabel", true);
                        WindowToolbar.entry(button, 80, () -> {
                            var menu = new JMenu(dropdown.title());
                            menu.putClientProperty("html.disable", true);
                            live.forEach(action -> menu.add(WindowToolbar.menuItem(action)));
                            return menu;
                        });
                        button.addActionListener(event -> {
                            owner.updateActions();
                            JPopupMenu popup = new JPopupMenu();
                            live.forEach(popup::add);
                            popup.show(button, 0, button.getHeight());
                        });
                        contributedToolbar.add(button);
                    }
                }
            }
            int index = toolbar.getComponentIndex(pluginSeparator) + 1;
            for (Component control : contributedToolbar) toolbar.add(control, index++);
        }
        pluginSeparator.setVisible(!contributedToolbar.isEmpty());
        toolbar.revalidate(); toolbar.repaint();
    }

    /** Builds contributed entries; {@code item} makes menu-bar items or ordinary popup items. */
    private List<Component> build(List<MenuEntry> entries, java.util.function.Function<Action, JMenuItem> item) {
        List<Component> built = new ArrayList<>();
        for (MenuEntry entry : entries) {
            switch (entry) {
                case MenuEntry.Item placed -> {
                    Action action = contributions.action(placed.actionId());
                    if (action != null) built.add(item.apply(action));
                }
                case MenuEntry.Separator separator -> built.add(new JPopupMenu.Separator());
                case MenuEntry.Submenu submenu -> {
                    List<Component> children = build(submenu.entries(), item);
                    if (children.stream().noneMatch(child -> child instanceof JMenuItem)) break;
                    JMenu menu = new JMenu(submenu.title());
                    children.forEach(menu::add);
                    built.add(menu);
                }
            }
        }
        // A section reduced to separators by vanished actions renders as nothing.
        return built.stream().anyMatch(child -> child instanceof JMenuItem) ? built : List.of();
    }

    void renderContributedMenus() {
        menusDirty = true;
        if (renderingMenus) return;
        renderingMenus = true;
        try {
            // macOS/JBR can dispatch EDT callbacks while a native menu peer is changing.
            // Finish this pass before handling a nested refresh, then use the latest model.
            do {
                menusDirty = false;
                rebuildContributedMenus();
            } while (menusDirty);
        } finally {
            renderingMenus = false;
        }
    }

    private void rebuildContributedMenus() {
        contributedSections.forEach((menu, added) -> added.forEach(menu::remove));
        contributedSections.clear();
        contributedMenus.forEach(menuBar::remove);
        contributedMenus.clear();
        if (contributions != null) {
            for (MenuSection section : contributions.model().menus()) {
                List<Component> built = build(section.entries(), MenuBarItems.Item::new);
                if (built.isEmpty()) continue;
                switch (section.target().type()) {
                    case STANDARD -> {
                        JMenu menu = standardMenus.get(MenuTarget.Slot.valueOf(section.target().key()));
                        List<Component> added = contributedSections.computeIfAbsent(menu, key -> new ArrayList<>());
                        var separator = new JPopupMenu.Separator();
                        menu.add(separator); added.add(separator);
                        for (Component child : built) { menu.add(child); added.add(child); }
                    }
                    case TOP_LEVEL -> {
                        JMenu menu = observe(new JMenu(section.target().title()));
                        built.forEach(menu::add);
                        menuBar.add(menu); contributedMenus.add(menu);
                    }
                    case CONTEXT -> { }
                }
            }
        }
        menuBar.revalidate(); menuBar.repaint();
    }

    private JMenu menu(String label, ActionId... ids) {
        JMenu menu = new JMenu(label);
        for (ActionId id : ids) menu.add(new MenuBarItems.Item(owner.action(id)));
        observe(menu);
        return menu;
    }

    private JMenu observe(JMenu menu) {
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
            case RELOAD_CONFIG -> "Reload config"; case QUIT -> "Exit"; default -> id.label();
        };
        ReferenceButton button = new ReferenceButton(owner.action(id), id);
        button.setText(label); button.putClientProperty("label", label);
        button.setIcon(AppIcons.icon(icon)); button.setFocusable(false);
        button.setBorder(BorderFactory.createEmptyBorder()); button.setContentAreaFilled(false);
        button.setIconTextGap(UIScale.scale(4));
        button.getAccessibleContext().setAccessibleName(id.label());
        button.setToolTipText(id.label());
        boolean labelled = id == ActionId.SPLIT_RIGHT;
        button.putClientProperty("keepLabel", labelled);
        button.setHideActionText(!labelled);
        if (!labelled) button.setText(null);
        int priority = switch (id) {
            case FIND, OPEN_SETTINGS -> 100;
            case NEW_TAB, SPLIT_RIGHT -> 90;
            default -> 10;
        };
        WindowToolbar.entry(button, priority, () -> WindowToolbar.menuItem(owner.action(id)));
        toolbar.add(button);
        return button;
    }

    /** An IntelliJ toolbar button: transparent at rest, a rounded fill on hover or press, a small arrow for menus. */
    private static final class ReferenceButton extends JButton {
        private final ActionId id;

        private boolean chevron;
        ReferenceButton(Action action, ActionId id) { super(action); this.id = id; setRolloverEnabled(true); }
        private boolean labels() { return getText() != null; }
        private boolean menu() { return id == ActionId.SPLIT_RIGHT || chevron; }

        /** The tooltip names the action and, while one is bound, its live shortcut. */
        @Override public String getToolTipText() {
            String base = super.getToolTipText();
            if (base == null || getAction() == null) return base;
            Object value = getAction().getValue(Action.ACCELERATOR_KEY);
            String shortcut = value instanceof KeyStroke stroke ? shortcutText(stroke) : "";
            return shortcut.isEmpty() ? base : base + " (" + shortcut + ")";
        }

        static String shortcutText(KeyStroke stroke) {
            int modifiers = stroke.getModifiers();
            String key = KeyEvent.getKeyText(stroke.getKeyCode());
            if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
                String prefix = "";
                if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) prefix += "\u2303";
                if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) prefix += "\u2325";
                if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) prefix += "\u21e7";
                return prefix + "\u2318" + key;
            }
            String prefix = KeyEvent.getModifiersExText(modifiers &
                (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
            return prefix.isEmpty() ? key : prefix + "+" + key;
        }

        private Font chromeFont() {
            Font font = UIManager.getFont("Label.font");
            return font.deriveFont(Font.PLAIN, font.getSize2D() - UIScale.scale(.5f));
        }

        /** The toolbar's button height; icon-only buttons are square. */
        private int side() {
            return getParent() instanceof WindowToolbar bar ? bar.buttonSide() : WindowToolbar.buttonSide(ConfigSnapshot.DEFAULT_TOOLBAR_HEIGHT);
        }

        @Override public Dimension getPreferredSize() {
            int side = side();
            // A labelled button keeps its 4 px padding however tall the toolbar is.
            int width = labels() ? UIScale.scale(24) + UIScale.scale(4) + getFontMetrics(chromeFont()).stringWidth(getText()) : side;
            if (menu()) width += UIScale.scale(10);
            return new Dimension(width, side);
        }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                ButtonModel model = getModel();
                if (isEnabled() && (model.isRollover() || model.isPressed() || model.isSelected())) {
                    g.setColor(UIManager.getColor(model.isPressed() || model.isSelected()
                        ? "Button.toolbar.pressedBackground" : "Button.toolbar.hoverBackground"));
                    int arc = UIScale.scale(6);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                }
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.38f));
                Icon icon = getIcon();
                int x = labels() ? UIScale.scale(4)
                    : menu() ? Math.max(UIScale.scale(4), (getWidth() - UIScale.scale(10) - icon.getIconWidth()) / 2)
                    : (getWidth() - icon.getIconWidth()) / 2;
                icon.paintIcon(this, g, x, (getHeight() - icon.getIconHeight()) / 2);
                x += icon.getIconWidth();
                if (labels()) {
                    x += UIScale.scale(4);
                    g.setFont(chromeFont()); g.setColor(UIManager.getColor("Jasper.chromeForeground"));
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(getText(), x, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    x += fm.stringWidth(getText());
                }
                if (menu()) {
                    x += UIScale.scale(3);
                    Icon arrow = AppIcons.dropdown();
                    arrow.paintIcon(this, g, x, (getHeight() - arrow.getIconHeight()) / 2);
                }
            } finally { g.dispose(); }
        }
    }

    private static final class ToolbarSeparator extends JSeparator {
        ToolbarSeparator() { super(SwingConstants.VERTICAL); }
        @Override protected void paintComponent(Graphics g) {
            g.setColor(UIManager.getColor("Separator.foreground"));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    JPopupMenu contextMenu() {
        owner.updateActions();
        JPopupMenu menu = new JPopupMenu();
        for (ActionId id : new ActionId[]{ActionId.COPY, ActionId.PASTE, ActionId.FIND, ActionId.SPLIT_RIGHT,
            ActionId.SPLIT_DOWN, ActionId.ZOOM_PANE, ActionId.CLOSE_PANE}) menu.add(owner.action(id));
        if (contributions != null) {
            for (MenuSection section : contributions.model().menus()) {
                if (section.target().type() != MenuTarget.Type.CONTEXT) continue;
                List<Component> built = build(section.entries(), JMenuItem::new);
                if (built.isEmpty()) continue;
                menu.addSeparator();
                built.forEach(menu::add);
            }
        }
        return menu;
    }

    void clearThemeMenus() { uiThemes.removeAll(); workspaceThemes.removeAll(); displayedThemes = null; }
    void closeThemeMenus() {
        clearThemeMenus(); uiThemes.removeMenuListener(themeMenuListener); workspaceThemes.removeMenuListener(themeMenuListener);
        standardMenus.get(MenuTarget.Slot.VIEW).removeMenuListener(themeMenuListener);
    }
    private void rebuildThemeMenus() {
        var catalog = owner.availableThemes(); String ui = owner.requestedUiTheme(), workspace = owner.requestedWorkspaceTheme();
        if (catalog.equals(displayedThemes) && ui.equals(displayedUi) && workspace.equals(displayedWorkspace)) return;
        displayedThemes = catalog; displayedUi = ui; displayedWorkspace = workspace;
        fillThemes(uiThemes, catalog.names(), ui, false); fillThemes(workspaceThemes, catalog.names(), workspace, true);
    }
    private void fillThemes(JMenu menu, List<String> names, String selected, boolean workspace) {
        menu.removeAll(); var group = new ButtonGroup();
        if (workspace) addTheme(menu, group, "Same as UI theme", "match", selected, true);
        for (int start = 0; start < names.size(); start += 30) {
            int end = Math.min(start + 30, names.size());
            JMenu destination = menu;
            if (names.size() > 30) { destination = new JMenu((start + 1) + "–" + end); menu.add(destination); }
            for (String name : names.subList(start, end)) addTheme(destination, group, name, name, selected, workspace);
        }
    }
    private void addTheme(JMenu menu, ButtonGroup group, String label, String value, String selected, boolean workspace) {
        var item = new JRadioButtonMenuItem(label); item.putClientProperty("html.disable", Boolean.TRUE);
        item.setSelected(value.equalsIgnoreCase(selected)); group.add(item); menu.add(item);
        item.addActionListener(event -> {
            if (!owner.isActiveAndOpen() || owner.commandPalette().isOpen() || dev.jasper.app.platform.WindowInput.blocked(owner)) return;
            if (workspace) owner.chooseWorkspaceTheme(value); else owner.chooseUiTheme(value);
            // An attempted write may be rejected; the next open must rebuild the actual selection.
            displayedThemes = null;
        });
    }
    void setToolbarMode(ToolbarMode mode) {
        toolbar.setVisible(mode != ToolbarMode.HIDDEN);
        toolbar.setLabelsWanted(mode == ToolbarMode.ICONS_AND_LABELS);
        toolbarModes.getElements().asIterator().forEachRemaining(item -> item.setSelected(item.getActionCommand().equals(mode.name())));
    }
    void refreshTheme() {
        header.setBackground(UIManager.getColor("Jasper.titleBackground"));
        search.repaint();
        toolbar.setBackground(UIManager.getColor("Jasper.titleBackground"));
        for (Component child : toolbar.getComponents())
            if (child instanceof ReferenceButton button) button.setFont(button.chromeFont());
        status.refreshTheme();

    }
    void setStatusVisible(boolean visible) { status.setVisible(visible); statusVisible.setSelected(visible); }
    JToolBar toolbar() { return toolbar; }
    WindowHeader header() { return header; }
    TitleSearchField search() { return search; }
    void setToolbarHeight(int height) { toolbar.setBarHeight(height); }
    WindowStatusBar status() { return status; }
    CollapsingMenuBar menuBar() { return menuBar; }
}
