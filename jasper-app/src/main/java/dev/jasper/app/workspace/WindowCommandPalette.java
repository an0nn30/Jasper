package dev.jasper.app.workspace;

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

    private final dev.jasper.app.commands.CommandHistory history;

    WindowCommandPalette(WindowContent owner, ScopeRegistry scopes, dev.jasper.app.commands.CommandHistory history,
                         String defaultScopeId, boolean macOs) {
        this.history = history;
        this.owner = owner; this.defaultScopeId = defaultScopeId;
        controller = new PaletteController(scopes, history, macOs, this::layoutOverlay, this::restoreAndHide,
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
    dev.jasper.app.commands.CommandHistory history() { return history; }
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
        else owner.focusSelected();
        priorFocus = null; originTab = null; originPane = null;
    }

    void setVisibleRows(int value) { controller.setVisibleRows(value); }
    int visibleRows() { return controller.visibleRows(); }
    void dismiss() { controller.dismiss(); }
    boolean isOpen() { return controller.isOpen(); }
    String activeScopeId() { return controller.activeScopeId(); }
    boolean composing() { return controller.composing(); }
    CommandPalette component() { return controller.component(); }
    void enterPressed(int verb) { controller.enterPressed(verb); }
    void moveSelection(int delta) { controller.moveSelection(delta); }
    boolean stepOpen() { return controller.stepOpen(); }
    void escape() { controller.escape(); }
    boolean tabPressed(boolean backwards) { return controller.tabPressed(backwards); }
    boolean backspacePressed() { return controller.backspacePressed(); }
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

    static final int ANCHORED_MINIMUM_WIDTH = 560;

    /** The card dropped from the title field: over it, centred on it, at least the minimum wide, inside the window. */
    static Rectangle anchored(Rectangle field, Rectangle window, Dimension preferred, int minimumWidth, int margin) {
        int insetX = Math.min(margin, Math.max(0, window.width / 2));
        int width = Math.min(Math.max(field.width, minimumWidth), Math.max(0, window.width - 2 * insetX));
        int x = field.x + (field.width - width) / 2;
        x = Math.max(window.x + insetX, Math.min(x, window.x + window.width - insetX - width));
        int height = Math.min(preferred.height, Math.max(0, window.y + window.height - margin - field.y));
        return new Rectangle(x, field.y, width, height);
    }

    static Rectangle positioned(Rectangle available, Dimension preferred, int margin) {
        int insetX = Math.min(margin, Math.max(0, available.width / 2));
        int insetY = Math.min(margin, Math.max(0, available.height / 2));
        int width = Math.min(preferred.width, Math.max(0, available.width - 2 * insetX));
        int height = Math.min(preferred.height, Math.max(0, available.height - 2 * insetY));
        int x = available.x + (available.width - width) / 2;
        // Anchor the card's TOP, never its centre: the input row is the first thing in the card, so
        // a height that depends on the result count must grow downwards only. Anchoring the centre
        // moved the input row every time a scope returned a different number of rows.
        int y = available.y + available.height / TOP_ANCHOR_DIVISOR;
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
            Rectangle field = owner.paletteAnchor(this);
            if (field != null) {
                palette.setBounds(anchored(field, new Rectangle(0, 0, getWidth(), getHeight()), palette.getPreferredSize(),
                    UIScale.scale(ANCHORED_MINIMUM_WIDTH), UIScale.scale(16)));
                return;
            }
            // No usable field: centre across the workspace, which spans the window including its rail and
            // tool panels. Panel dividers must not move or narrow the palette.
            Rectangle window = SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), this);
            palette.setBounds(positioned(window, palette.getPreferredSize(), UIScale.scale(16)));
        }
        @Override protected void paintComponent(Graphics graphics) {
            if (!controller.isOpen()) return;
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Rectangle card = palette.getBounds();
                Color shadow = UIManager.getColor("Popup.dropShadowColor");
                if (shadow == null) shadow = Color.BLACK;
                g.setColor(new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), owner.theme().chrome().dark() ? 4 : 2));
                for (int i = 12; i >= 1; i--) {
                    int expansion = UIScale.scale(i);
                    g.fillRect(card.x - expansion, card.y - expansion, card.width + expansion * 2, card.height + expansion * 2);
                }
            } finally { g.dispose(); }
        }
    }
}
