package dev.jasper.app.notificationui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.View;

/**
 * Read-only plain text that wraps to the width its row gives it and never asks for more (the same approach as the
 * Settings dialog's wrapping text). A text area never interprets markup. EDT only.
 */
final class WrappedText extends JTextArea {
    private static final int NOMINAL_WIDTH = 200;
    private final String colorKey;
    private boolean bold;

    WrappedText(String text, String colorKey, boolean bold) {
        super(text);
        this.colorKey = colorKey;
        this.bold = bold;
        setEditable(false);
        setFocusable(false);
        setOpaque(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        restyle();
    }

    void setBold(boolean value) {
        if (bold == value) return;
        bold = value;
        restyle();
        revalidate();
    }

    @Override public void updateUI() {
        super.updateUI();
        // JTextArea's constructor calls this before the fields exist; the constructor styles the text itself.
        if (colorKey != null) restyle();
    }

    private void restyle() {
        setBorder(BorderFactory.createEmptyBorder());
        Font label = UIManager.getFont("Label.font");
        // An explicit style both ways: a look and feel whose label font is already bold (Metal) must not show read text bold.
        if (label != null) setFont(label.deriveFont(bold ? Font.BOLD : Font.PLAIN));
        setForeground(NotificationStyle.color(colorKey, Color.BLACK));
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(NOMINAL_WIDTH, heightFor(getWidth() > 0 ? getWidth() : NOMINAL_WIDTH));
    }

    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

    private int heightFor(int width) {
        Insets insets = getInsets();
        View root = getUI().getRootView(this);
        root.setSize(Math.max(1, width - insets.left - insets.right), Integer.MAX_VALUE);
        return (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom;
    }

    @Override public void setBounds(int x, int y, int width, int height) {
        int before = getWidth();
        super.setBounds(x, y, width, height);
        // The height follows the width, which the layout has only just decided.
        if (width != before) SwingUtilities.invokeLater(this::revalidate);
    }
}
