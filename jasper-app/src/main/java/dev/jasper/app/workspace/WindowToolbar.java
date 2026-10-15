package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.platform.AppIcons;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.*;

/** One row of stable-size controls packed against the trailing edge; lower-priority controls move whole
 * into a live overflow menu behind a vertical ellipsis. */
final class WindowToolbar extends JToolBar {
    private static final String MENU = "Jasper.overflowMenu", PRIORITY = "Jasper.toolbarPriority";
    private final JButton overflow = new JButton(AppIcons.semantic("more.vertical"));
    private final Runnable refreshActions;
    private int barHeight = ConfigSnapshot.DEFAULT_TOOLBAR_HEIGHT;

    WindowToolbar(Runnable refreshActions) {
        this.refreshActions = refreshActions;
        setFloatable(false);
        setBorder(BorderFactory.createEmptyBorder());
        overflow.setName("toolbarOverflow");
        overflow.setText(null);
        overflow.putClientProperty("label", "More actions");
        NativeStyles.installed().button(overflow, () -> ThemeZone.theme(overflow));
        overflow.setToolTipText("More actions");
        overflow.getAccessibleContext().setAccessibleName("More actions");
        overflow.setFocusable(false);
        overflow.addActionListener(event -> overflowMenu().show(overflow, 0, overflow.getHeight()));
        overflow.setVisible(false);
    }

    void finish() { add(overflow); }
    void setBarHeight(int height) {
        int old = barHeight;
        barHeight = height;
        firePropertyChange("barHeight", old, height);
        revalidate(); repaint();
    }
    static int buttonSide(int height) {
        return UIScale.scale(height - dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.TOOLBAR_BUTTON_TRIM));
    }
    int buttonSide() { return buttonSide(barHeight); }
    JButton overflowButton() { return overflow; }

    private boolean labelsWanted = true;

    /** Whether labelled controls (Split, contributed buttons and dropdowns) show their labels when they fit. */
    void setLabelsWanted(boolean value) {
        labelsWanted = value;
        applyLabels(value);
        revalidate(); repaint();
    }

    private boolean labelled(JButton button) {
        return button != overflow && !Boolean.FALSE.equals(button.getClientProperty("keepLabel"))
            && button.getClientProperty("label") instanceof String;
    }

    private void applyLabels(boolean on) {
        for (Component child : getComponents())
            if (child instanceof JButton button && labelled(button)) {
                String text = on ? (String) button.getClientProperty("label") : null;
                if (!java.util.Objects.equals(button.getText(), text)) button.setText(text);
            }
    }

    private int groupWidth(int gap) {
        int width = 0, count = 0;
        for (Component child : getComponents())
            if (child instanceof JButton button && button != overflow) { width += button.getPreferredSize().width + gap; count++; }
        return count == 0 ? 0 : width - gap;
    }

    /** The width that shows every control; the header never gives the toolbar more. */
    int fullWidth() {
        int padding = NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_PADDING), gap = NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING), width = 0;
        for (Component child : getComponents())
            if (child instanceof JButton button && button != overflow) width += button.getPreferredSize().width + gap;
        return width == 0 ? 0 : 2 * padding + width - gap;
    }

    /** The width with every hideable control in the overflow menu. */
    int minimumWidth() {
        int padding = NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_PADDING), gap = NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING), width = buttonSide();
        for (Component child : getComponents())
            if (child instanceof JButton button && button != overflow && !(button.getClientProperty(MENU) instanceof Supplier<?>))
                width += button.getPreferredSize().width + gap;
        return 2 * padding + width;
    }

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
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme); return menu;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(barHeight)); }
    @Override public Dimension getPreferredSize() { return new Dimension(fullWidth(), UIScale.scale(barHeight)); }

    @Override public void doLayout() {
        int padding = NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_PADDING), gap = NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING), side = buttonSide();
        int available = Math.max(0, getWidth() - 2 * padding);
        // Labels give way before any control moves into the overflow menu.
        applyLabels(labelsWanted);
        if (labelsWanted && groupWidth(gap) > available) applyLabels(false);
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
        int group = buttons.stream().mapToInt(button -> button.getPreferredSize().width + gap).sum() - (buttons.isEmpty() ? 0 : gap);
        boolean hidden = false;
        for (JButton button : removable) {
            if (group + (hidden ? gap + side : 0) <= available) break;
            if (!(button.getClientProperty(MENU) instanceof Supplier<?>)) continue;
            button.setVisible(false); button.setBounds(0, 0, 0, 0);
            group -= button.getPreferredSize().width + gap;
            hidden = true;
        }
        overflow.setVisible(hidden);
        int shown = Math.max(0, group);
        int trailing = hidden ? side + (shown > 0 ? gap : 0) : 0;
        // Separators share the inter-control spacing; they never consume button target space.
        int x = Math.max(padding, getWidth() - padding - trailing - shown), y = (getHeight() - side) / 2;
        boolean previousVisible = false;
        for (Component child : getComponents()) {
            if (child == overflow) continue;
            if (child instanceof JButton button) {
                if (!button.isVisible()) continue;
                int width = button.getPreferredSize().width;
                button.setBounds(x, y, width, side);
                x += width + gap; previousVisible = true;
            } else if (child instanceof JSeparator) {
                child.setBounds(Math.max(0, x - gap / 2), NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_SEPARATOR_INSET), NativeStyles.installed().metric(DesignMetrics.Role.HAIRLINE),
                    Math.max(0, getHeight() - NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_SEPARATOR_INSETS)));
                // Empty groups paint no rule; a hidden rule (the empty plugin group's) leaves the
                // previous group open, so the rule before Find still shows.
                boolean shownRule = previousVisible && hasFollowingVisibleButton(child);
                child.setVisible(shownRule);
                if (shownRule) previousVisible = false;
            }
        }
        if (hidden) overflow.setBounds(Math.max(0, getWidth() - padding - side), y, side, side);
        else overflow.setBounds(0, 0, 0, 0);
    }

    private boolean hasFollowingVisibleButton(Component separator) {
        for (int i = getComponentIndex(separator) + 1; i < getComponentCount(); i++) {
            Component next = getComponent(i);
            if (next instanceof JSeparator) return false;
            if (next instanceof JButton && next != overflow && next.isVisible()) return true;
        }
        return false;
    }
}
