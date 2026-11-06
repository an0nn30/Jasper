package dev.jasper.browser.adblock;

import dev.jasper.browser.json.StrictJson;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A content-blocker list as text: one JSON array of objects, read strictly, at most 8 levels deep and 32 MiB long, as
 * hostile input; anything else is refused with a reason.
 */
class BlockerJsonTest {
    @Test void readsAnArrayOfObjectsOfStringsBooleansNumbersArraysAndObjects() {
        String text = " [ {\"trigger\": {\"url-filter\": \"ads\\\\.js\", \"url-filter-is-case-sensitive\": true, "
            + "\"if-domain\": [\"*a.test\"]}, \"action\": {\"type\": \"block\"}, \"n\": 2, \"d\": 2.5}, {} ] ";
        assertThat(BlockerJson.read(text)).containsExactly(
            Map.of("trigger", Map.of("url-filter", "ads\\.js", "url-filter-is-case-sensitive", true, "if-domain", List.of("*a.test")),
                "action", Map.of("type", "block"), "n", 2L, "d", StrictJson.NUMBER),
            Map.of());
        assertThat(BlockerJson.read("[]")).isEmpty();
    }

    @Test void nestingUpToEightLevelsIsRead() {
        String eight = "[{\"a\":[[[[[[1]]]]]]}]";
        assertThat(BlockerJson.read(eight)).hasSize(1);
        assertThatThrownBy(() -> BlockerJson.read("[{\"a\":[[[[[[[1]]]]]]]}]")).isInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith("Not a content blocker list: ").hasMessageContaining("8 levels");
    }

    @Test void aTruncatedArrayIsRefused() {
        for (String text : List.of("[", "[{\"a\":1}", "[{\"a\":1},", "[{\"a\":\"x", "[{\"a\":1}]x", "[{\"a\":1,}]"))
            assertThatThrownBy(() -> BlockerJson.read(text)).as(text).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Not a content blocker list: ");
    }

    @Test void anythingButAnArrayIsRefused() {
        for (String text : List.of("", "  ", "{}", "{\"rules\":[]}", "\"x\"", "1", "true", "null", "<html>"))
            assertThatThrownBy(() -> BlockerJson.read(text)).as(text).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not a content blocker list: not a JSON array");
    }

    @Test void anItemThatIsNotAnObjectIsRefused() {
        for (String text : List.of("[1]", "[{}, \"x\"]", "[[]]", "[null]", "[true]"))
            assertThatThrownBy(() -> BlockerJson.read(text)).as(text).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Not a content blocker list: ").hasMessageContaining("not an object");
    }

    @Test void aNullAnywhereIsRefused() {
        for (String text : List.of("[{\"a\":null}]", "[{\"a\":[1,null]}]", "[{\"a\":{\"b\":null}}]"))
            assertThatThrownBy(() -> BlockerJson.read(text)).as(text).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Not a content blocker list: ").hasMessageContaining("null");
    }

    @Test void textLongerThan32MiBIsRefusedUnread() {
        String limit = "[" + " ".repeat(32 * 1024 * 1024 - 2) + "]";
        assertThat(BlockerJson.read(limit)).isEmpty();
        assertThatThrownBy(() -> BlockerJson.read(limit + " ")).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Not a content blocker list: longer than 32 MiB");
    }

    @Test void writesWhatItReadsBack() {
        List<Map<String, Object>> rules = List.of(
            Map.of("trigger", Map.of("url-filter", "a\"b\\c", "load-type", List.of("third-party")), "action", Map.of("type", "block")),
            Map.of("trigger", Map.of("url-filter", ".*", "url-filter-is-case-sensitive", false),
                "action", Map.of("type", "css-display-none", "selector", "a[href^=\"https://x/\"]")));
        assertThat(BlockerJson.read(BlockerJson.write(rules))).isEqualTo(rules);
        assertThat(BlockerJson.write(List.of())).isEqualTo("[]");
    }
}
