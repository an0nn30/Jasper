package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.SystemInfo;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.WorkspaceDropResolver;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * The tab drag and the pane drag (workspace tabs spec 12; tabs and panes spec 6 and 7). A left press on a tab's entry in
 * its window's strip, the {@link ParentTabBar}, that moves past the kit's tab drag threshold
 * ({@code TAB_REORDER_THRESHOLD}, 5 px by default) starts a tab drag, carrying the whole tab with every pane of it. A
 * left press with the pane drag's modifier ({@link #movesPane}: Cmd+Option on macOS, Ctrl+Alt elsewhere) on an item of a
 * window's shown tab, which that window hands over ({@link #pressedPane}), starts a pane drag past the same threshold,
 * carrying the item's pane. Every event of a pane's gesture, from its press to its left button's release, is the pane
 * drag's, whether the drag runs, has ended or never starts: the window consumes the press, and this drag the press's
 * drags and its release, before any listener of the item hears them, so a terminal neither reports them to its program
 * nor selects and a text view neither moves its caret nor drags its text. Both drags use plain mouse events in screen
 * coordinates, never AWT drag and drop: Swing keeps sending the pressed component its drag events wherever the cursor
 * goes, and synthetic events drive them in tests. While a drag runs, {@link TabDragLabel} shows the entry's or the item's
 * label beside the cursor and the target window's layered pane shows where it would land ({@link DockDropOverlay});
 * nothing moves until the release.
 * <p>The target: of the windows under the cursor that can take a drop (on macOS none in full screen on the display of
 * the drag's own window but that window), the most recently active. For a tab, over its strip, the place before the
 * first of the strip's other entries whose middle is not left of the cursor, where the entries take their new places at
 * once on the release; a window whose strip hides (the standard look's one tab) takes it at its end over the band its
 * strip takes when it shows, and the main window's empty workspace, which has no panes, anywhere over it, in its place.
 * Over a pane of a window's shown tab other than the dragged tab's own, beside that pane on the side whose edge the
 * cursor is nearest, every pane of the tab as one nested split (pane flexibility spec 5); the drag's own window shows the
 * dragged tab, so its other tabs' panes take it once spring-loading shows one. Anywhere else in that window (its dock,
 * chrome or title bar), and over any other window of Jasper's (Settings, About, a dialog), nothing; outside every window,
 * a new full window the size of the tab's workspace, with the entry
 * where the cursor holds it and kept on the display under the cursor, except for a window's only tab (a full window's
 * already is its own; workspace plan 8a Rulings 6). A tab holding an item that is pinned (a modal owner holds it in its
 * window), or one being closed, takes targets along its own strip only.
 * <p>A pane's targets (tabs and panes spec 7): a pane of the window's shown tab other than its own, beside which it goes
 * on the side whose edge the cursor is nearest; the middle half of a tab's entry in the window's strip, which shows that
 * tab at once, with no drop of its own, so that one of its panes can take the pane; the tab that showed there before
 * shows again when the drag ends, unless its drop lands in that window (spec 13); a new-tab place in the strip
 * ({@link TabStrip#newTabPlace}: an entry's outer quarter, the classic tabs' "+" and the room after them), where it
 * becomes a new tab, except in its own window for its tab's only pane, which Move Pane to New Tab leaves where it is
 * too; and the main window's empty workspace, anywhere over it, a new tab in its place. A pane takes targets in windows
 * alone, as a pane never becomes a window; the drag of an item that is pinned or being closed never starts.
 * <p>Releasing the left button carries the target out: a tab along its own strip as a reorder and a pane within its
 * window as a layout change, neither through the tab registry; into another window, or a tab out of every window,
 * through the window manager of the drag's window, which moves the items through the tab registry, a moved pane's item
 * taking the focus where it lands; none touches an item's content or session, and a window no manager opened moves
 * nothing out of itself.
 * Releasing on no target, Esc, another press or a plain move (both mean Swing lost the release), the drag's window losing
 * the focus (a modal dialog, another application), and the dragged item closing or leaving its tab, or, for a tab drag,
 * the item its entry shows giving way to another of its panes end the drag without a drop. EDT only.
 */
final class TabDrag {
    /** Where a release would put the dragged tab or pane. */
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

        /**
         * Beside the pane {@code group} of {@code window}'s shown tab, on {@code side}: a pane drag's (tabs and panes spec
         * 7).
         *
         * @param window the window
         * @param group the pane
         * @param side where the dragged pane goes
         */
        record Beside(WindowContent window, GroupId group, Side side) implements Target { }

        /**
         * A new tab at {@code index} of {@code window}'s strip, or the main window's empty workspace in its place (index
         * 0): a pane drag's (tabs and panes spec 7).
         *
         * @param window the window
         * @param index the new tab's place among the strip's entries
         */
        record NewTab(WindowContent window, int index) implements Target { }

        /**
         * Beside the pane {@code group} of {@code window}'s shown tab, on {@code side}: a tab drag's, every pane of the
         * dragged tab going there as one nested split (pane flexibility spec 5).
         *
         * @param window the window
         * @param group the pane
         * @param side where the dragged tab's panes go
         */
        record Into(WindowContent window, GroupId group, Side side) implements Target { }
    }

    /** How far right of and below the cursor the label's corner sits, so no window is ever under the pointer's tip. */
    static final int LABEL_OFFSET = 8;

    /** The keys a mouse event may hold besides its buttons: a pane's press holds its modifier of them and no other. */
    private static final int KEYS = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
        | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

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
    /** After Esc ended a drag: the rest of that keystroke ({@link #escapeRest}). */
    private final KeyEventDispatcher escapeRest = this::escapeRest;
    private final AWTEventListener lostRelease = this::lostRelease;
    /** The focus manager the Esc dispatcher went to, while a drag runs. */
    private KeyboardFocusManager keys;
    /** The focus manager {@link #escapeRest} went to, until Esc's release or the next other key. */
    private KeyboardFocusManager restKeys;
    /**
     * While a press is pending or a drag runs: the drag's window; the dragged tab, or the tab of a pane drag's item;
     * the item its entry shows, or the item whose pane a pane drag carries; the component the press reached; the press
     * on screen.
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
    /** Whether the pending press or the drag carries an item's pane rather than a whole tab (tabs and panes spec 7). */
    private boolean pane;
    /**
     * While a pane's gesture lasts, from its press to its left button's release: the component the press reached, whose
     * events are the pane drag's, and the press itself, which the gesture's listener does not take for a new one.
     */
    private Component gesture;
    private MouseEvent gesturePress;
    private final AWTEventListener gestureEvents = this::gestureEvent;
    /**
     * The windows a pane drag showed another tab of, each with the tab that showed there before, which shows again as the
     * drag ends unless its drop lands in that window (tabs and panes spec 13).
     */
    private final Map<WindowContent, ParentTabId> hovered = new LinkedHashMap<>();
    /** The text view whose text drag sits out the gesture, while it does (workspace plan 8c Rulings 16). */
    private JTextComponent textDrag;

    /**
     * @param windows the windows a drag can reach, the most recently active first; what leaves its window goes through
     *     the window manager of the drag's window ({@code WindowContent.manager()})
     */
    TabDrag(Supplier<List<WindowContent>> windows) {
        this.windows = Objects.requireNonNull(windows, "windows");
    }

    /** @return whether a drag runs: from the threshold to its release or its end */
    boolean active() { return dragging; }

    /** @return while a drag runs, the item the dragged tab's entry shows or whose pane it carries; else null */
    Tab dragged() { return dragging ? tab : null; }

    /** @return the dragged tab while a drag runs, else null */
    ParentTabId draggedParentTab() { return dragging ? parentTab : null; }

    /** @return whether a running drag carries a pane (tabs and panes spec 7) */
    boolean movingPane() { return dragging && pane; }

    /** @return where a release would put the dragged tab or pane now */
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
     * Whether {@code event} carries the pane drag's modifier (tabs and panes spec 7; workspace plan 8c Rulings 1): the left
     * button with Cmd+Option on macOS, either Option key, and Ctrl+Alt elsewhere, and no other key; Shift, and elsewhere
     * AltGr, which Windows reports with Ctrl and Alt, make it none.
     *
     * @param event a mouse event
     * @param macOs whether the window's keymap is macOS's
     * @return whether a press with it begins a pane's gesture
     */
    static boolean movesPane(MouseEvent event, boolean macOs) {
        int modifier = macOs ? InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK
            : InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        int keys = event.getModifiersEx() & KEYS;
        // macOS reports the right Option key as AltGraph; it has no AltGr of its own, so either Option counts.
        if (macOs && (keys & InputEvent.ALT_GRAPH_DOWN_MASK) != 0)
            keys = (keys & ~InputEvent.ALT_GRAPH_DOWN_MASK) | InputEvent.ALT_DOWN_MASK;
        // The left button's own press or click: another button's, the left one held, is none.
        return event.getButton() == MouseEvent.BUTTON1 && keys == modifier;
    }

    /**
     * A left press with the pane drag's modifier on {@code item}, an item of {@code window}'s shown tab, which the window
     * consumes (tabs and panes spec 7): the press's gesture is the pane drag's from now to its release (workspace plan 8c
     * Rulings 2), and a drag of the item's pane may start from it. A press while another is pending or a drag runs ends
     * that one first.
     */
    void pressedPane(WindowContent window, Tab item, MouseEvent event) {
        if (this.tab != null) cancel();
        Optional<ParentTabId> id = window.parentTabOf(item);
        if (id.isEmpty()) return;
        beginGesture(event);
        from = window;
        parentTab = id.get();
        tab = item;
        pane = true;
        source = event.getComponent();
        pressedAt = screen(event);
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
            // A pinned item, or one being closed, does not move: its pane's drag never starts (workspace plan 8c Rulings 11).
            if (!inPlace() || pane && !from.tabRegistry().movable(tab)) {
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
     * landing go whatever happens. The window the drop is for keeps the tab a pane drag showed there while the drop
     * shows the item there; after a drop that does nothing or fails, the tab that showed before shows again.
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
        WindowContent kept = null;
        ParentTabId before = null;
        try {
            update(screen(event));
            where = target;
            kept = where.map(TabDrag::window).orElse(null);
            before = hovered.get(kept);
        } finally {
            finish(kept);
        }
        if (where.isEmpty()) return;
        try {
            drop(moving, home, parent, where.get());
        } catch (RuntimeException | Error failure) {
            try {
                unhover(kept, before, home, moving);
            } catch (RuntimeException | Error late) {
                // The drop's own failure stays the one that propagates.
                failure.addSuppressed(late);
            }
            throw failure;
        }
        unhover(kept, before, home, moving);
    }

    /**
     * After a drop for {@code window}, whose tab {@code before} showed there before a pane drag showed another (tabs
     * and panes spec 13): unless the drop landed {@code moving} in the window's shown tab, {@code before} shows again,
     * its active item focused in {@code home}, the drag's own window, as {@link #restore} shows it. Nothing for a
     * window the drag showed no other tab of, or one that closed.
     */
    private static void unhover(WindowContent window, ParentTabId before, WindowContent home, Tab moving) {
        if (window == null || before == null || window.closed()) return;
        if (window.parentTabOf(moving).filter(window.activeParentTab()::equals).isPresent()) return;
        if (window == home) window.selectParentTab(before);
        else window.parentTabs().select(before);
    }

    /** Ends a running drag or a pending press without a drop. */
    void cancel() { finish(); }

    /**
     * {@code window} lost the focus or closed: a press or a drag from it ends without a drop, and the rest of an Esc
     * keystroke that ended a drag is claimed no longer, as its release has gone elsewhere.
     */
    void cancelFrom(WindowContent window) {
        endEscape();
        if (window != null && from == window) cancel();
    }

    /**
     * While a drag runs it owns Esc, wherever the focus is: Esc ends it, and the rest of that keystroke reaches no
     * component either ({@link #escapeRest}).
     */
    boolean dispatchKeyEvent(KeyEvent event) {
        if (!dragging || event.getKeyCode() != KeyEvent.VK_ESCAPE) return false;
        if (event.getID() == KeyEvent.KEY_PRESSED) {
            // Taken before the drag ends, so that whatever ending it throws, the rest of the keystroke is still taken.
            endEscape();
            restKeys = keys;
            restKeys.addKeyEventDispatcher(escapeRest);
            cancel();
        }
        return true;
    }

    /**
     * The rest of the Esc keystroke that ended a drag (workspace plan 8c's final review): its typed ESC, which a
     * terminal writes to its program while Ctrl is down, its repeats and its release reach no component; the modifier's
     * own keys pass. Esc's release ends it, or, when that release went elsewhere (the focus moved away), the next other
     * key.
     */
    private boolean escapeRest(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT
            || code == KeyEvent.VK_META || code == KeyEvent.VK_ALT_GRAPH) return false;
        boolean escape = event.getID() == KeyEvent.KEY_TYPED ? event.getKeyChar() == '\u001b'
            : code == KeyEvent.VK_ESCAPE;
        if (!escape || event.getID() == KeyEvent.KEY_RELEASED) endEscape();
        return escape;
    }

    /** The Esc keystroke that ended a drag is over: nothing claims a key any more. */
    private void endEscape() {
        if (restKeys == null) return;
        restKeys.removeKeyEventDispatcher(escapeRest);
        restKeys = null;
    }

    /**
     * A release Swing lost: while the left button is held, Swing sends only drag events, to the pressed component, so a
     * plain move or a press anywhere means the drag's release never came.
     */
    private void lostRelease(AWTEvent event) {
        if (dragging && (event.getID() == MouseEvent.MOUSE_MOVED || event.getID() == MouseEvent.MOUSE_PRESSED)) cancel();
    }

    /** A pane's gesture begins with {@code press}: its later events come to {@link #gestureEvent} until its release. */
    private void beginGesture(MouseEvent press) {
        endGesture();
        gesture = press.getComponent();
        gesturePress = press;
        // A text view with its input methods off (the Editor's Vim Normal and Visual modes) still hears a consumed press,
        // and Swing's text drag never asks: it sits out the gesture, as RTextArea's own triple-click handling does.
        JTextComponent text = gesture instanceof JTextComponent own ? own
            : (JTextComponent) SwingUtilities.getAncestorOfClass(JTextComponent.class, gesture);
        if (text != null && text.getDragEnabled()) {
            text.setDragEnabled(false);
            textDrag = text;
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(gestureEvents, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    /** The pane's gesture is over: a text view's text drag comes back, and its listener goes. */
    private void endGesture() {
        if (gesture == null) return;
        gesture = null;
        gesturePress = null;
        if (textDrag != null) {
            textDrag.setDragEnabled(true);
            textDrag = null;
        }
        Toolkit.getDefaultToolkit().removeAWTEventListener(gestureEvents);
    }

    /** The gesture's release never came: the gesture is over, and so is the press or the drag it began. */
    private void lost() {
        endGesture();
        if (pane) cancel();
    }

    /**
     * An event while a pane's gesture lasts, before any component hears it (workspace plan 8c Rulings 2): the pressed
     * component's events while the left button is held, and that button's release, are consumed, a drag followed and the
     * release ending the gesture and carrying the drag out; a new left press, another press away from it, or a plain move
     * means that release never came: the gesture ends, with the press or the drag it began, and that event is not its.
     */
    private void gestureEvent(AWTEvent event) {
        if (gesture == null || event == gesturePress || !(event instanceof MouseEvent mouse)) return;
        // A new left press means this gesture's release went elsewhere: the gesture is over and the press is not its.
        if (mouse.getID() == MouseEvent.MOUSE_PRESSED && mouse.getButton() == MouseEvent.BUTTON1) {
            lost();
            return;
        }
        boolean release = mouse.getID() == MouseEvent.MOUSE_RELEASED && mouse.getButton() == MouseEvent.BUTTON1;
        if (mouse.getComponent() == gesture && (release || (mouse.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0)) {
            mouse.consume();
            if (mouse.getID() == MouseEvent.MOUSE_DRAGGED) dragged(mouse);
            if (release) {
                endGesture();
                released(mouse);
            }
        } else if (mouse.getID() == MouseEvent.MOUSE_PRESSED || mouse.getID() == MouseEvent.MOUSE_MOVED) lost();
    }

    /**
     * Whether what the press found is still in place: its item open and in the press's window, and for a tab drag still
     * the item the press's tab shows, for a pane drag still in the press's tab.
     */
    private boolean inPlace() {
        if (tab == null || tab.closed() || !Objects.equals(tab.window(), from.id())) return false;
        Optional<WorkspaceView> view = from.parentTabs().view(parentTab);
        if (pane) return view.filter(held -> held.contains(tab)).isPresent();
        return view.map(WorkspaceView::selected).filter(tab::equals).isPresent();
    }

    /** Starts the drag: what ends it is in place before anything shows, so {@link #finish} can always take it away. */
    private void start(Point at) {
        dragging = true;
        // An earlier drag's Esc whose release went elsewhere claims no key of this drag's.
        endEscape();
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
        if (pane) {
            // A pane takes targets in a window alone: it never becomes a window (tabs and panes spec 7).
            if (under.isPresent()) target = onPanes(open.get(under.getAsInt()), at);
            showLanding();
            return;
        }
        // What the registry keeps in its window stays there: a tab holding an item that is pinned or being closed. No
        // other window takes it, and it tears nothing out.
        boolean stays = !movable();
        if (under.isPresent()) {
            WindowContent window = open.get(under.getAsInt());
            // A tab drops on a strip (tabs and panes spec 6), else beside a pane of a shown tab but its own (pane
            // flexibility spec 5), unless it moves along its own strip only.
            if (!stays || window == from) target = onBar(window, at);
            if (target.isEmpty() && !stays) target = intoPanes(window, at);
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
        boolean shows = window.tabBar().component().isVisible();
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
        Component bar = window.tabBar().component();
        if (bar.isVisible()) return bar.getBounds();
        return new Rectangle(0, 0, window.parentTabs().getWidth(), bar.getPreferredSize().height);
    }

    /**
     * A pane's target in {@code window} at {@code at} (tabs and panes spec 7): the main window's empty workspace anywhere
     * over it, a new tab in its place (workspace plan 8c Rulings 10); over the shown strip, a new-tab place
     * ({@link TabStrip#newTabPlace}), a new tab there, but not in the drag's own window for its tab's only pane (Rulings
     * 8), or an entry's middle half, whose tab shows at once and takes no drop itself; over a pane of the shown tab other
     * than the dragged one's own, beside it on the side whose edge is nearest (Rulings 6 and 7). Empty anywhere else (the
     * window's dock, chrome and title bar, a divider, the dragged pane itself) and for an item that may no longer move.
     */
    private Optional<Target> onPanes(WindowContent window, Point at) {
        if (!from.tabRegistry().movable(tab)) return Optional.empty();
        ParentTabsView parents = window.parentTabs();
        Point local = new Point(at);
        SwingUtilities.convertPointFromScreen(local, parents);
        if (!new Rectangle(parents.getSize()).contains(local)) return Optional.empty();
        if (parents.isEmpty()) return Optional.of(new Target.NewTab(window, 0));
        Component strip = window.tabBar().component();
        if (strip.isVisible() && strip.getBounds().contains(local)) {
            Point inStrip = SwingUtilities.convertPoint(parents, local, strip);
            OptionalInt place = window.tabBar().newTabPlace(inStrip);
            if (place.isPresent())
                return window != from || from.mayMoveToNewTab(tab) ? Optional.of(new Target.NewTab(window, place.getAsInt())) : Optional.empty();
            List<Rectangle> entries = window.tabBar().tabBounds();
            List<Tab> listed = parents.bar().tabs();
            // The point is on the strip, so an entry's x range alone counts: the row below the entries is theirs.
            for (int i = 0; i < Math.min(entries.size(), listed.size()); i++) {
                Rectangle entry = entries.get(i);
                if (inStrip.x >= entry.x && inStrip.x < entry.x + entry.width)
                    hover(window, parents.bar().id(listed.get(i)));
            }
            return Optional.empty();
        }
        return WorkspaceDropResolver.resolve(local, paneAreas(parents, tab)).map(drop -> new Target.Beside(window, drop.group(), drop.side()));
    }

    /**
     * A tab's drop on a pane of {@code window}'s shown tab at {@code at} (pane flexibility spec 5; workspace plan 9a Rulings
     * 14): beside it on the side whose edge is nearest, as a pane drag's (workspace plan 8c Rulings 6). Empty over the
     * strip, which {@link #onBar} answers, over the main window's empty workspace, which takes the tab in its place, over
     * the dragged tab's own panes and anywhere else.
     */
    private Optional<Target> intoPanes(WindowContent window, Point at) {
        ParentTabsView parents = window.parentTabs();
        if (parents.isEmpty() || parents.model().active().equals(parentTab)) return Optional.empty();
        Point local = new Point(at);
        SwingUtilities.convertPointFromScreen(local, parents);
        if (!new Rectangle(parents.getSize()).contains(local)) return Optional.empty();
        Component strip = window.tabBar().component();
        if (strip.isVisible() && strip.getBounds().contains(local)) return Optional.empty();
        return WorkspaceDropResolver.resolve(local, paneAreas(parents, null)).map(drop -> new Target.Into(window, drop.group(), drop.side()));
    }

    /** The panes of {@code parents}' shown tab that show, but {@code excluded}'s, each with its bounds in {@code parents}. */
    private static List<WorkspaceDropResolver.Area> paneAreas(ParentTabsView parents, Tab excluded) {
        WorkspaceView workspace = parents.active();
        var panes = new ArrayList<WorkspaceDropResolver.Area>();
        for (GroupView view : workspace.groupViews())
            if (view.tab() != excluded && view.isVisible() && SwingUtilities.isDescendingFrom(view, workspace))
                panes.add(new WorkspaceDropResolver.Area(view.id(),
                    SwingUtilities.convertRectangle(view, new Rectangle(view.getSize()), parents)));
        return panes;
    }

    /**
     * Shows {@code window}'s tab {@code id} for a pane drag (tabs and panes spec 7 and 13; workspace plan 8c Rulings 4),
     * remembering the tab that showed there before the drag first showed another. In the drag's own window its active
     * item takes the focus, as Select Tab gives it; another window's focus stays where it is, as a focus request there
     * would raise that window and take the focus from the drag's own, which ends the drag. Its panes take their places at
     * once, for the next target.
     */
    private void hover(WindowContent window, ParentTabId id) {
        if (id == null || window.activeParentTab().equals(id)) return;
        hovered.putIfAbsent(window, window.activeParentTab());
        if (window == from) window.selectParentTab(id);
        else window.parentTabs().select(id);
        window.parentTabs().validate();
    }

    /**
     * Each window a pane drag showed another tab of shows the tab that showed before (tabs and panes spec 13; workspace
     * plan 8c Rulings 5), except {@code kept}, the window its drop is for, which shows what the drop shows; in
     * {@code home}, the drag's own window, that tab's active item takes the focus again. A tab gone meanwhile stays gone.
     */
    private void restore(WindowContent home, WindowContent kept) {
        var before = new LinkedHashMap<>(hovered);
        hovered.clear();
        for (Map.Entry<WindowContent, ParentTabId> entry : before.entrySet()) {
            WindowContent window = entry.getKey();
            if (window == kept || window.closed()) continue;
            if (window == home) window.selectParentTab(entry.getValue());
            else window.parentTabs().select(entry.getValue());
        }
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

    /** The strip's entries in {@code window}'s parent-tabs view's coordinates, in strip order, without a dragged tab's own. */
    private List<Rectangle> barEntries(WindowContent window) {
        TabStrip bar = window.tabBar();
        List<Tab> listed = window.parentTabs().bar().tabs();
        List<Rectangle> bounds = bar.tabBounds();
        var entries = new ArrayList<Rectangle>();
        for (int i = 0; i < Math.min(listed.size(), bounds.size()); i++) {
            if (!pane && listed.get(i) == tab) continue;
            entries.add(SwingUtilities.convertRectangle(bar.component(), bounds.get(i), window.parentTabs()));
        }
        return entries;
    }

    /**
     * Shows the target's landing on its window's layered pane: for a tab, a bar where it would go in the strip as the
     * placeholder, or the band of a strip that hides as the highlight; for a pane, the half of the pane beside which it
     * would go as the highlight, or a bar at the new-tab place where it would become a tab as the placeholder; for a tab
     * dropped beside a pane, that half of the pane as the highlight too (pane flexibility spec 5); the main
     * window's whole empty workspace for what drops there. Without a target in a window the overlay leaves its window.
     */
    private void showLanding() {
        WindowContent window = target.map(TabDrag::window).orElse(null);
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
        // The main window's empty workspace gives way to what drops there: all of it shows.
        if (parents.isEmpty())
            highlight = SwingUtilities.convertRectangle(parents, new Rectangle(parents.getSize()), layers);
        else switch (target.get()) {
            // The half of the pane beside which the dragged pane goes (tabs and panes spec 7).
            case Target.Beside beside -> highlight = SwingUtilities.convertRectangle(parents,
                besideLanding(parents, beside.group(), beside.side()), layers);
            // The half of the pane beside which the dragged tab's panes go (pane flexibility spec 5).
            case Target.Into into -> highlight = SwingUtilities.convertRectangle(parents,
                besideLanding(parents, into.group(), into.side()), layers);
            // A pane becoming a new tab: a bar at its place, as the tab drag marks a tab's.
            case Target.NewTab newTab -> placeholder = SwingUtilities.convertRectangle(parents,
                WorkspaceDropResolver.marker(barArea(window), barEntries(window), newTab.index(), marker), layers);
            // A strip that hides takes the tab at its end: its band shows (workspace plan 7c Rulings 5).
            case Target.Bar bar when !window.tabBar().component().isVisible() ->
                highlight = SwingUtilities.convertRectangle(parents, barArea(window), layers);
            case Target.Bar bar -> placeholder = SwingUtilities.convertRectangle(parents,
                WorkspaceDropResolver.marker(barArea(window), barEntries(window), bar.index(), marker), layers);
            case Target.TearOut out -> { }
        }
        if (overlay.getParent() == null) layers.add(overlay, JLayeredPane.DRAG_LAYER);
        overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        overlay.show(highlight, placeholder, null, null);
    }

    /** Where what drops beside the pane {@code group} on {@code side} lands, in {@code parents}' coordinates: that half of it. */
    private static Rectangle besideLanding(ParentTabsView parents, GroupId group, Side side) {
        GroupView view = parents.active().groupView(group);
        var area = new WorkspaceDropResolver.Area(group, SwingUtilities.convertRectangle(view, new Rectangle(view.getSize()), parents));
        return WorkspaceDropResolver.landing(new WorkspaceDropResolver.Drop(group, side), List.of(area));
    }

    /** Ends a pending press or a drag: its subscriptions, its listeners, the label and the landing go. */
    private void finish() { finish(null); }

    /**
     * As {@link #finish()}; the windows a pane drag showed another tab of show the tab that showed before, except
     * {@code kept}, the window the drop is for (tabs and panes spec 13).
     */
    private void finish(WindowContent kept) {
        boolean wasDragging = dragging;
        WindowContent home = from;
        dragging = false;
        from = null;
        parentTab = null;
        tab = null;
        source = null;
        pressedAt = null;
        grab = null;
        size = null;
        target = Optional.empty();
        pane = false;
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
        // The tabs a pane drag showed give way once nothing of the drag listens any more.
        restore(home, kept);
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
     * whole ({@code home}, the drag's window, holds {@code parent}). A pane beside another pane of its window, or onto a
     * new-tab place of its strip, moves there as a layout change; beside another window's pane, onto a new-tab place of
     * its strip or onto the main window's empty workspace, the window manager moves its item there (workspace plan 8c
     * Rulings 12). A tab beside a pane of its window merges into that pane's tab as a layout change, and beside another
     * window's pane through the window manager (pane flexibility spec 5; workspace plan 9a Rulings 12 and 13). A window no
     * manager opened moves nothing out of itself.
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
            case Target.Beside beside -> {
                if (beside.window() == home) home.movePane(moving, beside.group(), beside.side());
                // Another window's pane: the window manager moves the item there, through the tab registry.
                else home.manager().ifPresent(manager -> manager.movePane(home, moving, beside.window(), beside.group(), beside.side()));
            }
            case Target.NewTab newTab -> {
                if (newTab.window() == home) home.movePaneToNewTab(moving, newTab.index());
                // Another window's strip, or the main window's empty workspace: through the window manager and the registry.
                else home.manager().ifPresent(manager -> manager.movePaneToNewTab(home, moving, newTab.window(), newTab.index()));
            }
            case Target.Into into -> {
                if (into.window() == home) home.mergeTab(parent, into.group(), into.side());
                // Another window's pane: the window manager moves the tab's items there, through the tab registry.
                else home.manager().ifPresent(manager -> manager.moveTabInto(home, parent, into.window(), into.group(), into.side()));
            }
        }
    }

    /** The window {@code target} drops in; none for a tear-out's new window. */
    private static WindowContent window(Target target) {
        return switch (target) {
            case Target.Bar bar -> bar.window();
            case Target.Beside beside -> beside.window();
            case Target.NewTab newTab -> newTab.window();
            case Target.Into into -> into.window();
            case Target.TearOut out -> null;
        };
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
