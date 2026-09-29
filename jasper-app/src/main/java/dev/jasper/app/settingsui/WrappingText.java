package dev.jasper.app.settingsui;

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
 * Read-only text that wraps to the width its container gives it and never asks for more, so a long description cannot
 * widen a settings page. EDT only.
 */
final class WrappingText extends JTextArea {
    /** The width the text asks for before it has been laid out; the page's layout stretches it to the row. */
    private static final int NOMINAL_WIDTH = 240;

    private final String colorKey;
    private final Color fallback;
    private final float smaller;

    /**
     * @param colorKey the UI colour for the text
     * @param fallback the colour when the look and feel has none under that key
     * @param smaller points below the label font
     */
    WrappingText(String colorKey, Color fallback, float smaller) {
        this.colorKey = colorKey;
        this.fallback = fallback;
        this.smaller = smaller;
        setEditable(false);
        setFocusable(false);
        setOpaque(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        restyle();
    }

    @Override public void updateUI() {
        super.updateUI();
        // Called by JTextArea's constructor before the fields are set; the constructor styles the text itself.
        if (colorKey != null) restyle();
    }

    private void restyle() {
        setBorder(BorderFactory.createEmptyBorder());
        Font label = UIManager.getFont("Label.font");
        if (label != null) setFont(label.deriveFont(label.getSize2D() - smaller));
        Color color = UIManager.getColor(colorKey);
        setForeground(color != null ? color : fallback);
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(NOMINAL_WIDTH, heightFor(getWidth() > 0 ? getWidth() : NOMINAL_WIDTH));
    }

    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    /** The height the text needs when wrapped at {@code width}. */
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
