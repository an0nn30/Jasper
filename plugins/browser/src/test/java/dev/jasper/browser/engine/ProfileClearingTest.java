package dev.jasper.browser.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 6 Task 8 and F20: a live Clear Website Data clears the browser cache, then each site's storage, through the
 * helper browser's DevTools, with at most 8 calls in flight, each bounded by 5 seconds. A recording DevTools answers
 * here, on a recording UI thread, as no helper browser can be made without Chromium. The cookies' domains become the
 * https and http origins of their sites.
 */
class ProfileClearingTest {
    private final ConcurrentLinkedQueue<Runnable> ui = new ConcurrentLinkedQueue<>();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<CompletableFuture<String>> answers = new CopyOnWriteArrayList<>();
    private final AtomicInteger inFlight = new AtomicInteger(), mostInFlight = new AtomicInteger();
    // Calls made off the recording UI thread's turns, which must be none.
    private final AtomicBoolean offUi = new AtomicBoolean();
    private volatile boolean onUi;

    @Test void theCacheGoesFirstThenEachOriginWithAtMostEightInFlight() throws Exception {
        List<String> origins = new ArrayList<>();
        for (int each = 0; each < 20; each++) origins.add("https://site" + each + ".example");
        var cleared = ProfileClearing.clearOrigins(this::devTools, origins, ui::add, () -> true, Duration.ofSeconds(5), 8);
        assertThat(calls).as("nothing on the caller's thread").isEmpty();
        runUi();
        assertThat(calls).containsExactly("Network.clearBrowserCache null");
        answerAll();
        assertThat(calls).hasSize(9);
        assertThat(calls.get(1)).isEqualTo("Storage.clearDataForOrigin {\"origin\":\"https://site0.example\",\"storageTypes\":\"all\"}");
        while (!cleared.isDone()) answerAll();
        assertThat(cleared.isCompletedExceptionally()).isFalse();
        assertThat(calls).hasSize(21);
        assertThat(mostInFlight).as("F20: at most 8 in flight").hasValue(8);
        assertThat(offUi).as("every call on the UI thread").isFalse();
    }

    @Test void noOriginStillClearsTheCache() {
        var cleared = ProfileClearing.clearOrigins(this::devTools, List.of(), ui::add, () -> true, Duration.ofSeconds(5), 8);
        answerAll();
        assertThat(cleared).isCompleted();
        assertThat(cleared.isCompletedExceptionally()).isFalse();
        assertThat(calls).containsExactly("Network.clearBrowserCache null");
    }

    @Test void aFailedCallFailsTheClearingAndNoMoreAreMade() {
        var cleared = ProfileClearing.clearOrigins(this::devTools, List.of("https://a.example", "https://b.example", "https://c.example"),
            ui::add, () -> true, Duration.ofSeconds(5), 2);
        answerAll();
        assertThat(calls).hasSize(3);
        answers.get(1).completeExceptionally(new IllegalStateException("DevTools answered with an error"));
        runUi();
        assertThat(cleared).isCompletedExceptionally();
        answers.get(2).complete("{}");
        runUi();
        assertThat(calls).as("c.example is not asked").hasSize(3);
    }

    @Test void aLateAnswerFailsTheClearing() throws Exception {
        var cleared = ProfileClearing.clearOrigins(this::devTools, List.of("https://a.example"), ui::add, () -> true, Duration.ofMillis(50), 8);
        for (long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10); !cleared.isDone() && System.nanoTime() < until; ) {
            runUi();
            Thread.onSpinWait();
        }
        assertThat(cleared).isCompletedExceptionally();
        assertThat(cleared.handle((done, failure) -> failure).join()).isInstanceOf(TimeoutException.class);
    }

    /** Once Chromium stops, or the clearing is given up, no further call is made. */
    @Test void noCallIsMadeOnceChromiumStopsOrTheClearingIsGivenUp() {
        var running = new AtomicBoolean(true);
        var cleared = ProfileClearing.clearOrigins(this::devTools, List.of("https://a.example", "https://b.example"), ui::add, running::get,
            Duration.ofSeconds(5), 1);
        answerAll();
        assertThat(calls).hasSize(2);
        running.set(false);
        answerAll();
        assertThat(calls).as("stopped: b.example is not asked").hasSize(2);
        assertThat(cleared).isCompletedExceptionally();

        calls.clear();
        answers.clear();
        var givenUp = ProfileClearing.clearOrigins(this::devTools, List.of("https://a.example", "https://b.example"), ui::add, () -> true,
            Duration.ofSeconds(5), 1);
        answerAll();
        givenUp.cancel(false);
        answerAll();
        assertThat(calls).hasSize(2);
    }

    /** A DevTools that throws, or answers nothing, fails the clearing. */
    @Test void aDevToolsThatThrowsOrAnswersNothingFailsTheClearing() {
        BiFunction<String, String, CompletableFuture<String>> throwing = (method, parameters) -> { throw new IllegalStateException("closed"); };
        var threw = ProfileClearing.clearOrigins(throwing, List.of(), ui::add, () -> true, Duration.ofSeconds(5), 8);
        runUi();
        assertThat(threw).isCompletedExceptionally();
        var none = ProfileClearing.clearOrigins((method, parameters) -> null, List.of(), ui::add, () -> true, Duration.ofSeconds(5), 8);
        runUi();
        assertThat(none).isCompletedExceptionally();
    }

    @Test void aCookiesDomainIsItsSitesHttpsAndHttpOrigins() {
        assertThat(ProfileClearing.cookieOrigins(List.of(".a.example", "B.Example", "a.example", "localhost", "192.168.1.10", "my_host.local")))
            .containsExactlyInAnyOrder("https://a.example", "http://a.example", "https://b.example", "http://b.example",
                "https://localhost", "http://localhost", "https://192.168.1.10", "http://192.168.1.10", "https://my_host.local",
                "http://my_host.local");
        assertThat(ProfileClearing.cookieOrigins(List.of("", ".", "bad domain", "a..example", "[::1]", "a.example/x", "a\"b.example")))
            .as("none a cookie of a web site's could have").isEmpty();
        assertThat(ProfileClearing.cookieOrigins(Set.of())).isEmpty();
    }

    /** The recording DevTools: each call is noted, and answered later by the test; it counts the calls in flight. */
    private CompletableFuture<String> devTools(String method, String parameters) {
        if (!onUi) offUi.set(true);
        calls.add(method + " " + parameters);
        var answer = new CompletableFuture<String>();
        mostInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
        answer.whenComplete((done, failure) -> inFlight.decrementAndGet());
        answers.add(answer);
        return answer;
    }

    /** Answers every call not yet answered, then runs what the UI thread was handed. */
    private void answerAll() {
        runUi();
        for (CompletableFuture<String> answer : List.copyOf(answers)) answer.complete("{}");
        runUi();
    }

    /** Runs what the recording UI thread was handed, until nothing is left. */
    private void runUi() {
        Runnable task;
        while ((task = ui.poll()) != null) {
            onUi = true;
            try {
                task.run();
            } finally {
                onUi = false;
            }
        }
    }
}
