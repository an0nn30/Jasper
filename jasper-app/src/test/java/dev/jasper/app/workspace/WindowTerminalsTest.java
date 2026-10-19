package dev.jasper.app.workspace;

import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.layout.Side;
import dev.jasper.app.terminals.TabEntry;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.WindowEntry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import dev.jasper.app.terminals.OpenSpec;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class WindowTerminalsTest {
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private final List<TerminalEvent> events = new ArrayList<>();
    private TerminalRegistry registry;
    private WindowContent owner;
    private int fronted;

    @AfterEach void close() throws Exception { closeOwners(); }

    private void open() throws Exception {
        edt(() -> {
            owner = content(launcher(pending));
            registry = new TerminalRegistry();
            registry.onEvent(events::add);
            owner.connectTerminals(registry, () -> fronted++);
            owner.connectTerminals(registry, () -> fronted++);
        });
    }

    private <T> T onEdt(java.util.function.Supplier<T> query) throws Exception {
        Object[] result = new Object[1];
        edt(() -> result[0] = query.get());
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }

    @Test void aWindowAnnouncesItselfAndAnswersQueriesBeforeAnyShellRuns() throws Exception {
        open();
        edt(() -> {
            WindowEntry window = registry.windows().get(0);
            TabEntry tab = window.tabs().get().get(0);
            PaneEntry pane = tab.terminal().get().orElseThrow();
            assertThat(window.id()).isEqualTo(owner.id());
            assertThat(window.selectedTab().get()).contains(tab);
            assertThat(tab.terminal().get()).contains(pane);
            assertThat(tab.kind()).isEqualTo(TabEntry.Kind.TERMINAL);
            assertThat(tab.windowId()).isEqualTo(window.id());
            assertThat(pane.tabId()).isEqualTo(tab.id());
            assertThat(events).containsExactly(new TerminalEvent.WindowOpened(window.id()), new TerminalEvent.TabOpened(window.id(), tab.id()),
                new TerminalEvent.PaneOpened(tab.id(), pane.id()), new TerminalEvent.TabSelected(window.id(), tab.id()));
            PaneSnapshot starting = pane.snapshot().get();
            assertThat(starting.state()).isEqualTo(PaneSnapshot.State.STARTING);
            assertThat(starting.columns()).isZero();
            assertThat(starting.workingDirectory()).contains(HOME);
            pane.write().accept("ignored".getBytes(StandardCharsets.UTF_8));
            pane.paste().accept("ignored");
            assertThat(pane.selection().get()).isEmpty();
            assertThat(pane.foregroundJob().get()).isCompletedWithValue(Optional.empty());
            assertThat(pane.split().apply(Side.RIGHT, new OpenSpec.Local(Optional.empty()))).as("nothing runs there yet").isEmpty();
            registry.windowActivated(window.id());
            assertThat(registry.activePane()).contains(pane);
            window.toFront().run();
            assertThat(fronted).isEqualTo(1);
        });
    }

    @Test void aPluginsTabIsListedOpenedMovedAndClosedLikeATerminalTab() throws Exception {
        open();
        WindowContent[] other = new WindowContent[1];
        edt(() -> {
            var model = new dev.jasper.app.contributions.Contributions();
            owner.connectContributions(model);
            other[0] = content(launcher(pending));
            other[0].connectContributions(model);
            other[0].connectTerminals(registry, () -> { });
            UUID windowId = owner.id(), otherId = other[0].id();
            registry.windowActivated(windowId);
            events.clear();
            var view = model.tabs().open("dev.example.editor", "notes", "notes.txt", windowId, new javax.swing.JPanel());
            UUID tabId = view.id().value();
            assertThat(events).containsSubsequence(new TerminalEvent.TabOpened(windowId, tabId), new TerminalEvent.TabSelected(windowId, tabId),
                new TerminalEvent.ActivePaneChanged(Optional.empty()));
            WindowEntry window = registry.window(windowId).orElseThrow();
            assertThat(window.tabs().get()).extracting(TabEntry::kind).containsExactly(TabEntry.Kind.TERMINAL, TabEntry.Kind.CONTENT);
            TabEntry entry = window.selectedTab().get().orElseThrow();
            assertThat(entry.id()).isEqualTo(tabId);
            assertThat(entry.terminal().get()).isEmpty();
            assertThat(entry.title().get()).isEqualTo("notes.txt");
            assertThat(registry.activePane()).as("a plugin's tab has no terminal").isEmpty();
            events.clear();
            model.tabs().move(view, otherId);
            assertThat(events).filteredOn(TerminalEvent.TabMoved.class::isInstance).containsExactly(new TerminalEvent.TabMoved(tabId, windowId, otherId));
            assertThat(events).contains(new TerminalEvent.TabSelected(otherId, tabId));
            assertThat(events).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
            assertThat(registry.tab(tabId).orElseThrow().windowId()).isEqualTo(otherId);
            events.clear();
            view.close();
            assertThat(events).contains(new TerminalEvent.TabClosed(otherId, tabId));
        });
    }

    @Test void tabsSplitsInjectionAndExitAreReported() throws Exception {
        open();
        UUID windowId = owner.id();
        PaneEntry first = onEdt(() -> registry.windows().get(0).tabs().get().get(0).terminal().get().orElseThrow());
        pending.remove().run();
        until(() -> first.snapshot().get().state() == PaneSnapshot.State.RUNNING);
        edt(() -> {
            assertThat(events).contains(new TerminalEvent.SessionStarted(first.id()));
            assertThat(first.snapshot().get().columns()).isPositive();
        });

        Optional<PaneEntry> split = onEdt(() -> first.split().apply(Side.DOWN, new OpenSpec.Local(Optional.empty())));
        assertThat(split).isPresent();
        edt(() -> {
            UUID splitTab = split.get().tabId();
            assertThat(splitTab).as("a split is a terminal tab of its own").isNotEqualTo(first.tabId());
            assertThat(events).contains(new TerminalEvent.TabOpened(windowId, splitTab), new TerminalEvent.PaneOpened(splitTab, split.get().id()));
            assertThat(registry.tab(splitTab).orElseThrow().terminal().get()).contains(split.get());
            assertThat(registry.tab(first.tabId()).orElseThrow().terminal().get()).contains(first);
            assertThat(owner.workspace().model().groups()).as("in a new group below").hasSize(2);
        });

        Optional<PaneEntry> opened = onEdt(() -> registry.window(windowId).orElseThrow().openTab().apply(new OpenSpec.Local(Optional.of(HOME))));
        assertThat(opened).isPresent();
        UUID secondTab = opened.get().tabId();
        edt(() -> {
            int at = events.indexOf(new TerminalEvent.TabOpened(windowId, secondTab));
            assertThat(events.subList(at, at + 3)).containsExactly(new TerminalEvent.TabOpened(windowId, secondTab),
                new TerminalEvent.PaneOpened(secondTab, opened.get().id()), new TerminalEvent.TabSelected(windowId, secondTab));
            assertThat(registry.window(windowId).orElseThrow().selectedTab().get().map(TabEntry::id)).contains(secondTab);
        });

        edt(() -> first.write().accept("bye\n".getBytes(StandardCharsets.UTF_8)));
        until(() -> events.contains(new TerminalEvent.SessionExited(first.id(), OptionalInt.of(0))));
        edt(() -> {
            assertThat(first.snapshot().get().state()).isEqualTo(PaneSnapshot.State.EXITED);
            assertThat(first.snapshot().get().exitStatus()).hasValue(0);
            assertThat(registry.pane(first.id())).as("the pane stays open after its shell exits").isPresent();
        });

        edt(() -> {
            events.clear();
            registry.windowActivated(windowId);
            owner.closeTab(owner.currentTerminal());
            assertThat(events).containsExactly(new TerminalEvent.WindowActivated(windowId),
                new TerminalEvent.ActivePaneChanged(Optional.of(opened.get().id())),
                new TerminalEvent.PaneClosed(secondTab, opened.get().id()), new TerminalEvent.TabClosed(windowId, secondTab),
                new TerminalEvent.TabSelected(windowId, split.get().tabId()),
                new TerminalEvent.ActivePaneChanged(registry.activePane().map(PaneEntry::id)));
            events.clear();
            owner.close();
            assertThat(events.get(events.size() - 1)).isEqualTo(new TerminalEvent.WindowClosed(windowId));
            assertThat(events).contains(new TerminalEvent.TabClosed(windowId, first.tabId()));
            assertThat(registry.windows()).isEmpty();
        });
    }
}
