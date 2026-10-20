package dev.jasper.app.settings;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettingKindTest {
    private static Object toml(String literal) { return TomlLiterals.parseValue(literal); }

    @Test void booleansAcceptOnlyBooleansAndAWrongTypeRejectsTheFile() {
        var kind = SettingKind.bool();
        assertThat(kind.read(toml("false"), true)).isEqualTo(new Reading<>(false, List.of()));
        var wrong = kind.read(toml("1"), true);
        assertThat(wrong.value()).isTrue();
        assertThat(wrong.rejectsFile()).isTrue();
        assertThat(wrong.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Reading.Severity.ERROR);
            assertThat(issue.message()).isEqualTo("Expected a boolean; configuration was not applied.");
        });
        assertThat(kind.literal(true)).isEqualTo("true");
    }

    @Test void integersEitherFallBackWithAnErrorOrClampWithAWarning() {
        var strict = SettingKind.integer(5, 500, SettingKind.OutOfRange.DEFAULT);
        assertThat(strict.read(toml("80"), 100).value()).isEqualTo(80);
        var low = strict.read(toml("4"), 100);
        assertThat(low.value()).isEqualTo(100);
        assertThat(low.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Reading.Severity.ERROR);
            assertThat(issue.message()).isEqualTo("Use an integer from 5–500; using the default.");
            assertThat(issue.rejectsFile()).isFalse();
        });
        var clamped = SettingKind.integer(20, 72, SettingKind.OutOfRange.CLAMP).read(toml("99"), 30);
        assertThat(clamped.value()).isEqualTo(72);
        assertThat(clamped.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Reading.Severity.WARNING);
            assertThat(issue.message()).isEqualTo("Use an integer from 20–72; using 72.");
        });
        assertThat(strict.read(toml("5.0"), 100).issues().getFirst().message()).isEqualTo("Expected an integer; configuration was not applied.");
        assertThat(strict.check(501)).hasValue("Use an integer from 5–500; using the default.");
        assertThat(strict.check(6)).isEmpty();
    }

    @Test void numbersAcceptIntegersAndFloatsAndWriteWholeValuesWithoutAFraction() {
        var kind = SettingKind.number(.5, 3);
        assertThat(kind.read(toml("2"), 1f).value()).isEqualTo(2f);
        assertThat(kind.read(toml("1.25"), 1f).value()).isEqualTo(1.25f);
        assertThat(kind.read(toml("nan"), 1f).issues()).singleElement()
            .satisfies(issue -> assertThat(issue.message()).isEqualTo("Use a finite number from 0.5–3; using the default."));
        assertThat(SettingKind.number(6, 72).read(toml("100"), 16f).issues().getFirst().message())
            .isEqualTo("Use a finite number from 6–72; using the default.");
        assertThat(kind.literal(16f)).isEqualTo("16");
        assertThat(kind.literal(.3f)).isEqualTo("0.3");
        assertThat(kind.literal(Float.NaN)).isEqualTo("nan");
        assertThat(SettingKind.number(0, 1).read(toml(kind.literal(.3f)), 0f).value()).isEqualTo(.3f);
    }

    @Test void stringsValidateThenNormalize() {
        var font = SettingKind.string(text -> !text.isBlank(), "Use a nonblank font name without NUL; using the default.");
        assertThat(font.read(toml("' '"), "Mono").issues().getFirst().message())
            .isEqualTo("Use a nonblank font name without NUL; using the default.");
        var theme = SettingKind.string(text -> true, "", String::strip);
        assertThat(theme.read(toml("'  Dracula '"), "Jasper Dark").value()).isEqualTo("Dracula");
        assertThat(font.literal("a \"quoted\" \\ name")).isEqualTo("\"a \\\"quoted\\\" \\\\ name\"");
        assertThat(font.read(toml(font.literal("tab\there")), "x").value()).isEqualTo("tab\there");
    }

    @Test void choicesKeepTheirOrderAndListSortedTokensInMessages() {
        var kind = (SettingKind.Choice<String>) SettingKind.choice(List.of(Map.entry("block", "B"), Map.entry("beam", "I"), Map.entry("underline", "_")));
        assertThat(kind.read(toml("'beam'"), "B").value()).isEqualTo("I");
        assertThat(kind.read(toml("'dot'"), "B").issues().getFirst().message()).isEqualTo("Use beam, block, underline; using the default.");
        assertThat(kind.read(toml("7"), "B").issues().getFirst().message()).isEqualTo("Expected a string; configuration was not applied.");
        assertThat(kind.token("_")).isEqualTo("underline");
        assertThat(kind.literal("I")).isEqualTo("\"beam\"");
        assertThat(kind.tokens()).extracting(Map.Entry::getKey).containsExactly("block", "beam", "underline");
        assertThatIllegalArgumentException().isThrownBy(() -> kind.token("?"));
        assertThat(kind.check("?")).isPresent();
    }

    @Test void stringListsDefaultAsAWholeAndRejectWrongElementTypes() {
        var kind = SettingKind.stringList(text -> !text.isBlank());
        assertThat(kind.read(toml("['a', 'b']"), List.of("d")).value()).containsExactly("a", "b");
        assertThat(kind.read(toml("[]"), List.of("d")).value()).isEmpty();
        var invalid = kind.read(toml("['a', ' ']"), List.of("d"));
        assertThat(invalid.value()).containsExactly("d");
        assertThat(invalid.rejectsFile()).isFalse();
        assertThat(invalid.issues().getFirst().message()).isEqualTo("Invalid string array element; using the default list.");
        var wrong = kind.read(toml("['a', 7]"), List.of("d"));
        assertThat(wrong.rejectsFile()).isTrue();
        assertThat(wrong.issues().getFirst().message()).isEqualTo("Expected an array of strings; configuration was not applied.");
        assertThat(kind.read(toml("'a'"), List.of()).rejectsFile()).isTrue();
        assertThat(kind.literal(List.of("a", "b c"))).isEqualTo("[\"a\", \"b c\"]");
    }

    @Test void stringMapsReportEachEntryAtItsOwnKey() {
        var rules = new SettingKind.MapRules(name -> name.matches("[A-Z_]+"), "Bad name.", Set.of("TERM"), "Reserved.",
            value -> value.indexOf('\0') < 0, "Bad value.", "a string environment value", Reading.Severity.ERROR);
        var kind = SettingKind.stringMap(rules);
        var reading = kind.read(toml("{ OK = 'yes', bad = 'x', TERM = 'y' }"), Map.of());
        assertThat(reading.value()).containsExactly(Map.entry("OK", "yes"));
        assertThat(reading.issues()).extracting(Reading.Issue::subPath, Reading.Issue::severity, Reading.Issue::message)
            .containsExactlyInAnyOrder(tuple(List.of("bad"), Reading.Severity.ERROR, "Bad name."),
                tuple(List.of("TERM"), Reading.Severity.WARNING, "Reserved."));
        var wrong = kind.read(toml("{ A = 1 }"), Map.of());
        assertThat(wrong.rejectsFile()).isTrue();
        assertThat(wrong.issues().getFirst().message()).isEqualTo("Expected a string environment value; configuration was not applied.");
        assertThat(kind.read(toml("'x'"), Map.of()).issues().getFirst().message()).isEqualTo("Expected a table; configuration was not applied.");
        assertThat(kind.literal(Map.of("B", "2", "A", "1"))).isEqualTo("{ A = \"1\", B = \"2\" }");
        assertThat(kind.literal(Map.of())).isEqualTo("{}");
        assertThat(kind.check(Map.of("TERM", "x"))).hasValue("Reserved.");
    }

    @Test void warningTablesIgnoreUnusableEntriesWithoutRejectingTheFile() {
        var rules = new SettingKind.MapRules(name -> name.matches("[a-z.]+"), "Bad id.", Set.of(), "",
            Set.of("card", "off")::contains, "Bad mode.", "a display mode", Reading.Severity.WARNING);
        var kind = SettingKind.stringMap(rules);
        var reading = kind.read(toml("{ 'a.b' = 'card', 'A B' = 'off', c = 'CARD', d = 3 }"), Map.of());
        assertThat(reading.value()).containsExactly(Map.entry("a.b", "card"));
        assertThat(reading.rejectsFile()).isFalse();
        assertThat(reading.issues()).extracting(Reading.Issue::subPath, Reading.Issue::severity, Reading.Issue::message)
            .containsExactlyInAnyOrder(tuple(List.of("A B"), Reading.Severity.WARNING, "Bad id."),
                tuple(List.of("c"), Reading.Severity.WARNING, "Bad mode."),
                tuple(List.of("d"), Reading.Severity.WARNING, "Expected a display mode; entry ignored."));
    }

    @Test void literalsQuoteKeysOnlyWhenNeededAndFormatParsedValues() {
        assertThat(TomlLiterals.key("tab_height")).isEqualTo("tab_height");
        assertThat(TomlLiterals.key("app.shortcuts")).isEqualTo("\"app.shortcuts\"");
        assertThat(TomlLiterals.key("")).isEqualTo("\"\"");
        assertThat(TomlLiterals.path(List.of("keybindings", "app.shortcuts"))).isEqualTo("keybindings.\"app.shortcuts\"");
        assertThat(TomlLiterals.format(toml("[1, 2]"))).isEqualTo("[1, 2]");
        assertThat(TomlLiterals.format(toml("['a', 'b']"))).isEqualTo("[\"a\", \"b\"]");
        assertThat(TomlLiterals.format(toml("2.5"))).isEqualTo("2.5");
        assertThat(TomlLiterals.format(toml("{ x = 1 }"))).isEqualTo("{ x = 1 }");
        assertThatIllegalArgumentException().isThrownBy(() -> TomlLiterals.parseValue("not toml"));
    }
}
