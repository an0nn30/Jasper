package dev.jasper.app.terminals;

import dev.jasper.terminal.session.AttachedConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SessionAttemptTest {
    private final List<Runnable> cleanup = new ArrayList<>();
    private final List<Runnable> posted = new ArrayList<>();
    private final List<String> seen = new ArrayList<>();
    private final AtomicInteger closes = new AtomicInteger();

    private SessionAttempt attempt() {
        var attempt = new SessionAttempt(UUID.randomUUID(), 120, 40, cleanup::add, posted::add);
        attempt.onStatus = text -> seen.add("status " + text);
        attempt.onAttached = connection -> seen.add("attached");
        attempt.onFailed = message -> seen.add("failed " + message);
        return attempt;
    }

    private AttachedConnection connection() {
        return new AttachedConnection(InputStream.nullInputStream(), OutputStream.nullOutputStream(), (columns, rows) -> { },
            new CompletableFuture<>(), closes::incrementAndGet);
    }

    private void run(List<Runnable> queue) { while (!queue.isEmpty()) queue.remove(0).run(); }

    @Test void statusThenAttachReachThePaneOnTheUiThreadAndTheFirstOutcomeWins() {
        SessionAttempt attempt = attempt();
        assertThat(attempt.columns()).isEqualTo(120);
        assertThat(attempt.rows()).isEqualTo(40);
        attempt.status("Authenticating");
        assertThat(seen).as("posted, not called on the caller's thread").isEmpty();
        run(posted);
        attempt.attach(connection());
        attempt.fail("too late");
        attempt.status("too late");
        attempt.cancel();
        run(posted);
        assertThat(seen).containsExactly("status Authenticating", "attached");
        assertThat(attempt.state()).isEqualTo(SessionAttempt.State.ATTACHED);
        assertThat(attempt.isCancelled()).isFalse();
        assertThat(closes).as("the pane owns it now").hasValue(0);
    }

    @Test void theGuardedConnectionClosesOnceOnTheCleanupWorker() {
        SessionAttempt attempt = attempt();
        List<AttachedConnection> handed = new ArrayList<>();
        attempt.onAttached = handed::add;
        attempt.attach(connection());
        run(posted);
        handed.get(0).close().run();
        handed.get(0).close().run();
        assertThat(closes).as("never on the caller's thread").hasValue(0);
        run(cleanup);
        assertThat(closes).hasValue(1);
    }

    @Test void aConnectionOfferedToAFinishedAttemptIsClosedAtOnce() {
        SessionAttempt cancelled = attempt();
        cancelled.cancel();
        cancelled.attach(connection());
        SessionAttempt failed = attempt();
        failed.fail("refused");
        failed.attach(connection());
        SessionAttempt attached = attempt();
        attached.attach(connection());
        attached.attach(connection());
        run(posted);
        run(cleanup);
        assertThat(closes).as("three rejected connections, each closed once").hasValue(3);
        assertThat(seen).containsExactly("failed refused", "attached");
    }

    @Test void cancellationHandlersRunOnceOnTheCleanupWorkerEvenWhenRegisteredLate() {
        SessionAttempt attempt = attempt();
        attempt.onCancelled(() -> seen.add("first"));
        var withdrawn = attempt.onCancelled(() -> seen.add("withdrawn"));
        withdrawn.close();
        attempt.cancel();
        attempt.cancel();
        assertThat(attempt.isCancelled()).isTrue();
        assertThat(seen).as("not on the caller's thread").isEmpty();
        run(cleanup);
        attempt.onCancelled(() -> seen.add("late"));
        run(cleanup);
        assertThat(seen).containsExactly("first", "late");

        SessionAttempt done = attempt();
        done.attach(connection());
        done.onCancelled(() -> seen.add("never"));
        done.cancel();
        run(cleanup);
        assertThat(seen).doesNotContain("never");
    }
}
