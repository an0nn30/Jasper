package dev.jasper.remote.tunnels;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Live numbers for one running tunnel: written by its connection threads, read by the UI once a second. */
public final class Counters {
    private final LongAdder received = new LongAdder(), sent = new LongAdder();
    private final AtomicInteger open = new AtomicInteger();
    private volatile String warning;

    /** Bytes that came back from the far end toward whoever connected. */
    public long received() { return received.sum(); }
    /** Bytes that went from whoever connected toward the far end. */
    public long sent() { return sent.sum(); }
    /** Connections forwarding right now. */
    public int open() { return open.get(); }
    /** Why the most recent connection could not be forwarded, until another one is. */
    public Optional<String> warning() { return Optional.ofNullable(warning); }

    void opened() { open.incrementAndGet(); warning = null; }
    void closed() { open.decrementAndGet(); }
    void received(long bytes) { received.add(bytes); }
    void sent(long bytes) { sent.add(bytes); }
    void refused(String reason) { warning = reason; }
}
