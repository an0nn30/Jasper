package dev.jasper.app.startup;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Process-wide start-up timing. Every method is safe from any thread. Marks are first-write-wins. Times are
 * milliseconds since the operating system started this process, so the launcher and JVM creation count too.
 */
public final class StartupTrace {
    public enum Milestone {
        MAIN_ENTERED, CONFIG_LOADED, TOOLKIT_READY, SHELL_SPAWNED, SHELL_OUTPUT, WINDOW_SHOWN,
        FIRST_PAINT, OUTPUT_PAINTED, PLUGINS_BOUND, WARMED_UP
    }

    /** {@code -Djasper.startupTrace=<file>} also writes the trace as JSON, for the start-up benchmark. */
    public static final String TRACE_FILE_PROPERTY = "jasper.startupTrace";
    static final long CAP_NANOS = TimeUnit.SECONDS.toNanos(10);
    static final int WINDOW_LIMIT = 50;
    static final long UNSET = Long.MIN_VALUE;

    /**
     * What a trace observes and where it reports. {@code anchorMillis} is the time from the process start to
     * {@code anchorNanos}; {@code timer} runs a task after a delay in nanoseconds.
     */
    record Environment(LongSupplier nanos, long anchorNanos, double anchorMillis, Instant processStart, long pid,
                       Path traceFile, Consumer<String> log, Executor background, Supplier<JvmStatus> jvm,
                       BiConsumer<Long, Runnable> timer) { }

    /** Whether this JVM mapped an AOT cache or the default CDS archive. */
    record JvmStatus(String aotCache, String cds) {
        static JvmStatus of(String aotCacheOption, boolean sharing) {
            boolean configured = aotCacheOption != null && !aotCacheOption.isEmpty();
            return new JvmStatus(!configured ? "none" : sharing ? "used" : "stale", sharing ? "on" : "off");
        }

        /** Off the EDT: loads the management classes on first use. */
        static JvmStatus observe() {
            boolean sharing = String.valueOf(System.getProperty("java.vm.info")).contains("sharing");
            String configured = "";
            try {
                configured = java.lang.management.ManagementFactory
                    .getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class).getVMOption("AOTCache").getValue();
            } catch (RuntimeException | LinkageError unavailable) {
                // No management module or no such option: report the cache as not configured.
            }
            return of(configured, sharing);
        }
    }

    private static final class Holder {
        static final StartupTrace INSTANCE = new StartupTrace(processEnvironment());
    }

    private final Environment environment;
    private final AtomicLongArray marks = new AtomicLongArray(Milestone.values().length);
    private final Map<String, LongAdder> phases = new ConcurrentHashMap<>();
    private final Map<Milestone, List<Runnable>> waiting = new EnumMap<>(Milestone.class); // guarded by this
    private final Deque<WindowTrace> windows = new ArrayDeque<>(); // guarded by windowsLock
    private final Object windowsLock = new Object();
    private final Object fileLock = new Object();
    private boolean fileFailureReported; // guarded by fileLock
    private final AtomicBoolean settling = new AtomicBoolean();
    private volatile boolean settled;
    private volatile JvmStatus jvm;
    private volatile Supplier<MemoryReading> memory = () -> null;
    private volatile long idleSince = UNSET;

    StartupTrace(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
        for (int i = 0; i < marks.length(); i++) marks.set(i, UNSET);
        long elapsed = (long) (environment.anchorMillis() * 1_000_000) + (environment.nanos().getAsLong() - environment.anchorNanos());
        schedule(CAP_NANOS - elapsed, this::settle);
    }

    /** The process-wide trace; the first call anchors it. */
    public static StartupTrace current() { return Holder.INSTANCE; }

    /** A trace that never logs, writes a file or schedules a timer: for tests and tools. */
    public static StartupTrace detached() {
        long now = System.nanoTime();
        return new StartupTrace(new Environment(System::nanoTime, now, 0, Instant.now(), ProcessHandle.current().pid(),
            null, line -> { }, Runnable::run, () -> JvmStatus.of("", false), (delay, task) -> { }));
    }

    /** The trace file named by the property; null for a blank or malformed value, because tracing must never stop a launch. */
    static Path traceFile(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Path.of(value); }
        catch (java.nio.file.InvalidPathException malformed) { return null; }
    }

    static Environment processEnvironment() {
        long anchor = System.nanoTime();
        Instant now = Instant.now();
        Instant start = ProcessHandle.current().info().startInstant().orElse(now);
        double anchorMillis = Math.max(0, Duration.between(start, now).toNanos() / 1_000_000.0);
        String file = System.getProperty(TRACE_FILE_PROPERTY);
        System.Logger logger = System.getLogger("dev.jasper.app.startup");
        return new Environment(System::nanoTime, anchor, anchorMillis, start, ProcessHandle.current().pid(),
            traceFile(file),
            line -> logger.log(System.Logger.Level.INFO, line),
            task -> Thread.ofVirtual().name("jasper-startup-report").start(task),
            JvmStatus::observe,
            (delay, task) -> Thread.ofVirtual().name("jasper-startup-timer").start(() -> {
                try { TimeUnit.NANOSECONDS.sleep(Math.max(0, delay)); }
                catch (InterruptedException interrupted) { return; }
                task.run();
            }));
    }

    /** Records {@code milestone} now unless it was already recorded; settles once both settling milestones exist. */
    public void mark(Milestone milestone) {
        Objects.requireNonNull(milestone, "milestone");
        if (!marks.compareAndSet(milestone.ordinal(), UNSET, environment.nanos().getAsLong())) return;
        List<Runnable> ready;
        synchronized (this) { ready = waiting.remove(milestone); }
        if (ready != null) {
            for (Runnable dispatch : ready) {
                try {
                    dispatch.run();
                } catch (RuntimeException failure) {
                    environment.log().accept("A start-up trace action failed after " + label(milestone.name()) + ": " + failure.getClass().getName());
                }
            }
        }
        if (marked(Milestone.PLUGINS_BOUND) && (marked(Milestone.OUTPUT_PAINTED) || marked(Milestone.WARMED_UP))) settle();
    }

    /** Adds the time from {@code startNanos} ({@link System#nanoTime}) to now under {@code name}; repeats accumulate. */
    public void phase(String name, long startNanos) {
        Objects.requireNonNull(name, "name");
        phases.computeIfAbsent(name, key -> new LongAdder()).add(Math.max(0, environment.nanos().getAsLong() - startNanos));
    }

    /** Milliseconds from the process start to {@code milestone}; empty until it is marked. */
    public OptionalDouble millis(Milestone milestone) {
        long at = marks.get(milestone.ordinal());
        return at == UNSET ? OptionalDouble.empty() : OptionalDouble.of(sinceStart(at));
    }

    /** Runs {@code action} through {@code executor} once {@code milestone} is marked: at once if it already is. */
    public void when(Milestone milestone, Executor executor, Runnable action) {
        Objects.requireNonNull(milestone, "milestone");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(action, "action");
        Runnable dispatch = () -> executor.execute(action);
        synchronized (this) {
            if (!marked(milestone)) {
                waiting.computeIfAbsent(milestone, key -> new ArrayList<>()).add(dispatch);
                return;
            }
        }
        try {
            dispatch.run();
        } catch (RuntimeException failure) {
            environment.log().accept("A start-up trace action failed after " + label(milestone.name()) + ": " + failure.getClass().getName());
        }
    }

    /**
     * Reads this process's memory for the line of a window opened after a spell with none (see {@link #windowsClosed}),
     * on systems that have the figures; such a window reads it on the EDT when requested and again when shown, so it
     * must be cheap by then. {@code reader} returns null when it cannot read them. A first window never reads it.
     */
    public void readMemoryWith(Supplier<MemoryReading> reader) {
        memory = Objects.requireNonNull(reader, "reader");
    }

    /** The last window has closed: the next window's line reports how long the process sat with none. */
    public void windowsClosed() {
        idleSince = environment.nanos().getAsLong();
    }

    /** This process's memory now, or null. Never throws. */
    MemoryReading memory() {
        try {
            return memory.get();
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** Starts timing a window requested now; it gives up 10 s later if it never paints output. */
    public WindowTrace window(String trigger) {
        long now = environment.nanos().getAsLong();
        long idle = idleSince;
        idleSince = UNSET;
        WindowTrace trace = idle == UNSET ? new WindowTrace(this, trigger, now, UNSET, null)
            : new WindowTrace(this, trigger, now, now - idle, memory());
        synchronized (windowsLock) {
            windows.addLast(trace);
            while (windows.size() > WINDOW_LIMIT) windows.removeFirst();
        }
        schedule(CAP_NANOS, trace::complete);
        return trace;
    }

    /** The last 50 windows, oldest first. */
    public List<WindowTrace> windows() {
        synchronized (windowsLock) { return List.copyOf(windows); }
    }

    /** Idempotent: logs the start-up line once and writes the trace file, on a background thread. */
    public void settle() {
        if (!settling.compareAndSet(false, true)) return;
        environment.background().execute(() -> {
            JvmStatus status = status();
            environment.log().accept(line(status));
            settled = true;
            writeFile();
        });
    }

    void windowCompleted(WindowTrace trace) {
        environment.background().execute(() -> {
            environment.log().accept(trace.line());
            writeFile();
        });
    }

    /** The trace file's content; see the start-up benchmark for its reader. */
    Map<String, Object> document(JvmStatus status) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", 1);
        root.put("pid", environment.pid());
        root.put("processStart", environment.processStart().toString());
        Map<String, Object> vm = new LinkedHashMap<>();
        vm.put("aotCache", status.aotCache());
        vm.put("cds", status.cds());
        root.put("jvm", vm);
        root.put("kind", kind());
        root.put("settled", settled);
        Map<String, Object> milestones = new LinkedHashMap<>();
        for (Milestone milestone : Milestone.values()) millis(milestone).ifPresent(at -> milestones.put(milestone.name(), round(at)));
        root.put("milestones", milestones);
        Map<String, Object> durations = new LinkedHashMap<>();
        phaseMillis().forEach((name, millis) -> durations.put(name, round(millis)));
        root.put("phases", durations);
        root.put("windows", windows().stream().map(WindowTrace::document).toList());
        return root;
    }

    private void writeFile() {
        Path file = environment.traceFile();
        if (file == null) return;
        synchronized (fileLock) {
            try { StartupTraceFile.write(file, document(status())); }
            catch (IOException | RuntimeException failure) {
                if (!fileFailureReported) {
                    fileFailureReported = true;
                    environment.log().accept("Could not write the start-up trace file: " + failure.getClass().getName());
                }
            }
        }
    }

    long nanos() { return environment.nanos().getAsLong(); }

    double sinceStart(long nanos) { return environment.anchorMillis() + (nanos - environment.anchorNanos()) / 1_000_000.0; }

    String kind() { return marked(Milestone.WARMED_UP) ? "background" : "cold"; }

    String line(JvmStatus status) {
        StringBuilder out = new StringBuilder("Startup (").append(kind()).append(", AOT cache ").append(status.aotCache())
            .append(", CDS ").append(status.cds()).append("):");
        String separator = " ";
        for (Milestone milestone : Milestone.values()) {
            OptionalDouble at = millis(milestone);
            if (at.isEmpty()) continue;
            out.append(separator).append(label(milestone.name())).append(' ').append(Math.round(at.getAsDouble())).append(" ms");
            separator = ", ";
        }
        if (separator.equals(" ")) out.append(" no milestones");
        Map<String, Double> durations = phaseMillis();
        if (!durations.isEmpty()) {
            out.append(" |");
            separator = " ";
            for (Map.Entry<String, Double> entry : durations.entrySet()) {
                out.append(separator).append(entry.getKey()).append(' ').append(Math.round(entry.getValue())).append(" ms");
                separator = ", ";
            }
        }
        return out.toString();
    }

    Map<String, Double> phaseMillis() {
        Map<String, Double> result = new TreeMap<>();
        phases.forEach((name, nanos) -> result.put(name, nanos.sum() / 1_000_000.0));
        return result;
    }

    JvmStatus status() {
        JvmStatus known = jvm;
        if (known == null) jvm = known = environment.jvm().get();
        return known;
    }

    boolean isSettled() { return settled; }

    Environment environment() { return environment; }

    static String label(String enumName) { return enumName.toLowerCase(Locale.ROOT).replace('_', ' '); }

    static double round(double millis) { return Math.round(millis * 10) / 10.0; }

    private boolean marked(Milestone milestone) { return marks.get(milestone.ordinal()) != UNSET; }

    private void schedule(long delayNanos, Runnable task) { environment.timer().accept(delayNanos, task); }
}
