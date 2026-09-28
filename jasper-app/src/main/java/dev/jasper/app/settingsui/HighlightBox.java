package dev.jasper.app.settingsui;

import com.formdev.flatlaf.util.UIScale;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** Holds a row's label and paints a rounded search-match background behind it while highlighted. */
final class HighlightBox extends JPanel {
    private boolean highlighted;
    private final JComponent child;
    private Color normal;

    HighlightBox(JComponent child) {
        super(new BorderLayout());
        this.child = child;
        setOpaque(false);
        child.setOpaque(false);
        add(child, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
    }

    boolean highlighted() { return highlighted; }

    /** Highlights the label; its text turns dark so it stays readable on the light match colour in every theme. */
    void setHighlighted(boolean value) {
        if (highlighted == value) return;
        highlighted = value;
        if (value) { normal = child.getForeground(); child.setForeground(new Color(0x1e, 0x1f, 0x22)); }
        else child.setForeground(normal);
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        if (highlighted) {
            Color color = UIManager.getColor("SearchMatch.startBackground");
            if (color == null) color = UIManager.getColor("TextComponent.selectionBackgroundInactive");
            if (color == null) color = new Color(255, 214, 102);
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(color);
                int arc = UIScale.scale(8);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            } finally { g.dispose(); }
        }
        super.paintComponent(graphics);
    }
}
