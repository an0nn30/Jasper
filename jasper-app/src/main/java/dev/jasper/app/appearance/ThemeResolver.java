package dev.jasper.app.appearance;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Merges a theme's {@code extends} chain and checks that the result has the seeds a theme needs. */
final class ThemeResolver {
    private static final List<Seed> REQUIRED = List.of(Seed.BACKGROUND, Seed.FOREGROUND, Seed.ACCENT);

    private ThemeResolver() {}

    /** {@code document} on its own: it may not extend anything. */
    static ThemeDocument resolve(ThemeDocument document) { return resolve(document, name -> List.of()); }

    /**
     * @param candidates every theme document with a given name, highest precedence first; may include
     *                   {@code document} itself; a same-name extension uses only candidates below it, so a theme can
     *                   extend the theme it replaces
     */
    static ThemeDocument resolve(ThemeDocument document, Function<String, List<ThemeDocument>> candidates) {
        return resolve(document, candidates, new ArrayList<>(), new IdentityHashMap<>());
    }

    private static ThemeDocument resolve(ThemeDocument document, Function<String, List<ThemeDocument>> candidates,
                                         List<ThemeDocument> chain, Map<ThemeDocument, ThemeFileException> cycles) {
        if (cycles.containsKey(document)) throw cycles.get(document);
        chain.add(document);
        try {
            ThemeDocument merged = document;
            if (document.parent().isPresent()) {
                String name = document.parent().get();
                List<ThemeDocument> found = candidates.apply(name);
                if (found.isEmpty())
                    throw new ThemeFileException(document.origin(), document.parentLine(),
                        "\"extends\" names \"" + name + "\", which is not a theme Jasper has");
                ThemeFileException failure = null;
                ThemeDocument parent = null;
                // A same-name override extends only layers below itself, never a higher-priority override.
                int own = document.name().strip().equalsIgnoreCase(name.strip()) ? identityIndex(found, document) : -1;
                for (int index = own + 1; index < found.size(); index++) {
                    ThemeDocument candidate = found.get(index);
                    int ancestor = identityIndex(chain, candidate);
                    if (ancestor >= 0) {
                        ThemeFileException cycle = cycle(document, chain, name);
                        for (int member = ancestor; member < chain.size(); member++) cycles.put(chain.get(member), cycle);
                        throw cycle;
                    }
                    try {
                        // A skipped file must not hide a later valid theme of the same name.
                        parent = resolve(candidate, candidates, chain, cycles);
                        break;
                    } catch (ThemeFileException invalid) {
                        // Only callers outside a cycle may skip it and try a lower-priority parent.
                        if (cycles.containsKey(document)) throw cycles.get(document);
                        if (failure == null) failure = invalid;
                    }
                }
                if (parent == null) {
                    if (failure != null) throw failure;
                    throw cycle(document, chain, name);
                }
                merged = document.over(parent);
            }
            requireSeeds(merged);
            return merged;
        } finally {
            chain.removeLast();
        }
    }

    private static int identityIndex(List<ThemeDocument> documents, ThemeDocument document) {
        for (int index = 0; index < documents.size(); index++) if (documents.get(index) == document) return index;
        return -1;
    }

    private static ThemeFileException cycle(ThemeDocument document, List<ThemeDocument> chain, String name) {
        return new ThemeFileException(document.origin(), document.parentLine(),
            "\"extends\" forms a cycle: " + chain.stream().map(ThemeDocument::name)
                .collect(Collectors.joining(" -> ")) + " -> " + name);
    }

    private static void requireSeeds(ThemeDocument document) {
        var missing = new ArrayList<String>();
        for (Seed seed : REQUIRED) if (!document.seeds().containsKey(seed)) missing.add(seed.key());
        for (int i = 0; i < 8; i++) if (!document.ansi().containsKey(i)) missing.add("normal." + ThemeDocument.ANSI_NAMES.get(i));
        if (!missing.isEmpty()) throw new ThemeFileException(document.origin(), 0, "[colors] is missing " + String.join(", ", missing));
    }
}
