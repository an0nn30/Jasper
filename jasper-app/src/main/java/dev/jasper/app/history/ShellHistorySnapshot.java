package dev.jasper.app.history;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable merged history: newest first, one entry per command text, at most {@link #MAX_ENTRIES}. */
public record ShellHistorySnapshot(List<ShellHistoryEntry> entries, Set<String> shells) {
    static final int MAX_ENTRIES = 50_000;
    static final ShellHistorySnapshot EMPTY = new ShellHistorySnapshot(List.of(), Set.of());

    public ShellHistorySnapshot {
        entries = List.copyOf(entries);
        shells = Set.copyOf(shells);
    }

    /** One history file's entries, oldest first, with its last-modified time in epoch seconds (0 when unknown). */
    record Source(List<ShellHistoryEntry> entries, long modified) {
        Source {
            entries = List.copyOf(entries);
        }
    }

    private record Keyed(ShellHistoryEntry entry, long rank, long sequence) {}

    /**
     * Each source's list is oldest first. An entry with a known timestamp ranks by it; one without ranks
     * just before its file was last written, stepping back a second per entry from the end. bash records
     * no timestamps unless HISTTIMEFORMAT is set, and treating its 0 as a real time buried every bash
     * command beneath every timestamped zsh one however recently it ran. Live entries beat file entries
     * at the same rank. The most recent occurrence of a command wins and the shells that ran it merge.
     */
    static ShellHistorySnapshot build(Collection<Source> perSource, List<ShellHistoryEntry> live, int cap) {
        var keyed = new ArrayList<Keyed>();
        long sequence = 0;
        for (Source source : perSource) {
            List<ShellHistoryEntry> entries = source.entries();
            for (int i = 0; i < entries.size(); i++) {
                ShellHistoryEntry entry = entries.get(i);
                long rank = entry.timestamp() > 0 ? entry.timestamp()
                    : Math.max(0, source.modified() - (entries.size() - i));
                keyed.add(new Keyed(entry, rank, sequence++));
            }
        }
        for (int i = 0; i < live.size(); i++)
            keyed.add(new Keyed(live.get(i), live.get(i).timestamp(), Long.MAX_VALUE - live.size() + i));
        keyed.sort(Comparator.comparingLong(Keyed::rank).thenComparingLong(Keyed::sequence).reversed());
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
