package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.Node;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.EnterpriseLook;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tabs and panes spec 7: the pane drag within one window, on synthetic events over a stand-in frame on a pretend screen.
 * The main window holds two tabs of plugin views: the first, showing, "a" beside "b" above "c" (H[a, V[b, c]]), the
 * second "d"; its strip shows, as it holds two tabs. No window manager opened it, so its drag is its own.
 */
class PaneDragTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final TerminalRegistry terminals = new TerminalRegistry();
    private final List<TerminalEvent> facts = new ArrayList<>();
    /** Stands in for the focus manager during each test: it records the Esc dispatchers a drag adds. */
    private CommandPaletteShortcutsTest.RecordingFocusManager keys;
    private KeyboardFocusManager previousKeys;
    private int listeners;
    private WindowContent owner;
    private JPanel frame;
    private Tab a, b, c, d;
    private ParentTabId first, second;

    @BeforeEach void window() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            previousKeys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
            listeners = motionListeners();
            terminals.onEvent(facts::add);
            owner = content(idleLauncher(pending), WindowRole.MAIN, null, true, () -> { });
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.connectContributions(model);
            owner.connectTerminals(terminals, () -> { });
            a = view("a", TabRegistry.Placement.NEW_TAB);
            b = view("b", TabRegistry.Placement.SPLIT_RIGHT);
            c = view("c", TabRegistry.Placement.SPLIT_DOWN);
            d = view("d", TabRegistry.Placement.NEW_TAB);
            first = owner.parentTabOf(a).orElseThrow();
            second = owner.parentTabOf(d).orElseThrow();
            owner.selectParentTab(first);
            frame = frame(owner, 100, 50, 1208, 760);
        });
    }

    /** A gesture or a drag that outlived its test would keep its listeners into the next test of this JVM. */
    @AfterEach void cleanup() throws Exception {
        edt(() -> {
            try {
                owner.tabDrag().cancel();
                assertThat(keys.dispatchers).as("no Esc dispatcher is left behind").isEmpty();
                assertThat(motionListeners()).as("no gesture or lost-release listener is left behind").isEqualTo(listeners);
            } finally {
                try {
                    // A plain move: a gesture a failed test left behind lost its release, so it ends and skews no later test.
                    broadcast(new MouseEvent(owner, MouseEvent.MOUSE_MOVED, 0, 0, 0, 0, 0, false, MouseEvent.NOBUTTON));
                } finally {
                    KeyboardFocusManager.setCurrentKeyboardFocusManager(previousKeys);
                }
            }
        });
        closeOwners();
    }

    /** A plugin's view titled {@code title} in {@code owner}, placed as {@code placement} says. */
    private Tab view(String title, TabRegistry.Placement placement) { return view(title, new JPanel(), placement); }

    private Tab view(String title, JComponent component, TabRegistry.Placement placement) {
        return model.tabs().open("dev.example.notes", title, title, owner.id(), component, placement);
    }

    /** The arrangement of {@code window}'s shown tab: each pane by its item's title, each split as H[...] or V[...]. */
    static String shape(WindowContent window) { return shape(window.workspace(), window.workspace().model().root()); }

    private static String shape(WorkspaceView workspace, Node node) {
        return switch (node) {
            case Group group -> group.selected().flatMap(workspace::tab).map(Tab::title).orElse("-");
            case Split split -> (split.orientation() == Orientation.HORIZONTAL ? "H" : "V")
                + split.children().stream().map(child -> shape(workspace, child.node())).toList();
        };
    }

    private List<TerminalEvent> moves() { return facts.stream().filter(TerminalEvent.TabMoved.class::isInstance).toList(); }

    /** Esc pressed and let go: whether the drag took its press. */
    private boolean esc() {
        boolean taken = keys.send(key(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        keys.send(key(KeyEvent.KEY_RELEASED, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        return taken;
    }

    /** A key event of the window's with {@code held} keys down, not dispatched. */
    private KeyEvent key(int id, int held, int code, char typed) { return new KeyEvent(owner, id, 0, held, code, typed); }

    /** A left press on {@code source} with {@code held} keys, not dispatched. */
    private static MouseEvent press(Component source, int held) {
        return new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0, held | InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false,
            MouseEvent.BUTTON1);
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 2 and 14: a press with the pane drag's modifier inside a pane is
     * the pane drag's, consumed before the item hears it, and makes the pane the active one; within plan 5's threshold
     * nothing runs, past it the drag runs with the item's label and owns Esc.
     */
    @Test void aModifierPressInAPaneIsThePaneDragsAndPastTheThresholdItRuns() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = inPane(owner, b);
            Point press = middle(source);
            assertThat(mouse(source, MouseEvent.MOUSE_PRESSED, press, PANE).isConsumed()).as("the press").isTrue();
            assertThat(owner.selectedTab()).as("its pane is the active one, as any press makes it").isSameAs(b);
            assertThat(motionListeners()).as("the gesture listens").isEqualTo(listeners + 1);
            assertThat(mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x + 3, press.y + 3), PANE).isConsumed()).isTrue();
            assertThat(drag.active()).as("4.2 px is within the threshold").isFalse();
            assertThat(mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x, press.y + 12), PANE).isConsumed()).isTrue();
            assertThat(drag.active()).isTrue();
            assertThat(drag.movingPane()).as("a pane, not a tab").isTrue();
            assertThat(drag.dragged()).isSameAs(b);
            assertThat(drag.label().showing()).isTrue();
            assertThat(keys.dispatchers).as("it owns Esc while it runs").hasSize(1);
            assertThat(motionListeners()).as("the gesture's and the lost release's listeners").isEqualTo(listeners + 2);
            assertThat(mouse(source, MouseEvent.MOUSE_RELEASED, new Point(press.x, press.y + 12), PANE).isConsumed()).isTrue();
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(shape(owner)).as("released over its own pane: nothing moves").isEqualTo("H[a, V[b, c]]");
            assertThat(motionListeners()).as("the release ended the gesture").isEqualTo(listeners);
        });
    }

    /**
     * Workspace plan 8c Rulings 14 and its final review (M-b): with the focus in a tool window, a modifier press on the
     * pane that already is the active one focuses its item, as any press does.
     */
    @Test void aModifierPressOnTheActivePaneFocusesItsItem() throws Exception {
        edt(() -> {
            owner.selectTab(b);
            keys.own(new JPanel());
            int[] focused = {0};
            b.focusTarget(() -> focused[0]++);
            Component source = inPane(owner, b);
            mouse(source, MouseEvent.MOUSE_PRESSED, middle(source), PANE);
            assertThat(focused[0]).as("the press focuses its item").isEqualTo(1);
            mouse(source, MouseEvent.MOUSE_RELEASED, middle(source), PANE);
        });
    }

    /**
     * Workspace plan 8c Rulings 14 and its final review (T2 M4): with the focus outside the workspace, a modifier press on
     * the active pane, a hover on another tab of its window, then Esc: the dragged item has the focus, as the press gave it.
     */
    @Test void afterAHoverInItsOwnWindowEscLeavesTheDraggedItemFocused() throws Exception {
        edt(() -> {
            owner.selectTab(b);
            keys.own(new JPanel());
            int[] focused = {0};
            b.focusTarget(() -> focused[0]++);
            Component source = inPane(owner, b);
            Point press = middle(source), entry = onEntry(owner, d, 0.5);
            mouse(source, MouseEvent.MOUSE_PRESSED, press, PANE);
            assertThat(focused[0]).as("the press").isEqualTo(1);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x, press.y + 12), PANE);
            mouse(source, MouseEvent.MOUSE_DRAGGED, entry, PANE);
            assertThat(owner.activeParentTab()).as("the hovered tab").isEqualTo(second);
            focused[0] = 0;
            assertThat(esc()).isTrue();
            assertThat(owner.activeParentTab()).isEqualTo(first);
            assertThat(owner.selectedTab()).isSameAs(b);
            assertThat(focused[0]).as("the dragged item has the focus again").isPositive();
            mouse(source, MouseEvent.MOUSE_RELEASED, entry, PANE);
        });
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 1: without exactly the modifier a press and its drag are the
     * item's own, Shift added or another button too; a click with the modifier is the pane drag's.
     */
    @Test void withoutExactlyTheModifierAPressIsTheItemsOwn() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = inPane(owner, b);
            Point press = middle(source), below = new Point(press.x, press.y + 12);
            for (int held : new int[] {0, PANE | InputEvent.SHIFT_DOWN_MASK}) {
                assertThat(mouse(source, MouseEvent.MOUSE_PRESSED, press, held).isConsumed()).as("keys %d", held).isFalse();
                assertThat(mouse(source, MouseEvent.MOUSE_DRAGGED, below, held).isConsumed()).isFalse();
                assertThat(drag.active()).isFalse();
                assertThat(mouse(source, MouseEvent.MOUSE_RELEASED, below, held).isConsumed()).isFalse();
            }
            var right = new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0, PANE | InputEvent.BUTTON3_DOWN_MASK, 5, 5, 1, false,
                MouseEvent.BUTTON3);
            source.dispatchEvent(right);
            assertThat(right.isConsumed()).as("another button").isFalse();
            assertThat(mouse(source, MouseEvent.MOUSE_CLICKED, press, PANE).isConsumed()).as("a click with the modifier").isTrue();
            assertThat(motionListeners()).as("no gesture began").isEqualTo(listeners);
        });
    }

    /**
     * Workspace plan 8c Rulings 1: Cmd+Option on macOS (either Option key), Ctrl+Alt elsewhere, with the left button and
     * no other key.
     */
    @Test void theModifierIsCmdOptionOnMacOsAndCtrlAltElsewhere() {
        int mac = InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK, other = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        var panel = new JPanel();
        assertThat(TabDrag.movesPane(press(panel, mac), true)).isTrue();
        assertThat(TabDrag.movesPane(press(panel, other), false)).isTrue();
        assertThat(TabDrag.movesPane(press(panel, other), true)).as("Ctrl+Option on macOS").isFalse();
        assertThat(TabDrag.movesPane(press(panel, mac), false)).as("Meta+Alt elsewhere").isFalse();
        assertThat(TabDrag.movesPane(press(panel, mac | InputEvent.SHIFT_DOWN_MASK), true)).as("Shift too").isFalse();
        assertThat(TabDrag.movesPane(press(panel, other | InputEvent.ALT_GRAPH_DOWN_MASK), false)).as("AltGr").isFalse();
        assertThat(TabDrag.movesPane(press(panel, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK), false))
            .as("Ctrl and AltGr elsewhere").isFalse();
        assertThat(TabDrag.movesPane(press(panel, InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK), true))
            .as("Cmd and the right Option key on macOS (Rulings 1)").isTrue();
        assertThat(TabDrag.movesPane(press(panel, InputEvent.ALT_DOWN_MASK), false)).as("Alt alone").isFalse();
        assertThat(TabDrag.movesPane(press(panel, mac | InputEvent.CTRL_DOWN_MASK), true)).as("Cmd+Option+Ctrl").isFalse();
        assertThat(TabDrag.movesPane(press(panel, other | InputEvent.META_DOWN_MASK), false)).as("Ctrl+Alt+Meta").isFalse();
        var right = new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, other | InputEvent.BUTTON3_DOWN_MASK, 1, 1, 1, false,
            MouseEvent.BUTTON3);
        assertThat(TabDrag.movesPane(right, false)).as("another button").isFalse();
        var second = new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, other | InputEvent.BUTTON1_DOWN_MASK
            | InputEvent.BUTTON3_DOWN_MASK, 1, 1, 1, false, MouseEvent.BUTTON3);
        assertThat(TabDrag.movesPane(second, false)).as("another button's press, the left one held").isFalse();
    }

    /**
     * Workspace plan 8c's final review (M-a): a second button's press while a pane drag runs, the left one still held,
     * over the drag's own pane, ends the drag, as a lost release does, and starts no other; the gesture's rest moves
     * nothing.
     */
    @Test void aSecondButtonsPressMidDragEndsItAndStartsNoOther() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Point low = inside(pane(owner, a.group().orElseThrow()), 0.5, 0.8);
            Component source = startPane(owner, a, low);
            assertThat(drag.active()).isTrue();
            Point at = new Point(low);
            SwingUtilities.convertPointFromScreen(at, source);
            source.dispatchEvent(new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0, PANE | InputEvent.BUTTON1_DOWN_MASK
                | InputEvent.BUTTON3_DOWN_MASK, at.x, at.y, 1, false, MouseEvent.BUTTON3));
            assertThat(drag.active()).as("the press ends the drag").isFalse();
            Point over = inside(pane(owner, c.group().orElseThrow()), 0.5, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, over, PANE);
            assertThat(drag.active()).as("and starts none").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, over, PANE);
            assertThat(shape(owner)).isEqualTo("H[a, V[b, c]]");
        });
    }

    /**
     * Workspace plan 8c Rulings 1 and Review Focus 1: the window's keymap, not the host, picks the modifier its
     * {@code paneClicks} hands the drag: this host's other platform's in a window with that platform's keymap.
     */
    @Test void theWindowsKeymapPicksTheModifier() throws Exception {
        edt(() -> {
            boolean hostMac = (PANE & InputEvent.META_DOWN_MASK) != 0;
            int foreign = hostMac ? InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
                : InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
            WindowContent other = content(idleLauncher(pending), !hostMac);
            other.connectContributions(model);
            Tab notes = model.tabs().open("dev.example.notes", "notes", "notes", other.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            frame(other, 100, 900, 800, 500);
            Component source = inPane(other, notes);
            Point press = middle(source);
            assertThat(mouse(source, MouseEvent.MOUSE_PRESSED, press, PANE).isConsumed()).as("this host's modifier").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, press, PANE);
            assertThat(motionListeners()).as("no gesture began").isEqualTo(listeners);
            assertThat(mouse(source, MouseEvent.MOUSE_PRESSED, press, foreign).isConsumed()).as("its keymap's").isTrue();
            assertThat(motionListeners()).as("the gesture listens").isEqualTo(listeners + 1);
            assertThat(mouse(source, MouseEvent.MOUSE_RELEASED, press, foreign).isConsumed()).isTrue();
            assertThat(motionListeners()).isEqualTo(listeners);
        });
    }

    /**
     * Workspace plan 8c Rulings 11 and its final review (T1 M5): an item whose close waits for its guard never starts a
     * pane drag; its gesture is still the drag's.
     */
    @Test void anItemBeingClosedNeverStartsAPaneDrag() throws Exception {
        edt(() -> {
            var answer = new CompletableFuture<Boolean>();
            model.tabs().guard("dev.example.notes", (tabs, batch) -> answer);
            var closing = a.requestClose();
            try {
                assertThat(closing).as("the guard has not answered").isNotDone();
                Point over = inside(pane(owner, c.group().orElseThrow()), 0.5, 0.5);
                Component source = startPane(owner, a, over);
                assertThat(owner.tabDrag().active()).isFalse();
                assertThat(owner.tabDrag().label().showing()).isFalse();
                assertThat(mouse(source, MouseEvent.MOUSE_RELEASED, over, PANE).isConsumed()).isTrue();
                assertThat(shape(owner)).isEqualTo("H[a, V[b, c]]");
            } finally {
                answer.complete(false);
            }
            assertThat(closing).isCompletedWithValue(false);
        });
    }

    /**
     * Workspace plan 8c's final review (I1): Esc pressed with the modifier still held ends the drag, and the rest of that
     * keystroke reaches no component: neither its typed ESC, which a terminal writes to its program while Ctrl is down,
     * nor a repeat nor its release; the modifier's own keys pass. After the release Esc is the focused component's again.
     */
    @Test void escWithTheModifierHeldEndsTheDragAndTakesItsWholeKeystroke() throws Exception {
        edt(() -> {
            int held = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
            Point low = inside(pane(owner, a.group().orElseThrow()), 0.5, 0.8);
            Component source = startPane(owner, a, low);
            assertThat(keys.send(key(KeyEvent.KEY_PRESSED, held, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).as("Esc").isTrue();
            assertThat(owner.tabDrag().active()).isFalse();
            assertThat(keys.send(key(KeyEvent.KEY_TYPED, held, KeyEvent.VK_UNDEFINED, '\u001b'))).as("its typed ESC").isTrue();
            assertThat(keys.send(key(KeyEvent.KEY_PRESSED, held, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).as("a repeat").isTrue();
            assertThat(keys.send(key(KeyEvent.KEY_TYPED, held, KeyEvent.VK_UNDEFINED, '\u001b'))).isTrue();
            assertThat(keys.send(key(KeyEvent.KEY_RELEASED, InputEvent.ALT_DOWN_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED)))
                .as("Ctrl let go first").isFalse();
            assertThat(keys.send(key(KeyEvent.KEY_RELEASED, InputEvent.ALT_DOWN_MASK, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)))
                .as("its release").isTrue();
            assertThat(keys.dispatchers).as("nothing claims Esc after its release").isEmpty();
            assertThat(keys.send(key(KeyEvent.KEY_TYPED, held, KeyEvent.VK_UNDEFINED, '\u001b'))).as("the next typed ESC").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, low, PANE);
        });
    }

    /**
     * I1: an Esc whose release went elsewhere (the focus moved away) claims nothing past the next other key, and a drag
     * that starts meanwhile still ends on Esc.
     */
    @Test void anEscWhoseReleaseWentElsewhereClaimsNoOtherKey() throws Exception {
        edt(() -> {
            Point low = inside(pane(owner, a.group().orElseThrow()), 0.5, 0.8);
            Component source = startPane(owner, a, low);
            assertThat(keys.send(key(KeyEvent.KEY_PRESSED, PANE, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).isTrue();
            mouse(source, MouseEvent.MOUSE_RELEASED, low, PANE);
            Component again = startPane(owner, a, low);
            assertThat(owner.tabDrag().active()).isTrue();
            assertThat(keys.send(key(KeyEvent.KEY_PRESSED, PANE, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).as("Esc").isTrue();
            assertThat(owner.tabDrag().active()).as("ends the new drag").isFalse();
            mouse(again, MouseEvent.MOUSE_RELEASED, low, PANE);
            assertThat(keys.send(key(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_A, KeyEvent.CHAR_UNDEFINED))).as("another key").isFalse();
            assertThat(keys.dispatchers).as("nothing claims a key after it").isEmpty();
        });
    }

    /** Workspace plan 8c's final review: a window losing the focus after Esc ended its drag leaves a later Esc alone. */
    @Test void aWindowLosingTheFocusAfterEscClaimsNoLaterEsc() throws Exception {
        edt(() -> {
            Point low = inside(pane(owner, a.group().orElseThrow()), 0.5, 0.8);
            Component source = startPane(owner, a, low);
            assertThat(keys.send(key(KeyEvent.KEY_PRESSED, PANE, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).isTrue();
            mouse(source, MouseEvent.MOUSE_RELEASED, low, PANE);
            owner.setActive(false);
            assertThat(keys.dispatchers).as("its release went elsewhere").isEmpty();
            assertThat(keys.send(key(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).as("a later Esc").isFalse();
            owner.setActive(true);
        });
    }

    /**
     * Workspace plan 8c Rulings 2 and Review Focus 1: every event of a pane's gesture, to its release, is the pane drag's,
     * whatever ends the drag: here Esc, after which the rest of the gesture still reaches no listener of the item unconsumed.
     */
    @Test void everyEventOfAGestureIsThePaneDragsWhateverEndsTheDrag() throws Exception {
        edt(() -> {
            var heard = new ArrayList<Boolean>();
            var listener = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) { heard.add(event.isConsumed()); }
                @Override public void mouseReleased(MouseEvent event) { heard.add(event.isConsumed()); }
                @Override public void mouseDragged(MouseEvent event) { heard.add(event.isConsumed()); }
            };
            Component source = inPane(owner, a);
            source.addMouseListener(listener);
            source.addMouseMotionListener(listener);
            TabDrag drag = owner.tabDrag();
            Point low = inside(pane(owner, a.group().orElseThrow()), 0.5, 0.8);
            startPane(owner, a, low);
            assertThat(drag.active()).isTrue();
            assertThat(esc()).as("Esc").isTrue();
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(keys.dispatchers).isEmpty();
            Point over = inside(pane(owner, c.group().orElseThrow()), 0.5, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, over, 0);
            mouse(source, MouseEvent.MOUSE_RELEASED, over, 0);
            assertThat(heard).as("the press, two drags, a drag after Esc and the release: each consumed first")
                .hasSize(5).containsOnly(true);
            assertThat(motionListeners()).as("the release ended the gesture").isEqualTo(listeners);
            assertThat(shape(owner)).isEqualTo("H[a, V[b, c]]");
            assertThat(mouse(source, MouseEvent.MOUSE_PRESSED, middle(source), 0).isConsumed()).as("the next plain press").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, middle(source), 0);
        });
    }

    /** Tabs and panes spec 7: a lost release and the window losing the focus end the pane drag, as they end a tab drag. */
    @Test void aLostReleaseAndTheWindowLosingTheFocusEndThePaneDrag() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Point over = inside(pane(owner, c.group().orElseThrow()), 0.5, 0.5);
            startPane(owner, a, over);
            broadcast(new MouseEvent(owner.workspace(), MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false, MouseEvent.NOBUTTON));
            assertThat(drag.active()).as("a plain move: the release never came").isFalse();
            assertThat(motionListeners()).as("and the gesture is over").isEqualTo(listeners);
            Component source = startPane(owner, a, over);
            owner.setActive(false);
            assertThat(drag.active()).as("a modal dialog or another application took the focus").isFalse();
            assertThat(mouse(source, MouseEvent.MOUSE_RELEASED, over, PANE).isConsumed()).as("its release is still the drag's").isTrue();
            assertThat(shape(owner)).isEqualTo("H[a, V[b, c]]");
        });
    }

    /** Tabs and panes spec 7: the pane drag ends when its item closes, or leaves its tab meanwhile. */
    @Test void thePaneDragEndsWhenItsItemClosesOrLeavesItsTab() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Point over = inside(pane(owner, a.group().orElseThrow()), 0.5, 0.8);
            Component source = startPane(owner, c, over);
            model.tabs().remove(c);
            assertThat(drag.active()).as("its item closed").isFalse();
            relayout(frame);
            mouse(source, MouseEvent.MOUSE_RELEASED, over, PANE);
            source = startPane(owner, b, over);
            owner.moveToNewTab(b);
            mouse(source, MouseEvent.MOUSE_DRAGGED, over, PANE);
            assertThat(drag.active()).as("its item left its tab").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, over, PANE);
            assertThat(owner.parentTabCount()).as("Move Pane to New Tab moved it, the drag nothing").isEqualTo(3);
        });
    }

    /** Tabs and panes spec 7 and workspace plan 8c Rulings 11: a pinned item's pane drag never starts; its gesture is still the drag's. */
    @Test void aPinnedItemsPaneDragNeverStarts() throws Exception {
        edt(() -> {
            var pin = a.pin();
            try {
                Point over = inside(pane(owner, c.group().orElseThrow()), 0.5, 0.5);
                Component source = startPane(owner, a, over);
                assertThat(owner.tabDrag().active()).isFalse();
                assertThat(owner.tabDrag().label().showing()).isFalse();
                assertThat(mouse(source, MouseEvent.MOUSE_RELEASED, over, PANE).isConsumed()).isTrue();
                assertThat(shape(owner)).isEqualTo("H[a, V[b, c]]");
            } finally {
                pin.close();
            }
        });
    }

    /** Review Focus 2: a text view's selection and caret stay as they were through a pane gesture over its selection. */
    @Test void aTextViewsSelectionStaysThroughAPaneGesture() throws Exception {
        edt(() -> {
            var text = new JTextArea("one two three");
            Tab notes = view("notes", text, TabRegistry.Placement.SPLIT_RIGHT);
            relayout(frame);
            text.select(4, 7);
            assertThat(inPane(owner, notes)).isSameAs(text);
            Point over = middle(text), past = new Point(over.x + 40, over.y);
            Component source = startPane(owner, notes, past);
            mouse(source, MouseEvent.MOUSE_RELEASED, past, PANE);
            assertThat(text.getSelectionStart()).isEqualTo(4);
            assertThat(text.getSelectionEnd()).isEqualTo(7);
        });
    }

    /** A text view that drags its text, as the Editor's does outside headless: the drag is counted here, not refused. */
    private static final class DraggingText extends JTextArea {
        boolean drag = true;
        DraggingText(String text) { super(text); }
        @Override public boolean getDragEnabled() { return drag; }
        @Override public void setDragEnabled(boolean value) { drag = value; }
    }

    /** Review Focus 2 and workspace plan 8c Rulings 16: a text view's text drag sits out a pane gesture and comes back after its release. */
    @Test void aTextViewsTextDragSitsOutAPaneGesture() throws Exception {
        edt(() -> {
            var text = new DraggingText("one two three");
            Tab notes = view("notes", text, TabRegistry.Placement.SPLIT_RIGHT);
            relayout(frame);
            Point past = new Point(middle(text).x + 40, middle(text).y);
            Component source = startPane(owner, notes, past);
            assertThat(text.getDragEnabled()).as("during the gesture").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, past, PANE);
            assertThat(text.getDragEnabled()).as("after its release").isTrue();
        });
    }

    /** Workspace plan 8c Rulings 2 and 16: after Esc the gesture lasts to its release, and so does the text drag's rest. */
    @Test void aTextViewsTextDragComesBackAtTheReleaseAfterEsc() throws Exception {
        edt(() -> {
            var text = new DraggingText("one two three");
            Tab notes = view("notes", text, TabRegistry.Placement.SPLIT_RIGHT);
            relayout(frame);
            Point past = new Point(middle(text).x + 40, middle(text).y);
            Component source = startPane(owner, notes, past);
            assertThat(owner.tabDrag().active()).isTrue();
            assertThat(esc()).isTrue();
            assertThat(owner.tabDrag().active()).isFalse();
            assertThat(text.getDragEnabled()).as("Esc ends the drag, not the gesture").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, past, PANE);
            assertThat(text.getDragEnabled()).as("the release ends the gesture").isTrue();
        });
    }

    /**
     * Workspace plan 8c Rulings 2: a gesture whose release went elsewhere ends at the next left press, which is the item's
     * own, and so does the press it began: a plain drag after it drags no pane.
     */
    @Test void aLeftPressAfterAGestureWhoseReleaseWasLostIsTheItemsOwn() throws Exception {
        edt(() -> {
            Component source = inPane(owner, a);
            Point press = middle(source), below = new Point(press.x, press.y + 12);
            mouse(source, MouseEvent.MOUSE_PRESSED, press, PANE);
            assertThat(motionListeners()).as("the gesture listens").isEqualTo(listeners + 1);
            // No release came. The next left press, a plain one on the same view, is not the gesture's.
            assertThat(mouse(source, MouseEvent.MOUSE_PRESSED, press, 0).isConsumed()).isFalse();
            assertThat(motionListeners()).as("the gesture is over").isEqualTo(listeners);
            assertThat(mouse(source, MouseEvent.MOUSE_DRAGGED, below, 0).isConsumed()).isFalse();
            assertThat(owner.tabDrag().active()).as("its press ended with it").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, below, 0);
        });
    }

    /** Review Focus 1: a terminal's view is a pane like any other: its gesture is the pane drag's and selects nothing. */
    @Test void aTerminalsGestureStartsThePaneDragAndNoSelection() throws Exception {
        WindowContent[] shell = new WindowContent[1];
        JPanel[] shown = new JPanel[1];
        edt(() -> {
            shell[0] = content(idleLauncher(pending));
            shown[0] = frame(shell[0], 100, 900, 800, 500);
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { });
        edt(() -> {
            relayout(shown[0]);
            Tab terminal = shell[0].selectedTab();
            var view = ((TerminalContent) terminal.content()).pane().view();
            assertThat(inPane(shell[0], terminal)).as("its view takes the press").isSameAs(view);
            Point from = middle(view), to = new Point(from.x + 80, from.y + 40);
            Component source = startPane(shell[0], terminal, to);
            assertThat(shell[0].tabDrag().movingPane()).isTrue();
            assertThat(mouse(source, MouseEvent.MOUSE_RELEASED, to, PANE).isConsumed()).isTrue();
            assertThat(view.hasSelection()).as("no selection").isFalse();
        });
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 6 and 12: a pane dropped on another pane of its tab goes beside
     * it on the side whose edge the cursor is nearest; the landing shows that half; nothing reaches the tab registry.
     */
    @Test void aPaneDropsBesideAnotherPaneOfItsTabOnTheNearestSide() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            GroupView under = owner.workspace().groupView(c.group().orElseThrow());
            Point low = inside(pane(owner, under.id()), 0.5, 0.9);
            facts.clear();
            Component source = startPane(owner, a, low);
            assertThat(drag.target()).contains(new TabDrag.Target.Beside(owner, under.id(), Side.DOWN));
            var layers = SwingUtilities.getRootPane(owner).getLayeredPane();
            assertThat(drag.overlay().getParent()).as("the landing shows in its window").isSameAs(layers);
            assertThat(drag.overlay().highlight()).as("c's lower half").isEqualTo(SwingUtilities.convertRectangle(under,
                new Rectangle(0, under.getHeight() - under.getHeight() / 2, under.getWidth(), under.getHeight() / 2), layers));
            mouse(source, MouseEvent.MOUSE_RELEASED, low, PANE);
            assertThat(shape(owner)).isEqualTo("V[b, c, a]");
            assertThat(owner.selectedTab()).as("the moved pane is the active one").isSameAs(a);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(moves()).as("within its window: no TAB_MOVED").isEmpty();
            assertThat(drag.overlay().getParent()).isNull();
        });
    }

    /** Tabs and panes spec 7 and workspace plan 8c Rulings 7: over its own pane, a divider or the title bar a pane takes no drop. */
    @Test void aPaneOverItsOwnPaneADividerOrTheTitleBarTakesNoDrop() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = startPane(owner, b, inside(pane(owner, b.group().orElseThrow()), 0.9, 0.9));
            assertThat(drag.active()).isTrue();
            assertThat(drag.target()).as("its own pane, near an edge").isEmpty();
            assertThat(drag.overlay().getParent()).isNull();
            mouse(source, MouseEvent.MOUSE_DRAGGED, middle(owner.workspace().split(List.of()).divider(0)), PANE);
            assertThat(drag.target()).as("the divider between a and the column").isEmpty();
            mouse(source, MouseEvent.MOUSE_DRAGGED, onTitleBar(frame), PANE);
            assertThat(drag.target()).as("the title bar").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, onTitleBar(frame), PANE);
            assertThat(shape(owner)).isEqualTo("H[a, V[b, c]]");
        });
    }

    /**
     * Tabs and panes spec 7 and 13: hovering another tab's entry shows that tab at once, with no drop of its own, and a
     * pane of it then takes the pane, which leaves its tab and shows in its new one.
     */
    @Test void hoveringATabsEntryShowsItAndAPaneOfItTakesTheDrop() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = startPane(owner, c, onEntry(owner, d, 0.5));
            assertThat(owner.activeParentTab()).as("the hovered tab shows at once").isEqualTo(second);
            assertThat(drag.target()).as("an entry takes no drop").isEmpty();
            Point right = inside(pane(owner, d.group().orElseThrow()), 0.9, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, right, PANE);
            assertThat(drag.target()).contains(new TabDrag.Target.Beside(owner, d.group().orElseThrow(), Side.RIGHT));
            facts.clear();
            mouse(source, MouseEvent.MOUSE_RELEASED, right, PANE);
            assertThat(owner.activeParentTab()).as("the tab it landed in shows").isEqualTo(second);
            assertThat(shape(owner)).isEqualTo("H[d, c]");
            assertThat(owner.selectedTab()).isSameAs(c);
            assertThat(moves()).isEmpty();
            assertThat(announced()).as("announced once, where it lands")
                .containsExactly(new TerminalEvent.TabSelected(owner.id(), c.id().value()));
            owner.selectParentTab(first);
            assertThat(shape(owner)).as("its old tab keeps its other panes").isEqualTo("H[a, b]");
        });
    }

    /**
     * Tabs and panes spec 13 and Review Focus 3: a drag that showed another tab and ends elsewhere shows the tab that
     * showed before again, its item focused: after Esc, and after a release over nothing.
     */
    @Test void aDragEndingElsewhereShowsTheTabThatShowedBefore() throws Exception {
        edt(() -> {
            int[] focused = {0};
            b.focusTarget(() -> focused[0]++);
            Point entry = onEntry(owner, d, 0.5);
            Component source = startPane(owner, b, entry);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            focused[0] = 0;
            assertThat(esc()).isTrue();
            assertThat(owner.activeParentTab()).as("after Esc").isEqualTo(first);
            assertThat(owner.selectedTab()).isSameAs(b);
            assertThat(focused[0]).as("its item takes the focus again").isPositive();
            mouse(source, MouseEvent.MOUSE_RELEASED, entry, PANE);
            source = startPane(owner, b, entry, onTitleBar(frame));
            assertThat(owner.activeParentTab()).as("still the hovered tab while the drag runs").isEqualTo(second);
            mouse(source, MouseEvent.MOUSE_RELEASED, onTitleBar(frame), PANE);
            assertThat(owner.activeParentTab()).as("after a release over the title bar").isEqualTo(first);
            assertThat(shape(owner)).isEqualTo("H[a, V[b, c]]");
        });
    }

    /** Tabs and panes spec 7: a tab's last pane moved into another tab of its window takes its tab with it. */
    @Test void aTabsLastPaneMovedIntoAnotherTabClosesItsTab() throws Exception {
        edt(() -> {
            owner.selectParentTab(second);
            Tab shownThere = owner.parentTabs().view(first).orElseThrow().selected();
            Component source = startPane(owner, d, onEntry(owner, shownThere, 0.5));
            assertThat(owner.activeParentTab()).isEqualTo(first);
            Point left = inside(pane(owner, a.group().orElseThrow()), 0.1, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, left, PANE);
            mouse(source, MouseEvent.MOUSE_RELEASED, left, PANE);
            assertThat(owner.parentTabCount()).as("its tab went with it").isEqualTo(1);
            assertThat(shape(owner)).isEqualTo("H[d, a, V[b, c]]");
            assertThat(owner.tabBar().component().isVisible()).as("one tab: the strip hides").isFalse();
        });
    }

    /**
     * Workspace plan 8c Rulings 17's cases: a terminal opens beside "c" and is selected, so the registry, which takes this
     * window as its active one, has an active pane to report; "a", "b" and "c", the plugin views a move of the terminal
     * leaves behind, record into {@code heard} every activation change they hear from then on.
     *
     * @return the terminal's item
     */
    private Tab shellBesideC(List<Boolean> heard) {
        owner.selectTab(c);
        TerminalContent terminal = owner.split(Side.RIGHT);
        Tab shell = owner.selectedTab();
        assertThat(shell.content()).isSameAs(terminal);
        terminals.windowActivated(owner.id());
        for (Tab left : List.of(a, b, c)) left.onActive(heard::add);
        facts.clear();
        return shell;
    }

    /** What the registry heard of selections, moves, closes and the active pane since {@link #facts} was last cleared. */
    private List<TerminalEvent> announced() {
        return facts.stream().filter(fact -> fact instanceof TerminalEvent.TabSelected || fact instanceof TerminalEvent.TabMoved
            || fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.ActivePaneChanged).toList();
    }

    /**
     * Workspace plan 8c Rulings 17: the selected item's pane moved beside a pane of another tab of its window is announced
     * once, where it lands. It stays the selected item, so the registry hears no selection, move, close or active pane,
     * and no item of the tab it left turns active on the way. This pins {@code movePane} itself, called while the item's
     * own tab shows: a drag shows the other tab first (Rulings 4), so its drop announces the moved item once, which
     * {@code hoveringATabsEntryShowsItAndAPaneOfItTakesTheDrop} pins.
     */
    @Test void aPaneMovedIntoAnotherTabIsAnnouncedOnceWhereItLands() throws Exception {
        edt(() -> {
            var heard = new ArrayList<Boolean>();
            Tab shell = shellBesideC(heard);
            owner.movePane(shell, d.group().orElseThrow(), Side.RIGHT);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            assertThat(owner.workspace().tabs()).containsExactly(d, shell);
            assertThat(owner.selectedTab()).isSameAs(shell);
            assertThat(announced()).as("nothing changed for the registry").isEmpty();
            assertThat(heard).as("no item it left turns active").isEmpty();
        });
    }

    /**
     * Workspace plan 8c Rulings 17: Move Pane to New Tab, plan 8b's command, is announced once too, where the pane lands,
     * though the command announced its old tab's next pane before this plan.
     */
    @Test void movePaneToNewTabIsAnnouncedOnceWhereThePaneLands() throws Exception {
        edt(() -> {
            var heard = new ArrayList<Boolean>();
            Tab shell = shellBesideC(heard);
            owner.updateActions();
            owner.invoke(ActionId.MOVE_PANE_TO_NEW_TAB);
            assertThat(owner.parentTabCount()).isEqualTo(3);
            assertThat(owner.workspace().tabs()).as("its own tab, shown").containsExactly(shell);
            assertThat(announced()).as("nothing changed for the registry").isEmpty();
            assertThat(heard).as("no item it left turns active").isEmpty();
        });
    }

    /** A plugin's view whose next showing throws once armed, as a view that fails to take its new pane does. */
    static final class Refusing extends JPanel {
        boolean armed;

        @Override public void setVisible(boolean visible) {
            if (visible && armed) {
                armed = false;
                throw new IllegalStateException("refused");
            }
            super.setVisible(visible);
        }
    }

    /**
     * Workspace plan 8c Rulings 17: a pane that fails to land goes back where it was, beside another tab's pane and into a
     * new tab alike: the window's tabs and layouts as they were, the item in its pane, nothing closed, and the failure
     * propagates.
     */
    @Test void aPaneThatFailsToLandGoesBackWhereItWas() throws Exception {
        edt(() -> {
            var refusing = new Refusing();
            Tab notes = view("notes", refusing, TabRegistry.Placement.SPLIT_RIGHT);
            var before = owner.windowLayout();
            var pane = notes.group().orElseThrow();
            refusing.armed = true;
            assertThatThrownBy(() -> owner.movePane(notes, d.group().orElseThrow(), Side.RIGHT)).hasMessage("refused");
            assertThat(owner.windowLayout()).as("beside another tab's pane").isEqualTo(before);
            assertThat(refusing.getParent()).as("back in its pane").isSameAs(owner.workspace().groupView(pane));
            refusing.armed = true;
            assertThatThrownBy(() -> owner.moveToNewTab(notes)).hasMessage("refused");
            assertThat(owner.windowLayout()).as("into a new tab").isEqualTo(before);
            assertThat(refusing.getParent()).isSameAs(owner.workspace().groupView(pane));
            assertThat(notes.closed()).as("nothing closes").isFalse();
        });
    }

    /**
     * Workspace plan 8c Rulings 17: a tab's last pane that fails to land beside another tab's pane goes back, its tab
     * standing as it stood; once it lands, its tab goes with it (spec 4).
     */
    @Test void aTabsLastPaneThatFailsToLandKeepsItsTab() throws Exception {
        edt(() -> {
            var refusing = new Refusing();
            Tab notes = view("notes", refusing, TabRegistry.Placement.NEW_TAB);
            owner.selectParentTab(first);
            var before = owner.windowLayout();
            var pane = notes.group().orElseThrow();
            ParentTabId own = owner.parentTabOf(notes).orElseThrow();
            refusing.armed = true;
            assertThatThrownBy(() -> owner.movePane(notes, a.group().orElseThrow(), Side.LEFT)).hasMessage("refused");
            assertThat(owner.windowLayout()).isEqualTo(before);
            assertThat(refusing.getParent()).as("back in its pane")
                .isSameAs(owner.parentTabs().view(own).orElseThrow().groupView(pane));
            assertThat(notes.closed()).as("nothing closes").isFalse();
            owner.movePane(notes, a.group().orElseThrow(), Side.LEFT);
            assertThat(owner.parentTabCount()).as("its tab went with it").isEqualTo(2);
            assertThat(shape(owner)).isEqualTo("H[notes, a, V[b, c]]");
        });
    }

    /**
     * Workspace plan 8c Rulings 17: a new tab that fails once it stands in the strip, where a displayable window first
     * gives its item's view a peer, goes again, and the pane goes back where it was.
     */
    @Test void aNewTabThatFailsOnceItStandsGoesAgain() throws Exception {
        edt(() -> {
            boolean[] armed = {false};
            owner.parentTabs().addContainerListener(new java.awt.event.ContainerAdapter() {
                @Override public void componentAdded(java.awt.event.ContainerEvent event) {
                    if (!armed[0]) return;
                    armed[0] = false;
                    throw new IllegalStateException("refused");
                }
            });
            var before = owner.windowLayout();
            var pane = c.group().orElseThrow();
            armed[0] = true;
            assertThatThrownBy(() -> owner.moveToNewTab(c)).hasMessage("refused");
            assertThat(owner.windowLayout()).isEqualTo(before);
            assertThat(owner.parentTabs().views()).as("no tab stays behind").hasSize(2);
            assertThat(c.component().getParent()).as("back in its pane").isSameAs(owner.workspace().groupView(pane));
        });
    }

    /**
     * Workspace plan 8c's final review (M-c): a drop that fails after a hover shows the tab that showed before again, its
     * item back in its pane there and the selected one, and the failure propagates.
     */
    @Test void aDropThatFailsAfterAHoverShowsTheTabThatShowedBefore() throws Exception {
        edt(() -> {
            var refusing = new Refusing();
            Tab notes = view("notes", refusing, TabRegistry.Placement.SPLIT_RIGHT);
            relayout(frame);
            var pane = notes.group().orElseThrow();
            Component source = startPane(owner, notes, onEntry(owner, d, 0.5));
            assertThat(owner.activeParentTab()).as("the hovered tab").isEqualTo(second);
            Point right = inside(pane(owner, d.group().orElseThrow()), 0.9, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, right, PANE);
            refusing.armed = true;
            assertThatThrownBy(() -> mouse(source, MouseEvent.MOUSE_RELEASED, right, PANE)).hasMessage("refused");
            assertThat(owner.activeParentTab()).as("the tab that showed before").isEqualTo(first);
            assertThat(owner.selectedTab()).isSameAs(notes);
            assertThat(refusing.getParent()).as("back in its pane").isSameAs(owner.workspace().groupView(pane));
            owner.selectParentTab(second);
            assertThat(shape(owner)).as("the hovered tab as it was").isEqualTo("d");
        });
    }

    /**
     * Workspace plan 8c's final review (M-d): when the tab a pane was to join fails to take its layout back too, the tab
     * the pane left still gets its own back, holding the item again, and that later failure is suppressed under the first.
     */
    @Test void aFailedUndoOfTheJoinedTabStillRestoresTheTabThePaneLeft() throws Exception {
        edt(() -> {
            int[] refusals = {0};
            owner.parentTabs().view(second).orElseThrow().addContainerListener(new java.awt.event.ContainerAdapter() {
                @Override public void componentAdded(java.awt.event.ContainerEvent event) {
                    if (refusals[0] == 0) return;
                    refusals[0]--;
                    throw new IllegalStateException("refused " + refusals[0]);
                }
            });
            var before = owner.windowLayout();
            var pane = c.group().orElseThrow();
            refusals[0] = 2;
            Throwable failure = catchThrowable(() -> owner.movePane(c, d.group().orElseThrow(), Side.RIGHT));
            assertThat(failure).as("the landing's failure").hasMessage("refused 1");
            assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).as("the joined tab's undo").contains("refused 0");
            assertThat(c.component().getParent()).as("back in its pane").isNotNull()
                .isSameAs(owner.parentTabs().view(first).orElseThrow().groupView(pane));
            assertThat(owner.parentTabs().view(first).orElseThrow().model()).as("its tab's layout")
                .isEqualTo(before.parentTabs().getFirst().layout());
            assertThat(c.closed()).as("nothing closes").isFalse();
        });
    }

    /** Tabs and panes spec 13 and Review Focus 4: a pane dropped into a tab whose pane is maximized ends the maximize. */
    @Test void aDropIntoAMaximizedTabEndsItsMaximize() throws Exception {
        edt(() -> {
            owner.selectParentTab(second);
            view("e", TabRegistry.Placement.SPLIT_RIGHT);
            owner.selectTab(d);
            owner.workspace().toggleMaximize();
            owner.selectParentTab(first);
            relayout(frame);
            Component source = startPane(owner, b, onEntry(owner, d, 0.5));
            assertThat(owner.workspace().model().maximized()).as("the hovered tab shows maximized").isPresent();
            Point right = inside(pane(owner, d.group().orElseThrow()), 0.9, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, right, PANE);
            assertThat(owner.tabDrag().target()).as("only the maximized pane shows")
                .contains(new TabDrag.Target.Beside(owner, d.group().orElseThrow(), Side.RIGHT));
            mouse(source, MouseEvent.MOUSE_RELEASED, right, PANE);
            assertThat(owner.workspace().model().maximized()).as("the maximize ends").isEmpty();
            assertThat(shape(owner)).isEqualTo("H[d, b, e]");
        });
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 3 and 9: a pane dropped on an entry's outer quarter becomes a new
     * tab at that place, which shows and lands at once; a bar at the place shows where.
     */
    @Test void aPaneDroppedOnAnEntrysOuterQuarterBecomesANewTabThere() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = startPane(owner, b, onEntry(owner, d, 0.9));
            assertThat(drag.target()).as("after the last tab").contains(new TabDrag.Target.NewTab(owner, 2));
            Point before = onEntry(owner, d, 0.1);
            mouse(source, MouseEvent.MOUSE_DRAGGED, before, PANE);
            assertThat(drag.target()).as("before the second tab").contains(new TabDrag.Target.NewTab(owner, 1));
            assertThat(owner.activeParentTab()).as("a new-tab place shows no tab").isEqualTo(first);
            assertThat(drag.overlay().placeholder()).as("a bar at the place").isEqualTo(marker(owner, null, 1));
            facts.clear();
            mouse(source, MouseEvent.MOUSE_RELEASED, before, PANE);
            assertThat(owner.parentTabCount()).isEqualTo(3);
            assertThat(owner.parentTabs().model().indexOf(owner.activeParentTab())).as("at that place, shown").isEqualTo(1);
            assertThat(shape(owner)).isEqualTo("b");
            assertThat(owner.selectedTab()).isSameAs(b);
            assertThat(moves()).as("within its window: no TAB_MOVED").isEmpty();
            owner.selectParentTab(first);
            assertThat(shape(owner)).isEqualTo("H[a, c]");
        });
    }

    /**
     * Workspace plan 8c Rulings 17: the selected item's pane dropped on a new-tab place of its window is announced once,
     * where it lands: the registry hears no selection, move, close or active pane, and no item of its old tab turns active.
     */
    @Test void aPaneDroppedOnANewTabPlaceIsAnnouncedOnceWhereItLands() throws Exception {
        edt(() -> {
            var heard = new ArrayList<Boolean>();
            Tab shell = shellBesideC(heard);
            relayout(frame);
            Point after = onEntry(owner, d, 0.9);
            Component source = startPane(owner, shell, after);
            assertThat(owner.tabDrag().target()).contains(new TabDrag.Target.NewTab(owner, 2));
            facts.clear();
            heard.clear();
            mouse(source, MouseEvent.MOUSE_RELEASED, after, PANE);
            assertThat(owner.parentTabCount()).isEqualTo(3);
            assertThat(owner.workspace().tabs()).as("its own tab, shown").containsExactly(shell);
            assertThat(announced()).as("nothing changed for the registry").isEmpty();
            assertThat(heard).as("no item it left turns active").isEmpty();
        });
    }

    /** Workspace plan 8c Rulings 8: in its own window a tab's only pane takes no new tab, at any new-tab place. */
    @Test void theOnlyPaneOfATabTakesNoNewTabInItsWindow() throws Exception {
        edt(() -> {
            owner.selectParentTab(second);
            Tab shownThere = owner.parentTabs().view(first).orElseThrow().selected();
            Point end = onEntry(owner, d, 0.9), start = onEntry(owner, shownThere, 0.1);
            Component source = startPane(owner, d, end);
            assertThat(owner.tabDrag().active()).isTrue();
            assertThat(owner.tabDrag().target()).as("after the last tab").isEmpty();
            mouse(source, MouseEvent.MOUSE_DRAGGED, start, PANE);
            assertThat(owner.tabDrag().target()).as("before the first").isEmpty();
            assertThat(owner.tabDrag().overlay().getParent()).as("no landing").isNull();
            assertThat(owner.activeParentTab()).as("an outer quarter shows no tab").isEqualTo(second);
            mouse(source, MouseEvent.MOUSE_RELEASED, start, PANE);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.activeParentTab()).isEqualTo(second);
        });
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 3: under Enterprise the "+", the room after the last tab and a
     * tab's outer quarter make a new tab; the tab's middle shows its tab and takes no drop.
     */
    @Test void underEnterpriseThePlusTheRoomAfterTheTabsAndATabsOuterQuarterMakeANewTab() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                WindowContent classic = content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                classic.connectContributions(model);
                Tab shell = classic.selectedTab();
                Tab notes = model.tabs().open("dev.example.notes", "notes", "notes", classic.id(), new JPanel(),
                    TabRegistry.Placement.SPLIT_RIGHT);
                frame(classic, 100, 50, 1208, 760);
                var strip = (ClassicTabs) classic.tabBar();
                TabDrag drag = classic.tabDrag();
                Point plus = middle(strip.add());
                Component source = startPane(classic, notes, plus);
                assertThat(drag.target()).as("the +").contains(new TabDrag.Target.NewTab(classic, 1));
                Rectangle entry = onScreen(strip, strip.entries().getFirst().getBounds());
                mouse(source, MouseEvent.MOUSE_DRAGGED, inside(entry, 0.5, 0.5), PANE);
                assertThat(drag.target()).as("its tab's middle").isEmpty();
                mouse(source, MouseEvent.MOUSE_DRAGGED, inside(entry, 0.1, 0.5), PANE);
                assertThat(drag.target()).as("its tab's leading quarter").contains(new TabDrag.Target.NewTab(classic, 0));
                Point after = new Point(plus.x + strip.add().getWidth(), plus.y);
                mouse(source, MouseEvent.MOUSE_DRAGGED, after, PANE);
                assertThat(drag.target()).as("the room after the +").contains(new TabDrag.Target.NewTab(classic, 1));
                mouse(source, MouseEvent.MOUSE_RELEASED, after, PANE);
                assertThat(classic.parentTabCount()).isEqualTo(2);
                assertThat(classic.parentTabs().model().indexOf(classic.activeParentTab())).as("at the end, shown").isEqualTo(1);
                assertThat(classic.selectedTab()).isSameAs(notes);
                classic.selectTab(shell);
                assertThat(classic.workspace().tabs()).containsExactly(shell);
            } finally {
                closeOwnersNow();
                themes.close();
                EnterpriseLook.restoreDefault();
            }
        });
    }
}
