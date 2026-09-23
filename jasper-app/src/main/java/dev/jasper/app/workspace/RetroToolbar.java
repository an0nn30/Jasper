package dev.jasper.app.workspace;

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JToolBar;

/** Metal toolbar layout and controls on a solid background, with app label preferences and icon-only compaction. */
final class RetroToolbar extends JToolBar {
    /** Flat classic launcher button, with captions centered below its artwork. */
    static void styleButton(JButton button) {
        button.setMargin(new java.awt.Insets(3, 5, 3, 5));
        button.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 5, 3, 5));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        button.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        button.setIconTextGap(3);
        var font = javax.swing.UIManager.getFont("Button.font");
        button.setFont(font.deriveFont(java.awt.Font.PLAIN, Math.max(1f, font.getSize2D() - 1f)));
    }

    private boolean layingOut;
    private boolean labels = true;
    void labels(boolean value) { labels = value; revalidate(); }
    @Override public void doLayout() {
        if (layingOut) { super.doLayout(); return; }
        layingOut = true;
        try {
            texts(labels);
            if (labels && super.getPreferredSize().width > getWidth()) texts(false);
            super.doLayout();
        } finally { layingOut = false; }
    }
    private void texts(boolean visible) {
        for (Component child : getComponents()) if (child instanceof JButton button)
            button.setText(visible ? (String) button.getClientProperty("label") : null);
        // BoxLayout caches size requirements; text changes during layout must invalidate them now.
        if (getLayout() instanceof java.awt.LayoutManager2 layout) layout.invalidateLayout(this);
    }
    @Override public Dimension getMinimumSize() {
        var edge = getInsets();
        int width = edge.left + edge.right, height = 0;
        for (Component child : getComponents()) {
            if (!child.isVisible()) continue;
            Dimension size = child.getMinimumSize();
            if (child instanceof JButton button) {
                var insets = button.getInsets();
                width += insets.left + insets.right + (button.getIcon() == null ? 0 : button.getIcon().getIconWidth());
            } else width += size.width;
            height = Math.max(height, size.height);
        }
        return new Dimension(width, height + edge.top + edge.bottom);
    }
}
