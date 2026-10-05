package dev.jasper.indexer.index;

import dev.jasper.sdk.Subscription;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** The registered projects and their latest snapshots, in registration order. UI thread. */
public final class Projects {
    /** How the last job for a project ended. */
    public enum Outcome {
        /** No job finished yet. */
        NONE,
        /** It produced the current snapshot. */
        OK,
        /** It failed; the previous snapshot, if any, stays. */
        FAILED,
        /** The root folder was gone. */
        MISSING
    }

    /** What Settings shows for a project. */
    public enum State { FRESH, STALE, TRUNCATED, MISSING, NEEDS_REINDEX, INDEXING }

    /**
     * One project.
     *
     * @param project the registration
     * @param index its latest snapshot
     * @param outcome how its last job ended
     * @param indexing whether a job for it is running
     */
    public record Entry(Project project, Optional<ProjectIndex> index, Outcome outcome, boolean indexing) {
        /** Rejects nulls. */
        public Entry {
            Objects.requireNonNull(project, "project");
            Objects.requireNonNull(index, "index");
            Objects.requireNonNull(outcome, "outcome");
        }

        /** @return the state Settings shows */
        public State state() {
            if (indexing) return State.INDEXING;
            if (outcome == Outcome.MISSING) return State.MISSING;
            if (index.isEmpty()) return State.NEEDS_REINDEX;
            if (outcome == Outcome.FAILED) return State.STALE;
            return index.get().truncated() ? State.TRUNCATED : State.FRESH;
        }
    }

    private final Map<ProjectKey, Entry> entries = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** @return every project, in registration order */
    public List<Entry> entries() { return List.copyOf(entries.values()); }

    /**
     * @param key a project
     * @return its entry
     */
    public Optional<Entry> get(ProjectKey key) { return Optional.ofNullable(entries.get(key)); }

    /** @return the snapshots search may use: present, and not of a missing root */
    public List<ProjectIndex> indexes() {
        return entries.values().stream().filter(entry -> entry.outcome() != Outcome.MISSING)
            .flatMap(entry -> entry.index().stream()).toList();
    }

    /**
     * @param folder a local folder
     * @return the project with the deepest root containing it
     */
    public Optional<Entry> containing(Path folder) { return deepest(entry -> entry.project().key().contains(folder)); }

    /**
     * @param host a saved host's id
     * @param folder an absolute folder on it
     * @return the project with the deepest root containing it
     */
    public Optional<Entry> containing(UUID host, String folder) { return deepest(entry -> entry.project().key().contains(host, folder)); }

    /** @param project a project to register without a snapshot; nothing happens when it is known */
    public void add(Project project) {
        if (entries.containsKey(project.key())) return;
        entries.put(project.key(), new Entry(project, Optional.empty(), Outcome.NONE, false));
        changed();
    }

    /**
     * @param project a saved project
     * @param index its saved snapshot
     */
    public void loaded(Project project, Optional<ProjectIndex> index) {
        entries.put(project.key(), new Entry(project, index, index.isPresent() ? Outcome.OK : Outcome.NONE, false));
        changed();
    }

    /** @param key a project to forget */
    public void remove(ProjectKey key) { if (entries.remove(key) != null) changed(); }

    /**
     * @param key a project
     * @param running whether a job for it is running
     */
    public void indexing(ProjectKey key, boolean running) {
        update(key, entry -> new Entry(entry.project(), entry.index(), entry.outcome(), running));
    }

    /**
     * @param key a project
     * @param index its new snapshot
     */
    public void indexed(ProjectKey key, ProjectIndex index) {
        update(key, entry -> new Entry(entry.project(), Optional.of(index), Outcome.OK, entry.indexing()));
    }

    /**
     * @param key a project
     * @param outcome how its last job failed
     */
    public void outcome(ProjectKey key, Outcome outcome) {
        update(key, entry -> new Entry(entry.project(), entry.index(), outcome, entry.indexing()));
    }

    /**
     * @param listener called on the UI thread after any change
     * @return removes the listener
     */
    public Subscription onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    private Optional<Entry> deepest(Predicate<Entry> contains) {
        return entries.values().stream().filter(contains)
            .max(Comparator.comparingInt(entry -> entry.project().key().root().length()));
    }

    private void update(ProjectKey key, UnaryOperator<Entry> change) {
        Entry entry = entries.get(key);
        if (entry == null) return;
        entries.put(key, change.apply(entry));
        changed();
    }

    private void changed() { listeners.forEach(Runnable::run); }
}
