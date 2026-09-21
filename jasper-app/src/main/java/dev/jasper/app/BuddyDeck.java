package dev.jasper.app;

import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.notice.BuddyNotice;

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

    private final List<BuddyNotice> notices = new ArrayList<>();
    /** Notices the reader has already looked at. About the reader, not about the thing. */
    private final Set<BuddyNoticeId> seen = new HashSet<>();
    /**
     * Bumped only by {@link #post}. A surface compares it to the last value it drew to know a notice
     * genuinely arrived, rather than being told separately — being told separately is exactly how
     * the arrival animation came to have no production caller at all.
     */
    private int generation;
    private List<BuddyNotice> columnSnapshot, noticeSnapshot;

    private void changed() { columnSnapshot = null; noticeSnapshot = null; }

    /** Adds a notice, or replaces the one with the same source and key and promotes it to the top. */
    void post(BuddyNotice notice) {
        Objects.requireNonNull(notice, "notice");
        notices.removeIf(existing -> existing.sameAs(notice.id()));
        // A new state is new news: a tunnel that drops after you acknowledged it must speak up again.
        seen.remove(notice.id());
        notices.addFirst(notice);
        generation++;
        changed();
        while (notices.size() > MAX_NOTICES) seen.remove(idOf(notices.removeLast()));
    }

    /** A live producer changed its wording; this is neither a new notice nor new attention. */
    boolean updateTitle(String source, Object key, String title) {
        for (int i = 0; i < notices.size(); i++) {
            BuddyNotice notice = notices.get(i);
            if (!notice.sameAs(new BuddyNoticeId(source, key))) continue;
            if (notice.title().equals(title)) return false;
            changed();
            notices.set(i, new BuddyNotice(notice.id(), notice.kind(), title, notice.state(), notice.detail(), notice.activate()));
            return true;
        }
        return false;
    }

    /** Removes one notice; true when there was one to remove. */
    boolean dismiss(String source, Object key) {
        changed();
        seen.remove(new BuddyNoticeId(source, key));
        return notices.removeIf(notice -> notice.sameAs(new BuddyNoticeId(source, key)));
    }

    void clear() {
        changed();
        notices.clear();
        seen.clear();
    }

    /** You looked at it. Only matters once it stops being live. */
    void acknowledge(String source, Object key) {
        changed();
        if (notices.stream().anyMatch(notice -> notice.sameAs(new BuddyNoticeId(source, key)))) seen.add(new BuddyNoticeId(source, key));
    }

    boolean acknowledged(String source, Object key) { return seen.contains(new BuddyNoticeId(source, key)); }

    /** What belongs above his head: still happening, or not yet seen. Newest first. */
    List<BuddyNotice> column() {
        if (columnSnapshot == null)
            columnSnapshot = notices.stream().filter(notice -> notice.live() || !seen.contains(idOf(notice))).toList();
        return columnSnapshot;
    }

    private static BuddyNoticeId idOf(BuddyNotice notice) { return notice.id(); }

    /**
     * Its origin went away. The notice keeps its place, its outcome and what it said, and stops
     * responding, because there is nowhere left to go.
     */
    void orphan(String source, Object key) {
        changed();
        notices.replaceAll(notice -> notice.sameAs(new BuddyNoticeId(source, key))
            ? new BuddyNotice(notice.id(), notice.kind(), notice.title(), notice.state(), notice.detail(), null)
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
        changed();
        notices.replaceAll(notice -> notice.sameAs(new BuddyNoticeId(source, key))
            ? new BuddyNotice(notice.id(), notice.kind(), notice.title(), notice.state(), () -> finalDetail, null)
            : notice);
    }

    /** Newest first. A copy: the caller may be painting while a producer posts. */
    List<BuddyNotice> notices() {
        if (noticeSnapshot == null) noticeSnapshot = List.copyOf(notices);
        return noticeSnapshot;
    }

    /** Changes when, and only when, something is posted. */
    int generation() { return generation; }

    boolean isEmpty() { return notices.isEmpty(); }

    int size() { return notices.size(); }
}
