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
        assertThat(ids).hasSize(16).doesNotHaveDuplicates();
        assertThat(TerminalEvents.owns(Topic.of("jasper.activity", String.class))).isFalse();
    }

    @Test void aMissingCapabilityNamesThePluginAndTheCapability() {
        var failure = new MissingCapabilityException("dev.example.tool", Capabilities.TERMINAL_INJECT);
        assertThat(failure.pluginId()).isEqualTo("dev.example.tool");
        assertThat(failure.capability()).isEqualTo("terminal.inject");
        assertThat(failure).hasMessageContaining("dev.example.tool").hasMessageContaining("terminal.inject");
        assertThat(Capabilities.ALL).containsExactly("terminal.observe", "terminal.selection", "terminal.inject", "terminal.open", "session.provide", "palette.contribute", "files.source", "network.listen");
    }
}
