package dev.jasper.buddy.notice;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One thing worth showing. {@code id} is whatever produced it — a terminal pane today, an sftp
 * transfer or an SSH session later — and a later notice with the same source and key replaces this
 * one rather than stacking on top of it. Two producers cannot collide, because the source is part of
 * the identity.
 *
 * <p>{@code detail} is a supplier rather than a string so a running notice can tick without the deck
 * being re-posted every second, and so a future transfer can report bytes through the same field.
 *
 * <p>Identity is {@link #id}, never {@code equals}: the record holds two lambdas, so its
 * generated {@code equals} compares them by reference and means nothing useful.
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

    public enum State {
        RUNNING, NEEDS_INPUT, DONE, FAILED, UP, DEGRADED, DOWN;

        /** A task is never UP; a connection is never DONE. */
        public boolean fits(Kind kind) {
            return kind == Kind.TASK
                ? this == RUNNING || this == NEEDS_INPUT || this == DONE || this == FAILED
                : this == UP || this == DEGRADED || this == DOWN;
        }
    }

    public BuddyNotice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A notice needs a non-blank title");
        if (!state.fits(kind)) throw new IllegalArgumentException(state + " is not a state a " + kind + " can be in");
    }

    /**
     * Still happening, so it belongs above his head whether or not you have seen it. An orphan never
     * is: its pane is gone, so a command left in {@code RUNNING} is not running any more — nothing
     * will ever move it on, and without this it would sit above his head for the rest of the session.
     */
    public boolean live() {
        return !orphaned() && (state == State.RUNNING || state == State.NEEDS_INPUT
            || state == State.UP || state == State.DEGRADED);
    }

    /** Wants you specifically: it ended, it broke, or it is waiting on you. */
    public boolean wantsAttention() { return state != State.RUNNING && state != State.UP; }

    /**
     * Its origin is gone — the pane closed. Still worth reading, so this is a missing action rather
     * than another state: a pane closed after a successful build must still say it succeeded.
     */
    public boolean orphaned() { return activate == null; }

    public boolean sameAs(BuddyNoticeId otherId) { return id.equals(otherId); }
}
