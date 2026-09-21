package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PendingSession;
import dev.jasper.sdk.terminal.SessionSpec;
import dev.jasper.sdk.terminal.TerminalConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FakeSessionsTest {
    @Test void aPluginProvidesASessionAndTheTestDrivesIt() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            List<PendingSession> pendings = new ArrayList<>();
            var typed = new ByteArrayOutputStream();
            var closes = new AtomicInteger();
            var exited = new CompletableFuture<Integer>();
            PluginContext context = host.start(new PluginInfo("dev.x.ssh", "SSH", "1.0.0", Set.of(Capabilities.SESSION_PROVIDE)), Set.of(), Set.of(), plugin -> { });
            PaneHandle pane = context.terminals().openTab(context.terminals().window(window).orElseThrow(),
                OpenRequest.session(SessionSpec.of("build-host", pendings::add))).orElseThrow();
            assertThat(host.sessionState(pane.id())).isEqualTo("CONNECTING|");
            pendings.get(0).status("Authenticating");
            assertThat(host.sessionState(pane.id())).isEqualTo("CONNECTING|Authenticating");
            pendings.get(0).attach(new TerminalConnection(new ByteArrayInputStream("welcome".getBytes(StandardCharsets.UTF_8)), typed,
                (columns, rows) -> { }, exited, closes::incrementAndGet));
            assertThat(host.sessionState(pane.id())).isEqualTo("RUNNING|");
            host.typeIntoSession(pane.id(), "uptime\r");
            assertThat(typed.toString(StandardCharsets.UTF_8)).isEqualTo("uptime\r");
            assertThat(host.sessionOutput(pane.id())).isEqualTo("welcome");
            exited.complete(3);
            host.flush();
            assertThat(host.sessionState(pane.id())).isEqualTo("EXITED|exit 3");
            assertThat(closes).hasValue(1);
            host.reconnectSession(pane.id());
            assertThat(pendings).hasSize(2);
            host.cancelSession(pane.id());
            assertThat(pendings.get(1).isCancelled()).isTrue();
            assertThat(host.sessionState(pane.id())).isEqualTo("EXITED|Connection cancelled");
            assertThat(host.failures()).isEmpty();
        }
    }
}
