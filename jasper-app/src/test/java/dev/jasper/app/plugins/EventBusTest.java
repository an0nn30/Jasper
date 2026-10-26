package dev.jasper.app.plugins;

import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.events.Topic;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EventBusTest {
    record Ping(int n) { }
    private static final Topic<Ping> PING = Topic.of("test.alpha.ping", Ping.class);

    private final Deque<Runnable> ui = new ArrayDeque<>();
    private final Containment containment = new Containment(() -> true);
    private final EventBus bus = new EventBus(ui::add, containment);

    private void drain() { while (!ui.isEmpty()) ui.poll().run(); }

    @Test void phaseAndEndCannotOvertakeAReportStillBeingEnqueued() throws Exception {
        var queued = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var block = new java.util.concurrent.atomic.AtomicBoolean();
        var ordered = new EventBus(callback -> {
            if (block.compareAndSet(true, false)) {
                entered.countDown();
                try { assertThat(release.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
            }
            queued.add(callback);
        }, containment);
        var seen = new ArrayList<ActivityEvent>();
        ordered.subscribe(EventBus.APP, Activities.TOPIC, seen::add);
        var hub = new ActivityHub(ordered);
        var task = hub.begin("test.alpha", ActivitySpec.of("work"));
        block.set(true);
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var reporting = new Thread(() -> { try { task.progress(.4, "old"); } catch (Throwable t) { failure.set(t); } });
        var ending = new Thread(() -> { task.needsInput("input"); task.resume("resume"); task.succeed("done"); });
        reporting.start();
        try {
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            ending.start();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (ending.getState() != Thread.State.BLOCKED && ending.isAlive() && System.nanoTime() < deadline) Thread.onSpinWait();
            assertThat(ending.getState()).isEqualTo(Thread.State.BLOCKED);
        } finally { release.countDown(); reporting.join(5000); ending.join(5000); }
        assertThat(failure.get()).isNull();
        assertThat(reporting.isAlive() || ending.isAlive()).isFalse();
        while (!queued.isEmpty()) queued.remove().run();
        assertThat(seen).extracting(ActivityEvent::state).containsExactly(ActivityEvent.State.STARTED,
            ActivityEvent.State.NEEDS_INPUT, ActivityEvent.State.RESUMED, ActivityEvent.State.SUCCEEDED);
        assertThat(seen.getLast().fraction()).hasValue(.4);
        assertThat(hub.current()).isEmpty();
    }

    @Test void pendingCountsQueuedDeliveriesUntilTheyRun() {
        bus.publish("test.alpha", PING, new Ping(1));
        bus.publish("test.alpha", PING, new Ping(2));
        assertThat(bus.pending()).isEqualTo(2);
        drain();
        assertThat(bus.pending()).isZero();
    }

    @Test void rejectsNullAndMistypedPayloads() {
        assertThatNullPointerException().isThrownBy(() -> bus.publish("test.alpha", PING, null));
        @SuppressWarnings({"unchecked", "rawtypes"})
        Topic<Object> raw = (Topic) PING;
        assertThatIllegalArgumentException().isThrownBy(() -> bus.publish("test.alpha", raw, "not a ping"));
        assertThat(bus.pending()).isZero();
    }

    @Test void removeAllDropsOnePluginsSubscriptions() {
        List<String> seen = new ArrayList<>();
        bus.subscribe("test.alpha", PING, ping -> seen.add("alpha"));
        bus.subscribe("test.beta", PING, ping -> seen.add("beta"));
        bus.removeAll("test.alpha");
        bus.publish("test.alpha", PING, new Ping(1));
        drain();
        assertThat(seen).containsExactly("beta");
    }

    @Test void containmentCountsFailuresAndNamesTheExecutingCallback() {
        List<String> executing = new ArrayList<>();
        bus.subscribe("test.alpha", PING, ping -> { executing.add(containment.executing()); throw new IllegalStateException("x"); });
        bus.publish("test.alpha", PING, new Ping(1));
        drain();
        assertThat(executing).singleElement().asString().contains("test.alpha", "test.alpha.ping");
        assertThat(containment.executing()).isNull();
        assertThat(containment.failures("test.alpha")).isEqualTo(1);
        assertThat(containment.attempt("test.beta", "start", () -> { throw new java.io.IOException("checked"); }))
            .isInstanceOf(java.io.IOException.class);
        assertThat(containment.attempt("test.beta", "start", () -> null)).isNull();
        assertThatThrownBy(() -> containment.attempt("test.beta", "start", () -> { throw new AssertionError("not contained"); }))
            .isInstanceOf(AssertionError.class);
    }

    @Test void failAllEndsOnlyThatPluginsActivities() {
        var hub = new ActivityHub(bus);
        List<ActivityEvent> log = new ArrayList<>();
        bus.subscribe(EventBus.APP, Activities.TOPIC, log::add);
        hub.begin("test.alpha", ActivitySpec.of("A"));
        var beta = hub.begin("test.beta", ActivitySpec.of("B"));
        hub.failAll("test.alpha", "Plugin stopped");
        drain();
        assertThat(log).extracting(event -> event.title() + ":" + event.state())
            .containsExactly("A:STARTED", "B:STARTED", "A:FAILED");
        assertThat(log.get(2).detail()).isEqualTo("Plugin stopped");
        assertThat(hub.current()).singleElement().satisfies(event -> assertThat(event.id()).isEqualTo(beta.id()));
    }
}
