package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.TestClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ServerEventsTest {
    final TestClock clock = new TestClock();
    final List<ServerEvent> seen = new ArrayList<>();

    @Test void reportsEventsAndThrottlesTransferProgress() {
        var events = new ServerEvents(clock, seen::add);
        events.connected("10.0.0.5");
        events.loginSucceeded("10.0.0.5", "me");
        events.loginFailed("10.0.0.5", "root");
        events.changed("10.0.0.5", "deleted /a.txt");
        events.error("", "disk full");
        boolean[] cancelled = {false};
        var transfer = events.transfer("10.0.0.5", ServerEvent.Direction.UPLOAD, "/a.bin", 300, () -> cancelled[0] = true);
        transfer.add(100);
        clock.advance(Duration.ofMillis(300));
        transfer.add(100);
        transfer.add(50);
        clock.advance(Duration.ofMillis(100));
        transfer.finish(true);
        transfer.finish(false);
        assertThat(seen).extracting(event -> event.getClass().getSimpleName()).containsExactly(
            "Connected", "LoginSucceeded", "LoginFailed", "Changed", "Failed", "TransferStarted", "TransferProgress", "Transferred");
        var started = (ServerEvent.TransferStarted) seen.get(5);
        started.cancel().run();
        assertThat(cancelled[0]).isTrue();
        assertThat(((ServerEvent.TransferProgress) seen.get(6)).bytes()).isEqualTo(200);
        var done = (ServerEvent.Transferred) seen.get(7);
        assertThat(done.bytes()).isEqualTo(250);
        assertThat(done.took()).isEqualTo(Duration.ofMillis(400));
        assertThat(done.complete()).isTrue();
        assertThat(done.id()).isEqualTo(started.id());
    }

    @Test void aThrowingSinkDoesNotReachTheEngine() {
        var events = new ServerEvents(clock, event -> { throw new IllegalStateException("broken listener"); });
        events.connected("10.0.0.5");
        events.transfer("10.0.0.5", ServerEvent.Direction.DOWNLOAD, "/a", -1, () -> { }).finish(true);
    }
}
