package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakeTerminalsTest {
    private static PaneInfo info(String title) {
        return new PaneInfo(title, Optional.of(Path.of("/src")), Optional.empty(), 80, 24, true, SessionKind.LOCAL, Optional.empty(),
            SessionState.RUNNING, OptionalInt.empty());
    }

    @Test void theHostScriptsAWorkspaceThatActionContextsSee() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "build"), pane = host.addTerminalPane(tab, info("make"));
            host.activateTerminalWindow(window);
            host.setSelection(pane, "selected");
            host.setForegroundJob(pane, "vim");
            var seen = new java.util.ArrayList<PaneHandle>();
            var context = host.start(new PluginInfo("dev.x.tool", "Tool", "1.0.0",
                Set.of(Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT)), Set.of(), Set.of(), plugin ->
                plugin.actions().register(dev.jasper.sdk.ui.ActionSpec.of("dev.x.tool.type", "Type"), invoked -> seen.add(invoked.pane().orElseThrow())));
            assertThat(context).isNotNull();
            assertThat(host.invoke("dev.x.tool.type", window, pane)).isTrue();
            PaneHandle handle = seen.get(0);
            assertThat(handle.info()).isEqualTo(info("make"));
            assertThat(handle.foregroundJob()).isCompletedWithValue(Optional.of("vim"));
            assertThatThrownBy(handle::selection).isInstanceOf(MissingCapabilityException.class);
            handle.sendText("make test\n");
            handle.paste("pasted");
            assertThat(host.sent(pane)).containsExactly("write:make test\n", "paste:pasted");
            host.setPaneInfo(pane, info("make test"));
            assertThat(handle.info().title()).isEqualTo("make test");
            host.closeTerminalPane(pane);
            assertThat(handle.isOpen()).isFalse();
            assertThat(handle.info().title()).isEqualTo("make test");
            assertThat(host.sent(pane)).as("kept for the test to read after the pane closed").hasSize(2);
            assertThat(host.openRequests()).isEmpty();
        }
    }

    @Test void terminalTopicsNeedObserveAndTheDriversPublishThem() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "build"), pane = host.addTerminalPane(tab, info("make"));
            var heard = new java.util.ArrayList<Object>();
            host.start(new PluginInfo("dev.x.blind", "Blind", "1.0.0", Set.of()), Set.of(), Set.of(), plugin ->
                assertThatThrownBy(() -> plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.BELL, heard::add))
                    .isInstanceOf(MissingCapabilityException.class));
            host.start(new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of(Capabilities.TERMINAL_OBSERVE)), Set.of(), Set.of(), plugin -> {
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.COMMAND_FINISHED, heard::add);
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.TITLE_CHANGED, heard::add);
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.SESSION_STATE_CHANGED, heard::add);
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.ACTIVE_PANE_CHANGED, heard::add);
            });
            assertThat(host.failures()).isEmpty();
            host.flush();
            heard.clear();
            host.activateTerminalWindow(window);
            host.titleChanged(pane, "make test");
            host.commandFinished(pane, "make test", OptionalInt.of(2), java.time.Duration.ofSeconds(3));
            host.sessionExited(pane, OptionalInt.of(0));
            assertThat(heard).as("delivered by flush, like every event").isEmpty();
            host.flush();
            assertThat(heard).containsExactly(new dev.jasper.sdk.terminal.TerminalEvents.ActivePaneChanged(Optional.of(pane)),
                new dev.jasper.sdk.terminal.TerminalEvents.TitleChanged(pane, "make test"),
                new dev.jasper.sdk.terminal.TerminalEvents.CommandFinished(pane, "make test", OptionalInt.of(2), java.time.Duration.ofSeconds(3),
                    Optional.of(Path.of("/src")), Optional.empty()),
                new dev.jasper.sdk.terminal.TerminalEvents.SessionStateChanged(pane, SessionState.EXITED, OptionalInt.of(0)));
        }
    }
}
