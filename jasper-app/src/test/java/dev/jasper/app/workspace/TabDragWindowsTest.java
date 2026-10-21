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
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Workspace tabs spec 12, 15 and 17, tabs and panes spec 6 and 7: one tab drag, which drags panes too, across Jasper's
 * windows, on synthetic events, headless. The window manager's windows stand in stand-in frames on a pretend screen,
 * each under a 28 px title bar: the main window at 0,0 (1208 x 800) and a child window beside it at 1400,100 (800 x
 * 600), one tab each holding one terminal, and a new window far away until a test places it. The standard look shows a
 * window's strip from two tabs, so a test gives the window it drags from a second tab first.
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
    /** Each window a move brought to the front, in order. */
    private final List<WindowContent> raised = new ArrayList<>();
    private WindowManager manager;
    private MainWindow main;
    private ChildWindow child;

    @BeforeEach void windows() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            terminals.onEvent(facts::add);
            manager = new WindowManager(model, new WindowManager.Hooks(this::compose, this::frame, window -> { }, window -> { },
                window -> { }, state -> { }, window -> { }, () -> { }));
            main = manager.ensureMain("cold", null).orElseThrow();
            child = manager.openTerminalWindow(null).orElseThrow();
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
            KeyBindings.defaults(macOs), new CommandHistory(), macOs, WorkspaceChrome.STANDARD));
        content.connectContributions(model);
        content.connectTerminals(terminals, () -> raised.add(content));
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

    private static GroupId activeGroup(JasperWindow window) { return window.content().workspace().model().activeGroup(); }

    private static Object session(Tab tab) { return ((TerminalContent) tab.content()).pane().session(); }

    private List<TerminalEvent> moves() { return facts.stream().filter(TerminalEvent.TabMoved.class::isInstance).toList(); }

    /** A new tab in {@code window}, shown and laid out, so its strip shows: its id. Its shell starts at the next launch. */
    private ParentTabId secondTab(JasperWindow window) {
        window.content().newTab(HOME);
        relayout(frameOf(window));
        return window.content().activeParentTab();
    }

    /** The item that the strip's entry of {@code window}'s tab {@code id} shows. */
    private static Tab entryOf(WindowContent window, ParentTabId id) {
        return window.parentTabs().view(id).orElseThrow().selected();
    }

    /** {@code window}'s tabs, in strip order. */
    private static List<ParentTabId> tabIds(WindowContent window) {
        return window.parentTabs().model().parentTabs().stream().map(ParentTab::id).toList();
    }

    /** The band at the top of {@code window}'s tabs where its strip shows, on the pretend screen. */
    private static Rectangle band(WindowContent window) {
        var parents = window.parentTabs();
        int height = window.tabBar().component().getPreferredSize().height;
        return onScreen(parents, new Rectangle(0, 0, parents.getWidth(), height));
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

    @Test void everyWindowOfTheManagerSharesItsOneDrag() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> {
            assertThat(main.content().tabDrag()).isSameAs(manager.tabDrag());
            assertThat(child.content().tabDrag()).isSameAs(manager.tabDrag());
            Tab moving = entryOf(main.content(), id[0]);
            Point there = inside(band(child.content()), 0.5, 0.5);
            Component source = start(main.content(), moving, there);
            assertThat(manager.tabDrag().target()).as("the child window's band").contains(new TabDrag.Target.Bar(child.content(), 1));
            child.content().setActive(false);
            assertThat(manager.tabDrag().active()).as("another window losing the focus leaves the drag alone").isTrue();
            main.content().setActive(false);
            assertThat(manager.tabDrag().active()).as("its own window losing it ends it").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, there);
            assertThat(moving.window()).isEqualTo(main.id());
        });
    }

    /** Tabs and panes spec 6 and workspace plan 7c Rulings 4: a tab dropped on another window's strip moves there whole. */
    @Test void aTabDroppedOnAnotherWindowsStripMovesThereWhole() throws Exception {
        ParentTabId[] ids = new ParentTabId[3];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            main.content().split(Side.RIGHT);
            secondTab(main);
            ids[1] = child.content().activeParentTab();
            ids[2] = secondTab(child);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            ParentTabId moving = ids[0], left = ids[1], right = ids[2];
            WorkspaceView workspace = from.parentTabs().view(moving).orElseThrow();
            List<Tab> tabs = workspace.tabs();
            assertThat(tabs).as("its two panes").hasSize(2);
            Layout layout = workspace.model();
            var sessions = tabs.stream().map(TabDragWindowsTest::session).toList();
            Point between = onEntry(to, entryOf(to, right), 0.25);
            facts.clear();
            Component source = start(from, entryOf(from, moving), between);
            assertThat(manager.tabDrag().draggedParentTab()).isEqualTo(moving);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Bar(to, 1));
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows in the target window")
                .isSameAs(SwingUtilities.getRootPane(to).getLayeredPane());
            assertThat(manager.tabDrag().overlay().placeholder()).as("a bar between its entries")
                .isEqualTo(marker(to, entryOf(from, moving), 1));
            mouse(source, MouseEvent.MOUSE_RELEASED, between);
            assertThat(tabIds(to)).containsExactly(left, moving, right);
            assertThat(to.parentTabs().view(moving).orElseThrow().model()).as("whole, with its id and layout").isEqualTo(layout);
            assertThat(to.activeParentTab()).isEqualTo(moving);
            assertThat(tabIds(from)).hasSize(1);
            assertThat(from.tabBar().component().isVisible()).as("one tab left: its strip hides").isFalse();
            assertThat(moves()).as("one move per item, reported where it arrives").containsExactlyElementsOf(tabs.stream()
                .map(tab -> (TerminalEvent) new TerminalEvent.TabMoved(tab.id().value(), main.id(), child.id())).toList());
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
            for (int i = 0; i < tabs.size(); i++) assertThat(session(tabs.get(i))).as("the same session").isSameAs(sessions.get(i));
        });
    }

    /** Workspace plan 7c Rulings 3: a tab dragged along its strip moves no item, so no TAB_MOVED, and the focus stays. */
    @Test void aTabDraggedAlongItsStripReportsNoMoveAndLeavesTheFocus() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            ids[1] = secondTab(main);
        });
        launch();
        edt(() -> recordingKeys(keys -> {
            WindowContent from = main.content();
            Tab moving = entryOf(from, ids[0]);
            Point past = onEntry(from, entryOf(from, ids[1]), 0.75);
            facts.clear();
            Component source = start(from, moving, past);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Bar(from, 1));
            keys.own(moving.component());
            int[] focused = {0};
            moving.focusTarget(() -> focused[0]++);
            mouse(source, MouseEvent.MOUSE_RELEASED, past);
            assertThat(tabIds(from)).containsExactly(ids[1], ids[0]);
            assertThat(keys.getPermanentFocusOwner()).as("the focus stays where it was").isSameAs(moving.component());
            assertThat(focused[0]).as("and no item takes it").isZero();
            assertThat(moves()).as("no item moved").isEmpty();
        }));
    }

    /**
     * Workspace plan 7c Rulings 5 (the controller's ruling for 8a): a window showing no strip (the standard look, one tab)
     * takes a tab over the band at its top, where its strip shows, at its end; over its panes, nothing.
     */
    @Test void aWindowShowingNoStripTakesATabAlongItsTop() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = child.content().activeParentTab();
            ids[1] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            assertThat(to.tabBar().component().isVisible()).as("one tab").isFalse();
            Tab moving = entryOf(from, ids[1]);
            Point panes = inside(pane(to, activeGroup(child)), 0.5, 0.5);
            Component source = start(from, moving, panes);
            assertThat(manager.tabDrag().target()).as("a pane takes no tab").isEmpty();
            Rectangle band = band(to);
            Point top = inside(band, 0.5, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, top);
            assertThat(manager.tabDrag().target()).as("at the end of its strip").contains(new TabDrag.Target.Bar(to, 1));
            JLayeredPane layers = SwingUtilities.getRootPane(to).getLayeredPane();
            Point corner = new Point(band.getLocation());
            SwingUtilities.convertPointFromScreen(corner, layers);
            assertThat(manager.tabDrag().overlay().highlight()).as("the band where its strip shows")
                .isEqualTo(new Rectangle(corner, band.getSize()));
            assertThat(manager.tabDrag().overlay().placeholder()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, top);
            assertThat(tabIds(to)).containsExactly(ids[0], ids[1]);
            assertThat(to.tabBar().component().isVisible()).as("two tabs: its strip shows").isTrue();
            assertThat(moving.window()).isEqualTo(child.id());
            assertThat(from.tabBar().component().isVisible()).isFalse();
        });
    }

    /** Workspace plan 7c Rulings 5: the main window's empty workspace takes a tab dragged along its top, in its place. */
    @Test void theEmptyMainWorkspaceTakesATabAlongItsTop() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(child));
        launch();
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            for (Tab open : List.copyOf(to.tabs())) to.closeTab(open);
            relayout(frameOf(main));
            assertThat(to.tabs()).isEmpty();
            assertThat(to.tabBar().component().isVisible()).as("the empty workspace shows no strip").isFalse();
            Tab moving = entryOf(from, id[0]);
            Point top = inside(band(to), 0.5, 0.5);
            Component source = start(from, moving, top);
            assertThat(manager.tabDrag().target()).as("its strip has no entry").contains(new TabDrag.Target.Bar(to, 0));
            mouse(source, MouseEvent.MOUSE_RELEASED, top);
            assertThat(tabIds(to)).as("in the empty workspace's place").containsExactly(id[0]);
            assertThat(to.tabBar().component().isVisible()).as("one tab: still no strip").isFalse();
            assertThat(moving.window()).isEqualTo(main.id());
            assertThat(tabIds(from)).hasSize(1);
        });
    }

    /**
     * Workspace plan 7c's final fix wave (the controller's ruling for 8a): the main window's empty workspace has no panes,
     * so it takes a tab dragged anywhere over it in its place, and the whole of it is the landing.
     */
    @Test void theEmptyMainWorkspaceTakesATabAnywhereOverIt() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(child));
        launch();
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            for (Tab open : List.copyOf(to.tabs())) to.closeTab(open);
            relayout(frameOf(main));
            ParentTabId empty = to.activeParentTab();
            Tab moving = entryOf(from, id[0]);
            Point centre = middle(to.workspace());
            Component source = start(from, moving, centre);
            assertThat(manager.tabDrag().target()).as("in its middle").contains(new TabDrag.Target.Bar(to, 0));
            ParentTabsView parents = to.parentTabs();
            assertThat(manager.tabDrag().overlay().highlight()).as("all of it")
                .isEqualTo(SwingUtilities.convertRectangle(parents, new Rectangle(parents.getSize()),
                    SwingUtilities.getRootPane(to).getLayeredPane()));
            assertThat(manager.tabDrag().overlay().placeholder()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, centre);
            assertThat(tabIds(to)).as("in the empty workspace's place").containsExactly(id[0]);
            assertThat(to.parentTabs().view(empty)).isEmpty();
            assertThat(moving.window()).isEqualTo(main.id());
            assertThat(tabIds(from)).hasSize(1);
        });
    }

    /** Tabs and panes spec 6 and workspace plan 7c Rulings 6: a tab released outside every window becomes a full window. */
    @Test void aTabReleasedOutsideEveryWindowBecomesAFullWindowUnderTheCursor() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> {
            WindowContent from = main.content();
            WorkspaceView workspace = from.parentTabs().view(id[0]).orElseThrow();
            Layout layout = workspace.model();
            Tab moving = entryOf(from, id[0]);
            Object session = session(moving);
            TabStrip strip = from.tabBar();
            Rectangle home = onScreen(strip.component(), strip.tabBounds().get(1));
            Point press = middle(entry(from, moving));
            Point outside = new Point(2600, 900);
            // The new window's workspace is the tab's size, with the entry's corner where the cursor holds it.
            Rectangle expected = new Rectangle(outside.x - (press.x - home.x), outside.y - (press.y - home.y),
                workspace.getWidth(), workspace.getHeight());
            facts.clear();
            Component source = start(from, moving, outside);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.TearOut(expected, outside));
            assertThat(manager.tabDrag().overlay().getParent()).as("no landing in any window").isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, outside);
            ChildWindow full = manager.children().getLast();
            assertThat(full).isNotSameAs(child);
            assertThat(full.content().parentTabs().model()).as("a full window, the tab whole").isEqualTo(ParentTabs.of(new ParentTab(id[0], layout)));
            assertThat(placed).as("its workspace, which its frame grows by its title bar").containsExactly(expected);
            assertThat(cursors).as("on the display under the cursor").containsExactly(outside);
            assertThat(session(moving)).isSameAs(session);
            assertThat(moves()).containsExactly(new TerminalEvent.TabMoved(moving.id().value(), main.id(), full.id()));
            assertThat(from.parentTabCount()).isEqualTo(1);
        });
    }

    /** Tabs and panes spec 6: a tab dragged out of a full window of two tabs becomes a full window; its own keeps the other. */
    @Test void aTabDraggedOutOfAFullWindowOfTwoTabsBecomesAFullWindow() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(child));
        launch();
        edt(() -> {
            WindowContent from = child.content();
            ParentTabId stays = tabIds(from).getFirst();
            Layout layout = from.parentTabs().view(id[0]).orElseThrow().model();
            Tab moving = entryOf(from, id[0]);
            Object session = session(moving);
            Point outside = new Point(2600, 900);
            facts.clear();
            Component source = start(from, moving, outside);
            assertThat(manager.tabDrag().target()).as("out of every window").containsInstanceOf(TabDrag.Target.TearOut.class);
            mouse(source, MouseEvent.MOUSE_RELEASED, outside);
            ChildWindow full = manager.children().getLast();
            assertThat(full).isNotSameAs(child);
            assertThat(child.closed()).as("the full window it left stays open").isFalse();
            assertThat(tabIds(from)).as("with its other tab").containsExactly(stays);
            assertThat(full.content().parentTabs().model()).as("a full window, the tab whole").isEqualTo(ParentTabs.of(new ParentTab(id[0], layout)));
            assertThat(cursors).as("on the display under the cursor").containsExactly(outside);
            assertThat(session(moving)).isSameAs(session);
            assertThat(moves()).containsExactly(new TerminalEvent.TabMoved(moving.id().value(), child.id(), full.id()));
        });
    }

    /**
     * Workspace plan 7c Rulings 7: no standard look shows a strip over a window's only tab, so none can be dragged; the
     * window manager still moves it, as Move to Main Window does, onto another window's strip, never out to a window of
     * its own, and its window closes after the move.
     */
    @Test void aFullWindowsOnlyTabMovesOntoAnotherWindowsStripAndItsWindowClosesAfterTheMove() throws Exception {
        edt(() -> secondTab(main));
        launch();
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            ParentTabId only = from.activeParentTab();
            Tab moving = entryOf(from, only);
            assertThat(from.tabBar().component().isVisible()).as("no strip over it to drag it from").isFalse();
            assertThat(from.mayMoveTabToNewWindow(only)).as("a full window's only tab stays in its window").isFalse();
            List<ParentTabId> before = tabIds(to);
            facts.clear();
            assertThat(manager.moveParentTab(from, only, to, before.size())).isTrue();
            assertThat(tabIds(to)).as("at the end of the main window's strip").containsExactly(before.get(0), before.get(1), only);
            assertThat(child.closed()).as("its last tab moved out").isTrue();
            assertThat(manager.children()).isEmpty();
            int moved = facts.indexOf(new TerminalEvent.TabMoved(moving.id().value(), child.id(), main.id()));
            int closed = facts.indexOf(new TerminalEvent.WindowClosed(child.id()));
            assertThat(moved).as("TAB_MOVED").isNotNegative();
            assertThat(closed).as("then WINDOW_CLOSED").isGreaterThan(moved);
        });
    }

    /** Workspace plan 7b Rulings 10: a move taking the main window's only tab leaves its empty workspace under a new id. */
    @Test void aMoveThatEmptiesTheMainWindowGivesItsEmptyWorkspaceANewId() throws Exception {
        edt(() -> secondTab(child));
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            ParentTabId only = from.activeParentTab();
            List<ParentTabId> before = tabIds(to);
            assertThat(manager.moveParentTab(from, only, to, 2)).isTrue();
            assertThat(tabIds(to)).as("at the end of its strip").containsExactly(before.get(0), before.get(1), only);
            assertThat(main.closed()).as("the main window stays").isFalse();
            assertThat(from.tabs()).isEmpty();
            ParentTabId empty = from.activeParentTab();
            assertThat(tabIds(from)).containsExactly(empty);
            assertThat(empty).as("its empty workspace's id is new").isNotEqualTo(only);
            assertThat(tabIds(to)).as("so no id is in two windows").doesNotContain(empty);
        });
    }

    /** Workspace plan 7c Rulings 8: a tab holding a pinned item moves along its own strip only. */
    @Test void aTabHoldingAPinnedItemMovesAlongItsOwnStripOnly() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            ids[1] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content();
            var pin = from.parentTabs().view(ids[0]).orElseThrow().tabs().getFirst().pin();
            try {
                facts.clear();
                Point there = inside(band(child.content()), 0.5, 0.5);
                Component source = start(from, entryOf(from, ids[0]), there);
                assertThat(manager.tabDrag().active()).isTrue();
                assertThat(manager.tabDrag().target()).as("a modal owner holds one of its items in its window").isEmpty();
                mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(2600, 900));
                assertThat(manager.tabDrag().target()).as("nor does it tear out").isEmpty();
                Point past = onEntry(from, entryOf(from, ids[1]), 0.75);
                mouse(source, MouseEvent.MOUSE_DRAGGED, past);
                assertThat(manager.tabDrag().target()).as("its own strip still takes it").contains(new TabDrag.Target.Bar(from, 1));
                mouse(source, MouseEvent.MOUSE_RELEASED, past);
                assertThat(tabIds(from)).containsExactly(ids[1], ids[0]);
                assertThat(manager.children()).containsExactly(child);
                assertThat(moves()).isEmpty();
            } finally {
                pin.close();
            }
        });
    }

    /** Workspace plan 7c's final review: a tab holding an item whose close waits for its guard moves along its strip only. */
    @Test void aTabHoldingAnItemBeingClosedMovesAlongItsOwnStripOnly() throws Exception {
        edt(() -> {
            WindowContent from = main.content();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), new JPanel());
            ParentTabId holding = from.parentTabOf(notes).orElseThrow();
            ParentTabId last = secondTab(main);
            from.selectParentTab(holding);
            from.selectTab(notes);
            relayout(frameOf(main));
            var answer = new CompletableFuture<Boolean>();
            model.tabs().guard("dev.example.editor", (tabs, batch) -> answer);
            var closing = notes.requestClose();
            try {
                assertThat(closing).as("the guard has not answered").isNotDone();
                facts.clear();
                Point there = inside(band(child.content()), 0.5, 0.5);
                Component source = start(from, notes, there);
                assertThat(manager.tabDrag().active()).isTrue();
                assertThat(manager.tabDrag().target()).as("another window takes no tab holding an item being closed").isEmpty();
                assertThat(manager.tabDrag().overlay().getParent()).as("no landing shows").isNull();
                mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(2600, 900));
                assertThat(manager.tabDrag().target()).as("nor does it tear out").isEmpty();
                mouse(source, MouseEvent.MOUSE_DRAGGED, onEntry(from, entryOf(from, last), 0.75));
                assertThat(manager.tabDrag().target()).as("its own strip still takes it").isPresent();
                mouse(source, MouseEvent.MOUSE_RELEASED, there);
                assertThat(notes.window()).isEqualTo(main.id());
                assertThat(manager.children()).containsExactly(child);
                assertThat(moves()).isEmpty();
            } finally {
                answer.complete(false);
            }
            assertThat(closing).isCompletedWithValue(false);
        });
    }

    /** A tab whose pane holds a plugin's view moves with the same view, shown in its new window. */
    @Test void aTabHoldingAPluginsViewMovesWithItsView() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            JPanel view = new JPanel();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), view);
            ParentTabId holding = from.parentTabOf(notes).orElseThrow();
            secondTab(main);
            from.selectParentTab(holding);
            from.selectTab(notes);
            relayout(frameOf(main));
            drop(from, notes, inside(band(to), 0.5, 0.5));
            assertThat(notes.window()).isEqualTo(child.id());
            assertThat(notes.component()).as("the same view").isSameAs(view);
            assertThat(SwingUtilities.isDescendingFrom(view, to)).isTrue();
            assertThat(view.isVisible()).as("shown in its new window").isTrue();
            assertThat(notes.closed()).isFalse();
        });
    }

    /** A title can run to 200 characters: the label keeps near a tab's width, as VS Code's and IntelliJ's drag images do. */
    @Test void theDragLabelCapsALongTitleWithAnEllipsis() throws Exception {
        edt(() -> {
            String title = "quarterly-report-final-revision-".repeat(5) + "notes.txt";
            Tab notes = model.tabs().open("dev.example.editor", "notes", title, main.id(), new JPanel());
            ParentTabId holding = main.content().parentTabOf(notes).orElseThrow();
            secondTab(main);
            main.content().selectParentTab(holding);
            main.content().selectTab(notes);
            relayout(frameOf(main));
            assertThat(main.content().tabTitle(notes)).isEqualTo(title);
            start(main.content(), notes, new Point(3000, 1200));
            assertThat(manager.tabDrag().label().text()).as("40 characters, the last an ellipsis")
                .isEqualTo(title.substring(0, 39) + "…");
        });
    }

    @Test void releasingOverAnotherWindowsTitleBarChangesNothing() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> {
            Tab moving = entryOf(main.content(), id[0]);
            Point title = onTitleBar(frameOf(child));
            Component source = start(main.content(), moving, title);
            assertThat(manager.tabDrag().target()).as("over the child window's title bar").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, title);
            assertThat(moving.window()).isEqualTo(main.id());
            assertThat(manager.children()).containsExactly(child);
            assertThat(placed).isEmpty();
            assertThat(moves()).isEmpty();
        });
    }

    @Test void whereWindowsOverlapTheMostRecentlyActiveOneTakesTheDrop() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> {
            place(frameOf(child), 300, 200, 800, 500);
            Point overlap = inside(band(child.content()), 0.5, 0.5);
            Rectangle mainFrame = onScreen(frameOf(main), new Rectangle(frameOf(main).getSize()));
            assertThat(mainFrame.contains(overlap)).as("over both windows").isTrue();
            Tab moving = entryOf(main.content(), id[0]);
            child.events().activated().accept(true);
            Component source = start(main.content(), moving, overlap);
            assertThat(manager.tabDrag().target()).as("the child window came to the front last")
                .contains(new TabDrag.Target.Bar(child.content(), 1));
            main.events().activated().accept(true);
            mouse(source, MouseEvent.MOUSE_DRAGGED, overlap);
            assertThat(manager.tabDrag().target()).as("now the main window did, whose pane takes no tab").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, overlap);
            assertThat(moving.window()).isEqualTo(main.id());
        });
    }

    /** A command moving the item a dragged tab's entry shows (Move to Main Window from the keyboard) ends the drag. */
    @Test void anItemMovedToAnotherWindowDuringTheDragEndsIt() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(child));
        launch();
        edt(() -> {
            Tab moving = entryOf(child.content(), id[0]);
            Point there = inside(band(main.content()), 0.5, 0.5);
            Component source = start(child.content(), moving, there);
            assertThat(manager.tabDrag().active()).isTrue();
            model.tabs().moveToMainWindow(moving);
            assertThat(manager.tabDrag().active()).isFalse();
            assertThat(manager.tabDrag().label().showing()).isFalse();
            assertThat(manager.tabDrag().overlay().getParent()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, there);
            assertThat(moving.window()).as("where the command put it").isEqualTo(main.id());
            assertThat(child.content().tabs()).hasSize(1);
        });
    }

    @Test void aMoveThatIsRefusedOrFailsAtTheDropLeavesTheItemWhereItWasAndTheDragEnded() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> recordingKeys(keys -> {
            int listeners = motionListeners();
            WindowContent from = main.content(), to = child.content();
            Tab tab = entryOf(from, id[0]);
            Object session = session(tab);
            Point there = inside(band(to), 0.5, 0.5);
            // Refused: a listener of the item throws as it takes the child window.
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
            assertThat(from.parentTabOf(tab)).as("still in its tab").contains(id[0]);
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
            assertThat(to.tabs()).doesNotContain(tab).hasSize(1);
            assertThat(session(tab)).as("the same session").isSameAs(session);
            assertThat(((TerminalContent) tab.content()).pane().session().exitFuture()).isNotDone();
            assertEnded(keys, listeners);
        }));
    }

    /** The drag's own window closing (Close Window, its last shell exiting) ends the drag clean, its landing elsewhere too. */
    @Test void theDragsOwnWindowClosingMidDragEndsItClean() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(child));
        launch();
        edt(() -> recordingKeys(keys -> {
            int listeners = motionListeners();
            Tab tab = entryOf(child.content(), id[0]);
            Point there = inside(band(main.content()), 0.5, 0.5);
            Component source = start(child.content(), tab, there);
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows in the main window")
                .isSameAs(SwingUtilities.getRootPane(main.content()).getLayeredPane());
            child.requestClose();
            assertThat(child.closed()).isTrue();
            assertEnded(keys, listeners);
            mouse(source, MouseEvent.MOUSE_RELEASED, there);
            assertThat(main.content().tabs()).as("nothing dropped").hasSize(1);
            assertThat(moves()).isEmpty();
        }));
    }

    /**
     * The target window closing while it holds the landing leaves the drag running: at the next event the landing leaves
     * that window, which takes no drop any more, and the drag goes on to its release.
     */
    @Test void theTargetWindowClosingUnderTheLandingLeavesTheDragRunning() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> recordingKeys(keys -> {
            int listeners = motionListeners();
            WindowContent from = main.content();
            ParentTabId other = tabIds(from).getFirst();
            Tab tab = entryOf(from, id[0]);
            Point there = inside(band(child.content()), 0.5, 0.5);
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
            Point before = onEntry(from, entryOf(from, other), 0.25);
            mouse(source, MouseEvent.MOUSE_DRAGGED, before);
            mouse(source, MouseEvent.MOUSE_RELEASED, before);
            assertThat(tabIds(from)).as("the drag went on to its drop").containsExactly(id[0], other);
            assertThat(manager.children()).isEmpty();
            assertEnded(keys, listeners);
        }));
    }

    @Test void releasingOverAnotherWindowOfJaspersLikeSettingsDropsNothing() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> {
            // A headless JVM has no windows: the drag is told where a Settings frame stands on the pretend screen.
            manager.tabDrag().otherWindows = () -> List.of(new Rectangle(2400, 700, 600, 500));
            Tab tab = entryOf(main.content(), id[0]);
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

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 12: a pane dropped beside another window's pane moves there
     * through the tab registry, one TAB_MOVED, its session kept, and shows there; its old tab keeps its other pane.
     */
    @Test void aPaneDroppedBesideAnotherWindowsPaneMovesThroughTheRegistry() throws Exception {
        edt(() -> {
            main.content().split(Side.RIGHT);
            relayout(frameOf(main));
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            List<Tab> panes = from.workspace().tabs();
            Tab stays = panes.get(0), moving = panes.get(1), there = to.selectedTab();
            GroupId target = there.group().orElseThrow();
            Object kept = session(moving);
            Point left = inside(pane(to, target), 0.1, 0.5);
            facts.clear();
            Component source = startPane(from, moving, left);
            assertThat(manager.tabDrag().movingPane()).isTrue();
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Beside(to, target, Side.LEFT));
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows in the target window")
                .isSameAs(SwingUtilities.getRootPane(to).getLayeredPane());
            mouse(source, MouseEvent.MOUSE_RELEASED, left, PANE);
            assertThat(to.workspace().tabs()).as("beside it, on its left").containsExactly(moving, there);
            assertThat(to.selectedTab()).as("shown where it landed").isSameAs(moving);
            assertThat(from.workspace().tabs()).containsExactly(stays);
            assertThat(moves()).containsExactly(new TerminalEvent.TabMoved(moving.id().value(), main.id(), child.id()));
            assertThat(session(moving)).as("the same session").isSameAs(kept);
        });
    }

    /** Review Focus 5: a full window's last pane dropped into another window moves there, and its window closes after the move. */
    @Test void aFullWindowsLastPaneLeavingClosesItAfterItsMove() throws Exception {
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            Tab moving = from.selectedTab(), there = to.selectedTab();
            Point right = inside(pane(to, there.group().orElseThrow()), 0.9, 0.5);
            facts.clear();
            dropPane(from, moving, right);
            assertThat(to.workspace().tabs()).containsExactly(there, moving);
            assertThat(child.closed()).as("its last pane moved out").isTrue();
            assertThat(manager.children()).isEmpty();
            int moved = facts.indexOf(new TerminalEvent.TabMoved(moving.id().value(), child.id(), main.id()));
            int closed = facts.indexOf(new TerminalEvent.WindowClosed(child.id()));
            assertThat(moved).as("TAB_MOVED").isNotNegative();
            assertThat(closed).as("then WINDOW_CLOSED").isGreaterThan(moved);
        });
    }

    /**
     * Review Focus 5 and spec 4: the main window's only pane dropped beside another window's leaves the main window's
     * empty workspace, under the id it had, which no other window holds.
     */
    @Test void theMainWindowsOnlyPaneLeavingLeavesItsEmptyWorkspace() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = from.selectedTab(), there = to.selectedTab();
            ParentTabId emptied = from.activeParentTab();
            dropPane(from, moving, inside(pane(to, there.group().orElseThrow()), 0.5, 0.9));
            assertThat(to.workspace().tabs()).as("below it").containsExactly(there, moving);
            assertThat(main.closed()).isFalse();
            assertThat(from.parentTabs().isEmpty()).as("the main window's empty workspace").isTrue();
            assertThat(tabIds(from)).as("its one tab, empty").containsExactly(emptied);
            assertThat(tabIds(to)).as("no id in two windows").doesNotContain(emptied);
        });
    }

    /** Tabs and panes spec 7: a tab's only pane dropped into another window takes its tab with it; its window keeps the other. */
    @Test void aTabsOnlyPaneLeavingForAnotherWindowTakesItsTab() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            ids[1] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = entryOf(from, ids[1]), there = to.selectedTab();
            dropPane(from, moving, inside(pane(to, there.group().orElseThrow()), 0.9, 0.5));
            assertThat(to.workspace().tabs()).containsExactly(there, moving);
            assertThat(tabIds(from)).as("its tab went with it").containsExactly(ids[0]);
            assertThat(from.tabBar().component().isVisible()).as("one tab: the strip hides").isFalse();
        });
    }

    /**
     * Tabs and panes spec 13 and workspace plan 8c Rulings 4 and 5: hovering another window's tab shows it there until the
     * drag ends elsewhere, when the tab that showed before shows again; a pane of the hovered tab takes the drop.
     */
    @Test void hoveringAnotherWindowsTabShowsItUntilTheDragEndsElsewhere() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = child.content().activeParentTab();
            ids[1] = secondTab(child);
            main.content().split(Side.RIGHT);
            relayout(frameOf(main));
        });
        launch();
        edt(() -> recordingKeys(keys -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = from.workspace().tabs().get(1), there = entryOf(to, ids[0]);
            Point entry = onEntry(to, there, 0.5);
            Component source = startPane(from, moving, entry);
            assertThat(to.activeParentTab()).as("the hovered tab shows").isEqualTo(ids[0]);
            assertThat(keys.send(new KeyEvent(from, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).isTrue();
            assertThat(to.activeParentTab()).as("Esc: the tab that showed before").isEqualTo(ids[1]);
            mouse(source, MouseEvent.MOUSE_RELEASED, entry, PANE);
            source = startPane(from, moving, entry);
            Point below = inside(pane(to, there.group().orElseThrow()), 0.5, 0.9);
            mouse(source, MouseEvent.MOUSE_DRAGGED, below, PANE);
            mouse(source, MouseEvent.MOUSE_RELEASED, below, PANE);
            assertThat(to.activeParentTab()).as("the tab it landed in shows").isEqualTo(ids[0]);
            assertThat(to.workspace().tabs()).containsExactly(there, moving);
        }));
    }

    /** Tabs and panes spec 7: outside every window a pane drops nothing, and no window opens for it. */
    @Test void outsideEveryWindowAPaneDropsNothing() throws Exception {
        edt(() -> {
            main.content().split(Side.RIGHT);
            relayout(frameOf(main));
        });
        launch();
        edt(() -> {
            WindowContent from = main.content();
            Tab moving = from.workspace().tabs().get(1);
            Point outside = new Point(3000, 3000);
            Component source = startPane(from, moving, outside);
            assertThat(manager.tabDrag().active()).isTrue();
            assertThat(manager.tabDrag().target()).isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, outside, PANE);
            assertThat(manager.windows()).containsExactly(main, child);
            assertThat(placed).as("no window was placed").isEmpty();
            assertThat(from.workspace().tabs()).contains(moving);
        });
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 3 and 12: a pane dropped on the leading quarter of another
     * window's first entry becomes that window's first tab, shown, through the tab registry, one TAB_MOVED, under an id no
     * other window holds.
     */
    @Test void aPaneDroppedOnAnotherWindowsEntryQuarterBecomesANewTabThere() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            main.content().split(Side.RIGHT);
            relayout(frameOf(main));
            ids[0] = child.content().activeParentTab();
            ids[1] = secondTab(child);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = from.workspace().tabs().get(1);
            Point before = onEntry(to, entryOf(to, ids[0]), 0.1);
            facts.clear();
            Component source = startPane(from, moving, before);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.NewTab(to, 0));
            assertThat(manager.tabDrag().overlay().placeholder()).as("a bar before the first tab").isEqualTo(marker(to, null, 0));
            mouse(source, MouseEvent.MOUSE_RELEASED, before, PANE);
            List<ParentTabId> after = tabIds(to);
            assertThat(after).hasSize(3);
            assertThat(after.get(0)).as("first, shown").isEqualTo(to.activeParentTab());
            assertThat(after.subList(1, 3)).containsExactly(ids[0], ids[1]);
            assertThat(to.workspace().tabs()).containsExactly(moving);
            assertThat(moves()).containsExactly(new TerminalEvent.TabMoved(moving.id().value(), main.id(), child.id()));
            assertThat(after).as("no id in two windows").doesNotContainAnyElementsOf(tabIds(from));
        });
    }

    /** Review Focus 5 and workspace plan 8c Rulings 10: the main window's empty workspace takes a pane anywhere over it, in its place. */
    @Test void theMainWindowsEmptyWorkspaceTakesAPaneAnywhereOverIt() throws Exception {
        edt(() -> {
            child.content().split(Side.DOWN);
            relayout(frameOf(child));
        });
        launch();
        edt(() -> {
            WindowContent empty = main.content(), from = child.content();
            empty.closeTab(empty.selectedTab());
            assertThat(empty.parentTabs().isEmpty()).isTrue();
            ParentTabId only = empty.activeParentTab();
            Tab moving = from.workspace().tabs().get(1);
            var parents = empty.parentTabs();
            Point anywhere = inside(onScreen(parents, new Rectangle(parents.getSize())), 0.3, 0.7);
            facts.clear();
            Component source = startPane(from, moving, anywhere);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.NewTab(empty, 0));
            assertThat(manager.tabDrag().overlay().highlight()).as("all of the empty workspace").isEqualTo(SwingUtilities
                .convertRectangle(parents, new Rectangle(parents.getSize()), SwingUtilities.getRootPane(empty).getLayeredPane()));
            mouse(source, MouseEvent.MOUSE_RELEASED, anywhere, PANE);
            assertThat(empty.workspace().tabs()).containsExactly(moving);
            assertThat(tabIds(empty)).as("in its one tab's place, under its id").containsExactly(only);
            assertThat(from.workspace().tabs()).hasSize(1);
            assertThat(moves()).as("exactly one TAB_MOVED").containsExactly(new TerminalEvent.TabMoved(moving.id().value(),
                child.id(), main.id()));
        });
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 4 and 12 (its final review, T2 M6): a pane dropped on a pane of a
     * tab it showed in another window lands there, shown, the window raised and the item focused.
     */
    @Test void aPaneMovedIntoAnotherWindowRaisesItAndTakesTheFocusThere() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = child.content().activeParentTab();
            ids[1] = secondTab(child);
            main.content().split(Side.RIGHT);
            relayout(frameOf(main));
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = from.workspace().tabs().get(1), there = entryOf(to, ids[0]);
            int[] focused = {0};
            moving.focusTarget(() -> focused[0]++);
            Component source = startPane(from, moving, onEntry(to, there, 0.5));
            assertThat(to.activeParentTab()).as("the hovered tab").isEqualTo(ids[0]);
            Point right = inside(pane(to, there.group().orElseThrow()), 0.9, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, right, PANE);
            focused[0] = 0;
            raised.clear();
            mouse(source, MouseEvent.MOUSE_RELEASED, right, PANE);
            assertThat(to.activeParentTab()).as("the tab it landed in shows").isEqualTo(ids[0]);
            assertThat(to.workspace().tabs()).containsExactly(there, moving);
            assertThat(to.selectedTab()).isSameAs(moving);
            assertThat(focused[0]).as("the item takes the focus where it lands").isPositive();
            assertThat(raised).as("its new window comes to the front").endsWith(to);
        });
    }

    /**
     * Tabs and panes spec 13 and workspace plan 8c Rulings 5 (its final review, T2 M6): a drag that showed a tab in its own
     * window and one in another window and then ends by its window losing the focus, or by a lost release, shows the tabs
     * that showed before again in both.
     */
    @Test void focusLossOrALostReleaseMidHoverBringsBackTheEarlierTabs() throws Exception {
        ParentTabId[] ids = new ParentTabId[4];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            main.content().split(Side.RIGHT);
            ids[1] = secondTab(main);
            ids[2] = child.content().activeParentTab();
            ids[3] = secondTab(child);
            main.content().selectParentTab(ids[0]);
            relayout(frameOf(main));
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = from.workspace().tabs().get(1);
            Point own = onEntry(from, entryOf(from, ids[1]), 0.5), there = onEntry(to, entryOf(to, ids[2]), 0.5);
            Component source = startPane(from, moving, own, there);
            assertThat(from.activeParentTab()).as("its own window's hovered tab").isEqualTo(ids[1]);
            assertThat(to.activeParentTab()).as("the other window's").isEqualTo(ids[2]);
            from.setActive(false);
            assertThat(manager.tabDrag().active()).as("its window lost the focus").isFalse();
            assertThat(from.activeParentTab()).as("its own window's earlier tab").isEqualTo(ids[0]);
            assertThat(to.activeParentTab()).as("the other window's").isEqualTo(ids[3]);
            mouse(source, MouseEvent.MOUSE_RELEASED, there, PANE);
            from.setActive(true);
            startPane(from, moving, own, there);
            assertThat(to.activeParentTab()).isEqualTo(ids[2]);
            broadcast(new MouseEvent(from, MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false, MouseEvent.NOBUTTON));
            assertThat(manager.tabDrag().active()).as("a lost release").isFalse();
            assertThat(from.activeParentTab()).isEqualTo(ids[0]);
            assertThat(to.activeParentTab()).isEqualTo(ids[3]);
            assertThat(from.workspace().tabs()).contains(moving);
        });
    }

    /**
     * STATUS, plan 8c's Known (its final review, T2 M6): a pane whose move to another window fails lands in a new tab of
     * its old window, the registry's recovery; nothing closes, and the failure propagates.
     */
    @Test void aFailedMoveToAnotherWindowLandsTheItemInANewTabOfItsOldWindow() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            var refusing = new PaneDragTest.Refusing();
            Tab notes = model.tabs().open("dev.example.notes", "notes", "notes", main.id(), refusing,
                dev.jasper.app.contributions.TabRegistry.Placement.SPLIT_RIGHT);
            relayout(frameOf(main));
            ParentTabId own = from.activeParentTab();
            Tab there = to.selectedTab();
            Point right = inside(pane(to, there.group().orElseThrow()), 0.9, 0.5);
            Component source = startPane(from, notes, right);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Beside(to, there.group().orElseThrow(), Side.RIGHT));
            refusing.armed = true;
            assertThatThrownBy(() -> mouse(source, MouseEvent.MOUSE_RELEASED, right, PANE)).hasMessage("refused");
            assertThat(notes.closed()).as("nothing closes").isFalse();
            assertThat(notes.window()).isEqualTo(main.id());
            assertThat(tabIds(from)).as("a new tab of its old window").hasSize(2);
            assertThat(from.parentTabOf(notes)).isPresent().isNotEqualTo(java.util.Optional.of(own));
            assertThat(from.parentTabs().view(own).orElseThrow().tabs()).as("its old tab keeps its terminal").hasSize(1);
            assertThat(to.workspace().tabs()).containsExactly(there);
            assertThat(main.closed()).isFalse();
            assertThat(child.closed()).isFalse();
        });
    }

    /**
     * Review Focus 5 and workspace plan 8c Rulings 3 (its final review, T3 M2): a full window's only pane dropped on a
     * new-tab place of another window becomes a tab there, and its window closes after the move's TAB_MOVED.
     */
    @Test void aFullWindowsOnlyPaneDroppedOnANewTabPlaceClosesItAfterTheMove() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> ids[0] = secondTab(main));
        launch();
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            Tab moving = from.selectedTab();
            Point after = onEntry(to, entryOf(to, ids[0]), 0.9);
            facts.clear();
            Component source = startPane(from, moving, after);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.NewTab(to, 2));
            mouse(source, MouseEvent.MOUSE_RELEASED, after, PANE);
            assertThat(tabIds(to)).hasSize(3);
            assertThat(to.workspace().tabs()).as("its own tab, last, shown").containsExactly(moving);
            assertThat(child.closed()).as("its only pane moved out").isTrue();
            int moved = facts.indexOf(new TerminalEvent.TabMoved(moving.id().value(), child.id(), main.id()));
            int closed = facts.indexOf(new TerminalEvent.WindowClosed(child.id()));
            assertThat(moved).as("TAB_MOVED").isNotNegative();
            assertThat(closed).as("then WINDOW_CLOSED").isGreaterThan(moved);
            assertThat(moves()).as("one TAB_MOVED").hasSize(1);
        });
    }
}
