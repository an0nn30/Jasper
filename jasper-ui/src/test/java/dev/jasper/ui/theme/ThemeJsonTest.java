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

    @Test void rejectsWhatTheThemeFileNeverHoldsNamingTheOriginAndLine() {
        for (var bad : List.of(
                Map.entry("{ \"a\": 1.5 }", "Expected a whole number"),
                Map.entry("{ \"a\": 1e3 }", "Expected a whole number"),
                Map.entry("{ \"a\": 01 }", "A number has no leading zeros"),
                Map.entry("{ \"a\": 1234567890 }", "A number of at most nine digits"),
                Map.entry("{ \"a\": -1 }", "Expected a string, an object or a whole number for \"a\""),
                Map.entry("{ \"a\": true }", "Expected a string, an object or a whole number for \"a\""),
                Map.entry("{ \"a\": [\"x\"] }", "Expected a string, an object or a whole number for \"a\""),
                Map.entry("{ \"a\": \"x\",\n  \"a\": \"y\" }", "t.json:2: Duplicate key \"a\""),
                Map.entry("{ \"a\": \"x\" } x", "Unexpected content after the theme object"),
                Map.entry("{ \"a\": \"x\" ", "Unexpected end"),
                Map.entry("{ \"a\": \"x\" \"b\": \"y\" }", "Expected ',' or '}'"),
                Map.entry("{ \"a\": \"\\q\" }", "Unknown escape \\q"))) {
            assertThatThrownBy(() -> ThemeJson.parse(bad.getKey(), "t.json")).as(bad.getKey())
                .isInstanceOf(ThemeFileException.class).hasMessageContaining(bad.getValue()).hasMessageStartingWith("t.json");
        }
    }

    @Test void readsTheBundledFile() throws Exception {
        try (var in = ThemeJsonTest.class.getResourceAsStream("/dev/jasper/ui/theme/slate-steel.json")) {
            var root = ThemeJson.parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), "slate-steel.json");
            assertThat(root.keySet()).containsExactly("jasper-theme", "name", "author", "ui", "terminal", "editor");
        }
    }
}
