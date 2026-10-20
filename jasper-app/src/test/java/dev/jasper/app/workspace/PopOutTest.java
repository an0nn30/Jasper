package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.MovesFixture.moved;
import static dev.jasper.app.workspace.MovesFixture.session;
import static org.assertj.core.api.Assertions.assertThat;

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
import dev.jasper.app.layout.Side;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.testsupport.EnterpriseLook;
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
            assertThat(windows.manager.popOutPane(pane.content(), pane.content().workspace().model().activeGroup()))
                .as("a pane window's only pane").isEmpty();
            assertThat(windows.manager.children()).containsExactly(full, pane);
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
