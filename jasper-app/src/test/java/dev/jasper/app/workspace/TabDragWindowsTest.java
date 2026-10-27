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
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private JasperWindow main;
    private JasperWindow child;

    @BeforeEach void windows() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            terminals.onEvent(facts::add);
            manager = new WindowManager(model, new WindowManager.Hooks(this::compose, this::frame, window -> { }, window -> { },
                window -> { }, state -> { }, window -> { }, () -> { }));
            main = manager.ensureWindow("cold", null).orElseThrow();
            child = manager.openWindow(null).orElseThrow();
            place(frames.get(main.id()), 0, 0, 1208, 800);
            place(frames.get(child.id()), 1400, 100, 800, 600);
        });
        launch();
    }

    @AfterEach void cleanup() throws Exception {
        edt(() -> {
            assertThat(manager.tabDrag().active() || !manager.tabDrag().spring.isRunning())
                .as("a stopped drag leaves no wait running").isTrue();
            manager.tabDrag().cancel();
            manager.closeAll();
        });
        closeOwners();
    }

    private void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    private WindowContent compose(WindowManager.Opening opening) {
        var launcher = new ShellLauncher(pending::add, directory -> IdleTerminalSession.start(), "idle-test-session");
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        var content = DesktopTestSupport.track(new WindowContent(launcher, opening.directory(), opening.empty(),
            directory -> manager.openWindow(directory), () -> { }, ThemeTestSupport.controller(true),
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

    /** Every window after the first, in opening order. */
    private List<JasperWindow> others() { var all = manager.windows(); return all.subList(1, all.size()); }

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
     * takes a tab over the band at its top, where its strip shows, at its end; over its panes, beside the pane under the
     * cursor (pane flexibility spec 5).
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
            assertThat(manager.tabDrag().target()).as("a pane takes the tab beside it, a tie going left")
                .contains(new TabDrag.Target.Into(to, activeGroup(child), Side.LEFT));
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
            JasperWindow full = others().getLast();
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
            JasperWindow full = others().getLast();
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
            assertThat(others()).isEmpty();
            int moved = facts.indexOf(new TerminalEvent.TabMoved(moving.id().value(), child.id(), main.id()));
            int closed = facts.indexOf(new TerminalEvent.WindowClosed(child.id()));
            assertThat(moved).as("TAB_MOVED").isNotNegative();
            assertThat(closed).as("then WINDOW_CLOSED").isGreaterThan(moved);
        });
    }

    /** Full windows spec 5.3: a move taking a window's only tab closes that window once the move has reported itself. */
    @Test void aMoveThatEmptiesAWindowClosesIt() throws Exception {
        edt(() -> secondTab(child));
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            ParentTabId only = from.activeParentTab();
            List<ParentTabId> before = tabIds(to);
            assertThat(manager.moveParentTab(from, only, to, 2)).isTrue();
            assertThat(tabIds(to)).as("at the end of its strip").containsExactly(before.get(0), before.get(1), only);
            assertThat(main.closed()).as("the window it emptied closes").isTrue();
            int moved = facts.indexOf(facts.stream().filter(TerminalEvent.TabMoved.class::isInstance).findFirst().orElseThrow());
            assertThat(facts.indexOf(new TerminalEvent.WindowClosed(main.id()))).as("after the move").isGreaterThan(moved);
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
                assertThat(others()).containsExactly(child);
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
                assertThat(others()).containsExactly(child);
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
            assertThat(others()).containsExactly(child);
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

    /** A command moving the item a dragged tab's entry shows (Move Tab to Window from the keyboard) ends the drag. */
    @Test void anItemMovedToAnotherWindowDuringTheDragEndsIt() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(child));
        launch();
        edt(() -> {
            Tab moving = entryOf(child.content(), id[0]);
            Point there = inside(band(main.content()), 0.5, 0.5);
            Component source = start(child.content(), moving, there);
            assertThat(manager.tabDrag().active()).isTrue();
            model.tabs().moveToWindow(moving, main.id());
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
            assertThat(others()).isEmpty();
            assertEnded(keys, listeners);
        }));
    }

    @Test void releasingOverAnotherWindowOfJaspersLikeSettingsDropsNothing() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> {
            // A headless JVM has no windows: the drag is told where a Settings frame stands on the pretend screen.
            manager.tabDrag().otherWindows = () -> List.of(new TabDrag.Auxiliary(new Object(),
                new Rectangle(2400, 700, 600, 500)));
            Tab tab = entryOf(main.content(), id[0]);
            Point overSettings = new Point(2600, 900);
            Component source = start(main.content(), tab, overSettings);
            assertThat(manager.tabDrag().active()).isTrue();
            assertThat(manager.tabDrag().target()).as("a window of Jasper's, outside any workspace").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, overSettings);
            assertThat(others()).containsExactly(child);
            assertThat(placed).isEmpty();
            assertThat(tab.window()).isEqualTo(main.id());
        });
    }

    /** How many listeners take every window event, as the window manager's watch of Jasper's other windows does. */
    private static int windowListeners() {
        return Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.WINDOW_EVENT_MASK).length;
    }

    /** A stand-in Settings frame over the whole child window and a little past it, on the pretend screen. */
    private Rectangle settingsOverChild() {
        Rectangle covered = onScreen(frameOf(child), new Rectangle(frameOf(child).getSize()));
        covered.grow(40, 40);
        return covered;
    }

    /**
     * Workspace plan 9a Rulings 21: of Jasper's windows under the cursor the one that came to the front last takes or
     * refuses a drop, as native drag and drop does on macOS and Windows and in VS Code. A Settings frame that came to
     * the front after the child window it covers gives a tab drag no target over the child's strip or pane: no strip
     * drop, no merge and no spring-loading, and the landing hides; a release there moves nothing.
     */
    @Test void aWindowOfJaspersThatCameToTheFrontAfterTheWindowItCoversTakesNoTabDrop() throws Exception {
        ParentTabId[] ids = new ParentTabId[3];
        edt(() -> {
            ids[0] = secondTab(main);
            ids[1] = child.content().activeParentTab();
            ids[2] = secondTab(child);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Object settings = new Object();
            manager.tabDrag().otherWindows = () -> List.of(new TabDrag.Auxiliary(settings, settingsOverChild()));
            child.events().activated().accept(true);
            manager.tabDrag().activated(settings);
            main.events().activated().accept(true);
            Tab moving = entryOf(from, ids[0]);
            GroupId pane = activeGroup(child);
            List<ParentTabId> before = tabIds(to);
            Point onOwnStrip = onEntry(from, entryOf(from, tabIds(from).getFirst()), 0.25);
            Component source = start(from, moving, onOwnStrip);
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows on the drag's own strip")
                .isSameAs(SwingUtilities.getRootPane(from).getLayeredPane());
            Point overPane = inside(pane(to, pane), 0.9, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, overPane);
            assertThat(manager.tabDrag().target()).as("Settings above the child's pane").isEmpty();
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing hides").isNull();
            mouse(source, MouseEvent.MOUSE_DRAGGED, inside(band(to), 0.5, 0.5));
            assertThat(manager.tabDrag().target()).as("Settings above the child's strip").isEmpty();
            mouse(source, MouseEvent.MOUSE_DRAGGED, onEntry(to, entryOf(to, ids[1]), 0.5));
            assertThat(manager.tabDrag().spring.isRunning()).as("nor does a hidden tab's entry spring").isFalse();
            facts.clear();
            mouse(source, MouseEvent.MOUSE_RELEASED, overPane);
            assertThat(tabIds(to)).as("nothing merged").isEqualTo(before);
            assertThat(to.workspace().tabs()).hasSize(1);
            assertThat(moving.window()).isEqualTo(main.id());
            assertThat(moves()).isEmpty();
            assertThat(placed).as("nor torn out").isEmpty();
        });
    }

    /** Workspace plan 9a Rulings 21: the same Settings frame, come to the front before the child window, lies below. */
    @Test void aWindowOfJaspersThatCameToTheFrontBeforeTheWindowUnderItLeavesItTheDrop() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> ids[0] = secondTab(main));
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Object settings = new Object();
            manager.tabDrag().otherWindows = () -> List.of(new TabDrag.Auxiliary(settings, settingsOverChild()));
            manager.tabDrag().activated(settings);
            child.events().activated().accept(true);
            main.events().activated().accept(true);
            Tab moving = entryOf(from, ids[0]), there = to.selectedTab();
            GroupId pane = activeGroup(child);
            Point overPane = inside(pane(to, pane), 0.9, 0.5);
            Component source = start(from, moving, overPane);
            assertThat(manager.tabDrag().target()).as("the child window is on top")
                .contains(new TabDrag.Target.Into(to, pane, Side.RIGHT));
            mouse(source, MouseEvent.MOUSE_RELEASED, overPane);
            assertThat(to.workspace().tabs()).as("the drop reaches the pane").containsExactly(there, moving);
            assertThat(moves()).containsExactly(new TerminalEvent.TabMoved(moving.id().value(), main.id(), child.id()));
        });
    }

    /**
     * Workspace plan 9a Rulings 21, as the final review's residual round rules it: macOS keeps a window above the
     * window that owns it, so a plugin's modeless dialog owned by the child window is above the child window even when
     * the child window came to the front after it, and a tab drag over it has no target; an ownerless frame that came
     * to the front before the child window still leaves the drop to the child window's pane.
     */
    @Test void aWindowOwnedByTheWindowUnderTheCursorIsAboveItWhicheverCameToTheFrontLast() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> ids[0] = secondTab(main));
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Object dialog = new Object(), settings = new Object();
            var owned = new TabDrag.Auxiliary(dialog, settingsOverChild(), List.of(frameOf(child)));
            var ownerless = new TabDrag.Auxiliary(settings, settingsOverChild());
            manager.tabDrag().otherWindows = () -> List.of(owned);
            manager.tabDrag().activated(dialog);
            manager.tabDrag().activated(settings);
            child.events().activated().accept(true);
            main.events().activated().accept(true);
            Tab moving = entryOf(from, ids[0]), there = to.selectedTab();
            GroupId pane = activeGroup(child);
            Point overPane = inside(pane(to, pane), 0.9, 0.5);
            Component source = start(from, moving, overPane);
            assertThat(manager.tabDrag().target()).as("the dialog its owner came in front of after it").isEmpty();
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing hides").isNull();
            manager.tabDrag().otherWindows = () -> List.of(ownerless);
            mouse(source, MouseEvent.MOUSE_DRAGGED, overPane);
            assertThat(manager.tabDrag().target()).as("an ownerless frame below the child window")
                .contains(new TabDrag.Target.Into(to, pane, Side.RIGHT));
            mouse(source, MouseEvent.MOUSE_RELEASED, overPane);
            assertThat(to.workspace().tabs()).as("the drop reaches the pane").containsExactly(there, moving);
        });
    }

    /**
     * Workspace plan 9a Rulings 21 for a pane drag (tabs and panes spec 7): Settings above the child window's pane
     * gives it no target there; once the child window comes to the front after Settings, its pane takes the drop.
     */
    @Test void aPaneDragOverAWindowOfJaspersAboveAnotherWindowsPaneDropsNothingThere() throws Exception {
        edt(() -> {
            main.content().split(Side.RIGHT);
            relayout(frameOf(main));
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Object settings = new Object();
            manager.tabDrag().otherWindows = () -> List.of(new TabDrag.Auxiliary(settings, settingsOverChild()));
            child.events().activated().accept(true);
            manager.tabDrag().activated(settings);
            main.events().activated().accept(true);
            Tab moving = from.workspace().tabs().get(1), there = to.selectedTab();
            GroupId target = there.group().orElseThrow();
            Point left = inside(pane(to, target), 0.1, 0.5);
            Component source = startPane(from, moving, left);
            assertThat(manager.tabDrag().movingPane()).isTrue();
            assertThat(manager.tabDrag().target()).as("Settings above the child's pane").isEmpty();
            assertThat(manager.tabDrag().overlay().getParent()).isNull();
            child.events().activated().accept(true);
            mouse(source, MouseEvent.MOUSE_DRAGGED, left, PANE);
            assertThat(manager.tabDrag().target()).as("the child window came to the front since")
                .contains(new TabDrag.Target.Beside(to, target, Side.LEFT));
            mouse(source, MouseEvent.MOUSE_RELEASED, left, PANE);
            assertThat(to.workspace().tabs()).containsExactly(moving, there);
        });
    }

    /**
     * Workspace plan 9a Rulings 21: the window manager hears Jasper's other windows come to the front from when one of
     * its windows first does until its last window closes, so no listener outlives its windows.
     */
    @Test void theManagerWatchesJaspersOtherWindowsWhileItsWindowsAreOpen() throws Exception {
        edt(() -> {
            int before = windowListeners();
            main.events().activated().accept(true);
            child.events().activated().accept(true);
            assertThat(windowListeners()).as("one listener for every window").isEqualTo(before + 1);
            child.requestClose();
            assertThat(windowListeners()).as("while the main window is open").isEqualTo(before + 1);
            manager.closeAll();
            assertThat(windowListeners()).as("gone with the last window").isEqualTo(before);
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
            assertThat(others()).isEmpty();
            int moved = facts.indexOf(new TerminalEvent.TabMoved(moving.id().value(), child.id(), main.id()));
            int closed = facts.indexOf(new TerminalEvent.WindowClosed(child.id()));
            assertThat(moved).as("TAB_MOVED").isNotNegative();
            assertThat(closed).as("then WINDOW_CLOSED").isGreaterThan(moved);
        });
    }

    /** Review Focus 5; full windows spec 5.3: a window's only pane dropped beside another window's closes the window. */
    @Test void aWindowsOnlyPaneLeavingClosesTheWindow() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = from.selectedTab(), there = to.selectedTab();
            dropPane(from, moving, inside(pane(to, there.group().orElseThrow()), 0.5, 0.9));
            assertThat(to.workspace().tabs()).as("below it").containsExactly(there, moving);
            assertThat(main.closed()).as("the window it emptied closes").isTrue();
            assertThat(manager.windows()).containsExactly(child);
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

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 15: the dragged entry resting on another window's entry shows
     * that tab there, so a pane of it takes the drop; a drag that ends over no target after a spring shows the tab that
     * showed there before.
     */
    @Test void restingOnAnotherWindowsEntryShowsItsTabThereForTheDrop() throws Exception {
        ParentTabId[] ids = new ParentTabId[3];
        edt(() -> {
            ids[0] = child.content().activeParentTab();
            ids[1] = secondTab(child);
            ids[2] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            TabDrag drag = manager.tabDrag();
            Tab hidden = entryOf(to, ids[0]);
            Point rest = onEntry(to, hidden, 0.5);
            Component source = start(from, entryOf(from, ids[2]), rest);
            elapse(drag);
            assertThat(to.activeParentTab()).as("its tab shows there at once").isEqualTo(ids[0]);
            mouse(source, MouseEvent.MOUSE_DRAGGED, onTitleBar(frameOf(child)));
            mouse(source, MouseEvent.MOUSE_RELEASED, onTitleBar(frameOf(child)));
            assertThat(to.activeParentTab()).as("over no target: the tab that showed before").isEqualTo(ids[1]);
            source = start(from, entryOf(from, ids[2]), rest);
            elapse(drag);
            Point right = inside(pane(to, hidden.group().orElseThrow()), 0.9, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, right);
            assertThat(drag.target()).contains(new TabDrag.Target.Into(to, hidden.group().orElseThrow(), Side.RIGHT));
            mouse(source, MouseEvent.MOUSE_RELEASED, right);
            assertThat(to.activeParentTab()).as("the tab it joined").isEqualTo(ids[0]);
            assertThat(to.workspace().tabs()).hasSize(2).startsWith(hidden);
            assertThat(tabIds(from)).as("its entry went").hasSize(1).doesNotContain(ids[2]);
        });
    }

    /**
     * Workspace plan 9a Rulings 15: the wait's listener running late, after the window whose entry the dragged one rested
     * on closed, shows nothing there, and the drag runs on.
     */
    @Test void aWaitRunningOutLateAfterItsWindowClosedShowsNothing() throws Exception {
        ParentTabId[] ids = new ParentTabId[3];
        edt(() -> {
            ids[0] = child.content().activeParentTab();
            ids[1] = secondTab(child);
            ids[2] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            TabDrag drag = manager.tabDrag();
            Component source = start(from, entryOf(from, ids[2]), onEntry(to, entryOf(to, ids[0]), 0.5));
            assertThat(drag.spring.isRunning()).isTrue();
            child.requestClose();
            assertThat(child.closed()).isTrue();
            runOutLate(drag);
            assertThat(to.activeParentTab()).as("nothing shows in the closed window").isEqualTo(ids[1]);
            assertThat(drag.active()).as("the drag runs on").isTrue();
            assertThat(from.activeParentTab()).isEqualTo(ids[2]);
            mouse(source, MouseEvent.MOUSE_DRAGGED, onTitleBar(frameOf(main)));
            mouse(source, MouseEvent.MOUSE_RELEASED, onTitleBar(frameOf(main)));
            assertThat(tabIds(from)).as("nothing moved").hasSize(2);
        });
    }

    /** Every pane id of {@code window}'s tabs. */
    private static java.util.Set<GroupId> groupIds(WindowContent window) {
        var ids = new java.util.HashSet<GroupId>();
        for (ParentTab tab : window.windowLayout().parentTabs()) for (Group group : tab.layout().groups()) ids.add(group.id());
        return ids;
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 13 and 14: a tab's entry dragged over another window's pane
     * shows the half beside which it lands, and its release moves every pane of the tab there through the tab registry,
     * one TAB_MOVED per item in the tab's order, each session kept, as one nested split to the right of that pane,
     * which is not the target tab's active one. The tab it joined keeps its id and shows the moved tab's active item,
     * its first pane here, its window raised; the moved tab's entry goes, and no pane is in two windows.
     */
    @Test void aTabDroppedOnAnotherWindowsPaneMergesBesideItThroughTheRegistry() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            main.content().split(Side.RIGHT);
            secondTab(main);
            child.content().split(Side.RIGHT);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            WorkspaceView workspace = from.parentTabs().view(ids[0]).orElseThrow();
            List<Tab> tabs = workspace.tabs();
            from.selectTab(tabs.get(0));
            relayout(frameOf(main));
            Tab shown = workspace.selected(), there = to.workspace().tabs().get(0), active = to.selectedTab();
            assertThat(shown).as("its first pane, not the split's new one").isSameAs(tabs.get(0));
            assertThat(active).as("the target tab's active pane is its other one").isNotSameAs(there);
            ParentTabId target = to.activeParentTab();
            GroupId pane = there.group().orElseThrow();
            var sessions = tabs.stream().map(TabDragWindowsTest::session).toList();
            Point right = inside(pane(to, pane), 0.9, 0.5);
            facts.clear();
            raised.clear();
            Component source = start(from, entryOf(from, ids[0]), right);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Into(to, pane, Side.RIGHT));
            JLayeredPane layers = SwingUtilities.getRootPane(to).getLayeredPane();
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows in the target window").isSameAs(layers);
            GroupView under = to.workspace().groupView(pane);
            assertThat(manager.tabDrag().overlay().highlight()).as("the pane's right half").isEqualTo(SwingUtilities.convertRectangle(under,
                new Rectangle(under.getWidth() - under.getWidth() / 2, 0, under.getWidth() / 2, under.getHeight()), layers));
            mouse(source, MouseEvent.MOUSE_RELEASED, right);
            assertThat(tabIds(to)).as("the tab it joined keeps its id").containsExactly(target);
            assertThat(to.workspace().tabs()).as("to the right of the pane, in the tab's order")
                .containsExactly(there, tabs.get(0), tabs.get(1), active);
            assertThat(((Split) to.workspace().model().root()).children()).extracting(Child::weight)
                .as("one nested split in the pane's right half").containsExactly(0.25, 0.125, 0.125, 0.5);
            assertThat(to.selectedTab()).as("the moved tab's active item").isSameAs(shown);
            assertThat(raised).as("its window comes to the front").contains(to);
            assertThat(tabIds(from)).as("its entry goes").hasSize(1).doesNotContain(ids[0]);
            assertThat(moves()).as("one move per item, in the tab's order").containsExactlyElementsOf(tabs.stream()
                .map(tab -> (TerminalEvent) new TerminalEvent.TabMoved(tab.id().value(), main.id(), child.id())).toList());
            var selected = new TerminalEvent.TabSelected(to.id(), shown.id().value());
            assertThat(facts.lastIndexOf(selected)).as("the moved active item selected after the last move")
                .isGreaterThan(facts.lastIndexOf(moves().getLast()));
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
            for (int i = 0; i < tabs.size(); i++) assertThat(session(tabs.get(i))).as("the same session").isSameAs(sessions.get(i));
            assertThat(groupIds(from)).as("no pane is in two windows").doesNotContainAnyElementsOf(groupIds(to));
        });
    }

    /** The terminal pane of {@code tab}'s item. */
    private static UUID paneOf(Tab tab) { return ((TerminalContent) tab.content()).pane().id(); }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 12 and 16, with terminals: a tab of two terminal panes
     * merged into the tab that shows is announced once, where it lands: its active item selected, then the active pane;
     * the tab that shows merged into a hidden one, whose item stays selected and active, announces nothing. Nothing
     * opens, closes or moves for the registry.
     */
    @Test void aTabOfTerminalsMergedWithinItsWindowIsAnnouncedOnceWhereItLands() throws Exception {
        ParentTabId[] ids = new ParentTabId[3];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            main.content().split(Side.RIGHT);
            ids[1] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent window = main.content();
            Tab moved = entryOf(window, ids[0]);
            terminals.windowActivated(main.id());
            facts.clear();
            window.moveTabInto(ids[0], ids[1]);
            assertThat(window.activeParentTab()).isEqualTo(ids[1]);
            assertThat(window.selectedTab()).isSameAs(moved);
            assertThat(facts).as("its active item selected, then the active pane").containsExactly(
                new TerminalEvent.TabSelected(main.id(), moved.id().value()),
                new TerminalEvent.ActivePaneChanged(Optional.of(paneOf(moved))));
            ids[2] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent window = main.content();
            Tab shown = window.selectedTab();
            facts.clear();
            window.moveTabInto(ids[2], ids[1]);
            assertThat(window.activeParentTab()).isEqualTo(ids[1]);
            assertThat(window.selectedTab()).isSameAs(shown);
            assertThat(facts).as("the item that showed stays selected and active").isEmpty();
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 13 and 16, with terminals: the tab that shows, of two
     * terminal panes, merged into another window's tab is announced move by move in the tab's order, each item selected
     * where it arrives; the window it left shows its other tab as the first item leaves, that tab's item selected and
     * its pane the active pane; once the target window comes to the front, the moved tab's active item is. Nothing
     * opens or closes.
     */
    @Test void aTabOfTerminalsMergedIntoAnotherWindowIsAnnouncedMoveByMove() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            ids[1] = secondTab(main);
            main.content().split(Side.RIGHT);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            List<Tab> tabs = from.parentTabs().view(ids[1]).orElseThrow().tabs();
            Tab shown = entryOf(from, ids[1]), stays = entryOf(from, ids[0]);
            terminals.windowActivated(main.id());
            facts.clear();
            assertThat(manager.moveTabInto(from, ids[1], to, activeGroup(child), Side.RIGHT)).isTrue();
            assertThat(tabs).as("the split's new pane is the tab's active item, and its last").hasSize(2).endsWith(shown);
            assertThat(facts).as("move by move; the window it left shows its other tab as the first item leaves").containsExactly(
                new TerminalEvent.TabMoved(tabs.get(0).id().value(), main.id(), child.id()),
                new TerminalEvent.TabSelected(child.id(), tabs.get(0).id().value()),
                new TerminalEvent.TabSelected(main.id(), stays.id().value()),
                new TerminalEvent.ActivePaneChanged(Optional.of(paneOf(stays))),
                new TerminalEvent.TabMoved(shown.id().value(), main.id(), child.id()),
                new TerminalEvent.TabSelected(child.id(), shown.id().value()));
            assertThat(to.selectedTab()).isSameAs(shown);
            facts.clear();
            terminals.windowActivated(child.id());
            assertThat(facts).as("the target window at the front: the moved tab's active item").containsExactly(
                new TerminalEvent.WindowActivated(child.id()), new TerminalEvent.ActivePaneChanged(Optional.of(paneOf(shown))));
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 13: a full window's only tab merged into another window's tab
     * moves item by item, and its window closes after the last move has reported itself; then the moved tab's active
     * item, its first here, is selected where it landed.
     */
    @Test void aFullWindowsOnlyTabMergedAwayClosesItsWindowAfterItsMoves() throws Exception {
        edt(() -> child.content().split(Side.DOWN));
        launch();
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            ParentTabId only = from.activeParentTab();
            List<Tab> tabs = from.workspace().tabs();
            from.selectTab(tabs.get(0));
            Tab there = to.selectedTab();
            facts.clear();
            assertThat(manager.moveTabInto(from, only, to, there.group().orElseThrow(), Side.LEFT)).isTrue();
            assertThat(to.workspace().tabs()).as("on its left").containsExactly(tabs.get(0), tabs.get(1), there);
            assertThat(child.closed()).as("its last tab moved out").isTrue();
            assertThat(others()).isEmpty();
            int first = facts.indexOf(new TerminalEvent.TabMoved(tabs.get(0).id().value(), child.id(), main.id()));
            int last = facts.indexOf(new TerminalEvent.TabMoved(tabs.get(1).id().value(), child.id(), main.id()));
            int closed = facts.indexOf(new TerminalEvent.WindowClosed(child.id()));
            assertThat(first).as("TAB_MOVED per item, in the tab's order").isNotNegative().isLessThan(last);
            assertThat(closed).as("then WINDOW_CLOSED").isGreaterThan(last);
            Tab active = tabs.get(0);
            assertThat(to.selectedTab()).as("the moved tab's active item").isSameAs(active);
            assertThat(facts.lastIndexOf(new TerminalEvent.TabSelected(main.id(), active.id().value())))
                .as("selected after WINDOW_CLOSED").isGreaterThan(closed);
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 13; full windows spec 5.3: a window's only tab merged into
     * another window's closes the window, and the moved panes keep their ids in the other window.
     */
    @Test void aWindowsOnlyTabMergedAwayClosesTheWindow() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            ParentTabId only = from.activeParentTab();
            GroupId moved = from.workspace().model().activeGroup();
            Tab there = to.selectedTab();
            assertThat(manager.moveTabInto(from, only, to, there.group().orElseThrow(), Side.RIGHT)).isTrue();
            assertThat(main.closed()).as("the window it emptied closes").isTrue();
            assertThat(groupIds(to)).as("the moved pane keeps its id there").contains(moved);
        });
    }

    /**
     * Workspace plan 9a's Task 6 review: a window's empty workspace has a pane id but no pane to merge beside,
     * so the window manager refuses a tab merged into it, moving nothing; a drop there takes the tab in its place
     * instead.
     */
    @Test void aTabMergedIntoAnEmptyWorkspaceIsRefused() throws Exception {
        edt(() -> {
            WindowContent empty = main.content(), from = child.content();
            empty.closeTab(empty.selectedTab());
            assertThat(empty.parentTabs().isEmpty()).as("the window's empty workspace").isTrue();
            ParentTabId only = from.activeParentTab();
            List<Tab> tabs = from.workspace().tabs();
            facts.clear();
            assertThat(manager.moveTabInto(from, only, empty, empty.workspace().model().activeGroup(), Side.RIGHT))
                .as("no pane to go beside").isFalse();
            assertThat(empty.parentTabs().isEmpty()).isTrue();
            assertThat(from.workspace().tabs()).as("nothing moved").containsExactlyElementsOf(tabs);
            assertThat(moves()).isEmpty();
        });
    }

    /** Pane flexibility spec 5 and Review Focus 2: a tab merged into another window's maximized tab ends the maximize. */
    @Test void aTabMergedIntoAnotherWindowsMaximizedTabEndsItsMaximize() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> {
            child.content().split(Side.RIGHT);
            ids[0] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            to.toggleMaximize();
            relayout(frameOf(child));
            assertThat(to.workspace().model().maximized()).isPresent();
            Tab there = to.selectedTab(), other = to.workspace().tabs().getFirst(), moved = entryOf(from, ids[0]);
            Point right = inside(pane(to, there.group().orElseThrow()), 0.9, 0.5);
            Component source = start(from, moved, right);
            assertThat(manager.tabDrag().target()).as("only the maximized pane shows")
                .contains(new TabDrag.Target.Into(to, there.group().orElseThrow(), Side.RIGHT));
            mouse(source, MouseEvent.MOUSE_RELEASED, right);
            assertThat(to.workspace().model().maximized()).as("the maximize ends").isEmpty();
            assertThat(to.workspace().tabs()).as("beside the pane that was maximized, on its right").containsExactly(other, there, moved);
            assertThat(((Split) to.workspace().model().root()).children()).extracting(Child::weight)
                .as("in its half").containsExactly(0.5, 0.25, 0.25);
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 7c Rulings 8: a tab holding a pinned item, as a dialog open in one of its
     * panes pins it, merges into no other window's tab: its drag takes no pane, and the window manager moves nothing.
     */
    @Test void aTabHoldingAPinnedItemMergesIntoNoOtherWindowsTab() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> ids[0] = secondTab(main));
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = entryOf(from, ids[0]), there = to.selectedTab();
            var pin = moving.pin();
            try {
                Point over = inside(pane(to, there.group().orElseThrow()), 0.9, 0.5);
                Component source = start(from, moving, over);
                assertThat(manager.tabDrag().active()).isTrue();
                assertThat(manager.tabDrag().target()).as("its drag takes no pane").isEmpty();
                mouse(source, MouseEvent.MOUSE_RELEASED, over);
                assertThat(manager.moveTabInto(from, ids[0], to, there.group().orElseThrow(), Side.RIGHT)).isFalse();
                assertThat(to.workspace().tabs()).containsExactly(there);
                assertThat(moves()).isEmpty();
            } finally {
                pin.close();
            }
        });
    }

    /** Review Focus 1: a window that closed takes no merge, and a tab never merges into its own window this way. */
    @Test void aWindowThatClosedOrTheTabsOwnTakesNoMerge() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> ids[0] = secondTab(main));
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            GroupId there = activeGroup(child);
            assertThat(manager.moveTabInto(from, ids[0], from, from.workspace().model().activeGroup(), Side.RIGHT))
                .as("its own window: Move Tab Into's").isFalse();
            child.requestClose();
            assertThat(child.closed()).isTrue();
            assertThat(manager.moveTabInto(from, ids[0], to, there, Side.RIGHT)).as("a closed window").isFalse();
            assertThat(tabIds(from)).hasSize(2);
            assertThat(moves()).isEmpty();
        });
    }

    /**
     * Workspace plan 9a Rulings 13: when an item's move fails mid-merge, the items that arrived keep the panes they landed
     * in, to the right of the pane, the one that failed stays in its tab, no pane is in two windows, and the failure
     * propagates.
     */
    @Test void aMergeWhoseItemFailsToMoveLeavesItInItsTab() throws Exception {
        ParentTabId[] ids = new ParentTabId[1];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            main.content().split(Side.RIGHT);
            secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            List<Tab> tabs = from.parentTabs().view(ids[0]).orElseThrow().tabs();
            Tab goes = tabs.get(0), stays = tabs.get(1), there = to.selectedTab();
            var refused = new IllegalStateException("the tab will not go");
            var refusing = stays.onChanged(() -> { if (child.id().equals(stays.window())) throw refused; });
            try {
                assertThatThrownBy(() -> manager.moveTabInto(from, ids[0], to, there.group().orElseThrow(), Side.RIGHT)).isSameAs(refused);
            } finally {
                refusing.close();
            }
            assertThat(to.workspace().tabs()).as("the item that arrived, to the right of the pane").containsExactly(there, goes);
            assertThat(from.parentTabs().view(ids[0]).orElseThrow().tabs()).as("the one that failed, in its tab").containsExactly(stays);
            assertThat(groupIds(from)).as("no pane is in two windows").doesNotContainAnyElementsOf(groupIds(to));
        });
    }
}
