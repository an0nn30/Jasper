# Settings Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Not started. Branch `claude/settings-foundation`, worktree `.worktrees/settings-foundation`.
Record every deviation from this text here and in `docs/STATUS.md`.

**Goal:** Give `config.toml` one declarative schema, the version 2 key layout, a comment-preserving writer
with a single save transaction, an opt-in migration with a backup, and plugin diagnostics attributed to
the plugin's own file, as the foundation for the Settings dialog.

**Architecture:** A new JDK-plus-tomlj package `dev.jasper.app.settings` holds the generic machinery
(`SettingKind`, `Setting`, `SettingsSchema`, `SettingValues`, `TomlDocument`, `SettingWriter`).
`dev.jasper.app.config.CoreSettings` declares every core setting; `ConfigLoader` becomes a schema walker
that feeds `ConfigSnapshot.from`, so no consumer changes. `ConfigService` gains `save(SettingsEdit)` and
`migrate(...)` on one internal read-edit-verify-write pipeline shared with `saveKeybindings`.

**Tech Stack:** Java 25 (JetBrains Runtime), Gradle wrapper, tomlj 1.1.1 (parse only), JUnit 5, AssertJ, Swing.

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-settings-foundation-design.md` (read it first; this plan
argues from it). The spec's "Amendments from planning" section lists the decisions this plan adds.

## Global Constraints

- Build and test only with `./gradlew` from the worktree root; never launch the GUI (`:jasper-app:run`).
- Work only in `/Users/dustin/projects/moray/.worktrees/settings-foundation`; run `git branch --show-current` before every commit and confirm `claude/settings-foundation`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push.
- `dev.jasper.app.settings` depends on the JDK and tomlj only: no other `dev.jasper.*` import.
- `config` may depend on `settings`; `settings` never depends on `config`. The app package graph stays acyclic (`verifyApplicationArchitecture`).
- No SDK (`jasper-sdk`), testkit or plugin (`plugins/`) source changes.
- No interface without two real implementations: use records, sealed interfaces with in-file records, and `java.util.function` types.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; write the en dash in messages as the Java escape `–`.
- Existing diagnostic wording and severities stay the same except where this plan names a new message.
- Version 2 layout: `terminal.font.{family,size,fallback,ligatures,line_height}` (was `font.*`), `terminal.colors` (was `ui.theme.terminal`), `system.run_in_background` (was `background.enabled`); every other key keeps its path.
- Moved-key warning: `Moved to <new key>. Use Migrate Config to update the file.`; superseded: `Superseded by <new key>; ignored.`
- Refusal message from `TomlDocument`: `Can't safely edit <key> here; edit the file by hand.`

## File Structure

New, `jasper-app/src/main/java/dev/jasper/app/settings/`:
- `package-info.java`: package contract.
- `Applies.java`: when a change takes effect.
- `Reading.java`: a kind's result (value plus issues).
- `SettingKind.java`: sealed kinds (bool, integer, number, string, choice, string list, string map) and `MapRules`.
- `TomlLiterals.java`: TOML source text for keys and values.
- `Setting.java`: typed descriptor plus builder.
- `LegacyKey.java`: an old key and the key it moved to.
- `SettingsSchema.java`: ordered registry, alias lookup, known tables, reserved tables, table notes.
- `SettingValues.java`: loaded values with presence and source key.
- `TomlDocument.java`: comment-preserving TOML editor with a built-in safety net.
- `SettingWriter.java`: writes one setting's value (or removal) into a `TomlDocument`.

New, `jasper-app/src/main/java/dev/jasper/app/config/`:
- `CoreSettings.java`: every core setting and `SCHEMA`.
- `SettingsEdit.java`: what `ConfigService.save` writes.
- `ConfigMigration.java`: pure version 1 to version 2 rewrite, and the backup writer.

Modified: `config/ConfigLoader.java`, `ConfigSnapshot.java`, `ConfigTemplate.java`, `ConfigService.java`,
`KeybindingText.java`, `package-info.java`; `application/ConfigurationController.java`,
`JasperApplication.java`; `workspace/WindowContent.java`; `plugins/PluginSettingsFiles.java`,
`PluginSettings.java`, `PluginHost.java`, `PluginRuntime.java`; `bootstrap/ApplicationBootstrap.java`;
`jasper-app/build.gradle.kts`; `config.example.toml`; docs.

---

### Task 1: Setting kinds and TOML literals

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settings/package-info.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settings/Applies.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settings/Reading.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settings/TomlLiterals.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settings/SettingKind.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settings/SettingKindTest.java`

**Interfaces:**
- Produces: `Applies { LIVE, NEW_PANES, NEW_WINDOWS, RESTART }`;
  `Reading<T>(T value, List<Issue> issues)` with `Reading.Severity { WARNING, ERROR }`,
  `Reading.Issue(List<String> subPath, Severity severity, String message, boolean rejectsFile)`, `boolean rejectsFile()`;
  `SettingKind<T>` with `Reading<T> read(Object value, T defaultValue)`, `String literal(T value)`,
  `Optional<String> check(T value)`, factories `bool()`, `integer(int, int, OutOfRange)`, `number(double, double)`,
  `string(Predicate<String>, String)`, `string(Predicate<String>, String, UnaryOperator<String>)`,
  `choice(List<Map.Entry<String, E>>)`, `stringList(Predicate<String>)`, `stringMap(MapRules)`;
  records `Bool`, `IntegerRange`, `NumberRange`, `Text`, `Choice<E>` (with `String token(E)`), `StringList`, `StringMap`, `MapRules`;
  `TomlLiterals.string(String)`, `key(String)`, `path(List<String>)`, `parseValue(String)`, `format(Object)`.

- [ ] **Step 1: Write the failing test**

```java
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
            value -> value.indexOf('\0') < 0, "Bad value.", "a string environment value");
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settings.SettingKindTest"`
Expected: FAIL: compilation errors (`SettingKind`, `Reading`, `TomlLiterals` do not exist).

- [ ] **Step 3: Write the implementation**

`package-info.java`:

```java
/**
 * Declarative settings: typed descriptors, their schema, loaded values and comment-preserving TOML edits.
 * Pure values and text; the caller chooses thread and lifecycle.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.settings;
```

`Applies.java`:

```java
package dev.jasper.app.settings;

/** When a saved change to a setting takes effect. */
public enum Applies { LIVE, NEW_PANES, NEW_WINDOWS, RESTART }
```

`Reading.java`:

```java
package dev.jasper.app.settings;

import java.util.List;
import java.util.Objects;

/**
 * What a {@link SettingKind} made of one TOML value: the value to use and any problems. An issue that
 * {@linkplain Issue#rejectsFile() rejects the file} means the value had the wrong TOML type.
 *
 * @param value the value to use, the default when the file's value was unusable
 * @param issues problems found, in the order found
 */
public record Reading<T>(T value, List<Issue> issues) {
    /** How serious an issue is. */
    public enum Severity { WARNING, ERROR }

    /**
     * One problem.
     *
     * @param subPath the key below the setting's own key where it was found; empty for the key itself
     * @param severity how serious it is
     * @param message plain text for the user
     * @param rejectsFile whether the whole file must be rejected (a wrong TOML type)
     */
    public record Issue(List<String> subPath, Severity severity, String message, boolean rejectsFile) {
        public Issue {
            subPath = List.copyOf(subPath);
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(message, "message");
        }
    }

    public Reading {
        issues = List.copyOf(issues);
    }

    static <T> Reading<T> ok(T value) { return new Reading<>(value, List.of()); }

    static <T> Reading<T> wrongType(T fallback, String expected) {
        return new Reading<>(fallback, List.of(new Issue(List.of(), Severity.ERROR,
            "Expected " + expected + "; configuration was not applied.", true)));
    }

    static <T> Reading<T> replaced(T value, Severity severity, String message) {
        return new Reading<>(value, List.of(new Issue(List.of(), severity, message, false)));
    }

    /** Whether any issue rejects the whole file. */
    public boolean rejectsFile() { return issues.stream().anyMatch(Issue::rejectsFile); }
}
```

`TomlLiterals.java`:

```java
package dev.jasper.app.settings;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** TOML source text for keys and values. */
public final class TomlLiterals {
    private TomlLiterals() { }

    /** A basic string: quoted, with every character TOML requires escaped. */
    public static String string(String value) { return "\"" + Toml.tomlEscape(value) + "\""; }

    /** One key: bare when TOML allows it, otherwise quoted. */
    public static String key(String key) {
        boolean bare = !key.isEmpty() && key.chars().allMatch(c -> c < 128 && (Character.isLetterOrDigit(c) || c == '_' || c == '-'));
        return bare ? key : string(key);
    }

    /** A dotted key such as {@code terminal.font.size} or {@code keybindings."app.shortcuts"}. */
    public static String path(List<String> path) {
        return path.stream().map(TomlLiterals::key).collect(Collectors.joining("."));
    }

    /** Parses one value's source text; throws IllegalArgumentException when it is not a TOML value. */
    public static Object parseValue(String literal) {
        var parsed = Toml.parse("v = " + literal);
        if (parsed.hasErrors()) throw new IllegalArgumentException("Not a TOML value: " + literal);
        return parsed.get(List.of("v"));
    }

    /** Source text for a value tomlj parsed: strings, numbers, booleans, dates and times, arrays and tables. */
    public static String format(Object value) {
        return switch (value) {
            case String text -> string(text);
            case Long number -> number.toString();
            case Double number -> Double.isNaN(number) ? "nan"
                : Double.isInfinite(number) ? (number > 0 ? "inf" : "-inf") : number.toString();
            case Boolean flag -> flag.toString();
            case TomlArray array -> {
                var items = new ArrayList<String>();
                for (int index = 0; index < array.size(); index++) items.add(format(array.get(index)));
                yield "[" + String.join(", ", items) + "]";
            }
            case TomlTable table -> table.keySet().stream()
                .map(key -> key(key) + " = " + format(table.get(List.of(key))))
                .collect(Collectors.joining(", ", "{ ", " }"));
            case TemporalAccessor time -> time.toString();
            default -> throw new IllegalArgumentException("Unsupported TOML value: " + value.getClass().getName());
        };
    }
}
```

`SettingKind.java`:

```java
package dev.jasper.app.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * How one setting's TOML value is read, checked and written. Messages and severities match what the
 * configuration loader has always reported, so moving a setting onto a kind changes no diagnostic.
 */
public sealed interface SettingKind<T> {
    /** What an out-of-range integer does: use the nearest bound with a warning, or the default with an error. */
    enum OutOfRange { CLAMP, DEFAULT }

    /** Reads a value tomlj parsed; {@code defaultValue} stands in for anything unusable. */
    Reading<T> read(Object value, T defaultValue);

    /** The TOML source text for {@code value}. */
    String literal(T value);

    /** Empty when {@code value} could be saved; otherwise the message a file holding it would produce. */
    default Optional<String> check(T value) {
        Object parsed;
        try {
            parsed = TomlLiterals.parseValue(literal(value));
        } catch (IllegalArgumentException | ClassCastException | NullPointerException invalid) {
            return Optional.of("Not a valid value: " + value + ".");
        }
        return read(parsed, value).issues().stream().map(Reading.Issue::message).findFirst();
    }

    static SettingKind<Boolean> bool() { return new Bool(); }

    static SettingKind<Integer> integer(int min, int max, OutOfRange outOfRange) { return new IntegerRange(min, max, outOfRange); }

    static SettingKind<Float> number(double min, double max) { return new NumberRange(min, max); }

    static SettingKind<String> string(Predicate<String> valid, String invalidMessage) {
        return new Text(valid, invalidMessage, UnaryOperator.identity());
    }

    static SettingKind<String> string(Predicate<String> valid, String invalidMessage, UnaryOperator<String> normalize) {
        return new Text(valid, invalidMessage, normalize);
    }

    /** Tokens in the order a picker should list them. */
    static <E> SettingKind<E> choice(List<Map.Entry<String, E>> tokens) { return new Choice<>(tokens); }

    static SettingKind<List<String>> stringList(Predicate<String> validElement) { return new StringList(validElement); }

    static SettingKind<Map<String, String>> stringMap(MapRules rules) { return new StringMap(rules); }

    record Bool() implements SettingKind<Boolean> {
        @Override public Reading<Boolean> read(Object value, Boolean defaultValue) {
            return value instanceof Boolean flag ? Reading.ok(flag) : Reading.wrongType(defaultValue, "a boolean");
        }
        @Override public String literal(Boolean value) { return value.toString(); }
    }

    record IntegerRange(int min, int max, OutOfRange outOfRange) implements SettingKind<Integer> {
        public IntegerRange {
            if (min > max) throw new IllegalArgumentException("min exceeds max");
            Objects.requireNonNull(outOfRange, "outOfRange");
        }
        @Override public Reading<Integer> read(Object value, Integer defaultValue) {
            if (!(value instanceof Long number)) return Reading.wrongType(defaultValue, "an integer");
            if (number >= min && number <= max) return Reading.ok(number.intValue());
            String range = "Use an integer from " + min + "–" + max;
            if (outOfRange == OutOfRange.DEFAULT)
                return Reading.replaced(defaultValue, Reading.Severity.ERROR, range + "; using the default.");
            int bound = number < min ? min : max;
            return Reading.replaced(bound, Reading.Severity.WARNING, range + "; using " + bound + ".");
        }
        @Override public String literal(Integer value) { return value.toString(); }
    }

    record NumberRange(double min, double max) implements SettingKind<Float> {
        @Override public Reading<Float> read(Object value, Float defaultValue) {
            if (!(value instanceof Long) && !(value instanceof Double)) return Reading.wrongType(defaultValue, "a number");
            double number = ((Number) value).doubleValue();
            if (Double.isFinite(number) && number >= min && number <= max) return Reading.ok((float) number);
            return Reading.replaced(defaultValue, Reading.Severity.ERROR,
                "Use a finite number from " + bound(min) + "–" + bound(max) + "; using the default.");
        }
        @Override public String literal(Float value) {
            float number = value;
            if (Float.isNaN(number)) return "nan";
            if (Float.isInfinite(number)) return number > 0 ? "inf" : "-inf";
            if (number == Math.rint(number) && Math.abs(number) < 1e15) return Long.toString((long) number);
            return Float.toString(number);
        }
        /** Whole bounds print without a fraction, so messages read "6–72". */
        private static String bound(double value) {
            return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
        }
    }

    record Text(Predicate<String> valid, String invalidMessage, UnaryOperator<String> normalize) implements SettingKind<String> {
        @Override public Reading<String> read(Object value, String defaultValue) {
            if (!(value instanceof String text)) return Reading.wrongType(defaultValue, "a string");
            if (!valid.test(text)) return Reading.replaced(defaultValue, Reading.Severity.ERROR, invalidMessage);
            return Reading.ok(normalize.apply(text));
        }
        @Override public String literal(String value) { return TomlLiterals.string(value); }
    }

    record Choice<E>(List<Map.Entry<String, E>> tokens) implements SettingKind<E> {
        public Choice {
            tokens = List.copyOf(tokens);
            if (tokens.isEmpty()) throw new IllegalArgumentException("A choice needs tokens");
        }
        @Override public Reading<E> read(Object value, E defaultValue) {
            if (!(value instanceof String text)) return Reading.wrongType(defaultValue, "a string");
            for (var token : tokens) if (token.getKey().equals(text)) return Reading.ok(token.getValue());
            return Reading.replaced(defaultValue, Reading.Severity.ERROR, "Use "
                + tokens.stream().map(Map.Entry::getKey).sorted().collect(Collectors.joining(", ")) + "; using the default.");
        }
        @Override public String literal(E value) { return TomlLiterals.string(token(value)); }
        /** The token that stands for {@code value}. */
        public String token(E value) {
            for (var token : tokens) if (token.getValue().equals(value)) return token.getKey();
            throw new IllegalArgumentException("Not one of this setting's choices: " + value);
        }
    }

    record StringList(Predicate<String> validElement) implements SettingKind<List<String>> {
        @Override public Reading<List<String>> read(Object value, List<String> defaultValue) {
            if (!(value instanceof TomlArray array)) return Reading.wrongType(defaultValue, "an array of strings");
            var values = new ArrayList<String>();
            var issues = new ArrayList<Reading.Issue>();
            for (int index = 0; index < array.size(); index++) {
                Object element = array.get(index);
                if (!(element instanceof String text))
                    issues.add(new Reading.Issue(List.of(), Reading.Severity.ERROR,
                        "Expected an array of strings; configuration was not applied.", true));
                else if (!validElement.test(text))
                    issues.add(new Reading.Issue(List.of(), Reading.Severity.ERROR,
                        "Invalid string array element; using the default list.", false));
                else values.add(text);
            }
            return new Reading<>(issues.isEmpty() ? List.copyOf(values) : defaultValue, issues);
        }
        @Override public String literal(List<String> value) {
            return value.stream().map(TomlLiterals::string).collect(Collectors.joining(", ", "[", "]"));
        }
    }

    /**
     * The rules a table of string values follows. Each message is the whole diagnostic text.
     *
     * @param validName which entry names are allowed
     * @param invalidName the error for any other name; the entry is omitted
     * @param reserved names the application owns
     * @param reservedName the warning for a reserved name; the entry is omitted
     * @param validValue which values are allowed
     * @param invalidValue the error for any other value; the entry is omitted
     * @param valueType what a value must be, for the wrong-type message ("a string environment value")
     */
    record MapRules(Predicate<String> validName, String invalidName, Set<String> reserved, String reservedName,
                    Predicate<String> validValue, String invalidValue, String valueType) {
        public MapRules { reserved = Set.copyOf(reserved); }
    }

    record StringMap(MapRules rules) implements SettingKind<Map<String, String>> {
        @Override public Reading<Map<String, String>> read(Object value, Map<String, String> defaultValue) {
            if (!(value instanceof TomlTable table)) return Reading.wrongType(defaultValue, "a table");
            var values = new LinkedHashMap<String, String>();
            var issues = new ArrayList<Reading.Issue>();
            for (String name : table.keySet()) {
                Object entry = table.get(List.of(name));
                List<String> at = List.of(name);
                if (!(entry instanceof String text))
                    issues.add(new Reading.Issue(at, Reading.Severity.ERROR,
                        "Expected " + rules.valueType() + "; configuration was not applied.", true));
                else if (!rules.validName().test(name)) issues.add(new Reading.Issue(at, Reading.Severity.ERROR, rules.invalidName(), false));
                else if (rules.reserved().contains(name)) issues.add(new Reading.Issue(at, Reading.Severity.WARNING, rules.reservedName(), false));
                else if (!rules.validValue().test(text)) issues.add(new Reading.Issue(at, Reading.Severity.ERROR, rules.invalidValue(), false));
                else values.put(name, text);
            }
            return new Reading<>(Map.copyOf(values), issues);
        }
        @Override public String literal(Map<String, String> value) {
            if (value.isEmpty()) return "{}";
            return value.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> TomlLiterals.key(entry.getKey()) + " = " + TomlLiterals.string(entry.getValue()))
                .collect(Collectors.joining(", ", "{ ", " }"));
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settings.SettingKindTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/settings jasper-app/src/test/java/dev/jasper/app/settings
git commit -m "Add setting kinds and TOML literals

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Setting, schema and loaded values

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settings/Setting.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settings/LegacyKey.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settings/SettingsSchema.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settings/SettingValues.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settings/SettingsSchemaTest.java`

**Interfaces:**
- Consumes: Task 1's `SettingKind`, `Applies`, `TomlLiterals`.
- Produces: `Setting<T>(List<String> key, SettingKind<T> kind, T defaultValue, Optional<T> example, List<List<String>> aliases, String label, String description, List<String> keywords, Applies applies)` with `Setting.of(String dottedKey, SettingKind<T>, T)` returning `Setting.Builder<T>` (`alias`, `label`, `description`, `keywords(String...)`, `applies`, `example`, `build`), `name()`, `table()`, `leaf()`;
  `LegacyKey(List<String> from, List<String> to)`;
  `SettingsSchema.of(List<Setting<?>>, Set<List<String>> reserved, Map<List<String>, String> tableNotes)`, `settings()`, `find(List<String>)`, `aliasOf(List<String>)`, `knownTable(List<String>)`, `reserved(List<String>)`, `tableNote(List<String>)`, static `startsWith(List<String> path, List<String> prefix)`;
  `SettingValues.defaults(SettingsSchema)`, `SettingValues.builder(SettingsSchema)` returning `SettingValues.Builder` (`put(Setting<T>, T, List<String> source)`, `present(Setting<?>)`, `build()`), `get(Setting<T>)`, `present(Setting<?>)`, `source(Setting<?>)`, `saved(Setting<T>)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.settings;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettingsSchemaTest {
    private static final Setting<Integer> HEIGHT = Setting.of("window.tab_height",
        SettingKind.integer(20, 72, SettingKind.OutOfRange.CLAMP), 30).label("Tab height").build();
    private static final Setting<Float> SIZE = Setting.of("terminal.font.size", SettingKind.number(6, 72), 16f)
        .label("Font size").alias("font.size").keywords("font", "points").applies(Applies.LIVE).build();
    private static final SettingsSchema SCHEMA = SettingsSchema.of(List.of(HEIGHT, SIZE),
        Set.of(List.of("keybindings")), Map.of(List.of("window"), "Chrome."));

    @Test void settingsDescribeTheirKeyAndRequireALabel() {
        assertThat(SIZE.name()).isEqualTo("terminal.font.size");
        assertThat(SIZE.table()).containsExactly("terminal", "font");
        assertThat(SIZE.leaf()).isEqualTo("size");
        assertThat(SIZE.aliases()).containsExactly(List.of("font", "size"));
        assertThat(SIZE.example()).isEmpty();
        assertThat(Setting.of("ui.font.size", SettingKind.number(8, 32), 0f).label("UI font size").example(14f).build().example()).hasValue(14f);
        assertThatIllegalArgumentException().isThrownBy(() -> Setting.of("a.b", SettingKind.bool(), true).build());
        assertThat(Setting.of("keybindings.\"app.shortcuts\"", SettingKind.bool(), true).label("x").build().name())
            .isEqualTo("keybindings.\"app.shortcuts\"");
    }

    @Test void theSchemaFindsKeysAliasesAndTables() {
        assertThat(SCHEMA.settings()).containsExactly(HEIGHT, SIZE);
        assertThat(SCHEMA.find(List.of("terminal", "font", "size"))).containsSame(SIZE);
        assertThat(SCHEMA.find(List.of("font", "size"))).isEmpty();
        assertThat(SCHEMA.aliasOf(List.of("font", "size"))).containsSame(SIZE);
        assertThat(SCHEMA.knownTable(List.of("terminal"))).isTrue();
        assertThat(SCHEMA.knownTable(List.of("terminal", "font"))).isTrue();
        assertThat(SCHEMA.knownTable(List.of("font"))).as("old tables stay known").isTrue();
        assertThat(SCHEMA.knownTable(List.of("terminal", "font", "size"))).as("a key is not a table").isFalse();
        assertThat(SCHEMA.reserved(List.of("keybindings"))).isTrue();
        assertThat(SCHEMA.tableNote(List.of("window"))).hasValue("Chrome.");
        assertThat(SCHEMA.tableNote(List.of("terminal"))).isEmpty();
    }

    @Test void theSchemaRejectsCollidingKeys() {
        var duplicate = Setting.of("window.tab_height", SettingKind.bool(), true).label("x").build();
        assertThatIllegalArgumentException().isThrownBy(() -> SettingsSchema.of(List.of(HEIGHT, duplicate), Set.of(), Map.of()))
            .withMessageContaining("window.tab_height");
        var aliasOnKey = Setting.of("other.key", SettingKind.bool(), true).label("x").alias("window.tab_height").build();
        assertThatIllegalArgumentException().isThrownBy(() -> SettingsSchema.of(List.of(HEIGHT, aliasOnKey), Set.of(), Map.of()));
        var inside = Setting.of("window.tab_height.more", SettingKind.bool(), true).label("x").build();
        assertThatIllegalArgumentException().isThrownBy(() -> SettingsSchema.of(List.of(HEIGHT, inside), Set.of(), Map.of()))
            .withMessageContaining("lies inside");
        var reserved = Setting.of("keybindings.copy", SettingKind.bool(), true).label("x").build();
        assertThatIllegalArgumentException().isThrownBy(() -> SettingsSchema.of(List.of(reserved), Set.of(List.of("keybindings")), Map.of()))
            .withMessageContaining("reserved");
    }

    @Test void valuesRecordPresenceAndTheKeyTheyCameFrom() {
        var values = SettingValues.builder(SCHEMA).put(SIZE, 18f, List.of("font", "size")).build();
        assertThat(values.get(SIZE)).isEqualTo(18f);
        assertThat(values.present(SIZE)).isTrue();
        assertThat(values.source(SIZE)).hasValue(List.of("font", "size"));
        assertThat(values.saved(SIZE)).hasValue(18f);
        assertThat(values.get(HEIGHT)).isEqualTo(30);
        assertThat(values.present(HEIGHT)).isFalse();
        assertThat(values.saved(HEIGHT)).isEqualTo(Optional.empty());
        assertThat(SettingValues.defaults(SCHEMA).get(SIZE)).isEqualTo(16f);
        assertThat(values).isEqualTo(SettingValues.builder(SCHEMA).put(SIZE, 18f, List.of("font", "size")).build());
        var stranger = Setting.of("x.y", SettingKind.bool(), true).label("x").build();
        assertThatIllegalArgumentException().isThrownBy(() -> values.get(stranger));
        assertThat(new LegacyKey(List.of("font", "size"), List.of("terminal", "font", "size")).to()).containsExactly("terminal", "font", "size");
        assertThat(SettingsSchema.startsWith(List.of("a", "b"), List.of("a"))).isTrue();
        assertThat(SettingsSchema.startsWith(List.of("a"), List.of("a", "b"))).isFalse();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settings.SettingsSchemaTest"`
Expected: FAIL: compilation errors (`Setting`, `SettingsSchema`, `SettingValues`, `LegacyKey` do not exist).

- [ ] **Step 3: Write the implementation**

`LegacyKey.java`:

```java
package dev.jasper.app.settings;

import java.util.List;

/**
 * An old key found in a file, and the current key it moved to.
 *
 * @param from the key as the file wrote it
 * @param to the setting's current key
 */
public record LegacyKey(List<String> from, List<String> to) {
    public LegacyKey {
        from = List.copyOf(from);
        to = List.copyOf(to);
    }
}
```

`Setting.java`:

```java
package dev.jasper.app.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.tomlj.Toml;

/**
 * One saved setting: where it lives, how it is read and written, its default and how people find it.
 *
 * @param key the full key path, such as {@code [terminal, font, size]}
 * @param kind how its value is read, checked and written
 * @param defaultValue the value when the file does not set it
 * @param example a value the generated template shows instead of the default (for a default that is not a
 *                writable value, such as a platform-sized font)
 * @param aliases older keys still read as this setting
 * @param label a short name for people
 * @param description plain text; each line becomes a comment line in the generated template
 * @param keywords extra words search should match
 * @param applies when a saved change takes effect
 */
public record Setting<T>(List<String> key, SettingKind<T> kind, T defaultValue, Optional<T> example,
                         List<List<String>> aliases, String label, String description, List<String> keywords,
                         Applies applies) {
    public Setting {
        key = List.copyOf(key);
        if (key.isEmpty()) throw new IllegalArgumentException("A setting needs a key");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(example, "example");
        aliases = aliases.stream().map(List::copyOf).toList();
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A setting needs a label: " + TomlLiterals.path(key));
        Objects.requireNonNull(description, "description");
        keywords = List.copyOf(keywords);
        Objects.requireNonNull(applies, "applies");
    }

    /** Starts a setting at a dotted TOML key, such as {@code "terminal.font.size"}. */
    public static <T> Builder<T> of(String dottedKey, SettingKind<T> kind, T defaultValue) {
        return new Builder<>(Toml.parseDottedKey(dottedKey), kind, defaultValue);
    }

    /** The key as TOML writes it, for messages. */
    public String name() { return TomlLiterals.path(key); }

    /** The table that holds this setting's key. */
    public List<String> table() { return key.subList(0, key.size() - 1); }

    /** The key's last component. */
    public String leaf() { return key.getLast(); }

    /** Caller-confined construction; {@link #build} validates. */
    public static final class Builder<T> {
        private final List<String> key;
        private final SettingKind<T> kind;
        private final T defaultValue;
        private final List<List<String>> aliases = new ArrayList<>();
        private T example;
        private String label;
        private String description = "";
        private List<String> keywords = List.of();
        private Applies applies = Applies.LIVE;

        private Builder(List<String> key, SettingKind<T> kind, T defaultValue) {
            this.key = key; this.kind = kind; this.defaultValue = defaultValue;
        }

        public Builder<T> alias(String dottedKey) { aliases.add(Toml.parseDottedKey(dottedKey)); return this; }
        public Builder<T> label(String value) { label = value; return this; }
        public Builder<T> description(String value) { description = value; return this; }
        public Builder<T> keywords(String... values) { keywords = List.of(values); return this; }
        public Builder<T> applies(Applies value) { applies = value; return this; }
        public Builder<T> example(T value) { example = value; return this; }

        public Setting<T> build() {
            return new Setting<>(key, kind, defaultValue, Optional.ofNullable(example), aliases, label, description, keywords, applies);
        }
    }
}
```

`SettingsSchema.java`:

```java
package dev.jasper.app.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An ordered set of settings: lookup by key or old key, the tables their keys imply, tables that other
 * readers own ({@code keybindings}, say), and short notes the generated template prints under table headers.
 */
public final class SettingsSchema {
    private final List<Setting<?>> settings;
    private final Map<List<String>, Setting<?>> byKey = new HashMap<>();
    private final Map<List<String>, Setting<?>> byAlias = new HashMap<>();
    private final Set<List<String>> tables = new HashSet<>();
    private final Set<List<String>> reserved;
    private final Map<List<String>, String> notes;

    private SettingsSchema(List<Setting<?>> settings, Set<List<String>> reserved, Map<List<String>, String> notes) {
        this.settings = List.copyOf(settings);
        this.reserved = reserved.stream().map(List::copyOf).collect(Collectors.toUnmodifiableSet());
        this.notes = Map.copyOf(notes);
        var paths = new ArrayList<List<String>>();
        for (Setting<?> setting : this.settings) {
            claim(paths, setting.key());
            byKey.put(setting.key(), setting);
            for (List<String> alias : setting.aliases()) {
                claim(paths, alias);
                byAlias.put(alias, setting);
            }
        }
        for (int i = 0; i < paths.size(); i++)
            for (int j = 0; j < paths.size(); j++)
                if (i != j && paths.get(j).size() > paths.get(i).size() && startsWith(paths.get(j), paths.get(i)))
                    throw new IllegalArgumentException(TomlLiterals.path(paths.get(j)) + " lies inside the setting " + TomlLiterals.path(paths.get(i)));
        for (List<String> table : this.reserved)
            for (List<String> path : paths)
                if (startsWith(path, table) || startsWith(table, path))
                    throw new IllegalArgumentException(TomlLiterals.path(path) + " overlaps the reserved table " + TomlLiterals.path(table));
        for (List<String> path : paths)
            for (int length = 1; length < path.size(); length++) tables.add(List.copyOf(path.subList(0, length)));
    }

    private static void claim(List<List<String>> paths, List<String> path) {
        if (paths.contains(path)) throw new IllegalArgumentException("Duplicate setting key: " + TomlLiterals.path(path));
        paths.add(path);
    }

    /**
     * Builds a schema.
     *
     * @param settings every setting, in template order
     * @param reserved tables read by their own readers; no setting may overlap one
     * @param tableNotes plain text printed under a table's header in the generated template
     */
    public static SettingsSchema of(List<Setting<?>> settings, Set<List<String>> reserved, Map<List<String>, String> tableNotes) {
        return new SettingsSchema(settings, reserved, tableNotes);
    }

    public List<Setting<?>> settings() { return settings; }

    /** The setting whose current key is {@code key}. */
    public Optional<Setting<?>> find(List<String> key) { return Optional.ofNullable(byKey.get(key)); }

    /** The setting that reads {@code path} as an old key. */
    public Optional<Setting<?>> aliasOf(List<String> path) { return Optional.ofNullable(byAlias.get(path)); }

    /** Whether {@code path} is a table some key or old key lies inside. */
    public boolean knownTable(List<String> path) { return tables.contains(path); }

    /** Whether {@code path} is a table another reader owns. */
    public boolean reserved(List<String> path) { return reserved.contains(path); }

    public Optional<String> tableNote(List<String> table) { return Optional.ofNullable(notes.get(table)); }

    /** Whether {@code path} equals {@code prefix} or lies below it. */
    public static boolean startsWith(List<String> path, List<String> prefix) {
        return path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix);
    }
}
```

`SettingValues.java`:

```java
package dev.jasper.app.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Loaded values: each setting's value, and whether the file set it and under which key. Immutable. */
public final class SettingValues {
    private record Entry(Object value, List<String> source) { }

    private final SettingsSchema schema;
    private final Map<List<String>, Entry> entries;

    private SettingValues(SettingsSchema schema, Map<List<String>, Entry> entries) {
        this.schema = schema;
        this.entries = Map.copyOf(entries);
    }

    /** Every setting at its default; nothing present. */
    public static SettingValues defaults(SettingsSchema schema) { return new SettingValues(schema, Map.of()); }

    public static Builder builder(SettingsSchema schema) { return new Builder(schema); }

    /** The value in force: the file's, or the default. */
    @SuppressWarnings("unchecked")
    public <T> T get(Setting<T> setting) {
        Entry entry = entries.get(known(schema, setting));
        return entry == null ? setting.defaultValue() : (T) entry.value();
    }

    /** Whether the file set this setting, under its current key or an old one. */
    public boolean present(Setting<?> setting) { return entries.containsKey(known(schema, setting)); }

    /** The key the file set it under. */
    public Optional<List<String>> source(Setting<?> setting) {
        return Optional.ofNullable(entries.get(known(schema, setting))).map(Entry::source);
    }

    /** The saved value, or empty when the file does not set it. */
    public <T> Optional<T> saved(Setting<T> setting) { return present(setting) ? Optional.of(get(setting)) : Optional.empty(); }

    private static List<String> known(SettingsSchema schema, Setting<?> setting) {
        if (!schema.find(setting.key()).map(setting::equals).orElse(false))
            throw new IllegalArgumentException("Not a setting of this schema: " + setting.name());
        return setting.key();
    }

    @Override public boolean equals(Object other) {
        return other instanceof SettingValues values && schema == values.schema && entries.equals(values.entries);
    }
    @Override public int hashCode() { return entries.hashCode(); }
    @Override public String toString() { return "SettingValues" + entries; }

    /** Caller-confined; the last {@link #put} for a setting wins. */
    public static final class Builder {
        private final SettingsSchema schema;
        private final Map<List<String>, Entry> entries = new LinkedHashMap<>();

        private Builder(SettingsSchema schema) { this.schema = schema; }

        public <T> Builder put(Setting<T> setting, T value, List<String> source) {
            entries.put(known(schema, setting), new Entry(Objects.requireNonNull(value, "value"), List.copyOf(source)));
            return this;
        }

        public boolean present(Setting<?> setting) { return entries.containsKey(known(schema, setting)); }

        public SettingValues build() { return new SettingValues(schema, entries); }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settings.*"`
Expected: PASS (SettingKindTest 8, SettingsSchemaTest 4).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/settings jasper-app/src/test/java/dev/jasper/app/settings
git commit -m "Add settings schema, setting descriptors and loaded values

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: TomlDocument: index, set, remove and the safety net

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settings/TomlDocument.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settings/TomlDocumentTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settings/TomlDocumentPropertyTest.java`

**Interfaces:**
- Consumes: `TomlLiterals`, `SettingsSchema.startsWith`.
- Produces: `TomlDocument.parse(String)`, `text()`, `set(List<String> path, String literal)`, `remove(List<String> path)`, `keysUnder(List<String> prefix)`, static `leaves(String text)` returning `Map<List<String>, Object>` (arrays as `List`, tables inside arrays as `Map`). Every operation throws `IllegalArgumentException("Can't safely edit <key> here; edit the file by hand.")` when it cannot land exactly. Task 4 adds `move`, `renameTable`, `removeTable`, `removeTableIfEmpty` to this class.

- [ ] **Step 1: Write the failing tests**

`TomlDocumentTest.java`:

```java
package dev.jasper.app.settings;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TomlDocumentTest {
    private static String set(String text, List<String> path, String literal) { return TomlDocument.parse(text).set(path, literal).text(); }
    private static String remove(String text, List<String> path) { return TomlDocument.parse(text).remove(path).text(); }

    @Test void replacingAValueKeepsItsCommentAndEverythingElse() {
        assertThat(set("# head\n[window]\ntab_height = 30 # px\n", List.of("window", "tab_height"), "44"))
            .isEqualTo("# head\n[window]\ntab_height = 44 # px\n");
        assertThat(set("k = [\n  1,\n  2,\n] # list\n", List.of("k"), "[3]")).isEqualTo("k = [3] # list\n");
    }

    @Test void aNewKeyGoesAfterTheLastAssignmentOfItsOwnTable() {
        assertThat(set("[window]\na = 1\n\n[terminal]\nb = 2\n", List.of("window", "c"), "3"))
            .isEqualTo("[window]\na = 1\nc = 3\n\n[terminal]\nb = 2\n");
        assertThat(set("[window]\n# nothing yet\n", List.of("window", "a"), "1")).isEqualTo("[window]\na = 1\n# nothing yet\n");
        assertThat(set("['keybindings']\ncopy = 'x'\n", List.of("keybindings", "app.shortcuts"), "\"ctrl+k\""))
            .isEqualTo("['keybindings']\ncopy = 'x'\n\"app.shortcuts\" = \"ctrl+k\"\n");
    }

    @Test void withoutAHeaderANewKeyJoinsDottedSiblingsOrStartsASection() {
        assertThat(set("terminal.font.family = 'x'\n[window]\na = 1\n", List.of("terminal", "font", "size"), "16"))
            .isEqualTo("terminal.font.family = 'x'\nterminal.font.size = 16\n[window]\na = 1\n");
        assertThat(set("[window]\na = 1\n", List.of("terminal", "font", "size"), "16")).isEqualTo("[window]\na = 1\n\n[terminal.font]\nsize = 16\n");
        assertThat(set("[window]\na = 1\n\n", List.of("t", "b"), "2")).isEqualTo("[window]\na = 1\n\n[t]\nb = 2\n");
        assertThat(set("", List.of("t", "b"), "2")).isEqualTo("[t]\nb = 2\n");
        assertThat(set("[a]\nb = 1", List.of("a", "c"), "2")).isEqualTo("[a]\nb = 1\nc = 2\n");
    }

    @Test void rootKeysGoAfterRootAssignmentsOrLeadingComments() {
        assertThat(set("# Jasper\n# settings\n\n[window]\na = 1\n", List.of("version"), "2"))
            .isEqualTo("# Jasper\n# settings\nversion = 2\n\n[window]\na = 1\n");
        assertThat(set("a = 1\n[t]\nb = 2\n", List.of("c"), "3")).isEqualTo("a = 1\nc = 3\n[t]\nb = 2\n");
        assertThat(set("[t]\nb = 2\n", List.of("version"), "2")).isEqualTo("version = 2\n[t]\nb = 2\n");
    }

    @Test void inlineTablesAreRewrittenInPlace() {
        String text = "keybindings = { copy = 'ctrl+c' } # keep\n";
        assertThat(set(text, List.of("keybindings", "paste"), "\"v\"")).isEqualTo("keybindings = { copy = \"ctrl+c\", paste = \"v\" } # keep\n");
        assertThat(remove(text, List.of("keybindings", "copy"))).isEqualTo("keybindings = {} # keep\n");
    }

    @Test void lineEndingsFollowTheFile() {
        assertThat(set("[a]\r\nb = 1\r\n", List.of("a", "c"), "2")).isEqualTo("[a]\r\nb = 1\r\nc = 2\r\n");
        assertThat(remove("[a]\r\nb = 1 # why\r\nc = 2\r\n", List.of("a", "c"))).isEqualTo("[a]\r\nb = 1 # why\r\n");
    }

    @Test void removingKeepsASameLineCommentAsItsOwnLine() {
        assertThat(remove("[a]\nb = 1 # why\nc = 2\n", List.of("a", "b"))).isEqualTo("[a]\n# why\nc = 2\n");
        assertThat(remove("[a]\nb = 1\nc = 2\n", List.of("a", "c"))).isEqualTo("[a]\nb = 1\n");
        assertThat(remove("[k]\ncopy = \"\"\"\nctrl+c\"\"\" # keep\n", List.of("k", "copy"))).isEqualTo("[k]\n# keep\n");
        var unchanged = TomlDocument.parse("[a]\nb = 1\n");
        assertThat(unchanged.remove(List.of("a", "zzz"))).isSameAs(unchanged);
        assertThat(remove("t = { x = 1, y = 2 }\n", List.of("t"))).isEqualTo("");
    }

    @Test void editsThatCannotLandExactlyAreRefusedWithoutChangingAnything() {
        var arrays = TomlDocument.parse("[[hosts]]\nname = 'a'\n");
        assertThatIllegalArgumentException().isThrownBy(() -> arrays.set(List.of("hosts", "name"), "'b'"))
            .withMessage("Can't safely edit hosts.name here; edit the file by hand.");
        assertThatIllegalArgumentException().isThrownBy(() -> arrays.set(List.of("hosts", "port"), "22"));
        var scalar = TomlDocument.parse("a = 1\n");
        assertThatIllegalArgumentException().isThrownBy(() -> scalar.set(List.of("a", "b"), "2"));
        assertThat(scalar.text()).isEqualTo("a = 1\n");
        assertThatIllegalArgumentException().isThrownBy(() -> scalar.set(List.of("t"), "{ x = 1 }"));
        assertThatIllegalArgumentException().isThrownBy(() -> TomlDocument.parse("[broken"));
        assertThatIllegalArgumentException().isThrownBy(() -> scalar.set(List.of("b"), "not toml"));
    }

    @Test void keysUnderListsAssignmentsBelowAPrefixInFileOrder() {
        var document = TomlDocument.parse("[terminal.env]\nA = '1'\nB = '2'\n[terminal]\nbell = 'none'\n");
        assertThat(document.keysUnder(List.of("terminal", "env"))).containsExactly(List.of("terminal", "env", "A"), List.of("terminal", "env", "B"));
        assertThat(document.keysUnder(List.of("terminal"))).hasSize(3);
        assertThat(TomlDocument.leaves("a = [1, 2]\n[t]\nx = 'y'\n")).containsOnlyKeys(List.of("a"), List.of("t", "x"));
    }
}
```

`TomlDocumentPropertyTest.java`:

```java
package dev.jasper.app.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Random edits over real and awkward files: every accepted edit lands exactly, and nothing else changes. */
class TomlDocumentPropertyTest {
    private static final List<String> LEAVES = List.of("alpha", "beta", "gamma-2", "needs space", "dotted.key");
    private static final List<String> LITERALS = List.of("1", "-7", "2.5", "true", "\"text\"", "\"with \\\"quote\\\"\"",
        "[1, 2]", "[\"a\", \"b\"]", "'literal'");

    private static List<String> corpus() throws Exception {
        String example = Files.readString(Path.of(System.getProperty("jasper.projectDir")).resolve("config.example.toml"), StandardCharsets.UTF_8);
        return List.of(example,
            "# top\nname = 'x' # trailing\n[server]\nport = 8080\nhosts = [\n  'a', # first\n  'b',\n]\n\n[server.tls]\nenabled = true\n",
            "a.b.c = 1\n'quoted key' = \"v\"\n[t]\ninline = { x = 1, y = 'z' }\n[t.sub]\n\"dotted.name\" = 2\n",
            "x = 1\r\n[crlf]\r\ny = 2 # c\r\n",
            "",
            "# only comments\n",
            "key = \"\"\"\nmulti\nline\"\"\"\n[z]\nw = '''raw'''\n");
    }

    @Test void randomEditsLandExactlyAndLeaveEverythingElseAlone() throws Exception {
        for (String text : corpus()) for (long seed = 1; seed <= 20; seed++) {
            var random = new Random(seed);
            TomlDocument document = TomlDocument.parse(text);
            var model = new LinkedHashMap<>(TomlDocument.leaves(text));
            int applied = 0, refused = 0;
            for (int step = 0; step < 40; step++) {
                var existing = new ArrayList<>(model.keySet());
                if (!existing.isEmpty() && random.nextInt(4) == 0) {
                    List<String> path = existing.get(random.nextInt(existing.size()));
                    try { document = document.remove(path); model.remove(path); applied++; }
                    catch (IllegalArgumentException refusal) { refused++; }
                } else {
                    List<String> path = randomPath(random, existing);
                    if (conflicts(path, model.keySet())) continue;
                    String literal = LITERALS.get(random.nextInt(LITERALS.size()));
                    try {
                        document = document.set(path, literal);
                        model.put(path, TomlDocument.leaves("v = " + literal).get(List.of("v")));
                        applied++;
                    } catch (IllegalArgumentException refusal) { refused++; }
                }
                assertThat(TomlDocument.leaves(document.text())).as("seed %d step %d:%n%s", seed, step, document.text()).isEqualTo(model);
            }
            assertThat(applied).as("seed %d: applied %d, refused %d", seed, applied, refused).isGreaterThan(refused * 4);
        }
    }

    private static List<String> randomPath(Random random, List<List<String>> existing) {
        if (!existing.isEmpty() && random.nextBoolean()) return existing.get(random.nextInt(existing.size()));
        List<String> parent = switch (random.nextInt(3)) {
            case 0 -> List.of();
            case 1 -> List.of("zeta", "sub");
            default -> {
                if (existing.isEmpty()) yield List.of("zeta");
                List<String> sample = existing.get(random.nextInt(existing.size()));
                yield sample.subList(0, sample.size() - 1);
            }
        };
        var path = new ArrayList<>(parent);
        path.add(LEAVES.get(random.nextInt(LEAVES.size())));
        return List.copyOf(path);
    }

    /** A leaf cannot become a table, nor a table a leaf. */
    private static boolean conflicts(List<String> path, Collection<List<String>> keys) {
        for (List<String> key : keys)
            if (!key.equals(path) && (SettingsSchema.startsWith(key, path) || SettingsSchema.startsWith(path, key))) return true;
        return false;
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settings.TomlDocument*"`
Expected: FAIL: compilation errors (`TomlDocument` does not exist).

- [ ] **Step 3: Write the implementation**

`TomlDocument.java`:

```java
package dev.jasper.app.settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * A TOML file as text, edited in place: comments, blank lines, key order and line endings survive every
 * edit. Each operation returns a new document, and is refused with an IllegalArgumentException unless
 * the result parses to exactly the intended values with every other value unchanged.
 */
public final class TomlDocument {
    private enum Kind { HEADER, ASSIGNMENT }

    /**
     * One logical statement. {@code end} is where its text stops: a comment's {@code #}, the line break, or the
     * end of the file. {@code lineEnd} is just past its last line break. For an assignment, {@code path} is
     * the full key and {@code table} the header it sits under; {@code array} marks array-of-tables content.
     */
    private record Statement(Kind kind, int start, int end, int lineEnd, List<String> path, List<String> table,
                             int valueStart, int valueEnd, boolean array, boolean inline) {
        boolean comment(String text) { return end < text.length() && text.charAt(end) == '#'; }
    }

    private record Edit(int start, int end, String text) { }

    private final String text;
    private final String newline;
    private final List<Statement> statements;
    private final Map<List<String>, Object> values;

    private TomlDocument(String text, Map<List<String>, Object> values) {
        this.text = text;
        this.newline = text.contains("\r\n") ? "\r\n" : "\n";
        this.statements = index(text);
        this.values = Map.copyOf(values);
    }

    /** Indexes {@code text}, which must be valid TOML. */
    public static TomlDocument parse(String text) {
        TomlParseResult parsed = Toml.parse(text);
        if (parsed.hasErrors()) throw new IllegalArgumentException("The file has TOML errors; fix them before saving.");
        return new TomlDocument(text, leaves(parsed));
    }

    public String text() { return text; }

    /** Every value in {@code text} by full key path; arrays become lists and tables inside arrays maps. */
    public static Map<List<String>, Object> leaves(String text) {
        TomlParseResult parsed = Toml.parse(text);
        if (parsed.hasErrors()) throw new IllegalArgumentException("Not valid TOML.");
        return leaves(parsed);
    }

    /** Every assignment at or below {@code prefix}, by full key path, in file order. */
    public List<List<String>> keysUnder(List<String> prefix) {
        return statements.stream()
            .filter(statement -> statement.kind() == Kind.ASSIGNMENT && SettingsSchema.startsWith(statement.path(), prefix))
            .map(Statement::path).toList();
    }

    /** Sets {@code path} to the TOML value {@code literal}: in place when it exists, otherwise inserted. */
    public TomlDocument set(List<String> path, String literal) { return set(path, literal, ""); }

    TomlDocument set(List<String> path, String literal, String comment) {
        requirePath(path);
        Object value = TomlLiterals.parseValue(literal);
        if (value instanceof TomlTable) throw new IllegalArgumentException("Set " + TomlLiterals.path(path) + "'s entries one at a time.");
        var intended = new LinkedHashMap<>(values);
        intended.put(path, plain(value));
        Statement existing = assignment(path);
        if (existing != null) return apply(path, intended, List.of(new Edit(existing.valueStart(), existing.valueEnd(), literal)));
        Statement holder = inlineHolding(path);
        if (holder != null) return apply(path, intended, List.of(rewriteInline(holder, path, Optional.of(literal))));
        for (Edit insertion : insertions(path, literal + comment)) {
            try {
                return apply(path, intended, List.of(insertion));
            } catch (IllegalArgumentException refused) {
                // Another placement may be valid TOML.
            }
        }
        throw refused(path);
    }

    /** Removes {@code path}, or an inline table with everything in it; a same-line comment stays as a comment line. */
    public TomlDocument remove(List<String> path) {
        requirePath(path);
        var intended = new LinkedHashMap<>(values);
        intended.keySet().removeIf(key -> SettingsSchema.startsWith(key, path));
        Statement existing = assignment(path);
        if (existing != null) return apply(path, intended, List.of(delete(existing)));
        Statement holder = inlineHolding(path);
        if (holder != null) return apply(path, intended, List.of(rewriteInline(holder, path, Optional.empty())));
        if (intended.size() == values.size()) return this;
        throw refused(path);
    }

    private Statement assignment(List<String> path) {
        for (Statement statement : statements)
            if (statement.kind() == Kind.ASSIGNMENT && statement.path().equals(path)) {
                if (statement.array()) throw refused(path);
                return statement;
            }
        return null;
    }

    /** The inline table assignment whose value holds {@code path}, if any. */
    private Statement inlineHolding(List<String> path) {
        for (Statement statement : statements)
            if (statement.kind() == Kind.ASSIGNMENT && statement.inline() && path.size() > statement.path().size()
                    && SettingsSchema.startsWith(path, statement.path())) {
                if (statement.array()) throw refused(path);
                return statement;
            }
        return null;
    }

    private Edit rewriteInline(Statement holder, List<String> path, Optional<String> literal) {
        if (path.size() != holder.path().size() + 1) throw refused(path);
        if (!(TomlLiterals.parseValue(text.substring(holder.valueStart(), holder.valueEnd())) instanceof TomlTable table)) throw refused(path);
        var entries = new LinkedHashMap<String, String>();
        for (String key : table.keySet()) {
            Object value = table.get(List.of(key));
            if (value instanceof TomlTable) throw refused(path);
            entries.put(key, TomlLiterals.format(value));
        }
        if (literal.isPresent()) entries.put(path.getLast(), literal.get());
        else entries.remove(path.getLast());
        String inline = entries.isEmpty() ? "{}" : entries.entrySet().stream()
            .map(entry -> TomlLiterals.key(entry.getKey()) + " = " + entry.getValue())
            .collect(Collectors.joining(", ", "{ ", " }"));
        return new Edit(holder.valueStart(), holder.valueEnd(), inline);
    }

    /**
     * Candidate placements for a new key, best first: after the last assignment of its own table (the root
     * counts as always present), beside a dotted sibling in the same table, then a new section at the end.
     */
    private List<Edit> insertions(List<String> path, String valueText) {
        List<String> parent = path.subList(0, path.size() - 1);
        String leaf = TomlLiterals.key(path.getLast()) + " = " + valueText;
        var edits = new ArrayList<Edit>();
        sectionEnd(parent).ifPresent(at -> edits.add(line(at, leaf)));
        for (Statement statement : statements)
            if (statement.kind() == Kind.ASSIGNMENT && !statement.array() && !statement.inline()
                    && statement.path().size() == path.size() && statement.path().subList(0, parent.size()).equals(parent)
                    && statement.table().size() < parent.size() && SettingsSchema.startsWith(parent, statement.table()))
                edits.add(line(statement.lineEnd(),
                    TomlLiterals.path(path.subList(statement.table().size(), path.size())) + " = " + valueText));
        if (!parent.isEmpty()) edits.add(section(parent, leaf));
        return edits;
    }

    /** Where a new line for {@code table} goes: after its last assignment, or just after its header. */
    private Optional<Integer> sectionEnd(List<String> table) {
        int at;
        int from;
        if (table.isEmpty()) {
            at = leadingComments();
            from = 0;
        } else {
            int header = -1;
            for (int index = 0; index < statements.size(); index++) {
                Statement statement = statements.get(index);
                if (statement.kind() == Kind.HEADER && !statement.array() && statement.path().equals(table)) { header = index; break; }
            }
            if (header < 0) return Optional.empty();
            at = statements.get(header).lineEnd();
            from = header + 1;
        }
        for (int index = from; index < statements.size() && statements.get(index).kind() == Kind.ASSIGNMENT; index++)
            at = statements.get(index).lineEnd();
        return Optional.of(at);
    }

    /** Just past the file's opening run of comment lines. */
    private int leadingComments() {
        int at = 0;
        while (at < text.length()) {
            int lineBreak = text.indexOf('\n', at);
            int next = lineBreak < 0 ? text.length() : lineBreak + 1;
            if (!text.substring(at, next).strip().startsWith("#")) break;
            at = next;
        }
        return at;
    }

    private Edit line(int at, String content) {
        String before = at == text.length() && !text.isEmpty() && !text.endsWith("\n") ? newline : "";
        return new Edit(at, at, before + content + newline);
    }

    private Edit section(List<String> table, String content) {
        String before = "";
        if (!text.isEmpty()) {
            before = text.endsWith("\n") ? "" : newline;
            if (!(text.endsWith("\n\n") || text.endsWith("\n\r\n"))) before += newline;
        }
        return new Edit(text.length(), text.length(), before + "[" + TomlLiterals.path(table) + "]" + newline + content + newline);
    }

    /** Deletes a statement; a same-line comment stays behind as a comment line, otherwise the whole line goes. */
    private Edit delete(Statement statement) {
        if (statement.comment(text)) return new Edit(statement.start(), statement.end(), "");
        return new Edit(statement.start(), statement.lineEnd(), "");
    }

    private TomlDocument apply(List<String> path, Map<List<String>, Object> intended, List<Edit> edits) {
        var result = new StringBuilder(text);
        edits.stream().sorted(Comparator.comparingInt(Edit::start).reversed())
            .forEach(edit -> result.replace(edit.start(), edit.end(), edit.text()));
        String next = result.toString();
        TomlParseResult parsed = Toml.parse(next);
        if (parsed.hasErrors() || !leaves(parsed).equals(intended)) throw refused(path);
        return new TomlDocument(next, intended);
    }

    private static IllegalArgumentException refused(List<String> path) {
        return new IllegalArgumentException("Can't safely edit " + TomlLiterals.path(path) + " here; edit the file by hand.");
    }

    private static void requirePath(List<String> path) {
        if (path.isEmpty()) throw new IllegalArgumentException("A key path needs at least one key.");
    }

    private static Map<List<String>, Object> leaves(TomlTable table) {
        var result = new LinkedHashMap<List<String>, Object>();
        collect(List.of(), table, result);
        return result;
    }

    private static void collect(List<String> parent, TomlTable table, Map<List<String>, Object> into) {
        for (String key : table.keySet()) {
            var path = new ArrayList<>(parent);
            path.add(key);
            Object value = table.get(List.of(key));
            if (value instanceof TomlTable nested) collect(List.copyOf(path), nested, into);
            else into.put(List.copyOf(path), plain(value));
        }
    }

    private static Object plain(Object value) {
        if (value instanceof TomlArray array) {
            var items = new ArrayList<Object>();
            for (int index = 0; index < array.size(); index++) items.add(plain(array.get(index)));
            return List.copyOf(items);
        }
        if (value instanceof TomlTable table) {
            var map = new LinkedHashMap<String, Object>();
            for (String key : table.keySet()) map.put(key, plain(table.get(List.of(key))));
            return Map.copyOf(map);
        }
        return value;
    }

    private static List<Statement> index(String text) {
        var result = new ArrayList<Statement>();
        var arrays = new ArrayList<List<String>>();
        List<String> table = List.of();
        boolean array = false;
        for (int start = 0; start < text.length();) {
            int end = statementEnd(text, start);
            int lineEnd = end;
            while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;
            if (lineEnd < text.length()) lineEnd++;
            String statement = text.substring(start, end).strip();
            if (statement.startsWith("[[")) {
                table = List.copyOf(Toml.parseDottedKey(statement.substring(2, statement.lastIndexOf("]]")).strip()));
                arrays.add(table);
                array = true;
                result.add(new Statement(Kind.HEADER, start, end, lineEnd, table, List.of(), -1, -1, true, false));
            } else if (statement.startsWith("[")) {
                List<String> header = List.copyOf(Toml.parseDottedKey(statement.substring(1, statement.lastIndexOf(']')).strip()));
                table = header;
                array = arrays.stream().anyMatch(prefix -> SettingsSchema.startsWith(header, prefix));
                result.add(new Statement(Kind.HEADER, start, end, lineEnd, table, List.of(), -1, -1, array, false));
            } else if (!statement.isEmpty()) {
                int equals = assignmentEquals(text, start, end);
                if (equals < 0) throw new IllegalArgumentException("Cannot read the assignment at offset " + start + ".");
                var path = new ArrayList<>(table);
                path.addAll(Toml.parseDottedKey(text.substring(start, equals).strip()));
                int valueStart = equals + 1;
                while (valueStart < end && Character.isWhitespace(text.charAt(valueStart))) valueStart++;
                int valueEnd = end;
                while (valueEnd > valueStart && Character.isWhitespace(text.charAt(valueEnd - 1))) valueEnd--;
                result.add(new Statement(Kind.ASSIGNMENT, start, end, lineEnd, List.copyOf(path), table, valueStart, valueEnd,
                    array, valueStart < end && text.charAt(valueStart) == '{'));
            }
            start = lineEnd;
        }
        return List.copyOf(result);
    }

    private static int assignmentEquals(String text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '\'' || c == '"') i = stringEnd(text, i) - 1;
            else if (c == '=') return i;
        }
        return -1;
    }

    /** TOML logical statements, including multiline strings and arrays, without trailing comments. */
    private static int statementEnd(String text, int start) {
        int nesting = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' || c == '"') i = stringEnd(text, i) - 1;
            else if (c == '#') {
                if (nesting == 0) return i;
                while (i < text.length() && text.charAt(i) != '\n') i++;
            } else if (c == '[' || c == '{') nesting++;
            else if (c == ']' || c == '}') nesting--;
            else if (c == '\n' && nesting == 0) return i;
        }
        return text.length();
    }

    private static int stringEnd(String text, int start) {
        char quote = text.charAt(start);
        boolean triple = start + 2 < text.length() && text.charAt(start + 1) == quote && text.charAt(start + 2) == quote;
        for (int i = start + (triple ? 3 : 1); i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && quote == '"') { i++; continue; }
            if (c != quote) continue;
            if (!triple) return i + 1;
            int end = i;
            while (end < text.length() && text.charAt(end) == quote) end++;
            if (end - i >= 3) return end;
            i = end - 1;
        }
        throw new IllegalArgumentException("Unterminated TOML string.");
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settings.*"`
Expected: PASS. If the property test fails, its message prints the seed, step and text; fix `TomlDocument`, not the test. If a case is refused more often than the ratio allows, investigate the placement rule before loosening the ratio, and record any change in the status banner.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/settings/TomlDocument.java jasper-app/src/test/java/dev/jasper/app/settings/TomlDocumentTest.java jasper-app/src/test/java/dev/jasper/app/settings/TomlDocumentPropertyTest.java
git commit -m "Add a comment-preserving TOML document editor

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: TomlDocument move, table operations, and shortcuts through it

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settings/TomlDocument.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/KeybindingText.java` (replace the whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/package-info.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settings/TomlDocumentTableTest.java`

**Interfaces:**
- Consumes: Task 3's `TomlDocument` internals (`assignment`, `apply`, `delete`, `refused`, `requirePath`, `values`, `statements`, package-private `set(path, literal, comment)`).
- Produces: `move(List<String> from, List<String> to)`, `renameTable(List<String> from, List<String> to)`, `removeTable(List<String> table)`, `removeTableIfEmpty(List<String> table)`. `KeybindingText.update(String, Map<String, Optional<String>>)` keeps its signature.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.settings;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TomlDocumentTableTest {
    @Test void moveCarriesTheValueAsWrittenAndItsComment() {
        var moved = TomlDocument.parse("[ui.theme]\nname = 'x'\nterminal = \"dark\" # always dark\n[terminal]\nscrollback = 5\n")
            .move(List.of("ui", "theme", "terminal"), List.of("terminal", "colors"));
        assertThat(moved.text()).isEqualTo("[ui.theme]\nname = 'x'\n[terminal]\nscrollback = 5\ncolors = \"dark\" # always dark\n");
        var fraction = TomlDocument.parse("font.size = 16.0\n").move(List.of("font", "size"), List.of("terminal", "font", "size"));
        assertThat(fraction.text()).isEqualTo("[terminal.font]\nsize = 16.0\n");
        var taken = TomlDocument.parse("a = 1\nb = 2\n");
        assertThatIllegalArgumentException().isThrownBy(() -> taken.move(List.of("a"), List.of("b")));
        assertThatIllegalArgumentException().isThrownBy(() -> taken.move(List.of("zzz"), List.of("c")));
    }

    @Test void renameTableRewritesHeadersAndRefusesKeysItCannotReach() {
        var renamed = TomlDocument.parse("[font] # mine\n# The font I like\nfamily = 'Iosevka'\n[font.extra]\nx = 1\n")
            .renameTable(List.of("font"), List.of("terminal", "font"));
        assertThat(renamed.text()).isEqualTo("[terminal.font] # mine\n# The font I like\nfamily = 'Iosevka'\n[terminal.font.extra]\nx = 1\n");
        assertThatIllegalArgumentException().isThrownBy(() -> TomlDocument.parse("font.size = 1\n").renameTable(List.of("font"), List.of("terminal", "font")));
        assertThatIllegalArgumentException().isThrownBy(() -> TomlDocument.parse("[font]\nsize = 1\nfont2 = 1\n[x]\nfont.q = 2\n")
            .renameTable(List.of("x"), List.of("font")));
    }

    @Test void removeTableTakesValuesHeadersAndTheBlankLineAboveEachHeader() {
        String text = "[a]\nkeep = 1\n\n[palette.scopes.history]\nenabled = false\n\n[plugins.\"dev.x\"]\nx = 1\n";
        var document = TomlDocument.parse(text).removeTable(List.of("palette", "scopes"));
        assertThat(document.text()).isEqualTo("[a]\nkeep = 1\n\n[plugins.\"dev.x\"]\nx = 1\n");
        assertThat(document.removeTable(List.of("plugins", "dev.x")).text()).isEqualTo("[a]\nkeep = 1\n");
        var dotted = TomlDocument.parse("palette.scopes.history.enabled = false\nb = 1\n").removeTable(List.of("palette", "scopes"));
        assertThat(dotted.text()).isEqualTo("b = 1\n");
        var none = TomlDocument.parse("b = 1\n");
        assertThat(none.removeTable(List.of("nothing"))).isSameAs(none);
    }

    @Test void removeTableIfEmptyOnlyRemovesAHeaderWithNothingUnderIt() {
        var empty = TomlDocument.parse("x = 1\n\n[background]\n\n[ui]\ny = 2\n").removeTableIfEmpty(List.of("background"));
        assertThat(empty.text()).isEqualTo("x = 1\n\n[ui]\ny = 2\n");
        var used = TomlDocument.parse("[background]\nz = 1\n");
        assertThat(used.removeTableIfEmpty(List.of("background"))).isSameAs(used);
        var commented = TomlDocument.parse("[background] # old\n").removeTableIfEmpty(List.of("background"));
        assertThat(commented.text()).isEqualTo("# old\n");
        var absent = TomlDocument.parse("x = 1\n");
        assertThat(absent.removeTableIfEmpty(List.of("background"))).isSameAs(absent);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settings.TomlDocumentTableTest"`
Expected: FAIL: compilation errors (`move`, `renameTable`, `removeTable`, `removeTableIfEmpty` do not exist).

- [ ] **Step 3: Write the implementation**

Add these methods to `TomlDocument` (after `remove`):

```java
    /** Moves {@code from}'s value, exactly as written, and its same-line comment to {@code to}, which must be unset. */
    public TomlDocument move(List<String> from, List<String> to) {
        requirePath(from);
        requirePath(to);
        Statement source = assignment(from);
        if (source == null || source.inline() || values.keySet().stream().anyMatch(key -> SettingsSchema.startsWith(key, to)))
            throw refused(from);
        String value = text.substring(source.valueStart(), source.valueEnd());
        String comment = source.comment(text) ? " " + text.substring(source.end(), lineContentEnd(source)).strip() : "";
        var intended = new LinkedHashMap<>(values);
        intended.remove(from);
        return apply(from, intended, List.of(new Edit(source.start(), source.lineEnd(), ""))).set(to, value, comment);
    }

    /** Renames the {@code [from]} header, and any header below it, to {@code to}. */
    public TomlDocument renameTable(List<String> from, List<String> to) {
        requirePath(from);
        requirePath(to);
        var edits = new ArrayList<Edit>();
        for (Statement statement : statements)
            if (statement.kind() == Kind.HEADER && SettingsSchema.startsWith(statement.path(), from)) {
                if (statement.array()) throw refused(from);
                var renamed = new ArrayList<>(to);
                renamed.addAll(statement.path().subList(from.size(), statement.path().size()));
                int open = text.indexOf('[', statement.start());
                int close = text.lastIndexOf(']', statement.end() - 1) + 1;
                edits.add(new Edit(open, close, "[" + TomlLiterals.path(renamed) + "]"));
            }
        if (edits.isEmpty()) throw refused(from);
        var intended = new LinkedHashMap<List<String>, Object>();
        values.forEach((key, value) -> {
            if (!SettingsSchema.startsWith(key, from)) { intended.put(key, value); return; }
            var renamed = new ArrayList<>(to);
            renamed.addAll(key.subList(from.size(), key.size()));
            intended.put(List.copyOf(renamed), value);
        });
        return apply(from, intended, edits);
    }

    /** Removes every value at or below {@code table}, and every header naming it or a table below it. */
    public TomlDocument removeTable(List<String> table) {
        requirePath(table);
        var intended = new LinkedHashMap<>(values);
        intended.keySet().removeIf(key -> SettingsSchema.startsWith(key, table));
        var edits = new ArrayList<Edit>();
        for (Statement statement : statements) {
            if (!SettingsSchema.startsWith(statement.path(), table)) continue;
            if (statement.array()) throw refused(table);
            edits.add(statement.kind() == Kind.HEADER ? deleteHeader(statement) : delete(statement));
        }
        if (edits.isEmpty() && intended.size() == values.size()) return this;
        return apply(table, intended, edits);
    }

    /** Removes the {@code [table]} header when no assignment follows it before the next header. */
    public TomlDocument removeTableIfEmpty(List<String> table) {
        requirePath(table);
        for (int index = 0; index < statements.size(); index++) {
            Statement statement = statements.get(index);
            if (statement.kind() != Kind.HEADER || statement.array() || !statement.path().equals(table)) continue;
            boolean empty = index + 1 == statements.size() || statements.get(index + 1).kind() == Kind.HEADER;
            return empty ? apply(table, values, List.of(deleteHeader(statement))) : this;
        }
        return this;
    }

    /** Deletes a header line and one blank line just above it; a same-line comment stays behind. */
    private Edit deleteHeader(Statement header) {
        if (header.comment(text)) return new Edit(header.start(), header.end(), "");
        int start = header.start();
        if (start > 0) {
            int previous = text.lastIndexOf('\n', start - 2) + 1;
            if (text.substring(previous, start).isBlank()) start = previous;
        }
        return new Edit(start, header.lineEnd(), "");
    }

    private int lineContentEnd(Statement statement) {
        int at = statement.lineEnd();
        while (at > statement.end() && (text.charAt(at - 1) == '\n' || text.charAt(at - 1) == '\r')) at--;
        return at;
    }
```

Replace `KeybindingText.java` with:

```java
package dev.jasper.app.config;

import dev.jasper.app.settings.TomlDocument;
import dev.jasper.app.settings.TomlLiterals;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.tomlj.Toml;

/** Patches binding assignments, leaving comments and unrelated TOML text byte-for-byte intact. */
final class KeybindingText {
    private KeybindingText() { }

    static String update(String text, Map<String, Optional<String>> changes) {
        if (Toml.parse(text).hasErrors()) throw new IllegalArgumentException("Fix the settings file's TOML errors before saving shortcuts.");
        var document = TomlDocument.parse(text);
        for (var change : new TreeMap<>(changes).entrySet()) {
            List<String> path = List.of("keybindings", change.getKey());
            document = change.getValue().isPresent()
                ? document.set(path, TomlLiterals.string(change.getValue().get()))
                : document.remove(path);
        }
        return document.text();
    }
}
```

In `config/package-info.java`, change the dependency line to:

```java
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.commands, dev.jasper.app.persistence, dev.jasper.app.settings, dev.jasper.terminal.config.
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settings.*" --tests "dev.jasper.app.config.KeybindingSaveTest" --tests "dev.jasper.app.workspace.ShortcutEditorIntegrationTest"`
Expected: PASS. `KeybindingSaveTest` only asserts parsed results and preserved comments, so the new insertion position (after the table's last assignment instead of right after its header) needs no change. If a byte-pinned expectation fails, update it to the new position and record the case in the status banner.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/settings/TomlDocument.java jasper-app/src/main/java/dev/jasper/app/config/KeybindingText.java jasper-app/src/main/java/dev/jasper/app/config/package-info.java jasper-app/src/test/java/dev/jasper/app/settings/TomlDocumentTableTest.java
git commit -m "Move, rename and remove TOML tables; save shortcuts through TomlDocument

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Core settings declarations

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java` (add `from`)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java`

**Interfaces:**
- Consumes: `Setting`, `SettingKind`, `SettingsSchema`, `SettingValues`, `Applies`.
- Produces: `CoreSettings.SCHEMA` and constants `WINDOW_TAB_HEIGHT`, `WINDOW_RAIL_WIDTH`, `WINDOW_TOOLBAR`, `WINDOW_TOOLBAR_HEIGHT`, `WINDOW_STATUS_BAR`, `WINDOW_STATUS_BAR_HEIGHT`, `WINDOW_COLUMNS`, `WINDOW_LINES`, `BUDDY_ENABLED`, `NOTIFICATIONS_LONG_COMMAND_SECONDS`, `SYSTEM_RUN_IN_BACKGROUND`, `PALETTE_MAX_RESULTS`, `TERMINAL_SCROLLBACK`, `TERMINAL_OPTION_AS_META`, `TERMINAL_DIM_INACTIVE_PANES`, `TERMINAL_COPY_ON_SELECT`, `TERMINAL_BELL`, `TERMINAL_ON_EXIT`, `TERMINAL_SHELL_INTEGRATION`, `TERMINAL_COLORS`, `TERMINAL_FONT_FAMILY`, `TERMINAL_FONT_SIZE`, `TERMINAL_FONT_FALLBACK`, `TERMINAL_FONT_LIGATURES`, `TERMINAL_FONT_LINE_HEIGHT`, `TERMINAL_SHELL_PROGRAM`, `TERMINAL_SHELL_ARGS`, `TERMINAL_CURSOR_SHAPE`, `TERMINAL_CURSOR_BLINK`, `TERMINAL_ENV`, `UI_FONT_FAMILY`, `UI_FONT_SIZE`, `UI_THEME_NAME`; package-private `CoreSettings.noNul`, `fontName`; `ConfigSnapshot.from(SettingValues, Map<String, String> keybindings, Map<String, Map<String, Object>> plugins)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.settings.Applies;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import dev.jasper.app.settings.SettingValues;
import dev.jasper.app.settings.TomlLiterals;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreSettingsTest {
    @Test void defaultsBuildTheBuiltInSnapshot() {
        assertThat(ConfigSnapshot.from(SettingValues.defaults(CoreSettings.SCHEMA), Map.of(), Map.of()))
            .isEqualTo(ConfigSnapshot.defaults());
    }

    @Test void everySettingIsDescribedAndItsTemplateValueRoundTrips() {
        assertThat(CoreSettings.SCHEMA.settings()).hasSize(33);
        for (Setting<?> setting : CoreSettings.SCHEMA.settings()) {
            assertThat(setting.label()).as(setting.name()).isNotBlank();
            assertThat(setting.description()).as(setting.name()).isNotBlank();
            assertRoundTrip(setting);
        }
    }

    private static <T> void assertRoundTrip(Setting<T> setting) {
        T value = setting.example().orElse(setting.defaultValue());
        var reading = setting.kind().read(TomlLiterals.parseValue(setting.kind().literal(value)), setting.defaultValue());
        assertThat(reading.issues()).as(setting.name()).isEmpty();
        assertThat(reading.value()).as(setting.name()).isEqualTo(value);
    }

    @Test void theVersionTwoLayoutKeepsOldKeysAsAliases() {
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("font", "size"))).containsSame(CoreSettings.TERMINAL_FONT_SIZE);
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("font", "family"))).containsSame(CoreSettings.TERMINAL_FONT_FAMILY);
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("font", "fallback"))).containsSame(CoreSettings.TERMINAL_FONT_FALLBACK);
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("font", "ligatures"))).containsSame(CoreSettings.TERMINAL_FONT_LIGATURES);
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("font", "line_height"))).containsSame(CoreSettings.TERMINAL_FONT_LINE_HEIGHT);
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("ui", "theme", "terminal"))).containsSame(CoreSettings.TERMINAL_COLORS);
        assertThat(CoreSettings.SCHEMA.aliasOf(List.of("background", "enabled"))).containsSame(CoreSettings.SYSTEM_RUN_IN_BACKGROUND);
        assertThat(CoreSettings.SCHEMA.reserved(List.of("keybindings"))).isTrue();
        assertThat(CoreSettings.SCHEMA.reserved(List.of("plugins"))).isTrue();
        assertThat(CoreSettings.SCHEMA.reserved(List.of("palette", "scopes"))).isTrue();
        assertThat(CoreSettings.TERMINAL_ENV.kind()).isInstanceOf(SettingKind.StringMap.class);
        assertThat(CoreSettings.SYSTEM_RUN_IN_BACKGROUND.applies()).isEqualTo(Applies.RESTART);
        assertThat(CoreSettings.WINDOW_COLUMNS.applies()).isEqualTo(Applies.NEW_WINDOWS);
        assertThat(CoreSettings.TERMINAL_SHELL_PROGRAM.applies()).isEqualTo(Applies.NEW_PANES);
    }

    @Test void theSnapshotReadsEveryField() {
        var values = SettingValues.builder(CoreSettings.SCHEMA)
            .put(CoreSettings.TERMINAL_FONT_SIZE, 20f, CoreSettings.TERMINAL_FONT_SIZE.key())
            .put(CoreSettings.SYSTEM_RUN_IN_BACKGROUND, true, CoreSettings.SYSTEM_RUN_IN_BACKGROUND.key())
            .put(CoreSettings.TERMINAL_COLORS, TerminalColors.DARK, CoreSettings.TERMINAL_COLORS.key())
            .put(CoreSettings.TERMINAL_ENV, Map.of("A", "1"), CoreSettings.TERMINAL_ENV.key())
            .put(CoreSettings.WINDOW_STATUS_BAR_HEIGHT, 30, CoreSettings.WINDOW_STATUS_BAR_HEIGHT.key()).build();
        var snapshot = ConfigSnapshot.from(values, Map.of("copy", "none"), Map.of());
        assertThat(snapshot.fontSize()).isEqualTo(20f);
        assertThat(snapshot.backgroundEnabled()).isTrue();
        assertThat(snapshot.terminalColors()).isEqualTo(TerminalColors.DARK);
        assertThat(snapshot.terminal().env()).containsExactly(Map.entry("A", "1"));
        assertThat(snapshot.statusBarHeight()).isEqualTo(30);
        assertThat(snapshot.keybindings()).containsExactly(Map.entry("copy", "none"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.config.CoreSettingsTest"`
Expected: FAIL: compilation errors (`CoreSettings` and `ConfigSnapshot.from` do not exist).

- [ ] **Step 3: Write the implementation**

`CoreSettings.java`:

```java
package dev.jasper.app.config;

import static dev.jasper.app.settings.SettingKind.OutOfRange.CLAMP;
import static dev.jasper.app.settings.SettingKind.OutOfRange.DEFAULT;

import dev.jasper.app.settings.Applies;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import dev.jasper.app.settings.SettingsSchema;
import dev.jasper.terminal.config.BellMode;
import dev.jasper.terminal.config.CursorStyle;
import dev.jasper.terminal.config.OptionAsMeta;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every setting config.toml holds, in the order the generated template lists them. The loader reads
 * through this schema; the template, the example file's test and the documentation test check against it.
 */
public final class CoreSettings {
    private CoreSettings() { }

    private static final String FONT_NAME = "Use a nonblank font name without NUL; using the default.";

    static boolean noNul(String text) { return text.indexOf('\0') < 0; }

    static boolean fontName(String text) { return !text.isBlank() && noNul(text); }

    public static final Setting<Integer> WINDOW_TAB_HEIGHT = Setting.of("window.tab_height", SettingKind.integer(20, 72, CLAMP), 30)
        .label("Tab height").keywords("tabs", "height", "size")
        .description("Tab height in logical pixels, 20-72; out-of-range values use the nearest limit. Updates live.").build();
    public static final Setting<Integer> WINDOW_RAIL_WIDTH = Setting.of("window.rail_width",
            SettingKind.integer(ConfigSnapshot.MIN_RAIL_WIDTH, ConfigSnapshot.MAX_RAIL_WIDTH, CLAMP), ConfigSnapshot.DEFAULT_RAIL_WIDTH)
        .label("Panel rail width").keywords("rail", "panels", "sidebar", "width")
        .description("Panel rail width in logical pixels, 28-64; out-of-range values use the nearest limit. Updates live.").build();
    public static final Setting<ToolbarMode> WINDOW_TOOLBAR = Setting.of("window.toolbar", SettingKind.choice(List.of(
            Map.entry("icons_and_labels", ToolbarMode.ICONS_AND_LABELS), Map.entry("icons", ToolbarMode.ICONS),
            Map.entry("hidden", ToolbarMode.HIDDEN))), ToolbarMode.ICONS_AND_LABELS)
        .label("Toolbar").keywords("toolbar", "buttons", "labels", "icons")
        .description("Toolbar: \"icons_and_labels\", \"icons\", or \"hidden\"; updates live.").build();
    public static final Setting<Integer> WINDOW_TOOLBAR_HEIGHT = Setting.of("window.toolbar_height",
            SettingKind.integer(ConfigSnapshot.MIN_TOOLBAR_HEIGHT, ConfigSnapshot.MAX_TOOLBAR_HEIGHT, CLAMP), ConfigSnapshot.DEFAULT_TOOLBAR_HEIGHT)
        .label("Toolbar height").keywords("toolbar", "title bar", "height")
        .description("Toolbar/title-bar height in logical pixels, 28-64; out-of-range values use the nearest limit. Updates live.").build();
    public static final Setting<Boolean> WINDOW_STATUS_BAR = Setting.of("window.status_bar", SettingKind.bool(), true)
        .label("Show status bar").keywords("status bar", "footer")
        .description("Show the status bar; updates live.").build();
    public static final Setting<Integer> WINDOW_STATUS_BAR_HEIGHT = Setting.of("window.status_bar_height",
            SettingKind.integer(ConfigSnapshot.MIN_STATUS_BAR_HEIGHT, ConfigSnapshot.MAX_STATUS_BAR_HEIGHT, CLAMP), ConfigSnapshot.DEFAULT_STATUS_BAR_HEIGHT)
        .label("Status bar height").keywords("status bar", "footer", "height")
        .description("Status bar height in logical pixels, 20-48; never shorter than its text. Updates live.").build();
    public static final Setting<Integer> WINDOW_COLUMNS = Setting.of("window.columns", SettingKind.integer(5, 500, DEFAULT), 100)
        .label("Initial columns").keywords("grid", "width", "size", "columns").applies(Applies.NEW_WINDOWS)
        .description("Initial terminal grid for new windows only; existing windows keep their size.\n"
            + "Columns: 5-500. The window is clamped to the usable screen.").build();
    public static final Setting<Integer> WINDOW_LINES = Setting.of("window.lines", SettingKind.integer(2, 200, DEFAULT), 35)
        .label("Initial lines").keywords("grid", "height", "size", "rows").applies(Applies.NEW_WINDOWS)
        .description("Initial rows for new windows only, 2-200.").build();
    public static final Setting<Boolean> BUDDY_ENABLED = Setting.of("buddy.enabled", SettingKind.bool(), false)
        .label("Show Jasper").keywords("buddy", "desk buddy", "mascot")
        .description("Show the pixel-art Jasper floating above your windows while a terminal is open; off by default,\n"
            + "updates live. View > Show Jasper and a right-click on Jasper toggle him for this session only.").build();
    public static final Setting<Integer> NOTIFICATIONS_LONG_COMMAND_SECONDS = Setting.of("notifications.long_command_seconds",
            SettingKind.integer(0, 3600, DEFAULT), 10)
        .label("Notify after (seconds)").keywords("notification", "long command", "finished", "alert")
        .description("Live. Notify when a command that ran at least this long finishes in a pane you are not\n"
            + "looking at. Needs shell integration: the duration comes from its command marks.\n"
            + "Zero disables notifications. Range 0-3600.").build();
    public static final Setting<Boolean> SYSTEM_RUN_IN_BACKGROUND = Setting.of("system.run_in_background", SettingKind.bool(), false)
        .alias("background.enabled").label("Run in background").keywords("resident", "login", "autostart", "background")
        .applies(Applies.RESTART)
        .description("Keep Jasper running with no windows after the last one closes to reduce next-launch startup\n"
            + "work, and start it in the background at login. Quit still exits completely.\n"
            + "Residency applies from the next start; the login item updates as soon as you save.\n"
            + "Autostart needs an installed Jasper, not a development run.").build();
    public static final Setting<Integer> PALETTE_MAX_RESULTS = Setting.of("palette.max_results",
            SettingKind.integer(PaletteSettings.MIN_MAX_RESULTS, PaletteSettings.MAX_MAX_RESULTS, DEFAULT), PaletteSettings.DEFAULT_MAX_RESULTS)
        .label("Results per scope").keywords("palette", "search", "results")
        .description("Rows each palette scope shows, and the All tab shows per scope, 1-20; updates live.").build();
    public static final Setting<Integer> TERMINAL_SCROLLBACK = Setting.of("terminal.scrollback", SettingKind.integer(0, 1_000_000, DEFAULT), 10_000)
        .label("Scrollback lines").keywords("history", "buffer", "scrollback").applies(Applies.NEW_PANES)
        .description("Scrollback lines, 0-1000000; new panes only. Existing sessions keep their capacity.").build();
    public static final Setting<OptionAsMeta> TERMINAL_OPTION_AS_META = Setting.of("terminal.option_as_meta", SettingKind.choice(List.of(
            Map.entry("left", OptionAsMeta.LEFT), Map.entry("right", OptionAsMeta.RIGHT),
            Map.entry("both", OptionAsMeta.BOTH), Map.entry("none", OptionAsMeta.NONE))), OptionAsMeta.LEFT)
        .label("Option key as Meta").keywords("option", "alt", "meta", "keyboard")
        .description("Option key sending Meta: \"left\", \"right\", \"both\", or \"none\"; updates live.").build();
    public static final Setting<Float> TERMINAL_DIM_INACTIVE_PANES = Setting.of("terminal.dim_inactive_panes", SettingKind.number(0, 1), .3f)
        .label("Dim inactive panes").keywords("dim", "split", "inactive", "focus")
        .description("Inactive pane dimming, 0.0-1.0; updates live.").build();
    public static final Setting<Boolean> TERMINAL_COPY_ON_SELECT = Setting.of("terminal.copy_on_select", SettingKind.bool(), false)
        .label("Copy on select").keywords("clipboard", "selection", "copy")
        .description("Copy a completed selection to the clipboard; updates live.").build();
    public static final Setting<BellMode> TERMINAL_BELL = Setting.of("terminal.bell", SettingKind.choice(List.of(
            Map.entry("visual", BellMode.VISUAL), Map.entry("sound", BellMode.SOUND), Map.entry("none", BellMode.NONE))), BellMode.VISUAL)
        .label("Bell").keywords("bell", "beep", "sound", "flash")
        .description("Bell: \"visual\", \"sound\", or \"none\"; updates live.").build();
    public static final Setting<ShellExitBehavior> TERMINAL_ON_EXIT = Setting.of("terminal.on_exit", SettingKind.choice(List.of(
            Map.entry("keep_open", ShellExitBehavior.KEEP_OPEN), Map.entry("close_on_success", ShellExitBehavior.CLOSE_ON_SUCCESS),
            Map.entry("close", ShellExitBehavior.CLOSE))), ShellExitBehavior.KEEP_OPEN)
        .label("When the shell exits").keywords("exit", "close", "keep open")
        .description("Shell process exit: \"keep_open\", \"close_on_success\" (code 0), or \"close\" (any exit).\n"
            + "Only the exited pane closes; its tab/window closes when empty. Live for future exits.\n"
            + "\"keep_open\" retains output from the stopped shell; reload never closes retained output.").build();
    public static final Setting<ShellIntegrationMode> TERMINAL_SHELL_INTEGRATION = Setting.of("terminal.shell_integration", SettingKind.choice(List.of(
            Map.entry("auto", ShellIntegrationMode.AUTO), Map.entry("manual", ShellIntegrationMode.MANUAL),
            Map.entry("off", ShellIntegrationMode.OFF))), ShellIntegrationMode.AUTO)
        .label("Shell integration").keywords("integration", "prompt marks", "osc 133", "zsh", "bash", "fish").applies(Applies.NEW_PANES)
        .description("Shell integration: \"auto\" loads Jasper's zsh/bash/fish script after your own startup files (new panes only),\n"
            + "\"manual\" only exports JASPER_SHELL_INTEGRATION so you can source it yourself, \"off\" does neither.").build();
    public static final Setting<TerminalColors> TERMINAL_COLORS = Setting.of("terminal.colors", SettingKind.choice(List.of(
            Map.entry("match", TerminalColors.MATCH), Map.entry("light", TerminalColors.LIGHT), Map.entry("dark", TerminalColors.DARK))),
            TerminalColors.MATCH)
        .alias("ui.theme.terminal").label("Terminal colors").keywords("palette", "colours", "color scheme", "theme")
        .description("Terminal colors: \"match\" uses the theme's console colours (Jasper's palette when it has\n"
            + "none); \"light\" or \"dark\" keeps Jasper's palette. Switches live.").build();
    public static final Setting<String> TERMINAL_FONT_FAMILY = Setting.of("terminal.font.family",
            SettingKind.string(CoreSettings::fontName, FONT_NAME), FontConfig.defaults().family())
        .alias("font.family").label("Font").keywords("font", "typeface", "family")
        .description("Font family and ordered fallbacks update live. Missing fonts use JBR/system fallback.").build();
    public static final Setting<Float> TERMINAL_FONT_SIZE = Setting.of("terminal.font.size", SettingKind.number(6, 72), FontConfig.defaults().size())
        .alias("font.size").label("Font size").keywords("font", "size", "points", "zoom")
        .description("Font size in points, 6-72; changes update all panes live.\n"
            + "Reset Font Size restores this saved default.\n"
            + "Temporary size/appearance choices survive reloads until their respective saved value changes.").build();
    public static final Setting<List<String>> TERMINAL_FONT_FALLBACK = Setting.of("terminal.font.fallback",
            SettingKind.stringList(CoreSettings::fontName), FontConfig.defaults().fallback())
        .alias("font.fallback").label("Fallback fonts").keywords("font", "fallback", "emoji", "nerd font")
        .description("Fallback names must be nonblank; an empty fallback list is allowed.").build();
    public static final Setting<Boolean> TERMINAL_FONT_LIGATURES = Setting.of("terminal.font.ligatures", SettingKind.bool(), true)
        .alias("font.ligatures").label("Ligatures").keywords("font", "ligatures")
        .description("Render programming ligatures; updates live.").build();
    public static final Setting<Float> TERMINAL_FONT_LINE_HEIGHT = Setting.of("terminal.font.line_height", SettingKind.number(.5, 3), 1f)
        .alias("font.line_height").label("Line height").keywords("font", "line height", "spacing", "rows")
        .description("Line height multiplier, 0.5-3.0; updates live. Below 1.0 rows are tighter and glyphs may overlap.").build();
    public static final Setting<String> TERMINAL_SHELL_PROGRAM = Setting.of("terminal.shell.program",
            SettingKind.string(text -> (text.isEmpty() || !text.isBlank()) && noNul(text),
                "Use an empty or nonblank executable name without NUL; using the default."), "")
        .label("Shell program").keywords("shell", "zsh", "bash", "fish", "command").applies(Applies.NEW_PANES)
        .description("New panes only. Empty program resolves the default shell and appends configured args.\n"
            + "An explicit program gets only configured args, with no implicit login flag.").build();
    public static final Setting<List<String>> TERMINAL_SHELL_ARGS = Setting.of("terminal.shell.args",
            SettingKind.stringList(CoreSettings::noNul), List.<String>of())
        .label("Shell arguments").keywords("shell", "arguments", "flags").applies(Applies.NEW_PANES)
        .description("Arguments are exact strings, without shell parsing; preserve each argument separately.\n"
            + "Program and arguments must not contain NUL; a nonempty program cannot be blank.").build();
    public static final Setting<CursorStyle> TERMINAL_CURSOR_SHAPE = Setting.of("terminal.cursor.shape", SettingKind.choice(List.of(
            Map.entry("block", CursorStyle.BLOCK), Map.entry("beam", CursorStyle.BEAM), Map.entry("underline", CursorStyle.UNDERLINE))),
            CursorStyle.BLOCK)
        .label("Cursor shape").keywords("cursor", "caret", "block", "beam")
        .description("Live fallback when the running program has not set its own cursor style.\n"
            + "Shape: \"block\", \"beam\", or \"underline\".").build();
    public static final Setting<Boolean> TERMINAL_CURSOR_BLINK = Setting.of("terminal.cursor.blink", SettingKind.bool(), true)
        .label("Blinking cursor").keywords("cursor", "blink", "caret")
        .description("Blink the fallback cursor; updates live.").build();
    public static final Setting<Map<String, String>> TERMINAL_ENV = Setting.of("terminal.env", SettingKind.stringMap(new SettingKind.MapRules(
            TerminalConfig::validEnvName, "Use a portable environment name; entry omitted.",
            Set.of("TERM", "COLORTERM"), "Jasper sets this environment variable; entry omitted.",
            CoreSettings::noNul, "Environment values must contain no NUL; entry omitted.", "a string environment value")), Map.<String, String>of())
        .label("Environment variables").keywords("environment", "env", "variables", "PATH").applies(Applies.NEW_PANES)
        .description("New panes only: add string values to the inherited environment.\n"
            + "Names use letters, digits and underscore, starting with a letter or underscore.\n"
            + "Values must not contain NUL. Desktop launches remove inherited TERM_PROGRAM,\n"
            + "TERM_PROGRAM_VERSION, TERM_SESSION_ID, TMUX, TMUX_PANE, and ITERM_* variables,\n"
            + "then apply this overlay.\n"
            + "Explicit configured values are intentional overrides. On macOS, LANG defaults to\n"
            + "en_US.UTF-8 only when absent or blank; LC_* values are preserved. TERM and COLORTERM\n"
            + "remain reserved and are always forced to xterm-256color and truecolor.\n"
            + "This table does not change which default login shell is selected.").build();
    public static final Setting<String> UI_FONT_FAMILY = Setting.of("ui.font.family",
            SettingKind.string(CoreSettings::fontName, FONT_NAME), UiFontConfig.defaults().family())
        .label("UI font").keywords("font", "interface", "chrome", "typeface")
        .description("UI font for app chrome and plugin controls; updates live, independently of the terminal font.\n"
            + "\"system\" uses the platform font on macOS, Linux and Windows.\n"
            + "A missing named font falls back to that platform font.").build();
    public static final Setting<Float> UI_FONT_SIZE = Setting.of("ui.font.size", SettingKind.number(8, 32), UiFontConfig.defaults().size())
        .example(14f).label("UI font size").keywords("font", "interface", "size", "points")
        .description("Omit size to retain platform sizing. Use 8-32 points, including fractions, when set.").build();
    public static final Setting<String> UI_THEME_NAME = Setting.of("ui.theme.name",
            SettingKind.string(text -> true, "", String::strip), ConfigSnapshot.DEFAULT_THEME)
        .label("Theme").keywords("theme", "dark", "light", "appearance", "look")
        .description("The UI theme by name: \"Jasper Dark\" (the default), \"IntelliJ Light\", or a theme plugin\n"
            + "installed in the themes folder beside this file. Switches live across windows.").build();

    public static final SettingsSchema SCHEMA = SettingsSchema.of(List.<Setting<?>>of(
            WINDOW_TAB_HEIGHT, WINDOW_RAIL_WIDTH, WINDOW_TOOLBAR, WINDOW_TOOLBAR_HEIGHT, WINDOW_STATUS_BAR, WINDOW_STATUS_BAR_HEIGHT,
            WINDOW_COLUMNS, WINDOW_LINES, BUDDY_ENABLED, NOTIFICATIONS_LONG_COMMAND_SECONDS, SYSTEM_RUN_IN_BACKGROUND,
            PALETTE_MAX_RESULTS, TERMINAL_SCROLLBACK, TERMINAL_OPTION_AS_META, TERMINAL_DIM_INACTIVE_PANES, TERMINAL_COPY_ON_SELECT,
            TERMINAL_BELL, TERMINAL_ON_EXIT, TERMINAL_SHELL_INTEGRATION, TERMINAL_COLORS, TERMINAL_FONT_FAMILY, TERMINAL_FONT_SIZE,
            TERMINAL_FONT_FALLBACK, TERMINAL_FONT_LIGATURES, TERMINAL_FONT_LINE_HEIGHT, TERMINAL_SHELL_PROGRAM, TERMINAL_SHELL_ARGS,
            TERMINAL_CURSOR_SHAPE, TERMINAL_CURSOR_BLINK, TERMINAL_ENV, UI_FONT_FAMILY, UI_FONT_SIZE, UI_THEME_NAME),
        Set.of(List.of("keybindings"), List.of("plugins"), List.of("palette", "scopes")),
        Map.ofEntries(
            Map.entry(List.of("window"), "Workspace chrome. Heights and widths are logical pixels."),
            Map.entry(List.of("buddy"), "The desk buddy."),
            Map.entry(List.of("notifications"), "Desktop notifications for finished commands."),
            Map.entry(List.of("system"), "How Jasper runs when no window is open."),
            Map.entry(List.of("palette"), "The command palette."),
            Map.entry(List.of("terminal"), "Terminal behaviour shared by every pane."),
            Map.entry(List.of("terminal", "font"), "The terminal font; the interface font is [ui.font]."),
            Map.entry(List.of("terminal", "shell"), "The shell new panes start."),
            Map.entry(List.of("terminal", "cursor"), "The cursor a program sees until it chooses its own."),
            Map.entry(List.of("ui", "font"), "Interface typography, independent of the terminal font."),
            Map.entry(List.of("ui", "theme"), "The interface theme; terminal colours are terminal.colors.")));
}
```

In `ConfigSnapshot.java`, add the import `import dev.jasper.app.settings.SettingValues;` and this method after `defaults()`:

```java
    /** The snapshot for loaded settings plus the keybindings and legacy plugin tables their own readers produced. */
    public static ConfigSnapshot from(SettingValues values, Map<String, String> keybindings, Map<String, Map<String, Object>> plugins) {
        return new ConfigSnapshot(values.get(CoreSettings.WINDOW_TAB_HEIGHT), values.get(CoreSettings.WINDOW_TOOLBAR),
            values.get(CoreSettings.WINDOW_STATUS_BAR),
            new FontConfig(values.get(CoreSettings.TERMINAL_FONT_FAMILY), values.get(CoreSettings.TERMINAL_FONT_SIZE),
                values.get(CoreSettings.TERMINAL_FONT_FALLBACK), values.get(CoreSettings.TERMINAL_FONT_LIGATURES),
                values.get(CoreSettings.TERMINAL_FONT_LINE_HEIGHT)),
            values.get(CoreSettings.UI_THEME_NAME), keybindings, values.get(CoreSettings.WINDOW_COLUMNS), values.get(CoreSettings.WINDOW_LINES),
            new TerminalConfig(new TerminalConfig.Shell(values.get(CoreSettings.TERMINAL_SHELL_PROGRAM), values.get(CoreSettings.TERMINAL_SHELL_ARGS)),
                values.get(CoreSettings.TERMINAL_ENV), values.get(CoreSettings.TERMINAL_SCROLLBACK), values.get(CoreSettings.TERMINAL_OPTION_AS_META),
                values.get(CoreSettings.TERMINAL_CURSOR_SHAPE), values.get(CoreSettings.TERMINAL_CURSOR_BLINK),
                values.get(CoreSettings.TERMINAL_DIM_INACTIVE_PANES), values.get(CoreSettings.TERMINAL_COPY_ON_SELECT),
                values.get(CoreSettings.TERMINAL_BELL), values.get(CoreSettings.TERMINAL_ON_EXIT), values.get(CoreSettings.TERMINAL_SHELL_INTEGRATION)),
            values.get(CoreSettings.BUDDY_ENABLED), values.get(CoreSettings.PALETTE_MAX_RESULTS),
            values.get(CoreSettings.NOTIFICATIONS_LONG_COMMAND_SECONDS), values.get(CoreSettings.SYSTEM_RUN_IN_BACKGROUND), plugins,
            new UiFontConfig(values.get(CoreSettings.UI_FONT_FAMILY), values.get(CoreSettings.UI_FONT_SIZE)),
            values.get(CoreSettings.TERMINAL_COLORS), values.get(CoreSettings.WINDOW_RAIL_WIDTH),
            values.get(CoreSettings.WINDOW_TOOLBAR_HEIGHT), values.get(CoreSettings.WINDOW_STATUS_BAR_HEIGHT));
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.config.CoreSettingsTest" --tests "dev.jasper.app.config.ConfigSnapshotBuilderTest"`
Expected: PASS. The count assertion is 33 settings (8 window, 1 buddy, 1 notifications, 1 system, 1 palette, 8 terminal, 5 terminal.font, 2 terminal.shell, 2 terminal.cursor, 1 terminal.env, 2 ui.font, 1 ui.theme).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java
git commit -m "Declare every core setting in one schema

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Schema-driven loader, aliases and version

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigLoader.java` (replace the whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigTemplate.java` (three text blocks)
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java:75,288` (log/Javadoc key names)
- Modify: `config.example.toml`
- Modify tests: `config/ConfigLoaderTest.java`, `config/ConfigTemplateTest.java`, `config/ConfigServiceTest.java`, `config/KeybindingSaveTest.java`, `workspace/ExpandedConfigTest.java`, `workspace/ConfigurationControllerTest.java`, `workspace/InstalledThemesTest.java`, `application/JasperApplicationResidencyTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/config/ConfigAliasTest.java`

**Interfaces:**
- Consumes: `CoreSettings.SCHEMA`, `ConfigSnapshot.from`, `Reading`, `SettingValues`, `LegacyKey`.
- Produces: `ConfigLoader.VERSION = 2`; `ConfigLoader.Result(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, boolean rejected, SettingValues values, List<LegacyKey> legacyKeys, List<List<String>> deadTables)`; `ConfigLoader.parse` and `freeze` keep their signatures.

- [ ] **Step 1: Write the failing test**

`ConfigAliasTest.java`:

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.settings.LegacyKey;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigAliasTest {
    private static final Path FILE = Path.of("/fixture/config.toml");
    private static ConfigLoader.Result parse(String text) { return ConfigLoader.parse(FILE, text, true); }

    @Test void versionOneKeysStillLoadWithMovedWarnings() {
        var result = parse("[font]\nsize = 20\nfamily = 'Iosevka'\n[background]\nenabled = true\n[ui.theme]\nterminal = 'dark'\n");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().fontSize()).isEqualTo(20f);
        assertThat(result.snapshot().font().family()).isEqualTo("Iosevka");
        assertThat(result.snapshot().backgroundEnabled()).isTrue();
        assertThat(result.snapshot().terminalColors()).isEqualTo(TerminalColors.DARK);
        assertThat(result.diagnostics()).allSatisfy(d -> assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING))
            .extracting(ConfigDiagnostic::key, ConfigDiagnostic::line, ConfigDiagnostic::message)
            .containsExactly(
                tuple("font.size", 2, "Moved to terminal.font.size. Use Migrate Config to update the file."),
                tuple("font.family", 3, "Moved to terminal.font.family. Use Migrate Config to update the file."),
                tuple("background.enabled", 5, "Moved to system.run_in_background. Use Migrate Config to update the file."),
                tuple("ui.theme.terminal", 7, "Moved to terminal.colors. Use Migrate Config to update the file."));
        assertThat(result.legacyKeys()).containsExactly(
            new LegacyKey(List.of("font", "size"), List.of("terminal", "font", "size")),
            new LegacyKey(List.of("font", "family"), List.of("terminal", "font", "family")),
            new LegacyKey(List.of("background", "enabled"), List.of("system", "run_in_background")),
            new LegacyKey(List.of("ui", "theme", "terminal"), List.of("terminal", "colors")));
        assertThat(result.values().source(CoreSettings.TERMINAL_FONT_SIZE)).hasValue(List.of("font", "size"));
    }

    @Test void theCurrentKeyWinsWhereverItAppears() {
        for (String text : List.of("font.size = 12\n[terminal.font]\nsize = 18\n", "[terminal.font]\nsize = 18\n[font]\nsize = 12\n")) {
            var result = parse(text);
            assertThat(result.snapshot().fontSize()).as(text).isEqualTo(18f);
            assertThat(result.diagnostics()).as(text).singleElement().satisfies(d -> {
                assertThat(d.key()).isEqualTo("font.size");
                assertThat(d.message()).isEqualTo("Superseded by terminal.font.size; ignored.");
            });
            assertThat(result.legacyKeys()).as(text).containsExactly(new LegacyKey(List.of("font", "size"), List.of("terminal", "font", "size")));
            assertThat(result.values().source(CoreSettings.TERMINAL_FONT_SIZE)).hasValue(List.of("terminal", "font", "size"));
        }
    }

    @Test void anOldKeyWithABadValueReportsBoth() {
        var result = parse("[font]\nsize = 100\n");
        assertThat(result.snapshot().fontSize()).isEqualTo(16f);
        assertThat(result.diagnostics()).extracting(ConfigDiagnostic::message).containsExactlyInAnyOrder(
            "Use a finite number from 6–72; using the default.",
            "Moved to terminal.font.size. Use Migrate Config to update the file.");
        assertThat(parse("[font]\nsize = 'big'\n").rejected()).isTrue();
    }

    @Test void theVersionKeyIsCheckedButNeverRequired() {
        assertThat(parse("version = 2\n").diagnostics()).isEmpty();
        assertThat(parse("version = 1\n").diagnostics()).isEmpty();
        assertThat(parse("version = 3\n").diagnostics()).singleElement().satisfies(d -> {
            assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
            assertThat(d.message()).isEqualTo("Written by a newer Jasper; unknown settings are ignored.");
        });
        assertThat(parse("version = 0\n").diagnostics()).singleElement()
            .satisfies(d -> assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.ERROR));
        assertThat(parse("version = 'two'\n").rejected()).isTrue();
    }

    @Test void deadTablesAndPresenceAreReported() {
        var dead = parse("[palette.scopes]\n[plugins.'dev.x']\na = 1\n");
        assertThat(dead.deadTables()).containsExactly(List.of("palette", "scopes"), List.of("plugins", "dev.x"));
        var present = parse("[window]\ntab_height = 44\n");
        assertThat(present.values().present(CoreSettings.WINDOW_TAB_HEIGHT)).isTrue();
        assertThat(present.values().present(CoreSettings.WINDOW_LINES)).isFalse();
        assertThat(present.deadTables()).isEmpty();
        assertThat(parse("[palette.scopes.history]\nenabled = false\n").diagnostics().getFirst().message())
            .contains("plugins/dev.jasper.history/dev.jasper.history.toml");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.config.ConfigAliasTest"`
Expected: FAIL: compilation errors (`Result.legacyKeys()`, `values()`, `deadTables()` do not exist).

- [ ] **Step 3: Replace `ConfigLoader.java`**

```java
package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.settings.LegacyKey;
import dev.jasper.app.settings.Reading;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingValues;
import dev.jasper.app.settings.SettingsSchema;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure TOML parsing and validation, with no file or Swing operations; the schema is {@link CoreSettings#SCHEMA}. */
public final class ConfigLoader {
    /**
     * A parsed file.
     *
     * @param snapshot the settings to use (meaningless when {@code rejected})
     * @param diagnostics problems, by position
     * @param rejected whether a syntax or type error means the file must not be applied
     * @param values each setting's loaded value and where it came from
     * @param legacyKeys old keys found, in file order, with the keys they moved to
     * @param deadTables old {@code palette.scopes} and {@code plugins."<id>"} tables Migrate Config may remove
     */
    public record Result(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, boolean rejected,
                         SettingValues values, List<LegacyKey> legacyKeys, List<List<String>> deadTables) {
        public Result {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(values, "values");
            diagnostics = List.copyOf(diagnostics);
            legacyKeys = List.copyOf(legacyKeys);
            deadTables = deadTables.stream().map(List::copyOf).toList();
        }
    }

    /** The layout this build writes; a file without {@code version} is version 1. */
    public static final int VERSION = 2;

    private static final List<String> KEYBINDINGS = List.of("keybindings");
    private static final List<String> PLUGINS = List.of("plugins");
    private static final List<String> PALETTE_SCOPES = List.of("palette", "scopes");
    private static final List<String> VERSION_KEY = List.of("version");
    private static final java.util.regex.Pattern PLUGIN_ID = java.util.regex.Pattern.compile("[a-z][a-z0-9_.-]{0,127}");

    private record AliasHit(Setting<?> setting, List<String> path, Object value) { }

    private final SettingsSchema schema = CoreSettings.SCHEMA;
    private final Path file;
    private final TomlParseResult toml;
    private final boolean macOs;
    private final List<ConfigDiagnostic> diagnostics = new ArrayList<>();
    private final SettingValues.Builder values = SettingValues.builder(CoreSettings.SCHEMA);
    private final List<AliasHit> aliasHits = new ArrayList<>();
    private final List<List<String>> deadTables = new ArrayList<>();
    private boolean rejected;
    private Map<String, String> keybindings = Map.of();
    private Map<String, Map<String, Object>> plugins = Map.of();

    private ConfigLoader(Path file, String text, boolean macOs) {
        this.file = Objects.requireNonNull(file, "file");
        this.toml = Toml.parse(text);
        this.macOs = macOs;
    }

    public static Result parse(Path file, String text, boolean macOs) {
        return new ConfigLoader(file, text, macOs).parse();
    }

    private Result parse() {
        if (toml.hasErrors()) {
            toml.errors().forEach(error -> diagnostics.add(new ConfigDiagnostic(
                ConfigDiagnostic.Severity.ERROR, file, error.position().line(), error.position().column(), "",
                "Invalid TOML syntax or duplicate definition; check this location.")));
            return new Result(ConfigSnapshot.defaults(), diagnostics, true, SettingValues.defaults(schema), List.of(), List.of());
        }
        readTable(List.of(), toml);
        List<LegacyKey> legacyKeys = resolveAliases();
        diagnostics.sort(Comparator.comparingInt(ConfigDiagnostic::line).thenComparingInt(ConfigDiagnostic::column));
        SettingValues loaded = values.build();
        return new Result(ConfigSnapshot.from(loaded, keybindings, plugins), diagnostics, rejected, loaded, legacyKeys, deadTables);
    }

    private void readTable(List<String> parent, TomlTable table) {
        for (String key : table.keySet()) {
            List<String> path = append(parent, key);
            Object value = table.get(List.of(key));
            if (path.equals(VERSION_KEY)) { readVersion(path, value); continue; }
            var current = schema.find(path);
            if (current.isPresent()) { read(current.get(), path, value); continue; }
            var aliased = schema.aliasOf(path);
            if (aliased.isPresent()) { aliasHits.add(new AliasHit(aliased.get(), path, value)); continue; }
            if (!schema.reserved(path) && !schema.knownTable(path)) { warning(path, "Unknown setting or table; ignored."); continue; }
            if (!(value instanceof TomlTable nested)) typeError(path, "a table");
            else if (path.equals(KEYBINDINGS)) readBindings(nested);
            else if (path.equals(PLUGINS)) readPlugins(nested);
            else if (path.equals(PALETTE_SCOPES)) readOldScopes(nested);
            else readTable(path, nested);
        }
    }

    private <T> void read(Setting<T> setting, List<String> path, Object value) {
        Reading<T> reading = setting.kind().read(value, setting.defaultValue());
        for (Reading.Issue issue : reading.issues()) {
            if (issue.rejectsFile()) rejected = true;
            var at = new ArrayList<>(path);
            at.addAll(issue.subPath());
            diagnostic(issue.severity() == Reading.Severity.WARNING ? ConfigDiagnostic.Severity.WARNING : ConfigDiagnostic.Severity.ERROR,
                at, issue.message());
        }
        values.put(setting, reading.value(), path);
    }

    /** Old keys are read after the walk, so a current key wins wherever it appears in the file. */
    private List<LegacyKey> resolveAliases() {
        aliasHits.sort(Comparator.comparingInt((AliasHit hit) -> line(hit.path())).thenComparingInt(hit -> column(hit.path())));
        var legacy = new ArrayList<LegacyKey>();
        for (AliasHit hit : aliasHits) {
            Setting<?> setting = hit.setting();
            legacy.add(new LegacyKey(hit.path(), setting.key()));
            if (values.present(setting)) warning(hit.path(), "Superseded by " + setting.name() + "; ignored.");
            else {
                read(setting, hit.path(), hit.value());
                warning(hit.path(), "Moved to " + setting.name() + ". Use Migrate Config to update the file.");
            }
        }
        return legacy;
    }

    private void readVersion(List<String> path, Object value) {
        if (!(value instanceof Long number)) typeError(path, "an integer");
        else if (number < 1) valueError(path, "Use a version from 1–" + VERSION + "; treating this file as version 1.");
        else if (number > VERSION) warning(path, "Written by a newer Jasper; unknown settings are ignored.");
    }

    /** palette.scopes once held the History scope's settings; it is kept known so the move is explained. */
    private void readOldScopes(TomlTable table) {
        deadTables.add(PALETTE_SCOPES);
        for (String key : table.keySet()) {
            List<String> path = append(PALETTE_SCOPES, key);
            if (key.equals("history"))
                warning(path, "History moved to the Shell History plugin: its settings (trivial_commands, deprioritize_trivial) "
                    + "now live in plugins/dev.jasper.history/dev.jasper.history.toml; disable the plugin in Manage Plugins to turn it off.");
            else warning(path, "Unknown setting or table; ignored.");
        }
    }

    /** Plugin tables are opaque to the application: no schema, so no unknown-key warnings inside them. */
    private void readPlugins(TomlTable table) {
        var staged = new LinkedHashMap<String, Map<String, Object>>();
        for (String id : table.keySet()) {
            List<String> path = List.of("plugins", id);
            Object value = table.get(List.of(id));
            if (!PLUGIN_ID.matcher(id).matches()) { warning(path, "Not a plugin id; ignored."); continue; }
            if (!(value instanceof TomlTable nested)) { typeError(path, "a table"); continue; }
            staged.put(id, freeze(path, nested));
            deadTables.add(path);
            warning(path, "Plugin settings moved to plugins/" + id + "/" + id + ".toml; this table only seeds that file when it is missing.");
        }
        plugins = Map.copyOf(staged);
    }

    private Map<String, Object> freeze(List<String> parent, TomlTable table) { return freeze(parent, table, this::warning); }

    /**
     * The value kinds plugin settings may hold, as an immutable map: strings, integers, floats, booleans, arrays of
     * strings and nested tables; anything else is dropped silently.
     *
     * @param table a parsed TOML table
     * @return the frozen values
     */
    public static Map<String, Object> freeze(TomlTable table) { return freeze(List.of(), table, (path, message) -> { }); }

    private static Map<String, Object> freeze(List<String> parent, TomlTable table, java.util.function.BiConsumer<List<String>, String> problem) {
        var result = new LinkedHashMap<String, Object>();
        for (String key : table.keySet()) {
            var path = new ArrayList<>(parent);
            path.add(key);
            Object value = table.get(List.of(key));
            switch (value) {
                case String text -> result.put(key, text);
                case Long number -> result.put(key, number);
                case Double number -> result.put(key, number);
                case Boolean flag -> result.put(key, flag);
                case TomlTable nested -> result.put(key, freeze(path, nested, problem));
                case TomlArray array -> {
                    List<String> items = new ArrayList<>();
                    for (int i = 0; i < array.size(); i++) if (array.get(i) instanceof String text) items.add(text);
                    if (items.size() == array.size()) result.put(key, List.copyOf(items));
                    else problem.accept(path, "Only arrays of strings are supported in plugin settings; ignored.");
                }
                default -> problem.accept(path, "Unsupported value type in plugin settings; ignored.");
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private void readBindings(TomlTable table) {
        Map<String, String> overrides = new LinkedHashMap<>();
        boolean invalid = false;
        for (String action : table.keySet()) {
            List<String> path = List.of("keybindings", action);
            if (!knownAction(action) && !KeyBindings.extensionId(action)) {
                warning(path, "Unknown action; ignored.");
                continue;
            }
            Object value = table.get(List.of(action));
            if (!(value instanceof String binding)) {
                typeError(path, "a shortcut string");
                continue;
            }
            try {
                KeyBindings.parse(binding, macOs);
                overrides.put(action, binding);
            } catch (IllegalArgumentException ignored) {
                // The action may belong to a plugin that is not even installed: a bad shortcut for it
                // must not cost the user every other binding, so only this entry is ignored.
                if (!knownAction(action)) { warning(path, "Use a valid shortcut or none; ignored."); continue; }
                valueError(path, "Use a valid shortcut or none; using all default keybindings.");
                invalid = true;
            }
        }
        // Remove every overridden default first, so valid swaps do not collide with
        // the old binding. The existing engine alone decides shortcut equivalence.
        Map<String, String> staged = new LinkedHashMap<>();
        overrides.keySet().forEach(action -> staged.put(action, "none"));
        for (var entry : overrides.entrySet()) {
            staged.put(entry.getKey(), entry.getValue());
            try {
                KeyBindings.withOverrides(macOs, staged);
            } catch (IllegalArgumentException ignored) {
                valueError(List.of("keybindings", entry.getKey()),
                    "Shortcut conflicts with another action; use unique shortcuts or none. Using all default keybindings.");
                staged.put(entry.getKey(), "none");
                invalid = true;
            }
        }
        if (!invalid) keybindings = Map.copyOf(overrides);
    }

    private static boolean knownAction(String id) {
        for (ActionId action : ActionId.values()) if (action.id().equals(id)) return true;
        return false;
    }

    private static List<String> append(List<String> parent, String key) {
        var path = new ArrayList<>(parent);
        path.add(key);
        return List.copyOf(path);
    }

    private int line(List<String> path) {
        TomlPosition position = toml.inputPositionOf(path);
        return position == null ? 0 : position.line();
    }

    private int column(List<String> path) {
        TomlPosition position = toml.inputPositionOf(path);
        return position == null ? 0 : position.column();
    }

    private void typeError(List<String> path, String expected) {
        rejected = true;
        valueError(path, "Expected " + expected + "; configuration was not applied.");
    }

    private void warning(List<String> path, String message) {
        diagnostic(ConfigDiagnostic.Severity.WARNING, path, message);
    }

    private void valueError(List<String> path, String message) {
        diagnostic(ConfigDiagnostic.Severity.ERROR, path, message);
    }

    private void diagnostic(ConfigDiagnostic.Severity severity, List<String> path, String message) {
        TomlPosition position = toml.inputPositionOf(path);
        diagnostics.add(new ConfigDiagnostic(severity, file,
            position == null ? 0 : position.line(), position == null ? 0 : position.column(),
            Toml.joinKeyPath(path), message));
    }
}
```

- [ ] **Step 4: Move the template, the example and the fixtures to the version 2 keys**

In `ConfigTemplate.java` make three replacements inside the text block (Task 7 replaces the whole template; this keeps it valid now):

1. Replace
```
            [background]
            # Keep Jasper running with no windows after the last one closes to reduce next-launch startup
            # work, and start it in the background at login. Quit still exits completely.
            # Residency applies from the next start; the login item updates as soon as you save.
            # Autostart needs an installed Jasper, not a development run.
            # enabled = false
```
with
```
            [system]
            # Keep Jasper running with no windows after the last one closes to reduce next-launch startup
            # work, and start it in the background at login. Quit still exits completely.
            # Residency applies from the next start; the login item updates as soon as you save.
            # Autostart needs an installed Jasper, not a development run.
            # run_in_background = false
```
2. Replace the header line `            [font]` with `            [terminal.font]`.
3. Replace
```
            # name = "Jasper Dark"
            # Terminal colors: "match" uses the theme's console colours (Jasper's palette when it has
            # none); "light" or "dark" keeps Jasper's palette. Switches live.
            # terminal = "match"
```
with
```
            # name = "Jasper Dark"
```
and insert, directly after the line `            # shell_integration = "auto"`, these lines:
```
            # Terminal colors: "match" uses the theme's console colours (Jasper's palette when it has
            # none); "light" or "dark" keeps Jasper's palette. Switches live.
            # colors = "match"
```

In `config.example.toml`:
1. Insert after the three opening comment lines (before the blank line that precedes `[window]`):
```toml
version = 2
```
2. Replace the `[background]` table header and its `enabled = false` line with `[system]` and `run_in_background = false` (keep its comment lines).
3. Replace the `[font]` header with `[terminal.font]`. In the `[ui.font]` table's first comment, change `independent of terminal [font].` to `independent of [terminal.font].`
4. Remove from `[ui.theme]` the two lines
```toml
# Terminal colors, live: "match" uses the theme's console colours; "light" or "dark" keeps Jasper's palette.
terminal = "match"
```
and add them to the end of the `[terminal]` table (after `shell_integration = "auto"`), with the key renamed:
```toml
# Terminal colors, live: "match" uses the theme's console colours; "light" or "dark" keeps Jasper's palette.
colors = "match"
```

In `ApplicationBootstrap.java`, change the two occurrences of `background.enabled` (the log message at line 75 and the Javadoc at line 288) to `system.run_in_background`.

Rename old keys in test fixtures with this script (run from the worktree root):

```bash
python3 - <<'PY'
import pathlib, re
root = pathlib.Path("jasper-app/src/test/java/dev/jasper/app")
files = ["config/ConfigLoaderTest.java", "config/ConfigServiceTest.java", "config/KeybindingSaveTest.java",
         "workspace/ExpandedConfigTest.java", "workspace/ConfigurationControllerTest.java"]
names = "family|size|fallback|ligatures|line_height"
for name in files:
    path = root / name
    text = path.read_text(encoding="utf-8")
    text = text.replace("[font]", "[terminal.font]")
    # Only keys inside TOML strings: at a string start or after a "\n" escape, and followed by '=', a quote,
    # a comma or a closing bracket, never by '(' (Java calls such as font.size()).
    text = re.sub(r"(?:(?<=\\n)|(?<![\w.]))font\.(" + names + r")(?=\s*=|[\"',\]])", r"terminal.font.\1", text)
    path.write_text(text, encoding="utf-8")
PY
```

Then make these exact edits:

`config/ConfigLoaderTest.java`, method `backgroundEnabledDefaultsToOffAndRejectsNonBooleans`: replace its body's fixtures and key:
- `parse("[background]\nenabled = true\n")` becomes `parse("[system]\nrun_in_background = true\n")`
- `parse("[background]\nenabled = 1\n")` becomes `parse("[system]\nrun_in_background = 1\n")`
- `assertDiagnostic(bad, "background.enabled", 2, 1, ...)` becomes `assertDiagnostic(bad, "system.run_in_background", 2, 1, ...)`
- `parse("[background]\nstart_at_login = true\n")` becomes `parse("[system]\nstart_at_login = true\n")` and its key `"background.start_at_login"` becomes `"system.start_at_login"`.

`config/ConfigLoaderTest.java`, method `terminalColorsDefaultToMatchAndAcceptEveryChoice`:
- `parse("[ui.theme]\nterminal = '" + ... + "'\n")` becomes `parse("[terminal]\ncolors = '" + ... + "'\n")`
- `parse("ui.theme.terminal = 'dark'\nui.theme.name = 'IntelliJ Light'\n")` becomes `parse("terminal.colors = 'dark'\nui.theme.name = 'IntelliJ Light'\n")`

`config/ConfigLoaderTest.java`, method `invalidTerminalColorsUseTheExistingPerFieldDiagnosticPolicy`:
- `parse("[ui.theme]\nterminal=" + value + "\nname='IntelliJ Light'\n")` becomes `parse("[terminal]\ncolors=" + value + "\n[ui.theme]\nname='IntelliJ Light'\n")`
- `isEqualTo("ui.theme.terminal")` becomes `isEqualTo("terminal.colors")` (the line stays 2).

`workspace/InstalledThemesTest.java:68`: `"[ui.theme]\nname = 'fixture dark'\nterminal = 'light'\n"` becomes `"[ui.theme]\nname = 'fixture dark'\n[terminal]\ncolors = 'light'\n"`.

`workspace/ConfigurationControllerTest.java:629,631`: `"[ui.theme]\nname='IntelliJ Light'\nterminal='dark'\n"` becomes `"[ui.theme]\nname='IntelliJ Light'\n[terminal]\ncolors='dark'\n"`, and the `'match'` line likewise.

`application/JasperApplicationResidencyTest.java:90,106`: `"[background]\nenabled = true\n"` becomes `"[system]\nrun_in_background = true\n"`, and the `false` line likewise.

`config/ConfigTemplateTest.java`, method `repositoryExampleIsCompleteAndParsesAsBuiltInDefaultsOnBothPlatforms`: replace the key-set assertions between `assertThat(toml.errors()).isEmpty();` and `assertThat(toml.getTable("keybindings").keySet()).isEmpty();` with:

```java
        assertThat(toml.getLong("version")).isEqualTo(2L);
        assertThat(toml.getTable("buddy").keySet()).containsExactly("enabled");
        assertThat(toml.getTable("notifications").keySet()).containsExactly("long_command_seconds");
        assertThat(toml.getTable("system").keySet()).containsExactly("run_in_background");
        assertThat(toml.getTable("palette").keySet()).containsExactly("max_results");
        assertThat(toml.getTable("window").keySet())
            .containsExactlyInAnyOrder("tab_height", "rail_width", "toolbar", "toolbar_height", "status_bar",
                "status_bar_height", "columns", "lines");
        assertThat(toml.getTable("terminal.font").keySet())
            .containsExactlyInAnyOrder("family", "size", "fallback", "ligatures", "line_height");
        assertThat(toml.getTable("terminal").keySet())
            .containsExactlyInAnyOrder("scrollback", "option_as_meta", "dim_inactive_panes", "copy_on_select",
                "bell", "on_exit", "shell_integration", "colors", "font", "shell", "cursor", "env");
        assertThat(toml.getTable("terminal.shell").keySet()).containsExactlyInAnyOrder("program", "args");
        assertThat(toml.getTable("terminal.cursor").keySet()).containsExactlyInAnyOrder("shape", "blink");
        assertThat(toml.getTable("terminal.env").keySet()).isEmpty();
        assertThat(toml.getTable("ui.font").keySet()).containsExactly("family");
        assertThat(toml.getTable("ui.theme").keySet()).containsExactly("name");
        assertThat(toml.contains("font")).isFalse();
        assertThat(toml.contains("background")).isFalse();
```

- [ ] **Step 5: Run the configuration tests, then the whole app suite**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.config.*" --tests "dev.jasper.app.workspace.*Config*" --tests "dev.jasper.app.workspace.InstalledThemesTest" --tests "dev.jasper.app.application.*"`
Expected: PASS.

Run: `./gradlew :jasper-app:test`
Expected: PASS. Any remaining failure that is only an extra "Moved to …" warning comes from a fixture still using a version 1 key: convert that fixture to the version 2 key, as above, and list the file in the status banner.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A jasper-app/src config.example.toml
git commit -m "Load config.toml through the schema with version 2 keys and aliases

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Generated template, schema-checked example and documentation table

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigTemplate.java` (`text` generated from the schema)
- Modify: `jasper-app/src/test/java/dev/jasper/app/config/ConfigTemplateTest.java`
- Modify: `jasper-app/build.gradle.kts` (test input for the configuration guide)
- Modify: `docs/configuration.md` (the `## Supported settings` sample and key table)

**Interfaces:**
- Consumes: `CoreSettings.SCHEMA`, `Setting.example()`, `SettingsSchema.tableNote`, `TomlLiterals`, `TomlDocument.leaves`.
- Produces: `ConfigTemplate.text(boolean macOs)` (same signature), beginning with the comment header and `version = 2`.

- [ ] **Step 1: Write the failing tests**

Add to `ConfigTemplateTest.java` (imports: `dev.jasper.app.settings.Setting`, `dev.jasper.app.settings.SettingKind`, `dev.jasper.app.settings.TomlDocument`, `dev.jasper.app.settings.TomlLiterals`, `java.util.List`, `java.util.ArrayList`):

```java
    @Test void theTemplateIsVersionTwoAndListsEverySettingUnderItsTableOnce() {
        for (boolean macOs : new boolean[]{true, false}) {
            String template = ConfigTemplate.text(macOs);
            assertThat(template).contains("\nversion = 2\n").doesNotContain("[font]", "[background]");
            String uncommented = template.lines()
                .map(line -> line.matches("# ([a-z_0-9]+ = .*|\\[.*])") ? line.substring(2) : line)
                .collect(java.util.stream.Collectors.joining("\n"));
            var parsed = ConfigLoader.parse(directory.resolve("config.toml"), uncommented, macOs);
            assertThat(parsed.diagnostics()).isEmpty();
            for (Setting<?> setting : CoreSettings.SCHEMA.settings()) {
                if (setting.kind() instanceof SettingKind.StringMap) continue;
                assertThat(parsed.values().present(setting)).as(setting.name()).isTrue();
                assertThat(parsed.values().get(setting)).as(setting.name()).isEqualTo(templateValue(setting));
            }
            var headers = template.lines().filter(line -> line.startsWith("[")).toList();
            assertThat(headers).doesNotHaveDuplicates();
        }
    }

    private static <T> T templateValue(Setting<T> setting) { return setting.example().orElse(setting.defaultValue()); }

    @Test void theConfigurationGuideTableNamesEveryCurrentKeyAndNoOldOne() throws Exception {
        Path guide = Path.of(System.getProperty("jasper.projectDir")).resolve("docs/configuration.md");
        String text = Files.readString(guide, StandardCharsets.UTF_8);
        for (Setting<?> setting : CoreSettings.SCHEMA.settings()) {
            assertThat(text).as(setting.name()).contains("| `" + setting.name() + "` |");
            for (List<String> alias : setting.aliases()) assertThat(text).doesNotContain("| `" + TomlLiterals.path(alias) + "` |");
        }
    }
```

Replace the key-set assertions of `repositoryExampleIsCompleteAndParsesAsBuiltInDefaultsOnBothPlatforms` (the block Task 6 wrote) with a schema-driven check:

```java
        assertThat(toml.getLong("version")).isEqualTo(2L);
        var expected = new ArrayList<List<String>>();
        expected.add(List.of("version"));
        for (Setting<?> setting : CoreSettings.SCHEMA.settings())
            if (!(setting.kind() instanceof SettingKind.StringMap) && setting != CoreSettings.UI_FONT_SIZE) expected.add(setting.key());
        assertThat(TomlDocument.leaves(text).keySet()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(toml.getTable("terminal.env").keySet()).isEmpty();
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.config.ConfigTemplateTest"`
Expected: FAIL: the template has no `version = 2`, and the guide's table still names `font.size`.

- [ ] **Step 3: Generate the template**

Replace `ConfigTemplate.text` (keep `ensureExists`; remove the now unused `Command` import) with:

```java
    private static final String HEADER = """
        # Jasper settings
        # Uncomment a setting to change its saved default. Live settings reload across windows.
        # View menu choices are temporary overrides until that saved setting changes.
        # Session and grid defaults affect new panes/windows as described below.
        # Reload Config forces a fresh read.
        # Invalid syntax or types keep the last valid settings; diagnostics explain errors.
        version = 2
        """;

    /** The commented defaults for every setting in {@link CoreSettings#SCHEMA}, then the platform's shortcuts. */
    public static String text(boolean macOs) {
        var text = new StringBuilder(HEADER);
        List<String> table = null;
        for (Setting<?> setting : CoreSettings.SCHEMA.settings()) {
            boolean map = setting.kind() instanceof SettingKind.StringMap;
            List<String> next = map ? setting.key() : setting.table();
            if (!next.equals(table)) {
                table = next;
                text.append("\n[").append(TomlLiterals.path(table)).append("]\n");
                CoreSettings.SCHEMA.tableNote(table).ifPresent(note -> comment(text, note));
            }
            comment(text, setting.description());
            if (!map) text.append("# ").append(TomlLiterals.key(setting.leaf())).append(" = ").append(literal(setting)).append('\n');
        }
        text.append("""

            [keybindings]
            # Shortcuts update live. Use "none" to disable an action; shortcuts must be unique.
            # Modifiers: cmd, ctrl, alt, shift. Brace aliases { and } include shift.
            """);
        text.append(macOs
            ? "# cmd is the Command key on macOS.\n"
            : "# cmd means Ctrl+Shift; some defaults add Alt to keep actions distinct.\n# Command Palette uses plain Ctrl+K; Clear Scrollback uses Ctrl+Shift+K.\n");
        for (ActionId action : ActionId.values()) {
            text.append("# ").append(action.label()).append('\n');
            text.append("# ").append(action.id()).append(" = \"")
                .append(KeyBindings.effectiveDefaultBinding(action, macOs)).append("\"\n");
        }
        return text.toString();
    }

    private static void comment(StringBuilder text, String lines) {
        for (String line : lines.split("\n", -1)) text.append("# ").append(line).append('\n');
    }

    private static <T> String literal(Setting<T> setting) {
        return setting.kind().literal(setting.example().orElse(setting.defaultValue()));
    }
```

Add imports: `dev.jasper.app.settings.Setting`, `dev.jasper.app.settings.SettingKind`, `dev.jasper.app.settings.TomlLiterals`, `java.util.List`.

- [ ] **Step 4: Update the guide and the test inputs**

In `docs/configuration.md`, in the `## Supported settings` TOML sample:
- insert `version = 2` and a blank line before `[window]`;
- replace `[background]\nenabled = false` with `[system]\nrun_in_background = false`;
- replace `[font]` with `[terminal.font]` and move that whole table (five keys) to directly after the `[terminal]` table;
- in `[terminal]`, add `colors = "match"` after `shell_integration = "auto"`;
- in `[ui.theme]`, delete the two lines `# "match" (the theme's console colours), "light" or "dark" terminal colors.` and `terminal = "match"`.

In the key table below it, change these rows (keep their other columns):
- `` `background.enabled` `` becomes `` `system.run_in_background` ``;
- `` `font.family` ``, `` `font.size` ``, `` `font.fallback` ``, `` `font.ligatures` ``, `` `font.line_height` `` become `` `terminal.font.family` `` and so on;
- `` `ui.theme.terminal` `` becomes `` `terminal.colors` ``.

In `jasper-app/build.gradle.kts`, inside `tasks.test { ... }`, after the `inputs.file(configExample)` line, add:

```kotlin
    inputs.file(rootProject.layout.projectDirectory.file("docs/configuration.md")).withPathSensitivity(PathSensitivity.RELATIVE)
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.config.*" --tests "dev.jasper.app.documentation.*"`
Expected: PASS (`commentedTemplatesParseCleanly…` and `uncommentedDefaultsAreValid…` still pass: the generated template uses `16`, `1` and `0.3` for numbers, which parse to the same defaults).

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/config/ConfigTemplate.java jasper-app/src/test/java/dev/jasper/app/config/ConfigTemplateTest.java jasper-app/build.gradle.kts docs/configuration.md
git commit -m "Generate the settings template from the schema; check the example and guide against it

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: One save transaction: `ConfigService.save`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settings/SettingWriter.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/config/SettingsEdit.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java` (`transact`, `save`, `saveKeybindings`)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/SettingsSaveTest.java`

**Interfaces:**
- Consumes: `TomlDocument`, `LegacyKey`, `Setting`, `SettingKind.check`, `ConfigLoader.Result.values()/legacyKeys()`.
- Produces: `SettingWriter.write(TomlDocument, Setting<T>, Optional<T>)`; `SettingsEdit(Map<Setting<?>, Optional<?>> changes, Map<Setting<?>, Optional<?>> expected)`; `ConfigService.save(SettingsEdit)` returning `CompletableFuture<State>`; private `ConfigService.transact(String prefix, Function<Source, String> edit)` and `record Source(String text, ConfigLoader.Result parsed)` reused by Task 9.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.settings.Setting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsSaveTest {
    @TempDir Path directory;

    private static SettingsEdit edit(Map<Setting<?>, Optional<?>> changes, Map<Setting<?>, Optional<?>> expected) {
        return new SettingsEdit(changes, expected);
    }

    private ConfigService.State save(ConfigService service, SettingsEdit edit) throws Exception {
        return service.save(edit).get(5, TimeUnit.SECONDS);
    }

    @Test void writesNewAndReplacedValuesKeepingComments() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "# mine\n[window]\ntab_height = 30 # px\n");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(
                Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(44), CoreSettings.TERMINAL_FONT_SIZE, Optional.of(18f)),
                Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(30), CoreSettings.TERMINAL_FONT_SIZE, Optional.empty())));
            assertThat(Files.readString(file)).isEqualTo("# mine\n[window]\ntab_height = 44 # px\n\n[terminal.font]\nsize = 18\n");
            assertThat(state.snapshot().tabHeight()).isEqualTo(44);
            assertThat(state.snapshot().fontSize()).isEqualTo(18f);
        }
    }

    @Test void anEmptyValueRestoresTheDefaultByRemovingTheKey() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[window]\ntab_height = 44\nstatus_bar = false\n");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty()),
                Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(44))));
            assertThat(Files.readString(file)).isEqualTo("[window]\nstatus_bar = false\n");
            assertThat(state.snapshot().tabHeight()).isEqualTo(30);
        }
    }

    @Test void aValueChangedOnDiskIsRefusedAndRepublished() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[window]\ntab_height = 44\n");
        try (var service = new ConfigService(file, true)) {
            assertThatThrownBy(() -> save(service, edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50)),
                    Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(30)))))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("window.tab_height changed on disk. Review the new value and try again.");
            assertThat(Files.readString(file)).isEqualTo("[window]\ntab_height = 44\n");
        }
    }

    @Test void invalidValuesAndRejectedFilesAreRefusedButUntouchedValueErrorsAreNot() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[window]\ncolumns = 1\n");
        try (var service = new ConfigService(file, true)) {
            assertThatThrownBy(() -> save(service, edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(99)),
                    Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty()))))
                .hasCauseInstanceOf(IllegalArgumentException.class).hasRootCauseMessage("window.tab_height: Use an integer from 20–72; using 72.");
            save(service, edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(40)), Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty())));
            assertThat(Files.readString(file)).isEqualTo("[window]\ncolumns = 1\ntab_height = 40\n");
        }
        Files.writeString(file, "[window\n");
        try (var service = new ConfigService(file, true)) {
            assertThatThrownBy(() -> save(service, edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(40)),
                    Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty()))))
                .hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(Files.readString(file)).isEqualTo("[window\n");
        }
    }

    @Test void savingASettingHeldUnderAnOldKeyMovesItToTheCurrentKey() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[font]\nsize = 15 # old\n");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.TERMINAL_FONT_SIZE, Optional.of(18f)),
                Map.of(CoreSettings.TERMINAL_FONT_SIZE, Optional.of(15f))));
            assertThat(Files.readString(file)).isEqualTo("[font]\n# old\n\n[terminal.font]\nsize = 18\n");
            assertThat(state.diagnostics()).isEmpty();
        }
    }

    @Test void aMissingFileStartsFromTheTemplate() throws Exception {
        Path file = directory.resolve("new/config.toml");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(44)), Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty())));
            assertThat(Files.readString(file)).startsWith("# Jasper settings");
            assertThat(state.snapshot().tabHeight()).isEqualTo(44);
            assertThat(state.diagnostics()).isEmpty();
        }
    }

    @Test void environmentTablesAreWrittenEntryByEntry() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[terminal.env]\nA = \"1\"\nB = \"2\"\n");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.TERMINAL_ENV, Optional.of(Map.of("A", "9", "C", "3"))),
                Map.of(CoreSettings.TERMINAL_ENV, Optional.of(Map.of("A", "1", "B", "2")))));
            assertThat(state.snapshot().terminal().env()).containsOnly(Map.entry("A", "9"), Map.entry("C", "3"));
            assertThat(Files.readString(file)).containsOnlyOnce("[terminal.env]");
        }
    }

    @Test void anEditMustNameTheSameSettingsTwice() {
        assertThatIllegalArgumentException().isThrownBy(() -> edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(40)), Map.of()));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.config.SettingsSaveTest"`
Expected: FAIL: compilation errors (`SettingsEdit`, `ConfigService.save` do not exist).

- [ ] **Step 3: Write the implementation**

`SettingWriter.java`:

```java
package dev.jasper.app.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Writes one setting's value, or its removal, into a document. */
public final class SettingWriter {
    private SettingWriter() { }

    /** Writes {@code value} for {@code setting}, or removes it (restoring the default) when {@code value} is empty. */
    public static <T> TomlDocument write(TomlDocument document, Setting<T> setting, Optional<T> value) {
        if (!(setting.kind() instanceof SettingKind.StringMap)) {
            return value.isPresent() ? document.set(setting.key(), setting.kind().literal(value.get())) : document.remove(setting.key());
        }
        TomlDocument next = document;
        for (List<String> key : document.keysUnder(setting.key())) next = next.remove(key);
        @SuppressWarnings("unchecked")
        Map<String, String> entries = (Map<String, String>) value.orElse(null);
        if (entries == null) return next;
        for (var entry : new TreeMap<>(entries).entrySet()) {
            var path = new ArrayList<>(setting.key());
            path.add(entry.getKey());
            next = next.set(List.copyOf(path), TomlLiterals.string(entry.getValue()));
        }
        return next;
    }
}
```

`SettingsEdit.java`:

```java
package dev.jasper.app.config;

import dev.jasper.app.settings.Setting;
import java.util.Map;
import java.util.Optional;

/**
 * Settings to write, and what the caller last saw for each.
 *
 * @param changes each setting's new value, or empty to restore its default by removing its key
 * @param expected for exactly the same settings, the saved value the caller last read (empty when the file did not set it)
 */
public record SettingsEdit(Map<Setting<?>, Optional<?>> changes, Map<Setting<?>, Optional<?>> expected) {
    public SettingsEdit {
        changes = Map.copyOf(changes);
        expected = Map.copyOf(expected);
        if (!changes.keySet().equals(expected.keySet()))
            throw new IllegalArgumentException("Expected values must name exactly the changed settings.");
    }
}
```

In `ConfigService.java` add imports:

```java
import dev.jasper.app.settings.LegacyKey;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingWriter;
import dev.jasper.app.settings.TomlDocument;
import java.io.UncheckedIOException;
import java.util.function.Function;
```

Replace the whole `saveKeybindings` method with these members:

```java
    /** The file's text (the template when it is missing) and how it parsed. */
    private record Source(String text, ConfigLoader.Result parsed) { }

    /** A caller's view of the file is out of date; the state is republished before this reaches the caller. */
    private static final class StaleSetting extends IllegalArgumentException {
        StaleSetting(String message) { super(message); }
    }

    /**
     * Writes settings on the config worker, then publishes them through the normal reload. Refuses a rejected file,
     * a value its setting does not accept, and a setting whose saved value is no longer the one {@code edit} expects.
     */
    public CompletableFuture<State> save(SettingsEdit edit) {
        Objects.requireNonNull(edit, "edit");
        return submit(() -> {
            try {
                return transact(".settings-", source -> {
                    var parsed = source.parsed();
                    if (parsed.rejected()) throw new IllegalArgumentException("Fix config.toml's errors before saving settings.");
                    for (var change : edit.changes().entrySet()) {
                        Setting<?> setting = change.getKey();
                        if (!parsed.values().saved(setting).equals(edit.expected().get(setting)))
                            throw new StaleSetting(setting.name() + " changed on disk. Review the new value and try again.");
                        change.getValue().ifPresent(value -> check(setting, value));
                    }
                    TomlDocument document = TomlDocument.parse(source.text());
                    for (var change : edit.changes().entrySet()) {
                        for (LegacyKey legacy : parsed.legacyKeys())
                            if (legacy.to().equals(change.getKey().key())) document = document.remove(legacy.from());
                        document = write(document, change.getKey(), change.getValue());
                    }
                    String updated = document.text();
                    var checked = ConfigLoader.parse(file, updated, macOs);
                    if (checked.rejected() || edit.changes().entrySet().stream()
                            .anyMatch(change -> !checked.values().saved(change.getKey()).equals(change.getValue())))
                        throw new IllegalArgumentException("Cannot safely save these settings; review config.toml.");
                    return updated;
                });
            } catch (StaleSetting stale) {
                refresh(true, true);
                throw stale;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> void check(Setting<T> setting, Object value) {
        Optional<String> problem;
        try {
            problem = setting.kind().check((T) value);
        } catch (ClassCastException wrongType) {
            problem = Optional.of("Not a value for this setting.");
        }
        problem.ifPresent(message -> { throw new IllegalArgumentException(setting.name() + ": " + message); });
    }

    @SuppressWarnings("unchecked")
    private static <T> TomlDocument write(TomlDocument document, Setting<T> setting, Optional<?> value) {
        return SettingWriter.write(document, setting, (Optional<T>) value);
    }

    /** Applies explicit shortcut edits on the config worker, then publishes them through normal reload. */
    public CompletableFuture<State> saveKeybindings(Map<String, String> expected,
            Map<String, Optional<String>> changes, List<KeyBindings.Extension> extensions) {
        var before = Map.copyOf(expected);
        var edits = Map.copyOf(changes);
        var actions = List.copyOf(extensions);
        return submit(() -> {
            try {
                return transact(".shortcuts-", source -> {
                    var parsed = source.parsed();
                    if (parsed.rejected() || parsed.diagnostics().stream().anyMatch(d ->
                            d.severity() == ConfigDiagnostic.Severity.ERROR || d.key().startsWith("keybindings")))
                        throw new IllegalArgumentException("Fix the settings file's keybinding or configuration errors before saving shortcuts.");
                    if (!parsed.snapshot().keybindings().equals(before))
                        throw new StaleSetting("Shortcuts changed on disk. Review the updated bindings and try again.");
                    var next = new LinkedHashMap<>(before);
                    edits.forEach((id, value) -> { if (value.isPresent()) next.put(id, value.get()); else next.remove(id); });
                    var oldBindings = KeyBindings.withOverrides(macOs, before).withExtensions(actions).bindings();
                    var newBindings = KeyBindings.withOverrides(macOs, next).withExtensions(actions).bindings();
                    for (var bound : oldBindings.strokes().entrySet()) {
                        if (!edits.containsKey(bound.getKey()) && !newBindings.strokeFor(bound.getKey()).filter(bound.getValue()::equals).isPresent())
                            throw new IllegalArgumentException("Shortcut is used by " + bound.getKey() + ". Reassign it explicitly first.");
                    }
                    for (var edit : edits.entrySet()) {
                        var builtIn = ActionId.forId(edit.getKey());
                        var extension = actions.stream().filter(a -> a.id().equals(edit.getKey())).findFirst();
                        if (builtIn.isEmpty() && extension.isEmpty()) continue;
                        String intended = edit.getValue().orElseGet(() -> builtIn
                            .map(a -> KeyBindings.effectiveDefaultBinding(a, macOs))
                            .orElseGet(() -> extension.orElseThrow().defaultBinding().orElse("none")));
                        if (!KeyBindings.parse(intended, macOs).equals(newBindings.strokeFor(edit.getKey())))
                            throw new IllegalArgumentException("The default shortcut for " + edit.getKey() + " is unavailable. Assign a shortcut explicitly instead.");
                    }
                    String updated = KeybindingText.update(source.text(), edits);
                    var checked = ConfigLoader.parse(file, updated, macOs);
                    if (checked.rejected() || !checked.snapshot().keybindings().equals(next))
                        throw new IllegalArgumentException("Cannot safely save these shortcuts; review the settings file.");
                    return updated;
                });
            } catch (StaleSetting stale) {
                refresh(true, true);
                throw stale;
            }
        });
    }

    /** The file itself, through any symbolic link, or its absolute path when it does not exist yet. */
    private Path target() throws IOException {
        return Files.exists(file) || Files.isSymbolicLink(file) ? file.toRealPath() : file.toAbsolutePath();
    }

    /**
     * Reads the file (the template when it is missing), lets {@code edit} produce the new text or refuse by throwing,
     * then writes it atomically unless the file or its location changed meanwhile, and publishes the reload.
     * An {@link UncheckedIOException} from {@code edit} is rethrown as its cause.
     */
    private State transact(String prefix, Function<Source, String> edit) throws IOException {
        Path target = target();
        var original = TomlStateFile.readBounded(file, MAX_BYTES, "Configuration");
        String source = original.orElseGet(() -> ConfigTemplate.text(macOs));
        String updated;
        try {
            updated = edit.apply(new Source(source, ConfigLoader.parse(file, source, macOs)));
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        // An edit that changes nothing (Migrate Config with nothing to do) leaves the file and its timestamp alone.
        if (original.isPresent() && updated.equals(original.get())) return refresh(true, true);
        if (updated.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Configuration exceeds 1 MiB.");
        if (!TomlStateFile.readBounded(file, MAX_BYTES, "Configuration").equals(original))
            throw new IOException("Settings changed while saving. Try again.");
        if (!target.equals(target())) throw new IOException("Settings location changed while saving. Try again.");
        TomlStateFile.writeAtomically(target, prefix, updated);
        return refresh(true, true);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.config.*" --tests "dev.jasper.app.workspace.ShortcutEditorIntegrationTest"`
Expected: PASS (including every `KeybindingSaveTest` case, which now runs through `transact`).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/settings/SettingWriter.java jasper-app/src/main/java/dev/jasper/app/config/SettingsEdit.java jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java jasper-app/src/test/java/dev/jasper/app/config/SettingsSaveTest.java
git commit -m "Save settings through one verified transaction shared with shortcuts

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Migration and backup

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/config/ConfigMigration.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java` (`State` components, `readState`, `migrate`)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/ConfigMigrationTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/config/ConfigServiceMigrationTest.java`

**Interfaces:**
- Consumes: `TomlDocument.move/renameTable/removeTable/removeTableIfEmpty/keysUnder/set/remove`, `ConfigLoader.Result`, `ConfigLoader.VERSION`, Task 8's `transact`, `target()`, `Source`.
- Produces: `ConfigMigration.plan(String text, ConfigLoader.Result parsed, Predicate<String> pluginSettingsExist)` returning `Optional<ConfigMigration.Outcome>`; `ConfigMigration.Outcome(String text, int changes)`; `ConfigMigration.backUp(Path target, String original)` returning the backup `Path`; `ConfigService.State(ConfigSnapshot, List<ConfigDiagnostic>, Path, boolean, List<LegacyKey> legacyKeys, List<List<String>> deadTables)` plus the old four-argument constructor and `boolean migratable()`; `ConfigService.Migration(int changes, Optional<Path> backup, State state)`; `ConfigService.migrate(Predicate<String> pluginSettingsExist)` returning `CompletableFuture<Migration>`.

- [ ] **Step 1: Write the failing tests**

`ConfigMigrationTest.java`:

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigMigrationTest {
    private static final Path FILE = Path.of("/fixture/config.toml");
    private static ConfigLoader.Result parse(String text) { return ConfigLoader.parse(FILE, text, true); }
    private static ConfigMigration.Outcome migrate(String text) {
        return ConfigMigration.plan(text, parse(text), id -> id.equals("dev.jasper.history")).orElseThrow();
    }

    static final String VERSION_ONE = """
        # My Jasper settings
        [window]
        tab_height = 40 # roomy

        [font]
        # The font I like
        family = "Iosevka"
        size = 15.0 # points

        [terminal]
        scrollback = 5000

        [background]
        enabled = true

        [ui.theme]
        name = "IntelliJ Light"
        terminal = "dark" # always dark

        [palette.scopes.history]
        enabled = false

        [plugins."dev.jasper.history"]
        trivial_commands = ["ls"]

        [plugins."dev.jasper.gone"]
        x = 1
        """;

    static final String VERSION_TWO = """
        # My Jasper settings
        version = 2
        [window]
        tab_height = 40 # roomy

        [terminal.font]
        # The font I like
        family = "Iosevka"
        size = 15.0 # points

        [terminal]
        scrollback = 5000
        colors = "dark" # always dark

        [ui.theme]
        name = "IntelliJ Light"

        [plugins."dev.jasper.gone"]
        x = 1

        [system]
        run_in_background = true
        """;

    @Test void aVersionOneFileBecomesTheGoldenVersionTwoFile() {
        var outcome = migrate(VERSION_ONE);
        assertThat(outcome.text()).isEqualTo(VERSION_TWO);
        assertThat(outcome.changes()).isEqualTo(6);
        var migrated = parse(outcome.text());
        assertThat(migrated.legacyKeys()).isEmpty();
        assertThat(migrated.snapshot().font().family()).isEqualTo("Iosevka");
        assertThat(migrated.snapshot().fontSize()).isEqualTo(15f);
        assertThat(migrated.snapshot().terminalColors()).isEqualTo(TerminalColors.DARK);
        assertThat(migrated.snapshot().backgroundEnabled()).isTrue();
        assertThat(migrated.deadTables()).containsExactly(List.of("plugins", "dev.jasper.gone"));
        assertThat(ConfigMigration.plan(outcome.text(), migrated, id -> id.equals("dev.jasper.history"))).as("idempotent").isEmpty();
    }

    @Test void keysMoveOneByOneWhenTheirTableCannotBeRenamed() {
        var outcome = migrate("[terminal.font]\nfamily = 'A'\n[font]\nsize = 14\nfamily = 'B'\n");
        assertThat(outcome.text()).isEqualTo("version = 2\n[terminal.font]\nfamily = 'A'\nsize = 14\n");
        assertThat(outcome.changes()).isEqualTo(2);
        assertThat(migrate("font.size = 14\n").text()).isEqualTo("version = 2\n[terminal.font]\nsize = 14\n");
    }

    @Test void nothingToMigrateWritesNothingAndARejectedFileIsRefused() {
        String current = "[window]\ntab_height = 40\n";
        assertThat(ConfigMigration.plan(current, parse(current), id -> true)).isEmpty();
        String keptPlugin = "[plugins.\"dev.jasper.gone\"]\nx = 1\n";
        assertThat(ConfigMigration.plan(keptPlugin, parse(keptPlugin), id -> false)).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> ConfigMigration.plan("[font]\nsize = 'x'\n", parse("[font]\nsize = 'x'\n"), id -> true))
            .withMessage("Fix config.toml's errors before migrating.");
    }
}
```

`ConfigServiceMigrationTest.java`:

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigServiceMigrationTest {
    @TempDir Path directory;

    @Test void migrationBacksUpThenRewritesAndASecondRunDoesNothing() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, ConfigMigrationTest.VERSION_ONE);
        try (var service = new ConfigService(file, true)) {
            assertThat(service.initialState().migratable()).isTrue();
            var first = service.migrate(id -> id.equals("dev.jasper.history")).get(5, TimeUnit.SECONDS);
            assertThat(first.changes()).isEqualTo(6);
            assertThat(first.backup()).hasValue(directory.resolve("config.toml.v1.bak").toRealPath());
            assertThat(Files.readString(directory.resolve("config.toml.v1.bak"))).isEqualTo(ConfigMigrationTest.VERSION_ONE);
            assertThat(Files.readString(file)).isEqualTo(ConfigMigrationTest.VERSION_TWO);
            assertThat(first.state().legacyKeys()).isEmpty();
            var second = service.migrate(id -> id.equals("dev.jasper.history")).get(5, TimeUnit.SECONDS);
            assertThat(second.changes()).isZero();
            assertThat(second.backup()).isEmpty();
            try (var files = Files.list(directory)) {
                assertThat(files.map(p -> p.getFileName().toString()).toList()).containsExactlyInAnyOrder("config.toml", "config.toml.v1.bak");
            }
        }
    }

    @Test void anExistingBackupIsNeverOverwritten() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[font]\nsize = 14\n");
        Files.writeString(directory.resolve("config.toml.v1.bak"), "older");
        try (var service = new ConfigService(file, true)) {
            var result = service.migrate(id -> false).get(5, TimeUnit.SECONDS);
            assertThat(result.backup().orElseThrow().getFileName().toString()).isEqualTo("config.toml.v1.2.bak");
            assertThat(Files.readString(directory.resolve("config.toml.v1.bak"))).isEqualTo("older");
            assertThat(Files.readString(directory.resolve("config.toml.v1.2.bak"))).isEqualTo("[font]\nsize = 14\n");
        }
    }

    @Test void aSymlinkedConfigIsMigratedAndBackedUpBesideItsTarget() throws Exception {
        Path real = Files.createDirectories(directory.resolve("dotfiles")).resolve("jasper.toml");
        Path link = directory.resolve("config.toml");
        Files.writeString(real, "[font]\nsize = 14\n");
        try { Files.createSymbolicLink(link, real); }
        catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + unavailable.getMessage());
        }
        try (var service = new ConfigService(link, true)) {
            service.migrate(id -> false).get(5, TimeUnit.SECONDS);
            assertThat(Files.isSymbolicLink(link)).isTrue();
            assertThat(Files.readString(real)).isEqualTo("version = 2\n[terminal.font]\nsize = 14\n");
            assertThat(real.resolveSibling("jasper.toml.v1.bak")).hasContent("[font]\nsize = 14\n");
        }
    }

    @Test void aMissingOrBrokenFileIsNotMigrated() throws Exception {
        Path missing = directory.resolve("absent/config.toml");
        try (var service = new ConfigService(missing, true)) {
            assertThat(service.migrate(id -> true).get(5, TimeUnit.SECONDS).changes()).isZero();
            assertThat(missing).doesNotExist();
        }
        Path broken = directory.resolve("config.toml");
        Files.writeString(broken, "[font]\nsize = 'x'\n");
        try (var service = new ConfigService(broken, true)) {
            assertThatThrownBy(() -> service.migrate(id -> true).get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(Files.readString(broken)).isEqualTo("[font]\nsize = 'x'\n");
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.config.ConfigMigrationTest" --tests "dev.jasper.app.config.ConfigServiceMigrationTest"`
Expected: FAIL: compilation errors (`ConfigMigration`, `ConfigService.migrate`, `State.migratable` do not exist).

- [ ] **Step 3: Write the implementation**

`ConfigMigration.java`:

```java
package dev.jasper.app.config;

import dev.jasper.app.settings.LegacyKey;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.TomlDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Rewrites a version 1 config.toml to the current layout: pure text in and out, plus the backup it needs. */
final class ConfigMigration {
    private ConfigMigration() { }

    /**
     * A planned rewrite.
     *
     * @param text the migrated file
     * @param changes old keys moved or dropped, plus dead tables removed
     */
    record Outcome(String text, int changes) { }

    /**
     * Plans the rewrite of {@code text}; empty when there is nothing to migrate. Refuses a rejected file.
     *
     * @param pluginSettingsExist whether {@code plugins/<id>/<id>.toml} exists, so an old plugin table may go
     */
    static Optional<Outcome> plan(String text, ConfigLoader.Result parsed, Predicate<String> pluginSettingsExist) {
        if (parsed.rejected()) throw new IllegalArgumentException("Fix config.toml's errors before migrating.");
        List<List<String>> removable = parsed.deadTables().stream()
            .filter(table -> !table.getFirst().equals("plugins") || pluginSettingsExist.test(table.get(1))).toList();
        if (parsed.legacyKeys().isEmpty() && removable.isEmpty()) return Optional.empty();
        TomlDocument document = TomlDocument.parse(text);
        var done = new HashSet<LegacyKey>();
        var byTable = new LinkedHashMap<List<String>, List<LegacyKey>>();
        for (LegacyKey key : parsed.legacyKeys())
            byTable.computeIfAbsent(key.from().subList(0, key.from().size() - 1), table -> new java.util.ArrayList<>()).add(key);
        for (Map.Entry<List<String>, List<LegacyKey>> group : byTable.entrySet()) {
            List<String> table = group.getKey();
            List<LegacyKey> keys = group.getValue();
            List<String> target = keys.getFirst().to().subList(0, keys.getFirst().to().size() - 1);
            boolean sameShape = keys.stream().allMatch(key -> key.to().subList(0, key.to().size() - 1).equals(target)
                && key.from().getLast().equals(key.to().getLast()) && !superseded(parsed, key));
            Set<List<String>> moving = new HashSet<>(keys.stream().map(LegacyKey::from).toList());
            if (sameShape && new HashSet<>(document.keysUnder(table)).equals(moving) && document.keysUnder(target).isEmpty()) {
                try {
                    document = document.renameTable(table, target);
                    done.addAll(keys);
                } catch (IllegalArgumentException notAHeader) {
                    // Written as dotted keys: move them one by one below.
                }
            }
        }
        for (LegacyKey key : parsed.legacyKeys()) {
            if (done.contains(key)) continue;
            document = superseded(parsed, key) ? document.remove(key.from()) : document.move(key.from(), key.to());
        }
        for (List<String> table : byTable.keySet()) if (!table.isEmpty()) document = document.removeTableIfEmpty(table);
        for (List<String> table : removable) document = document.removeTable(table);
        document = document.removeTableIfEmpty(List.of("plugins")).removeTableIfEmpty(List.of("palette"));
        document = document.set(List.of("version"), Integer.toString(ConfigLoader.VERSION));
        return Optional.of(new Outcome(document.text(), parsed.legacyKeys().size() + removable.size()));
    }

    /** Whether the file also sets the current key, so the old one was ignored. */
    private static boolean superseded(ConfigLoader.Result parsed, LegacyKey key) {
        Setting<?> setting = CoreSettings.SCHEMA.find(key.to()).orElseThrow();
        return parsed.values().source(setting).filter(key.to()::equals).isPresent();
    }

    /** Copies the original beside {@code target} as {@code <name>.v1.bak}, numbering the name rather than overwriting. */
    static Path backUp(Path target, String original) throws IOException {
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);
        for (int number = 1; ; number++) {
            Path candidate = target.resolveSibling(target.getFileName() + ".v1" + (number == 1 ? "" : "." + number) + ".bak");
            try {
                Files.write(candidate, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return candidate;
            } catch (FileAlreadyExistsException taken) {
                // Every earlier backup is kept.
            }
        }
    }
}
```

Note on `removeTableIfEmpty(List.of("palette"))`: it only removes a `[palette]` header with nothing under it, so a user's `[palette] max_results` is untouched.

When there is nothing to migrate, the edit below returns the file's own text, and Task 8's `transact` then returns
without writing.

In `ConfigService.java`, replace the `State` record with:

```java
    /**
     * The published configuration.
     *
     * @param legacyKeys old keys the file still uses
     * @param deadTables old tables Migrate Config may remove
     */
    public record State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present,
                        List<LegacyKey> legacyKeys, List<List<String>> deadTables) {
        public State {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(file, "file");
            diagnostics = List.copyOf(diagnostics);
            legacyKeys = List.copyOf(legacyKeys);
            deadTables = deadTables.stream().map(List::copyOf).toList();
        }

        /** A state with nothing to migrate. */
        public State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present) {
            this(snapshot, diagnostics, file, present, List.of(), List.of());
        }

        /** Whether Migrate Config has old keys or dead tables to rewrite. */
        public boolean migratable() { return !legacyKeys.isEmpty() || !deadTables.isEmpty(); }
    }

    /**
     * What Migrate Config did.
     *
     * @param changes old keys and tables rewritten; zero when there was nothing to migrate
     * @param backup the copy of the original file
     * @param state the state published afterwards
     */
    public record Migration(int changes, Optional<Path> backup, State state) { }
```

In `readState`, replace the line `return new State(parsed.rejected() ? lastGood : parsed.snapshot(), parsed.diagnostics(), file, true);` with:

```java
            return parsed.rejected()
                ? new State(lastGood, parsed.diagnostics(), file, true)
                : new State(parsed.snapshot(), parsed.diagnostics(), file, true, parsed.legacyKeys(), parsed.deadTables());
```

Add this method after `save` (it uses fully qualified names, so no new imports):

```java
    /**
     * Migrate Config: backs the file up beside itself, then rewrites old keys to the current layout, on the config
     * worker. Nothing is written when there is nothing to migrate or the file is missing; a rejected file is refused.
     *
     * @param pluginSettingsExist whether a plugin already has its own settings file, so its old table may go
     */
    public CompletableFuture<Migration> migrate(java.util.function.Predicate<String> pluginSettingsExist) {
        Objects.requireNonNull(pluginSettingsExist, "pluginSettingsExist");
        return submit(() -> {
            if (TomlStateFile.readBounded(file, MAX_BYTES, "Configuration").isEmpty())
                return new Migration(0, Optional.empty(), refresh(true, true));
            var planned = new java.util.concurrent.atomic.AtomicReference<ConfigMigration.Outcome>();
            var backup = new java.util.concurrent.atomic.AtomicReference<Path>();
            State state = transact(".migrate-", source -> {
                var outcome = ConfigMigration.plan(source.text(), source.parsed(), pluginSettingsExist);
                if (outcome.isEmpty()) return source.text();
                planned.set(outcome.get());
                try {
                    backup.set(ConfigMigration.backUp(target(), source.text()));
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
                return outcome.get().text();
            });
            return planned.get() == null
                ? new Migration(0, Optional.empty(), state)
                : new Migration(planned.get().changes(), Optional.of(backup.get()), state);
        });
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.config.*"`
Expected: PASS. If the golden comparison differs, print both texts, find the operation responsible (`renameTable`, `move`, `removeTable`, `deleteHeader`), and fix it; do not edit `VERSION_TWO` unless the difference is a documented placement rule, and then record the change in the status banner.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/config/ConfigMigration.java jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java jasper-app/src/test/java/dev/jasper/app/config/ConfigMigrationTest.java jasper-app/src/test/java/dev/jasper/app/config/ConfigServiceMigrationTest.java
git commit -m "Migrate version 1 settings files on request, with a backup

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Migrate Config action and diagnostics button

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java` (`settingsFileExists`)
- Modify: `jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java` (new test)

**Interfaces:**
- Consumes: `ConfigService.migrate`, `ConfigService.State.migratable()`, `ConfigService.Migration`.
- Produces: `ConfigurationController.pluginSettingsExist(Predicate<String>)`, `migrationAvailable()`, `migrate()` returning `CompletableFuture<String>`; `WindowContent.onInfo` (`Consumer<String>`), `WindowContent.connectConfiguration(Runnable settings, Runnable reload, Runnable migrate, Runnable unregister)`; action `config.migrate` "Migrate Config"; `PluginRuntime.settingsFileExists(String id)`.

- [ ] **Step 1: Write the failing test**

Add to `ConfigurationTestSupport.java`:

```java
    public boolean migrationAvailable() { return controller.migrationAvailable(); }
    public java.util.concurrent.CompletableFuture<String> migrate() { return controller.migrate(); }
    public void pluginSettingsExist(java.util.function.Predicate<String> test) { controller.pluginSettingsExist(test); }
```

Add to `ConfigurationControllerTest.java`:

```java
    @Test void diagnosticsOfferMigrateConfigWhichRewritesTheFileAndReportsIt() throws Exception {
        start("[font]\nsize = 19\n"); edt(this::owner);
        var owner = owners.getFirst();
        var shown = new ArrayList<JComponent>();
        var infos = new java.util.concurrent.CopyOnWriteArrayList<String>();
        edt(() -> {
            owner.showConfigDiagnostics = shown::add;
            owner.onInfo = infos::add;
            assertThat(controller.migrationAvailable()).isTrue();
            owner.status().configButton().doClick();
        });
        assertThat(shown).hasSize(1);
        var button = find(shown.getFirst(), "configuration.migrate");
        edt(button::doClick);
        until(() -> !infos.isEmpty());
        assertThat(infos).containsExactly("Migrated 1 old setting to the new layout. Backup: config.toml.v1.bak.");
        assertThat(Files.readString(directory.resolve("config.toml"))).isEqualTo("version = 2\n[terminal.font]\nsize = 19\n");
        edt(() -> {});
        edt(() -> assertThat(controller.migrationAvailable()).isFalse());
        var nothing = new AtomicReference<String>();
        edt(() -> controller.migrate().thenAccept(nothing::set));
        until(() -> nothing.get() != null);
        assertThat(nothing.get()).isEqualTo("Nothing to migrate: config.toml already uses the current layout.");
    }

    @Test void oldPluginTablesCountOnlyWhenThePluginHasItsOwnFile() throws Exception {
        start("[plugins.\"dev.x\"]\na = 1\n"); edt(this::owner);
        edt(() -> assertThat(controller.migrationAvailable()).isFalse());
        edt(() -> controller.pluginSettingsExist(id -> id.equals("dev.x")));
        edt(() -> assertThat(controller.migrationAvailable()).isTrue());
    }

    private static JButton find(java.awt.Component root, String name) {
        if (root instanceof JButton button && name.equals(button.getName())) return button;
        if (root instanceof java.awt.Container container)
            for (var child : container.getComponents()) {
                JButton found = find(child, name);
                if (found != null) return found;
            }
        return null;
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.workspace.ConfigurationControllerTest"`
Expected: FAIL: compilation errors (`migrationAvailable`, `migrate`, `pluginSettingsExist`, `onInfo` do not exist).

- [ ] **Step 3: Write the implementation**

In `ConfigurationController.java` add the imports `java.util.Objects`, `java.util.concurrent.CompletionException`, `java.util.function.Predicate`, then:

- a field: `private Predicate<String> pluginSettingsExist = id -> false;`
- replace `shown()`'s return statement with:

```java
        var removable = state.deadTables().stream()
            .filter(table -> !table.getFirst().equals("plugins") || pluginSettingsExist.test(table.get(1))).toList();
        return new ConfigService.State(state.snapshot(), merged, state.file(), state.present(), state.legacyKeys(), removable);
```

- in `register`, replace the `owner.connectConfiguration(...)` call with:

```java
        owner.connectConfiguration(() -> reportFailure(owner, service.openSettings(editor)),
            () -> reportFailure(owner, service.reload()), () -> migrateFor(owner), () -> unregister(owner));
```

- these methods:

```java
    /** Which plugins already have their own settings file, so Migrate Config may drop their old tables. */
    void pluginSettingsExist(Predicate<String> test) {
        requireEdt();
        pluginSettingsExist = Objects.requireNonNull(test, "test");
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown());
    }

    /** Whether Migrate Config has anything to do for the file as last loaded. */
    boolean migrationAvailable() {
        requireEdt();
        return shown().migratable();
    }

    /** Runs Migrate Config; completes with the message to show. */
    CompletableFuture<String> migrate() {
        requireEdt();
        return service.migrate(pluginSettingsExist).thenApply(result -> result.changes() == 0
            ? "Nothing to migrate: " + result.state().file().getFileName() + " already uses the current layout."
            : "Migrated " + result.changes() + " old setting" + (result.changes() == 1 ? "" : "s") + " to the new layout. Backup: "
                + result.backup().map(path -> path.getFileName().toString()).orElse("none") + ".");
    }

    private void migrateFor(WindowContent owner) {
        migrate().whenComplete((message, failure) -> SwingUtilities.invokeLater(() -> {
            if (closed || !owners.contains(owner)) return;
            if (failure == null) owner.onInfo.accept(message);
            else owner.onError.accept((failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure).getMessage());
        }));
    }
```

In `WindowContent.java`:

- after the `onError` field add:

```java
    /** Shows a plain informational message, such as the result of Migrate Config. */
    public Consumer<String> onInfo = message -> JOptionPane.showMessageDialog(this, message, "Jasper", JOptionPane.INFORMATION_MESSAGE);
```

- change `private Runnable openSettings, reloadConfiguration;` to `private Runnable openSettings, reloadConfiguration, migrateConfiguration;`
- replace `connectConfiguration` with:

```java
    public void connectConfiguration(Runnable settings, Runnable reload, Runnable unregister) {
        connectConfiguration(settings, reload, null, unregister);
    }

    /** As the three-argument form, plus Migrate Config for the diagnostics dialog; {@code migrate} may be null. */
    public void connectConfiguration(Runnable settings, Runnable reload, Runnable migrate, Runnable unregister) {
        if (closed) return;
        openSettings = settings; reloadConfiguration = reload; migrateConfiguration = migrate; unregisterConfiguration = unregister;
        action(ActionId.OPEN_SETTINGS).putValue(Action.SHORT_DESCRIPTION, "Open configuration file");
        action(ActionId.RELOAD_CONFIG).putValue(Action.SHORT_DESCRIPTION, "Reload configuration file");
        updateActions();
    }
```

- in `disconnectConfiguration`, change the first line to `openSettings = null; reloadConfiguration = null; migrateConfiguration = null; unregisterConfiguration = () -> {};`
- in `setConfigurationState`, replace `showConfigDiagnostics.accept(new JScrollPane(text));` with:

```java
            JScrollPane scroll = new JScrollPane(text);
            Runnable migrate = migrateConfiguration;
            if (!state.migratable() || migrate == null) { showConfigDiagnostics.accept(scroll); return; }
            var button = new JButton("Migrate Config…");
            button.setName("configuration.migrate");
            button.setToolTipText("Rewrite old keys in this file to the current layout; the original is backed up first.");
            button.addActionListener(event -> {
                Window dialog = SwingUtilities.getWindowAncestor(button);
                if (dialog != null) dialog.dispose();
                migrate.run();
            });
            var actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 0, 0));
            actions.setOpaque(false);
            actions.add(button);
            var details = new JPanel(new BorderLayout(0, 8));
            details.setOpaque(false);
            details.add(scroll, BorderLayout.CENTER);
            details.add(actions, BorderLayout.SOUTH);
            showConfigDiagnostics.accept(details);
```

In `PluginRuntime.java` add:

```java
    /**
     * Whether a plugin's own settings file exists, so the old {@code [plugins."<id>"]} table in config.toml may go.
     *
     * @param id a plugin id
     * @return whether {@code plugins/<id>/<id>.toml} is a regular file
     */
    public boolean settingsFileExists(String id) {
        return Files.isRegularFile(PluginSettingsFiles.file(options.userDirectory(), id));
    }
```

(add `import java.nio.file.Files;` if it is not already imported).

In `JasperApplication.java`:

- add a field `private ActionEntry migrateAction;`
- in `startPlugins`, directly after the `contributions.addMenuSection(MenuTarget.standard(MenuTarget.Slot.FILE))...` line for `plugins.manage`, add:

```java
        if (configuration != null) configuration.pluginSettingsExist(plugins::settingsFileExists);
        migrateAction = contributions.addAction("config.migrate", "Migrate Config", null,
            List.of("configuration", "settings", "upgrade", "old keys", "config.toml"), Optional.empty(), invocation -> migrateConfiguration());
        updateMigrateAction();
```

- in the `configuration.onSnapshot(snapshot -> { ... })` listener body, add `updateMigrateAction();` as its last statement;
- add these methods next to `notice`:

```java
    private void updateMigrateAction() {
        if (migrateAction != null && configuration != null) migrateAction.setEnabled(configuration.migrationAvailable());
    }

    private void migrateConfiguration() {
        if (configuration == null || quitting || stopped) return;
        configuration.migrate().whenComplete((message, failure) -> SwingUtilities.invokeLater(() -> {
            if (failure == null) info(message);
            else notice((failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure).getMessage());
        }));
    }

    /** A plain informational message: the window the user used last, or the log when there is none. */
    void info(String message) {
        TerminalWindow target = lastActive != null && windows.contains(lastActive) ? lastActive : windows.stream().findFirst().orElse(null);
        if (target == null) LOG.log(System.Logger.Level.INFO, message);
        else target.content().onInfo.accept(message);
    }
```

(`SwingUtilities` is already imported in `JasperApplication`; if it is not, use `javax.swing.SwingUtilities` fully qualified.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.workspace.*" --tests "dev.jasper.app.application.*"`
Expected: PASS (the existing `malformedReloadUpdatesAccessiblePlainTextStatusAndRetainsWorkingSettings` still sees a bare `JScrollPane`, because that state is not migratable).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java
git commit -m "Offer Migrate Config from the palette and the diagnostics dialog

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Plugin diagnostics name their own file

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettingsFiles.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettings.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify tests: `plugins/PluginSettingsFilesTest.java`, `plugins/PluginSettingsTest.java`, `plugins/PluginRuntimeTest.java`, `plugins/BundledSamplePluginTest.java`, `plugins/RemoteShortcutConfigTest.java`, `plugins/HostedPaletteTest.java`, `plugins/AppContractTest.java`, `pluginmanager/PluginManagerTest.java`, `application/ConfigurationTestSupport.java`, `workspace/ConfigurationControllerTest.java`

**Interfaces:**
- Consumes: `ConfigDiagnostic`.
- Produces: `PluginRuntime.SettingsReports(Consumer<ConfigDiagnostic> report, Consumer<Path> clear)` with `SettingsReports.IGNORED`, replacing the `BiConsumer<String, String> configReport` parameter of both public `PluginRuntime` constructors; `PluginSettingsFiles(Path, Consumer<ConfigDiagnostic>)`, `PluginSettingsFiles.Position(int line, int column)`, `Optional<Position> position(String id, List<String> path)`; `PluginSettings(String, Path, Map<String, Object>, Containment, Function<List<String>, Optional<PluginSettingsFiles.Position>>, Consumer<ConfigDiagnostic>)` and `update(Map, boolean force)`; `PluginHost.Environment` gains `BiFunction<String, List<String>, Optional<PluginSettingsFiles.Position>> positions` after `settingsFile`, and `configReport` becomes `Consumer<ConfigDiagnostic>`; `PluginHost.settingsChanged(Set<String> reloaded)`; `ConfigurationController.report(ConfigDiagnostic)`, `clearReports(Path)`.

- [ ] **Step 1: Write the failing tests**

Replace `PluginSettingsTest.typedGettersNestedTablesChangeDetectionAndQualifiedReports`'s constructor calls and report assertions:

```java
        List<ConfigDiagnostic> reports = new ArrayList<>();
        var file = java.nio.file.Path.of("/tmp/dev.example.tool.toml");
        java.util.function.Function<List<String>, java.util.Optional<PluginSettingsFiles.Position>> positions =
            path -> path.equals(List.of("name")) ? java.util.Optional.of(new PluginSettingsFiles.Position(3, 1)) : java.util.Optional.empty();
        var settings = new PluginSettings("dev.example.tool", file, Map.of("name", "x", "count", 3L, "on", true,
            "hosts", List.of("a"), "proxy", Map.of("port", 22L)), containment, positions, reports::add);
```

and at the end of the method:

```java
        settings.report("name", "too short");
        new PluginSettings("dev.example.tool", file, Map.of("proxy", Map.of()), containment, positions, reports::add)
            .table("proxy").orElseThrow().report("port", "missing");
        assertThat(reports).extracting(ConfigDiagnostic::file, ConfigDiagnostic::key, ConfigDiagnostic::line, ConfigDiagnostic::message)
            .containsExactly(tuple(file, "name", 3, "too short"), tuple(file, "proxy.port", 0, "missing"));
        settings.onChanged(() -> changes.add("forced"));
        settings.update(Map.of(), true);
        assertThat(changes).as("a forced update reruns listeners on equal values").contains("forced");
```

(import `dev.jasper.app.config.ConfigDiagnostic` and `static org.assertj.core.api.Assertions.tuple`).

In `PluginSettingsFilesTest.readsPollsAndKeepsTheLastGoodValuesWhenTheFileBreaks`, change `List<String> reports = new ArrayList<>();` to `List<ConfigDiagnostic> reports = new ArrayList<>();` (import `dev.jasper.app.config.ConfigDiagnostic`), construct with `new PluginSettingsFiles(user(), reports::add)`, and replace the report assertion with:

```java
        assertThat(reports).singleElement().satisfies(d -> {
            assertThat(d.file()).isEqualTo(file);
            assertThat(d.key()).isEmpty();
            assertThat(d.line()).isEqualTo(2);
            assertThat(d.message()).contains(file.getFileName().toString());
        });
        assertThat(files.position("dev.example.tool", List.of("greeting"))).hasValue(new PluginSettingsFiles.Position(1, 1));
```

and before the final `Files.writeString(file, "greeting = \"again\"\n");` add a recovery check:

```java
        Files.writeString(file, "greeting = \"hi\"\n[proxy]\nport = 22\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 7000));
        assertThat(files.refresh(false)).as("recovering from a broken file counts as a reload").containsExactly("dev.example.tool");
```

Add to `ConfigurationControllerTest.java`:

```java
    @Test void pluginReportsSurviveAppReloadsAndClearPerFile() throws Exception {
        start(""); edt(this::owner);
        Path a = directory.resolve("plugins/a/a.toml"), b = directory.resolve("plugins/b/b.toml");
        edt(() -> {
            controller.report(new dev.jasper.app.config.ConfigDiagnostic(dev.jasper.app.config.ConfigDiagnostic.Severity.WARNING, a, 2, 1, "x", "bad x"));
            controller.report(new dev.jasper.app.config.ConfigDiagnostic(dev.jasper.app.config.ConfigDiagnostic.Severity.WARNING, b, 0, 0, "", "bad file"));
        });
        reload("[window]\ntab_height = 40\n");
        edt(() -> assertThat(controller.shown().diagnostics()).extracting(d -> d.file()).containsExactly(a, b));
        edt(() -> controller.clearReports(a));
        edt(() -> assertThat(controller.shown().diagnostics()).extracting(d -> d.file()).containsExactly(b));
    }
```

and to `ConfigurationTestSupport.java`:

```java
    public void report(ConfigDiagnostic diagnostic) { controller.report(diagnostic); }
    public void clearReports(Path file) { controller.clearReports(file); }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.plugins.PluginSettings*" --tests "dev.jasper.app.workspace.ConfigurationControllerTest"`
Expected: FAIL: compilation errors (new constructors, `Position`, `report(ConfigDiagnostic)`, `clearReports` do not exist).

- [ ] **Step 3: Write the implementation**

`PluginSettingsFiles.java`:
- imports: add `dev.jasper.app.config.ConfigDiagnostic`, `java.util.List`, `java.util.function.Consumer`, `org.tomlj.TomlParseError`, `org.tomlj.TomlPosition`; remove `java.util.function.BiConsumer`.
- replace the `Known` class, the `report` field and the constructor with:

```java
    /** A 1-based line and column in a plugin's settings file. */
    record Position(int line, int column) { }

    private static final class Known { Fingerprint fingerprint; Map<String, Object> values = Map.of(); TomlParseResult parsed; boolean broken; }

    private final Path userDirectory;
    private final Consumer<ConfigDiagnostic> report;
    private final Map<String, Known> known = new HashMap<>();

    PluginSettingsFiles(Path userDirectory, Consumer<ConfigDiagnostic> report) { this.userDirectory = userDirectory; this.report = report; }

    /** Where {@code path} is in the plugin's file as last read successfully. */
    synchronized Optional<Position> position(String id, List<String> path) {
        Known state = known.get(id);
        if (state == null || state.parsed == null) return Optional.empty();
        TomlPosition position = state.parsed.inputPositionOf(path);
        return position == null ? Optional.empty() : Optional.of(new Position(position.line(), position.column()));
    }
```

- update the class Javadoc's last sentence to: `A file that fails to parse keeps its last good values and is reported against that file; recovering from that counts as a change.`
- update `refresh`'s Javadoc to: `Re-reads every known file whose fingerprint changed ({@code force}: all of them); the ids whose values changed or that recovered from a broken file.`
- replace `read` with:

```java
    private boolean read(String id, Known state, boolean force) {
        Path file = file(userDirectory, id);
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            var next = new Fingerprint(attributes.lastModifiedTime(), attributes.size());
            if (!force && next.equals(state.fingerprint)) return false;
            state.fingerprint = next;
            byte[] bytes;
            try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES) throw new IOException("larger than 1 MiB");
            TomlParseResult parsed = Toml.parse(new String(bytes, StandardCharsets.UTF_8));
            if (parsed.hasErrors()) {
                TomlParseError error = parsed.errors().getFirst();
                broken(state, file, error.position().line(), error.position().column(), error.toString());
                return false;
            }
            Map<String, Object> values = ConfigLoader.freeze(parsed);
            boolean recovered = state.broken;
            state.broken = false;
            state.parsed = parsed;
            if (values.equals(state.values) && !recovered) return false;
            state.values = values;
            return true;
        } catch (NoSuchFileException absent) {
            state.fingerprint = null;
            state.parsed = null;
            boolean had = !state.values.isEmpty() || state.broken;
            state.broken = false;
            state.values = Map.of();
            return had;
        } catch (IOException failure) {
            broken(state, file, 0, 0, failure.getMessage());
            return false;
        }
    }

    private void broken(Known state, Path file, int line, int column, String why) {
        if (!state.broken) report.accept(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, file, line, column, "",
            file.getFileName() + " could not be read: " + why + "; the last good settings stay in force."));
        state.broken = true;
    }
```

`PluginSettings.java`:
- imports: add `dev.jasper.app.config.ConfigDiagnostic`, `java.util.ArrayList`, `java.util.function.Consumer`, `java.util.function.Function`, `org.tomlj.Toml`; remove `java.util.function.BiConsumer`.
- replace the `report` field and constructor with:

```java
    private final Consumer<ConfigDiagnostic> report;
    private final Function<List<String>, Optional<PluginSettingsFiles.Position>> positions;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final View root;
    private volatile Map<String, Object> values;

    private final java.nio.file.Path file;

    PluginSettings(String pluginId, java.nio.file.Path file, Map<String, Object> initial, Containment containment,
                   Function<List<String>, Optional<PluginSettingsFiles.Position>> positions, Consumer<ConfigDiagnostic> report) {
        this.pluginId = pluginId; this.file = file; this.containment = containment; this.positions = positions; this.report = report;
        this.values = Map.copyOf(initial);
        this.root = new View(() -> values, List.of());
    }

    void update(Map<String, Object> next) { update(next, false); }

    /** Replaces the values; listeners run when they changed, or always when {@code force} (the file was reloaded). */
    void update(Map<String, Object> next, boolean force) {
        if (next.equals(values) && !force) return;
        values = Map.copyOf(next);
        for (Runnable listener : listeners) containment.run(pluginId, "configuration change", listener);
    }
```

- in `View`, change the `prefix` field and constructor to `List<String>`:

```java
        private final Supplier<Map<String, Object>> table;
        private final List<String> prefix;

        View(Supplier<Map<String, Object>> table, List<String> prefix) { this.table = table; this.prefix = prefix; }
```

- in `View.table(String key)`, build the nested prefix as a list:

```java
            var nested = new ArrayList<>(prefix);
            nested.add(key);
            return Optional.of(new View(() -> nested(key), List.copyOf(nested)));
```

- replace `View.report` with:

```java
        @Override public void report(String key, String message) {
            var path = new ArrayList<>(prefix);
            path.add(key);
            var at = positions.apply(path);
            report.accept(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, file,
                at.map(PluginSettingsFiles.Position::line).orElse(0), at.map(PluginSettingsFiles.Position::column).orElse(0),
                Toml.joinKeyPath(path), message));
        }
```

`PluginHost.java`:
- in `Environment`, replace `Function<String, Path> settingsFile, BiConsumer<String, String> configReport,` with
  `Function<String, Path> settingsFile, java.util.function.BiFunction<String, List<String>, java.util.Optional<PluginSettingsFiles.Position>> positions, Consumer<dev.jasper.app.config.ConfigDiagnostic> configReport,`
  (remove the now unused `BiConsumer` import if nothing else uses it).
- in `start`, replace the `new PluginSettings(...)` call with:

```java
        var context = new HostedContext(this, hosted, new PluginSettings(id, environment.settingsFile().apply(id), environment.settings().apply(id),
            containment, path -> environment.positions().apply(id, path), environment.configReport()));
```

- replace `settingsChanged()` with:

```java
    void settingsChanged() { settingsChanged(Set.of()); }

    /** Pushes each active plugin's current values; plugins in {@code reloaded} rerun their listeners even when equal. */
    void settingsChanged(Set<String> reloaded) {
        requireUi();
        for (HostedContext context : List.copyOf(contexts.values()))
            if (context.state == HostedContext.State.ACTIVE) {
                String id = context.hosted.info().id();
                context.settings.update(environment.settings().apply(id), reloaded.contains(id));
            }
    }
```

`PluginRuntime.java`:
- add, next to `Outcome`:

```java
    /**
     * Where plugin settings problems go, on the UI thread.
     *
     * @param report adds one problem, attributed to the plugin's own settings file
     * @param clear forgets every problem in one settings file, before its plugin sees the reloaded values
     */
    public record SettingsReports(Consumer<ConfigDiagnostic> report, Consumer<Path> clear) {
        /** Drops every report. */
        public static final SettingsReports IGNORED = new SettingsReports(diagnostic -> { }, file -> { });
    }
```

  (import `dev.jasper.app.config.ConfigDiagnostic`).
- replace the field `private final BiConsumer<String, String> configReport;` with `private final SettingsReports reports;`
- in both public constructors, change the parameter `BiConsumer<String, String> configReport` to `SettingsReports reports`, the Javadoc `@param configReport receives plugin complaints about their settings as key and message` to `@param reports receives plugin complaints about their settings, and clears them per file`, the delegating call's argument to `reports`, and the assignments to:

```java
        this.reports = Objects.requireNonNull(reports);
        this.settingsFiles = new PluginSettingsFiles(options.userDirectory(), diagnostic -> onUi(() -> reports.report().accept(diagnostic)));
```

- add:

```java
    /** The settings poller reads files off the UI thread; reports always reach the application on it. */
    private static void onUi(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task);
    }
```

- in `start`, change the `PluginHost.Environment` arguments `id -> PluginSettingsFiles.file(options.userDirectory(), id), configReport,` to `id -> PluginSettingsFiles.file(options.userDirectory(), id), settingsFiles::position, reports.report(),`
- change the poller body to:

```java
                settingsPoller.scheduleWithFixedDelay(() -> {
                    Set<String> changed = settingsFiles.refresh(false);
                    if (!changed.isEmpty()) SwingUtilities.invokeLater(() -> applySettings(changed));
                }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
```

- in `configurationChanged`, replace `settingsFiles.refresh(true);` and `current.settingsChanged();` with `applySettings(settingsFiles.refresh(true));`
- replace `applySettings` and `pollSettingsNow` with:

```java
    /** Forgets each reloaded file's problems, then lets its plugin see the values again. */
    private void applySettings(Set<String> reloaded) {
        for (String id : reloaded) reports.clear().accept(PluginSettingsFiles.file(options.userDirectory(), id));
        PluginHost current = host;
        if (current != null) current.settingsChanged(reloaded);
    }

    /** Test seam: one poll of the settings files, on the UI thread. */
    void pollSettingsNow() {
        Set<String> changed = settingsFiles.refresh(false);
        if (!changed.isEmpty()) applySettings(changed);
    }
```

  (keep the existing Javadoc on `pollSettingsNow` if it has one; remove the unused `BiConsumer` import if nothing else uses it.)

`ConfigurationController.java`:
- add a field: `private final java.util.Map<Path, java.util.LinkedHashSet<ConfigDiagnostic>> pluginReports = new java.util.LinkedHashMap<>();`
- in `shown()`, after `merged.addAll(reported);` add `pluginReports.values().forEach(merged::addAll);`, and update its Javadoc to "…and problems plugins reported about their own settings files."
- add:

```java
    /** A plugin's problem with its own settings file; shown until that file is reloaded. */
    void report(ConfigDiagnostic diagnostic) {
        requireEdt();
        if (closed) return;
        pluginReports.computeIfAbsent(diagnostic.file(), file -> new java.util.LinkedHashSet<>()).add(diagnostic);
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown());
    }

    /** Forgets every plugin problem in {@code file}. */
    void clearReports(Path file) {
        requireEdt();
        if (closed || pluginReports.remove(file) == null) return;
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown());
    }
```

  `accept` keeps clearing `reported` (application problems such as binding conflicts); it does not clear `pluginReports`.

`JasperApplication.java`: replace the runtime's third argument `(key, message) -> { if (configuration != null) configuration.report(key, message); },` with:

```java
            new PluginRuntime.SettingsReports(
                diagnostic -> { if (configuration != null) configuration.report(diagnostic); },
                file -> { if (configuration != null) configuration.clearReports(file); }),
```

Tests constructing the runtime or host:
- `BundledSamplePluginTest` (two places), `RemoteShortcutConfigTest`, `PluginManagerTest`: replace `(key, message) -> { }` / `(key, message) -> {}` with `PluginRuntime.SettingsReports.IGNORED` (in `PluginManagerTest`, qualify as `dev.jasper.app.plugins.PluginRuntime.SettingsReports.IGNORED`).
- `PluginRuntimeTest.runtime(...)`: replace `(key, message) -> reports.add(key + ": " + message)` with
  `new PluginRuntime.SettingsReports(d -> reports.add(d.file().getFileName() + ": " + d.message()), file -> reports.add("clear " + file.getFileName()))`,
  and in `aChangedSettingsFileReachesThePluginWithoutAConfigReload` replace `assertThat(reports).singleElement().asString().startsWith("plugins.dev.example.probe: ");` with
  `assertThat(reports).filteredOn(r -> !r.startsWith("clear ")).singleElement().asString().startsWith("dev.example.probe.toml: ");`
- `HostedPaletteTest` and `AppContractTest`: in each `PluginHost.Environment(...)` call, the argument after `id -> data.resolve(id + ".toml")` is `(key, message) -> { }` (in `AppContractTest` it starts the next line). Replace that one argument with the two arguments `(id, path) -> java.util.Optional.empty(), diagnostic -> { }`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.plugins.*" --tests "dev.jasper.app.pluginmanager.*" --tests "dev.jasper.app.workspace.ConfigurationControllerTest" --tests "dev.jasper.app.application.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Attribute plugin settings problems to the plugin's own file

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Documentation, status and the full check

**Files:**
- Modify: `docs/configuration.md`, `docs/themes.md`, `docs/packaging.md`, `README.md`, `docs/app-maintenance.md`, `docs/app-architecture.md`, `docs/plugin-authoring.md`, `docs/STATUS.md`
- Modify: this plan's status banner

- [ ] **Step 1: Replace old key names in prose**

```bash
python3 - <<'PY'
import pathlib
pairs = [
    ("`[background] enabled = true`", "`[system] run_in_background = true`"),
    ("[background]\nenabled = false", "[system]\nrun_in_background = false"),
    ("`background.enabled`", "`system.run_in_background`"),
    ("`ui.theme.terminal = \"match\"`", "`terminal.colors = \"match\"`"),
    ("`ui.theme.terminal`", "`terminal.colors`"),
    ("saved `font.size`", "saved `terminal.font.size`"),
    ("`[font]` controls terminal text", "`[terminal.font]` controls terminal text"),
    ("terminal `[font]`", "`[terminal.font]`"),
]
for name in ["docs/configuration.md", "docs/themes.md", "docs/packaging.md", "README.md"]:
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    for old, new in pairs:
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
PY
grep -n "background\.enabled\|\[background\]\|ui\.theme\.terminal\|\`font\.size\`\|\[font\]" docs/configuration.md docs/themes.md docs/packaging.md README.md
```

Expected: no output. Fix any line the grep still prints by hand, using the version 2 key.

- [ ] **Step 2: Document versions and Migrate Config**

In `docs/configuration.md`, insert this section directly before `## Reload and diagnostics`:

```markdown
## Versions and Migrate Config

`config.toml` starts with `version = 2`. A file without `version` is version 1, whose keys still work:

| Version 1 key | Version 2 key |
|---|---|
| `[font]` `family`, `size`, `fallback`, `ligatures`, `line_height` | `[terminal.font]` with the same names |
| `ui.theme.terminal` (version 1) | `terminal.colors` |
| `background.enabled` (version 1) | `system.run_in_background` |

(The "(version 1)" suffix also keeps these rows distinct from the key table, which a test checks names no old key.)

Each old key is read as its new one, with a warning: *Moved to terminal.font.size. Use Migrate Config to
update the file.* When a file sets both, the new key wins and the old one is reported as superseded.

**Migrate Config** (in the command palette, and as a button in the configuration details when there is
something to migrate) rewrites the file in place: it renames `[font]` to `[terminal.font]` when it can,
moves the other old keys with their comments, removes the old `palette.scopes` table and any
`[plugins."<id>"]` table whose plugin already has its own settings file, and sets `version = 2`. It first
copies the original to `config.toml.v1.bak` beside the file (beside the link's target for a symlinked
config), numbering the name instead of overwriting an existing backup. Nothing happens automatically, and
a version 1 file keeps working until you migrate it.

Jasper edits the file itself only for shortcut saves and Migrate Config. Both keep your comments and
formatting and refuse, rather than guess, when a change cannot be made exactly.
```

- [ ] **Step 3: Fix stale and contributor documentation**

- `docs/plugin-authoring.md`: replace `Users write \`[plugins."<id>"]\` in \`config.toml\`. It is read-only to` with `Users edit \`plugins/<id>/<id>.toml\`; see [plugin settings](configuration.md#plugins). It is read-only to` (keep the rest of the bullet). Problems a plugin reports with `report(key, message)` now appear against that file with the key's line: add the sentence `Problems you report with \`config().report(key, message)\` are shown against that file, at the key's line, until the file is next reloaded.` to the same bullet.
- `docs/app-architecture.md`:
  - replace `separately preserves a temporary theme choice until the saved appearance changes.` with `separately preserves a temporary terminal-colours choice until the saved \`terminal.colors\` changes.`
  - in the package table, add after the `config` row: `| \`settings\` | Pure declarative settings machinery: setting kinds, descriptors, the schema, loaded values and the comment-preserving TomlDocument editor. No Jasper dependencies; caller chooses thread. |`
  - change the `config` row's text to `Immutable values and pure parsing through CoreSettings.SCHEMA; ConfigService owns background watch/reload, the save transaction (settings, shortcuts) and Migrate Config, and marshals delivery to EDT. Application closes the service.`
- `docs/app-maintenance.md`, section `## Saved, live and session-only settings`: replace the sentence beginning `Every new field must update:` up to and including `; and, for new-session-only values, LaunchSettings capture and SessionLaunchOptions construction.` with:

```markdown
Every new field starts in [CoreSettings](../jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java): declare a `Setting` (key, kind, default, label, description, keywords, when it applies) and add it to `SCHEMA` in template order. Then add the `ConfigSnapshot` component, its builder/toBuilder copying and its line in `ConfigSnapshot.from`; a row in the key table of `docs/configuration.md` and a line in the root `config.example.toml` (both are checked against the schema); comparison against the previous saved value in the responsible live owner (WorkspaceConfiguration for view/workspace fields, ThemeController for appearance, JasperApplication for app policy); and, for new-session-only values, LaunchSettings capture and SessionLaunchOptions construction. The loader, the generated template and saving need no per-field code. To rename a key, give the setting an `alias` for the old one: old files keep working with a warning, and Migrate Config moves it.
```

- [ ] **Step 4: Record the work in `docs/STATUS.md` and the plan banner**

Add at the top of `## Current state — …` in `docs/STATUS.md`:

```markdown
- **Settings foundation** (`claude/settings-foundation`, 2026-09-27; spec
  `docs/superpowers/specs/2026-09-27-jasper-settings-foundation-design.md`, plan
  `docs/superpowers/plans/2026-09-27-jasper-settings-foundation.md`): `config.toml` is described by one
  schema (`dev.jasper.app.settings`, `config.CoreSettings`); the loader, the generated template and the
  example/guide tests read it. Version 2 keys: `[terminal.font]`, `terminal.colors`,
  `system.run_in_background`; version 1 keys load with "Moved to" warnings. `TomlDocument` edits TOML in
  place, keeping comments, and refuses any edit that would not land exactly; `ConfigService.save` and
  shortcut saves share one verified transaction. Migrate Config (palette and diagnostics button) rewrites
  old keys after backing the file up to `config.toml.v1.bak`. Plugin settings problems name the plugin's
  own file and line, survive app reloads and clear when that file reloads; the settings poller no longer
  reports from its own thread. Next: sub-project B (Settings dialog), then C (SDK settings API).
  Deviations: see the plan's status banner. Not pushed.
```

Update this plan's `**Status:**` line to `Implemented on claude/settings-foundation; see deviations below.` and list every deviation recorded during Tasks 1–11 under it (or `None.`).

- [ ] **Step 5: Run the full check and the hygiene scan**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Count results with:

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = e = s = 0
for p in list(pathlib.Path(".").glob("*/build/test-results/test/*.xml")) + list(pathlib.Path(".").glob("plugins/*/build/test-results/test/*.xml")):
    r = ET.parse(p).getroot()
    t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(f"tests={t} failures={f} errors={e} skipped={s}")
PY
```

Expected: `failures=0 errors=0`. Put the counts in the STATUS entry.

Run the AGENTS.md hygiene scan (the Python snippet under "Source hygiene") over `jasper-app/src`.
Expected: no output.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add docs README.md
git commit -m "Document version 2 settings, Migrate Config and the settings package

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Then stop: merging into `main` waits for the user.
