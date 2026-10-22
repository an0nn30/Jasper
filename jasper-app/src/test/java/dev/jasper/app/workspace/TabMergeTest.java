package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.OtherTab;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pane flexibility spec 5 within one window: a whole tab moves into another tab of its window as one nested split beside
 * a pane, from Move Tab Into in a tab's menu and through {@link WindowContent#mergeTab}. The main window, in a stand-in
 * frame on a pretend screen, holds three tabs of plugin views: the first "a" beside "b" (H[a, b]), "b" active and
 * showing; the second "c" above "d" (V[c, d]), "d" active; the third "e". Its strip shows, as it holds three tabs.
 */
class TabMergeTest {
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
    private Tab a, b, c, d, e;
    private ParentTabId first, second, third;

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
            c = view("c", TabRegistry.Placement.NEW_TAB);
            d = view("d", TabRegistry.Placement.SPLIT_DOWN);
            e = view("e", TabRegistry.Placement.NEW_TAB);
            first = owner.parentTabOf(a).orElseThrow();
            second = owner.parentTabOf(c).orElseThrow();
            third = owner.parentTabOf(e).orElseThrow();
            owner.selectTab(b);
            frame = frame(owner, 100, 50, 1208, 760);
        });
    }

    /** Esc pressed and let go: whether the drag took its press. */
    private boolean esc() {
        boolean taken = keys.send(new KeyEvent(owner, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        keys.send(new KeyEvent(owner, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        return taken;
    }

    /** A drag that outlived its test would keep its listeners into the next test of this JVM. */
    @AfterEach void cleanup() throws Exception {
        edt(() -> {
            try {
                assertThat(owner.tabDrag().active() || !owner.tabDrag().spring.isRunning())
                    .as("a stopped drag leaves no wait running").isTrue();
                owner.tabDrag().cancel();
                assertThat(keys.dispatchers).as("no Esc dispatcher is left behind").isEmpty();
                assertThat(motionListeners()).as("no lost-release listener is left behind").isEqualTo(listeners);
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousKeys);
            }
        });
        closeOwners();
    }

    /** A plugin's view titled {@code title} in the window, placed as {@code placement} says. */
    private Tab view(String title, TabRegistry.Placement placement) { return view(title, new JPanel(), placement); }

    private Tab view(String title, JComponent component, TabRegistry.Placement placement) {
        return model.tabs().open("dev.example.notes", title, title, owner.id(), component, placement);
    }

    /** The window's tabs, in strip order. */
    private List<ParentTabId> ids() { return owner.windowLayout().parentTabs().stream().map(ParentTab::id).toList(); }

    /** What the registry heard of selections, moves and closes since {@link #facts} was last cleared. */
    private List<TerminalEvent> announced() {
        return facts.stream().filter(fact -> fact instanceof TerminalEvent.TabSelected || fact instanceof TerminalEvent.TabMoved
            || fact instanceof TerminalEvent.TabClosed).toList();
    }

    /** The Move Tab Into submenu of the menu of {@code window}'s strip entry that shows {@code item}. */
    static JMenu tabMoves(WindowContent window, Tab item) {
        for (Component child : window.tabMenu(item).getComponents())
            if (child instanceof JMenu moves && "Move Tab Into".equals(moves.getText())) return moves;
        throw new AssertionError("no Move Tab Into");
    }

    static List<String> texts(JMenu menu) {
        var texts = new ArrayList<String>();
        for (Component child : menu.getMenuComponents()) if (child instanceof JMenuItem item) texts.add(item.getText());
        return texts;
    }

    static JMenuItem entry(JMenu menu, String text) {
        for (Component child : menu.getMenuComponents())
            if (child instanceof JMenuItem item && text.equals(item.getText())) return item;
        throw new AssertionError("no " + text + " in " + texts(menu));
    }

    /** The titles of {@code window}'s strip entries as the strip draws them, from left to right. */
    static List<String> drawn(WindowContent window) {
        JComponent strip = window.tabBar().component();
        var shown = new ArrayList<Component>();
        selects(strip, shown);
        shown.sort(Comparator.comparingInt(select -> SwingUtilities.convertPoint(select, 0, 0, strip).x));
        return shown.stream().map(select -> select.getName().substring("select:".length())).toList();
    }

    /** Every title button under {@code parent} whose entry shows. */
    private static void selects(java.awt.Container parent, List<Component> found) {
        for (Component child : parent.getComponents()) {
            if (child.getName() != null && child.getName().startsWith("select:")) {
                if (child.getParent().isVisible()) found.add(child);
            } else if (child instanceof java.awt.Container container) {
                selects(container, found);
            }
        }
    }

    /**
     * Pane flexibility spec 5: the strip draws its entries in its tabs' order, as Select Tab, Move Tab Into's numbers, the
     * tab list and the drop places ({@code tabBounds()}) read them: titled {@code titles} from left to right.
     */
    private void drawsInOrder(String... titles) {
        assertThat(owner.parentTabs().bar().tabs()).extracting(owner::tabTitle).as("the tabs' order").containsExactly(titles);
        assertThat(drawn(owner)).as("the entries from left to right").containsExactly(titles);
        assertThat(owner.tabBar().tabBounds()).extracting(bounds -> bounds.x).as("the drop places from left to right")
            .doesNotHaveDuplicates().isSorted();
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 10 to 12: Move Tab Into, from the menu of a tab that does not
     * show, lists the window's other tabs and moves every pane of the tab to the right of the target's active pane as one
     * nested split, its sizes kept. Its entry goes, the tab it joined shows, named after the moved tab's active pane,
     * which takes the focus (spec 5 as amended), and the registry hears that item selected once and no move or close.
     */
    @Test void moveTabIntoPutsEveryPaneBesideTheTargetsActivePaneAsOneNestedSplit() throws Exception {
        edt(() -> {
            owner.parentTabs().view(second).orElseThrow().resizeSplit(List.of(), List.of(0.3, 0.7));
            JMenu moves = tabMoves(owner, d);
            assertThat(texts(moves)).as("the window's other tabs, numbered as Select Tab numbers them").containsExactly("1 b", "3 e");
            assertThat(moves.isEnabled()).isTrue();
            facts.clear();
            entry(moves, "1 b").doClick();
            assertThat(ids()).as("its entry goes").containsExactly(first, third);
            assertThat(owner.activeParentTab()).as("the tab it joined shows").isEqualTo(first);
            assertThat(PaneDragTest.shape(owner)).as("to the right of b, the tab's active pane").isEqualTo("H[a, b, V[c, d]]");
            var root = (Split) owner.workspace().model().root();
            assertThat(root.children()).extracting(Child::weight).as("b's half for the moved tab").containsExactly(0.5, 0.25, 0.25);
            assertThat(((Split) root.children().get(2).node()).children()).extracting(Child::weight).as("its sizes kept")
                .containsExactly(0.3, 0.7);
            assertThat(owner.selectedTab()).as("the moved tab's active pane has the focus").isSameAs(d);
            assertThat(owner.tabTitle(owner.parentTabs().active().selected())).isEqualTo("d");
            assertThat(announced()).as("announced once, where it lands")
                .containsExactly(new TerminalEvent.TabSelected(owner.id(), d.id().value()));
            assertThat(List.of(c, d)).noneMatch(Tab::closed);
            assertThat(d.window()).isEqualTo(owner.id());
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 15: the dragged entry resting on another tab's entry waits,
     * the reorder marker as before; when the wait ends that tab shows at once, its active item focused, and a drop beside
     * one of its panes moves every pane of the dragged tab there as one nested split, announced once where it lands.
     */
    @Test void restingOnAnotherEntryShowsItsTabAndAPaneOfItTakesTheDraggedTab() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Point rest = onEntry(owner, d, 0.6);
            Component source = start(owner, b, rest);
            assertThat(drag.spring.isRunning()).as("the wait runs").isTrue();
            assertThat(owner.activeParentTab()).as("not before it ends").isEqualTo(first);
            assertThat(drag.target()).as("the reorder marker as before").contains(new TabDrag.Target.Bar(owner, 1));
            elapse(drag);
            assertThat(owner.activeParentTab()).as("its tab shows at once").isEqualTo(second);
            assertThat(owner.selectedTab()).as("its active item focused, as Select Tab focuses it").isSameAs(d);
            assertThat(drag.active()).as("the drag runs on").isTrue();
            Point right = inside(pane(owner, d.group().orElseThrow()), 0.9, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, right);
            assertThat(drag.spring.isRunning()).as("off the entries the wait stops").isFalse();
            assertThat(drag.target()).contains(new TabDrag.Target.Into(owner, d.group().orElseThrow(), Side.RIGHT));
            facts.clear();
            mouse(source, MouseEvent.MOUSE_RELEASED, right);
            assertThat(ids()).as("its entry goes").containsExactly(second, third);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            assertThat(PaneDragTest.shape(owner)).as("beside d, as one nested split").isEqualTo("V[c, H[d, a, b]]");
            assertThat(owner.selectedTab()).as("the moved tab's active pane has the focus").isSameAs(b);
            assertThat(announced()).as("announced once, where it lands")
                .containsExactly(new TerminalEvent.TabSelected(owner.id(), b.id().value()));
        });
    }

    /**
     * Workspace plan 9a Rulings 15: no wait over the dragged tab's own entry; within a few pixels the entry still rests;
     * off the entries the wait stops; another entry's wait shows that tab; and the tab that shows waits for nothing.
     */
    @Test void theWaitRunsOnlyWhileTheEntryRestsOnAnotherTabsEntry() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = start(owner, b, onEntry(owner, b, 0.3));
            assertThat(drag.spring.isRunning()).as("over its own entry").isFalse();
            Point rest = onEntry(owner, d, 0.6);
            mouse(source, MouseEvent.MOUSE_DRAGGED, rest);
            assertThat(drag.spring.isRunning()).isTrue();
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(rest.x + 2, rest.y + 1));
            assertThat(drag.spring.isRunning()).as("within a few pixels it still rests").isTrue();
            mouse(source, MouseEvent.MOUSE_DRAGGED, inside(pane(owner, a.group().orElseThrow()), 0.5, 0.5));
            assertThat(drag.spring.isRunning()).as("off the entries it stops").isFalse();
            mouse(source, MouseEvent.MOUSE_DRAGGED, onEntry(owner, e, 0.6));
            elapse(drag);
            assertThat(owner.activeParentTab()).as("the entry it rests on now").isEqualTo(third);
            mouse(source, MouseEvent.MOUSE_DRAGGED, onEntry(owner, e, 0.7));
            assertThat(drag.spring.isRunning()).as("over the tab that shows").isFalse();
        });
    }

    /**
     * Pane flexibility spec 5: Esc, a release over no target, a lost release and focus loss after a spring bring back the
     * tab that showed before, its item focused; nothing moves.
     */
    @Test void aDragEndingWithoutADropAfterASpringShowsTheTabThatShowedBefore() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Point rest = onEntry(owner, d, 0.6);
            Component source = start(owner, b, rest);
            elapse(drag);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            assertThat(esc()).isTrue();
            assertThat(owner.activeParentTab()).as("after Esc").isEqualTo(first);
            assertThat(owner.selectedTab()).isSameAs(b);
            mouse(source, MouseEvent.MOUSE_RELEASED, rest);
            source = start(owner, b, rest);
            elapse(drag);
            mouse(source, MouseEvent.MOUSE_DRAGGED, onTitleBar(frame));
            mouse(source, MouseEvent.MOUSE_RELEASED, onTitleBar(frame));
            assertThat(owner.activeParentTab()).as("after a release over no target").isEqualTo(first);
            start(owner, b, rest);
            elapse(drag);
            broadcast(new MouseEvent(owner.workspace(), MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false, MouseEvent.NOBUTTON));
            assertThat(drag.active()).isFalse();
            assertThat(owner.activeParentTab()).as("after a lost release").isEqualTo(first);
            source = start(owner, b, rest);
            elapse(drag);
            owner.setActive(false);
            assertThat(drag.active()).isFalse();
            assertThat(owner.activeParentTab()).as("after focus loss").isEqualTo(first);
            mouse(source, MouseEvent.MOUSE_RELEASED, rest);
            assertThat(ids()).as("nothing moved").containsExactly(first, second, third);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[a, b]");
        });
    }

    /** Workspace plan 9a Rulings 7 and 15: a tab with a dialog open in a pane springs nothing; its strip still takes it. */
    @Test void aTabThatMovesAlongItsStripOnlySpringsNothing() throws Exception {
        edt(() -> {
            var held = a.pin();
            try {
                Point rest = onEntry(owner, d, 0.6);
                Component source = start(owner, b, rest);
                assertThat(owner.tabDrag().spring.isRunning()).as("no wait").isFalse();
                assertThat(owner.tabDrag().target()).as("its strip takes it").contains(new TabDrag.Target.Bar(owner, 1));
                mouse(source, MouseEvent.MOUSE_RELEASED, rest);
                assertThat(ids()).as("reordered, as before").containsExactly(second, first, third);
            } finally {
                held.close();
            }
        });
    }

    /** Pane flexibility spec 5: a reorder after a spring is a reorder, as before; the dragged tab shows again, its item focused. */
    @Test void aReorderAfterASpringShowsTheDraggedTabAgain() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Point rest = onEntry(owner, d, 0.6);
            Component source = start(owner, b, rest);
            elapse(drag);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            mouse(source, MouseEvent.MOUSE_RELEASED, rest);
            assertThat(ids()).as("reordered").containsExactly(second, first, third);
            assertThat(owner.activeParentTab()).as("the dragged tab shows again").isEqualTo(first);
            assertThat(owner.selectedTab()).isSameAs(b);
        });
    }

    /**
     * Workspace plan 9a Rulings 15: the entry rests while the cursor stays within {@link TabDrag#SPRING_SLOP} of where it
     * came to rest, that far included, measured from there and not from the last move; one pixel past it the wait starts
     * again where the cursor is, and far along the same entry the place it rests follows the cursor.
     */
    @Test void theEntryRestsWithinTheSlopOfWhereItCameToRest() throws Exception {
        edt(() -> {
            assertThat(TabDrag.SPRING_SLOP).isEqualTo(4);
            int slop = UIScale.scale(TabDrag.SPRING_SLOP);
            TabDrag drag = owner.tabDrag();
            Point rest = onEntry(owner, d, 0.4);
            Component source = start(owner, b, rest);
            assertThat(drag.springRest()).as("where it came to rest").isEqualTo(rest);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(rest.x + slop, rest.y));
            assertThat(drag.springRest()).as("the slop away, included").isEqualTo(rest);
            Point past = new Point(rest.x + slop + 1, rest.y);
            mouse(source, MouseEvent.MOUSE_DRAGGED, past);
            assertThat(drag.springRest()).as("a pixel past the slop from where it rested, though a pixel from the last move")
                .isEqualTo(past);
            Point far = onEntry(owner, d, 0.8);
            mouse(source, MouseEvent.MOUSE_DRAGGED, far);
            assertThat(drag.springRest()).as("far along the same entry").isEqualTo(far);
            elapse(drag);
            assertThat(owner.activeParentTab()).isEqualTo(second);
        });
    }

    /**
     * Workspace plan 9a Rulings 15: onto another entry the wait starts again, however close the cursor stays: from a pixel
     * left of the boundary between two entries to a pixel right of it, within the slop, the entry it rests on last shows.
     */
    @Test void crossingIntoTheNextEntryWithinTheSlopWaitsForThatEntry() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            List<Rectangle> entries = owner.tabBar().tabBounds();
            List<Tab> listed = owner.parentTabs().bar().tabs();
            Rectangle left = entries.get(listed.indexOf(d)), right = entries.get(listed.indexOf(e));
            Component strip = owner.tabBar().component();
            Point inD = onScreen(strip, new Rectangle(left.x + left.width - 1, left.y + left.height / 2, 0, 0)).getLocation();
            Point inE = onScreen(strip, new Rectangle(right.x, right.y + right.height / 2, 0, 0)).getLocation();
            assertThat(inD.distance(inE)).as("within the slop").isLessThanOrEqualTo(UIScale.scale(TabDrag.SPRING_SLOP));
            Component source = start(owner, b, inD);
            assertThat(drag.springRest()).isEqualTo(inD);
            mouse(source, MouseEvent.MOUSE_DRAGGED, inE);
            assertThat(drag.springRest()).as("the wait starts again over e's entry").isEqualTo(inE);
            elapse(drag);
            assertThat(owner.activeParentTab()).as("e's tab").isEqualTo(third);
        });
    }

    /**
     * Workspace plan 9a Rulings 15: the wait's listener running late shows nothing once the drag has ended, or once the
     * tab it waited on has closed, and leaves the drag no tab to bring back when it ends.
     */
    @Test void aWaitRunningOutLateShowsNothing() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Point rest = onEntry(owner, d, 0.6);
            Component source = start(owner, b, rest);
            assertThat(esc()).isTrue();
            mouse(source, MouseEvent.MOUSE_RELEASED, rest);
            runOutLate(drag);
            assertThat(owner.activeParentTab()).as("after the drag ended").isEqualTo(first);
            assertThat(drag.active()).isFalse();
            source = start(owner, b, onEntry(owner, e, 0.6));
            assertThat(drag.spring.isRunning()).isTrue();
            e.close();
            assertThat(ids()).as("its tab went with its only item").containsExactly(first, second);
            assertThat(drag.active()).as("the drag runs on").isTrue();
            int[] focused = {0};
            b.focusTarget(() -> focused[0]++);
            runOutLate(drag);
            assertThat(owner.activeParentTab()).as("after the tab closed").isEqualTo(first);
            assertThat(owner.selectedTab()).isSameAs(b);
            assertThat(esc()).isTrue();
            assertThat(focused[0]).as("no tab to bring back, nothing focused again").isZero();
            mouse(source, MouseEvent.MOUSE_RELEASED, rest);
            assertThat(ids()).containsExactly(first, second);
        });
    }

    /** Workspace plan 9a Rulings 15: Esc while the wait runs stops it, and nothing shows. */
    @Test void escWhileTheWaitRunsStopsItAndShowsNothing() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            start(owner, b, onEntry(owner, d, 0.6));
            assertThat(drag.spring.isRunning()).isTrue();
            assertThat(esc()).isTrue();
            assertThat(drag.spring.isRunning()).as("Esc stops the wait").isFalse();
            assertThat(owner.activeParentTab()).isEqualTo(first);
        });
    }

    /** Workspace plan 9a Rulings 15: a drag's wait is macOS's spring-loading delay, run once; only tests shorten it. */
    @Test void aDragWaitsMacOsSpringLoadingDelayOnce() throws Exception {
        edt(() -> {
            var drag = new TabDrag(List::of);
            assertThat(drag.spring.getInitialDelay()).isEqualTo(TabDrag.SPRING_DELAY).isEqualTo(500);
            assertThat(drag.spring.isRepeats()).isFalse();
            assertThat(owner.tabDrag().spring.getInitialDelay()).as("a framed window's drag never springs by itself")
                .isGreaterThan(60_000);
        });
    }

    /**
     * Workspace plan 9a Rulings 12 and 15 (Task 6's review): a drop on a pane of a tab that spring-loading showed in the
     * drag's own window, whose merge fails, leaves both tabs exactly as they were, the dragged tab shows again with its
     * item focused, and the failure propagates; the next drag merges it.
     */
    @Test void aMergeThatFailsAfterASpringLeavesBothTabsAsTheyWereAndShowsTheDraggedTabAgain() throws Exception {
        edt(() -> {
            var refusing = new PaneDragTest.Refusing();
            Tab notes = view("notes", refusing, TabRegistry.Placement.NEW_TAB);
            Tab more = view("more", TabRegistry.Placement.SPLIT_RIGHT);
            owner.selectTab(notes);
            relayout(frame);
            ParentTabId own = owner.parentTabOf(notes).orElseThrow();
            WorkspaceView moving = owner.parentTabs().view(own).orElseThrow(), target = owner.parentTabs().view(second).orElseThrow();
            var before = owner.windowLayout();
            Layout movingBefore = moving.model(), targetBefore = target.model();
            TabDrag drag = owner.tabDrag();
            Point rest = onEntry(owner, d, 0.6);
            Component source = start(owner, notes, rest);
            elapse(drag);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            Point right = inside(pane(owner, d.group().orElseThrow()), 0.9, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, right);
            assertThat(drag.target()).contains(new TabDrag.Target.Into(owner, d.group().orElseThrow(), Side.RIGHT));
            facts.clear();
            refusing.armed = true;
            assertThatThrownBy(() -> mouse(source, MouseEvent.MOUSE_RELEASED, right)).as("the failure propagates")
                .hasMessage("refused");
            assertThat(drag.active()).isFalse();
            assertThat(drag.overlay().getParent()).as("the landing goes").isNull();
            assertThat(owner.activeParentTab()).as("the dragged tab shows again").isEqualTo(own);
            assertThat(owner.selectedTab()).as("its item focused").isSameAs(notes);
            assertThat(owner.windowLayout()).as("the window's tabs as they were").isEqualTo(before);
            assertThat(owner.parentTabs().view(own)).as("its workspace stays").containsSame(moving);
            assertThat(moving.model()).as("the dragged tab exactly as it was").isEqualTo(movingBefore);
            assertThat(target.model()).as("the target exactly as it was").isEqualTo(targetBefore);
            assertThat(refusing.getParent()).as("back in its pane").isSameAs(moving.groupView(notes.group().orElseThrow()));
            assertThat(more.component().getParent()).as("in its pane").isSameAs(moving.groupView(more.group().orElseThrow()));
            assertThat(target.contains(notes) || target.contains(more)).as("the target holds neither").isFalse();
            assertThat(List.of(notes, more)).noneMatch(Tab::closed);
            assertThat(announced()).as("nothing moved or closed")
                .noneMatch(fact -> fact instanceof TerminalEvent.TabMoved || fact instanceof TerminalEvent.TabClosed);
            drawsInOrder("b", "d", "e", "notes");
            Component again = start(owner, notes, rest);
            elapse(drag);
            mouse(again, MouseEvent.MOUSE_DRAGGED, right);
            mouse(again, MouseEvent.MOUSE_RELEASED, right);
            assertThat(owner.parentTabOf(notes)).as("once it lands").contains(second);
            assertThat(ids()).containsExactly(first, second, third);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("V[c, H[d, notes, more]]");
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 14 and 15 (Task 6's review): after a spring in the drag's own
     * window, a pane of the tab that shows takes the dragged tab, its landing that pane's half on the nearest side, in
     * that window's layered pane; leaving the pane, Esc and the release each take the landing away.
     */
    @Test void aPaneOfASprungTabShowsTheLandingInTheDragsOwnWindow() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            JLayeredPane layers = SwingUtilities.getRootPane(owner).getLayeredPane();
            Point rest = onEntry(owner, d, 0.6);
            Component source = start(owner, b, rest);
            elapse(drag);
            GroupView under = owner.workspace().groupView(d.group().orElseThrow());
            Rectangle half = SwingUtilities.convertRectangle(under, new Rectangle(0, 0, under.getWidth() / 2, under.getHeight()), layers);
            Point left = inside(pane(owner, under.id()), 0.1, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, left);
            assertThat(drag.target()).contains(new TabDrag.Target.Into(owner, under.id(), Side.LEFT));
            assertThat(drag.overlay().getParent()).as("the landing shows in the drag's own window").isSameAs(layers);
            assertThat(drag.overlay().highlight()).as("d's left half").isEqualTo(half);
            assertThat(drag.overlay().placeholder()).isNull();
            mouse(source, MouseEvent.MOUSE_DRAGGED, onTitleBar(frame));
            assertThat(drag.target()).as("off the pane").isEmpty();
            assertThat(drag.overlay().getParent()).as("leaving the pane takes it away").isNull();
            mouse(source, MouseEvent.MOUSE_DRAGGED, left);
            assertThat(drag.overlay().getParent()).as("back over the pane").isSameAs(layers);
            assertThat(esc()).isTrue();
            assertThat(drag.overlay().getParent()).as("Esc takes it away").isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, left);
            source = start(owner, b, rest);
            elapse(drag);
            mouse(source, MouseEvent.MOUSE_DRAGGED, left);
            assertThat(drag.overlay().highlight()).isEqualTo(half);
            mouse(source, MouseEvent.MOUSE_RELEASED, left);
            assertThat(drag.overlay().getParent()).as("the release takes it away").isNull();
            assertThat(PaneDragTest.shape(owner)).as("beside d, on its left").isEqualTo("V[c, H[a, b, d]]");
        });
    }

    /** Pane flexibility spec 5: the menu of the tab that shows moves it into a tab that does not, which then shows. */
    @Test void theShownTabMovesIntoAHiddenOneWhichShows() throws Exception {
        edt(() -> {
            entry(tabMoves(owner, b), "3 e").doClick();
            assertThat(ids()).containsExactly(second, third);
            assertThat(owner.activeParentTab()).isEqualTo(third);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[e, a, b]");
            assertThat(owner.selectedTab()).isSameAs(b);
        });
    }

    /** Pane flexibility spec 5: a tab merged into a tab whose pane is maximized ends the maximize. */
    @Test void aTabMergedIntoAMaximizedTabEndsItsMaximize() throws Exception {
        edt(() -> {
            owner.selectTab(a);
            owner.toggleMaximize();
            assertThat(owner.workspace().model().maximized()).isPresent();
            owner.moveTabInto(third, first);
            assertThat(owner.workspace().model().maximized()).as("the maximize ends").isEmpty();
            assertThat(PaneDragTest.shape(owner)).as("to the right of a, the maximized pane").isEqualTo("H[a, e, b]");
            assertThat(owner.selectedTab()).isSameAs(e);
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 7: a tab holding an item that may not move, as under a modal
     * prompt, offers no tab and stays, and a window of one tab offers none: the submenu shows disabled.
     */
    @Test void aTabThatMayNotMoveAndAWindowOfOneTabOfferNoTab() throws Exception {
        edt(() -> {
            var held = c.pin();
            try {
                assertThat(owner.tabTargets(second)).as("a dialog open in one of its panes").isEmpty();
                assertThat(tabMoves(owner, d).isEnabled()).isFalse();
                owner.moveTabInto(second, first);
                owner.mergeTab(second, a.group().orElseThrow(), Side.LEFT);
                assertThat(ids()).as("it stays").containsExactly(first, second, third);
            } finally {
                held.close();
            }
            assertThat(tabMoves(owner, d).isEnabled()).isTrue();
            var lone = content(idleLauncher(pending), WindowRole.CHILD, null, true, () -> { });
            lone.connectContributions(model);
            Tab only = model.tabs().open("dev.example.notes", "only", "only", lone.id(), new JPanel(), TabRegistry.Placement.NEW_TAB);
            assertThat(lone.tabTargets(lone.activeParentTab())).isEmpty();
            assertThat(tabMoves(lone, only).isEnabled()).as("a window of one tab").isFalse();
        });
    }

    /** A tab is never merged into itself, and Move Tab Into names another tab. */
    @Test void aTabTakesNoMergeIntoItself() throws Exception {
        edt(() -> {
            owner.mergeTab(first, a.group().orElseThrow(), Side.RIGHT);
            owner.moveTabInto(first, first);
            assertThat(ids()).containsExactly(first, second, third);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[a, b]");
        });
    }

    /** Pane flexibility spec 5 and Review Focus 6: a tab's title is plain text in Move Tab Into, never HTML. */
    @Test void aTabsTitleIsPlainTextInMoveTabInto() throws Exception {
        edt(() -> {
            view("<html><b>B", TabRegistry.Placement.NEW_TAB);
            owner.selectTab(b);
            JMenu moves = tabMoves(owner, b);
            assertThat(texts(moves)).containsExactly("2 d", "3 e", "4 <html><b>B");
            for (Component child : moves.getMenuComponents()) {
                assertThat(((JMenuItem) child).getClientProperty("html.disable"))
                    .as("%s disables HTML", ((JMenuItem) child).getText()).isEqualTo(Boolean.TRUE);
                assertThat(((JMenuItem) child).getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey))
                    .as("%s renders no HTML", ((JMenuItem) child).getText()).isNull();
            }
        });
    }

    /**
     * Workspace plan 9a Rulings 12: a tab that fails to land stays as it was, its items back in their panes, nothing
     * closed, and the failure propagates.
     */
    @Test void aTabThatFailsToLandStaysAsItWas() throws Exception {
        edt(() -> {
            var refusing = new PaneDragTest.Refusing();
            Tab notes = view("notes", refusing, TabRegistry.Placement.NEW_TAB);
            owner.selectTab(b);
            ParentTabId own = owner.parentTabOf(notes).orElseThrow();
            var pane = notes.group().orElseThrow();
            var before = owner.windowLayout();
            refusing.armed = true;
            assertThatThrownBy(() -> owner.mergeTab(own, a.group().orElseThrow(), Side.RIGHT)).hasMessage("refused");
            assertThat(owner.windowLayout()).as("the window's tabs as they were").isEqualTo(before);
            assertThat(refusing.getParent()).as("back in its pane")
                .isSameAs(owner.parentTabs().view(own).orElseThrow().groupView(pane));
            assertThat(notes.closed()).as("nothing closes").isFalse();
            owner.mergeTab(own, a.group().orElseThrow(), Side.RIGHT);
            assertThat(owner.parentTabOf(notes)).as("once it lands").contains(first);
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 12: a two-pane tab that fails to land leaves both tabs exactly
     * as they were, each of its items back in its own pane, the strip as it was, and nothing closed.
     */
    @Test void aTwoPaneTabThatFailsToLandLeavesBothTabsExactlyAsTheyWere() throws Exception {
        edt(() -> {
            var refusing = new PaneDragTest.Refusing();
            Tab notes = view("notes", refusing, TabRegistry.Placement.NEW_TAB);
            Tab more = view("more", TabRegistry.Placement.SPLIT_RIGHT);
            owner.selectTab(b);
            ParentTabId own = owner.parentTabOf(notes).orElseThrow();
            assertThat(owner.parentTabOf(more)).contains(own);
            WorkspaceView moving = owner.parentTabs().view(own).orElseThrow(), target = owner.parentTabs().view(first).orElseThrow();
            var before = owner.windowLayout();
            Layout movingBefore = moving.model(), targetBefore = target.model();
            refusing.armed = true;
            assertThatThrownBy(() -> owner.mergeTab(own, a.group().orElseThrow(), Side.RIGHT)).hasMessage("refused");
            assertThat(owner.windowLayout()).as("the window's tabs as they were").isEqualTo(before);
            assertThat(owner.parentTabs().view(own)).as("its workspace stays").containsSame(moving);
            assertThat(moving.model()).as("the moving tab exactly as it was").isEqualTo(movingBefore);
            assertThat(target.model()).as("the target exactly as it was").isEqualTo(targetBefore);
            assertThat(refusing.getParent()).as("back in its pane").isSameAs(moving.groupView(notes.group().orElseThrow()));
            assertThat(more.component().getParent()).as("in its pane").isSameAs(moving.groupView(more.group().orElseThrow()));
            assertThat(target.contains(notes) || target.contains(more)).as("the target holds neither").isFalse();
            assertThat(owner.activeParentTab()).isEqualTo(first);
            assertThat(List.of(notes, more)).noneMatch(Tab::closed);
            drawsInOrder("b", "d", "e", "more");
        });
    }

    /**
     * Workspace plan 9a Rulings 12, as plan 8c's {@code PaneDragTest.aPaneMovedIntoAnotherTabIsAnnouncedOnceWhereItLands}:
     * the shown tab moving into a hidden one keeps its active item the selected item, so the registry hears no selection,
     * move or close, and nothing closes.
     */
    @Test void theShownTabMovingIntoAHiddenOneKeepsItsItemSelectedForTheRegistry() throws Exception {
        edt(() -> {
            facts.clear();
            entry(tabMoves(owner, b), "3 e").doClick();
            assertThat(owner.activeParentTab()).isEqualTo(third);
            assertThat(owner.selectedTab()).isSameAs(b);
            assertThat(announced()).as("nothing changed for the registry").isEmpty();
            assertThat(List.of(a, b)).noneMatch(Tab::closed);
        });
    }

    /** Workspace plan 9a Rulings 5: after a merge the tabs are numbered again, in strip order. */
    @Test void theTabsAreNumberedAgainAfterAMerge() throws Exception {
        edt(() -> {
            owner.moveTabInto(first, second);
            assertThat(ids()).containsExactly(second, third);
            assertThat(texts(tabMoves(owner, b))).as("the tab it joined, now the first, named after b").containsExactly("2 e");
            assertThat(texts(tabMoves(owner, e))).containsExactly("1 b");
            assertThat(owner.tabTargets(third)).extracting(OtherTab::number).containsExactly(1);
        });
    }

    /** Pane flexibility spec 5: a tab whose own pane is maximized arrives unmaximized, its active pane active. */
    @Test void aMaximizedTabArrivesUnmaximizedWithItsActivePaneActive() throws Exception {
        edt(() -> {
            owner.selectTab(a);
            owner.toggleMaximize();
            assertThat(owner.workspace().model().maximized()).contains(a.group().orElseThrow());
            owner.moveTabInto(first, third);
            Layout joined = owner.workspace().model();
            assertThat(joined.maximized()).as("it arrives unmaximized").isEmpty();
            assertThat(joined.activeGroup()).as("its active pane active").isEqualTo(a.group().orElseThrow());
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[e, a, b]");
            assertThat(owner.selectedTab()).isSameAs(a);
        });
    }

    /** Pane flexibility spec 5: a tab merges on any side of a pane; a split of that side's orientation joins its parent's. */
    @Test void aTabMergesAboveAPane() throws Exception {
        edt(() -> {
            owner.mergeTab(second, b.group().orElseThrow(), Side.UP);
            assertThat(ids()).containsExactly(first, third);
            assertThat(PaneDragTest.shape(owner)).as("above b, in b's column").isEqualTo("H[a, V[c, d, b]]");
            var column = (Split) ((Split) owner.workspace().model().root()).children().get(1).node();
            assertThat(column.children()).extracting(Child::weight).as("b's half for the moved tab").containsExactly(0.25, 0.25, 0.5);
            assertThat(owner.selectedTab()).isSameAs(d);
            assertThat(owner.workspace().model().activeGroup()).isEqualTo(d.group().orElseThrow());
        });
    }

    /** Workspace plan 9a Rulings 12: a workspace takes another tab's items only with the layout that places them. */
    @Test void aJoinWhoseLayoutPlacesOtherItemsIsRefused() throws Exception {
        edt(() -> {
            WorkspaceView target = owner.parentTabs().view(first).orElseThrow();
            Layout before = target.model(), arranged = owner.parentTabs().view(second).orElseThrow().model();
            GroupId pane = a.group().orElseThrow();
            assertThatThrownBy(() -> target.join(List.of(c), arranged, pane, Side.RIGHT)).as("one item short")
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> target.join(List.of(c, d, e), arranged, pane, Side.RIGHT)).as("one item more")
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(target.model()).as("nothing changes").isEqualTo(before);
            assertThat(List.of(c, d, e)).noneMatch(target::contains);
        });
    }

    /**
     * Pane flexibility spec 5: after a merge between tabs that are not neighbours the strip draws its entries in its tabs'
     * order at once: the third tab into the first.
     */
    @Test void theStripKeepsItsTabsOrderAfterTheThirdTabMergesIntoTheFirst() throws Exception {
        edt(() -> {
            drawsInOrder("b", "d", "e");
            owner.moveTabInto(third, first);
            assertThat(ids()).containsExactly(first, second);
            drawsInOrder("e", "d");
        });
    }

    /** Pane flexibility spec 5: the shown tab, the first, into the third, which is not its neighbour. */
    @Test void theStripKeepsItsTabsOrderAfterTheShownTabMergesIntoAHiddenOne() throws Exception {
        edt(() -> {
            owner.moveTabInto(first, third);
            assertThat(ids()).containsExactly(second, third);
            drawsInOrder("d", "b");
        });
    }

    /** Workspace plan 8c's pane move: the third tab's last pane beside a pane of the first, which is not its neighbour. */
    @Test void theStripKeepsItsTabsOrderAfterATabsLastPaneMovesIntoATabThatIsNotItsNeighbour() throws Exception {
        edt(() -> {
            owner.movePane(e, a.group().orElseThrow(), Side.RIGHT);
            assertThat(ids()).containsExactly(first, second);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[a, e, b]");
            drawsInOrder("e", "d");
        });
    }
}
