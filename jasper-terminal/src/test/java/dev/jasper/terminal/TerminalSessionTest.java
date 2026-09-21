package dev.jasper.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.CursorShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalSessionTest {
    private FakeConnector connector;
    private TerminalSession session;

    @BeforeEach
    void start() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    void outputAppearsOnScreenWithZeroBasedCursor() throws Exception {
        connector.feed("hello");

        Await.until(() -> session.snapshot().lineText(0).equals("hello"), "hello on row 0");
        ScreenSnapshot snapshot = session.snapshot();
        assertThat(snapshot.cursorColumn()).isEqualTo(5);
        assertThat(snapshot.cursorRow()).isZero();
        assertThat(snapshot.width()).isEqualTo(20);
        assertThat(snapshot.height()).isEqualTo(4);
    }

    @Test
    void screenChangesAreReported() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        session.addListener(new TerminalSessionListener() {
            @Override
            public void screenChanged() {
                changes.incrementAndGet();
            }
        });

        connector.feed("x");

        Await.until(() -> changes.get() > 0, "a screenChanged callback");
    }

    @Test
    void cursorPositionRequestIsAnsweredThroughTheConnector() throws Exception {
        connector.feed("ab\033[6n");

        Await.until(() -> connector.written().equals("\033[1;3R"), "cursor position report");
    }

    @Test
    void titleIsReported() throws Exception {
        AtomicReference<String> title = new AtomicReference<>();
        session.addListener(new TerminalSessionListener() {
            @Override
            public void titleChanged(String newTitle) {
                title.set(newTitle);
            }
        });

        connector.feed("\033]0;my title\007");

        Await.until(() -> "my title".equals(title.get()), "title callback");
        assertThat(session.title()).isEqualTo("my title");
    }

    @Test
    void bellIsReported() throws Exception {
        AtomicInteger bells = new AtomicInteger();
        session.addListener(new TerminalSessionListener() {
            @Override
            public void bell() {
                bells.incrementAndGet();
            }
        });

        connector.feed("\007");

        Await.until(() -> bells.get() == 1, "bell callback");
    }

    @Test
    void writeGoesToTheConnector() {
        session.write("ls\r");

        assertThat(connector.written()).isEqualTo("ls\r");
    }

    @Test
    void resizeUpdatesBufferAndConnector() {
        session.resize(30, 10);

        ScreenSnapshot snapshot = session.snapshot();
        assertThat(snapshot.width()).isEqualTo(30);
        assertThat(snapshot.height()).isEqualTo(10);
        assertThat(connector.lastResize()).isEqualTo(new TermSize(30, 10));
        assertThat(session.columns()).isEqualTo(30);
        assertThat(session.rows()).isEqualTo(10);
    }

    @Test
    void cursorShapeRequestsAreRecordedAndClearedByReset() throws Exception {
        connector.feed("\033[6 q");
        Await.until(() -> new CursorRequest(CursorStyle.BEAM, false).equals(session.internalAccess().snapshot().cursorShape()), "beam cursor request");

        connector.feed("\033c");
        Await.until(() -> session.internalAccess().snapshot().cursorShape() == null, "reset clears the request");
    }

    @Test
    void cursorOnlyMovesAreReportedAsScreenChanges() throws Exception {
        connector.feed("abc");
        Await.until(() -> session.snapshot().cursorColumn() == 3, "cursor after abc");
        AtomicInteger changes = new AtomicInteger();
        session.addListener(new TerminalSessionListener() {
            @Override
            public void screenChanged() {
                changes.incrementAndGet();
            }
        });

        connector.feed("\b"); // cursor backward: JediTerm moves the cursor without a buffer change

        Await.until(() -> changes.get() > 0, "screenChanged after a cursor-only move");
        assertThat(session.snapshot().cursorColumn()).isEqualTo(2);
    }

    @Test
    void exitFutureCompletesWhenOutputEnds() throws Exception {
        connector.finish();

        assertThat(session.exitFuture().get(10, TimeUnit.SECONDS)).isZero();
    }
@Test void clearHistoryNotificationRunsOnTheCallingThread() throws Exception {
    AtomicReference<Thread> delivered = new AtomicReference<>();
    session.addListener(new TerminalSessionListener() {
        @Override public void scrollbackReset() { delivered.set(Thread.currentThread()); }
    });
    connector.feed("one\r\ntwo\r\nthree\r\nfour\r\nfive");
    Await.until(() -> session.internalAccess().snapshot().historyLines() > 0, "history exists");
    Thread caller = Thread.currentThread();
    session.clearScrollback();
    assertThat(delivered.get()).isSameAs(caller);
}

    @Test void commandStartListenersCanQueryTheBufferFromAnotherThread() throws Exception {
        var outcome = new java.util.concurrent.CompletableFuture<Boolean>();
        session.addListener(new TerminalSessionListener() {
            @Override public void commandStarted(String command) {
                var queried = new java.util.concurrent.CountDownLatch(1);
                Thread.ofPlatform().daemon().start(() -> {
                    session.internalAccess().snapshot();
                    queried.countDown();
                });
                try { outcome.complete(queried.await(2, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { outcome.completeExceptionally(failure); }
            }
        });
        connector.feed("\033]133;A\007$ \033]133;B\007echo test\r\n\033]133;C\007");
        assertThat(outcome.get(5, TimeUnit.SECONDS)).as("command listener holds no buffer lock").isTrue();
    }
    @Test void aSessionReaderCannotBeStartedTwice() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> session.internalAccess().startReading())
            .isInstanceOf(IllegalStateException.class);
    }
}
