package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.ToIntFunction;

/**
 * Pure: compares what this process selected at launch with what the next launch would select from the
 * disk as it is now, after pending removals and installs. No I/O, no class loading.
 */
final class PluginCatalog {
    /** What this process found and chose; {@code selected} maps id to the version chosen to load. */
    record Launch(List<PluginCandidate> candidates, List<PluginStatus> statuses, Map<String, String> selected, boolean safeMode) {
        Launch { candidates = List.copyOf(candidates); statuses = List.copyOf(statuses); selected = Map.copyOf(selected); }
    }

    /** The disk now: installed candidates of every origin, staged installs by id, and the saved state. */
    record Disk(List<PluginCandidate> candidates, Map<String, PluginCandidate> pending, Map<String, PluginStateStore.Entry> state) {
        Disk { candidates = List.copyOf(candidates); pending = Map.copyOf(pending); state = Map.copyOf(state); }
    }

    private PluginCatalog() { }

    static Map<String, String> versions(List<PluginCandidate> load) {
        Map<String, String> result = new TreeMap<>();
        for (PluginCandidate candidate : load) result.put(candidate.id(), candidate.descriptor().version().toString());
        return result;
    }

    /** The candidates the next launch will find once maintenance has run. */
    private static List<PluginCandidate> afterMaintenance(Disk disk) {
        List<PluginCandidate> next = new ArrayList<>();
        for (PluginCandidate candidate : disk.candidates()) {
            boolean user = candidate.origin() == PluginCandidate.Origin.USER;
            if (user && (removed(disk, candidate.id()) || disk.pending().containsKey(candidate.id()))) continue;
            next.add(candidate);
        }
        disk.pending().forEach((id, candidate) -> { if (!removed(disk, id)) next.add(candidate); });
        return next;
    }

    private static boolean removed(Disk disk, String id) {
        PluginStateStore.Entry entry = disk.state().get(id);
        return entry != null && entry.remove();
    }

    private static Optional<PluginCandidate> winner(List<PluginCandidate> candidates, String id) {
        return candidates.stream().filter(candidate -> candidate.id().equals(id)).max(PluginResolver.ORDER);
    }

    static Optional<PluginCandidate> subject(Disk disk, String id) { return winner(afterMaintenance(disk), id); }

    static PluginRuntime.Snapshot compute(Launch launch, Disk disk, Version sdk, ToIntFunction<String> errors, java.nio.file.Path userDirectory) {
        List<PluginCandidate> next = afterMaintenance(disk);
        Map<String, PluginStateStore.Entry> nextState = new TreeMap<>(disk.state());
        nextState.values().removeIf(PluginStateStore.Entry::remove);
        PluginResolver.Resolution resolution = PluginResolver.resolve(next, nextState, sdk, launch.safeMode());
        Map<String, String> willRun = versions(resolution.load());

        Set<String> ids = new TreeSet<>();
        launch.candidates().forEach(candidate -> ids.add(candidate.id()));
        disk.candidates().forEach(candidate -> ids.add(candidate.id()));
        ids.addAll(disk.pending().keySet());

        List<PluginRuntime.Row> rows = new ArrayList<>();
        for (String id : ids) {
            PluginCandidate shown = winner(next, id).or(() -> winner(launch.candidates(), id))
                .or(() -> winner(disk.candidates(), id)).orElseThrow();
            PluginDescriptor descriptor = shown.descriptor();
            PluginStateStore.Entry entry = disk.state().get(id);
            boolean user = shown.origin() == PluginCandidate.Origin.USER;
            boolean pendingRemoval = removed(disk, id);
            boolean pendingInstall = disk.pending().containsKey(id) && !pendingRemoval;
            List<String> capabilities = new ArrayList<>(new TreeSet<>(descriptor.capabilities()));
            List<String> unconsented = new ArrayList<>(capabilities);
            if (!user) unconsented.clear();
            else if (entry != null) unconsented.removeAll(entry.consented());
            boolean needsConsent = user && !pendingRemoval && (entry == null || !unconsented.isEmpty());

            Optional<PluginStatus> atLaunch = status(launch.statuses(), id);
            Optional<PluginStatus> rejectedNext = status(resolution.rejected(), id);
            String state = atLaunch.map(status -> status.state().name())
                .orElseGet(() -> rejectedNext.map(status -> status.state().name()).orElse("NOT_LOADED"));
            String reason = atLaunch.map(PluginStatus::reason).orElseGet(() -> rejectedNext.map(PluginStatus::reason).orElse(""));

            rows.add(new PluginRuntime.Row(id, descriptor.name(), descriptor.version().toString(), descriptor.description(),
                descriptor.vendor(), switch (shown.origin()) { case BUNDLED -> "Bundled"; case USER -> "Installed"; case DEV -> "Development"; },
                state, reason, capabilities, unconsented, requires(descriptor), errors.applyAsInt(id),
                entry == null || entry.enabled(), needsConsent,
                !pendingRemoval && !(user && entry == null), user, pendingRemoval, pendingInstall,
                pending(id, launch, willRun, rejectedNext, pendingRemoval, pendingInstall, descriptor),
                PluginSettingsFiles.folder(userDirectory, id), PluginSettingsFiles.file(userDirectory, id), PluginSettingsFiles.data(userDirectory, id)));
        }
        boolean restartNeeded = !launch.safeMode()
            && (!willRun.equals(launch.selected()) || disk.pending().keySet().stream().anyMatch(id -> !removed(disk, id)));
        return new PluginRuntime.Snapshot(rows, restartNeeded, launch.safeMode());
    }

    /** A superseded copy has a status of its own; the row is about the copy that won. */
    private static Optional<PluginStatus> status(List<PluginStatus> statuses, String id) {
        return statuses.stream().filter(status -> status.id().equals(id))
            .filter(status -> !status.reason().startsWith("superseded by")).findFirst();
    }

    private static List<String> requires(PluginDescriptor descriptor) {
        return descriptor.requires().stream().map(requirement -> requirement.id()
            + (requirement.version().toString().equals("any") ? "" : " " + requirement.version())
            + (requirement.optional() ? " (optional)" : "")).toList();
    }

    private static String pending(String id, Launch launch, Map<String, String> willRun, Optional<PluginStatus> rejectedNext,
                                  boolean pendingRemoval, boolean pendingInstall, PluginDescriptor descriptor) {
        if (pendingRemoval) return "Will be removed at restart";
        if (pendingInstall) return "Version " + descriptor.version() + " will be installed at restart";
        if (launch.safeMode()) return "";
        String runs = launch.selected().get(id), next = willRun.get(id);
        if (runs != null && next == null)
            return "Will not load after restart" + rejectedNext.map(status -> ": " + status.reason()).orElse("");
        if (runs == null && next != null) return "Will load after restart";
        if (runs != null && !runs.equals(next)) return "Version " + next + " loads after restart";
        return "";
    }
}
