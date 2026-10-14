package dev.jasper.app.plugins;

import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class HostedTerminalsTest {
    private final TerminalFixture fixture = new TerminalFixture();
    private final List<Runnable> posted = new ArrayList<>();
    private final AtomicBoolean onUi = new AtomicBoolean(true);
    private final AtomicBoolean open = new AtomicBoolean(true);

    private HostedTerminals terminals(String... capabilities) {
        return new HostedTerminals("dev.x.tool", new CapabilityGate("dev.x.tool", Set.of(capabilities)), fixture.registry, posted::add,
            onUi::get, open::get, new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null, name -> new javax.swing.ImageIcon()));
    }

    private final TestShellProfiles shells = new TestShellProfiles();

    private HostedTerminals profiled(String... capabilities) {
        return new HostedTerminals("dev.x.tool", new CapabilityGate("dev.x.tool", Set.of(capabilities)), fixture.registry, posted::add,
            onUi::get, open::get, new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null, name -> new javax.swing.ImageIcon()),
            shells.source(), new Containment(() -> true));
    }

    static final ShellProfile LOGIN = new ShellProfile("login", "Login shell", ShellProfile.Kind.LOGIN_SHELL, Optional.empty(),
        List.of("/bin/zsh", "-l"));
    static final ShellProfile UBUNTU = new ShellProfile("wsl:Ubuntu", "Ubuntu", ShellProfile.Kind.WSL, Optional.of("WSL 2"),
        List.of("wsl.exe", "-d", "Ubuntu"));

    @Test void profilesMirrorTheCatalogAndTheirListenersCloseWithThePlugin() {
        shells.set(List.of(LOGIN, UBUNTU));
        HostedTerminals terminals = profiled(Capabilities.TERMINAL_OPEN);
        assertThat(terminals.profiles()).containsExactly(
            new ShellProfileInfo("login", "Login shell", ShellProfileInfo.Kind.LOGIN_SHELL, Optional.empty()),
            new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2")));
        assertThatThrownBy(() -> profiled().profiles()).isInstanceOf(MissingCapabilityException.class);
        assertThatThrownBy(() -> profiled().onProfilesChanged(() -> { })).isInstanceOf(MissingCapabilityException.class);
        List<String> heard = new ArrayList<>();
        dev.jasper.sdk.Subscription first = terminals.onProfilesChanged(() -> heard.add("first"));
        terminals.onProfilesChanged(() -> { throw new IllegalStateException("a plugin bug"); });
        terminals.onProfilesChanged(() -> heard.add("second"));
        shells.set(List.of(UBUNTU));
        assertThat(heard).as("a throwing listener is contained").containsExactly("first", "second");
        first.close();
        first.close();
        shells.set(List.of(LOGIN));
        assertThat(heard).containsExactly("first", "second", "second");
        assertThat(shells.listeners()).isEqualTo(2);
        terminals.closeAll();
        assertThat(shells.listeners()).as("closed with the plugin").isZero();
        shells.set(List.of());
        assertThat(heard).hasSize(3);
    }

    @Test void aNamedProfileOpensOnlyWhenTheCatalogListsIt() {
        shells.set(List.of(UBUNTU));
        HostedTerminals terminals = profiled(Capabilities.TERMINAL_OPEN);
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), only = fixture.addPane(tab, "make", Path.of("/src"));
        assertThat(terminals.openTab(terminals.windowHandle(window), OpenRequest.localProfile("wsl:Ubuntu"))).isPresent();
        assertThat(terminals.openTab(terminals.windowHandle(window), OpenRequest.localProfile("wsl:Debian"))).isEmpty();
        assertThat(terminals.split(terminals.paneHandle(only), Direction.RIGHT, OpenRequest.localProfile("pwsh"))).isEmpty();
        assertThat(terminals.split(terminals.paneHandle(only), Direction.DOWN, OpenRequest.local())).as("the default needs no profile").isPresent();
        assertThat(fixture.opened()).containsExactly("tab|" + window + "|-|wsl:Ubuntu", "split|" + only + "|DOWN|-");
    }

    @Test void queriesMirrorTheRegistryAndHandlesAreEqualById() {
        HostedTerminals terminals = terminals(Capabilities.TERMINAL_OBSERVE);
        assertThat(terminals.windows()).isEmpty();
        assertThat(terminals.activeWindow()).isEmpty();
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), left = fixture.addPane(tab, "make", Path.of("/src"));
        UUID right = fixture.addPane(tab, "top", null);
        fixture.activateWindow(window);
        WindowHandle handle = terminals.windows().get(0);
        assertThat(handle).isEqualTo(terminals.activeWindow().orElseThrow()).isEqualTo(terminals.windowHandle(window)).hasSameHashCodeAs(terminals.windowHandle(window));
        assertThat(handle.isOpen()).isTrue();
        assertThat(handle.isActive()).isTrue();
        TabHandle tabHandle = handle.activeTab().orElseThrow();
        assertThat(handle.tabs()).containsExactly(tabHandle);
        assertThat(tabHandle.id()).isEqualTo(tab);
        assertThat(tabHandle.window()).isEqualTo(handle);
        assertThat(tabHandle.title()).isEqualTo("build");
        assertThat(tabHandle.panes()).extracting(PaneHandle::id).containsExactly(left, right);
        PaneHandle pane = terminals.activePane().orElseThrow();
        assertThat(pane.id()).isEqualTo(left);
        assertThat(pane).isEqualTo(terminals.pane(left).orElseThrow()).isEqualTo(tabHandle.activePane().orElseThrow());
        assertThat(pane.tab()).isEqualTo(tabHandle);
        assertThat(pane.info()).isEqualTo(new PaneInfo("make", Optional.of(Path.of("/src")), Optional.empty(), 80, 24, true, SessionKind.LOCAL,
            Optional.empty(), SessionState.RUNNING, java.util.OptionalInt.empty(), "zsh"));
        assertThat(pane.foregroundJob()).isCompletedWithValue(Optional.of("vim"));
        assertThat(terminals.tab(tab)).contains(tabHandle);
        assertThat(terminals.window(window)).contains(handle);
        assertThat(terminals.pane(UUID.randomUUID())).isEmpty();

        terminals.pane(right).orElseThrow().focus();
        assertThat(terminals.activePane().map(PaneHandle::id)).contains(right);
        handle.toFront();
        assertThat(fixture.opened()).containsExactly("front|" + window);
        fixture.reportDirectory(left, "build-host", "/srv/app");
        assertThat(pane.info().workingDirectory()).isEmpty();
        assertThat(pane.info().remoteDirectory()).contains(new dev.jasper.sdk.terminal.RemoteDirectory("build-host", "/srv/app"));
        fixture.reportDirectory(left, "", "/tmp");
        assertThat(pane.info().workingDirectory()).contains(Path.of("/tmp"));
        assertThat(pane.info().remoteDirectory()).isEmpty();
    }

    @Test void aClosedPaneKeepsItsLastValuesAndIgnoresCommands() {
        HostedTerminals terminals = terminals(Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT, Capabilities.TERMINAL_SELECTION,
            Capabilities.TERMINAL_OPEN);
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), only = fixture.addPane(tab, "make", Path.of("/src"));
        PaneHandle pane = terminals.pane(only).orElseThrow();
        TabHandle tabHandle = pane.tab();
        WindowHandle windowHandle = tabHandle.window();
        assertThat(pane.info().title()).isEqualTo("make");
        assertThat(tabHandle.title()).isEqualTo("build");
        fixture.closePane(only);
        assertThat(pane.isOpen()).isFalse();
        assertThat(tabHandle.isOpen()).isFalse();
        assertThat(windowHandle.isOpen()).as("the last tab took the window with it").isFalse();
        assertThat(pane.info().title()).as("the last known value").isEqualTo("make");
        assertThat(tabHandle.title()).isEqualTo("build");
        assertThat(tabHandle.panes()).isEmpty();
        assertThat(windowHandle.tabs()).isEmpty();
        assertThat(pane.selection()).isEmpty();
        assertThat(pane.foregroundJob()).isCompletedWithValue(Optional.empty());
        assertThatCode(() -> { pane.sendText("late"); pane.paste("late"); pane.focus(); tabHandle.select(); windowHandle.toFront(); })
            .doesNotThrowAnyException();
        assertThat(terminals.paneHandle(UUID.randomUUID()).info()).isEqualTo(PaneInfo.unknown());
        assertThat(terminals.openTab(windowHandle, OpenRequest.local())).isEmpty();
    }

    @Test void everyGatedCallNamesTheMissingCapabilityBeforeAnythingHappens() {
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), only = fixture.addPane(tab, "make", null);
        HostedTerminals bare = terminals();
        PaneHandle pane = bare.pane(only).orElseThrow();
        assertThat(pane.isOpen()).as("identity and structure need nothing").isTrue();
        assertThat(pane.tab().panes()).hasSize(1);
        for (var gated : List.<java.util.Map.Entry<String, Runnable>>of(
                java.util.Map.entry(Capabilities.TERMINAL_OBSERVE, pane::info), java.util.Map.entry(Capabilities.TERMINAL_OBSERVE, pane::foregroundJob),
                java.util.Map.entry(Capabilities.TERMINAL_OBSERVE, () -> pane.tab().title()),
                java.util.Map.entry(Capabilities.TERMINAL_SELECTION, pane::selection),
                java.util.Map.entry(Capabilities.TERMINAL_INJECT, () -> pane.sendText("x")),
                java.util.Map.entry(Capabilities.TERMINAL_INJECT, () -> pane.sendBytes(new byte[]{1})),
                java.util.Map.entry(Capabilities.TERMINAL_INJECT, () -> pane.paste("x")),
                java.util.Map.entry(Capabilities.TERMINAL_OPEN, () -> bare.openTab(bare.windowHandle(window), OpenRequest.local())),
                java.util.Map.entry(Capabilities.TERMINAL_OPEN, () -> bare.split(pane, Direction.RIGHT, OpenRequest.local())))) {
            assertThatThrownBy(gated.getValue()::run).isInstanceOfSatisfying(MissingCapabilityException.class, failure -> {
                assertThat(failure.pluginId()).isEqualTo("dev.x.tool");
                assertThat(failure.capability()).isEqualTo(gated.getKey());
            });
        }
        assertThat(fixture.sent(only)).isEmpty();
        assertThat(fixture.opened()).isEmpty();
    }

    @Test void injectionKeepsItsOrderFromAnyThreadAndSelectionIsRead() {
        HostedTerminals terminals = terminals(Capabilities.TERMINAL_INJECT, Capabilities.TERMINAL_SELECTION);
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), only = fixture.addPane(tab, "make", null);
        PaneHandle pane = terminals.pane(only).orElseThrow();
        pane.sendText("ls\n");
        byte[] bytes = "é".getBytes(StandardCharsets.UTF_8);
        pane.sendBytes(bytes);
        bytes[0] = 0;
        pane.paste("pasted");
        assertThat(fixture.sent(only)).containsExactly("write:ls\n", "write:é", "paste:pasted");

        onUi.set(false);
        pane.sendText("one"); pane.sendText("two");
        assertThat(fixture.sent(only)).as("posted, not run on the caller's thread").hasSize(3);
        assertThatIllegalStateException().isThrownBy(pane::selection);
        assertThatIllegalStateException().isThrownBy(terminals::windows);
        onUi.set(true);
        posted.forEach(Runnable::run);
        assertThat(fixture.sent(only)).endsWith("write:one", "write:two");

        fixture.select(only, "selected text");
        assertThat(pane.selection()).contains("selected text");
        open.set(false);
        pane.sendText("after stop");
        assertThat(fixture.sent(only)).hasSize(5);
        assertThatIllegalStateException().isThrownBy(terminals::windows);
    }

    @Test void openingATabOrASplitReturnsTheNewPane() {
        HostedTerminals terminals = terminals(Capabilities.TERMINAL_OPEN);
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), only = fixture.addPane(tab, "make", Path.of("/src"));
        PaneHandle opened = terminals.openTab(terminals.windowHandle(window), OpenRequest.localIn(Path.of("/tmp").toAbsolutePath())).orElseThrow();
        assertThat(opened.isOpen()).isTrue();
        assertThat(opened.tab().id()).isNotEqualTo(tab);
        PaneHandle split = terminals.split(terminals.paneHandle(only), Direction.DOWN, OpenRequest.local()).orElseThrow();
        assertThat(split.tab().id()).isEqualTo(tab);
        assertThat(fixture.opened()).containsExactly("tab|" + window + "|" + Path.of("/tmp").toAbsolutePath(), "split|" + only + "|DOWN|-");
        assertThat(terminals.split(terminals.paneHandle(UUID.randomUUID()), Direction.RIGHT, OpenRequest.local())).isEmpty();
    }

    @Test void anEnvironmentContributorNeedsTheCapabilityAndTheUiThreadAndEndsWithThePlugin() {
        var contributions = new ShellContributions(new Containment(() -> true));
        HostedTerminals terminals = new HostedTerminals("dev.x.tool", new CapabilityGate("dev.x.tool", Set.of(Capabilities.TERMINAL_ENVIRONMENT)),
            fixture.registry, posted::add, onUi::get, open::get,
            new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null, name -> new javax.swing.ImageIcon()),
            PluginRuntime.ShellProfiles.NONE, new Containment(() -> true), contributions);
        assertThatThrownBy(() -> terminals().environment(environment -> java.util.Map.of("A", "1")))
            .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo(Capabilities.TERMINAL_ENVIRONMENT));
        onUi.set(false);
        assertThatIllegalStateException().isThrownBy(() -> terminals.environment(environment -> java.util.Map.of("A", "1")));
        onUi.set(true);
        var first = terminals.environment(environment -> java.util.Map.of("A", "1"));
        terminals.environment(environment -> java.util.Map.of("B", "2"));
        assertThat(contributions.variables(java.util.Map.of())).isEqualTo(java.util.Map.of("A", "1", "B", "2"));
        onUi.set(false);
        first.close();
        assertThat(contributions.variables(java.util.Map.of())).as("a close off the UI thread is posted to it").containsKey("A");
        onUi.set(true);
        List.copyOf(posted).forEach(Runnable::run);
        assertThat(contributions.variables(java.util.Map.of())).isEqualTo(java.util.Map.of("B", "2"));
        terminals.closeAll();
        assertThat(contributions.variables(java.util.Map.of())).as("the plugin stopped").isEmpty();
    }
}
