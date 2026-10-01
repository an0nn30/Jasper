package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TransferActivitiesTest {
    static final Instant AT = Instant.parse("2026-09-30T12:00:00Z");
    final List<ActivitySpec> begun = new ArrayList<>();
    final List<String> updates = new ArrayList<>();

    ActivityHandle handle() {
        return new ActivityHandle() {
            @Override public UUID id() { return UUID.randomUUID(); }
            @Override public void progress(double fraction, String detail) { updates.add(Math.round(fraction * 100) + "% " + detail); }
            @Override public void detail(String detail) { updates.add(detail); }
            @Override public void succeed(String detail) { updates.add("done " + detail); }
            @Override public void fail(String detail) { updates.add("failed " + detail); }
            @Override public void cancelled() { updates.add("cancelled"); }
        };
    }

    @Test void onlyTransfersLongerThanASecondBecomeActivities() {
        var activities = new TransferActivities(spec -> { begun.add(spec); return handle(); }, "dev.jasper.fileserver.show-panel");
        boolean[] cancelled = {false};
        activities.accept(new ServerEvent.TransferStarted(AT, "10.0.0.2", 1, ServerEvent.Direction.DOWNLOAD, "/big.iso", 4096, () -> cancelled[0] = true));
        activities.accept(new ServerEvent.TransferProgress(AT.plusMillis(500), "10.0.0.2", 1, 1024, 4096));
        assertThat(begun).isEmpty();
        activities.accept(new ServerEvent.TransferProgress(AT.plusMillis(1200), "10.0.0.2", 1, 2048, 4096));
        assertThat(begun).singleElement().satisfies(spec -> {
            assertThat(spec.title()).isEqualTo("Sending big.iso");
            assertThat(spec.detail()).isEqualTo("10.0.0.2");
            assertThat(spec.activateActionId()).contains("dev.jasper.fileserver.show-panel");
            spec.cancel().orElseThrow().run();
        });
        assertThat(cancelled[0]).isTrue();
        activities.accept(new ServerEvent.Transferred(AT.plusSeconds(2), "10.0.0.2", 1, ServerEvent.Direction.DOWNLOAD, "/big.iso", 4096, Duration.ofSeconds(2), true));
        activities.accept(new ServerEvent.TransferStarted(AT, "10.0.0.2", 2, ServerEvent.Direction.UPLOAD, "/quick.txt", -1, () -> { }));
        activities.accept(new ServerEvent.Transferred(AT.plusMillis(100), "10.0.0.2", 2, ServerEvent.Direction.UPLOAD, "/quick.txt", 3, Duration.ofMillis(100), true));
        assertThat(updates).containsExactly("50% 2.0 KB of 4.0 KB", "done 4.0 KB in 2.0 s");
        assertThat(begun).hasSize(1);
    }
}
