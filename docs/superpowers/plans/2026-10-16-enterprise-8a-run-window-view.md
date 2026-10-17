# Enterprise presentation 8a: the Run window view Implementation Plan

> **Status:** executed 2026-10-17 on `claude/enterprise-run-window` (`b6baeeab3..720b22d27`, then the documentation commit); deviations: (a) Task 3's review round (`c16f1a8dd`): a SWATCH glyph takes a row's glyph size only under `runWindow`, so a compact panel row's swatch keeps its caption size (spec 8.2); `RunWindowPresentation.releaseHelpChildren` returns the help glyph's colours when the flag turns off; `NewRolesTest`'s Run window names sit on their own lines; three more tests (`RunWindowPresentationTest` 2, `SwatchesTest` 1), so the plan adds 36 tests, not 33; (b) Rulings 4's type role sizes are the classic scale's, `RUN_HEADING` 11/600 and `RUN_DETAIL` 10.5/400 (the default scale's are 12/600 and 11.5/400); (c) Task 1 shows the step's card before `step.show` in `CardView.showStep`, so the first field can take focus; (d) the final review's fixes ("Plan 8a: final review fixes"): `RunWindowView.showStep` shows the card first too; a step taller than the list box scrolls inside it and the focused field scrolls into view; every pair the Run window paints is declared and guarded, amending Rulings 8 (the muted details, tags and count and a selected row's detail TEXT, the help glyph INDICATOR, the field and list box edges CONTROL, the hue squares INDICATOR with a hairline ring in the guarded colour around a square whose fill misses; `authored()` is gone); the help text centred in its line, at most two lines, in a help row of one height; context-menu verbs are `verb:<verb>:<entry key>`, amending Rulings 11; `setChip` re-reads the ▼ menu; the checkbox's label follows the toggle's drawn width; the 10-argument `PaletteController` constructor and `PaletteEntry.Header(String)` are gone; a FONTS-owned "help text top" probe; three more tests.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The palette gets its second view: `PaletteView` is extracted from today's card (renamed `CardView`), headings
are typed, and `RunWindowView`, assembled from kit nodes and drawn by a new jasper-ui Run window presentation, renders
the two `06 Run Dialog` mocks under the Enterprise kit and theme within stated tolerances, with "Recent", "Most used",
"Matches" and "i of n", hue squares, the "{Noun}: {detail}" line, the SPLIT checkbox and the primary verb button.
`PaletteViews.of` picks the view from `PaletteLook`; nothing reaches the Run window yet (plan 8b lands it).

**Architecture:** `dev.jasper.app.palette` gains the `PaletteView` interface (the controller-facing API of today's
card), `CardView` (today's `CommandPalette`, renamed), `RunWindowView`, `PaletteViews.of`, the shared `StepForm` and
`QueryEdits` (moved out of the card, used by both views) and the pure `RunCopy` (the Run window's headings, nouns and
"i of n"). `PaletteController` builds its view through `PaletteViews.of(PaletteLook, …)` with one `PaletteView.Host` of
callbacks. jasper-ui owns every Run window pixel: `KitView.runWindow(boolean)` is an app-only presentation flag (like
`titleStrip` and `setHintPresentation`) under which RESULTS lists, PROMPT_HEADER tiles, transparent fields and text
roles take the Run window's metrics and colours, and `SurfacePanel` gains the `RUN_WINDOW`, `FIELD` and `BOX` kinds that
paint the frame, title and footer bands, the Open field's box and the list's box. The app only lays views out.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ (`python3 -I`) with Pillow for the edit scripts and the mock measurements.

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 5.5, 7.3, 9.1, 11.1 to 11.4, 13 (`RECENT_ROWS`), 16.1.1, 16.1.2, 16.1.4, 16.1.7, 16.1.9, 17 and 19.1 row 8). This is
the first of two plans for row 8 (Rulings 1); [8b](2026-10-16-enterprise-8b-run-window.md) executes after it on the same
branch: `PaletteHome` and its two homes, the saved place per home kind, the modeless window and focus hand-back,
Browse…, the landing of `Presentation.Landed.RUN_WINDOW` and the workspace renders.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-8`, branch
`claude/enterprise-run-window`, which holds `main` at `b9178db45` (plans 3a to 3e, 4a and 5a) plus the plan commits of
8a and 8b. Run every command from the worktree root. Line numbers below were re-verified at `b9178db45` on 2026-10-16;
every edit quotes the exact text it replaces and the edit scripts stop with a message when that text is not found the
stated number of times. Plans 4b and 6a run in parallel in other worktrees and edit some of the same files
(`Labels.java`, `ItemLists.java`, `Stacks.java`, `TextFields.java`, `DesignMetrics.java`, `compact-roles.tsv`,
`NewRolesTest`, `PaintedContrastTest`, `migration.csv`, `docs/STATUS.md`); this plan's edits there stay appended and
textually local, and are reconciled at merge (see "Execution notes (2026-10-16)" at the end). If a script stops on an
anchor, re-find the quoted text (it moves, it seldom changes) and re-run; never edit around a missing anchor.

## Before you start (the refresh step)

This plan was written while plans 6 and 7 were being written; it relies on the earlier plans by name. The branch already
holds `main` at `b9178db45`; do not rebase or merge. Check that the plans it uses have landed:

```bash
grep -c 'RECENT_ROWS = 3' jasper-app/src/main/java/dev/jasper/app/palette/PaletteController.java          # 3e: 1
grep -c 'Role role' jasper-app/src/main/java/dev/jasper/app/palette/PaletteVerb.java                      # 3d: 1
grep -c 'default dev.jasper.ui.model.Nodes.Hue hue()' jasper-app/src/main/java/dev/jasper/app/palette/PaletteScope.java   # 3d: 1
grep -c 'SWATCH' jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java                                    # 3b: >= 1
grep -c 'public static final Kit CLASSIC' jasper-ui/src/main/java/dev/jasper/ui/kit/Kit.java              # 4a: 1
grep -c 'public static Kit enterprise()' jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java   # 4a: 1
ls jasper-app/src/main/java/dev/jasper/app/presentation/PaletteLook.java                                  # 5a
grep -c 'OPEN_FILE' jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java                         # 3e: 1
```

Expected: every `grep -c` prints at least 1 and the `ls` finds its file. If any fails, stop. Plans 4b (classic faces),
4c (classic data faces) and 5b (the Enterprise plugin, its theme and IBM Plex) are not on `main`, and this plan uses
none of their code (Execution notes, R1): the Run window draws its own help tile, the Enterprise Light fixture theme is
4c's text added here, and the mock comparison runs under the Enterprise fixture kit with the platform faces. Then
re-anchor: for every "Modify" line below, open the file and confirm the quoted anchor text exists once; record any
substitution in the status banner. Record the `./gradlew check --continue` test count of the branch before Task 1 (the
base for Task 8's count).

### Earlier plans' names this plan uses

| Name | From |
|---|---|
| `PaletteEntry`, `PaletteScope.hue()` (never NONE), `PaletteScope.itemNoun()` (the label when none), `PaletteVerb(id, label, Role)`, `PaletteVerb.Role { STANDARD, SPLIT }` | 3d Task 2 |
| `RECENT_ROWS = 3` | 3e Task 8 |
| `Nodes.IconSize.SWATCH`, `Labels.Swatch`, the SWATCH branch of `Labels.IconControl.refresh` | 3b Task 4 |
| `DesignMetrics.Role.RUN_WINDOW_WIDTH` (460 in both catalogs), `CHECKBOX_BOX` (13), `CHECKBOX_LABEL_GAP` (7), `FIELD_RADIUS`, `classic-roles.tsv`, `NewRolesTest.ADDED`, `TypeScale.CLASSIC`, `classic-type.tsv`, `Kit.CLASSIC`, `Kit.colors()` (`ColorSlots.dialogSurface()`), `KitFixtures.enterprise()` | 4a Tasks 2 to 6 |
| `ThemePainting.focusHaloShare` (already on `main`, `ThemePainting.java:121`) | before plan 4 |
| `KitFixtures.enterpriseLight()`: plan 4c Task 4's text, added by this plan's Task 3 (R4) | 4c Task 4 |
| `dev.jasper.app.presentation.PaletteLook(View { CARD, RUN_WINDOW }, Home { TITLE_FIELD, CORNER })` (no constants: Task 6 adds `DEFAULT` and `RUN_WINDOW`) | 5a Task 8 |
| `ActionId.OPEN_FILE`, package-private `WindowContent.openFile()` | 3e Task 2 |

Not used (not on `main`): 4b's `KitContext.faces()`, `ClassicPromptFace`, `BesideLayout`, `FieldSurface.paintDefault`
and classic checkbox and button faces; 5b's `EnterpriseResources`, the shipped kit and theme and the Plex files.

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md). Headless JUnit renders and `:jasper-ui:gallery` (headless) may be run.
- Work only in the worktree above on `claude/enterprise-run-window`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: the default look stays byte-identical. No existing render changes: every file the headless gallery writes for Slate Cobalt Dark and Light is byte-identical to the base captured in Task 3 Step 1, and every reference pair and calibration suite stays green unchanged. The Run window presentation paints only inside a `KitView` whose host called `runWindow(true)` and inside the three new `SurfacePanel` kinds, and the default look builds neither (it builds `CardView`). The new metric roles and type roles change no existing role's value. The card's copy stays "Recently used", "Most used", "Suggested" and scope labels (spec 11.3).
- `PaletteView` and `PaletteHome` (8b) are pairs of real implementations created in one factory each (spec 3.3): `PaletteViews.of` is the only class that reads `PaletteLook.view()`; no class outside `dev.jasper.app.presentation` (whose `Projections` builds the looks) names `PaletteLook.View` (8b extends the variant-read scan to enforce it).
- "No app code paints it" (spec 11.1): `RunWindowView` sets bounds, borders of zero and visibility only; every fill, rule, glyph and text colour is painted by jasper-ui (`KitView` controls and `SurfacePanel`).
- SDK types never appear in `dev.jasper.app.palette` (AGENTS.md); the palette uses `dev.jasper.ui.model` values only.
- Javadoc doclint runs in `check`: jasper-app with `-Xdoclint:all`, jasper-ui with `-Xdoclint:all,-missing`; every `{@link}` resolves and no comment contains a raw `<`, `>`, `&` or `->`. New public jasper-ui members get a Javadoc sentence.
- Every new candidate source under jasper-app, the SDK, the testkit or plugins gets its `docs/design/theme-engine/migration.csv` row in the same commit (`ThemeMigrationCoverageTest`); a row has exactly eight comma-separated fields, so the disposition text uses semicolons, never commas. The renamed `CommandPalette.java` row is renamed with the file.
- Frozen tables: every new `DesignMetrics.Role` gets a row in `compact-roles.tsv` and `classic-roles.tsv`, every new `Typography.Role` a row in `default-type.tsv` and `classic-type.tsv`, in enum order at the end.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file. The symbols this plan's sources use (`▶`, `▼`, `…`, `⇧`, `⌘`, `⌃`, `⌥`) are ordinary BMP characters, which the rule allows; the sources write them as written (the card already writes `…`), and a test that compares with them may use `\u` escapes.

## Review Focus

1. The user ticks "Open in a split pane", then moves the selection to a row whose scope has no SPLIT verb (a snippet):
   the box clears and disables, and Enter runs that row's verb 0, never a split. Task 5:
   `RunWindowViewTest.theSplitBoxFollowsTheSelectionAndClearsOnAScopeWithoutASplitVerb`.
2. A query that matches several scopes with "More in…" rows: one "Matches" heading, "i of n" counts only real rows, and
   a selected "More in…" row blanks the count and makes the primary button read "Open". Task 1:
   `RunCopyTest.iOfNCountsMatchingRowsOnlyAndBlanksOnAMoreRow`; Task 5: `RunWindowViewTest.aQueryShowsOneMatchesHeadingWithItsPosition`.
3. Very long titles and details (a 300-character path) and a tag: the tag drops first, the detail is cut with an
   ellipsis, the window never widens, and the "{Noun}: {detail}" line ellipsises at the window's right padding. Task 5:
   `RunWindowViewTest.longTextNeverWidensTheWindow`.
4. Fewer results than `palette.visible_rows` (one match, or nothing found): the list keeps its fixed height, the
   empty text shows inside the box, and the primary button reads "Open", disabled. Task 5:
   `RunWindowViewTest.theListKeepsItsHeightWithOneRowOrNone`.
5. A step (a scope's inline form) opened from the Run window: it renders in the list box exactly as in the card, Tab
   moves between its fields, Escape leaves it, and the checkbox and primary button stay inert while it shows. Task 5:
   `RunWindowViewTest.aStepRendersInTheListBoxAsInTheCard`; Task 6: `PaletteViewContractTest.stepsBehaveTheSameInBothViews`.

## Verified facts

Re-verified at `b9178db45` (2026-10-16).

1. `CommandPalette` (`jasper-app/src/main/java/dev/jasper/app/palette/CommandPalette.java`, 673 lines) is the card:
   `extends dev.jasper.ui.swing.SurfacePanel` (`:49`), built by `PaletteController` (`PaletteController.java:49`) with
   `(kit, macOs, this::queryChanged, this::execute, this::prefix, scope -> selectTab(scope.id()))`; its
   controller-facing calls are `setQueryText`, `setVisible`, `setChip(label, icon)`, `setPlaceholder`,
   `setVisibleRows`, `visibleRows`, `composing`, `queryText`, `executeSelected(verb)`, `selectRelative`, `showStep`,
   `hideStep`, `focusStepField`, `stepValues`, `setStepError`, `selectRow`, `selectedKey`, `setEntries`, `refreshTheme`
   and `close` (`PaletteController.java:78-425`); `WindowCommandPalette` also calls `focusQuery`, `setReveal`,
   `revealedHeight`, `getPreferredSize` and mounts it (`WindowCommandPalette.java:62-65`, `:98`, `:140-163`, `:260`).
2. The card's step form is `stepTitleView`, `stepErrorView`, `stepTitleRow`, `stepErrorRow`, `stepPanel`, `stepRows`
   and `StepRow` with `stepTitleSpec`, `stepErrorSpec`, `stepLabel`, `stepFieldSpec`, `buildStepRows`, `clearStepRows`,
   `applyStepGeometry`, `showStep`, `hideStep`, `focusStepField`, `stepInput`, `stepValues` and `setStepError`
   (`CommandPalette.java:62-79`, `:126-139`, `:256-305`, `:524-588`, `:662-671`); a typed scope prefix becomes the chip
   through `edited`, `consumedPrefix`, `settleConsumedPrefix` and the static `removal` (`:326-367`). The card's unused
   `import dev.jasper.app.platform.AppIcons;` (`:4`) goes with the rewrite.
3. `PaletteEntry.Header(String label)` is the only heading shape (`PaletteEntry.java:8-10`); the controller makes
   "Recently used" and "Most used"/"Suggested" (`PaletteController.java:215-216`), a chosen scope's section label
   (`:348`) and each scope's label under a query (`:370`), with "More in…" rows from a one-row probe (`:354-373`).
   Tests make one-argument headers in `CommandPaletteTest` (14 sites) and `PaletteTestSupport.describe` prints
   `"# " + header.label()` (`PaletteTestSupport.java:68`).
4. `PaletteTestSupport` takes `CommandPalette` in 35 public methods (`PaletteTestSupport.java:25-180`), which the
   workspace tests call with `owner.commandPalette().component()` (`WindowCommandPaletteTest` 44 times,
   `CommandPaletteShortcutsTest` 9, `CommandPalettePreview` 7, `PaletteKeyRouterTest` 3) or through a local
   `palette.component()` (`PaletteScopesTest`, `ShortcutEditorIntegrationTest`, `WindowContributionsTest`); `WindowCommandPaletteTest:458,466,477`
   call `palette.component().revealedHeight()`, and `theCardDripsOpenFastThenSlow…` holds `var card = palette.component();`
   (`:277`) and calls `card.revealedHeight()` four times (`:280`, `:287`, `:288`, `:291`); no other `card.` call in that file
   needs more than `JComponent`. `CommandPaletteTest` builds the card with the six-argument constructor
   at `:66`, `:133`, `:158`, `:207` and calls `setChip(String, Icon)` at `:161`, `:168`, `:209`.
5. Kit text colours: LABEL and DESCRIPTION draw `text.secondary`, CAPTION, COUNT, SECTION_LABEL, MONO_CAPTION and
   SUPPORTING draw `text.tertiary`, the rest `text.primary` (`Labels.java:130-134`); DESCRIPTION's wrapped line height is
   `DESCRIPTION_LINE_HEIGHT` scaled by BODY's size over 13 (`Labels.java:139-141`); `TextControl.refresh` ends with
   `((LiteralLabel)label).wrapped.centeredLeading = supporting;` (`:143`). A MONO, MONO_SMALL or MONO_CAPTION text is cut
   at its start (`Jasper.leadingEllipsis`, `Labels.java:123`, and for a row's controls `InteractiveRows.java:1006-1010`).
   Controls reach their view as `view` (`ControlRegistry.java:93`), and `Control.update` refreshes (`:104`). App-only
   view flags exist: `setHintPresentation` (`KitView.java:124-128`), `managerColumn` (`:305-309`, re-reconciling with
   `update(spec)`) and `titleStrip` (`:319-326`).
6. RESULTS lists: rows and headings are `ResultMetrics.height(context, heading)` tall (`ItemLists.java:174-177`,
   `ResultMetrics.java:10-13`); a heading lays out only its title at `RESULTS_ROW_PADDING` (`InteractiveRows.java:1189-1205`,
   which also serves identity lists since 3c) and paints a top rule after the first row (`:1236-1245`); a data row lays out
   an icon column of `ICON_RAIL`, the title, the detail and a tag that drops first (`:1206-1234`); selection fills the
   row edge to edge with the accent ink (`:855-866`, `:254`); `populate` styles headings in `RESULTS_HEADING`, details in
   `text.tertiary` and a selected row's texts in the fill's ink, then sets a leading glyph's size with
   `glyph.glyph(panelCompact ? … : 0)` (`ItemLists.java:398-455`).
7. A PROMPT_HEADER stack puts its `Icon` child in `StackControl.tile`, a `Stacks.Tile` sized by its `final
   DesignMetrics.Role sizeRole` (`PROMPT_TILE`, 54 default and 30 classic) and painted on `surface.control` inside
   `border.control` (`Stacks.java:46`, `:124-129`, `:169-204`), and lays the tile over its text with `PromptLayout`
   (`:206-257`, a `Composition.WidthLayout`); the reconciler centres a prompt header's text on every pass
   (`ViewReconciler.java:333`). Plan 4b (not on `main`) moves the layout and the tile's painting into a `PromptFace`; this
   plan draws the Run window's help tile itself (R2). `PROMPT_TILE_GAP` is 14 and `DESCRIPTION_LINE_HEIGHT` 18
   (`compact-roles.tsv:354,356`).
8. A TRANSPARENT field paints its focus ring inside its own bounds and otherwise an outline, in
   `FieldSurface.paintComponent` (`TextFields.java:449-456`); its font is MONO for PORT, PATH and TECHNICAL fields, else
   BODY (`:284`); its `FieldSurface` has `transparent` and `radiusRole` (`:395`), set in `FieldControl.refresh`
   (`:299-300`), and its input has an empty border (`:42`), so the surface's border (`:320`) is the only inset.
9. Built-in icons are paths; `FILLED_BUILT_INS` is `Set.of("folder.filled")` (`IconRegistry.java:21`); `play` is the
   stroked path `M 5 2 L 14 8 L 5 14 Z` (`:403`) and is drawn in the default gallery's RESULTS fixture
   (`StateFixtures.java:75`). `IconRegistryTest:113` lists built-in names (this plan does not edit it). The testkit's
   `FakeKit.BUILT_INS` mirrors the names (`FakeKit.java:21`).
10. `SurfacePanel` has `Kind { POPUP, CONTENT, NOTICE }`, the constructors `(context, kind)` and `(context, kind, header)`,
    and paints in `paintComponent` and `paintChildren` (`SurfacePanel.java:16-100`); `SurfacePanel.measuredHeight` measures
    a one-root `KitView` through its root's width layout (`:336-350`), and `SurfacePanel.footerBand` is the precedent for a
    kit-painted band in a native shell (`:357-375`).
11. `Typography.font` draws MONO, MONO_SMALL, MONO_CAPTION and PANEL_DETAIL_MONO in the data face
    (`Typography.java:74-75`); the role list ends `ORDERED_ACTION(12, 400);` (`:27`); `default-type.tsv` and
    `classic-type.tsv` list every role in enum order (38 rows); `TypographyTest.former` names the data roles
    (`TypographyTest.java:144-145`). `DesignMetrics.Role` ends `DOCK_RULE_HIT(6);` (`DesignMetrics.java:136`), and both
    role tables end with its row (594 rows).
12. `PaletteController`'s constructor is `(KitContext, ScopeRegistry, CommandHistory, boolean macOs, Runnable layout,
    Runnable dismissed, BooleanSupplier batching, Runnable updateActions, Consumer<String> reportError,
    Consumer<String> reopen)` (`PaletteController.java:42-51`), called only by `WindowCommandPalette.java:59-61`.
    `ScopeRegistry.scopes()` is package-private (`ScopeRegistry.java:45`); `modeScopes()` orders Commands first
    (`PaletteController.java:175-180`). `CommandHistory.uses()` is most recent first and `recordUse(scopeId, rowId, title,
    now)` counts a run (`CommandHistory.java:82-100`). `WindowContent.paletteShortcut()` (`WindowContent.java:688`) and
    `openFile()` (`:676`) are package-private.
13. `KitFixtures.render(view, width, height, scale)` lays out a `KitView` three times and paints it scaled
    (`KitFixtures.java:233-246`); `KitGallery.installed(theme, kit, action)` installs
    FlatLaf for a theme and kit, puts a 13-point `defaultFont` (the default kit's size, which `SwingTypography.user()` then
    applies to every role) and runs `action(context, focus)` on the EDT (`KitGallery.java:170-193`); `KitContext.typography()`
    reads the font setting on each call (`KitContext.java:61-62`).
14. `tools/visual/pairs.json` is a list of `{name, mock, crop, render[, render_crop][, note]}` with `crop` a JSON list
    `[x, y, w, h]` in the mock's pixels, written as `json.dumps(…, indent=2)` with ASCII only; plan 4c (not on `main`)
    adds `min_agreement`, `colours` and `check_pairs.py`, and `compare_all.py` ignores keys it does not read;
    `tools/visual/compare_mock.py` computes luminance edges (the 90th-percentile half threshold, at least 12) and the mean
    of both directions' share of edge pixels within one pixel of the other's (`compare_mock.py:40-79`).

## Mock measurements

Measured with Pillow (`python3 -I`) on `docs/design/enterprise/reference/06 Run Dialog/` at their 2× pixels (2884 × 1804),
halved to logical pixels. "A" is `Run Dialog - Just opened (recent + most used).png`, "B" `Run Dialog - Typing auth
(filtered).png`; both draw the window at the same place: its frame spans x 60-983 and y 854-1695 (2×). Positions are
logical, from the window's outer top-left (frame included). The Dark Mode mock does not show the Run window.

| Measured | Mock place (2×) | Logical value | Role, rule or colour (Enterprise Light) |
|---|---|---|---|
| Window outer size | frame x 60-983, y 854-1695 | 462 × 421 | `RUN_WINDOW_WIDTH` 460 inside a 1 px frame |
| Frame | x 60-61, y 854-855 | 1, corner radius about 4 | `border.hover` #8E949C; `RUN_WINDOW_RADIUS` 4 |
| Title band | y 856-903 | 24 tall | `surface.header.active` #D9DFEA; `RUN_WINDOW_TITLE_HEIGHT` 24 |
| Title rule | y 904-905 | 1 | `border.window` #C4C7CC |
| "Run" | ink x 84, cap 871-887 | x 11 (text at 10), 12 px semibold | WINDOW_TITLE, `text.primary` #1E1F22; `RUN_WINDOW_TITLE_SIDE` 10 |
| "⇧⌘P" | ink x 887-920 | ends at 429, 11 px | CAPTION, `text.tertiary` #5C6370; `RUN_WINDOW_SHORTCUT_GAP` 10 |
| × | ink x 948-963, y 873-887 | 8 × 8 glyph, centre (447, 12) | a GHOST `close` button, `RUN_WINDOW_CLOSE` 16 at `RUN_WINDOW_CLOSE_SIDE` 5 |
| Body | y 906-1611 | from 26, 353 tall | the dialog surface `surface.app` #ECEDEF |
| Help tile | x 86-149, y 930-993 | (13, 38) 32 × 32, 1 px edge | accent #2F65CA, edge `accent.edge` #2655AE; `RUN_WINDOW_TILE` 32 |
| ▶ in the tile | x 111-125, y 955-970 | 7.5 × 8, centred | `play.filled` (new) at `RUN_WINDOW_TILE_GLYPH` 13, `text.onAccent` |
| Help text | ink x 170, lines at 938 and 974 | at 55, 18 apart, 12 px, two lines | DESCRIPTION drawn at BODY, `text.strong.secondary` #3A3D42; `RUN_WINDOW_TILE_GAP` 10, `RUN_WINDOW_HELP_LINE` 18, `RUN_WINDOW_HELP_TOP` 10 |
| "Open:" | ink x 87-148, y 1033-1054 | at 13, centred on the field | LABEL, `text.strong.secondary` |
| Open field box | x 182-957, y 1020-1063 | (61, 83) 388 × 22 | `surface.input` #FFFFFF, focused edge accent, 2 px halo; `RUN_WINDOW_LABEL_COLUMN` 48, `FIELD_RADIUS` |
| Query text | "auth" ink x 197 (B) | 6 inside the box, data face about 12 px | MONO; `RUN_WINDOW_FIELD_SIDE` 6 |
| ▼ | x 931-941, y 1035-1047 | 5.5 × 6.5, centre 10.5 from the box's right edge | `triangle.down` (new) at CAPTION size, `text.secondary`; `RUN_WINDOW_MENU_WIDTH` 20 |
| List box | x 86-957, y 1084-1487 | (13, 115) 436 × 202 | `surface.card` #FFFFFF inside a 1 px `border.control` #B4B8BF |
| Heading band | y 1086-1121, rule 1122-1123 | 18 tall, then a 1 px rule | `surface.tableHeader` #F0F1F3, rule #DDE0E4 (`border.row` #E4E5E8, ΔE 3.4); `RUN_WINDOW_HEADING_HEIGHT` 18 |
| Heading text | "Recent" ink x 105, cap 1097-1112 | 8 in, 11 px semibold | RUN_HEADING (new, 11/600), `text.strong.secondary` (mock #4A4F57, 4c's precedent) |
| "1 of 11" | ink x 875-938 (B) | right edge 9 in | CAPTION, `text.placeholder` #7A808A |
| Row | y 1124-1165 and every 42 | 21 tall | `RUN_WINDOW_ROW_HEIGHT` 21 |
| Selected row | fill y 1124-1165 | full width, square | `selection.bg`/accent #2F65CA; title `text.onAccent`, detail `text.onAccent.secondary` #DCE5F7 |
| Hue square | x 104-117, y 1138-1151 | 7 × 7 at 8 in, centred; keeps its hue when selected | SWATCH at `RUN_WINDOW_SWATCH` 7, `RUN_WINDOW_ROW_SIDE` 8; hues #2E9E4F, #2F65CA, #8A4FBF, #B5651D |
| Row title | ink x 133 | 22 in, 12 px | BODY, `text.primary`; `RUN_WINDOW_ROW_GAP` 7 |
| Row detail | "dustin@192.168.1.119:22" x 191-478 | 7 after the title, 6.24 per character, cut at its end | RUN_DETAIL (new, data face 10.5/400), `text.placeholder`; `RUN_WINDOW_DETAIL_GAP` 7 (R11) |
| Row tag | "⌃⌥⇧C" x 891-936 | right edge 10 in | CAPTION, `text.placeholder` |
| List height | 1086-1485 | 200 inside | 8 rows + 2 headings at the mock's pitch is 206 (Rulings 5) |
| Detail line | "Session:" ink 87-170, value 185-472, cap 1515 | at 13, value 6 after the colon | BODY_STRONG drawn semibold, `text.strong.secondary`; value RUN_DETAIL, `text.secondary`; `RUN_WINDOW_DETAIL_TOP` 10, `RUN_WINDOW_DETAIL_LABEL_GAP` 6 |
| Checkbox | x 86-111, y 1562-1587 | 13 at (13, 354) | the classic checkbox (4b), edge #9AA0A8; `RUN_WINDOW_CHECK_TOP` 10; its row `CHECKBOX_BOX` + 2 × `FOCUS_RING` (R7) |
| Checkbox label | ink x 127 | 7 after the box | LABEL, `text.strong.secondary`; `CHECKBOX_LABEL_GAP` 7 |
| Body bottom | y 1611 | 12 under the box | `RUN_WINDOW_BOTTOM` 10 under the toggle's focus reserve |
| Footer | rule 1612-1613, band 1614-1693 | 1 + 40 | `border.window`, `surface.footer` #E6E7EA; `RUN_WINDOW_FOOTER_HEIGHT` 40 |
| Buttons | x 514-653, 666-805, 818-957; y 1630-1677 | 70 × 24 each, 6 apart, 12 from the right, 8 from the band's top | `RUN_WINDOW_BUTTON_WIDTH` 70, `RUN_WINDOW_BUTTON_GAP` 6, `BUTTON_HEIGHT` 24; primary accent with `accent.edge`, the others the classic fill |
| Side padding | list x 86, field right 957, buttons 957 | 12 | `RUN_WINDOW_SIDE` 12 |

The help row's first line is at 16 under the body's top and the tile at 12 (the 36 px text block centred on the 32 px
tile, starting at 10); the field box is 11 under the help row; the list box 10 under the field box.

## Rulings

Decisions where the spec is silent or a literal reading cannot be built. Record each in `docs/STATUS.md` (Task 8) and in
this plan's status banner.

1. **Two plans.** Row 8 is delivered as 8a (the view, drawn and compared) and 8b (the workspace: homes, saved place,
   modeless window, openers, Browse…, landing). Each leaves `check` green; 8a changes no visible behaviour. Cost if
   wrong: none; the order is the spec's.
2. **`CommandPalette` is renamed `CardView`** (spec 11.1 names it), with a git rename; `CommandPaletteTest` keeps its
   name, and `PaletteTestSupport` takes a `JComponent` and casts to `CardView`, so the workspace tests keep calling it
   with `owner.commandPalette().component()`. Cost if wrong: a rename.
3. **The step form and the prefix-to-chip edit are shared** (`StepForm`, `QueryEdits`), so "inline PaletteStep forms
   render in the list region exactly as they do in the card" (11.4) by construction. Cost if wrong: none.
4. **The Run window's geometry, two type roles and two icons are new kit values.** Thirty `RUN_WINDOW_*` metric roles
   (the "Mock measurements" table), equal in `DEFAULT` and `CLASSIC` (4a's rule for roles only classic faces read);
   `RUN_HEADING` (11/600 classic, 12/600 default) and `RUN_DETAIL` (data face, 10.5/400 classic, 11.5/400 default),
   because the mocks' headings are 11 px semibold and their data text 10.4 px (CSS 0.87 em), which no existing role
   draws; `play.filled` and `triangle.down` (filled paths), because the mock's ▶ and ▼ are solid and `play` is stroked
   in the default gallery. Cost if wrong: one value and its table rows each.
5. **The list's height is `visible_rows` rows plus two headings at the mock's pitch: 8 × 21 + 2 × 19 = 206** inside the
   box. The mock's box is 200, which cuts its eighth row by 6 px; spec 11.2's formula wins over that cut, so the window
   is 427 tall where the mock's is 421, and the comparison aligns the part under the list 6 px lower (Task 7). Cost if
   wrong: one formula.
6. **The title-row shortcut and the row tags are plain CAPTION text**, not boxed keycaps: both mocks draw them unboxed,
   while the toolbar's Search everywhere field (plan 6) keeps its boxed keycap. Spec 11.1's "the existing Keycaps"
   names the shortcut's text source (the live COMMAND_PALETTE binding). Cost if wrong: a node change.
7. **The help tile is `Icon("play.filled", BLUE, PROMPT)` in a PROMPT_HEADER stack**, which under `runWindow`
   `RunWindowPresentation` draws as an accent tile in an `accent.edge` hairline (the INDICATOR pair `tileEdge(Kit)`) with
   the glyph in the accent's ink, beside its message: the tile is `RUN_WINDOW_TILE` (the Run mock's 32, against the Unlock
   Vault mock's 30) and the gap `RUN_WINDOW_TILE_GAP` (Execution notes, R2). Cost if wrong: two values.
8. **Run window text colours, as declared pairs** (spec 7.3, 16.1.1). Under `runWindow`, LABEL, DESCRIPTION and
   BODY_STRONG text draws `text.strong.secondary` (the mocks' #3A3D42), DESCRIPTION at BODY's size with an 18 px line,
   BODY_STRONG at PANEL_TITLE (12/600), MONO at RUN_DETAIL in `text.secondary`, the title WINDOW_TITLE in `text.primary`
   and the title row's CAPTION shortcut in `text.tertiary`. Each is painted through a pair `RunWindowPresentation.pairs(Kit)`
   declares with the TEXT guard: the title and the shortcut on `surface.header.active`, the labels, help and detail label
   and the detail value on the kit's dialog surface, a heading on `surface.tableHeader`, a selected row's title on the
   accent and a "More in…" row's title on `surface.card`. Enterprise Light meets every guard as authored (the shortcut's
   #5C6370 on #D9DFEA is 4.52:1), so the mock comparison is unaffected. Three pairs are painted as authored, unguarded
   (`RunWindowPresentation.authored()`): list details and tags in `text.placeholder` on `surface.card` (the mocks'
   #7A808A, 3.98:1 on white, like today's card details in `text.tertiary`), the matches count in `text.placeholder` on
   `surface.tableHeader`, and a selected row's detail and tag in the selection's supporting ink, `text.onAccent.secondary`
   on the accent (the card's ink; 4.33:1 in Enterprise Light, as the mock paints it). The empty line inside the list box
   is DESCRIPTION text guarded against the dialog surface, not `surface.card` (a residual, recorded). Surfaces and rules
   are fills, not pairs: the title band's `border.window` rule on `surface.header.active` is 1.27:1 in Enterprise Light,
   which a STRUCTURAL guard would repaint away from the mock. `PaintedContrastTest` paints every pair over the theme set
   and the degenerate palettes (Task 4). Cost if wrong: one mapping or one guard.
9. **The Open field shows no placeholder** (the mocks' empty field), its query is in the data face, and the chip sits in
   the box before it; the ▼ opens "All" and each scope, with its shortcut, and choosing one sets the chip. Cost if
   wrong: copy.
10. **Copy.** The help row reads "Type the name of a session, command, file or snippet, and Jasper will open it for you."
    and, with a chip, that scope's description (the fixed string when blank); the detail line reads "{Noun}: {detail}"
    with the noun's first letter capitalised and the row's title when it has no detail, blank with no row or a "More
    in…" row selected; the primary button reads the selected row's verb 0, "Open" enabled for a "More in…" row and
    "Open" disabled with nothing selected; the checkbox reads "Open in a split pane" and its label is a click target.
    Cost if wrong: copy.
11. **The second and third verbs appear in each row's context menu** with action ids `verb:<entry>:<verb>`; choosing one
    selects that row and runs the verb. Cost if wrong: none.
12. **The comparison is a JUnit test with the mock read from the repository** (spec 16.1.4 keeps `pairs.json`
    comparisons opt-in; the user asked for the match to be pinned): `RunWindowRenderTest` renders both mocks' states at
    2× under the Enterprise fixture kit (`KitFixtures.enterprise()`) with its 12-point body and the Enterprise Light
    fixture theme (`KitFixtures.enterpriseLight()`), and checks (a) 35 geometry probes for "Just opened" and 34 for
    "Typing auth", each run identically on the mock and the render, within ±1 device px for bands, boxes and rules and
    ±3 device px for text ink starts, (b) 14 fill colours within ΔE76 3, and (c) luminance-edge agreement of at least
    0.70 per band, computed as `compare_mock.py` does, with the area under the list compared 12 device px lower (Rulings
    5), the count text of B and the scrollbar column masked. The probes and fills of what plan 4b's classic faces and
    plan 5b's Plex draw are measured and printed, and held to the mock once those plans are in the branch (R8, R9). Dark
    renders (Slate Cobalt Dark until 5b ships Enterprise Dark) are written and checked for structure only, as no Dark
    mock shows the window. `pairs.json` gains the four opt-in pairs for `check_pairs.py`. Every comparison runs on the EDT
    inside `KitGallery.installed`, whose context closes when the action returns. Cost if wrong: floors tuned once in Task
    7, recorded.
13. **The matches count is the heading's second title node.** A list heading has a title only (`ListItem`, kept by
    plan 3c), so the "i of n" text is `title().get(1)` (id `entry.N.count`); under `runWindow` the heading's title panel
    lays its first node at the row inset and its second at the band's end. The model is unchanged. Cost if wrong: none.
14. **The title text drags.** The "Run" title and the shortcut are kit labels with tooltips, so a press on them stops
    at the label; `RunWindowView` forwards their mouse events to itself, where plan 8b's drag listens, so the whole title
    row drags (spec 11.2). Cost if wrong: one listener.

## File structure

- `jasper-app/src/main/java/dev/jasper/app/palette/`: `PaletteEntry` (typed headings), new `RunCopy`, new `PaletteView`,
  `CommandPalette` renamed `CardView`, new `StepForm` and `QueryEdits`, new `RunWindowView`, new `PaletteViews`,
  `PaletteController` (`PaletteViews.of`, the host), `package-info.java` (the `presentation` edge).
- `jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java`: the view, `PaletteLook.DEFAULT`.
- `jasper-app/src/main/java/dev/jasper/app/presentation/PaletteLook.java`: `DEFAULT` and `RUN_WINDOW` constants.
- `jasper-ui/src/main/java/dev/jasper/ui/`: `metrics/DesignMetrics.java` and `metrics/Typography.java`,
  `metrics/TypeScale.java` (roles), `icons/IconRegistry.java` (two icons), `swing/KitView.java` (the flag),
  `swing/RunWindowPresentation.java` (new), `swing/Labels.java`, `swing/Stacks.java`, `swing/ItemLists.java`,
  `swing/InteractiveRows.java`, `swing/TextFields.java`, `swing/SurfacePanel.java`; the test fixtures'
  `testsupport/KitFixtures.java` (`enterpriseLight()`, plan 4c's text); the testkit's `FakeKit.java`.
- Frozen tables under `jasper-ui/src/test/resources/dev/jasper/ui/metrics/`: `compact-roles.tsv`, `classic-roles.tsv`,
  `default-type.tsv`, `classic-type.tsv` (rows appended).
- Tests: `palette/RunCopyTest`, `palette/PaletteHeadingsTest`, `palette/RunWindowViewTest`,
  `palette/PaletteViewContractTest`, `palette/RunWindowRenderTest`, `palette/RunWindowFixtures`, `testsupport/MockComparison`
  (jasper-app); `swing/RunWindowPresentationTest`, `swing/RunWindowSurfacesTest`, `metrics/RunWindowRolesTest` (jasper-ui);
  edits to `CommandPaletteTest`, `PaletteTestSupport`, `WindowCommandPaletteTest`, `NewRolesTest`, `TypographyTest`,
  and jasper-app's `appearance/PaintedContrastTest` (the Run window's declared pairs, Task 4).
- `tools/visual/pairs.json` (two pairs), `docs/design/theme-engine/migration.csv`, docs (Task 8).

---
### Task 1: `PaletteView`, and the card as `CardView`

Spec 11.1: `PaletteView` is the card's controller-facing API, and today's card becomes `CardView`, its first
implementation. The step form and the prefix-to-chip edit move into `StepForm` and `QueryEdits` so the Run window
(Task 5) shares them. The controller builds the card with one `PaletteView.Host` of callbacks. A pure refactor: no pixel,
behaviour or copy changes, and the card's tests pass unchanged but for the rename.

**Files:**
- Rename: `jasper-app/src/main/java/dev/jasper/app/palette/CommandPalette.java` to `CardView.java` (then replace it whole, Step 3)
- Create: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteView.java`, `StepForm.java`, `QueryEdits.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteController.java:25,49,80,118,135,411`, `jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java:11,35,62-65,98,140-163,172`
- Modify (tests): `jasper-app/src/test/java/dev/jasper/app/palette/PaletteTestSupport.java` (every `CommandPalette` parameter), `CommandPaletteTest.java` (the type name), `jasper-app/src/test/java/dev/jasper/app/workspace/WindowCommandPaletteTest.java:280,287,288,291,458,466,477`
- Test: `jasper-app/src/test/java/dev/jasper/app/palette/PaletteViewSeamTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (the renamed row; rows for the four new files)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `public interface PaletteView extends AutoCloseable` with the nested `record Host(Consumer<String>
  queryChanged, ObjIntConsumer<PaletteEntry> execute, Function<String, Optional<ScopePrefix>> prefixes,
  Consumer<PaletteScope> chooseScope, Supplier<List<PaletteScope>> scopes, Supplier<String> openerShortcut, Runnable
  cancel, Runnable browse)` (a null scope chooses All) and the methods `JComponent component()`, `boolean modeless()`,
  `void opened()`, `void focusQuery()`, `boolean composing()`, `String queryText()`, `void setQueryText(String)`,
  `void setChip(PaletteScope)`, `void setPlaceholder(String)`, `void setEntries(List<PaletteEntry>, String, String)`,
  `PaletteEntry selectedEntry()`, `String selectedKey()`, `void selectRow(String)`, `void selectRelative(int)`,
  `void executeSelected(int)`, `void setVisibleRows(int)`, `int visibleRows()`, `void showStep(String,
  List<PaletteStep.Field>)`, `void hideStep()`, `boolean stepShowing()`, `void focusStepField(int)`, `Map<String,
  String> stepValues()`, `void setStepError(String)`, `void refreshTheme()`, `void setReveal(double)`, `int
  revealedHeight()`, `void close()`; `public final class CardView extends SurfacePanel implements PaletteView` with
  `CardView(KitContext, boolean macOs, PaletteView.Host)` and today's package-private accessors; `final class StepForm`
  (`StepForm(KitContext)`, `JPanel panel()`, `show`, `hide`, `showing`, `focusField`, `values`, `setError`,
  `preferredHeight`, `applyGeometry` and the accessors the card's tests read); `final class QueryEdits`;
  `PaletteController.view()` (`PaletteView`) and `PaletteController.component()` (`JComponent`);
  `WindowCommandPalette.view()` (`PaletteView`) and `WindowCommandPalette.component()` (`JComponent`).

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/palette/PaletteViewSeamTest.java`:

```java
package dev.jasper.app.palette;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.testsupport.KitTestSupport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 11.1: the controller drives its view only through PaletteView; the card is that view's first implementation. */
class PaletteViewSeamTest {
    @Test void theControllerDrivesTheCardThroughThePaletteView() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new ScopeRegistry(); var history = new CommandHistory()) {
                registry.register(PaletteTestSupport.scope("t.one", null));
                var controller = new PaletteController(KitTestSupport.context(), registry, history, true, () -> { }, () -> { },
                    () -> false, () -> { }, message -> { }, id -> { });
                try {
                    PaletteView view = controller.view();
                    assertThat(view).isInstanceOf(CardView.class);
                    assertThat(view.modeless()).as("the card dismisses as today").isFalse();
                    assertThat(controller.component()).isSameAs(view.component());
                    controller.open("t.one", PaletteTarget.none(), () -> true);
                    assertThat(view.component().isVisible()).isTrue();
                    assertThat(view.selectedKey()).isEqualTo("t.one/alpha");
                    view.selectRelative(1);
                    assertThat(view.selectedKey()).isEqualTo("t.one/beta");
                    controller.dismiss();
                    assertThat(view.component().isVisible()).isFalse();
                } finally { controller.close(); }
            }
        });
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.PaletteViewSeamTest'`
Expected: FAIL: `compileTestJava` reports "cannot find symbol … class PaletteView" and "… view()".

- [ ] **Step 3: The interface, the shared parts and the card**

Run from the worktree root:

```bash
git mv jasper-app/src/main/java/dev/jasper/app/palette/CommandPalette.java jasper-app/src/main/java/dev/jasper/app/palette/CardView.java
```

Create `jasper-app/src/main/java/dev/jasper/app/palette/PaletteView.java`:

```java
package dev.jasper.app.palette;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import javax.swing.JComponent;

/**
 * What the palette's controller drives (Enterprise presentation spec 11.1): the query and chip, the list and its
 * selection, a step's small form and the view's own surface. Two implementations exist, the card ({@link CardView}) and
 * the Run window ({@code RunWindowView}), built by {@code PaletteViews}. EDT only.
 */
public interface PaletteView extends AutoCloseable {
    /**
     * What a view calls back.
     *
     * @param queryChanged the query the person typed, after a scope prefix left it
     * @param execute run an entry with a verb index (0 is Enter's)
     * @param prefixes the scope a typed prefix names
     * @param chooseScope set the chip to a scope, or to none (All) for null
     * @param scopes the scopes in the order the mode list shows them
     * @param openerShortcut the opener's current shortcut text, blank when unbound
     * @param cancel close the palette
     * @param browse run File, Open File…
     */
    record Host(Consumer<String> queryChanged, ObjIntConsumer<PaletteEntry> execute, Function<String, Optional<ScopePrefix>> prefixes,
                Consumer<PaletteScope> chooseScope, Supplier<List<PaletteScope>> scopes, Supplier<String> openerShortcut,
                Runnable cancel, Runnable browse) {
        /** Every callback is required. */
        public Host {
            Objects.requireNonNull(queryChanged); Objects.requireNonNull(execute); Objects.requireNonNull(prefixes);
            Objects.requireNonNull(chooseScope); Objects.requireNonNull(scopes); Objects.requireNonNull(openerShortcut);
            Objects.requireNonNull(cancel); Objects.requireNonNull(browse);
        }
    }

    /** The Swing surface its host mounts. */
    JComponent component();
    /** Whether the view stays open while the person works elsewhere (the Run window); the card dismisses as before. */
    boolean modeless();
    /** The controller opened the view afresh. */
    void opened();
    /** Moves keyboard focus to the query field. */
    void focusQuery();
    /** Whether an input method is composing in the query field. */
    boolean composing();
    /** The query. */
    String queryText();
    /** Replaces the query as if typed; the same text changes nothing. */
    void setQueryText(String text);
    /** Shows {@code scope}'s chip, or none (All) for null. */
    void setChip(PaletteScope scope);
    /** The query field's placeholder. */
    void setPlaceholder(String text);
    /** Replaces the list, selecting {@code selectionKey} when present and otherwise the first selectable entry. */
    void setEntries(List<PaletteEntry> entries, String selectionKey, String empty);
    /** The selected entry, or null. */
    PaletteEntry selectedEntry();
    /** The selected entry's key, or null. */
    String selectedKey();
    /** Selects the first row with {@code rowId}. */
    void selectRow(String rowId);
    /** Moves {@code delta} selectable entries. */
    void selectRelative(int delta);
    /** Runs the selected entry with the view's verb for {@code verb}: 0 is the view's primary verb (spec 11.4). */
    void executeSelected(int verb);
    /** How many rows the list shows before it scrolls. */
    void setVisibleRows(int rows);
    /** How many rows the list shows before it scrolls. */
    int visibleRows();
    /** Shows a step's form in the list's place; its first field takes focus. */
    void showStep(String title, List<PaletteStep.Field> fields);
    /** Leaves a step. */
    void hideStep();
    /** Whether a step is showing. */
    boolean stepShowing();
    /** Moves focus to the next or previous step field, wrapping. */
    void focusStepField(int delta);
    /** The step's values by field name. */
    Map<String, String> stepValues();
    /** Shows a step's error, or none for null. */
    void setStepError(String message);
    /** Follows a theme change. */
    void refreshTheme();
    /** Shows only the top {@code fraction} of the view while it is revealed; 1 when fully shown. */
    void setReveal(double fraction);
    /** The height currently shown. */
    int revealedHeight();
    /** Closes the view's kit views. */
    @Override void close();
}
```

Create `jasper-app/src/main/java/dev/jasper/app/palette/QueryEdits.java`:

```java
package dev.jasper.app.palette;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A typed scope prefix ("history > ls") leaving the query to become the chip, as both palette views apply it. The kit
 * applies the shortened query one event-loop turn after the edit (Swing forbids document writes while it notifies), so a
 * key queued before then reports text that still begins with the prefix; that text is stripped until the field shows
 * the query. Deleting text never makes a chip. EDT only.
 */
final class QueryEdits {
    /** An edit's outcome: the query to keep and the scope its prefix named, if any. */
    record Outcome(String query, Optional<ScopePrefix> prefix) { }

    private final Function<String, Optional<ScopePrefix>> prefixes;
    private String consumedPrefix;

    QueryEdits(Function<String, Optional<ScopePrefix>> prefixes) { this.prefixes = Objects.requireNonNull(prefixes); }

    /** The query after {@code text} replaced {@code previous}, {@code typed} or set by the host. */
    Outcome edited(String previous, String text, boolean typed) {
        if (consumedPrefix != null) {
            if (typed && text.startsWith(consumedPrefix)) text = text.substring(consumedPrefix.length());
            else consumedPrefix = null;
        }
        Optional<ScopePrefix> prefix = text.equals(previous) || removal(previous, text) ? Optional.empty() : prefixes.apply(text);
        if (prefix.isPresent()) consumedPrefix = text.substring(0, text.length() - prefix.get().rest().length());
        return new Outcome(prefix.map(ScopePrefix::rest).orElse(text), prefix);
    }

    /** Whether a consumed prefix may still arrive in typed text. */
    boolean pending() { return consumedPrefix != null; }

    /** After the kit's own deferred update (queued first), forgets the consumed prefix once the field shows the query. */
    void settle(BooleanSupplier closed, Supplier<String> field, Supplier<String> query) {
        java.awt.EventQueue.invokeLater(() -> {
            if (closed.getAsBoolean() || consumedPrefix == null) return;
            if (Objects.equals(field.get(), query.get())) consumedPrefix = null;
        });
    }

    /** Whether {@code next} is {@code previous} with one stretch of it deleted. */
    static boolean removal(String previous, String next) {
        if (next.length() >= previous.length()) return false;
        int common = 0;
        while (common < next.length() && previous.charAt(common) == next.charAt(common)) common++;
        return previous.endsWith(next.substring(common));
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/palette/StepForm.java`:

```java
package dev.jasper.app.palette;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

/**
 * A step's small form, as both palette views show it in their list's place: a title row, one labelled row per field
 * (a fixed label column and an EMBEDDED field, each its own kit view) and a hidden error row. Moved unchanged out of the
 * card (Enterprise presentation plan 8a), so the Run window renders steps exactly as the card does. EDT only.
 */
final class StepForm implements AutoCloseable {
    /** The node id prefix of the step's views. */
    static final String STEP = "step";
    private static final String FIELD = "step.field.";

    private final KitContext kit;
    private final List<KitView> views = new ArrayList<>();
    private final KitView titleView, errorView;
    private final JPanel panel, titleRow, errorRow;
    private final List<Row> rows = new ArrayList<>();
    private String title;
    private List<PaletteStep.Field> fields = List.of();
    private final List<String> texts = new ArrayList<>();
    private String error;
    private int focus = -1;
    private boolean closed;

    StepForm(KitContext kit) {
        this.kit = Objects.requireNonNull(kit);
        titleView = view(titleSpec());
        errorView = view(errorSpec());
        titleRow = dev.jasper.ui.swing.SurfacePanel.row(kit, DesignMetrics.Role.PALETTE_STEP_TITLE_HEIGHT, false);
        titleRow.setLayout(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(titleView, BorderLayout.CENTER);
        errorRow = dev.jasper.ui.swing.SurfacePanel.row(kit, DesignMetrics.Role.PALETTE_STEP_TITLE_HEIGHT, false);
        errorRow.setLayout(new BorderLayout());
        errorRow.setOpaque(false);
        errorRow.add(errorView, BorderLayout.CENTER);
        errorRow.setVisible(false);
        panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setOpaque(false);
    }

    private KitView view(ViewSpec spec) {
        var view = new KitView(kit, spec, this::event);
        views.add(view);
        return view;
    }

    private void event(UiEvent event) {
        if (closed) return;
        if (event instanceof UiEvent.Edited edited && edited.id().startsWith(FIELD)) {
            int index = Integer.parseInt(edited.id().substring(FIELD.length()));
            if (index < texts.size()) texts.set(index, edited.text());
        }
    }

    private static Nodes.Text text(String id, String value, Nodes.TextRole role) { return new Nodes.Text(id, ControlState.READY, value, role); }

    private ViewSpec titleSpec() { return new ViewSpec(text(STEP + ".title", title == null ? "" : title, Nodes.TextRole.CAPTION)); }

    private ViewSpec errorSpec() {
        var shown = new ControlState(error != null, true, false, "");
        return new ViewSpec(new Nodes.Stack(STEP + ".error", shown, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.StatusDot(STEP + ".error.status", ControlState.READY, Nodes.StatusKind.DANGER),
            text(STEP + ".error.text", error == null ? "" : error, Nodes.TextRole.BODY))));
    }

    private static String label(PaletteStep.Field field) { return field.label().isBlank() ? field.name() : field.label(); }

    /** The row's own label column names the field, so the field is EMBEDDED: its label stays its accessible name. */
    private ViewSpec fieldSpec(int index) {
        return new ViewSpec(new Nodes.Field(FIELD + index, ControlState.READY, Nodes.FieldKind.TEXT, texts.get(index), "",
            label(fields.get(index)), "", java.util.Optional.empty(), Nodes.FieldPresentation.EMBEDDED));
    }

    private void render(KitView view, ViewSpec spec) { if (!view.isClosed()) view.update(spec); }

    /** The form's panel, which the owning view shows in its list's place. */
    JPanel panel() { return panel; }

    /** Shows {@code title} over one row per field, prefilled; the first field takes focus. */
    void show(String title, List<PaletteStep.Field> fields) {
        this.title = title;
        this.fields = List.copyOf(fields);
        texts.clear();
        for (PaletteStep.Field field : this.fields) texts.add(field.prefill());
        error = null;
        render(titleView, titleSpec());
        render(errorView, errorSpec());
        errorRow.setVisible(false);
        buildRows();
        focus = -1;
        focusField(1);
    }

    /** Leaves the form. */
    void hide() {
        title = null;
        fields = List.of();
        texts.clear();
        error = null;
        focus = -1;
        clearRows();
        render(titleView, titleSpec());
        render(errorView, errorSpec());
        errorRow.setVisible(false);
    }

    private void buildRows() {
        clearRows();
        panel.add(titleRow);
        for (int i = 0; i < fields.size(); i++) {
            var row = new Row(view(new ViewSpec(text(STEP + ".label." + i, label(fields.get(i)), Nodes.TextRole.CAPTION))), view(fieldSpec(i)));
            rows.add(row);
            panel.add(row.panel);
        }
        panel.add(errorRow);
        panel.add(javax.swing.Box.createVerticalGlue());
        applyGeometry();
    }

    private void clearRows() {
        for (Row row : rows) {
            row.label.close(); row.field.close();
            views.remove(row.label); views.remove(row.field);
        }
        rows.clear();
        panel.removeAll();
    }

    /** The former step geometry: 4/12 row padding, a fixed label column and a gap before the field. */
    void applyGeometry() {
        int side = com.formdev.flatlaf.util.UIScale.scale(kit.metrics().value(DesignMetrics.Role.PALETTE_STEP_SIDE_PADDING));
        titleRow.setBorder(new EmptyBorder(0, side, 0, side));
        errorRow.setBorder(new EmptyBorder(0, side, 0, side));
        panel.revalidate();
    }

    /** The form's height: its title row, its rows and the error row while an error shows. */
    int preferredHeight() {
        return titleRow.getPreferredSize().height + rows.stream().mapToInt(row -> row.panel.getPreferredSize().height).sum()
            + (error != null ? errorRow.getPreferredSize().height : 0);
    }

    boolean showing() { return focus >= 0; }
    int focusIndex() { return focus; }
    String titleText() { return title; }
    String errorText() { return error; }
    int fieldCount() { return fields.size(); }

    /** Moves focus to the next (or previous) field, wrapping, and selects its text for replacement. */
    void focusField(int delta) {
        if (fields.isEmpty()) return;
        focus = Math.floorMod(focus + delta, fields.size());
        var view = rows.get(focus).field;
        if (view.isClosed()) return;
        view.focus(FIELD + focus);
        var input = input(focus);
        if (input != null) input.selectAll();
    }

    /** The mounted kit input of the {@code index}th field (kit components are named after their node). */
    JTextComponent input(int index) { return CardView.find(rows.get(index).field, FIELD + index + ".input", JTextComponent.class); }

    Map<String, String> values() {
        var values = new LinkedHashMap<String, String>();
        for (int i = 0; i < fields.size(); i++) values.put(fields.get(i).name(), texts.get(i));
        return values;
    }

    void setError(String message) {
        error = message;
        render(errorView, errorSpec());
        errorRow.setVisible(message != null);
    }

    JPanel titleRow() { return titleRow; }
    JPanel errorRow() { return errorRow; }
    JPanel row(int index) { return rows.get(index).panel; }
    JComponent labelView(int index) { return rows.get(index).label; }
    KitView fieldView(int index) { return rows.get(index).field; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        views.forEach(KitView::close);
    }

    /** Retained views, laid out by the module's responsive native label/control composition. */
    private final class Row {
        final KitView label, field;
        final JPanel panel;
        Row(KitView label, KitView field) {
            this.label = label; this.field = field;
            panel = dev.jasper.ui.swing.SurfacePanel.labelledRow(kit, label, field);
        }
    }
}
```

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/palette/CardView.java` with:

```java
package dev.jasper.app.palette;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

/**
 * Jasper's palette card: a query row with an optional scope chip, a one-line result list with section headings (or the
 * empty line, or a step's small form), and a hint bar with the selected row's detail and, when there are several, its
 * scope's verbs. The module supplies its shared surface, outline and measured rows on the chrome {@link KitContext}.
 * Every control remains a retained kit view. The query field keeps focus; the host moves the selection and routes the
 * keys. The palette's first {@link PaletteView} (Enterprise presentation spec 11.1). EDT only.
 */
public final class CardView extends dev.jasper.ui.swing.SurfacePanel implements PaletteView {
    static final String QUERY = "query", CHIP = "chip", RESULTS = "results", HINT = "hint", EMPTY = "empty", STEP = StepForm.STEP,
        RUN = "run", VERB = "verb:";

    private final KitContext kit;
    private final dev.jasper.app.platform.PresentationIcons presentationIcons = new dev.jasper.app.platform.PresentationIcons();
    private final boolean macOs;
    private final Consumer<String> queryChanged;
    private final ObjIntConsumer<PaletteEntry> execute;
    private final Consumer<PaletteScope> chipTyped;
    private final QueryEdits edits;
    private final List<KitView> views = new ArrayList<>();
    private final KitView chipView, queryView, resultsView, emptyView, hintView;
    private final JPanel fieldRow, hintBar, cards;
    private final StepForm step;
    private final CardLayout cardLayout = new CardLayout();
    private final AutoCloseable themeSubscription;
    private int visibleRows = dev.jasper.app.config.PaletteSettings.DEFAULT_VISIBLE_ROWS;

    private String query = "", placeholder = "Search everything", chip, emptyText = "";
    private List<PaletteEntry> entries = List.of();
    private int selected = -1;
    private long resultCount, revision;
    private String listCard = EMPTY;
    private boolean closed;
    /** The shown fraction of the card's height while it drips open; 1 when fully open. */
    private double reveal = 1;

    /** A card calling back {@code host}; the card uses its query, execute, prefix and scope callbacks. */
    CardView(KitContext kit, boolean macOs, PaletteView.Host host) {
        super(kit, dev.jasper.ui.swing.SurfacePanel.Kind.POPUP);
        this.kit = presentationIcons.context(Objects.requireNonNull(kit));
        kit = this.kit;
        this.macOs = macOs;
        this.queryChanged = host.queryChanged();
        this.execute = host.execute();
        this.edits = new QueryEdits(host.prefixes());
        this.chipTyped = host.chooseScope();
        setOpaque(false);
        // AWT retargets a press to the nearest ancestor that has mouse listeners. Without one here, a
        // press on the chip, the hint bar's detail text or the field row's padding would bubble past
        // the palette to the overlay behind it, which dismisses on any press it receives.
        var swallowClicks = new MouseAdapter() { };
        addMouseListener(swallowClicks);
        addMouseMotionListener(swallowClicks);
        // Focus cycles within the palette and always lands on the query; the host routes Tab between a step's fields.
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override public Component getComponentAfter(Container root, Component current) { return queryInput(); }
            @Override public Component getComponentBefore(Container root, Component current) { return queryInput(); }
            @Override public Component getFirstComponent(Container root) { return queryInput(); }
            @Override public Component getLastComponent(Container root) { return queryInput(); }
            @Override public Component getDefaultComponent(Container root) { return queryInput(); }
        });

        chipView = view(chipSpec());
        chipView.setVisible(false);
        queryView = view(querySpec());
        fieldRow = dev.jasper.ui.swing.SurfacePanel.row(kit, DesignMetrics.Role.PALETTE_FIELD_HEIGHT, false);
        fieldRow.setLayout(new BorderLayout());
        fieldRow.setOpaque(false);
        fieldRow.add(chipView, BorderLayout.WEST);
        fieldRow.add(queryView, BorderLayout.CENTER);
        add(fieldRow, BorderLayout.NORTH);

        resultsView = view(resultsSpec());
        emptyView = view(emptySpec());
        var empty = new JPanel(new GridBagLayout());
        empty.setOpaque(false);
        empty.add(emptyView);
        step = new StepForm(kit);
        cards = new JPanel(cardLayout);
        cards.setOpaque(false);
        cards.add(resultsView, RESULTS);
        cards.add(empty, EMPTY);
        cards.add(step.panel(), STEP);
        cardLayout.show(cards, listCard);
        add(cards, BorderLayout.CENTER);

        hintView = view(hintSpec());
        hintBar = dev.jasper.ui.swing.SurfacePanel.row(kit, DesignMetrics.Role.PALETTE_HINT_HEIGHT, true, dev.jasper.ui.theme.SemanticColor.BORDER_MENU);
        hintView.setHintPresentation(HINT, true);
        hintBar.add(hintView, BorderLayout.CENTER);
        add(hintBar, BorderLayout.SOUTH);

        themeSubscription = kit.binding().subscribe(pair -> refreshTheme());
        refreshTheme();
        setChip(null, null);
        updateHint();
    }

    /** For the card's own tests: a card whose scope list, opener, cancel and Browse… callbacks do nothing. */
    CardView(KitContext kit, boolean macOs, Consumer<String> queryChanged, ObjIntConsumer<PaletteEntry> execute,
             Function<String, Optional<ScopePrefix>> prefixes, Consumer<PaletteScope> chipTyped) {
        this(kit, macOs, new PaletteView.Host(queryChanged, execute, prefixes, chipTyped, List::of, () -> "", () -> { }, () -> { }));
    }

    @Override public JComponent component() { return this; }
    @Override public boolean modeless() { return false; }
    @Override public void opened() { }

    /**
     * Shows only the top {@code fraction} of the card, 0-1: its fill, border, corners and hit area follow that edge while
     * the content stays laid out at full height, so an opening reveal uncovers the card without moving its rows.
     */
    @Override public void setReveal(double fraction) {
        double clamped = Math.max(0, Math.min(1, fraction));
        if (clamped == reveal) return;
        reveal = clamped;
        repaint();
    }

    /** The height currently shown; the full height unless a reveal is under way. */
    @Override public int revealedHeight() { return reveal >= 1 ? getHeight() : (int) Math.ceil(getHeight() * reveal); }

    @Override protected int surfaceHeight() { return revealedHeight(); }

    private KitView view(ViewSpec spec) {
        var view = new KitView(kit, spec, this::event);
        views.add(view);
        return view;
    }

    private int metric(DesignMetrics.Role role) { return kit.metrics().value(role); }
    private int scaled(DesignMetrics.Role role) { return UIScale.scale(metric(role)); }
    private Color color(SemanticColor role) { return kit.theme().semantic().color(role); }

    // ---- Descriptors ------------------------------------------------------------------------------------------------

    private ViewSpec chipSpec() {
        return new ViewSpec(new Nodes.Chip(CHIP, ControlState.READY, chip == null ? "All" : chip, Nodes.Hue.BLUE));
    }

    private ViewSpec querySpec() {
        String name = chip == null ? "All" : chip;
        // One surface throughout: the query shows the card inside its input outline, as before.
        return new ViewSpec(new Nodes.Field(QUERY, ControlState.READY, Nodes.FieldKind.SEARCH, query, placeholder,
            "Search " + name.toLowerCase(Locale.ROOT), "", Optional.empty(), Nodes.FieldPresentation.TRANSPARENT));
    }

    private void renderResults() {
        presentationIcons.reconcile(() -> render(resultsView, resultsSpec()));
    }

    private ViewSpec resultsSpec() {
        var items = new ArrayList<ListItem>();
        for (int i = 0; i < entries.size(); i++) items.add(item(i, entries.get(i)));
        Set<String> selection = selected < 0 ? Set.of() : Set.of(id(selected));
        return new ViewSpec(new Nodes.ItemList(RESULTS, ControlState.READY, Nodes.ListKind.RESULTS, items, selection, RUN, List.of(),
            Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(visibleRows)));
    }

    private static String id(int index) { return "entry." + index; }

    private ListItem item(int index, PaletteEntry entry) {
        String id = id(index);
        return switch (entry) {
            case PaletteEntry.Header header -> new ListItem(id, Optional.empty(),
                List.of(text(id + ".title", header.label(), Nodes.TextRole.CAPTION)), List.of(), List.of(), true);
            case PaletteEntry.Item item -> {
                PaletteRow row = item.row();
                // Preserve the semantic name and scoped monochrome artwork in this receiving kit context.
                Optional<Nodes.Node> icon = Optional.ofNullable(row.icon()).flatMap(value -> presentationIcons.name(value))
                    .map(name -> new Nodes.Icon(id + ".icon", ControlState.READY, name, Nodes.Hue.NONE, Nodes.IconSize.RAIL, row.title()));
                Nodes.TextRole role = !row.enabled() ? Nodes.TextRole.DESCRIPTION
                    : item.scope().monospaceRows() ? Nodes.TextRole.MONO : Nodes.TextRole.BODY;
                yield new ListItem(id, icon, List.of(text(id + ".title", row.title(), role)),
                    row.detail() == null ? List.of() : List.of(text(id + ".detail", row.detail(), Nodes.TextRole.BODY)),
                    row.tag() == null ? List.of() : List.of(text(id + ".tag", row.tag(), Nodes.TextRole.CAPTION)));
            }
            case PaletteEntry.More more -> new ListItem(id, Optional.empty(),
                List.of(text(id + ".title", "More in " + more.scope().label() + "…", Nodes.TextRole.DESCRIPTION)), List.of(), List.of());
        };
    }

    private static Nodes.Text text(String id, String value, Nodes.TextRole role) {
        return new Nodes.Text(id, ControlState.READY, value, role);
    }

    private ViewSpec emptySpec() {
        return new ViewSpec(text(EMPTY, emptyText, Nodes.TextRole.DESCRIPTION));
    }

    /** The selected row's detail (or tag) and its scope's second and third verbs, as links. */
    private ViewSpec hintSpec() {
        var children = new ArrayList<Nodes.Node>();
        children.add(text(HINT + ".text", hintText(), Nodes.TextRole.CAPTION));
        if (selectedEntry() instanceof PaletteEntry.Item item) {
            // A lone verb is Enter's and goes unlisted; with more, every verb is listed, Enter's first.
            List<PaletteVerb> verbs = item.scope().verbs();
            for (int verb = 0; verbs.size() > 1 && verb < Math.min(3, verbs.size()); verb++)
                children.add(new Nodes.Button(VERB + verb, ControlState.READY, verbs.get(verb).label() + " " + keys(verb), "",
                    Nodes.ButtonKind.LINK, VERB + verb, "", Nodes.ButtonPresentation.EMBEDDED));
        }
        return new ViewSpec(new Nodes.Stack(HINT, ControlState.READY, Nodes.LayoutRole.NAVIGATION, children));
    }

    private void render(KitView view, ViewSpec spec) { if (!view.isClosed()) view.update(spec); }

    // ---- Events -----------------------------------------------------------------------------------------------------

    private void event(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(QUERY) -> edited(edited.text(), true);
            case UiEvent.Selected chosen when chosen.id().equals(RESULTS) ->
                chosen.ids().stream().findFirst().ifPresent(id -> select(Integer.parseInt(id.substring("entry.".length()))));
            case UiEvent.Action action when action.id().equals(RUN) -> executeSelected(0);
            case UiEvent.Action action when action.id().startsWith(VERB) -> executeSelected(Integer.parseInt(action.id().substring(VERB.length())));
            default -> { }
        }
    }

    /**
     * The query changed, typed ({@code typed}) or set by the host. A scope prefix typed with no chip ("history > ls")
     * leaves the query and becomes the chip, as the field's prefix filter did; deleting text never makes a chip.
     */
    private void edited(String text, boolean typed) {
        var outcome = edits.edited(query, text, typed);
        query = outcome.query();
        render(queryView, querySpec());
        if (edits.pending()) edits.settle(() -> closed, () -> queryInput() instanceof JTextComponent input ? input.getText() : null, () -> query);
        queryChanged.accept(query);
        outcome.prefix().ifPresent(found -> chipTyped.accept(found.scope()));
    }

    /** Whether {@code next} is {@code previous} with one stretch of it deleted. */
    static boolean removal(String previous, String next) { return QueryEdits.removal(previous, next); }

    // ---- Query and chip ---------------------------------------------------------------------------------------------

    /** Moves keyboard focus to the query field. */
    @Override public void focusQuery() { if (!queryView.isClosed()) queryView.focus(QUERY); }

    @Override public String queryText() { return query; }

    /** The query view's mounted text input, the palette's one focus target. */
    private Component queryInput() { return first(queryView, JTextComponent.class); }

    private static <T extends Component> T first(Component component, Class<T> type) {
        if (type.isInstance(component)) return type.cast(component);
        if (component instanceof Container container) for (Component child : container.getComponents()) {
            T found = first(child, type);
            if (found != null) return found;
        }
        return null;
    }

    /** Replaces the query as if typed (a typed scope prefix still becomes the chip); the same text changes nothing. */
    @Override public void setQueryText(String text) { if (!Objects.requireNonNull(text).equals(query)) edited(text, false); }

    @Override public void setChip(PaletteScope scope) { setChip(scope == null ? null : scope.label(), null); }

    /** Shows {@code label}'s chip, or none for null; the field and list are named after it. The icon stays off the chip. */
    void setChip(String label, Icon ignoredIcon) {
        chip = label;
        render(chipView, chipSpec());
        chipView.setVisible(label != null);
        chipView.getAccessibleContext().setAccessibleName(label == null ? null : "Scope: " + label);
        render(queryView, querySpec());
        resultsView.getAccessibleContext().setAccessibleName(label == null ? "All" : label);
        fieldRow.revalidate();
        fieldRow.repaint();
    }

    /** The chip's label; null while no chip is set. */
    String chipText() { return chip; }
    @Override public void setPlaceholder(String text) { placeholder = text == null ? "" : text; render(queryView, querySpec()); }
    String placeholder() { return placeholder; }
    @Override public boolean composing() { return !queryView.isClosed() && queryView.composing(QUERY); }

    // ---- Results ----------------------------------------------------------------------------------------------------

    /** Replaces the list, selecting {@code selectionKey} when present and otherwise the first selectable entry. */
    @Override public void setEntries(List<PaletteEntry> next, String selectionKey, String empty) {
        long rows = next.stream().filter(entry -> entry instanceof PaletteEntry.Item).count();
        if (rows > PaletteResults.MAX_ROWS) throw new IllegalArgumentException("Too many palette results");
        int chosen = -1, first = -1;
        for (int i = 0; i < next.size(); i++) {
            PaletteEntry entry = next.get(i);
            if (entry.selectable() && first < 0) first = i;
            if (selectionKey != null && selectionKey.equals(entry.key())) chosen = i;
        }
        entries = List.copyOf(next);
        selected = chosen >= 0 ? chosen : first;
        emptyText = empty == null ? "" : empty;
        resultCount = rows;
        revision++;
        listCard = first < 0 ? EMPTY : RESULTS;
        if (!stepShowing()) cardLayout.show(cards, listCard);
        render(emptyView, emptySpec());
        renderResults();
        updateHint();
        revalidate();
        repaint();
    }

    @Override public PaletteEntry selectedEntry() { return selected < 0 || selected >= entries.size() ? null : entries.get(selected); }

    @Override public String selectedKey() {
        PaletteEntry entry = selectedEntry();
        return entry == null ? null : entry.key();
    }

    /** Selects the first row with {@code rowId}, whichever scope it came from. */
    @Override public void selectRow(String rowId) {
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i) instanceof PaletteEntry.Item item && item.row().id().equals(rowId)) { select(i); return; }
    }

    /** Moves {@code delta} selectable entries, skipping headings and stopping at either end. */
    @Override public void selectRelative(int delta) {
        if (selected < 0 || delta == 0) return;
        int direction = Integer.signum(delta), remaining = Math.abs(delta), target = selected;
        for (int i = selected + direction; remaining > 0 && i >= 0 && i < entries.size(); i += direction)
            if (entries.get(i).selectable()) { target = i; remaining--; }
        select(target);
    }

    private void select(int index) {
        if (index < 0 || index >= entries.size() || !entries.get(index).selectable()) return;
        selected = index;
        renderResults();
        updateHint();
    }

    void executeSelected() { executeSelected(0); }

    /** The card's primary verb is verb 0 (spec 11.4). */
    @Override public void executeSelected(int verb) {
        PaletteEntry entry = selectedEntry();
        if (entry != null) execute.accept(entry, verb);
    }

    int itemHeight() { return dev.jasper.ui.swing.ResultMetrics.height(kit, false); }

    /** How many result rows the list shows before it scrolls; section headings among them add their own height. */
    @Override public void setVisibleRows(int rows) { visibleRows = rows; renderResults(); revalidate(); }
    @Override public int visibleRows() { return visibleRows; }

    String hintText() {
        return switch (selectedEntry()) {
            case PaletteEntry.Item item -> item.row().detail() != null ? item.row().detail() : item.row().tag() != null ? item.row().tag() : "";
            case PaletteEntry.More more -> "Show every match in " + more.scope().label();
            case null, default -> "";
        };
    }

    List<String> hintActionTexts() {
        var texts = new ArrayList<String>();
        if (hintView.spec().root() instanceof Nodes.Stack stack)
            for (Nodes.Node child : stack.children()) if (child instanceof Nodes.Button button) texts.add(button.label());
        return texts;
    }

    /** As a click on the {@code index}th hint action. */
    void clickHintAction(int index) { executeSelected(index); }

    private void updateHint() {
        String verbsDescription = "";
        if (selectedEntry() instanceof PaletteEntry.Item item) verbsDescription = verbsDescription(item.scope().verbs());
        else if (selectedEntry() instanceof PaletteEntry.More more) verbsDescription = keys(0) + " Show every match in " + more.scope().label();
        resultsView.getAccessibleContext().setAccessibleDescription(
            resultCount + " results" + (verbsDescription.isEmpty() ? "" : "; " + verbsDescription));
        render(hintView, hintSpec());
        hintBar.revalidate();
        hintBar.repaint();
    }

    /** Every verb, primary through third, in the base card's format: {@code key label  key label}. */
    private String verbsDescription(List<PaletteVerb> verbs) {
        if (verbs.size() < 2) return "";
        var text = new StringBuilder();
        for (int verb = 0; verb < Math.min(3, verbs.size()); verb++) {
            if (verb > 0) text.append("  ");
            text.append(keys(verb)).append(' ').append(verbs.get(verb).label());
        }
        return text.toString();
    }

    private String keys(int verb) {
        return (macOs ? new String[]{"⏎", "⌘⏎", "⇧⏎"} : new String[]{"Enter", "Ctrl+Enter", "Shift+Enter"})[verb];
    }

    // ---- Step -------------------------------------------------------------------------------------------------------

    /** Shows a form instead of the list; the chip and query stay and the hint bar hides. The first field takes focus. */
    @Override public void showStep(String title, List<PaletteStep.Field> fields) {
        step.show(title, fields);
        cardLayout.show(cards, STEP);
        hintBar.setVisible(false);
        revalidate(); repaint();
    }

    @Override public void hideStep() {
        step.hide();
        hintBar.setVisible(true);
        cardLayout.show(cards, listCard);
        revalidate(); repaint();
    }

    @Override public boolean stepShowing() { return step.showing(); }
    int stepFocusIndex() { return step.focusIndex(); }
    String stepTitleText() { return step.titleText(); }
    /** The step's error; null while none shows. */
    String stepErrorText() { return step.errorText(); }
    int stepFieldCount() { return step.fieldCount(); }
    @Override public void focusStepField(int delta) { step.focusField(delta); }
    JTextComponent stepInput(int index) { return step.input(index); }
    @Override public Map<String, String> stepValues() { return step.values(); }
    @Override public void setStepError(String message) { step.setError(message); revalidate(); repaint(); }

    // ---- Shell ------------------------------------------------------------------------------------------------------

    /** The kit views follow the theme themselves; the shell repaints its surface, outline and rule and re-measures. */
    @Override public void refreshTheme() {
        hintBar.setBorder(new EmptyBorder(scaled(DesignMetrics.Role.HAIRLINE), scaled(DesignMetrics.Role.PALETTE_HINT_SIDE_PADDING),
            0, scaled(DesignMetrics.Role.PALETTE_HINT_SIDE_PADDING)));
        int vertical = scaled(DesignMetrics.Role.PALETTE_FIELD_VERTICAL_PADDING), side = scaled(DesignMetrics.Role.PALETTE_FIELD_SIDE_PADDING);
        fieldRow.setBorder(new EmptyBorder(vertical, side, vertical, side));
        ((BorderLayout) fieldRow.getLayout()).setHgap(scaled(DesignMetrics.Role.PALETTE_CHIP_GAP));
        step.applyGeometry();
        revalidate();
        repaint();
    }

    @Override public Dimension getPreferredSize() {
        Insets insets = getInsets();
        int height = fieldRow.getPreferredSize().height + (stepShowing() ? step.preferredHeight()
            : listHeight() + hintBar.getPreferredSize().height);
        return new Dimension(scaled(DesignMetrics.Role.PALETTE_WIDTH), height + insets.top + insets.bottom);
    }

    /** Tall enough for the first {@link #visibleRows} rows and the headings above them; the rest scroll. */
    private int listHeight() {
        int row = itemHeight(), heading = dev.jasper.ui.swing.ResultMetrics.height(kit, true);
        if (EMPTY.equals(listCard)) return row;
        int height = 0;
        for (int i = 0, rows = 0; i < entries.size() && rows < visibleRows; i++) {
            boolean header = entries.get(i) instanceof PaletteEntry.Header;
            height += header ? heading : row;
            if (!header) rows++;
        }
        return height;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { themeSubscription.close(); } catch (Exception ignored) { /* closing a subscription has nothing to report */ }
        views.forEach(KitView::close);
        step.close();
        presentationIcons.close();
    }

    // ---- Package-private access for headless tests ------------------------------------------------------------------

    KitView queryView() { return queryView; }
    KitView chipView() { return chipView; }
    KitView resultsView() { return resultsView; }
    KitView hintView() { return hintView; }
    JPanel stepPanel() { return step.panel(); }
    JPanel stepTitleRow() { return step.titleRow(); }
    JPanel stepErrorRow() { return step.errorRow(); }
    JPanel stepRow(int index) { return step.row(index); }
    JComponent stepLabelView(int index) { return step.labelView(index); }
    KitView stepFieldView(int index) { return step.fieldView(index); }
    KitView emptyView() { return emptyView; }
    JPanel fieldRow() { return fieldRow; }
    JPanel hintBar() { return hintBar; }
    List<PaletteEntry> entries() { return entries; }
    int selectedIndex() { return selected; }
    /** How many times the host replaced the list. */
    long revision() { return revision; }
    KitContext kit() { return kit; }

    static <T extends Component> T find(Component component, String name, Class<T> type) {
        if (type.isInstance(component) && name.equals(component.getName())) return type.cast(component);
        if (component instanceof Container container) for (Component child : container.getComponents()) {
            T found = find(child, name, type);
            if (found != null) return found;
        }
        return null;
    }
}
```

The card's `showStep` now leaves the reset and first focus to `StepForm.show`, which runs them in the same order as before
(`stepFocus = -1`, then `focusStepField(1)`). Then point the controller, the window adapter and the tests at the view:

```bash
python3 -I - <<'PY'
"""Plan 8a Task 1 step 3: the controller builds a CardView through PaletteView.Host; the window adapter and tests follow."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/palette/"
edit(P + "PaletteController.java", [
    ("    private final CommandPalette palette;\n", "    private final PaletteView palette;\n", 1),
    ("        palette = new CommandPalette(kit, macOs, this::queryChanged, this::execute, this::prefix, scope -> selectTab(scope.id()));\n",
     "        palette = new CardView(kit, macOs, new PaletteView.Host(this::queryChanged, this::execute, this::prefix,\n"
     "            scope -> selectTab(scope == null ? PaletteScope.ALL_ID : scope.id()), this::modeScopes, () -> \"\", this::dismiss, () -> { }));\n", 1),
    ("            palette.setVisible(true);\n", "            palette.component().setVisible(true);\n            palette.opened();\n", 1),
    ("        palette.setChip(active == null ? null : active.label(), active == null ? null : active.icon());\n",
     "        palette.setChip(active);\n", 1),
    ("    public CommandPalette component() { return palette; }\n",
     "    /** The view's Swing surface. */\n    public javax.swing.JComponent component() { return palette.component(); }\n"
     "    /** The view this controller drives. */\n    public PaletteView view() { return palette; }\n", 1),
    ("        palette.hideStep(); palette.setVisible(false);\n", "        palette.hideStep(); palette.component().setVisible(false);\n", 1)])
W = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java"
edit(W, [
    ("import dev.jasper.app.palette.CommandPalette;\n", "import dev.jasper.app.palette.PaletteView;\n", 1),
    ("    private final CommandPalette palette;\n", "    private final PaletteView view;\n    private final JComponent palette;\n", 1),
    ("        palette = controller.component();\n", "        view = controller.view();\n        palette = view.component();\n", 1),
    ("                palette.focusQuery();\n", "                view.focusQuery();\n", 1),
    ("        palette.setReveal(0); overlay.repaint();\n", "        view.setReveal(0); overlay.repaint();\n", 1),
    ("        palette.setReveal(drip(elapsed)); overlay.repaint();\n", "        view.setReveal(drip(elapsed)); overlay.repaint();\n", 1),
    ("        palette.setReveal(1); overlay.repaint();\n", "        view.setReveal(1); overlay.repaint();\n", 1),
    ("    CommandPalette component() { return controller.component(); }\n",
     "    JComponent component() { return palette; }\n    PaletteView view() { return view; }\n", 1),
    ("            card.height = palette.revealedHeight();\n", "            card.height = view.revealedHeight();\n", 1)])
T = "jasper-app/src/test/java/dev/jasper/app/palette/"
path = pathlib.Path(T + "PaletteTestSupport.java"); text = path.read_text(encoding="utf-8")
count = text.count("(CommandPalette palette")
text = text.replace("(CommandPalette palette", "(JComponent palette")
text = re.sub(r"\bpalette\.(?=[A-Za-z_]+\()", "card(palette).", text)
text = text.replace("CommandPalette.", "CardView.")
text = text.replace("    private PaletteTestSupport() {}\n",
    "    private PaletteTestSupport() {}\n\n    /** {@code palette} as the card it is in the default look. */\n"
    "    static CardView card(JComponent palette) { return (CardView) palette; }\n", 1)
if "CommandPalette" in text: raise SystemExit("PaletteTestSupport still names CommandPalette")
path.write_text(text, encoding="utf-8"); print("PaletteTestSupport parameters:", count)
for name in ("CommandPaletteTest.java", "PaletteHeadingsTest.java"):
    path = pathlib.Path(T + name)
    if path.exists(): path.write_text(re.sub(r"\bCommandPalette\b", "CardView", path.read_text(encoding="utf-8")), encoding="utf-8")
edit("jasper-app/src/test/java/dev/jasper/app/workspace/WindowCommandPaletteTest.java", [
    ("palette.component().revealedHeight()", "palette.view().revealedHeight()", 3),
    # theCardDripsOpenFastThenSlow... holds `var card = palette.component();`, now a JComponent.
    ("card.revealedHeight()", "palette.view().revealedHeight()", 4)])
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
old = "jasper-app/src/main/java/dev/jasper/app/palette/CommandPalette.java,app.palette,CommandPalette,"
if text.count(old) != 1: raise SystemExit("migration.csv: the CommandPalette row moved")
text = text.replace(old, "jasper-app/src/main/java/dev/jasper/app/palette/CardView.java,app.palette,CardView,")
rows = [
    "jasper-app/src/main/java/dev/jasper/app/palette/PaletteView.java,app.palette,PaletteView,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/palette/PaletteViewSeamTest.java,New in Enterprise plan 8a Task 1; the palette view seam (spec 11.1); names JComponent only",
    "jasper-app/src/main/java/dev/jasper/app/palette/StepForm.java,app.palette,StepForm,standard-ui,8,8,"
    "jasper-app/src/test/java/dev/jasper/app/palette/CommandPaletteTest.java,New in Enterprise plan 8a Task 1; the card's step form moved unchanged; layout-only shell rows hosting kit views",
    "jasper-app/src/main/java/dev/jasper/app/palette/QueryEdits.java,app.palette,QueryEdits,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/palette/CommandPaletteTest.java,New in Enterprise plan 8a Task 1; the card's prefix-to-chip edit moved unchanged; no Swing",
    "jasper-app/src/test/java/dev/jasper/app/palette/PaletteViewSeamTest.java,app.palette,PaletteViewSeamTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/palette/PaletteViewSeamTest.java,Test/fixture ownership; New in Enterprise plan 8a Task 1"]
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
```

Expected: the script prints `PaletteTestSupport parameters: 35` and stops on no anchor. A `standard-ui` ledger row needs numeric migration and composition task fields (`ThemeMigrationCoverageTest`
matches `[0-9]+(;[0-9]+)*`), so `StepForm` (and Task 5's `RunWindowView`) carry `8,8` for this plan's row 8; the
nonvisual helpers keep `none,none`. The test sources still name `CommandPaletteTest` (the class keeps its name, Rulings 2).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.workspace.*Palette*' --tests 'dev.jasper.app.workspace.ShortcutEditorIntegrationTest' --tests 'dev.jasper.app.workspace.WindowContributionsTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture`
Expected: PASS. `PaletteViewSeamTest` passes; every card, palette-scope, shortcut and window-palette test passes
unchanged (the rename and the support's parameter type aside). If `ThemeMigrationCoverageTest` names another file, add
its row in the same style.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src/main/java/dev/jasper/app/palette jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java \
  jasper-app/src/test/java/dev/jasper/app/palette jasper-app/src/test/java/dev/jasper/app/workspace/WindowCommandPaletteTest.java \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Run window: the palette view seam, and the card as CardView

PaletteView is the card's controller-facing API; today's card becomes
CardView, its first implementation, and the controller builds it with one
PaletteView.Host of callbacks. The step form and the prefix-to-chip edit
move unchanged into StepForm and QueryEdits so the Run window can share
them. No behaviour or pixel changes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: Typed headings and the Run window's copy

`PaletteEntry.Header` gains `Kind` (spec 11.3): RECENT, USED, SUGGESTED, SECTION and MATCHES. The controller types the
four headings it makes; the card keeps its strings. The pure `RunCopy` holds the Run window's copy: its headings, the
item noun capitalised, the detail line, the primary label and "i of n".

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteEntry.java:7-10`, `jasper-app/src/main/java/dev/jasper/app/palette/PaletteController.java:215-216,348,370`
- Create: `jasper-app/src/main/java/dev/jasper/app/palette/RunCopy.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/palette/PaletteHeadingsTest.java`, `jasper-app/src/test/java/dev/jasper/app/palette/RunCopyTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (a row for `PaletteHeadingsTest`, which `ThemeMigrationCoverageTest` names: it uses Swing; `RunCopy` and `RunCopyTest` need none)

**Interfaces:**
- Consumes: Task 1's `PaletteController.view()`, `CardView.entries()`, `PaletteView.setQueryText`; 3d's
  `PaletteScope.itemNoun()`, `PaletteVerb.role()`; 3e's `RECENT_ROWS = 3`.
- Produces: `record PaletteEntry.Header(PaletteEntry.Header.Kind kind, String label)` with `enum Kind { RECENT, USED,
  SUGGESTED, SECTION, MATCHES }` and the convenience `Header(String label)` (SECTION); `final class RunCopy` with
  `static final String HELP`, `static String heading(PaletteEntry.Header)`, `static String position(List<PaletteEntry>,
  int selected)`, `static String noun(PaletteScope)`, `static String detailLabel(PaletteEntry)`, `static String
  detailValue(PaletteEntry)`, `static String primaryLabel(PaletteEntry)`, `static boolean primaryEnabled(PaletteEntry)`,
  `static int splitVerb(PaletteScope)` (-1 for none).

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/palette/PaletteHeadingsTest.java`:

```java
package dev.jasper.app.palette;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.testsupport.KitTestSupport;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 11.3: every heading the controller makes says which kind it is; the card keeps its strings. */
class PaletteHeadingsTest {
    static PaletteScope scope(String id, String label, String section, String... rows) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public String placeholder() { return "Search " + label; }
            @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("run", "Run")); }
            @Override public PaletteResults search(String query, PaletteContext context) {
                var found = new ArrayList<PaletteRow>();
                for (String row : rows) if (row.contains(query)) found.add(PaletteRow.of(row, row));
                return new PaletteResults(found.subList(0, Math.min(found.size(), context.maxResults())), section, null);
            }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    static List<String> headings(PaletteController controller) {
        var texts = new ArrayList<String>();
        for (PaletteEntry entry : ((CardView) controller.view()).entries())
            if (entry instanceof PaletteEntry.Header header) texts.add(header.kind() + " " + header.label());
        return texts;
    }

    @Test void theControllerTypesEveryHeadingAndKeepsTheCardsWords() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new ScopeRegistry(); var history = new CommandHistory()) {
                registry.register(scope("t.alpha", "Alpha", "Recent", "a1", "a2", "a3", "a4", "a5"));
                registry.register(scope("t.beta", "Beta", null, "b1"));
                long now = System.currentTimeMillis();
                for (int i = 1; i <= 4; i++) history.recordUse("t.alpha", "a" + i, "a" + i, now + i);
                var controller = new PaletteController(KitTestSupport.context(), registry, history, true, () -> { }, () -> { },
                    () -> false, () -> { }, message -> { }, id -> { });
                try {
                    controller.open(PaletteScope.ALL_ID, PaletteTarget.none(), () -> true);
                    assertThat(headings(controller)).as("three recent rows (3e), then the most used one")
                        .containsExactly("RECENT Recently used", "USED Most used");
                    controller.view().setQueryText("1");
                    assertThat(headings(controller)).as("a query heads each scope by its label")
                        .containsExactly("MATCHES Alpha", "MATCHES Beta");
                    controller.selectTab("t.alpha");
                    controller.view().setQueryText("");
                    assertThat(headings(controller)).as("a chosen scope's own section label").containsExactly("SECTION Recent");
                } finally { controller.close(); }
            }
        });
    }

    @Test void withoutUsesTheEmptyQuerySuggests() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new ScopeRegistry(); var history = new CommandHistory()) {
                registry.register(scope("t.alpha", "Alpha", null, "a1", "a2"));
                var controller = new PaletteController(KitTestSupport.context(), registry, history, true, () -> { }, () -> { },
                    () -> false, () -> { }, message -> { }, id -> { });
                try {
                    controller.open(PaletteScope.ALL_ID, PaletteTarget.none(), () -> true);
                    assertThat(headings(controller)).containsExactly("SUGGESTED Suggested");
                } finally { controller.close(); }
            }
        });
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/palette/RunCopyTest.java`:

```java
package dev.jasper.app.palette;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.lifecycle.Subscription;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 11.2 and 11.3: the Run window's headings, nouns, detail line, primary label and "i of n". */
class RunCopyTest {
    static PaletteScope scope(String id, String label, String noun, List<PaletteVerb> verbs) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public String placeholder() { return ""; }
            @Override public List<PaletteVerb> verbs() { return verbs; }
            @Override public String itemNoun() { return noun; }
            @Override public PaletteResults search(String query, PaletteContext context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    static final PaletteScope HOSTS = scope("t.hosts", "SSH", "session", List.of(new PaletteVerb("connect", "Connect"),
        new PaletteVerb("split", "Connect in split", PaletteVerb.Role.SPLIT), new PaletteVerb("edit", "Edit")));
    static final PaletteScope SNIPPETS = scope("t.snippets", "Snippets", "snippet", List.of(new PaletteVerb("insert", "Insert")));
    static final PaletteScope LABELLED = scope("t.other", "File Servers", "File Servers", List.of(new PaletteVerb("run", "Run")));

    static PaletteEntry.Item item(PaletteScope scope, String id, String detail) {
        return new PaletteEntry.Item(scope, new PaletteRow(id, "Row " + id, detail, null, null, true, null));
    }

    @Test void headingsReadTheRunWindowsWords() {
        assertThat(RunCopy.heading(new PaletteEntry.Header(PaletteEntry.Header.Kind.RECENT, "Recently used"))).isEqualTo("Recent");
        assertThat(RunCopy.heading(new PaletteEntry.Header(PaletteEntry.Header.Kind.USED, "Most used"))).isEqualTo("Most used");
        assertThat(RunCopy.heading(new PaletteEntry.Header(PaletteEntry.Header.Kind.SUGGESTED, "Suggested"))).isEqualTo("Suggested");
        assertThat(RunCopy.heading(new PaletteEntry.Header(PaletteEntry.Header.Kind.SECTION, "Pinned"))).isEqualTo("Pinned");
        assertThat(RunCopy.heading(new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "SSH"))).isEqualTo("Matches");
        assertThat(new PaletteEntry.Header("Commands").kind()).as("the convenience is a section label").isEqualTo(PaletteEntry.Header.Kind.SECTION);
    }

    /** Review Focus 2. */
    @Test void iOfNCountsMatchingRowsOnlyAndBlanksOnAMoreRow() {
        var entries = List.<PaletteEntry>of(new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "SSH"), item(HOSTS, "a", null),
            item(HOSTS, "b", null), new PaletteEntry.More(HOSTS), new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "Snippets"),
            item(SNIPPETS, "c", null));
        assertThat(RunCopy.position(entries, 1)).isEqualTo("1 of 3");
        assertThat(RunCopy.position(entries, 5)).isEqualTo("3 of 3");
        assertThat(RunCopy.position(entries, 3)).as("a More row").isEmpty();
        assertThat(RunCopy.position(entries, -1)).as("no selection").isEmpty();
        var recent = List.<PaletteEntry>of(new PaletteEntry.Header(PaletteEntry.Header.Kind.RECENT, "Recently used"), item(HOSTS, "a", null));
        assertThat(RunCopy.position(recent, 1)).as("only under a query").isEmpty();
    }

    @Test void theDetailLineNamesTheRowByItsNoun() {
        assertThat(RunCopy.detailLabel(item(HOSTS, "mac", "dustin@192.168.1.119:22"))).isEqualTo("Session:");
        assertThat(RunCopy.detailValue(item(HOSTS, "mac", "dustin@192.168.1.119:22"))).isEqualTo("dustin@192.168.1.119:22");
        assertThat(RunCopy.detailValue(item(SNIPPETS, "x", null))).as("no detail: the title").isEqualTo("Row x");
        assertThat(RunCopy.detailLabel(item(LABELLED, "y", null))).as("a label noun keeps its case").isEqualTo("File Servers:");
        assertThat(RunCopy.noun(SNIPPETS)).isEqualTo("Snippet");
        assertThat(RunCopy.detailLabel(new PaletteEntry.More(HOSTS))).isEmpty();
        assertThat(RunCopy.detailLabel(null)).isEmpty();
        assertThat(RunCopy.detailValue(null)).isEmpty();
    }

    @Test void thePrimaryButtonReadsVerbZeroAndOpenOtherwise() {
        assertThat(RunCopy.primaryLabel(item(HOSTS, "mac", null))).isEqualTo("Connect");
        assertThat(RunCopy.primaryEnabled(item(HOSTS, "mac", null))).isTrue();
        assertThat(RunCopy.primaryLabel(new PaletteEntry.More(HOSTS))).isEqualTo("Open");
        assertThat(RunCopy.primaryEnabled(new PaletteEntry.More(HOSTS))).isTrue();
        assertThat(RunCopy.primaryLabel(null)).isEqualTo("Open");
        assertThat(RunCopy.primaryEnabled(null)).isFalse();
        assertThat(RunCopy.splitVerb(HOSTS)).isEqualTo(1);
        assertThat(RunCopy.splitVerb(SNIPPETS)).isEqualTo(-1);
        assertThat(RunCopy.HELP).isEqualTo("Type the name of a session, command, file or snippet, and Jasper will open it for you.");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.PaletteHeadingsTest' --tests 'dev.jasper.app.palette.RunCopyTest'`
Expected: FAIL: `compileTestJava` reports "cannot find symbol … kind()", "… Kind" and "… class RunCopy".

- [ ] **Step 3: Type the headings and write the copy**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 8a Task 2 step 3: PaletteEntry.Header.Kind, the controller's four headings and the new test's ledger row."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/palette/"
edit(P + "PaletteEntry.java", [(
'''    /** A heading: a scope's label in All, or a scope's own section label ("Recent") in its tab. Never selected. */
    record Header(String label) implements PaletteEntry {
        public Header { Objects.requireNonNull(label); }
    }''',
'''    /**
     * A heading: a scope's label in All, or a scope's own section label ("Recent") in its tab. Never selected. Its
     * {@code kind} says what it heads, so a view may word it its own way (spec 11.3); {@code label} is the card's words.
     */
    record Header(Kind kind, String label) implements PaletteEntry {
        /** What a heading heads. */
        public enum Kind {
            /** The rows run most recently. */ RECENT,
            /** The rows ranked by use. */ USED,
            /** The rows offered before anything is used. */ SUGGESTED,
            /** A chosen scope's own section label, shown as written. */ SECTION,
            /** One scope's matches under a query; the label is the scope's. */ MATCHES
        }
        public Header { Objects.requireNonNull(kind); Objects.requireNonNull(label); }
        /** A section label. */
        public Header(String label) { this(Kind.SECTION, label); }
    }''', 1)])
edit(P + "PaletteController.java", [
    ('''        if (!recent.isEmpty()) { entries.add(new PaletteEntry.Header("Recently used")); entries.addAll(recent); }
        if (!ranked.isEmpty()) { entries.add(new PaletteEntry.Header(used ? "Most used" : "Suggested")); entries.addAll(ranked); }''',
     '''        if (!recent.isEmpty()) { entries.add(new PaletteEntry.Header(PaletteEntry.Header.Kind.RECENT, "Recently used")); entries.addAll(recent); }
        if (!ranked.isEmpty()) {
            entries.add(used ? new PaletteEntry.Header(PaletteEntry.Header.Kind.USED, "Most used")
                : new PaletteEntry.Header(PaletteEntry.Header.Kind.SUGGESTED, "Suggested"));
            entries.addAll(ranked);
        }''', 1),
    ('''entries.add(new PaletteEntry.Header(results.sectionLabel()));''',
     '''entries.add(new PaletteEntry.Header(PaletteEntry.Header.Kind.SECTION, results.sectionLabel()));''', 1),
    ('''                entries.add(new PaletteEntry.Header(scope.label()));''',
     '''                entries.add(new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, scope.label()));''', 1)])
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n")
    + "jasper-app/src/test/java/dev/jasper/app/palette/PaletteHeadingsTest.java,app.palette,PaletteHeadingsTest,nonvisual-helper,none,none,"
    + "jasper-app/src/test/java/dev/jasper/app/palette/PaletteHeadingsTest.java,Test/fixture ownership; New in Enterprise plan 8a Task 2\n", encoding="utf-8")
PY
```

Create `jasper-app/src/main/java/dev/jasper/app/palette/RunCopy.java`:

```java
package dev.jasper.app.palette;

import java.util.List;
import java.util.Locale;

/**
 * The Run window's words (spec 11.2 and 11.3), as pure functions of the entries the controller made: its headings,
 * the item noun, the detail line, the primary button and the matches count. The card keeps its own strings.
 */
final class RunCopy {
    /** The help row's fixed text when no scope is chosen, or the chosen scope describes nothing. */
    static final String HELP = "Type the name of a session, command, file or snippet, and Jasper will open it for you.";
    /** The primary button's label while no row's verb names it. */
    static final String OPEN = "Open";

    private RunCopy() { }

    /** The Run window's word for {@code header}: one "Matches" for every scope's matches. */
    static String heading(PaletteEntry.Header header) {
        return switch (header.kind()) {
            case RECENT -> "Recent";
            case USED -> "Most used";
            case SUGGESTED -> "Suggested";
            case SECTION -> header.label();
            case MATCHES -> "Matches";
        };
    }

    /**
     * "i of n" under a query: the position of the selected entry among the rows of MATCHES sections, counting no
     * "More in…" row; blank without a query's headings, with no selection or with a "More in…" row selected.
     */
    static String position(List<PaletteEntry> entries, int selected) {
        boolean matches = false;
        int count = 0, index = 0;
        for (int i = 0; i < entries.size(); i++) {
            PaletteEntry entry = entries.get(i);
            if (entry instanceof PaletteEntry.Header header) { matches = header.kind() == PaletteEntry.Header.Kind.MATCHES; continue; }
            if (!matches || !(entry instanceof PaletteEntry.Item)) continue;
            count++;
            if (i == selected) index = count;
        }
        return index == 0 ? "" : index + " of " + count;
    }

    /** {@code scope}'s item noun with its first letter in upper case ("session" reads "Session"). */
    static String noun(PaletteScope scope) {
        String noun = scope.itemNoun();
        return noun.isEmpty() ? noun : noun.substring(0, 1).toUpperCase(Locale.ROOT) + noun.substring(1);
    }

    /** The detail line's label, "{Noun}:", for a selected row; blank for anything else. */
    static String detailLabel(PaletteEntry entry) {
        return entry instanceof PaletteEntry.Item item ? noun(item.scope()) + ":" : "";
    }

    /** The detail line's value: the row's detail, or its title when it has none; blank for anything else. */
    static String detailValue(PaletteEntry entry) {
        if (!(entry instanceof PaletteEntry.Item item)) return "";
        return item.row().detail() != null ? item.row().detail() : item.row().title();
    }

    /** The primary button's label: the selected row's verb 0, else "Open". */
    static String primaryLabel(PaletteEntry entry) {
        return entry instanceof PaletteEntry.Item item ? item.scope().verbs().getFirst().label() : OPEN;
    }

    /** Whether the primary button can run: a row or a "More in…" row is selected. */
    static boolean primaryEnabled(PaletteEntry entry) { return entry != null && entry.selectable(); }

    /** The index of {@code scope}'s SPLIT verb, or -1 when it has none. */
    static int splitVerb(PaletteScope scope) {
        var verbs = scope.verbs();
        for (int i = 0; i < verbs.size(); i++) if (verbs.get(i).role() == PaletteVerb.Role.SPLIT) return i;
        return -1;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.workspace.PaletteScopesTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS. `PaletteHeadingsTest` 2 tests, `RunCopyTest` 4; the card's tests are unchanged (a one-argument header is
a section label and the card prints `label()`); `ThemeMigrationCoverageTest` passes with the `PaletteHeadingsTest` row.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/palette jasper-app/src/test/java/dev/jasper/app/palette docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Run window: typed palette headings and the Run window's copy

A palette heading says what it heads: recent, most used, suggested, a
scope's own section or a scope's matches under a query. The card keeps its
words; RunCopy holds the Run window's: Recent, Most used, Suggested, one
Matches heading with "i of n", the "{Noun}: {detail}" line and the primary
verb's label.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: jasper-ui: the Run window's roles and its presentation flag

The Run window's geometry and type become kit values (Rulings 4), and `KitView.runWindow(boolean)` is an app-only flag
under which a view's RESULTS lists, PROMPT_HEADER tiles, transparent fields and text take them: 21 px rows and 18 px
heading bands with a rule and a right-aligned count, 7 px hue squares that keep their hue on the selection, the 32 px
help tile and its filled ▶, the data-face query with no ring of its own, and the mocks' text colours (Rulings 8). Views
without the flag paint exactly as before.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (the `Role` enum's last constant), `jasper-ui/src/main/java/dev/jasper/ui/metrics/Typography.java:27,74`, `jasper-ui/src/main/java/dev/jasper/ui/metrics/TypeScale.java` (`classicRow`)
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv`, `classic-roles.tsv`, `default-type.tsv`, `classic-type.tsv` (rows appended), `jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java` (`ADDED`, the thirty names appended as plans 4c, 6a and 7 append theirs), `jasper-ui/src/test/java/dev/jasper/ui/metrics/TypographyTest.java:144-145` (`former`'s data-face roles)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java:21,402`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKit.java:21`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java:326`, `Labels.java:143,175,181` (the SWATCH branch is 3b's), `Stacks.java:92-93,103,133,170-171,193-195`, `ItemLists.java:176,196,425,455`, `InteractiveRows.java:1190,1213-1217,1237-1238`, `TextFields.java:284,299,320,395,451-452`
- Modify (test fixtures): `jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java:19` (`enterpriseLight()`, plan 4c Task 4's text)
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/RunWindowPresentation.java`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/metrics/RunWindowRolesTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/swing/RunWindowPresentationTest.java`

**Interfaces:**
- Consumes: 4a's catalog, `classic-roles.tsv` and `Kit.colors().dialogSurface()`, 3b's SWATCH branch, `Stacks.Tile`, `Composition.WidthLayout`, `InteractiveRows.Ink`; plan 2's `PaintPair`, `SurfaceContrast.painted` and `SurfaceContrast.Guard`.
- Produces: `DesignMetrics.Role.RUN_WINDOW_RADIUS` … `RUN_WINDOW_BUTTON_GAP` (the thirty roles of the "Mock measurements"
  table, values below); `Typography.Role.RUN_HEADING`, `RUN_DETAIL` (a data-face role); built-in icons `play.filled` and
  `triangle.down`; `public void KitView.runWindow(boolean)` and `public boolean KitView.runWindow()`; `public final class
  RunWindowPresentation` with the declared pairs (Rulings 8): the `PaintPair` constants `TITLE`, `SHORTCUT`, `HEADING`,
  `SELECTED_TITLE`, `LIST_TEXT`, `static PaintPair labelPair(Kit)`, `static PaintPair valuePair(Kit)`, `static
  List<PaintPair> pairs(Kit)` (in that order: TITLE, SHORTCUT, labelPair, valuePair, HEADING, SELECTED_TITLE, LIST_TEXT),
  `record Authored(ColorRole slot, ColorRole against)`, the `Authored` constants `ROW_DETAIL`, `COUNT`,
  `SELECTED_DETAIL`, `static List<Authored> authored()` and `static PaintPair tileEdge(Kit)` (the help tile's edge, an
  INDICATOR pair on the dialog surface); and package-private `RunWindowPresentation.text(…)`, `.row(…)`,
  `.rowHeight(KitContext, boolean)`, `.headingLayout(InteractiveRows.Row)`, `.paintHeading(InteractiveRows.Row, Graphics)`,
  `.helpLayout(Stacks.StackControl)`, `.helpChildren(List<ControlRegistry.Control>)` and `.paintTile(Stacks.Tile,
  Graphics2D)`; `Stacks.Tile.run`; `public static GeneratedTheme KitFixtures.enterpriseLight()`. A Run window heading's
  count is its second title node (Rulings 13).

- [ ] **Step 1: Capture the gallery base**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
rm -rf jasper-ui/build/plan8-base && mkdir -p jasper-ui/build/plan8-base
cp -R jasper-ui/build/gallery/dark-1x jasper-ui/build/gallery/light-1x jasper-ui/build/plan8-base/
ls jasper-ui/build/plan8-base/dark-1x | wc -l
```

Expected: both runs print "Rendered N fixtures"; keep the directory until Task 4's check.

- [ ] **Step 2: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/metrics/RunWindowRolesTest.java`:

```java
package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Enterprise presentation plan 8a: the Run window's geometry and type, measured on the 06 Run Dialog mocks. */
class RunWindowRolesTest {
    static final Map<String, Integer> ROLES = new LinkedHashMap<>();
    static {
        String[] rows = {"RADIUS 4", "TITLE_HEIGHT 24", "TITLE_SIDE 10", "CLOSE 16", "CLOSE_SIDE 5", "SHORTCUT_GAP 10", "SIDE 12",
            "HELP_TOP 10", "TILE 32", "TILE_GLYPH 13", "TILE_GAP 10", "HELP_LINE 18", "FIELD_TOP 11", "LABEL_COLUMN 48", "FIELD_SIDE 6",
            "MENU_WIDTH 20", "LIST_TOP 10", "ROW_HEIGHT 21", "HEADING_HEIGHT 18", "ROW_SIDE 8", "SWATCH 7", "ROW_GAP 7", "DETAIL_GAP 7",
            "DETAIL_TOP 10", "DETAIL_LABEL_GAP 6", "CHECK_TOP 10", "BOTTOM 10", "FOOTER_HEIGHT 40", "BUTTON_WIDTH 70", "BUTTON_GAP 6"};
        for (String row : rows) ROLES.put("RUN_WINDOW_" + row.split(" ")[0], Integer.parseInt(row.split(" ")[1]));
    }

    /** One block in order (later plans may append their own roles after it), with the mocks' values in both catalogs. */
    @Test void theRunWindowRolesAreOneBlockWithTheMocksValuesInBothCatalogs() {
        var names = Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        int start = names.indexOf("RUN_WINDOW_RADIUS");
        assertThat(start).as("the block's first role").isNotNegative();
        assertThat(names.subList(start, Math.min(names.size(), start + ROLES.size()))).containsExactlyElementsOf(ROLES.keySet());
        ROLES.forEach((name, value) -> {
            var role = DesignMetrics.Role.valueOf(name);
            assertThat(DesignMetrics.DEFAULT.value(role)).as(name).isEqualTo(value);
            assertThat(DesignMetrics.CLASSIC.value(role)).as(name + " (only the Run window reads it)").isEqualTo(value);
        });
    }

    @Test void theRunWindowTypeIsSemiboldHeadingsAndASmallDataFace() {
        assertThat(TypeScale.CLASSIC.size(Typography.Role.RUN_HEADING)).isEqualTo(11f);
        assertThat(TypeScale.CLASSIC.weight(Typography.Role.RUN_HEADING)).isEqualTo(600);
        assertThat(TypeScale.CLASSIC.size(Typography.Role.RUN_DETAIL)).isEqualTo(10.5f);
        assertThat(TypeScale.CLASSIC.weight(Typography.Role.RUN_DETAIL)).isEqualTo(400);
        assertThat(TypeScale.DEFAULT.size(Typography.Role.RUN_HEADING)).isEqualTo(12f);
        assertThat(TypeScale.DEFAULT.size(Typography.Role.RUN_DETAIL)).isEqualTo(11.5f);
        var font = Typography.of(TypeScale.CLASSIC, FaceSet.PLATFORM, Typography.UserFont.NONE).font(Typography.Role.RUN_DETAIL);
        assertThat(font.getFamily(java.util.Locale.ROOT)).as("the data face").isEqualTo(
            Typography.of(TypeScale.CLASSIC, FaceSet.PLATFORM, Typography.UserFont.NONE).font(Typography.Role.MONO).getFamily(java.util.Locale.ROOT));
    }
}
```

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/RunWindowPresentationTest.java`:

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.Test;

/** Enterprise presentation plan 8a: KitView.runWindow draws the Run window's list, help tile, query and text. */
class RunWindowPresentationTest {
    static final ControlState READY = ControlState.READY;

    static Nodes.Text text(String id, String value, Nodes.TextRole role) { return new Nodes.Text(id, READY, value, role); }

    /** A heading with a count (its second title node: a heading has a title only), a selected session row and a snippet row with a tag. */
    static ViewSpec list() {
        var items = List.of(
            new ListItem("h", Optional.empty(), List.of(text("h.title", "Matches", Nodes.TextRole.CAPTION),
                text("h.count", "1 of 2", Nodes.TextRole.CAPTION)), List.of(), List.of(), true),
            new ListItem("a", Optional.of(new Nodes.Icon("a.swatch", READY, "file", Nodes.Hue.GREEN, Nodes.IconSize.SWATCH, "Session")),
                List.of(text("a.title", "Mac", Nodes.TextRole.BODY)), List.of(text("a.detail", "dustin@192.168.1.119:22", Nodes.TextRole.MONO)), List.of()),
            new ListItem("b", Optional.of(new Nodes.Icon("b.swatch", READY, "file", Nodes.Hue.PURPLE, Nodes.IconSize.SWATCH, "Snippet")),
                List.of(text("b.title", "gcloud auth", Nodes.TextRole.BODY)), List.of(text("b.detail", "gcloud auth login", Nodes.TextRole.MONO)),
                List.of(text("b.tag", "K", Nodes.TextRole.CAPTION))));
        return new ViewSpec(new Nodes.ItemList("results", READY, Nodes.ListKind.RESULTS, items, Set.of("a"), "run", List.of(),
            Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(8)));
    }

    static List<Component> rows(KitView view) {
        var canvas = (Container) find(view, "results", JScrollPane.class).getViewport().getView();
        var rows = new ArrayList<Component>();
        for (Component child : canvas.getComponents()) if (child instanceof InteractiveRows.Row && child.isVisible()) rows.add(child);
        rows.sort(java.util.Comparator.comparingInt(Component::getY));
        return rows;
    }

    static <T extends Component> T find(Component root, String name, Class<T> type) {
        if (type.isInstance(root) && name.equals(root.getName())) return type.cast(root);
        if (root instanceof Container container) for (Component child : container.getComponents()) {
            T found = find(child, name, type);
            if (found != null) return found;
        }
        return null;
    }

    static int m(KitContext context, DesignMetrics.Role role) { return context.metrics().value(role); }

    @Test void theListTakesTheRunWindowsRowsBandsAndHueSquares() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, list(), event -> { })) {
                KitFixtures.render(view, 436, 120, 1);
                int defaultHeading = rows(view).getFirst().getHeight();
                view.runWindow(true);
                BufferedImage image = KitFixtures.render(view, 436, 120, 1);
                var rows = rows(view);
                assertThat(rows).extracting(Component::getHeight).containsExactly(19, 21, 21);
                assertThat(defaultHeading).as("the flag is what changes it").isNotEqualTo(19);
                var theme = context.theme();
                assertThat(new Color(image.getRGB(200, 5))).as("the heading band").isEqualTo(DerivedColor.SURFACE_TABLE_HEADER.color(theme));
                assertThat(new Color(image.getRGB(200, 18))).as("its rule").isEqualTo(DerivedColor.BORDER_ROW.color(theme));
                assertThat(new Color(image.getRGB(300, 19 + 10))).as("the selected row").isEqualTo(theme.semantic().color(SemanticColor.ACCENT));
                int side = m(context, DesignMetrics.Role.RUN_WINDOW_ROW_SIDE), square = m(context, DesignMetrics.Role.RUN_WINDOW_SWATCH);
                assertThat(new Color(image.getRGB(side + square / 2, 19 + 10))).as("the square keeps its hue on the selection")
                    .isEqualTo(theme.semantic().color(SemanticColor.HUE_GREEN));
                assertThat(new Color(image.getRGB(side + square + 1, 19 + 10))).isEqualTo(theme.semantic().color(SemanticColor.ACCENT));
                var count = find(view, "h.count", JLabel.class);
                assertThat(count.getForeground()).isEqualTo(theme.semantic().color(SemanticColor.TEXT_PLACEHOLDER));
                assertThat(javax.swing.SwingUtilities.convertRectangle(count.getParent(), count.getBounds(), rows.getFirst()).getMaxX())
                    .as("the count ends at the row inset").isEqualTo(436 - side);
                var heading = find(view, "h.title", JLabel.class);
                assertThat(heading.getFont().getSize2D()).isEqualTo(11f);
                assertThat(heading.getForeground()).isEqualTo(theme.semantic().color(SemanticColor.TEXT_STRONG_SECONDARY));
                var detail = find(view, "b.detail", JLabel.class);
                assertThat(detail.getFont().getSize2D()).isEqualTo(10.5f);
                assertThat(detail.getForeground()).isEqualTo(theme.semantic().color(SemanticColor.TEXT_PLACEHOLDER));
                assertThat(detail.getClientProperty("Jasper.leadingEllipsis")).as("a long detail is cut at its end, as the mocks cut it").isEqualTo(false);
                assertThat(find(view, "b.tag", JLabel.class).getForeground()).isEqualTo(theme.semantic().color(SemanticColor.TEXT_PLACEHOLDER));
                var title = find(view, "b.title", JLabel.class);
                assertThat(javax.swing.SwingUtilities.convertPoint(title.getParent(), title.getX(), 0, rows.get(2)).x)
                    .isEqualTo(side + square + m(context, DesignMetrics.Role.RUN_WINDOW_ROW_GAP));
            }
        });
    }

    @Test void theHelpRowIsTheRunTileBesideItsTextInTheRunWindowsColours() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var help = new ViewSpec(new Nodes.Stack("help", READY, Nodes.LayoutRole.PROMPT_HEADER, List.of(
                new Nodes.Icon("help.icon", READY, "play.filled", Nodes.Hue.BLUE, Nodes.IconSize.PROMPT, "Run"),
                text("help.text", "Type the name of a session, command, file or snippet, and Jasper will open it for you.", Nodes.TextRole.DESCRIPTION)), ""));
            try (var view = new KitView(context, help, event -> { })) {
                KitFixtures.render(view, 436, 60, 1);
                int classicTile = find(view, "help.tile", JComponent.class).getWidth();
                view.runWindow(true);
                BufferedImage image = KitFixtures.render(view, 436, 60, 1);
                var tile = find(view, "help.tile", JComponent.class);
                assertThat(tile.getWidth()).isEqualTo(32);
                assertThat(classicTile).as("the classic prompt's tile").isEqualTo(m(context, DesignMetrics.Role.PROMPT_TILE));
                var label = find(view, "help.text", JLabel.class);
                var at = javax.swing.SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), view);
                var tileAt = javax.swing.SwingUtilities.convertRectangle(tile.getParent(), tile.getBounds(), view);
                assertThat(at.x - (tileAt.x + tileAt.width)).isEqualTo(m(context, DesignMetrics.Role.RUN_WINDOW_TILE_GAP));
                assertThat(label.getFont().getSize2D()).as("the help at BODY's size").isEqualTo(12f);
                assertThat(label.getForeground()).isEqualTo(context.theme().semantic().color(SemanticColor.TEXT_STRONG_SECONDARY));
                assertThat(((Labels.LiteralLabel) label).wrapped.lineHeight).isEqualTo(18);
                var palette = context.theme().semantic();
                assertThat(new Color(image.getRGB(tileAt.x + 3, tileAt.y + 16))).as("the accent tile").isEqualTo(palette.color(SemanticColor.ACCENT));
                assertThat(new Color(image.getRGB(tileAt.x + 16, tileAt.y))).as("its edge")
                    .isEqualTo(dev.jasper.ui.theme.SurfaceContrast.painted(context.theme(), RunWindowPresentation.tileEdge(context.kit())));
                var centre = new Color(image.getRGB(tileAt.x + 16, tileAt.y + 16));
                var ink = palette.color(SemanticColor.TEXT_ONACCENT);
                // The glyph's raster covers its interior at 254 of 255, so the ink is matched within two levels a channel.
                assertThat(List.of(centre.getRed() - ink.getRed(), centre.getGreen() - ink.getGreen(), centre.getBlue() - ink.getBlue()))
                    .as("a filled play glyph in the on-accent ink").allMatch(difference -> Math.abs(difference) <= 2);
            }
        });
    }

    @Test void runWindowTextAndTheQuietQueryTakeTheMocksFacesAndColours() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var spec = new ViewSpec(new Nodes.Stack("row", READY, Nodes.LayoutRole.VERTICAL, List.of(
                text("label", "Open:", Nodes.TextRole.LABEL), text("strong", "Session:", Nodes.TextRole.BODY_STRONG),
                text("mono", "dustin@192.168.1.119:22", Nodes.TextRole.MONO),
                new Nodes.Field("query", READY, Nodes.FieldKind.TEXT, "auth", "", "Open", "", Optional.empty(), Nodes.FieldPresentation.TRANSPARENT)), ""));
            try (var view = new KitView(context, spec, event -> { })) {
                view.runWindow(true);
                KitFixtures.render(view, 436, 120, 1);
                var palette = context.theme().semantic();
                assertThat(find(view, "label", JLabel.class).getForeground()).isEqualTo(palette.color(SemanticColor.TEXT_STRONG_SECONDARY));
                var strong = find(view, "strong", JLabel.class);
                assertThat(strong.getForeground()).isEqualTo(palette.color(SemanticColor.TEXT_STRONG_SECONDARY));
                assertThat(strong.getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.PANEL_TITLE));
                var mono = find(view, "mono", JLabel.class);
                assertThat(mono.getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.RUN_DETAIL));
                assertThat(mono.getForeground()).isEqualTo(palette.color(SemanticColor.TEXT_SECONDARY));
                var input = find(view, "query.input", javax.swing.text.JTextComponent.class);
                assertThat(input.getFont()).as("the query in the data face").isEqualTo(ThemePainting.font(context, Typography.Role.MONO));
                var surface = (TextFields.FieldSurface) input.getParent();
                assertThat(surface.quiet).isTrue();
                assertThat(surface.getInsets()).isEqualTo(new java.awt.Insets(0, 0, 0, 0));
            }
        });
    }
}
```

Append the thirty names to `NewRolesTest.ADDED`, as plans 6a and 7 append theirs (its test requires `ADDED` to be one
run of the enum, in order; `RunWindowRolesTest` checks the Run window's block itself; R14):

```bash
python3 -I - <<'PY'
"""Plan 8a Task 3 step 2: NewRolesTest.ADDED gains the Run window's thirty roles, in enum order."""
import pathlib, re
NAMES = ["RADIUS", "TITLE_HEIGHT", "TITLE_SIDE", "CLOSE", "CLOSE_SIDE", "SHORTCUT_GAP", "SIDE", "HELP_TOP", "TILE", "TILE_GLYPH",
         "TILE_GAP", "HELP_LINE", "FIELD_TOP", "LABEL_COLUMN", "FIELD_SIDE", "MENU_WIDTH", "LIST_TOP", "ROW_HEIGHT", "HEADING_HEIGHT",
         "ROW_SIDE", "SWATCH", "ROW_GAP", "DETAIL_GAP", "DETAIL_TOP", "DETAIL_LABEL_GAP", "CHECK_TOP", "BOTTOM", "FOOTER_HEIGHT",
         "BUTTON_WIDTH", "BUTTON_GAP"]
path = pathlib.Path("jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java"); text = path.read_text(encoding="utf-8")
match = re.search(r"static final List<String> ADDED = List\.of\(([^;]*)\);", text)
if match is None: raise SystemExit("NewRolesTest: ADDED moved")
if "RUN_WINDOW_RADIUS" in match.group(1): raise SystemExit("NewRolesTest: ADDED already lists the Run window roles")
text = text[:match.end(1)] + ", " + ", ".join(f'"RUN_WINDOW_{name}"' for name in NAMES) + text[match.end(1):]
path.write_text(text, encoding="utf-8")
PY
```

Add the Enterprise Light fixture theme the tests render with, plan 4c Task 4's method verbatim and at 4c's anchor, so the
merge with 4c keeps one copy (R4):

```bash
python3 -I - <<'PY'
"""Plan 8a Task 3 step 2: KitFixtures.enterpriseLight(), plan 4c Task 4's fixture theme, verbatim at 4c's anchor (Execution notes R4)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java", [
    ('''    /** The platform UI family the default kit draws in when the user names none (resolved once per process). */''',
     '''    /**
     * Spec 7.6's Enterprise Light as a fixture theme (plan 4c Rulings 20): Slate Cobalt Light with the table's named roles
     * and its eight derived overrides; a role the table does not name keeps Slate Cobalt Light's value. The shipped theme,
     * with its derived neutrals, is plan 5's.
     */
    public static GeneratedTheme enterpriseLight() {
        var base = light();
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, base.semantic().color(role));
        java.util.function.BiConsumer<Integer, SemanticColor[]> name = (rgb, roles) -> { for (var role : roles) values.put(role, new java.awt.Color(rgb)); };
        name.accept(0xECEDEF, new SemanticColor[] {SemanticColor.SURFACE_APP, SemanticColor.SURFACE_DIALOG});
        name.accept(0xE1E3E6, new SemanticColor[] {SemanticColor.SURFACE_TITLEBAR});
        name.accept(0xF7F7F8, new SemanticColor[] {SemanticColor.SURFACE_PANEL, SemanticColor.SURFACE_SIDEBAR});
        name.accept(0xFFFFFF, new SemanticColor[] {SemanticColor.SURFACE_CARD, SemanticColor.SURFACE_INPUT, SemanticColor.SURFACE_TERMINAL,
            SemanticColor.TEXT_ONACCENT, SemanticColor.TEXT_ONSELECTION});
        name.accept(0x2F65CA, new SemanticColor[] {SemanticColor.ACCENT, SemanticColor.SELECTION_BG, SemanticColor.HUE_BLUE});
        name.accept(0xDCE5F7, new SemanticColor[] {SemanticColor.TEXT_ONACCENT_SECONDARY, SemanticColor.TEXT_ONSELECTION_SECONDARY});
        name.accept(0x8A5A00, new SemanticColor[] {SemanticColor.STATUS_WARNING});
        name.accept(0xB5651D, new SemanticColor[] {SemanticColor.HUE_AMBER});
        name.accept(0xC4C7CC, new SemanticColor[] {SemanticColor.BORDER_WINDOW});
        name.accept(0xB4B8BF, new SemanticColor[] {SemanticColor.BORDER_CONTROL, SemanticColor.BORDER_INPUT, SemanticColor.BUTTON_SECONDARY_BORDER});
        name.accept(0x1E1F22, new SemanticColor[] {SemanticColor.TEXT_PRIMARY});
        name.accept(0x3A3D42, new SemanticColor[] {SemanticColor.TEXT_STRONG_SECONDARY});
        name.accept(0x5C6370, new SemanticColor[] {SemanticColor.TEXT_SECONDARY, SemanticColor.TEXT_TERTIARY});
        name.accept(0x7A808A, new SemanticColor[] {SemanticColor.TEXT_PLACEHOLDER});
        name.accept(0x2E9E4F, new SemanticColor[] {SemanticColor.STATUS_SUCCESS, SemanticColor.HUE_GREEN});
        name.accept(0xD03A3A, new SemanticColor[] {SemanticColor.STATUS_DANGER});
        name.accept(0x8A4FBF, new SemanticColor[] {SemanticColor.HUE_PURPLE});
        var overrides = new java.util.EnumMap<DerivedColor, java.awt.Color>(DerivedColor.class);
        overrides.put(DerivedColor.SURFACE_HEADER, new java.awt.Color(0xE1E3E6));
        overrides.put(DerivedColor.SURFACE_HEADER_ACTIVE, new java.awt.Color(0xD9DFEA));
        overrides.put(DerivedColor.SURFACE_STRIPE, new java.awt.Color(0xE4E6E9));
        overrides.put(DerivedColor.SURFACE_TABLE_HEADER, new java.awt.Color(0xF0F1F3));
        overrides.put(DerivedColor.SURFACE_ZEBRA, new java.awt.Color(0xF7F8FA));
        overrides.put(DerivedColor.SURFACE_ROW_HOVER, new java.awt.Color(0xEEF2FA));
        overrides.put(DerivedColor.WARNING_GROUND, new java.awt.Color(0xFBF1DA));
        overrides.put(DerivedColor.WARNING_BORDER, new java.awt.Color(0xE6CF95));
        return new GeneratedTheme("Enterprise", false, base.author(), new SemanticPalette(values), base.terminal(), base.editor(), base.syntax(),
            base.glass(), overrides);
    }

    /** The platform UI family the default kit draws in when the user names none (resolved once per process). */''', 1)])
PY
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.RunWindowRolesTest' --tests 'dev.jasper.ui.swing.RunWindowPresentationTest'`
Expected: FAIL: `compileTestJava` reports "cannot find symbol … RUN_WINDOW_ROW_SIDE", "… RUN_DETAIL" and "… runWindow(boolean)".

- [ ] **Step 4: The roles, the type and the icons**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 8a Task 3 step 4: thirty RUN_WINDOW_* roles, RUN_HEADING and RUN_DETAIL, play.filled and triangle.down."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
ROLES = [("RADIUS", 4), ("TITLE_HEIGHT", 24), ("TITLE_SIDE", 10), ("CLOSE", 16), ("CLOSE_SIDE", 5), ("SHORTCUT_GAP", 10), ("SIDE", 12),
         ("HELP_TOP", 10), ("TILE", 32), ("TILE_GLYPH", 13), ("TILE_GAP", 10), ("HELP_LINE", 18), ("FIELD_TOP", 11), ("LABEL_COLUMN", 48),
         ("FIELD_SIDE", 6), ("MENU_WIDTH", 20), ("LIST_TOP", 10), ("ROW_HEIGHT", 21), ("HEADING_HEIGHT", 18), ("ROW_SIDE", 8), ("SWATCH", 7),
         ("ROW_GAP", 7), ("DETAIL_GAP", 7), ("DETAIL_TOP", 10), ("DETAIL_LABEL_GAP", 6), ("CHECK_TOP", 10), ("BOTTOM", 10),
         ("FOOTER_HEIGHT", 40), ("BUTTON_WIDTH", 70), ("BUTTON_GAP", 6)]
path = pathlib.Path("jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java"); text = path.read_text(encoding="utf-8")
start = text.index("public enum Role {")
last = re.compile(r"(\w+\(-?\d+\));").search(text, start)
if last is None: raise SystemExit("DesignMetrics: the Role enum's last constant was not found")
block = ",\n        // The Run window (Enterprise presentation plan 8a), measured on the 06 Run Dialog mocks; read only by the Run window.\n        " \
    + ", ".join(f"RUN_WINDOW_{name}({value})" for name, value in ROLES) + ";"
text = text[:last.start()] + last.group(1) + block + text[last.end():]
path.write_text(text, encoding="utf-8")
M = "jasper-ui/src/test/resources/dev/jasper/ui/metrics/"
for table in ("compact-roles.tsv", "classic-roles.tsv"):
    path = pathlib.Path(M + table); text = path.read_text(encoding="utf-8")
    if "RUN_WINDOW_RADIUS" in text: raise SystemExit(table + " already has the Run window roles")
    path.write_text(text + "".join(f"RUN_WINDOW_{name}\t{value}\n" for name, value in ROLES), encoding="utf-8")
path = pathlib.Path("jasper-ui/src/main/java/dev/jasper/ui/metrics/Typography.java"); text = path.read_text(encoding="utf-8")
start = text.index("public enum Role {")
last = re.compile(r"(\w+\([\d.]+f?, ?\d+\));").search(text, start)
if last is None: raise SystemExit("Typography: the Role enum's last constant was not found")
text = text[:last.start()] + last.group(1) + ",\n        // The Run window's headings and data text (plan 8a): the mocks' 11 px semibold and 0.87 em data face.\n" \
    + "        RUN_HEADING(12, 600), RUN_DETAIL(11.5f, 400);" + text[last.end():]
old = "role == Role.MONO || role == Role.MONO_SMALL || role == Role.MONO_CAPTION || role == Role.PANEL_DETAIL_MONO"
if text.count(old) != 1: raise SystemExit("Typography: the data-face roles moved (plan 5b may list them elsewhere; add RUN_DETAIL there)")
path.write_text(text.replace(old, old + " || role == Role.RUN_DETAIL"), encoding="utf-8")
# TypographyTest's former() oracle runs over every role with default-type.tsv; RUN_DETAIL is a data-face role there too.
edit("jasper-ui/src/test/java/dev/jasper/ui/metrics/TypographyTest.java", [
    ("            || role == Typography.Role.PANEL_DETAIL_MONO ? Font.MONOSPACED : family;",
     "            || role == Typography.Role.PANEL_DETAIL_MONO || role == Typography.Role.RUN_DETAIL ? Font.MONOSPACED : family;", 1)])
for table, rows in (("default-type.tsv", "RUN_HEADING\t12\t600\nRUN_DETAIL\t11.5\t400\n"), ("classic-type.tsv", "RUN_HEADING\t11\t600\nRUN_DETAIL\t10.5\t400\n")):
    path = pathlib.Path(M + table); text = path.read_text(encoding="utf-8")
    path.write_text(text + rows, encoding="utf-8")
path = pathlib.Path("jasper-ui/src/main/java/dev/jasper/ui/metrics/TypeScale.java"); text = path.read_text(encoding="utf-8")
cases = re.findall(r"\n            case \w+ -> new float\[\] \{[\d.]+f?, \d+\};", text)
if not cases: raise SystemExit("TypeScale: classicRow's cases were not found")
text = text.replace(cases[-1], cases[-1] + "\n            case RUN_HEADING -> new float[] {11, 600};\n            case RUN_DETAIL -> new float[] {10.5f, 400};", 1)
path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java", [
    ('    private static final Set<String> FILLED_BUILT_INS = Set.of("folder.filled");',
     '    private static final Set<String> FILLED_BUILT_INS = Set.of("folder.filled", "play.filled", "triangle.down");', 1),
    ('            Map.entry("play", "M 5 2 L 14 8 L 5 14 Z"),\n',
     '            Map.entry("play", "M 5 2 L 14 8 L 5 14 Z"),\n'
     '            // The Run window\'s solid help glyph and its Open field\'s menu arrow (plan 8a), as the mocks draw them.\n'
     '            Map.entry("play.filled", "M 4 3 L 13 8 L 4 13 Z"),\n'
     '            Map.entry("triangle.down", "M 4.5 4.5 L 11.5 4.5 L 8 12.5 Z"),\n', 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKit.java", [
    ('"terminal","play","warning"', '"terminal","play","play.filled","triangle.down","warning"', 1)])
PY
```

If a script stops because an earlier plan wrote the enums' last constants differently, re-find the last constant of
`DesignMetrics.Role` (or `Typography.Role`) and append the same constants after it; if plan 5b replaced the data-face
condition with a set or a method, add `Role.RUN_DETAIL` to it (5b's `boolean data = …` line keeps the quoted condition).
If `TypographyTest.former`'s condition was rewritten, add `Typography.Role.RUN_DETAIL` to its data-face roles the same way.

- [ ] **Step 5: The flag and its presentation**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/RunWindowPresentation.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.ColorRole;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JLabel;

/**
 * The Run window's presentation (Enterprise presentation spec 11.2, the 06 Run Dialog mocks), applied to the controls of
 * a view whose host called {@link KitView#runWindow(boolean)}: its text colours and faces, its RESULTS rows, heading
 * bands and counts, and its help row's accent tile. Every value is a kit role; nothing here is read for a view without
 * the flag. Every text colour is a declared pair (spec 7.3 and 16.1.1): {@link #pairs(Kit)} painted through their
 * guards, and {@link #authored()} painted as authored (plan 8a Rulings 8); the tile's edge is {@link #tileEdge(Kit)}.
 * Public for those tables, which jasper-app's PaintedContrastTest paints.
 */
public final class RunWindowPresentation {
    private RunWindowPresentation() { }

    /**
     * A pair the Run window paints as authored, with no guard (plan 8a Rulings 8).
     *
     * @param slot the role painted
     * @param against the role it is painted on
     */
    public record Authored(ColorRole slot, ColorRole against) {
        /** Both roles are required. */
        public Authored { Objects.requireNonNull(slot, "slot"); Objects.requireNonNull(against, "against"); }
    }

    /** The title row's "Run" on the title band. */
    public static final PaintPair TITLE = new PaintPair(SemanticColor.TEXT_PRIMARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.TEXT);
    /** The opener's shortcut on the title band. */
    public static final PaintPair SHORTCUT = new PaintPair(SemanticColor.TEXT_TERTIARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.TEXT);
    /** A list heading's title on its band. */
    public static final PaintPair HEADING = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, DerivedColor.SURFACE_TABLE_HEADER, SurfaceContrast.Guard.TEXT);
    /** A selected row's title on the accent. */
    public static final PaintPair SELECTED_TITLE = new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.ACCENT, SurfaceContrast.Guard.TEXT);
    /** A "More in" row's (or a disabled row's) title in the list box. */
    public static final PaintPair LIST_TEXT = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
    /** A row's detail and tag in the list box, as authored. */
    public static final Authored ROW_DETAIL = new Authored(SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_CARD);
    /** The matches count on a heading band, as authored. */
    public static final Authored COUNT = new Authored(SemanticColor.TEXT_PLACEHOLDER, DerivedColor.SURFACE_TABLE_HEADER);
    /** A selected row's detail and tag in the selection's supporting ink, as the card paints them. */
    public static final Authored SELECTED_DETAIL = new Authored(SemanticColor.TEXT_ONACCENT_SECONDARY, SemanticColor.ACCENT);

    /** "Open:", the checkbox's label, the help text and the detail line's label on {@code kit}'s dialog surface. */
    public static PaintPair labelPair(Kit kit) {
        return new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, kit.colors().dialogSurface(), SurfaceContrast.Guard.TEXT);
    }

    /** The detail line's value on {@code kit}'s dialog surface. */
    public static PaintPair valuePair(Kit kit) {
        return new PaintPair(SemanticColor.TEXT_SECONDARY, kit.colors().dialogSurface(), SurfaceContrast.Guard.TEXT);
    }

    /** Every guarded pair the Run window paints under {@code kit}: TITLE, SHORTCUT, the label pair, the value pair, HEADING, SELECTED_TITLE, LIST_TEXT. */
    public static List<PaintPair> pairs(Kit kit) {
        return List.of(TITLE, SHORTCUT, labelPair(kit), valuePair(kit), HEADING, SELECTED_TITLE, LIST_TEXT);
    }

    /** Every pair the Run window paints as authored: ROW_DETAIL, COUNT, SELECTED_DETAIL. */
    public static List<Authored> authored() { return List.of(ROW_DETAIL, COUNT, SELECTED_DETAIL); }

    /**
     * {@code text}'s Run window face and colour, after its control set the role's own: WINDOW_TITLE and the title row's
     * CAPTION through {@link #TITLE} and {@link #SHORTCUT}; LABEL, DESCRIPTION and BODY_STRONG through the label pair,
     * DESCRIPTION at BODY's size on the help's line, BODY_STRONG semibold; MONO in the small data face through the value
     * pair. A list's CAPTION, DESCRIPTION and MONO texts are repainted by {@link #row}. A selected row's ink and a
     * severity keep their colour.
     */
    static void text(KitContext context, Nodes.Text text, Labels.LiteralLabel label, boolean inked) {
        if (text.severity().isPresent()) return;
        var theme = context.theme();
        Kit kit = context.kit();
        switch (text.role()) {
            case WINDOW_TITLE -> { if (!inked) label.setForeground(SurfaceContrast.painted(theme, TITLE)); }
            case CAPTION -> { if (!inked) label.setForeground(SurfaceContrast.painted(theme, SHORTCUT)); }
            case LABEL -> { if (!inked) label.setForeground(SurfaceContrast.painted(theme, labelPair(kit))); }
            case DESCRIPTION -> {
                var font = ThemePainting.font(context, Typography.Role.BODY);
                label.setFont(font);
                label.wrapped.lineHeight = Math.round(context.metrics().value(DesignMetrics.Role.RUN_WINDOW_HELP_LINE) * font.getSize2D() / 12f);
                if (!inked) label.setForeground(SurfaceContrast.painted(theme, labelPair(kit)));
            }
            case BODY_STRONG -> {
                label.setFont(ThemePainting.font(context, Typography.Role.PANEL_TITLE));
                if (!inked) label.setForeground(SurfaceContrast.painted(theme, labelPair(kit)));
            }
            case MONO -> {
                label.setFont(ThemePainting.font(context, Typography.Role.RUN_DETAIL));
                if (!inked) label.setForeground(SurfaceContrast.painted(theme, valuePair(kit)));
            }
            default -> { }
        }
    }

    /** A RESULTS row's height: the row, or a heading's band and its rule. */
    static int rowHeight(KitContext context, boolean heading) {
        return heading ? ThemePainting.metric(context, DesignMetrics.Role.RUN_WINDOW_HEADING_HEIGHT) + ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE)
            : ThemePainting.metric(context, DesignMetrics.Role.RUN_WINDOW_ROW_HEIGHT);
    }

    /**
     * A heading's title semibold through {@link #HEADING} and its count (its second title node: a heading has a title
     * only) at the band's end as {@link #COUNT}; a row's title through {@link #SELECTED_TITLE} when selected, or
     * {@link #LIST_TEXT} for a DESCRIPTION title; its detail in the small data face, cut at its end, and its detail and tag
     * as {@link #ROW_DETAIL}, or {@link #SELECTED_DETAIL} when selected.
     */
    static void row(KitContext context, InteractiveRows.Row row, ListItem item, boolean selected) {
        var theme = context.theme();
        if (item.heading()) {
            var title = item.title();
            for (int i = 0; i < title.size(); i++) {
                boolean count = i > 0;
                label(row, title.get(i)).ifPresent(label -> {
                    label.setFont(ThemePainting.font(context, count ? Typography.Role.CAPTION : Typography.Role.RUN_HEADING));
                    label.setForeground(count ? COUNT.slot().color(theme) : SurfaceContrast.painted(theme, HEADING));
                });
            }
            // The row's slot gave the title panel an inline layout this populate; the heading's replaces it until the next.
            row.title.setLayout(new HeadingLayout(ThemePainting.metric(context, DesignMetrics.Role.RUN_WINDOW_ROW_GAP)));
            return;
        }
        for (var node : item.title()) label(row, node).ifPresent(label -> {
            if (selected) label.setForeground(SurfaceContrast.painted(theme, SELECTED_TITLE));
            else if (((Nodes.Text) node).role() == Nodes.TextRole.DESCRIPTION) label.setForeground(SurfaceContrast.painted(theme, LIST_TEXT));
        });
        var faint = (selected ? SELECTED_DETAIL : ROW_DETAIL).slot().color(theme);
        for (var node : item.subtitle()) label(row, node).ifPresent(label -> {
            label.setFont(ThemePainting.font(context, Typography.Role.RUN_DETAIL));
            label.setForeground(faint);
            // The mocks cut a long detail at its end, as the row's title is cut.
            label.putClientProperty("Jasper.leadingEllipsis", false);
        });
        for (var node : item.trailing()) label(row, node).ifPresent(label -> label.setForeground(faint));
    }

    private static Optional<JLabel> label(InteractiveRows.Row row, Nodes.Node node) {
        var control = row.controls.get(node.id());
        return node instanceof Nodes.Text && control != null && control.component instanceof JLabel label ? Optional.of(label) : Optional.empty();
    }

    /** A heading's title panel across the row, inset on both sides, above the band's rule; its layout places the count. */
    static void headingLayout(InteractiveRows.Row row) {
        var context = row.owner.context();
        int side = ThemePainting.metric(context, DesignMetrics.Role.RUN_WINDOW_ROW_SIDE);
        int band = Math.max(0, row.getHeight() - ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE));
        row.leading.setBounds(0, 0, 0, 0);
        row.subtitle.setBounds(0, 0, 0, 0);
        row.trailing.setBounds(0, 0, 0, 0);
        row.title.setBounds(side, 0, Math.max(0, row.getWidth() - 2 * side), band);
        row.title.doLayout();
    }

    /** A heading's band on {@code surface.tableHeader} over a {@code border.row} rule. */
    static void paintHeading(InteractiveRows.Row row, Graphics g) {
        var theme = row.owner.context().theme();
        int hair = ThemePainting.metric(row.owner.context(), DesignMetrics.Role.HAIRLINE);
        g.setColor(DerivedColor.SURFACE_TABLE_HEADER.color(theme));
        g.fillRect(0, 0, row.getWidth(), Math.max(0, row.getHeight() - hair));
        g.setColor(DerivedColor.BORDER_ROW.color(theme));
        g.fillRect(0, row.getHeight() - hair, row.getWidth(), hair);
    }

    /** A Run window heading's title panel: the heading at its start and the count, its last child, at its end. */
    private static final class HeadingLayout implements LayoutManager {
        private final int gap;

        HeadingLayout(int gap) { this.gap = gap; }

        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }

        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = 0, height = 0, n = parent.getComponentCount();
            for (int i = 0; i < n; i++) {
                var size = parent.getComponent(i).getPreferredSize();
                width += size.width; height = Math.max(height, size.height);
            }
            return new Dimension(width + Math.max(0, n - 1) * gap, height);
        }

        @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, preferredLayoutSize(parent).height); }

        @Override public void layoutContainer(Container parent) {
            int n = parent.getComponentCount(), w = parent.getWidth(), h = parent.getHeight();
            if (n == 0) return;
            int count = 0;
            if (n > 1) {
                var last = parent.getComponent(n - 1);
                count = Math.min(last.getPreferredSize().width, w);
                last.setBounds(w - count, 0, count, h);
            }
            var heading = parent.getComponent(0);
            heading.setBounds(0, 0, Math.max(0, Math.min(heading.getPreferredSize().width, w - (n > 1 ? count + gap : 0))), h);
        }
    }

    /** The help tile's edge on {@code kit}'s dialog surface: {@code accent.edge}, guarded as an indicator. */
    public static PaintPair tileEdge(Kit kit) {
        return new PaintPair(DerivedColor.ACCENT_EDGE, kit.colors().dialogSurface(), SurfaceContrast.Guard.INDICATOR);
    }

    /** The help row's layout: the tile, then its text beside it at the Run window's gap, the shorter centred on the other. */
    static LayoutManager helpLayout(Stacks.StackControl owner) { return new HelpLayout(owner); }

    /** The help row's children: its glyph takes the accent's ink, and its text reads from the tile instead of centring. */
    static void helpChildren(List<ControlRegistry.Control> controls) {
        for (var control : controls) {
            if (control.component instanceof Labels.LiteralLabel label) label.wrapped.centered = false;
            if (control instanceof Labels.IconControl icon && !(icon.inked && icon.ink == InteractiveRows.Ink.ACCENT)) {
                icon.inked = true; icon.ink = InteractiveRows.Ink.ACCENT; icon.refresh();
            }
        }
    }

    /** The help tile: the accent inside a hairline of {@link #tileEdge(Kit)} at the prompt tile's radius. */
    static void paintTile(Stacks.Tile tile, Graphics2D g) {
        var context = tile.context;
        ThemePainting.antialias(g);
        int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
        double radius = ThemePainting.metric(context, DesignMetrics.Role.PROMPT_TILE_RADIUS), inner = Math.max(0, radius - line);
        var outerShape = new RoundRectangle2D.Double(0, 0, tile.getWidth(), tile.getHeight(), 2 * radius, 2 * radius);
        var innerShape = new RoundRectangle2D.Double(line, line, tile.getWidth() - 2.0 * line, tile.getHeight() - 2.0 * line, 2 * inner, 2 * inner);
        g.setColor(ThemePainting.color(context, SemanticColor.ACCENT)); g.fill(innerShape);
        var ring = new Area(outerShape); ring.subtract(new Area(innerShape));
        g.setColor(SurfaceContrast.painted(context.theme(), tileEdge(context.kit()))); g.fill(ring);
    }

    /** The help row: the tile, then the text beside it; the text block centred on the tile when shorter, and the reverse. */
    private static final class HelpLayout implements Composition.WidthLayout {
        private final Stacks.StackControl owner;

        HelpLayout(Stacks.StackControl owner) { this.owner = owner; }

        private int gap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.RUN_WINDOW_TILE_GAP); }

        private List<Component> lines(Container parent) {
            return Arrays.stream(parent.getComponents()).filter(c -> c.isVisible() && c != owner.tile).toList();
        }

        private int room(int width) { return Math.max(0, width - owner.tile.getPreferredSize().width - gap()); }

        private int text(Container parent, int width) {
            var lines = lines(parent);
            int height = 0;
            for (int i = 0; i < lines.size(); i++)
                height += (i > 0 ? ThemePainting.metric(owner.context(), DesignMetrics.Role.DESCRIPTION_GAP) : 0) + Composition.height(lines.get(i), room(width));
            return height;
        }

        @Override public int height(Container parent, int width) { return Math.max(owner.tile.getPreferredSize().height, text(parent, width)); }

        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = owner.tile.getPreferredSize().width + gap() + lines(parent).stream().mapToInt(c -> c.getPreferredSize().width).max().orElse(0);
            return new Dimension(width, height(parent, parent.getWidth() > 0 ? parent.getWidth() : width));
        }

        @Override public void layoutContainer(Container parent) {
            var size = owner.tile.getPreferredSize();
            int textHeight = text(parent, parent.getWidth()), y = Math.max(0, (size.height - textHeight) / 2), width = room(parent.getWidth());
            owner.tile.setBounds(0, Math.max(0, (textHeight - size.height) / 2), size.width, size.height);
            for (var line : lines(parent)) {
                int height = Composition.height(line, width);
                line.setBounds(size.width + gap(), y, width, height);
                y += height + ThemePainting.metric(owner.context(), DesignMetrics.Role.DESCRIPTION_GAP);
            }
        }
    }
}
```

Then wire the flag through the controls:

```bash
python3 -I - <<'PY'
"""Plan 8a Task 3 step 5: KitView.runWindow and the controls that read it."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitView.java", [('''    private boolean titleStrip;
''', '''    private boolean titleStrip;
    /**
     * App-only: this view is part of the Run window (Enterprise presentation spec 11.2). Its RESULTS lists take the Run
     * window's rows, heading bands and counts, its PROMPT_HEADER tile the Run window's tile, its transparent fields the
     * data face and no ring of their own (the host's field box shows focus), and its text the Run window's colours.
     * Re-reconciles the current descriptor. EDT confined.
     */
    public void runWindow(boolean value) {
        requireOpen();
        if (runWindow == value) return;
        runWindow = value; update(spec);
    }
    /** Whether this view is presented as part of the Run window. */
    public boolean runWindow() { return runWindow; }
    private boolean runWindow;
''', 1)])
edit(S + "Labels.java", [
    ('''            ((LiteralLabel)label).wrapped.centeredLeading = supporting;
''', '''            ((LiteralLabel)label).wrapped.centeredLeading = supporting;
            if (view.runWindow()) RunWindowPresentation.text(context(), text, (LiteralLabel) label, inked);
''', 1),
    ('''            int size = com.formdev.flatlaf.util.UIScale.scale(glyphOverride > 0 ? glyphOverride : context().icons().size(context().metrics(), icon.size()));''',
     '''            int size = com.formdev.flatlaf.util.UIScale.scale(glyphOverride > 0 ? glyphOverride
                : view.runWindow() && icon.size() == Nodes.IconSize.PROMPT ? context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.RUN_WINDOW_TILE_GLYPH)
                : context().icons().size(context().metrics(), icon.size()));''', 1),
    ('''int extent = com.formdev.flatlaf.util.UIScale.scale(context().icons().size(context().metrics(), Nodes.IconSize.SWATCH));''',
     '''int extent = com.formdev.flatlaf.util.UIScale.scale(glyphOverride > 0 ? glyphOverride : context().icons().size(context().metrics(), Nodes.IconSize.SWATCH));''', 1)])
edit(S + "Stacks.java", [
    ('''            state();
            var stack = (Nodes.Stack) node;
''', '''            state();
            var stack = (Nodes.Stack) node;
            // The Run window's help tile (Enterprise presentation plan 8a) is its own size.
            tile.sizeRole = view.runWindow() ? DesignMetrics.Role.RUN_WINDOW_TILE : DesignMetrics.Role.PROMPT_TILE;
            tile.run = view.runWindow();
''', 1),
    ('''                            ? new PromptLayout(this)
''', '''                            ? view.runWindow() ? RunWindowPresentation.helpLayout(this) : new PromptLayout(this)
''', 1),
    ('''            children = List.copyOf(controls);
''', '''            children = List.copyOf(controls);
            if (view.runWindow() && ((Nodes.Stack) node).role() == Nodes.LayoutRole.PROMPT_HEADER) RunWindowPresentation.helpChildren(controls);
''', 1),
    ('''        final KitContext context;
        final DesignMetrics.Role sizeRole;
''', '''        final KitContext context;
        /** The tile's size: the prompt tile, or the Run window's (set by its stack on every refresh). */
        DesignMetrics.Role sizeRole;
        /** The Run window's help tile (plan 8a): the accent tile with its edge (set by its stack on every refresh). */
        boolean run;
''', 1),
    ('''            try {
                ThemePainting.antialias(g);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_CONTROL));
                int radius = ThemePainting.metric(context, DesignMetrics.Role.PROMPT_TILE_RADIUS);''',
     '''            try {
                if (run) { RunWindowPresentation.paintTile(this, g); return; }
                ThemePainting.antialias(g);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_CONTROL));
                int radius = ThemePainting.metric(context, DesignMetrics.Role.PROMPT_TILE_RADIUS);''', 1)])
edit(S + "ItemLists.java", [
    ('''            return ResultMetrics.height(context(), value().items().get(index).heading());
''', '''            if (results() && view.runWindow()) return RunWindowPresentation.rowHeight(context(), value().items().get(index).heading());
            return ResultMetrics.height(context(), value().items().get(index).heading());
''', 1),
    ('''            return results() ? ResultMetrics.height(context(), false) : rowHeight;''',
     '''            return results() && view.runWindow() ? RunWindowPresentation.rowHeight(context(), false) : results() ? ResultMetrics.height(context(), false) : rowHeight;''', 1),
    ('''                glyph.glyph(panelCompact ? context().metrics().value(DesignMetrics.Role.PANEL_COMPACT_ROW_GLYPH) : 0);''',
     '''                glyph.glyph(results() && view.runWindow() && icon.size() == Nodes.IconSize.SWATCH ? context().metrics().value(DesignMetrics.Role.RUN_WINDOW_SWATCH)
                    : panelCompact ? context().metrics().value(DesignMetrics.Role.PANEL_COMPACT_ROW_GLYPH) : 0);''', 1),
    ('''            row.tooltip(item.tooltip());
''', '''            if (results() && view.runWindow()) RunWindowPresentation.row(context(), row, item, selected.contains(item.id()));
            row.tooltip(item.tooltip());
''', 1)])
edit(S + "InteractiveRows.java", [
    ('''        void headingLayout() {
''', '''        void headingLayout() {
            if (owner.resultsLayout() && owner.view.runWindow()) { RunWindowPresentation.headingLayout(this); return; }
''', 1),
    ('''            int side = ThemePainting.metric(context, DesignMetrics.Role.RESULTS_ROW_PADDING),
                    gap = ThemePainting.metric(context, DesignMetrics.Role.RESULTS_ROW_GAP),
                    icon = ThemePainting.metric(context, DesignMetrics.Role.ICON_RAIL), h = getHeight();
            leading.setBounds(side, 0, icon, h);
            int start = side + icon + gap, end = getWidth() - side;''',
     '''            // The Run window's rows (plan 8a): its inset, its hue square's column and its gaps.
            boolean run = owner.view.runWindow();
            int side = ThemePainting.metric(context, run ? DesignMetrics.Role.RUN_WINDOW_ROW_SIDE : DesignMetrics.Role.RESULTS_ROW_PADDING),
                    gap = ThemePainting.metric(context, run ? DesignMetrics.Role.RUN_WINDOW_DETAIL_GAP : DesignMetrics.Role.RESULTS_ROW_GAP),
                    icon = ThemePainting.metric(context, run ? DesignMetrics.Role.RUN_WINDOW_SWATCH : DesignMetrics.Role.ICON_RAIL), h = getHeight();
            leading.setBounds(side, 0, icon, h);
            int start = side + icon + (run ? ThemePainting.metric(context, DesignMetrics.Role.RUN_WINDOW_ROW_GAP) : gap), end = getWidth() - side;''', 1),
    ('''            if (!owner.dataRow(index)) {
                // A heading after other rows opens its section with a hairline rule''',
     '''            if (!owner.dataRow(index)) {
                if (owner.resultsLayout() && owner.view.runWindow()) { RunWindowPresentation.paintHeading(this, g); return; }
                // A heading after other rows opens its section with a hairline rule''', 1)])
edit(S + "TextFields.java", [
    ('''field.presentation() == Nodes.FieldPresentation.TECHNICAL ? Typography.Role.MONO''',
     '''(field.presentation() == Nodes.FieldPresentation.TECHNICAL || view.runWindow() && field.presentation() == Nodes.FieldPresentation.TRANSPARENT) ? Typography.Role.MONO''', 1),
    ('''            surface.transparent = field.presentation() == Nodes.FieldPresentation.TRANSPARENT;
''', '''            surface.transparent = field.presentation() == Nodes.FieldPresentation.TRANSPARENT;
            surface.quiet = surface.transparent && view.runWindow();
''', 1),
    ('''            surface.setBorder(new EmptyBorder(ring * 2, padding, ring * 2, padding));''',
     '''            surface.setBorder(surface.quiet ? new EmptyBorder(0, 0, 0, 0) : new EmptyBorder(ring * 2, padding, ring * 2, padding));''', 1),
    ('''        boolean transparent;
''', '''        boolean transparent;
        /** A Run window's query (plan 8a): its host's field box paints the edge and the focus, so this surface paints nothing. */
        boolean quiet;
''', 1),
    # The quiet surface paints nothing: its host's field box shows the edge and the focus.
    ('''            try {
                if (transparent && focus.visible() && !error) ThemePainting.transparentFocus(''',
     '''            try {
                if (quiet) { putClientProperty("Jasper.kit.halo", null); return; }
                if (transparent && focus.visible() && !error) ThemePainting.transparentFocus(''', 1)])
PY
```

The anchors are `main`'s, without plan 4b. Under `runWindow` the PROMPT_HEADER stack lays its tile beside its text with
`RunWindowPresentation.helpLayout`, inks the glyph on the accent and stops centring the text (`helpChildren`), and the
tile paints itself through `paintTile` (R2); the quiet surface returns first in `FieldSurface.paintComponent` (R3).
At the merge with 4b, keep each `runWindow` branch ahead of the face it bypasses (`context().faces().prompt.headerLayout`,
the face's `paintTile`, and `paintDefault`'s first statement for the quiet surface).

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.*' --tests 'dev.jasper.ui.swing.RunWindowPresentationTest' --tests 'dev.jasper.ui.swing.ResultsKitTest' --tests 'dev.jasper.ui.icons.IconRegistryTest' --tests 'dev.jasper.ui.gallery.*' :jasper-sdk-testkit:test :jasper-ui:javadoc`
Expected: PASS. `RunWindowRolesTest` 2, `RunWindowPresentationTest` 3; the frozen-table tests (`DesignMetricsTest`,
`ClassicCatalogTest`, `TypeScaleTest`, `ClassicTypeTest`, `TypographyTest`, `NewRolesTest`) read the appended rows and
names; `ResultsKitTest` and the gallery tests are unchanged (the dry run: 64 metrics, presentation, results and icon
tests pass; the whole jasper-ui suite fails only `GalleryCoverageTest.shippedJarExcludesGalleryAndTestSupport`, which
needs Gradle's `jasper.uiJar`, as before). If `theListTakesTheRunWindowsRowsBandsAndHueSquares` reports
the count's right edge or the title's start one pixel off, the title panel or a label carries an inset: subtract it in
`RunWindowPresentation.headingLayout` or its `HeadingLayout` (never change the test's expected value). The help glyph's
raster covers its interior at 254 of 255 and is compared within two levels a channel (R5).

- [ ] **Step 7: Commit**

```bash
git add jasper-ui/src jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKit.java
git commit -F - <<'EOF'
Run window: the kit's Run window roles and presentation flag

Thirty RUN_WINDOW_* metric roles measured on the 06 Run Dialog mocks, the
RUN_HEADING and RUN_DETAIL type roles, and the filled play and down-arrow
glyphs. KitView.runWindow(true) gives a view's results its 21 px rows,
banded headings with a count, 7 px hue squares and details cut at their
end, its prompt header the 32 px accent help tile beside its text, its
transparent field the data face without a ring, and its text the mocks'
colours. Views without the flag are unchanged. The test fixtures gain plan
4c's Enterprise Light fixture theme.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: jasper-ui: the Run window's surfaces

`SurfacePanel` gains three kinds the Run window mounts its views on (spec 11.2, 10.8's frame): `RUN_WINDOW` (the frame in
`border.hover` at `RUN_WINDOW_RADIUS` on the kit's dialog surface, a title band on `surface.header.active` above a
`border.window` rule, a footer band on `surface.footer` under one), `FIELD` (an input's box at `FIELD_RADIUS` around a
composite of views, its edge and halo following focus inside it) and `BOX` (a list's box: `surface.card` inside a 1 px
`border.control`). The default look builds none of them. With the surfaces in place, `PaintedContrastTest` paints the
Run window's declared pairs (Task 3, Rulings 8) and the help tile's edge on them over every palette (spec 16.1.2), as
plan 7 does for `ChromePainter.DOCK_PAIRS`.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/SurfacePanel.java:4,15-36,67-69,73`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/RunWindowSurfacesTest.java`
- Modify (test): `jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java` (one test and two helpers, before `searchHint`)

**Interfaces:**
- Consumes: Task 3's roles and `RunWindowPresentation.pairs(Kit)`, `.authored()`, `.tileEdge(Kit)`; 4a's `KitContext.kit().colors().dialogSurface()`;
  `KitFixtures.enterprise()`, `.enterpriseLight()`, `.render(…)`; `PaintedContrastTest`'s `palettes()` (the theme set and
  the degenerate palettes) and `name(theme)`.
- Produces: `SurfacePanel.Kind.RUN_WINDOW`, `FIELD`, `BOX`; `public SurfacePanel(KitContext, Kind, IntSupplier header,
  IntSupplier footer)`; `public void focusShown(Boolean shown)` (null follows keyboard focus; headless renders force it);
  `public boolean focusWithin()`.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/RunWindowSurfacesTest.java`:

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Enterprise presentation plan 8a: the Run window's frame and bands, its field box and its list box. */
class RunWindowSurfacesTest {
    static BufferedImage paint(SurfacePanel panel, int width, int height) {
        panel.setSize(width, height);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { panel.paint(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y)); }

    @Test void theRunWindowIsAFramedDialogWithATitleBandAndAFooterBand() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var theme = context.theme();
            var window = new SurfacePanel(context, SurfacePanel.Kind.RUN_WINDOW, () -> 24, () -> 40);
            var image = paint(window, 462, 427);
            assertThat(window.getInsets()).isEqualTo(new java.awt.Insets(1, 1, 1, 1));
            assertThat(at(image, 200, 0)).as("the frame").isEqualTo(DerivedColor.BORDER_HOVER.color(theme));
            assertThat(at(image, 0, 200)).isEqualTo(DerivedColor.BORDER_HOVER.color(theme));
            assertThat(at(image, 200, 12)).as("the title band").isEqualTo(DerivedColor.SURFACE_HEADER_ACTIVE.color(theme));
            assertThat(at(image, 200, 25)).as("its rule").isEqualTo(theme.semantic().color(SemanticColor.BORDER_WINDOW));
            assertThat(at(image, 200, 200)).as("the dialog surface").isEqualTo(theme.semantic().color(context.kit().colors().dialogSurface()));
            assertThat(at(image, 200, 427 - 1 - 40 - 1)).as("the footer's rule").isEqualTo(theme.semantic().color(SemanticColor.BORDER_WINDOW));
            assertThat(at(image, 200, 427 - 20)).as("the footer band").isEqualTo(DerivedColor.SURFACE_FOOTER.color(theme));
            assertThat(image.getRGB(0, 0) >>> 24).as("a rounded corner").isLessThan(255);
            assertThat(window.contains(1, 1)).as("the whole rectangle takes presses").isTrue();
        });
    }

    @Test void theFieldBoxShowsFocusWithAnAccentEdgeAndAHalo() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var palette = context.theme().semantic();
            var field = new SurfacePanel(context, SurfacePanel.Kind.FIELD);
            int ring = context.metrics().value(DesignMetrics.Role.FOCUS_RING);
            assertThat(field.getInsets()).isEqualTo(new java.awt.Insets(ring + 1, ring + 1, ring + 1, ring + 1));
            field.focusShown(false);
            var idle = paint(field, 392, 26);
            assertThat(at(idle, ring, 13)).as("the idle edge").isEqualTo(palette.color(SemanticColor.BORDER_INPUT));
            assertThat(at(idle, 100, 13)).isEqualTo(palette.color(SemanticColor.SURFACE_INPUT));
            assertThat(idle.getRGB(0, 13) >>> 24).as("no halo").isZero();
            field.focusShown(true);
            var focused = paint(field, 392, 26);
            assertThat(at(focused, ring, 13)).as("the focused edge").isEqualTo(palette.color(SemanticColor.BORDER_FOCUS));
            var halo = new Color(focused.getRGB(0, 13), true);
            var expected = ColorMath.opacity(palette.color(SemanticColor.BORDER_FOCUS), ThemePainting.focusHaloShare(context, false));
            // A translucent fill stored unpremultiplied rounds its channels: the alpha is exact, each channel within two levels.
            assertThat(halo.getAlpha()).as("the halo's share").isEqualTo(expected.getAlpha());
            assertThat(List.of(halo.getRed() - expected.getRed(), halo.getGreen() - expected.getGreen(), halo.getBlue() - expected.getBlue()))
                .as("the halo's colour").allMatch(difference -> Math.abs(difference) <= 2);
            field.focusShown(null);
            assertThat(field.focusWithin()).as("nothing focused headless").isFalse();
        });
    }

    @Test void theListBoxIsACardInsideAControlEdge() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var palette = context.theme().semantic();
            var box = new SurfacePanel(context, SurfacePanel.Kind.BOX);
            var image = paint(box, 436, 208);
            assertThat(box.getInsets()).isEqualTo(new java.awt.Insets(1, 1, 1, 1));
            assertThat(at(image, 0, 0)).as("square").isEqualTo(palette.color(SemanticColor.BORDER_CONTROL));
            assertThat(at(image, 435, 100)).isEqualTo(palette.color(SemanticColor.BORDER_CONTROL));
            assertThat(at(image, 100, 100)).isEqualTo(palette.color(SemanticColor.SURFACE_CARD));
        });
    }
}
```

Then add the painted-contrast case to jasper-app's `PaintedContrastTest`, before `searchHint` (plan 7 inserts its dock
case at the same place, and plan 4b adds its face pairs elsewhere in the class; the anchor stays unique):

```bash
python3 -I - <<'PY'
"""Plan 8a Task 4 step 1: the Run window's declared pairs over every palette (spec 16.1.2)."""
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java"); text = path.read_text(encoding="utf-8")
anchor = '''    private static boolean searchHint(GeneratedTheme theme, Color expected) {'''
if text.count(anchor) != 1: raise SystemExit("PaintedContrastTest: searchHint moved")
block = '''    /**
     * Plan 8a and spec 16.1.2: the Run window paints each pair RunWindowPresentation declares as its guard makes it, and
     * each unguarded pair as authored, on the surfaces the pairs name, over every palette; the help tile's edge too.
     */
    @Test void theRunWindowPaintsEachPairAsItsGuardMakesItOverEveryPalette() throws Exception {
        record Sampled(String name, KitView view, String id) { }
        var enterprise = KitFixtures.enterprise();
        var initial = KitFixtures.enterpriseLight();
        var original = UIManager.getLookAndFeel();
        try (var binding = new ThemeBinding(new ThemePair(initial, initial), failure -> { throw new AssertionError(failure); });
             var icons = new IconRegistry()) {
            var kit = KitContext.of(enterprise, binding, ThemeRoute.CHROME, icons, () -> false, action -> "");
            var texts = new ViewSpec(new Nodes.Stack("run", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                runText("run.title", "Run", Nodes.TextRole.WINDOW_TITLE), runText("run.shortcut", "\\u21e7\\u2318P", Nodes.TextRole.CAPTION),
                runText("run.label", "Open:", Nodes.TextRole.LABEL), runText("run.value", "dustin@192.168.1.119:22", Nodes.TextRole.MONO)), ""));
            var list = new ViewSpec(new Nodes.ItemList("results", ControlState.READY, Nodes.ListKind.RESULTS, List.of(
                new ListItem("h", Optional.empty(), List.of(runText("h.title", "Matches", Nodes.TextRole.CAPTION),
                    runText("h.count", "1 of 2", Nodes.TextRole.CAPTION)), List.of(), List.of(), true),
                new ListItem("a", Optional.empty(), List.of(runText("a.title", "Mac", Nodes.TextRole.BODY)),
                    List.of(runText("a.detail", "dustin@host", Nodes.TextRole.MONO)), List.of(runText("a.tag", "K", Nodes.TextRole.CAPTION))),
                new ListItem("b", Optional.empty(), List.of(runText("b.title", "gcloud auth", Nodes.TextRole.BODY)),
                    List.of(runText("b.detail", "gcloud auth login", Nodes.TextRole.MONO)), List.of(runText("b.tag", "K", Nodes.TextRole.CAPTION))),
                new ListItem("m", Optional.empty(), List.of(runText("m.title", "More in SSH", Nodes.TextRole.DESCRIPTION)), List.of(), List.of())),
                java.util.Set.of("a"), "run", List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), java.util.OptionalInt.of(4)));
            var help = new ViewSpec(new Nodes.Stack("run.help", ControlState.READY, Nodes.LayoutRole.PROMPT_HEADER, List.of(
                new Nodes.Icon("run.help.icon", ControlState.READY, "play.filled", Nodes.Hue.BLUE, Nodes.IconSize.PROMPT, "Run"),
                runText("run.help.text", "Type the name of a session.", Nodes.TextRole.DESCRIPTION)), ""));
            try (var textView = new KitView(kit, texts, event -> { }); var listView = new KitView(kit, list, event -> { });
                 var helpView = new KitView(kit, help, event -> { })) {
                var guarded = List.of(new Sampled("title", textView, "run.title"), new Sampled("shortcut", textView, "run.shortcut"),
                    new Sampled("label", textView, "run.label"), new Sampled("detail value", textView, "run.value"),
                    new Sampled("heading", listView, "h.title"), new Sampled("selected title", listView, "a.title"),
                    new Sampled("list text", listView, "m.title"));
                var authored = List.of(new Sampled("row detail", listView, "b.detail"), new Sampled("count", listView, "h.count"),
                    new Sampled("selected detail", listView, "a.detail"));
                var pairs = RunWindowPresentation.pairs(enterprise);
                assertThat(guarded).as("every declared Run window pair is sampled").hasSameSizeAs(pairs);
                assertThat(authored).as("every unguarded Run window pair is sampled").hasSameSizeAs(RunWindowPresentation.authored());
                var window = new SurfacePanel(kit, SurfacePanel.Kind.RUN_WINDOW, () -> 24, () -> 40);
                var box = new SurfacePanel(kit, SurfacePanel.Kind.BOX);
                var field = new SurfacePanel(kit, SurfacePanel.Kind.FIELD);
                field.focusShown(false);
                for (var theme : palettes()) {
                    FlatLafInstaller.install(theme, enterprise); binding.publish(new ThemePair(theme, theme));
                    for (var view : List.of(textView, listView, helpView)) {
                        SwingUtilities.updateComponentTreeUI(view);
                        // Re-reconciles under this theme, so every control refreshes its colours.
                        view.runWindow(false); view.runWindow(true);
                    }
                    var rendered = KitFixtures.render(listView, 436, 100, 1);
                    String where = name(theme);
                    // The surfaces the pairs name are the ones painted under them.
                    var frame = surface(window, 462, 427);
                    assertThat(frame.getRGB(200, 12)).as(where + ", title band").isEqualTo(DerivedColor.SURFACE_HEADER_ACTIVE.color(theme).getRGB());
                    assertThat(frame.getRGB(200, 200)).as(where + ", dialog surface").isEqualTo(enterprise.colors().dialogSurface().color(theme).getRGB());
                    assertThat(surface(box, 436, 208).getRGB(100, 100)).as(where + ", list box").isEqualTo(theme.semantic().color(SURFACE_CARD).getRGB());
                    assertThat(surface(field, 392, 26).getRGB(100, 13)).as(where + ", field box").isEqualTo(theme.semantic().color(SURFACE_INPUT).getRGB());
                    assertThat(rendered.getRGB(200, 5)).as(where + ", heading band").isEqualTo(DerivedColor.SURFACE_TABLE_HEADER.color(theme).getRGB());
                    assertThat(rendered.getRGB(300, 19 + 10)).as(where + ", selected row").isEqualTo(theme.semantic().color(ACCENT).getRGB());
                    // The help tile's edge, an indicator on the dialog surface.
                    var helpImage = KitFixtures.render(helpView, 436, 40, 1);
                    var tile = KitTestSupport.require(helpView, "run.help.tile", JComponent.class);
                    var tileAt = SwingUtilities.convertRectangle(tile.getParent(), tile.getBounds(), helpView);
                    var edge = RunWindowPresentation.tileEdge(enterprise);
                    Color painted = SurfaceContrast.painted(theme, edge);
                    assertThat(edge.guard().holds(painted, edge.against().color(theme))).as(where + ", the help tile's edge meets its guard").isTrue();
                    assertThat(helpImage.getRGB(tileAt.x + tileAt.width / 2, tileAt.y)).as(where + ", the help tile's edge").isEqualTo(painted.getRGB());
                    for (int i = 0; i < guarded.size(); i++) {
                        var pair = pairs.get(i);
                        var sample = guarded.get(i);
                        Color expected = SurfaceContrast.painted(theme, pair);
                        assertThat(pair.guard().holds(expected, pair.against().color(theme))).as(where + ", " + sample.name() + " meets its guard").isTrue();
                        assertThat(KitTestSupport.require(sample.view(), sample.id(), JLabel.class).getForeground())
                            .as(where + ", " + sample.name() + " is painted " + ColorMath.hex(expected)).isEqualTo(expected);
                    }
                    for (int i = 0; i < authored.size(); i++) {
                        var pair = RunWindowPresentation.authored().get(i);
                        var sample = authored.get(i);
                        assertThat(KitTestSupport.require(sample.view(), sample.id(), JLabel.class).getForeground())
                            .as(where + ", " + sample.name() + " as authored").isEqualTo(pair.slot().color(theme));
                    }
                }
            }
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static Nodes.Text runText(String id, String value, Nodes.TextRole role) { return new Nodes.Text(id, ControlState.READY, value, role); }

    /** {@code panel} painted at {@code width} by {@code height}. */
    private static BufferedImage surface(SurfacePanel panel, int width, int height) {
        panel.setSize(width, height);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { panel.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }

'''
path.write_text(text.replace(anchor, block + anchor), encoding="utf-8")
PY
```

The guarded samples follow `pairs(Kit)`'s order (TITLE, SHORTCUT, label, value, HEADING, SELECTED_TITLE, LIST_TEXT) and
the unguarded ones `authored()`'s (ROW_DETAIL, COUNT, SELECTED_DETAIL). `PaintedContrastTest` already imports
`dev.jasper.ui.model.*`, `dev.jasper.ui.swing.*`, `dev.jasper.ui.theme.*`, `KitFixtures`, `KitTestSupport`, `IconRegistry`,
`java.awt.*`, `java.awt.image.BufferedImage`, `javax.swing.*` and `java.util.Optional`, and statically `SemanticColor.*`.
The Java source spells the shortcut as `⇧⌘P` (the script's `\\u` writes one backslash).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.RunWindowSurfacesTest'`
Expected: FAIL: `compileTestJava` reports "cannot find symbol … RUN_WINDOW", "… focusShown" and the four-argument constructor.

- [ ] **Step 3: The three kinds**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 8a Task 4 step 3: SurfacePanel's RUN_WINDOW, FIELD and BOX kinds."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/SurfacePanel.java", [
    ('''    /** Floating rounded hit shape, inset content with a square header, and rectangular-hit notification. */
    public enum Kind { POPUP, CONTENT, NOTICE }''',
     '''    /**
     * Floating rounded hit shape, inset content with a square header, and rectangular-hit notification; the Run window's
     * framed dialog with a title band and a footer band, its input box around a composite of views, and its list box
     * (Enterprise presentation plan 8a).
     */
    public enum Kind { POPUP, CONTENT, NOTICE, RUN_WINDOW, FIELD, BOX }''', 1),
    ('''    private final IntSupplier header;
    private SemanticColor fill;''', '''    private final IntSupplier header, footer;
    private SemanticColor fill;
    /** A FIELD's forced focus look for headless renders; null follows the keyboard focus. */
    private Boolean focusShown;
    private final java.beans.PropertyChangeListener focusWatch = event -> repaint();''', 1),
    ('''    public SurfacePanel(KitContext context, Kind kind, IntSupplier header) {
        super(new BorderLayout());
        this.context = Objects.requireNonNull(context);
        this.kind = Objects.requireNonNull(kind);
        this.header = Objects.requireNonNull(header);''', '''    public SurfacePanel(KitContext context, Kind kind, IntSupplier header) { this(context, kind, header, () -> 0); }

    /**
     * Creates a surface whose title band ({@code header}) and footer band ({@code footer}) heights its owner measures;
     * only a RUN_WINDOW paints the bands.
     */
    public SurfacePanel(KitContext context, Kind kind, IntSupplier header, IntSupplier footer) {
        super(new BorderLayout());
        this.context = Objects.requireNonNull(context);
        this.kind = Objects.requireNonNull(kind);
        this.header = Objects.requireNonNull(header);
        this.footer = Objects.requireNonNull(footer);
        if (kind == Kind.RUN_WINDOW || kind == Kind.FIELD || kind == Kind.BOX) setBorder(new AbstractBorder() {
            @Override public Insets getBorderInsets(Component c, Insets insets) {
                int line = metric(DesignMetrics.Role.HAIRLINE) + (kind == Kind.FIELD ? metric(DesignMetrics.Role.FOCUS_RING) : 0);
                insets.set(line, line, line, line); return insets;
            }
            @Override public Insets getBorderInsets(Component c) { return getBorderInsets(c, new Insets(0, 0, 0, 0)); }
        });''', 1),
    ('''    @Override public boolean contains(int x,int y) {
        return kind == Kind.NOTICE || kind == Kind.CONTENT && y < surfaceTop() ? super.contains(x,y) : shape(0).contains(x,y);
    }''', '''    @Override public boolean contains(int x,int y) {
        return kind == Kind.NOTICE || kind == Kind.RUN_WINDOW || kind == Kind.FIELD || kind == Kind.BOX
            || kind == Kind.CONTENT && y < surfaceTop() ? super.contains(x,y) : shape(0).contains(x,y);
    }

    /** For headless renders: true or false forces a FIELD's focused or idle look; null follows the keyboard focus. */
    public void focusShown(Boolean shown) { focusShown = shown; repaint(); }

    /** Whether keyboard focus is inside this surface, or the forced look says it is. */
    public boolean focusWithin() {
        if (focusShown != null) return focusShown;
        var owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        return owner != null && SwingUtilities.isDescendingFrom(owner, this);
    }

    @Override public void addNotify() {
        super.addNotify();
        if (kind == Kind.FIELD) KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", focusWatch);
    }

    @Override public void removeNotify() {
        if (kind == Kind.FIELD) KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("permanentFocusOwner", focusWatch);
        super.removeNotify();
    }

    /** The Run window's frame and bands, its field box or its list box; false for the other kinds. */
    private boolean paintRunKind(Graphics2D g) {
        int hair = metric(DesignMetrics.Role.HAIRLINE), w = getWidth(), h = getHeight();
        var theme = context.theme();
        switch (kind) {
            case RUN_WINDOW -> {
                double arc = 2.0 * metric(DesignMetrics.Role.RUN_WINDOW_RADIUS), inner = Math.max(0, arc - 2.0 * hair);
                var outerShape = new RoundRectangle2D.Double(0, 0, w, h, arc, arc);
                var innerShape = new RoundRectangle2D.Double(hair, hair, w - 2.0 * hair, h - 2.0 * hair, inner, inner);
                var body = (Graphics2D) g.create();
                try {
                    body.clip(innerShape);
                    body.setColor(ThemePainting.color(context, context.kit().colors().dialogSurface()));
                    body.fillRect(0, 0, w, h);
                    int title = header.getAsInt(), foot = footer.getAsInt();
                    if (title > 0) {
                        body.setColor(DerivedColor.SURFACE_HEADER_ACTIVE.color(theme)); body.fillRect(0, hair, w, title);
                        body.setColor(ThemePainting.color(context, SemanticColor.BORDER_WINDOW)); body.fillRect(0, hair + title, w, hair);
                    }
                    if (foot > 0) {
                        int top = h - hair - foot;
                        body.setColor(DerivedColor.SURFACE_FOOTER.color(theme)); body.fillRect(0, top, w, foot);
                        body.setColor(ThemePainting.color(context, SemanticColor.BORDER_WINDOW)); body.fillRect(0, top - hair, w, hair);
                    }
                } finally { body.dispose(); }
                var ring = new Area(outerShape); ring.subtract(new Area(innerShape));
                g.setColor(DerivedColor.BORDER_HOVER.color(theme)); g.fill(ring);
                return true;
            }
            case FIELD -> {
                int focusRing = metric(DesignMetrics.Role.FOCUS_RING);
                double radius = metric(DesignMetrics.Role.FIELD_RADIUS);
                boolean focused = focusWithin();
                var box = new RoundRectangle2D.Double(focusRing, focusRing, w - 2.0 * focusRing, h - 2.0 * focusRing, 2 * radius, 2 * radius);
                if (focused) {
                    var halo = new Area(new RoundRectangle2D.Double(0, 0, w, h, 2 * (radius + focusRing), 2 * (radius + focusRing)));
                    halo.subtract(new Area(box));
                    g.setColor(ColorMath.opacity(ThemePainting.color(context, SemanticColor.BORDER_FOCUS), ThemePainting.focusHaloShare(context, false)));
                    g.fill(halo);
                }
                var inside = new RoundRectangle2D.Double(focusRing + hair, focusRing + hair, w - 2.0 * (focusRing + hair), h - 2.0 * (focusRing + hair),
                    2 * Math.max(0, radius - hair), 2 * Math.max(0, radius - hair));
                var edge = new Area(box); edge.subtract(new Area(inside));
                g.setColor(ThemePainting.color(context, focused ? SemanticColor.BORDER_FOCUS : SemanticColor.BORDER_INPUT)); g.fill(edge);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_INPUT)); g.fill(inside);
                return true;
            }
            case BOX -> {
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_CONTROL)); g.fillRect(0, 0, w, h);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_CARD)); g.fillRect(hair, hair, Math.max(0, w - 2 * hair), Math.max(0, h - 2 * hair));
                return true;
            }
            default -> { return false; }
        }
    }''', 1),
    ('''        try {
            ThemePainting.antialias(g);
            if(kind == Kind.CONTENT) { g.setColor(getBackground());g.fillRect(0,0,getWidth(),getHeight()); }''',
     '''        try {
            ThemePainting.antialias(g);
            if (paintRunKind(g)) return;
            if(kind == Kind.CONTENT) { g.setColor(getBackground());g.fillRect(0,0,getWidth(),getHeight()); }''', 1),
    ('''import dev.jasper.ui.theme.SemanticColor;
''', '''import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
''', 1)])
PY
```

`SurfacePanel` imports `java.awt.*` and `javax.swing.*` (`KeyboardFocusManager`, `SwingUtilities`) and `java.awt.geom.Area`
and `RoundRectangle2D` already (`SurfacePanel.java:5-10`); `ThemePainting.focusHaloShare` is on `main`
(`ThemePainting.java:121`). A FIELD with a square `FIELD_RADIUS` of 0 still draws its edge, as the `Area` difference is
a ring. A translucent halo stored unpremultiplied rounds its channels, so the test holds its alpha exactly and its
channels within two levels (R5).

- [ ] **Step 4: Run the tests to verify they pass, and the gallery to verify nothing moved**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.RunWindowSurfacesTest' --tests 'dev.jasper.ui.swing.*Surface*' :jasper-ui:javadoc :jasper-app:test --tests 'dev.jasper.app.appearance.PaintedContrastTest' --continue`
Expected: PASS (`RunWindowSurfacesTest` 3 tests; the existing surface tests unchanged; `PaintedContrastTest` with
`theRunWindowPaintsEachPairAsItsGuardMakesItOverEveryPalette` over the theme set and the eight degenerate palettes). A
pair that misses its guard on a degenerate palette is a colour painted with its raw role somewhere: route it through
`SurfaceContrast.painted` in `RunWindowPresentation`, never loosen the guard. If a surface sample lands on text, move the
sample, not the surface.

Then prove the pixel rule for Tasks 3 and 4:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
diff -r jasper-ui/build/plan8-base/dark-1x jasper-ui/build/gallery/dark-1x && diff -r jasper-ui/build/plan8-base/light-1x jasper-ui/build/gallery/light-1x && echo IDENTICAL
```

Expected: `IDENTICAL`. Any difference means a default view read a Run window value: find the control and gate it on
`view.runWindow()` (or the new kind) before continuing.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/SurfacePanel.java jasper-ui/src/test/java/dev/jasper/ui/swing/RunWindowSurfacesTest.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java
git commit -F - <<'EOF'
Run window: the kit's frame, field box and list box surfaces

SurfacePanel's RUN_WINDOW kind paints the Run window's frame in the hover
border on the kit's dialog surface, its title band and its footer band;
FIELD paints an input's box around a composite of views with the focused
edge and halo; BOX paints a list's card inside a control edge. The
painted-contrast suite paints the Run window's declared pairs and the help
tile's edge on them over every palette. The headless gallery is
byte-identical.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: `RunWindowView`

The Run window (spec 11.2) as the palette's second view: the title row (Run, the opener's shortcut, ×), the help row (▶
tile and the help text, or the chosen scope's description), "Open:" with the field box holding the chip, the data-face
query and the ▼ scope menu, the bordered list of fixed height with "Recent"/"Most used"/"Suggested" or one "Matches"
heading and "i of n", each row's hue square, title, detail and tag, the "{Noun}: {detail}" line, the "Open in a split
pane" checkbox and the footer's primary verb, Cancel and Browse…. Every pixel comes from Tasks 3 and 4; this class lays
views out. Steps render in the list box through Task 1's `StepForm`.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/palette/RunWindowView.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteView.java` (`sealed … permits CardView, RunWindowView`, and its class comment's `{@link RunWindowView}`)
- Create (tests): `jasper-app/src/test/java/dev/jasper/app/palette/RunWindowFixtures.java`, `RunWindowViewTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (rows for the three new files)

**Interfaces:**
- Consumes: Task 1's `PaletteView`, `PaletteView.Host`, `StepForm`, `QueryEdits`; Task 2's `RunCopy`,
  `PaletteEntry.Header.Kind`; Tasks 3 and 4's `KitView.runWindow`, `SurfacePanel.Kind.RUN_WINDOW`, `FIELD`, `BOX`, the
  `RUN_WINDOW_*` roles, `play.filled` and `triangle.down`; 3b's `IconSize.SWATCH`; 3d's `PaletteScope.hue()`.
- Produces: `public final class RunWindowView extends SurfacePanel implements PaletteView` with `RunWindowView(KitContext,
  boolean macOs, PaletteView.Host)`, the node ids `RunWindowView.TITLE` … `BROWSE` and `SCOPE`, the package-private
  `void handle(UiEvent)` (every region's events) and accessors (`viewFor(String id)` for each region's kit view,
  `entries()`, `selectedIndex()`, `splitTicked()`, `fieldBox()`, `listBox()`, `step()`, `helpText()`, `listHeight()`);
  `RunWindowFixtures` (test) with `scopes()`, `justOpened()`, `typingAuth()`,
  `view(KitContext, List<String> events)` and `host(List<String> events)`.

- [ ] **Step 1: Write the fixtures and the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/palette/RunWindowFixtures.java`:

```java
package dev.jasper.app.palette;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.model.Nodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The 06 Run Dialog mocks' data as palette entries (the mocks' names, hosts and paths are fixtures): the sessions,
 * commands, snippets and files scopes with their nouns, hues and verbs (3d's bundled values), "Just opened" and
 * "Typing auth".
 */
final class RunWindowFixtures {
    private RunWindowFixtures() { }

    static PaletteScope scope(String id, String label, String noun, Nodes.Hue hue, String description, List<PaletteVerb> verbs) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public String description() { return description; }
            @Override public String placeholder() { return "Search " + label; }
            @Override public List<PaletteVerb> verbs() { return verbs; }
            @Override public Nodes.Hue hue() { return hue; }
            @Override public String itemNoun() { return noun; }
            @Override public PaletteResults search(String query, PaletteContext context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    static final PaletteScope SESSIONS = scope("dev.jasper.remote.scope", "SSH", "session", Nodes.Hue.GREEN, "Connect to a saved SSH host",
        List.of(new PaletteVerb("connect", "Connect"), new PaletteVerb("split", "Connect in split", PaletteVerb.Role.SPLIT), new PaletteVerb("edit", "Edit")));
    static final PaletteScope COMMANDS = scope("jasper.commands", "Commands", "command", Nodes.Hue.BLUE, "Run a command",
        List.of(new PaletteVerb("run", "Run")));
    static final PaletteScope SNIPPETS = scope("dev.jasper.snippets.scope", "Snippets", "snippet", Nodes.Hue.PURPLE, "Insert or run a saved command",
        List.of(new PaletteVerb("paste", "Insert"), new PaletteVerb("paste_run", "Insert and run"), new PaletteVerb("edit", "Edit")));
    static final PaletteScope FILES = scope("dev.jasper.indexer.files", "Files", "file", Nodes.Hue.AMBER, "Open a file of an indexed project",
        List.of(new PaletteVerb("open", "Open"), new PaletteVerb("copy-path", "Copy path"), new PaletteVerb("terminal", "Open terminal here")));

    static List<PaletteScope> scopes() { return List.of(COMMANDS, SESSIONS, SNIPPETS, FILES); }

    static PaletteEntry.Item item(PaletteScope scope, String id, String title, String detail, String tag) {
        return new PaletteEntry.Item(scope, new PaletteRow(id, title, detail, tag, null, true, null));
    }

    /** "Run Dialog - Just opened (recent + most used)": three recent rows, then five most used; Mac selected. */
    static List<PaletteEntry> justOpened() {
        return List.of(new PaletteEntry.Header(PaletteEntry.Header.Kind.RECENT, "Recently used"),
            item(SESSIONS, "mac", "Mac", "dustin@192.168.1.119:22", null),
            item(COMMANDS, "install_cli", "Install 'jasper' Command in PATH", "/usr/local/bin/jasper", null),
            item(SNIPPETS, "wiremock", "wiremock run", "./gradlew run --args=\"regression --config regression-suite.yml\"", null),
            new PaletteEntry.Header(PaletteEntry.Header.Kind.USED, "Most used"),
            item(COMMANDS, "new_editor_file", "New Editor File", "Editor", null),
            item(FILES, "settings", "settings.gradle.kts", "moray/settings.gradle.kts", null),
            item(SNIPPETS, "gcloud", "gcloud auth", "gcloud auth login", null),
            item(FILES, "vendor", "VendorDetailsService.java", "src/main/java/com/homedepot/sourcing/orders/VendorDetailsService.java", null),
            item(COMMANDS, "thd_crypto", "THD Crypto: Encrypt / Decrypt…", "THD Sourcing Crypto", "⌃⌥⇧C"));
    }

    /** "Run Dialog - Typing auth (filtered)": one match, a snippet, selected. */
    static List<PaletteEntry> typingAuth() {
        return List.of(new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "Snippets"), item(SNIPPETS, "gcloud", "gcloud auth", "gcloud auth login", null));
    }

    /** A host that records what the view calls back. */
    static PaletteView.Host host(List<String> events) {
        return new PaletteView.Host(query -> events.add("query:" + query),
            (entry, verb) -> events.add("run:" + PaletteTestSupport.describe(entry) + ":" + verb),
            text -> Optional.empty(), scope -> events.add("scope:" + (scope == null ? "all" : scope.id())), RunWindowFixtures::scopes,
            () -> "⇧⌘P", () -> events.add("cancel"), () -> events.add("browse"));
    }

    static RunWindowView view(dev.jasper.ui.swing.KitContext context, List<String> events) {
        var view = new RunWindowView(context, true, host(events));
        view.setVisibleRows(8);
        return view;
    }

    /** Every visible label's text under {@code root}, in component order. */
    static List<String> texts(java.awt.Component root) {
        var texts = new ArrayList<String>();
        collect(root, texts);
        return texts;
    }

    private static void collect(java.awt.Component component, List<String> texts) {
        if (!component.isVisible()) return;
        if (component instanceof javax.swing.JLabel label && label.getText() != null && !label.getText().isEmpty()) texts.add(label.getText());
        else if (component instanceof javax.swing.AbstractButton button && button.getText() != null && !button.getText().isEmpty()) texts.add(button.getText());
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) collect(child, texts);
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/palette/RunWindowViewTest.java`:

```java
package dev.jasper.app.palette;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 11.2 to 11.4: the Run window's regions, copy, primary verb, SPLIT checkbox and fixed list. */
class RunWindowViewTest {
    static KitContext enterprise() { return KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise()); }
    static int m(KitContext context, DesignMetrics.Role role) { return context.metrics().value(role); }

    static void layout(RunWindowView view) {
        view.setSize(view.getPreferredSize());
        dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(view);
    }

    static Nodes.ItemList list(RunWindowView view) { return (Nodes.ItemList) view.viewFor(RunWindowView.RESULTS).spec().root(); }

    static List<String> listTexts(RunWindowView view) {
        var texts = new ArrayList<String>();
        for (ListItem item : list(view).items()) {
            var line = new StringBuilder(item.heading() ? "# " : "");
            // A heading's count is its second title node (Rulings 13).
            for (int i = 0; i < item.title().size(); i++) line.append(i > 0 ? " | " : "").append(((Nodes.Text) item.title().get(i)).text());
            texts.add(line.toString());
        }
        return texts;
    }

    static String text(RunWindowView view, String id) {
        var root = view.viewFor(id).spec().root();
        return switch (root) {
            case Nodes.Text text -> text.text();
            case Nodes.Button button -> button.label();
            case Nodes.Stack stack -> ((Nodes.Text) stack.children().get(1)).text();
            default -> throw new AssertionError(root);
        };
    }

    static boolean enabled(RunWindowView view, String id) { return view.viewFor(id).spec().root().state().enabled(); }

    static List<String> scopeLabels(RunWindowView view) {
        return ((Nodes.MenuButton) view.viewFor(RunWindowView.SCOPES).spec().root()).items().stream().map(dev.jasper.ui.model.MenuItem::label).toList();
    }

    /** Spec 11.2: the live COMMAND_PALETTE keycap and each scope registered by the time the window opens. */
    @Test void openingReadsTheLiveShortcutAndTheScopesRegisteredSince() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var scopes = new ArrayList<PaletteScope>(List.of(RunWindowFixtures.COMMANDS));
            var shortcut = new String[] {""};
            var host = new PaletteView.Host(query -> { }, (entry, verb) -> { }, text -> java.util.Optional.empty(), scope -> { },
                () -> List.copyOf(scopes), () -> shortcut[0], () -> { }, () -> { });
            try (var view = new RunWindowView(enterprise(), true, host)) {
                assertThat(text(view, RunWindowView.SHORTCUT)).as("unbound when built").isEmpty();
                assertThat(scopeLabels(view)).containsExactly("All", "Commands");
                scopes.add(RunWindowFixtures.SNIPPETS);
                shortcut[0] = "\u21e7\u2318P";
                view.opened();
                assertThat(text(view, RunWindowView.SHORTCUT)).isEqualTo("\u21e7\u2318P");
                assertThat(scopeLabels(view)).as("a scope registered after the window was built").containsExactly("All", "Commands", "Snippets");
                shortcut[0] = "\u2318K";
                view.refreshTheme();
                assertThat(text(view, RunWindowView.SHORTCUT)).as("a rebinding").isEqualTo("\u2318K");
            }
        });
    }

    @Test void theRegionsSitWhereTheMockPutsThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var context = enterprise();
            try (var view = RunWindowFixtures.view(context, new ArrayList<>())) {
                view.setEntries(RunWindowFixtures.justOpened(), null, "Nothing found");
                layout(view);
                assertThat(view.getWidth()).as("RUN_WINDOW_WIDTH inside a 1 px frame").isEqualTo(462);
                int hair = 1, side = m(context, DesignMetrics.Role.RUN_WINDOW_SIDE), top = hair + 24 + hair;
                assertThat(view.viewFor(RunWindowView.HELP).getBounds()).isEqualTo(new Rectangle(hair + side, top + 10, 436, view.viewFor(RunWindowView.HELP).getHeight()));
                Rectangle field = view.fieldBox().getBounds();
                int ring = m(context, DesignMetrics.Role.FOCUS_RING);
                assertThat(field.x + ring).as("the field box at the label column").isEqualTo(hair + side + 48);
                assertThat(field.x + field.width - ring).as("to the right padding").isEqualTo(462 - hair - side);
                assertThat(field.height - 2 * ring).isEqualTo(m(context, DesignMetrics.Role.FIELD_HEIGHT));
                Rectangle box = view.listBox().getBounds();
                assertThat(box.x).isEqualTo(hair + side);
                assertThat(box.width).isEqualTo(436);
                assertThat(box.y).isEqualTo(field.y + ring + m(context, DesignMetrics.Role.FIELD_HEIGHT) + 10);
                assertThat(box.height).as("8 rows and two headings (Rulings 5)").isEqualTo(2 + 8 * 21 + 2 * 19);
                var primary = view.viewFor(RunWindowView.PRIMARY).getBounds();
                var cancel = view.viewFor(RunWindowView.CANCEL).getBounds();
                var browse = view.viewFor(RunWindowView.BROWSE).getBounds();
                assertThat(List.of(primary.width, cancel.width, browse.width)).as("at least the mock's 70").allMatch(width -> width >= 70);
                assertThat(browse.x + browse.width).isEqualTo(462 - hair - side);
                assertThat(cancel.x + cancel.width + 6).isEqualTo(browse.x);
                assertThat(primary.x + primary.width + 6).isEqualTo(cancel.x);
                assertThat(view.getHeight() - hair - 40 + 8).as("buttons 8 into the footer").isEqualTo(primary.y);
                var close = view.viewFor(RunWindowView.CLOSE).getBounds();
                assertThat(close.x + close.width).isEqualTo(462 - hair - 5);
                assertThat(close.y + close.height / 2).isEqualTo(hair + 12);
                var split = view.viewFor(RunWindowView.SPLIT).getBounds();
                assertThat(split.x + ring).as("the box at the side padding").isEqualTo(hair + side);
                var label = view.viewFor(RunWindowView.SPLIT_LABEL).getBounds();
                assertThat(label.x).isEqualTo(hair + side + m(context, DesignMetrics.Role.CHECKBOX_BOX) + m(context, DesignMetrics.Role.CHECKBOX_LABEL_GAP));
                assertThat(view.getHeight()).isEqualTo(view.getPreferredSize().height);
            }
        });
    }

    @Test void justOpenedReadsAsTheMock() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var view = RunWindowFixtures.view(enterprise(), new ArrayList<>())) {
                view.setEntries(RunWindowFixtures.justOpened(), null, "Nothing found");
                assertThat(listTexts(view)).containsExactly("# Recent", "Mac", "Install 'jasper' Command in PATH", "wiremock run", "# Most used",
                    "New Editor File", "settings.gradle.kts", "gcloud auth", "VendorDetailsService.java", "THD Crypto: Encrypt / Decrypt…");
                assertThat(text(view, RunWindowView.TITLE)).isEqualTo("Run");
                assertThat(text(view, RunWindowView.SHORTCUT)).isEqualTo("⇧⌘P");
                assertThat(text(view, RunWindowView.HELP)).isEqualTo(RunCopy.HELP);
                assertThat(text(view, RunWindowView.OPEN)).isEqualTo("Open:");
                assertThat(text(view, RunWindowView.DETAIL_LABEL)).isEqualTo("Session:");
                assertThat(text(view, RunWindowView.DETAIL_VALUE)).isEqualTo("dustin@192.168.1.119:22");
                assertThat(text(view, RunWindowView.PRIMARY)).isEqualTo("Connect");
                assertThat(text(view, RunWindowView.CANCEL)).isEqualTo("Cancel");
                assertThat(text(view, RunWindowView.BROWSE)).isEqualTo("Browse…");
                assertThat(text(view, RunWindowView.SPLIT_LABEL)).isEqualTo("Open in a split pane");
                assertThat(enabled(view, RunWindowView.SPLIT)).as("a session has a split verb").isTrue();
                var mac = list(view).items().get(1);
                assertThat(((Nodes.Icon) mac.leading().orElseThrow()).size()).isEqualTo(Nodes.IconSize.SWATCH);
                assertThat(((Nodes.Icon) mac.leading().orElseThrow()).hue()).isEqualTo(Nodes.Hue.GREEN);
                assertThat(((Nodes.Text) mac.subtitle().getFirst()).role()).as("the detail in the data face").isEqualTo(Nodes.TextRole.MONO);
                assertThat(mac.contextActions().orElseThrow()).extracting(dev.jasper.ui.model.MenuItem::label)
                    .as("the second and third verbs").containsExactly("Connect in split", "Edit");
                var crypto = list(view).items().get(9);
                assertThat(((Nodes.Text) crypto.trailing().getFirst()).text()).isEqualTo("⌃⌥⇧C");
                var scopes = (Nodes.MenuButton) view.viewFor(RunWindowView.SCOPES).spec().root();
                assertThat(scopes.items()).extracting(dev.jasper.ui.model.MenuItem::label).containsExactly("All", "Commands", "SSH", "Snippets", "Files");
                assertThat(scopes.icon()).isEqualTo("triangle.down");
                var help = (Nodes.Stack) view.viewFor(RunWindowView.HELP).spec().root();
                assertThat(help.role()).isEqualTo(Nodes.LayoutRole.PROMPT_HEADER);
                assertThat(((Nodes.Icon) help.children().getFirst()).name()).isEqualTo("play.filled");
            }
        });
    }

    /** Review Focus 2. */
    @Test void aQueryShowsOneMatchesHeadingWithItsPosition() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var view = RunWindowFixtures.view(enterprise(), new ArrayList<>())) {
                var entries = List.<PaletteEntry>of(new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "SSH"),
                    RunWindowFixtures.item(RunWindowFixtures.SESSIONS, "a", "alpha", null, null), RunWindowFixtures.item(RunWindowFixtures.SESSIONS, "b", "beta", null, null),
                    new PaletteEntry.More(RunWindowFixtures.SESSIONS), new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "Snippets"),
                    RunWindowFixtures.item(RunWindowFixtures.SNIPPETS, "c", "gamma", null, null));
                view.setEntries(entries, null, "Nothing found");
                assertThat(listTexts(view)).containsExactly("# Matches | 1 of 3", "alpha", "beta", "More in SSH…", "gamma");
                view.selectRelative(2);
                assertThat(listTexts(view).getFirst()).as("a More row blanks the count").isEqualTo("# Matches | ");
                assertThat(text(view, RunWindowView.PRIMARY)).isEqualTo("Open");
                assertThat(enabled(view, RunWindowView.PRIMARY)).isTrue();
                assertThat(text(view, RunWindowView.DETAIL_LABEL)).isEmpty();
                view.selectRelative(1);
                assertThat(listTexts(view).getFirst()).isEqualTo("# Matches | 3 of 3");
                assertThat(text(view, RunWindowView.DETAIL_LABEL)).isEqualTo("Snippet:");
                assertThat(text(view, RunWindowView.PRIMARY)).isEqualTo("Insert");
            }
        });
    }

    /** Review Focus 1, and spec 11.4's Enter contract in the Run window. */
    @Test void theSplitBoxFollowsTheSelectionAndClearsOnAScopeWithoutASplitVerb() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var events = new ArrayList<String>();
            try (var view = RunWindowFixtures.view(enterprise(), events)) {
                view.setEntries(RunWindowFixtures.justOpened(), null, "Nothing found");
                view.executeSelected(0);
                assertThat(events).containsExactly("run:dev.jasper.remote.scope/mac:0");
                view.handle(new dev.jasper.ui.model.UiEvent.Toggled(RunWindowView.SPLIT, true));
                assertThat(view.splitTicked()).isTrue();
                view.executeSelected(0);
                assertThat(events).as("ticked: Enter runs the SPLIT verb").endsWith("run:dev.jasper.remote.scope/mac:1");
                view.executeSelected(2);
                assertThat(events).as("Shift+Enter's verb is unchanged").endsWith("run:dev.jasper.remote.scope/mac:2");
                view.selectRelative(2);
                assertThat(view.splitTicked()).as("a snippet has no split verb").isFalse();
                assertThat(enabled(view, RunWindowView.SPLIT)).isFalse();
                assertThat(enabled(view, RunWindowView.SPLIT_LABEL)).isFalse();
                view.executeSelected(0);
                assertThat(events).endsWith("run:dev.jasper.snippets.scope/wiremock:0");
                view.selectRelative(-2);
                assertThat(view.splitTicked()).as("the box stays clear when it comes back").isFalse();
                view.handle(new dev.jasper.ui.model.UiEvent.Toggled(RunWindowView.SPLIT, true));
                view.opened();
                assertThat(view.splitTicked()).as("cleared each time the window opens").isFalse();
            }
        });
    }

    /** Review Focus 3. */
    @Test void longTextNeverWidensTheWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var view = RunWindowFixtures.view(enterprise(), new ArrayList<>())) {
                String path = "/very/long/".repeat(30) + "file.txt";
                view.setEntries(List.of(new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "Files"),
                    RunWindowFixtures.item(RunWindowFixtures.FILES, "long", "x".repeat(200), path, "⌘K")), null, "");
                layout(view);
                assertThat(view.getWidth()).isEqualTo(462);
                var value = view.viewFor(RunWindowView.DETAIL_VALUE);
                assertThat(value.getX() + value.getWidth()).as("the detail line ends at the padding").isLessThanOrEqualTo(462 - 1 - 12);
            }
        });
    }

    /** Review Focus 4. */
    @Test void theListKeepsItsHeightWithOneRowOrNone() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var view = RunWindowFixtures.view(enterprise(), new ArrayList<>())) {
                view.setEntries(RunWindowFixtures.typingAuth(), null, "Nothing found");
                layout(view);
                int one = view.listBox().getHeight();
                view.setEntries(List.of(), null, "Nothing found");
                layout(view);
                assertThat(view.listBox().getHeight()).isEqualTo(one).isEqualTo(view.listHeight());
                assertThat(RunWindowFixtures.texts(view.listBox())).containsExactly("Nothing found");
                assertThat(text(view, RunWindowView.PRIMARY)).isEqualTo("Open");
                assertThat(enabled(view, RunWindowView.PRIMARY)).isFalse();
                assertThat(text(view, RunWindowView.DETAIL_VALUE)).isEmpty();
            }
        });
    }

    /** Review Focus 5. */
    @Test void aStepRendersInTheListBoxAsInTheCard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var view = RunWindowFixtures.view(enterprise(), new ArrayList<>())) {
                view.setEntries(RunWindowFixtures.justOpened(), null, "");
                int height = view.getPreferredSize().height;
                view.showStep("Rebase", List.of(new PaletteStep.Field("branch", "Branch", "main"), new PaletteStep.Field("onto", "Onto", "")));
                layout(view);
                assertThat(view.stepShowing()).isTrue();
                assertThat(SwingUtilities.isDescendingFrom(view.step().panel(), view.listBox())).isTrue();
                assertThat(view.step().panel().isShowing() || view.step().panel().isVisible()).isTrue();
                assertThat(enabled(view, RunWindowView.PRIMARY)).isFalse();
                assertThat(enabled(view, RunWindowView.SPLIT)).isFalse();
                assertThat(view.getPreferredSize().height).as("the window keeps its height").isEqualTo(height);
                assertThat(view.stepValues()).containsEntry("branch", "main");
                view.hideStep();
                assertThat(view.stepShowing()).isFalse();
                assertThat(enabled(view, RunWindowView.PRIMARY)).isTrue();
            }
        });
    }

    @Test void theButtonsTheMenuAndAChosenScopeCallTheHost() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var events = new ArrayList<String>();
            try (var view = RunWindowFixtures.view(enterprise(), events)) {
                view.setEntries(RunWindowFixtures.justOpened(), null, "");
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.CANCEL));
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.CLOSE));
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.BROWSE));
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.PRIMARY));
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.SCOPE + "dev.jasper.snippets.scope"));
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.SCOPE + PaletteScope.ALL_ID));
                view.handle(new dev.jasper.ui.model.UiEvent.Action("verb:5:1"));
                assertThat(events).containsExactly("cancel", "cancel", "browse", "run:dev.jasper.remote.scope/mac:0",
                    "scope:dev.jasper.snippets.scope", "scope:all", "run:jasper.commands/new_editor_file:1");
                view.setChip(RunWindowFixtures.SNIPPETS);
                assertThat(text(view, RunWindowView.HELP)).as("a chosen scope's description").isEqualTo("Insert or run a saved command");
                assertThat(view.viewFor(RunWindowView.CHIP).isVisible()).isTrue();
                view.setChip(null);
                assertThat(view.viewFor(RunWindowView.CHIP).isVisible()).isFalse();
                assertThat(text(view, RunWindowView.HELP)).isEqualTo(RunCopy.HELP);
                view.setPlaceholder("Search everything");
                assertThat(((Nodes.Field) view.viewFor(RunWindowView.QUERY).spec().root()).hint()).as("no placeholder (Rulings 9)").isEmpty();
                assertThat(view.modeless()).isTrue();
            }
        });
    }
}
```

The tests drive the view's own event handler (`handle`, which every one of its kit views calls back) rather than
clicking kit components, so they do not depend on the classic faces' component structure.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.RunWindowViewTest'`
Expected: FAIL: `compileTestJava` reports "cannot find symbol … class RunWindowView".

- [ ] **Step 3: The view**

Create `jasper-app/src/main/java/dev/jasper/app/palette/RunWindowView.java`:

```java
package dev.jasper.app.palette;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.swing.SurfacePanel;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.GridBagLayout;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;

/**
 * The palette as the Run window (Enterprise presentation spec 11.2, the 06 Run Dialog mocks): a framed window with a
 * title row, the help row, "Open:" and its field, a bordered list of fixed height, the selected row's detail line, the
 * split-pane checkbox and a footer of the primary verb, Cancel and Browse…. Every region is a kit view presented with
 * {@link KitView#runWindow(boolean)} on a kit-painted {@link SurfacePanel}; this class only lays them out. It stays
 * open while the person works elsewhere ({@link #modeless()}). EDT only.
 */
public final class RunWindowView extends SurfacePanel implements PaletteView {
    static final String TITLE = "run.title", SHORTCUT = "run.shortcut", CLOSE = "run.close", HELP = "run.help", HELP_ICON = "run.help.icon",
        HELP_TEXT = "run.help.text", OPEN = "run.open", CHIP = "run.chip", QUERY = "run.query", SCOPES = "run.scopes", RESULTS = "run.results",
        EMPTY = "run.empty", DETAIL_LABEL = "run.detail.label", DETAIL_VALUE = "run.detail.value", SPLIT = "run.split",
        SPLIT_LABEL = "run.split.label", PRIMARY = "run.primary", CANCEL = "run.cancel", BROWSE = "run.browse", RUN = "run",
        VERB = "verb:", SCOPE = "scope:";
    /** The checkbox's label (spec 11.2). */
    static final String SPLIT_TEXT = "Open in a split pane";
    private static final String LIST = "list", NONE = "none", STEP = StepForm.STEP;

    private final KitContext kit;
    private final PaletteView.Host host;
    private final QueryEdits edits;
    private final Map<String, KitView> views = new LinkedHashMap<>();
    private final SurfacePanel fieldBox, listBox;
    private final JPanel cards;
    private final CardLayout cardLayout = new CardLayout();
    private final StepForm step;
    private final AutoCloseable themeSubscription;
    private int visibleRows = dev.jasper.app.config.PaletteSettings.DEFAULT_VISIBLE_ROWS;
    private String query = "", emptyText = "", listCard = NONE;
    private PaletteScope chip;
    private List<PaletteEntry> entries = List.of();
    private int selected = -1;
    private boolean split, closed;

    RunWindowView(KitContext kit, boolean macOs, PaletteView.Host host) {
        super(kit, SurfacePanel.Kind.RUN_WINDOW, () -> UIScale.scale(kit.metrics().value(DesignMetrics.Role.RUN_WINDOW_TITLE_HEIGHT)),
            () -> UIScale.scale(kit.metrics().value(DesignMetrics.Role.RUN_WINDOW_FOOTER_HEIGHT)));
        this.kit = Objects.requireNonNull(kit);
        this.host = Objects.requireNonNull(host);
        this.edits = new QueryEdits(host.prefixes());
        setOpaque(false);
        // Presses that no control takes stop here, so the overlay behind never sees them; the host drags by them.
        var swallow = new MouseAdapter() { };
        addMouseListener(swallow);
        addMouseMotionListener(swallow);
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override public Component getComponentAfter(Container root, Component current) { return queryInput(); }
            @Override public Component getComponentBefore(Container root, Component current) { return queryInput(); }
            @Override public Component getFirstComponent(Container root) { return queryInput(); }
            @Override public Component getLastComponent(Container root) { return queryInput(); }
            @Override public Component getDefaultComponent(Container root) { return queryInput(); }
        });
        // The step form first: the checkbox's and the primary button's descriptors ask whether a step is showing.
        step = new StepForm(kit);
        for (String id : List.of(TITLE, SHORTCUT, CLOSE, HELP, OPEN, CHIP, QUERY, SCOPES, RESULTS, EMPTY, DETAIL_LABEL, DETAIL_VALUE,
                SPLIT, SPLIT_LABEL, PRIMARY, CANCEL, BROWSE)) {
            var view = new KitView(kit, spec(id), this::handle);
            view.runWindow(true);
            views.put(id, view);
        }
        fieldBox = new SurfacePanel(kit, SurfacePanel.Kind.FIELD);
        fieldBox.setLayout(new FieldLayout());
        fieldBox.add(view(CHIP)); fieldBox.add(view(QUERY)); fieldBox.add(view(SCOPES));
        view(CHIP).setVisible(false);
        var empty = new JPanel(new GridBagLayout());
        empty.setOpaque(false);
        empty.add(view(EMPTY));
        cards = new JPanel(cardLayout);
        cards.setOpaque(false);
        cards.add(view(RESULTS), LIST);
        cards.add(empty, NONE);
        cards.add(step.panel(), STEP);
        cardLayout.show(cards, listCard);
        listBox = new SurfacePanel(kit, SurfacePanel.Kind.BOX);
        listBox.add(cards);
        setLayout(new WindowLayout());
        for (String id : List.of(TITLE, SHORTCUT, CLOSE, HELP, OPEN, DETAIL_LABEL, DETAIL_VALUE, SPLIT, SPLIT_LABEL, PRIMARY, CANCEL, BROWSE)) add(view(id));
        add(fieldBox);
        add(listBox);
        // The checkbox's label is its click target too, as a desktop checkbox's is.
        var toggleByLabel = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (splitAvailable()) setSplit(!split); }
        };
        view(SPLIT_LABEL).addMouseListener(toggleByLabel);
        var label = CardView.find(view(SPLIT_LABEL), SPLIT_LABEL, javax.swing.JLabel.class);
        if (label != null) label.addMouseListener(toggleByLabel);
        // The title row drags the window (spec 11.2), its text included: the title and shortcut labels carry tooltips, so a
        // press stops at them; they hand their mouse events to the view, where the host's drag listens (Rulings 14).
        var toView = new MouseAdapter() {
            private void pass(MouseEvent event) { dispatchEvent(javax.swing.SwingUtilities.convertMouseEvent(event.getComponent(), event, RunWindowView.this)); }
            @Override public void mousePressed(MouseEvent event) { pass(event); }
            @Override public void mouseReleased(MouseEvent event) { pass(event); }
            @Override public void mouseClicked(MouseEvent event) { pass(event); }
            @Override public void mouseDragged(MouseEvent event) { pass(event); }
        };
        for (String id : List.of(TITLE, SHORTCUT)) {
            view(id).addMouseListener(toView); view(id).addMouseMotionListener(toView);
            var text = CardView.find(view(id), id, javax.swing.JLabel.class);
            if (text != null) { text.addMouseListener(toView); text.addMouseMotionListener(toView); }
        }
        themeSubscription = kit.binding().subscribe(pair -> refreshTheme());
    }

    private KitView view(String id) { return views.get(id); }

    /** The kit view of region {@code id}, for headless tests. */
    KitView viewFor(String id) { return view(id); }

    private int s(DesignMetrics.Role role) { return UIScale.scale(kit.metrics().value(role)); }

    private static Nodes.Text text(String id, String value, Nodes.TextRole role) { return new Nodes.Text(id, ControlState.READY, value, role); }

    private static ControlState enabled(boolean value) { return new ControlState(true, value, false, ""); }

    // ---- Descriptors ------------------------------------------------------------------------------------------------

    private ViewSpec spec(String id) {
        PaletteEntry selection = selectedEntry();
        return new ViewSpec(switch (id) {
            case TITLE -> text(TITLE, "Run", Nodes.TextRole.WINDOW_TITLE);
            case SHORTCUT -> text(SHORTCUT, host.openerShortcut().get(), Nodes.TextRole.CAPTION);
            case CLOSE -> new Nodes.Button(CLOSE, ControlState.READY, "", "close", Nodes.ButtonKind.GHOST, CLOSE, "Close", Nodes.ButtonPresentation.EMBEDDED);
            case HELP -> new Nodes.Stack(HELP, ControlState.READY, Nodes.LayoutRole.PROMPT_HEADER, List.of(
                new Nodes.Icon(HELP_ICON, ControlState.READY, "play.filled", Nodes.Hue.BLUE, Nodes.IconSize.PROMPT, "Run"),
                text(HELP_TEXT, helpText(), Nodes.TextRole.DESCRIPTION)), "");
            case OPEN -> text(OPEN, "Open:", Nodes.TextRole.LABEL);
            case CHIP -> new Nodes.Chip(CHIP, ControlState.READY, chip == null ? "All" : chip.label(), Nodes.Hue.BLUE);
            case QUERY -> new Nodes.Field(QUERY, ControlState.READY, Nodes.FieldKind.TEXT, query, "", "Open", "", Optional.empty(),
                Nodes.FieldPresentation.TRANSPARENT);
            case SCOPES -> new Nodes.MenuButton(SCOPES, ControlState.READY, "", "triangle.down", Nodes.ButtonKind.GHOST, "", scopeItems(),
                "Choose what to open", Nodes.ButtonPresentation.EMBEDDED);
            case RESULTS -> new Nodes.ItemList(RESULTS, ControlState.READY, Nodes.ListKind.RESULTS, items(),
                selected < 0 ? Set.of() : Set.of(entryId(selected)), RUN, List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(visibleRows));
            case EMPTY -> text(EMPTY, emptyText, Nodes.TextRole.DESCRIPTION);
            case DETAIL_LABEL -> text(DETAIL_LABEL, RunCopy.detailLabel(selection), Nodes.TextRole.BODY_STRONG);
            case DETAIL_VALUE -> text(DETAIL_VALUE, RunCopy.detailValue(selection), Nodes.TextRole.MONO);
            case SPLIT -> new Nodes.Toggle(SPLIT, enabled(splitAvailable()), SPLIT_TEXT, split, SPLIT, false, "");
            case SPLIT_LABEL -> new Nodes.Text(SPLIT_LABEL, enabled(splitAvailable()), SPLIT_TEXT, Nodes.TextRole.LABEL);
            case PRIMARY -> new Nodes.Button(PRIMARY, enabled(RunCopy.primaryEnabled(selection) && !step.showing()), RunCopy.primaryLabel(selection), "",
                Nodes.ButtonKind.PRIMARY, PRIMARY, "", Nodes.ButtonPresentation.STANDARD);
            case CANCEL -> new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, "", Nodes.ButtonPresentation.STANDARD);
            case BROWSE -> new Nodes.Button(BROWSE, ControlState.READY, "Browse…", "", Nodes.ButtonKind.SECONDARY, BROWSE, "", Nodes.ButtonPresentation.STANDARD);
            default -> throw new IllegalArgumentException(id);
        });
    }

    /** The help row's text: the chosen scope's description, else the fixed host string (Rulings 10). */
    String helpText() {
        return chip == null || chip.description().isBlank() ? RunCopy.HELP : chip.description();
    }

    /** "All", then each scope with its shortcut, as today's mode list orders them. */
    private List<MenuItem> scopeItems() {
        var items = new ArrayList<MenuItem>();
        items.add(new MenuItem(SCOPE + "all", "All", "", SCOPE + PaletteScope.ALL_ID, true, false, Nodes.Hue.NEUTRAL, "command_palette"));
        for (PaletteScope scope : host.scopes().get())
            items.add(new MenuItem(SCOPE + "item." + items.size(), scope.label(), "", SCOPE + scope.id(), true, false, Nodes.Hue.NEUTRAL,
                scope.shortcutActionId().orElse("")));
        return items;
    }

    private static String entryId(int index) { return "entry." + index; }

    private List<ListItem> items() {
        var items = new ArrayList<ListItem>();
        boolean matches = false;
        for (int i = 0; i < entries.size(); i++) {
            String id = entryId(i);
            switch (entries.get(i)) {
                case PaletteEntry.Header header -> {
                    if (header.kind() == PaletteEntry.Header.Kind.MATCHES) {
                        // One "Matches" heading for every scope's matches, its count on the right (spec 11.3). A heading has a
                        // title only, so the count is its second title node, which the kit lays at the band's end (Rulings 13).
                        if (matches) continue;
                        matches = true;
                        items.add(new ListItem(id, Optional.empty(), List.of(text(id + ".title", RunCopy.heading(header), Nodes.TextRole.CAPTION),
                            text(id + ".count", RunCopy.position(entries, selected), Nodes.TextRole.CAPTION)), List.of(), List.of(), true));
                    } else items.add(new ListItem(id, Optional.empty(), List.of(text(id + ".title", RunCopy.heading(header), Nodes.TextRole.CAPTION)),
                        List.of(), List.of(), true));
                }
                case PaletteEntry.Item item -> items.add(row(i, item));
                case PaletteEntry.More more -> items.add(new ListItem(id, Optional.empty(),
                    List.of(text(id + ".title", "More in " + more.scope().label() + "…", Nodes.TextRole.DESCRIPTION)), List.of(), List.of()));
            }
        }
        return items;
    }

    /** A row: its scope's hue square, its title, its detail in the data face, its tag, and its second and third verbs in its menu. */
    private ListItem row(int index, PaletteEntry.Item item) {
        String id = entryId(index);
        PaletteRow row = item.row();
        PaletteScope scope = item.scope();
        var square = new Nodes.Icon(id + ".swatch", ControlState.READY, "file", scope.hue(), Nodes.IconSize.SWATCH, scope.label());
        var verbs = scope.verbs();
        var menu = new ArrayList<MenuItem>();
        for (int verb = 1; verb < Math.min(3, verbs.size()); verb++) {
            String action = VERB + index + ":" + verb;
            menu.add(new MenuItem(action, verbs.get(verb).label(), "", action, true, false));
        }
        return new ListItem(id, Optional.of(square), List.of(text(id + ".title", row.title(), row.enabled() ? Nodes.TextRole.BODY : Nodes.TextRole.DESCRIPTION)),
            row.detail() == null ? List.of() : List.of(text(id + ".detail", row.detail(), Nodes.TextRole.MONO)),
            row.tag() == null ? List.of() : List.of(text(id + ".tag", row.tag(), Nodes.TextRole.CAPTION)), false,
            menu.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(menu)), "");
    }

    private void render(String... ids) {
        for (String id : ids) { var view = view(id); if (!view.isClosed()) view.update(spec(id)); }
        revalidate(); repaint();
    }

    /** The regions that follow the selection: the list (its selection and count), the detail line, the checkbox and the primary button. */
    private void renderSelection() {
        if (split && !splitAvailable()) split = false;
        render(RESULTS, DETAIL_LABEL, DETAIL_VALUE, SPLIT, SPLIT_LABEL, PRIMARY);
    }

    // ---- Events -----------------------------------------------------------------------------------------------------

    /** Every kit view's events, and the tests' way in. */
    void handle(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(QUERY) -> edited(edited.text(), true);
            case UiEvent.Selected chosen when chosen.id().equals(RESULTS) ->
                chosen.ids().stream().findFirst().ifPresent(id -> select(Integer.parseInt(id.substring("entry.".length()))));
            case UiEvent.Action action when action.id().equals(RUN) || action.id().equals(PRIMARY) -> executeSelected(0);
            case UiEvent.Action action when action.id().startsWith(VERB) -> {
                String[] parts = action.id().substring(VERB.length()).split(":");
                select(Integer.parseInt(parts[0]));
                executeSelected(Integer.parseInt(parts[1]));
            }
            case UiEvent.Action action when action.id().equals(CLOSE) || action.id().equals(CANCEL) -> host.cancel().run();
            case UiEvent.Action action when action.id().equals(BROWSE) -> host.browse().run();
            case UiEvent.Action action when action.id().startsWith(SCOPE) -> {
                String id = action.id().substring(SCOPE.length());
                host.chooseScope().accept(id.equals(PaletteScope.ALL_ID) ? null
                    : host.scopes().get().stream().filter(scope -> scope.id().equals(id)).findFirst().orElse(null));
            }
            case UiEvent.Toggled toggled when toggled.id().equals(SPLIT) -> setSplit(toggled.checked());
            default -> { }
        }
    }

    private void setSplit(boolean value) { split = value && splitAvailable(); render(SPLIT); }

    /** Whether the selected row's scope has a SPLIT verb and no step is showing (spec 11.2). */
    private boolean splitAvailable() {
        return !step.showing() && selectedEntry() instanceof PaletteEntry.Item item && RunCopy.splitVerb(item.scope()) >= 0;
    }

    private void edited(String text, boolean typed) {
        var outcome = edits.edited(query, text, typed);
        query = outcome.query();
        render(QUERY);
        if (edits.pending()) edits.settle(() -> closed, () -> queryInput() instanceof JTextComponent input ? input.getText() : null, () -> query);
        host.queryChanged().accept(query);
        outcome.prefix().ifPresent(found -> host.chooseScope().accept(found.scope()));
    }

    // ---- PaletteView ------------------------------------------------------------------------------------------------

    @Override public JComponent component() { return this; }
    @Override public boolean modeless() { return true; }
    /**
     * Each opening clears the checkbox (spec 11.2) and reads the opener's live COMMAND_PALETTE shortcut and the scopes
     * registered now (plugin scopes usually arrive after the window is built).
     */
    @Override public void opened() { split = false; render(SHORTCUT, SCOPES, SPLIT); }
    @Override public void focusQuery() { if (!view(QUERY).isClosed()) view(QUERY).focus(QUERY); }
    @Override public boolean composing() { return !view(QUERY).isClosed() && view(QUERY).composing(QUERY); }
    @Override public String queryText() { return query; }
    @Override public void setQueryText(String text) { if (!Objects.requireNonNull(text).equals(query)) edited(text, false); }

    @Override public void setChip(PaletteScope scope) {
        chip = scope;
        view(CHIP).setVisible(scope != null);
        render(CHIP, QUERY, HELP);
        fieldBox.revalidate();
    }

    /** The Run window's field shows no placeholder: the help row says what to type (Rulings 9). */
    @Override public void setPlaceholder(String text) { }

    @Override public void setEntries(List<PaletteEntry> next, String selectionKey, String empty) {
        if (next.stream().filter(entry -> entry instanceof PaletteEntry.Item).count() > PaletteResults.MAX_ROWS)
            throw new IllegalArgumentException("Too many palette results");
        int chosen = -1, first = -1;
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).selectable() && first < 0) first = i;
            if (selectionKey != null && selectionKey.equals(next.get(i).key())) chosen = i;
        }
        entries = List.copyOf(next);
        selected = chosen >= 0 ? chosen : first;
        emptyText = empty == null ? "" : empty;
        listCard = first < 0 ? NONE : LIST;
        if (!step.showing()) cardLayout.show(cards, listCard);
        render(EMPTY);
        renderSelection();
    }

    @Override public PaletteEntry selectedEntry() { return selected < 0 || selected >= entries.size() ? null : entries.get(selected); }
    @Override public String selectedKey() { var entry = selectedEntry(); return entry == null ? null : entry.key(); }

    @Override public void selectRow(String rowId) {
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i) instanceof PaletteEntry.Item item && item.row().id().equals(rowId)) { select(i); return; }
    }

    @Override public void selectRelative(int delta) {
        if (selected < 0 || delta == 0) return;
        int direction = Integer.signum(delta), remaining = Math.abs(delta), target = selected;
        for (int i = selected + direction; remaining > 0 && i >= 0 && i < entries.size(); i += direction)
            if (entries.get(i).selectable()) { target = i; remaining--; }
        select(target);
    }

    private void select(int index) {
        if (index < 0 || index >= entries.size() || !entries.get(index).selectable()) return;
        selected = index;
        renderSelection();
    }

    /** Enter's verb is verb 0, or the SPLIT verb while the box is ticked; the others keep their keys (spec 11.4). */
    @Override public void executeSelected(int verb) {
        PaletteEntry entry = selectedEntry();
        if (entry == null) return;
        int index = verb;
        if (verb == 0 && split && entry instanceof PaletteEntry.Item item && RunCopy.splitVerb(item.scope()) >= 0) index = RunCopy.splitVerb(item.scope());
        host.execute().accept(entry, index);
    }

    @Override public void setVisibleRows(int rows) { visibleRows = rows; render(RESULTS); }
    @Override public int visibleRows() { return visibleRows; }

    @Override public void showStep(String title, List<PaletteStep.Field> fields) {
        step.show(title, fields);
        cardLayout.show(cards, STEP);
        render(SPLIT, SPLIT_LABEL, PRIMARY);
    }

    @Override public void hideStep() {
        step.hide();
        cardLayout.show(cards, listCard);
        render(SPLIT, SPLIT_LABEL, PRIMARY);
    }

    @Override public boolean stepShowing() { return step.showing(); }
    @Override public void focusStepField(int delta) { step.focusField(delta); }
    @Override public Map<String, String> stepValues() { return step.values(); }
    @Override public void setStepError(String message) { step.setError(message); revalidate(); repaint(); }
    /** Follows a theme change; the title row's shortcut and the scope menu are read again, as a rebinding may come with it. */
    @Override public void refreshTheme() { step.applyGeometry(); render(SHORTCUT, SCOPES); }
    /** The Run window has no reveal (spec 11.6). */
    @Override public void setReveal(double fraction) { }
    @Override public int revealedHeight() { return getHeight(); }

    @Override public Dimension getPreferredSize() { return getLayout().preferredLayoutSize(this); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { themeSubscription.close(); } catch (Exception ignored) { /* closing a subscription has nothing to report */ }
        views.values().forEach(KitView::close);
        step.close();
    }

    private Component queryInput() { return CardView.find(view(QUERY), QUERY + ".input", JTextComponent.class); }

    /** The list box's height: {@code visible_rows} rows and two heading bands inside its edge (spec 11.2, Rulings 5). */
    int listHeight() {
        int hair = s(DesignMetrics.Role.HAIRLINE);
        return 2 * hair + visibleRows * s(DesignMetrics.Role.RUN_WINDOW_ROW_HEIGHT) + 2 * (s(DesignMetrics.Role.RUN_WINDOW_HEADING_HEIGHT) + hair);
    }

    // ---- Package-private access for headless tests ------------------------------------------------------------------

    List<PaletteEntry> entries() { return entries; }
    int selectedIndex() { return selected; }
    boolean splitTicked() { return split; }
    SurfacePanel fieldBox() { return fieldBox; }
    SurfacePanel listBox() { return listBox; }
    StepForm step() { return step; }

    // ---- Layout -----------------------------------------------------------------------------------------------------

    /** The mocks' geometry from the Run window roles: the title row, the body's rows and the footer's buttons, right-packed. */
    private final class WindowLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }

        private int content() { return s(DesignMetrics.Role.RUN_WINDOW_WIDTH) - 2 * s(DesignMetrics.Role.RUN_WINDOW_SIDE); }
        private int help() { return SurfacePanel.measuredHeight(view(HELP), content()); }
        private int line() { return Math.max(view(DETAIL_LABEL).getPreferredSize().height, view(DETAIL_VALUE).getPreferredSize().height); }
        /** The checkbox row: the classic box and its focus reserve, whichever face draws the toggle (plan 8a R7). */
        private int check() { return s(DesignMetrics.Role.CHECKBOX_BOX) + 2 * s(DesignMetrics.Role.FOCUS_RING); }

        @Override public Dimension preferredLayoutSize(Container parent) {
            int hair = s(DesignMetrics.Role.HAIRLINE);
            int height = hair + s(DesignMetrics.Role.RUN_WINDOW_TITLE_HEIGHT) + hair
                + s(DesignMetrics.Role.RUN_WINDOW_HELP_TOP) + help() + s(DesignMetrics.Role.RUN_WINDOW_FIELD_TOP) + s(DesignMetrics.Role.FIELD_HEIGHT)
                + s(DesignMetrics.Role.RUN_WINDOW_LIST_TOP) + listHeight() + s(DesignMetrics.Role.RUN_WINDOW_DETAIL_TOP) + line()
                + s(DesignMetrics.Role.RUN_WINDOW_CHECK_TOP) + check() + s(DesignMetrics.Role.RUN_WINDOW_BOTTOM)
                + hair + s(DesignMetrics.Role.RUN_WINDOW_FOOTER_HEIGHT) + hair;
            return new Dimension(s(DesignMetrics.Role.RUN_WINDOW_WIDTH) + 2 * hair, height);
        }

        @Override public void layoutContainer(Container parent) {
            int hair = s(DesignMetrics.Role.HAIRLINE), side = s(DesignMetrics.Role.RUN_WINDOW_SIDE), ring = s(DesignMetrics.Role.FOCUS_RING);
            int right = getWidth() - hair, x = hair + side, content = Math.max(0, getWidth() - 2 * hair - 2 * side);
            int title = s(DesignMetrics.Role.RUN_WINDOW_TITLE_HEIGHT), close = s(DesignMetrics.Role.RUN_WINDOW_CLOSE);
            view(CLOSE).setBounds(right - s(DesignMetrics.Role.RUN_WINDOW_CLOSE_SIDE) - close, hair + (title - close) / 2, close, close);
            var shortcut = view(SHORTCUT).getPreferredSize();
            int shortcutX = view(CLOSE).getX() - s(DesignMetrics.Role.RUN_WINDOW_SHORTCUT_GAP) - shortcut.width;
            view(SHORTCUT).setBounds(shortcutX, hair + (title - shortcut.height) / 2, shortcut.width, shortcut.height);
            var name = view(TITLE).getPreferredSize();
            int titleX = hair + s(DesignMetrics.Role.RUN_WINDOW_TITLE_SIDE);
            // The title takes its own width, so the rest of the row stays an empty area that drags the window.
            view(TITLE).setBounds(titleX, hair + (title - name.height) / 2, Math.max(0, Math.min(name.width, shortcutX - titleX)), name.height);
            int y = hair + title + hair + s(DesignMetrics.Role.RUN_WINDOW_HELP_TOP);
            int help = help();
            view(HELP).setBounds(x, y, content, help);
            y += help + s(DesignMetrics.Role.RUN_WINDOW_FIELD_TOP);
            int field = s(DesignMetrics.Role.FIELD_HEIGHT), column = s(DesignMetrics.Role.RUN_WINDOW_LABEL_COLUMN);
            var open = view(OPEN).getPreferredSize();
            view(OPEN).setBounds(x, y + (field - open.height) / 2, Math.min(open.width, column), open.height);
            fieldBox.setBounds(x + column - ring, y - ring, Math.max(0, content - column) + 2 * ring, field + 2 * ring);
            y += field + s(DesignMetrics.Role.RUN_WINDOW_LIST_TOP);
            int list = listHeight();
            listBox.setBounds(x, y, content, list);
            y += list + s(DesignMetrics.Role.RUN_WINDOW_DETAIL_TOP);
            int line = line();
            var label = view(DETAIL_LABEL).getPreferredSize();
            view(DETAIL_LABEL).setBounds(x, y + (line - label.height) / 2, Math.min(label.width, content), label.height);
            int valueX = x + label.width + (label.width > 0 ? s(DesignMetrics.Role.RUN_WINDOW_DETAIL_LABEL_GAP) : 0);
            var value = view(DETAIL_VALUE).getPreferredSize();
            view(DETAIL_VALUE).setBounds(valueX, y + (line - value.height) / 2, Math.max(0, x + content - valueX), value.height);
            y += line + s(DesignMetrics.Role.RUN_WINDOW_CHECK_TOP);
            int check = check();
            var box = view(SPLIT).getPreferredSize();
            view(SPLIT).setBounds(x - ring, y + (check - box.height) / 2, box.width, box.height);
            var splitLabel = view(SPLIT_LABEL).getPreferredSize();
            int labelX = x + s(DesignMetrics.Role.CHECKBOX_BOX) + s(DesignMetrics.Role.CHECKBOX_LABEL_GAP);
            view(SPLIT_LABEL).setBounds(labelX, y + (check - splitLabel.height) / 2, Math.max(0, Math.min(splitLabel.width, x + content - labelX)), splitLabel.height);
            int foot = s(DesignMetrics.Role.RUN_WINDOW_FOOTER_HEIGHT), height = s(DesignMetrics.Role.BUTTON_HEIGHT);
            int buttonY = getHeight() - hair - foot + (foot - height) / 2, buttonX = right - side;
            for (String id : List.of(BROWSE, CANCEL, PRIMARY)) {
                int width = Math.max(s(DesignMetrics.Role.RUN_WINDOW_BUTTON_WIDTH), view(id).getPreferredSize().width);
                buttonX -= width;
                view(id).setBounds(buttonX, buttonY, width, height);
                buttonX -= s(DesignMetrics.Role.RUN_WINDOW_BUTTON_GAP);
            }
        }
    }

    /** Inside the field box: the chip, the query filling the row, and the ▼ at the box's end. */
    private final class FieldLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) { return parent.getSize(); }
        @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, 0); }
        @Override public void layoutContainer(Container parent) {
            var insets = parent.getInsets();
            int x = insets.left + s(DesignMetrics.Role.RUN_WINDOW_FIELD_SIDE), top = insets.top;
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom), end = parent.getWidth() - insets.right;
            int menu = s(DesignMetrics.Role.RUN_WINDOW_MENU_WIDTH);
            view(SCOPES).setBounds(end - menu, top, menu, height);
            if (view(CHIP).isVisible()) {
                var chipSize = view(CHIP).getPreferredSize();
                view(CHIP).setBounds(x, top + (height - chipSize.height) / 2, chipSize.width, chipSize.height);
                x += chipSize.width + s(DesignMetrics.Role.RUN_WINDOW_FIELD_SIDE);
            }
            view(QUERY).setBounds(x, top, Math.max(0, end - menu - x), height);
        }
    }
}
```

Seal the interface and add the ledger rows:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/palette/PaletteView.java"); text = path.read_text(encoding="utf-8")
old = "public interface PaletteView extends AutoCloseable {"
if text.count(old) != 1: raise SystemExit("PaletteView: the declaration moved")
text = text.replace(old, "public sealed interface PaletteView extends AutoCloseable permits CardView, RunWindowView {")
old = "the Run window ({@code RunWindowView})"
if text.count(old) != 1: raise SystemExit("PaletteView: the class comment moved")
path.write_text(text.replace(old, "the Run window ({@link RunWindowView})"), encoding="utf-8")
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
rows = [
    "jasper-app/src/main/java/dev/jasper/app/palette/RunWindowView.java,app.palette,RunWindowView,standard-ui,8,8,"
    "jasper-app/src/test/java/dev/jasper/app/palette/RunWindowViewTest.java,New in Enterprise plan 8a Task 5; the Run window (spec 11.2); layout only: kit views on the kit's RUN_WINDOW; FIELD and BOX surfaces",
    "jasper-app/src/test/java/dev/jasper/app/palette/RunWindowViewTest.java,app.palette,RunWindowViewTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/palette/RunWindowViewTest.java,Test/fixture ownership; New in Enterprise plan 8a Task 5",
    "jasper-app/src/test/java/dev/jasper/app/palette/RunWindowFixtures.java,app.palette,RunWindowFixtures,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/palette/RunWindowViewTest.java,Test/fixture ownership; New in Enterprise plan 8a Task 5; the 06 Run Dialog mocks' data"]
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
```

`ScopePrefix.scope()` is the `PaletteScope` a typed prefix names (`ScopePrefix.java`), so a prefix sets the chip through
`chooseScope` as the card's `chipTyped` does. `SurfacePanel.measuredHeight(KitView, width)` measures a one-root view
through its width layout (`SurfacePanel.java:336-350`), which `RunWindowPresentation`'s help layout implements. The
checkbox row is `CHECKBOX_BOX` plus two focus rings tall, the classic checkbox's own size, whichever face draws the
toggle (R7): before plan 4b the toggle is the default switch, centred in that row, and the window stays 427 tall.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS: `RunWindowViewTest` 9 tests and every earlier palette test. A geometry assertion that is off by the
toggle's focus reserve or a label's inset is fixed in `WindowLayout` (the mock's numbers in the test stay).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/palette jasper-app/src/test/java/dev/jasper/app/palette docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Run window: the Run window view

RunWindowView is the palette's second view: the title row with the
opener's shortcut and close, the help tile and text, Open with the chip,
the data-face query and the scope menu, the fixed-height list with Recent,
Most used or one Matches heading and its count, hue squares, details and
tags, the item's detail line, the split-pane checkbox that follows the
selection, and the primary verb, Cancel and Browse. It only lays out kit
views; jasper-ui paints every pixel. Not reachable yet.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: `PaletteViews.of`, and one contract for both views

`PaletteViews.of(PaletteLook, …)` is the one place that reads `PaletteLook.view()` (spec 3.3, 9.1): CARD builds the card,
RUN_WINDOW the Run window. The controller takes the look, the opener's shortcut text and Browse…'s action, and builds its
view there; `WindowCommandPalette` passes `PaletteLook.DEFAULT` until 8b gives it the window's look. The shared contract
(spec 16.1.9) runs against both views through the controller: Enter runs the view's primary verb (the card's verb 0; the
Run window's verb 0, or SPLIT when ticked), the second and third verbs and Escape, Tab and Backspace behave the same,
the SPLIT box gates, "i of n" counts, and steps behave the same.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteViews.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/presentation/PaletteLook.java` (two constants), `jasper-app/src/main/java/dev/jasper/app/palette/PaletteView.java` (the class comment's `{@link PaletteViews}`), `jasper-app/src/main/java/dev/jasper/app/palette/PaletteController.java:42-51`, `jasper-app/src/main/java/dev/jasper/app/palette/package-info.java:3`, `jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java:59-61`
- Test: `jasper-app/src/test/java/dev/jasper/app/palette/PaletteViewContractTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (rows for `PaletteViews.java` and the test)

**Interfaces:**
- Consumes: Tasks 1 to 5; 5a's `PaletteLook(View, Home)`; 3e's `WindowContent.openFile()`.
- Produces: `public final class PaletteViews` with `static PaletteView of(PaletteLook, KitContext, boolean macOs,
  PaletteView.Host)`; `PaletteLook.DEFAULT` (CARD, TITLE_FIELD) and `PaletteLook.RUN_WINDOW` (RUN_WINDOW, CORNER);
  `PaletteController(KitContext, PaletteLook, ScopeRegistry, CommandHistory, boolean macOs, Runnable layout, Runnable
  dismissed, BooleanSupplier batching, Runnable updateActions, Consumer<String> reportError, Consumer<String> reopen,
  Supplier<String> openerShortcut, Runnable browse)` (the former ten-argument constructor stays and means the card).

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/palette/PaletteViewContractTest.java`:

```java
package dev.jasper.app.palette;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 16.1.9: PaletteViewContractTest. Both views honour one contract; a disagreement is a host bug. */
class PaletteViewContractTest {
    static final List<PaletteLook> LOOKS = List.of(PaletteLook.DEFAULT, PaletteLook.RUN_WINDOW);

    /** A scope whose rows are its names, whose runs are recorded, and whose third verb asks a step. */
    static PaletteScope scope(String id, String label, String noun, List<PaletteVerb> verbs, List<String> runs, String... rows) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public String placeholder() { return "Search " + label; }
            @Override public List<PaletteVerb> verbs() { return verbs; }
            @Override public Nodes.Hue hue() { return Nodes.Hue.GREEN; }
            @Override public String itemNoun() { return noun; }
            @Override public PaletteResults search(String query, PaletteContext context) {
                var found = new ArrayList<PaletteRow>();
                for (String row : rows) if (row.contains(query)) found.add(new PaletteRow(row, row, "detail of " + row, null, null, true, null));
                return new PaletteResults(found.subList(0, Math.min(found.size(), context.maxResults())), null, null);
            }
            @Override public PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) {
                if (!verb.id().equals("ask")) return null;
                return new PaletteStep("Ask", List.of(new PaletteStep.Field("one", "One", "1"), new PaletteStep.Field("two", "Two", "2")),
                    (values, done) -> { runs.add(id + "/" + row.id() + ":ask:" + values); done.accept(PaletteStep.Result.done()); });
            }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { runs.add(id + "/" + row.id() + ":" + verb.id()); }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    record Harness(PaletteController controller, List<String> runs, List<String> browsed, ScopeRegistry registry, CommandHistory history) implements AutoCloseable {
        @Override public void close() { controller.close(); registry.close(); history.close(); }
        void open() { controller.open(PaletteScope.ALL_ID, PaletteTarget.none(), () -> true); }
        void query(String text) { controller.view().setQueryText(text); }
    }

    static Harness harness(PaletteLook look) {
        var runs = new ArrayList<String>();
        var browsed = new ArrayList<String>();
        var registry = new ScopeRegistry();
        var history = new CommandHistory();
        registry.register(scope("t.hosts", "Hosts", "session", List.of(new PaletteVerb("connect", "Connect"),
            new PaletteVerb("split", "Connect in split", PaletteVerb.Role.SPLIT), new PaletteVerb("ask", "Ask")), runs, "mac", "minecraft"));
        registry.register(scope("t.snippets", "Snippets", "snippet", List.of(new PaletteVerb("insert", "Insert"),
            new PaletteVerb("insert_run", "Insert and run")), runs, "gcloud auth", "make"));
        var controller = new PaletteController(KitTestSupport.context(), look, registry, history, true, () -> { }, () -> { }, () -> false, () -> { },
            message -> { }, id -> { }, () -> "⇧⌘P", () -> browsed.add("browse"));
        return new Harness(controller, runs, browsed, registry, history);
    }

    @Test void enterRunsTheViewsPrimaryVerbAndTheOtherVerbsKeepTheirKeys() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var look : LOOKS) try (var h = harness(look)) {
                h.open(); h.query("mac");
                h.controller().enterPressed(0);
                h.open(); h.query("mac");
                h.controller().enterPressed(1);
                h.open(); h.query("make");
                h.controller().enterPressed(1);
                assertThat(h.runs()).as(look.toString()).containsExactly("t.hosts/mac:connect", "t.hosts/mac:split", "t.snippets/make:insert_run");
                assertThat(h.controller().isOpen()).as("running a row closes both views").isFalse();
            }
        });
    }

    @Test void onlyTheRunWindowsTickedBoxMakesEnterSplitAndOnlyWhereASplitVerbExists() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var h = harness(PaletteLook.RUN_WINDOW)) {
                h.open(); h.query("mac");
                var view = (RunWindowView) h.controller().view();
                view.handle(new dev.jasper.ui.model.UiEvent.Toggled(RunWindowView.SPLIT, true));
                h.controller().enterPressed(0);
                h.open();
                assertThat(view.splitTicked()).as("cleared when the window opens").isFalse();
                h.query("make");
                view.handle(new dev.jasper.ui.model.UiEvent.Toggled(RunWindowView.SPLIT, true));
                assertThat(view.splitTicked()).as("a snippet cannot be ticked").isFalse();
                h.controller().enterPressed(0);
                assertThat(h.runs()).containsExactly("t.hosts/mac:split", "t.snippets/make:insert");
            }
            try (var h = harness(PaletteLook.DEFAULT)) {
                h.open(); h.query("mac");
                assertThat(h.controller().view()).isInstanceOf(CardView.class);
                h.controller().enterPressed(0);
                assertThat(h.runs()).as("the card's Enter is always verb 0").containsExactly("t.hosts/mac:connect");
            }
        });
    }

    @Test void escapeTabAndBackspaceBehaveTheSameInBothViews() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var look : LOOKS) try (var h = harness(look)) {
                h.open();
                assertThat(h.controller().tabPressed()).as(look + ": Tab is the palette's").isTrue();
                h.controller().selectTab("t.snippets");
                assertThat(h.controller().activeScopeId()).isEqualTo("t.snippets");
                assertThat(h.controller().backspacePressed()).as(look + ": Backspace in an empty query removes the chip").isTrue();
                assertThat(h.controller().activeScopeId()).isEqualTo(PaletteScope.ALL_ID);
                h.controller().escape();
                assertThat(h.controller().isOpen()).as(look + ": Escape closes").isFalse();
            }
        });
    }

    /** Review Focus 5. */
    @Test void stepsBehaveTheSameInBothViews() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var look : LOOKS) try (var h = harness(look)) {
                h.open(); h.query("mac");
                h.controller().enterPressed(2);
                assertThat(h.controller().stepOpen()).as(look.toString()).isTrue();
                assertThat(h.controller().view().stepShowing()).isTrue();
                h.controller().escape();
                assertThat(h.controller().stepOpen()).as(look + ": Escape leaves the step").isFalse();
                assertThat(h.controller().isOpen()).isTrue();
                h.controller().enterPressed(2);
                h.controller().enterPressed(0);
                assertThat(h.runs()).as(look + ": the step's values, in field order").containsExactly("t.hosts/mac:ask:{one=1, two=2}");
            }
        });
    }

    @Test void theRunWindowCountsMatchesAndShowsTheOpenersShortcutAndBrowses() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var h = harness(PaletteLook.RUN_WINDOW)) {
                h.open(); h.query("a");
                var view = (RunWindowView) h.controller().view();
                var list = (Nodes.ItemList) view.viewFor(RunWindowView.RESULTS).spec().root();
                var heading = list.items().getFirst();
                assertThat(((Nodes.Text) heading.title().getFirst()).text()).isEqualTo("Matches");
                assertThat(((Nodes.Text) heading.title().get(1)).text()).as("mac, minecraft, gcloud auth and make match").isEqualTo("1 of 4");
                assertThat(((Nodes.Text) view.viewFor(RunWindowView.SHORTCUT).spec().root()).text()).isEqualTo("⇧⌘P");
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.BROWSE));
                assertThat(h.browsed()).containsExactly("browse");
                view.handle(new dev.jasper.ui.model.UiEvent.Action(RunWindowView.CANCEL));
                assertThat(h.controller().isOpen()).isFalse();
            }
        });
    }
}
```

The step's values print as `{one=1, two=2}`: `StepForm.values()` is a `LinkedHashMap` in field order.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.PaletteViewContractTest'`
Expected: FAIL: `compileTestJava` reports "cannot find symbol … DEFAULT" (`PaletteLook`) and the thirteen-argument
`PaletteController` constructor.

- [ ] **Step 3: The factory and the controller**

Create `jasper-app/src/main/java/dev/jasper/app/palette/PaletteViews.java`:

```java
package dev.jasper.app.palette;

import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.ui.swing.KitContext;

/**
 * Builds the palette's view (Enterprise presentation spec 3.3 and 11.1): the card or the Run window, as the look's
 * {@link PaletteLook#view()} says. The only reader of that variant.
 */
public final class PaletteViews {
    private PaletteViews() { }

    /** The view {@code look} names, calling back {@code host}, over the chrome context {@code kit}. */
    public static PaletteView of(PaletteLook look, KitContext kit, boolean macOs, PaletteView.Host host) {
        return switch (look.view()) {
            case CARD -> new CardView(kit, macOs, host);
            case RUN_WINDOW -> new RunWindowView(kit, macOs, host);
        };
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 8a Task 6 step 3: PaletteLook's constants, the controller's look and the window adapter's arguments."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
L = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/presentation/PaletteLook.java"); text = L.read_text(encoding="utf-8")
if "PaletteLook DEFAULT" not in text:
    old = "    public PaletteLook { Objects.requireNonNull(view); Objects.requireNonNull(home); }"
    if text.count(old) != 1: raise SystemExit("PaletteLook: its compact constructor moved")
    text = text.replace(old, old + '''

    /** The default look's palette: the card dropped from the title field. */
    public static final PaletteLook DEFAULT = new PaletteLook(View.CARD, Home.TITLE_FIELD);
    /** The Run window at the bottom-left of the dock work area (spec 11.5). */
    public static final PaletteLook RUN_WINDOW = new PaletteLook(View.RUN_WINDOW, Home.CORNER);''')
    L.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/palette/"
edit(P + "PaletteController.java", [
    ('''    public PaletteController(dev.jasper.ui.swing.KitContext kit, ScopeRegistry scopes, dev.jasper.app.commands.CommandHistory history, boolean macOs,
                      Runnable layout, Runnable dismissed, BooleanSupplier batching, Runnable updateActions,
                      Consumer<String> reportError, Consumer<String> reopen) {
        this.scopes = scopes;''',
     '''    /** The default look's card, with no opener shortcut and no Browse… action. */
    public PaletteController(dev.jasper.ui.swing.KitContext kit, ScopeRegistry scopes, dev.jasper.app.commands.CommandHistory history, boolean macOs,
                      Runnable layout, Runnable dismissed, BooleanSupplier batching, Runnable updateActions,
                      Consumer<String> reportError, Consumer<String> reopen) {
        this(kit, dev.jasper.app.presentation.PaletteLook.DEFAULT, scopes, history, macOs, layout, dismissed, batching, updateActions,
            reportError, reopen, () -> "", () -> { });
    }

    /**
     * A controller whose view {@code look} names (spec 11.1); {@code openerShortcut} is the opener's current shortcut text
     * and {@code browse} runs File, Open File… (spec 11.7).
     */
    public PaletteController(dev.jasper.ui.swing.KitContext kit, dev.jasper.app.presentation.PaletteLook look, ScopeRegistry scopes,
                      dev.jasper.app.commands.CommandHistory history, boolean macOs, Runnable layout, Runnable dismissed,
                      BooleanSupplier batching, Runnable updateActions, Consumer<String> reportError, Consumer<String> reopen,
                      java.util.function.Supplier<String> openerShortcut, Runnable browse) {
        this.scopes = scopes;''', 1),
    ('''        palette = new CardView(kit, macOs, new PaletteView.Host(this::queryChanged, this::execute, this::prefix,
            scope -> selectTab(scope == null ? PaletteScope.ALL_ID : scope.id()), this::modeScopes, () -> "", this::dismiss, () -> { }));''',
     '''        palette = PaletteViews.of(look, kit, macOs, new PaletteView.Host(this::queryChanged, this::execute, this::prefix,
            scope -> selectTab(scope == null ? PaletteScope.ALL_ID : scope.id()), this::modeScopes, openerShortcut, this::dismiss, browse));''', 1)])
edit(P + "PaletteView.java", [("built by {@code PaletteViews}", "built by {@link PaletteViews}", 1)])
edit(P + "package-info.java", [
    (" * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.commands, dev.jasper.app.config, dev.jasper.app.lifecycle, dev.jasper.app.platform.",
     " * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.commands, dev.jasper.app.config, dev.jasper.app.lifecycle, dev.jasper.app.platform,\n"
     " * dev.jasper.app.presentation (PaletteLook, read only by PaletteViews).", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java", [
    ('''        controller = new PaletteController(kit, scopes, history, macOs, this::layoutOverlay, this::restoreAndHide,
            owner::updatingActions, owner::updateActions,
            message -> owner.onError.accept("Command failed", message), this::open);''',
     '''        controller = new PaletteController(kit, dev.jasper.app.presentation.PaletteLook.DEFAULT, scopes, history, macOs, this::layoutOverlay,
            this::restoreAndHide, owner::updatingActions, owner::updateActions,
            message -> owner.onError.accept("Command failed", message), this::open, owner::paletteShortcut, owner::openFile);''', 1)])
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
rows = [
    "jasper-app/src/main/java/dev/jasper/app/palette/PaletteViews.java,app.palette,PaletteViews,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/palette/PaletteViewContractTest.java,New in Enterprise plan 8a Task 6; the palette view factory (spec 3.3); the only PaletteLook.view reader",
    "jasper-app/src/test/java/dev/jasper/app/palette/PaletteViewContractTest.java,app.palette,PaletteViewContractTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/palette/PaletteViewContractTest.java,Test/fixture ownership; New in Enterprise plan 8a Task 6; spec 16.1.9"]
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
```

5a's `PaletteLook` has no constants (its `Projections` builds the looks), so the script adds both; `Projections` keeps
naming `PaletteLook.View` to build them (R15). `owner::openFile` is 3e's package-private `WindowContent.openFile()`
(`WindowContent.java:676`); `owner::paletteShortcut` is `WindowContent.java:688`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.workspace.*Palette*' --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture`
Expected: PASS: `PaletteViewContractTest` 5 tests, the earlier palette tests, the window-palette tests (the card is still
what every window builds) and the architecture check (`palette` now depends on `presentation`; no cycle).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/palette jasper-app/src/main/java/dev/jasper/app/presentation/PaletteLook.java \
  jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java jasper-app/src/test/java/dev/jasper/app/palette \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Run window: PaletteViews.of and one contract for both views

The controller builds its view through PaletteViews.of from the look's
palette: the card or the Run window. Both views honour one contract: Enter
runs the view's primary verb (the Run window's split verb while its box is
ticked), the other verbs, Escape, Tab, Backspace and steps behave the same.
Every window still builds the card.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: The Run window against the 06 Run Dialog mocks

A headless JUnit render of both mocked states, at 2×, under the Enterprise fixture kit with its 12-point body and the
Enterprise Light fixture theme, compared with the mocks (Rulings 12): geometry features probed the same way in the
mock and the render, fill colours sampled at the same points, and luminance-edge agreement per band. Each probe and
fill names its owner: this plan's layout and surfaces are held to the mock now; plan 4b's classic checkbox and buttons
and plan 5b's IBM Plex text are measured and printed until those plans are in this branch (R8, R9). Dark (Slate Cobalt
Dark until 5b's Enterprise Dark) is rendered and checked for structure and contrast (no Dark mock shows the window). The images and comparison sheets land under
`jasper-app/build/run-window/`; `tools/visual/pairs.json` gains the opt-in pairs.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/testsupport/MockComparison.java`, `jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java`
- Modify: `jasper-app/build.gradle.kts:102` (the mocks as a test input), `tools/visual/pairs.json` (four pairs), `docs/design/theme-engine/migration.csv` (two rows)

**Interfaces:**
- Consumes: Task 5's `RunWindowView`, `RunWindowFixtures`; Task 4's `SurfacePanel.focusShown`; Task 3's
  `KitFixtures.enterpriseLight()`; 4a's `KitFixtures.enterprise()`; `KitGallery.installed` and
  `KitFixtures.platformFamily()` (jasper-ui test fixtures).
- Produces: `static void RunWindowRenderTest.check(BufferedImage render, String state, KitContext c, Path directory)` (8b
  reuses it; on the EDT with `c` open), `static BufferedImage RunWindowRenderTest.render(boolean dark, String state,
  RunWindowRenderTest.Drawn then)`, `RunWindowRenderTest.Owner { RUN_WINDOW, FACES, FONTS }` and `CHECKED` (no
  `fonts()`: nothing registers Plex before 5b; R16); `public final class MockComparison` with `static double deltaE(Color, Color)`, `static int edge(BufferedImage,
  int x, int y, int dx, int dy, int steps, Color background)`, `static boolean[][] edges(BufferedImage, Rectangle,
  List<Rectangle> masks)`, `static double agreement(boolean[][], boolean[][])`, `static double bestAgreement(BufferedImage
  mock, Rectangle mockArea, BufferedImage render, Rectangle renderArea, List<Rectangle> masks, int search)`, `static
  BufferedImage side(BufferedImage mock, Rectangle mockArea, BufferedImage render, Rectangle renderArea)`; the renders
  `jasper-app/build/run-window/{light,dark}/{just-opened,typing-auth}@2x.png` and `…-side.png`.

- [ ] **Step 1: Write the comparison helper and the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/testsupport/MockComparison.java`:

```java
package dev.jasper.app.testsupport;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

/**
 * Compares a render with a preserved mock, as {@code tools/visual/compare_mock.py} does: luminance edges (the 90th
 * percentile's half, at least 12, of the central-difference gradient), agreement as the mean of both directions' share
 * of edge pixels within one pixel of the other's, CIE76 colour distance, and first-change probes along a line. Pure.
 */
public final class MockComparison {
    private MockComparison() { }

    /** CIE76 distance between two sRGB colours (D65). */
    public static double deltaE(Color a, Color b) {
        double[] p = lab(a), q = lab(b);
        return Math.sqrt(Math.pow(p[0] - q[0], 2) + Math.pow(p[1] - q[1], 2) + Math.pow(p[2] - q[2], 2));
    }

    private static double[] lab(Color color) {
        double r = linear(color.getRed()), g = linear(color.getGreen()), b = linear(color.getBlue());
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047, y = 0.2126 * r + 0.7152 * g + 0.0722 * b, z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) { return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116; }

    /**
     * Steps from {@code (x, y)} by {@code (dx, dy)} and returns how many steps it took to reach a pixel more than 6 ΔE76
     * from {@code background}; -1 when none is reached in {@code steps}.
     */
    public static int edge(BufferedImage image, int x, int y, int dx, int dy, int steps, Color background) {
        for (int i = 0; i < steps; i++) {
            int px = x + i * dx, py = y + i * dy;
            if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) return -1;
            if (deltaE(new Color(image.getRGB(px, py)), background) > 6) return i;
        }
        return -1;
    }

    /** The edge mask of {@code area}, false inside every mask rectangle (given in the area's coordinates). */
    public static boolean[][] edges(BufferedImage image, Rectangle area, List<Rectangle> masks) {
        int w = area.width, h = area.height;
        double[][] gray = new double[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int rgb = image.getRGB(area.x + x, area.y + y);
            gray[y][x] = ((rgb >> 16 & 255) * 299 + (rgb >> 8 & 255) * 587 + (rgb & 255) * 114) / 1000.0;
        }
        double[] magnitudes = new double[w * h];
        double[][] magnitude = new double[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double gx = x > 0 && x < w - 1 ? gray[y][x + 1] - gray[y][x - 1] : 0;
            double gy = y > 0 && y < h - 1 ? gray[y + 1][x] - gray[y - 1][x] : 0;
            magnitude[y][x] = Math.hypot(gx, gy);
            magnitudes[y * w + x] = magnitude[y][x];
        }
        double threshold = Math.max(12.0, percentile(magnitudes, 90) * 0.5);
        boolean[][] mask = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean masked = false;
            for (Rectangle m : masks) if (m.contains(x, y)) { masked = true; break; }
            mask[y][x] = !masked && magnitude[y][x] > threshold;
        }
        return mask;
    }

    /** numpy's default (linear) percentile. */
    static double percentile(double[] values, double percent) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double rank = percent / 100.0 * (sorted.length - 1);
        int low = (int) Math.floor(rank), high = (int) Math.ceil(rank);
        return sorted[low] + (sorted[high] - sorted[low]) * (rank - low);
    }

    private static boolean[][] dilate(boolean[][] mask) {
        int h = mask.length, w = h == 0 ? 0 : mask[0].length;
        boolean[][] out = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (mask[y][x])
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int ny = y + dy, nx = x + dx;
                if (ny >= 0 && ny < h && nx >= 0 && nx < w) out[ny][nx] = true;
            }
        return out;
    }

    /** Mean of both directions' share of edge pixels within one pixel of the other's edges; 0 when either has none. */
    public static double agreement(boolean[][] a, boolean[][] b) {
        boolean[][] da = dilate(a), db = dilate(b);
        long ca = 0, cb = 0, ab = 0, ba = 0;
        for (int y = 0; y < a.length; y++) for (int x = 0; x < a[0].length; x++) {
            if (a[y][x]) { ca++; if (db[y][x]) ab++; }
            if (b[y][x]) { cb++; if (da[y][x]) ba++; }
        }
        return ca == 0 || cb == 0 ? 0 : ((double) ab / ca + (double) ba / cb) / 2;
    }

    /** The best agreement of the two areas with the render shifted by at most {@code search} pixels each way. */
    public static double bestAgreement(BufferedImage mock, Rectangle mockArea, BufferedImage render, Rectangle renderArea,
                                       List<Rectangle> masks, int search) {
        var reference = edges(mock, mockArea, masks);
        double best = 0;
        for (int dy = -search; dy <= search; dy++) for (int dx = -search; dx <= search; dx++) {
            var shifted = new Rectangle(renderArea.x + dx, renderArea.y + dy, renderArea.width, renderArea.height);
            if (shifted.x < 0 || shifted.y < 0 || shifted.x + shifted.width > render.getWidth() || shifted.y + shifted.height > render.getHeight()) continue;
            best = Math.max(best, agreement(reference, edges(render, shifted, masks)));
        }
        return best;
    }

    /** Mock | render, side by side, for inspection. */
    public static BufferedImage side(BufferedImage mock, Rectangle mockArea, BufferedImage render, Rectangle renderArea) {
        var image = new BufferedImage(mockArea.width + renderArea.width + 8, Math.max(mockArea.height, renderArea.height), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setColor(Color.MAGENTA); g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(mock.getSubimage(mockArea.x, mockArea.y, mockArea.width, mockArea.height), 0, 0, null);
            g.drawImage(render.getSubimage(renderArea.x, renderArea.y, renderArea.width, renderArea.height), mockArea.width + 8, 0, null);
        } finally { g.dispose(); }
        return image;
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java`:

```java
package dev.jasper.app.palette;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.MockComparison;
import dev.jasper.ui.gallery.KitGallery;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Plan 8a Rulings 12: the Run window under the Enterprise fixture kit (the classic catalog with the shipped tokens and
 * its 12-point body) and the Enterprise Light fixture theme against the two 06 Run Dialog mocks, at 2x. The mocks draw
 * the window's frame at x 60-983, y 854-1695; the render is the window alone. The part under the list is compared 12
 * device pixels lower (plan 8a Rulings 5). Images go to {@code jasper-app/build/run-window/}. Every comparison runs on
 * the EDT inside {@code KitGallery.installed}, while the context that drew the render is open (installed closes its
 * binding when its action returns). What plan 4b's classic faces and plan 5b's IBM Plex draw is measured and printed
 * until those plans are in this branch ({@link #CHECKED}, plan 8a R9).
 */
public class RunWindowRenderTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    static final Path OUT = ROOT.resolve("jasper-app/build/run-window");
    static final Path MOCKS = ROOT.resolve("docs/design/enterprise/reference/06 Run Dialog");
    static final int MOCK_X = 60, MOCK_Y = 854, WIDTH = 924, MOCK_HEIGHT = 842, SHIFT = 12, BAND = 632;
    /** The scrollbar's column over the list, and the B mock's "1 of 11" (its fixture count), in window pixels. */
    static final Rectangle SCROLLBAR = new Rectangle(870, 228, 28, 406), COUNT = new Rectangle(800, 232, 98, 38);

    /** Who draws a measured part: this plan's layout and surfaces, plan 4b's classic checkbox and buttons, or plan 5b's IBM Plex text. */
    enum Owner { RUN_WINDOW, FACES, FONTS }
    /** The owners whose parts are held to the mock now; FACES joins when plan 4b is in this branch, FONTS with plan 5b. */
    static final java.util.Set<Owner> CHECKED = java.util.EnumSet.of(Owner.RUN_WINDOW);

    /** A probe: from (x, y) step (dx, dy) to the first pixel off {@code background}; {@code lower} probes under the list. */
    record Feature(String name, int x, int y, int dx, int dy, Function<KitContext, Color> background, boolean lower, int tolerance, Owner owner) {
        Feature(String name, int x, int y, int dx, int dy, Function<KitContext, Color> background, boolean lower, int tolerance) {
            this(name, x, y, dx, dy, background, lower, tolerance, Owner.RUN_WINDOW);
        }
    }

    static Color semantic(KitContext c, SemanticColor role) { return c.theme().semantic().color(role); }
    static Color derived(KitContext c, DerivedColor role) { return role.color(c.theme()); }
    static Color dialog(KitContext c) { return semantic(c, c.kit().colors().dialogSurface()); }

    static final List<Feature> FEATURES = List.of(
        new Feature("title band to its rule", 300, 10, 0, 1, c -> derived(c, DerivedColor.SURFACE_HEADER_ACTIVE), false, 1),
        new Feature("title text", 4, 26, 1, 0, c -> derived(c, DerivedColor.SURFACE_HEADER_ACTIVE), false, 3),
        new Feature("shortcut text end", 880, 26, -1, 0, c -> derived(c, DerivedColor.SURFACE_HEADER_ACTIVE), false, 3, Owner.FONTS),
        new Feature("close glyph", 860, 26, 1, 0, c -> derived(c, DerivedColor.SURFACE_HEADER_ACTIVE), false, 3),
        new Feature("help tile top", 58, 54, 0, 1, RunWindowRenderTest::dialog, false, 1),
        new Feature("help tile left", 4, 108, 1, 0, RunWindowRenderTest::dialog, false, 1),
        new Feature("help text", 92, 95, 1, 0, RunWindowRenderTest::dialog, false, 3),
        new Feature("open label", 4, 190, 1, 0, RunWindowRenderTest::dialog, false, 3),
        new Feature("field box left", 92, 186, 1, 0, RunWindowRenderTest::dialog, false, 1),
        new Feature("field box top", 500, 120, 0, 1, RunWindowRenderTest::dialog, false, 1),
        new Feature("field box right", 920, 186, -1, 0, RunWindowRenderTest::dialog, false, 1),
        new Feature("menu arrow", 860, 186, 1, 0, c -> semantic(c, SemanticColor.SURFACE_INPUT), false, 3),
        new Feature("list box top", 500, 215, 0, 1, RunWindowRenderTest::dialog, false, 1),
        new Feature("list box left", 4, 300, 1, 0, RunWindowRenderTest::dialog, false, 1),
        new Feature("list box right", 920, 300, -1, 0, RunWindowRenderTest::dialog, false, 1),
        new Feature("heading rule above the selected row", 500, 280, 0, -1, c -> semantic(c, SemanticColor.ACCENT), false, 1),
        new Feature("heading text", 30, 250, 1, 0, c -> derived(c, DerivedColor.SURFACE_TABLE_HEADER), false, 3),
        new Feature("selected row to the next", 500, 275, 0, 1, c -> semantic(c, SemanticColor.ACCENT), false, 1),
        new Feature("hue square left", 30, 290, 1, 0, c -> semantic(c, SemanticColor.ACCENT), false, 1),
        new Feature("hue square top", 47, 272, 0, 1, c -> semantic(c, SemanticColor.ACCENT), false, 1),
        new Feature("row title", 60, 290, 1, 0, c -> semantic(c, SemanticColor.ACCENT), false, 3),
        new Feature("detail label", 4, 671, 1, 0, RunWindowRenderTest::dialog, true, 3, Owner.FONTS),
        new Feature("detail value", 120, 671, 1, 0, RunWindowRenderTest::dialog, true, 3, Owner.FONTS),
        new Feature("checkbox left", 4, 721, 1, 0, RunWindowRenderTest::dialog, true, 1, Owner.FACES),
        new Feature("checkbox top", 36, 696, 0, 1, RunWindowRenderTest::dialog, true, 1, Owner.FACES),
        new Feature("checkbox label", 56, 721, 1, 0, RunWindowRenderTest::dialog, true, 3, Owner.FACES),
        new Feature("footer rule", 200, 740, 0, 1, RunWindowRenderTest::dialog, true, 1),
        new Feature("primary button left", 300, 796, 1, 0, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1, Owner.FACES),
        new Feature("cancel button left", 596, 796, 1, 0, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1, Owner.FACES),
        new Feature("browse button right", 920, 796, -1, 0, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1),
        new Feature("button top", 520, 765, 0, 1, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1),
        new Feature("window bottom", 200, 800, 0, 1, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1),
        new Feature("window right", 900, 150, 1, 0, RunWindowRenderTest::dialog, false, 1));
    /** A only: the second heading (by the list's right edge, clear of row text and the mock's scrollbar) and the selected row's detail. */
    static final List<Feature> OPENED = List.of(
        new Feature("second heading", 893, 320, 0, 1, c -> semantic(c, SemanticColor.SURFACE_CARD), false, 1),
        new Feature("row detail", 118, 290, 1, 0, c -> semantic(c, SemanticColor.ACCENT), false, 3));
    /** B only: the query's first glyph. */
    static final List<Feature> TYPING = List.of(
        new Feature("query text", 125, 186, 1, 0, c -> semantic(c, SemanticColor.SURFACE_INPUT), false, 3, Owner.FONTS));

    record Sample(String name, int x, int y, boolean lower, Owner owner) {
        Sample(String name, int x, int y, boolean lower) { this(name, x, y, lower, Owner.RUN_WINDOW); }
    }
    static final List<Sample> SAMPLES = List.of(new Sample("frame", 200, 0, false), new Sample("title band", 200, 20, false),
        new Sample("body", 20, 400, false), new Sample("help tile", 35, 90, false), new Sample("field", 500, 186, false),
        new Sample("list edge", 26, 300, false), new Sample("heading band", 300, 250, false), new Sample("selected row", 600, 290, false),
        new Sample("row", 860, 330, false), new Sample("hue square", 47, 290, false), new Sample("checkbox inside", 38, 721, true, Owner.FACES),
        new Sample("footer rule", 200, 758, true), new Sample("footer", 200, 820, true), new Sample("primary", 462, 800, true, Owner.FACES));

    /** What runs with a render on the EDT, while the context that drew it is still open. */
    @FunctionalInterface interface Drawn { void accept(BufferedImage render, KitContext context) throws Exception; }

    /**
     * {@code state} rendered at 2x under the shipped kit in {@code dark} or Light, written to {@code build/}, and handed
     * with the context that drew it to {@code then} on the EDT before {@code KitGallery.installed} closes that context.
     */
    static BufferedImage render(boolean dark, String state, Drawn then) throws Exception {
        var kitValue = KitFixtures.enterprise();
        var theme = dark ? KitFixtures.dark() : KitFixtures.enterpriseLight();
        Path directory = Files.createDirectories(OUT.resolve(dark ? "dark" : "light"));
        var image = new BufferedImage[1];
        var failure = new Exception[1];
        try {
            SwingUtilities.invokeAndWait(() -> KitGallery.installed(theme, kitValue, (kit, focus) -> {
                // The kit's 12-point body as the application's font setting (5a's kit defaults); the gallery installs 13.
                javax.swing.UIManager.put("defaultFont", new java.awt.Font(KitFixtures.platformFamily(), java.awt.Font.PLAIN, 12));
                try (var view = RunWindowFixtures.view(kit, new ArrayList<>())) {
                    if (state.equals("typing-auth")) { view.setQueryText("auth"); view.setEntries(RunWindowFixtures.typingAuth(), null, "Nothing found"); }
                    else view.setEntries(RunWindowFixtures.justOpened(), null, "Nothing found");
                    view.fieldBox().focusShown(true);
                    view.setSize(view.getPreferredSize());
                    for (int i = 0; i < 3; i++) dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(view);
                    var out = new BufferedImage(view.getWidth() * 2, view.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
                    var g = out.createGraphics();
                    try {
                        g.setColor(semantic(kit, SemanticColor.SURFACE_APP)); g.fillRect(0, 0, out.getWidth(), out.getHeight());
                        g.scale(2, 2); view.paint(g);
                    } finally { g.dispose(); }
                    image[0] = out;
                    ImageIO.write(out, "png", directory.resolve(state + "@2x.png").toFile());
                    then.accept(out, kit);
                } catch (Exception thrown) { failure[0] = thrown; }
            }));
        } catch (java.lang.reflect.InvocationTargetException wrapped) {
            // An assertion inside then fails the test with its own message.
            if (wrapped.getCause() instanceof Error error) throw error;
            throw wrapped;
        }
        if (failure[0] != null) throw failure[0];
        return image[0];
    }

    static BufferedImage mock(String state) throws Exception {
        return ImageIO.read(MOCKS.resolve(state.equals("typing-auth") ? "Run Dialog - Typing auth (filtered).png" : "Run Dialog - Just opened (recent + most used).png").toFile());
    }

    /** The probe's result in window pixels: in the mock from its window's origin, in the render the part under the list moved up. */
    static int probe(BufferedImage image, int originX, int originY, Feature feature, int shift, KitContext c) {
        int steps = feature.dx() != 0 ? WIDTH : MOCK_HEIGHT;
        int found = MockComparison.edge(image, originX + feature.x(), originY + feature.y() + shift, feature.dx(), feature.dy(), steps, feature.background().apply(c));
        return found < 0 ? -1 : (feature.dx() != 0 ? feature.x() + found * feature.dx() : feature.y() + found * feature.dy());
    }

    void compare(String state) throws Exception { render(false, state, (image, c) -> check(image, state, c, OUT.resolve("light"))); }

    /**
     * {@code render}, the Run window alone at 2x drawn with {@code c}, against {@code state}'s mock; the side image goes to
     * {@code directory}. Call it on the EDT while {@code c} is open: from {@link #render}'s {@code then}, or, as plan 8b's
     * workspace render does for the window it crops from a whole window, inside the theme controller's lifetime.
     */
    public static void check(BufferedImage render, String state, KitContext c, Path directory) throws Exception {
        var mock = mock(state);
        assertThat(render.getWidth()).as("the window's width").isEqualTo(WIDTH);
        assertThat(render.getHeight()).as("the mock's height plus the six pixels of Rulings 5, at 2x").isEqualTo(MOCK_HEIGHT + SHIFT);
        var features = new ArrayList<>(FEATURES);
        features.addAll(state.equals("typing-auth") ? TYPING : OPENED);
        var misses = new ArrayList<String>();
        var pending = new ArrayList<String>();
        for (var feature : features) {
            int expected = probe(mock, MOCK_X, MOCK_Y, feature, 0, c);
            int actual = probe(render, 0, 0, feature, feature.lower() ? SHIFT : 0, c);
            assertThat(expected).as(state + ": the mock shows " + feature.name()).isNotNegative();
            String line = feature.name() + ": mock " + expected + ", render " + actual;
            if (!CHECKED.contains(feature.owner())) pending.add(feature.owner() + " " + line);
            else if (Math.abs(actual - expected) > feature.tolerance()) misses.add(line);
        }
        var colours = new ArrayList<String>();
        for (var sample : SAMPLES) {
            var expected = new Color(mock.getRGB(MOCK_X + sample.x(), MOCK_Y + sample.y()));
            var actual = new Color(render.getRGB(sample.x(), sample.y() + (sample.lower() ? SHIFT : 0)));
            double distance = MockComparison.deltaE(expected, actual);
            String line = sample.name() + ": mock " + ColorMath.hex(expected) + ", render " + ColorMath.hex(actual) + String.format(" (%.1f)", distance);
            if (!CHECKED.contains(sample.owner())) pending.add(sample.owner() + " " + line);
            else if (distance > 3) colours.add(line);
        }
        pending.forEach(line -> System.out.println("run-window " + state + " (not yet checked) " + line));
        assertThat(misses).as(state + ": geometry within tolerance").isEmpty();
        assertThat(colours).as(state + ": fills within 3 dE76").isEmpty();
        var masks = new ArrayList<>(List.of(SCROLLBAR));
        if (state.equals("typing-auth")) masks.add(COUNT);
        double top = MockComparison.bestAgreement(mock, new Rectangle(MOCK_X, MOCK_Y, WIDTH, BAND), render, new Rectangle(0, 0, WIDTH, BAND), masks, 2);
        int lower = MOCK_HEIGHT - BAND - 2;
        double bottom = MockComparison.bestAgreement(mock, new Rectangle(MOCK_X, MOCK_Y + BAND + 2, WIDTH, lower), render,
            new Rectangle(0, BAND + 2 + SHIFT, WIDTH, lower), List.of(), 2);
        ImageIO.write(MockComparison.side(mock, new Rectangle(MOCK_X, MOCK_Y, WIDTH, MOCK_HEIGHT), render, new Rectangle(0, 0, WIDTH, MOCK_HEIGHT + SHIFT)),
            "png", Files.createDirectories(directory).resolve(state + "-side.png").toFile());
        System.out.printf("run-window %s: edge agreement top %.3f, bottom %.3f%n", state, top, bottom);
        assertThat(top).as(state + ": edges above the list's foot").isGreaterThanOrEqualTo(0.70);
        assertThat(bottom).as(state + ": edges under the list").isGreaterThanOrEqualTo(0.70);
    }

    @Test void justOpenedMatchesItsMock() throws Exception { compare("just-opened"); }

    @Test void typingAuthMatchesItsMock() throws Exception { compare("typing-auth"); }

    /** The horizontal one-pixel probes of checked parts: the structure Dark keeps. */
    static List<Feature> structure() { return FEATURES.stream().filter(f -> f.dy() == 0 && f.tolerance() <= 1 && CHECKED.contains(f.owner())).toList(); }

    /** No Dark mock shows the window: Dark keeps the Light geometry and its declared pairs readable. */
    @Test void enterpriseDarkKeepsTheGeometryAndTheTextReadable() throws Exception {
        var light = new java.util.LinkedHashMap<String, Integer>();
        var lightImage = render(false, "just-opened", (image, c) -> {
            for (var feature : structure()) light.put(feature.name(), probe(image, 0, 0, feature, feature.lower() ? SHIFT : 0, c));
        });
        render(true, "just-opened", (dark, c) -> {
            assertThat(dark.getWidth()).isEqualTo(lightImage.getWidth());
            assertThat(dark.getHeight()).isEqualTo(lightImage.getHeight());
            assertThat(c.theme().dark()).isTrue();
            var palette = c.theme().semantic();
            assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_PRIMARY), derived(c, DerivedColor.SURFACE_HEADER_ACTIVE))).as("the title").isGreaterThanOrEqualTo(4.5);
            assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_STRONG_SECONDARY), dialog(c))).as("labels and help").isGreaterThanOrEqualTo(4.5);
            assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_STRONG_SECONDARY), derived(c, DerivedColor.SURFACE_TABLE_HEADER))).as("headings").isGreaterThanOrEqualTo(4.5);
            assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_ONACCENT), palette.color(SemanticColor.ACCENT))).as("the selected row").isGreaterThanOrEqualTo(4.5);
            for (var feature : structure())
                assertThat(probe(dark, 0, 0, feature, feature.lower() ? SHIFT : 0, c)).as("dark: " + feature.name()).isEqualTo(light.get(feature.name()));
        });
    }
}
```

`KitGallery.installed` puts the default kit's 13-point `defaultFont`, which every role would take; `render` puts the
kit's 12 points in its place, as the application's font setting is under the look's kit defaults (R8). Two probes are
aimed at what the mocks measure (R10): the shortcut's end ("ends at 429"), stepping left from the close button (its ⇧ and
⌘ come from whichever face covers them, so it waits for Plex), and the heading's rule, stepping up from the selected row
(a `border.row` rule lies under 6 ΔE76 from `surface.tableHeader`, so stepping down through the band never sees it). Each render's checks run inside its own `KitGallery.installed` call, on
the EDT, while its `KitContext` is open:
`ThemeBinding.current()` (reached through `KitContext.theme()`) refuses a closed binding and any thread but the EDT, so a
context must never be kept past the callback. The Light probes are kept as numbers, which Dark's callback compares.
An `AssertionError` raised in a callback reaches the test through `invokeAndWait`'s `InvocationTargetException`, which
`render` unwraps. `ColorMath.hex` and `ColorMath.contrast` are jasper-ui's (`ColorMath.java`); every pair the Run
window declares is also painted over every palette by Task 4's `PaintedContrastTest` case.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.RunWindowRenderTest'`
Expected: FAIL: `compileTestJava` reports "cannot find symbol … class MockComparison" until the helper compiles; with it
in place the run fails where the render is not yet tuned (Step 3), or passes.

- [ ] **Step 3: Make it match, and record the result**

Run the test. The dry run at `b9178db45` passed every checked probe and fill, and printed edge agreement top 0.879,
bottom 0.835 for "Just opened" and top 0.896, bottom 0.842 for "Typing auth"; the not-yet-checked lines were the
checkbox and the primary and Cancel buttons' left edges (the default switch and button faces) and the shortcut's end
and the detail line's and the query's first glyphs (the platform faces). For every miss it reports, open `jasper-app/build/run-window/light/<state>-side.png` and the mock region
with `python3 -I tools/visual/region.py` (the mock's crop is `60,854,924,842`), find the role or layout line that moves
that feature, and change only that value. The values are measured: the "Mock measurements" table gives each role's
measurement, so a miss is a layout bug or a font-metric offset (a text ink start off by its side bearing), never a
reason to widen a tolerance. Change a role in `DesignMetrics` together with its rows in `compact-roles.tsv`,
`classic-roles.tsv` and `RunWindowRolesTest`, and record every changed value in this plan's status banner with the
measurement that justifies it. When geometry and colours pass, the two agreement values print on the test's output;
if either is under 0.70 while every feature passes, inspect the side images for an unmeasured difference (a glyph, a
weight, a rule), fix it the same way, and record the final agreements. The floor never goes below 0.70.

Then append the opt-in pairs (the part above the list's foot and the part under it, for both states) and declare the
mocks as a test input:

```bash
python3 -I - <<'PY'
import json, pathlib
path = pathlib.Path("tools/visual/pairs.json"); pairs = json.loads(path.read_text(encoding="utf-8"))
if any(p["name"].startswith("run-window-") for p in pairs): raise SystemExit("pairs.json already has the Run window pairs")
R = "docs/design/enterprise/reference/06 Run Dialog/"
for state, mock in (("just-opened", "Run Dialog - Just opened (recent + most used).png"), ("typing-auth", "Run Dialog - Typing auth (filtered).png")):
    render = f"jasper-app/build/run-window/light/{state}@2x.png"
    pairs.append({"name": f"run-window-{state}", "mock": R + mock, "crop": [60, 854, 924, 632], "render": render,
                  "render_crop": [0, 0, 924, 632], "min_agreement": 0.70,
                  "colours": ["#D9DFEA", "#ECEDEF", "#2F65CA", "#F0F1F3", "#FFFFFF", "#B4B8BF"]})
    pairs.append({"name": f"run-window-{state}-lower", "mock": R + mock, "crop": [60, 1488, 924, 208], "render": render,
                  "render_crop": [0, 646, 924, 208], "min_agreement": 0.70, "colours": ["#ECEDEF", "#E6E7EA", "#C4C7CC", "#2F65CA"]})
path.write_text(json.dumps(pairs, indent=2) + "\n", encoding="utf-8")
build = pathlib.Path("jasper-app/build.gradle.kts"); text = build.read_text(encoding="utf-8")
old = '    inputs.file(rootProject.file("docs/design/theme-engine/migration.csv")).withPathSensitivity(PathSensitivity.RELATIVE)\n'
if text.count(old) != 1: raise SystemExit("jasper-app/build.gradle.kts: the migration.csv input moved")
build.write_text(text.replace(old, old + '    // Plan 8a: RunWindowRenderTest compares with the 06 Run Dialog mocks.\n'
    '    inputs.dir(rootProject.file("docs/design/enterprise/reference/06 Run Dialog")).withPropertyName("runDialogMocks").withPathSensitivity(PathSensitivity.RELATIVE)\n'), encoding="utf-8")
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
rows = [
    "jasper-app/src/test/java/dev/jasper/app/testsupport/MockComparison.java,app.testsupport,MockComparison,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java,Test/fixture ownership; New in Enterprise plan 8a Task 7; compare_mock.py's edges in Java",
    "jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java,app.palette,RunWindowRenderTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java,Test/fixture ownership; New in Enterprise plan 8a Task 7; the 06 Run Dialog mocks"]
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
```

If plan 4c's `check_pairs.py` is present, run `python3 -I tools/visual/check_pairs.py jasper-ui/build/check run-window` and
record its PASS lines. (Its `pairs.json` keys are 4c's; `compare_all.py` ignores `min_agreement` and `colours`.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS: `RunWindowRenderTest` 3 tests, printing two agreement lines of at least 0.70 each and the parts not yet
checked; every other palette test (a role tuned in Step 3 is also checked by `RunWindowRolesTest` and `RunWindowViewTest`).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/test jasper-app/build.gradle.kts tools/visual/pairs.json docs/design/theme-engine/migration.csv jasper-ui/src
git commit -F - <<'EOF'
Run window: compared with the 06 Run Dialog mocks

A headless JUnit render of both mocked states at 2x under the Enterprise
fixture kit and the Enterprise Light fixture theme: the geometry probes of
the Run window's own parts within a device pixel for edges and three for
text, its fills within 3 dE76, and luminance-edge agreement of at least
0.70 above and under the list. The classic faces' and IBM Plex's parts are
measured and printed until plans 4b and 5b are here. Dark keeps the
geometry and its text readable. The opt-in pairs join pairs.json.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: Documentation, STATUS and the whole build

The smallest accurate edits (spec 17; AGENTS.md's documentation rules): the app architecture's `palette` row names the
two views, the UI architecture names the Run window presentation and surfaces, and `docs/STATUS.md` gains this plan's
entry at the top. `docs/command-palette.md` describes the Run window in 8b, when it is reachable.

**Files:**
- Modify: `docs/app-architecture.md:164`, `docs/ui-architecture.md:232`, `docs/STATUS.md` (a new entry at the top)
- Modify: this plan's status banner (top of this file)

**Interfaces:**
- Consumes: every earlier task.
- Produces: nothing new.

- [ ] **Step 1: The documents**

Run from the worktree root:

```bash
python3 -I - <<'PY'
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/app-architecture.md", [(
"| `palette` | EDT scope/query/step state, keyboard routing, the Swing palette popup and the built-in Commands scope.",
"| `palette` | EDT scope/query/step state, keyboard routing, the palette's two views behind `PaletteView` (the card, `CardView`, and the Run window, `RunWindowView`, built by `PaletteViews.of` from the look's `PaletteLook`) and the built-in Commands scope.", 1)])
edit("docs/ui-architecture.md", [(
"keep it and it is not part of the plugin SDK.",
"keep it and it is not part of the plugin SDK. The host-only `KitView.runWindow(true)` presents a view as part of the\n"
"Run window: its RESULTS rows, heading bands and counts, PROMPT_HEADER tile, transparent field and text take the\n"
"`RUN_WINDOW_*` roles, `RUN_HEADING` and `RUN_DETAIL`; `SurfacePanel`'s `RUN_WINDOW`, `FIELD` and `BOX` kinds paint the\n"
"window's frame and bands, its field box and its list box.", 1)])
PY
```

If an anchor moved because an earlier plan edited the same sentence, re-find it and make the same addition.

- [ ] **Step 2: The whole build**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Then count the results:

```bash
python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
totals = [0, 0, 0, 0]
for path in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    root = ET.parse(path).getroot()
    for i, key in enumerate(("tests", "failures", "errors", "skipped")): totals[i] += int(root.get(key, 0))
print("tests %d, failures %d, errors %d, skipped %d" % tuple(totals))
PY
```

Expected: "failures 0, errors 0", and a test count above the base recorded in "Before you start" by this plan's new
tests (2 + 4 + 1 + 2 + 3 + 3 + 1 + 9 + 5 + 3 = 33: headings, copy, seam, roles, presentation, surfaces, the painted-contrast
case, view, contract, render).

- [ ] **Step 3: STATUS and the banner**

Add this entry at the top of `docs/STATUS.md`, above plan 5a's (in the style of the plan-5a entry), with
`<range>` the branch's commit range, `<counts>` Step 2's line, `<agreements>` the four values Task 7 printed,
`<tuned>` every role value Task 7 Step 3 changed (or "none"), and `<deviations>` every departure from this plan's text
(or "none beyond the plan's Rulings and Execution notes"):

```markdown
**Enterprise presentation, plan 8a: the Run window view — DATE** (`claude/enterprise-run-window`, worktree
`.claude/worktrees/enterprise-8`, `<range>`;
[plan](superpowers/plans/2026-10-16-enterprise-8a-run-window-view.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). The palette has two views behind
`PaletteView` (spec 11.1): today's card, renamed `CardView`, and `RunWindowView`, built by `PaletteViews.of` from the
look's `PaletteLook`; the step form and the prefix-to-chip edit are shared (`StepForm`, `QueryEdits`). Headings are typed
(`PaletteEntry.Header.Kind`: RECENT, USED, SUGGESTED, SECTION, MATCHES) and `RunCopy` words them for the Run window
("Recent", "Most used", "Suggested", one "Matches" with "i of n"). The Run window is kit views laid out by the app:
jasper-ui's `KitView.runWindow(true)` gives a view the Run window's rows (21), heading bands (18 and a `border.row` rule)
and counts, 7 px hue squares that keep their hue on the selection, the 32 px help tile with the filled ▶, a data-face
query with no ring of its own and the mocks' text colours, from thirty `RUN_WINDOW_*` roles, `RUN_HEADING` (11/600) and
`RUN_DETAIL` (data face, 10.5/400), all equal in both catalogs; `SurfacePanel`'s `RUN_WINDOW`, `FIELD` and `BOX` kinds
paint the frame and bands, the field box and the list box. Enter runs the view's primary verb: the card's verb 0, the Run
window's verb 0 or its SPLIT verb while "Open in a split pane" is ticked (`PaletteViewContractTest`). Compared with the 06
Run Dialog mocks at 2× under the Enterprise fixture kit and the Enterprise Light fixture theme (`RunWindowRenderTest`):
the Run window's own geometry probes within a device pixel for edges and three for text, its fills within 3 ΔE76, edge
agreement <agreements>; the classic checkbox's and buttons' (plan 4b) and IBM Plex's (plan 5b) probes are measured and
printed until those plans are here (`RunWindowRenderTest.CHECKED`); tuned in the comparison: <tuned>. Rulings (plan, "Rulings"): (1) two plans, 8b lands the window; (2) the card renamed `CardView`;
(3) the step form and prefix edit shared; (4) the Run window's roles, two type roles and two filled icons; (5) the list is
8 × 21 + 2 × 19 = 206 inside its box, 6 px taller than the mock's cut box, so the window is 427 tall; (6) the title
shortcut and row tags are plain CAPTION text, as drawn; (7) the help tile is a PROMPT_HEADER `play.filled` icon at the
Run mock's 32; (8) the Run window's text colours as declared pairs (`RunWindowPresentation.pairs(Kit)`, TEXT-guarded,
painted over every palette by `PaintedContrastTest`), its list details, tags and count in `text.placeholder` and a
selected row's detail in the card's supporting ink unguarded, the empty line guarded against the dialog surface (a
residual); (9) no placeholder in the Open field, the query in the data face; (10) the copy; (11) the second and third
verbs in each row's context menu; (12) the JUnit comparison with its floors, run on the EDT while each context is open;
(13) the matches count is the heading's second title node (a heading has a title only); (14) the title row's labels hand
their presses to the view, so the title text drags; execution notes R1 to R16 (no 4b, 4c or 5b code: the Run window's
own help tile, 4c's fixture theme, the fixture kit's comparison with owners, the checkbox row from roles, details cut
at their end). Deviations: <deviations>. Default look byte-identical: the
headless gallery's files for Slate Cobalt Dark and Light match the base (Task 4). `./gradlew check --continue`:
**<counts>**. GUI checks for the user: none yet (nothing reaches the Run window until 8b). Not merged, not pushed.
```

Replace DATE with the day the plan executes. Then set this plan's status banner (insert under the title):

```markdown
> **Status:** executed DATE on `claude/enterprise-run-window` (`<range>`); deviations: <deviations>.
```

- [ ] **Step 4: Commit**

```bash
git add docs/app-architecture.md docs/ui-architecture.md docs/STATUS.md docs/superpowers/plans/2026-10-16-enterprise-8a-run-window-view.md
git commit -F - <<'EOF'
Run window: documentation and status for plan 8a

The architecture notes name the palette's two views and the kit's Run
window presentation and surfaces; STATUS records the plan, its rulings,
the mock comparison and the build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Execution notes (2026-10-16)

**Refresh.** Every anchor, quoted text, path, signature and line number this plan relies on was re-checked against the
worktree at `b83a734ab` (code identical to `main` at `b9178db45`: plans 3a to 3e, 4a and 5a; plans 4b, 4c and 5b are not
there). Method: a dry run in a scratch copy of the tree applied every step in order (each edit script with its stated
counts, each created file, each ledger row); after every task `javac` (JBR 25, the Gradle cache's jars, no Gradle)
compiled jasper-sdk, the testkit, jasper-buddy, jasper-ui (main, test fixtures, tests) and jasper-app (main, tests) with
zero errors (jasper-terminal's classes came from the `enterprise-5` worktree's build of the same sources), and each
task's tests ran headless through the JUnit 6.1.3 launcher. A second replay applied every block of this refreshed text to
a fresh copy, task by task, and produced the same files. The whole jasper-ui suite (713 tests discovered) failed only
`GalleryCoverageTest.shippedJarExcludesGalleryAndTestSupport`, which needs Gradle's `jasper.uiJar`, as the unchanged tree
does; the whole jasper-app suite in one JVM (3,138 tests discovered) failed exactly the 78 tests the unchanged tree fails
(staged-plugin, packaging and jar-property tests that need Gradle's task inputs). The headless gallery (`KitGallery`,
1x, Slate Cobalt Dark and Light, 75 fixtures each) is byte-identical to the unchanged tree's after Tasks 3, 4 and 7.
`javadoc` with jasper-app's `-Xdoclint:all` and jasper-ui's `-Xdoclint:all,-missing` reports no error (jasper-app's new
public members add "no comment" warnings like the module's existing 3,275); `jdeps` shows one new package edge,
`palette → presentation`, and no cycle; the source-hygiene scan of the 43 touched Java files is clean; every new ledger
row has eight fields, and the renamed `CardView` row keeps the old row's quoted fields.

Changes made to this plan's text by the refresh: the Workspace paragraph and "Before you start" (no rebase; checks for
3b, 3d, 3e, 4a and 5a only); "Earlier plans' names" (4b, 4c and 5b names not used); the Global Constraints' hygiene and
`PaletteLook.View` sentences; facts 2 and 4 to 14 (line numbers, and what `main` has without 4b and 5b); two Mock
measurements rows; Rulings 7 and 12; File structure; Task 1's expected count; Task 2's ledger row; Task 3's Files,
Interfaces, the `enterpriseLight()` step, `RunWindowPresentation` (the help tile and the detail's ellipsis), its test and
the Step 5 script; Task 4's halo assertion and the painted-contrast case's help tile; Task 5's checkbox row; Task 6's
notes; Task 7's render, owners and two probes; Task 8's line numbers, STATUS anchor and wording.

### Pre-flight scan

Pairs of tasks that share a file or an interface:

| Tasks | Shared file or interface | Agreement | Ruling |
|---|---|---|---|
| 1 → 2, 5, 6 | `PaletteView` and `PaletteView.Host`, `CardView`, `PaletteController.view()` and `component()`: 2 reads `CardView.entries()` through `view()`, 5 seals the interface over Task 1's text, 6 rewrites Task 1's `new CardView(…, new PaletteView.Host(…))` line | Agree; each later anchor is the earlier task's output | R19 |
| 1 → 5 | `StepForm`, `QueryEdits`, `CardView.find`: `RunWindowView` uses exactly the methods Task 1 gives them | Agree | none |
| 1 → 6 | `PaletteController.java` (field, constructor, `component()`), `WindowCommandPalette.java` (field, `component()`, `view()`; 6 rewrites its constructor call) | Agree | R19 |
| 1, 2, 5, 6, 7 | `docs/design/theme-engine/migration.csv`, append-only | Distinct paths, eight fields each; `ThemeMigrationCoverageTest` passes at every task's end | R13 |
| 2 → 5, 6, 7 | `PaletteEntry.Header.Kind`, `RunCopy` | Agree | none |
| 3 → 4, 5, 7 | `KitFixtures.enterpriseLight()`, `RunWindowPresentation.pairs`, `.authored`, `.tileEdge`, `KitView.runWindow`, the roles and icons | Agree | R4 |
| 3 ↔ 7 | Task 7 Step 3 may tune a role: `DesignMetrics`, both role tables and `RunWindowRolesTest` change together | No role was tuned in the dry run | none |
| 4 → 5, 7 | `SurfacePanel(…, header, footer)`, `Kind.RUN_WINDOW`, `FIELD`, `BOX`, `focusShown`, `focusWithin` | Agree | none |
| 5 → 6, 7 | `RunWindowView` (`handle`, `viewFor`, the ids, `splitTicked`, `fieldBox`, `listBox`, `listHeight`), `RunWindowFixtures` | Agree | none |
| 5 → 7 | The window's height: Task 7 asserts 427 (854 device px) | Agreed only with the checkbox row from roles and the kit's 12-point body | R7, R8 |
| 6, 7 → 8b | `PaletteLook.DEFAULT`, `.RUN_WINDOW`, the 13-argument `PaletteController`, `RunWindowRenderTest.check`, `.render`, `.fonts` | 8b names `RunWindowRenderTest.fonts`, which this plan no longer has | R16 |
| 3, 4 ↔ 4b (parallel) | `Stacks.java`, `TextFields.java`, `Labels.java`, `ItemLists.java`, `KitFixtures.java`, `PaintedContrastTest` | Adjacent hunks; textually local | R18 |
| 3 ↔ 6a (parallel) | `DesignMetrics.Role`'s last constant, `compact-roles.tsv`, `classic-roles.tsv`, `NewRolesTest.ADDED` | Both append after `DOCK_RULE_HIT` | R14, R18 |
| 3 ↔ 4c (later) | `KitFixtures.enterpriseLight()` | The same text at the same anchor | R4 |
| 7 ↔ 4b, 5b (later) | `RunWindowRenderTest.CHECKED`, the kit and theme `render` uses | Held to the mock as each lands | R9 |
| 8 | `docs/app-architecture.md`, `docs/ui-architecture.md`, `docs/STATUS.md`, this banner | Only Task 8 edits them | none |

Each task against itself:

| Task | Agreement of its own text | Ruling |
|---|---|---|
| 1 | Agree; the script prints 35; the card, palette-scope, shortcut, window-palette, contributions and coverage tests (128) pass | none |
| 2 | The script's docstring named Task 1; `ThemeMigrationCoverageTest` names `PaletteHeadingsTest` (it uses Swing), so its row is written out; 2 + 4 cases pass | R13 |
| 3 | Did not run as written: `ClassicPromptFace.java` and `FieldSurface.paintDefault` are 4b's and `enterpriseLight()` is 4c's; two exact-colour assertions missed by one or two levels; the `NewRolesTest` sentence misstated the test. Fixed; 2 + 3 cases pass | R2, R3, R4, R5, R11, R14 |
| 4 | The halo assertion missed by two levels a channel; fixed; the painted-contrast case also samples the help tile's edge; 3 + 1 cases pass | R5, R6 |
| 5 | Agree; 9 cases pass; the checkbox row changed for Task 7 | R7 |
| 6 | Agree; 5a's `PaletteLook` has no constants (the script adds them); 5 cases pass; `palette → presentation` adds no cycle | R15 |
| 7 | Did not run as written: `EnterpriseResources` and the Plex files are 5b's; under `KitGallery.installed` every role took 13 points (the window was 438 tall); the parts 4b's faces and Plex draw missed; two probes measured the wrong thing; the details' ellipsis side differed. Fixed; 3 cases pass, agreement 0.879 / 0.835 ("Just opened") and 0.896 / 0.842 ("Typing auth") | R7 to R12, R16 |
| 8 | `ui-architecture.md`'s anchor is at `:232`; the top STATUS entry is 5a's; the STATUS text describes the fixture comparison; the count adds 33 tests | R17 |

### Rulings

This plan's text refers to these as R1 to R19.

1. **R1: No rebase, and no code of plans 4b, 4c or 5b.** The branch holds `b9178db45`; plan 4b runs in parallel, 4c and 5b
   later. What — the plan's anchors are `main`'s, and where it named 4b's, 4c's or 5b's code it now uses its own (R2 to
   R4, R8). Why — the orchestrator forbids rebasing and runs 8a now. Cost if wrong: merge work only (R18).
2. **R2: The Run window draws its own help tile.** Under `runWindow`, `Stacks` lays a PROMPT_HEADER stack out with
   `RunWindowPresentation.helpLayout` (the tile beside its text at `RUN_WINDOW_TILE_GAP`, the shorter centred on the
   other, as 4b's `BesideLayout` does), `helpChildren` inks the glyph in the accent's ink and stops the text's centring,
   and `Stacks.Tile.run` paints the accent inside an `accent.edge` hairline, the INDICATOR pair `tileEdge(Kit)` on the
   dialog surface. Why — 4b's classic prompt face is not on `main`, and the Run window's tile is the Run window's
   (Rulings 7). Cost if wrong: at the merge with 4b, the `runWindow` branches stay ahead of the face (`headerLayout`,
   `paintTile`); a duplicate of 4b's layout, about 40 lines.
3. **R3: The quiet query surface returns first in today's `FieldSurface.paintComponent`.** Why — 4b's `paintDefault` does not
   exist on `main`. Cost if wrong: at the merge the line moves to `paintDefault`'s first statement.
4. **R4: `KitFixtures.enterpriseLight()` is plan 4c Task 4's method, verbatim, at 4c's anchor.** Why — the tests need spec
   7.6's Enterprise Light and 4c has not run; identical text at the same place merges as one copy. Cost if wrong: one
   duplicate method to delete at the merge with 4c.
5. **R5: Two pixel assertions compare within two levels a channel.** The help glyph's raster covers its interior at 254 of
   255, and a translucent halo stored in an unpremultiplied image rounds its channels (its alpha is held exactly). The
   help-row test also holds the tile's accent fill and its edge exactly. Why — the dry run measured 254 and one-level
   offsets; the colours are right. Cost if wrong: a two-level colour error would pass.
6. **R6: `PaintedContrastTest` also paints the help tile's edge over every palette** (spec 16.1.2): a third view, its tile's
   top edge sampled against `SurfaceContrast.painted(theme, tileEdge(kit))` and its guard. Why — R2 adds a painted pair.
   Cost if wrong: one sample.
7. **R7: The checkbox row is `CHECKBOX_BOX` plus two focus rings tall**, the classic checkbox's own size (4b's
   `ClassicToggleFace.preferredSize`), whichever face draws the toggle; the toggle and its label are centred in it.
   Why — the window's geometry follows its roles, as the mock does; with the toggle's preferred height the default
   switch (before 4b) made the window 5 px taller. Cost if wrong: before 4b the switch overhangs the row by 2.5 px each
   side and overlaps its label's start (test renders only; the Run window is unreachable until 8b).
8. **R8: Task 7 renders under `KitFixtures.enterprise()` and `KitFixtures.enterpriseLight()` with the kit's 12-point body**
   (`render` puts a 12-point `defaultFont`, as the application's font setting is under the look's kit defaults, in place
   of the 13 points `KitGallery.installed` puts for the default kit), and Dark under Slate Cobalt Dark. Why — 5b's
   shipped kit, theme and Plex are not on `main`; at 13 points the help text took 40 px and the window 438. Cost if
   wrong: when 5b lands, `render` switches to the shipped kit and theme (Enterprise Dark for Dark) and the numbers are
   re-measured.
9. **R9: Each probe and fill names its owner; `CHECKED` holds RUN_WINDOW now.** FACES (4b: the checkbox's left and top, its
   label's start, which the switch overlaps, the primary and Cancel buttons' left edges, the checkbox's inside and the
   primary's fill) and FONTS (5b: the shortcut's end, the detail line's label and value starts, the query's first glyph)
   are measured and printed, not asserted. Every RUN_WINDOW probe and fill passes at the plan's tolerances, and the
   edge-agreement floor stays 0.70 (measured 0.879 / 0.835 and 0.896 / 0.842). Why — those parts are drawn by code this
   plan does not have. Cost if wrong: add FACES to `CHECKED` when 4b is in this branch and FONTS with 5b (R8); a miss
   then is that plan's or a layout bug, fixed as Step 3 says.
10. **R10: Two probes re-aimed.** "shortcut text" measured the shortcut's start, which depends on the face drawing ⇧ and ⌘,
    while the mock measurement is its end ("ends at 429"): it is now "shortcut text end", stepping left from the close
    button (FONTS: its end moved by four device pixels between a lone run and the whole suite's font registrations).
    "heading band to its rule" stepped down through the band: the render's `border.row` (#E4E5E8) lies within 6 ΔE76 of
    `surface.tableHeader` (#F0F1F3) where the mock's #DDE0E4 does not, so it found the rule (device row 268) in the mock
    and the next row (270) in the render, though both draw the rule at 268: it is now "heading rule above the selected
    row", stepping up from the accent. Why — a probe must measure the same feature in both images. Cost if wrong: none;
    the counts stay 35 and 34.
11. **R11: A list row's detail is cut at its end under `runWindow`** (`Jasper.leadingEllipsis` false for RESULTS subtitles),
    as both mocks cut "./gradlew run --args=…" and the Vendor path; a MONO text elsewhere keeps its leading ellipsis.
    `RunWindowPresentationTest` holds it. Why — the render cut them at their start; "Just opened"'s top agreement rose
    from 0.855 to 0.879. Cost if wrong: one client property.
12. **R12: The B mock draws the split checkbox enabled for a snippet; spec 11.2 wins** ("enabled when the selected row's
    scope has a SPLIT verb", Review Focus 1): the render's box and label are disabled there. Why — the spec is binding.
    Cost if wrong: none for the comparison (the checkbox is FACES, R9).
13. **R13: Ledger rows are written out**: `PaletteHeadingsTest` (Task 2) gets its row in the task's script, as
    `ThemeMigrationCoverageTest` names it; `RunCopy` and `RunCopyTest` need none. Why — the plan made the row
    conditional and the dry run met the condition. Cost if wrong: a disposition's wording.
14. **R14: `NewRolesTest.ADDED` requires one run of the enum in order, not its end.** The plan appends the thirty names as
    written, and `RunWindowRolesTest` checks the block. Why — the plan's sentence misstated the test; 6a appends its own
    names. Cost if wrong: at the merge with 6a, the `DesignMetrics` blocks and the `ADDED` appends take the same order.
15. **R15: `PaletteLook.DEFAULT` and `RUN_WINDOW` are this plan's**; 5a's `Projections` keeps building looks with
    `PaletteLook.View`, so the "names `PaletteLook.View`" rule excludes `dev.jasper.app.presentation`. Why — 5a defined no
    constants. Cost if wrong: 8b's variant-read scan allows that package.
16. **R16: `RunWindowRenderTest` has no `fonts()`**, and `render(dark, …)` draws Dark in Slate Cobalt Dark; `check(…)` and
    `render(…)` keep their signatures. Why — nothing registers Plex before 5b. Cost if wrong: 8b's refresh drops its
    `RunWindowRenderTest.fonts()` call (8b Rulings name it) and reads `CHECKED` for its workspace render.
17. **R17: Task 8's STATUS entry describes the fixture comparison and its owners** and sits above plan 5a's entry; the
    `ui-architecture.md` anchor is at `:232` (it still matches once). Why — STATUS documents what the code does. Cost if
    wrong: an inaccurate STATUS entry.
18. **R18: Shared files with the parallel plans are reconciled at merge, not here.** Plan 4b rewrites the PROMPT_HEADER layout
    choice and the tile's painting in `Stacks` (keep this plan's `runWindow` branches first), moves `FieldSurface`'s
    painting into `paintDefault` (R3), adds `Labels.TextControl.styled` before this plan's `RunWindowPresentation.text`
    call (keep both; the face's styling applies at the end of refresh, after the Run window's), edits `ItemLists`'s
    toggle row height and adds `KitFixtures.capture` and face pairs in `PaintedContrastTest`; plan 6a appends roles after
    `DOCK_RULE_HIT` and rows to the role tables and names to `ADDED` (R14). Keep this plan's anchors at `b9178db45`.
    Cost if wrong: merge work only.
19. **R19: Order is binding.** Every shared file above is edited in task order, and each later anchor is the earlier task's
    output, as the dry run shows; one commit per task. Other plans' Gradle builds run at the same time, so a
    timing-sensitive test (the window-palette and contract tests wait on the EDT) may flake once: re-run it alone before
    treating it as a failure, and never run `./gradlew --stop`. Cost if wrong: one rerun.
