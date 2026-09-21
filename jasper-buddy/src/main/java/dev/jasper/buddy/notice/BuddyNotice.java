package dev.jasper.buddy.notice;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One thing worth showing. {@code id} is whatever produced it — a terminal pane today, an sftp
 * transfer or an SSH session later — and a later notice with the same source and key replaces this
 * one rather than stacking on top of it. Different source strings distinguish producers; each
 * producer must choose keys that are unique within its own source.
 *
 * <p>{@code detail} is a supplier rather than a string so a running notice can tick without the deck
 * being re-posted every second, and so a future transfer can report bytes through the same field.
 *
 * <p>Identity is {@link #id()}, never {@code equals}: the record holds two lambdas, so its
 * generated {@code equals} includes callback equality as well as the displayed values.
 * <p>Construction is resource-free. Detail and activation run on EDT and must not block.
 * @param id nonnull source-qualified identity with stable equality
 * @param kind nonnull task or connection kind
 * @param title nonblank display title
 * @param state nonnull state compatible with kind
 * @param detail nonnull nonblocking detail supplier; presentation tolerates a failed supplier
 * @param activate activation request, or null when its origin is gone
 */
public record BuddyNotice(BuddyNoticeId id, Kind kind, String title, State state,
                   Supplier<String> detail, Runnable activate) {

    /** What sort of thing this is, which is what decides when it stops mattering. */
    public enum Kind {
        /** Begins and ends: a command, a file transfer. */
        TASK,
        /** Up until it is not: an SSH session, a tunnel. It never "completes". */
        CONNECTION
    }

    /** Task and connection lifecycle states; kind compatibility is validated at construction. */
    public enum State {
        /** A task is in progress. */
        RUNNING,
        /** A task is waiting for user input. */
        NEEDS_INPUT,
        /** A task completed successfully. */
        DONE,
        /** A task failed. */
        FAILED,
        /** A connection is available. */
        UP,
        /** A connection is available with a problem needing attention. */
        DEGRADED,
        /** A connection is unavailable. */
        DOWN;

        /** A task is never UP; a connection is never DONE. */
        public boolean fits(Kind kind) {
            return kind == Kind.TASK
                ? this == RUNNING || this == NEEDS_INPUT || this == DONE || this == FAILED
                : this == UP || this == DEGRADED || this == DOWN;
        }
    }

    /** Rejects null required inputs, blank titles and kind/state mismatches before acquiring anything. */
    public BuddyNotice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A notice needs a non-blank title");
        if (!state.fits(kind)) throw new IllegalArgumentException(state + " is not a state a " + kind + " can be in");
    }

    /**
     * True for a non-orphaned RUNNING/NEEDS_INPUT task or UP/DEGRADED connection. Live notices
     * remain eligible for the column after acknowledgement. Null activation makes a notice
     * non-live without changing its state; an unacknowledged orphan is still eligible as unseen.
     */
    public boolean live() {
        return !orphaned() && (state == State.RUNNING || state == State.NEEDS_INPUT
            || state == State.UP || state == State.DEGRADED);
    }

    /** True for NEEDS_INPUT, DONE, FAILED, DEGRADED and DOWN. Derived from state alone, independently of orphaning or acknowledgement. */
    public boolean wantsAttention() { return state != State.RUNNING && state != State.UP; }

    /**
     * True exactly when activation is null. Orphaning keeps the recorded outcome: a producer
     * closing after a successful operation must not rewrite its DONE state.
     */
    public boolean orphaned() { return activate == null; }

    /** Compares only stable notice identity; callback identity never participates. */
    public boolean sameAs(BuddyNoticeId otherId) { return id.equals(otherId); }
}
