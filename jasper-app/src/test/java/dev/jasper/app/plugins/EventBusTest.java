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
