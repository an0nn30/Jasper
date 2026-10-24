package dev.jasper.buddy.internal.model;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/**
 * Bounded retained live-work model: newest-first replacement, context/title changes, dismissal and clear. Notices have no expiry timer;
 * a producer dismisses its notice when the work ends, and the user may dismiss one or clear them all. A producer
 * supplies identity, state and callbacks, so the model knows nothing about panes, command thresholds or persistence.
 *
 * <p>EDT only, like the rest of the buddy's state.
 */
public final class BuddyDeck {
    /** Bounded so a long session cannot grow it without limit; the oldest fall off silently. */
    public static final int MAX_NOTICES = 50;

    private final List<BuddyNotice> notices = new ArrayList<>();
    private final Set<BuddyNoticeId> suppressed = new HashSet<>();
    /**
     * Bumped only by {@link #post}; distinguishes a new post from a title change. Retained as internal model
     * bookkeeping; presentation refreshes through the facade and keys motion by notice identity.
     */
    private int generation;
    private List<BuddyNotice> snapshot;

    /** Adds a notice, or replaces the one with the same source and key and promotes it to the top. */
    public void post(BuddyNotice notice) {
        post(notice, true);
    }

    /** Retains a notice and atomically sets its visible projection membership. */
    public void post(BuddyNotice notice, boolean visible) {
        Objects.requireNonNull(notice, "notice");
        if (visible) suppressed.remove(notice.id()); else suppressed.add(notice.id());
        notices.removeIf(existing -> existing.sameAs(notice.id()));
        notices.addFirst(notice);
        generation++;
        snapshot = null;
        while (notices.size() > MAX_NOTICES) suppressed.remove(notices.removeLast().id());
    }

    /** A live producer changed its wording; this is neither a new notice nor news. */
    public boolean updateTitle(BuddyNoticeId id, String title) {
        Objects.requireNonNull(title, "title");
        if (title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        Objects.requireNonNull(id, "id");
        for (int i = 0; i < notices.size(); i++) {
            BuddyNotice notice = notices.get(i);
            if (!notice.sameAs(id)) continue;
            if (notice.title().equals(title)) return false;
            snapshot = null;
            notices.set(i, new BuddyNotice(notice.id(), notice.kind(), title, notice.state(), notice.detail(), notice.activate(), notice.group()));
            return true;
        }
        return false;
    }

    /** Updates retained context in place without promotion; missing IDs are harmless. */
    public void updateContext(BuddyNoticeId id, BuddyNotice.Group group, boolean visible) {
        Objects.requireNonNull(id, "id");
        for (int i = 0; i < notices.size(); i++) {
            BuddyNotice notice = notices.get(i);
            if (!notice.sameAs(id)) continue;
            boolean groupChanged = !Objects.equals(notice.group(), group);
            boolean visibilityChanged = visible == suppressed.contains(id);
            if (!groupChanged && !visibilityChanged) return;
            if (groupChanged) notices.set(i, new BuddyNotice(notice.id(), notice.kind(), notice.title(),
                notice.state(), notice.detail(), notice.activate(), group));
            if (visible) suppressed.remove(id); else suppressed.add(id);
            snapshot = null;
            return;
        }
    }

    /** Removes one notice; true when there was one to remove. */
    public boolean dismiss(BuddyNoticeId id) {
        Objects.requireNonNull(id, "id");
        boolean removed = notices.removeIf(notice -> notice.sameAs(id));
        suppressed.remove(id);
        if (removed) snapshot = null;
        return removed;
    }

    public void clear() {
        notices.clear();
        suppressed.clear();
        snapshot = null;
    }

    /**
     * Cached immutable newest-first visible snapshot, which the column and the drawer both read. Later EDT mutations invalidate
     * the cache without changing an earlier snapshot.
     */
    public List<BuddyNotice> notices() {
        if (snapshot == null) snapshot = notices.stream().filter(notice -> !suppressed.contains(notice.id())).toList();
        return snapshot;
    }

    /** Changes when, and only when, something is posted. */
    public int generation() { return generation; }

    public boolean isEmpty() { return notices().isEmpty(); }

    public int size() { return notices().size(); }
}
