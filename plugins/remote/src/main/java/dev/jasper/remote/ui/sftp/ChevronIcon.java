package dev.jasper.remote.ui.sftp;

import java.awt.*;
import javax.swing.*;

/** A 16-pixel ‹ or › drawn in the button's text colour, dimmed when disabled; follows every theme without artwork. */
final class ChevronIcon implements Icon {
    enum Direction { LEFT, RIGHT }
    private static final int SIZE = 16;
    private final Direction direction;

    ChevronIcon(Direction direction) { this.direction = direction; }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color disabled = UIManager.getColor("Label.disabledForeground");
            Color enabled = component == null || component.getForeground() == null ? Color.DARK_GRAY : component.getForeground();
            g.setColor(component != null && !component.isEnabled() ? (disabled == null ? Color.GRAY : disabled) : enabled);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int left = x + 6, right = x + 10, top = y + 4, middle = y + 8, bottom = y + 12;
            if (direction == Direction.LEFT) g.drawPolyline(new int[] {right, left, right}, new int[] {top, middle, bottom}, 3);
            else g.drawPolyline(new int[] {left, right, left}, new int[] {top, middle, bottom}, 3);
        } finally {
            g.dispose();
        }
    }

    @Override public int getIconWidth() { return SIZE; }
    @Override public int getIconHeight() { return SIZE; }
}
