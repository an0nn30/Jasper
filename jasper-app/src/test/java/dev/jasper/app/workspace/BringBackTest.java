package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.MovesFixture.moved;
import static dev.jasper.app.workspace.MovesFixture.session;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.TerminalEvent;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 5, 11 and 14: Pop Out Parent Tab, Move to Main Window and Bring Back, and the origins they follow. */
class BringBackTest {
    private MovesFixture windows;

    @BeforeEach void windows() throws Exception { edt(() -> windows = new MovesFixture()); }

    @AfterEach void cleanup() throws Exception {
        edt(() -> windows.close());
        closeOwners();
    }

    /** Spec 5 and Review Focus 5: the pane window's layout returns nested beside its origin pane; its active tab has the focus. */
    @Test void bringBackReturnsThePanesNestedBesideTheOriginPane() throws Exception {
        MainWindow[] main = new MainWindow[1];
        ChildWindow[] pane = new ChildWindow[1];
        edt(() -> { main[0] = windows.main(); main[0].content().split(Side.RIGHT); });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            pane[0] = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            pane[0].content().split(Side.DOWN);
        });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            GroupId left = content.workspace().model().activeGroup();
            Layout panes = pane[0].content().workspace().model();
            List<Tab> moving = pane[0].content().tabs();
            var sessions = moving.stream().map(MovesFixture::session).toList();
            Tab active = pane[0].content().selectedTab();
            int[] focused = {0};
            active.focusTarget(() -> focused[0]++);
            windows.facts.clear();
            assertThat(windows.manager.bringBack(pane[0].content())).containsSame(main[0]);
            assertThat(pane[0].closed()).as("the pane window closes with its last tab").isTrue();
            Layout layout = content.workspace().model();
            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split -> {
                assertThat(split.orientation()).isEqualTo(Orientation.HORIZONTAL);
                assertThat(split.children()).extracting(Child::weight).containsExactly(0.5, 0.5);
                assertThat(split.children()).extracting(Child::node).as("nested as it was in the pane window")
                    .containsExactly(layout.group(left).orElseThrow(), panes.root());
            });
            assertThat(content.selectedTab()).as("the pane window's active tab").isSameAs(active);
            assertThat(focused[0]).isPositive();
            assertThat(windows.tabFacts()).isEqualTo(moved(moving, pane[0].id(), main[0].id()));
            for (int i = 0; i < moving.size(); i++) assertThat(session(moving.get(i))).isSameAs(sessions.get(i));
        });
    }

    /** Spec 11: Bring Back into a maximized parent tab ends the maximize. */
    @Test void bringBackIntoAMaximizedParentTabEndsTheMaximize() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.split(Side.RIGHT);
            content.split(Side.RIGHT);
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            content.toggleMaximize();
            assertThat(content.workspace().model().maximized()).isPresent();
            windows.manager.bringBack(pane.content());
            assertThat(content.workspace().model().maximized()).isEmpty();
            assertThat(content.workspace().model().groups()).hasSize(3);
        });
        windows.launch();
    }

    /** Spec 11: with the origin pane gone and its parent tab still there, the panes go beside its active pane. */
    @Test void withTheOriginPaneGoneThePanesGoBesideItsParentTabsActivePane() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            Tab first = content.selectedTab();
            content.split(Side.RIGHT);
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            content.split(Side.DOWN);
            GroupId below = content.workspace().model().activeGroup();
            content.closeTab(first);
            assertThat(content.workspace().model().groups()).extracting(Group::id).containsExactly(below);
            Tab back = pane.content().selectedTab();
            windows.manager.bringBack(pane.content());
            Layout layout = content.workspace().model();
            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split -> {
                assertThat(split.orientation()).as("on the recorded side").isEqualTo(Orientation.HORIZONTAL);
                assertThat(split.children().get(0).node()).isEqualTo(layout.group(below).orElseThrow());
                assertThat(((Group) split.children().get(1).node()).tabs()).containsExactly(back.id());
            });
        });
        windows.launch();
    }

    /** Review Focus 3 and spec 5: a parent tab's only pane popping out closes it; Bring Back then uses the main window's. */
    @Test void withItsParentTabGoneThePanesGoBesideTheMainWindowsActivePane() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            content.newParentTab(HOME);
            ParentTabId second = content.activeParentTab();
            GroupId secondPane = content.workspace().model().activeGroup();
            content.selectParentTab(first);
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            assertThat(content.parentTabCount()).as("the parent tab went with its only pane").isEqualTo(1);
            assertThat(content.activeParentTab()).as("the one used before it shows").isEqualTo(second);
            assertThat(pane.content().origin().orElseThrow().parentTab()).isEqualTo(first);
            Tab back = pane.content().selectedTab();
            assertThat(windows.manager.bringBack(pane.content())).containsSame(windows.main());
            Layout layout = content.workspace().model();
            assertThat(content.parentTabOf(back)).contains(second);
            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split ->
                assertThat(split.children()).extracting(Child::node).first().isEqualTo(layout.group(secondPane).orElseThrow()));
        });
        windows.launch();
    }

    /** Spec 11: the origin follows its parent tab by id, into the full window that parent tab popped out to. */
    @Test void theOriginFollowsItsParentTabIntoAFullWindow() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            GroupId left = content.workspace().model().activeGroup();
            content.split(Side.RIGHT);
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            content.newParentTab(HOME);
            ChildWindow full = windows.manager.popOutParentTab(content, first).orElseThrow();
            assertThat(full.content().isPaneWindow()).isFalse();
            assertThat(full.content().activeParentTab()).as("the parent tab keeps its id").isEqualTo(first);
            Tab back = pane.content().selectedTab();
            assertThat(windows.manager.bringBack(pane.content())).containsSame(full);
            Layout layout = full.content().workspace().model();
            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split ->
                assertThat(split.children()).extracting(Child::node).first().isEqualTo(layout.group(left).orElseThrow()));
            assertThat(full.content().parentTabOf(back)).contains(first);
        });
        windows.launch();
    }

    /**
     * Review Focus 2: the main window's only pane popped out; its empty workspace keeps the parent tab's id, which the pane
     * window's origin names, and Bring Back fills it (workspace plan 7b Rulings 10).
     */
    @Test void bringBackFillsTheEmptyMainWorkspaceItLeft() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId only = content.activeParentTab();
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            assertThat(content.workspace().emptyWorkspace()).as("the main window's empty workspace").isNotNull();
            assertThat(content.activeParentTab()).as("a pane popping out leaves the id").isEqualTo(only);
            pane.content().split(Side.RIGHT);
            Layout panes = pane.content().workspace().model();
            windows.manager.bringBack(pane.content());
            assertThat(content.parentTabCount()).isEqualTo(1);
            assertThat(content.activeParentTab()).isEqualTo(only);
            assertThat(content.workspace().model()).as("the panes, whole").isEqualTo(panes);
            assertThat(content.workspace().emptyWorkspace()).isNull();
        });
        windows.launch();
    }

    /**
     * Workspace plan 7b Rulings 10: the main window's only parent tab moves whole with its id, so the empty workspace it
     * leaves takes a new one, and none of the moved panes' ids (parent tabs spec 4).
     */
    @Test void theMainWindowsOnlyParentTabPopsOutWholeAndItsEmptyWorkspaceTakesANewId() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId only = content.activeParentTab();
            content.split(Side.RIGHT);
            Layout moving = content.workspace().model();
            ChildWindow full = windows.manager.popOutParentTab(content, only).orElseThrow();
            assertThat(full.content().activeParentTab()).isEqualTo(only);
            assertThat(full.content().workspace().model()).isEqualTo(moving);
            assertThat(content.activeParentTab()).as("the empty workspace's new id").isNotEqualTo(only);
            assertThat(content.parentTabCount()).isEqualTo(1);
            assertThat(content.workspace().emptyWorkspace()).isNotNull();
            assertThat(moving.groups()).extracting(Group::id).doesNotContain(content.workspace().model().activeGroup());
        });
        windows.launch();
    }

    /** Spec 5 and workspace plan 7b Rulings 10: a parent tab pops out whole into a full window, with its id and layout. */
    @Test void popOutParentTabMovesItWholeToAFullWindow() throws Exception {
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
            content.newParentTab(HOME);
            ParentTabId second = content.activeParentTab();
            windows.facts.clear();
            ChildWindow full = windows.manager.popOutParentTab(content, first).orElseThrow();
            assertThat(full.content().windowLayout()).isInstanceOfSatisfying(ParentTabs.class, parents ->
                assertThat(parents.parentTabs()).containsExactly(new ParentTab(first, before)));
            assertThat(content.parentTabCount()).isEqualTo(1);
            assertThat(content.activeParentTab()).isEqualTo(second);
            assertThat(windows.tabFacts()).isEqualTo(moved(moving, main[0].id(), full.id()));
            for (int i = 0; i < moving.size(); i++) assertThat(session(moving.get(i))).isSameAs(sessions.get(i));
            assertThat(windows.manager.popOutParentTab(full.content(), first)).as("a full window's only parent tab stays").isEmpty();
        });
        windows.launch();
    }

    /** Spec 5 and workspace plan 7b Rulings 12: a full window's parent tabs join the main window's bar after its active one. */
    @Test void moveToMainWindowJoinsTheParentTabsAfterTheActiveOne() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            ParentTabId m1 = main.activeParentTab();
            main.newParentTab(HOME);
            ParentTabId m2 = main.activeParentTab();
            main.selectParentTab(m1);
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            var content = full.content();
            ParentTabId p1 = content.activeParentTab();
            content.newParentTab(HOME);
            content.split(Side.RIGHT);
            ParentTabId p2 = content.activeParentTab();
            var layouts = ((ParentTabs) content.windowLayout()).parentTabs();
            int tabs = content.tabCount();
            windows.facts.clear();
            assertThat(windows.manager.moveToMainWindow(content)).containsSame(windows.main());
            assertThat(full.closed()).as("the full window closes with its last tab").isTrue();
            var parents = (ParentTabs) main.windowLayout();
            assertThat(parents.parentTabs()).extracting(ParentTab::id).containsExactly(m1, p1, p2, m2);
            assertThat(parents.parentTabs().subList(1, 3)).as("each with its layout").containsExactlyElementsOf(layouts);
            assertThat(main.activeParentTab()).as("the parent tab it showed").isEqualTo(p2);
            assertThat(windows.tabFacts()).hasSize(tabs).allMatch(TerminalEvent.TabMoved.class::isInstance);
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 12: the main window's empty workspace gives way to the first parent tab. */
    @Test void moveToMainWindowFillsAnEmptyMainWorkspace() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.closeTab(main.selectedTab());
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            ParentTabId p1 = full.content().activeParentTab();
            windows.manager.moveToMainWindow(full.content());
            assertThat(((ParentTabs) main.windowLayout()).parentTabs()).extracting(ParentTab::id).containsExactly(p1);
            assertThat(main.tabCount()).isEqualTo(1);
            assertThat(full.closed()).as("the full window closes with its last tab").isTrue();
            assertThat(main.activeParentTab()).as("the parent tab it showed").isEqualTo(p1);
        });
        windows.launch();
    }

    /** Review Focus 1: a pinned tab keeps its parent tab and its pane window whole, and nothing moves. */
    @Test void aPinnedTabKeepsItsParentTabAndItsPaneWindowWhole() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            content.split(Side.RIGHT);
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            content.newParentTab(HOME);
            Subscription heldHere = content.tabs().getFirst().pin();
            Subscription heldThere = pane.content().selectedTab().pin();
            windows.facts.clear();
            assertThat(windows.manager.popOutParentTab(content, first)).isEmpty();
            assertThat(windows.manager.bringBack(pane.content())).isEmpty();
            assertThat(windows.manager.moveParentTab(content, first, pane.content(), 0)).as("nor into a pane window").isFalse();
            assertThat(pane.closed()).isFalse();
            assertThat(windows.manager.children()).containsExactly(pane);
            assertThat(windows.tabFacts()).isEmpty();
            heldHere.close();
            heldThere.close();
            assertThat(windows.manager.bringBack(pane.content())).isPresent();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 9 and 10: a whole parent tab never moves into or out of a pane window, nor within its window. */
    @Test void aParentTabMovesNeitherIntoAPaneWindowNorWithinItsWindow() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            content.split(Side.RIGHT);
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            content.newParentTab(HOME);
            windows.facts.clear();
            assertThat(windows.manager.moveParentTab(content, first, pane.content(), 0)).as("into a pane window").isFalse();
            assertThat(windows.manager.moveParentTab(pane.content(), pane.content().activeParentTab(), content, 0))
                .as("out of a pane window").isFalse();
            assertThat(windows.manager.moveParentTab(content, first, content, 0)).as("within its own window").isFalse();
            assertThat(windows.tabFacts()).isEmpty();
            assertThat(content.parentTabCount()).isEqualTo(2);
            assertThat(pane.content().tabCount()).isEqualTo(1);
        });
        windows.launch();
    }

    /** Spec 5 and 11: Bring Back into a hidden parent tab shows it, the panes beside the origin pane. */
    @Test void bringBackShowsTheHiddenParentTabItGoesTo() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            GroupId left = content.workspace().model().activeGroup();
            content.split(Side.RIGHT);
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            Tab back = pane.content().selectedTab();
            content.newParentTab(HOME);
            assertThat(content.activeParentTab()).as("another parent tab shows").isNotEqualTo(first);
            assertThat(windows.manager.bringBack(pane.content())).containsSame(windows.main());
            assertThat(content.activeParentTab()).as("the parent tab the panes went to").isEqualTo(first);
            Layout layout = content.workspace().model();
            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split -> {
                assertThat(split.orientation()).isEqualTo(Orientation.HORIZONTAL);
                assertThat(split.children().get(0).node()).isEqualTo(layout.group(left).orElseThrow());
                assertThat(((Group) split.children().get(1).node()).tabs()).containsExactly(back.id());
            });
            assertThat(content.selectedTab()).isSameAs(back);
            assertThat(content.parentTabCount()).isEqualTo(2);
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 10: a parent tab moving whole keeps its layout, its maximize included. */
    @Test void aMaximizedParentTabPopsOutStillMaximized() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            content.split(Side.RIGHT);
            content.toggleMaximize();
            Layout before = content.workspace().model();
            GroupId maximized = before.maximized().orElseThrow();
            content.newParentTab(HOME);
            ChildWindow full = windows.manager.popOutParentTab(content, first).orElseThrow();
            assertThat(full.content().workspace().model().maximized()).as("on the same pane").contains(maximized);
            assertThat(full.content().workspace().model()).isEqualTo(before);
        });
        windows.launch();
    }

    /** The empty workspace a parent tab moving whole leaves behind takes the window's theme and glass, as a new one does. */
    @Test void theRenewedEmptyWorkspaceTakesTheWindowsThemeAndGlass() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.applyBackgroundOpacity(0.5);
            ParentTabId only = content.activeParentTab();
            content.split(Side.RIGHT);
            windows.manager.popOutParentTab(content, only).orElseThrow();
            WorkspaceView renewed = content.workspace();
            assertThat(renewed.id()).as("the renewed workspace").isNotEqualTo(only);
            assertThat(renewed.isOpaque()).as("under glass it paints nothing behind its tabs").isFalse();
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

    /** A tab the planned layout lacks, one that came in meanwhile, joins its active pane; the plan still lands. */
    @Test void rearrangingKeepsATabThePlanLacks() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.split(Side.RIGHT);
            WorkspaceView workspace = content.workspace();
            Tab planned = content.selectedTab();
            Layout plan = workspace.model().resize(List.of(), List.of(0.3, 0.7));
            content.newTab(HOME);
            Tab extra = content.selectedTab();
            workspace.rearrange(plan);
            Layout layout = workspace.model();
            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split ->
                assertThat(split.children()).extracting(Child::weight).as("the plan landed").containsExactly(0.3, 0.7));
            assertThat(layout.group(plan.activeGroup()).orElseThrow().tabs()).as("in its active pane")
                .containsExactly(planned.id(), extra.id());
            assertThat(content.selectedTab()).as("the plan's active tab").isSameAs(planned);
            assertThat(content.tabs()).hasSize(3).contains(extra);
        });
        windows.launch();
    }
}
