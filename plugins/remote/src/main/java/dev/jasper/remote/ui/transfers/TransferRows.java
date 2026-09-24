package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.*;
import java.io.IOException;
import java.util.*;

/** Pure text for one transfer in the strip: what, where, how far, and the one action that applies. */
public final class TransferRows {
    public enum Action {
        RESUME("Resume"), RESOLVE("Resolve…"), RETRY_FAILED("Retry failed"), RETRY_CLEANUP("Retry cleanup");
        private final String label;
        Action(String label) { this.label = label; }
        public String label() { return label; }
    }
    public record Row(UUID id, String arrow, String title, String tooltip, String status, OptionalDouble fraction,
                      boolean indeterminate, Optional<Action> action, boolean finished, boolean cancelling) { }
    static final String UP = "↑", DOWN = "↓", ACROSS = "⇄", LOCAL = "Local";

    private TransferRows() { }

    public static Row row(TransferJob job, Optional<TransferRequest> request, long doneBytes, double bytesPerSecond) {
        String arrow = job.source().equals(LOCAL) ? UP : job.destination().equals(LOCAL) ? DOWN : ACROSS;
        String title = request.map(r -> what(r.paths(), job) + " → " + where(r, job)).orElse(job.source() + " → " + job.destination());
        String tooltip = request.map(r -> job.source() + ": " + String.join(", ", r.paths()) + " → " + job.destination() + ": " + r.directory())
            .orElse(job.source() + " → " + job.destination());
        boolean indeterminate = job.state() == TransferState.SCANNING;
        OptionalDouble fraction = job.state() == TransferState.COMPLETED ? OptionalDouble.of(1)
            : !indeterminate && job.totalBytes() > 0 ? OptionalDouble.of(Math.clamp((double) doneBytes / job.totalBytes(), 0, 1)) : OptionalDouble.empty();
        return new Row(job.id(), arrow, title, tooltip, status(job, doneBytes, bytesPerSecond), fraction, indeterminate, action(job),
            job.state().terminal(), job.intent() == TransferJob.Intent.CANCEL);
    }

    static Optional<Action> action(TransferJob job) {
        if (job.cleanupPending() > 0 && job.state().terminal()) return Optional.of(Action.RETRY_CLEANUP);
        if (job.intent() == TransferJob.Intent.CANCEL) return Optional.empty();
        return switch (job.state()) {
            case PAUSED, INTERRUPTED, FAILED -> Optional.of(Action.RESUME);
            case NEEDS_ATTENTION -> Optional.of(Action.RESOLVE);
            case COMPLETED_WITH_ISSUES -> job.failedEntries() > 0 ? Optional.of(Action.RETRY_FAILED) : Optional.empty();
            default -> Optional.empty();
        };
    }

    static String status(TransferJob job, long doneBytes, double speed) {
        String reason = job.detail().isBlank() ? "" : " — " + job.detail();
        return switch (job.state()) {
            case QUEUED -> "Queued";
            case SCANNING -> "Scanning… " + job.totalEntries() + (job.totalEntries() == 1 ? " item" : " items") + " found";
            case VALIDATING -> "Checking partial file…";
            case RUNNING -> running(job, doneBytes, speed);
            case PAUSING -> "Pausing…";
            case PAUSED -> "Paused";
            case CANCELLING -> "Cancelling…";
            case CANCELLED -> "Cancelled";
            case COMPLETED -> "Done";
            case COMPLETED_WITH_ISSUES -> done(job);
            case NEEDS_ATTENTION -> "Needs attention" + reason;
            case INTERRUPTED -> "Interrupted" + reason;
            case FAILED -> "Failed" + reason;
        };
    }

    private static String running(TransferJob job, long doneBytes, double speed) {
        if (job.totalBytes() <= 0) return "Transferring";
        var text = new StringBuilder().append((long) Math.floor(100.0 * Math.min(doneBytes, job.totalBytes()) / job.totalBytes())).append('%');
        if (speed > 0) {
            text.append(" · ").append(TransferPresentation.bytes((long) speed)).append("/s");
            long left = job.totalBytes() - doneBytes;
            if (left > 0) text.append(" · ").append(remaining((long) Math.ceil(left / speed))).append(" left");
        }
        return text.toString();
    }

    private static String done(TransferJob job) {
        var text = new StringBuilder("Done");
        if (job.failedEntries() > 0) text.append(" · ").append(job.failedEntries()).append(" failed");
        if (job.skippedEntries() > 0) text.append(" · ").append(job.skippedEntries()).append(" skipped");
        if (job.metadataWarnings() > 0) text.append(" · ").append(job.metadataWarnings()).append(" warnings");
        return text.toString();
    }

    static String remaining(long seconds) {
        if (seconds < 60) return seconds + " s";
        if (seconds < 3600) return (seconds + 59) / 60 + " min";
        return seconds / 3600 + " h " + (seconds % 3600) / 60 + " min";
    }

    static String name(String path) {
        String trimmed = path;
        while (trimmed.length() > 1 && (trimmed.endsWith("/") || trimmed.endsWith("\\"))) trimmed = trimmed.substring(0, trimmed.length() - 1);
        int cut = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        return cut < 0 || cut == trimmed.length() - 1 ? trimmed : trimmed.substring(cut + 1);
    }

    private static String what(List<String> paths, TransferJob job) {
        String first = name(paths.getFirst());
        if (paths.size() > 1) return first + " and " + (paths.size() - 1) + " more";
        return job.scanned() && job.totalEntries() > 1 ? first + " (" + (job.totalEntries() - 1) + " items)" : first;
    }

    private static String where(TransferRequest request, TransferJob job) {
        try {
            return request.destination().identity().map(identity -> identity.host().name() + ":" + request.directory()).orElse(request.directory());
        } catch (IOException unreadable) {
            return job.destination();
        }
    }
}
