package dev.jasper.remote.ui;

import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/** Small presentation helpers shared by Remote's rail panels. No connection state is held here. */
public final class PanelStyle {
    private PanelStyle() { }

    /** Uses the host's UI font scale; the graphics transform already supplies device scaling. */
    public static int scale(Component component, int value) {
        Font font = UIManager.getFont("Label.font");
        return Math.max(1, Math.round(value * (font == null ? 1f : font.getSize2D() / 13f)));
    }

    public static void button(AbstractButton button) {
        button.putClientProperty("FlatLaf.style", "arc: 10");
        button.putClientProperty("JButton.minimumWidth", 0);
        button.putClientProperty("JButton.minimumHeight", 28);
        button.setMargin(new java.awt.Insets(5, 12, 5, 12));
    }

    /** The reference's blue for explicit actions and enabled switches. */
    public static Color accent(Component component) {
        Color background = WorkspacePalette.current(component).background();
        return background.getRed() * .299 + background.getGreen() * .587 + background.getBlue() * .114 < 128
            ? new Color(0x3274f6) : new Color(0x2467de);
    }

    /** A local primary action, without changing the window's default Enter binding. */
    public static JButton primaryButton(String text) {
        var button = new JButton(text) {
            @Override public Color getForeground() {
                return Color.WHITE;
            }
            @Override protected void paintComponent(Graphics graphics) {
                var g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    var palette = WorkspacePalette.current(this);
                    Color color = isEnabled() ? accent(this) : palette.border();
                    if (getModel().isPressed()) color = color.darker();
                    else if (getModel().isRollover()) color = color.brighter();
                    g.setColor(color);
                    int inset = scale(this, 3), arc = scale(this, 10);
                    g.fillRoundRect(inset, inset, getWidth() - 2 * inset, getHeight() - 2 * inset, arc, arc);
                    if (isFocusPainted() && hasFocus()) {
                        g.setColor(accent(this));
                        g.setStroke(new java.awt.BasicStroke(Math.max(1f, scale(this, 1))));
                        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc + inset, arc + inset);
                    }
                } finally { g.dispose(); }
                super.paintComponent(graphics);
            }
        };
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button(button);
        return button;
    }

    public static JLabel iconTile(Icon icon) { return iconTile(icon, 40); }

    public static JLabel iconTile(Icon icon, int logicalSize) {
        return new JLabel(icon, SwingConstants.CENTER) {
            @Override public Dimension getPreferredSize() {
                int size = scale(this, logicalSize);
                return new Dimension(size, size);
            }
            @Override protected void paintComponent(Graphics graphics) {
                var g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    var palette = WorkspacePalette.current(this);
                    Color base = palette.background(), tint = palette.textMuted();
                    g.setColor(new Color((base.getRed() * 9 + tint.getRed()) / 10,
                        (base.getGreen() * 9 + tint.getGreen()) / 10, (base.getBlue() * 9 + tint.getBlue()) / 10));
                    int arc = scale(this, 12);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                } finally { g.dispose(); }
                super.paintComponent(graphics);
            }
        };
    }

    /** A centered, width-aware empty state. Its action remains a normal keyboard-accessible Swing button. */
    public static JPanel emptyState(Icon icon, String title, String description, JButton action) {
        var tile = iconTile(icon);
        var heading = new JLabel(title, SwingConstants.CENTER) {
            @Override public void updateUI() {
                super.updateUI();
                Font font = UIManager.getFont("Label.font");
                if (font != null) setFont(font.deriveFont(Font.BOLD));
            }
            @Override public Color getForeground() { return WorkspacePalette.current(this).text(); }
        };
        heading.putClientProperty("html.disable", true);
        var body = new JLabel() {
            @Override public Color getForeground() { return WorkspacePalette.current(this).textMuted(); }
        };
        button(action);
        var panel = new JPanel(null) {
            private int textWidth = -1;
            @Override public Dimension getPreferredSize() { return new Dimension(scale(this, 240), scale(this, 220)); }
            @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }
            @Override public void doLayout() {
                int inset = scale(this, 16);
                int width = Math.max(1, Math.min(getWidth() - inset * 2, scale(this, 280)));
                if (textWidth != width) {
                    textWidth = width;
                    body.setText("<html><center>" + description + "</center></html>");
                }
                int iconSize = tile.getPreferredSize().height;
                int headingHeight = heading.getPreferredSize().height;
                var textView = (javax.swing.text.View) body.getClientProperty("html");
                textView.setSize(width, 0);
                int bodyHeight = (int) Math.ceil(textView.getPreferredSpan(javax.swing.text.View.Y_AXIS));
                Dimension buttonSize = action.getPreferredSize();
                int gap = scale(this, 12), smallGap = scale(this, 8), buttonGap = scale(this, 16);
                int height = iconSize + gap + headingHeight + smallGap + bodyHeight + buttonGap + buttonSize.height;
                int y = Math.max(inset, (getHeight() - height) / 2);
                tile.setBounds((getWidth() - iconSize) / 2, y, iconSize, iconSize);
                y += iconSize + gap;
                heading.setBounds(inset, y, Math.max(0, getWidth() - inset * 2), headingHeight);
                y += headingHeight + smallGap;
                body.setBounds((getWidth() - width) / 2, y, width, bodyHeight);
                y += bodyHeight + buttonGap;
                int buttonWidth = Math.min(buttonSize.width, Math.max(0, getWidth() - inset * 2));
                action.setBounds((getWidth() - buttonWidth) / 2, y, buttonWidth, buttonSize.height);
            }
            @Override public void updateUI() { super.updateUI(); textWidth = -1; }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder());
        panel.add(tile); panel.add(heading); panel.add(body); panel.add(action);
        return panel;
    }
}
