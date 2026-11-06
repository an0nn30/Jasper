package dev.jasper.browser.adblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static dev.jasper.browser.adblock.ContentRulesTest.block;
import static dev.jasper.browser.adblock.ContentRulesTest.hide;
import static dev.jasper.browser.adblock.ContentRulesTest.ignore;
import static dev.jasper.browser.adblock.ContentRulesTest.rule;
import static dev.jasper.browser.adblock.ContentRulesTest.trigger;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which requests a compiled list blocks, and which elements it hides on a page: a request is blocked when its last
 * matching block rule comes after its last matching ignore rule, and a page hides what its matching hide rules after
 * the last matching ignore rule select.
 */
class RuleSetTest {
    private static final Set<ResourceKind> SCRIPT = Set.of(ResourceKind.SCRIPT), IMAGE = Set.of(ResourceKind.IMAGE);
    private static final String PAGE = "https://news.test/story";

    @Test void anEmptySetBlocksAndHidesNothing() {
        assertThat(RuleSet.EMPTY.size()).isZero();
        assertThat(RuleSet.EMPTY.blocks("https://ads.test/x.js", SCRIPT, PAGE)).isFalse();
        assertThat(RuleSet.EMPTY.stylesheet(PAGE)).isEmpty();
        assertThat(RuleSet.EMPTY.genericStylesheet(PAGE)).isEmpty();
    }

    @Test void aRuleCoversTheTypesItNamesOrEveryType() {
        RuleSet rules = rules(List.of(rule(trigger("url-filter", "/img-ads/", "resource-type", List.of("image")), block()),
            rule(trigger("url-filter", "/xhr-ads/", "resource-type", List.of("fetch")), block()),
            rule(trigger("url-filter", "/any-ads/"), block())));
        assertThat(rules.size()).isEqualTo(3);
        assertThat(rules.blocks("https://a.test/img-ads/1.png", IMAGE, PAGE)).isTrue();
        assertThat(rules.blocks("https://a.test/img-ads/1.png", SCRIPT, PAGE)).isFalse();
        assertThat(rules.blocks("https://a.test/xhr-ads/q", Set.of(ResourceKind.RAW, ResourceKind.FETCH), PAGE)).isTrue();
        assertThat(rules.blocks("https://a.test/xhr-ads/q", Set.of(ResourceKind.RAW, ResourceKind.OTHER), PAGE)).isFalse();
        assertThat(rules.blocks("https://a.test/any-ads/q", Set.of(ResourceKind.FONT), PAGE)).isTrue();
        assertThat(rules.blocks("https://a.test/any-ads/q", Set.of(), PAGE)).as("a load of no kind, such as a main frame's").isFalse();
    }

    @Test void firstPartyMeansTheSameBaseDomain() {
        RuleSet rules = rules(List.of(rule(trigger("url-filter", "/third/", "load-type", List.of("third-party")), block()),
            rule(trigger("url-filter", "/first/", "load-type", List.of("first-party")), block())));
        assertThat(rules.blocks("https://cdn.ads.test/third/x.js", SCRIPT, "https://news.test/")).isTrue();
        assertThat(rules.blocks("https://cdn.news.test/third/x.js", SCRIPT, "https://www.news.test/")).isFalse();
        assertThat(rules.blocks("https://edition.cnn.com/third/x.js", SCRIPT, "https://www.cnn.com/")).as("a short-named .com").isFalse();
        assertThat(rules.blocks("https://a.x.co.uk/third/x.js", SCRIPT, "https://x.co.uk/")).isFalse();
        assertThat(rules.blocks("https://y.co.uk/third/x.js", SCRIPT, "https://x.co.uk/")).isTrue();
        assertThat(rules.blocks("https://cdn.news.test/first/x.js", SCRIPT, "https://news.test/")).isTrue();
        assertThat(rules.blocks("https://ads.test/first/x.js", SCRIPT, "https://news.test/")).isFalse();
        assertThat(rules.blocks("https://ads.test/first/x.js", SCRIPT, "about:blank")).as("a page with no host").isFalse();
        assertThat(rules.blocks("https://ads.test/third/x.js", SCRIPT, "about:blank")).isTrue();
        assertThat(rules.blocks("https://ads.test/third/x.js", SCRIPT, null)).isTrue();
    }

    @Test void ifDomainCoversItsPagesAloneAndAStarItsSubdomainsToo() {
        RuleSet rules = rules(List.of(rule(trigger("url-filter", "/a/", "if-domain", List.of("*news.test")), block()),
            rule(trigger("url-filter", "/b/", "if-domain", List.of("shop.test")), block())));
        assertThat(rules.blocks("https://cdn.test/a/x", SCRIPT, "https://news.test/")).isTrue();
        assertThat(rules.blocks("https://cdn.test/a/x", SCRIPT, "https://www.news.test/")).isTrue();
        assertThat(rules.blocks("https://cdn.test/a/x", SCRIPT, "https://fakenews.test/")).isFalse();
        assertThat(rules.blocks("https://cdn.test/a/x", SCRIPT, "https://other.test/")).isFalse();
        assertThat(rules.blocks("https://cdn.test/b/x", SCRIPT, "https://shop.test/")).isTrue();
        assertThat(rules.blocks("https://cdn.test/b/x", SCRIPT, "https://www.shop.test/")).isFalse();
        assertThat(rules.blocks("https://cdn.test/b/x", SCRIPT, "https://SHOP.test:8443/cart")).isTrue();
    }

    @Test void unlessDomainCoversEveryOtherPage() {
        RuleSet rules = rules(List.of(rule(trigger("url-filter", "/a/", "unless-domain", List.of("*news.test")), block()),
            rule(trigger("url-filter", "/b/", "unless-domain", List.of("shop.test")), block())));
        assertThat(rules.blocks("https://cdn.test/a/x", SCRIPT, "https://news.test/")).isFalse();
        assertThat(rules.blocks("https://cdn.test/a/x", SCRIPT, "https://www.news.test/")).isFalse();
        assertThat(rules.blocks("https://cdn.test/a/x", SCRIPT, "https://other.test/")).isTrue();
        assertThat(rules.blocks("https://cdn.test/b/x", SCRIPT, "https://shop.test/")).isFalse();
        assertThat(rules.blocks("https://cdn.test/b/x", SCRIPT, "https://www.shop.test/")).isTrue();
        assertThat(rules.blocks("https://cdn.test/b/x", SCRIPT, "about:blank")).isTrue();
    }

    @Test void aFilterIsCaseInsensitiveUnlessTheRuleSaysOtherwise() {
        RuleSet rules = rules(List.of(rule(trigger("url-filter", "/AdBanner/"), block()),
            rule(trigger("url-filter", "/PopUnder/", "url-filter-is-case-sensitive", true), block())));
        assertThat(rules.blocks("https://a.test/adbanner/1.png", IMAGE, PAGE)).isTrue();
        assertThat(rules.blocks("https://a.test/ADBANNER/1.png", IMAGE, PAGE)).isTrue();
        assertThat(rules.blocks("https://a.test/PopUnder/1.png", IMAGE, PAGE)).isTrue();
        assertThat(rules.blocks("https://a.test/popunder/1.png", IMAGE, PAGE)).isFalse();
    }

    @Test void blockThenIgnoreAllowsAndIgnoreThenBlockBlocks() {
        RuleSet allowed = rules(List.of(rule(trigger("url-filter", "/ads/"), block()), rule(trigger("url-filter", "/ads/self/"), ignore())));
        assertThat(allowed.blocks("https://a.test/ads/self/x.js", SCRIPT, PAGE)).isFalse();
        assertThat(allowed.blocks("https://a.test/ads/other/x.js", SCRIPT, PAGE)).isTrue();
        RuleSet blocked = rules(List.of(rule(trigger("url-filter", "/ads/self/"), ignore()), rule(trigger("url-filter", "/ads/"), block())));
        assertThat(blocked.blocks("https://a.test/ads/self/x.js", SCRIPT, PAGE)).isTrue();
        RuleSet again = rules(List.of(rule(trigger("url-filter", "/ads/"), block()), rule(trigger("url-filter", ".*", "if-domain", List.of("*news.test")), ignore()),
            rule(trigger("url-filter", "/ads/popup"), block())));
        assertThat(again.blocks("https://a.test/ads/x.js", SCRIPT, "https://news.test/")).isFalse();
        assertThat(again.blocks("https://a.test/ads/x.js", SCRIPT, "https://other.test/")).isTrue();
        assertThat(again.blocks("https://a.test/ads/popup.js", SCRIPT, "https://news.test/")).isTrue();
    }

    @Test void aRequestIsReadToItsFirst8KiB() {
        RuleSet rules = rules(List.of(rule(trigger("url-filter", "/adsy/"), block())));
        String head = "https://a.test/" + "p".repeat(8 * 1024 - "https://a.test/".length());
        assertThat(rules.blocks(head.substring(0, head.length() - 6) + "/adsy/", SCRIPT, PAGE)).isTrue();
        assertThat(rules.blocks(head + "/adsy/", SCRIPT, PAGE)).isFalse();
        assertThat(rules.blocks(null, SCRIPT, PAGE)).isFalse();
    }

    @Test void theStylesheetHidesWhatTheGenericAndThePagesOwnRulesSelect() {
        RuleSet rules = rules(List.of(rule(trigger("url-filter", ".*"), hide(".ad")),
            rule(trigger("url-filter", ".*", "if-domain", List.of("*news.test")), hide(".sidebar-ad")),
            rule(trigger("url-filter", ".*", "if-domain", List.of("shop.test")), hide("#shop-ad")),
            rule(trigger("url-filter", ".*", "unless-domain", List.of("*news.test")), hide(".promo")),
            rule(trigger("url-filter", "^https?://video\\.test/watch"), hide(".overlay")),
            rule(trigger("url-filter", ".*", "resource-type", List.of("image")), hide(".not-for-pages")),
            rule(trigger("url-filter", ".*", "load-type", List.of("third-party")), hide(".never-a-page")),
            rule(trigger("url-filter", ".*", "resource-type", List.of("document", "image"), "load-type", List.of("first-party")), hide("div[id^=\"ad-\"]")),
            rule(trigger("url-filter", "/ads/"), block())));
        assertThat(lines(rules.stylesheet("https://www.news.test/a"))).containsExactlyInAnyOrder(".ad", ".sidebar-ad", "div[id^=\"ad-\"]");
        assertThat(lines(rules.stylesheet("https://shop.test/"))).containsExactlyInAnyOrder(".ad", "#shop-ad", ".promo", "div[id^=\"ad-\"]");
        assertThat(lines(rules.stylesheet("https://www.shop.test/"))).containsExactlyInAnyOrder(".ad", ".promo", "div[id^=\"ad-\"]");
        assertThat(lines(rules.stylesheet("https://video.test/watch?v=1"))).containsExactlyInAnyOrder(".ad", ".promo", ".overlay", "div[id^=\"ad-\"]");
        assertThat(rules.stylesheet("https://other.test/")).isEqualTo(".ad{display:none!important}\n"
            + "div[id^=\"ad-\"]{display:none!important}\n.promo{display:none!important}\n");
        assertThat(rules.genericStylesheet("https://www.news.test/a")).as("the part every page shares")
            .isEqualTo(".ad{display:none!important}\ndiv[id^=\"ad-\"]{display:none!important}\n");
    }

    @Test void theStylesheetKeepsOnlyWhatComesAfterThePagesLastIgnore() {
        RuleSet rules = rules(List.of(rule(trigger("url-filter", ".*"), hide(".early")),
            rule(trigger("url-filter", ".*", "if-domain", List.of("*clean.test")), hide(".clean-early")),
            rule(trigger("url-filter", ".*", "if-domain", List.of("*clean.test")), ignore()),
            rule(trigger("url-filter", ".*"), hide(".late")),
            rule(trigger("url-filter", ".*", "if-domain", List.of("*clean.test")), hide(".clean-late")),
            rule(trigger("url-filter", "/keep/"), ignore()),
            rule(trigger("url-filter", ".*", "resource-type", List.of("image")), ignore())));
        assertThat(lines(rules.stylesheet("https://clean.test/"))).containsExactlyInAnyOrder(".late", ".clean-late");
        assertThat(rules.genericStylesheet("https://clean.test/")).isEqualTo(".late{display:none!important}\n");
        assertThat(lines(rules.stylesheet("https://other.test/"))).containsExactlyInAnyOrder(".early", ".late");
        assertThat(rules.stylesheet("https://other.test/keep/")).isEmpty();
        assertThat(rules.genericStylesheet("https://other.test/keep/")).isEmpty();
    }

    @Test void thePagesStylesheetStaysWithin2MiB() {
        var list = new ArrayList<Map<String, Object>>();
        for (int at = 0; at < 2_500; at++) list.add(rule(trigger("url-filter", ".*"), hide(".g" + at + "-" + "x".repeat(990))));
        for (int at = 0; at < 100; at++)
            list.add(rule(trigger("url-filter", ".*", "if-domain", List.of("news.test")), hide(".d" + at + "-" + "x".repeat(990))));
        RuleSet rules = rules(list);
        for (String page : List.of("https://news.test/", "https://other.test/")) {
            String sheet = rules.stylesheet(page);
            assertThat(sheet.length()).as(page).isLessThanOrEqualTo(2 * 1024 * 1024).isGreaterThan(2 * 1024 * 1024 - 1_100);
            assertThat(sheet).as("whole lines").endsWith("{display:none!important}\n");
            assertThat(lines(sheet)).allSatisfy(selector -> assertThat(selector).hasSizeGreaterThan(990));
        }
    }

    @Test void theRulesAreTakenInTheOrderOfTheirIndexes() {
        List<Rule> compiled = ContentRules.compile(List.of(rule(trigger("url-filter", "/ads/"), block()),
            rule(trigger("url-filter", "/ads/self/"), ignore()))).rules();
        RuleSet reversed = RuleSet.of(List.of(compiled.get(1), compiled.get(0)));
        assertThat(reversed.blocks("https://a.test/ads/self/x.js", SCRIPT, PAGE)).isFalse();
    }

    private static List<String> lines(String sheet) {
        return sheet.lines().map(line -> {
            assertThat(line).endsWith("{display:none!important}");
            return line.substring(0, line.length() - "{display:none!important}".length());
        }).toList();
    }

    private static RuleSet rules(List<Map<String, Object>> list) {
        ContentRules.Compiled compiled = ContentRules.compile(list);
        assertThat(compiled.skipped()).as("skipped").isZero();
        return RuleSet.of(compiled.rules());
    }
}
