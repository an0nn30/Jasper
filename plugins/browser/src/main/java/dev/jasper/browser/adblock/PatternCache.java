package dev.jasper.browser.adblock;

import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The compiled expressions of one rule set's filters (ruling V48), so that a list of the allowed size never holds the
 * heap: the least recently used goes first once their weight, as each filter's parse estimates it
 * ({@link UrlFilter#weight()}), passes 32 MiB. A filter that is not kept is compiled for the call that needs it, which
 * is charged for the compile ({@link UrlFilter#compileCost()}) before it starts; a call that cannot pay ends there,
 * unstarted, as the call's reads do once spent.
 *
 * <p>Each filter is kept by its id, its place among the rule set's rules, in arrays of that size, so no lookup goes by
 * a hash a list could choose (ruling V57); equal filters at two places are compiled and weighed apart. Requests ask on
 * Chromium's IO thread and pages on its UI thread, which may ask at once: the lock is held only to look a filter up
 * and to keep one, and never while one compiles, so neither thread waits on the other's compile. Two threads that
 * compile one filter at once each compile it, and the cache keeps the first.
 */
final class PatternCache {
    /** How much the expressions kept may weigh at most, in bytes, as their filters estimate it. */
    static final long WEIGHT = 32L * 1024 * 1024;
    private static final int NONE = -1;

    private final UrlFilter[] filters;
    private final long bound;
    private final Function<UrlFilter, Pattern> compiler;
    private final Object lock = new Object();
    // Guarded by the lock. By id: the expression kept, and the ids kept before and after it in the order of their last
    // use; made at the first compile.
    private Pattern[] kept;
    private int[] older, newer;
    private int eldest = NONE, newest = NONE, size;
    private long weight, compiles;

    /** The cache of a rule set's filters, by id, of at most {@link #WEIGHT}, each compiled by {@link UrlFilter#compile()}. */
    PatternCache(UrlFilter[] filters) {
        this(filters, WEIGHT, UrlFilter::compile);
    }

    /**
     * The cache of {@code filters}, by id, of at most {@code bound} bytes of estimated weight, each compiled by
     * {@code compiler}.
     */
    PatternCache(UrlFilter[] filters, long bound, Function<UrlFilter, Pattern> compiler) {
        this.filters = filters.clone();
        this.bound = bound;
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    /**
     * The compiled expression of filter {@code id}: the one kept, or one compiled now, which the cache keeps, letting the
     * least recently used go while the expressions kept weigh more than the bound.
     *
     * @param id the filter's place among the rule set's rules; its expression is not empty
     * @param reads the call's reads, charged the compile before it starts
     * @return the expression
     * @throws UrlFilter.Reads.Exhausted when the call cannot pay for the compile, which is then not started
     */
    Pattern pattern(int id, UrlFilter.Reads reads) {
        UrlFilter filter = filters[id];
        synchronized (lock) {
            if (kept != null && kept[id] != null) {
                use(id);
                return kept[id];
            }
        }
        reads.charge(filter.compileCost());
        Pattern compiled = compiler.apply(filter);
        synchronized (lock) {
            compiles++;
            if (kept == null) {
                kept = new Pattern[filters.length];
                older = new int[filters.length];
                newer = new int[filters.length];
            }
            if (kept[id] != null) {
                use(id);
                return kept[id];
            }
            kept[id] = compiled;
            link(id);
            size++;
            weight += filter.weight();
            while (weight > bound && eldest != NONE) drop(eldest);
        }
        return compiled;
    }

    /** Makes kept filter {@code id} the most recently used. Holds the lock. */
    private void use(int id) {
        if (id == newest) return;
        unlink(id);
        link(id);
    }

    /** Adds {@code id} as the most recently used. Holds the lock. */
    private void link(int id) {
        older[id] = newest;
        newer[id] = NONE;
        if (newest == NONE) eldest = id;
        else newer[newest] = id;
        newest = id;
    }

    /** Takes {@code id} out of the order of use. Holds the lock. */
    private void unlink(int id) {
        if (older[id] == NONE) eldest = newer[id];
        else newer[older[id]] = newer[id];
        if (newer[id] == NONE) newest = older[id];
        else older[newer[id]] = older[id];
    }

    /** Lets kept filter {@code id} go. Holds the lock. */
    private void drop(int id) {
        unlink(id);
        kept[id] = null;
        size--;
        weight -= filters[id].weight();
    }

    /** How much the expressions kept weigh, as their filters estimate it. */
    long weight() {
        synchronized (lock) {
            return weight;
        }
    }

    /** How many expressions are kept. */
    int size() {
        synchronized (lock) {
            return size;
        }
    }

    /** How many compiles the cache has had, those of two threads at once both counted. */
    long compiles() {
        synchronized (lock) {
            return compiles;
        }
    }
}
