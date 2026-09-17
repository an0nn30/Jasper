package dev.jasper.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    /** Adds a notice, or replaces the one with the same source and key and promotes it to the top. */
    void post(BuddyNotice notice) {
        Objects.requireNonNull(notice, "notice");
        notices.removeIf(existing -> existing.sameAs(notice.source(), notice.key()));
        notices.addFirst(notice);
        while (notices.size() > MAX_NOTICES) notices.removeLast();
    }

    /** Removes one notice; true when there was one to remove. */
    boolean dismiss(String source, Object key) {
        return notices.removeIf(notice -> notice.sameAs(source, key));
    }

    void clear() { notices.clear(); }

    /**
     * Its origin went away. The notice keeps its place, its outcome and what it said, and stops
     * responding, because there is nowhere left to go.
     */
    void orphan(String source, Object key) {
        notices.replaceAll(notice -> notice.sameAs(source, key)
            ? new BuddyNotice(notice.source(), notice.key(), notice.title(), notice.state(),
                notice.detail(), null)
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
            ? new BuddyNotice(notice.source(), notice.key(), notice.title(), notice.state(),
                () -> finalDetail, null)
            : notice);
    }

    /** Newest first. A copy: the caller may be painting while a producer posts. */
    List<BuddyNotice> notices() { return List.copyOf(notices); }

    boolean isEmpty() { return notices.isEmpty(); }

    int size() { return notices.size(); }
}
