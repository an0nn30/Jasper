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
 * and the side regions run the full height beside the tabs. The center has a rounded frame and
 * exterior gutter only while the rail is visible; otherwise it fills its available area edge to edge.
 * The center frame is painted in the terminal
 * palette's background, never a theme's tool window colour. EDT only.
 */
final class WorkspaceRegions extends JPanel {
    static final int DEFAULT_SIDE = 260, DEFAULT_BOTTOM = 200, MINIMUM = 120;

    /** A shared frame around plugin-owned content; gutters belong to the workspace chrome. */
    private static final class Host extends JPanel {
        private static final int GUTTER = 6, ARC = 16;

        private final boolean toolWindow;
        private boolean rounded = true;
        private float opacity = 1f;

        Host() { this(true); }
        Host(boolean toolWindow) {
            super(new BorderLayout());
            this.toolWindow = toolWindow;
            if (toolWindow) putClientProperty("Jasper.zone", "chrome");
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
            int height = Math.max(0, getHeight() - insets.top - insets.bottom), arc = rounded ? UIScale.scale(ARC) : 0;
            return new RoundRectangle2D.Double(insets.left, insets.top, width, height, arc, arc);
        }

        @Override protected boolean isPaintingOrigin() { return true; }
        @Override public boolean contains(int x, int y) { return surface().contains(x, y); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                java.awt.Color chrome = UIManager.getColor("Jasper.titleBackground");
                g.setColor(chrome);
                g.fillRect(0, 0, getWidth(), getHeight());
                if (opacity < 1f) {
                    // A translucent window: the surface is glass of the terminal background, replacing what lies
                    // beneath. Panes replace it again with the same fill, so every pixel gets exactly one alpha
                    // fill, and a strip no pane covers (the lower arc) is glass rather than a hole. Java2D's Metal
                    // pipeline renders a translucent Src fill in software, reading the GPU back, unless it is an
                    // unantialiased rectangle (200 ms instead of 1 ms per frame): fill the bounds as a plain
                    // rectangle, then round the corners with opaque, antialiased chrome over it.
                    var background = getBackground();
                    var bounds = surface().getBounds();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    g.setComposite(java.awt.AlphaComposite.Src);
                    g.setColor(new java.awt.Color(background.getRed(), background.getGreen(), background.getBlue(),
                        Math.round(255 * opacity)));
                    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                    if (rounded && chrome != null) {
                        var corners = new java.awt.geom.Area(bounds);
                        corners.subtract(new java.awt.geom.Area(surface()));
                        g.setComposite(java.awt.AlphaComposite.SrcOver);
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(chrome);
                        g.fill(corners);
                    }
                } else {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // The terminal column (tab strip included) is one surface in the terminal palette, so every
                    // theme follows the same frame, including its optional rounded corners.
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
    private final JPanel lowerInset = new JPanel() {
        @Override public Dimension getPreferredSize() { return new Dimension(0, UIScale.scale(Host.ARC / 2)); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    };
    private boolean railVisible;
    private boolean statusVisible;
    private boolean headerVisible;
    private final EnumMap<PanelRegion, JComponent> contents = new EnumMap<>(PanelRegion.class);
    private final EnumMap<PanelRegion, Host> hosts = new EnumMap<>(PanelRegion.class);
    private final EnumMap<PanelRegion, Integer> sizes = new EnumMap<>(PanelRegion.class);

    WorkspaceRegions(JComponent center) {
        super(new BorderLayout());
        this.center.add(center, BorderLayout.CENTER);
        lowerInset.setOpaque(false);
        this.center.add(lowerInset, BorderLayout.SOUTH);
        rebuild();
    }

    /** The terminal palette background of the whole center, including the tab strip and any lower arc. */
    void setTerminalBackground(java.awt.Color background) { center.setBackground(background); }

    /**
     * The opacity in effect for the center surface, 1 being opaque. Below 1 the surface is filled with the terminal
     * background at that opacity, the same fill the panes inside paint over it.
     */
    void setGlass(float opacity) {
        if (center.opacity == opacity) return;
        center.opacity = opacity;
        center.repaint();
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
        host.removeAll();
        host.add(contents.get(region), BorderLayout.CENTER);
        ToolWindowSurface.apply(host);
        return host;
    }

    /** Reapplies the tool window colour after a theme change reset the region backgrounds. */
    void refreshTheme() { hosts.values().forEach(ToolWindowSurface::apply); }

    /** A visible rail frames the terminal; hiding it lets the terminal fill its available area. */
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

    /** The visible title row or toolbar already supplies the spacing above the workspace. */
    void setHeaderVisible(boolean visible) {
        if (headerVisible == visible) return;
        headerVisible = visible;
        refreshGutters();
        revalidate(); repaint();
    }

    private void refreshGutters() {
        int top = headerVisible ? 0 : 6;
        int leading = railVisible ? 0 : 6;
        int trailing = statusVisible ? 0 : 6;
        center.rounded = railVisible;
        lowerInset.setVisible(railVisible);
        if (railVisible) {
            center.gutters(top, 0, contents.containsKey(PanelRegion.BOTTOM) ? 0 : trailing,
                contents.containsKey(PanelRegion.RIGHT) ? 0 : 6);
        } else {
            center.gutters(0, 0, 0, 0);
        }
        hosts.forEach((region, host) -> host.gutters(region == PanelRegion.BOTTOM ? 0 : top,
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
