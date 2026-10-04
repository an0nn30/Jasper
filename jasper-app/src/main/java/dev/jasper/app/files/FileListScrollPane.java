package dev.jasper.app.files;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneLayout;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** A file list with a transient thumb over the viewport; revealing it never moves the columns. EDT only. */
final class FileListScrollPane extends JScrollPane {
    private boolean scrolling;
    private int previousValue;
    private final Timer idle = new Timer(1000, event -> {
        if (getVerticalScrollBar().getValueIsAdjusting()) ((Timer) event.getSource()).restart();
        else stop();
    });

    FileListScrollPane(Component view) {
        super(view);
        idle.setRepeats(false);
        setLayout(new ScrollPaneLayout() {
            @Override public void layoutContainer(Container parent) {
                super.layoutContainer(parent);
                if (!vsb.isVisible()) return;
                Rectangle bounds = viewport.getBounds();
                int reserved = vsb.getWidth();
                boolean leftToRight = parent.getComponentOrientation().isLeftToRight();
                if (!leftToRight) bounds.x -= reserved;
                bounds.width += reserved;
                viewport.setBounds(bounds);
                if (colHead != null) colHead.setBounds(bounds.x, colHead.getY(), bounds.width, colHead.getHeight());
                int width = UIScale.scale(10), inset = UIScale.scale(2);
                vsb.setBounds(leftToRight ? bounds.x + bounds.width - width - inset : bounds.x + inset,
                    bounds.y + inset, width, Math.max(0, bounds.height - 2 * inset));
            }
        });
        setComponentZOrder(getVerticalScrollBar(), 0);
        // A blit could copy an old overlay thumb along with the table pixels.
        getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        getVerticalScrollBar().addAdjustmentListener(event -> {
            int value = event.getValue();
            if (value != previousValue && isShowing()) {
                scrolling = true;
                repaint();
            }
            previousValue = value;
            if (scrolling) idle.restart();
        });
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) stop();
        });
    }

    @Override public boolean isOptimizedDrawingEnabled() { return false; }
    @Override protected boolean isPaintingOrigin() { return true; }

    @Override public JScrollBar createVerticalScrollBar() {
        return new ScrollBar(JScrollBar.VERTICAL) {
            @Override public void updateUI() {
                setUI(new BasicScrollBarUI() {
                    @Override protected JButton createDecreaseButton(int orientation) { return emptyButton(); }
                    @Override protected JButton createIncreaseButton(int orientation) { return emptyButton(); }
                    @Override protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) { }
                    @Override protected void paintThumb(Graphics graphics, JComponent c, Rectangle bounds) {
                        if (bounds.isEmpty() || !c.isEnabled()) return;
                        var g = (Graphics2D) graphics.create();
                        try {
                            Color color = UIManager.getColor("ScrollBar.thumb");
                            if (color == null) color = UIManager.getColor("Label.disabledForeground");
                            if (color == null) color = Color.GRAY;
                            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 190));
                            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            int inset = UIScale.scale(2);
                            g.fillRoundRect(bounds.x + inset, bounds.y, Math.max(0, bounds.width - 2 * inset),
                                bounds.height, UIScale.scale(6), UIScale.scale(6));
                        } finally { g.dispose(); }
                    }
                });
                setOpaque(false);
            }
            @Override public void paint(Graphics graphics) { if (scrolling) super.paint(graphics); }
            @Override public boolean contains(int x, int y) { return scrolling && super.contains(x, y); }
        };
    }

    private static JButton emptyButton() {
        var button = new JButton();
        button.setPreferredSize(new Dimension());
        button.setMinimumSize(new Dimension());
        button.setMaximumSize(new Dimension());
        button.setFocusable(false);
        return button;
    }

    void stop() {
        idle.stop();
        scrolling = false;
        repaint();
    }

    @Override public void removeNotify() {
        stop();
        super.removeNotify();
    }
}
