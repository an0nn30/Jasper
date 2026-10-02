# Start-up Speed Plan 1: Measure — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every Jasper launch records accurate, agent-free start-up timing (one log line, optional JSON), and an opt-in
`startupBench` measures cold and warm launches of a packaged image the way a Dock click does.

**Architecture:** A JDK-only `dev.jasper.app.startup` package at the bottom of the application's package graph holds
`StartupTrace` (process-wide, first-write-wins milestones anchored to the OS process start), `WindowTrace` (one window's
request-to-output timing) and `StartupTraceFile` (atomic JSON write and a small reader). The bootstrap, application,
plugin runtime and a new `TerminalView.afterNextPaint` hook feed it. `StartupBench` in `dev.jasper.app.benchmark`
launches the image through LaunchServices with `JAVA_TOOL_OPTIONS`, reads each run's trace and reports medians.

**Tech Stack:** Java 25 on JBR 25, Swing, JUnit 5 + AssertJ, `com.sun.management.HotSpotDiagnosticMXBean`, macOS `open`.
No new dependencies.

**Spec:** [Start-up speed design](../specs/2026-10-02-jasper-startup-speed-design.md), "Part 1 — Measure". Shared names
with Plans 2, 3A, 3B and 4 come from the contract reproduced in "Interfaces produced" below; later plans use them
verbatim.

**Status:** Written 2026-10-02 against `claude/startup-speed@3b1a1544` (local `main@91400d29` plus the spec). First of
five plans (1 → 2 → 4 → 3A → 3B). Baseline `./gradlew check`: 7,954 tests, 0 failures, 6 skips.

## Global Constraints

- "A `-javaagent` probe distorts exactly the CDS and AOT effects being tuned … so the instrumentation lives in the
  application." No agent, no JVMTI, no `-Xlog` in the product path.
- `StartupTrace` is "a JDK-only recorder in a new package, `dev.jasper.app.startup`, at the bottom of the application's
  package graph so every layer may depend on it": it imports no other Jasper package.
- "First-write-wins timestamps … anchored to the operating system's process start time
  (`ProcessHandle.current().info().startInstant()`, sampled once against `nanoTime`)."
- "Every launch logs one INFO line … written by the log thread once start-up settles (first output and plugins bound,
  or a 10-second cap)". Nothing measurable is added to the critical path: settling, the MXBean lookup and file writes
  run on a background virtual thread, never on the EDT.
- "`-Djasper.startupTrace=<file>` also writes the same data as JSON for the benchmark. There is no exit hook in product
  code."
- "`startupBench` … launches the GUI, so it is user-run or agent-run with the user's per-batch approval, and never part
  of `check`." Agents must ask the user before each batch (see memory/AGENTS.md).
- The app never touches `dev.jasper.terminal.internal` or `TerminalSession.internalAccess()` (bytecode-checked by
  `verifyApplicationArchitecture`); the new `TerminalView` method is part of the supported `view` package.
- Use `./gradlew`, never system Gradle; tests are headless; commits end with the two attribution lines shown in each
  commit step.

## Review Focus

1. A launch where the shell never prints (a broken shell, a command that exits): the trace must still settle at the
   10-second cap and log the milestones it has. Task 1 pins the cap.
2. A trace file path whose directory does not exist, or that cannot be written: start-up must continue and report the
   failure once in the log, never throw into the EDT. Task 2 pins it.
3. A resident process that opens hundreds of windows over a day: window traces must stay bounded (last 50) in memory
   and in the JSON. Task 1 pins it.
4. A second `TerminalView.afterNextPaint` registered while painting, or registration off the EDT: the action runs once,
   after the paint that follows it; off-EDT use throws. Task 3 pins both.
5. The benchmark started while any Jasper (the user's own, or a leftover resident) is running: it must refuse, because
   LaunchServices could route `open -a` to the wrong process. Task 6 pins it.

---

## File map

| File | Responsibility |
|---|---|
| Create `jasper-app/src/main/java/dev/jasper/app/startup/package-info.java` | Package contract: JDK-only, bottom of the graph |
| Create `jasper-app/src/main/java/dev/jasper/app/startup/StartupTrace.java` | Milestones, phases, `when`, settling, the log line, the JSON document, JVM status |
| Create `jasper-app/src/main/java/dev/jasper/app/startup/WindowTrace.java` | One window's request-relative milestones |
| Create `jasper-app/src/main/java/dev/jasper/app/startup/StartupTraceFile.java` | Atomic JSON write; minimal JSON reader for the benchmark |
| Modify `jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalView.java` | `afterNextPaint(Runnable)` |
| Modify `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` | `onFirstTerminal(Consumer<TerminalPane>)` |
| Create `jasper-app/src/main/java/dev/jasper/app/application/SessionOutput.java` | "Has this session printed yet", watched from right after its start |
| Modify `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` | Window traces with triggers, shell and paint milestones, `openOrRaise(trigger)` |
| Modify `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` | Bootstrap milestones and phases, the `handoff` trigger |
| Modify `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java` | `PLUGINS_BOUND` and the `plugins` phase |
| Modify the `package-info.java` of `application`, `bootstrap`, `plugins`, `benchmark` | Allowed outgoing dependency on `dev.jasper.app.startup` |
| Create `jasper-app/src/main/java/dev/jasper/app/benchmark/StartupBenchOptions.java` | Benchmark arguments |
| Create `jasper-app/src/main/java/dev/jasper/app/benchmark/StartupRuns.java` | Launch plans, trace reading, statistics, report |
| Create `jasper-app/src/main/java/dev/jasper/app/benchmark/StartupBench.java` | Entry point |
| Modify `tools/benchmarks/run-macos.sh` | `startup` mode |
| Create `docs/benchmarks/2026-10-02-startup-baseline.md` | Recorded baseline and method |
| Modify `docs/diagnostics.md`, `docs/benchmarks.md`, `docs/app-architecture.md`, `docs/STATUS.md` | Documentation |

## Interfaces produced (the shared contract; later plans rely on these exact names)

```java
package dev.jasper.app.startup;
public final class StartupTrace {
    public enum Milestone { MAIN_ENTERED, CONFIG_LOADED, TOOLKIT_READY, SHELL_SPAWNED, SHELL_OUTPUT, WINDOW_SHOWN,
        FIRST_PAINT, OUTPUT_PAINTED, PLUGINS_BOUND, WARMED_UP }
    public static final String TRACE_FILE_PROPERTY = "jasper.startupTrace";
    public static StartupTrace current();
    public static StartupTrace detached();                       // added here: no log, file or timers; for tests and tools
    public void mark(Milestone milestone);
    public void phase(String name, long startNanos);
    public java.util.OptionalDouble millis(Milestone milestone);
    public void when(Milestone milestone, java.util.concurrent.Executor executor, Runnable action);
    public WindowTrace window(String trigger);
    public java.util.List<WindowTrace> windows();                 // added here: the last 50, oldest first
    public void settle();
}
public final class WindowTrace {
    public enum Milestone { SHOWN, FIRST_PAINT, OUTPUT_PAINTED }
    public void mark(Milestone milestone);
    public String trigger();                                     // added here
    public java.util.OptionalDouble millis(Milestone milestone); // added here: since the request
    public boolean completed();                                  // added here
}
public final class StartupTraceFile {
    public static void write(java.nio.file.Path file, java.util.Map<String, ?> document) throws java.io.IOException;
    public static java.util.Map<String, Object> read(java.nio.file.Path file) throws java.io.IOException;
}
// jasper-terminal, supported view package:
public void afterNextPaint(Runnable action);                     // TerminalView
// jasper-app:
public void onFirstTerminal(java.util.function.Consumer<TerminalPane> action);   // WindowContent
public void openOrRaise(String trigger);                          // JasperApplication (replaces openOrRaise())
public void openOrRaise(java.nio.file.Path directory, String trigger);   // replaces openOrRaise(Path)
```

Window triggers used in this plan: `cold` (the bootstrap's first window), `menu` (any later `newWindow()`), `reopen`
(the macOS reopen event), `handoff` (a launcher handed off over the socket), and `menu` for the resident indicator's
Open. Plan 4 refines the indicator and menu bar triggers.

Benchmark invocation (refines the contract's sketch into options):
`tools/benchmarks/run-macos.sh /abs/Jasper.app startup REVISION /abs/out.json [--mode cold|warm] [--runs N] [--prime N] [--real-home] [--timeout-seconds N]`.

---

### Task 1: `StartupTrace` and `WindowTrace`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/startup/package-info.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/startup/StartupTrace.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/startup/WindowTrace.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/startup/StartupTraceTest.java`

**Interfaces:**
- Consumes: nothing from Jasper.
- Produces: everything in "Interfaces produced" for `StartupTrace` and `WindowTrace` except the JSON file (Task 2).
  Package-private for Task 2 and tests: `StartupTrace(Environment)`, `record Environment(...)`,
  `record JvmStatus(String aotCache, String cds)` with `static JvmStatus of(String aotCacheOption, boolean sharing)` and
  `static JvmStatus observe()`, `String line(JvmStatus)`, `String kind()`, `double sinceStart(long nanos)`,
  `static double round(double)`, `static String label(String enumName)`, `WindowTrace.document()`, `WindowTrace.line()`.

- [ ] **Step 1: Write the failing tests**

```java
// jasper-app/src/test/java/dev/jasper/app/startup/StartupTraceTest.java
package dev.jasper.app.startup;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StartupTraceTest {
    /** A fake clock and sinks: the process started 100 ms before the anchor at nanos 0. */
    static final class Fixture {
        final AtomicLong now = new AtomicLong();
        final List<String> log = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        final List<Runnable> timers = new ArrayList<>();

        StartupTrace trace() { return trace(null); }

        StartupTrace trace(Path file) {
            return new StartupTrace(new StartupTrace.Environment(now::get, 0, 100.0, Instant.parse("2026-10-02T16:35:09.123Z"),
                42, file, log::add, Runnable::run, () -> StartupTrace.JvmStatus.of("/Jasper.app/Contents/app/jasper.aot", true),
                (delay, task) -> { delays.add(delay); timers.add(task); }));
        }

        void at(long millis) { now.set(TimeUnit.MILLISECONDS.toNanos(millis)); }
    }

    @Test void marksAreFirstWriteWinsAndCountFromTheProcessStart() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        fixture.at(5);
        trace.mark(StartupTrace.Milestone.MAIN_ENTERED);
        fixture.at(9);
        trace.mark(StartupTrace.Milestone.MAIN_ENTERED);
        assertThat(trace.millis(StartupTrace.Milestone.MAIN_ENTERED)).hasValue(105.0);
        assertThat(trace.millis(StartupTrace.Milestone.CONFIG_LOADED)).isEmpty();
    }

    @Test void whenRunsOnceOnTheMarkOrAtOnceIfAlreadyMarked() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        AtomicInteger before = new AtomicInteger();
        AtomicInteger after = new AtomicInteger();
        trace.when(StartupTrace.Milestone.FIRST_PAINT, Runnable::run, before::incrementAndGet);
        assertThat(before).hasValue(0);
        trace.mark(StartupTrace.Milestone.FIRST_PAINT);
        trace.mark(StartupTrace.Milestone.FIRST_PAINT);
        assertThat(before).hasValue(1);
        trace.when(StartupTrace.Milestone.FIRST_PAINT, Runnable::run, after::incrementAndGet);
        assertThat(after).hasValue(1);
    }

    @Test void aColdLaunchSettlesOnceWhenOutputIsPaintedAndPluginsAreBound() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        fixture.at(1);
        trace.mark(StartupTrace.Milestone.MAIN_ENTERED);
        fixture.at(0);
        long plugins = fixture.now.get();
        fixture.at(3);
        trace.phase("plugins", plugins);
        fixture.at(300);
        trace.mark(StartupTrace.Milestone.PLUGINS_BOUND);
        assertThat(fixture.log).isEmpty();
        fixture.at(500);
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        trace.settle();
        assertThat(fixture.log).containsExactly(
            "Startup (cold, AOT cache used, CDS on): main entered 101 ms, output painted 600 ms, plugins bound 400 ms"
                + " | plugins 3 ms");
    }

    @Test void aBackgroundLaunchSettlesWhenWarmedUpAndPluginsAreBound() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        fixture.at(200);
        trace.mark(StartupTrace.Milestone.PLUGINS_BOUND);
        fixture.at(250);
        trace.mark(StartupTrace.Milestone.WARMED_UP);
        assertThat(fixture.log).containsExactly(
            "Startup (background, AOT cache used, CDS on): plugins bound 300 ms, warmed up 350 ms");
    }

    @Test void theCapSettlesTenSecondsAfterTheProcessStartWithWhateverWasMarked() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        assertThat(fixture.delays).first().isEqualTo(TimeUnit.MILLISECONDS.toNanos(9_900));
        fixture.at(20);
        trace.mark(StartupTrace.Milestone.MAIN_ENTERED);
        fixture.timers.getFirst().run();
        assertThat(fixture.log).containsExactly("Startup (cold, AOT cache used, CDS on): main entered 120 ms");
    }

    @Test void aWindowReportsItsMilestonesFromTheRequestAndCompletesOnce() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        fixture.at(1_000);
        WindowTrace window = trace.window("reopen");
        fixture.at(1_040);
        window.mark(WindowTrace.Milestone.SHOWN);
        fixture.at(1_070);
        window.mark(WindowTrace.Milestone.FIRST_PAINT);
        fixture.at(1_095);
        window.mark(WindowTrace.Milestone.OUTPUT_PAINTED);
        window.mark(WindowTrace.Milestone.OUTPUT_PAINTED);
        assertThat(window.trigger()).isEqualTo("reopen");
        assertThat(window.millis(WindowTrace.Milestone.SHOWN)).hasValue(40.0);
        assertThat(window.completed()).isTrue();
        assertThat(fixture.log).containsExactly(
            "Window (reopen): shown 40 ms, first paint 70 ms, output painted 95 ms after the request");
        assertThat(window.document()).containsEntry("trigger", "reopen").containsEntry("requested", 1_100.0)
            .containsEntry("SHOWN", 1_140.0).containsEntry("OUTPUT_PAINTED", 1_195.0);
    }

    @Test void aWindowThatNeverPrintsGivesUpAtTheCap() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        WindowTrace window = trace.window("menu");
        window.mark(WindowTrace.Milestone.SHOWN);
        assertThat(fixture.delays).last().isEqualTo(StartupTrace.CAP_NANOS);
        fixture.timers.getLast().run();
        assertThat(window.completed()).isTrue();
        assertThat(fixture.log).containsExactly("Window (menu): shown 0 ms after the request");
    }

    @Test void onlyTheLastFiftyWindowsAreKept() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        for (int i = 0; i < 55; i++) trace.window("menu-" + i);
        assertThat(trace.windows()).hasSize(StartupTrace.WINDOW_LIMIT);
        assertThat(trace.windows().getFirst().trigger()).isEqualTo("menu-5");
    }

    @Test void theJvmStatusNamesAStaleCache() {
        assertThat(StartupTrace.JvmStatus.of("", true)).isEqualTo(new StartupTrace.JvmStatus("none", "on"));
        assertThat(StartupTrace.JvmStatus.of("/a.aot", true)).isEqualTo(new StartupTrace.JvmStatus("used", "on"));
        assertThat(StartupTrace.JvmStatus.of("/a.aot", false)).isEqualTo(new StartupTrace.JvmStatus("stale", "off"));
        assertThat(StartupTrace.JvmStatus.of(null, false)).isEqualTo(new StartupTrace.JvmStatus("none", "off"));
    }

    @Test void aDetachedTraceNeverLogsOrSchedules() {
        StartupTrace trace = StartupTrace.detached();
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        trace.mark(StartupTrace.Milestone.PLUGINS_BOUND);
        trace.window("menu").mark(WindowTrace.Milestone.OUTPUT_PAINTED);
        assertThat(trace.millis(StartupTrace.Milestone.OUTPUT_PAINTED)).isPresent();
        assertThat(trace.windows()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.startup.StartupTraceTest'`
Expected: FAIL — compilation errors, `StartupTrace` and `WindowTrace` do not exist.

- [ ] **Step 3: Write the package and the two classes**

```java
// jasper-app/src/main/java/dev/jasper/app/startup/package-info.java
/**
 * Process-wide start-up timing: first-write-wins milestones anchored to the operating system's process start, phase
 * durations, per-window traces, one log line once start-up settles and an optional JSON file for the start-up
 * benchmark. Every type is safe from any thread; nothing here blocks the EDT.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.startup;
```

```java
// jasper-app/src/main/java/dev/jasper/app/startup/StartupTrace.java
package dev.jasper.app.startup;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
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
    private final AtomicBoolean settling = new AtomicBoolean();
    private volatile boolean settled;
    private volatile JvmStatus jvm;

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

    static Environment processEnvironment() {
        long anchor = System.nanoTime();
        Instant now = Instant.now();
        Instant start = ProcessHandle.current().info().startInstant().orElse(now);
        double anchorMillis = Math.max(0, Duration.between(start, now).toNanos() / 1_000_000.0);
        String file = System.getProperty(TRACE_FILE_PROPERTY);
        System.Logger logger = System.getLogger("dev.jasper.app.startup");
        return new Environment(System::nanoTime, anchor, anchorMillis, start, ProcessHandle.current().pid(),
            file == null || file.isBlank() ? null : Path.of(file),
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
        if (ready != null) ready.forEach(Runnable::run);
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
        dispatch.run();
    }

    /** Starts timing a window requested now; it gives up 10 s later if it never paints output. */
    public WindowTrace window(String trigger) {
        WindowTrace trace = new WindowTrace(this, trigger, environment.nanos().getAsLong());
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

    /** Idempotent: logs the start-up line once, on a background thread. */
    public void settle() {
        if (!settling.compareAndSet(false, true)) return;
        environment.background().execute(() -> {
            JvmStatus status = status();
            environment.log().accept(line(status));
            settled = true;
        });
    }

    void windowCompleted(WindowTrace trace) {
        environment.background().execute(() -> environment.log().accept(trace.line()));
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
```

```java
// jasper-app/src/main/java/dev/jasper/app/startup/WindowTrace.java
package dev.jasper.app.startup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;

/** One window's timing, from the request to its first output painted. Any thread; first write wins. */
public final class WindowTrace {
    public enum Milestone { SHOWN, FIRST_PAINT, OUTPUT_PAINTED }

    private final StartupTrace owner;
    private final String trigger;
    private final long requestedNanos;
    private final AtomicLongArray marks = new AtomicLongArray(Milestone.values().length);
    private final AtomicBoolean completed = new AtomicBoolean();

    WindowTrace(StartupTrace owner, String trigger, long requestedNanos) {
        this.owner = owner;
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.requestedNanos = requestedNanos;
        for (int i = 0; i < marks.length(); i++) marks.set(i, StartupTrace.UNSET);
    }

    /** Records {@code milestone} now unless already recorded; output painted completes the trace. */
    public void mark(Milestone milestone) {
        Objects.requireNonNull(milestone, "milestone");
        if (!marks.compareAndSet(milestone.ordinal(), StartupTrace.UNSET, owner.nanos())) return;
        if (milestone == Milestone.OUTPUT_PAINTED) complete();
    }

    /** What asked for the window: cold, menu, reopen, handoff, … */
    public String trigger() { return trigger; }

    /** Milliseconds from the request to {@code milestone}; empty until it is marked. */
    public OptionalDouble millis(Milestone milestone) {
        long at = marks.get(milestone.ordinal());
        return at == StartupTrace.UNSET ? OptionalDouble.empty() : OptionalDouble.of((at - requestedNanos) / 1_000_000.0);
    }

    /** True once output was painted or the trace gave up. */
    public boolean completed() { return completed.get(); }

    void complete() {
        if (completed.compareAndSet(false, true)) owner.windowCompleted(this);
    }

    String line() {
        StringBuilder out = new StringBuilder("Window (").append(trigger).append("):");
        String separator = " ";
        for (Milestone milestone : Milestone.values()) {
            OptionalDouble at = millis(milestone);
            if (at.isEmpty()) continue;
            out.append(separator).append(StartupTrace.label(milestone.name())).append(' ')
                .append(Math.round(at.getAsDouble())).append(" ms");
            separator = ", ";
        }
        if (separator.equals(" ")) out.append(" nothing shown");
        return out.append(" after the request").toString();
    }

    /** This window's JSON entry: the request and each milestone in milliseconds since the process start. */
    Map<String, Object> document() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("trigger", trigger);
        entry.put("requested", StartupTrace.round(owner.sinceStart(requestedNanos)));
        for (Milestone milestone : Milestone.values()) {
            long at = marks.get(milestone.ordinal());
            if (at != StartupTrace.UNSET) entry.put(milestone.name(), StartupTrace.round(owner.sinceStart(at)));
        }
        return entry;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.startup.StartupTraceTest'`
Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/startup jasper-app/src/test/java/dev/jasper/app/startup/StartupTraceTest.java
git commit -m "feat(startup): add the process-wide start-up trace

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2: The trace file (JSON) and JVM status in it

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/startup/StartupTraceFile.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/startup/StartupTrace.java` (settle and window completion write the file; `document`)
- Test: `jasper-app/src/test/java/dev/jasper/app/startup/StartupTraceFileTest.java`

**Interfaces:**
- Consumes: Task 1's `StartupTrace` package-private members and `WindowTrace.document()`.
- Produces: `StartupTraceFile.write(Path, Map<String, ?>)`, `StartupTraceFile.read(Path)` (objects are
  `LinkedHashMap<String, Object>`, arrays `ArrayList<Object>`, numbers `Double`), and the JSON schema in the contract.

- [ ] **Step 1: Write the failing tests**

```java
// jasper-app/src/test/java/dev/jasper/app/startup/StartupTraceFileTest.java
package dev.jasper.app.startup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupTraceFileTest {
    @TempDir Path temp;

    @Test void aDocumentRoundTripsWithEscapesNestingAndNumbersAsDoubles() throws Exception {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("ok", true);
        inner.put("none", null);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schema", 1);
        document.put("text", "quote \" back \\ line\n tab\t bell\u0007");
        document.put("list", List.of(1.5, "two", false));
        document.put("inner", inner);
        Path file = temp.resolve("nested/trace.json");
        StartupTraceFile.write(file, document);
        Map<String, Object> read = StartupTraceFile.read(file);
        assertThat(read).containsEntry("schema", 1.0).containsEntry("text", "quote \" back \\ line\n tab\t bell\u0007")
            .containsEntry("list", List.of(1.5, "two", false));
        assertThat(map(read.get("inner"))).containsEntry("ok", true).containsEntry("none", null);
        try (var listing = Files.list(file.getParent())) { assertThat(listing.toList()).containsExactly(file); }
    }

    @Test void readingRejectsAnythingButAWellFormedObject() throws Exception {
        Path array = temp.resolve("array.json");
        Files.writeString(array, "[1, 2]");
        assertThatThrownBy(() -> StartupTraceFile.read(array)).isInstanceOf(IOException.class);
        Path broken = temp.resolve("broken.json");
        Files.writeString(broken, "{\"a\": }");
        assertThatThrownBy(() -> StartupTraceFile.read(broken)).isInstanceOf(IOException.class);
    }

    @Test void settlingWritesTheDocumentAndEachCompletedWindowRewritesIt() throws Exception {
        var fixture = new StartupTraceTest.Fixture();
        Path file = temp.resolve("trace.json");
        StartupTrace trace = fixture.trace(file);
        fixture.at(400);
        trace.mark(StartupTrace.Milestone.PLUGINS_BOUND);
        fixture.at(500);
        WindowTrace window = trace.window("cold");
        fixture.at(520);
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        Map<String, Object> settled = StartupTraceFile.read(file);
        assertThat(settled).containsEntry("schema", 1.0).containsEntry("pid", 42.0)
            .containsEntry("processStart", "2026-10-02T16:35:09.123Z").containsEntry("kind", "cold")
            .containsEntry("settled", true);
        assertThat(map(settled.get("jvm"))).containsEntry("aotCache", "used").containsEntry("cds", "on");
        assertThat(map(settled.get("milestones"))).containsEntry("PLUGINS_BOUND", 500.0)
            .containsEntry("OUTPUT_PAINTED", 620.0);
        fixture.at(530);
        window.mark(WindowTrace.Milestone.OUTPUT_PAINTED);
        List<?> windows = (List<?>) StartupTraceFile.read(file).get("windows");
        assertThat(windows).singleElement().satisfies(entry -> assertThat(map(entry))
            .containsEntry("trigger", "cold").containsEntry("requested", 600.0).containsEntry("OUTPUT_PAINTED", 630.0));
    }

    @Test void anUnwritableTraceFileIsReportedOnceAndNeverThrows() throws Exception {
        var fixture = new StartupTraceTest.Fixture();
        Path blocked = temp.resolve("a-file");
        Files.writeString(blocked, "not a directory");
        StartupTrace trace = fixture.trace(blocked.resolve("trace.json"));
        trace.mark(StartupTrace.Milestone.PLUGINS_BOUND);
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        assertThat(fixture.log).hasSize(2);
        assertThat(fixture.log.get(1)).startsWith("Could not write the start-up trace ");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.startup.StartupTraceFileTest'`
Expected: FAIL — `StartupTraceFile` does not exist.

- [ ] **Step 3: Write `StartupTraceFile` and connect the trace to it**

```java
// jasper-app/src/main/java/dev/jasper/app/startup/StartupTraceFile.java
package dev.jasper.app.startup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The start-up trace's JSON file: written atomically by the application, read back by the start-up benchmark. */
public final class StartupTraceFile {
    private StartupTraceFile() { }

    /** Writes {@code document} to {@code file} through a sibling temporary file and a move. */
    public static void write(Path file, Map<String, ?> document) throws IOException {
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".startup-trace-", ".tmp");
        try {
            Files.writeString(temporary, json(document) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Reads a JSON object: objects become maps, arrays lists, numbers doubles. */
    public static Map<String, Object> read(Path file) throws IOException {
        Object value = new Parser(Files.readString(file, StandardCharsets.UTF_8)).document();
        if (!(value instanceof Map<?, ?>)) throw new IOException("A start-up trace is a JSON object: " + file);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number number) return Double.isFinite(number.doubleValue()) ? number.toString() : "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> members = new ArrayList<>();
            map.forEach((key, member) -> members.add(json(String.valueOf(key)) + ":" + json(member)));
            return "{" + String.join(",", members) + "}";
        }
        if (value instanceof Collection<?> list) return "[" + String.join(",", list.stream().map(StartupTraceFile::json).toList()) + "]";
        StringBuilder out = new StringBuilder("\"");
        for (char ch : value.toString().toCharArray()) {
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        return out.append('"').toString();
    }

    private static final class Parser {
        private final String text;
        private int at;

        Parser(String text) { this.text = text; }

        Object document() throws IOException {
            Object value = value();
            skip();
            if (at != text.length()) throw error("trailing text");
            return value;
        }

        private Object value() throws IOException {
            skip();
            if (at >= text.length()) throw error("unexpected end");
            return switch (text.charAt(at)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() throws IOException {
            Map<String, Object> result = new LinkedHashMap<>();
            at++;
            skip();
            if (peek('}')) { at++; return result; }
            while (true) {
                skip();
                if (!peek('"')) throw error("expected a member name");
                String key = string();
                skip();
                expect(':');
                result.put(key, value());
                skip();
                if (peek(',')) { at++; continue; }
                expect('}');
                return result;
            }
        }

        private List<Object> array() throws IOException {
            List<Object> result = new ArrayList<>();
            at++;
            skip();
            if (peek(']')) { at++; return result; }
            while (true) {
                result.add(value());
                skip();
                if (peek(',')) { at++; continue; }
                expect(']');
                return result;
            }
        }

        private String string() throws IOException {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char ch = text.charAt(at++);
                if (ch == '"') return out.toString();
                if (ch != '\\') { out.append(ch); continue; }
                if (at >= text.length()) break;
                char escaped = text.charAt(at++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (at + 4 > text.length()) throw error("short unicode escape");
                        try { out.append((char) Integer.parseInt(text.substring(at, at + 4), 16)); }
                        catch (NumberFormatException bad) { throw error("bad unicode escape"); }
                        at += 4;
                    }
                    default -> throw error("bad escape");
                }
            }
            throw error("unterminated string");
        }

        private Double number() throws IOException {
            int start = at;
            while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
            if (start == at) throw error("unexpected character");
            try { return Double.valueOf(text.substring(start, at)); }
            catch (NumberFormatException bad) { throw error("bad number"); }
        }

        private Object literal(String word, Object value) throws IOException {
            if (!text.startsWith(word, at)) throw error("unexpected character");
            at += word.length();
            return value;
        }

        private boolean peek(char expected) { return at < text.length() && text.charAt(at) == expected; }

        private void expect(char expected) throws IOException {
            if (!peek(expected)) throw error("expected '" + expected + "'");
            at++;
        }

        private void skip() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }

        private IOException error(String message) { return new IOException(message + " at offset " + at); }
    }
}
```

In `StartupTrace.java`, add the imports `java.io.IOException` and `java.util.LinkedHashMap`, a field
`private final Object fileLock = new Object();`, and replace `settle()` and `windowCompleted(...)`:

```java
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
                environment.log().accept("Could not write the start-up trace " + file + ": " + failure.getMessage());
            }
        }
    }
```

The `Environment` record keeps no extra field; `isSettled()` and `environment()` stay for later plans' tests.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.startup.*'`
Expected: PASS, 14 tests (10 from Task 1, 4 new).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/startup jasper-app/src/test/java/dev/jasper/app/startup/StartupTraceFileTest.java
git commit -m "feat(startup): write the start-up trace as JSON for the benchmark

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 3: `TerminalView.afterNextPaint`

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalView.java` (a field, the method, the end of
  `paintComponent` at line ~409)
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalAfterPaintTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `public void afterNextPaint(Runnable action)` on `TerminalView` — EDT only; the action is posted with
  `SwingUtilities.invokeLater` once, after the next `paintComponent` of this view completes.

- [ ] **Step 1: Write the failing test**

```java
// jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalAfterPaintTest.java
package dev.jasper.terminal.view;

import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.emulation.FakeConnector;
import dev.jasper.terminal.session.TerminalSession;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminalAfterPaintTest {
    private TerminalSession session;
    private TerminalView view;

    @BeforeEach void setUp() throws Exception {
        session = EmulationFixture.unstarted(new FakeConnector(), 20, 4, 100);
        session.internalAccess().startReading();
        onEdt(() -> {
            view = new TerminalView(session, TerminalOptions.defaults());
            view.setSize(view.getPreferredSize());
            view.addNotify();
        });
    }

    @AfterEach void tearDown() throws Exception {
        onEdt(() -> view.removeNotify());
        session.close();
    }

    @Test void runsOnceAfterTheNextPaintOnly() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        onEdt(() -> view.afterNextPaint(runs::incrementAndGet));
        onEdt(() -> { });
        assertThat(runs).hasValue(0);
        onEdt(this::paint);
        onEdt(() -> { });
        assertThat(runs).hasValue(1);
        onEdt(this::paint);
        onEdt(() -> { });
        assertThat(runs).hasValue(1);
    }

    @Test void anActionAddedWhileARunningActionExecutesWaitsForTheFollowingPaint() throws Exception {
        AtomicInteger inner = new AtomicInteger();
        onEdt(() -> view.afterNextPaint(() -> view.afterNextPaint(inner::incrementAndGet)));
        onEdt(this::paint);
        onEdt(() -> { });
        assertThat(inner).hasValue(0);
        onEdt(this::paint);
        onEdt(() -> { });
        assertThat(inner).hasValue(1);
    }

    @Test void registeringOffTheEdtIsRefused() {
        assertThatThrownBy(() -> view.afterNextPaint(() -> { })).isInstanceOf(IllegalStateException.class);
    }

    private void paint() {
        var image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { view.paint(graphics); } finally { graphics.dispose(); }
    }

    private static void onEdt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.view.TerminalAfterPaintTest'`
Expected: FAIL — `afterNextPaint` is not defined.

- [ ] **Step 3: Implement**

In `TerminalView`, beside the other private fields, add:

```java
    private final java.util.List<Runnable> afterPaint = new java.util.ArrayList<>();
```

Next to `setOnCloseRequest`, add:

```java
    /** Runs {@code action} on the EDT once, after the next completed paint of this view. EDT only. */
    public void afterNextPaint(Runnable action) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("afterNextPaint requires the EDT");
        afterPaint.add(java.util.Objects.requireNonNull(action, "action"));
    }
```

At the very end of `paintComponent(Graphics g)`, after the `inactiveDim` block, add:

```java
        if (!afterPaint.isEmpty()) {
            var ready = java.util.List.copyOf(afterPaint);
            afterPaint.clear();
            for (Runnable action : ready) SwingUtilities.invokeLater(action);
        }
```

Posting rather than running keeps plugin and application code out of the paint pass and lets the frame reach the
screen first.

- [ ] **Step 4: Run the terminal tests and the architecture check**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.view.*' verifyTerminalArchitecture`
Expected: PASS (the new test's 3 cases and every existing view test); architecture check green.

- [ ] **Step 5: Commit**

```bash
git add jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalView.java jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalAfterPaintTest.java
git commit -m "feat(terminal): let the owner run an action after a view's next paint

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 4: The first-terminal hook and the session output watcher

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (a field, `onFirstTerminal`, the end
  of `configurePane` at line ~762)
- Create: `jasper-app/src/main/java/dev/jasper/app/application/SessionOutput.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowContentFirstTerminalTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/SessionOutputTest.java`

**Interfaces:**
- Consumes: `TerminalPane.view()`, `TerminalPane.session()` (public), `TerminalSession.addListener/removeListener`,
  `TerminalSessionListener.screenChanged()`.
- Produces: `WindowContent.onFirstTerminal(Consumer<TerminalPane>)` (called once per window, on the EDT, when its first
  terminal's view is configured); `SessionOutput.watch(TerminalSession, Runnable first)` and
  `SessionOutput.whenSeen(Runnable)` (package-private, application package).

- [ ] **Step 1: Write the failing tests**

```java
// jasper-app/src/test/java/dev/jasper/app/workspace/WindowContentFirstTerminalTest.java
package dev.jasper.app.workspace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

class WindowContentFirstTerminalTest {
    @AfterEach void close() throws Exception { DesktopTestSupport.closeOwners(); }

    @Test void reportsOnlyTheWindowsFirstConfiguredTerminal() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        List<TerminalPane> seen = new ArrayList<>();
        WindowContent[] content = new WindowContent[1];
        edt(() -> {
            content[0] = DesktopTestSupport.content(DesktopTestSupport.launcher(pending));
            content[0].onFirstTerminal(seen::add);
        });
        pending.poll().run();
        until(() -> seen.size() == 1);
        TerminalPane first = seen.getFirst();
        assertThat(first.view()).isNotNull();
        TerminalTab[] second = new TerminalTab[1];
        edt(() -> second[0] = content[0].openTab(DesktopTestSupport.HOME, null));
        pending.poll().run();
        until(() -> second[0].panes().getFirst().view() != null);
        edt(() -> { });
        assertThat(seen).containsExactly(first);
    }
}
```

```java
// jasper-app/src/test/java/dev/jasper/app/application/SessionOutputTest.java
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

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) throw new AssertionError("Condition did not hold within 5 seconds");
            Thread.sleep(5);
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowContentFirstTerminalTest' --tests 'dev.jasper.app.application.SessionOutputTest'`
Expected: FAIL — `onFirstTerminal` and `SessionOutput` do not exist.

- [ ] **Step 3: Implement**

In `WindowContent`, add the fields next to the other callback fields:

```java
    private java.util.function.Consumer<TerminalPane> firstTerminal = pane -> { };
    private boolean firstTerminalSeen;
```

and the method next to `activity(...)`:

```java
    /** Called once, on the EDT, when this window's first terminal is configured: start-up timing. */
    public void onFirstTerminal(java.util.function.Consumer<TerminalPane> action) {
        firstTerminal = java.util.Objects.requireNonNull(action, "action");
    }
```

At the end of `configurePane(TerminalTab tab, TerminalPane pane)`, after the context-menu handler, add:

```java
        if (!firstTerminalSeen) {
            firstTerminalSeen = true;
            firstTerminal.accept(pane);
        }
```

```java
// jasper-app/src/main/java/dev/jasper/app/application/SessionOutput.java
package dev.jasper.app.application;

import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Whether a session has printed anything yet, watched from right after its start. Any thread. A shell cannot print in
 * the moment between its start returning and this listener attaching, so the first screen change is not missed.
 */
final class SessionOutput implements TerminalSessionListener {
    private final TerminalSession session;
    private final Runnable first;
    private final List<Runnable> waiting = new ArrayList<>(); // guarded by this
    private boolean seen; // guarded by this

    private SessionOutput(TerminalSession session, Runnable first) {
        this.session = session;
        this.first = first;
    }

    /** Starts watching; {@code first} runs once, on the thread that delivers the first screen change. */
    static SessionOutput watch(TerminalSession session, Runnable first) {
        var watch = new SessionOutput(session, first);
        session.addListener(watch);
        return watch;
    }

    @Override public void screenChanged() {
        List<Runnable> ready;
        synchronized (this) {
            if (seen) return;
            seen = true;
            ready = List.copyOf(waiting);
            waiting.clear();
        }
        session.removeListener(this);
        first.run();
        ready.forEach(Runnable::run);
    }

    /** Runs {@code action} once output exists: now if it already does, otherwise on the first screen change. */
    void whenSeen(Runnable action) {
        synchronized (this) {
            if (!seen) {
                waiting.add(action);
                return;
            }
        }
        action.run();
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowContentFirstTerminalTest' --tests 'dev.jasper.app.application.SessionOutputTest'`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java jasper-app/src/main/java/dev/jasper/app/application/SessionOutput.java jasper-app/src/test/java/dev/jasper/app/workspace/WindowContentFirstTerminalTest.java jasper-app/src/test/java/dev/jasper/app/application/SessionOutputTest.java
git commit -m "feat(startup): observe a window's first terminal and a session's first output

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 5: Wire the milestones into the bootstrap, application and plugin runtime

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (`main` line 28, `start` lines
  226–229, `startDesktop` line 76, `createApplication` lines 114–137, the present lambda lines 97–105, `handoffHandler`
  line 295)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (fields; `newWindow` lines
  268–271; `openWindow` lines 274–317; `startSession` lines 647–655; reopen listener line 221; `showIndicator` line
  341; `openOrRaise` lines 380–391)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java` (end of `start`, line ~419)
- Modify: `package-info.java` of `dev.jasper.app.application`, `dev.jasper.app.bootstrap`, `dev.jasper.app.plugins`
- Modify: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationResidencyTest.java` line 126
- Test: `jasper-app/src/test/java/dev/jasper/app/application/WindowTriggerTest.java`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: `JasperApplication.openOrRaise(String trigger)`, `JasperApplication.openOrRaise(Path directory, String
  trigger)` (the no-trigger overloads are removed; MVP, no shims), package-private
  `JasperApplication.trace(StartupTrace)` for tests, package-private `static String windowTrigger(int windowsOpened)`.
  Milestones marked: `MAIN_ENTERED` (main), `CONFIG_LOADED` (after `ConfigService`), `TOOLKIT_READY`
  (`startDesktop`), `SHELL_SPAWNED` and `SHELL_OUTPUT` (each local session the application starts),
  `WINDOW_SHOWN`, `FIRST_PAINT`, `OUTPUT_PAINTED` (windows), `PLUGINS_BOUND` (end of `PluginRuntime.start`),
  `WARMED_UP` (after `warmUp()`). Phases: `theme upgrade`, `config`, `application`, `first window`, `plugins`.

Headless tests cannot open a `TerminalWindow` (it owns a `JFrame`), so the window wiring is covered by Task 4's unit
tests, the small trigger test below and the user-run `startupBench` in Task 7.

- [ ] **Step 1: Write the failing test**

```java
// jasper-app/src/test/java/dev/jasper/app/application/WindowTriggerTest.java
package dev.jasper.app.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowTriggerTest {
    @Test void theProcessesFirstPlainWindowIsTheColdOneAndLaterOnesAreMenuWindows() {
        assertThat(JasperApplication.windowTrigger(0)).isEqualTo("cold");
        assertThat(JasperApplication.windowTrigger(1)).isEqualTo("menu");
        assertThat(JasperApplication.windowTrigger(7)).isEqualTo("menu");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.WindowTriggerTest'`
Expected: FAIL — `windowTrigger` does not exist.

- [ ] **Step 3: Wire the application**

In `JasperApplication`, add imports `dev.jasper.app.startup.StartupTrace`, `dev.jasper.app.startup.WindowTrace`,
`dev.jasper.app.workspace.TerminalPane` and `dev.jasper.terminal.view.TerminalView` (skip any already present), and
these fields beside `private boolean resident;`:

```java
    private volatile StartupTrace trace = StartupTrace.current();
    private int windowsOpened;
    /** The output watch of every local session this application started, until its first terminal claims it. */
    private final java.util.Map<TerminalSession, SessionOutput> outputs =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
```

Add, near `newWindow`:

```java
    /** Tests replace the process-wide trace. EDT. */
    void trace(StartupTrace value) { trace = java.util.Objects.requireNonNull(value, "value"); }

    /** The first plain window of a process is its cold start; later plain windows come from a menu or shortcut. */
    static String windowTrigger(int windowsOpened) { return windowsOpened == 0 ? "cold" : "menu"; }
```

Replace the two `newWindow` methods and the signature of `openWindow`:

```java
    /** A window whose tab starts where a new shell starts: the user's home, which a WSL profile reads as its Linux home. */
    public TerminalWindow newWindow() { return openWindow(null, false, windowTrigger(windowsOpened)); }

    /** A window whose tab starts in {@code directory}; null opens a detached window with no tab. */
    public TerminalWindow newWindow(Path directory) { return openWindow(directory, directory == null, "menu"); }

    private TerminalWindow openWindow(Path directory, boolean detached, String trigger) {
```

In `openWindow`, directly after `if (quitting) return null;`, add:

```java
        WindowTrace windowTrace = trace.window(trigger);
        windowsOpened++;
```

change the session start passed to `windowLauncher` from
`(path, settings) -> track(startSession(path, settings))` to
`(path, settings) -> track(watchOutput(startSession(path, settings)))`,
add after `window.content().onToggleBuddy = this::toggleBuddy;`:

```java
        window.content().onFirstTerminal(pane -> timeFirstTerminal(pane, windowTrace));
```

and replace the final `windows.add(window); window.show();` with:

```java
        windows.add(window); window.show();
        windowTrace.mark(WindowTrace.Milestone.SHOWN);
        trace.mark(StartupTrace.Milestone.WINDOW_SHOWN);
```

Add the two helpers after `startSession`:

```java
    /** Marks the first spawn and watches the session's first output from right after its start. Any thread. */
    private TerminalSession watchOutput(TerminalSession session) {
        trace.mark(StartupTrace.Milestone.SHELL_SPAWNED);
        outputs.put(session, SessionOutput.watch(session, () -> trace.mark(StartupTrace.Milestone.SHELL_OUTPUT)));
        return session;
    }

    /** First paint, then the first paint after output: the window is usable. EDT. */
    private void timeFirstTerminal(TerminalPane pane, WindowTrace windowTrace) {
        TerminalView view = pane.view();
        view.afterNextPaint(() -> {
            windowTrace.mark(WindowTrace.Milestone.FIRST_PAINT);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
        });
        view.repaint();
        SessionOutput output = outputs.remove(pane.session());
        if (output == null) return; // A provider's connection or a supplied test launcher: no output timing.
        output.whenSeen(() -> SwingUtilities.invokeLater(() -> {
            if (pane.view() != view) return;
            view.afterNextPaint(() -> {
                windowTrace.mark(WindowTrace.Milestone.OUTPUT_PAINTED);
                trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
            });
            view.repaint();
        }));
    }
```

Replace both `openOrRaise` methods:

```java
    /**
     * The handoff, the macOS reopen event and the resident indicator share this: raise what is open, or open the
     * first window where a new shell starts. {@code trigger} names the caller for start-up timing.
     */
    public void openOrRaise(String trigger) {
        if (quitting || stopped) return;
        if (windows.isEmpty()) openWindow(null, false, trigger);
        else raiseTerminal();
    }

    /** As {@link #openOrRaise(String)}, opening the first window in {@code directory}. */
    public void openOrRaise(Path directory, String trigger) {
        if (quitting || stopped) return;
        if (windows.isEmpty()) openWindow(directory, directory == null, trigger);
        else raiseTerminal();
    }
```

Update the callers: the reopen listener becomes
`reopenListener = event -> SwingUtilities.invokeLater(() -> openOrRaise("reopen"));` and `showIndicator` becomes
`removeIndicator = residentIndicator.apply(() -> openOrRaise("menu"), this::quit);`. In
`JasperApplicationResidencyTest` line 126 use `application.openOrRaise(DesktopTestSupport.HOME, "handoff")`.

In `ApplicationBootstrap`, import `dev.jasper.app.startup.StartupTrace` and:

```java
    public static void main(String[] args) {
        StartupTrace.current().mark(StartupTrace.Milestone.MAIN_ENTERED);
        int result = start(args, System.out, System.err, ApplicationBootstrap::prepareDesktop);
        if (result != 0) System.exit(result);
    }
```

In `start(...)`, replace the three lines that create `problems`, `acquired` and `service` with:

```java
        long upgrade = System.nanoTime();
        var problems = dev.jasper.app.application.ThemeUpgrade.prepare(file, os.startsWith("Mac"));
        StartupTrace.current().phase("theme upgrade", upgrade);
        var acquired = new StartupResources();
        long read = System.nanoTime();
        ConfigService service = acquired.own(new ConfigService(file, os.startsWith("Mac")));
        StartupTrace.current().phase("config", read);
        StartupTrace.current().mark(StartupTrace.Milestone.CONFIG_LOADED);
```

In `startDesktop`, make the first statement inside `try {`:

```java
            StartupTrace.current().mark(StartupTrace.Milestone.TOOLKIT_READY);
```

In `createApplication`, add `long began = System.nanoTime();` before `var acquired = new StartupResources();` and
`StartupTrace.current().phase("application", began);` immediately before `acquired.transfer();`.

Replace the present lambda's body:

```java
                }, application -> {
                    if (!options.background()) {
                        long window = System.nanoTime();
                        application.newWindow();
                        StartupTrace.current().phase("first window", window);
                        return;
                    }
                    if (!application.resident()) {
                        LOG.log(System.Logger.Level.WARNING, standalone
                            ? "Started with --background, but a --config, --safe-mode, --plugin-dir or --standalone launch is always standalone and cannot be resident; exiting"
                            : "Started with --background but the handoff endpoint is unavailable; exiting");
                        application.quitNow();
                    } else {
                        application.warmUp();
                        StartupTrace.current().mark(StartupTrace.Milestone.WARMED_UP);
                    }
                });
```

and in `handoffHandler` replace `SwingUtilities.invokeLater(application::openOrRaise);` with
`SwingUtilities.invokeLater(() -> application.openOrRaise("handoff"));`.

In `PluginRuntime.start`, import `dev.jasper.app.startup.StartupTrace` and after the `"Plugins started in "` log line
add:

```java
        StartupTrace.current().phase("plugins", began);
        StartupTrace.current().mark(StartupTrace.Milestone.PLUGINS_BOUND);
```

Add `dev.jasper.app.startup` to the "Allowed outgoing Jasper dependencies" sentence of three `package-info.java`
files, keeping each list's order: in `dev.jasper.app.application` and `dev.jasper.app.plugins` insert
`dev.jasper.app.startup, ` immediately before `dev.jasper.app.terminals`; in `dev.jasper.app.bootstrap` insert it
immediately before `dev.jasper.app.workspace`.

- [ ] **Step 4: Run the affected tests and the architecture check**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.plugins.*' verifyApplicationArchitecture`
Expected: PASS (`WindowTriggerTest` plus every existing test in those packages); architecture check green (the new
package has no outgoing Jasper edges, so no cycle).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/bootstrap jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/application
git commit -m "feat(startup): mark the start-up milestones and time every window

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 6: `startupBench`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/benchmark/StartupBenchOptions.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/benchmark/StartupRuns.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/benchmark/StartupBench.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/benchmark/package-info.java`
- Modify: `tools/benchmarks/run-macos.sh`
- Test: `jasper-app/src/test/java/dev/jasper/app/benchmark/StartupBenchTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/benchmark/BenchmarkScriptTest.java`

**Interfaces:**
- Consumes: `StartupTraceFile.read`, `StartupTrace.TRACE_FILE_PROPERTY`, `BenchmarkReport.writeJson` (same package).
- Produces: `StartupBench.main`; package-private `StartupBenchOptions.parse(String[])`, `StartupRuns.parseRss(String)`,
  `StartupRuns.launchOptions(Path traceFile, Path home)`, `StartupRuns.openCommand(Path app, String javaToolOptions,
  boolean background)`, `StartupRuns.reopenCommand(Path app)`, `StartupRuns.fromClick(Map<String, Object> trace, long
  clickMillis, String trigger)`, `StartupRuns.summary(List<Map<String, Double>>)`, and `record StartupRuns.Host(...)` for
  process control.

- [ ] **Step 1: Write the failing tests**

```java
// jasper-app/src/test/java/dev/jasper/app/benchmark/StartupBenchTest.java
package dev.jasper.app.benchmark;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupBenchTest {
    @Test void optionsDefaultToTenColdRunsWithOnePrimingRunInAnIsolatedHome() {
        var options = StartupBenchOptions.parse(new String[] {"--app", "/A/Jasper.app", "--output", "/r/out.json", "--revision", "r1"});
        assertThat(options).isEqualTo(new StartupBenchOptions(Path.of("/A/Jasper.app"), Path.of("/r/out.json"), "r1",
            "cold", 10, 1, false, 45));
    }

    @Test void optionsRejectUnknownModesBadCountsAndAWarmRealHome() {
        String[] base = {"--app", "/A.app", "--output", "/o.json", "--revision", "r"};
        assertThatThrownBy(() -> StartupBenchOptions.parse(join(base, "--mode", "hot"))).hasMessageContaining("--mode");
        assertThatThrownBy(() -> StartupBenchOptions.parse(join(base, "--runs", "0"))).hasMessageContaining("--runs");
        assertThatThrownBy(() -> StartupBenchOptions.parse(join(base, "--mode", "warm", "--real-home")))
            .hasMessageContaining("isolated");
        assertThatThrownBy(() -> StartupBenchOptions.parse(new String[] {"--app", "/A.app"})).hasMessageContaining("required");
        assertThatThrownBy(() -> StartupBenchOptions.parse(join(base, "--app", "/B.app"))).hasMessageContaining("unique");
    }

    @Test void launchesGoThroughLaunchServicesWithTheTraceAndAnIsolatedHome() {
        String options = StartupRuns.launchOptions(Path.of("/tmp/jsb1/trace-3.json"), Path.of("/tmp/jsb1/home"));
        assertThat(options).isEqualTo("-Djasper.startupTrace=/tmp/jsb1/trace-3.json -Duser.home=/tmp/jsb1/home");
        assertThat(StartupRuns.launchOptions(Path.of("/tmp/t.json"), null)).isEqualTo("-Djasper.startupTrace=/tmp/t.json");
        assertThat(StartupRuns.openCommand(Path.of("/Apps/My Jasper.app"), options, false)).containsExactly(
            "open", "-n", "-a", "/Apps/My Jasper.app", "--env", "JAVA_TOOL_OPTIONS=" + options);
        assertThat(StartupRuns.openCommand(Path.of("/A.app"), options, true)).containsExactly(
            "open", "-n", "-a", "/A.app", "--env", "JAVA_TOOL_OPTIONS=" + options, "--args", "--background");
        assertThat(StartupRuns.reopenCommand(Path.of("/A.app"))).containsExactly("open", "-a", "/A.app");
    }

    @Test void milestonesAreRebasedFromTheProcessStartToTheClick() {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("processStart", "2026-10-02T16:35:09.100Z");
        trace.put("milestones", Map.of("MAIN_ENTERED", 70.0, "OUTPUT_PAINTED", 500.0));
        trace.put("windows", List.of(Map.of("trigger", "cold", "requested", 300.0, "OUTPUT_PAINTED", 500.0),
            Map.of("trigger", "reopen", "requested", 5_000.0, "SHOWN", 5_040.0, "OUTPUT_PAINTED", 5_090.0)));
        long click = java.time.Instant.parse("2026-10-02T16:35:09.080Z").toEpochMilli();
        assertThat(StartupRuns.fromClick(trace, click, null)).containsEntry("PROCESS_START", 20.0)
            .containsEntry("MAIN_ENTERED", 90.0).containsEntry("OUTPUT_PAINTED", 520.0);
        long reopen = java.time.Instant.parse("2026-10-02T16:35:14.090Z").toEpochMilli();
        assertThat(StartupRuns.fromClick(trace, reopen, "reopen")).containsExactly(
            Map.entry("REQUESTED", 10.0), Map.entry("SHOWN", 50.0), Map.entry("OUTPUT_PAINTED", 100.0));
    }

    @Test void refusesWhileAnyJasperIsRunningAndLaunchesNothing() throws Exception {
        List<List<String>> commands = new java.util.ArrayList<>();
        var host = new StartupRuns.Host(command -> { commands.add(command); return 0; }, () -> 0L, pid -> true, pid -> 0L);
        var options = StartupBenchOptions.parse(new String[] {"--app", "/A.app", "--output", "/tmp/never.json", "--revision", "r"});
        assertThat(new StartupRuns(options, host).run()).isEqualTo(3);
        assertThat(commands).containsExactly(List.of("pgrep", "-f", "/Contents/MacOS/Jasper"));
    }

    @Test void residentMemoryComesFromPsOutputInKibibytes() {
        assertThat(StartupRuns.parseRss("  412345\n")).isEqualTo(412_345L);
        assertThat(StartupRuns.parseRss("")).isEqualTo(-1L);
        assertThat(StartupRuns.parseRss("rss")).isEqualTo(-1L);
    }

    @Test void theSummaryGivesCountMedianMinimumAndMaximumPerMilestone() {
        var summary = StartupRuns.summary(List.of(Map.of("A", 10.0, "B", 1.0), Map.of("A", 30.0), Map.of("A", 20.0)));
        assertThat(summary.get("A")).containsEntry("count", 3).containsEntry("median", 20.0)
            .containsEntry("min", 10.0).containsEntry("max", 30.0);
        assertThat(summary.get("B")).containsEntry("count", 1).containsEntry("median", 1.0);
    }

    private static String[] join(String[] base, String... more) {
        String[] result = java.util.Arrays.copyOf(base, base.length + more.length);
        System.arraycopy(more, 0, result, base.length, more.length);
        return result;
    }
}
```

Add a second test method to `BenchmarkScriptTest`:

```java
    @Test void macScriptRunsTheStartupBenchWithTheImagePath() throws Exception {
        Path image = temp.resolve("image with spaces/Jasper.app");
        Path runtime = image.resolve("Contents/runtime/Contents/Home/bin/java");
        Files.createDirectories(runtime.getParent());
        Files.writeString(runtime, "#!/bin/sh\nprintf '%s\\n' \"$@\"\n");
        assertThat(runtime.toFile().setExecutable(true)).isTrue();
        Path jar = image.resolve("Contents/app/jasper-app.jar"); Files.createDirectories(jar.getParent());
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("dev/jasper/app/benchmark/StartupBench.class")); out.closeEntry();
        }
        var process = new ProcessBuilder("/bin/sh", Path.of(System.getProperty("jasper.repoRoot"),
            "tools/benchmarks/run-macos.sh").toString(), image.toString(), "startup", "revision",
            temp.resolve("output.json").toString(), "--mode", "warm").redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
        assertThat(output.lines().toList()).contains("dev.jasper.app.benchmark.StartupBench", "--app", image.toString(),
            "--mode", "warm");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.benchmark.StartupBenchTest' --tests 'dev.jasper.app.benchmark.BenchmarkScriptTest'`
Expected: FAIL — the new classes do not exist; the script rejects `startup`.

- [ ] **Step 3: Implement the options, the runs and the entry point**

```java
// jasper-app/src/main/java/dev/jasper/app/benchmark/StartupBenchOptions.java
package dev.jasper.app.benchmark;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Bounded inputs of the opt-in start-up benchmark. */
record StartupBenchOptions(Path app, Path output, String revision, String mode, int runs, int prime, boolean realHome,
                           int timeoutSeconds) {
    static StartupBenchOptions parse(String[] args) {
        Map<String, String> values = new HashMap<>();
        boolean realHome = false;
        Set<String> keys = Set.of("app", "output", "revision", "mode", "runs", "prime", "timeout-seconds");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--real-home")) { realHome = true; continue; }
            if (!args[i].startsWith("--") || !keys.contains(args[i].substring(2)) || i + 1 == args.length
                || args[i + 1].startsWith("--") || values.putIfAbsent(args[i].substring(2), args[i + 1]) != null)
                throw new IllegalArgumentException("Expected unique --option value pairs; invalid option: " + args[i]);
            i++;
        }
        String app = values.get("app"), output = values.get("output"), revision = values.get("revision");
        if (app == null || output == null || revision == null || revision.isBlank())
            throw new IllegalArgumentException("--app PATH, --output PATH and --revision LABEL are required");
        String mode = values.getOrDefault("mode", "cold");
        if (!Set.of("cold", "warm").contains(mode)) throw new IllegalArgumentException("--mode must be cold or warm");
        if (realHome && mode.equals("warm"))
            throw new IllegalArgumentException("Warm mode runs in an isolated home, where it can turn residency on");
        return new StartupBenchOptions(Path.of(app).toAbsolutePath(), Path.of(output).toAbsolutePath(), revision, mode,
            number(values, "runs", 10, 1, 50), number(values, "prime", 1, 0, 5), realHome,
            number(values, "timeout-seconds", 45, 5, 300));
    }

    private static int number(Map<String, String> values, String key, int fallback, int min, int max) {
        int result = Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback)));
        if (result < min || result > max) throw new IllegalArgumentException("--" + key + " must be " + min + ".." + max);
        return result;
    }
}
```

```java
// jasper-app/src/main/java/dev/jasper/app/benchmark/StartupRuns.java
package dev.jasper.app.benchmark;

import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.startup.StartupTraceFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Runs the start-up benchmark: each launch goes through LaunchServices ({@code open}), like a Dock click, with the trace
 * file and, by default, an isolated home passed in {@code JAVA_TOOL_OPTIONS}. Never part of the headless check.
 */
final class StartupRuns {
    /** Process control, injected so the planning code stays testable. */
    record Host(Function<List<String>, Integer> run, LongSupplier epochMillis, Predicate<Long> terminate,
                Function<Long, Long> residentKiB) {
        static Host system() {
            return new Host(command -> {
                try { return new ProcessBuilder(command).inheritIO().start().waitFor(); }
                catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return -1; }
            }, System::currentTimeMillis, pid -> ProcessHandle.of(pid).map(StartupRuns::stop).orElse(true), pid -> {
                try {
                    Process ps = new ProcessBuilder("ps", "-o", "rss=", "-p", Long.toString(pid)).start();
                    String text = new String(ps.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    ps.waitFor();
                    return parseRss(text);
                } catch (IOException failure) {
                    return -1L;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return -1L;
                }
            });
        }
    }

    /** {@code ps -o rss=} output in KiB, or -1 when there is none. */
    static long parseRss(String text) {
        try { return text.isBlank() ? -1 : Long.parseLong(text.strip()); }
        catch (NumberFormatException unexpected) { return -1; }
    }

    private final StartupBenchOptions options;
    private final Host host;

    StartupRuns(StartupBenchOptions options, Host host) {
        this.options = options;
        this.host = host;
    }

    static String launchOptions(Path traceFile, Path home) {
        return "-D" + StartupTrace.TRACE_FILE_PROPERTY + "=" + traceFile + (home == null ? "" : " -Duser.home=" + home);
    }

    static List<String> openCommand(Path app, String javaToolOptions, boolean background) {
        List<String> command = new ArrayList<>(List.of("open", "-n", "-a", app.toString(), "--env",
            "JAVA_TOOL_OPTIONS=" + javaToolOptions));
        if (background) command.addAll(List.of("--args", "--background"));
        return command;
    }

    static List<String> reopenCommand(Path app) { return List.of("open", "-a", app.toString()); }

    /**
     * Milliseconds from the click: the process start, every process milestone, or ({@code trigger} non-null) the
     * request and milestones of the latest window with that trigger.
     */
    static Map<String, Double> fromClick(Map<String, Object> trace, long clickMillis, String trigger) {
        double processStart = Instant.parse((String) trace.get("processStart")).toEpochMilli() - (double) clickMillis;
        Map<String, Double> result = new LinkedHashMap<>();
        if (trigger == null) {
            result.put("PROCESS_START", processStart);
            if (trace.get("milestones") instanceof Map<?, ?> milestones)
                milestones.forEach((name, at) -> result.put(name.toString(), processStart + ((Number) at).doubleValue()));
            return result;
        }
        Map<?, ?> window = null;
        if (trace.get("windows") instanceof List<?> windows)
            for (Object entry : windows) if (entry instanceof Map<?, ?> map && trigger.equals(map.get("trigger"))) window = map;
        if (window == null) return result;
        for (String name : List.of("requested", "SHOWN", "FIRST_PAINT", "OUTPUT_PAINTED"))
            if (window.get(name) instanceof Number at)
                result.put(name.equals("requested") ? "REQUESTED" : name, processStart + at.doubleValue());
        return result;
    }

    static Map<String, Map<String, Object>> summary(List<Map<String, Double>> runs) {
        Map<String, List<Double>> values = new TreeMap<>();
        for (Map<String, Double> run : runs) run.forEach((name, value) -> values.computeIfAbsent(name, key -> new ArrayList<>()).add(value));
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        values.entrySet().stream()
            .sorted(Comparator.comparingDouble(entry -> median(entry.getValue())))
            .forEach(entry -> {
                List<Double> sorted = entry.getValue().stream().sorted().toList();
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("count", sorted.size());
                stats.put("median", median(sorted));
                stats.put("min", sorted.getFirst());
                stats.put("max", sorted.getLast());
                result.put(entry.getKey(), stats);
            });
        return result;
    }

    int run() throws IOException {
        if (host.run().apply(List.of("pgrep", "-f", "/Contents/MacOS/Jasper")) == 0) {
            System.err.println("A Jasper process is running; quit every Jasper (including a resident one) first.");
            return 3;
        }
        // Short root: the handoff socket lives under the home, and macOS caps socket paths at 104 bytes.
        Path root = Files.createTempDirectory(Path.of("/tmp"), "jsb");
        Path home = options.realHome() ? null : Files.createDirectories(root.resolve("home"));
        if (home != null && options.mode().equals("warm")) {
            Path config = Files.createDirectories(home.resolve(".config/jasper")).resolve("config.toml");
            Files.writeString(config, "[system]\nrun_in_background = true\n", StandardCharsets.UTF_8);
        }
        if (options.realHome()) warnAboutTerminalMultiplexers();
        List<Map<String, Object>> runs = new ArrayList<>();
        List<Map<String, Double>> measured = new ArrayList<>();
        String status = "ok";
        for (int index = 0; index < options.prime() + options.runs(); index++) {
            Map<String, Object> result = options.mode().equals("warm") ? warm(root, home, index) : cold(root, home, index);
            if (index < options.prime()) continue;
            runs.add(result);
            if (result.get("fromClick") instanceof Map<?, ?> values && !values.isEmpty()) {
                Map<String, Double> copy = new LinkedHashMap<>();
                values.forEach((name, value) -> copy.put(name.toString(), ((Number) value).doubleValue()));
                measured.add(copy);
            } else {
                status = "incomplete";
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema", 1);
        report.put("benchmark", "startup");
        report.put("status", status);
        report.put("revision", options.revision());
        report.put("mode", options.mode());
        report.put("app", options.app().toString());
        report.put("realHome", options.realHome());
        report.put("runs", runs);
        report.put("summary", summary(measured));
        BenchmarkReport.writeJson(options.output(), report);
        writeCompanion(report);
        return status.equals("ok") ? 0 : 1;
    }

    private Map<String, Object> cold(Path root, Path home, int index) throws IOException {
        Path traceFile = root.resolve("trace-" + index + ".json");
        long click = host.epochMillis().getAsLong();
        host.run().apply(openCommand(options.app(), launchOptions(traceFile, home), false));
        Map<String, Object> trace = await(traceFile, document -> Boolean.TRUE.equals(document.get("settled")));
        return finish(index, trace, trace == null ? Map.of() : fromClick(trace, click, null));
    }

    private Map<String, Object> warm(Path root, Path home, int index) throws IOException {
        Path traceFile = root.resolve("trace-" + index + ".json");
        host.run().apply(openCommand(options.app(), launchOptions(traceFile, home), true));
        Map<String, Object> resident = await(traceFile, document -> Boolean.TRUE.equals(document.get("settled")));
        if (resident == null) return finish(index, null, Map.of());
        // Idle memory of the warm, windowless resident: what residency costs while it waits.
        try { Thread.sleep(2_000); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        long idle = resident.get("pid") instanceof Number pid ? host.residentKiB().apply(pid.longValue()) : -1;
        long click = host.epochMillis().getAsLong();
        host.run().apply(reopenCommand(options.app()));
        Map<String, Object> trace = await(traceFile, document -> fromClick(document, click, "reopen").containsKey("OUTPUT_PAINTED"));
        Map<String, Object> result = finish(index, trace == null ? resident : trace,
            trace == null ? Map.of() : fromClick(trace, click, "reopen"));
        result.put("idleResidentKiB", idle);
        return result;
    }

    private Map<String, Object> finish(int index, Map<String, Object> trace, Map<String, Double> fromClick) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run", index - options.prime() + 1);
        result.put("outcome", trace == null ? "timeout" : fromClick.isEmpty() ? "incomplete" : "ok");
        if (trace != null) {
            result.put("kind", trace.get("kind"));
            result.put("jvm", trace.get("jvm"));
            result.put("phases", trace.get("phases"));
            if (trace.get("pid") instanceof Number pid && !host.terminate().test(pid.longValue()))
                result.put("outcome", "did-not-exit");
        } else {
            host.run().apply(List.of("pkill", "-KILL", "-f", options.app() + "/Contents/MacOS/Jasper"));
        }
        result.put("fromClick", fromClick);
        return result;
    }

    private Map<String, Object> await(Path traceFile, Predicate<Map<String, Object>> ready) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(options.timeoutSeconds());
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(traceFile)) {
                try {
                    Map<String, Object> document = StartupTraceFile.read(traceFile);
                    if (ready.test(document)) return document;
                } catch (IOException | RuntimeException notYet) {
                    // A file mid-replacement or a document not ready yet: poll again.
                }
            }
            try { Thread.sleep(25); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return null; }
        }
        return null;
    }

    private static boolean stop(ProcessHandle process) {
        process.destroy();
        try {
            process.onExit().get(15, TimeUnit.SECONDS);
            return true;
        } catch (Exception notYet) {
            process.destroyForcibly();
            return false;
        }
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return (sorted.get((n - 1) / 2) + sorted.get(n / 2)) / 2;
    }

    private void warnAboutTerminalMultiplexers() {
        Path config = Path.of(System.getProperty("user.home"), ".config/jasper/config.toml");
        try {
            String text = Files.isRegularFile(config) ? Files.readString(config) : "";
            if (text.contains("tmux") || text.contains("screen"))
                System.err.println("Warning: the configured terminal program looks like tmux or screen; each launch leaves a "
                    + "session in its server. Remove those sessions after the run.");
        } catch (IOException unreadable) {
            System.err.println("Warning: could not read " + config + " to check for tmux or screen.");
        }
    }

    private void writeCompanion(Map<String, Object> report) throws IOException {
        StringBuilder out = new StringBuilder("# Jasper start-up benchmark\n\nStatus: " + report.get("status")
            + ". Mode: " + report.get("mode") + ". Revision: " + report.get("revision")
            + ". Milliseconds from the click (`open`), medians of the measured runs.\n\n| Milestone | Runs | Median | Min | Max |\n|---|---|---|---|---|\n");
        if (report.get("summary") instanceof Map<?, ?> summary)
            summary.forEach((name, stats) -> {
                Map<?, ?> values = (Map<?, ?>) stats;
                out.append("| ").append(name).append(" | ").append(values.get("count")).append(" | ")
                    .append(Math.round(((Number) values.get("median")).doubleValue())).append(" | ")
                    .append(Math.round(((Number) values.get("min")).doubleValue())).append(" | ")
                    .append(Math.round(((Number) values.get("max")).doubleValue())).append(" |\n");
            });
        Files.writeString(Path.of(options.output() + ".md"), out, StandardCharsets.UTF_8);
    }

}
```

```java
// jasper-app/src/main/java/dev/jasper/app/benchmark/StartupBench.java
package dev.jasper.app.benchmark;

/** Opt-in macOS start-up benchmark of a packaged image; run it through {@code tools/benchmarks/run-macos.sh}. */
public final class StartupBench {
    private StartupBench() { }

    public static void main(String[] args) throws Exception {
        StartupBenchOptions options;
        try { options = StartupBenchOptions.parse(args); }
        catch (IllegalArgumentException failure) { System.err.println(failure.getMessage()); System.exit(2); return; }
        System.exit(new StartupRuns(options, StartupRuns.Host.system()).run());
    }
}
```

In `benchmark/package-info.java`, insert `dev.jasper.app.startup, ` before `dev.jasper.app.workspace` in the allowed
list.

Replace the mode handling in `tools/benchmarks/run-macos.sh`:

```sh
if [ "$#" -lt 4 ]; then
    echo "Usage: $0 /absolute/Jasper.app throughput|memory|startup REVISION /absolute/result.json [benchmark options...]" >&2
    exit 2
fi
```

```sh
case "$mode" in throughput) entry=Bench;; memory) entry=MemoryBench;; startup) entry=StartupBench;; *) echo 'Expected throughput, memory or startup' >&2; exit 2;; esac
```

and, immediately before the final `exec`:

```sh
# The start-up benchmark launches the image itself through LaunchServices.
if [ "$mode" = startup ]; then set -- --app "$image" "$@"; fi
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.benchmark.*' verifyApplicationArchitecture`
Expected: PASS — `StartupBenchTest` (7 tests), both `BenchmarkScriptTest` tests, the existing `BenchmarkTest`.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/benchmark tools/benchmarks/run-macos.sh jasper-app/src/test/java/dev/jasper/app/benchmark
git commit -m "feat(benchmark): add the start-up benchmark for packaged images

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 7: Documentation, the recorded baseline and the full check

**Files:**
- Create: `docs/benchmarks/2026-10-02-startup-baseline.md`
- Modify: `docs/diagnostics.md` (a "Start-up timing" section), `docs/benchmarks.md` (a "Start-up benchmark" section),
  `docs/app-architecture.md` (the `startup` package in the package list), `docs/STATUS.md` (a Current state entry)

**Interfaces:** none.

- [ ] **Step 1: Write the baseline record**

```markdown
# Start-up baseline — 2026-10-02

Measured before any start-up work, on the development Mac (Apple M4 Pro, macOS 26, Palo Alto Cortex XDR running),
warm disk cache, launching through LaunchServices (`open -n -a`) like a Dock click. Throwaway harness: a Java agent
probe plus an external `CGWindowList` watcher and `tmux capture-pane` (the user's `terminal.program` is tmux). Medians
of 5–10 runs, milliseconds from the click. The agent probe distorts CDS and AOT, so those rows use the external probe.

| Build | Window on screen | Notes |
|---|---|---|
| Installed `/Applications/Jasper.app`, built 10:06 (before `c4211cf1`) | ~3,470 | prompt mark ~4,330; 10,050 classes loaded; 7.6 s CPU |
| `claude/startup-speed@91400d29` image | 2,320 | |
| + default CDS archive (`java -Xshare:dump` in the image runtime) | 2,189 | the packaged runtime ships no `classes.jsa` |
| + JDK 25 AOT cache (one training launch, `-XX:AOTCacheOutput`) | 1,683 | −640 ms with no code change |
| + CDS, bundled plugins off (`-Djasper.plugins.bundled=<empty>`) | ~1,625 | plugins cost ~500 ms of critical path |
| + CDS, `-XX:TieredStopAtLevel=1` | ~2,150 | CPU 5.5 s → 2.8 s, wall time unchanged |
| + CDS, `-XX:+UseSerialGC` | ~2,190 | no change |

Where the time went (JFR at 1 ms with full stacks, `-Xlog:class+load`, `-Xlog:library`): about 1.5 s of class loading
and verification that sampling cannot see; plugins started on the EDT before the first window; FlatLaf's, JNA's,
pty4j's and SQLite's native libraries extracted to new random file names every launch (FlatLaf's `dlopen` alone
~108 ms on the EDT); the built-in theme catalog's 284 headers (~80 ms); system font enumeration (~66 ms); seven
dock-icon PNGs decoded and unused (~23 ms); the shell spawned only after all of it.

From Plan 1 on, `tools/benchmarks/run-macos.sh <image> startup …` replaces the throwaway harness; record its JSON next
to this file for every later plan.
```

- [ ] **Step 2: Update the maintained documents**

Append to `docs/diagnostics.md`:

```markdown
## Start-up timing

Every launch logs one INFO line from `dev.jasper.app.startup` once start-up settles (the first shell output is painted
and every plugin has started, or 10 seconds after the process started), for example:

    Startup (cold, AOT cache used, CDS on): main entered 71 ms, config loaded 90 ms, … output painted 512 ms, plugins bound 980 ms | application 210 ms, plugins 280 ms

Times are milliseconds since the operating system started the process. `AOT cache stale` means the cache in the
package no longer matches its jars (for example after a copy that did not keep modification times) and costs more than
having none. Each window a running Jasper opens logs `Window (<trigger>): shown …, first paint …, output painted …
after the request`. With `-Djasper.startupTrace=<file>` the same data is also written as JSON; the start-up benchmark
uses it.
```

Append to `docs/benchmarks.md`:

```markdown
## Start-up benchmark

`tools/benchmarks/run-macos.sh /absolute/Jasper.app startup REVISION /absolute/result.json [--mode cold|warm] [--runs N]
[--prime N] [--real-home] [--timeout-seconds N]` launches a packaged image through LaunchServices, like a Dock click,
reads each launch's start-up trace and quits it. Cold mode measures fresh launches; warm mode starts a resident Jasper
with `--background`, records its idle memory (`ps` RSS, two seconds after it settles), then measures the window a
reopen (`open -a`) produces. By default each run uses an isolated home
under `/tmp` (`-Duser.home`), so the user's configuration, logs and login items are untouched; one priming launch is
discarded. `--real-home` measures the user's own setup and warns when the terminal program is tmux or screen, whose
servers keep a session per launch. The benchmark refuses to start while any Jasper is running. It opens windows: ask
the user before each run. The 2026-10-02 baseline is [recorded here](benchmarks/2026-10-02-startup-baseline.md).
```

In `docs/app-architecture.md`, add `dev.jasper.app.startup` to the package list it maintains, described as "Start-up
timing (`StartupTrace`, `WindowTrace`, `StartupTraceFile`); JDK-only, depended on by bootstrap, application, plugins
and benchmark".

Add a "Current state" entry at the top of `docs/STATUS.md`:

```markdown
- **Start-up speed Plan 1 (Measure)** (branch `claude/startup-speed`, worktree `.claude/worktrees/startup-speed`,
  [spec](superpowers/specs/2026-10-02-jasper-startup-speed-design.md),
  [plan](superpowers/plans/2026-10-02-startup-1-measure.md)): every launch logs one start-up line (milestones from the
  OS process start, phases, AOT/CDS use) and each window logs its own timing; `-Djasper.startupTrace` writes JSON;
  `TerminalView.afterNextPaint` is the new supported view hook; `tools/benchmarks/run-macos.sh … startup` measures cold
  and warm launches of a packaged image. [Baseline](benchmarks/2026-10-02-startup-baseline.md): window on screen 2.3 s
  at HEAD, 1.7 s with an AOT cache. Next: Plan 2 (JVM and packaging). Not done: a recorded `startupBench` run (user-run).
```

- [ ] **Step 3: Run the whole check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL; 7,982 tests (the baseline 7,954 plus this plan's 28: 14 startup, 3 view, 1 first-terminal,
1 session output, 1 trigger, 7 start-up bench, 1 script), 0 failures, 6 skips. Count from
`*/build/test-results/test/*.xml`.

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs(startup): record the start-up baseline and document the trace and benchmark

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

- [ ] **Step 5: User-run check (ask the user first; it opens windows)**

With the user's approval, build an image (`./gradlew :jasper-app:packageApp`), copy it out of the build folder with
`ditto`, and run `tools/benchmarks/run-macos.sh <copy> startup plan1 /tmp/startup-plan1-cold.json --runs 5` and the
same with `--mode warm`. Confirm the log line appears in the isolated home's `logs/jasper-0-0.log`, the summary's
`OUTPUT_PAINTED` median is within 10% of the baseline's window time, and record both JSON files under
`docs/benchmarks/`.
```
