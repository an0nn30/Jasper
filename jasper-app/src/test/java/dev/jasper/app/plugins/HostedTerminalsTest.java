package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
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
            onUi::get, open::get);
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
            Optional.empty(), SessionState.RUNNING, java.util.OptionalInt.empty()));
        assertThat(pane.foregroundJob()).isCompletedWithValue(Optional.of("vim"));
        assertThat(terminals.tab(tab)).contains(tabHandle);
        assertThat(terminals.window(window)).contains(handle);
        assertThat(terminals.pane(UUID.randomUUID())).isEmpty();

        terminals.pane(right).orElseThrow().focus();
        assertThat(terminals.activePane().map(PaneHandle::id)).contains(right);
        handle.toFront();
        assertThat(fixture.opened()).containsExactly("front|" + window);
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
        PaneHandle opened = terminals.openTab(terminals.windowHandle(window), OpenRequest.localIn(Path.of("/tmp"))).orElseThrow();
        assertThat(opened.isOpen()).isTrue();
        assertThat(opened.tab().id()).isNotEqualTo(tab);
        PaneHandle split = terminals.split(terminals.paneHandle(only), Direction.DOWN, OpenRequest.local()).orElseThrow();
        assertThat(split.tab().id()).isEqualTo(tab);
        assertThat(fixture.opened()).containsExactly("tab|" + window + "|/tmp", "split|" + only + "|DOWN|-");
        assertThat(terminals.split(terminals.paneHandle(UUID.randomUUID()), Direction.RIGHT, OpenRequest.local())).isEmpty();
    }
}
