package dev.jasper.app.workspace;

import dev.jasper.app.appearance.BuiltinTheme;
import dev.jasper.app.palette.CommandPalette;
import dev.jasper.app.palette.PaletteController;
import dev.jasper.app.palette.PaletteTarget;
import dev.jasper.app.palette.ScopeRegistry;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** Workspace adapter for palette overlay, captured terminal capabilities and focus restoration. */
final class WindowCommandPalette implements AutoCloseable {
    private static final int TOP_ANCHOR_DIVISOR = 5;
    private final WindowContent owner;
    private final String defaultScopeId;
    private final PaletteController controller;
    private final CommandPalette palette;
    private final Overlay overlay = new Overlay();
    private JRootPane root;
    private TerminalTab originTab;
    private TerminalPane originPane;
    private Component priorFocus;
    private boolean closed;
    private final ComponentAdapter resize = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { layoutOverlay(); }
        @Override public void componentMoved(ComponentEvent event) { layoutOverlay(); }
    };

    WindowCommandPalette(WindowContent owner, ScopeRegistry scopes, String defaultScopeId, boolean macOs) {
        this.owner = owner; this.defaultScopeId = defaultScopeId;
        controller = new PaletteController(scopes, macOs, this::layoutOverlay, this::restoreAndHide,
            owner::scopeShortcut, owner::updatingActions, owner::updateActions,
            message -> owner.onError.accept(message), this::open);
        palette = controller.component();
        palette.setFocusCycleRoot(true);
        palette.setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override public Component getComponentAfter(Container root, Component current) { return palette.queryField(); }
            @Override public Component getComponentBefore(Container root, Component current) { return palette.queryField(); }
            @Override public Component getFirstComponent(Container root) { return palette.queryField(); }
            @Override public Component getLastComponent(Container root) { return palette.queryField(); }
            @Override public Component getDefaultComponent(Container root) { return palette.queryField(); }
        });
        overlay.setLayout(null); overlay.setOpaque(false); overlay.add(palette); overlay.setVisible(false);
    }

    void install(JRootPane replacement) {
        if (closed || root == replacement) return;
        uninstall(); root = replacement;
        root.getLayeredPane().add(overlay, JLayeredPane.MODAL_LAYER);
        root.addComponentListener(resize); root.getLayeredPane().addComponentListener(resize);
        owner.addComponentListener(resize); owner.tabStrip().addComponentListener(resize);
        layoutOverlay();
    }

    PaletteController controller() { return controller; }
    void toggle() { open(defaultScopeId); }
    boolean hasScope(String id) { return controller.hasScope(id); }
        void open(String id) { open(id, null, null); }

        /** {@code queryOrNull} replaces the query text and {@code rowIdOrNull} selects a row; neither applies when this call dismisses. */
        void open(String id, String queryOrNull, String rowIdOrNull) {
            if (closed || !hasScope(id)) return;
            boolean wasOpen = controller.isOpen();
            if (!wasOpen) {
                if (root == null || !owner.isActiveAndOpen()
                        || !SwingUtilities.isDescendingFrom(owner, root.getLayeredPane())) return;
                owner.updateActions();
                originTab = owner.currentTab(); originPane = owner.currentPane();
                priorFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            }
            if (controller.open(id, originPane == null ? PaletteTarget.window(owner.id()) : target(originPane), this::validOrigin, queryOrNull, rowIdOrNull)) {
                if (!wasOpen) overlay.swallowing = false;
                overlay.setVisible(true); layoutOverlay();
                palette.queryField().requestFocusInWindow();
            }
        }

    /** The terminal adapter stays here; providers see only narrow captured capabilities. */
    private PaletteTarget target(TerminalPane pane) {
        return new PaletteTarget(
            text -> { if (pane.view() != null) pane.view().paste(text); },
            () -> { if (pane.session() != null) pane.session().write("\r"); },
            () -> pane.session() == null ? java.util.Optional.empty() : pane.session().workingDirectory(),
            pane::running, java.util.Optional.of(owner.id()), java.util.Optional.of(pane.id()));
    }

    private boolean validOrigin() {
        return owner.isActiveAndOpen() && owner.currentTab() == originTab
            && owner.currentPane() == originPane
            && (originPane == null || originTab != null && originTab.panes().contains(originPane));
    }

    private void restoreAndHide() {
        boolean restorePriorFocus = validOrigin();
        overlay.setVisible(overlay.swallowing);
        if (restorePriorFocus && priorFocus != null && priorFocus.isShowing()
                && SwingUtilities.isDescendingFrom(priorFocus, owner)) priorFocus.requestFocusInWindow();
        else if (owner.currentPane() != null) owner.currentPane().focusTerminal();
        priorFocus = null; originTab = null; originPane = null;
    }

    void setMaxResults(int value) { controller.setMaxResults(value); }
    int maxResults() { return controller.maxResults(); }
    void dismiss() { controller.dismiss(); }
    void openPicker() { controller.openPicker(); }
    boolean isOpen() { return controller.isOpen(); }
    boolean pickerOpen() { return controller.pickerOpen(); }
    String activeScopeId() { return controller.activeScopeId(); }
    boolean composing() { return controller.composing(); }
    CommandPalette component() { return controller.component(); }
    void enterPressed(int verb) { controller.enterPressed(verb); }
    void executeNumber(int number) { controller.executeNumber(number); }
    void moveSelection(int delta) { controller.moveSelection(delta); }
    boolean stepOpen() { return controller.stepOpen(); }
    void escape() { controller.escape(); }
    boolean tabPressed(boolean backwards) { return controller.tabPressed(backwards); }
    void refresh() { controller.refresh(); }
    void refreshIfChanged() { controller.refreshIfChanged(); }
    boolean tabPressed() { return tabPressed(false); }
    void refreshTheme() { controller.refreshTheme(); if (isOpen()) { layoutOverlay(); overlay.repaint(); } }

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
        // Anchor the card's TOP, never its centre: the input row is the first thing in the card, so
        // a height that depends on the result count must grow downwards only. Anchoring the centre
        // moved the input row every time a scope returned a different number of rows.
        int y = terminal.y + terminal.height / TOP_ANCHOR_DIVISOR;
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
        controller.close(); closed = true; uninstall();
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
                    event.consume(); swallowing = false; if (!controller.isOpen()) setVisible(false);
                }
                @Override public void mouseMoved(MouseEvent event) {
                    if (swallowing && !controller.isOpen()) { swallowing = false; setVisible(false); }
                }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
            addMouseWheelListener(MouseEvent::consume);
        }
        @Override public void doLayout() {
            if (root == null || !SwingUtilities.isDescendingFrom(owner, root.getLayeredPane())) return;
            Rectangle terminal = SwingUtilities.convertRectangle(owner.tabStrip().getParent(), owner.tabStrip().getBounds(), this);
            Rectangle available = terminal;
            if (terminal.height < palette.getPreferredSize().height + UIScale.scale(32))
                available = SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), this);
            palette.setBounds(positioned(terminal, palette.getPreferredSize(), available, UIScale.scale(16)));
        }
        @Override protected void paintComponent(Graphics graphics) {
            if (!controller.isOpen()) return;
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Rectangle card = palette.getBounds();
                g.setColor(new Color(0, 0, 0, owner.theme().chrome() == BuiltinTheme.LIGHT ? 2 : 4));
                for (int i = 12; i >= 1; i--) {
                    int expansion = UIScale.scale(i), arc = UIScale.scale(24) + expansion * 2;
                    g.fillRoundRect(card.x - expansion, card.y - expansion, card.width + expansion * 2,
                        card.height + expansion * 2, arc, arc);
                }
            } finally { g.dispose(); }
        }
    }
}
