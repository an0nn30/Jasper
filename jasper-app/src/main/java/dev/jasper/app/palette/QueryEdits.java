package dev.jasper.app.palette;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A typed scope prefix ("history > ls") leaving the query to become the chip, as both palette views apply it. The kit
 * applies the shortened query one event-loop turn after the edit (Swing forbids document writes while it notifies), so a
 * key queued before then reports text that still begins with the prefix; that text is stripped until the field shows
 * the query. Deleting text never makes a chip. EDT only.
 */
final class QueryEdits {
    /** An edit's outcome: the query to keep and the scope its prefix named, if any. */
    record Outcome(String query, Optional<ScopePrefix> prefix) { }

    private final Function<String, Optional<ScopePrefix>> prefixes;
    private String consumedPrefix;

    QueryEdits(Function<String, Optional<ScopePrefix>> prefixes) { this.prefixes = Objects.requireNonNull(prefixes); }

    /** The query after {@code text} replaced {@code previous}, {@code typed} or set by the host. */
    Outcome edited(String previous, String text, boolean typed) {
        if (consumedPrefix != null) {
            if (typed && text.startsWith(consumedPrefix)) text = text.substring(consumedPrefix.length());
            else consumedPrefix = null;
        }
        Optional<ScopePrefix> prefix = text.equals(previous) || removal(previous, text) ? Optional.empty() : prefixes.apply(text);
        if (prefix.isPresent()) consumedPrefix = text.substring(0, text.length() - prefix.get().rest().length());
        return new Outcome(prefix.map(ScopePrefix::rest).orElse(text), prefix);
    }

    /** Whether a consumed prefix may still arrive in typed text. */
    boolean pending() { return consumedPrefix != null; }

    /** After the kit's own deferred update (queued first), forgets the consumed prefix once the field shows the query. */
    void settle(BooleanSupplier closed, Supplier<String> field, Supplier<String> query) {
        java.awt.EventQueue.invokeLater(() -> {
            if (closed.getAsBoolean() || consumedPrefix == null) return;
            if (Objects.equals(field.get(), query.get())) consumedPrefix = null;
        });
    }

    /** Whether {@code next} is {@code previous} with one stretch of it deleted. */
    static boolean removal(String previous, String next) {
        if (next.length() >= previous.length()) return false;
        int common = 0;
        while (common < next.length() && previous.charAt(common) == next.charAt(common)) common++;
        return previous.endsWith(next.substring(common));
    }
}
