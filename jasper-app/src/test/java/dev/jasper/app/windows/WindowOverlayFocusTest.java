package dev.jasper.app.windows;

import java.awt.Component;
import java.awt.DefaultKeyboardFocusManager;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WindowOverlayFocusTest {
    private static final class Focus extends DefaultKeyboardFocusManager {
        Component current;
        @Override public Component getFocusOwner() { return current; }
        int dispatchers() { var all = getKeyEventDispatchers(); return all == null ? 0 : all.size(); }
    }
    private static final class Target extends JButton {
        final AtomicInteger requests = new AtomicInteger();
        @Override public boolean isShowing() { return true; }
        @Override public boolean requestFocusInWindow() { requests.incrementAndGet(); return true; }
    }

    @Test void cancelRestoresPriorFocusButSuccessfulHandoffWinsAndListenersAreReleased() throws Exception {
        var focus = new Focus(); var previous = new Target(); var next = new Target(); var cancel = new Target();
        var root = new JRootPane();
        SwingUtilities.invokeAndWait(() -> {
            var background = new JPanel(); background.add(previous); background.add(next); root.setContentPane(background);
            root.getLayeredPane().setSize(800, 600);
            var card = new JPanel(); card.add(cancel);
            focus.current = previous;
            var overlay = new WindowOverlay(root, card, focus); overlay.show();
            focus.current = cancel; overlay.close();
            assertThat(focus.dispatchers()).isZero();
            assertThat(focus.getPropertyChangeListeners("focusOwner")).isEmpty();
        });
        SwingUtilities.invokeAndWait(() -> assertThat(previous.requests.get()).isEqualTo(1));
        SwingUtilities.invokeAndWait(() -> {
            var card = new JPanel(); card.add(cancel); focus.current = previous;
            var overlay = new WindowOverlay(root, card, focus); overlay.show();
            focus.current = cancel; overlay.close();
            // Pending successful-session focus arrives before the restoration callback.
            focus.current = next;
        });
        SwingUtilities.invokeAndWait(() -> assertThat(previous.requests.get()).isEqualTo(1));
    }
}
