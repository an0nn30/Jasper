package dev.jasper.app.plugins;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * What plugins add to the environment of new local shells ({@code Terminals.environment}). For each launch the
 * contributors run plugin by plugin in id order and, within a plugin, in registration order, each inside the plugin's
 * containment, so one that throws adds nothing. An entry whose name is invalid or reserved, or whose value is missing or
 * holds NUL, is dropped; the first contributor of a name wins. Each dropped or losing entry is logged once per plugin and
 * name. UI thread.
 */
final class ShellContributions {
    private static final System.Logger LOG = System.getLogger(ShellContributions.class.getName());
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    /** Jasper sets these itself: TERM and COLORTERM after the contributions, the others as its own markers. */
    private static final Set<String> RESERVED = Set.of("TERM", "COLORTERM", "TERM_PROGRAM", "TERM_PROGRAM_VERSION");

    /** One registration, compared by identity: a function registered twice is two contributors. */
    private static final class Contributor {
        final Function<Map<String, String>, Map<String, String>> function;
        Contributor(Function<Map<String, String>, Map<String, String>> function) { this.function = function; }
    }

    /** A plugin and a variable name, so each problem is logged once. */
    private record Logged(String pluginId, String name) { }

    private final Containment containment;
    private final TreeMap<String, List<Contributor>> byPlugin = new TreeMap<>();
    private final Set<Logged> logged = new HashSet<>();

    ShellContributions(Containment containment) { this.containment = Objects.requireNonNull(containment, "containment"); }

    /**
     * Adds a plugin's contributor.
     *
     * @return removes it; idempotent
     */
    dev.jasper.app.lifecycle.Subscription register(String pluginId, Function<Map<String, String>, Map<String, String>> contributor) {
        Objects.requireNonNull(pluginId, "pluginId");
        var entry = new Contributor(Objects.requireNonNull(contributor, "contributor"));
        byPlugin.computeIfAbsent(pluginId, id -> new ArrayList<>()).add(entry);
        return new dev.jasper.app.lifecycle.Subscription(() -> {
            List<Contributor> contributors = byPlugin.get(pluginId);
            if (contributors == null) return;
            contributors.removeIf(registered -> registered == entry);
            if (contributors.isEmpty()) byPlugin.remove(pluginId);
        });
    }

    /**
     * The variables plugins add to a new shell.
     *
     * @param jasperEnvironment Jasper's own environment; each contributor gets a read-only copy
     * @return the variables; never null
     */
    Map<String, String> variables(Map<String, String> jasperEnvironment) {
        if (byPlugin.isEmpty()) return Map.of();
        Map<String, String> environment = Map.copyOf(jasperEnvironment);
        var added = new HashMap<String, String>();
        var owners = new HashMap<String, String>();
        for (var plugin : List.copyOf(byPlugin.entrySet())) {
            String pluginId = plugin.getKey();
            for (Contributor contributor : List.copyOf(plugin.getValue())) {
                var offered = new ArrayList<Map.Entry<String, String>>();
                Throwable failure = containment.attempt(pluginId, "shell environment", () -> {
                    Map<String, String> result = contributor.function.apply(environment);
                    if (result != null) result.forEach((name, value) -> offered.add(new AbstractMap.SimpleImmutableEntry<>(name, value)));
                    return null;
                });
                if (failure != null) continue;
                for (Map.Entry<String, String> entry : offered) {
                    String name = entry.getKey();
                    String problem = problem(name, entry.getValue());
                    if (problem != null) {
                        once(pluginId, name, "Plugin " + pluginId + "'s shell variable " + name + " " + problem + "; it was dropped");
                        continue;
                    }
                    String owner = owners.putIfAbsent(name, pluginId);
                    if (owner == null) added.put(name, entry.getValue());
                    else if (!owner.equals(pluginId)) once(pluginId, name, "Plugin " + pluginId + "'s shell variable " + name + " lost to " + owner);
                }
            }
        }
        return Map.copyOf(added);
    }

    /** Why an entry is dropped, or null when it is kept. */
    private static String problem(String name, String value) {
        if (name == null || !NAME.matcher(name).matches()) return "is not a valid name";
        if (RESERVED.contains(name) || name.startsWith("JASPER_")) return "is reserved";
        if (value == null) return "has no value";
        if (value.indexOf('\0') >= 0) return "holds a NUL character";
        return null;
    }

    private void once(String pluginId, String name, String message) {
        if (logged.add(new Logged(pluginId, name))) LOG.log(System.Logger.Level.WARNING, message);
    }
}
