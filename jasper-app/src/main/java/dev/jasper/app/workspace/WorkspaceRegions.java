package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import com.formdev.flatlaf.ui.FlatEmptyBorder;
import dev.jasper.app.contributions.PanelRegion;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.UIManager;

/**
 * The terminal column (the tab strip over the deck) with up to three panel regions around it. BOTTOM
 * wraps the center, RIGHT wraps that, LEFT wraps that, so the bottom region spans only the terminal
 * and the side regions run the full height beside the tabs. With no region shown the
 * center retains its rounded frame and exterior gutter. EDT only.
 */
final class WorkspaceRegions extends JPanel {
    static final int DEFAULT_SIDE = 260, DEFAULT_BOTTOM = 200, MINIMUM = 120;

    /** A shared frame around plugin-owned content; gutters belong to the workspace chrome. */
    private static final class Host extends JPanel {
        private static final int GUTTER = 6, ARC = 16;

        private final boolean roundBottom;

        Host() { this(true); }
        Host(boolean roundBottom) {
            super(new BorderLayout());
            this.roundBottom = roundBottom;
            gutters(GUTTER, GUTTER, GUTTER, GUTTER);
            if (roundBottom) setMinimumSize(new Dimension(UIScale.scale(MINIMUM), UIScale.scale(MINIMUM)));
        }

        void gutters(int top, int left, int bottom, int right) {
            // An app-owned border keeps the gutter through look-and-feel switches.
            setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(top, left, bottom, right), null));
        }

        private java.awt.Shape surface() {
            var insets = getInsets();
            int width = Math.max(0, getWidth() - insets.left - insets.right);
            int height = Math.max(0, getHeight() - insets.top - insets.bottom), arc = UIScale.scale(ARC);
            var rounded = new RoundRectangle2D.Double(insets.left, insets.top, width, height, arc, arc);
            if (roundBottom) return rounded;
            // Keep terminal cells rectangular at the bottom; only the tab-strip shoulders round.
            var shape = new java.awt.geom.Area(rounded);
            shape.add(new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(
                insets.left, insets.top + height / 2.0, width, height / 2.0)));
            return shape;
        }

        @Override protected boolean isPaintingOrigin() { return true; }
        @Override public boolean contains(int x, int y) { return surface().contains(x, y); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setColor(UIManager.getColor("Jasper.titleBackground"));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(roundBottom ? getBackground() : UIManager.getColor("ToolWindow.background")); g.fill(surface());
            } finally { g.dispose(); }
        }

        @Override protected void paintChildren(Graphics graphics) {
            var clipped = (Graphics2D) graphics.create();
            try { clipped.clip(surface()); super.paintChildren(clipped); }
            finally { clipped.dispose(); }
        }
    }

    /** A split whose second component keeps a fixed size: the divider is placed once the split knows its own size. */
    private static final class Split extends JSplitPane {
        private int pendingTrailing = -1;

        Split(int orientation, JComponent first, JComponent second, boolean trailing, int size) {
            super(orientation, true, first, second);
            setBorder(null);
            setDividerSize(UIScale.scale(6));
            putClientProperty("FlatLaf.style", "style: plain");
            setResizeWeight(trailing ? 1.0 : 0.0);
            if (trailing) pendingTrailing = size; else setDividerLocation(size);
        }

        @Override public void updateUI() {
            super.updateUI();
            setBorder(null);
            setBackground(UIManager.getColor("Jasper.titleBackground"));
            setOpaque(true);
            if (getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI ui) {
                ui.getDivider().setBorder(BorderFactory.createEmptyBorder());
                ui.getDivider().setBackground(getBackground());
            }
        }

        @Override public void doLayout() {
            int total = getOrientation() == HORIZONTAL_SPLIT ? getWidth() : getHeight();
            if (pendingTrailing >= 0 && total > 0) {
                setDividerLocation(Math.max(0, total - pendingTrailing - getDividerSize()));
                pendingTrailing = -1;
            }
            super.doLayout();
        }
    }

    private final Host center = new Host(false);
    private final EnumMap<PanelRegion, JComponent> contents = new EnumMap<>(PanelRegion.class);
    private final EnumMap<PanelRegion, Host> hosts = new EnumMap<>(PanelRegion.class);
    private final EnumMap<PanelRegion, Integer> sizes = new EnumMap<>(PanelRegion.class);

    WorkspaceRegions(JComponent center) {
        super(new BorderLayout());
        this.center.add(center, BorderLayout.CENTER);
        rebuild();
    }

    void show(PanelRegion region, JComponent content, int size) {
        capture();
        contents.put(region, content);
        sizes.put(region, Math.max(UIScale.scale(MINIMUM), size));
        rebuild();
    }

    void hide(PanelRegion region) {
        capture();
        if (contents.remove(region) != null) rebuild();
    }

    JComponent content(PanelRegion region) { return contents.get(region); }

    int size(PanelRegion region) {
        capture();
        return sizes.getOrDefault(region, UIScale.scale(region == PanelRegion.BOTTOM ? DEFAULT_BOTTOM : DEFAULT_SIDE));
    }

    /** Remembers what the user dragged the dividers to, for regions that are laid out. */
    private void capture() {
        hosts.forEach((region, host) -> {
            int current = region == PanelRegion.BOTTOM ? host.getHeight() : host.getWidth();
            if (contents.containsKey(region) && host.getParent() != null && current > 0) sizes.put(region, current);
        });
    }

    private JPanel host(PanelRegion region) {
        Host host = hosts.computeIfAbsent(region, key -> new Host());
        host.gutters(region == PanelRegion.BOTTOM ? 0 : 6,
            region == PanelRegion.RIGHT || region == PanelRegion.BOTTOM && contents.containsKey(PanelRegion.LEFT) ? 0 : 6,
            6, region == PanelRegion.LEFT || region == PanelRegion.BOTTOM && contents.containsKey(PanelRegion.RIGHT) ? 0 : 6);
        host.removeAll();
        host.add(contents.get(region), BorderLayout.CENTER);
        ToolWindowSurface.apply(host);
        return host;
    }

    /** Reapplies the tool window colour after a theme change reset the region backgrounds. */
    void refreshTheme() { hosts.values().forEach(ToolWindowSurface::apply); }

    private void rebuild() {
        removeAll();
        hosts.values().forEach(JPanel::removeAll);
        center.gutters(6, contents.containsKey(PanelRegion.LEFT) ? 0 : 6,
            contents.containsKey(PanelRegion.BOTTOM) ? 0 : 6, contents.containsKey(PanelRegion.RIGHT) ? 0 : 6);
        JComponent current = center;
        if (contents.containsKey(PanelRegion.BOTTOM))
            current = new Split(JSplitPane.VERTICAL_SPLIT, current, host(PanelRegion.BOTTOM), true, sizes.get(PanelRegion.BOTTOM));
        if (contents.containsKey(PanelRegion.RIGHT))
            current = new Split(JSplitPane.HORIZONTAL_SPLIT, current, host(PanelRegion.RIGHT), true, sizes.get(PanelRegion.RIGHT));
        if (contents.containsKey(PanelRegion.LEFT))
            current = new Split(JSplitPane.HORIZONTAL_SPLIT, host(PanelRegion.LEFT), current, false, sizes.get(PanelRegion.LEFT));
        add(current, BorderLayout.CENTER);
        revalidate(); repaint();
    }
}
