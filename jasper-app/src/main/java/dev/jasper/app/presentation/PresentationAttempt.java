package dev.jasper.app.presentation;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The attempt record of the look this process runs (spec 4.6): {@link #pending} just before a window shows or a rehearsal
 * runs, {@link #proven} once it painted, {@link #failed} when the look or its provider failed, and an orderly quit before
 * proof clears the pending mark at exit. {@link #NONE} records nothing: the default look, or a look that was skipped.
 */
public final class PresentationAttempt {
    /** Records nothing. */
    public static final PresentationAttempt NONE = new PresentationAttempt(null, "", "", "", "");
    private final AttemptStore store;
    private final String id, version, digest, build;
    private final AtomicBoolean orderly = new AtomicBoolean(), proven = new AtomicBoolean(), failed = new AtomicBoolean();

    /**
     * @param store the record, or null for {@link #NONE}
     * @param id the provider's id
     * @param version the provider's version
     * @param digest the look's digest
     * @param build this application's build identity
     */
    public PresentationAttempt(AttemptStore store, String id, String version, String digest, String build) {
        this.store = store;
        this.id = Objects.requireNonNull(id, "id"); this.version = Objects.requireNonNull(version, "version");
        this.digest = Objects.requireNonNull(digest, "digest"); this.build = Objects.requireNonNull(build, "build");
    }

    /** Whether this handle writes a record. */
    public boolean records() { return store != null; }

    /** The provider's id; empty for {@link #NONE}. */
    public String id() { return id; }

    /**
     * A window is about to show, or a rehearsal to run: pending, unless this look already failed or was proven (a proven
     * look stays proven within a process). Runs on the EDT, bounded by {@link AttemptStore#EDT_WAIT}.
     */
    public void pending() {
        if (store == null || failed.get() || proven.get()) return;
        store.update(id, attempt -> attempt.tried(version, digest, build, AttemptStore.State.PENDING), AttemptStore.EDT_WAIT);
    }

    /** The first window or the rehearsal painted: proven, unless this look already failed. Off the EDT; once. */
    public void proven() {
        if (store == null || failed.get() || !proven.compareAndSet(false, true)) return;
        store.update(id, attempt -> attempt.tried(version, digest, build, AttemptStore.State.PROVEN), AttemptStore.WAIT);
    }

    /** The kit was blamed at start, or the provider failed to load or start: failed. */
    public void failed() {
        if (store == null) return;
        failed.set(true);
        store.update(id, attempt -> attempt.tried(version, digest, build, AttemptStore.State.FAILED), AttemptStore.EDT_WAIT);
    }

    /** The quit under way is orderly (Quit, or a programmatic {@code quitNow}). */
    public void orderlyQuit() { orderly.set(true); }

    /**
     * The process is exiting (the shutdown hook): after an orderly quit before proof, the pending mark goes; otherwise it
     * stays. A look that failed keeps its failed mark.
     */
    public void exiting() {
        if (store == null || !orderly.get() || proven.get() || failed.get()) return;
        store.update(id, attempt -> attempt.state().equals(Optional.of(AttemptStore.State.PENDING)) && attempt.digest().equals(digest)
            ? attempt.cleared() : attempt, AttemptStore.EDT_WAIT);
    }

    /** Try Again: forget whether the look finished starting, so the next start tries it afresh. Off the EDT. */
    public void clear() {
        if (store != null) store.update(id, AttemptStore.Attempt::cleared, AttemptStore.WAIT);
    }

    /**
     * Whether the problem of a look with {@code digest} is new for {@code id}, recording it as announced (spec 5.9's "once
     * per digest"). A record that cannot be written still answers true, so the notice is shown.
     */
    public static boolean announce(AttemptStore store, String id, String digest) {
        if (store.read().of(id).announced().equals(digest)) return false;
        store.update(id, attempt -> attempt.announcing(digest), AttemptStore.EDT_WAIT);
        return true;
    }
}
