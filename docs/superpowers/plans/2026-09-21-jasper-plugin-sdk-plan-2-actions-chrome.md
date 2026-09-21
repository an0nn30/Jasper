# Jasper Plugin SDK Plan 2: Actions and Chrome Placements — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on 2026-09-21 and merged into local `main` by fast-forward (not pushed); all headless checks pass on the merged result (1,246 tests); native acceptance pending. Deviations from this text: (1) Task 1: an invalid shortcut for a namespaced action id is a per-entry warning, not an error that resets all keybindings; the pre-existing `ConfigLoaderTest.unknownKeysAndEmptyTablesWarnWhileSupportedSettingsApply` required it and `KeyBindingsExtensionTest` now covers it. (2) Task 9: empty contributed status rows are laid out at the origin with zero size and non-empty left rows are clamped inside the bar, because the planned `doLayout` placed an empty row past the right edge at narrow widths and broke `ConfigurationStatusTest`'s bounded-layout contract; `WindowStatusBarContributionsTest` now checks every child at six widths. (3) `jasper-app/build.gradle.kts` declares `plugins/sample/src` rather than the whole `plugins/` tree as a test input, fixing a plan-1 bug that made Gradle reject invocations running both the sample's and the app's tasks. (4) Task 5's test and implementation were written together; its failing state was a compile error by construction.

**Goal:** Let plugins contribute actions that appear in the command palette and can be rebound, and place them in the toolbar, the menu bar, the terminal context menu and the status bar, with theme-aware icons; proven by the sample plugin.

**Architecture:** The SDK gains a `ui` package (actions and their placements, appearance) and identity-only window and pane handles. SDK types stay confined to `dev.jasper.app.plugins`: a new app-native leaf package, `dev.jasper.app.contributions`, holds an EDT model of contributed actions, toolbar entries, menu sections and status entries. The plugin runtime adapts SDK calls onto that model; each window observes it and renders it with the existing chrome (the same buttons, menus and status styling), so a plugin never supplies chrome components. `KeyBindings` becomes keyed by action-id string, carries user bindings for namespaced (dotted) ids through configuration loading, and resolves them against registered contributed actions with the precedence: user configuration, built-in defaults, plugin defaults in load order.

**Tech Stack:** Java 25 on JBR 25, Gradle wrapper, Swing, FlatLaf extras (`FlatSVGIcon`, already an app dependency), JUnit 6.1.3, AssertJ 3.27.7. No new dependency.

**Spec:** `docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md` (section 4 items 1 and 3, section 5 "Actions and keybindings", "Toolbar", "Menus", "Status bar", "Look and feel", section 13 item 2). Plan 1 and its deviations: `docs/superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-1-core-runtime.md`. Executors read both.

## Global Constraints

- Use `./gradlew`, never a system Gradle. Java 25, JetBrains vendor toolchain. `./gradlew check` is headless and must pass at the end of every task.
- **Never launch the GUI** (`:jasper-app:run`, benchmarks, previews that open windows). GUI checks are the user's; the end of this plan lists them.
- Work on branch `claude/plugin-sdk-plan-2` in `.worktrees/plugin-sdk-plan-2` (this plan is committed there). Run `git branch --show-current` before every commit. End commit messages with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- SDK modules reference only the JDK and `dev.jasper.sdk.*` (`verifySdkArchitecture`). SDK types appear in the app only inside `dev.jasper.app.plugins` (`verifyApplicationArchitecture`). `dev.jasper.app.contributions`, `workspace`, `config` and `platform` must not import `dev.jasper.sdk`.
- No interface without two real implementations: every new SDK interface is implemented by the testkit and by the app. App-native model types are final classes and records.
- Contributed action ids, top-level menu ids and status item ids start with `<plugin id>.`; they match `[a-z][a-z0-9_.-]{0,127}`. Built-in `ActionId.id()` values contain no dot and do not change.
- Keybinding precedence: user `[keybindings]`, then built-in defaults, then plugin defaults in plugin load order. A plugin default that collides is dropped and logged, not shown as a configuration warning. A user binding for an id no registered action has is a configuration warning.
- Threading: every new SDK registration call and every handle mutator is EDT-only and throws `IllegalStateException` elsewhere; action handlers run on the EDT, contained like every other plugin callback.
- Plugins contribute actions, text and icons only; the app renders all chrome. A placement may reference only an action the same plugin has registered.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in source. Run the AGENTS.md Python check (its default glob already covers every module).
- Every production package has a `package-info.java`; app package-infos name allowed outgoing dependencies and end with "The module architecture check forbids package cycles; app types are not an external plugin API."
- Javadoc runs with `-Xdoclint:all` and `failOnError` for `jasper-sdk` and `jasper-app`.
- `AppDocumentationTest` link-checks and example-checks every Markdown file under `docs/superpowers`, including this plan: keep documentation links inside code fences here, and never write a literal example marker comment in this file (Task 12 spells it `EXAMPLE-MARKER`).
- Known flake, not to be "fixed" here: `TerminalAppIntegrationTest` `"reflow"` case fails about one full `check --rerun-tasks` in eight on untouched code (see `docs/STATUS.md`). If it is the only failure, rerun; anything else is yours.

### Deliberate scope decisions

1. `WindowHandle` and `PaneHandle` are introduced now because `ActionContext` needs them, but carry **identity only** (`UUID id()`). Queries, focus, injection and capability gating arrive with the terminal plan (plan 4), which may add methods because only the app and the testkit implement these interfaces.
2. `JasperSdk.VERSION` becomes `0.2.0`; the sample plugin's range becomes `>=0.2, <0.3`.
3. The sample plugin's UI is off unless `demo_ui = true`, like its activity, so a daily-driver Jasper shows nothing new by default.
4. Status items are global (one handle drives every window), as the spec's known limit says.

## File Structure

```
jasper-sdk/src/main/java/dev/jasper/sdk/
  JasperSdk.java                                   modify: 0.2.0
  terminal/  package-info.java  WindowHandle.java  PaneHandle.java            create
  ui/        package-info.java  Actions.java  ActionSpec.java  ActionContext.java  PluginAction.java
             Toolbar.java  ToolbarItem.java  Menus.java  StandardMenu.java  PluginMenu.java
             StatusBar.java  StatusItemSpec.java  StatusItem.java  Side.java  Appearance.java   create
  plugin/PluginContext.java                        modify: five accessors

jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/
  FakeUi.java                                      create: recording implementations
  FakePluginContext.java  FakePluginHost.java      modify
jasper-sdk-testkit/src/testFixtures/.../contract/
  ContractHarness.java  PluginContractTest.java    modify: UI operations and contract cases

jasper-app/src/main/java/dev/jasper/app/
  commands/ActionId.java                           modify: forId
  config/KeyBindings.java  ConfigLoader.java       modify: string ids, extension bindings
  platform/AppIcons.java                           modify: themed(ClassLoader, path)
  contributions/  package-info.java  Contributions.java  ActionEntry.java  ToolbarEntry.java
                  MenuEntry.java  MenuTarget.java  MenuSection.java  StatusEntry.java     create
  plugins/HostedUi.java  HostedMenu.java           create: SDK adapters onto the model
  plugins/HostedContext.java  PluginHost.java  PluginRuntime.java  package-info.java      modify
  workspace/WindowContributions.java               create: per-window Swing actions and rendering glue
  workspace/WindowContent.java  WindowChrome.java  WindowStatusBar.java  package-info.java  modify
  application/JasperApplication.java               modify: own the model, report binding problems

plugins/sample/src/main/resources/plugin.toml, dev/jasper/sample/flask.svg
plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java             modify
docs/plugin-authoring.md  docs/sdk-architecture.md  docs/configuration.md  docs/app-architecture.md
docs/STATUS.md  jasper-sdk/README.md                                         modify
```

---

### Task 0: Workspace

- [ ] **Step 1: Confirm the worktree and branch**

```bash
cd /Users/dustin/projects/moray/.worktrees/plugin-sdk-plan-2 && git branch --show-current
```

Expected: `claude/plugin-sdk-plan-2`. If the worktree does not exist, create it with `git worktree add -b claude/plugin-sdk-plan-2 .worktrees/plugin-sdk-plan-2 main` from the main checkout and copy this plan in.

- [ ] **Step 2: Confirm the baseline is green**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL` (see the known flake above).

---

### Task 1: Keybindings keyed by action id, with extension bindings

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/KeyBindings.java`, `ConfigLoader.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/config/KeyBindingsExtensionTest.java`

**Interfaces:**
- Produces:
  - `ActionId.forId(String id)` → `Optional<ActionId>`
  - `KeyBindings.extensionId(String id)` → `boolean` (well-formed and contains a dot)
  - `record KeyBindings.Extension(String id, Optional<String> defaultBinding)`
  - `record KeyBindings.Problem(Kind kind, String actionId, String message)` with `enum Kind { UNKNOWN_ACTION, DEFAULT_DROPPED }`
  - `record KeyBindings.Resolved(KeyBindings bindings, List<Problem> problems)`
  - `Resolved withExtensions(List<Extension> extensions)`; `Optional<String> idFor(KeyStroke)`; `Optional<KeyStroke> strokeFor(String id)`; `Map<String, KeyStroke> strokes()`
  - Unchanged signatures: `defaults(boolean)`, `effectiveDefaultBinding(ActionId, boolean)`, `withOverrides(boolean, Map<String, String>)`, `actionFor(KeyStroke)`, `strokeFor(ActionId)`, `parse(String, boolean)`

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KeyBindingsExtensionTest {
    private static KeyBindings.Extension extension(String id, String binding) {
        return new KeyBindings.Extension(id, Optional.ofNullable(binding));
    }

    private static KeyStroke stroke(String binding) { return KeyBindings.parse(binding, true).orElseThrow(); }

    @Test void stringAndEnumLookupsAgree() {
        KeyBindings bindings = KeyBindings.defaults(true);
        KeyStroke newTab = stroke("cmd+t");
        assertThat(bindings.idFor(newTab)).hasValue("new_tab");
        assertThat(bindings.actionFor(newTab)).hasValue(ActionId.NEW_TAB);
        assertThat(bindings.strokeFor("new_tab")).isEqualTo(bindings.strokeFor(ActionId.NEW_TAB)).hasValue(newTab);
        assertThat(bindings.strokes()).hasSize(ActionId.values().length).containsEntry("new_tab", newTab);
        assertThat(ActionId.forId("new_tab")).hasValue(ActionId.NEW_TAB);
        assertThat(ActionId.forId("dev.x.tool.run")).isEmpty();
        assertThat(KeyBindings.extensionId("dev.x.tool.run")).isTrue();
        for (String id : new String[]{"new_tab", "", null, "Dev.x", "dev..x", ".dev", "dev.x."})
            assertThat(KeyBindings.extensionId(id)).as(String.valueOf(id)).isFalse();
    }

    @Test void userBindingsForExtensionIdsAreCollisionCheckedButNotBoundUntilTheActionExists() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            KeyBindings.withOverrides(true, Map.of("dev.x.tool.run", "cmd+t"))).withMessageContaining("collision");
        assertThatIllegalArgumentException().isThrownBy(() ->
            KeyBindings.withOverrides(true, Map.of("unknown_builtin", "cmd+y"))).withMessageContaining("unknown action");
        var overrides = new LinkedHashMap<String, String>();
        overrides.put("new_tab", "none");
        overrides.put("dev.x.tool.run", "cmd+t");
        KeyBindings base = KeyBindings.withOverrides(true, overrides);
        assertThat(base.idFor(stroke("cmd+t"))).as("not bound before the action is registered").isEmpty();

        KeyBindings.Resolved resolved = base.withExtensions(List.of(extension("dev.x.tool.run", "cmd+alt+j")));
        assertThat(resolved.problems()).isEmpty();
        assertThat(resolved.bindings().idFor(stroke("cmd+t"))).hasValue("dev.x.tool.run");
        assertThat(resolved.bindings().idFor(stroke("cmd+alt+j"))).as("the user's choice replaces the default").isEmpty();
        assertThat(resolved.bindings().actionFor(stroke("cmd+t"))).as("not a built-in").isEmpty();
    }

    @Test void builtInsBeatPluginDefaultsAndEarlierPluginsBeatLaterOnes() {
        KeyBindings.Resolved resolved = KeyBindings.defaults(true).withExtensions(List.of(
            extension("dev.a.one", "cmd+k"),
            extension("dev.a.two", "cmd+alt+j"),
            extension("dev.b.three", "cmd+alt+j"),
            extension("dev.b.four", "not a shortcut"),
            extension("dev.b.five", null)));
        KeyBindings bindings = resolved.bindings();
        assertThat(bindings.idFor(stroke("cmd+k"))).hasValue("command_palette");
        assertThat(bindings.idFor(stroke("cmd+alt+j"))).hasValue("dev.a.two");
        assertThat(bindings.strokeFor("dev.b.three")).isEmpty();
        assertThat(resolved.problems()).extracting(KeyBindings.Problem::kind)
            .containsOnly(KeyBindings.Problem.Kind.DEFAULT_DROPPED);
        assertThat(resolved.problems()).extracting(KeyBindings.Problem::actionId)
            .containsExactly("dev.a.one", "dev.b.three", "dev.b.four");
        assertThat(resolved.problems().get(0).message()).contains("cmd+k", "command_palette");
        assertThat(resolved.problems().get(1).message()).contains("dev.a.two");
    }

    @Test void noneUnbindsAndUnregisteredUserIdsAreReported() {
        KeyBindings base = KeyBindings.withOverrides(true, Map.of("dev.a.one", "none", "dev.gone.action", "cmd+alt+g"));
        KeyBindings.Resolved resolved = base.withExtensions(List.of(extension("dev.a.one", "cmd+alt+j")));
        assertThat(resolved.bindings().strokeFor("dev.a.one")).isEmpty();
        assertThat(resolved.bindings().idFor(stroke("cmd+alt+g"))).isEmpty();
        assertThat(resolved.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.kind()).isEqualTo(KeyBindings.Problem.Kind.UNKNOWN_ACTION);
            assertThat(problem.actionId()).isEqualTo("dev.gone.action");
        });
        assertThat(base.withExtensions(List.of()).bindings().strokes()).isEqualTo(KeyBindings.defaults(true).strokes());
    }

    @Test void theLoaderKeepsExtensionBindingsAndStillRejectsUnknownBuiltIns() {
        var ok = ConfigLoader.parse(Path.of("config.toml"), """
            [keybindings]
            "dev.jasper.sample.demo" = "cmd+alt+j"
            new_tab = "cmd+y"
            """, true);
        assertThat(ok.diagnostics()).isEmpty();
        assertThat(ok.snapshot().keybindings()).containsEntry("dev.jasper.sample.demo", "cmd+alt+j").containsEntry("new_tab", "cmd+y");

        var unknown = ConfigLoader.parse(Path.of("config.toml"), "[keybindings]\nnot_an_action = \"cmd+y\"\n", true);
        assertThat(unknown.diagnostics()).singleElement().satisfies(d -> assertThat(d.message()).contains("Unknown action"));

        var colliding = ConfigLoader.parse(Path.of("config.toml"), "[keybindings]\n\"dev.x.tool.run\" = \"cmd+t\"\n", true);
        assertThat(colliding.diagnostics()).singleElement().satisfies(d -> assertThat(d.message()).contains("conflicts"));
        assertThat(colliding.snapshot().keybindings()).isEmpty();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*KeyBindingsExtensionTest'`
Expected: compilation FAILS (`idFor`, `Extension`, `forId` not found).

- [ ] **Step 3: Add `ActionId.forId`**

In `ActionId`, after `id()`:

```java
    /** The built-in action with this configuration id, if any; contributed actions are not in this catalog. */
    public static java.util.Optional<ActionId> forId(String id) {
        for (ActionId action : values()) if (action.id.equals(id)) return java.util.Optional.of(action);
        return java.util.Optional.empty();
    }
```

Change the enum's Javadoc from "The complete Phase 1 application action catalog." to "The built-in application action catalog; plugins contribute further actions by namespaced id."

- [ ] **Step 4: Rewrite the upper half of `KeyBindings`**

Replace everything from the imports down to, but not including, `static Optional<KeyStroke> parse(String binding, boolean macOs) {` with:

```java
import dev.jasper.app.commands.ActionId;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses and indexes application keybindings by action id, independently of the Swing action layer.
 * Built-in ids come from {@link ActionId}; namespaced (dotted) ids belong to contributed actions.
 * A user's binding for a dotted id is validated when the configuration loads but bound only once
 * {@link #withExtensions} learns that the action exists.
 */
public final class KeyBindings {
    /** A contributed action and the shortcut its contributor would like, if any. */
    public record Extension(String id, Optional<String> defaultBinding) {
        public Extension {
            if (!extensionId(id)) throw new IllegalArgumentException("not a contributed action id: '" + id + "'");
            Objects.requireNonNull(defaultBinding, "defaultBinding");
        }
    }

    /** Something {@link #withExtensions} could not honor. */
    public record Problem(Kind kind, String actionId, String message) {
        public enum Kind {
            /** The user bound an id that no registered action has: a configuration warning. */
            UNKNOWN_ACTION,
            /** A contributor's default lost to an existing binding or was invalid: logged, not a warning. */
            DEFAULT_DROPPED
        }
    }

    /** Effective bindings and what was dropped on the way. */
    public record Resolved(KeyBindings bindings, List<Problem> problems) {
        public Resolved { problems = List.copyOf(problems); }
    }

    private static final Map<String, Integer> NAMED_KEYS = namedKeys();
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    private final boolean macOs;
    private final Map<String, KeyStroke> strokesById;
    private final Map<KeyStroke, String> idsByStroke;
    private final Map<String, Optional<KeyStroke>> extensionOverrides;

    private KeyBindings(boolean macOs, Map<String, KeyStroke> strokesById, Map<String, Optional<KeyStroke>> extensionOverrides) {
        this.macOs = macOs;
        this.strokesById = Collections.unmodifiableMap(new LinkedHashMap<>(strokesById));
        Map<KeyStroke, String> reverse = new HashMap<>();
        for (Map.Entry<String, KeyStroke> entry : strokesById.entrySet()) reverse.put(entry.getValue(), entry.getKey());
        idsByStroke = Map.copyOf(reverse);
        this.extensionOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(extensionOverrides));
    }

    /** Whether the id is well formed and namespaced with at least one dot, which no built-in id is. */
    public static boolean extensionId(String id) {
        return id != null && id.length() <= 128 && ID.matcher(id).matches();
    }

    public static KeyBindings defaults(boolean macOs) {
        Map<String, KeyStroke> strokes = new LinkedHashMap<>();
        for (ActionId action : ActionId.values()) {
            String binding = effectiveDefaultBinding(action, macOs);
            KeyStroke stroke = parse(binding, macOs).orElseThrow();
            putWithoutCollision(strokes, action.id(), stroke, binding);
        }
        return new KeyBindings(macOs, strokes, Map.of());
    }

    /** Text that round-trips the effective default, including non-macOS compatibility modifiers. */
    public static String effectiveDefaultBinding(ActionId action, boolean macOs) {
        String binding = action.defaultBinding(macOs);
        if (action == ActionId.COMMAND_PALETTE || action == ActionId.CLEAR_SCROLLBACK || action == ActionId.HISTORY_PALETTE
            || action == ActionId.SNIPPETS_PALETTE) return binding;
        return !macOs && binding.contains("cmd+shift") ? "alt+" + binding : binding;
    }

    public static KeyBindings withOverrides(boolean macOs, Map<String, String> overrides) {
        if (overrides == null) {
            throw new IllegalArgumentException("keybinding overrides must not be null");
        }
        Map<String, KeyStroke> result = new LinkedHashMap<>(defaults(macOs).strokesById);
        Map<String, String> builtIn = new LinkedHashMap<>();
        Map<String, String> extensions = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String id = entry.getKey();
            if (ActionId.forId(id).isPresent()) builtIn.put(id, entry.getValue());
            else if (extensionId(id)) extensions.put(id, entry.getValue());
            else throw new IllegalArgumentException("unknown action name: '" + id + "'");
        }
        builtIn.keySet().forEach(result::remove);
        for (Map.Entry<String, String> entry : builtIn.entrySet()) {
            parseFor(entry.getKey(), entry.getValue(), macOs)
                .ifPresent(stroke -> putWithoutCollision(result, entry.getKey(), stroke, entry.getValue()));
        }
        // A contributed action's shortcut must not collide with anything the user or the defaults claim,
        // but it is not bound here: the action may not exist in this launch.
        Map<String, KeyStroke> claimed = new LinkedHashMap<>(result);
        Map<String, Optional<KeyStroke>> extensionOverrides = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : extensions.entrySet()) {
            Optional<KeyStroke> parsed = parseFor(entry.getKey(), entry.getValue(), macOs);
            parsed.ifPresent(stroke -> putWithoutCollision(claimed, entry.getKey(), stroke, entry.getValue()));
            extensionOverrides.put(entry.getKey(), parsed);
        }
        return new KeyBindings(macOs, result, extensionOverrides);
    }

    /**
     * Adds contributed actions in registration order. The user's binding for an action wins; otherwise
     * its default applies unless the shortcut is already taken or invalid. Always call this on the
     * result of {@link #withOverrides} or {@link #defaults}, never on an already extended instance.
     */
    public Resolved withExtensions(List<Extension> extensions) {
        Map<String, KeyStroke> strokes = new LinkedHashMap<>();
        strokesById.forEach((id, stroke) -> { if (!extensionId(id)) strokes.put(id, stroke); });
        List<Problem> problems = new ArrayList<>();
        Set<String> registered = new LinkedHashSet<>();
        for (Extension extension : extensions) registered.add(extension.id());
        for (Map.Entry<String, Optional<KeyStroke>> override : extensionOverrides.entrySet()) {
            if (!registered.contains(override.getKey())) {
                problems.add(new Problem(Problem.Kind.UNKNOWN_ACTION, override.getKey(), "Unknown action; ignored."));
                continue;
            }
            override.getValue().ifPresent(stroke -> strokes.put(override.getKey(), stroke));
        }
        for (Extension extension : extensions) {
            if (extensionOverrides.containsKey(extension.id()) || extension.defaultBinding().isEmpty()) continue;
            String binding = extension.defaultBinding().get();
            Optional<KeyStroke> parsed;
            try { parsed = parse(binding, macOs); }
            catch (IllegalArgumentException invalid) {
                problems.add(new Problem(Problem.Kind.DEFAULT_DROPPED, extension.id(),
                    "Default shortcut '" + binding + "' is not valid; the action is unbound."));
                continue;
            }
            if (parsed.isEmpty()) continue;
            String holder = null;
            for (Map.Entry<String, KeyStroke> existing : strokes.entrySet())
                if (existing.getValue().equals(parsed.get())) holder = existing.getKey();
            if (holder != null) {
                problems.add(new Problem(Problem.Kind.DEFAULT_DROPPED, extension.id(), "Default shortcut '" + binding
                    + "' is already used by '" + holder + "'; bind the action under [keybindings] to give it one."));
                continue;
            }
            strokes.put(extension.id(), parsed.get());
        }
        return new Resolved(new KeyBindings(macOs, strokes, extensionOverrides), problems);
    }

    public Optional<String> idFor(KeyStroke stroke) {
        return Optional.ofNullable(idsByStroke.get(stroke));
    }

    public Optional<KeyStroke> strokeFor(String id) {
        return Optional.ofNullable(strokesById.get(id));
    }

    /** Every bound id with its shortcut, built-ins first, in a stable order. */
    public Map<String, KeyStroke> strokes() { return strokesById; }

    public Optional<ActionId> actionFor(KeyStroke stroke) {
        return idFor(stroke).flatMap(ActionId::forId);
    }

    public Optional<KeyStroke> strokeFor(ActionId action) {
        return strokeFor(action.id());
    }

    private static Optional<KeyStroke> parseFor(String id, String binding, boolean macOs) {
        try {
            return parse(binding, macOs);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "invalid keybinding for action '" + id + "': '" + binding + "' (" + exception.getMessage() + ")", exception);
        }
    }

```

Then delete the old `actionForId` method and replace `putWithoutCollision` with the string-keyed form:

```java
    private static void putWithoutCollision(Map<String, KeyStroke> strokes, String id, KeyStroke stroke, String binding) {
        for (Map.Entry<String, KeyStroke> existing : strokes.entrySet()) {
            if (existing.getValue().equals(stroke)) {
                throw new IllegalArgumentException(
                    "keybinding collision for '" + binding + "': actions '" + existing.getKey() + "' and '" + id + "'");
            }
        }
        strokes.put(id, stroke);
    }
```

`parse`, `keyCode` and `namedKeys` are unchanged. `EnumMap` is no longer imported.

- [ ] **Step 5: Let the loader keep extension bindings**

In `ConfigLoader.readBindings`, change the unknown-action guard so a namespaced id is validated like any other binding instead of being dropped:

```java
            if (!knownAction(action) && !KeyBindings.extensionId(action)) {
                warning(path, "Unknown action; ignored.");
                continue;
            }
```

Nothing else in `readBindings` changes: the staged `withOverrides` calls already reject a shortcut that conflicts, now including conflicts with a contributed action's user binding.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.workspace.*'`
Expected: PASS, including every pre-existing `KeyBindingsTest` and `ConfigLoaderTest` case. If a pre-existing test asserts the exact text of a collision message that names actions, it still matches: the message format is unchanged and built-in ids are the same strings.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "refactor: key keybindings by action id and resolve contributed actions

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 2: SDK `ui` and `terminal` types

This task adds types only. `PluginContext` gains its accessors in Task 6, when both implementations exist, so every task leaves the build green.

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/{package-info,WindowHandle,PaneHandle}.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/{package-info,Actions,ActionSpec,ActionContext,PluginAction,Toolbar,ToolbarItem,Menus,StandardMenu,PluginMenu,StatusBar,StatusItemSpec,StatusItem,Side,Appearance}.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java`

**Interfaces:**
- Produces (package `dev.jasper.sdk.terminal`): `WindowHandle { UUID id(); }`, `PaneHandle { UUID id(); }`
- Produces (package `dev.jasper.sdk.ui`):
  - `record ActionSpec(String id, String title, Optional<Icon> icon, List<String> keywords, Optional<String> defaultBinding)`; `static ActionSpec of(String id, String title)`; `ActionSpec withIcon(Icon)`, `withKeywords(List<String>)`, `withDefaultBinding(String)`
  - `ActionContext { WindowHandle window(); Optional<PaneHandle> pane(); }`
  - `PluginAction extends Subscription { void setEnabled(boolean); void setTitle(String); }`
  - `Actions { PluginAction register(ActionSpec spec, Consumer<ActionContext> handler); }`
  - `sealed interface ToolbarItem permits ToolbarItem.Button, ToolbarItem.Dropdown`; `record Button(String actionId)`; `record Dropdown(Icon icon, String title, List<String> actionIds)`; `static ToolbarItem action(String)`; `static ToolbarItem menu(Icon, String, List<String>)`
  - `Toolbar { Subscription add(ToolbarItem item); }`
  - `enum StandardMenu { FILE, EDIT, VIEW, PANE, TAB }`
  - `PluginMenu extends Subscription { Subscription add(String actionId); Subscription addSeparator(); PluginMenu submenu(String title); void clear(); }`
  - `Menus { PluginMenu standard(StandardMenu menu); PluginMenu create(String menuId, String title); PluginMenu terminalContext(); }`
  - `enum Side { LEFT, RIGHT }`; `record StatusItemSpec(String id, Side side, int priority)`
  - `StatusItem extends Subscription { void setText(String); void setIcon(Icon); void setTooltip(String); void setAction(String actionId); void setVisible(boolean); }`
  - `StatusBar { StatusItem add(StatusItemSpec spec); }`
  - `Appearance { Variant variant(); Subscription onChanged(Consumer<Variant> handler); Icon icon(String svgResourcePath); }`

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.sdk.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UiValuesTest {
    @Test void actionSpecValidatesAndCopies() {
        var keywords = new ArrayList<>(List.of("connect"));
        ActionSpec spec = ActionSpec.of("dev.x.tool.run", "Run Tool").withKeywords(keywords).withDefaultBinding("cmd+alt+j");
        keywords.add("later");
        assertThat(spec.keywords()).containsExactly("connect");
        assertThat(spec.defaultBinding()).hasValue("cmd+alt+j");
        assertThat(spec.icon()).isEmpty();
        assertThat(ActionSpec.of("dev.x.tool.run", "Run").withDefaultBinding(null).defaultBinding()).isEmpty();
        for (String id : new String[]{"", "Upper.case", "nodot", "sp ace.x"})
            assertThatIllegalArgumentException().as(id).isThrownBy(() -> ActionSpec.of(id, "Title"));
        assertThatIllegalArgumentException().isThrownBy(() -> ActionSpec.of("dev.x.run", " "));
        assertThatNullPointerException().isThrownBy(() ->
            new ActionSpec("dev.x.run", "Run", null, List.of(), Optional.empty()));
    }

    @Test void toolbarItemsAndStatusSpecsValidate() {
        assertThat(ToolbarItem.action("dev.x.run")).isEqualTo(new ToolbarItem.Button("dev.x.run"));
        var ids = new ArrayList<>(List.of("dev.x.a", "dev.x.b"));
        var dropdown = (ToolbarItem.Dropdown) ToolbarItem.menu(new javax.swing.ImageIcon(), "Hosts", ids);
        ids.clear();
        assertThat(dropdown.actionIds()).containsExactly("dev.x.a", "dev.x.b");
        assertThatIllegalArgumentException().isThrownBy(() -> ToolbarItem.menu(new javax.swing.ImageIcon(), " ", List.of("dev.x.a")));
        assertThatIllegalArgumentException().isThrownBy(() -> ToolbarItem.menu(new javax.swing.ImageIcon(), "Hosts", List.of()));
        assertThatNullPointerException().isThrownBy(() -> ToolbarItem.menu(null, "Hosts", List.of("dev.x.a")));
        assertThatNullPointerException().isThrownBy(() -> ToolbarItem.action(null));

        assertThat(new StatusItemSpec("dev.x.lock", Side.RIGHT, 10).side()).isEqualTo(Side.RIGHT);
        assertThatIllegalArgumentException().isThrownBy(() -> new StatusItemSpec("nodot", Side.LEFT, 0));
        assertThatNullPointerException().isThrownBy(() -> new StatusItemSpec("dev.x.lock", null, 0));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-sdk:test --tests '*UiValuesTest'`
Expected: compilation FAILS, `ActionSpec` not found.

- [ ] **Step 3: Write the handles**

`terminal/package-info.java`:

```java
/**
 * Stable identities for the application's windows and panes. In SDK 0.2 a handle carries identity
 * only; queries, focus and injection arrive with the terminal API. Depends on no other SDK package.
 */
package dev.jasper.sdk.terminal;
```

`terminal/WindowHandle.java`:

```java
package dev.jasper.sdk.terminal;

import java.util.UUID;

/** One application window. Implemented by the application; holds no Swing object. */
public interface WindowHandle {
    /**
     * The window's identity for as long as it is open.
     *
     * @return the id
     */
    UUID id();
}
```

`terminal/PaneHandle.java`:

```java
package dev.jasper.sdk.terminal;

import java.util.UUID;

/** One terminal pane. Implemented by the application; holds no Swing object. */
public interface PaneHandle {
    /**
     * The pane's identity for as long as it is open.
     *
     * @return the id
     */
    UUID id();
}
```

- [ ] **Step 4: Write the `ui` package**

`ui/package-info.java`:

```java
/**
 * What a plugin contributes to the application's chrome. Anything clickable is an action; the toolbar,
 * menus and status bar only place actions, and the application renders them, so contributed chrome
 * looks like built-in chrome. Depends on {@code dev.jasper.sdk} and {@code dev.jasper.sdk.terminal}.
 */
package dev.jasper.sdk.ui;
```

`ui/ActionSpec.java`:

```java
package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.swing.Icon;

/**
 * An action as the user sees it in the command palette, menus and toolbar.
 *
 * @param id namespaced id that starts with the plugin's id and a dot, for example {@code dev.example.tool.run};
 *           it is also the key users write under {@code [keybindings]}
 * @param title non-blank title
 * @param icon icon for the palette and toolbar, normally from {@link Appearance#icon}
 * @param keywords extra palette search words; copied
 * @param defaultBinding shortcut in the configuration syntax, for example {@code cmd+alt+j}. The user's
 *                       configuration and the built-in shortcuts win; a default that collides is dropped
 */
public record ActionSpec(String id, String title, Optional<Icon> icon, List<String> keywords, Optional<String> defaultBinding) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates the id and title and copies the keywords. */
    public ActionSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced action id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("An action needs a title");
        Objects.requireNonNull(icon, "icon");
        keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
        Objects.requireNonNull(defaultBinding, "defaultBinding");
    }

    /**
     * An action with only an id and a title.
     *
     * @param id namespaced id
     * @param title non-blank title
     * @return the spec
     */
    public static ActionSpec of(String id, String title) {
        return new ActionSpec(id, title, Optional.empty(), List.of(), Optional.empty());
    }

    /**
     * A copy with an icon.
     *
     * @param value the icon, or null for none
     * @return the copy
     */
    public ActionSpec withIcon(Icon value) {
        return new ActionSpec(id, title, Optional.ofNullable(value), keywords, defaultBinding);
    }

    /**
     * A copy with palette keywords.
     *
     * @param value the keywords
     * @return the copy
     */
    public ActionSpec withKeywords(List<String> value) {
        return new ActionSpec(id, title, icon, value, defaultBinding);
    }

    /**
     * A copy with a default shortcut.
     *
     * @param value the shortcut, or null for none
     * @return the copy
     */
    public ActionSpec withDefaultBinding(String value) {
        return new ActionSpec(id, title, icon, keywords, Optional.ofNullable(value));
    }
}
```

`ui/ActionContext.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Optional;

/** Where an action was invoked. */
public interface ActionContext {
    /**
     * The window whose palette, menu, toolbar, status bar or shortcut invoked the action.
     *
     * @return the window
     */
    WindowHandle window();

    /**
     * The pane the action concerns: the pane under a terminal context menu, otherwise the window's focused pane.
     *
     * @return the pane, or empty when the window has none
     */
    Optional<PaneHandle> pane();
}
```

`ui/PluginAction.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;

/** A registered action. Closing it removes the action and every placement of it. Mutators are UI-thread only. */
public interface PluginAction extends Subscription {
    /**
     * Enables or disables the action everywhere it appears. A disabled action's shortcut is still consumed.
     *
     * @param enabled whether the action can run
     */
    void setEnabled(boolean enabled);

    /**
     * Changes the title everywhere the action appears.
     *
     * @param title non-blank title
     */
    void setTitle(String title);
}
```

`ui/Actions.java`:

```java
package dev.jasper.sdk.ui;

import java.util.function.Consumer;

/** Registers actions. Every registered action appears in each window's command palette and can be bound to a key. */
public interface Actions {
    /**
     * Registers an action on the UI thread.
     *
     * @param spec what the action is
     * @param handler runs on the UI thread each time the action is invoked; failures are contained
     * @return the registration
     * @throws IllegalArgumentException when the id does not start with the plugin's id and a dot, or is already registered
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    PluginAction register(ActionSpec spec, Consumer<ActionContext> handler);
}
```

`ui/ToolbarItem.java`:

```java
package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Objects;
import javax.swing.Icon;

/** Something a plugin places in the main toolbar. The application draws it in the toolbar's own style. */
public sealed interface ToolbarItem permits ToolbarItem.Button, ToolbarItem.Dropdown {
    /**
     * A button for one action, using the action's title and icon.
     *
     * @param actionId an action this plugin registered
     */
    record Button(String actionId) implements ToolbarItem {
        /** Rejects a null id. */
        public Button { Objects.requireNonNull(actionId, "actionId"); }
    }

    /**
     * A button that opens a menu of actions.
     *
     * @param icon the button's icon
     * @param title non-blank label
     * @param actionIds one or more actions this plugin registered; copied
     */
    record Dropdown(Icon icon, String title, List<String> actionIds) implements ToolbarItem {
        /** Validates and copies. */
        public Dropdown {
            Objects.requireNonNull(icon, "icon");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("A toolbar menu needs a title");
            actionIds = List.copyOf(Objects.requireNonNull(actionIds, "actionIds"));
            if (actionIds.isEmpty()) throw new IllegalArgumentException("A toolbar menu needs at least one action");
        }
    }

    /**
     * A button for one action.
     *
     * @param actionId an action this plugin registered
     * @return the item
     */
    static ToolbarItem action(String actionId) { return new Button(actionId); }

    /**
     * A button that opens a menu of actions.
     *
     * @param icon the button's icon
     * @param title non-blank label
     * @param actionIds one or more actions this plugin registered
     * @return the item
     */
    static ToolbarItem menu(Icon icon, String title, List<String> actionIds) { return new Dropdown(icon, title, actionIds); }
}
```

`ui/Toolbar.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;

/** The main toolbar's plugin section, which follows the built-in buttons in every window. */
public interface Toolbar {
    /**
     * Adds an item on the UI thread.
     *
     * @param item the item
     * @return the registration
     * @throws IllegalArgumentException when the item names an action this plugin has not registered
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    Subscription add(ToolbarItem item);
}
```

`ui/StandardMenu.java`:

```java
package dev.jasper.sdk.ui;

/** The application's built-in menus; a plugin's entries form a section at the end of one. */
public enum StandardMenu {
    /** The File menu. */
    FILE,
    /** The Edit menu. */
    EDIT,
    /** The View menu. */
    VIEW,
    /** The Pane menu. */
    PANE,
    /** The Tab menu. */
    TAB
}
```

`ui/PluginMenu.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;

/**
 * A menu, or a plugin's section of a menu, that the plugin may change at any time on the UI thread:
 * a host list can be rebuilt with {@link #clear()} and {@link #add}. Closing a section removes only
 * this plugin's entries; closing a submenu removes it from its parent.
 */
public interface PluginMenu extends Subscription {
    /**
     * Appends an action.
     *
     * @param actionId an action this plugin registered
     * @return removes this entry
     * @throws IllegalArgumentException when this plugin has not registered the action
     */
    Subscription add(String actionId);

    /**
     * Appends a separator.
     *
     * @return removes this separator
     */
    Subscription addSeparator();

    /**
     * Appends a submenu.
     *
     * @param title non-blank title
     * @return the submenu
     */
    PluginMenu submenu(String title);

    /** Removes every entry, including submenus. */
    void clear();
}
```

`ui/Menus.java`:

```java
package dev.jasper.sdk.ui;

/** Menu bar and terminal context menu contributions. All calls are UI-thread only. */
public interface Menus {
    /**
     * A new section at the end of a built-in menu, after a separator.
     *
     * @param menu the built-in menu
     * @return the section
     */
    PluginMenu standard(StandardMenu menu);

    /**
     * A new top-level menu, placed after the Tab menu.
     *
     * @param menuId namespaced id that starts with the plugin's id and a dot
     * @param title non-blank title
     * @return the menu
     * @throws IllegalArgumentException when the id is malformed, foreign or already in use
     */
    PluginMenu create(String menuId, String title);

    /**
     * A new section at the end of every pane's right-click menu. An action invoked from it receives that pane.
     *
     * @return the section
     */
    PluginMenu terminalContext();
}
```

`ui/Side.java`:

```java
package dev.jasper.sdk.ui;

/** Which end of the status bar an item sits at. */
public enum Side {
    /** After the shell and directory. */
    LEFT,
    /** Before the terminal size and configuration state. */
    RIGHT
}
```

`ui/StatusItemSpec.java`:

```java
package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Where a status item goes.
 *
 * @param id namespaced id that starts with the plugin's id and a dot
 * @param side which end of the status bar
 * @param priority items on one side are ordered by ascending priority, left to right
 */
public record StatusItemSpec(String id, Side side, int priority) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates the id and side. */
    public StatusItemSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced status item id: " + id);
        Objects.requireNonNull(side, "side");
    }
}
```

`ui/StatusItem.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import javax.swing.Icon;

/**
 * A status bar item the application renders in every window. One handle drives them all; per-window
 * content is not supported. Mutators are UI-thread only and do nothing after {@link #close()}.
 */
public interface StatusItem extends Subscription {
    /**
     * Sets the text; line breaks become spaces.
     *
     * @param text the text, or empty for an icon-only item
     */
    void setText(String text);

    /**
     * Sets the icon.
     *
     * @param icon the icon, or null for none
     */
    void setIcon(Icon icon);

    /**
     * Sets the tooltip.
     *
     * @param text plain text, or null for none
     */
    void setTooltip(String text);

    /**
     * Makes the item clickable.
     *
     * @param actionId an action this plugin registered, or null to make the item inert
     * @throws IllegalArgumentException when this plugin has not registered the action
     */
    void setAction(String actionId);

    /**
     * Shows or hides the item.
     *
     * @param visible whether the item is shown
     */
    void setVisible(boolean visible);
}
```

`ui/StatusBar.java`:

```java
package dev.jasper.sdk.ui;

/** Status bar contributions. */
public interface StatusBar {
    /**
     * Adds an item on the UI thread. It is visible, with no text, icon, tooltip or action, until set.
     *
     * @param spec where the item goes
     * @return the item
     * @throws IllegalArgumentException when the id does not start with the plugin's id and a dot, or is already in use
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    StatusItem add(StatusItemSpec spec);
}
```

`ui/Appearance.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import java.util.function.Consumer;
import javax.swing.Icon;

/**
 * The application's look. Plugins build ordinary Swing components and the global look and feel applies
 * to them; this is for the few things that must follow the theme by hand.
 */
public interface Appearance {
    /**
     * The current variant; safe from any thread.
     *
     * @return dark or light
     */
    Variant variant();

    /**
     * Runs the handler on the UI thread after each change of variant.
     *
     * @param handler receives the new variant
     * @return the registration
     */
    Subscription onChanged(Consumer<Variant> handler);

    /**
     * A 16 by 16 icon from an SVG in the plugin's own jars, recolored to the chrome's foreground so it
     * follows the theme without being reloaded. Use monochrome artwork.
     *
     * @param svgResourcePath classpath path without a leading slash, for example {@code dev/example/tool/run.svg}
     * @return the icon
     * @throws IllegalArgumentException when the plugin's jars hold no such resource
     */
    Icon icon(String svgResourcePath);
}
```

- [ ] **Step 5: Run the tests and guards**

Run: `./gradlew :jasper-sdk:check verifySdkArchitecture`
Expected: `BUILD SUCCESSFUL`; Javadoc clean; package graph acyclic (`ui` → `terminal`, root; `terminal` → nothing).

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-sdk
git commit -m "feat: define the SDK actions, placements and appearance API

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: The app-native contributions model

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/contributions/{package-info,Contributions,ActionEntry,ToolbarEntry,MenuEntry,MenuTarget,MenuSection,StatusEntry}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/contributions/ContributionsTest.java`

**Interfaces:**
- Produces (all public, EDT only, no SDK types):
  - `Contributions`: `enum Kind { ACTIONS, TOOLBAR, MENUS, STATUS }`; `record Invocation(UUID windowId, Optional<UUID> paneId)`; `ActionEntry addAction(String id, String title, Icon iconOrNull, List<String> keywords, Optional<String> defaultBinding, Consumer<Invocation> handler)`; `Subscription addToolbar(ToolbarEntry entry)`; `MenuSection addMenuSection(MenuTarget target)`; `StatusEntry addStatus(String id, boolean left, int priority)`; `List<ActionEntry> actions()`; `Optional<ActionEntry> action(String id)`; `List<ToolbarEntry> toolbar()`; `List<MenuSection> menus()`; `List<StatusEntry> status()` (sorted by priority, then registration); `Subscription onChanged(Consumer<Kind> listener)`
  - `ActionEntry`: `id()`, `title()`, `icon()` (nullable), `keywords()`, `defaultBinding()`, `enabled()`, `void invoke(Contributions.Invocation)`, `setEnabled(boolean)`, `setTitle(String)`, `close()`
  - `sealed interface ToolbarEntry permits ToolbarEntry.Button, ToolbarEntry.Dropdown`; `record Button(String actionId)`; `record Dropdown(Icon icon, String title, List<String> actionIds)`
  - `sealed interface MenuEntry permits MenuEntry.Item, MenuEntry.Separator, MenuEntry.Submenu`; `record Item(String actionId)`; `record Separator()`; `record Submenu(String title, List<MenuEntry> entries)`
  - `record MenuTarget(Type type, String key, String title)` with `enum Type { STANDARD, TOP_LEVEL, CONTEXT }`, `enum Slot { FILE, EDIT, VIEW, PANE, TAB }`, factories `standard(Slot)`, `topLevel(String id, String title)`, `terminalContext()`
  - `MenuSection`: `target()`, `entries()`, `set(List<MenuEntry>)`, `close()`
  - `StatusEntry`: `id()`, `left()`, `priority()`, `text()`, `icon()`, `tooltip()`, `actionId()` (nullable), `visible()`, setters for the last five, `close()`

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.contributions;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ContributionsTest {
    private final Contributions model = new Contributions();
    private final List<Contributions.Kind> changes = new ArrayList<>();

    @Test void actionsAreUniqueObservableAndInvocable() {
        model.onChanged(changes::add);
        var seen = new ArrayList<Contributions.Invocation>();
        ActionEntry run = model.addAction("dev.x.run", "Run", null, List.of("go"), Optional.of("cmd+alt+j"), seen::add);
        assertThatIllegalArgumentException().isThrownBy(() ->
            model.addAction("dev.x.run", "Again", null, List.of(), Optional.empty(), invocation -> { }));
        var invocation = new Contributions.Invocation(UUID.randomUUID(), Optional.empty());
        run.invoke(invocation);
        run.setEnabled(false);
        run.invoke(invocation);
        run.setTitle("Run Now");
        assertThat(seen).containsExactly(invocation);
        assertThat(model.action("dev.x.run")).get().extracting(ActionEntry::title).isEqualTo("Run Now");
        assertThat(model.actions()).containsExactly(run);
        run.close();
        run.close();
        run.setTitle("ignored after close");
        assertThat(model.actions()).isEmpty();
        assertThat(changes).containsExactly(Contributions.Kind.ACTIONS, Contributions.Kind.ACTIONS,
            Contributions.Kind.ACTIONS, Contributions.Kind.ACTIONS);
    }

    @Test void toolbarMenusAndStatusKeepOrderAndNotify() {
        model.onChanged(changes::add);
        var first = model.addToolbar(new ToolbarEntry.Button("dev.x.a"));
        model.addToolbar(new ToolbarEntry.Dropdown(new javax.swing.ImageIcon(), "Hosts", List.of("dev.x.a")));
        first.close();
        assertThat(model.toolbar()).singleElement().isInstanceOf(ToolbarEntry.Dropdown.class);

        MenuSection view = model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.VIEW));
        MenuSection top = model.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Tool"));
        assertThatIllegalArgumentException().isThrownBy(() -> model.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Again")));
        view.set(List.of(new MenuEntry.Item("dev.x.a"), new MenuEntry.Separator(),
            new MenuEntry.Submenu("More", List.of(new MenuEntry.Item("dev.x.a")))));
        assertThat(model.menus()).containsExactly(view, top);
        assertThat(view.entries()).hasSize(3);
        top.close();
        assertThat(model.menus()).containsExactly(view);
        model.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Reused after close"));

        StatusEntry late = model.addStatus("dev.x.late", false, 20);
        StatusEntry early = model.addStatus("dev.x.early", false, 10);
        assertThatIllegalArgumentException().isThrownBy(() -> model.addStatus("dev.x.early", true, 0));
        early.setText("line one\nline two");
        early.setActionId("dev.x.a");
        early.setVisible(false);
        assertThat(model.status()).containsExactly(early, late);
        assertThat(early.text()).isEqualTo("line one line two");
        assertThat(early.visible()).isFalse();
        early.close();
        early.setText("ignored");
        assertThat(model.status()).containsExactly(late);
        assertThat(changes).contains(Contributions.Kind.TOOLBAR, Contributions.Kind.MENUS, Contributions.Kind.STATUS)
            .doesNotContain(Contributions.Kind.ACTIONS);
    }

    @Test void aFailingListenerDoesNotStopTheOthersAndClosedListenersAreSilent() {
        var quiet = model.onChanged(changes::add);
        model.onChanged(kind -> { throw new IllegalStateException("listener failure"); });
        model.onChanged(changes::add);
        assertThatCode(() -> model.addToolbar(new ToolbarEntry.Button("dev.x.a"))).doesNotThrowAnyException();
        assertThat(changes).hasSize(2);
        quiet.close();
        model.addToolbar(new ToolbarEntry.Button("dev.x.a"));
        assertThat(changes).hasSize(3);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*ContributionsTest'`
Expected: compilation FAILS, `Contributions` not found.

- [ ] **Step 3: Implement**

`package-info.java`:

```java
/**
 * EDT model of what extensions contribute to the chrome: actions, toolbar entries, menu sections and
 * status entries, in app-native types. The plugin runtime writes it, every window renders it; neither
 * sees the other. The application owns the single instance; contributors close their own entries.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.lifecycle.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.contributions;
```

`ToolbarEntry.java`:

```java
package dev.jasper.app.contributions;

import java.util.List;
import javax.swing.Icon;

/** One contributed toolbar control. */
public sealed interface ToolbarEntry permits ToolbarEntry.Button, ToolbarEntry.Dropdown {
    /** A button for one contributed action. */
    record Button(String actionId) implements ToolbarEntry { }

    /** A button that opens a menu of contributed actions. */
    record Dropdown(Icon icon, String title, List<String> actionIds) implements ToolbarEntry {
        public Dropdown { actionIds = List.copyOf(actionIds); }
    }
}
```

`MenuEntry.java`:

```java
package dev.jasper.app.contributions;

import java.util.List;

/** One immutable node of a contributed menu section. */
public sealed interface MenuEntry permits MenuEntry.Item, MenuEntry.Separator, MenuEntry.Submenu {
    /** A contributed action. */
    record Item(String actionId) implements MenuEntry { }

    /** A separator. */
    record Separator() implements MenuEntry { }

    /** A nested menu. */
    record Submenu(String title, List<MenuEntry> entries) implements MenuEntry {
        public Submenu { entries = List.copyOf(entries); }
    }
}
```

`MenuTarget.java`:

```java
package dev.jasper.app.contributions;

import java.util.Objects;

/**
 * Where a menu section goes.
 *
 * @param type the kind of target
 * @param key the slot name for a standard menu, the menu id for a top-level menu, empty for the context menu
 * @param title a top-level menu's title, otherwise empty
 */
public record MenuTarget(Type type, String key, String title) {
    /** The kinds of target. */
    public enum Type { STANDARD, TOP_LEVEL, CONTEXT }

    /** The built-in menus that accept a contributed section. */
    public enum Slot { FILE, EDIT, VIEW, PANE, TAB }

    public MenuTarget {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(title, "title");
    }

    /** A section at the end of a built-in menu. */
    public static MenuTarget standard(Slot slot) { return new MenuTarget(Type.STANDARD, slot.name(), ""); }

    /** A contributed top-level menu. */
    public static MenuTarget topLevel(String id, String title) { return new MenuTarget(Type.TOP_LEVEL, id, title); }

    /** A section at the end of every pane's context menu. */
    public static MenuTarget terminalContext() { return new MenuTarget(Type.CONTEXT, "", ""); }
}
```

`ActionEntry.java`:

```java
package dev.jasper.app.contributions;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Icon;

/** A contributed action. Mutators notify every window; all of them do nothing after {@link #close()}. */
public final class ActionEntry {
    private final Contributions owner;
    private final String id;
    private final Icon icon;
    private final List<String> keywords;
    private final Optional<String> defaultBinding;
    private Consumer<Contributions.Invocation> handler;
    private String title;
    private boolean enabled = true;
    private boolean closed;

    ActionEntry(Contributions owner, String id, String title, Icon icon, List<String> keywords,
                Optional<String> defaultBinding, Consumer<Contributions.Invocation> handler) {
        this.owner = owner; this.id = id; this.title = title; this.icon = icon;
        this.keywords = List.copyOf(keywords); this.defaultBinding = defaultBinding; this.handler = handler;
    }

    public String id() { return id; }
    public String title() { return title; }
    /** The icon, or null. */
    public Icon icon() { return icon; }
    public List<String> keywords() { return keywords; }
    public Optional<String> defaultBinding() { return defaultBinding; }
    public boolean enabled() { return enabled && !closed; }

    /** Runs the contributor's handler unless the action is disabled or closed. */
    public void invoke(Contributions.Invocation invocation) {
        if (enabled()) handler.accept(invocation);
    }

    public void setEnabled(boolean value) {
        if (closed || enabled == value) return;
        enabled = value;
        owner.changed(Contributions.Kind.ACTIONS);
    }

    public void setTitle(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("An action needs a title");
        if (closed || title.equals(value)) return;
        title = value;
        owner.changed(Contributions.Kind.ACTIONS);
    }

    public void close() {
        if (closed) return;
        closed = true;
        handler = invocation -> { };
        owner.remove(this);
    }
}
```

`MenuSection.java`:

```java
package dev.jasper.app.contributions;

import java.util.List;

/** One contributor's entries for one menu target; replaced as a whole. */
public final class MenuSection {
    private final Contributions owner;
    private final MenuTarget target;
    private List<MenuEntry> entries = List.of();
    private boolean closed;

    MenuSection(Contributions owner, MenuTarget target) { this.owner = owner; this.target = target; }

    public MenuTarget target() { return target; }
    public List<MenuEntry> entries() { return entries; }

    public void set(List<MenuEntry> next) {
        if (closed) return;
        entries = List.copyOf(next);
        owner.changed(Contributions.Kind.MENUS);
    }

    public void close() {
        if (closed) return;
        closed = true;
        entries = List.of();
        owner.remove(this);
    }
}
```

`StatusEntry.java`:

```java
package dev.jasper.app.contributions;

import javax.swing.Icon;

/** A contributed status item, rendered identically in every window. */
public final class StatusEntry {
    private final Contributions owner;
    private final String id;
    private final boolean left;
    private final int priority;
    private String text = "";
    private Icon icon;
    private String tooltip;
    private String actionId;
    private boolean visible = true;
    private boolean closed;

    StatusEntry(Contributions owner, String id, boolean left, int priority) {
        this.owner = owner; this.id = id; this.left = left; this.priority = priority;
    }

    public String id() { return id; }
    public boolean left() { return left; }
    public int priority() { return priority; }
    public String text() { return text; }
    /** The icon, or null. */
    public Icon icon() { return icon; }
    /** The tooltip, or null. */
    public String tooltip() { return tooltip; }
    /** The contributed action a click invokes, or null. */
    public String actionId() { return actionId; }
    public boolean visible() { return visible && !closed; }

    public void setText(String value) { if (!closed) { text = value == null ? "" : value.replace("\r", "").replace("\n", " "); notifyOwner(); } }
    public void setIcon(Icon value) { if (!closed) { icon = value; notifyOwner(); } }
    public void setTooltip(String value) { if (!closed) { tooltip = value; notifyOwner(); } }
    public void setActionId(String value) { if (!closed) { actionId = value; notifyOwner(); } }
    public void setVisible(boolean value) { if (!closed) { visible = value; notifyOwner(); } }

    private void notifyOwner() { owner.changed(Contributions.Kind.STATUS); }

    public void close() {
        if (closed) return;
        closed = true;
        owner.remove(this);
    }
}
```

`Contributions.java`:

```java
package dev.jasper.app.contributions;

import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.SwingUtilities;

/**
 * What extensions have contributed, in registration order. Windows subscribe and re-render the part
 * that changed. Everything here is EDT-only; callbacks into contributors are their concern to contain.
 */
public final class Contributions {
    /** Which part changed. */
    public enum Kind { ACTIONS, TOOLBAR, MENUS, STATUS }

    /** Where an action was invoked: the window, and the pane it concerns if the window has one. */
    public record Invocation(UUID windowId, Optional<UUID> paneId) {
        public Invocation {
            Objects.requireNonNull(windowId, "windowId");
            Objects.requireNonNull(paneId, "paneId");
        }
    }

    private static final System.Logger LOG = System.getLogger(Contributions.class.getName());
    private final Map<String, ActionEntry> actions = new LinkedHashMap<>();
    private final List<ToolbarEntry> toolbar = new ArrayList<>();
    private final List<MenuSection> menus = new ArrayList<>();
    private final List<StatusEntry> status = new ArrayList<>();
    private final List<Consumer<Kind>> listeners = new ArrayList<>();

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Contributions belong to the EDT");
    }

    public ActionEntry addAction(String id, String title, Icon iconOrNull, List<String> keywords,
                                 Optional<String> defaultBinding, Consumer<Invocation> handler) {
        requireEdt();
        Objects.requireNonNull(handler, "handler");
        if (actions.containsKey(id)) throw new IllegalArgumentException("Action already registered: " + id);
        var entry = new ActionEntry(this, id, title, iconOrNull, keywords, defaultBinding, handler);
        actions.put(id, entry);
        changed(Kind.ACTIONS);
        return entry;
    }

    public Subscription addToolbar(ToolbarEntry entry) {
        requireEdt();
        toolbar.add(Objects.requireNonNull(entry, "entry"));
        changed(Kind.TOOLBAR);
        // Identity removal: two equal records are still two toolbar controls.
        return new Subscription(() -> {
            for (int i = 0; i < toolbar.size(); i++) if (toolbar.get(i) == entry) { toolbar.remove(i); break; }
            changed(Kind.TOOLBAR);
        });
    }

    public MenuSection addMenuSection(MenuTarget target) {
        requireEdt();
        if (target.type() == MenuTarget.Type.TOP_LEVEL)
            for (MenuSection existing : menus)
                if (existing.target().type() == MenuTarget.Type.TOP_LEVEL && existing.target().key().equals(target.key()))
                    throw new IllegalArgumentException("Menu already exists: " + target.key());
        var section = new MenuSection(this, target);
        menus.add(section);
        changed(Kind.MENUS);
        return section;
    }

    public StatusEntry addStatus(String id, boolean left, int priority) {
        requireEdt();
        for (StatusEntry existing : status)
            if (existing.id().equals(id)) throw new IllegalArgumentException("Status item already exists: " + id);
        var entry = new StatusEntry(this, id, left, priority);
        status.add(entry);
        changed(Kind.STATUS);
        return entry;
    }

    public List<ActionEntry> actions() { return List.copyOf(actions.values()); }
    public Optional<ActionEntry> action(String id) { return Optional.ofNullable(actions.get(id)); }
    public List<ToolbarEntry> toolbar() { return List.copyOf(toolbar); }
    public List<MenuSection> menus() { return List.copyOf(menus); }

    /** Ascending priority; equal priorities keep registration order. */
    public List<StatusEntry> status() {
        return status.stream().sorted(Comparator.comparingInt(StatusEntry::priority)).toList();
    }

    public Subscription onChanged(Consumer<Kind> listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    void remove(ActionEntry entry) { if (actions.remove(entry.id(), entry)) changed(Kind.ACTIONS); }
    void remove(MenuSection section) { if (menus.remove(section)) changed(Kind.MENUS); }
    void remove(StatusEntry entry) { if (status.remove(entry)) changed(Kind.STATUS); }

    void changed(Kind kind) {
        for (Consumer<Kind> listener : List.copyOf(listeners)) {
            try { listener.accept(kind); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A contributions listener failed", failure); }
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*ContributionsTest' --tests '*AppDocumentationTest' verifyApplicationArchitecture`
Expected: PASS. In `actionsAreUniqueObservableAndInvocable` the four `ACTIONS` changes are: add, disable, retitle, close.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: add the app-native model of contributed chrome

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 4: Application adapters from the SDK onto the model

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/AppIcons.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/{HostedUi,HostedMenu}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/package-info.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java`, `jasper-app/src/test/java/dev/jasper/app/platform/AppIconsThemedTest.java`

**Interfaces:**
- Consumes: `Contributions` and its entries (Task 3), `Containment` (plan 1), the SDK `ui` types (Task 2).
- Produces:
  - `AppIcons.themed(ClassLoader loader, String svgResourcePath)` → `javax.swing.Icon`; throws `IllegalArgumentException` when the resource is absent
  - `HostedUi(String pluginId, Contributions model, Containment containment, Consumer<Runnable> ui, BooleanSupplier onUi, BooleanSupplier open, ClassLoader loader, Supplier<Variant> variant, Function<Consumer<Variant>, Subscription> themeSubscriber)` with `Actions actions()`, `Toolbar toolbar()`, `Menus menus()`, `StatusBar statusBar()`, `Appearance appearance()`, `void closeAll()`; package-private helpers `void guard(String what)`, `void requireOwn(String actionId)`, `Subscription subscription(Runnable removal)` (untracked), `Subscription tracked(Runnable removal)` (also removed by `closeAll`)
  - `HostedMenu` implements `PluginMenu`; `static HostedMenu root(HostedUi ui, MenuSection section)`

- [ ] **Step 1: Write the failing tests**

`AppIconsThemedTest.java`:

```java
package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AppIconsThemedTest {
    @Test void loadsAnSvgFromAGivenLoaderAtChromeSizeAndRejectsMissingResources() {
        var icon = AppIcons.themed(AppIcons.class.getClassLoader(), "dev/jasper/app/icons/search.svg");
        assertThat(icon.getIconWidth()).isEqualTo(icon.getIconHeight()).isPositive();
        assertThatIllegalArgumentException().isThrownBy(() ->
            AppIcons.themed(AppIcons.class.getClassLoader(), "dev/jasper/app/icons/absent.svg")).withMessageContaining("absent.svg");
    }
}
```

`HostedUiTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.ToolbarItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class HostedUiTest {
    private final Contributions model = new Contributions();
    private final Containment containment = new Containment(() -> true);
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final List<Consumer<Variant>> themeHandlers = new ArrayList<>();
    private Variant variant = Variant.DARK;
    private final HostedUi ui = new HostedUi("dev.x.tool", model, containment, Runnable::run, () -> true, open::get,
        HostedUiTest.class.getClassLoader(), () -> variant,
        handler -> { themeHandlers.add(handler); return () -> themeHandlers.remove(handler); });

    @Test void actionsReachTheModelWithContainedHandlersAndVerifiedContext() {
        List<ActionContext> seen = new ArrayList<>();
        PluginAction run = ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run").withDefaultBinding("cmd+alt+j"), seen::add);
        ui.actions().register(ActionSpec.of("dev.x.tool.boom", "Boom"), context -> { throw new IllegalStateException("handler"); });
        assertThatIllegalArgumentException().isThrownBy(() -> ui.actions().register(ActionSpec.of("dev.other.run", "Foreign"), seen::add));
        assertThatIllegalArgumentException().as("a longer id sharing the prefix is another namespace")
            .isThrownBy(() -> ui.actions().register(ActionSpec.of("dev.x.toolbox.run", "Foreign"), seen::add));

        UUID window = UUID.randomUUID(), pane = UUID.randomUUID();
        ActionEntry entry = model.action("dev.x.tool.run").orElseThrow();
        assertThat(entry.defaultBinding()).hasValue("cmd+alt+j");
        entry.invoke(new Contributions.Invocation(window, Optional.of(pane)));
        model.action("dev.x.tool.boom").orElseThrow().invoke(new Contributions.Invocation(window, Optional.empty()));
        assertThat(seen).singleElement().satisfies(context -> {
            assertThat(context.window().id()).isEqualTo(window);
            assertThat(context.pane()).get().extracting(handle -> handle.id()).isEqualTo(pane);
        });
        assertThat(containment.failures("dev.x.tool")).isEqualTo(1);

        run.setTitle("Run Now");
        run.setEnabled(false);
        assertThat(entry.title()).isEqualTo("Run Now");
        assertThat(entry.enabled()).isFalse();
        run.close();
        assertThat(model.action("dev.x.tool.run")).isEmpty();
        assertThatIllegalArgumentException().as("a closed action can no longer be placed")
            .isThrownBy(() -> ui.toolbar().add(ToolbarItem.action("dev.x.tool.run")));
    }

    @Test void placementsAcceptOnlyThisPluginsActions() {
        ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), context -> { });
        Subscription button = ui.toolbar().add(ToolbarItem.action("dev.x.tool.run"));
        ui.toolbar().add(ToolbarItem.menu(new javax.swing.ImageIcon(), "Tool", List.of("dev.x.tool.run")));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.toolbar().add(ToolbarItem.action("dev.x.tool.absent")));
        assertThatIllegalArgumentException().isThrownBy(() ->
            ui.toolbar().add(ToolbarItem.menu(new javax.swing.ImageIcon(), "Tool", List.of("dev.x.tool.run", "new_tab"))));
        assertThat(model.toolbar()).hasSize(2);
        button.close();
        button.close();
        assertThat(model.toolbar()).singleElement().isInstanceOf(ToolbarEntry.Dropdown.class);
    }

    @Test void menusAreMutableTreesPushedAsSnapshots() {
        ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), context -> { });
        PluginMenu view = ui.menus().standard(StandardMenu.VIEW);
        Subscription first = view.add("dev.x.tool.run");
        view.addSeparator();
        PluginMenu more = view.submenu("More");
        more.add("dev.x.tool.run");
        assertThat(model.menus()).singleElement().satisfies(section -> assertThat(section.entries()).containsExactly(
            new MenuEntry.Item("dev.x.tool.run"), new MenuEntry.Separator(),
            new MenuEntry.Submenu("More", List.of(new MenuEntry.Item("dev.x.tool.run")))));
        first.close();
        more.close();
        assertThat(model.menus().get(0).entries()).containsExactly(new MenuEntry.Separator());
        view.clear();
        assertThat(model.menus().get(0).entries()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> view.add("new_tab"));
        assertThatIllegalArgumentException().isThrownBy(() -> view.submenu(" "));

        ui.menus().create("dev.x.tool.menu", "Tool");
        assertThatIllegalArgumentException().isThrownBy(() -> ui.menus().create("dev.x.tool.menu", "Again"));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.menus().create("dev.other.menu", "Foreign"));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.menus().create("dev.x.tool.other", " "));
        ui.menus().terminalContext().add("dev.x.tool.run");
        assertThat(model.menus()).hasSize(3);
        view.close();
        assertThat(model.menus()).hasSize(2);
    }

    @Test void statusItemsAndAppearance() {
        ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), context -> { });
        StatusItem item = ui.statusBar().add(new StatusItemSpec("dev.x.tool.state", Side.RIGHT, 5));
        item.setText("Idle");
        item.setTooltip("Tool state");
        item.setAction("dev.x.tool.run");
        assertThatIllegalArgumentException().isThrownBy(() -> item.setAction("new_tab"));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.statusBar().add(new StatusItemSpec("dev.other.state", Side.LEFT, 0)));
        assertThat(model.status()).singleElement().satisfies(entry -> {
            assertThat(entry.left()).isFalse();
            assertThat(entry.text()).isEqualTo("Idle");
            assertThat(entry.actionId()).isEqualTo("dev.x.tool.run");
        });
        item.setAction(null);
        assertThat(model.status().get(0).actionId()).isNull();

        List<Variant> changes = new ArrayList<>();
        Subscription watching = ui.appearance().onChanged(changes::add);
        assertThat(ui.appearance().variant()).isEqualTo(Variant.DARK);
        variant = Variant.LIGHT;
        List.copyOf(themeHandlers).forEach(handler -> handler.accept(Variant.LIGHT));
        assertThat(ui.appearance().variant()).isEqualTo(Variant.LIGHT);
        assertThat(changes).containsExactly(Variant.LIGHT);
        watching.close();
        assertThat(themeHandlers).isEmpty();
        assertThat(ui.appearance().icon("dev/jasper/app/icons/search.svg").getIconWidth()).isPositive();
        assertThatIllegalArgumentException().isThrownBy(() -> ui.appearance().icon("nope.svg"));
    }

    @Test void closeAllRemovesEverythingAndAClosedContextRejectsRegistration() {
        ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), context -> { });
        ui.toolbar().add(ToolbarItem.action("dev.x.tool.run"));
        ui.menus().standard(StandardMenu.FILE).add("dev.x.tool.run");
        StatusItem item = ui.statusBar().add(new StatusItemSpec("dev.x.tool.state", Side.LEFT, 0));
        ui.closeAll();
        assertThat(model.actions()).isEmpty();
        assertThat(model.toolbar()).isEmpty();
        assertThat(model.menus()).isEmpty();
        assertThat(model.status()).isEmpty();
        assertThatCode(() -> item.setText("after close")).doesNotThrowAnyException();
        open.set(false);
        assertThatIllegalStateException().isThrownBy(() -> ui.actions().register(ActionSpec.of("dev.x.tool.late", "Late"), c -> { }));
        assertThatIllegalStateException().isThrownBy(() -> ui.menus().standard(StandardMenu.FILE));
    }

    @Test void registrationOffTheUiThreadIsRejectedAndCloseIsPosted() {
        List<Runnable> posted = new ArrayList<>();
        AtomicBoolean onUi = new AtomicBoolean(true);
        var other = new HostedUi("dev.x.tool", model, containment, posted::add, onUi::get, () -> true,
            HostedUiTest.class.getClassLoader(), () -> Variant.DARK, handler -> () -> { });
        PluginAction action = other.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), context -> { });
        onUi.set(false);
        assertThatIllegalStateException().isThrownBy(() -> other.actions().register(ActionSpec.of("dev.x.tool.b", "B"), c -> { }))
            .withMessageContaining("UI thread");
        assertThatIllegalStateException().isThrownBy(() -> action.setEnabled(false));
        action.close();
        assertThat(model.action("dev.x.tool.run")).as("removal waits for the UI thread").isPresent();
        onUi.set(true);
        posted.forEach(Runnable::run);
        assertThat(model.action("dev.x.tool.run")).isEmpty();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*HostedUiTest' --tests '*AppIconsThemedTest'`
Expected: compilation FAILS (`HostedUi`, `AppIcons.themed` not found).

- [ ] **Step 3: Add `AppIcons.themed`**

In `AppIcons`, after `icon(String)`:

```java
    /**
     * A chrome-sized icon from an SVG that another class loader holds, recolored like the bundled icons.
     * The color is read from the look and feel at paint time, so the icon follows theme changes.
     */
    public static javax.swing.Icon themed(ClassLoader loader, String svgResourcePath) {
        java.util.Objects.requireNonNull(loader, "loader");
        if (svgResourcePath == null || loader.getResource(svgResourcePath) == null)
            throw new IllegalArgumentException("No such icon resource: " + svgResourcePath);
        FlatSVGIcon icon = new FlatSVGIcon(svgResourcePath, 16, 16, loader);
        return icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> themed("Jasper.chromeForeground", source)));
    }
```

- [ ] **Step 4: Write `HostedUi`**

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.contributions.StatusEntry;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Actions;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.Menus;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusBar;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.Toolbar;
import dev.jasper.sdk.ui.ToolbarItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.Icon;

/**
 * One plugin's chrome contributions, adapted onto the app-native model. It enforces what the model
 * cannot know: ids belong to this plugin's namespace, placements name this plugin's own actions,
 * registration happens on the UI thread through an open context, and handlers are contained.
 */
final class HostedUi {
    private record Context(WindowHandle window, Optional<PaneHandle> pane) implements ActionContext { }

    private final String pluginId;
    private final Contributions model;
    private final Containment containment;
    private final Consumer<Runnable> ui;
    private final BooleanSupplier onUi;
    private final BooleanSupplier open;
    private final ClassLoader loader;
    private final Supplier<Variant> variant;
    private final Function<Consumer<Variant>, Subscription> themeSubscriber;
    private final Set<String> ownActions = new HashSet<>();
    private final List<Runnable> closers = new ArrayList<>();

    HostedUi(String pluginId, Contributions model, Containment containment, Consumer<Runnable> ui, BooleanSupplier onUi,
             BooleanSupplier open, ClassLoader loader, Supplier<Variant> variant,
             Function<Consumer<Variant>, Subscription> themeSubscriber) {
        this.pluginId = pluginId; this.model = model; this.containment = containment; this.ui = ui; this.onUi = onUi;
        this.open = open; this.loader = loader; this.variant = variant; this.themeSubscriber = themeSubscriber;
    }

    /** Registration needs an open context and the UI thread. */
    void guard(String what) {
        if (!open.getAsBoolean()) throw new IllegalStateException("Plugin context is closed: " + pluginId);
        requireUi(what);
    }

    private void requireUi(String what) {
        if (!onUi.getAsBoolean()) throw new IllegalStateException(what + " must be called on the UI thread: " + pluginId);
    }

    void requireOwn(String actionId) {
        if (!ownActions.contains(actionId))
            throw new IllegalArgumentException(actionId + " is not an action registered by " + pluginId);
    }

    private void requireNamespace(String id, String what) {
        if (!id.startsWith(pluginId + "."))
            throw new IllegalArgumentException(what + " id must start with " + pluginId + ".: " + id);
    }

    /** Idempotent and safe from any thread: off the UI thread the removal is posted to it. */
    Subscription subscription(Runnable removal) {
        var closed = new AtomicBoolean();
        return () -> {
            if (!closed.compareAndSet(false, true)) return;
            if (onUi.getAsBoolean()) removal.run(); else ui.accept(removal);
        };
    }

    /**
     * A top-level registration that {@link #closeAll} also removes. Menu leaves are not tracked: a
     * menu rebuilt many times must not grow this list, and closing its section removes them anyway.
     */
    Subscription tracked(Runnable removal) {
        closers.add(removal);
        return subscription(() -> { closers.remove(removal); removal.run(); });
    }

    /** UI thread: removes whatever the plugin left registered, newest first. Removals are idempotent. */
    void closeAll() {
        List<Runnable> pending = new ArrayList<>(closers);
        closers.clear();
        for (int i = pending.size() - 1; i >= 0; i--) pending.get(i).run();
        ownActions.clear();
    }

    Actions actions() {
        return (spec, handler) -> {
            guard("register");
            java.util.Objects.requireNonNull(handler, "handler");
            requireNamespace(spec.id(), "An action");
            ActionEntry entry = model.addAction(spec.id(), spec.title(), spec.icon().orElse(null), spec.keywords(),
                spec.defaultBinding(), invocation -> containment.run(pluginId, "action " + spec.id(), () ->
                    handler.accept(new Context(invocation::windowId, invocation.paneId().map(id -> (PaneHandle) () -> id)))));
            ownActions.add(spec.id());
            Subscription removal = tracked(() -> { ownActions.remove(spec.id()); entry.close(); });
            return new PluginAction() {
                @Override public void setEnabled(boolean enabled) { requireUi("setEnabled"); entry.setEnabled(enabled); }
                @Override public void setTitle(String title) { requireUi("setTitle"); entry.setTitle(title); }
                @Override public void close() { removal.close(); }
            };
        };
    }

    Toolbar toolbar() {
        return item -> {
            guard("add");
            ToolbarEntry entry = switch (item) {
                case ToolbarItem.Button button -> { requireOwn(button.actionId()); yield new ToolbarEntry.Button(button.actionId()); }
                case ToolbarItem.Dropdown dropdown -> {
                    dropdown.actionIds().forEach(this::requireOwn);
                    yield new ToolbarEntry.Dropdown(dropdown.icon(), dropdown.title(), dropdown.actionIds());
                }
            };
            var registration = model.addToolbar(entry);
            return tracked(registration::close);
        };
    }

    Menus menus() {
        return new Menus() {
            @Override public PluginMenu standard(StandardMenu menu) {
                guard("standard");
                return HostedMenu.root(HostedUi.this, model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.valueOf(menu.name()))));
            }
            @Override public PluginMenu create(String menuId, String title) {
                guard("create");
                if (menuId == null || !dev.jasper.sdk.PluginInfo.validId(menuId))
                    throw new IllegalArgumentException("Not a namespaced menu id: " + menuId);
                requireNamespace(menuId, "A menu");
                if (title == null || title.isBlank()) throw new IllegalArgumentException("A menu needs a title");
                // The model rejects a duplicate top-level id and frees it again when the menu closes.
                return HostedMenu.root(HostedUi.this, model.addMenuSection(MenuTarget.topLevel(menuId, title)));
            }
            @Override public PluginMenu terminalContext() {
                guard("terminalContext");
                return HostedMenu.root(HostedUi.this, model.addMenuSection(MenuTarget.terminalContext()));
            }
        };
    }

    StatusBar statusBar() {
        return spec -> {
            guard("add");
            requireNamespace(spec.id(), "A status item");
            StatusEntry entry = model.addStatus(spec.id(), spec.side() == Side.LEFT, spec.priority());
            Subscription removal = tracked(entry::close);
            return new StatusItem() {
                @Override public void setText(String text) { requireUi("setText"); entry.setText(text); }
                @Override public void setIcon(Icon icon) { requireUi("setIcon"); entry.setIcon(icon); }
                @Override public void setTooltip(String text) { requireUi("setTooltip"); entry.setTooltip(text); }
                @Override public void setAction(String actionId) {
                    requireUi("setAction");
                    if (actionId != null) requireOwn(actionId);
                    entry.setActionId(actionId);
                }
                @Override public void setVisible(boolean visible) { requireUi("setVisible"); entry.setVisible(visible); }
                @Override public void close() { removal.close(); }
            };
        };
    }

    Appearance appearance() {
        return new Appearance() {
            @Override public Variant variant() { return variant.get(); }
            @Override public Subscription onChanged(Consumer<Variant> handler) {
                guard("onChanged");
                return themeSubscriber.apply(java.util.Objects.requireNonNull(handler, "handler"));
            }
            @Override public Icon icon(String svgResourcePath) { return AppIcons.themed(loader, svgResourcePath); }
        };
    }
}
```

`Context(invocation::windowId, …)` works because `WindowHandle` and `PaneHandle` each have a single abstract method; if the compiler rejects the method reference for `WindowHandle`, write `() -> invocation.windowId()`.

- [ ] **Step 5: Write `HostedMenu`**

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuSection;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.PluginMenu;
import java.util.ArrayList;
import java.util.List;

/**
 * A mutable menu tree whose root pushes an immutable snapshot to its model section after every change,
 * so windows never observe a half-built menu. UI thread only.
 */
final class HostedMenu implements PluginMenu {
    /** An action when {@code actionId} is set, otherwise a separator. Identity, not value, decides removal. */
    private static final class Leaf {
        final String actionId;
        Leaf(String actionId) { this.actionId = actionId; }
    }

    private final HostedUi ui;
    private final HostedMenu parent;
    private final MenuSection section;
    private final String title;
    private final List<Object> children = new ArrayList<>();
    private final Subscription removal;
    private boolean closed;

    static HostedMenu root(HostedUi ui, MenuSection section) { return new HostedMenu(ui, null, section, ""); }

    private HostedMenu(HostedUi ui, HostedMenu parent, MenuSection section, String title) {
        this.ui = ui; this.parent = parent; this.section = section; this.title = title;
        this.removal = parent == null
            ? ui.tracked(() -> { closed = true; section.close(); })
            : ui.subscription(() -> { closed = true; if (parent.children.remove(this)) parent.push(); });
    }

    private void push() {
        HostedMenu root = this;
        while (root.parent != null) root = root.parent;
        if (!root.closed) root.section.set(root.snapshot());
    }

    private List<MenuEntry> snapshot() {
        List<MenuEntry> entries = new ArrayList<>();
        for (Object child : children) {
            if (child instanceof HostedMenu menu) entries.add(new MenuEntry.Submenu(menu.title, menu.snapshot()));
            else entries.add(((Leaf) child).actionId == null ? new MenuEntry.Separator() : new MenuEntry.Item(((Leaf) child).actionId));
        }
        return entries;
    }

    private Subscription append(Leaf leaf) {
        children.add(leaf);
        push();
        return ui.subscription(() -> { if (children.remove(leaf)) push(); });
    }

    @Override public Subscription add(String actionId) {
        ui.guard("add");
        ui.requireOwn(actionId);
        return append(new Leaf(actionId));
    }

    @Override public Subscription addSeparator() {
        ui.guard("addSeparator");
        return append(new Leaf(null));
    }

    @Override public PluginMenu submenu(String submenuTitle) {
        ui.guard("submenu");
        if (submenuTitle == null || submenuTitle.isBlank()) throw new IllegalArgumentException("A submenu needs a title");
        var menu = new HostedMenu(ui, this, section, submenuTitle);
        children.add(menu);
        push();
        return menu;
    }

    @Override public void clear() {
        ui.guard("clear");
        children.clear();
        push();
    }

    @Override public void close() { removal.close(); }
}
```

- [ ] **Step 6: Update the package contract**

In `plugins/package-info.java`, change the allowed-dependencies line to:

```java
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.contributions, dev.jasper.app.notifications, dev.jasper.app.persistence, dev.jasper.app.platform, dev.jasper.sdk, dev.jasper.sdk.activity, dev.jasper.sdk.events, dev.jasper.sdk.plugin, dev.jasper.sdk.services, dev.jasper.sdk.terminal, dev.jasper.sdk.ui.
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*HostedUiTest' --tests '*AppIconsThemedTest' verifyApplicationArchitecture`
Expected: PASS, 7 tests; no package cycle (`plugins` → `contributions`, `platform`; neither depends on `plugins`).

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: adapt SDK chrome contributions onto the application model

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Testkit recording UI

**Files:**
- Create: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakePluginContext,FakePluginHost}.java`
- Test: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeUiTest.java`

**Interfaces:**
- Produces on `FakePluginContext` (concrete methods now; they become `@Override`s in Task 6): `Actions actions()`, `Toolbar toolbar()`, `Menus menus()`, `StatusBar statusBar()`, `Appearance appearance()`
- Produces on `FakePluginHost`, all in one **rendering format that Task 6's application harness must reproduce exactly**:
  - `List<String> actions()` → `"<id>|<title>|<enabled>"` per action, registration order across plugins
  - `boolean invoke(String actionId, UUID windowId, UUID paneIdOrNull)` → whether an enabled action ran
  - `List<String> toolbar()` → `"button:<id>"` or `"menu:<title>:<id>,<id>"`; ids of closed actions are omitted, and an entry with none left is omitted
  - `List<String> menu(String target)` → target is `FILE`, `EDIT`, `VIEW`, `PANE`, `TAB`, `context` or `top:<menuId>`; lines are `"item:<id>"`, `"---"`, `"submenu:<title>"`, children indented two spaces per level; sections of one target are separated by `"==="`; items of closed actions are omitted
  - `List<String> status()` → `"<id>|<LEFT or RIGHT>|<text>|<tooltip>|<actionId>"` for visible items, ascending priority then registration order; absent values are empty
  - `void setVariant(Variant variant)` → changes `appearance().variant()` and publishes `AppEvents.THEME_CHANGED`

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.ToolbarItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakeUiTest {
    private static final PluginInfo INFO = new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of());

    @Test void recordsAndRendersEveryContribution() {
        try (var host = new FakePluginHost()) {
            List<String> ran = new ArrayList<>();
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> { });
            var run = context.actions().register(ActionSpec.of("dev.x.tool.run", "Run"),
                invoked -> ran.add(invoked.window().id() + "/" + invoked.pane().isPresent()));
            context.actions().register(ActionSpec.of("dev.x.tool.stop", "Stop"), invoked -> { });
            context.toolbar().add(ToolbarItem.action("dev.x.tool.run"));
            context.toolbar().add(ToolbarItem.menu(new javax.swing.ImageIcon(), "Tool", List.of("dev.x.tool.run", "dev.x.tool.stop")));
            var view = context.menus().standard(StandardMenu.VIEW);
            view.add("dev.x.tool.run");
            view.addSeparator();
            view.submenu("More").add("dev.x.tool.stop");
            context.menus().standard(StandardMenu.VIEW).add("dev.x.tool.stop");
            context.menus().create("dev.x.tool.menu", "Tool").add("dev.x.tool.run");
            context.menus().terminalContext().add("dev.x.tool.stop");
            var late = context.statusBar().add(new StatusItemSpec("dev.x.tool.late", Side.RIGHT, 20));
            var early = context.statusBar().add(new StatusItemSpec("dev.x.tool.early", Side.RIGHT, 10));
            late.setText("Late");
            early.setText("Early");
            early.setTooltip("tip");
            early.setAction("dev.x.tool.run");

            assertThat(host.actions()).containsExactly("dev.x.tool.run|Run|true", "dev.x.tool.stop|Stop|true");
            assertThat(host.toolbar()).containsExactly("button:dev.x.tool.run", "menu:Tool:dev.x.tool.run,dev.x.tool.stop");
            assertThat(host.menu("VIEW")).containsExactly("item:dev.x.tool.run", "---", "submenu:More", "  item:dev.x.tool.stop",
                "===", "item:dev.x.tool.stop");
            assertThat(host.menu("top:dev.x.tool.menu")).containsExactly("item:dev.x.tool.run");
            assertThat(host.menu("context")).containsExactly("item:dev.x.tool.stop");
            assertThat(host.menu("FILE")).isEmpty();
            assertThat(host.status()).containsExactly("dev.x.tool.early|RIGHT|Early|tip|dev.x.tool.run", "dev.x.tool.late|RIGHT|Late||");

            UUID window = UUID.randomUUID();
            assertThat(host.invoke("dev.x.tool.run", window, UUID.randomUUID())).isTrue();
            run.setEnabled(false);
            assertThat(host.invoke("dev.x.tool.run", window, null)).isFalse();
            assertThat(host.invoke("dev.x.tool.absent", window, null)).isFalse();
            assertThat(ran).containsExactly(window + "/true");

            run.close();
            assertThat(host.toolbar()).containsExactly("menu:Tool:dev.x.tool.stop");
            assertThat(host.menu("VIEW")).containsExactly("---", "submenu:More", "  item:dev.x.tool.stop", "===", "item:dev.x.tool.stop");
            assertThat(host.menu("top:dev.x.tool.menu")).isEmpty();
        }
    }

    @Test void appearanceFollowsTheHostAndIconsComeFromThePluginsLoader() {
        try (var host = new FakePluginHost()) {
            List<Variant> seen = new ArrayList<>();
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> plugin.appearance().onChanged(seen::add));
            assertThat(context.appearance().variant()).isEqualTo(Variant.DARK);
            host.setVariant(Variant.LIGHT);
            host.flush();
            assertThat(context.appearance().variant()).isEqualTo(Variant.LIGHT);
            assertThat(seen).containsExactly(Variant.LIGHT);
            assertThatIllegalArgumentException().isThrownBy(() -> context.appearance().icon("no/such/icon.svg"));
        }
    }
}
```

`plugin -> plugin.appearance().onChanged(seen::add)` only compiles once `PluginContext` has `appearance()` (Task 6). In this task write the first lambda as `plugin -> ((FakePluginContext) plugin).appearance().onChanged(seen::add)`; Task 6 removes the cast.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-sdk-testkit:test --tests '*FakeUiTest'`
Expected: compilation FAILS, `actions()` not found on `FakePluginContext`.

- [ ] **Step 3: Write `FakeUi`**

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Actions;
import dev.jasper.sdk.ui.Menus;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusBar;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.Toolbar;
import dev.jasper.sdk.ui.ToolbarItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.Icon;

/** One fake plugin's recorded chrome contributions, with the same rules as the application. */
final class FakeUi {
    static final class Action {
        final ActionSpec spec; final Consumer<ActionContext> handler; String title; boolean enabled = true;
        Action(ActionSpec spec, Consumer<ActionContext> handler) { this.spec = spec; this.handler = handler; this.title = spec.title(); }
    }

    static final class Menu implements PluginMenu {
        final String target; final String title; final Menu parent; final FakeUi ui;
        final List<Object> children = new ArrayList<>();
        Menu(FakeUi ui, Menu parent, String target, String title) { this.ui = ui; this.parent = parent; this.target = target; this.title = title; }

        @Override public Subscription add(String actionId) {
            ui.context.requireOpen();
            ui.requireOwn(actionId);
            Object leaf = new String[]{actionId};
            children.add(leaf);
            return () -> children.remove(leaf);
        }
        @Override public Subscription addSeparator() {
            ui.context.requireOpen();
            Object leaf = new String[]{null};
            children.add(leaf);
            return () -> children.remove(leaf);
        }
        @Override public PluginMenu submenu(String submenuTitle) {
            ui.context.requireOpen();
            if (submenuTitle == null || submenuTitle.isBlank()) throw new IllegalArgumentException("A submenu needs a title");
            var menu = new Menu(ui, this, target, submenuTitle);
            children.add(menu);
            return menu;
        }
        @Override public void clear() { ui.context.requireOpen(); children.clear(); }
        @Override public void close() {
            if (parent == null) { ui.sections.remove(this); ui.topLevelIds.remove(target); }
            else parent.children.remove(this);
        }

        void render(List<String> lines, String indent) {
            for (Object child : children) {
                if (child instanceof Menu menu) { lines.add(indent + "submenu:" + menu.title); menu.render(lines, indent + "  "); }
                else {
                    String actionId = ((String[]) child)[0];
                    if (actionId == null) lines.add(indent + "---");
                    else if (ui.host.actionExists(actionId)) lines.add(indent + "item:" + actionId);
                }
            }
        }
    }

    static final class Status implements StatusItem {
        final StatusItemSpec spec; final FakeUi ui;
        String text = ""; String tooltip; String actionId; boolean visible = true; boolean closed;
        Status(FakeUi ui, StatusItemSpec spec) { this.ui = ui; this.spec = spec; }
        @Override public void setText(String value) { if (!closed) text = value == null ? "" : value.replace("\r", "").replace("\n", " "); }
        @Override public void setIcon(Icon icon) { }
        @Override public void setTooltip(String value) { if (!closed) tooltip = value; }
        @Override public void setAction(String value) { if (value != null) ui.requireOwn(value); if (!closed) actionId = value; }
        @Override public void setVisible(boolean value) { if (!closed) visible = value; }
        @Override public void close() { closed = true; ui.status.remove(this); }
    }

    final FakePluginHost host;
    final FakePluginContext context;
    final Map<String, Action> actions = new LinkedHashMap<>();
    final List<ToolbarItem> toolbar = new ArrayList<>();
    final List<Menu> sections = new ArrayList<>();
    final List<String> topLevelIds = new ArrayList<>();
    final List<Status> status = new ArrayList<>();

    FakeUi(FakePluginHost host, FakePluginContext context) { this.host = host; this.context = context; }

    private String pluginId() { return context.plugin().id(); }

    void requireOwn(String actionId) {
        if (!actions.containsKey(actionId))
            throw new IllegalArgumentException(actionId + " is not an action registered by " + pluginId());
    }

    private void requireNamespace(String id, String what) {
        if (!id.startsWith(pluginId() + "."))
            throw new IllegalArgumentException(what + " id must start with " + pluginId() + ".: " + id);
    }

    void closeAll() { actions.clear(); toolbar.clear(); sections.clear(); topLevelIds.clear(); status.clear(); }

    boolean invoke(String actionId, UUID windowId, UUID paneIdOrNull) {
        Action action = actions.get(actionId);
        if (action == null || !action.enabled) return false;
        WindowHandle window = () -> windowId;
        Optional<PaneHandle> pane = Optional.ofNullable(paneIdOrNull).map(id -> (PaneHandle) () -> id);
        try {
            action.handler.accept(new ActionContext() {
                @Override public WindowHandle window() { return window; }
                @Override public Optional<PaneHandle> pane() { return pane; }
            });
        } catch (RuntimeException | LinkageError failure) { host.recordFailure(pluginId() + " action " + actionId + ": " + failure); }
        return true;
    }

    Actions actions() {
        return (spec, handler) -> {
            context.requireOpen();
            java.util.Objects.requireNonNull(handler, "handler");
            requireNamespace(spec.id(), "An action");
            if (actions.containsKey(spec.id())) throw new IllegalArgumentException("Action already registered: " + spec.id());
            var action = new Action(spec, handler);
            actions.put(spec.id(), action);
            return new PluginAction() {
                @Override public void setEnabled(boolean enabled) { action.enabled = enabled; }
                @Override public void setTitle(String title) {
                    if (title == null || title.isBlank()) throw new IllegalArgumentException("An action needs a title");
                    action.title = title;
                }
                @Override public void close() { actions.remove(spec.id(), action); }
            };
        };
    }

    Toolbar toolbar() {
        return item -> {
            context.requireOpen();
            switch (item) {
                case ToolbarItem.Button button -> requireOwn(button.actionId());
                case ToolbarItem.Dropdown dropdown -> dropdown.actionIds().forEach(this::requireOwn);
            }
            toolbar.add(item);
            return () -> { for (int i = 0; i < toolbar.size(); i++) if (toolbar.get(i) == item) { toolbar.remove(i); break; } };
        };
    }

    Menus menus() {
        return new Menus() {
            @Override public PluginMenu standard(StandardMenu menu) { return section(menu.name(), ""); }
            @Override public PluginMenu create(String menuId, String title) {
                context.requireOpen();
                if (menuId == null || !PluginInfo.validId(menuId)) throw new IllegalArgumentException("Not a namespaced menu id: " + menuId);
                requireNamespace(menuId, "A menu");
                if (title == null || title.isBlank()) throw new IllegalArgumentException("A menu needs a title");
                if (topLevelIds.contains("top:" + menuId)) throw new IllegalArgumentException("Menu already exists: " + menuId);
                topLevelIds.add("top:" + menuId);
                return section("top:" + menuId, title);
            }
            @Override public PluginMenu terminalContext() { return section("context", ""); }
        };
    }

    private Menu section(String target, String title) {
        context.requireOpen();
        var menu = new Menu(this, null, target, title);
        sections.add(menu);
        return menu;
    }

    StatusBar statusBar() {
        return spec -> {
            context.requireOpen();
            requireNamespace(spec.id(), "A status item");
            for (Status existing : status)
                if (existing.spec.id().equals(spec.id())) throw new IllegalArgumentException("Status item already exists: " + spec.id());
            var item = new Status(this, spec);
            status.add(item);
            return item;
        };
    }

    static Icon blankIcon() {
        return new Icon() {
            @Override public void paintIcon(java.awt.Component component, java.awt.Graphics graphics, int x, int y) { }
            @Override public int getIconWidth() { return 16; }
            @Override public int getIconHeight() { return 16; }
        };
    }

    Variant variant() { return host.variant(); }
}
```

- [ ] **Step 4: Wire it into the fake context and host**

In `FakePluginContext`, add the field `final FakeUi ui;`, assign it at the end of the constructor with `this.ui = new FakeUi(host, this);`, make `requireOpen()` package-private if it is not already, and add (imports: `dev.jasper.sdk.ui.*`, `dev.jasper.sdk.Variant`, `dev.jasper.sdk.events.AppEvents`):

```java
    /** Action registration. */
    public Actions actions() { return ui.actions(); }

    /** Toolbar placements. */
    public Toolbar toolbar() { return ui.toolbar(); }

    /** Menu placements. */
    public Menus menus() { return ui.menus(); }

    /** Status bar items. */
    public StatusBar statusBar() { return ui.statusBar(); }

    /** The host's variant, change notifications, and icons checked against the plugin's own class loader. */
    public Appearance appearance() {
        return new Appearance() {
            @Override public Variant variant() { return ui.variant(); }
            @Override public Subscription onChanged(java.util.function.Consumer<Variant> handler) {
                return events().subscribe(AppEvents.THEME_CHANGED, event -> handler.accept(event.variant()));
            }
            @Override public javax.swing.Icon icon(String svgResourcePath) {
                if (svgResourcePath == null || plugin.getClass().getClassLoader().getResource(svgResourcePath) == null)
                    throw new IllegalArgumentException("No such icon resource: " + svgResourcePath);
                return FakeUi.blankIcon();
            }
        };
    }
```

In `FakePluginHost`:

- in `teardown`, after `context.closeOwned();`, add `context.ui.closeAll();`
- add the field `private volatile Variant variant = Variant.DARK;` and these members (imports: `dev.jasper.sdk.Variant`, `dev.jasper.sdk.events.AppEvents`, `dev.jasper.sdk.ui.ToolbarItem`, `java.util.UUID` is already imported):

```java
    Variant variant() { return variant; }

    void recordFailure(String line) { failures.add(line); }

    boolean actionExists(String actionId) {
        for (FakePluginContext context : contexts.values()) if (context.ui.actions.containsKey(actionId)) return true;
        return false;
    }

    /**
     * Changes the look and announces it.
     *
     * @param next the new variant
     */
    public void setVariant(Variant next) {
        variant = Objects.requireNonNull(next, "next");
        publishApp(AppEvents.THEME_CHANGED, new AppEvents.ThemeChanged(next));
    }

    /**
     * Registered actions in registration order.
     *
     * @return lines of the form {@code id|title|enabled}
     */
    public List<String> actions() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values())
            for (FakeUi.Action action : context.ui.actions.values())
                lines.add(action.spec.id() + "|" + action.title + "|" + action.enabled);
        return lines;
    }

    /**
     * Invokes an action as a window would.
     *
     * @param actionId the action
     * @param windowId the invoking window
     * @param paneIdOrNull the pane the action concerns, or null
     * @return whether an enabled action ran
     */
    public boolean invoke(String actionId, UUID windowId, UUID paneIdOrNull) {
        for (FakePluginContext context : contexts.values())
            if (context.ui.actions.containsKey(actionId)) return context.ui.invoke(actionId, windowId, paneIdOrNull);
        return false;
    }

    /**
     * The toolbar's plugin section.
     *
     * @return {@code button:id} or {@code menu:title:id,id}; closed actions are omitted
     */
    public List<String> toolbar() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values()) {
            for (ToolbarItem item : context.ui.toolbar) {
                switch (item) {
                    case ToolbarItem.Button button -> { if (actionExists(button.actionId())) lines.add("button:" + button.actionId()); }
                    case ToolbarItem.Dropdown dropdown -> {
                        List<String> live = dropdown.actionIds().stream().filter(this::actionExists).toList();
                        if (!live.isEmpty()) lines.add("menu:" + dropdown.title() + ":" + String.join(",", live));
                    }
                }
            }
        }
        return lines;
    }

    /**
     * One menu target's contributed entries.
     *
     * @param target {@code FILE}, {@code EDIT}, {@code VIEW}, {@code PANE}, {@code TAB}, {@code context} or {@code top:<menuId>}
     * @return {@code item:id}, {@code ---} and {@code submenu:title} lines, children indented, sections separated by {@code ===}
     */
    public List<String> menu(String target) {
        List<String> lines = new ArrayList<>();
        boolean first = true;
        for (FakePluginContext context : contexts.values()) {
            for (FakeUi.Menu section : context.ui.sections) {
                if (!section.target.equals(target)) continue;
                if (!first) lines.add("===");
                first = false;
                section.render(lines, "");
            }
        }
        return lines;
    }

    /**
     * Visible status items, ascending priority.
     *
     * @return lines of the form {@code id|side|text|tooltip|actionId}
     */
    public List<String> status() {
        List<FakeUi.Status> items = new ArrayList<>();
        for (FakePluginContext context : contexts.values()) items.addAll(context.ui.status);
        return items.stream().filter(item -> item.visible)
            .sorted(java.util.Comparator.comparingInt((FakeUi.Status item) -> item.spec.priority()))
            .map(item -> item.spec.id() + "|" + item.spec.side() + "|" + item.text + "|"
                + (item.tooltip == null ? "" : item.tooltip) + "|" + (item.actionId == null ? "" : item.actionId))
            .toList();
    }
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-sdk-testkit:check verifySdkArchitecture`
Expected: PASS: the 9 contract tests, `FakePluginHostTest` and the 2 new `FakeUiTest` cases.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-sdk-testkit
git commit -m "feat: record chrome contributions in the testkit fake

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 6: `PluginContext` accessors, runtime wiring and the contract suite

This is the task in which the SDK interface grows, so both implementations and the shared contract change together.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/package-info.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/{FakeUiTest,FakeContractTest}.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/{ContractHarness,PluginContractTest}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/{HostedContext,PluginHost,PluginRuntime}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `plugins/sample/src/main/resources/plugin.toml`
- Modify tests: `jasper-app/src/test/java/dev/jasper/app/plugins/{AppContractTest,PluginRuntimeTest,BundledSamplePluginTest}.java`

**Interfaces:**
- Produces:
  - `PluginContext`: `Actions actions()`, `Toolbar toolbar()`, `Menus menus()`, `StatusBar statusBar()`, `Appearance appearance()`
  - `JasperSdk.VERSION` = `"0.2.0"`
  - `PluginHost.Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory, Function<String, Map<String, Object>> settings, BiConsumer<String, String> configReport, Duration drainGrace, Contributions contributions, BooleanSupplier dark)`
  - `PluginRuntime(Options options, ActivityNotifier notifier, BiConsumer<String, String> configReport, Contributions contributions)`; `void start(Map<String, Map<String, Object>> pluginTables, boolean dark)`
  - `ContractHarness` additions: `List<String> actions()`, `boolean invoke(String actionId, UUID windowId, UUID paneIdOrNull)`, `List<String> toolbar()`, `List<String> menu(String target)`, `List<String> status()`, `void setVariant(Variant variant)`, all in Task 5's rendering format

- [ ] **Step 1: Extend the harness and write the failing contract cases**

In `ContractHarness` add (imports `dev.jasper.sdk.Variant`, `java.util.UUID`):

```java
    /** Registered actions as {@code id|title|enabled}, in registration order. */
    List<String> actions();

    /** Invokes an action as a window would; true when an enabled action ran. */
    boolean invoke(String actionId, UUID windowId, UUID paneIdOrNull);

    /** The toolbar's plugin section: {@code button:id} or {@code menu:title:id,id}; closed actions omitted. */
    List<String> toolbar();

    /**
     * One menu target ({@code FILE}, {@code EDIT}, {@code VIEW}, {@code PANE}, {@code TAB}, {@code context},
     * {@code top:<menuId>}): {@code item:id}, {@code ---}, {@code submenu:title}, children indented two
     * spaces, sections separated by {@code ===}; items of closed actions omitted.
     */
    List<String> menu(String target);

    /** Visible status items as {@code id|side|text|tooltip|actionId}, ascending priority. */
    List<String> status();

    /** Changes the look and announces it. */
    void setVariant(Variant variant);
```

Append these cases to `PluginContractTest` (add imports `dev.jasper.sdk.Variant`, `dev.jasper.sdk.ui.ActionSpec`, `dev.jasper.sdk.ui.PluginAction`, `dev.jasper.sdk.ui.PluginMenu`, `dev.jasper.sdk.ui.Side`, `dev.jasper.sdk.ui.StandardMenu`, `dev.jasper.sdk.ui.StatusItem`, `dev.jasper.sdk.ui.StatusItemSpec`, `dev.jasper.sdk.ui.ToolbarItem`, `dev.jasper.sdk.Subscription` is already imported, `static org.assertj.core.api.Assertions.assertThatCode`):

```java
    @Test void actionsAreNamespacedUniqueInvocableAndContained() {
        List<String> ran = Collections.synchronizedList(new ArrayList<>());
        var run = new AtomicReference<PluginAction>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            run.set(context.actions().register(ActionSpec.of("test.alpha.run", "Run").withDefaultBinding("cmd+alt+j"),
                invoked -> ran.add(invoked.window().id() + "/" + invoked.pane().map(pane -> pane.id().toString()).orElse("none"))));
            context.actions().register(ActionSpec.of("test.alpha.boom", "Boom"), invoked -> { throw new IllegalStateException("handler failure"); });
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alpha.run", "Twice"), invoked -> { }));
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.beta.run", "Foreign"), invoked -> { }));
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alphabet.run", "Foreign"), invoked -> { }));
        });
        assertThat(h.actions()).containsExactly("test.alpha.run|Run|true", "test.alpha.boom|Boom|true");
        UUID window = UUID.randomUUID(), pane = UUID.randomUUID();
        h.ui(() -> {
            assertThat(h.invoke("test.alpha.run", window, pane)).isTrue();
            assertThat(h.invoke("test.alpha.run", window, null)).isTrue();
            assertThat(h.invoke("test.alpha.boom", window, null)).as("a throwing handler is contained").isTrue();
            assertThat(h.invoke("test.alpha.absent", window, null)).isFalse();
            run.get().setTitle("Run Now");
            run.get().setEnabled(false);
            assertThat(h.invoke("test.alpha.run", window, null)).isFalse();
        });
        assertThat(ran).containsExactly(window + "/" + pane, window + "/none");
        assertThat(h.actions()).containsExactly("test.alpha.run|Run Now|false", "test.alpha.boom|Boom|true");
    }

    @Test void placementsNameOnlyThePluginsOwnActionsAndVanishWithThem() {
        var run = new AtomicReference<PluginAction>();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            run.set(context.actions().register(ActionSpec.of("test.alpha.run", "Run"), invoked -> { }));
            context.actions().register(ActionSpec.of("test.alpha.stop", "Stop"), invoked -> { });
            context.toolbar().add(ToolbarItem.action("test.alpha.run"));
            context.toolbar().add(ToolbarItem.menu(new javax.swing.ImageIcon(), "Alpha", List.of("test.alpha.run", "test.alpha.stop")));
            assertThatIllegalArgumentException().isThrownBy(() -> context.toolbar().add(ToolbarItem.action("new_tab")));
            assertThatIllegalArgumentException().isThrownBy(() -> context.menus().standard(StandardMenu.FILE).add("test.alpha.absent"));
        });
        h.start(info("test.beta"), Set.of(), Set.of(), context ->
            assertThatIllegalArgumentException().as("another plugin's action").isThrownBy(() -> context.toolbar().add(ToolbarItem.action("test.alpha.run"))));
        assertThat(h.toolbar()).containsExactly("button:test.alpha.run", "menu:Alpha:test.alpha.run,test.alpha.stop");
        h.ui(() -> run.get().close());
        assertThat(h.toolbar()).containsExactly("menu:Alpha:test.alpha.stop");
        h.ui(() -> assertThatIllegalArgumentException().as("a closed action cannot be placed again")
            .isThrownBy(() -> alpha.get().toolbar().add(ToolbarItem.action("test.alpha.run"))));
    }

    @Test void menusAreMutableTreesInIndependentSections() {
        var view = new AtomicReference<PluginMenu>();
        var first = new AtomicReference<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.run", "Run"), invoked -> { });
            PluginMenu section = context.menus().standard(StandardMenu.VIEW);
            view.set(section);
            first.set(section.add("test.alpha.run"));
            section.addSeparator();
            section.submenu("More").add("test.alpha.run");
            context.menus().standard(StandardMenu.VIEW).add("test.alpha.run");
            context.menus().create("test.alpha.menu", "Alpha").add("test.alpha.run");
            context.menus().terminalContext().add("test.alpha.run");
            assertThatIllegalArgumentException().isThrownBy(() -> context.menus().create("test.alpha.menu", "Again"));
            assertThatIllegalArgumentException().isThrownBy(() -> context.menus().create("test.beta.menu", "Foreign"));
        });
        assertThat(h.menu("VIEW")).containsExactly("item:test.alpha.run", "---", "submenu:More", "  item:test.alpha.run",
            "===", "item:test.alpha.run");
        assertThat(h.menu("top:test.alpha.menu")).containsExactly("item:test.alpha.run");
        assertThat(h.menu("context")).containsExactly("item:test.alpha.run");
        assertThat(h.menu("FILE")).isEmpty();
        h.ui(() -> { first.get().close(); first.get().close(); });
        assertThat(h.menu("VIEW")).containsExactly("---", "submenu:More", "  item:test.alpha.run", "===", "item:test.alpha.run");
        h.ui(() -> view.get().clear());
        assertThat(h.menu("VIEW")).containsExactly("===", "item:test.alpha.run");
        h.ui(() -> view.get().close());
        assertThat(h.menu("VIEW")).containsExactly("item:test.alpha.run");
    }

    @Test void statusItemsAreOrderedGlobalAndInertAfterClose() {
        var late = new AtomicReference<StatusItem>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.run", "Run"), invoked -> { });
            late.set(context.statusBar().add(new StatusItemSpec("test.alpha.late", Side.RIGHT, 20)));
            StatusItem early = context.statusBar().add(new StatusItemSpec("test.alpha.early", Side.LEFT, 10));
            late.get().setText("Late");
            early.setText("two\nlines");
            early.setTooltip("tip");
            early.setAction("test.alpha.run");
            assertThatIllegalArgumentException().isThrownBy(() -> early.setAction("new_tab"));
            assertThatIllegalArgumentException().isThrownBy(() -> context.statusBar().add(new StatusItemSpec("test.alpha.early", Side.LEFT, 0)));
            assertThatIllegalArgumentException().isThrownBy(() -> context.statusBar().add(new StatusItemSpec("test.beta.item", Side.LEFT, 0)));
        });
        assertThat(h.status()).containsExactly("test.alpha.early|LEFT|two lines|tip|test.alpha.run", "test.alpha.late|RIGHT|Late||");
        h.ui(() -> late.get().setVisible(false));
        assertThat(h.status()).containsExactly("test.alpha.early|LEFT|two lines|tip|test.alpha.run");
        h.ui(() -> { late.get().close(); late.get().close(); assertThatCode(() -> late.get().setText("after close")).doesNotThrowAnyException(); });
        assertThat(h.status()).hasSize(1);
    }

    @Test void stoppingOrFailingRemovesEveryContributionAndAppearanceFollowsTheHost() {
        List<Variant> seen = Collections.synchronizedList(new ArrayList<>());
        var failed = new AtomicReference<PluginContext>();
        h.start(info("test.doomed"), Set.of(), Set.of(), context -> {
            failed.set(context);
            context.actions().register(ActionSpec.of("test.doomed.run", "Run"), invoked -> { });
            context.toolbar().add(ToolbarItem.action("test.doomed.run"));
            context.statusBar().add(new StatusItemSpec("test.doomed.item", Side.LEFT, 0)).setText("Doomed");
            throw new IllegalStateException("start failure");
        });
        assertThat(h.actions()).isEmpty();
        assertThat(h.toolbar()).isEmpty();
        assertThat(h.status()).isEmpty();
        h.ui(() -> assertThatIllegalStateException().isThrownBy(() ->
            failed.get().actions().register(ActionSpec.of("test.doomed.late", "Late"), invoked -> { })));

        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.actions().register(ActionSpec.of("test.alpha.run", "Run"), invoked -> { });
            context.menus().standard(StandardMenu.TAB).add("test.alpha.run");
            context.appearance().onChanged(seen::add);
            assertThatIllegalArgumentException().isThrownBy(() -> context.appearance().icon("no/such/icon.svg"));
        });
        assertThat(alpha.get().appearance().variant()).isEqualTo(Variant.DARK);
        h.setVariant(Variant.LIGHT);
        h.flush();
        assertThat(alpha.get().appearance().variant()).isEqualTo(Variant.LIGHT);
        assertThat(seen).containsExactly(Variant.LIGHT);
        h.stopAll();
        assertThat(h.actions()).isEmpty();
        assertThat(h.menu("TAB")).isEmpty();
    }
```

In `FakeContractTest`'s anonymous harness add the six delegations:

```java
            @Override public List<String> actions() { return host.actions(); }
            @Override public boolean invoke(String actionId, java.util.UUID windowId, java.util.UUID paneIdOrNull) { return host.invoke(actionId, windowId, paneIdOrNull); }
            @Override public List<String> toolbar() { return host.toolbar(); }
            @Override public List<String> menu(String target) { return host.menu(target); }
            @Override public List<String> status() { return host.status(); }
            @Override public void setVariant(dev.jasper.sdk.Variant variant) { host.setVariant(variant); }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-sdk-testkit:test`
Expected: compilation FAILS: `PluginContext` has no `actions()`.

- [ ] **Step 3: Grow the SDK**

In `PluginContext` add (imports `dev.jasper.sdk.ui.Actions`, `Appearance`, `Menus`, `StatusBar`, `Toolbar`), after `services()`:

```java
    /**
     * Action registration.
     *
     * @return the actions service as seen by this plugin
     */
    Actions actions();

    /**
     * The main toolbar's plugin section.
     *
     * @return the toolbar service
     */
    Toolbar toolbar();

    /**
     * Menu bar and terminal context menu contributions.
     *
     * @return the menus service
     */
    Menus menus();

    /**
     * Status bar items.
     *
     * @return the status bar service
     */
    StatusBar statusBar();

    /**
     * The application's look, change notifications and theme-aware icons.
     *
     * @return the appearance service
     */
    Appearance appearance();
```

In `plugin/package-info.java` nothing changes (it already says the package depends on every other SDK package). Set `JasperSdk.VERSION = "0.2.0"`. In `plugins/sample/src/main/resources/plugin.toml` set `sdk = ">=0.2, <0.3"`. In `BundledSamplePluginTest` nothing version-specific is asserted; in `docs/plugin-authoring.md` and the descriptor example the range is updated in Task 12.

In `FakePluginContext` add `@Override` to the five methods from Task 5; in `FakeUiTest` remove the `(FakePluginContext)` cast.

- [ ] **Step 4: Run the fake against the grown contract**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check`
Expected: PASS: 14 contract tests (9 + 5) for the fake. `jasper-app` does not compile yet; that is the next step.

- [ ] **Step 5: Wire the application runtime**

`PluginHost.Environment` becomes (import `dev.jasper.app.contributions.Contributions`):

```java
    record Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Function<String, Map<String, Object>> settings, BiConsumer<String, String> configReport,
                       Duration drainGrace, Contributions contributions, BooleanSupplier dark) { }
```

In `HostedContext` add the field and the five accessors (imports `dev.jasper.sdk.Variant`, `dev.jasper.sdk.events.AppEvents`, `dev.jasper.sdk.ui.*`):

```java
    private final HostedUi ui;
```

assigned at the end of the constructor:

```java
        this.ui = new HostedUi(id, host.environment.contributions(), host.containment, host.environment.ui(),
            host.environment.onUi(), () -> state != State.CLOSED, hosted.loader(),
            () -> host.environment.dark().getAsBoolean() ? Variant.DARK : Variant.LIGHT,
            handler -> events().subscribe(AppEvents.THEME_CHANGED, event -> handler.accept(event.variant())));
```

```java
    @Override public Actions actions() { return ui.actions(); }
    @Override public Toolbar toolbar() { return ui.toolbar(); }
    @Override public Menus menus() { return ui.menus(); }
    @Override public StatusBar statusBar() { return ui.statusBar(); }
    @Override public Appearance appearance() { return ui.appearance(); }
```

and in `teardown`, directly after `state = State.CLOSED;`:

```java
        ui.closeAll();
```

In `PluginRuntime`: add the constructor parameter and field `private final Contributions contributions;`, a field `private volatile boolean dark = true;`, change `start` to `public void start(Map<String, Map<String, Object>> pluginTables, boolean dark)` (assign `this.dark = dark;` first, and document the parameter as "whether the current look is dark"), pass `contributions, () -> this.dark` as the last two `Environment` arguments, and make `themeChanged` record the value before publishing:

```java
    public void themeChanged(boolean dark) {
        this.dark = dark;
        PluginHost current = host;
        if (current != null) current.bus.publish(EventBus.APP, AppEvents.THEME_CHANGED,
            new AppEvents.ThemeChanged(dark ? Variant.DARK : Variant.LIGHT));
    }
```

In `JasperApplication` add `private final dev.jasper.app.contributions.Contributions contributions = new dev.jasper.app.contributions.Contributions();` beside `plugins`, pass it as the fourth `PluginRuntime` constructor argument, and start with the current look: `plugins.start(configuration == null ? Map.of() : configuration.snapshot().plugins(), themes.current().chrome() == BuiltinTheme.DARK);`. Windows are connected to the model in Task 10.

- [ ] **Step 6: Update the application's harness and callers**

In `AppContractTest.host(...)` add a `Contributions` parameter and a dark flag:

```java
    static PluginHost host(Path data, Map<String, Map<String, Object>> tables, Duration drainGrace) {
        return host(data, tables, drainGrace, onEdtValue(Contributions::new), new java.util.concurrent.atomic.AtomicBoolean(true));
    }

    static <T> T onEdtValue(java.util.function.Supplier<T> supplier) {
        var value = new AtomicReference<T>();
        onEdt(() -> value.set(supplier.get()));
        return value.get();
    }

    static PluginHost host(Path data, Map<String, Map<String, Object>> tables, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark) {
        return onEdtValue(() -> new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, data::resolve, id -> tables.getOrDefault(id, Map.of()),
            (key, message) -> { }, drainGrace, contributions, dark::get)));
    }
```

and replace `newHarness()`'s host creation and add the six operations, rendering the model in Task 5's format:

```java
        Contributions contributions = onEdtValue(Contributions::new);
        var dark = new java.util.concurrent.atomic.AtomicBoolean(true);
        PluginHost host = host(data, Map.of(), Duration.ofMillis(200), contributions, dark);
```

```java
            @Override public List<String> actions() {
                return onEdtValue(() -> contributions.actions().stream()
                    .map(action -> action.id() + "|" + action.title() + "|" + action.enabled()).toList());
            }
            @Override public boolean invoke(String actionId, java.util.UUID windowId, java.util.UUID paneIdOrNull) {
                return onEdtValue(() -> contributions.action(actionId).filter(dev.jasper.app.contributions.ActionEntry::enabled).map(action -> {
                    action.invoke(new Contributions.Invocation(windowId, java.util.Optional.ofNullable(paneIdOrNull)));
                    return true;
                }).orElse(false));
            }
            @Override public List<String> toolbar() {
                return onEdtValue(() -> {
                    List<String> lines = new java.util.ArrayList<>();
                    for (var entry : contributions.toolbar()) {
                        switch (entry) {
                            case dev.jasper.app.contributions.ToolbarEntry.Button button -> {
                                if (contributions.action(button.actionId()).isPresent()) lines.add("button:" + button.actionId());
                            }
                            case dev.jasper.app.contributions.ToolbarEntry.Dropdown dropdown -> {
                                List<String> live = dropdown.actionIds().stream().filter(id -> contributions.action(id).isPresent()).toList();
                                if (!live.isEmpty()) lines.add("menu:" + dropdown.title() + ":" + String.join(",", live));
                            }
                        }
                    }
                    return lines;
                });
            }
            @Override public List<String> menu(String target) {
                return onEdtValue(() -> {
                    List<String> lines = new java.util.ArrayList<>();
                    boolean first = true;
                    for (var section : contributions.menus()) {
                        var where = section.target();
                        String key = switch (where.type()) {
                            case STANDARD -> where.key(); case TOP_LEVEL -> "top:" + where.key(); case CONTEXT -> "context";
                        };
                        if (!key.equals(target)) continue;
                        if (!first) lines.add("===");
                        first = false;
                        render(contributions, section.entries(), "", lines);
                    }
                    return lines;
                });
            }
            @Override public List<String> status() {
                return onEdtValue(() -> contributions.status().stream().filter(dev.jasper.app.contributions.StatusEntry::visible)
                    .map(item -> item.id() + "|" + (item.left() ? "LEFT" : "RIGHT") + "|" + item.text() + "|"
                        + (item.tooltip() == null ? "" : item.tooltip()) + "|" + (item.actionId() == null ? "" : item.actionId())).toList());
            }
            @Override public void setVariant(dev.jasper.sdk.Variant variant) {
                dark.set(variant == dev.jasper.sdk.Variant.DARK);
                host.bus.publish(EventBus.APP, dev.jasper.sdk.events.AppEvents.THEME_CHANGED, new dev.jasper.sdk.events.AppEvents.ThemeChanged(variant));
            }
```

with this helper in `AppContractTest`:

```java
    private static void render(Contributions contributions, List<dev.jasper.app.contributions.MenuEntry> entries, String indent, List<String> lines) {
        for (var entry : entries) {
            switch (entry) {
                case dev.jasper.app.contributions.MenuEntry.Item item -> {
                    if (contributions.action(item.actionId()).isPresent()) lines.add(indent + "item:" + item.actionId());
                }
                case dev.jasper.app.contributions.MenuEntry.Separator separator -> lines.add(indent + "---");
                case dev.jasper.app.contributions.MenuEntry.Submenu submenu -> {
                    lines.add(indent + "submenu:" + submenu.title());
                    render(contributions, submenu.entries(), indent + "  ", lines);
                }
            }
        }
    }
```

The contract calls `h.invoke(...)` from inside `h.ui(...)`; `onEdt` from the EDT would deadlock, so make `AppContractTest.onEdt` run the action directly when it is already on the EDT:

```java
    static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) { action.run(); return; }
        ...
```

In `PluginRuntimeTest` and `BundledSamplePluginTest`, pass `onEdtValue(Contributions::new)` (created once per runtime) as the new constructor argument and `true` as `start`'s second argument. In `PluginRuntimeTest.runsADevelopmentPlugin…`, `runtime.themeChanged(false)` still yields `LIGHT`.

- [ ] **Step 7: Run everything**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`: 14 contract tests pass for the fake **and** for the application; `verifyApplicationArchitecture` still confines SDK types to `dev.jasper.app.plugins`. A contract case that passes for one implementation and fails for the other means the implementations disagree; fix the implementation.

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add -A jasper-sdk jasper-sdk-testkit jasper-app plugins
git commit -m "feat: expose actions, placements and appearance through PluginContext

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 7: Contributed actions in each window: palette, shortcuts and dispatch

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/{WindowContent,package-info}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowContributionsTest.java`

**Interfaces:**
- Consumes: `Contributions`, `ActionEntry` (Task 3); `KeyBindings.withExtensions`, `idFor`, `strokes`, `ActionId.forId` (Task 1); `Command`, `CommandRegistry`.
- Produces:
  - `WindowContent`: `public UUID id()`; `public void connectContributions(Contributions model)` (EDT, once; ignored after close); package-private `void rebind()`
  - `WindowContributions` (package-private): `Contributions model()`, `Action action(String id)` (null when the action is gone), `List<KeyBindings.Extension> extensions()`, `void applyAccelerators()`, `void invoke(String id)`, `void close()`. Tasks 8 and 9 add the chrome and status rendering calls to its `changed` method.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.commands.Command;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class WindowContributionsTest {
    private static final boolean MAC = System.getProperty("os.name").startsWith("Mac");

    private static KeyStroke stroke(String binding) { return KeyBindings.withOverrides(MAC, Map.of("new_tab", binding)).strokeFor("new_tab").orElseThrow(); }

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    @Test void aContributedActionIsACommandAShortcutAndAnInvocationWithThisWindowsIdentity() throws Exception {
        edt(() -> {
            var model = new Contributions();
            List<Contributions.Invocation> seen = new ArrayList<>();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            ActionEntry early = model.addAction("dev.x.early", "Early", null, List.of(), Optional.empty(), seen::add);
            owner.connectContributions(model);
            owner.connectContributions(model);
            ActionEntry run = model.addAction("dev.x.run", "Run Tool", null, List.of("go"), Optional.of("cmd+alt+j"), seen::add);

            assertThat(owner.commands().find("dev.x.early")).as("actions registered before the window connected").isPresent();
            Command command = owner.commands().find("dev.x.run").orElseThrow();
            assertThat(command.action().getValue(Action.NAME)).isEqualTo("Run Tool");
            assertThat(command.keywords()).contains("go");
            assertThat(command.action().getValue(Action.ACCELERATOR_KEY)).isEqualTo(stroke("cmd+alt+j"));

            assertThat(owner.dispatchShortcut(stroke("cmd+alt+j"), null)).isTrue();
            owner.dispatchCommand(command);
            assertThat(seen).hasSize(2).allSatisfy(invocation -> {
                assertThat(invocation.windowId()).isEqualTo(owner.id());
                assertThat(invocation.paneId()).isEqualTo(Optional.ofNullable(owner.currentPane()).map(TerminalPane::id));
            });

            run.setEnabled(false);
            assertThat(command.action().isEnabled()).isFalse();
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+j"), null)).as("a disabled app key is still not terminal input").isTrue();
            assertThat(seen).hasSize(2);
            run.setEnabled(true);
            run.setTitle("Run It");
            assertThat(command.action().getValue(Action.NAME)).isEqualTo("Run It");
            assertThat(command.action().getValue(Command.TITLE)).isEqualTo("Run It");
            early.close();
            assertThat(owner.commands().find("dev.x.early")).isEmpty();
        });
    }

    @Test void userBindingsWinRootBindingsFollowAndRemovalUnbinds() throws Exception {
        edt(() -> {
            var model = new Contributions();
            List<Contributions.Invocation> seen = new ArrayList<>();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            owner.installRootBindings(root);
            owner.connectContributions(model);
            ActionEntry run = model.addAction("dev.x.run", "Run", null, List.of(), Optional.of("cmd+alt+j"), seen::add);
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke("cmd+alt+j"))).isEqualTo("dev.x.run");
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke("cmd+t"))).isEqualTo("new_tab");

            owner.setBindings(KeyBindings.withOverrides(MAC, Map.of("dev.x.run", "cmd+alt+u")));
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+j"), null)).isFalse();
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+u"), null)).isTrue();
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke("cmd+alt+j"))).isNull();
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke("cmd+alt+u"))).isEqualTo("dev.x.run");
            assertThat(seen).hasSize(1);

            run.close();
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+u"), null)).isFalse();
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke("cmd+alt+u"))).isNull();
            assertThat(owner.dispatchShortcut(stroke("cmd+t"), null)).as("built-ins are untouched").isTrue();
        });
    }

    @Test void aClosedWindowStopsListening() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            owner.close();
            model.addAction("dev.x.late", "Late", null, List.of(), Optional.empty(), invocation -> { });
            assertThat(owner.commands().find("dev.x.late")).isEmpty();
        });
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*WindowContributionsTest'`
Expected: compilation FAILS (`connectContributions`, `id()` not found).

- [ ] **Step 3: Write `WindowContributions`**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.commands.Command;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.lifecycle.Subscription;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractAction;
import javax.swing.Action;

/**
 * This window's Swing side of the application-wide contributions model: one {@link Action} per
 * contributed action, registered as a palette command and bound to its shortcut, kept in step with the
 * model. Chrome and status rendering hang off the same change notifications. EDT only.
 */
final class WindowContributions implements AutoCloseable {
    private final WindowContent owner;
    private final Contributions model;
    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final Map<String, Subscription> commands = new LinkedHashMap<>();
    private Subscription listening;

    WindowContributions(WindowContent owner, Contributions model) {
        this.owner = owner;
        this.model = model;
        syncActions();
        listening = model.onChanged(this::changed);
    }

    Contributions model() { return model; }

    /** The window's action for a contributed id, or null when the contributor removed it. */
    Action action(String id) { return actions.get(id); }

    /** Contributed actions in registration order, which is plugin load order: the keybinding precedence. */
    List<KeyBindings.Extension> extensions() {
        List<KeyBindings.Extension> result = new ArrayList<>();
        for (ActionEntry entry : model.actions())
            if (KeyBindings.extensionId(entry.id())) result.add(new KeyBindings.Extension(entry.id(), entry.defaultBinding()));
        return result;
    }

    void applyAccelerators() {
        actions.forEach((id, action) -> action.putValue(Action.ACCELERATOR_KEY, owner.bindings().strokeFor(id).orElse(null)));
    }

    /** From a shortcut, the palette, a menu, the toolbar or a status item. */
    void invoke(String id) {
        if (owner.closed() || owner.commandPalette() != null && owner.commandPalette().isOpen()) return;
        Optional<java.util.UUID> pane = Optional.ofNullable(owner.currentPane()).map(TerminalPane::id);
        model.action(id).ifPresent(entry -> entry.invoke(new Contributions.Invocation(owner.id(), pane)));
    }

    private void changed(Contributions.Kind kind) {
        if (kind != Contributions.Kind.ACTIONS) return;
        if (syncActions()) owner.rebind();
    }

    /** Returns whether the set of actions changed, which is when shortcuts must be recomputed. */
    private boolean syncActions() {
        boolean structural = false;
        Map<String, ActionEntry> current = new LinkedHashMap<>();
        for (ActionEntry entry : model.actions()) current.put(entry.id(), entry);
        for (String id : List.copyOf(actions.keySet())) {
            if (current.containsKey(id)) continue;
            commands.remove(id).close();
            actions.remove(id).setEnabled(false);
            structural = true;
        }
        for (ActionEntry entry : current.values()) {
            Action action = actions.get(entry.id());
            if (action == null) {
                String id = entry.id();
                action = new AbstractAction(entry.title()) {
                    @Override public void actionPerformed(ActionEvent event) { invoke(id); }
                };
                if (entry.icon() != null) action.putValue(Command.ICON, entry.icon());
                actions.put(id, action);
                List<String> keywords = new ArrayList<>(List.of(id.replace('.', ' ').replace('_', ' ')));
                keywords.addAll(entry.keywords());
                // The name is set before registration: a command needs a non-blank Action.NAME.
                commands.put(id, owner.commands().register(new Command(id, action, keywords)));
                structural = true;
            }
            action.putValue(Action.NAME, entry.title());
            action.putValue(Command.TITLE, entry.title());
            action.setEnabled(entry.enabled());
        }
        return structural;
    }

    @Override public void close() {
        if (listening == null) return;
        listening.close();
        listening = null;
        commands.values().forEach(Subscription::close);
        commands.clear();
        actions.values().forEach(action -> action.setEnabled(false));
        actions.clear();
    }
}
```

- [ ] **Step 4: Integrate with `WindowContent`**

Add imports `dev.jasper.app.contributions.Contributions` and `java.util.UUID`. Add fields beside `bindings`:

```java
    private final UUID id = UUID.randomUUID();
    private KeyBindings baseBindings;
    private WindowContributions contributed;
```

In the main constructor, replace `this.bindings = bindings;` with:

```java
        this.baseBindings = bindings;
        this.bindings = bindings;
```

Add, near `isActiveAndOpen()`:

```java
    /** Stable identity of this window for extensions; never a Swing object. */
    public UUID id() { return id; }

    /**
     * Connects this window to the application-wide contributions model, once. Contributed actions
     * become palette commands and shortcuts here; their toolbar, menu and status placements follow.
     */
    public void connectContributions(Contributions model) {
        if (closed || contributed != null) return;
        contributed = new WindowContributions(this, Objects.requireNonNull(model));
        rebind();
    }
```

Replace `installRootBindings`'s loop, `removeRootBindings` and `setBindings` so they cover every bound id and layer contributed actions over the saved bindings:

```java
    void installRootBindings(JRootPane root) {
        removeRootBindings();
        bindingRoot = root;
        bindings.strokes().forEach((actionId, stroke) -> {
            root.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(stroke, actionId);
            root.getActionMap().put(actionId, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) {
                    dispatchShortcut(stroke, KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
                }
            });
        });
        commandPalette.install(root);
        syncPaletteDispatcher();
    }
```

```java
    private void removeRootBindings() {
        if (bindingRoot == null) return;
        bindings.strokes().forEach((actionId, stroke) -> {
            bindingRoot.getInputMap(WHEN_IN_FOCUSED_WINDOW).remove(stroke);
            bindingRoot.getActionMap().remove(actionId);
        });
        bindingRoot = null;
    }

    void setBindings(KeyBindings replacement) {
        baseBindings = Objects.requireNonNull(replacement);
        rebind();
    }

    /** Effective bindings are the saved ones plus contributed actions in registration order. */
    void rebind() {
        JRootPane root = bindingRoot;
        removeRootBindings();
        bindings = contributed == null ? baseBindings : baseBindings.withExtensions(contributed.extensions()).bindings();
        for (ActionId id : ActionId.values())
            action(id).putValue(Action.ACCELERATOR_KEY, bindings.strokeFor(id).orElse(null));
        if (contributed != null) contributed.applyAccelerators();
        if (root != null) installRootBindings(root);
        toolbar().revalidate(); toolbar().repaint();
        windowTabs.refresh();
    }
```

(`removeRootBindings` must run while `bindings` still holds the old table, which the order above guarantees. Keep whatever the existing `installRootBindings` does after its loop; only the loop changes.)

Replace the lookup in `dispatchShortcut`:

```java
    boolean dispatchShortcut(KeyStroke stroke, Component source) {
        if (paletteKeys.dispatchShortcut(stroke, source == null ? this : source)) return true;
        Optional<String> found = bindings.idFor(stroke);
        if (found.isEmpty()) return false;
        Optional<ActionId> builtIn = ActionId.forId(found.get());
        if (builtIn.isEmpty()) {
            // A recognized contributed shortcut is an app key, never terminal input, even while disabled.
            if (contributed != null) contributed.invoke(found.get());
            return true;
        }
        ActionId id = builtIn.get();
        if (source instanceof JTextComponent && (id == ActionId.COPY || id == ActionId.PASTE)) return false;
        updateActions();
        // A recognized but unavailable command is still an app key, never terminal input.
        if (action(id).isEnabled()) invoke(id);
        return true;
    }
```

In `close()`, before `commandPalette.close(); windowCommands.close(); commands.close();`:

```java
        if (contributed != null) { contributed.close(); contributed = null; }
```

In `workspace/package-info.java`, add `dev.jasper.app.contributions` to the allowed outgoing dependencies, after `dev.jasper.app.config`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.palette.*' verifyApplicationArchitecture`
Expected: PASS, including every pre-existing workspace test (a window that is never connected behaves exactly as before).

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: make contributed actions palette commands with rebindable shortcuts

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: Toolbar, menu bar and context menu rendering

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/{WindowChrome,WindowContributions}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeContributionsTest.java`

**Interfaces:**
- Consumes: `WindowContributions.action(String)`, `model()`; `ToolbarEntry`, `MenuSection`, `MenuEntry`, `MenuTarget`.
- Produces on `WindowChrome` (package-private): `void connect(WindowContributions source)`, `void renderContributedToolbar()`, `void renderContributedMenus()`; `contextMenu()` appends contributed context sections.

Layout rules: contributed toolbar controls sit between the built-in group's trailing separator and the glue, so Settings and Reload stay right-aligned. A contributed standard-menu section follows a separator at the end of its menu. Contributed top-level menus follow Tab. Entries whose action is gone, and submenus and sections left empty by that, are not rendered.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuSection;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.contributions.ToolbarEntry;
import java.awt.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class WindowChromeContributionsTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    private static List<String> buttons(WindowContent owner) {
        List<String> labels = new ArrayList<>();
        for (Component child : owner.toolbar().getComponents())
            if (child instanceof JButton button) labels.add(String.valueOf(button.getClientProperty("label")));
        return labels;
    }

    private static List<String> items(JMenu menu) {
        List<String> labels = new ArrayList<>();
        for (Component child : menu.getMenuComponents())
            labels.add(child instanceof JMenu sub ? "submenu:" + sub.getText() : child instanceof JMenuItem item ? item.getText() : "---");
        return labels;
    }

    private static JMenu menu(WindowContent owner, String title) {
        for (int i = 0; i < owner.menuBar().getMenuCount(); i++)
            if (owner.menuBar().getMenu(i).getText().equals(title)) return owner.menuBar().getMenu(i);
        return null;
    }

    @Test void toolbarControlsSitBeforeTheRightAlignedGroupAndFollowModeAndRemoval() throws Exception {
        edt(() -> {
            var model = new Contributions();
            List<Contributions.Invocation> seen = new ArrayList<>();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            List<String> before = buttons(owner);
            owner.connectContributions(model);
            ActionEntry run = model.addAction("dev.x.run", "Run Tool", null, List.of(), Optional.empty(), seen::add);
            model.addAction("dev.x.stop", "Stop Tool", null, List.of(), Optional.empty(), seen::add);
            model.addToolbar(new ToolbarEntry.Button("dev.x.run"));
            model.addToolbar(new ToolbarEntry.Dropdown(new javax.swing.ImageIcon(), "Tool", List.of("dev.x.run", "dev.x.stop")));
            model.addToolbar(new ToolbarEntry.Button("dev.x.never-registered"));

            List<String> after = buttons(owner);
            assertThat(after).containsSubsequence("Find", "Run Tool", "Tool", "Settings");
            assertThat(after).hasSize(before.size() + 2);
            JButton button = null;
            for (Component child : owner.toolbar().getComponents())
                if (child instanceof JButton candidate && "Run Tool".equals(candidate.getClientProperty("label"))) button = candidate;
            assertThat(button.getIcon()).as("a fallback icon keeps the toolbar's icon-led layout").isNotNull();
            button.doClick();
            assertThat(seen).hasSize(1);

            owner.setToolbarMode(ToolbarMode.ICONS);
            assertThat(button.getText()).isNull();
            owner.setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
            run.setTitle("Run It");
            assertThat(buttons(owner)).contains("Run It").doesNotContain("Run Tool");
            run.close();
            assertThat(buttons(owner)).doesNotContain("Run It").contains("Tool");
        });
    }

    @Test void menusGainSectionsTopLevelMenusAndContextEntries() throws Exception {
        edt(() -> {
            var model = new Contributions();
            List<Contributions.Invocation> seen = new ArrayList<>();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            int viewBefore = menu(owner, "View").getMenuComponentCount();
            int contextBefore = owner.chrome().contextMenu().getComponentCount();
            owner.connectContributions(model);
            ActionEntry run = model.addAction("dev.x.run", "Run Tool", null, List.of(), Optional.empty(), seen::add);
            MenuSection view = model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.VIEW));
            view.set(List.of(new MenuEntry.Item("dev.x.run"), new MenuEntry.Separator(),
                new MenuEntry.Submenu("More", List.of(new MenuEntry.Item("dev.x.run"))),
                new MenuEntry.Submenu("Empty", List.of(new MenuEntry.Item("dev.x.gone")))));
            MenuSection top = model.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Tool"));
            top.set(List.of(new MenuEntry.Item("dev.x.run")));
            model.addMenuSection(MenuTarget.terminalContext()).set(List.of(new MenuEntry.Item("dev.x.run")));

            List<String> viewItems = items(menu(owner, "View"));
            assertThat(viewItems).hasSize(viewBefore + 4);
            assertThat(viewItems.subList(viewBefore, viewItems.size())).containsExactly("---", "Run Tool", "---", "submenu:More");
            assertThat(owner.menuBar().getMenu(owner.menuBar().getMenuCount() - 1).getText()).isEqualTo("Tool");
            assertThat(items(menu(owner, "Tool"))).containsExactly("Run Tool");
            ((JMenuItem) menu(owner, "Tool").getMenuComponent(0)).doClick();
            assertThat(seen).hasSize(1);

            JPopupMenu context = owner.chrome().contextMenu();
            assertThat(context.getComponentCount()).isEqualTo(contextBefore + 2);
            assertThat(((JMenuItem) context.getComponent(contextBefore + 1)).getText()).isEqualTo("Run Tool");

            top.set(List.of());
            assertThat(menu(owner, "Tool")).as("an empty contributed menu is not shown").isNull();
            run.close();
            assertThat(items(menu(owner, "View"))).hasSize(viewBefore);
            assertThat(owner.chrome().contextMenu().getComponentCount()).isEqualTo(contextBefore);
        });
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*WindowChromeContributionsTest'`
Expected: FAIL: the toolbar and menus do not change.

- [ ] **Step 3: Render in `WindowChrome`**

Add imports `dev.jasper.app.commands.Command`, `dev.jasper.app.contributions.MenuEntry`, `MenuSection`, `MenuTarget`, `ToolbarEntry`, `java.util.ArrayList`, `java.util.LinkedHashMap`, `java.util.List`, `java.util.Map`. Add fields:

```java
    private final Component toolbarGlue = Box.createHorizontalGlue();
    private final java.util.EnumMap<MenuTarget.Slot, JMenu> standardMenus = new java.util.EnumMap<>(MenuTarget.Slot.class);
    private final List<Component> contributedToolbar = new ArrayList<>();
    private final Map<JMenu, List<Component>> contributedSections = new LinkedHashMap<>();
    private final List<JMenu> contributedMenus = new ArrayList<>();
    private WindowContributions contributions;
```

In the constructor: after `menuBar.add(file); … menuBar.add(tab);` record the slots, and use the glue field instead of an anonymous glue:

```java
        standardMenus.put(MenuTarget.Slot.FILE, file); standardMenus.put(MenuTarget.Slot.EDIT, edit);
        standardMenus.put(MenuTarget.Slot.VIEW, view); standardMenus.put(MenuTarget.Slot.PANE, pane);
        standardMenus.put(MenuTarget.Slot.TAB, tab);
```

```java
        toolbar.add(toolbarGlue);
```

(replacing `toolbar.add(Box.createHorizontalGlue());`). Extract the menu listener so contributed top-level menus refresh action state like built-in ones: in `menu(String label, ActionId... ids)` replace the inline `menu.addMenuListener(...)` with `observe(menu);` and add:

```java
    private JMenu observe(JMenu menu) {
        menu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent event) { owner.updateActions(); }
            @Override public void menuDeselected(MenuEvent event) {}
            @Override public void menuCanceled(MenuEvent event) {}
        });
        return menu;
    }
```

Make `ReferenceButton` tolerate contributed controls, which have no `ActionId`: add a field `private boolean chevron;`, and change the two places that assume an id:

```java
            if ((id == ActionId.SPLIT_RIGHT || chevron) && labels()) width += UIScale.scale(16);
            int trim = labels() && id != null ? switch (id) { case NEW_TAB -> 6; case NEW_WINDOW -> 3; case FIND -> 4; default -> 0; } : 0;
```

```java
                if (id == ActionId.SPLIT_RIGHT || chevron) {
```

Then add the rendering:

```java
    void connect(WindowContributions source) {
        contributions = source;
        renderContributedToolbar();
        renderContributedMenus();
    }

    private ReferenceButton contributedButton(Action action, String label, Icon icon) {
        ReferenceButton button = new ReferenceButton(action, null);
        button.setText(owner.toolbarMode() == ToolbarMode.ICONS ? null : label);
        button.putClientProperty("label", label);
        button.setIcon(icon != null ? icon : AppIcons.icon("command"));
        button.setFocusable(false);
        button.setBorder(BorderFactory.createEmptyBorder()); button.setContentAreaFilled(false);
        button.setIconTextGap(UIScale.scale(8));
        button.getAccessibleContext().setAccessibleName(label);
        button.setToolTipText(label);
        button.setFont(button.chromeFont());
        return button;
    }

    /** Rebuilds the plugin section, which sits between the built-in group and the right-aligned group. */
    void renderContributedToolbar() {
        contributedToolbar.forEach(toolbar::remove);
        contributedToolbar.clear();
        if (contributions != null) {
            for (ToolbarEntry entry : contributions.model().toolbar()) {
                switch (entry) {
                    case ToolbarEntry.Button placed -> {
                        Action action = contributions.action(placed.actionId());
                        if (action != null) contributedToolbar.add(contributedButton(action,
                            (String) action.getValue(Action.NAME), (Icon) action.getValue(Command.ICON)));
                    }
                    case ToolbarEntry.Dropdown dropdown -> {
                        List<Action> live = new ArrayList<>();
                        for (String id : dropdown.actionIds()) if (contributions.action(id) != null) live.add(contributions.action(id));
                        if (live.isEmpty()) break;
                        ReferenceButton button = contributedButton(null, dropdown.title(), dropdown.icon());
                        button.chevron = true;
                        button.addActionListener(event -> {
                            owner.updateActions();
                            JPopupMenu popup = new JPopupMenu();
                            live.forEach(popup::add);
                            popup.show(button, 0, button.getHeight());
                        });
                        contributedToolbar.add(button);
                    }
                }
            }
            int index = toolbar.getComponentIndex(toolbarGlue);
            for (Component control : contributedToolbar) toolbar.add(control, index++);
        }
        toolbar.revalidate(); toolbar.repaint();
    }

    private List<Component> build(List<MenuEntry> entries) {
        List<Component> built = new ArrayList<>();
        for (MenuEntry entry : entries) {
            switch (entry) {
                case MenuEntry.Item item -> {
                    Action action = contributions.action(item.actionId());
                    if (action != null) built.add(new JMenuItem(action));
                }
                case MenuEntry.Separator separator -> built.add(new JPopupMenu.Separator());
                case MenuEntry.Submenu submenu -> {
                    List<Component> children = build(submenu.entries());
                    if (children.stream().noneMatch(child -> child instanceof JMenuItem)) break;
                    JMenu menu = new JMenu(submenu.title());
                    children.forEach(menu::add);
                    built.add(menu);
                }
            }
        }
        // A section reduced to separators by vanished actions renders as nothing.
        return built.stream().anyMatch(child -> child instanceof JMenuItem) ? built : List.of();
    }

    void renderContributedMenus() {
        contributedSections.forEach((menu, added) -> added.forEach(menu::remove));
        contributedSections.clear();
        contributedMenus.forEach(menuBar::remove);
        contributedMenus.clear();
        if (contributions != null) {
            for (MenuSection section : contributions.model().menus()) {
                List<Component> built = build(section.entries());
                if (built.isEmpty()) continue;
                switch (section.target().type()) {
                    case STANDARD -> {
                        JMenu menu = standardMenus.get(MenuTarget.Slot.valueOf(section.target().key()));
                        List<Component> added = contributedSections.computeIfAbsent(menu, key -> new ArrayList<>());
                        var separator = new JPopupMenu.Separator();
                        menu.add(separator); added.add(separator);
                        for (Component child : built) { menu.add(child); added.add(child); }
                    }
                    case TOP_LEVEL -> {
                        JMenu menu = observe(new JMenu(section.target().title()));
                        built.forEach(menu::add);
                        menuBar.add(menu); contributedMenus.add(menu);
                    }
                    case CONTEXT -> { }
                }
            }
        }
        menuBar.revalidate(); menuBar.repaint();
    }
```

`JMenuItem` is a `JMenu`'s superclass, so `child instanceof JMenuItem` is true for submenus too, which is what both emptiness checks want.

Extend `contextMenu()`, before `return menu;`:

```java
        if (contributions != null) {
            for (MenuSection section : contributions.model().menus()) {
                if (section.target().type() != MenuTarget.Type.CONTEXT) continue;
                List<Component> built = build(section.entries());
                if (built.isEmpty()) continue;
                menu.addSeparator();
                built.forEach(menu::add);
            }
        }
```

- [ ] **Step 4: Drive it from `WindowContributions`**

In the constructor, after `syncActions();`, add `owner.chrome().connect(this);`. Replace `changed` with:

```java
    private void changed(Contributions.Kind kind) {
        switch (kind) {
            case ACTIONS -> {
                if (syncActions()) owner.rebind();
                // Titles label toolbar buttons and a vanished action removes its placements.
                owner.chrome().renderContributedToolbar();
                owner.chrome().renderContributedMenus();
            }
            case TOOLBAR -> owner.chrome().renderContributedToolbar();
            case MENUS -> owner.chrome().renderContributedMenus();
            case STATUS -> { }
        }
    }
```

In `close()`, after `actions.clear();`, add `owner.chrome().connect(null);` so a closing window drops its contributed controls.

`WindowChrome.connect(null)` must work: both render methods already treat a null `contributions` as "nothing contributed".

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*'`
Expected: PASS. If a pre-existing toolbar layout or preview test counts toolbar children, it is unaffected: nothing is contributed in those tests, and the glue is the same component in the same position.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: render contributed toolbar controls, menus and context entries

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: Status bar items

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/{WindowStatusBar,WindowContributions}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowStatusBarContributionsTest.java`

**Interfaces:**
- Produces on `WindowStatusBar` (package-private): `void setContributed(List<StatusEntry> entries, java.util.function.Function<String, Action> actions)`; `List<JButton> contributedItems(boolean left)` for tests.

Layout contract: the existing class promises that long shell or path text never displaces the dimensions and configuration state. Contributed items keep that promise: the right segment is laid out first at its preferred width; right-side items take up to a third of what remains, left-side items up to a quarter, and the shell/directory segment gets the rest and truncates as it does today.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.StatusEntry;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class WindowStatusBarContributionsTest {
    @Test void itemsRenderInPriorityOrderOnTheirSideAndClickTheirAction() {
        var model = new Contributions();
        List<String> clicks = new ArrayList<>();
        Action run = new AbstractAction("Run") {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { clicks.add("run"); }
        };
        StatusEntry lock = model.addStatus("dev.x.lock", false, 20);
        StatusEntry sync = model.addStatus("dev.x.sync", false, 10);
        StatusEntry host = model.addStatus("dev.x.host", true, 0);
        StatusEntry hidden = model.addStatus("dev.x.hidden", true, 5);
        lock.setText("Locked"); lock.setTooltip("Vault is locked"); lock.setActionId("dev.x.run");
        sync.setText("Synced");
        host.setText("example.org");
        hidden.setVisible(false);

        var bar = new WindowStatusBar();
        bar.setMetadata("zsh", "/Users/someone/a/very/long/path/that/keeps/going/and/going", "120x40", true, true);
        bar.setContributed(model.status(), Map.of("dev.x.run", run)::get);
        bar.setSize(900, 30);
        bar.doLayout();

        assertThat(bar.contributedItems(false)).extracting(JButton::getText).containsExactly("Synced", "Locked");
        assertThat(bar.contributedItems(true)).extracting(JButton::getText).containsExactly("example.org");
        JButton locked = bar.contributedItems(false).get(1);
        assertThat(locked.getToolTipText()).isEqualTo("Vault is locked");
        locked.doClick();
        bar.contributedItems(false).get(0).doClick();
        assertThat(clicks).containsExactly("run");

        run.setEnabled(false);
        bar.setContributed(model.status(), Map.of("dev.x.run", run)::get);
        assertThat(bar.contributedItems(false).get(1).isEnabled()).isFalse();
        bar.setContributed(List.of(), id -> null);
        assertThat(bar.contributedItems(false)).isEmpty();
        assertThat(bar.contributedItems(true)).isEmpty();
    }

    @Test void theConfigurationSegmentKeepsItsFullWidthWhenSpaceIsTight() {
        var model = new Contributions();
        for (int i = 0; i < 6; i++) model.addStatus("dev.x.item" + i, i % 2 == 0, i).setText("A fairly long status item " + i);
        var bar = new WindowStatusBar();
        bar.setMetadata("zsh", "/a/long/directory/name/that/would/like/more/room", "120x40", false, false);
        bar.setContributed(model.status(), id -> null);
        bar.setSize(420, 30);
        bar.doLayout();
        JButton config = bar.configButton();
        assertThat(config.getParent().getWidth()).isEqualTo(config.getParent().getPreferredSize().width);
        assertThat(config.getParent().getX() + config.getParent().getWidth()).isLessThanOrEqualTo(bar.getWidth());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*WindowStatusBarContributionsTest'`
Expected: compilation FAILS, `setContributed` not found.

- [ ] **Step 3: Implement**

In `WindowStatusBar` add imports `dev.jasper.app.contributions.StatusEntry`, `java.util.ArrayList`, `java.util.List`, `java.util.function.Function`. Add fields and register the two item rows in the constructor (`add(left); add(right);` becomes `add(left); add(leftItems); add(rightItems); add(right);`):

```java
    private final Box leftItems = Box.createHorizontalBox();
    private final Box rightItems = Box.createHorizontalBox();
```

```java
    /** Replaces the contributed items. An item with a live action is a button; any other is inert text. */
    void setContributed(List<StatusEntry> entries, Function<String, Action> actions) {
        leftItems.removeAll();
        rightItems.removeAll();
        for (StatusEntry entry : entries) {
            if (!entry.visible()) continue;
            var item = new JButton(entry.text(), entry.icon());
            item.setBorder(BorderFactory.createEmptyBorder()); item.setContentAreaFilled(false); item.setOpaque(false);
            item.setFocusable(false); item.putClientProperty("html.disable", true);
            item.setIconTextGap(UIScale.scale(5));
            item.setToolTipText(entry.tooltip());
            item.getAccessibleContext().setAccessibleName(entry.text().isEmpty() && entry.tooltip() != null ? entry.tooltip() : entry.text());
            Action action = entry.actionId() == null ? null : actions.apply(entry.actionId());
            if (action != null) {
                item.setEnabled(action.isEnabled());
                item.addActionListener(event -> action.actionPerformed(event));
            } else {
                item.setRolloverEnabled(false);
            }
            Box row = entry.left() ? leftItems : rightItems;
            if (row.getComponentCount() > 0) row.add(Box.createHorizontalStrut(UIScale.scale(14)));
            row.add(item);
        }
        refreshTheme();
        revalidate(); repaint();
    }

    List<JButton> contributedItems(boolean leftSide) {
        List<JButton> items = new ArrayList<>();
        for (Component child : (leftSide ? leftItems : rightItems).getComponents()) if (child instanceof JButton button) items.add(button);
        return items;
    }
```

In `refreshTheme()`, after `left.refreshTheme(); right.refreshTheme();`, style the items like the segments (the rows are field initializers, so they exist when the constructor calls `refreshTheme()`):

```java
        for (Box row : new Box[]{leftItems, rightItems})
            for (Component child : row.getComponents()) if (child instanceof JButton item) {
                item.setForeground(muted());
                item.setFont(UIManager.getFont("Label.font").deriveFont(UIScale.scale(10f)));
            }
```

Replace `doLayout()`:

```java
    @Override public void doLayout() {
        int edge = Math.min(UIScale.scale(14), getWidth() / 2), leftInset = Math.min(getWidth(), UIScale.scale(26));
        int gap = UIScale.scale(14);
        int available = Math.max(0, getWidth() - edge * 2);
        int rightWidth = Math.min(available, right.getPreferredSize().width);
        right.setBounds(Math.max(edge, getWidth() - edge - rightWidth), 0, rightWidth, getHeight());
        int remaining = Math.max(0, right.getX() - leftInset - edge);
        int rightItemsWidth = rightItems.getComponentCount() == 0 ? 0 : Math.min(rightItems.getPreferredSize().width, remaining / 3);
        int rightItemsSpace = rightItemsWidth == 0 ? 0 : rightItemsWidth + gap;
        rightItems.setBounds(right.getX() - rightItemsSpace, 0, rightItemsWidth, getHeight());
        remaining -= rightItemsSpace;
        int leftItemsWidth = leftItems.getComponentCount() == 0 ? 0 : Math.min(leftItems.getPreferredSize().width, remaining / 4);
        int leftItemsSpace = leftItemsWidth == 0 ? 0 : leftItemsWidth + gap;
        int leftWidth = Math.max(0, Math.min(left.getPreferredSize().width, remaining - leftItemsSpace));
        if (leftItemsWidth == 0) leftWidth = Math.max(0, remaining);
        left.setBounds(leftInset, 0, leftWidth, getHeight());
        leftItems.setBounds(leftInset + leftWidth + gap, 0, leftItemsWidth, getHeight());
    }
```

With nothing contributed this is the old layout exactly: `left` spans from the inset to the right segment.

- [ ] **Step 4: Drive it from `WindowContributions`**

Add `renderStatus()`, call it at the end of the constructor (after `owner.chrome().connect(this);`), and make `changed` its final form: an action's existence and enabled state affect status items too.

```java
    private void renderStatus() { owner.status().setContributed(model.status(), this::action); }

    private void changed(Contributions.Kind kind) {
        switch (kind) {
            case ACTIONS -> {
                if (syncActions()) owner.rebind();
                // Titles label toolbar buttons, and a vanished or disabled action changes every placement of it.
                owner.chrome().renderContributedToolbar();
                owner.chrome().renderContributedMenus();
                renderStatus();
            }
            case TOOLBAR -> owner.chrome().renderContributedToolbar();
            case MENUS -> owner.chrome().renderContributedMenus();
            case STATUS -> renderStatus();
        }
    }
```

In `close()`, after `owner.chrome().connect(null);`, add `owner.status().setContributed(List.of(), id -> null);`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*'`
Expected: PASS, including the pre-existing status bar tests (unchanged layout when nothing is contributed).

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: render contributed status items within the bounded status layout

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 10: Application wiring and keybinding diagnostics

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/{JasperApplication,ConfigurationController}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`

**Interfaces:**
- Consumes: `Contributions`, `WindowContent.connectContributions`, `KeyBindings.withExtensions`, `ConfigurationController.report`.
- Produces: `ConfigurationController.macOs()` (package-private); `JasperApplication.bindingProblems()` (package-private) → `List<KeyBindings.Problem>` for the current configuration and contributed actions.

Behavior: every new window is connected to the model. After plugins start, and after every configuration reload, the application resolves bindings once: a user binding for an id that no registered action has becomes a configuration warning keyed `keybindings."<id>"`; a dropped plugin default is logged at INFO. An action registered after startup is evaluated at the next reload; windows themselves rebind immediately.

- [ ] **Step 1: Write the failing test**

Add to `JasperApplicationPluginsTest`:

```java
    private static final String BINDING_FIXTURE = """
        package fix.keys;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import dev.jasper.sdk.ui.ActionSpec;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.actions().register(ActionSpec.of("dev.example.keys.palette", "Steal The Palette Key").withDefaultBinding("cmd+k"), invoked -> { });
                context.actions().register(ActionSpec.of("dev.example.keys.bound", "Bound By The User"), invoked -> { });
            }
        }
        """;

    @Test void bindingProblemsSeparateUnknownUserIdsFromDroppedPluginDefaults() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        java.nio.file.Files.writeString(dirs.configFile(), """
            [keybindings]
            "dev.example.keys.bound" = "cmd+alt+b"
            "dev.example.gone.action" = "cmd+alt+g"
            """);
        Path dev = home.resolve("keys-plugin");
        PluginJars.build(dev, "keys.jar", PluginJars.descriptor("dev.example.keys", "1.0.0", "fix.keys.Main"),
            Map.of("fix.keys.Main", BINDING_FIXTURE), List.of());
        boolean mac = System.getProperty("os.name").startsWith("Mac");
        var worker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        var service = new dev.jasper.app.config.ConfigService(dirs.configFile(), mac, worker, javax.swing.SwingUtilities::invokeLater);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { });
                application[0].startPlugins(null, dev, false, dirs);
                assertThat(application[0].bindingProblems()).extracting(problem -> problem.kind() + " " + problem.actionId())
                    .containsExactlyInAnyOrder("UNKNOWN_ACTION dev.example.gone.action", "DEFAULT_DROPPED dev.example.keys.palette");
            });
        } finally {
            edt(application[0]::quit);
            worker.shutdownNow();
        }
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*JasperApplicationPluginsTest'`
Expected: compilation FAILS, `bindingProblems` not found.

- [ ] **Step 3: Implement**

In `ConfigurationController` add:

```java
    /** The platform the saved shortcuts were parsed for. */
    boolean macOs() { return service.macOs(); }
```

In `JasperApplication` add imports `dev.jasper.app.config.KeyBindings` and `dev.jasper.app.contributions.ActionEntry`, then:

```java
    /** What resolving the saved shortcuts against the contributed actions could not honor, right now. */
    List<KeyBindings.Problem> bindingProblems() {
        boolean macOs = configuration == null ? System.getProperty("os.name").startsWith("Mac") : configuration.macOs();
        ConfigSnapshot snapshot = configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot();
        List<KeyBindings.Extension> extensions = new ArrayList<>();
        for (ActionEntry action : contributions.actions())
            if (KeyBindings.extensionId(action.id())) extensions.add(new KeyBindings.Extension(action.id(), action.defaultBinding()));
        return snapshot.bindings(macOs).withExtensions(extensions).problems();
    }

    /** A user's binding for an unknown action is theirs to fix; a plugin's losing default is only worth a log line. */
    private void reportBindingProblems() {
        for (KeyBindings.Problem problem : bindingProblems()) {
            if (problem.kind() == KeyBindings.Problem.Kind.UNKNOWN_ACTION && configuration != null)
                configuration.report("keybindings.\"" + problem.actionId() + "\"", problem.message());
            else LOG.log(System.Logger.Level.INFO, "Keybinding for " + problem.actionId() + ": " + problem.message());
        }
    }
```

Call it at the end of `startPlugins` (after the theme subscription) and in the constructor's `onSnapshot` listener, after the plugin line, because a reload clears earlier reports:

```java
            if (plugins != null) { plugins.configurationChanged(snapshot.plugins()); reportBindingProblems(); }
```

In `newWindow`, directly after `if (configuration != null) configuration.register(window.content());`:

```java
        window.content().connectContributions(contributions);
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*'`
Expected: PASS. `newWindow` creates a native frame and is not covered headlessly, as today; the connection it makes is covered by Task 7's tests of `connectContributions`.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: connect windows to contributions and report keybinding problems

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 11: The sample plugin's actions and placements

**Files:**
- Create: `plugins/sample/src/main/resources/dev/jasper/sample/flask.svg`
- Modify: `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java`
- Modify: `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java`

**Interfaces:**
- Produces, when `demo_ui = true`: action `dev.jasper.sample.demo` ("Run Sample Activity", default `cmd+alt+j`), a toolbar button, a top-level "Sample" menu, a View menu entry, a terminal context entry, and status item `dev.jasper.sample.status` on the right whose text follows the sample's own activity events.

- [ ] **Step 1: Write the failing tests**

Append to `SamplePluginTest`:

```java
    @Test void contributesNothingToTheChromeByDefault() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.actions()).isEmpty();
            assertThat(host.toolbar()).isEmpty();
            assertThat(host.status()).isEmpty();
        }
    }

    @Test void theDemoUiPlacesOneActionEverywhereAndItsStatusFollowsTheActivity() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_ui", true, "demo_step_millis", 0L));
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).containsExactly("dev.jasper.sample.demo|Run Sample Activity|true");
            assertThat(host.toolbar()).containsExactly("button:dev.jasper.sample.demo");
            assertThat(host.menu("top:dev.jasper.sample.menu")).containsExactly("item:dev.jasper.sample.demo");
            assertThat(host.menu("VIEW")).containsExactly("item:dev.jasper.sample.demo");
            assertThat(host.menu("context")).containsExactly("item:dev.jasper.sample.demo");
            assertThat(host.status()).containsExactly("dev.jasper.sample.status|RIGHT|Sample: idle|Run the sample activity|dev.jasper.sample.demo");

            assertThat(host.invoke("dev.jasper.sample.demo", java.util.UUID.randomUUID(), null)).isTrue();
            assertThat(host.actions()).as("no second run while one is going").containsExactly("dev.jasper.sample.demo|Run Sample Activity|false");
            assertThat(host.runBackground()).isEqualTo(1);
            host.flush();
            assertThat(host.status()).singleElement().asString().contains("|Sample: ready|");
            assertThat(host.actions()).containsExactly("dev.jasper.sample.demo|Run Sample Activity|true");
        }
    }
```

In `BundledSamplePluginTest`, add `"demo_ui", true` to the sample's table and, after the runtime starts, assert that the real jar's icon and placements reached the model (the test already owns a `Contributions` from Task 6; name it `contributions`):

```java
        onEdt(() -> {
            assertThat(contributions.action("dev.jasper.sample.demo")).get()
                .satisfies(action -> assertThat(action.icon()).as("an SVG from the plugin's own jar").isNotNull());
            assertThat(contributions.toolbar()).hasSize(1);
            assertThat(contributions.status()).singleElement().satisfies(item -> assertThat(item.text()).startsWith("Sample:"));
        });
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-sample:test`
Expected: FAIL: `theDemoUiPlacesOneActionEverywhere…` finds no actions.

- [ ] **Step 3: Add the icon**

`plugins/sample/src/main/resources/dev/jasper/sample/flask.svg` (monochrome strokes, so the application can recolor it):

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#6e6e6e" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <path d="M9 3l6 0" />
  <path d="M10 9l4 0" />
  <path d="M10 3v6l-4 11a.7 .7 0 0 0 .5 1h11a.7 .7 0 0 0 .5 -1l-4 -11v-6" />
</svg>
```

- [ ] **Step 4: Extend the plugin**

Replace `SamplePlugin` with:

```java
package dev.jasper.sample;

import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.ToolbarItem;
import java.util.List;
import java.util.Optional;

/**
 * Logs, listens for theme changes and, when configured, shows a short activity on Buddy
 * ({@code demo_activity = true}) and one action placed everywhere the SDK allows ({@code demo_ui = true}).
 */
public final class SamplePlugin implements Plugin {
    private static final int STEPS = 10;
    private static final String DEMO = "dev.jasper.sample.demo";

    /** Created by the runtime. */
    public SamplePlugin() { }

    // example:plugin:start
    @Override public void start(PluginContext context) {
        context.log().log(System.Logger.Level.INFO, "Sample plugin " + context.plugin().version() + " started");
        context.events().subscribe(AppEvents.THEME_CHANGED,
            event -> context.log().log(System.Logger.Level.INFO, "The look is now " + event.variant()));
        long delay = context.config().integer("demo_step_millis").orElse(300);
        if (delay < 0 || delay > 5000) {
            context.config().report("demo_step_millis", "Use 0 to 5000; using 300.");
            delay = 300;
        }
        long stepMillis = delay;
        if (context.config().bool("demo_ui").orElse(false)) installUi(context, stepMillis);
        if (context.config().bool("demo_activity").orElse(false))
            context.background().execute(() -> demo(context, stepMillis));
    }
    // example:plugin:end

    // example:pluginui:start
    private static void installUi(PluginContext context, long stepMillis) {
        PluginAction[] demo = new PluginAction[1];
        demo[0] = context.actions().register(ActionSpec.of(DEMO, "Run Sample Activity")
                .withIcon(context.appearance().icon("dev/jasper/sample/flask.svg"))
                .withKeywords(List.of("sample", "demo", "activity"))
                .withDefaultBinding("cmd+alt+j"),
            invoked -> {
                demo[0].setEnabled(false);
                context.background().execute(() -> demo(context, stepMillis));
            });
        context.toolbar().add(ToolbarItem.action(DEMO));
        context.menus().create("dev.jasper.sample.menu", "Sample").add(DEMO);
        context.menus().standard(StandardMenu.VIEW).add(DEMO);
        context.menus().terminalContext().add(DEMO);

        StatusItem status = context.statusBar().add(new StatusItemSpec("dev.jasper.sample.status", Side.RIGHT, 100));
        status.setText("Sample: idle");
        status.setTooltip("Run the sample activity");
        status.setAction(DEMO);
        // Handlers run on the UI thread, which is where status items and actions may be changed.
        context.events().subscribe(Activities.TOPIC, event -> {
            if (!event.sourcePluginId().equals(context.plugin().id())) return;
            status.setText(event.terminal() ? "Sample: ready" : "Sample: " + Math.round(event.fraction().orElse(0) * 100) + "%");
            if (event.terminal()) demo[0].setEnabled(true);
        });
    }
    // example:pluginui:end

    private static void demo(PluginContext context, long stepMillis) {
        ActivityHandle activity = context.activities().begin(
            new ActivitySpec("Sample plugin", "Warming up", Optional.empty(), Optional.empty()));
        try {
            for (int step = 1; step <= STEPS; step++) {
                Thread.sleep(stepMillis);
                activity.progress(step / (double) STEPS, "Step " + step + " of " + STEPS);
            }
            activity.succeed("Ready");
        } catch (InterruptedException stopped) {
            activity.cancelled();
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-sample:check :jasper-app:test --tests '*BundledSamplePluginTest' verifyPluginArchitecture`
Expected: PASS. The existing `AppDocumentationTest` still passes here only if the `plugin` example in `docs/plugin-authoring.md` matches the new `start` method; Task 12 updates that guide, so run the documentation tests there, not here.

- [ ] **Step 6: Commit with Task 12**

The documentation example must change in the same commit as `SamplePlugin.start`, or `check` fails between them. Do not commit yet; continue to Task 12.

---

### Task 12: Documentation

**Files:**
- Modify: `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/configuration.md`, `docs/app-architecture.md`, `docs/STATUS.md`, `jasper-sdk/README.md`, this plan's status banner

- [ ] **Step 1: Update the authoring guide**

In `docs/plugin-authoring.md`:

- First paragraph: change "This guide covers what the SDK offers today (0.1): lifecycle, configuration, events, activities and services. Panels, toolbar and menu items, status items, windows and terminal access arrive in later SDK versions." to "This guide covers what the SDK offers today (0.2): lifecycle, configuration, events, activities, services, actions and their placements in the toolbar, menus and status bar. Panels, plugin windows and terminal access arrive in later SDK versions."
- In the descriptor example change the range to `sdk = ">=0.2, <0.3"`.
- Replace the `plugin` example block with the new text between `// example:plugin:start` and `// example:plugin:end` in `SamplePlugin.java`; copy it from the source.
- After the "Entry point" section add:

````markdown
## Actions and where they appear

Anything clickable is an action. Register it once and it is a command in every window's
palette, may have a shortcut, and can be placed in the toolbar, the menu bar, the terminal's
right-click menu and the status bar. Jasper draws all of it, so contributed chrome looks like
built-in chrome; a plugin supplies titles, icons and handlers, never components.

<!-- EXAMPLE-MARKER:pluginui -->
```java
private static void installUi(PluginContext context, long stepMillis) {
    PluginAction[] demo = new PluginAction[1];
    demo[0] = context.actions().register(ActionSpec.of(DEMO, "Run Sample Activity")
            .withIcon(context.appearance().icon("dev/jasper/sample/flask.svg"))
            .withKeywords(List.of("sample", "demo", "activity"))
            .withDefaultBinding("cmd+alt+j"),
        invoked -> {
            demo[0].setEnabled(false);
            context.background().execute(() -> demo(context, stepMillis));
        });
    context.toolbar().add(ToolbarItem.action(DEMO));
    context.menus().create("dev.jasper.sample.menu", "Sample").add(DEMO);
    context.menus().standard(StandardMenu.VIEW).add(DEMO);
    context.menus().terminalContext().add(DEMO);

    StatusItem status = context.statusBar().add(new StatusItemSpec("dev.jasper.sample.status", Side.RIGHT, 100));
    status.setText("Sample: idle");
    status.setTooltip("Run the sample activity");
    status.setAction(DEMO);
    // Handlers run on the UI thread, which is where status items and actions may be changed.
    context.events().subscribe(Activities.TOPIC, event -> {
        if (!event.sourcePluginId().equals(context.plugin().id())) return;
        status.setText(event.terminal() ? "Sample: ready" : "Sample: " + Math.round(event.fraction().orElse(0) * 100) + "%");
        if (event.terminal()) demo[0].setEnabled(true);
    });
}
```

- **Ids** of actions, top-level menus and status items start with your plugin id and a dot.
- **Shortcuts.** `withDefaultBinding` uses the `[keybindings]` syntax. The user's configuration
  wins, then Jasper's own shortcuts, then plugin defaults in load order; a default that loses is
  dropped and logged. Users rebind an action under `[keybindings]` with its quoted id.
- **Placements** may name only actions your plugin registered. Closing an action removes it from
  everywhere it was placed.
- **Menus** are mutable: `clear()` and `add(...)` rebuild a host list at any time.
- **Status items** are global: one handle updates the item in every window.
- **Icons.** `context.appearance().icon("path/in/your/jar.svg")` returns a 16 by 16 icon that
  follows the theme. Use monochrome artwork.
- **Threads.** Register and mutate on the event thread. Event handlers already run there, so
  updating a status item from a handler, as the sample does, needs no marshaling.
````

In the file you write, replace `EXAMPLE-MARKER` with `example` (it is spelled differently here because the documentation test scans this plan too, before the sample's new source exists). The block must equal the source text between `// example:pluginui:start` and `// example:pluginui:end` after `stripIndent().strip()`; if you changed `installUi` while implementing Task 11, copy from the source rather than from here.

- In "Testing", add after the existing snippet: "`host.actions()`, `host.toolbar()`, `host.menu(\"VIEW\")`, `host.status()` and `host.invoke(id, windowId, paneIdOrNull)` show and drive what the plugin contributed."

- [ ] **Step 2: Update the other documents**

`jasper-sdk/README.md`: add two table rows and change "0.x" wording only where a version number appears:

```markdown
| `dev.jasper.sdk.ui` | `Actions`, `ActionSpec`, `PluginAction`, `Toolbar`, `Menus`, `PluginMenu`, `StatusBar`, `StatusItem`, `Appearance` |
| `dev.jasper.sdk.terminal` | `WindowHandle`, `PaneHandle` (identity only until the terminal API) |
```

`docs/sdk-architecture.md`:
- In the table, change the `dev.jasper.app.plugins` row's "May depend on" to "SDK, `contributions`, `notifications`, `persistence`, `platform`", and add a row: "`dev.jasper.app.contributions` | App-native EDT model of contributed actions, toolbar entries, menu sections and status entries | `lifecycle`".
- Add a section before "Shutdown":

```markdown
## Chrome contributions

A plugin's `actions()`, `toolbar()`, `menus()` and `statusBar()` calls reach `HostedUi`, which
enforces namespaces, ownership of placed actions, the UI thread and containment, and writes the
single application-wide `Contributions` model. Each `WindowContent` connects to that model once:
`WindowContributions` turns every contributed action into a Swing `Action` registered in the
window's `CommandRegistry`, and `WindowChrome` and `WindowStatusBar` rebuild only their plugin
sections when the model changes. Workspace code never sees an SDK type.

`KeyBindings` is keyed by action id. A user's binding for a namespaced id is validated when the
configuration loads and bound when `withExtensions` learns the action exists. Precedence is the
user's configuration, built-in defaults, then plugin defaults in load order. The application
reports a user binding for an unknown action as a configuration warning and logs a dropped
plugin default.
```

- In "Not yet implemented", delete the plan 2 clause so it begins with "The rail, panels…".

`docs/configuration.md`, in the "Shortcuts" section, add:

````markdown
Plugin actions are bound by their quoted id, for example:

```toml
[keybindings]
"dev.jasper.sample.demo" = "cmd+alt+j"   # or "none"
```

A plugin may suggest a default shortcut; yours always wins, and Jasper's built-in shortcuts win
over a plugin's suggestion. A binding for an action that no installed plugin provides is
reported as a configuration warning.
````

and in the "Plugins" sample table add the line `demo_ui = true            # add the sample's action to the toolbar, menus and status bar`.

`docs/app-architecture.md`: add the package row after `config`:

```markdown
| `contributions` | EDT model of what extensions contribute to the chrome, in app-native types. Application owns the single instance; the plugin runtime writes it and every window renders it. |
```

`docs/STATUS.md`: change "plans 2–4 are not" to "plan 2 (actions and chrome placements) is implemented on `claude/plugin-sdk-plan-2`; plans 3–4 are not", and add a dated "Plugin SDK plan 2" section recording: the four scope decisions at the top of this plan; exact test counts from `*/build/test-results/test/*.xml`; that an action registered after startup is not re-evaluated for keybinding diagnostics until the next reload; and that native acceptance (below) is pending.

Set this plan's **Status** banner to "Implemented on `claude/plugin-sdk-plan-2`; native acceptance pending" and list any deviation.

- [ ] **Step 3: Run the documentation tests, the hygiene check and everything**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: PASS: both examples match `SamplePlugin.java`, links resolve, the new packages have `package-info.java`.

Run the AGENTS.md Python check. Expected: no output.

Run: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist`
Expected: `BUILD SUCCESSFUL` (rerun if the only failure is the known terminal flake).

- [ ] **Step 4: Commit Tasks 11 and 12**

```bash
git branch --show-current
git add -A
git commit -m "feat: place the sample plugin's action across the chrome and document the ui API

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Native acceptance (user-run; agents must not launch the GUI)

Add to `config.toml`:

```toml
[plugins."dev.jasper.sample"]
demo_ui = true
```

1. `./gradlew :jasper-app:run`. The toolbar shows a flask button labeled "Run Sample Activity" between Find and the right-aligned Settings; the menu bar ends with "Sample"; View ends with a separator and "Run Sample Activity"; the status bar shows "Sample: idle" left of the terminal size.
2. Click the button. Buddy shows the activity; the status item counts up to "Sample: ready"; the button is disabled while it runs. The same happens from the Sample menu, the terminal's right-click menu, the status item, the command palette ("sample") and Cmd+Option+J.
3. Toolbar modes Icons Only and Hidden treat the button like the built-in ones. At a narrow window width it compacts with them.
4. Switch Appearance: the flask icon recolors with the other toolbar icons.
5. Add `"dev.jasper.sample.demo" = "cmd+alt+u"` under `[keybindings]` and reload: the new shortcut works, the old one does not, and the menu item shows the new accelerator. Set it to `"none"`: no shortcut. Add `"dev.example.nothing" = "cmd+alt+n"`: the status bar reports a configuration warning naming it.
6. Open a second window: it has the same contributions, and running the activity from either updates the status item in both.
7. Remove `demo_ui`, restart: nothing of the sample is visible.

## Self-review record

- **Spec coverage.** §4.1 string-keyed `KeyBindings` and post-start validation → Tasks 1, 10. §4.3 plugin sections in toolbar, menus, context menu and status bar → Tasks 8, 9. §5 Actions (namespace, palette via `CommandRegistry`, `ActionContext`, precedence) → Tasks 1, 2, 4, 7, 10; Toolbar (`action`, `menu`, toolbar mode and compact fallback) → Tasks 2, 4, 8; Menus (standard sections, top-level after Tab, terminal context, mutable) → Tasks 2, 4, 8; Status bar (global items, text, icon, tooltip, action, visibility) → Tasks 2, 4, 9; Look and feel (`variant`, `onChanged`, themed SVG `icon`) → Tasks 2, 4, 6. §11 testkit and contract suite → Tasks 5, 6. §12 SDK confinement → unchanged guard, new app-native `contributions` package. §13 item 2 demo → Task 11. Not in this plan by design: the rail, panels, plugin windows and dialogs, `updateComponentTreeUI` on plugin roots (there are no plugin component roots until plan 3), handle queries and capability gating (plan 4).
- **Type consistency.** `Contributions.addAction(id, title, iconOrNull, keywords, defaultBinding, handler)`, `Contributions.Invocation(windowId, paneId)`, `KeyBindings.Extension(id, defaultBinding)`, `HostedUi`'s nine-argument constructor, `PluginHost.Environment`'s eight components, `PluginRuntime`'s four-argument constructor and `start(tables, dark)`, and the `ContractHarness` rendering format are used identically in every task that names them.
- **Build stays green per task** except between Tasks 11 and 12, which share one commit because the compiled documentation example changes with `SamplePlugin.start`.
