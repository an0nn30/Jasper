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
 * center is the only child and the layout is exactly what it was before regions existed. EDT only.
 */
final class WorkspaceRegions extends JPanel {
    static final int DEFAULT_SIDE = 260, DEFAULT_BOTTOM = 200, MINIMUM = 120;

    /** A shared frame around plugin-owned content; gutters belong to the workspace chrome. */
    private static final class Host extends JPanel {
        private static final int GUTTER = 6, ARC = 16;

        Host() {
            super(new BorderLayout());
            // Own the border rather than exposing a UIResource that a theme switch can remove.
            setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(GUTTER, GUTTER, GUTTER, GUTTER), null));
            setMinimumSize(new Dimension(UIScale.scale(MINIMUM), UIScale.scale(MINIMUM)));
        }

        private RoundRectangle2D surface() {
            int gutter = UIScale.scale(GUTTER), arc = UIScale.scale(ARC);
            return new RoundRectangle2D.Double(gutter, gutter,
                Math.max(0, getWidth() - 2 * gutter), Math.max(0, getHeight() - 2 * gutter), arc, arc);
        }

        @Override protected boolean isPaintingOrigin() { return true; }
        @Override public boolean contains(int x, int y) { return surface().contains(x, y); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setColor(UIManager.getColor("Jasper.titleBackground"));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getBackground()); g.fill(surface());
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
            setDividerSize(UIScale.scale(5));
            setResizeWeight(trailing ? 1.0 : 0.0);
            if (trailing) pendingTrailing = size; else setDividerLocation(size);
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

    private final JComponent center;
    private final EnumMap<PanelRegion, JComponent> contents = new EnumMap<>(PanelRegion.class);
    private final EnumMap<PanelRegion, JPanel> hosts = new EnumMap<>(PanelRegion.class);
    private final EnumMap<PanelRegion, Integer> sizes = new EnumMap<>(PanelRegion.class);

    WorkspaceRegions(JComponent center) {
        super(new BorderLayout());
        this.center = center;
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
        JPanel host = hosts.computeIfAbsent(region, key -> new Host());
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
