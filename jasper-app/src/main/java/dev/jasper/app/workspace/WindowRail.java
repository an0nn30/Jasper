package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.Command;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.platform.AppIcons;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
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
 * action buttons. Settings lives in the workspace header. It owns no state; the window re-renders it.
 */
final class WindowRail extends JPanel {
    record PanelButton(String id, String title, Icon icon, PanelRegion region, boolean selected, Optional<PanelBadge> badge) {
        PanelButton(String id, String title, Icon icon, PanelRegion region, boolean selected) {
            this(id, title, icon, region, selected, Optional.empty());
        }
    }

    Consumer<String> onToggle = id -> { };
    BiConsumer<String, PanelRegion> onMove = (id, region) -> { };
    private final List<AbstractButton> buttons = new ArrayList<>();
    /** The rail's width in logical pixels; buttons, separators and spacing follow it. */
    private int railWidth = ConfigSnapshot.DEFAULT_RAIL_WIDTH;

    WindowRail() {
        super(null);
        getAccessibleContext().setAccessibleName("Window tools");
        render(List.of(), List.of());
        refreshTheme();
    }

    private static void style(AbstractButton button, Icon icon, String name) {
        button.setHideActionText(true);
        button.setText(null);
        button.setIcon(icon);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.putClientProperty("FlatLaf.style", "arc: 10");
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
                style(button, panel.badge().<Icon>map(badge -> new BadgedIcon(panel.icon(), badge.tone())).orElse(panel.icon()),
                    panel.title());
                panel.badge().ifPresent(badge -> {
                    button.setToolTipText(panel.title() + " (" + badge.description() + ")");
                    button.getAccessibleContext().setAccessibleDescription(badge.description());
                });
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
        revalidate(); repaint();
    }

    /** True when there are no contributed tools, in which case the window hides the rail. */
    boolean empty() { return buttons.isEmpty(); }

    List<AbstractButton> buttons() { return List.copyOf(buttons); }

    /** Sets the width in logical pixels; the caller validates the range. */
    void setRailWidth(int width) {
        if (railWidth == width) return;
        railWidth = width;
        revalidate(); repaint();
    }

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(railWidth), UIScale.scale(80)); }
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        // Square buttons 3 px in from each side, 4 px apart; separators inset 7 px.
        int x = UIScale.scale(3), side = UIScale.scale(railWidth - 6), step = side + UIScale.scale(4), y = UIScale.scale(4);
        for (Component child : getComponents()) {
            if (child instanceof JSeparator) {
                child.setBounds(UIScale.scale(7), y + UIScale.scale(3), UIScale.scale(railWidth - 14), UIScale.scale(1));
                y += UIScale.scale(8);
            } else {
                child.setBounds(x, y, side, side);
                y += step;
            }
        }
    }

    void refreshTheme() {
        setBackground(UIManager.getColor("Jasper.titleBackground"));
        repaint();
    }
}
