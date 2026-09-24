package dev.jasper.remote.transfer;

/** Shared queue presentation; durable intent is tracked separately from observed worker state. */
public enum TransferState {
    QUEUED, SCANNING, VALIDATING, RUNNING, PAUSING, PAUSED, CANCELLING, CANCELLED,
    COMPLETED, COMPLETED_WITH_ISSUES, NEEDS_ATTENTION, INTERRUPTED, FAILED;
    public boolean terminal() { return this == CANCELLED || this == COMPLETED || this == COMPLETED_WITH_ISSUES; }
}
