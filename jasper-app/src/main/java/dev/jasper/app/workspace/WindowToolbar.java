package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.platform.AppIcons;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.*;

/** One row of stable-size controls; lower-priority controls move whole into a live overflow menu. */
final class WindowToolbar extends JToolBar {
    private static final String MENU = "Jasper.overflowMenu", PRIORITY = "Jasper.toolbarPriority";
    private final JButton overflow = new JButton(AppIcons.named("MORE"));
    private final Runnable refreshActions;
    private int barHeight = ConfigSnapshot.DEFAULT_TOOLBAR_HEIGHT;
    private boolean titleBar;

    WindowToolbar(Runnable refreshActions) {
        this.refreshActions = refreshActions;
        setFloatable(false);
        setBorder(BorderFactory.createEmptyBorder());
        overflow.setName("toolbarOverflow");
        overflow.setText(null);
        overflow.putClientProperty("label", "More actions");
        overflow.putClientProperty("JButton.buttonType", "toolBarButton");
        overflow.setToolTipText("More actions");
        overflow.getAccessibleContext().setAccessibleName("More actions");
        overflow.setFocusable(false);
        overflow.addActionListener(event -> overflowMenu().show(overflow, 0, overflow.getHeight()));
        overflow.setVisible(false);
    }

    void finish() { add(overflow); }
    void setTitleBar(boolean value) { titleBar = value; revalidate(); }
    void setBarHeight(int height) {
        int old = barHeight;
        barHeight = height;
        firePropertyChange("barHeight", old, height);
        revalidate(); repaint();
    }
    static int buttonSide(int height) { return UIScale.scale(height - 6); }
    int buttonSide() { return buttonSide(barHeight); }

    static void entry(JButton button, int priority, Supplier<JMenuItem> menu) {
        button.putClientProperty(PRIORITY, priority);
        button.putClientProperty(MENU, menu);
    }

    static JMenuItem menuItem(Action action) {
        JMenuItem item = action.getValue(Action.SELECTED_KEY) instanceof Boolean
            ? new JCheckBoxMenuItem(action) : new JMenuItem(action);
        item.putClientProperty("html.disable", true);
        return item;
    }

    JPopupMenu overflowMenu() {
        refreshActions.run();
        var menu = new JPopupMenu();
        for (Component child : getComponents()) {
            if (!(child instanceof JButton button) || button == overflow || button.isVisible()) continue;
            if (button.getClientProperty(MENU) instanceof Supplier<?> factory) menu.add((JMenuItem) factory.get());
        }
        return menu;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(barHeight)); }
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        int padding = UIScale.scale(8), gap = UIScale.scale(4), side = buttonSide();
        int available = Math.max(0, getWidth() - 2 * padding);
        int dragSpace = titleBar ? Math.min(UIScale.scale(64), Math.max(0, available - side)) : 0;
        List<JButton> buttons = new ArrayList<>();
        for (Component child : getComponents()) {
            if (child instanceof JButton button && button != overflow) {
                buttons.add(button); button.setVisible(true);
            }
        }
        // Stable priority, with later controls overflowing first within a priority tier.
        var removable = new ArrayList<>(buttons);
        java.util.Collections.reverse(removable);
        removable.sort(Comparator.comparingInt(button -> button.getClientProperty(PRIORITY) instanceof Integer p ? p : 50));
        int needed = buttons.stream().mapToInt(button -> button.getPreferredSize().width + gap).sum();
        boolean hidden = false;
        for (JButton button : removable) {
            if (needed + dragSpace + (hidden ? side + gap : 0) <= available) break;
            if (!(button.getClientProperty(MENU) instanceof Supplier<?>)) continue;
            button.setVisible(false); button.setBounds(0, 0, 0, 0);
            needed -= button.getPreferredSize().width + gap;
            hidden = true;
        }
        overflow.setVisible(hidden);
        // Separators share the inter-control spacing; they never consume button target space.
        int x = padding, y = (getHeight() - side) / 2;
        int remaining = needed + (hidden ? side + gap : 0);
        boolean previousVisible = false;
        for (Component child : getComponents()) {
            if (child == overflow) continue;
            if (child instanceof JButton button) {
                if (!button.isVisible()) continue;
                int width = button.getPreferredSize().width;
                button.setBounds(x, y, width, side);
                x += width + gap; remaining -= width + gap; previousVisible = true;
            } else if (child instanceof Box.Filler) {
                int width = Math.max(0, getWidth() - padding - x - remaining);
                child.setBounds(x, 0, width, getHeight()); x += width; previousVisible = false;
            } else if (child instanceof JSeparator) {
                child.setBounds(Math.max(0, x - gap / 2), UIScale.scale(7), UIScale.scale(1),
                    Math.max(0, getHeight() - UIScale.scale(14)));
                // Empty plugin separators are maintained by the owner; hidden groups paint no rule.
                child.setVisible(previousVisible && hasFollowingVisibleButton(child));
                previousVisible = false;
            }
        }
        if (hidden) overflow.setBounds(Math.max(0, getWidth() - padding - side), y, side, side);
        else overflow.setBounds(0, 0, 0, 0);
    }

    private boolean hasFollowingVisibleButton(Component separator) {
        for (int i = getComponentIndex(separator) + 1; i < getComponentCount(); i++) {
            Component next = getComponent(i);
            if (next instanceof Box.Filler || next instanceof JSeparator) return false;
            if (next instanceof JButton && next != overflow && next.isVisible()) return true;
        }
        return false;
    }

}
