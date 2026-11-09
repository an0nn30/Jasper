package dev.jasper.browser.adblock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static dev.jasper.browser.adblock.ContentRulesTest.block;
import static dev.jasper.browser.adblock.ContentRulesTest.rule;
import static dev.jasper.browser.adblock.ContentRulesTest.trigger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Final review B (ruling V48): a rule set's compiled filters are kept in one cache, the least recently used out first,
 * within an estimated weight of 32 MiB. A compile is charged to the call that needs it before it starts, and runs
 * outside the cache's lock, so a list of the allowed size never holds the heap, and no call waits on another's compile.
 * W1's re-review (V57): the cache keeps each filter by its place in the rule set, so no list can choose the hash its
 * lookups go by.
 */
class PatternCacheTest {
    private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final Set<ResourceKind> IMAGE = Set.of(ResourceKind.IMAGE);
    private static final List<String> URLS = List.of("https://a.com/x.png", "https://cdn.b.org/i/1.gif", "https://c.net/p", "https://d.io/q.png?x=1");

    @Test void theLeastRecentlyUsedGoesFirstAndAnEvictedFilterCompilesAgainCharged() {
        UrlFilter a = filter("aaa.b"), b = filter("bbb.c"), c = filter("ccc.d");
        assertThat(List.of(a.weight(), b.weight(), c.weight())).as("one weight").containsOnly(a.weight());
        var compiled = new ArrayList<UrlFilter>();
        var cache = new PatternCache(new UrlFilter[] {a, b, c}, 2L * a.weight(), filter -> {
            compiled.add(filter);
            return filter.compile();
        });
        var reads = new UrlFilter.Reads(RuleSet.CALL_READS);
        assertThat(cache.pattern(0, reads).pattern()).isEqualTo(a.expression());
        cache.pattern(1, reads);
        assertThat(reads.used()).as("each compile, charged").isEqualTo(a.compileCost() + b.compileCost());
        cache.pattern(0, reads);
        assertThat(reads.used()).as("a cached filter is not charged").isEqualTo(a.compileCost() + b.compileCost());
        cache.pattern(2, reads);
        assertThat(cache.size()).as("b, the least recently used, made room").isEqualTo(2);
        assertThat(cache.weight()).isEqualTo(2L * a.weight());
        var again = new UrlFilter.Reads(RuleSet.CALL_READS);
        cache.pattern(0, again);
        assertThat(again.used()).isZero();
        assertThat(cache.pattern(1, again).pattern()).isEqualTo(b.expression());
        assertThat(again.used()).as("an evicted filter compiles again, charged").isEqualTo(b.compileCost());
        assertThat(compiled).containsExactly(a, b, c, b);
        assertThat(cache.compiles()).isEqualTo(4);
        assertThat(cache.weight()).isLessThanOrEqualTo(2L * a.weight());
    }

    /** A compile the call's charge left cannot pay is not started: the call ends spent, in the safe direction. */
    @Test void aCompileTheCallCannotPayIsNotStarted() {
        UrlFilter a = filter("aaa.b");
        var cache = new PatternCache(new UrlFilter[] {a}, PatternCache.WEIGHT, filter -> {
            throw new AssertionError("A compile the call cannot pay is not started");
        });
        var reads = new UrlFilter.Reads(a.compileCost() - 1);
        assertThatThrownBy(() -> cache.pattern(0, reads)).isInstanceOf(UrlFilter.Reads.Exhausted.class);
        assertThat(reads.used()).as("spent").isEqualTo(a.compileCost() - 1);
        assertThat(cache.size()).isZero();
        assertThat(cache.compiles()).isZero();
    }

    /** Chromium's IO and UI threads may ask at once: while one compiles, the other finds and compiles its own. */
    @Test void noCompileRunsUnderTheCachesLock() throws Exception {
        UrlFilter slow = filter("slow.x"), quick = filter("quick.y"), kept = filter("kept.z");
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var cache = new PatternCache(new UrlFilter[] {slow, quick, kept}, PatternCache.WEIGHT, filter -> {
            if (filter.equals(slow)) {
                started.countDown();
                try {
                    assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException interrupted) {
                    throw new AssertionError(interrupted);
                }
            }
            return filter.compile();
        });
        cache.pattern(2, unbounded());
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Pattern> waiting = CompletableFuture.supplyAsync(() -> cache.pattern(0, unbounded()), other);
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                assertThat(cache.pattern(2, unbounded()).pattern()).isEqualTo(kept.expression());
                assertThat(cache.pattern(1, unbounded()).pattern()).isEqualTo(quick.expression());
            });
            assertThat(waiting).isNotDone();
            release.countDown();
            assertThat(waiting.get(10, TimeUnit.SECONDS).pattern()).isEqualTo(slow.expression());
            assertThat(cache.size()).isEqualTo(3);
        } finally {
            release.countDown();
            other.shutdownNow();
        }
    }

    /** Two threads that compile one filter at once each get a pattern, and the cache keeps one. */
    @Test void aFilterCompiledTwiceAtOnceIsKeptOnce() throws Exception {
        UrlFilter shared = filter("shared.x");
        var both = new CountDownLatch(2);
        var made = new CopyOnWriteArrayList<Pattern>();
        var cache = new PatternCache(new UrlFilter[] {shared}, PatternCache.WEIGHT, filter -> {
            both.countDown();
            try {
                assertThat(both.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException interrupted) {
                throw new AssertionError(interrupted);
            }
            Pattern pattern = filter.compile();
            made.add(pattern);
            return pattern;
        });
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            var first = CompletableFuture.supplyAsync(() -> cache.pattern(0, unbounded()), threads);
            var second = CompletableFuture.supplyAsync(() -> cache.pattern(0, unbounded()), threads);
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).allSatisfy(pattern -> assertThat(made).contains(pattern));
            assertThat(made).hasSize(2);
            assertThat(first.get()).as("both get the one kept").isSameAs(second.get());
            assertThat(cache.size()).isEqualTo(1);
            assertThat(cache.weight()).isEqualTo(shared.weight());
            assertThat(cache.pattern(0, unbounded())).isSameAs(first.get());
        } finally {
            threads.shutdownNow();
        }
    }

    /** V57: equal filters at two places are kept apart, each compiled and weighed, as the cache keys neither by its filter. */
    @Test void equalFiltersAtTwoPlacesAreKeptApart() {
        UrlFilter first = filter("same.x"), second = filter("same.x");
        assertThat(first).isEqualTo(second);
        var compiled = new ArrayList<UrlFilter>();
        var cache = new PatternCache(new UrlFilter[] {first, second}, PatternCache.WEIGHT, filter -> {
            compiled.add(filter);
            return filter.compile();
        });
        var reads = new UrlFilter.Reads(RuleSet.CALL_READS);
        Pattern one = cache.pattern(0, reads), other = cache.pattern(1, reads);
        assertThat(one).isNotSameAs(other);
        assertThat(compiled).hasSize(2).allSatisfy(filter -> assertThat(filter).isEqualTo(first));
        assertThat(reads.used()).isEqualTo(2L * first.compileCost());
        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.weight()).isEqualTo(2L * first.weight());
        assertThat(cache.pattern(1, reads)).isSameAs(other);
    }

    /**
     * W1's re-review (V57): 8,192 filters whose sources all share one hash, each in 30 or 31 of a list's 250,000 rules,
     * cost what the same list of 8,192 distinct hashes costs: each call is charged the same and compiles as much, and
     * neither takes long; keyed by its filters, the colliding list's every lookup was a walk of one bin under the lock,
     * 11 to 13 s a request.
     */
    @Test void filtersWhoseSourcesShareOneHashCostWhatOthersCost() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            RuleSet colliding = flood(true), control = flood(false);
            assertThat(colliding.size()).isEqualTo(ContentRules.MAX_RULES);
            for (int call = 0; call < 10; call++) {
                var flooded = new UrlFilter.Reads(RuleSet.CALL_READS);
                var plain = new UrlFilter.Reads(RuleSet.CALL_READS);
                assertThat(colliding.blocks(URLS.getFirst(), IMAGE, "https://page.example/", flooded)).isFalse();
                assertThat(control.blocks(URLS.getFirst(), IMAGE, "https://page.example/", plain)).isFalse();
                assertThat(flooded.used()).as("call %d's charge", call).isEqualTo(plain.used());
                assertThat(colliding.patterns().compiles()).as("call %d's compiles", call).isEqualTo(control.patterns().compiles());
                assertThat(colliding.patterns().size()).isEqualTo(control.patterns().size());
            }
            assertThat(colliding.patterns().compiles()).as("duplicates compile apart").isGreaterThan(8_192);
        });
    }

    /**
     * 250,000 block rules over 8,192 filters of 13 groups, each {@code (Aa)} or, by a bit of its number, {@code (BB)},
     * whose hashes are all one, or, {@code colliding} false, {@code (Bc)}, whose hashes differ.
     */
    private static RuleSet flood(boolean colliding) {
        var sources = new ArrayList<String>(8_192);
        for (int n = 0; n < 8_192; n++) {
            var source = new StringBuilder();
            for (int bit = 0; bit < 13; bit++) source.append((n >> bit & 1) == 1 ? (colliding ? "(BB)" : "(Bc)") : "(Aa)");
            sources.add(source.toString());
        }
        assertThat(sources.stream().map(String::hashCode).distinct().count()).isEqualTo(colliding ? 1 : 8_192);
        var list = new ArrayList<Map<String, Object>>(ContentRules.MAX_RULES);
        for (int at = 0; at < ContentRules.MAX_RULES; at++) list.add(rule(trigger("url-filter", sources.get(at % 8_192)), block()));
        ContentRules.Compiled compiled = ContentRules.compile(list);
        assertThat(compiled.skipped()).isZero();
        return RuleSet.of(compiled.rules());
    }

    /**
     * Final review B: the reviewer's shapes, each a 32 MiB list of distinct filters that every request examines and
     * whose expressions, written out, weigh many times the cache's bound. However many calls run, the cache stays
     * within 32 MiB of estimated weight, each call within its charge, its compiles within what it paid for, and nothing
     * is blocked: the safe answer for a call cut short.
     */
    @Test void theReviewersShapesAt32MiBStayWithinTheCacheAndTheCharge() {
        for (String shape : List.of("[a]", "[^a]", ".", "(a)")) {
            List<Rule> list = distinct(shape);
            RuleSet rules = RuleSet.of(list);
            long all = list.stream().mapToLong(rule -> rule.filter().weight()).sum();
            assertThat(all).as("%s: the list's expressions weigh far more than the cache holds", shape).isGreaterThan(8 * PatternCache.WEIGHT);
            int cheapest = list.stream().mapToInt(rule -> rule.filter().compileCost()).min().orElseThrow();
            long compiles = 0;
            for (int call = 0; call < 40; call++) {
                var reads = new UrlFilter.Reads(RuleSet.CALL_READS);
                String url = URLS.get(call % URLS.size());
                assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThat(rules.blocks(url, IMAGE, "https://page.example/", reads))
                    .as("%s: nothing is blocked", shape).isFalse());
                assertThat(reads.used()).isLessThanOrEqualTo(RuleSet.CALL_READS);
                long now = rules.patterns().compiles();
                assertThat((now - compiles) * cheapest).as("%s: the compiles call %d paid for", shape, call).isLessThanOrEqualTo(RuleSet.CALL_READS);
                compiles = now;
                assertThat(rules.patterns().weight()).as("%s: the cache's weight after call %d", shape, call).isLessThanOrEqualTo(PatternCache.WEIGHT);
            }
            assertThat(compiles).as("%s: the cache made room, so filters compiled again", shape).isGreaterThan(rules.patterns().size());
        }
    }

    /**
     * A list of about 32 MiB of block rules whose filters are {@code atom} repeated to 2,048 characters, each made
     * distinct and none with a literal of 4 characters, so every request examines every rule.
     */
    private static List<Rule> distinct(String atom) {
        var list = new ArrayList<Map<String, Object>>();
        long size = 2;
        for (int at = 0; ; at++) {
            Map<String, Object> rule = rule(trigger("url-filter", source(atom, at)), block());
            size += BlockerJson.write(List.of(rule)).length() - 1;
            if (size > BlockerJson.MAX_LENGTH) break;
            list.add(rule);
        }
        ContentRules.Compiled compiled = ContentRules.compile(list);
        assertThat(compiled.skipped()).as(atom).isZero();
        assertThat(compiled.rules()).as(atom).hasSizeGreaterThan(15_000);
        return compiled.rules();
    }

    /** The {@code at}th filter of {@code atom}'s shape: its first three atoms name {@code at}. */
    private static String source(String atom, int at) {
        String[] digits = {String.valueOf(DIGITS.charAt(at % 62)), String.valueOf(DIGITS.charAt(at / 62 % 62)),
            String.valueOf(DIGITS.charAt(at / 3_844 % 62))};
        var source = new StringBuilder();
        if (atom.equals(".")) {
            // Dots, with one / and one : placed by at: its literal is one character, which every address holds.
            for (int place = 0; place < UrlFilter.MAX_LENGTH; place++)
                source.append(place == at % 1_000 ? '/' : place == 1_000 + at / 1_000 % 1_000 ? ':' : '.');
            return source.toString();
        }
        for (String digit : digits) source.append(atom.replace("a", digit));
        while (source.length() + atom.length() <= UrlFilter.MAX_LENGTH) source.append(atom);
        return source.toString();
    }

    private static UrlFilter filter(String source) {
        return UrlFilter.of(source, false).orElseThrow();
    }

    private static UrlFilter.Reads unbounded() {
        return new UrlFilter.Reads(Integer.MAX_VALUE);
    }
}
