package dev.jasper.browser.json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one reader of untrusted JSON the Browser has, for a page's requests through the opener bridge and DevTools'
 * answers: one object, read whole and strictly, within a nesting bound; anything else gives nothing, never an exception.
 * And the one writer of the answers Jasper's own pages read, whose every string reads back as it was written.
 */
class StrictJsonTest {
    /** What {@code quote} writes of each character, counted without writing it, so a script can be bounded before it is quoted. */
    @Test void theQuotedLengthOfEachCharacterIsWhatQuoteWrites() {
        for (int c = 0; c <= 0xFFFF; c++) {
            String one = String.valueOf((char) c);
            assertThat(StrictJson.quotedLength((char) c)).as("U+%04X", c).isEqualTo(StrictJson.quote(one).length() - 2);
        }
    }

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

    /** A search engine's OpenSearch suggestions are one array, read as strictly and within the same kind of bound. */
    @Test void oneWholeArrayReadsAsItsValues() {
        assertThat(StrictJson.array(" [ \"jc\" , [ \"jcef\" , \"caf\\u00e9\" ] , [ ] , { \"k\" : [ 1 ] } ] ", 3))
            .hasValue(List.of("jc", List.of("jcef", "caf\u00e9"), List.of(), Map.of("k", List.of(1L))));
        assertThat(StrictJson.array("[]", 1)).hasValue(List.of());
        for (String text : new String[] {null, "", "{}", "\"x\"", "[] []", "[1,]", "[1 2]", "[\"a\"", "[{\"a\":1,\"a\":2}]"})
            assertThat(StrictJson.array(text, 8)).as(String.valueOf(text)).isEmpty();
        assertThat(StrictJson.array("[[1]]", 1)).isEmpty();
        assertThat(StrictJson.array("[[1]]", 2)).isPresent();
        assertThat(StrictJson.array("[" + "[".repeat(100_000), 8)).as("no stack overflow").isEmpty();
    }

    /** Plan 5's F6: every answer a page of Jasper's reads is written here, compactly, and reads back as it was written. */
    @Test void writesMapsListsStringsLongsBooleansAndNullInTheirOrder() {
        var members = new LinkedHashMap<String, Object>();
        members.put("s", "x");
        members.put("n", 42L);
        members.put("negative", Long.MIN_VALUE);
        members.put("t", true);
        members.put("f", false);
        members.put("none", StrictJson.NULL);
        members.put("list", List.of("a", 1L, List.of(), Map.of()));
        members.put("o", Map.of("k", "v"));
        String text = StrictJson.write(members);
        assertThat(text).isEqualTo("{\"s\":\"x\",\"n\":42,\"negative\":-9223372036854775808,\"t\":true,\"f\":false,\"none\":null,"
            + "\"list\":[\"a\",1,[],{}],\"o\":{\"k\":\"v\"}}");
        members.remove("negative"); // more than 18 digits, which the reader takes as a number it does not hold
        assertThat(StrictJson.object(StrictJson.write(members), 3)).hasValue(members);
        assertThat(StrictJson.write(List.of())).isEqualTo("[]");
        assertThat(StrictJson.write(Map.of())).isEqualTo("{}");
        assertThat(StrictJson.write("x")).isEqualTo("\"x\"");
        assertThat(StrictJson.write(StrictJson.NULL)).isEqualTo("null");
    }

    /**
     * Review Focus 1: a hostile title or address in an answer stays one string. Quotes, backslashes, control characters,
     * DEL, the line and paragraph separators and surrogates are escaped, so none ends the string or the line it is on.
     */
    @Test void aWrittenStringEscapesQuotesControlCharactersAndTheLineAndParagraphSeparators() {
        String face = new String(Character.toChars(0x1F600));
        String hostile = "</script><img src=x onerror=alert(1)>\"'\\" + (char) 0x2028 + (char) 0x2029 + "\n\r\t\b\f"
            + (char) 0x01 + (char) 0x1F + (char) 0x7F + face + "\u00e9";
        String text = StrictJson.write(Map.of("title", hostile));
        assertThat(text).contains("\\\"'\\\\", "\\u2028", "\\u2029", "\\n\\r\\t\\b\\f", "\\u0001", "\\u001f", "\\u007f", "\\ud83d\\ude00",
            "\u00e9");
        assertThat(text.chars().filter(c -> c < 0x20 || c == 0x7F || c == 0x2028 || c == 0x2029 || Character.isSurrogate((char) c)))
            .as("no raw control character, separator or surrogate").isEmpty();
        assertThat(StrictJson.object(text, 1)).hasValue(Map.of("title", hostile));
    }

    /** Only what JSON holds is written: Java's null, other numbers, other objects and keys that are not text are refused. */
    @Test void writingRefusesWhatJsonCannotHold() {
        var nulls = Arrays.asList("a", null);
        var nullValue = new LinkedHashMap<String, Object>();
        nullValue.put("a", null);
        for (Object value : new Object[] {null, 1, 1.5, 'c', new Object(), StrictJson.NUMBER, Map.of(1L, "x"), nulls, nullValue})
            assertThatThrownBy(() -> StrictJson.write(value)).as(String.valueOf(value)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Nesting is bounded, so a list or map that holds itself is refused rather than written forever. */
    @Test void writingRefusesNestingPastItsBoundAndACycle() {
        Object deepest = "x";
        for (int level = 0; level < StrictJson.WRITE_DEPTH; level++) deepest = level % 2 == 0 ? List.of(deepest) : Map.of("k", deepest);
        Object bounded = deepest;
        assertThat(StrictJson.write(bounded)).as("%d levels", StrictJson.WRITE_DEPTH).endsWith("\"x\"" + "]}".repeat(StrictJson.WRITE_DEPTH / 2));
        assertThatThrownBy(() -> StrictJson.write(List.of(bounded))).as("one more").isInstanceOf(IllegalArgumentException.class);
        var cycle = new ArrayList<Object>();
        cycle.add(cycle);
        assertThatThrownBy(() -> StrictJson.write(cycle)).isInstanceOf(IllegalArgumentException.class);
    }
}
