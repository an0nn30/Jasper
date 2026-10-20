package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.KeyboardFocusManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Parent tabs spec 4, 7, 10, 11 and 14: a window's parent tabs, each a workspace of its own; what lands in which, what
 * closes them, and that switching keeps every tab's component and session.
 */
class ParentTabsViewTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** New Tab in {@code owner}: the tab of its new parent tab. */
    private static Tab open(WindowContent owner) {
        owner.newParentTab(HOME);
        return owner.selectedTab();
    }

    private static dev.jasper.terminal.session.TerminalSession session(Tab tab) {
        return ((TerminalContent) tab.content()).pane().session();
    }

    @Test void aNewParentTabHoldsOneTerminalAtTheEndOfTheBarAndShowsAlone() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            Tab first = owner.selectedTab();
            WorkspaceView before = owner.workspace();
            TerminalContent opened = owner.newParentTab(HOME);
            Tab second = owner.selectedTab();
            assertThat(second.content()).isSameAs(opened);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.workspace()).as("the new parent tab shows").isNotSameAs(before);
            assertThat(owner.workspace().tabs()).containsExactly(second);
            assertThat(owner.workspace().isVisible()).isTrue();
            assertThat(before.isVisible()).as("the first stays in the tree, hidden").isFalse();
            assertThat(before.getParent()).isSameAs(owner.parentTabs());
            assertThat(SwingUtilities.isDescendingFrom(first.component(), before)).as("its tab stays where it was").isTrue();
            assertThat(owner.tabs()).as("every tab, the parent tabs in bar order").containsExactly(first, second);
            var parents = (ParentTabs) owner.windowLayout();
            assertThat(parents.parentTabs()).extracting(ParentTab::id).containsExactly(before.id(), owner.workspace().id());
            assertThat(parents.active()).isEqualTo(owner.activeParentTab()).isEqualTo(owner.workspace().id());
            assertThat(parents.activeParentTab().layout()).isEqualTo(owner.workspace().model());
            assertThat(owner.parentTabOf(first)).contains(before.id());
            assertThat(first.group()).as("the registry finds a hidden parent tab's group").contains(before.model().activeGroup());
            owner.applyBackgroundOpacity(0.5);
            assertThat(owner.workspaceOpacity(first.component())).as("a hidden parent tab is in the workspace").isEqualTo(0.5);
        });
    }

    @Test void switchingParentTabsKeepsEveryTabsComponentAndSession() throws Exception {
        var launcher = new ShellLauncher(Runnable::run, directory -> IdleTerminalSession.start(), "idle-test-session");
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            owner[0] = content(launcher);
            owner[0].newTab(HOME);
            owner[0].newParentTab(HOME);
            owner[0].setSize(1208, 700);
            LayoutTestSupport.layoutTree(owner[0]);
        });
        edt(() -> { });
        edt(() -> {
            var window = owner[0];
            var tabs = window.tabs();
            var sessions = tabs.stream().map(ParentTabsViewTest::session).toList();
            var places = tabs.stream().map(tab -> tab.component().getParent()).toList();
            assertThat(sessions).as("every shell started").hasSize(3).doesNotContainNull();
            ParentTabId first = window.parentTabOf(tabs.get(0)).orElseThrow(), second = window.activeParentTab();
            for (ParentTabId id : List.of(first, second, first)) {
                window.selectParentTab(id);
                LayoutTestSupport.layoutTree(window);
                assertThat(window.activeParentTab()).isEqualTo(id);
                assertThat(window.tabs()).as("nothing opened or closed").isEqualTo(tabs);
                for (int i = 0; i < tabs.size(); i++) {
                    assertThat(session(tabs.get(i))).as("tab %d keeps its session", i).isSameAs(sessions.get(i));
                    assertThat(sessions.get(i).exitFuture()).as("tab %d's shell runs", i).isNotDone();
                    assertThat(tabs.get(i).component().getParent()).as("tab %d keeps its place", i).isSameAs(places.get(i));
                }
                assertThat(window.parentTabs().views()).filteredOn(java.awt.Component::isVisible).containsExactly(window.workspace());
            }
            assertThat(window.selectedTab()).as("the first parent tab shows the tab it showed").isSameAs(tabs.get(1));
        });
    }

    @Test void aParentTabGoesWithItsLastTabAndTheOneUsedBeforeItShows() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            Tab a = owner.selectedTab();
            ParentTabId first = owner.activeParentTab();
            Tab b = open(owner);
            ParentTabId second = owner.activeParentTab();
            Tab c = open(owner);
            ParentTabId third = owner.activeParentTab();
            owner.selectParentTab(first);
            owner.selectParentTab(third);
            owner.selectParentTab(second);
            owner.closeTab(b);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.activeParentTab()).as("the parent tab used before it").isEqualTo(third);
            assertThat(owner.parentTabs().view(second)).isEmpty();
            assertThat(((ParentTabs) owner.windowLayout()).parentTabs()).extracting(ParentTab::id).containsExactly(first, third);
            owner.closeTab(c);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            owner.closeTab(a);
            assertThat(owner.parentTabCount()).as("the main window keeps its only parent tab").isEqualTo(1);
            assertThat(owner.windowLayout().tabs()).isEmpty();
            assertThat(owner.workspace().emptyWorkspace()).as("as its empty workspace").isNotNull();
            owner.newParentTab(HOME);
            assertThat(owner.parentTabCount()).as("New Tab fills it rather than opening a second, empty one beside it")
                .isEqualTo(1);
            assertThat(owner.tabs()).hasSize(1);
        });
    }

    @Test void aChildWindowClosesWithItsLastParentTabsLastTab() throws Exception {
        int[] emptied = {0};
        edt(() -> {
            var child = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> emptied[0]++);
            Tab a = child.selectedTab();
            Tab b = open(child);
            child.closeTab(a);
            assertThat(child.parentTabCount()).isEqualTo(1);
            assertThat(emptied[0]).as("its other parent tab keeps it open").isZero();
            child.closeTab(b);
            assertThat(emptied[0]).as("a full window closes with its last parent tab").isEqualTo(1);
        });
    }

    @Test void closeParentTabClosesItsTabsAsOneGestureAfterTheirGuards() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            var model = new Contributions();
            owner.connectContributions(model);
            Tab terminal = owner.selectedTab();
            ParentTabId first = owner.activeParentTab();
            owner.split(Side.RIGHT);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel());
            Tab other = open(owner);
            boolean[] allow = {false};
            var asked = new ArrayList<List<Tab>>();
            model.tabs().guard("dev.example.editor", (tabs, batch) -> {
                asked.add(tabs);
                return CompletableFuture.completedFuture(allow[0]);
            });
            owner.closeParentTab(first);
            assertThat(asked).containsExactly(List.of(view));
            assertThat(owner.parentTabCount()).as("a refusal closes nothing").isEqualTo(2);
            assertThat(terminal.closed()).isFalse();
            allow[0] = true;
            owner.closeParentTab(first);
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).isTrue();
            assertThat(owner.parentTabCount()).isEqualTo(1);
            assertThat(owner.tabs()).as("the rest of the window").containsExactly(other);
        });
    }

    @Test void whatOpensLandsInTheShownParentTabsActivePane() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            var model = new Contributions();
            owner.connectContributions(model);
            open(owner);
            ParentTabId shown = owner.activeParentTab();
            GroupId pane = owner.workspace().model().activeGroup();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel());
            assertThat(owner.parentTabOf(notes)).as("TabPlacement.ACTIVE_GROUP: the shown parent tab's active pane").contains(shown);
            assertThat(notes.group()).contains(pane);
            Tab beside = model.tabs().open("dev.example.editor", "beside", "beside.txt", owner.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            assertThat(owner.parentTabOf(beside)).contains(shown);
            assertThat(owner.workspace().model().groups()).as("a new pane of the shown parent tab").hasSize(2);
            owner.newTab(HOME);
            assertThat(owner.parentTabOf(owner.selectedTab())).as("a request's folder (single instance), Terminals.openTab")
                .contains(shown);
            assertThat(owner.parentTabCount()).isEqualTo(2);
        });
    }

    @Test void parentTabsOpeningClosingAndSwitchingMoveNoTabAndAMoveBetweenWindowsReportsOne() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var source = content(idleLauncher(pending));
            var target = content(idleLauncher(pending));
            source.connectContributions(model);
            target.connectContributions(model);
            var registry = new TerminalRegistry();
            var facts = new ArrayList<TerminalEvent>();
            registry.onEvent(facts::add);
            source.connectTerminals(registry, () -> { });
            target.connectTerminals(registry, () -> { });
            Tab a = source.selectedTab();
            ParentTabId first = source.activeParentTab();
            Tab b = open(source);
            ParentTabId targetFirst = target.activeParentTab();
            GroupId hidden = target.workspace().model().activeGroup();
            open(target);
            facts.clear();
            source.selectParentTab(first);
            source.selectParentTab(source.parentTabOf(b).orElseThrow());
            source.closeTab(open(source));
            assertThat(facts).as("parent tabs opening, closing and switching move no tab")
                .noneMatch(TerminalEvent.TabMoved.class::isInstance);
            facts.clear();
            model.tabs().move(a, target.id(), hidden, Side.RIGHT);
            assertThat(facts).filteredOn(TerminalEvent.TabMoved.class::isInstance)
                .containsExactly(new TerminalEvent.TabMoved(a.id().value(), source.id(), target.id()));
            assertThat(source.parentTabCount()).as("its parent tab went with it").isEqualTo(1);
            assertThat(target.parentTabOf(a)).as("beside the group it named, in that group's parent tab").contains(targetFirst);
            assertThat(target.activeParentTab()).as("which shows it").isEqualTo(targetFirst);
            assertThat(target.workspace().model().groupOf(a.id())).isPresent().isNotEqualTo(Optional.of(hidden));
        });
    }

    /** Review Focus 1: a hidden parent tab's last tab closes on its own, as a shell's exit or a plugin's close does. */
    @Test void aHiddenParentTabsLastTabClosingLeavesTheShownOneAlone() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            Tab hidden = owner.selectedTab();
            Tab shown = open(owner);
            ParentTabId active = owner.activeParentTab();
            int[] focused = {0};
            shown.focusTarget(() -> focused[0]++);
            hidden.close();
            assertThat(owner.parentTabCount()).isEqualTo(1);
            assertThat(owner.activeParentTab()).isEqualTo(active);
            assertThat(owner.selectedTab()).isSameAs(shown);
            assertThat(focused[0]).as("the focus is left where it is").isZero();
        });
    }

    /** Review Focus 5 (workspace plan 7a Rulings 10). */
    @Test void closingTheShownParentTabLeavesFocusThatIsElsewhereWhereItIs() throws Exception {
        var prior = new KeyboardFocusManager[1];
        var keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
        var outside = new JPanel();
        var field = new JTextField();
        outside.add(field);
        try {
            edt(() -> {
                prior[0] = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
                var owner = content(idleLauncher(pending));
                Tab first = owner.selectedTab();
                Tab second = open(owner);
                int[] focused = {0};
                first.focusTarget(() -> focused[0]++);
                outside.addNotify();
                assertThat(field.isShowing()).isTrue();
                keys.own(field);
                owner.closeTab(second);
                assertThat(owner.selectedTab()).isSameAs(first);
                assertThat(focused[0]).as("the focus in a tool window or the palette stays there").isZero();
                keys.own(null);
                owner.closeTab(open(owner));
                assertThat(focused[0]).as("with the focus nowhere a person sees, the shown parent tab's tab takes it").isEqualTo(1);
            });
        } finally {
            edt(() -> {
                if (prior[0] != null) KeyboardFocusManager.setCurrentKeyboardFocusManager(prior[0]);
                outside.removeNotify();
            });
        }
    }
}
