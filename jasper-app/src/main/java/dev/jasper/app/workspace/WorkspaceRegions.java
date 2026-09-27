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

        private final boolean toolWindow;

        Host() { this(true); }
        Host(boolean toolWindow) {
            super(new BorderLayout());
            this.toolWindow = toolWindow;
            gutters(GUTTER, GUTTER, GUTTER, GUTTER);
            if (toolWindow) setMinimumSize(new Dimension(UIScale.scale(MINIMUM), UIScale.scale(MINIMUM)));
        }

        void gutters(int top, int left, int bottom, int right) {
            // An app-owned border keeps the gutter through look-and-feel switches.
            setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(top, left, bottom, right), null));
        }

        private java.awt.Shape surface() {
            var insets = getInsets();
            int width = Math.max(0, getWidth() - insets.left - insets.right);
            int height = Math.max(0, getHeight() - insets.top - insets.bottom), arc = UIScale.scale(ARC);
            return new RoundRectangle2D.Double(insets.left, insets.top, width, height, arc, arc);
        }

        @Override protected boolean isPaintingOrigin() { return true; }
        @Override public boolean contains(int x, int y) { return surface().contains(x, y); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setColor(UIManager.getColor("Jasper.titleBackground"));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(toolWindow ? getBackground() : UIManager.getColor("ToolWindow.background")); g.fill(surface());
                if (!toolWindow) {
                    // The lower arc lives below the cell grid and continues its terminal palette.
                    int radius = UIScale.scale(ARC / 2);
                    g.clipRect(0, getHeight() - getInsets().bottom - radius, getWidth(), radius);
                    g.setColor(getBackground()); g.fill(surface());
                }
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
    private boolean railVisible;
    private boolean statusVisible;
    private final EnumMap<PanelRegion, JComponent> contents = new EnumMap<>(PanelRegion.class);
    private final EnumMap<PanelRegion, Host> hosts = new EnumMap<>(PanelRegion.class);
    private final EnumMap<PanelRegion, Integer> sizes = new EnumMap<>(PanelRegion.class);

    WorkspaceRegions(JComponent center) {
        super(new BorderLayout());
        this.center.add(center, BorderLayout.CENTER);
        var lowerInset = new JPanel() {
            @Override public Dimension getPreferredSize() { return new Dimension(0, UIScale.scale(Host.ARC / 2)); }
            @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        };
        lowerInset.setOpaque(false);
        this.center.add(lowerInset, BorderLayout.SOUTH);
        rebuild();
    }

    /** Continues the resolved terminal surface below its unclipped rectangular cell grid. */
    void setTerminalBackground(java.awt.Color background) { center.setBackground(background); }

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
        host.removeAll();
        host.add(contents.get(region), BorderLayout.CENTER);
        ToolWindowSurface.apply(host);
        return host;
    }

    /** Reapplies the tool window colour after a theme change reset the region backgrounds. */
    void refreshTheme() { hosts.values().forEach(ToolWindowSurface::apply); }

    /** The visible rail already supplies balanced spacing; do not add a second gutter beside it. */
    void setRailVisible(boolean visible) {
        if (railVisible == visible) return;
        railVisible = visible;
        refreshGutters();
        revalidate(); repaint();
    }

    /** The visible status bar supplies the spacing below the workspace surfaces. */
    void setStatusVisible(boolean visible) {
        if (statusVisible == visible) return;
        statusVisible = visible;
        refreshGutters();
        revalidate(); repaint();
    }

    private void refreshGutters() {
        int leading = railVisible ? 0 : 6;
        int trailing = statusVisible ? 0 : 6;
        center.gutters(6, contents.containsKey(PanelRegion.LEFT) ? 0 : leading,
            contents.containsKey(PanelRegion.BOTTOM) ? 0 : trailing, contents.containsKey(PanelRegion.RIGHT) ? 0 : 6);
        hosts.forEach((region, host) -> host.gutters(region == PanelRegion.BOTTOM ? 0 : 6,
            region == PanelRegion.RIGHT || region == PanelRegion.BOTTOM && contents.containsKey(PanelRegion.LEFT) ? 0 : leading,
            trailing, region == PanelRegion.LEFT || region == PanelRegion.BOTTOM && contents.containsKey(PanelRegion.RIGHT) ? 0 : 6));
    }

    private void rebuild() {
        removeAll();
        hosts.values().forEach(JPanel::removeAll);
        JComponent current = center;
        if (contents.containsKey(PanelRegion.BOTTOM))
            current = new Split(JSplitPane.VERTICAL_SPLIT, current, host(PanelRegion.BOTTOM), true, sizes.get(PanelRegion.BOTTOM));
        if (contents.containsKey(PanelRegion.RIGHT))
            current = new Split(JSplitPane.HORIZONTAL_SPLIT, current, host(PanelRegion.RIGHT), true, sizes.get(PanelRegion.RIGHT));
        if (contents.containsKey(PanelRegion.LEFT))
            current = new Split(JSplitPane.HORIZONTAL_SPLIT, host(PanelRegion.LEFT), current, false, sizes.get(PanelRegion.LEFT));
        refreshGutters();
        add(current, BorderLayout.CENTER);
        revalidate(); repaint();
    }
}
