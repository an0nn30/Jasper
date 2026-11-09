package dev.jasper.browser.adblock;

import dev.jasper.browser.store.CapturedLog;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import static dev.jasper.browser.adblock.ContentRulesTest.block;
import static dev.jasper.browser.adblock.ContentRulesTest.hide;
import static dev.jasper.browser.adblock.ContentRulesTest.ignore;
import static dev.jasper.browser.adblock.ContentRulesTest.rule;
import static dev.jasper.browser.adblock.ContentRulesTest.trigger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

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
        RuleSet again = rules(List.of(rule(trigger("url-filter", "/ads/"), block()),
            rule(trigger("url-filter", ".*", "if-domain", List.of("*news.test")), ignore()),
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

    /** V28: each part of a selector list is a line of its own, and an ignore keeps the parts after it. */
    @Test void eachPartOfASelectorListIsALineOfItsOwn() {
        RuleSet rules = rules(List.of(rule(trigger("url-filter", ".*"), hide(".a, .b")),
            rule(trigger("url-filter", ".*", "if-domain", List.of("*clean.test")), ignore()),
            rule(trigger("url-filter", ".*"), hide(".c, .d ,.e,.f,.g")),
            rule(trigger("url-filter", ".*", "if-domain", List.of("news.test")), hide("aside, .f:not(.g, .h)"))));
        assertThat(rules.stylesheet("https://other.test/")).isEqualTo(".a{display:none!important}\n.b{display:none!important}\n"
            + ".c{display:none!important}\n.d{display:none!important}\n.e{display:none!important}\n.f{display:none!important}\n"
            + ".g{display:none!important}\n");
        assertThat(lines(rules.stylesheet("https://clean.test/"))).as("the lines after the ignore, of a rule with several")
            .containsExactly(".c", ".d", ".e", ".f", ".g");
        assertThat(lines(rules.stylesheet("https://news.test/"))).containsExactly(".a", ".b", ".c", ".d", ".e", ".f", ".g", "aside",
            ".f:not(.g, .h)");
        assertThat(lines(rules.scanStylesheet("https://news.test/"))).containsExactly(".a", ".b", ".c", ".d", ".e", ".f", ".g", "aside",
            ".f:not(.g, .h)");
    }

    /** V28: a rule for every http and https page is shared by those pages alone, their scheme read by its case rule. */
    @Test void theRulesForEveryWebPageAreSharedByWebPagesAlone() {
        var examined = new ArrayList<Rule>();
        List<Rule> compiled = ContentRules.compile(List.of(rule(trigger("url-filter", "^https?://"), hide(".web")),
            rule(trigger("url-filter", "^https?://", "url-filter-is-case-sensitive", true), hide(".exact, .exact-too")),
            rule(trigger("url-filter", "^HTTPS?:\\/\\/.*"), hide(".upper-written")),
            rule(trigger("url-filter", "^HTTPS?://", "url-filter-is-case-sensitive", true), hide(".upper-only")),
            rule(trigger("url-filter", ".*"), hide(".every")))).rules();
        RuleSet rules = RuleSet.of(compiled, examined::add, (filter, url) -> { });
        List<String> web = List.of(".every", ".web", ".upper-written", ".exact", ".exact-too");
        assertThat(lines(rules.genericStylesheet("https://a.test/"))).containsExactlyInAnyOrderElementsOf(web);
        assertThat(lines(rules.genericStylesheet("http://a.test/"))).containsExactlyInAnyOrderElementsOf(web);
        assertThat(lines(rules.stylesheet("https://a.test/"))).containsExactlyInAnyOrderElementsOf(web);
        assertThat(examined).as("no shared rule is examined").isEmpty();
        assertThat(lines(rules.genericStylesheet("HTTPS://A.TEST/"))).containsExactlyInAnyOrder(".every", ".web", ".upper-written");
        assertThat(lines(rules.stylesheet("HTTPS://A.TEST/"))).containsExactlyInAnyOrder(".every", ".web", ".upper-written", ".upper-only");
        assertThat(examined).as("the rule for upper-case schemes alone").extracting(Rule::selector).containsOnly(".upper-only");
        assertThat(lines(rules.genericStylesheet("Https://a.test/"))).containsExactlyInAnyOrder(".every", ".web", ".upper-written");
        for (String page : List.of("file:///Users/ada/a.html", "data:text/html,x", "about:blank", "ftp://a.test/", "httpx://a.test/", "https:/a.test/"))
            assertThat(lines(rules.stylesheet(page))).as(page).containsExactly(".every");
        for (String page : List.of("https://a.test/", "http://a.test/", "HTTPS://A.TEST/", "Http://a.test/", "file:///x", "data:,x", "about:blank"))
            assertThat(lines(rules.stylesheet(page))).as(page).containsExactlyInAnyOrderElementsOf(lines(rules.scanStylesheet(page)));
    }

    /** Review I1 (V30): a call spends at most 2,000,000 reads, however many rules run, and keeps what it proved. */
    @Test void aCallSpendsAtMost2000000ReadsAcrossItsRules() {
        var list = new ArrayList<Map<String, Object>>();
        list.add(rule(trigger("url-filter", "/ads/"), block()));
        list.add(rule(trigger("url-filter", ".*"), hide(".below")));
        for (int at = 0; at < 10_000; at++) list.add(rule(trigger("url-filter", "z.*.*.*.*q"), at % 2 == 0 ? block() : hide(".heavy")));
        list.add(rule(trigger("url-filter", ".*"), hide(".above")));
        list.add(rule(trigger("url-filter", "^https?://a\\.test/"), hide(".proved")));
        RuleSet rules = rules(list);
        String url = "https://a.test/ads/" + "z".repeat(8 * 1024);
        try (var log = new CapturedLog(RuleSet.class.getName())) {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                assertThat(rules.blocks(url, SCRIPT, PAGE)).as("not blocked past the budget").isFalse();
                assertThat(lines(rules.stylesheet(url))).as("the proved lines").containsExactly(".above", ".proved");
                assertThat(lines(rules.genericStylesheet(url))).as("no hide rule runs").containsExactly(".below", ".above");
                assertThat(rules.blocks(url, SCRIPT, PAGE)).isFalse();
            });
            assertThat(log.records()).as("once a list").singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(Level.WARNING);
                assertThat(record.getMessage()).doesNotContain("https://a.test/");
            });
        }
        assertThat(rules.blocks("https://a.test/ads/x", SCRIPT, PAGE)).as("a short address is within it").isTrue();
        var reads = new UrlFilter.Reads(RuleSet.CALL_READS);
        assertThat(rules.blocks(url, SCRIPT, PAGE, reads)).isFalse();
        assertThat(reads.used()).isEqualTo(RuleSet.CALL_READS);
    }

    /** Re-review (V33): a call's charge bounds its work, though its filters' optional parts run steps without reads. */
    @Test void aCallIsChargedForTheStepsItsReadsStandFor() {
        String optional = "http.*" + "(x)?".repeat(255) + "y", half = "http.*" + "x?(x)?".repeat(127) + "y";
        var blocks = new ArrayList<Map<String, Object>>();
        for (int at = 0; at < 40; at++) blocks.add(rule(trigger("url-filter", optional), block()));
        RuleSet blocking = rules(blocks);
        var pages = new ArrayList<Map<String, Object>>();
        for (int at = 0; at < 50; at++) {
            pages.add(rule(trigger("url-filter", half), hide(".h" + at)));
            pages.add(rule(trigger("url-filter", half), ignore()));
        }
        RuleSet hiding = rules(pages);
        try (var log = new CapturedLog(RuleSet.class.getName())) {
            for (String url : List.of("https://a.test/x.js?" + "x".repeat(20), "https://a.test/" + "x".repeat(8 * 1024))) {
                var reads = new UrlFilter.Reads(RuleSet.CALL_READS);
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThat(blocking.blocks(url, SCRIPT, PAGE, reads)).isFalse());
                assertThat(reads.used()).isLessThanOrEqualTo(RuleSet.CALL_READS);
                assertThat(reads.characters()).as("characters read, at 257 each").isLessThanOrEqualTo(RuleSet.CALL_READS / 257);
            }
            String page = "https://a.test/" + "x".repeat(8 * 1024);
            for (boolean own : new boolean[] {true, false}) {
                var reads = new UrlFilter.Reads(RuleSet.CALL_READS);
                assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> assertThat(own ? hiding.stylesheet(page, reads) : hiding.genericStylesheet(page, reads)).isEmpty());
                assertThat(reads.characters()).as("characters read, at 256 each").isLessThanOrEqualTo(RuleSet.CALL_READS / 256);
            }
            assertThat(log.records()).hasSize(2);
        }
    }

    /** Re-review (V33): each literal check is charged for the address it reads, so a list's size cannot hold a call. */
    @Test void eachLiteralCheckIsChargedForTheAddressItReads() {
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        var distinct = new ArrayList<Map<String, Object>>();
        for (char a : alphabet.toCharArray())
            for (char b : alphabet.toCharArray()) distinct.add(rule(trigger("url-filter", "q" + a + b + "[a-z]"), block()));
        var list = new ArrayList<Map<String, Object>>(ContentRules.MAX_RULES);
        for (int at = 0; at < ContentRules.MAX_RULES; at++) list.add(distinct.get(at % distinct.size()));
        RuleSet rules = rules(list);
        try (var log = new CapturedLog(RuleSet.class.getName())) {
            var reads = new UrlFilter.Reads(RuleSet.CALL_READS);
            String url = "https://a.test/" + "x".repeat(8 * 1024);
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThat(rules.blocks(url, SCRIPT, PAGE, reads)).isFalse());
            assertThat(reads.used()).as("stopped at the call's budget").isEqualTo(RuleSet.CALL_READS);
            assertThat(reads.characters()).as("no expression ran").isZero();
            var brief = new UrlFilter.Reads(RuleSet.CALL_READS);
            assertThat(rules.blocks("https://a.test/x.js", SCRIPT, PAGE, brief)).isFalse();
            assertThat(brief.used()).as("a short address: 1 to examine a rule and 1 a check").isEqualTo(2 * ContentRules.MAX_RULES);
            assertThat(log.records()).hasSize(1);
        }
    }

    /**
     * Final review B (V50): each rule a call examines is charged 1, those whose trigger does not fit too, so a list of
     * rules that each fail at once is charged as its size; and each call returns the answer the rules give.
     */
    @Test void eachRuleACallExaminesIsChargedThoseThatDoNotFitToo() {
        var byType = new ArrayList<Map<String, Object>>(ContentRules.MAX_RULES);
        var byDomain = new ArrayList<Map<String, Object>>(ContentRules.MAX_RULES);
        var hiding = new ArrayList<Map<String, Object>>(ContentRules.MAX_RULES);
        for (int at = 0; at < ContentRules.MAX_RULES; at++) {
            byType.add(rule(trigger("url-filter", "ad", "resource-type", List.of("image")), block()));
            byDomain.add(rule(trigger("url-filter", "ad", "unless-domain", List.of("news.test")), block()));
            hiding.add(rule(trigger("url-filter", ".*", "unless-domain", List.of("news.test")), hide(".h" + at)));
        }
        String url = "https://ads.test/ad.js";
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            var reads = new UrlFilter.Reads(RuleSet.CALL_READS);
            assertThat(rules(byType).blocks(url, SCRIPT, "https://news.test/", reads)).as("no rule fits a script").isFalse();
            assertThat(reads.used()).as("1 for each rule examined").isEqualTo(ContentRules.MAX_RULES);
            var unless = new UrlFilter.Reads(RuleSet.CALL_READS);
            assertThat(rules(byDomain).blocks(url, SCRIPT, "https://news.test/", unless)).as("no rule fits the page").isFalse();
            assertThat(unless.used()).isEqualTo(ContentRules.MAX_RULES);
            RuleSet hides = rules(hiding);
            var page = new UrlFilter.Reads(RuleSet.CALL_READS);
            assertThat(hides.stylesheet("https://news.test/", page)).as("no rule fits the page").isEmpty();
            assertThat(page.used()).isEqualTo(ContentRules.MAX_RULES);
            var other = new UrlFilter.Reads(RuleSet.CALL_READS);
            assertThat(hides.stylesheet("https://other.test/", other).length()).as("every rule fits: the sheet's bound")
                .isGreaterThan(RuleSet.SHEET_LIMIT - 1_100).isLessThanOrEqualTo(RuleSet.SHEET_LIMIT);
            assertThat(other.used()).as("no expression: .* matches every page").isEqualTo(ContentRules.MAX_RULES);
        });
    }

    /**
     * Review I1 (V30) and final review B (V50): a rule past its own budget, or the stack, counts as a match when it is
     * an ignore rule and as none otherwise, so the request is allowed and the page hides less; the call goes on.
     */
    @Test void aRuleOverItsOwnBudgetErrsTowardAllowingTheRequestAndHidingLess() {
        String heavy = "z.*.*.*.*q", url = "https://a.test/ads/" + "z".repeat(8 * 1024);
        RuleSet ignoring = rules(List.of(rule(trigger("url-filter", "/ads/"), block()), rule(trigger("url-filter", heavy), ignore())));
        RuleSet unblocked = rules(List.of(rule(trigger("url-filter", "/ads/"), ignore()), rule(trigger("url-filter", heavy), block())));
        RuleSet goesOn = rules(List.of(rule(trigger("url-filter", "/ads/"), block()), rule(trigger("url-filter", heavy), block())));
        RuleSet hiding = rules(List.of(rule(trigger("url-filter", ".*"), hide(".before")), rule(trigger("url-filter", heavy), ignore()),
            rule(trigger("url-filter", ".*"), hide(".after")), rule(trigger("url-filter", heavy), hide(".heavy"))));
        try (var log = new CapturedLog(RuleSet.class.getName())) {
            assertThat(ignoring.blocks(url, SCRIPT, PAGE)).as("an ignore rule past its budget counts as a match").isFalse();
            assertThat(ignoring.blocks(url, SCRIPT, PAGE)).isFalse();
            assertThat(unblocked.blocks(url, SCRIPT, PAGE)).as("a block rule past its budget counts as none").isFalse();
            assertThat(goesOn.blocks(url, SCRIPT, PAGE)).as("and the call goes on").isTrue();
            assertThat(lines(hiding.stylesheet(url))).as("the ignore rule counts, the hide rule does not").containsExactly(".after");
            assertThat(lines(hiding.genericStylesheet(url))).containsExactly(".after");
            assertThat(log.records()).as("once a list").hasSize(4)
                .allSatisfy(record -> assertThat(record.getMessage()).contains("z.*.*.*.*q"));
        }
    }

    /** Final review B (V48): a filter's compile is charged to the first call that runs it, by its expression's size. */
    @Test void aFiltersCompileIsChargedToTheFirstCallThatRunsIt() {
        List<Rule> compiled = ContentRules.compile(List.of(rule(trigger("url-filter", "ads[0-9]x"), block()))).rules();
        RuleSet rules = RuleSet.of(compiled);
        UrlFilter filter = compiled.getFirst().filter();
        String url = "https://a.test/ads1x.js";
        var first = new UrlFilter.Reads(RuleSet.CALL_READS);
        assertThat(rules.blocks(url, SCRIPT, PAGE, first)).isTrue();
        var second = new UrlFilter.Reads(RuleSet.CALL_READS);
        assertThat(rules.blocks(url, SCRIPT, PAGE, second)).isTrue();
        int examined = 1, check = 1 + url.length() / 32;
        assertThat(second.used()).as("a cached filter: its reads").isEqualTo(examined + check + second.characters());
        assertThat(first.used()).as("and its compile, once").isEqualTo(second.used() + filter.compileCost());
        assertThat(rules.patterns().compiles()).isEqualTo(1);
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
