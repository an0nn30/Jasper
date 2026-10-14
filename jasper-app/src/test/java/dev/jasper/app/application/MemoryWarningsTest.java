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
