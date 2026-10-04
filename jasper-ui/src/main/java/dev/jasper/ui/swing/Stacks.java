package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import java.awt.*;
import java.util.List;
import javax.swing.*;

/** Basic semantic composition pulled forward for stable siblings; larger containers compose this layout. */
final class Stacks {
    private Stacks() {}
    static final class StackControl extends ControlRegistry.Control {
        StackControl(KitView view, Nodes.Stack node) { super(view, node, new JPanel()); component.setOpaque(false); refresh(); }
        @Override void refresh() {
            state(); var stack = (Nodes.Stack)node;
            component.setLayout(new StackLayout(context(), stack.role())); component.revalidate(); component.repaint();
        }
        void children(List<JComponent> wanted) {
            for (Component child : component.getComponents()) if (!wanted.contains(child)) component.remove(child);
            for (int i = 0; i < wanted.size(); i++) {
                JComponent child = wanted.get(i);
                if (child.getParent() != component) component.add(child, i);
                else if (component.getComponentZOrder(child) != i) component.setComponentZOrder(child, i);
            }
        }
    }
    static final class StackLayout implements LayoutManager {
        final KitContext context; final Nodes.LayoutRole role;
        StackLayout(KitContext context, Nodes.LayoutRole role) { this.context = context; this.role = role; }
        boolean horizontal() { return switch (role) { case INLINE, ACTIONS, FOOTER, NAVIGATION -> true; default -> false; }; }
        int gap() { return ThemePainting.metric(context, role == Nodes.LayoutRole.ACTIONS ? DesignMetrics.Role.BUTTON_GAP : horizontal() ? DesignMetrics.Role.COLUMN_GAP : DesignMetrics.Role.ROW_GAP); }
        @Override public void addLayoutComponent(String name, Component component) {}
        @Override public void removeLayoutComponent(Component component) {}
        @Override public Dimension preferredLayoutSize(Container parent) { return measure(parent, false); }
        @Override public Dimension minimumLayoutSize(Container parent) { return measure(parent, true); }
        Dimension measure(Container parent, boolean minimum) {
            int primary = 0, cross = 0, count = 0;
            for (Component child : parent.getComponents()) if (child.isVisible()) {
                Dimension size = minimum ? child.getMinimumSize() : child.getPreferredSize();
                primary += horizontal() ? size.width : size.height; cross = Math.max(cross, horizontal() ? size.height : size.width); count++;
            }
            primary += Math.max(0, count - 1) * gap(); var insets = parent.getInsets();
            return new Dimension((horizontal() ? primary : cross) + insets.left + insets.right, (horizontal() ? cross : primary) + insets.top + insets.bottom);
        }
        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets(); int x = insets.left, y = insets.top;
            int width = Math.max(0, parent.getWidth() - insets.left - insets.right), height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            for (Component child : parent.getComponents()) if (child.isVisible()) {
                var size = child.getPreferredSize();
                if (horizontal()) { child.setBounds(x, y + Math.max(0, (height - size.height) / 2), size.width, Math.min(height, size.height)); x += size.width + gap(); }
                else { child.setBounds(x, y, width, size.height); y += size.height + gap(); }
            }
        }
    }
}
