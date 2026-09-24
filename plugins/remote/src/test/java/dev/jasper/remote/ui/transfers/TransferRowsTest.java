package dev.jasper.remote.ui.transfers;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.remote.transfer.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TransferRowsTest {
    static final UUID ID = UUID.randomUUID();
    static TransferJob job(TransferState state, String source, String destination, long entries, long total, long confirmed, long done, long skipped, long failed, String detail, long cleanup) {
        return new TransferJob(ID, source, destination, state, TransferJob.Intent.RUN, 0, entries, total, confirmed, done, skipped, failed, state != TransferState.SCANNING, detail, cleanup);
    }
    static TransferRequest upload(String... paths) { return new TransferRequest(EndpointRef.local(), List.of(paths), EndpointRef.local(), "/srv/app"); }

    @Test void copyingShowsPercentSpeedAndTimeLeft() {
        var running = job(TransferState.RUNNING, "Local", "dustin@host", 1, 100L * 1024 * 1024, 0, 0, 0, 0, "", 0);
        var row = TransferRows.row(running, Optional.of(upload("/Users/me/report.pdf")), 58L * 1024 * 1024, 4.1 * 1024 * 1024);
        assertThat(row.arrow()).isEqualTo("↑");
        assertThat(row.title()).isEqualTo("report.pdf → /srv/app");
        assertThat(row.status()).isEqualTo("58% · 4.1 MiB/s · 11 s left");
        assertThat(row.fraction().orElseThrow()).isEqualTo(0.58, within(0.001));
        assertThat(row.action()).isEmpty();
        assertThat(row.finished()).isFalse();
        assertThat(TransferRows.status(running, 58L * 1024 * 1024, 0)).as("no speed yet, no time left").isEqualTo("58%");
    }

    @Test void titlesNameTheSelectionAndTheDestination() {
        var folder = job(TransferState.RUNNING, "dustin@host", "Local", 341, 10, 0, 0, 0, 0, "", 0);
        assertThat(TransferRows.row(folder, Optional.of(upload("/var/log/")), 0, 0).title()).isEqualTo("log (340 items) → /srv/app");
        assertThat(TransferRows.row(folder, Optional.of(upload("/a/one", "/a/two", "/a/three")), 0, 0).title()).isEqualTo("one and 2 more → /srv/app");
        assertThat(TransferRows.row(folder, Optional.of(upload("C:\\Users\\me\\notes.txt")), 0, 0).title()).startsWith("notes.txt");
        assertThat(TransferRows.row(folder, Optional.empty(), 0, 0).title()).as("before the request arrives").isEqualTo("dustin@host → Local");
        assertThat(TransferRows.row(folder, Optional.empty(), 0, 0).arrow()).isEqualTo("↓");
        assertThat(TransferRows.row(job(TransferState.RUNNING, "a@one", "b@two", 1, 1, 0, 0, 0, 0, "", 0), Optional.empty(), 0, 0).arrow()).isEqualTo("⇄");
        assertThat(TransferRows.row(folder, Optional.of(upload("/var/log")), 0, 0).tooltip()).contains("dustin@host", "/var/log", "/srv/app");
    }

    @Test void statesUseTheirNamesAndReasons() {
        assertThat(TransferRows.status(job(TransferState.SCANNING, "Local", "h", 12, 0, 0, 0, 0, 0, "", 0), 0, 0)).isEqualTo("Scanning… 12 items found");
        assertThat(TransferRows.row(job(TransferState.SCANNING, "Local", "h", 12, 0, 0, 0, 0, 0, "", 0), Optional.empty(), 0, 0).indeterminate()).isTrue();
        assertThat(TransferRows.status(job(TransferState.INTERRUPTED, "Local", "h", 1, 10, 5, 0, 0, 0, "connection lost", 0), 5, 0)).isEqualTo("Interrupted — connection lost");
        assertThat(TransferRows.status(job(TransferState.NEEDS_ATTENTION, "Local", "h", 1, 10, 5, 0, 0, 0, "Destination changed", 0), 5, 0)).isEqualTo("Needs attention — Destination changed");
        assertThat(TransferRows.status(job(TransferState.COMPLETED, "Local", "h", 1, 10, 10, 1, 0, 0, "", 0), 10, 0)).isEqualTo("Done");
        assertThat(TransferRows.status(job(TransferState.COMPLETED_WITH_ISSUES, "Local", "h", 5, 10, 8, 2, 1, 2, "", 0), 8, 0)).isEqualTo("Done · 2 failed · 1 skipped");
        assertThat(TransferRows.status(job(TransferState.CANCELLED, "Local", "h", 1, 10, 5, 0, 0, 0, "", 0), 5, 0)).isEqualTo("Cancelled");
        assertThat(TransferRows.status(job(TransferState.PAUSED, "Local", "h", 1, 10, 5, 0, 0, 0, "", 0), 5, 0)).isEqualTo("Paused");
    }

    @Test void oneActionPerStateWithCleanupFirst() {
        assertThat(TransferRows.action(job(TransferState.PAUSED, "Local", "h", 1, 1, 0, 0, 0, 0, "", 0))).contains(TransferRows.Action.RESUME);
        assertThat(TransferRows.action(job(TransferState.INTERRUPTED, "Local", "h", 1, 1, 0, 0, 0, 0, "", 0))).contains(TransferRows.Action.RESUME);
        assertThat(TransferRows.action(job(TransferState.FAILED, "Local", "h", 1, 1, 0, 0, 0, 0, "", 0))).contains(TransferRows.Action.RESUME);
        assertThat(TransferRows.action(job(TransferState.NEEDS_ATTENTION, "Local", "h", 1, 1, 0, 0, 0, 0, "", 0))).contains(TransferRows.Action.RESOLVE);
        assertThat(TransferRows.action(job(TransferState.COMPLETED_WITH_ISSUES, "Local", "h", 3, 1, 0, 1, 0, 2, "", 0))).contains(TransferRows.Action.RETRY_FAILED);
        assertThat(TransferRows.action(job(TransferState.COMPLETED_WITH_ISSUES, "Local", "h", 3, 1, 0, 2, 1, 0, "", 0))).as("skips only").isEmpty();
        assertThat(TransferRows.action(job(TransferState.CANCELLED, "Local", "h", 1, 1, 0, 0, 0, 0, "", 2))).contains(TransferRows.Action.RETRY_CLEANUP);
        assertThat(TransferRows.action(job(TransferState.COMPLETED_WITH_ISSUES, "Local", "h", 3, 1, 0, 1, 0, 2, "", 1))).contains(TransferRows.Action.RETRY_CLEANUP);
        assertThat(TransferRows.action(job(TransferState.RUNNING, "Local", "h", 1, 1, 0, 0, 0, 0, "", 0))).isEmpty();
        assertThat(TransferRows.Action.RESOLVE.label()).isEqualTo("Resolve…");
        var cancelling = new TransferJob(ID, "Local", "h", TransferState.PAUSED, TransferJob.Intent.CANCEL, 0, 1, 1, 0, 0, 0, 0, true, "", 0);
        assertThat(TransferRows.action(cancelling)).isEmpty();
        assertThat(TransferRows.row(cancelling, Optional.empty(), 0, 0).cancelling()).isTrue();
        assertThat(TransferRows.row(job(TransferState.CANCELLED, "Local", "h", 1, 1, 0, 0, 0, 0, "", 0), Optional.empty(), 0, 0).finished()).isTrue();
    }

    @Test void aCancelledTransferCanBeDismissedAndOffersCleanupRetry() {
        var cancelled = new TransferJob(ID, "Local", "h", TransferState.CANCELLED, TransferJob.Intent.CANCEL, 0, 1, 1, 0, 0, 0, 0, true, "", 0);
        var row = TransferRows.row(cancelled, Optional.empty(), 0, 0);
        assertThat(row.cancelling()).as("× enabled").isFalse();
        assertThat(row.finished()).isTrue();
        assertThat(row.action()).isEmpty();
        var dirty = new TransferJob(ID, "Local", "h", TransferState.CANCELLED, TransferJob.Intent.CANCEL, 0, 1, 1, 0, 0, 0, 0, true, "", 2);
        var dirtyRow = TransferRows.row(dirty, Optional.empty(), 0, 0);
        assertThat(dirtyRow.cancelling()).isFalse();
        assertThat(dirtyRow.finished()).isTrue();
        assertThat(dirtyRow.action()).contains(TransferRows.Action.RETRY_CLEANUP);
        var stopping = new TransferJob(ID, "Local", "h", TransferState.CANCELLING, TransferJob.Intent.CANCEL, 0, 1, 1, 0, 0, 0, 0, true, "", 0);
        assertThat(TransferRows.row(stopping, Optional.empty(), 0, 0).cancelling()).isTrue();
    }

    @Test void remainingTimeReads() {
        assertThat(TransferRows.remaining(12)).isEqualTo("12 s");
        assertThat(TransferRows.remaining(61)).isEqualTo("2 min");
        assertThat(TransferRows.remaining(3900)).isEqualTo("1 h 5 min");
        assertThat(TransferRows.name("/a/b/")).isEqualTo("b");
        assertThat(TransferRows.name("C:\\x\\y.txt")).isEqualTo("y.txt");
    }

    @Test void speedIsSmoothedAndResetsWhenNotCopying() {
        var speeds = new JobSpeeds();
        long second = TimeUnit.SECONDS.toNanos(1);
        assertThat(speeds.update(ID, TransferState.RUNNING, 0, 0)).isZero();
        assertThat(speeds.update(ID, TransferState.RUNNING, 1000, second)).isEqualTo(1000.0, within(0.01));
        assertThat(speeds.update(ID, TransferState.RUNNING, 3000, 2 * second)).isEqualTo(1300.0, within(0.01));
        assertThat(speeds.update(ID, TransferState.PAUSED, 3000, 3 * second)).isZero();
        assertThat(speeds.update(ID, TransferState.RUNNING, 3000, 4 * second)).as("a pause starts over").isZero();
    }

    @Test void skipOnlyFinishFadesButFailuresStay() {
        var finished = new FinishedJobs();
        var clean = job(TransferState.COMPLETED, "Local", "h", 1, 1, 1, 1, 0, 0, "", 0);
        var skipOnly = new TransferJob(UUID.randomUUID(), "Local", "h", TransferState.COMPLETED_WITH_ISSUES, TransferJob.Intent.RUN, 0, 3, 1, 1, 2, 1, 0, true, "", 0);
        var failed = new TransferJob(UUID.randomUUID(), "Local", "h", TransferState.COMPLETED_WITH_ISSUES, TransferJob.Intent.RUN, 0, 3, 1, 1, 2, 0, 1, true, "", 0);
        var dirty = new TransferJob(UUID.randomUUID(), "Local", "h", TransferState.COMPLETED, TransferJob.Intent.RUN, 0, 1, 1, 1, 1, 0, 0, true, "", 1);
        var jobs = List.of(clean, skipOnly, failed, dirty);
        assertThat(finished.expired(jobs, 0)).isEmpty();
        assertThat(finished.expired(jobs, FinishedJobs.FADE_NANOS - 1)).isEmpty();
        assertThat(finished.expired(jobs, FinishedJobs.FADE_NANOS)).containsExactlyInAnyOrder(clean.id(), skipOnly.id());
        assertThat(FinishedJobs.clean(failed)).isFalse();
        assertThat(FinishedJobs.clean(dirty)).isFalse();
    }
}
