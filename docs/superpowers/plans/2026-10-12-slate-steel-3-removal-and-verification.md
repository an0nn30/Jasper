# Slate Steel Plan 3: removal and verification — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status: complete on 2026-10-12** on `claude/slate-steel`, executed inline. Deviations from the text below:
> - Codemods: the shared scripts missed fully qualified constructor calls, loop headers with text before `{`, split
>   `configure` calls and `cond ? Theme.DARK : Theme.JASPER_LIGHT` ternaries (a ternary rule was added); every residue was
>   migrated by the rules, including sites the residue scan did not list (two-argument `ResolvedTheme`, the predicate
>   constructor).
> - Assertions that pinned a role coinciding in the removed themes now pin the role the code paints (for example
>   `surface.control.selected` for ghost toolbar selection, `text.tertiary` for captions and inactive titles,
>   alpha-carrying `border.subtle` composited over its surface).
> - Contrast floors Slate Steel misses as authored are pinned to the authored values (Light off-switch 1.68:1, Light
>   `status.success` 3.32:1 on panels, the split divider 1.43:1/1.58:1, Light menu selection text 3.98:1, the status
>   slash 1.19:1/1.14:1); they are design notes in the acceptance record.
> - Hosting cases that needed a second theme of the same darkness, or a split chrome/workspace, publish recoloured
>   Slate Steel copies or `mixed` pairs through the hosting code (`WorkspaceAppearanceFixture`, `applyTheme`), since a
>   controller publication always matches; `ThemeTestSupport.withSyntax` was added for the editor's keyword case.
> - Task 6 also deleted SettingsDialogPreview's `--migration` mode with `ThemeMigrationPaletteCapture`, and the six
>   jasper-ui `themeimport` tests under `jasper-app/src/test/java/dev/jasper/ui/` (they imported the deleted app package).
> - Task 7: the key generator also carries `UiTemplate`'s text-field loop (127 keys, not 119); jasper-buddy's
>   down-connection cross read a dropped alias and now reads `Jasper.semantic.status.danger`.
> - Task 8: the popup shadow is published as look-and-feel defaults rather than string extras (a hex string would trip
>   the scan); the card-shadow test recolours the scrim red, because Slate Steel's near-black scrim was already black at
>   a ring's alpha.
> - Task 9: the calibration column was re-rendered at `f65b40bb` in a temporary worktree; the Light inspection found
>   illegible selected-row subtitles in lists outside panels, fixed in the kit (`ItemLists`) with a test.
> - Final review: Task 8 Step 5's heading-rule change applied to every list; heading rules outside the command palette
>   are `border.subtle` again, the palette's results keep `border.menu`. The subscriber-containment case deleted in Task 4
>   is restored on Slate Steel (`aFailingSubscriberDoesNotStopTheOthersOrTheChange`).
> - Follow-ups to the final review's minors (branch `claude/slate-steel-followups`): the FlatLaf key→source table is
>   frozen (`ui-color-keys.tsv`) and checked on a palette whose every role is distinct, as are the plugin palettes;
>   the colour scan also matches fully qualified constructors; the older-install test compares every path and byte
>   under the home; the failed-install case checks that the selection rolled back; the rejected-install palette check
>   reads the failing controller; `LegacyPalettePublicationTest` is `HostedPalettePublicationTest`; a root entry that
>   is not an object no longer reads ".ui"; `tools/icons/IconPreview.java` runs on Slate Steel; `docs/themes.md` states
>   the scan's scope and STATUS lists the open items. The heading-rule check needed no change after the fix pass.

**Goal:** Delete the legacy theme pipeline, assets, tools and test seams, so Slate Steel is the only theme in code, resources, tests and documentation; prove no colour value exists outside `slate-steel.json`; re-render and record the mock comparisons on Slate Steel; and fold in the review findings deferred from Plans 1 and 2.

**Architecture:** Parallel change, so every task leaves `./gradlew check` green. Tasks 1–5 move the tests off the legacy themes onto a Slate Steel test helper while the legacy code still exists (a test that only exercises legacy code stays until its code goes). Task 6 deletes the app's legacy layer. Task 7 rewrites FlatLaf's UI keys onto semantic roles, gives `GeneratedTheme` its final shape, deletes jasper-ui's legacy layer and the repository's theme sources, and adds the colour-literal scan. Task 8 fixes the deferred review findings, Task 9 re-renders the comparisons, Task 10 rewrites the documentation and runs the full check.

**Tech Stack:** Java 25 on JBR 25 (`./gradlew` only), Swing + FlatLaf, JUnit 5 + AssertJ, Python 3 with Pillow and NumPy for `tools/visual` (offline).

**Spec:** [`docs/superpowers/specs/2026-10-12-jasper-slate-steel-themes-design.md`](../specs/2026-10-12-jasper-slate-steel-themes-design.md) sections 3.1, 4, 9, 11, 12 and phasing 13.3. Plans 1 ([engine and kit](2026-10-12-slate-steel-1-engine-and-kit.md)) and 2 ([app switch](2026-10-12-slate-steel-2-app-switch.md)) built `SlateSteel` and switched the app to it; their deferred review findings are listed in Task 8.

## Global Constraints

- Work in `/Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay` on branch `claude/slate-steel`. Never build in `/Users/RQ7RQVF/projects/moray` (the user runs `:jasper-app:run` there). Never launch the GUI; preview tasks and render tests are headless (`-Djava.awt.headless=true`, `printAll` into images) and allowed. Never train the AOT cache. Never push.
- Spec 3.1: no colour value exists anywhere in production code or resources except `jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json` (scope: Decision 1). Components reference token keys, never hex values.
- Spec 1: Jasper is an MVP; settings, APIs and files are removed outright, with no migration, aliases or deprecation shims.
- Spec 9 removals: jasper-ui `themeimport` and its resources (the five native TOMLs, the IntelliJ `.theme.json` files, `THIRD-PARTY-THEMES.md`), `themeio`, and the legacy layer of spec 4 (`Token`, `SemanticDefaults`, `ThemeResolver`, `ThemeDocument` and their parents, `UiTemplate`'s token section, `UiTokenKeys`, seeds, `ThemeGenerator`'s derivation rules); jasper-app `themeimport`, `themesui`, `ThemeCatalog`, `BuiltInThemes`, `ThemeManager`'s themes folder and installed-theme handling, `Theme`'s named constants, `WorkspaceThemeChoice`, `themes/legacy-aliases.json`; the repository's `third_party/themes`, `tools/themes`, root `THIRD-PARTY-THEMES.md`, and the `convertThemes` and `themeMigrationPreview` Gradle tasks. `GeneratedTheme`'s other uses (route selection, glass levels, change notification through `ThemeBinding`/`ThemePair`) stay.
- Spec 11: references to the removed themes in tests move to the Slate Steel test helper; test-only calibration themes are retired.
- `dev.jasper.ui.theme` stays JDK-only (`verifyUiArchitecture`); FlatLaf-facing code lives in `dev.jasper.ui.swing`. `dev.jasper.app.config` stays free of jasper-ui types; `dev.jasper.app.settingsui` uses jasper-ui only through `dev.jasper.ui.model`/`dev.jasper.ui.swing`.
- jasper-terminal: no public signature takes or returns a JediTerm type; new public members carry JavaDoc (doclint fails the build).
- `docs/design/theme-engine/migration.csv` (checked by `ThemeMigrationCoverageTest`): every new jasper-app/sdk/testkit/plugin Java file matching its `CANDIDATE` pattern gets a row; a deleted file's row keeps its path with a disposition starting `Removed in Slate Steel Plan 3; authority=<an existing file>`; every evidence path must exist. Use `inventory.py` (Shared tools).
- Keep these documentation anchors (linked and checked by `AppDocumentationTest`): `docs/themes.md#native-acceptance-for-this-upgrade-user-run` (from `docs/STATUS.md:2091`) and `docs/app-maintenance.md#themes` (from `jasper-app/README.md`).
- Commit messages end with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Test commands: `./gradlew :jasper-ui:test`, `./gradlew :jasper-ui:check`, `./gradlew :jasper-app:test --tests '<pattern>'`, `./gradlew :jasper-app:test` (about 4 minutes), `./gradlew verifyApplicationArchitecture`, `./gradlew check --continue` (about 5 minutes). Read exact results from `*/build/test-results/test/*.xml`.

## Decisions recorded by this plan (for the user's review)

1. **Scan scope.** The colour-literal scan covers production Java and text resources of `jasper-ui`, `jasper-app` and `plugins/*`. It excludes SVG artwork (the kit's JetBrains icons are SHA-256-pinned and painted as drawn; plugin icons are plugin-owned ink by contract), and the independent libraries `jasper-terminal` (its built-in default palettes for embedders that set none), `jasper-buddy` (the desk buddy's own artwork, JDK-only) and the test library `jasper-sdk-testkit`.
2. **Scan pattern.** It matches the spec's hex forms: `#RRGGBB`/`#RRGGBBAA` in Java string literals and text resources, and hex integers given to `new Color(`, `new ColorUIResource(` or `Color.decode(`. Decimal `new Color(r, g, b)` literals (fully transparent fills; the editor plugin's vim sneak highlight) and the `Color.BLACK`/`Color.WHITE` constants that spec 8's shadow and inset metrics are painted in are not matched.
3. **The 26 `Jasper.*` UI aliases are dropped** from the FlatLaf keys: nothing reads them (the kit reads the installed `Jasper.semantic.*` roles), so they would be a second, unused colour map.
4. **`GeneratedTheme` becomes `(name, dark, author, semantic, terminal, editor, syntax, uiKeys, glass)`**: it gains the editor palette spec 4 lists and loses the legacy `tokens` and the always-empty `icons`.
5. **Route machinery stays** (spec 4): `ThemeRoute` CHROME/WORKSPACE, `ResolvedTheme`'s chrome and workspace, `WorkspaceTheme`/`ThemeZone`/`HostedUi`'s workspace paths. Slate Steel always matches, so tests of a controller that splits chrome and workspace are deleted; tests of hosting code that accepts any `ResolvedTheme` keep a mixed pair from the test helper.
6. **Calibration and custom renders are retired.** Previews and render tests paint Slate Steel Dark and Light only; `tools/visual/pairs.json` points at the Dark renders. The record's "Before" column is the last recorded value (calibration theme), "After" is Slate Steel Dark; edge agreement may fall where a mock's palette differs, and is recorded as measured.
7. **A test that needs "another theme" uses a recoloured Slate Steel palette** (`ThemeTestSupport.recolored`, `KitFixtures.custom`), never a test-only theme file.
8. **`WindowContent.selectVariant(boolean dark)` replaces `selectTheme(Theme)`** as the package-private switch tests and previews use (it configures the window's `ThemeController` as `ui.appearance` does, keeping the terminal scheme).
9. **`docs/themes.md` keeps a "Native acceptance for this upgrade (user-run)" section**, now Slate Steel's native checks, because `docs/STATUS.md` links its anchor.
10. **The popup shadow's strength moves from the theme's UI keys to `FlatLafInstaller`**, read from `DesignMetrics` (it duplicated `POPOVER_SHADOW_PERCENT_DARK/LIGHT`); the switch knob's shadow gets a metric.
11. **`ThemeGenerator` is deleted, not rewritten.** Spec 4 has it "rewritten to assemble a `GeneratedTheme` from the palettes"; `SlateSteel.generate` already does exactly that, so a second assembler would have no caller.

## Review Focus

1. An existing install still has `themes/` and `terminal-themes/` folders beside `config.toml`: start-up ignores them, leaves every file byte-identical and starts Slate Steel — pinned in Task 6 (`JasperApplicationThemesTest`).
2. Plugins see the same `WorkspacePalette`, `EditorPalette` and `SyntaxPalette` values after `HostedPalettes` stops reading tokens — pinned in Task 7 (`HostedPalettesTest`).
3. FlatLaf's defaults are unchanged by the UI-key rewrite (text selection, a search field's no-match tint, list selection, buttons) for both variants — pinned in Task 7 (`UiColorKeysTest`).
4. The packaged jars hold `slate-steel.json` and none of the removed resources (`themeimport/native`, `themeimport/intellij`, `legacy-aliases.json`, the notices) — pinned in Task 7 (`PackagedResourcesTest`).
5. Switching a window between Dark and Light keeps its sessions and terminal scheme and repaints the window surface and panes from the new variant — pinned in Task 1 (`WindowVariantTest`).

---

## Shared tools (scratch scripts, never committed)

Write these once into a scratch directory outside the repository (`$SCRATCH` below; this session's scratchpad) and run them from the worktree root with `python3 -I`.

### `legacy_scan.py` — legacy references left in test sources

```python
"""List test files (or a subset by path prefix) that still reference the legacy theme layer: python3 -I legacy_scan.py [PREFIX...]"""
import pathlib, re, sys
ROOTS = ["jasper-app/src/test/java", "jasper-ui/src/test/java", "jasper-sdk-testkit/src", "plugins"]
LEGACY = re.compile(r"\bTheme\.(?:DARK|JASPER_LIGHT|of\(|Bundled|UserFile|Source)|new Theme\(|\bThemeFixtures\b|\bThemeCatalog\b"
    r"|\bBuiltInThemes\b|\bThemeState\b|ThemeManager\.(?:generated|builtIns|retainGenerated|read)\(|\bWorkspaceThemeChoice\b"
    r"|config\.TerminalColors|\bTerminalColors\.(?:MATCH|LIGHT|DARK|builtIns|parse|normalize|reserved)\b|configureWorkspace\("
    r"|configureTerminal\(|\.choice\(\)|workspaceChoice\(\)|activeThemes\(\)|selectTheme\(|ThemeTestSupport\.named\("
    r"|new ThemeController\(\)|ThemeController::new|\bToken\.[A-Z]|\.tokens\(\)|\bThemeToml\b|\bThemeResolver\b|\bThemeDocument\b"
    r"|\bThemeGenerator\b|\bSemanticDefaults\b|\bUiTemplate\b|\bUiTokenKeys\b|ui\.themeimport|ui\.themeio|app\.themeimport|app\.themesui"
    r"|new ResolvedTheme\([^,()]+,[^,()]+\)")
prefixes = sys.argv[1:]
hits = 0
for root in ROOTS:
    for path in sorted(pathlib.Path(root).rglob("*.java")):
        name = str(path)
        if prefixes and not any(name.startswith(p) for p in prefixes): continue
        if "/build/" in name: continue
        found = [(n, m.group(0)) for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1) for m in LEGACY.finditer(line)]
        if found:
            hits += 1
            print(f"{name}: {len(found)} — " + ", ".join(f"{n}:{text}" for n, text in found[:6]) + (" …" if len(found) > 6 else ""))
print("files with legacy references:", hits)
```

### `theme_codemod.py` — the mechanical replacements

```python
"""Mechanical legacy-to-Slate-Steel replacements in the given test files: python3 -I theme_codemod.py FILE..."""
import pathlib, re, sys
Q = r"(?:dev\.jasper\.app\.appearance\.)?"
DARK = Q + r"(?:Theme\.DARK|ThemeFixtures\.DARCULA)"
LIGHT = Q + r"(?:Theme\.JASPER_LIGHT|ThemeFixtures\.INTELLIJ_LIGHT)"
MATCH = "dev.jasper.app.config.TerminalSchemeChoice.MATCH"
RULES = []
for variant, flag in ((DARK, "true"), (LIGHT, "false")):
    RULES += [
        (r"new ThemeController\(" + variant + r"\)", f"ThemeTestSupport.controller({flag})"),
        (r"\.selectTheme\(" + variant + r"\)", f".selectVariant({flag})"),
        (r"ThemeManager\.generated\(" + variant + r"\)", f"ThemeTestSupport.generated({flag})"),
        (r"(?:ThemeTestSupport|ThemeManager)\.install\(" + variant + r"\)", f"ThemeTestSupport.install({flag})"),
        (r"\.configure\(" + variant + r"\)", f".configure({flag}, {MATCH})"),
        (r"\.configure\(" + variant + r",\s*", f".configure({flag}, {MATCH}, "),
        (variant + r"\.palette\(\)", f"ThemeTestSupport.terminal({flag})"),
    ]
RULES += [
    (r"new ThemeController\(\)", "ThemeTestSupport.controller(true)"),
    (r"ThemeController::new", "() -> ThemeTestSupport.controller(true)"),
    (r"ThemeTestSupport\.slateSteel\(", "ThemeTestSupport.controller("),
]
IMPORT = "import dev.jasper.app.appearance.ThemeTestSupport;"
for name in sys.argv[1:]:
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8"); before = text
    for pattern, replacement in RULES: text = re.sub(pattern, replacement, text)
    in_appearance = "package dev.jasper.app.appearance;" in text
    if "ThemeTestSupport." in text and not in_appearance and IMPORT not in text and "dev.jasper.app.appearance.*" not in text:
        first = re.search(r"^import .*;$", text, re.M)
        text = text[:first.start()] + IMPORT + "\n" + text[first.start():] if first else text
    for unused in ("import dev.jasper.app.appearance.Theme;", "import dev.jasper.app.appearance.ThemeFixtures;"):
        simple = unused.rsplit(".", 1)[1][:-1]
        body = text.replace(unused, "")
        if unused in text and not re.search(r"\b" + simple + r"\b", body): text = body.replace("\n\n\n", "\n\n")
    if text != before: path.write_text(text, encoding="utf-8"); print("rewrote", name)
```

### `inventory.py` — `migration.csv` rows (edits only the affected lines, never re-sorts)

```python
"""python3 -I inventory.py '<json>' with keys: removed {path: authority}, evidence {old test: new test}, added [[path, classification, note]], tests {path: evidence}"""
import csv, io, json, pathlib, sys
PREFIX = "Removed in Slate Steel Plan 3; authority="
spec = json.loads(sys.argv[1])
p = pathlib.Path("docs/design/theme-engine/migration.csv")
text = p.read_text(encoding="utf-8"); trailing = text.endswith("\n")
lines = text.split("\n")[:-1] if trailing else text.split("\n")
def parse(line): return next(csv.reader([line]))
def dump(row):
    out = io.StringIO(); csv.writer(out, lineterminator="").writerow(row); return out.getvalue()
removed, evidence = spec.get("removed", {}), spec.get("evidence", {})
for i in range(1, len(lines)):
    row = parse(lines[i]); changed = False
    if row[0] in removed and not row[7].startswith(PREFIX):
        row[7] = PREFIX + removed[row[0]] + "; " + row[7]; changed = True
    tests = row[6].split(";"); new = [evidence.get(t, t) for t in tests]
    if new != tests: row[6] = ";".join(dict.fromkeys(new)); changed = True
    if changed: lines[i] = dump(row)
existing = {parse(l)[0] for l in lines[1:]}
for path, classification, note in spec.get("added", []):
    if path in existing: continue
    name = path.rsplit("/", 1)[1][:-5]
    owner = "app." + path.split("/dev/jasper/app/")[1].split("/")[0] if "/dev/jasper/app/" in path else "other"
    test = path if "/src/test/" in path else spec.get("tests", {}).get(path, "none-direct")
    row = dump([path, owner, name, classification, "none", "none", test, note])
    at = next((i for i in range(1, len(lines)) if parse(lines[i])[0] > path), len(lines))
    lines.insert(at, row); existing.add(path)
p.write_text("\n".join(lines) + ("\n" if trailing else ""), encoding="utf-8")
```

### Legacy token → Slate Steel source

`SlateSteel.legacy()` (removed in Task 7) defined every token from a semantic role; a test assertion or a UI key that used a token uses its source. `ThemeTestSupport.color(dark, role)` and `theme.semantic().color(role)` read a role.

| Token | Source | Token | Source |
| --- | --- | --- | --- |
| `SURFACE`, `SCROLLBAR_TRACK` | `SURFACE_PANEL` | `HOVER`, `TAB_HOVER` | `SURFACE_CONTROL_HOVER` |
| `SURFACE_RAISED` | `SURFACE_PANEL_RAISED` | `PRESSED`, `SELECTION_INACTIVE` | `SURFACE_CONTROL_SELECTED` |
| `SURFACE_SUNKEN` | `SURFACE_CARD` | `SELECTION` | `SELECTION_BG` |
| `TITLE_BAR` | `SURFACE_APP` | `TEXT_SELECTION` | the variant's matching terminal scheme's selection |
| `STATUS_BAR` | `SURFACE_STATUS` | `TAB_BAR` | `SURFACE_TABSTRIP` |
| `SIDEBAR` | `SURFACE_SIDEBAR` | `TAB_ACTIVE` | `SURFACE_TERMINAL` |
| `TEXT`, `TAB_ACTIVE_TEXT`, `TEXT_SELECTION_TEXT` | `TEXT_PRIMARY` | `TAB_UNDERLINE`, `ACCENT`, `BUTTON_DEFAULT` | `ACCENT` |
| `TEXT_MUTED` | `TEXT_SECONDARY` | `TAB_UNDERLINE_INACTIVE` | `TEXT_TERTIARY` |
| `TEXT_DISABLED` | `TEXT_PLACEHOLDER` | `INPUT_BACKGROUND` | `SURFACE_INPUT` |
| `TEXT_ON_ACCENT`, `SELECTION_TEXT` | `TEXT_ONACCENT` | `BUTTON_BACKGROUND` | `BUTTON_SECONDARY_BG` |
| `LINK` | `TEXT_LINK` | `BUTTON_BORDER` | `BUTTON_SECONDARY_BORDER` |
| `BORDER`, `DIVIDER`, `TAB_ACTIVE_BORDER` | `BORDER_WINDOW` | `BUTTON_TEXT` | `TEXT_STRONG_SECONDARY` |
| `SEPARATOR` | `BORDER_SUBTLE` | `SCROLLBAR_THUMB` | `BORDER_CONTROL` |
| `FOCUS_RING` | `BORDER_FOCUS` | `SUCCESS` / `WARNING` / `ERROR` | `STATUS_SUCCESS` / `STATUS_WARNING` / `STATUS_DANGER` |
| `ERROR_BACKGROUND` | `ColorMath.mix(BORDER_ERROR, SURFACE_INPUT, .16)` (`UiColorKeys.errorTint` from Task 7) | `EDITOR_*` | `SlateSteel.bundled().editor(dark)`'s matching field |

### Migration rules for Tasks 3–5 (apply in this order to each file the residue scan lists)

| Pattern | Replacement |
| --- | --- |
| (a) `new ThemeController()` / `ThemeController::new` / a `Theme` argument / a `Predicate` argument | `ThemeTestSupport.controller(true)` / `controller(dark)` / `controller(dark, installer)` (the codemod does the first three) |
| (b) `selectTheme(Theme)` | `selectVariant(dark)`; a `List.of(Theme.DARK, ThemeFixtures.INTELLIJ_LIGHT)` loop becomes `for (boolean dark : new boolean[]{true, false})`; a `ThemeMigrationCapture` variant string becomes `theme.equals("dark")` |
| (c) `ThemeManager.generated(Theme)`, 2-argument `new ResolvedTheme(g, g)` | `ThemeTestSupport.generated(dark)`, `ThemeTestSupport.resolved(dark)` |
| (d) `Token.X` / `tokens().get(X)` | the source in the token table |
| (e) a legacy `Theme`'s `palette()` | `ThemeTestSupport.terminal(dark)` or `themes.current().palette()` |
| (f) loops over built-in themes, lookups by name | a loop over `dark`; a case whose subject is a theme's name or the catalog stays until Task 6 deletes it |
| (g) a custom in-memory theme (`new Theme(...)`, `Theme.of`, a TOML, a `ThemeDocument` copy, a calibration file) | `ThemeTestSupport.recolored(generated(dark), Map.of(role, colour, …))`, `withUiKeys`, `withGlass`; delete the test resource when its last user moves |
| (i) `ThemeTestSupport.install(Theme)` / `ThemeManager.install(Theme)` | `ThemeTestSupport.install(dark)` or `install(generatedTheme)` |
| (j) `configure(Theme…)`; `choice()`; `workspaceChoice()` | `configure(dark, MATCH[, font])`; `current().chrome().dark()`; `schemeChoice()` |
| (k) a split chrome/workspace (`WorkspaceThemeChoice`, `configureWorkspace`, 4-argument `configure`) | where the code under test takes a `ResolvedTheme` (plugin hosting, `WorkspaceTheme.apply`, `WindowContent.applyTheme`), `ThemeTestSupport.mixed(chrome, workspace)`; a case whose subject is the controller splitting is deleted (Decision 5) |
| (l) `config.TerminalColors` / `configureTerminal` | `configureScheme(TerminalSchemeChoice.X)` or `WindowContent.selectTerminalScheme(X)` |
| A test that exercises only legacy code (catalog, converter, resolver, legacy install colour tables) | leave it; Task 6 or Task 7 deletes it with its code |

A literal colour of a removed theme in an assertion (for example `0x191c20`, Jasper Dark's terminal) is replaced by the Slate Steel value of the same thing, written as a role (`ThemeTestSupport.color(true, SemanticColor.SURFACE_APP)`) or a terminal palette (`ThemeTestSupport.terminal(true).background()`), never as a new literal.

A preview or render test keeps its output paths; where a path held the family name `calibration` (`theme-surfaces/calibration/…`, `settings/calibration-0/…`, `task-4b-captures/calibration/…`, `workspace-surfaces/<surface>/calibration-WxH.png`) it now holds `dark`, and the Light family `light`, so Task 9 can re-point `tools/visual/pairs.json` mechanically.

---

### Task 1: The Slate Steel test helper and the window's variant switch

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (add `selectVariant` beside `selectTheme`, about line 1114)
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java:239,543` (`slateSteel(` → `controller(`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowVariantTest.java` (create)

**Interfaces:**
- Consumes: `ThemeController(boolean, TerminalSchemeChoice[, Predicate<GeneratedTheme>])`, `configure(boolean, TerminalSchemeChoice)`, `schemeChoice()`; `ResolvedTheme.slateSteel(boolean, TerminalScheme)` and its canonical constructor; `TerminalPaletteAdapter.toTerminal(SchemeColors)`; `SlateSteel.bundled()`.
- Produces (`ThemeTestSupport`, public static): `GeneratedTheme generated(boolean dark)`; `ResolvedTheme resolved(boolean dark)`; `ResolvedTheme resolved(boolean dark, TerminalScheme scheme)`; `ResolvedTheme mixed(GeneratedTheme chrome, GeneratedTheme workspace)`; `Palette terminal(boolean dark)`; `Palette terminal(TerminalScheme scheme)`; `Color color(boolean dark, SemanticColor role)`; `ThemeController controller(boolean dark)`; `ThemeController controller(boolean dark, Predicate<GeneratedTheme> installer)`; `boolean install(boolean dark)`; `GeneratedTheme recolored(GeneratedTheme base, Map<SemanticColor, Color> roles)`; `GeneratedTheme withUiKeys(GeneratedTheme base, Map<String, Object> keys)`; `GeneratedTheme withGlass(GeneratedTheme base, GlassLevels glass)`; `GeneratedTheme withDark(GeneratedTheme base, boolean dark)`. `slateSteel(...)` is renamed `controller(...)`. `WindowContent.selectVariant(boolean dark)` (package-private).

- [ ] **Step 1: Write the failing test** — `WindowVariantTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SlateSteel;
import dev.jasper.ui.theme.TerminalScheme;
import java.util.ArrayDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Review Focus 5: a window switches between Slate Steel Dark and Light in place. */
class WindowVariantTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> ThemeTestSupport.controller(true)); }

    @Test @DisabledOnOs(OS.WINDOWS)
    void selectingAVariantRethemesTheWindowAndKeepsTheSchemeAndSessions() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending), new ThemeController(true, TerminalSchemeChoice.HIGH_CONTRAST)));
        while (!pending.isEmpty()) pending.remove().run();
        until(() -> owner[0].currentPane().view() != null);
        edt(() -> {
            var session = owner[0].currentPane().session();
            owner[0].selectVariant(false);
            assertThat(owner[0].theme().chrome()).isSameAs(SlateSteel.bundled().generated(false));
            assertThat(owner[0].terminalSchemeSelected(TerminalSchemeChoice.HIGH_CONTRAST)).as("the scheme stays").isTrue();
            assertThat(owner[0].currentPane().session()).as("the shell keeps running").isSameAs(session);
            assertThat(owner[0].getBackground()).isEqualTo(ThemeTestSupport.color(false, SemanticColor.SURFACE_TERMINAL));
            assertThat(owner[0].currentPane().view().palette()).isEqualTo(ThemeTestSupport.terminal(TerminalScheme.HIGH_CONTRAST));
            owner[0].selectVariant(true);
            assertThat(owner[0].theme().chrome()).isSameAs(SlateSteel.bundled().generated(true));
            assertThat(owner[0].getBackground()).isEqualTo(ThemeTestSupport.color(true, SemanticColor.SURFACE_TERMINAL));
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*WindowVariantTest'`
Expected: FAIL to compile (`selectVariant`, `ThemeTestSupport.controller`, `color`, `terminal` not found).

- [ ] **Step 3: Write the helper and the switch**

Add to `ThemeTestSupport` (keep the legacy members until Task 6; replace `slateSteel` by the `controller` overloads and update its two callers in `ConfigurationControllerTest`):

```java
    /** Slate Steel's Dark or Light variant. */
    public static GeneratedTheme generated(boolean dark) { return SlateSteel.bundled().generated(dark); }
    /** The variant with its matching terminal scheme, as a window shows it. */
    public static ResolvedTheme resolved(boolean dark) { return resolved(dark, TerminalScheme.matching(dark)); }
    /** The variant with {@code scheme} in its terminal panes. */
    public static ResolvedTheme resolved(boolean dark, TerminalScheme scheme) { return ResolvedTheme.slateSteel(dark, scheme); }
    /** Chrome and workspace that differ, for hosting code that accepts any pair; panes take the workspace's terminal colours. */
    public static ResolvedTheme mixed(GeneratedTheme chrome, GeneratedTheme workspace) {
        return new ResolvedTheme(chrome, workspace,
            TerminalPaletteAdapter.toTerminal(new SchemeColors(workspace.terminal(), workspace.terminal().foreground())));
    }
    /** The variant's matching terminal scheme as a terminal palette. */
    public static Palette terminal(boolean dark) { return terminal(TerminalScheme.matching(dark)); }
    /** {@code scheme} as a terminal palette. */
    public static Palette terminal(TerminalScheme scheme) { return TerminalPaletteAdapter.toTerminal(SlateSteel.bundled().terminal(scheme)); }
    /** {@code role} in the variant. */
    public static Color color(boolean dark, SemanticColor role) { return SlateSteel.bundled().palette(dark).color(role); }
    /** A controller showing the variant with the matching scheme; constructing one also resets the installed look and fonts. */
    public static ThemeController controller(boolean dark) { return new ThemeController(dark, TerminalSchemeChoice.MATCH); }
    /** As {@link #controller(boolean)}, installing through {@code installer}. */
    public static ThemeController controller(boolean dark, Predicate<GeneratedTheme> installer) {
        return new ThemeController(dark, TerminalSchemeChoice.MATCH, installer);
    }
    /** Installs the variant's look and feel. */
    public static boolean install(boolean dark) { return ThemeManager.install(generated(dark)); }
    /** {@code base} with {@code roles} replaced: a theme unlike either variant for tests that need another one. */
    public static GeneratedTheme recolored(GeneratedTheme base, Map<SemanticColor, Color> roles) {
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) colors.put(role, roles.getOrDefault(role, base.semantic().color(role)));
        return new GeneratedTheme(base.name(), base.dark(), base.author(), base.tokens(), base.syntax(), base.terminal(),
            base.uiKeys(), base.icons(), base.glass(), new SemanticPalette(colors));
    }
    /** {@code base} with these FlatLaf UI keys added or replaced (values {@code #rrggbb} or FlatLaf values). */
    public static GeneratedTheme withUiKeys(GeneratedTheme base, Map<String, Object> keys) {
        var merged = new LinkedHashMap<>(base.uiKeys()); merged.putAll(keys);
        return new GeneratedTheme(base.name(), base.dark(), base.author(), base.tokens(), base.syntax(), base.terminal(),
            merged, base.icons(), base.glass(), base.semantic());
    }
    /** {@code base} with its workspace glass replaced. */
    public static GeneratedTheme withGlass(GeneratedTheme base, GlassLevels glass) {
        return new GeneratedTheme(base.name(), base.dark(), base.author(), base.tokens(), base.syntax(), base.terminal(),
            base.uiKeys(), base.icons(), glass, base.semantic());
    }
    /** {@code base} reporting {@code dark}. */
    public static GeneratedTheme withDark(GeneratedTheme base, boolean dark) {
        return new GeneratedTheme(base.name(), dark, base.author(), base.tokens(), base.syntax(), base.terminal(),
            base.uiKeys(), base.icons(), base.glass(), base.semantic());
    }
```

with imports `dev.jasper.app.config.TerminalSchemeChoice`, `dev.jasper.terminal.config.Palette`, `dev.jasper.ui.theme.*`, `java.awt.Color`, `java.util.EnumMap`, `java.util.LinkedHashMap`, `java.util.Map`, `java.util.function.Predicate`. Task 7 rewrites the four `new GeneratedTheme(...)` bodies for the final record.

Add to `WindowContent`, after `selectTheme`:

```java
    /**
     * Slate Steel's Dark ({@code dark}) or Light variant in every window sharing this window's themes, as
     * {@code ui.appearance} applies it; the terminal scheme stays. Tests and previews switch variants this way.
     */
    void selectVariant(boolean dark) {
        if (closed) return;
        try { themes.configure(dark, themes.schemeChoice()); }
        catch (ThemeController.InstallationFailure failure) { onError.accept("Could not apply the theme", failure.getMessage()); }
        finally { chrome.refreshTheme(); chrome.status().refreshTheme(); }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :jasper-app:test --tests '*WindowVariantTest' --tests '*ConfigurationControllerTest' --tests '*ThemeMigrationCoverageTest'`
Expected: PASS. If `ThemeMigrationCoverageTest` reports `WindowVariantTest.java` as an unlisted owner, add its row with `inventory.py` (`added: [["jasper-app/src/test/java/dev/jasper/app/workspace/WindowVariantTest.java", "nonvisual-helper", "New in Slate Steel Plan 3 Task 1; test owner of the variant switch"]]`) and re-run.

- [ ] **Step 5: Run the module**

Run: `./gradlew :jasper-app:test`
Expected: PASS (no behaviour changed).

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -m "The Slate Steel test helper and the window's variant switch

ThemeTestSupport gains Slate Steel variants, resolved and mixed themes, terminal
palettes, roles, controllers, installs and recoloured copies; WindowContent's
selectVariant switches a window between Dark and Light as ui.appearance does,
keeping the terminal scheme.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: jasper-ui's tests on Slate Steel

**Files:**
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java` (`dark`, `light`, `custom`, `marked`, new `withSemantic`, `withDark`)
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/swing/ChoicesTest.java:43,91,115`, `InteractiveRowsTest.java:614`, `SwitchesTest.java:115`, `PanelHostHeaderTest.java:184`, `jasper-ui/src/test/java/dev/jasper/ui/icons/BuiltInArtworkTest.java:40`
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/gallery/KitGallery.java:82-90`, `jasper-ui/build.gradle.kts:37`; delete `jasper-ui/src/test/resources/dev/jasper/ui/gallery/calibration.toml`
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/gallery/UiExamplesTest.java:17-41` and `docs/ui-architecture.md` (the `example:palette` block, lines 20-46, and its lead sentence)
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/theme/SemanticPaletteTest.java:161-174`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java` (create)

**Interfaces:**
- Consumes: `SlateSteel.bundled().generated(boolean)`, `SemanticPalette(Map<SemanticColor, Color>)`, `ColorMath.mix(Color, Color, double)`.
- Produces: `KitFixtures.dark()`/`light()` are Slate Steel's variants; `custom()` is Dark with every role 20% plum; `marked()`; `GeneratedTheme withSemantic(GeneratedTheme base, SemanticPalette palette)`; `GeneratedTheme withDark(GeneratedTheme base, boolean dark)`. The gallery takes `-Pjasper.galleryTheme=dark|light`.

- [ ] **Step 1: Write the failing test** — `KitFixturesTest.java`:

```java
package dev.jasper.ui.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SlateSteel;
import org.junit.jupiter.api.Test;

class KitFixturesTest {
    @Test void theFixturesAreSlateSteelAndCustomDiffersInEveryOpaqueRole() {
        assertThat(KitFixtures.dark()).isSameAs(SlateSteel.bundled().generated(true));
        assertThat(KitFixtures.light()).isSameAs(SlateSteel.bundled().generated(false));
        var custom = KitFixtures.custom();
        assertThat(custom.dark()).isTrue();
        for (var role : SemanticColor.values()) {
            var base = KitFixtures.dark().semantic().color(role);
            assertThat(custom.semantic().color(role).getAlpha()).as(role.key()).isEqualTo(base.getAlpha());
            if (base.getAlpha() == 255) assertThat(custom.semantic().color(role)).as(role.key()).isNotEqualTo(base);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*KitFixturesTest'`
Expected: FAIL (`dark()` is the seed-generated fixture, not Slate Steel).

- [ ] **Step 3: Rewrite the fixtures**

Replace `dark()`, `light()`, `custom()` and the private `generate(...)`, and `marked()`'s constructor call, in `KitFixtures`:

```java
    public static GeneratedTheme dark() { return SlateSteel.bundled().generated(true); }

    public static GeneratedTheme light() { return SlateSteel.bundled().generated(false); }

    /** A third appearance: Slate Steel Dark with every role 20% plum (alpha kept), so a hard-coded variant colour shows. */
    public static GeneratedTheme custom() {
        var plum = new java.awt.Color(0x7a3fa0);
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) {
            var base = dark().semantic().color(role);
            var mixed = ColorMath.mix(base, plum, .8);
            values.put(role, new java.awt.Color(mixed.getRed(), mixed.getGreen(), mixed.getBlue(), base.getAlpha()));
        }
        return withSemantic(dark(), new SemanticPalette(values));
    }

    /** {@code base} with its semantic palette replaced. */
    public static GeneratedTheme withSemantic(GeneratedTheme base, SemanticPalette palette) {
        return new GeneratedTheme(base.name(), base.dark(), base.author(), base.tokens(), base.syntax(), base.terminal(),
            base.uiKeys(), base.icons(), base.glass(), palette);
    }

    /** {@code base} reporting {@code dark}. */
    public static GeneratedTheme withDark(GeneratedTheme base, boolean dark) {
        return new GeneratedTheme(base.name(), dark, base.author(), base.tokens(), base.syntax(), base.terminal(),
            base.uiKeys(), base.icons(), base.glass(), base.semantic());
    }
```

and `marked()` returns `withSemantic(dark(), new SemanticPalette(values))`. Drop the `ThemeToml` import. Task 7 rewrites `withSemantic` and `withDark` for the final record.

- [ ] **Step 4: Move the copy sites, the gallery and the example**
  - Each `new GeneratedTheme(..., base.tokens(), ...)` in `ChoicesTest`, `InteractiveRowsTest`, `SwitchesTest`, `PanelHostHeaderTest` and `BuiltInArtworkTest`: a copy that replaces only the semantic palette becomes `KitFixtures.withSemantic(base, palette)`; one that changes only `dark` becomes `KitFixtures.withDark(base, dark)`.
  - `KitGallery.theme(name)`: `case "dark" -> KitFixtures.dark(); case "light" -> KitFixtures.light(); default -> throw new IllegalArgumentException("Unknown gallery theme: " + name);` (custom and calibration go); `jasper-ui/build.gradle.kts:37` requires `theme.get() in listOf("dark", "light")`; `git rm jasper-ui/src/test/resources/dev/jasper/ui/gallery/calibration.toml`.
  - `UiExamplesTest.colorEngineWorksWithoutSwing` between the `example:palette` markers becomes

```java
        var theme = SlateSteel.bundled().generated(true);
        var cardColor = theme.semantic().color(SemanticColor.SURFACE_CARD);
```

    and its assertion `assertThat(cardColor).isEqualTo(SlateSteel.bundled().palette(true).color(SemanticColor.SURFACE_CARD));`; drop the `ThemeToml` import. In `docs/ui-architecture.md` the lead sentence becomes "Read a Slate Steel role without constructing Swing controls:" and the `example:palette` block holds the same two lines.
  - `SemanticPaletteTest` lines 161-174 (the strip/terminal luminance check over the jasper-dark/light TOMLs) become
    `for (boolean dark : new boolean[]{true, false}) { var p = SlateSteel.bundled().generated(dark).semantic(); assertThat(ColorMath.luminance(p.color(SemanticColor.SURFACE_TERMINAL))).as(dark ? "dark" : "light").isNotEqualTo(ColorMath.luminance(p.color(SemanticColor.SURFACE_TABSTRIP))); }`.
  - A legacy-pipeline test that used `KitFixtures` (it tests `SemanticDefaults`, `ThemeGenerator` or `ThemeResolver`) generates its own theme with `ThemeGeneratorTest.generate(ThemeGeneratorTest.SEEDS)` until Task 7 deletes it.

- [ ] **Step 5: Run the module**

Run: `./gradlew :jasper-ui:check`
Expected: PASS (tests, doclint, `verifyUiArchitecture`, `UiDocumentationTest`). A kit test that asserted a seed fixture's literal colour asserts the fixture's role instead (ledger each).

- [ ] **Step 6: Commit**

```bash
git add -A jasper-ui docs/ui-architecture.md
git commit -m "jasper-ui's tests on Slate Steel

The kit fixtures are Slate Steel Dark and Light, a plum-tinted Dark stands in for
any other theme, copies replace only the palette or the variant, and the gallery
and the palette example read Slate Steel.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: App tests on Slate Steel I — appearance, application, config, platform, test support and the surface fixtures

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java` (`pair(dark)` returns the Slate Steel pair)
- Modify: `jasper-app/src/test/java/dev/jasper/app/testsupport/KitTestSupport.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/appearance/SurfaceFixtures.java:57-71` and the open-family default; `SurfacePreview.java:29`; `jasper-app/src/test/java/dev/jasper/app/plugins/SessionsSurfacePreview.java:17-19`, `PanelSurfacePreview.java:27-45`, `SessionsCompositionTest.java:18`, `StagedAddHostTest.java`, `StagedFileServerFormTest.java`
- Modify: every file `legacy_scan.py` lists under `jasper-app/src/test/java/dev/jasper/app/{appearance,application,config,documentation,platform,testsupport}` except those testing only legacy code (they stay for Task 6: `BuiltInThemesTest`, `BundledWorkspaceParityTest`, `ClearThemesTest`, `JasperLightTest`, `NativeThemeCatalogTest`, `PluginThemeParityTest`, `TerminalColorsThemeTest`, `ThemeCatalogTest`, `ThemeFixtures`, `ThemeResolverTest`, `ThemeStateTest`, `ThemeTomlWriterTest`, `WorkspaceGlassThemeTest`, `WorkspaceValuesTest`, `ThemeMigrationPreview`, `config/TerminalColorsValueTest`, and `ThemeManagerTest`'s colour-table, built-in, IntelliJ-palette and `ThemeGeneratorTest.SEEDS` cases)
- Delete: `jasper-app/src/test/resources/dev/jasper/app/appearance/desktop-hero.toml`, `desktop-host.toml`, `desktop-server.toml`, `desktop-sessions.toml`, `desktop-tunnel.toml` (once unused)

**Interfaces:**
- Consumes: Task 1's `ThemeTestSupport` API.
- Produces: `ThemeTestSupport.pair(boolean dark)` = `new ThemePair(generated(dark), generated(dark))`; `KitTestSupport.context()`/`bindings(dark)` on Slate Steel; `SurfaceFixtures.theme(String)` accepts `"dark"` and `"light"` only and `open(id, state)` uses `"dark"`; the surface previews render the families `dark` and `light`.

- [ ] **Step 1: List the residue**

Run: `python3 -I $SCRATCH/legacy_scan.py jasper-app/src/test/java/dev/jasper/app/appearance jasper-app/src/test/java/dev/jasper/app/application jasper-app/src/test/java/dev/jasper/app/config jasper-app/src/test/java/dev/jasper/app/documentation jasper-app/src/test/java/dev/jasper/app/platform jasper-app/src/test/java/dev/jasper/app/testsupport`
Expected: the files of this task listed with their legacy references (about 35 files).

- [ ] **Step 2: Apply the codemod**

Run: `python3 -I $SCRATCH/theme_codemod.py <every listed file that is not in the Task 6 list above> jasper-app/src/test/java/dev/jasper/app/plugins/SessionsCompositionTest.java jasper-app/src/test/java/dev/jasper/app/plugins/StagedAddHostTest.java jasper-app/src/test/java/dev/jasper/app/plugins/StagedFileServerFormTest.java jasper-app/src/test/java/dev/jasper/app/plugins/SessionsSurfacePreview.java jasper-app/src/test/java/dev/jasper/app/plugins/PanelSurfacePreview.java`
Expected: "rewrote …" for each changed file.

- [ ] **Step 3: The shared fixtures**
  - `ThemeTestSupport.pair(boolean dark)` body: `var theme = generated(dark); return new ThemePair(theme, theme);`.
  - `SurfaceFixtures.theme(String name)`:

```java
    static ResolvedTheme theme(String name) {
        return switch (name) {
            case "dark" -> ThemeTestSupport.resolved(true);
            case "light" -> ThemeTestSupport.resolved(false);
            default -> throw new IllegalArgumentException("Unknown surface family: " + name + " (dark or light)");
        };
    }
```

    and `open(id, state)` opens `"dark"`. Callers that passed `"calibration"`, `"host-calibration"`, `"server-calibration"` or `"sessions-calibration"` pass `"dark"`; callers that passed `"custom"` pass `"light"`. `SurfacePreview`, `SessionsSurfacePreview` and `PanelSurfacePreview` loop over `List.of("dark", "light")`; New Tunnel's `desktop-tunnel.toml` render becomes the `dark` family. Then `git rm` the five calibration TOMLs listed in **Files**.

- [ ] **Step 4: The remaining residue, by the migration rules**
  - `ShippedControlReadabilityTest`: the built-in loop becomes a loop over `dark` with `ThemeTestSupport.generated(dark)`; the contrast floors stay and are measured on Slate Steel.
  - `ThemeCompletionSmokeTest`: the themes are the two variants (`configure(dark, MATCH, new UiFontConfig("system", size))`); the "completion-custom" JSON import goes (it tests import).
  - `ThemeButtonsTest`: the IntelliJ Light gradient case runs on Slate Steel Light with the same tolerance.
  - `ChromeKeysTest`: the case at line 21 runs over both variants; the case at line 35 (legacy keys) stays for Task 6.
  - `ThemeManagerTest`: the "switching leaves nothing behind" and "own keys win" install mechanics install `ThemeTestSupport.generated(dark)`; the legacy colour tables stay for Task 6.
  - `ZoneIconsTest`: split cases apply `WorkspaceTheme.apply(component, ThemeTestSupport.mixed(ThemeTestSupport.generated(false), ThemeTestSupport.generated(true)))` instead of `configureWorkspace`; `configureTerminal` becomes `configureScheme`.
  - Everything else: the migration rules table.

- [ ] **Step 5: Check the residue**

Run: the Step 1 command again.
Expected: only the Task 6 files listed above remain.

- [ ] **Step 6: Run tests**

Run: `./gradlew :jasper-app:test`
Expected: PASS. A failure caused by Slate Steel's values in a migrated file is fixed in that file by the rules (ledger each); a failure in a file outside this task's packages caused by the `pair`/`KitTestSupport` switch is fixed the same way here.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -m "App tests on Slate Steel: appearance, application, config, platform and the surface fixtures

The shared fixtures and these packages' tests use Slate Steel Dark and Light; the
surface previews render those two families and the calibration themes go.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: App tests on Slate Steel II — the workspace

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java:185-187` (the 5-argument constructor)
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/DesktopTestSupport.java:54,61,76`
- Modify: every file `legacy_scan.py` lists under `jasper-app/src/test/java/dev/jasper/app/workspace` except `ThemeMigrationWorkspacePreview.java` (deleted in Task 6)
- Delete: `jasper-app/src/test/resources/dev/jasper/app/appearance/desktop-workspace.toml`, `desktop-palette.toml` (once unused)

**Interfaces:**
- Consumes: Task 1's helper and `selectVariant`; Task 3's `pair`.
- Produces: `WindowContent(launcher, directory, newWindow, quit, onEmpty)` starts on `new ThemeController(true, TerminalSchemeChoice.MATCH)`; `DesktopTestSupport.content(...)` overloads build Slate Steel controllers; `captureIconOwners(owner, theme, w, h)` calls `owner.selectVariant(theme.equals("dark"))`.

- [ ] **Step 1: List the residue**

Run: `python3 -I $SCRATCH/legacy_scan.py jasper-app/src/test/java/dev/jasper/app/workspace`
Expected: about 58 files.

- [ ] **Step 2: Production and the support class**
  - `WindowContent`'s 5-argument constructor: `this(launcher, directory, newWindow, quit, onEmpty, new ThemeController(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH));`.
  - `DesktopTestSupport`: the two `new ThemeController()` become `ThemeTestSupport.controller(true)`; `captureIconOwners` starts with `owner.selectVariant(theme.equals("dark"));`.

- [ ] **Step 3: Apply the codemod**

Run: `python3 -I $SCRATCH/theme_codemod.py <every file listed in Step 1 except ThemeMigrationWorkspacePreview.java>`
Expected: "rewrote …" for each changed file.

- [ ] **Step 4: Re-baseline the removed themes' literals** (each becomes the Slate Steel value of the same thing):
  - `GraphiteChromeTest.java:95-99`: the terminal background `0x191c20` → `ThemeTestSupport.terminal(true).background()`; `ToolWindow.background` `0x191c20` → `UIManager.getColor("ToolWindow.background")` equals the role its UI key maps to in the token table; `ThemeZone` `SURFACE_APP` `0x25292e` → `ThemeTestSupport.color(true, SemanticColor.SURFACE_APP)`; `Jasper.tabUnderline` `0x8eb8d2` → `ThemeTestSupport.color(true, SemanticColor.ACCENT)` read from `Jasper.semantic.accent`.
  - `MacTitleBarTest.java:224-230`: title pixel `0x25292e` → `color(true, SURFACE_APP)`; label foreground `0xa7b2bf` → `color(true, TEXT_SECONDARY)`; the Light title pixel `0xf2f2f2` → `color(false, SURFACE_APP)`.
  - `ThemeControllerTest.java:206,301`: `0x191c20` → `ThemeTestSupport.terminal(true).background()`. Its independent-workspace cases (lines 26 and 71) and its `named("Nord")`/"Darcula" cases (lines 34-42, 83) test the catalog and the split controller: delete them (Decision 5). The rollback, subscription and owner-update cases run on the variants.
  - `ToolWindowSurfaceTest.java:59`: `0x21252a` → `color(true, SURFACE_PANEL)`.
  - `MockUiTest.java:40`: `0x25292e` → `color(true, SURFACE_APP)`.
  - `WorkspaceGlassTest.java:354` (Clear Dark's glass) → a theme from `ThemeTestSupport.withGlass(generated(true), new GlassLevels(0.95, true))` applied with `owner.applyTheme(ThemeTestSupport.mixed(theme, theme), true)` after `ThemeTestSupport.install(theme)`; line 192 (Clear Dark's tab bar against its terminal) uses `color(true, SURFACE_TABSTRIP)` against `terminal(true).background()`.

- [ ] **Step 5: Custom themes, tokens and splits** (rules (d), (g), (k)):
  - `WindowCommandPaletteTest.java:244-251` "shadow-test" → `ThemeTestSupport.recolored(generated(true), Map.of(SemanticColor.SURFACE_SCRIM, Color.RED))`, installed with `ThemeTestSupport.install(theme)` and applied with `owner.applyTheme(ThemeTestSupport.mixed(theme, theme), true)`.
  - `DockDropOverlayTest.java:236-247` "drag-test" → `recolored(generated(true), Map.of(SemanticColor.ACCENT, …))` then `withUiKeys(…, Map.of("DragAndDrop.areaBackground", "#…", "DragAndDrop.borderColor", "#…"))`, applied the same way.
  - `ConfigurationStatusTest.java:84-130` "status-surface", `WorkspaceRolesTest.java:72-80` "role-test", `WorkspaceThemeTest.java:22-32` "optional-tool-window-*", `PluginPaneAppearanceTest.java:157-166` "optional-pane" → `recolored`/`withUiKeys` copies of the variants with the same overridden roles or keys, applied the same way.
  - Token lookups (`FindBarModernTest.java:99-113`, `WindowTabsTest.java:135`, `FolderTabScaleTest.java:36-37`, `GraphiteChromeTest.java:161`, `WorkspaceGlassTest.java:192`, `WorkspaceThemeTest.java:34,148,190`, `HostedContentPreview.java:48-49`, `ConfigurationStatusTest.java:84-123`) → the token table's sources. `WindowTabsTest`'s `!matching()` branch (line 133-135) never runs on Slate Steel: delete it.
  - Splits (`PluginPaneAppearanceTest.java:44`, `WorkspaceThemeTest.java:87`, `PresentationOwnershipTest.java:17`, `FindBarModernTest.java:106,116`, `WorkspaceGlassTest.java:191`, `WorkspaceSurfacePreview.java:180`) → rule (k).
  - Previews: `MockUiPreview`, `HostedContentPreview`, `TitleBarPreview`, `FlatTabsPreview`, `FolderTabsPreview`, `AlacrittyTabsPreview`, `WindowTabMotionPreview` switch with `selectVariant`; `CommandPalettePreview` (`desktop-palette.toml`) and `WorkspaceSurfacePreview` (`desktop-workspace.toml`) render the families `dark` and `light` with `selectVariant`/`controller(dark)`; then `git rm` the two TOMLs.

- [ ] **Step 6: Check the residue**

Run: `python3 -I $SCRATCH/legacy_scan.py jasper-app/src/test/java/dev/jasper/app/workspace`
Expected: only `ThemeMigrationWorkspacePreview.java`.

- [ ] **Step 7: Run tests**

Run: `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -m "App tests on Slate Steel: the workspace

Windows built without a controller start on Slate Steel Dark; the workspace tests
and previews switch variants with selectVariant, read roles instead of tokens and
the removed themes' colours, and recolour Slate Steel where they need another
theme. Tests of a controller that split chrome and workspace go.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: App tests on Slate Steel III — plugins, Settings, shortcuts, plugin manager, notifications, palette, files and windows

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/WorkspaceAppearanceFixture.java` (the no-argument constructor and `setVariant`; `set(...)` stays until Task 7)
- Modify: every file `legacy_scan.py` lists under `jasper-app/src/test/java/dev/jasper/app/{plugins,settingsui,shortcuthelp,pluginmanager,notificationui,palette,files,windows}` except `HostedKitTest`'s token-edited copies (lines 138-140, 200-202; Task 7) and `NativeThemeNavigationTest`'s catalog cases (lines 24, 31; Task 6)
- Delete: `jasper-app/src/test/resources/dev/jasper/app/appearance/desktop-settings.toml`, `desktop-vault.toml`, `custom-hero.toml`, `jasper-app/src/test/resources/dev/jasper/app/plugins/desktop-picker.toml` (once unused)

**Interfaces:**
- Consumes: Tasks 1 and 3.
- Produces: `WorkspaceAppearanceFixture()` shows `ThemeTestSupport.resolved(true)`, `setVariant(v)` shows `resolved(v == Variant.DARK)`; the Settings, plugin-manager, shortcut and vault previews and render tests paint the families `dark` and `light`.

- [ ] **Step 1: List the residue**

Run: `python3 -I $SCRATCH/legacy_scan.py jasper-app/src/test/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/settingsui jasper-app/src/test/java/dev/jasper/app/shortcuthelp jasper-app/src/test/java/dev/jasper/app/pluginmanager jasper-app/src/test/java/dev/jasper/app/notificationui jasper-app/src/test/java/dev/jasper/app/palette jasper-app/src/test/java/dev/jasper/app/files jasper-app/src/test/java/dev/jasper/app/windows`
Expected: about 52 files.

- [ ] **Step 2: Apply the codemod** to every listed file.

- [ ] **Step 3: The remaining residue**
  - `WorkspaceAppearanceFixture`: no-argument constructor `this(ThemeTestSupport.resolved(true))`; `setVariant(Variant v)` sets `ThemeTestSupport.resolved(v == Variant.DARK)`.
  - `SettingsSurfacePreview.java:50-64`, `Settings9bPreview.java:39-56`, `PluginManagerStatesPreview.java:38-54`, `PluginSettingsPagesEvidenceTest.java:62-83`: no temporary themes folder or catalog; the families `dark` and `light` configure `controller.configure(dark, TerminalSchemeChoice.MATCH, font)` ("Copper Night" goes); `git rm desktop-settings.toml`.
  - `SettingsDialogPreview.java:98`, `ShortcutHelpPreview.java:27`: the built-in loop becomes a loop over `dark`.
  - `VaultSheetRenderTest.java:24-34` (`desktop-vault.toml`, `custom-hero.toml`) and `VaultRenderEvidenceTest.java:37-41` ("companion-custom"): the families `dark` and `light`; `StagedAnchoredPickerTest.java:26` (`desktop-picker.toml`, `JASPER_LIGHT`): `dark` and `light`; then `git rm` the three TOMLs.
  - `EditorCompositionTest.java:48-50`, `EditorDocumentPreservationTest.java:39,210` ("another built-in of the same darkness"): the second theme is a scheme change (`configureScheme(TerminalSchemeChoice.HIGH_CONTRAST)`) when the case is about a workspace change keeping documents, or `recolored(...)` applied through the hosting code when it is about a different palette of the same darkness (ledger which).
  - `NotificationCardTest.java:80-81`: the five names become the two variants; the 4.5 floor stays.
  - `ThemeMigrationEvidenceTest` (files, notificationui, pluginmanager), `ThemeMigrationPaletteCapture`, `RemoteRenderEvidenceTest`, `FileServerRenderEvidenceTest`, `EditorRenderEvidenceTest`, `PluginPanelPageTest`: the capture's `"dark"`/`"light"` become `ThemeTestSupport.controller(theme.equals("dark"))` or `selectVariant`.
  - `HostedUiTest.java:69,155` and `LegacyPalettePublicationTest.java:20-35`: rule (k) and the token table; `LegacyPalettePublicationTest`'s token-to-palette cases stay for Task 7 if they depend on `HostedPalettes` reading tokens.
  - Everything else: the migration rules.

- [ ] **Step 4: Check the residue** (Step 1's command).
Expected: only `HostedKitTest` (lines 138-140, 200-202), `LegacyPalettePublicationTest` if kept for Task 7, `WorkspaceAppearanceFixture.set(...)`, and `NativeThemeNavigationTest` (lines 24, 31).

- [ ] **Step 5: Run tests**

Run: `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -m "App tests on Slate Steel: plugins, Settings, shortcuts, notifications, palette, files and windows

These packages' tests, previews and render tests use Slate Steel Dark and Light;
the Settings, vault and picker calibration themes and the custom families go.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: The app's legacy theme layer removed

**Files:**
- Delete (main): `jasper-app/src/main/java/dev/jasper/app/appearance/{BuiltInThemes,Theme,ThemeCatalog,ThemeState}.java`; `jasper-app/src/main/java/dev/jasper/app/config/{WorkspaceThemeChoice,TerminalColors}.java`; the packages `jasper-app/src/main/java/dev/jasper/app/themeimport/` and `.../themesui/`; `jasper-app/src/main/resources/dev/jasper/app/themes/legacy-aliases.json`
- Modify (main): `appearance/ThemeManager.java` (keep only `install(GeneratedTheme)` and `requireEdt`); `appearance/ThemeController.java` (remove the legacy members); `appearance/ResolvedTheme.java:16-18` (the 2-argument constructor); `appearance/TerminalPaletteAdapter.java:9-11` (`toTerminal(dev.jasper.ui.theme.TerminalColors)`); `workspace/WindowContent.java:7,1114-1119` (`selectTheme` and the `Theme` import); `appearance/package-info.java:3` (drop `dev.jasper.ui.themeio`, `dev.jasper.ui.themeimport`)
- Modify: `jasper-app/build.gradle.kts:194-224` (delete `convertThemes` and `themeMigrationPreview`)
- Delete (tests): `jasper-app/src/test/java/dev/jasper/app/themeimport/*` (8), `.../themesui/*` (4), `.../appearance/{BuiltInThemesTest,BundledWorkspaceParityTest,ClearThemesTest,JasperLightTest,NativeThemeCatalogTest,PluginThemeParityTest,TerminalColorsThemeTest,ThemeCatalogTest,ThemeFixtures,ThemeResolverTest,ThemeStateTest,ThemeTomlWriterTest,WorkspaceGlassThemeTest,WorkspaceValuesTest,ThemeMigrationPreview}.java`, `.../config/TerminalColorsValueTest.java`, `.../workspace/ThemeMigrationWorkspacePreview.java`; test resources `jasper-app/src/test/resources/dev/jasper/app/themeimport/golden/*`, `.../appearance/round-trip.toml`, `jasper-app/src/test/resources/dev/jasper/ui/themeimport/native/{intellij-light,darcula}.toml`
- Modify (tests): `appearance/ThemeManagerTest.java` (delete the legacy cases), `appearance/ChromeKeysTest.java:35`, `settingsui/NativeThemeNavigationTest.java:24,31`, `appearance/ThemeTestSupport.java` (delete `install(Theme)`, `named`), `architecture/PackagedResourcesTest.java:78`, `appearance/ThemeMigrationCoverageTest.java:28-38` (accept the Plan 3 prefix), `application/JasperApplicationThemesTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/architecture/RemovedThemeLayerTest.java` (create)

**Interfaces:**
- Consumes: Tasks 3–5 left no test outside this task's deletions using these types.
- Produces: `ThemeController` with only the Slate Steel members (`startup`, `ThemeController(boolean, TerminalSchemeChoice[, Predicate])`, `scheme`, `current`, `schemeChoice`, `configure(boolean, TerminalSchemeChoice[, UiFontConfig])`, `configureScheme`, `configureAnimations`, `animationsEnabled`, `appearance`, `subscribe`, `close`, `InstallationFailure`); `ThemeManager.install(GeneratedTheme)`; `ResolvedTheme(chrome, workspace, palette)`, `slateSteel`, `pair`, `matching`, `surface`; `TerminalPaletteAdapter.toTerminal(SchemeColors)`.

- [ ] **Step 1: Write the failing tests**

`RemovedThemeLayerTest.java`:

```java
package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 9: the app's legacy theme layer is gone, so nothing can reach it again. */
class RemovedThemeLayerTest {
    static final List<String> REMOVED = List.of("dev.jasper.app.appearance.Theme", "dev.jasper.app.appearance.ThemeCatalog",
        "dev.jasper.app.appearance.BuiltInThemes", "dev.jasper.app.appearance.ThemeState",
        "dev.jasper.app.config.WorkspaceThemeChoice", "dev.jasper.app.config.TerminalColors",
        "dev.jasper.app.themeimport.ThemeMigration", "dev.jasper.app.themeimport.ConvertThemes",
        "dev.jasper.app.themesui.ThemeImport");

    @Test void theLegacyClassesAndAliasesAreGone() {
        for (String name : REMOVED)
            assertThatThrownBy(() -> Class.forName(name)).as(name).isInstanceOf(ClassNotFoundException.class);
        assertThat(RemovedThemeLayerTest.class.getResource("/dev/jasper/app/themes/legacy-aliases.json")).isNull();
    }
}
```

and in `JasperApplicationThemesTest` (Review Focus 1):

```java
    /** Review Focus 1: an older install's theme folders are neither read nor changed. */
    @Test void anOlderInstallsThemeFoldersAreIgnoredAndLeftAsTheyAre() throws Exception {
        java.nio.file.Files.createDirectories(home.resolve("themes"));
        java.nio.file.Files.createDirectories(home.resolve("terminal-themes"));
        var theme = java.nio.file.Files.writeString(home.resolve("themes/nord.toml"), "name = \"Nord\"\n");
        var colors = java.nio.file.Files.writeString(home.resolve("terminal-themes/dark.toml"), "[colors]\n");
        start("[ui]\nappearance = 'light'\n", false);
        assertThat(java.nio.file.Files.readString(theme)).isEqualTo("name = \"Nord\"\n");
        assertThat(java.nio.file.Files.readString(colors)).isEqualTo("[colors]\n");
        try (var listing = java.nio.file.Files.list(home)) {
            assertThat(listing.map(path -> path.getFileName().toString()))
                .containsExactlyInAnyOrder("config.toml", "themes", "terminal-themes");
        }
    }
```

(`start(...)` already asserts Slate Steel is installed; change its `finally` reset to `ThemeTestSupport.controller(true)` and drop its "no themes folder" assertion, which this test supersedes.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*RemovedThemeLayerTest' --tests '*JasperApplicationThemesTest'`
Expected: `RemovedThemeLayerTest` FAILS (the classes exist); the folders test PASSES already (a pin: nothing has read them since Plan 2).

- [ ] **Step 3: Delete and trim**
  - `git rm` the main files, packages, resource, tests and test resources in **Files**.
  - `ThemeManager`: remove `BASE`, `GENERATED`, `builtIns()`, `generated(Theme)`, `retainGenerated`, `install(Theme)` and `read(String)`.
  - `ThemeController`: remove the imports of `Theme`, `config.TerminalColors` and `WorkspaceThemeChoice`, the `state` field, the constructors `ThemeController()`, `(Theme)`, `(Predicate)` and `(Theme, Predicate)`, `choice()`, `workspaceChoice()`, `activeThemes()`, `legacy()`, `configureWorkspace`, `configureTerminal`, and the four `configure(Theme…)` overloads; the private constructor takes `(Slate slate, Predicate<GeneratedTheme> installer)`, `resolve(Slate)` is `ResolvedTheme.slateSteel(slate.dark(), scheme(slate.dark(), slate.scheme()))`, `apply(Slate next, ResolvedTheme resolved, UiFontConfig font)` drops the legacy parameter and its two uses, `configureScheme` uses `slate.dark()`; the class JavaDoc describes Slate Steel only.
  - `ResolvedTheme`: delete the 2-argument constructor. `TerminalPaletteAdapter`: delete `toTerminal(dev.jasper.ui.theme.TerminalColors)`. `WindowContent`: delete `selectTheme(Theme)` and `import dev.jasper.app.appearance.Theme;`.
  - `jasper-app/build.gradle.kts`: delete the `convertThemes` and `themeMigrationPreview` registrations (lines 194-224). If `SettingsDialogPreview` has a `--migration` mode that only `ThemeMigrationPreview` used, delete the mode.
  - `ThemeManagerTest`: delete the colour-table, built-in, IntelliJ-palette, catalog and `ThemeGeneratorTest.SEEDS` cases; `ChromeKeysTest`: delete the case at line 35; `NativeThemeNavigationTest`: delete lines 24 and 31's catalog assertions; `ThemeTestSupport`: delete `install(Theme)` and `named`.
  - `PackagedResourcesTest.java:78`: the app jar holds no `dev/jasper/app/themes/` entry.
  - `ThemeMigrationCoverageTest.java:28-38`: add `|| disposition.startsWith("Removed in Slate Steel Plan 3; authority=")`.

- [ ] **Step 4: The inventory**

Run `inventory.py` with `removed` = every deleted file that has a row (the main files above: authority `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java` for the appearance and config files, `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` for `themeimport`/`themesui`; the deleted tests: authority `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java`), `evidence` = each deleted test → `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java` (appearance tests), `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java` (`themeimport`/`themesui` tests), `jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java` (`TerminalColorsValueTest`), and `added` = `RemovedThemeLayerTest.java` ("nonvisual-helper", "New in Slate Steel Plan 3 Task 6; pins the removal"). A row whose path is a jasper-ui-package test under `jasper-app/src/test/java/dev/jasper/ui/` stays for Task 7.

- [ ] **Step 5: Run tests**

Run: `./gradlew :jasper-app:test` then `./gradlew verifyApplicationArchitecture`
Expected: PASS. A compile failure names a test the earlier tasks missed: migrate it by the rules (ledger). A jasper-app test under `jasper-app/src/test/java/dev/jasper/ui/` that used a deleted app type (for example the app's `ThemeFixtures`) tests jasper-ui's legacy layer: delete it here, with a `Removed in Slate Steel Plan 3` row, rather than migrating it.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app docs/design/theme-engine/migration.csv
git commit -m "The app's legacy theme layer removed

Theme, its constants, the catalog, the built-in themes, the theme state, the
workspace and terminal-colour choices, theme import and its sheets, the one-time
migration and its aliases, ThemeManager's legacy install and ThemeController's
legacy seams go, with their tests and the convertThemes and themeMigrationPreview
tasks. An older install's theme folders are left as they are.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: jasper-ui's legacy layer removed — UI keys from semantic roles, the final `GeneratedTheme`, the colour-literal scan

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/UiColorKeys.java` (rewritten), `SlateSteel.java:210-288`, `GeneratedTheme.java`, `package-info.java`
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafThemeData.java` (moved from `themeimport`, package-private); Modify `swing/FlatLafInstaller.java:6,38,57`, `swing/NativeControlStyles.java:159`, `swing/NativeChromePainting.java:208`, `swing/package-info.java:3`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedPalettes.java`, `workspace/FindBar.java:236`, `workspace/WorkspaceTheme.java:4,25`
- Delete (main): `jasper-ui/src/main/java/dev/jasper/ui/theme/{Token,SemanticDefaults,ThemeResolver,ThemeDocument,ThemeGenerator,AuthoredUiColors,RuntimePlatform,Seed,UiTemplate,UiTokenKeys,WorkspaceGlass}.java`; the packages `dev/jasper/ui/themeio/` and `dev/jasper/ui/themeimport/`; the resources `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/`; the repository's `third_party/themes/`, `tools/themes/`, `THIRD-PARTY-THEMES.md`
- Modify: `jasper-ui/build.gradle.kts:6` (drop tomlj), `gradle/ui-architecture.gradle.kts:24` (drop `themeio`, `themeimport`), `.gitattributes:7-12`
- Delete (tests): `jasper-ui/src/test/java/dev/jasper/ui/theme/{ThemeGeneratorTest,ThemeResolverTest,AuthoredUiColorsTest}.java`, `jasper-ui/src/test/java/dev/jasper/ui/themeimport/*`, `jasper-ui/src/test/java/dev/jasper/ui/themeio/*`, their resources (`jasper-ui/src/test/resources/dev/jasper/ui/themeimport/golden/*`, `.../themeio/round-trip.toml`); `jasper-app/src/test/java/dev/jasper/ui/{theme,themeio,themeimport}/*` (8)
- Modify (tests): `ThemeTestSupport` and `KitFixtures` bodies, `jasper-ui/.../swing/SlateSteelInstallTest`, `SlateSteelGeneratedTest`, `SemanticPaletteTest` (legacy cases), `WorkspaceAppearanceFixture.set`, `HostedKitTest:138-140,200-202`, `LegacyPalettePublicationTest`, `PackagedResourcesTest.java:91-101`, tests reading `Jasper.*` alias keys (`legacy_scan.py` will not find them: `grep -rn '"Jasper\.\(editor\|title\|chrome\|muted\|tab\|find\|split\|running\|config\)' */src/test`)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java` (create), `jasper-app/src/test/java/dev/jasper/app/plugins/HostedPalettesTest.java` (create), `jasper-app/src/test/java/dev/jasper/app/architecture/ColourLiteralScanTest.java` (create); `RemovedThemeLayerTest` gains the jasper-ui classes

**Interfaces:**
- Consumes: Task 6 (no app code reaches the legacy layer).
- Produces: `UiColorKeys.flatLaf(SemanticPalette palette, Color textSelection)` → ordered `Map<String, Object>` of `#rrggbb[aa]`; `UiColorKeys.errorTint(SemanticPalette)`; `record GeneratedTheme(String name, boolean dark, String author, SemanticPalette semantic, TerminalColors terminal, EditorColors editor, Map<SyntaxRole, SyntaxStyle> syntax, Map<String, Object> uiKeys, GlassLevels glass)`; `HostedPalettes` from roles and `theme.editor()`.

**First commit: the UI keys from roles (the legacy layer still present, for the parity check).**

- [ ] **Step 1: Write the failing parity test** — `UiColorKeysTest.java`:

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

/** Review Focus 3: FlatLaf's keys from roles are exactly the template's values from the legacy token view. */
class UiColorKeysTest {
    @Test void theKeysFromRolesAreTheTemplateValuesForBothVariants() {
        for (boolean dark : new boolean[]{true, false}) {
            var theme = SlateSteel.bundled().generated(dark);
            var legacy = new LinkedHashMap<String, Object>();
            UiTemplate.FLATLAF.forEach((key, token) -> legacy.put(key, UiTemplate.hex(theme.tokens().get((Token) token))));
            var scheme = SlateSteel.bundled().terminal(TerminalScheme.matching(dark));
            assertThat(UiColorKeys.flatLaf(theme.semantic(), scheme.colors().selection())).as(dark ? "dark" : "light")
                .containsExactlyEntriesOf(legacy);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*UiColorKeysTest'`
Expected: FAIL to compile (`UiColorKeys.flatLaf` does not exist).

- [ ] **Step 3: Write `UiColorKeys`**

Add the following to `UiColorKeys` beside its legacy members (`chromeAliases`, `semanticRoles`, `nativeOverrideWarning`, `supports` and the expression grammar), which stay until Step 9 deletes their callers; Step 9 also replaces the class JavaDoc with the one shown here. The class as it stands after Step 9:

```java
package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.SemanticColor.*;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FlatLaf's colour defaults for a Slate Steel variant, each from a semantic role; text selection in text components
 * takes the variant's terminal selection and a search field's no-match background the {@linkplain #errorTint error
 * tint}. Order matters: a {@code *.suffix} key recolours the keys before it that end in the suffix.
 */
public final class UiColorKeys {
    private UiColorKeys() {}

    private sealed interface Source permits Role, TextSelection, ErrorTint {}
    private record Role(SemanticColor role) implements Source {}
    private record TextSelection() implements Source {}
    private record ErrorTint() implements Source {}
    private static final Source SELECTED_TEXT = new TextSelection(), ERROR = new ErrorTint();
    private static final Map<String, Source> KEYS = keys();

    /** The keys in FlatLaf's order, each as {@code #rrggbb} or {@code #rrggbbaa}. */
    public static Map<String, Object> flatLaf(SemanticPalette palette, Color textSelection) {
        var keys = new LinkedHashMap<String, Object>();
        KEYS.forEach((key, source) -> keys.put(key, ColorMath.hex(switch (source) {
            case Role role -> palette.color(role.role());
            case TextSelection ignored -> textSelection;
            case ErrorTint ignored -> errorTint(palette);
        })));
        return Collections.unmodifiableMap(keys);
    }

    /** A search field's no-match background: the error border laid lightly over the input surface. */
    public static Color errorTint(SemanticPalette palette) {
        return ColorMath.mix(palette.color(BORDER_ERROR), palette.color(SURFACE_INPUT), .16);
    }

    private static Map<String, Source> keys() {
        var keys = new LinkedHashMap<String, Source>();
        // UI KEYS: generated from UiTemplate.FLATLAF by the token table, in its order.
        return Collections.unmodifiableMap(keys);
    }

    private static void role(Map<String, Source> keys, String key, SemanticColor role) { keys.put(key, new Role(role)); }
}
```

Then generate the 119 entries into `keys()` (replace the marker line) with this script, run from the worktree root:

```python
import pathlib, re
SOURCE = {"SURFACE": "SURFACE_PANEL", "SCROLLBAR_TRACK": "SURFACE_PANEL", "SURFACE_RAISED": "SURFACE_PANEL_RAISED",
    "SURFACE_SUNKEN": "SURFACE_CARD", "TITLE_BAR": "SURFACE_APP", "STATUS_BAR": "SURFACE_STATUS", "SIDEBAR": "SURFACE_SIDEBAR",
    "TEXT": "TEXT_PRIMARY", "TAB_ACTIVE_TEXT": "TEXT_PRIMARY", "TEXT_SELECTION_TEXT": "TEXT_PRIMARY", "TEXT_MUTED": "TEXT_SECONDARY",
    "TEXT_DISABLED": "TEXT_PLACEHOLDER", "TEXT_ON_ACCENT": "TEXT_ONACCENT", "SELECTION_TEXT": "TEXT_ONACCENT", "LINK": "TEXT_LINK",
    "BORDER": "BORDER_WINDOW", "DIVIDER": "BORDER_WINDOW", "TAB_ACTIVE_BORDER": "BORDER_WINDOW", "SEPARATOR": "BORDER_SUBTLE",
    "FOCUS_RING": "BORDER_FOCUS", "HOVER": "SURFACE_CONTROL_HOVER", "TAB_HOVER": "SURFACE_CONTROL_HOVER",
    "PRESSED": "SURFACE_CONTROL_SELECTED", "SELECTION_INACTIVE": "SURFACE_CONTROL_SELECTED", "SELECTION": "SELECTION_BG",
    "TAB_BAR": "SURFACE_TABSTRIP", "TAB_ACTIVE": "SURFACE_TERMINAL", "TAB_UNDERLINE": "ACCENT", "ACCENT": "ACCENT",
    "BUTTON_DEFAULT": "ACCENT", "TAB_UNDERLINE_INACTIVE": "TEXT_TERTIARY", "INPUT_BACKGROUND": "SURFACE_INPUT",
    "BUTTON_BACKGROUND": "BUTTON_SECONDARY_BG", "BUTTON_BORDER": "BUTTON_SECONDARY_BORDER", "BUTTON_TEXT": "TEXT_STRONG_SECONDARY",
    "SCROLLBAR_THUMB": "BORDER_CONTROL", "SUCCESS": "STATUS_SUCCESS", "WARNING": "STATUS_WARNING", "ERROR": "STATUS_DANGER"}
template = pathlib.Path("jasper-ui/src/main/java/dev/jasper/ui/theme/UiTemplate.java").read_text(encoding="utf-8")
a = template.index("private static Map<String, Object> flatlaf()"); b = template.index("private static Map<String, Token> jasper()")
lines = []
for key, token in re.findall(r'keys\.put\("([^"]+)", ([A-Z_]+)\);', template[a:b]):
    if token == "TEXT_SELECTION": lines.append(f'        keys.put("{key}", SELECTED_TEXT);')
    elif token == "ERROR_BACKGROUND": lines.append(f'        keys.put("{key}", ERROR);')
    else: lines.append(f'        role(keys, "{key}", {SOURCE[token]});')
assert len(lines) == 119, len(lines)
target = pathlib.Path("jasper-ui/src/main/java/dev/jasper/ui/theme/UiColorKeys.java")
text = target.read_text(encoding="utf-8")
marker = "        // UI KEYS: generated from UiTemplate.FLATLAF by the token table, in its order.\n"
assert text.count(marker) == 1
target.write_text(text.replace(marker, "\n".join(lines) + "\n"), encoding="utf-8")
```

In this commit the legacy members stay untouched, so their callers (`ThemeGenerator`, `AuthoredUiColors`, `UiTokenKeys`, `ThemeToml`, `ImportedColors`, `WorkspaceTokenKeys`) keep compiling; Step 9 deletes them together.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :jasper-ui:test --tests '*UiColorKeysTest'`
Expected: PASS.

- [ ] **Step 5: Slate Steel's keys, and production off tokens**
  - `SlateSteel.uiKeys(...)` becomes

```java
    /** FlatLaf's colour keys from the variant's roles; FlatLafInstaller adds the metric keys. */
    private static Map<String, Object> uiKeys(SemanticPalette palette, SchemeColors scheme, boolean dark) {
        var keys = new LinkedHashMap<>(UiColorKeys.flatLaf(palette, scheme.colors().selection()));
        // Popovers and menus: FlatLaf's painted popup shadow (macOS keeps its native one), black at theme.md's strength.
        keys.put("Popup.dropShadowColor", ColorMath.hex(Color.BLACK));
        keys.put("Popup.dropShadowOpacity", dark ? .5 : .18);
        return keys;
    }
```

    (`generate` passes `palette, scheme, dark`; the `Jasper.*` aliases go — Decision 3).
  - `NativeControlStyles.java:159`: `UiColorKeys.errorTint(palette)` instead of `theme.tokens().get(Token.ERROR_BACKGROUND)`. `FindBar.java:236`: `UiColorKeys.errorTint(ThemeZone.theme(this).semantic())`. `NativeChromePainting.java:208`: `theme.semantic().color(SemanticColor.BORDER_WINDOW)`. `WorkspaceTheme.java:25`: `theme.workspace().semantic().color(SemanticColor.TEXT_PRIMARY)`; drop the `Token` imports.
  - Tests reading a `Jasper.*` alias key (the grep in **Files**) read `Jasper.semantic.<role>` or the role from the token table.

- [ ] **Step 6: Run tests and commit**

Run: `./gradlew :jasper-ui:check` then `./gradlew :jasper-app:test`
Expected: PASS.

```bash
git add -A jasper-ui jasper-app/src
git commit -m "FlatLaf's keys from Slate Steel's roles

UiColorKeys maps each FlatLaf key to a semantic role (text selection to the
terminal selection, a search miss to the error tint) and equals the template's
values for both variants. The unread Jasper.* aliases go, and the find bar, search
fields and split dividers read roles instead of tokens.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

**Second commit: the legacy layer removed.**

- [ ] **Step 7: Write the failing tests**

`ColourLiteralScanTest.java` (Decisions 1 and 2):

```java
package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Spec 3.1 and 11: no colour value lives in production code or resources outside slate-steel.json. */
class ColourLiteralScanTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot"));
    static final Path THEME = ROOT.resolve("jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json");
    static final Pattern STRING_HEX = Pattern.compile("\"[^\"\\n]*#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?(?![0-9A-Za-z])[^\"\\n]*\"");
    static final Pattern CONSTRUCTED = Pattern.compile("(?:new Color|new ColorUIResource|Color\\.decode)\\(\\s*\"?(?:0x|#)[0-9A-Fa-f]{6}");
    static final Pattern RESOURCE_HEX = Pattern.compile("#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?(?![0-9A-Za-z])");
    static final Set<String> TEXT_RESOURCES = Set.of("json", "toml", "properties", "xml", "css", "html", "tsv");

    @Test void noProductionColourLiteralOutsideTheThemeFile() throws IOException {
        var offenders = new ArrayList<String>();
        var roots = new ArrayList<>(List.of(ROOT.resolve("jasper-ui/src/main"), ROOT.resolve("jasper-app/src/main")));
        try (var plugins = Files.list(ROOT.resolve("plugins"))) {
            plugins.map(plugin -> plugin.resolve("src/main")).filter(Files::isDirectory).forEach(roots::add);
        }
        for (Path root : roots) try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString(), extension = name.substring(name.lastIndexOf('.') + 1);
                if (file.equals(THEME)) continue;
                String text;
                if (extension.equals("java")) text = Files.readString(file);
                else if (TEXT_RESOURCES.contains(extension)) text = Files.readString(file);
                else continue;
                var lines = text.split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    boolean hit = extension.equals("java")
                        ? STRING_HEX.matcher(line).find() || CONSTRUCTED.matcher(line).find()
                        : RESOURCE_HEX.matcher(line).find();
                    if (hit) offenders.add(ROOT.relativize(file) + ":" + (i + 1) + ": " + line.strip());
                }
            }
        }
        assertThat(offenders).as("colour literals outside slate-steel.json").isEmpty();
    }
}
```

and in `jasper-app/build.gradle.kts`'s documentation-inputs block add `rootProject.fileTree("jasper-ui/src/main")` and `rootProject.fileTree("plugins") { include("*/src/main/**") }` to `inputs.files(...)`, so a source edit re-runs the scan.

`HostedPalettesTest.java` (Review Focus 2):

```java
package dev.jasper.app.plugins;

import static dev.jasper.ui.theme.SemanticColor.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.sdk.ui.EditorPalette;
import dev.jasper.sdk.ui.WorkspacePalette;
import dev.jasper.ui.theme.SlateSteel;
import org.junit.jupiter.api.Test;

/** Review Focus 2: plugins receive Slate Steel's roles and editor scheme, as before the token view went. */
class HostedPalettesTest {
    @Test void thePalettesAreTheVariantsRolesAndEditorScheme() {
        for (boolean dark : new boolean[]{true, false}) {
            var theme = SlateSteel.bundled().generated(dark);
            var p = theme.semantic();
            assertThat(HostedPalettes.workspace(theme)).isEqualTo(new WorkspacePalette(p.color(SURFACE_PANEL), p.color(SURFACE_PANEL_RAISED),
                p.color(SURFACE_CARD), p.color(TEXT_PRIMARY), p.color(TEXT_SECONDARY), p.color(BORDER_WINDOW), p.color(SELECTION_BG),
                p.color(TEXT_ONACCENT), p.color(SURFACE_CONTROL_HOVER), p.color(ACCENT), p.color(STATUS_SUCCESS),
                p.color(STATUS_WARNING), p.color(STATUS_DANGER)));
            var e = SlateSteel.bundled().editor(dark);
            assertThat(theme.editor()).isEqualTo(e);
            assertThat(HostedPalettes.editor(theme)).isEqualTo(new EditorPalette(e.background(), e.foreground(), e.caret(),
                e.selection(), e.currentLine(), e.gutter(), e.lineNumbers()));
        }
    }
}
```

and `RemovedThemeLayerTest.REMOVED` gains `"dev.jasper.ui.theme.Token"`, `"dev.jasper.ui.theme.ThemeGenerator"`, `"dev.jasper.ui.theme.SemanticDefaults"`, `"dev.jasper.ui.theme.ThemeResolver"`, `"dev.jasper.ui.theme.ThemeDocument"`, `"dev.jasper.ui.themeio.ThemeToml"`, `"dev.jasper.ui.themeimport.ThemeConverter"`, with `assertThat(RemovedThemeLayerTest.class.getResource("/dev/jasper/ui/themeimport/native/index.txt")).isNull();`.

- [ ] **Step 8: Run tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*ColourLiteralScanTest' --tests '*HostedPalettesTest' --tests '*RemovedThemeLayerTest'`
Expected: FAIL — the scan lists `themeimport/ImportPalettes.java`, `themeio/ThemeToml.java` and the native TOML and IntelliJ JSON resources; `HostedPalettesTest` does not compile (`theme.editor()`); `RemovedThemeLayerTest` finds the jasper-ui classes.

- [ ] **Step 9: The final record and the deletions**
  - `GeneratedTheme`:

```java
package dev.jasper.ui.theme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A Slate Steel variant with every value filled in: the semantic palette, the matching terminal palette, the editor
 * scheme and its syntax styles, FlatLaf's ordered UI keys and the workspace glass. Immutable and Swing-free.
 */
public record GeneratedTheme(String name, boolean dark, String author, SemanticPalette semantic, TerminalColors terminal,
                             EditorColors editor, Map<SyntaxRole, SyntaxStyle> syntax, Map<String, Object> uiKeys, GlassLevels glass) {
    public GeneratedTheme {
        Objects.requireNonNull(name); Objects.requireNonNull(author); Objects.requireNonNull(semantic);
        Objects.requireNonNull(terminal); Objects.requireNonNull(editor); Objects.requireNonNull(glass);
        syntax = Map.copyOf(syntax);
        uiKeys = Collections.unmodifiableMap(new LinkedHashMap<>(uiKeys));
    }
}
```

    `SlateSteel.generate(dark)` returns `new GeneratedTheme("Slate Steel", dark, "Jasper", palette, scheme.colors(), editor(dark), syntax(dark), uiKeys(palette, scheme, dark), GlassLevels.OPAQUE)`; delete `legacy(...)`.
  - Move `FlatLafThemeData` to `dev.jasper.ui.swing` as a package-private final class with only `explicitColors`, `forThisOs`, `color`, `normalized`, `json(String name, boolean dark, String author, Map<String, Object> ui)` (no icons) and the writers; `FlatLafInstaller` calls `FlatLafThemeData.json(theme.name(), theme.dark(), theme.author(), theme.uiKeys())` and drops the `themeimport` import; `swing/package-info.java` drops the `themeimport.FlatLafThemeData` dependency.
  - `HostedPalettes`:

```java
    static WorkspacePalette workspace(GeneratedTheme theme) {
        var p = theme.semantic();
        return new WorkspacePalette(p.color(SemanticColor.SURFACE_PANEL), p.color(SemanticColor.SURFACE_PANEL_RAISED),
            p.color(SemanticColor.SURFACE_CARD), p.color(SemanticColor.TEXT_PRIMARY), p.color(SemanticColor.TEXT_SECONDARY),
            p.color(SemanticColor.BORDER_WINDOW), p.color(SemanticColor.SELECTION_BG), p.color(SemanticColor.TEXT_ONACCENT),
            p.color(SemanticColor.SURFACE_CONTROL_HOVER), p.color(SemanticColor.ACCENT), p.color(SemanticColor.STATUS_SUCCESS),
            p.color(SemanticColor.STATUS_WARNING), p.color(SemanticColor.STATUS_DANGER));
    }
    static EditorPalette editor(GeneratedTheme theme) {
        var e = theme.editor();
        return new EditorPalette(e.background(), e.foreground(), e.caret(), e.selection(), e.currentLine(), e.gutter(), e.lineNumbers());
    }
```

  - Test helpers: `ThemeTestSupport.recolored/withUiKeys/withGlass/withDark` and `KitFixtures.withSemantic/withDark` construct the new record (`new GeneratedTheme(base.name(), dark, base.author(), palette, base.terminal(), base.editor(), base.syntax(), keys, glass)`). `WorkspaceAppearanceFixture.set(variant, workspace, editor, syntax)` builds `recolored(generated(dark), roles)` from the 13 workspace fields (the `HostedPalettes.workspace` role order) with `new EditorColors(editor.background(), …)` and the syntax palette mapped to `SyntaxStyle`s; `HostedKitTest`'s token-edited copies become `recolored` copies; `LegacyPalettePublicationTest` asserts the roles (or is deleted if it only covered the token view; ledger).
  - `UiColorKeys`: delete the legacy members (`chromeAliases`, `semanticRoles`, `nativeOverrideWarning`, `supports`, the expression grammar and their patterns) and give the class the JavaDoc of Step 3.
  - `git rm` every file in **Files**' delete lists; drop `tomlj` from `jasper-ui/build.gradle.kts` (keep it wherever else it is declared); drop `themeio`/`themeimport` from `gradle/ui-architecture.gradle.kts:24`; drop the IntelliJ JSON lines from `.gitattributes`; trim `theme/package-info.java` to the Slate Steel engine (no "authored documents, inheritance resolution", no "legacy UI color mappings").
  - `UiColorKeysTest`: the parity case goes with `UiTemplate`; it becomes `everyKeyIsARolesColour` (`for (var entry : UiColorKeys.flatLaf(p, sel).entrySet()) assertThat(entry.getValue()).isIn(allRoleHexes, ColorMath.hex(sel), ColorMath.hex(UiColorKeys.errorTint(p)))`) and `textSelectionAndTheSearchTintKeepTheirSources` (`"*.selectionBackground"` → `sel`; `"SearchField.errorBackground"` → `errorTint`).
  - `SlateSteelGeneratedTest`: drop the token lines; assert `theme.editor()` equals `SlateSteel.bundled().editor(dark)` and `uiKeys().get("Panel.background")` equals `ColorMath.hex(semantic().color(SURFACE_PANEL))`. `SemanticPaletteTest`: delete the cases on `SemanticDefaults` or `tokens()`.
  - `PackagedResourcesTest.java:91-101`: the UI jar holds `dev/jasper/ui/theme/slate-steel.json` and no entry under `dev/jasper/ui/themeimport/`; neither jar holds `THIRD-PARTY-THEMES.md`.
  - `docs/design/theme-engine/migration.csv` via `inventory.py`: `removed` for the jasper-app tests under `jasper-app/src/test/java/dev/jasper/ui/` (authority `jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java`); evidence of deleted tests → `jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java`; `added` for `HostedPalettesTest.java` and `ColourLiteralScanTest.java` if `CANDIDATE` matches them.
  - Javadoc links to deleted types (`SlateSteel` comments, `Seed`, `BuiltInThemes`) go with them; doclint must stay clean.

- [ ] **Step 10: Run tests**

Run: `./gradlew :jasper-ui:check` then `./gradlew :jasper-app:test` then `./gradlew verifyApplicationArchitecture`
Expected: PASS, including the three new tests.

- [ ] **Step 11: Commit**

```bash
git add -A jasper-ui jasper-app gradle .gitattributes third_party tools THIRD-PARTY-THEMES.md docs/design/theme-engine/migration.csv
git commit -m "jasper-ui's legacy theme layer removed

GeneratedTheme holds Slate Steel's palette, terminal and editor schemes, syntax,
FlatLaf keys and glass; plugins' palettes come from the roles and the editor
scheme. Token, the semantic defaults, the resolver, theme documents, the
generator, themeio, themeimport and their resources go, with the vendored theme
sources, their tools and notices, and tomlj. A scan pins that no colour value
lives outside slate-steel.json.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: The deferred review findings

Plan 1's final review deferred findings 1–6 (finding 7, the acceptance record's before→after per row, is Task 10); Plan 2's deferred 7–11.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java:118,145,300-305`; Test: `SlateSteelPaletteTest` (and `SlateSteelTerminalTest`, `SlateSteelEditorTest` if they pin the old messages)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Buttons.java:244-249`; Test: `ButtonsTest`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java` (`uiKeys`: no popup keys), `swing/FlatLafInstaller.java:44-49`, `swing/Switches.java:95`, `metrics/DesignMetrics.java` (`SWITCH_KNOB_SHADOW_PERCENT(15)`); Test: `SlateSteelInstallTest`, `SwitchesTest`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notificationui/CardLayer.java:337-339`; Test: `CardLayerTest`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/InteractiveRows.java:1210`; Test: `InteractiveRowsTest`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java:33-34`; Test: `SlateSteelControllerTest`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowStatusBar.java:14,39,192`, `WindowContent.java` (`chrome.status().applyPalette(...)` in `applyTheme`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (package-private `themes()`); Tests: `JasperApplicationThemesTest`, `SettingsDialogTest` (the reset case), `SystemThemeApplicationTest`

**Interfaces:**
- Produces: `DesignMetrics.Role.SWITCH_KNOB_SHADOW_PERCENT`; `JasperApplication.themes()` (package-private, "The application's theme controller"); `WindowStatusBar` without a terminal palette.

- [ ] **Step 1: Load errors name the key** (Plan 1 finding 1). Test in `SlateSteelPaletteTest` (beside `aFileTheEngineCannotUseFailsNamingTheKey`, which shows the file's spacing):

```java
    @Test void aMissingOrUnknownSchemeOrEditorEntryIsNamed() throws Exception {
        String json = bundledJson();
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"fg\\.bold\": \"#[0-9A-Fa-f]+\",\\s*", ""), "t.json"))
            .hasMessageContaining("terminal.slate-steel-dark: Missing colour \"fg.bold\"");
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"bg\":", "\"bgx\": \"#000000\", \"bg\":"), "t.json"))
            .hasMessageContaining("terminal.slate-steel-dark: Unknown colour \"bgx\"");
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"caret\": \"[^\"]+\",\\s*", ""), "t.json"))
            .hasMessageContaining("editor.slate-steel-dark: Missing entry \"caret\"");
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"editor\":", "\"extra\": {}, \"editor\":"), "t.json"))
            .hasMessageContaining("Unknown object \"extra\"");
    }
```

  Run it: FAIL (the messages list every expected key instead of naming one). Fix: replace the set-equality checks in `scheme(...)` (line 118), `editor(...)` (line 145) and `objects(...)` (line 302) with one helper:

```java
    /** {@code keys} exactly: names the first missing, then the first unknown entry. */
    private void exactly(Set<String> actual, Collection<String> keys, String where, String noun) {
        for (String key : keys) if (!actual.contains(key)) throw error(where + ": Missing " + noun + " \"" + key + "\"");
        for (String key : actual) if (!keys.contains(key)) throw error(where + ": Unknown " + noun + " \"" + key + "\"");
    }
```

  (`scheme`: `exactly(values.keySet(), TERMINAL_KEYS, where, "colour")`; `editor`: `exactly(values.keySet(), expected, where, "entry")`; `objects`: `exactly(map.keySet(), keys, where.isEmpty() ? "slate-steel.json" : where, "object")` after its `instanceof Map` check). Update `SlateSteelPaletteTest:52`'s "Expected the objects" to the new message. PASS.

- [ ] **Step 2: The primary button's inset line follows the corners** (finding 2). Today the line is a rectangle under a hard (unantialiased) clip, which leaves the face's outermost fully covered pixels plain accent (the calibrated test `compactDetailFaceMatchesSourceExtentAndCornersWithoutShrinkingItsHitBox` says so). Test in `ButtonsTest`, with that test's setup (the `connect` button in a panel detail, 74×28, painted at scale 2 into a 148×56 ARGB image, face rows from device row 6):

```java
    /** Plan 1 finding 2: the inset line reaches the face's outermost pixels, antialiased into its rounded corners. */
    @Test void thePrimaryInsetLineReachesTheFacesOutermostPixels() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var action = new Nodes.Button("connect", ControlState.READY, "Connect", "", ButtonKind.PRIMARY, "connect", "Connect");
            var detail = new Nodes.Stack("detail", ControlState.READY, LayoutRole.DETAIL, List.of(action));
            var panel = new Nodes.Panel("panel", ControlState.READY, new Header("header", ControlState.READY, "Panel", java.util.OptionalInt.empty(), java.util.Optional.empty(), java.util.Optional.empty(), List.of()),
                java.util.Optional.empty(), new Text("body", ControlState.READY, "Body", TextRole.BODY), java.util.Optional.of(detail));
            try (var view = new KitView(ctx, new ViewSpec(panel), e -> {})) {
                view.setSize(260, 160); InteractiveRowsTest.layout(view);
                var button = find(view, "connect", JButton.class); button.setSize(74, 28);
                var image = new BufferedImage(148, 56, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
                try { g.scale(2, 2); button.paint(g); } finally { g.dispose(); }
                int accent = ctx.theme().semantic().color(SemanticColor.ACCENT).getRGB();
                for (int row = 6; row < 8; row++) {
                    int x = 0;
                    while (x < image.getWidth() && (image.getRGB(x, row) >>> 24) != 255) x++;
                    assertThat(image.getRGB(x, row)).as("row %s's outermost opaque face pixel carries the inset line", row).isNotEqualTo(accent);
                }
            }
        });
    }
```

  FAIL (plain accent). Fix in `Buttons.java:244-249`: instead of clipping a rectangle, fill the face minus the face moved down one logical pixel, antialiased:

```java
                ThemePainting.antialias(top);
                var line = new java.awt.geom.Area(face);
                line.subtract(new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(face.x, face.y + UIScale.scale(1),
                    face.width, face.height, face.arcwidth, face.archeight)));
                top.setColor(ColorMath.opacity(Color.WHITE, context.metrics().value(DesignMetrics.Role.BUTTON_INSET_PERCENT) / 100.0));
                top.fill(line);
```

  PASS. In the calibrated test, the first two face rows now blend at the corners: count those rows' opaque face pixels (`image.getRGB(x, row + 6) >>> 24 == 255`) instead of exact `accent`/`tinted` values, and update its comment; every other assertion stays.

- [ ] **Step 3: One source for the popup and knob shadows** (finding 3). Test in `SlateSteelInstallTest`:

```java
    @Test void thePopupShadowComesFromTheMetricsNotTheThemeKeys() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                for (boolean dark : new boolean[] {true, false}) {
                    var theme = SlateSteel.bundled().generated(dark);
                    assertThat(theme.uiKeys().keySet()).noneMatch(key -> key.startsWith("Popup."));
                    FlatLafInstaller.install(theme);
                    assertThat(UIManager.getColor("Popup.dropShadowColor")).isEqualTo(java.awt.Color.BLACK);
                    assertThat(((Number) UIManager.get("Popup.dropShadowOpacity")).doubleValue()).isCloseTo(dark ? .5 : .18,
                        org.assertj.core.data.Offset.offset(1e-6));
                }
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }
```

  FAIL (the keys are in `uiKeys`). Fix: delete the two `keys.put("Popup.…")` lines from `SlateSteel.uiKeys`; in `FlatLafInstaller`'s fixed extras put `Popup.dropShadowColor` = `new ColorUIResource(Color.BLACK)` and `Popup.dropShadowOpacity` = `DesignMetrics.DEFAULT.value(theme.dark() ? DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK : DesignMetrics.Role.POPOVER_SHADOW_PERCENT_LIGHT) / 100f`. PASS. Then add `SWITCH_KNOB_SHADOW_PERCENT(15)` to `DesignMetrics.Role` and use it at `Switches.java:95` (`ColorMath.opacity(Color.BLACK, context.metrics().value(DesignMetrics.Role.SWITCH_KNOB_SHADOW_PERCENT) / 100.0)`): the same value from one place, so `SwitchesTest` stays green.

- [ ] **Step 4: Notification card shadows are black** (finding 5). Test in `CardLayerTest` (with its `showingTwo` and `paint` helpers; the newest card is the bottom one, so the margin below it is shadow only):

```java
    /** Plan 1 finding 5: card shadows are black (theme.md), not the scrim's colour. */
    @Test void cardShadowsAreBlackInBothVariants() {
        for (boolean dark : new boolean[] {true, false}) {
            var bindings = KitTestSupport.bindings(dark);
            try {
                var ids = new UUID[2];
                var layer = showingTwo(bindings.context(dev.jasper.ui.theme.ThemeRoute.CHROME), ids);
                var image = paint(layer, 1);
                var card = layer.card(ids[1]).getBounds();
                int shadowed = 0;
                for (int x = card.x; x < card.x + card.width; x++)
                    for (int y = card.y + card.height; y < Math.min(image.getHeight(), card.y + card.height + 6); y++) {
                        int argb = image.getRGB(x, y);
                        if (argb >>> 24 == 0) continue;
                        shadowed++;
                        assertThat(argb & 0xFFFFFF).as("%s shadow pixel (%s,%s)", dark ? "dark" : "light", x, y).isZero();
                    }
                assertThat(shadowed).as("the card casts a shadow").isPositive();
            } finally { bindings.close(); }
        }
    }
```

  (close `bindings` as the neighbouring tests do). FAIL (Slate Steel's scrim is `#0A0C10`/`#1C2027`). Fix at `CardLayer.java:337-339`: `int argb = new Color(0, 0, 0, alpha).getRGB();` with the comment "The shadow is black (theme.md) at the former per-ring alphas, stronger on a dark theme." PASS; `aShadowIsRenderedAgainWhenItsCardChangesSizeOrItsColourOrTheScaleChanges` still re-renders on the switch, because the alpha differs.

- [ ] **Step 5: The palette's heading rules are the menu edge** (finding 6). `CommandPaletteTest.java:421` asserts the heading rule is `BORDER_SUBTLE`; change that assertion to `semantic.color(SemanticColor.BORDER_MENU)` (spec 7: `border.menu` is the palette's "edge and separators"). Run `./gradlew :jasper-app:test --tests '*CommandPaletteTest'`: FAIL. Fix at `InteractiveRows.java:1210`: `SemanticColor.BORDER_MENU`. PASS; a kit test that pinned the subtle hairline follows.

- [ ] **Step 6: Installation failures name Slate Steel by identity** (Plan 2 finding 10). Test in `SlateSteelControllerTest`:

```java
    @Test void aFailureNamesTheVariantOnlyForSlateSteelItself() {
        var copy = ThemeTestSupport.recolored(STEEL.generated(true), java.util.Map.of());
        assertThat(new ThemeController.InstallationFailure(copy, new IllegalStateException("x")).getMessage())
            .isEqualTo("Could not apply theme: Slate Steel");
        assertThat(new ThemeController.InstallationFailure(STEEL.generated(false), new IllegalStateException("x")).getMessage())
            .isEqualTo("Could not apply theme: Slate Steel Light");
    }
```

  (the constructor is package-private and the test is in `dev.jasper.app.appearance`). FAIL (the copy, named "Slate Steel", gets " Dark"). Fix at `ThemeController.java:33-34`:

```java
            super("Could not apply theme: " + theme.name()
                + (theme == SlateSteel.bundled().generated(theme.dark()) ? (theme.dark() ? " Dark" : " Light") : ""), cause);
```

  PASS.

- [ ] **Step 7: The status bar holds no terminal palette** (Plan 2 finding 11). Delete `WindowStatusBar`'s `palette` field, `applyPalette` and the `Palette` import, and the `chrome.status().applyPalette(theme.palette())` call in `WindowContent.applyTheme`. This removes dead code (nothing read the field), so there is no failing test; `./gradlew :jasper-app:test --tests '*WindowStatusBar*' --tests '*WindowContent*'` stays green (ledger).

- [ ] **Step 8: Pin Plan 2's three test gaps** (Plan 2 findings 7–9; the behaviour exists, so these pass at once):
  - `SettingsDialogTest.appearanceSelectionsApplyAndReopenAfterAnExternalChange`: after the `reset` apply, `assertThat(java.nio.file.Files.readString(file)).doesNotContain("appearance");`.
  - `JasperApplication`: add `/** The application's theme controller. */ ThemeController themes() { return themes; }`; `JasperApplicationThemesTest.start(...)` takes the expected scheme and asserts `application[0].themes().current().palette()` equals `TerminalPaletteAdapter.toTerminal(SlateSteel.bundled().terminal(scheme))` (High Contrast for the light case, Slate Steel Dark for the default).
  - `SystemThemeApplicationTest.withTheSystemAppearanceAnOsChangeReThemesWithoutPublishingOrWritingConfiguration`: before the flip, `var draft = new dev.jasper.app.settingsui.SettingsDraft(service.initialState().values()); draft.stage(CoreSettings.WINDOW_TAB_HEIGHT, java.util.Optional.of(44));`; after it, `assertThat(draft.modified()).isTrue(); assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(44);`.

- [ ] **Step 9: Run tests**

Run: `./gradlew :jasper-ui:check` then `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add -A jasper-ui jasper-app
git commit -m "The deferred review findings

Theme-file errors name the missing or unknown key; the primary button's inset line
follows its rounded corners; the popup and switch-knob shadows have one metric
source; notification card shadows are black; the palette's heading rules are the
menu edge; installation failures recognise Slate Steel by identity; the status bar
drops an unread terminal palette; and three Plan 2 behaviours are pinned.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: The mock comparisons on Slate Steel

**Files:**
- Modify: `tools/visual/pairs.json` (render paths), `tools/visual/README.md` (if it names calibration themes)
- Modify: `docs/design/theme-engine/acceptance.md` ("How the evidence is made", the mocked-surfaces table, a new Slate Steel table, "User-run checks still open")

**Interfaces:**
- Consumes: Tasks 3–5's `dark`/`light` render families.

- [ ] **Step 1: Render** (all headless):

```bash
./gradlew :jasper-app:themeSurfacePreview :jasper-app:settingsSurfacePreview :jasper-app:settings9bPreview :jasper-app:workspaceSurfacePreview
./gradlew :jasper-app:commandPalettePreview --args="$PWD/jasper-app/build/palette-surfaces --task9c"
./gradlew :jasper-app:test --tests '*StagedAnchoredPickerTest' --tests '*VaultSheetRenderTest' --tests '*StagedAddHostTest' --tests '*StagedFileServerFormTest' --tests '*SessionsCompositionTest'
```

Expected: BUILD SUCCESSFUL; PNGs under `jasper-app/build/` for the `dark` and `light` families.

- [ ] **Step 2: Point the pairs at the Dark renders.** Tasks 3–5 kept every preview's output path and put the family name `dark` where it said `calibration`, so the rewrite is mechanical:

```bash
python3 -I - <<'PY'
import json, pathlib, re
path = pathlib.Path("tools/visual/pairs.json"); pairs = json.loads(path.read_text(encoding="utf-8"))
for pair in pairs:
    render = pair["render"]
    render = re.sub(r"/calibration(?=[-/])", "/dark", render)          # theme-surfaces/calibration/…, settings/calibration-0/…, task-4b-captures/calibration/…
    render = re.sub(r"/calibration-(\d+x\d+)\.png$", r"/dark-\1.png", render)  # workspace-surfaces/<surface>/calibration-WxH.png
    pair["render"] = render
path.write_text(json.dumps(pairs, indent=2) + "\n", encoding="utf-8")
PY
```

  The palette pair's render (`palette-surfaces/user_palette.toml-13.0-791x712.png`) is named by `CommandPalettePreview` after the theme; set it by hand to the Dark family's file of the same size (`ls jasper-app/build/palette-surfaces`). Then:

Run: `python3 -I tools/visual/compare_all.py jasper-app/build/visual-compare`
Expected: 34 lines, none `MISSING render`.

- [ ] **Step 3: Record.** In `acceptance.md`:
  - "How the evidence is made": renders are the real owners on **Slate Steel Dark** (and Light, inspected); the calibration themes are retired; the edge metric compares luminance edges, so mocks drawn in other palettes (the Vault mock's accent `#4676EE`) still compare; 34 pairs; the regeneration commands of Step 1.
  - A new table "Slate Steel (Plan 3)" with one row per pair: reference, surface, the last recorded agreement (calibration), the Slate Steel Dark agreement, and a note for any drop over 0.05 saying which colours differ from the mock (read the `-side.png` overlay).

- [ ] **Step 4: Inspect Light.** Open each family's Light render (one per surface) and its Dark render side by side; record in the same section any Light defect (illegible text, a role that does not flip, a surface with no edge). A defect found here is fixed at its owner with a test (ledger) or recorded as a user check.

- [ ] **Step 5: User-run checks.** "User-run checks still open" becomes: (1) compare each mocked page with its mock at 100% in Slate Steel Dark, (2) native behaviour with Dark, Light and Match System followed live (OS switch with Settings and a sheet open) and each terminal scheme including High Contrast (the band below the grid, the tab meeting a black pane), (3) the Dark active tab's title colour, (4) VoiceOver on the new descriptors, (5) Windows and Linux packaging and a native pass there.

- [ ] **Step 6: Commit**

```bash
git add tools/visual docs/design/theme-engine/acceptance.md
git commit -m "The mock comparisons on Slate Steel

All 34 recorded pairs are re-rendered on Slate Steel Dark and recorded beside
their calibration values; every family's Light render was inspected.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: Documentation and the full check

**Files:**
- Rewrite: `docs/themes.md`
- Modify: `README.md:9,115`, `docs/README.md:34`, `docs/configuration.md:179-180`, `config.example.toml:139,142` with `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java:248,253,275` (the glass descriptions' "workspace theme's value"), `docs/app-maintenance.md` (the `## Themes` section, lines 223, 243), `docs/app-architecture.md` (lines 176-177, 190, 224, 237-242, 254-255), `docs/ui-architecture.md` (lines 10-15, 49-50, 295), `docs/ui-maintenance.md` (lines 10-18, 30-32, 112-118, 166-169), `jasper-ui/README.md` (lines 7-9, 24, 47), `jasper-app/README.md:87-94`, `jasper-sdk/README.md:116`, `docs/plugin-ui.md` (line 14; an "alpha-carrying roles are painted as given" note near lines 188-196), `docs/sdk-architecture.md:120-121`, `docs/sdk-icons.md:15`, `docs/credential-vault.md:97`, `docs/command-palette.md:32`, `docs/packaging.md:301,337,353`, `docs/notifications.md:49`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/SemanticColor.java:5` and `jasper-ui/src/main/java/dev/jasper/ui/theme/SemanticColor.java:5` (the class JavaDoc: opacity carried by a role is painted as given; the kit's metrics add treatments such as focus rings and hovers)
- Modify: `docs/design/theme-engine/acceptance.md` (the Plan 1 section's before→after per bullet; a Plan 3 section), `docs/STATUS.md`, the spec's status line, this plan's status banner

- [ ] **Step 1: `docs/themes.md`**, whole file:

```markdown
# Themes

Jasper has one theme, **Slate Steel**, in Dark and Light, and three terminal colour schemes: **Slate Steel Dark**,
**Slate Steel Light** and **High Contrast**. Its colours are defined once, in
[the Slate Steel colour reference](design/theme-engine/slate-steel.md), and bundled as `slate-steel.json`; there are no
other themes, no themes folder and no theme import.

## Choosing the appearance and the terminal scheme

```toml
[ui]
appearance = "system" # or "dark", "light"

[terminal]
scheme = "match" # or "slate-steel-dark", "slate-steel-light", "high-contrast"
```

`ui.appearance` (`system`, `dark`, `light`) chooses Slate Steel Dark or Light, or follows the operating system;
`terminal.scheme` (`match`, `slate-steel-dark`, `slate-steel-light`, `high-contrast`) colours the terminal panes. With
`system`, Jasper follows the operating system's light or dark setting live and uses Dark when it cannot read it; an
operating-system change never writes `config.toml`. `match` uses Slate Steel Dark with the Dark appearance and Slate
Steel Light with the Light one.

Settings › Appearance & Behavior › Appearance has the Dark, Light and Match System tiles, the Theme row (Slate Steel)
and the **Terminal scheme** dropdown, whose first entry, **Same as UI theme**, is `match`. Terminal › Font & Colors
links there, and the command palette's **Themes…** opens it. Choices stay staged until Apply or OK.

## What the terminal scheme colours

A scheme colours only the terminal text area: background, text, bold text, cursor, selection and the 16 ANSI colours.
Tabs, the tab strip, the area around panes, the editor, panels and dialogs stay on the appearance. With `match` the
text area and the active tab meet seamlessly; with High Contrast the text area is black and the tab keeps the
appearance's terminal surface. Workspace opacity and blur apply to the terminal area. The editor always follows the
appearance, with the editor scheme derived from Slate Steel.

## The token rule

Every colour in Jasper's code is a semantic role (`surface.panel`, `text.primary`, `accent`, …) read from the active
variant; no component holds a colour value, and a test fails on any colour literal in production code or resources
outside `slate-steel.json`. A role that carries opacity (`surface.scrim`, `border.subtle`, `text.onAccent.secondary`)
is painted as given; treatments such as focus rings, hovers and tile tints are kit metrics applied to a role.
Shadows are black at the strengths the colour reference gives. Plugins receive the same roles through the SDK.

## When something is wrong

- An unknown `ui.appearance` or `terminal.scheme` value is reported ("Use …; using the default.") and uses the
  default (`system`, `match`).
- A removed setting (`[ui.theme]`, `terminal.colors`, `[colors]`) is reported as an unknown setting and ignored.
- If Slate Steel cannot be installed, start-up uses Dark and a reload keeps the current appearance; Reload config
  retries.

## Native acceptance for this upgrade (user-run)

On a Mac, check Dark, Light and Match System (switch the operating system's appearance with Settings and a sheet
open), each terminal scheme including High Contrast, and compare the mocked pages at 100%; the
[acceptance record](design/theme-engine/acceptance.md) lists every check.
```

- [ ] **Step 2: Every other document** in **Files**: remove theme authoring, import, the themes folder, the five old themes, IntelliJ themes, Alacritty themes, workspace schemes and the calibration themes; name `ui.appearance` and `terminal.scheme` (the sentence of Step 1's second paragraph) where a document describes theming; the glass descriptions say "Omit to keep the terminal area opaque" instead of "use the workspace theme's value" (update `CoreSettings`, `config.example.toml` and `docs/configuration.md` together, as `ConfigTemplateTest` checks the template). `docs/app-maintenance.md`'s `## Themes` heading stays (linked); its body describes `slate-steel.json`, `SlateSteel`, `UiColorKeys` and the colour-literal scan. `docs/plugin-ui.md` and both `SemanticColor` JavaDocs gain: "A role that carries opacity is painted as given; the kit applies treatments such as focus rings and hovers on top."
- [ ] **Step 3: The acceptance record.** In the "Slate Steel Plan 1" section, each bullet gains the role the component used before (from `git show cb2ccec9 6259aee2 7bd98915 da052684` and the Plan 1 plan's audit), as "(was `<role or token>`)". A "Slate Steel Plan 3" section lists what was removed, the scan, the comparisons (Task 9) and this plan's eleven decisions, verbatim.
- [ ] **Step 4: Status.** `docs/STATUS.md`, after the Plan 2 entry: "**Slate Steel Plan 3 (removal and verification) complete — <date>.** On `claude/slate-steel`: the legacy theme pipeline, the old themes, theme import, the vendored theme sources and their tools are gone; tests run on a Slate Steel helper; FlatLaf's keys come from the roles; a scan pins that no colour value lives outside `slate-steel.json`; the 34 mock comparisons are re-rendered on Slate Steel Dark (Light inspected); the deferred review findings are fixed. The native pass is the user's (acceptance record)." The spec's status line appends "Plan 3 (removal and verification) complete on <date>."; this plan gains a status banner with every deviation.
- [ ] **Step 5: Source hygiene** — the AGENTS.md Python check over `jasper-ui/src`, `jasper-app/src`, `jasper-terminal/src`, `jasper-buddy/src`, `jasper-sdk/src`, `jasper-sdk-testkit/src` and `plugins`.
Expected: no output.
- [ ] **Step 6: Full check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL; read failures from `*/build/test-results/test/*.xml` and fix each at its owner.

- [ ] **Step 7: Commit**

```bash
git add -A docs README.md config.example.toml jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java jasper-ui/README.md jasper-app/README.md jasper-sdk jasper-ui/src/main/java/dev/jasper/ui/theme/SemanticColor.java
git commit -m "Slate Steel Plan 3: documentation and verification

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
