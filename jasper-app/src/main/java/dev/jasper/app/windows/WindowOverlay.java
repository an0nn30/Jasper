package dev.jasper.app.windows;

import dev.jasper.app.platform.WindowInput;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/** One EDT-owned, centered overlay inside an existing root. Creates no native window. */
final class WindowOverlay implements AutoCloseable {
    private final JRootPane root;
    private final JComponent content;
    private final Backdrop backdrop = new Backdrop();
    private final JScrollPane card;
    private final KeyboardFocusManager focusManager;
    private final KeyEventDispatcher keys = this::filterKey;
    private final ComponentAdapter resize = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { layout(); }
    };
    private boolean shown, closed;
    private final PropertyChangeListener focusChanged = this::focusChanged;
    private Component priorFocus;

    WindowOverlay(JRootPane root, JComponent content) {
        this(root, content, KeyboardFocusManager.getCurrentKeyboardFocusManager());
    }

    WindowOverlay(JRootPane root, JComponent content, KeyboardFocusManager focusManager) {
        this.root = root; this.content = content; this.focusManager = focusManager;
        card = new JScrollPane(content);
        card.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") == null
            ? Color.GRAY : UIManager.getColor("Component.borderColor")));
        card.setFocusCycleRoot(true);
        card.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
        card.setFocusable(true);
        backdrop.setLayout(null); backdrop.add(card);
    }

    private void focusChanged(java.beans.PropertyChangeEvent event) {
        if (shown && event.getNewValue() instanceof Component next
                && SwingUtilities.getRootPane(next) == root && !SwingUtilities.isDescendingFrom(next, backdrop))
            SwingUtilities.invokeLater(() -> { if (shown) focus(); });
    }

    void show() {
        if (closed) return;
        if (!shown) {
            if (WindowInput.blocked(root)) throw new IllegalStateException("This window already has an overlay");
            priorFocus = focusManager.getFocusOwner();
            shown = true;
            root.putClientProperty(WindowInput.OVERLAY, this);
            root.getLayeredPane().add(backdrop, Integer.valueOf(JLayeredPane.DRAG_LAYER + 1));
            root.getLayeredPane().addComponentListener(resize);
            focusManager.addKeyEventDispatcher(keys);
            focusManager.addPropertyChangeListener("focusOwner", focusChanged);
        }
        layout(); focus();
    }

    void layout() {
        if (!shown) return;
        backdrop.setBounds(0, 0, root.getLayeredPane().getWidth(), root.getLayeredPane().getHeight());
        backdrop.doLayout(); backdrop.revalidate(); backdrop.repaint();
    }

    Rectangle cardBounds() { return card.getBounds(); }

    static Rectangle centered(Dimension preferred, Dimension available) {
        int width = Math.min(Math.max(0, preferred.width), Math.max(0, available.width));
        int height = Math.min(Math.max(0, preferred.height), Math.max(0, available.height));
        return new Rectangle((available.width - width) / 2, (available.height - height) / 2, width, height);
    }

    void focus() {
        if (!shown) return;
        Component current = focusManager.getFocusOwner();
        if (current != null && SwingUtilities.isDescendingFrom(current, card)) return;
        Component first = card.getFocusTraversalPolicy().getDefaultComponent(card);
        if (first == null || !first.requestFocusInWindow()) card.requestFocusInWindow();
    }

    /** Source-root checks let native Vault/trust prompts keep their own input. */
    boolean filterKey(KeyEvent event) {
        if (!shown || SwingUtilities.getRootPane(event.getComponent()) != root) return false;
        boolean inside = SwingUtilities.isDescendingFrom(event.getComponent(), card);
        boolean escape = event.getKeyCode() == KeyEvent.VK_ESCAPE || event.getKeyChar() == 27;
        boolean command = event.isMetaDown() || event.isControlDown() || event.isAltDown();
        boolean editing = event.getComponent() instanceof JTextComponent && !event.isAltDown()
            && switch (event.getKeyCode()) {
                case KeyEvent.VK_A, KeyEvent.VK_C, KeyEvent.VK_V, KeyEvent.VK_X, KeyEvent.VK_Z, KeyEvent.VK_Y -> true;
                default -> false;
            };
        if (!inside || escape || command && !editing) {
            event.consume();
            if (!inside) focus();
            return true;
        }
        return false;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        Component closingFocus = focusManager.getFocusOwner();
        boolean restore = shown && closingFocus != null && SwingUtilities.isDescendingFrom(closingFocus, backdrop);
        shown = false;
        focusManager.removeKeyEventDispatcher(keys);
        focusManager.removePropertyChangeListener("focusOwner", focusChanged);
        root.getLayeredPane().removeComponentListener(resize);
        if (root.getClientProperty(WindowInput.OVERLAY) == this) root.putClientProperty(WindowInput.OVERLAY, null);
        root.getLayeredPane().remove(backdrop);
        card.setViewportView(null);
        root.revalidate(); root.repaint();
        Component previous = priorFocus; priorFocus = null;
        // A successful connection may already have requested focus for its new pane. Let it win.
        if (restore && previous != null) SwingUtilities.invokeLater(() -> {
            Component current = focusManager.getFocusOwner();
            if (!WindowInput.blocked(root) && previous.isShowing() && SwingUtilities.getRootPane(previous) == root
                    && (current == null || current == closingFocus)) previous.requestFocusInWindow();
        });
    }

    private final class Backdrop extends JComponent {
        Backdrop() {
            var mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) { event.consume(); focus(); }
                @Override public void mouseReleased(MouseEvent event) { event.consume(); }
                @Override public void mouseClicked(MouseEvent event) { event.consume(); }
                @Override public void mouseMoved(MouseEvent event) { event.consume(); }
                @Override public void mouseDragged(MouseEvent event) { event.consume(); }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(MouseEvent::consume);
        }
        @Override public void doLayout() {
            // Derive every time: retry messages and live UI font changes can alter preferred size.
            Dimension preferred = content.getPreferredSize();
            int margin = Math.min(16, Math.min(getWidth(), getHeight()) / 2);
            var bounds = centered(preferred, new Dimension(Math.max(0, getWidth() - 2 * margin), Math.max(0, getHeight() - 2 * margin)));
            bounds.translate(margin, margin); card.setBounds(bounds); card.doLayout();
        }
        @Override protected void paintComponent(Graphics graphics) {
            graphics.setColor(new Color(0, 0, 0, 90));
            graphics.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}
