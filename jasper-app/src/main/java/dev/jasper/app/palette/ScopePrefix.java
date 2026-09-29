package dev.jasper.app.palette;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A chip prefix at the very start of a query: {@code >} selects Commands, and {@code <label> >} selects
 * the scope whose label is, ignoring case, exactly that text or else the only one starting with it.
 * {@code rest} is the query after the {@code >}, leading spaces removed.
 */
record ScopePrefix(PaletteScope scope, String rest) {
    static Optional<ScopePrefix> parse(String text, List<PaletteScope> scopes) {
        int marker = text.indexOf('>');
        if (marker < 0 || (marker > 0 && Character.isWhitespace(text.charAt(0)))) return Optional.empty();
        String rest = text.substring(marker + 1).stripLeading();
        if (marker == 0)
            return scopes.stream().filter(scope -> PaletteScope.COMMANDS_ID.equals(scope.id())).findFirst()
                .map(scope -> new ScopePrefix(scope, rest));
        String name = text.substring(0, marker).strip().toLowerCase(Locale.ROOT);
        for (PaletteScope scope : scopes)
            if (scope.label().toLowerCase(Locale.ROOT).equals(name)) return Optional.of(new ScopePrefix(scope, rest));
        List<PaletteScope> starting = scopes.stream()
            .filter(scope -> scope.label().toLowerCase(Locale.ROOT).startsWith(name)).toList();
        return starting.size() == 1 ? Optional.of(new ScopePrefix(starting.getFirst(), rest)) : Optional.empty();
    }
}
