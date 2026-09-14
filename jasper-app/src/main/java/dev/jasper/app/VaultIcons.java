package dev.jasper.app;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Icon;

/** Sixteen-pixel outline icons painted in the host component's foreground; no bundled artwork. */
final class VaultIcons {
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();

    private VaultIcons() {}

    static Icon icon(String name) {
        return CACHE.computeIfAbsent(name, VaultIcons::create);
    }

    private static Icon create(String name) {
        return new Icon() {
            @Override public int getIconWidth() { return 16; }
            @Override public int getIconHeight() { return 16; }
            @Override public void paintIcon(Component c, Graphics raw, int x, int y) {
                Graphics2D g = (Graphics2D) raw.create();
                try {
                    g.translate(x, y);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(c == null ? Color.DARK_GRAY : c.getForeground());
                    g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    paint(name, g);
                } finally { g.dispose(); }
            }
        };
    }

    private static void paint(String name, Graphics2D g) {
        switch (name) {
            case "lock" -> { g.drawRoundRect(3, 7, 10, 8, 2, 2); g.drawArc(5, 1, 6, 10, 0, 180); g.drawLine(8, 10, 8, 12); }
            case "unlock" -> { g.drawRoundRect(3, 7, 10, 8, 2, 2); g.drawArc(7, 1, 6, 10, 0, 180); g.drawLine(8, 10, 8, 12); }
            case "login" -> { g.drawOval(5, 1, 6, 6); g.drawArc(3, 9, 10, 9, 0, 180); }
            case "key" -> { g.drawOval(8, 1, 6, 6); g.drawLine(9, 6, 2, 13); g.drawLine(3, 12, 5, 14); g.drawLine(5, 10, 7, 12); }
            case "eye" -> { Path2D p = new Path2D.Float(); p.moveTo(1, 8); p.quadTo(8, -1, 15, 8); p.quadTo(8, 17, 1, 8); g.draw(p); g.drawOval(6, 6, 4, 4); }
            case "copy" -> { g.drawRoundRect(5, 5, 9, 10, 2, 2); g.drawPolyline(new int[]{11, 2, 2}, new int[]{2, 2, 12}, 3); }
            case "import" -> { g.drawLine(8, 2, 8, 11); g.drawPolyline(new int[]{4, 8, 12}, new int[]{6, 2, 6}, 3); g.drawPolyline(new int[]{2, 2, 14, 14}, new int[]{11, 14, 14, 11}, 4); }
            case "settings" -> {
                g.drawOval(3, 3, 10, 10); g.drawOval(6, 6, 4, 4);
                for (int a = 0; a < 8; a++) {
                    double t = a * Math.PI / 4;
                    g.drawLine((int) Math.round(8 + 5 * Math.cos(t)), (int) Math.round(8 + 5 * Math.sin(t)),
                        (int) Math.round(8 + 7 * Math.cos(t)), (int) Math.round(8 + 7 * Math.sin(t)));
                }
            }
            case "save" -> { g.drawRect(2, 2, 12, 12); g.drawRect(5, 2, 6, 4); g.drawRect(5, 9, 6, 5); }
            case "add" -> { g.drawLine(8, 2, 8, 14); g.drawLine(2, 8, 14, 8); }
            default -> throw new IllegalArgumentException("Unknown vault icon: " + name);
        }
    }
}
