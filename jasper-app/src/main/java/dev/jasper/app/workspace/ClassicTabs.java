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
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Classic editor tabs (spec 10.5; the Workstation mocks): always visible, content-sized, each with a status dot, its
 * label and a persistent ×, a "+" after the last one (at the trailing edge, over the tabs a crowded strip clips); the
 * active tab on {@code surface.card} with a 2 px {@code tab.indicator} underline; 1 px {@code border.window} dividers
 * and the strip's rule on {@code surface.tabstrip}. Never moves.
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
        // The + follows the last divider; when the tabs run past the edge it stays at the edge, over the clipped tabs,
        // behind a divider of its own. As child 0 it paints and takes the mouse above them.
        add.covering = x + addWidth > getWidth();
        if (add.covering) add.setBounds(Math.max(0, getWidth() - addWidth - divider), 0, addWidth + divider, height);
        else add.setBounds(x, 0, addWidth, height);
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
        var theme = ThemeZone.theme(this); var palette = theme.semantic();
        graphics.setColor(palette.color(SemanticColor.SURFACE_TABSTRIP));
        graphics.fillRect(0, 0, getWidth(), getHeight());
        int line = metric(DesignMetrics.Role.HAIRLINE), indicator = metric(DesignMetrics.Role.TAB_INDICATOR);
        for (Entry entry : entries) {
            Rectangle at = entry.getBounds();
            if (entry.tab == selected) {
                graphics.setColor(palette.color(SemanticColor.SURFACE_CARD));
                graphics.fillRect(at.x, at.y, at.width, at.height);
                graphics.setColor(SurfaceContrast.painted(theme, WindowPairs.TAB_INDICATOR));
                graphics.fillRect(at.x, at.y + at.height - indicator, at.width, indicator);
            }
            graphics.setColor(palette.color(SemanticColor.BORDER_WINDOW));
            graphics.fillRect(at.x + at.width, 0, line, at.height);
        }
        graphics.setColor(palette.color(SemanticColor.BORDER_WINDOW));
        graphics.fillRect(0, getHeight() - line, getWidth(), line);
    }

    /** One tab: its dot, its label and its ×, which closes it on a release over it, as a button does. */
    final class Entry extends JComponent {
        private final Component tab;
        private boolean closeHover, closePressed;
        private Point origin;

        Entry(Component tab) {
            this.tab = tab;
            putClientProperty("html.disable", true);
            setFont(ThemeZone.font(Typography.Role.BODY));
            var mouse = new MouseAdapter() {
                private void popup(MouseEvent event) { owner.tabMenu(tab).show(Entry.this, event.getX(), event.getY()); }
                private void hover(MouseEvent event) {
                    boolean over = closeTarget().contains(event.getPoint());
                    if (over != closeHover) { closeHover = over; repaint(); }
                }
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (disposed) return;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (SwingUtilities.isMiddleMouseButton(event)) { owner.closeTab(tab); return; }
                    if (!SwingUtilities.isLeftMouseButton(event)) return;
                    // A press on the × neither selects nor drags; dragging off it before the release cancels the close.
                    if (closeTarget().contains(event.getPoint())) { closePressed = true; return; }
                    owner.selectTab(tab);
                    origin = SwingUtilities.convertPoint(Entry.this, event.getPoint(), ClassicTabs.this);
                }
                @Override public void mouseReleased(MouseEvent event) {
                    boolean closing = closePressed;
                    closePressed = false;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (closing) {
                        if (SwingUtilities.isLeftMouseButton(event) && closeTarget().contains(event.getPoint())) closeClicked();
                        return;
                    }
                    Point start = origin; origin = null;
                    if (disposed || start == null) return;
                    Point end = SwingUtilities.convertPoint(Entry.this, event.getPoint(), ClassicTabs.this);
                    if (start.distance(end) <= metric(DesignMetrics.Role.TAB_REORDER_THRESHOLD)) return;
                    for (int i = 0; i < entries.size(); i++)
                        if (entries.get(i).getBounds().contains(end)) { owner.reorderTab(owner.tabStrip().indexOfComponent(tab), i); break; }
                }
                @Override public void mouseMoved(MouseEvent event) { hover(event); }
                // While the × is held, its hover shows whether a release would close.
                @Override public void mouseDragged(MouseEvent event) { if (closePressed) hover(event); }
                @Override public void mouseExited(MouseEvent event) { if (closeHover) { closeHover = false; repaint(); } }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
        }

        // A bare JComponent has no accessible context; this one names its role and gives the tab's actions.
        @Override public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleEntry();
            return accessibleContext;
        }

        /** A page tab, selected while it is; its first action selects it, its second ("Close tab") is the ×. */
        private final class AccessibleEntry extends AccessibleJComponent implements AccessibleAction {
            @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.PAGE_TAB; }
            @Override public AccessibleStateSet getAccessibleStateSet() {
                var states = super.getAccessibleStateSet();
                if (tab == selected) states.add(AccessibleState.SELECTED);
                return states;
            }
            @Override public AccessibleAction getAccessibleAction() { return this; }
            @Override public int getAccessibleActionCount() { return 2; }
            // The first is a button's "click", so assistive technology's default action selects rather than closes.
            @Override public String getAccessibleActionDescription(int index) {
                return switch (index) {
                    case 0 -> UIManager.getString("AbstractButton.clickText");
                    case 1 -> "Close tab";
                    default -> null;
                };
            }
            @Override public boolean doAccessibleAction(int index) {
                if (disposed || index < 0 || index > 1) return false;
                if (index == 0) owner.selectTab(tab); else closeClicked();
                return true;
            }
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
            // A title IBM Plex cannot draw (a dirty tab's "●", CJK) takes the platform face (plan 5b, ruling 26).
            setFont(ThemeZone.font(Typography.Role.BODY, label()));
            repaint();
        }

        private boolean dotted() { return tab instanceof TerminalTab; }
        boolean running() { return tab instanceof TerminalTab terminal && terminal.focusedPane() != null && terminal.focusedPane().running(); }
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

        /**
         * Where the × takes the pointer: a square around the glyph reaching into the gap before it and the trailing inset
         * after it, at most the tab's height, so the small glyph is easy to hit (as IntelliJ's classic tabs are).
         */
        Rectangle closeTarget() {
            Rectangle glyph = closeBounds();
            int reach = Math.min(metric(DesignMetrics.Role.CLASSIC_TAB_TRAILING), metric(DesignMetrics.Role.CLASSIC_TAB_GAP));
            int side = Math.min(getHeight(), glyph.width + 2 * reach);
            return new Rectangle(glyph.x + glyph.width / 2 - side / 2, (getHeight() - side) / 2, side, side);
        }

        void closeClicked() { if (!disposed) owner.closeTab(tab); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                FlatUIUtils.setRenderingHints(g);
                var theme = ThemeZone.theme(this);
                boolean chosen = tab == selected;
                int x = metric(DesignMetrics.Role.CLASSIC_TAB_LEADING), middle = getHeight() / 2;
                if (dotted()) {
                    int dot = metric(DesignMetrics.Role.STATUS_DOT);
                    g.setColor(SurfaceContrast.painted(theme, WindowPairs.tabDot(running(), chosen)));
                    g.fillOval(x, middle - dot / 2, dot, dot);
                    x += dot + metric(DesignMetrics.Role.CLASSIC_TAB_GAP);
                }
                Rectangle close = closeBounds();
                g.setFont(getFont());
                FontMetrics metrics = g.getFontMetrics();
                int room = Math.max(0, close.x - metric(DesignMetrics.Role.CLASSIC_TAB_GAP) - x);
                Color ink = SurfaceContrast.painted(theme, WindowPairs.tabLabel(active, chosen));
                g.setColor(ink);
                FlatUIUtils.drawString(this, g, TitleSearchField.clip(label(), metrics, room), x, (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
                g.setColor(closeHover ? ink : SurfaceContrast.painted(theme, WindowPairs.tabClose(chosen)));
                g.setStroke(new java.awt.BasicStroke(Math.max(1f, UIScale.scale(1.2f))));
                g.drawLine(close.x, close.y, close.x + close.width - 1, close.y + close.height - 1);
                g.drawLine(close.x, close.y + close.height - 1, close.x + close.width - 1, close.y);
            } finally { g.dispose(); }
        }
    }

    /** The "+" after the last tab: a flat, unfocusable button that opens a new tab on a release over it. */
    final class Add extends JButton {
        /** Whether the + stands over clipped tabs; it then hides them under the strip's fill and a divider before it. */
        private boolean covering;

        Add() {
            setFocusable(false); setOpaque(false); setContentAreaFilled(false); setBorderPainted(false);
            setToolTipText("New Tab");
            getAccessibleContext().setAccessibleName("New Tab");
            addActionListener(event -> { if (!disposed) owner.invoke(ActionId.NEW_TAB); });
        }

        @Override protected void paintComponent(Graphics graphics) {
            var theme = ThemeZone.theme(this); var palette = theme.semantic();
            int size = metric(DesignMetrics.Role.CHROME_GLYPH), stroke = Math.max(1, UIScale.scale(1));
            int x = metric(DesignMetrics.Role.CLASSIC_TAB_PLUS_INSET), middle = getHeight() / 2;
            if (covering) {
                int line = metric(DesignMetrics.Role.HAIRLINE);
                graphics.setColor(palette.color(SemanticColor.SURFACE_TABSTRIP));
                graphics.fillRect(0, 0, getWidth(), getHeight());
                graphics.setColor(palette.color(SemanticColor.BORDER_WINDOW));
                graphics.fillRect(0, 0, line, getHeight());
                x += line;
            }
            graphics.setColor(SurfaceContrast.painted(theme, WindowPairs.TAB_PLUS));
            graphics.fillRect(x, middle - stroke / 2, size, stroke);
            graphics.fillRect(x + (size - stroke) / 2, middle - size / 2, stroke, size);
        }
    }
}
