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
        StartupTrace.JvmStatus status = StartupTrace.JvmStatus.of("/Jasper.app/Contents/app/jasper.aot", true, null);

        StartupTrace trace() { return trace(null); }

        StartupTrace trace(Path file) {
            return new StartupTrace(new StartupTrace.Environment(now::get, 0, 100.0, Instant.parse("2026-10-02T16:35:09.123Z"),
                42, file, log::add, Runnable::run, () -> status,
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
            "Startup (cold, AOT cache used, CDS on, look=default): main entered 101 ms, output painted 600 ms, plugins bound 400 ms"
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
            "Startup (background, AOT cache used, CDS on, look=default): plugins bound 300 ms, warmed up 350 ms");
    }

    @Test void theCapSettlesTenSecondsAfterTheProcessStartWithWhateverWasMarked() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        assertThat(fixture.delays).first().isEqualTo(TimeUnit.MILLISECONDS.toNanos(9_900));
        fixture.at(20);
        trace.mark(StartupTrace.Milestone.MAIN_ENTERED);
        fixture.timers.getFirst().run();
        assertThat(fixture.log).containsExactly("Startup (cold, AOT cache used, CDS on, look=default): main entered 120 ms");
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

    @Test void aWindowAfterAnIdleSpellReportsTheSpellAndItsMemoryUntilShown() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        var readings = new java.util.ArrayDeque<>(List.of(new MemoryReading(1_000, 120L << 20), new MemoryReading(41_000, 300L << 20)));
        trace.readMemoryWith(readings::poll);
        fixture.at(1_000);
        trace.windowsClosed();
        fixture.at(1_000 + TimeUnit.MINUTES.toMillis(170));
        WindowTrace window = trace.window("handoff");
        fixture.at(1_000 + TimeUnit.MINUTES.toMillis(170) + 900);
        window.mark(WindowTrace.Milestone.SHOWN);
        window.mark(WindowTrace.Milestone.SHOWN);
        window.mark(WindowTrace.Milestone.OUTPUT_PAINTED);
        assertThat(fixture.log).containsExactly("Window (handoff): shown 900 ms, output painted 900 ms after the request;"
            + " no window for 2 h 50 min before, 40000 page faults until shown, working set 120 to 300 MB");
        assertThat(readings).as("read once when requested and once when shown").isEmpty();
        // The idle spell belongs to that window only.
        trace.window("menu").mark(WindowTrace.Milestone.OUTPUT_PAINTED);
        assertThat(fixture.log.getLast()).isEqualTo("Window (menu): output painted 0 ms after the request");
    }

    @Test void aWindowWithoutAnIdleSpellBeforeItNeverReadsMemory() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        trace.readMemoryWith(() -> { throw new AssertionError("a first or further window pays nothing for the reading"); });
        WindowTrace first = trace.window("cold");
        first.mark(WindowTrace.Milestone.SHOWN);
        first.mark(WindowTrace.Milestone.OUTPUT_PAINTED);
        assertThat(fixture.log).containsExactly("Window (cold): shown 0 ms, output painted 0 ms after the request");
    }

    @Test void anUnreadableMemoryReadingLeavesTheLineAsItWas() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        trace.readMemoryWith(() -> { throw new IllegalStateException("no counters"); });
        trace.windowsClosed();
        fixture.at(45_000);
        trace.window("reopen").mark(WindowTrace.Milestone.OUTPUT_PAINTED);
        assertThat(fixture.log).containsExactly("Window (reopen): output painted 0 ms after the request; no window for 45 s before");
        assertThat(WindowTrace.duration(TimeUnit.MINUTES.toNanos(90))).isEqualTo("90 min");
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
        assertThat(StartupTrace.JvmStatus.of("", true, null)).isEqualTo(new StartupTrace.JvmStatus("none", "on"));
        assertThat(StartupTrace.JvmStatus.of("/a.aot", true, null)).isEqualTo(new StartupTrace.JvmStatus("used", "on"));
        assertThat(StartupTrace.JvmStatus.of("/a.aot", false, null)).isEqualTo(new StartupTrace.JvmStatus("stale", "off"));
        assertThat(StartupTrace.JvmStatus.of(null, false, null)).isEqualTo(new StartupTrace.JvmStatus("none", "off"));
    }

    @Test void theJvmStatusNamesASkippedCacheByItsReason() {
        assertThat(StartupTrace.JvmStatus.of("", true, "launch-failed")).isEqualTo(new StartupTrace.JvmStatus("skipped: launch-failed", "on"));
        assertThat(StartupTrace.JvmStatus.of("", true, "remembered")).isEqualTo(new StartupTrace.JvmStatus("skipped: remembered", "on"));
        assertThat(StartupTrace.JvmStatus.of("", false, "environment")).isEqualTo(new StartupTrace.JvmStatus("skipped: environment", "off"));
        assertThat(StartupTrace.JvmStatus.of("", true, "/Users/me/notes")).as("a hand-set value stays out of the log")
            .isEqualTo(new StartupTrace.JvmStatus("skipped: environment", "on"));
        assertThat(StartupTrace.JvmStatus.of("/a.aot", true, " ")).as("a blank value is no skip")
            .isEqualTo(new StartupTrace.JvmStatus("used", "on"));
    }

    @Test void theLaunchersSkipReachesTheObservedStatus() {
        String previous = System.getProperty("jasper.aotCacheSkipped");
        System.setProperty("jasper.aotCacheSkipped", "remembered");
        try {
            assertThat(StartupTrace.JvmStatus.observe().aotCache()).isEqualTo("skipped: remembered");
        } finally {
            if (previous == null) System.clearProperty("jasper.aotCacheSkipped");
            else System.setProperty("jasper.aotCacheSkipped", previous);
        }
    }

    @Test void aStartWithoutTheCacheSaysWhyInTheLineAndTheFile() {
        var fixture = new Fixture();
        fixture.status = StartupTrace.JvmStatus.of("", true, "launch-failed");
        StartupTrace trace = fixture.trace();
        fixture.at(20);
        trace.mark(StartupTrace.Milestone.MAIN_ENTERED);
        trace.settle();
        assertThat(fixture.log).containsExactly("Startup (cold, AOT cache skipped: launch-failed, CDS on, look=default): main entered 120 ms");
        assertThat(trace.document(trace.status()).get("jvm")).isEqualTo(java.util.Map.of("aotCache", "skipped: launch-failed", "cds", "on"));
    }

    @Test void aDetachedTraceNeverLogsOrSchedules() {
        StartupTrace trace = StartupTrace.detached();
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        trace.mark(StartupTrace.Milestone.PLUGINS_BOUND);
        trace.window("menu").mark(WindowTrace.Milestone.OUTPUT_PAINTED);
        assertThat(trace.millis(StartupTrace.Milestone.OUTPUT_PAINTED)).isPresent();
        assertThat(trace.windows()).hasSize(1);
    }

    @Test void aThrowingWaiterNeitherStopsTheOthersNorTheSettleNorTheCaller() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        AtomicInteger second = new AtomicInteger();
        trace.when(StartupTrace.Milestone.OUTPUT_PAINTED, Runnable::run, () -> { throw new IllegalStateException("boom"); });
        trace.when(StartupTrace.Milestone.OUTPUT_PAINTED, Runnable::run, second::incrementAndGet);
        trace.mark(StartupTrace.Milestone.PLUGINS_BOUND);
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        assertThat(second).hasValue(1);
        assertThat(fixture.log).hasSize(2);
        assertThat(fixture.log.get(0)).isEqualTo("A start-up trace action failed after output painted: java.lang.IllegalStateException");
        assertThat(fixture.log.get(1)).startsWith("Startup (cold");
    }

    @Test void anInvalidTraceFilePathMeansNoTraceFile() {
        assertThat(StartupTrace.traceFile("\u0000bad")).isNull();
        assertThat(StartupTrace.traceFile("  ")).isNull();
        assertThat(StartupTrace.traceFile(null)).isNull();
        assertThat(StartupTrace.traceFile("/tmp/trace.json")).isEqualTo(Path.of("/tmp/trace.json"));
    }

    @Test void theLineNamesTheLook() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        trace.look("dev.jasper.enterprise");
        fixture.at(20);
        trace.mark(StartupTrace.Milestone.MAIN_ENTERED);
        trace.settle();
        assertThat(fixture.log).containsExactly("Startup (cold, AOT cache used, CDS on, look=dev.jasper.enterprise): main entered 120 ms");
    }
}
