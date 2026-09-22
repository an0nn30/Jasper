package dev.jasper.vault.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.UIManager;

/** Small vector action icons; colors follow the host look and feel. */
final class VaultIcons implements Icon {
    enum Shape { SEARCH, ADD, EDIT, DELETE, COPY, LOCK, MORE }
    private final Shape shape;
    VaultIcons(Shape shape) { this.shape = shape; }
    @Override public int getIconWidth() { return 16; }
    @Override public int getIconHeight() { return 16; }
    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        var g = (Graphics2D) graphics.create();
        try {
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = UIManager.getColor(component.isEnabled() ? "Label.foreground" : "Label.disabledForeground");
            g.setColor(color == null ? Color.GRAY : color); g.setStroke(new BasicStroke(1.5f));
            switch (shape) {
                case SEARCH -> { g.drawOval(2, 1, 9, 9); g.drawLine(10, 10, 14, 14); }
                case ADD -> { g.fillRect(7, 2, 2, 12); g.fillRect(2, 7, 12, 2); }
                case DELETE -> g.fillRect(2, 7, 12, 2);
                case EDIT -> { g.rotate(-Math.PI / 4, 8, 8); g.fillRect(6, 2, 4, 11); g.fillPolygon(new int[]{6, 10, 8}, new int[]{14, 14, 16}, 3); }
                case COPY -> { g.drawRect(2, 2, 9, 10); g.fillRect(5, 5, 9, 10); g.setColor(UIManager.getColor("Panel.background")); g.drawLine(7, 8, 12, 8); g.drawLine(7, 11, 12, 11); }
                case LOCK -> { g.drawArc(5, 1, 6, 9, 0, 180); g.fillRect(4, 6, 8, 9); }
                case MORE -> { for (int at : new int[]{2, 7, 12}) g.fillOval(at, 7, 2, 2); }
            }
        } finally { g.dispose(); }
    }
}
