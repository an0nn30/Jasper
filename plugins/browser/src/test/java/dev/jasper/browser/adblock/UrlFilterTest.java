package dev.jasper.browser.adblock;

import dev.jasper.browser.store.CapturedLog;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A content-blocker rule's {@code url-filter}: WebKit's grammar alone, the longest run of literal characters every
 * match holds, and a match that reads at most 8 KiB of an address within a bounded number of steps.
 */
class UrlFilterTest {
    @Test void theLiteralIsTheLongestRunOfTopLevelLiteralAtoms() {
        assertThat(literal("^https?://([^/]+\\.)?doubleclick\\.net")).contains("doubleclick.net");
        assertThat(literal("ads\\.js")).contains("ads.js");
        assertThat(literal("ab+c")).as("an atom followed by + stays in its run and ends it").contains("ab");
        assertThat(literal("abc*de")).as("an atom followed by * is left out").contains("ab");
        assertThat(literal("abc?d")).as("an atom followed by ? is left out").contains("ab");
        assertThat(literal(".*")).isEmpty();
        assertThat(literal("^$")).isEmpty();
        assertThat(literal("a.bcd[x]ef(ghijk)lm")).as("a dot, a class and a group end runs").contains("bcd");
        assertThat(literal("x(abcdefg)+y")).as("a group's own characters are not top-level").contains("x");
        assertThat(literal("\\/ads\\/")).contains("/ads/");
        assertThat(literal("^abc$")).contains("abc");
        assertThat(literal("ab.cd")).as("on a tie, the first run").contains("ab");
    }

    @Test void theLiteralIsLowerCaseUnlessTheRuleIsCaseSensitive() {
        assertThat(UrlFilter.of("/AdBanner/", false).flatMap(UrlFilter::literal)).contains("/adbanner/");
        assertThat(UrlFilter.of("/AdBanner/", true).flatMap(UrlFilter::literal)).contains("/AdBanner/");
    }

    @Test void filtersOutsideWebKitsGrammarAreRefused() {
        for (String source : List.of("(a+)+", "(a*)*", "(a?)+", "(ab+c)*", "((a+))+", "(a(b?))*", "\\d", "\\w+", "\\s", "\\b",
            "\\Q.\\E", "\\p{L}", "\\1", "(a)\\1", "\\ ", "\\", "a\\", "(a|aa)+", "a|b", "a{2}", "a{2,}", "a{", "a}", "(?:a)", "(?i)a",
            "(?=a)", "(?!a)", "(", "a)", "(a", "()", "()+", "[", "[a", "[]", "[^]", "[z-a]", "[a-\\d]", "]", "*a", "+a", "?a", "a**",
            "a*+", "a*?", "a+?", "a??", "^*", "^+", "a^", "a$b", "$$", "(^a)", "(a$)", "(a)$+", "\\a", "\\0"))
            assertThat(UrlFilter.of(source, false)).as(source).isEmpty();
    }

    @Test void aGroupMayHoldAQuantifierOnlyWhenItRepeatsAtMostOnce() {
        assertThat(UrlFilter.of("(a+)?", false)).isPresent();
        assertThat(UrlFilter.of("([^/]+\\.)?x", false)).isPresent();
        assertThat(UrlFilter.of("(ab)+", false)).as("a repeated group without a quantifier").isPresent();
        assertThat(UrlFilter.of("((ab)c)*", false)).isPresent();
        assertThat(UrlFilter.of("((a+)?)+", false)).as("a repeated group holding one, however deep").isEmpty();
    }

    @Test void atMostFourUnboundedQuantifiers() {
        assertThat(UrlFilter.of("a*b+c*d+", false)).isPresent();
        assertThat(UrlFilter.of("a*b+c*d+e*", false)).isEmpty();
        assertThat(UrlFilter.of("a?b?c?d?e?f?", false)).as("? is bounded").isPresent();
        assertThat(UrlFilter.of(".*.*.*.*.*", false)).isEmpty();
    }

    @Test void atMostEightGroupsOneInsideAnother() {
        assertThat(UrlFilter.of("(".repeat(8) + "a" + ")".repeat(8), false)).isPresent();
        assertThat(UrlFilter.of("(".repeat(9) + "a" + ")".repeat(9), false)).isEmpty();
        assertThat(UrlFilter.of("x(a(b)c)y(d)".repeat(100), false)).as("side by side").isPresent();
    }

    @Test void aFilterOfTheLongestLengthCompilesAndMatches() {
        UrlFilter longest = filter("a.".repeat(1_024), false);
        assertThat(longest.matches("ab".repeat(1_024))).isTrue();
        assertThat(longest.matches("ab".repeat(1_023))).isFalse();
        UrlFilter groups = filter("(a)b".repeat(512), false);
        assertThat(groups.matches("ab".repeat(512))).isTrue();
    }

    @Test void onlyPrintableAsciiAndPunctuationEscapes() {
        assertThat(UrlFilter.of("caf" + (char) 0xE9, false)).isEmpty();
        assertThat(UrlFilter.of("a" + (char) 0x09 + "b", false)).isEmpty();
        assertThat(UrlFilter.of("a" + (char) 0x7F, false)).isEmpty();
        assertThat(UrlFilter.of("[a" + (char) 0xE9 + "]", false)).isEmpty();
        for (char c : "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".toCharArray())
            assertThat(UrlFilter.of("\\" + c, false)).as("an escaped " + c).isPresent()
                .hasValueSatisfying(filter -> assertThat(filter.matches("x" + c + "y")).isTrue());
        assertThat(UrlFilter.of("a b'\"#%&,-/:;<=>@_`~", false)).isPresent();
        assertThat(UrlFilter.of("[\\]a-c^[-]", false)).isPresent();
    }

    @Test void anEmptyFilterAndOneLongerThan2048CharactersAreRefused() {
        assertThat(UrlFilter.of("", false)).isEmpty();
        assertThat(UrlFilter.of(null, false)).isEmpty();
        assertThat(UrlFilter.of("a".repeat(2_048), false)).isPresent();
        assertThat(UrlFilter.of("a".repeat(2_049), false)).isEmpty();
    }

    @Test void matchesFindsTheFilterAnywhereCaseInsensitivelyUnlessTheRuleSaysOtherwise() {
        UrlFilter ads = filter("/ads/", false);
        assertThat(ads.matches("https://a.test/ads/x.js")).isTrue();
        assertThat(ads.matches("https://a.test/ADS/x.js")).isTrue();
        assertThat(ads.matches("https://a.test/adsx/")).isFalse();
        UrlFilter banner = filter("/AdBanner/", true);
        assertThat(banner.matches("https://a.test/AdBanner/1.png")).isTrue();
        assertThat(banner.matches("https://a.test/adbanner/1.png")).isFalse();
    }

    @Test void theAnchorsTheDotAndTheClasses() {
        UrlFilter anchored = filter("^https?://([^/]+\\.)?doubleclick\\.net[/:]", false);
        assertThat(anchored.matches("https://doubleclick.net/x")).isTrue();
        assertThat(anchored.matches("http://ad.g.doubleclick.net:443/x")).isTrue();
        assertThat(anchored.matches("https://notdoubleclick.net/x")).isFalse();
        assertThat(anchored.matches("https://a.test/?u=https://doubleclick.net/")).isFalse();
        UrlFilter end = filter("\\.swf$", false);
        assertThat(end.matches("https://a.test/x.swf")).isTrue();
        assertThat(end.matches("https://a.test/x.swf?y")).isFalse();
        assertThat(filter("a.c", false).matches("abc")).isTrue();
        assertThat(filter("[^a-c]x", false).matches("bx")).isFalse();
        assertThat(filter("[^a-c]x", false).matches("dx")).isTrue();
        assertThat(filter("(ab)+c", false).matches("xababc")).isTrue();
        assertThat(filter("^$", false).matches("")).isTrue();
        assertThat(filter("^$", false).matches("a")).isFalse();
    }

    @Test void aFilterThatMatchesEveryAddressMatchesWithoutAnExpression() {
        for (String source : List.of(".*", "^.*", ".*$", "^.*$", ".*.*", "^", "$")) {
            UrlFilter filter = filter(source, false);
            assertThat(filter.matchesEverything()).as(source).isTrue();
            assertThat(filter.matches("")).as(source).isTrue();
            assertThat(filter.matches("https://a.test/")).as(source).isTrue();
        }
        assertThat(filter("^$", false).matchesEverything()).isFalse();
        assertThat(filter(".*a", false).matchesEverything()).isFalse();
    }

    @Test void aMatchReadsAtMostTheFirst8KiBOfAnAddress() {
        String head = "https://a.test/" + "p".repeat(8 * 1024 - "https://a.test/".length() - 4);
        String url = head + "ads/" + "q".repeat(2 * 1024 * 1024);
        assertThat(filter("ads/", false).matches(url)).as("within the first 8 KiB").isTrue();
        assertThat(filter("ads/q", false).matches(url)).as("one character past it").isFalse();
        assertThat(filter("q$", false).matches(url)).isFalse();
        assertThat(filter("p$", false).matches(head + "pppp" + "x".repeat(10))).as("$ is the end of what is read").isTrue();
    }

    @Test void aMatchOverItsStepBudgetCountsAsNoMatchAndIsLoggedOnce() {
        UrlFilter heavy = filter("a.*.*.*.*x", false);
        String url = "https://a.test/" + "a".repeat(2 * 1024 * 1024);
        try (var log = new CapturedLog(UrlFilter.class.getName())) {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                assertThat(heavy.matches(url)).isFalse();
                assertThat(heavy.matches(url)).isFalse();
            });
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
                assertThat(record.getMessage()).contains("a.*.*.*.*x").doesNotContain("https://a.test/");
            });
            UrlFilter exponential = filter("a?".repeat(30) + "a".repeat(30), false);
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThat(exponential.matches("a".repeat(30))).isFalse());
            assertThat(log.records()).hasSize(2);
        }
        assertThat(heavy.matches("https://a.test/ax")).as("a short address is within the budget").isTrue();
    }

    @Test void equalWhenTheSourceAndTheCaseRuleAreEqual() {
        assertThat(filter("ads", false)).isEqualTo(filter("ads", false)).hasSameHashCodeAs(filter("ads", false))
            .isNotEqualTo(filter("ads", true)).isNotEqualTo(filter("adz", false));
        assertThat(filter("ads", true).source()).isEqualTo("ads");
        assertThat(filter("ads", true).caseSensitive()).isTrue();
    }

    /**
     * Over random filters in WebKit's alphabet, every filter that is accepted compiles, and every address it matches
     * holds its literal: the invariant the rule set's index stands on.
     */
    @Test void everyAcceptedFilterCompilesAndEveryMatchHoldsItsLiteral() {
        var random = new Random(20_261_106L);
        String alphabet = "abcAB.*+?()[]^$\\-/", text = "abcAB./-";
        int accepted = 0, matched = 0;
        for (int round = 0; round < 40_000; round++) {
            var source = new StringBuilder();
            for (int at = 1 + random.nextInt(9); at > 0; at--) source.append(alphabet.charAt(random.nextInt(alphabet.length())));
            boolean caseSensitive = random.nextBoolean();
            Optional<UrlFilter> parsed = UrlFilter.of(source.toString(), caseSensitive);
            if (parsed.isEmpty()) continue;
            accepted++;
            UrlFilter filter = parsed.get();
            for (int sample = 0; sample < 8; sample++) {
                var url = new StringBuilder();
                for (int at = random.nextInt(12); at > 0; at--) url.append(text.charAt(random.nextInt(text.length())));
                if (!filter.matches(url.toString())) continue;
                matched++;
                String literal = filter.literal().orElse(""), haystack = caseSensitive ? url.toString() : UrlFilter.fold(url.toString());
                assertThat(haystack).as("%s matched by %s", url, filter).contains(literal);
            }
        }
        assertThat(accepted).as("filters accepted").isGreaterThan(2_000);
        assertThat(matched).as("matches checked").isGreaterThan(2_000);
    }

    private static Optional<String> literal(String source) {
        return filter(source, false).literal();
    }

    private static UrlFilter filter(String source, boolean caseSensitive) {
        return UrlFilter.of(source, caseSensitive).orElseThrow(() -> new AssertionError("refused: " + source));
    }
}
