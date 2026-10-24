package dev.jasper.buddy.notice;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One piece of live work worth showing: a running command or transfer, a task waiting for input, or a connection.
 * {@code id} is whatever produced it (a terminal pane, a plugin activity, a plugin connection), and a later notice with
 * the same source and key replaces this one rather than stacking on top of it. Different source strings distinguish
 * producers; each producer must choose keys that are unique within its own source. A producer dismisses its notice when
 * the work ends: Buddy keeps no outcomes.
 *
 * <p>{@code detail} is a supplier rather than a string so a running notice can tick without the deck being re-posted
 * every second, and so a transfer can report bytes through the same field.
 *
 * <p>Identity is {@link #id()}, never {@code equals}: the record holds two lambdas, so its generated {@code equals}
 * includes callback equality as well as the displayed values.
 * <p>Construction is resource-free. Detail and activation run on EDT and must not block.
 * @param id nonnull source-qualified identity with stable equality
 * @param kind nonnull task or connection kind
 * @param title nonblank display title
 * @param state nonnull state compatible with kind
 * @param detail nonnull nonblocking detail supplier; presentation tolerates a failed supplier
 * @param activate nonnull activation request
 * @param group nullable display grouping context
 */
public record BuddyNotice(BuddyNoticeId id, Kind kind, String title, State state,
                   Supplier<String> detail, Runnable activate, Group group) {

    /** Creates an ungrouped notice. */
    public BuddyNotice(BuddyNoticeId id, Kind kind, String title, State state,
                       Supplier<String> detail, Runnable activate) {
        this(id, kind, title, state, detail, activate, null);
    }

    /** Generic display group with stable identity.
     * @param id nonnull source-qualified group identity
     * @param title nonblank display title
     */
    public record Group(BuddyNoticeId id, String title) {
        /** Validates group identity and display title. */
        public Group {
            Objects.requireNonNull(id, "id");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("A group needs a non-blank title");
        }
    }

    /** What sort of thing this is, which is what decides how it ends. */
    public enum Kind {
        /** Begins and ends: a command, a file transfer. */
        TASK,
        /** Up until it is not: an SSH session, a tunnel. It never "completes". */
        CONNECTION
    }

    /** Task and connection states; kind compatibility is validated at construction. */
    public enum State {
        /** A task is in progress. */
        RUNNING,
        /** A task is waiting for user input. */
        NEEDS_INPUT,
        /** A connection is available. */
        UP,
        /** A connection is available with a problem needing attention. */
        DEGRADED,
        /** A connection is unavailable. */
        DOWN;

        /** A task is never UP; a connection is never RUNNING. */
        public boolean fits(Kind kind) {
            return kind == Kind.TASK ? this == RUNNING || this == NEEDS_INPUT : this == UP || this == DEGRADED || this == DOWN;
        }
    }

    /** Rejects null required inputs, blank titles and kind/state mismatches before acquiring anything. */
    public BuddyNotice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(activate, "activate");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A notice needs a non-blank title");
        if (!state.fits(kind)) throw new IllegalArgumentException(state + " is not a state a " + kind + " can be in");
    }

    /** True for NEEDS_INPUT, DEGRADED and DOWN: the work is waiting on you or in trouble. */
    public boolean wantsAttention() {
        return state == State.NEEDS_INPUT || state == State.DEGRADED || state == State.DOWN;
    }

    /** Compares only stable notice identity; callback identity never participates. */
    public boolean sameAs(BuddyNoticeId otherId) { return id.equals(otherId); }
}
