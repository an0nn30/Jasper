package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.ui.PanelStyle;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/** Compact start/stop switch artwork; the list owns its mouse and keyboard actions. */
final class TunnelSwitch implements Icon {
    private final boolean enabled, pending;
    TunnelSwitch(boolean enabled, boolean pending) { this.enabled = enabled; this.pending = pending; }
    @Override public int getIconWidth() { return PanelStyle.scale(null, 28); }
    @Override public int getIconHeight() { return PanelStyle.scale(null, 16); }
    @Override public void paintIcon(Component owner, Graphics graphics, int x, int y) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getIconWidth(), height = getIconHeight(), inset = PanelStyle.scale(owner, 2);
            var p = WorkspacePalette.current(owner);
            Color blue = PanelStyle.accent(owner);
            g.setColor(pending ? p.warning() : enabled ? blue : p.border());
            g.fillRoundRect(x, y, width, height, height, height);
            int thumb = height - 2 * inset;
            g.setColor(Color.WHITE);
            g.fillOval(x + (pending ? (width - thumb) / 2 : enabled ? width - thumb - inset : inset), y + inset, thumb, thumb);
        } finally { g.dispose(); }
    }
}
