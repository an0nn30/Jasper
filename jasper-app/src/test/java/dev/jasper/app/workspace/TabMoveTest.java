package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Side;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Workspace tabs spec 9.3 and 17: a tab moved between windows through the registry keeps its session. */
class TabMoveTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private WindowContent source, target;

    @BeforeEach void windows() throws Exception {
        edt(() -> {
            source = content(idleLauncher(pending));
            target = content(idleLauncher(pending));
            source.connectContributions(model);
            target.connectContributions(model);
            source.newTab(HOME);
        });
        while (!pending.isEmpty()) pending.remove().run();
    }

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void aTerminalTabMovedBetweenWindowsKeepsItsSession() throws Exception {
        edt(() -> {
            Tab tab = source.selectedTab();
            var terminal = (TerminalContent) tab.content();
            var pane = terminal.pane();
            var session = pane.session(); var view = pane.view();
            assertThat(session).as("its shell started").isNotNull();
            model.tabs().move(tab, target.id());
            assertThat(tab.window()).isEqualTo(target.id());
            assertThat(source.tabs()).doesNotContain(tab);
            assertThat(source.tabCount()).as("the source keeps its other tab").isEqualTo(1);
            assertThat(target.tabs()).contains(tab);
            assertThat(target.selectedTab()).as("it arrives selected in the active group").isSameAs(tab);
            assertThat(SwingUtilities.isDescendingFrom(terminal, target.workspace())).isTrue();
            assertThat(SwingUtilities.isDescendingFrom(terminal, source)).isFalse();
            assertThat(pane.session()).as("the same session").isSameAs(session);
            assertThat(pane.view()).isSameAs(view);
            assertThat(session.exitFuture()).as("still running").isNotDone();
            assertThat(tab.closed()).isFalse();
        });
    }

    @Test void aMovedTabReportsToItsNewWindow() throws Exception {
        edt(() -> {
            var registry = new TerminalRegistry();
            var facts = new ArrayList<TerminalEvent>();
            registry.onEvent(facts::add);
            source.connectTerminals(registry, () -> { });
            target.connectTerminals(registry, () -> { });
            var before = new ArrayList<WorkspaceActivity.Event>(); var after = new ArrayList<WorkspaceActivity.Event>();
            source.activity(before::add); target.activity(after::add);
            before.clear(); after.clear(); facts.clear();
            Tab tab = source.selectedTab();
            var pane = ((TerminalContent) tab.content()).pane();
            model.tabs().move(tab, target.id());
            assertThat(after).as("a moved pane is not opened again").isEmpty();
            assertThat(facts).as("one move, reported where it arrives").filteredOn(TerminalEvent.TabMoved.class::isInstance)
                .containsExactly(new TerminalEvent.TabMoved(tab.id().value(), source.id(), target.id()));
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.PaneOpened || fact instanceof TerminalEvent.PaneClosed);
            assertThat(registry.tab(tab.id().value()).orElseThrow().windowId()).isEqualTo(target.id());
            pane.onTitleChanged.accept("moved");
            assertThat(after).containsExactly(new WorkspaceActivity.TitleChanged(pane.id(), "moved"));
            assertThat(before).isEmpty();
            tab.close();
            assertThat(after).contains(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.CLOSED));
            assertThat(facts).contains(new TerminalEvent.PaneClosed(tab.id().value(), pane.id()),
                new TerminalEvent.TabClosed(target.id(), tab.id().value()));
        });
    }

    /** Workspace plan 3b Rulings 5: a window that announced an arrival and then fails to show the tab takes it back. */
    @Test void anArrivalItsWindowFailsToShowIsTakenBack() throws Exception {
        edt(() -> {
            var registry = new TerminalRegistry();
            var facts = new ArrayList<TerminalEvent>();
            registry.onEvent(facts::add);
            source.connectTerminals(registry, () -> { });
            target.connectTerminals(registry, () -> { });
            Tab tab = source.selectedTab();
            UUID id = tab.id().value();
            target.onTitle = title -> { target.onTitle = ignored -> { }; throw new IllegalStateException("title failed"); };
            facts.clear();
            assertThatIllegalStateException().isThrownBy(() -> model.tabs().move(tab, target.id())).withMessage("title failed");
            assertThat(tab.window()).as("back where it was").isEqualTo(source.id());
            assertThat(source.tabs()).contains(tab);
            assertThat(target.tabs()).doesNotContain(tab);
            assertThat(facts).as("the move there and back").filteredOn(TerminalEvent.TabMoved.class::isInstance)
                .containsExactly(new TerminalEvent.TabMoved(id, source.id(), target.id()), new TerminalEvent.TabMoved(id, target.id(), source.id()));
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
            assertThat(registry.tab(id).orElseThrow().windowId()).isEqualTo(source.id());
            facts.clear();
            tab.close();
            assertThat(facts).contains(new TerminalEvent.TabClosed(source.id(), id));
            target.onTitle = title -> { target.onTitle = ignored -> { }; throw new IllegalStateException("title failed"); };
            facts.clear();
            assertThatIllegalStateException().isThrownBy(() -> model.tabs().open("dev.example.editor", "notes", "notes.txt",
                target.id(), new javax.swing.JPanel())).withMessage("title failed");
            var opened = facts.stream().filter(TerminalEvent.TabOpened.class::isInstance).map(TerminalEvent.TabOpened.class::cast).toList();
            assertThat(opened).singleElement().extracting(TerminalEvent.TabOpened::windowId).isEqualTo(target.id());
            assertThat(facts).as("an open the window failed to show closes").containsSubsequence(opened.getFirst(),
                new TerminalEvent.TabClosed(target.id(), opened.getFirst().tabId()));
            assertThat(facts).as("closed once").filteredOn(fact -> fact instanceof TerminalEvent.TabClosed closed && closed.tabId().equals(opened.getFirst().tabId()))
                .hasSize(1);
        });
    }

    @Test void goToFollowsAMovedTab() throws Exception {
        edt(() -> {
            int[] raised = new int[2];
            var registry = new TerminalRegistry();
            source.connectTerminals(registry, () -> raised[0]++);
            target.connectTerminals(registry, () -> raised[1]++);
            var finished = new ArrayList<WorkspaceActivity.Finished>();
            source.activity(event -> { if (event instanceof WorkspaceActivity.Finished done) finished.add(done); });
            Tab tab = source.selectedTab();
            var pane = ((TerminalContent) tab.content()).pane();
            pane.onCommandFinished.accept("make", OptionalInt.of(0), Duration.ofSeconds(3), Optional.empty(), Optional.empty());
            assertThat(finished).hasSize(1);
            model.tabs().move(tab, target.id());
            target.selectTab(target.tabs().get(0));
            assertThat(target.selectedTab()).isNotSameAs(tab);
            raised[0] = 0; raised[1] = 0;
            finished.getFirst().activate().run();
            assertThat(target.selectedTab()).as("the tab is selected where it is now").isSameAs(tab);
            assertThat(raised[1]).as("its window comes to the front").isEqualTo(1);
            assertThat(raised[0]).as("the old window is left alone").isZero();
            assertThat(source.tabs()).doesNotContain(tab);
        });
    }

    @Test void aHostedTabMovesTooAndItsGuardStillAnswers() throws Exception {
        edt(() -> {
            var panel = new javax.swing.JPanel();
            Tab tab = model.tabs().open("dev.example.editor", "notes", "notes.txt", source.id(), panel);
            assertThat(source.selectedContent()).isSameAs(tab);
            model.tabs().move(tab, target.id());
            assertThat(target.selectedContent()).isSameAs(tab);
            assertThat(tab.component()).isSameAs(panel);
            int[] asked = {0};
            model.tabs().guard("dev.example.editor", (tabs, batch) -> { asked[0]++; return java.util.concurrent.CompletableFuture.completedFuture(false); });
            target.closeTab(tab);
            assertThat(asked[0]).isEqualTo(1);
            assertThat(target.tabs()).contains(tab);
            assertThat(target.mayClose()).as("the guard still answers for the window it is in").isFalse();
        });
    }

    @Test void aMoveEndsTheMaximizeOnBothSidesAndClosingATabKeepsIt() throws Exception {
        edt(() -> { source.newTab(HOME); source.newTab(HOME); target.newTab(HOME); });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            var from = source.workspace(); var to = target.workspace();
            var moving = source.tabs();
            from.moveToNewGroup(moving.get(3), from.model().activeGroup(), Side.RIGHT);
            source.selectTab(moving.get(0));
            from.toggleMaximize();
            var maximized = from.model().maximized();
            assertThat(maximized).contains(from.model().groupOf(moving.get(0).id()).orElseThrow());
            source.closeTab(moving.get(0));
            assertThat(from.model().maximized()).as("closing one of its tabs keeps the maximize").isEqualTo(maximized);
            var staying = target.tabs();
            to.moveToNewGroup(staying.get(1), to.model().activeGroup(), Side.RIGHT);
            to.toggleMaximize();
            assertThat(to.model().maximized()).isPresent();
            model.tabs().move(moving.get(1), target.id());
            assertThat(from.model().maximized()).as("its group lost a tab by a move").isEmpty();
            assertThat(to.model().maximized()).as("the active group gained one").isEmpty();
            assertThat(target.selectedTab()).isSameAs(moving.get(1));
        });
    }

    /** Workspace plan 4a (plan 3a's final review): a moved terminal tab's pane asks the window it arrived in. */
    @Test void aMovedTerminalTabsPaneAsksTheWindowItArrivedIn() throws Exception {
        edt(() -> {
            var failures = new ArrayList<String>();
            source.onLaunchFailure = (details, cause) -> failures.add("source " + details);
            target.onLaunchFailure = (details, cause) -> failures.add("target " + details);
            Tab tab = source.selectedTab();
            var terminal = (TerminalContent) tab.content();
            int sourceTabs = source.tabCount();
            model.tabs().move(tab, target.id());
            assertThat(terminal.lastInWindow.getAsBoolean()).as("the target holds another tab").isFalse();
            target.closeTab(target.tabs().getFirst());
            assertThat(terminal.lastInWindow.getAsBoolean()).as("now it is the target's only tab").isTrue();
            terminal.onError.accept("launch failed", null);
            assertThat(failures).containsExactly("target launch failed");
            terminal.onEmpty.run();
            assertThat(tab.closed()).as("closed from the window it is in").isTrue();
            assertThat(target.tabs()).doesNotContain(tab);
            assertThat(source.tabCount()).as("the source is untouched").isEqualTo(sourceTabs - 1);
        });
    }

    @Test void aTabThatArrivesByAMoveIsShownWithoutTheOpenMotion() throws Exception {
        edt(() -> {
            Tab tab = source.selectedTab();
            assertThat(source.workspace().moved(tab)).as("it opened here").isFalse();
            model.tabs().move(tab, target.id());
            assertThat(target.workspace().moved(tab)).as("it arrived by a move (Rulings 27)").isTrue();
            assertThat(source.workspace().moved(tab)).isFalse();
        });
    }
}
