package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.WorkspaceDropResolver.Drop;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Workspace tabs spec 12, 15 and 17: one tab drag across Jasper's windows, on synthetic events, headless. The window
 * manager's windows stand in stand-in frames on a pretend screen, each under a 28 px title bar: the main window, with
 * two terminal tabs, at 0,0 (1208 x 800), a child window, with two too, beside it at 1400,100 (800 x 600), and a new
 * window far away until a test places it. Under the standard look a pane's only tab shows no strip (parent tabs spec 7),
 * so a window's only tab has none to drag from or drop on; a test that drags a window's only tab gives its child window
 * the classic tabs, which always show (a mix of looks only these tests make; the drag reads no look).
 */
class TabDragWindowsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final TerminalRegistry terminals = new TerminalRegistry();
    private final List<TerminalEvent> facts = new ArrayList<>();
    /** Each window's stand-in frame, by window id. */
    private final Map<UUID, JPanel> frames = new HashMap<>();
    /** Where the window manager put tear-out windows' frames on the pretend screen, and the cursor it gave with each. */
    private final List<Rectangle> placed = new ArrayList<>();
    private final List<Point> cursors = new ArrayList<>();
    private WindowManager manager;
    private MainWindow main;
    private ChildWindow child;
    /** The look the manager's next window opens under. */
    private WorkspaceChrome look = WorkspaceChrome.STANDARD;

    @BeforeEach void windows() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            terminals.onEvent(facts::add);
            manager = new WindowManager(model, new WindowManager.Hooks(this::compose, this::frame, window -> { }, window -> { },
                window -> { }, state -> { }, window -> { }, () -> { }));
            main = manager.ensureMain("cold", null).orElseThrow();
            main.content().newTab(HOME);
            child = manager.openTerminalWindow(null).orElseThrow();
            child.content().newTab(HOME);
            place(frames.get(main.id()), 0, 0, 1208, 800);
            place(frames.get(child.id()), 1400, 100, 800, 600);
        });
        launch();
    }

    @AfterEach void cleanup() throws Exception {
        edt(() -> {
            manager.tabDrag().cancel();
            manager.closeAll();
        });
        closeOwners();
    }

    private void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    private WindowContent compose(WindowManager.Opening opening) {
        var launcher = new ShellLauncher(pending::add, directory -> IdleTerminalSession.start(), "idle-test-session");
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        var content = DesktopTestSupport.track(new WindowContent(opening.role(), launcher, opening.directory(), opening.empty(),
            directory -> manager.openTerminalWindow(directory), () -> { }, opening.emptied(), ThemeTestSupport.controller(true),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs, look));
        content.connectContributions(model);
        content.connectTerminals(terminals, () -> { });
        // A tab opening in a laid-out strip would move into its place over several EDT turns: tests take it at once.
        content.setAnimations(new AnimationSettings(false, 0, 0, 0));
        return content;
    }

    /** A window's stand-in frame, far from the others until a test places it; the bounds the manager gives it are recorded. */
    private WindowShell frame(JasperWindow window) {
        JPanel shell = TabDragTestSupport.frame(window.content(), 5000, 5000, 800, 600);
        frames.put(window.id(), shell);
        return new WindowShell(() -> { }, () -> { }, () -> { }, () -> { }, () -> null,
            component -> SwingUtilities.isDescendingFrom(component, shell), (bounds, cursor) -> {
                placed.add(bounds);
                cursors.add(cursor);
            });
    }

    private JPanel frameOf(JasperWindow window) { return frames.get(window.id()); }

    /**
     * Swaps the child window for one with a single tab under the classic tabs, in its place: their strip shows for a
     * window's only tab, so a drag can start from it. Its shell starts at the next launch.
     */
    private void classicChild() {
        child.requestClose();
        look = ClassicTabsTest.classic();
        try {
            child = manager.openTerminalWindow(null).orElseThrow();
        } finally {
            look = WorkspaceChrome.STANDARD;
        }
        place(frames.get(child.id()), 1400, 100, 800, 600);
    }

    private static GroupId activeGroup(JasperWindow window) { return window.content().workspace().model().activeGroup(); }

    private static Object session(Tab tab) { return ((TerminalContent) tab.content()).pane().session(); }

    private List<TerminalEvent> moves() { return facts.stream().filter(TerminalEvent.TabMoved.class::isInstance).toList(); }

    @Test void everyWindowOfTheManagerSharesItsOneDrag() throws Exception {
        edt(() -> {
            assertThat(main.content().tabDrag()).isSameAs(manager.tabDrag());
            assertThat(child.content().tabDrag()).isSameAs(manager.tabDrag());
            Tab tab = main.content().tabs().get(0);
            Point there = onTab(child.content(), child.content().tabs().get(0), 0.25);
            Component source = start(main.content(), tab, there);
            child.content().setActive(false);
            assertThat(manager.tabDrag().active()).as("another window losing the focus leaves the drag alone").isTrue();
            main.content().setActive(false);
            assertThat(manager.tabDrag().active()).as("its own window losing it ends it").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, there);
            assertThat(tab.window()).isEqualTo(main.id());
        });
    }

    @Test void aTabDroppedOnAnotherWindowsStripMovesThereWithItsSession() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab tab = from.tabs().get(0), there = to.tabs().get(0), last = to.tabs().get(1);
            Object session = session(tab);
            assertThat(session).as("its shell started").isNotNull();
            facts.clear();
            Point before = onTab(to, there, 0.25);
            Component source = start(from, tab, before);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Into(to, new Drop.Strip(activeGroup(child), 0)));
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows in the target window")
                .isSameAs(SwingUtilities.getRootPane(to).getLayeredPane());
            mouse(source, MouseEvent.MOUSE_RELEASED, before);
            assertThat(tab.window()).isEqualTo(child.id());
            assertThat(to.tabs()).as("before the child window's first tab").containsExactly(tab, there, last);
            assertThat(to.selectedTab()).isSameAs(tab);
            assertThat(from.tabs()).hasSize(1);
            assertThat(session(tab)).as("the same session").isSameAs(session);
            assertThat(((TerminalContent) tab.content()).pane().session().exitFuture()).isNotDone();
            assertThat(moves()).as("one move, reported where it arrives")
                .containsExactly(new TerminalEvent.TabMoved(tab.id().value(), main.id(), child.id()));
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
        });
    }

    @Test void aTabDroppedOnAnotherWindowsGroupEdgeOpensAGroupBesideIt() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab tab = from.tabs().get(0), there = to.tabs().get(0);
            GroupId group = activeGroup(child);
            facts.clear();
            drop(from, tab, inside(cards(to, group), 0.95, 0.5));
            var layout = to.workspace().model();
            assertThat(layout.groups()).hasSize(2);
            assertThat(((Split) layout.root()).orientation()).isEqualTo(Orientation.HORIZONTAL);
            assertThat(layout.groupOf(there.id())).contains(group);
            assertThat(layout.groupOf(tab.id())).isNotEqualTo(layout.groupOf(there.id()));
            assertThat(layout.activeTab()).contains(tab.id());
            assertThat(moves()).hasSize(1);
        });
    }

    /** A window's only tab shows no strip: a tab dragged onto that window near its top splits it, its middle takes it in. */
    @Test void aWindowWhoseOnlyTabShowsNoStripTakesDropsAtItsEdgesAndCentre() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            to.closeTab(to.tabs().get(1));
            relayout(frameOf(child));
            assertThat(to.windowTabs().isVisible()).isFalse();
            GroupId group = activeGroup(child);
            Rectangle whole = cards(to, group);
            assertThat(whole).as("its card area fills its workspace")
                .isEqualTo(onScreen(to.workspace(), new Rectangle(to.workspace().getSize())));
            Tab tab = from.tabs().get(0);
            Point top = new Point(whole.x + whole.width / 2, whole.y + 2);
            Component source = start(from, tab, top);
            assertThat(manager.tabDrag().target()).as("where a strip would be").contains(new TabDrag.Target.Into(to, new Drop.Edge(group, Side.UP)));
            Point middle = inside(whole, 0.5, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, middle);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Into(to, new Drop.Centre(group)));
            mouse(source, MouseEvent.MOUSE_RELEASED, middle);
            assertThat(tab.window()).isEqualTo(child.id());
            assertThat(to.tabs()).hasSize(2);
            assertThat(to.windowTabs().isVisible()).as("its second tab shows the strip").isTrue();
        });
    }

    @Test void aTabDroppedIntoAnotherWindowsMaximizedGroupEndsThatMaximize() throws Exception {
        edt(() -> {
            WindowContent to = main.content();
            Tab right = to.tabs().get(1);
            to.workspace().moveToNewGroup(right, activeGroup(main), Side.RIGHT);
            to.workspace().toggleMaximize();
            relayout(frameOf(main));
            GroupId maximized = activeGroup(main);
            assertThat(to.workspace().model().maximized()).contains(maximized);
            Tab tab = child.content().tabs().get(0);
            // Each pane holds one tab, so neither shows a strip (parent tabs spec 7): the drop is on the shown pane's middle.
            drop(child.content(), tab, inside(cards(to, maximized), 0.5, 0.5));
            assertThat(to.workspace().model().maximized()).as("a tab moving in ends it (spec 15)").isEmpty();
            assertThat(to.workspace().model().group(maximized).orElseThrow().tabs()).containsExactly(right.id(), tab.id());
        });
    }

    @Test void aTabDroppedIntoAnotherWindowsInactiveGroupLandsThereAndActivatesIt() throws Exception {
        edt(() -> {
            WindowContent to = main.content();
            Tab a = to.tabs().get(0), b = to.tabs().get(1);
            GroupId left = activeGroup(main);
            to.workspace().moveToNewGroup(b, left, Side.RIGHT);
            GroupId right = activeGroup(main);
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), new JPanel());
            to.selectTab(b);
            relayout(frameOf(main));
            assertThat(activeGroup(main)).isEqualTo(right);
            assertThat(to.workspace().model().group(right).orElseThrow().tabs()).containsExactly(b.id(), notes.id());
            Tab tab = child.content().tabs().get(0);
            // The registry's move puts it at the end of the active group, shown there; the drop then takes it on. The left
            // pane holds one tab, so it shows no strip (parent tabs spec 7): the drop is on its middle.
            drop(child.content(), tab, inside(cards(to, left), 0.5, 0.5));
            var layout = to.workspace().model();
            assertThat(tab.window()).isEqualTo(main.id());
            assertThat(layout.group(left).orElseThrow().tabs()).as("after the group's tab").containsExactly(a.id(), tab.id());
            assertThat(layout.activeGroup()).as("the group it landed in").isEqualTo(left);
            assertThat(layout.activeTab()).contains(tab.id());
            assertThat(layout.group(right).orElseThrow().tabs()).containsExactly(b.id(), notes.id());
            assertThat(layout.group(right).orElseThrow().selected()).as("the active group shows what it showed").contains(b.id());
            assertThat(moves()).hasSize(1);
        });
    }

    @Test void aChildWindowsLastTabDraggedBackClosesItAfterTheMoveReportsItself() throws Exception {
        edt(this::classicChild);
        launch();
        edt(() -> {
            Tab tab = child.content().tabs().get(0);
            Object session = session(tab);
            GroupId group = activeGroup(main);
            facts.clear();
            drop(child.content(), tab, inside(cards(main.content(), group), 0.5, 0.5));
            assertThat(tab.window()).isEqualTo(main.id());
            assertThat(main.content().workspace().model().group(group).orElseThrow().tabs()).as("after the group's tabs").endsWith(tab.id());
            assertThat(child.closed()).as("its last tab moved out").isTrue();
            assertThat(manager.children()).isEmpty();
            assertThat(session(tab)).isSameAs(session);
            int moved = facts.indexOf(new TerminalEvent.TabMoved(tab.id().value(), child.id(), main.id()));
            int closed = facts.indexOf(new TerminalEvent.WindowClosed(child.id()));
            assertThat(moved).as("TAB_MOVED").isNotNegative();
            assertThat(closed).as("then WINDOW_CLOSED").isGreaterThan(moved);
        });
    }

    @Test void aTabReleasedOutsideEveryWindowTearsOutWithItsGroupsSizeUnderTheCursor() throws Exception {
        edt(() -> {
            WindowContent from = main.content();
            Tab tab = from.tabs().get(1);
            GroupView group = groupOf(from, tab);
            Dimension size = group.getSize();
            Point press = middle(entry(from, tab));
            Rectangle home = onScreen(group, group.tabBounds(group).get(group.tabs().indexOf(tab)));
            Object session = session(tab);
            Point outside = new Point(2600, 900);
            // The workspace keeps the group's size and the tab stays under the cursor; its frame adds its title bar.
            Rectangle expected = new Rectangle(outside.x - (press.x - home.x), outside.y - (press.y - home.y),
                size.width, size.height);
            facts.clear();
            Component source = start(from, tab, outside);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.TearOut(expected, outside));
            assertThat(manager.tabDrag().overlay().getParent()).as("no landing in any window").isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, outside);
            ChildWindow torn = manager.children().getLast();
            assertThat(torn).isNotSameAs(child);
            assertThat(tab.window()).isEqualTo(torn.id());
            assertThat(torn.content().tabs()).containsExactly(tab);
            assertThat(placed).as("its workspace, which its frame grows by its title bar").containsExactly(expected);
            assertThat(cursors).as("on the display under the cursor").containsExactly(outside);
            assertThat(session(tab)).isSameAs(session);
            assertThat(moves()).containsExactly(new TerminalEvent.TabMoved(tab.id().value(), main.id(), torn.id()));
            assertThat(facts).as("the window opens empty, then the tab moves in")
                .noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
        });
    }

    @Test void aPluginsTabTearsOutAndDragsBackLikeATerminalTab() throws Exception {
        edt(() -> {
            JPanel view = new JPanel();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), view);
            relayout(frameOf(main));
            drop(main.content(), notes, new Point(3000, 1200));
            ChildWindow torn = manager.children().getLast();
            assertThat(notes.window()).isEqualTo(torn.id());
            assertThat(notes.component()).as("the same view").isSameAs(view);
            assertThat(view.isVisible()).as("shown in its new window").isTrue();
            // A second tab shows the torn-out window's strip, to drag the tab back from.
            torn.content().newTab(HOME);
            relayout(frameOf(torn));
            drop(torn.content(), notes, inside(cards(main.content(), activeGroup(main)), 0.5, 0.5));
            assertThat(notes.window()).isEqualTo(main.id());
            assertThat(torn.content().tabs()).doesNotContain(notes);
            assertThat(SwingUtilities.isDescendingFrom(view, main.content())).isTrue();
            assertThat(view.isVisible()).as("shown again").isTrue();
            assertThat(notes.closed()).isFalse();
        });
    }

    /** A tab's title can run to 200 characters: the label keeps near a tab's width, as VS Code's and IntelliJ's drag images do. */
    @Test void theDragLabelCapsALongTitleWithAnEllipsis() throws Exception {
        edt(() -> {
            String title = "quarterly-report-final-revision-".repeat(5) + "notes.txt";
            Tab notes = model.tabs().open("dev.example.editor", "notes", title, main.id(), new JPanel());
            relayout(frameOf(main));
            assertThat(main.content().tabTitle(notes)).isEqualTo(title);
            start(main.content(), notes, new Point(3000, 1200));
            assertThat(manager.tabDrag().label().text()).as("40 characters, the last an ellipsis")
                .isEqualTo(title.substring(0, 39) + "\u2026");
        });
    }

    @Test void theOnlyTabOfAChildWindowDraggedOutsideEveryWindowStays() throws Exception {
        edt(this::classicChild);
        launch();
        edt(() -> {
            Tab only = child.content().tabs().get(0);
            Point outside = new Point(3000, 1500);
            Component source = start(child.content(), only, outside);
            assertThat(manager.tabDrag().active()).isTrue();
            assertThat(manager.tabDrag().target()).as("its window already is its own").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, outside);
            assertThat(manager.children()).containsExactly(child);
            assertThat(placed).isEmpty();
            assertThat(only.window()).isEqualTo(child.id());
        });
    }

    @Test void releasingOverAWindowOutsideItsWorkspaceCancels() throws Exception {
        edt(() -> {
            Tab tab = main.content().tabs().get(0);
            Point title = onTitleBar(frameOf(child));
            Component source = start(main.content(), tab, title);
            assertThat(manager.tabDrag().target()).as("over the child window's title bar").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, title);
            Tab theirs = child.content().tabs().get(0);
            Rectangle workspace = onScreen(main.content().workspace(), new Rectangle(main.content().workspace().getSize()));
            Point chrome = new Point(workspace.x + workspace.width / 2, workspace.y - 3);
            drop(child.content(), theirs, chrome);
            assertThat(tab.window()).isEqualTo(main.id());
            assertThat(theirs.window()).as("over the main window's chrome").isEqualTo(child.id());
            assertThat(manager.children()).containsExactly(child);
            assertThat(placed).isEmpty();
            assertThat(moves()).isEmpty();
        });
    }

    @Test void whereWindowsOverlapTheMostRecentlyActiveOneTakesTheDrop() throws Exception {
        edt(() -> {
            place(frameOf(child), 300, 200, 800, 500);
            Point overlap = inside(cards(child.content(), activeGroup(child)), 0.5, 0.5);
            Rectangle mainFrame = onScreen(frameOf(main), new Rectangle(frameOf(main).getSize()));
            assertThat(mainFrame.contains(overlap)).as("over both windows").isTrue();
            child.events().activated().accept(true);
            Tab tab = main.content().tabs().get(0);
            drop(main.content(), tab, overlap);
            assertThat(tab.window()).as("the child window came to the front last").isEqualTo(child.id());
            main.events().activated().accept(true);
            relayout(frameOf(child));
            Tab back = child.content().tabs().get(0);
            drop(child.content(), back, overlap);
            assertThat(back.window()).as("now the main window did").isEqualTo(main.id());
        });
    }

    @Test void aPinnedTabMovesOnlyWithinItsWindow() throws Exception {
        edt(() -> {
            WindowContent from = main.content();
            Tab tab = from.tabs().get(0), other = from.tabs().get(1);
            var pin = tab.pin();
            try {
                Point there = onTab(child.content(), child.content().tabs().get(0), 0.25);
                Component source = start(from, tab, there);
                assertThat(manager.tabDrag().active()).isTrue();
                assertThat(manager.tabDrag().target()).as("a modal owner holds it in its window").isEmpty();
                Point outside = new Point(2600, 900);
                mouse(source, MouseEvent.MOUSE_DRAGGED, outside);
                assertThat(manager.tabDrag().target()).as("nor does it tear out").isEmpty();
                Point past = onTab(from, other, 0.75);
                mouse(source, MouseEvent.MOUSE_DRAGGED, past);
                assertThat(manager.tabDrag().target()).as("its own window still takes it").isPresent();
                mouse(source, MouseEvent.MOUSE_RELEASED, past);
                assertThat(from.tabs()).containsExactly(other, tab);
                assertThat(manager.children()).containsExactly(child);
            } finally {
                pin.close();
            }
        });
    }

    /** Parent tabs spec 5 and workspace plan 7b Rulings 11: a pane window opens where its pane was, at its size. */
    @Test void aPoppedOutTabsPaneWindowOpensAtItsPanesPlace() throws Exception {
        edt(() -> {
            GroupView pane = main.content().workspace().activeGroupView();
            Rectangle expected = onScreen(pane, new Rectangle(pane.getSize()));
            ChildWindow popped = manager.popOutTab(main.content().selectedTab()).orElseThrow();
            assertThat(popped.content().isPaneWindow()).isTrue();
            assertThat(placed).as("the pane's place and size").containsExactly(expected);
            assertThat(cursors).as("on the display under its middle")
                .containsExactly(new Point((int) expected.getCenterX(), (int) expected.getCenterY()));
        });
    }

    /**
     * Workspace plan 7b Rulings 11: a pane a maximize keeps out of the tree keeps its last size but has no place on
     * screen, so the platform places its pane window.
     */
    @Test void aPaneAMaximizeHidesPopsOutWhereThePlatformPutsIt() throws Exception {
        edt(() -> {
            WindowContent from = main.content();
            Tab hidden = from.tabs().get(0);
            from.workspace().moveToNewGroup(from.tabs().get(1), activeGroup(main), Side.RIGHT);
            relayout(frameOf(main));
            from.workspace().toggleMaximize();
            relayout(frameOf(main));
            GroupId pane = hidden.group().orElseThrow();
            assertThat(from.workspace().model().maximized()).isPresent().get().isNotEqualTo(pane);
            assertThat(from.workspace().groupView(pane).getWidth()).as("its last size, out of the tree").isPositive();
            ChildWindow popped = manager.popOutTab(hidden).orElseThrow();
            assertThat(popped.content().isPaneWindow()).isTrue();
            assertThat(placed).as("no place on screen").isEmpty();
        });
    }

    @Test void aTabMovedToAnotherWindowDuringTheDragEndsIt() throws Exception {
        edt(() -> {
            Tab tab = main.content().tabs().get(0);
            Point there = onTab(child.content(), child.content().tabs().get(0), 0.25);
            Component source = start(main.content(), tab, there);
            assertThat(manager.tabDrag().active()).isTrue();
            // Pop Out Tab from the keyboard while the mouse still drags the tab.
            ChildWindow moved = manager.popOutTab(tab).orElseThrow();
            assertThat(manager.tabDrag().active()).isFalse();
            assertThat(manager.tabDrag().label().showing()).isFalse();
            assertThat(manager.tabDrag().overlay().getParent()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, there);
            assertThat(tab.window()).as("where the command put it").isEqualTo(moved.id());
            assertThat(child.content().tabs()).hasSize(2);
        });
    }

    @Test void aMoveThatIsRefusedOrFailsAtTheDropLeavesTheTabWhereItWasAndTheDragEnded() throws Exception {
        edt(() -> {
            KeyboardFocusManager previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
            int listeners = motionListeners();
            try {
                WindowContent from = main.content(), to = child.content();
                Tab tab = from.tabs().get(0);
                Object session = session(tab);
                Point there = onTab(to, to.tabs().get(0), 0.25);
                // Refused: a listener of the tab throws as it takes the child window.
                var refused = new IllegalStateException("the tab will not go");
                var refusing = tab.onChanged(() -> { if (child.id().equals(tab.window())) throw refused; });
                Component source = start(from, tab, there);
                assertThat(keys.dispatchers).as("the drag owns Esc while it runs").hasSize(1);
                try {
                    assertThatThrownBy(() -> mouse(source, MouseEvent.MOUSE_RELEASED, there)).isSameAs(refused);
                } finally {
                    refusing.close();
                }
                assertThat(tab.window()).isEqualTo(main.id());
                assertThat(from.tabs()).contains(tab);
                assertThat(to.tabs()).doesNotContain(tab);
                assertEnded(keys, listeners);
                // Failed: the child window cannot show it (a listener of its TAB_MOVED throws an Error, which propagates).
                var failed = new Error("the window cannot show it");
                boolean[] thrown = {false};
                var failing = terminals.onEvent(fact -> {
                    if (!thrown[0] && fact instanceof TerminalEvent.TabMoved moved && moved.toWindowId().equals(child.id())) {
                        thrown[0] = true;
                        throw failed;
                    }
                });
                Component again = start(from, tab, there);
                try {
                    assertThatThrownBy(() -> mouse(again, MouseEvent.MOUSE_RELEASED, there)).isSameAs(failed);
                } finally {
                    failing.close();
                }
                assertThat(thrown[0]).isTrue();
                assertThat(tab.window()).as("back in the window it left").isEqualTo(main.id());
                assertThat(from.tabs()).contains(tab);
                assertThat(to.tabs()).doesNotContain(tab).hasSize(2);
                assertThat(session(tab)).as("the same session").isSameAs(session);
                assertThat(((TerminalContent) tab.content()).pane().session().exitFuture()).isNotDone();
                assertEnded(keys, listeners);
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
            }
        });
    }

    /** The drag's own window closing (Close Window, its last shell exiting) ends the drag clean, its landing elsewhere too. */
    @Test void theDragsOwnWindowClosingMidDragEndsItClean() throws Exception {
        edt(() -> recordingKeys(keys -> {
            int listeners = motionListeners();
            Tab tab = child.content().tabs().get(0);
            Point there = inside(cards(main.content(), activeGroup(main)), 0.5, 0.5);
            Component source = start(child.content(), tab, there);
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows in the main window")
                .isSameAs(SwingUtilities.getRootPane(main.content()).getLayeredPane());
            child.requestClose();
            assertThat(child.closed()).isTrue();
            assertEnded(keys, listeners);
            mouse(source, MouseEvent.MOUSE_RELEASED, there);
            assertThat(main.content().tabs()).as("nothing dropped").hasSize(2);
            assertThat(moves()).isEmpty();
        }));
    }

    /**
     * The target window closing while it holds the landing leaves the drag running: at the next event the landing leaves
     * that window, which takes no drop any more, and the drag goes on to its release.
     */
    @Test void theTargetWindowClosingUnderTheLandingLeavesTheDragRunning() throws Exception {
        edt(() -> recordingKeys(keys -> {
            int listeners = motionListeners();
            WindowContent from = main.content();
            Tab tab = from.tabs().get(0), other = from.tabs().get(1);
            Point there = onTab(child.content(), child.content().tabs().get(0), 0.25);
            Component source = start(from, tab, there);
            JLayeredPane layers = SwingUtilities.getRootPane(child.content()).getLayeredPane();
            assertThat(manager.tabDrag().overlay().getParent()).isSameAs(layers);
            child.requestClose();
            assertThat(child.closed()).isTrue();
            assertThat(manager.tabDrag().active()).as("another window closing leaves the drag alone").isTrue();
            mouse(source, MouseEvent.MOUSE_DRAGGED, there);
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing leaves the closed window").isNull();
            assertThat(manager.tabDrag().target()).as("where it stood, outside every open window")
                .hasValueSatisfying(target -> assertThat(target).isInstanceOf(TabDrag.Target.TearOut.class));
            Point past = onTab(from, other, 0.75);
            mouse(source, MouseEvent.MOUSE_DRAGGED, past);
            mouse(source, MouseEvent.MOUSE_RELEASED, past);
            assertThat(from.tabs()).as("the drag went on to its drop").containsExactly(other, tab);
            assertThat(manager.children()).isEmpty();
            assertEnded(keys, listeners);
        }));
    }

    /** The empty main workspace is all centre: a tab dropped there from another window moves in through the registry. */
    @Test void aTabDroppedIntoAnotherWindowsEmptyMainWorkspaceLandsThereSelected() throws Exception {
        edt(() -> {
            WindowContent to = main.content();
            for (Tab open : List.copyOf(to.tabs())) to.closeTab(open);
            relayout(frameOf(main));
            assertThat(to.tabs()).isEmpty();
            assertThat(main.closed()).as("the main window stays, its workspace empty").isFalse();
            Tab tab = child.content().tabs().get(0);
            Object session = session(tab);
            Point centre = middle(to.workspace());
            facts.clear();
            Component source = start(child.content(), tab, centre);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Into(to, new Drop.Centre(activeGroup(main))));
            mouse(source, MouseEvent.MOUSE_RELEASED, centre);
            assertThat(tab.window()).isEqualTo(main.id());
            assertThat(to.tabs()).containsExactly(tab);
            assertThat(to.selectedTab()).isSameAs(tab);
            assertThat(to.workspace().model().activeTab()).contains(tab.id());
            assertThat(session(tab)).isSameAs(session);
            assertThat(moves()).containsExactly(new TerminalEvent.TabMoved(tab.id().value(), child.id(), main.id()));
            assertThat(child.closed()).as("its other tab keeps it open").isFalse();
        });
    }

    /** Runs {@code test} with a focus manager that records the drag's Esc dispatcher, then puts the previous one back. */
    private static void recordingKeys(Consumer<CommandPaletteShortcutsTest.RecordingFocusManager> test) {
        KeyboardFocusManager previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
        KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
        try {
            test.accept(keys);
        } finally {
            KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
        }
    }

    /** The drag ended clean: no Esc dispatcher, lost-release listener, landing or label is left. */
    private void assertEnded(CommandPaletteShortcutsTest.RecordingFocusManager keys, int listeners) {
        assertThat(manager.tabDrag().active()).isFalse();
        assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
        assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
        assertThat(manager.tabDrag().overlay().getParent()).as("the landing goes").isNull();
        assertThat(manager.tabDrag().label().showing()).as("the label goes").isFalse();
    }

    @Test void releasingOverAnotherWindowOfJaspersLikeSettingsDropsNothing() throws Exception {
        edt(() -> {
            // A headless JVM has no windows: the drag is told where a Settings frame stands on the pretend screen.
            manager.tabDrag().otherWindows = () -> List.of(new Rectangle(2400, 700, 600, 500));
            Tab tab = main.content().tabs().get(1);
            Point overSettings = new Point(2600, 900);
            Component source = start(main.content(), tab, overSettings);
            assertThat(manager.tabDrag().active()).isTrue();
            assertThat(manager.tabDrag().target()).as("a window of Jasper's, outside any workspace").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, overSettings);
            assertThat(manager.children()).containsExactly(child);
            assertThat(placed).isEmpty();
            assertThat(tab.window()).isEqualTo(main.id());
        });
    }

    /**
     * The stand-in frames are no native windows, so the drag reaches this rule only through its helper; a full-screen
     * window on a second display is a GUI check.
     */
    @Test void onMacOsAWindowInFullScreenOnTheDragsDisplayOtherThanTheDragsOwnTakesNoDrop() {
        Rectangle display = new Rectangle(0, 0, 1512, 982);
        assertThat(TabDrag.dropFrame(display, display, false, true, true)).as("full screen: a Space of its own").isEqualTo(new Rectangle());
        assertThat(TabDrag.dropFrame(display, display, true, true, true)).as("the drag's own window is in sight").isEqualTo(display);
        assertThat(TabDrag.dropFrame(new Rectangle(0, 25, 1512, 957), display, false, true, true)).as("zoomed, under the menu bar")
            .isEqualTo(new Rectangle(0, 25, 1512, 957));
        Rectangle second = new Rectangle(1512, 0, 1920, 1080);
        assertThat(TabDrag.dropFrame(second, second, false, false, true))
            .as("full screen on another display, which shows a Space of its own: in sight").isEqualTo(second);
        assertThat(TabDrag.dropFrame(display, display, false, true, false)).as("elsewhere a window covering its display is in sight")
            .isEqualTo(display);
        assertThat(TabDrag.dropFrame(display, null, false, false, true)).as("no display known").isEqualTo(display);
    }

    @Test void aTearOutWindowIsKeptOnTheDisplayUnderTheCursor() {
        Rectangle usable = new Rectangle(0, 25, 1440, 875);
        assertThat(TabDrag.fitted(new Rectangle(100, 100, 800, 600), usable)).as("on it: as it is").isEqualTo(new Rectangle(100, 100, 800, 600));
        assertThat(TabDrag.fitted(new Rectangle(1000, 600, 800, 600), usable)).as("past its right and bottom edges: moved onto it")
            .isEqualTo(new Rectangle(640, 300, 800, 600));
        assertThat(TabDrag.fitted(new Rectangle(-50, 0, 2000, 1200), usable)).as("larger: as large as it").isEqualTo(usable);
        assertThat(TabDrag.fitted(new Rectangle(5, 5, 800, 600), new Rectangle())).as("no display known: as it is")
            .isEqualTo(new Rectangle(5, 5, 800, 600));
    }

    /**
     * A tear-out's workspace reaches right of and below the cursor by most of a group, so near the edge of a display left
     * of or above another it mostly covers that other one: the display under the cursor takes the window all the same.
     */
    @Test void aTearOutWindowOpensOnTheDisplayUnderTheCursor() {
        List<Rectangle> displays = List.of(new Rectangle(0, 0, 1440, 900), new Rectangle(1440, 0, 1920, 1080));
        assertThat(TabDrag.displayFor(new Point(1500, 220), displays)).as("its corner on the left display, the cursor on the right")
            .hasValue(1);
        assertThat(TabDrag.displayFor(new Point(130, 120), displays)).as("on the left").hasValue(0);
        assertThat(TabDrag.displayFor(new Point(1440, 0), displays)).as("on the edge: the display it begins").hasValue(1);
        assertThat(TabDrag.displayFor(new Point(5000, 5000), displays)).as("on none: the default display").isEmpty();
        List<Rectangle> left = List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(-1440, 0, 1440, 900));
        Rectangle workspace = new Rectangle(-80, 280, 800, 600);
        assertThat(workspace.intersection(left.get(0)).width).as("the workspace is mostly on the primary").isGreaterThan(700);
        assertThat(TabDrag.displayFor(new Point(-50, 300), left)).as("the cursor on the display left of the primary").hasValue(1);
        List<Rectangle> above = List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(0, -900, 1440, 900));
        assertThat(TabDrag.displayFor(new Point(400, -40), above)).as("the cursor on the display above the primary").hasValue(1);
        List<Rectangle> mirrored = List.of(new Rectangle(0, 0, 1440, 900), new Rectangle(0, 0, 1440, 900));
        assertThat(TabDrag.displayFor(new Point(100, 100), mirrored)).as("displays that overlap: the first").hasValue(0);
        assertThat(TabDrag.displayFor(new Point(100, 100), List.of())).as("no display known: the default").isEmpty();
    }
}
