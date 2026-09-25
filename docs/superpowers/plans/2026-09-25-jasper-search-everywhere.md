# Search Everywhere Palette Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Plan written 2026-09-25, awaiting user review. Not started. It runs after `2026-09-25-jasper-theme-engine.md` and uses its `Theme`, `ThemeTestSupport.install(Theme)` and `SearchEverywhere.*` keys.

**Goal:** Rebuild the command palette as classic IntelliJ's Search Everywhere:
- a tab row (All, then one tab per scope);
- a full-width search field;
- one-line rows grouped under section headers;
- a hint bar.

Every colour comes from the theme. Cmd/Ctrl+K opens All.

**Architecture:** `PaletteController` gains an All tab. It queries every scope that takes part in All, with one row more than it shows, and builds a list of `PaletteEntry` values:
- `Header`, for a section heading;
- `Item`, for a row together with its scope;
- `More`, for a jump to a scope's tab.

`CommandPalette` is rewritten to paint that list, the tabs, the search field and the hint bar from `SearchEverywhere.*`, `List.*` and `Popup.borderColor`. The `>` picker, scope aliases and ⌘1–5 row shortcuts are removed. SDK 0.8.0 (unreleased) gains `ScopeSpec.withInAll` and loses `aliases`.

**Tech Stack:** Java 25 (JBR), Swing, FlatLaf 3.7 (`FlatClientProperties.STYLE`, `TEXT_FIELD_LEADING_ICON`), JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-25-jasper-search-everywhere-design.md`

**Planning evidence:** checked against FlatLaf 3.7 in the scratchpad before this plan was written:
- `FlatClientProperties.STYLE` accepts `background`, `borderColor` and `placeholderForeground` on a `JTextField`.
- `JTextField.leadingIcon` draws a leading icon.
- On Windows and Linux, `ctrl+1`–`ctrl+9` are bound to Select Tab, so those keys stay swallowed while the palette is open.

**Planning decisions** (rulings where the spec leaves room; the spec is binding):
1. **Scope aliases go from both the app and the SDK.** They existed only for the picker. `ScopeSpec.aliases` and `withAliases` are removed in the unreleased SDK 0.8.0, under the MVP rule. `description` stays and becomes the tab tooltip.
2. **How All knows a scope has more matches.** All asks each scope for `max_results + 1` rows. `PaletteContext` therefore accepts 1 to `PaletteResults.MAX_ROWS` (200), not the 1–20 settings range.
3. **The hint bar's left side** shows the row's detail, or else its tag, because a command carries its shortcut in its tag.
4. **A scope's own section label** ("Recent", "Most recent") becomes a header entry at the top of its tab, drawn like All's section headers. In All, the headers are scope labels.
5. **`jasper.all` is reserved.** It is `PaletteScope.ALL_ID`, and `ScopeRegistry` rejects it.
6. **Shortcuts in tooltips.** The All tab's tooltip names the palette shortcut. The Commands tab has none, since Cmd+K now opens All.
7. **The list scrolls after 15 lines.** The old "never scrolls" rule cannot hold for All's sections.
8. **Geometry:**
   - width 680;
   - tab row 30;
   - field row 40;
   - rows 24 and headers 22;
   - hint bar 26;
   - step rows 36 (logical px).
9. **Hint actions are clickable.** Clicking one runs that verb on the selected row.
10. **Tests switch from ⌘1 to Enter.** Existing key-sequence tests that ran a row with ⌘1 now use Enter; the tail semantics under test are the same.
11. **The palette's `Jasper.palette*` keys and their `ChromeKeys` rows are removed** (spec 2b).
12. **Shadow and card shape.** The overlay's shadow becomes square to match the square popup.

## Global Constraints

- **Build:** Java 25 on JBR 25, built with `./gradlew` only; `./gradlew check` passes at the end of every task.
- **SDK version:** stays `0.8.0`, and bundled plugins keep `sdk = ">=0.8.0, <0.9"`. `ScopeSpec.withInAll` is documented `@since 0.8.0`.
- **No hard-coded colours:** no palette colour is a literal or a `Jasper.palette*` key. Each surface reads its theme key, falling back only to a key FlatLaf always defines:

  | Surface | Theme key | Fallback |
  |---|---|---|
  | Header | `SearchEverywhere.Header.background` | `Panel.background` |
  | Selected tab | `SearchEverywhere.Tab.selectedBackground` / `selectedForeground` | `List.selectionInactiveBackground` / `Label.foreground` |
  | Search field | `SearchEverywhere.SearchField.background` / `borderColor` / `infoForeground` | `TextField.background` / `Component.borderColor` / `Label.disabledForeground` |
  | Section rule and label | `SearchEverywhere.List.separatorColor` / `separatorForeground` | `Separator.foreground` / `Label.disabledForeground` |
  | Rows | `List.background`, `List.foreground`, `List.selectionBackground`, `List.selectionForeground` and `List.hoverBackground` (no hover highlight when absent) | — |
  | Hint bar | `SearchEverywhere.Advertiser.background` / `foreground` | `Panel.background` / `Label.disabledForeground` |
  | Hint links | `Component.linkColor` | — |
  | Border | `Popup.borderColor` | `PopupMenu.borderColor` |

- **Keys:**
  - Cmd/Ctrl+K (`command_palette`) opens All.
  - A scope's shortcut opens its tab; the open tab's own shortcut closes the palette.
  - Tab / Shift+Tab cycle the tabs, or move between fields in a step.
  - Enter, Cmd/Ctrl+Enter and Shift+Enter run verbs 1–3; Enter on a More row opens its tab.
  - Up and Down skip headers; Esc leaves a step, then closes.
  - Switching tabs keeps the query.
- **Vault** calls `withInAll(false)`.
- **Agent limits:** never launch the GUI. The preview task is headless and allowed.
- **Source hygiene:** no raw control, private-use or surrogate characters in Java. `⏎⌘⇧` are ordinary BMP characters and already used.
- **Git:** work in `.worktrees/theme-engine` on `claude/theme-engine`; commits end with the attribution trailer below:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **Roster changes while All is open.** A scope registered or removed while All is open updates the tabs and sections, and its listeners follow. Removing the open tab's scope dismisses the palette. (Task 2: `scopesRegisteredOrRemovedWhileAllIsOpenUpdateItsTabsAndSections`, `scopeChangesRefreshTheOpenListAndRemovingTheActiveScopeDismisses`.)
2. **A failing scope is contained.** A scope that throws is skipped in All while the others show. (Task 2: `aScopeThatFailsIsSkippedInAllWhileTheOthersShow`.)
3. **Selection is preserved by key** (scope id plus row id) when rows move between sections, and falls back to the first row when the selected one vanishes. (Task 2: `allGroupsRowsUnderHeadersAndSelectionSkipsThem`, `refreshPreservesSelection…` in `WindowCommandPaletteTest`.)
4. **Key tails still drain.** The held Enter that ran a row and closed the palette does not leak into the terminal or into another window. (Task 2: the adapted `PaletteKeyRouterTest` and `CommandPaletteShortcutsTest` sequences.)
5. **Steps from All.** A step opened from an All row belongs to the row's scope, and completion rechecks that scope. (Task 2: `aRowFromAllRunsItsOwnScopesVerbs`, plus the step tests.)

## File Structure

- **SDK and plugins:**
  - Modify `jasper-sdk/src/main/java/dev/jasper/sdk/palette/ScopeSpec.java` (`inAll`, no aliases).
  - Modify `jasper-sdk-testkit/.../FakePluginHost.java` (`scopesInAll()`).
  - Modify the plugin scopes: History, Remote, Snippets, Vault and Sample.
- **New:** `jasper-app/src/main/java/dev/jasper/app/palette/PaletteEntry.java`.
- **Rewrite:** `palette/CommandPalette.java` and `palette/PaletteController.java`.
- **Modify:**
  - `palette/PaletteScope.java`, `ScopeRegistry.java`, `PaletteContext.java`, `CommandsScope.java` and `PaletteKeyRouter.java`;
  - `plugins/HostedPalette.java`;
  - `workspace/WindowCommandPalette.java` and `workspace/WindowContent.java`;
  - `appearance/ChromeKeys.java`.
- **Tests:**
  - Rewrite `palette/CommandPaletteTest`, `palette/PaletteTestSupport` and `workspace/PaletteScopesTest`.
  - Adapt `workspace/WindowCommandPaletteTest`, `PaletteKeyRouterTest`, `CommandPaletteShortcutsTest`, `WindowContributionsTest`, `CommandPalettePreview`, `palette/PaletteScopeModelTest`, `plugins/HostedPaletteTest`, `appearance/ChromeKeysTest`, and the SDK, testkit and plugin tests.
- **Docs:** `docs/command-palette.md`, `docs/configuration.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/design/command-palette/`, `docs/STATUS.md`.

---

### Task 1: Scopes opt in to All; aliases and the picker placeholders go

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/palette/ScopeSpec.java`
- Modify: `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`
- Modify: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePaletteTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteScope.java` (adds `inAll()` only)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedPalette.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedPaletteTest.java`
- Modify: `plugins/history/src/main/java/dev/jasper/history/HistoryScope.java`, `plugins/remote/src/main/java/dev/jasper/remote/ui/RemoteScope.java`, `plugins/snippets/src/main/java/dev/jasper/snippets/SnippetsScope.java`, `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultScope.java`, `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java`
- Modify: `plugins/remote/src/test/java/dev/jasper/remote/ui/RemoteScopeTest.java`, `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java`
- Modify: `docs/plugin-authoring.md` (the compiled `pluginpalette` example must match `SamplePlugin.java`)

**Interfaces:**
- Produces:
  - `ScopeSpec(String id, String label, String description, String placeholder, List<PaletteVerb> verbs, boolean monospaceRows, Optional<Icon> icon, Optional<String> shortcutActionId, boolean inAll)`;
  - `ScopeSpec.withInAll(boolean)`;
  - app `PaletteScope.inAll()`, default `true`;
  - `FakePluginHost.scopesInAll()`, returning ids in registration order.

- [ ] **Step 1: Write the failing tests**

In `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java`, replace the whole `aScopeSpecIsNamespacedHasOneToThreeDistinctVerbsAndLowercaseAliases` method with:

```java
    @Test void aScopeSpecIsNamespacedHasOneToThreeDistinctVerbsAndTakesPartInAllUnlessItOptsOut() {
        ScopeSpec spec = ScopeSpec.of("dev.x.things", "Things", "Search things", List.of(PASTE))
            .withDescription("All the things").withMonospaceRows(true).withShortcutActionId("dev.x.open");
        assertThat(spec.inAll()).isTrue();
        assertThat(spec.withInAll(false).inAll()).isFalse();
        assertThat(spec.withInAll(false).withDescription("x").inAll()).as("derivations keep the flag").isFalse();
        assertThat(spec.description()).isEqualTo("All the things");
        assertThat(spec.shortcutActionId()).contains("dev.x.open");
        assertThat(spec.icon()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("things", "Things", "Search", List.of(PASTE)));
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("dev.x.things", " ", "Search", List.of(PASTE)));
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("dev.x.things", "Things", "Search", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("dev.x.things", "Things", "Search",
            List.of(PASTE, new PaletteVerb("a", "A"), new PaletteVerb("b", "B"), new PaletteVerb("c", "C"))));
        assertThatIllegalArgumentException().as("verb ids are distinct").isThrownBy(() -> ScopeSpec.of("dev.x.things", "Things", "Search",
            List.of(PASTE, new PaletteVerb("paste", "Paste again"))));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteVerb("Paste", "Paste"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteVerb("paste", ""));
    }
```

In `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePaletteTest.java`, after the line `assertThat(host.scopes()).containsExactly("test.a.things|Things|paste,name");`, add:

```java
            assertThat(host.scopesInAll()).containsExactly("test.a.things");
```

In `jasper-app/src/test/java/dev/jasper/app/plugins/HostedPaletteTest.java`:
- In `Flaky.spec()`, replace `.withAliases(List.of("fl"))` with `.withInAll(false)`.
- Replace `assertThat(scope.aliases()).containsExactly("fl");` with `assertThat(scope.inAll()).isFalse();`.

In `plugins/remote/src/test/java/dev/jasper/remote/ui/RemoteScopeTest.java`, replace `assertThat(scope.spec().aliases()).containsExactly("ssh", "remote", "hosts");` with:

```java
        assertThat(scope.spec().inAll()).isTrue();
        assertThat(scope.spec().placeholder()).isEqualTo("Search saved hosts");
```

In `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java`, after `assertThat(host.scopes()).containsExactly("dev.jasper.vault.scope|Vault|copy_password,copy_username,open");`, add:

```java
            assertThat(host.scopesInAll()).as("secrets stay out of the All tab").isEmpty();
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests '*PaletteValuesTest' :jasper-sdk-testkit:test --tests '*FakePaletteTest'`
Expected: FAIL to compile; `cannot find symbol: method withInAll(boolean)` / `inAll()` / `scopesInAll()`.

- [ ] **Step 3: Rewrite ScopeSpec**

Replace `jasper-sdk/src/main/java/dev/jasper/sdk/palette/ScopeSpec.java` with:

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
 * What a scope is: identity, wording, verbs and how it is reached. The palette shows an All tab first,
 * then one tab per scope.
 *
 * @param id               namespaced like an action id and starting with the plugin's id, such as
 *                         {@code dev.jasper.history.scope}; the host rejects any other prefix
 * @param label            the tab text, such as "History"
 * @param description      one line, shown as the tab's tooltip
 * @param placeholder      the query field's hint while this scope's tab is open
 * @param verbs            one to three verbs with distinct ids, bound to Enter, Cmd/Ctrl+Enter, Shift+Enter
 * @param monospaceRows    whether rows are painted in the terminal font (commands, paths)
 * @param icon             the tab icon
 * @param shortcutActionId one of the plugin's own actions: while the palette is open, that action's
 *                         shortcut switches to or dismisses this scope's tab instead of running the handler
 * @param inAll            whether the All tab searches this scope too (since 0.8.0)
 */
public record ScopeSpec(String id, String label, String description, String placeholder, List<PaletteVerb> verbs,
                        boolean monospaceRows, Optional<Icon> icon, Optional<String> shortcutActionId, boolean inAll) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates every part and copies the verbs. */
    public ScopeSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches()) throw new IllegalArgumentException("Not a namespaced scope id: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A scope needs a label");
        description = description == null ? "" : description;
        if (placeholder == null || placeholder.isBlank()) throw new IllegalArgumentException("A scope needs a placeholder");
        verbs = List.copyOf(Objects.requireNonNull(verbs, "verbs"));
        if (verbs.isEmpty() || verbs.size() > 3) throw new IllegalArgumentException("A scope has one to three verbs");
        Set<String> ids = new HashSet<>();
        for (PaletteVerb verb : verbs) if (!ids.add(verb.id())) throw new IllegalArgumentException("Duplicate verb id: " + verb.id());
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(shortcutActionId, "shortcutActionId");
    }

    /**
     * The minimum: no description, icon or shortcut, proportional rows, searched by the All tab.
     *
     * @param id          the scope id
     * @param label       the tab text
     * @param placeholder the query hint
     * @param verbs       the verbs
     * @return the spec
     */
    public static ScopeSpec of(String id, String label, String placeholder, List<PaletteVerb> verbs) {
        return new ScopeSpec(id, label, "", placeholder, verbs, false, Optional.empty(), Optional.empty(), true);
    }

    /**
     * Derives a value.
     *
     * @param value the tooltip line
     * @return a copy with that description
     */
    public ScopeSpec withDescription(String value) { return new ScopeSpec(id, label, value, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll); }

    /**
     * Derives a value.
     *
     * @param value whether rows use the terminal font
     * @return a copy with that setting
     */
    public ScopeSpec withMonospaceRows(boolean value) { return new ScopeSpec(id, label, description, placeholder, verbs, value, icon, shortcutActionId, inAll); }

    /**
     * Derives a value.
     *
     * @param value the tab icon, or null for none
     * @return a copy with that icon
     */
    public ScopeSpec withIcon(Icon value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, Optional.ofNullable(value), shortcutActionId, inAll); }

    /**
     * Derives a value.
     *
     * @param value the plugin's action whose shortcut reaches this scope, or null for none
     * @return a copy naming it
     */
    public ScopeSpec withShortcutActionId(String value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, Optional.ofNullable(value), inAll); }

    /**
     * Derives a value. A scope whose rows should not appear beside others, such as secrets, opts out.
     *
     * @param value whether the All tab searches this scope
     * @return a copy with that setting
     * @since 0.8.0
     */
    public ScopeSpec withInAll(boolean value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, value); }
}
```

- [ ] **Step 4: Carry the flag through the app and the testkit**

In `jasper-app/src/main/java/dev/jasper/app/palette/PaletteScope.java`, after `default boolean monospaceRows() { return false; }`, add:

```java
    /** Whether the palette's All tab searches this scope too; true unless the scope opts out. */
    default boolean inAll() { return true; }
```

In `jasper-app/src/main/java/dev/jasper/app/plugins/HostedPalette.java`, in `Adapted`, replace

```java
        @Override public List<String> aliases() { return spec.aliases(); }
```

with

```java
        @Override public boolean inAll() { return spec.inAll(); }
```

In `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`, after the `scopes()` method, add:

```java
        /** The ids of registered scopes the palette's All tab searches, in registration order. */
        public List<String> scopesInAll() {
            return scopes.values().stream().filter(registered -> registered.spec().inAll()).map(registered -> registered.spec().id()).toList();
        }
```

- [ ] **Step 5: Update the plugins, the sample and its documented copy**

Edit each plugin scope's `spec()`:
- In `plugins/history/src/main/java/dev/jasper/history/HistoryScope.java`, replace the placeholder `"Search shell history, or > to switch scope"` with `"Search shell history"`, and delete the `.withAliases(List.of("hist", "shell"))` call from the chain.
- In `plugins/remote/src/main/java/dev/jasper/remote/ui/RemoteScope.java`, replace the placeholder `"Search saved hosts, or > to switch scope"` with `"Search saved hosts"`, and delete `.withAliases(List.of("ssh", "remote", "hosts"))`.
- In `plugins/snippets/src/main/java/dev/jasper/snippets/SnippetsScope.java`, replace the placeholder `"Search snippets, or > to switch scope"` with `"Search snippets"`.
- In `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultScope.java`, replace the placeholder `"Search accounts and keys, or > to switch scope"` with `"Search accounts and keys"`, and append `.withInAll(false)` to the returned spec.

In both `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java` and the `<!-- example:pluginpalette -->` block of `docs/plugin-authoring.md`, replace

```java
            return ScopeSpec.of(GREETINGS, "Greetings", "Search greetings, or > to switch scope",
                    List.of(new PaletteVerb("paste", "Paste"), new PaletteVerb("paste_run", "Paste and run")))
                .withAliases(List.of("greet")).withShortcutActionId(GREETINGS_OPEN);
```

with

```java
            return ScopeSpec.of(GREETINGS, "Greetings", "Search greetings",
                    List.of(new PaletteVerb("paste", "Paste"), new PaletteVerb("paste_run", "Paste and run")))
                .withShortcutActionId(GREETINGS_OPEN);
```

The indentation differs by one level between the two files. Keep each file's own indentation; the documentation test compares the blocks after `stripIndent`.

Also in `docs/plugin-authoring.md`, under `## Palette scopes`, replace

```markdown
`palette.contribute`, register a `PaletteScope` through `context.palette()`, and the scope appears in
every window's scope picker under its label and `>alias`. The spec is read once; its id must start
```

with

```markdown
`palette.contribute`, register a `PaletteScope` through `context.palette()`, and the scope gets a tab
in every window's palette and a section in its All tab (`withInAll(false)` keeps a scope, such as
Vault's secrets, out of All). The spec is read once; its id must start
```

and replace `listener. The palette shows at most \`maxResults\` rows and never scrolls. A verb may return a` with `listener. Return at most \`maxResults\` rows. A verb may return a`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*HostedPaletteTest' --tests '*AppDocumentationTest' :jasper-plugin-remote:test :jasper-plugin-vault:test :jasper-plugin-history:test :jasper-plugin-snippets:test :jasper-plugin-sample:test`
Expected: PASS.

Run: `git grep -n 'withAliases\|to switch scope' -- '*.java' 'docs/plugin-authoring.md'`
Expected: only `jasper-app/src/main/java/dev/jasper/app/palette/CommandsScope.java` and `PaletteController.java`, which Task 2 removes.

- [ ] **Step 7: Run check and commit**

Run: `./gradlew check > /tmp/se-task1.log 2>&1; tail -5 /tmp/se-task1.log`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A jasper-sdk jasper-sdk-testkit jasper-app/src plugins docs/plugin-authoring.md
git commit -m "feat(sdk): let palette scopes opt out of the All tab and drop scope aliases

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: The Search Everywhere palette

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteEntry.java`
- Rewrite: `jasper-app/src/main/java/dev/jasper/app/palette/CommandPalette.java`, `PaletteController.java`
- Modify:
  - `jasper-app/src/main/java/dev/jasper/app/palette/PaletteScope.java`, `ScopeRegistry.java`, `PaletteContext.java`, `CommandsScope.java` and `PaletteKeyRouter.java`;
  - `jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java` and `WindowContent.java`.
- Rewrite (tests): `jasper-app/src/test/java/dev/jasper/app/palette/PaletteTestSupport.java`, `CommandPaletteTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/PaletteScopesTest.java`
- Modify (tests):
  - `jasper-app/src/test/java/dev/jasper/app/workspace/WindowCommandPaletteTest.java`, `WindowContributionsTest.java`, `PaletteKeyRouterTest.java`, `CommandPaletteShortcutsTest.java` and `CommandPalettePreview.java`;
  - `jasper-app/src/test/java/dev/jasper/app/palette/PaletteScopeModelTest.java`.

**Interfaces:**
- Consumes:
  - `PaletteScope.inAll()` (Task 1);
  - `Theme`, `ThemeTestSupport.install(Theme)` and the `SearchEverywhere.*` keys (theme-engine plan);
  - `AppIcons.icon("search")`.
- Produces:
  - `sealed interface PaletteEntry` with `Header(String label)`, `Item(PaletteScope scope, PaletteRow row)`, `More(PaletteScope scope)`, `key()`, `selectable()` and `static String key(PaletteScope, String rowId)`;
  - `PaletteScope.ALL_ID = "jasper.all"`;
  - `CommandPalette(boolean macOs, Consumer<String> queryChanged, ObjIntConsumer<PaletteEntry> execute, Consumer<String> tabSelected)`;
  - `CommandPalette.Tab(String id, String label, Icon icon, String tooltip)`;
  - on `PaletteController`: `selectTab(String)` and `cycleTab(int)`. `tabPressed(boolean)` now cycles tabs outside steps, and `activeScopeId()` returns `ALL_ID` for All. `openPicker`, `pickerOpen` and `executeNumber` are removed, from `WindowCommandPalette` too.

- [ ] **Step 1: Write the test support and the failing component tests**

Replace `jasper-app/src/test/java/dev/jasper/app/palette/PaletteTestSupport.java` with:

```java
package dev.jasper.app.palette;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.lifecycle.Subscription;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Test-only component input and observation; callers run on the EDT. */
public final class PaletteTestSupport {
    private PaletteTestSupport() {}

    public static JList<PaletteEntry> entryList(CommandPalette palette) { return palette.entryList(); }

    /** Every entry as text: {@code # Label} for a header, {@code scopeId/rowId} for a row, {@code more:scopeId}. */
    public static List<String> entries(CommandPalette palette) {
        var texts = new ArrayList<String>();
        var model = palette.entryList().getModel();
        for (int i = 0; i < model.getSize(); i++) texts.add(describe(model.getElementAt(i)));
        return texts;
    }

    public static String describe(PaletteEntry entry) {
        return switch (entry) {
            case PaletteEntry.Header header -> "# " + header.label();
            case PaletteEntry.Item item -> item.scope().id() + "/" + item.row().id();
            case PaletteEntry.More more -> "more:" + more.scope().id();
        };
    }

    /** The rows shown, in order, without headers or "More in" entries. */
    public static List<PaletteRow> rows(CommandPalette palette) {
        var rows = new ArrayList<PaletteRow>();
        var model = palette.entryList().getModel();
        for (int i = 0; i < model.getSize(); i++) if (model.getElementAt(i) instanceof PaletteEntry.Item item) rows.add(item.row());
        return rows;
    }

    public static int rowCount(CommandPalette palette) { return rows(palette).size(); }
    public static PaletteRow rowAt(CommandPalette palette, int index) { return rows(palette).get(index); }

    /** The selected row, or null when nothing or a "More in" entry is selected. */
    public static PaletteRow selectedRow(CommandPalette palette) {
        return palette.selectedEntry() instanceof PaletteEntry.Item item ? item.row() : null;
    }

    /** The selected row's position among the rows, or -1. */
    public static int selectedRowIndex(CommandPalette palette) {
        var model = palette.entryList().getModel();
        int selected = palette.entryList().getSelectedIndex();
        for (int i = 0, row = 0; i < model.getSize(); i++) {
            if (!(model.getElementAt(i) instanceof PaletteEntry.Item)) continue;
            if (i == selected) return row;
            row++;
        }
        return -1;
    }

    /** The list-cell bounds of the {@code index}th row. */
    public static Rectangle rowBounds(CommandPalette palette, int index) {
        var model = palette.entryList().getModel();
        for (int i = 0, row = 0; i < model.getSize(); i++)
            if (model.getElementAt(i) instanceof PaletteEntry.Item && row++ == index) return palette.entryList().getCellBounds(i, i);
        throw new IndexOutOfBoundsException(index);
    }

    public static List<String> tabIds(CommandPalette palette) { return palette.tabIds(); }
    public static String selectedTabId(CommandPalette palette) { return palette.selectedTabId(); }
    public static void clickTab(CommandPalette palette, String id) { palette.clickTab(id); }
    public static String placeholder(CommandPalette palette) { return (String) palette.queryField().getClientProperty("JTextField.placeholderText"); }
    public static JPanel hintBar(CommandPalette palette) { return palette.hintBar(); }
    public static String hint(CommandPalette palette) { return palette.hintText(); }
    public static List<String> hintActions(CommandPalette palette) { return palette.hintActionTexts(); }
    public static void clickHintAction(CommandPalette palette, int index) { palette.clickHintAction(index); }
    public static int itemHeight(CommandPalette palette) { return palette.itemHeight(); }
    public static JLabel stepTitle(CommandPalette palette) { return palette.stepTitle(); }
    public static List<JTextField> stepFields(CommandPalette palette) { return palette.stepFields(); }
    public static int stepFocusIndex(CommandPalette palette) { return palette.stepFocusIndex(); }
    public static JLabel stepError(CommandPalette palette) { return palette.stepError(); }
    public static void selectRow(CommandPalette palette, String id) { palette.selectRow(id); }
    public static void selectRelative(CommandPalette palette, int delta) { palette.selectRelative(delta); }
    public static void executeSelected(CommandPalette palette, int verb) { palette.executeSelected(verb); }
    public static void executeSelected(CommandPalette palette) { palette.executeSelected(); }

    /** Shows {@code rows} as {@code scope}'s own tab would, selecting {@code selectionId}. */
    public static void setRows(CommandPalette palette, PaletteScope scope, List<PaletteRow> rows, String selectionId) {
        palette.setEntries(rows.stream().<PaletteEntry>map(row -> new PaletteEntry.Item(scope, row)).toList(),
            selectionId == null ? null : PaletteEntry.key(scope, selectionId), "No matching " + scope.label());
    }

    public static String scopeFor(ActionId action) { return PaletteKeyRouter.scopeFor(action); }

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

    /** Rows the Commands scope builds for {@code commands}, for tests that seed the list directly. */
    public static List<PaletteRow> rows(CommandsScope scope, List<Command> commands) { return scope.rows(commands); }
}
```

Replace `jasper-app/src/test/java/dev/jasper/app/palette/CommandPaletteTest.java` with:

```java
package dev.jasper.app.palette;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.lifecycle.Subscription;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputMethodEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.DefaultEditorKit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommandPaletteTest {
    private static final PaletteScope COMMANDS = scope("jasper.commands", "Commands", false, List.of(new PaletteVerb("run", "Run")));
    private static final PaletteScope HISTORY = scope("test.history", "History", true, List.of(new PaletteVerb("paste", "Paste"),
        new PaletteVerb("paste_run", "Paste and run"), new PaletteVerb("save", "Save as snippet…")));

    static PaletteScope scope(String id, String label, boolean monospace, List<PaletteVerb> verbs) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public String placeholder() { return "Search " + label; }
            @Override public List<PaletteVerb> verbs() { return verbs; }
            @Override public boolean monospaceRows() { return monospace; }
            @Override public PaletteResults search(String query, PaletteContext context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    private static CommandPalette palette(List<String> events) {
        return new CommandPalette(true, query -> { },
            (entry, verb) -> events.add(PaletteTestSupport.describe(entry) + ":" + verb), id -> events.add("tab:" + id));
    }

    private static PaletteEntry item(PaletteScope scope, String id) { return new PaletteEntry.Item(scope, PaletteRow.of(id, "Row " + id)); }

    @Test void queryNotifiesWhenClearedAndKeepsNativeEditingActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var changes = new ArrayList<String>();
            var palette = new CommandPalette(false, changes::add, (entry, verb) -> { }, id -> { });
            palette.queryField().setText("split 2");
            palette.queryField().setText("");
            assertThat(changes).containsExactly("split 2", "");
            assertThat(palette.queryField().getActionMap().get(DefaultEditorKit.copyAction)).isNotNull();
            assertThat(palette.queryField().getActionMap().get(DefaultEditorKit.pasteAction)).isNotNull();
            assertThat(hasInputBinding(palette.queryField(), DefaultEditorKit.copyAction)).isTrue();
            assertThat(hasInputBinding(palette.queryField(), DefaultEditorKit.pasteAction)).isTrue();
            assertThat(palette.tabIds()).containsExactly(PaletteScope.ALL_ID);
            assertThat(palette.queryField().getAccessibleContext().getAccessibleName()).isEqualTo("Search all");
            assertThat(palette.entryList().getAccessibleContext().getAccessibleName()).isEqualTo("All");
            assertThat(palette.queryField().getClientProperty("JTextField.placeholderText")).isEqualTo("Search everywhere");
        });
    }

    @Test void tabsShowInOrderMarkTheSelectedOneAndReportClicks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var events = new ArrayList<String>();
            var palette = palette(events);
            palette.setTabs(List.of(new CommandPalette.Tab(PaletteScope.ALL_ID, "All", null, "Search everywhere (⌘K)"),
                new CommandPalette.Tab("jasper.commands", "Commands", null, null),
                new CommandPalette.Tab("test.history", "History", null, "Search shell history")), "test.history");
            assertThat(palette.tabIds()).containsExactly(PaletteScope.ALL_ID, "jasper.commands", "test.history");
            assertThat(palette.selectedTabId()).isEqualTo("test.history");
            assertThat(palette.queryField().getAccessibleContext().getAccessibleName()).isEqualTo("Search history");
            assertThat(palette.entryList().getAccessibleContext().getAccessibleName()).isEqualTo("History");
            var labels = labels(palette.tabStrip());
            assertThat(labels).extracting(JLabel::getText).containsExactly("All", "Commands", "History");
            assertThat(labels.getFirst().getToolTipText()).isEqualTo("Search everywhere (⌘K)");
            labels.get(1).dispatchEvent(mousePress(labels.get(1), 3, 3));
            assertThat(events).containsExactly("tab:jasper.commands");
            palette.clickTab("test.history");
            palette.clickTab("missing");
            assertThat(events).containsExactly("tab:jasper.commands", "tab:test.history");
            palette.setPlaceholder("Search shell history");
            assertThat(palette.queryField().getClientProperty("JTextField.placeholderText")).isEqualTo("Search shell history");
        });
    }

    @Test void allGroupsRowsUnderHeadersAndSelectionSkipsThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var entries = List.<PaletteEntry>of(new PaletteEntry.Header("Commands"), item(COMMANDS, "new_tab"),
                new PaletteEntry.Header("History"), item(HISTORY, "ls"), new PaletteEntry.More(HISTORY));
            palette.setEntries(entries, null, "Nothing found");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).as("the first row, never a header").isEqualTo("jasper.commands/new_tab");
            palette.selectRelative(1);
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("test.history/ls");
            palette.selectRelative(1);
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("more:test.history");
            palette.selectRelative(50);
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("more:test.history");
            palette.selectRelative(-50);
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("jasper.commands/new_tab");
            assertThat(palette.entryList().getAccessibleContext().getAccessibleDescription()).isEqualTo("2 results");
            palette.setEntries(entries, PaletteEntry.key(HISTORY, "ls"), "Nothing found");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).as("kept by scope and row").isEqualTo("test.history/ls");
            palette.setEntries(entries.subList(0, 2), PaletteEntry.key(HISTORY, "ls"), "Nothing found");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).as("falls back to the first row").isEqualTo("jasper.commands/new_tab");
            palette.setEntries(List.of(), null, "Nothing found");
            palette.selectRelative(1);
            assertThat(palette.selectedEntry()).isNull();
            var tooMany = new ArrayList<PaletteEntry>();
            for (int i = 0; i < PaletteResults.MAX_ROWS + 1; i++) tooMany.add(item(COMMANDS, "many." + i));
            assertThatIllegalArgumentException().isThrownBy(() -> palette.setEntries(tooMany, null, null));
        });
    }

    @Test void selectRowFindsARowByIdAndTheQueryAcceptsDigits() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            palette.setEntries(List.of(item(COMMANDS, "select_tab_1"), item(COMMANDS, "select_tab_2")), null, null);
            palette.queryField().setText("tab 2");
            palette.selectRow("select_tab_2");
            assertThat(palette.queryField().getText()).isEqualTo("tab 2");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("jasper.commands/select_tab_2");
            palette.selectRow("missing");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("jasper.commands/select_tab_2");
        });
    }

    @Test void preservedSelectionStaysVisibleAcrossReorderAndGrowth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            List<PaletteEntry> rows = new ArrayList<>();
            for (String id : List.of("first", "second", "third", "fourth", "selected")) rows.add(item(COMMANDS, id));
            palette.setEntries(rows, null, null);
            palette.setSize(UIScale.scale(318), UIScale.scale(150));
            layoutTree(palette);
            palette.selectRelative(4);
            var list = palette.entryList();
            assertThat(list.getVisibleRect().contains(list.getCellBounds(4, 4))).isTrue();
            var reordered = new ArrayList<>(rows); reordered.addFirst(reordered.removeLast());
            palette.setEntries(reordered, PaletteEntry.key(COMMANDS, "selected"), null);
            layoutTree(palette);
            assertThat(list.getSelectedIndex()).isZero();
            assertThat(list.getVisibleRect().contains(list.getCellBounds(0, 0))).isTrue();
            palette.setEntries(rows, PaletteEntry.key(COMMANDS, "selected"), null);
            layoutTree(palette);
            assertThat(list.getSelectedIndex()).isEqualTo(4);
            assertThat(list.getVisibleRect().contains(list.getCellBounds(4, 4))).isTrue();
        });
    }

    @Test void rowsExecuteOnClickWhileHeadersAndBlankSpaceDoNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var events = new ArrayList<String>();
            var palette = palette(events);
            palette.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "first"), item(COMMANDS, "second")), null, null);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);
            var list = palette.entryList();
            Rectangle header = list.getCellBounds(0, 0), second = list.getCellBounds(2, 2);
            assertThat(header.height).isEqualTo(UIScale.scale(22));
            assertThat(second.height).isEqualTo(UIScale.scale(24));
            // BasicListUI asks the native toolkit for the platform menu mask on mouse press.
            // Remove only that delegate listener so this component-level hit test stays headless.
            Arrays.stream(list.getMouseListeners())
                .filter(listener -> listener.getClass().getName().startsWith("javax.swing.plaf."))
                .forEach(list::removeMouseListener);
            list.dispatchEvent(mousePress(list, header.x + 4, header.y + 4));
            list.dispatchEvent(mousePress(list, second.x + 4, second.y + 4));
            list.setSize(list.getWidth(), UIScale.scale(200));
            list.dispatchEvent(mousePress(list, 4, UIScale.scale(190)));
            assertThat(events).containsExactly("jasper.commands/second:0");
        });
    }

    @Test void inputMethodEventsExposeOnlyActiveComposition() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            palette.queryField().dispatchEvent(new InputMethodEvent(palette.queryField(),
                InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, new AttributedString("ab").getIterator(), 1, null, null));
            assertThat(palette.composing()).isTrue();
            palette.queryField().dispatchEvent(new InputMethodEvent(palette.queryField(),
                InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, new AttributedString("ab").getIterator(), 2, null, null));
            assertThat(palette.composing()).isFalse();
        });
    }

    @Test void geometryFollowsSearchEverywhere() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            palette.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "a"), item(COMMANDS, "b")), null, null);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);
            Insets insets = palette.getInsets();
            assertThat(palette.getPreferredSize().width).isEqualTo(UIScale.scale(680));
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(30 + 40 + 22 + 2 * 24 + 26) + insets.top + insets.bottom);
            assertThat(palette.tabStrip().getHeight()).isEqualTo(UIScale.scale(30));
            assertThat(palette.queryField().getParent().getHeight()).isEqualTo(UIScale.scale(40));
            assertThat(palette.hintBar().getHeight()).isEqualTo(UIScale.scale(26));
            assertThat(palette.itemHeight()).isEqualTo(UIScale.scale(24));
            assertThat(palette.isOpaque()).isTrue();
            palette.setEntries(List.of(), null, "Nothing found");
            assertThat(palette.getPreferredSize().height).as("the empty line").isEqualTo(UIScale.scale(30 + 40 + 24 + 26) + insets.top + insets.bottom);
        });
    }

    @Test void theListScrollsPastFifteenLines() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var rows = new ArrayList<PaletteEntry>();
            for (int i = 0; i < 30; i++) rows.add(item(HISTORY, "r" + i));
            palette.setEntries(rows, null, null);
            Insets insets = palette.getInsets();
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(30 + 40 + 15 * 24 + 26) + insets.top + insets.bottom);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);
            palette.selectRelative(29);
            var list = palette.entryList();
            assertThat(list.getSelectedIndex()).isEqualTo(29);
            assertThat(list.getVisibleRect().intersects(list.getCellBounds(29, 29))).isTrue();
        });
    }

    @Test void everySurfaceTakesItsColourFromTheTheme() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var original = UIManager.getLookAndFeel();
            try {
                for (Theme theme : List.of(Theme.LIGHT, Theme.DARK)) {
                    ThemeTestSupport.install(theme);
                    var palette = palette(new ArrayList<>());
                    palette.setTabs(List.of(new CommandPalette.Tab(PaletteScope.ALL_ID, "All", null, null),
                        new CommandPalette.Tab("jasper.commands", "Commands", null, null)), PaletteScope.ALL_ID);
                    palette.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "a"), item(COMMANDS, "b"),
                        new PaletteEntry.Header("History"), item(HISTORY, "c")), null, null);
                    palette.setSize(palette.getPreferredSize());
                    layoutTree(palette);
                    var image = paint(palette);
                    var list = palette.entryList();
                    var allTab = labels(palette.tabStrip()).getFirst();
                    String name = theme.name();
                    assertPixel(image, new Point(0, 0), "Popup.borderColor", name);
                    assertPixel(image, at(allTab, 3, 3, palette), "SearchEverywhere.Tab.selectedBackground", name);
                    assertPixel(image, at(palette.tabStrip(), palette.tabStrip().getWidth() - 3, 3, palette), "SearchEverywhere.Header.background", name);
                    assertPixel(image, at(palette.queryField().getParent(), palette.queryField().getParent().getWidth() - 3, 2, palette),
                        "SearchEverywhere.SearchField.background", name);
                    Rectangle selected = list.getCellBounds(1, 1), plain = list.getCellBounds(2, 2), rule = list.getCellBounds(3, 3);
                    assertPixel(image, at(list, selected.width - 3, selected.y + 3, palette), "List.selectionBackground", name);
                    assertPixel(image, at(list, plain.width - 3, plain.y + 3, palette), "List.background", name);
                    assertPixel(image, at(list, rule.width - 3, rule.y, palette), "SearchEverywhere.List.separatorColor", name);
                    assertPixel(image, at(palette.hintBar(), palette.hintBar().getWidth() - 3, palette.hintBar().getHeight() / 2, palette),
                        "SearchEverywhere.Advertiser.background", name);
                    assertThat(labels(palette.tabStrip()).get(1).getForeground()).as(name).isEqualTo(UIManager.getColor("List.foreground"));
                    assertThat(allTab.getForeground()).as(name).isEqualTo(UIManager.getColor("SearchEverywhere.Tab.selectedForeground"));
                }
            } finally {
                try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new IllegalStateException(failure); }
            }
        });
    }

    @Test void selectedAndHoveredRowsPaintTheListsColours() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var original = UIManager.getLookAndFeel();
            try {
                ThemeTestSupport.install(Theme.LIGHT);
                var palette = palette(new ArrayList<>());
                palette.setEntries(List.of(item(COMMANDS, "a"), item(COMMANDS, "b")), null, null);
                palette.setSize(palette.getPreferredSize());
                layoutTree(palette);
                var list = palette.entryList();
                Rectangle first = list.getCellBounds(0, 0), second = list.getCellBounds(1, 1);
                list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_MOVED, 1L, 0, 4, second.y + 4, 0, false));
                var image = paint(palette);
                assertPixel(image, at(list, first.width - 3, first.y + 3, palette), "List.selectionBackground", "selected");
                assertPixel(image, at(list, second.width - 3, second.y + 3, palette), "List.hoverBackground", "hovered");
                list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_EXITED, 1L, 0, 4, second.y + 4, 0, false));
                assertPixel(paint(palette), at(list, second.width - 3, second.y + 3, palette), "List.background", "after the mouse left");
            } finally {
                try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new IllegalStateException(failure); }
            }
        });
    }

    @Test void rowsLayOutOnOneLineAndTheTagGoesFirstWhenSpaceRunsOut() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var row = new PaletteRow("r", "Rendered command", "a detail", "⌘T", null, true, null);
            palette.setEntries(List.of(new PaletteEntry.Item(COMMANDS, row), new PaletteEntry.More(HISTORY), new PaletteEntry.Header("History")), null, null);
            var list = palette.entryList();
            var renderer = list.getCellRenderer();
            var line = (Container) renderer.getListCellRendererComponent(list, list.getModel().getElementAt(0), 0, false, false);
            line.setSize(UIScale.scale(680), UIScale.scale(24));
            line.doLayout();
            JLabel title = label(line, "Rendered command"), detail = label(line, "a detail"), tag = label(line, "⌘T");
            assertThat(detail.getX()).as("the detail follows the title").isGreaterThanOrEqualTo(title.getX() + title.getWidth());
            assertThat(detail.getY()).isEqualTo(title.getY());
            assertThat(tag.getX() + tag.getWidth()).as("the tag is right-aligned").isEqualTo(UIScale.scale(680) - UIScale.scale(8));
            line.setSize(title.getPreferredSize().width + UIScale.scale(40), UIScale.scale(24));
            line.doLayout();
            assertThat(tag.getWidth()).as("the tag goes first").isZero();
            assertThat(title.getWidth()).isPositive();
            var more = (Container) renderer.getListCellRendererComponent(list, list.getModel().getElementAt(1), 1, false, false);
            assertThat(labels(more)).extracting(JLabel::getText).contains("More in History…");
            var header = renderer.getListCellRendererComponent(list, list.getModel().getElementAt(2), 2, false, false);
            assertThat(header.getPreferredSize().height).isEqualTo(UIScale.scale(22));
        });
    }

    @Test void theHintBarShowsTheDetailOrTagAndTheOtherVerbsWithTheirKeys() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var events = new ArrayList<String>();
            var palette = palette(events);
            palette.setEntries(List.of(new PaletteEntry.Item(HISTORY, new PaletteRow("ls", "ls -la", "in ~/src", null, null, true, null)),
                new PaletteEntry.Item(COMMANDS, new PaletteRow("new_tab", "New Tab", null, "⌘T", null, true, null)),
                new PaletteEntry.More(HISTORY)), null, null);
            assertThat(palette.hintText()).isEqualTo("in ~/src");
            assertThat(palette.hintActionTexts()).containsExactly("Paste and run ⌘⏎", "Save as snippet… ⇧⏎");
            palette.clickHintAction(1);
            assertThat(events).containsExactly("test.history/ls:2");
            palette.selectRelative(1);
            assertThat(palette.hintText()).as("a command shows its shortcut").isEqualTo("⌘T");
            assertThat(palette.hintActionTexts()).isEmpty();
            palette.selectRelative(1);
            assertThat(palette.hintText()).isEqualTo("Show every match in History");
            var elsewhere = new CommandPalette(false, query -> { }, (entry, verb) -> { }, id -> { });
            elsewhere.setEntries(List.of(item(HISTORY, "ls")), null, null);
            assertThat(elsewhere.hintActionTexts()).containsExactly("Paste and run Ctrl+Enter", "Save as snippet… Shift+Enter");
        });
    }

    @Test void aStepReplacesTheListAndHidesTheHintBar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            new ThemeController(Appearance.LIGHT);
            try {
                var palette = palette(new ArrayList<>());
                palette.setEntries(List.of(item(HISTORY, "a"), item(HISTORY, "b")), null, null);
                palette.showStep("Rebase", List.of(new PaletteStep.Field("branch", "branch", "main"),
                    new PaletteStep.Field("remote", "remote", "")));
                assertThat(palette.stepShowing()).isTrue();
                assertThat(palette.stepFields()).hasSize(2);
                assertThat(palette.stepFields().getFirst().getText()).isEqualTo("main");
                assertThat(palette.stepFields().getFirst().getAccessibleContext().getAccessibleName()).isEqualTo("branch");
                assertThat(palette.stepFocusIndex()).isZero();
                assertThat(palette.stepTitle().getText()).isEqualTo("Rebase");
                assertThat(palette.hintBar().isVisible()).isFalse();
                Insets insets = palette.getInsets();
                assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(30 + 40 + 22 + 2 * 36) + insets.top + insets.bottom);
                palette.focusStepField(1);
                assertThat(palette.stepFocusIndex()).isEqualTo(1);
                palette.focusStepField(1);
                assertThat(palette.stepFocusIndex()).isZero();
                palette.focusStepField(-1);
                assertThat(palette.stepFocusIndex()).isEqualTo(1);
                palette.stepFields().get(1).setText("origin");
                assertThat(palette.stepValues()).hasSize(2).containsEntry("branch", "main").containsEntry("remote", "origin");
                palette.setStepError("Nope");
                assertThat(palette.stepError().isVisible()).isTrue();
                assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(30 + 40 + 22 + 2 * 36 + 22) + insets.top + insets.bottom);
                palette.setStepError(null);
                assertThat(palette.stepError().isVisible()).isFalse();
                palette.hideStep();
                assertThat(palette.stepShowing()).isFalse();
                assertThat(palette.stepFields()).isEmpty();
                assertThat(palette.hintBar().isVisible()).isTrue();
            } finally { new ThemeController(); }
        });
    }

    @Test void liveTypographyKeepsRowAndHeaderHeights() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            var palette = palette(new ArrayList<>());
            palette.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "a")), null, null);
            try {
                for (float size : new float[]{18, 32, 12}) {
                    themes.configure(Appearance.DARK, new UiFontConfig("system", size));
                    SwingUtilities.updateComponentTreeUI(palette);
                    palette.refreshTheme();
                    palette.setSize(palette.getPreferredSize());
                    layoutTree(palette);
                    assertThat(palette.entryList().getCellBounds(0, 0).height).isEqualTo(UIScale.scale(22));
                    assertThat(palette.entryList().getCellBounds(1, 1).height).isEqualTo(UIScale.scale(24));
                    assertThat(palette.hintBar().getHeight()).isEqualTo(UIScale.scale(26));
                }
            } finally { themes.configure(Appearance.DARK, UiFontConfig.defaults()); }
        });
    }

    @Test void renderPaletteResultsAndFormsAtBothScales() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                for (var choice : List.of(Appearance.DARK, Appearance.LIGHT)) {
                    new ThemeController(choice);
                    var card = palette(new ArrayList<>());
                    card.setTabs(List.of(new CommandPalette.Tab(PaletteScope.ALL_ID, "All", null, null),
                        new CommandPalette.Tab("jasper.commands", "Commands", null, null)), PaletteScope.ALL_ID);
                    card.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "new_tab"), item(COMMANDS, "settings")), null, null);
                    var host = new javax.swing.JPanel(new java.awt.GridBagLayout()); host.add(card);
                    for (int scale : new int[]{1, 2}) saveRender(host, choice + "-palette-" + scale, scale);
                    card.showStep("Connect", List.of(new PaletteStep.Field("host", "Host", "example.org"), new PaletteStep.Field("user", "Username", "dustin")));
                    for (int scale : new int[]{1, 2}) saveRender(host, choice + "-palette-form-" + scale, scale);
                }
            } finally { new ThemeController(); }
        });
    }

    private static Point at(Component component, int x, int y, Component root) {
        return SwingUtilities.convertPoint(component, x, y, root);
    }

    private static void assertPixel(BufferedImage image, Point point, String key, String context) {
        assertThat(new Color(image.getRGB(point.x, point.y), true)).as("%s %s at %s", context, key, point).isEqualTo(UIManager.getColor(key));
    }

    private static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        component.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static JLabel label(Container container, String text) {
        return labels(container).stream().filter(label -> text.equals(label.getText())).findFirst().orElseThrow();
    }

    private static MouseEvent mousePress(Component source, int x, int y) {
        return new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 1L, 0, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private static void layoutTree(Component component) {
        if (!(component instanceof Container container)) return;
        container.doLayout();
        for (Component child : container.getComponents()) layoutTree(child);
    }

    private static List<JLabel> labels(Component component) {
        var found = new ArrayList<JLabel>();
        if (component instanceof JLabel label) found.add(label);
        if (component instanceof Container container) Arrays.stream(container.getComponents()).forEach(child -> found.addAll(labels(child)));
        return found;
    }

    private static boolean hasInputBinding(javax.swing.JTextField field, String action) {
        KeyStroke[] keys = field.getInputMap().allKeys();
        return keys != null && Arrays.stream(keys).anyMatch(key -> action.equals(field.getInputMap().get(key)));
    }

    private static void saveRender(JComponent component, String name, int scale) {
        component.setSize(960, 640);
        layoutTree(component);
        var image = new BufferedImage(960 * scale, 640 * scale, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.scale(scale, scale); component.printAll(graphics); }
        finally { graphics.dispose(); }
        try {
            var folder = java.nio.file.Path.of("build/render-preview");
            java.nio.file.Files.createDirectories(folder);
            javax.imageio.ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
```

- [ ] **Step 2: Write the failing workspace tests**

Replace `jasper-app/src/test/java/dev/jasper/app/workspace/PaletteScopesTest.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.commands.CommandSearch;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.palette.PaletteContext;
import dev.jasper.app.palette.PaletteResults;
import dev.jasper.app.palette.PaletteRow;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.PaletteStep;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.palette.PaletteVerb;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.CommandPaletteShortcutsTest.*;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class PaletteScopesTest {
    /** A scope with no Swing in it: rows and verbs are data; execution records what it was asked to do. */
    static class FakeScope implements PaletteScope {
        final List<String> executed = new ArrayList<>();
        final List<Runnable> listeners = new ArrayList<>();
        int activations;
        boolean inAll = true;
        List<PaletteRow> rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta"));
        PaletteStep.Result stepResult = PaletteStep.Result.done();
        final List<Map<String, String>> completed = new ArrayList<>();
        boolean deferCompletion;
        boolean reuseStep;
        PaletteStep cachedStep;
        java.util.function.Consumer<PaletteStep.Result> pending;
        @Override public String id() { return "test.fake"; }
        @Override public String label() { return "Fake"; }
        @Override public String description() { return "Fixture scope"; }
        @Override public String placeholder() { return "Search fake"; }
        @Override public boolean inAll() { return inAll; }
        @Override public List<PaletteVerb> verbs() {
            return List.of(new PaletteVerb("one", "One"), new PaletteVerb("two", "Two"), new PaletteVerb("three", "Three"));
        }
        @Override public PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) {
            if (!verb.id().equals("three")) return null;
            if (reuseStep && cachedStep != null) return cachedStep;
            return cachedStep = new PaletteStep("Fill " + row.title(),
                List.of(new PaletteStep.Field("first", "First", "pre"), new PaletteStep.Field("second", "Second", "")),
                (values, done) -> {
                    completed.add(values);
                    if (deferCompletion) pending = done; else done.accept(stepResult);
                });
        }
        @Override public void activated(PaletteContext context) { activations++; }
        @Override public PaletteResults search(String query, PaletteContext context) {
            String q = CommandSearch.normalize(query);
            return new PaletteResults(rows.stream()
                .filter(row -> q.isEmpty() || row.title().toLowerCase(Locale.ROOT).contains(q))
                .limit(context.maxResults()).toList(),
                q.isEmpty() ? "Most recent" : null, null);
        }
        @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
            executed.add(row.id() + ":" + verb.id());
        }
        @Override public Subscription onChanged(Runnable listener) {
            listeners.add(listener);
            return new Subscription(() -> listeners.remove(listener));
        }
    }

    @Test void theCommandPaletteShortcutOpensAllWithATabPerScope() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                assertThat(PaletteTestSupport.tabIds(card)).containsExactly(PaletteScope.ALL_ID, PaletteScope.COMMANDS_ID, "test.fake");
                assertThat(PaletteTestSupport.selectedTabId(card)).isEqualTo(PaletteScope.ALL_ID);
                assertThat(PaletteTestSupport.placeholder(card)).isEqualTo("Search everywhere");
                var entries = PaletteTestSupport.entries(card);
                assertThat(entries.getFirst()).isEqualTo("# Commands");
                assertThat(entries).containsSubsequence("# Fake", "test.fake/alpha", "test.fake/beta");
                assertThat(fake.activations).isEqualTo(1);
                assertThat(PaletteTestSupport.selectedRow(card)).isNotNull();
                palette.toggle();
                assertThat(palette.isOpen()).as("the open tab's own shortcut closes").isFalse();
            }
        });
    }

    @Test void allCapsEachSectionAndOffersTheRestInTheScopesTab() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("alpine", "Alpine"), PaletteRow.of("alps", "Alps"));
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.setMaxResults(2);
                palette.toggle();
                card.queryField().setText("alp");
                var entries = PaletteTestSupport.entries(card);
                int section = entries.indexOf("# Fake");
                assertThat(entries.subList(section, section + 4)).containsExactly("# Fake", "test.fake/alpha", "test.fake/alpine", "more:test.fake");
                PaletteTestSupport.selectRow(card, "alpine");
                palette.moveSelection(1);
                assertThat(PaletteTestSupport.selectedRow(card)).as("the More row is selected").isNull();
                palette.enterPressed(0);
                assertThat(palette.isOpen()).isTrue();
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(card.queryField().getText()).as("the query is kept").isEqualTo("alp");
                assertThat(PaletteTestSupport.rowCount(card)).isEqualTo(2);
                assertThat(fake.executed).isEmpty();
            }
        });
    }

    @Test void aScopeThatOptsOutKeepsItsTabButStaysOutOfAll() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope(); fake.inAll = false;
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                assertThat(PaletteTestSupport.tabIds(card)).contains("test.fake");
                assertThat(PaletteTestSupport.entries(card)).doesNotContain("# Fake").noneMatch(entry -> entry.startsWith("test.fake/"));
                assertThat(fake.activations).as("All does not activate it").isZero();
                palette.open("test.fake");
                assertThat(PaletteTestSupport.entries(card)).contains("test.fake/alpha");
            }
        });
    }

    @Test void aScopeThatFailsIsSkippedInAllWhileTheOthersShow() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                owner.scopes().register(new FakeScope() {
                    @Override public String id() { return "test.broken"; }
                    @Override public String label() { return "Broken"; }
                    @Override public PaletteResults search(String query, PaletteContext context) { throw new IllegalStateException("broken"); }
                });
                owner.scopes().register(new FakeScope());
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                assertThat(palette.isOpen()).isTrue();
                assertThat(PaletteTestSupport.entries(card)).contains("# Commands", "# Fake").doesNotContain("# Broken");
            }
        });
    }

    @Test void tabAndShiftTabCycleTheTabsAndKeepTheQuery() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                owner.scopes().register(new FakeScope());
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                card.queryField().setText("be");
                assertThat(palette.tabPressed(false)).isTrue();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(PaletteTestSupport.placeholder(card)).isEqualTo("Type a command");
                assertThat(palette.tabPressed(false)).isTrue();
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                assertThat(card.queryField().getText()).isEqualTo("be");
                assertThat(PaletteTestSupport.entries(card)).containsExactly("test.fake/beta");
                palette.tabPressed(false);
                assertThat(palette.activeScopeId()).as("wraps to All").isEqualTo(PaletteScope.ALL_ID);
                palette.tabPressed(true);
                assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                PaletteTestSupport.clickTab(card, PaletteScope.COMMANDS_ID);
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(PaletteTestSupport.selectedTabId(card)).isEqualTo(PaletteScope.COMMANDS_ID);
                palette.dismiss();
                assertThat(palette.tabPressed(false)).as("closed").isFalse();
            }
        });
    }

    @Test void upAndDownSkipSectionHeadersAcrossScopes() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                owner.scopes().register(new FakeScope());
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                int commands = PaletteTestSupport.entries(card).indexOf("# Fake") - 1;
                assertThat(commands).isPositive();
                palette.moveSelection(commands - 1);
                assertThat(PaletteTestSupport.selectedRowIndex(card)).isEqualTo(commands - 1);
                palette.moveSelection(1);
                assertThat(PaletteTestSupport.selectedRow(card).id()).as("past the Fake header").isEqualTo("alpha");
                palette.moveSelection(-1);
                assertThat(PaletteTestSupport.selectedRowIndex(card)).isEqualTo(commands - 1);
                palette.moveSelection(50);
                assertThat(PaletteTestSupport.selectedRow(card).id()).isEqualTo("beta");
            }
        });
    }

    @Test void theHintBarShowsTheRowsDetailAndItsScopesOtherVerbsWhichAreClickable() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                fake.rows = List.of(new PaletteRow("alpha", "Alpha", "alpha detail", "tag", null, true, null));
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                assertThat(PaletteTestSupport.hint(card)).isEqualTo("alpha detail");
                assertThat(PaletteTestSupport.hintActions(card)).containsExactly("Two ⌘⏎", "Three ⇧⏎");
                PaletteTestSupport.clickHintAction(card, 0);
                assertThat(fake.executed).containsExactly("alpha:two");
                palette.open("test.fake");
                PaletteTestSupport.clickHintAction(card, 1);
                assertThat(palette.stepOpen()).isTrue();
                assertThat(PaletteTestSupport.hintBar(card).isVisible()).as("hidden in a step").isFalse();
                palette.escape();
                assertThat(PaletteTestSupport.hintBar(card).isVisible()).isTrue();
                palette.open(PaletteScope.COMMANDS_ID);
                card.queryField().setText("new tab");
                assertThat(PaletteTestSupport.hint(card)).as("a command's shortcut").isEqualTo("⌘T");
                assertThat(PaletteTestSupport.hintActions(card)).isEmpty();
            }
        });
    }

    @Test void verbsRouteEnterAndCmdEnterThroughTheOriginTarget() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                PaletteTestSupport.executeSelected(card);
                assertThat(palette.isOpen()).isFalse();
                palette.open("test.fake");
                PaletteTestSupport.executeSelected(card, 1);
                palette.open("test.fake");
                PaletteTestSupport.selectRelative(card, 1);
                PaletteTestSupport.executeSelected(card);
                assertThat(fake.executed).containsExactly("alpha:one", "alpha:two", "beta:one");
                palette.toggle();
                card.queryField().setText("new tab");
                PaletteTestSupport.executeSelected(card, 1);
                assertThat(palette.isOpen()).as("Commands has no second verb").isTrue();
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
            }
        });
    }

    @Test void aRowFromAllRunsItsOwnScopesVerbs() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                PaletteTestSupport.selectRow(card, "beta");
                PaletteTestSupport.executeSelected(card, 1);
                assertThat(fake.executed).containsExactly("beta:two");
                palette.toggle();
                PaletteTestSupport.selectRow(card, "beta");
                palette.enterPressed(2);
                assertThat(palette.stepOpen()).as("the scope's own step").isTrue();
                assertThat(PaletteTestSupport.stepTitle(card).getText()).isEqualTo("Fill Beta");
                palette.enterPressed(0);
                assertThat(fake.completed).hasSize(1);
                assertThat(palette.isOpen()).isFalse();
            }
        });
    }

    @Test void scopeChangesRefreshTheOpenListAndRemovingTheActiveScopeDismisses() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var fake = new FakeScope();
                var registration = owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                assertThat(PaletteTestSupport.entries(card).getFirst()).as("the scope's own section label").isEqualTo("# Most recent");
                PaletteTestSupport.selectRelative(card, 1);
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta"), PaletteRow.of("gamma", "Gamma"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(PaletteTestSupport.rowCount(card)).isEqualTo(3);
                assertThat(PaletteTestSupport.selectedRow(card).id()).isEqualTo("beta");
                registration.close();
                assertThat(palette.isOpen()).isFalse();
                assertThat(fake.listeners).isEmpty();
            }
        });
    }

    @Test void scopesRegisteredOrRemovedWhileAllIsOpenUpdateItsTabsAndSections() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle();
                var fake = new FakeScope();
                var registration = owner.scopes().register(fake);
                assertThat(PaletteTestSupport.tabIds(card)).contains("test.fake");
                assertThat(PaletteTestSupport.entries(card)).contains("# Fake");
                fake.rows = List.of(PaletteRow.of("gamma", "Gamma"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(PaletteTestSupport.entries(card)).contains("test.fake/gamma");
                registration.close();
                assertThat(palette.isOpen()).as("All stays open").isTrue();
                assertThat(PaletteTestSupport.tabIds(card)).doesNotContain("test.fake");
                assertThat(PaletteTestSupport.entries(card)).doesNotContain("# Fake");
                assertThat(fake.listeners).isEmpty();
            }
        });
    }

    @Test void routerOpensAllCyclesWithTabAndRunsTheSecondVerbWithCmdEnter() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner);
                var fake = new FakeScope(); owner.scopes().register(fake);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                int mod = primary(true);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_K));
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                for (int i = 0; i < 2; i++) {
                    assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, 0))).isTrue();
                    router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_TAB));
                }
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo("test.fake");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_TAB));
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                owner.commandPalette().open("test.fake");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, mod))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_ENTER));
                assertThat(fake.executed).containsExactly("alpha:two");
                assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_K));
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, mod))).isTrue();
                assertThat(owner.commandPalette().isOpen()).as("Cmd+K on All closes").isFalse();
            }
        });
    }

    @Test void aStepReplacesTheListCompletesWithValuesAndReopensWhereTheScopeAsks() throws Exception {
        edt(() -> {
            try (var owner = new WindowContent(DesktopTestSupport.launcher(new java.util.ArrayDeque<>()), DesktopTestSupport.HOME, path -> {}, () -> {}, () -> {},
                new dev.jasper.app.appearance.ThemeController(dev.jasper.app.config.Appearance.LIGHT),
                dev.jasper.app.config.KeyBindings.defaults(true), new dev.jasper.app.commands.CommandHistory(), true)) {
                var root = install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                var router = PaletteKeyRouterTest.router(owner, true, root);
                palette.open("test.fake");
                card.queryField().setText("al");
                assertThat(PaletteTestSupport.hintActions(card)).containsExactly("Two ⌘⏎", "Three ⇧⏎");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_ENTER));
                assertThat(palette.stepOpen()).isTrue();
                assertThat(card.queryField().getText()).isEqualTo("al");
                assertThat(PaletteTestSupport.stepFields(card)).hasSize(2);
                assertThat(PaletteTestSupport.stepFields(card).getFirst().getText()).isEqualTo("pre");
                assertThat(PaletteTestSupport.stepTitle(card).getText()).isEqualTo("Fill Alpha");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_2, primary(true)))).isTrue();
                assertThat(fake.executed).isEmpty();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, 0))).isTrue();
                assertThat(PaletteTestSupport.stepFocusIndex(card)).isEqualTo(1);
                router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_TAB));
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(PaletteTestSupport.stepFocusIndex(card)).isZero();
                assertThat(palette.activeScopeId()).as("Tab moves between fields in a step").isEqualTo("test.fake");
                PaletteTestSupport.stepFields(card).get(1).setText("two");
                fake.stepResult = PaletteStep.Result.error("Nope");
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(fake.completed).hasSize(1);
                assertThat(fake.completed.getFirst()).containsEntry("first", "pre").containsEntry("second", "two");
                assertThat(palette.stepOpen()).isTrue();
                assertThat(PaletteTestSupport.stepError(card).getText()).isEqualTo("Nope");
                fake.stepResult = PaletteStep.Result.reopen(PaletteScope.COMMANDS_ID, "new_tab");
                palette.enterPressed(1);
                assertThat(fake.completed).hasSize(2);
                assertThat(palette.isOpen()).isTrue();
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(PaletteTestSupport.selectedRow(card).id()).isEqualTo("new_tab");
                assertThat(card.queryField().getText()).isEmpty();
            } finally { new dev.jasper.app.appearance.ThemeController(); }
        });
    }

    @Test void escapeLeavesAStepWithTheQueryIntactAndScopeShortcutsOrDoneDismissIt() throws Exception {
        edt(() -> {
            try (var owner = new WindowContent(DesktopTestSupport.launcher(new java.util.ArrayDeque<>()), DesktopTestSupport.HOME, path -> {}, () -> {}, () -> {},
                new dev.jasper.app.appearance.ThemeController(dev.jasper.app.config.Appearance.LIGHT),
                dev.jasper.app.config.KeyBindings.defaults(true), new dev.jasper.app.commands.CommandHistory(), true)) {
                install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open("test.fake");
                card.queryField().setText("al");
                palette.enterPressed(2);
                assertThat(palette.stepOpen()).isTrue();
                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("alpine", "Alpine"));
                List.copyOf(fake.listeners).forEach(Runnable::run);
                assertThat(palette.stepOpen()).isTrue();
                palette.escape();
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.isOpen()).isTrue();
                assertThat(card.queryField().getText()).isEqualTo("al");
                assertThat(PaletteTestSupport.rowCount(card)).isEqualTo(2);
                palette.enterPressed(2);
                palette.open(PaletteScope.COMMANDS_ID);
                assertThat(palette.stepOpen()).isFalse();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(card.queryField().getText()).isEqualTo("al");
                palette.open("test.fake");
                palette.enterPressed(2);
                fake.stepResult = PaletteStep.Result.done();
                palette.enterPressed(0);
                assertThat(palette.isOpen()).isFalse();
                assertThat(fake.completed).hasSize(1);
            } finally { new dev.jasper.app.appearance.ThemeController(); }
        });
    }

    @Test void cmdShiftEnterIsNotTheThirdVerb() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                var root = install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                owner.commandPalette().open("test.fake");
                router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, primary(true) | InputEvent.SHIFT_DOWN_MASK));
                assertThat(owner.commandPalette().stepOpen()).isFalse();
                assertThat(fake.executed).isEmpty();
                assertThat(owner.commandPalette().isOpen()).isTrue();
            }
        });
    }

    @Test void aSecondEnterWhileAStepsAsyncCompletionIsPendingDoesNothing() throws Exception {
        edt(() -> {
            try (var owner = owner(true)) {
                install(owner); var fake = new FakeScope(); owner.scopes().register(fake);
                var palette = owner.commandPalette();
                palette.open("test.fake");
                fake.deferCompletion = true;
                palette.enterPressed(2);
                assertThat(palette.stepOpen()).isTrue();
                palette.enterPressed(0);
                palette.enterPressed(0);
                assertThat(fake.completed).hasSize(1);
                assertThat(palette.stepOpen()).isTrue();
                assertThat(palette.isOpen()).isTrue();
                fake.pending.accept(PaletteStep.Result.done());
                assertThat(palette.isOpen()).isFalse();
            }
        });
    }

    @Test void queuedCompletionCannotAffectAnotherOpeningEvenWhenScopeReusesItsStep() throws Exception {
        for (int transition = 0; transition < 3; transition++) {
            final int mode = transition;
            var ref = new java.util.concurrent.atomic.AtomicReference<WindowContent>();
            var fake = new FakeScope(); fake.deferCompletion = true; fake.reuseStep = true;
            var delivered = new java.util.concurrent.atomic.AtomicBoolean();
            try {
                edt(() -> {
                    var content = owner(true); ref.set(content); install(content);
                    var registration = content.scopes().register(fake);
                    var palette = content.commandPalette();
                    palette.open(fake.id()); palette.enterPressed(2); palette.enterPressed(0);
                    assertThat(fake.pending).isNotNull();
                    var oldCompletion = fake.pending;
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        delivered.set(true);
                        oldCompletion.accept(PaletteStep.Result.reopen(PaletteScope.COMMANDS_ID, "new_tab"));
                    });
                    if (mode == 0) palette.dismiss();
                    else if (mode == 1) { registration.close(); content.scopes().register(fake); }
                    else {
                        var oldTab = content.currentTab();
                        content.newTab(DesktopTestSupport.HOME); content.closeTab(oldTab);
                    }
                    palette.open(fake.id()); palette.component().queryField().setText("beta");
                    palette.enterPressed(2); palette.enterPressed(0);
                    assertThat(fake.completed).hasSize(2);
                });
                edt(() -> {
                    assertThat(delivered).isTrue();
                    var palette = ref.get().commandPalette();
                    assertThat(palette.isOpen()).as("transition %s", mode).isTrue();
                    assertThat(palette.stepOpen()).isTrue();
                    assertThat(palette.activeScopeId()).isEqualTo("test.fake");
                    assertThat(palette.component().queryField().getText()).isEqualTo("beta");
                    palette.enterPressed(0);
                    assertThat(fake.completed).hasSize(2);
                    assertThat(fake.executed).isEmpty();
                });
            } finally { edt(() -> { if (ref.get() != null) ref.get().close(); }); }
        }
    }
}
```

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile; `cannot find symbol: class PaletteEntry`, `PaletteScope.ALL_ID`, `CommandPalette.Tab`, and so on.

- [ ] **Step 3: Add PaletteEntry and change the scope model**

Create `jasper-app/src/main/java/dev/jasper/app/palette/PaletteEntry.java`:

```java
package dev.jasper.app.palette;

import java.util.Objects;

/** One line of the palette list: a section heading, one scope's row, or a jump to that scope's tab. */
public sealed interface PaletteEntry {
    /** A heading: a scope's label in All, or a scope's own section label ("Recent") in its tab. Never selected. */
    record Header(String label) implements PaletteEntry {
        public Header { Objects.requireNonNull(label); }
    }

    /** A row and the scope it came from; that scope's verbs act on it. */
    record Item(PaletteScope scope, PaletteRow row) implements PaletteEntry {
        public Item { Objects.requireNonNull(scope); Objects.requireNonNull(row); }
    }

    /** "More in Scope…": the scope matched more than All shows; choosing it opens the scope's tab. */
    record More(PaletteScope scope) implements PaletteEntry {
        public More { Objects.requireNonNull(scope); }
    }

    /** The identity a refresh keeps selected; null for a header. Scope ids never contain {@code /}. */
    default String key() {
        return switch (this) {
            case Header header -> null;
            case Item item -> key(item.scope(), item.row().id());
            case More more -> "more:" + more.scope().id();
        };
    }

    default boolean selectable() { return !(this instanceof Header); }

    static String key(PaletteScope scope, String rowId) { return scope.id() + "/" + rowId; }
}
```

Replace `jasper-app/src/main/java/dev/jasper/app/palette/PaletteScope.java` with:

```java
package dev.jasper.app.palette;

import dev.jasper.app.lifecycle.Subscription;

import java.util.List;
import javax.swing.Icon;
import java.util.Optional;

/**
 * One kind of searchable thing. The palette shows an All tab, then a tab per scope; a scope sees only
 * its own query and produces only its own rows. Every method runs on the EDT; search and availability
 * do no I/O.
 */
public interface PaletteScope {
    String COMMANDS_ID = "jasper.commands";
    /** The All tab's id; reserved, so no scope registers under it. */
    String ALL_ID = "jasper.all";

    String id();
    String label();
    default Icon icon() { return null; }
    /** One line, shown as the scope's tab tooltip. */
    default String description() { return ""; }
    String placeholder();
    /** One to three verbs, bound in order to Enter, Cmd/Ctrl+Enter and Shift+Enter. */
    List<PaletteVerb> verbs();
    default boolean monospaceRows() { return false; }
    /** Whether the palette's All tab searches this scope too; true unless the scope opts out. */
    default boolean inAll() { return true; }

    /**
     * A contributed action whose shortcut, while the palette is open, switches to or dismisses this
     * scope's tab instead of being swallowed; the All tab is routed by {@code ActionId} instead.
     */
    default Optional<String> shortcutActionId() { return Optional.empty(); }
    /** This scope's tab, or the All tab, opened; a scope may ask its index for a background refresh here. */
    default void activated(PaletteContext context) {}
    /** At most {@code context.maxResults()} rows. */
    PaletteResults search(String query, PaletteContext context);
    /** Rechecked immediately before execution; a false answer refreshes the list instead of executing. */
    default boolean available(PaletteRow row, PaletteVerb verb, PaletteContext context) { return row.enabled(); }
    /**
     * Consulted before {@link #execute}: a non-null step is shown in the palette instead of running the verb,
     * and {@code execute} is not called for that action. The step's completion does the work.
     */
    default PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) { return null; }
    void execute(PaletteRow row, PaletteVerb verb, PaletteContext context);
    Subscription onChanged(Runnable listener);

    static String requireValidId(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}"))
            throw new IllegalArgumentException("Invalid scope ID: " + id);
        return id;
    }
}
```

In `jasper-app/src/main/java/dev/jasper/app/palette/ScopeRegistry.java`, in `register`, after `String id = PaletteScope.requireValidId(scope.id());`, add:

```java
        if (PaletteScope.ALL_ID.equals(id)) throw new IllegalArgumentException("Reserved scope ID: " + id);
```

In `jasper-app/src/main/java/dev/jasper/app/palette/PaletteContext.java`, replace the javadoc and the compact constructor with:

```java
/**
 * What a scope is told about each query: the platform, the origin pane and how many rows it may return.
 * The All tab asks for one row more than it shows, to learn whether a scope has more.
 */
public record PaletteContext(boolean macOs, PaletteTarget target, int maxResults) {

    public PaletteContext {
        Objects.requireNonNull(target);
        if (maxResults < 1 || maxResults > PaletteResults.MAX_ROWS)
            throw new IllegalArgumentException("Max results must be 1–" + PaletteResults.MAX_ROWS);
    }
```

Leave the two-argument constructor as it is.

In `jasper-app/src/main/java/dev/jasper/app/palette/CommandsScope.java`:
- replace `@Override public String placeholder() { return "Type a command, or > to switch scope"; }` with `@Override public String placeholder() { return "Type a command"; }`;
- delete the line `@Override public List<String> aliases() { return List.of("cmd", "commands", "actions"); }`.

In `jasper-app/src/test/java/dev/jasper/app/palette/PaletteScopeModelTest.java`:
- Replace `static PaletteScope scope(String id, String... aliases) {` with `static PaletteScope scope(String id) {`.
- Delete the line `@Override public List<String> aliases() { return List.of(aliases); }`.
- Replace `var first = scope("test.one", "uno");` with `var first = scope("test.one");`.

Then add this test to the class:

```java
    @Test void theAllTabsIdIsReservedAndContextsAcceptOneMoreThanTheSettingsCap() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new ScopeRegistry()) {
                assertThatIllegalArgumentException().isThrownBy(() -> registry.register(scope(PaletteScope.ALL_ID)));
            }
        });
        assertThat(new PaletteContext(true, PaletteTarget.none(), 21).maxResults()).isEqualTo(21);
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteContext(true, PaletteTarget.none(), 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteContext(true, PaletteTarget.none(), PaletteResults.MAX_ROWS + 1));
    }
```

- [ ] **Step 4: Rewrite the palette component**

Replace `jasper-app/src/main/java/dev/jasper/app/palette/CommandPalette.java` with:

```java
package dev.jasper.app.palette;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Jasper's Search Everywhere: a tab row (All, then each scope), a full-width search field, a one-line
 * result list with section headers, and a hint bar with the selected row's detail and its scope's other
 * verbs. Every colour comes from the installed theme ({@code SearchEverywhere.*}, {@code List.*},
 * {@code Popup.borderColor}); a key a theme lacks falls back to one FlatLaf always defines. The host owns
 * placement, focus, tab switching and key routing.
 */
public final class CommandPalette extends JPanel {
    static final int WIDTH = 680;
    static final int TAB_HEIGHT = 30;
    static final int FIELD_HEIGHT = 40;
    static final int ROW_HEIGHT = 24;
    static final int HEADER_HEIGHT = 22;
    static final int HINT_HEIGHT = 26;
    static final int STEP_ROW_HEIGHT = 36;
    static final int MAX_VISIBLE_LINES = 15;

    /** A tab above the search field: All, or one scope. */
    public record Tab(String id, String label, Icon icon, String tooltip) {
        public Tab { Objects.requireNonNull(id); Objects.requireNonNull(label); }
    }

    private final boolean macOs;
    private final ObjIntConsumer<PaletteEntry> execute;
    private final Consumer<String> tabSelected;
    private final JPanel tabStrip = new FixedHeightPanel(TAB_HEIGHT);
    private final List<TabLabel> tabLabels = new ArrayList<>();
    private final JPanel fieldRow = new FixedHeightPanel(FIELD_HEIGHT);
    private final JTextField query = new JTextField();
    private final DefaultListModel<PaletteEntry> model = new DefaultListModel<>();
    private final JList<PaletteEntry> list = new JList<>(model);
    private final JScrollPane scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    private final JLabel empty = new JLabel("", SwingConstants.CENTER);
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JPanel stepPanel = new JPanel();
    private final JLabel stepTitle = new JLabel();
    private final List<JTextField> stepFields = new ArrayList<>();
    private final List<String> stepNames = new ArrayList<>();
    private final List<JLabel> stepLabels = new ArrayList<>();
    private final JLabel stepError = new JLabel();
    private JPanel errorRow;
    private final JPanel hintBar = new FixedHeightPanel(HINT_HEIGHT);
    private final JLabel hint = new JLabel();
    private final JPanel hintActions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 0, 0));
    private final List<Integer> hintVerbs = new ArrayList<>();
    private String listCard = "empty";
    private int stepFocus = -1;
    private int hover = -1;
    private boolean composing;
    private Color tabSelectedBackground, tabSelectedForeground, foreground, infoForeground, separatorColor,
        separatorForeground, selectionBackground, selectionForeground, hoverBackground, advertiserForeground, linkColor;
    private Font font, smallFont, monoFont;

    CommandPalette(boolean macOs, Consumer<String> queryChanged, ObjIntConsumer<PaletteEntry> execute,
                   Consumer<String> tabSelected) {
        super(new BorderLayout());
        this.macOs = macOs;
        this.execute = Objects.requireNonNull(execute);
        this.tabSelected = Objects.requireNonNull(tabSelected);
        Objects.requireNonNull(queryChanged);
        setOpaque(true);

        tabStrip.setLayout(new FlowLayout(FlowLayout.LEADING, 0, 0));
        query.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search everywhere");
        query.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
            private void changed() { queryChanged.accept(query.getText()); }
        });
        query.addInputMethodListener(new InputMethodListener() {
            @Override public void inputMethodTextChanged(InputMethodEvent event) { composing = uncommittedCharacters(event) > 0; }
            @Override public void caretPositionChanged(InputMethodEvent event) {}
        });
        fieldRow.setLayout(new BorderLayout());
        fieldRow.add(query, BorderLayout.CENTER);
        var top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(tabStrip);
        top.add(fieldRow);
        add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false);
        list.setCellRenderer(new EntryRenderer());
        list.getAccessibleContext().setAccessibleDescription("0 results");
        list.addListSelectionListener(event -> updateHint());
        var mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                int index = entryAt(event.getPoint());
                if (index < 0) return;
                list.setSelectedIndex(index);
                execute.accept(model.get(index), 0);
            }
            @Override public void mouseMoved(MouseEvent event) { setHover(entryAt(event.getPoint())); }
            @Override public void mouseExited(MouseEvent event) { setHover(-1); }
        };
        list.addMouseListener(mouse);
        list.addMouseMotionListener(mouse);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        empty.putClientProperty("html.disable", Boolean.TRUE);
        stepPanel.setLayout(new BoxLayout(stepPanel, BoxLayout.Y_AXIS));
        for (JLabel label : List.of(stepTitle, stepError, hint)) label.putClientProperty("html.disable", Boolean.TRUE);
        cards.add(scroll, "results");
        cards.add(empty, "empty");
        cards.add(stepPanel, "step");
        cardLayout.show(cards, listCard);
        add(cards, BorderLayout.CENTER);

        hintActions.setOpaque(false);
        hintBar.setLayout(new BorderLayout());
        hintBar.add(hint, BorderLayout.CENTER);
        hintBar.add(hintActions, BorderLayout.EAST);
        add(hintBar, BorderLayout.SOUTH);
        setTabs(List.of(new Tab(PaletteScope.ALL_ID, "All", null, null)), PaletteScope.ALL_ID);
        refreshTheme();
    }

    public JTextField queryField() { return query; }
    JList<PaletteEntry> entryList() { return list; }
    JPanel tabStrip() { return tabStrip; }
    JPanel hintBar() { return hintBar; }
    JLabel stepTitle() { return stepTitle; }

    /** Shows {@code tabs} in order and marks {@code selectedId}; the field and list are named after it. */
    void setTabs(List<Tab> tabs, String selectedId) {
        tabStrip.removeAll();
        tabLabels.clear();
        String name = "All";
        for (Tab tab : tabs) {
            var label = new TabLabel(tab, tab.id().equals(selectedId));
            if (label.selected()) name = tab.label();
            tabLabels.add(label);
            tabStrip.add(label);
        }
        query.getAccessibleContext().setAccessibleName("Search " + name.toLowerCase(Locale.ROOT));
        list.getAccessibleContext().setAccessibleName(name);
        applyTabColors();
        tabStrip.revalidate();
        tabStrip.repaint();
    }

    List<String> tabIds() { return tabLabels.stream().map(TabLabel::id).toList(); }
    String selectedTabId() { return tabLabels.stream().filter(TabLabel::selected).map(TabLabel::id).findFirst().orElse(null); }
    /** As a left click on that tab; an unknown id does nothing. */
    void clickTab(String id) { if (tabIds().contains(id)) tabSelected.accept(id); }
    void setPlaceholder(String text) { query.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, text); }

    /** Replaces the list, selecting {@code selectionKey} when present and otherwise the first selectable entry. */
    void setEntries(List<PaletteEntry> entries, String selectionKey, String emptyText) {
        long rows = entries.stream().filter(entry -> entry instanceof PaletteEntry.Item).count();
        if (rows > PaletteResults.MAX_ROWS) throw new IllegalArgumentException("Too many palette results");
        int selected = -1, first = -1;
        model.clear();
        hover = -1;
        for (int i = 0; i < entries.size(); i++) {
            PaletteEntry entry = entries.get(i);
            model.addElement(entry);
            if (entry.selectable() && first < 0) first = i;
            if (selectionKey != null && selectionKey.equals(entry.key())) selected = i;
        }
        if (selected < 0) selected = first;
        empty.setText(emptyText == null ? "" : emptyText);
        if (selected < 0) list.clearSelection(); else list.setSelectedIndex(selected);
        listCard = first < 0 ? "empty" : "results";
        if (!stepShowing()) cardLayout.show(cards, listCard);
        list.getAccessibleContext().setAccessibleDescription(rows + " results");
        updateHint();
        revalidate();
        if (selected >= 0) {
            scroll.doLayout();
            scroll.getViewport().doLayout();
            reveal(selected);
        }
        repaint();
    }

    PaletteEntry selectedEntry() { return list.getSelectedValue(); }

    String selectedKey() {
        PaletteEntry entry = list.getSelectedValue();
        return entry == null ? null : entry.key();
    }

    /** Selects the first row with {@code rowId}, whichever scope it came from. */
    void selectRow(String rowId) {
        for (int i = 0; i < model.size(); i++)
            if (model.get(i) instanceof PaletteEntry.Item item && item.row().id().equals(rowId)) {
                list.setSelectedIndex(i);
                reveal(i);
                return;
            }
    }

    /** Moves {@code delta} selectable entries, skipping headers and stopping at either end. */
    void selectRelative(int delta) {
        int index = list.getSelectedIndex();
        if (index < 0 || delta == 0) return;
        int direction = Integer.signum(delta), remaining = Math.abs(delta), target = index;
        for (int i = index + direction; remaining > 0 && i >= 0 && i < model.size(); i += direction)
            if (model.get(i).selectable()) { target = i; remaining--; }
        list.setSelectedIndex(target);
        reveal(target);
    }

    void executeSelected() { executeSelected(0); }

    void executeSelected(int verb) {
        PaletteEntry entry = list.getSelectedValue();
        if (entry != null) execute.accept(entry, verb);
    }

    boolean composing() { return composing; }
    int itemHeight() { return UIScale.scale(ROW_HEIGHT); }
    String hintText() { return hint.getText(); }

    List<String> hintActionTexts() {
        var texts = new ArrayList<String>();
        for (Component action : hintActions.getComponents()) texts.add(((JLabel) action).getText());
        return texts;
    }

    /** As a click on the {@code index}th hint action. */
    void clickHintAction(int index) { executeSelected(hintVerbs.get(index)); }

    /** Shows a form instead of the list; the tabs and query stay and the hint bar hides. The first field takes focus. */
    void showStep(String title, List<PaletteStep.Field> fields) {
        stepPanel.removeAll(); stepFields.clear(); stepNames.clear(); stepLabels.clear();
        stepTitle.setText(title);
        stepPanel.add(row(stepTitle));
        for (PaletteStep.Field field : fields) {
            var label = new JLabel(field.label());
            label.putClientProperty("html.disable", Boolean.TRUE);
            label.setPreferredSize(new Dimension(UIScale.scale(140), 0));
            var text = new JTextField(field.prefill());
            text.getAccessibleContext().setAccessibleName(field.label());
            var line = new FixedHeightPanel(STEP_ROW_HEIGHT);
            line.setLayout(new BorderLayout(UIScale.scale(10), 0));
            line.setOpaque(false);
            line.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(4), UIScale.scale(12), UIScale.scale(4), UIScale.scale(12)));
            line.add(label, BorderLayout.LINE_START);
            line.add(text, BorderLayout.CENTER);
            stepPanel.add(line);
            stepFields.add(text); stepNames.add(field.name()); stepLabels.add(label);
        }
        stepError.setText("");
        stepError.setVisible(false);
        errorRow = row(stepError);
        errorRow.setVisible(false);
        stepPanel.add(errorRow);
        applyStepColors();
        cardLayout.show(cards, "step");
        hintBar.setVisible(false);
        stepFocus = 0;
        stepFields.getFirst().requestFocusInWindow();
        stepFields.getFirst().selectAll();
        revalidate(); repaint();
    }

    void hideStep() {
        stepPanel.removeAll(); stepFields.clear(); stepNames.clear(); stepLabels.clear();
        errorRow = null;
        stepFocus = -1;
        hintBar.setVisible(true);
        cardLayout.show(cards, listCard);
        revalidate(); repaint();
    }

    boolean stepShowing() { return stepFocus >= 0; }
    List<JTextField> stepFields() { return List.copyOf(stepFields); }
    int stepFocusIndex() { return stepFocus; }
    JLabel stepError() { return stepError; }

    /** Moves focus to the next (or previous) field, wrapping. */
    void focusStepField(int delta) {
        if (stepFields.isEmpty()) return;
        stepFocus = Math.floorMod(stepFocus + delta, stepFields.size());
        JTextField field = stepFields.get(stepFocus);
        field.requestFocusInWindow();
        field.selectAll();
    }

    Map<String, String> stepValues() {
        var values = new LinkedHashMap<String, String>();
        for (int i = 0; i < stepFields.size(); i++) values.put(stepNames.get(i), stepFields.get(i).getText());
        return values;
    }

    void setStepError(String message) {
        stepError.setText(message == null ? "" : message);
        stepError.setVisible(message != null);
        if (errorRow != null) errorRow.setVisible(message != null);
        revalidate(); repaint();
    }

    void refreshTheme() {
        Color headerBackground = color("SearchEverywhere.Header.background", "Panel.background");
        tabSelectedBackground = color("SearchEverywhere.Tab.selectedBackground", "List.selectionInactiveBackground");
        tabSelectedForeground = color("SearchEverywhere.Tab.selectedForeground", "Label.foreground");
        foreground = color("List.foreground", "Label.foreground");
        infoForeground = color("SearchEverywhere.SearchField.infoForeground", "Label.disabledForeground");
        separatorColor = color("SearchEverywhere.List.separatorColor", "Separator.foreground");
        separatorForeground = color("SearchEverywhere.List.separatorForeground", "Label.disabledForeground");
        Color listBackground = color("List.background", "Panel.background");
        selectionBackground = UIManager.getColor("List.selectionBackground");
        selectionForeground = UIManager.getColor("List.selectionForeground");
        hoverBackground = UIManager.getColor("List.hoverBackground");
        Color advertiserBackground = color("SearchEverywhere.Advertiser.background", "Panel.background");
        advertiserForeground = color("SearchEverywhere.Advertiser.foreground", "Label.disabledForeground");
        linkColor = UIManager.getColor("Component.linkColor");
        Color border = color("Popup.borderColor", "PopupMenu.borderColor");
        Color fieldBackground = color("SearchEverywhere.SearchField.background", "TextField.background");
        Color fieldBorder = color("SearchEverywhere.SearchField.borderColor", "Component.borderColor");
        font = UIManager.getFont("Label.font");
        smallFont = font.deriveFont(Font.PLAIN, font.getSize2D() - UIScale.scale(1f));
        monoFont = new Font(Font.MONOSPACED, Font.PLAIN, 13).deriveFont(font.getSize2D());

        setBackground(listBackground);
        setBorder(BorderFactory.createLineBorder(border, UIScale.scale(1)));
        tabStrip.setBackground(headerBackground);
        fieldRow.setBackground(fieldBackground);
        fieldRow.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(6), UIScale.scale(8), UIScale.scale(6), UIScale.scale(8)));
        query.putClientProperty(FlatClientProperties.STYLE, "background: " + hex(fieldBackground)
            + "; borderColor: " + hex(fieldBorder) + "; placeholderForeground: " + hex(infoForeground));
        query.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, AppIcons.icon("search"));
        query.setFont(font);
        list.setBackground(listBackground);
        list.setForeground(foreground);
        list.setSelectionBackground(selectionBackground);
        list.setSelectionForeground(selectionForeground);
        list.setFont(font);
        scroll.getViewport().setBackground(listBackground);
        cards.setBackground(listBackground);
        stepPanel.setBackground(listBackground);
        empty.setForeground(infoForeground);
        empty.setFont(font);
        hintBar.setBackground(advertiserBackground);
        hintBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(UIScale.scale(1), 0, 0, 0, separatorColor),
            BorderFactory.createEmptyBorder(0, UIScale.scale(12), 0, UIScale.scale(12))));
        hint.setForeground(advertiserForeground);
        hint.setFont(smallFont);
        applyTabColors();
        applyHintColors();
        applyStepColors();
        revalidate(); repaint();
    }

    @Override public Dimension getPreferredSize() {
        Insets insets = getInsets();
        int height = TAB_HEIGHT + FIELD_HEIGHT + (stepShowing()
            ? HEADER_HEIGHT + stepFields.size() * STEP_ROW_HEIGHT + (stepError.isVisible() ? HEADER_HEIGHT : 0)
            : listHeight() + HINT_HEIGHT);
        return new Dimension(UIScale.scale(WIDTH), UIScale.scale(height) + insets.top + insets.bottom);
    }

    private int listHeight() {
        if ("empty".equals(listCard)) return ROW_HEIGHT;
        int height = 0;
        for (int i = 0; i < Math.min(model.size(), MAX_VISIBLE_LINES); i++)
            height += model.get(i) instanceof PaletteEntry.Header ? HEADER_HEIGHT : ROW_HEIGHT;
        return height;
    }

    private void reveal(int index) {
        if (index > 0 && !model.get(index - 1).selectable()) list.ensureIndexIsVisible(index - 1);
        list.ensureIndexIsVisible(index);
    }

    private int entryAt(Point point) {
        int index = list.locationToIndex(point);
        if (index < 0) return -1;
        Rectangle bounds = list.getCellBounds(index, index);
        return bounds != null && bounds.contains(point) && model.get(index).selectable() ? index : -1;
    }

    private void setHover(int index) {
        if (index == hover) return;
        hover = index;
        list.repaint();
    }

    private void updateHint() {
        hintActions.removeAll();
        hintVerbs.clear();
        String text = "";
        PaletteEntry entry = list.getSelectedValue();
        if (entry instanceof PaletteEntry.Item item) {
            PaletteRow row = item.row();
            text = row.detail() != null ? row.detail() : row.tag() != null ? row.tag() : "";
            List<PaletteVerb> verbs = item.scope().verbs();
            for (int verb = 1; verb < Math.min(3, verbs.size()); verb++) {
                int chosen = verb;
                var action = new SizedLabel(verbs.get(verb).label() + " " + keys(verb), HINT_HEIGHT - 1);
                action.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(14), 0, 0));
                action.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                action.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent event) { if (SwingUtilities.isLeftMouseButton(event)) executeSelected(chosen); }
                });
                hintActions.add(action);
                hintVerbs.add(verb);
            }
        } else if (entry instanceof PaletteEntry.More more) {
            text = "Show every match in " + more.scope().label();
        }
        hint.setText(text);
        applyHintColors();
        hintActions.revalidate();
        hintActions.repaint();
    }

    private String keys(int verb) {
        return (macOs ? new String[]{"⏎", "⌘⏎", "⇧⏎"} : new String[]{"Enter", "Ctrl+Enter", "Shift+Enter"})[verb];
    }

    private void applyTabColors() {
        for (TabLabel label : tabLabels) {
            label.setForeground(label.selected() ? tabSelectedForeground : foreground);
            if (font != null) label.setFont(font);
        }
    }

    private void applyHintColors() {
        for (Component action : hintActions.getComponents()) {
            action.setForeground(linkColor);
            if (smallFont != null) action.setFont(smallFont);
        }
    }

    private void applyStepColors() {
        if (foreground == null) return;
        stepTitle.setForeground(separatorForeground);
        stepTitle.setFont(smallFont);
        for (JLabel label : stepLabels) { label.setForeground(infoForeground); label.setFont(smallFont); }
        stepError.setForeground(UIManager.getColor("Actions.Red"));
        stepError.setFont(smallFont);
    }

    private static JPanel row(JLabel label) {
        var row = new FixedHeightPanel(HEADER_HEIGHT);
        row.setLayout(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(12), 0, UIScale.scale(12)));
        row.add(label, BorderLayout.CENTER);
        return row;
    }

    private static Color color(String key, String fallbackKey) {
        Color value = UIManager.getColor(key);
        return value != null ? value : UIManager.getColor(fallbackKey);
    }

    private static String hex(Color color) { return String.format("#%06x", color.getRGB() & 0xffffff); }

    private static int uncommittedCharacters(InputMethodEvent event) {
        AttributedCharacterIterator text = event.getText();
        if (text == null) return 0;
        int length = text.getEndIndex() - text.getBeginIndex();
        return Math.max(0, length - event.getCommittedCharacterCount());
    }

    private static class FixedHeightPanel extends JPanel {
        private final int logicalHeight;

        FixedHeightPanel(int logicalHeight) { this.logicalHeight = logicalHeight; }

        @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(logicalHeight)); }
        @Override public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, UIScale.scale(logicalHeight)); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, UIScale.scale(logicalHeight)); }
    }

    /** A label whose height is fixed in logical pixels, so tabs and hints stay aligned under any font. */
    private static class SizedLabel extends JLabel {
        private final int logicalHeight;

        SizedLabel(String text, int logicalHeight) {
            super(text);
            this.logicalHeight = logicalHeight;
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, UIScale.scale(logicalHeight)); }
    }

    private final class TabLabel extends SizedLabel {
        private final String id;
        private final boolean selected;

        TabLabel(Tab tab, boolean selected) {
            super(tab.label(), TAB_HEIGHT);
            this.id = tab.id();
            this.selected = selected;
            setIcon(tab.icon());
            setIconTextGap(UIScale.scale(4));
            setToolTipText(tab.tooltip());
            setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(10), 0, UIScale.scale(10)));
            getAccessibleContext().setAccessibleName(tab.label() + " tab");
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) { if (SwingUtilities.isLeftMouseButton(event)) tabSelected.accept(id); }
            });
        }

        String id() { return id; }
        boolean selected() { return selected; }

        @Override protected void paintComponent(Graphics graphics) {
            if (selected && tabSelectedBackground != null) {
                graphics.setColor(tabSelectedBackground);
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paintComponent(graphics);
        }
    }

    private final class EntryRenderer extends JPanel implements ListCellRenderer<PaletteEntry> {
        private final JLabel icon = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel detail = new JLabel();
        private final JLabel tag = new JLabel();
        private PaletteEntry entry;
        private boolean selected, hovered, first;

        EntryRenderer() {
            setLayout(null);
            setOpaque(false);
            for (JLabel label : List.of(icon, title, detail, tag)) {
                label.setOpaque(false);
                label.putClientProperty("html.disable", Boolean.TRUE);
                add(label);
            }
            icon.setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override public Component getListCellRendererComponent(JList<? extends PaletteEntry> source, PaletteEntry value,
                                                                 int index, boolean isSelected, boolean cellHasFocus) {
            entry = value;
            selected = isSelected && value.selectable();
            hovered = index == hover && !selected && value.selectable();
            first = index == 0;
            icon.setIcon(null);
            detail.setText("");
            tag.setText("");
            switch (value) {
                case PaletteEntry.Header header -> {
                    title.setText(header.label());
                    title.setFont(smallFont);
                    title.setForeground(separatorForeground);
                }
                case PaletteEntry.Item item -> {
                    PaletteRow row = item.row();
                    icon.setIcon(row.icon());
                    title.setText(row.title());
                    title.setFont(item.scope().monospaceRows() ? monoFont : font);
                    title.setForeground(selected ? selectionForeground : row.enabled() ? foreground : infoForeground);
                    detail.setText(row.detail() == null ? "" : row.detail());
                    detail.setFont(font);
                    detail.setForeground(selected ? selectionForeground : infoForeground);
                    tag.setText(row.tag() == null ? "" : row.tag());
                    tag.setFont(smallFont);
                    tag.setForeground(selected ? selectionForeground : infoForeground);
                }
                case PaletteEntry.More more -> {
                    title.setText("More in " + more.scope().label() + "…");
                    title.setFont(font);
                    title.setForeground(selected ? selectionForeground : infoForeground);
                }
            }
            return this;
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(0, UIScale.scale(entry instanceof PaletteEntry.Header ? HEADER_HEIGHT : ROW_HEIGHT));
        }

        @Override public void doLayout() {
            int side = UIScale.scale(8), gap = UIScale.scale(8), iconSize = UIScale.scale(16), height = getHeight();
            if (entry instanceof PaletteEntry.Header) {
                title.setBounds(side, 0, Math.max(0, getWidth() - 2 * side), height);
                for (JLabel unused : List.of(icon, detail, tag)) unused.setBounds(0, 0, 0, 0);
                return;
            }
            icon.setBounds(side, (height - iconSize) / 2, iconSize, iconSize);
            int start = side + iconSize + gap, end = getWidth() - side;
            int titleWidth = title.getPreferredSize().width;
            int tagWidth = tag.getText().isEmpty() ? 0 : tag.getPreferredSize().width;
            int detailWidth = detail.getText().isEmpty() ? 0 : detail.getPreferredSize().width;
            // When space runs out the tag goes first; then the detail, and finally the title, are cut short.
            boolean showTag = tagWidth > 0 && start + titleWidth + gap + tagWidth <= end;
            int textEnd = showTag ? end - tagWidth - gap : end;
            tag.setBounds(showTag ? end - tagWidth : 0, 0, showTag ? tagWidth : 0, height);
            int shownTitle = Math.max(0, Math.min(titleWidth, textEnd - start));
            title.setBounds(start, 0, shownTitle, height);
            int detailStart = start + shownTitle + gap;
            detail.setBounds(detailStart, 0, Math.max(0, Math.min(detailWidth, textEnd - detailStart)), height);
        }

        @Override protected void paintComponent(Graphics graphics) {
            // CellRendererPane assigns the bounds just before painting; lay the labels out at that width.
            doLayout();
            Color fill = selected ? selectionBackground : hovered ? hoverBackground : null;
            if (fill != null) {
                graphics.setColor(fill);
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
            if (entry instanceof PaletteEntry.Header && !first) {
                graphics.setColor(separatorColor);
                graphics.fillRect(0, 0, getWidth(), UIScale.scale(1));
            }
            super.paintComponent(graphics);
        }
    }
}
```

- [ ] **Step 5: Rewrite the controller**

Replace `jasper-app/src/main/java/dev/jasper/app/palette/PaletteController.java` with:

```java
package dev.jasper.app.palette;

import dev.jasper.app.config.PaletteSettings;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/**
 * EDT-owned palette state: the open tab (All or one scope), the query, a step and queued completions.
 * All searches every scope that takes part in it, a section each, capped at the result limit. Host
 * callbacks contain all workspace integration.
 */
public final class PaletteController implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(PaletteController.class.getName());
    private final ScopeRegistry scopes;
    private final boolean macOs;
    private final CommandPalette palette;
    private final Runnable layout, dismissed, updateActions;
    private final Function<String, String> shortcut;
    private final BooleanSupplier batching;
    private final Consumer<String> reportError, reopen;
    private final Subscription scopesListener;
    private final List<Subscription> tabListeners = new ArrayList<>();
    /** The open tab, {@link PaletteScope#ALL_ID} or a scope id; null while closed. */
    private String tab;
    /** The open tab's scope; null for All. */
    private PaletteScope active;
    private PaletteStep step;
    private PaletteScope stepScope;
    private PaletteContext context;
    private BooleanSupplier originValid = () -> false;
    private boolean open, closed, completing, dirty = true, rosterChanged;
    private long generation;
    private int maxResults = PaletteSettings.DEFAULT_MAX_RESULTS;

    public PaletteController(ScopeRegistry scopes, boolean macOs, Runnable layout, Runnable dismissed,
                      Function<String, String> shortcut, BooleanSupplier batching, Runnable updateActions,
                      Consumer<String> reportError, Consumer<String> reopen) {
        this.scopes = scopes; this.macOs = macOs; this.layout = layout; this.dismissed = dismissed;
        this.shortcut = shortcut; this.batching = batching; this.updateActions = updateActions;
        this.reportError = reportError; this.reopen = reopen;
        context = new PaletteContext(macOs, PaletteTarget.none());
        palette = new CommandPalette(macOs, this::queryChanged, this::execute, this::selectTab);
        scopesListener = scopes.onChanged(this::rosterChanged);
    }

    /** Whether {@code id} is a tab: All, or a registered scope. */
    public boolean hasScope(String id) { return PaletteScope.ALL_ID.equals(id) || scopes.find(id).isPresent(); }

    /** The registered scope whose shortcut action is {@code actionId}. */
    public Optional<String> scopeForShortcutAction(String actionId) {
        return scopes.byShortcutAction(actionId).map(PaletteScope::id);
    }

    /** Captures the origin only on first open; changing tabs retains that captured target. */
    public boolean open(String tabId, PaletteTarget target, BooleanSupplier valid) { return open(tabId, target, valid, null, null); }

    /**
     * As {@link #open(String, PaletteTarget, BooleanSupplier)}; afterwards {@code queryOrNull} replaces the
     * query text and {@code rowIdOrNull} selects a row of the resulting list. Opening the tab that is
     * already showing dismisses instead, and then neither is applied.
     */
    public boolean open(String tabId, PaletteTarget target, BooleanSupplier valid, String queryOrNull, String rowIdOrNull) {
        if (closed || !hasScope(tabId)) return false;
        if (open) {
            if (tabId.equals(tab)) { dismiss(); return false; }
            activate(tabId);
        } else {
            generation++;
            originValid = valid;
            context = new PaletteContext(macOs, target, maxResults);
            palette.queryField().setText("");
            open = true;
            palette.setVisible(true);
            activate(tabId);
        }
        if (queryOrNull != null) palette.queryField().setText(queryOrNull);
        if (rowIdOrNull != null) palette.selectRow(rowIdOrNull);
        return true;
    }

    /** Selects the tab {@code id}, keeping the query; the open tab again does nothing. */
    public void selectTab(String id) {
        if (!open || id.equals(tab) || !hasScope(id)) return;
        activate(id);
    }

    /** Selects the next ({@code delta} 1) or previous ({@code -1}) tab, wrapping, and keeps the query. */
    public void cycleTab(int delta) {
        if (!open) return;
        var ids = new ArrayList<String>();
        ids.add(PaletteScope.ALL_ID);
        scopes.scopes().forEach(scope -> ids.add(scope.id()));
        int index = Math.max(0, ids.indexOf(tab));
        activate(ids.get(Math.floorMod(index + delta, ids.size())));
    }

    private void activate(String tabId) {
        generation++;
        if (step != null) { step = null; stepScope = null; palette.hideStep(); }
        completing = false;
        rosterChanged = false;
        tabListeners.forEach(Subscription::close);
        tabListeners.clear();
        tab = tabId;
        active = PaletteScope.ALL_ID.equals(tabId) ? null : scopes.find(tabId).orElseThrow();
        for (PaletteScope scope : tabScopes()) {
            tabListeners.add(scope.onChanged(this::changed));
            scope.activated(context);
        }
        showTabs();
        rebuild(false);
    }

    /** The scopes the open tab searches: its own scope, or every scope that takes part in All. */
    private List<PaletteScope> tabScopes() {
        if (active != null) return List.of(active);
        return scopes.scopes().stream().filter(PaletteScope::inAll).toList();
    }

    private void showTabs() {
        var tabs = new ArrayList<CommandPalette.Tab>();
        tabs.add(new CommandPalette.Tab(PaletteScope.ALL_ID, "All", null, tooltip("Search everywhere", PaletteScope.ALL_ID)));
        for (PaletteScope scope : scopes.scopes())
            tabs.add(new CommandPalette.Tab(scope.id(), scope.label(), scope.icon(), tooltip(scope.description(), scope.id())));
        palette.setTabs(tabs, tab);
        palette.setPlaceholder(active == null ? "Search everywhere" : active.placeholder());
    }

    private String tooltip(String description, String id) {
        String keys = shortcut.apply(id);
        if (keys == null || keys.isBlank()) return description.isBlank() ? null : description;
        return description.isBlank() ? keys : description + " (" + keys + ")";
    }

    /** The hard cap every scope returns; a live change re-runs the open query under the new cap. */
    public void setMaxResults(int value) {
        if (value == maxResults) return;
        maxResults = value;
        context = new PaletteContext(macOs, context.target(), maxResults);
        if (open) rebuild(true);
    }

    public int maxResults() { return maxResults; }
    public void dismiss() { if (open) restoreAndHide(); }
    public boolean isOpen() { return open; }
    /** The open tab: {@link PaletteScope#ALL_ID} or a scope id; null while closed. */
    public String activeScopeId() { return tab; }
    public boolean composing() { return palette.composing(); }
    public CommandPalette component() { return palette; }

    /** Enter and its modifier variants: completes an open step, otherwise acts on the selected entry. */
    public void enterPressed(int verb) {
        if (!open) return;
        if (step != null) { if (!completing) completeStep(); } else palette.executeSelected(verb);
    }

    public void moveSelection(int delta) { if (open && step == null) palette.selectRelative(delta); }
    public boolean stepOpen() { return open && step != null; }

    /** Escape leaves a step, and otherwise dismisses. */
    public void escape() {
        if (step != null) { closeStep(); return; }
        dismiss();
    }

    public boolean tabPressed() { return tabPressed(false); }

    /** Tab moves between step fields, and otherwise to the next tab; Shift+Tab goes back. */
    public boolean tabPressed(boolean backwards) {
        if (!open) return false;
        if (step != null) { palette.focusStepField(backwards ? -1 : 1); return true; }
        cycleTab(backwards ? -1 : 1);
        return true;
    }

    private void showStep(PaletteStep pending, PaletteScope owner) {
        step = pending;
        stepScope = owner;
        palette.showStep(pending.title(), pending.fields());
        layout.run();
    }

    private void closeStep() {
        generation++;
        step = null;
        stepScope = null;
        completing = false;
        palette.hideStep();
        rebuild(true);
        palette.queryField().requestFocusInWindow();
    }

    private void completeStep() {
        PaletteStep current = step;
        long submitted = ++generation;
        completing = true;
        palette.setStepError(null);
        try {
            current.complete().accept(palette.stepValues(), result -> {
                Runnable apply = () -> {
                    if (!acceptsCompletion(submitted, current)) return;
                    completing = false;
                    if (result.error() != null) { palette.setStepError(result.error()); layout.run(); return; }
                    step = null;
                    stepScope = null;
                    palette.hideStep();
                    restoreAndHide();
                    if (result.reopenScopeId() != null) {
                        reopen.accept(result.reopenScopeId());
                        if (open && result.reopenQuery() != null) palette.queryField().setText(result.reopenQuery());
                        if (open && result.reopenRowId() != null) palette.selectRow(result.reopenRowId());
                    }
                };
                if (SwingUtilities.isEventDispatchThread()) apply.run();
                else SwingUtilities.invokeLater(apply);
            });
        } catch (RuntimeException failure) {
            if (!acceptsCompletion(submitted, current)) return;
            completing = false;
            LOG.log(System.Logger.Level.ERROR, "Palette step completion failed", failure);
            palette.setStepError("Could not complete: " + failure.getMessage());
        }
    }

    private boolean acceptsCompletion(long submitted, PaletteStep current) {
        return !closed && open && generation == submitted && step == current
            && originValid.getAsBoolean() && scopes.contains(stepScope);
    }

    private void queryChanged(String query) {
        if (open) rebuild(false);
    }

    private void changed() {
        dirty = true;
        if (!batching.getAsBoolean()) refreshIfChanged();
    }

    private void rosterChanged() {
        rosterChanged = true;
        changed();
    }

    public void refreshIfChanged() {
        if (!open) return;
        if (!valid()) { dismiss(); return; }
        if (rosterChanged) applyRoster();
        if (dirty) rebuild(true);
    }

    public void refresh() {
        if (!open) return;
        if (!valid()) { dismiss(); return; }
        if (rosterChanged) applyRoster();
        rebuild(true);
    }

    private boolean valid() { return originValid.getAsBoolean() && (active == null || scopes.contains(active)); }

    /** A scope came or went while open: show the new tabs and, on All, listen to the new roster. */
    private void applyRoster() {
        rosterChanged = false;
        if (active == null) {
            tabListeners.forEach(Subscription::close);
            tabListeners.clear();
            for (PaletteScope scope : tabScopes()) tabListeners.add(scope.onChanged(this::changed));
        }
        showTabs();
    }

    public void refreshTheme() { palette.refreshTheme(); if (open) layout.run(); }

    private void rebuild(boolean preserve) {
        if (!open || tab == null) return;
        if (step != null) { dirty = true; return; }
        dirty = false;
        String keep = preserve ? palette.selectedKey() : null;
        String query = palette.queryField().getText();
        var entries = new ArrayList<PaletteEntry>();
        String initial = null;
        if (active != null) {
            PaletteResults results = active.search(query, context);
            if (results.sectionLabel() != null && !results.rows().isEmpty()) entries.add(new PaletteEntry.Header(results.sectionLabel()));
            for (PaletteRow row : results.rows()) entries.add(new PaletteEntry.Item(active, row));
            if (results.initialSelectionId() != null) initial = PaletteEntry.key(active, results.initialSelectionId());
        } else {
            // One row more than shown tells whether a scope has more matches than All has room for.
            var probe = new PaletteContext(macOs, context.target(), maxResults + 1);
            for (PaletteScope scope : tabScopes()) {
                PaletteResults results;
                try { results = scope.search(query, probe); }
                catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.ERROR, "Palette scope failed to search: " + scope.id(), failure);
                    continue;
                }
                if (results.rows().isEmpty()) continue;
                entries.add(new PaletteEntry.Header(scope.label()));
                List<PaletteRow> shown = results.rows().subList(0, Math.min(maxResults, results.rows().size()));
                for (PaletteRow row : shown) entries.add(new PaletteEntry.Item(scope, row));
                if (results.rows().size() > maxResults) entries.add(new PaletteEntry.More(scope));
                String first = results.initialSelectionId();
                if (initial == null && first != null && shown.stream().anyMatch(row -> row.id().equals(first)))
                    initial = PaletteEntry.key(scope, first);
            }
        }
        palette.setEntries(entries, keep != null ? keep : initial,
            active == null ? "Nothing found" : "No matching " + active.label().toLowerCase(Locale.ROOT));
        layout.run();
    }

    private void execute(PaletteEntry entry, int verbIndex) {
        if (!open) return;
        if (entry instanceof PaletteEntry.More more) { selectTab(more.scope().id()); return; }
        if (!(entry instanceof PaletteEntry.Item item)) return;
        PaletteScope scope = item.scope();
        PaletteRow row = item.row();
        if (!valid() || !scopes.contains(scope)) { dismiss(); return; }
        if (verbIndex < 0 || verbIndex >= scope.verbs().size()) return;
        PaletteVerb verb = scope.verbs().get(verbIndex);
        updateActions.run();
        if (!valid() || !scopes.contains(scope) || !scope.available(row, verb, context)) { refresh(); return; }
        PaletteStep pending = scope.step(row, verb, context);
        if (pending != null) { showStep(pending, scope); return; }
        PaletteContext target = context;
        restoreAndHide();
        try {
            scope.execute(row, verb, target);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.ERROR, "Palette action failed: " + scope.id() + " " + row.id(), failure);
            reportError.accept("Could not run " + row.title() + ". See the application log for details.");
        }
    }

    private void restoreAndHide() {
        generation++;
        open = false; step = null; stepScope = null; completing = false;
        palette.hideStep(); palette.setVisible(false);
        tabListeners.forEach(Subscription::close);
        tabListeners.clear();
        tab = null;
        active = null;
        context = new PaletteContext(macOs, PaletteTarget.none(), maxResults);
        originValid = () -> false;
        dismissed.run();
    }

    @Override public void close() {
        if (closed) return;
        dismiss(); closed = true; generation++;
        scopesListener.close();
    }
}
```

- [ ] **Step 6: Route Cmd+K to All and drop the row numbers**

In `jasper-app/src/main/java/dev/jasper/app/palette/PaletteKeyRouter.java`:
- Delete the line `boolean numbered = modifiers == primary && code >= KeyEvent.VK_1 && code <= KeyEvent.VK_5;`.
- Delete the branch `if (numbered) operation = () -> palette.executeNumber(code - KeyEvent.VK_1 + 1);`, and turn the following `else if (secondVerb)` into `if (secondVerb)`.
- In `scopeFor`, replace `case COMMAND_PALETTE -> PaletteScope.COMMANDS_ID;` with `case COMMAND_PALETTE -> PaletteScope.ALL_ID;`.

In `jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java`:
- Delete the three methods `void openPicker()`, `boolean pickerOpen()` and `void executeNumber(int number)`.
- In `Overlay.paintComponent`, replace the shadow loop:

```java
                g.setColor(new Color(0, 0, 0, owner.theme().chrome().appearance() == dev.jasper.app.config.Appearance.LIGHT ? 2 : 4));
                for (int i = 12; i >= 1; i--) {
                    int expansion = UIScale.scale(i), arc = UIScale.scale(24) + expansion * 2;
                    g.fillRoundRect(card.x - expansion, card.y - expansion, card.width + expansion * 2,
                        card.height + expansion * 2, arc, arc);
                }
```

  with a square one that matches the square popup:

```java
                g.setColor(new Color(0, 0, 0, owner.theme().chrome().dark() ? 4 : 2));
                for (int i = 12; i >= 1; i--) {
                    int expansion = UIScale.scale(i);
                    g.fillRect(card.x - expansion, card.y - expansion, card.width + expansion * 2, card.height + expansion * 2);
                }
```

In `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`:
- Replace `commandPalette = new WindowCommandPalette(this, scopes, PaletteScope.COMMANDS_ID, macOs);` with `commandPalette = new WindowCommandPalette(this, scopes, PaletteScope.ALL_ID, macOs);`.
- In `scopeShortcut`, replace `case PaletteScope.COMMANDS_ID -> ActionId.COMMAND_PALETTE;` with `case PaletteScope.ALL_ID -> ActionId.COMMAND_PALETTE;`.

- [ ] **Step 7: Adapt the remaining tests to the entry list**

Run this rewrite from the repository root. It maps every `PaletteTestSupport.resultList(...)` call chain onto the row-level helpers:

```bash
python3 - <<'PY'
import pathlib, re
FILES = ["jasper-app/src/test/java/dev/jasper/app/workspace/WindowCommandPaletteTest.java",
         "jasper-app/src/test/java/dev/jasper/app/workspace/WindowContributionsTest.java",
         "jasper-app/src/test/java/dev/jasper/app/workspace/PaletteKeyRouterTest.java",
         "jasper-app/src/test/java/dev/jasper/app/workspace/CommandPalettePreview.java"]
CHAINS = [(".getSelectedValue()", "selectedRow({})"), (".getSelectedIndex()", "selectedRowIndex({})"),
          (".getModel().getSize()", "rowCount({})"), (".getFixedCellHeight()", "itemHeight({})"),
          (".getModel().getElementAt(", "rowAt({}, "),
          (".getModel().addListDataListener(", "entryList({}).getModel().addListDataListener("),
          (".getVisibleRect()", "entryList({}).getVisibleRect()")]
MARKER = "PaletteTestSupport.resultList("
for name in FILES:
    path = pathlib.Path(name); text = path.read_text(); out = []; i = 0
    while (j := text.find(MARKER, i)) >= 0:
        start = j + len(MARKER); depth = 1; k = start
        while depth:
            depth += {"(": 1, ")": -1}.get(text[k], 0); k += 1
        arg, rest = text[start:k - 1], text[k:]
        for chain, replacement in CHAINS:
            if rest.startswith(chain):
                out.append(text[i:j] + "PaletteTestSupport." + replacement.format(arg)); i = k + len(chain); break
        else:
            m = re.match(r"\.getCellBounds\((\w+),\s*\1\)", rest)
            if not m: raise SystemExit(f"{name}: unhandled chain {rest[:40]!r}")
            out.append(text[i:j] + f"PaletteTestSupport.rowBounds({arg}, {m.group(1)})"); i = k + m.end()
    out.append(text[i:]); path.write_text("".join(out))
PY
```

Then make these edits by hand.

In `WindowCommandPaletteTest.java`, replace both occurrences of

```java
PaletteTestSupport.setResults(owner.commandPalette().component(), PaletteTestSupport.rows(owner.commandsScope(), List.of(stale)), null, null);
```

with

```java
PaletteTestSupport.setRows(owner.commandPalette().component(), owner.commandsScope(), PaletteTestSupport.rows(owner.commandsScope(), List.of(stale)), null);
```

and replace `assertThat(palette.queryField().getParent().getHeight()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(56));` with `assertThat(palette.queryField().getParent().getHeight()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(40));`.

In `PaletteKeyRouterTest.java`, in `executionRepeatsAndForeignFocusTailsAreConsumedButNewForeignKeysPass`, replace

```java
                assertThat(router.dispatch(press(owner, KeyEvent.VK_1, primary(mac)))).isTrue();
                assertThat(count.get()).isEqualTo(1);
                assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(router.dispatch(press(other, KeyEvent.VK_1, primary(mac)))).isTrue();
                assertThat(router.dispatch(typed(other, '1'))).isTrue();
                assertThat(router.dispatch(release(other, KeyEvent.VK_1))).isTrue();
```

with

```java
                assertThat(router.dispatch(press(owner, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(count.get()).isEqualTo(1);
                assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(router.dispatch(press(other, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(router.dispatch(typed(other, '\n'))).isTrue();
                assertThat(router.dispatch(release(other, KeyEvent.VK_ENTER))).isTrue();
```

In `CommandPaletteShortcutsTest.java`, in `newerOwnersHeldCommandCannotExecuteInOlderDestinationDispatcher`, replace

```java
                assertThat(manager.send(PaletteKeyRouterTest.press(newer, KeyEvent.VK_1, primary(true)))).isTrue();
                assertThat(older.commandPalette().isOpen()).isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.press(older, KeyEvent.VK_1, primary(true)))).isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.typed(older, '1'))).isTrue();
                assertThat(calls.get()).isZero();
                assertThat(manager.send(PaletteKeyRouterTest.release(older, KeyEvent.VK_1))).isTrue();
                assertThat(manager.dispatchers).hasSize(closeSource ? 1 : 2);
                assertThat(manager.send(PaletteKeyRouterTest.press(older, KeyEvent.VK_1, primary(true)))).isTrue();
                assertThat(calls.get()).isEqualTo(1);
                manager.send(PaletteKeyRouterTest.release(older, KeyEvent.VK_1));
```

with

```java
                assertThat(manager.send(PaletteKeyRouterTest.press(newer, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(older.commandPalette().isOpen()).isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.press(older, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.typed(older, '\n'))).isTrue();
                assertThat(calls.get()).isZero();
                assertThat(manager.send(PaletteKeyRouterTest.release(older, KeyEvent.VK_ENTER))).isTrue();
                assertThat(manager.dispatchers).hasSize(closeSource ? 1 : 2);
                assertThat(manager.send(PaletteKeyRouterTest.press(older, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(calls.get()).isEqualTo(1);
                manager.send(PaletteKeyRouterTest.release(older, KeyEvent.VK_ENTER));
```

and in `completePaletteSequencesSendZeroBytesToTheRealTerminalConnector`, replace

```java
                    send.accept(PaletteKeyRouterTest.press(view, KeyEvent.VK_1, primary(mac)));
                    send.accept(PaletteKeyRouterTest.press(view, KeyEvent.VK_1, primary(mac)));
                    send.accept(PaletteKeyRouterTest.typed(view, '1'));
                    send.accept(PaletteKeyRouterTest.release(view, KeyEvent.VK_1));
```

with

```java
                    send.accept(PaletteKeyRouterTest.press(view, KeyEvent.VK_ENTER, 0));
                    send.accept(PaletteKeyRouterTest.press(view, KeyEvent.VK_ENTER, 0));
                    send.accept(PaletteKeyRouterTest.typed(view, '\n'));
                    send.accept(PaletteKeyRouterTest.release(view, KeyEvent.VK_ENTER));
```

In `CommandPalettePreview.java`:

1. Replace the `Scenario` constants

```java
        RECENTS("recents", "", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.COMMANDS_ID),
        PANE_QUERY("pane-query", "pane", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.COMMANDS_ID),
        NO_MATCH("no-match", "quasar never", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.COMMANDS_ID),
        LONG_LABELS("long-labels-narrow", "preview fixture", NARROW_WIDTH, NARROW_HEIGHT, PaletteScope.COMMANDS_ID),
        SCOPE_PICKER("scope-picker", ">", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.COMMANDS_ID);
```

   with

```java
        RECENTS("recents", "", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.ALL_ID),
        PANE_QUERY("pane-query", "pane", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.ALL_ID),
        NO_MATCH("no-match", "quasar never", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.ALL_ID),
        LONG_LABELS("long-labels-narrow", "preview fixture", NARROW_WIDTH, NARROW_HEIGHT, PaletteScope.ALL_ID),
        COMMANDS_TAB("commands-tab", "pane", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.COMMANDS_ID);
```

2. Replace `case SCOPE_PICKER -> 1;` and the comment above it with `case COMMANDS_TAB -> 5;`.
3. In `verifyUiScale` and its report, replace every `560 * expectedScale` with `680 * expectedScale`, and every `56 * expectedScale` with `40 * expectedScale`. Then replace the two row-height uses, `rowHeight != 40 * expectedScale` and `"rowHeight=" + (40 * expectedScale)`, with `rowHeight != 24 * expectedScale` and `"rowHeight=" + (24 * expectedScale)`. Make these as whole-string replacements, and do the row-height ones before the input-height ones, so no replacement rewrites another's result.

- [ ] **Step 8: Run the palette tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.*' --tests '*PaletteScopesTest' --tests '*WindowCommandPaletteTest' --tests '*PaletteKeyRouterTest' --tests '*CommandPaletteShortcutsTest' --tests '*WindowContributionsTest' --tests '*HostedPaletteTest' --tests '*AppExamplesTest'`
Expected: PASS.

Run: `git grep -nE 'openPicker|pickerOpen|executeNumber|aliases\(\)|footerText|Scopes"|chip\(' -- jasper-app/src plugins`
Expected: no output.

- [ ] **Step 9: Run check and commit**

Run: `./gradlew check > /tmp/se-task2.log 2>&1; tail -5 /tmp/se-task2.log`
Expected: `BUILD SUCCESSFUL`.

Run the AGENTS.md Python source-hygiene check. Expected: no output.

```bash
git add -A jasper-app/src
git commit -m "feat(palette): rebuild the command palette as IntelliJ Search Everywhere

An All tab (Cmd/Ctrl+K) groups every scope's matches with a More row; each scope keeps its own
tab; the > picker, aliases and Cmd+1-5 row shortcuts are gone; colours come from the theme.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Retire the palette's Jasper keys

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java`

- [ ] **Step 1: Write the failing assertion**

In `ChromeKeysTest.flatLafsOwnKeysAreEnoughForEveryChromeKey`, replace

```java
        assertThat(defaults.getColor("Jasper.paletteSelectionBackground")).isEqualTo(new Color(0x2675bf));
```

with

```java
        assertThat(ChromeKeys.KEYS).noneMatch(key -> key.startsWith("Jasper.palette"));
        assertThat(defaults.getColor("Jasper.paletteSelectionBackground")).as("the palette reads the theme's own keys").isNull();
```

Run: `./gradlew :jasper-app:test --tests '*ChromeKeysTest'`
Expected: FAIL; `KEYS` still contains `Jasper.paletteBackground`.

- [ ] **Step 2: Remove the rows**

In `ChromeKeys.java`:
- Delete the seven entries from `KEYS`: `"Jasper.paletteBackground", "Jasper.paletteForeground", "Jasper.paletteMutedForeground", "Jasper.paletteBorder", "Jasper.paletteAccent", "Jasper.paletteSelectionBackground", "Jasper.paletteSelectionForeground"`.
- Delete the comment `// Until the Search Everywhere palette reads the theme's own keys (part 2b).` and the seven `put(defaults, added, "Jasper.palette…", …)` lines after it.

- [ ] **Step 3: Verify and commit**

Run: `./gradlew :jasper-app:test --tests '*ChromeKeysTest' --tests '*ThemeManagerTest'`
Expected: PASS.

Run: `git grep -n 'Jasper\.palette' -- jasper-app/src plugins`
Expected: no output.

```bash
git add jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java
git commit -m "refactor(appearance): drop the palette's derived Jasper keys

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Documentation, the design matrix and status

**Files:**
- Modify: `docs/command-palette.md`, `docs/configuration.md`, `docs/sdk-architecture.md`, `docs/design/command-palette/README.md`, `docs/STATUS.md`
- Regenerate: `docs/design/command-palette/*.png` (headless preview task)

- [ ] **Step 1: Rewrite the palette guide's opening**

In `docs/command-palette.md`, replace the two opening paragraphs, from "The palette holds more than one kind of searchable thing" through "plain digits are search text.", with:

```markdown
The palette works like IntelliJ's Search Everywhere. A row of tabs sits above the search field:
**All** first, then one tab per **scope** (Commands, History, Snippets, SSH and any a plugin adds).
Cmd+K on macOS or Ctrl+K on Windows and Linux (or View → Command Palette) opens **All**, which
searches every scope at once and groups the matches under each scope's name, at most
`palette.max_results` per scope; when a scope has more, a "More in <Scope>…" row opens that scope's
tab with the query kept. Vault's secrets stay out of All; its tab searches them. Each scope's own
shortcut opens its tab: Cmd+R (Ctrl+Shift+R elsewhere) History, Cmd+J (Ctrl+Shift+J) Snippets.
Pressing the shortcut of the tab already showing closes the palette. Tab and Shift+Tab move between
tabs, keeping the query; clicking a tab does the same.

Rows are one line: an icon, the title, a grey detail and a right-aligned tag. Up and Down move
between rows, skipping the section headers. Enter runs a row's first verb, Cmd+Enter (Ctrl+Enter
elsewhere) its second and Shift+Enter its third. The hint bar under the list shows the selected
row's detail and its other verbs with their keys; click one to run it. Escape leaves an open step,
keeping the list underneath, and otherwise closes the palette, as does an outside click. Plain
digits are search text. Every colour comes from the installed theme.
```

Then:
- In the scope example further down, replace `"Search fake feature, or > to switch scope"` with `"Search fake feature"`.
- Replace ``scopes retains that target. `PaletteController` owns query, picker and step state.`` with ``tabs retains that target. `PaletteController` owns the open tab, query and step state.``.
- In the last paragraph, replace `verification, including scopes, the picker, History rows, the Snippets list and` with `verification, including the All tab, scope tabs, History rows, the Snippets list and`, and replace `its fill-in and name steps. Native focus, input methods, accessibility, chip` with `its fill-in and name steps. Native focus, input methods, accessibility, tab`.

- [ ] **Step 2: Update configuration, SDK and design docs**

In `docs/configuration.md`, under `### Palette`, replace the paragraph from "`palette.max_results` is the hard cap on rows" through "Changes apply live, including to an open palette." with:

```markdown
`palette.max_results` is the cap on rows each scope contributes: a scope's tab lists at most that
many, and the All tab shows at most that many per scope, with a "More in <Scope>…" row when there
are more. Changes apply live, including to an open palette.
```

Then:
- Under `## Command palette shortcuts`, replace

  ```markdown
  Ctrl+Shift+J), rebindable by their quoted ids. While
  the palette is open, Cmd/Ctrl+1–5 runs the corresponding visible result; plain digits
  continue to edit the search field.
  ```

  with

  ```markdown
  Ctrl+Shift+J), rebindable by their quoted ids. Cmd+K opens the All tab. While the palette is
  open, Tab and Shift+Tab switch tabs; plain digits edit the search field.
  ```
- Under `### Snippets`, replace `` (Cmd+J on macOS, Ctrl+Shift+J elsewhere; `>snip` from the picker)`` with `` (Cmd+J on macOS, Ctrl+Shift+J elsewhere, or its tab in the palette)``.

In `docs/sdk-architecture.md`, under `## Palette scopes`, replace

```markdown
the plugin's row kept as the app row's token so it comes back unchanged. `HostedContext.notices()`
```

with

```markdown
the plugin's row kept as the app row's token so it comes back unchanged. Every scope gets a tab;
`ScopeSpec.inAll` (SDK 0.8.0, `withInAll(false)` to opt out) decides whether the All tab searches it
too, and `HostedPalette` carries it as `PaletteScope.inAll()`. `HostedContext.notices()`
```

Regenerate the palette design matrix headlessly. This creates no native window:

```bash
./gradlew :jasper-app:commandPalettePreview --args="$PWD/docs/design/command-palette"
git rm -q docs/design/command-palette/scope-picker-*.png
```

In `docs/design/command-palette/README.md`, replace the table row that begins `| Scope picker (`>`) |` with:

```markdown
| Commands tab, `pane` | [1×](commands-tab-dark-900x600-1x.png) / [2×](commands-tab-dark-900x600-2x.png) | [1×](commands-tab-light-900x600-1x.png) / [2×](commands-tab-light-900x600-2x.png) |
```

and add this paragraph directly under the `# Command palette verification` heading:

```markdown
Regenerated on 2026-09-25 for the Search Everywhere palette (All and scope tabs, IntelliJ Light and
Jasper Dark). The Recents, `pane`, No match and Long labels states now render the All tab. The
History and Snippets images, and the notes about the chip, the `>` picker and numbered badges
further down, record the earlier palette.
```

- [ ] **Step 3: Add the STATUS entry**

In `docs/STATUS.md`, under `## Current state — 2026-09-23`, after the "Theme engine" bullet, add:

```markdown
- **Search Everywhere palette** (branch `claude/theme-engine`, plan
  `docs/superpowers/plans/2026-09-25-jasper-search-everywhere.md`):
  - **Tabs:** All first (Cmd/Ctrl+K), then one tab per scope.
  - **All tab:** groups each participating scope's matches, capped per scope, with a "More in…"
    row. SDK 0.8.0 adds `ScopeSpec.withInAll`; Vault opts out.
  - **Rows:** one line, with a hint bar showing the other verbs. Tab and Shift+Tab switch tabs.
  - **Colours** come from the theme's `SearchEverywhere.*`, `List.*` and `Popup.borderColor`.
  - **Removed:** the `>` picker, scope aliases, the Cmd+1–5 row shortcuts and the `Jasper.palette*`
    keys.
  - **Planning decisions:** listed in the plan header.
  - **Not done:** visual acceptance (user-run), merge and push.
```

- [ ] **Step 4: Verify and commit**

Run: `./gradlew check > /tmp/se-task4.log 2>&1; tail -5 /tmp/se-task4.log`
Expected: `BUILD SUCCESSFUL`. `AppDocumentationTest` resolves every link, including the new `commands-tab-*` images.

Run: `git grep -nE '> to switch scope|>snip|scope picker|Cmd/Ctrl\+1–5 runs' -- 'docs/*.md' ':!docs/superpowers/**' ':!docs/STATUS.md' ':!docs/design/**'`
Expected: no output.

```bash
git add -A docs
git commit -m "docs(palette): document the Search Everywhere palette

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
