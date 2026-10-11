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
        appearance = context.binding().subscribe(pair -> { revalidate(); repaint(); });
    }
    private int metric(DesignMetrics.Role role) { return ThemePainting.metric(context, role); }
    private int sidebar() { return Math.min(metric(DesignMetrics.Role.SETTINGS_SIDEBAR_WIDTH), Math.max(0, getWidth() / 3)); }
    @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }
    @Override public void doLayout() {
        int left = sidebar(), width = Math.max(0, getWidth() - left);
        int top = Math.max(metric(DesignMetrics.Role.SETTINGS_HEADER_HEIGHT), header.getPreferredSize().height);
        int bottom = Math.max(metric(DesignMetrics.Role.SETTINGS_FOOTER_HEIGHT), footer.getPreferredSize().height);
        int gap = metric(DesignMetrics.Role.ICON_GAP), pad = metric(DesignMetrics.Role.COLUMN_GAP);
        int searchHeight = search.getPreferredSize().height;
        search.setBounds(pad, top, Math.max(0, left - 2 * pad), searchHeight);
        int navTop = top + searchHeight + gap;
        navigation.setBounds(metric(DesignMetrics.Role.SETTINGS_NAV_INSET), navTop, Math.max(0, left - 2 * metric(DesignMetrics.Role.SETTINGS_NAV_INSET)), Math.max(0, getHeight() - bottom - navTop));
        header.setBounds(left + metric(DesignMetrics.Role.SETTINGS_PAGE_INSET), 0, Math.max(0, width - 2 * metric(DesignMetrics.Role.SETTINGS_PAGE_INSET)), top);
        int bannerHeight = banner.isVisible() ? banner.getPreferredSize().height : 0;
        banner.setBounds(left, top, width, bannerHeight);
        body.setBounds(left, top + bannerHeight, width, Math.max(0, getHeight() - top - bannerHeight - bottom));
        footer.setBounds(pad, Math.max(0, getHeight() - bottom), Math.max(0, getWidth() - 2 * pad), bottom);
    }
    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D)graphics.create();
        try {
            g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_APP)); g.fillRect(0, 0, getWidth(), getHeight());
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
