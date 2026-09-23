package dev.jasper.app.workspace;

import dev.jasper.app.platform.AppIcons;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.IdentityHashMap;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Metal tab headers; the owner retains every tab, selection and session lifetime. */
final class RetroTabs implements AutoCloseable {
    private final WindowContent owner;
    private final IdentityHashMap<TerminalTab, Header> headers = new IdentityHashMap<>();
    private boolean closed;
    RetroTabs(WindowContent owner) { this.owner = owner; }
    void refresh() {
        if (closed) return;
        var deck = owner.tabStrip();
        headers.keySet().removeIf(tab -> deck.indexOfComponent(tab) < 0);
        for (int i = 0; i < deck.getTabCount(); i++) {
            var tab = (TerminalTab) deck.getComponentAt(i);
            var header = headers.computeIfAbsent(tab, Header::new);
            header.title.setText(tab.title());
            header.title.setToolTipText(tab.title());
            header.title.getAccessibleContext().setAccessibleName(tab.title());
            header.hint.setText(owner.tabShortcut(i));
            header.close.setToolTipText("Close " + tab.title());
            header.close.getAccessibleContext().setAccessibleName("Close " + tab.title());
            if (deck.getTabComponentAt(i) != header) deck.setTabComponentAt(i, header);
        }
    }
    @Override public void close() { closed = true; headers.clear(); }
    private final class Header extends JPanel {
        private final JLabel title = new JLabel(), hint = new JLabel();
        private final JButton close = new JButton(AppIcons.icon("close"));
        private Point origin;
        Header(TerminalTab tab) {
            super(new FlowLayout(FlowLayout.LEADING, 4, 0));
            setOpaque(false);
            title.putClientProperty("html.disable", true);
            hint.putClientProperty("html.disable", true);
            close.setFocusable(false);
            close.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
            close.setBorderPainted(false);
            close.setContentAreaFilled(false);
            close.setOpaque(false);
            close.addActionListener(event -> { if (!closed) owner.closeTab(tab); });
            add(title); add(hint); add(close);
            var gestures = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (closed || owner.tabStrip().indexOfComponent(tab) < 0) return;
                    if (SwingUtilities.isMiddleMouseButton(event)) { owner.closeTab(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(event)) {
                        owner.selectTab(tab);
                        origin = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), owner.tabStrip());
                    }
                }
                @Override public void mouseReleased(MouseEvent event) {
                    Point start = origin; origin = null;
                    if (closed || start == null || !SwingUtilities.isLeftMouseButton(event)) return;
                    var deck = owner.tabStrip();
                    Point end = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), deck);
                    if (start.distance(end) <= 5) return;
                    owner.reorderTab(deck.indexOfComponent(tab), deck.indexAtLocation(end.x, end.y));
                }
            };
            addMouseListener(gestures); title.addMouseListener(gestures); hint.addMouseListener(gestures);
            close.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    if (!closed && SwingUtilities.isMiddleMouseButton(event)) owner.closeTab(tab);
                }
            });
        }
    }
}
