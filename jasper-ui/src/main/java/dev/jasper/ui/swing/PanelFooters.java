package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Persistent panel footer surface; its content is reconciler-owned, its wrapper panel-owned. */
final class PanelFooters {
    private PanelFooters() {}

    static final class Footer extends JPanel implements Scrollable {
        final KitContext context;
        boolean detail;

        Footer(KitContext context, String id) {
            this.context = context;
            setOpaque(false);
            KitOwnership.mark(this, id);
            refresh();
        }

        void refresh() {
            int side = ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING),
                    gap = ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
            setBorder(detail ? new EmptyBorder(0, 0, 0, 0) : new EmptyBorder(gap, side, gap, side));
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
                    ThemePainting.metric(context, DesignMetrics.Role.PANEL_FOOTER),
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
                int radius = ThemePainting.metric(context, DesignMetrics.Role.CARD_RADIUS);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_PANEL_RAISED));
                g.fillRoundRect(0, -radius, getWidth(), getHeight() + radius, radius, radius);
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
                // A separator is one physical pixel at every device/user scale.
                double deviceScale = Math.abs(g.getTransform().getScaleY());
                g.fill(new java.awt.geom.Rectangle2D.Double(0, 0, getWidth(), 1.0 / deviceScale));
            } finally { g.dispose(); }
        }
    }
}
