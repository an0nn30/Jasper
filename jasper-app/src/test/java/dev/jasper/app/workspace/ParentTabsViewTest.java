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
import dev.jasper.app.layout.Side;
import dev.jasper.app.terminals.OpenSpec;
import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.KeyboardFocusManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
        owner.newTab(HOME);
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
            TerminalContent opened = owner.newTab(HOME);
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
            var parents = owner.windowLayout();
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
            owner[0].split(Side.RIGHT);
            owner[0].newTab(HOME);
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
            assertThat(owner.windowLayout().parentTabs()).extracting(ParentTab::id).containsExactly(first, third);
            owner.closeTab(c);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            owner.closeTab(a);
            assertThat(owner.parentTabCount()).as("the main window keeps its only parent tab").isEqualTo(1);
            assertThat(owner.windowLayout().tabs()).isEmpty();
            assertThat(owner.workspace().emptyWorkspace()).as("as its empty workspace").isNotNull();
            owner.newTab(HOME);
            assertThat(owner.parentTabCount()).as("New Tab fills it rather than opening a second, empty one beside it")
                .isEqualTo(1);
            assertThat(owner.tabs()).hasSize(1);
        });
    }

    /** Full windows spec 5.3: a window's last tab closing leaves its empty workspace. */
    @Test void aWindowKeepsItsEmptyWorkspaceWhenItsLastParentTabsLastTabCloses() throws Exception {
        edt(() -> {
            var window = content(idleLauncher(pending), HOME, false);
            Tab a = window.selectedTab();
            Tab b = open(window);
            window.closeTab(a);
            assertThat(window.parentTabCount()).isEqualTo(1);
            window.closeTab(b);
            assertThat(window.closed()).as("the window stays").isFalse();
            assertThat(window.workspace().emptyWorkspace()).as("its empty workspace shows").isNotNull();
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
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
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

    /**
     * Review Focus 1 (tabs and panes spec 13; workspace plan 8a Rulings 3): whatever opens without a pane opens as a new
     * tab at the end of the strip, shown, and the main window's empty workspace takes the first in its place.
     */
    @Test void whatOpensWithoutAPaneOpensAsATabAndTheEmptyWorkspaceTakesTheFirst() throws Exception {
        edt(() -> {
            var main = content(idleLauncher(pending), HOME, true);
            var model = new Contributions();
            main.connectContributions(model);
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), new JPanel());
            assertThat(main.parentTabCount()).as("the empty workspace takes it: no empty tab is left beside it").isEqualTo(1);
            assertThat(main.selectedTab()).isSameAs(notes);
            assertThat(main.workspace().model().groups()).as("in one pane").hasSize(1);
            TerminalContent terminal = main.newTab(HOME);
            assertThat(main.parentTabCount()).as("New Tab, a request's folder, Terminals.openTab: a new tab").isEqualTo(2);
            assertThat(main.selectedTab().content()).isSameAs(terminal);
            assertThat(main.workspace().tabs()).containsExactly(main.selectedTab());
            Tab second = model.tabs().open("dev.example.editor", "second", "second.txt", main.id(), new JPanel());
            assertThat(main.parentTabCount()).as("a plugin's view placed without a pane").isEqualTo(3);
            assertThat(main.windowLayout().parentTabs().getLast().layout().tabs()).as("at the end of the strip").containsExactly(second.id());
            assertThat(main.activeParentTab()).as("shown").isEqualTo(main.parentTabOf(second).orElseThrow());
            Tab beside = model.tabs().open("dev.example.editor", "beside", "beside.txt", main.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            assertThat(main.parentTabOf(beside)).as("a split placement: a new pane of the shown tab").isEqualTo(main.parentTabOf(second));
            assertThat(main.workspace().model().groups()).hasSize(2);
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

    /** An item of a hidden tab is selected before its tab shows: one TabSelected, for it, and no other item turns active. */
    @Test void selectingAnItemOfAHiddenTabPublishesOneTabSelectedForIt() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            var registry = new TerminalRegistry();
            var facts = new ArrayList<TerminalEvent>();
            registry.onEvent(facts::add);
            owner.connectTerminals(registry, () -> { });
            Tab left = owner.selectedTab();
            owner.split(Side.RIGHT);
            Tab right = owner.selectedTab();
            ParentTabId behind = owner.activeParentTab();
            open(owner);
            assertThat(owner.activeParentTab()).as("the split tab hides, its right pane active").isNotEqualTo(behind);
            var heard = new ArrayList<Boolean>();
            right.onActive(heard::add);
            facts.clear();
            owner.selectTab(left);
            assertThat(facts).filteredOn(TerminalEvent.TabSelected.class::isInstance).as("one TabSelected, for the item selected")
                .containsExactly(new TerminalEvent.TabSelected(owner.id(), left.id().value()));
            assertThat(heard).as("the hidden tab's active item never turns active on the way").isEmpty();
            assertThat(owner.activeParentTab()).isEqualTo(behind);
            assertThat(owner.selectedTab()).isSameAs(left);
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

    /** Workspace plan 7a Rulings 10: a person's switch focuses the shown parent tab's active tab. */
    @Test void aSwitchFocusesTheShownParentTabsActiveTab() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            Tab first = owner.selectedTab();
            ParentTabId one = owner.activeParentTab();
            Tab second = open(owner);
            ParentTabId two = owner.activeParentTab();
            int[] focused = {0, 0};
            first.focusTarget(() -> focused[0]++);
            second.focusTarget(() -> focused[1]++);
            owner.selectParentTab(one);
            assertThat(focused).as("the tab of the parent tab that shows now takes the focus").containsExactly(1, 0);
            owner.selectParentTab(two);
            assertThat(focused).containsExactly(1, 1);
        });
    }

    /** Workspace plan 7a Rulings 3: a new parent tab goes at the end of the bar, whichever shows; the views follow the bar. */
    @Test void aNewParentTabGoesAtTheEndOfTheBarAndTheViewsFollowTheBar() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            ParentTabId first = owner.activeParentTab();
            open(owner);
            ParentTabId second = owner.activeParentTab();
            owner.selectParentTab(first);
            open(owner);
            ParentTabId third = owner.activeParentTab();
            var parents = owner.parentTabs();
            assertThat(parents.model().parentTabs()).extracting(ParentTab::id).as("at the end, not after the one that showed")
                .containsExactly(first, second, third);
            assertThat(Arrays.stream(parents.getComponents()).filter(WorkspaceView.class::isInstance)
                .map(view -> ((WorkspaceView) view).id()).toList()).as("the views in bar order").containsExactly(first, second, third);
        });
    }

    /** A new parent tab's workspace takes the window's theme and glass, and so do the splits it builds later. */
    @Test void aNewParentTabTakesTheWindowsThemeAndGlass() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.applyBackgroundOpacity(0.5);
            open(owner);
            WorkspaceView view = owner.workspace();
            assertThat(view.isOpaque()).as("under glass it paints nothing behind its tabs").isFalse();
            assertThat(view.getBackground()).as("the theme's surface").isEqualTo(owner.theme().surface());
            owner.split(Side.RIGHT);
            SplitContainer split = view.split(List.of());
            assertThat(split).as("its split").isNotNull();
            assertThat(split.isOpaque()).isFalse();
            assertThat(split.getBackground()).isEqualTo(owner.theme().surface());
        });
    }

    /** Workspace plan 7a Rulings 21: PaneHandle.split beside a hidden parent tab's terminal opens there and shows nothing new. */
    @Test void aSplitBesideAHiddenParentTabsTerminalOpensThereAndLeavesTheShownOneShown() throws Exception {
        var launcher = new ShellLauncher(Runnable::run, directory -> IdleTerminalSession.start(), "idle-test-session");
        var registry = new TerminalRegistry();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            owner[0] = content(launcher);
            owner[0].connectTerminals(registry, () -> { });
            owner[0].newTab(HOME);
        });
        edt(() -> { });
        edt(() -> {
            var window = owner[0];
            Tab hidden = window.tabs().getFirst();
            ParentTabId behind = window.parentTabOf(hidden).orElseThrow(), shown = window.activeParentTab();
            Tab selected = window.selectedTab();
            assertThat(behind).isNotEqualTo(shown);
            PaneEntry pane = registry.pane(((TerminalContent) hidden.content()).pane().id()).orElseThrow();
            Optional<PaneEntry> split = pane.split().apply(Side.RIGHT, new OpenSpec.Local(Optional.empty()));
            assertThat(split).as("the SDK's split").isPresent();
            Tab opened = window.tabs().stream().filter(tab -> tab.id().value().equals(split.get().tabId())).findFirst().orElseThrow();
            assertThat(window.parentTabOf(opened)).as("in the hidden parent tab").contains(behind);
            assertThat(window.parentTabs().view(behind).orElseThrow().model().groups()).as("in a pane beside its terminal's").hasSize(2);
            assertThat(window.activeParentTab()).as("the shown parent tab stays shown").isEqualTo(shown);
            assertThat(window.selectedTab()).isSameAs(selected);
        });
    }
}
