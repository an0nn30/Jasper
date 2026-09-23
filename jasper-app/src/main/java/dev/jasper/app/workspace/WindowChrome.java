package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuSection;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.platform.AppIcons;
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
    private final JToolBar toolbar;
    private final WindowStatusBar status = new WindowStatusBar();
    private final JMenuBar menuBar = new JMenuBar();
    private final java.util.EnumMap<Appearance, JRadioButtonMenuItem> themeItems = new java.util.EnumMap<>(Appearance.class);
    private final ButtonGroup toolbarModes = new ButtonGroup();
    private final JCheckBoxMenuItem statusVisible = new JCheckBoxMenuItem("Status Bar", true);
    private final JCheckBoxMenuItem buddyVisible = new JCheckBoxMenuItem("Show Jasper", false);
    private final JCheckBoxMenuItem railVisible = new JCheckBoxMenuItem("Rail", true);
    private final Component toolbarGlue = Box.createHorizontalGlue();
    private final java.util.EnumMap<MenuTarget.Slot, JMenu> standardMenus = new java.util.EnumMap<>(MenuTarget.Slot.class);
    private final List<Component> contributedToolbar = new ArrayList<>();
    private final Map<JMenu, List<Component>> contributedSections = new LinkedHashMap<>();
    private final List<JMenu> contributedMenus = new ArrayList<>();
    private WindowContributions contributions;

    WindowChrome(WindowContent owner) {
        this.owner = owner;
        toolbar = owner.retro() ? new RetroToolbar() : new ReferenceToolbar();
        toolbar.setFloatable(false);
        if (!owner.retro()) toolbar.setBorder(BorderFactory.createEmptyBorder());
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
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(owner.windowCommands().view("view.toolbar." + mode.name().toLowerCase(java.util.Locale.ROOT)));
            item.setActionCommand(mode.name());
            toolbarModes.add(item); modes.add(item);
        }
        view.addSeparator(); view.add(modes); view.add(statusVisible); view.add(railVisible); view.add(buddyVisible);
        statusVisible.setAction(owner.windowCommands().view("view.status_bar"));
        buddyVisible.setAction(owner.windowCommands().view("view.buddy"));
        railVisible.setAction(owner.windowCommands().view("view.rail"));
        JMenu appearance = new JMenu("Appearance");
        ButtonGroup themes = new ButtonGroup();
        for (Appearance theme : new Appearance[]{Appearance.LIGHT, Appearance.DARK}) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                owner.windowCommands().view("view.appearance." + theme.name().toLowerCase(java.util.Locale.ROOT)));
            themeItems.put(theme, item); themes.add(item); appearance.add(item);
        }
        appearance.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent event) { refreshTheme(); }
            @Override public void menuDeselected(MenuEvent event) {}
            @Override public void menuCanceled(MenuEvent event) {}
        });
        if (owner.retro()) {
            var note = new JMenuItem("Retro uses light Metal; change style in Settings and restart.");
            note.setEnabled(false); appearance.addSeparator(); appearance.add(note);
        }
        view.add(appearance);
        JMenuItem tabHeight = new JMenuItem(owner.windowCommands().view("view.tab_height"));
        view.add(tabHeight);
        addButton(ActionId.NEW_TAB, "square-plus"); addButton(ActionId.NEW_WINDOW, "app-window");
        addToolbarSeparator();
        JButton split = addButton(ActionId.SPLIT_RIGHT, "columns-2");
        split.setAction(null); split.setText("Split"); split.setIcon(AppIcons.toolbarIcon("columns-2"));
        split.setToolTipText("Split pane right or down"); split.getAccessibleContext().setAccessibleName("Split pane");
        split.addActionListener(event -> {
            owner.updateActions(); JPopupMenu popup = new JPopupMenu();
            popup.add(owner.action(ActionId.SPLIT_RIGHT)); popup.add(owner.action(ActionId.SPLIT_DOWN));
            popup.show(split, 0, split.getHeight());
        });
        owner.action(ActionId.SPLIT_RIGHT).addPropertyChangeListener(event -> {
            if (event.getPropertyName().equals("enabled")) split.setEnabled(owner.action(ActionId.SPLIT_RIGHT).isEnabled());
        });
        toolbar.add(Box.createHorizontalStrut(UIScale.scale(4)));
        addButton(ActionId.ZOOM_PANE, "maximize"); addButton(ActionId.FIND, "search");
        addToolbarSeparator();
        toolbar.add(toolbarGlue);
    }

    private void addToolbarSeparator() {
        if (owner.retro()) toolbar.addSeparator(); else toolbar.add(new ToolbarSeparator());
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
        button.setIcon(icon != null ? icon : AppIcons.toolbarIcon("command"));
        button.setFocusable(false);
        if (owner.retro()) {
            RetroToolbar.styleButton(button);
        } else {
            button.setBorder(BorderFactory.createEmptyBorder()); button.setContentAreaFilled(false);
            button.setIconTextGap(UIScale.scale(8));
        }
        button.getAccessibleContext().setAccessibleName(label);
        button.setToolTipText(label);
        if (!owner.retro()) button.setFont(button.chromeFont());
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
            int index = toolbar.getComponentIndex(toolbarGlue);
            for (Component control : contributedToolbar) toolbar.add(control, index++);
        }
        toolbar.revalidate(); toolbar.repaint();
    }

    private List<Component> build(List<MenuEntry> entries) {
        List<Component> built = new ArrayList<>();
        for (MenuEntry entry : entries) {
            switch (entry) {
                case MenuEntry.Item item -> {
                    Action action = contributions.action(item.actionId());
                    if (action != null) built.add(new JMenuItem(action));
                }
                case MenuEntry.Separator separator -> built.add(new JPopupMenu.Separator());
                case MenuEntry.Submenu submenu -> {
                    List<Component> children = build(submenu.entries());
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
        contributedSections.forEach((menu, added) -> added.forEach(menu::remove));
        contributedSections.clear();
        contributedMenus.forEach(menuBar::remove);
        contributedMenus.clear();
        if (contributions != null) {
            for (MenuSection section : contributions.model().menus()) {
                List<Component> built = build(section.entries());
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

    void editTabHeight() {
        var control = new JPanel(new FlowLayout(FlowLayout.LEADING));
        var height = new JSpinner(new SpinnerNumberModel(owner.tabHeight(),
            WindowContent.MIN_TAB_HEIGHT, WindowContent.MAX_TAB_HEIGHT, 1));
        height.setName("tabHeight");
        var label = new JLabel("Height (pixels):"); label.setLabelFor(height);
        var reset = new JButton("Reset to default"); reset.setName("resetTabHeight");
        reset.addActionListener(event -> height.setValue(WindowContent.DEFAULT_TAB_HEIGHT));
        control.add(label); control.add(height); control.add(reset);
        if (owner.confirmTabHeight.applyAsInt(control) != JOptionPane.OK_OPTION) return;
        try {
            height.commitEdit();
            owner.setTabHeight(((Number) height.getValue()).intValue());
        } catch (java.text.ParseException | IllegalArgumentException failure) {
            owner.onError.accept("Enter a tab height between 28 and 72 pixels.");
        }
    }

    private JMenu menu(String label, ActionId... ids) {
        JMenu menu = new JMenu(label);
        for (ActionId id : ids) menu.add(owner.action(id));
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
            case RELOAD_CONFIG -> "Reload config"; default -> id.label();
        };
        ReferenceButton button = new ReferenceButton(owner.action(id), id);
        button.setText(label); button.putClientProperty("label", label);
        button.setIcon(AppIcons.toolbarIcon(icon)); button.setFocusable(false);
        if (owner.retro()) {
            RetroToolbar.styleButton(button);
        } else {
            button.setBorder(BorderFactory.createEmptyBorder()); button.setContentAreaFilled(false);
            button.setIconTextGap(UIScale.scale(8));
        }
        button.getAccessibleContext().setAccessibleName(id.label());
        if (button.getToolTipText() == null) button.setToolTipText(id.label());
        toolbar.add(button);
        return button;
    }

    /** Actual action buttons with a secondary shortcut hint and reference spacing. */
    private static final class ReferenceButton extends JButton {
        private final ActionId id;
        private boolean compact;
        private boolean chevron;
        ReferenceButton(Action action, ActionId id) { super(action); this.id = id; }
        private boolean labels() { return getText() != null && !compact; }
        private String hint() {
            if (id != ActionId.NEW_TAB || !labels()) return "";
            Object value = getAction().getValue(Action.ACCELERATOR_KEY);
            if (!(value instanceof KeyStroke stroke)) return "";
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
            Font font = UIManager.getFont("Label.font").deriveFont(Font.PLAIN, UIScale.scale(11.5f));
            return id == ActionId.NEW_TAB ? font.deriveFont(java.util.Map.of(java.awt.font.TextAttribute.WEIGHT,
                java.awt.font.TextAttribute.WEIGHT_SEMIBOLD)) : font;
        }
        @Override public Dimension getPreferredSize() {
            if (dev.jasper.app.platform.SwingAppearance.retro()) return super.getPreferredSize();
            FontMetrics fm = getFontMetrics(chromeFont());
            int width = UIScale.scale(32);
            if (labels()) width += UIScale.scale(5) + fm.stringWidth(getText());
            if (!hint().isEmpty()) width += UIScale.scale(8) + getFontMetrics(chromeFont().deriveFont(UIScale.scale(10f))).stringWidth(hint());
            if ((id == ActionId.SPLIT_RIGHT || chevron) && labels()) width += UIScale.scale(16);
            int trim = labels() && id != null ? switch (id) { case NEW_TAB -> 6; case NEW_WINDOW -> 3; case FIND -> 4; default -> 0; } : 0;
            int measuredWidth = width - UIScale.scale(trim);
            if (id == ActionId.NEW_TAB && labels()) measuredWidth = Math.max(UIScale.scale(104), measuredWidth);
            return new Dimension(measuredWidth, UIScale.scale(30));
        }
        @Override protected void paintComponent(Graphics graphics) {
            if (dev.jasper.app.platform.SwingAppearance.retro()) { super.paintComponent(graphics); return; }
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int arc = UIScale.scale(12);
                if (id == ActionId.NEW_TAB || getModel().isRollover() && isEnabled() || getModel().isPressed()) {
                    g.setColor(UIManager.getColor(getModel().isPressed() ? "Button.toolbar.pressedBackground" :
                        id == ActionId.NEW_TAB ? "Jasper.primaryBackground" : "Button.toolbar.hoverBackground"));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                    if (id == ActionId.NEW_TAB) {
                        g.setColor(UIManager.getColor("Jasper.primaryBorder"));
                        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                    }
                }
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.38f));
                int x = labels() ? UIScale.scale(id == ActionId.NEW_WINDOW ? 11 : 8) : (getWidth() - getIcon().getIconWidth()) / 2;
                getIcon().paintIcon(this, g, x, (getHeight() - getIcon().getIconHeight()) / 2);
                if (!labels()) return;
                x += getIcon().getIconWidth() + UIScale.scale(5);
                g.setFont(chromeFont()); g.setColor(UIManager.getColor("Jasper.chromeForeground"));
                FontMetrics fm = g.getFontMetrics(); int baseline = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g.drawString(getText(), x, baseline); x += fm.stringWidth(getText());
                if (!hint().isEmpty()) {
                    g.setFont(chromeFont().deriveFont(UIScale.scale(10f))); g.setColor(UIManager.getColor("Jasper.mutedForeground"));
                    g.drawString(hint(), x + UIScale.scale(8), baseline);
                }
                if (id == ActionId.SPLIT_RIGHT || chevron) {
                    x += UIScale.scale(8); int y = getHeight() / 2;
                    g.setStroke(new BasicStroke(UIScale.scale(1f)));
                    g.drawLine(x, y - 2, x + 3, y + 1); g.drawLine(x + 3, y + 1, x + 6, y - 2);
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

    /** Shrinks to icon controls before any action can disappear at ordinary narrow widths. */
    private static final class ReferenceToolbar extends JToolBar {
        @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(42)); }
        @Override public Dimension getPreferredSize() { return new Dimension(0, UIScale.scale(42)); }
        @Override public void doLayout() {
            int available = Math.max(0, getWidth() - UIScale.scale(30));
            int preferred = 0;
            for (Component child : getComponents()) {
                if (child instanceof ReferenceButton button) { button.compact = false; preferred += button.getPreferredSize().width; }
                else if (child instanceof JSeparator) preferred += UIScale.scale(16);
                else preferred += child.getPreferredSize().width;
            }
            boolean compact = preferred > available;
            int fixed = 0;
            for (Component child : getComponents()) {
                if (child instanceof ReferenceButton button) { button.compact = compact; fixed += button.getPreferredSize().width; }
                else if (child instanceof JSeparator) fixed += UIScale.scale(16);
                else fixed += child.getPreferredSize().width;
            }
            // At extreme widths compress spacing/buttons together; menus retain the same actions.
            double ratio = Math.min(1, available / (double) Math.max(1, fixed));
            int x = UIScale.scale(15), y = (getHeight() - UIScale.scale(30)) / 2;
            for (Component child : getComponents()) {
                int width;
                if (child instanceof JButton) {
                    width = (int) Math.floor(child.getPreferredSize().width * ratio);
                    child.setBounds(x, y, width, UIScale.scale(30));
                } else if (child instanceof JSeparator) {
                    width = (int) Math.floor(UIScale.scale(16) * ratio);
                    child.setBounds(x + width / 2, (getHeight() - UIScale.scale(16)) / 2, UIScale.scale(1), UIScale.scale(16));
                } else {
                    width = child.getPreferredSize().width > 0 ? (int) Math.floor(child.getPreferredSize().width * ratio) : Math.max(0, available - fixed);
                    child.setBounds(x, 0, width, getHeight());
                }
                x += width;
            }
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(UIManager.getColor("Separator.foreground"));
            g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
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
                List<Component> built = build(section.entries());
                if (built.isEmpty()) continue;
                menu.addSeparator();
                built.forEach(menu::add);
            }
        }
        return menu;
    }

    void setToolbarMode(ToolbarMode mode) {
        toolbar.setVisible(mode != ToolbarMode.HIDDEN);
        for (var component : toolbar.getComponents()) if (component instanceof JButton button) {
            button.setText(mode == ToolbarMode.ICONS ? null : (String) button.getClientProperty("label"));
        }
        if (toolbar instanceof RetroToolbar retro) retro.labels(mode == ToolbarMode.ICONS_AND_LABELS);
        toolbarModes.getElements().asIterator().forEachRemaining(item -> item.setSelected(item.getActionCommand().equals(mode.name())));
    }
    void refreshTheme() {
        toolbar.setBackground(UIManager.getColor("Jasper.titleBackground"));
        for (Component child : toolbar.getComponents()) if (!owner.retro() && child instanceof JButton button)
            button.setFont(((ReferenceButton) button).chromeFont());
        status.setBackground(owner.retro() ? UIManager.getColor("Panel.background") : owner.theme().palette().background());
        themeItems.forEach((theme, item) -> item.setSelected(owner.appearance() == theme));
    }
    void setStatusVisible(boolean visible) { status.setVisible(visible); statusVisible.setSelected(visible); }
    JToolBar toolbar() { return toolbar; }
    WindowStatusBar status() { return status; }
    JMenuBar menuBar() { return menuBar; }
}
