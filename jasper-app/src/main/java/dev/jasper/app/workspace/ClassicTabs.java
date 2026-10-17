package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;

/**
 * Classic editor tabs (spec 10.5; the Workstation mocks): always visible, content-sized, each with a status dot, its
 * label and a persistent ×, a "+" after the last one; the active tab on {@code surface.card} with a 2 px
 * {@code tab.indicator} underline; 1 px {@code border.window} dividers and the strip's rule on {@code surface.tabstrip}.
 * Never moves.
 */
final class ClassicTabs extends JPanel implements TabStrip {
    private final WindowContent owner;
    private final List<Entry> entries = new ArrayList<>();
    private final Add add = new Add();
    private Component selected;
    private boolean active = true, disposed;

    ClassicTabs(WindowContent owner) {
        super(null);
        this.owner = owner;
        setName("classicTabs");
        setOpaque(true);
        add(add);
    }

    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    @Override public JComponent component() { return this; }

    @Override public void refresh() {
        if (disposed) return;
        var current = new ArrayList<Component>();
        for (int i = 0; i < owner.tabStrip().getTabCount(); i++) current.add(owner.tabStrip().getComponentAt(i));
        if (!current.equals(entries.stream().map(entry -> entry.tab).toList())) {
            entries.forEach(this::remove);
            entries.clear();
            for (Component tab : current) { var entry = new Entry(tab); entries.add(entry); add(entry); }
        }
        selected = owner.selectedComponent();
        for (Entry entry : entries) entry.refresh();
        setBackground(ThemeZone.color(this, SemanticColor.SURFACE_TABSTRIP));
        revalidate(); repaint();
    }

    @Override public void setActive(boolean value) { active = value; refresh(); }
    @Override public void setAnimations(AnimationSettings settings) { }

    @Override public JPopupMenu tabList() {
        var menu = new JPopupMenu();
        for (Entry entry : entries) {
            var item = new JCheckBoxMenuItem(WindowTabs.capped(owner.tabTitle(entry.tab)), owner.tabIcon(entry.tab), entry.tab == selected);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { if (!disposed) owner.selectTab(entry.tab); });
            menu.add(item);
        }
        NativeStyles.installed().menuTree(menu, FlatLafInstaller::installedTheme);
        return menu;
    }

    @Override public void close() { disposed = true; entries.clear(); removeAll(); selected = null; }

    List<Entry> entries() { return List.copyOf(entries); }
    JComponent add() { return add; }

    @Override public Dimension getPreferredSize() { return new Dimension(0, UIScale.scale(owner.tabHeight())); }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    @Override public void doLayout() {
        int height = Math.max(0, getHeight() - metric(DesignMetrics.Role.HAIRLINE)), divider = metric(DesignMetrics.Role.HAIRLINE);
        int addWidth = 2 * metric(DesignMetrics.Role.CLASSIC_TAB_PLUS_INSET) + metric(DesignMetrics.Role.CHROME_GLYPH);
        int[] labels = new int[entries.size()];
        int labelRoom = Math.max(0, getWidth() - addWidth);
        for (int i = 0; i < labels.length; i++) { labels[i] = entries.get(i).labelWidth(); labelRoom -= entries.get(i).chromeWidth() + divider; }
        // Too many tabs: the longest labels shorten first, down to the minimum; past that the strip clips.
        int cap = cap(labels, labelRoom, metric(DesignMetrics.Role.CLASSIC_TAB_MIN_LABEL)), x = 0;
        for (int i = 0; i < labels.length; i++) {
            int width = entries.get(i).chromeWidth() + Math.min(labels[i], cap);
            entries.get(i).setBounds(x, 0, width, height);
            x += width + divider;
        }
        add.setBounds(x, 0, addWidth, height);
    }

    /** The largest label width, at least {@code minimum}, at which every label cut to it fits {@code room}. */
    static int cap(int[] labels, int room, int minimum) {
        long total = 0;
        int max = 0;
        for (int label : labels) { total += label; max = Math.max(max, label); }
        if (total <= room) return max;
        int low = Math.min(minimum, max), high = max;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            long sum = 0;
            for (int label : labels) sum += Math.min(label, middle);
            if (sum <= room) low = middle; else high = middle - 1;
        }
        return low;
    }

    @Override protected void paintComponent(Graphics graphics) {
        var palette = ThemeZone.theme(this).semantic();
        graphics.setColor(palette.color(SemanticColor.SURFACE_TABSTRIP));
        graphics.fillRect(0, 0, getWidth(), getHeight());
        int line = metric(DesignMetrics.Role.HAIRLINE), indicator = metric(DesignMetrics.Role.TAB_INDICATOR);
        for (Entry entry : entries) {
            Rectangle at = entry.getBounds();
            if (entry.tab == selected) {
                graphics.setColor(palette.color(SemanticColor.SURFACE_CARD));
                graphics.fillRect(at.x, at.y, at.width, at.height);
                graphics.setColor(palette.color(SemanticColor.TAB_INDICATOR));
                graphics.fillRect(at.x, at.y + at.height - indicator, at.width, indicator);
            }
            graphics.setColor(palette.color(SemanticColor.BORDER_WINDOW));
            graphics.fillRect(at.x + at.width, 0, line, at.height);
        }
        graphics.setColor(palette.color(SemanticColor.BORDER_WINDOW));
        graphics.fillRect(0, getHeight() - line, getWidth(), line);
    }

    /** One tab: its dot, its label and its ×, which a click closes. */
    final class Entry extends JComponent {
        private final Component tab;
        private boolean closeHover;
        private Point origin;

        Entry(Component tab) {
            this.tab = tab;
            putClientProperty("html.disable", true);
            setFont(ThemeZone.font(Typography.Role.BODY));
            var mouse = new MouseAdapter() {
                private void popup(MouseEvent event) { owner.tabMenu(tab).show(Entry.this, event.getX(), event.getY()); }
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (disposed) return;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (SwingUtilities.isMiddleMouseButton(event)) { owner.closeTab(tab); return; }
                    if (!SwingUtilities.isLeftMouseButton(event)) return;
                    if (closeBounds().contains(event.getPoint())) { closeClicked(); return; }
                    owner.selectTab(tab);
                    origin = SwingUtilities.convertPoint(Entry.this, event.getPoint(), ClassicTabs.this);
                }
                @Override public void mouseReleased(MouseEvent event) {
                    if (event.isPopupTrigger()) { popup(event); return; }
                    Point start = origin; origin = null;
                    if (disposed || start == null) return;
                    Point end = SwingUtilities.convertPoint(Entry.this, event.getPoint(), ClassicTabs.this);
                    if (start.distance(end) <= metric(DesignMetrics.Role.TAB_REORDER_THRESHOLD)) return;
                    for (int i = 0; i < entries.size(); i++)
                        if (entries.get(i).getBounds().contains(end)) { owner.reorderTab(owner.tabStrip().indexOfComponent(tab), i); break; }
                }
                @Override public void mouseMoved(MouseEvent event) {
                    boolean over = closeBounds().contains(event.getPoint());
                    if (over != closeHover) { closeHover = over; repaint(); }
                }
                @Override public void mouseExited(MouseEvent event) { if (closeHover) { closeHover = false; repaint(); } }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
        }

        // A bare JComponent has no accessible context; this one names its role.
        @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
                @Override public javax.accessibility.AccessibleRole getAccessibleRole() { return javax.accessibility.AccessibleRole.PAGE_TAB; }
                @Override public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
                    var states = super.getAccessibleStateSet();
                    if (tab == selected) states.add(javax.accessibility.AccessibleState.SELECTED);
                    return states;
                }
            };
            return accessibleContext;
        }

        // A tab title comes from the terminal (OSC 0/2), so its tip never renders HTML.
        @Override public JToolTip createToolTip() {
            var tip = new JToolTip(); tip.putClientProperty("html.disable", true); tip.setComponent(this); return tip;
        }

        void refresh() {
            String title = owner.tabTitle(tab), shortcut = owner.tabShortcut(entries.indexOf(this));
            String tip = WindowTabs.capped(owner.tabTooltip(tab));
            setToolTipText(shortcut.isEmpty() ? tip : tip + " (" + shortcut + ")");
            getAccessibleContext().setAccessibleName(title);
            getAccessibleContext().setAccessibleDescription(running() ? "Session running" : null);
            setFont(ThemeZone.font(Typography.Role.BODY));
            repaint();
        }

        private boolean dotted() { return tab instanceof TerminalTab; }
        private boolean running() { return tab instanceof TerminalTab terminal && terminal.focusedPane() != null && terminal.focusedPane().running(); }
        private String label() { return WindowTabs.capped(owner.tabTitle(tab)); }
        int labelWidth() { return getFontMetrics(getFont()).stringWidth(label()); }
        /** Everything but the label: the leading inset, the dot and its gap, the gap, the ×, the trailing inset. */
        int chromeWidth() {
            int dot = dotted() ? metric(DesignMetrics.Role.STATUS_DOT) + metric(DesignMetrics.Role.CLASSIC_TAB_GAP) : 0;
            return metric(DesignMetrics.Role.CLASSIC_TAB_LEADING) + dot + metric(DesignMetrics.Role.CLASSIC_TAB_GAP)
                + metric(DesignMetrics.Role.CHROME_GLYPH) + metric(DesignMetrics.Role.CLASSIC_TAB_TRAILING);
        }

        /** Where the × is, in this tab. */
        Rectangle closeBounds() {
            int size = metric(DesignMetrics.Role.CHROME_GLYPH), x = getWidth() - metric(DesignMetrics.Role.CLASSIC_TAB_TRAILING) - size;
            return new Rectangle(x, (getHeight() - size) / 2, size, size);
        }

        void closeClicked() { if (!disposed) owner.closeTab(tab); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                FlatUIUtils.setRenderingHints(g);
                var palette = ThemeZone.theme(this).semantic();
                SemanticColor ground = tab == selected ? SemanticColor.SURFACE_CARD : SemanticColor.SURFACE_TABSTRIP;
                int x = metric(DesignMetrics.Role.CLASSIC_TAB_LEADING), middle = getHeight() / 2;
                if (dotted()) {
                    int dot = metric(DesignMetrics.Role.STATUS_DOT);
                    g.setColor(SurfaceContrast.on(palette, running() ? SemanticColor.STATUS_SUCCESS : SemanticColor.STATUS_IDLE, ground, SurfaceContrast.INDICATOR));
                    g.fillOval(x, middle - dot / 2, dot, dot);
                    x += dot + metric(DesignMetrics.Role.CLASSIC_TAB_GAP);
                }
                Rectangle close = closeBounds();
                g.setFont(getFont());
                FontMetrics metrics = g.getFontMetrics();
                int room = Math.max(0, close.x - metric(DesignMetrics.Role.CLASSIC_TAB_GAP) - x);
                Color ink = SurfaceContrast.on(palette, active ? SemanticColor.TEXT_PRIMARY : SemanticColor.TEXT_SECONDARY, ground, SurfaceContrast.TEXT);
                g.setColor(ink);
                FlatUIUtils.drawString(this, g, TitleSearchField.clip(label(), metrics, room), x, (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
                g.setColor(closeHover ? ink : palette.color(SemanticColor.TEXT_PLACEHOLDER));
                g.setStroke(new java.awt.BasicStroke(Math.max(1f, UIScale.scale(1.2f))));
                g.drawLine(close.x, close.y, close.x + close.width - 1, close.y + close.height - 1);
                g.drawLine(close.x, close.y + close.height - 1, close.x + close.width - 1, close.y);
            } finally { g.dispose(); }
        }
    }

    /** The "+" after the last tab: a new tab. */
    final class Add extends JComponent {
        // A bare JComponent has no accessible context; this one names its role.
        @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
                @Override public javax.accessibility.AccessibleRole getAccessibleRole() { return javax.accessibility.AccessibleRole.PUSH_BUTTON; }
            };
            return accessibleContext;
        }

        Add() {
            setToolTipText("New Tab");
            getAccessibleContext().setAccessibleName("New Tab");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) { if (!disposed && SwingUtilities.isLeftMouseButton(event)) owner.invoke(ActionId.NEW_TAB); }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            var palette = ThemeZone.theme(this).semantic();
            int size = metric(DesignMetrics.Role.CHROME_GLYPH), stroke = Math.max(1, UIScale.scale(1));
            int x = metric(DesignMetrics.Role.CLASSIC_TAB_PLUS_INSET), middle = getHeight() / 2;
            graphics.setColor(SurfaceContrast.on(palette, SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_TABSTRIP, SurfaceContrast.TEXT));
            graphics.fillRect(x, middle - stroke / 2, size, stroke);
            graphics.fillRect(x + (size - stroke) / 2, middle - size / 2, stroke, size);
        }
    }
}
