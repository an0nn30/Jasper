package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.engine.ServerEvent;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ActivityLogTest {
    static final Instant AT = Instant.parse("2026-09-30T12:00:00Z");

    @Test void describesWhatPeopleCareAbout() {
        assertThat(ActivityLog.describe(new ServerEvent.LoginSucceeded(AT, "192.168.1.44", "dustin"))).contains("192.168.1.44 login dustin");
        assertThat(ActivityLog.describe(new ServerEvent.LoginFailed(AT, "192.168.1.44", "root"))).contains("192.168.1.44 login failed for root");
        assertThat(ActivityLog.describe(new ServerEvent.LoginFailed(AT, "192.168.1.44", ""))).contains("192.168.1.44 login failed");
        assertThat(ActivityLog.describe(new ServerEvent.Transferred(AT, "192.168.1.44", 1, ServerEvent.Direction.UPLOAD, "/IMG_2231.HEIC", 3_250_585, Duration.ofMillis(400), true)))
            .contains("192.168.1.44 ↑ /IMG_2231.HEIC  3.1 MB  0.4 s");
        assertThat(ActivityLog.describe(new ServerEvent.Transferred(AT, "10.0.0.2", 2, ServerEvent.Direction.DOWNLOAD, "/a", 10, Duration.ofSeconds(2), false)))
            .contains("10.0.0.2 ↓ /a  10 B  2.0 s  (stopped)");
        assertThat(ActivityLog.describe(new ServerEvent.Changed(AT, "10.0.0.2", "deleted /a"))).contains("10.0.0.2 deleted /a");
        assertThat(ActivityLog.describe(new ServerEvent.Failed(AT, "", "disk full"))).contains("error: disk full");
        assertThat(ActivityLog.describe(new ServerEvent.Connected(AT, "10.0.0.2"))).contains("10.0.0.2 connected");
        assertThat(ActivityLog.describe(new ServerEvent.TransferProgress(AT, "10.0.0.2", 1, 5, 10))).isEmpty();
    }

    @Test void keepsTheNewestEntries() {
        var log = new ActivityLog(2);
        assertThat(log.add(new ServerEvent.Connected(AT, "a"))).isTrue();
        assertThat(log.add(new ServerEvent.TransferProgress(AT, "a", 1, 1, 1))).isFalse();
        log.add(new ServerEvent.Connected(AT, "b"));
        log.add(new ServerEvent.Connected(AT, "c"));
        assertThat(log.entries()).extracting(ActivityLog.Entry::text).containsExactly("b connected", "c connected");
        log.clear();
        assertThat(log.entries()).isEmpty();
    }
}
