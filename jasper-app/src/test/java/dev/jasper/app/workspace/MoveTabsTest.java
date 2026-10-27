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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tabs and panes spec 6 and 13: Move Tab to New Window, Move Tab to Window and an item's move into a window of its own;
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
        JasperWindow[] main = new JasperWindow[1];
        edt(() -> { main[0] = windows.first(); main[0].content().split(Side.RIGHT); });
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
            var activity = new ArrayList<WorkspaceActivity.Event>();
            content.activity(activity::add);
            var oldActivation = activity.stream().filter(WorkspaceActivity.Presentation.class::isInstance)
                .map(WorkspaceActivity.Presentation.class::cast)
                .filter(value -> value.id().equals(((TerminalContent) active.content()).pane().id()))
                .findFirst().orElseThrow().activate();
            activity.clear();
            JasperWindow full = windows.manager.moveTabToNewWindow(content, first).orElseThrow();
            var arrived = new ArrayList<WorkspaceActivity.Presentation>();
            full.content().activity(event -> {
                if (event instanceof WorkspaceActivity.Presentation value) arrived.add(value);
            });
            assertThat(arrived).hasSize(2).allSatisfy(value -> {
                assertThat(value.groupId()).isEqualTo(first.value());
                assertThat(value.groupTitle()).isEqualTo(full.content().tabTitle(active));
            });
            assertThat(activity).noneMatch(event -> event instanceof WorkspaceActivity.PaneState state
                && state.state() == WorkspaceActivity.State.CLOSED);
            oldActivation.run();
            assertThat(full.content().selectedTab()).isSameAs(active);
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

    /** This plan's Rulings 6 (Spec issues 1): the first window's only tab stays too, and no window opens for it. */
    @Test void theFirstWindowsOnlyTabStays() throws Exception {
        edt(() -> {
            var content = windows.first().content();
            content.split(Side.RIGHT);
            windows.facts.clear();
            assertThat(windows.manager.moveTabToNewWindow(content, content.activeParentTab())).isEmpty();
            assertThat(windows.facts).as("no window opened and closed again").noneMatch(TerminalEvent.WindowOpened.class::isInstance);
            assertThat(windows.others()).isEmpty();
            assertThat(content.tabCount()).isEqualTo(2);
        });
        windows.launch();
    }

    /** Spec 6: a full window's tab moves whole to a new full window too, and its window keeps its other tab. */
    @Test void moveTabToNewWindowFromAFullWindowLeavesItsOtherTab() throws Exception {
        JasperWindow[] full = new JasperWindow[1];
        edt(() -> { full[0] = windows.manager.openWindow(null).orElseThrow(); full[0].content().split(Side.RIGHT); });
        windows.launch();
        edt(() -> {
            var content = full[0].content();
            ParentTabId first = content.activeParentTab();
            content.workspace().resizeSplit(List.of(), List.of(0.3, 0.7));
            Layout before = content.workspace().model();
            List<Tab> moving = content.tabs();
            Tab active = content.selectedTab();
            content.newTab(HOME);
            ParentTabId second = content.activeParentTab();
            int[] focused = {0};
            active.focusTarget(() -> focused[0]++);
            windows.facts.clear();
            JasperWindow next = windows.manager.moveTabToNewWindow(content, first).orElseThrow();
            assertThat(next).isNotSameAs(full[0]);
            assertThat(full[0].closed()).as("the full window it left stays open").isFalse();
            assertThat(content.parentTabCount()).as("with its other tab").isEqualTo(1);
            assertThat(content.activeParentTab()).isEqualTo(second);
            assertThat(next.content().windowLayout()).as("the tab whole, with its layout")
                .isEqualTo(ParentTabs.of(new ParentTab(first, before)));
            assertThat(next.content().selectedTab()).as("the tab's active item, shown").isSameAs(active);
            assertThat(focused[0]).as("the item has the focus in its new window").isPositive();
            assertThat(windows.tabFacts()).isEqualTo(moved(moving, full[0].id(), next.id()));
        });
        windows.launch();
    }

    /** Full windows spec 5.4: Move Tab to Window puts the tab, whole, after the other window's shown one, and shows it. */
    @Test void moveTabToWindowJoinsTheTabAfterTheShownOne() throws Exception {
        edt(() -> {
            var first = windows.first().content();
            ParentTabId m1 = first.activeParentTab();
            first.newTab(HOME);
            ParentTabId m2 = first.activeParentTab();
            first.selectParentTab(m1);
            JasperWindow other = windows.manager.openWindow(null).orElseThrow();
            var content = other.content();
            ParentTabId p1 = content.activeParentTab();
            content.newTab(HOME);
            content.split(Side.RIGHT);
            ParentTabId p2 = content.activeParentTab();
            ParentTab layout = content.windowLayout().parentTabs().get(1);
            int tabs = content.workspace().tabs().size();
            Tab active = content.selectedTab();
            int[] focused = {0};
            active.focusTarget(() -> focused[0]++);
            windows.facts.clear();
            assertThat(windows.manager.moveTabToWindow(content, p2, first)).isTrue();
            assertThat(other.closed()).as("its other tab keeps the window open").isFalse();
            assertThat(first.windowLayout().parentTabs()).extracting(ParentTab::id).containsExactly(m1, p2, m2);
            assertThat(first.windowLayout().parentTabs().get(1)).as("with its layout").isEqualTo(layout);
            assertThat(first.activeParentTab()).as("shown").isEqualTo(p2);
            assertThat(first.selectedTab()).as("its active item, shown").isSameAs(active);
            assertThat(focused[0]).as("the item has the focus in the other window").isPositive();
            assertThat(windows.tabFacts()).hasSize(tabs).allMatch(TerminalEvent.TabMoved.class::isInstance);
            assertThat(windows.manager.moveTabToWindow(content, p1, first)).isTrue();
            assertThat(other.closed()).as("the window closes with its last tab").isTrue();
            assertThat(first.windowLayout().parentTabs()).extracting(ParentTab::id).containsExactly(m1, p2, p1, m2);
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 12: a window's empty workspace gives way to the tab. */
    @Test void moveTabToWindowFillsAnEmptyWorkspace() throws Exception {
        edt(() -> {
            var first = windows.first().content();
            first.closeTab(first.selectedTab());
            JasperWindow other = windows.manager.openWindow(null).orElseThrow();
            ParentTabId p1 = other.content().activeParentTab();
            assertThat(windows.manager.moveTabToWindow(other.content(), p1, first)).isTrue();
            assertThat(first.windowLayout().parentTabs()).extracting(ParentTab::id).containsExactly(p1);
            assertThat(first.tabCount()).isEqualTo(1);
            assertThat(other.closed()).as("the window closes with its last tab").isTrue();
            assertThat(first.activeParentTab()).as("the tab it showed").isEqualTo(p1);
        });
        windows.launch();
    }

    /** Review Focus 1 of plan 7b: a pinned item keeps its tab whole, and nothing moves. */
    @Test void aPinnedItemKeepsItsTabWhole() throws Exception {
        edt(() -> {
            var content = windows.first().content();
            ParentTabId first = content.activeParentTab();
            content.split(Side.RIGHT);
            content.newTab(HOME);
            JasperWindow full = windows.manager.openWindow(null).orElseThrow();
            Subscription held = content.tabs().getFirst().pin();
            windows.facts.clear();
            assertThat(windows.manager.moveTabToNewWindow(content, first)).isEmpty();
            assertThat(windows.manager.moveParentTab(content, first, full.content(), 0)).as("nor onto another window").isFalse();
            assertThat(windows.others()).containsExactly(full);
            assertThat(windows.tabFacts()).isEmpty();
            held.close();
            assertThat(windows.manager.moveTabToNewWindow(content, first)).isPresent();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 10: a tab never moves whole within its own window. */
    @Test void aTabMovesNotWithinItsOwnWindow() throws Exception {
        edt(() -> {
            var content = windows.first().content();
            ParentTabId first = content.activeParentTab();
            content.newTab(HOME);
            windows.facts.clear();
            assertThat(windows.manager.moveParentTab(content, first, content, 0)).isFalse();
            assertThat(windows.tabFacts()).isEmpty();
            assertThat(content.parentTabCount()).isEqualTo(2);
        });
        windows.launch();
    }

    /**
     * Spec 5 and workspace plan 8a Rulings 20: a split tab moving whole onto a window whose strip shows takes its place at
     * once, though the strip is laid out and its motion on; a new tab there still opens with the motion.
     */
    @Test void aSplitTabArrivingInAShownStripTakesItsPlaceAtOnce() throws Exception {
        edt(() -> {
            var content = windows.first().content();
            ParentTabId first = content.activeParentTab();
            content.split(Side.RIGHT);
            content.newTab(HOME);
            var target = windows.manager.openWindow(null).orElseThrow().content();
            target.newTab(HOME);
            target.setSize(1000, 700);
            WindowTabsTest.layoutTree(target);
            var strip = target.windowTabs();
            assertThat(target.animations().enabled() && target.lookAnimates()).as("the motion is on").isTrue();
            assertThat(strip.isVisible()).as("two tabs: the strip shows").isTrue();
            assertThat(strip.getWidth()).as("and is laid out").isPositive();
            assertThat(strip.motionRunning()).isFalse();
            assertThat(windows.manager.moveParentTab(content, first, target, 2)).isTrue();
            assertThat(target.parentTabCount()).isEqualTo(3);
            assertThat(target.parentTabs().view(first).orElseThrow().tabs()).as("the tab whole").hasSize(2);
            assertThat(strip.motionRunning()).as("a tab that arrives by a move").isFalse();
            target.newTab(HOME);
            assertThat(strip.motionRunning()).as("a new tab opens with the motion").isTrue();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 10: a tab moving whole keeps its layout, its maximize included. */
    @Test void aMaximizedTabMovesStillMaximized() throws Exception {
        edt(() -> {
            var content = windows.first().content();
            ParentTabId first = content.activeParentTab();
            content.split(Side.RIGHT);
            content.toggleMaximize();
            Layout before = content.workspace().model();
            GroupId maximized = before.maximized().orElseThrow();
            content.newTab(HOME);
            JasperWindow full = windows.manager.moveTabToNewWindow(content, first).orElseThrow();
            assertThat(full.content().workspace().model().maximized()).as("on the same pane").contains(maximized);
            assertThat(full.content().workspace().model()).isEqualTo(before);
        });
        windows.launch();
    }

    /**
     * Parent tabs spec 4 (7b's final review): a listener pins the tab's second item as its first moves. What arrived takes
     * a new tab id; what stayed keeps its ids; and Move Tab to New Window gives the full window holding what arrived.
     */
    @Test void aTabThatMovesInPartLeavesNoIdInTwoWindows() throws Exception {
        edt(() -> {
            var content = windows.first().content();
            content.split(Side.RIGHT);
            ParentTabId first = content.activeParentTab();
            List<Tab> moving = content.workspace().tabs();
            GroupId stays = moving.get(1).group().orElseThrow();
            content.newTab(HOME);
            Subscription[] pin = MovesFixture.pinWhenItLeaves(moving.get(0), moving.get(1));
            JasperWindow full = windows.manager.moveTabToNewWindow(content, first).orElseThrow();
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
            var content = windows.first().content();
            content.split(Side.RIGHT);
            ParentTabId first = content.activeParentTab();
            List<Tab> moving = content.workspace().tabs();
            content.newTab(HOME);
            var refusal = MovesFixture.refuseItsMove(moving.get(1));
            assertThatThrownBy(() -> windows.manager.moveTabToNewWindow(content, first)).isSameAs(refusal);
            assertThat(windows.others()).singleElement().satisfies(full -> {
                assertThat(full.content().tabs()).containsExactly(moving.get(0));
                assertThat(full.content().activeParentTab()).isNotEqualTo(first);
            });
            assertThat(content.parentTabOf(moving.get(1))).contains(first);
            windows.assertNoIdInTwoWindows(moving);
        });
        windows.launch();
    }

    /**
     * Parent tabs spec 4 (7b's final review): Move Tab to Window with the tab moving in part. Its part that arrived joins
     * the strip under a new id and shows; the window it left keeps the rest.
     */
    @Test void aTabThatMovesToAnotherWindowInPartLeavesNoIdInTwoWindows() throws Exception {
        edt(() -> {
            var main = windows.first().content();
            ParentTabId m1 = main.activeParentTab();
            JasperWindow full = windows.manager.openWindow(null).orElseThrow();
            var content = full.content();
            content.split(Side.RIGHT);
            ParentTabId p1 = content.activeParentTab();
            List<Tab> split = content.workspace().tabs();
            content.newTab(HOME);
            ParentTabId p2 = content.activeParentTab();
            content.selectParentTab(p1);
            Subscription[] pin = MovesFixture.pinWhenItLeaves(split.get(0), split.get(1));
            assertThat(windows.manager.moveTabToWindow(content, p1, main)).isTrue();
            ParentTabId arrived = main.parentTabOf(split.get(0)).orElseThrow();
            assertThat(arrived).as("a new id").isNotEqualTo(p1);
            assertThat(main.windowLayout().parentTabs()).extracting(ParentTab::id).containsExactly(m1, arrived);
            assertThat(main.activeParentTab()).as("what arrived of the tab").isEqualTo(arrived);
            assertThat(full.closed()).isFalse();
            assertThat(content.tabs()).contains(split.get(1)).doesNotContain(split.get(0));
            assertThat(content.parentTabs().view(p2)).as("its other tab stays").isPresent();
            windows.assertNoIdInTwoWindows(main.tabs());
            windows.assertNoIdInTwoWindows(split);
            pin[0].close();
        });
        windows.launch();
    }

    /** A pane the planned layout lacks, one that opened meanwhile, stays in the tab; the plan still lands. */
    @Test void rearrangingKeepsAPaneThePlanLacks() throws Exception {
        edt(() -> {
            var content = windows.first().content();
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
        JasperWindow[] main = new JasperWindow[1];
        edt(() -> { main[0] = windows.first(); main[0].content().split(Side.RIGHT); });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            Tab tab = content.selectedTab();
            var running = session(tab);
            int[] focused = {0};
            tab.focusTarget(() -> focused[0]++);
            windows.facts.clear();
            JasperWindow child = windows.manager.moveToNewWindow(tab).orElseThrow();
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

    /** Workspace plan 7b Rulings 8: a window's only item stays, as that window already is its own. */
    @Test void aWindowsOnlyItemStays() throws Exception {
        edt(() -> {
            JasperWindow first = windows.first();
            JasperWindow full = windows.manager.openWindow(null).orElseThrow();
            assertThat(windows.manager.moveToNewWindow(full.content().selectedTab())).isEmpty();
            assertThat(windows.manager.moveToNewWindow(first.content().selectedTab())).as("the first window's too").isEmpty();
            assertThat(windows.manager.windows()).containsExactly(first, full);
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 7: an item whose close waits for its guard stays, and no window opens for it even briefly. */
    @Test void anItemBeingClosedOrPinnedOpensNoWindow() throws Exception {
        edt(() -> {
            var content = windows.first().content();
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
            assertThat(windows.others()).isEmpty();
            assertThat(content.tabs()).contains(notes);
            answer.complete(false);
            assertThat(closing).isCompletedWithValue(false);
            assertThat(windows.manager.moveToNewWindow(notes)).as("once the close is refused, it may go").isPresent();
        });
        windows.launch();
    }
}
