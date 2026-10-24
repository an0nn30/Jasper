package dev.jasper.buddy.internal.model;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Immutable, newest-first display projection of the visible deck. */
public final class BuddyGroups {
    private BuddyGroups() { }
    /** Group and notice identities occupy separate namespaces. */
    public record Identity(boolean grouped, BuddyNoticeId id) { }
    /** One column entry and its ordered underlying notices. */
    public record Entry(Identity identity, BuddyNotice.Group group, List<BuddyNotice> members) {
        public Entry { members = List.copyOf(members); }
        public int backingLayers() { return Math.min(2, members.size() - 1); }
        public BuddyNotice collapsed() {
            BuddyNotice first = members.getFirst();
            if (group == null) return first;
            return new BuddyNotice(first.id(), first.kind(), group.title(), first.state(),
                members.size() > 1 ? () -> members.size() + " running" : first.detail(), first.activate(), group);
        }
    }
    public static List<Entry> project(List<BuddyNotice> notices) {
        var groups = new LinkedHashMap<Identity, List<BuddyNotice>>();
        for (BuddyNotice notice : notices) {
            var identity = new Identity(notice.group() != null, notice.group() == null ? notice.id() : notice.group().id());
            groups.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(notice);
        }
        return groups.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue().getFirst().group(), e.getValue())).toList();
    }
}
