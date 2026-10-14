package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Which plugin answers each {@code jasper} subcommand: chosen once at start from the manifests of the plugins that load,
 * then of the discovered plugins that do not, so a plugin that is off still owns its names.
 */
final class Subcommands {
    static final Subcommands NONE = new Subcommands(Map.of());

    /** A subcommand and the plugin that answers it. */
    record Owner(String name, String summary, String pluginId, String pluginName) { }

    private final Map<String, Owner> owners;

    private Subcommands(Map<String, Owner> owners) { this.owners = Map.copyOf(owners); }

    /** As {@link #choose(List, List, BiConsumer)} when every discovered plugin loads. */
    static Subcommands choose(List<PluginCandidate> loading, BiConsumer<String, String> problem) {
        return choose(loading, List.of(), problem);
    }

    /**
     * A bundled plugin's declaration wins over another plugin's; otherwise the first by plugin id wins. Each losing
     * declaration of a plugin that loads goes to {@code problem} as a title and details. The plugins that do not load
     * (disabled, awaiting consent, removed, skipped in safe mode, incompatible) then take the names nobody owns yet, in
     * the same order and without a problem: their commands report that the plugin is off, not that they are unknown.
     */
    static Subcommands choose(List<PluginCandidate> loading, List<PluginCandidate> notLoading, BiConsumer<String, String> problem) {
        var owners = new LinkedHashMap<String, Owner>();
        for (PluginCandidate candidate : inPrecedenceOrder(loading)) {
            for (PluginDescriptor.Subcommand subcommand : candidate.descriptor().subcommands()) {
                Owner holder = owners.get(subcommand.name());
                if (holder == null) {
                    owners.put(subcommand.name(), owner(candidate, subcommand));
                } else {
                    problem.accept("A plugin command was not added", candidate.descriptor().name() + ": the subcommand "
                        + subcommand.name() + " is already provided by " + holder.pluginName());
                }
            }
        }
        for (PluginCandidate candidate : inPrecedenceOrder(notLoading))
            for (PluginDescriptor.Subcommand subcommand : candidate.descriptor().subcommands())
                owners.putIfAbsent(subcommand.name(), owner(candidate, subcommand));
        return new Subcommands(owners);
    }

    private static List<PluginCandidate> inPrecedenceOrder(List<PluginCandidate> candidates) {
        var ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparing((PluginCandidate candidate) -> candidate.origin() != PluginCandidate.Origin.BUNDLED)
            .thenComparing(PluginCandidate::id));
        return ordered;
    }

    private static Owner owner(PluginCandidate candidate, PluginDescriptor.Subcommand subcommand) {
        return new Owner(subcommand.name(), subcommand.summary(), candidate.id(), candidate.descriptor().name());
    }

    /** A table of exactly these owners, for harnesses that start plugins without manifests. */
    static Subcommands of(Collection<Owner> owners) {
        var map = new LinkedHashMap<String, Owner>();
        for (Owner owner : owners) map.put(owner.name(), owner);
        return new Subcommands(map);
    }

    Optional<Owner> owner(String name) { return Optional.ofNullable(owners.get(name)); }

    /** Every owner, by name. */
    List<Owner> all() { return owners.values().stream().sorted(Comparator.comparing(Owner::name)).toList(); }
}
