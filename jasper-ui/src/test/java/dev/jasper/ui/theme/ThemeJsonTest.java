package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThemeJsonTest {
    @Test void readsNestedObjectsOfStringsInOrder() {
        var root = ThemeJson.parse("""
            { "ui": { "b": "#000000", "a": "x \\"q\\" \\u0041" }, "z": {} }
            """, "t.json");
        assertThat(root.keySet()).containsExactly("ui", "z");
        @SuppressWarnings("unchecked") var ui = (Map<String, Object>) root.get("ui");
        assertThat(ui).containsExactly(Map.entry("b", "#000000"), Map.entry("a", "x \"q\" A"));
        assertThat(root.get("z")).isEqualTo(Map.of());
    }

    @Test void readsWholeNumbers() {
        assertThat(ThemeJson.parse("{ \"jasper-theme\": 1, \"n\": 120 }", "t.json")).containsExactly(Map.entry("jasper-theme", 1L), Map.entry("n", 120L));
    }

    @Test void readsEveryOtherJsonValueAsAnOpaqueValueSoTheLoaderCanSayWhatTheFileIs() {
        var root = ThemeJson.parse("""
            { "t": true, "f": false, "n": null, "list": [1, "x", { "k": [ [], {} ] }, true, null], "empty": [],
              "neg": -1, "zero": -0, "frac": 1.5, "exp": 1e3, "both": -0.5E+2, "long": 1234567890, "plain": 0 }
            """, "t.json");
        for (String key : List.of("t", "f", "n", "list", "empty", "neg", "zero", "frac", "exp", "both", "long"))
            assertThat(root.get(key)).as(key).isInstanceOf(ThemeJson.Other.class);
        assertThat(root.get("plain")).isEqualTo(0L);
        assertThat(ThemeJson.parse("{ \"a\": [ { \"b\": true } ], \"c\": \"x\" }", "t.json")).containsEntry("c", "x");
    }

    @Test void rejectsWhatIsNotJsonNamingTheOriginAndLine() {
        for (var bad : List.of(
                Map.entry("{ \"a\": 01 }", "A number has no leading zeros"),
                Map.entry("{ \"a\": -01 }", "A number has no leading zeros"),
                Map.entry("{ \"a\": [01] }", "A number has no leading zeros"),
                Map.entry("{ \"a\": 1. }", "Expected a digit"),
                Map.entry("{ \"a\": 1e }", "Expected a digit"),
                Map.entry("{ \"a\": 1e+ }", "Expected a digit"),
                Map.entry("{ \"a\": - }", "Expected a digit"),
                Map.entry("{ \"a\": .5 }", "Expected a JSON value for \"a\""),
                Map.entry("{ \"a\": +1 }", "Expected a JSON value for \"a\""),
                Map.entry("{ \"a\": x }", "Expected a JSON value for \"a\""),
                Map.entry("{ \"a\": tru }", "Expected a JSON value for \"a\""),
                Map.entry("{ \"a\": True }", "Expected a JSON value for \"a\""),
                Map.entry("{ \"a\": trueish }", "Expected ',' or '}'"),
                Map.entry("{ \"a\": [1,] }", "Expected a JSON value"),
                Map.entry("{ \"a\": [1 2] }", "Expected ',' or ']'"),
                Map.entry("{ \"a\": [1, 2 }", "Expected ',' or ']'"),
                Map.entry("{ \"a\": [", "Unexpected end"),
                Map.entry("{ \"a\": [{ \"k\": 1, \"k\": 2 }] }", "Duplicate key \"k\""),
                Map.entry("{ \"a\": \"x\",\n  \"a\": \"y\" }", "t.json:2: Duplicate key \"a\""),
                Map.entry("{ \"a\": \"x\" } x", "Unexpected content after the theme object"),
                Map.entry("[1]", "Expected '{'"),
                Map.entry("{ \"a\": \"x\" ", "Unexpected end"),
                Map.entry("{ \"a\": \"x\" \"b\": \"y\" }", "Expected ',' or '}'"),
                Map.entry("{ \"a\": \"\\q\" }", "Unknown escape \\q"))) {
            assertThatThrownBy(() -> ThemeJson.parse(bad.getKey(), "t.json")).as(bad.getKey())
                .isInstanceOf(ThemeFileException.class).hasMessageContaining(bad.getValue()).hasMessageStartingWith("t.json");
        }
    }

    @Test void aSyntaxErrorInsideAnArrayNamesItsLine() {
        assertThatThrownBy(() -> ThemeJson.parse("{ \"a\": [\n  1,\n  x ] }", "t.json")).isInstanceOf(ThemeFileException.class)
            .hasMessageStartingWith("t.json:3:");
    }

    @Test void objectsAndArraysNestedTooDeeplyAreRefusedNotACrash() {
        String nine = "{ \"a\": ".repeat(9) + "{}" + " }".repeat(9);
        assertThatThrownBy(() -> ThemeJson.parse(nine, "t.json")).isInstanceOf(ThemeFileException.class)
            .hasMessageContaining("nested more than 8 deep");
        String deep = "{\"a\":".repeat(150_000) + "{}" + "}".repeat(150_000);
        assertThatThrownBy(() -> ThemeJson.parse(deep, "t.json")).isInstanceOf(ThemeFileException.class)
            .hasMessageContaining("nested more than 8 deep");
        assertThat(ThemeJson.parse("{ \"a\": ".repeat(7) + "{}" + " }".repeat(7), "t.json")).containsKey("a");
        assertThat(ThemeJson.parse("{ \"a\": " + "[".repeat(7) + "]".repeat(7) + " }", "t.json")).containsKey("a");
        assertThatThrownBy(() -> ThemeJson.parse("{ \"a\": " + "[".repeat(8) + "]".repeat(8) + " }", "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("Objects and arrays nested more than 8 deep");
        String unit = "{ \"a\": [{ \"b\": ", close = " } ] }";
        assertThat(ThemeJson.parse(unit.repeat(2) + "[[]]" + close.repeat(2), "t.json")).containsKey("a");
        assertThatThrownBy(() -> ThemeJson.parse(unit.repeat(2) + "[[[]]]" + close.repeat(2), "t.json"))
            .as("objects and arrays share one depth").isInstanceOf(ThemeFileException.class).hasMessageContaining("nested more than 8 deep");
        String hostile = "{ \"a\": " + "[".repeat(150_000) + "]".repeat(150_000) + " }";
        assertThatThrownBy(() -> ThemeJson.parse(hostile, "t.json")).isInstanceOf(ThemeFileException.class)
            .hasMessageContaining("nested more than 8 deep");
    }

    @Test void readsTheBundledFile() throws Exception {
        try (var in = ThemeJsonTest.class.getResourceAsStream("/dev/jasper/ui/theme/slate-steel.json")) {
            var root = ThemeJson.parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), "slate-steel.json");
            assertThat(root.keySet()).containsExactly("jasper-theme", "name", "author", "ui", "terminal", "editor");
        }
    }
}
