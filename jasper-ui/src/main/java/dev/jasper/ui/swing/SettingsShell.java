package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import javax.swing.*;

/** Reusable Settings shell. The caller owns its search, navigation, header, banner, page scroller and footer views. */
public final class SettingsShell extends JPanel implements AutoCloseable {
    private final KitContext context;
    private final JComponent search, navigation, header, banner, body, footer;
    private final AutoCloseable appearance;
    public SettingsShell(KitContext context, JComponent search, JComponent navigation, JComponent header,
                         JComponent banner, JComponent body, JComponent footer) {
        this.context = context; this.search = search; this.navigation = navigation; this.header = header;
        this.banner = banner; this.body = body; this.footer = footer;
        setOpaque(false); setLayout(null);
        for (JComponent child : new JComponent[]{search, navigation, header, banner, body, footer}) add(child);
        for (JComponent child : new JComponent[]{search, navigation, header, banner, footer})
            if (child instanceof KitView view) view.settingsChrome(child == header);
        appearance = context.binding().subscribe(pair -> { revalidate(); repaint(); });
    }
    /** Marks a page's own view as part of the Settings surface, so its controls take the Settings geometry. */
    public static void settingsPage(KitView view) { view.settingsChrome(false); }
    /** The page scroller the shell expects: the OVERLAY scroll policy, so a page never loses width to a resting bar. */
    public static JScrollPane pageScroller(KitContext context) { return new OverlayScrollPane(java.util.Objects.requireNonNull(context)); }
    private int metric(DesignMetrics.Role role) { return ThemePainting.metric(context, role); }
    private int sidebar() { return Math.min(metric(DesignMetrics.Role.SETTINGS_SIDEBAR_WIDTH), Math.max(0, getWidth() / 3)); }
    /**
     * The header row's height, in pixels. The shell spans the whole window, so this row is the window's title row: the
     * window controls sit over the sidebar beside it and the header's rule closes it.
     */
    public int titleRowHeight() { return Math.max(metric(DesignMetrics.Role.SETTINGS_HEADER_HEIGHT), header.getPreferredSize().height); }
    @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }
    @Override public void doLayout() {
        int left = sidebar(), width = Math.max(0, getWidth() - left);
        int top = titleRowHeight();
        int bottom = Math.max(metric(DesignMetrics.Role.SETTINGS_FOOTER_HEIGHT), footer.getPreferredSize().height);
        int inset = metric(DesignMetrics.Role.SETTINGS_SIDEBAR_INSET), side = metric(DesignMetrics.Role.SETTINGS_FOOTER_SIDE);
        int searchHeight = search.getPreferredSize().height;
        search.setBounds(inset, top, Math.max(0, left - 2 * inset), searchHeight);
        int navTop = top + searchHeight + metric(DesignMetrics.Role.SETTINGS_SEARCH_GAP);
        // The navigation spans the sidebar; its rows keep the navigation inset as their gutter (ViewReconciler).
        navigation.setBounds(0, navTop, left, Math.max(0, getHeight() - bottom - navTop));
        header.setBounds(left + metric(DesignMetrics.Role.SETTINGS_PAGE_INSET), 0, Math.max(0, width - 2 * metric(DesignMetrics.Role.SETTINGS_PAGE_INSET)), top);
        int bannerHeight = banner.isVisible() ? banner.getPreferredSize().height : 0;
        banner.setBounds(left, top, width, bannerHeight);
        body.setBounds(left, top + bannerHeight, width, Math.max(0, getHeight() - top - bannerHeight - bottom));
        footer.setBounds(side, Math.max(0, getHeight() - bottom), Math.max(0, getWidth() - 2 * side), bottom);
    }
    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D)graphics.create();
        try {
            g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_DIALOG)); g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_SIDEBAR)); g.fillRect(0, 0, sidebar(), getHeight());
            int bottom = Math.max(metric(DesignMetrics.Role.SETTINGS_FOOTER_HEIGHT), footer.getPreferredSize().height);
            g.fillRect(0, getHeight() - bottom, getWidth(), bottom);
            g.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
            g.drawLine(sidebar(), 0, sidebar(), getHeight() - bottom);
            g.drawLine(sidebar(), header.getHeight(), getWidth(), header.getHeight());
            g.drawLine(0, getHeight() - bottom, getWidth(), getHeight() - bottom);
        } finally { g.dispose(); }
    }
    @Override public void close() { try { appearance.close(); } catch (Exception failure) { System.getLogger(SettingsShell.class.getName()).log(System.Logger.Level.WARNING, "Settings shell cleanup failed", failure); } }
}
