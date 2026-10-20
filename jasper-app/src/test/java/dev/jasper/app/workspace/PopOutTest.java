package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.MovesFixture.moved;
import static dev.jasper.app.workspace.MovesFixture.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Origin;
import dev.jasper.app.layout.PaneLayout;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.testsupport.EnterpriseLook;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 4, 5 and 14: Pop Out Tab and Pop Out Pane, and the pane windows they open. */
class PopOutTest {
    private MovesFixture windows;

    @BeforeEach void windows() throws Exception { edt(() -> windows = new MovesFixture()); }

    @AfterEach void cleanup() throws Exception {
        edt(() -> windows.close());
        closeOwners();
    }

    /** Spec 5 and Review Focus 5: one tab leaves for a pane window whose origin is its pane; one TAB_MOVED; focus follows. */
    @Test void popOutTabTakesOneTabToAPaneWindowFromItsPane() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> { main[0] = windows.main(); main[0].content().newTab(HOME); });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            Tab tab = content.selectedTab();
            var running = session(tab);
            GroupId pane = content.workspace().model().activeGroup();
            int[] focused = {0};
            tab.focusTarget(() -> focused[0]++);
            windows.facts.clear();
            ChildWindow child = windows.manager.popOutTab(tab).orElseThrow();
            Origin origin = new Origin(content.activeParentTab(), pane, Side.RIGHT, 0.5);
            assertThat(child.content().isPaneWindow()).isTrue();
            assertThat(child.content().origin()).as("its pane, which keeps the other tab: it goes back on the right").contains(origin);
            assertThat(child.content().windowLayout()).isEqualTo(new PaneLayout(child.content().workspace().model(), origin));
            assertThat(child.content().tabs()).containsExactly(tab);
            assertThat(windows.tabFacts()).as("one move; no tab opened or closed").isEqualTo(moved(List.of(tab), main[0].id(), child.id()));
            assertThat(session(tab)).isSameAs(running);
            assertThat(running.exitFuture()).isNotDone();
            assertThat(focused[0]).as("the tab has the focus in its pane window").isPositive();
            assertThat(content.tabCount()).isEqualTo(1);
        });
    }

    /** Workspace plan 7b Rulings 3: a pane's only tab takes its pane along; the origin is where the pane sat. */
    @Test void aPanesOnlyTabPopsOutFromWhereItsPaneSat() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> { main[0] = windows.main(); main[0].content().split(Side.RIGHT); });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            GroupId left = content.workspace().model().groups().getFirst().id();
            ChildWindow child = windows.manager.popOutTab(content.selectedTab()).orElseThrow();
            assertThat(child.content().origin()).contains(new Origin(content.activeParentTab(), left, Side.RIGHT, 0.5));
            assertThat(content.workspace().model().groups()).as("the pane went with its tab").hasSize(1);
        });
    }

    /** Spec 5: a pane's tabs leave together, in order, showing the one it showed; one TAB_MOVED each; sessions kept. */
    @Test void popOutPaneTakesEveryTabOfThePaneInOrder() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> {
            main[0] = windows.main();
            main[0].content().newTab(HOME);
            main[0].content().newTab(HOME);
            main[0].content().split(Side.DOWN);
        });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            var workspace = content.workspace();
            GroupId top = workspace.model().groups().get(0).id(), bottom = workspace.model().groups().get(1).id();
            List<Tab> moving = workspace.groupView(top).tabs();
            Tab shown = moving.get(1);
            content.selectTab(shown);
            var sessions = moving.stream().map(MovesFixture::session).toList();
            windows.facts.clear();
            ChildWindow child = windows.manager.popOutPane(content, top).orElseThrow();
            assertThat(child.content().origin()).as("the pane below takes its space; it goes back above it")
                .contains(new Origin(content.activeParentTab(), bottom, Side.UP, 0.5));
            assertThat(child.content().workspace().model().groups()).singleElement().satisfies(group -> {
                assertThat(group.tabs()).containsExactlyElementsOf(moving.stream().map(Tab::id).toList());
                assertThat(group.selected()).contains(shown.id());
            });
            assertThat(child.content().selectedTab()).isSameAs(shown);
            assertThat(content.workspace().model().groups()).extracting(Group::id).containsExactly(bottom);
            assertThat(windows.tabFacts()).isEqualTo(moved(moving, main[0].id(), child.id()));
            for (int i = 0; i < moving.size(); i++) assertThat(session(moving.get(i))).isSameAs(sessions.get(i));
        });
    }

    /** Workspace plan 7b Rulings 8: a child window's only tab and only pane stay, as that window already is its own. */
    @Test void aChildWindowsOnlyTabAndOnlyPaneStay() throws Exception {
        edt(() -> {
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            assertThat(windows.manager.popOutTab(full.content().selectedTab())).isEmpty();
            assertThat(windows.manager.popOutPane(full.content(), full.content().workspace().model().activeGroup())).isEmpty();
            full.content().newTab(HOME);
            ChildWindow pane = windows.manager.popOutTab(full.content().selectedTab()).orElseThrow();
            assertThat(windows.manager.popOutTab(pane.content().selectedTab())).as("a pane window's only tab").isEmpty();
            assertThat(windows.manager.popOutPane(pane.content(), pane.content().workspace().model().activeGroup()))
                .as("a pane window's only pane").isEmpty();
            assertThat(windows.manager.children()).containsExactly(full, pane);
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 8: the main window's only tab and only pane may pop out, leaving its empty workspace. */
    @Test void theMainWindowsOnlyTabAndOnlyPanePopOutAndLeaveItsEmptyWorkspace() throws Exception {
        edt(() -> {
            MainWindow main = windows.main();
            var content = main.content();
            assertThat(windows.manager.popOutTab(content.selectedTab())).as("its only tab").isPresent();
            assertThat(main.closed()).isFalse();
            assertThat(content.tabCount()).isZero();
            assertThat(content.workspace().emptyWorkspace()).as("its empty workspace shows").isNotNull();
            content.newTab(HOME);
            assertThat(content.workspace().emptyWorkspace()).isNull();
            assertThat(windows.manager.popOutPane(content, content.workspace().model().activeGroup())).as("its only pane").isPresent();
            assertThat(content.tabCount()).isZero();
            assertThat(content.workspace().emptyWorkspace()).isNotNull();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 19: plan 5's tear-out still opens a full window, which has no origin. */
    @Test void plan5sTearOutOpensAFullWindow() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.newTab(HOME);
            Tab torn = content.selectedTab();
            ChildWindow child = windows.manager.moveToNewWindow(torn, new Rectangle(100, 100, 600, 400), new Point(400, 300))
                .orElseThrow();
            assertThat(child.content().isPaneWindow()).isFalse();
            assertThat(child.content().origin()).isEmpty();
            assertThat(child.content().windowLayout()).isInstanceOf(ParentTabs.class);
            assertThat(child.content().tabs()).containsExactly(torn);
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 4: what pops out of a pane window keeps that window's origin. */
    @Test void whatPopsOutOfAPaneWindowKeepsItsOrigin() throws Exception {
        ChildWindow[] first = new ChildWindow[1];
        edt(() -> {
            var main = windows.main().content();
            main.newTab(HOME);
            first[0] = windows.manager.popOutTab(main.selectedTab()).orElseThrow();
            first[0].content().newTab(HOME);
            first[0].content().newTab(HOME);
        });
        windows.launch();
        edt(() -> {
            var pane = first[0].content();
            Origin origin = pane.origin().orElseThrow();
            ChildWindow second = windows.manager.popOutTab(pane.selectedTab()).orElseThrow();
            assertThat(second.content().origin()).contains(origin);
            pane.split(Side.RIGHT);
            ChildWindow third = windows.manager.popOutPane(pane, pane.workspace().model().activeGroup()).orElseThrow();
            assertThat(third.content().origin()).contains(origin);
        });
        windows.launch();
    }

    /** Spec 4 and 7, workspace plan 7b Rulings 9: a pane window has no parent tabs, so New Tab stacks in its pane. */
    @Test void aPaneWindowHasNoParentTabsAndNewTabStacksInItsPane() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.newTab(HOME);
            var pane = windows.manager.popOutTab(main.selectedTab()).orElseThrow().content();
            pane.invoke(ActionId.NEW_TAB);
            assertThat(pane.parentTabCount()).isEqualTo(1);
            assertThat(pane.tabCount()).as("New Tab stacks in its pane").isEqualTo(2);
            assertThat(pane.workspace().model().groups()).hasSize(1);
            assertThat(pane.parentTabBar().component().isVisible()).isFalse();
            assertThat(pane.windowLayout()).isInstanceOf(PaneLayout.class);
            pane.updateActions();
            assertThat(pane.action(ActionId.CLOSE_PARENT_TAB).isEnabled()).as("it has no parent tab to close").isFalse();
        });
        windows.launch();
    }

    /** Spec 7: Enterprise always shows the parent-tab bar, but a pane window has none. */
    @Test void enterprisesPaneWindowShowsNoParentTabBar() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                boolean macOs = System.getProperty("os.name").startsWith("Mac");
                var pane = track(new WindowContent(WindowRole.CHILD, idleLauncher(new ArrayDeque<>()), null, true, path -> { },
                    () -> { }, () -> { }, themes, KeyBindings.defaults(macOs), new CommandHistory(), macOs, ClassicTabsTest.classic()));
                assertThat(pane.parentTabBar().component().isVisible()).as("as a full window's").isTrue();
                pane.paneWindow(new Origin(ParentTabId.random(), GroupId.random(), Side.RIGHT, 0.5));
                assertThat(pane.parentTabBar().component().isVisible()).isFalse();
                pane.newTab(HOME);
                assertThat(pane.parentTabBar().component().isVisible()).isFalse();
                assertThat(pane.tabBar().component().isVisible()).as("its pane's classic strip shows").isTrue();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Review Focus 1: a pane holding a tab with a modal prompt open stays whole; nothing moves and no window opens. */
    @Test void aPaneHoldingAPinnedTabStaysWhole() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.newTab(HOME);
            Tab pinned = content.selectedTab();
            Subscription pin = pinned.pin();
            windows.facts.clear();
            assertThat(windows.manager.popOutPane(content, content.workspace().model().activeGroup())).isEmpty();
            assertThat(windows.manager.popOutTab(pinned)).isEmpty();
            assertThat(windows.manager.children()).isEmpty();
            assertThat(windows.tabFacts()).isEmpty();
            assertThat(content.tabCount()).isEqualTo(2);
            pin.close();
            assertThat(windows.manager.popOutPane(content, content.workspace().model().activeGroup())).isPresent();
        });
        windows.launch();
    }

    /**
     * Parent tabs spec 4 (7b's final review): a listener pins the pane's second tab as its first moves. The pane stays split
     * across the two windows, each window's ids unique, and Pop Out Pane gives the pane window holding what arrived.
     */
    @Test void aPaneThatPopsOutInPartLeavesNoIdInTwoWindows() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.newTab(HOME);
            GroupId pane = content.workspace().model().activeGroup();
            List<Tab> moving = content.workspace().groupView(pane).tabs();
            Subscription[] pin = MovesFixture.pinWhenItLeaves(moving.get(0), moving.get(1));
            ChildWindow child = windows.manager.popOutPane(content, pane).orElseThrow();
            assertThat(child.content().tabs()).as("what arrived").containsExactly(moving.get(0));
            assertThat(content.tabs()).as("what stayed").containsExactly(moving.get(1));
            assertThat(content.workspace().model().groups()).extracting(Group::id).containsExactly(pane);
            windows.assertNoIdInTwoWindows(moving);
            pin[0].close();
        });
        windows.launch();
    }

    /** As above, the second tab's move failing: the failure propagates, and what arrived stays in the pane window. */
    @Test void aPaneWhoseSecondTabFailsToMoveLeavesNoIdInTwoWindows() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.newTab(HOME);
            GroupId pane = content.workspace().model().activeGroup();
            List<Tab> moving = content.workspace().groupView(pane).tabs();
            var refusal = MovesFixture.refuseItsMove(moving.get(1));
            assertThatThrownBy(() -> windows.manager.popOutPane(content, pane)).isSameAs(refusal);
            assertThat(windows.manager.children()).singleElement()
                .satisfies(child -> assertThat(child.content().tabs()).containsExactly(moving.get(0)));
            assertThat(content.tabs()).containsExactly(moving.get(1));
            windows.assertNoIdInTwoWindows(moving);
        });
        windows.launch();
    }

    /**
     * Workspace plan 7b Rulings 7: a pane holding a tab whose close waits for its guard stays whole. Pop Out Pane is
     * disabled, as a command and in the palette, and the window manager refuses it, opening no window.
     */
    @Test void aPaneHoldingATabBeingClosedStaysWhole() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            Tab notes = windows.model.tabs().open("dev.example.editor", "notes", "notes.txt", content.id(), new JPanel());
            GroupId pane = content.workspace().model().activeGroup();
            assertThat(content.workspace().groupView(pane).tabs()).hasSize(2).contains(notes);
            var answer = new CompletableFuture<Boolean>();
            windows.model.tabs().guard("dev.example.editor", (tabs, batch) -> answer);
            var closing = notes.requestClose();
            assertThat(closing).as("the guard has not answered").isNotDone();
            content.updateActions();
            assertThat(content.action(ActionId.POP_OUT_PANE).isEnabled()).as("the command").isFalse();
            assertThat(content.commands().find("pop_out_pane").orElseThrow().action().isEnabled()).as("in the palette").isFalse();
            windows.facts.clear();
            assertThat(windows.manager.popOutPane(content, pane)).isEmpty();
            assertThat(windows.facts).as("no window opened and closed again").noneMatch(TerminalEvent.WindowOpened.class::isInstance);
            assertThat(windows.manager.children()).isEmpty();
            assertThat(content.workspace().groupView(pane).tabs()).as("the pane stays whole").hasSize(2);
            answer.complete(false);
            assertThat(closing).isCompletedWithValue(false);
            content.updateActions();
            assertThat(content.action(ActionId.POP_OUT_PANE).isEnabled()).as("once the close is refused").isTrue();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 7: a tab whose close waits for its guard stays, and no window opens for it even briefly. */
    @Test void aTabBeingClosedOpensNoWindow() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            Tab notes = windows.model.tabs().open("dev.example.editor", "notes", "notes.txt", content.id(), new JPanel());
            var answer = new CompletableFuture<Boolean>();
            windows.model.tabs().guard("dev.example.editor", (tabs, batch) -> answer);
            var closing = notes.requestClose();
            assertThat(closing).as("the guard has not answered").isNotDone();
            windows.facts.clear();
            assertThat(windows.manager.popOutTab(notes)).isEmpty();
            assertThat(windows.facts).as("no window opened and closed again").noneMatch(TerminalEvent.WindowOpened.class::isInstance);
            assertThat(windows.manager.children()).isEmpty();
            assertThat(content.tabs()).contains(notes);
            answer.complete(false);
            assertThat(closing).isCompletedWithValue(false);
            assertThat(windows.manager.popOutTab(notes)).as("once the close is refused, it may go").isPresent();
        });
        windows.launch();
    }
}
