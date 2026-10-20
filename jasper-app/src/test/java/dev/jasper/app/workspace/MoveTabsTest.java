package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.MovesFixture.moved;
import static dev.jasper.app.workspace.MovesFixture.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.TerminalEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tabs and panes spec 6 and 13: Move Tab to New Window, Move to Main Window and an item's move into a window of its own;
 * what moves whole, and what stays.
 */
class MoveTabsTest {
    private MovesFixture windows;

    @BeforeEach void windows() throws Exception { edt(() -> windows = new MovesFixture()); }

    @AfterEach void cleanup() throws Exception {
        edt(() -> windows.close());
        closeOwners();
    }

    /** Spec 6 and workspace plan 7b Rulings 10: a tab moves whole into a full window, with its id and layout. */
    @Test void moveTabToNewWindowMovesItWholeToAFullWindow() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> { main[0] = windows.main(); main[0].content().split(Side.RIGHT); });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            ParentTabId first = content.activeParentTab();
            content.workspace().resizeSplit(List.of(), List.of(0.3, 0.7));
            Layout before = content.workspace().model();
            List<Tab> moving = content.tabs();
            var sessions = moving.stream().map(MovesFixture::session).toList();
            Tab active = content.selectedTab();
            content.newTab(HOME);
            ParentTabId second = content.activeParentTab();
            int[] focused = {0};
            active.focusTarget(() -> focused[0]++);
            windows.facts.clear();
            ChildWindow full = windows.manager.moveTabToNewWindow(content, first).orElseThrow();
            assertThat(full.content().windowLayout()).isEqualTo(ParentTabs.of(new ParentTab(first, before)));
            assertThat(full.content().selectedTab()).as("the tab's active item, shown").isSameAs(active);
            assertThat(focused[0]).as("the item has the focus in its new window").isPositive();
            assertThat(content.parentTabCount()).isEqualTo(1);
            assertThat(content.activeParentTab()).isEqualTo(second);
            assertThat(windows.tabFacts()).isEqualTo(moved(moving, main[0].id(), full.id()));
            for (int i = 0; i < moving.size(); i++) assertThat(session(moving.get(i))).isSameAs(sessions.get(i));
            assertThat(windows.manager.moveTabToNewWindow(full.content(), first)).as("a full window's only tab stays").isEmpty();
        });
        windows.launch();
    }

    /** This plan's Rulings 6 (Spec issues 1): the main window's only tab stays too, and no window opens for it. */
    @Test void theMainWindowsOnlyTabStays() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.split(Side.RIGHT);
            windows.facts.clear();
            assertThat(windows.manager.moveTabToNewWindow(content, content.activeParentTab())).isEmpty();
            assertThat(windows.facts).as("no window opened and closed again").noneMatch(TerminalEvent.WindowOpened.class::isInstance);
            assertThat(windows.manager.children()).isEmpty();
            assertThat(content.tabCount()).isEqualTo(2);
        });
        windows.launch();
    }

    /** Spec 6 and workspace plan 7b Rulings 12: a full window's tabs join the main window's strip after its shown one. */
    @Test void moveToMainWindowJoinsTheTabsAfterTheShownOne() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            ParentTabId m1 = main.activeParentTab();
            main.newTab(HOME);
            ParentTabId m2 = main.activeParentTab();
            main.selectParentTab(m1);
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            var content = full.content();
            ParentTabId p1 = content.activeParentTab();
            content.newTab(HOME);
            content.split(Side.RIGHT);
            ParentTabId p2 = content.activeParentTab();
            var layouts = content.windowLayout().parentTabs();
            int tabs = content.tabCount();
            Tab active = content.selectedTab();
            int[] focused = {0};
            active.focusTarget(() -> focused[0]++);
            windows.facts.clear();
            assertThat(windows.manager.moveToMainWindow(content)).containsSame(windows.main());
            assertThat(full.closed()).as("the full window closes with its last tab").isTrue();
            var parents = main.windowLayout();
            assertThat(parents.parentTabs()).extracting(ParentTab::id).containsExactly(m1, p1, p2, m2);
            assertThat(parents.parentTabs().subList(1, 3)).as("each with its layout").containsExactlyElementsOf(layouts);
            assertThat(main.activeParentTab()).as("the tab it showed").isEqualTo(p2);
            assertThat(main.selectedTab()).as("its active item, shown").isSameAs(active);
            assertThat(focused[0]).as("the item has the focus in the main window").isPositive();
            assertThat(windows.tabFacts()).hasSize(tabs).allMatch(TerminalEvent.TabMoved.class::isInstance);
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 12: the main window's empty workspace gives way to the first tab. */
    @Test void moveToMainWindowFillsAnEmptyMainWorkspace() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.closeTab(main.selectedTab());
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            ParentTabId p1 = full.content().activeParentTab();
            windows.manager.moveToMainWindow(full.content());
            assertThat(main.windowLayout().parentTabs()).extracting(ParentTab::id).containsExactly(p1);
            assertThat(main.tabCount()).isEqualTo(1);
            assertThat(full.closed()).as("the full window closes with its last tab").isTrue();
            assertThat(main.activeParentTab()).as("the tab it showed").isEqualTo(p1);
        });
        windows.launch();
    }

    /** Review Focus 1 of plan 7b: a pinned item keeps its tab whole, and nothing moves. */
    @Test void aPinnedItemKeepsItsTabWhole() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            content.split(Side.RIGHT);
            content.newTab(HOME);
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            Subscription held = content.tabs().getFirst().pin();
            windows.facts.clear();
            assertThat(windows.manager.moveTabToNewWindow(content, first)).isEmpty();
            assertThat(windows.manager.moveParentTab(content, first, full.content(), 0)).as("nor onto another window").isFalse();
            assertThat(windows.manager.children()).containsExactly(full);
            assertThat(windows.tabFacts()).isEmpty();
            held.close();
            assertThat(windows.manager.moveTabToNewWindow(content, first)).isPresent();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 10: a tab never moves whole within its own window. */
    @Test void aTabMovesNotWithinItsOwnWindow() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            content.newTab(HOME);
            windows.facts.clear();
            assertThat(windows.manager.moveParentTab(content, first, content, 0)).isFalse();
            assertThat(windows.tabFacts()).isEmpty();
            assertThat(content.parentTabCount()).isEqualTo(2);
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 10: a tab moving whole keeps its layout, its maximize included. */
    @Test void aMaximizedTabMovesStillMaximized() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            content.split(Side.RIGHT);
            content.toggleMaximize();
            Layout before = content.workspace().model();
            GroupId maximized = before.maximized().orElseThrow();
            content.newTab(HOME);
            ChildWindow full = windows.manager.moveTabToNewWindow(content, first).orElseThrow();
            assertThat(full.content().workspace().model().maximized()).as("on the same pane").contains(maximized);
            assertThat(full.content().workspace().model()).isEqualTo(before);
        });
        windows.launch();
    }

    /** The empty workspace a tab moving whole leaves behind takes the window's theme and glass, as a new one does. */
    @Test void theRenewedEmptyWorkspaceTakesTheWindowsThemeAndGlass() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.applyBackgroundOpacity(0.5);
            ParentTabId only = content.activeParentTab();
            content.split(Side.RIGHT);
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            assertThat(windows.manager.moveParentTab(content, only, full.content(), 1)).isTrue();
            WorkspaceView renewed = content.workspace();
            assertThat(renewed.id()).as("the renewed workspace").isNotEqualTo(only);
            assertThat(renewed.isOpaque()).as("under glass it paints nothing behind its panes").isFalse();
            assertThat(renewed.getBackground()).as("the theme's surface").isEqualTo(content.theme().surface());
            content.split(Side.RIGHT);
            content.split(Side.RIGHT);
            SplitContainer split = renewed.split(List.of());
            assertThat(split).as("a split it builds later").isNotNull();
            assertThat(split.isOpaque()).isFalse();
            assertThat(split.getBackground()).isEqualTo(content.theme().surface());
        });
        windows.launch();
    }

    /**
     * Parent tabs spec 4 (7b's final review): a listener pins the tab's second item as its first moves. What arrived takes
     * a new tab id; what stayed keeps its ids; and Move Tab to New Window gives the full window holding what arrived.
     */
    @Test void aTabThatMovesInPartLeavesNoIdInTwoWindows() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.split(Side.RIGHT);
            ParentTabId first = content.activeParentTab();
            List<Tab> moving = content.workspace().tabs();
            GroupId stays = moving.get(1).group().orElseThrow();
            content.newTab(HOME);
            Subscription[] pin = MovesFixture.pinWhenItLeaves(moving.get(0), moving.get(1));
            ChildWindow full = windows.manager.moveTabToNewWindow(content, first).orElseThrow();
            assertThat(full.content().tabs()).as("what arrived").containsExactly(moving.get(0));
            assertThat(full.content().activeParentTab()).as("under a new id").isNotEqualTo(first);
            assertThat(content.parentTabOf(moving.get(1))).as("what stayed keeps its tab").contains(first);
            assertThat(content.parentTabs().view(first).orElseThrow().model().groups()).extracting(Group::id).containsExactly(stays);
            windows.assertNoIdInTwoWindows(content.tabs());
            windows.assertNoIdInTwoWindows(moving);
            pin[0].close();
        });
        windows.launch();
    }

    /** As above, the second item's move failing: the failure propagates, and what arrived stays in the full window. */
    @Test void aTabWhoseSecondItemFailsToMoveLeavesNoIdInTwoWindows() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.split(Side.RIGHT);
            ParentTabId first = content.activeParentTab();
            List<Tab> moving = content.workspace().tabs();
            content.newTab(HOME);
            var refusal = MovesFixture.refuseItsMove(moving.get(1));
            assertThatThrownBy(() -> windows.manager.moveTabToNewWindow(content, first)).isSameAs(refusal);
            assertThat(windows.manager.children()).singleElement().satisfies(full -> {
                assertThat(full.content().tabs()).containsExactly(moving.get(0));
                assertThat(full.content().activeParentTab()).isNotEqualTo(first);
            });
            assertThat(content.parentTabOf(moving.get(1))).contains(first);
            windows.assertNoIdInTwoWindows(moving);
        });
        windows.launch();
    }

    /**
     * Parent tabs spec 4 (7b's final review): Move to Main Window with the shown tab moving in part. Its part that arrived
     * joins the strip under a new id, shows, and the next tab follows it; the full window keeps the rest.
     */
    @Test void tabsThatMoveToTheMainWindowInPartLeaveNoIdInTwoWindows() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            ParentTabId m1 = main.activeParentTab();
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            var content = full.content();
            content.split(Side.RIGHT);
            ParentTabId p1 = content.activeParentTab();
            List<Tab> split = content.workspace().tabs();
            content.newTab(HOME);
            ParentTabId p2 = content.activeParentTab();
            content.selectParentTab(p1);
            Subscription[] pin = MovesFixture.pinWhenItLeaves(split.get(0), split.get(1));
            assertThat(windows.manager.moveToMainWindow(content)).containsSame(windows.main());
            ParentTabId arrived = main.parentTabOf(split.get(0)).orElseThrow();
            assertThat(arrived).as("a new id").isNotEqualTo(p1);
            assertThat(main.windowLayout().parentTabs()).extracting(ParentTab::id).containsExactly(m1, arrived, p2);
            assertThat(main.activeParentTab()).as("what arrived of the tab the full window showed").isEqualTo(arrived);
            assertThat(full.closed()).isFalse();
            assertThat(content.tabs()).containsExactly(split.get(1));
            windows.assertNoIdInTwoWindows(main.tabs());
            windows.assertNoIdInTwoWindows(split);
            pin[0].close();
        });
        windows.launch();
    }

    /** A pane the planned layout lacks, one that opened meanwhile, stays in the tab; the plan still lands. */
    @Test void rearrangingKeepsAPaneThePlanLacks() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.split(Side.RIGHT);
            WorkspaceView workspace = content.workspace();
            Tab planned = content.selectedTab();
            Layout plan = workspace.model().resize(List.of(), List.of(0.3, 0.7));
            content.split(Side.DOWN);
            Tab extra = content.selectedTab();
            workspace.rearrange(plan);
            Layout layout = workspace.model();
            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split -> assertThat(split.children())
                .extracting(Child::weight).as("the plan landed, its active pane split for the item it lacks").satisfiesExactly(
                    weight -> assertThat(weight).isCloseTo(0.3, org.assertj.core.api.Assertions.within(1e-9)),
                    weight -> assertThat(weight).isCloseTo(0.35, org.assertj.core.api.Assertions.within(1e-9)),
                    weight -> assertThat(weight).isCloseTo(0.35, org.assertj.core.api.Assertions.within(1e-9))));
            assertThat(layout.groupOf(extra.id())).as("in a pane of its own, beside the plan's active one").isPresent()
                .isNotEqualTo(layout.groupOf(planned.id()));
            assertThat(layout.activeGroup()).isEqualTo(plan.activeGroup());
            assertThat(content.selectedTab()).as("the plan's active item").isSameAs(planned);
            assertThat(content.tabs()).hasSize(3).contains(extra);
        });
        windows.launch();
    }

    /** Spec 13 and Review Focus 5 of plan 7b: an item leaves for a full window of its own; one TAB_MOVED; focus follows. */
    @Test void moveToNewWindowTakesOneItemToAFullWindowOfItsOwn() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> { main[0] = windows.main(); main[0].content().split(Side.RIGHT); });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            Tab tab = content.selectedTab();
            var running = session(tab);
            int[] focused = {0};
            tab.focusTarget(() -> focused[0]++);
            windows.facts.clear();
            ChildWindow child = windows.manager.moveToNewWindow(tab).orElseThrow();
            assertThat(child.content().parentTabCount()).as("a full window, its tab holding the item").isEqualTo(1);
            assertThat(child.content().tabs()).containsExactly(tab);
            assertThat(windows.tabFacts()).as("one move; no tab opened or closed").isEqualTo(moved(List.of(tab), main[0].id(), child.id()));
            assertThat(session(tab)).isSameAs(running);
            assertThat(running.exitFuture()).isNotDone();
            assertThat(focused[0]).as("the item has the focus in its window").isPositive();
            assertThat(content.tabCount()).isEqualTo(1);
            assertThat(content.workspace().model().groups()).as("its pane went with it").hasSize(1);
        });
    }

    /** Workspace plan 7b Rulings 8: a child window's only item stays, as that window already is its own. */
    @Test void aChildWindowsOnlyItemStays() throws Exception {
        edt(() -> {
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            assertThat(windows.manager.moveToNewWindow(full.content().selectedTab())).isEmpty();
            assertThat(windows.manager.children()).containsExactly(full);
        });
        windows.launch();
    }

    /** The main window's only item may leave, leaving its empty workspace. */
    @Test void theMainWindowsOnlyItemMovesAndLeavesItsEmptyWorkspace() throws Exception {
        edt(() -> {
            MainWindow main = windows.main();
            var content = main.content();
            assertThat(windows.manager.moveToNewWindow(content.selectedTab())).isPresent();
            assertThat(main.closed()).isFalse();
            assertThat(content.tabCount()).isZero();
            assertThat(content.workspace().emptyWorkspace()).as("its empty workspace shows").isNotNull();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 7: an item whose close waits for its guard stays, and no window opens for it even briefly. */
    @Test void anItemBeingClosedOrPinnedOpensNoWindow() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            Tab notes = windows.model.tabs().open("dev.example.editor", "notes", "notes.txt", content.id(), new JPanel());
            Subscription pin = content.tabs().getFirst().pin();
            windows.facts.clear();
            assertThat(windows.manager.moveToNewWindow(content.tabs().getFirst())).as("a pinned item").isEmpty();
            pin.close();
            var answer = new CompletableFuture<Boolean>();
            windows.model.tabs().guard("dev.example.editor", (tabs, batch) -> answer);
            var closing = notes.requestClose();
            assertThat(closing).as("the guard has not answered").isNotDone();
            assertThat(windows.manager.moveToNewWindow(notes)).isEmpty();
            assertThat(windows.facts).as("no window opened and closed again").noneMatch(TerminalEvent.WindowOpened.class::isInstance);
            assertThat(windows.manager.children()).isEmpty();
            assertThat(content.tabs()).contains(notes);
            answer.complete(false);
            assertThat(closing).isCompletedWithValue(false);
            assertThat(windows.manager.moveToNewWindow(notes)).as("once the close is refused, it may go").isPresent();
        });
        windows.launch();
    }
}
