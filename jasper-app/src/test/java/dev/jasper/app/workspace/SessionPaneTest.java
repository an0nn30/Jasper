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

    @Test void headerAndShortTabFollowCommandsProgramTitlesAndActivePane() throws Exception {
        open(false);
        var connection = new Loopback(); attempts.getFirst().attach(connection.connection());
        until(() -> pane.view() != null);
        var bar = new dev.jasper.app.platform.MacTitleBar[1];
        var field = new TitleSearchField[1];
        edt(() -> {
            bar[0] = WindowContent.installTitleBar(new javax.swing.JRootPane(), owner, true, title -> {});
            field[0] = owner.chrome().search();
        });
        try {
            attempts.getFirst().reportRemoteDirectory(new dev.jasper.app.terminals.RemoteLocation("build-host", "/srv/app"));
            String command = java.util.Base64.getEncoder().encodeToString("sleep 12".getBytes(StandardCharsets.UTF_8));
            connection.remote.write(("\033]2;old prompt\007\033]1341;jasper;cmd;" + command + "\007\033]133;C\007").getBytes(StandardCharsets.UTF_8));
            connection.remote.flush();
            until(() -> field[0].contextText().equals("build-host:/srv/app : sleep 12"));
            edt(() -> assertThat(owner.currentTab().title()).isEqualTo("sleep 12"));
            connection.remote.write("\033]2;Claude Code — Reviewing files\007".getBytes(StandardCharsets.UTF_8)); connection.remote.flush();
            until(() -> field[0].contextText().equals("Claude Code — Reviewing files"));
            edt(() -> {
                assertThat(owner.currentTab().title()).isEqualTo("Claude Code — Reviewing files");
                var tab = owner.currentTab();
                tab.split(SplitTree.Axis.RIGHT);
                assertThat(field[0].contextText()).isNotEqualTo("Claude Code — Reviewing files");
                tab.focus(pane);
                assertThat(field[0].contextText()).isEqualTo("Claude Code — Reviewing files");
                owner.newTab(HOME); owner.selectTab(tab);
                assertThat(field[0].contextText()).isEqualTo("Claude Code — Reviewing files");
            });
            connection.remote.write("\033]133;D;0\007".getBytes(StandardCharsets.UTF_8)); connection.remote.flush();
            until(() -> field[0].contextText().equals("build-host:/srv/app : Shell"));
            edt(() -> assertThat(owner.currentTab().title()).isEqualTo("build-host"));
        } finally { edt(() -> bar[0].close()); }
    }

    @Test void providerDirectoryIsDisplayOnlyAndOldAttemptsCannotChangeAReconnectedPane() throws Exception {
        open(false);
        edt(() -> assertThat(pane.locationLabel()).isEqualTo("build-host"));
        var first = new Loopback();
        var original = attempts.getFirst();
        original.attach(first.connection());
        until(() -> pane.view() != null);
        original.reportRemoteDirectory(new dev.jasper.app.terminals.RemoteLocation("build-host", "/srv/one"));
        until(() -> pane.snapshot().remoteDirectory().isPresent());
        edt(() -> {
            assertThat(pane.locationLabel()).isEqualTo("build-host:/srv/one");
            assertThat(pane.snapshot().workingDirectory()).isEmpty();
            assertThat(pane.directory()).isEqualTo(HOME);
            assertThat(events).noneMatch(event -> event instanceof TerminalEvent.DirectoryChanged);
        });
        first.exit(0);
        until(() -> pane.noticePrimary().isVisible());
        edt(() -> pane.noticePrimary().doClick());
        edt(() -> assertThat(pane.snapshot().remoteDirectory()).isEmpty());
        var second = new Loopback();
        attempts.getLast().attach(second.connection());
        until(() -> pane.running());
        original.reportRemoteDirectory(new dev.jasper.app.terminals.RemoteLocation("old", "/stale"));
        edt(() -> assertThat(pane.snapshot().remoteDirectory()).isEmpty());
        attempts.getLast().reportRemoteDirectory(new dev.jasper.app.terminals.RemoteLocation("new", "/srv/two"));
        until(() -> pane.locationLabel().equals("new:/srv/two"));
        second.remote.write("\033]7;file://shell-host/shell/path\007".getBytes(StandardCharsets.UTF_8));
        second.remote.flush();
        until(() -> pane.locationLabel().equals("shell-host:/shell/path"));
        attempts.getLast().reportRemoteDirectory(new dev.jasper.app.terminals.RemoteLocation("new", "/must-not-win"));
        edt(() -> assertThat(pane.locationLabel()).isEqualTo("shell-host:/shell/path"));
        edt(() -> pane.close());
        attempts.getLast().reportRemoteDirectory(new dev.jasper.app.terminals.RemoteLocation("new", "/closed"));
        edt(() -> assertThat(pane.locationLabel()).isEqualTo("shell-host:/shell/path"));
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
            for (var button : new javax.swing.JButton[]{pane.noticePrimary(), pane.noticeSecondary()}) {
                assertThat(button.getClientProperty("JButton.buttonType")).as("notice commands stay framed buttons").isNull();
                assertThat(button.isContentAreaFilled()).isTrue();
            }
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
