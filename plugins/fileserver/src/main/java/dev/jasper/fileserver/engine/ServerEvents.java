package dev.jasper.fileserver.engine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** How an engine reports; safe from any thread. A sink that throws is ignored so a listener cannot break a server. */
public final class ServerEvents {
    static final Duration PROGRESS_INTERVAL = Duration.ofMillis(250);
    private final Clock clock;
    private final Consumer<ServerEvent> sink;
    /** Shared by every instance: one observer sees the transfers of all servers, so ids must never repeat. */
    private static final AtomicLong IDS = new AtomicLong();

    public ServerEvents(Clock clock, Consumer<ServerEvent> sink) { this.clock = clock; this.sink = sink; }

    public void connected(String remote) { emit(new ServerEvent.Connected(clock.instant(), remote)); }
    public void loginSucceeded(String remote, String user) { emit(new ServerEvent.LoginSucceeded(clock.instant(), remote, user)); }
    public void loginFailed(String remote, String user) { emit(new ServerEvent.LoginFailed(clock.instant(), remote, user)); }
    public void changed(String remote, String description) { emit(new ServerEvent.Changed(clock.instant(), remote, description)); }
    public void error(String remote, String message) { emit(new ServerEvent.Failed(clock.instant(), remote, message)); }

    /** Starts a transfer; {@code total} is -1 when unknown and {@code cancel} drops the client. */
    public Transfer transfer(String remote, ServerEvent.Direction direction, String path, long total, Runnable cancel) {
        var transfer = new Transfer(IDS.incrementAndGet(), remote, direction, path, total, clock.instant());
        emit(new ServerEvent.TransferStarted(transfer.started, remote, transfer.id, direction, path, total, cancel));
        return transfer;
    }

    private void emit(ServerEvent event) {
        try { sink.accept(event); } catch (RuntimeException ignored) { /* a listener cannot break a server */ }
    }

    /** One transfer's progress; reports at most every 250 ms and finishes once. */
    public final class Transfer {
        private final long id;
        private final String remote, path;
        private final ServerEvent.Direction direction;
        private final long total;
        private final Instant started;
        private long bytes;
        private Instant reported;
        private boolean finished;

        private Transfer(long id, String remote, ServerEvent.Direction direction, String path, long total, Instant started) {
            this.id = id; this.remote = remote; this.direction = direction; this.path = path; this.total = total;
            this.started = started; this.reported = started;
        }

        public synchronized void add(long count) {
            bytes += count;
            Instant now = clock.instant();
            if (!now.isBefore(reported.plus(PROGRESS_INTERVAL))) {
                reported = now;
                emit(new ServerEvent.TransferProgress(now, remote, id, bytes, total));
            }
        }

        public synchronized long bytes() { return bytes; }

        public synchronized void finish(boolean complete) {
            if (finished) return;
            finished = true;
            Instant now = clock.instant();
            emit(new ServerEvent.Transferred(now, remote, id, direction, path, bytes, Duration.between(started, now), complete));
        }
    }
}
