package dev.jasper.remote.edit;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EditChannelsTest {
    final ExecutorService ui = Executors.newSingleThreadExecutor();
    final ExecutorService background = Executors.newCachedThreadPool();
    final List<Optional<WindowHandle>> owners = new CopyOnWriteArrayList<>();
    final List<TestEndpoint> opened = new CopyOnWriteArrayList<>();
    volatile boolean connected = true;
    EditChannels channels = new EditChannels((identity, owner) -> {
        owners.add(owner);
        var endpoint = new TestEndpoint(new LocalEndpoint());
        opened.add(endpoint);
        return CompletableFuture.completedFuture(endpoint);
    }, identity -> connected, background, ui);

    @AfterEach void stop() throws Exception {
        onUi(() -> { channels.close(); return null; });
        ui.shutdownNow(); background.shutdownNow();
    }

    static ConnectionIdentity identity(String name) {
        return new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(
            RemoteHost.create(name, "127.0.0.1", 22, "deploy", Auth.AGENT, "", Optional.empty()), "deploy")));
    }
    <T> T onUi(Callable<T> work) throws Exception { return ui.submit(work).get(10, TimeUnit.SECONDS); }
    <T> CompletableFuture<T> submit(ConnectionIdentity identity, EditChannels.Work<T> work) throws Exception {
        return onUi(() -> channels.submit(identity, Optional::empty, work));
    }

    @Test void oneIdentityRunsOneOperationAtATimeOnOneChannelThenClosesIt() throws Exception {
        var prod = identity("prod");
        var release = new CountDownLatch(1);
        var running = new AtomicInteger();
        var most = new AtomicInteger();
        EditChannels.Work<String> work = endpoint -> {
            most.accumulateAndGet(running.incrementAndGet(), Math::max);
            try { release.await(10, TimeUnit.SECONDS); return "done"; } finally { running.decrementAndGet(); }
        };
        var first = submit(prod, work);
        var second = submit(prod, work);
        var third = submit(prod, work);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (running.get() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
        Thread.sleep(100);
        release.countDown();
        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS), third.get(10, TimeUnit.SECONDS))).containsOnly("done");
        assertThat(most).as("never two at once").hasValue(1);
        assertThat(opened).as("one channel for the three").hasSize(1);
        assertThat(onUi(() -> channels.active(prod))).as("an emptied queue closes its channel").isFalse();
        assertThat(opened.getFirst().wasAborted()).isTrue();
    }

    @Test void twoIdentitiesRunInParallel() throws Exception {
        var both = new CountDownLatch(2);
        EditChannels.Work<Boolean> work = endpoint -> { both.countDown(); return both.await(10, TimeUnit.SECONDS); };
        var prod = submit(identity("prod"), work);
        var staging = submit(identity("staging"), work);
        assertThat(prod.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(staging.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(opened).hasSize(2);
    }

    @Test void theNextOperationAfterAnEmptyQueueOpensAFreshChannel() throws Exception {
        var prod = identity("prod");
        assertThat(submit(prod, endpoint -> "one").get(10, TimeUnit.SECONDS)).isEqualTo("one");
        assertThat(submit(prod, endpoint -> "two").get(10, TimeUnit.SECONDS)).isEqualTo("two");
        assertThat(opened).hasSize(2);
    }

    @Test void cancellingRunningWorkAbortsItsChannelAndTheNextOperationReopens() throws Exception {
        var prod = identity("prod");
        var started = new CountDownLatch(1);
        CompletableFuture<String> reading = submit(prod, endpoint -> {
            started.countDown();
            if (!((TestEndpoint) endpoint).aborted.await(10, TimeUnit.SECONDS)) throw new AssertionError("not aborted");
            throw new IOException("aborted");
        });
        var next = submit(prod, endpoint -> "next");
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        onUi(() -> reading.cancel(true));
        assertThat(next.get(10, TimeUnit.SECONDS)).isEqualTo("next");
        assertThat(opened).hasSize(2);
        assertThat(opened.getFirst().wasAborted()).isTrue();
    }

    @Test void cancellingQueuedWorkWithdrawsIt() throws Exception {
        var prod = identity("prod");
        var release = new CountDownLatch(1);
        var ran = new AtomicBoolean();
        var first = submit(prod, endpoint -> release.await(10, TimeUnit.SECONDS));
        var withdrawn = submit(prod, endpoint -> { ran.set(true); return "withdrawn"; });
        var last = submit(prod, endpoint -> "last");
        onUi(() -> withdrawn.cancel(true));
        release.countDown();
        assertThat(last.get(10, TimeUnit.SECONDS)).isEqualTo("last");
        assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(ran).isFalse();
        assertThat(opened).hasSize(1);
    }

    @Test void reconnectingWithoutAJasperWindowFailsWithTheSignInMessage() throws Exception {
        connected = false;
        var failure = submit(identity("prod-web"), endpoint -> "never");
        assertThatThrownBy(() -> failure.get(10, TimeUnit.SECONDS))
            .hasRootCauseMessage("Reconnecting to prod-web needs an open Jasper window for the sign-in prompt.");
        assertThat(owners).as("nothing was opened").isEmpty();
    }

    @Test void aLiveConnectionNeedsNoWindow() throws Exception {
        assertThat(submit(identity("prod"), endpoint -> "read").get(10, TimeUnit.SECONDS)).isEqualTo("read");
        assertThat(owners).containsExactly(Optional.empty());
    }

    @Test void aWindowOwnsTheSignInWhenReconnecting() throws Exception {
        connected = false;
        var window = new TestWindow(UUID.randomUUID(), new AtomicBoolean(true));
        var read = onUi(() -> channels.submit(identity("prod"), () -> Optional.of(window), endpoint -> "read"));
        assertThat(read.get(10, TimeUnit.SECONDS)).isEqualTo("read");
        assertThat(owners).containsExactly(Optional.of(window));
    }

    @Test void aFailedOpenFailsEveryQueuedOperationOnce() throws Exception {
        onUi(() -> { channels.close(); return null; });
        var opening = new CompletableFuture<FileEndpoint>();
        var asked = new AtomicInteger();
        channels = new EditChannels((identity, owner) -> { asked.incrementAndGet(); return opening; }, identity -> true, background, ui);
        var prod = identity("prod");
        var reads = List.of(submit(prod, endpoint -> "a"), submit(prod, endpoint -> "b"), submit(prod, endpoint -> "c"));
        onUi(() -> opening.completeExceptionally(new IOException("Permission denied (publickey)")));
        for (var read : reads) assertThatThrownBy(() -> read.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("Permission denied (publickey)");
        assertThat(asked).as("one sign-in attempt for the three").hasValue(1);
    }

    @Test void closingFailsQueuedWorkAndAbortsTheChannel() throws Exception {
        var prod = identity("prod");
        var started = new CountDownLatch(1);
        var running = submit(prod, endpoint -> {
            started.countDown();
            ((TestEndpoint) endpoint).aborted.await(10, TimeUnit.SECONDS);
            throw new IOException("aborted");
        });
        var queued = submit(prod, endpoint -> "never");
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        onUi(() -> { channels.close(); return null; });
        assertThatThrownBy(() -> queued.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("Remote has stopped");
        assertThatThrownBy(() -> running.get(10, TimeUnit.SECONDS)).hasMessageContaining("Remote has stopped");
        assertThat(opened.getFirst().wasAborted()).isTrue();
        var late = submit(prod, endpoint -> "late");
        assertThatThrownBy(() -> late.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("Remote has stopped");
    }

    @Test void aJobSubmittedFromAFailureCallbackIsNotLostByTheInFlightFailure() throws Exception {
        var prod = identity("prod");
        var firstOpening = new CompletableFuture<FileEndpoint>();
        var secondEndpoint = new TestEndpoint(new LocalEndpoint());
        var attempts = new AtomicInteger();
        var direct = new EditChannels((identity, owner) -> attempts.incrementAndGet() == 1
            ? firstOpening : CompletableFuture.completedFuture(secondEndpoint), identity -> true, background, Runnable::run);
        var first = direct.submit(prod, Optional::empty, endpoint -> "first");
        var second = direct.submit(prod, Optional::empty, endpoint -> "second");
        var third = new AtomicReference<CompletableFuture<String>>();
        first.whenComplete((value, failure) -> third.set(direct.submit(prod, Optional::empty, endpoint -> "third")));
        firstOpening.completeExceptionally(new IOException("dead"));
        assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("dead");
        assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("dead");
        assertThat(third.get().get(10, TimeUnit.SECONDS)).isEqualTo("third");
        assertThat(attempts).as("one failed attempt for the first two, one fresh attempt for the third").hasValue(2);
        direct.close();
    }
}
