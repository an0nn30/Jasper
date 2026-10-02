package dev.jasper.app.notifications;

import dev.jasper.app.lifecycle.Subscription;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.swing.SwingUtilities;

/**
 * The application's one notification store: declared groups, the in-memory history (at most {@value #HISTORY_LIMIT}
 * entries, oldest dropped), read and expired state, and cards waiting for Jasper to be focused. Nothing is persisted.
 *
 * <p>EDT-confined; {@link #submit} is the any-thread entry point. Listeners hear changes in the order they happened; a
 * change a listener makes is delivered after the one it is reacting to, and a card is never requested for an entry
 * that is read, expired or gone by the time the request is delivered.
 */
public final class NotificationCenter {
    public static final int HISTORY_LIMIT = 500;
    /** An identical post within this long of the last one counts as a repeat of it. */
    public static final Duration COALESCE = Duration.ofSeconds(60);
    private static final System.Logger LOG = System.getLogger(NotificationCenter.class.getName());

    /** What changed. */
    public sealed interface Change {
        /** A new entry, or an existing one whose count, read or expired state changed. */
        record Upserted(Notification notification) implements Change {}
        /** A card should show now for this entry, or refresh if one is showing. */
        record CardRequested(Notification notification) implements Change {}
        record Removed(UUID id) implements Change {}
        record Cleared() implements Change {}
    }

    private final Executor ui;
    private final Clock clock;
    private final Supplier<Delivery.Presence> presence;
    private final Map<String, NotificationGroup> groups = new LinkedHashMap<>();
    /** Oldest first; a repeat moves to the end. */
    private final LinkedHashMap<UUID, Notification> history = new LinkedHashMap<>();
    private final LinkedHashSet<UUID> pending = new LinkedHashSet<>();
    private final List<Consumer<Change>> listeners = new ArrayList<>();
    private final ArrayDeque<Change> queue = new ArrayDeque<>();
    private boolean delivering;

    /**
     * @param ui runs {@link #submit}ted posts on the EDT ({@code SwingUtilities::invokeLater} in the application)
     * @param clock stamps posts and decides coalescing; its zone is the one times are shown in
     * @param presence whether Jasper is focused, in the background, or has no window open
     */
    public NotificationCenter(Executor ui, Clock clock, Supplier<Delivery.Presence> presence) {
        this.ui = Objects.requireNonNull(ui, "ui");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.presence = Objects.requireNonNull(presence, "presence");
    }

    public Clock clock() { return clock; }

    /** Declares a group, or returns the one already declared under its id. */
    public NotificationGroup register(NotificationGroup group) {
        requireEdt();
        Objects.requireNonNull(group, "group");
        return groups.computeIfAbsent(group.id(), id -> group);
    }

    public Optional<NotificationGroup> group(String id) {
        requireEdt();
        return Optional.ofNullable(groups.get(id));
    }

    /** Declared groups, in declaration order. */
    public List<NotificationGroup> groups() {
        requireEdt();
        return List.copyOf(groups.values());
    }

    /** How the group's notifications appear: its declared default; {@code OFF} for an undeclared group. */
    public DisplayMode mode(String groupId) {
        requireEdt();
        NotificationGroup group = groups.get(groupId);
        return group == null ? DisplayMode.OFF : group.defaultMode();
    }

    /** Posts, returning the entry it landed in (an earlier one for a repeat), or empty when its group drops it. */
    public Optional<UUID> post(NotificationRequest request) {
        requireEdt();
        Objects.requireNonNull(request, "request");
        if (!groups.containsKey(request.group())) {
            LOG.log(System.Logger.Level.WARNING, "A notification for an undeclared group was dropped: " + request.group());
            return Optional.empty();
        }
        Delivery.Outcome outcome = Delivery.route(mode(request.group()), presence.get());
        if (!outcome.record()) return Optional.empty();
        Instant now = clock.instant();
        Notification earlier = repeatOf(request, now);
        Notification entry;
        if (earlier != null) {
            entry = earlier.repeated(now);
            history.remove(entry.id());
            history.put(entry.id(), entry);
        } else {
            entry = new Notification(UUID.randomUUID(), request, now, 1, false, false);
            history.put(entry.id(), entry);
            trim();
        }
        if (outcome.card()) emit(new Change.Upserted(entry), new Change.CardRequested(entry));
        else {
            if (outcome.pending()) pending.add(entry.id());
            emit(new Change.Upserted(entry));
        }
        return Optional.of(entry.id());
    }

    /** Posts from any thread: at once on the EDT, otherwise through the UI executor. */
    public void submit(NotificationRequest request) {
        Objects.requireNonNull(request, "request");
        if (SwingUtilities.isEventDispatchThread()) post(request);
        else ui.execute(() -> post(request));
    }

    public Optional<Notification> find(UUID id) {
        requireEdt();
        return Optional.ofNullable(history.get(id));
    }

    /** Newest first. */
    public List<Notification> history() {
        requireEdt();
        var newestFirst = new ArrayList<>(history.values());
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    public int unread() {
        requireEdt();
        return (int) history.values().stream().filter(entry -> !entry.read()).count();
    }

    public int unreadErrors() {
        requireEdt();
        return (int) history.values().stream()
            .filter(entry -> !entry.read() && entry.request().severity() == Severity.ERROR).count();
    }

    public void markRead(UUID id) {
        requireEdt();
        update(id, entry -> entry.withRead(true));
    }

    public void markAllRead() {
        requireEdt();
        for (UUID id : List.copyOf(history.keySet())) markRead(id);
    }

    /** The poster says it no longer applies: its card closes and its actions stop working; the entry stays. */
    public void expire(UUID id) {
        requireEdt();
        pending.remove(id);
        update(id, Notification::withExpired);
    }

    public void remove(UUID id) {
        requireEdt();
        if (history.remove(id) == null) return;
        pending.remove(id);
        emit(new Change.Removed(id));
    }

    /** Clear All: every entry and every waiting card. */
    public void clear() {
        requireEdt();
        if (history.isEmpty()) return;
        history.clear();
        pending.clear();
        emit(new Change.Cleared());
    }

    /** Runs one of the entry's actions: the entry is read first, and expired afterwards when the action says so. */
    public void runAction(UUID id, int index) {
        requireEdt();
        Notification entry = history.get(id);
        if (entry == null || entry.expired() || index < 0 || index >= entry.request().actions().size()) return;
        NotificationAction action = entry.request().actions().get(index);
        markRead(id);
        contained(action.run(), "A notification action failed");
        if (action.expires()) expire(id);
    }

    /** A click on the body: the entry is read, and its default action runs when it has one (spec section 2). */
    public void activate(UUID id) {
        requireEdt();
        Notification entry = history.get(id);
        if (entry == null || entry.expired()) return;
        markRead(id);
        entry.request().activate().ifPresent(task -> contained(task, "A notification's default action failed"));
    }

    /** Jasper is focused again: cards that waited show now, in the order they were posted. */
    public void focusReturned() {
        requireEdt();
        List<UUID> waiting = List.copyOf(pending);
        pending.clear();
        for (UUID id : waiting) {
            Notification entry = history.get(id);
            if (entry != null) emit(new Change.CardRequested(entry));
        }
    }

    public Subscription onChange(Consumer<Change> listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    private Notification repeatOf(NotificationRequest request, Instant now) {
        for (Notification entry : history.values())
            if (!entry.expired() && entry.request().sameText(request) && !now.isAfter(entry.posted().plus(COALESCE))) return entry;
        return null;
    }

    private void trim() {
        while (history.size() > HISTORY_LIMIT) {
            UUID oldest = history.keySet().iterator().next();
            history.remove(oldest);
            pending.remove(oldest);
            emit(new Change.Removed(oldest));
        }
    }

    private void update(UUID id, UnaryOperator<Notification> change) {
        Notification entry = history.get(id);
        if (entry == null) return;
        Notification next = change.apply(entry);
        if (next.equals(entry)) return;
        history.put(id, next);
        emit(new Change.Upserted(next));
    }

    private void emit(Change... changes) {
        queue.addAll(List.of(changes));
        if (delivering) return;
        delivering = true;
        try {
            for (Change next = queue.poll(); next != null; next = queue.poll()) {
                if (next instanceof Change.CardRequested requested) {
                    Notification latest = history.get(requested.notification().id());
                    if (latest == null || latest.read() || latest.expired()) continue;
                    next = new Change.CardRequested(latest);
                }
                for (Consumer<Change> listener : List.copyOf(listeners)) {
                    try { listener.accept(next); }
                    catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A notification listener failed", failure); }
                }
            }
        } finally { delivering = false; }
    }

    private static void contained(Runnable task, String failed) {
        try { task.run(); }
        catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, failed, failure); }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("The notification center belongs to the EDT");
    }
}
