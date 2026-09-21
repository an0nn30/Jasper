package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.platform.SystemFonts;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.LongSupplier;
import javax.swing.*;

/** A single visible tab row over WindowContent's retained Swing selection model. */
final class WindowTabs extends JPanel implements AutoCloseable {
    private final WindowContent owner;
    private final IdentityHashMap<TerminalTab, Entry> entries = new IdentityHashMap<>();
    private final List<TerminalTab> order = new ArrayList<>();
    private final List<Entry> visualOrder = new ArrayList<>();
    private final JButton plus, previous, next;
    private TerminalTab selected;
    private int firstVisible;
    private int layoutWidth = -1;
    private boolean revealSelection = true;
    private boolean active = true;
    private final LongSupplier clock;
    final Timer animationTimer;
    private boolean laidOut, settleOnLayout, disposed;
    private int layoutHeight = -1, tabRegionLeft;

    WindowTabs(WindowContent owner, LongSupplier clock) {
        super(null);
        this.owner = owner;
        this.clock = clock;
        animationTimer = new Timer(16, event -> animateFrame());
        animationTimer.setCoalesce(true);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) settleMotion();
        });
        setName("windowTabs");
        plus = button("newTab", "New tab", "plus");
        plus.addActionListener(event -> owner.action(ActionId.NEW_TAB).actionPerformed(event));
        previous = button("previousTabs", "Previous tabs", null);
        previous.setText("\u2039");
        previous.addActionListener(event -> { firstVisible = Math.max(0, firstVisible - 1); settleOnLayout = true; revalidate(); repaint(); });
        next = button("nextTabs", "Next tabs", null);
        next.setText("\u203a");
        next.addActionListener(event -> { firstVisible = Math.min(order.size() - 1, firstVisible + 1); settleOnLayout = true; revalidate(); repaint(); });
        add(plus); add(previous); add(next);
    }

    void refresh() {
        var updated = new ArrayList<TerminalTab>();
        for (int i = 0; i < owner.tabStrip().getTabCount(); i++) updated.add((TerminalTab) owner.tabStrip().getComponentAt(i));
        if (!updated.equals(order)) {
            boolean reordered = !updated.stream().filter(order::contains).toList()
                .equals(order.stream().filter(updated::contains).toList());
            if (reordered) { settleOnLayout = true; discardDepartures(); }
            for (TerminalTab tab : List.copyOf(order)) if (!updated.contains(tab)) {
                Entry entry = entries.get(tab);
                if (!reordered && updated.size() > 1 && laidOut && isShowing() && !disposed
                    && entry.isVisible() && !previous.isVisible()) {
                    entry.depart();
                } else removeEntry(entry);
            }
            order.clear(); order.addAll(updated);
            for (TerminalTab tab : order) if (!entries.containsKey(tab)) {
                Entry entry = new Entry(tab); entries.put(tab, entry); visualOrder.add(entry); add(entry);
            }
            if (reordered) {
                visualOrder.clear();
                for (TerminalTab tab : order) visualOrder.add(entries.get(tab));
            }
            if (order.isEmpty()) discardDepartures();
            revealSelection = true;
        }
        boolean showTabs = order.size() > 1;
        if (isVisible() != showTabs) { settleMotion(); setVisible(showTabs); }
        if (selected != owner.currentTab()) { selected = owner.currentTab(); revealSelection = true; }
        setBackground(UIManager.getColor("Jasper.titleBackground"));
        for (TerminalTab tab : order) entries.get(tab).refresh();
        plus.setEnabled(owner.action(ActionId.NEW_TAB).isEnabled());
        for (JButton button : new JButton[]{plus, previous, next}) style(button);
        revalidate(); repaint();
    }

    void setActive(boolean active) { this.active = active; refresh(); }

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(80), UIScale.scale(owner.tabHeight())); }
    @Override public Dimension getPreferredSize() {
        return new Dimension(UIScale.scale(Math.min(800, order.size() * 160 + 32)), UIScale.scale(owner.tabHeight()));
    }

    @Override public void doLayout() {
        long now = clock.getAsLong();
        for (Entry entry : List.copyOf(visualOrder))
            if (entry.departing && !entry.width.moving(now)) removeEntry(entry);
        boolean settle = !laidOut || settleOnLayout || disposed || order.size() < 2;
        boolean widthChanged = layoutWidth != getWidth();
        if (widthChanged) { layoutWidth = getWidth(); revealSelection = true; settle = true; }
        if (layoutHeight != getHeight()) { layoutHeight = getHeight(); settle = true; }
        int oldFirstVisible = firstVisible;
        int width = getWidth(), minimumTabWidth = UIScale.scale(140);
        int plusWidth = Math.min(width, UIScale.scale(24));
        boolean overflow = order.size() * minimumTabWidth + plusWidth > width;
        int navigation = overflow ? Math.min(UIScale.scale(24), Math.max(0, (width - plusWidth) / 3)) : 0;
        int space = Math.max(0, width - plusWidth - 2 * navigation);
        if (navigation != tabRegionLeft) settle = true;
        int count = Math.min(order.size(), Math.max(1, space / minimumTabWidth));
        firstVisible = Math.max(0, Math.min(firstVisible, order.size() - count));
        int selectedIndex = order.indexOf(selected);
        if (revealSelection && selectedIndex >= 0) {
            if (selectedIndex < firstVisible) firstVisible = selectedIndex;
            if (selectedIndex >= firstVisible + count) firstVisible = selectedIndex - count + 1;
            revealSelection = false;
        }
        if (firstVisible != oldFirstVisible || overflow) settle = true;
        if (settle) discardDepartures();
        tabRegionLeft = navigation;
        double tabWidth = count == 0 ? 0 : (double) space / count;
        double total = 0;
        for (Entry entry : visualOrder) {
            int index = order.indexOf(entry.tab);
            boolean visible = entry.departing || (index >= firstVisible && index < firstVisible + count);
            entry.setVisible(visible);
            if (!entry.departing) {
                if (entry.entering) { entry.width = new TabMotion(0); entry.entering = false; }
                entry.width.target(tabWidth, now, settle || !visible);
            }
            if (visible) total += Math.max(0, entry.width.value(now));
        }
        // Normalize the spring widths so tabs always fill the bar, including interrupted
        // arrivals/departures. The add button never moves when titles or tab counts change.
        double accumulated = 0;
        int x = navigation;
        for (Entry entry : visualOrder) if (entry.isVisible()) {
            accumulated += Math.max(0, entry.width.value(now));
            int right = navigation + (total == 0 ? 0 : (int) Math.round(space * accumulated / total));
            entry.setBounds(x, 0, Math.max(0, right - x), getHeight());
            entry.doLayout(); x = right;
        }
        laidOut = true; settleOnLayout = false;
        plus.setBounds(width - plusWidth, 0, plusWidth, getHeight());
        previous.setVisible(overflow); next.setVisible(overflow);
        previous.setBounds(0, 0, navigation, getHeight());
        next.setBounds(width - plusWidth - navigation, 0, navigation, getHeight());
        previous.setEnabled(firstVisible > 0);
        next.setEnabled(firstVisible + count < order.size());
        boolean moving = entries.values().stream().anyMatch(entry -> entry.width.moving(now));
        if (moving && isShowing() && !disposed) animationTimer.start();
        else animationTimer.stop();
    }

    /** A frame changes only title-strip bounds; it never revalidates the terminal deck. */
    private void animateFrame() { doLayout(); repaint(); }

    private void removeEntry(Entry entry) {
        entries.remove(entry.tab); visualOrder.remove(entry); remove(entry);
    }

    private void discardDepartures() {
        for (Entry entry : List.copyOf(visualOrder)) if (entry.departing) removeEntry(entry);
    }

    private void settleMotion() {
        animationTimer.stop();
        discardDepartures();
        entries.values().forEach(entry -> { entry.entering = false; entry.width.settle(); });
        settleOnLayout = true;
    }

    @Override public void addNotify() { super.addNotify(); settleMotion(); }
    @Override public void removeNotify() { settleMotion(); super.removeNotify(); }
    @Override public void close() { disposed = true; settleMotion(); }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(UIManager.getColor("Jasper.titleSeparator"));
        g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
    }

    private JButton button(String name, String accessible, String icon) {
        JButton button = new JButton();
        button.setName(name);
        button.putClientProperty("html.disable", true);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setContentAreaFilled(false); button.setFocusable(false);
        button.setToolTipText(accessible); button.getAccessibleContext().setAccessibleName(accessible);
        if (icon != null) button.setIcon(new FlatSVGIcon("dev/jasper/app/icons/title/" + icon + ".svg", icon.equals("x") ? 12 : 16, icon.equals("x") ? 12 : 16)
            .setColorFilter(new FlatSVGIcon.ColorFilter(source -> button.getForeground())));
        style(button);
        return button;
    }

    private Color foreground() {
        return UIManager.getColor(active ? "Jasper.titleForeground" : "Jasper.titleInactiveForeground");
    }

    private void style(JButton button) {
        button.setFont(SystemFonts.system(Font.PLAIN, UIScale.scale(13f)));
        button.setForeground(foreground());
    }

    private final class Entry extends JPanel {
        private final TerminalTab tab;
        private final JButton select, close;
        private final JLabel shortcut = new JLabel("", SwingConstants.RIGHT);
        private boolean hovered;
        private Point origin;
        private boolean entering = laidOut && !disposed;
        private boolean departing, wasSelected;
        private TabMotion width = new TabMotion(UIScale.scale(160));

        Entry(TerminalTab tab) {
            super(null);
            this.tab = tab;
            setOpaque(false);
            select = button("select:" + tab.title(), tab.title(), null);
            select.setHorizontalAlignment(SwingConstants.CENTER);
            select.addActionListener(event -> { if (!departing) owner.selectTab(tab); });
            close = button("close:" + tab.title(), "Close tab", "x");
            close.addActionListener(event -> { if (!departing) owner.closeTab(tab); });
            MouseAdapter gestures = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hovered = true; close.setVisible(!departing); }
                @Override public void mouseExited(MouseEvent event) {
                    Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), Entry.this);
                    hovered = contains(point); close.setVisible(hovered && !departing);
                }
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (departing) return;
                    if (SwingUtilities.isMiddleMouseButton(event)) { owner.closeTab(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(event)) {
                        owner.selectTab(tab);
                        origin = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), WindowTabs.this);
                    }
                }
                @Override public void mouseReleased(MouseEvent event) {
                    Point start = origin; origin = null;
                    if (departing) return;
                    if (start == null || !SwingUtilities.isLeftMouseButton(event)) return;
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
            addMouseListener(gestures); select.addMouseListener(gestures);
            shortcut.addMouseListener(gestures);
            close.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hovered = true; close.setVisible(!departing); }
                @Override public void mouseExited(MouseEvent event) {
                    hovered = contains(SwingUtilities.convertPoint(close, event.getPoint(), Entry.this));
                    close.setVisible(hovered && !departing);
                }
            });
            close.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    if (!departing && SwingUtilities.isMiddleMouseButton(event)) owner.closeTab(tab);
                }
            });
            shortcut.putClientProperty("html.disable", true);
            close.setVisible(false);
            add(select); add(close); add(shortcut);
        }

        void depart() {
            departing = true; entering = false; wasSelected = tab == selected; origin = null;
            width.target(0, clock.getAsLong(), false);
            select.setEnabled(false); close.setEnabled(false); close.setVisible(false);
        }

        void refresh() {
            String text = tab.title();
            if (!text.equals(select.getText())) {
                select.setText(text); select.setToolTipText(text); select.getAccessibleContext().setAccessibleName(text);
                select.setName("select:" + text); close.setName("close:" + text);
            }
            select.setSelected(tab == selected);
            style(select); style(close);
            shortcut.setFont(select.getFont());
            shortcut.setForeground(foreground());
            shortcut.setText(owner.tabShortcut(order.indexOf(tab)));
            shortcut.setName("shortcut:" + text);
            if (tab == selected && active) select.setForeground(UIManager.getColor("Jasper.tabSelectedForeground"));
        }

        @Override public void doLayout() {
            int edge = UIScale.scale(8), closeWidth = Math.min(UIScale.scale(16), getWidth());
            int hintWidth = Math.min(shortcut.getPreferredSize().width, Math.max(0, getWidth() / 3));
            // Symmetric margins keep text centered even when shortcut strings differ.
            int inset = Math.min(getWidth() / 2, Math.max(edge + closeWidth, edge + hintWidth) + UIScale.scale(8));
            close.setBounds(Math.min(edge, Math.max(0, getWidth() - closeWidth)), 0, closeWidth, getHeight());
            shortcut.setBounds(Math.max(0, getWidth() - edge - hintWidth), 0, hintWidth, getHeight());
            select.setBounds(inset, 0, Math.max(0, getWidth() - 2 * inset), getHeight());
        }

        @Override protected void paintComponent(Graphics graphics) {
            if (tab == selected || (departing && wasSelected)) {
                graphics.setColor(UIManager.getColor("Jasper.tabSelectedBackground"));
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paintComponent(graphics);
            graphics.setColor(UIManager.getColor("Jasper.titleSeparator"));
            int line = UIScale.scale(1);
            graphics.fillRect(Math.max(0, getWidth() - line), 0, line, getHeight());
            if (tab != selected && !(departing && wasSelected))
                graphics.fillRect(0, getHeight() - line, getWidth(), line);
        }
    }
}
