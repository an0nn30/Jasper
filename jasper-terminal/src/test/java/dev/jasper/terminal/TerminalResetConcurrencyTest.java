package dev.jasper.terminal;

import com.jediterm.terminal.CursorShape;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalResetConcurrencyTest {
    @Test
    void snapshotWaitsForTheWholeRisIncludingCursorHome() throws Exception {
        FakeConnector connector = new FakeConnector();
        try (TerminalSession session = new TerminalSession(connector, 20, 4, 100)) {
            session.startReading();
            connector.feed("before\033[3;8H\033[6 q\033]0;ready\007");
            Await.until(() -> session.title().equals("ready"), "initial cursor and text");
            assertThat(session.snapshot().cursorShape()).isEqualTo(new CursorRequest(CursorStyle.BEAM, false));
            CountDownLatch clearing = new CountDownLatch(1);
            CountDownLatch resume = new CountDownLatch(1);
            session.addListener(new TerminalSession.Listener() {
                @Override public void scrollbackReset() {
                    clearing.countDown();
                    try {
                        if (!resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("RIS was not released");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }
            });
            CompletableFuture<ScreenSnapshot> captured = new CompletableFuture<>();
            Thread reader = Thread.ofPlatform().daemon().unstarted(() -> {
                try { captured.complete(session.snapshot()); }
                catch (Throwable failure) { captured.completeExceptionally(failure); }
            });
            try {
                connector.feed("\033c\033]0;reset\007");
                assertThat(clearing.await(5, TimeUnit.SECONDS)).isTrue();
                reader.start();
                Await.until(() -> captured.isDone() || reader.getState() == Thread.State.WAITING,
                    "snapshot completes or waits for the reset lock");
                assertThat(captured.isDone()).as("snapshot must not expose a partially reset buffer").isFalse();
            } finally {
                resume.countDown();
                reader.join(5000);
            }
            ScreenSnapshot snapshot = captured.get(5, TimeUnit.SECONDS);
            assertThat(snapshot.lineText(0)).isEmpty();
            assertThat(snapshot.cursorColumn()).isZero();
            assertThat(snapshot.cursorRow()).isZero();
            assertThat(snapshot.cursorShape()).isNull();
            assertThat(snapshot.historyLines()).isZero();
            Await.until(() -> session.title().equals("reset"), "RIS finished");
            // An incomplete escape sequence must never hold the snapshot lock while waiting for input.
            connector.feed("\033[");
            CompletableFuture<ScreenSnapshot> idle = new CompletableFuture<>();
            Thread.ofPlatform().daemon().start(() -> idle.complete(session.snapshot()));
            assertThat(idle.get(5, TimeUnit.SECONDS).cursorShape()).isNull();
        }
    }
}
