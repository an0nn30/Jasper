package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/** Which plugin answers each {@code jasper} subcommand: chosen once at start from the manifests of the plugins that load. */
final class Subcommands {
    static final Subcommands NONE = new Subcommands(Map.of());

    /** A subcommand and the plugin that answers it. */
    record Owner(String name, String summary, String pluginId, String pluginName) { }

    private final Map<String, Owner> owners;

    private Subcommands(Map<String, Owner> owners) { this.owners = Map.copyOf(owners); }

    /**
     * A bundled plugin's declaration wins over another plugin's; otherwise the first by plugin id wins. Each losing
     * declaration goes to {@code problem} as a title and details.
     */
    static Subcommands choose(List<PluginCandidate> loading, BiConsumer<String, String> problem) {
        var ordered = new ArrayList<>(loading);
        ordered.sort(Comparator.comparing((PluginCandidate candidate) -> candidate.origin() != PluginCandidate.Origin.BUNDLED)
            .thenComparing(PluginCandidate::id));
        var owners = new LinkedHashMap<String, Owner>();
        for (PluginCandidate candidate : ordered) {
            for (PluginDescriptor.Subcommand subcommand : candidate.descriptor().subcommands()) {
                Owner holder = owners.get(subcommand.name());
                if (holder == null) {
                    owners.put(subcommand.name(), new Owner(subcommand.name(), subcommand.summary(), candidate.id(),
                        candidate.descriptor().name()));
                } else {
                    problem.accept("A plugin command was not added", candidate.descriptor().name() + ": the subcommand "
                        + subcommand.name() + " is already provided by " + holder.pluginName());
                }
            }
        }
        return new Subcommands(owners);
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
