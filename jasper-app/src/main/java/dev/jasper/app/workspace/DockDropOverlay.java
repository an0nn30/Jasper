package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * The drag guides, on the layered pane's DRAG_LAYER: a translucent highlight with a 1px border exactly where the
 * dragged tool window would land, a highlighted rounded square in the stripe's gap, and the window's icon at the
 * cursor. It takes no mouse input. Colours: {@code DragAndDrop.areaBackground} and {@code DragAndDrop.borderColor}
 * when the theme defines them, otherwise {@code Component.accentColor} at 25 % for the fill and 60 % for the border.
 * EDT only.
 */
final class DockDropOverlay extends JComponent {
    private static final int FILL_ALPHA = 64, BORDER_ALPHA = 153, ARC = 10;
    /** IntelliJ's accent, for a theme without an accent colour. */
    private static final Color FALLBACK_ACCENT = new Color(0x3574f0);

    private Rectangle highlight;
    private Rectangle placeholder;
    private Icon ghost;
    private Point cursor;

    DockDropOverlay() { setOpaque(false); }

    /**
     * Shows the guides; each may be null.
     *
     * @param highlight where the window would land
     * @param placeholder the stripe's gap
     * @param ghost the dragged window's icon
     * @param cursor where to draw the ghost, centred
     */
    void show(Rectangle highlight, Rectangle placeholder, Icon ghost, Point cursor) {
        this.highlight = highlight == null ? null : new Rectangle(highlight);
        this.placeholder = placeholder == null ? null : new Rectangle(placeholder);
        this.ghost = ghost;
        this.cursor = cursor == null ? null : new Point(cursor);
        repaint();
    }

    void clear() { show(null, null, null, null); }

    Rectangle highlight() { return highlight == null ? null : new Rectangle(highlight); }

    Rectangle placeholder() { return placeholder == null ? null : new Rectangle(placeholder); }

    /** Events pass through to whatever is underneath. */
    @Override public boolean contains(int x, int y) { return false; }

    static Color fill() {
        Color theme = UIManager.getColor("DragAndDrop.areaBackground");
        return theme != null ? theme : withAlpha(accent(), FILL_ALPHA);
    }

    static Color border() {
        Color theme = UIManager.getColor("DragAndDrop.borderColor");
        return theme != null ? theme : withAlpha(accent(), BORDER_ALPHA);
    }

    private static Color accent() {
        Color accent = UIManager.getColor("Component.accentColor");
        return accent != null ? accent : FALLBACK_ACCENT;
    }

    private static Color withAlpha(Color color, int alpha) { return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha); }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            if (highlight != null) {
                // The fill stays inside the border, so the two translucent colours never stack.
                g.setColor(fill());
                g.fillRect(highlight.x + 1, highlight.y + 1, highlight.width - 2, highlight.height - 2);
                g.setColor(border());
                g.drawRect(highlight.x, highlight.y, highlight.width - 1, highlight.height - 1);
            }
            if (placeholder != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double arc = UIScale.scale(ARC);
                g.setColor(fill());
                g.fill(new RoundRectangle2D.Double(placeholder.x, placeholder.y, placeholder.width, placeholder.height, arc, arc));
                g.setColor(border());
                g.draw(new RoundRectangle2D.Double(placeholder.x + 0.5, placeholder.y + 0.5, placeholder.width - 1, placeholder.height - 1, arc, arc));
            }
            if (ghost != null && cursor != null)
                ghost.paintIcon(this, g, cursor.x - ghost.getIconWidth() / 2, cursor.y - ghost.getIconHeight() / 2);
        } finally { g.dispose(); }
    }
}
