# Editor colour scheme setting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status:** executed natively in one session (superpowers:executing-plans) with one whole-branch review at the end,
> instead of per-task subagents: the user asked for no questions, and the four tasks chain through one new SDK method.

**Goal:** The Editor gets a **Color scheme** setting: follow the workspace's editor scheme (today's behaviour), or use
one installed theme's Dark or Light editor scheme whatever the appearance.

**Architecture:** The SDK's `Appearance` lists every installed theme's editor schemes (`editorSchemes()`, a new
`EditorScheme` record). The app builds the list from the `ThemeLibrary` the `ThemeController` holds and announces a
library change as a workspace change; the testkit's fake takes a list from the test. The Editor declares
`color_scheme`, shows it as a dropdown in a new Colors section of its Settings page, and resolves it each time it
applies its appearance, falling back to the workspace scheme when the saved scheme is not installed.

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea, Jasper SDK 0.21.0 (unreleased), JUnit 5 + AssertJ.

**Spec:** this plan carries the design; the user's request (2026-10-23): "a drop down for color scheme in the settings
for the editor that lets me pick either "Follow Workspace Scheme" or a list of all schemes we have for the UI (light or
dark explicitly specified)".

## Global Constraints

- Setting key `color_scheme` in `<home>/plugins/dev.jasper.editor/dev.jasper.editor.toml`; values `"workspace"` (default) or `<theme id>/dark` / `<theme id>/light`.
- Dropdown title `Color scheme`, section `Colors` (after Font); first option label exactly `Follow Workspace Scheme`; the others `<theme label> (Dark)` / `<theme label> (Light)`, in the library's theme order, Dark before Light.
- A saved scheme that is not installed is listed as `<id> (not found)` and the Editor shows the workspace scheme.
- A fixed scheme does not follow the Light/Dark appearance. The Markdown preview keeps the workspace's colours (its code blocks too).
- SDK additions carry `@since 0.21.0`; no SDK version bump (0.21.0 is unreleased). Regenerate `docs/sdk-reference.html` with `./gradlew sdkReference`.
- Jasper is an MVP: no migration, no compatibility shims.
- No GUI launches; commits on `claude/editor-color-scheme`, each ending with the Home Depot attribution and `Co-Authored-By: Claude <noreply@anthropic.com>`.

## Review Focus

- A theme removed or re-imported while a fixed scheme is chosen: open tabs fall back or recolour at once (Task 2 test `libraryChangeIsAWorkspaceChange`, Task 3 test `missingSchemeFallsBackToWorkspace`).
- A theme with only one variant (an imported Light-only theme): only that variant is listed (Task 2 test `editorSchemesListEachInstalledVariant`).
- A hand-edited bad value (`"mocha"`, `"x/dim"`): Settings reports it and the default applies (Task 3 test `colorSchemeRejectsMalformedValues`).
- A translucent workspace with a fixed scheme: the glass takes the chosen scheme's background (Task 3 test `fixedSchemeAppliesToGlass`).
- Switching back to Follow Workspace Scheme re-applies the workspace palette without a theme change (Task 3 test `settingChangeReappliesScheme`).

---

### Task 1: SDK `EditorScheme` and `Appearance.editorSchemes()`, testkit fake, contract

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/EditorScheme.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Appearance.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`, `FakePluginContext.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeUiTest.java`
- Docs: `docs/sdk-reference.html` (generated)

**Interfaces:**
- Produces: `record EditorScheme(String id, String name, Variant variant, EditorPalette editor, SyntaxPalette syntax)`; the
  constructor requires every component non-null, a non-blank name, and `id` matching `[a-z0-9]+(-[a-z0-9]+)*/(dark|light)`
  whose suffix agrees with `variant`. `Appearance.editorSchemes()` returns `List<EditorScheme>` (abstract, UI thread only,
  changes reported through `onWorkspaceChanged`). `FakePluginHost.setEditorSchemes(List<EditorScheme>)` queues the list for
  the next `flush()`, which announces a workspace change when it differs; the fake starts with an empty list.

- [ ] **Step 1: Failing tests.** `UiValuesTest.editorSchemeChecksItsId`: `new EditorScheme("jasper-meadow/dark", "Jasper Meadow", DARK, e, s)` is accepted; `"jasper-meadow/light"` with `DARK`, `"Meadow/dark"`, `"jasper-meadow"`, and a blank name throw `IllegalArgumentException`. `FakeUiTest.editorSchemesArePublishedOnFlush`: empty at first; after `setEditorSchemes(List.of(dark))` and `flush()` the list is `[dark]` and one workspace handler ran once; the same list again runs none.
- [ ] **Step 2: Run** `./gradlew :jasper-sdk:test --tests '*UiValuesTest' :jasper-sdk-testkit:test --tests '*FakeUiTest'`; expect compile failures.
- [ ] **Step 3: Implement** the record, the `Appearance` method (Javadoc: ids, order, `@since 0.21.0`), the fake's storage beside `Workspace`, and `editorSchemes()` in `FakePluginContext.appearance()` with `host.requireAppearanceThread()`.
- [ ] **Step 4: Contract.** `PluginContractTest.editorSchemesAreUiThreadValuesWithUniqueIds`: on the UI thread the ids are distinct and each variant agrees with its id; off it `editorSchemes()` throws `IllegalStateException`. (Both runtimes; the app's list is the built-ins.)
- [ ] **Step 5: Run** `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check` after `./gradlew sdkReference`; expect PASS (the app's `AppContractTest` fails to compile until Task 2, so do not run `:jasper-app` here).
- [ ] **Step 6: Commit** "SDK: Appearance lists every installed theme's editor schemes".

### Task 2: App supplies the installed editor schemes

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedPalettes.java`, `HostedUi.java`, `HostedContext.java`, `PluginHost.java`, `PluginRuntime.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (beside `plugins.appearance(...)`)
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java`, `HostedPalettesTest.java`

**Interfaces:**
- Consumes: Task 1's `EditorScheme`, `Appearance.editorSchemes()`.
- Produces: `static List<EditorScheme> HostedPalettes.editorSchemes(ThemeLibrary library)`; `PluginRuntime.themes(Supplier<ThemeLibrary>)` (before start, like `animations`); `PluginHost.themes` field defaulting to `ThemeLibrary::bundledOnly`; `HostedUi.themes(Supplier<ThemeLibrary>)` setter.

- [ ] **Step 1: Failing tests.** `HostedPalettesTest.editorSchemesListEachInstalledVariant`: for `ThemeLibrary.bundledOnly()` the ids are, in order, each theme's `<id>/dark` then `<id>/light` for the variants it has, names are `library.label(theme)`, and `jasper-meadow/dark`'s editor palette equals `HostedPalettes.editor(theme.generated(true))`. `HostedUiTest.libraryChangeIsAWorkspaceChange`: with a mutable library supplier, publishing an equal `ResolvedTheme` after swapping the library runs the workspace handler once; without a swap it runs none.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*HostedPalettesTest' --tests '*HostedUiTest'`; expect FAIL.
- [ ] **Step 3: Implement.** HostedUi caches the converted list by library identity, remembers the last library it saw, and in its appearance subscription announces a workspace change when the library differs even if the `ResolvedTheme` is equal. JasperApplication passes `themes::library`.
- [ ] **Step 4: Run** `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*'`; expect PASS (including `AppContractTest`).
- [ ] **Step 5: Commit** "App: plugins read every installed theme's editor schemes".

### Task 3: Editor `color_scheme` setting, dropdown and resolution

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`, `EditorSettingsPanel.java`, `DocumentArea.java`
- Modify: `plugins/editor/src/main/resources/settings.toml`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java`, `EditorSettingsPanelTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`

**Interfaces:**
- Consumes: `Appearance.editorSchemes()`, `FakePluginHost.setEditorSchemes`.
- Produces: `EditorSettings.colorScheme()` (String, a new last record component); `EditorSettings.Declared.WORKSPACE = "workspace"`; `static Optional<EditorScheme> EditorSettings.scheme(List<EditorScheme> installed, String chosen)` (empty for `workspace` or a missing id).

- [ ] **Step 1: Failing tests.** `EditorSettingsTest.colorSchemeRejectsMalformedValues` (default `workspace`; `catppuccin-mocha/dark` accepted; `mocha`, `x/dim`, `X/dark` rejected). `EditorSettingsPanelTest.colorSchemeDropdownListsWorkspaceThenEverySchemeVariant` (options `workspace`/`Follow Workspace Scheme`, then `a/dark`/`A (Dark)`, `a/light`/`A (Light)`; selecting stages the id; Reset stages `workspace`; a saved `gone/dark` adds `gone/dark (not found)`). `EditorPluginTest`: `fixedSchemeIgnoresTheWorkspace` (an open tab's area background is the chosen scheme's background after a workspace change to another palette), `missingSchemeFallsBackToWorkspace`, `settingChangeReappliesScheme`, `fixedSchemeAppliesToGlass` (opacity 0.5: the DocumentArea's glass colour is the chosen scheme's background).
- [ ] **Step 2: Run** `./gradlew :jasper-plugin-editor:test --tests '*EditorSettings*' --tests '*EditorPluginTest'`; expect FAIL.
- [ ] **Step 3: Implement.** Declare `color_scheme` as `SettingKind.text` with the pattern above, label `Color scheme`, description `The Editor's colours: the workspace's editor scheme, or one theme's Dark or Light scheme whatever the appearance.`, section `Colors`, keywords `editor`, `color`, `colour`, `scheme`, `theme`. The panel draws it as a custom dropdown row (as `choiceRow`) in a `Colors` section after Font. `DocumentArea.refreshAppearance` picks the palettes with `EditorSettings.scheme(appearance.editorSchemes(), settings.colorScheme())`, else the workspace's; the preview style keeps `workspacePalette()` and `syntaxPalette()`. Add the commented entry to `settings.toml` after `follow_terminal_font`.
- [ ] **Step 4: Run** `./gradlew :jasper-plugin-editor:check`; expect PASS.
- [ ] **Step 5: Commit** "Editor: a Color scheme setting picks the workspace's or one theme's editor scheme".

### Task 4: Documentation

**Files:** `docs/editor.md` (Theme colours and the settings list), `docs/configuration.md` (the Settings page table's Tools › Editor row, if it lists sections), `docs/plugin-apis.md` / `docs/sdk-architecture.md` only where `Appearance`'s palettes are enumerated, `docs/STATUS.md` (one entry).

- [ ] **Step 1:** Rewrite `docs/editor.md`'s Theme colours paragraph to describe `color_scheme`; drop "The terminal scheme (`terminal.scheme`) does not change the Editor." only if it no longer reads naturally (it stays true).
- [ ] **Step 2:** Run `./gradlew :jasper-plugin-editor:check :jasper-app:test --tests '*Docs*'` (tests that read docs); expect PASS.
- [ ] **Step 3: Commit** "Docs: the Editor's Color scheme setting".
