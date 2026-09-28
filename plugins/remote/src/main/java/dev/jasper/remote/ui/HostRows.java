package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/** The panel's list model: the WSL group first, then group headers with counts and hosts, favorites first, "Other" last. */
public final class HostRows {
    public static final String OTHER = "Other";
    /** The WSL group's title. */
    public static final String WSL = "WSL";
    /** The WSL group's entry in the collapsed set, as {@code panel-state.toml} stores it. */
    public static final String WSL_KEY = "wsl";

    public sealed interface Row { }

    /** A group header; {@code key} is its entry in the collapsed set: the name, or {@link #WSL_KEY} for the WSL group. */
    public record Group(String name, int count, boolean collapsed, String key) implements Row {
        public Group(String name, int count, boolean collapsed) { this(name, count, collapsed, name); }

        /** Whether this is the WSL group, not a host group that happens to share its name. */
        public boolean wsl() { return WSL.equals(name) && WSL_KEY.equals(key); }
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

    /** A distribution matches on its name or on the group's name. */
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
        for (var entry : groups.entrySet()) {
            boolean hidden = !searching && collapsed.contains(entry.getKey());
            rows.add(new Group(entry.getKey(), entry.getValue().size(), hidden));
            if (hidden) continue;
            entry.getValue().stream().sorted(Comparator.comparing((RemoteHost host) -> !host.favorite()).thenComparing(host -> host.name().toLowerCase(Locale.ROOT)))
                .forEach(host -> rows.add(new Host(host)));
        }
        return List.copyOf(rows);
    }

    /**
     * As {@link #rows(List, String, Set, String)}, led by a "WSL" group of the matching WSL distributions in the order
     * given; other kinds of profile are ignored, and there is no group when no distribution matches.
     */
    public static List<Row> rows(List<RemoteHost> hosts, String query, Set<String> collapsed, String defaultGroup, List<Distro> distros) {
        boolean searching = !query.strip().isEmpty();
        var rows = new ArrayList<Row>();
        List<Distro> shown = distros.stream()
            .filter(row -> row.profile().kind() == ShellProfileInfo.Kind.WSL && matches(row.profile(), query)).toList();
        if (!shown.isEmpty()) {
            boolean hidden = !searching && collapsed.contains(WSL_KEY);
            rows.add(new Group(WSL, shown.size(), hidden, WSL_KEY));
            if (!hidden) rows.addAll(shown);
        }
        rows.addAll(rows(hosts, query, collapsed, defaultGroup));
        return List.copyOf(rows);
    }
}
