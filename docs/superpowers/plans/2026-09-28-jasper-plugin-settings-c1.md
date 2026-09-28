# Plugin Settings C1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on `claude/plugin-settings` (inline, checkpoints after Tasks 3 and 5); `./gradlew check` green:
3,450 tests, 0 failures, 0 errors, 5 skipped. Worktree `.worktrees/plugin-settings`, from local `main` at ef745526.
Record every deviation from this text here and in `docs/STATUS.md`.

Scope notes (decided while writing this plan):
- The custom-page interfaces (`SettingsPanel`, `SettingsPanelHost`) and `PluginSettings.page(...)` arrive in plan C2, with
  the pages that host them; C1 has no SDK interface without an implementation. `SettingSpec` already carries
  `section` and `editor` hints, which C2 renders.
- `JasperSdk.VERSION` stays `0.8.5` in C1 (the API only grows); `PluginConfig` stays until C3.
- Task 3 adds `PluginContext.settings()` with the testkit's implementation; the application's `HostedContext`
  throws `UnsupportedOperationException` from it until Task 5 replaces that line. No bundled plugin calls it in between.
- The spec's contract case "listeners run on the UI thread" is an application test (Task 5): the fake has no UI thread.

Deviations so far:
- Tasks 2 and 3: tests and code were written together; each test's failure was observed by moving the new
  implementation aside before running it.
- Task 5: a plugin that declares no settings is neither read nor reported by `DeclaredSettings.reload`. Without this,
  every key of a plugin still using `PluginConfig` counted as unknown (`PluginRuntimeTest` caught it). A new
  `DeclaredSettingsTest` case, `aPluginThatDeclaresNothingGetsNoReports`, covers it and was seen failing without the
  guard.

**Goal:** Plugins can declare their settings in code through SDK `dev.jasper.sdk.settings` and read, watch and save
them through typed handles, validated by the application and by the testkit fake alike.

**Architecture:** The schema-driven reader inside `ConfigLoader` moves into `dev.jasper.app.settings.SchemaReader`,
which both `config.toml` and plugin files use. The SDK gains plain declaration values (`SettingSpec`, `SettingKind`,
`Choice`, `EditorHint`, `Applies`) and two interfaces (`PluginSettings`, `Setting`). Inside `dev.jasper.app.plugins`,
`SettingTranslation` turns declarations into app `Setting`s, `DeclaredSettings` reads each plugin's file with its own
schema, reports problems through the existing configuration diagnostics, and saves through a comment-preserving,
atomic transaction on `PluginSettingsFiles`. The testkit's `FakePluginSettings` does the same over raw values, and the
contract suite holds both to the same behaviour.

**Tech Stack:** Java 25 (JetBrains Runtime), tomlj 1.1.1 (app only), JUnit 5, AssertJ, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-09-28-jasper-plugin-settings-design.md` (read sections 1, 2 and 4 and
"Delivery"). Sub-project A's spec, `docs/superpowers/specs/2026-09-27-jasper-settings-foundation-design.md`, describes
the schema, kinds, `TomlDocument` and `SettingWriter` this plan reuses.

## Global Constraints

- Build and test only with `./gradlew` from `/Users/dustin/projects/moray/.worktrees/plugin-settings`; never launch the GUI.
- Run `git branch --show-current` before every commit and confirm `claude/plugin-settings`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push; do not merge.
- `jasper-sdk` and `jasper-sdk-testkit` depend on the JDK only (`verifySdkArchitecture`); `dev.jasper.sdk.settings`
  depends only on `dev.jasper.sdk`. SDK public API needs complete Javadoc: the SDK's `javadoc` runs with
  `-Xdoclint:all` and fails the build (document every public type, method, record component and type parameter).
- SDK types appear in the application only inside `dev.jasper.app.plugins`. `dev.jasper.app.settings` stays free of
  Jasper dependencies (`verifyApplicationArchitecture`).
- Plugin code runs under `Containment` in the application: predicates, listeners and handlers never throw into the host.
- Source hygiene per AGENTS.md: no raw non-ASCII characters in Java source; write `"–"` for the en dash.
- `AppDocumentationTest` checks every Markdown link in `docs/`, plans included: add no Markdown links to this plan, and
  only checked links to docs.
- Messages, exact: unknown keys "Unknown setting or table; ignored."; a save that finds a different saved value
  "<key> changed on disk. Review the new value and try again."; a save into a broken file "<file name> has errors; fix
  them before saving settings."; declaring after start "Declare settings while the plugin starts: <key>".

## File Structure

New:
- `jasper-app/src/main/java/dev/jasper/app/settings/SchemaReader.java`: reads TOML text against any schema.
- `jasper-sdk/src/main/java/dev/jasper/sdk/settings/`: `package-info.java`, `Applies.java`, `Choice.java`,
  `EditorHint.java`, `SettingKind.java`, `SettingSpec.java`, `Setting.java`, `PluginSettings.java`.
- `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginSettings.java`, `FakeSetting.java`.
- `jasper-app/src/main/java/dev/jasper/app/plugins/SettingTranslation.java`, `DeclaredSettings.java`, `DeclaredSetting.java`.
- Tests: `jasper-app/src/test/java/dev/jasper/app/settings/SchemaReaderTest.java`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/settings/SettingKindTest.java`, `SettingSpecTest.java`,
  `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/DeclaredSettingsTest.java`.

Modified: `config/ConfigLoader.java`, `plugins/PluginSettingsFiles.java`, `plugins/PluginHost.java`,
`plugins/HostedContext.java`, `plugins/PluginRuntime.java`, `plugins/package-info.java`, SDK `plugin/PluginContext.java`,
testkit `FakePluginContext.java` and `FakePluginHost.java`, contract `ContractHarness.java` and `PluginContractTest.java`,
`AppContractTest.java`, `FakeContractTest.java`, `HostedPaletteTest.java`, `PluginSettingsFilesTest.java`, docs.

---

### Task 1: One schema reader for any settings file

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settings/SchemaReader.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigLoader.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settings/SchemaReaderTest.java` (new); the existing `config` tests guard `ConfigLoader`.

**Interfaces:**
- Produces: `public final class SchemaReader` with `public SchemaReader(SettingsSchema schema, String text)`,
  `public Result read(BiPredicate<List<String>, Object> special)`, `public static Result read(SettingsSchema schema, String text)`,
  `public void warning(List<String> path, String message)`, `public void error(List<String> path, String message)`,
  `public void typeError(List<String> path, String expected)`; records
  `Problem(Reading.Severity severity, String key, int line, int column, String message)` and
  `Result(SettingValues values, List<Problem> problems, boolean rejected, boolean syntaxError, List<LegacyKey> legacyKeys)`.

- [ ] **Step 1: Write the failing test**

Create `SchemaReaderTest.java`:

```java
package dev.jasper.app.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchemaReaderTest {
    private static final Setting<Integer> SIZE = Setting.of("size", SettingKind.integer(8, 48, SettingKind.OutOfRange.DEFAULT), 13)
        .label("Size").build();
    private static final Setting<Boolean> WRAP = Setting.of("view.wrap", SettingKind.bool(), false).label("Wrap").alias("wrap").build();
    private static final SettingsSchema SCHEMA = SettingsSchema.of(List.of(SIZE, WRAP), Set.of(List.of("own")), Map.of());

    @Test void readsValuesAndPlacesEveryProblem() {
        var result = SchemaReader.read(SCHEMA, "size = 99\nmystery = 1\n[view]\nwrap = true\n");
        assertThat(result.values().get(SIZE)).isEqualTo(13);
        assertThat(result.values().get(WRAP)).isTrue();
        assertThat(result.rejected()).isFalse();
        assertThat(result.syntaxError()).isFalse();
        assertThat(result.problems()).containsExactly(
            new SchemaReader.Problem(Reading.Severity.ERROR, "size", 1, 1, "Use an integer from 8–48; using the default."),
            new SchemaReader.Problem(Reading.Severity.WARNING, "mystery", 2, 1, "Unknown setting or table; ignored."));
    }

    @Test void aWrongTypeRejectsTheFileAndBadSyntaxReadsNothing() {
        var wrong = SchemaReader.read(SCHEMA, "size = \"big\"\n");
        assertThat(wrong.rejected()).isTrue();
        assertThat(wrong.syntaxError()).isFalse();
        assertThat(wrong.problems()).extracting(SchemaReader.Problem::message)
            .containsExactly("Expected an integer; configuration was not applied.");
        var broken = SchemaReader.read(SCHEMA, "size = \n");
        assertThat(broken.syntaxError()).isTrue();
        assertThat(broken.rejected()).isTrue();
        assertThat(broken.values().present(SIZE)).isFalse();
        assertThat(broken.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEmpty();
            assertThat(problem.message()).isEqualTo("Invalid TOML syntax or duplicate definition; check this location.");
        });
    }

    @Test void oldKeysMoveAndTheCallerReadsItsOwnPaths() {
        var special = new ArrayList<String>();
        var reader = new SchemaReader(SCHEMA, "wrap = true\nversion = 3\n[own]\nanything = 1\n");
        var result = reader.read((path, value) -> {
            if (path.equals(List.of("version"))) { special.add("version=" + value); reader.warning(path, "Newer file."); return true; }
            if (path.equals(List.of("own"))) { special.add("own"); return true; }
            return false;
        });
        assertThat(special).containsExactlyInAnyOrder("version=3", "own");
        assertThat(result.values().get(WRAP)).isTrue();
        assertThat(result.legacyKeys()).containsExactly(new LegacyKey(List.of("wrap"), List.of("view", "wrap")));
        assertThat(result.problems()).extracting(SchemaReader.Problem::message)
            .containsExactly("Moved to view.wrap. Use Migrate Config to update the file.", "Newer file.");
    }

    @Test void aCurrentKeyWinsOverItsOldKey() {
        var result = SchemaReader.read(SCHEMA, "wrap = false\n[view]\nwrap = true\n");
        assertThat(result.values().get(WRAP)).isTrue();
        assertThat(result.problems()).extracting(SchemaReader.Problem::message).containsExactly("Superseded by view.wrap; ignored.");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settings.SchemaReaderTest'`
Expected: compilation fails: `SchemaReader` does not exist.

- [ ] **Step 3: Implement**

Create `SchemaReader.java`:

```java
package dev.jasper.app.settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

/**
 * Reads TOML text against a {@link SettingsSchema}: each setting's value through its kind, old keys, unknown keys and
 * type errors, with the position of every problem. A caller reads the paths the schema does not describe (a version
 * key, tables other readers own) through the {@code special} hook, reporting through this reader. One read per
 * instance; the caller chooses the thread.
 */
public final class SchemaReader {
    /**
     * One problem.
     *
     * @param severity how serious it is
     * @param key the dotted key where it was found; empty for the whole file
     * @param line 1-based line, 0 when unknown
     * @param column 1-based column, 0 when unknown
     * @param message plain text for the user
     */
    public record Problem(Reading.Severity severity, String key, int line, int column, String message) { }

    /**
     * What the text held.
     *
     * @param values each setting's value; all defaults after a syntax error
     * @param problems every problem, in file order
     * @param rejected whether a syntax or type error means the values must not be applied
     * @param syntaxError whether the text is not valid TOML
     * @param legacyKeys old keys found, in file order, with the keys they moved to
     */
    public record Result(SettingValues values, List<Problem> problems, boolean rejected, boolean syntaxError, List<LegacyKey> legacyKeys) {
        public Result {
            Objects.requireNonNull(values, "values");
            problems = List.copyOf(problems);
            legacyKeys = List.copyOf(legacyKeys);
        }
    }

    private record AliasHit(Setting<?> setting, List<String> path, Object value) { }

    private final SettingsSchema schema;
    private final TomlParseResult toml;
    private final SettingValues.Builder values;
    private final List<Problem> problems = new ArrayList<>();
    private final List<AliasHit> aliasHits = new ArrayList<>();
    private boolean rejected;

    public SchemaReader(SettingsSchema schema, String text) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.toml = Toml.parse(Objects.requireNonNull(text, "text"));
        this.values = SettingValues.builder(schema);
    }

    /** Reads text that holds nothing beyond the schema's settings. */
    public static Result read(SettingsSchema schema, String text) {
        return new SchemaReader(schema, text).read((path, value) -> false);
    }

    /** Reads the text. {@code special} sees every path first and returns true for a path it read itself. */
    public Result read(BiPredicate<List<String>, Object> special) {
        if (toml.hasErrors()) {
            toml.errors().forEach(error -> problems.add(new Problem(Reading.Severity.ERROR, "", error.position().line(),
                error.position().column(), "Invalid TOML syntax or duplicate definition; check this location.")));
            return new Result(SettingValues.defaults(schema), problems, true, true, List.of());
        }
        readTable(List.of(), toml, special);
        List<LegacyKey> legacyKeys = resolveAliases();
        problems.sort(Comparator.comparingInt(Problem::line).thenComparingInt(Problem::column));
        return new Result(values.build(), problems, rejected, false, legacyKeys);
    }

    private void readTable(List<String> parent, TomlTable table, BiPredicate<List<String>, Object> special) {
        for (String key : table.keySet()) {
            List<String> path = append(parent, key);
            Object value = table.get(List.of(key));
            if (special.test(path, value)) continue;
            var current = schema.find(path);
            if (current.isPresent()) { read(current.get(), path, value); continue; }
            var aliased = schema.aliasOf(path);
            if (aliased.isPresent()) { aliasHits.add(new AliasHit(aliased.get(), path, value)); continue; }
            if (!schema.reserved(path) && !schema.knownTable(path)) { warning(path, "Unknown setting or table; ignored."); continue; }
            if (!(value instanceof TomlTable nested)) typeError(path, "a table");
            else readTable(path, nested, special);
        }
    }

    private <T> void read(Setting<T> setting, List<String> path, Object value) {
        Reading<T> reading = setting.kind().read(value, setting.defaultValue());
        for (Reading.Issue issue : reading.issues()) {
            if (issue.rejectsFile()) rejected = true;
            var at = new ArrayList<>(path);
            at.addAll(issue.subPath());
            problem(issue.severity(), at, issue.message());
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

    /** A warning at {@code path}. */
    public void warning(List<String> path, String message) { problem(Reading.Severity.WARNING, path, message); }

    /** An error at {@code path} that does not reject the file. */
    public void error(List<String> path, String message) { problem(Reading.Severity.ERROR, path, message); }

    /** A value of the wrong TOML type: an error that rejects the file. */
    public void typeError(List<String> path, String expected) {
        rejected = true;
        error(path, "Expected " + expected + "; configuration was not applied.");
    }

    private void problem(Reading.Severity severity, List<String> path, String message) {
        problems.add(new Problem(severity, Toml.joinKeyPath(path), line(path), column(path), message));
    }

    private int line(List<String> path) {
        TomlPosition position = toml.inputPositionOf(path);
        return position == null ? 0 : position.line();
    }

    private int column(List<String> path) {
        TomlPosition position = toml.inputPositionOf(path);
        return position == null ? 0 : position.column();
    }

    private static List<String> append(List<String> parent, String key) {
        var path = new ArrayList<>(parent);
        path.add(key);
        return List.copyOf(path);
    }
}
```

Now make `ConfigLoader` use it. In `ConfigLoader.java`:

1. Replace the fields from `private record AliasHit(...)` through `private Map<String, Map<String, Object>> plugins = Map.of();` with:

```java
    private final Path file;
    private final SchemaReader reader;
    private final boolean macOs;
    private final List<List<String>> deadTables = new ArrayList<>();
    private Map<String, String> keybindings = Map.of();
    private Map<String, Map<String, Object>> plugins = Map.of();
```

2. Replace the constructor and `parse()` (the instance method), `readTable`, `read` and `resolveAliases` with:

```java
    private ConfigLoader(Path file, String text, boolean macOs) {
        this.file = Objects.requireNonNull(file, "file");
        this.reader = new SchemaReader(CoreSettings.SCHEMA, text);
        this.macOs = macOs;
    }

    public static Result parse(Path file, String text, boolean macOs) {
        return new ConfigLoader(file, text, macOs).parse();
    }

    private Result parse() {
        SchemaReader.Result read = reader.read(this::special);
        List<ConfigDiagnostic> diagnostics = read.problems().stream().map(this::diagnostic).toList();
        if (read.syntaxError())
            return new Result(ConfigSnapshot.defaults(), diagnostics, true, read.values(), List.of(), List.of());
        return new Result(ConfigSnapshot.from(read.values(), keybindings, plugins), diagnostics, read.rejected(), read.values(),
            read.legacyKeys(), deadTables);
    }

    /** The version key and the tables other readers own. */
    private boolean special(List<String> path, Object value) {
        if (path.equals(VERSION_KEY)) { readVersion(path, value); return true; }
        if (!path.equals(KEYBINDINGS) && !path.equals(PLUGINS) && !path.equals(PALETTE_SCOPES)) return false;
        if (!(value instanceof TomlTable table)) { typeError(path, "a table"); return true; }
        if (path.equals(KEYBINDINGS)) readBindings(table);
        else if (path.equals(PLUGINS)) readPlugins(table);
        else readOldScopes(table);
        return true;
    }

    private ConfigDiagnostic diagnostic(SchemaReader.Problem problem) {
        return new ConfigDiagnostic(problem.severity() == Reading.Severity.WARNING ? ConfigDiagnostic.Severity.WARNING
            : ConfigDiagnostic.Severity.ERROR, file, problem.line(), problem.column(), problem.key(), problem.message());
    }
```

3. Replace the helpers at the end of the class, from `private int line(List<String> path) {` through the end of
   `diagnostic(ConfigDiagnostic.Severity severity, List<String> path, String message)`, with:

```java
    private void typeError(List<String> path, String expected) { reader.typeError(path, expected); }

    private void warning(List<String> path, String message) { reader.warning(path, message); }

    private void valueError(List<String> path, String message) { reader.error(path, message); }
```

4. Remove imports that no longer have a use (`org.tomlj.Toml`, `org.tomlj.TomlParseResult`, `org.tomlj.TomlPosition`,
   `dev.jasper.app.settings.Setting`, `dev.jasper.app.settings.SettingsSchema`, `dev.jasper.app.settings.LegacyKey`,
   `java.util.Comparator` only if `grep -n` finds none left) and add `import dev.jasper.app.settings.SchemaReader;`.
   `append`, `freeze`, `readVersion`, `readOldScopes`, `readPlugins`, `readBindings` and `knownAction` stay as they are;
   they now report through the three one-line helpers above.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settings.*' --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.workspace.ExpandedConfigTest' --tests 'dev.jasper.app.workspace.ConfigurationControllerTest'`
Expected: PASS (the `config` and `ExpandedConfig` tests check every existing message and position unchanged).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings
git add jasper-app/src/main/java/dev/jasper/app/settings/SchemaReader.java jasper-app/src/main/java/dev/jasper/app/config/ConfigLoader.java \
    jasper-app/src/test/java/dev/jasper/app/settings/SchemaReaderTest.java
git commit -m "Move the schema reader out of ConfigLoader so any settings file can use it

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The SDK settings declarations

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/settings/package-info.java`, `Applies.java`, `Choice.java`,
  `EditorHint.java`, `SettingKind.java`, `SettingSpec.java`, `Setting.java`, `PluginSettings.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/settings/SettingKindTest.java`, `SettingSpecTest.java` (new)

**Interfaces:**
- Produces: `SettingKind<T>` (sealed) with `Optional<String> check(T value)` and factories `bool()`, `integer(int, int)`,
  `number(double, double)`, `text()`, `text(Predicate<String>, String)`, `choice(List<Choice<T>>)`, `stringList()`,
  `stringList(Predicate<String>)`; records `Bool`, `IntegerRange(int min, int max)`, `NumberRange(double min, double max)`,
  `Text(Predicate<String> valid, String invalidMessage)`, `OneOf<T>(List<Choice<T>> choices)`,
  `StringList(Predicate<String> validElement)`.
- Produces: `record Choice<T>(String token, String displayName, T value)` with `Choice.of(...)`;
  `sealed interface EditorHint` with records `Font(boolean monospaced)`, `Directory()`, `File()` and factories
  `font(boolean)`, `directory()`, `file()`; `enum Applies { LIVE, RESTART }`.
- Produces: `final class SettingSpec<T>` with `of(String key, SettingKind<T> kind, T defaultValue)`, chained
  `label`, `description`, `keywords(String...)`, `section`, `applies`, `editor`, and getters `key()`, `kind()`,
  `defaultValue()`, `label()` (empty until set), `description()`, `keywords()`, `section()`, `applies()`,
  `editor()` (`Optional<EditorHint>`).
- Produces: `interface Setting<T>` (`key()`, `get()`, `CompletableFuture<Void> set(T)`, `Subscription onChanged(Consumer<T>)`)
  and `interface PluginSettings` (`<T> Setting<T> declare(SettingSpec<T>)`, `Path file()`, `Subscription onChanged(Runnable)`).

- [ ] **Step 1: Write the failing tests**

Create `SettingKindTest.java`:

```java
package dev.jasper.sdk.settings;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SettingKindTest {
    @Test void eachKindChecksItsValues() {
        assertThat(SettingKind.bool().check(true)).isEmpty();
        assertThat(SettingKind.integer(8, 48).check(8)).isEmpty();
        assertThat(SettingKind.integer(8, 48).check(49)).contains("Use a whole number from 8–48.");
        assertThat(SettingKind.number(0.5, 3).check(0.5)).isEmpty();
        assertThat(SettingKind.number(0.5, 3).check(Double.NaN)).contains("Use a number from 0.5–3.");
        assertThat(SettingKind.text().check("anything")).isEmpty();
        assertThat(SettingKind.text(text -> text.startsWith("/"), "Use an absolute path.").check("relative"))
            .contains("Use an absolute path.");
        var mode = SettingKind.choice(List.of(Choice.of("fast", "Fast", 1), Choice.of("safe", "Safe", 2)));
        assertThat(mode.check(2)).isEmpty();
        assertThat(mode.check(3)).contains("Use one of: fast, safe.");
        var words = SettingKind.stringList(word -> !word.contains(" "));
        assertThat(words.check(List.of("ls", "cd"))).isEmpty();
        assertThat(words.check(List.of("ls", "git log"))).contains("Not an allowed list element: \"git log\".");
        assertThat(SettingKind.stringList().check(List.of())).isEmpty();
    }

    @Test void impossibleKindsAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> SettingKind.integer(5, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingKind.number(2, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingKind.number(0, Double.POSITIVE_INFINITY));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingKind.choice(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingKind.choice(List.of(Choice.of("a", "A", 1), Choice.of("a", "B", 2))));
        assertThatIllegalArgumentException().isThrownBy(() -> Choice.of(" ", "Blank", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> Choice.of("a", "", 1));
        assertThatNullPointerException().isThrownBy(() -> SettingKind.text(null, "message"));
    }
}
```

Create `SettingSpecTest.java`:

```java
package dev.jasper.sdk.settings;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SettingSpecTest {
    @Test void aDeclarationCarriesEverythingSettingsShows() {
        var spec = SettingSpec.of("sftp.max_parallel_files", SettingKind.integer(1, 8), 2).label("Parallel files")
            .description("Files transferred at once.").keywords("sftp", "transfer").section("SFTP")
            .applies(Applies.RESTART).editor(EditorHint.directory());
        assertThat(spec.key()).isEqualTo("sftp.max_parallel_files");
        assertThat(spec.defaultValue()).isEqualTo(2);
        assertThat(spec.label()).isEqualTo("Parallel files");
        assertThat(spec.description()).isEqualTo("Files transferred at once.");
        assertThat(spec.keywords()).containsExactly("sftp", "transfer");
        assertThat(spec.section()).isEqualTo("SFTP");
        assertThat(spec.applies()).isEqualTo(Applies.RESTART);
        assertThat(spec.editor()).contains(EditorHint.directory());
        var plain = SettingSpec.of("vim", SettingKind.bool(), false);
        assertThat(plain.label()).isEmpty();
        assertThat(plain.description()).isEmpty();
        assertThat(plain.keywords()).isEmpty();
        assertThat(plain.section()).isEmpty();
        assertThat(plain.applies()).isEqualTo(Applies.LIVE);
        assertThat(plain.editor()).isEmpty();
        assertThat(EditorHint.font(true)).isEqualTo(new EditorHint.Font(true));
    }

    @Test void badDeclarationsAreRejected() {
        for (String key : new String[]{"", ".a", "a.", "a..b", "has space", "a.b c"})
            assertThatIllegalArgumentException().as(key).isThrownBy(() -> SettingSpec.of(key, SettingKind.bool(), false));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingSpec.of(null, SettingKind.bool(), false));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingSpec.of("size", SettingKind.integer(1, 8), 9))
            .withMessageContaining("Use a whole number from 1–8.");
        assertThatIllegalArgumentException().isThrownBy(() -> SettingSpec.of("vim", SettingKind.bool(), false).label(" "));
        assertThatNullPointerException().isThrownBy(() -> SettingSpec.of("vim", SettingKind.bool(), null));
        assertThatNullPointerException().isThrownBy(() -> SettingSpec.of("vim", null, false));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.settings.*'`
Expected: compilation fails: package `dev.jasper.sdk.settings` does not exist.

- [ ] **Step 3: Implement**

Create `package-info.java`:

```java
/**
 * Plugin settings: declarations of a plugin's settings, the typed handles that read, watch and save them, and the
 * kinds that check their values. The runtime keeps each plugin's settings in its own TOML file, reports values a kind
 * rejects and keys nothing declares, and shows the settings in Settings. This package depends only on
 * {@code dev.jasper.sdk}.
 */
package dev.jasper.sdk.settings;
```

Create `Applies.java`:

```java
package dev.jasper.sdk.settings;

/** When a saved change to a setting takes effect. */
public enum Applies {
    /** At once: the plugin reads the new value when it changes. */
    LIVE,
    /** When the plugin next starts. */
    RESTART
}
```

Create `Choice.java`:

```java
package dev.jasper.sdk.settings;

import java.util.Objects;

/**
 * One option of a {@link SettingKind#choice} setting.
 *
 * @param token the text the settings file holds; non-blank
 * @param displayName what Settings shows; non-blank
 * @param value the value the plugin reads
 * @param <T> the value type
 */
public record Choice<T>(String token, String displayName, T value) {
    /** Rejects blank text and a null value. */
    public Choice {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("A choice needs a token");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("A choice needs a display name");
        Objects.requireNonNull(value, "value");
    }

    /**
     * A choice.
     *
     * @param token the text the settings file holds
     * @param displayName what Settings shows
     * @param value the value the plugin reads
     * @param <T> the value type
     * @return the choice
     */
    public static <T> Choice<T> of(String token, String displayName, T value) { return new Choice<>(token, displayName, value); }
}
```

Create `EditorHint.java`:

```java
package dev.jasper.sdk.settings;

/** Which control Settings uses for a setting, where the kind alone does not say. */
public sealed interface EditorHint {
    /**
     * A font family picker.
     *
     * @param monospaced whether only monospaced families are offered
     */
    record Font(boolean monospaced) implements EditorHint { }

    /** A folder chooser beside a text field. */
    record Directory() implements EditorHint { }

    /** A file chooser beside a text field. */
    record File() implements EditorHint { }

    /**
     * A font family picker.
     *
     * @param monospaced whether only monospaced families are offered
     * @return the hint
     */
    static EditorHint font(boolean monospaced) { return new Font(monospaced); }

    /**
     * A folder chooser.
     *
     * @return the hint
     */
    static EditorHint directory() { return new Directory(); }

    /**
     * A file chooser.
     *
     * @return the hint
     */
    static EditorHint file() { return new File(); }
}
```

Create `SettingKind.java`:

```java
package dev.jasper.sdk.settings;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * How a setting's values are checked. The runtime reads a value from the plugin's settings file; a value its kind
 * rejects is replaced by the setting's default and reported.
 *
 * @param <T> the value type
 */
public sealed interface SettingKind<T> {
    /**
     * Checks a value.
     *
     * @param value a value of this kind's type
     * @return empty when the value is allowed, otherwise a message for the user
     */
    Optional<String> check(T value);

    /**
     * True or false.
     *
     * @return the kind
     */
    static SettingKind<Boolean> bool() { return new Bool(); }

    /**
     * A whole number in a range.
     *
     * @param min the lowest allowed value
     * @param max the highest allowed value
     * @return the kind
     */
    static SettingKind<Integer> integer(int min, int max) { return new IntegerRange(min, max); }

    /**
     * A finite number in a range.
     *
     * @param min the lowest allowed value
     * @param max the highest allowed value
     * @return the kind
     */
    static SettingKind<Double> number(double min, double max) { return new NumberRange(min, max); }

    /**
     * Any text.
     *
     * @return the kind
     */
    static SettingKind<String> text() { return new Text(text -> true, "Not an allowed value."); }

    /**
     * Text that a rule accepts. The rule is plugin code; the runtime treats a rule that throws as rejecting the value.
     *
     * @param valid the rule
     * @param invalidMessage the message for a value the rule rejects
     * @return the kind
     */
    static SettingKind<String> text(Predicate<String> valid, String invalidMessage) { return new Text(valid, invalidMessage); }

    /**
     * One of a fixed set of options.
     *
     * @param choices the options, in the order a picker lists them
     * @param <T> the value type
     * @return the kind
     */
    static <T> SettingKind<T> choice(List<Choice<T>> choices) { return new OneOf<>(choices); }

    /**
     * A list of strings.
     *
     * @return the kind
     */
    static SettingKind<List<String>> stringList() { return new StringList(element -> true); }

    /**
     * A list of strings that each pass a rule.
     *
     * @param validElement the rule for each element
     * @return the kind
     */
    static SettingKind<List<String>> stringList(Predicate<String> validElement) { return new StringList(validElement); }

    /** True or false. */
    record Bool() implements SettingKind<Boolean> {
        @Override public Optional<String> check(Boolean value) { return Optional.empty(); }
    }

    /**
     * A whole number in a range.
     *
     * @param min the lowest allowed value
     * @param max the highest allowed value
     */
    record IntegerRange(int min, int max) implements SettingKind<Integer> {
        /** Rejects an empty range. */
        public IntegerRange {
            if (min > max) throw new IllegalArgumentException("min exceeds max");
        }
        @Override public Optional<String> check(Integer value) {
            return value >= min && value <= max ? Optional.empty()
                : Optional.of("Use a whole number from " + min + "–" + max + ".");
        }
    }

    /**
     * A finite number in a range.
     *
     * @param min the lowest allowed value
     * @param max the highest allowed value
     */
    record NumberRange(double min, double max) implements SettingKind<Double> {
        /** Rejects an empty or infinite range. */
        public NumberRange {
            if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) throw new IllegalArgumentException("Not a finite range");
        }
        @Override public Optional<String> check(Double value) {
            return Double.isFinite(value) && value >= min && value <= max ? Optional.empty()
                : Optional.of("Use a number from " + bound(min) + "–" + bound(max) + ".");
        }
        private static String bound(double value) {
            return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
        }
    }

    /**
     * Text that a rule accepts.
     *
     * @param valid the rule
     * @param invalidMessage the message for a value the rule rejects
     */
    record Text(Predicate<String> valid, String invalidMessage) implements SettingKind<String> {
        /** Rejects nulls. */
        public Text {
            Objects.requireNonNull(valid, "valid");
            Objects.requireNonNull(invalidMessage, "invalidMessage");
        }
        @Override public Optional<String> check(String value) {
            return valid.test(value) ? Optional.empty() : Optional.of(invalidMessage);
        }
    }

    /**
     * One of a fixed set of options.
     *
     * @param choices the options, with unique tokens
     * @param <T> the value type
     */
    record OneOf<T>(List<Choice<T>> choices) implements SettingKind<T> {
        /** Copies the options and rejects an empty list or a repeated token. */
        public OneOf {
            choices = List.copyOf(choices);
            if (choices.isEmpty()) throw new IllegalArgumentException("A choice needs options");
            var tokens = new HashSet<String>();
            for (Choice<T> choice : choices)
                if (!tokens.add(choice.token())) throw new IllegalArgumentException("Repeated token: " + choice.token());
        }
        @Override public Optional<String> check(T value) {
            for (Choice<T> choice : choices) if (choice.value().equals(value)) return Optional.empty();
            return Optional.of("Use one of: " + choices.stream().map(Choice::token).collect(Collectors.joining(", ")) + ".");
        }
    }

    /**
     * A list of strings that each pass a rule.
     *
     * @param validElement the rule for each element
     */
    record StringList(Predicate<String> validElement) implements SettingKind<List<String>> {
        /** Rejects a null rule. */
        public StringList {
            Objects.requireNonNull(validElement, "validElement");
        }
        @Override public Optional<String> check(List<String> value) {
            for (String element : value)
                if (!validElement.test(element)) return Optional.of("Not an allowed list element: \"" + element + "\".");
            return Optional.empty();
        }
    }
}
```

Create `SettingSpec.java`:

```java
package dev.jasper.sdk.settings;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The declaration of one plugin setting: its key in the settings file, its kind and default, and how Settings shows
 * it. Start one with {@link #of}, give it at least a {@link #label}, and pass it to {@link PluginSettings#declare}, which
 * reads it once; later changes to the declaration are ignored.
 *
 * @param <T> the value type
 */
public final class SettingSpec<T> {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");

    private final String key;
    private final SettingKind<T> kind;
    private final T defaultValue;
    private String label = "";
    private String description = "";
    private List<String> keywords = List.of();
    private String section = "";
    private Applies applies = Applies.LIVE;
    private EditorHint editor;

    private SettingSpec(String key, SettingKind<T> kind, T defaultValue) {
        this.key = key; this.kind = kind; this.defaultValue = defaultValue;
    }

    /**
     * Starts a declaration.
     *
     * @param key the dotted key in the settings file, such as {@code sftp.max_parallel_files}; each part is letters,
     *            digits, {@code _} and {@code -}, and each dot but the last names a table
     * @param kind how values are checked
     * @param defaultValue the value when the file does not set one; the kind must allow it
     * @param <T> the value type
     * @return the declaration
     */
    public static <T> SettingSpec<T> of(String key, SettingKind<T> kind, T defaultValue) {
        if (key == null || !KEY.matcher(key).matches()) throw new IllegalArgumentException("Not a setting key: " + key);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Optional<String> problem = kind.check(defaultValue);
        if (problem.isPresent()) throw new IllegalArgumentException("The default of " + key + " is not allowed: " + problem.get());
        return new SettingSpec<>(key, kind, defaultValue);
    }

    /**
     * Sets the name Settings shows.
     *
     * @param value non-blank text
     * @return this declaration
     */
    public SettingSpec<T> label(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A setting needs a label: " + key);
        label = value;
        return this;
    }

    /**
     * Sets the explanation Settings shows under the control.
     *
     * @param value plain text
     * @return this declaration
     */
    public SettingSpec<T> description(String value) { description = Objects.requireNonNull(value, "value"); return this; }

    /**
     * Sets extra words Settings' search matches.
     *
     * @param values the words
     * @return this declaration
     */
    public SettingSpec<T> keywords(String... values) { keywords = List.of(values); return this; }

    /**
     * Sets the heading this setting appears under on the plugin's page.
     *
     * @param value the heading; empty for none
     * @return this declaration
     */
    public SettingSpec<T> section(String value) { section = Objects.requireNonNull(value, "value"); return this; }

    /**
     * Sets when a saved change takes effect.
     *
     * @param value when it applies
     * @return this declaration
     */
    public SettingSpec<T> applies(Applies value) { applies = Objects.requireNonNull(value, "value"); return this; }

    /**
     * Sets the control Settings uses.
     *
     * @param value the hint
     * @return this declaration
     */
    public SettingSpec<T> editor(EditorHint value) { editor = Objects.requireNonNull(value, "value"); return this; }

    /**
     * The dotted key.
     *
     * @return the key
     */
    public String key() { return key; }

    /**
     * How values are checked.
     *
     * @return the kind
     */
    public SettingKind<T> kind() { return kind; }

    /**
     * The value when the file does not set one.
     *
     * @return the default
     */
    public T defaultValue() { return defaultValue; }

    /**
     * The name Settings shows.
     *
     * @return the label; empty until {@link #label(String)} sets it
     */
    public String label() { return label; }

    /**
     * The explanation Settings shows.
     *
     * @return the description; empty by default
     */
    public String description() { return description; }

    /**
     * Extra search words.
     *
     * @return the keywords
     */
    public List<String> keywords() { return keywords; }

    /**
     * The heading on the plugin's page.
     *
     * @return the section; empty for none
     */
    public String section() { return section; }

    /**
     * When a saved change takes effect.
     *
     * @return {@link Applies#LIVE} unless set
     */
    public Applies applies() { return applies; }

    /**
     * The control Settings uses, when the kind alone does not decide it.
     *
     * @return the hint, if any
     */
    public Optional<EditorHint> editor() { return Optional.ofNullable(editor); }
}
```

Create `Setting.java`:

```java
package dev.jasper.sdk.settings;

import dev.jasper.sdk.Subscription;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A declared setting of the running plugin: its value in force and a way to save a new one.
 *
 * @param <T> the value type
 */
public interface Setting<T> {
    /**
     * The dotted key.
     *
     * @return the key
     */
    String key();

    /**
     * The value in force: the file's value when the kind allows it, otherwise the default. Safe from any thread.
     *
     * @return the value
     */
    T get();

    /**
     * Saves a new value to the plugin's settings file, keeping the file's comments. The future completes after this
     * setting's handle shows the new value. It fails with {@link IllegalArgumentException} when the kind rejects the
     * value, and otherwise when the file changed on disk, has errors or cannot be written. Safe from any thread.
     *
     * @param value the new value
     * @return the save's completion
     */
    CompletableFuture<Void> set(T value);

    /**
     * Runs the handler on the UI thread whenever this setting's value changes, whether the file changed or
     * {@link #set} saved it.
     *
     * @param handler receives the new value
     * @return the registration; the runtime closes it when the plugin stops
     */
    Subscription onChanged(Consumer<T> handler);
}
```

Create `PluginSettings.java`:

```java
package dev.jasper.sdk.settings;

import dev.jasper.sdk.Subscription;
import java.nio.file.Path;

/**
 * A plugin's settings, kept in {@code plugins/<id>/<id>.toml} under Jasper's home. A plugin declares every setting
 * while it starts; the runtime then reads their values, reports values their kinds reject and keys nothing declares,
 * and shows them in Settings.
 */
public interface PluginSettings {
    /**
     * Declares a setting. UI thread, while the plugin starts.
     *
     * @param spec the declaration, with a label
     * @param <T> the value type
     * @return the setting's handle, already holding the file's value
     * @throws IllegalStateException after the plugin's start has returned
     * @throws IllegalArgumentException for a declaration without a label, a key declared twice, or a key inside
     *         another declared key
     */
    <T> Setting<T> declare(SettingSpec<T> spec);

    /**
     * The settings file.
     *
     * @return {@code plugins/<id>/<id>.toml} under Jasper's home
     */
    Path file();

    /**
     * Runs the handler on the UI thread after any declared value changed.
     *
     * @param handler the handler
     * @return the registration; the runtime closes it when the plugin stops
     */
    Subscription onChanged(Runnable handler);
}
```

- [ ] **Step 4: Run the tests and the SDK's Javadoc check**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.settings.*' :jasper-sdk:javadoc verifySdkArchitecture`
Expected: PASS, with no doclint errors.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings
git add jasper-sdk/src
git commit -m "Add SDK settings declarations: kinds, specs, handles and PluginSettings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: `PluginContext.settings()` and the testkit fake

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java`
- Create: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginSettings.java`, `FakeSetting.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java`, `FakePluginHost.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java` (temporary line, replaced in Task 5)
- Test: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java` (new)

**Interfaces:**
- Consumes: Task 2's SDK types.
- Produces: `PluginSettings PluginContext.settings()`; on `FakePluginHost`: `setSetting(String pluginId, String key, Object rawValue)`,
  `Map<String, Object> savedSettings(String pluginId)`, `List<String> settingsReports(String pluginId)` (lines
  `key: message`), `List<String> declared(String pluginId)`. Raw values are what TOML reads: `String`, `Long`, `Double`,
  `Boolean` or `List<String>`.

- [ ] **Step 1: Write the failing test**

Create `FakePluginSettingsTest.java`:

```java
package dev.jasper.sdk.testing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FakePluginSettingsTest {
    private final FakePluginHost host = new FakePluginHost();
    private static final PluginInfo INFO = new PluginInfo("test.fake", "Fake", "1.0.0", Set.of());

    @AfterEach void close() { host.close(); }

    @Test void declaredValuesComeFromTheFileWithReportsForBadAndUnknownKeys() {
        host.setSetting("test.fake", "font_size", 99L);
        host.setSetting("test.fake", "ratio", 2L);
        host.setSetting("test.fake", "mode", "safe");
        host.setSetting("test.fake", "words", List.of("ls", "cd"));
        host.setSetting("test.fake", "mystery", 1L);
        var size = new AtomicReference<Setting<Integer>>();
        var ratio = new AtomicReference<Setting<Double>>();
        var mode = new AtomicReference<Setting<Integer>>();
        var words = new AtomicReference<Setting<List<String>>>();
        host.start(INFO, Set.of(), Set.of(), context -> {
            size.set(context.settings().declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            ratio.set(context.settings().declare(SettingSpec.of("ratio", SettingKind.number(0.5, 3), 1.0).label("Ratio")));
            mode.set(context.settings().declare(SettingSpec.of("mode",
                SettingKind.choice(List.of(Choice.of("fast", "Fast", 1), Choice.of("safe", "Safe", 2))), 1).label("Mode")));
            words.set(context.settings().declare(SettingSpec.of("words", SettingKind.stringList(), List.of()).label("Words")));
        });
        assertThat(size.get().get()).isEqualTo(13);
        assertThat(ratio.get().get()).isEqualTo(2.0);
        assertThat(mode.get().get()).isEqualTo(2);
        assertThat(words.get().get()).containsExactly("ls", "cd");
        assertThat(host.settingsReports("test.fake")).containsExactlyInAnyOrder(
            "font_size: Use a whole number from 8–48.", "mystery: Unknown setting or table; ignored.");
        assertThat(host.declared("test.fake")).containsExactly("font_size", "ratio", "mode", "words");
    }

    @Test void setSavesTheRawValueAndListenersHearIt() throws Exception {
        var vim = new AtomicReference<Setting<Boolean>>();
        var seen = new ArrayList<Boolean>();
        int[] any = {0};
        host.start(INFO, Set.of(), Set.of(), context -> {
            vim.set(context.settings().declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode")));
            vim.get().onChanged(seen::add);
            context.settings().onChanged(() -> any[0]++);
        });
        vim.get().set(true).get();
        assertThat(vim.get().get()).isTrue();
        assertThat(seen).containsExactly(true);
        assertThat(any[0]).isEqualTo(1);
        assertThat(host.savedSettings("test.fake")).containsEntry("vim", true);
        host.setSetting("test.fake", "vim", "yes");
        assertThat(vim.get().get()).as("a wrong type keeps the last good value").isTrue();
        assertThat(host.settingsReports("test.fake")).singleElement().asString().startsWith("vim: Expected a boolean");
    }

    @Test void invalidSavesAndLateDeclarationsAreRefused() {
        var settings = new AtomicReference<PluginSettings>();
        var size = new AtomicReference<Setting<Integer>>();
        host.start(INFO, Set.of(), Set.of(), context -> {
            settings.set(context.settings());
            size.set(context.settings().declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            assertThatIllegalArgumentException().isThrownBy(() ->
                context.settings().declare(SettingSpec.of("font_size", SettingKind.bool(), true).label("Again")));
            assertThatIllegalArgumentException().isThrownBy(() ->
                context.settings().declare(SettingSpec.of("font_size.inner", SettingKind.bool(), true).label("Inside")));
            assertThatIllegalArgumentException().isThrownBy(() ->
                context.settings().declare(SettingSpec.of("unlabelled", SettingKind.bool(), true)));
        });
        assertThat(host.active("test.fake")).isTrue();
        assertThatThrownBy(() -> size.get().set(99).get()).isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThatIllegalStateException().isThrownBy(() ->
            settings.get().declare(SettingSpec.of("late", SettingKind.bool(), false).label("Late")))
            .withMessage("Declare settings while the plugin starts: late");
        assertThat(settings.get().file()).isEqualTo(host.dataRoot().resolve("test.fake").resolve("test.fake.toml"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakePluginSettingsTest'`
Expected: compilation fails: `PluginContext.settings()` and `FakePluginHost.setSetting` do not exist.

- [ ] **Step 3: Implement**

In `PluginContext.java`, after `PluginConfig config();` and its Javadoc, add (and `import dev.jasper.sdk.settings.PluginSettings;`):

```java

    /**
     * The plugin's declared settings.
     *
     * @return this plugin's settings
     */
    PluginSettings settings();
```

Create `FakePluginSettings.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingSpec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** One fake plugin's declared settings over the host's raw values, keyed by dotted key. Caller's thread, like the rest of the fake. */
final class FakePluginSettings implements PluginSettings {
    static final String UNKNOWN = "Unknown setting or table; ignored.";

    private final FakePluginHost host;
    private final String pluginId;
    private final Path file;
    private final List<FakeSetting<?>> declared = new ArrayList<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private boolean open = true;

    FakePluginSettings(FakePluginHost host, String pluginId, Path file) {
        this.host = host; this.pluginId = pluginId; this.file = file;
    }

    @Override public <T> Setting<T> declare(SettingSpec<T> spec) {
        Objects.requireNonNull(spec, "spec");
        if (!open) throw new IllegalStateException("Declare settings while the plugin starts: " + spec.key());
        if (spec.label().isBlank()) throw new IllegalArgumentException("A setting needs a label: " + spec.key());
        for (FakeSetting<?> other : declared) {
            if (other.key().equals(spec.key())) throw new IllegalArgumentException("Duplicate setting key: " + spec.key());
            if (other.key().startsWith(spec.key() + ".") || spec.key().startsWith(other.key() + "."))
                throw new IllegalArgumentException(spec.key() + " overlaps the setting " + other.key());
        }
        var setting = new FakeSetting<>(this, spec);
        declared.add(setting);
        setting.load(raw().get(spec.key()), false, new ArrayList<>());
        return setting;
    }

    @Override public Path file() { return file; }

    @Override public Subscription onChanged(Runnable handler) {
        listeners.add(Objects.requireNonNull(handler, "handler"));
        return () -> listeners.remove(handler);
    }

    Map<String, Object> raw() { return host.settingFile(pluginId); }

    List<String> keys() { return declared.stream().map(FakeSetting::key).toList(); }

    /** The plugin's start returned: declarations close, and the file's problems are reported. */
    void complete() {
        open = false;
        reload();
    }

    /** Re-reads every value, replacing this plugin's reports; listeners of changed values run. */
    void reload() {
        var reports = new ArrayList<String>();
        boolean changed = false;
        for (FakeSetting<?> setting : declared) changed |= setting.load(raw().get(setting.key()), true, reports);
        for (String key : List.copyOf(raw().keySet()))
            if (declared.stream().noneMatch(setting -> setting.key().equals(key))) reports.add(key + ": " + UNKNOWN);
        host.settingsReports.put(pluginId, List.copyOf(reports));
        if (changed) for (Runnable listener : listeners) listener.run();
    }

    void close() {
        open = false;
        listeners.clear();
        declared.forEach(FakeSetting::close);
    }
}
```

Create `FakeSetting.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** One declared setting of a fake plugin. */
final class FakeSetting<T> implements Setting<T> {
    private static final Object WRONG_TYPE = new Object();

    private final FakePluginSettings owner;
    private final SettingSpec<T> spec;
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

    FakeSetting(FakePluginSettings owner, SettingSpec<T> spec) {
        this.owner = owner; this.spec = spec; this.value = spec.defaultValue();
    }

    @Override public String key() { return spec.key(); }

    @Override public T get() { return value; }

    @Override public CompletableFuture<Void> set(T next) {
        Objects.requireNonNull(next, "value");
        Optional<String> problem = spec.kind().check(next);
        if (problem.isPresent()) return CompletableFuture.failedFuture(new IllegalArgumentException(problem.get()));
        owner.raw().put(spec.key(), toRaw(next));
        owner.reload();
        return CompletableFuture.completedFuture(null);
    }

    @Override public Subscription onChanged(Consumer<T> handler) {
        listeners.add(Objects.requireNonNull(handler, "handler"));
        return () -> listeners.remove(handler);
    }

    void close() { listeners.clear(); }

    /** Reads a raw value (absent: the default); returns whether the value changed. Listeners run when {@code notify}. */
    boolean load(Object raw, boolean notify, List<String> reports) {
        T next;
        if (raw == null) next = spec.defaultValue();
        else {
            Object converted = convert(raw);
            if (converted == WRONG_TYPE) {
                reports.add(key() + ": Expected " + expected() + "; the last good value stays in force.");
                return false;
            }
            @SuppressWarnings("unchecked") T candidate = (T) converted;
            Optional<String> problem = candidate == null ? Optional.of(unknownToken()) : spec.kind().check(candidate);
            if (problem.isPresent()) { reports.add(key() + ": " + problem.get()); next = spec.defaultValue(); }
            else next = candidate;
        }
        boolean changed = !next.equals(value);
        value = next;
        if (changed && notify) for (Consumer<T> listener : listeners) listener.accept(next);
        return changed;
    }

    /** The value a raw TOML value stands for; null for an unknown choice token; {@link #WRONG_TYPE} for a wrong type. */
    private Object convert(Object raw) {
        return switch (spec.kind()) {
            case SettingKind.Bool bool -> raw instanceof Boolean ? raw : WRONG_TYPE;
            case SettingKind.IntegerRange range -> raw instanceof Long number
                ? (Object) Math.clamp(number, Integer.MIN_VALUE, Integer.MAX_VALUE) : WRONG_TYPE;
            case SettingKind.NumberRange range -> raw instanceof Long || raw instanceof Double
                ? (Object) ((Number) raw).doubleValue() : WRONG_TYPE;
            case SettingKind.Text text -> raw instanceof String ? raw : WRONG_TYPE;
            case SettingKind.OneOf<?> oneOf -> raw instanceof String token
                ? oneOf.choices().stream().filter(choice -> choice.token().equals(token)).findFirst().<Object>map(Choice::value).orElse(null)
                : WRONG_TYPE;
            case SettingKind.StringList list -> raw instanceof List<?> items && items.stream().allMatch(String.class::isInstance)
                ? List.copyOf(items) : WRONG_TYPE;
        };
    }

    private Object toRaw(T next) {
        return switch (spec.kind()) {
            case SettingKind.IntegerRange range -> ((Integer) next).longValue();
            case SettingKind.OneOf<?> oneOf -> oneOf.choices().stream().filter(choice -> choice.value().equals(next))
                .findFirst().orElseThrow().token();
            default -> next;
        };
    }

    private String expected() {
        return switch (spec.kind()) {
            case SettingKind.Bool bool -> "a boolean";
            case SettingKind.IntegerRange range -> "an integer";
            case SettingKind.NumberRange range -> "a number";
            case SettingKind.Text text -> "a string";
            case SettingKind.OneOf<?> oneOf -> "a string";
            case SettingKind.StringList list -> "an array of strings";
        };
    }

    private String unknownToken() {
        var oneOf = (SettingKind.OneOf<?>) spec.kind();
        return "Use one of: " + oneOf.choices().stream().map(Choice::token).collect(Collectors.joining(", ")) + ".";
    }
}
```

In `FakePluginHost.java`:
- Add the fields next to `reports`:

```java
    final Map<String, Map<String, Object>> settingFiles = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<String, List<String>> settingsReports = new java.util.concurrent.ConcurrentHashMap<>();
```

- Add, after `setConfig(...)`:

```java
    Map<String, Object> settingFile(String pluginId) {
        return settingFiles.computeIfAbsent(pluginId, id -> new java.util.LinkedHashMap<>());
    }

    /**
     * Sets a raw value in a plugin's settings file, as a user editing the file would. Before the plugin starts this
     * is part of the file it starts with; afterwards its declared settings reload and their listeners run.
     *
     * @param pluginId the plugin
     * @param key the dotted key
     * @param rawValue a String, Long, Double, Boolean or List of String, as TOML reads them
     */
    public void setSetting(String pluginId, String key, Object rawValue) {
        settingFile(pluginId).put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(rawValue, "rawValue"));
        FakePluginContext context = contexts.get(pluginId);
        if (context != null && context.state == FakePluginContext.State.ACTIVE) context.settings.reload();
    }

    /**
     * The raw values in a plugin's settings file, including what the plugin saved.
     *
     * @param pluginId the plugin
     * @return the values by dotted key
     */
    public Map<String, Object> savedSettings(String pluginId) { return Map.copyOf(settingFile(pluginId)); }

    /**
     * The problems the last read of a plugin's settings found.
     *
     * @param pluginId the plugin
     * @return lines of the form {@code key: message}
     */
    public List<String> settingsReports(String pluginId) { return settingsReports.getOrDefault(pluginId, List.of()); }

    /**
     * The keys a plugin declared.
     *
     * @param pluginId the plugin
     * @return the keys in declaration order
     */
    public List<String> declared(String pluginId) {
        FakePluginContext context = contexts.get(pluginId);
        return context == null ? List.of() : context.settings.keys();
    }
```

- In `start(...)`, replace `context.state = FakePluginContext.State.ACTIVE;` with:

```java
            context.state = FakePluginContext.State.ACTIVE;
            context.settings.complete();
```

- In `teardown(FakePluginContext context, String reason)`, after `context.closeOwned();` add `context.settings.close();`.

In `FakePluginContext.java`: add the field `final FakePluginSettings settings;`, at the end of the constructor
`this.settings = new FakePluginSettings(host, info.id(), host.dataRoot().resolve(info.id()).resolve(info.id() + ".toml"));`,
and after `config()`:

```java
    @Override public dev.jasper.sdk.settings.PluginSettings settings() { return settings; }
```

In `HostedContext.java` (application), after `@Override public PluginConfig config() { return settings; }`, add the
temporary line Task 5 replaces:

```java
    @Override public dev.jasper.sdk.settings.PluginSettings settings() {
        throw new UnsupportedOperationException("Declared settings arrive in plan C1 Task 5");
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-sdk-testkit:javadoc :jasper-sdk:javadoc :jasper-app:compileJava verifySdkArchitecture`
Expected: PASS (the testkit's own contract suite, `FakeContractTest`, still passes).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings
git add jasper-sdk/src jasper-sdk-testkit/src jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java
git commit -m "Give plugins PluginContext.settings(), with the testkit's fake

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Plugin settings files keep their text and can be saved

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettingsFiles.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsFilesTest.java`

**Interfaces:**
- Produces (package-private): `Path file(String id)`, `String text(String id)` (the last good text; empty when the file
  is absent) and `void save(String id, UnaryOperator<String> edit) throws IOException` (re-reads, edits, refuses a
  concurrent change, writes atomically and re-reads the file into the cache).

- [ ] **Step 1: Write the failing tests**

Add to `PluginSettingsFilesTest.java` (its `@TempDir` field is `root`; add the imports `java.nio.file.Files` and
`java.nio.file.attribute.FileTime` if missing):

```java
    @Test void theLastGoodTextIsKeptAndSavesEditItAtomically() throws Exception {
        var files = new PluginSettingsFiles(root, diagnostic -> { });
        Path file = files.file("dev.test");
        assertThat(files.text("dev.test")).as("no file yet").isEmpty();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "# Settings\nsize = 13\n");
        files.refresh(true);
        assertThat(files.text("dev.test")).isEqualTo("# Settings\nsize = 13\n");
        Files.writeString(file, "size = \n");
        files.refresh(true);
        assertThat(files.text("dev.test")).as("a broken file keeps the last good text").isEqualTo("# Settings\nsize = 13\n");
        Files.writeString(file, "# Settings\nsize = 13\n");
        files.save("dev.test", text -> text.replace("13", "15"));
        assertThat(Files.readString(file)).isEqualTo("# Settings\nsize = 15\n");
        assertThat(files.text("dev.test")).isEqualTo("# Settings\nsize = 15\n");
        assertThat(files.current("dev.test")).containsEntry("size", 15L);
        try (var listing = Files.list(file.getParent())) {
            assertThat(listing.map(path -> path.getFileName().toString())).containsExactly("dev.test.toml");
        }
    }

    @Test void aSaveCreatesAMissingFileAndLeavesAnUnchangedFileAlone() throws Exception {
        var files = new PluginSettingsFiles(root, diagnostic -> { });
        files.save("dev.new", text -> text + "vim = true\n");
        assertThat(Files.readString(files.file("dev.new"))).isEqualTo("vim = true\n");
        var before = FileTime.fromMillis(1_000_000);
        Files.setLastModifiedTime(files.file("dev.new"), before);
        files.save("dev.new", text -> text);
        assertThat(Files.getLastModifiedTime(files.file("dev.new"))).isEqualTo(before);
        assertThatThrownBy(() -> files.save("dev.new", text -> "x".repeat(1024 * 1024 + 1)))
            .isInstanceOf(java.io.IOException.class).hasMessageContaining("1 MiB");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginSettingsFilesTest'`
Expected: compilation fails: `file(String)`, `text` and `save` do not exist.

- [ ] **Step 3: Implement**

In `PluginSettingsFiles.java`:
- Change `private static final class Known { Fingerprint fingerprint; Map<String, Object> values = Map.of(); TomlParseResult parsed; boolean broken; }`
  to `private static final class Known { Fingerprint fingerprint; Map<String, Object> values = Map.of(); String text = ""; TomlParseResult parsed; boolean broken; }`.
- In `read(...)`, replace `TomlParseResult parsed = Toml.parse(new String(bytes, StandardCharsets.UTF_8));` with:

```java
            String text = new String(bytes, StandardCharsets.UTF_8);
            TomlParseResult parsed = Toml.parse(text);
```

  and after `state.parsed = parsed;` add `state.text = text;`. In the `NoSuchFileException` branch, after
  `state.parsed = null;` add `state.text = "";`.
- Add, after `static Path data(...)`:

```java
    /** This store's settings file for {@code id}. */
    Path file(String id) { return file(userDirectory, id); }

    /** The file's last good text for {@code id}, empty when it is absent; reads the file on first use. Safe from any thread. */
    synchronized String text(String id) {
        current(id);
        return known.get(id).text;
    }

    /**
     * Edits the plugin's file: reads it (empty when absent), applies {@code edit}, and writes the result atomically
     * unless nothing changed, then re-reads it here. Refuses a file that grew past 1 MiB or changed during the edit.
     * Off the UI thread; {@code edit} may throw to refuse the change.
     */
    synchronized void save(String id, java.util.function.UnaryOperator<String> edit) throws IOException {
        Path file = file(userDirectory, id);
        var original = dev.jasper.app.persistence.TomlStateFile.readBounded(file, MAX_BYTES, "Plugin settings");
        String updated = edit.apply(original.orElse(""));
        if (original.isPresent() && updated.equals(original.get())) return;
        if (updated.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Plugin settings exceed 1 MiB.");
        if (!dev.jasper.app.persistence.TomlStateFile.readBounded(file, MAX_BYTES, "Plugin settings").equals(original))
            throw new IOException("Settings changed while saving. Try again.");
        dev.jasper.app.persistence.TomlStateFile.writeAtomically(file, ".settings-", updated);
        Known state = known.computeIfAbsent(id, key -> new Known());
        read(id, state, true);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginSettingsFilesTest' --tests 'dev.jasper.app.plugins.PluginSettingsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings
git add jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettingsFiles.java jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsFilesTest.java
git commit -m "Keep each plugin settings file's text and save edits to it atomically

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Declared settings in the application

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/SettingTranslation.java`, `DeclaredSettings.java`, `DeclaredSetting.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java`, `HostedContext.java`, `PluginRuntime.java`, `package-info.java`
- Modify (tests): `AppContractTest.java`, `HostedPaletteTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/DeclaredSettingsTest.java` (new)

**Interfaces:**
- Consumes: `SchemaReader` (Task 1), SDK settings types (Task 2), `PluginSettingsFiles.file/text/save` (Task 4).
- Produces: `PluginHost.Environment` gains two last components, `PluginSettingsFiles settingsFiles` and
  `Consumer<Path> clearReports`; `PluginHost.settingsWritten(String id)`; `HostedContext.settings()` returns the
  plugin's `DeclaredSettings`; test helper `AppContractTest.host(Path, Map, Duration, Contributions, AtomicBoolean,
  AuxiliaryWindows, TerminalRegistry, List<String>, List<Path>, List<ConfigDiagnostic>)`.

- [ ] **Step 1: Write the failing test**

First give the test harness a real settings store. In `AppContractTest.java`, replace the method

```java
    static PluginHost host(Path data, Map<String, Map<String, Object>> tables, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> notices, List<Path> edited) {
        return onEdtValue(() -> new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, data::resolve, id -> tables.getOrDefault(id, Map.of()), id -> data.resolve(id + ".toml"),
            (id, path) -> java.util.Optional.empty(), diagnostic -> { }, drainGrace, contributions, dark::get, auxiliary, terminals, notices::add, edited::add)));
    }
```

with:

```java
    static PluginHost host(Path data, Map<String, Map<String, Object>> tables, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> notices, List<Path> edited) {
        return host(data, tables, drainGrace, contributions, dark, auxiliary, terminals, notices, edited, new CopyOnWriteArrayList<>());
    }

    /** As above; configuration problems land in {@code diagnostics}, and a file's reload or save clears that file's. */
    static PluginHost host(Path data, Map<String, Map<String, Object>> tables, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> notices, List<Path> edited,
                           List<dev.jasper.app.config.ConfigDiagnostic> diagnostics) {
        var files = new PluginSettingsFiles(data, diagnostics::add);
        return onEdtValue(() -> new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, data::resolve, id -> tables.getOrDefault(id, Map.of()), id -> data.resolve(id + ".toml"),
            (id, path) -> java.util.Optional.empty(), diagnostics::add, drainGrace, contributions, dark::get, auxiliary, terminals,
            notices::add, edited::add, files, file -> diagnostics.removeIf(diagnostic -> diagnostic.file().equals(file)))));
    }
```

In `HostedPaletteTest.java`, in the `new PluginHost.Environment(...)` call, replace the final `notices::add, edited::add)));`
with `notices::add, edited::add, new PluginSettingsFiles(data, diagnostic -> { }), file -> { })));`.

Create `DeclaredSettingsTest.java`:

```java
package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.settings.TomlDocument;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeclaredSettingsTest {
    private static final String ID = "test.declared";
    @TempDir Path data;
    private final List<ConfigDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
    private PluginHost host;

    @BeforeEach void open() {
        host = AppContractTest.host(data, Map.of(), Duration.ofMillis(200), onEdtValue(Contributions::new), new AtomicBoolean(true),
            onEdtValue(AppContractTest::headlessWindows), onEdtValue(TerminalRegistry::new), new CopyOnWriteArrayList<>(),
            new CopyOnWriteArrayList<>(), diagnostics);
    }

    @AfterEach void stop() {
        var pending = new AtomicReference<List<java.util.concurrent.CompletableFuture<?>>>(List.of());
        onEdt(() -> pending.set(host.stop()));
        java.util.concurrent.CompletableFuture.allOf(pending.get().toArray(java.util.concurrent.CompletableFuture[]::new)).join();
    }

    private Path file() { return data.resolve(ID).resolve(ID + ".toml"); }

    private void write(String text) throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), text);
    }

    /** Starts the plugin; {@code declare} runs inside its start. */
    private PluginSettings start(Consumer<PluginSettings> declare) {
        var context = new AtomicReference<PluginContext>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID, "Declared", "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            DeclaredSettingsTest.class.getClassLoader(), () -> started -> { context.set(started); declare.accept(started.settings()); })));
        assertThat(host.active(ID)).as("started").isTrue();
        return context.get().settings();
    }

    private void reload() {
        onEdt(() -> {
            var changed = host.environment.settingsFiles().refresh(true);
            for (String id : changed) host.environment.clearReports().accept(host.environment.settingsFiles().file(id));
            host.settingsChanged(changed);
        });
    }

    private List<String> reports() {
        return diagnostics.stream().filter(diagnostic -> diagnostic.file().equals(file()))
            .map(diagnostic -> diagnostic.severity() + " " + diagnostic.key() + " " + diagnostic.line() + " " + diagnostic.message()).toList();
    }

    @Test void valuesComeFromTheFileAndProblemsArePlaced() throws Exception {
        write("font_size = 99\ntab_size = 6\nmystery = 1\n[sftp]\nmax_parallel_files = 3\n");
        var size = new AtomicReference<Setting<Integer>>();
        var tab = new AtomicReference<Setting<Integer>>();
        var parallel = new AtomicReference<Setting<Integer>>();
        start(settings -> {
            size.set(settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            tab.set(settings.declare(SettingSpec.of("tab_size", SettingKind.integer(1, 16), 4).label("Tab size")));
            parallel.set(settings.declare(SettingSpec.of("sftp.max_parallel_files", SettingKind.integer(1, 8), 2).label("Parallel files")));
        });
        assertThat(size.get().get()).isEqualTo(13);
        assertThat(tab.get().get()).isEqualTo(6);
        assertThat(parallel.get().get()).isEqualTo(3);
        assertThat(reports()).containsExactly(
            "ERROR font_size 1 Use an integer from 8–48; using the default.",
            "WARNING mystery 3 Unknown setting or table; ignored.");
    }

    @Test void everyKindTranslates() throws Exception {
        write("wrap = true\nratio = 1.25\nshell = \"/bin/zsh\"\nmode = \"safe\"\nwords = [\"ls\", \"cd\"]\n");
        var wrap = new AtomicReference<Setting<Boolean>>();
        var ratio = new AtomicReference<Setting<Double>>();
        var shell = new AtomicReference<Setting<String>>();
        var mode = new AtomicReference<Setting<Integer>>();
        var words = new AtomicReference<Setting<List<String>>>();
        start(settings -> {
            wrap.set(settings.declare(SettingSpec.of("wrap", SettingKind.bool(), false).label("Wrap")));
            ratio.set(settings.declare(SettingSpec.of("ratio", SettingKind.number(0.5, 3), 1.0).label("Ratio")));
            shell.set(settings.declare(SettingSpec.of("shell", SettingKind.text(text -> text.startsWith("/"), "Use an absolute path."), "/bin/sh").label("Shell")));
            mode.set(settings.declare(SettingSpec.of("mode",
                SettingKind.choice(List.of(Choice.of("fast", "Fast", 1), Choice.of("safe", "Safe", 2))), 1).label("Mode")));
            words.set(settings.declare(SettingSpec.of("words", SettingKind.stringList(), List.of()).label("Words")));
        });
        assertThat(wrap.get().get()).isTrue();
        assertThat(ratio.get().get()).isEqualTo(1.25);
        assertThat(shell.get().get()).isEqualTo("/bin/zsh");
        assertThat(mode.get().get()).isEqualTo(2);
        assertThat(words.get().get()).containsExactly("ls", "cd");
        assertThat(reports()).isEmpty();
    }

    @Test void aThrowingRuleCountsAsRejectingTheValue() throws Exception {
        write("name = \"x\"\n");
        var name = new AtomicReference<Setting<String>>();
        start(settings -> name.set(settings.declare(SettingSpec.of("name",
            SettingKind.text(text -> { if (text.equals("x")) throw new IllegalStateException("boom"); return true; }, "Bad name."), "ok").label("Name"))));
        assertThat(name.get().get()).isEqualTo("ok");
        assertThat(reports()).containsExactly("ERROR name 1 Bad name.");
        assertThat(host.failures(ID)).isPositive();
    }

    @Test void declarationsCloseWhenStartReturnsAndKeysAreUnique() {
        var settings = start(declared -> {
            declared.declare(SettingSpec.of("a", SettingKind.bool(), false).label("A"));
            assertThatIllegalArgumentException().isThrownBy(() -> declared.declare(SettingSpec.of("a", SettingKind.bool(), true).label("Again")));
            assertThatIllegalArgumentException().isThrownBy(() -> declared.declare(SettingSpec.of("a.b", SettingKind.bool(), true).label("Inside")));
            assertThatIllegalArgumentException().isThrownBy(() -> declared.declare(SettingSpec.of("b", SettingKind.bool(), true)));
        });
        onEdt(() -> assertThatIllegalStateException()
            .isThrownBy(() -> settings.declare(SettingSpec.of("late", SettingKind.bool(), false).label("Late")))
            .withMessage("Declare settings while the plugin starts: late"));
        assertThat(settings.file()).isEqualTo(file());
    }

    @Test void fileChangesReachListenersOnTheUiThreadAndAWrongTypeKeepsTheLastGoodValue() throws Exception {
        write("vim = false\n");
        var vim = new AtomicReference<Setting<Boolean>>();
        var seen = new CopyOnWriteArrayList<String>();
        int[] any = {0};
        start(settings -> {
            vim.set(settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode")));
            vim.get().onChanged(value -> seen.add(value + " " + SwingUtilities.isEventDispatchThread()));
            settings.onChanged(() -> any[0]++);
        });
        write("vim = true\n");
        reload();
        assertThat(vim.get().get()).isTrue();
        assertThat(seen).containsExactly("true true");
        assertThat(any[0]).isEqualTo(1);
        write("vim = true\n# a comment\n");
        reload();
        assertThat(seen).as("an unchanged value is quiet").hasSize(1);
        write("vim = \"yes\"\n");
        reload();
        assertThat(vim.get().get()).isTrue();
        assertThat(reports()).containsExactly("ERROR vim 1 Expected a boolean; configuration was not applied.");
    }

    @Test void setWritesTheFileKeepingItsCommentsAndTheHandleHearsIt() throws Exception {
        write("# Editor settings\nfont_size = 13\n");
        var size = new AtomicReference<Setting<Integer>>();
        var parallel = new AtomicReference<Setting<Integer>>();
        var seen = new CopyOnWriteArrayList<Integer>();
        start(settings -> {
            size.set(settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            parallel.set(settings.declare(SettingSpec.of("sftp.max_parallel_files", SettingKind.integer(1, 8), 2).label("Parallel files")));
            size.get().onChanged(seen::add);
        });
        size.get().set(15).get(5, TimeUnit.SECONDS);
        parallel.get().set(4).get(5, TimeUnit.SECONDS);
        onEdt(() -> { });
        String text = Files.readString(file());
        assertThat(text).startsWith("# Editor settings\n");
        assertThat(TomlDocument.leaves(text)).containsEntry(List.of("font_size"), 15L)
            .containsEntry(List.of("sftp", "max_parallel_files"), 4L);
        assertThat(size.get().get()).isEqualTo(15);
        assertThat(parallel.get().get()).isEqualTo(4);
        assertThat(seen).containsExactly(15);
    }

    @Test void setRefusesAnInvalidValueAChangeOnDiskAndABrokenFile() throws Exception {
        write("font_size = 99\n");
        var size = new AtomicReference<Setting<Integer>>();
        start(settings -> size.set(settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size"))));
        assertThat(reports()).hasSize(1);
        assertThatThrownBy(() -> size.get().set(99).get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .cause().isInstanceOf(IllegalArgumentException.class).hasMessage("Use a whole number from 8–48.");
        size.get().set(14).get(5, TimeUnit.SECONDS);
        onEdt(() -> { });
        assertThat(reports()).as("a save clears the file's old reports").isEmpty();
        write("font_size = 20\n");
        assertThatThrownBy(() -> size.get().set(15).get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .cause().hasMessage("font_size changed on disk. Review the new value and try again.");
        write("font_size = \"big\"\n");
        reload();
        assertThatThrownBy(() -> size.get().set(16).get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .cause().hasMessage(ID + ".toml has errors; fix them before saving settings.");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.DeclaredSettingsTest'`
Expected: compilation fails: the `Environment` has no `settingsFiles` or `clearReports` component.

- [ ] **Step 3: Implement**

Create `SettingTranslation.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.settings.Applies;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.SettingSpec;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** SDK setting declarations in the application's terms. SDK types stop here. */
final class SettingTranslation {
    private SettingTranslation() { }

    /**
     * One declaration in application terms.
     *
     * @param spec the plugin's declaration
     * @param setting the application's setting
     * @param toSdk converts an application value to the plugin's (identity except for numbers)
     * @param toApp converts a plugin value to the application's
     */
    record Translated(SettingSpec<?> spec, Setting<?> setting, Function<Object, Object> toSdk, Function<Object, Object> toApp) { }

    /** Translates {@code spec}; {@code guard} wraps each plugin-supplied rule so that it runs contained. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Translated translate(SettingSpec<?> spec, UnaryOperator<Predicate<String>> guard) {
        Function<Object, Object> same = value -> value;
        Function<Object, Object> toSdk = same;
        Function<Object, Object> toApp = same;
        Object defaultValue = spec.defaultValue();
        SettingKind kind = switch (spec.kind()) {
            case dev.jasper.sdk.settings.SettingKind.Bool bool -> SettingKind.bool();
            case dev.jasper.sdk.settings.SettingKind.IntegerRange range ->
                SettingKind.integer(range.min(), range.max(), SettingKind.OutOfRange.DEFAULT);
            case dev.jasper.sdk.settings.SettingKind.NumberRange range -> {
                toSdk = value -> ((Float) value).doubleValue();
                toApp = value -> ((Double) value).floatValue();
                defaultValue = ((Double) spec.defaultValue()).floatValue();
                yield SettingKind.number(range.min(), range.max());
            }
            case dev.jasper.sdk.settings.SettingKind.Text text -> SettingKind.string(guard.apply(text.valid()), text.invalidMessage());
            case dev.jasper.sdk.settings.SettingKind.OneOf oneOf -> SettingKind.choice(((List<Choice<?>>) oneOf.choices()).stream()
                .map(choice -> Map.entry(choice.token(), (Object) choice.value())).toList());
            case dev.jasper.sdk.settings.SettingKind.StringList list -> SettingKind.stringList(guard.apply(list.validElement()));
        };
        Setting<?> setting = Setting.of(spec.key(), kind, defaultValue).label(spec.label()).description(spec.description())
            .keywords(spec.keywords().toArray(String[]::new))
            .applies(spec.applies() == dev.jasper.sdk.settings.Applies.RESTART ? Applies.RESTART : Applies.LIVE).build();
        return new Translated(spec, setting, toSdk, toApp);
    }
}
```

Create `DeclaredSetting.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.settings.SettingValues;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingSpec;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** One declared setting's handle. {@link #get} is safe from any thread; listeners run on the UI thread, contained. */
final class DeclaredSetting<T> implements Setting<T> {
    private final DeclaredSettings owner;
    private final SettingTranslation.Translated translated;
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

    DeclaredSetting(DeclaredSettings owner, SettingTranslation.Translated translated) {
        this.owner = owner; this.translated = translated;
    }

    dev.jasper.app.settings.Setting<?> appSetting() { return translated.setting(); }

    @SuppressWarnings("unchecked")
    SettingSpec<T> spec() { return (SettingSpec<T>) translated.spec(); }

    Object toApp(T value) { return translated.toApp().apply(value); }

    @SuppressWarnings("unchecked")
    T value(SettingValues values) { return (T) translated.toSdk().apply(values.get(translated.setting())); }

    @Override public String key() { return translated.spec().key(); }

    @Override public T get() { return value(owner.values()); }

    @Override public CompletableFuture<Void> set(T value) { return owner.save(this, value); }

    @Override public Subscription onChanged(Consumer<T> handler) {
        listeners.add(Objects.requireNonNull(handler, "handler"));
        return () -> listeners.remove(handler);
    }

    /** Whether this setting's value differs between two reads of the same schema. */
    boolean changed(SettingValues before, SettingValues after) {
        return !before.get(translated.setting()).equals(after.get(translated.setting()));
    }

    void fire(SettingValues values, Containment containment, String pluginId) {
        T value = value(values);
        for (Consumer<T> listener : listeners) containment.run(pluginId, "setting change", () -> listener.accept(value));
    }

    void close() { listeners.clear(); }
}
```

Create `DeclaredSettings.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.settings.Reading;
import dev.jasper.app.settings.SchemaReader;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingValues;
import dev.jasper.app.settings.SettingWriter;
import dev.jasper.app.settings.SettingsSchema;
import dev.jasper.app.settings.TomlDocument;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.SettingSpec;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One plugin's declared settings: its schema, the values its file holds and its handles. Declarations and reloads run
 * on the UI thread; values are readable from any thread; saves write off the UI thread.
 */
final class DeclaredSettings implements PluginSettings {
    private final String pluginId;
    private final PluginSettingsFiles files;
    private final Containment containment;
    private final Consumer<Runnable> ui;
    private final Consumer<ConfigDiagnostic> report;
    private final Runnable written;
    private final List<DeclaredSetting<?>> handles = new ArrayList<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile SettingsSchema schema = SettingsSchema.of(List.of(), Set.of(), Map.of());
    private volatile SettingValues values = SettingValues.defaults(schema);
    private volatile boolean open = true;
    private volatile boolean closed;

    // written: runs on the UI thread after a save; the host clears the file's reports and reloads it.
    DeclaredSettings(String pluginId, PluginSettingsFiles files, Containment containment, Consumer<Runnable> ui,
                     Consumer<ConfigDiagnostic> report, Runnable written) {
        this.pluginId = pluginId; this.files = files; this.containment = containment; this.ui = ui;
        this.report = report; this.written = written;
    }

    SettingValues values() { return values; }

    @Override public <T> dev.jasper.sdk.settings.Setting<T> declare(SettingSpec<T> spec) {
        Objects.requireNonNull(spec, "spec");
        if (!open) throw new IllegalStateException("Declare settings while the plugin starts: " + spec.key());
        if (spec.label().isBlank()) throw new IllegalArgumentException("A setting needs a label: " + spec.key());
        var translated = SettingTranslation.translate(spec, this::guard);
        var settings = new ArrayList<Setting<?>>();
        for (DeclaredSetting<?> handle : handles) settings.add(handle.appSetting());
        settings.add(translated.setting());
        SettingsSchema next = SettingsSchema.of(settings, Set.of(), Map.of());
        var handle = new DeclaredSetting<T>(this, translated);
        handles.add(handle);
        schema = next;
        values = SchemaReader.read(next, files.text(pluginId)).values();
        return handle;
    }

    @Override public Path file() { return files.file(pluginId); }

    @Override public Subscription onChanged(Runnable handler) {
        listeners.add(Objects.requireNonNull(handler, "handler"));
        return () -> listeners.remove(handler);
    }

    /** The plugin's start returned: declarations close, and the file's problems are reported. UI thread. */
    void complete() {
        open = false;
        reload(true);
    }

    /**
     * Reads the file again; {@code report}: the file was reloaded and its earlier reports are gone. A rejected file
     * keeps the last good values. Listeners of changed values run. UI thread.
     */
    void reload(boolean report) {
        if (closed || open) return;
        var result = SchemaReader.read(schema, files.text(pluginId));
        if (report) for (SchemaReader.Problem problem : result.problems()) this.report.accept(diagnostic(problem));
        if (result.rejected()) return;
        SettingValues before = values;
        values = result.values();
        boolean any = false;
        for (DeclaredSetting<?> handle : handles) {
            if (!handle.changed(before, values)) continue;
            any = true;
            handle.fire(values, containment, pluginId);
        }
        if (any) for (Runnable listener : listeners) containment.run(pluginId, "settings change", listener);
    }

    void close() {
        closed = true;
        open = false;
        listeners.clear();
        handles.forEach(DeclaredSetting::close);
    }

    /** Checks and writes one value off the UI thread; the future completes after the handles show it. */
    <T> CompletableFuture<Void> save(DeclaredSetting<T> handle, T value) {
        Objects.requireNonNull(value, "value");
        Optional<String> problem = handle.spec().kind().check(value);
        if (problem.isPresent()) return CompletableFuture.failedFuture(new IllegalArgumentException(problem.get()));
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("The plugin has stopped."));
        @SuppressWarnings("unchecked") Setting<Object> setting = (Setting<Object>) handle.appSetting();
        Object appValue = handle.toApp(value);
        Optional<Object> expected = values.saved(setting);
        SettingsSchema current = schema;
        var done = new CompletableFuture<Void>();
        Thread.ofVirtual().name("jasper-plugin-settings-save").start(() -> {
            try {
                files.save(pluginId, text -> edit(current, text, setting, appValue, expected));
                ui.accept(() -> { written.run(); done.complete(null); });
            } catch (IOException | RuntimeException failure) {
                done.completeExceptionally(failure);
            }
        });
        return done;
    }

    private String edit(SettingsSchema current, String text, Setting<Object> setting, Object value, Optional<Object> expected) {
        String name = file().getFileName().toString();
        var read = SchemaReader.read(current, text);
        if (read.rejected()) throw new IllegalStateException(name + " has errors; fix them before saving settings.");
        if (!read.values().saved(setting).equals(expected))
            throw new IllegalStateException(setting.name() + " changed on disk. Review the new value and try again.");
        String updated = SettingWriter.write(TomlDocument.parse(text), setting, Optional.of(value)).text();
        var checked = SchemaReader.read(current, updated);
        if (checked.rejected() || !checked.values().get(setting).equals(value))
            throw new IllegalStateException("Cannot safely save " + setting.name() + "; review " + name + ".");
        return updated;
    }

    /** A plugin-supplied rule that runs contained; a throw rejects the value. */
    private Predicate<String> guard(Predicate<String> rule) {
        return text -> {
            boolean[] accepted = {false};
            Throwable failure = containment.attempt(pluginId, "setting check", () -> { accepted[0] = rule.test(text); return null; });
            return failure == null && accepted[0];
        };
    }

    private ConfigDiagnostic diagnostic(SchemaReader.Problem problem) {
        return new ConfigDiagnostic(problem.severity() == Reading.Severity.WARNING ? ConfigDiagnostic.Severity.WARNING
            : ConfigDiagnostic.Severity.ERROR, file(), problem.line(), problem.column(), problem.key(), problem.message());
    }
}
```

In `PluginHost.java`:
- Extend `Environment`: after `Consumer<Path> editor` add `, PluginSettingsFiles settingsFiles, Consumer<Path> clearReports`.
- In `start(...)`, replace

```java
        var context = new HostedContext(this, hosted, new PluginSettings(id, environment.settingsFile().apply(id), environment.settings().apply(id),
            containment, path -> environment.positions().apply(id, path), environment.configReport()));
```

  with

```java
        var declared = new DeclaredSettings(id, environment.settingsFiles(), containment, environment.ui(), environment.configReport(),
            () -> settingsWritten(id));
        var context = new HostedContext(this, hosted, new PluginSettings(id, environment.settingsFile().apply(id), environment.settings().apply(id),
            containment, path -> environment.positions().apply(id, path), environment.configReport()), declared);
```

- In `start(...)`, replace `context.state = HostedContext.State.ACTIVE;` with:

```java
        context.state = HostedContext.State.ACTIVE;
        context.declared.complete();
```

- In `settingsChanged(Set<String> reloaded)`, after `context.settings.update(environment.settings().apply(id), reloaded.contains(id));`
  add `context.declared.reload(reloaded.contains(id));`.
- Add after `settingsChanged(Set<String> reloaded)`:

```java
    /** A plugin saved a setting: its file's old reports go and its handles see the file again. UI thread. */
    void settingsWritten(String id) {
        requireUi();
        environment.clearReports().accept(environment.settingsFiles().file(id));
        settingsChanged(Set.of(id));
    }
```

In `HostedContext.java`:
- Add the field `final DeclaredSettings declared;`, change the constructor to
  `HostedContext(PluginHost host, HostedPlugin hosted, PluginSettings settings, DeclaredSettings declared)` and set
  `this.declared = declared;` beside `this.settings = settings;`.
- In `teardown(...)`, after `settings.close();` add `declared.close();`.
- Replace the temporary `settings()` from Task 3 with
  `@Override public dev.jasper.sdk.settings.PluginSettings settings() { return declared; }`.

In `PluginRuntime.java`, in the `new PluginHost.Environment(...)` call, replace the final `notice, editor));` with
`notice, editor, settingsFiles, reports.clear()));`.

In `plugins/package-info.java`, add `dev.jasper.app.config, dev.jasper.app.settings, ` before
`dev.jasper.app.terminals` and `dev.jasper.sdk.settings, ` after `dev.jasper.sdk.services, ` in the allowed list.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' verifyApplicationArchitecture`
Expected: PASS (the application's contract suite, `AppContractTest`, still passes).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings
git add jasper-app/src
git commit -m "Read, report, watch and save plugins' declared settings in the application

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The settings contract, held by the fake and the application

**Files:**
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`, `PluginContractTest.java`
- Modify: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`

**Interfaces:**
- Consumes: `FakePluginHost.setSetting/savedSettings/settingsReports` (Task 3); the application's settings store (Tasks 4 and 5).
- Produces: `ContractHarness.setSetting(String pluginId, String key, Object rawValue)`,
  `Optional<Object> savedSetting(String pluginId, String key)`, `List<String> settingsReports(String pluginId)`.

- [ ] **Step 1: Write the failing tests**

In `ContractHarness.java`, before `@Override void close();`, add:

```java
    /**
     * Sets a raw value in a plugin's settings file as a user editing it would, then returns once the plugin has seen
     * the file again. Raw values are what TOML reads: String, Long, Double, Boolean or List of String.
     */
    void setSetting(String pluginId, String key, Object rawValue);

    /** The raw value a plugin's settings file holds for a dotted key. */
    java.util.Optional<Object> savedSetting(String pluginId, String key);

    /** The problems reported for a plugin's settings file and not yet cleared, as {@code key: message}. */
    List<String> settingsReports(String pluginId);
```

In `PluginContractTest.java`, add the imports `dev.jasper.sdk.settings.PluginSettings`, `dev.jasper.sdk.settings.Setting`,
`dev.jasper.sdk.settings.SettingKind`, `dev.jasper.sdk.settings.SettingSpec`, `java.util.concurrent.CopyOnWriteArrayList`,
`java.util.concurrent.ExecutionException`, `java.util.concurrent.TimeUnit`, and the tests:

```java
    @Test void declaredSettingsReadTheirFileAndABadValueFallsBackWithOneReport() {
        h.setSetting("test.settings", "font_size", 99L);
        h.setSetting("test.settings", "tab_size", 6L);
        var size = new AtomicReference<Setting<Integer>>();
        var tab = new AtomicReference<Setting<Integer>>();
        h.start(info("test.settings"), Set.of(), Set.of(), context -> {
            size.set(context.settings().declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            tab.set(context.settings().declare(SettingSpec.of("tab_size", SettingKind.integer(1, 16), 4).label("Tab size")));
        });
        assertThat(h.active("test.settings")).isTrue();
        assertThat(size.get().get()).isEqualTo(13);
        assertThat(tab.get().get()).isEqualTo(6);
        assertThat(h.settingsReports("test.settings")).singleElement().asString().startsWith("font_size: ");
    }

    @Test void settingsAreDeclaredOnlyWhileStartingWithUniqueLabelledKeys() {
        var settings = new AtomicReference<PluginSettings>();
        h.start(info("test.declare"), Set.of(), Set.of(), context -> {
            settings.set(context.settings());
            context.settings().declare(SettingSpec.of("a", SettingKind.bool(), false).label("A"));
            assertThatIllegalArgumentException().isThrownBy(() -> context.settings().declare(SettingSpec.of("a", SettingKind.bool(), true).label("Again")));
            assertThatIllegalArgumentException().isThrownBy(() -> context.settings().declare(SettingSpec.of("a.b", SettingKind.bool(), true).label("Inside")));
            assertThatIllegalArgumentException().isThrownBy(() -> context.settings().declare(SettingSpec.of("b", SettingKind.bool(), true)));
        });
        assertThat(h.active("test.declare")).isTrue();
        h.ui(() -> assertThatIllegalStateException()
            .isThrownBy(() -> settings.get().declare(SettingSpec.of("late", SettingKind.bool(), false).label("Late"))));
    }

    @Test void setSavesTheValueAndItsListenersHearIt() throws Exception {
        var vim = new AtomicReference<Setting<Boolean>>();
        var size = new AtomicReference<Setting<Integer>>();
        var seen = new CopyOnWriteArrayList<Boolean>();
        var any = new AtomicInteger();
        h.start(info("test.save"), Set.of(), Set.of(), context -> {
            vim.set(context.settings().declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode")));
            size.set(context.settings().declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            vim.get().onChanged(seen::add);
            context.settings().onChanged(any::incrementAndGet);
        });
        var saved = new AtomicReference<CompletableFuture<Void>>();
        h.ui(() -> saved.set(vim.get().set(true)));
        saved.get().get(5, TimeUnit.SECONDS);
        h.flush();
        assertThat(vim.get().get()).isTrue();
        assertThat(seen).containsExactly(true);
        assertThat(any.get()).isEqualTo(1);
        assertThat(h.savedSetting("test.save", "vim")).contains(true);
        var refused = new AtomicReference<CompletableFuture<Void>>();
        h.ui(() -> refused.set(size.get().set(99)));
        assertThatThrownBy(() -> refused.get().get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(h.savedSetting("test.save", "font_size")).isEmpty();
    }

    @Test void editsToTheFileReachListenersAndUnknownKeysAreReported() {
        var vim = new AtomicReference<Setting<Boolean>>();
        var seen = new CopyOnWriteArrayList<Boolean>();
        h.start(info("test.edit"), Set.of(), Set.of(), context -> {
            vim.set(context.settings().declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode")));
            vim.get().onChanged(seen::add);
        });
        h.setSetting("test.edit", "vim", true);
        h.flush();
        assertThat(seen).containsExactly(true);
        h.setSetting("test.edit", "mystery", 1L);
        h.flush();
        assertThat(seen).containsExactly(true);
        assertThat(h.settingsReports("test.edit")).anySatisfy(line -> assertThat(line).startsWith("mystery: "));
    }

    @Test void aValueOfTheWrongTypeKeepsTheLastGoodValue() {
        var vim = new AtomicReference<Setting<Boolean>>();
        h.start(info("test.wrong"), Set.of(), Set.of(), context ->
            vim.set(context.settings().declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode"))));
        h.setSetting("test.wrong", "vim", true);
        h.setSetting("test.wrong", "vim", "yes");
        h.flush();
        assertThat(vim.get().get()).isTrue();
        assertThat(h.settingsReports("test.wrong")).anySatisfy(line -> assertThat(line).startsWith("vim: "));
    }
```

`AtomicInteger` and `CompletableFuture` are already imported by the suite; add them if the compiler says otherwise.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeContractTest'`
Expected: compilation fails: the anonymous harness does not implement `setSetting`, `savedSetting` or `settingsReports`.

- [ ] **Step 3: Implement the two harnesses**

In `FakeContractTest.java`, inside the anonymous `ContractHarness`, add:

```java
            @Override public void setSetting(String pluginId, String key, Object rawValue) { host.setSetting(pluginId, key, rawValue); }
            @Override public java.util.Optional<Object> savedSetting(String pluginId, String key) {
                return java.util.Optional.ofNullable(host.savedSettings(pluginId).get(key));
            }
            @Override public List<String> settingsReports(String pluginId) { return host.settingsReports(pluginId); }
```

In `AppContractTest.java`, in `newHarness()`:
- Replace `PluginHost host = host(data, Map.of(), Duration.ofMillis(200), contributions, dark, auxiliary, terminalFixture.registry, notices, edited);`
  with:

```java
        List<dev.jasper.app.config.ConfigDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
        PluginHost host = host(data, Map.of(), Duration.ofMillis(200), contributions, dark, auxiliary, terminalFixture.registry, notices, edited,
            diagnostics);
        PluginSettingsFiles files = host.environment.settingsFiles();
```

- Inside the anonymous `ContractHarness`, add:

```java
            @Override public void setSetting(String pluginId, String key, Object rawValue) {
                Path file = files.file(pluginId);
                try {
                    String text = Files.exists(file) ? Files.readString(file) : "";
                    var document = dev.jasper.app.settings.TomlDocument.parse(text).set(org.tomlj.Toml.parseDottedKey(key), literal(rawValue));
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, document.text());
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
                onEdt(() -> {
                    var changed = files.refresh(true);
                    for (String id : changed) diagnostics.removeIf(diagnostic -> diagnostic.file().equals(files.file(id)));
                    host.settingsChanged(changed);
                });
            }
            @Override public java.util.Optional<Object> savedSetting(String pluginId, String key) {
                Path file = files.file(pluginId);
                try {
                    if (!Files.exists(file)) return java.util.Optional.empty();
                    return java.util.Optional.ofNullable(dev.jasper.app.settings.TomlDocument.leaves(Files.readString(file))
                        .get(org.tomlj.Toml.parseDottedKey(key)));
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            }
            @Override public List<String> settingsReports(String pluginId) {
                return diagnostics.stream().filter(diagnostic -> diagnostic.file().equals(files.file(pluginId)))
                    .map(diagnostic -> diagnostic.key() + ": " + diagnostic.message()).toList();
            }
```

- Add to the class:

```java
    /** A raw TOML value as source text. */
    private static String literal(Object raw) {
        if (raw instanceof List<?> items)
            return items.stream().map(item -> dev.jasper.app.settings.TomlLiterals.string((String) item))
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        return dev.jasper.app.settings.TomlLiterals.format(raw);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.AppContractTest'`
Expected: PASS: the five settings cases pass against the fake and against the application.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings
git add jasper-sdk-testkit/src jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java
git commit -m "Hold the fake and the application to one declared-settings contract

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Documentation and the full check

**Files:**
- Modify: `jasper-sdk/README.md`, `docs/sdk-architecture.md`, `docs/plugin-authoring.md`, `docs/app-architecture.md`,
  `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: Document the API**

`jasper-sdk/README.md`: after the table row for `dev.jasper.sdk.plugin`, add:

```text
| `dev.jasper.sdk.settings` | `PluginSettings`, `Setting`, `SettingSpec`, `SettingKind`, `Choice`, `EditorHint`, `Applies` |
```

`docs/sdk-architecture.md`: at the end of the file, add:

```text
### Declared settings (plan C1; SDK 0.9 completes them)

`PluginContext.settings()` returns `PluginSettings`. A plugin declares each setting during `start` with a
`SettingSpec` (dotted key, `SettingKind`, default, label, description, keywords, section, applies-when, editor hint)
and gets a typed `Setting` handle: `get()` is the file's value when its kind allows it, else the default; `set(value)`
saves through the same comment-preserving, atomic write as `config.toml`; `onChanged` runs on the UI thread. The
application translates declarations into its own settings schema inside `dev.jasper.app.plugins`, reads the plugin's
file with `dev.jasper.app.settings.SchemaReader`, and reports rejected values and undeclared keys against the file.
The testkit's `FakePluginHost` offers `setSetting`, `savedSettings`, `settingsReports` and `declared`, and the contract
suite holds the fake and the application to the same behaviour. `PluginConfig` remains until SDK 0.9 removes it; the
Settings pages for plugins arrive in plan C2.
```

`docs/plugin-authoring.md`: before `## Testing`, add a section headed `## Declared settings` with this opening
paragraph:

```text
Declare each setting while the plugin starts; the handle reads the file's value, reports bad values for you and
saves new ones:
```

then this example as a `java` code block:

```java
Setting<Integer> size = context.settings().declare(
    SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")
        .description("Font size in points.").section("Editor"));
size.onChanged(points -> window.setFontSize(points));
size.set(15);   // writes plugins/<id>/<id>.toml, keeping its comments
```

and this closing paragraph:

```text
Kinds are `bool`, `integer`, `number`, `text` (optionally with a rule and message), `choice` and `stringList`. A value
the kind rejects falls back to the default and is reported against the file with its line; keys nothing declares are
reported as unknown. Declaring after `start` returns throws. `context.config()` still works until SDK 0.9 removes it.
```

`docs/app-architecture.md`: in the package table, append to the `settings` row's description
` SchemaReader reads any settings file against its schema.`; append to the `plugins` row's description
` Declared plugin settings (DeclaredSettings) are read with the plugin's own schema and saved through PluginSettingsFiles.`

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest*' :jasper-sdk:javadoc`
Expected: PASS.

- [ ] **Step 2: The full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Count the tests:

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = e = s = 0
for pat in ["*/build/test-results/test/*.xml", "plugins/*/build/test-results/test/*.xml"]:
    for p in pathlib.Path(".").glob(pat):
        r = ET.parse(p).getroot(); t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(f"tests={t} failures={f} errors={e} skipped={s}")
PY
```

Also run the source hygiene check from AGENTS.md.

- [ ] **Step 3: Record the outcome**

In `docs/STATUS.md`, under "## Current state", add at the top (with the real test count in place of COUNT):

```text
- **Plugin settings C1** (`claude/plugin-settings`, 2026-09-28; spec
  `docs/superpowers/specs/2026-09-28-jasper-plugin-settings-design.md`, plan
  `docs/superpowers/plans/2026-09-28-jasper-plugin-settings-c1.md`): SDK `dev.jasper.sdk.settings` (declarations,
  kinds, typed handles) and `PluginContext.settings()`; the application reads each plugin's declared settings with its
  own schema (`SchemaReader`, moved out of `ConfigLoader`), reports bad values and unknown keys against the file,
  runs listeners on the UI thread and saves through a comment-preserving atomic write; the testkit fake and the
  contract suite cover the same behaviour. `PluginConfig` stays until C3; Settings pages come in C2.
  `./gradlew check`: COUNT tests, 0 failures. Deviations: see the plan's status banner. Not pushed; not merged.
```

Update this plan's **Status** line to `Implemented on claude/plugin-settings; ./gradlew check green, COUNT tests.` and
list deviations under "Deviations so far".

- [ ] **Step 4: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings
git add -A docs jasper-sdk/README.md
git commit -m "Document declared plugin settings (plan C1)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
