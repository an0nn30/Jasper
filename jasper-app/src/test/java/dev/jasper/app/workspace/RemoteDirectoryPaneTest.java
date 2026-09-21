package dev.jasper.app.workspace;

import dev.jasper.app.terminals.RemoteLocation;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.terminal.session.AttachedConnection;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class RemoteDirectoryPaneTest {
    private final List<SessionAttempt> attempts = Collections.synchronizedList(new ArrayList<>());
    private final List<TerminalEvent> events = Collections.synchronizedList(new ArrayList<>());
    private final PipedOutputStream remote = new PipedOutputStream();
    private WindowContent owner;
    private TerminalPane pane;

    @AfterEach void close() throws Exception { closeOwners(); remote.close(); }

    @Test void aProvidedSessionShowsWhereItsRemoteShellIsAndNeverAsALocalPath() throws Exception {
        var output = new PipedInputStream(remote, 1 << 16);
        edt(() -> {
            owner = content(launcher(new ArrayDeque<>()));
            var registry = new TerminalRegistry();
            registry.onEvent(events::add);
            owner.connectTerminals(registry, () -> { });
            pane = owner.openTab(HOME, new SessionRequest("dev.example.ssh", "build-host", false, attempts::add, Runnable::run)).focusedPane();
        });
        attempts.get(0).attach(new AttachedConnection(output, OutputStream.nullOutputStream(), (columns, rows) -> { }, new CompletableFuture<>(),
            () -> { try { output.close(); } catch (java.io.IOException ignored) { } }));
        until(() -> pane.view() != null);
        remote.write("\033]7;file://build-host/srv/app\007\033]133;A\007$ \033]133;B\007make\r\n\033]133;C\007ok\r\n\033]133;D;2\007\033]133;A\007$ "
            .getBytes(StandardCharsets.UTF_8));
        remote.flush();
        var where = Optional.of(new RemoteLocation("build-host", "/srv/app"));
        until(() -> events.stream().anyMatch(event -> event instanceof TerminalEvent.CommandFinished));
        edt(() -> {
            assertThat(pane.snapshot().remoteDirectory()).isEqualTo(where);
            assertThat(pane.snapshot().workingDirectory()).isEmpty();
            assertThat(pane.directory()).as("a split of this pane starts at home, not in a remote path").isEqualTo(HOME);
            assertThat(pane.locationLabel()).isEqualTo("build-host:/srv/app");
            assertThat(events).contains(new TerminalEvent.DirectoryChanged(pane.id(), Optional.empty(), where));
            assertThat(events).anySatisfy(event -> assertThat(event).isInstanceOfSatisfying(TerminalEvent.CommandFinished.class, finished -> {
                assertThat(finished.command()).isEqualTo("make");
                assertThat(finished.exitStatus()).isEqualTo(OptionalInt.of(2));
                assertThat(finished.directory()).isEmpty();
                assertThat(finished.remote()).isEqualTo(where);
                assertThat(finished.duration()).isGreaterThanOrEqualTo(Duration.ZERO);
            }));
        });
    }
}
