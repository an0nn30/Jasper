package dev.jasper.ui.swing;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/** The classic look's drawn glyphs (DESIGN_LANGUAGE §6): a check mark, a dash and filled triangles, in a theme colour. */
final class ClassicGlyphs {
    private ClassicGlyphs() {}

    /** A check mark filling the {@code size} square at ({@code x}, {@code y}). */
    static void check(Graphics2D g, double x, double y, double size, Color color) {
        double u = size / 13;
        var path = new Path2D.Double();
        path.moveTo(x + 3.25 * u, y + 6.75 * u); path.lineTo(x + 5.5 * u, y + 9 * u); path.lineTo(x + 9.75 * u, y + 4.25 * u);
        stroke(g, path, 1.5 * u, color);
    }

    /** A dash across the {@code size} square at ({@code x}, {@code y}): a box whose state is not yet confirmed. */
    static void dash(Graphics2D g, double x, double y, double size, Color color) {
        double u = size / 13;
        stroke(g, new Line2D.Double(x + 3.5 * u, y + 6.5 * u, x + 9.5 * u, y + 6.5 * u), 1.5 * u, color);
    }

    /** A triangle {@code width} wide pointing down, centred on {@code cx} with its top at {@code y}; {@code width} × 0.8 tall. */
    static void down(Graphics2D g, double cx, double y, double width, Color color) {
        fill(g, color, cx - width / 2, y, cx + width / 2, y, cx, y + width * .8);
    }

    /** A triangle {@code width} wide pointing up, centred on {@code cx} with its top at {@code y}. */
    static void up(Graphics2D g, double cx, double y, double width, Color color) {
        fill(g, color, cx - width / 2, y + width * .8, cx + width / 2, y + width * .8, cx, y);
    }

    /** A triangle {@code width} tall pointing right, its left edge at {@code x}, centred on {@code cy}. */
    static void right(Graphics2D g, double x, double cy, double width, Color color) {
        fill(g, color, x, cy - width / 2, x, cy + width / 2, x + width * .8, cy);
    }

    private static void fill(Graphics2D g, Color color, double x1, double y1, double x2, double y2, double x3, double y3) {
        var path = new Path2D.Double();
        path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.closePath();
        var s = (Graphics2D) g.create();
        try { s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); s.setColor(color); s.fill(path); }
        finally { s.dispose(); }
    }

    private static void stroke(Graphics2D g, Shape shape, double width, Color color) {
        var s = (Graphics2D) g.create();
        try {
            s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            s.setColor(color); s.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); s.draw(shape);
        } finally { s.dispose(); }
    }
}
