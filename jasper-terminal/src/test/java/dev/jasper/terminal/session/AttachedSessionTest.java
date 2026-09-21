package dev.jasper.terminal.session;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.testsupport.Await;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachedSessionTest {
    private final PipedOutputStream remote = new PipedOutputStream();
    private final ByteArrayOutputStream typed = new ByteArrayOutputStream();
    private final CompletableFuture<Integer> exited = new CompletableFuture<>();
    private final AtomicInteger closes = new AtomicInteger();
    private final List<String> resizes = Collections.synchronizedList(new ArrayList<>());
    private TerminalSession session;

    private TerminalSession attach() throws IOException {
        var output = new PipedInputStream(remote, 1 << 16);
        OutputStream input = new OutputStream() {
            @Override public synchronized void write(int value) { typed.write(value); }
        };
        session = TerminalSession.attach(new AttachedConnection(output, input, (columns, rows) -> resizes.add(columns + "x" + rows), exited,
            () -> { closes.incrementAndGet(); try { remote.close(); output.close(); } catch (IOException ignored) { } }), new GridSize(40, 10), 100);
        return session;
    }

    private void feed(String text) throws IOException { remote.write(text.getBytes(StandardCharsets.UTF_8)); remote.flush(); }
    private String typed() { return typed.toString(StandardCharsets.UTF_8); }

    @AfterEach void close() { if (session != null) session.close(); }

    @Test void outputReachesTheScreenAndInputAndResizesReachTheConnection() throws Exception {
        attach();
        feed("remote says hi");
        Await.until(() -> "remote says hi".equals(session.internalAccess().lineText(0).strip()), "output on the first row");
        session.write("ls\r");
        session.write(new byte[]{3});
        session.resize(100, 30);
        Await.until(() -> resizes.contains("100x30") && typed().equals("ls\r\u0003"), "input and resize delivered by the writer thread");
        assertThat(session.columns()).isEqualTo(100);
        assertThat(session.foregroundJob()).isEmpty();
        assertThat(session.exitFuture()).isNotDone();
    }

    @Test void shellIntegrationWorksButNeverReportsALocalDirectory() throws Exception {
        attach();
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        session.addListener(new TerminalSessionListener() {
            @Override public void workingDirectoryChanged(Path directory) { events.add("cwd " + directory); }
            @Override public void commandStarted(String command) { events.add("started " + command); }
            @Override public void commandExecuted(String command, OptionalInt exitStatus, Optional<Path> workingDirectory, Duration duration) {
                events.add("finished " + command + " " + exitStatus + " " + workingDirectory);
            }
        });
        feed("\033]7;file://build-host/srv/app\007\033]133;A\007$ \033]133;B\007make\r\n\033]133;C\007ok\r\n\033]133;D;2\007\033]133;A\007$ ");
        Await.until(() -> events.stream().anyMatch(event -> event.startsWith("finished")), "the command finished");
        assertThat(events).containsExactly("started make", "finished make OptionalInt[2] Optional.empty");
        assertThat(session.workingDirectory()).as("a remote path must never look local").isEmpty();
        assertThat(session.shellIntegrationDetected()).isTrue();
    }

    @Test void aNormalExitDrainsCompletesWithTheStatusWritesNoExitLineAndClosesOnce() throws Exception {
        attach();
        feed("last words");
        exited.complete(3);
        remote.close();
        assertThat(session.exitFuture().get(10, TimeUnit.SECONDS)).isEqualTo(3);
        assertThat(session.internalAccess().lineText(0).strip()).isEqualTo("last words");
        assertThat(session.internalAccess().lineText(1).strip()).as("the pane's banner reports the exit, not the buffer").isEmpty();
        assertThat(closes).hasValue(1);
        session.close();
        session.write("late");
        assertThat(closes).hasValue(1);
    }

    @Test void aTransportFailureCompletesTheExitExceptionallyWithItsMessage() throws Exception {
        attach();
        exited.completeExceptionally(new IOException("connection reset by peer"));
        assertThatThrownBy(() -> session.exitFuture().get(10, TimeUnit.SECONDS)).hasRootCauseMessage("The connection ended: connection reset by peer");
        assertThat(closes).hasValue(1);
    }

    @Test void closingTheSessionClosesTheConnectionOnceAndEndsTheReader() throws Exception {
        attach();
        feed("x");
        session.close();
        session.close();
        Await.until(() -> session.exitFuture().isDone(), "the reader ended after close unblocked it");
        assertThat(closes).hasValue(1);
    }

    @Test void aRejectedWriteIsReportedToListeners() throws Exception {
        attach();
        var dropped = new AtomicInteger();
        session.addListener(new TerminalSessionListener() { @Override public void inputDropped() { dropped.incrementAndGet(); } });
        session.write(new byte[TerminalSession.ATTACHED_QUEUE_BYTES + 1]);
        assertThat(dropped).hasValue(1);
    }

    @Test void attachValidatesAndStillOwnsTheConnectionWhenItFails() throws Exception {
        var output = new PipedInputStream(remote);
        var connection = new AttachedConnection(output, OutputStream.nullOutputStream(), (columns, rows) -> { }, exited, closes::incrementAndGet);
        assertThatThrownBy(() -> TerminalSession.attach(connection, new GridSize(40, 10), -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(closes).as("ownership transferred at the call").hasValue(1);
        assertThatThrownBy(() -> new AttachedConnection(null, OutputStream.nullOutputStream(), (columns, rows) -> { }, exited, () -> { }))
            .isInstanceOf(NullPointerException.class);
    }
}
