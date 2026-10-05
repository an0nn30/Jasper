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

    static final class Footer extends JPanel {
        final KitContext context;

        Footer(KitContext context, String id) {
            this.context = context;
            setOpaque(false);
            KitOwnership.mark(this, id);
            refresh();
        }

        void refresh() {
            int side = ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING),
                    gap = ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
            setBorder(new EmptyBorder(gap, side, gap, side));
            setLayout(new Composition.Vertical(0));
        }

        void mount(JComponent child) {
            Stacks.mount(this, List.of(child));
        }

        int height(int width) {
            return Math.max(
                    ThemePainting.metric(context, DesignMetrics.Role.PANEL_FOOTER),
                    Composition.height(this, width));
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
                g.drawLine(0, 0, getWidth(), 0);
            } finally { g.dispose(); }
        }
    }
}
