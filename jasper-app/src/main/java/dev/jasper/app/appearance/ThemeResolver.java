package dev.jasper.app.appearance;

import java.util.ArrayList;
import java.util.List;
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
     *                   {@code document} itself, which is then skipped, so a theme can extend the theme it replaces
     */
    static ThemeDocument resolve(ThemeDocument document, Function<String, List<ThemeDocument>> candidates) {
        var chain = new ArrayList<ThemeDocument>(List.of(document));
        ThemeDocument merged = document;
        ThemeDocument current = document;
        while (current.parent().isPresent()) {
            String name = current.parent().get();
            ThemeDocument from = current;
            List<ThemeDocument> found = candidates.apply(name);
            if (found.isEmpty())
                throw new ThemeFileException(from.origin(), from.parentLine(), "\"extends\" names \"" + name + "\", which is not a theme Jasper has");
            ThemeDocument parent = found.stream().filter(candidate -> chain.stream().noneMatch(seen -> seen == candidate))
                .findFirst().orElseThrow(() -> new ThemeFileException(from.origin(), from.parentLine(),
                    "\"extends\" forms a cycle: " + chain.stream().map(ThemeDocument::name).collect(Collectors.joining(" -> ")) + " -> " + name));
            chain.add(parent);
            merged = merged.over(parent);
            current = parent;
        }
        var missing = new ArrayList<String>();
        for (Seed seed : REQUIRED) if (!merged.seeds().containsKey(seed)) missing.add(seed.key());
        for (int i = 0; i < 8; i++) if (!merged.ansi().containsKey(i)) missing.add("normal." + ThemeDocument.ANSI_NAMES.get(i));
        if (!missing.isEmpty()) throw new ThemeFileException(document.origin(), 0, "[colors] is missing " + String.join(", ", missing));
        return merged;
    }
}
