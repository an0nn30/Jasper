package dev.jasper.app.application;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.AttachedConnection;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionOutputTest {
    /** A session whose output arrives only when the test writes it. */
    private static final class Feed {
        private final LinkedBlockingQueue<Integer> bytes = new LinkedBlockingQueue<>();
        final TerminalSession session;

        Feed() {
            var exited = new CompletableFuture<Integer>();
            InputStream output = new InputStream() {
                @Override public int read() throws IOException {
                    try { return bytes.take(); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
                }

                @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                    if (length == 0) return 0;
                    int first = read();
                    if (first < 0) return -1;
                    buffer[offset] = (byte) first;
                    int count = 1;
                    Integer next;
                    while (count < length && (next = bytes.peek()) != null && next >= 0) {
                        bytes.poll();
                        buffer[offset + count++] = (byte) (int) next;
                    }
                    return count;
                }
            };
            session = TerminalSession.attach(new AttachedConnection(output, OutputStream.nullOutputStream(),
                (columns, rows) -> { }, exited, () -> { bytes.add(-1); exited.complete(0); }), new GridSize(80, 24), 100);
        }

        void write(String text) { for (byte b : text.getBytes(StandardCharsets.UTF_8)) bytes.add(b & 0xff); }
    }

    @Test void runsTheFirstActionAndWaitersOnceOnTheFirstScreenChange() throws Exception {
        var feed = new Feed();
        try {
            AtomicInteger first = new AtomicInteger();
            AtomicInteger waiter = new AtomicInteger();
            AtomicInteger changes = new AtomicInteger();
            SessionOutput output = SessionOutput.watch(feed.session, first::incrementAndGet);
            output.whenSeen(waiter::incrementAndGet);
            feed.session.addListener(new TerminalSessionListener() {
                @Override public void screenChanged() { changes.incrementAndGet(); }
            });
            assertThat(first).hasValue(0);
            feed.write("hello");
            await(() -> first.get() == 1 && waiter.get() == 1);
            int before = changes.get();
            feed.write(" again");
            await(() -> changes.get() > before);
            assertThat(first).hasValue(1);
            assertThat(waiter).hasValue(1);
            AtomicInteger late = new AtomicInteger();
            output.whenSeen(late::incrementAndGet);
            assertThat(late).as("output already seen: runs at once").hasValue(1);
        } finally {
            feed.session.close();
        }
    }

    @Test void aThrowingFirstActionNeitherLosesWaitersNorStarvesLaterListeners() throws Exception {
        var feed = new Feed();
        try {
            AtomicInteger waiter = new AtomicInteger();
            AtomicInteger later = new AtomicInteger();
            SessionOutput output = SessionOutput.watch(feed.session, () -> { throw new IllegalStateException("boom"); });
            output.whenSeen(waiter::incrementAndGet);
            feed.session.addListener(new TerminalSessionListener() {
                @Override public void screenChanged() { later.incrementAndGet(); }
            });
            feed.write("x");
            await(() -> waiter.get() == 1 && later.get() >= 1);
        } finally {
            feed.session.close();
        }
    }

    @Test void aResizeOnTheEdtIsNotOutput() throws Exception {
        var feed = new Feed();
        try {
            AtomicInteger first = new AtomicInteger();
            AtomicInteger waiter = new AtomicInteger();
            SessionOutput output = SessionOutput.watch(feed.session, first::incrementAndGet);
            output.whenSeen(waiter::incrementAndGet);
            SwingUtilities.invokeAndWait(() -> feed.session.resize(72, 20));
            SwingUtilities.invokeAndWait(() -> { });
            assertThat(first).as("a resize is not the shell's first output").hasValue(0);
            assertThat(waiter).hasValue(0);
            feed.write("prompt");
            await(() -> first.get() == 1 && waiter.get() == 1);
        } finally {
            feed.session.close();
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) throw new AssertionError("Condition did not hold within 5 seconds");
            Thread.sleep(5);
        }
    }
}
