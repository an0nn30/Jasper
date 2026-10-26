package dev.jasper.app.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static dev.jasper.app.platform.DesktopProtocol.*;

/** One helper incarnation. State belongs to the serialized manager; pipe operations use independent workers. */
final class DesktopProcess implements AutoCloseable {
    private record Sample(long generation, boolean dark) { }
    private final ScheduledExecutorService timer;
    private final Executor manager;
    private final LongSupplier clock;
    private final Function<List<String>, Process> launcher;
    private final DesktopObservation.Source source;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final byte[] diagnostics = new byte[16 * 1024];
    private int diagnosticEnd, diagnosticSize;
    private volatile Process child;
    private Consumer<Message> messages;
    private Consumer<String> unavailable;
    private ScheduledFuture<?> startup, heartbeat, sampleTimeout, pingTimeout;
    private Sample pending, sampling, completed;
    private boolean sampleQueued;
    private long pingSequence;
    private Long ping;
    private ExecutorService ownedManager;
    private boolean ownedTimer;

    DesktopProcess(ScheduledExecutorService timer, Executor manager, LongSupplier clock,
                   Function<List<String>, Process> launcher) {
        this(timer, manager, clock, launcher, null);
    }
    DesktopProcess(ScheduledExecutorService timer, Executor manager, LongSupplier clock,
                   Function<List<String>, Process> launcher, DesktopObservation.Source source) {
        this.source = source;
        this.timer = Objects.requireNonNull(timer); this.manager = Objects.requireNonNull(manager);
        this.clock = Objects.requireNonNull(clock); this.launcher = Objects.requireNonNull(launcher);
    }
    static DesktopProcess start(List<String> command, Consumer<Message> messages, Consumer<String> unavailable) {
        var manager = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("desktop-process").factory());
        var timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("desktop-timeout").factory());
        var process = new DesktopProcess(timer, manager, System::nanoTime, DesktopProcess::spawn);
        process.ownedManager = manager; process.ownedTimer = true;
        process.launch(command, messages, unavailable); return process;
    }
    static Process spawn(List<String> command) {
        try { return new ProcessBuilder(command).start(); }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    static long retrySeconds(int attempt) { return attempt < 5 ? 1L << Math.max(0, attempt) : 30L; }

    void launch(List<String> command, Consumer<Message> messages, Consumer<String> unavailable) {
        var fixed = List.copyOf(command);
        if (fixed.isEmpty() || !Path.of(fixed.getFirst()).isAbsolute())
            throw new IllegalArgumentException("Desktop helper command must use an absolute path");
        this.messages = Objects.requireNonNull(messages); this.unavailable = Objects.requireNonNull(unavailable);
        manage(() -> {
            if (closed.get()) return;
            startup = deadline(() -> fail("Desktop helper startup timed out"));
            Thread.startVirtualThread(() -> {
                Process launched;
                try { launched = launcher.apply(fixed); }
                catch (RuntimeException failure) { manage(() -> fail("Desktop helper launch failed: " + failure.getMessage())); return; }
                // Close can precede ProcessBuilder returning; never give the late process ownership of callbacks.
                if (closed.get()) { retire(launched); return; }
                if (!manage(() -> {
                    if (closed.get()) { retire(launched); return; }
                    child = launched;
                    Thread.startVirtualThread(() -> stdout(launched.getInputStream()));
                    Thread.startVirtualThread(() -> stderr(launched.getErrorStream()));
                    Thread.startVirtualThread(() -> {
                        try { launched.waitFor(); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        manage(() -> fail("Desktop helper exited"));
                    });
                    scheduleHeartbeat(); flushSample();
                })) retire(launched);
            });
        });
    }
    /** At most one in-flight and one replacement sample; callers never accumulate management jobs. */
    void sample(long generation, boolean dark) {
        DesktopObservation.generation(generation);
        synchronized (this) {
            if (closed.get()) return;
            pending = new Sample(generation, dark);
            if (sampleQueued) return;
            sampleQueued = true;
        }
        manage(() -> { synchronized (this) { sampleQueued = false; } flushSample(); });
    }
    private void flushSample() {
        if (closed.get() || child == null || sampling != null) return;
        synchronized (this) { sampling = pending; pending = null; }
        if (sampling == null) return;
        Sample request = sampling;
        sampleTimeout = deadline(() -> { if (sampling == request) fail("Desktop sample timed out"); });
        write(DesktopProtocol.sample(request.generation(), request.dark()));
    }
    private void scheduleHeartbeat() {
        heartbeat = timer.schedule(() -> manage(() -> {
            if (closed.get()) return;
            long generation = ++pingSequence;
            ping = generation;
            pingTimeout = deadline(() -> { if (ping != null && ping == generation) fail("Desktop ping timed out"); });
            write(DesktopProtocol.ping(generation)); scheduleHeartbeat();
        }), 10, TimeUnit.SECONDS);
    }
    private void write(byte[] command) {
        // Each command is tiny and there are only two outstanding commands. A single pipe writer preserves ordering.
        synchronized (writes) { writes.add(command); if (writing) return; writing = true; }
        Thread.startVirtualThread(() -> {
            while (!closed.get()) {
                byte[] next;
                synchronized (writes) { next = writes.poll(); if (next == null) { writing = false; return; } }
                try { child.getOutputStream().write(next); child.getOutputStream().flush(); }
                catch (IOException | RuntimeException failure) { manage(() -> fail("Desktop helper stdin write failed")); break; }
            }
            synchronized (writes) { writes.clear(); writing = false; }
        });
    }
    private final java.util.ArrayDeque<byte[]> writes = new java.util.ArrayDeque<>();
    private boolean writing;

    private ScheduledFuture<?> deadline(Runnable action) {
        long due = clock.getAsLong() + TimeUnit.SECONDS.toNanos(2);
        return timer.schedule(() -> manage(() -> { if (!closed.get() && clock.getAsLong() >= due) action.run(); }), 2, TimeUnit.SECONDS);
    }
    private void stdout(InputStream input) {
        // Allocation stays fixed, including when a malicious helper omits LF indefinitely.
        byte[] line = new byte[MAX_MESSAGE]; byte[] chunk = new byte[4096]; int size = 0;
        try {
            int count;
            while (!closed.get() && (count = input.read(chunk)) != -1) {
                for (int i = 0; i < count; i++) {
                    int value = chunk[i] & 255;
                    if (value == '\n') {
                        Message decoded = decode(Arrays.copyOf(line, size)); size = 0;
                        // Backpressure bounds callbacks too: one decoded envelope can await management at a time.
                        var handled = new CompletableFuture<Void>();
                        if (!manage(() -> { try { receive(decoded); } finally { handled.complete(null); } })) handled.complete(null);
                        handled.join();
                    } else {
                        if (size == MAX_MESSAGE) throw new IllegalArgumentException("Oversized desktop message");
                        line[size++] = (byte) value;
                    }
                }
            }
            if (!closed.get()) manage(() -> fail("Desktop helper stdout exited"));
        } catch (IOException | IllegalArgumentException failure) {
            manage(() -> fail("Desktop helper output invalid: " + failure.getMessage()));
        }
    }
    private void receive(Message message) {
        if (closed.get()) return;
        cancel(startup); startup = null;
        if (message instanceof Pong pong) {
            if (ping == null || ping != pong.generation()) return;
            ping = null; cancel(pingTimeout); pingTimeout = null; return;
        }
        if (message instanceof PaletteMessage palette) {
            // The owner rejects foreign identities, including unsolicited opposite-polarity messages.
            if (source != null && palette.value().source() != source) { deliver(message); return; }
            long generation = palette.value().generation();
            if (sampling != null && generation == sampling.generation()) {
                completed = sampling; sampling = null; cancel(sampleTimeout); sampleTimeout = null;
                deliver(message); flushSample(); return;
            }
            // Current-only observers report actual desktop polarity for the completed generation.
            if (sampling != null || completed == null || completed.generation() != generation
                || (!palette.value().source().currentPaletteOnly()
                    && palette.value().appearance() != DesktopObservation.Polarity.UNKNOWN
                    && (palette.value().appearance() == DesktopObservation.Polarity.DARK) != completed.dark())) return;
        }
        deliver(message);
    }
    private void deliver(Message message) {
        try { messages.accept(message); }
        catch (RuntimeException failure) { System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Desktop helper listener failed", failure); }
    }
    private void stderr(InputStream input) {
        byte[] chunk = new byte[4096];
        try {
            int count;
            while ((count = input.read(chunk)) != -1) synchronized (diagnostics) {
                for (int i = 0; i < count; i++) {
                    diagnostics[diagnosticEnd] = chunk[i]; diagnosticEnd = (diagnosticEnd + 1) % diagnostics.length;
                    diagnosticSize = Math.min(diagnostics.length, diagnosticSize + 1);
                }
            }
        } catch (IOException ignored) { /* Retirement closes the diagnostic pipe. */ }
    }
    String stderrTail() {
        synchronized (diagnostics) {
            byte[] tail = new byte[diagnosticSize];
            for (int i = 0; i < tail.length; i++) tail[i] = diagnostics[(diagnosticEnd - diagnosticSize + i + diagnostics.length) % diagnostics.length];
            return new String(tail, StandardCharsets.UTF_8);
        }
    }
    private void fail(String reason) {
        if (closed.get()) return;
        Consumer<String> target = unavailable;
        close();
        String bounded = reason.codePointCount(0, reason.length()) <= 1024 ? reason : reason.substring(0, reason.offsetByCodePoints(0, 1024));
        try { target.accept(bounded); }
        catch (RuntimeException failure) { System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Desktop failure listener failed", failure); }
    }
    private boolean manage(Runnable action) {
        try { manager.execute(action); return true; }
        catch (RejectedExecutionException ignored) { return false; }
    }
    private static void cancel(ScheduledFuture<?> task) { if (task != null) task.cancel(false); }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        manage(() -> {
            cancel(startup); cancel(heartbeat); cancel(sampleTimeout); cancel(pingTimeout);
            synchronized (this) { pending = null; }
            synchronized (writes) { writes.clear(); }
            Process process = child; child = null;
            if (process != null) retire(process);
            if (ownedManager != null) ownedManager.shutdown();
            if (ownedTimer) timer.shutdown();
        });
    }
    private void retire(Process process) {
        // Retire callback ownership first, request EOF, then destroy and enforce the bounded grace period.
        try { timer.schedule(() -> { if (process.isAlive()) process.destroyForcibly(); }, 500, TimeUnit.MILLISECONDS); }
        catch (RejectedExecutionException retired) { process.destroyForcibly(); }
        Thread.startVirtualThread(() -> {
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
            process.destroy();
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            try { process.getErrorStream().close(); } catch (IOException ignored) { }
        });
    }
}
