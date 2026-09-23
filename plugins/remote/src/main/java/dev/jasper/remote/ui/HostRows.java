package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.RemoteHost;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/** The panel's list model: group headers with counts and hosts, favorites first, "Other" last. */
public final class HostRows {
    public static final String OTHER = "Other";

    public sealed interface Row { }
    public record Group(String name, int count, boolean collapsed) implements Row { }
    public record Host(RemoteHost host) implements Row { }
    public record Error(String message) implements Row { }

    private HostRows() { }

    public static boolean matches(RemoteHost host, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        return (host.name() + " " + host.hostname() + " " + host.username() + " " + host.group()).toLowerCase(Locale.ROOT).contains(q);
    }

    /** Collapsed groups hide their hosts unless a search is active; the search never changes {@code collapsed}. */
    public static List<Row> rows(List<RemoteHost> hosts, String query, Set<String> collapsed) {
        boolean searching = !query.strip().isEmpty();
        var groups = new TreeMap<String, List<RemoteHost>>(Comparator.comparing((String name) -> name.equals(OTHER) ? 1 : 0).thenComparing(name -> name.toLowerCase(Locale.ROOT)));
        for (RemoteHost host : hosts) {
            if (!matches(host, query)) continue;
            groups.computeIfAbsent(host.group().isEmpty() ? OTHER : host.group(), name -> new ArrayList<>()).add(host);
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
}
