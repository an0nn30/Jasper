package dev.jasper.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable merged history: newest first, one entry per command text, at most {@link #MAX_ENTRIES}. */
record ShellHistorySnapshot(List<ShellHistoryEntry> entries, Set<String> shells) {
    static final int MAX_ENTRIES = 50_000;
    static final ShellHistorySnapshot EMPTY = new ShellHistorySnapshot(List.of(), Set.of());

    ShellHistorySnapshot {
        entries = List.copyOf(entries);
        shells = Set.copyOf(shells);
    }

    private record Keyed(ShellHistoryEntry entry, long timestamp, long sequence) {}

    /**
     * Each per-source list is oldest first. Known timestamps order first; entries without one keep their
     * file order behind them. Live entries beat file entries at the same timestamp. The most recent
     * occurrence of a command wins and the shells that ran it are merged into it.
     */
    static ShellHistorySnapshot build(Collection<List<ShellHistoryEntry>> perSource, List<ShellHistoryEntry> live, int cap) {
        var keyed = new ArrayList<Keyed>();
        long sequence = 0;
        for (List<ShellHistoryEntry> list : perSource)
            for (ShellHistoryEntry entry : list) keyed.add(new Keyed(entry, entry.timestamp(), sequence++));
        for (int i = 0; i < live.size(); i++)
            keyed.add(new Keyed(live.get(i), live.get(i).timestamp(), Long.MAX_VALUE - live.size() + i));
        keyed.sort(Comparator.comparingLong(Keyed::timestamp).thenComparingLong(Keyed::sequence).reversed());
        Map<String, ShellHistoryEntry> byCommand = new LinkedHashMap<>();
        for (Keyed item : keyed) byCommand.merge(item.entry().command(), item.entry(), ShellHistorySnapshot::merge);
        List<ShellHistoryEntry> ordered = byCommand.values().stream().limit(Math.max(0, cap)).toList();
        var shells = new HashSet<String>();
        for (ShellHistoryEntry entry : ordered) shells.addAll(entry.shells());
        return new ShellHistorySnapshot(ordered, shells);
    }

    private static ShellHistoryEntry merge(ShellHistoryEntry newest, ShellHistoryEntry older) {
        var shells = new HashSet<>(newest.shells());
        shells.addAll(older.shells());
        return new ShellHistoryEntry(newest.command(), newest.timestamp(), shells,
            newest.directory() != null ? newest.directory() : older.directory(),
            newest.exitStatus() != null ? newest.exitStatus() : older.exitStatus());
    }
}
