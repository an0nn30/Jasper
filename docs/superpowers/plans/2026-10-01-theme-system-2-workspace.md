# Theme System Plan 2: Workspace Zone Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Executed and independently reviewed on `codex/theme-system-plans` through the final fix wave;
user-run GUI acceptance remains pending. Baseline `main@5fc76c7d`.
Tasks 1–7 have independent task reviews. Plan 3 (generic plugin pane styling) is next; plans 4–5 remain future work.
Serialized `./gradlew check` passed with 7,769 tests, zero failures/errors and six skips; all architecture checks,
`git diff --check` and Java source hygiene passed. Final fix-wave per-module XML totals are in `.superpowers/sdd/2026-10-01-theme-system-2-workspace/final-fix-report.md`.
The final review found one Important omission from remaining-work decision 6: selected native/Alacritty name
collisions were silent. Native precedence is retained; a source-aware warning now identifies the shadowed Alacritty
theme and chosen native theme, including case-insensitive collisions. Non-colliding selections remain warning-free.
The full-branch independent review covered `5fc76c7d..ec030a72`; scoped independent re-review approved
`ec030a72..1b1cb027` with no actionable findings. Fix commit `1a389653` adds the diagnostic and regressions;
`1b1cb027` removes one inherited trailing test-file blank line found by the branch-wide diff check. The
bundled-source collision path lacks a dedicated test; its fallback was reviewed, while user-source exact/mixed-case
collisions and a non-collision control are tested. This is a coverage limit, not an open review finding.

**Execution deviations and rulings:** Legacy `terminal.colors` and `ui.theme.terminal` are path-only aliases:
only values read from those paths convert `light`/`dark` to native Jasper names; canonical native names remain literal.
Legacy `dark` therefore uses Jasper Dark's native `#191c20` terminal background, replacing the retired `#282a36`
hardcoded palette. The retained menu identity moved to `WorkspaceThemeChoice` because the old `TerminalColors`
adapter mislabeled native Light; cost if wrong: revert the earlier menu refactoring, but retaining ambiguous identity
would be incorrect. Complete-theme workspace values are materialized through an isolated private LAF without changing
the installed chrome LAF; one EDT-owned generated cache is pruned on catalog refresh while retaining active choices.
`UiTemplate` did not need edits; a pure inverse map covers all 48 tokens. `WindowContent` owns terminal-tab zone lifetime,
and the rendered divider test lives in `WorkspaceThemeTest`. A JDK foreground client property supports paint-time KEY
icon selection. The native SDK boundary uses `PluginRuntime.appearance` to supply resolved themes before start;
workspace defaults are native Color/Boolean keys, with SDK values mapped only in app.plugins. Fake palette access
accepts its creating test thread and Swing EDT but rejects other workers; cost if wrong: narrow testkit threading
and update Editor tests, while production remains UI-thread confined. Fake workspace publication is queued with its
notifications, so getters expose the last flushed snapshot and callback values stay coherent; cost if wrong: fake
clients expecting pre-flush reads must flush or the scheduling contract must change. The existing fake method is
`setVariant`, not the brief's `setTheme`; no alias was added. SDK 0.12.0 and seven bundled manifest ranges landed
without changing plugin release versions. Editor maps all 16 roles to RSyntax token types, updates same-darkness
changes and preserves documents, edits and undo state.

**Goal:** Make terminal panes, their tab strip, dividers, find UI, icons and Editor follow a complete workspace theme,
independently of chrome, with the SDK palettes that plugin restyling will consume.

**Architecture:** `appearance` publishes two immutable generated values; only chrome installs a LAF. `workspace`
uses app-native tokens, `platform` reads JDK zone properties and `plugins` alone maps native values to SDK types.
Keep the existing theme pages operational through this plan and plan 3.

**Tech Stack:** JBR 25, Gradle wrapper, FlatLaf 3.7, Swing, tomlj 1.1.1, SDK 0.12.0, existing RSyntaxTextArea dependency.

**Spec:** `docs/superpowers/specs/2026-10-01-jasper-theme-system-design.md`, sections 1, 2.3, 4, 5 and 8–9.
**Companion:** `2026-10-01-theme-system-remaining-work.md` defines the audited baseline and cross-plan decisions.

## Global Constraints

- No GUI, login shell, `:jasper-app:run` or benchmark. Headless JUnit only; manual acceptance belongs to the user.
- One installed FlatLaf LAF, always chrome. `match` means the exact same `GeneratedTheme` object in both slots.
- Canonical `ui.theme.workspace`, default `match`; read aliases `terminal.colors` and `ui.theme.terminal`.
- SDK 0.12.0; all seven bundled plugin manifests use `>=0.12.0, <0.13` when the host API lands.
- SDK/testkit remain JDK-only. Only `dev.jasper.app.plugins` may reference SDK types from the app.
- Preserve explicit colors, author identity, named colors, icon data and plan-1 parity/regression tests.
- Keep configuration parsing/scanning Swing-free; private LAF materialization, publication and Swing changes are EDT-only.
- Keep `GeneratedTheme.author`, `icons`, 48 tokens and `ui_complete`. Do not resurrect the removed `Theme.LIGHT` name.
- No new interface with only one implementation. Use records/classes and existing functional types for injection.
- Work on a branch; one commit per task, each ending `Co-Authored-By: Codex <noreply@openai.com>`.
- Each task: complete the red/green steps, inspect failures, run its targeted test plus `./gradlew check`, then commit.
- No raw control/private-use/surrogate characters in Java. Record deviations in this banner and `docs/STATUS.md`.

## Review Focus

1. Old and new keys coexist in either order: canonical wins and explicit saving removes aliases (Task 1).
2. A converted UI theme has a different console palette: workspace controls must reflect resolved UI tokens without
   installing its LAF or changing chrome defaults (Task 2).
3. Same-darkness workspace changes still update listeners; failed chrome installation publishes neither slot (Task 3).
4. Moving a retained icon/component between workspace, sidebar and popup changes its zone without recreating it (Tasks 4–5).
5. Editor retheming preserves document, caret, selection, undo, font and dirty state; removed listeners stay removed (Tasks 6–7).

## File map

All app Java paths below are relative to `jasper-app/src/main/java/dev/jasper/app/`; test paths mirror them under
`jasper-app/src/test/java/dev/jasper/app/`. SDK roots are named in the relevant task.

| Files | Responsibility |
|---|---|
| `config/WorkspaceThemeChoice.java` (new), `CoreSettings`, `ConfigSnapshot`, `ConfigLoader`, `ConfigMigration` | Canonical config and old-key normalization |
| `settings/SchemaReader.java` | Path-specific alias-value conversion, without a config dependency |
| `appearance/WorkspaceValues.java` (new), `ThemeManager`, `ThemeGenerator`, `UiTemplate` | Materialize effective workspace tokens |
| `terminalthemes/AlacrittyConversion.java` (new) | Transitional palette-to-native conversion, moved in plan 4 |
| `appearance/ResolvedTheme`, `ThemeState`, `ThemeController` | Atomic two-slot state and notifications |
| `application/ConfigurationController`, `JasperApplication` | Catalog resolution, diagnostic provenance, plugin wiring |
| `workspace/WorkspaceTheme.java` (new), `WindowContent`, `WindowTabs`, `TerminalPane`, `FindBar`, `SplitDividerBorder` | Host surfaces and zone lifetime |
| `platform/AppIcons`, `platform/ThemeZone.java` (new) | Paint-time artwork selection using JDK properties |
| `plugins/HostedUi`, `HostedContext`, `PluginHost`, `PluginRuntime` | SDK adapter and subscriptions |
| SDK `ui/WorkspacePalette`, `ui/SyntaxPalette`, `ui/SyntaxRole`, `ui/SyntaxStyle`, `ui/Appearance`, `ui/EditorPalette` | Plugin value API |
| Editor `ui/EditorTheme`, `ui/EditorTab`, `ui/EditorWindow`, `EditorPlugin` | Semantic syntax application |

## Task 1: One workspace setting, with compatible reads and safe saves

**Files:** the config/settings files in the map; `settingsui/CorePages`, `Editors`; `workspace/WindowContent`,
`WindowChrome`; `terminalthemes/AlacrittyThemesPage`; tests `config/ConfigLoaderTest`, `ConfigMigrationTest`,
`ConfigServiceTest`, `settings/SchemaReaderTest`, `settingsui/TerminalColorsEditorTest` (create if absent).

**Interfaces:**
- Produce `public record WorkspaceThemeChoice(String name)` with `MATCH`, `parse(String)`, `boolean matchesChrome()`.
- Produce `CoreSettings.UI_THEME_WORKSPACE: Setting<String>` and `ConfigSnapshot.workspaceTheme(): WorkspaceThemeChoice`.
- Add `SchemaReader(SettingsSchema, String, BiFunction<List<String>, Object, Object> aliasValue)`; existing constructor
  delegates with `(path, value) -> value`. Apply it only inside `resolveAliases`, before the setting's kind reads the value.
- `ConfigLoader` supplies path-specific legacy workspace and variant transforms; `ConfigMigration` writes the normalized loaded value for a
  transformed alias instead of just moving its old literal. Native current-key values are never transformed as aliases.

- [x] **Step 1: Add failing compatibility tests.** Use actual `ConfigLoader.parse` and `ConfigMigration.plan`:

```java
@Test void workspaceCanonicalKeyWinsOverLegacyInEitherOrder() {
    for (String text : List.of(
            "terminal.colors = 'dark'\nui.theme.workspace = 'Jasper Light'\n",
            "ui.theme.workspace = 'Jasper Light'\nterminal.colors = 'dark'\n")) {
        var parsed = ConfigLoader.parse(Path.of("config.toml"), text, true);
        assertThat(parsed.rejected()).isFalse();
        assertThat(parsed.snapshot().workspaceTheme().name()).isEqualTo("Jasper Light");
    }
}
@Test void oldVariantLightChoosesJaspersLightTheme() {
    var parsed = ConfigLoader.parse(Path.of("config.toml"), "ui.theme.variant = 'light'\n", true);
    assertThat(parsed.snapshot().theme()).isEqualTo("Jasper Light");
    var migrated = ConfigMigration.plan("ui.theme.variant = 'light'\n", parsed, id -> false).orElseThrow();
    assertThat(ConfigLoader.parse(Path.of("config.toml"), migrated.text(), true).snapshot().theme())
        .isEqualTo("Jasper Light");
    assertThat(migrated.text()).doesNotContain("variant");
}
@Test void workspaceChoiceReservesOnlyMatch() {
    assertThat(WorkspaceThemeChoice.parse(" Light ").name()).isEqualTo("Light");
    assertThat(WorkspaceThemeChoice.parse("Dark").name()).isEqualTo("Dark");
    assertThat(WorkspaceThemeChoice.parse(" match ").matchesChrome()).isTrue();
    assertThat(WorkspaceThemeChoice.parse("Tokyo Night").name()).isEqualTo("Tokyo Night");
}
```

Also pin bad setting types as diagnosed/defaulted without discarding other valid settings, blank workspace as warned/defaulted, canonical `ui.theme.name = "Light"` unchanged,
`ui.theme.terminal` still accepted, a superseded legacy alias removed by saving, and user comments/unrelated keys
surviving migration. Run `./gradlew :jasper-app:test --tests '*ConfigLoaderTest' --tests '*ConfigMigrationTest'`:
expect missing API or incorrect old behavior before implementation.

- [x] **Step 2: Implement the value and schema migration.** The complete normalization body is:

```java
public record WorkspaceThemeChoice(String name) {
    public static final WorkspaceThemeChoice MATCH = new WorkspaceThemeChoice("match");
    public WorkspaceThemeChoice {
        name = java.util.Objects.requireNonNull(name).strip();
        if (name.isEmpty()) throw new IllegalArgumentException("A workspace choice needs a name");
    }
    public static WorkspaceThemeChoice parse(String value) {
        String name = java.util.Objects.requireNonNull(value).strip();
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "match" -> MATCH;
            default -> new WorkspaceThemeChoice(name);
        };
    }
    public boolean matchesChrome() { return name.equalsIgnoreCase("match"); }
}
```

Add `ConfigLoaderTest.legacyWorkspaceAliasesNormalizeLightAndDark`: each of terminal.colors and ui.theme.terminal
maps light/dark to Jasper Light/Dark, while canonical ui.theme.workspace = "Light" stays "Light". Apply that conversion
in the same path-specific SchemaReader callback used for ui.theme.variant; WorkspaceThemeChoice itself reserves only
match. This prevents compatibility handling from making a native theme named Light impossible to select.

Register the new setting once, with both old aliases. Remove `TERMINAL_COLORS` from the schema and migrate callers;
if a temporary constant is needed for an old page, it must reference the same Setting object, not register a second
setting. Add `ui.theme.variant` as an alias of UI_THEME_NAME and transform strings `light`/`dark` on that legacy path to
`Jasper Light`/`Jasper Dark`; an unsupported old variant warns and uses the current default. `ConfigMigration` uses
`SettingWriter` with `parsed.values().get(setting)` for transformed aliases after removing their old path, preserving
canonical precedence and backup behavior. Do not use string replacement on TOML.

- [x] **Step 3: Keep transitional UI coherent.** The old terminal selector and Alacritty Use buttons write
`UI_THEME_WORKSPACE`. Relabel explanatory text to say it affects workspace, keep the match checkbox behavior, and
include native theme names as well as Alacritty names. Both old pages still open. Migrate menu save paths and update
`config.example.toml`, generated comments and configuration docs with the new key and aliases.
- [x] **Step 4: Run the named tests plus ConfigService/Settings tests and `./gradlew check`; expect exit 0.**
- [x] **Step 5: Commit** `feat(config): add workspace theme setting and legacy aliases` with the required trailer.

## Task 2: Generate accurate workspace values and transitional Alacritty themes

**Files:** `appearance/WorkspaceValues` (new), `ThemeManager`, `ThemeGenerator`, `UiTemplate`,
`terminalthemes/AlacrittyConversion` (new), `TerminalThemeCatalog`; tests `appearance/WorkspaceValuesTest` and
`terminalthemes/AlacrittyConversionTest` (new), existing `PluginThemeParityTest`.

**Interfaces:**
- `public static GeneratedTheme ThemeManager.generated(Theme theme)` becomes EDT-checked and publicly usable by
  app owners; native themes reuse the existing cached result.
- `static GeneratedTheme WorkspaceValues.resolve(ThemeDocument document, GeneratedTheme generated)` is EDT-only.
  For `ui_complete`, obtain an uninstalled `IntelliJTheme.createLaf` with source author/UI/icons, apply APP_DEFAULTS,
  call its `getDefaults()`, restore skipped colors locally, derive ChromeKeys locally, then invert effective keys.
- `static GeneratedTheme ThemeGenerator.withWorkspaceTokens(ThemeDocument document, Map<Token,Color> tokens)` computes
  final tokens/syntax using the supplied effective base plus the document's explicit overrides; preserve the original
  generated terminal palette, UI keys, icons, author and name.
- `public static Theme AlacrittyConversion.convert(TerminalTheme theme)` produces a complete native seed document,
  `ui_complete = false`, stable `legacy-alacritty:` id; no Swing. Use the existing palette exactly, derive accent
  from ANSI blue to at least 3:1 against background, using black/white if blue cannot reach it.
- Add transitional `Theme.LegacyPalette(Optional<Path> file, ThemeDocument document) implements Theme.Source` and
  permit it in the sealed source list; the file is absent for bundled legacy palettes. Construct Theme with its public
  canonical constructor (Theme.of is package-private). Remove this source variant in plan 4. Keep appearance free of
  terminalthemes types. Make the pure native generator entry point public with JavaDoc for converter reuse.

- [x] **Step 1: Write the mandatory private-defaults isolation spike as a permanent headless test.** Capture
`UIManager.getLookAndFeel()` and its color defaults, generate a different complete theme, and compare both afterward.
Use two explicit, different colors for console background and Panel.background; assert workspace SURFACE equals the
latter and terminal background equals the former. Add this pure-value test:

```java
@Test void alacrittyConversionKeepsTerminalColorsAndDerivesTheRest() {
    var palette = dev.jasper.terminal.config.Palette.jasperDark();
    var source = new TerminalTheme("Fixture", palette, java.util.Optional.empty());
    Theme converted = AlacrittyConversion.convert(source);
    assertThat(converted.palette()).isEqualTo(palette);
    assertThat(converted.source().document().uiComplete()).contains(false);
    assertThat(converted.source().document().seeds()).containsKeys(Seed.BACKGROUND, Seed.FOREGROUND, Seed.ACCENT);
}
```

The decisive additional assertions in the EDT test are:

```java
assertThat(UIManager.getLookAndFeel()).isSameAs(chromeLaf);
assertThat(UIManager.getColor("Panel.background")).isEqualTo(chromeBackground);
assertThat(workspace.tokens().get(Token.SURFACE)).isEqualTo(new Color(0x123456));
assertThat(workspace.terminal().background()).isEqualTo(new Color(0x010203));
assertThat(workspace.uiKeys()).isEqualTo(original.uiKeys());
```

Run the two new tests first; expect missing conversion/materialization behavior. No LAF setup or window launch in the
materializer. If `getDefaults()` mutates global state, do not temporarily install/restore a LAF; use deterministic
pure inverse mapping with the theme's resolved explicit keys and document the tested fallback keys before continuing.

- [x] **Step 2: Implement the inverse mapping.** Use these effective defaults, with explicit token values winning:

| Token | Effective key (first available) |
|---|---|
| SURFACE / TEXT / TEXT_MUTED / TEXT_DISABLED | Panel.background / Label.foreground / Jasper.mutedForeground / Label.disabledForeground |
| TITLE_BAR / STATUS_BAR / SIDEBAR | Jasper.titleBackground / StatusBar.background / ToolWindow.background |
| SURFACE_RAISED / SURFACE_SUNKEN / INPUT_BACKGROUND | PopupMenu.background / List.background / TextField.background |
| BORDER / SEPARATOR / DIVIDER / FOCUS_RING | Component.borderColor / Separator.foreground / Jasper.splitDivider / Component.focusColor |
| SELECTION / SELECTION_TEXT / SELECTION_INACTIVE | List.selectionBackground / List.selectionForeground / List.selectionInactiveBackground |
| TEXT_SELECTION / TEXT_SELECTION_TEXT | TextField.selectionBackground / TextField.selectionForeground |
| TAB_BAR / TAB_ACTIVE / TAB_ACTIVE_TEXT / TAB_ACTIVE_BORDER | TabbedPane.background / Jasper.tabSelectedBackground / Jasper.tabSelectedForeground / Jasper.tabSelectedBorder |
| TAB_HOVER / TAB_UNDERLINE / TAB_UNDERLINE_INACTIVE | Jasper.tabHoverBackground / Jasper.tabUnderline / Jasper.tabUnderlineInactive |
| BUTTON_BACKGROUND / BUTTON_BORDER / BUTTON_TEXT / BUTTON_DEFAULT / TEXT_ON_ACCENT | Button.background / Button.borderColor / Button.foreground / Button.default.background / Button.default.foreground |
| HOVER / PRESSED / SCROLLBAR_THUMB / SCROLLBAR_TRACK | Button.hoverBackground / Button.pressedBackground / ScrollBar.thumb / ScrollBar.track |
| ACCENT / LINK | Component.accentColor / Component.linkColor |
| SUCCESS / WARNING / ERROR / ERROR_BACKGROUND | Jasper.configSuccessForeground / Jasper.configWarningForeground / Jasper.configErrorForeground / Jasper.findErrorBackground |
| EDITOR_* | Corresponding Jasper.editor* keys already derived by ChromeKeys |

Missing effective keys retain generator values; do not replace a present explicit token with a fallback. Preserve
alpha. Extract the key table into package-private `WorkspaceTokenKeys` for this step; plan 4 makes its pure inverse mapping
public for conversion without invoking this EDT materializer. Recompute derived syntax against the resulting editor background; explicit syntax styles survive. Avoid an
unbounded new cache: reuse ThemeManager's theme cache, and remove obsolete user-theme entries on catalog refresh.

- [x] **Step 3: Bridge the transitional catalog from application composition.** Do not make `appearance` depend on
`terminalthemes` (which already depends on appearance). `ConfigurationController` asks the native catalog first,
then converts a named terminal theme only when no native name matches. Preserve file problems. Keep native theme
identity on unchanged scans and reuse unchanged converted values, so reloads do not spuriously notify subscribers.
- [x] **Step 4: Run isolation, conversion and all appearance parity tests, then `./gradlew check`.**
- [x] **Step 5: Commit** `feat(appearance): materialize workspace tokens and convert legacy palettes`.

## Task 3: Publish two slots atomically and implement missing-theme fallback

**Files:** `appearance/ResolvedTheme`, `ThemeState`, `ThemeController`; `application/ConfigurationController`,
`JasperApplication`; tests `appearance/ThemeStateTest`, `workspace/ThemeControllerTest`,
`workspace/ConfigurationControllerTest`.

**Interfaces:**

```java
public record ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace) {
    public ResolvedTheme {
        java.util.Objects.requireNonNull(chrome);
        java.util.Objects.requireNonNull(workspace);
    }
    public dev.jasper.terminal.config.Palette palette() { return workspace.terminal(); }
    public boolean matching() { return chrome == workspace; }
}
```

Retain `ThemeController.choice(): Theme` for menu identity. Add `workspaceChoice(): WorkspaceThemeChoice` and
`configure(Theme chrome, WorkspaceThemeChoice choice, Theme workspaceOrNull, UiFontConfig font)`; workspaceOrNull is
null only for match/missing fallback, never a signal to keep a stale workspace. Keep `configure(Theme)` overloads
preserving the configured workspace choice. Retain `subscribe(BiConsumer<ResolvedTheme,Boolean>)`: the boolean means
chrome delegate refresh. Workspace event adapters compare workspace values themselves; same darkness is not equality.

- [x] **Step 1: Add red state/publication tests.** Construct native Theme fixtures via existing test patterns. Pin:

```java
assertThat(controller.current().workspace()).isSameAs(controller.current().chrome()); // default/match
assertThat(controller.current().palette()).isEqualTo(controller.current().workspace().terminal());
assertThat(installs).hasSize(1); // a workspace-only switch did not reinstall chrome
assertThat(notifications).hasSize(1); // a different dark workspace still notifies
assertThat(notifications.getFirst().getValue()).isFalse(); // no delegate refresh
```

Use a failing installer for a combined chrome/workspace/font change and assert old `current()`, choice and font
remain, no listener fires, and the original LAF is restored. Test light chrome → missing chrome chooses Jasper Light;
fresh-start missing chrome chooses Jasper Dark; missing workspace chooses match with a keyed warning. A repaired
file reselects the requested theme on reload. Store requested configuration separately from fallback runtime values.
Run the named state/controller tests; expect old one-palette semantics to fail.

- [x] **Step 2: Implement atomic state.** Materialize both candidates first. For match call the generator once and
assign the same object to both fields. Install chrome only if chrome/font changed; commit both fields only after
installation succeeds. Then notify a snapshot of listeners. Repeated unchanged resolution sends nothing. Retain the
existing rollback and font-offset behavior. Update every use of `theme.chrome()` that needs Theme identity to use
`themes.choice()` instead; use generated `dark()`/`name()` elsewhere.
- [x] **Step 3: Resolve fallback in ConfigurationController.** Read names with the existing case/space rules. Keep
`lastChromeDark` updated only after successful apply; choose built-in constants directly for fallback, so a broken
user shadow cannot replace the safe fallback. An unknown workspace uses the chrome generated object and a warning
at `ui.theme.workspace`, while retaining the user's requested string in config. Do not mask parse diagnostics with
fallback diagnostics. Dispose subscriptions and keep the existing cross-window publication path.
- [x] **Step 4: Run state/configuration/typography/rollback tests and `./gradlew check`.**
- [x] **Step 5: Commit** `feat(appearance): resolve chrome and workspace themes atomically`.

## Task 4: Host workspace surfaces and explicit zone boundaries

**Files:** new `workspace/WorkspaceTheme`; `WindowContent`, `WindowTabs`, `TerminalPane`, `TerminalTab`, `FindBar`,
`SplitDividerBorder`, `WorkspaceRegions`; tests `workspace/WorkspaceThemeTest` (new), `WindowTabsTest`,
`SplitDividerTest`, `FindBarModernTest`, `TerminalColorsTest`.

**Interfaces:** `WorkspaceTheme.apply(JComponent root, ResolvedTheme theme)`,
`WorkspaceTheme.clear(JComponent root)`, `WorkspaceTheme.color(Component component, Token token, String chromeKey)`.
Use `Jasper.zone = "workspace"`, `Jasper.workspaceTheme = GeneratedTheme`, `Jasper.workspace.dark = Boolean` on owned
workspace roots. `match` retains the zone marker for ownership but no overriding style/color payload. Paint lookup
stops at explicit chrome boundaries and popup/window roots; no dependency from platform into this class.

- [x] **Step 1: Add headless rendered-color tests.** Under Jasper Light chrome and Jasper Dark workspace assert the
selected tab, inactive tab, divider, find row, find error state, terminal background and terminal ANSI values match
workspace tokens/palette. Verify title bar, sidebar, status bar, a JPopupMenu and a dialog's content defaults stay light.
Assert `match` uses chrome defaults and removes all workspace overrides. Test mounting a new tab after initial apply,
reparenting hosted content, terminal launch/error labels and existing sessions surviving a theme change.
Run targeted workspace tests; expect the current UIManager-only tab/divider/find reads to fail.
- [x] **Step 2: Replace paint lookups and apply retained state.** Map WindowTabs background to TAB_BAR, active fill/text
and underline to TAB_ACTIVE/TAB_ACTIVE_TEXT/TAB_UNDERLINE, inactive titles to TEXT_MUTED, hover to TAB_HOVER. Split
borders use DIVIDER. Find row uses SURFACE, TEXT and TEXT_MUTED; input uses INPUT_BACKGROUND/TEXT/TEXT_SELECTION;
missing/error input uses ERROR_BACKGROUND. Explicitly style the host-owned find buttons/field now; do not wait for the
plugin restyler. Keep the terminal view on `workspace.terminal()`, not UI tokens. The rounded center's terminal
surround keeps the terminal palette background; tab strip painting uses its workspace token.

Use this fallback lookup shape in the new helper (the ancestor reader is local to WorkspaceTheme):

```java
static Color resolvedColor(GeneratedTheme workspace, Token token, String chromeKey) {
    Color color = workspace == null ? UIManager.getColor(chromeKey) : workspace.tokens().get(token);
    return java.util.Objects.requireNonNull(color, chromeKey);
}
```

Run `updateComponentTreeUI` only on chrome changes, then reapply workspace properties/styles. At `mountContent` mark
hosted content; at `unmountContent` clear host-owned properties. Terminal and hosted roots are workspace; rail hosts
remain chrome. Nested popup trees do not inherit workspace by following their invoker.
- [x] **Step 3: Run workspace rendering/lifetime tests and `./gradlew check`.**
- [x] **Step 4: Commit** `feat(workspace): paint terminal surfaces from workspace tokens`.

## Task 5: Paint-time zone-aware host icons

**Files:** `platform/ThemeZone` (new), `AppIcons`; `workspace/WorkspaceTheme`; tests `platform/ZoneIconsTest` (new),
existing `NamedIconsTest`, `AppIconsTest`, `AppIconsPluginTest`, `IntellijIconsTest`.

**Interfaces:** `public static boolean ThemeZone.workspace(Component component)` and
`public static boolean ThemeZone.dark(Component component, boolean chromeDark)` inspect only JDK client properties.
Treat JPopupMenu and JToolTip as chrome even when logically associated with a workspace invoker. No workspace import.

- [x] **Step 1: Add red image comparisons.** Paint the same retained named icon into a BufferedImage in light chrome,
dark workspace, detached component and popup. Compare the workspace rendering with a fixed-URL dark sibling and the
chrome rendering with the light sibling; move the same JLabel between roots and repeat. Verify disabled icons keep
correct artwork/opacity, dimensions remain 16 logical px (dropdown 8), and custom plugin SVGs remain authored.
- [x] **Step 2: Implement dynamic wrappers.** Cache base catalog ids, not one globally selected variant. In paintIcon,
select explicit URL light/dark artwork using ThemeZone, falling back to the original when a sibling is absent. Keep
proportional fitting and disabled provider behavior. Update `forBackground` to recognize the new wrapper so existing
surface overrides still work. The KEY semantic icon uses the selected zone text color; do not recolor arbitrary SVGs.
- [x] **Step 3: Run icon and workspace tests plus `./gradlew check`.**
- [x] **Step 4: Commit** `feat(icons): choose workspace artwork at paint time`.

## Task 6: SDK 0.12.0 palettes, host adapter and fake contracts

**Files:** SDK `src/main/java/dev/jasper/sdk/ui/{WorkspacePalette,SyntaxPalette,SyntaxRole,SyntaxStyle}.java` (new),
`Appearance`, `EditorPalette`; `dev/jasper/sdk/JasperSdk.java`; testkit `dev/jasper/sdk/testing/{FakePluginHost,FakePluginContext}.java`,
`src/testFixtures/java/dev/jasper/sdk/testing/contract/{ContractHarness,PluginContractTest}.java`; app plugin files in
file map plus `JasperApplication`; all seven `plugins/*/src/main/resources/plugin.toml`; SDK docs and architecture lists.

**Interfaces:**

```java
public record WorkspacePalette(Color background, Color surfaceRaised, Color surfaceSunken, Color text,
        Color textMuted, Color border, Color selection, Color selectionText, Color hover, Color accent,
        Color success, Color warning, Color error) { }
public record SyntaxStyle(Color color, boolean bold, boolean italic) { }
public enum SyntaxRole {
    KEYWORD, TYPE, FUNCTION, VARIABLE, CONSTANT, NUMBER, STRING, COMMENT, DOC_COMMENT,
    ANNOTATION, OPERATOR, PUNCTUATION, TAG, ATTRIBUTE, REGEX, ERROR
}
public record SyntaxPalette(java.util.Map<SyntaxRole, SyntaxStyle> styles) {
    public SyntaxPalette {
        styles = java.util.Map.copyOf(styles);
        for (SyntaxRole role : SyntaxRole.values()) java.util.Objects.requireNonNull(styles.get(role), role.name());
    }
    public SyntaxStyle style(SyntaxRole role) { return styles.get(java.util.Objects.requireNonNull(role)); }
}
```

Add full null validation to both color records, with @param/@return/@since JavaDoc. `WorkspacePalette.current()` reads
host `Jasper.workspace.*` semantic keys, falling back to ordinary Swing keys for custom hosts. The additive
`current(Component)` chooses workspace only inside that component's workspace zone; otherwise uses chrome keys.
This is required by plan 3's rail-panel renderer audit. SDK implementation reads property strings with JDK Swing only.

Appearance additions (complete default bodies):

```java
default Variant workspaceVariant() { return variant(); }
default WorkspacePalette workspacePalette() { return WorkspacePalette.current(); }
default SyntaxPalette syntaxPalette() { return SyntaxPalette.current(); }
default Subscription onWorkspaceChanged(java.util.function.Consumer<Variant> handler) {
    return onChanged(java.util.Objects.requireNonNull(handler));
}
```

Define `SyntaxPalette.current()` to read `Jasper.workspace.syntax.<role>` color/bold/italic keys, falling back to a
complete plain-text palette from EditorPalette.current (comments italic). Hosted and fake Appearance override all new
methods. `editorPalette()` now returns workspace editor colors; keep `variant()/onChanged` about chrome.

- [x] **Step 1: Add failing SDK value and contract tests.** Assert immutability, all 16 roles, missing/null rejection,
custom-host defaults without app keys, component-aware chrome/workspace resolution and default method source
compatibility. Extend the contract harness with `setWorkspace(Variant, WorkspacePalette, EditorPalette, SyntaxPalette)`;
both app harness and fake implement it. In `PluginContractTest`, subscribe, change dark→different dark, assert one
callback and new values visible inside it; chrome handler does not fire. Close subscription and stop plugin, then
change again and assert neither disposed handler runs. Call safe variant getters off UI thread; palette methods retain
UI-thread checks. Compile old custom-host implementation relying only on default additions.
- [x] **Step 2: Implement the host boundary.** Add an app-native appearance input to PluginRuntime/PluginHost using
`Supplier<ResolvedTheme>` and `Function<Consumer<ResolvedTheme>, dev.jasper.app.lifecycle.Subscription>`; app.plugins
adapts to SDK records. Capture immutable snapshots for safe off-thread variant reads. `HostedUi` registers workspace
handlers through existing lifecycle containment; skip the initial replay for change listeners. Compare the whole
workspace generated value, not just darkness. Publish semantic workspace keys after successful apply, separately
from chrome defaults; on match publish the chrome generated palette/syntax as workspace values (only the component
style layer disappears). Reapply these values after a chrome LAF rebuild. Never put an SDK object in
UIManager from outside app.plugins. Add the allowed plugins→appearance package edge, no reverse edge.
- [x] **Step 3: Implement fake parity and versioning.** Add `FakePluginHost.setWorkspace(...)` with the same event and
lifetime rules. Make existing `setTheme(Variant)` update workspace when fake mode is match, preserving old tests. Set
`JasperSdk.VERSION = "0.12.0"`; update every manifest range and version-sensitive fixture/document. Don't change unrelated
plugin release numbers unless the repository packaging convention requires it; document any such bump.
- [x] **Step 4: Run SDK/testkit contract, HostedUi and staged-plugin tests, all architecture tasks, then `./gradlew check`.**
- [x] **Step 5: Commit** `feat(sdk): expose workspace and syntax palettes in 0.12.0`.

## Task 7: Editor follows workspace syntax without resetting documents

**Files:** `plugins/editor/src/main/java/dev/jasper/editor/{EditorPlugin,ui/EditorTheme,ui/EditorWindow,ui/EditorTab}.java`;
new/extend `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorThemeTest.java` and Editor lifecycle tests.

**Interfaces:** replace variant-based XML loading with
`EditorTheme.apply(RSyntaxTextArea area, RTextScrollPane scroll, EditorPalette editor, SyntaxPalette syntax)`.
EditorWindow gets both palettes from Appearance; EditorPlugin uses `onWorkspaceChanged`. Preserve unrelated settings
subscriptions. Existing font/line-wrap/tab-width settings still apply on configuration changes.

- [x] **Step 1: Add the red mapping and retention tests.** Give each role a distinct literal color and font flags;
assert each token type below receives its role and current editor font family/size with bold/italic combined. Capture
document identity, text, caret, selection, undo availability and dirty flag before applying a different dark workspace;
assert all survive and colors change. Same-theme repeated apply must not accumulate styles/listeners.
- [x] **Step 2: Implement the complete mapping:**

| Role | RSyntaxTextArea TokenTypes |
|---|---|
| KEYWORD | RESERVED_WORD, RESERVED_WORD_2, PREPROCESSOR |
| TYPE | DATA_TYPE |
| FUNCTION | FUNCTION |
| VARIABLE | VARIABLE |
| CONSTANT | LITERAL_BOOLEAN |
| NUMBER | LITERAL_NUMBER_DECIMAL_INT, LITERAL_NUMBER_FLOAT, LITERAL_NUMBER_HEXADECIMAL |
| STRING | LITERAL_STRING_DOUBLE_QUOTE, LITERAL_CHAR, LITERAL_BACKQUOTE |
| COMMENT | COMMENT_EOL, COMMENT_MULTILINE |
| DOC_COMMENT | COMMENT_DOCUMENTATION, COMMENT_KEYWORD, COMMENT_MARKUP |
| ANNOTATION | ANNOTATION |
| OPERATOR | OPERATOR |
| PUNCTUATION | SEPARATOR |
| TAG | MARKUP_TAG_DELIMITER, MARKUP_TAG_NAME |
| ATTRIBUTE | MARKUP_TAG_ATTRIBUTE, MARKUP_TAG_ATTRIBUTE_VALUE |
| REGEX | REGEX |
| ERROR | ERROR_IDENTIFIER, ERROR_NUMBER_FORMAT, ERROR_STRING_DOUBLE, ERROR_CHAR |

Keep unmapped token types on workspace editor foreground; inspect the pinned TokenTypes constants and explicitly map
any remaining `MARKUP_TAG_*`, `LITERAL_NUMBER_*` or `ERROR_*` constant into the corresponding row. No reflection-based
classification at runtime. Copy/update the SyntaxScheme, never call Theme.load or either XML theme. Set editor and
gutter surfaces exactly as the existing method does; reset token font flags on every apply so italic cannot linger.
A style font is `area.getFont().deriveFont((bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0))`.
- [x] **Step 3: Run Editor theme/lifecycle tests, contract tests and `./gradlew check`.**
- [x] **Step 4: Commit** `feat(editor): apply workspace syntax roles to editor tokens`.

## Task 8: Close the runnable deliverable

- [x] Update `docs/themes.md`, `docs/configuration.md`, SDK architecture/authoring docs and `docs/STATUS.md`; mark this
  plan executed only after actual completion and record any interface/derivation deviation here and in STATUS.
- [x] Run `./gradlew check`; report per-module XML totals, not an estimated count. Run `git diff --check` and the Java
  source-hygiene scan from AGENTS.md. Final independent review covers the full branch including compatibility and cleanup.
- [x] Commit `docs: record workspace theme delivery and acceptance checks` with the required trailer.
- [x] Hand GUI checks to the user (not run by the agent): Jasper Light chrome + Jasper Dark/Alacritty workspace; tabs/find/dividers/icons;
  switch dark workspaces without restarting Editor; match; multiple windows; menus/sidebar stay chrome. Confirm old
  IntelliJ/Alacritty pages still function. General plugin pane restyling is explicitly the next deliverable.
