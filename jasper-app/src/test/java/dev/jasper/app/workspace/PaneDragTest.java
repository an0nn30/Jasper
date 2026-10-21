package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.Node;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Split;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
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
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousKeys);
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

    private boolean esc() {
        return keys.send(new KeyEvent(owner, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
    }

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
        var right = new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, other | InputEvent.BUTTON3_DOWN_MASK, 1, 1, 1, false,
            MouseEvent.BUTTON3);
        assertThat(TabDrag.movesPane(right, false)).as("another button").isFalse();
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
}
