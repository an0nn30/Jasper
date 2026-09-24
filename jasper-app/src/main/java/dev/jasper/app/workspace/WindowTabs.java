package dev.jasper.app.workspace;

import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.SystemFonts;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import javax.swing.*;

/** IntelliJ-style editor tabs over WindowContent's retained Swing selection model. */
final class WindowTabs extends JPanel implements AutoCloseable {
    private static final int MIN_TAB = 80, MAX_TAB = 240, LIST_WIDTH = 24, EDGE = 8, GAP = 6, UNDERLINE = 3;
    private final WindowContent owner;
    private final IdentityHashMap<TerminalTab, Entry> entries = new IdentityHashMap<>();
    private final List<TerminalTab> order = new ArrayList<>();
    private final JButton list = new JButton();
    private TerminalTab selected;
    private int firstVisible;
    private int layoutWidth = -1;
    private boolean revealSelection = true;
    private boolean active = true;
    private boolean disposed;

    WindowTabs(WindowContent owner) {
        super(null);
        this.owner = owner;
        setName("windowTabs");
        flat(list);
        list.setName("tabList");
        list.setIcon(AppIcons.chrome("arrowDown"));
        list.setToolTipText("Show all tabs"); list.getAccessibleContext().setAccessibleName("Show all tabs");
        list.addActionListener(event -> tabList().show(list, 0, list.getHeight()));
        add(list);
        addMouseWheelListener(event -> scrollTabs(event.getWheelRotation()));
    }

    void refresh() {
        var updated = new ArrayList<TerminalTab>();
        for (int i = 0; i < owner.tabStrip().getTabCount(); i++) updated.add((TerminalTab) owner.tabStrip().getComponentAt(i));
        if (!updated.equals(order)) {
            for (TerminalTab tab : List.copyOf(order)) if (!updated.contains(tab)) remove(entries.remove(tab));
            order.clear(); order.addAll(updated);
            for (TerminalTab tab : order) if (!entries.containsKey(tab)) {
                Entry entry = new Entry(tab); entries.put(tab, entry); add(entry);
            }
            revealSelection = true;
        }
        if (selected != owner.currentTab()) { selected = owner.currentTab(); revealSelection = true; }
        setBackground(UIManager.getColor("Jasper.titleBackground"));
        for (TerminalTab tab : order) entries.get(tab).refresh();
        list.setEnabled(!disposed);
        revalidate(); repaint();
    }

    void setActive(boolean active) { this.active = active; refresh(); }

    /** Moves the first visible tab by {@code delta}; the mouse wheel scrolls the strip. */
    void scrollTabs(int delta) {
        if (order.isEmpty()) return;
        firstVisible = Math.max(0, Math.min(order.size() - 1, firstVisible + delta));
        revalidate(); repaint();
    }

    /** Every tab in order, titles literal; choosing one selects and reveals it. */
    JPopupMenu tabList() {
        var menu = new JPopupMenu();
        for (TerminalTab tab : order) {
            var item = new JCheckBoxMenuItem(tab.title(), tab.icon(), tab == selected);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { if (!disposed) owner.selectTab(tab); });
            menu.add(item);
        }
        return menu;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(MIN_TAB), UIScale.scale(owner.tabHeight())); }
    @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(MIN_TAB), UIScale.scale(owner.tabHeight())); }

    @Override public void doLayout() {
        int width = getWidth(), height = getHeight();
        if (width != layoutWidth) { layoutWidth = width; revealSelection = true; }
        int listWidth = Math.min(width, UIScale.scale(LIST_WIDTH));
        int space = Math.max(0, width - listWidth);
        int[] widths = new int[order.size()];
        for (int i = 0; i < widths.length; i++) widths[i] = entries.get(order.get(i)).tabWidth();
        firstVisible = Math.max(0, Math.min(firstVisible, order.size() - 1));
        int selectedIndex = order.indexOf(selected);
        if (revealSelection && selectedIndex >= 0) {
            if (selectedIndex < firstVisible) firstVisible = selectedIndex;
            while (firstVisible < selectedIndex && sum(widths, firstVisible, selectedIndex + 1) > space) firstVisible++;
            revealSelection = false;
        }
        // Scrolled-away tabs return once everything after them fits again.
        while (firstVisible > 0 && sum(widths, firstVisible - 1, widths.length) <= space) firstVisible--;
        int x = 0;
        boolean full = false;
        for (int i = 0; i < order.size(); i++) {
            Entry entry = entries.get(order.get(i));
            boolean visible = !full && i >= firstVisible && (x + widths[i] <= space || i == firstVisible);
            if (i >= firstVisible && !visible) full = true;
            entry.setVisible(visible);
            if (!visible) continue;
            int tabWidth = Math.max(0, Math.min(widths[i], space - x));
            entry.setBounds(x, 0, tabWidth, height);
            entry.doLayout();
            x += tabWidth;
        }
        list.setBounds(width - listWidth, 0, listWidth, height);
    }

    private static int sum(int[] values, int from, int to) {
        int total = 0;
        for (int i = from; i < to; i++) total += values[i];
        return total;
    }

    @Override public void close() { disposed = true; list.setEnabled(false); }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(UIManager.getColor("Jasper.titleSeparator"));
        g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
    }

    private static void flat(JButton button) {
        button.putClientProperty("html.disable", true);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setContentAreaFilled(false); button.setFocusable(false);
    }

    private final class Entry extends JPanel {
        private final TerminalTab tab;
        private final JButton select = new JButton(), close = new JButton();
        private boolean hovered;
        private Point origin;

        Entry(TerminalTab tab) {
            super(null);
            this.tab = tab;
            setOpaque(false);
            flat(select); flat(close);
            select.setHorizontalAlignment(SwingConstants.LEADING);
            select.setIconTextGap(UIScale.scale(GAP));
            select.addActionListener(event -> { if (!disposed) owner.selectTab(tab); });
            close.setIcon(AppIcons.chrome("close"));
            close.setRolloverIcon(AppIcons.chrome("closeHovered"));
            close.setRolloverEnabled(true);
            close.setToolTipText("Close tab"); close.getAccessibleContext().setAccessibleName("Close tab");
            close.addActionListener(event -> { if (!disposed) owner.closeTab(tab); });
            MouseAdapter gestures = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hover(true); }
                @Override public void mouseExited(MouseEvent event) {
                    hover(contains(SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), Entry.this)));
                }
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (disposed) return;
                    if (SwingUtilities.isMiddleMouseButton(event)) { owner.closeTab(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(event) && event.getComponent() != close) {
                        owner.selectTab(tab);
                        origin = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), WindowTabs.this);
                    }
                }
                @Override public void mouseReleased(MouseEvent event) {
                    Point start = origin; origin = null;
                    if (disposed || start == null || !SwingUtilities.isLeftMouseButton(event)) return;
                    Point end = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), WindowTabs.this);
                    if (start.distance(end) <= UIScale.scale(5)) return;
                    for (int i = 0; i < order.size(); i++) {
                        Entry target = entries.get(order.get(i));
                        if (target.isVisible() && target.getBounds().contains(end)) {
                            owner.reorderTab(owner.tabStrip().indexOfComponent(tab), i); break;
                        }
                    }
                }
            };
            addMouseListener(gestures); select.addMouseListener(gestures); close.addMouseListener(gestures);
            add(select); add(close);
        }

        private void hover(boolean value) {
            hovered = value;
            close.setVisible(tab == selected || hovered);
            repaint();
        }

        int tabWidth() {
            FontMetrics metrics = select.getFontMetrics(select.getFont());
            int content = UIScale.scale(EDGE + 16 + GAP) + metrics.stringWidth(tab.title()) + UIScale.scale(GAP + 16 + EDGE);
            return Math.max(UIScale.scale(MIN_TAB), Math.min(UIScale.scale(MAX_TAB), content));
        }

        void refresh() {
            String text = tab.title();
            if (!text.equals(select.getText())) {
                select.setText(text); select.getAccessibleContext().setAccessibleName(text);
                select.setName("select:" + text); close.setName("close:" + text);
            }
            String shortcut = owner.tabShortcut(order.indexOf(tab));
            select.setToolTipText(shortcut.isEmpty() ? text : text + " (" + shortcut + ")");
            select.setIcon(tab.icon());
            select.setSelected(tab == selected);
            select.setFont(SystemFonts.ui(Font.PLAIN, 13f));
            select.setForeground(UIManager.getColor(!active ? "Jasper.titleInactiveForeground"
                : tab == selected ? "Jasper.tabSelectedForeground" : "Jasper.titleForeground"));
            close.setVisible(tab == selected || hovered);
        }

        @Override public void doLayout() {
            int edge = UIScale.scale(EDGE), closeWidth = Math.min(UIScale.scale(16), getWidth());
            int closeX = Math.max(0, getWidth() - edge - closeWidth);
            close.setBounds(closeX, 0, closeWidth, getHeight());
            select.setBounds(Math.min(edge, getWidth()), 0, Math.max(0, closeX - UIScale.scale(GAP) - edge), getHeight());
        }

        @Override protected void paintComponent(Graphics graphics) {
            if (tab == selected || hovered) {
                graphics.setColor(UIManager.getColor(tab == selected ? "Jasper.tabSelectedBackground" : "Jasper.tabHoverBackground"));
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paintComponent(graphics);
            if (tab == selected) {
                int line = UIScale.scale(UNDERLINE);
                graphics.setColor(UIManager.getColor(active ? "Jasper.tabUnderline" : "Jasper.tabUnderlineInactive"));
                graphics.fillRect(0, getHeight() - line, getWidth(), line);
            }
        }
    }
}
