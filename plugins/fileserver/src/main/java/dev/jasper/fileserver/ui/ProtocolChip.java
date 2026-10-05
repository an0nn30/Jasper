package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Protocol;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JLabel;

/** Compact protocol label, with a distinct tint for every supported server type. */
final class ProtocolChip extends JLabel {
    ProtocolChip(Protocol protocol, Font font, Color background) {
        super(protocol.label());
        setFont(font.deriveFont(Font.PLAIN, Math.max(10f, font.getSize2D() - 2)));
        boolean dark = background.getRed() * .299 + background.getGreen() * .587 + background.getBlue() * .114 < 128;
        setForeground(switch (protocol) {
            case HTTP -> new Color(dark ? 0x4989e8 : 0x2467de);
            case SFTP -> new Color(dark ? 0x4dac80 : 0x23784f);
            case FTP -> new Color(dark ? 0xd49a46 : 0x925a0a);
            case TFTP -> new Color(dark ? 0xa274dc : 0x7043ae);
        });
        setBorder(BorderFactory.createEmptyBorder(PanelStyle.scale(this, 1), PanelStyle.scale(this, 4), PanelStyle.scale(this, 1), PanelStyle.scale(this, 4)));
    }
    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color foreground = getForeground();
            g.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 30));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), PanelStyle.scale(this, 6), PanelStyle.scale(this, 6));
        } finally { g.dispose(); }
        super.paintComponent(graphics);
    }
}
