package dev.jasper.terminal.internal.transport;

import dev.jasper.terminal.testsupport.Await;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AttachedTransportTest {
    private final PipedOutputStream remote = new PipedOutputStream();
    private final CompletableFuture<Integer> exited = new CompletableFuture<>();
    private final AtomicInteger closes = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();
    private final List<String> sent = Collections.synchronizedList(new ArrayList<>());
    /** Counted down when the writer thread enters its first flush, so a test knows the queue is empty again. */
    private final CountDownLatch flushing = new CountDownLatch(1);
    private AttachedTransport transport;

    /** Records writes and resizes in the order the writer thread delivers them; {@code gate} can hold the first write. */
    private AttachedTransport open(CountDownLatch gate, OutputStream input, int capacity, Duration drain) throws IOException {
        var output = new PipedInputStream(remote, 1 << 16);
        OutputStream sink = input != null ? input : new OutputStream() {
            private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
            @Override public void write(int value) { pending.write(value); }
            @Override public void flush() throws IOException {
                flushing.countDown();
                try { if (gate != null) gate.await(); } catch (InterruptedException interrupted) { throw new IOException(interrupted); }
                sent.add("write:" + pending.toString(StandardCharsets.UTF_8));
                pending.reset();
            }
        };
        transport = new AttachedTransport(output, sink, (columns, rows) -> sent.add("resize:" + columns + "x" + rows), exited,
            () -> { closes.incrementAndGet(); try { remote.close(); output.close(); } catch (IOException ignored) { } },
            dropped::incrementAndGet, capacity, drain);
        transport.start();
        return transport;
    }

    @AfterEach void close() { if (transport != null) transport.close(); }

    @Test void outputIsDecodedAsUtf8AcrossReads() throws Exception {
        open(null, null, 1024, Duration.ofSeconds(5));
        byte[] bytes = "héllo".getBytes(StandardCharsets.UTF_8);
        remote.write(bytes, 0, 2);
        remote.flush();
        char[] buffer = new char[16];
        assertThat(transport.read(buffer, 0, buffer.length)).isEqualTo(1);
        remote.write(bytes, 2, bytes.length - 2);
        remote.flush();
        int read = transport.read(buffer, 1, buffer.length - 1);
        assertThat(new String(buffer, 0, 1 + read)).isEqualTo("héllo");
        assertThat(transport.connected()).isTrue();
    }

    @Test void writesAndResizesKeepTheirOrderAndQueuedResizesCoalesce() throws Exception {
        var gate = new CountDownLatch(1);
        open(gate, null, 1024, Duration.ofSeconds(5));
        transport.write("one".getBytes(StandardCharsets.UTF_8));
        assertThat(flushing.await(10, java.util.concurrent.TimeUnit.SECONDS)).as("the writer blocks in the first flush").isTrue();
        transport.resize(100, 30);
        transport.resize(120, 40);
        transport.write("two".getBytes(StandardCharsets.UTF_8));
        transport.resize(80, 24);
        gate.countDown();
        Await.until(() -> sent.size() == 4, "everything delivered");
        assertThat(sent).containsExactly("write:one", "resize:120x40", "write:two", "resize:80x24");
    }

    @Test void aWriteThatDoesNotFitIsDroppedWholeAndNothingBlocks() throws Exception {
        var gate = new CountDownLatch(1);
        open(gate, null, 8, Duration.ofSeconds(5));
        transport.write("1234".getBytes(StandardCharsets.UTF_8));
        assertThat(flushing.await(10, java.util.concurrent.TimeUnit.SECONDS)).as("the writer holds the first write").isTrue();
        transport.write("5678".getBytes(StandardCharsets.UTF_8));
        transport.write("abcdefghij".getBytes(StandardCharsets.UTF_8));
        transport.write("9".getBytes(StandardCharsets.UTF_8));
        transport.write("toolong!!".getBytes(StandardCharsets.UTF_8));
        assertThat(dropped).as("two whole writes were rejected, on the caller's thread, at once").hasValue(2);
        gate.countDown();
        Await.until(() -> sent.size() == 3, "the rest delivered");
        assertThat(sent).containsExactly("write:1234", "write:5678", "write:9");
    }

    @Test void aFailingWriteIsATransportFailureAndClosesOnce() throws Exception {
        open(null, new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("broken pipe"); }
        }, 1024, Duration.ofSeconds(5));
        transport.write("x".getBytes(StandardCharsets.UTF_8));
        Await.until(() -> closes.get() == 1, "closed after the failure");
        assertThat(transport.failure()).hasValueSatisfying(message -> assertThat(message).contains("broken pipe"));
        assertThat(transport.connected()).isFalse();
        transport.close();
        transport.write("late".getBytes(StandardCharsets.UTF_8));
        assertThat(closes).hasValue(1);
        assertThat(dropped).hasValue(0);
    }

    @Test void afterTheRemoteExitsOutputIsDrainedToTheEndThenClosedOnce() throws Exception {
        open(null, null, 1024, Duration.ofSeconds(5));
        remote.write("bye".getBytes(StandardCharsets.UTF_8));
        exited.complete(3);
        remote.close();
        char[] buffer = new char[16];
        assertThat(transport.read(buffer, 0, buffer.length)).isEqualTo(3);
        assertThat(transport.read(buffer, 0, buffer.length)).isEqualTo(-1);
        assertThat(transport.awaitExit()).isEqualTo(3);
        assertThat(transport.failure()).isEmpty();
        assertThat(closes).hasValue(1);
    }

    @Test void aRemoteThatExitsWithoutEndingItsOutputIsCutOffAfterTheDrainWindow() throws Exception {
        open(null, null, 1024, Duration.ofMillis(100));
        exited.complete(0);
        char[] buffer = new char[16];
        // The cut-off ends the blocked read, as end of stream or as an IOException depending on the connection.
        try { while (transport.read(buffer, 0, buffer.length) >= 0) { } } catch (IOException cutOff) { /* equally fine */ }
        assertThat(transport.awaitExit()).isEqualTo(0);
        assertThat(transport.failure()).as("closing our own side is not a failure").isEmpty();
        assertThat(closes).hasValue(1);
    }

    @Test void anUnknownExitAndAFailedExitAreSaidSo() throws Exception {
        open(null, null, 1024, Duration.ofMillis(100));
        remote.close();
        char[] buffer = new char[16];
        assertThat(transport.read(buffer, 0, buffer.length)).isEqualTo(-1);
        assertThat(transport.awaitExit()).isEqualTo(AttachedTransport.UNKNOWN_STATUS);
        assertThat(transport.failure()).hasValueSatisfying(message -> assertThat(message).contains("exit status"));
        assertThat(closes).hasValue(1);
    }

    @Test void aConnectionWhoseExitFailsReportsThatFailure() throws Exception {
        open(null, null, 1024, Duration.ofMillis(100));
        exited.completeExceptionally(new IOException("connection reset"));
        Await.until(() -> closes.get() == 1, "closed after the drain window");
        assertThat(transport.awaitExit()).isEqualTo(AttachedTransport.UNKNOWN_STATUS);
        assertThat(transport.failure()).hasValueSatisfying(message -> assertThat(message).contains("connection reset"));
    }
}
