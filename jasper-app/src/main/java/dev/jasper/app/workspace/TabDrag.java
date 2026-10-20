package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.SystemInfo;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.WorkspaceDropResolver;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
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
 * The tab drag (workspace tabs spec 12; tabs and panes spec 6). A left press on a tab's entry in its window's strip,
 * the {@link ParentTabBar}, that moves past the kit's tab drag threshold ({@code TAB_REORDER_THRESHOLD}, 5 px by
 * default) starts it, carrying the whole tab with every pane of it. It uses plain mouse events in screen coordinates,
 * never AWT drag and drop: Swing keeps sending the pressed component its drag events wherever the cursor goes, and
 * synthetic events drive it in tests. While it runs, {@link TabDragLabel} shows the entry's label beside the cursor and
 * the target window's layered pane shows where the tab would land ({@link DockDropOverlay}); nothing moves until the
 * release.
 * <p>The target: of the windows under the cursor that can take a drop (on macOS none in full screen on the display of
 * the drag's own window but that window), the most recently active. Over its strip, the place before the first of the
 * strip's other entries whose middle is not left of the cursor, where the entries take their new places at once on the
 * release; a window whose strip hides (the standard look's one tab) takes it at its end over the band its strip takes
 * when it shows, and the main window's empty workspace, which has no panes, anywhere over it, in its place. Anywhere
 * else in that window (a pane, its dock, chrome or title bar), and over any other window of Jasper's (Settings, About, a
 * dialog), nothing; outside every window, a new full window the size of the tab's workspace, with the entry where the
 * cursor holds it and kept on the display under the cursor, except for a window's only tab (a full window's already is
 * its own; workspace plan 8a Rulings 6). A tab holding an item that is pinned (a modal owner holds it in its window), or
 * one being closed, takes targets along its own strip only. Releasing the left button carries the target out:
 * along its own strip as a reorder, into another window or out of every window through the window manager of the
 * drag's window, which moves the tab's items through the tab registry; none touches an item's content or session, and a
 * window no manager opened moves nothing out of itself. Releasing on no target, Esc, another press or a plain move
 * (both mean Swing lost the release), the drag's window losing the focus (a modal dialog, another application), and the
 * item the entry shows closing, leaving its tab or giving way to another of its panes end the drag without a drop. EDT
 * only.
 */
final class TabDrag {
    /** Where a release would put the dragged tab. */
    sealed interface Target {
        /**
         * Into {@code window}'s strip at {@code index} (tabs and panes spec 6).
         *
         * @param window the window
         * @param index the tab's place among the strip's entries other than its own
         */
        record Bar(WindowContent window, int index) implements Target { }

        /**
         * Out of every window, into a new full window whose workspace takes {@code bounds}, on the display under
         * {@code cursor} (tabs and panes spec 6).
         *
         * @param bounds the new window's workspace on screen; its frame adds its title bar
         * @param cursor the cursor on screen, inside {@code bounds}
         */
        record TearOut(Rectangle bounds, Point cursor) implements Target { }
    }

    /** How far right of and below the cursor the label's corner sits, so no window is ever under the pointer's tip. */
    static final int LABEL_OFFSET = 8;

    private final Supplier<List<WindowContent>> windows;
    private final DockDropOverlay overlay = new DockDropOverlay();
    private final TabDragLabel label = new TabDragLabel();
    /**
     * Test seam: the frames on screen of Jasper's other showing windows (Settings, About, Licenses, a dialog, a popup),
     * where a release drops nothing; none of the windows the drag reaches, nor the label's. Production reads
     * {@link Window#getWindows()}, which a headless JVM leaves empty.
     */
    Supplier<List<Rectangle>> otherWindows = this::auxiliaryFrames;
    private final KeyEventDispatcher escape = this::dispatchKeyEvent;
    private final AWTEventListener lostRelease = this::lostRelease;
    /** The focus manager the Esc dispatcher went to, while a drag runs. */
    private KeyboardFocusManager keys;
    /**
     * While a press is pending or a drag runs: the drag's window; the dragged tab; the item its entry shows; the
     * component the press reached; the press on screen.
     */
    private WindowContent from;
    private ParentTabId parentTab;
    private Tab tab;
    private Component source;
    private Point pressedAt;
    /** Where the press held the entry, from its top-left corner, and the size of the tab's workspace: a tear-out's window. */
    private Point grab;
    private Dimension size;
    private boolean dragging;
    /** End the drag when the item the entry shows closes, leaves the tab or gives way to another of its panes. */
    private Subscription closing, changed, shown;
    private Optional<Target> target = Optional.empty();

    /**
     * @param windows the windows a drag can reach, the most recently active first; what leaves its window goes through
     *     the window manager of the drag's window ({@code WindowContent.manager()})
     */
    TabDrag(Supplier<List<WindowContent>> windows) {
        this.windows = Objects.requireNonNull(windows, "windows");
    }

    /** @return whether a drag runs: from the threshold to its release or its end */
    boolean active() { return dragging; }

    /** @return the item the dragged tab's entry shows, while a drag runs; else null */
    Tab dragged() { return dragging ? tab : null; }

    /** @return the dragged tab while a drag runs, else null */
    ParentTabId draggedParentTab() { return dragging ? parentTab : null; }

    /** @return where a release would put the tab now */
    Optional<Target> target() { return target; }

    DockDropOverlay overlay() { return overlay; }

    TabDragLabel label() { return label; }

    /**
     * A left press on {@code tab}'s entry in {@code bar}, the entry of the tab whose active pane holds it: a drag of that
     * whole tab may start from it. A press while another is pending or a drag runs ends that one first; any other press
     * starts nothing.
     */
    void pressed(ParentTabBar bar, Tab tab, MouseEvent event) {
        if (this.tab != null) cancel();
        WindowContent window = bar.owner();
        Optional<ParentTabId> id = window.parentTabOf(tab);
        Optional<WorkspaceView> view = id.flatMap(window.parentTabs()::view);
        List<Rectangle> places = bar.strip().tabBounds();
        int index = bar.tabs().indexOf(tab);
        if (view.isEmpty() || !SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger() || index < 0
                || index >= places.size()) return;
        from = window;
        parentTab = id.get();
        this.tab = tab;
        source = event.getComponent();
        pressedAt = screen(event);
        Point inStrip = SwingUtilities.convertPoint(source, event.getPoint(), bar.strip().component());
        grab = new Point(inStrip.x - places.get(index).x, inStrip.y - places.get(index).y);
        size = view.get().getSize();
    }

    /**
     * A drag event from the pressed component: past the threshold with the left button held the drag starts, then follows
     * the cursor. An entry that changed since the press has nothing to drag.
     */
    void dragged(MouseEvent event) {
        if (tab == null || event.getComponent() != source) return;
        Point at = screen(event);
        if (!dragging) {
            // Swing sends the pressed component a drag with any button held: only the left one drags a tab.
            if (!SwingUtilities.isLeftMouseButton(event)) return;
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
        WindowContent home = from;
        ParentTabId parent = parentTab;
        Optional<Target> where = Optional.empty();
        try {
            update(screen(event));
            where = target;
        } finally {
            finish();
        }
        where.ifPresent(place -> drop(moving, home, parent, place));
    }

    /** Ends a running drag or a pending press without a drop. */
    void cancel() { finish(); }

    /** {@code window} lost the focus or closed: a press or a drag from it ends without a drop. */
    void cancelFrom(WindowContent window) {
        if (window != null && from == window) cancel();
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

    /**
     * Whether the entry is still where the press found it: its item open and in the press's window, and still the item
     * the press's tab shows.
     */
    private boolean inPlace() {
        if (tab == null || tab.closed() || !Objects.equals(tab.window(), from.id())) return false;
        return from.parentTabs().view(parentTab).map(WorkspaceView::selected).filter(tab::equals).isPresent();
    }

    /** Starts the drag: what ends it is in place before anything shows, so {@link #finish} can always take it away. */
    private void start(Point at) {
        dragging = true;
        keys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        keys.addKeyEventDispatcher(escape);
        Toolkit.getDefaultToolkit().addAWTEventListener(lostRelease, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        closing = tab.onClosed(this::cancel);
        // A command that moves the item while its tab is dragged (Move to Main Window from the keyboard) ends the drag.
        changed = tab.onChanged(() -> { if (!inPlace()) cancel(); });
        // The entry giving way to another pane of its tab (a click there, a request opening there) ends the drag.
        shown = tab.onActive(active -> { if (!inPlace()) cancel(); });
        // The title button saw the press: its release after a drag must not click it too.
        if (source instanceof AbstractButton button) {
            button.getModel().setArmed(false);
            button.getModel().setPressed(false);
        }
        label.show(from.tabTitle(tab), from.tabIcon(tab), labelAt(at));
    }

    /** Finds the target under {@code at}, moves the label and shows the landing; an entry no longer in place ends the drag. */
    private void update(Point at) {
        if (!inPlace()) {
            cancel();
            return;
        }
        label.move(labelAt(at));
        target = Optional.empty();
        List<WindowContent> open = windows.get().stream().filter(window -> !window.closed()).toList();
        GraphicsDevice home = device(from);
        OptionalInt under = WorkspaceDropResolver.windowAt(at,
            open.stream().map(window -> onScreen(window, window == from, home)).toList());
        // What the registry keeps in its window stays there: a tab holding an item that is pinned or being closed. No
        // other window takes it, and it tears nothing out.
        boolean stays = !movable();
        if (under.isPresent()) {
            WindowContent window = open.get(under.getAsInt());
            // A tab drops on a strip alone (tabs and panes spec 6).
            if (!stays || window == from) target = onBar(window, at);
        } else if (WorkspaceDropResolver.windowAt(at, otherWindows.get()).isEmpty() && !stays && tearsOut()) {
            // Outside every window of Jasper's: a full window.
            target = Optional.of(new Target.TearOut(tearOutBounds(at), new Point(at)));
        }
        showLanding();
    }

    /**
     * The dragged tab's drop on {@code window}'s strip at {@code at}: before the first of the strip's other entries whose
     * middle is not left of the cursor; while another window's strip hides, at its end, over the band the strip takes
     * when it shows (workspace plan 7c Rulings 5); on the main window's empty workspace, which has no panes, anywhere over
     * it, in its place. Empty off the strip, and for the place the tab has.
     */
    private Optional<Target> onBar(WindowContent window, Point at) {
        boolean shows = window.parentTabBar().component().isVisible();
        if (window == from && !shows) return Optional.empty();
        Point local = new Point(at);
        SwingUtilities.convertPointFromScreen(local, window.parentTabs());
        Rectangle area = barArea(window);
        OptionalInt index = OptionalInt.empty();
        if (window != from && window.parentTabs().isEmpty()) {
            if (new Rectangle(window.parentTabs().getSize()).contains(local)) index = OptionalInt.of(0);
        } else if (shows) {
            index = WorkspaceDropResolver.place(local, area, barEntries(window));
        } else if (area.contains(local)) {
            index = OptionalInt.of(window.parentTabs().bar().tabs().size());
        }
        if (index.isEmpty() || (window == from && index.getAsInt() == from.parentTabs().model().indexOf(parentTab)))
            return Optional.empty();
        return Optional.of(new Target.Bar(window, index.getAsInt()));
    }

    /**
     * {@code window}'s strip in its parent-tabs view's coordinates: the strip while it shows, else the band at the view's
     * top that it takes when it shows.
     */
    private static Rectangle barArea(WindowContent window) {
        Component bar = window.parentTabBar().component();
        if (bar.isVisible()) return bar.getBounds();
        return new Rectangle(0, 0, window.parentTabs().getWidth(), bar.getPreferredSize().height);
    }

    /**
     * Whether the registry would move every item of the dragged tab: none pinned or being closed
     * ({@code TabRegistry.movable}; workspace plan 7b Rulings 7).
     */
    private boolean movable() {
        Optional<WorkspaceView> view = from.parentTabs().view(parentTab);
        return view.isPresent() && view.get().tabs().stream().allMatch(from.tabRegistry()::movable);
    }

    /** Whether a release outside every window takes the tab out of its window: never a window's only tab (Rulings 6). */
    private boolean tearsOut() { return from.mayMoveTabToNewWindow(parentTab); }

    /** The strip's entries in {@code window}'s parent-tabs view's coordinates, in strip order, without the dragged one's own. */
    private List<Rectangle> barEntries(WindowContent window) {
        TabStrip bar = window.parentTabBar();
        List<Tab> listed = window.parentTabs().bar().tabs();
        List<Rectangle> bounds = bar.tabBounds();
        var entries = new ArrayList<Rectangle>();
        for (int i = 0; i < Math.min(listed.size(), bounds.size()); i++) {
            if (listed.get(i) == tab) continue;
            entries.add(SwingUtilities.convertRectangle(bar.component(), bounds.get(i), window.parentTabs()));
        }
        return entries;
    }

    /**
     * Shows the target's landing on its window's layered pane: a bar where the tab would go in the strip as the
     * placeholder, or the band of a strip that hides, or the main window's whole empty workspace, as the highlight.
     * Without a target in a window the overlay leaves its window.
     */
    private void showLanding() {
        Target.Bar bar = target.orElse(null) instanceof Target.Bar strip ? strip : null;
        WindowContent window = bar == null ? null : bar.window();
        JLayeredPane layers = window == null ? null : layers(window);
        Container parent = overlay.getParent();
        if (parent != null && parent != layers) {
            overlay.clear();
            parent.remove(overlay);
            parent.repaint();
        }
        if (layers == null) return;
        int marker = NativeStyles.installed().metric(DesignMetrics.Role.TAB_INDICATOR);
        Rectangle highlight = null, placeholder = null;
        ParentTabsView parents = window.parentTabs();
        // The main window's empty workspace gives way to the tab: all of it shows.
        if (parents.isEmpty())
            highlight = SwingUtilities.convertRectangle(parents, new Rectangle(parents.getSize()), layers);
        // A strip that hides takes the tab at its end: its band shows (workspace plan 7c Rulings 5).
        else if (!window.parentTabBar().component().isVisible())
            highlight = SwingUtilities.convertRectangle(parents, barArea(window), layers);
        else placeholder = SwingUtilities.convertRectangle(parents,
            WorkspaceDropResolver.marker(barArea(window), barEntries(window), bar.index(), marker), layers);
        if (overlay.getParent() == null) layers.add(overlay, JLayeredPane.DRAG_LAYER);
        overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        overlay.show(highlight, placeholder, null, null);
    }

    /** Ends a pending press or a drag: its subscriptions, its listeners, the label and the landing go. */
    private void finish() {
        boolean wasDragging = dragging;
        dragging = false;
        from = null;
        parentTab = null;
        tab = null;
        source = null;
        pressedAt = null;
        grab = null;
        size = null;
        target = Optional.empty();
        if (closing != null) {
            closing.close();
            closing = null;
        }
        if (changed != null) {
            changed.close();
            changed = null;
        }
        if (shown != null) {
            shown.close();
            shown = null;
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

    /**
     * Carries a release out; nothing touches an item's content or session. Along its own strip the tab takes its new
     * place there; on another window's strip, or out of every window into a full window, the window manager moves it
     * whole ({@code home}, the drag's window, holds {@code parent}). A window no manager opened moves nothing out of
     * itself.
     */
    private void drop(Tab moving, WindowContent home, ParentTabId parent, Target where) {
        if (moving.closed()) return;
        switch (where) {
            case Target.Bar bar -> {
                if (bar.window() == home) home.reorderParentTab(parent, bar.index());
                // Another window's strip: the window manager moves the tab whole, through the tab registry.
                else home.manager().ifPresent(manager -> manager.moveParentTab(home, parent, bar.window(), bar.index()));
            }
            case Target.TearOut out -> home.manager().ifPresent(manager -> manager.moveTabToNewWindow(home, parent, out.bounds(), out.cursor()));
        }
    }

    /**
     * A tear-out's workspace on screen: the size of the tab's workspace, with the entry where the cursor holds it, as
     * IntelliJ and VS Code size a window from the content dragged out of it. The new frame grows it by its own title bar
     * and keeps itself on the display under the cursor ({@code WindowFrame}).
     */
    private Rectangle tearOutBounds(Point at) {
        return new Rectangle(at.x - grab.x, at.y - grab.y, size.width, size.height);
    }

    /** The frames on screen of Jasper's showing windows the drag does not reach, but the label's: see {@link #otherWindows}. */
    private List<Rectangle> auxiliaryFrames() {
        var reached = new HashSet<Component>();
        for (WindowContent window : windows.get()) reached.add(outermost(window));
        var frames = new ArrayList<Rectangle>();
        for (Window window : Window.getWindows())
            if (window.isShowing() && !reached.contains(window) && !label.owns(window)) frames.add(window.getBounds());
        return frames;
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

    /** The display {@code window}'s native window is on; null for content in no native window. */
    private static GraphicsDevice device(WindowContent window) {
        GraphicsConfiguration display = outermost(window) instanceof Window frame ? frame.getGraphicsConfiguration() : null;
        return display == null ? null : display.getDevice();
    }

    /**
     * {@code window}'s frame on screen, title bar and all, where it can take a drop: its native window's bounds (empty while
     * it does not show, is minimized, or by {@link #dropFrame} is out of sight in full screen), or its outermost component's
     * for content in no native window. {@code home} is the display of the drag's own window.
     */
    private static Rectangle onScreen(WindowContent window, boolean source, GraphicsDevice home) {
        Component top = outermost(window);
        if (top instanceof Window frame) {
            if (!frame.isShowing() || frame instanceof Frame decorated && (decorated.getExtendedState() & Frame.ICONIFIED) != 0)
                return new Rectangle();
            GraphicsConfiguration display = frame.getGraphicsConfiguration();
            return dropFrame(frame.getBounds(), display == null ? null : display.getBounds(), source,
                display != null && display.getDevice().equals(home), SystemInfo.isMacOS);
        }
        if (!top.isVisible()) return new Rectangle();
        Point origin = new Point();
        SwingUtilities.convertPointToScreen(origin, top);
        return new Rectangle(origin, top.getSize());
    }

    /**
     * Where a window takes drops. On macOS a window whose frame is exactly its display's bounds is in full screen (which
     * covers the menu bar too), on a Space of its own. On the display of the drag's own window the user cannot see that
     * Space while dragging from another window: a window other than the drag's own takes none there. Each display shows a
     * Space of its own (macOS's default, "Displays have separate Spaces"), so on another display a window in full screen
     * can be in sight: it takes drops. Java sees no other Space, so a window on one that is not in full screen still takes
     * drops where its frame lies. Elsewhere a window covering its display is in sight (a tiling window manager's), so the
     * rule is macOS's alone.
     *
     * @param frame the window's frame on screen
     * @param display its display's bounds, or null when unknown
     * @param source whether it is the drag's own window
     * @param onSourceDisplay whether it is on the display of the drag's own window
     * @param macOs whether this is macOS
     * @return the frame, or an empty rectangle where it takes no drop
     */
    static Rectangle dropFrame(Rectangle frame, Rectangle display, boolean source, boolean onSourceDisplay, boolean macOs) {
        if (macOs && !source && onSourceDisplay && frame.equals(display)) return new Rectangle();
        return new Rectangle(frame);
    }

    private static JLayeredPane layers(WindowContent window) {
        JRootPane root = SwingUtilities.getRootPane(window);
        return root == null ? null : root.getLayeredPane();
    }
}
