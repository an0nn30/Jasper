# Custom VM Options, Plan 3: Memory — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: **Complete on 2026-10-14.** Written 2026-10-13 on `claude/custom-vm-options-memory` (from `main` at `7c0a0938`, which holds
plans 1 and 2).

Deviations beyond the eight planned ones, from the reviews: `HeapThreshold` watches only an old generation (a pool that
supports both usage and collection-usage thresholds), because a large young generation could otherwise be chosen;
`MemoryIndicator.collectNow()` refreshes only while the indicator is shown, and `close()` also closes the `app.memory.gc`
action and makes later calls do nothing.

**Goal:** Jasper can show its heap use in the status bar (`312 of 2048M`, click to collect garbage), warns when the heap
stays nearly full or runs out, and the warning's **Adjust Memory Settings…** opens the heap limit with 512 MiB more
filled in.

**Architecture:** The JDK-only package `dev.jasper.app.vmoptions` gains `HeapReading` (one reading of the heap and its
text), `LowMemoryWatch` (spec 5.4's throttling rules on an injected clock) and `HeapThreshold` (the old generation's
collection-usage threshold and its JMX notifications). In `dev.jasper.app.application`, `MemoryIndicator` owns an
app-contributed status entry, its click action and one shared timer, and `MemoryWarnings` turns threshold signals and
`OutOfMemoryError`s into the warning. A Settings fragment `vm.heap=<MiB>` stages a value in the heap row. A new boolean
setting `window.memory_indicator` turns the indicator on.

**Tech Stack:** Java 25 (`java.lang.management`, `javax.management`), Swing timers, the contributions model, JUnit 5 +
AssertJ, Gradle.

**Spec:** [`docs/superpowers/specs/2026-10-13-jasper-custom-vm-options-design.md`](../specs/2026-10-13-jasper-custom-vm-options-design.md)
(sections 5.3, 5.4, 5.5, 6.4 `LowMemoryWatch`, 6.5, 8, 9 and 11, plan 3). Earlier plans:
[`2026-10-13-jasper-custom-vm-options-1-launcher.md`](2026-10-13-jasper-custom-vm-options-1-launcher.md),
[`2026-10-13-jasper-custom-vm-options-2-settings.md`](2026-10-13-jasper-custom-vm-options-2-settings.md).

## Global Constraints

- Setting: `window.memory_indicator`, boolean, default `false`, label "Show memory indicator", on the Window page under
  "Toolbar & Status Bar"; live.
- Indicator: each window's status bar shows `<used> of <max>M` (heap used and maximum, MiB) as an app-contributed status
  entry; the tooltip breaks down heap used, committed and maximum, and non-heap use; one shared timer refreshes the
  text every 3 seconds while at least one indicator is visible; updates are instant (no animation); a click runs
  `System.gc()` and refreshes. IntelliJ's double-click "release caches" is not copied.
- Low-memory warning: a collection-usage threshold at 95% of the maximum of the heap pool that supports one (the old
  generation); each threshold notification is a signal; signals are throttled to one per 20 seconds; more than five
  signals within 15 minutes raise "Jasper is running low on memory" with **Adjust Memory Settings…**, which opens the
  heap field pre-filled with the current maximum plus 512 MiB; at most once per session, unless the user changes the
  heap; an `OutOfMemoryError` reaching `UnexpectedExceptions` raises the same notification at once.
- Notification group: the existing `jasper.vmoptions` ("Memory & VM options", spec 5.5).
- `dev.jasper.app.vmoptions` stays JDK-only (`java.lang.management`, `javax.management` and `com.sun.management` are
  JDK); `verifyApplicationArchitecture` enforces it.
- Start-up path: nothing new runs before the first output paints except reading the four system properties. The
  threshold is installed on the existing settled triggers (`OUTPUT_PAINTED`, `WARMED_UP`, 5 s after `FIRST_PAINT`);
  the indicator reads the heap only while shown.
- `ThemeMigrationCoverageTest` needs a row in `docs/design/theme-engine/migration.csv` for every Java file that matches
  its UI pattern (a Swing/AWT/kit import, `new ArrayList`, `new J…`, and so on). Each task runs it; for each file it
  reports missing, add a row in path order:
  `<path>,app.<package>,<ClassName>,nonvisual-helper,none,none,<its test path>,"New in custom VM options plan 3; <one phrase>, no styling"`.
- Never launch the Jasper GUI; never run `trainAotCache`, `packageDist` or `-Pjasper.aot=true` (AGENTS.md).
- Work in the worktree `.claude/worktrees/custom-vm-options` on `claude/custom-vm-options-memory`, never on `main`.
  Every commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in source files; write "…"
  in Java strings as `…` unless the surrounding line already uses the literal character.
- `-Xlint:all` is on for every Java compile; keep new code warning-free. `check` runs javadoc with doclint.

## Planned deviations from the spec

1. **The indicator's click is an action**, `app.memory.gc` ("Run Garbage Collection"): a contributed status entry
   clicks through an action id, and the contributions model has no hidden actions, so it is also in the command
   palette and Keymap.
2. **"While at least one indicator is visible"** means: the setting is on, `window.status_bar` is on, and at least one
   window is open.
3. **The setting is read from `ConfigService.State`**, as `notifications.groups` is, instead of a new `ConfigSnapshot`
   component; no window reads it.
4. **An `OutOfMemoryError` is still recorded as an internal-error incident**, so it shows that card too; the low-memory
   card says what to do about it.
5. **"At most once per session" covers the `OutOfMemoryError` path too**; saving a new heap limit re-arms both.
6. **Pre-filling stages, it doesn't save.** The Settings fragment `vm.heap=<MiB>` stages the value as the heap row's
   edit (Apply saves it). A disabled heap row (a development run, a corrupted file) only scrolls to the section.
7. **The 15-minute window slides**, and a signal within 20 s of the last counted one is dropped, not queued.
8. **`bootstrap` is unchanged.** `ApplicationBootstrap.UnexpectedExceptions` already forwards every uncaught failure to
   `JasperApplication.unexpected`, which now recognises an `OutOfMemoryError` (spec 6.5 expected a `bootstrap` change).

## Review Focus

Inputs the spec implies but no other test pins, most likely to bite first; each has its test in the owning task:

1. The setting turned off, or the last window closed (Jasper resident in the background), while the timer runs: the
   timer stops at once and the entry hides — Task 5.
2. An `OutOfMemoryError` wrapped in another exception (an `ExecutionException`, a `RuntimeException`): the warning still
   shows — Task 6.
3. A heap with no defined maximum (`MemoryUsage.getMax() == -1`): the label falls back to the committed heap, and pools
   without a maximum are never given a threshold — Tasks 1 and 2.
4. Adjust Memory Settings… in a development run (heap row disabled) or with a malformed value: Settings opens at the
   section without staging anything — Task 3.
5. A garbage-collection storm (a signal every second for minutes): exactly one warning — Task 2 (throttle) and Task 6.

## File structure

| Path | Responsibility |
|---|---|
| `jasper-app/src/main/java/dev/jasper/app/vmoptions/HeapReading.java` | One reading of the heap; the indicator's label and tooltip. |
| `…/vmoptions/LowMemoryWatch.java` | Spec 5.4's rules as a state machine on an injected clock. |
| `…/vmoptions/HeapThreshold.java` | The old generation's collection-usage threshold and its JMX listener. |
| `…/vmoptions/package-info.java` | Package contract, now naming the memory pieces. |
| `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java` | A `vm.heap=<MiB>` fragment stages the value. |
| `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`, `…/settingsui/CorePages.java` | The setting and its Window page row. |
| `jasper-app/src/main/java/dev/jasper/app/application/MemoryIndicator.java` | Status entry, click action, shared timer. |
| `…/application/MemoryWarnings.java` | Threshold signals and `OutOfMemoryError` → the warning; Adjust Memory Settings…. |
| `…/application/VmOptionsNotices.java`, `…/application/VmOptionsFeature.java` | The warning's request; a heap-saved hook. |
| `…/application/JasperApplication.java`, `…/shortcuthelp/ShortcutCatalog.java` | Wiring. |
| `jasper-app/src/test/java/dev/jasper/app/testsupport/MutableClock.java` | A test clock that moves only when told. |
| Tests under `jasper-app/src/test/java/dev/jasper/app/{vmoptions,settingsui,config,application}/` | One class per unit. |
| Docs | `config.example.toml`, `docs/configuration.md`, `docs/notifications.md`, `docs/app-architecture.md`, `docs/STATUS.md`, `docs/design/theme-engine/migration.csv`, spec status, this plan's status. |

---

### Task 1: `HeapReading` — one reading of the heap

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/vmoptions/HeapReading.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/vmoptions/package-info.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/vmoptions/HeapReadingTest.java`

**Interfaces:**
- Produces: `public record HeapReading(long heapUsed, long heapCommitted, long heapMax, long nonHeapUsed)` (bytes) with
  `static HeapReading current()`, `String label()`, `String tooltip()`, package-private `long limit()`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/vmoptions/HeapReadingTest.java`:

```java
package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HeapReadingTest {
    @Test void theLabelAndTooltipAreInMiB() {
        var reading = new HeapReading(312L << 20, 512L << 20, 2048L << 20, 98L << 20);
        assertThat(reading.label()).isEqualTo("312 of 2048M");
        assertThat(reading.tooltip()).isEqualTo(
            "Heap: 312 MiB used, 512 MiB committed, 2048 MiB maximum. Non-heap: 98 MiB used. Click to run garbage collection.");
    }

    @Test void partialMebibytesRoundDown() {
        assertThat(new HeapReading((312L << 20) + (700L << 10), 512L << 20, 2048L << 20, 0).label()).isEqualTo("312 of 2048M");
    }

    @Test void withoutAMaximumTheCommittedHeapIsTheLimit() {
        var reading = new HeapReading(10L << 20, 64L << 20, -1, 0);
        assertThat(reading.limit()).isEqualTo(64L << 20);
        assertThat(reading.label()).isEqualTo("10 of 64M");
        assertThat(reading.tooltip()).contains("64 MiB maximum");
    }

    @Test void thisJvmCanBeRead() {
        var reading = HeapReading.current();
        assertThat(reading.heapUsed()).isPositive();
        assertThat(reading.heapCommitted()).isGreaterThanOrEqualTo(reading.heapUsed());
        assertThat(reading.label()).matches("\\d+ of \\d+M");
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.HeapReadingTest'`
Expected: FAIL — compilation error, `HeapReading` does not exist.

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/vmoptions/HeapReading.java`:

```java
package dev.jasper.app.vmoptions;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * One reading of this JVM's memory, in bytes, and the status bar indicator's text for it (spec section 5.3).
 *
 * @param heapUsed heap in use
 * @param heapCommitted heap the JVM has committed
 * @param heapMax the heap limit, or {@code -1} when the JVM names none
 * @param nonHeapUsed non-heap memory in use (metaspace, code cache)
 */
public record HeapReading(long heapUsed, long heapCommitted, long heapMax, long nonHeapUsed) {
    /** This JVM's memory now. */
    public static HeapReading current() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        return new HeapReading(heap.getUsed(), heap.getCommitted(), heap.getMax(), memory.getNonHeapMemoryUsage().getUsed());
    }

    /** The indicator's text: {@code <used> of <max>M}, in MiB. */
    public String label() { return mib(heapUsed) + " of " + mib(limit()) + "M"; }

    /** The indicator's tooltip: heap used, committed and maximum, and non-heap use. */
    public String tooltip() {
        return "Heap: " + mib(heapUsed) + " MiB used, " + mib(heapCommitted) + " MiB committed, " + mib(limit())
            + " MiB maximum. Non-heap: " + mib(nonHeapUsed) + " MiB used. Click to run garbage collection.";
    }

    /** The heap limit in bytes: the maximum, else what is committed when the JVM names no maximum. */
    long limit() { return heapMax >= 0 ? heapMax : heapCommitted; }

    private static long mib(long bytes) { return Math.max(0, bytes) >> 20; }
}
```

Replace the whole comment in `jasper-app/src/main/java/dev/jasper/app/vmoptions/package-info.java` (it gains "the
memory readings and low-memory watch of spec sections 5.3 and 5.4"):

```java
/**
 * Custom VM options (spec section 6.4): the launcher's reading of {@code jasper.vmoptions}, mirrored for the
 * application; editing the file; what this JVM was launched with; the bundled launcher configuration; the check
 * that runs the bundled {@code java} with the options; and the memory readings and low-memory watch of spec sections 5.3
 * and 5.4. JDK-only: no Swing or AWT ({@code com.sun.management} for the
 * physical memory); {@code verifyApplicationArchitecture} enforces it. No owner: values and pure functions, plus file
 * I/O and a check that run on the caller's thread.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.vmoptions;
```

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.HeapReadingTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL. If `ThemeMigrationCoverageTest` reports a missing path, add its row (Global Constraints) and
run again.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/vmoptions jasper-app/src/test/java/dev/jasper/app/vmoptions docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Memory: one reading of the heap and the indicator's text

HeapReading reads heap used, committed and maximum and non-heap use, and
formats the status bar's "312 of 2048M" and its tooltip; a heap without a
maximum uses the committed size.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: `LowMemoryWatch` and `HeapThreshold`

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/testsupport/MutableClock.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/vmoptions/LowMemoryWatch.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/vmoptions/HeapThreshold.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/vmoptions/LowMemoryWatchTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/vmoptions/HeapThresholdTest.java`

**Interfaces:**
- Produces: test fixture `dev.jasper.app.testsupport.MutableClock` (`advance(Duration)`); `public final class
  LowMemoryWatch` with `FRACTION = 0.95`, package-private `THROTTLE`, `WINDOW`, `SIGNALS`, constructor
  `LowMemoryWatch(Clock)`, `synchronized boolean signal()`, `synchronized boolean outOfMemory()`,
  `synchronized void heapChanged()`; `public final class HeapThreshold implements AutoCloseable` with
  `static Optional<HeapThreshold> install(double fraction, Runnable signal)`,
  package-private `static Optional<MemoryPoolMXBean> pool(List<MemoryPoolMXBean>)`, `String poolName()`,
  `long threshold()`, `void close()` (no checked exception).

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/testsupport/MutableClock.java`:

```java
package dev.jasper.app.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A UTC clock that moves only when a test advances it. Not thread-safe. */
public final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-13T12:00:00Z");

    /** Moves the clock forward. */
    public void advance(Duration by) { now = now.plus(by); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/vmoptions/LowMemoryWatchTest.java`:

```java
package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LowMemoryWatchTest {
    private final MutableClock clock = new MutableClock();
    private final LowMemoryWatch watch = new LowMemoryWatch(clock);

    /** Signals {@code count} times, {@code apart} apart; returns which ones raised the warning. */
    private List<Integer> signals(int count, Duration apart) {
        var raised = new ArrayList<Integer>();
        for (int i = 0; i < count; i++) {
            if (i > 0) clock.advance(apart);
            if (watch.signal()) raised.add(i);
        }
        return raised;
    }

    @Test void theSixthSignalWithinFifteenMinutesWarnsOnce() {
        assertThat(signals(10, Duration.ofSeconds(20))).containsExactly(5);
    }

    @Test void signalsCloserThanTwentySecondsAreDropped() {
        // At 0, 10, 20 … 100 s only every other signal counts, so the sixth counted one is the eleventh.
        assertThat(signals(11, Duration.ofSeconds(10))).containsExactly(10);
        var storm = new LowMemoryWatch(clock);
        int raised = 0;
        for (int i = 0; i < 600; i++) {
            clock.advance(Duration.ofSeconds(1));
            if (storm.signal()) raised++;
        }
        assertThat(raised).as("a garbage-collection storm warns exactly once").isEqualTo(1);
    }

    @Test void signalsSpreadOverMoreThanFifteenMinutesNeverWarn() {
        assertThat(signals(30, Duration.ofMinutes(4))).isEmpty();
    }

    @Test void anOutOfMemoryErrorWarnsAtOnceButOnlyOnce() {
        assertThat(watch.outOfMemory()).isTrue();
        assertThat(watch.outOfMemory()).isFalse();
        assertThat(signals(10, Duration.ofSeconds(20))).as("already warned this session").isEmpty();
    }

    @Test void changingTheHeapRearmsTheWarning() {
        assertThat(signals(6, Duration.ofSeconds(20))).containsExactly(5);
        assertThat(watch.outOfMemory()).isFalse();
        watch.heapChanged();
        clock.advance(Duration.ofSeconds(20));
        assertThat(signals(6, Duration.ofSeconds(20))).as("the count starts again").containsExactly(5);
        watch.heapChanged();
        assertThat(watch.outOfMemory()).isTrue();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/vmoptions/HeapThresholdTest.java`:

```java
package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HeapThresholdTest {
    private static MemoryPoolMXBean named(String name) {
        return ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool -> pool.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test void theLargestHeapPoolWithAThresholdIsChosen() {
        assertThat(HeapThreshold.pool(ManagementFactory.getMemoryPoolMXBeans())).hasValueSatisfying(pool -> {
            assertThat(pool.getType()).isEqualTo(MemoryType.HEAP);
            assertThat(pool.isCollectionUsageThresholdSupported()).isTrue();
            assertThat(pool.getUsage().getMax()).isPositive();
        });
        assertThat(HeapThreshold.pool(List.of())).isEmpty();
    }

    @Test void theThresholdIsTheFractionOfTheMaximumAndCloseReleasesIt() {
        var threshold = HeapThreshold.install(LowMemoryWatch.FRACTION, () -> { }).orElseThrow();
        MemoryPoolMXBean pool = named(threshold.poolName());
        try {
            long expected = (long) (pool.getUsage().getMax() * LowMemoryWatch.FRACTION);
            assertThat(threshold.threshold()).isEqualTo(expected);
            assertThat(pool.getCollectionUsageThreshold()).isEqualTo(expected);
        } finally {
            threshold.close();
        }
        assertThat(pool.getCollectionUsageThreshold()).isZero();
        threshold.close();
    }

    @Test void aCollectionAboveTheThresholdSignals() throws InterruptedException {
        var signalled = new CountDownLatch(1);
        // A fraction of 0 sets the smallest threshold, one byte, which any collection of the old generation exceeds.
        var threshold = HeapThreshold.install(0, signalled::countDown).orElseThrow();
        try {
            System.gc();
            assertThat(signalled.await(10, TimeUnit.SECONDS)).as("a threshold notification after a full collection").isTrue();
        } finally {
            threshold.close();
        }
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.LowMemoryWatchTest' --tests 'dev.jasper.app.vmoptions.HeapThresholdTest'`
Expected: FAIL — compilation errors, `LowMemoryWatch` and `HeapThreshold` do not exist.

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/vmoptions/LowMemoryWatch.java`:

```java
package dev.jasper.app.vmoptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Objects;

/**
 * When to warn that Jasper is running low on memory (spec section 5.4): heap threshold signals are throttled to one per
 * {@link #THROTTLE}; more than {@link #SIGNALS} within a sliding {@link #WINDOW} warn; an {@code OutOfMemoryError}
 * warns at once; Jasper warns at most once until the user changes the heap. Thread-safe: signals arrive on the JMX
 * notification thread.
 */
public final class LowMemoryWatch {
    /** The share of the old generation's maximum at which its collection usage signals. */
    public static final double FRACTION = 0.95;
    static final Duration THROTTLE = Duration.ofSeconds(20);
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int SIGNALS = 5;

    private final Clock clock;
    private final ArrayDeque<Instant> counted = new ArrayDeque<>();
    private boolean warned;

    /** A watch reading the time from {@code clock}. */
    public LowMemoryWatch(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    /** A heap threshold signal; true when it is the one that should raise the warning. */
    public synchronized boolean signal() {
        Instant now = clock.instant();
        if (!counted.isEmpty() && Duration.between(counted.getLast(), now).compareTo(THROTTLE) < 0) return false;
        counted.addLast(now);
        while (Duration.between(counted.getFirst(), now).compareTo(WINDOW) > 0) counted.removeFirst();
        if (warned || counted.size() <= SIGNALS) return false;
        warned = true;
        return true;
    }

    /** An {@code OutOfMemoryError}; true unless Jasper already warned. */
    public synchronized boolean outOfMemory() {
        if (warned) return false;
        warned = true;
        return true;
    }

    /** The user saved a new heap limit: the warning may show again, and the count starts over. */
    public synchronized void heapChanged() {
        warned = false;
        counted.clear();
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/vmoptions/HeapThreshold.java`:

```java
package dev.jasper.app.vmoptions;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

/**
 * A collection-usage threshold on the heap pool that supports one (the old generation, spec section 5.4), with a
 * listener that calls {@code signal} on each threshold notification (on the JMX notification thread).
 */
public final class HeapThreshold implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(HeapThreshold.class.getName());

    private final MemoryPoolMXBean pool;
    private final long threshold;
    private final NotificationEmitter emitter;
    private final NotificationListener listener;
    private boolean closed;

    private HeapThreshold(MemoryPoolMXBean pool, long threshold, NotificationEmitter emitter, NotificationListener listener) {
        this.pool = pool;
        this.threshold = threshold;
        this.emitter = emitter;
        this.listener = listener;
    }

    /**
     * Sets the threshold to {@code fraction} of the pool's maximum (at least one byte) and listens; empty when no heap
     * pool supports a collection-usage threshold or names a maximum, or the JVM refuses.
     */
    public static Optional<HeapThreshold> install(double fraction, Runnable signal) {
        Objects.requireNonNull(signal, "signal");
        Optional<MemoryPoolMXBean> chosen = pool(ManagementFactory.getMemoryPoolMXBeans());
        if (chosen.isEmpty()) return Optional.empty();
        MemoryPoolMXBean pool = chosen.get();
        String name = pool.getName();
        try {
            long threshold = Math.max(1, (long) (pool.getUsage().getMax() * fraction));
            var emitter = (NotificationEmitter) ManagementFactory.getMemoryMXBean();
            NotificationListener listener = (notification, handback) -> {
                if (!MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals(notification.getType())) return;
                if (name.equals(MemoryNotificationInfo.from((CompositeData) notification.getUserData()).getPoolName())) signal.run();
            };
            emitter.addNotificationListener(listener, null, null);
            pool.setCollectionUsageThreshold(threshold);
            return Optional.of(new HeapThreshold(pool, threshold, emitter, listener));
        } catch (RuntimeException refused) {
            LOG.log(System.Logger.Level.WARNING, "Could not watch the heap pool " + name, refused);
            return Optional.empty();
        }
    }

    /** Among {@code pools}, the heap pool with a collection-usage threshold and the largest maximum. */
    static Optional<MemoryPoolMXBean> pool(List<MemoryPoolMXBean> pools) {
        return pools.stream()
            .filter(pool -> pool.getType() == MemoryType.HEAP && pool.isCollectionUsageThresholdSupported())
            .filter(pool -> pool.getUsage() != null && pool.getUsage().getMax() > 0)
            .max(Comparator.comparingLong(pool -> pool.getUsage().getMax()));
    }

    /** The watched pool's name. */
    public String poolName() { return pool.getName(); }

    /** The threshold in bytes. */
    public long threshold() { return threshold; }

    /** Stops listening and clears the threshold; idempotent. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        try {
            emitter.removeNotificationListener(listener);
        } catch (ListenerNotFoundException gone) {
            // Already removed.
        }
        pool.setCollectionUsageThreshold(0);
    }
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL (add any migration rows the coverage test asks for).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/vmoptions jasper-app/src/test/java/dev/jasper/app/vmoptions jasper-app/src/test/java/dev/jasper/app/testsupport/MutableClock.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Memory: the low-memory rules and the old generation's threshold

LowMemoryWatch applies spec 5.4: signals throttled to one per 20 s, more
than five in a sliding 15 minutes warn, an OutOfMemoryError warns at once,
once until the heap changes. HeapThreshold sets a collection-usage
threshold at 95% of the old generation's maximum and listens for it.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: A Settings fragment pre-fills the heap

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java` (`reveal`, `selected`, a new field)
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageExternalTest.java`

**Interfaces:**
- Produces: `SchemaPage.reveal("<external id>=<integer>")` stages the integer as that external row's edit when the
  dialog shows the page (once; not when the row is disabled; a malformed value only reveals). Task 6 opens
  `system#vm.heap=<MiB>`.

- [ ] **Step 1: Write the failing tests**

In `jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageExternalTest.java`, add these tests (the class's
existing `page()` helper, `saves`, `enabled` and `kit` are used as they are):

```java
    @Test void aRevealWithAValueStagesItAsTheRowsEdit() {
        var page = page();
        page.component();
        page.reveal("vm.heap=4096");
        page.selected();
        assertThat(page.externals().getFirst().pending()).isEqualTo(4096);
        assertThat(kit(page, "vm.heap.input", JTextField.class).getText()).isEqualTo("4096");
        assertThat(page.modified()).isTrue();
        assertThat(saves).as("staged, not saved").isEmpty();
        assertThat(page.revealed()).as("the row still waits to be focused").isEqualTo("vm.heap");
        kit(page, "vm.heap.input", JTextField.class).setText("3072");
        page.selected();
        assertThat(page.externals().getFirst().pending()).as("staged once").isEqualTo(3072);
    }

    @Test void aRevealedValueIsCheckedLikeTypedOne() {
        var page = page();
        page.component();
        page.reveal("vm.heap=256");
        page.selected();
        assertThat(page.externals().getFirst().error()).isEqualTo("Use 512 MiB or more.");
        assertThat(page.valid()).isFalse();
    }

    @Test void aDisabledRowOrAMalformedValueStagesNothing() {
        enabled = false;
        var disabled = page();
        disabled.component();
        disabled.reveal("vm.heap=4096");
        disabled.selected();
        assertThat(disabled.externals().getFirst().pending()).isNull();
        assertThat(disabled.modified()).isFalse();
        enabled = true;
        var garbled = page();
        garbled.component();
        garbled.reveal("vm.heap=lots");
        garbled.selected();
        assertThat(garbled.externals().getFirst().pending()).isNull();
        assertThat(garbled.revealed()).isEqualTo("vm.heap");
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.SchemaPageExternalTest'`
Expected: FAIL — `aRevealWithAValueStagesItAsTheRowsEdit` (pending is null; `revealed()` is `"vm.heap=4096"`) and
`aRevealedValueIsCheckedLikeTypedOne` (no error).

- [ ] **Step 3: Implement**

In `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java`:

1. After the field `private String revealed;` add:

```java
    private Integer proposed;
```

2. Replace:

```java
    /** Remembers the row to focus once the dialog shows this page; {@code fragment} is an external row's id. */
    @Override public void reveal(String fragment) { revealed = fragment; }
```

with:

```java
    /**
     * Remembers the row to focus once the dialog shows this page. {@code fragment} is an external row's id, optionally
     * followed by {@code =<integer>}, a value to stage as that row's edit (Adjust Memory Settings… pre-fills the heap).
     */
    @Override public void reveal(String fragment) {
        int equals = fragment.indexOf('=');
        revealed = equals < 0 ? fragment : fragment.substring(0, equals);
        proposed = null;
        if (equals < 0) return;
        try {
            proposed = Integer.valueOf(fragment.substring(equals + 1).strip());
        } catch (NumberFormatException malformed) {
            // A value that is no integer only reveals the row.
        }
    }
```

3. In `selected()`, right after its first line
`if (disposed || revealed == null || view == null || view.isClosed()) return;` add:

```java
        stageProposed();
```

4. After `selected()` add:

```java
    /** Stages the value {@link #reveal} named as the revealed external row's edit, once, unless the row is disabled. */
    private void stageProposed() {
        Integer value = proposed;
        proposed = null;
        if (value == null) return;
        for (var row : externals) {
            if (row.id().equals(revealed) && row.value.enabled().getAsBoolean()) {
                row.editor.show(value);
                editedExternal(row);
                render();
                return;
            }
        }
    }
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageExternalTest.java
git commit -F - <<'EOF'
Settings: a page fragment can pre-fill an external row

A fragment such as vm.heap=2560 stages the value as the heap row's edit
when the page shows (once, checked like a typed value, not on a disabled
row); Apply saves it.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: The `window.memory_indicator` setting

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java` (constant, `SCHEMA`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java` (Window page)
- Modify: `config.example.toml`, `docs/configuration.md` (settings table)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java`

**Interfaces:**
- Produces: `CoreSettings.WINDOW_MEMORY_INDICATOR` (`Setting<Boolean>`, `window.memory_indicator`, default false);
  Task 7 reads it with `state.values().get(CoreSettings.WINDOW_MEMORY_INDICATOR)`.

- [ ] **Step 1: Write the failing tests**

In `jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java`, add:

```java
    @Test void theWindowPageOffersTheMemoryIndicatorWhereTheStatusBarShows() {
        var window = CorePages.specs(APPEARANCE, system).stream().filter(spec -> spec.id().equals("window")).findFirst().orElseThrow();
        assertThat(window.settings()).containsSubsequence(CoreSettings.WINDOW_STATUS_BAR, CoreSettings.WINDOW_STATUS_BAR_HEIGHT,
            CoreSettings.WINDOW_MEMORY_INDICATOR, CoreSettings.WINDOW_TAB_HEIGHT);
        var noStatusBar = PageKit.context(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "[window]\nstatus_bar = false\n", true).values()),
            id -> { }, () -> { });
        var page = window.build(noStatusBar);
        assertThat(PageKit.node(page, "window.memory_indicator").state().enabled()).as("only where the status bar shows").isFalse();
        assertThat(PageKit.node(window.build(context), "window.memory_indicator").state().enabled()).isTrue();
    }
```

In `jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java`, in
`everySettingIsDescribedAndItsTemplateValueRoundTrips`, raise the `hasSize(…)` count by one (it is 43 at `7c0a0938`;
use the current value plus one), and add a test:

```java
    @Test void theMemoryIndicatorIsOffByDefault() {
        assertThat(CoreSettings.WINDOW_MEMORY_INDICATOR.name()).isEqualTo("window.memory_indicator");
        assertThat(CoreSettings.WINDOW_MEMORY_INDICATOR.defaultValue()).isFalse();
        assertThat(CoreSettings.WINDOW_MEMORY_INDICATOR.label()).isEqualTo("Show memory indicator");
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.CoreSettingsTest' --tests 'dev.jasper.app.settingsui.CorePagesTest'`
Expected: FAIL — compilation errors, `WINDOW_MEMORY_INDICATOR` does not exist.

- [ ] **Step 3: Implement**

In `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`, after the `WINDOW_STATUS_BAR_HEIGHT` declaration
(the one ending `.description("Status bar height in logical pixels, 20-48; never shorter than its text. Updates live.").build();`) add:

```java
    public static final Setting<Boolean> WINDOW_MEMORY_INDICATOR = Setting.of("window.memory_indicator", SettingKind.bool(), false)
        .label("Show memory indicator").keywords("memory", "heap", "indicator", "status bar", "garbage collection")
        .description("Show the heap in use and its limit in the status bar; click it to run garbage collection. Updates live.").build();
```

and in `SCHEMA` replace `WINDOW_STATUS_BAR, WINDOW_STATUS_BAR_HEIGHT,` with
`WINDOW_STATUS_BAR, WINDOW_STATUS_BAR_HEIGHT, WINDOW_MEMORY_INDICATOR,`.

In `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java`, in the Window page, after
`.row(WINDOW_STATUS_BAR_HEIGHT, Editors.dependentHeight()).enabledWhen(draft -> draft.value(WINDOW_STATUS_BAR))` add:

```java
                .row(WINDOW_MEMORY_INDICATOR).enabledWhen(draft -> draft.value(WINDOW_STATUS_BAR))
```

In `config.example.toml`, after the line `status_bar_height = 24` add:

```toml
# Live across open windows.
memory_indicator = false
```

In `docs/configuration.md`, after the table row starting ``| `window.status_bar_height` |`` add:

```markdown
| `window.memory_indicator` | `false` | Boolean | Live |
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.settingsui.*'`
Expected: BUILD SUCCESSFUL (`ConfigTemplateTest` checks `config.example.toml` and the guide's table).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java config.example.toml docs/configuration.md jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java
git commit -F - <<'EOF'
Settings: window.memory_indicator

A live boolean, off by default, shown as "Show memory indicator" under
Toolbar & Status Bar and enabled only while the status bar shows.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: `MemoryIndicator` — the status entry, its click and the shared timer

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/MemoryIndicator.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/MemoryIndicatorTest.java`

**Interfaces:**
- Consumes: Task 1's `HeapReading` (`label()`, `tooltip()`); `Contributions.addAction(String id, String title, Icon,
  List<String> keywords, Optional<String> defaultBinding, Consumer<Invocation> handler)`,
  `Contributions.addStatus(String id, boolean left, int priority)`, `StatusEntry` setters.
- Produces: `final class MemoryIndicator implements AutoCloseable` with `STATUS_ID = "app.memory.indicator"`,
  `COLLECT = "app.memory.gc"`, `INTERVAL = Duration.ofSeconds(3)`, constructor
  `MemoryIndicator(Contributions, Supplier<HeapReading> readings, Runnable collect, BiFunction<Duration, Runnable, Runnable> every)`,
  `void show(boolean)`, `void windows(int)`, `void collectNow()`, `close()`, and
  `static Runnable swingTimer(Duration, Runnable)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/application/MemoryIndicatorTest.java`:

```java
package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.StatusEntry;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.vmoptions.HeapReading;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class MemoryIndicatorTest {
    private final Contributions contributions = new Contributions();
    private final ArrayDeque<HeapReading> readings = new ArrayDeque<>(List.of(
        new HeapReading(300L << 20, 512L << 20, 2048L << 20, 90L << 20),
        new HeapReading(400L << 20, 512L << 20, 2048L << 20, 91L << 20),
        new HeapReading(120L << 20, 512L << 20, 2048L << 20, 91L << 20)));
    private final List<Runnable> timers = new ArrayList<>();
    private final List<Duration> intervals = new ArrayList<>();
    private int stops;
    private int collections;

    private MemoryIndicator indicator() {
        return new MemoryIndicator(contributions, () -> readings.size() > 1 ? readings.removeFirst() : readings.getFirst(),
            () -> collections++, (interval, task) -> { intervals.add(interval); timers.add(task); return () -> stops++; });
    }

    private StatusEntry entry() {
        return contributions.status().stream().filter(entry -> entry.id().equals(MemoryIndicator.STATUS_ID)).findFirst().orElseThrow();
    }

    @Test void itIsAHiddenRightHandEntryThatClicksThroughTheCollectAction() {
        indicator();
        assertThat(entry().visible()).isFalse();
        assertThat(entry().left()).isFalse();
        assertThat(entry().actionId()).isEqualTo(MemoryIndicator.COLLECT);
        assertThat(contributions.action(MemoryIndicator.COLLECT)).get()
            .satisfies(action -> assertThat(action.title()).isEqualTo("Run Garbage Collection"));
        assertThat(timers).isEmpty();
    }

    @Test void shownWithAWindowItReadsTheHeapAndRefreshesOnOneSharedTimer() {
        var indicator = indicator();
        indicator.show(true);
        assertThat(timers).as("no window, no timer").isEmpty();
        indicator.windows(1);
        assertThat(entry().visible()).isTrue();
        assertThat(entry().text()).isEqualTo("300 of 2048M");
        assertThat(entry().tooltip()).startsWith("Heap: 300 MiB used");
        assertThat(intervals).containsExactly(Duration.ofSeconds(3));
        indicator.windows(2);
        assertThat(timers).as("one timer for every window").hasSize(1);
        timers.getFirst().run();
        assertThat(entry().text()).isEqualTo("400 of 2048M");
    }

    @Test void theTimerStopsWhenTheLastWindowClosesOrTheIndicatorIsTurnedOff() {
        var indicator = indicator();
        indicator.show(true);
        indicator.windows(1);
        indicator.windows(0);
        assertThat(stops).isEqualTo(1);
        indicator.windows(1);
        assertThat(timers).hasSize(2);
        indicator.show(false);
        assertThat(stops).isEqualTo(2);
        assertThat(entry().visible()).isFalse();
        indicator.show(false);
        assertThat(stops).as("stopping twice stops once").isEqualTo(2);
    }

    @Test void aClickCollectsGarbageAndRefreshes() {
        var indicator = indicator();
        indicator.show(true);
        indicator.windows(1);
        contributions.action(MemoryIndicator.COLLECT).orElseThrow().invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        assertThat(collections).isEqualTo(1);
        assertThat(entry().text()).isEqualTo("400 of 2048M");
    }

    @Test void closeStopsTheTimerAndRemovesTheEntry() {
        var indicator = indicator();
        indicator.show(true);
        indicator.windows(1);
        indicator.close();
        assertThat(stops).isEqualTo(1);
        assertThat(contributions.status()).noneMatch(entry -> entry.id().equals(MemoryIndicator.STATUS_ID));
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.MemoryIndicatorTest'`
Expected: FAIL — compilation error, `MemoryIndicator` does not exist.

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/application/MemoryIndicator.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.StatusEntry;
import dev.jasper.app.vmoptions.HeapReading;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The status bar's memory indicator (spec section 5.3): a contributed entry, {@code 312 of 2048M}, rendered the same in
 * every window, refreshed by one shared timer while it is shown and a window is open; a click (the
 * {@link #COLLECT} action) runs garbage collection and refreshes. Updates are instant. EDT only.
 */
final class MemoryIndicator implements AutoCloseable {
    static final String STATUS_ID = "app.memory.indicator";
    static final String COLLECT = "app.memory.gc";
    static final Duration INTERVAL = Duration.ofSeconds(3);

    private final StatusEntry entry;
    private final Supplier<HeapReading> readings;
    private final Runnable collect;
    private final BiFunction<Duration, Runnable, Runnable> every;
    private boolean shown;
    private int windows;
    private Runnable stop;

    /**
     * @param contributions where the entry and its action live
     * @param readings reads the heap
     * @param collect runs garbage collection
     * @param every starts a repeating task at an interval and returns the way to stop it
     */
    MemoryIndicator(Contributions contributions, Supplier<HeapReading> readings, Runnable collect,
                    BiFunction<Duration, Runnable, Runnable> every) {
        this.readings = Objects.requireNonNull(readings, "readings");
        this.collect = Objects.requireNonNull(collect, "collect");
        this.every = Objects.requireNonNull(every, "every");
        contributions.addAction(COLLECT, "Run Garbage Collection", null,
            List.of("memory", "heap", "gc", "garbage collection", "free memory"), Optional.empty(), invocation -> collectNow());
        entry = contributions.addStatus(STATUS_ID, false, Integer.MAX_VALUE);
        entry.setActionId(COLLECT);
        entry.setVisible(false);
    }

    /** Shows or hides the indicator: {@code window.memory_indicator} while the status bar shows. */
    void show(boolean value) {
        shown = value;
        update();
    }

    /** How many windows are open; without one the timer stops. */
    void windows(int count) {
        windows = count;
        update();
    }

    /** Runs garbage collection, then reads the heap again. */
    void collectNow() {
        collect.run();
        refresh();
    }

    @Override public void close() {
        stopTimer();
        entry.close();
    }

    /** Swing's repeating timer, as a plain function: start a task at an interval, get back the way to stop it. */
    static Runnable swingTimer(Duration interval, Runnable task) {
        javax.swing.Timer timer = new javax.swing.Timer((int) Math.max(1, Math.min(Integer.MAX_VALUE, interval.toMillis())),
            event -> task.run());
        timer.start();
        return timer::stop;
    }

    private void update() {
        entry.setVisible(shown);
        if (shown && windows > 0) {
            if (stop == null) {
                refresh();
                stop = every.apply(INTERVAL, this::refresh);
            }
        } else {
            stopTimer();
        }
    }

    private void stopTimer() {
        if (stop == null) return;
        stop.run();
        stop = null;
    }

    private void refresh() {
        HeapReading reading = readings.get();
        entry.setText(reading.label());
        entry.setTooltip(reading.tooltip());
    }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.MemoryIndicatorTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL. `MemoryIndicatorTest` matches the coverage pattern (`new ArrayList`); its row is:
`jasper-app/src/test/java/dev/jasper/app/application/MemoryIndicatorTest.java,app.application,MemoryIndicatorTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/application/MemoryIndicatorTest.java,New in custom VM options plan 3; test owner of the memory indicator`.
Add rows for any other file the test reports.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application/MemoryIndicator.java jasper-app/src/test/java/dev/jasper/app/application/MemoryIndicatorTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Memory: the status bar indicator

MemoryIndicator contributes a right-hand status entry ("312 of 2048M",
with a heap and non-heap tooltip) and its click action, Run Garbage
Collection; one shared timer refreshes it every 3 s while it is shown and
a window is open.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: `MemoryWarnings` — the low-memory warning

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/MemoryWarnings.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsNotices.java` (`lowMemory`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsFeature.java` (`onHeapSaved`)
- Test: `jasper-app/src/test/java/dev/jasper/app/application/MemoryWarningsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java` (one test)

**Interfaces:**
- Consumes: Task 2's `LowMemoryWatch`; Task 3's `vm.heap=<MiB>` fragment; `NotificationCenter.submit` (any thread).
- Produces: `static NotificationRequest VmOptionsNotices.lowMemory(Runnable adjust)`; `final class MemoryWarnings` with
  `ADJUST_STEP_MIB = 512`, constructor `MemoryWarnings(LowMemoryWatch watch, Function<Runnable, Runnable> watchHeap,
  NotificationCenter center, LongSupplier runningHeapMiB, Consumer<String> openSettings)`, `void start()`,
  `void signal()`, `void outOfMemory()`, `void heapChanged()`, `static boolean isOutOfMemory(Throwable)`;
  `VmOptionsFeature.onHeapSaved(Runnable)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/MemoryWarningsTest.java`:

```java
package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.MutableClock;
import dev.jasper.app.vmoptions.LowMemoryWatch;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class MemoryWarningsTest {
    private final MutableClock clock = new MutableClock();
    private final NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
    private final List<Runnable> watching = new ArrayList<>();
    private final List<String> opened = new ArrayList<>();
    private int stops;

    private MemoryWarnings warnings() {
        center.register(VmOptionsNotices.GROUP);
        return new MemoryWarnings(new LowMemoryWatch(clock), signal -> { watching.add(signal); return () -> stops++; }, center,
            () -> 2048, opened::add);
    }

    @Test void startWatchesTheHeapOnce() {
        var warnings = warnings();
        warnings.start();
        warnings.start();
        assertThat(watching).hasSize(1);
    }

    @Test void sixSignalsTwentySecondsApartWarnOnce() {
        var warnings = warnings();
        warnings.start();
        for (int i = 0; i < 8; i++) {
            watching.getFirst().run();
            clock.advance(Duration.ofSeconds(20));
        }
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().group()).isEqualTo("jasper.vmoptions");
            assertThat(entry.request().severity()).isEqualTo(Severity.WARNING);
            assertThat(entry.request().title()).isEqualTo("Jasper is running low on memory");
            assertThat(entry.request().actions()).extracting(NotificationAction::label).containsExactly("Adjust Memory Settings…");
            assertThat(entry.count()).isEqualTo(1);
        });
    }

    @Test void adjustOpensTheHeapFieldWithFiveHundredTwelveMiBMore() {
        var warnings = warnings();
        warnings.outOfMemory();
        center.history().getFirst().request().actions().getFirst().run().run();
        assertThat(opened).containsExactly("system#vm.heap=2560");
    }

    @Test void anOutOfMemoryErrorWarnsAtOnceAndOnlyOnceUntilTheHeapChanges() {
        var warnings = warnings();
        warnings.outOfMemory();
        warnings.outOfMemory();
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(entry.count()).isEqualTo(1));
        warnings.heapChanged();
        warnings.outOfMemory();
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(entry.count()).as("posted again").isEqualTo(2));
    }

    @Test void anOutOfMemoryErrorIsFoundAmongCauses() {
        assertThat(MemoryWarnings.isOutOfMemory(new OutOfMemoryError("Java heap space"))).isTrue();
        assertThat(MemoryWarnings.isOutOfMemory(new RuntimeException(new ExecutionException(new OutOfMemoryError("Metaspace"))))).isTrue();
        assertThat(MemoryWarnings.isOutOfMemory(new IllegalStateException("broken"))).isFalse();
        var first = new RuntimeException("first");
        var second = new RuntimeException("second", first);
        first.initCause(second);
        assertThat(MemoryWarnings.isOutOfMemory(first)).as("a cause cycle ends").isFalse();
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java`, add:

```java
    @Test void aSavedHeapIsReportedSoTheLowMemoryWarningCanShowAgain() throws IOException {
        var feature = feature(managed(), true);
        int[] saved = {0};
        feature.onHeapSaved(() -> saved[0]++);
        feature.hooks().heap().save().apply(4096).join();
        assertThat(saved[0]).isEqualTo(1);
        Files.write(file(), new byte[] {'-', 'X', 'm', 'x', '1', 'g', '\n', 0});
        assertThat(feature.hooks().heap().save().apply(3072)).isCompletedExceptionally();
        assertThat(saved[0]).as("a refused save changes nothing").isEqualTo(1);
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.MemoryWarningsTest' --tests 'dev.jasper.app.application.VmOptionsFeatureTest'`
Expected: FAIL — compilation errors (`MemoryWarnings`, `VmOptionsNotices.lowMemory`, `onHeapSaved` do not exist).

- [ ] **Step 3: Implement**

In `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsNotices.java`, after `failed(...)` add:

```java
    /** Jasper's heap stays nearly full, or ran out (spec section 5.4); {@code adjust} opens the heap limit. Any thread. */
    static NotificationRequest lowMemory(Runnable adjust) {
        return NotificationRequest.of(GROUP.id(), Severity.WARNING, "Jasper is running low on memory")
            .withContent("Raise the maximum heap size so Jasper keeps up; the new limit applies after a restart.")
            .withAction(new NotificationAction("Adjust Memory Settings…", adjust, false));
    }
```

and update the class comment's first sentence to name it: "Custom VM options notifications (spec sections 4.3, 5.1,
5.4 and 5.5) in their own group: a fallback start, a corrupted file, a pending restart, failures and low memory."

Create `jasper-app/src/main/java/dev/jasper/app/application/MemoryWarnings.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.vmoptions.LowMemoryWatch;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * The low-memory warning (spec section 5.4): the heap pool's threshold signals and an {@code OutOfMemoryError} raise
 * "Jasper is running low on memory" in the Memory &amp; VM options group, at most once until the user changes the
 * heap. {@link #signal()} and {@link #outOfMemory()} run on any thread; the rest on the EDT.
 */
final class MemoryWarnings {
    private static final System.Logger LOG = System.getLogger(MemoryWarnings.class.getName());
    /** What Adjust Memory Settings… adds to the current maximum, in MiB. */
    static final long ADJUST_STEP_MIB = 512;
    private static final int CAUSE_DEPTH = 16;

    private final LowMemoryWatch watch;
    private final Function<Runnable, Runnable> watchHeap;
    private final NotificationCenter center;
    private final LongSupplier runningHeapMiB;
    private final Consumer<String> openSettings;
    private Runnable stop;

    /**
     * @param watch the throttling rules
     * @param watchHeap starts calling its argument on each heap threshold signal; returns the way to stop
     * @param center posts the warning
     * @param runningHeapMiB this JVM's heap limit in MiB
     * @param openSettings opens Settings at a page and fragment, such as {@code system#vm.heap=2560}
     */
    MemoryWarnings(LowMemoryWatch watch, Function<Runnable, Runnable> watchHeap, NotificationCenter center,
                   LongSupplier runningHeapMiB, Consumer<String> openSettings) {
        this.watch = Objects.requireNonNull(watch, "watch");
        this.watchHeap = Objects.requireNonNull(watchHeap, "watchHeap");
        this.center = Objects.requireNonNull(center, "center");
        this.runningHeapMiB = Objects.requireNonNull(runningHeapMiB, "runningHeapMiB");
        this.openSettings = Objects.requireNonNull(openSettings, "openSettings");
    }

    /** Once, after start-up settles: starts watching the heap. EDT. */
    void start() {
        if (stop == null) stop = watchHeap.apply(this::signal);
    }

    /** A heap threshold signal. Any thread. */
    void signal() {
        if (watch.signal()) raise();
    }

    /** An {@code OutOfMemoryError} reached the uncaught-exception handler. Any thread; never throws. */
    void outOfMemory() {
        try {
            if (watch.outOfMemory()) raise();
        } catch (RuntimeException | Error failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not warn that Jasper ran out of memory", failure);
        }
    }

    /** The user saved a new heap limit: the warning may show again. EDT. */
    void heapChanged() { watch.heapChanged(); }

    /** Whether {@code failure}, or one of its causes, is an {@code OutOfMemoryError}. */
    static boolean isOutOfMemory(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < CAUSE_DEPTH; depth++, cause = cause.getCause()) {
            if (cause instanceof OutOfMemoryError) return true;
        }
        return false;
    }

    private void raise() { center.submit(VmOptionsNotices.lowMemory(this::adjust)); }

    /** Adjust Memory Settings…: the heap field, pre-filled with the current maximum plus 512 MiB. EDT. */
    private void adjust() { openSettings.accept("system#vm.heap=" + (runningHeapMiB.getAsLong() + ADJUST_STEP_MIB)); }
}
```

In `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsFeature.java`:

1. After the field `private final List<Runnable> listeners = new ArrayList<>();` add:

```java
    private Runnable heapSaved = () -> { };
```

2. After `editable()` add:

```java
    /** Runs {@code action} after each successful heap save (the low-memory warning may show again). EDT. */
    void onHeapSaved(Runnable action) { heapSaved = Objects.requireNonNull(action, "action"); }
```

3. In `saveHeap`'s success turn, replace:

```java
                notifyListeners();
                done.complete(null);
                reload();
```

with:

```java
                notifyListeners();
                done.complete(null);
                heapSaved.run();
                reload();
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: BUILD SUCCESSFUL. `MemoryWarningsTest` matches the coverage pattern; its row is:
`jasper-app/src/test/java/dev/jasper/app/application/MemoryWarningsTest.java,app.application,MemoryWarningsTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/application/MemoryWarningsTest.java,New in custom VM options plan 3; test owner of the low-memory warning`.
Add rows for any other file the test reports.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/test/java/dev/jasper/app/application docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Memory: the low-memory warning

MemoryWarnings turns heap threshold signals and OutOfMemoryErrors into
"Jasper is running low on memory" (once until the heap is saved again);
Adjust Memory Settings... opens the heap limit with 512 MiB more staged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 7: Wire it into the application, and document it

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutCatalog.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`
- Docs: `docs/configuration.md`, `docs/notifications.md`, `docs/app-architecture.md`, `docs/STATUS.md`, the spec's
  status paragraph, this plan's status line

**Interfaces:**
- Consumes: Tasks 1–6 (`HeapReading::current`, `LowMemoryWatch`, `HeapThreshold.install`, `MemoryIndicator`,
  `MemoryWarnings`, `VmOptionsFeature.onHeapSaved`, `CoreSettings.WINDOW_MEMORY_INDICATOR`).

- [ ] **Step 1: Write the failing tests**

In `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`, in
`theManagerIsAContributedActionInTheFileMenu`, after the `app.vmoptions` assertion add:

```java
            assertThat(application[0].contributions().action(MemoryIndicator.COLLECT)).get()
                .satisfies(action -> assertThat(action.title()).isEqualTo("Run Garbage Collection"));
            assertThat(application[0].contributions().status()).anySatisfy(entry -> {
                assertThat(entry.id()).isEqualTo(MemoryIndicator.STATUS_ID);
                assertThat(entry.visible()).as("off by default").isFalse();
            });
```

and add a test next to it (same fixtures as that test):

```java
    @Test void anOutOfMemoryErrorWarnsThatJasperIsLowOnMemory() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, null, false, dirs);
            application[0].unexpected(Thread.currentThread(), new RuntimeException("wrapped", new OutOfMemoryError("Java heap space")));
            assertThat(application[0].notificationCenter().history())
                .anySatisfy(entry -> assertThat(entry.request().title()).isEqualTo("Jasper is running low on memory"));
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.JasperApplicationPluginsTest'`
Expected: FAIL — `app.memory.gc` is not registered and no low-memory notification is posted.

- [ ] **Step 3: Wire**

In `JasperApplication.java`:

1. Fields, next to `private VmOptionsFeature vmOptions;`:

```java
    private final MemoryIndicator memoryIndicator;
    private volatile MemoryWarnings memoryWarnings;
```

2. In the constructor, just before `if (configuration != null) configuration.onSnapshot(snapshot -> {`, add:

```java
        // The indicator reads the heap only while shown; nothing here runs before the first output paints.
        memoryIndicator = new MemoryIndicator(contributions, dev.jasper.app.vmoptions.HeapReading::current, System::gc,
            MemoryIndicator::swingTimer);
        if (configuration != null) {
            showMemoryIndicator(configuration.state());
            configuration.onState(this::showMemoryIndicator);
        }
```

(If the class has several constructors, put this in the one the others delegate to, so the final field is assigned on
every path.)

3. Add the method below `applyGroupModes(...)`:

```java
    /** {@code window.memory_indicator}, live; the indicator shows only where the status bar does. */
    private void showMemoryIndicator(dev.jasper.app.config.ConfigService.State state) {
        memoryIndicator.show(state.values().get(dev.jasper.app.config.CoreSettings.WINDOW_MEMORY_INDICATOR)
            && state.values().get(dev.jasper.app.config.CoreSettings.WINDOW_STATUS_BAR));
    }
```

4. In `composeWindow`, right after `windows.add(window);` (on the line
`windows.add(window); window.restoreLayout(uiState); window.show();`) add `memoryIndicator.windows(windows.size());`
after `window.show();`. In `forget(TerminalWindow window)`, right after `windows.remove(window);` add:

```java
        memoryIndicator.windows(windows.size());
```

5. In `startPlugins`, right after the `vmOptions = new VmOptionsFeature(…);` statement, add:

```java
        memoryWarnings = new MemoryWarnings(new dev.jasper.app.vmoptions.LowMemoryWatch(java.time.Clock.systemUTC()),
            signal -> dev.jasper.app.vmoptions.HeapThreshold.install(dev.jasper.app.vmoptions.LowMemoryWatch.FRACTION, signal)
                .<Runnable>map(threshold -> threshold::close).orElse(() -> { }),
            notificationCenter, dev.jasper.app.vmoptions.VmOptionsLaunch::runningMaxHeapMiB, page -> openSettings(null, page));
        vmOptions.onHeapSaved(memoryWarnings::heapChanged);
```

and replace the three start-up trigger lines:

```java
        trace.when(StartupTrace.Milestone.OUTPUT_PAINTED, SwingUtilities::invokeLater, vmOptions::startupSettled);
        trace.when(StartupTrace.Milestone.WARMED_UP, SwingUtilities::invokeLater, vmOptions::startupSettled);
        trace.when(StartupTrace.Milestone.FIRST_PAINT, SwingUtilities::invokeLater, () -> afterDelay(Duration.ofSeconds(5), vmOptions::startupSettled));
```

with:

```java
        Runnable settled = () -> {
            vmOptions.startupSettled();
            memoryWarnings.start();
        };
        trace.when(StartupTrace.Milestone.OUTPUT_PAINTED, SwingUtilities::invokeLater, settled);
        trace.when(StartupTrace.Milestone.WARMED_UP, SwingUtilities::invokeLater, settled);
        trace.when(StartupTrace.Milestone.FIRST_PAINT, SwingUtilities::invokeLater, () -> afterDelay(Duration.ofSeconds(5), settled));
```

(keep the comment above them, extending it to "the options file is read and the heap watched once …").

6. Replace `unexpected`:

```java
    /** An uncaught failure, after the default handler logged it: an incident (spec section 7, producer 6). Any thread. */
    public void unexpected(Thread thread, Throwable failure) {
        incidentNotices.jasperFailure(thread == null ? "an unknown thread" : "thread " + thread.getName(), failure);
    }
```

with:

```java
    /**
     * An uncaught failure, after the default handler logged it: an incident (spec section 7, producer 6), and the
     * low-memory warning when it is an {@code OutOfMemoryError} (spec section 5.4). Any thread.
     */
    public void unexpected(Thread thread, Throwable failure) {
        MemoryWarnings warnings = memoryWarnings;
        if (warnings != null && MemoryWarnings.isOutOfMemory(failure)) warnings.outOfMemory();
        incidentNotices.jasperFailure(thread == null ? "an unknown thread" : "thread " + thread.getName(), failure);
    }
```

In `ShortcutCatalog.java`, add `id.equals("app.memory.gc")` to the "Application" condition in `pluginGroup` (next to
`id.equals("app.vmoptions")`).

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.settingsui.*' verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Document**

`docs/configuration.md`:
- At the end of the paragraph under `### Custom VM options` that ends "so in a notification in the **Memory & VM
  options** group.", add: "Settings › Window › **Show memory indicator** (`window.memory_indicator`) adds the heap in
  use and its limit, such as `312 of 2048M`, to the status bar; click it to run garbage collection. When the heap stays
  nearly full, or Jasper runs out of memory, a notification in the same group offers **Adjust Memory Settings…**, which
  opens the heap limit with 512 MiB more filled in."
- In the bindable-actions sentence, replace "`"app.vmoptions"` opens `jasper.vmoptions` in your
  editor; none has a default shortcut." with "`"app.vmoptions"` opens `jasper.vmoptions` in your
  editor and `"app.memory.gc"` runs garbage collection; none has a default shortcut." (keep the line wrapping close to
  the surrounding width).

`docs/notifications.md`, the "Memory & VM options" row: change "…, and an options file that could not be opened, saved
or revealed" to "…, an options file that could not be opened, saved or revealed, and a warning when Jasper runs low on
memory".

`docs/app-architecture.md`, the `vmoptions` row: change "the bundled `Jasper.cfg` and the check that runs the bundled
`java`." to "the bundled `Jasper.cfg`, the check that runs the bundled `java`, the heap readings and the low-memory
watch with its heap threshold."

In the spec's status paragraph add "Plan 3 (memory) complete on 2026-10-13." at the end. In this plan, change
`Status: **Not started.**` to `Status: **Complete on 2026-10-13.**` followed by any deviation beyond the eight planned
ones. In `docs/STATUS.md`, add at the top (fill in the counts from Step 6):

```markdown
**Custom VM options plan 3 (memory) complete — 2026-10-13.** On `claude/custom-vm-options-memory` (not pushed):
Settings › Window › Show memory indicator (`window.memory_indicator`, off by default) adds `312 of 2048M` to the status
bar, refreshed every 3 s by one shared timer while shown with a window open, with a heap and non-heap tooltip; a click
runs garbage collection (the `app.memory.gc` action, also in the palette). A collection-usage threshold at 95% of the
old generation feeds `LowMemoryWatch` (one signal per 20 s, more than five in 15 minutes warn, once until the heap is
saved again); an `OutOfMemoryError` warns at once. The warning, "Jasper is running low on memory", is in the Memory &
VM options group; **Adjust Memory Settings…** opens the heap limit with the running maximum plus 512 MiB staged.
Deviations: the eight in the plan. Full `./gradlew check`: <tests> tests, <failures> failures, <skips> skips. Manual
checks pending (the user): turn the indicator on and watch it update; click it; in the packaged app with a small heap
(`-Xmx600m` in `jasper.vmoptions`), open many large scrollbacks until the warning shows, and use Adjust Memory
Settings….
```

- [ ] **Step 6: Run the whole check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Count the tests across every module (including `plugins/*`):

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = s = 0
for p in list(pathlib.Path('.').glob('*/build/test-results/test/*.xml')) + list(pathlib.Path('.').glob('plugins/*/build/test-results/test/*.xml')):
    r = ET.parse(p).getroot(); t += int(r.get('tests')); f += int(r.get('failures')) + int(r.get('errors')); s += int(r.get('skipped'))
print(t, 'tests', f, 'failures', s, 'skips')
PY
```

Put the counts into the STATUS entry.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src docs
git commit -F - <<'EOF'
Memory indicator and low-memory warning in the application

JasperApplication shows the indicator from window.memory_indicator (where
the status bar shows), tells it how many windows are open, starts the
heap watch with the other settled work, and warns on an OutOfMemoryError
reaching the uncaught-exception handler. Docs: configuration,
notifications, architecture, STATUS.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Hand-off checks for the user

Agents never launch the GUI. After the branch is reviewed:

1. `./gradlew :jasper-app:run`: Settings › Window › Show memory indicator; the status bar shows `N of 2048M`, updating
   every few seconds; hover for the breakdown; click it and watch the used figure drop. Turn the status bar off: the
   checkbox greys out and the indicator goes with the bar.
2. In the packaged app with `-Xmx600m` in `jasper.vmoptions`, fill memory (many panes with large scrollback, or `yes`
   into a pane for a while) until "Jasper is running low on memory" shows; **Adjust Memory Settings…** opens System
   Settings with the heap field at the running maximum plus 512 MiB, waiting for Apply.
