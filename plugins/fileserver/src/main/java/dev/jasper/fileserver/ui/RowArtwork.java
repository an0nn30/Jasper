package dev.jasper.fileserver.ui;

import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/** Small panel artwork. Actual actions are provided by the list and the window header. */
final class RowArtwork implements Icon {
    enum Kind { ADD, FOLDER, COPY, SWITCH }
    private final Kind kind;
    private final boolean on, pending;
    RowArtwork(Kind kind, boolean on, boolean pending) { this.kind = kind; this.on = on; this.pending = pending; }
    @Override public int getIconWidth() { return PanelStyle.scale(null, kind == Kind.SWITCH ? 28 : 16); }
    @Override public int getIconHeight() { return PanelStyle.scale(null, 16); }
    @Override public void paintIcon(Component owner, Graphics graphics, int x, int y) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(x, y);
            double scale = getIconHeight() / 16d;
            g.scale(scale, scale);
            var p = WorkspacePalette.current(owner);
            Color blue = PanelStyle.accent(owner);
            g.setColor(p.textMuted());
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (kind) {
                case ADD -> { g.drawLine(3, 8, 13, 8); g.drawLine(8, 3, 8, 13); }
                case COPY -> {
                    if (!owner.isEnabled()) g.setColor(p.border());
                    g.drawRoundRect(5, 5, 8, 9, 2, 2);
                    g.drawLine(3, 11, 2, 11); g.drawLine(2, 11, 2, 2); g.drawLine(2, 2, 10, 2); g.drawLine(10, 2, 10, 3);
                }
                case FOLDER -> {
                    Color background = p.background();
                    boolean dark = background.getRed() * .299 + background.getGreen() * .587 + background.getBlue() * .114 < 128;
                    g.setColor(new Color(dark ? 0x5e9bed : 0x397ac4));
                    var folder = new Path2D.Float();
                    folder.moveTo(2, 3); folder.lineTo(6.3, 3); folder.lineTo(8.2, 5); folder.lineTo(14, 5);
                    folder.quadTo(15, 5, 15, 6); folder.lineTo(15, 12); folder.quadTo(15, 13, 14, 13);
                    folder.lineTo(2, 13); folder.quadTo(1, 13, 1, 12); folder.lineTo(1, 4); folder.quadTo(1, 3, 2, 3);
                    folder.closePath(); g.fill(folder);
                }
                case SWITCH -> {
                    g.setColor(pending ? p.warning() : on ? blue : p.border());
                    g.fillRoundRect(0, 0, 28, 16, 16, 16);
                    g.setColor(Color.WHITE);
                    g.fillOval(pending ? 8 : on ? 14 : 2, 2, 12, 12);
                }
            }
        } finally { g.dispose(); }
    }
}
