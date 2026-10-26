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
            @Override public void needsInput(String detail) { updates.add("needs input " + detail); }
            @Override public void resume(String detail) { updates.add("resumed " + detail); }
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

    @Test void interleavedTransfersFromTwoServersEachGetTheirOwnActivity() {
        var handles = new ArrayList<List<String>>();
        var activities = new TransferActivities(spec -> {
            var mine = new ArrayList<String>();
            handles.add(mine);
            return new ActivityHandle() {
                @Override public UUID id() { return UUID.randomUUID(); }
                @Override public void progress(double fraction, String detail) { mine.add("progress"); }
                @Override public void detail(String detail) { mine.add("detail"); }
                @Override public void needsInput(String detail) { mine.add("needs input"); }
                @Override public void resume(String detail) { mine.add("resumed"); }
                @Override public void succeed(String detail) { mine.add("done"); }
                @Override public void fail(String detail) { mine.add("failed"); }
                @Override public void cancelled() { mine.add("cancelled"); }
            };
        }, "show");
        activities.accept(new ServerEvent.TransferStarted(AT, "a", 1, ServerEvent.Direction.DOWNLOAD, "/one", 100, () -> { }));
        activities.accept(new ServerEvent.TransferStarted(AT, "b", 2, ServerEvent.Direction.UPLOAD, "/two", 100, () -> { }));
        activities.accept(new ServerEvent.TransferProgress(AT.plusSeconds(2), "a", 1, 10, 100));
        activities.accept(new ServerEvent.TransferProgress(AT.plusSeconds(2), "b", 2, 10, 100));
        activities.accept(new ServerEvent.Transferred(AT.plusSeconds(3), "b", 2, ServerEvent.Direction.UPLOAD, "/two", 100, Duration.ofSeconds(3), true));
        activities.accept(new ServerEvent.Transferred(AT.plusSeconds(3), "a", 1, ServerEvent.Direction.DOWNLOAD, "/one", 100, Duration.ofSeconds(3), false));
        assertThat(handles).hasSize(2);
        assertThat(handles.get(0)).containsExactly("progress", "failed");
        assertThat(handles.get(1)).containsExactly("progress", "done");
    }

    @Test void aCancelledTransferEndsItsActivityAsCancelled() {
        var activities = new TransferActivities(spec -> { begun.add(spec); return handle(); }, "show");
        activities.accept(new ServerEvent.TransferStarted(AT, "a", 1, ServerEvent.Direction.DOWNLOAD, "/big", 100, () -> { }));
        activities.accept(new ServerEvent.TransferProgress(AT.plusSeconds(2), "a", 1, 10, 100));
        begun.getFirst().cancel().orElseThrow().run();
        activities.accept(new ServerEvent.Transferred(AT.plusSeconds(3), "a", 1, ServerEvent.Direction.DOWNLOAD, "/big", 10, Duration.ofSeconds(3), false));
        assertThat(updates).containsExactly("10% 10 B of 100 B", "cancelled");
    }
}
