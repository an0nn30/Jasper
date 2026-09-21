package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Pure selection and ordering: no I/O, no class loading. */
final class PluginResolver {
    record Resolution(List<PluginCandidate> load, List<PluginStatus> rejected) {
        Resolution { load = List.copyOf(load); rejected = List.copyOf(rejected); }
    }

    private PluginResolver() { }

    static Resolution resolve(List<PluginCandidate> candidates, Map<String, PluginStateStore.Entry> state,
                              Version sdk, boolean safeMode) {
        List<PluginStatus> rejected = new ArrayList<>();
        Map<String, PluginCandidate> chosen = new TreeMap<>();
        for (PluginCandidate candidate : candidates) {
            if (safeMode && candidate.origin() == PluginCandidate.Origin.USER) {
                rejected.add(PluginStatus.of(candidate, PluginStatus.State.DISABLED, "safe mode"));
                continue;
            }
            PluginCandidate rival = chosen.get(candidate.id());
            if (rival == null) { chosen.put(candidate.id(), candidate); continue; }
            boolean wins = ORDER.compare(candidate, rival) > 0;
            PluginCandidate winner = wins ? candidate : rival, loser = wins ? rival : candidate;
            chosen.put(candidate.id(), winner);
            rejected.add(PluginStatus.of(loser, PluginStatus.State.SKIPPED,
                "superseded by version " + winner.descriptor().version() + " from " + winner.origin()));
        }
        for (PluginCandidate candidate : List.copyOf(chosen.values())) {
            PluginStatus status = admit(candidate, state.get(candidate.id()), sdk);
            if (status != null) { chosen.remove(candidate.id()); rejected.add(status); }
        }
        // Members of a cycle first, so their dependents are then explained by the missing dependency.
        for (String id : cycleMembers(chosen)) {
            rejected.add(PluginStatus.of(chosen.remove(id), PluginStatus.State.SKIPPED, "dependency cycle"));
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (PluginCandidate candidate : List.copyOf(chosen.values())) {
                String problem = unmet(candidate, chosen);
                if (problem == null) continue;
                chosen.remove(candidate.id());
                rejected.add(PluginStatus.of(candidate, PluginStatus.State.SKIPPED, problem));
                changed = true;
            }
        }
        return new Resolution(order(chosen), rejected);
    }

    /** Higher version wins; on a tie a development copy beats a user copy beats the bundled one. */
    static final Comparator<PluginCandidate> ORDER = Comparator
        .comparing((PluginCandidate candidate) -> candidate.descriptor().version())
        .thenComparing(candidate -> candidate.origin().ordinal());

    private static PluginStatus admit(PluginCandidate candidate, PluginStateStore.Entry entry, Version sdk) {
        if (!candidate.descriptor().sdk().contains(sdk))
            return PluginStatus.of(candidate, PluginStatus.State.SKIPPED,
                "needs SDK " + candidate.descriptor().sdk() + "; this is SDK " + sdk);
        if (entry != null && entry.remove()) return PluginStatus.of(candidate, PluginStatus.State.DISABLED, "marked for removal");
        if (entry != null && !entry.enabled()) return PluginStatus.of(candidate, PluginStatus.State.DISABLED, "disabled by the user");
        if (candidate.origin() != PluginCandidate.Origin.USER) return null;
        if (entry == null) return PluginStatus.of(candidate, PluginStatus.State.NEEDS_CONSENT, "not reviewed yet");
        Set<String> missing = new TreeSet<>(candidate.descriptor().capabilities());
        missing.removeAll(entry.consented());
        return missing.isEmpty() ? null
            : PluginStatus.of(candidate, PluginStatus.State.NEEDS_CONSENT, "new capabilities: " + String.join(", ", missing));
    }

    private static String unmet(PluginCandidate candidate, Map<String, PluginCandidate> available) {
        for (PluginDescriptor.Requirement requirement : candidate.descriptor().requires()) {
            if (requirement.optional()) continue;
            PluginCandidate provider = available.get(requirement.id());
            if (provider == null) return "requires " + requirement.id() + ", which is not available";
            if (!requirement.version().contains(provider.descriptor().version()))
                return "requires " + requirement.id() + " " + requirement.version() + "; found " + provider.descriptor().version();
        }
        return null;
    }

    private static Set<String> cycleMembers(Map<String, PluginCandidate> plugins) {
        Set<String> members = new TreeSet<>();
        for (String start : plugins.keySet()) {
            // A plugin is in a cycle exactly when it can reach itself through hard or optional edges.
            Set<String> seen = new LinkedHashSet<>();
            List<String> frontier = new ArrayList<>(edges(plugins, start));
            while (!frontier.isEmpty()) {
                String next = frontier.remove(frontier.size() - 1);
                if (next.equals(start)) { members.add(start); break; }
                if (seen.add(next)) frontier.addAll(edges(plugins, next));
            }
        }
        return members;
    }

    private static List<String> edges(Map<String, PluginCandidate> plugins, String id) {
        PluginCandidate candidate = plugins.get(id);
        if (candidate == null) return List.of();
        return candidate.descriptor().requires().stream().map(PluginDescriptor.Requirement::id)
            .filter(plugins::containsKey).toList();
    }

    /** Depth-first post-order over ids in sorted order: dependencies first, otherwise alphabetical. */
    private static List<PluginCandidate> order(Map<String, PluginCandidate> plugins) {
        Map<String, PluginCandidate> ordered = new LinkedHashMap<>();
        for (String id : plugins.keySet()) visit(id, plugins, ordered);
        return new ArrayList<>(ordered.values());
    }

    private static void visit(String id, Map<String, PluginCandidate> plugins, Map<String, PluginCandidate> ordered) {
        if (ordered.containsKey(id)) return;
        for (String dependency : new TreeSet<>(edges(plugins, id))) visit(dependency, plugins, ordered);
        ordered.put(id, plugins.get(id));
    }
}
