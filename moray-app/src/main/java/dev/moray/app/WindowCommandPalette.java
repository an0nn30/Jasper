package dev.moray.app;

import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;

/** Window-local palette ownership and synchronous, validated command dispatch. */
final class WindowCommandPalette implements AutoCloseable {
    private final WindowContent owner;
    private final CommandRegistry registry;
    private final CommandHistory history;
    private final CommandPalette palette;
    private final Overlay overlay = new Overlay();
    private final CommandRegistry.Subscription registryListener, historyListener;
    private final ComponentAdapter resize = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { layoutOverlay(); }
        @Override public void componentMoved(ComponentEvent event) { layoutOverlay(); }
    };
    private JRootPane root;
    private TerminalTab originTab;
    private TerminalPane originPane;
    private Component priorFocus;
    private boolean open, closed, dirty = true;

    WindowCommandPalette(WindowContent owner, CommandRegistry registry, CommandHistory history, boolean macOs) {
        this.owner = owner; this.registry = registry; this.history = history;
        palette = new CommandPalette(macOs, query -> rebuild(false), this::execute, this::dismiss);
        palette.setFocusCycleRoot(true);
        palette.setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override public Component getComponentAfter(Container root, Component current) { return palette.queryField(); }
            @Override public Component getComponentBefore(Container root, Component current) { return palette.queryField(); }
            @Override public Component getFirstComponent(Container root) { return palette.queryField(); }
            @Override public Component getLastComponent(Container root) { return palette.queryField(); }
            @Override public Component getDefaultComponent(Container root) { return palette.queryField(); }
        });
        overlay.setLayout(null); overlay.setOpaque(false); overlay.add(palette); overlay.setVisible(false);
        registryListener = registry.onChanged(this::changed);
        historyListener = history.onChanged(this::changed);
    }

    void install(JRootPane replacement) {
        if (closed || root == replacement) return;
        uninstall(); root = replacement;
        root.getLayeredPane().add(overlay, JLayeredPane.MODAL_LAYER);
        root.addComponentListener(resize); root.getLayeredPane().addComponentListener(resize);
        owner.addComponentListener(resize); owner.tabStrip().addComponentListener(resize);
        layoutOverlay();
    }

    void toggle() {
        if (open) { dismiss(); return; }
        if (closed || root == null || !owner.isActiveAndOpen()
            || !SwingUtilities.isDescendingFrom(owner, root.getLayeredPane())) return;
        owner.updateActions();
        originTab = owner.currentTab(); originPane = owner.currentPane();
        priorFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        open = true; overlay.swallowing = false; palette.setVisible(true); overlay.setVisible(true);
        palette.queryField().setText(""); rebuild(false); layoutOverlay(); palette.queryField().requestFocusInWindow();
    }

    void dismiss() { if (open) restoreAndHide(); }
    boolean isOpen() { return open; }
    boolean composing() { return palette.composing(); }
    CommandPalette component() { return palette; }

    private void changed() {
        dirty = true;
        if (!owner.updatingActions()) refreshIfChanged();
    }

    void refreshIfChanged() {
        if (!open) return;
        if (!validOrigin()) { dismiss(); return; }
        if (dirty) refresh();
    }

    void refresh() {
        if (!open) return;
        if (!validOrigin()) { dismiss(); return; }
        rebuild(true);
    }

    void refreshTheme() { palette.refreshTheme(); if (open) { layoutOverlay(); overlay.repaint(); } }

    private void rebuild(boolean preserve) {
        if (!open) return;
        dirty = false;
        Command selected = palette.resultList().getSelectedValue();
        String query = palette.queryField().getText();
        boolean empty = CommandSearch.normalize(query).isEmpty();
        List<Command> matches;
        if (empty) {
            matches = available(history.recent());
            boolean suggested = matches.isEmpty();
            if (suggested) matches = available(List.of("new_tab", "split_right", "open_settings", "new_window"));
            palette.setOpeningLabel(suggested ? "Suggested" : "Recent");
        } else matches = CommandSearch.find(registry.entries(), query, history.recent());
        palette.setResults(matches, empty, preserve && selected != null ? selected.id() : null);
        layoutOverlay();
    }

    private List<Command> available(List<String> ids) {
        return ids.stream().flatMap(id -> registry.entries().stream().map(CommandSearch.Entry::command)
            .filter(command -> command.id().equals(id) && command.action().isEnabled())).limit(3).toList();
    }

    private boolean validOrigin() {
        return owner.isActiveAndOpen() && owner.currentTab() == originTab
            && owner.currentPane() == originPane
            && (originPane == null || originTab != null && originTab.panes().contains(originPane));
    }

    private void execute(Command command) {
        if (!isOpen() || !validOrigin() || !registry.contains(command)) { dismiss(); return; }
        owner.updateActions();
        if (!validOrigin() || !registry.contains(command) || !command.action().isEnabled()) { refresh(); return; }
        restoreAndHide();
        try {
            command.action().actionPerformed(new ActionEvent(owner, ActionEvent.ACTION_PERFORMED, command.id()));
            history.record(command.id());
        } catch (RuntimeException failure) {
            System.getLogger(WindowCommandPalette.class.getName()).log(System.Logger.Level.ERROR,
                "Command failed: " + command.id(), failure);
            owner.onError.accept("Could not run " + command.title() + ". See the application log for details.");
        }
    }

    private void restoreAndHide() {
        // Pane changes notify us after choosing the new logical target. A captured component
        // in the old pane may still be showing, but restoring it would undo that transition.
        boolean restorePriorFocus = validOrigin();
        open = false; palette.setVisible(false); overlay.setVisible(overlay.swallowing);
        if (restorePriorFocus && priorFocus != null && priorFocus.isShowing()
            && SwingUtilities.isDescendingFrom(priorFocus, owner))
            priorFocus.requestFocusInWindow();
        else if (owner.currentPane() != null) owner.currentPane().focusTerminal();
        priorFocus = null; originTab = null; originPane = null;
    }

    private void layoutOverlay() {
        if (root == null) return;
        // Include Swing title/menu chrome, but never the native window controls.
        overlay.setBounds(0, 0, root.getLayeredPane().getWidth(), root.getLayeredPane().getHeight());
        overlay.doLayout(); overlay.repaint();
    }

    static Rectangle positioned(Rectangle terminal, Dimension preferred, Rectangle available, int margin) {
        int insetX = Math.min(margin, Math.max(0, available.width / 2));
        int insetY = Math.min(margin, Math.max(0, available.height / 2));
        int width = Math.min(preferred.width, Math.max(0, available.width - 2 * insetX));
        int height = Math.min(preferred.height, Math.max(0, available.height - 2 * insetY));
        int x = terminal.x + (terminal.width - width) / 2;
        // Anchor the card in the upper half while keeping clearance in small windows.
        int y = terminal.y + terminal.height / 3 - height / 2;
        x = Math.max(available.x + insetX, Math.min(x, available.x + available.width - insetX - width));
        y = Math.max(available.y + insetY, Math.min(y, available.y + available.height - insetY - height));
        return new Rectangle(x, y, width, height);
    }

    private void uninstall() {
        if (root == null) return;
        root.removeComponentListener(resize); root.getLayeredPane().removeComponentListener(resize);
        owner.removeComponentListener(resize); owner.tabStrip().removeComponentListener(resize);
        root.getLayeredPane().remove(overlay); root.repaint(); root = null;
    }

    @Override public void close() {
        if (closed) return;
        dismiss(); closed = true; registryListener.close(); historyListener.close(); uninstall();
    }

    private final class Overlay extends JComponent {
        private boolean swallowing;
        Overlay() {
            var mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    event.consume(); swallowing = true; dismiss();
                }
                @Override public void mouseReleased(MouseEvent event) { event.consume(); }
                @Override public void mouseClicked(MouseEvent event) {
                    event.consume(); swallowing = false; if (!open) setVisible(false);
                }
                @Override public void mouseMoved(MouseEvent event) {
                    if (swallowing && !open) { swallowing = false; setVisible(false); }
                }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
            addMouseWheelListener(MouseEvent::consume);
        }
        @Override public void doLayout() {
            if (root == null || !SwingUtilities.isDescendingFrom(owner, root.getLayeredPane())) return;
            Rectangle terminal = SwingUtilities.convertRectangle(owner.tabStrip().getParent(), owner.tabStrip().getBounds(), this);
            Rectangle available = terminal;
            if (terminal.height < palette.getPreferredSize().height + 32)
                available = SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), this);
            palette.setBounds(positioned(terminal, palette.getPreferredSize(), available, 16));
        }

    }
}
