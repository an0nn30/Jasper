package dev.jasper.app.workspace;

import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.terminal.session.AttachedConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class SessionPaneTest {
    /** An in-memory remote: what the pane types is recorded, and the test decides when it exits. */
    private static final class Loopback {
        final PipedOutputStream remote = new PipedOutputStream();
        final PipedInputStream output;
        final ByteArrayOutputStream typed = new ByteArrayOutputStream();
        final CompletableFuture<Integer> exited = new CompletableFuture<>();
        final AtomicInteger closes = new AtomicInteger();
        Loopback() throws IOException { output = new PipedInputStream(remote, 1 << 16); }
        AttachedConnection connection() {
            OutputStream input = new OutputStream() { @Override public void write(int value) { typed.write(value); } };
            return new AttachedConnection(output, input, (columns, rows) -> { }, exited,
                () -> { closes.incrementAndGet(); try { remote.close(); output.close(); } catch (IOException ignored) { } });
        }
        void exit(int status) throws IOException { exited.complete(status); remote.close(); }
        String typed() { return typed.toString(StandardCharsets.UTF_8); }
    }

    private final List<SessionAttempt> attempts = Collections.synchronizedList(new ArrayList<>());
    private final List<TerminalEvent> events = new ArrayList<>();
    private WindowContent owner;
    private TerminalRegistry registry;
    private TerminalPane pane;

    @AfterEach void close() throws Exception { closeOwners(); }

    private void open(boolean closeOnExit) throws Exception {
        edt(() -> {
            owner = content(launcher(new ArrayDeque<>()));
            registry = new TerminalRegistry();
            registry.onEvent(events::add);
            owner.connectTerminals(registry, () -> { });
            var request = new SessionRequest("dev.example.ssh", "build-host", closeOnExit, attempts::add, Runnable::run);
            pane = owner.openTab(HOME, request).focusedPane();
        });
    }

    private <T> T onEdt(java.util.function.Supplier<T> query) throws Exception {
        Object[] result = new Object[1];
        edt(() -> result[0] = query.get());
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }

    @Test void thePaneAppearsFirstThenShowsTheAttachedSessionAndTypesIntoIt() throws Exception {
        open(false);
        edt(() -> {
            assertThat(attempts).hasSize(1);
            assertThat(attempts.get(0).paneId()).isEqualTo(pane.id());
            assertThat(attempts.get(0).columns()).isPositive();
            assertThat(pane.noticeText()).isEqualTo("Connecting…");
            assertThat(pane.noticeSecondary().getText()).isEqualTo("Cancel");
            assertThat(pane.noticePrimary().isVisible()).isFalse();
            PaneSnapshot pending = pane.snapshot();
            assertThat(pending.state()).isEqualTo(PaneSnapshot.State.STARTING);
            assertThat(pending.providerId()).contains("dev.example.ssh");
            assertThat(pending.title()).isEqualTo("build-host");
            assertThat(pending.workingDirectory()).as("a remote pane has no local directory").isEmpty();
            assertThat(events).contains(new TerminalEvent.SessionConnecting(pane.id()));
        });
        attempts.get(0).status("Authenticating…");
        until(() -> pane.noticeText().equals("Authenticating…"));
        var loopback = new Loopback();
        attempts.get(0).attach(loopback.connection());
        until(() -> pane.view() != null);
        edt(() -> {
            assertThat(pane.noticeText()).isEmpty();
            assertThat(pane.snapshot().state()).isEqualTo(PaneSnapshot.State.RUNNING);
            assertThat(pane.snapshot().providerId()).contains("dev.example.ssh");
            assertThat(events).contains(new TerminalEvent.SessionStarted(pane.id()));
            assertThat(pane.directory()).as("a user's split starts a local shell at home").isEqualTo(HOME);
            pane.write("uptime\r".getBytes(StandardCharsets.UTF_8));
        });
        until(() -> loopback.typed().equals("uptime\r"));
        edt(() -> owner.closeTab(owner.currentTab()));
        until(() -> loopback.closes.get() == 1);
    }

    @Test void aDisconnectedPaneOffersReconnectAndStaleAttemptsCannotAttach() throws Exception {
        open(false);
        var first = new Loopback();
        attempts.get(0).attach(first.connection());
        until(() -> pane.view() != null);
        first.exit(3);
        until(() -> pane.noticeText().equals("Disconnected (exit 3)"));
        edt(() -> {
            assertThat(first.closes).as("closed before Reconnect is offered").hasValue(1);
            assertThat(pane.noticePrimary().getText()).isEqualTo("Reconnect");
            assertThat(pane.noticeSecondary().getText()).isEqualTo("Close");
            assertThat(pane.snapshot().state()).isEqualTo(PaneSnapshot.State.EXITED);
            assertThat(pane.snapshot().exitStatus()).hasValue(3);
            pane.noticePrimary().doClick();
            assertThat(attempts).hasSize(2);
            assertThat(pane.noticeText()).isEqualTo("Connecting…");
            assertThat(pane.snapshot().state()).isEqualTo(PaneSnapshot.State.STARTING);
        });
        var stale = new Loopback();
        attempts.get(0).attach(stale.connection());
        assertThat(stale.closes).as("a slow first attempt cannot attach into a later one").hasValue(1);
        attempts.get(1).fail("Connection refused");
        until(() -> pane.noticeText().equals("Connection refused"));
        edt(() -> {
            assertThat(pane.noticePrimary().getText()).isEqualTo("Reconnect");
            pane.noticePrimary().doClick();
        });
        var second = new Loopback();
        attempts.get(2).attach(second.connection());
        until(() -> pane.noticeText().isEmpty() && pane.snapshot().state() == PaneSnapshot.State.RUNNING);
        edt(() -> pane.write("ok".getBytes(StandardCharsets.UTF_8)));
        until(() -> second.typed().equals("ok"));
        assertThat(first.typed()).isEmpty();
    }

    @Test void cancellingTheFirstAttemptClosesThePaneAndRunsItsHandlers() throws Exception {
        open(false);
        List<String> cancelled = Collections.synchronizedList(new ArrayList<>());
        attempts.get(0).onCancelled(() -> cancelled.add("aborted"));
        edt(() -> pane.noticeSecondary().doClick());
        edt(() -> {
            assertThat(registry.pane(pane.id())).isEmpty();
            assertThat(attempts.get(0).isCancelled()).isTrue();
        });
        assertThat(cancelled).containsExactly("aborted");
        var late = new Loopback();
        attempts.get(0).attach(late.connection());
        assertThat(late.closes).hasValue(1);
    }

    @Test void aFailedFirstAttemptKeepsThePaneWithRetryAndClose() throws Exception {
        open(false);
        attempts.get(0).fail("Host key mismatch");
        until(() -> pane.noticeText().equals("Host key mismatch"));
        edt(() -> {
            assertThat(pane.noticePrimary().getText()).isEqualTo("Retry");
            assertThat(pane.snapshot().state()).isEqualTo(PaneSnapshot.State.EXITED);
            pane.noticeSecondary().doClick();
            assertThat(registry.pane(pane.id())).isEmpty();
        });
    }

    @Test void aFailedTransportSaysWhyAndClosePaneOnExitClosesIt() throws Exception {
        open(false);
        var broken = new Loopback();
        attempts.get(0).attach(broken.connection());
        until(() -> pane.view() != null);
        broken.exited.completeExceptionally(new IOException("connection reset"));
        until(() -> pane.noticeText().equals("Disconnected: The connection ended: connection reset"));
        edt(() -> assertThat(pane.snapshot().exitStatus()).isEmpty());
        closeOwners();

        open(true);
        var closing = new Loopback();
        attempts.get(attempts.size() - 1).attach(closing.connection());
        until(() -> pane.view() != null);
        closing.exit(0);
        until(() -> registry.pane(pane.id()).isEmpty());
        assertThat(closing.closes).hasValue(1);
    }

    @Test void aProviderThatThrowsFailsTheAttempt() throws Exception {
        edt(() -> {
            owner = content(launcher(new ArrayDeque<>()));
            var request = new SessionRequest("dev.example.ssh", "build-host", false, attempt -> { throw new IllegalStateException("no such host"); }, Runnable::run);
            pane = owner.openTab(HOME, request).focusedPane();
        });
        until(() -> pane.noticeText().equals("no such host"));
        assertThat(onEdt(() -> pane.noticePrimary().getText())).isEqualTo("Retry");
        assertThat(Optional.ofNullable(onEdt(() -> pane.view()))).isEmpty();
    }
}
