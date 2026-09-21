package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.terminal.ExitPolicy;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PendingSession;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionSpec;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.TerminalConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class HostedSessionsTest {
    private final TerminalFixture fixture = new TerminalFixture();
    private final Containment containment = new Containment(() -> true);
    private final List<Runnable> cleanup = new ArrayList<>();
    private final List<PendingSession> pendings = new ArrayList<>();
    private final ByteArrayOutputStream typed = new ByteArrayOutputStream();
    private final AtomicInteger closes = new AtomicInteger();
    private final CompletableFuture<Integer> exited = new CompletableFuture<>();
    private HostedSessions sessions;

    private HostedTerminals terminals(String... capabilities) {
        HostedTerminals[] holder = new HostedTerminals[1];
        sessions = new HostedSessions("dev.x.ssh", containment, cleanup::add, id -> holder[0].detachedPaneHandle(id));
        holder[0] = new HostedTerminals("dev.x.ssh", new CapabilityGate("dev.x.ssh", Set.of(capabilities)), fixture.registry, Runnable::run,
            () -> true, () -> true, sessions);
        return holder[0];
    }

    private TerminalConnection connection(String output) {
        return new TerminalConnection(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)), typed, (columns, rows) -> { }, exited,
            closes::incrementAndGet);
    }

    private void runCleanup() { while (!cleanup.isEmpty()) cleanup.remove(0).run(); }

    @Test void aProvidedSessionAppearsFirstThenAttachesAndCarriesBytes() {
        HostedTerminals terminals = terminals(Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE);
        UUID window = fixture.addWindow();
        PaneHandle pane = terminals.openTab(terminals.windowHandle(window), OpenRequest.session(SessionSpec.of("build-host", pendings::add))).orElseThrow();
        assertThat(fixture.opened()).containsExactly("session-tab|" + window + "|build-host");
        assertThat(pendings).hasSize(1);
        PendingSession pending = pendings.get(0);
        assertThat(pending.pane()).isEqualTo(pane);
        assertThat(pending.columns()).isEqualTo(80);
        assertThat(pending.rows()).isEqualTo(24);
        assertThat(pane.info().kind()).isEqualTo(SessionKind.PLUGIN);
        assertThat(pane.info().providerPluginId()).contains("dev.x.ssh");
        assertThat(pane.info().state()).isEqualTo(SessionState.CONNECTING);
        pending.status("Authenticating");
        assertThat(fixture.sessionState(pane.id())).isEqualTo("CONNECTING|Authenticating");
        pending.attach(connection("welcome"));
        assertThat(fixture.sessionState(pane.id())).isEqualTo("RUNNING|");
        assertThat(pane.info().state()).isEqualTo(SessionState.RUNNING);
        fixture.typeIntoSession(pane.id(), "uptime\r");
        assertThat(typed.toString(StandardCharsets.UTF_8)).isEqualTo("uptime\r");
        assertThat(fixture.sessionOutput(pane.id())).isEqualTo("welcome");
        fixture.closePane(pane.id());
        assertThat(closes).as("never on the caller's thread").hasValue(0);
        runCleanup();
        assertThat(closes).hasValue(1);
    }

    @Test void sessionsNeedTheirCapabilityAndAThrowingConnectorFailsTheAttempt() {
        UUID window = fixture.addWindow();
        HostedTerminals bare = terminals(Capabilities.TERMINAL_OPEN);
        assertThatThrownBy(() -> bare.openTab(bare.windowHandle(window), OpenRequest.session(SessionSpec.of("x", pendings::add))))
            .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo(Capabilities.SESSION_PROVIDE));
        assertThat(fixture.opened()).isEmpty();
        HostedTerminals terminals = terminals(Capabilities.SESSION_PROVIDE);
        PaneHandle pane = terminals.openTab(terminals.windowHandle(window),
            OpenRequest.session(SessionSpec.of("x", pending -> { throw new IllegalStateException("no such host"); }))).orElseThrow();
        assertThat(fixture.sessionState(pane.id())).isEqualTo("EXITED|no such host");
        assertThat(containment.failures("dev.x.ssh")).isEqualTo(1);
    }

    @Test void cancellationReconnectAndStaleAttemptsFollowTheRules() {
        HostedTerminals terminals = terminals(Capabilities.SESSION_PROVIDE);
        UUID window = fixture.addWindow();
        UUID keep = fixture.addTab(window, "local");
        fixture.addPane(keep, "zsh", null);
        PaneHandle pane = terminals.openTab(terminals.windowHandle(window), OpenRequest.session(new SessionSpec("build-host", Optional.empty(),
            ExitPolicy.KEEP_OPEN, pendings::add))).orElseThrow();
        List<String> seen = new ArrayList<>();
        var withdrawn = pendings.get(0).onCancelled(() -> seen.add("withdrawn"));
        withdrawn.close();
        withdrawn.close();
        pendings.get(0).fail("Connection refused");
        assertThat(fixture.sessionState(pane.id())).isEqualTo("EXITED|Connection refused");
        fixture.reconnectSession(pane.id());
        assertThat(pendings).as("the same connector, a fresh attempt").hasSize(2);
        pendings.get(0).attach(connection("stale"));
        runCleanup();
        assertThat(closes).as("a slow first attempt cannot attach into a later one").hasValue(1);
        assertThat(fixture.sessionState(pane.id())).isEqualTo("CONNECTING|");

        pendings.get(1).onCancelled(() -> { seen.add("aborted"); throw new IllegalStateException("handler failure"); });
        sessions.cancelAll();
        assertThat(pendings.get(1).isCancelled()).isTrue();
        assertThat(seen).as("on the cleanup worker").isEmpty();
        runCleanup();
        pendings.get(1).onCancelled(() -> seen.add("late"));
        runCleanup();
        assertThat(seen).containsExactly("aborted", "late");
        assertThat(containment.failures("dev.x.ssh")).as("a failing handler is contained").isEqualTo(1);
        pendings.get(1).attach(connection("after stop"));
        runCleanup();
        assertThat(closes).hasValue(2);
    }
}
