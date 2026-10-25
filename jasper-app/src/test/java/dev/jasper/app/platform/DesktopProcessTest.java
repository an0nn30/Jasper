package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.platform.DesktopProtocol.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class DesktopProcessTest {
    @Test void retryDelayIsBounded() {
        assertThat(java.util.stream.IntStream.range(0, 8)
            .mapToLong(DesktopProcess::retrySeconds).toArray())
            .containsExactly(1, 2, 4, 8, 16, 30, 30, 30);
    }
    @Test void malformedUtf8SplitOverReadsIsUnavailable() { rejects("malformed"); }
    @Test void oversizedLineWithoutNewlineIsUnavailable() { rejects("oversized"); }
    @Test void childExitIsUnavailable() { rejects("exit"); }
    private void rejects(String mode) {
        try (var f = new Fixture()) {
            var failures = new ArrayList<String>();
            var process = f.start(mode, value -> { }, failures::add);
            f.await(() -> !failures.isEmpty());
            assertThat(failures).hasSize(1);
            process.close(); process.close();
        }
    }
    @Test void stderrFloodDoesNotBlockStdoutAndTailIsBounded() {
        try (var f = new Fixture()) {
            var seen = new ArrayList<Message>();
            var process = f.start("stderr", seen::add, value -> fail(value));
            f.await(() -> !seen.isEmpty());
            process.sample(7, true);
            f.await(() -> seen.stream().anyMatch(PaletteMessage.class::isInstance));
            f.await(() -> process.stderrTail().length() == 16 * 1024);
            assertThat(process.stderrTail()).isEqualTo("x".repeat(16 * 1024));
        }
    }
    @Test void stuckLiveChildTimesOutAndIsTerminated() {
        try (var f = new Fixture()) {
            var failures = new ArrayList<String>(); var seen = new ArrayList<Message>();
            var process = f.start("stuck", seen::add, failures::add);
            f.await(() -> !seen.isEmpty()); process.sample(3, true); f.pump();
            f.time.advance(2, TimeUnit.SECONDS); f.pump();
            assertThat(failures).singleElement().asString().contains("timed out");
            f.await(() -> f.children.stream().noneMatch(Process::isAlive));
        }
    }
    @Test void validReplyRetiresItsTimeoutAndOldTimerCannotFailNextRequest() {
        try (var f = new Fixture()) {
            var seen = new ArrayList<Message>(); var failures = new ArrayList<String>();
            var process = f.start("echo", seen::add, failures::add);
            process.sample(1, true); f.await(() -> palettes(seen).size() == 1);
            f.time.advance(2, TimeUnit.SECONDS); f.pump();
            process.sample(2, false); f.await(() -> palettes(seen).size() == 2);
            assertThat(failures).isEmpty();
            assertThat(palettes(seen)).extracting(p -> p.value().generation()).containsExactly(1L, 2L);
        }
    }
    @Test void rapidReplacementKeepsOnlyNewestPendingSample() {
        try (var f = new Fixture()) {
            var seen = new ArrayList<Message>();
            var process = f.start("echo", seen::add, value -> fail(value));
            f.await(() -> !seen.isEmpty());
            process.sample(1, true);
            for (int i = 2; i <= 1000; i++) process.sample(i, false);
            f.pump(); f.await(() -> palettes(seen).stream().anyMatch(p -> p.value().generation() == 1000));
            assertThat(palettes(seen)).hasSizeLessThanOrEqualTo(2);
        }
    }
    @Test void heartbeatRunsIndependentlyOfSamplingAndDetectsStalledChild() {
        try (var f = new Fixture()) {
            var seen = new ArrayList<Message>(); var failures = new ArrayList<String>();
            f.start("stuck", seen::add, failures::add); f.await(() -> !seen.isEmpty());
            f.time.advance(10, TimeUnit.SECONDS); f.pump();
            f.time.advance(2, TimeUnit.SECONDS); f.pump();
            assertThat(failures).singleElement().asString().contains("timed out");
        }
    }
    @Test void stdinWriteFailureBecomesOneFailureEvent() {
        try (var f = new Fixture()) {
            var seen = new ArrayList<Message>(); var failures = new ArrayList<String>();
            var process = new DesktopProcess(f.time, f.queue::add, f.time::now, command -> {
                Process real = f.launch(command);
                return new ForwardingProcess(real) {
                    public java.io.OutputStream getOutputStream() { return new java.io.OutputStream() {
                        public void write(int value) throws java.io.IOException { throw new java.io.IOException("broken stdin"); }
                    }; }
                };
            });
            f.processes.add(process); process.launch(child("echo"), seen::add, failures::add);
            f.await(() -> !seen.isEmpty()); process.sample(1, true);
            f.await(() -> !failures.isEmpty());
            assertThat(failures).singleElement().asString().contains("write");
        }
    }
    @Test void closeBeforeProcessBuilderReturnsRetiresLateChildAndCallbacks() throws Exception {
        try (var f = new Fixture()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var seen = new ArrayList<Message>(); var failures = new ArrayList<String>();
            var process = new DesktopProcess(f.time, f.queue::add, f.time::now, command -> {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("launcher gate"); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                return f.launch(command);
            });
            f.processes.add(process); process.launch(child("echo"), seen::add, failures::add); f.pump();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            process.close(); process.close(); f.pump(); release.countDown();
            f.await(() -> !f.children.isEmpty());
            f.await(() -> f.children.stream().noneMatch(Process::isAlive));
            f.time.advance(1, TimeUnit.DAYS); f.pump();
            assertThat(seen).isEmpty(); assertThat(failures).isEmpty();
        }
    }
    @Test void replyAlreadyQueuedWinsAgainstItsSimultaneouslyDueTimeout() {
        try (var f = new Fixture()) {
            var seen = new ArrayList<Message>(); var failures = new ArrayList<String>();
            var process = f.start("echo", seen::add, failures::add); f.await(() -> !seen.isEmpty());
            process.sample(6, true); f.pump();
            f.awaitWithoutPump(() -> !f.queue.isEmpty());
            f.time.advance(2, TimeUnit.SECONDS); f.pump();
            assertThat(palettes(seen)).singleElement().extracting(p -> p.value().generation()).isEqualTo(6L);
            assertThat(failures).isEmpty();
        }
    }
    @Test void timeoutWinsAgainstReplyDeliveredAfterRetirement() {
        try (var f = new Fixture()) {
            var seen = new ArrayList<Message>(); var failures = new ArrayList<String>();
            var process = f.start("echo", seen::add, failures::add); f.await(() -> !seen.isEmpty());
            process.sample(6, true); f.pump(); f.awaitWithoutPump(() -> !f.queue.isEmpty());
            Runnable response = f.queue.remove();
            f.time.advance(2, TimeUnit.SECONDS); f.pump(); response.run(); f.pump();
            assertThat(failures).hasSize(1); assertThat(palettes(seen)).isEmpty();
        }
    }
    @Test void relativeHelperCommandsAreRejectedWithoutLaunching() {
        try (var f = new Fixture()) {
            var process = new DesktopProcess(f.time, f.queue::add, f.time::now, f::launch);
            assertThatThrownBy(() -> process.launch(List.of("relative-helper"), value -> { }, value -> { }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("absolute");
            assertThat(f.children).isEmpty(); process.close(); f.pump();
        }
    }
    @Test void shutdownForcesAChildThatIgnoresEofAndTerminationAfterFiveHundredMilliseconds() {
        try (var f = new Fixture()) {
            var seen = new ArrayList<Message>();
            var process = f.start("force", seen::add, value -> fail(value)); f.await(() -> !seen.isEmpty());
            process.close(); f.pump();
            f.time.advance(499, TimeUnit.MILLISECONDS); f.pump(); assertThat(f.children.getFirst().isAlive()).isTrue();
            f.time.advance(1, TimeUnit.MILLISECONDS); f.pump();
            f.await(() -> !f.children.getFirst().isAlive());
        }
    }
    @Test void mismatchedPongDoesNotSatisfyTheOutstandingHeartbeat() {
        try (var f = new Fixture()) {
            var seen = new ArrayList<Message>(); var failures = new ArrayList<String>();
            var process = f.start("wrong-pong", seen::add, failures::add); f.await(() -> !seen.isEmpty());
            f.time.advance(10, TimeUnit.SECONDS); f.pump();
            f.await(() -> process.stderrTail().contains("pong")); f.pump();
            f.time.advance(2, TimeUnit.SECONDS); f.pump();
            assertThat(failures).singleElement().asString().contains("ping timed out");
        }
    }
    static class ForwardingProcess extends Process {
        private final Process child;
        ForwardingProcess(Process child) { this.child = child; }
        public java.io.OutputStream getOutputStream() { return child.getOutputStream(); }
        public java.io.InputStream getInputStream() { return child.getInputStream(); }
        public java.io.InputStream getErrorStream() { return child.getErrorStream(); }
        public int waitFor() throws InterruptedException { return child.waitFor(); }
        public int exitValue() { return child.exitValue(); }
        public void destroy() { child.destroy(); }
        public Process destroyForcibly() { child.destroyForcibly(); return this; }
        public boolean isAlive() { return child.isAlive(); }
    }
    static List<PaletteMessage> palettes(List<Message> seen) {
        return seen.stream().filter(PaletteMessage.class::isInstance).map(PaletteMessage.class::cast).toList();
    }
    static List<String> child(String mode) {
        return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
            DesktopChildMain.class.getProtectionDomain().getCodeSource().getLocation().getPath(), DesktopChildMain.class.getName(), mode);
    }
    static final class Fixture implements AutoCloseable {
        final Scheduler time = new Scheduler();
        final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        final List<Process> children = new CopyOnWriteArrayList<>();
        final List<DesktopProcess> processes = new ArrayList<>();
        DesktopProcess start(String mode, java.util.function.Consumer<Message> messages, java.util.function.Consumer<String> failure) {
            var process = new DesktopProcess(time, queue::add, time::now, command -> {
                Process child = launch(command);
                if (!mode.equals("malformed")) return child;
                return new ForwardingProcess(child) {
                    private final java.io.InputStream split = new java.io.FilterInputStream(child.getInputStream()) {
                        @Override public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
                            return super.read(bytes, offset, Math.min(1, length));
                        }
                    };
                    @Override public java.io.InputStream getInputStream() { return split; }
                };
            });
            processes.add(process); process.launch(child(mode), messages, failure); pump(); return process;
        }
        Process launch(List<String> command) {
            try { Process process = new ProcessBuilder(command).start(); children.add(process); return process; }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
        void pump() { Runnable next; while ((next = queue.poll()) != null) next.run(); }
        void awaitWithoutPump(BooleanSupplier condition) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            do { if (condition.getAsBoolean()) return; Thread.yield(); } while (System.nanoTime() < deadline);
            fail("Timed out awaiting queued pipe callback");
        }
        void await(BooleanSupplier condition) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            do { pump(); if (condition.getAsBoolean()) return; Thread.yield(); } while (System.nanoTime() < deadline);
            fail("Timed out awaiting child state");
        }
        public void close() {
            processes.forEach(DesktopProcess::close); pump();
            time.advance(1, TimeUnit.SECONDS); pump();
            children.forEach(p -> { if (p.isAlive()) p.destroyForcibly(); });
            for (Process child : children) try { assertThat(child.waitFor(5, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            time.shutdownNow();
        }
    }
    /** Virtual time only: callbacks are delivered in deadline/registration order, cancelled jobs are removed. */
    static final class Scheduler extends AbstractExecutorService implements ScheduledExecutorService {
        private long now, sequence; private boolean shutdown;
        private final PriorityQueue<Job<?>> jobs = new PriorityQueue<>();
        synchronized long now() { return now; }
        void advance(long amount, TimeUnit unit) {
            long end = now() + unit.toNanos(amount);
            while (true) {
                Job<?> job;
                synchronized (this) {
                    job = jobs.peek();
                    if (job == null || job.deadline > end) { now = end; return; }
                    jobs.remove(); now = job.deadline;
                }
                job.run();
            }
        }
        public synchronized <V> ScheduledFuture<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
            if (shutdown) throw new RejectedExecutionException();
            var job = new Job<V>(task, now + unit.toNanos(delay), sequence++); jobs.add(job); return job;
        }
        public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) { return schedule(Executors.callable(task), delay, unit); }
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long delay, long period, TimeUnit unit) { throw new UnsupportedOperationException(); }
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay, long period, TimeUnit unit) { throw new UnsupportedOperationException(); }
        public void execute(Runnable task) { schedule(task, 0, TimeUnit.NANOSECONDS); }
        public synchronized void shutdown() { shutdown = true; }
        public synchronized List<Runnable> shutdownNow() { shutdown = true; jobs.clear(); return List.of(); }
        public synchronized boolean isShutdown() { return shutdown; }
        public synchronized boolean isTerminated() { return shutdown && jobs.isEmpty(); }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
        private final class Job<V> extends FutureTask<V> implements ScheduledFuture<V> {
            final long deadline, order;
            Job(Callable<V> task, long deadline, long order) { super(task); this.deadline = deadline; this.order = order; }
            public long getDelay(TimeUnit unit) { return unit.convert(deadline - now(), TimeUnit.NANOSECONDS); }
            public int compareTo(Delayed value) { var other = (Job<?>) value; int compare = Long.compare(deadline, other.deadline); return compare == 0 ? Long.compare(order, other.order) : compare; }
            public boolean cancel(boolean interrupt) { synchronized (Scheduler.this) { jobs.remove(this); } return super.cancel(interrupt); }
        }
    }
}
