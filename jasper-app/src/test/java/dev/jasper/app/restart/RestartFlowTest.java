package dev.jasper.app.restart;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RestartFlowTest {
    private final List<RestartMode> restarts = new ArrayList<>();
    private final List<RestartFlow.State> states = new ArrayList<>();
    private boolean restartable = true;

    /** Everything inline: the worker and the UI are the calling thread. */
    private RestartFlow flow(ResidentControl control, Duration wait) {
        var flow = new RestartFlow(control, mode -> { restarts.add(mode); return restartable; }, Runnable::run, Runnable::run,
            wait, Duration.ofMillis(1));
        flow.onChanged(() -> states.add(flow.state()));
        return flow;
    }

    @Test void restartNowKeepsTheLaunchAndAnUnknownCommandLineIsSaidSo() {
        RestartFlow flow = flow(ResidentControl.NONE, Duration.ofMillis(50));
        flow.restartNow();
        assertThat(restarts).containsExactly(RestartMode.SAME);
        assertThat(flow.state()).isEqualTo(RestartFlow.State.IDLE);
        restartable = false;
        flow.restartNow();
        assertThat(flow.state()).isEqualTo(RestartFlow.State.UNAVAILABLE);
    }

    @Test void withNoResidentRestartingNormallyJustRestarts() {
        RestartFlow flow = flow(new ResidentControl(() -> false, () -> { throw new AssertionError("nobody to retire"); }), Duration.ofMillis(50));
        flow.restartNormally();
        assertThat(restarts).containsExactly(RestartMode.NORMAL);
        assertThat(states).containsExactly(RestartFlow.State.PROBING, RestartFlow.State.IDLE);
    }

    @Test void aResidentIsAskedToQuitAndTheRestartWaitsForItsEndpoint() {
        var answers = new AtomicInteger();
        var retired = new AtomicBoolean();
        // Live when probed, still live twice while it shuts down, then gone.
        RestartFlow flow = flow(new ResidentControl(() -> answers.incrementAndGet() <= 3, () -> { retired.set(true); return true; }),
            Duration.ofSeconds(5));
        flow.restartNormally();
        assertThat(flow.state()).isEqualTo(RestartFlow.State.RESIDENT_FOUND);
        assertThat(restarts).as("never while the resident holds the endpoint").isEmpty();
        flow.askResidentToQuit();
        assertThat(retired).isTrue();
        assertThat(restarts).containsExactly(RestartMode.NORMAL);
        assertThat(states).containsExactly(RestartFlow.State.PROBING, RestartFlow.State.RESIDENT_FOUND, RestartFlow.State.WAITING,
            RestartFlow.State.IDLE);
    }

    @Test void aResidentThatRefusesOrNeverExitsLeavesOnlyAStandaloneLaunch() {
        RestartFlow refused = flow(new ResidentControl(() -> true, () -> false), Duration.ofSeconds(5));
        refused.restartNormally();
        refused.askResidentToQuit();
        assertThat(refused.state()).isEqualTo(RestartFlow.State.RESIDENT_STUCK);
        assertThat(restarts).isEmpty();
        refused.launchAnyway();
        assertThat(restarts).containsExactly(RestartMode.STANDALONE);

        restarts.clear();
        RestartFlow wedged = flow(new ResidentControl(() -> true, () -> true), Duration.ofMillis(30));
        wedged.restartNormally();
        wedged.askResidentToQuit();
        assertThat(wedged.state()).as("the wait elapsed").isEqualTo(RestartFlow.State.RESIDENT_STUCK);
        assertThat(restarts).isEmpty();
        wedged.cancel();
        assertThat(wedged.state()).isEqualTo(RestartFlow.State.IDLE);
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out");
            Thread.sleep(1);
        }
    }

    @Test void cancellingTheWaitStopsItAndLaunchAnywayWorksWhileWaiting() throws Exception {
        var waiting = new CountDownLatch(1);
        var restarted = new CountDownLatch(1);
        var flow = new RestartFlow(new ResidentControl(() -> { waiting.countDown(); return true; }, () -> true),
            mode -> { synchronized (restarts) { restarts.add(mode); } restarted.countDown(); return true; },
            work -> Thread.ofPlatform().daemon().start(work), Runnable::run, Duration.ofSeconds(30), Duration.ofMillis(1));
        flow.restartNormally();
        assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
        await(() -> flow.state() == RestartFlow.State.RESIDENT_FOUND);
        flow.askResidentToQuit();
        await(() -> flow.state() == RestartFlow.State.WAITING);
        flow.launchAnyway();
        assertThat(restarted.await(5, TimeUnit.SECONDS)).isTrue();
        synchronized (restarts) { assertThat(restarts).containsExactly(RestartMode.STANDALONE); }
        Thread.sleep(50);
        synchronized (restarts) { assertThat(restarts).as("the abandoned wait restarts nothing").hasSize(1); }
    }
}
