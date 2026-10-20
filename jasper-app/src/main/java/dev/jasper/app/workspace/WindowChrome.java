package dev.jasper.app.workspace;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.ChromePainter;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuSection;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.config.ViewMode;

import java.awt.*;
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
    /** This window's header composition (spec 10.3), built once. */
    private final HeaderComposition composition;
    private final WindowToolbar toolbar;
    private final TitleSearchField search;
    private final WindowHeader header;
    private final WindowStatusBar status;
    private final CollapsingMenuBar menuBar = new CollapsingMenuBar();
    private final ButtonGroup toolbarModes = new ButtonGroup();
    private final JCheckBoxMenuItem statusVisible = new MenuBarItems.CheckBox("Status Bar", true);
    private final JCheckBoxMenuItem railVisible = new MenuBarItems.CheckBox("Rail", true);
    private final java.util.EnumMap<MenuTarget.Slot, JMenu> standardMenus = new java.util.EnumMap<>(MenuTarget.Slot.class);
    private final List<Component> contributedToolbar = new ArrayList<>();
    private final Map<JMenu, List<Component>> contributedSections = new LinkedHashMap<>();
    private final List<JMenu> contributedMenus = new ArrayList<>();
    private final ToolbarSeparator pluginSeparator = new ToolbarSeparator();
    /** When a contributed view gives way on a narrow row: after contributed buttons (30), before dropdowns (80). */
    private static final int VIEW_PRIORITY = 40;
    /** This window's built toolbar views by entry; null where the contributor's factory failed. */
    private final java.util.Map<ToolbarEntry.View, ToolbarEntry.Mounted> views = new java.util.IdentityHashMap<>();
    /** Whether this window's open event fired, so that it builds toolbar views. */
    private boolean opened;
    /** Whether a toolbar pass is running, and whether a change during it asked for another. */
    private boolean renderingToolbar;
    private boolean toolbarDirty;
    private WindowContributions contributions;
    private boolean renderingMenus;
    private boolean menusDirty;

    WindowChrome(WindowContent owner) {
        this.owner = owner;
        composition = owner.look().header();
        status = owner.look().statusBar(owner.action(ActionId.COMMAND_PALETTE), owner::paletteOpen);
        toolbar = new WindowToolbar(owner::updateActions);
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder());
        search = new TitleSearchField(() -> owner.action(ActionId.COMMAND_PALETTE).actionPerformed(
            new ActionEvent(owner, ActionEvent.ACTION_PERFORMED, "titleSearch")),
            owner::paletteShortcut, composition);
        header = new WindowHeader(search, toolbar, composition);
        dev.jasper.app.platform.AppIcons.chromeReceiver(header, owner.look().chromeArtwork());
        // With a native title the toolbar row is chrome, not a title-bar zone (spec 10.1).
        if (composition.titleBarZone()) dev.jasper.app.platform.ThemeZone.titleBar(header);
        header.setOpaque(true);
        // In the Windows and Linux title bar the toolbar's blank space drags the window; its buttons stay clickable.
        toolbar.putClientProperty(com.formdev.flatlaf.FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION,
            (java.util.function.Function<Point, Boolean>) point -> null);
        JMenu file = menu("File", ActionId.NEW_TAB, ActionId.NEW_WINDOW, ActionId.OPEN_FILE, ActionId.CLOSE_TAB, ActionId.CLOSE_PANE,
            ActionId.OPEN_SETTINGS, ActionId.RELOAD_CONFIG, ActionId.QUIT);
        JMenu edit = menu("Edit", ActionId.COPY, ActionId.PASTE, ActionId.FIND, ActionId.FIND_NEXT,
            ActionId.FIND_PREVIOUS, ActionId.CLEAR_SCROLLBACK);
        JMenu view = menu("View", ActionId.COMMAND_PALETTE);
        view.addSeparator();
        JMenu viewModes = menu("View Modes");
        var zen = new MenuBarItems.CheckBox("Zen", false);
        zen.setAction(owner.action(ActionId.TOGGLE_ZEN_MODE));
        viewModes.add(zen);
        view.add(viewModes);
        view.add(new MenuBarItems.Item(owner.action(ActionId.ZOOM_PANE)));
        view.addSeparator();
        JMenu visibility = menu("Show/Hide");
        view.add(visibility);
        view.add(menu("Font Size", ActionId.FONT_BIGGER, ActionId.FONT_SMALLER, ActionId.FONT_RESET));
        JMenu pane = paneMenu();
        JMenu tab = tabMenu();
        // The look's layout decides which built-in menus the bar shows from the start (spec 10.2).
        for (JMenu fixed : owner.look().menus().fixed(new MenuBarLayout.BuiltIn(file, edit, view, pane, tab))) menuBar.add(fixed);
        owner.look().menus().style(menuBar);
        standardMenus.put(MenuTarget.Slot.FILE, file); standardMenus.put(MenuTarget.Slot.EDIT, edit);
        standardMenus.put(MenuTarget.Slot.VIEW, view); standardMenus.put(MenuTarget.Slot.PANE, pane);
        standardMenus.put(MenuTarget.Slot.TAB, tab);
        JMenu modes = menu("Toolbar");
        for (ToolbarMode mode : ToolbarMode.values()) {
            JRadioButtonMenuItem item = new MenuBarItems.Radio(owner.windowCommands().view("view.toolbar." + mode.name().toLowerCase(java.util.Locale.ROOT)));
            item.setActionCommand(mode.name());
            toolbarModes.add(item); modes.add(item);
        }
        visibility.add(modes); visibility.add(statusVisible); visibility.add(railVisible);
        statusVisible.setAction(owner.windowCommands().view("view.status_bar"));
        railVisible.setAction(owner.windowCommands().view("view.rail"));
        pluginSeparator.setName("pluginSeparator");
        composition.build(this);
        owner.action(ActionId.COMMAND_PALETTE).addPropertyChangeListener(event -> {
            if (Action.ACCELERATOR_KEY.equals(event.getPropertyName())) search.repaint();
        });
    }

    /** IntelliJ grouping: tab and window, pane, plugin items, then Find and Settings, all packed right. */
    void buildUnifiedToolbar() {
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

    /** A classic toolbar button for {@code id} in {@code into} (spec 10.3): the icon {@code icon}, then its label when shown. */
    ClassicToolbarButton classicButton(WindowToolbar into, ActionId id, String label, String icon, int priority) {
        var button = new ClassicToolbarButton(owner.action(id), label, false, AppIcons.icon(icon));
        WindowToolbar.entry(button, priority, () -> WindowToolbar.menuItem(owner.action(id)));
        into.add(button);
        return button;
    }

    /** The classic "Split ▾": its icon, label and caret (a corner ▼ under icons only) opening Split Right and Split Down. */
    void classicSplit() {
        var split = new ClassicToolbarButton(null, "Split", true, AppIcons.icon("columns-2"));
        split.setToolTipText("Split pane right or down"); split.getAccessibleContext().setAccessibleName("Split pane");
        WindowToolbar.entry(split, 90, () -> {
            var menu = new JMenu("Split");
            menu.add(WindowToolbar.menuItem(owner.action(ActionId.SPLIT_RIGHT)));
            menu.add(WindowToolbar.menuItem(owner.action(ActionId.SPLIT_DOWN)));
            return menu;
        });
        split.addActionListener(event -> {
            owner.updateActions(); JPopupMenu popup = new JPopupMenu();
            popup.add(owner.action(ActionId.SPLIT_RIGHT)); popup.add(owner.action(ActionId.SPLIT_DOWN));
            NativeStyles.installed().menuTree(popup, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
            popup.show(split, 0, split.getHeight());
        });
        owner.action(ActionId.SPLIT_RIGHT).addPropertyChangeListener(event -> {
            if (event.getPropertyName().equals("enabled")) split.setEnabled(owner.action(ActionId.SPLIT_RIGHT).isEnabled());
        });
        toolbar.add(split);
    }

    /** A toolbar rule; a classic one is a 1 px {@code border.window} line. */
    JSeparator separator(boolean classic) { var rule = new ToolbarSeparator(); rule.classic = classic; return rule; }

    /** The group contributed controls join, after its rule. */
    void addPluginGroup(boolean classic) {
        pluginSeparator.classic = classic;
        pluginSeparator.setVisible(false);
        toolbar.add(pluginSeparator);
    }

    WindowToolbar windowToolbar() { return toolbar; }
    HeaderComposition composition() { return composition; }

    /** Today's contributed button, as UnifiedHeader draws it; a dropdown's shows its chevron and keeps its label. */
    JButton referenceButton(Action action, String label, Icon icon, boolean dropdown) {
        ReferenceButton button = contributedButton(action, label, icon);
        if (dropdown) { button.chevron = true; button.putClientProperty("keepLabel", true); }
        return button;
    }

    /** Set once a native title draws above the header: the row is window chrome, not a title-bar zone (spec 10.1). */
    private boolean chromeRow;

    /** Makes the header a row of window chrome under a native title (Rulings 16); its views follow on the next pass. */
    void chromeRow() {
        chromeRow = true;
        dev.jasper.app.platform.ThemeZone.chrome(header);
        renderContributedToolbar();
    }

    /** Whether the header paints from the title bar's theme. */
    boolean headerIsTitleBar() { return composition.titleBarZone() && !chromeRow; }

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

    /** This window's open event fired (its terminals are registered): it builds its toolbar views now. */
    void windowOpened() {
        if (opened) return;
        opened = true;
        renderContributedToolbar();
    }

    private ReferenceButton contributedButton(Action action, String label, Icon icon) {
        ReferenceButton button = new ReferenceButton(action, null);
        button.setText(owner.toolbarMode() == ToolbarMode.ICONS ? null : label);
        button.putClientProperty("label", label);
        button.setIcon(icon != null ? icon : AppIcons.icon("command"));
        button.setFocusable(false);
        NativeStyles.installed().unframed(button);
        button.setIconTextGap(NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING));
        button.getAccessibleContext().setAccessibleName(label);
        button.setToolTipText(label);
        button.setFont(button.chromeFont());
        if (action != null) WindowToolbar.entry(button, 30, () -> WindowToolbar.menuItem(action));
        return button;
    }

    /**
     * Rebuilds the plugin section after the built-in controls. A contributed view is built once, after this window
     * opened, and closed here when its entry goes or the window lets go of its contributions. Until then it stays in the
     * toolbar across passes, moved into place without a remove, so that a control inside it keeps its focus and an open
     * dropdown stays open while other contributions change. A view's factory and its
     * close are contributor code that may change the contributions (register an action, enable one, add a toolbar
     * item), which renders the toolbar again from inside this pass: that nested call only marks the toolbar dirty, and
     * this pass then runs again with the latest model, as {@code renderContributedMenus} does.
     */
    void renderContributedToolbar() {
        toolbarDirty = true;
        if (renderingToolbar) return;
        renderingToolbar = true;
        try {
            do {
                toolbarDirty = false;
                rebuildContributedToolbar();
            } while (toolbarDirty);
        } finally {
            renderingToolbar = false;
        }
    }

    private void rebuildContributedToolbar() {
        // A built view stays a child of the toolbar: Container.remove would call its removeNotify.
        var kept = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Component, Boolean>());
        for (ToolbarEntry.Mounted mounted : views.values()) if (mounted != null) kept.add(mounted.component());
        for (Component control : contributedToolbar) if (!kept.contains(control)) toolbar.remove(control);
        contributedToolbar.clear();
        List<ToolbarEntry> entries = contributions == null ? List.of() : contributions.model().toolbar();
        // Not "live": the Dropdown case below declares its own local of that name.
        var liveViews = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ToolbarEntry.View, Boolean>());
        for (ToolbarEntry entry : entries) if (entry instanceof ToolbarEntry.View view) liveViews.add(view);
        for (var built = views.entrySet().iterator(); built.hasNext(); ) {
            var next = built.next();
            if (liveViews.contains(next.getKey())) continue;
            // Read before the remove: an IdentityHashMap entry is invalid once removed.
            ToolbarEntry.Mounted gone = next.getValue();
            built.remove();
            if (gone != null) { toolbar.remove(gone.component()); gone.close(); }
        }
        if (contributions != null) {
            for (ToolbarEntry entry : entries) {
                switch (entry) {
                    case ToolbarEntry.Button placed -> {
                        Action action = contributions.action(placed.actionId());
                        // The composition draws a contributed button in its own style (spec 10.3).
                        if (action != null) contributedToolbar.add(composition.contributed(this, action,
                            (String) action.getValue(Action.NAME), (Icon) action.getValue(Command.ICON), false));
                    }
                    case ToolbarEntry.Dropdown dropdown -> {
                        List<Action> live = new ArrayList<>();
                        for (String id : dropdown.actionIds()) if (contributions.action(id) != null) live.add(contributions.action(id));
                        if (live.isEmpty()) break;
                        JButton button = composition.contributed(this, null, dropdown.title(), dropdown.icon(), true);
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
                    case ToolbarEntry.View view -> {
                        // Built once per window and only after it opened, so never for the warm-up rehearsal's window.
                        if (!opened) break;
                        if (!views.containsKey(view)) {
                            // Reserve the slot before the contributor's factory runs, so that no pass asks it twice.
                            views.put(view, null);
                            views.put(view, view.factory().apply(owner.id()).orElse(null));
                        }
                        ToolbarEntry.Mounted mounted = views.get(view);
                        if (mounted == null) break;
                        // A kit view paints from the header's theme, as the header's own controls do: the title bar's
                        // in a title-bar zone, the window chrome's in a classic row or under a native title.
                        if (mounted.component() instanceof dev.jasper.ui.swing.KitView kit && !kit.isClosed()) {
                            kit.toolbarStrip(true);
                            kit.classicChromeIcons(owner.look().chromeArtwork() == dev.jasper.ui.icons.ChromeArtwork.CLASSIC);
                            kit.route(headerIsTitleBar() ? dev.jasper.ui.theme.ThemeRoute.TITLE_BAR : dev.jasper.ui.theme.ThemeRoute.CHROME);
                        }
                        WindowToolbar.view(mounted.component(), VIEW_PRIORITY);
                        contributedToolbar.add(mounted.component());
                    }
                }
            }
            int index = toolbar.getComponentIndex(pluginSeparator) + 1;
            // setComponentZOrder moves a kept view without removeNotify; everything else is new to the toolbar.
            for (Component control : contributedToolbar) {
                if (control.getParent() == toolbar) toolbar.setComponentZOrder(control, index++);
                else toolbar.add(control, index++);
            }
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
                    NativeStyles.installed().menu(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
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
        MenuBarLayout layout = owner.look().menus();
        if (contributions != null) {
            var sections = contributions.model().menus();
            // Session, Transfer and Tools hold only plugin entries and, like a plugin's own menu, are built fresh on
            // every pass and added to the bar only while they have any: a stale one would keep an old look and feel,
            // and one JMenu would be re-inserted into the macOS screen menu bar.
            standardMenus.put(MenuTarget.Slot.SESSION, menu("Session"));
            standardMenus.put(MenuTarget.Slot.TRANSFER, menu("Transfer"));
            standardMenus.put(MenuTarget.Slot.TOOLS, menu("Tools"));
            // A layout that gathers Pane and Tab into another menu builds them fresh too.
            layout.fresh(standardMenus, this::paneMenu, this::tabMenu);
            var toolWindows = contributions.model().toolWindowsSection();
            for (MenuSection section : sections) {
                if (section.target().type() != MenuTarget.Type.STANDARD) continue;
                if (!layout.toolWindowsInView() && toolWindows.filter(own -> own == section).isPresent()) continue;
                List<Component> built = build(section.entries(), MenuBarItems.Item::new);
                if (built.isEmpty()) continue;
                JMenu menu = standardMenus.get(MenuTarget.Slot.valueOf(section.target().key()));
                List<Component> added = contributedSections.computeIfAbsent(menu, key -> new ArrayList<>());
                // Session, Transfer and Tools have no items of their own, so a first section there needs no separator.
                if (menu.getMenuComponentCount() > 0) {
                    var separator = new JPopupMenu.Separator();
                    menu.add(separator); added.add(separator);
                }
                for (Component child : built) { menu.add(child); added.add(child); }
            }
            var topLevel = new ArrayList<MenuBarLayout.TopMenu>();
            for (MenuSection section : sections) {
                if (section.target().type() != MenuTarget.Type.TOP_LEVEL) continue;
                List<Component> built = build(section.entries(), MenuBarItems.Item::new);
                if (built.isEmpty()) continue;
                JMenu menu = observe(new JMenu(section.target().title()));
                built.forEach(menu::add);
                topLevel.add(new MenuBarLayout.TopMenu(menu, MenuTarget.HELP.equals(section.target().key())));
            }
            // The bar orders menus as they are added; the layout gives the order (spec 10.2). A layout that keeps the
            // tool windows in View takes no items, so the default look builds none.
            List<JMenuItem> toolItems = layout.toolWindowsInView() ? List.of() : toolWindowItems();
            for (JMenu menu : layout.contributed(new MenuBarLayout.Pass(standardMenus, topLevel, label -> menu(label), toolItems))) {
                menuBar.add(menu); contributedMenus.add(menu);
            }
        }
        menuBar.revalidate(); menuBar.repaint();
    }

    private static final ActionId[] PANE_ITEMS = {ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.FOCUS_PANE_LEFT,
        ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN, ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT};
    private static final ActionId[] TAB_ITEMS = {ActionId.NEXT_TAB, ActionId.PREVIOUS_TAB, ActionId.RENAME_TAB,
        ActionId.SELECT_TAB_1, ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3, ActionId.SELECT_TAB_4,
        ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6, ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9};
    private JMenu paneMenu() { return menu("Pane", PANE_ITEMS); }
    private JMenu tabMenu() { return menu("Tab", TAB_ITEMS); }

    /** One item per tool window whose toggle action this window has, in registration order. */
    private List<JMenuItem> toolWindowItems() {
        var items = new ArrayList<JMenuItem>();
        for (var panel : contributions.model().panels()) {
            Action action = contributions.action(panel.toggleActionId());
            if (action != null) items.add(new MenuBarItems.Item(action));
        }
        return items;
    }

    private JMenu menu(String label, ActionId... ids) {
        JMenu menu = new JMenu(label);
        for (ActionId id : ids) menu.add(new MenuBarItems.Item(owner.action(id)));
        observe(menu);
        return menu;
    }

    private JMenu observe(JMenu menu) {
        NativeStyles.installed().menu(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
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
        NativeStyles.installed().unframed(button);
        button.setIconTextGap(NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING));
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
            return ThemeZone.font(Typography.Role.TOOLBAR_LABEL);
        }

        /** The toolbar's button height; icon-only buttons are square. */
        private int side() {
            return getParent() instanceof WindowToolbar bar ? bar.buttonSide() : WindowToolbar.buttonSide(ConfigSnapshot.DEFAULT_TOOLBAR_HEIGHT);
        }

        @Override public Dimension getPreferredSize() {
            int side = side();
            // A labelled button keeps its padding however tall the toolbar is; icon-only buttons are square.
            return new Dimension(ChromePainter.installed().actionWidth(this, side, labels(), menu()), side);
        }

        @Override protected void paintComponent(Graphics graphics) {
            ChromePainter.installed().action(this, graphics, ThemeZone.theme(this), labels(), menu() ? AppIcons.dropdown() : null);
        }
    }

    private static final class ToolbarSeparator extends JSeparator {
        /** A classic rule (spec 10.3): the toolbar's 1 px border.window line. */
        boolean classic;
        ToolbarSeparator() { super(SwingConstants.VERTICAL); }
        @Override protected void paintComponent(Graphics g) {
            if (!classic) { ChromePainter.installed().rule(this, g, ThemeZone.theme(null)); return; }
            g.setColor(dev.jasper.ui.theme.SurfaceContrast.painted(ThemeZone.theme(this), WindowPairs.RULE));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    JPopupMenu contextMenu() {
        owner.updateActions();
        JPopupMenu menu = new JPopupMenu();
        for (ActionId id : new ActionId[]{ActionId.COPY, ActionId.PASTE, ActionId.FIND, ActionId.CLEAR_TERMINAL,
            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.ZOOM_PANE, ActionId.CLOSE_PANE}) menu.add(owner.action(id));
        if (contributions != null) {
            for (MenuSection section : contributions.model().menus()) {
                if (section.target().type() != MenuTarget.Type.CONTEXT) continue;
                List<Component> built = build(section.entries(), JMenuItem::new);
                if (built.isEmpty()) continue;
                menu.addSeparator();
                built.forEach(menu::add);
            }
        }
        NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        return menu;
    }

    void setToolbarMode(ToolbarMode mode) {
        composition.toolbarMode(header, mode, owner.viewMode() == ViewMode.NORMAL);
        toolbarModes.getElements().asIterator().forEachRemaining(item -> item.setSelected(item.getActionCommand().equals(mode.name())));
    }
    void refreshTheme() {
        owner.look().menus().style(menuBar);
        header.setBackground(ThemeZone.color(header, SemanticColor.SURFACE_APP));
        search.repaint();
        toolbar.setBackground(ThemeZone.color(toolbar, SemanticColor.SURFACE_APP));
        composition.refreshTheme(header);
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
