package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.Border;

/**
 * The classic table and tree header and box (spec 5.2; the Settings Notifications and Keymap mocks), shared by both
 * classic faces (plan 4c Rulings 1 to 5).
 */
final class ClassicHeaders {
    private ClassicHeaders() {}

    /** How far a title or cell after a column separator sits from its column's start: the separator and the icon gap. */
    static int inset(KitContext context) {
        return ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE) + ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
    }

    /** Title {@code index} in {@code ink}'s guarded colour, inset after its separator unless it is the first. */
    static void title(JLabel title, KitContext context, int index, PaintPair ink) {
        title.setForeground(ThemePainting.painted(context, ink));
        if (index > 0) title.setBorder(BorderFactory.createEmptyBorder(0, inset(context), 0, 0));
    }

    /** A band {@code TABLE_HEADER_HEIGHT} tall around titles {@code line} high, then the rule under it. */
    static Insets insets(KitContext context, int side, int line) {
        int band = ThemePainting.metric(context, DesignMetrics.Role.TABLE_HEADER_HEIGHT), rule = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
        int top = Math.max(0, (band - line) / 2);
        return new Insets(top, side, Math.max(0, band - line - top) + rule, side);
    }

    /** The band, a separator before every later title and the rule along the bottom. */
    static void paint(JComponent header, Graphics2D g, KitContext context) {
        int rule = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), width = header.getWidth(), height = header.getHeight();
        g.setColor(ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER));
        g.fillRect(0, 0, width, height);
        g.setColor(ThemePainting.role(context, DerivedColor.BORDER_DIVIDER));
        g.fillRect(0, height - rule, width, rule);
        for (int i = 1; i < header.getComponentCount(); i++) {
            var title = header.getComponent(i);
            if (title.isVisible()) g.fillRect(title.getX(), 0, rule, Math.max(0, height - rule));
        }
    }

    /**
     * The 1 px box around a table or tree that is not in a panel body (Rulings 2), its edge through {@code edge}'s guard,
     * around the rows' {@code surface.card} (the mocks' white list). A border paints before the pane's children, so the
     * fill lies under the header band and the rows.
     */
    static final class Box implements Border {
        final KitContext context;
        final PaintPair edge;

        Box(KitContext context, PaintPair edge) { this.context = context; this.edge = edge; }

        /** A panel body draws its own rules around the rows. */
        static boolean drawn(Component c) { return SwingUtilities.getAncestorOfClass(Panels.Body.class, c) == null; }

        @Override public Insets getBorderInsets(Component c) {
            int line = drawn(c) ? ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE) : 0;
            return new Insets(line, line, line, line);
        }

        @Override public boolean isBorderOpaque() { return false; }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            if (!drawn(c)) return;
            int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            g.setColor(ThemePainting.color(context, dev.jasper.ui.theme.SemanticColor.SURFACE_CARD));
            g.fillRect(x + line, y + line, Math.max(0, width - 2 * line), Math.max(0, height - 2 * line));
            g.setColor(ThemePainting.painted(context, edge));
            g.fillRect(x, y, width, line);
            g.fillRect(x, y + height - line, width, line);
            g.fillRect(x, y, line, height);
            g.fillRect(x + width - line, y, line, height);
        }
    }
}
