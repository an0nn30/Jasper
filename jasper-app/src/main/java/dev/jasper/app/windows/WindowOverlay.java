package dev.jasper.app.windows;

import dev.jasper.app.platform.WindowInput;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.ModalOverlay;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/**
 * One EDT-owned modal overlay inside an existing root: the kit's {@link ModalOverlay} (the scrim, the centred card and
 * its fade-up motion) with the window's input routed to it. Overlays over one window stack: the newest takes the keys
 * and focus, and closing it hands them back to the one below. Creates no native window.
 */
final class WindowOverlay implements AutoCloseable {
    /** The root's open overlays, oldest first; {@link WindowInput#OVERLAY} names the newest. */
    private static final String STACK = "Jasper.windowOverlays";
    private final JRootPane root;
    private final ModalOverlay overlay;
    /** A dialog's Escape reaches its content (its Cancel); a progress overlay keeps Escape from the window. */
    private final boolean dialog;
    private final KeyboardFocusManager focusManager;
    private final KeyEventDispatcher keys = this::filterKey;
    private final ComponentAdapter resize = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { layout(); }
    };
    private boolean shown, closed;
    private final PropertyChangeListener focusChanged = this::focusChanged;
    private Component priorFocus;

    WindowOverlay(JRootPane root, JComponent content, KitContext context, boolean dialog) {
        this(root, content, context, dialog, KeyboardFocusManager.getCurrentKeyboardFocusManager());
    }

    WindowOverlay(JRootPane root, JComponent content, KitContext context, boolean dialog, KeyboardFocusManager focusManager) {
        this.root = root; this.dialog = dialog; this.focusManager = focusManager;
        overlay = new ModalOverlay(context, content);
        var card = overlay.card();
        card.setFocusCycleRoot(true);
        card.setFocusTraversalPolicy(new ContainerOrderFocusTraversalPolicy() {
            @Override protected boolean accept(Component component) {
                return !(component instanceof JPanel || component instanceof JScrollPane
                    || component instanceof JViewport || component instanceof JLabel) && super.accept(component);
            }
        });
        card.setFocusable(true);
        // A press on the scrim returns focus to the card.
        overlay.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { focus(); }
        });
    }

    @SuppressWarnings("unchecked")
    private List<WindowOverlay> stack() {
        if (!(root.getClientProperty(STACK) instanceof List<?> list)) {
            var created = new ArrayList<WindowOverlay>();
            root.putClientProperty(STACK, created);
            return created;
        }
        return (List<WindowOverlay>) list;
    }

    private boolean top() { return shown && root.getClientProperty(WindowInput.OVERLAY) == this; }

    private void focusChanged(java.beans.PropertyChangeEvent event) {
        if (top() && event.getNewValue() instanceof Component next
                && SwingUtilities.getRootPane(next) == root && !SwingUtilities.isDescendingFrom(next, overlay.card()))
            SwingUtilities.invokeLater(() -> { if (top()) focus(); });
    }

    void show() {
        if (closed) return;
        if (!shown) {
            priorFocus = focusManager.getFocusOwner();
            shown = true;
            var stack = stack();
            // Each newer overlay takes the layer above the last, over everything the window shows.
            root.getLayeredPane().add(overlay, Integer.valueOf(JLayeredPane.DRAG_LAYER + 1 + stack.size()));
            stack.add(this);
            root.putClientProperty(WindowInput.OVERLAY, this);
            root.getLayeredPane().addComponentListener(resize);
            focusManager.addKeyEventDispatcher(keys);
            focusManager.addPropertyChangeListener("focusOwner", focusChanged);
            layout();
            overlay.open();
        }
        layout(); focus();
    }

    void layout() {
        if (!shown) return;
        overlay.setBounds(0, 0, root.getLayeredPane().getWidth(), root.getLayeredPane().getHeight());
        overlay.doLayout(); overlay.revalidate(); overlay.repaint();
    }

    Rectangle cardBounds() { return overlay.cardBounds(); }

    void focus() {
        if (!top()) return;
        Component current = focusManager.getFocusOwner();
        var card = overlay.card();
        if (current != null && SwingUtilities.isDescendingFrom(current, card)) return;
        Component first = card.getFocusTraversalPolicy().getDefaultComponent(card);
        if (first == null || !first.requestFocusInWindow()) card.requestFocusInWindow();
    }

    /** Source-root checks let native Vault/trust prompts keep their own input; only the newest overlay filters. */
    boolean filterKey(KeyEvent event) {
        if (!top() || SwingUtilities.getRootPane(event.getComponent()) != root) return false;
        boolean inside = SwingUtilities.isDescendingFrom(event.getComponent(), overlay.card());
        boolean escape = event.getKeyCode() == KeyEvent.VK_ESCAPE || event.getKeyChar() == 27;
        boolean command = event.isMetaDown() || event.isControlDown() || event.isAltDown();
        boolean editing = event.getComponent() instanceof JTextComponent && !event.isAltDown()
            && switch (event.getKeyCode()) {
                case KeyEvent.VK_A, KeyEvent.VK_C, KeyEvent.VK_V, KeyEvent.VK_X, KeyEvent.VK_Z, KeyEvent.VK_Y -> true;
                default -> false;
            };
        if (!inside || escape && !dialog || command && !editing) {
            event.consume();
            if (!inside) focus();
            return true;
        }
        return false;
    }

    /** Hands input back at once and lets the card fade out; the overlay leaves the window when the exit ends. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        Component closingFocus = focusManager.getFocusOwner();
        boolean wasTop = top();
        boolean restore = shown && closingFocus != null && SwingUtilities.isDescendingFrom(closingFocus, overlay);
        boolean wasShown = shown;
        shown = false;
        focusManager.removeKeyEventDispatcher(keys);
        focusManager.removePropertyChangeListener("focusOwner", focusChanged);
        root.getLayeredPane().removeComponentListener(resize);
        var stack = stack();
        stack.remove(this);
        WindowOverlay below = stack.isEmpty() ? null : stack.getLast();
        if (wasTop || root.getClientProperty(WindowInput.OVERLAY) == this) root.putClientProperty(WindowInput.OVERLAY, below);
        if (stack.isEmpty()) root.putClientProperty(STACK, null);
        Runnable gone = () -> { overlay.card().removeAll(); overlay.close(); root.revalidate(); root.repaint(); };
        if (wasShown) overlay.dismiss(gone); else gone.run();
        if (below != null) {
            if (restore) below.focus();
            return;
        }
        Component previous = priorFocus; priorFocus = null;
        // A successful connection may already have requested focus for its new pane. Let it win.
        if (restore && previous != null) SwingUtilities.invokeLater(() -> {
            Component current = focusManager.getFocusOwner();
            if (!WindowInput.blocked(root) && previous.isShowing() && SwingUtilities.getRootPane(previous) == root
                    && (current == null || current == closingFocus)) previous.requestFocusInWindow();
        });
    }
}
