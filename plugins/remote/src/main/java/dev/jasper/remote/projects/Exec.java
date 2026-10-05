package dev.jasper.remote.projects;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;

/**
 * Runs a script as {@code sh -s} on an exec channel and streams the NUL-separated fields it prints after its marker.
 * Output before the marker (from the user's startup files) is ignored. Blocking: call it off the UI thread.
 * The field predicate runs on the SSH library's I/O thread, so it must be fast and must not block; it is never called
 * after {@code run} returns or throws, and an exception it throws makes {@code run} throw an {@code IOException}.
 */
final class Exec {
    static final String COMMAND = "sh -s";
    private static final long POLL_MILLIS = 100;

    /**
     * @param status the exit status, or -1 when unknown or stopped
     * @param started whether the marker arrived
     * @param stopped whether a field asked to stop
     */
    record Outcome(int status, boolean started, boolean stopped) { }

    private Exec() { }

    static Outcome run(ClientSession session, byte[] script, String marker, Predicate<String> field, Duration timeout,
                       BooleanSupplier cancelled) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        var parser = new Fields((marker + "\0").getBytes(StandardCharsets.UTF_8), field);
        ChannelExec channel = session.createExecChannel(COMMAND);
        Outcome outcome;
        try {
            channel.setIn(new ByteArrayInputStream(script));
            channel.setOut(parser);
            channel.setErr(OutputStream.nullOutputStream());
            channel.open().verify(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())));
            outcome = await(channel, parser, deadline, cancelled);
        } finally {
            parser.stop();
            channel.close(true);
        }
        if (parser.failure() != null) throw new IOException("The field filter failed", parser.failure());
        return outcome;
    }

    private static Outcome await(ChannelExec channel, Fields parser, long deadline, BooleanSupplier cancelled) throws IOException {
        while (true) {
            if (parser.stopped()) return new Outcome(-1, true, true);
            if (cancelled.getAsBoolean()) throw new CancellationException();
            long left = deadline - System.nanoTime();
            if (left <= 0) throw new IOException("The remote command timed out");
            long wait = Math.max(1, Math.min(POLL_MILLIS, TimeUnit.NANOSECONDS.toMillis(left)));
            if (channel.waitFor(Set.of(ClientChannelEvent.CLOSED), Duration.ofMillis(wait)).contains(ClientChannelEvent.CLOSED)) break;
        }
        if (parser.stopped()) return new Outcome(-1, true, true);
        Integer status = channel.getExitStatus();
        return new Outcome(status == null ? -1 : status, parser.started(), false);
    }

    /** Splits output after the marker into NUL-terminated UTF-8 fields. MINA writes from one thread at a time. */
    private static final class Fields extends OutputStream {
        private final byte[] marker;
        private final Predicate<String> field;
        private final ByteArrayOutputStream current = new ByteArrayOutputStream();
        private int matched;
        private volatile boolean started;
        private volatile boolean stopped;
        private volatile RuntimeException failure;

        Fields(byte[] marker, Predicate<String> field) { this.marker = marker; this.field = field; }

        boolean started() { return started; }
        boolean stopped() { return stopped; }
        RuntimeException failure() { return failure; }

        /** Ends the parse; once this returns, {@code field} is never called again. */
        synchronized void stop() { stopped = true; }

        @Override public synchronized void write(int value) {
            if (stopped) return;
            byte b = (byte) value;
            if (!started) {
                if (b == marker[matched]) {
                    if (++matched == marker.length) started = true;
                } else matched = b == marker[0] ? 1 : 0;
                return;
            }
            if (b != 0) {
                current.write(b);
                return;
            }
            String text = current.toString(StandardCharsets.UTF_8);
            current.reset();
            try {
                if (!field.test(text)) stopped = true;
            } catch (RuntimeException broken) {
                // Thrown on MINA's I/O thread it would close the whole shared session; hand it to run instead.
                failure = broken;
                stopped = true;
            }
        }

        @Override public synchronized void write(byte[] bytes, int offset, int length) {
            for (int i = offset; i < offset + length && !stopped; i++) write(bytes[i]);
        }
    }
}
