package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * The OVERLAY scroll policy: a transient thumb drawn over the rows only while they scroll, which takes no width, so
 * revealing it never moves the columns. A held thumb drag stays visible; the thumb hides after the idle delay and when
 * the pane leaves the screen. EDT only.
 */
final class OverlayScrollPane extends JScrollPane {
    private final KitContext context;
    private boolean scrolling;
    private int previousValue;
    private final Timer idle;

    OverlayScrollPane(KitContext context) {
        this.context = context;
        idle = new Timer(context.metrics().value(DesignMetrics.Role.OVERLAY_SCROLLBAR_IDLE_MILLIS), event -> {
            if (getVerticalScrollBar().getValueIsAdjusting()) ((Timer) event.getSource()).restart();
            else stop();
        });
        idle.setRepeats(false);
        setVerticalScrollBar(new Thumb());
        setLayout(new ScrollPaneLayout() {
            @Override public void layoutContainer(Container parent) {
                super.layoutContainer(parent);
                if (vsb == null || !vsb.isVisible()) return;
                Rectangle bounds = viewport.getBounds();
                int reserved = vsb.getWidth();
                boolean leftToRight = parent.getComponentOrientation().isLeftToRight();
                if (!leftToRight) bounds.x -= reserved;
                bounds.width += reserved;
                viewport.setBounds(bounds);
                if (colHead != null) colHead.setBounds(bounds.x, colHead.getY(), bounds.width, colHead.getHeight());
                int width = ThemePainting.metric(OverlayScrollPane.this.context, DesignMetrics.Role.OVERLAY_SCROLLBAR_WIDTH);
                int inset = ThemePainting.metric(OverlayScrollPane.this.context, DesignMetrics.Role.OVERLAY_SCROLLBAR_INSET);
                vsb.setBounds(leftToRight ? bounds.x + bounds.width - width - inset : bounds.x + inset,
                    bounds.y + inset, width, Math.max(0, bounds.height - 2 * inset));
            }
        });
        setComponentZOrder(getVerticalScrollBar(), 0);
        // A blit could copy an old overlay thumb along with the row pixels.
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

    /** Hides the thumb and cancels the idle timer. */
    void stop() {
        idle.stop();
        scrolling = false;
        repaint();
    }

    @Override public void removeNotify() {
        stop();
        super.removeNotify();
    }

    private final class Thumb extends JScrollBar {
        Thumb() { super(JScrollBar.VERTICAL); }

        @Override public void updateUI() {
            setUI(new BasicScrollBarUI() {
                @Override protected JButton createDecreaseButton(int orientation) { return empty(); }
                @Override protected JButton createIncreaseButton(int orientation) { return empty(); }
                @Override protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) { }
                @Override protected void paintThumb(Graphics graphics, JComponent c, Rectangle bounds) {
                    if (bounds.isEmpty() || !c.isEnabled()) return;
                    var g = (Graphics2D) graphics.create();
                    try {
                        Color color = ThemePainting.color(context, SemanticColor.TEXT_TERTIARY);
                        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
                            context.metrics().value(DesignMetrics.Role.OVERLAY_SCROLLBAR_ALPHA)));
                        ThemePainting.antialias(g);
                        int inset = ThemePainting.metric(context, DesignMetrics.Role.OVERLAY_SCROLLBAR_INSET);
                        int arc = ThemePainting.metric(context, DesignMetrics.Role.OVERLAY_SCROLLBAR_ARC);
                        g.fillRoundRect(bounds.x + inset, bounds.y, Math.max(0, bounds.width - 2 * inset), bounds.height, arc, arc);
                    } finally { g.dispose(); }
                }
            });
            setOpaque(false);
        }

        @Override public void paint(Graphics graphics) { if (scrolling) super.paint(graphics); }
        @Override public boolean contains(int x, int y) { return scrolling && super.contains(x, y); }
    }

    private static JButton empty() {
        var button = new JButton();
        button.setPreferredSize(new Dimension());
        button.setMinimumSize(new Dimension());
        button.setMaximumSize(new Dimension());
        button.setFocusable(false);
        return button;
    }
}
