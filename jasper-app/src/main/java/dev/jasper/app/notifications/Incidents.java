package dev.jasper.app.notifications;

import dev.jasper.app.lifecycle.Subscription;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * This session's incidents, the Errors window's model (spec section 5): at most {@value #LIMIT}, the oldest dropped.
 * A failure from the same source, of the same class and with the same root-cause frames is a repeat of an earlier
 * incident. Nothing is written to disk. EDT-confined.
 */
public final class Incidents {
    public static final int LIMIT = 100;
    private static final System.Logger LOG = System.getLogger(Incidents.class.getName());

    /** What changed. */
    public sealed interface Change {
        record Added(Incident incident) implements Change {}
        record Repeated(Incident incident) implements Change {}
        /** Dropped to keep the newest {@value Incidents#LIMIT}. */
        record Removed(UUID id) implements Change {}
        record Cleared() implements Change {}
    }

    private final Clock clock;
    /** Oldest sighting first; a repeat moves to the end. Keyed by source, exception class and signature. */
    private final LinkedHashMap<String, Incident> byKey = new LinkedHashMap<>();
    private final List<Consumer<Change>> listeners = new ArrayList<>();

    /**
     * @param clock stamps sightings
     */
    public Incidents(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    /**
     * Records a failure: a new incident, or a repeat of the one with the same key.
     *
     * @param source {@code jasper} or a plugin id
     * @param operation what it was doing, such as "thread main" or "panel remote.sftp"
     * @param thread the name of the thread that caught it
     * @param capture what was taken of the throwable on that thread
     * @return the incident as it now stands
     */
    public Incident record(String source, String operation, String thread, Capture capture) {
        requireEdt();
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(thread, "thread");
        Objects.requireNonNull(capture, "capture");
        Instant now = clock.instant();
        String key = source + '\n' + capture.exceptionClass() + '\n' + capture.signature();
        Incident earlier = byKey.remove(key);
        if (earlier != null) {
            Incident again = earlier.repeated(now);
            byKey.put(key, again);
            emit(new Change.Repeated(again));
            return again;
        }
        Incident added = new Incident(UUID.randomUUID(), source, operation, thread, capture.exceptionClass(),
            capture.message(), capture.trace(), 1, now, now);
        byKey.put(key, added);
        var dropped = new ArrayList<UUID>();
        for (Iterator<Incident> oldest = byKey.values().iterator(); byKey.size() > LIMIT; ) {
            dropped.add(oldest.next().id());
            oldest.remove();
        }
        emit(new Change.Added(added));
        for (UUID id : dropped) emit(new Change.Removed(id));
        return added;
    }

    public Optional<Incident> find(UUID id) {
        requireEdt();
        return byKey.values().stream().filter(incident -> incident.id().equals(id)).findFirst();
    }

    /** The incidents, the most recently seen first. */
    public List<Incident> list() {
        requireEdt();
        var newestFirst = new ArrayList<>(byKey.values());
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    /** The Errors window's Clear. */
    public void clear() {
        requireEdt();
        if (byKey.isEmpty()) return;
        byKey.clear();
        emit(new Change.Cleared());
    }

    public Subscription onChange(Consumer<Change> listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    private void emit(Change change) {
        for (Consumer<Change> listener : List.copyOf(listeners)) {
            try { listener.accept(change); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "An incident listener failed", failure); }
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Incidents belong to the EDT");
    }
}
