package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.Command;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.platform.AppIcons;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

/**
 * The single icon rail on the window's left edge: a toggle per panel grouped by region, then plain
 * action buttons, with Settings pinned at the bottom. It owns no state; the window re-renders it.
 */
final class WindowRail extends JPanel {
    record PanelButton(String id, String title, Icon icon, PanelRegion region, boolean selected) { }

    Consumer<String> onToggle = id -> { };
    BiConsumer<String, PanelRegion> onMove = (id, region) -> { };
    private final List<AbstractButton> buttons = new ArrayList<>();
    private final JButton settings;

    WindowRail(Action settingsAction) {
        super(null);
        settings = new JButton(settingsAction);
        style(settings, AppIcons.icon("settings"), (String) settingsAction.getValue(Action.NAME));
        getAccessibleContext().setAccessibleName("Window tools");
        render(List.of(), List.of());
        refreshTheme();
    }

    private static void style(AbstractButton button, Icon icon, String name) {
        button.setHideActionText(true);
        button.setText(null);
        button.setIcon(icon);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(false);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
    }

    void render(List<PanelButton> panels, List<Action> actions) {
        removeAll();
        buttons.clear();
        PanelRegion previous = null;
        for (PanelRegion region : PanelRegion.values()) {
            for (PanelButton panel : panels) {
                if (panel.region() != region) continue;
                if (previous != null && previous != region) add(new JSeparator());
                previous = region;
                var button = new JToggleButton();
                style(button, panel.icon(), panel.title());
                button.setSelected(panel.selected());
                button.addActionListener(event -> onToggle.accept(panel.id()));
                var menu = new JPopupMenu();
                for (PanelRegion target : PanelRegion.values()) {
                    if (target == region) continue;
                    String label = "Move to " + target.name().charAt(0) + target.name().substring(1).toLowerCase(java.util.Locale.ROOT);
                    var item = new JMenuItem(label);
                    item.addActionListener(event -> onMove.accept(panel.id(), target));
                    menu.add(item);
                }
                button.setComponentPopupMenu(menu);
                buttons.add(button); add(button);
            }
        }
        if (!actions.isEmpty() && !buttons.isEmpty()) add(new JSeparator());
        for (Action action : actions) {
            var button = new JButton(action);
            Icon icon = action.getValue(Command.ICON) instanceof Icon contributed ? contributed : AppIcons.icon("command");
            style(button, icon, (String) action.getValue(Action.NAME));
            buttons.add(button); add(button);
        }
        add(settings);
        revalidate(); repaint();
    }

    /** True when there is nothing but Settings, in which case the window hides the rail. */
    boolean empty() { return buttons.isEmpty(); }

    List<AbstractButton> buttons() { return List.copyOf(buttons); }

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(36), UIScale.scale(80)); }
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        int x = UIScale.scale(3), side = UIScale.scale(30), y = UIScale.scale(4);
        for (Component child : getComponents()) {
            if (child == settings) continue;
            if (child instanceof JSeparator) {
                child.setBounds(UIScale.scale(7), y + UIScale.scale(3), UIScale.scale(22), UIScale.scale(1));
                y += UIScale.scale(8);
            } else {
                child.setBounds(x, y, side, side);
                y += UIScale.scale(34);
            }
        }
        settings.setBounds(x, Math.max(y, getHeight() - UIScale.scale(34)), side, side);
    }

    void refreshTheme() {
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, UIScale.scale(1), UIManager.getColor("Separator.foreground")));
        repaint();
    }
}
