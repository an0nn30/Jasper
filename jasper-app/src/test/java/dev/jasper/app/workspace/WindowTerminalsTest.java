package dev.jasper.app.workspace;

import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.SplitAxis;
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
            PaneEntry pane = tab.panes().get().get(0);
            assertThat(window.id()).isEqualTo(owner.id());
            assertThat(window.selectedTab().get()).contains(tab);
            assertThat(tab.focusedPane().get()).contains(pane);
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
            assertThat(pane.split().apply(SplitAxis.RIGHT, Optional.empty())).as("nothing runs there yet").isEmpty();
            registry.windowActivated(window.id());
            assertThat(registry.activePane()).contains(pane);
            window.toFront().run();
            assertThat(fronted).isEqualTo(1);
        });
    }

    @Test void tabsSplitsInjectionAndExitAreReported() throws Exception {
        open();
        UUID windowId = owner.id();
        PaneEntry first = onEdt(() -> registry.windows().get(0).tabs().get().get(0).panes().get().get(0));
        pending.remove().run();
        until(() -> first.snapshot().get().state() == PaneSnapshot.State.RUNNING);
        edt(() -> {
            assertThat(events).contains(new TerminalEvent.SessionStarted(first.id()));
            assertThat(first.snapshot().get().columns()).isPositive();
        });

        Optional<PaneEntry> split = onEdt(() -> first.split().apply(SplitAxis.DOWN, Optional.empty()));
        assertThat(split).isPresent();
        edt(() -> {
            assertThat(events).contains(new TerminalEvent.PaneOpened(first.tabId(), split.get().id()));
            assertThat(registry.tab(first.tabId()).orElseThrow().panes().get()).containsExactly(first, split.get());
        });

        Optional<PaneEntry> opened = onEdt(() -> registry.window(windowId).orElseThrow().openTab().apply(Optional.of(HOME)));
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
            owner.closeTab(owner.currentTab());
            assertThat(events).containsExactly(new TerminalEvent.WindowActivated(windowId),
                new TerminalEvent.ActivePaneChanged(Optional.of(opened.get().id())),
                new TerminalEvent.PaneClosed(secondTab, opened.get().id()), new TerminalEvent.TabClosed(windowId, secondTab),
                new TerminalEvent.TabSelected(windowId, first.tabId()),
                new TerminalEvent.ActivePaneChanged(registry.activePane().map(PaneEntry::id)));
            events.clear();
            owner.close();
            assertThat(events.get(events.size() - 1)).isEqualTo(new TerminalEvent.WindowClosed(windowId));
            assertThat(events).contains(new TerminalEvent.TabClosed(windowId, first.tabId()));
            assertThat(registry.windows()).isEmpty();
        });
    }
}
