package dev.jasper.sdk.terminal;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.events.Topic;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TerminalValuesTest {
    @Test void paneInfoRejectsImpossibleValues() {
        PaneInfo unknown = PaneInfo.unknown();
        assertThat(unknown.state()).isEqualTo(SessionState.EXITED);
        assertThat(SessionState.values()).as("in the order of a session's life")
            .containsExactly(SessionState.NOT_STARTED, SessionState.CONNECTING, SessionState.RUNNING, SessionState.EXITED);
        assertThat(unknown.kind()).isEqualTo(SessionKind.LOCAL);
        assertThatIllegalArgumentException().isThrownBy(() -> new PaneInfo("t", Optional.empty(), Optional.empty(), -1, 24, false,
            SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.empty(), "zsh"));
        assertThatIllegalArgumentException().as("an exit status belongs to an exited session").isThrownBy(() -> new PaneInfo("t",
            Optional.empty(), Optional.empty(), 80, 24, false, SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.of(0), "zsh"));
        assertThatNullPointerException().isThrownBy(() -> new PaneInfo(null, Optional.empty(), Optional.empty(), 80, 24, false,
            SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.empty(), "zsh"));
        assertThatNullPointerException().as("the shell label is required; use \"\" when unknown").isThrownBy(() -> new PaneInfo("t",
            Optional.empty(), Optional.empty(), 80, 24, false, SessionKind.LOCAL, Optional.empty(),
            SessionState.RUNNING, OptionalInt.empty(), null));
        assertThat(PaneInfo.unknown().shell()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new RemoteDirectory(" ", "/srv"));
        assertThat(new RemoteDirectory("", "/srv").host()).as("the program named no host").isEmpty();
    }

    @Test void openRequestsAreLocalAndTakeAnAbsoluteDirectory() {
        assertThat(OpenRequest.local()).isEqualTo(new OpenRequest.Local(new LocalSpec(Optional.empty())));
        Path home = Path.of(System.getProperty("user.home"));
        assertThat(OpenRequest.localIn(home)).isEqualTo(OpenRequest.local(new LocalSpec(Optional.of(home))));
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalSpec(Optional.of(Path.of("relative"))));
    }

    @Test void shellProfilesAreNamedValuesAndALocalSpecMayNameOne() throws Exception {
        var ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
        assertThat(ubuntu.detail()).contains("WSL 2");
        assertThat(ShellProfileInfo.Kind.values()).containsExactly(ShellProfileInfo.Kind.LOGIN_SHELL, ShellProfileInfo.Kind.LOCAL_SHELL,
            ShellProfileInfo.Kind.WSL, ShellProfileInfo.Kind.CUSTOM);
        assertThatIllegalArgumentException().isThrownBy(() -> new ShellProfileInfo(" ", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ShellProfileInfo("wsl:Ubuntu", "", ShellProfileInfo.Kind.WSL, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new ShellProfileInfo("pwsh", "PowerShell 7", null, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, null));

        assertThat(new LocalSpec(Optional.empty()).profile()).as("the old constructor means the default shell").isEmpty();
        assertThat(OpenRequest.localProfile("wsl:Ubuntu"))
            .isEqualTo(new OpenRequest.Local(new LocalSpec(Optional.empty(), Optional.of("wsl:Ubuntu"))))
            .isEqualTo(OpenRequest.local(LocalSpec.ofProfile("wsl:Ubuntu")));
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalSpec(Optional.empty(), Optional.of(" ")));
        assertThatNullPointerException().isThrownBy(() -> new LocalSpec(Optional.empty(), null));
        assertThatIllegalArgumentException().as("the directory rule still applies")
            .isThrownBy(() -> new LocalSpec(Optional.of(Path.of("relative")), Optional.of("pwsh")));

        assertThat(Terminals.class.getMethod("profiles").isDefault()).isTrue();
        assertThat(Terminals.class.getMethod("onProfilesChanged", Runnable.class).isDefault()).isTrue();
        Terminals older = (Terminals) java.lang.reflect.Proxy.newProxyInstance(Terminals.class.getClassLoader(), new Class<?>[]{Terminals.class},
            (proxy, method, args) -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null);
        assertThat(older.profiles()).as("a host that detects nothing").isEmpty();
        var heard = new java.util.concurrent.atomic.AtomicInteger();
        older.onProfilesChanged(heard::incrementAndGet).close();
        assertThat(heard).hasValue(0);
        assertThatNullPointerException().isThrownBy(() -> older.onProfilesChanged(null));
    }

    @Test void everyTerminalTopicIsNamespacedAndDistinct() throws Exception {
        List<String> ids = new ArrayList<>();
        for (var field : TerminalEvents.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != Topic.class) continue;
            Topic<?> topic = (Topic<?>) field.get(null);
            assertThat(TerminalEvents.owns(topic)).as(field.getName()).isTrue();
            assertThat(topic.id()).isEqualTo(TerminalEvents.PREFIX + field.getName().toLowerCase(java.util.Locale.ROOT));
            ids.add(topic.id());
        }
        assertThat(ids).hasSize(17).doesNotHaveDuplicates();
        assertThat(TerminalEvents.owns(Topic.of("jasper.activity", String.class))).isFalse();
    }

    /** Workspace tabs spec 14: a tab is a terminal tab, holding one pane, or a content tab, holding none. */
    @Test void aTabHasAKindAndATerminalTabItsOnePane() throws Exception {
        assertThat(TabHandle.Kind.values()).containsExactly(TabHandle.Kind.TERMINAL, TabHandle.Kind.CONTENT);
        assertThat(TabHandle.class.getMethod("kind").getReturnType()).isEqualTo(TabHandle.Kind.class);
        assertThat(TabHandle.class.getMethod("terminal").getGenericReturnType().getTypeName())
            .isEqualTo("java.util.Optional<dev.jasper.sdk.terminal.PaneHandle>");
        assertThat(java.util.Arrays.stream(TabHandle.class.getMethods()).map(java.lang.reflect.Method::getName))
            .as("a tab has no pane list").doesNotContain("panes", "activePane");
        assertThat(Direction.values()).containsExactly(Direction.RIGHT, Direction.DOWN);
    }

    /** Workspace tabs spec 14: a move names the tab and both windows. */
    @Test void aMoveNamesTheTabAndBothWindows() {
        var tab = java.util.UUID.randomUUID(); var from = java.util.UUID.randomUUID(); var to = java.util.UUID.randomUUID();
        var moved = new TerminalEvents.TabMoved(tab, from, to);
        assertThat(moved.tabId()).isEqualTo(tab);
        assertThat(moved.fromWindowId()).isEqualTo(from);
        assertThat(moved.toWindowId()).isEqualTo(to);
        assertThat(TerminalEvents.TAB_MOVED.payloadType()).isEqualTo(TerminalEvents.TabMoved.class);
        assertThatNullPointerException().isThrownBy(() -> new TerminalEvents.TabMoved(tab, null, to));
        assertThatNullPointerException().isThrownBy(() -> new TerminalEvents.TabMoved(tab, from, null));
        assertThatIllegalArgumentException().as("a move goes to another window").isThrownBy(() -> new TerminalEvents.TabMoved(tab, from, from));
    }

    @Test void aMissingCapabilityNamesThePluginAndTheCapability() {
        var failure = new MissingCapabilityException("dev.example.tool", Capabilities.TERMINAL_INJECT);
        assertThat(failure.pluginId()).isEqualTo("dev.example.tool");
        assertThat(failure.capability()).isEqualTo("terminal.inject");
        assertThat(failure).hasMessageContaining("dev.example.tool").hasMessageContaining("terminal.inject");
        assertThat(Capabilities.ALL).containsExactly("terminal.observe", "terminal.selection", "terminal.inject", "terminal.open", "session.provide", "palette.contribute", "files.source", "network.listen", "clipboard.read", "terminal.environment", "editor.provide", "presentation.provide");
    }

    @Test void aLocalSpecMayRunACommandInsteadOfTheShell() {
        Path home = Path.of(System.getProperty("user.home"));
        var words = new ArrayList<>(List.of("nvim", "+3", "notes.txt"));
        LocalSpec spec = LocalSpec.running(words, Optional.of(home));
        words.add("later");
        assertThat(spec.command()).as("copied").contains(List.of("nvim", "+3", "notes.txt"));
        assertThat(spec.workingDirectory()).contains(home);
        assertThat(spec.profile()).isEmpty();
        assertThat(spec).isEqualTo(new LocalSpec(Optional.of(home), Optional.empty(), Optional.of(List.of("nvim", "+3", "notes.txt"))));
        assertThat(OpenRequest.local(spec)).isEqualTo(new OpenRequest.Local(spec));
        assertThat(new LocalSpec(Optional.empty()).command()).as("the old constructors run the shell").isEmpty();
        assertThat(new LocalSpec(Optional.empty(), Optional.of("pwsh")).command()).isEmpty();
        assertThat(LocalSpec.ofProfile("pwsh").command()).isEmpty();
        assertThatIllegalArgumentException().as("no program").isThrownBy(() -> LocalSpec.running(List.of(), Optional.empty()));
        assertThatIllegalArgumentException().as("a blank program").isThrownBy(() -> LocalSpec.running(List.of(" ", "x"), Optional.empty()));
        assertThatNullPointerException().as("a null word").isThrownBy(() -> LocalSpec.running(java.util.Arrays.asList("nvim", null), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new LocalSpec(Optional.empty(), Optional.empty(), null));
        assertThatIllegalArgumentException().as("a profile or a command, not both")
            .isThrownBy(() -> new LocalSpec(Optional.empty(), Optional.of("pwsh"), Optional.of(List.of("nvim"))));
        assertThatIllegalArgumentException().as("the directory rule still applies")
            .isThrownBy(() -> LocalSpec.running(List.of("nvim"), Optional.of(Path.of("relative"))));
    }

    @Test void theEnvironmentHookIsADefaultThatAnOlderHostIgnores() throws Exception {
        assertThat(Terminals.class.getMethod("environment", java.util.function.Function.class).isDefault()).isTrue();
        Terminals older = (Terminals) java.lang.reflect.Proxy.newProxyInstance(Terminals.class.getClassLoader(), new Class<?>[]{Terminals.class},
            (proxy, method, args) -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null);
        var asked = new java.util.concurrent.atomic.AtomicInteger();
        older.environment(environment -> {
            asked.incrementAndGet();
            return java.util.Map.of("EDITOR", "vim");
        }).close();
        assertThat(asked).as("an older host never asks").hasValue(0);
        assertThatNullPointerException().isThrownBy(() -> older.environment(null));
    }
}
