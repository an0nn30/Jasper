package dev.jasper.terminal.internal.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * One attached connection. A stalled remote must never freeze typing, pasting or resizing, so callers only
 * enqueue: one writer thread delivers writes (each followed by a flush) and resizes in submission order,
 * a write that does not fit is rejected whole, and a resize directly behind another queued resize replaces it.
 * After the remote reports its exit, output is read to its end or until the drain window closes the
 * connection. {@code close} reaches the connection exactly once. Unsupported module collaboration API.
 */
public final class AttachedTransport implements AutoCloseable {
    /** The status of a session that ended without a usable exit status. */
    public static final int UNKNOWN_STATUS = Integer.MIN_VALUE;
    private static final System.Logger LOG = System.getLogger(AttachedTransport.class.getName());

    private final Reader reader;
    private final OutputStream input;
    private final BiConsumer<Integer, Integer> resize;
    private final CompletableFuture<Integer> exited;
    private final Runnable closeConnection;
    private final Runnable inputDropped;
    private final int capacity;
    private final Duration drain;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    /** {@code byte[]} is a write, {@code int[2]} a resize. */
    private final ArrayDeque<Object> queue = new ArrayDeque<>();
    private long queuedBytes;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<String> failure = new AtomicReference<>();

    /** Unsupported module collaboration API; used only by terminal owners. */
    public AttachedTransport(InputStream output, OutputStream input, BiConsumer<Integer, Integer> resize, CompletableFuture<Integer> exited,
                             Runnable close, Runnable inputDropped, int capacityBytes, Duration drain) {
        this.reader = new InputStreamReader(Objects.requireNonNull(output, "output"), StandardCharsets.UTF_8);
        this.input = Objects.requireNonNull(input, "input");
        this.resize = Objects.requireNonNull(resize, "resize");
        this.exited = Objects.requireNonNull(exited, "exited");
        this.closeConnection = Objects.requireNonNull(close, "close");
        this.inputDropped = Objects.requireNonNull(inputDropped, "inputDropped");
        if (capacityBytes <= 0) throw new IllegalArgumentException("The outbound queue needs a positive capacity");
        this.capacity = capacityBytes;
        this.drain = Objects.requireNonNull(drain, "drain");
    }

    /** Starts the writer thread and begins watching for the remote's exit. Once only. */
    public void start() {
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("transport already started");
        Thread.ofPlatform().name("jasper-session-writer").daemon().start(this::writeLoop);
        exited.whenComplete((status, error) -> {
            if (error != null) fail("The connection ended: " + describe(error));
            // Final output may still be in flight: the reader goes on to the end of output, but not forever.
            CompletableFuture.delayedExecutor(drain.toMillis(), TimeUnit.MILLISECONDS).execute(this::close);
        });
    }

    private static String describe(Throwable error) {
        Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private void fail(String message) {
        if (failure.compareAndSet(null, message)) LOG.log(System.Logger.Level.INFO, "Attached session transport failure: " + message);
    }

    /** Reader thread. Blocks; {@link #close()} is what unblocks it. */
    public int read(char[] buffer, int offset, int length) throws IOException {
        try { return reader.read(buffer, offset, length); }
        catch (IOException broken) {
            // Our own close, or the cut-off after the drain window, is not the remote's failure.
            if (!closed.get() && !exited.isDone()) fail("Reading from the remote side failed: " + describe(broken));
            throw broken;
        }
    }

    /** Whether a read would not block. */
    public boolean ready() throws IOException { return reader.ready(); }

    /** Any thread. Enqueues the whole write or drops it whole; never blocks and never runs connection code. */
    public void write(byte[] bytes) {
        if (closed.get() || bytes.length == 0) return;
        boolean fits;
        lock.lock();
        try {
            fits = queuedBytes + bytes.length <= capacity;
            if (fits) { queue.add(bytes.clone()); queuedBytes += bytes.length; changed.signal(); }
        } finally { lock.unlock(); }
        if (fits) return;
        try { inputDropped.run(); }
        catch (RuntimeException listenerFailure) { LOG.log(System.Logger.Level.WARNING, "An input-dropped listener failed", listenerFailure); }
    }

    /** Any thread. Only the latest of consecutive queued resizes reaches the connection. */
    public void resize(int columns, int rows) {
        if (closed.get()) return;
        lock.lock();
        try {
            if (queue.peekLast() instanceof int[] pending) { pending[0] = columns; pending[1] = rows; }
            else { queue.add(new int[]{columns, rows}); changed.signal(); }
        } finally { lock.unlock(); }
    }

    private void writeLoop() {
        while (true) {
            Object next;
            lock.lock();
            try {
                while (queue.isEmpty() && !closed.get()) changed.await();
                if (closed.get()) return;
                next = queue.poll();
                if (next instanceof byte[] bytes) queuedBytes -= bytes.length;
            } catch (InterruptedException interrupted) {
                return;
            } finally { lock.unlock(); }
            try {
                if (next instanceof byte[] bytes) { input.write(bytes); input.flush(); }
                else { int[] size = (int[]) next; resize.accept(size[0], size[1]); }
            } catch (IOException | RuntimeException broken) {
                if (!closed.get()) fail("Sending to the remote side failed: " + describe(broken));
                close();
                return;
            }
        }
    }

    /** Whether the connection has not been closed. */
    public boolean connected() { return !closed.get(); }

    /**
     * Reader thread, after the end of output: the remote's exit status, waiting at most the drain window for it.
     * The connection is closed when this returns.
     */
    public int awaitExit() throws InterruptedException {
        try {
            Integer status = exited.get(drain.toMillis(), TimeUnit.MILLISECONDS);
            if (status != null) return status;
            fail("The connection ended without an exit status");
            return UNKNOWN_STATUS;
        } catch (TimeoutException silent) {
            fail("The connection ended without an exit status");
            return UNKNOWN_STATUS;
        } catch (ExecutionException | CancellationException broken) {
            fail("The connection ended: " + describe(broken));
            return UNKNOWN_STATUS;
        } finally { close(); }
    }

    /** The first transport failure, written for the user. */
    public Optional<String> failure() { return Optional.ofNullable(failure.get()); }

    /** Idempotent, any thread, never throws. The connection's close is invoked exactly once. */
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        lock.lock();
        try { queue.clear(); queuedBytes = 0; changed.signalAll(); }
        finally { lock.unlock(); }
        try { closeConnection.run(); }
        catch (RuntimeException broken) { LOG.log(System.Logger.Level.WARNING, "Closing an attached connection failed", broken); }
    }
}
