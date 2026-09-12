package dev.moray.app;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import javax.swing.*;

/** A single visible tab row over WindowContent's retained Swing selection model. */
final class WindowTabs extends JPanel {
    private final WindowContent owner;
    private final IdentityHashMap<TerminalTab, Entry> entries = new IdentityHashMap<>();
    private final List<TerminalTab> order = new ArrayList<>();
    private final JButton plus, previous, next;
    private TerminalTab selected;
    private int firstVisible;
    private int layoutWidth = -1;
    private boolean revealSelection = true;
    private boolean active = true;

    WindowTabs(WindowContent owner) {
        super(null);
        this.owner = owner;
        setName("windowTabs");
        plus = button("newTab", "New tab", "plus");
        plus.addActionListener(event -> owner.action(ActionId.NEW_TAB).actionPerformed(event));
        previous = button("previousTabs", "Previous tabs", null);
        previous.setText("\u2039");
        previous.addActionListener(event -> { firstVisible = Math.max(0, firstVisible - 1); revalidate(); repaint(); });
        next = button("nextTabs", "Next tabs", null);
        next.setText("\u203a");
        next.addActionListener(event -> { firstVisible = Math.min(order.size() - 1, firstVisible + 1); revalidate(); repaint(); });
        add(plus); add(previous); add(next);
    }

    void refresh() {
        var updated = new ArrayList<TerminalTab>();
        for (int i = 0; i < owner.tabStrip().getTabCount(); i++) updated.add((TerminalTab) owner.tabStrip().getComponentAt(i));
        if (!updated.equals(order)) {
            entries.entrySet().removeIf(item -> {
                if (updated.contains(item.getKey())) return false;
                remove(item.getValue()); return true;
            });
            order.clear(); order.addAll(updated);
            for (TerminalTab tab : order) if (!entries.containsKey(tab)) {
                Entry entry = new Entry(tab); entries.put(tab, entry); add(entry);
            }
            revealSelection = true;
        }
        if (selected != owner.currentTab()) { selected = owner.currentTab(); revealSelection = true; }
        setBackground(UIManager.getColor("Moray.titleBackground"));
        for (TerminalTab tab : order) entries.get(tab).refresh();
        plus.setEnabled(owner.action(ActionId.NEW_TAB).isEnabled());
        for (JButton button : new JButton[]{plus, previous, next}) style(button);
        revalidate(); repaint();
    }

    void setActive(boolean active) { this.active = active; refresh(); }

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(80), UIScale.scale(54)); }
    @Override public Dimension getPreferredSize() {
        return new Dimension(UIScale.scale(Math.min(800, order.size() * 160 + 32)), UIScale.scale(54));
    }

    @Override public void doLayout() {
        if (layoutWidth != getWidth()) { layoutWidth = getWidth(); revealSelection = true; }
        int width = getWidth(), tabWidth = UIScale.scale(160), plusWidth = Math.min(width, UIScale.scale(32));
        boolean overflow = order.size() * tabWidth + plusWidth > width;
        int navigation = overflow ? Math.min(UIScale.scale(24), Math.max(0, (width - plusWidth) / 3)) : 0;
        int space = Math.max(0, width - plusWidth - 2 * navigation);
        int count = Math.min(order.size(), Math.max(1, space / tabWidth));
        firstVisible = Math.max(0, Math.min(firstVisible, order.size() - count));
        int selectedIndex = order.indexOf(selected);
        if (revealSelection && selectedIndex >= 0) {
            if (selectedIndex < firstVisible) firstVisible = selectedIndex;
            if (selectedIndex >= firstVisible + count) firstVisible = selectedIndex - count + 1;
            revealSelection = false;
        }
        int x = navigation;
        for (int i = 0; i < order.size(); i++) {
            Entry entry = entries.get(order.get(i));
            boolean visible = i >= firstVisible && i < firstVisible + count;
            entry.setVisible(visible);
            if (visible) {
                int actualWidth = Math.min(tabWidth, space);
                entry.setBounds(x, 0, actualWidth, getHeight());
                x += actualWidth; space -= actualWidth;
            }
        }
        plus.setBounds(x, 0, plusWidth, getHeight());
        previous.setVisible(overflow); next.setVisible(overflow);
        previous.setBounds(0, 0, navigation, getHeight());
        next.setBounds(x + plusWidth, 0, navigation, getHeight());
        previous.setEnabled(firstVisible > 0);
        next.setEnabled(firstVisible + count < order.size());
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(UIManager.getColor("Moray.titleSeparator"));
        g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
    }

    private JButton button(String name, String accessible, String icon) {
        JButton button = new JButton();
        button.setName(name);
        button.putClientProperty("html.disable", true);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setContentAreaFilled(false); button.setFocusable(false);
        button.setToolTipText(accessible); button.getAccessibleContext().setAccessibleName(accessible);
        if (icon != null) button.setIcon(new FlatSVGIcon("dev/moray/app/icons/title/" + icon + ".svg", icon.equals("x") ? 12 : 16, icon.equals("x") ? 12 : 16)
            .setColorFilter(new FlatSVGIcon.ColorFilter(source -> button.getForeground())));
        style(button);
        return button;
    }

    private Color foreground() {
        return UIManager.getColor(active ? "Moray.titleForeground" : "Moray.titleInactiveForeground");
    }

    private void style(JButton button) {
        button.setFont(UIManager.getFont("Label.font").deriveFont(UIScale.scale(12f)));
        button.setForeground(foreground());
    }

    private final class Entry extends JPanel {
        private final TerminalTab tab;
        private final JButton select, close;
        private Point origin;

        Entry(TerminalTab tab) {
            super(null);
            this.tab = tab;
            setOpaque(false);
            select = button("select:" + tab.title(), tab.title(), "terminal-2");
            select.setHorizontalAlignment(SwingConstants.LEFT);
            select.setIconTextGap(UIScale.scale(8));
            select.addActionListener(event -> owner.selectTab(tab));
            close = button("close:" + tab.title(), "Close tab", "x");
            close.addActionListener(event -> owner.closeTab(tab));
            MouseAdapter gestures = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (SwingUtilities.isMiddleMouseButton(event)) { owner.closeTab(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(event)) {
                        owner.selectTab(tab);
                        origin = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), WindowTabs.this);
                    }
                }
                @Override public void mouseReleased(MouseEvent event) {
                    Point start = origin; origin = null;
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
            close.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    if (SwingUtilities.isMiddleMouseButton(event)) owner.closeTab(tab);
                }
            });
            add(select); add(close);
        }

        void refresh() {
            String text = tab.title();
            if (!text.equals(select.getText())) {
                select.setText(text); select.setToolTipText(text); select.getAccessibleContext().setAccessibleName(text);
                select.setName("select:" + text); close.setName("close:" + text);
            }
            select.setSelected(tab == selected);
            style(select); style(close);
            select.setFont(select.getFont().deriveFont(java.util.Map.of(java.awt.font.TextAttribute.WEIGHT,
                java.awt.font.TextAttribute.WEIGHT_SEMIBOLD)));
            if (tab == selected && active) select.setForeground(UIManager.getColor("Moray.tabSelectedForeground"));
        }

        @Override public void doLayout() {
            int inset = UIScale.scale(14), closeWidth = Math.min(UIScale.scale(16), getWidth());
            close.setBounds(Math.max(0, getWidth() - UIScale.scale(10) - closeWidth), 0, closeWidth, getHeight());
            select.setBounds(Math.min(inset, getWidth()), 0, Math.max(0, getWidth() - inset * 2 - closeWidth), getHeight());
        }

        @Override protected void paintComponent(Graphics graphics) {
            if (tab == selected) {
                graphics.setColor(UIManager.getColor("Moray.tabSelectedBackground"));
                graphics.fillRect(0, 0, getWidth(), getHeight());
                graphics.setColor(UIManager.getColor("Moray.tabUnderline"));
                int inset = UIScale.scale(14);
                graphics.fillRect(inset, getHeight() - UIScale.scale(1), Math.max(0, getWidth() - 2 * inset), UIScale.scale(1));
            }
            super.paintComponent(graphics);
        }
    }
}
