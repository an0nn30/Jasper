package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
    private int reads;
    private boolean failing;

    private MemoryIndicator indicator() {
        return new MemoryIndicator(contributions, this::read,
            () -> collections++, (interval, task) -> { intervals.add(interval); timers.add(task); return () -> stops++; });
    }

    private HeapReading read() {
        reads++;
        if (failing) throw new IllegalStateException("management is unavailable");
        return readings.size() > 1 ? readings.removeFirst() : readings.getFirst();
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
        indicator.ready();
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

    @Test void beforeStartUpSettlesItReadsNoHeapStartsNoTimerAndStaysHidden() {
        var indicator = indicator();
        indicator.show(true);
        indicator.windows(1);
        assertThat(reads).as("nothing reads the heap before start-up settles").isZero();
        assertThat(timers).isEmpty();
        assertThat(entry().visible()).isFalse();
        contributions.action(MemoryIndicator.COLLECT).orElseThrow().invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        assertThat(collections).as("a click still collects").isEqualTo(1);
        assertThat(reads).as("and still reads no heap before start-up settles").isZero();
        indicator.ready();
        assertThat(reads).as("the first read happens when start-up settles").isEqualTo(1);
        assertThat(timers).hasSize(1);
        assertThat(entry().visible()).isTrue();
        assertThat(entry().text()).isEqualTo("300 of 2048M");
        indicator.ready();
        indicator.windows(2);
        assertThat(reads).as("ready is idempotent").isEqualTo(1);
        assertThat(timers).as("and starts the timer once").hasSize(1);
    }

    @Test void settlingWithoutAWindowOrWhileHiddenStartsNothing() {
        var indicator = indicator();
        indicator.ready();
        assertThat(reads).isZero();
        indicator.windows(1);
        assertThat(reads).as("settled, but not shown").isZero();
        assertThat(timers).isEmpty();
        assertThat(entry().visible()).isFalse();
        indicator.show(true);
        assertThat(reads).isEqualTo(1);
        assertThat(entry().visible()).isTrue();
    }

    @Test void aReadingThatFailsNeverEscapesAndKeepsTheLastTextAndIsLoggedOnce() {
        var logger = java.util.logging.Logger.getLogger(MemoryIndicator.class.getName());
        var previousLevel = logger.getLevel();
        boolean previousParents = logger.getUseParentHandlers();
        var records = new ArrayList<java.util.logging.LogRecord>();
        var handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        handler.setLevel(java.util.logging.Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(java.util.logging.Level.ALL);
        logger.setUseParentHandlers(false);
        try {
            var indicator = indicator();
            indicator.show(true);
            indicator.windows(1);
            indicator.ready();
            assertThat(entry().text()).isEqualTo("300 of 2048M");
            failing = true;
            assertThatCode(() -> timers.getFirst().run()).doesNotThrowAnyException();
            assertThatCode(() -> timers.getFirst().run()).doesNotThrowAnyException();
            assertThatCode(() -> contributions.action(MemoryIndicator.COLLECT).orElseThrow()
                .invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()))).doesNotThrowAnyException();
            assertThat(reads).isEqualTo(4);
            assertThat(entry().text()).as("the last text stays").isEqualTo("300 of 2048M");
            assertThat(entry().visible()).isTrue();
            assertThat(records).as("the failure is logged once, then quiet").singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
                assertThat(record.getThrown()).isInstanceOf(IllegalStateException.class);
            });
            failing = false;
            timers.getFirst().run();
            assertThat(entry().text()).as("it recovers on the next good reading").isEqualTo("400 of 2048M");
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParents);
        }
    }

    @Test void aFirstReadingThatFailsLeavesTheEntryHiddenUntilOneSucceeds() {
        failing = true;
        var indicator = indicator();
        indicator.show(true);
        indicator.windows(1);
        assertThatCode(indicator::ready).doesNotThrowAnyException();
        assertThat(timers).as("the timer runs on and retries").hasSize(1);
        assertThat(entry().visible()).isFalse();
        assertThat(entry().text()).isEmpty();
        assertThatCode(() -> timers.getFirst().run()).doesNotThrowAnyException();
        assertThat(entry().visible()).isFalse();
        failing = false;
        timers.getFirst().run();
        assertThat(entry().visible()).isTrue();
        assertThat(entry().text()).isEqualTo("300 of 2048M");
    }

    @Test void theEntryChangesOnlyWhenItsValuesChange() {
        readings.clear();
        readings.add(new HeapReading(300L << 20, 512L << 20, 2048L << 20, 90L << 20));
        var indicator = indicator();
        indicator.show(true);
        indicator.windows(1);
        indicator.ready();
        var changes = new ArrayList<Contributions.Kind>();
        try (var subscription = contributions.onChanged(changes::add)) {
            timers.getFirst().run();
            timers.getFirst().run();
            indicator.windows(2);
            indicator.show(true);
            indicator.ready();
            assertThat(reads).isEqualTo(3);
            assertThat(changes).as("the same reading and visibility change nothing").isEmpty();
            readings.clear();
            readings.add(new HeapReading(310L << 20, 512L << 20, 2048L << 20, 90L << 20));
            timers.getFirst().run();
            assertThat(changes).as("a new reading changes the entry").isNotEmpty().containsOnly(Contributions.Kind.STATUS);
            changes.clear();
            indicator.show(false);
            assertThat(changes).as("hiding changes it once").containsExactly(Contributions.Kind.STATUS);
        }
    }

    @Test void theTimerStopsWhenTheLastWindowClosesOrTheIndicatorIsTurnedOff() {
        var indicator = indicator();
        indicator.ready();
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
        indicator.ready();
        indicator.show(true);
        indicator.windows(1);
        contributions.action(MemoryIndicator.COLLECT).orElseThrow().invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        assertThat(collections).isEqualTo(1);
        assertThat(entry().text()).isEqualTo("400 of 2048M");
    }

    @Test void aCollectionWhileHiddenRunsGarbageCollectionAndReadsNoHeap() {
        indicator();
        contributions.action(MemoryIndicator.COLLECT).orElseThrow().invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        assertThat(collections).isEqualTo(1);
        assertThat(reads).as("a hidden indicator reads no heap").isZero();
        assertThat(entry().text()).isEmpty();
        assertThat(entry().visible()).isFalse();
    }

    @Test void closeStopsTheTimerAndRemovesTheEntry() {
        var indicator = indicator();
        indicator.ready();
        indicator.show(true);
        indicator.windows(1);
        indicator.close();
        assertThat(stops).isEqualTo(1);
        assertThat(contributions.status()).noneMatch(entry -> entry.id().equals(MemoryIndicator.STATUS_ID));
        assertThat(contributions.action(MemoryIndicator.COLLECT)).as("the click action goes with the entry").isEmpty();
        int readsAtClose = reads;
        indicator.show(true);
        indicator.windows(1);
        indicator.collectNow();
        assertThat(timers).as("a closed indicator starts no timer").hasSize(1);
        assertThat(stops).isEqualTo(1);
        assertThat(collections).as("a closed indicator collects nothing").isZero();
        assertThat(reads).as("a closed indicator reads no heap").isEqualTo(readsAtClose);
        indicator.close();
        assertThat(stops).as("closing twice stops once").isEqualTo(1);
        indicator();
        assertThat(contributions.action(MemoryIndicator.COLLECT)).as("a new indicator registers again").isPresent();
    }
}
