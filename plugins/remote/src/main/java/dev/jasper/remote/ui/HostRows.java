package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/** The panel's list model: WSL first, then alphabetical hosts in groups, with the default group last. */
public final class HostRows {
    public static final String OTHER = "Other";
    /** The WSL group's title. */
    public static final String WSL = "WSL";
    public sealed interface Row { }

    /**
     * A group header. {@code wsl} marks the WSL group, whose collapsed state is a flag of its own, never an entry in the
     * host groups' collapsed set, so a host group of any name (even "wsl") cannot share it.
     */
    public record Group(String name, int count, boolean collapsed, boolean wsl) implements Row {
        /** A host group. */
        public Group(String name, int count, boolean collapsed) { this(name, count, collapsed, false); }
    }

    public record Host(RemoteHost host) implements Row { }

    /** A WSL distribution the application detected, with the panes Remote opened for it that still run. Never saved. */
    public record Distro(ShellProfileInfo profile, int sessions) implements Row { }

    public record Error(String message) implements Row { }

    private HostRows() { }

    public static boolean matches(RemoteHost host, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        return (host.name() + " " + host.hostname() + " " + host.username() + " " + host.group()).toLowerCase(Locale.ROOT).contains(q);
    }

    /** A distribution matches on its name alone, as the {@code >ssh} palette scope searches it. */
    public static boolean matchesName(ShellProfileInfo profile, String query) {
        return profile.name().toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT));
    }

    /** In the panel, a distribution matches on its name or on the group's name. */
    public static boolean matches(ShellProfileInfo profile, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        return (profile.name() + " " + WSL).toLowerCase(Locale.ROOT).contains(q);
    }

    /** Collapsed groups hide their hosts unless a search is active; the search never changes {@code collapsed}. */
    public static List<Row> rows(List<RemoteHost> hosts, String query, Set<String> collapsed) {
        return rows(hosts, query, collapsed, OTHER);
    }

    public static List<Row> rows(List<RemoteHost> hosts, String query, Set<String> collapsed, String defaultGroup) {
        boolean searching = !query.strip().isEmpty();
        var groups = new TreeMap<String, List<RemoteHost>>(Comparator.comparing((String name) -> name.equals(defaultGroup) ? 1 : 0).thenComparing(name -> name.toLowerCase(Locale.ROOT)));
        for (RemoteHost host : hosts) {
            if (!matches(host, query) && !(host.group().isEmpty() && defaultGroup.toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT)))) continue;
            groups.computeIfAbsent(host.group().isEmpty() ? defaultGroup : host.group(), name -> new ArrayList<>()).add(host);
        }
        var rows = new ArrayList<Row>();
        boolean flat = defaultGroup.equals(OTHER) && hosts.stream().allMatch(host -> host.group().isEmpty());
        for (var entry : groups.entrySet()) {
            boolean hidden = !searching && collapsed.contains(entry.getKey());
            // Keep a restored collapsed header reachable; a renamed default group retains its identity too.
            if (!flat || hidden) rows.add(new Group(entry.getKey(), entry.getValue().size(), hidden));
            if (hidden) continue;
            entry.getValue().stream().sorted(Comparator.comparing((RemoteHost host) -> host.name().toLowerCase(Locale.ROOT)).thenComparing(RemoteHost::name))
                .forEach(host -> rows.add(new Host(host)));
        }
        return List.copyOf(rows);
    }

    /**
     * As {@link #rows(List, String, Set, String)}, led by a "WSL" group of the matching WSL distributions in the order
     * given; other kinds of profile are ignored, and there is no group when no distribution matches. The WSL group is
     * collapsed by {@code wslCollapsed} alone; {@code collapsed} names host groups only.
     */
    public static List<Row> rows(List<RemoteHost> hosts, String query, Set<String> collapsed, String defaultGroup, List<Distro> distros,
                                 boolean wslCollapsed) {
        boolean searching = !query.strip().isEmpty();
        var rows = new ArrayList<Row>();
        List<Distro> shown = distros.stream()
            .filter(row -> row.profile().kind() == ShellProfileInfo.Kind.WSL && matches(row.profile(), query)).toList();
        if (!shown.isEmpty()) {
            boolean hidden = !searching && wslCollapsed;
            rows.add(new Group(WSL, shown.size(), hidden, true));
            if (!hidden) rows.addAll(shown);
        }
        rows.addAll(rows(hosts, query, collapsed, defaultGroup));
        return List.copyOf(rows);
    }
}
