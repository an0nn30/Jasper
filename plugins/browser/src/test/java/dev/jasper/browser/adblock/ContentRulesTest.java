package dev.jasper.browser.adblock;

import dev.jasper.browser.adblock.Rule.Action;
import dev.jasper.browser.adblock.Rule.LoadType;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compiling a WebKit content-blocker list: the triggers and actions Jasper applies become rules, and every other rule
 * is skipped and counted, as a hostile list's would be.
 */
class ContentRulesTest {
    @Test void eachTriggerField() {
        Rule rule = only(rule(trigger("url-filter", "ads\\.js", "url-filter-is-case-sensitive", true,
            "resource-type", List.of("script", "image"), "load-type", List.of("third-party"), "if-domain", List.of("*News.Test", "b.test")),
            block()));
        assertThat(rule.index()).isZero();
        assertThat(rule.filter()).isEqualTo(UrlFilter.of("ads\\.js", true).orElseThrow());
        assertThat(rule.types()).containsExactly(ResourceKind.IMAGE, ResourceKind.SCRIPT);
        assertThat(rule.load()).isEqualTo(LoadType.THIRD_PARTY);
        assertThat(rule.ifDomain()).as("lower case").containsExactly("*news.test", "b.test");
        assertThat(rule.unlessDomain()).isEmpty();
        assertThat(rule.action()).isEqualTo(Action.BLOCK);
        assertThat(rule.selector()).isEmpty();

        Rule plain = only(rule(trigger("url-filter", "x", "url-filter-is-case-sensitive", false, "unless-domain", List.of("*a.test")), block()));
        assertThat(plain.filter().caseSensitive()).isFalse();
        assertThat(plain.types()).as("every type").isEmpty();
        assertThat(plain.load()).isEqualTo(LoadType.ANY);
        assertThat(plain.ifDomain()).isEmpty();
        assertThat(plain.unlessDomain()).containsExactly("*a.test");
    }

    @Test void everyResourceTypeByWebKitsName() {
        List<String> names = List.of("document", "image", "style-sheet", "script", "font", "raw", "svg-document", "media", "popup",
            "ping", "fetch", "websocket", "other");
        assertThat(Arrays.stream(ResourceKind.values()).map(ResourceKind::webKitName)).containsExactlyElementsOf(names);
        for (String name : names)
            assertThat(only(rule(trigger("url-filter", "x", "resource-type", List.of(name)), block())).types())
                .containsExactly(ResourceKind.named(name).orElseThrow());
        assertThat(ResourceKind.named("Image")).isEmpty();
    }

    @Test void eachLoadType() {
        assertThat(only(rule(trigger("url-filter", "x", "load-type", List.of("first-party")), block())).load()).isEqualTo(LoadType.FIRST_PARTY);
        assertThat(only(rule(trigger("url-filter", "x", "load-type", List.of("third-party")), block())).load()).isEqualTo(LoadType.THIRD_PARTY);
        assertThat(only(rule(trigger("url-filter", "x", "load-type", List.of("third-party", "first-party")), block())).load())
            .as("both").isEqualTo(LoadType.ANY);
    }

    @Test void eachAction() {
        assertThat(only(rule(trigger("url-filter", "x"), block())).action()).isEqualTo(Action.BLOCK);
        Rule hide = only(rule(trigger("url-filter", ".*"), hide("div[id^=\"ad-\"] > .banner, #promo")));
        assertThat(hide.action()).isEqualTo(Action.HIDE);
        assertThat(hide.selector()).isEqualTo("div[id^=\"ad-\"] > .banner, #promo");
        assertThat(only(rule(trigger("url-filter", "x"), ignore())).action()).isEqualTo(Action.IGNORE_PREVIOUS);
    }

    @Test void eachRuleIsNumberedByItsPlaceAmongTheRulesKept() {
        ContentRules.Compiled compiled = ContentRules.compile(List.of(rule(trigger("url-filter", "a"), block()),
            rule(trigger("url-filter", "(a+)+"), block()), rule(trigger("url-filter", "b"), ignore())));
        assertThat(compiled.rules()).extracting(Rule::index).containsExactly(0, 1);
        assertThat(compiled.skipped()).isEqualTo(1);
    }

    @Test void eachUnsupportedTriggerKeyIsSkippedAndCounted() {
        for (String key : List.of("if-top-url", "unless-top-url", "load-context", "if-frame-url", "unless-frame-url", "Url-Filter"))
            assertSkipped(key, rule(trigger("url-filter", "x", key, List.of("y")), block()));
    }

    @Test void eachUnsupportedActionIsSkippedAndCounted() {
        for (String type : List.of("block-cookies", "make-https", "notify", "redirect", "modify-headers", "Block", ""))
            assertSkipped(type, rule(trigger("url-filter", "x"), Map.of("type", type)));
        assertSkipped("an action with a key of another", rule(trigger("url-filter", "x"), Map.of("type", "block", "selector", ".a")));
        assertSkipped("an action with no type", rule(trigger("url-filter", "x"), Map.of()));
        assertSkipped("an action that is no object", Map.of("trigger", trigger("url-filter", "x"), "action", "block"));
    }

    @Test void aRuleWithBothDomainListsIsSkippedAndCounted() {
        assertSkipped("both", rule(trigger("url-filter", "x", "if-domain", List.of("a.test"), "unless-domain", List.of("b.test")), block()));
    }

    @Test void aFilterThatDoesNotCompileOrIsLongerThan2048CharactersIsSkippedAndCounted() {
        for (String filter : List.of("(", "[a", "a{2}", "a|b", "\\d", "(?:a)", "(a)\\1", "", "caf" + (char) 0xE9, "(".repeat(9) + "a" + ")".repeat(9)))
            assertSkipped(filter, rule(trigger("url-filter", filter), block()));
        assertSkipped("2,049 characters", rule(trigger("url-filter", "a".repeat(2_049)), block()));
        assertThat(ContentRules.compile(List.of(rule(trigger("url-filter", "a".repeat(2_048)), block()))).rules()).hasSize(1);
    }

    @Test void aRepeatedGroupThatHoldsAQuantifierIsSkippedAndCounted() {
        for (String filter : List.of("(a+)+", "(a*)*", "(ab?)+", "x(y(z+))*"))
            assertSkipped(filter, rule(trigger("url-filter", filter), block()));
        assertThat(ContentRules.compile(List.of(rule(trigger("url-filter", "^https?://([^/]+\\.)?a\\.test"), block()))).rules()).hasSize(1);
    }

    @Test void aMalformedTriggerIsSkippedAndCounted() {
        assertSkipped("no url-filter", rule(trigger("resource-type", List.of("image")), block()));
        assertSkipped("a url-filter that is no string", rule(trigger("url-filter", List.of("x")), block()));
        assertSkipped("a case flag that is no boolean", rule(trigger("url-filter", "x", "url-filter-is-case-sensitive", "true"), block()));
        assertSkipped("an unknown resource type", rule(trigger("url-filter", "x", "resource-type", List.of("image", "images")), block()));
        assertSkipped("a resource type that is no array", rule(trigger("url-filter", "x", "resource-type", "image"), block()));
        assertSkipped("no resource types", rule(trigger("url-filter", "x", "resource-type", List.of()), block()));
        assertSkipped("an unknown load type", rule(trigger("url-filter", "x", "load-type", List.of("same-site")), block()));
        assertSkipped("no load types", rule(trigger("url-filter", "x", "load-type", List.of()), block()));
        assertSkipped("a load type that is no array", rule(trigger("url-filter", "x", "load-type", "third-party"), block()));
        assertSkipped("no trigger", Map.of("action", block()));
        assertSkipped("a trigger that is no object", Map.of("trigger", "x", "action", block()));
        assertSkipped("no action", Map.of("trigger", trigger("url-filter", "x")));
        assertSkipped("a key besides trigger and action", Map.of("trigger", trigger("url-filter", "x"), "action", block(), "comment", "y"));
    }

    @Test void aMalformedOrOversizedDomainListIsSkippedAndCounted() {
        for (String key : List.of("if-domain", "unless-domain")) {
            assertSkipped(key + " empty", rule(trigger("url-filter", "x", key, List.of()), block()));
            assertSkipped(key + " no array", rule(trigger("url-filter", "x", key, "a.test"), block()));
            assertSkipped(key + " a number", rule(trigger("url-filter", "x", key, List.of(1L)), block()));
            for (String domain : List.of("", "*", "**a.test", "a*.test", "a test", "a/b", "caf" + (char) 0xE9 + ".test"))
                assertSkipped(key + " '" + domain + "'", rule(trigger("url-filter", "x", key, List.of("a.test", domain)), block()));
            List<String> limit = IntStream.range(0, 1_024).mapToObj(at -> "d" + at + ".test").toList();
            assertThat(ContentRules.compile(List.of(rule(trigger("url-filter", "x", key, limit), block()))).rules()).as(key).hasSize(1);
            assertSkipped(key + " 1,025 domains", rule(trigger("url-filter", "x", key,
                IntStream.range(0, 1_025).mapToObj(at -> "d" + at + ".test").toList()), block()));
        }
    }

    @Test void aSelectorThatCouldReachPastItsOwnRuleIsSkippedAndCounted() {
        for (String selector : List.of("a{color:red}", "a}b", "a;b", "@import url(x)", "a<b", "", " ", "a\nb", "a" + (char) 0x7F))
            assertSkipped("'" + selector + "'", rule(trigger("url-filter", ".*"), hide(selector)));
        assertSkipped("1,025 characters", rule(trigger("url-filter", ".*"), hide("a".repeat(1_025))));
        assertThat(ContentRules.compile(List.of(rule(trigger("url-filter", ".*"), hide("a".repeat(1_024))))).rules()).hasSize(1);
        assertSkipped("no selector", rule(trigger("url-filter", ".*"), Map.of("type", "css-display-none")));
        assertSkipped("a selector that is no string", rule(trigger("url-filter", ".*"), Map.of("type", "css-display-none", "selector", true)));
    }

    @Test void anItemThatIsNoRuleIsSkippedAndCounted() {
        assertSkipped("no keys", Map.of());
    }

    @Test void rulesPastTheFirst250000AreSkippedAndCounted() {
        Map<String, Object> bad = rule(trigger("url-filter", "(a+)+"), block()), good = rule(trigger("url-filter", "ads"), block());
        var list = new java.util.ArrayList<Map<String, Object>>();
        list.add(bad);
        list.addAll(Collections.nCopies(250_002, good));
        ContentRules.Compiled compiled = ContentRules.compile(list);
        assertThat(compiled.rules()).hasSize(250_000);
        assertThat(compiled.rules().getLast().index()).isEqualTo(249_999);
        assertThat(compiled.skipped()).isEqualTo(3);
    }

    @Test void encodingThenCompilingGivesTheSameRules() {
        List<Map<String, Object>> list = List.of(
            rule(trigger("url-filter", "^https?://([^/]+\\.)?ads\\.test[/:]", "load-type", List.of("third-party")), block()),
            rule(trigger("url-filter", "/AdBanner/", "url-filter-is-case-sensitive", true, "resource-type", List.of("image", "media")), block()),
            rule(trigger("url-filter", ".*", "if-domain", List.of("*news.test", "a.test")), hide(".sidebar-ad, div[class*=\"sponsor\"]")),
            rule(trigger("url-filter", ".*", "unless-domain", List.of("*shop.test")), hide("#promo")),
            rule(trigger("url-filter", "/ads/self", "load-type", List.of("first-party")), ignore()),
            rule(trigger("url-filter", "x", "load-type", List.of("first-party", "third-party"), "url-filter-is-case-sensitive", false), block()));
        ContentRules.Compiled compiled = ContentRules.compile(list);
        assertThat(compiled.skipped()).isZero();
        List<Map<String, Object>> encoded = ContentRules.encode(compiled.rules());
        assertThat(ContentRules.compile(encoded)).isEqualTo(compiled);
        assertThat(ContentRules.compile(BlockerJson.read(BlockerJson.write(encoded)))).isEqualTo(compiled);
        assertThat(encoded.get(1)).isEqualTo(Map.of("trigger", Map.of("url-filter", "/AdBanner/", "url-filter-is-case-sensitive", true,
            "resource-type", List.of("image", "media")), "action", Map.of("type", "block")));
        assertThat(encoded.get(5)).as("the defaults are left out").isEqualTo(Map.of("trigger", Map.of("url-filter", "x"), "action", Map.of("type", "block")));
    }

    @Test void aRuleKeepsItsOwnCopies() {
        var types = EnumSet.of(ResourceKind.IMAGE);
        var domains = new java.util.ArrayList<>(List.of("a.test"));
        Rule rule = new Rule(0, UrlFilter.of("x", false).orElseThrow(), types, LoadType.ANY, domains, List.of(), Action.BLOCK, "");
        types.add(ResourceKind.SCRIPT);
        domains.add("b.test");
        assertThat(rule.types()).containsExactly(ResourceKind.IMAGE);
        assertThat(rule.ifDomain()).containsExactly("a.test");
        assertThat(rule.types()).isEqualTo(Set.of(ResourceKind.IMAGE));
    }

    private static void assertSkipped(String reason, Map<String, Object> rule) {
        ContentRules.Compiled compiled = ContentRules.compile(List.of(rule));
        assertThat(compiled.rules()).as(reason).isEmpty();
        assertThat(compiled.skipped()).as(reason).isEqualTo(1);
    }

    private static Rule only(Map<String, Object> rule) {
        ContentRules.Compiled compiled = ContentRules.compile(List.of(rule));
        assertThat(compiled.skipped()).as("skipped").isZero();
        return compiled.rules().getFirst();
    }

    static Map<String, Object> rule(Map<String, Object> trigger, Map<String, Object> action) {
        return Map.of("trigger", trigger, "action", action);
    }

    static Map<String, Object> trigger(Object... keysAndValues) {
        var trigger = new LinkedHashMap<String, Object>();
        for (int at = 0; at < keysAndValues.length; at += 2) trigger.put((String) keysAndValues[at], keysAndValues[at + 1]);
        return trigger;
    }

    static Map<String, Object> block() {
        return Map.of("type", "block");
    }

    static Map<String, Object> hide(String selector) {
        return Map.of("type", "css-display-none", "selector", selector);
    }

    static Map<String, Object> ignore() {
        return Map.of("type", "ignore-previous-rules");
    }
}
