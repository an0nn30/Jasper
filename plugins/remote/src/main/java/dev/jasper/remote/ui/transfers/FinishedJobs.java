package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.TransferJob;
import dev.jasper.remote.transfer.TransferState;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Remembers when each transfer finished cleanly and says which have shown "Done" long enough to leave. */
final class FinishedJobs {
    static final long FADE_NANOS = TimeUnit.SECONDS.toNanos(5);
    private final Map<UUID, Long> since = new HashMap<>();

    /** Completed, or completed whose only issues are deliberately skipped items, with nothing left to clean up. */
    static boolean clean(TransferJob job) {
        if (job.cleanupPending() > 0) return false;
        return job.state() == TransferState.COMPLETED
            || job.state() == TransferState.COMPLETED_WITH_ISSUES && job.failedEntries() == 0 && job.metadataWarnings() == 0;
    }

    List<UUID> expired(List<TransferJob> jobs, long nanos) {
        var seen = new HashSet<UUID>();
        var result = new ArrayList<UUID>();
        for (var job : jobs) {
            if (!clean(job)) continue;
            seen.add(job.id());
            if (nanos - since.computeIfAbsent(job.id(), id -> nanos) >= FADE_NANOS) result.add(job.id());
        }
        since.keySet().retainAll(seen);
        return List.copyOf(result);
    }
}
