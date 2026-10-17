package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import javax.swing.*;

/** Reusable Settings shell. The caller owns its search, navigation, header, banner, page scroller and footer views. */
public final class SettingsShell extends JPanel implements AutoCloseable {
    /**
     * How the shell meets its window (Enterprise presentation spec 12.1).
     *
     * @param integratedTitle whether the shell's header row is the window's title row (a unified title bar); otherwise the
     *     window shows its own title, and the shell draws a search strip, ruled frames and a footer band
     * @param collapsibleNavigation whether the navigation's sections collapse behind a disclosure triangle
     */
    public record Frame(boolean integratedTitle, boolean collapsibleNavigation) {
        /** Today's frame: the header row is the title row and every section stays open. */
        public static final Frame INTEGRATED = new Frame(true, false);
    }

    private final KitContext context;
    private final Frame frame;
    private final JComponent search, navigation, header, banner, body, footer;
    private final AutoCloseable appearance;

    /** A shell in {@link Frame#INTEGRATED}. */
    public SettingsShell(KitContext context, JComponent search, JComponent navigation, JComponent header,
                         JComponent banner, JComponent body, JComponent footer) {
        this(context, Frame.INTEGRATED, search, navigation, header, banner, body, footer);
    }

    /** A shell in {@code frame}. */
    public SettingsShell(KitContext context, Frame frame, JComponent search, JComponent navigation, JComponent header,
                         JComponent banner, JComponent body, JComponent footer) {
        this.context = context; this.frame = Objects.requireNonNull(frame, "frame");
        this.search = search; this.navigation = navigation; this.header = header;
        this.banner = banner; this.body = body; this.footer = footer;
        setOpaque(false); setLayout(null);
        for (JComponent child : new JComponent[]{search, navigation, header, banner, body, footer}) add(child);
        for (JComponent child : new JComponent[]{search, navigation, header, banner, footer})
            if (child instanceof KitView view) view.settingsChrome(child == header);
        if (navigation instanceof KitView view) view.collapsibleNavigation(frame.collapsibleNavigation());
        appearance = context.binding().subscribe(pair -> { revalidate(); repaint(); });
    }
    /** Marks a page's own view as part of the Settings surface, so its controls take the Settings geometry. */
    public static void settingsPage(KitView view) { view.settingsChrome(false); }
    /** The page scroller the shell expects: the OVERLAY scroll policy, so a page never loses width to a resting bar. */
    public static JScrollPane pageScroller(KitContext context) { return new OverlayScrollPane(java.util.Objects.requireNonNull(context)); }

    /**
     * The pairs the classic frame paints through their guards: its window rules beside the navigation, under the header and
     * over the footer band (Enterprise presentation plan 9a, Rulings 7). The strip rule is a faint divider and is unguarded.
     */
    public static List<PaintPair> framePairs(KitContext context) {
        var slots = context.kit().colors();
        return List.of(new PaintPair(SemanticColor.BORDER_WINDOW, slots.navigationSurface(), SurfaceContrast.Guard.STRUCTURAL),
            new PaintPair(SemanticColor.BORDER_WINDOW, slots.dialogSurface(), SurfaceContrast.Guard.STRUCTURAL),
            new PaintPair(SemanticColor.BORDER_WINDOW, DerivedColor.SURFACE_FOOTER, SurfaceContrast.Guard.STRUCTURAL));
    }

    private int metric(DesignMetrics.Role role) { return ThemePainting.metric(context, role); }
    private int sidebar() { return Math.min(metric(DesignMetrics.Role.SETTINGS_SIDEBAR_WIDTH), Math.max(0, getWidth() / 3)); }
    private int footerHeight() { return Math.max(metric(DesignMetrics.Role.SETTINGS_FOOTER_HEIGHT), footer.getPreferredSize().height); }
    /** The classic frame's search strip: the search field with the sidebar inset above and below it. */
    private int strip() { return 2 * metric(DesignMetrics.Role.SETTINGS_SIDEBAR_INSET) + search.getPreferredSize().height; }
    /**
     * The header row's height, in pixels. In the integrated frame the shell spans the whole window, so this row is the
     * window's title row: the window controls sit over the sidebar beside it and the header's rule closes it.
     */
    public int titleRowHeight() { return Math.max(metric(DesignMetrics.Role.SETTINGS_HEADER_HEIGHT), header.getPreferredSize().height); }
    @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }

    @Override public void doLayout() {
        if (frame.integratedTitle()) { integratedLayout(); return; }
        int left = sidebar(), rule = metric(DesignMetrics.Role.HAIRLINE), x = left + rule, width = Math.max(0, getWidth() - x);
        int top = titleRowHeight(), bottom = footerHeight();
        int inset = metric(DesignMetrics.Role.SETTINGS_SIDEBAR_INSET), side = metric(DesignMetrics.Role.SETTINGS_FOOTER_SIDE);
        int headerInset = metric(DesignMetrics.Role.SETTINGS_HEADER_INSET);
        search.setBounds(inset, inset, Math.max(0, left - 2 * inset), search.getPreferredSize().height);
        int navTop = strip() + rule + metric(DesignMetrics.Role.SETTINGS_SEARCH_GAP);
        navigation.setBounds(0, navTop, left, Math.max(0, getHeight() - bottom - navTop));
        header.setBounds(x + headerInset, 0, Math.max(0, width - 2 * headerInset), top);
        int bannerHeight = banner.isVisible() ? banner.getPreferredSize().height : 0;
        banner.setBounds(x, top + rule, width, bannerHeight);
        body.setBounds(x, top + rule + bannerHeight, width, Math.max(0, getHeight() - top - rule - bannerHeight - bottom));
        footer.setBounds(side, Math.max(0, getHeight() - bottom + rule), Math.max(0, getWidth() - 2 * side), Math.max(0, bottom - rule));
    }

    private void integratedLayout() {
        int left = sidebar(), width = Math.max(0, getWidth() - left);
        int top = titleRowHeight();
        int bottom = footerHeight();
        int inset = metric(DesignMetrics.Role.SETTINGS_SIDEBAR_INSET), side = metric(DesignMetrics.Role.SETTINGS_FOOTER_SIDE);
        int searchHeight = search.getPreferredSize().height;
        search.setBounds(inset, top, Math.max(0, left - 2 * inset), searchHeight);
        int navTop = top + searchHeight + metric(DesignMetrics.Role.SETTINGS_SEARCH_GAP);
        // The navigation spans the sidebar; its rows keep the navigation inset as their gutter (ViewReconciler).
        navigation.setBounds(0, navTop, left, Math.max(0, getHeight() - bottom - navTop));
        header.setBounds(left + metric(DesignMetrics.Role.SETTINGS_HEADER_INSET), 0, Math.max(0, width - 2 * metric(DesignMetrics.Role.SETTINGS_HEADER_INSET)), top);
        int bannerHeight = banner.isVisible() ? banner.getPreferredSize().height : 0;
        banner.setBounds(left, top, width, bannerHeight);
        body.setBounds(left, top + bannerHeight, width, Math.max(0, getHeight() - top - bannerHeight - bottom));
        footer.setBounds(side, Math.max(0, getHeight() - bottom), Math.max(0, getWidth() - 2 * side), bottom);
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D)graphics.create();
        try {
            var slots = context.kit().colors();
            int left = sidebar(), bottom = footerHeight();
            g.setColor(ThemePainting.color(context, slots.dialogSurface())); g.fillRect(0, 0, getWidth(), getHeight());
            if (frame.integratedTitle()) {
                g.setColor(ThemePainting.color(context, slots.navigationSurface())); g.fillRect(0, 0, left, getHeight());
                g.fillRect(0, getHeight() - bottom, getWidth(), bottom);
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
                g.drawLine(left, 0, left, getHeight() - bottom);
                g.drawLine(left, header.getHeight(), getWidth(), header.getHeight());
                g.drawLine(0, getHeight() - bottom, getWidth(), getHeight() - bottom);
                return;
            }
            int rule = metric(DesignMetrics.Role.HAIRLINE), strip = strip(), top = titleRowHeight();
            var pairs = framePairs(context);
            g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_SIDEBAR)); g.fillRect(0, 0, left, strip);
            g.setColor(ThemePainting.role(context, DerivedColor.BORDER_ROW)); g.fillRect(0, strip, left, rule);
            g.setColor(ThemePainting.color(context, slots.navigationSurface())); g.fillRect(0, strip + rule, left, Math.max(0, getHeight() - bottom - strip - rule));
            g.setColor(ThemePainting.role(context, DerivedColor.SURFACE_FOOTER)); g.fillRect(0, getHeight() - bottom, getWidth(), bottom);
            g.setColor(ThemePainting.painted(context, pairs.get(0))); g.fillRect(left, 0, rule, Math.max(0, getHeight() - bottom));
            g.setColor(ThemePainting.painted(context, pairs.get(1))); g.fillRect(left + rule, top, Math.max(0, getWidth() - left - rule), rule);
            g.setColor(ThemePainting.painted(context, pairs.get(2))); g.fillRect(0, getHeight() - bottom, getWidth(), rule);
        } finally { g.dispose(); }
    }
    @Override public void close() { try { appearance.close(); } catch (Exception failure) { System.getLogger(SettingsShell.class.getName()).log(System.Logger.Level.WARNING, "Settings shell cleanup failed", failure); } }
}
