package dev.jasper.browser.adblock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
        "https://market.test/");
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
        RuleSet set = RuleSet.of(rules, (filter, url) -> {
            runs.add(new Run(filter, url));
            return filter.matches(url);
        });
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
        RuleSet set = RuleSet.of(rules, (filter, url) -> {
            runs.add(new Run(filter, url));
            return filter.matches(url);
        });
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
        RuleSet set = RuleSet.of(rules, (filter, url) -> {
            runs.add(new Run(filter, url));
            return filter.matches(url);
        });
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
