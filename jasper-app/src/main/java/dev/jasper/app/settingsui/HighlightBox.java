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

    HighlightBox(JComponent child) {
        super(new BorderLayout());
        setOpaque(false);
        child.setOpaque(false);
        add(child, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
    }

    boolean highlighted() { return highlighted; }

    void setHighlighted(boolean value) {
        if (highlighted == value) return;
        highlighted = value;
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
