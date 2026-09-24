package dev.jasper.remote.ui.sftp;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.remote.client.ShellFolder;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DirectoryFollowerTest {
    final List<Runnable> scheduled = new ArrayList<>();
    final List<Duration> delays = new ArrayList<>();
    final Set<UUID> wanted = new HashSet<>();
    final List<String> delivered = new ArrayList<>(), notices = new ArrayList<>();
    final List<CompletableFuture<Optional<String>>> probes = new ArrayList<>();
    final UUID pane = UUID.randomUUID();
    final DirectoryFollower follower = new DirectoryFollower(Runnable::run,
        (delay, task) -> { delays.add(delay); scheduled.add(task); return () -> scheduled.remove(task); },
        wanted::contains, (id, path) -> delivered.add(id + " " + path), (id, text) -> notices.add(id + " " + text));

    Supplier<CompletableFuture<Optional<String>>> probe() {
        return () -> { var future = new CompletableFuture<Optional<String>>(); probes.add(future); return future; };
    }
    void runScheduled() { var tasks = List.copyOf(scheduled); scheduled.clear(); tasks.forEach(Runnable::run); }

    @Test void enterProbesAfterTheDelayAndAFurtherEnterRestartsIt() {
        wanted.add(pane); follower.track(pane, probe());
        follower.enter(pane); follower.enter(pane);
        assertThat(scheduled).hasSize(1);
        assertThat(delays).containsOnly(DirectoryFollower.ENTER_DELAY).first().isEqualTo(Duration.ofMillis(250));
        assertThat(probes).isEmpty();
        runScheduled();
        assertThat(probes).hasSize(1);
        probes.getFirst().complete(Optional.of("/srv/app"));
        assertThat(delivered).containsExactly(pane + " /srv/app");
    }

    @Test void requestProbesAtOnceOnlyForWantedTrackedPanes() {
        follower.track(pane, probe());
        follower.request(pane); follower.enter(pane); runScheduled();
        assertThat(probes).as("not wanted").isEmpty();
        wanted.add(pane);
        follower.request(pane);
        assertThat(probes).hasSize(1);
        UUID untracked = UUID.randomUUID(); wanted.add(untracked);
        follower.request(untracked); follower.enter(untracked);
        assertThat(probes).hasSize(1);
        assertThat(scheduled).isEmpty();
    }

    @Test void coalescesTriggersWhileAProbeRunsAndDeliversOnlyChanges() {
        wanted.add(pane); follower.track(pane, probe());
        follower.request(pane); follower.request(pane); follower.request(pane);
        assertThat(probes).hasSize(1);
        probes.get(0).complete(Optional.of("/a"));
        assertThat(probes).as("one more probe after the first").hasSize(2);
        probes.get(1).complete(Optional.of("/a"));
        assertThat(probes).hasSize(2);
        assertThat(delivered).containsExactly(pane + " /a");
    }

    @Test void dropsEmptyAndRelativeResults() {
        wanted.add(pane); follower.track(pane, probe());
        follower.request(pane); probes.get(0).complete(Optional.empty());
        follower.request(pane); probes.get(1).complete(Optional.of("relative"));
        follower.request(pane); probes.get(2).complete(Optional.of("/b"));
        assertThat(delivered).containsExactly(pane + " /b");
        assertThat(notices).isEmpty();
    }

    @Test void stopsAfterThreeFailuresAndShowsWhyWhenAskedAgain() {
        wanted.add(pane); follower.track(pane, probe());
        for (int i = 0; i < 2; i++) { follower.request(pane); probes.get(i).completeExceptionally(new IOException("Directory probe timed out")); }
        follower.request(pane); probes.get(2).complete(Optional.of("/ok"));
        assertThat(notices).as("a success resets the count").isEmpty();
        for (int i = 3; i < 6; i++) { follower.request(pane); probes.get(i).completeExceptionally(new CompletionException(new IOException("refused"))); }
        assertThat(notices).containsExactly(pane + " " + DirectoryFollower.UNSUPPORTED);
        follower.request(pane); follower.enter(pane); runScheduled();
        assertThat(probes).as("stopped").hasSize(6);
        assertThat(notices).as("shown again when the view asks").hasSize(2);
    }

    @Test void anExplicitRequestAlwaysDeliversEvenTheSamePath() {
        wanted.add(pane); follower.track(pane, probe());
        follower.request(pane); probes.get(0).complete(Optional.of("/a"));
        assertThat(delivered).containsExactly(pane + " /a");
        follower.request(pane); probes.get(1).complete(Optional.of("/a"));
        assertThat(delivered).as("an explicit request always delivers, even the same path")
            .containsExactly(pane + " /a", pane + " /a");
        follower.enter(pane); runScheduled(); probes.get(2).complete(Optional.of("/a"));
        assertThat(delivered).as("an Enter-triggered result that is unchanged is still not delivered twice")
            .containsExactly(pane + " /a", pane + " /a");
    }

    @Test void unsupportedStopsAtOnce() {
        wanted.add(pane); follower.track(pane, probe());
        follower.request(pane);
        probes.getFirst().completeExceptionally(new CompletionException(new ShellFolder.Unsupported()));
        assertThat(notices).containsExactly(pane + " " + DirectoryFollower.UNSUPPORTED);
        follower.request(pane);
        assertThat(probes).hasSize(1);
    }

    @Test void anOsc7ReportEndsProbingForThatPane() {
        wanted.add(pane); follower.track(pane, probe());
        follower.enter(pane);
        follower.reported(pane);
        assertThat(scheduled).as("the pending delay is cancelled").isEmpty();
        follower.track(pane, probe());
        follower.request(pane);
        assertThat(probes).isEmpty();
    }

    @Test void forgetIgnoresALateResultAndCancelsTheDelay() {
        wanted.add(pane); follower.track(pane, probe());
        follower.request(pane);
        follower.enter(pane);
        follower.forget(pane);
        assertThat(scheduled).isEmpty();
        probes.getFirst().complete(Optional.of("/late"));
        assertThat(delivered).isEmpty();
    }

    @Test void closeCancelsDelaysAndStopsTracking() {
        wanted.add(pane); follower.track(pane, probe());
        follower.enter(pane);
        follower.close();
        assertThat(scheduled).isEmpty();
        follower.request(pane);
        assertThat(probes).isEmpty();
    }
}
