package dev.jasper.app.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Spec 10.11: the splash's steps follow start-up as it happens; the fill is the step's number over the count. */
class StartupStepsTest {
    @Test void reconciliationPreservesCountOnlyCallersAndStopsAfterStartup() {
        var shown = new ArrayList<StartupSteps.Step>();
        var steps = StartupSteps.of("Protean", 2, shown::add, () -> {});
        steps.configuration();
        steps.reconcilePlugins(java.util.List.of("Remote", "Remote"));
        assertThat(shown).containsExactly(new StartupSteps.Step(StartupSteps.CONFIGURATION, 2, 6));
        steps.reconcilePlugins(java.util.List.of("Remote"));
        assertThat(steps.current()).isEqualTo(new StartupSteps.Step(StartupSteps.CONFIGURATION, 2, 5));
        steps.plugin("Remote");
        assertThatThrownBy(() -> steps.reconcilePlugins(java.util.List.of())).isInstanceOf(IllegalStateException.class);
        steps.done();
        steps.reconcilePlugins(java.util.List.of());
        assertThat(steps.current().count()).isEqualTo(5);
    }

    @Test void resolvedQueueCopiesNamesAndKeepsDuplicatePositions() {
        var names = new ArrayList<>(java.util.List.of("Remote", "Remote", "日本語 🚀"));
        var steps = StartupSteps.of("Protean", names, step -> {}, () -> {});
        names.clear();
        assertThat(steps.queue()).containsExactly(StartupSteps.RUNTIME, StartupSteps.CONFIGURATION,
            StartupSteps.PLUGIN + "Remote", StartupSteps.PLUGIN + "Remote", StartupSteps.PLUGIN + "日本語 🚀",
            StartupSteps.WORKSPACE, "Starting Protean Workstation…");
        steps.configuration(); steps.plugin("Remote"); steps.plugin("Remote");
        assertThat(steps.current().index()).isEqualTo(4);
        steps.workspace(); assertThat(steps.current().index()).isEqualTo(6);
        assertThat(StartupSteps.queue("Protean", java.util.List.of())).hasSize(4);
    }

    @Test void theStepsFollowStartUpAndFillIndexOverCount() {
        var shown = new ArrayList<StartupSteps.Step>();
        var closed = new AtomicInteger();
        var steps = StartupSteps.of("JasperOps", 2, shown::add, closed::incrementAndGet);
        assertThat(steps.current()).isEqualTo(new StartupSteps.Step("Starting Java runtime…", 1, 6));
        assertThat(steps.active()).isTrue();
        steps.configuration();
        steps.plugin("File Server");
        steps.plugin("Remote");
        steps.workspace();
        steps.workstation();
        assertThat(shown).containsExactly(new StartupSteps.Step("Reading configuration…", 2, 6),
            new StartupSteps.Step("Loading plugin: File Server", 3, 6), new StartupSteps.Step("Loading plugin: Remote", 4, 6),
            new StartupSteps.Step("Restoring workspace…", 5, 6), new StartupSteps.Step("Starting JasperOps Workstation…", 6, 6));
        assertThat(shown.getFirst().fraction()).isEqualTo(2 / 6.0);
        assertThat(shown.getLast().fraction()).isEqualTo(1.0);
        assertThat(closed).hasValue(0);
        steps.done();
        steps.done();
        assertThat(closed).as("closed once").hasValue(1);
        assertThat(steps.active()).isFalse();
        steps.plugin("Late");
        steps.workstation();
        assertThat(shown).as("nothing after done").hasSize(5);
    }

    /** Spec 10.11: a splash never outlives its start, so steps a resident keeps hold nothing of it once done. */
    @Test void doneLetsGoOfTheSplash() throws InterruptedException {
        var steps = held(new byte[8 << 20]);
        var art = steps.reference();
        steps.steps().done();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (art.get() != null && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(20);
        }
        assertThat(art.get()).as("the splash's art, reachable only through the steps").isNull();
        assertThat(steps.steps().active()).isFalse();
    }

    private record Held(StartupSteps steps, java.lang.ref.WeakReference<byte[]> reference) { }

    /** Steps whose show and close are the only holders of {@code art}. */
    private static Held held(byte[] art) {
        var reference = new java.lang.ref.WeakReference<>(art);
        return new Held(StartupSteps.of("JasperOps", 0, step -> { if (art.length == 0) throw new AssertionError(); },
            () -> { if (art.length == 0) throw new AssertionError(); }), reference);
    }

    @Test void stepsOnlyMoveForward() {
        var shown = new ArrayList<StartupSteps.Step>();
        var steps = StartupSteps.of("JasperOps", 1, shown::add, () -> { });
        steps.plugin("A");
        steps.plugin("B");
        assertThat(shown).as("a plugin beyond the count is ignored").containsExactly(new StartupSteps.Step("Loading plugin: A", 3, 5));
        steps.workspace();
        steps.configuration();
        steps.workstation();
        assertThat(shown).extracting(StartupSteps.Step::index).containsExactly(3, 4, 5);
        var none = StartupSteps.of("JasperOps", 0, shown::add, () -> { });
        none.workspace();
        assertThat(shown.getLast()).isEqualTo(new StartupSteps.Step("Restoring workspace…", 3, 4));
    }

    @Test void noStepsDoNothing() {
        assertThat(StartupSteps.NONE.active()).isFalse();
        StartupSteps.NONE.configuration();
        StartupSteps.NONE.plugin("A");
        StartupSteps.NONE.workspace();
        StartupSteps.NONE.workstation();
        StartupSteps.NONE.done();
        assertThat(StartupSteps.NONE.active()).isFalse();
    }

    /** Review Focus 1: a splash that cannot show a step closes, and start-up goes on. */
    @Test void aFailingSplashEndsTheStepsWithoutStoppingStartUp() {
        var closed = new AtomicInteger();
        var steps = StartupSteps.of("JasperOps", 1, step -> { throw new IllegalStateException("cannot paint"); }, closed::incrementAndGet);
        steps.configuration();
        assertThat(steps.active()).isFalse();
        assertThat(closed).hasValue(1);
        steps.plugin("A");
        var throwing = StartupSteps.of("JasperOps", 0, step -> { }, () -> { throw new IllegalStateException("cannot close"); });
        throwing.done();
        assertThat(throwing.active()).isFalse();
    }

    @Test void aSplashNamesItsProductAndCountsPlugins() {
        assertThatThrownBy(() -> StartupSteps.of(" ", 0, step -> { }, () -> { })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StartupSteps.of("JasperOps", -1, step -> { }, () -> { })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StartupSteps.Step("x", 0, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StartupSteps.Step("x", 5, 4)).isInstanceOf(IllegalArgumentException.class);
    }
}
