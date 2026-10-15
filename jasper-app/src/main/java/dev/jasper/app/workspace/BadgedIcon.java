package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;

import dev.jasper.app.contributions.PanelBadge;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;

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
        // Badges sit on the rail (surface.app); the kit holds them to the indicator floor there.
        dev.jasper.ui.swing.ChromePainter.installed().badge(graphics, x, y, getIconWidth(), ThemeZone.theme(null), tone == PanelBadge.Tone.ERROR);
    }
}
