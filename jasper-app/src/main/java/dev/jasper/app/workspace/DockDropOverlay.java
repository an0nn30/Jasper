package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * The drag guides, on the layered pane's DRAG_LAYER: a translucent highlight with a 1px border where the dragged tool
 * window would land (for a second slot, always that slot's half; see {@link dev.jasper.app.dock.DockGeometry#landing}),
 * a highlighted rounded square in the stripe's gap, and the window's icon at the cursor. It takes no mouse input.
 * Colours: {@code DragAndDrop.areaBackground} and {@code DragAndDrop.borderColor} when the theme defines them,
 * otherwise {@code Component.accentColor} at 25 % for the fill and 60 % for the border. Both the highlight and the
 * placeholder draw their fill inside their 1px border, so the translucent colours never stack. {@link #show} repaints
 * only the regions of the guides that changed, never the whole window under this transparent component. EDT only.
 */
final class DockDropOverlay extends JComponent {
    private static final int FILL_ALPHA = 64, BORDER_ALPHA = 153, ARC = 10;
    /**
     * A margin, in pixels, for pipeline rounding when repainting the placeholder and the ghost. The antialiased stroke
     * stays inside its rectangle in the software pipeline.
     */
    private static final int BLEED = 1;
    /** IntelliJ's accent, for a theme without an accent colour. */
    private static final Color FALLBACK_ACCENT = new Color(0x3574f0);

    private Rectangle highlight;
    private Rectangle placeholder;
    private Icon ghost;
    private Point cursor;

    DockDropOverlay() { setOpaque(false); }

    /**
     * Shows the guides; each may be null. Repaints only what changed: for each guide that differs from the one shown
     * before, the union of its old and new bounds (the placeholder's and the ghost's grown by a pixel of margin
     * for pipeline rounding), and nothing at all when no guide changed.
     *
     * @param highlight where the window would land
     * @param placeholder the stripe's gap
     * @param ghost the dragged window's icon
     * @param cursor where to draw the ghost, centred
     */
    void show(Rectangle highlight, Rectangle placeholder, Icon ghost, Point cursor) {
        Rectangle oldHighlight = this.highlight, oldPlaceholder = this.placeholder, oldGhost = ghostBounds();
        boolean ghostChanged = !Objects.equals(this.ghost, ghost) || !Objects.equals(this.cursor, cursor);
        this.highlight = highlight == null ? null : new Rectangle(highlight);
        this.placeholder = placeholder == null ? null : new Rectangle(placeholder);
        this.ghost = ghost;
        this.cursor = cursor == null ? null : new Point(cursor);
        if (!Objects.equals(oldHighlight, this.highlight)) repaintBetween(oldHighlight, this.highlight, 0);
        if (!Objects.equals(oldPlaceholder, this.placeholder)) repaintBetween(oldPlaceholder, this.placeholder, BLEED);
        if (ghostChanged) repaintBetween(oldGhost, ghostBounds(), BLEED);
    }

    /** Repaints the union of the two bounds, either of which may be null, grown by {@code bleed} on every side. */
    private void repaintBetween(Rectangle before, Rectangle after, int bleed) {
        Rectangle dirty = before == null ? after : after == null ? before : before.union(after);
        if (dirty != null) repaint(dirty.x - bleed, dirty.y - bleed, dirty.width + 2 * bleed, dirty.height + 2 * bleed);
    }

    /** Where the ghost paints: its icon centred on the cursor; null when it has no icon or no cursor. */
    private Rectangle ghostBounds() {
        if (ghost == null || cursor == null) return null;
        return new Rectangle(cursor.x - ghost.getIconWidth() / 2, cursor.y - ghost.getIconHeight() / 2, ghost.getIconWidth(), ghost.getIconHeight());
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
                // The fill sits inside the border, so the two never stack. The border is stroked 0.5px in, so its
                // inner edge has corner diameter arc - 1: the fill uses it too, and the two are concentric.
                double inner = Math.max(0, arc - 1);
                g.setColor(fill());
                g.fill(new RoundRectangle2D.Double(placeholder.x + 1, placeholder.y + 1, placeholder.width - 2, placeholder.height - 2, inner, inner));
                g.setColor(border());
                g.draw(new RoundRectangle2D.Double(placeholder.x + 0.5, placeholder.y + 0.5, placeholder.width - 1, placeholder.height - 1, arc, arc));
            }
            Rectangle ghostBounds = ghostBounds();
            if (ghostBounds != null) ghost.paintIcon(this, g, ghostBounds.x, ghostBounds.y);
        } finally { g.dispose(); }
    }
}
