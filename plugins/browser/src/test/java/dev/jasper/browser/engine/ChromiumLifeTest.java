package dev.jasper.browser.engine;

import dev.jasper.browser.engine.ChromiumLife.Step;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every order in which Chromium's start, initialization, client, timeout and close can meet, driven through the life's
 * transitions directly: each ends with at most one dispose, and none when the start never reached JCEF. The steps that
 * touch futures are carried out as Chromium carries them out; the rest are counted.
 */
class ChromiumLifeTest {
    private final ChromiumLife<String> life = new ChromiumLife<>(new StartOnce());
    private final List<Throwable> told = new java.util.ArrayList<>();
    private int starts, creates, disposes, ends, awaits;

    @Test void closeBeforeTheWorkerRunsEndsAtOnceAndKeepsTheStartOutOfJcef() {
        var client = ask();
        assertThat(starts).isEqualTo(1);
        apply(life.close());
        assertThat(life.enter()).as("the worker may not call JCEF").isFalse();
        assertThat(failure(client)).hasMessage(Chromium.ONCE);
        assertThat(disposes).isZero();
        assertThat(ends).isEqualTo(1);
        assertThat(awaits).as("nothing to wait for").isZero();
        assertThat(told).isEmpty();
        assertThat(life.state()).isEqualTo(Chromium.State.STOPPED);
    }

    @Test void closeDuringTheStartThenTheStartThrowsEndsWithoutADispose() {
        var client = ask();
        assertThat(life.enter()).isTrue();
        apply(life.close());
        assertThat(awaits).isEqualTo(1);
        assertThat(ends).isZero();
        apply(life.threw(new UnsatisfiedLinkError("no Chromium")));
        assertThat(ends).isEqualTo(1);
        assertThat(disposes).isZero();
        assertThat(failure(client)).hasMessage(Chromium.ONCE);
        assertThat(told).as("the close cut the start short").isEmpty();
        assertThat(life.state()).isEqualTo(Chromium.State.STOPPED);
    }

    @Test void closeBeforeInitializationDisposesOnceWhenInitializationArrives() {
        var client = ask();
        life.enter();
        apply(life.close());
        assertThat(disposes).isZero();
        apply(life.initialized());
        assertThat(disposes).isEqualTo(1);
        assertThat(creates).as("no client after a close").isZero();
        assertThat(life.mayCreateClient()).isFalse();
        apply(life.terminated());
        assertThat(ends).isEqualTo(1);
        assertThat(failure(client)).hasMessage(Chromium.ONCE);
        assertThat(life.state()).isEqualTo(Chromium.State.STOPPED);
    }

    @Test void initializationBeforeCloseRunsChromiumAndTheCloseDisposesOnce() {
        var client = ask();
        life.enter();
        apply(life.initialized());
        assertThat(creates).isEqualTo(1);
        assertThat(life.mayCreateClient()).isTrue();
        apply(life.clientCreated("client"));
        assertThat(client).isCompletedWithValue("client");
        assertThat(life.state()).isEqualTo(Chromium.State.RUNNING);
        apply(life.close());
        assertThat(disposes).isEqualTo(1);
        assertThat(awaits).isEqualTo(1);
        assertThat(client).as("the client handed out stays handed out").isCompletedWithValue("client");
        assertThat(told).isEmpty();
        assertThat(life.state()).isEqualTo(Chromium.State.STOPPED);
    }

    @Test void closeWhileTheClientIsMadeDisposesOnceAndHandsOutNoClient() {
        var client = ask();
        life.enter();
        apply(life.initialized());
        assertThat(life.mayCreateClient()).isTrue();
        apply(life.close());
        assertThat(disposes).isEqualTo(1);
        assertThat(life.clientCreated("late")).as("the dispose takes the late client with it").isEmpty();
        assertThat(failure(client)).hasMessage(Chromium.ONCE);
        assertThat(disposes).isEqualTo(1);
        assertThat(life.state()).isEqualTo(Chromium.State.STOPPED);
    }

    @Test void aTimeoutThenALateInitializationMakesNoClientAndTheCloseDisposesOnce() {
        var client = ask();
        life.enter();
        var slow = new TimeoutException(Chromium.SLOW);
        apply(life.timedOut(slow));
        assertThat(failure(client)).isSameAs(slow);
        assertThat(told).containsExactly(slow);
        assertThat(life.state()).isEqualTo(Chromium.State.FAILED);
        apply(life.initialized());
        assertThat(creates).isZero();
        assertThat(life.mayCreateClient()).isFalse();
        apply(life.close());
        assertThat(disposes).isEqualTo(1);
        assertThat(told).hasSize(1);
        assertThat(life.state()).isEqualTo(Chromium.State.STOPPED);
    }

    @Test void aTimeoutThenACloseThenALateInitializationDisposesOnce() {
        ask();
        life.enter();
        apply(life.timedOut(new TimeoutException(Chromium.SLOW)));
        apply(life.close());
        assertThat(disposes).as("not initialized yet").isZero();
        assertThat(awaits).isEqualTo(1);
        apply(life.initialized());
        assertThat(disposes).isEqualTo(1);
        assertThat(creates).isZero();
        assertThat(told).hasSize(1);
        assertThat(life.state()).isEqualTo(Chromium.State.STOPPED);
    }

    @Test void aSecondCloseAnswersNothing() {
        assertThat(apply(life.close())).as("never started").hasSize(1).allMatch(Step.End.class::isInstance);
        assertThat(life.close()).isEmpty();
        var running = new ChromiumLife<String>(new StartOnce());
        running.ask();
        running.enter();
        running.initialized();
        running.clientCreated("client");
        assertThat(apply(running.close())).anyMatch(Step.Dispose.class::isInstance);
        assertThat(running.close()).isEmpty();
        assertThat(disposes).isEqualTo(1);
        assertThat(running.state()).isEqualTo(Chromium.State.STOPPED);
    }

    @Test void aTimeoutAfterInitializationDoesNothing() {
        ask();
        life.enter();
        apply(life.initialized());
        assertThat(life.timedOut(new TimeoutException(Chromium.SLOW))).isEmpty();
        assertThat(life.state()).isEqualTo(Chromium.State.STARTING);
        assertThat(life.mayCreateClient()).isTrue();
    }

    @Test void aClientThatCannotBeMadeFailsTheStartAndIsToldOnce() {
        var client = ask();
        life.enter();
        apply(life.initialized());
        var broken = new LinkageError("no client");
        apply(life.failed(broken));
        assertThat(failure(client)).isSameAs(broken);
        assertThat(told).containsExactly(broken);
        assertThat(life.state()).isEqualTo(Chromium.State.FAILED);
        assertThat(life.failed(new IllegalStateException("again"))).as("a failed start is not failed again").isEmpty();
        apply(life.close());
        assertThat(disposes).isEqualTo(1);
        assertThat(told).hasSize(1);
        assertThat(life.state()).isEqualTo(Chromium.State.STOPPED);
    }

    @Test void aStartThatThrowsFailsAndTheCloseHasNothingToDispose() {
        var client = ask();
        life.enter();
        var missing = new UnsatisfiedLinkError("no Chromium");
        apply(life.threw(missing));
        assertThat(failure(client)).isSameAs(missing);
        assertThat(told).containsExactly(missing);
        apply(life.close());
        assertThat(disposes).isZero();
        assertThat(ends).isEqualTo(1);
        assertThat(awaits).isZero();
        assertThat(life.state()).isEqualTo(Chromium.State.STOPPED);
    }

    private CompletableFuture<String> ask() {
        var asked = life.ask();
        apply(asked.steps());
        return asked.client();
    }

    /** Carries out the steps that touch futures, as Chromium does, and counts the rest. */
    private List<Step<String>> apply(List<Step<String>> steps) {
        for (Step<String> step : steps) {
            switch (step) {
                case Step.Start<String> start -> starts++;
                case Step.CreateClient<String> create -> creates++;
                case Step.Complete<String> done -> done.client().complete(done.value());
                case Step.Fail<String> fail -> fail.client().completeExceptionally(fail.cause());
                case Step.Tell<String> tell -> told.add(tell.cause());
                case Step.Dispose<String> dispose -> disposes++;
                case Step.End<String> end -> ends++;
                case Step.Await<String> await -> awaits++;
            }
        }
        return steps;
    }

    private static Throwable failure(CompletableFuture<?> future) {
        assertThat(future).isCompletedExceptionally();
        return future.handle((value, thrown) -> thrown).join();
    }
}
