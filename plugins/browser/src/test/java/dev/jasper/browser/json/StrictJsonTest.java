package dev.jasper.browser.json;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one reader of untrusted JSON the Browser has, for a page's requests through the opener bridge and DevTools'
 * answers: one object, read whole and strictly, within a nesting bound; anything else gives nothing, never an exception.
 */
class StrictJsonTest {
    @Test void readsEveryKindOfValue() {
        String text = " { \"s\" : \"x\" , \"t\" : true , \"f\" : false , \"n\" : null , \"i\" : -12 , \"z\" : -0 , "
            + "\"big\" : 123456789012345678 , \"bigger\" : 1234567890123456789 , \"d\" : 2.5e3 , \"e\" : -0.1E-2 , "
            + "\"a\" : [ 1 , [ ] , { } ] , \"o\" : { \"k\" : \"v\" } } ";
        assertThat(StrictJson.object(text, 3)).hasValueSatisfying(members -> assertThat(members).containsExactly(
            Map.entry("s", "x"), Map.entry("t", true), Map.entry("f", false), Map.entry("n", StrictJson.NULL), Map.entry("i", -12L),
            Map.entry("z", 0L), Map.entry("big", 123456789012345678L), Map.entry("bigger", StrictJson.NUMBER),
            Map.entry("d", StrictJson.NUMBER), Map.entry("e", StrictJson.NUMBER), Map.entry("a", List.of(1L, List.of(), Map.of())),
            Map.entry("o", Map.of("k", "v"))));
        assertThat(StrictJson.object("{}", 1)).hasValue(Map.of());
    }

    @Test void readsEveryEscape() {
        assertThat(StrictJson.object("{\"a\":\"q\\\"b\\\\s\\/d\\b\\f\\n\\r\\t\\u00e9\\uD83D\\uDE00\\u00E9\"}", 1))
            .hasValue(Map.of("a", "q\"b\\s/d\b\f\n\r\t\u00e9" + (char) 0xD83D + (char) 0xDE00 + "\u00e9"));
    }

    @Test void anythingButOneWholeObjectIsNothing() {
        for (String text : new String[] {null, "", "  ", "[]", "\"x\"", "1", "null", "true", "{} {}", "{} x", "{\"a\":1,\"a\":2}",
            "{\"o\":{\"a\":1,\"a\":1}}", "{\"a\":1,}", "{\"a\":[1,]}", "{,}", "{\"a\" 1}", "{'a':'b'}", "{a:1}", "{\"a\":\"b\"",
            "{\"a\":\"b}", "{\"a\":[1 2]}", "{\"a\":\"x\ny\"}", "{\"a\":\"\\x\"}", "{\"a\":\"\\u12\"}", "{\"a\":\"\\u12g4\"}",
            "{\"a\":\"\\u\uFF10\uFF10\uFF14\uFF11\"}", "{\"a\":01}", "{\"a\":-}", "{\"a\":1.}", "{\"a\":.5}", "{\"a\":1e}",
            "{\"a\":+1}", "{\"a\":tru}", "{\"a\":nul}", "{\"a\":True}", "{\"a\":NaN}", "{\"a\":\"\\"})
            assertThat(StrictJson.object(text, 8)).as(String.valueOf(text)).isEmpty();
    }

    /** The object is the first level: {@code maxDepth} 1 takes a flat object, and each array or object inside is a level more. */
    @Test void nestingPastTheBoundIsNothing() {
        assertThat(StrictJson.object("{\"a\":\"b\"}", 1)).isPresent();
        assertThat(StrictJson.object("{\"a\":[]}", 1)).isEmpty();
        assertThat(StrictJson.object("{\"a\":{}}", 1)).isEmpty();
        assertThat(StrictJson.object("{\"a\":[[{}]]}", 4)).isPresent();
        assertThat(StrictJson.object("{\"a\":[[{\"b\":[]}]]}", 4)).isEmpty();
        assertThat(StrictJson.object("{\"a\":" + "[".repeat(100_000), 8)).as("no stack overflow").isEmpty();
    }
}
