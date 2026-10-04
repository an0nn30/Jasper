package dev.jasper.editor.ui;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * The editor's text column. In a translucent window it fills the editor background, and then the gutter's, once each
 * at the workspace opacity, replacing what lies beneath; the text area, viewports and gutter above it stay clear.
 * Java2D renders a translucent Src fill in software unless it is an unantialiased rectangle, so both are rectangles.
 */
final class GlassSurface extends JPanel {
    private final RTextScrollPane scroll;
    private double opacity = 1;
    private Color background = Color.BLACK, gutter = Color.BLACK;

    GlassSurface(RTextScrollPane scroll) {
        super(new BorderLayout());
        this.scroll = scroll;
    }

    /** Below 1 this surface paints the glass; at 1 it is an ordinary opaque panel. */
    void glass(double opacity, Color background, Color gutter) {
        this.opacity = opacity;
        this.background = background;
        this.gutter = gutter;
        setOpaque(opacity >= 1);
        repaint();
    }

    double opacity() { return opacity; }

    @Override protected void paintComponent(Graphics graphics) {
        if (opacity >= 1) { super.paintComponent(graphics); return; }
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setComposite(AlphaComposite.Src);
            g.setColor(translucent(background));
            g.fillRect(0, 0, getWidth(), getHeight());
            var header = scroll.getRowHeader();
            if (header != null && header.isVisible() && header.getParent() != null) {
                Rectangle bounds = SwingUtilities.convertRectangle(header.getParent(), header.getBounds(), this);
                g.setColor(translucent(gutter));
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        } finally {
            g.dispose();
        }
    }

    private Color translucent(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.round(255 * opacity));
    }
}
