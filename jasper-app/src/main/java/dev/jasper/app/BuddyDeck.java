package dev.jasper.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The notification drawer: notices newest first, nothing auto-hides, cleared only by the user or by
 * quitting. It knows nothing about panes, tabs or commands — a producer supplies the identity — so a
 * later sftp transfer or SSH session posts here without the deck changing at all.
 *
 * <p>EDT only, like the rest of the buddy's state.
 */
final class BuddyDeck {
    /** Bounded so a long session cannot grow it without limit; the oldest fall off silently. */
    static final int MAX_NOTICES = 50;

    /** Identity of a notice, so acknowledgements cannot collide across producers. */
    private record Id(String source, Object key) {}

    private final List<BuddyNotice> notices = new ArrayList<>();
    /** Notices the reader has already looked at. About the reader, not about the thing. */
    private final Set<Id> seen = new HashSet<>();
    /**
     * Bumped only by {@link #post}. A surface compares it to the last value it drew to know a notice
     * genuinely arrived, rather than being told separately — being told separately is exactly how
     * the arrival animation came to have no production caller at all.
     */
    private int generation;

    /** Adds a notice, or replaces the one with the same source and key and promotes it to the top. */
    void post(BuddyNotice notice) {
        Objects.requireNonNull(notice, "notice");
        notices.removeIf(existing -> existing.sameAs(notice.source(), notice.key()));
        // A new state is new news: a tunnel that drops after you acknowledged it must speak up again.
        seen.remove(new Id(notice.source(), notice.key()));
        notices.addFirst(notice);
        generation++;
        while (notices.size() > MAX_NOTICES) seen.remove(idOf(notices.removeLast()));
    }

    /** A live producer changed its wording; this is neither a new notice nor new attention. */
    boolean updateTitle(String source, Object key, String title) {
        for (int i = 0; i < notices.size(); i++) {
            BuddyNotice notice = notices.get(i);
            if (!notice.sameAs(source, key)) continue;
            if (notice.title().equals(title)) return false;
            notices.set(i, new BuddyNotice(notice.source(), notice.key(), notice.kind(), title,
                notice.state(), notice.detail(), notice.activate()));
            return true;
        }
        return false;
    }

    /** Removes one notice; true when there was one to remove. */
    boolean dismiss(String source, Object key) {
        seen.remove(new Id(source, key));
        return notices.removeIf(notice -> notice.sameAs(source, key));
    }

    void clear() {
        notices.clear();
        seen.clear();
    }

    /** You looked at it. Only matters once it stops being live. */
    void acknowledge(String source, Object key) {
        if (notices.stream().anyMatch(notice -> notice.sameAs(source, key))) seen.add(new Id(source, key));
    }

    boolean acknowledged(String source, Object key) { return seen.contains(new Id(source, key)); }

    /** What belongs above his head: still happening, or not yet seen. Newest first. */
    List<BuddyNotice> column() {
        return notices.stream().filter(notice -> notice.live() || !seen.contains(idOf(notice))).toList();
    }

    private static Id idOf(BuddyNotice notice) { return new Id(notice.source(), notice.key()); }

    /**
     * Its origin went away. The notice keeps its place, its outcome and what it said, and stops
     * responding, because there is nowhere left to go.
     */
    void orphan(String source, Object key) {
        notices.replaceAll(notice -> notice.sameAs(source, key)
            ? new BuddyNotice(notice.source(), notice.key(), notice.kind(), notice.title(),
                notice.state(), notice.detail(), null)
            : notice);
    }

    /**
     * As {@link #orphan(String, Object)}, and freezes what it says. Only for a notice that was still
     * running: it would otherwise tick forever against a process that no longer exists. A notice that
     * had already finished keeps its own words — rewriting them would lose what happened, which is
     * the very thing orphaning is shaped to preserve.
     */
    void orphan(String source, Object key, String finalDetail) {
        Objects.requireNonNull(finalDetail, "finalDetail");
        notices.replaceAll(notice -> notice.sameAs(source, key)
            ? new BuddyNotice(notice.source(), notice.key(), notice.kind(), notice.title(),
                notice.state(), () -> finalDetail, null)
            : notice);
    }

    /** Newest first. A copy: the caller may be painting while a producer posts. */
    List<BuddyNotice> notices() { return List.copyOf(notices); }

    /** Changes when, and only when, something is posted. */
    int generation() { return generation; }

    boolean isEmpty() { return notices.isEmpty(); }

    int size() { return notices.size(); }
}
