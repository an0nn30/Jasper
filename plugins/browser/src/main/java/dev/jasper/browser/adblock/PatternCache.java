package dev.jasper.browser.adblock;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The compiled expressions of one rule set's filters (ruling V48), so that a list of the allowed size never holds the
 * heap: the least recently used goes first once their weight, as each filter's parse estimates it
 * ({@link UrlFilter#weight()}), passes 32 MiB. Equal filters share one expression. A filter that is not kept is
 * compiled for the call that needs it, which is charged for the compile ({@link UrlFilter#compileCost()}) before it
 * starts; a call that cannot pay ends there, unstarted, as the call's reads do once spent.
 *
 * <p>Requests ask on Chromium's IO thread and pages on its UI thread, which may ask at once: the lock is held only to
 * look a filter up and to keep one, and never while one compiles, so neither thread waits on the other's compile. Two
 * threads that compile one filter at once each compile it, and the cache keeps the first.
 */
final class PatternCache {
    /** How much the expressions kept may weigh at most, in bytes, as their filters estimate it. */
    static final long WEIGHT = 32L * 1024 * 1024;

    private final long bound;
    private final Function<UrlFilter, Pattern> compiler;
    // In the order of their last use, the least recent first; guarded by itself, as are the two counts.
    private final LinkedHashMap<UrlFilter, Pattern> kept = new LinkedHashMap<>(16, 0.75f, true);
    private long weight, compiles;

    /** A cache of at most {@link #WEIGHT}, which compiles a filter as {@link UrlFilter#compile()} does. */
    PatternCache() {
        this(WEIGHT, UrlFilter::compile);
    }

    /** A cache of at most {@code bound} bytes of estimated weight, which compiles a filter with {@code compiler}. */
    PatternCache(long bound, Function<UrlFilter, Pattern> compiler) {
        this.bound = bound;
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    /**
     * The filter's compiled expression: the one kept, or one compiled now, which the cache keeps, letting the least
     * recently used go while the expressions kept weigh more than the bound.
     *
     * @param filter a filter whose expression is not empty
     * @param reads the call's reads, charged the compile before it starts
     * @return the expression
     * @throws UrlFilter.Reads.Exhausted when the call cannot pay for the compile, which is then not started
     */
    Pattern pattern(UrlFilter filter, UrlFilter.Reads reads) {
        synchronized (kept) {
            Pattern found = kept.get(filter);
            if (found != null) return found;
        }
        reads.charge(filter.compileCost());
        Pattern compiled = compiler.apply(filter);
        synchronized (kept) {
            compiles++;
            Pattern raced = kept.putIfAbsent(filter, compiled);
            if (raced != null) return raced;
            weight += filter.weight();
            Iterator<Map.Entry<UrlFilter, Pattern>> eldest = kept.entrySet().iterator();
            while (weight > bound && eldest.hasNext()) {
                weight -= eldest.next().getKey().weight();
                eldest.remove();
            }
        }
        return compiled;
    }

    /** How much the expressions kept weigh, as their filters estimate it. */
    long weight() {
        synchronized (kept) {
            return weight;
        }
    }

    /** How many expressions are kept. */
    int size() {
        synchronized (kept) {
            return kept.size();
        }
    }

    /** How many compiles the cache has had, those of two threads at once both counted. */
    long compiles() {
        synchronized (kept) {
            return compiles;
        }
    }
}
