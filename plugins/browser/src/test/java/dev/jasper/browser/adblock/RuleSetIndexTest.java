package dev.jasper.browser.adblock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static dev.jasper.browser.adblock.ContentRulesTest.block;
import static dev.jasper.browser.adblock.ContentRulesTest.hide;
import static dev.jasper.browser.adblock.ContentRulesTest.ignore;
import static dev.jasper.browser.adblock.ContentRulesTest.rule;
import static dev.jasper.browser.adblock.ContentRulesTest.trigger;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule set's index answers as a scan of every rule would: over a hand-written list of rules and addresses, and
 * addresses built from those rules' literals, whatever the request's kinds and its page. No rule's expression runs on
 * an address that lacks the rule's literal.
 */
class RuleSetIndexTest {
    private static final List<Set<ResourceKind>> KINDS = List.of(Set.of(ResourceKind.SCRIPT), Set.of(ResourceKind.IMAGE),
        Set.of(ResourceKind.RAW, ResourceKind.FETCH), Set.of(ResourceKind.DOCUMENT), Set.of(ResourceKind.MEDIA),
        Set.of(ResourceKind.PING), Set.of(ResourceKind.WEBSOCKET), Set.of(ResourceKind.POPUP), Set.of(ResourceKind.RAW, ResourceKind.OTHER),
        Set.of(ResourceKind.FONT), Set.of(ResourceKind.STYLE_SHEET), Set.of(ResourceKind.MEDIA, ResourceKind.OTHER));
    private static final List<String> PAGES = List.of("https://news.example.co.uk/article/1", "https://www.shop.test/cart",
        "https://shop.test/", "https://trusted.test/home", "https://adnet.test/home", "https://sponsor-friends.test/",
        "https://popups.test/", "https://clean.test/", "about:blank", "https://video.example/watch?v=9", "https://www.tracker.test/",
        "https://market.test/", "file:///Users/ada/page.html", "data:text/html,hello", "HTTPS://NEWS.EXAMPLE.CO.UK/ARTICLE/1",
        "Http://shop.test/");
    private static List<Rule> rules;
    private static List<String> urls;

    @BeforeAll static void readTheFixtures() throws IOException {
        String list = resource("rules.jsonc").lines().filter(line -> !line.startsWith("//")).collect(Collectors.joining("\n"));
        ContentRules.Compiled compiled = ContentRules.compile(BlockerJson.read(list));
        assertThat(compiled.skipped()).as("the fixture's unsupported rules").isEqualTo(4);
        rules = compiled.rules();
        assertThat(rules).hasSizeBetween(50, 70);
        urls = resource("urls.txt").lines().filter(line -> !line.startsWith("#")).toList();
        assertThat(urls).hasSizeBetween(140, 170);
    }

    @Test void theFixturesAddressesAnswerAsAScanDoesForEveryKindAndPage() {
        var runs = new ArrayList<Run>();
        RuleSet set = RuleSet.of(rules, rule -> { }, (filter, url) -> runs.add(new Run(filter, url)));
        int blocked = 0, pairs = 0;
        for (String url : urls)
            for (Set<ResourceKind> kinds : KINDS)
                for (String page : PAGES) {
                    boolean expected = set.scan(url, kinds, page);
                    assertThat(set.blocks(url, kinds, page)).as("%s as %s on %s", url, kinds, page).isEqualTo(expected);
                    pairs++;
                    if (expected) blocked++;
                }
        assertThat(blocked).as("blocked of %d", pairs).isBetween(pairs / 20, pairs / 2);
        assertEveryRunHadItsLiteral(runs);
        long requestRules = rules.stream().filter(rule -> rule.action() != Rule.Action.HIDE).count();
        assertThat((long) runs.size()).as("expressions run, of %d for a scan", pairs * requestRules).isLessThan(pairs * requestRules / 20);
    }

    @Test void addressesBuiltFromTheRulesLiteralsAnswerAsAScanDoes() {
        var runs = new ArrayList<Run>();
        RuleSet set = RuleSet.of(rules, rule -> { }, (filter, url) -> runs.add(new Run(filter, url)));
        List<String> literals = rules.stream().flatMap(rule -> rule.filter().literal().stream()).distinct().toList();
        var random = new Random(20_261_106L);
        int blocked = 0, pairs = 0;
        for (int round = 0; round < 2_000; round++) {
            String url = built(random, literals);
            for (int context = 0; context < 6; context++) {
                Set<ResourceKind> kinds = KINDS.get(random.nextInt(KINDS.size()));
                String page = random.nextInt(4) == 0 ? url : PAGES.get(random.nextInt(PAGES.size()));
                boolean expected = set.scan(url, kinds, page);
                assertThat(set.blocks(url, kinds, page)).as("%s as %s on %s", url, kinds, page).isEqualTo(expected);
                pairs++;
                if (expected) blocked++;
            }
        }
        assertThat(blocked).as("blocked of %d", pairs).isBetween(pairs / 20, pairs * 9 / 10);
        assertEveryRunHadItsLiteral(runs);
    }

    @Test void anIgnoreRuleDecidesSomeOfThePairs() {
        RuleSet all = RuleSet.of(rules), blocksOnly = RuleSet.of(rules.stream().filter(rule -> rule.action() == Rule.Action.BLOCK).toList());
        int overruled = 0;
        for (String url : urls)
            for (Set<ResourceKind> kinds : KINDS)
                for (String page : PAGES)
                    if (blocksOnly.blocks(url, kinds, page) && !all.blocks(url, kinds, page)) overruled++;
        assertThat(overruled).isGreaterThan(50);
    }

    @Test void eachPagesStylesheetIsTheOneAScanGives() {
        var runs = new ArrayList<Run>();
        RuleSet set = RuleSet.of(rules, rule -> { }, (filter, url) -> runs.add(new Run(filter, url)));
        var pages = new ArrayList<>(PAGES);
        pages.addAll(urls);
        int hidden = 0;
        for (String page : pages) {
            List<String> lines = set.stylesheet(page).lines().sorted().toList();
            assertThat(lines).as(page).isEqualTo(set.scanStylesheet(page).lines().sorted().toList());
            assertThat(lines).as(page).containsAll(set.genericStylesheet(page).lines().toList());
            hidden += lines.size();
        }
        assertThat(hidden).isGreaterThan(pages.size());
        assertEveryRunHadItsLiteral(runs);
    }

    /** Review I1 (V30): no call over the fixture spends a tenth of its reads, so a real list is never cut short. */
    @Test void noCallOverTheFixtureSpendsATenthOfItsReads() {
        var runs = new ArrayList<Run>();
        RuleSet set = RuleSet.of(rules, rule -> { }, (filter, url) -> runs.add(new Run(filter, url)));
        int most = 0;
        var addresses = new ArrayList<>(urls);
        // Re-review (V33): each fixture address made 200 characters long, whose literal checks cost 7 each.
        for (String url : urls) addresses.add(url + (url.contains("?") ? "&" : "?") + "pad=" + "p".repeat(Math.max(0, 195 - url.length())));
        assertThat(addresses.getLast()).hasSizeGreaterThanOrEqualTo(200);
        var pages = new ArrayList<>(PAGES);
        pages.addAll(addresses);
        for (String url : addresses)
            for (Set<ResourceKind> kinds : KINDS)
                for (String page : PAGES) {
                    var reads = new UrlFilter.Reads(RuleSet.CALL_READS);
                    assertThat(set.blocks(url, kinds, page, reads)).as("%s as %s on %s", url, kinds, page).isEqualTo(set.scan(url, kinds, page));
                    most = Math.max(most, reads.used());
                }
        for (String page : pages) {
            var reads = new UrlFilter.Reads(RuleSet.CALL_READS);
            set.stylesheet(page, reads);
            most = Math.max(most, reads.used());
        }
        assertThat(most).isPositive().isLessThanOrEqualTo(RuleSet.CALL_READS / 10);
        int mostForAMatch = 0;
        for (Run run : new HashSet<>(runs)) {
            var reads = new UrlFilter.Reads(Integer.MAX_VALUE);
            run.filter().matches(run.url(), reads);
            mostForAMatch = Math.max(mostForAMatch, reads.used());
        }
        assertThat(mostForAMatch).as("the most a match was charged").isPositive().isLessThanOrEqualTo(UrlFilter.STEPS / 10);
    }

    /** Review M2: a page's sheet examines a few of the list's hide rules, not every one. */
    @Test void aPagesSheetExaminesFewOfTheHideRules() {
        var list = new ArrayList<Map<String, Object>>();
        for (int at = 0; at < 2_000; at++) list.add(rule(trigger("url-filter", ".*", "if-domain", List.of("*site" + at + ".test")), hide(".s" + at)));
        for (int at = 0; at < 200; at++) list.add(rule(trigger("url-filter", ".*"), hide(".g" + at)));
        for (int at = 0; at < 200; at++) list.add(rule(trigger("url-filter", "^https?://([^/]+\\.)?page" + at + "\\.test/"), hide(".p" + at)));
        for (int at = 0; at < 10; at++) list.add(rule(trigger("url-filter", ".*", "unless-domain", List.of("*site" + at + ".test")), hide(".u" + at)));
        list.add(rule(trigger("url-filter", ".*", "if-domain", List.of("*clean.test")), ignore()));
        ContentRules.Compiled compiled = ContentRules.compile(list);
        var examined = new ArrayList<Rule>();
        RuleSet set = RuleSet.of(compiled.rules(), examined::add, (filter, url) -> { });
        List<String> lines = set.stylesheet("https://www.site7.test/a").lines().toList();
        assertThat(lines).hasSize(200 + 1 + 9);
        assertThat(examined).as("of %d hide rules", 2_410).hasSizeLessThanOrEqualTo(12);
        examined.clear();
        assertThat(set.stylesheet("https://www.page42.test/a").lines()).hasSize(200 + 1 + 10);
        assertThat(examined).hasSizeLessThanOrEqualTo(12);
        long hideRules = rules.stream().filter(rule -> rule.action() == Rule.Action.HIDE).count();
        examined.clear();
        RuleSet fixture = RuleSet.of(rules, examined::add, (filter, url) -> { });
        for (String page : PAGES) fixture.stylesheet(page);
        long hidesExamined = examined.stream().filter(rule -> rule.action() == Rule.Action.HIDE).count();
        assertThat(hidesExamined).as("hide rules examined over %d pages, of %d", PAGES.size(), hideRules).isLessThan(PAGES.size() * hideRules / 2);
    }

    /** V32: a literal is keyed by the rarest of its first 64 4-grams, counted over the first 64 of each literal. */
    @Test void aLiteralIsKeyedByOneOfItsFirst64FourGrams() {
        String head = "/common-head-" + "h".repeat(60), tail = "q".repeat(2_000 - head.length());
        var list = new ArrayList<Map<String, Object>>();
        for (int at = 0; at < 5; at++) list.add(rule(trigger("url-filter", head), block()));
        list.add(rule(trigger("url-filter", head + tail), block()));
        List<Rule> compiled = ContentRules.compile(list).rules();
        assertThat(compiled.getLast().filter().literal()).hasValueSatisfying(literal -> assertThat(literal).hasSize(2_000));
        var examined = new HashSet<Rule>();
        RuleSet set = RuleSet.of(compiled, examined::add, (filter, url) -> { });
        set.blocks("https://a.test/" + tail, Set.of(ResourceKind.SCRIPT), "https://a.test/");
        assertThat(examined).as("an address holding the literal's tail alone").doesNotContain(compiled.getLast());
        set.blocks("https://a.test/" + head, Set.of(ResourceKind.SCRIPT), "https://a.test/");
        assertThat(examined).as("an address holding its head").contains(compiled.getLast());
        assertThat(set.blocks("https://a.test/" + head + tail, Set.of(ResourceKind.SCRIPT), "https://a.test/")).isTrue();
    }

    /** An address with a literal of the rules in it, or one a character off, at a random place in a random address. */
    private static String built(Random random, List<String> literals) {
        String[] schemes = {"https", "http", "wss"}, hosts = {"adnet.test", "cdn.adnet.test", "tracker.test", "pixel.example.net",
            "cdn.example.com", "metrics.example.org", "socket-ads.test", "x.ads.example", "video.example", "news.example.co.uk",
            "shop.test", "market.test", "cdn.test", "static.trusted.test", "clean.test"};
        String[] tails = {"", ".js", ".gif", "?x=1", "/", "/allowed/", "?allow=1", ".swf", "?print=1", "/House/1.png"};
        String url = schemes[random.nextInt(schemes.length)] + "://" + hosts[random.nextInt(hosts.length)] + "/p" + random.nextInt(100)
            + "/" + tails[random.nextInt(tails.length)];
        String literal = literals.get(random.nextInt(literals.size()));
        var mutated = new StringBuilder(literal);
        switch (random.nextInt(6)) {
            case 0 -> {
                int at = random.nextInt(mutated.length());
                mutated.setCharAt(at, Character.toUpperCase(mutated.charAt(at)));
            }
            case 1 -> {
                if (mutated.length() > 1) mutated.deleteCharAt(random.nextInt(mutated.length()));
            }
            case 2 -> mutated.setCharAt(random.nextInt(mutated.length()), "aZ/._-?".charAt(random.nextInt(7)));
            default -> { }
        }
        int at = url.indexOf("://") + 3 + random.nextInt(url.length() - url.indexOf("://") - 3 + 1);
        return url.substring(0, at) + mutated + url.substring(at) + tails[random.nextInt(tails.length)];
    }

    private static void assertEveryRunHadItsLiteral(List<Run> runs) {
        assertThat(runs).as("expressions run").isNotEmpty();
        for (Run run : runs) {
            String literal = run.filter().literal().orElse("");
            String haystack = run.filter().caseSensitive() ? run.url() : UrlFilter.fold(run.url());
            assertThat(haystack).as("%s run on %s", run.filter(), run.url()).contains(literal);
        }
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = RuleSetIndexTest.class.getResourceAsStream("/adblock/" + name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record Run(UrlFilter filter, String url) { }
}
