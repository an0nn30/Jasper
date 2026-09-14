package dev.jasper.app;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/** Standard Swing tabs, with the active look and feel's own layout and painting. */
final class TerminalDeck extends JTabbedPane {
    Consumer<Point> onMiddleClick = point -> {};

    TerminalDeck() {
        super(TOP, SCROLL_TAB_LAYOUT); setName("windowTabs");
        setRequestFocusEnabled(false); setFocusable(false);
    }

    @Override protected void processMouseEvent(MouseEvent event) {
        // BasicTabbedPaneUI also selects on middle press. Close without changing selection first.
        if (SwingUtilities.isMiddleMouseButton(event)) {
            if (event.getID() == MouseEvent.MOUSE_PRESSED) onMiddleClick.accept(event.getPoint());
            return;
        }
        super.processMouseEvent(event);
    }
}
