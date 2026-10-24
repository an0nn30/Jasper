package dev.jasper.app.application;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CygwinFeatureTest {
    @Test void runsOffUiOnceAndRefreshesOnCompletion() {
        var worker = new ArrayDeque<Runnable>(); var ui = new ArrayDeque<Runnable>(); var calls = new AtomicInteger(); var notices = new ArrayList<String>();
        var feature = new CygwinFeature(worker::add, ui::add, () -> { calls.incrementAndGet(); return "finished"; },
            () -> notices.add("refresh"), (failed, text) -> notices.add(text));
        feature.manage(); feature.manage(); assertThat(worker).hasSize(1); assertThat(calls).hasValue(0);
        worker.remove().run(); assertThat(notices).isEmpty(); ui.remove().run();
        assertThat(calls).hasValue(1); assertThat(notices).containsExactly("refresh", "finished");
    }
    @Test void closingSuppressesCallbacksWithoutCancellingSupervisor() {
        var worker = new ArrayDeque<Runnable>(); var ui = new ArrayDeque<Runnable>(); var calls = new AtomicInteger();
        var feature = new CygwinFeature(worker::add, ui::add, () -> { calls.incrementAndGet(); return "finished"; },
            () -> { throw new AssertionError("disposed catalog"); }, (failed, text) -> { throw new AssertionError("disposed UI"); });
        feature.manage(); worker.remove().run(); feature.close(); ui.remove().run(); assertThat(calls).hasValue(1);
    }
}
