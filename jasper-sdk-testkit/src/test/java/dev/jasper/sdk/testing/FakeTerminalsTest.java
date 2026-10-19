package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakeTerminalsTest {
    static PaneInfo info(String title) {
        return new PaneInfo(title, Optional.of(Path.of("/src")), Optional.empty(), 80, 24, true, SessionKind.LOCAL, Optional.empty(),
            SessionState.RUNNING, OptionalInt.empty(), "zsh");
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
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.CWD_CHANGED, heard::add);
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
            host.remoteCwdChanged(pane, "build-host", "/srv/app");
            assertThat(heard).as("delivered by flush, like every event").isEmpty();
            host.flush();
            assertThat(heard).containsExactly(new dev.jasper.sdk.terminal.TerminalEvents.ActivePaneChanged(Optional.of(pane)),
                new dev.jasper.sdk.terminal.TerminalEvents.TitleChanged(pane, "make test"),
                new dev.jasper.sdk.terminal.TerminalEvents.CommandFinished(pane, "make test", OptionalInt.of(2), java.time.Duration.ofSeconds(3),
                    Optional.of(Path.of("/src")), Optional.empty()),
                new dev.jasper.sdk.terminal.TerminalEvents.SessionStateChanged(pane, SessionState.EXITED, OptionalInt.of(0)),
                new dev.jasper.sdk.terminal.TerminalEvents.CwdChanged(pane, Optional.empty(), Optional.of(new dev.jasper.sdk.terminal.RemoteDirectory("build-host", "/srv/app"))));
        }
    }

    /** Workspace tabs spec 9.4: a terminal tab holds one pane, and a split is a terminal tab of its own. */
    @Test void aTerminalTabHoldsOnePaneAndASplitOpensATabOfItsOwn() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "build"), pane = host.addTerminalPane(tab, info("make"));
            assertThatIllegalStateException().isThrownBy(() -> host.addTerminalPane(tab, info("top"))).withMessageContaining("one pane");
            var context = host.start(new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of(Capabilities.TERMINAL_OPEN)), Set.of(), Set.of(), plugin -> { });
            var terminals = context.terminals();
            var tabHandle = terminals.tab(tab).orElseThrow();
            assertThat(tabHandle.kind()).isEqualTo(dev.jasper.sdk.terminal.TabHandle.Kind.TERMINAL);
            assertThat(tabHandle.terminal().map(PaneHandle::id)).contains(pane);
            PaneHandle split = terminals.split(terminals.pane(pane).orElseThrow(), dev.jasper.sdk.terminal.Direction.RIGHT,
                OpenRequest.local()).orElseThrow();
            assertThat(split.tab().id()).as("a split is a terminal tab of its own").isNotEqualTo(tab);
            assertThat(split.tab().window().id()).isEqualTo(window);
            assertThat(terminals.window(window).orElseThrow().tabs()).extracting(dev.jasper.sdk.terminal.TabHandle::id)
                .containsExactly(tab, split.tab().id());
            assertThat(terminals.window(window).orElseThrow().activeTab()).as("and it is selected").contains(split.tab());
            assertThat(host.openRequests()).containsExactly("split|" + pane + "|RIGHT|-");
            host.closeTerminalPane(pane);
            assertThat(tabHandle.isOpen()).as("a pane closes with its tab").isFalse();
            assertThat(tabHandle.terminal()).isEmpty();
            assertThat(tabHandle.kind()).as("a closed tab keeps its kind").isEqualTo(dev.jasper.sdk.terminal.TabHandle.Kind.TERMINAL);
        }
    }

    /** Workspace tabs spec 14: scripted content tabs and moves are tabs of their windows, announced on the tab topics. */
    @Test void scriptedContentTabsAndMovesAreTabsOfTheirWindows() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), shell = host.addTerminalTab(window, "zsh"), pane = host.addTerminalPane(shell, info("zsh"));
            var heard = new java.util.ArrayList<Object>();
            var context = host.start(new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of(Capabilities.TERMINAL_OBSERVE)), Set.of(), Set.of(), plugin -> {
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.TAB_OPENED, heard::add);
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.TAB_MOVED, heard::add);
            });
            host.flush();
            heard.clear();
            UUID notes = host.addContentTab(window, "notes.txt");
            UUID other = host.addTerminalWindow();
            host.moveTab(shell, other);
            host.flush();
            assertThat(heard).containsExactly(new dev.jasper.sdk.terminal.TerminalEvents.TabEvent(window, notes),
                new dev.jasper.sdk.terminal.TerminalEvents.TabMoved(shell, window, other));
            var terminals = context.terminals();
            assertThat(terminals.window(window).orElseThrow().tabs()).extracting(dev.jasper.sdk.terminal.TabHandle::id).containsExactly(notes);
            assertThat(terminals.tab(notes).orElseThrow().kind()).isEqualTo(dev.jasper.sdk.terminal.TabHandle.Kind.CONTENT);
            assertThat(terminals.tab(notes).orElseThrow().terminal()).isEmpty();
            assertThat(terminals.pane(pane).orElseThrow().tab().window().id()).as("the pane follows its tab").isEqualTo(other);
            assertThatIllegalArgumentException().isThrownBy(() -> host.addTerminalPane(notes, info("pane")));
        }
    }

    /** As in the application: a move that empties the active window reports no active pane as that window closes. */
    @Test void aMoveThatEmptiesTheActiveWindowReportsNoActivePaneAsItCloses() {
        try (var host = new FakePluginHost()) {
            UUID first = host.addTerminalWindow(), moving = host.addTerminalTab(first, "build");
            host.addTerminalPane(moving, info("make"));
            UUID second = host.addTerminalWindow(), other = host.addTerminalTab(second, "zsh");
            host.addTerminalPane(other, info("zsh"));
            host.activateTerminalWindow(first);
            var heard = new java.util.ArrayList<Object>();
            host.start(new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of(Capabilities.TERMINAL_OBSERVE)), Set.of(), Set.of(), plugin -> {
                for (var topic : List.of(dev.jasper.sdk.terminal.TerminalEvents.WINDOW_CLOSED, dev.jasper.sdk.terminal.TerminalEvents.TAB_MOVED,
                        dev.jasper.sdk.terminal.TerminalEvents.TAB_SELECTED, dev.jasper.sdk.terminal.TerminalEvents.ACTIVE_PANE_CHANGED))
                    plugin.events().subscribe(topic, heard::add);
            });
            host.flush();
            heard.clear();
            host.moveTab(moving, second);
            host.flush();
            assertThat(heard).containsExactly(new dev.jasper.sdk.terminal.TerminalEvents.WindowEvent(first),
                new dev.jasper.sdk.terminal.TerminalEvents.ActivePaneChanged(Optional.empty()),
                new dev.jasper.sdk.terminal.TerminalEvents.TabMoved(moving, first, second),
                new dev.jasper.sdk.terminal.TerminalEvents.TabEvent(second, moving));
        }
    }

    /** As the application's window does on every selection: a content tab is active while it is its window's selected tab. */
    @Test void aContentTabIsActiveWhileItIsItsWindowsSelectedTab() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try (var host = new FakePluginHost()) {
                UUID window = host.addTerminalWindow(), shell = host.addTerminalTab(window, "zsh"), pane = host.addTerminalPane(shell, info("zsh"));
                var context = host.start(new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of(Capabilities.TERMINAL_OPEN)), Set.of(), Set.of(), plugin -> { });
                var terminals = context.terminals();
                var view = context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("body", dev.jasper.sdk.ui.ControlState.READY,
                    "Notes", dev.jasper.sdk.ui.Nodes.TextRole.BODY)), event -> { });
                var notes = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), terminals.window(window).orElseThrow(), view);
                var tab = terminals.window(window).orElseThrow().activeTab().orElseThrow();
                var heard = new java.util.ArrayList<Boolean>();
                notes.onActive(heard::add);
                assertThat(notes.isActive()).as("it opens selected").isTrue();
                terminals.tab(shell).orElseThrow().select();
                assertThat(notes.isActive()).as("selecting a terminal tab deactivates it").isFalse();
                assertThat(heard).containsExactly(false);
                tab.select();
                assertThat(notes.isActive()).as("selecting its tab activates it").isTrue();
                assertThat(heard).containsExactly(false, true);
                host.focusTerminalPane(pane);
                assertThat(notes.isActive()).as("focusing a pane selects its tab").isFalse();
                notes.select();
                terminals.split(terminals.pane(pane).orElseThrow(), dev.jasper.sdk.terminal.Direction.RIGHT, OpenRequest.local());
                assertThat(notes.isActive()).as("a split's tab opens selected").isFalse();
                notes.select();
                host.addTerminalTab(window, "top");
                assertThat(notes.isActive()).as("so does a scripted tab").isFalse();
                assertThat(heard).containsExactly(false, true, false, true, false, true, false);
            }
        });
    }

    @Test void profilesAreScriptedAndOnlyListedOnesOpen() {
        try (var host = new FakePluginHost()) {
            var ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
            UUID window = host.addTerminalWindow();
            var heard = new java.util.ArrayList<String>();
            var context = host.start(new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of(Capabilities.TERMINAL_OPEN)), Set.of(), Set.of(),
                plugin -> plugin.terminals().onProfilesChanged(() -> heard.add("changed")));
            assertThat(context.terminals().profiles()).isEmpty();
            host.setShellProfiles(List.of(ubuntu));
            assertThat(heard).containsExactly("changed");
            assertThat(context.terminals().profiles()).containsExactly(ubuntu);
            var handle = context.terminals().window(window).orElseThrow();
            assertThat(context.terminals().openTab(handle, OpenRequest.localProfile("wsl:Ubuntu"))).isPresent();
            assertThat(context.terminals().openTab(handle, OpenRequest.localProfile("wsl:Debian"))).isEmpty();
            assertThat(host.openRequests()).containsExactly("tab|" + window + "|-|wsl:Ubuntu");
            host.stopAll();
            host.setShellProfiles(List.of());
            assertThat(heard).as("closed with the plugin").hasSize(1);
        }
    }
}
