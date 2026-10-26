package dev.jasper.buddy.internal.model;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Bounded first-observed ordering and expansion for the retained deck. EDT only. */
public final class BuddyGroups {
    public record Identity(boolean grouped, BuddyNoticeId id) { }
    public record Entry(Identity identity, BuddyNotice.Group group, List<BuddyNotice> members) {
        public Entry { members = List.copyOf(members); }
    }
    private final Set<BuddyNoticeId> order = new LinkedHashSet<>();
    private final Set<Identity> sections = new LinkedHashSet<>();
    private final Set<Identity> collapsed = new LinkedHashSet<>();
    private BuddyNoticeId attention;

    private static Identity identity(BuddyNotice n) {
        return new Identity(n.group() != null, n.group() == null ? n.id() : n.group().id());
    }

    public List<Entry> reconcile(BuddyDeck deck) {
        var retained = deck.retainedNotices();
        order.retainAll(retained.stream().map(BuddyNotice::id).toList());
        sections.retainAll(retained.stream().map(BuddyGroups::identity).toList());
        collapsed.retainAll(sections);
        for (BuddyNotice n : retained) { order.add(n.id()); sections.add(identity(n)); }
        var visible = new LinkedHashMap<BuddyNoticeId, BuddyNotice>();
        for (BuddyNotice n : deck.notices()) visible.put(n.id(), n);
        var groups = new LinkedHashMap<Identity, List<BuddyNotice>>();
        for (Identity id : sections) groups.put(id, new ArrayList<>());
        for (BuddyNoticeId id : order) {
            BuddyNotice n = visible.get(id);
            if (n != null) groups.get(identity(n)).add(n);
        }
        return groups.entrySet().stream().filter(e -> !e.getValue().isEmpty())
            .map(e -> new Entry(e.getKey(), e.getValue().getFirst().group(), e.getValue())).toList();
    }

    public boolean expanded(Identity id) { return !collapsed.contains(id); }
    public void toggle(Identity id) { if (!collapsed.remove(id)) collapsed.add(id); }

    /** A synthetic display notice; its activation is always handled by the summary's owner. */
    public BuddyNotice summary(BuddyDeck deck) {
        List<BuddyNotice> visible = reconcile(deck).stream().flatMap(e -> e.members().stream()).toList();
        if (visible.isEmpty()) { attention = null; return null; }
        int priority = visible.stream().mapToInt(n -> priority(n.state())).max().orElse(0);
        BuddyNotice selected = visible.stream().filter(n -> priority(n.state()) == priority && n.id().equals(attention))
            .findFirst().orElseGet(() -> visible.stream().filter(n -> priority(n.state()) == priority).findFirst().orElseThrow());
        attention = priority > 0 ? selected.id() : null;
        String title;
        String detail;
        if (priority > 0 || visible.size() == 1) {
            title = priority > 0 || selected.group() == null ? selected.title() : selected.group().title();
            detail = label(selected.state());
            if (visible.size() > 1) detail += " · " + (visible.size() - 1) + " other " + (visible.size() == 2 ? "activity" : "activities");
        } else {
            long tasks = visible.stream().filter(n -> n.kind() == BuddyNotice.Kind.TASK).count();
            long connections = visible.size() - tasks;
            title = tasks == 0 ? count(connections, "connection") : count(tasks, "task")
                + (connections == 0 ? "" : " · " + count(connections, "connection"));
            detail = connections == 0 ? "Running" : "View activity";
        }
        String text = detail;
        return new BuddyNotice(selected.id(), selected.kind(), title, selected.state(), () -> text, () -> {});
    }

    private static String count(long count, String noun) { return count + " " + noun + (count == 1 ? "" : "s"); }
    private static int priority(BuddyNotice.State state) {
        return switch (state) { case NEEDS_INPUT -> 3; case DOWN -> 2; case DEGRADED -> 1; default -> 0; };
    }
    public static String label(BuddyNotice.State state) {
        return switch (state) {
            case RUNNING -> "Running"; case NEEDS_INPUT -> "Needs input"; case UP -> "Connected";
            case DOWN -> "Disconnected"; case DEGRADED -> "Degraded";
        };
    }
}
