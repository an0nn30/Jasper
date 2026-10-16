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
 * into a live overflow menu behind a vertical ellipsis. A contributed view never does: it hides while the row is too
 * narrow for it. */
final class WindowToolbar extends JToolBar {
    private static final String MENU = "Jasper.overflowMenu", PRIORITY = "Jasper.toolbarPriority", VIEW = "Jasper.toolbarView";
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

    private static boolean isView(Component child) {
        return child instanceof JComponent component && Boolean.TRUE.equals(component.getClientProperty(VIEW));
    }

    /** A control the row places: a button other than the overflow button, or a contributed view. */
    private boolean placed(Component child) { return child instanceof JButton button ? button != overflow : isView(child); }

    private int groupWidth(int gap) {
        int width = 0, count = 0;
        for (Component child : getComponents())
            if (placed(child)) { width += child.getPreferredSize().width + gap; count++; }
        return count == 0 ? 0 : width - gap;
    }

    /** The width of the controls the row shows now, with the gaps between them. */
    private int visibleWidth(int gap) {
        int width = 0, count = 0;
        for (Component child : getComponents())
            if (placed(child) && child.isVisible()) { width += child.getPreferredSize().width + gap; count++; }
        return count == 0 ? 0 : width - gap;
    }

    /** The width that shows every control; the header never gives the toolbar more. */
    int fullWidth() {
        int padding = NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_PADDING), gap = NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING), width = 0;
        for (Component child : getComponents())
            if (placed(child)) width += child.getPreferredSize().width + gap;
        return width == 0 ? 0 : 2 * padding + width - gap;
    }

    /** The width with every hideable control in the overflow menu or hidden. */
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

    /**
     * Marks a contributed view (a plugin's toolbar view): the row places it at its preferred size like a button, and
     * {@code priority} decides when it gives way as a button's does, but it never moves into the overflow menu: it hides.
     */
    static void view(JComponent component, int priority) {
        component.putClientProperty(PRIORITY, priority);
        component.putClientProperty(VIEW, Boolean.TRUE);
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
        boolean labelsGaveWay = labelsWanted && groupWidth(gap) > available;
        if (labelsGaveWay) applyLabels(false);
        List<Component> controls = new ArrayList<>();
        for (Component child : getComponents()) {
            if (placed(child)) {
                controls.add(child); child.setVisible(true);
            }
        }
        // Stable priority, with later controls giving way first within a priority tier. A button moves into the overflow
        // menu; a view hides, so only a button that moved makes room for the overflow button.
        var removable = new ArrayList<>(controls);
        java.util.Collections.reverse(removable);
        removable.sort(Comparator.comparingInt(control -> ((JComponent) control).getClientProperty(PRIORITY) instanceof Integer p ? p : 50));
        int group = controls.stream().mapToInt(control -> control.getPreferredSize().width + gap).sum() - (controls.isEmpty() ? 0 : gap);
        boolean hidden = false, viewHid = false;
        List<Component> overflowed = new ArrayList<>();
        for (Component control : removable) {
            if (group + (hidden ? gap + side : 0) <= available) break;
            boolean view = isView(control);
            if (!view && !(((JComponent) control).getClientProperty(MENU) instanceof Supplier<?>)) continue;
            control.setVisible(false); control.setBounds(0, 0, 0, 0);
            group -= control.getPreferredSize().width + gap;
            if (view) viewHid = true; else { hidden = true; overflowed.add(control); }
        }
        if (viewHid) {
            // A wide view that hid can leave room for buttons that gave way before it: they come back, the most recently
            // overflowed first, while they fit beside the overflow button any others still need.
            for (int i = overflowed.size() - 1; i >= 0; i--) {
                Component control = overflowed.get(i);
                int width = control.getPreferredSize().width + gap;
                if (group + width + (i > 0 ? gap + side : 0) > available) break;
                control.setVisible(true); group += width; overflowed.remove(i);
            }
            hidden = !overflowed.isEmpty();
            // Labels that gave way for the view come back too when the row still fits with them.
            if (labelsGaveWay) {
                applyLabels(true);
                int width = visibleWidth(gap);
                if (width + (hidden ? gap + side : 0) > available) applyLabels(false);
                else group = width;
            }
        }
        overflow.setVisible(hidden);
        int shown = Math.max(0, group);
        int trailing = hidden ? side + (shown > 0 ? gap : 0) : 0;
        // Separators share the inter-control spacing; they never consume button target space.
        int x = Math.max(padding, getWidth() - padding - trailing - shown), y = (getHeight() - side) / 2;
        boolean previousVisible = false;
        for (Component child : getComponents()) {
            if (child == overflow) continue;
            if (placed(child)) {
                if (!child.isVisible()) continue;
                int width = child.getPreferredSize().width;
                if (isView(child)) {
                    // A view keeps its own height, centred in the row.
                    int height = Math.min(getHeight(), child.getPreferredSize().height);
                    child.setBounds(x, (getHeight() - height) / 2, width, height);
                } else child.setBounds(x, y, width, side);
                x += width + gap; previousVisible = true;
            } else if (child instanceof JSeparator) {
                child.setBounds(Math.max(0, x - gap / 2), NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_SEPARATOR_INSET), NativeStyles.installed().metric(DesignMetrics.Role.HAIRLINE),
                    Math.max(0, getHeight() - NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_SEPARATOR_INSETS)));
                // Empty groups paint no rule; a hidden rule (the empty plugin group's) leaves the
                // previous group open, so the rule before Find still shows.
                boolean shownRule = previousVisible && hasFollowingVisibleControl(child);
                child.setVisible(shownRule);
                if (shownRule) previousVisible = false;
            }
        }
        if (hidden) overflow.setBounds(Math.max(0, getWidth() - padding - side), y, side, side);
        else overflow.setBounds(0, 0, 0, 0);
    }

    private boolean hasFollowingVisibleControl(Component separator) {
        for (int i = getComponentIndex(separator) + 1; i < getComponentCount(); i++) {
            Component next = getComponent(i);
            if (next instanceof JSeparator) return false;
            if (placed(next) && next.isVisible()) return true;
        }
        return false;
    }
}
