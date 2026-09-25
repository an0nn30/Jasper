package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.PanelRegion;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.EnumMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

/**
 * The terminal deck with up to three panel regions around it. BOTTOM wraps the center, RIGHT wraps
 * that, LEFT wraps that, so the bottom region spans only the terminal. With no region shown the
 * center is the only child and the layout is exactly what it was before regions existed. EDT only.
 */
final class WorkspaceRegions extends JPanel {
    static final int DEFAULT_SIDE = 260, DEFAULT_BOTTOM = 200, MINIMUM = 120;

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
        JPanel host = hosts.computeIfAbsent(region, key -> {
            var panel = new JPanel(new BorderLayout());
            panel.setMinimumSize(new Dimension(UIScale.scale(MINIMUM), UIScale.scale(MINIMUM)));
            return panel;
        });
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
