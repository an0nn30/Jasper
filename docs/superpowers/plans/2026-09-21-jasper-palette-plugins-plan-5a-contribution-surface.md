# Jasper Palette Plugins Plan 5a: Palette Contribution Surface — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on `claude/palette-plugins`; native acceptance pending. Deviations: three existing tests pinned the capability list or built a `PaneInfo` the plan's grep missed and were extended (`TerminalValuesTest`, `DescriptorParserTest`, `FakeContractTest`); the SDK's one-line Javadocs were rewritten as multi-line comments for doclint. No code deviations. Final verification: 1,435 tests, two expected skips, no failures (see `docs/STATUS.md`).

**Goal:** Let a plugin contribute a command-palette scope, open the palette, tag panes with their shell, show an error notice and open a file in the user's editor, with the host adapter, testkit fake and contract cases behind each, while the application's own History and Snippets scopes stay exactly where they are.

**Architecture:** A JDK-only `dev.jasper.sdk.palette` package mirrors the application's `PaletteScope` seam. In the app, the `Contributions` model gains scopes and palette requests the way it has actions and panel requests; `WindowContributions` registers every contributed scope in its window's `ScopeRegistry`, and `PaletteKeyRouter` treats a contributed scope's shortcut like a built-in one while the palette is open. `plugins.HostedPalette` adapts an SDK scope to an app scope with contained calls and the plugin's row handed back unchanged. `PaneInfo` grows a `shell` component, and the plugin context gains `palette()`, `notices()` and `platform()`, each implemented by the app and by the testkit.

**Tech Stack:** Java 25 on JBR 25, Gradle wrapper, JUnit 6.1.3, AssertJ 3.27.7. No new dependency.

**Spec:** `docs/superpowers/specs/2026-09-21-jasper-palette-plugins-design.md` (sections 3, 4, 5 and the 5a half of 8). Executors read `docs/sdk-architecture.md`, `docs/plugin-authoring.md` and `jasper-app/src/main/java/dev/jasper/app/palette/package-info.java` first; the Commands scope and `PaletteController` show how the app already uses the seam.

## Global Constraints

- Use `./gradlew`, never a system Gradle. Java 25, JetBrains vendor toolchain. `./gradlew check` is headless and must pass at the end of every task.
- **Never launch the GUI** (`:jasper-app:run`, benchmarks, previews that open windows). GUI checks are the user's; the end of this plan lists them.
- Work on branch `claude/palette-plugins` in `.worktrees/palette-plugins` (this plan is committed there, after the spec). Run `git branch --show-current` before every commit and commit only when the verification command exited 0. End commit messages with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- `jasper-sdk` stays JDK-only (`verifySdkArchitecture`); `javax.swing.Icon` is already used by `ActionSpec` and is allowed. SDK types appear in the app only inside `dev.jasper.app.plugins` (`verifyApplicationArchitecture`). `dev.jasper.app.contributions` may now depend on `dev.jasper.app.palette`, and its `package-info` says so; `palette` must not depend on `contributions`.
- No interface without two real implementations: `Palette`, `Notices` and `Platform` are implemented by `HostedContext`/`HostedPalette` and by the testkit; the SDK `PaletteScope` is implemented by the sample plugin here and by the History and Snippets plugins in plan 5b.
- Every call into a plugin scope is contained (`Containment.run` or `attempt`); a failing scope yields an empty result list, an unavailable row, no step or a no-op, never a palette that stops working. Every registration needs the UI thread, an open context and `palette.contribute`.
- The application's `ShellHistoryScope`, `SnippetsScope`, `history` and `snippets` packages are **not touched** in this plan; plan 5b removes them.
- Source hygiene, package-info contracts and Javadoc doclint apply as in earlier plans. Every new production package (there is one, `dev.jasper.sdk.palette`) has a `package-info.java`; every new public type and member has a Javadoc comment, or doclint fails `check`.
- `AppDocumentationTest` link-checks and example-checks Markdown: keep documentation links inside code fences in this plan; never write a literal example marker comment here (the executor writes them in the documents themselves). When `SamplePlugin.start()` changes, the `plugin` example in `docs/plugin-authoring.md` must be re-copied from the source between its start and end markers, or the documentation test fails.
- Never build file content in an unquoted shell heredoc.
- Known flake, not to be fixed here: `TerminalAppIntegrationTest` `"reflow"` case. If it is the only failure, rerun.

### Deliberate scope decisions

1. **`PaneInfo.shell` is the last component**, so every existing constructor call changes by appending one argument. `PaneSnapshot.shell` likewise.
2. **`PaletteTarget` carries `Optional<UUID> windowId` and `Optional<UUID> paneId`** rather than `PaletteContext`, because the target is what `WindowCommandPalette` already builds per open; `PaletteTarget.none()` has neither, and the adapter answers a windowless query with no rows (only fixtures build one).
3. **A contributed scope's shortcut is intercepted only while the palette is open.** Closed, the plugin's own action handler runs through the normal shortcut path and calls `Palette.open`; open, the router switches or dismisses, as with Cmd+P/R/J. `WindowContributions.invoke` therefore needs no exemption.
4. **The adapter keeps the plugin's row as the app row's token.** The app never reads tokens, and the plugin gets its own `PaletteRow` (with its own token) back in `available`, `step` and `execute`.
5. **`Palette.open` on a scope that is already showing dismisses the palette**, the same toggle every scope shortcut has, and does not apply the query or row.
6. **`Notices.error` goes to the last active window's `onError`** (today a dialog), or the log when there is none. `Platform.openInEditor` runs the app's `ConfigEditor` on the plugin's background executor and reports failure through `Notices`.
7. The contract's `addTerminalPane` keeps its signature; both harnesses create panes whose shell is `zsh`, and the contract asserts that.

## File Structure

```
jasper-sdk/src/main/java/dev/jasper/sdk/
  palette/{package-info,Palette,PaletteScope,ScopeSpec,PaletteVerb,PaletteRow,PaletteResults,PaletteStep,PaletteQuery}.java  create
  ui/Notices.java, ui/Platform.java                                create
  Capabilities.java, JasperSdk.java, plugin/PluginContext.java     modify
  terminal/PaneInfo.java                                           modify (shell)
jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java   create

jasper-app/src/main/java/dev/jasper/app/
  palette/{PaletteScope,ScopeRegistry,PaletteTarget,PaletteController,PaletteKeyRouter}.java   modify
  contributions/{Contributions,package-info}.java                  modify
  workspace/{WindowContributions,WindowCommandPalette,TerminalPane}.java   modify
  terminals/PaneSnapshot.java                                      modify (shell)
  plugins/HostedPalette.java                                       create
  plugins/{HostedContext,HostedTerminals,PluginHost,PluginRuntime,DescriptorParser}.java   modify
  pluginmanager/Capabilities.java                                  modify
  application/JasperApplication.java                               modify
jasper-app/src/test/java/dev/jasper/app/
  plugins/{HostedPaletteTest}.java                                 create
  plugins/{TerminalFixture,HostedTerminalsTest,AppContractTest}.java   modify
  contributions/ContributionsTest.java, workspace/{WindowContributionsTest,PaletteKeyRouterTest}.java   modify
  terminals/TerminalRegistryTest.java                              modify

jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/
  FakePalette.java                                                 create
  {FakePluginContext,FakePluginHost,FakeTerminals}.java            modify
jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/{ContractHarness,PluginContractTest}.java   modify
jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/{FakePaletteTest}.java   create
jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/{FakeContractTest,FakeTerminalsTest}.java   modify

plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java, plugins/sample/src/main/resources/plugin.toml, SamplePluginTest.java   modify
docs/{plugin-authoring,sdk-architecture,STATUS}.md, docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md, jasper-sdk/README.md, AGENTS.md   modify
```

---

### Task 0: Branch check

- [ ] **Step 1: Confirm the worktree**

Run: `git branch --show-current && git log --oneline -2`
Expected: `claude/palette-plugins`; the two newest commits are this plan and the spec, over `main` at `9fbdc49`. That main was verified minutes before this branch was cut, so no separate baseline `check` is run here.

---

### Task 1: SDK palette package, `Notices`, `Platform`, capability and version

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/palette/{package-info,PaletteVerb,PaletteRow,PaletteResults,PaletteStep,PaletteQuery,ScopeSpec,PaletteScope,Palette}.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/ui/{Notices,Platform}.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`, `plugins/sample/src/main/resources/plugin.toml`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java`

**Interfaces:**
- Produces everything under section 3 of the spec, used verbatim by Tasks 4 to 6. `PluginContext` is **not** changed here (Task 4 adds the three accessors together with both implementations).

- [ ] **Step 1: Write the failing test**

`jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java`:

```java
package dev.jasper.sdk.palette;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.JasperSdk;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaletteValuesTest {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste");

    @Test void aScopeSpecIsNamespacedHasOneToThreeDistinctVerbsAndLowercaseAliases() {
        ScopeSpec spec = ScopeSpec.of("dev.x.things", "Things", "Search things", List.of(PASTE))
            .withAliases(List.of("th", "things")).withDescription("All the things").withMonospaceRows(true)
            .withShortcutActionId("dev.x.open");
        assertThat(spec.aliases()).containsExactly("th", "things");
        assertThat(spec.shortcutActionId()).contains("dev.x.open");
        assertThat(spec.icon()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("things", "Things", "Search", List.of(PASTE)));
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("dev.x.things", " ", "Search", List.of(PASTE)));
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("dev.x.things", "Things", "Search", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("dev.x.things", "Things", "Search",
            List.of(PASTE, new PaletteVerb("a", "A"), new PaletteVerb("b", "B"), new PaletteVerb("c", "C"))));
        assertThatIllegalArgumentException().as("verb ids are distinct").isThrownBy(() -> ScopeSpec.of("dev.x.things", "Things", "Search",
            List.of(PASTE, new PaletteVerb("paste", "Paste again"))));
        assertThatIllegalArgumentException().isThrownBy(() -> spec.withAliases(List.of("Th")));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteVerb("Paste", "Paste"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteVerb("paste", ""));
    }

    @Test void rowsResultsAndStepsValidateLikeTheApplicationsOwn() {
        PaletteRow row = PaletteRow.of("r1", "Row").withDetail(" ").withTag("3 fields").withToken(42);
        assertThat(row.detail()).isEmpty();
        assertThat(row.tag()).contains("3 fields");
        assertThat(row.token()).isEqualTo(42);
        assertThat(row.enabled()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> PaletteRow.of(" ", "Row"));
        assertThatIllegalArgumentException().isThrownBy(() -> PaletteRow.of("x".repeat(257), "Row"));
        assertThatIllegalArgumentException().isThrownBy(() -> PaletteRow.of("r1", ""));

        PaletteResults results = PaletteResults.of(List.of(row, PaletteRow.of("r2", "Other")));
        assertThat(results.initialSelectionId()).as("defaults to the first row").contains("r1");
        assertThat(results.sectionLabel()).isEmpty();
        assertThat(PaletteResults.none().rows()).isEmpty();
        assertThat(PaletteResults.none().initialSelectionId()).isEmpty();
        assertThat(new PaletteResults(List.of(row), Optional.of("Recent"), Optional.of("r1")).sectionLabel()).contains("Recent");
        List<PaletteRow> many = java.util.stream.IntStream.range(0, 201).mapToObj(i -> PaletteRow.of("r" + i, "Row " + i)).toList();
        assertThatIllegalArgumentException().isThrownBy(() -> PaletteResults.of(many));

        PaletteStep step = new PaletteStep("Name it", List.of(new PaletteStep.Field("name", "Name", null)), (values, done) -> done.accept(PaletteStep.Result.done()));
        assertThat(step.fields().getFirst().prefill()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteStep("Name it", List.of(), (values, done) -> { }));
        assertThat(PaletteStep.Result.error("Taken").error()).contains("Taken");
        assertThat(PaletteStep.Result.reopen("dev.x.other", Optional.of("r9"), Optional.empty()).reopenScopeId()).contains("dev.x.other");
        assertThat(PaletteStep.Result.done().reopenScopeId()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> PaletteStep.Result.error(null));
    }

    @Test void aQueryHasABoundedResultCountAndTheCapabilityAndVersionExist() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteQuery(null, Optional.empty(), 5, true));
        assertThat(Capabilities.PALETTE_CONTRIBUTE).isEqualTo("palette.contribute");
        assertThat(Capabilities.ALL).contains("palette.contribute");
        assertThat(JasperSdk.VERSION).isEqualTo("0.6.0");
    }
}
```

Note on the last test: `PaletteQuery` takes a `WindowHandle`, which the SDK has no way to build in a unit test; `null` must be rejected with `IllegalArgumentException` (not `NullPointerException`), so the record checks it explicitly. `maxResults` must be 1 to 200.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.palette.*' -q`
Expected: compilation failure (`package dev.jasper.sdk.palette does not exist`).

- [ ] **Step 3: Write the package**

`jasper-sdk/src/main/java/dev/jasper/sdk/palette/package-info.java`:

```java
/**
 * Command-palette scopes contributed by plugins. A scope is one kind of searchable thing: it answers a
 * query with rows, runs one of its verbs on a row and may show a small form first. Every method runs
 * on the UI thread and does no I/O; the palette shows at most {@code PaletteQuery.maxResults()} rows
 * and never scrolls. Depends on {@code dev.jasper.sdk} and {@code dev.jasper.sdk.terminal}.
 */
package dev.jasper.sdk.palette;
```

`PaletteVerb.java`:

```java
package dev.jasper.sdk.palette;

/**
 * One thing a scope can do with a row. A scope's verbs are bound in order to Enter, Cmd/Ctrl+Enter
 * and Shift+Enter.
 *
 * @param id    lower-case identifier, unique within the scope: {@code [a-z][a-z0-9_.-]{0,127}}
 * @param label what the footer shows, such as "Paste and run"
 */
public record PaletteVerb(String id, String label) {
    /** Validates both parts. */
    public PaletteVerb {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Invalid verb id: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A verb needs a label");
    }
}
```

`PaletteRow.java`:

```java
package dev.jasper.sdk.palette;

import java.util.Objects;
import java.util.Optional;
import javax.swing.Icon;

/**
 * One result row as plain data; the palette paints every scope's rows the same way. The host never
 * reads {@code token}: a scope keeps its own object there and gets this same row back in
 * {@code available}, {@code step} and {@code execute}.
 *
 * @param id      unique within one result list, at most 256 characters; a step's {@code reopen} names it
 * @param title   the main text
 * @param detail  secondary text under the title, such as a directory
 * @param tag     a short right-aligned marker, such as a shortcut or "2 fields"
 * @param icon    a row icon
 * @param enabled false paints the row dimmed and makes it unavailable
 * @param token   scope-private, may be null
 */
public record PaletteRow(String id, String title, Optional<String> detail, Optional<String> tag, Optional<Icon> icon,
                         boolean enabled, Object token) {
    /** Validates the id and title; blank {@code detail} and {@code tag} become empty. */
    public PaletteRow {
        if (id == null || id.isBlank() || id.length() > 256) throw new IllegalArgumentException("A row needs an id of at most 256 characters");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A row needs a title");
        detail = Objects.requireNonNull(detail, "detail").filter(text -> !text.isBlank());
        tag = Objects.requireNonNull(tag, "tag").filter(text -> !text.isBlank());
        Objects.requireNonNull(icon, "icon");
    }

    /**
     * An enabled row with a title only.
     *
     * @param id    the row id
     * @param title the title
     * @return the row
     */
    public static PaletteRow of(String id, String title) {
        return new PaletteRow(id, title, Optional.empty(), Optional.empty(), Optional.empty(), true, null);
    }

    /** @param value the detail text, or null for none
     *  @return a copy with that detail */
    public PaletteRow withDetail(String value) { return new PaletteRow(id, title, Optional.ofNullable(value), tag, icon, enabled, token); }

    /** @param value the tag, or null for none
     *  @return a copy with that tag */
    public PaletteRow withTag(String value) { return new PaletteRow(id, title, detail, Optional.ofNullable(value), icon, enabled, token); }

    /** @param value the icon, or null for none
     *  @return a copy with that icon */
    public PaletteRow withIcon(Icon value) { return new PaletteRow(id, title, detail, tag, Optional.ofNullable(value), enabled, token); }

    /** @param value whether the row can be chosen
     *  @return a copy with that state */
    public PaletteRow withEnabled(boolean value) { return new PaletteRow(id, title, detail, tag, icon, value, token); }

    /** @param value the scope-private object, or null
     *  @return a copy carrying it */
    public PaletteRow withToken(Object value) { return new PaletteRow(id, title, detail, tag, icon, enabled, value); }
}
```

`PaletteResults.java`:

```java
package dev.jasper.sdk.palette;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a scope answers a query with.
 *
 * @param rows               at most 200 rows, in display order; the palette shows the first {@code maxResults}
 * @param sectionLabel       a heading for the list, used for the empty query ("Recent", "Most recent")
 * @param initialSelectionId the row selected first; defaults to the first row
 */
public record PaletteResults(List<PaletteRow> rows, Optional<String> sectionLabel, Optional<String> initialSelectionId) {
    /** The hard cap on rows in one answer. */
    public static final int MAX_ROWS = 200;

    /** Copies the rows and defaults the selection to the first row. */
    public PaletteResults {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("At most " + MAX_ROWS + " rows");
        sectionLabel = Objects.requireNonNull(sectionLabel, "sectionLabel").filter(text -> !text.isBlank());
        Objects.requireNonNull(initialSelectionId, "initialSelectionId");
        if (initialSelectionId.isEmpty() && !rows.isEmpty()) initialSelectionId = Optional.of(rows.getFirst().id());
    }

    /** @param rows the rows
     *  @return those rows with no section label */
    public static PaletteResults of(List<PaletteRow> rows) { return new PaletteResults(rows, Optional.empty(), Optional.empty()); }

    /** @return no rows */
    public static PaletteResults none() { return new PaletteResults(List.of(), Optional.empty(), Optional.empty()); }
}
```

`PaletteStep.java`:

```java
package dev.jasper.sdk.palette;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A small form the palette shows instead of its list. A scope returns one from
 * {@link PaletteScope#step}; the palette collects the field values and hands them to
 * {@code complete}, which answers with a {@link Result} from any thread: an error keeps the form
 * open, anything else dismisses the palette, optionally reopening it in another scope.
 *
 * @param title    the form's heading
 * @param fields   at least one field, in display order
 * @param complete receives the values by field name and a callback for the result, called exactly once
 */
public record PaletteStep(String title, List<Field> fields, BiConsumer<Map<String, String>, Consumer<Result>> complete) {
    /**
     * One text field.
     *
     * @param name    the key in the values map
     * @param label   what the user sees
     * @param prefill the initial text, or null for empty
     */
    public record Field(String name, String label, String prefill) {
        /** Validates the name and label; a null {@code prefill} becomes empty. */
        public Field {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("A field needs a name");
            if (label == null || label.isBlank()) throw new IllegalArgumentException("A field needs a label");
            prefill = prefill == null ? "" : prefill;
        }
    }

    /**
     * How a step ended.
     *
     * @param error         a message that keeps the form open
     * @param reopenScopeId a scope to reopen the palette in, after dismissing
     * @param reopenRowId   the row to select there
     * @param reopenQuery   the query text to show there
     */
    public record Result(Optional<String> error, Optional<String> reopenScopeId, Optional<String> reopenRowId, Optional<String> reopenQuery) {
        /** Rejects nulls. */
        public Result {
            Objects.requireNonNull(error, "error"); Objects.requireNonNull(reopenScopeId, "reopenScopeId");
            Objects.requireNonNull(reopenRowId, "reopenRowId"); Objects.requireNonNull(reopenQuery, "reopenQuery");
        }

        /** @return the work is done; dismiss */
        public static Result done() { return new Result(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()); }

        /** @param message what went wrong
         *  @return keep the form open with that message */
        public static Result error(String message) {
            return new Result(Optional.of(Objects.requireNonNull(message, "message")), Optional.empty(), Optional.empty(), Optional.empty());
        }

        /**
         * @param scopeId the scope to reopen in
         * @param rowId   the row to select, if any
         * @param query   the query text, if any
         * @return dismiss, then reopen there
         */
        public static Result reopen(String scopeId, Optional<String> rowId, Optional<String> query) {
            return new Result(Optional.empty(), Optional.of(Objects.requireNonNull(scopeId, "scopeId")), rowId, query);
        }
    }

    /** Validates the title, fields and completion. */
    public PaletteStep {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A step needs a title");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        if (fields.isEmpty()) throw new IllegalArgumentException("A step needs at least one field");
        Objects.requireNonNull(complete, "complete");
    }
}
```

`PaletteQuery.java`:

```java
package dev.jasper.sdk.palette;

import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Objects;
import java.util.Optional;

/**
 * What a scope is told about each query.
 *
 * @param window     the window whose palette is open
 * @param target     the pane the palette was opened from, when there is one; the handle is bound to
 *                   the scope's plugin, so pasting into it needs {@code terminal.inject}
 * @param maxResults how many rows the palette will show, 1 to 200
 * @param macOs      whether to show macOS shortcut glyphs and wording
 */
public record PaletteQuery(WindowHandle window, Optional<PaneHandle> target, int maxResults, boolean macOs) {
    /** Validates the window and the bound. */
    public PaletteQuery {
        if (window == null) throw new IllegalArgumentException("A query needs a window");
        Objects.requireNonNull(target, "target");
        if (maxResults < 1 || maxResults > PaletteResults.MAX_ROWS) throw new IllegalArgumentException("maxResults must be 1 to " + PaletteResults.MAX_ROWS);
    }
}
```

`ScopeSpec.java`:

```java
package dev.jasper.sdk.palette;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.Icon;

/**
 * What a scope is: identity, wording, verbs and how it is reached.
 *
 * @param id               namespaced like an action id and starting with the plugin's id, such as
 *                         {@code dev.jasper.history.scope}; the host rejects any other prefix
 * @param label            the chip text, such as "History"
 * @param description      one line for the scope picker
 * @param placeholder      the query field's hint while this scope is active
 * @param aliases          lower-case words the picker accepts after {@code >}, such as {@code hist}
 * @param verbs            one to three verbs with distinct ids, bound to Enter, Cmd/Ctrl+Enter, Shift+Enter
 * @param monospaceRows    whether rows are painted in the terminal font (commands, paths)
 * @param icon             the chip icon
 * @param shortcutActionId one of the plugin's own actions: while the palette is open, that action's
 *                         shortcut switches to or dismisses this scope instead of running the handler
 */
public record ScopeSpec(String id, String label, String description, String placeholder, List<String> aliases,
                        List<PaletteVerb> verbs, boolean monospaceRows, Optional<Icon> icon, Optional<String> shortcutActionId) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");
    private static final Pattern ALIAS = Pattern.compile("[a-z][a-z0-9_-]{0,31}");

    /** Validates every part and copies the lists. */
    public ScopeSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches()) throw new IllegalArgumentException("Not a namespaced scope id: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A scope needs a label");
        description = description == null ? "" : description;
        if (placeholder == null || placeholder.isBlank()) throw new IllegalArgumentException("A scope needs a placeholder");
        aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
        for (String alias : aliases) if (!ALIAS.matcher(alias).matches()) throw new IllegalArgumentException("Not a lower-case alias: " + alias);
        verbs = List.copyOf(Objects.requireNonNull(verbs, "verbs"));
        if (verbs.isEmpty() || verbs.size() > 3) throw new IllegalArgumentException("A scope has one to three verbs");
        Set<String> ids = new HashSet<>();
        for (PaletteVerb verb : verbs) if (!ids.add(verb.id())) throw new IllegalArgumentException("Duplicate verb id: " + verb.id());
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(shortcutActionId, "shortcutActionId");
    }

    /**
     * The minimum: no aliases, description, icon or shortcut, proportional rows.
     *
     * @param id          the scope id
     * @param label       the chip text
     * @param placeholder the query hint
     * @param verbs       the verbs
     * @return the spec
     */
    public static ScopeSpec of(String id, String label, String placeholder, List<PaletteVerb> verbs) {
        return new ScopeSpec(id, label, "", placeholder, List.of(), verbs, false, Optional.empty(), Optional.empty());
    }

    /** @param value the picker line
     *  @return a copy with that description */
    public ScopeSpec withDescription(String value) { return new ScopeSpec(id, label, value, placeholder, aliases, verbs, monospaceRows, icon, shortcutActionId); }

    /** @param value the aliases
     *  @return a copy with those aliases */
    public ScopeSpec withAliases(List<String> value) { return new ScopeSpec(id, label, description, placeholder, value, verbs, monospaceRows, icon, shortcutActionId); }

    /** @param value whether rows use the terminal font
     *  @return a copy with that setting */
    public ScopeSpec withMonospaceRows(boolean value) { return new ScopeSpec(id, label, description, placeholder, aliases, verbs, value, icon, shortcutActionId); }

    /** @param value the chip icon, or null for none
     *  @return a copy with that icon */
    public ScopeSpec withIcon(Icon value) { return new ScopeSpec(id, label, description, placeholder, aliases, verbs, monospaceRows, Optional.ofNullable(value), shortcutActionId); }

    /** @param value the plugin's action whose shortcut reaches this scope, or null for none
     *  @return a copy naming it */
    public ScopeSpec withShortcutActionId(String value) { return new ScopeSpec(id, label, description, placeholder, aliases, verbs, monospaceRows, icon, Optional.ofNullable(value)); }
}
```

`PaletteScope.java`:

```java
package dev.jasper.sdk.palette;

import dev.jasper.sdk.Subscription;
import java.util.Optional;

/**
 * One kind of searchable thing, implemented by a plugin and registered through {@link Palette}.
 * Every method runs on the UI thread and must do no I/O; a scope that reads files keeps an index it
 * refreshes in the background and publishes through its {@link #onChanged} listener. The host contains
 * every call: a failing method yields no rows, an unavailable row, no step or nothing done.
 */
public interface PaletteScope {
    /**
     * Read once, at registration.
     *
     * @return what this scope is
     */
    ScopeSpec spec();

    /**
     * Answers a query. The empty query is the "recent" or "all" list and may carry a section label.
     *
     * @param query   the raw query text
     * @param context the window, origin pane and row budget
     * @return at most {@code context.maxResults()} rows
     */
    PaletteResults search(String query, PaletteQuery context);

    /**
     * Rechecked immediately before a verb runs; {@code false} refreshes the list instead of executing.
     *
     * @param row     a row this scope returned
     * @param verb    one of this scope's verbs
     * @param context the query context
     * @return whether the verb may run now
     */
    default boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) { return row.enabled(); }

    /**
     * Consulted before {@link #execute}: a present step is shown as a form instead of running the verb,
     * and {@code execute} is not called for that action; the step's completion does the work.
     *
     * @param row     a row this scope returned
     * @param verb    the verb chosen
     * @param context the query context
     * @return a form to fill first, or empty to run the verb at once
     */
    default Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) { return Optional.empty(); }

    /**
     * Runs a verb on a row.
     *
     * @param row     a row this scope returned
     * @param verb    the verb chosen
     * @param context the query context
     */
    void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context);

    /**
     * The scope became active in an open palette; a scope may ask its index for a background refresh.
     *
     * @param context the query context
     */
    default void activated(PaletteQuery context) { }

    /**
     * Tells the palette its results may have changed; an open query is re-run.
     *
     * @param listener called on the UI thread
     * @return the registration
     */
    Subscription onChanged(Runnable listener);
}
```

`Palette.java`:

```java
package dev.jasper.sdk.palette;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Optional;

/**
 * The command palette as a plugin sees it. Both methods need {@code palette.contribute}, the UI thread
 * and an open context; implemented by the application and by the testkit.
 */
public interface Palette {
    /**
     * Registers an application-wide scope; every window shows it. The spec is read once; its id must
     * start with the plugin's id, and its {@code shortcutActionId}, if any, must be an action this
     * plugin registered earlier.
     *
     * @param scope the scope
     * @return the registration; closing removes the scope from every window
     */
    Subscription register(PaletteScope scope);

    /**
     * Opens the palette in {@code window} on {@code scopeId}, or dismisses it when that scope is already
     * showing. {@code query} replaces the query text and {@code rowId} selects a row of the first list;
     * neither is applied when dismissing. Unknown scopes are ignored.
     *
     * @param window  the window
     * @param scopeId any registered scope, including {@code jasper.commands} and other plugins' scopes
     * @param query   the query text to show
     * @param rowId   the row to select
     */
    void open(WindowHandle window, String scopeId, Optional<String> query, Optional<String> rowId);
}
```

`jasper-sdk/src/main/java/dev/jasper/sdk/ui/Notices.java`:

```java
package dev.jasper.sdk.ui;

/**
 * Tells the user something went wrong, the way the application reports its own failures: in the
 * window the user is using, or in the log when there is none. Safe from any thread. Implemented by
 * the application and by the testkit.
 */
public interface Notices {
    /**
     * Shows an error.
     *
     * @param message one or two sentences, already in the user's terms
     */
    void error(String message);
}
```

`jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java`:

```java
package dev.jasper.sdk.ui;

import java.nio.file.Path;

/**
 * Desktop integration the application already does for its own files. Implemented by the application
 * and by the testkit.
 */
public interface Platform {
    /**
     * Opens a file in the user's editor: the configured editor, then the desktop's edit or open
     * action, then revealing the file. Runs in the background; a failure is reported through
     * {@link Notices}, never thrown.
     *
     * @param file the file
     */
    void openInEditor(Path file);
}
```

Edit `Capabilities.java`: add after `SESSION_PROVIDE`

```java
    /** Contribute command-palette scopes and open the palette. */
    public static final String PALETTE_CONTRIBUTE = "palette.contribute";
```

and make `ALL` end with `SESSION_PROVIDE, PALETTE_CONTRIBUTE)`.

Edit `JasperSdk.java`: `VERSION = "0.6.0"`, and its Javadoc sentence about the version if it names one.

Edit `plugins/sample/src/main/resources/plugin.toml`: `sdk = ">=0.6, <0.7"`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-sdk:test :jasper-sdk:javadoc :jasper-plugin-sample:test -q`
Expected: PASS. If `:jasper-plugin-sample` is not the sample module's Gradle path, read `settings.gradle.kts` and use the path found there (it is the one `jasper-app/build.gradle.kts` names in `stagePlugins`).

Run: `grep -rn "0\.5" jasper-sdk-testkit/src plugins/sample/src jasper-app/src/test --include=*.java --include=*.toml | grep -i "sdk\|version\|range"`
Expected: no test or fixture pins the SDK version to 0.5 (the resolver tests use their own numbers). Fix any that does.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-sdk plugins/sample/src/main/resources/plugin.toml
git commit -m "feat: add the palette contribution surface, notices and platform to the SDK

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `PaneInfo.shell` and `PaneSnapshot.shell`

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/PaneInfo.java`, `jasper-app/src/main/java/dev/jasper/app/terminals/PaneSnapshot.java`, `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java`, `jasper-app/src/main/java/dev/jasper/app/plugins/HostedTerminals.java`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakeTerminals,FakePluginHost}.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/{TerminalFixture,HostedTerminalsTest}.java`, `jasper-app/src/test/java/dev/jasper/app/terminals/TerminalRegistryTest.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeTerminalsTest.java`, `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`

**Interfaces:**
- Produces `PaneInfo(…, OptionalInt exitStatus, String shell)` (eleventh, last component) and `PaneSnapshot(…, Optional<RemoteLocation> remoteDirectory, String shell)` (tenth, last). Task 5's contract asserts a harness pane's shell is `zsh`.

- [ ] **Step 1: Write the failing tests**

In `TerminalValuesTest.paneInfoRejectsImpossibleValues` append one argument `, "zsh"` to each of the three `new PaneInfo(` calls, and add at the end of the method:

```java
        assertThatNullPointerException().as("the shell label is required; use \"\" when unknown").isThrownBy(() -> new PaneInfo("t",
            Optional.empty(), Optional.empty(), 80, 24, false, dev.jasper.sdk.terminal.SessionKind.LOCAL, Optional.empty(),
            dev.jasper.sdk.terminal.SessionState.RUNNING, OptionalInt.empty(), null));
        assertThat(PaneInfo.unknown().shell()).isEmpty();
```

In `HostedTerminalsTest`, the `pane.info()` equality at line 59 gains `, "zsh"` as the last constructor argument.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests '*TerminalValuesTest' -q`
Expected: compilation failure (no eleven-argument constructor).

- [ ] **Step 3: Add the component everywhere**

`PaneInfo`: add the component and its Javadoc line `@param shell the shell or program label, such as {@code zsh}, or a provided session's title; empty when unknown`; in the compact constructor `Objects.requireNonNull(shell, "shell");`; `unknown()` passes `""`.

`PaneSnapshot`: add `String shell` as the last component with Javadoc `@param shell the launcher's label or the provided session's title`; if the record has a compact constructor add `Objects.requireNonNull(shell, "shell")`, otherwise add one with only that line.

`TerminalPane.snapshot()`: both `new PaneSnapshot(` calls (lines 386 and 392) gain `, shellLabel` last.

`HostedTerminals.info(PaneSnapshot)`: the `new PaneInfo(` gains `, snapshot.shell()` last.

`FakeTerminals`: the three `new PaneInfo(` calls gain, in order, `, "sh"` (the blank local pane at line 187), `, session.spec().title()` (the connecting provided pane at line 192) and `, info.shell()` (the state copy at line 198).

`FakePluginHost`: the three rebuilds at lines 607, 656 and 743 gain `, info.shell()`, `, pane.info.shell()` and `, pane.info.shell()` respectively.

`TerminalFixture`: the `Pane` class gains `String shell = "zsh";` and the snapshot supplier passes `, pane.shell` last.

`TerminalRegistryTest` line 35 and `FakeTerminalsTest` line 20 gain `, "sh"` and `, "zsh"`; `SamplePluginTest` line 112 gains `, "zsh"`.

Then: `grep -rn "new PaneInfo(\|new PaneSnapshot(" --include=*.java jasper-sdk jasper-sdk-testkit jasper-app plugins | grep -v /build/` and check every call was covered; the compiler catches any miss.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*HostedTerminalsTest' --tests '*TerminalRegistryTest' --tests '*TerminalBridgeTest' --tests '*AppContractTest' -q` and then the sample module's `test`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A jasper-sdk jasper-sdk-testkit jasper-app plugins
git commit -m "feat: tag panes with their shell label for plugins

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 3: Contributed scopes in the application model, windows and key routing

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/{PaletteScope,ScopeRegistry,PaletteTarget,PaletteController,PaletteKeyRouter}.java`, `jasper-app/src/main/java/dev/jasper/app/contributions/{Contributions,package-info}.java`, `jasper-app/src/main/java/dev/jasper/app/workspace/{WindowContributions,WindowCommandPalette}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/contributions/ContributionsTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/WindowContributionsTest.java`

**Interfaces:**
- Produces, for Task 4: `Contributions.addScope(PaletteScope) → Subscription`, `Contributions.scopes()`, `Contributions.PaletteRequest(UUID windowId, String scopeId, Optional<String> query, Optional<String> rowId)`, `Contributions.requestPalette(PaletteRequest)`, `Contributions.onPaletteRequest(Consumer<PaletteRequest>)`, `Kind.SCOPES`; app `PaletteScope.shortcutActionId()`; `PaletteTarget.windowId()` and `paneId()`; `PaletteContext` unchanged in shape.
- Consumes nothing new.

- [ ] **Step 1: Write the failing tests**

Add to `jasper-app/src/test/java/dev/jasper/app/palette/PaletteTestSupport.java` (public, so tests in `contributions` and `workspace` share it; imports `dev.jasper.app.lifecycle.Subscription`, `java.util.List`, `java.util.Optional`):

```java
    /** A scope with one verb and two fixed rows; {@code shortcutOrNull} names a contributed action. */
    public static PaletteScope scope(String id, String shortcutOrNull) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return id; }
            @Override public String placeholder() { return "Search " + id; }
            @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("one", "One")); }
            @Override public Optional<String> shortcutActionId() { return Optional.ofNullable(shortcutOrNull); }
            @Override public PaletteResults search(String query, PaletteContext context) {
                return new PaletteResults(List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta")), null, null);
            }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }
```

Append to `ContributionsTest` (it runs on the EDT through `EdtTestExtension`; add `import static dev.jasper.app.palette.PaletteTestSupport.scope;`, `dev.jasper.app.palette.PaletteScope`, `dev.jasper.app.lifecycle.Subscription`):

```java
    @Test void scopesAreContributedOnceEachRemovalIsItsOwnAndPaletteRequestsReachListeners() {
        {
            var model = new Contributions();
            List<Contributions.Kind> kinds = new ArrayList<>();
            model.onChanged(kinds::add);
            List<Contributions.PaletteRequest> requests = new ArrayList<>();
            model.onPaletteRequest(requests::add);
            PaletteScope first = scope("dev.x.first", null), second = scope("dev.x.second", "dev.x.open");
            Subscription one = model.addScope(first);
            model.addScope(second);
            assertThat(model.scopes()).containsExactly(first, second);
            assertThat(kinds).containsExactly(Contributions.Kind.SCOPES, Contributions.Kind.SCOPES);
            assertThatIllegalArgumentException().isThrownBy(() -> model.addScope(scope("dev.x.first", null)));
            assertThatIllegalArgumentException().isThrownBy(() -> model.addScope(scope("Bad Id", null)));
            one.close(); one.close();
            assertThat(model.scopes()).containsExactly(second);
            assertThat(kinds).hasSize(3);
            UUID window = UUID.randomUUID();
            model.requestPalette(new Contributions.PaletteRequest(window, "dev.x.second", Optional.of("be"), Optional.of("beta")));
            assertThat(requests).singleElement().satisfies(request -> {
                assertThat(request.windowId()).isEqualTo(window);
                assertThat(request.query()).contains("be");
            });
        }
    }
```

Append to `WindowContributionsTest` (imports: `dev.jasper.app.palette.PaletteScope`, `dev.jasper.app.palette.PaletteTestSupport`, `java.util.UUID`; the owner comes from `CommandPaletteShortcutsTest.owner(true)`, whose palette can open, and is closed by the try block):

```java
    @Test void aContributedScopeIsInThisWindowsPaletteItsShortcutSwitchesWhileOpenAndRequestsAreRouted() throws Exception {
        edt(() -> {
            try (WindowContent owner = CommandPaletteShortcutsTest.owner(true)) {
            var model = new Contributions();
            CommandPaletteShortcutsTest.install(owner);
            model.addAction("dev.x.open", "Open X", null, List.of(), Optional.of("cmd+alt+h"),
                invocation -> owner.commandPalette().open("dev.x.scope"));
            var registration = model.addScope(PaletteTestSupport.scope("dev.x.scope", "dev.x.open"));
            owner.connectContributions(model);
            var palette = owner.commandPalette();
            assertThat(owner.scopes().find("dev.x.scope")).as("registered in the window on connect").isPresent();

            assertThat(owner.dispatchShortcut(stroke("cmd+alt+h"), null)).as("closed: the plugin's handler runs").isTrue();
            assertThat(palette.isOpen()).isTrue();
            assertThat(palette.activeScopeId()).isEqualTo("dev.x.scope");
            palette.open(PaletteScope.COMMANDS_ID);
            assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+h"), null)).as("open: the router switches scope").isTrue();
            assertThat(palette.activeScopeId()).isEqualTo("dev.x.scope");
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+h"), null)).as("again: dismisses").isTrue();
            assertThat(palette.isOpen()).isFalse();

            model.requestPalette(new Contributions.PaletteRequest(UUID.randomUUID(), "dev.x.scope", Optional.empty(), Optional.empty()));
            assertThat(palette.isOpen()).as("another window's request").isFalse();
            model.requestPalette(new Contributions.PaletteRequest(owner.id(), "dev.x.scope", Optional.of("al"), Optional.of("beta")));
            assertThat(palette.isOpen()).isTrue();
            assertThat(palette.activeScopeId()).isEqualTo("dev.x.scope");
            assertThat(palette.component().queryField().getText()).isEqualTo("al");
            assertThat(PaletteTestSupport.resultList(palette.component()).getSelectedValue().id()).isEqualTo("beta");
            model.requestPalette(new Contributions.PaletteRequest(owner.id(), "dev.x.scope", Optional.of("x"), Optional.empty()));
            assertThat(palette.isOpen()).as("a request for the showing scope dismisses").isFalse();

            var scope = model.addScope(PaletteTestSupport.scope("dev.x.late", null));
            assertThat(owner.scopes().find("dev.x.late")).as("added after connect").isPresent();
            scope.close(); registration.close();
            assertThat(owner.scopes().find("dev.x.late")).isEmpty();
            assertThat(owner.scopes().find("dev.x.scope")).isEmpty();
            }
        });
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*ContributionsTest' --tests '*WindowContributionsTest' -q`
Expected: compilation failure (`addScope`, `PaletteRequest`, `shortcutActionId`).

- [ ] **Step 3: The palette package**

`PaletteScope` (app): add `import java.util.Optional;` and, after `monospaceRows()`:

```java
    /**
     * A contributed action whose shortcut, while the palette is open, switches to or dismisses this
     * scope instead of being swallowed; the built-in scopes are routed by {@code ActionId} instead.
     */
    default Optional<String> shortcutActionId() { return Optional.empty(); }
```

`ScopeRegistry`: add

```java
    /** The scope naming {@code actionId} as its shortcut action, if any. */
    public Optional<PaletteScope> byShortcutAction(String actionId) {
        CommandRegistry.requireEdt();
        for (PaletteScope scope : scopes.values())
            if (scope.shortcutActionId().filter(actionId::equals).isPresent()) return Optional.of(scope);
        return Optional.empty();
    }
```

`PaletteTarget`: replace the record with

```java
/**
 * The origin pane as a scope sees it. Scopes get no window, pane or emulator types; the ids let the
 * plugin runtime hand a contributed scope handles of its own.
 */
public record PaletteTarget(Consumer<String> paste, Runnable sendReturn, Supplier<Optional<Path>> workingDirectory,
                     Supplier<String> shellName, BooleanSupplier live, Optional<UUID> windowId, Optional<UUID> paneId) {
    public PaletteTarget {
        Objects.requireNonNull(paste); Objects.requireNonNull(sendReturn); Objects.requireNonNull(workingDirectory);
        Objects.requireNonNull(shellName); Objects.requireNonNull(live); Objects.requireNonNull(windowId); Objects.requireNonNull(paneId);
    }

    /** No pane and no window: a scope answers with nothing it needs a window for. */
    public static PaletteTarget none() {
        return new PaletteTarget(text -> {}, () -> {}, Optional::empty, () -> "", () -> false, Optional.empty(), Optional.empty());
    }

    /** A window with no origin pane: nothing to paste into, but scopes know where they are. */
    public static PaletteTarget window(UUID windowId) {
        return new PaletteTarget(text -> {}, () -> {}, Optional::empty, () -> "", () -> false, Optional.of(windowId), Optional.empty());
    }
}
```

(add `import java.util.UUID;`). Then `grep -rn "new PaletteTarget(" --include=*.java jasper-app/src` and append `, Optional.empty(), Optional.empty()` to every call outside `PaletteTarget` itself and `WindowCommandPalette.target` (the next step rewrites that one).

`PaletteController`: add

```java
    /** The registered scope whose shortcut action is {@code actionId}. */
    public Optional<String> scopeForShortcutAction(String actionId) {
        return scopes.byShortcutAction(actionId).map(PaletteScope::id);
    }
```

and replace `open(String scopeId, PaletteTarget target, BooleanSupplier valid)` with

```java
    /** Captures the origin only on first open; changing scopes retains that captured target. */
    public boolean open(String scopeId, PaletteTarget target, BooleanSupplier valid) { return open(scopeId, target, valid, null, null); }

    /**
     * As {@link #open(String, PaletteTarget, BooleanSupplier)}; afterwards {@code queryOrNull} replaces
     * the query text and {@code rowIdOrNull} selects a row of the resulting list. Neither is applied
     * when the call dismisses an already showing scope.
     */
    public boolean open(String scopeId, PaletteTarget target, BooleanSupplier valid, String queryOrNull, String rowIdOrNull) {
        if (closed) return false;
        PaletteScope scope = scopes.find(scopeId).orElse(null);
        if (scope == null) return false;
        if (open) {
            if (scope == active) { dismiss(); return false; }
            activate(scope, !picker);
        } else {
            generation++;
            originValid = valid;
            context = new PaletteContext(macOs, target, maxResults, trivialCommands);
            open = true; palette.setVisible(true);
            activate(scope, false);
        }
        if (queryOrNull != null) palette.queryField().setText(queryOrNull);
        if (rowIdOrNull != null) palette.selectRow(rowIdOrNull);
        return true;
    }
```

(`import java.util.Optional;` if missing.)

`PaletteKeyRouter.route`: after `String scope = scopeFor(action.orElse(null));` insert

```java
        // A contributed scope's shortcut is intercepted only while the palette is open; closed, the
        // plugin's own action handler runs through the ordinary shortcut path and opens the palette itself.
        if (scope == null && palette.isOpen())
            scope = bindings.get().idFor(stroke).filter(KeyBindings::extensionId).flatMap(palette::scopeForShortcutAction).orElse(null);
```

- [ ] **Step 4: The contributions model**

`Contributions`: add `import dev.jasper.app.palette.PaletteScope;` and

```java
    /** Asks one window to open its palette. */
    public record PaletteRequest(UUID windowId, String scopeId, Optional<String> query, Optional<String> rowId) {
        public PaletteRequest {
            Objects.requireNonNull(windowId, "windowId"); Objects.requireNonNull(scopeId, "scopeId");
            Objects.requireNonNull(query, "query"); Objects.requireNonNull(rowId, "rowId");
        }
    }

    private final List<PaletteScope> scopes = new ArrayList<>();
    private final List<Consumer<PaletteRequest>> paletteListeners = new ArrayList<>();

    /**
     * A scope every window registers in its palette, one instance shared by all of them. Ids are
     * unique among contributed scopes; a window also refuses a clash with one of its built-in ids.
     */
    public Subscription addScope(PaletteScope scope) {
        requireEdt();
        String id = PaletteScope.requireValidId(Objects.requireNonNull(scope, "scope").id());
        if (scope.verbs().isEmpty()) throw new IllegalArgumentException("Scope needs at least one verb: " + id);
        for (PaletteScope existing : scopes)
            if (existing.id().equals(id)) throw new IllegalArgumentException("Scope already contributed: " + id);
        scopes.add(scope);
        changed(Kind.SCOPES);
        return new Subscription(() -> { if (scopes.remove(scope)) changed(Kind.SCOPES); });
    }

    public List<PaletteScope> scopes() { return List.copyOf(scopes); }

    public Subscription onPaletteRequest(Consumer<PaletteRequest> listener) {
        requireEdt();
        paletteListeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> paletteListeners.remove(listener));
    }

    public void requestPalette(PaletteRequest request) {
        requireEdt();
        for (Consumer<PaletteRequest> listener : List.copyOf(paletteListeners)) {
            try { listener.accept(request); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A palette request listener failed", failure); }
        }
    }
```

`Kind` becomes `ACTIONS, TOOLBAR, MENUS, STATUS, PANELS, RAIL, SCOPES`. Update the package Javadoc sentence to "actions, toolbar entries, menu sections, status entries, panels and palette scopes" and its allowed-dependencies line to `dev.jasper.app.lifecycle, dev.jasper.app.palette`.

- [ ] **Step 5: Windows**

`WindowContributions`: add fields and wiring

```java
    private static final System.Logger LOG = System.getLogger(WindowContributions.class.getName());
    private final Map<PaletteScope, Subscription> scopes = new LinkedHashMap<>();
    private Subscription paletteRequests;
```

(`import dev.jasper.app.palette.PaletteScope;`). In the constructor, after `panelRequests = model.onPanelRequest(this::requested);` add `paletteRequests = model.onPaletteRequest(this::requestedPalette); syncScopes();`. Add:

```java
    private void requestedPalette(Contributions.PaletteRequest request) {
        if (!request.windowId().equals(owner.id()) || owner.commandPalette() == null) return;
        owner.commandPalette().open(request.scopeId(), request.query().orElse(null), request.rowId().orElse(null));
    }

    /** Registers contributed scopes this window does not have yet and drops the ones that left the model. */
    private void syncScopes() {
        List<PaletteScope> current = model.scopes();
        for (PaletteScope scope : List.copyOf(scopes.keySet()))
            if (!current.contains(scope)) scopes.remove(scope).close();
        for (PaletteScope scope : current) {
            if (scopes.containsKey(scope)) continue;
            try { scopes.put(scope, owner.scopes().register(scope)); }
            catch (IllegalArgumentException clash) {
                LOG.log(System.Logger.Level.WARNING, "A contributed scope is not shown in this window: " + clash.getMessage());
            }
        }
    }
```

In `changed(Kind)` add `case SCOPES -> syncScopes();`. In `close()`, after the `commands` lines: `scopes.values().forEach(Subscription::close); scopes.clear(); if (paletteRequests != null) { paletteRequests.close(); paletteRequests = null; }`.

`WindowCommandPalette`: replace `void open(String id)` with

```java
    void open(String id) { open(id, null, null); }

    /** {@code queryOrNull} replaces the query text and {@code rowIdOrNull} selects a row; neither applies when this call dismisses. */
    void open(String id, String queryOrNull, String rowIdOrNull) {
        if (closed || !hasScope(id)) return;
        boolean wasOpen = controller.isOpen();
        if (!wasOpen) {
            if (root == null || !owner.isActiveAndOpen()
                    || !SwingUtilities.isDescendingFrom(owner, root.getLayeredPane())) return;
            owner.updateActions();
            originTab = owner.currentTab(); originPane = owner.currentPane();
            priorFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        }
        if (controller.open(id, originPane == null ? PaletteTarget.window(owner.id()) : target(originPane), this::validOrigin, queryOrNull, rowIdOrNull)) {
            if (!wasOpen) overlay.swallowing = false;
            overlay.setVisible(true); layoutOverlay();
            palette.queryField().requestFocusInWindow();
        }
    }
```

and make `target` an instance method whose `new PaletteTarget(` ends with `, Optional.of(owner.id()), Optional.of(pane.id())` (keep its five existing arguments as they are; `TerminalPane.id()` already exists, as `WindowContributions.invoke` shows). The `reopen` consumer passed to the controller stays `this::open` (the one-argument overload; if the method reference is now ambiguous, pass `id -> open(id)`).

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.palette.*' -q`
Expected: PASS.

Run: `./gradlew :jasper-app:check verifyApplicationArchitecture -q`
Expected: `BUILD SUCCESSFUL` (the package graph stays acyclic with `contributions -> palette`).

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add -A jasper-app
git commit -m "feat: let the contributions model carry palette scopes and open requests into every window

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 4: `palette()`, `notices()` and `platform()` in the app and the testkit

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedPalette.java`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePalette.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java`, `jasper-app/src/main/java/dev/jasper/app/plugins/{HostedContext,PluginHost,PluginRuntime,DescriptorParser}.java`, `jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java`, `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakePluginContext,FakePluginHost}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedPaletteTest.java` (create), `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java` (the `host` factory only), `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePaletteTest.java` (create)

**Interfaces:**
- Produces `PluginContext.palette()`, `notices()`, `platform()`; `PluginHost.Environment(…, TerminalRegistry terminals, Consumer<String> notice, Consumer<Path> editor)`; `PluginRuntime(Options, ActivityNotifier, BiConsumer<String,String>, Contributions, AuxiliaryWindows, TerminalRegistry, Consumer<String> notice, Consumer<Path> editor)` beside the existing six-argument constructor; `FakePluginHost.{scopes, searchScope, availableInScope, stepInScope, completeStep, executeInScope, paletteOpens, notices, openedInEditor}` used by Task 5's harness.
- Consumes Task 1's SDK types, Task 3's model.

- [ ] **Step 1: Write the failing tests**

`jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePaletteTest.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteStep;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakePaletteTest {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste"), NAME = new PaletteVerb("name", "Name it");

    /** Two rows; "paste" pastes the title into the target, "name" asks for a name and reopens elsewhere. */
    static final class Things implements PaletteScope {
        final List<String> log = new ArrayList<>();
        @Override public ScopeSpec spec() {
            return ScopeSpec.of("test.a.things", "Things", "Search things", List.of(PASTE, NAME)).withShortcutActionId("test.a.open");
        }
        @Override public PaletteResults search(String query, PaletteQuery context) {
            log.add("search:" + query + ":" + context.window().id());
            return PaletteResults.of(List.of(PaletteRow.of("one", "One").withToken("t1"), PaletteRow.of("two", "Two").withEnabled(false)));
        }
        @Override public Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
            if (!verb.equals(NAME)) return Optional.empty();
            return Optional.of(new PaletteStep("Name " + row.title(), List.of(new PaletteStep.Field("name", "Name", row.title())),
                (values, done) -> done.accept(values.get("name").isBlank() ? PaletteStep.Result.error("Give it a name")
                    : PaletteStep.Result.reopen("test.b.other", Optional.of("row-" + values.get("name")), Optional.of(values.get("name"))))));
        }
        @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
            log.add("execute:" + row.id() + ":" + verb.id() + ":" + row.token());
            context.target().ifPresent(pane -> pane.paste(row.title()));
        }
        @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
    }

    @Test void aScopeIsRegisteredSearchedSteppedAndRunWithItsOwnRowsAndHandles() {
        try (var host = new FakePluginHost()) {
            var things = new Things();
            var context = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.a", "A", "1.0.0", Set.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT)), Set.of(), Set.of(), c -> {
                context.set(c);
                c.actions().register(ActionSpec.of("test.a.open", "Things…"), invoked -> c.palette().open(invoked.window(), "test.a.things", Optional.empty(), Optional.empty()));
                c.palette().register(things);
            });
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "t");
            UUID pane = host.addTerminalPane(tab, FakeTerminalsTest.info("zsh"));
            assertThat(host.scopes()).containsExactly("test.a.things|Things|paste,name");
            assertThat(host.searchScope("test.a.things", "on", window, pane)).containsExactly("one|One|true", "two|Two|false");
            assertThat(things.log).containsExactly("search:on:" + window);
            assertThat(host.availableInScope("test.a.things", "one", "paste", window, pane)).isTrue();
            assertThat(host.availableInScope("test.a.things", "two", "paste", window, pane)).isFalse();
            assertThat(host.stepInScope("test.a.things", "one", "paste", window, pane)).isEmpty();
            assertThat(host.stepInScope("test.a.things", "one", "name", window, pane)).contains("Name One");
            assertThat(host.completeStep("test.a.things", "one", "name", window, pane, Map.of("name", " "))).isEqualTo("error:Give it a name");
            assertThat(host.completeStep("test.a.things", "one", "name", window, pane, Map.of("name", "x"))).isEqualTo("reopen:test.b.other:row-x:x");
            host.executeInScope("test.a.things", "one", "paste", window, pane);
            assertThat(things.log).contains("execute:one:paste:t1");
            assertThat(host.sent(pane)).containsExactly("paste:One");
            assertThat(host.invoke("test.a.open", window, pane)).isTrue();
            assertThat(host.paletteOpens()).containsExactly(window + " test.a.things - -");
            host.stopAll();
            assertThat(host.scopes()).isEmpty();
        }
    }

    @Test void registrationIsGatedNamespacedAndBoundToOwnActionsAndNoticesAndTheEditorAreRecorded() {
        try (var host = new FakePluginHost()) {
            var bare = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.bare", "Bare", "1.0.0", Set.of()), Set.of(), Set.of(), bare::set);
            assertThatThrownBy(() -> bare.get().palette().register(new Things())).isInstanceOf(MissingCapabilityException.class);
            var a = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.a", "A", "1.0.0", Set.of(Capabilities.PALETTE_CONTRIBUTE)), Set.of(), Set.of(), a::set);
            assertThatIllegalArgumentException().as("the shortcut action must exist").isThrownBy(() -> a.get().palette().register(new Things()));
            a.get().actions().register(ActionSpec.of("test.a.open", "Open"), invoked -> { });
            Subscription first = a.get().palette().register(new Things());
            assertThatIllegalArgumentException().as("duplicate id").isThrownBy(() -> a.get().palette().register(new Things()));
            var b = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.b", "B", "1.0.0", Set.of(Capabilities.PALETTE_CONTRIBUTE)), Set.of(), Set.of(), b::set);
            assertThatIllegalArgumentException().as("another plugin's namespace").isThrownBy(() -> b.get().palette().register(new Things()));
            first.close();
            assertThat(host.scopes()).isEmpty();
            a.get().notices().error("It broke");
            a.get().platform().openInEditor(Path.of("/tmp/x.toml"));
            assertThat(host.notices()).containsExactly("test.a: It broke");
            assertThat(host.openedInEditor()).containsExactly(Path.of("/tmp/x.toml"));
        }
    }
}
```

`FakeTerminalsTest.info(String)` is the existing package-private helper at its line 20 (it builds a `PaneInfo`); if it is private, make it package-private.

`jasper-app/src/test/java/dev/jasper/app/plugins/HostedPaletteTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.palette.PaletteContext;
import dev.jasper.app.palette.PaletteTarget;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteStep;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

class HostedPaletteTest {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste"), BOOM = new PaletteVerb("boom", "Boom");
    final List<String> notices = new CopyOnWriteArrayList<>();
    final List<Path> edited = new CopyOnWriteArrayList<>();
    Contributions contributions = onEdtValue(Contributions::new);
    TerminalFixture fixture = onEdtValue(TerminalFixture::new);
    PluginHost host;

    PluginHost host() throws Exception {
        Path data = Files.createTempDirectory("jasper-palette");
        host = onEdtValue(() -> new PluginHost(new PluginHost.Environment(javax.swing.SwingUtilities::invokeLater,
            javax.swing.SwingUtilities::isEventDispatchThread, data::resolve, id -> Map.of(), (key, message) -> { },
            Duration.ofMillis(200), contributions, () -> true, AppContractTest.headlessWindows(), fixture.registry,
            notices::add, edited::add)));
        return host;
    }

    /** Stops every plugin the way the contract harness does and waits for their background work. */
    void stopAll() {
        var pending = new AtomicReference<List<java.util.concurrent.CompletableFuture<?>>>(List.of());
        onEdt(() -> pending.set(host.stop()));
        java.util.concurrent.CompletableFuture.allOf(pending.get().toArray(java.util.concurrent.CompletableFuture[]::new)).join();
    }

    @AfterEach void stop() { if (host != null) stopAll(); }

    /** A scope whose "boom" verb throws everywhere it can. */
    static final class Flaky implements PaletteScope {
        final List<String> log = new ArrayList<>();
        @Override public ScopeSpec spec() { return ScopeSpec.of("test.a.flaky", "Flaky", "Search", List.of(PASTE, BOOM)).withAliases(List.of("fl")); }
        @Override public PaletteResults search(String query, PaletteQuery context) {
            if (query.equals("boom")) throw new IllegalStateException("search failed");
            log.add("search:" + query + ":" + context.target().map(pane -> pane.id().toString()).orElse("-"));
            return new PaletteResults(List.of(PaletteRow.of("one", "One").withToken("t1"), PaletteRow.of("two", "Two")), Optional.of("Recent"), Optional.of("two"));
        }
        @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
            if (verb.equals(BOOM)) throw new IllegalStateException("available failed");
            return true;
        }
        @Override public Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
            if (verb.equals(BOOM)) throw new IllegalStateException("step failed");
            return row.id().equals("two") ? Optional.of(new PaletteStep("Two", List.of(new PaletteStep.Field("f", "F", "")),
                (values, done) -> done.accept(PaletteStep.Result.reopen("jasper.commands", Optional.of("new_tab"), Optional.empty())))) : Optional.empty();
        }
        @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
            if (verb.equals(BOOM)) throw new IllegalStateException("execute failed");
            log.add("execute:" + row.id() + ":" + row.token());
        }
        @Override public dev.jasper.sdk.Subscription onChanged(Runnable listener) { return () -> { }; }
    }

    @Test void aContributedScopeIsAdaptedContainedAndHandsThePluginItsOwnRowsBack() throws Exception {
        PluginHost host = host();
        var flaky = new Flaky();
        var context = new AtomicReference<PluginContext>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo("test.a", "A", "1.0.0", Set.of(Capabilities.PALETTE_CONTRIBUTE)), Set.of(), Set.of(),
            Set.of(getClass().getPackageName()), getClass().getClassLoader(), () -> c -> { context.set(c); c.palette().register(flaky); })));
        UUID window = onEdtValue(fixture::addWindow), tab = onEdtValue(() -> fixture.addTab(window, "t"));
        UUID pane = onEdtValue(() -> fixture.addPane(tab, "zsh", Path.of("/src")));
        onEdt(() -> {
            var scope = contributions.scopes().getFirst();
            assertThat(scope.id()).isEqualTo("test.a.flaky");
            assertThat(scope.aliases()).containsExactly("fl");
            assertThat(scope.verbs()).extracting(dev.jasper.app.palette.PaletteVerb::id).containsExactly("paste", "boom");
            var target = new PaletteTarget(text -> { }, () -> { }, Optional::empty, () -> "", () -> true, Optional.of(window), Optional.of(pane));
            var ctx = new PaletteContext(true, target, 5);
            var results = scope.search("on", ctx);
            assertThat(results.sectionLabel()).isEqualTo("Recent");
            assertThat(results.initialSelectionId()).isEqualTo("two");
            assertThat(results.rows()).extracting(dev.jasper.app.palette.PaletteRow::id).containsExactly("one", "two");
            assertThat(results.rows().getFirst().token()).isInstanceOf(PaletteRow.class);
            assertThat(flaky.log).containsExactly("search:on:" + pane);
            assertThat(scope.search("boom", ctx).rows()).as("a failing search is contained").isEmpty();
            var one = results.rows().getFirst(); var two = results.rows().get(1);
            var paste = scope.verbs().getFirst(); var boom = scope.verbs().get(1);
            assertThat(scope.available(one, paste, ctx)).isTrue();
            assertThat(scope.available(one, boom, ctx)).as("contained").isFalse();
            assertThat(scope.step(one, paste, ctx)).isNull();
            assertThat(scope.step(one, boom, ctx)).as("contained").isNull();
            var step = scope.step(two, paste, ctx);
            assertThat(step.title()).isEqualTo("Two");
            var result = new AtomicReference<dev.jasper.app.palette.PaletteStep.Result>();
            step.complete().accept(Map.of("f", "v"), result::set);
            assertThat(result.get().reopenScopeId()).isEqualTo("jasper.commands");
            assertThat(result.get().reopenRowId()).isEqualTo("new_tab");
            scope.execute(one, paste, ctx);
            scope.execute(one, boom, ctx);
            assertThat(flaky.log).contains("execute:one:t1");
            assertThat(scope.search("x", new PaletteContext(true, PaletteTarget.none(), 5)).rows()).as("no window, no rows").isEmpty();
            context.get().palette().open(context.get().terminals().window(window).orElseThrow(), "test.a.flaky", Optional.of("q"), Optional.empty());
        });
        List<Contributions.PaletteRequest> requests = new ArrayList<>();
        onEdt(() -> contributions.onPaletteRequest(requests::add));
        onEdt(() -> context.get().palette().open(context.get().terminals().window(window).orElseThrow(), "test.a.flaky", Optional.of("q"), Optional.of("one")));
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.windowId()).isEqualTo(window);
            assertThat(request.query()).contains("q");
            assertThat(request.rowId()).contains("one");
        });
        stopAll();
        assertThat(onEdtValue(contributions::scopes)).as("teardown removes the scope").isEmpty();
    }

    @Test void registrationIsGatedAndNamespacedAndNoticesAndTheEditorReachTheApplication() throws Exception {
        PluginHost host = host();
        var failures = new AtomicReference<Throwable>();
        var context = new AtomicReference<PluginContext>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo("test.bare", "Bare", "1.0.0", Set.of()), Set.of(), Set.of(),
            Set.of(getClass().getPackageName()), getClass().getClassLoader(), () -> c -> {
                context.set(c);
                try { c.palette().register(new Flaky()); } catch (RuntimeException failure) { failures.set(failure); }
            })));
        assertThat(failures.get()).isInstanceOf(MissingCapabilityException.class);
        var other = new AtomicReference<PluginContext>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo("test.b", "B", "1.0.0", Set.of(Capabilities.PALETTE_CONTRIBUTE)), Set.of(), Set.of(),
            Set.of(getClass().getPackageName()), getClass().getClassLoader(), () -> other::set)));
        onEdt(() -> assertThatIllegalArgumentException().as("test.a.flaky is not in test.b's namespace")
            .isThrownBy(() -> other.get().palette().register(new Flaky())));
        onEdt(() -> other.get().actions().register(ActionSpec.of("test.b.open", "Open"), invoked -> { }));
        onEdt(() -> assertThatIllegalArgumentException().as("a shortcut action must be the plugin's own").isThrownBy(() -> other.get().palette().register(new PaletteScope() {
            @Override public ScopeSpec spec() { return ScopeSpec.of("test.b.s", "S", "Search", List.of(PASTE)).withShortcutActionId("test.a.open"); }
            @Override public PaletteResults search(String query, PaletteQuery context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) { }
            @Override public dev.jasper.sdk.Subscription onChanged(Runnable listener) { return () -> { }; }
        })));
        onEdt(() -> { other.get().notices().error("Broken"); other.get().platform().openInEditor(Path.of("/tmp/s.toml")); });
        for (int i = 0; i < 50 && edited.isEmpty(); i++) Thread.sleep(20);
        onEdt(() -> { });
        assertThat(notices).containsExactly("B: Broken");
        assertThat(edited).containsExactly(Path.of("/tmp/s.toml"));
    }
}
```

`TerminalFixture.addWindow()` and `addTab(UUID, String)` are the existing fixture methods behind the harness's `addTerminalWindow`/`addTerminalTab`; use their actual names (read the fixture). `HostedPlugin`'s constructor is the one `AppContractTest.start` uses.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests '*FakePaletteTest' :jasper-app:test --tests '*HostedPaletteTest' -q`
Expected: compilation failure (`palette()` is not in `PluginContext`).

- [ ] **Step 3: The SDK context**

`PluginContext`: add three accessors with the same Javadoc shape as its neighbours:

```java
    /** @return the command palette; registering needs {@code palette.contribute} */
    Palette palette();

    /** @return error notices shown to the user */
    Notices notices();

    /** @return desktop integration such as opening a file in the editor */
    Platform platform();
```

(imports `dev.jasper.sdk.palette.Palette`, `dev.jasper.sdk.ui.Notices`, `dev.jasper.sdk.ui.Platform`).

- [ ] **Step 4: The application**

`jasper-app/src/main/java/dev/jasper/app/plugins/HostedPalette.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.palette.PaletteContext;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.Palette;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteStep;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Icon;

/**
 * One plugin's palette scopes, adapted onto the application's scope contract. Every call into the
 * plugin is contained: a failing scope shows no rows, an unavailable row, no step or nothing done.
 * The plugin's own row travels as the app row's token and comes back to the plugin unchanged.
 */
final class HostedPalette implements Palette {
    private final String pluginId;
    private final CapabilityGate gate;
    private final Contributions model;
    private final Containment containment;
    private final HostedUi ui;
    private final HostedTerminals terminals;
    private boolean audited;

    HostedPalette(String pluginId, CapabilityGate gate, Contributions model, Containment containment, HostedUi ui, HostedTerminals terminals) {
        this.pluginId = pluginId; this.gate = gate; this.model = model; this.containment = containment; this.ui = ui; this.terminals = terminals;
    }

    private void requireContribute() {
        gate.require(Capabilities.PALETTE_CONTRIBUTE);
        if (!audited) { audited = true; gate.audit(Capabilities.PALETTE_CONTRIBUTE, "used the command palette"); }
    }

    @Override public Subscription register(PaletteScope scope) {
        ui.guard("register");
        Objects.requireNonNull(scope, "scope");
        requireContribute();
        ScopeSpec spec = Objects.requireNonNull(scope.spec(), "spec");
        if (!spec.id().startsWith(pluginId + "."))
            throw new IllegalArgumentException("A scope id must start with " + pluginId + ".: " + spec.id());
        spec.shortcutActionId().ifPresent(ui::requireOwn);
        dev.jasper.app.lifecycle.Subscription added = model.addScope(new Adapted(scope, spec));
        return ui.tracked(added::close);
    }

    @Override public void open(WindowHandle window, String scopeId, Optional<String> query, Optional<String> rowId) {
        ui.guard("open");
        Objects.requireNonNull(window, "window");
        requireContribute();
        model.requestPalette(new Contributions.PaletteRequest(window.id(), Objects.requireNonNull(scopeId, "scopeId"),
            Objects.requireNonNull(query, "query"), Objects.requireNonNull(rowId, "rowId")));
    }

    private PaletteQuery query(PaletteContext context) {
        var windowId = context.target().windowId().orElseThrow(() -> new IllegalStateException("The palette has no window"));
        return new PaletteQuery(terminals.windowHandle(windowId), context.target().paneId().map(terminals::paneHandle),
            context.maxResults(), context.macOs());
    }

    private static dev.jasper.app.palette.PaletteRow appRow(PaletteRow row) {
        return new dev.jasper.app.palette.PaletteRow(row.id(), row.title(), row.detail().orElse(null), row.tag().orElse(null),
            row.icon().orElse(null), row.enabled(), row);
    }

    private static dev.jasper.app.palette.PaletteStep.Result appResult(PaletteStep.Result result) {
        if (result.error().isPresent()) return dev.jasper.app.palette.PaletteStep.Result.error(result.error().get());
        if (result.reopenScopeId().isPresent())
            return dev.jasper.app.palette.PaletteStep.Result.reopen(result.reopenScopeId().get(), result.reopenRowId().orElse(null), result.reopenQuery().orElse(null));
        return dev.jasper.app.palette.PaletteStep.Result.done();
    }

    /** The application's view of one plugin scope. */
    private final class Adapted implements dev.jasper.app.palette.PaletteScope {
        private final PaletteScope scope;
        private final ScopeSpec spec;
        private final List<dev.jasper.app.palette.PaletteVerb> verbs;

        Adapted(PaletteScope scope, ScopeSpec spec) {
            this.scope = scope; this.spec = spec;
            this.verbs = spec.verbs().stream().map(verb -> new dev.jasper.app.palette.PaletteVerb(verb.id(), verb.label())).toList();
        }

        @Override public String id() { return spec.id(); }
        @Override public String label() { return spec.label(); }
        @Override public Icon icon() { return spec.icon().orElse(null); }
        @Override public String description() { return spec.description(); }
        @Override public String placeholder() { return spec.placeholder(); }
        @Override public List<String> aliases() { return spec.aliases(); }
        @Override public List<dev.jasper.app.palette.PaletteVerb> verbs() { return verbs; }
        @Override public boolean monospaceRows() { return spec.monospaceRows(); }
        @Override public Optional<String> shortcutActionId() { return spec.shortcutActionId(); }

        private Optional<PaletteVerb> own(dev.jasper.app.palette.PaletteVerb verb) {
            return spec.verbs().stream().filter(candidate -> candidate.id().equals(verb.id())).findFirst();
        }

        private static Optional<PaletteRow> own(dev.jasper.app.palette.PaletteRow row) {
            return row.token() instanceof PaletteRow original ? Optional.of(original) : Optional.empty();
        }

        /** Runs {@code call} inside containment; {@code fallback} when it throws. */
        private <T> T contained(String what, T fallback, Callable<T> call) {
            var result = new AtomicReference<>(fallback);
            containment.attempt(pluginId, "scope " + spec.id() + " " + what, () -> { result.set(call.call()); return null; });
            return result.get();
        }

        @Override public void activated(PaletteContext context) {
            contained("activated", null, () -> { scope.activated(query(context)); return null; });
        }

        @Override public dev.jasper.app.palette.PaletteResults search(String query, PaletteContext context) {
            PaletteResults results = contained("search", PaletteResults.none(), () -> scope.search(query, query(context)));
            var rows = new ArrayList<dev.jasper.app.palette.PaletteRow>();
            for (PaletteRow row : results.rows()) {
                if (rows.size() == context.maxResults()) break;
                rows.add(appRow(row));
            }
            return new dev.jasper.app.palette.PaletteResults(rows, results.sectionLabel().orElse(null), results.initialSelectionId().orElse(null));
        }

        @Override public boolean available(dev.jasper.app.palette.PaletteRow row, dev.jasper.app.palette.PaletteVerb verb, PaletteContext context) {
            Optional<PaletteRow> original = own(row); Optional<PaletteVerb> chosen = own(verb);
            if (original.isEmpty() || chosen.isEmpty()) return false;
            return contained("available", false, () -> scope.available(original.get(), chosen.get(), query(context)));
        }

        @Override public dev.jasper.app.palette.PaletteStep step(dev.jasper.app.palette.PaletteRow row, dev.jasper.app.palette.PaletteVerb verb, PaletteContext context) {
            Optional<PaletteRow> original = own(row); Optional<PaletteVerb> chosen = own(verb);
            if (original.isEmpty() || chosen.isEmpty()) return null;
            Optional<PaletteStep> step = contained("step", Optional.empty(), () -> scope.step(original.get(), chosen.get(), query(context)));
            return step.map(this::appStep).orElse(null);
        }

        private dev.jasper.app.palette.PaletteStep appStep(PaletteStep step) {
            List<dev.jasper.app.palette.PaletteStep.Field> fields = step.fields().stream()
                .map(field -> new dev.jasper.app.palette.PaletteStep.Field(field.name(), field.label(), field.prefill())).toList();
            return new dev.jasper.app.palette.PaletteStep(step.title(), fields, (values, done) -> {
                // A completion that throws before answering would leave the form waiting; answer for it.
                if (!containment.run(pluginId, "scope " + spec.id() + " step", () -> step.complete().accept(values, result -> done.accept(appResult(result)))))
                    done.accept(dev.jasper.app.palette.PaletteStep.Result.error("The plugin could not complete this step."));
            });
        }

        @Override public void execute(dev.jasper.app.palette.PaletteRow row, dev.jasper.app.palette.PaletteVerb verb, PaletteContext context) {
            Optional<PaletteRow> original = own(row); Optional<PaletteVerb> chosen = own(verb);
            if (original.isEmpty() || chosen.isEmpty()) return;
            containment.run(pluginId, "scope " + spec.id() + " execute", () -> scope.execute(original.get(), chosen.get(), query(context)));
        }

        @Override public dev.jasper.app.lifecycle.Subscription onChanged(Runnable listener) {
            Subscription registration = contained("onChanged", null, () -> scope.onChanged(listener));
            return new dev.jasper.app.lifecycle.Subscription(() -> { if (registration != null) registration.close(); });
        }
    }
}
```

`PluginHost.Environment`: append `Consumer<String> notice, Consumer<Path> editor` (imports `java.nio.file.Path` is present; `Consumer` too). Javadoc the record if it has one.

`HostedContext`: add field `final HostedPalette palette;`, constructed after `ui`: `this.palette = new HostedPalette(id, gate, host.environment.contributions(), host.containment, ui, terminals);`, and

```java
    @Override public Palette palette() { return palette; }

    @Override public Notices notices() {
        return message -> {
            requireOpen();
            Objects.requireNonNull(message, "message");
            String shown = hosted.info().name() + ": " + message;
            host.environment.ui().accept(() -> host.environment.notice().accept(shown));
        };
    }

    @Override public Platform platform() {
        return file -> {
            requireOpen();
            Objects.requireNonNull(file, "file");
            background().execute(() -> {
                try { host.environment.editor().accept(file); }
                catch (RuntimeException failure) {
                    log().log(System.Logger.Level.WARNING, "Could not open " + file + " in an editor", failure);
                    notices().error("Could not open " + file.getFileName() + " in an editor.");
                }
            });
        };
    }
```

(imports `dev.jasper.sdk.palette.Palette`, `dev.jasper.sdk.ui.Notices`, `dev.jasper.sdk.ui.Platform`). The test expects the notice as `"<plugin name>: <message>"`.

`PluginRuntime`: add fields `private final Consumer<String> notice; private final Consumer<Path> editor;`, an eight-argument constructor that sets them, and make the existing six-argument one delegate with `message -> LOG.log(System.Logger.Level.WARNING, "Plugin notice: " + message)` and `new dev.jasper.app.platform.ConfigEditor()::open`. Pass `notice, editor` as the two new `Environment` arguments.

`JasperApplication`: construct the runtime with the eight-argument form, passing `this::notice` and `new ConfigEditor()::open` (the class already imports `ConfigEditor` or add it), and add

```java
    /** A plugin's error notice: the window the user used last, or the log when there is none. */
    void notice(String message) {
        TerminalWindow target = lastActive != null && windows.contains(lastActive) ? lastActive : windows.stream().findFirst().orElse(null);
        if (target == null) LOG.log(System.Logger.Level.WARNING, "Plugin notice with no window: " + message);
        else target.content().onError.accept(message);
    }
```

`DescriptorParser.CAPABILITIES` gains `"palette.contribute"`. `pluginmanager.Capabilities.describe` gains `case "palette.contribute" -> "Add scopes to the command palette and open it";`.

`AppContractTest.host(...)`: the `Environment` construction gains `message -> { }, path -> { }` for now (Task 5 replaces both with recording lists).

- [ ] **Step 5: The testkit**

`jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePalette.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.Palette;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Objects;
import java.util.Optional;

/** One plugin's palette registrations; the host keeps the scopes and answers queries through them. */
final class FakePalette implements Palette {
    /** A registered scope and the context that owns it. */
    record Registered(FakePluginContext context, PaletteScope scope, ScopeSpec spec) { }

    private final FakePluginHost host;
    private final FakePluginContext context;

    FakePalette(FakePluginHost host, FakePluginContext context) { this.host = host; this.context = context; }

    private void requireCapability() {
        if (!context.plugin().capabilities().contains(Capabilities.PALETTE_CONTRIBUTE))
            throw new MissingCapabilityException(context.plugin().id(), Capabilities.PALETTE_CONTRIBUTE);
    }

    @Override public Subscription register(PaletteScope scope) {
        context.requireOpen();
        Objects.requireNonNull(scope, "scope");
        requireCapability();
        ScopeSpec spec = Objects.requireNonNull(scope.spec(), "spec");
        if (!spec.id().startsWith(context.plugin().id() + "."))
            throw new IllegalArgumentException("A scope id must start with " + context.plugin().id() + ".: " + spec.id());
        spec.shortcutActionId().ifPresent(context.ui::requireOwn);
        if (host.scopes.containsKey(spec.id())) throw new IllegalArgumentException("Scope already contributed: " + spec.id());
        var registered = new Registered(context, scope, spec);
        host.scopes.put(spec.id(), registered);
        return () -> host.scopes.remove(spec.id(), registered);
    }

    @Override public void open(WindowHandle window, String scopeId, Optional<String> query, Optional<String> rowId) {
        context.requireOpen();
        Objects.requireNonNull(window, "window"); Objects.requireNonNull(scopeId, "scopeId");
        requireCapability();
        host.paletteOpens.add(window.id() + " " + scopeId + " " + query.orElse("-") + " " + rowId.orElse("-"));
    }

    /** Drops this plugin's scopes at teardown. */
    void closeAll() { host.scopes.values().removeIf(registered -> registered.context() == context); }
}
```

`FakePluginContext`: field `final FakePalette palette;` set in the constructor as `new FakePalette(host, this)`, and

```java
    @Override public dev.jasper.sdk.palette.Palette palette() { return palette; }
    @Override public dev.jasper.sdk.ui.Notices notices() { return message -> { requireOpen(); host.notices.add(info.id() + ": " + Objects.requireNonNull(message, "message")); }; }
    @Override public dev.jasper.sdk.ui.Platform platform() { return file -> { requireOpen(); host.openedInEditor.add(Objects.requireNonNull(file, "file")); }; }
```

`FakePluginHost`: fields

```java
    final Map<String, FakePalette.Registered> scopes = new LinkedHashMap<>();
    final List<String> paletteOpens = new ArrayList<>();
    final List<String> notices = new ArrayList<>();
    final List<Path> openedInEditor = new ArrayList<>();
    private final Map<String, Map<String, PaletteRow>> lastRows = new HashMap<>();
```

in `teardown(...)`, next to `context.ui.closeAll()`: `context.palette.closeAll();`. And these public methods, each with a Javadoc comment in the file's style:

```java
    /** Registered scopes as {@code id|label|verbIds}, in registration order. */
    public List<String> scopes() {
        return scopes.values().stream().map(registered -> registered.spec().id() + "|" + registered.spec().label() + "|"
            + String.join(",", registered.spec().verbs().stream().map(PaletteVerb::id).toList())).toList();
    }

    private FakePalette.Registered scope(String scopeId) {
        FakePalette.Registered registered = scopes.get(scopeId);
        if (registered == null) throw new IllegalArgumentException("No such scope: " + scopeId);
        return registered;
    }

    private PaletteQuery query(FakePalette.Registered registered, UUID windowId, UUID paneIdOrNull) {
        return new PaletteQuery(registered.context().terminals.windowHandle(windowId),
            Optional.ofNullable(paneIdOrNull).map(registered.context().terminals::paneHandle), 5, true);
    }

    private PaletteRow row(String scopeId, String rowId) {
        PaletteRow row = lastRows.getOrDefault(scopeId, Map.of()).get(rowId);
        if (row == null) throw new IllegalArgumentException("Search the scope first; no such row: " + rowId);
        return row;
    }

    private static PaletteVerb verb(FakePalette.Registered registered, String verbId) {
        return registered.spec().verbs().stream().filter(verb -> verb.id().equals(verbId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No such verb: " + verbId));
    }

    /** Searches a scope as the palette would, as {@code rowId|title|enabled} lines; later row lookups use this answer. */
    public List<String> searchScope(String scopeId, String query, UUID windowId, UUID paneIdOrNull) {
        var registered = scope(scopeId);
        PaletteResults results = registered.scope().search(query, query(registered, windowId, paneIdOrNull));
        var rows = new LinkedHashMap<String, PaletteRow>();
        for (PaletteRow row : results.rows()) rows.put(row.id(), row);
        lastRows.put(scopeId, rows);
        return results.rows().stream().map(row -> row.id() + "|" + row.title() + "|" + row.enabled()).toList();
    }

    /** {@code available} for a row of the last search. */
    public boolean availableInScope(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull) {
        var registered = scope(scopeId);
        return registered.scope().available(row(scopeId, rowId), verb(registered, verbId), query(registered, windowId, paneIdOrNull));
    }

    /** The title of the step a verb would show first, if any. */
    public Optional<String> stepInScope(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull) {
        var registered = scope(scopeId);
        return registered.scope().step(row(scopeId, rowId), verb(registered, verbId), query(registered, windowId, paneIdOrNull)).map(PaletteStep::title);
    }

    /**
     * Completes the step a verb shows with {@code values}: {@code done}, {@code error:<message>} or
     * {@code reopen:<scopeId>:<rowId or ->:<query or ->}.
     */
    public String completeStep(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull, Map<String, String> values) {
        var registered = scope(scopeId);
        PaletteStep step = registered.scope().step(row(scopeId, rowId), verb(registered, verbId), query(registered, windowId, paneIdOrNull))
            .orElseThrow(() -> new IllegalArgumentException("That verb shows no step"));
        var answer = new java.util.concurrent.atomic.AtomicReference<String>();
        step.complete().accept(Map.copyOf(values), result -> answer.set(describe(result)));
        return Objects.requireNonNull(answer.get(), "the step did not answer");
    }

    static String describe(PaletteStep.Result result) {
        if (result.error().isPresent()) return "error:" + result.error().get();
        if (result.reopenScopeId().isPresent())
            return "reopen:" + result.reopenScopeId().get() + ":" + result.reopenRowId().orElse("-") + ":" + result.reopenQuery().orElse("-");
        return "done";
    }

    /** Runs a verb on a row of the last search, without the availability recheck. */
    public void executeInScope(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull) {
        var registered = scope(scopeId);
        registered.scope().execute(row(scopeId, rowId), verb(registered, verbId), query(registered, windowId, paneIdOrNull));
    }

    /** Every {@code Palette.open} call as {@code windowId scopeId query rowId}, with {@code -} for an absent value. */
    public List<String> paletteOpens() { return List.copyOf(paletteOpens); }

    /** Every error notice as {@code pluginId: message}. */
    public List<String> notices() { return List.copyOf(notices); }

    /** Every file a plugin asked to open in the editor. */
    public List<Path> openedInEditor() { return List.copyOf(openedInEditor); }
```

with imports `dev.jasper.sdk.palette.{PaletteQuery,PaletteResults,PaletteRow,PaletteStep,PaletteVerb}`, `java.nio.file.Path`, `java.util.HashMap`.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests '*HostedPaletteTest' --tests '*AppContractTest' --tests '*PluginRuntimeTest' --tests '*JasperApplication*' -q` and the sample module's `test`.
Expected: PASS (the sample plugin does not implement `PluginContext`, so it compiles unchanged).

Run the AGENTS.md hygiene check on the new files. Expected: no output.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add -A jasper-sdk jasper-sdk-testkit jasper-app
git commit -m "feat: host plugin palette scopes, notices and the editor in the app and the testkit

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 5: Contract cases for the palette, notices, the editor and the shell label

**Files:**
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/{ContractHarness,PluginContractTest}.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`

**Interfaces:**
- Consumes Task 4's `FakePluginHost` methods and the app's `Contributions.scopes()`.
- Produces nine harness methods (below), the same `id|label|verbIds`, `rowId|title|enabled`, `windowId scopeId query rowId` and `done`/`error:`/`reopen:` line formats the fake uses.

- [ ] **Step 1: Extend the harness interface**

Append to `ContractHarness` (Javadoc each in the file's style):

```java
    /** Contributed scopes as {@code id|label|verbIds}, in registration order. */
    List<String> scopes();
    /** Searches a scope from a window and optional pane, as {@code rowId|title|enabled}; later row lookups use this answer. */
    List<String> searchScope(String scopeId, String query, java.util.UUID windowId, java.util.UUID paneIdOrNull);
    boolean availableInScope(String scopeId, String rowId, String verbId, java.util.UUID windowId, java.util.UUID paneIdOrNull);
    /** The step's title, or empty when the verb runs at once. */
    java.util.Optional<String> stepInScope(String scopeId, String rowId, String verbId, java.util.UUID windowId, java.util.UUID paneIdOrNull);
    /** {@code done}, {@code error:<message>} or {@code reopen:<scopeId>:<rowId or ->:<query or ->}. */
    String completeStep(String scopeId, String rowId, String verbId, java.util.UUID windowId, java.util.UUID paneIdOrNull, java.util.Map<String, String> values);
    void executeInScope(String scopeId, String rowId, String verbId, java.util.UUID windowId, java.util.UUID paneIdOrNull);
    /** Every palette open request as {@code windowId scopeId query rowId}, {@code -} for an absent value. */
    List<String> paletteOpens();
    /** Every error notice as {@code <plugin name>: <message>}. */
    List<String> notices();
    List<java.nio.file.Path> openedInEditor();
```

- [ ] **Step 2: Write the contract cases**

Append to `PluginContractTest` (imports: `dev.jasper.sdk.palette.*`, `dev.jasper.sdk.ui.ActionSpec` if missing, `java.nio.file.Path`, `java.util.Map`):

```java
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste"), NAME = new PaletteVerb("name", "Name it");

    /** Two rows; "paste" pastes the title into the target pane, "name" asks for a name and reopens elsewhere. */
    static final class Things implements PaletteScope {
        final String id; final List<String> log = new ArrayList<>();
        Things(String id) { this.id = id; }
        @Override public ScopeSpec spec() { return ScopeSpec.of(id, "Things", "Search things", List.of(PASTE, NAME)); }
        @Override public PaletteResults search(String query, PaletteQuery context) {
            log.add("search:" + query + ":" + context.window().id() + ":" + context.target().map(pane -> pane.id().toString()).orElse("-"));
            return PaletteResults.of(List.of(PaletteRow.of("one", "One").withToken("t1"), PaletteRow.of("two", "Two").withEnabled(false)));
        }
        @Override public Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
            if (!verb.equals(NAME)) return Optional.empty();
            return Optional.of(new PaletteStep("Name " + row.title(), List.of(new PaletteStep.Field("name", "Name", row.title())),
                (values, done) -> done.accept(values.get("name").isBlank() ? PaletteStep.Result.error("Give it a name")
                    : PaletteStep.Result.reopen("test.b.other", Optional.of("row-" + values.get("name")), Optional.of(values.get("name"))))));
        }
        @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
            log.add("execute:" + row.id() + ":" + verb.id() + ":" + row.token());
            context.target().ifPresent(pane -> pane.paste(row.title()));
        }
        @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
    }

    @Test void aPaletteScopeNeedsTheCapabilityAndThePluginsNamespace() {
        var failure = new AtomicReference<Throwable>();
        h.start(info("test.bare"), Set.of(), Set.of(), context -> {
            try { context.palette().register(new Things("test.bare.things")); } catch (RuntimeException caught) { failure.set(caught); }
        });
        assertThat(failure.get()).isInstanceOfSatisfying(MissingCapabilityException.class,
            missing -> assertThat(missing.capability()).isEqualTo(Capabilities.PALETTE_CONTRIBUTE));
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.PALETTE_CONTRIBUTE), Set.of(), Set.of(), a::set);
        h.ui(() -> assertThatIllegalArgumentException().isThrownBy(() -> a.get().palette().register(new Things("test.b.things"))));
        h.ui(() -> assertThatIllegalArgumentException().as("the shortcut action must be the plugin's own")
            .isThrownBy(() -> a.get().palette().register(new PaletteScope() {
                @Override public ScopeSpec spec() { return ScopeSpec.of("test.a.s", "S", "Search", List.of(PASTE)).withShortcutActionId("test.a.open"); }
                @Override public PaletteResults search(String query, PaletteQuery context) { return PaletteResults.none(); }
                @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) { }
                @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
            })));
        h.ui(() -> a.get().palette().register(new Things("test.a.things")));
        assertThat(h.scopes()).containsExactly("test.a.things|Things|paste,name");
        assertThat(h.active("test.bare")).as("a contained registration failure does not fail the plugin").isTrue();
    }

    @Test void aScopeGetsItsOwnRowsAndHandlesBackAndAStepCanReopenAnotherScope() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "t");
        UUID pane = h.addTerminalPane(tab, "zsh", Path.of("/src"));
        var things = new Things("test.a.things");
        h.start(info("test.a", Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT), Set.of(), Set.of(),
            context -> context.palette().register(things));
        assertThat(h.searchScope("test.a.things", "on", window, pane)).containsExactly("one|One|true", "two|Two|false");
        assertThat(things.log).containsExactly("search:on:" + window + ":" + pane);
        assertThat(h.availableInScope("test.a.things", "one", "paste", window, pane)).isTrue();
        assertThat(h.availableInScope("test.a.things", "two", "paste", window, pane)).as("the default follows enabled").isFalse();
        assertThat(h.stepInScope("test.a.things", "one", "paste", window, pane)).isEmpty();
        assertThat(h.stepInScope("test.a.things", "one", "name", window, pane)).contains("Name One");
        assertThat(h.completeStep("test.a.things", "one", "name", window, pane, Map.of("name", " "))).isEqualTo("error:Give it a name");
        assertThat(h.completeStep("test.a.things", "one", "name", window, pane, Map.of("name", "x"))).isEqualTo("reopen:test.b.other:row-x:x");
        h.executeInScope("test.a.things", "one", "paste", window, pane);
        assertThat(things.log).contains("execute:one:paste:t1");
        assertThat(h.sentToPane(pane)).containsExactly("paste:One");
        assertThat(h.searchScope("test.a.things", "", window, null)).hasSize(2);
        assertThat(things.log.getLast()).isEqualTo("search::" + window + ":-");
    }

    @Test void duplicateScopeIdsFailEachRegistrationRemovesOnlyItselfAndAStoppedPluginsScopesVanish() {
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.PALETTE_CONTRIBUTE), Set.of(), Set.of(), a::set);
        var first = new AtomicReference<Subscription>();
        h.ui(() -> {
            first.set(a.get().palette().register(new Things("test.a.things")));
            assertThatIllegalArgumentException().isThrownBy(() -> a.get().palette().register(new Things("test.a.things")));
            a.get().palette().register(new Things("test.a.more"));
        });
        assertThat(h.scopes()).containsExactly("test.a.things|Things|paste,name", "test.a.more|Things|paste,name");
        h.ui(() -> { first.get().close(); first.get().close(); });
        assertThat(h.scopes()).containsExactly("test.a.more|Things|paste,name");
        h.stopAll();
        assertThat(h.scopes()).isEmpty();
    }

    @Test void openingThePaletteNeedsTheCapabilityAndCarriesTheQueryAndRow() {
        UUID window = h.addTerminalWindow();
        var bare = new AtomicReference<PluginContext>();
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.bare", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), bare::set);
        h.start(info("test.a", Capabilities.PALETTE_CONTRIBUTE), Set.of(), Set.of(), context -> {
            a.set(context);
            context.actions().register(ActionSpec.of("test.a.open", "Things…"),
                invoked -> context.palette().open(invoked.window(), "test.a.things", Optional.empty(), Optional.empty()));
            context.palette().register(new Things("test.a.things"));
        });
        h.ui(() -> assertThatThrownBy(() -> bare.get().palette().open(bare.get().terminals().window(window).orElseThrow(), "jasper.commands", Optional.empty(), Optional.empty()))
            .isInstanceOf(MissingCapabilityException.class));
        assertThat(h.invoke("test.a.open", window, null)).isTrue();
        h.ui(() -> a.get().palette().open(a.get().terminals().window(window).orElseThrow(), "jasper.commands", Optional.of("new"), Optional.of("new_tab")));
        assertThat(h.paletteOpens()).containsExactly(window + " test.a.things - -", window + " jasper.commands new new_tab");
    }

    @Test void noticesAndTheEditorReachTheHostAndAPaneKnowsItsShell() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "t");
        UUID pane = h.addTerminalPane(tab, "zsh", Path.of("/src"));
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), a::set);
        h.ui(() -> {
            a.get().notices().error("It broke");
            a.get().platform().openInEditor(Path.of("/tmp/x.toml"));
            assertThat(a.get().terminals().pane(pane).orElseThrow().info().shell()).isEqualTo("zsh");
        });
        h.flush();
        for (int i = 0; i < 100 && h.openedInEditor().isEmpty(); i++) { try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
        assertThat(h.notices()).containsExactly("test.a: It broke");
        assertThat(h.openedInEditor()).containsExactly(Path.of("/tmp/x.toml"));
    }
```

(`info(id)` and `info(id, capabilities…)` create a `PluginInfo` whose name equals its id, so the app's `<name>: <message>` and the fake's `<id>: <message>` agree.) The optional-requirement shape is already covered by the existing `test.optional` case; no new case for it.

- [ ] **Step 3: Implement the fake harness**

In `FakeContractTest`'s anonymous harness, delegate each new method to the host: `scopes()`, `searchScope`, `availableInScope`, `stepInScope`, `completeStep`, `executeInScope`, `paletteOpens()`, `notices()`, `openedInEditor()` (same names on `FakePluginHost`).

- [ ] **Step 4: Implement the app harness**

`AppContractTest`: the seven-argument `host(...)` gains `List<String> notices, List<Path> edited` parameters and passes `notices::add, edited::add` to the `Environment`; the shorter overloads pass `new CopyOnWriteArrayList<>()` for both, and `newHarness()` creates the two lists it hands in. Add a `Map<String, Map<String, dev.jasper.app.palette.PaletteRow>> lastRows = new HashMap<>()` in `newHarness()` and these harness methods:

```java
            private dev.jasper.app.palette.PaletteScope scope(String scopeId) {
                return contributions.scopes().stream().filter(scope -> scope.id().equals(scopeId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No such scope: " + scopeId));
            }
            private dev.jasper.app.palette.PaletteContext context(UUID windowId, UUID paneIdOrNull) {
                var target = new dev.jasper.app.palette.PaletteTarget(text -> { }, () -> { }, Optional::empty, () -> "", () -> true,
                    Optional.of(windowId), Optional.ofNullable(paneIdOrNull));
                return new dev.jasper.app.palette.PaletteContext(true, target, 5);
            }
            private dev.jasper.app.palette.PaletteRow row(String scopeId, String rowId) {
                var row = lastRows.getOrDefault(scopeId, Map.of()).get(rowId);
                if (row == null) throw new IllegalArgumentException("Search the scope first; no such row: " + rowId);
                return row;
            }
            private static dev.jasper.app.palette.PaletteVerb verb(dev.jasper.app.palette.PaletteScope scope, String verbId) {
                return scope.verbs().stream().filter(verb -> verb.id().equals(verbId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No such verb: " + verbId));
            }
            @Override public List<String> scopes() {
                return onEdtValue(() -> contributions.scopes().stream().map(scope -> scope.id() + "|" + scope.label() + "|"
                    + String.join(",", scope.verbs().stream().map(dev.jasper.app.palette.PaletteVerb::id).toList())).toList());
            }
            @Override public List<String> searchScope(String scopeId, String query, UUID windowId, UUID paneIdOrNull) {
                return onEdtValue(() -> {
                    var results = scope(scopeId).search(query, context(windowId, paneIdOrNull));
                    var rows = new java.util.LinkedHashMap<String, dev.jasper.app.palette.PaletteRow>();
                    for (var row : results.rows()) rows.put(row.id(), row);
                    lastRows.put(scopeId, rows);
                    return results.rows().stream().map(row -> row.id() + "|" + row.title() + "|" + row.enabled()).toList();
                });
            }
            @Override public boolean availableInScope(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull) {
                return onEdtValue(() -> { var scope = scope(scopeId); return scope.available(row(scopeId, rowId), verb(scope, verbId), context(windowId, paneIdOrNull)); });
            }
            @Override public Optional<String> stepInScope(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull) {
                return onEdtValue(() -> { var scope = scope(scopeId);
                    return Optional.ofNullable(scope.step(row(scopeId, rowId), verb(scope, verbId), context(windowId, paneIdOrNull))).map(dev.jasper.app.palette.PaletteStep::title); });
            }
            @Override public String completeStep(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull, Map<String, String> values) {
                return onEdtValue(() -> {
                    var scope = scope(scopeId);
                    var step = scope.step(row(scopeId, rowId), verb(scope, verbId), context(windowId, paneIdOrNull));
                    if (step == null) throw new IllegalArgumentException("That verb shows no step");
                    var answer = new AtomicReference<String>();
                    step.complete().accept(Map.copyOf(values), result -> answer.set(
                        result.error() != null ? "error:" + result.error()
                            : result.reopenScopeId() != null ? "reopen:" + result.reopenScopeId() + ":" + (result.reopenRowId() == null ? "-" : result.reopenRowId())
                                + ":" + (result.reopenQuery() == null ? "-" : result.reopenQuery())
                            : "done"));
                    return java.util.Objects.requireNonNull(answer.get(), "the step did not answer");
                });
            }
            @Override public void executeInScope(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull) {
                onEdt(() -> { var scope = scope(scopeId); scope.execute(row(scopeId, rowId), verb(scope, verbId), context(windowId, paneIdOrNull)); });
            }
            @Override public List<String> paletteOpens() { return List.copyOf(opens); }
            @Override public List<String> notices() { return List.copyOf(notices); }
            @Override public List<Path> openedInEditor() { return List.copyOf(edited); }
```

where `opens` is a `CopyOnWriteArrayList<String>` filled by `onEdt(() -> contributions.onPaletteRequest(request -> opens.add(request.windowId() + " " + request.scopeId() + " " + request.query().orElse("-") + " " + request.rowId().orElse("-"))))` in `newHarness()`, right after the activity-log subscription.

- [ ] **Step 5: Run both suites**

Run: `./gradlew :jasper-sdk-testkit:test --tests '*FakeContractTest' :jasper-app:test --tests '*AppContractTest' -q`
Expected: PASS, 35 cases each (`python3 $S/results.py jasper-app AppContractTest` and `… jasper-sdk-testkit FakeContractTest` report `tests 35`). Rerun `AppContractTest` three times for stability.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A jasper-sdk-testkit jasper-app
git commit -m "feat: hold palette scopes, notices and the editor in the app and the testkit to one contract

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 6: Sample plugin scope, documentation and final verification

**Files:**
- Modify: `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java`, `plugins/sample/src/main/resources/plugin.toml`, `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `jasper-sdk/README.md`, `docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md`, `AGENTS.md`, `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: Write the failing sample test**

Append to `SamplePluginTest`:

```java
    @Test void thePaletteDemoContributesAGreetingsScopeThatPastesIntoTheOriginPane() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "build");
            UUID pane = host.addTerminalPane(tab, new PaneInfo("zsh", Optional.of(Path.of("/src")), Optional.empty(), 80, 24, true,
                SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.empty(), "zsh"));
            host.setConfig("dev.jasper.sample", Map.of("demo_scope", true));
            host.start(new PluginInfo("dev.jasper.sample", "Sample", "0.1.0", Set.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT)),
                Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.scopes()).containsExactly("dev.jasper.sample.greetings|Greetings|paste,paste_run");
            assertThat(host.searchScope("dev.jasper.sample.greetings", "", window, pane)).hasSize(3);
            assertThat(host.searchScope("dev.jasper.sample.greetings", "morn", window, pane)).containsExactly("greeting.0|good morning|true");
            assertThat(host.availableInScope("dev.jasper.sample.greetings", "greeting.0", "paste", window, null)).as("needs an origin pane").isFalse();
            host.executeInScope("dev.jasper.sample.greetings", "greeting.0", "paste_run", window, pane);
            assertThat(host.sent(pane)).as("the fake records paste: and write: lines").containsExactly("paste:echo 'good morning'", "write:\r");
            assertThat(host.invoke("dev.jasper.sample.greetings.open", window, pane)).isTrue();
            assertThat(host.paletteOpens()).containsExactly(window + " dev.jasper.sample.greetings - -");
        }
    }
```

(`"\r"` is the Java escape for carriage return; never paste the raw character.)

- [ ] **Step 2: Run it to verify it fails**

Run the sample module's `test` task with `--tests '*SamplePluginTest*'`.
Expected: FAIL (`host.scopes()` is empty; `failures()` may list a missing capability).

- [ ] **Step 3: The sample plugin**

`SamplePlugin`: add constants `private static final String GREETINGS = "dev.jasper.sample.greetings"; private static final String GREETINGS_OPEN = "dev.jasper.sample.greetings.open";`, imports `dev.jasper.sdk.Subscription`, `dev.jasper.sdk.palette.{PaletteQuery,PaletteResults,PaletteRow,PaletteScope,PaletteVerb,ScopeSpec}`, `java.util.ArrayList`, `java.util.Locale`; in `start()` add, after the `demo_session` line:

```java
        if (context.config().bool("demo_scope").orElse(false)) installPaletteDemo(context);
```

and the method, between the session demo and the activity demo:

```java
    // example:pluginpalette:start
    private static void installPaletteDemo(PluginContext context) {
        if (!context.plugin().capabilities().containsAll(List.of(Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT))) {
            context.log().log(System.Logger.Level.INFO, "The palette demo needs palette.contribute and terminal.inject");
            return;
        }
        // Register the action first: the scope names it, and its shortcut then reaches the scope both ways.
        // Closed, this handler opens the palette on the scope; open, the palette switches to it or dismisses.
        context.actions().register(ActionSpec.of(GREETINGS_OPEN, "Sample Greetings…").withDefaultBinding("cmd+alt+g"), invoked ->
            context.palette().open(invoked.window(), GREETINGS, Optional.empty(), Optional.empty()));
        List<String> greetings = List.of("good morning", "hello", "hi there");
        context.palette().register(new PaletteScope() {
            @Override public ScopeSpec spec() {
                return ScopeSpec.of(GREETINGS, "Greetings", "Search greetings, or > to switch scope",
                        List.of(new PaletteVerb("paste", "Paste"), new PaletteVerb("paste_run", "Paste and run")))
                    .withAliases(List.of("greet")).withShortcutActionId(GREETINGS_OPEN);
            }
            // Search runs on the UI thread for every keystroke: rank what is already in memory, never read files here.
            @Override public PaletteResults search(String query, PaletteQuery palette) {
                String needle = query.strip().toLowerCase(Locale.ROOT);
                var rows = new ArrayList<PaletteRow>();
                for (String greeting : greetings)
                    if (greeting.contains(needle)) rows.add(PaletteRow.of("greeting." + rows.size(), greeting).withToken(greeting));
                return new PaletteResults(rows, needle.isEmpty() ? Optional.of("Greetings") : Optional.empty(), Optional.empty());
            }
            // The row comes back exactly as returned, token included. The target is the pane the palette was opened
            // from; pasting into it needs terminal.inject, like any other pane.
            @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery palette) { return palette.target().isPresent(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery palette) {
                palette.target().ifPresent(pane -> {
                    pane.paste("echo '" + row.token() + "'");
                    if (verb.id().equals("paste_run")) pane.sendText("\r");
                });
            }
            @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
        });
    }
    // example:pluginpalette:end
```

`plugin.toml`: capabilities gain `"palette.contribute"`; the description mentions `demo_scope`. If `BundledSamplePluginTest` or the plugin-manager tests assert the sample's capability list, extend the expectation.

- [ ] **Step 4: Run the sample tests**

Run the sample module's `test` task and `./gradlew :jasper-app:test --tests '*BundledSamplePluginTest' --tests '*PluginManager*' -q`.
Expected: PASS.

- [ ] **Step 5: Documentation**

`docs/plugin-authoring.md`:
- Re-copy the `plugin` example from `SamplePlugin.start()` (it gained the `demo_scope` line).
- Insert a section `## Palette scopes` between "Providing a session" and "Rules that matter" with the marker for the `pluginpalette` example (the HTML comment the other sections use, followed by a `java` fence holding the method text between its start and end markers, stripped of indentation) and this text before it:

  "A scope is one kind of searchable thing in the command palette, beside Commands. Declare `palette.contribute`, register a `PaletteScope` through `context.palette()`, and the scope appears in every window's scope picker under its label and `>alias`. The spec is read once; its id must start with your plugin id. Search, `available`, `step` and `execute` run on the UI thread and must do no I/O: keep an index, refresh it in the background and tell the palette through the `onChanged` listener. The palette shows at most `maxResults` rows and never scrolls. A verb may return a `PaletteStep` (a small form) instead of running at once; its completion answers `done()`, `error(message)` (keeps the form open) or `reopen(scopeId, rowId, query)`, which dismisses and reopens the palette elsewhere, in any registered scope. `Palette.open` does the same from an action; on the scope that is already showing it dismisses instead. A scope that names one of your actions as `shortcutActionId` gets that action's shortcut while the palette is open, exactly as the built-in Cmd+P/R/J behave. The host contains every call: a scope that throws shows no rows, an unavailable row, no step or nothing done, and the palette keeps working."

- After it, two short paragraphs `## Notices and the editor`: "`context.notices().error(message)` shows an error the way the application shows its own, in the window the user is using. `context.platform().openInEditor(file)` opens a file with the user's editor (configured editor, then the desktop, then revealing the file), in the background; a failure is reported as a notice." And note `PaneInfo.shell` in "Terminals and capabilities": "`PaneInfo.shell` is the launcher's label (`zsh`, `fish`) or a provided session's title."
- In "Testing" add: "`host.scopes()`, `searchScope`, `availableInScope`, `stepInScope`, `completeStep` and `executeInScope` drive a contributed scope as the palette would; `paletteOpens()`, `notices()` and `openedInEditor()` record what the plugin asked for."

`docs/sdk-architecture.md`: insert `## Palette scopes` after "Provided sessions":

  "`Contributions.addScope` carries an application `PaletteScope` the way it carries actions; `WindowContributions` registers each one in its window's `ScopeRegistry` on connect and on change, and routes a `PaletteRequest` for its window to `WindowCommandPalette.open(scopeId, query, rowId)`. `PaletteKeyRouter` resolves a stroke bound to a contributed action to the scope naming it as `shortcutActionId`, only while the palette is open; closed, the plugin's own action handler runs and calls `Palette.open`. `plugins.HostedPalette` adapts an SDK scope: contained calls, a `PaletteQuery` built from the target's window and pane ids through the plugin's own handles, and the plugin's row kept as the app row's token so it comes back unchanged. `HostedContext.notices()` reaches the last active window's error handler; `platform().openInEditor` runs the application's `ConfigEditor` on the plugin's executor and reports failure as a notice. `PaneInfo.shell` is `PaneSnapshot.shell`, the pane's launcher label."

  Update "Not yet implemented" to: "Explicit commands in `LocalSpec`; a stronger locality signal than the host name; the History and Snippets scopes as bundled plugins (plan 5b)."

`jasper-sdk/README.md`: add a row `| dev.jasper.sdk.palette | Palette, PaletteScope, ScopeSpec, PaletteVerb, PaletteRow, PaletteResults, PaletteStep, PaletteQuery |` in the package table's style, add `Notices`, `Platform` to the `ui` row, and the version if the README states it.

`docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md` section 14: replace "palette scopes contributed by plugins (the `PaletteScope` seam stays internal until a plugin needs it)" with "palette scopes contributed by plugins (added on 2026-09-21 by the palette-plugins design, `2026-09-21-jasper-palette-plugins-design.md`)".

`AGENTS.md`, "Architecture rules", after the sentence ending "`verifySdkArchitecture` and `verifyPluginArchitecture` enforce this.": add "Plugins contribute palette scopes through `dev.jasper.sdk.palette`; `dev.jasper.app.contributions` carries them and depends on `dev.jasper.app.palette` for that."

`docs/STATUS.md`: in the opening paragraph, after the plan 4c sentence, add "The palette-plugins design (`superpowers/specs/2026-09-21-jasper-palette-plugins-design.md`) is approved; plan 5a (the contribution surface) is implemented on `claude/palette-plugins`, plan 5b (the History and Snippets plugins) is next." Add a dated `### Palette plugins plan 5a — 2026-09-21` section before the plan 4c one: what was added (SDK 0.6.0: `dev.jasper.sdk.palette`, `Notices`, `Platform`, `PaneInfo.shell`, `palette.contribute`; app: `Contributions` scopes and palette requests, `HostedPalette`, key routing; testkit: `FakePalette` and the nine harness methods; contract 35 cases; sample `demo_scope`), the seven scope decisions at the top of this plan, exact test counts, deviations, and native acceptance pending.

Set this plan's **Status** banner to "Implemented on `claude/palette-plugins`; native acceptance pending" and list any deviation.

- [ ] **Step 6: Verify everything**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*' :jasper-terminal:test --tests '*TerminalDocumentationTest' -q`
Expected: PASS.

Run the AGENTS.md Python hygiene check. Expected: no output.

Run: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
Expected: `BUILD SUCCESSFUL` (rerun if the only failure is the known terminal flake). Count tests per module from `*/build/test-results/test/*.xml` and `plugins/sample/build/test-results/test/*.xml` for the STATUS section.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add -A
git commit -m "docs: describe palette scopes, notices and the editor for plugin authors

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Native acceptance (user-run; agents must not launch the GUI)

1. `./gradlew :jasper-app:run` with `[plugins."dev.jasper.sample"] demo_scope = true` in the configuration. Cmd+Alt+G opens the palette on a "Greetings" chip; `>greet` in the picker reaches it too; typing filters three rows; Enter pastes `echo '…'` into the pane you opened from; Cmd+Enter pastes and runs it.
2. With the palette open on Commands, Cmd+Alt+G switches to Greetings; Cmd+Alt+G again dismisses. Cmd+P, Cmd+R and Cmd+J behave as before.
3. Split the window into two panes and open the palette from each: the greeting lands in the pane it was opened from.
4. Disable the sample plugin in Manage Plugins and restart: the chip and the shortcut are gone; nothing else about the palette changed.
5. History and Snippets still work exactly as before; "Save as snippet…" from History still reopens Snippets on the saved row.

## Self-review record

- **Spec coverage.** §3.1 `Palette.register`/`open` → Tasks 1, 4; §3.2 `PaletteScope` and its semantics → Tasks 1, 4 (adapter), 5 (contract); §3.3 values and validation, namespace, `shortcutActionId` → Tasks 1, 3, 4; §3.4 `PaneInfo.shell` → Task 2, `Notices` and `Platform` → Tasks 1, 4, SDK 0.6.0 and the sample's range → Task 1; §4 `Contributions` scopes and requests, `WindowContributions`, `PaletteTarget` ids, `PaletteController.open`, `PaletteKeyRouter`, `HostedPalette`, `HostedContext`, environment callbacks → Tasks 3, 4; §5 fake and contract → Tasks 4, 5, with one narrowing: the contract records `open` requests and leaves the "showing scope dismisses" toggle to the window test in Task 3, because neither harness drives a real window; §8 sample `demo_scope` and coexistence with built-in scopes → Tasks 3 (window test) and 6.
- **Type consistency.** `ScopeSpec.of(id, label, placeholder, verbs)` with `withAliases/withDescription/withMonospaceRows/withIcon/withShortcutActionId`; `PaletteRow.of(id, title)` with `withDetail/withTag/withIcon/withEnabled/withToken`; `PaletteResults(rows, Optional sectionLabel, Optional initialSelectionId)` with `of` and `none`; `PaletteStep.Result.{done, error, reopen(scopeId, Optional rowId, Optional query)}`; `PaletteQuery(window, Optional target, maxResults, macOs)`; `Palette.open(window, scopeId, Optional query, Optional rowId)`; `Contributions.PaletteRequest(windowId, scopeId, Optional query, Optional rowId)`; `PaletteTarget(…, live, Optional windowId, Optional paneId)` with `none()` and `window(id)`; `PaletteController.open(scopeId, target, valid, queryOrNull, rowIdOrNull)`; `WindowCommandPalette.open(id, queryOrNull, rowIdOrNull)`; `PluginHost.Environment(…, terminals, notice, editor)`; harness line formats `id|label|verbIds`, `rowId|title|enabled`, `windowId scopeId query rowId`, `done`/`error:`/`reopen:scope:row:query` are used identically in Tasks 4, 5 and 6.
- **Placeholders.** None; every step carries its code or an exact edit instruction.
