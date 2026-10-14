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

    private MemoryWarnings warnings() { return warnings(2048, 2048); }

    private MemoryWarnings warnings(long running, long saved) {
        center.register(VmOptionsNotices.GROUP);
        return new MemoryWarnings(new LowMemoryWatch(clock), signal -> { watching.add(signal); return () -> stops++; }, center,
            () -> running, () -> saved, opened::add);
    }

    @Test void startWatchesTheHeapOnce() {
        var warnings = warnings();
        warnings.start();
        warnings.start();
        assertThat(watching).hasSize(1);
    }

    @Test void closeStopsWatchingOnce() {
        var warnings = warnings();
        warnings.start();
        warnings.close();
        assertThat(stops).isEqualTo(1);
        warnings.close();
        assertThat(stops).as("closing twice stops once").isEqualTo(1);
    }

    @Test void aWarningClosedBeforeItStartedOrAfterNeverWatches() {
        var unstarted = warnings();
        unstarted.close();
        unstarted.start();
        assertThat(watching).as("start after close installs nothing").isEmpty();
        assertThat(stops).isZero();

        var started = warnings();
        started.start();
        started.close();
        started.start();
        assertThat(watching).as("start after a close does not watch again").hasSize(1);
        assertThat(stops).isEqualTo(1);
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
            assertThat(entry.request().actions()).extracting(NotificationAction::label).containsExactly("Adjust Memory Settings\u2026");
            assertThat(entry.count()).isEqualTo(1);
        });
    }

    @Test void adjustOpensTheHeapFieldWithFiveHundredTwelveMiBMore() {
        var warnings = warnings();
        warnings.outOfMemory();
        center.history().getFirst().request().actions().getFirst().run().run();
        assertThat(opened).containsExactly("system#vm.heap=2560");
    }

    @Test void adjustOnlyRevealsTheRowWhenAHigherHeapIsAlreadySaved() {
        warnings(2048, 4096).outOfMemory();
        center.history().getFirst().request().actions().getFirst().run().run();
        assertThat(opened).as("the saved 4096 is already more than 2560").containsExactly("system#vm.heap");
    }

    @Test void adjustOnlyRevealsTheRowWhenTheSavedHeapEqualsTheProposal() {
        warnings(2048, 2560).outOfMemory();
        center.history().getFirst().request().actions().getFirst().run().run();
        assertThat(opened).as("equal is not higher").containsExactly("system#vm.heap");
    }

    @Test void adjustProposesMoreThanTheRunningHeapWhenTheSavedOneIsNotHigher() {
        warnings(2048, 1024).outOfMemory();
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
