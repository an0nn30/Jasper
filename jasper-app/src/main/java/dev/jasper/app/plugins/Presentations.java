package dev.jasper.app.plugins;

import dev.jasper.app.presentation.AttemptStore;
import dev.jasper.app.presentation.PresentationFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the resolver needs to choose one presentation (spec 4.4): the facts of every candidate that declares one, by
 * {@link #key}, the attempt record, and this build's identity, so the launch and the Plugins page's prediction run the
 * same rule on the same data.
 */
record Presentations(Map<String, PresentationFacts> facts, AttemptStore.Record attempts, String build) {
    /** No presentation data: a provider without facts is skipped as unreadable. */
    static final Presentations NONE = new Presentations(Map.of(), AttemptStore.Record.EMPTY, "");

    Presentations {
        facts = Map.copyOf(facts);
        Objects.requireNonNull(attempts, "attempts"); Objects.requireNonNull(build, "build");
    }

    /** One copy of one plugin: its origin and id. */
    static String key(PluginCandidate candidate) { return candidate.origin() + ":" + candidate.id(); }

    Optional<PresentationFacts> of(PluginCandidate candidate) { return Optional.ofNullable(facts.get(key(candidate))); }

    /** Reads every presentation-declaring candidate's files; a later copy with the same key replaces an earlier one. */
    static Presentations read(List<PluginCandidate> candidates, AttemptStore.Record attempts, String build) {
        var facts = new HashMap<String, PresentationFacts>();
        for (PluginCandidate candidate : candidates) {
            if (!candidate.descriptor().declaresPresentation()) continue;
            facts.put(key(candidate), PresentationFacts.read(new PresentationFacts.Source(candidate.id(),
                candidate.descriptor().presentation(), PluginResources.of(candidate), candidate.descriptor().themes())));
        }
        return new Presentations(facts, attempts, build);
    }
}
