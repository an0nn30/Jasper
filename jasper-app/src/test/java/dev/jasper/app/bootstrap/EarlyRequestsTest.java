package dev.jasper.app.bootstrap;

import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Spec 5.3: requests that reach an owner before it is ready run in order once it is. */
@Timeout(30)
class EarlyRequestsTest {
    private final ConcurrentLinkedQueue<String> ran = new ConcurrentLinkedQueue<>();

    @Test void heldRequestsRunInArrivalOrderOnceOpenedAndLaterOnesAtOnce() throws Exception {
        var requests = new EarlyRequests();
        requests.submit(() -> ran.add("first"));
        requests.submit(() -> ran.add("second"));
        Thread.sleep(100);
        assertThat(ran).as("held until the application is ready").isEmpty();
        requests.open();
        until(() -> !requests.holding());
        assertThat(ran).containsExactly("first", "second");
        String caller = Thread.currentThread().getName();
        requests.submit(() -> ran.add("later on " + Thread.currentThread().getName()));
        assertThat(ran).as("run at once, on the submitting thread").containsExactly("first", "second", "later on " + caller);
    }

    @Test void aRequestThatArrivesWhileTheHeldOnesRunWaitsItsTurn() throws Exception {
        var requests = new EarlyRequests();
        var release = new CountDownLatch(1);
        requests.submit(() -> {
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            ran.add("held");
        });
        requests.open();
        Thread.sleep(100);
        requests.submit(() -> ran.add("arrived while the held one ran"));
        release.countDown();
        until(() -> ran.size() == 2);
        assertThat(ran).containsExactly("held", "arrived while the held one ran");
    }

    @Test void aFailingRequestDoesNotStopTheOthersAndOpeningTwiceRunsEachOnce() throws Exception {
        var requests = new EarlyRequests();
        requests.submit(() -> { throw new IllegalStateException("a broken request"); });
        requests.submit(() -> ran.add("after the failure"));
        requests.open();
        requests.open();
        until(() -> !requests.holding());
        assertThat(ran).containsExactly("after the failure");
    }

    @Test void eachRequestSaysWhatBecameOfItAndOnlyAnIdleQueueCanBeDeclined() throws Exception {
        var requests = new EarlyRequests();
        assertThat(requests.submit(() -> ran.add("held"), () -> ran.add("not run"))).isEqualTo(EarlyRequests.Outcome.HELD);
        assertThat(requests.declineIfIdle()).as("a request is held").isFalse();
        requests.open();
        until(() -> !requests.holding());
        assertThat(requests.submit(() -> ran.add("not run"), () -> ran.add("now"))).isEqualTo(EarlyRequests.Outcome.RAN);
        assertThat(ran).containsExactly("held", "now");
        assertThat(requests.declineIfIdle()).as("opened").isFalse();

        var idle = new EarlyRequests();
        assertThat(idle.declineIfIdle()).isTrue();
        assertThat(idle.submit(() -> ran.add("too late"))).isEqualTo(EarlyRequests.Outcome.DECLINED);
        assertThat(idle.holding()).isFalse();
        idle.open();
        Thread.sleep(100);
        assertThat(ran).as("a declined queue runs nothing").containsExactly("held", "now");
    }
}
