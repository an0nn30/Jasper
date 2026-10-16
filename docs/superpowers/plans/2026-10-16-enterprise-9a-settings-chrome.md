# Enterprise presentation 9a: the Settings chrome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Under the Enterprise look the Settings dialog wears its classic chrome, measured against the eighteen
`05 Settings` mocks: a native "Settings" title, a search strip over a collapsible navigation with ▼ sections and a
square full-width selection, a ruled header and a footer band, Reset to Default in each row's context menu, the rows the
look implies hidden, and the look's font row copy; the default look stays byte-identical.

**Architecture:** Plan 5a's `SettingsChrome` and `Composition` projections land (`Presentation.Landed.SETTINGS`) and reach
the dialog through `SettingsDialog.Hooks` and every page through `PageContext`; only the six composition roots of spec 9.1
may read `Composition` (`CompositionReadTest`), and the compositions themselves are plan 9b's. The dialog turns
`SettingsChrome` into values for jasper-ui: a `SettingsShell.Frame` (whether the header row is the window's title row,
whether the navigation collapses), a row-menu on `Nodes.SettingRow` (`contextActions`, jasper-ui model only), and the
classic catalog's Settings roles, which plan 9a measures from the mocks and adds to `DesignMetrics.CLASSIC`. A headless
fixture (`EnterpriseSettings`) opens the real dialog under the shipped kit, Plex and the Enterprise theme over the bundled
plugins, and `SettingsChromeMockTest` compares its 2x renders with each mock's sidebar, header and footer.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, the JDK class-file API (`java.lang.classfile`),
Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ 3.27.7, Python 3.11+ standard library for the mechanical edit scripts
(always `python3 -I`), Pillow for the measurements recorded below (already taken; tasks need no Pillow).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 9.1, 9.2, 12.1, 12.2's "Interface font", 16 and 19.1 row 9; 5.6 for the unset `ui.font.size`; 5.7 for the
`navigation_surface` slot; 7.1 for `surface.footer` and `border.row`), with
[DESIGN_LANGUAGE.md](../../design/enterprise/reference/DESIGN_LANGUAGE.md) and the mocks in
[`docs/design/enterprise/reference/05 Settings/`](../../design/enterprise/reference/05%20Settings/). Spec row 9 is
delivered as two plans: this one (9a, the chrome: spec 12.1, the hidden rows, the font row copy and the chrome renders)
and [9b](2026-10-16-enterprise-9b-settings-pages.md) (spec 12.2's compositions: Appearance, Notifications, Keymap with its
recording band, Plugins, Shell, and the full-page renders).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-9`, branch
`claude/enterprise-settings`, cut from `main` at `deafed424` and holding only this plan's and 9b's files until it
executes. Run every command from the worktree root, never in `~/projects/moray`. Line numbers below are from `deafed424`
and move with the earlier plans; every edit quotes the exact text it replaces, and the edit scripts stop with a message
when that text is not found the stated number of times. If a script stops, re-find the quoted text (it moves, it does not
change), adjust the anchor, re-run, and record the re-anchoring in this plan's status banner; never edit around a missing
anchor. Plan 9b executes on the same branch after this plan.

## Before you start

Plans 1 to 8 execute before this plan (spec 19.1: "1 to 11 in sequence"). Bring the branch up to `main` and check that
every plan this one builds on is in it:

```bash
git rebase main
grep -c 'record SettingsChrome(' jasper-app/src/main/java/dev/jasper/app/presentation/SettingsChrome.java      # 5a: 1
grep -c 'enum Composition' jasper-app/src/main/java/dev/jasper/app/presentation/Composition.java               # 5a: 1
grep -c 'public static final Set<Landed> LANDED' jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java  # 5a: 1
grep -c 'public <T> T defaultValue(Setting<T> setting)' jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsDraft.java  # 5a: 1
grep -c 'namesUiFaces' jasper-ui/src/main/java/dev/jasper/ui/metrics/FaceSet.java                              # 5b: at least 1
ls jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseResources.java \
   jasper-app/src/main/java/dev/jasper/app/application/LookFonts.java \
   jasper-app/src/main/java/dev/jasper/app/appearance/PluginThemes.java \
   plugins/enterprise/src/main/resources/themes/enterprise.json                                                # 5b: all exist
ls jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicTreeFace.java jasper-ui/src/main/java/dev/jasper/ui/kit/ColorSlots.java  # 4a, 4c
grep -c 'GROUP_ROW_INDENT' jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java                     # 4c: at least 1
grep -c 'boolean disclosure;' jasper-ui/src/main/java/dev/jasper/ui/swing/Buttons.java                         # 4c: 1
grep -c 'TableRow heading(' jasper-ui/src/main/java/dev/jasper/ui/model/TableRow.java                          # 3c: 1
grep -o 'Landed\.[A-Z_]*' jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java | sort -u     # 6-8: WINDOW_CHROME, DOCK_CHROME, RUN_WINDOW
ls jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java jasper-app/src/test/java/dev/jasper/app/testsupport/MockProfile.java \
   jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java                                     # 6a: all exist
grep -c 'DialogMapping dialogs) {' jasper-app/src/main/java/dev/jasper/app/windows/AuxiliaryWindows.java       # 6b: 1
```

If a check prints 0, a file is missing, or the last line lacks one of the three groups, stop: this plan needs those plans
on `main`. Plans 6 to 8 were written at the same time as this plan; before Task 1, read their merged code for
`Presentation.LANDED`, `ProjectionsTest`, `DesignMetrics.Role`'s last constants, `NewRolesTest.ADDED`,
`ClassicCatalogTest.theSpecsNamedValuesHold`, `classic-roles.tsv`'s last rows and `tools/visual/pairs.json`, which they
extend as this plan does; the scripts below find those by pattern, not by line. The test support this plan builds on
is plan 6a's `testsupport.EnterpriseLook` (the shipped kit with Plex registered, `controller(dark)`, `restoreDefault()`)
and `testsupport.MockProfile` (`mock`, `deltaE`, `print`, `write`), and 6a's `workspace.ChromeRenderTest` (`cases()`,
`Case`), which Task 7 extends. Task 6's whole-area comparison is this plan's own package-private
`settingsui.SettingsMockMatch` (an alignment search no earlier helper has), built on `MockProfile`; plan 10a's
`testsupport.MockMatch` is a different class, so the two never share a path.

Then record the pixel base before anything changes (the headless gallery and the opt-in headless Settings preview; both
set `java.awt.headless=true` and open no window):

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
rm -rf build/plan9-base && mkdir -p build/plan9-base
cp -R jasper-ui/build/gallery/dark-1x jasper-ui/build/gallery/light-1x build/plan9-base/
cp -R jasper-app/build/settings-preview build/plan9-base/settings-preview
ls build/plan9-base/settings-preview | grep -c '\.png$'
```

Expected: the gallery prints "Rendered N fixtures …" twice; the last line prints the preview's PNG count (more than 30).
Every later "pixel proof" step compares with this base with the script below, saved once as
`build/plan9-base/compare.py` (an untracked build file):

```bash
cat > build/plan9-base/compare.py <<'PY'
"""Plan 9 pixel proof: the default look's gallery and Settings preview are byte-identical to build/plan9-base."""
import hashlib, pathlib, sys
def digests(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for base, now in [("build/plan9-base/dark-1x", "jasper-ui/build/gallery/dark-1x"), ("build/plan9-base/light-1x", "jasper-ui/build/gallery/light-1x"),
                  ("build/plan9-base/settings-preview", "jasper-app/build/settings-preview")]:
    a, b = digests(pathlib.Path(base)), digests(pathlib.Path(now))
    bad += [f"{now}/{name}" for name in sorted(set(a) | set(b)) if a.get(name) != b.get(name)]
print("changed:", bad if bad else "none")
sys.exit(1 if bad else 0)
PY
```

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md); no task runs `packageDist`, `trainAotCache` or `-Pjasper.aot=true`. `:jasper-ui:gallery` and `:jasper-app:settingsPreview` are headless and may be run; the JUnit renders are headless.
- Work only in the worktree above on `claude/enterprise-settings`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: the default look does not change. Every file the headless gallery writes for Slate Cobalt Dark and Light and every file `:jasper-app:settingsPreview` writes (the real Settings dialog under the default look, dark and light, at the default and an 18 pt interface font) is byte-identical to `build/plan9-base` (the compare script above). No existing render may change; the only new images are this plan's own under `jasper-app/build/enterprise-settings/`, `jasper-ui/build/classic-faces/`, `jasper-app/build/settings-mock/` and the `settings-*` prints in `jasper-app/build/chrome-renders/` (inspection output, not committed). Tasks 3, 4, 5, 6 and 8 prove it.
- `SettingsChrome` changes values only (spec 12.1); renderers never branch on a look (spec 3.6). jasper-ui receives values (`SettingsShell.Frame`, `SettingRow.contextActions`, catalog roles), never `SettingsChrome`, `Composition` or `ChromeSpec`. Only the composition roots of spec 9.1 read `Composition` (`CompositionReadTest`, Task 1); this plan adds no reader.
- Colours come from the theme only (`ColourLiteralScanTest`): the frame paints semantic roles, the kit's two colour slots and spec 7.1's derived roles; the mock hex values below are test targets, never source literals in main code. Glyphs (the ▼/▶ triangles) are plan 4c's drawn shapes.
- New metric roles are appended at the end of `DesignMetrics.Role` (after every role plans 4 to 8 added), with their rows appended to `compact-roles.tsv` and `classic-roles.tsv`, and their names appended to `NewRolesTest.ADDED`; a classic value of an existing role is a `values.put` line in `DesignMetrics.CLASSIC` and the matching row of `classic-roles.tsv`, nothing else.
- Javadoc doclint fails the build on errors: jasper-app runs `-Xdoclint:all`, jasper-ui `-Xdoclint:all,-missing`; every `{@link}` must resolve; no comment may contain a raw `<`, `>`, `&` or `->`. New public types and members carry comments, and record components an `@param`.
- `docs/design/theme-engine/migration.csv`: every new source under jasper-app, the SDK, the testkit or plugins that `ThemeMigrationCoverageTest.CANDIDATE` matches gets its row in the same commit. A row has exactly eight comma-separated fields (`path,package,Class,nonvisual-helper,none,none,evidence,disposition`); a disposition begins "New in Enterprise plan 9a Task N" ("Test/fixture ownership; New in …" for a test) and uses semicolons, never commas. Each task lists its rows; each task's test run includes `ThemeMigrationCoverageTest`, which names any row still missing.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file; `…`, `▶`, `▼`, `›` and `·` are ordinary characters.
- The identifier word "hero" stays reserved for `Form.hero` (3a's `PromptVocabularyTest`).

## Review Focus

1. A modified setting under Enterprise, reached by mouse or keyboard: right-click on the row (or Shift+F10 or the menu key
   while a control in it has focus) offers "Reset to Default", enabled only while the value differs from its effective
   default; choosing it resets the row and its companions (the Appearance themes), and an unmodified row's item is
   disabled. Task 3: `ResetMenuTest` (four cases) and `SettingRowMenuTest.theKeyboardOpensTheRowsMenu`.
2. A user collapses the section that holds the page being shown, then goes Back, Forward or searches into it: the
   section opens again for a page navigated to, a search shows every section while it runs and restores the collapsed
   ones when cleared, and the next opening of Settings starts with every section open. Task 5:
   `SettingsNavigationTest.navigatingIntoACollapsedSectionOpensIt`, `aSearchOpensEverySectionUntilItIsCleared` and
   `theStateIsNotSaved`.
3. Enterprise on Windows or Linux, where "Use the system title bar" is shown under the default look: under Enterprise it
   and "Enable animations" are not built, and the Animations section leaves no empty header behind; the default look on
   Linux still shows the title-bar row. Task 2: `LookRowsTest`.
4. A large interface font or a narrow dialog under the classic frame: the search strip grows with the field, the
   navigation starts below its rule, the header row keeps its rule under taller buttons, and the footer keeps its
   buttons inside the band. Task 4: `SettingsShellFrameTest.theFrameFollowsATallerSearchFieldAndHeader`.
5. A theme whose window border all but disappears on its surface (Catppuccin Latte's `border.window` on `surface.app`,
   1.17:1, and the degenerate palettes): the classic frame's rules stay visible through the structural guard. Task 4:
   `SettingsShellFrameTest.theRulesMeetTheirGuardOverEveryPalette`.

## Rulings

Decisions where the spec is silent or a literal reading cannot be built, taken on the user's behalf (2026-10-16):
"what — why — cost if wrong". Record them in `docs/STATUS.md` (Task 8) and in this plan's status banner.

1. **Spec row 9 is two plans: 9a the chrome, 9b the compositions** — the row holds the shell, its navigation, the row
   menu, the hidden rows, five page compositions and eighteen mock comparisons, more than fourteen reviewable tasks —
   cost if wrong: one more STATUS entry.
2. **`Landed.SETTINGS` lands in 9a's first task, so between 9a and 9b Enterprise shows the classic chrome over today's
   page compositions** (`Composition.CLASSIC` reaches pages that do not read it yet) — each plan stays a runnable
   deliverable — cost if wrong: none; 9b follows on the same branch.
3. **The projections reach Settings through `SettingsDialog.Hooks` (`chrome`, `composition`) and `PageContext` (both);
   the former constructors mean the default look on this host, `Projections.standard(Host)`** — spec 9.1 hands each
   consumer its projection, pages are made from a `PageContext`, and tests and previews build both without a
   presentation (5a's core-look conveniences, its Rulings 10) — cost if wrong: two components.
4. **Reading `Composition` means naming one of its fields or methods (`Composition.CLASSIC`, `values()`, a switch's
   map); carrying it as a component or parameter is not a read** — spec 16.1.7 confines the reads to the listed roots,
   and `PageContext` and `SettingsDialog` only pass it — cost if wrong: none.
5. **The frame is a value of jasper-ui, `SettingsShell.Frame(integratedTitle, collapsibleNavigation)`; a frame that is
   not the window's title row draws the classic dialog: a search strip on `surface.sidebar` ruled at its bottom, the
   navigation on the `navigation_surface` slot, `border.window` rules beside the navigation and under the header, and a
   footer band on `surface.footer` under a `border.window` rule; the integrated frame draws exactly today's shell** —
   spec 12.1 ("a native title bar", "the footer sits on `surface.footer`") and the mocks; the default look keeps its
   integrated frame, so its pixels cannot move — cost if wrong: a fallback look with a native title also shows the
   footer band.
6. **Both frames paint the body on the `dialog_surface` slot and the navigation on the `navigation_surface` slot** —
   spec 5.7; the default slots are today's `surface.dialog` and `surface.sidebar` — cost if wrong: none.
7. **The search strip's rule is `border.row`, unguarded; the other three rules are `border.window` through the
   structural guard** — the mock draws the strip rule #DDE0E4 (`border.row` #E4E5E7, ΔE76 ≈ 3) and the window rules
   #C4C7CC; `border.row` is spec 7.1's faint divider, which a 1.3 floor would darken past the mock — cost if wrong: a
   faint rule that vanishes on a degenerate palette.
8. **The row menu is jasper-ui's model only: `Nodes.SettingRow` gains `contextActions`; the SDK's `SettingRow` does not,
   so a plugin's own composed page (`settings.page(...)` with host rows, `KitSettingRows`) keeps its inline Reset under
   every look, while generated plugin pages (`SchemaPage`) take the menu** — the SDK is frozen at 0.18's additions (spec
   8.2, 8.4) — cost if wrong: Sample's own page shows a Reset icon beside a modified row under Enterprise.
9. **"Reset to Default" is always offered under the classic rows and is disabled while the value is at its default; it
   opens on the row's label, description and background by the platform's popup trigger, and on Shift+F10 or the menu
   key while focus is inside the row** — IntelliJ's behaviour for its settings rows; a row's control keeps its own mouse
   behaviour — cost if wrong: one menu entry.
10. **A row without a Reset button has no reserved gap after its control** (`SettingsTrailingLayout` keeps
    `COLUMN_GAP` only between a control and its action) — the mock's dropdowns, steppers and buttons end at the box's
    inner padding (dropdown right edge 12 from the box at 2x 2024 against 2048) — cost if wrong: none; the default look
    always has the action.
11. **The font row copy is `SettingsChrome.fontRowCopy()` for the row title and its toggle's accessible name ("Use the
    default size" beside "Use platform size"); the first untick shows the stepper at the look's standard size, the
    kit's body size when it names a UI face (12 under Enterprise) and the setting's example (14) otherwise** — spec 12.1
    and 12.2; the default look keeps 14 — cost if wrong: a stepper starting one value off.
12. **The hidden keys replace `.shownWhen(!SystemInfo.isMacOS)` on `window.native_decorations`** — spec 12.1 says the
    default look hides it on macOS "as today", which `Projections` already computes; one mechanism — cost if wrong:
    none.
13. **The classic navigation is measured, not the classic tree face's**: rows 22 from 4 below the strip rule, a 9 px
    side, a 6 px triangle with 6 px to the section title, children 15 px deeper than their section's glyph, no group
    gaps, section titles 12/600 in `text.strong.secondary`, leaves 12/400 in `text.primary`, the selection the full
    sidebar width; the triangle and its colour are plan 4c's tree face — the Settings navigation's geometry differs from
    the Keymap tree's (text 12.5 after the glyph start against 16), and spec 12.1 gives it its own metrics — cost if
    wrong: one role per value.
14. **The collapsed sections live as long as one opening; a search shows every section while its query is not blank;
    selecting a page inside a collapsed section (by Back, Forward, a link or a search) opens its sections; Left and Right
    close and open the focused section** — spec 12.1 "the state is not saved", IntelliJ's tree — cost if wrong: none.
15. **The classic frame's own roles are classic values of existing Settings roles plus `SETTINGS_HEADER_INSET` (header
    to the body's edge, 20 by default, which is today's `SETTINGS_PAGE_INSET` there), `SETTINGS_NAV_CHILD_INDENT` (0 by
    default) and `SETTINGS_NAV_TEXT_GAP` (6 in both catalogs)**; the classic `CIRCULAR_ACTION_SIZE` 22 also sizes every
    other classic HELP button — the mocks' "?" is 22 — cost if wrong: one role.
16. **The page's titled boxes fill the body (classic `SETTINGS_CARD_WIDTH` 4000, page inset 16); `SETTINGS_PAGE_TOP`,
    `SETTINGS_SECTION_GAP` and `SETTINGS_CRUMB_GAP` are calibrated in Task 6 against the measured targets, starting at
    10, 13 and 6** — their visible result depends on 4b's titled box and Plex's line height, which only the full render
    shows — cost if wrong: a value edited in Task 6 with its two table rows.
17. **The mock comparison asserts its probes (rule positions, surfaces, the selected row) on every OS and its pixel
    shares on macOS only** — the mocks were drawn on macOS, Linux and Windows rasterize Plex differently, and the
    Workspace rows exist only on macOS (`CorePages`' `glass`) — cost if wrong: a Linux-only drift is seen only by its
    probes.
18. **The renders use the mocks' content sizes (1040 wide, each mock's height less its title bar) and fixture data close
    to the mocks** (a user plugin "THD Sourcing Crypto", a light theme labelled "Slate Daylight", the visible values of
    each page); the residuals are fixtures, recorded in Task 6's banner: the bundled Enterprise row on Plugins, "Use the
    default font size" where the mock says "Use platform font size" (spec 12.1 wins), and the View mode box under
    Appearance (spec 12.2 "View mode stays") — spec 1: mock data are fixtures — cost if wrong: none.
19. **The dialog's size stays 1000 × 700** — the mocks' heights are fixtures (691 to 949) — cost if wrong: one
    `Dimension`.

## Verified facts

Read at `deafed424`; facts about plans 3b to 8 cite the plan.

1. `SettingsDialog.Hooks` is an 8-component record (`SettingsDialog.java:54-62`); `open` builds the draft, the view, the
   pages' `PageContext` (`:163-169`), then `windows.modalDialog("app.settings", "Settings", new Dimension(1000, 700),
   ownerOrNull)` and `surface.integratedTitle(view::titleRowHeight)` (`:174-177`); `event` routes search, `PageTree`
   selections and the header and footer actions (`:190-209`); `show` puts the page, the breadcrumb and the tree
   selection (`:252-262`). Plan 5a Task 7 turns `:164` into `new SettingsDraft(state.values(), state.defaults())`.
2. `PageContext` is a 7-component record with 6- and 5-argument conveniences and `withDraft` (`PageContext.java:23-46`);
   13 `new PageContext(` calls exist, ten in tests (`grep -rn 'new PageContext(' jasper-app plugins`).
3. `SettingsView` builds the search, the navigation (a `SETTINGS_NAVIGATION` stack around `PageTree.node()`), the header
   (back and forward in `JOINED_ACTIONS`, the parent crumb as DESCRIPTION, a chevron icon, the page as WINDOW_TITLE), the
   banner and the footer (HELP, the message line, Cancel, Apply, OK in `SETTINGS_FOOTER`) and hands them to
   `new SettingsShell(context, search, navigation, header, banner, body, footer)` (`SettingsView.java:43-61`, `:100-137`).
4. `SettingsShell.doLayout` puts the search at `(inset, titleRowHeight)`, the navigation `SETTINGS_SEARCH_GAP` below it,
   the header at `left + SETTINGS_PAGE_INSET`, the body at `left`, the footer `SETTINGS_FOOTER_SIDE` in; `paintComponent`
   fills `SURFACE_DIALOG`, then `SURFACE_SIDEBAR` over the sidebar and the footer, and draws three `BORDER_SUBTLE` lines
   (`SettingsShell.java:35-63`).
5. `ViewReconciler.applySettingsHeader` gives the header's DESCRIPTION text the `SETTINGS_BODY` override
   (`ViewReconciler.java:168-174`), so the parent crumb is 12/400 under `TypeScale.CLASSIC`; `applySettingsNavigation`
   sets `tree.settingsNavigation` and the gutter `SETTINGS_NAV_INSET` (`:175-180`); `popupOpen` lists the controls whose
   popups are open (`:359-365`).
6. `KitView.settingsChrome(boolean)` is the kit-internal flag `SettingsShell` sets on its views (`KitView.java:130-141`).
7. `PageTree.node()` opens every branch (`PageTree.java:83-90`); a `TreeItem` carries no icon in the navigation
   (`:76-81`).
8. `Trees.TreeControl`: navigation rows have no disclosure (`Trees.java:308`, `!settingsNavigation`), `expandFocused`
   returns in the navigation (`:291-297`), navigation and rail rows have indent 0 (`:306`), a heading's height is
   `SETTINGS_NAV_HEADING` with `SECTION_LABEL` (`:177`), group gaps come from `SETTINGS_NAV_HEADING_GAP` and
   `SETTINGS_NAV_GROUP_GAP` (`:163-169`), leaf titles take the `SETTINGS_BODY` override (`:346-350`). Plan 4c Task 1 adds
   `context().faces().tree.box(context())` as the pane's border, the tree face's `indent` and `disclosure()` and the
   `styleDisclosure` call after `row.slot(row.leading, leading);`, and `Buttons.KitButton.disclosure` with its preferred
   size `glyph + DISCLOSURE_GAP − ICON_GAP` (4c, Task 1 Step 4).
9. `InteractiveRows.Row.doLayout` uses `SETTINGS_NAV_ROW_SIDE` as a navigation row's side and as the gap after its
   leading slot (`InteractiveRows.java:1096`, `:1109`); `paintFill` fills a selected or hovered row across the canvas
   width (`:818-828`).
10. `SchemaPage.row` builds each ordinary row's `SETTINGS_TRAILING` stack of control and Reset (`SchemaPage.java:335-343`),
    `externalNode` likewise (`:358-369`); `event` resets a row whose `resetId()` (`<setting>:reset`) is the action
    (`:398-412`), which also serves full-span editors whose own Reset uses `<id>:reset`; built rows are those with
    `row.shown()` (`:226`, `:248`, `:271`, `:292`).
11. `Stacks.SettingsTrailingLayout` keeps `COLUMN_GAP` before the second child even when there is none
    (`Stacks.java:474-484`); `SettingsFooterLayout` gives each trailing button at least `SETTINGS_ACTION_MIN_WIDTH`, `BUTTON_GAP`
    apart (`:400-425`); `SettingsHeaderLayout`'s gaps are `SETTINGS_HEADER_GAP` after the history buttons,
    `SETTINGS_HEADER_TITLE_GAP` for a page without ancestors and `SETTINGS_CRUMB_GAP` between crumbs (`:432-437`);
    `SettingsLayout` places the page's sections `SETTINGS_PAGE_INSET` in, `SETTINGS_PAGE_TOP` down, at most
    `SETTINGS_CARD_WIDTH` wide, `SETTINGS_SECTION_GAP` apart (`:498-529`).
12. `Nodes.SettingRow` (jasper-ui model) has 8 components and two conveniences (`Nodes.java:418-431`); `KitValues`
    projects the SDK's row with the 8-component form (`jasper-app/.../plugins/KitValues.java:23`).
13. `BreadcrumbControls` builds a crumb's own menu from `MenuItem`s into a `Menus.Popup` with `Menus.Item.describe` and
    reports `UiEvent.Action(item.action())` (`BreadcrumbControls.java:216-243`); the popup is registered as
    `KitOwnership.CONTEXT_MENU` (`:59-61`).
14. Full-span editors build their own rows and Reset: `AppearanceSelection` (`:58-75`, Reset `<id>:reset` and an invisible
    lane), `PlatformFontSize` (`:17-22`), `NotificationGroupsEditor` (`:85-87`, `resetId(id, i)`), `WorkspaceEditors.Opacity`
    (`:27-30`, invisible reserved buttons); plugin composed pages' host rows are SDK rows (`KitSettingRows.java:79-84`).
15. `CorePages` shows `window.native_decorations` with `.shownWhen(!com.formdev.flatlaf.util.SystemInfo.isMacOS)`
    (`CorePages.java:124`) and `ui.animations.enabled` in its own Animations section (`:108-109`).
16. `PlatformFontSize` starts its stepper at `UI_FONT_SIZE.example()` (14) and labels the row "Use platform font size"
    and the toggle "Use platform size" (`PlatformFontSize.java:12-22`); plan 5a Task 7 gives it the draft and compares with
    `Editors.effectiveDefault(draft,UI_FONT_SIZE)`, the exact text 5a's `DefaultsPrecedenceTest` reads in the file.
17. Plan 5a Task 8: `Projections.of(chrome, classicBase, namesUiFace, providerId, host, landed)` computes
    `SettingsChrome(integratedTitle = titleBar != NATIVE, collapsibleNav = classic, inlineReset = !classic, hiddenKeys,
    fontRowCopy)` and `Composition`; `Presentation.LANDED` gates the SETTINGS group; `ProjectionsTest` pins it.
18. Plan 4a: `DesignMetrics.CLASSIC` is built by `values.put(Role.X, n)` lines ending with
    `values.put(Role.THEME_PREVIEW_CAPTION_GAP, 8);`; `SETTINGS_NAV_ROW` and `SETTINGS_NAV_HEADING` are 22 and
    `SETTINGS_SIDEBAR_WIDTH` 230 there; `NewRolesTest.ADDED` lists the added roles, which close the enum in order, and
    `ClassicCatalogTest.theSpecsNamedValuesHold` requires each to equal its default except `ROW_HEIGHT` and
    `FIELD_RADIUS`; `ColorSlots(dialogSurface, navigationSurface)` is `Kit.colors()` (4a Task 4).
19. Plan 4b: `ThemePainting.role(KitContext, ColorRole)` and `ThemePainting.painted(KitContext, PaintPair)` (4b Task 1).
20. Plan 5b: `EnterpriseResources.facts().parsed()` is the shipped kit through the production parser, `theme()` the
    Enterprise `JasperTheme`, `LookFonts.register(KitParser.Faces)` (package-private in `dev.jasper.app.application`)
    registers Plex and returns its problems, `PluginThemes(provided, reserved, dormant, problems)` with
    `PluginThemes.Provided(theme, pluginName)` and `ThemeLibrary.of(PluginThemes)`; `FaceSet.namesUiFaces()` (5b Tasks
    1 to 4).
21. `SettingsDialogPreview` and `Settings9bPreview` open the real dialog headless over `AuxiliaryWindows` with a shell that
    hands back `surface.holder()`, a real `PluginRuntime` over `jasper.stagedPlugins`, `PluginManager` and `KeymapPage`,
    and paint the holder at 2x with `printAll` (`SettingsDialogPreview.java:45-145`, `Settings9bPreview.java:28-60`,
    `SettingsSurfacePreview.java:96-118`); jasper-app's test task sets `jasper.stagedPlugins`, `jasper.repoRoot` and
    `jasper.appJar` (`jasper-app/build.gradle.kts:83-98`).
22. `StaticDefaultTest.classFiles(Path jar, int minimum)` reads a jar's classes (`StaticDefaultTest.java:154`).

### Mock measurements

Measured with Pillow (`python3 -I`) on the eighteen `05 Settings` PNGs (2084 wide, 2x) and `07b`. Every mock draws a
1 px window edge (#A9ADB3) and the native title bar (2x rows 2 to 57, its rule in rows 58 and 59); the dialog's content
starts at 2x (2, 60). Values below are logical pixels from the content's top-left; colours are Enterprise Light's.

| Measured | Where | Value | Role or rule |
|---|---|---|---|
| Content size | every mock | 1040 wide; 691 tall (Notifications 827, Keymap recording 713, Editor 731, Project Index 949) | render sizes (Rulings 18) |
| Sidebar | every mock | 230 wide, then a 1 px rule #C4C7CC to the footer; the body starts at 231 | `SETTINGS_SIDEBAR_WIDTH` 230, `border.window` |
| Search strip | every mock | #F7F7F8, 0 to 38; the field at (8, 8), 214 × 22, #FFFFFF in #B4B8BF; a 1 px rule #DDE0E4 at 38 | `surface.sidebar`, `SETTINGS_SIDEBAR_INSET` 8, `border.row` (Rulings 7) |
| Navigation | every mock | #FFFFFF from 39; row k at 43 + 22k, 22 tall | `navigation_surface` = `surface.card`, `SETTINGS_SEARCH_GAP` 4, `SETTINGS_NAV_ROW` 22 |
| Section row | "Appearance & Behavior" | ▼ 6 × 6.5 #5C6370 at x 8.5; title at 21, #3A3D42, semibold | `SETTINGS_NAV_ROW_SIDE` 9, `DISCLOSURE_GLYPH` 6, `SETTINGS_NAV_TEXT_GAP` 6, `text.strong.secondary` |
| Leaves | "Appearance"; "Keymap" | text at 24 under a section, at 9 at the top level, #1E1F22 | `SETTINGS_NAV_CHILD_INDENT` 15 |
| Selection | every mock | #2F65CA across 0 to 230, square, white text | `SETTINGS_NAV_INSET` 0, `selection.bg` |
| Header | every mock | #ECEDEF, 34 tall, a 1 px rule #C4C7CC at 34 from the body's left edge to the right | `SETTINGS_HEADER_HEIGHT` 34, `border.window` |
| History buttons | every mock | 51 × 24 joined pair at body x 14, 5 from the top; back #1E1F22 on the classic gradient, forward disabled #A3A8B0 on #F0F1F3 | `SETTINGS_HEADER_INSET` 14, `JOINED_ACTION_HEIGHT` 24 |
| Breadcrumb | Appearance; Keymap | parent at body x 75 (#5C6370, 12/400), › #9AA0A8 at 212 to 214.5, page at 225 (#1E1F22, 12/600); a lone page title at 96 | `SETTINGS_HEADER_GAP` 10, `SETTINGS_CRUMB_GAP` (calibrated), `SETTINGS_HEADER_TITLE_GAP` 31 |
| Footer | every mock | a 1 px rule #C4C7CC at height − 45, then #E6E7EA 44 tall | `SETTINGS_FOOTER_HEIGHT` 45, `surface.footer` (#E6E7E9 by recipe) |
| Footer buttons | every mock | Cancel, Apply, OK 98 × 24, 6 apart, 14 from the right, 10 below the rule; "?" a 22 circle at x 14 | `SETTINGS_ACTION_MIN_WIDTH` 98, `BUTTON_GAP` 6, `SETTINGS_FOOTER_SIDE` 14, `CIRCULAR_ACTION_SIZE` 22 |
| Titled boxes | Command Palette, Appearance | box edges 16 from the body's sides; the first box's top border 18 below the header rule; border to border 21 | `SETTINGS_PAGE_INSET` 16, `SETTINGS_CARD_WIDTH` fill, `SETTINGS_PAGE_TOP`, `SETTINGS_SECTION_GAP` (calibrated) |
| Trailing controls | Appearance dropdowns, Window spinners | the control ends 12 inside the box (no reserved Reset gap); dropdowns 220 wide | Rulings 10, `SETTINGS_CHOICE_WIDTH` 220 |
| No Reset | every mock | no row shows a Reset icon | Rulings 9 |

## File structure

- `jasper-app/src/main/java/dev/jasper/app/presentation/`: `Presentation.java` (SETTINGS lands), `Projections.java`
  (`standard(Host)`).
- `jasper-app/src/main/java/dev/jasper/app/settingsui/`: `PageContext` (chrome and composition), `SettingsDialog` (hooks,
  frame, navigation events), `SettingsView` (frame), `PageTree` (collapsible), `SchemaPage` (hidden rows, reset menu,
  `resetMenu`), `CorePages` (the title-bar row), `PlatformFontSize` (copy, start, menu), `AppearanceSelection`,
  `NotificationGroupsEditor`, `WorkspaceEditors`, `Editors` (menus), `package-info.java`.
- `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`: the presentation's projections into Settings.
- `jasper-app/src/main/java/dev/jasper/app/pluginmanager/package-info.java`, `shortcuthelp/package-info.java`: the edge.
- `jasper-ui/src/main/java/dev/jasper/ui/`: `model/Nodes.java` (`SettingRow.contextActions`), `swing/SettingRows.java`
  (the row menu), `swing/Stacks.java` (trailing gap), `swing/ViewReconciler.java` (popup, navigation flags),
  `swing/SettingsShell.java` (`Frame`, the classic frame), `swing/KitView.java` (`collapsibleNavigation`),
  `swing/Trees.java`, `swing/Buttons.java`, `swing/InteractiveRows.java` (the collapsible navigation),
  `metrics/DesignMetrics.java` (three roles, the classic Settings values).
- Tests: `jasper-app/src/test/java/dev/jasper/app/` `architecture/CompositionReadTest`, `presentation/ProjectionsTest`,
  `settingsui/SettingsChromeTest`, `LookRowsTest`, `ResetMenuTest`, `SettingsNavigationTest`, `EnterpriseSettings`
  (fixture), `SettingsMockMatch`, `SettingsChromeMockTest`, `SettingsFrameRenders` (bridge); 6a's
  `workspace/ChromeRenderTest` and `appearance/PaintedContrastTest` (extended);
  `jasper-ui/src/test/java/dev/jasper/ui/swing/` `SettingRowMenuTest`, `SettingsShellFrameTest`,
  `CollapsibleNavigationTest`; frozen tables `metrics/compact-roles.tsv`, `metrics/classic-roles.tsv`; `NewRolesTest`,
  `ClassicCatalogTest`.
- Tools: `tools/visual/pairs.json` (eighteen `enterprise-settings-*` pairs; the existing `settings-*` pairs are the default look's).

---

### Task 1: The Settings projections land and reach the dialog and its pages

Plan 5a computed `SettingsChrome` and `Composition` but held them at the default until this plan; `Landed.SETTINGS`
lets them follow the kit file. The dialog takes both in its hooks and gives both to every page; `JasperApplication`
passes the presentation's. `Projections.standard(Host)` is the default look's value for everything made without a
presentation. `CompositionReadTest` keeps `Composition` reads in spec 9.1's six roots.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java` (`LANDED`), `Projections.java` (`standard`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/PageContext.java:12-50`, `SettingsDialog.java:42-62,168-169`, `package-info.java:4-5`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:973-975`
- Modify: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/package-info.java`, `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/package-info.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/architecture/CompositionReadTest.java`, `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsChromeTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (rows for the two new tests)

**Interfaces:**
- Consumes: plan 5a's `SettingsChrome`, `Composition`, `Projections.of`, `Projections.Host`, `Presentation.Landed`,
  `JasperApplication.presentation()`.
- Produces: `public static Projections Projections.standard(Projections.Host host)`; `Presentation.LANDED` containing
  `Landed.SETTINGS`; `PageContext(SettingsDraft, Consumer<String>, Consumer<Runnable>, BiFunction<…>, Runnable,
  Supplier<AuxiliarySurface>, Supplier<KitContext>, SettingsChrome chrome, Composition composition)` (the 7-, 6- and
  5-argument forms keep the default look), `public static Projections PageContext.standard()`;
  `SettingsDialog.Hooks(…8…, SettingsChrome chrome, Composition composition)` (the 8-argument form keeps the default
  look); package-private `SettingsChrome SettingsDialog.chrome()`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/architecture/CompositionReadTest.java`:

```java
package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 9.1 and 16.1.7: the Composition value is read (its constants or its methods named) only by
 * the composition roots and the presentation package that computes it; every other class only carries it.
 */
class CompositionReadTest {
    static final String COMPOSITION = "dev/jasper/app/presentation/Composition";
    static final Set<String> ROOTS = Set.of("dev/jasper/app/settingsui/CorePages", "dev/jasper/app/settingsui/AppearanceSelection",
        "dev/jasper/app/settingsui/NotificationGroupsEditor", "dev/jasper/app/settingsui/Editors",
        "dev/jasper/app/shortcuthelp/KeymapPage", "dev/jasper/app/pluginmanager/PluginsPage");

    static boolean allowed(String type) {
        if (type.startsWith("dev/jasper/app/presentation/")) return true;
        int nested = type.indexOf('$');
        return ROOTS.contains(nested < 0 ? type : type.substring(0, nested));
    }

    /** The classes among {@code files} that name a field or a method of Composition; {@code all} keeps the allowed ones. */
    static List<String> readers(List<byte[]> files, boolean all) {
        var found = new ArrayList<String>();
        for (var bytes : files) {
            var model = ClassFile.of().parse(bytes);
            String name = model.thisClass().asInternalName();
            if (name.equals(COMPOSITION) || !all && allowed(name)) continue;
            boolean reads = false;
            for (var entry : model.constantPool()) {
                if (entry instanceof FieldRefEntry field && field.owner().asInternalName().equals(COMPOSITION)) reads = true;
                if (entry instanceof MethodRefEntry method && method.owner().asInternalName().equals(COMPOSITION)) reads = true;
            }
            if (reads) found.add(name);
        }
        return found;
    }

    @Test void onlyTheCompositionRootsAndThePresentationPackageReadIt() throws Exception {
        var files = StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.appJar")), 400);
        assertThat(readers(files, true)).as("the scan is live").contains("dev/jasper/app/presentation/Projections");
        assertThat(readers(files, false)).isEmpty();
    }

    @Test void aReadElsewhereIsReported() {
        var composition = ClassDesc.of("dev.jasper.app.presentation.Composition");
        byte[] probe = ClassFile.of().build(ClassDesc.of("dev.jasper.app.workspace.CompositionProbe"), builder -> builder
            .withMethodBody("probe", MethodTypeDesc.of(composition), ClassFile.ACC_STATIC,
                code -> code.getstatic(composition, "CLASSIC", composition).areturn()));
        byte[] carrier = ClassFile.of().build(ClassDesc.of("dev.jasper.app.workspace.CompositionCarrier"), builder -> builder
            .withField("composition", composition, ClassFile.ACC_PRIVATE));
        assertThat(readers(List.of(probe, carrier), false)).containsExactly("dev/jasper/app/workspace/CompositionProbe");
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsChromeTest.java`:

```java
package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.presentation.Composition;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.presentation.SettingsChrome;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Spec 9.1 and 12.1: the dialog hands its Settings projections to every page; without them it runs the default look. */
class SettingsChromeTest {
    /** The Enterprise look's projection (spec 9.1's table). */
    static final SettingsChrome CLASSIC = new SettingsChrome(false, true, false,
        Set.of("window.native_decorations", "ui.animations.enabled"), "Use the default font size");
    static final Path FILE = Path.of("/fixture/config.toml");

    static AuxiliaryWindows windows() {
        return new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { },
            () -> new Rectangle(0, 0, 1000, 700), (title, initial) -> Optional.empty()));
    }

    static ConfigService.State state() {
        var parsed = ConfigLoader.parse(FILE, "", true);
        return new ConfigService.State(parsed.snapshot(), parsed.diagnostics(), FILE, true, parsed.legacyKeys(), parsed.deadTables(),
            parsed.values(), parsed.rejected());
    }

    /** Hooks over a fixed state, a test kit and {@code chrome} and {@code composition}. */
    static SettingsDialog.Hooks hooks(SettingsChrome chrome, Composition composition) {
        var state = state();
        return new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state, listener -> new Subscription(() -> { }),
            () -> { }, () -> { }, uri -> { }, (parent, count) -> true, KitTestSupport::context, chrome, composition);
    }

    @Test void theDialogHandsItsProjectionsToEveryPage() throws Exception {
        var contexts = new ArrayList<PageContext>();
        var dialog = new SettingsDialog(windows(), context -> {
            contexts.add(context);
            return List.<SettingsPage>of(new GroupPage("appearance", "Appearance", Optional.empty(), false, List::of, context));
        }, hooks(CLASSIC, Composition.CLASSIC));
        try {
            edt(() -> dialog.open(null, "appearance"));
            assertThat(contexts).singleElement().satisfies(context -> {
                assertThat(context.chrome()).isEqualTo(CLASSIC);
                assertThat(context.composition()).isEqualTo(Composition.CLASSIC);
                assertThat(context.withDraft(context.draft()).chrome()).isEqualTo(CLASSIC);
                assertThat(context.withDraft(context.draft()).composition()).isEqualTo(Composition.CLASSIC);
            });
            assertThat(dialog.chrome()).isEqualTo(CLASSIC);
        } finally { edt(dialog::cancel); }
    }

    @Test void withoutAPresentationTheDialogAndItsPagesRunTheDefaultLook() throws Exception {
        var state = state();
        var hooks = new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state, listener -> new Subscription(() -> { }),
            () -> { }, () -> { }, uri -> { }, (parent, count) -> true, KitTestSupport::context);
        boolean mac = com.formdev.flatlaf.util.SystemInfo.isMacOS;
        var standard = Projections.standard(new Projections.Host(mac, mac && com.formdev.flatlaf.util.SystemInfo.isMacFullWindowContentSupported));
        assertThat(hooks.chrome()).isEqualTo(standard.settings());
        assertThat(hooks.composition()).isEqualTo(Composition.ROWS);
        assertThat(PageContext.standard()).isEqualTo(standard);
        var context = new PageContext(new SettingsDraft(state.values()), id -> { }, run -> run.run(), (title, initial) -> Optional.empty(), () -> { });
        assertThat(context.chrome()).isEqualTo(standard.settings());
        assertThat(context.composition()).isEqualTo(Composition.ROWS);
        assertThat(context.chrome().inlineReset()).isTrue();
    }
}
```

Update `ProjectionsTest`: the landing gate now holds the Settings group, and the default-look projections have a name.
Plans 6a, 7 and 8b keep their landing assertions in `untilItsPlanLandsAChromeValueMapsToTheDefaultProjection` and in
their own `the…HasLanded` tests; this step leaves all of them as they are and appends two tests, as plans 7 and 8b do.
Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 1 step 1: ProjectionsTest pins the Settings group's landing and Projections.standard (appended)."""
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java")
text = path.read_text(encoding="utf-8")
if "theSettingsGroupHasLanded" in text: raise SystemExit("ProjectionsTest: the Settings landing test exists already")
addition = '''
    /** Plan 9a: the Settings group has landed, so the kit file's Settings chrome and composition reach the projection. */
    @Test void theSettingsGroupHasLanded() {
        assertThat(Presentation.LANDED).as("plan 9 lands the Settings group").contains(Presentation.Landed.SETTINGS);
        var none = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, Set.of());
        var standard = Projections.of(ChromeSpec.DEFAULT, false, false, "", MAC, Set.of());
        assertThat(none.settings()).as("not landed").isEqualTo(standard.settings());
        assertThat(none.composition()).as("not landed").isEqualTo(standard.composition());
        var settingsOnly = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, EnumSet.of(Presentation.Landed.SETTINGS));
        assertThat(settingsOnly.settings()).isEqualTo(new SettingsChrome(false, true, false,
            Set.of("window.native_decorations", "ui.animations.enabled"), "Use the default font size"));
        assertThat(settingsOnly.composition()).isEqualTo(Composition.CLASSIC);
        assertThat(settingsOnly.workspace().stripes()).as("the dock group is its own").isEqualTo(WorkspaceLook.Stripes.RAILS);
        var shipped = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, Presentation.LANDED);
        assertThat(shipped.settings()).isEqualTo(settingsOnly.settings());
        assertThat(shipped.composition()).isEqualTo(Composition.CLASSIC);
    }

    /** Plan 9a: the default look's projections, for pages and dialogs made without a presentation. */
    @Test void theStandardProjectionsAreTheDefaultLooks() {
        for (var host : new Projections.Host[] {MAC, LINUX})
            assertThat(Projections.standard(host)).isEqualTo(Projections.of(ChromeSpec.DEFAULT, false, false, "", host, Presentation.LANDED));
        assertThat(Projections.standard(LINUX).settings()).isEqualTo(new SettingsChrome(true, false, true, Set.of(), "Use platform font size"));
        assertThat(Projections.standard(MAC).settings().hiddenKeys()).containsExactly("window.native_decorations");
        assertThat(Projections.standard(LINUX).composition()).isEqualTo(Composition.ROWS);
    }
}
'''
body = text.rstrip()
if not body.endswith("}"): raise SystemExit("ProjectionsTest: expected the closing brace last")
path.write_text(body[:-1].rstrip() + "\n" + addition, encoding="utf-8")
PY
```

`ProjectionsTest` (5a) imports `EnumSet` and `Set` and shares `Composition`'s, `SettingsChrome`'s and `WorkspaceLook`'s
package. If an earlier plan's assertion in the file states that `Presentation.LANDED` lacks `SETTINGS` (an exact set or
a `doesNotContain`), add `SETTINGS` to that set or drop it from the `doesNotContain`, and record it in the banner; change
nothing else there.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.SettingsChromeTest' --tests 'dev.jasper.app.presentation.ProjectionsTest' --tests 'dev.jasper.app.architecture.CompositionReadTest'`
Expected: FAIL at `compileTestJava`: "cannot find symbol" for `Projections.standard`, `PageContext.chrome()`,
`PageContext.composition()`, `PageContext.standard()`, `SettingsDialog.chrome()` and the 10-argument `SettingsDialog.Hooks`.

- [ ] **Step 3: Land the group and carry the projections**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 1 step 3: Landed.SETTINGS, Projections.standard, the Settings hooks and page context, the edges."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/presentation/"
path = pathlib.Path(P + "Presentation.java"); text = path.read_text(encoding="utf-8")
found = re.search(r"(    /\*\*[^\n]*\*/\n)(    public static final Set<Landed> LANDED = )(.*);\n", text)
if not found: raise SystemExit("Presentation: LANDED moved")
rhs = found.group(3)
if "SETTINGS" in rhs: raise SystemExit("Presentation: SETTINGS has landed already")
if rhs in ("Set.of()", "java.util.Set.of()"): landed = "Set.of(Landed.SETTINGS)"
elif rhs.endswith(")"): landed = rhs[:-1] + ", Landed.SETTINGS)"
else: raise SystemExit(f"Presentation: unexpected LANDED {rhs!r}")
text = text.replace(found.group(0), "    /** The groups that have landed (spec 19.1 rows 6 to 9): their projections follow the kit file. */\n"
    + found.group(2) + landed + ";\n", 1)
path.write_text(text, encoding="utf-8")
edit(P + "Projections.java", [
    ('''    /**
     * Spec 4.7's fallback''', '''    /**
     * The default look's projections on {@code host}: what a Settings page made outside a dialog, or a dialog made without
     * a presentation, runs with.
     *
     * @param host the host's facts
     * @return the projections of {@link ChromeSpec#DEFAULT} under the default kit
     */
    public static Projections standard(Host host) {
        return of(ChromeSpec.DEFAULT, false, false, "", host, Presentation.LANDED);
    }

    /**
     * Spec 4.7's fallback''', 1)])
S = "jasper-app/src/main/java/dev/jasper/app/settingsui/"
edit(S + "PageContext.java", [
    ('''import dev.jasper.app.windows.AuxiliarySurface;''', '''import dev.jasper.app.presentation.Composition;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.presentation.SettingsChrome;
import dev.jasper.app.windows.AuxiliarySurface;''', 1),
    (''' * @param kit the dialog's kit context (chrome route); read on the EDT when a page builds its view
 */
public record PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                          BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed,
                          Supplier<AuxiliarySurface> surface, Supplier<KitContext> kit) {
    public PageContext {
        Objects.requireNonNull(kit, "kit");
    }
''', ''' * @param kit the dialog's kit context (chrome route); read on the EDT when a page builds its view
 * @param chrome the look's Settings projection (spec 12.1): Reset inline or in the row's menu, the rows it hides, the font
 *     row's copy
 * @param composition how the look composes the app's pages (spec 12.2); only the composition roots read it
 */
public record PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                          BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed,
                          Supplier<AuxiliarySurface> surface, Supplier<KitContext> kit, SettingsChrome chrome,
                          Composition composition) {
    public PageContext {
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(composition, "composition");
    }

    /** A context under the default look on this host ({@link #standard()}). */
    public PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                       BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed,
                       Supplier<AuxiliarySurface> surface, Supplier<KitContext> kit) {
        this(draft, navigate, closeThen, chooseFile, changed, surface, kit, standard().settings(), standard().composition());
    }

    /** The default look's projections on this host, for pages and dialogs made without a presentation. */
    public static Projections standard() {
        boolean mac = com.formdev.flatlaf.util.SystemInfo.isMacOS;
        return Projections.standard(new Projections.Host(mac, mac && com.formdev.flatlaf.util.SystemInfo.isMacFullWindowContentSupported));
    }
''', 1),
    ('''        return new PageContext(other, navigate, closeThen, chooseFile, changed, surface, kit);''',
     '''        return new PageContext(other, navigate, closeThen, chooseFile, changed, surface, kit, chrome, composition);''', 1)])
edit(S + "SettingsDialog.java", [
    ('''import dev.jasper.app.lifecycle.Subscription;''', '''import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.presentation.Composition;
import dev.jasper.app.presentation.SettingsChrome;''', 1),
    ('''     * @param kit the application's chrome kit context, read on the EDT each time the dialog opens
     */
    public record Hooks(Function<SettingsEdit, CompletableFuture<ConfigService.State>> save, Supplier<ConfigService.State> state,
                        Function<Consumer<ConfigService.State>, Subscription> onState, Runnable openConfigFile, Runnable migrate,
                        Consumer<URI> browse, BiPredicate<Component, Integer> confirmDiscard, Supplier<KitContext> kit) {
        public Hooks {
            Objects.requireNonNull(save); Objects.requireNonNull(state); Objects.requireNonNull(onState);
            Objects.requireNonNull(openConfigFile); Objects.requireNonNull(migrate); Objects.requireNonNull(browse);
            Objects.requireNonNull(confirmDiscard); Objects.requireNonNull(kit);
        }
    }''', '''     * @param kit the application's chrome kit context, read on the EDT each time the dialog opens
     * @param chrome the look's Settings projection (spec 12.1)
     * @param composition how the look composes the app's pages (spec 12.2), handed to every page
     */
    public record Hooks(Function<SettingsEdit, CompletableFuture<ConfigService.State>> save, Supplier<ConfigService.State> state,
                        Function<Consumer<ConfigService.State>, Subscription> onState, Runnable openConfigFile, Runnable migrate,
                        Consumer<URI> browse, BiPredicate<Component, Integer> confirmDiscard, Supplier<KitContext> kit,
                        SettingsChrome chrome, Composition composition) {
        public Hooks {
            Objects.requireNonNull(save); Objects.requireNonNull(state); Objects.requireNonNull(onState);
            Objects.requireNonNull(openConfigFile); Objects.requireNonNull(migrate); Objects.requireNonNull(browse);
            Objects.requireNonNull(confirmDiscard); Objects.requireNonNull(kit);
            Objects.requireNonNull(chrome); Objects.requireNonNull(composition);
        }

        /** Hooks under the default look on this host ({@link PageContext#standard()}). */
        public Hooks(Function<SettingsEdit, CompletableFuture<ConfigService.State>> save, Supplier<ConfigService.State> state,
                     Function<Consumer<ConfigService.State>, Subscription> onState, Runnable openConfigFile, Runnable migrate,
                     Consumer<URI> browse, BiPredicate<Component, Integer> confirmDiscard, Supplier<KitContext> kit) {
            this(save, state, onState, openConfigFile, migrate, browse, confirmDiscard, kit,
                PageContext.standard().settings(), PageContext.standard().composition());
        }
    }''', 1),
    ('''        pages = List.copyOf(pageFactory.apply(new PageContext(draft, this::select, this::closeThen, this::chooseFile, this::refreshNow,
            () -> isOpen() ? surface : null, this::kit)));''',
     '''        pages = List.copyOf(pageFactory.apply(new PageContext(draft, this::select, this::closeThen, this::chooseFile, this::refreshNow,
            () -> isOpen() ? surface : null, this::kit, hooks.chrome(), hooks.composition())));''', 1),
    ('''    SettingsView view() { return view; }''', '''    SettingsView view() { return view; }
    /** The look's Settings projection this dialog runs with. */
    SettingsChrome chrome() { return hooks.chrome(); }''', 1),
    ])
edit(S + "package-info.java", [
    ('''dev.jasper.app.platform, dev.jasper.app.lifecycle, dev.jasper.app.appearance (the themes folder's files).''',
     '''dev.jasper.app.platform, dev.jasper.app.lifecycle, dev.jasper.app.appearance (the themes folder's files),
 * dev.jasper.app.presentation (the look's Settings projection and its page composition).''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java", [
    ('''                    () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME)));
            configuration.settingsOpener(''',
     '''                    () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME),
                    presentation().projections().settings(), presentation().projections().composition()));
            configuration.settingsOpener(''', 1)])
for name in ("pluginmanager", "shortcuthelp"):
    path = pathlib.Path(f"jasper-app/src/main/java/dev/jasper/app/{name}/package-info.java"); text = path.read_text(encoding="utf-8")
    if "dev.jasper.app.presentation" in text: continue
    marker = "Allowed outgoing Jasper dependencies:"
    if text.count(marker) != 1: raise SystemExit(f"{name}/package-info.java: expected one {marker!r}")
    path.write_text(text.replace(marker, marker + " dev.jasper.app.presentation (its page composition),", 1), encoding="utf-8")
PY
```

If a `package-info.java` names its edges without the "Allowed outgoing Jasper dependencies:" sentence, the script stops:
add "dev.jasper.app.presentation (its page composition)" to the sentence that lists its dependencies, in its own words.

Add the two `migration.csv` rows (both tests construct lists; the ledger's pattern names them):

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
rows = [
    "jasper-app/src/test/java/dev/jasper/app/architecture/CompositionReadTest.java,app.architecture,CompositionReadTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/architecture/CompositionReadTest.java,Test/fixture ownership; New in Enterprise plan 9a Task 1; only the composition roots read Composition",
    "jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsChromeTest.java,app.settingsui,SettingsChromeTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsChromeTest.java,Test/fixture ownership; New in Enterprise plan 9a Task 1; the Settings projections reach every page"]
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.architecture.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture --continue`
Expected: PASS: `SettingsChromeTest` (2), `ProjectionsTest` (its cases plus `theSettingsGroupHasLanded` and
`theStandardProjectionsAreTheDefaultLooks`),
`CompositionReadTest` (2), every existing Settings and architecture test; `verifyApplicationArchitecture` reports no cycle
(`settingsui`, `pluginmanager` and `shortcuthelp` reach `presentation`, which reaches none of them).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/presentation jasper-app/src/main/java/dev/jasper/app/settingsui \
  jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java \
  jasper-app/src/main/java/dev/jasper/app/pluginmanager/package-info.java jasper-app/src/main/java/dev/jasper/app/shortcuthelp/package-info.java \
  jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java jasper-app/src/test/java/dev/jasper/app/architecture/CompositionReadTest.java \
  jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsChromeTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Settings chrome: the Settings projections land and reach every page

Presentation.LANDED gains SETTINGS, so SettingsChrome and Composition
follow the kit file. SettingsDialog.Hooks carries both from the
application's presentation and hands both to every page in its
PageContext; without a presentation both are Projections.standard, the
default look on this host. CompositionReadTest keeps reads of Composition
in spec 9.1's six composition roots.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: The rows the look implies and the font row copy

`SettingsChrome.hiddenKeys` decides which rows are built (spec 12.1): under Enterprise the system-title-bar and
animations rows go, and a section left without rows shows no header; the default look keeps hiding the title-bar row on
macOS, now through the same rule. The interface font row takes the look's copy, and the first untick starts the stepper
at the look's standard size.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java:221-299` (built rows)
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java:124`
- Replace: `jasper-app/src/main/java/dev/jasper/app/settingsui/PlatformFontSize.java` (whole file)
- Create: `jasper-app/src/test/java/dev/jasper/app/settingsui/LookRowsTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (one row)

**Interfaces:**
- Consumes: Task 1's `PageContext.chrome()`; plan 5a's `Editors.effectiveDefault(SettingsDraft, Setting)`; plan 5b's
  `FaceSet.namesUiFaces()`; `KitContext.kit()`, `Kit.faces()`, `Kit.type()`, `TypeScale.body()`.
- Produces: package-private `boolean SchemaPage.built(RowItem)` (private); `PlatformFontSize(PageContext context)`;
  package-private `static float PlatformFontSize.standardSize(PageContext context)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/LookRowsTest.java`:

```java
package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.presentation.Composition;
import dev.jasper.app.presentation.SettingsChrome;
import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.testsupport.KitFixtures;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 12.1 and 12.2: the rows the look implies are not built, and the interface font row speaks for the look. */
class LookRowsTest {
    static final SettingsChrome LINUX_DEFAULT = new SettingsChrome(true, false, true, Set.of(), "Use platform font size");

    static PageContext context(SettingsChrome chrome, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit) {
        var parsed = ConfigLoader.parse(Path.of("/fixture/config.toml"), "", true);
        return new PageContext(new SettingsDraft(parsed.values()), id -> { }, run -> run.run(), (title, initial) -> Optional.empty(), () -> { },
            () -> null, kit, chrome, Composition.ROWS);
    }

    /** The settings a core page builds rows for, by name. */
    static List<String> rows(String pageId, PageContext context) {
        var spec = CorePages.specs(AppearanceFixtures.CHOICES, new CorePages.SystemActions(() -> Path.of("/fixture/config.toml"),
            () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.empty()), ShellChoices.NONE, id -> { }, true,
            NotificationGroupChoices.NONE).stream().filter(page -> page.id().equals(pageId)).findFirst().orElseThrow();
        var page = spec.build(context);
        page.component();
        try { return page.rows().stream().map(row -> row.setting().name()).toList(); } finally { page.dispose(); }
    }

    /** Every section title a core page shows. */
    static List<String> sections(String pageId, PageContext context) {
        var spec = CorePages.specs(AppearanceFixtures.CHOICES, new CorePages.SystemActions(() -> Path.of("/fixture/config.toml"),
            () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.empty()), ShellChoices.NONE, id -> { }, true,
            NotificationGroupChoices.NONE).stream().filter(page -> page.id().equals(pageId)).findFirst().orElseThrow();
        var page = spec.build(context);
        page.component();
        var titles = new ArrayList<String>();
        try { collect(page.viewSpec().root(), titles); return titles; } finally { page.dispose(); }
    }

    static void collect(Nodes.Node node, List<String> titles) {
        switch (node) {
            case Nodes.Section section -> { titles.add(section.title()); collect(section.body(), titles); }
            case Nodes.Stack stack -> stack.children().forEach(child -> collect(child, titles));
            case Nodes.Card card -> card.children().forEach(child -> collect(child, titles));
            default -> { }
        }
    }

    @Test void theLooksImpliedSettingsAreNotBuiltAndLeaveNoEmptySection() throws Exception {
        edt(() -> {
            var classic = context(SettingsChromeTest.CLASSIC, KitTestSupport::context);
            assertThat(rows("window", classic)).doesNotContain("window.native_decorations").contains("window.toolbar");
            assertThat(rows("appearance", classic)).doesNotContain("ui.animations.enabled").contains("window.view_mode");
            assertThat(sections("appearance", classic)).doesNotContain("Animations").contains("View mode");
            var linux = context(LINUX_DEFAULT, KitTestSupport::context);
            assertThat(rows("window", linux)).as("the default look off macOS").contains("window.native_decorations");
            assertThat(rows("appearance", linux)).contains("ui.animations.enabled");
            assertThat(sections("appearance", linux)).contains("Animations");
        });
    }

    @Test void theFontRowSpeaksForTheLookAndStartsAtItsStandardSize() throws Exception {
        edt(() -> {
            var parsed = EnterpriseResources.facts().parsed().orElseThrow();
            var enterprise = KitFixtures.context(KitFixtures.light(), parsed.kit());
            var standard = KitFixtures.context(KitFixtures.light());
            try {
                var classic = context(SettingsChromeTest.CLASSIC, () -> enterprise);
                assertThat(PlatformFontSize.standardSize(classic)).as("[type].base").isEqualTo(12f);
                assertThat(PlatformFontSize.standardSize(context(LINUX_DEFAULT, () -> standard))).as("the example").isEqualTo(14f);
                var editor = new PlatformFontSize(classic);
                var node = (Nodes.Stack) editor.node(CoreSettings.UI_FONT_SIZE.name(), dev.jasper.ui.model.ControlState.READY);
                var row = (Nodes.SettingRow) node.children().getFirst();
                assertThat(row.title()).isEqualTo("Use the default font size");
                var toggle = (Nodes.Toggle) ((Nodes.Stack) row.control()).children().getFirst();
                assertThat(toggle.label()).isEqualTo("Use the default size");
                editor.show(CoreSettings.UI_FONT_SIZE.defaultValue());
                assertThat(editor.event(CoreSettings.UI_FONT_SIZE.name(), new UiEvent.Toggled(CoreSettings.UI_FONT_SIZE.name() + ":default", false))).isTrue();
                assertThat(editor.value()).as("the first untick starts at the look's size").isEqualTo(12f);
                var platform = new PlatformFontSize(context(LINUX_DEFAULT, () -> standard));
                var plain = (Nodes.SettingRow) ((Nodes.Stack) platform.node(CoreSettings.UI_FONT_SIZE.name(), dev.jasper.ui.model.ControlState.READY)).children().getFirst();
                assertThat(plain.title()).isEqualTo("Use platform font size");
                platform.show(CoreSettings.UI_FONT_SIZE.defaultValue());
                platform.event(CoreSettings.UI_FONT_SIZE.name(), new UiEvent.Toggled(CoreSettings.UI_FONT_SIZE.name() + ":default", false));
                assertThat(platform.value()).isEqualTo(14f);
            } finally {
                for (var context : List.of(enterprise, standard)) { context.binding().close(); context.icons().close(); }
            }
        });
    }
}
```

The `Nodes.Toggle` label accessor is `label()` in the jasper-ui model (`new Nodes.Toggle(id, state, label, checked,
action, …)`); if the model names it otherwise, use its name.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.LookRowsTest'`
Expected: FAIL at `compileTestJava`: "constructor PlatformFontSize in class PlatformFontSize cannot be applied to given
types" and "cannot find symbol: method standardSize".

- [ ] **Step 3: Build only the rows the look keeps, and give the font row its copy**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 2 step 3: hidden keys decide the built rows; the title-bar row loses its macOS rule."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-app/src/main/java/dev/jasper/app/settingsui/"
edit(S + "SchemaPage.java", [
    ('''            if (item instanceof RowItem row && row.shown()) {''', '''            if (item instanceof RowItem row && built(row)) {''', 1),
    ('''            if (item instanceof RowItem row) { if (row.shown()) return true; anyRow = true; }''',
     '''            if (item instanceof RowItem row) { if (built(row)) return true; anyRow = true; }''', 1),
    ('''            if (item instanceof RowItem row && row.shown()) addRow(row);''', '''            if (item instanceof RowItem row && built(row)) addRow(row);''', 1),
    ('''                    if(row.shown()) {''', '''                    if(built(row)) {''', 1),
    ('''    List<Row<?>> rows() { return rows; }''', '''    List<Row<?>> rows() { return rows; }

    /** Whether a row is built: shown on this platform, and not a setting the look implies (spec 12.1's hidden keys). */
    private boolean built(RowItem row) { return row.shown() && !context.chrome().hiddenKeys().contains(row.setting().name()); }''', 1)])
edit(S + "CorePages.java", [
    ('''                .row(WINDOW_NATIVE_DECORATIONS).shownWhen(!com.formdev.flatlaf.util.SystemInfo.isMacOS)''',
     '''                .row(WINDOW_NATIVE_DECORATIONS)''', 1)])
PY
```

If plan 3e's `heading` (or any later code) reads `row.shown()` on a `RowItem`, change that read to `built(row)` too, so
a hidden row never heads or titles a section; record it in the banner.

Replace `jasper-app/src/main/java/dev/jasper/app/settingsui/PlatformFontSize.java` with:

```java
package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.UI_FONT_SIZE;

import dev.jasper.app.settings.SettingKind;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.util.List;
import java.util.function.Supplier;

/**
 * A look-size switch and a manual stepper, kept mounted while inheritance controls visibility. The switch's copy is the
 * look's ({@code SettingsChrome.fontRowCopy}, spec 12.1); the first untick starts the stepper at the look's standard size
 * (spec 12.2: the kit's body size when it names a UI face, the setting's example otherwise).
 */
final class PlatformFontSize implements ValueEditor<Float> {
    private final Editors.NumberField<Float> number;
    private final SettingsDraft draft;
    private final String copy;
    private final Supplier<Float> standard;
    private boolean inherited = true, chosen;

    static Editors.Factory<Float> factory() {
        return new Editors.Factory<>((setting, context) -> new PlatformFontSize(context),
            List.of("Use platform size", "Use platform font size", "Use the default font size"));
    }

    /** The look's standard interface size: the kit's body size when it names a UI face, else the setting's example. */
    static float standardSize(PageContext context) {
        var kit = context.kit().get().kit();
        return kit.faces().namesUiFaces() ? kit.type().body() : UI_FONT_SIZE.example().orElse(13f);
    }

    PlatformFontSize(PageContext context) {
        this.draft = context.draft();
        this.copy = context.chrome().fontRowCopy();
        this.standard = () -> standardSize(context);
        var kind = (SettingKind.NumberRange) UI_FONT_SIZE.kind();
        number = new Editors.NumberField<>("Font size", new Nodes.Numeric(kind.min(), kind.max(), .5, 2), "pt", Float::valueOf, Nodes.FieldPresentation.STEPPER);
        number.show(UI_FONT_SIZE.example().orElse(13f));
    }

    @Override public boolean fullSpan() { return true; }
    @Override public boolean cardRows() { return true; }
    @Override public Nodes.Node node(String id, ControlState state) { return node(id, state, false); }
    @Override public Nodes.Node node(String id, ControlState state, boolean highlighted) {
        var toggle = new Nodes.Toggle(id + ":default", state, copy.replace(" font size", " size"), inherited, id + ":default", false);
        var reset = new Nodes.Button(id + ":reset", new ControlState(!inherited, state.enabled(), false, ""), "", "reset", Nodes.ButtonKind.GHOST,
            id + ":reset", "Reset interface font size to platform default");
        var control = new Nodes.Stack(id + ":toggle-controls", state, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(toggle, reset));
        var manual = new ControlState(!inherited, state.enabled(), state.busy(), state.error());
        return new Nodes.Stack(id + ":optional", state, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.SettingRow("row:" + id, state, copy, "", control, false, highlighted),
            new Nodes.SettingRow(id + ":manual-row", manual, "Font size", "", number.node(id, manual), true, highlighted)));
    }
    @Override public Float value() { return inherited ? Editors.effectiveDefault(draft,UI_FONT_SIZE) : number.value(); }
    @Override public void show(Float value) {
        inherited = value.equals(Editors.effectiveDefault(draft,UI_FONT_SIZE));
        if (!inherited) { number.show(value); chosen = true; }
    }
    @Override public boolean event(String id, UiEvent event) {
        if (event instanceof UiEvent.Toggled toggle && toggle.id().equals(id + ":default")) {
            inherited = toggle.checked();
            if (!inherited && !chosen) { number.show(standard.get()); chosen = true; }
            return true;
        }
        return number.event(id, event);
    }
    @Override public boolean removesAtDefault() { return true; }
}
```

The default look's copy is "Use platform font size", so its toggle still reads "Use platform size" and its stepper still
starts at 14: the default look's descriptors are unchanged (the pixel proof in Task 3 covers it). The two comparisons are
spelled `Editors.effectiveDefault(draft,UI_FONT_SIZE)`, without a space, as plan 5a wrote them: 5a's
`DefaultsPrecedenceTest.noAllowlistedKeyReachesACoreDefaultComparison` reads `PlatformFontSize.java` for that exact
text, and the file holds no `UI_FONT_SIZE.defaultValue()`. Add the test's
`migration.csv` row (it imports `dev.jasper.ui…`):

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
row = ("jasper-app/src/test/java/dev/jasper/app/settingsui/LookRowsTest.java,app.settingsui,LookRowsTest,nonvisual-helper,none,none,"
       "jasper-app/src/test/java/dev/jasper/app/settingsui/LookRowsTest.java,Test/fixture ownership; New in Enterprise plan 9a Task 2; hidden rows and the font row copy")
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.plugins.PluginSettingsPageTest' --tests 'dev.jasper.app.config.DefaultsPrecedenceTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `LookRowsTest` (2), `CorePagesTest` (every setting still appears on exactly one page), `SchemaPageTest`,
`EditorsTest` and `SettingsDialogTest` unchanged, and 5a's `DefaultsPrecedenceTest` (the effective-default comparisons
it reads in `PlatformFontSize.java`).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java \
  jasper-app/src/main/java/dev/jasper/app/settingsui/PlatformFontSize.java jasper-app/src/test/java/dev/jasper/app/settingsui/LookRowsTest.java \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Settings chrome: the rows the look implies and the font row copy

A row whose setting the look implies (SettingsChrome.hiddenKeys) is not
built, so under Enterprise the system title bar and animations rows go and
the Animations section with them; the default look hides the title bar row
on macOS through the same rule. The interface font row takes the look's
copy, and the first untick starts its stepper at the look's standard size.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: Reset to Default in the row's menu

Under `inlineReset = false` (spec 12.1) a Settings row has no Reset button: its own context menu offers "Reset to
Default". jasper-ui's `Nodes.SettingRow` gains `contextActions`, which `SettingRows.RowControl` shows on the platform's
popup trigger and on Shift+F10 or the menu key; the trailing slot drops its reserved gap when a row has no action. The
schema rows, external rows and the full-span editors (Appearance, interface font, notification groups, workspace
opacity) follow the chrome.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java:418-431` (`SettingRow`)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/SettingRows.java:17-68`, `Stacks.java:474-484`, `ViewReconciler.java:359-365`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/SettingRowMenuTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java:335-369`, `AppearanceSelection.java:34-75`,
  `PlatformFontSize.java` (Task 2's file), `NotificationGroupsEditor.java:37-41,85-87`, `WorkspaceEditors.java:11-30`, `Editors.java:191-194`
- Create: `jasper-app/src/test/java/dev/jasper/app/settingsui/ResetMenuTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (one row)

**Interfaces:**
- Consumes: Task 1's `PageContext.chrome()`; `Menus.Popup`, `Menus.Item.describe`, `Menus.Separator`,
  `KitOwnership.CONTEXT_MENU` (fact 13).
- Produces: `Nodes.SettingRow(String id, ControlState state, String title, String description, Node control, boolean
  dependent, boolean highlighted, String titleNote, List<MenuItem> contextActions)` (the 8-, 7- and 6-argument forms keep
  no menu); package-private `Menus.Popup SettingRows.RowControl.popup` and `void context(Component invoker, int x, int y)`;
  package-private `static List<MenuItem> SchemaPage.resetMenu(PageContext context, String action, boolean resettable)`;
  `NotificationGroupsEditor(NotificationGroupChoices, Supplier<KitContext>, boolean inlineReset)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/SettingRowMenuTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 12.1: a Settings row's own menu, by the popup trigger or the keyboard. */
class SettingRowMenuTest {
    static ViewSpec row(List<MenuItem> menu) {
        return new ViewSpec(new Nodes.SettingRow("row", ControlState.READY, "Visible rows", "", new Nodes.Field("rows", ControlState.READY,
            Nodes.FieldKind.TEXT, "8", "", "Visible rows", ""), false, false, "", menu));
    }
    static final MenuItem RESET = new MenuItem("rows:reset", "Reset to Default", "", "rows:reset", true, false);

    @Test void aRowsMenuReportsItsItemsActionAndARowWithoutOneHasNone() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, row(List.of(RESET)), events::add)) {
                var control = (SettingRows.RowControl) view.control("row");
                control.context(control.component, 4, 4);
                assertThat(control.popup.getComponentCount()).isEqualTo(1);
                var item = (JMenuItem) control.popup.getComponent(0);
                assertThat(item.getText()).isEqualTo("Reset to Default");
                item.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("rows:reset"));
                view.update(row(List.of()));
                control.context(control.component, 4, 4);
                assertThat(control.popup.getComponentCount()).isZero();
                assertThat(control.component.getMouseListeners()).as("a row without a menu takes no mouse events")
                    .noneMatch(listener -> listener.getClass().getName().contains("SettingRows"));
            } finally { release(context); }
        });
    }

    @Test void thePopupTriggerFillsTheMenuAndADisabledItemStaysDisabled() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, row(List.of(new MenuItem("rows:reset", "Reset to Default", "", "rows:reset", false, false))), event -> { })) {
                var control = (SettingRows.RowControl) view.control("row");
                var press = new MouseEvent(control.component, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON3_DOWN_MASK,
                    4, 4, 1, true, MouseEvent.BUTTON3);
                for (var listener : control.component.getMouseListeners()) listener.mousePressed(press);
                assertThat(control.popup.getComponentCount()).isEqualTo(1);
                assertThat(control.popup.getComponent(0).isEnabled()).isFalse();
            } finally { release(context); }
        });
    }

    @Test void theKeyboardOpensTheRowsMenu() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, row(List.of(RESET)), event -> { })) {
                var control = (SettingRows.RowControl) view.control("row");
                for (var stroke : List.of(KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK), KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0))) {
                    control.clearPopup();
                    var name = control.component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(stroke);
                    assertThat(name).as(stroke.toString()).isNotNull();
                    control.component.getActionMap().get(name).actionPerformed(new java.awt.event.ActionEvent(control.component, 0, ""));
                    assertThat(control.popup.getComponentCount()).as(stroke.toString()).isEqualTo(1);
                }
            } finally { release(context); }
        });
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/ResetMenuTest.java`:

```java
package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.presentation.Composition;
import dev.jasper.app.presentation.SettingsChrome;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 12.1: without an inline Reset, each row's own menu offers Reset to Default while the value differs from its default. */
class ResetMenuTest {
    static PageContext context(SettingsChrome chrome, String config) {
        var parsed = ConfigLoader.parse(Path.of("/fixture/config.toml"), config, true);
        return new PageContext(new SettingsDraft(parsed.values()), id -> { }, run -> run.run(), (title, initial) -> Optional.empty(), () -> { },
            () -> null, KitTestSupport::context, chrome, Composition.ROWS);
    }

    static SchemaPage page(String id, PageContext context, NotificationGroupChoices groups) {
        var spec = CorePages.specs(AppearanceFixtures.CHOICES, new CorePages.SystemActions(() -> Path.of("/fixture/config.toml"),
            () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.empty()), ShellChoices.NONE, page -> { }, true, groups)
            .stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
        var page = spec.build(context);
        page.component();
        return page;
    }

    static Nodes.Node find(Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        var children = switch (node) {
            case Nodes.Stack stack -> stack.children();
            case Nodes.Card card -> card.children();
            case Nodes.Section section -> List.of(section.body());
            case Nodes.SettingRow row -> List.of(row.control());
            default -> List.<Nodes.Node>of();
        };
        for (var child : children) { var found = find(child, id); if (found != null) return found; }
        return null;
    }

    static Nodes.SettingRow row(SchemaPage page, String id) { return (Nodes.SettingRow) find(page.viewSpec().root(), id); }

    @Test void aClassicRowHasNoResetButtonAndItsMenuResetsIt() throws Exception {
        edt(() -> {
            var context = context(SettingsChromeTest.CLASSIC, "[palette]\nvisible_rows = 9\n");
            var page = page("command-palette", context, NotificationGroupChoices.NONE);
            try {
                var row = row(page, "row:palette.visible_rows");
                assertThat(((Nodes.Stack) row.control()).children()).as("no Reset button").hasSize(1);
                assertThat(row.contextActions()).containsExactly(new MenuItem("palette.visible_rows:reset", "Reset to Default", "",
                    "palette.visible_rows:reset", true, false));
                page.event(new UiEvent.Action("palette.visible_rows:reset"));
                assertThat(context.draft().value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(context.draft().defaultValue(CoreSettings.PALETTE_VISIBLE_ROWS));
                assertThat(row(page, "row:palette.visible_rows").contextActions().getFirst().enabled()).as("at its default").isFalse();
            } finally { page.dispose(); }
        });
    }

    @Test void theDefaultLookKeepsItsResetButtonAndNoMenu() throws Exception {
        edt(() -> {
            var page = page("command-palette", context(LookRowsTest.LINUX_DEFAULT, "[palette]\nvisible_rows = 9\n"), NotificationGroupChoices.NONE);
            try {
                var row = row(page, "row:palette.visible_rows");
                assertThat(((Nodes.Stack) row.control()).children()).hasSize(2);
                assertThat(row.contextActions()).isEmpty();
            } finally { page.dispose(); }
        });
    }

    @Test void theFullSpanEditorsOfferTheMenuOnTheirOwnRows() throws Exception {
        edt(() -> {
            var context = context(SettingsChromeTest.CLASSIC, "[ui]\nappearance = \"light\"\n[ui.font]\nsize = 15\n");
            var page = page("appearance", context, NotificationGroupChoices.NONE);
            try {
                for (var id : List.of("row:ui.appearance", "row:ui.appearance:light")) {
                    var row = row(page, id);
                    assertThat(row.contextActions()).as(id).extracting(MenuItem::action).containsExactly("ui.appearance:reset");
                    assertThat(row.contextActions().getFirst().enabled()).as(id).isTrue();
                }
                assertThat(find(page.viewSpec().root(), "ui.appearance:reset")).as("no Reset button").isNull();
                assertThat(find(page.viewSpec().root(), "ui.appearance:light-reset")).as("no reserved lane").isNull();
                assertThat(row(page, "row:ui.font.size").contextActions()).extracting(MenuItem::action).containsExactly("ui.font.size:reset");
                assertThat(find(page.viewSpec().root(), "ui.font.size:reset")).isNull();
                page.event(new UiEvent.Action("ui.appearance:reset"));
                assertThat(context.draft().value(CoreSettings.UI_APPEARANCE)).isEqualTo(context.draft().defaultValue(CoreSettings.UI_APPEARANCE));
            } finally { page.dispose(); }
        });
    }

    @Test void aNotificationGroupsRowsOfferTheMenu() throws Exception {
        edt(() -> {
            var groups = new NotificationGroupChoices(() -> List.of(new NotificationGroupChoices.Group("configuration", "Configuration", "Jasper", "sticky")));
            var page = page("notifications", context(SettingsChromeTest.CLASSIC, "[notifications.groups]\nconfiguration = \"card\"\n"), groups);
            try {
                var row = row(page, "notifications.groups:row-0");
                assertThat(row.contextActions()).extracting(MenuItem::action).containsExactly(NotificationGroupsEditor.resetId("notifications.groups", 0));
                assertThat(row.contextActions().getFirst().enabled()).isTrue();
                assertThat(find(page.viewSpec().root(), NotificationGroupsEditor.resetId("notifications.groups", 0))).isNull();
            } finally { page.dispose(); }
        });
    }
}
```

The groups setting's name is `notifications.groups` (its editor ids start with it, fact 14); if `CoreSettings` names it
otherwise, use its `name()`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.SettingRowMenuTest' :jasper-app:test --tests 'dev.jasper.app.settingsui.ResetMenuTest' --continue`
Expected: FAIL at compile: "constructor SettingRow in record SettingRow cannot be applied to given types" (9 arguments),
"cannot find symbol: variable popup", "method context", "method contextActions()".

- [ ] **Step 3: Give rows a menu in jasper-ui**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 3 step 3: SettingRow.contextActions and the row's menu."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
U = "jasper-ui/src/main/java/dev/jasper/ui/"
edit(U + "model/Nodes.java", [
    ('''    public record SettingRow(String id, ControlState state, String title, String description, Node control, boolean dependent, boolean highlighted, String titleNote) implements Node {
        public SettingRow {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(description); Objects.requireNonNull(titleNote);
            Objects.requireNonNull(control);
        }''', '''    /**
     * A settings row: title, description and its control. {@code contextActions} is the row's own menu (Enterprise
     * presentation spec 12.1: Reset to Default when the look has no inline Reset); empty means none. Host-only: the SDK's
     * rows have no menu.
     */
    public record SettingRow(String id, ControlState state, String title, String description, Node control, boolean dependent, boolean highlighted, String titleNote,
                             List<MenuItem> contextActions) implements Node {
        public SettingRow {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(description); Objects.requireNonNull(titleNote);
            Objects.requireNonNull(control);
            contextActions = List.copyOf(contextActions);
            ModelChecks.unique(contextActions, MenuItem::id);
        }
        /** A row without a menu of its own. */
        public SettingRow(String id, ControlState state, String title, String description, Node control, boolean dependent, boolean highlighted, String titleNote) {
            this(id, state, title, description, control, dependent, highlighted, titleNote, List.of());
        }''', 1)])
edit(U + "swing/SettingRows.java", [
    ('''        SecretFields.SecretControl secret;''', '''        SecretFields.SecretControl secret;
        /** The row's own menu (Enterprise presentation spec 12.1), filled each time it opens. */
        final Menus.Popup popup;
        private final List<Runnable> popupCleanup = new java.util.ArrayList<>();
        private final java.awt.event.MouseAdapter menuTrigger = new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent event) { if (event.isPopupTrigger()) context(event.getComponent(), event.getX(), event.getY()); }
            @Override public void mouseReleased(java.awt.event.MouseEvent event) { if (event.isPopupTrigger()) context(event.getComponent(), event.getX(), event.getY()); }
        };
        private boolean menuShown;''', 1),
    ('''            component.add(title);
            component.add(description);
            refresh();
        }''', '''            component.add(title);
            component.add(description);
            popup = new Menus.Popup(context());
            component.putClientProperty(KitOwnership.CONTEXT_MENU, popup);
            component.getActionMap().put("settingRowMenu", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) { context(component, 0, component.getHeight()); }
            });
            own(() -> { menu(false); popup.setVisible(false); clearPopup(); });
            refresh();
        }

        /** Takes the popup trigger and the menu keys only while the row has a menu, so a plain row passes its mouse events on. */
        private void menu(boolean shown) {
            if (menuShown == shown) return;
            menuShown = shown;
            var keys = component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            for (var stroke : List.of(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F10, java.awt.event.InputEvent.SHIFT_DOWN_MASK),
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_CONTEXT_MENU, 0))) {
                if (shown) keys.put(stroke, "settingRowMenu"); else keys.remove(stroke);
            }
            for (JComponent target : List.<JComponent>of(component, title, description)) {
                if (shown) target.addMouseListener(menuTrigger); else target.removeMouseListener(menuTrigger);
            }
        }

        /** Fills the row's menu from its descriptor; shows it at {@code (x, y)} over {@code invoker} when there is one to show. */
        void context(Component invoker, int x, int y) {
            if (!interactive()) return;
            clearPopup();
            for (var item : ((Nodes.SettingRow) node).contextActions()) {
                if (item.separator()) { popup.add(new Menus.Separator(context())); continue; }
                var entry = new Menus.Item(context());
                entry.describe(item);
                java.awt.event.ActionListener chosen = event -> {
                    if (((Nodes.SettingRow) node).contextActions().contains(item)) emit(new dev.jasper.ui.model.UiEvent.Action(item.action()));
                };
                entry.addActionListener(chosen);
                popupCleanup.add(() -> entry.removeActionListener(chosen));
                popup.add(entry);
            }
            if (popup.getComponentCount() > 0 && invoker.isShowing()) popup.show(invoker, x, y);
        }

        void clearPopup() {
            popupCleanup.forEach(Runnable::run);
            popupCleanup.clear();
            popup.removeAll();
        }''', 1),
    ('''            ((SearchHighlight) component).highlight(row.highlighted());
        }''', '''            ((SearchHighlight) component).highlight(row.highlighted());
            menu(!row.contextActions().isEmpty());
        }''', 1)])
edit(U + "swing/Stacks.java", [
    ('''        int gap(){return ThemePainting.metric(context,DesignMetrics.Role.COLUMN_GAP);}
        int action(Container p){return p.getComponentCount()>1?p.getComponent(1).getPreferredSize().width:0;}
        @Override public Dimension preferredLayoutSize(Container p){if(p.getComponentCount()==0)return new Dimension();var c=p.getComponent(0).getPreferredSize();return new Dimension(c.width+gap()+action(p),c.height);}
        @Override public int height(Container p,int width){return p.getComponentCount()==0?0:Composition.height(p.getComponent(0),Math.max(0,width-gap()-action(p)));}''',
     '''        /** The gap before the row's action; a control without one (Reset in the row's menu, spec 12.1) reserves none. */
        int gap(Container p){return p.getComponentCount()>1?ThemePainting.metric(context,DesignMetrics.Role.COLUMN_GAP):0;}
        int action(Container p){return p.getComponentCount()>1?p.getComponent(1).getPreferredSize().width:0;}
        @Override public Dimension preferredLayoutSize(Container p){if(p.getComponentCount()==0)return new Dimension();var c=p.getComponent(0).getPreferredSize();return new Dimension(c.width+gap(p)+action(p),c.height);}
        @Override public int height(Container p,int width){return p.getComponentCount()==0?0:Composition.height(p.getComponent(0),Math.max(0,width-gap(p)-action(p)));}''', 1),
    ('''        @Override public void layoutContainer(Container p){if(p.getComponentCount()==0)return;int a=action(p),w=Math.max(0,p.getWidth()-gap()-a);p.getComponent(0).setBounds(halo(p),0,w,p.getHeight());if(p.getComponentCount()>1)p.getComponent(1).setBounds(w+gap(),0,a,p.getHeight());}''',
     '''        @Override public void layoutContainer(Container p){if(p.getComponentCount()==0)return;int a=action(p),w=Math.max(0,p.getWidth()-gap(p)-a);p.getComponent(0).setBounds(halo(p),0,w,p.getHeight());if(p.getComponentCount()>1)p.getComponent(1).setBounds(w+gap(p),0,a,p.getHeight());}''', 1)])
edit(U + "swing/ViewReconciler.java", [
    ('''        if(mounted.values().stream().anyMatch(owner -> owner instanceof BreadcrumbControls.BreadcrumbsControl crumbs && crumbs.popup.isVisible()))return true;''',
     '''        if(mounted.values().stream().anyMatch(owner -> owner instanceof BreadcrumbControls.BreadcrumbsControl crumbs && crumbs.popup.isVisible()))return true;
        if(mounted.values().stream().anyMatch(owner -> owner instanceof SettingRows.RowControl row && row.popup.isVisible()))return true;''', 1)])
PY
```

`SettingRows.java` imports `java.awt.*` and `javax.swing.*`, so `Component`, `JComponent`, `KeyStroke` and
`AbstractAction` resolve; `List` is `java.util.List` (its own import). If plan 4b reworded the anchored lines of
`SettingRows` or `SettingsTrailingLayout`, re-anchor on the same statements.

- [ ] **Step 4: Put Reset in the menu on the app's rows**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 3 step 4: Settings rows follow SettingsChrome.inlineReset. Every anchor is checked before any file is written."""
import pathlib
pending = []
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}; nothing was written")
        text = text.replace(old, new)
    pending.append((path, text))
S = "jasper-app/src/main/java/dev/jasper/app/settingsui/"
edit(S + "WorkspaceEditors.java", [
    ('''    static Editors.Factory<Float> opacity(AppearanceChoices choices) { return new Editors.Factory<>((setting,context)->new Opacity(choices,context.draft()),List.of("Enable opacity","opacity","transparency")); }''',
     '''    static Editors.Factory<Float> opacity(AppearanceChoices choices) { return new Editors.Factory<>((setting,context)->new Opacity(choices,context.draft(),context.chrome().inlineReset()),List.of("Enable opacity","opacity","transparency")); }''', 1),
    ('''        final AppearanceChoices choices;final SettingsDraft draft;float value;
        Opacity(AppearanceChoices choices,SettingsDraft draft){this.choices=choices;this.draft=draft;value=Editors.effectiveDefault(draft,UI_WORKSPACE_OPACITY);}''',
     '''        final AppearanceChoices choices;final SettingsDraft draft;final boolean inlineReset;float value;
        Opacity(AppearanceChoices choices,SettingsDraft draft){this(choices,draft,true);}
        /** {@code inlineReset} false: no row keeps a Reset slot, so neither reserves one (spec 12.1). */
        Opacity(AppearanceChoices choices,SettingsDraft draft,boolean inlineReset){this.choices=choices;this.draft=draft;this.inlineReset=inlineReset;value=Editors.effectiveDefault(draft,UI_WORKSPACE_OPACITY);}''', 1),
    ('''new Nodes.Stack(id+":inheritance-controls",state,Nodes.LayoutRole.SETTINGS_TRAILING,List.of(toggle,reserved(id+":reserved")))''',
     '''new Nodes.Stack(id+":inheritance-controls",state,Nodes.LayoutRole.SETTINGS_TRAILING,inlineReset?List.of(toggle,reserved(id+":reserved")):List.of(toggle))''', 1),
    ('''new Nodes.Stack(id+":slider-controls",state,Nodes.LayoutRole.SETTINGS_TRAILING,List.of(slider,reserved(id+":slider-reserved")))''',
     '''new Nodes.Stack(id+":slider-controls",state,Nodes.LayoutRole.SETTINGS_TRAILING,inlineReset?List.of(slider,reserved(id+":slider-reserved")):List.of(slider))''', 1)])
edit(S + "SchemaPage.java", [
    ('''    private Nodes.Node row(Row<?> row) {
        boolean enabled = row.enabled.test(context.draft());
        var state = new ControlState(true, enabled, false, row.error);
        var reset = new Nodes.Button(row.resetId(), new ControlState(row.resettable, true, false, ""), "", "reset",
            Nodes.ButtonKind.GHOST, row.resetId(), "Reset " + row.setting.label() + " to default");
        if (row.editor.fullSpan()) return row.editor.node(row.id(), state, row.highlighted);
        var control = new Nodes.Stack(row.id() + ":controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(row.editor.node(row.id(), state), reset));
        return new Nodes.SettingRow(row.rowId(), ControlState.READY, row.editor.rowLabel(row.setting.label()), row.comment, control, row.editor.dependent(), row.highlighted);
    }''', '''    private Nodes.Node row(Row<?> row) {
        boolean enabled = row.enabled.test(context.draft());
        var state = new ControlState(true, enabled, false, row.error);
        if (row.editor.fullSpan()) return row.editor.node(row.id(), state, row.highlighted);
        var controls = new ArrayList<Nodes.Node>(List.of(row.editor.node(row.id(), state)));
        if (context.chrome().inlineReset())
            controls.add(new Nodes.Button(row.resetId(), new ControlState(row.resettable, true, false, ""), "", "reset",
                Nodes.ButtonKind.GHOST, row.resetId(), "Reset " + row.setting.label() + " to default"));
        var control = new Nodes.Stack(row.id() + ":controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING, controls);
        return new Nodes.SettingRow(row.rowId(), ControlState.READY, row.editor.rowLabel(row.setting.label()), row.comment, control,
            row.editor.dependent(), row.highlighted, "", resetMenu(context, row.resetId(), row.resettable));
    }

    /**
     * Spec 12.1: a look without an inline Reset offers "Reset to Default" in the row's own menu, enabled while the value
     * differs from its effective default; {@code action} is what the row's Reset button would report.
     */
    static List<dev.jasper.ui.model.MenuItem> resetMenu(PageContext context, String action, boolean resettable) {
        return context.chrome().inlineReset() ? List.of()
            : List.of(new dev.jasper.ui.model.MenuItem(action, "Reset to Default", "", action, resettable, false));
    }''', 1),
    ('''        boolean resettable = enabled && row.shown() != row.value.defaultValue().getAsInt();
        var reset = new Nodes.Button(row.resetId(), new ControlState(resettable, true, false, ""), "", "reset",
            Nodes.ButtonKind.GHOST, row.resetId(), "Reset " + row.value.label() + " to default");
        var control = new Nodes.Stack(row.id() + ":controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING,
            List.of(row.editor.node(row.id(), state), reset));
        return new Nodes.SettingRow(row.rowId(), ControlState.READY, row.value.label(), row.value.comment().apply(row.shown()),
            control, false, row.highlighted);''', '''        boolean resettable = enabled && row.shown() != row.value.defaultValue().getAsInt();
        var controls = new ArrayList<Nodes.Node>(List.of(row.editor.node(row.id(), state)));
        if (context.chrome().inlineReset())
            controls.add(new Nodes.Button(row.resetId(), new ControlState(resettable, true, false, ""), "", "reset",
                Nodes.ButtonKind.GHOST, row.resetId(), "Reset " + row.value.label() + " to default"));
        var control = new Nodes.Stack(row.id() + ":controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING, controls);
        return new Nodes.SettingRow(row.rowId(), ControlState.READY, row.value.label(), row.value.comment().apply(row.shown()),
            control, false, row.highlighted, "", resetMenu(context, row.resetId(), resettable));''', 1)])
edit(S + "AppearanceSelection.java", [
    ('''    private final SettingsDraft draft;
    private final Subscription listening;''', '''    private final SettingsDraft draft;
    private final PageContext context;
    private final Subscription listening;''', 1),
    ('''        this.draft = context.draft();''', '''        this.draft = context.draft();
        this.context = context;''', 1),
    ('''        var darkControls = new Nodes.Stack(id + ":dark-controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING,
            List.of(withGear(id + ":dark", plain, darkPick, darkMenu, dark), reset));
        var lightControls = new Nodes.Stack(id + ":light-controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING,
            List.of(withGear(id + ":light", plain, lightPick, lightMenu, light), lane));
        return new Nodes.Stack(id + ":selection", plain, Nodes.LayoutRole.VERTICAL, List.of(tiles,
            new Nodes.SettingRow("row:" + id, plain, UI_THEME_DARK.label(), "", darkControls, false, highlighted),
            new Nodes.SettingRow("row:" + id + ":light", plain, UI_THEME_LIGHT.label(), "", lightControls, false, highlighted)));''',
     '''        boolean inline = context.chrome().inlineReset();
        var darkControls = new Nodes.Stack(id + ":dark-controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING,
            inline ? List.of(withGear(id + ":dark", plain, darkPick, darkMenu, dark), reset) : List.of(withGear(id + ":dark", plain, darkPick, darkMenu, dark)));
        var lightControls = new Nodes.Stack(id + ":light-controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING,
            inline ? List.of(withGear(id + ":light", plain, lightPick, lightMenu, light), lane) : List.of(withGear(id + ":light", plain, lightPick, lightMenu, light)));
        // Spec 12.1: without an inline Reset, both theme rows offer Reset to Default for the policy and its two themes.
        var menu = SchemaPage.resetMenu(context, id + ":reset", modified);
        return new Nodes.Stack(id + ":selection", plain, Nodes.LayoutRole.VERTICAL, List.of(tiles,
            new Nodes.SettingRow("row:" + id, plain, UI_THEME_DARK.label(), "", darkControls, false, highlighted, "", menu),
            new Nodes.SettingRow("row:" + id + ":light", plain, UI_THEME_LIGHT.label(), "", lightControls, false, highlighted, "", menu)));''', 1)])
edit(S + "PlatformFontSize.java", [
    ('''    private final Supplier<Float> standard;''', '''    private final Supplier<Float> standard;
    private final PageContext context;''', 1),
    ('''        this.draft = context.draft();''', '''        this.context = context;
        this.draft = context.draft();''', 1),
    ('''        var control = new Nodes.Stack(id + ":toggle-controls", state, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(toggle, reset));''',
     '''        var control = new Nodes.Stack(id + ":toggle-controls", state, Nodes.LayoutRole.SETTINGS_TRAILING,
            context.chrome().inlineReset() ? List.of(toggle, reset) : List.of(toggle));''', 1),
    ('''            new Nodes.SettingRow("row:" + id, state, copy, "", control, false, highlighted),''',
     '''            new Nodes.SettingRow("row:" + id, state, copy, "", control, false, highlighted, "", SchemaPage.resetMenu(context, id + ":reset", !inherited)),''', 1)])
edit(S + "NotificationGroupsEditor.java", [
    ('''    NotificationGroupsEditor(NotificationGroupChoices choices) {this(choices,null);}
    NotificationGroupsEditor(NotificationGroupChoices choices, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit) {
        this.kit=kit;''', '''    /** Whether each group's row keeps its Reset button; otherwise Reset to Default is in the row's menu (spec 12.1). */
    private final boolean inlineReset;
    NotificationGroupsEditor(NotificationGroupChoices choices) {this(choices,null);}
    NotificationGroupsEditor(NotificationGroupChoices choices, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit) {this(choices,kit,true);}
    NotificationGroupsEditor(NotificationGroupChoices choices, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit, boolean inlineReset) {
        this.kit=kit;
        this.inlineReset=inlineReset;''', 1),
    ('''                var control=new Nodes.Stack(modeId(id,i)+":controls",plain,Nodes.LayoutRole.SETTINGS_TRAILING,List.of(choice,reset));
                lines.add(new Nodes.SettingRow(id+":row-"+i,plain,group.name(),"",control,false,highlighted));''',
     '''                var control=new Nodes.Stack(modeId(id,i)+":controls",plain,Nodes.LayoutRole.SETTINGS_TRAILING,inlineReset?List.of(choice,reset):List.of(choice));
                var menu=inlineReset?List.<dev.jasper.ui.model.MenuItem>of():List.of(new dev.jasper.ui.model.MenuItem(resetId(id,i),"Reset to Default","",resetId(id,i),plain.enabled()&&value.containsKey(group.id()),false));
                lines.add(new Nodes.SettingRow(id+":row-"+i,plain,group.name(),"",control,false,highlighted,"",menu));''', 1)])
edit(S + "Editors.java", [
    ('''        return new Factory<>((setting, context) -> new NotificationGroupsEditor(choices, context.kit()),''',
     '''        return new Factory<>((setting, context) -> new NotificationGroupsEditor(choices, context.kit(), context.chrome().inlineReset()),''', 1)])
for path, text in pending: path.write_text(text, encoding="utf-8")
PY
```

The `WorkspaceEditors` anchors are the text plan 5a Task 7 leaves: it turned the four `UI_WORKSPACE_OPACITY.defaultValue()`
into `Editors.effectiveDefault(draft,UI_WORKSPACE_OPACITY)`, one of them in the constructor quoted above, and the new
three-argument constructor keeps that expression (5a's `DefaultsPrecedenceTest` forbids `UI_WORKSPACE_OPACITY.defaultValue()`
in the file). The script checks every anchor in every file before it writes one, so a stop leaves no file edited;
re-anchor on the text as it stands, never back to the pre-5a expression. `SchemaPage` already imports `ArrayList` and `List`. Add the app
test's `migration.csv` row:

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
row = ("jasper-app/src/test/java/dev/jasper/app/settingsui/ResetMenuTest.java,app.settingsui,ResetMenuTest,nonvisual-helper,none,none,"
       "jasper-app/src/test/java/dev/jasper/app/settingsui/ResetMenuTest.java,Test/fixture ownership; New in Enterprise plan 9a Task 3; Reset to Default in the row menu")
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --tests 'dev.jasper.ui.model.*' :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.config.DefaultsPrecedenceTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `SettingRowMenuTest` (3), `ResetMenuTest` (4), 5a's `DefaultsPrecedenceTest`, and every existing kit,
Settings and plugin test (the default look builds the same descriptors: a row's menu is empty and its trailing stack
keeps both children).

- [ ] **Step 6: Prove the default look unchanged**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
```

Expected: `changed: none`.

- [ ] **Step 7: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java jasper-ui/src/main/java/dev/jasper/ui/swing/SettingRows.java \
  jasper-ui/src/main/java/dev/jasper/ui/swing/Stacks.java jasper-ui/src/main/java/dev/jasper/ui/swing/ViewReconciler.java \
  jasper-ui/src/test/java/dev/jasper/ui/swing/SettingRowMenuTest.java jasper-app/src/main/java/dev/jasper/app/settingsui \
  jasper-app/src/test/java/dev/jasper/app/settingsui/ResetMenuTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Settings chrome: Reset to Default in the row's menu

jasper-ui's SettingRow gains contextActions, shown on the popup trigger
and on Shift+F10 or the menu key while the row has any; a trailing control
without an action reserves no gap. Under SettingsChrome.inlineReset false
the schema, external and full-span rows drop their Reset buttons and offer
Reset to Default in their own menu, enabled while the value differs from
its effective default. The default look builds the same descriptors.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: The classic Settings frame

A frame that is not the window's title row (spec 12.1, `integratedTitle = false`) draws the classic dialog of the
mocks: a native "Settings" title above it, the search strip and its rule, the navigation on the `navigation_surface`
slot, ruled header and sidebar, and the footer band; the classic catalog gains the measured Settings values. The
integrated frame is today's.

**Files:**
- Replace: `jasper-ui/src/main/java/dev/jasper/ui/swing/SettingsShell.java` (whole file)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (one role; the classic Settings values)
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv`, `classic-roles.tsv`, `jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java`, `ClassicCatalogTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsView.java:43-57`, `SettingsDialog.java:166,176-177`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/SettingsShellFrameTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java` (one test)

**Interfaces:**
- Consumes: `Kit.colors()` (`ColorSlots.dialogSurface()`, `navigationSurface()`), `ThemePainting.role`, `ThemePainting.painted`,
  `DerivedColor.SURFACE_FOOTER`, `DerivedColor.BORDER_ROW`, `PaintPair`, `SurfaceContrast.Guard.STRUCTURAL`; Task 1's
  `SettingsDialog.chrome()`.
- Produces: `public record SettingsShell.Frame(boolean integratedTitle, boolean collapsibleNavigation)` with
  `Frame.INTEGRATED = new Frame(true, false)`; `SettingsShell(KitContext, Frame, JComponent search, navigation, header,
  banner, body, footer)` (the 7-argument form is `Frame.INTEGRATED`); `static List<PaintPair> SettingsShell.framePairs(KitContext)`;
  `DesignMetrics.Role.SETTINGS_HEADER_INSET(20)`; `SettingsView(KitContext, SettingsShell.Frame, Consumer<UiEvent>, Runnable)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/SettingsShellFrameTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TreeItem;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 12.1 and plan 9a's "Mock measurements": the classic frame's geometry and surfaces. */
class SettingsShellFrameTest {
    static final SettingsShell.Frame CLASSIC = new SettingsShell.Frame(false, true);

    /** A shell of the Settings dialog's own views, as the app's SettingsView builds them. */
    record Shell(SettingsShell shell, List<KitView> views) implements AutoCloseable {
        @Override public void close() { shell.close(); views.forEach(KitView::close); }
    }

    static Shell shell(KitContext context, SettingsShell.Frame frame) {
        var search = new KitView(context, new ViewSpec(new Nodes.Field("search", ControlState.READY, Nodes.FieldKind.SEARCH, "", "Search settings",
            "Search settings", "")), event -> { });
        var tree = new Nodes.Tree("pages", ControlState.READY, List.of(new TreeItem("ab", "Appearance & Behavior", Optional.empty(), false,
            List.of(new TreeItem("appearance", "Appearance", Optional.empty(), true, List.of()))), new TreeItem("keymap", "Keymap", Optional.empty(), true, List.of())),
            Set.of("ab"), Set.of("appearance"), "");
        var navigation = new KitView(context, new ViewSpec(new Nodes.Stack("settings-navigation", ControlState.READY, Nodes.LayoutRole.SETTINGS_NAVIGATION, List.of(tree))), event -> { });
        var header = new KitView(context, new ViewSpec(new Nodes.Stack("header", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
            new Nodes.Stack("history", ControlState.READY, Nodes.LayoutRole.JOINED_ACTIONS, List.of(
                new Nodes.Button("back", ControlState.READY, "", "chevron.left", Nodes.ButtonKind.GHOST, "back", "Back"),
                new Nodes.Button("forward", new ControlState(true, false, false, ""), "", "chevron.right", Nodes.ButtonKind.GHOST, "forward", "Forward"))),
            new Nodes.Text("breadcrumb-parent", ControlState.READY, "Appearance & Behavior", Nodes.TextRole.DESCRIPTION),
            new Nodes.Icon("breadcrumb-separator", ControlState.READY, "chevron.right", Nodes.Hue.NONE, Nodes.IconSize.CAPTION, "Breadcrumb separator"),
            new Nodes.Text("breadcrumb", ControlState.READY, "Appearance", Nodes.TextRole.WINDOW_TITLE)))), event -> { });
        var banner = new KitView(context, new ViewSpec(new Nodes.Text("banner-text", ControlState.READY, " ", Nodes.TextRole.BODY)), event -> { });
        banner.setVisible(false);
        var footer = new KitView(context, new ViewSpec(new Nodes.Stack("footer", ControlState.READY, Nodes.LayoutRole.SETTINGS_FOOTER, List.of(
            new Nodes.Button("help", ControlState.READY, "", "question", Nodes.ButtonKind.GHOST, "help", "Help", Nodes.ButtonPresentation.HELP),
            new Nodes.Stack("message-line", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(new Nodes.Text("message", ControlState.READY, "", Nodes.TextRole.BODY))),
            new Nodes.Button("cancel", ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, "cancel", ""),
            new Nodes.Button("apply", new ControlState(true, false, false, ""), "Apply", "", Nodes.ButtonKind.SECONDARY, "apply", ""),
            new Nodes.Button("ok", ControlState.READY, "OK", "", Nodes.ButtonKind.PRIMARY, "ok", "")))), event -> { });
        JScrollPane body = SettingsShell.pageScroller(context);
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder()); body.setOpaque(false); body.getViewport().setOpaque(false);
        var shell = new SettingsShell(context, frame, search, navigation, header, banner, body, footer);
        return new Shell(shell, List.of(search, navigation, header, banner, footer));
    }

    static void layout(java.awt.Component component) {
        component.doLayout();
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) layout(child);
    }

    static BufferedImage render(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.printAll(g); } finally { g.dispose(); }
        return image;
    }

    @Test void theClassicFramePlacesItsPartsAsTheMocksDo() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var built = shell(context, CLASSIC)) {
                var shell = built.shell();
                shell.setSize(1040, 691);
                for (int i = 0; i < 3; i++) layout(shell);
                var views = built.views();
                assertThat(views.get(0).getBounds()).as("search").isEqualTo(new Rectangle(8, 8, 214, 22));
                assertThat(views.get(1).getY()).as("navigation below the strip rule and the gap").isEqualTo(43);
                assertThat(views.get(1).getWidth()).isEqualTo(230);
                assertThat(views.get(2).getBounds()).as("header").isEqualTo(new Rectangle(231 + 14, 0, 1040 - 231 - 28, 34));
                assertThat(views.get(4).getBounds()).as("footer").isEqualTo(new Rectangle(14, 691 - 45 + 1, 1040 - 28, 44));
                var ok = KitViewTest.find(views.get(4), "ok", JComponent.class);
                var cancel = KitViewTest.find(views.get(4), "cancel", JComponent.class);
                assertThat(ok.getWidth()).isEqualTo(98); assertThat(ok.getHeight()).isEqualTo(24);
                assertThat(ok.getX() + ok.getWidth()).as("OK ends at the footer's side").isEqualTo(1040 - 28);
                assertThat(ok.getY()).as("centred below the rule").isEqualTo(10);
                assertThat(cancel.getWidth()).isEqualTo(98);
                assertThat(KitViewTest.find(views.get(4), "help", JComponent.class).getWidth()).isEqualTo(22);
                var image = render(shell);
                var theme = context.theme();
                assertThat(image.getRGB(115, 4) & 0xFFFFFF).as("strip").isEqualTo(SemanticColor.SURFACE_SIDEBAR.color(theme).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(115, 38) & 0xFFFFFF).as("strip rule").isEqualTo(DerivedColor.BORDER_ROW.color(theme).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(200, 400) & 0xFFFFFF).as("navigation slot").isEqualTo(context.kit().colors().navigationSurface().color(theme).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(230, 400) & 0xFFFFFF).as("sidebar rule").isEqualTo(ThemePainting.painted(context,
                    new dev.jasper.ui.theme.PaintPair(SemanticColor.BORDER_WINDOW, context.kit().colors().navigationSurface(), SurfaceContrast.Guard.STRUCTURAL)).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(600, 34) & 0xFFFFFF).as("header rule").isEqualTo(ThemePainting.painted(context,
                    new dev.jasper.ui.theme.PaintPair(SemanticColor.BORDER_WINDOW, context.kit().colors().dialogSurface(), SurfaceContrast.Guard.STRUCTURAL)).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(600, 300) & 0xFFFFFF).as("body").isEqualTo(context.kit().colors().dialogSurface().color(theme).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(300, 691 - 45) & 0xFFFFFF).as("footer rule").isEqualTo(ThemePainting.painted(context,
                    new dev.jasper.ui.theme.PaintPair(SemanticColor.BORDER_WINDOW, DerivedColor.SURFACE_FOOTER, SurfaceContrast.Guard.STRUCTURAL)).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(300, 691 - 5) & 0xFFFFFF).as("footer band").isEqualTo(DerivedColor.SURFACE_FOOTER.color(theme).getRGB() & 0xFFFFFF);
            } finally { release(context); }
        });
    }

    @Test void theFrameFollowsATallerSearchFieldAndHeader() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var parts = new JComponent[6];
            for (int i = 0; i < parts.length; i++) { parts[i] = new javax.swing.JPanel(); parts[i].setOpaque(false); }
            parts[0].setPreferredSize(new java.awt.Dimension(214, 30));
            parts[2].setPreferredSize(new java.awt.Dimension(400, 40));
            parts[3].setVisible(false);
            parts[5].setPreferredSize(new java.awt.Dimension(400, 30));
            var shell = new SettingsShell(context, CLASSIC, parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
            try {
                shell.setSize(760, 691);
                shell.doLayout();
                assertThat(parts[1].getY()).as("8 + 30 + 8, the rule, the gap").isEqualTo(8 + 30 + 8 + 1 + 4);
                assertThat(shell.titleRowHeight()).isEqualTo(40);
                assertThat(parts[2].getHeight()).isEqualTo(40);
                assertThat(parts[4].getY()).as("the body below the taller header's rule").isEqualTo(41);
                assertThat(parts[5].getY() + parts[5].getHeight()).as("the footer inside the shell").isEqualTo(691);
                var image = render(shell);
                assertThat(image.getRGB(115, 46) & 0xFFFFFF).as("the strip rule moved").isEqualTo(DerivedColor.BORDER_ROW.color(context.theme()).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(600, 40) & 0xFFFFFF).as("the header rule moved").isNotEqualTo(image.getRGB(600, 300) & 0xFFFFFF);
            } finally { shell.close(); release(context); }
        });
    }

    @Test void theRulesMeetTheirGuardOverEveryPalette() {
        Edt.run(() -> {
            var palettes = new java.util.ArrayList<>(KitFixtures.themes());
            palettes.addAll(KitFixtures.degenerate());
            for (var theme : palettes) {
                var context = KitFixtures.context(theme, KitFixtures.enterprise());
                try {
                    for (var pair : SettingsShell.framePairs(context))
                        assertThat(pair.guard().holds(ThemePainting.painted(context, pair), pair.against().color(theme))).as(pair.toString()).isTrue();
                } finally { release(context); }
            }
        });
    }

    @Test void theIntegratedFrameIsTodaysShell() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var before = shell(context, SettingsShell.Frame.INTEGRATED)) {
                var shell = before.shell();
                shell.setSize(1000, 700);
                for (int i = 0; i < 3; i++) layout(shell);
                int top = shell.titleRowHeight();
                assertThat(before.views().get(0).getY()).as("the search sits under the title row").isEqualTo(top);
                assertThat(before.views().get(2).getX()).isEqualTo(Math.min(context.metrics().value(DesignMetrics.Role.SETTINGS_SIDEBAR_WIDTH), 1000 / 3)
                    + context.metrics().value(DesignMetrics.Role.SETTINGS_HEADER_INSET));
                assertThat(context.metrics().value(DesignMetrics.Role.SETTINGS_HEADER_INSET)).as("today's header inset")
                    .isEqualTo(context.metrics().value(DesignMetrics.Role.SETTINGS_PAGE_INSET));
            } finally { release(context); }
        });
    }
}
```

`KitFixtures.enterprise()` (plan 4a) is the classic catalog under the Enterprise tokens and `KitFixtures.enterpriseLight()`
(plan 4c) the Enterprise Light fixture theme; both are jasper-ui test fixtures.

Spec 16.1.2 has `PaintedContrastTest` sample every chrome painter's declared pairs as painted over the shared palette
sweep; the classic frame's three guarded rules join it. Insert the test before `searchHint`, as plan 7 inserts its dock
test there:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 4 step 1: PaintedContrastTest samples the classic Settings frame's guarded rules over every palette."""
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java"); text = path.read_text(encoding="utf-8")
anchor = '''    private static boolean searchHint(GeneratedTheme theme, Color expected) {'''
if text.count(anchor) != 1: raise SystemExit("PaintedContrastTest: searchHint moved")
block = '''    /** Spec 16.1.2 for plan 9a's classic Settings frame: each window rule it declares is painted as its guard makes it, everywhere. */
    @Test void theClassicSettingsFramePaintsEachRuleAsItsGuardMakesItOverEveryPalette() {
        var palettes = palettes();
        palettes.add(KitFixtures.enterpriseLight());
        for (var theme : palettes) {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            var parts = new JComponent[6];
            for (int i = 0; i < parts.length; i++) { parts[i] = new JPanel(); parts[i].setOpaque(false); }
            parts[3].setVisible(false);
            var shell = new SettingsShell(context, new SettingsShell.Frame(false, true), parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
            try {
                shell.setSize(760, 600);
                shell.doLayout();
                var image = paint(shell);
                var pairs = SettingsShell.framePairs(context);
                assertThat(pairs).as("the sidebar, header and footer rules").hasSize(3);
                // Beside the navigation, under the header (above the body), over the footer band.
                int[][] at = {{parts[1].getWidth(), parts[1].getY() + 20}, {parts[4].getX() + 40, parts[4].getY() - 1}, {300, parts[5].getY() - 1}};
                for (int i = 0; i < pairs.size(); i++) {
                    var pair = pairs.get(i);
                    String where = name(theme) + ", " + pair.slot().key() + " on " + pair.against().key();
                    Color expected = SurfaceContrast.painted(theme, pair);
                    assertThat(pair.guard().holds(expected, pair.against().color(theme))).as(where + " meets its guard").isTrue();
                    assertThat(image.getRGB(at[i][0], at[i][1])).as(where + " is painted " + ColorMath.hex(expected)).isEqualTo(expected.getRGB());
                }
            } finally { shell.close(); context.icons().close(); context.binding().close(); }
        }
    }

'''
path.write_text(text.replace(anchor, block + anchor), encoding="utf-8")
PY
```

`PaintedContrastTest` imports `dev.jasper.ui.swing.*` (so `SettingsShell`), `dev.jasper.ui.theme.*`, `java.awt.*`,
`javax.swing.*` and `KitFixtures`; `palettes()` returns a mutable list (4c's fact) and `paint` paints a component's
whole tree. The parts are empty panels, so each sampled pixel is the shell's own paint: the rule is one hairline, and
the body and footer start one hairline below the header and footer rules.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.SettingsShellFrameTest'`
Expected: FAIL at compile: "cannot find symbol: class Frame", "variable SETTINGS_HEADER_INSET", "method framePairs".

- [ ] **Step 3: Write the frame**

Replace `jasper-ui/src/main/java/dev/jasper/ui/swing/SettingsShell.java` with:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import javax.swing.*;

/** Reusable Settings shell. The caller owns its search, navigation, header, banner, page scroller and footer views. */
public final class SettingsShell extends JPanel implements AutoCloseable {
    /**
     * How the shell meets its window (Enterprise presentation spec 12.1).
     *
     * @param integratedTitle whether the shell's header row is the window's title row (a unified title bar); otherwise the
     *     window shows its own title, and the shell draws a search strip, ruled frames and a footer band
     * @param collapsibleNavigation whether the navigation's sections collapse behind a disclosure triangle
     */
    public record Frame(boolean integratedTitle, boolean collapsibleNavigation) {
        /** Today's frame: the header row is the title row and every section stays open. */
        public static final Frame INTEGRATED = new Frame(true, false);
    }

    private final KitContext context;
    private final Frame frame;
    private final JComponent search, navigation, header, banner, body, footer;
    private final AutoCloseable appearance;

    /** A shell in {@link Frame#INTEGRATED}. */
    public SettingsShell(KitContext context, JComponent search, JComponent navigation, JComponent header,
                         JComponent banner, JComponent body, JComponent footer) {
        this(context, Frame.INTEGRATED, search, navigation, header, banner, body, footer);
    }

    /** A shell in {@code frame}. */
    public SettingsShell(KitContext context, Frame frame, JComponent search, JComponent navigation, JComponent header,
                         JComponent banner, JComponent body, JComponent footer) {
        this.context = context; this.frame = Objects.requireNonNull(frame, "frame");
        this.search = search; this.navigation = navigation; this.header = header;
        this.banner = banner; this.body = body; this.footer = footer;
        setOpaque(false); setLayout(null);
        for (JComponent child : new JComponent[]{search, navigation, header, banner, body, footer}) add(child);
        for (JComponent child : new JComponent[]{search, navigation, header, banner, footer})
            if (child instanceof KitView view) view.settingsChrome(child == header);
        appearance = context.binding().subscribe(pair -> { revalidate(); repaint(); });
    }
    /** Marks a page's own view as part of the Settings surface, so its controls take the Settings geometry. */
    public static void settingsPage(KitView view) { view.settingsChrome(false); }
    /** The page scroller the shell expects: the OVERLAY scroll policy, so a page never loses width to a resting bar. */
    public static JScrollPane pageScroller(KitContext context) { return new OverlayScrollPane(java.util.Objects.requireNonNull(context)); }

    /**
     * The pairs the classic frame paints through their guards: its window rules beside the navigation, under the header and
     * over the footer band (Enterprise presentation plan 9a, Rulings 7). The strip rule is a faint divider and is unguarded.
     */
    public static List<PaintPair> framePairs(KitContext context) {
        var slots = context.kit().colors();
        return List.of(new PaintPair(SemanticColor.BORDER_WINDOW, slots.navigationSurface(), SurfaceContrast.Guard.STRUCTURAL),
            new PaintPair(SemanticColor.BORDER_WINDOW, slots.dialogSurface(), SurfaceContrast.Guard.STRUCTURAL),
            new PaintPair(SemanticColor.BORDER_WINDOW, DerivedColor.SURFACE_FOOTER, SurfaceContrast.Guard.STRUCTURAL));
    }

    private int metric(DesignMetrics.Role role) { return ThemePainting.metric(context, role); }
    private int sidebar() { return Math.min(metric(DesignMetrics.Role.SETTINGS_SIDEBAR_WIDTH), Math.max(0, getWidth() / 3)); }
    private int footerHeight() { return Math.max(metric(DesignMetrics.Role.SETTINGS_FOOTER_HEIGHT), footer.getPreferredSize().height); }
    /** The classic frame's search strip: the search field with the sidebar inset above and below it. */
    private int strip() { return 2 * metric(DesignMetrics.Role.SETTINGS_SIDEBAR_INSET) + search.getPreferredSize().height; }
    /**
     * The header row's height, in pixels. In the integrated frame the shell spans the whole window, so this row is the
     * window's title row: the window controls sit over the sidebar beside it and the header's rule closes it.
     */
    public int titleRowHeight() { return Math.max(metric(DesignMetrics.Role.SETTINGS_HEADER_HEIGHT), header.getPreferredSize().height); }
    @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }

    @Override public void doLayout() {
        if (frame.integratedTitle()) { integratedLayout(); return; }
        int left = sidebar(), rule = metric(DesignMetrics.Role.HAIRLINE), x = left + rule, width = Math.max(0, getWidth() - x);
        int top = titleRowHeight(), bottom = footerHeight();
        int inset = metric(DesignMetrics.Role.SETTINGS_SIDEBAR_INSET), side = metric(DesignMetrics.Role.SETTINGS_FOOTER_SIDE);
        int headerInset = metric(DesignMetrics.Role.SETTINGS_HEADER_INSET);
        search.setBounds(inset, inset, Math.max(0, left - 2 * inset), search.getPreferredSize().height);
        int navTop = strip() + rule + metric(DesignMetrics.Role.SETTINGS_SEARCH_GAP);
        navigation.setBounds(0, navTop, left, Math.max(0, getHeight() - bottom - navTop));
        header.setBounds(x + headerInset, 0, Math.max(0, width - 2 * headerInset), top);
        int bannerHeight = banner.isVisible() ? banner.getPreferredSize().height : 0;
        banner.setBounds(x, top + rule, width, bannerHeight);
        body.setBounds(x, top + rule + bannerHeight, width, Math.max(0, getHeight() - top - rule - bannerHeight - bottom));
        footer.setBounds(side, Math.max(0, getHeight() - bottom + rule), Math.max(0, getWidth() - 2 * side), Math.max(0, bottom - rule));
    }

    private void integratedLayout() {
        int left = sidebar(), width = Math.max(0, getWidth() - left);
        int top = titleRowHeight();
        int bottom = footerHeight();
        int inset = metric(DesignMetrics.Role.SETTINGS_SIDEBAR_INSET), side = metric(DesignMetrics.Role.SETTINGS_FOOTER_SIDE);
        int searchHeight = search.getPreferredSize().height;
        search.setBounds(inset, top, Math.max(0, left - 2 * inset), searchHeight);
        int navTop = top + searchHeight + metric(DesignMetrics.Role.SETTINGS_SEARCH_GAP);
        // The navigation spans the sidebar; its rows keep the navigation inset as their gutter (ViewReconciler).
        navigation.setBounds(0, navTop, left, Math.max(0, getHeight() - bottom - navTop));
        header.setBounds(left + metric(DesignMetrics.Role.SETTINGS_HEADER_INSET), 0, Math.max(0, width - 2 * metric(DesignMetrics.Role.SETTINGS_HEADER_INSET)), top);
        int bannerHeight = banner.isVisible() ? banner.getPreferredSize().height : 0;
        banner.setBounds(left, top, width, bannerHeight);
        body.setBounds(left, top + bannerHeight, width, Math.max(0, getHeight() - top - bannerHeight - bottom));
        footer.setBounds(side, Math.max(0, getHeight() - bottom), Math.max(0, getWidth() - 2 * side), bottom);
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D)graphics.create();
        try {
            var slots = context.kit().colors();
            int left = sidebar(), bottom = footerHeight();
            g.setColor(ThemePainting.color(context, slots.dialogSurface())); g.fillRect(0, 0, getWidth(), getHeight());
            if (frame.integratedTitle()) {
                g.setColor(ThemePainting.color(context, slots.navigationSurface())); g.fillRect(0, 0, left, getHeight());
                g.fillRect(0, getHeight() - bottom, getWidth(), bottom);
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
                g.drawLine(left, 0, left, getHeight() - bottom);
                g.drawLine(left, header.getHeight(), getWidth(), header.getHeight());
                g.drawLine(0, getHeight() - bottom, getWidth(), getHeight() - bottom);
                return;
            }
            int rule = metric(DesignMetrics.Role.HAIRLINE), strip = strip(), top = titleRowHeight();
            var pairs = framePairs(context);
            g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_SIDEBAR)); g.fillRect(0, 0, left, strip);
            g.setColor(ThemePainting.role(context, DerivedColor.BORDER_ROW)); g.fillRect(0, strip, left, rule);
            g.setColor(ThemePainting.color(context, slots.navigationSurface())); g.fillRect(0, strip + rule, left, Math.max(0, getHeight() - bottom - strip - rule));
            g.setColor(ThemePainting.role(context, DerivedColor.SURFACE_FOOTER)); g.fillRect(0, getHeight() - bottom, getWidth(), bottom);
            g.setColor(ThemePainting.painted(context, pairs.get(0))); g.fillRect(left, 0, rule, Math.max(0, getHeight() - bottom));
            g.setColor(ThemePainting.painted(context, pairs.get(1))); g.fillRect(left + rule, top, Math.max(0, getWidth() - left - rule), rule);
            g.setColor(ThemePainting.painted(context, pairs.get(2))); g.fillRect(0, getHeight() - bottom, getWidth(), rule);
        } finally { g.dispose(); }
    }
    @Override public void close() { try { appearance.close(); } catch (Exception failure) { System.getLogger(SettingsShell.class.getName()).log(System.Logger.Level.WARNING, "Settings shell cleanup failed", failure); } }
}
```

The integrated branch is today's `doLayout` and `paintComponent` with two value substitutions that the default kit makes
equal: `SETTINGS_HEADER_INSET` is `SETTINGS_PAGE_INSET` (20) in the default catalog, and the default colour slots are
`surface.dialog` and `surface.sidebar` (fact 4). `ThemePainting.color(KitContext, SemanticColor)` takes the slots'
`SemanticColor`s; `ThemePainting.role` and `painted` are plan 4b's package helpers.

- [ ] **Step 4: Add the role and the classic Settings values**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 4 step 4: SETTINGS_HEADER_INSET and the classic catalog's Settings frame values (Mock measurements)."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
M = "jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java"
path = pathlib.Path(M); text = path.read_text(encoding="utf-8")
start = text.find("public enum Role")
if start < 0: raise SystemExit("DesignMetrics: no Role enum")
last = re.compile(r"\b([A-Z][A-Z0-9_]*)\((\d+)\);").search(text, start)
if not last: raise SystemExit("DesignMetrics: the role list's end moved")
text = text[:last.start()] + f"{last.group(1)}({last.group(2)}),\n        // Plan 9a: the Settings header's inset from the body (today the page inset); its classic value is the mocks' 14.\n        SETTINGS_HEADER_INSET(20);" + text[last.end():]
CLASSIC = [("SETTINGS_HEADER_HEIGHT", 34), ("SETTINGS_FOOTER_HEIGHT", 45), ("SETTINGS_FOOTER_SIDE", 14), ("SETTINGS_ACTION_MIN_WIDTH", 98),
    ("CIRCULAR_ACTION_SIZE", 22), ("SETTINGS_HEADER_GAP", 10), ("SETTINGS_HEADER_TITLE_GAP", 31), ("SETTINGS_CRUMB_GAP", 6),
    ("SETTINGS_PAGE_INSET", 16), ("SETTINGS_PAGE_TOP", 10), ("SETTINGS_CARD_WIDTH", 4000), ("SETTINGS_SECTION_GAP", 13),
    ("SETTINGS_CHOICE_WIDTH", 220), ("SETTINGS_RICH_CHOICE_WIDTH", 220), ("SETTING_CHOICE_WIDTH", 220), ("SETTINGS_HEADER_INSET", 14)]
anchor = "        values.put(Role.THEME_PREVIEW_CAPTION_GAP, 8);\n"
if text.count(anchor) != 1: raise SystemExit("DesignMetrics: the classic delta's 4a end moved")
puts = "        // Plan 9a: the classic Settings frame (Mock measurements).\n" + "".join(f"        values.put(Role.{n}, {v});\n" for n, v in CLASSIC)
text = text.replace(anchor, anchor + puts, 1)
path.write_text(text, encoding="utf-8")
T = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics")
compact = T / "compact-roles.tsv"; compact.write_text(compact.read_text(encoding="utf-8") + "SETTINGS_HEADER_INSET\t20\n", encoding="utf-8")
classic = T / "classic-roles.tsv"; rows = classic.read_text(encoding="utf-8").splitlines()
rows.append("SETTINGS_HEADER_INSET\t20")
values = dict(CLASSIC)
seen = set()
for i, row in enumerate(rows):
    name = row.split("\t")[0]
    if name in values: rows[i] = f"{name}\t{values[name]}"; seen.add(name)
if seen != set(values): raise SystemExit(f"classic-roles.tsv: missing {sorted(set(values) - seen)}")
classic.write_text("\n".join(rows) + "\n", encoding="utf-8")
N = "jasper-ui/src/test/java/dev/jasper/ui/metrics/"
path = pathlib.Path(N + "NewRolesTest.java"); text = path.read_text(encoding="utf-8")
added = re.search(r"static final List<String> ADDED = List\.of\((.*?)\);", text, re.S)
if not added: raise SystemExit("NewRolesTest: ADDED moved")
text = text[:added.end(1)] + ', "SETTINGS_HEADER_INSET"' + text[added.end(1):]
path.write_text(text, encoding="utf-8")
edit(N + "ClassicCatalogTest.java", [
    ('''role != DesignMetrics.Role.FIELD_RADIUS''',
     '''role != DesignMetrics.Role.FIELD_RADIUS
                && !java.util.Set.of("SETTINGS_HEADER_INSET", "SETTINGS_NAV_CHILD_INDENT").contains(name)''', 1)])
PY
```

`SETTINGS_NAV_CHILD_INDENT` is Task 5's role; naming it here keeps the exception in one place. If a later plan already
turned the `FIELD_RADIUS` condition into a set of names, add the two names to that set instead.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.SettingsShellFrameTest' --tests 'dev.jasper.ui.metrics.*' --tests 'dev.jasper.ui.swing.SettingsKitTest' --tests 'dev.jasper.ui.swing.SettingsControlsTest' :jasper-app:test --tests 'dev.jasper.app.appearance.PaintedContrastTest' --continue`
Expected: PASS: `SettingsShellFrameTest` (4), `NewRolesTest`, `ClassicCatalogTest`, `DesignMetricsTest` (the two frozen
tables), the Settings kit tests, and `PaintedContrastTest` with
`theClassicSettingsFramePaintsEachRuleAsItsGuardMakesItOverEveryPalette` (the theme set, the degenerate palettes and
Enterprise Light).

- [ ] **Step 6: Give the dialog its frame**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 4 step 6: SettingsView builds the shell in the dialog's frame; a native title is not integrated."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-app/src/main/java/dev/jasper/app/settingsui/"
edit(S + "SettingsView.java", [
    ('''    SettingsView(KitContext context, Consumer<UiEvent> events, Runnable cancelled) {''',
     '''    SettingsView(KitContext context, Consumer<UiEvent> events, Runnable cancelled) {
        this(context, dev.jasper.ui.swing.SettingsShell.Frame.INTEGRATED, events, cancelled);
    }

    /** The panel in {@code frame}: the look's title and navigation (spec 12.1). */
    SettingsView(KitContext context, dev.jasper.ui.swing.SettingsShell.Frame frame, Consumer<UiEvent> events, Runnable cancelled) {''', 1),
    ('''        shell = new dev.jasper.ui.swing.SettingsShell(context, search, navigation, header, banner, body, footer);''',
     '''        shell = new dev.jasper.ui.swing.SettingsShell(context, frame, search, navigation, header, banner, body, footer);''', 1)])
edit(S + "SettingsDialog.java", [
    ('''        view = new SettingsView(kit, this::event, this::cancel);''',
     '''        view = new SettingsView(kit, new dev.jasper.ui.swing.SettingsShell.Frame(hooks.chrome().integratedTitle(), hooks.chrome().collapsibleNav()),
            this::event, this::cancel);''', 1),
    ('''        // The shell's sidebar and header reach the window's top: they are its title row, as in the Settings mocks.
        surface.integratedTitle(view::titleRowHeight);''',
     '''        // The shell's sidebar and header reach the window's top and are its title row; a look with a native title (spec
        // 12.1) keeps the window's "Settings" title above the shell.
        if (hooks.chrome().integratedTitle()) surface.integratedTitle(view::titleRowHeight);''', 1)])
PY
```

- [ ] **Step 7: Run the Settings tests and prove the default look unchanged**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.shortcuthelp.*' --continue
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
```

Expected: PASS for the tests; `changed: none` (the default kit's integrated frame is today's shell).

- [ ] **Step 8: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/SettingsShell.java jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java \
  jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-roles.tsv \
  jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java jasper-ui/src/test/java/dev/jasper/ui/metrics/ClassicCatalogTest.java \
  jasper-ui/src/test/java/dev/jasper/ui/swing/SettingsShellFrameTest.java jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java \
  jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsView.java jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsDialog.java
git commit -F - <<'EOF'
Settings chrome: the classic Settings frame

SettingsShell.Frame says whether the shell's header row is the window's
title row. A look with a native title gets the classic dialog of the
mocks: a search strip ruled at its bottom, the navigation on the
navigation_surface slot, border.window rules through the structural guard
beside the navigation, under the header and over a surface.footer band;
PaintedContrastTest samples those rules over every palette. The classic
catalog takes the measured Settings values; the integrated frame is
today's shell, byte for byte.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: The collapsible navigation

Under `collapsibleNav` (spec 12.1) each section of the navigation has a ▼/▶ triangle: a click on the triangle (or Left
and Right) closes and opens the section, a click on its title selects its page, and the state lasts for one opening.
The classic catalog places the rows as the mocks do (Rulings 13); the selection is the sidebar's full width.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/SettingsShell.java` (Task 4's file), `KitView.java:130-141`, `ViewReconciler.java:175-180`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Trees.java` (post-4c text), `Buttons.java` (post-4c text), `InteractiveRows.java:1109`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (two roles, four classic values), the two role tables, `NewRolesTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/PageTree.java`, `SettingsDialog.java:183-185,190-209,252-258`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/CollapsibleNavigationTest.java`, `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsNavigationTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (one row)

**Interfaces:**
- Consumes: Task 4's `SettingsShell.Frame.collapsibleNavigation()`; plan 4c's `TreeFace.disclosure()`, `styleDisclosure`,
  `KitButton.disclosure`, `TreeFace.box`.
- Produces: package-private `void KitView.collapsibleNavigation(boolean)` and `boolean collapsibleNavigation()`;
  `boolean Trees.TreeControl.collapsibleNavigation`, `void Trees.TreeControl.boxed(boolean)`; `int
  Buttons.KitButton.disclosureWidth`; `DesignMetrics.Role.SETTINGS_NAV_CHILD_INDENT(0)`, `SETTINGS_NAV_TEXT_GAP(6)`;
  package-private `PageTree.collapsible(boolean)`, `expanded(String, boolean)`, `reveal(String)`, `isCollapsed(String)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/CollapsibleNavigationTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TreeItem;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 12.1: the Settings navigation's ▼ sections, and their classic geometry (plan 9a Rulings 13). */
class CollapsibleNavigationTest {
    static ViewSpec navigation(Set<String> open) {
        var tree = new Nodes.Tree("pages", ControlState.READY, List.of(
            new TreeItem("ab", "Appearance & Behavior", Optional.empty(), false, List.of(new TreeItem("appearance", "Appearance", Optional.empty(), true, List.of()))),
            new TreeItem("keymap", "Keymap", Optional.empty(), true, List.of())), open, Set.of("appearance"), "");
        return new ViewSpec(new Nodes.Stack("settings-navigation", ControlState.READY, Nodes.LayoutRole.SETTINGS_NAVIGATION, List.of(tree)));
    }

    static KitView view(KitContext context, boolean collapsible, List<UiEvent> events) {
        var view = new KitView(context, navigation(Set.of("ab")), events::add);
        view.settingsChrome(false);
        view.collapsibleNavigation(collapsible);
        view.setSize(230, 300);
        InteractiveRowsTest.layout(view);
        return view;
    }

    @Test void aCollapsibleNavigationsTriangleTogglesAndItsKeysCloseAndOpen() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = view(context, true, events)) {
                var tree = (Trees.TreeControl) view.control("pages");
                assertThat(tree.collapsibleNavigation).isTrue();
                ((JButton) tree.mounted.get("ab").leading.getComponent(0)).doClick(0);
                assertThat(events).containsExactly(new UiEvent.Expanded("pages", "ab", false));
                events.clear();
                tree.select("ab", false);
                tree.canvas.getActionMap().get("LEFT").actionPerformed(new java.awt.event.ActionEvent(tree.canvas, 0, ""));
                assertThat(events).endsWith(new UiEvent.Expanded("pages", "ab", false));
                view.update(navigation(Set.of()));
                assertThat(tree.size()).as("closed: the section and Keymap").isEqualTo(2);
            } finally { release(context); }
        });
    }

    @Test void theDefaultNavigationHasNoTriangleAndStaysOpen() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = view(context, false, events)) {
                var tree = (Trees.TreeControl) view.control("pages");
                assertThat(tree.mounted.get("ab").leading.getComponentCount()).isZero();
                tree.select("ab", false);
                tree.canvas.getActionMap().get("LEFT").actionPerformed(new java.awt.event.ActionEvent(tree.canvas, 0, ""));
                assertThat(events).noneMatch(event -> event instanceof UiEvent.Expanded);
            } finally { release(context); }
        });
    }

    @Test void theClassicNavigationIsPlacedAsTheMocksPlaceIt() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = view(context, true, new ArrayList<>())) {
                var tree = (Trees.TreeControl) view.control("pages");
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var section = tree.mounted.get("ab"); var child = tree.mounted.get("appearance"); var top = tree.mounted.get("keymap");
                assertThat(section.leading.getX()).as("the triangle at the row side").isEqualTo(9);
                assertThat(section.leading.getWidth()).as("the glyph alone").isEqualTo(6);
                assertThat(section.title.getX()).as("the section title").isEqualTo(21);
                assertThat(child.title.getX()).as("a page under a section").isEqualTo(24);
                assertThat(top.title.getX()).as("a top-level page").isEqualTo(9);
                assertThat(child.getY() - section.getY()).as("22 rows, no group gap").isEqualTo(22);
                assertThat(top.getY() - child.getY()).isEqualTo(22);
                assertThat(section.getHeight()).isEqualTo(22);
                var image = new BufferedImage(230, 300, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); try { view.printAll(g); } finally { g.dispose(); }
                int y = child.getY() + child.getHeight() / 2 + tree.canvas.getY();
                int selection = dev.jasper.ui.theme.SemanticColor.SELECTION_BG.color(context.theme()).getRGB() & 0xFFFFFF;
                assertThat(image.getRGB(0, y) & 0xFFFFFF).as("the selection reaches the left edge").isEqualTo(selection);
                assertThat(image.getRGB(229, y) & 0xFFFFFF).as("and the right edge").isEqualTo(selection);
            } finally { release(context); }
        });
    }
}
```

The row geometry the test reads is the row's own (`InteractiveRows.Row` `leading`, `title`, its bounds on the tree's
canvas); `tree.canvas.getY()` is 0 unless the pane adds an inset.

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsNavigationTest.java`:

```java
package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.Composition;
import dev.jasper.app.testsupport.KitTestSupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import org.junit.jupiter.api.Test;

/** Spec 12.1: the collapsible navigation's sections, its search and its navigation, for one opening at a time. */
class SettingsNavigationTest {
    static SettingsDialog dialog(dev.jasper.app.presentation.SettingsChrome chrome) {
        return new SettingsDialog(SettingsChromeTest.windows(), context -> CorePages.pages(AppearanceFixtures.CHOICES,
            new CorePages.SystemActions(() -> Path.of("/fixture/config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.empty()),
            page -> new GroupPage("keymap", "Keymap", Optional.empty(), false, List::of, page),
            page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page)).apply(context),
            SettingsChromeTest.hooks(chrome, Composition.CLASSIC));
    }

    static void collapse(SettingsDialog dialog, String section) {
        KitTestSupport.require(dialog.view(), "@tree/pages/" + section + "/expand", AbstractButton.class).doClick(0);
    }

    @Test void aSectionsTriangleClosesItAndATitleClickSelectsItsPage() throws Exception {
        var dialog = dialog(SettingsChromeTest.CLASSIC);
        try {
            edt(() -> {
                dialog.open(null, "keymap");
                assertThat(dialog.view().tree.visibleIds()).contains("appearance", "window");
                collapse(dialog, "appearance-behavior");
                assertThat(dialog.view().tree.isCollapsed("appearance-behavior")).isTrue();
                assertThat(dialog.view().tree.node().expandedIds()).doesNotContain("appearance-behavior").contains("terminal");
                assertThat(dialog.currentPage()).as("toggling selects nothing").isEqualTo("keymap");
                dialog.select("appearance-behavior");
                assertThat(dialog.currentPage()).isEqualTo("appearance-behavior");
            });
        } finally { edt(dialog::cancel); }
    }

    @Test void navigatingIntoACollapsedSectionOpensIt() throws Exception {
        var dialog = dialog(SettingsChromeTest.CLASSIC);
        try {
            edt(() -> {
                dialog.open(null, "window");
                dialog.select("keymap");
                collapse(dialog, "appearance-behavior");
                dialog.back();
                assertThat(dialog.currentPage()).isEqualTo("window");
                assertThat(dialog.view().tree.isCollapsed("appearance-behavior")).isFalse();
                assertThat(dialog.view().tree.node().expandedIds()).contains("appearance-behavior");
            });
        } finally { edt(dialog::cancel); }
    }

    @Test void aSearchOpensEverySectionUntilItIsCleared() throws Exception {
        var dialog = dialog(SettingsChromeTest.CLASSIC);
        try {
            edt(() -> {
                // Appearance matches "font" itself, so the search selects no page inside the collapsed section.
                dialog.open(null, "appearance");
                collapse(dialog, "terminal");
                dialog.search("font");
                assertThat(dialog.view().tree.node().expandedIds()).contains("terminal");
                dialog.search("");
                assertThat(dialog.view().tree.node().expandedIds()).doesNotContain("terminal");
            });
        } finally { edt(dialog::cancel); }
    }

    @Test void theStateIsNotSaved() throws Exception {
        var dialog = dialog(SettingsChromeTest.CLASSIC);
        edt(() -> { dialog.open(null, "keymap"); collapse(dialog, "terminal"); });
        edt(dialog::cancel);
        try {
            edt(() -> {
                dialog.open(null, "keymap");
                assertThat(dialog.view().tree.isCollapsed("terminal")).isFalse();
                assertThat(dialog.view().tree.node().expandedIds()).contains("terminal");
            });
        } finally { edt(dialog::cancel); }
    }

    @Test void theDefaultLooksNavigationHasNoTriangles() throws Exception {
        var dialog = dialog(LookRowsTest.LINUX_DEFAULT);
        try {
            edt(() -> {
                dialog.open(null, "keymap");
                assertThat(KitTestSupport.find(dialog.view(), "@tree/pages/terminal/expand", AbstractButton.class)).isNull();
            });
        } finally { edt(dialog::cancel); }
    }
}
```

`KitTestSupport.find` returns null when nothing has the name (`require` fails); `CorePages.pages` with the fake Keymap
and Plugins pages is the four-argument form of fact 1's tests.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.CollapsibleNavigationTest' :jasper-app:test --tests 'dev.jasper.app.settingsui.SettingsNavigationTest' --continue`
Expected: FAIL at compile: "cannot find symbol: method collapsibleNavigation(boolean)", "variable collapsibleNavigation",
"method isCollapsed".

- [ ] **Step 3: Make the navigation collapse in jasper-ui**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 5 step 3: the collapsible Settings navigation and its classic geometry."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
U = "jasper-ui/src/main/java/dev/jasper/ui/"
edit(U + "swing/KitView.java", [
    ('''    boolean settingsSurface() { return settingsSurface; }''', '''    boolean settingsSurface() { return settingsSurface; }
    private boolean collapsibleNavigation;
    /** Kit-internal: the Settings navigation's sections collapse behind a disclosure triangle (Enterprise presentation spec 12.1). */
    void collapsibleNavigation(boolean value) { requireOpen(); if (collapsibleNavigation == value) return; collapsibleNavigation = value; update(spec); }
    boolean collapsibleNavigation() { return collapsibleNavigation; }''', 1)])
edit(U + "swing/SettingsShell.java", [
    ('''            if (child instanceof KitView view) view.settingsChrome(child == header);
        appearance =''', '''            if (child instanceof KitView view) view.settingsChrome(child == header);
        if (navigation instanceof KitView view) view.collapsibleNavigation(frame.collapsibleNavigation());
        appearance =''', 1)])
edit(U + "swing/ViewReconciler.java", [
    ('''        if(next.get(node.id()) instanceof Trees.TreeControl tree){tree.settingsNavigation=navigation;tree.gutter(''',
     '''        if(next.get(node.id()) instanceof Trees.TreeControl tree){tree.settingsNavigation=navigation;tree.collapsibleNavigation=navigation&&view.collapsibleNavigation();tree.boxed(!navigation);tree.gutter(''', 1)])
edit(U + "swing/Trees.java", [
    ('''            ((TreePane)scroll).owner=this;
            var box=context().faces().tree.box(context());
            if(box!=null)scroll.setBorder(box);''', '''            ((TreePane)scroll).owner=this;
            plainBorder=scroll.getBorder();box=context().faces().tree.box(context());
            if(box!=null)scroll.setBorder(box);''', 1),
    ('''        @Override int minimumRowHeight(){''', '''        private javax.swing.border.Border plainBorder, box;
        /** The tree face's box, or the pane's own border where the box frames nothing (the Settings navigation). */
        void boxed(boolean value){var border=value&&box!=null?box:plainBorder;if(scroll.getBorder()!=border)scroll.setBorder(border);}
        /** The Settings navigation's sections collapse behind a disclosure triangle (Enterprise presentation spec 12.1). */
        boolean collapsibleNavigation;
        @Override int minimumRowHeight(){''', 1),
    ('''        /** Settings navigation's headings stay open. */
        void expandFocused(boolean expanded) {
            if (settingsNavigation) return;''', '''        /** Settings navigation's headings stay open unless the navigation collapses (Enterprise presentation spec 12.1). */
        void expandFocused(boolean expanded) {
            if (settingsNavigation && !collapsibleNavigation) return;''', 1),
    ('''            row.indent = settingsNavigation || rail()?0:''',
     '''            row.indent = settingsNavigation?entry.depth()*ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_CHILD_INDENT):rail()?0:''', 1),
    ('''            if (!item.leaf() && !settingsNavigation && !rail())''', '''            if (!item.leaf() && (!settingsNavigation || collapsibleNavigation) && !rail())''', 1),
    ('''                context().faces().tree.styleDisclosure(expand.button, context(), value().expandedIds().contains(item.id()));
''', '''                context().faces().tree.styleDisclosure(expand.button, context(), value().expandedIds().contains(item.id()));
            // The navigation's triangle stands alone; its title follows at the navigation's text gap (plan 9a Rulings 13).
            if (settingsNavigation && row.controls.get("@tree/" + node.id() + "/" + item.id() + "/expand") instanceof Buttons.ButtonControl navigationExpand)
                navigationExpand.button.disclosureWidth = navigationExpand.button.disclosure ? ThemePainting.metric(context(), dev.jasper.ui.metrics.DesignMetrics.Role.DISCLOSURE_GLYPH) : 0;
''', 1),
    ('''            // Emphasis is strong text in the link color''', '''            // A collapsible navigation's sections read as category rows in the strong secondary text (plan 9a Rulings 13).
            if (settingsNavigation && collapsibleNavigation && !item.leaf() && !row.inked)
                row.controls.get(titleId).component.setForeground(ThemePainting.color(context(), dev.jasper.ui.theme.SemanticColor.TEXT_STRONG_SECONDARY));
            // Emphasis is strong text in the link color''', 1)])
edit(U + "swing/Buttons.java", [
    ('''        boolean disclosure;''', '''        boolean disclosure;
        /** A disclosure's width when its owner sets one (the Settings navigation, plan 9a); 0 keeps the glyph and its gap. */
        int disclosureWidth;''', 1),
    ('''return new Dimension(Math.max(glyph,glyph+ThemePainting.metric(context,DesignMetrics.Role.DISCLOSURE_GAP)-ThemePainting.metric(context,DesignMetrics.Role.ICON_GAP)),ThemePainting.metric(context,DesignMetrics.Role.ROW_HEIGHT));}''',
     '''return new Dimension(disclosureWidth>0?disclosureWidth:Math.max(glyph,glyph+ThemePainting.metric(context,DesignMetrics.Role.DISCLOSURE_GAP)-ThemePainting.metric(context,DesignMetrics.Role.ICON_GAP)),ThemePainting.metric(context,DesignMetrics.Role.ROW_HEIGHT));}''', 1)])
edit(U + "swing/InteractiveRows.java", [
    ('''owner.panelSingle ? ThemePainting.metric(owner.context(),DesignMetrics.Role.PANEL_ROW_TEXT_GAP) : pad : 0);''',
     '''owner.panelSingle ? ThemePainting.metric(owner.context(),DesignMetrics.Role.PANEL_ROW_TEXT_GAP) : settingsNavigation ? ThemePainting.metric(owner.context(),DesignMetrics.Role.SETTINGS_NAV_TEXT_GAP) : pad : 0);''', 1)])
M = U + "metrics/DesignMetrics.java"
path = pathlib.Path(M); text = path.read_text(encoding="utf-8")
end = "        SETTINGS_HEADER_INSET(20);"
if text.count(end) != 1: raise SystemExit("DesignMetrics: Task 4's role is not last")
text = text.replace(end, '''        SETTINGS_HEADER_INSET(20),
        // Plan 9a: a page's indent under its section in the Settings navigation (the classic navigation's 15), and the gap
        // from a navigation row's leading slot (the classic triangle) to its title.
        SETTINGS_NAV_CHILD_INDENT(0), SETTINGS_NAV_TEXT_GAP(6);''', 1)
anchor = "        values.put(Role.SETTINGS_HEADER_INSET, 14);\n"
if text.count(anchor) != 1: raise SystemExit("DesignMetrics: Task 4's classic values moved")
text = text.replace(anchor, anchor + "".join(f"        values.put(Role.{n}, {v});\n" for n, v in
    [("SETTINGS_NAV_INSET", 0), ("SETTINGS_NAV_ROW_SIDE", 9), ("SETTINGS_NAV_HEADING_GAP", 0), ("SETTINGS_NAV_GROUP_GAP", 0), ("SETTINGS_NAV_CHILD_INDENT", 15)]), 1)
path.write_text(text, encoding="utf-8")
T = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics")
compact = T / "compact-roles.tsv"; compact.write_text(compact.read_text(encoding="utf-8") + "SETTINGS_NAV_CHILD_INDENT\t0\nSETTINGS_NAV_TEXT_GAP\t6\n", encoding="utf-8")
classic = T / "classic-roles.tsv"; rows = classic.read_text(encoding="utf-8").splitlines() + ["SETTINGS_NAV_CHILD_INDENT\t15", "SETTINGS_NAV_TEXT_GAP\t6"]
values = {"SETTINGS_NAV_INSET": 0, "SETTINGS_NAV_ROW_SIDE": 9, "SETTINGS_NAV_HEADING_GAP": 0, "SETTINGS_NAV_GROUP_GAP": 0}
seen = set()
for i, row in enumerate(rows):
    name = row.split("\t")[0]
    if name in values: rows[i] = f"{name}\t{values[name]}"; seen.add(name)
if seen != set(values): raise SystemExit(f"classic-roles.tsv: missing {sorted(set(values) - seen)}")
classic.write_text("\n".join(rows) + "\n", encoding="utf-8")
path = pathlib.Path("jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java"); text = path.read_text(encoding="utf-8")
if text.count('"SETTINGS_HEADER_INSET"') != 1: raise SystemExit("NewRolesTest: Task 4's role is missing")
path.write_text(text.replace('"SETTINGS_HEADER_INSET"', '"SETTINGS_HEADER_INSET", "SETTINGS_NAV_CHILD_INDENT", "SETTINGS_NAV_TEXT_GAP"', 1), encoding="utf-8")
PY
```

The Trees anchors are plan 4c's text (fact 8): `((TreePane)scroll).owner=this;` with 4c's box lines, 4c's
`styleDisclosure` call, and `row.indent = settingsNavigation || rail()?0:` at the head of the indent expression 4c
completed. The navigation's default values (`SETTINGS_NAV_CHILD_INDENT` 0; a navigation row has no leading slot by
default, so `SETTINGS_NAV_TEXT_GAP` is never read) keep the default look's rows where they are.

- [ ] **Step 4: Track the sections in the dialog**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 5 step 4: PageTree's collapsed sections and the dialog's routing."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-app/src/main/java/dev/jasper/app/settingsui/"
edit(S + "PageTree.java", [
    (''' * pages holding unsaved changes emphasized. Every group stays open. EDT only.''',
     ''' * pages holding unsaved changes emphasized. Every group stays open, unless the look's navigation collapses (spec 12.1):
 * then a section the user closed stays closed for this opening, except while a search runs. EDT only.''', 1),
    ('''    private String selected;
''', '''    private String selected;
    private boolean collapsible;
    private final Set<String> collapsed = new HashSet<>();

    /** Whether sections collapse (spec 12.1). */
    void collapsible(boolean value) { collapsible = value; if (!value) collapsed.clear(); }

    /** The user opened or closed a section; the state lives as long as this tree (spec 12.1: it is not saved). */
    void expanded(String id, boolean open) {
        if (!collapsible) return;
        if (open) collapsed.remove(id); else collapsed.add(id);
    }

    /** Opens the sections above {@code id}, so a page navigated to is in view. */
    void reveal(String id) {
        for (var at = find(id).flatMap(SettingsPage::parent); at.isPresent(); at = find(at.get()).flatMap(SettingsPage::parent)) collapsed.remove(at.get());
    }

    boolean isCollapsed(String id) { return collapsed.contains(id); }
''', 1),
    ('''        for (var page : shown.values()) if (!children(page.id()).isEmpty()) open.add(page.id());''',
     '''        for (var page : shown.values())
            if (!children(page.id()).isEmpty() && (!counts.isEmpty() || !collapsed.contains(page.id()))) open.add(page.id());''', 1),
    ('''    /** The kit tree: every branch open, the selection, counts while searching and emphasis for unsaved pages. */''',
     '''    /** The kit tree: every branch open but the collapsed ones (all while searching), the selection, counts and emphasis. */''', 1)])
edit(S + "SettingsDialog.java", [
    ('''        view.tree.show(pages, page -> true, Map.of());
        view.tree();
        select(''', '''        view.tree.collapsible(hooks.chrome().collapsibleNav());
        view.tree.show(pages, page -> true, Map.of());
        view.tree();
        select(''', 1),
    ('''            case UiEvent.Selected selected when selected.id().equals(PageTree.ID) -> selected.ids().stream().findFirst().ifPresent(this::select);''',
     '''            case UiEvent.Selected selected when selected.id().equals(PageTree.ID) -> selected.ids().stream().findFirst().ifPresent(this::select);
            case UiEvent.Expanded expanded when expanded.id().equals(PageTree.ID) -> { view.tree.expanded(expanded.itemId(), expanded.expanded()); view.tree(); }''', 1),
    ('''        view.breadcrumb(breadcrumb(page));
        view.tree.select(page.id());''', '''        view.breadcrumb(breadcrumb(page));
        view.tree.reveal(page.id());
        view.tree.select(page.id());''', 1)])
PY
```

Add the app test's `migration.csv` row (it builds lists):

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
row = ("jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsNavigationTest.java,app.settingsui,SettingsNavigationTest,nonvisual-helper,none,none,"
       "jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsNavigationTest.java,Test/fixture ownership; New in Enterprise plan 9a Task 5; the collapsible navigation")
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --tests 'dev.jasper.ui.metrics.*' :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `CollapsibleNavigationTest` (3), `SettingsNavigationTest` (5), `TreesTest`, `TreeDetailTest`,
`ClassicTablesTest`, `SelectionInkTest` and every Settings test. If `theClassicNavigationIsPlacedAsTheMocksPlaceIt`
reports another x for the section title, read the leading slot's width and the gap after it in
`InteractiveRows.Row.doLayout` and correct `SETTINGS_NAV_TEXT_GAP` or the disclosure width (both tables) until the
title is at 21; the triangle's own position comes from 4c's glyph centring inside its button.

- [ ] **Step 6: Prove the default look unchanged and commit**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
git add jasper-ui/src/main/java/dev/jasper/ui jasper-ui/src/test/resources/dev/jasper/ui/metrics jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java \
  jasper-ui/src/test/java/dev/jasper/ui/swing/CollapsibleNavigationTest.java jasper-app/src/main/java/dev/jasper/app/settingsui/PageTree.java \
  jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsDialog.java jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsNavigationTest.java \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Settings chrome: the collapsible navigation

Under SettingsChrome.collapsibleNav each navigation section has the tree
face's triangle: it closes and opens the section, Left and Right do too,
and a title click selects the page. The state lasts one opening, a search
shows every section, and navigating to a page opens its sections. The
classic catalog places the rows as the Settings mocks do, with the
selection across the whole sidebar; the default navigation is unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Expected before the commit: `changed: none`.

### Task 6: The real dialog under Enterprise, compared with the eighteen mocks

`EnterpriseSettings` opens the real Settings dialog headless under plan 6a's `EnterpriseLook` (the shipped kit, its
registered Plex faces and the Enterprise theme), with spec 9.1's projections from the parsed kit, over the bundled plugins
and a fixture user plugin, and restores the default look when it closes.
`SettingsChromeMockTest` renders each of the eighteen pages at 2x into `jasper-app/build/enterprise-settings/` and checks
the chrome against each mock: probes on every OS (rules, surfaces, the selected row), pixel shares on macOS. The
calibrated page roles of Rulings 16 are settled here. `tools/visual/pairs.json` gains the eighteen pairs for the opt-in
tools.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsMockMatch.java`,
  `jasper-app/src/test/java/dev/jasper/app/settingsui/EnterpriseSettings.java`, `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsChromeMockTest.java`
- Modify: `tools/visual/pairs.json` (eighteen entries appended), `docs/design/theme-engine/migration.csv` (rows)
- Modify (calibration only): `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (Task 4's three calibrated values) and `classic-roles.tsv`

**Interfaces:**
- Consumes: plan 6a's `EnterpriseLook.controller(boolean)` and `restoreDefault()`, `MockProfile.mock(String)`,
  `MockProfile.deltaE(Color, Color)` and `MockProfile.REFERENCE`; plan 6b's `AuxiliaryWindows(UiState, Function,
  PathChooser, DialogMapping)`; plan 5b's `EnterpriseResources`; plan 5a's `Presentation.of`, `Projections.Host`,
  `ConfigLoader.parse(Path, String, boolean, DefaultSource)`, `ConfigService.State` (9 components);
  `PluginRuntime.pluginNames()`; Tasks 1 to 5.
- Produces: package-private `final class SettingsMockMatch` with `record Result(double matching, int dx, int dy)`,
  `static BufferedImage mock(String)`, `static BufferedImage crop(BufferedImage, Rectangle)`, `static double deltaE(int,
  int)`, `static Result compare(BufferedImage, BufferedImage, int shift, double tolerance)`, `static void inspect(Path,
  String, BufferedImage, BufferedImage, double tolerance)`; package-private `final class EnterpriseSettings implements
  AutoCloseable` with `static EnterpriseSettings open(boolean dark)`, `BufferedImage render(String pageId, int width, int
  height)`, `SettingsDialog dialog()`, `static final String CONFIG`, `static final Path OUT`; `SettingsChromeMockTest.PAGES`
  (the eighteen mocks with their page ids and heights), which plan 9b reuses.

- [ ] **Step 1: Write the comparison support**

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsMockMatch.java`:

```java
package dev.jasper.app.settingsui;

import dev.jasper.app.testsupport.MockProfile;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Compares a headless Settings render at 2x with a crop of its Enterprise mock (Enterprise presentation spec 16.1.4, plan
 * 9a): the share of pixels within a CIE76 distance after the best small alignment, and single-pixel probes. Reads the mocks
 * and measures colour through plan 6a's {@link MockProfile}; writes the render, the crop and a difference image under
 * build/ for inspection. Tests only.
 */
final class SettingsMockMatch {
    private SettingsMockMatch() { }

    /**
     * The best alignment found.
     *
     * @param matching the share of compared pixels within the tolerance, 0 to 1
     * @param dx the render's horizontal offset that matched best, in pixels
     * @param dy the render's vertical offset that matched best, in pixels
     */
    record Result(double matching, int dx, int dy) { }

    /** The mock {@code name}, a path under {@link MockProfile#REFERENCE}, as opaque RGB over white. */
    static BufferedImage mock(String name) {
        var image = MockProfile.mock(name);
        var copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = copy.createGraphics();
        try { g.setColor(Color.WHITE); g.fillRect(0, 0, copy.getWidth(), copy.getHeight()); g.drawImage(image, 0, 0, null); } finally { g.dispose(); }
        return copy;
    }

    /** A copy of {@code area} of {@code image}. */
    static BufferedImage crop(BufferedImage image, Rectangle area) {
        var copy = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_RGB);
        var g = copy.createGraphics();
        try { g.drawImage(image, -area.x, -area.y, null); } finally { g.dispose(); }
        return copy;
    }

    /** The CIE76 distance between two sRGB colours, as {@link MockProfile#deltaE} measures it; alpha is ignored. */
    static double deltaE(int a, int b) { return MockProfile.deltaE(new Color(a & 0xFFFFFF), new Color(b & 0xFFFFFF)); }

    private static double linear(double channel) { return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4); }
    private static double f(double t) { return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0; }

    /** CIE L*a*b* of an sRGB colour under D65, MockProfile's conversion, kept here so {@link #compare} converts each colour once. */
    private static double[] lab(int rgb) {
        double r = linear(((rgb >> 16) & 0xFF) / 255.0), g = linear(((rgb >> 8) & 0xFF) / 255.0), b = linear((rgb & 0xFF) / 255.0);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047, y = 0.2126 * r + 0.7152 * g + 0.0722 * b,
            z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        return new double[] {116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z))};
    }

    private static float[] labs(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        var out = new float[3 * w * h];
        var cache = new java.util.HashMap<Integer, double[]>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            var value = cache.computeIfAbsent(image.getRGB(x, y) & 0xFFFFFF, SettingsMockMatch::lab);
            int i = 3 * (y * w + x);
            out[i] = (float) value[0]; out[i + 1] = (float) value[1]; out[i + 2] = (float) value[2];
        }
        return out;
    }

    /**
     * The best alignment of {@code render} over {@code mock}, both the same size, within {@code shift} pixels each way: the
     * share of the pixels inside a {@code shift}-wide border whose distance is at most {@code tolerance}.
     */
    static Result compare(BufferedImage render, BufferedImage mock, int shift, double tolerance) {
        if (render.getWidth() != mock.getWidth() || render.getHeight() != mock.getHeight())
            throw new IllegalArgumentException("render " + render.getWidth() + "x" + render.getHeight() + " against mock " + mock.getWidth() + "x" + mock.getHeight());
        int w = mock.getWidth(), h = mock.getHeight();
        float[] a = labs(render), b = labs(mock);
        double limit = tolerance * tolerance;
        var best = new Result(-1, 0, 0);
        for (int dy = -shift; dy <= shift; dy++) for (int dx = -shift; dx <= shift; dx++) {
            long hits = 0, all = 0;
            for (int y = shift; y < h - shift; y++) for (int x = shift; x < w - shift; x++) {
                int i = 3 * ((y + dy) * w + x + dx), j = 3 * (y * w + x);
                float l = a[i] - b[j], p = a[i + 1] - b[j + 1], q = a[i + 2] - b[j + 2];
                if (l * l + p * p + q * q <= limit) hits++;
                all++;
            }
            double share = all == 0 ? 0 : (double) hits / all;
            if (share > best.matching()) best = new Result(share, dx, dy);
        }
        return best;
    }

    /** Writes {@code render}, {@code mock} and their difference (mismatches magenta over a faded render) as {@code name}-*.png. */
    static void inspect(Path directory, String name, BufferedImage render, BufferedImage mock, double tolerance) {
        try {
            Files.createDirectories(directory);
            ImageIO.write(render, "png", directory.resolve(name + "-render.png").toFile());
            ImageIO.write(mock, "png", directory.resolve(name + "-mock.png").toFile());
            var diff = new BufferedImage(mock.getWidth(), mock.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < mock.getHeight(); y++) for (int x = 0; x < mock.getWidth(); x++) {
                int r = render.getRGB(x, y), m = mock.getRGB(x, y);
                int faded = 0xC0C0C0 | ((r >> 2) & 0x3F3F3F);
                diff.setRGB(x, y, deltaE(r, m) <= tolerance ? faded : 0xFF00FF);
            }
            ImageIO.write(diff, "png", directory.resolve(name + "-diff.png").toFile());
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
```

The helper is the Settings comparisons' own (9a's chrome, 9b's pages), so it lives in their package under a name of its
own: plan 10a's `testsupport.MockMatch` (bands, agreement) is a different class at a different path. Plan 6a's
`MockProfile` reads the mocks and is the one Lab definition; `compare` keeps a cached copy of the conversion only so a
2080 × 1382 crop converts each distinct colour once.

- [ ] **Step 2: Write the fixture**

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/EnterpriseSettings.java`:

```java
package dev.jasper.app.settingsui;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.pluginmanager.PluginManager;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.presentation.Presentation;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.restart.ResidentControl;
import dev.jasper.app.restart.RestartFlow;
import dev.jasper.app.shortcuthelp.KeymapPage;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.buddy.view.BuddyTestSupport;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The real Settings dialog under the shipped Enterprise look, headless: plan 6a's {@link EnterpriseLook} (the parsed kit,
 * the registered Plex faces, the Enterprise theme), spec 9.1's projections and the dialog mapping the application gives
 * its windows, over the bundled plugins and a fixture user plugin (plan 9a Rulings 18). The look is installed for the
 * whole JVM, so {@link #close} (and a failed {@link #open}) installs the default look again. Never creates a native
 * window. Open on any thread; every method but {@link #open} and {@link #close} runs on the EDT.
 */
final class EnterpriseSettings implements AutoCloseable {
    /** Where the renders go, under {@code light/} and {@code dark/}. */
    static final Path OUT = EnterpriseResources.ROOT.resolve("jasper-app/build/enterprise-settings");
    /** The values the mocks show, as a config file (plan 9a Rulings 18). */
    static final String CONFIG = """
        [ui]
        appearance = "light"
        [ui.theme]
        dark = "slate-cobalt"
        light = "slate-daylight"
        [window]
        toolbar = "icons_and_labels"
        toolbar_height = 34
        status_bar = true
        status_bar_height = 30
        memory_indicator = false
        tab_height = 30
        rail_width = 28
        columns = 100
        lines = 35
        [notifications]
        system = true
        long_command_seconds = 10
        [palette]
        visible_rows = 18
        [terminal.font]
        family = "Cousine Nerd Font Mono"
        size = 12
        fallback = ["Symbols Nerd Font Mono", "Apple Color Emoji"]
        ligatures = true
        line_height = 1
        """;

    private final Path home;
    private final AuxiliaryWindows windows;
    private final JComponent[] holder;
    private final ThemeController themes;
    private final PluginRuntime runtime;
    private final SettingsDialog dialog;

    private EnterpriseSettings(Path home, AuxiliaryWindows windows, JComponent[] holder, ThemeController themes, PluginRuntime runtime, SettingsDialog dialog) {
        this.home = home; this.windows = windows; this.holder = holder; this.themes = themes; this.runtime = runtime; this.dialog = dialog;
    }

    SettingsDialog dialog() { return dialog; }

    /** A Slate Cobalt-shaped preview of {@code theme}'s variant, as the application builds tile previews. */
    static Option.Palette preview(JasperTheme theme, boolean dark) {
        var palette = theme.palette(dark);
        return new Option.Palette(palette.color(SemanticColor.SURFACE_INPUT).getRGB(), palette.color(SemanticColor.SURFACE_SIDEBAR).getRGB(),
            palette.color(SemanticColor.SURFACE_APP).getRGB(), palette.color(SemanticColor.TEXT_PRIMARY).getRGB(),
            palette.color(SemanticColor.ACCENT).getRGB());
    }

    /** The mocks' notification groups (Settings 03), as the application lists them. */
    static List<NotificationGroupChoices.Group> groups() {
        record G(String owner, String ownerId, String id, String name, String mode) { }
        var rows = List.of(new G("Jasper", "jasper", "jasper.general", "Jasper", "card"), new G("Jasper", "jasper", "jasper.config", "Configuration", "sticky"),
            new G("Jasper", "jasper", "jasper.plugins", "Plugin problems", "card"), new G("Jasper", "jasper", "jasper.errors", "Internal errors", "card"),
            new G("Jasper", "jasper", "jasper.commands", "Long-running commands", "card"), new G("Jasper", "jasper", "jasper.memory", "Memory & VM options", "card"),
            new G("THD Sourcing Crypto", "com.homedepot.sourcing.crypto", "com.homedepot.sourcing.crypto", "THD Sourcing Crypto", "card"),
            new G("THD Sourcing Crypto", "com.homedepot.sourcing.crypto", "com.homedepot.sourcing.crypto.activities", "THD Sourcing Crypto activities", "card"),
            new G("Editor", "dev.jasper.editor", "dev.jasper.editor", "Editor", "card"), new G("Editor", "dev.jasper.editor", "dev.jasper.editor.activities", "Editor activities", "card"),
            new G("Credential Vault", "dev.jasper.vault", "dev.jasper.vault", "Credential Vault", "card"),
            new G("Credential Vault", "dev.jasper.vault", "dev.jasper.vault.activities", "Credential Vault activities", "card"),
            new G("Credential Vault", "dev.jasper.vault", "dev.jasper.vault.copies", "Vault copies", "card"),
            new G("File Server", "dev.jasper.fileserver", "dev.jasper.fileserver", "File Server", "card"),
            new G("File Server", "dev.jasper.fileserver", "dev.jasper.fileserver.activities", "File Server activities", "card"),
            new G("Snippets", "dev.jasper.snippets", "dev.jasper.snippets", "Snippets", "card"), new G("Snippets", "dev.jasper.snippets", "dev.jasper.snippets.activities", "Snippets activities", "card"));
        return rows.stream().map(g -> new NotificationGroupChoices.Group(g.id(), g.name(), g.owner(), g.mode(), g.ownerId(), "")).toList();
    }

    /** Opens the dialog at Appearance under Enterprise Light ({@code dark} false) or Dark. */
    static EnterpriseSettings open(boolean dark) throws Exception {
        var parsed = EnterpriseResources.facts().parsed().orElseThrow();
        var enterprise = EnterpriseResources.theme();
        Path home = Files.createTempDirectory("jasper-enterprise-settings");
        // The mocks' user-installed plugin (a fixture): THD Sourcing Crypto 1.2.0 by The Home Depot Sourcing.
        PluginJars.build(home.resolve("plugins/com.homedepot.sourcing.crypto/jars"), "main.jar",
            PluginJars.descriptor("com.homedepot.sourcing.crypto", "1.2.0", "fix.crypto.Main").replace("name = \"com.homedepot.sourcing.crypto\"", "name = \"THD Sourcing Crypto\"")
                + "description = \"Encrypt or decrypt strings with the THD Sourcing crypto library (com.homedepot.ta.aa.crypto).\"\nvendor = \"The Home Depot Sourcing\"\n",
            Map.of("fix.crypto.Main", PluginJars.emptyPlugin("fix.crypto", "Main")), List.of());
        Files.writeString(home.resolve("plugins.toml"), "version = 1\n\n[plugins.\"com.homedepot.sourcing.crypto\"]\nenabled = true\nconsented = []\n");
        Path file = home.resolve("config.toml");
        Files.writeString(file, CONFIG);
        var loaded = ConfigLoader.parse(file, CONFIG, true, parsed.defaults());
        var state = new ConfigService.State(loaded.snapshot(), loaded.diagnostics(), file, true, loaded.legacyKeys(), loaded.deadTables(),
            loaded.values(), false, parsed.defaults());
        var projections = Presentation.of(new Presentation.Provider("dev.jasper.enterprise", "Enterprise", "fixture", "fixture"), parsed,
            new Projections.Host(true, true)).projections();
        var holder = new JComponent[1];
        // The application's mapping (plan 6b): under Enterprise the dialog's sheets and confirmations take the classic path.
        var windows = new AuxiliaryWindows(UiState.inMemory(), surface -> {
            holder[0] = surface.holder();
            return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 1040, 691), (title, initial) -> Optional.empty());
        }, (choice, cancel) -> List.of(), projections.dialogs());
        var themes = new ThemeController[1]; var runtime = new PluginRuntime[1]; var dialog = new SettingsDialog[1];
        var contributions = new Contributions();
        try {
            SwingUtilities.invokeAndWait(() -> {
                themes[0] = EnterpriseLook.controller(dark);
                runtime[0] = new PluginRuntime(new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")), home.resolve("plugins"), null, false,
                    home.resolve("plugins.toml"), home.resolve("plugins.lock")), new ActivityNotifier(new BuddyTestSupport().companion(), () -> { }),
                    PluginRuntime.SettingsReports.IGNORED, contributions, windows, new TerminalRegistry());
                runtime[0].start(Map.of(), dark);
                var flow = new RestartFlow(ResidentControl.NONE, mode -> true, Runnable::run, Runnable::run, Duration.ofMillis(20), Duration.ofMillis(1));
                var manager = new PluginManager(runtime[0], windows, new PluginManager.Hooks(surface -> Optional.empty(), flow, () -> { },
                    ResidentControl.NONE, false, Runnable::run, path -> { }, path -> { }, uri -> { }));
                var installed = new InstalledThemes(
                    List.of(new InstalledThemes.ThemeOption("slate-cobalt", "Slate Cobalt", preview(enterprise, true)), new InstalledThemes.ThemeOption("enterprise", "Enterprise", preview(enterprise, true))),
                    List.of(new InstalledThemes.ThemeOption("slate-daylight", "Slate Daylight", preview(enterprise, false)), new InstalledThemes.ThemeOption("enterprise", "Enterprise", preview(enterprise, false))),
                    List.of(new InstalledThemes.SchemeOption("enterprise/light", "Enterprise Light")),
                    Map.of("slate-cobalt", "Slate Cobalt", "slate-daylight", "Slate Daylight", "enterprise", "Enterprise"));
                var choices = new AppearanceChoices(100f, true, Optional::empty, ignored -> new Subscription(() -> { }), () -> installed, Optional.empty());
                // The plugins' display names as the application supplies them (JasperApplication passes plugins::pluginNames).
                var keymapHooks = new KeymapPage.Hooks(contributions, Map::of, runtime[0]::pluginNames, true,
                    edits -> CompletableFuture.completedFuture(null));
                var pages = new CorePages.Pages(context -> new KeymapPage(keymapHooks, context), context -> manager.page(context), context -> runtime[0].settingsPages(context));
                dialog[0] = new SettingsDialog(windows, CorePages.pages(choices, new CorePages.SystemActions(() -> Path.of("/Users/RQ7RQVF/.config/jasper/config.toml"),
                        () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.empty()), ShellChoices.NONE, new NotificationGroupChoices(EnterpriseSettings::groups), pages),
                    new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state, listener -> new Subscription(() -> { }), () -> { }, () -> { },
                        uri -> { }, (parent, count) -> true, () -> themes[0].appearance().context(ThemeRoute.CHROME), projections.settings(), projections.composition()));
                dialog[0].open(null, "appearance");
            });
            var ready = new CompletableFuture<Void>();
            runtime[0].snapshot(outcome -> ready.complete(null));
            ready.get(10, TimeUnit.SECONDS);
        } catch (Exception | Error failure) {
            // A failed opening leaves no Enterprise look behind for the tests that follow.
            SwingUtilities.invokeAndWait(() -> {
                try { if (dialog[0] != null) dialog[0].cancel(); if (themes[0] != null) themes[0].close(); } finally { EnterpriseLook.restoreDefault(); }
            });
            throw failure;
        }
        return new EnterpriseSettings(home, windows, holder, themes[0], runtime[0], dialog[0]);
    }

    /** Selects {@code pageId} and paints the dialog's content, {@code width} by {@code height}, at 2x; EDT. */
    BufferedImage render(String pageId, int width, int height) {
        dialog.select(pageId);
        var content = holder[0];
        content.setSize(width, height);
        for (int i = 0; i < 4; i++) KitTestSupport.layout(content);
        var image = new BufferedImage(2 * width, 2 * height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.scale(2, 2); content.printAll(g); } finally { g.dispose(); }
        return image;
    }

    /** Closes the dialog, stops the plugins and installs the default look again for the tests that follow (6a's restoreDefault). */
    @Override public void close() throws Exception {
        try {
            SwingUtilities.invokeAndWait(dialog::cancel);
            var stopping = new CompletableFuture<List<CompletableFuture<?>>>();
            SwingUtilities.invokeAndWait(() -> stopping.complete(runtime.stop()));
            CompletableFuture.allOf(stopping.get().toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> { windows.close(); themes.close(); });
        } finally {
            try { SwingUtilities.invokeAndWait(EnterpriseLook::restoreDefault); }
            finally { try (var paths = Files.walk(home)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); } }
        }
    }
}
```

The fixture uses the APIs of facts 20 and 21 as they stand after plans 5a, 5b, 6a and 6b. `ThemeController.close` only
drops its listeners, and jasper-app's tests share one JVM, so the Enterprise look `EnterpriseLook.controller` installs
would outlive the test without `restoreDefault()`; `close` runs it in a `finally` on the EDT. If a signature differs (for
example `NotificationGroupChoices.Group`'s 6-argument form or the order of `ConfigService.State`'s ninth component), adapt
the call to the merged API and record it in the banner; the fixture's data stay as written. The descriptor edit is `SettingsDialogPreview.java:55-58`'s.

- [ ] **Step 3: Write the chrome comparison**

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsChromeMockTest.java`:

```java
package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.plugins.PluginRuntime;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 12.1 and 16.1.4: the Settings chrome against the eighteen 05 Settings mocks (plan 9a's
 * "Mock measurements"): probes on every OS, pixel shares of the sidebar, header and footer on macOS (Rulings 17).
 */
class SettingsChromeMockTest {
    /**
     * One mock.
     *
     * @param mock its file under 05 Settings
     * @param id the page it shows
     * @param height its content height in logical pixels
     * @param row the selected navigation row, counted from the first
     */
    record Page(String mock, String id, int height, int row) { }

    static final List<Page> PAGES = List.of(
        new Page("Settings 01 - Appearance & Behavior - Appearance.png", "appearance", 691, 1),
        new Page("Settings 02 - Appearance & Behavior - Window.png", "window", 691, 2),
        new Page("Settings 03 - Appearance & Behavior - Notifications.png", "notifications", 827, 3),
        new Page("Settings 04 - Appearance & Behavior - Command Palette.png", "command-palette", 691, 4),
        new Page("Settings 05 - Appearance & Behavior - Desk Buddy.png", "desk-buddy", 691, 5),
        new Page("Settings 06 - Appearance & Behavior - System Settings.png", "system", 691, 6),
        new Page("Settings 07 - Keymap.png", "keymap", 691, 7),
        new Page("Settings 08 - Plugins.png", "plugins", 691, 8),
        new Page("Settings 09 - Terminal - Font & Colors.png", "terminal.font", 691, 10),
        new Page("Settings 10 - Terminal - Cursor.png", "terminal.cursor", 691, 11),
        new Page("Settings 11 - Terminal - Shell.png", "terminal.shell", 691, 12),
        new Page("Settings 12 - Tools - Credential Vault.png", PluginRuntime.settingsPageId("dev.jasper.vault"), 691, 14),
        new Page("Settings 13 - Tools - Editor.png", PluginRuntime.settingsPageId("dev.jasper.editor"), 731, 15),
        new Page("Settings 14 - Tools - File Server.png", PluginRuntime.settingsPageId("dev.jasper.fileserver"), 691, 16),
        new Page("Settings 15 - Tools - Project Index.png", PluginRuntime.settingsPageId("dev.jasper.indexer"), 949, 17),
        new Page("Settings 16 - Tools - Remote.png", PluginRuntime.settingsPageId("dev.jasper.remote"), 691, 18),
        new Page("Settings 17 - Tools - Sample.png", PluginRuntime.settingsPageId("dev.jasper.sample"), 691, 19),
        new Page("Settings 18 - Tools - Shell History.png", PluginRuntime.settingsPageId("dev.jasper.history"), 691, 20));

    static final double TOLERANCE = 12, PROBE = 3;
    static final boolean MAC = com.formdev.flatlaf.util.SystemInfo.isMacOS;

    /** The mock's content, as the render covers it: from 2x (2, 60). */
    static BufferedImage content(Page page) {
        var mock = SettingsMockMatch.mock("05 Settings/" + page.mock());
        return SettingsMockMatch.crop(mock, new Rectangle(2, 60, 2080, 2 * page.height()));
    }

    /** A probe: a logical point of the content and what it should show. */
    record Probe(String name, int x, int y) { }

    /** The frame's probes, and for two pages the page geometry of Rulings 16 (box edges and the gap between boxes). */
    static List<Probe> probesOf(Page page) {
        int h = page.height(), selected = 43 + 22 * page.row() + 11;
        var probes = new ArrayList<>(List.of(new Probe("strip", 115, 4), new Probe("strip rule", 115, 38), new Probe("navigation", 115, 41),
            new Probe("sidebar rule", 230, 300), new Probe("body edge", 231, 300), new Probe("header", 640, 2), new Probe("header rule", 640, 34),
            new Probe("body", 640, 36), new Probe("footer rule", 300, h - 45), new Probe("footer band", 300, h - 3), new Probe("selected row", 215, selected),
            new Probe("selection at the left edge", 1, selected), new Probe("selection at the right edge", 229, selected)));
        if (page.id().equals("command-palette"))
            probes.addAll(List.of(new Probe("first box top", 600, 53), new Probe("box left", 247, 100), new Probe("box right", 1023, 100)));
        if (page.id().equals("appearance"))
            probes.addAll(List.of(new Probe("Theme box bottom", 1000, 313), new Probe("Workspace box top", 1000, 334)));
        return probes;
    }

    /** Each probe's rendered pixel within {@link #PROBE} of the mock's at the same point; the failures. */
    static List<String> probes(Page page, BufferedImage render, BufferedImage mock) {
        var failures = new ArrayList<String>();
        for (var probe : probesOf(page)) {
            int x = 2 * probe.x(), y = 2 * probe.y();
            double distance = SettingsMockMatch.deltaE(render.getRGB(x, y), mock.getRGB(x, y));
            if (distance > PROBE) failures.add(String.format("%s %s at (%d, %d): render #%06X, mock #%06X, ΔE %.1f", page.id(), probe.name(),
                probe.x(), probe.y(), render.getRGB(x, y) & 0xFFFFFF, mock.getRGB(x, y) & 0xFFFFFF, distance));
        }
        return failures;
    }

    @Test void theChromeMatchesTheEighteenMocks() throws Exception {
        var failures = new ArrayList<String>();
        var shares = new StringBuilder();
        try (var settings = EnterpriseSettings.open(false)) {
            for (var page : PAGES) {
                var render = new BufferedImage[1];
                SwingUtilities.invokeAndWait(() -> render[0] = settings.render(page.id(), 1040, page.height()));
                var mock = content(page);
                var out = EnterpriseSettings.OUT.resolve("light");
                Files.createDirectories(out);
                ImageIO.write(render[0], "png", out.resolve(page.mock().substring(9, 11) + ".png").toFile());
                failures.addAll(probes(page, render[0], mock));
                int h = 2 * page.height();
                var areas = List.of(new Rectangle(0, 0, 460, h - 90), new Rectangle(462, 0, 1618, 70), new Rectangle(0, h - 90, 2080, 90));
                var names = List.of("sidebar", "header", "footer");
                var floors = List.of(0.97, 0.96, 0.98);
                for (int i = 0; i < areas.size(); i++) {
                    var area = areas.get(i);
                    var ours = SettingsMockMatch.crop(render[0], area); var theirs = SettingsMockMatch.crop(mock, area);
                    var result = SettingsMockMatch.compare(ours, theirs, 2, TOLERANCE);
                    shares.append(String.format("%s %s %.3f (%d, %d)%n", page.id(), names.get(i), result.matching(), result.dx(), result.dy()));
                    if (result.matching() < floors.get(i)) {
                        SettingsMockMatch.inspect(EnterpriseSettings.OUT.resolve("compare"), page.mock().substring(9, 11) + "-" + names.get(i), ours, theirs, TOLERANCE);
                        if (MAC) failures.add(String.format("%s %s: %.3f below %.2f", page.id(), names.get(i), result.matching(), floors.get(i)));
                    }
                }
            }
        }
        Files.writeString(EnterpriseSettings.OUT.resolve("chrome-shares.txt"), shares);
        assertThat(failures).as("see jasper-app/build/enterprise-settings/compare and chrome-shares.txt").isEmpty();
    }

    @Test void theDarkVariantPaintsTheSameFrameInItsOwnRoles() throws Exception {
        try (var settings = EnterpriseSettings.open(true)) {
            var render = new BufferedImage[1];
            SwingUtilities.invokeAndWait(() -> render[0] = settings.render("appearance", 1040, 691));
            ImageIO.write(render[0], "png", Files.createDirectories(EnterpriseSettings.OUT.resolve("dark")).resolve("01.png").toFile());
            var palette = dev.jasper.app.testsupport.EnterpriseResources.theme().palette(true);
            var light = content(PAGES.getFirst());
            assertThat(render[0].getRGB(230, 8) & 0xFFFFFF).as("the strip is dark surface.sidebar")
                .isEqualTo(palette.color(dev.jasper.ui.theme.SemanticColor.SURFACE_SIDEBAR).getRGB() & 0xFFFFFF);
            assertThat(render[0].getRGB(1280, 200) & 0xFFFFFF).as("the body is dark surface.app")
                .isEqualTo(palette.color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP).getRGB() & 0xFFFFFF);
            assertThat(render[0].getRGB(1280, 68) & 0xFFFFFF).as("the header rule stands apart from the body")
                .isNotEqualTo(render[0].getRGB(1280, 72) & 0xFFFFFF);
            assertThat(SettingsMockMatch.deltaE(render[0].getRGB(1280, 68), light.getRGB(1280, 68))).as("in Dark's own colour").isGreaterThan(PROBE);
        }
    }
}
```

The render's logical point `(x, y)` is the 2x pixel `(2x, 2y)`, and the mock crop starts at the content's top-left, so
both images share coordinates. The selected row's probe for the Plugins page (row 8) and every other page reads the
selection; the Keymap recording mock is plan 9b's.

- [ ] **Step 4: Run the comparison and calibrate**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.SettingsChromeMockTest' --continue`

Then read `jasper-app/build/enterprise-settings/chrome-shares.txt` and, for a failure, the
`compare/<nn>-<part>-diff.png` (mismatches in magenta). Expected: PASS; on macOS every share at or above its floor. Fix
by cause, re-running after each change:

- a probe failure at a rule, a surface or the selected row is a geometry or role defect of Tasks 4 and 5: correct the
  role value (both tables) or the frame code, never the probe;
- magenta along whole edges of text or controls in the header (the breadcrumb at 75, 212 and 225; the lone title at 96)
  means a header gap: adjust `SETTINGS_HEADER_GAP`, `SETTINGS_CRUMB_GAP` or `SETTINGS_HEADER_TITLE_GAP`;
- magenta confined to glyph interiors with every edge aligned is rasterization: on macOS, lower that part's floor to the
  measured share rounded down to 0.01, never below 0.90, and record the share and cause in the banner.

Calibrate Rulings 16's page roles with the page probes `probesOf` already holds: on Command Palette the first titled
box's top border at logical y 53 (18 below the header rule at 34 and its 1 px) and its edges at x 247 and 1023 (16 inside
the body); on Appearance the Theme box's bottom border at y 313 and the Workspace box's top border at y 334 (21 apart).
Adjust `SETTINGS_PAGE_TOP` until the first passes and `SETTINGS_SECTION_GAP` until the second does (both tables; 4b's
titled box puts its border at its title line's middle). Record the calibrated values, with `SETTINGS_CRUMB_GAP`, in the
banner.

- [ ] **Step 5: Add the pairs for the opt-in tools and the ledger rows**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 6 step 5: eighteen enterprise-settings-* pairs for compare_all.py and check_pairs.py (opt-in)."""
import json, pathlib
path = pathlib.Path("tools/visual/pairs.json")
pairs = json.loads(path.read_text(encoding="utf-8"))
if any(p["name"].startswith("enterprise-settings-") for p in pairs): raise SystemExit("pairs.json already has the Enterprise settings pairs")
R = "docs/design/enterprise/reference/05 Settings/"
heights = {"03": 827, "13": 731, "15": 949}
names = ["01 - Appearance & Behavior - Appearance", "02 - Appearance & Behavior - Window", "03 - Appearance & Behavior - Notifications",
    "04 - Appearance & Behavior - Command Palette", "05 - Appearance & Behavior - Desk Buddy", "06 - Appearance & Behavior - System Settings",
    "07 - Keymap", "08 - Plugins", "09 - Terminal - Font & Colors", "10 - Terminal - Cursor", "11 - Terminal - Shell", "12 - Tools - Credential Vault",
    "13 - Tools - Editor", "14 - Tools - File Server", "15 - Tools - Project Index", "16 - Tools - Remote", "17 - Tools - Sample", "18 - Tools - Shell History"]
chrome = ["#F7F7F8", "#FFFFFF", "#ECEDEF", "#C4C7CC", "#E6E7EA", "#2F65CA"]
for name in names:
    number = name[:2]
    height = heights.get(number, 691)
    pairs.append({"name": "enterprise-settings-" + number, "mock": R + "Settings " + name + ".png", "crop": [2, 60, 2080, 2 * height],
                  "render": "jasper-app/build/enterprise-settings/light/" + number + ".png", "min_agreement": 0.40, "colours": chrome})
path.write_text(json.dumps(pairs, indent=2) + "\n", encoding="utf-8")
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
rows = [
    "jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsMockMatch.java,app.settingsui,SettingsMockMatch,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsChromeMockTest.java,Test/fixture ownership; New in Enterprise plan 9a Task 6; compares Settings renders with the Enterprise mocks",
    "jasper-app/src/test/java/dev/jasper/app/settingsui/EnterpriseSettings.java,app.settingsui,EnterpriseSettings,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsChromeMockTest.java,Test/fixture ownership; New in Enterprise plan 9a Task 6; the real dialog under the Enterprise look",
    "jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsChromeMockTest.java,app.settingsui,SettingsChromeMockTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsChromeMockTest.java,Test/fixture ownership; New in Enterprise plan 9a Task 6; the chrome against the eighteen mocks"]
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
git diff --stat tools/visual/pairs.json
```

Expected: insertions only.

- [ ] **Step 6: Run the tests and the pixel proof**

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-ui:test --tests 'dev.jasper.ui.metrics.*' --tests 'dev.jasper.ui.gallery.*' --continue
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
```

Expected: PASS (`SettingsChromeMockTest` 2 and every Settings test; `EnterpriseGalleryTest` still finds exactly its
`classic-*` pairs); `changed: none`.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsMockMatch.java \
  jasper-app/src/test/java/dev/jasper/app/settingsui/EnterpriseSettings.java jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsChromeMockTest.java \
  tools/visual/pairs.json docs/design/theme-engine/migration.csv jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java \
  jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-roles.tsv
git commit -F - <<'EOF'
Settings chrome: the real dialog under Enterprise against the mocks

EnterpriseSettings opens the real Settings dialog headless under plan
6a's EnterpriseLook and the kit's projections, over the bundled plugins,
and restores the default look when it closes. SettingsChromeMockTest
renders the eighteen pages at 2x
and checks each mock's frame: rule, surface and selection probes on every
OS, sidebar, header and footer pixel shares on macOS. The page geometry
roles are calibrated against the mocks; pairs.json gains the pairs for the
opt-in tools.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: The Settings frame in the chrome renders

Spec 16.1.4 renders the test form of `settingsPreview` under every look plan 6a's `ChromeRenderTest` covers: the
default, Enterprise and complement kits, the fallback combination (native title, window menus, default kit) and the
`ChromeSpec` covering array. Two of them reach Settings frames no other test opens: the complement kit's integrated
title with the collapsible navigation and row menus, and the fallback's classic frame under the default kit. A test
bridge opens the real dialog under each case's kit and projections, checks the frame `SettingsChrome` names and returns
its print; `ChromeRenderTest` writes it beside the window renders.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsFrameRenders.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java` (one test), `docs/design/theme-engine/migration.csv` (one row)

**Interfaces:**
- Consumes: plan 6a's `ChromeRenderTest.cases()`, `Case(name, themes, projections)`, `MockProfile.print` and `write`,
  `EnterpriseLook.restoreDefault()`; plan 6b's four-argument `AuxiliaryWindows`; Tasks 1 to 5 (`SettingsDialog.view()`,
  `SettingsView.search`, `navigation`, `body`, `footer`, `titleRowHeight()`, `SettingsShell.framePairs`).
- Produces: `public final class SettingsFrameRenders` (test bridge) with `public static BufferedImage check(String name,
  Supplier<KitContext> kit, Projections projections)`, which plan 9b Task 7 extends with the compositions;
  `ChromeRenderTest.everyLookBuildsItsSettingsFrame`.

- [ ] **Step 1: Write the bridge and the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsFrameRenders.java`:

```java
package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.SettingsShell;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Test bridge for plan 6a's ChromeRenderTest (spec 16.1.4, plan 9a Task 7): the real Settings dialog under a look's kit
 * and projections, its frame checked against what the look's SettingsChrome names, printed at 2x. EDT only.
 */
public final class SettingsFrameRenders {
    static final Path FILE = Path.of("/fixture/config.toml");
    static final int WIDTH = 1000, HEIGHT = 700;

    private SettingsFrameRenders() { }

    /** The 2x print's pixel at {@code (x, y)} of {@code part}, without alpha. */
    static int at(BufferedImage image, JComponent part, JComponent root, int x, int y) {
        Point point = SwingUtilities.convertPoint(part, x, y, root);
        return image.getRGB(2 * point.x, 2 * point.y) & 0xFFFFFF;
    }

    /**
     * Opens the dialog at Command Palette, with its visible rows modified, under {@code kit} and {@code projections};
     * checks the frame the look's SettingsChrome names (the title row, the classic frame's rules and footer band, the
     * section triangles, the inline Reset) and returns the dialog's 2x print. EDT.
     */
    public static BufferedImage check(String name, Supplier<KitContext> kit, Projections projections) {
        var chrome = projections.settings();
        var parsed = ConfigLoader.parse(FILE, "[palette]\nvisible_rows = 9\n", true);
        var state = new ConfigService.State(parsed.snapshot(), parsed.diagnostics(), FILE, true, parsed.legacyKeys(), parsed.deadTables(),
            parsed.values(), parsed.rejected());
        var holder = new JComponent[1];
        var windows = new AuxiliaryWindows(UiState.inMemory(), surface -> {
            holder[0] = surface.holder();
            return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, WIDTH, HEIGHT), (title, initial) -> Optional.empty());
        }, (choice, cancel) -> List.of(), projections.dialogs());
        var dialog = new SettingsDialog(windows, context -> CorePages.pages(AppearanceFixtures.CHOICES,
            new CorePages.SystemActions(() -> FILE, () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.empty()),
            page -> new GroupPage("keymap", "Keymap", Optional.empty(), false, List::of, page),
            page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page)).apply(context),
            new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state, listener -> new Subscription(() -> { }),
                () -> { }, () -> { }, uri -> { }, (parent, count) -> true, kit, chrome, projections.composition()));
        try {
            dialog.open(null, "command-palette");
            var root = holder[0];
            var image = MockProfile.print(root, WIDTH, HEIGHT);
            var view = dialog.view();
            var context = kit.get();
            var theme = context.theme();
            boolean classicFrame = !chrome.integratedTitle();
            assertThat(view.search.getY() < view.titleRowHeight()).as(name + ": a native title gives the search its own strip")
                .isEqualTo(classicFrame);
            if (classicFrame) {
                var pairs = SettingsShell.framePairs(context);
                assertThat(at(image, view.navigation, root, view.navigation.getWidth(), 20)).as(name + ": the sidebar rule")
                    .isEqualTo(SurfaceContrast.painted(theme, pairs.get(0)).getRGB() & 0xFFFFFF);
                assertThat(at(image, view.body, root, 40, -1)).as(name + ": the header rule")
                    .isEqualTo(SurfaceContrast.painted(theme, pairs.get(1)).getRGB() & 0xFFFFFF);
                assertThat(at(image, view.footer, root, view.footer.getWidth() / 2, -1)).as(name + ": the footer rule")
                    .isEqualTo(SurfaceContrast.painted(theme, pairs.get(2)).getRGB() & 0xFFFFFF);
                assertThat(at(image, view.footer, root, -4, view.footer.getHeight() / 2)).as(name + ": the footer band")
                    .isEqualTo(DerivedColor.SURFACE_FOOTER.color(theme).getRGB() & 0xFFFFFF);
                for (var pair : pairs)
                    assertThat(pair.guard().holds(SurfaceContrast.painted(theme, pair), pair.against().color(theme)))
                        .as(name + ": " + pair.slot().key() + " on " + pair.against().key() + " meets its guard").isTrue();
            }
            assertThat(KitTestSupport.find(view, "@tree/pages/appearance-behavior/expand", AbstractButton.class) != null)
                .as(name + ": section triangles exactly when the navigation collapses").isEqualTo(chrome.collapsibleNav());
            assertThat(KitTestSupport.find(view, "palette.visible_rows:reset", JComponent.class) != null)
                .as(name + ": a Reset button exactly when Reset is inline").isEqualTo(chrome.inlineReset());
            return image;
        } finally {
            dialog.cancel();
            windows.close();
        }
    }
}
```

The probes sit one hairline outside the parts the frame places (the body starts below the header rule, the footer one
hairline below its rule and `SETTINGS_FOOTER_SIDE` in from the band's edge), so each reads the shell's own paint.
`KitContext.theme()` is the `GeneratedTheme` the context paints with; `SettingsView`'s fields and `titleRowHeight()` and
`SettingsDialog.view()` are package members the bridge shares a package with.

Add the test to `ChromeRenderTest` (6a's), before `theCoveringArrayHasStrengthTwoOverTheChromePoints`:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 7 step 1: ChromeRenderTest renders and checks the Settings frame under every look."""
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java"); text = path.read_text(encoding="utf-8")
anchor = "    @Test void theCoveringArrayHasStrengthTwoOverTheChromePoints() {"
if text.count(anchor) != 1: raise SystemExit("ChromeRenderTest: the covering array test moved")
block = '''    /** Plan 9a (spec 12.1 and 16.1.4): the real Settings dialog under each look's kit and projections, its frame checked and printed. */
    @Test void everyLookBuildsItsSettingsFrame() throws Exception {
        for (Case look : cases()) {
            edt(() -> {
                ThemeController themes = look.themes().get();
                try {
                    var image = dev.jasper.app.settingsui.SettingsFrameRenders.check(look.name(),
                        () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), look.projections());
                    MockProfile.write(image, "settings-" + look.name());
                } finally {
                    themes.close();
                    EnterpriseLook.restoreDefault();
                }
            });
        }
    }

'''
path.write_text(text.replace(anchor, block + anchor), encoding="utf-8")
PY
```

`ChromeRenderTest` imports `ThemeController`, `EnterpriseLook`, `MockProfile` and `DesktopTestSupport.edt`. Add the
bridge's `migration.csv` row:

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
row = ("jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsFrameRenders.java,app.settingsui,SettingsFrameRenders,nonvisual-helper,none,none,"
       "jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java,Test/fixture ownership; New in Enterprise plan 9a Task 7; the Settings frame in the chrome renders")
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ChromeRenderTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `ChromeRenderTest` with `everyLookBuildsItsSettingsFrame` (every case of `cases()`: the default and
complement looks integrated, the complement with triangles and no Reset button, the fallback and the native-title
covering rows with the classic frame), and `jasper-app/build/chrome-renders/settings-*.png` written. Tasks 1 to 5 built
what this checks, so the test passes on its first run; a failure is a defect of the task that built the part it names:
fix it there (both role tables for a value), never the assertion. If a covering row's look names a frame the dialog
does not build, read `Projections.of` for that row's `SettingsChrome` and record the case in the banner.

- [ ] **Step 3: Commit**

```bash
git add jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsFrameRenders.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Settings chrome: the Settings frame in the chrome renders

ChromeRenderTest opens the real Settings dialog under every look it
renders (the default, Enterprise and complement kits, the fallback and the
covering array) and checks the frame its SettingsChrome names: the title
row, the classic frame's guarded rules and footer band, the section
triangles and the inline Reset. The prints go beside the window renders.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: Documentation, STATUS and the whole build

**Files:**
- Modify: `docs/app-architecture.md:166`, `docs/configuration.md:84-86`, `docs/ui-maintenance.md` (one sentence), `docs/STATUS.md` (new entry at the top)
- Modify: this plan's status banner

**Interfaces:**
- Consumes: everything above.
- Produces: documentation only.

- [ ] **Step 1: Edit the documents**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9a Task 8 step 1: the smallest accurate documentation edits (spec 17)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/app-architecture.md", [
    ('''the application supplies the Keymap, Plugins and plugin tool pages (`CorePages.Pages`). SettingsDialog owns each opening's pages and disposes them on close. |''',
     '''the application supplies the Keymap, Plugins and plugin tool pages (`CorePages.Pages`). SettingsDialog owns each opening's pages and disposes them on close. The look's `presentation.SettingsChrome` sets the frame (a native title, the collapsible navigation), Reset inline or in the row's menu, the rows the look implies and the font row's copy; every page's `PageContext` carries it with the `Composition` only the composition roots read. |''', 1)])
edit("docs/configuration.md", [
    ('''changes shows its name in blue, and a setting that differs from its default has a Reset button, which removes
the key from the file.''', '''changes shows its name in blue, and a setting that differs from its default has a Reset button (under the
Enterprise look, Reset to Default in the row's context menu), which removes the key from the file.''', 1)])
PY
grep -n "SettingsShell\|Settings shell" docs/ui-maintenance.md docs/ui-architecture.md | head
```

If `docs/ui-maintenance.md` or `docs/ui-architecture.md` describes `SettingsShell`, add after that description, in its
voice, one sentence: "`SettingsShell.Frame` says whether the shell's header row is the window's title row; a frame that
is not draws the classic search strip, ruled frames and footer band, and `collapsibleNavigation` gives the navigation
its section triangles." If neither describes it, add nothing there.

- [ ] **Step 2: Run the whole build**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Then count the results:

```bash
python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
tests = failures = errors = skipped = 0
for path in pathlib.Path(".").glob("*/build/test-results/test/*.xml"):
    root = ET.parse(path).getroot()
    tests += int(root.get("tests", 0)); failures += int(root.get("failures", 0)); errors += int(root.get("errors", 0)); skipped += int(root.get("skipped", 0))
for path in pathlib.Path("plugins").glob("*/build/test-results/test/*.xml"):
    root = ET.parse(path).getroot()
    tests += int(root.get("tests", 0)); failures += int(root.get("failures", 0)); errors += int(root.get("errors", 0)); skipped += int(root.get("skipped", 0))
print(f"{tests:,} tests, {failures} failures, {errors} errors, {skipped} skipped")
PY
```

- [ ] **Step 3: Prove the default look unchanged once more**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
```

Expected: `changed: none`.

- [ ] **Step 4: Write the STATUS entry and the banner**

Add at the top of `docs/STATUS.md`, above the newest entry, in the style of the plan-1 and plan-2 entries (fill the
bracketed values from this execution: the commit range, the calibrated values, the measured shares, the counts):

```markdown
**Enterprise presentation, plan 9a: the Settings chrome — 2026-10-16** (`claude/enterprise-settings`, worktree
`.claude/worktrees/enterprise-9`, `[first]..[last]`, then this documentation commit;
[plan](superpowers/plans/2026-10-16-enterprise-9a-settings-chrome.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). Spec 12.1 under the Enterprise look,
measured on the eighteen `05 Settings` mocks. `Presentation.Landed.SETTINGS` lands, so `SettingsChrome` and
`Composition` follow the kit file; `SettingsDialog.Hooks` carries both from the application's presentation and every
page's `PageContext` carries both (without a presentation, `Projections.standard`), and `CompositionReadTest` keeps
`Composition` reads in spec 9.1's six roots. jasper-ui receives values: `SettingsShell.Frame(integratedTitle,
collapsibleNavigation)` draws the classic dialog when the header is not the title row (a search strip on
`surface.sidebar` ruled in `border.row`, the navigation on the `navigation_surface` slot, `border.window` rules through
the structural guard, a `surface.footer` band); `Nodes.SettingRow.contextActions` (model only) is the row's own menu,
shown by the popup trigger, Shift+F10 or the menu key; the collapsible navigation uses the tree face's triangles with the
navigation's own measured geometry (rows 22, side 9, triangle 6 + 6, children 15 deeper, the selection across the
sidebar). Under `inlineReset = false` every Settings row offers Reset to Default in its menu; `hiddenKeys` decides the
built rows (the title-bar row's macOS rule is the default look's hidden key now); the interface font row takes
`fontRowCopy` and starts its stepper at the look's size (12). New roles `SETTINGS_HEADER_INSET` (20; classic 14),
`SETTINGS_NAV_CHILD_INDENT` (0; 15), `SETTINGS_NAV_TEXT_GAP` (6); classic Settings values in `DesignMetrics.CLASSIC`
(header 34, footer 45, buttons 98, "?" 22, page inset 16, dropdowns 220, page top [n], section gap [n], crumb gap [n]).
`EnterpriseSettings` opens the real dialog under the shipped kit, Plex and the Enterprise theme over the bundled plugins;
`SettingsChromeMockTest` compares each page's sidebar, header and footer with its mock: probes on every OS, pixel shares
on macOS ([lowest share per part]); its fixture builds on plan 6a's `EnterpriseLook` and restores the default look when it
closes, and its comparison helper is the package-private `settingsui.SettingsMockMatch` on 6a's `MockProfile`.
`ChromeRenderTest.everyLookBuildsItsSettingsFrame` (through the test bridge `settingsui.SettingsFrameRenders`) opens the
real dialog under every look 6a's chrome renders cover and checks the frame its `SettingsChrome` names;
`PaintedContrastTest` samples the classic frame's three guarded rules over every palette. Zero pixel change in the default
look: the gallery and the Settings preview are byte-identical to the base. Rulings (plan, "Rulings"): [the nineteen, one line each]. Deviations from the plan's text:
[the re-anchorings and adaptations, or "none"]. `./gradlew check --continue`: **[n] tests, 0 failures, 0 errors, [n]
skipped**. GUI checks for the user: Settings under Enterprise on macOS (the native "Settings" title, the strip, the
triangles, a right-click on a modified row) and on Windows or Linux (no title-bar or animations rows). Hand-off to plan
10a: the mock helpers are 6a's `testsupport.MockProfile`, 7's `testsupport.MockCrops` and 9a's package-private
`settingsui.SettingsMockMatch`; 10a's `testsupport.MockMatch` takes a path none of them uses and reads mocks and ΔE through
`MockProfile` rather than adding another Lab conversion. Not merged, not pushed.
```

Update this plan's status banner (a line under the title): "Executed on 2026-10-16: [first]..[last]; calibrated
[values]; deviations [list or none]."

- [ ] **Step 5: Commit**

```bash
git add docs/app-architecture.md docs/configuration.md docs/ui-maintenance.md docs/ui-architecture.md docs/STATUS.md \
  docs/superpowers/plans/2026-10-16-enterprise-9a-settings-chrome.md
git commit -F - <<'EOF'
Settings chrome: documentation and status

The architecture guide names SettingsChrome's part in Settings, the
configuration guide Reset to Default in the row's menu under Enterprise,
and STATUS records plan 9a with its calibrated values, measured shares and
the whole build's counts.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```
