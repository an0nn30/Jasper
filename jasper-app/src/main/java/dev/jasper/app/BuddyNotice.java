package dev.jasper.app;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One thing worth remembering. {@code key} is whatever produced it — a terminal pane today, an sftp
 * transfer or an SSH session later — and a later notice with the same source and key replaces this
 * one rather than stacking on top of it. Two producers cannot collide, because the source is part of
 * the identity.
 *
 * <p>{@code detail} is a supplier rather than a string so a running notice can tick without the deck
 * being re-posted every second, and so a future transfer can report bytes through the same field. A
 * start timestamp would have served the first purpose and not the second, and would have needed a
 * zero sentinel — the mistake this repository has already made twice.
 *
 * <p>Identity is {@link #sameAs}, never {@code equals}: the record holds two lambdas, so its
 * generated {@code equals} compares them by reference and means nothing useful.
 */
record BuddyNotice(String source, Object key, String title, State state,
                   Supplier<String> detail, Runnable activate) {
    /** Deliberately not "running/succeeded/failed": a transfer is in progress, not running. */
    enum State { ACTIVE, DONE, FAILED }

    BuddyNotice {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A notice needs a non-blank title");
    }

    /**
     * Its origin is gone — the pane closed. Still worth reading, so this is a missing action rather
     * than a fourth state: a pane closed after a successful build must still say it succeeded.
     */
    boolean orphaned() { return activate == null; }

    boolean sameAs(String otherSource, Object otherKey) {
        return source.equals(otherSource) && key.equals(otherKey);
    }
}
