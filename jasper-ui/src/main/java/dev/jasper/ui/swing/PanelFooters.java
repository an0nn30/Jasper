package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Persistent panel footer surface; its content is reconciler-owned, its wrapper panel-owned. A detail footer (the
 * selected item's card) and other footer content are a raised surface under a full-width rule. A settings footer (a
 * persistent setting row, or a vertical stack holding one) stays on the panel surface: its persistent setting rows carry
 * the full-width rule above them, and status lines sit above that rule.
 */
final class PanelFooters {
    private PanelFooters() {}

    static final class Footer extends JPanel implements Scrollable {
        final KitContext context;
        boolean detail;
        /** The footer content arranges its own vertical spacing: a persistent setting row or a footer stack. */
        boolean arranged;
        /** The footer's persistent setting rows; each has the full-width rule along its top edge. */
        List<Component> separators = List.of();

        Footer(KitContext context, String id) {
            this.context = context;
            setOpaque(false);
            KitOwnership.mark(this, id);
            refresh();
        }

        void refresh() {
            // A settings footer (a persistent row or a footer stack) spaces its own lines at the settings-footer side;
            // any other footer content keeps the ordinary footer padding.
            int side = ThemePainting.metric(context, arranged ? DesignMetrics.Role.PANEL_FOOTER_SIDE : DesignMetrics.Role.FIELD_HORIZONTAL_PADDING),
                    vertical = arranged ? 0 : ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
            setBorder(detail ? new EmptyBorder(0, 0, 0, 0) : new EmptyBorder(vertical, side, vertical, side));
            setLayout(new Composition.Vertical(0));
        }

        void mount(JComponent child) {
            Stacks.mount(this, List.of(child));
        }

        int interactiveHeight(Container parent) {
            int height=0;
            for(var child:parent.getComponents())if(child.isVisible()) {
                if(child instanceof AbstractButton || child instanceof javax.swing.text.JTextComponent || child instanceof JComboBox<?> || child instanceof JSpinner)
                    height=Math.max(height,child.getPreferredSize().height);
                if(child instanceof InteractiveRows.Canvas canvas)height=Math.max(height,canvas.owner.rowHeight);
                if(child instanceof Container nested)height=Math.max(height,interactiveHeight(nested));
            }
            return height;
        }

        int height(int width) {
            return Math.max(
                    arranged ? 0 : ThemePainting.metric(context, DesignMetrics.Role.PANEL_FOOTER),
                    Composition.height(this, width));
        }

        @Override public Dimension getPreferredScrollableViewportSize() {return getPreferredSize();}
        @Override public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction) {return ThemePainting.metric(context,DesignMetrics.Role.PANEL_DETAIL_ACTION);}
        @Override public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction) {return Math.max(1,r.height-getScrollableUnitIncrement(r,orientation,direction));}
        @Override public boolean getScrollableTracksViewportWidth() {return true;}
        @Override public boolean getScrollableTracksViewportHeight() {return getParent()!=null && height(getParent().getWidth())<=getParent().getHeight();}
        @Override public Dimension getPreferredSize() {
            var natural=super.getPreferredSize();
            int width=getParent() instanceof JViewport viewport ? viewport.getExtentSize().width : natural.width;
            return new Dimension(natural.width,height(Math.max(1,width)));
        }
        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                // A separator is one physical pixel at every device/user scale.
                double stroke = 1.0 / Math.abs(g.getTransform().getScaleY());
                if (detail || !arranged) {
                    int radius = ThemePainting.metric(context, DesignMetrics.Role.CARD_RADIUS);
                    g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_PANEL_RAISED));
                    g.fillRoundRect(0, -radius, getWidth(), getHeight() + radius, radius, radius);
                    g.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
                    g.fill(new java.awt.geom.Rectangle2D.Double(0, 0, getWidth(), stroke));
                    return;
                }
                // An arranged footer's rule fills the logical hairline its row reserves above itself (two device pixels at
                // 2x, as both plugin-panel references show), unlike the one-physical-pixel rule of a raised footer.
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
                int rule = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                for (var row : separators) {
                    if (!row.isVisible() || row.getParent() == null || !SwingUtilities.isDescendingFrom(row, this)) continue;
                    int y = SwingUtilities.convertPoint(row.getParent(), 0, row.getY(), this).y;
                    g.fillRect(0, y, getWidth(), rule);
                }
            } finally { g.dispose(); }
        }
    }

    /**
     * A vertical stack that is a panel's footer: status lines stacked with the footer gap, a persistent setting row a
     * short separator gap below them and flush with the footer's bottom when it is last.
     */
    static final class StackLayout implements Composition.WidthLayout {
        final Stacks.StackControl owner;
        final Footer footer;
        StackLayout(Stacks.StackControl owner, Footer footer) { this.owner = owner; this.footer = footer; }
        int metric(DesignMetrics.Role role) { return ThemePainting.metric(owner.context(), role); }
        boolean separated(Component c) { return footer.separators.contains(c); }
        int arrange(Container parent, int width, boolean layout) {
            Insets insets = parent.getInsets();
            int y = insets.top, w = Math.max(0, width - insets.left - insets.right);
            Component previous = null;
            for (var child : parent.getComponents()) {
                if (!child.isVisible()) continue;
                boolean rule = separated(child);
                y += previous == null ? rule ? 0 : metric(DesignMetrics.Role.PANEL_FOOTER_TOP)
                    : rule ? metric(DesignMetrics.Role.PANEL_FOOTER_SEPARATOR_GAP) : metric(DesignMetrics.Role.PANEL_FOOTER_GAP);
                int h = Composition.height(child, w);
                if (layout) child.setBounds(insets.left, y, w, h);
                y += h;
                previous = child;
            }
            if (previous != null && !separated(previous)) y += metric(DesignMetrics.Role.PANEL_FOOTER_TOP);
            return y + insets.bottom;
        }
        @Override public void addLayoutComponent(String name, Component component) {}
        @Override public void removeLayoutComponent(Component component) {}
        @Override public int height(Container parent, int width) { return arrange(parent, width, false); }
        @Override public Dimension preferredLayoutSize(Container parent) {
            int w = 0;
            for (var child : parent.getComponents()) if (child.isVisible()) w = Math.max(w, child.getPreferredSize().width);
            return new Dimension(w, height(parent, parent.getWidth() > 0 ? parent.getWidth() : w));
        }
        @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
        @Override public void layoutContainer(Container parent) { arrange(parent, parent.getWidth(), true); }
    }
}
