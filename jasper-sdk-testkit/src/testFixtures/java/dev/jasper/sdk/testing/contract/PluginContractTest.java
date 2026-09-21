package dev.jasper.sdk.testing.contract;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.services.ServiceUnavailableException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static dev.jasper.sdk.activity.ActivityEvent.State.FAILED;
import static dev.jasper.sdk.activity.ActivityEvent.State.PROGRESS;
import static dev.jasper.sdk.activity.ActivityEvent.State.STARTED;
import static dev.jasper.sdk.activity.ActivityEvent.State.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runtime semantics every implementation of the SDK must share. Test bodies run off the UI thread. */
public abstract class PluginContractTest {
    /** A service interface owned by this package, which harnesses register as the provider's export. */
    public interface Greeter { String greet(String name); }

    /** A plugin-owned payload. */
    public record Ping(int n) { }

    static final Topic<Ping> PING = Topic.of("test.alpha.ping", Ping.class);

    private ContractHarness h;

    /** A fresh runtime; the suite closes it after each test. */
    protected abstract ContractHarness newHarness();

    @BeforeEach void open() { h = newHarness(); }
    @AfterEach void close() { h.close(); }

    private static PluginInfo info(String id) { return new PluginInfo(id, id, "1.0.0", Set.of()); }

    private PluginContext started(String id, Set<String> requires) {
        var context = new AtomicReference<PluginContext>();
        h.start(info(id), requires, Set.of(), context::set);
        assertThat(h.active(id)).as("%s started", id).isTrue();
        return context.get();
    }

    @Test void deliveryIsQueuedOrderedAndNeverReentrant() {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.events().subscribe(PING, ping -> {
                seen.add("in:" + ping.n());
                if (ping.n() == 1) context.events().publish(PING, new Ping(3));
                seen.add("out:" + ping.n());
            });
        });
        h.ui(() -> {
            alpha.get().events().publish(PING, new Ping(1));
            alpha.get().events().publish(PING, new Ping(2));
            seen.add("published");
        });
        h.flush();
        assertThat(seen).containsExactly("published", "in:1", "out:1", "in:2", "out:2", "in:3", "out:3");
    }

    @Test void onlyTheOwnerPublishesAndPayloadTypesCannotConflict() {
        PluginContext alpha = started("test.alpha", Set.of());
        PluginContext beta = started("test.beta", Set.of());
        assertThatIllegalArgumentException().isThrownBy(() -> beta.events().publish(PING, new Ping(1)));
        assertThatIllegalArgumentException().isThrownBy(() ->
            alpha.events().publish(AppEvents.CONFIG_RELOADED, new AppEvents.ConfigReloaded()));
        assertThatIllegalArgumentException().isThrownBy(() -> alpha.events().publish(Activities.TOPIC,
            new ActivityEvent(UUID.randomUUID(), "test.alpha", "forged", STARTED, OptionalDouble.empty(), "", Optional.empty())));
        assertThatIllegalArgumentException().as("a longer id sharing the prefix is another namespace").isThrownBy(() ->
            alpha.events().publish(Topic.of("test.alphabet.x", Ping.class), new Ping(1)));
        h.ui(() -> {
            beta.events().subscribe(PING, ping -> { });
            assertThatIllegalArgumentException().isThrownBy(() ->
                beta.events().subscribe(Topic.of("test.alpha.ping", String.class), text -> { }));
        });
    }

    @Test void closedSubscriptionsReceiveNothingAndCloseIsIdempotentFromAnyThread() {
        List<Integer> first = Collections.synchronizedList(new ArrayList<>());
        List<Integer> second = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        var subscriptions = new ArrayList<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            subscriptions.add(context.events().subscribe(PING, ping -> first.add(ping.n())));
            subscriptions.add(context.events().subscribe(PING, ping -> second.add(ping.n())));
        });
        alpha.get().events().publish(PING, new Ping(1));
        h.flush();
        h.ui(() -> { subscriptions.get(0).close(); subscriptions.get(0).close(); });
        subscriptions.get(1).close();
        subscriptions.get(1).close();
        alpha.get().events().publish(PING, new Ping(2));
        h.flush();
        assertThat(first).containsExactly(1);
        assertThat(second).containsExactly(1);
    }

    @Test void aFailingHandlerDoesNotStopOtherSubscribers() {
        List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.events().subscribe(PING, ping -> { throw new IllegalStateException("handler failure"); });
            context.events().subscribe(PING, ping -> seen.add(ping.n()));
        });
        alpha.get().events().publish(PING, new Ping(7));
        h.flush();
        assertThat(seen).containsExactly(7);
    }

    @Test void activitiesAreStampedCoalescedAndEndExactlyOnce() {
        PluginContext alpha = started("test.alpha", Set.of());
        var handle = new AtomicReference<ActivityHandle>();
        h.ui(() -> {
            ActivityHandle activity = alpha.activities().begin(
                new ActivitySpec("Upload", "starting", Optional.empty(), Optional.empty()));
            handle.set(activity);
            activity.progress(0.1, "a");
            activity.progress(0.2, "b");
            activity.progress(0.9, "almost");
            assertThatIllegalArgumentException().isThrownBy(() -> activity.progress(1.5, "too far"));
        });
        assertThat(alpha.activities().current()).singleElement()
            .satisfies(event -> assertThat(event.detail()).isEqualTo("almost"));
        h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED, PROGRESS);
        ActivityEvent progress = h.activityLog().get(1);
        assertThat(progress.id()).isEqualTo(handle.get().id());
        assertThat(progress.sourcePluginId()).isEqualTo("test.alpha");
        assertThat(progress.title()).isEqualTo("Upload");
        assertThat(progress.fraction()).hasValue(0.9);

        handle.get().detail("still going");
        h.flush();
        assertThat(h.activityLog().get(2).fraction()).hasValue(0.9);
        assertThat(h.activityLog().get(2).detail()).isEqualTo("still going");

        h.ui(() -> {
            handle.get().progress(1.0, "dropped because the end arrives first");
            handle.get().succeed("done");
            handle.get().fail("ignored");
        });
        h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state)
            .containsExactly(STARTED, PROGRESS, PROGRESS, SUCCEEDED);
        assertThat(h.activityLog().get(3).detail()).isEqualTo("done");
        assertThat(alpha.activities().current()).isEmpty();
    }

    @Test void activitiesLeftOpenFailWhenTheirPluginStops() {
        PluginContext alpha = started("test.alpha", Set.of());
        alpha.activities().begin(ActivitySpec.of("Sync"));
        h.flush();
        h.stopAll();
        h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED, FAILED);
        assertThat(h.active("test.alpha")).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> alpha.activities().begin(ActivitySpec.of("late")));
    }

    @Test void servicesCommitAfterStartAndAreBuiltOncePerRequirer() {
        List<String> built = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.provider"), Set.of(), Set.of(), context -> {
            context.services().publishPerConsumer(Greeter.class, consumer -> {
                built.add(consumer.id());
                return name -> "hi " + name + " from " + consumer.id();
            });
            assertThat(context.services().find(Greeter.class)).as("not visible before commit").isEmpty();
        });
        var greeting = new AtomicReference<String>();
        h.start(info("test.consumer"), Set.of("test.provider"), Set.of(), context -> {
            greeting.set(context.services().require(Greeter.class).greet("x"));
            assertThat(context.services().require(Greeter.class))
                .isSameAs(context.services().find(Greeter.class).orElseThrow());
        });
        assertThat(greeting).hasValue("hi x from test.consumer");
        assertThat(built).containsExactly("test.consumer");

        PluginContext stranger = started("test.stranger", Set.of());
        assertThat(stranger.services().find(Greeter.class)).as("no requires, no service").isEmpty();
        assertThatThrownBy(() -> stranger.services().require(Greeter.class))
            .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test void publicationIsLimitedToStartAndToInterfacesPublishedOnce() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            assertThatIllegalArgumentException().isThrownBy(() -> context.services().publish(String.class, "text"));
            context.services().publish(Greeter.class, name -> name);
            assertThatIllegalArgumentException().isThrownBy(() -> context.services().publish(Greeter.class, name -> name));
        });
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.late"), Set.of(), Set.of(), alpha::set);
        h.ui(() -> assertThatIllegalStateException().isThrownBy(() ->
            alpha.get().services().publish(Runnable.class, () -> { })));
    }

    @Test void aFailedStartRollsEverythingBackAndClosesTheContext() {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        var failed = new AtomicReference<PluginContext>();
        h.start(info("test.provider"), Set.of(), Set.of(), context -> {
            failed.set(context);
            context.events().subscribe(AppEvents.CONFIG_RELOADED, event -> seen.add("handler ran"));
            context.services().publish(Greeter.class, name -> name);
            context.activities().begin(ActivitySpec.of("Doomed"));
            throw new IllegalStateException("start failure");
        });
        assertThat(h.active("test.provider")).isFalse();

        var hardStarted = new AtomicBoolean();
        h.start(info("test.consumer"), Set.of("test.provider"), Set.of(), context -> hardStarted.set(true));
        assertThat(hardStarted).as("hard dependents are skipped").isFalse();
        assertThat(h.active("test.consumer")).isFalse();

        var optional = new AtomicReference<Optional<Greeter>>();
        h.start(info("test.optional"), Set.of(), Set.of("test.provider"),
            context -> optional.set(context.services().find(Greeter.class)));
        assertThat(h.active("test.optional")).isTrue();
        assertThat(optional.get()).as("the publication was discarded").isEmpty();

        h.publishApp(AppEvents.CONFIG_RELOADED, new AppEvents.ConfigReloaded());
        h.flush();
        assertThat(seen).isEmpty();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED, FAILED);

        PluginContext closed = failed.get();
        h.ui(() -> assertThatIllegalStateException().isThrownBy(() -> closed.events().subscribe(PING, ping -> { })));
        assertThatIllegalStateException().isThrownBy(() -> closed.activities().begin(ActivitySpec.of("late")));
        assertThatThrownBy(() -> closed.background().execute(() -> { })).isInstanceOf(RejectedExecutionException.class);
        assertThat(closed.log()).isNotNull();
    }
}
