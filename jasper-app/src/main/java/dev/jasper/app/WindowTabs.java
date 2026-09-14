package dev.jasper.app;

import java.awt.*;
import java.awt.event.*;
import java.util.IdentityHashMap;
import javax.swing.*;

/** Adds tab closing and drag reordering to Swing's standard tab pane. */
final class WindowTabs implements AutoCloseable {
    private final WindowContent owner;
    private final IdentityHashMap<TerminalTab, JPanel> headers = new IdentityHashMap<>();
    private boolean closed;

    private final MouseAdapter nativeGestures = new MouseAdapter() {
        private MouseAdapter active;
        @Override public void mousePressed(MouseEvent event) {
            int index = owner.tabStrip().indexAtLocation(event.getX(), event.getY());
            active = index < 0 ? null : gestures((TerminalTab) owner.tabStrip().getComponentAt(index));
            if (active != null) active.mousePressed(event);
        }
        @Override public void mouseReleased(MouseEvent event) {
            if (active != null) active.mouseReleased(event);
            active = null;
        }
    };

    WindowTabs(WindowContent owner) {
        this.owner = owner;
        owner.tabStrip().addMouseListener(nativeGestures);
        ((TerminalDeck) owner.tabStrip()).onMiddleClick = point -> {
            int index = owner.tabStrip().indexAtLocation(point.x, point.y);
            if (!closed && index >= 0) owner.closeTab((TerminalTab) owner.tabStrip().getComponentAt(index));
        };
    }

    void refresh() {
        JTabbedPane tabs = owner.tabStrip();
        headers.keySet().removeIf(tab -> tabs.indexOfComponent(tab) < 0);
        for (int i = 0; i < tabs.getTabCount(); i++) {
            TerminalTab tab = (TerminalTab) tabs.getComponentAt(i);
            JPanel header = headers.computeIfAbsent(tab, this::header);
            JLabel label = (JLabel) header.getComponent(0);
            label.setText(tab.title()); label.setName("select:" + tab.title());
            label.setToolTipText(tab.title());
            // Limit shell-supplied titles without altering their text/accessibility value.
            label.setPreferredSize(null);
            Dimension preferred = label.getPreferredSize();
            label.setPreferredSize(new Dimension(Math.min(180, preferred.width), preferred.height));
            JButton close = (JButton) header.getComponent(1);
            close.setName("close:" + tab.title()); close.setEnabled(!closed);
            close.getAccessibleContext().setAccessibleName("Close tab " + tab.title());
            if (tabs.getTabComponentAt(i) != header) tabs.setTabComponentAt(i, header);
        }
    }

    private JPanel header(TerminalTab tab) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        header.setOpaque(false);
        JLabel label = new JLabel(); label.putClientProperty("html.disable", true);
        JButton close = new JButton(closeIcon());
        close.setBorder(BorderFactory.createEmptyBorder());
        close.setBorderPainted(false); close.setContentAreaFilled(false); close.setOpaque(false);
        close.setFocusPainted(false);
        close.setHorizontalAlignment(SwingConstants.CENTER); close.setVerticalAlignment(SwingConstants.CENTER);
        close.setPreferredSize(new Dimension(20, 20));
        close.setMinimumSize(new Dimension(20, 20));
        close.setMaximumSize(new Dimension(20, 20));
        close.setFocusable(false); close.setMargin(new Insets(0, 0, 0, 0));
        close.setToolTipText("Close tab"); close.addActionListener(event -> owner.closeTab(tab));
        header.add(label); header.add(close);
        MouseAdapter gestures = gestures(tab);
        header.addMouseListener(gestures); label.addMouseListener(gestures);
        close.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!closed && SwingUtilities.isMiddleMouseButton(event)) owner.closeTab(tab);
            }
        });
        return header;
    }

    private static Icon closeIcon() {
        Icon artwork = AppIcons.icon("close");
        // The supplied 16px PNG's visible pixels occupy x=1..12, y=2..12.
        // Exclude its uneven transparent padding from Swing's centering calculation.
        return new Icon() {
            @Override public int getIconWidth() { return 12; }
            @Override public int getIconHeight() { return 11; }
            @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
                artwork.paintIcon(component, graphics, x - 1, y - 2);
            }
        };
    }

    private MouseAdapter gestures(TerminalTab tab) {
        return new MouseAdapter() {
            private Point pressed;
            @Override public void mousePressed(MouseEvent event) {
                if (closed) return;
                if (SwingUtilities.isMiddleMouseButton(event)) { owner.closeTab(tab); return; }
                if (SwingUtilities.isLeftMouseButton(event)) {
                    owner.selectTab(tab);
                    pressed = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), owner.tabStrip());
                }
                popup(event);
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (closed) return;
                popup(event);
                if (pressed == null || !SwingUtilities.isLeftMouseButton(event)) return;
                Point released = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), owner.tabStrip());
                if (pressed.distance(released) >= 5) {
                    int from = owner.tabStrip().indexOfComponent(tab);
                    int to = owner.tabStrip().indexAtLocation(released.x, released.y);
                    owner.reorderTab(from, to);
                }
                pressed = null;
            }
            private void popup(MouseEvent event) {
                if (!event.isPopupTrigger()) return;
                owner.selectTab(tab); owner.updateActions();
                JPopupMenu menu = new JPopupMenu();
                menu.add(owner.action(ActionId.RENAME_TAB)); menu.add(owner.action(ActionId.CLOSE_TAB));
                menu.show(event.getComponent(), event.getX(), event.getY());
            }
        };
    }
    @Override public void close() {
        closed = true;
        owner.tabStrip().removeMouseListener(nativeGestures);
        ((TerminalDeck) owner.tabStrip()).onMiddleClick = point -> {};
        headers.values().forEach(header -> ((JButton) header.getComponent(1)).setEnabled(false));
        headers.clear();
    }
}
