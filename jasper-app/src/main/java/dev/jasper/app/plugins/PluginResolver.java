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
    /** {@code presentation} is the elected presentation provider, which {@code load} holds. */
    record Resolution(List<PluginCandidate> load, List<PluginStatus> rejected, java.util.Optional<PluginCandidate> presentation) {
        Resolution {
            load = List.copyOf(load); rejected = List.copyOf(rejected);
            if (presentation.filter(load::contains).isPresent() != presentation.isPresent())
                throw new IllegalArgumentException("the presentation " + presentation.get().id() + " is not loaded");
        }
        Resolution(List<PluginCandidate> load, List<PluginStatus> rejected) { this(load, rejected, java.util.Optional.empty()); }
    }

    private PluginResolver() { }

    /** The core look's resolution: no presentation data, so a presentation provider is skipped as unreadable. */
    static Resolution resolve(List<PluginCandidate> candidates, Map<String, PluginStateStore.Entry> state,
                              Version sdk, boolean safeMode) {
        return resolve(candidates, state, sdk, safeMode, Presentations.NONE);
    }

    static Resolution resolve(List<PluginCandidate> candidates, Map<String, PluginStateStore.Entry> state,
                              Version sdk, boolean safeMode, Presentations presentations) {
        List<PluginStatus> rejected = new ArrayList<>();
        Map<String, PluginCandidate> chosen = admitted(candidates, state, sdk, safeMode, rejected);
        // Members of a cycle first, so their dependents are then explained by the missing dependency.
        for (String id : cycleMembers(chosen)) {
            rejected.add(PluginStatus.of(chosen.remove(id), PluginStatus.State.SKIPPED, "dependency cycle"));
        }
        dropUnmet(chosen, rejected);
        var presentation = elect(chosen, rejected, presentations);
        // A skipped provider's hard dependents are explained by the missing dependency, as any other's are.
        dropUnmet(chosen, rejected);
        return new Resolution(order(chosen), rejected, presentation);
    }

    /**
     * The candidates that pass safe mode, the choice between copies and {@link #admit}: a superset of the providers that
     * can reach the election, so only their presentation files need reading.
     */
    static List<PluginCandidate> admitted(List<PluginCandidate> candidates, Map<String, PluginStateStore.Entry> state,
                                          Version sdk, boolean safeMode) {
        return List.copyOf(admitted(candidates, state, sdk, safeMode, new ArrayList<>()).values());
    }

    private static Map<String, PluginCandidate> admitted(List<PluginCandidate> candidates, Map<String, PluginStateStore.Entry> state,
                                                         Version sdk, boolean safeMode, List<PluginStatus> rejected) {
        Map<String, PluginCandidate> chosen = new TreeMap<>();
        for (PluginCandidate candidate : candidates) {
            if (safeMode && candidate.descriptor().declaresPresentation()) {
                rejected.add(PluginStatus.of(candidate, PluginStatus.State.DISABLED, PluginStatus.SAFE_MODE_LOOK));
                continue;
            }
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
                PluginStatus.SUPERSEDED + " version " + winner.descriptor().version() + " from " + winner.origin()));
        }
        for (PluginCandidate candidate : List.copyOf(chosen.values())) {
            PluginStatus status = admit(candidate, state.get(candidate.id()), sdk);
            if (status != null) { chosen.remove(candidate.id()); rejected.add(status); }
        }
        return chosen;
    }

    /** {@code resolution} without {@code dropped}, skipped for {@code reason}; its hard dependents are skipped as unmet. */
    static Resolution without(Resolution resolution, PluginCandidate dropped, String reason) {
        Map<String, PluginCandidate> chosen = new TreeMap<>();
        for (PluginCandidate candidate : resolution.load()) if (!candidate.id().equals(dropped.id())) chosen.put(candidate.id(), candidate);
        List<PluginStatus> rejected = new ArrayList<>(resolution.rejected());
        rejected.add(PluginStatus.of(dropped, PluginStatus.State.SKIPPED, reason));
        dropUnmet(chosen, rejected);
        return new Resolution(order(chosen), rejected, java.util.Optional.empty());
    }

    private static void dropUnmet(Map<String, PluginCandidate> chosen, List<PluginStatus> rejected) {
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
    }

    /**
     * Spec 4.4: of the loadable providers without a problem, DEV wins over BUNDLED over USER, then the lowest id; the
     * losers are skipped whole, and a pending or failed attempt for exactly the winner's look skips it too. A provider
     * that could load only beside a loser cannot load at all, so it is skipped and the election runs again without it.
     */
    private static java.util.Optional<PluginCandidate> elect(Map<String, PluginCandidate> chosen, List<PluginStatus> rejected,
                                                             Presentations presentations) {
        List<PluginCandidate> providers = new ArrayList<>();
        for (PluginCandidate candidate : List.copyOf(chosen.values())) {
            if (!candidate.descriptor().declaresPresentation()) continue;
            java.util.Optional<String> problem = presentations.of(candidate).map(dev.jasper.app.presentation.PresentationFacts::problem)
                .orElse(java.util.Optional.of("presentation: " + candidate.descriptor().presentation() + " could not be read"));
            if (problem.isPresent()) {
                chosen.remove(candidate.id());
                rejected.add(PluginStatus.of(candidate, PluginStatus.State.SKIPPED, PluginStatus.INVALID + problem.get()));
                continue;
            }
            providers.add(candidate);
        }
        // Dependents of an invalid provider are explained by the missing dependency, so what fails a trial below is a loser.
        dropUnmet(chosen, rejected);
        providers.removeIf(candidate -> !chosen.containsKey(candidate.id()));
        providers.sort(Comparator.comparingInt((PluginCandidate candidate) -> rank(candidate.origin())).thenComparing(PluginCandidate::id));
        while (!providers.isEmpty()) {
            PluginCandidate winner = providers.getFirst();
            List<PluginCandidate> losers = providers.subList(1, providers.size());
            Map<String, PluginCandidate> trial = new TreeMap<>(chosen);
            for (PluginCandidate loser : losers) trial.remove(loser.id());
            dropUnmet(trial, new ArrayList<>());
            if (!trial.containsKey(winner.id())) {
                chosen.remove(winner.id());
                rejected.add(PluginStatus.of(winner, PluginStatus.State.SKIPPED, PluginStatus.NEEDS_LOOK));
                providers.removeFirst();
                continue;
            }
            for (PluginCandidate loser : losers) {
                chosen.remove(loser.id());
                rejected.add(PluginStatus.of(loser, PluginStatus.State.SKIPPED, PluginStatus.lookActive(winner.descriptor().name())));
            }
            String digest = presentations.of(winner).orElseThrow().digest();
            if (presentations.attempts().of(winner.id()).blocks(digest, presentations.build())) {
                chosen.remove(winner.id());
                rejected.add(PluginStatus.of(winner, PluginStatus.State.SKIPPED, PluginStatus.ATTEMPT_SKIP));
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(winner);
        }
        return java.util.Optional.empty();
    }

    private static int rank(PluginCandidate.Origin origin) {
        return switch (origin) { case DEV -> 0; case BUNDLED -> 1; case USER -> 2; };
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
        if (!PluginStateStore.effective(entry, candidate))
            return PluginStatus.of(candidate, PluginStatus.State.DISABLED, entry == null ? "off by default" : "disabled by the user");
        if (candidate.origin() != PluginCandidate.Origin.USER) return null;
        // Only bundled plugins are ever opted in: a user copy behind an opt-in entry has not been reviewed.
        if (entry == null || entry.optIn()) return PluginStatus.of(candidate, PluginStatus.State.NEEDS_CONSENT, "not reviewed yet");
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
