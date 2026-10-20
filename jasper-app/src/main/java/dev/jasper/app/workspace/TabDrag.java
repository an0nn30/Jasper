package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.WorkspaceDropResolver;
import dev.jasper.app.dock.WorkspaceDropResolver.Drop;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * The tab drag (workspace tabs spec 12). A left press on a tab in a strip of either face that moves past the kit's tab
 * drag threshold ({@code TAB_REORDER_THRESHOLD}, 5 px by default) starts it. It is the strips' only drag, so reordering a
 * strip and moving a tab to another group are one gesture. It uses plain mouse events in screen coordinates, never AWT
 * drag and drop: Swing keeps sending the pressed component its drag events wherever the cursor goes, and synthetic events
 * drive it in tests. While it runs, {@link TabDragLabel} shows the tab's label beside the cursor and the target window's
 * layered pane shows where the tab would land ({@link DockDropOverlay}, from {@link WorkspaceDropResolver#landing});
 * nothing moves until the release.
 * <p>The target: of the windows under the cursor, the most recently active; over its workspace, the resolver's drop
 * unless it would change nothing; anywhere else in that window (its dock, chrome or title bar), and outside every
 * window, nothing. A pinned tab (a modal owner holds it in its window) takes targets in its own window only. Releasing
 * the left button carries the target out through the workspace's layout operations, which never touch a tab's content
 * or session. Releasing on no target, Esc, another press or a plain move (both mean Swing lost the release), the drag's
 * window losing the focus (a modal dialog, another application), and the dragged tab closing or leaving its window or
 * its group (a command moving it) end the drag without a drop. EDT only.
 */
final class TabDrag {
    /** Where a release would put the dragged tab. */
    sealed interface Target {
        /**
         * Into {@code window}'s workspace, where {@code drop} says.
         *
         * @param window the window
         * @param drop the drop over its workspace
         */
        record Into(WindowContent window, Drop drop) implements Target { }
    }

    /** How far right of and below the cursor the label's corner sits, so no window is ever under the pointer's tip. */
    static final int LABEL_OFFSET = 8;

    private final Supplier<List<WindowContent>> windows;
    private final DockDropOverlay overlay = new DockDropOverlay();
    private final TabDragLabel label = new TabDragLabel();
    private final KeyEventDispatcher escape = this::dispatchKeyEvent;
    private final AWTEventListener lostRelease = this::lostRelease;
    /** The focus manager the Esc dispatcher went to, while a drag runs. */
    private KeyboardFocusManager keys;
    /** While a press is pending or a drag runs: the tab's group, the tab, the component the press reached, the press on screen. */
    private GroupView group;
    private Tab tab;
    private Component source;
    private Point pressedAt;
    private boolean dragging;
    /** End the drag when the dragged tab closes, or changes so that it is no longer where the press found it. */
    private Subscription closing, changed;
    private Optional<Target> target = Optional.empty();

    /** @param windows the windows a drag can reach, the most recently active first */
    TabDrag(Supplier<List<WindowContent>> windows) {
        this.windows = Objects.requireNonNull(windows, "windows");
    }

    /** @return whether a drag runs: from the threshold to its release or its end */
    boolean active() { return dragging; }

    /** @return the dragged tab while a drag runs, else null */
    Tab dragged() { return dragging ? tab : null; }

    /** @return where a release would put the tab now */
    Optional<Target> target() { return target; }

    DockDropOverlay overlay() { return overlay; }

    TabDragLabel label() { return label; }

    /**
     * A left press on {@code tab}'s entry in {@code group}'s strip: a drag may start from it. A press while another is
     * pending or a drag runs ends that one first.
     */
    void pressed(GroupView group, Tab tab, MouseEvent event) {
        if (this.tab != null) cancel();
        if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger() || !group.tabs().contains(tab)) return;
        this.group = group;
        this.tab = tab;
        source = event.getComponent();
        pressedAt = screen(event);
    }

    /**
     * A drag event from the pressed component: past the threshold the drag starts, then follows the cursor. A tab that
     * closed or moved since the press has nothing to drag.
     */
    void dragged(MouseEvent event) {
        if (tab == null || event.getComponent() != source) return;
        Point at = screen(event);
        if (!dragging) {
            if (at.distance(pressedAt) <= NativeStyles.installed().metric(DesignMetrics.Role.TAB_REORDER_THRESHOLD)) return;
            if (!inPlace()) {
                cancel();
                return;
            }
            try {
                start(at);
            } catch (RuntimeException | Error failure) {
                finish();
                throw failure;
            }
        }
        update(at);
    }

    /**
     * The left button's release on the pressed component: a running drag drops where its target says. The label and the
     * landing go whatever happens.
     */
    void released(MouseEvent event) {
        if (tab == null || event.getComponent() != source || event.getButton() != MouseEvent.BUTTON1) return;
        if (!dragging) {
            finish();
            return;
        }
        Tab moving = tab;
        Optional<Target> where = Optional.empty();
        try {
            update(screen(event));
            where = target;
        } finally {
            finish();
        }
        where.ifPresent(place -> drop(moving, place));
    }

    /** Ends a running drag or a pending press without a drop. */
    void cancel() { finish(); }

    /** {@code window} lost the focus or closed: a press or a drag from it ends without a drop. */
    void cancelFrom(WindowContent window) {
        if (group != null && group.owner() == window) cancel();
    }

    /** While a drag runs it owns Esc, wherever the focus is: Esc ends it. */
    boolean dispatchKeyEvent(KeyEvent event) {
        if (!dragging || event.getKeyCode() != KeyEvent.VK_ESCAPE) return false;
        if (event.getID() == KeyEvent.KEY_PRESSED) cancel();
        return true;
    }

    /**
     * A release Swing lost: while the left button is held, Swing sends only drag events, to the pressed component, so a
     * plain move or a press anywhere means the drag's release never came.
     */
    private void lostRelease(AWTEvent event) {
        if (dragging && (event.getID() == MouseEvent.MOUSE_MOVED || event.getID() == MouseEvent.MOUSE_PRESSED)) cancel();
    }

    /** Whether the tab is still where the press found it: open, in the press's window and in the press's group. */
    private boolean inPlace() {
        return tab != null && !tab.closed() && Objects.equals(tab.window(), group.owner().id())
            && group.owner().workspace().model().groupOf(tab.id()).filter(group.id()::equals).isPresent();
    }

    /** Starts the drag: what ends it is in place before anything shows, so {@link #finish} can always take it away. */
    private void start(Point at) {
        dragging = true;
        keys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        keys.addKeyEventDispatcher(escape);
        Toolkit.getDefaultToolkit().addAWTEventListener(lostRelease, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        closing = tab.onClosed(this::cancel);
        // A command that moves the tab while it is dragged (Move Tab to New Window from the keyboard) ends the drag.
        changed = tab.onChanged(() -> { if (!inPlace()) cancel(); });
        // The title button saw the press: its release after a drag must not click it too.
        if (source instanceof AbstractButton button) {
            button.getModel().setArmed(false);
            button.getModel().setPressed(false);
        }
        label.show(WindowTabs.capped(group.title(tab)), group.icon(tab), labelAt(at));
    }

    /** Finds the target under {@code at}, moves the label and shows the landing; a tab no longer in place ends the drag. */
    private void update(Point at) {
        if (!inPlace()) {
            cancel();
            return;
        }
        label.move(labelAt(at));
        target = Optional.empty();
        List<WindowContent> open = windows.get().stream().filter(window -> !window.closed()).toList();
        OptionalInt under = WorkspaceDropResolver.windowAt(at, open.stream().map(TabDrag::onScreen).toList());
        if (under.isPresent()) {
            WindowContent window = open.get(under.getAsInt());
            // A pinned tab stays in its window, as the registry keeps it there: no other window takes it.
            if (!tab.pinned() || window == group.owner()) target = into(window, at);
        }
        showLanding();
    }

    /** The drop into {@code window}'s workspace at {@code at}; empty outside its workspace or for a drop that would change nothing. */
    private Optional<Target> into(WindowContent window, Point at) {
        WorkspaceView workspace = window.workspace();
        Point local = new Point(at);
        SwingUtilities.convertPointFromScreen(local, workspace);
        return WorkspaceDropResolver.resolve(local, workspace.dropAreas(tab)).filter(drop -> workspace.changes(tab, drop))
            .map(drop -> new Target.Into(window, drop));
    }

    /**
     * Shows the target's landing on its window's layered pane: the area the tab would take as the highlight, or for a strip
     * drop a bar where it would go in the strip as the placeholder. Without a target the overlay leaves its window.
     */
    private void showLanding() {
        WindowContent window = target.orElse(null) instanceof Target.Into into ? into.window() : null;
        JLayeredPane layers = window == null ? null : layers(window);
        Container parent = overlay.getParent();
        if (parent != null && parent != layers) {
            overlay.clear();
            parent.remove(overlay);
            parent.repaint();
        }
        if (layers == null) return;
        Drop drop = ((Target.Into) target.orElseThrow()).drop();
        WorkspaceView workspace = window.workspace();
        Rectangle landing = SwingUtilities.convertRectangle(workspace, WorkspaceDropResolver.landing(drop, workspace.dropAreas(tab),
            NativeStyles.installed().metric(DesignMetrics.Role.TAB_INDICATOR)), layers);
        if (overlay.getParent() == null) layers.add(overlay, JLayeredPane.DRAG_LAYER);
        overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        if (drop instanceof Drop.Strip) overlay.show(null, landing, null, null);
        else overlay.show(landing, null, null, null);
    }

    /** Ends a pending press or a drag: its subscriptions, its listeners, the label and the landing go. */
    private void finish() {
        boolean wasDragging = dragging;
        dragging = false;
        group = null;
        tab = null;
        source = null;
        pressedAt = null;
        target = Optional.empty();
        if (closing != null) {
            closing.close();
            closing = null;
        }
        if (changed != null) {
            changed.close();
            changed = null;
        }
        if (!wasDragging) return;
        if (keys != null) {
            keys.removeKeyEventDispatcher(escape);
            keys = null;
        }
        Toolkit.getDefaultToolkit().removeAWTEventListener(lostRelease);
        label.hide();
        showLanding();
    }

    /** Carries a release out: the window's workspace places the tab; its content and session are never touched. */
    private void drop(Tab moving, Target where) {
        if (moving.closed() || !(where instanceof Target.Into into) || into.window().closed()) return;
        into.window().workspace().drop(moving, into.drop());
    }

    /** Where the label's corner goes for the cursor at {@code at}. */
    private static Point labelAt(Point at) {
        int offset = UIScale.scale(LABEL_OFFSET);
        return new Point(at.x + offset, at.y + offset);
    }

    /** Where {@code event} happened, on screen. */
    private static Point screen(MouseEvent event) {
        Point point = new Point(event.getPoint());
        SwingUtilities.convertPointToScreen(point, event.getComponent());
        return point;
    }

    /** {@code window}'s native window, or for content in no native window (a headless test's) its outermost component. */
    private static Component outermost(WindowContent window) {
        Component top = window;
        while (!(top instanceof Window) && top.getParent() != null) top = top.getParent();
        return top;
    }

    /**
     * {@code window}'s frame on screen, title bar and all: its native window's bounds, or its outermost component's for
     * content in no native window; empty while it does not show or is minimized.
     */
    private static Rectangle onScreen(WindowContent window) {
        Component top = outermost(window);
        boolean shows = top instanceof Window frame
            ? frame.isShowing() && !(frame instanceof Frame decorated && (decorated.getExtendedState() & Frame.ICONIFIED) != 0)
            : top.isVisible();
        if (!shows) return new Rectangle();
        Point origin = new Point();
        SwingUtilities.convertPointToScreen(origin, top);
        return new Rectangle(origin, top.getSize());
    }

    private static JLayeredPane layers(WindowContent window) {
        JRootPane root = SwingUtilities.getRootPane(window);
        return root == null ? null : root.getLayeredPane();
    }
}
