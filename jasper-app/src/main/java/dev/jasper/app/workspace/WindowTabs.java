package dev.jasper.app.workspace;

import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.appearance.Token;
import dev.jasper.app.platform.SystemFonts;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import javax.swing.*;

/**
 * Folder tabs over WindowContent's retained Swing selection model. The selected folder joins the workspace
 * surface; the recessed strip and fixed controls frame it independently of the surrounding chrome.
 */
final class WindowTabs extends JPanel implements AutoCloseable {
    private static final int MIN_TAB = 80, MAX_TAB = 240, LIST_WIDTH = 24, EDGE = 8, GAP = 6;
    /** Titles come from remote programs; tooltips and the tab list show at most this many characters. */
    private static final int TITLE_LIMIT = 200;
    private final WindowContent owner;
    private final IdentityHashMap<Component, Entry> entries = new IdentityHashMap<>();
    private final List<Component> order = new ArrayList<>();
    private final JButton list = new JButton();
    private final JButton add = new JButton();
    private final JPanel viewport = new JPanel(null) {
        // Child hover/close repaints must also repaint the fade above them.
        @Override protected boolean isPaintingOrigin() { return true; }
        @Override protected void paintChildren(Graphics graphics) {
            super.paintChildren(graphics);
            paintOverflow((Graphics2D) graphics);
        }
    };
    private Component selected;
    private double scrollOffset;
    private int contentWidth;
    private int layoutWidth = -1;
    private boolean revealSelection = true;
    private boolean active = true;
    private boolean disposed;

    WindowTabs(WindowContent owner) {
        super(null);
        this.owner = owner;
        setName("windowTabs");
        flat(list); flat(add);
        add.setName("newTab");
        add.setIcon(AppIcons.named("ADD"));
        add.setToolTipText("New tab"); add.getAccessibleContext().setAccessibleName("New tab");
        add.addActionListener(event -> { if (!disposed) owner.invoke(ActionId.NEW_TAB); });
        list.setName("tabList");
        list.setIcon(AppIcons.named("DROPDOWN"));
        list.setToolTipText("Show all tabs"); list.getAccessibleContext().setAccessibleName("Show all tabs");
        list.addActionListener(event -> tabList().show(list, 0, list.getHeight()));
        setOpaque(false);
        viewport.setOpaque(false);
        viewport.setName("tabViewport");
        add(viewport); add(list); add(add);
        addMouseWheelListener(this::wheel);
        viewport.addMouseWheelListener(this::wheel);
        list.addMouseWheelListener(this::wheel);
        add.addMouseWheelListener(this::wheel);
    }

    void refresh() {
        var updated = new ArrayList<Component>();
        for (int i = 0; i < owner.tabStrip().getTabCount(); i++) updated.add(owner.tabStrip().getComponentAt(i));
        if (!updated.equals(order)) {
            for (Component tab : List.copyOf(order)) if (!updated.contains(tab)) viewport.remove(entries.remove(tab));
            order.clear(); order.addAll(updated);
            for (Component tab : order) if (!entries.containsKey(tab)) {
                Entry entry = new Entry(tab); entries.put(tab, entry); viewport.add(entry);
            }
            revealSelection = true;
        }
        if (selected != owner.selectedComponent()) { selected = owner.selectedComponent(); revealSelection = true; }
        Color background = owner.theme().palette().background();
        // A small workspace-derived recess works with independently light or dark terminal palettes.
        float shade = owner.theme().workspace().dark() ? .78f : .94f;
        setBackground(new Color(Math.round(background.getRed() * shade), Math.round(background.getGreen() * shade),
            Math.round(background.getBlue() * shade)));
        list.setIcon(AppIcons.forBackground(AppIcons.named("DROPDOWN"), getBackground()));
        add.setIcon(AppIcons.forBackground(AppIcons.named("ADD"), getBackground()));
        boolean retitled = false;
        for (Component tab : order) retitled |= entries.get(tab).refresh();
        // A tab that grows can push the selected tab out; keep it shown if it was shown.
        Entry current = entries.get(selected);
        if (retitled && current != null && current.isVisible()) revealSelection = true;
        list.setEnabled(!disposed); add.setEnabled(!disposed);
        revalidate(); repaint();
    }

    void setActive(boolean active) { this.active = active; refresh(); }

    /** Scrolls the strip without changing the selected terminal. */
    private void wheel(MouseWheelEvent event) {
        if (disposed) return;
        double distance = event.getPreciseWheelRotation() * (event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL
            ? viewport.getWidth() : UIScale.scale(16) * event.getScrollAmount());
        scrollOffset = Math.max(0, Math.min(Math.max(0, contentWidth - viewport.getWidth()), scrollOffset + distance));
        revealSelection = false;
        doLayout(); repaint();
        event.consume();
    }

    /** Every tab in order, titles literal; choosing one selects and reveals it. */
    JPopupMenu tabList() {
        var menu = new JPopupMenu();
        for (Component tab : order) {
            var item = new JCheckBoxMenuItem(capped(owner.tabTitle(tab)), owner.tabIcon(tab), tab == selected);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { if (!disposed) { revealSelection = true; owner.selectTab(tab); revalidate(); repaint(); } });
            menu.add(item);
        }
        return menu;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(MIN_TAB), UIScale.scale(owner.tabHeight())); }
    @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(MIN_TAB), UIScale.scale(owner.tabHeight())); }

    @Override public void doLayout() {
        int width = getWidth(), height = getHeight();
        if (width != layoutWidth) { layoutWidth = width; revealSelection = true; }
        int addWidth = Math.min(width, UIScale.scale(LIST_WIDTH));
        int listWidth = Math.min(Math.max(0, width - addWidth), UIScale.scale(LIST_WIDTH));
        int space = Math.max(0, width - listWidth - addWidth);
        int[] widths = new int[order.size()];
        for (int i = 0; i < widths.length; i++) widths[i] = entries.get(order.get(i)).tabWidth();
        contentWidth = sum(widths, 0, widths.length);
        int selectedIndex = order.indexOf(selected);
        if (revealSelection && selectedIndex >= 0) {
            int start = sum(widths, 0, selectedIndex), end = start + widths[selectedIndex];
            int margin = widths[selectedIndex] + UIScale.scale(32) <= space ? UIScale.scale(16) : 0;
            if (start - margin < scrollOffset || widths[selectedIndex] > space) scrollOffset = start - margin;
            else if (end + margin > scrollOffset + space) scrollOffset = end + margin - space;
            revealSelection = false;
        }
        scrollOffset = Math.max(0, Math.min(Math.max(0, contentWidth - space), scrollOffset));
        viewport.setBounds(0, 0, space, height);
        int x = -(int) Math.round(scrollOffset);
        for (int i = 0; i < order.size(); i++) {
            Entry entry = entries.get(order.get(i));
            entry.setBounds(x, 0, widths[i], height);
            entry.setVisible(x < space && x + widths[i] > 0);
            if (entry.isVisible()) entry.doLayout();
            x += widths[i];
        }
        list.setBounds(space, 0, listWidth, height);
        add.setBounds(width - addWidth, 0, addWidth, height);
    }

    // Hover/close repaints reconstruct the strip before its children, including shoulders outside the entry.
    @Override protected boolean isPaintingOrigin() { return true; }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            // Replace the parent's glass once with the same workspace alpha. Paint only opaque negative space
            // over it: translucent antialiased Src shapes cause expensive Metal readbacks on macOS.
            g.setComposite(AlphaComposite.Src);
            g.setColor(owner.workspaceSurface());
            g.fillRect(0, 0, getWidth(), getHeight());
            var recess = new java.awt.geom.Area(new Rectangle(0, 0, getWidth(), getHeight()));
            Entry entry = entries.get(selected);
            if (entry != null && entry.isVisible()) {
                var folder = new java.awt.geom.Area(folder(entry));
                folder.intersect(new java.awt.geom.Area(viewport.getBounds()));
                recess.subtract(folder);
            }
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fill(recess);
        } finally { g.dispose(); }
    }

    /** Soft top corners with quarter-circle cutouts forming broad outward bottom shoulders. */
    private java.awt.Shape folder(Entry entry) {
        double x = entry.getX(), w = entry.getWidth(), h = getHeight(), top = topInset();
        double r = Math.min(UIScale.scale(10f), Math.max(0, (h - top) / 2));
        double k = r * .5522847498;
        var shape = new java.awt.geom.Path2D.Double();
        shape.moveTo(x - r, h);
        shape.curveTo(x - r + k, h, x, h - r + k, x, h - r);
        shape.lineTo(x, top + r);
        shape.curveTo(x, top + r - k, x + r - k, top, x + r, top);
        shape.lineTo(x + w - r, top);
        shape.curveTo(x + w - r + k, top, x + w, top + r - k, x + w, top + r);
        shape.lineTo(x + w, h - r);
        shape.curveTo(x + w, h - r + k, x + w + r - k, h, x + w + r, h);
        shape.closePath();
        return shape;
    }

    private int topInset() { return Math.min(UIScale.scale(3), Math.max(0, getHeight() - UIScale.scale(16))); }

    /** Fade clipped tabs into the recessed strip; the tab-list button stays outside the viewport. */
    private void paintOverflow(Graphics2D graphics) {
        int width = viewport.getWidth(), height = viewport.getHeight();
        int fade = Math.min(UIScale.scale(16), width / 2);
        if (fade < 2) return;
        Color surface = getBackground();
        Color clear = new Color(surface.getRed(), surface.getGreen(), surface.getBlue(), 0);
        var g = (Graphics2D) graphics.create();
        try {
            if (scrollOffset >= 0.5) {
                g.setPaint(new GradientPaint(0, 0, surface, fade - 1, 0, clear));
                g.fillRect(0, 0, fade, height);
            }
            if (Math.round(scrollOffset) + width < contentWidth) {
                g.setPaint(new GradientPaint(width - fade, 0, clear, width - 1, 0, surface));
                g.fillRect(width - fade, 0, fade, height);
            }
        } finally { g.dispose(); }
    }

    private static String capped(String title) {
        if (title.length() <= TITLE_LIMIT) return title;
        int end = TITLE_LIMIT - 1;
        if (Character.isHighSurrogate(title.charAt(end - 1))) end--; // never split an emoji
        return title.substring(0, end) + "\u2026";
    }

    private static int sum(int[] values, int from, int to) {
        int total = 0;
        for (int i = from; i < to; i++) total += values[i];
        return total;
    }

    @Override public void close() { disposed = true; list.setEnabled(false); add.setEnabled(false); }

    private static void flat(JButton button) {
        button.putClientProperty("html.disable", true);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setContentAreaFilled(false); button.setFocusable(false);
    }

    private final class Entry extends JPanel {
        private final Component tab;
        private final JButton close = new JButton();
        private final JButton select = new JButton() {
            // Swing creates a fresh tip that does not inherit html.disable from its component.
            @Override public JToolTip createToolTip() {
                var tip = new JToolTip();
                tip.putClientProperty("html.disable", true);
                tip.setComponent(this);
                return tip;
            }
        };
        private boolean hovered;
        private Point origin;

        Entry(Component tab) {
            super(null);
            this.tab = tab;
            setOpaque(false);
            flat(select); flat(close);
            select.setHorizontalAlignment(SwingConstants.LEADING);
            select.setIconTextGap(UIScale.scale(GAP));
            select.addActionListener(event -> { if (!disposed) owner.selectTab(tab); });
            close.setIcon(AppIcons.named("CLOSE"));
            close.setRolloverIcon(AppIcons.named("CLOSE_HOVERED"));
            close.setRolloverEnabled(true);
            close.setToolTipText("Close tab"); close.getAccessibleContext().setAccessibleName("Close tab");
            close.addActionListener(event -> { if (!disposed) owner.closeTab(tab); });
            MouseAdapter gestures = new MouseAdapter() {
                private void popup(MouseEvent event) { owner.tabMenu(tab).show(event.getComponent(), event.getX(), event.getY()); }
                @Override public void mouseEntered(MouseEvent event) { hover(true); }
                @Override public void mouseExited(MouseEvent event) {
                    hover(contains(SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), Entry.this)));
                }
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (disposed) return;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (SwingUtilities.isMiddleMouseButton(event)) { owner.closeTab(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(event) && event.getComponent() != close) {
                        owner.selectTab(tab);
                        origin = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), WindowTabs.this);
                    }
                }
                @Override public void mouseReleased(MouseEvent event) {
                    if (event.isPopupTrigger()) { popup(event); return; }
                    Point start = origin; origin = null;
                    if (disposed || start == null || !SwingUtilities.isLeftMouseButton(event)) return;
                    Point end = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), WindowTabs.this);
                    if (start.distance(end) <= UIScale.scale(5) || !viewport.getBounds().contains(end)) return;
                    for (int i = 0; i < order.size(); i++) {
                        Entry target = entries.get(order.get(i));
                        if (target.isVisible() && SwingUtilities.convertRectangle(viewport, target.getBounds(), WindowTabs.this).contains(end)) {
                            owner.reorderTab(owner.tabStrip().indexOfComponent(tab), i); break;
                        }
                    }
                }
            };
            addMouseListener(gestures); select.addMouseListener(gestures); close.addMouseListener(gestures);
            addMouseWheelListener(WindowTabs.this::wheel);
            select.addMouseWheelListener(WindowTabs.this::wheel);
            close.addMouseWheelListener(WindowTabs.this::wheel);
            add(select); add(close);
        }

        private void hover(boolean value) {
            hovered = value;
            close.setVisible(tab == selected || hovered);
            repaint();
        }

        int tabWidth() {
            FontMetrics metrics = select.getFontMetrics(select.getFont());
            int content = UIScale.scale(EDGE + 16 + GAP) + metrics.stringWidth(owner.tabTitle(tab)) + UIScale.scale(GAP + 16 + EDGE);
            return Math.max(UIScale.scale(MIN_TAB), Math.min(UIScale.scale(MAX_TAB), content));
        }

        /** Returns whether the title changed. */
        boolean refresh() {
            String text = owner.tabTitle(tab);
            boolean retitled = !text.equals(select.getText());
            if (retitled) {
                select.setText(text); select.getAccessibleContext().setAccessibleName(text);
                select.setName("select:" + text); close.setName("close:" + text);
            }
            String shortcut = owner.tabShortcut(order.indexOf(tab));
            String tip = capped(owner.tabTooltip(tab));
            select.setToolTipText(shortcut.isEmpty() ? tip : tip + " (" + shortcut + ")");
            Color surface = tab == selected ? owner.theme().palette().background() : WindowTabs.this.getBackground();
            select.setIcon(tab == selected ? AppIcons.forBackground(owner.tabIcon(tab), surface) : null);
            close.setIcon(AppIcons.forBackground(AppIcons.named("CLOSE"), surface));
            close.setRolloverIcon(AppIcons.forBackground(AppIcons.named("CLOSE_HOVERED"), surface));
            select.setSelected(tab == selected);
            select.setFont(SystemFonts.ui(Font.PLAIN, 13f));
            select.setForeground(tab == selected ? owner.theme().palette().foreground()
                : WorkspaceTheme.color(this, Token.TEXT_MUTED, !active ? "Jasper.titleInactiveForeground" : "Jasper.titleForeground"));
            close.setVisible(tab == selected || hovered);
            return retitled;
        }

        @Override public void doLayout() {
            int edge = UIScale.scale(EDGE), closeWidth = Math.min(UIScale.scale(16), getWidth());
            int closeX = Math.max(0, getWidth() - edge - closeWidth);
            int top = topInset(), height = Math.max(0, getHeight() - top);
            close.setBounds(closeX, top, closeWidth, height);
            // Reserve the icon's space even on plain inactive labels, so selecting never changes the tab width.
            int leading = edge + (tab == selected ? 0 : UIScale.scale(16 + GAP));
            select.setBounds(Math.min(leading, getWidth()), top,
                Math.max(0, closeX - UIScale.scale(GAP) - leading), height);
        }
    }
}
