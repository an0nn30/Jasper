package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.PanelBadge;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * A stripe icon with an unread dot at its top-right corner, ringed in the stripe's colour so it reads on any artwork.
 * The badge is painted inside the base icon's bounds, and the size equals the base icon's, so a stripe button can
 * change its badge without a relayout.
 */
final class BadgedIcon implements Icon {
    private final Icon base;
    private final PanelBadge.Tone tone;

    BadgedIcon(Icon base, PanelBadge.Tone tone) {
        this.base = base;
        this.tone = tone;
    }

    Icon base() { return base; }
    PanelBadge.Tone tone() { return tone; }

    @Override public int getIconWidth() { return base.getIconWidth(); }
    @Override public int getIconHeight() { return base.getIconHeight(); }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        base.paintIcon(component, graphics, x, y);
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int dot = UIScale.scale(6), ring = UIScale.scale(1);
            int left = x + getIconWidth() - dot;
            Color rail = UIManager.getColor("Jasper.titleBackground");
            if (rail != null) {
                g.setColor(rail);
                g.fillOval(left - ring, y - ring, dot + 2 * ring, dot + 2 * ring);
            }
            Color color = UIManager.getColor(tone == PanelBadge.Tone.ERROR ? "Jasper.railBadgeError" : "Jasper.railBadgeAccent");
            g.setColor(color != null ? color : Color.RED);
            g.fillOval(left, y, dot, dot);
        } finally { g.dispose(); }
    }
}
