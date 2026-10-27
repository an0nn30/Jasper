package dev.jasper.app.plugins;

import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.TerminalEvents;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class TerminalBridgeTest {
    private final List<Runnable> queued = new ArrayList<>();
    private final EventBus bus = new EventBus(queued::add, new Containment(() -> true));
    private final TerminalFixture fixture = new TerminalFixture();
    private final List<Object> heard = new ArrayList<>();

    private void deliver() { while (!queued.isEmpty()) queued.remove(0).run(); }

    @Test void aMoveAndAContentTabBecomeTheirTopics() {
        TerminalBridge.connect(fixture.registry, bus);
        for (var topic : List.of(TerminalEvents.TAB_OPENED, TerminalEvents.TAB_MOVED, TerminalEvents.TAB_CLOSED, TerminalEvents.PANE_CLOSED))
            bus.subscribe("dev.x.tool", topic, heard::add);
        UUID first = fixture.addWindow(), keep = fixture.addTab(first, "keep");
        fixture.addPane(keep, "zsh", null);
        UUID second = fixture.addWindow(), shell = fixture.addTab(second, "zsh");
        fixture.addPane(shell, "zsh", null);
        UUID notes = UUID.randomUUID();
        fixture.addContentTab(first, notes, () -> "notes.txt");
        deliver();
        assertThat(heard).endsWith(new TerminalEvents.TabEvent(first, notes));
        heard.clear();
        fixture.moveTab(notes, second);
        fixture.closeTab(notes);
        deliver();
        assertThat(heard).containsExactly(new TerminalEvents.TabMoved(notes, first, second), new TerminalEvents.TabEvent(second, notes));
    }

    @Test void everyRegistryFactBecomesItsTopic() {
        var bridge = TerminalBridge.connect(fixture.registry, bus);
        for (var topic : List.of(TerminalEvents.WINDOW_OPENED, TerminalEvents.WINDOW_ACTIVATED, TerminalEvents.TAB_OPENED, TerminalEvents.TAB_SELECTED,
                TerminalEvents.PANE_OPENED, TerminalEvents.PANE_FOCUSED, TerminalEvents.PANE_CLOSED, TerminalEvents.TAB_CLOSED, TerminalEvents.WINDOW_CLOSED,
                TerminalEvents.ACTIVE_PANE_CHANGED, TerminalEvents.TITLE_CHANGED, TerminalEvents.CWD_CHANGED, TerminalEvents.COMMAND_STARTED,
                TerminalEvents.COMMAND_FINISHED, TerminalEvents.SESSION_STATE_CHANGED, TerminalEvents.BELL))
            bus.subscribe("dev.x.tool", topic, heard::add);
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), pane = fixture.addPane(tab, "make", Path.of("/src"));
        fixture.activateWindow(window);
        fixture.registry.publish(new TerminalEvent.SessionStarted(pane));
        fixture.registry.publish(new TerminalEvent.TitleChanged(pane, "make test"));
        fixture.registry.publish(new TerminalEvent.DirectoryChanged(pane, Optional.of(Path.of("/src/app")), Optional.empty()));
        fixture.reportDirectory(pane, "build-host", "/srv/app");
        fixture.registry.publish(new TerminalEvent.CommandStarted(pane, "make test"));
        fixture.finishCommand(pane, "make test", 2);
        fixture.registry.publish(new TerminalEvent.Bell(tab, pane));
        fixture.registry.publish(new TerminalEvent.SessionExited(pane, OptionalInt.of(0)));
        fixture.focusPane(pane);
        assertThat(heard).as("queued like every event").isEmpty();
        deliver();
        assertThat(heard).containsExactly(
            new TerminalEvents.WindowEvent(window), new TerminalEvents.TabEvent(window, tab), new TerminalEvents.TabEvent(window, tab),
            new TerminalEvents.PaneEvent(tab, pane), new TerminalEvents.WindowEvent(window), new TerminalEvents.ActivePaneChanged(Optional.of(pane)),
            new TerminalEvents.SessionStateChanged(pane, SessionState.RUNNING, OptionalInt.empty()),
            new TerminalEvents.TitleChanged(pane, "make test"),
            new TerminalEvents.CwdChanged(pane, Optional.of(Path.of("/src/app")), Optional.empty()),
            new TerminalEvents.CwdChanged(pane, Optional.empty(), Optional.of(new dev.jasper.sdk.terminal.RemoteDirectory("build-host", "/srv/app"))),
            new TerminalEvents.CommandStarted(pane, "make test"),
            new TerminalEvents.CommandFinished(pane, "make test", OptionalInt.of(2), Duration.ofMillis(1500), Optional.empty(), Optional.of(new dev.jasper.sdk.terminal.RemoteDirectory("build-host", "/srv/app"))),
            new TerminalEvents.PaneEvent(tab, pane),
            new TerminalEvents.SessionStateChanged(pane, SessionState.EXITED, OptionalInt.of(0)),
            new TerminalEvents.PaneEvent(tab, pane));
        heard.clear();
        fixture.closePane(pane);
        deliver();
        assertThat(heard).as("the window keeps its empty workspace").containsExactly(new TerminalEvents.PaneEvent(tab, pane),
            new TerminalEvents.TabEvent(window, tab), new TerminalEvents.ActivePaneChanged(Optional.empty()));
        UUID other = fixture.addWindow(), moving = fixture.addTab(window, "next");
        deliver();
        heard.clear();
        fixture.moveTab(moving, other);
        deliver();
        assertThat(heard).as("a move that empties the window closes it").contains(new TerminalEvents.WindowEvent(window));
        int heardBefore = heard.size();
        bridge.close();
        fixture.addWindow();
        deliver();
        assertThat(heard).hasSize(heardBefore);
    }
}
