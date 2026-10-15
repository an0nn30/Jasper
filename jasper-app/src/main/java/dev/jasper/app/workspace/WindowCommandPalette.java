package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.swing.NativeStyles;

import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.config.ViewMode;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.palette.CommandPalette;
import dev.jasper.app.palette.PaletteController;
import dev.jasper.app.palette.PaletteTarget;
import dev.jasper.app.palette.ScopeRegistry;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import java.util.function.LongSupplier;
import javax.swing.*;

/** Workspace adapter for palette overlay, captured terminal capabilities and focus restoration. */
final class WindowCommandPalette implements AutoCloseable {
    private static final int TOP_ANCHOR_DIVISOR = 5;
    /** How long an opening card takes to drop to its full height. */
    static final int REVEAL_MILLIS = 300;
    /** The snap guides, as fractions of the window: its vertical centre line and the default top. */
    static final double GUIDE_CENTER = 0.5, GUIDE_TOP = 1.0 / TOP_ANCHOR_DIVISOR;
    /** Logical pixels a press must travel before it drags, and how near a guide a dragged card snaps to it. */
    static final int DRAG_THRESHOLD = 4, SNAP_DISTANCE = 8;
    /** How near its home under the title field, across, a dragged card re-attaches there; anywhere above that height counts. */
    static final int HOME_DISTANCE = 24;
    private final WindowContent owner;
    private final String defaultScopeId;
    private final PaletteController controller;
    private final CommandPalette palette;
    private final Overlay overlay = new Overlay();
    private final Drag drag = new Drag();
    private JRootPane root;
    private TerminalTab originTab;
    private TerminalPane originPane;
    private Component priorFocus;
    private boolean closed;
    private AnimationSettings animations = AnimationSettings.defaults();
    private LongSupplier clock = System::nanoTime;
    private Timer reveal;
    private long revealStart;
    private final ComponentAdapter resize = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { layoutOverlay(); }
        @Override public void componentMoved(ComponentEvent event) { layoutOverlay(); }
    };

    private final dev.jasper.app.commands.CommandHistory history;

    /** {@code kit} is the chrome context the palette's kit views mount on; the palette owns its focus cycle. */
    WindowCommandPalette(WindowContent owner, ScopeRegistry scopes, dev.jasper.app.commands.CommandHistory history,
                         String defaultScopeId, boolean macOs, dev.jasper.ui.swing.KitContext kit) {
        this.history = history;
        this.owner = owner; this.defaultScopeId = defaultScopeId;
        controller = new PaletteController(kit, scopes, history, macOs, this::layoutOverlay, this::restoreAndHide,
            owner::updatingActions, owner::updateActions,
            message -> owner.onError.accept("Command failed", message), this::open);
        palette = controller.component();
        overlay.setLayout(null); overlay.setOpaque(false); overlay.add(palette); overlay.setVisible(false);
        // Presses that no row, field or link takes reach the card itself: those empty areas move it.
        palette.addMouseListener(drag); palette.addMouseMotionListener(drag);
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
                if (!wasOpen) startReveal();
                palette.focusQuery();
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
        stopReveal(); drag.cancel();
        boolean restorePriorFocus = validOrigin();
        overlay.setVisible(overlay.swallowing);
        if (restorePriorFocus && priorFocus != null && priorFocus.isShowing()
                && SwingUtilities.isDescendingFrom(priorFocus, owner)) priorFocus.requestFocusInWindow();
        else owner.focusSelected();
        priorFocus = null; originTab = null; originPane = null;
    }

    void setAnimations(AnimationSettings settings) {
        animations = settings;
        if (!settings.enabled()) stopReveal();
    }

    /** Tests drive the reveal with the same monotonic clock it starts from. */
    void useClock(LongSupplier replacement) { clock = replacement; }

    /** The card drops open vertically: its bottom edge leaves the field fast and settles slowly. */
    private void startReveal() {
        stopReveal();
        if (!animations.enabled()) return;
        revealStart = clock.getAsLong();
        palette.setReveal(0); overlay.repaint();
        reveal = new Timer(8, event -> animate(clock.getAsLong()));
        reveal.start();
    }

    /** One elapsed-time frame of the reveal. */
    void animate(long now) {
        if (reveal == null) return;
        double elapsed = Math.max(0, Math.min(1, (now - revealStart) / (REVEAL_MILLIS * 1_000_000.0)));
        palette.setReveal(drip(elapsed)); overlay.repaint();
        if (elapsed >= 1) stopReveal();
    }

    /** Quartic ease-out: most of the height in the first third, then a slow settle. */
    static double drip(double elapsed) {
        double rest = 1 - elapsed;
        return 1 - rest * rest * rest * rest;
    }

    boolean revealRunning() { return reveal != null; }

    private void stopReveal() {
        if (reveal != null) { reveal.stop(); reveal = null; }
        palette.setReveal(1); overlay.repaint();
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

    /**
     * The card dropped from the title field: over it, centred on it, at least the minimum wide, inside the window and,
     * across, inside {@code clear}, the title row's span clear of the window controls, which draw over the card.
     */
    static Rectangle anchored(Rectangle field, Rectangle window, Rectangle clear, Dimension preferred, int minimumWidth, int margin) {
        int insetX = Math.min(margin, Math.max(0, window.width / 2));
        int left = Math.max(window.x + insetX, clear.x);
        int right = Math.max(left, Math.min(window.x + window.width - insetX, clear.x + clear.width));
        int width = Math.min(Math.max(field.width, minimumWidth), right - left);
        int x = field.x + (field.width - width) / 2;
        x = Math.max(left, Math.min(x, right - width));
        int height = Math.min(preferred.height, Math.max(0, window.y + window.height - margin - field.y));
        return new Rectangle(x, field.y, width, height);
    }

    /** A dragged card: its saved centre and top in {@code available}, at most the preferred size, inside the margin. */
    static Rectangle placed(Rectangle available, Dimension preferred, UiState.PalettePosition position, int margin) {
        Dimension size = fitted(available, preferred, margin);
        int x = available.x + (int) Math.round(available.width * position.centerX()) - size.width / 2;
        int y = available.y + (int) Math.round(available.height * position.top());
        return inside(new Rectangle(x, y, size.width, size.height), available, margin);
    }

    private static Dimension fitted(Rectangle available, Dimension preferred, int margin) {
        int insetX = Math.min(margin, Math.max(0, available.width / 2));
        int insetY = Math.min(margin, Math.max(0, available.height / 2));
        return new Dimension(Math.min(preferred.width, Math.max(0, available.width - 2 * insetX)),
            Math.min(preferred.height, Math.max(0, available.height - 2 * insetY)));
    }

    private static Rectangle inside(Rectangle card, Rectangle available, int margin) {
        int insetX = Math.min(margin, Math.max(0, available.width / 2));
        int insetY = Math.min(margin, Math.max(0, available.height / 2));
        card.x = Math.max(available.x + insetX, Math.min(card.x, available.x + available.width - insetX - card.width));
        card.y = Math.max(available.y + insetY, Math.min(card.y, available.y + available.height - insetY - card.height));
        return card;
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

    boolean dragging() { return drag.location != null; }
    boolean snappedToCenter() { return drag.location != null && drag.snappedX; }
    boolean snappedToTop() { return drag.location != null && drag.snappedY; }
    boolean snappedHome() { return drag.location != null && drag.snappedHome; }

    /** The card's size once dragged: the activated width in either view mode, inside the window. */
    private Dimension detachedSize(Rectangle window) {
        Dimension preferred = palette.getPreferredSize();
        preferred.width = UIScale.scale(ANCHORED_MINIMUM_WIDTH);
        return fitted(window, preferred, margin());
    }

    /**
     * Moves the card by its empty areas. Near the window's vertical centre line or its default top the card snaps to
     * that guide; the guides are felt, never drawn. Releasing saves the place for every window. Dragged back to its home
     * under the title field the card re-attaches there, and releasing forgets the place, as a double click does.
     */
    private final class Drag extends MouseAdapter {
        private Point press, origin, location;
        private boolean snappedX, snappedY, snappedHome;

        @Override public void mousePressed(MouseEvent event) {
            cancel();
            if (!SwingUtilities.isLeftMouseButton(event) || !controller.isOpen()) return;
            press = SwingUtilities.convertPoint(palette, event.getPoint(), overlay);
            origin = palette.getLocation();
        }

        @Override public void mouseDragged(MouseEvent event) {
            if (press == null || !controller.isOpen()) return;
            Point at = SwingUtilities.convertPoint(palette, event.getPoint(), overlay);
            int dx = at.x - press.x, dy = at.y - press.y;
            if (location == null && Math.hypot(dx, dy) < UIScale.scale(DRAG_THRESHOLD)) return;
            Rectangle window = overlay.workspace();
            if (window.width <= 0 || window.height <= 0) return;
            if (location == null) { stopReveal(); palette.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); }
            int x = origin.x + dx, y = origin.y + dy, snap = UIScale.scale(SNAP_DISTANCE);
            // Back at (or above) its home under the title field, the card re-attaches there.
            Rectangle home = overlay.home();
            int reach = UIScale.scale(HOME_DISTANCE);
            snappedHome = home != null && Math.abs(x - home.x) <= reach && y <= home.y + reach;
            if (snappedHome) {
                snappedX = false; snappedY = false;
                location = home.getLocation();
                overlay.doLayout(); overlay.repaint();
                return;
            }
            Dimension size = detachedSize(window);
            int guideX = guideX(window), guideY = guideY(window);
            snappedX = Math.abs(x + size.width / 2 - guideX) <= snap;
            if (snappedX) x = guideX - size.width / 2;
            snappedY = Math.abs(y - guideY) <= snap;
            if (snappedY) y = guideY;
            Rectangle card = inside(new Rectangle(x, y, size.width, size.height), window, margin());
            snappedX &= card.x == x; snappedY &= card.y == y;
            location = card.getLocation();
            overlay.doLayout(); overlay.repaint();
        }

        @Override public void mouseReleased(MouseEvent event) {
            if (location != null && snappedHome) {
                owner.uiState().setPalettePosition(null);
                owner.uiState().save();
            } else if (location != null) {
                Rectangle window = overlay.workspace();
                Dimension size = detachedSize(window);
                double centerX = snappedX ? GUIDE_CENTER : fraction((location.x + size.width / 2.0 - window.x) / window.width);
                double top = snappedY ? GUIDE_TOP : fraction((location.y - window.y) / (double) window.height);
                owner.uiState().setPalettePosition(new UiState.PalettePosition(centerX, top));
                owner.uiState().save();
            }
            cancel();
        }

        @Override public void mouseClicked(MouseEvent event) {
            if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 2 || !controller.isOpen()) return;
            owner.uiState().setPalettePosition(null);
            owner.uiState().save();
            layoutOverlay();
        }

        void cancel() {
            boolean moved = location != null;
            press = null; origin = null; location = null; snappedX = false; snappedY = false; snappedHome = false;
            if (moved) { palette.setCursor(null); layoutOverlay(); }
        }

        private static double fraction(double value) { return Math.max(0, Math.min(1, value)); }
    }

    /** The card's inset from the window's edges. */
    private static int margin() { return NativeStyles.installed().metric(DesignMetrics.Role.PALETTE_WINDOW_MARGIN); }

    private static int guideX(Rectangle window) { return window.x + (int) Math.round(window.width * GUIDE_CENTER); }
    private static int guideY(Rectangle window) { return window.y + (int) Math.round(window.height * GUIDE_TOP); }

    @Override public void close() {
        if (closed) return;
        stopReveal(); controller.close(); closed = true; uninstall();
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
        /** The workspace, which spans the window including its rail and tool panels. */
        Rectangle workspace() { return SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), this); }

        /** The card's default place, dropped from the title field; null in Compact mode or without a usable field. */
        Rectangle home() {
            Rectangle field = owner.paletteAnchor(this);
            return field == null ? null : anchored(field, new Rectangle(0, 0, getWidth(), getHeight()), owner.titleRowClear(this),
                palette.getPreferredSize(), UIScale.scale(ANCHORED_MINIMUM_WIDTH), margin());
        }

        @Override public void doLayout() {
            if (root == null || !SwingUtilities.isDescendingFrom(owner, root.getLayeredPane())) return;
            if (drag.location != null) {
                Rectangle home = drag.snappedHome ? home() : null;
                palette.setBounds(home != null ? home : new Rectangle(drag.location, detachedSize(workspace())));
                return;
            }
            var saved = owner.uiState().palettePosition();
            if (saved.isPresent()) {
                Rectangle window = workspace();
                palette.setBounds(placed(window, detachedSize(window), saved.get(), margin()));
                return;
            }
            Rectangle home = home();
            if (home != null) {
                palette.setBounds(home);
                return;
            }
            // No usable field: centre across the workspace, which spans the window including its rail and
            // tool panels. Panel dividers must not move or narrow the palette.
            Rectangle window = workspace();
            Dimension preferred = palette.getPreferredSize();
            // Compact changes placement while retaining the title-anchored card's activated width.
            if (owner.viewMode() == ViewMode.COMPACT) preferred.width = UIScale.scale(ANCHORED_MINIMUM_WIDTH);
            palette.setBounds(positioned(window, preferred, margin()));
        }
        @Override protected void paintComponent(Graphics graphics) {
            if (!controller.isOpen()) return;
            Rectangle card = palette.getBounds();
            card.height = palette.revealedHeight();
            if (card.height <= 0) return;
            ChromePainter.installed().popupShadow(graphics, card, ThemeZone.theme(null), owner.theme().chrome().dark());
        }
    }
}
