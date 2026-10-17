# Enterprise presentation 4a: the classic catalog Implementation Plan

> **Status (2026-10-16): executed on `claude/enterprise-classic-catalog` (`7d8c189c3..485316ed2`).** Deviations from this
> plan's text: Task 5's overlay also sets `Button.default.startBorderColor` and `Button.default.endBorderColor`
> to `accent.edge` (spec 6.5's intent, the default button's border is `accent.edge`: FlatLaf 3.7 reads that border from
> `startBorderColor` first and ignores `Button.default.borderColor` while it is set); Task 5's text carries it.
> Final review: Rulings 17 added (spec 5.4's provisional token ranges stand; spec amended).
> Residuals handed on: a focused, hovered or pressed classic native button paints flat, because FlatLaf paints the
> gradient only while the state colour is `Button.startBackground` itself and the inherited `Button.focusedBackground`,
> `hoverBackground` and `pressedBackground` are colours separate from it (without the first FlatLaf falls back to its own
> focus background), for 4b and 4c; an unfocused classic table or tree takes the
> terminal-selection wildcard for `selectionInactiveBackground` and `selectionInactiveForeground` (spec 6.5 lists
> neither), for 4c's mock comparison.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** jasper-ui gains the values of the classic look, with no painted pixel changed: plan 2's hand-offs, the new
metric roles (`ROW_HEIGHT` as the row floor, `FIELD_RADIUS` for fields, and the classic-only roles), `MetricTokens` with
`DesignMetrics.with(MetricTokens)`, `DesignMetrics.CLASSIC` (frozen as `classic-roles.tsv`), `TypeScale.CLASSIC`
(frozen as `classic-type.tsv`), the classic FlatLaf overlay `UiKeySet.CLASSIC` (frozen as `ui-color-keys-classic.tsv`),
`ControlVariants`, `ColorSlots`, `Kit.CLASSIC`, and the Enterprise and complement fixture kits.

**Architecture:** Spec 19.1 row 4 is delivered as three plans (Rulings 1): this one (4a) adds values only; 4b
(`2026-10-16-enterprise-4b-classic-form-faces.md`) adds `KitFaces`, the isolation and variant-read tests and the form
faces; 4c (`2026-10-16-enterprise-4c-classic-data-faces.md`) adds the data faces, the full kit matrix and the mock
comparison. Every value here is read by nothing that paints under `Kit.DEFAULT` except through roles whose default value
is today's (`ROW_HEIGHT` = `BUTTON_HEIGHT`, `FIELD_RADIUS` = `CONTROL_RADIUS`), so the headless gallery stays
byte-identical. `MetricTokens` moves every private role of a token by the token's change from the base catalog's value,
and recomputes the two vertical paddings from spec 5.4's formula, so a kit's file tunes a base without breaking the
base's own relations.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library for the mechanical edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 5.2 to 5.7, 6.1, 6.2, 6.5, 7.1 to 7.3, 16.1 and 19.1 row 4). The reference mocks are
[`docs/design/enterprise/reference/`](../../design/enterprise/reference/) (2× PNGs; the mock wins on layout,
DESIGN_LANGUAGE.md on intent). Plans 1 and 2 are merged on `main`; plan 2's `docs/STATUS.md` entry lists this plan's entry
conditions, which Task 1 does first. Plans 3a, 3b and 3d are merged on `main` (`149cd8fb9`); plans 3c (tables) and 3e
(default look) run in parallel in their own worktrees and are not on this branch, and 4a uses none of their APIs. Plans
4b, 4c and 5 follow it in that order (4b and 4c on this branch).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-4`, branch
`claude/enterprise-classic-catalog`. The branch already contains `main` at `149cd8fb9` (3a, 3b and 3d merged) and adds
the three plan files; do not rebase or merge. Before Task 1, check the base:

```bash
git merge-base --is-ancestor 149cd8fb9 HEAD && echo base-ok                                       # prints base-ok
grep -c 'PROMPT_TILE_RADIUS' jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java   # 3a: at least 1
grep -c 'record Separator(' jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java             # 3b: 1
grep -c 'SESSION' jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java                 # 3d: at least 1
```

Each `grep -c` must print at least 1; if one prints 0, stop. Run every command from the worktree root, never in
`~/projects/moray`. Every quoted anchor below was re-read at `149cd8fb9` on 2026-10-16 (Execution notes). Plans 3c and 3e
edit some of the same files in other worktrees (3c: `InteractiveRows.java`); the edit scripts replace text, not line
numbers, and stop with a message when their text is not found the stated number of times. If a script stops, re-find the
quoted text (it moves, it does not change) and re-run; never edit around a missing anchor. Line numbers in **Files** are
from `149cd8fb9`.

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md). `:jasper-ui:gallery` is headless (`java.awt.headless=true`) and may be run.
- Work only in the worktree above on `claude/enterprise-classic-catalog`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: zero pixel change. Every PNG and JSON file the headless gallery writes for Slate Cobalt Dark and Light at scale 1 is byte-identical to the base captured in Task 1 Step 1; no reference pair under `tools/visual/` changes. Task 7 proves it.
- The default look's frozen tables keep their values: `compact-roles.tsv` only gains the new roles' rows (appended, in enum order); `default-type.tsv` and `ui-color-keys.tsv` do not change.
- jasper-ui's package graph (spec 6.1): `dev.jasper.ui.kit` depends on `dev.jasper.ui.metrics` and `dev.jasper.ui.theme` only; `metrics` on nothing Jasper; `theme` never sees `kit` (`verifyUiArchitecture`).
- No interface without two real implementations (AGENTS.md); this plan adds none.
- Colours come from the theme only: no colour literal in production code or resources (`ColourLiteralScanTest`); the derived roles of spec 7.1 are read through `DerivedColor` and `ColorRole.color(theme)`.
- Jasper is an MVP: no deprecated alias, no migration code, no second name for a renamed member.
- Javadoc doclint fails the build on errors: jasper-ui runs `-Xdoclint:all,-missing`, jasper-app `-Xdoclint:all`; every `{@link}` must resolve and no comment may contain a raw `<`, `>`, `&` or `->`.
- Every new or renamed candidate source under jasper-app, the SDK, the testkit or plugins gets its `docs/design/theme-engine/migration.csv` row in the same commit (`ThemeMigrationCoverageTest`); a row has exactly eight comma-separated fields, so its text uses semicolons. This plan creates new files under `jasper-ui/src` only and edits existing jasper-app files, so it adds no row.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file.
- The identifier word "hero" stays reserved for `Form.hero` (3a's `PromptVocabularyTest`); no new identifier uses it.

## Review Focus

1. A theme whose secondary text on the accent is translucent (Slate Cobalt's `text.onAccent.secondary` is `#FFFFFFB3`):
   a guard declared on it measures what shows, the colour laid over the accent, not its RGB alone; while that holds the
   slot keeps its alpha. Task 1: `GuardSurfacesTest.aTranslucentSlotIsMeasuredAsItShowsOverItsSurface` and
   `aTranslucentSlotThatHoldsKeepsItsAlpha`.
2. A kit file that sets only `control_height = 26`: every control-height role moves by 4 from the base, buttons stay
   two taller than fields under the classic base, and the paddings follow the formula, so no control clips its text.
   Task 3: `MetricTokensTest.aTokenMovesEveryRoleItSetsByItsChangeFromTheBase` and `thePaddingsFollowTheControlHeight`.
3. A kit file at a token's extreme (`row_height = 18`, `control_radius = 0`): the catalog derives without a negative
   value, and a token outside its range fails with the message plan 5's parser shows
   (`[metrics].row_height: 40 is outside 18-28`). Task 3: `MetricTokensTest.aTokenOutsideItsRangeIsRefusedWithItsKey`
   and `anExtremeTokenNeverMakesANegativeRole`.
4. A user who sets a large interface font under the classic catalog: its 22 px rows still grow with the line, because
   `ROW_HEIGHT` takes the button padding rule. Task 2: `NewRolesTest.rowHeightGrowsWithTheLineLikeTheFloorItReplaces`.
5. A gallery fixture that paints a single antialiased pixel that happens to equal another theme's colour: the
   literal check (now by area) does not report it, while a 2 × 2 block of a foreign colour is still found. Task 1:
   `KitMatrixTest.aSingleAntialiasedPixelOfAnotherThemesColourIsNotALiteral` and `aLiteralOfAnotherThemesColourIsFound`.

## Rulings

Decisions where the spec is silent, taken on the user's behalf (2026-10-16). Each is "what — why — cost if wrong";
record them in `docs/STATUS.md` (Task 7) and in the banner when executed.

1. **Row 4 is three plans: 4a values, 4b form faces, 4c data faces, matrix and comparison** — the row holds 14 face
   pairs, four frozen tables, a FlatLaf overlay, two fixture kits and the matrix; one plan would exceed what one
   reviewer can gate per task, as row 3 did (3a to 3e) — cost if wrong: two extra STATUS entries and branch hand-offs.
2. **Guards measure a translucent slot laid over its `against` role** (a translucent `against` first over
   `surface.app`); a slot that holds keeps its alpha, a repair is opaque — plan 2 left it open and the eye sees the
   composite; no default declared pair has a translucent slot or surface in the theme set or the converter fixtures, so
   the default look is unchanged — cost if wrong: a later translucent pair repaired more or less than RGB would.
3. **The literal check counts a colour only where it fills a 2 × 2 block** — antialiased edges blend two roles into
   colours that can coincide with another theme's; a literal paints an area — cost if wrong: a one-pixel literal (a
   1 px rule drawn in a hard-coded colour) goes unseen; the marked mapping of 4c still checks every area.
4. **`custom()` tints authored overrides like its roles; `marked()` drops them** — `custom()` must paint no other
   theme's colour, and `marked()` must make every derived role a function of the markers; both gain a `(base)`
   overload so a theme with overrides (Latte) can be checked — cost if wrong: none for the shipped Slate Cobalt bases,
   which author no overrides.
5. **The structural guard engages on Latte's window border** (`border.window` on `surface.app`, 1.17:1, spec 7.3) —
   it is the shipped pair below 1.3:1, and pinning it keeps the guard live — cost if wrong: none; a palette change
   that lifts it fails the test's precondition loudly.
6. **Every new role but `ROW_HEIGHT` and `FIELD_RADIUS` has the same value in `DEFAULT` and `CLASSIC`** (spec 5.5: "the
   classic values for roles that only classic faces read") and is appended at the end of `DesignMetrics.Role`, so
   `compact-roles.tsv` only gains rows — cost if wrong: none; ordinals are not persisted.
7. **The new classic-only roles, measured from the mocks at 2× and halved** (Verified facts, Mock measurements):
   `CHECKBOX_BOX` 13, `CHECKBOX_RADIUS` 2, `CHECKBOX_LABEL_GAP` 7, `RADIO_RING` 14, `RADIO_DOT` 6, `RADIO_LABEL_GAP` 6,
   `RADIO_GAP` 18, `OPTION_SWATCH` 7, `GROUP_TITLE_INSET` 9, `GROUP_TITLE_PADDING` 4, `GROUP_TITLE_GAP` 4,
   `SPINNER_WIDTH` 82, `SPINNER_ARROW_WIDTH` 16, `SPINNER_GLYPH` 7, `DROPDOWN_GLYPH` 6, `DROPDOWN_GLYPH_INSET` 7,
   `TABLE_HEADER_HEIGHT` 20, `TREE_INDENT` 24, `DISCLOSURE_GLYPH` 6, `DISCLOSURE_GAP` 10, `FOLDER_TAB_HEIGHT` 26,
   `FOLDER_TAB_PADDING` 14, `FOLDER_TAB_RADIUS` 2, `ROUTE_EDGE` 3, `SIDE_COLUMN_WIDTH` 90, `SIDE_COLUMN_GAP` 6,
   `SIDE_BUTTON_GAP` 4, `STRIPE_LABEL_PADDING` 8, `STRIPE_LABEL_GAP` 1, `DOCK_RULE` 1, `DOCK_RULE_HIT` 6,
   `RUN_WINDOW_WIDTH` 460 (spec) — they are the mock's geometry, and spec 5.5 asks plan 4 to calibrate
   `GROUP_TITLE_INSET` and the stripe label metrics — cost if wrong: a few pixels of residual in 4c's comparison, fixed
   by editing one value and its two table rows.
8. **Buttons are two taller than fields under the classic base** (`BUTTON_HEIGHT`, `FULL_FOOTER_BUTTON_HEIGHT`,
   `PANEL_EMPTY_ACTION_HEIGHT` and `JOINED_ACTION_HEIGHT` 24, `FIELD_HEIGHT` 22) — every mock measures 24 for a
   button (Choose…, Cancel, Add Key…, OK) and 22 for a field or combo; `control_height` stays 22 as the shipped kit
   says, and the token moves both by the same delta — cost if wrong: buttons 2 px off DESIGN_LANGUAGE's "22px".
9. **A token sets its roles by delta from the base; the two paddings by formula** — spec 5.4 names the roles a token
   sets and the padding formula `max(2, (control_height − line) / 2)` per side with a host-calibrated line of 16;
   moving the other roles by the token's change keeps a base's relations (fields 22, buttons 24, ordered rows 20 at
   `row_height` 22) and makes `DEFAULT.with(MetricTokens.of(DEFAULT))` equal `DEFAULT` — cost if wrong: a third-party
   look that expected absolute values sees relative ones; plan 5's conformance test freezes the table either way.
10. **The classic catalog's own token values are Enterprise's** (22, 22, 3, 2, 3, 2, 24, 12, 20, 230, 7), and
    `Kit.CLASSIC`'s colour slots are Enterprise's (`surface.app`, `surface.card`) — so the Enterprise fixture kit is
    `Kit.CLASSIC` under another id and plan 5's `EnterpriseFixtureTest` compares one value — cost if wrong: a third-party
    `base = "classic"` kit inherits Enterprise's tuning, which it can override key by key.
11. **The calibrated type roles** (spec 5.6 leaves unlisted roles at 12/13 of their default until plan 4 calibrates
    them): `PANEL_DETAIL_TITLE` 12/600, `PANEL_DETAIL_LABEL`, `PANEL_ROW_SECONDARY`, `PANEL_DETAIL_MONO`,
    `PANEL_EMPTY_DESCRIPTION`, `SETTINGS_PREVIEW` and `ORDERED_ACTION` 12/400, `PANEL_ROW_CHIP` 10/500, `PICKER_COMPACT`
    and `PICKER_CAPTION` 11/400 — measured in the Workstation and Credential Vault mocks (cap heights over IBM Plex's
    0.698 em) — cost if wrong: small type residuals in 4c's comparison.
12. **`DESCRIPTION` stays 11 as spec 5.6 lists it**, although the New File Server mock draws its descriptions at 12
    (the cap height of "Clients" equals that of "Allow") — spec decisions outrank mocks (spec 1, authority order) and
    DESIGN_LANGUAGE §3 says 11; recorded as a measured residual for 4c — cost if wrong: one table row and its tsv line.
13. **The classic scale tracks no label** (`TypeScale.labelTracking()` false): spec 5.6 says "group titles, no
    tracking"; the default keeps its 0.06 `SECTION_LABEL` tracking — cost if wrong: none.
14. **`ControlVariants` is a record of 14 enums in spec 5.2's order**, each with its kit-file `key()`; `of(boolean...)`
    and `flags()` serve the covering array, `complement()` the complement kit; the parsing helpers plan 5 needs arrive
    with plan 5 — cost if wrong: plan 5 adds a lookup method.
15. **The classic overlay also sets `Table.selectionForeground` and `Tree.selectionForeground` to `text.onSelection`** —
    spec 6.5 sets the two backgrounds to `selection.bg`; without the foregrounds the `*.selectionForeground` wildcard
    (`text.primary`) would draw dark text on the selection — cost if wrong: two extra frozen rows.
16. **Plan 2's last entry condition, moving the three package-test repairs onto the default faces, is carried to the
    chrome-face plans (6 and 7)** — the three are chrome painters with no face in plan 4: `PanelHeaders`' connected dot
    (`PanelHostHeaderTest`) belongs to the frame and header strips, the status bar's configuration text
    (`ConfigurationStatusTest`) to the window and bottom strip, the notification counts (`NotificationCardTest`) to
    Event Log; plan 4's faces are spec 19.1 row 4's 14 control pairs, so only the two control repairs
    (`Choices`' selected segment label and `PreviewChoices`' selected caption) move, in 4b, and the `ChromePainter`
    pairs stay in `PaintedContrastTest.DEFAULT_PAIRS` beside them; the three package tests keep checking their repairs
    meanwhile, and 4a's STATUS entry hands the condition on — cost if wrong: plans 6 and 7 each move one or two
    declarations they would have found already moved.
17. **Spec 5.4's nine provisional token ranges stand as calibrated** (added by the final review) — every mock draws one
    value of each token, Enterprise's (Rulings 10), and each lies inside its range, so the mocks give nothing to narrow
    a range by; the default catalog's own tokens lie inside too (five at the top: `control_radius` 4, `field_radius` 4,
    `group_radius` 6, `focus_ring` 3, `tool_window_header` 32), and `MetricTokensTest.aCatalogsOwnTokensLeaveItAsItIs`
    pins both bases in range; spec 5.4's sentence records it — cost if wrong: plan 5 freezes a range that a later plan
    widens or narrows by editing one enum constant, its test row and the spec's table.

## Verified facts

Read at `222969926` with 3a's renames, and re-read at `149cd8fb9` (3a, 3b and 3d merged) on 2026-10-16; the line numbers
are `149cd8fb9`'s.

1. `DesignMetrics` is a final class over `int[] values` with `DEFAULT`, `with(Role, int)` and a private constructor; its
   enum ends `HISTORY_TITLE_GAP(5), HISTORY_CONTENT_GAP(2);` (`DesignMetrics.java:125`); `contentPadding` maps
   `PANEL_EMPTY_ACTION_HEIGHT, BUTTON_HEIGHT, PROMPT_BUTTON_HEIGHT, FULL_FOOTER_BUTTON_HEIGHT, ORDERED_ADD_HEIGHT,` and
   `PANEL_ACTION, INLINE_ACTION -> Role.BUTTON_VERTICAL_PADDING;` (`:159-160`); `contentHeight` is
   `max(value(height), line + value(padding))` (`:165-168`), so a padding role is both sides' total.
2. `compact-roles.tsv` is `ROLE\tvalue` per line in enum order, ending with a newline (560 lines); `DesignMetricsTest
   .everyRoleHasItsCompactValue` reads it (`DesignMetricsTest.java:32-44`).
3. `TypeScale` is a final class over `float[] sizes, int[] weights, float base, float body` with `DEFAULT` and
   `withBody` (`TypeScale.java:11-37`); `Typography.Role` holds `declaredSize` and `declaredWeight` package-private
   (`Typography.java:28-31`); `Typography.font` applies `TextAttribute.TRACKING, platformDefault && role ==
   Role.SECTION_LABEL ? .060f : .018f` in the Helvetica Neue medium branch (`Typography.java:84`). `default-type.tsv`
   is `ROLE\tsize\tweight` (38 lines); `TypeScaleTest.defaultTable()` reads it (`TypeScaleTest.java:19-28`).
4. `Kit` is the record `(String id, DesignMetrics metrics, TypeScale type, FaceSet faces, UiKeySet uiKeys)` with
   `DEFAULT` (`Kit.java:19-28`); its constructor is called (`grep -rn 'Kit(' | grep -E 'new (dev\.jasper\.ui\.kit\.)?Kit\('`)
   at `Kit.java:21`, `KitTest.java:22-35`, `KitContextTest.java:63`, `KitFixtures.java:27`, `ThemeManagerTest.java:153`,
   and fully qualified as `new dev.jasper.ui.kit.Kit(` at `HostedKitTest.java:58` and `WorkspaceMetricsTest.java:78`
   (both jasper-app tests). 3b and 3d added no caller, and the 3c and 3e plans add none.
5. `UiKeySet` is `{ DEFAULT }` (`UiKeySet.java:7-10`); `UiColorKeys.flatLaf(theme, set)` switches on it and fills
   `values(sources, palette, selection)` from a sealed `Source` of `Role`, `TextSelection`, `ErrorTint`
   (`UiColorKeys.java:20-46`); `ui-color-keys.tsv` is `key\tSOURCE` after one `#` comment line (`UiColorKeysTest.java:16-27`).
6. `FlatLafInstaller.install` puts `fixed.put("TextComponent.arc", control);` from `CONTROL_RADIUS` (`:60-62`).
7. The list, table and tree row floor is `ThemePainting.height(context(), DesignMetrics.Role.BUTTON_HEIGHT,
   Typography.Role.LABEL)` in `InteractiveRows.RowsControl.refresh` (`InteractiveRows.java:368-376`).
8. `TextFields.FieldControl.refresh` sets `surface.radiusRole = surface.transparent ? DesignMetrics.Role.TRANSPARENT_FIELD_RADIUS :
   DesignMetrics.Role.CONTROL_RADIUS;` (`TextFields.java:300`), and `FieldSurface` declares
   `DesignMetrics.Role heightRole=DesignMetrics.Role.FIELD_HEIGHT,radiusRole=DesignMetrics.Role.CONTROL_RADIUS;` (`:391`);
   secret fields and the breadcrumb path field paint through the same surface with its default radius role, and the
   picker's search surface sets `PICKER_SEARCH_RADIUS` itself (`PickerMenus.java:86`).
9. `SurfaceContrast.painted(theme, pair)` is `pair.guard().apply(pair.slot().color(theme), pair.against().color(theme),
   text.primary)` (`SurfaceContrast.java:92-95`); `Guard.apply` returns the colour when `holds` (`:71-77`). Its
   production callers are `Notices.java:76,118`, `Buttons.java:208` (a notice's link) and `PropertyLists.java:60`.
10. The shipped themes' and converter fixtures' only translucent roles are `surface.scrim`, `border.subtle`,
    `text.onAccent.secondary` and `text.onSelection.secondary` (read from the JSON files); none is a slot or surface of
    a declared default pair (`PaintedContrastTest.java:46-88`: 20 pairs since 3b, the Notice text, border and link
    pairs on the derived grounds and the Properties values on `surface.card` among them; derived roles are opaque).
11. `KitFixtures.custom()` keeps `base.derivedOverrides()` through `withSemantic` and `marked()` likewise
    (`KitFixtures.java:30-40`, `:210-216`, `withSemantic` at `:110-113`); Catppuccin Latte authors overrides (plan 2
    STATUS); Slate Cobalt authors none, so `custom()` and `marked()` are unchanged for their existing callers.
12. `KitMatrixTest.underCustomNoFixturePaintsAColourOnlyAnotherThemeHas` retains every opaque pixel colour
    (`KitMatrixTest.java:37-46`, `colours` at `:70-78`).
13. `StaticDefaultTest.CATALOGS` names `DesignMetrics.DEFAULT`, `TypeScale.DEFAULT` and `FaceSet.PLATFORM`, readable only
    from `dev.jasper.ui.metrics` and `dev.jasper.ui.kit` (`StaticDefaultTest.java:47-48`, `owner` at `:59`).
14. The gallery (`:jasper-ui:gallery`, `-Pjasper.galleryTheme=dark|light`) wrote 142 files per theme before 3b and
    159 at `149cd8fb9` (3b's eight fixtures add 17; 3d adds none); 3c's fixture arrives only when 3c merges. This plan
    compares against its own base.

### Mock measurements

Measured with Pillow (`python3 -I`) at the mocks' 2× pixels, halved to logical pixels. Colours are the Enterprise Light
targets of spec 7.6 and DESIGN_LANGUAGE §2; they become theme roles, never literals.

| Measured | Mock and place | Logical value | Role or rule |
|---|---|---|---|
| Field and combo outer height | New File Server – FTP, Name field and Reachable from combo | 22 | `FIELD_HEIGHT` |
| Button outer height | Same, Choose…, Cancel; Add Host, Add Key… | 24 | `BUTTON_HEIGHT` (Rulings 8) |
| Group box edge to the window content edge | Same, Server box (box edge at x = 34 and 1008 at 2×; the window edge, 1 logical, at 0-1 and 1042-1043); Add Host the same | 16 | classic `FORM_SIDES` |
| Group box border, radius | Same | 1 px `#C4C7CC`, 3 | `border.window`, `CARD_RADIUS` |
| Border segment before the title | Same, Server | 9 | `GROUP_TITLE_INSET` |
| Gap around the title text | Same | 4 | `GROUP_TITLE_PADDING` |
| Border line to first row | Same, line at 48, Name field top at 65 | 16 = 12 + 4 | `CARD_PADDING` + `GROUP_TITLE_GAP` |
| Last row to box bottom | Same, Passive ports | 12 | `CARD_PADDING` |
| Box to next box (component gap) | Server bottom 215, Access title top 228 | 12 | classic `SECTION_GAP` |
| Field column | New File Server, Add Host, New Tunnel | x = 144, 113 from the labels | classic `FORM_LABEL_WIDTH` 100 + `COLUMN_GAP` 12 |
| Checkbox | Allow changes; Track shell folder | 13 square, radius 2; edge `#9AA0A8`; checked fill `#2F65CA`, edge `#2655AE` | `CHECKBOX_BOX`, `CHECKBOX_RADIUS` |
| Checkbox to its label | Allow changes | 7 | `CHECKBOX_LABEL_GAP` |
| Radio | Protocol FTP | ring 14, 1 px; dot 6; selected ring `#2655AE`, dot `#2F65CA`, inside `#FFFFFF` | `RADIO_RING`, `RADIO_DOT` |
| Radio to label, option to option | Access: Anonymous, Vault Account | 6, 18 | `RADIO_LABEL_GAP`, `RADIO_GAP` |
| Protocol swatch | HTTP | 7 square | `OPTION_SWATCH` |
| Combo ▼ | Reachable from | 5 wide, 5 tall, `#5C6370`, centre 10 from the right edge | `DROPDOWN_GLYPH` 6, `DROPDOWN_GLYPH_INSET` 7 |
| Button fill | Choose…, Cancel | `#FEFEFE` top to `#EEF0F2` bottom, edge `#B4B8BF` | `button.secondary.bg` to `button.end` |
| Disabled buttons | Add Key's Remove; Add Server | flat `#F0F1F3` / `#E1E3E6`, edge `#D3D6DA` / `#CDD0D5`, text `#A3A8B0` | `surface.tableHeader` / `surface.header`, `border.divider` |
| Read-only field | Folder | fill `#F4F5F7`, edge `#CDD0D5` | `surface.tableHeader`, `border.divider` |
| Table header band | Settings – Notifications; Workstation SFTP | 20 tall, `#F0F1F3`, separators and bottom rule `#D3D6DA`, titles 11 px | `TABLE_HEADER_HEIGHT` |
| Table rows | Same | 22, zebra `#F7F8FA` | `ROW_HEIGHT` |
| Tree | Settings – Keymap | ▶/▼ 6 × 5 `#5C6370`; glyph to text 10; child text 24 + 2 right of the glyph | `DISCLOSURE_GLYPH`, `DISCLOSURE_GAP`, `TREE_INDENT` |
| Spinner | Settings – Window, Toolbar height | 82 × 22; arrow column 16 with the button fill; ▲▼ 7 × 4 | `SPINNER_WIDTH`, `SPINNER_ARROW_WIDTH`, `SPINNER_GLYPH` |
| Folder tabs | Settings – Plugins, Overview / Details | 26 tall, 14 side padding, top radius 2; active `#FFFFFF`, inactive `#E4E6E9`, edge `#B4B8BF` | `FOLDER_TAB_*` |
| Route strip | New Tunnel – Local | 44 tall, edge `#B4B8BF`, hue left edge 3, caption 11 px uppercase `#5C6370`, arrow `#7A808A` | `ROUTE_EDGE` |
| Ordered side column | Add Host Keys; Project Index | buttons 90-92 wide, 24 tall, 4 apart, 6 from the list | `SIDE_COLUMN_WIDTH`, `SIDE_BUTTON_GAP`, `SIDE_COLUMN_GAP` |
| Ordered rows | Project Index markers | 20 | classic `ORDERED_ROW_HEIGHT` |
| Checkbox setting rows | New File Server, Security & Startup | 26 a row, 42 with a description; the box at the row's side padding (x = 30) | classic `SETTING_HEIGHT`, `SETTING_DESCRIPTION_HEIGHT`, `FULL_SETTING_DESCRIPTION_HEIGHT` |
| Theme previews | Settings – Appearance | 150 × 78 with a 2 px frame and a 2 px gap; 20 apart; the radio 8 below | classic `THEME_PREVIEW_WIDTH`, `_HEIGHT`, `_GAP`, `_CAPTION_GAP` |
| Tag | Workstation, File Servers "HTTP" | 14 tall, edge `#B9C8EA`, tint `#E8EEFA`, text `#2655AE` | `CHIP_HEIGHT` 14, `tag.border.blue` |
| Prompt tile | Unlock Vault | 30 square, fill `#2F65CA`, edge `#2655AE`, white glyph | `PROMPT_TILE` 30, `ICON_PROMPT` 16 |
| Stripe labels | Workstation, "1: SFTP", "2: File Servers" | stripe 22; label tile padding 8; 1 between tiles | `STRIPE_LABEL_PADDING`, `STRIPE_LABEL_GAP` |
| Type | NFS "Server" title, labels, values; route caption | titles 12/600; labels and values 12; captions 11 | `TypeScale.CLASSIC` |

## File structure

- `jasper-ui/src/main/java/dev/jasper/ui/theme/SurfaceContrast.java`: translucent slots in `painted` (Task 1).
- `jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java`: `custom(base)`, `marked(base)` (Task 1);
  `enterprise()`, `complement()`, `withControls` (Task 6).
- `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java`: new roles (Task 2), `with(MetricTokens)` and
  `CLASSIC` (Task 3). New `MetricTokens.java` (Task 3).
- `jasper-ui/src/main/java/dev/jasper/ui/metrics/TypeScale.java`, `Typography.java`: `CLASSIC`, `labelTracking` (Task 4).
- `jasper-ui/src/main/java/dev/jasper/ui/theme/UiKeySet.java`, `UiColorKeys.java`: `CLASSIC` and derived sources (Task 5).
- `jasper-ui/src/main/java/dev/jasper/ui/kit/`: new `ControlVariants.java`, `ColorSlots.java`; `Kit.java` (Task 6).
- Rows and fields: `InteractiveRows.java`, `TextFields.java`, `FlatLafInstaller.java` (Task 2).
- Frozen tables under `jasper-ui/src/test/resources/dev/jasper/ui/`: `metrics/compact-roles.tsv` (rows added),
  `metrics/classic-roles.tsv`, `metrics/classic-type.tsv`, `theme/ui-color-keys-classic.tsv` (new).
- New tests: `theme/GuardSurfacesTest`, `metrics/NewRolesTest`, `metrics/MetricTokensTest`, `metrics/ClassicCatalogTest`,
  `metrics/ClassicTypeTest`, `theme/ClassicUiKeysTest`, `kit/ControlVariantsTest`, `testsupport/FixtureKitsTest`;
  edits to `KitMatrixTest`, `KitFixturesTest`, `KitTest`, `KitContextTest`, `ThemeManagerTest`, `StaticDefaultTest`.

---

### Task 1: Plan 2's hand-offs

Guards measure translucent slots as they show (Rulings 2), the literal check counts areas (Rulings 3), the colour
fixtures tint or drop authored overrides (Rulings 4), and a test pins the structural guard engaging (Rulings 5). The
fifth hand-off, the three package-test repairs, is carried to plans 6 and 7 (Rulings 16). Step 1 captures the gallery
base every later task compares against.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SurfaceContrast.java:88-95`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java:30-40,210-216`
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/gallery/KitMatrixTest.java:41,48-55,70`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/theme/GuardSurfacesTest.java`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java` (one test appended)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `SurfaceContrast.painted(GeneratedTheme, PaintPair)` with translucent measurement and the package-private
  `static Color SurfaceContrast.flat(Color color, Color under)`; `public static GeneratedTheme KitFixtures.custom(GeneratedTheme base)`
  and `marked(GeneratedTheme base)`; `static Set<Integer> KitMatrixTest.areas(BufferedImage)`.

- [ ] **Step 1: Capture the gallery base**

Run from the worktree root:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
rm -rf jasper-ui/build/plan4-base && mkdir -p jasper-ui/build/plan4-base
cp -R jasper-ui/build/gallery/dark-1x jasper-ui/build/gallery/light-1x jasper-ui/build/plan4-base/
ls jasper-ui/build/plan4-base/dark-1x | wc -l
```

Expected: both runs print "Rendered N fixtures with FlatLaf … at 1.0x"; the last line prints the dark file count (the
fixture count × 2 plus the contact sheets and `manifest.json`). Keep this directory until the end of plan 4c.

- [ ] **Step 2: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/theme/GuardSurfacesTest.java`:

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

/** Enterprise presentation plan 4 (plan 2's hand-offs): what a guard measures, and the structural guard engaging. */
class GuardSurfacesTest {
    /** Slate Cobalt Light with {@code role} replaced by {@code colour}. */
    private static GeneratedTheme with(SemanticColor role, Color colour) {
        var base = KitFixtures.light();
        var values = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var each : SemanticColor.values()) values.put(each, base.semantic().color(each));
        values.put(role, colour);
        return KitFixtures.withSemantic(base, new SemanticPalette(values));
    }

    /** Rulings 2: a near-transparent dark text reads as a pale grey on white, and that is what the guard holds to 4.5:1. */
    @Test void aTranslucentSlotIsMeasuredAsItShowsOverItsSurface() {
        var theme = with(SemanticColor.TEXT_SECONDARY, new Color(0x1E, 0x1F, 0x22, 0x30));
        var pair = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
        Color card = theme.semantic().color(SemanticColor.SURFACE_CARD);
        assertThat(ColorMath.contrast(theme.semantic().color(SemanticColor.TEXT_SECONDARY), card))
            .as("its RGB alone would pass").isGreaterThan(SurfaceContrast.TEXT);
        Color painted = SurfaceContrast.painted(theme, pair);
        assertThat(painted.getAlpha()).as("a repair is opaque").isEqualTo(255);
        assertThat(ColorMath.contrast(painted, card)).isGreaterThanOrEqualTo(SurfaceContrast.TEXT);
        assertThat(painted).isNotEqualTo(theme.semantic().color(SemanticColor.TEXT_SECONDARY));
    }

    @Test void aTranslucentSlotThatHoldsKeepsItsAlpha() {
        var slot = new Color(0x1E, 0x1F, 0x22, 0xF0);
        var theme = with(SemanticColor.TEXT_SECONDARY, slot);
        var pair = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
        assertThat(SurfaceContrast.painted(theme, pair)).isEqualTo(slot);
        assertThat(SurfaceContrast.painted(theme, pair).getAlpha()).isEqualTo(0xF0);
    }

    /** Rulings 2: for opaque slots on opaque surfaces the guard paints exactly what it painted before, over every palette. */
    @Test void anOpaquePairIsPaintedAsBefore() {
        var palettes = new java.util.ArrayList<>(KitFixtures.themes());
        palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) for (var slot : SemanticColor.values()) for (var against : new SemanticColor[] {
                SemanticColor.SURFACE_CARD, SemanticColor.SURFACE_APP, SemanticColor.SURFACE_INPUT, SemanticColor.ACCENT}) {
            Color s = slot.color(theme), a = against.color(theme);
            if (s.getAlpha() != 255 || a.getAlpha() != 255) continue;
            for (var guard : new SurfaceContrast.Guard[] {SurfaceContrast.Guard.TEXT, SurfaceContrast.Guard.STRUCTURAL}) {
                var before = guard.apply(s, a, theme.semantic().color(SemanticColor.TEXT_PRIMARY));
                assertThat(SurfaceContrast.painted(theme, new PaintPair(slot, against, guard)))
                    .as("%s %s on %s", theme.name(), slot.key(), against.key()).isEqualTo(before);
            }
        }
    }

    /** Rulings 5 (spec 7.3): Latte's window border on its app surface is 1.17:1, below the structural floor. */
    @Test void theStructuralGuardEngagesOnLattesWindowBorder() {
        var latte = KitFixtures.themes().stream().filter(t -> t.name().equals("Catppuccin Latte")).findFirst().orElseThrow();
        var pair = new PaintPair(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_APP, SurfaceContrast.Guard.STRUCTURAL);
        Color raw = latte.semantic().color(SemanticColor.BORDER_WINDOW), app = latte.semantic().color(SemanticColor.SURFACE_APP);
        assertThat(ColorMath.contrast(raw, app)).as("precondition").isLessThan(SurfaceContrast.STRUCTURAL);
        Color painted = SurfaceContrast.painted(latte, pair);
        assertThat(painted).isNotEqualTo(raw);
        assertThat(ColorMath.contrast(painted, app)).isGreaterThanOrEqualTo(SurfaceContrast.STRUCTURAL);
        assertThat(latte.semantic().color(SemanticColor.BORDER_WINDOW)).as("the role is unchanged").isEqualTo(raw);
    }
}
```

Append this test before the final `}` of `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java`:

```java
    /** Plan 4 (Rulings 4): custom() tints every authored override as it tints the roles; marked() drops them. */
    @Test void customTintsAndMarkedDropsTheAuthoredOverrides() {
        var latte = KitFixtures.themes().stream().filter(t -> t.name().equals("Catppuccin Latte")).findFirst().orElseThrow();
        assertThat(latte.derivedOverrides()).as("Latte authors overrides").isNotEmpty();
        var custom = KitFixtures.custom(latte);
        assertThat(custom.derivedOverrides().keySet()).isEqualTo(latte.derivedOverrides().keySet());
        latte.derivedOverrides().forEach((role, colour) ->
            assertThat(custom.derivedOverrides().get(role)).as(role.key()).isNotEqualTo(colour));
        var marked = KitFixtures.marked(latte);
        assertThat(marked.derivedOverrides()).isEmpty();
        for (var role : SemanticColor.values()) assertThat(marked.semantic().color(role)).isEqualTo(KitFixtures.marker(role));
        assertThat(KitFixtures.custom().semantic()).isEqualTo(KitFixtures.custom(KitFixtures.dark()).semantic());
        assertThat(KitFixtures.marked().semantic()).isEqualTo(KitFixtures.marked(KitFixtures.dark()).semantic());
    }
```

Edit `KitMatrixTest`: run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4a Task 1 step 2: the literal check counts painted areas."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/test/java/dev/jasper/ui/gallery/KitMatrixTest.java", [
    ('''        for (var capture : captures(KitFixtures.custom(), Kit.DEFAULT)) {
            var painted = colours(capture.image());''',
     '''        for (var capture : captures(KitFixtures.custom(), Kit.DEFAULT)) {
            var painted = areas(capture.image());''', 1),
    ('''    @Test void aLiteralOfAnotherThemesColourIsFound() {
        var literal = KitFixtures.dark().semantic().color(SemanticColor.ACCENT);
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(2, 2, literal.getRGB());
        var painted = colours(image);
        painted.retainAll(foreign(KitFixtures.custom()));
        assertThat(painted).containsExactly(literal.getRGB());
    }''',
     '''    @Test void aLiteralOfAnotherThemesColourIsFound() {
        var literal = KitFixtures.dark().semantic().color(SemanticColor.ACCENT);
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y <= 2; y++) for (int x = 1; x <= 2; x++) image.setRGB(x, y, literal.getRGB());
        var painted = areas(image);
        painted.retainAll(foreign(KitFixtures.custom()));
        assertThat(painted).containsExactly(literal.getRGB());
    }

    /** Plan 4 (Rulings 3): an antialiased edge pixel that happens to equal another theme's colour is not a literal. */
    @Test void aSingleAntialiasedPixelOfAnotherThemesColourIsNotALiteral() {
        var literal = KitFixtures.dark().semantic().color(SemanticColor.ACCENT);
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(2, 2, literal.getRGB());
        var painted = areas(image);
        painted.retainAll(foreign(KitFixtures.custom()));
        assertThat(painted).isEmpty();
    }''', 1),
    ('''    /** The opaque colours in {@code image}. */''',
     '''    /** The opaque colours that fill at least one 2 × 2 block of {@code image}: painted areas, not antialiased edges. */
    static Set<Integer> areas(BufferedImage image) {
        var colours = new HashSet<Integer>();
        for (int y = 0; y + 1 < image.getHeight(); y++) for (int x = 0; x + 1 < image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            if ((argb >>> 24) == 0xFF && image.getRGB(x + 1, y) == argb && image.getRGB(x, y + 1) == argb
                    && image.getRGB(x + 1, y + 1) == argb) colours.add(argb);
        }
        return colours;
    }

    /** The opaque colours in {@code image}. */''', 1)])
PY
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.GuardSurfacesTest' --tests 'dev.jasper.ui.testsupport.KitFixturesTest' --tests 'dev.jasper.ui.gallery.KitMatrixTest' --continue`
Expected: FAIL. `:jasper-ui:compileTestJava` stops with "cannot find symbol … method custom(GeneratedTheme)" in
`KitFixturesTest` (and `marked(GeneratedTheme)`), so no test runs; were it to compile,
`aTranslucentSlotIsMeasuredAsItShowsOverItsSurface` would fail at "[a repair is opaque] expected: 255 but was: 48" (the
RGB-only guard returns the translucent slot unchanged).

- [ ] **Step 4: Measure translucent slots and add the fixture overloads**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4a Task 1 step 4: translucent slots in SurfaceContrast.painted; custom(base) and marked(base)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/theme/SurfaceContrast.java", [
    ('''    /**
     * {@code pair}'s slot as painted in {@code theme}: its guard applied against the pair's other role, toward
     * {@code text.primary} first. The roles themselves are never changed.
     */
    public static Color painted(GeneratedTheme theme, PaintPair pair) {
        return pair.guard().apply(pair.slot().color(theme), pair.against().color(theme),
            theme.semantic().color(SemanticColor.TEXT_PRIMARY));
    }''',
     '''    /**
     * {@code pair}'s slot as painted in {@code theme}: its guard applied against the pair's other role, toward
     * {@code text.primary} first. A translucent slot is measured as it shows, laid over the role it is painted on (a
     * translucent {@code against} first over {@code surface.app}); while that holds the slot keeps its alpha, and a
     * repair is opaque. The roles themselves are never changed.
     */
    public static Color painted(GeneratedTheme theme, PaintPair pair) {
        Color under = flat(pair.against().color(theme), theme.semantic().color(SemanticColor.SURFACE_APP));
        Color slot = pair.slot().color(theme), shown = flat(slot, under);
        return pair.guard().holds(shown, under) ? slot
            : pair.guard().apply(shown, under, theme.semantic().color(SemanticColor.TEXT_PRIMARY));
    }

    /** {@code color} as it shows over {@code under} (taken as opaque): itself when opaque, else laid over it. */
    static Color flat(Color color, Color under) {
        Color base = under.getAlpha() == 255 ? under : new Color(under.getRGB() | 0xFF000000);
        return color.getAlpha() == 255 ? color : ColorMath.mix(color, base, color.getAlpha() / 255.0);
    }''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java", [
    ('''    /** A third appearance: Slate Cobalt Dark with every role 20% plum (alpha kept), so a hard-coded variant colour shows. */
    public static GeneratedTheme custom() {
        var plum = new java.awt.Color(0x7a3fa0);
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) {
            var base = dark().semantic().color(role);
            var mixed = ColorMath.mix(base, plum, .8);
            values.put(role, new java.awt.Color(mixed.getRed(), mixed.getGreen(), mixed.getBlue(), base.getAlpha()));
        }
        return withSemantic(dark(), new SemanticPalette(values));
    }''',
     '''    /** A third appearance: Slate Cobalt Dark with every role 20% plum (alpha kept), so a hard-coded variant colour shows. */
    public static GeneratedTheme custom() { return custom(dark()); }

    /** {@code base} with every role and every authored derived override 20% plum (alpha kept). */
    public static GeneratedTheme custom(GeneratedTheme base) {
        var plum = new java.awt.Color(0x7a3fa0);
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) {
            var own = base.semantic().color(role);
            var mixed = ColorMath.mix(own, plum, .8);
            values.put(role, new java.awt.Color(mixed.getRed(), mixed.getGreen(), mixed.getBlue(), own.getAlpha()));
        }
        var overrides = new java.util.EnumMap<DerivedColor, java.awt.Color>(DerivedColor.class);
        base.derivedOverrides().forEach((role, colour) -> overrides.put(role, ColorMath.mix(colour, plum, .8)));
        return new GeneratedTheme(base.name(), base.dark(), base.author(), new SemanticPalette(values), base.terminal(), base.editor(),
            base.syntax(), base.glass(), overrides);
    }''', 1),
    ('''    /** The dark fixture with every semantic role in its own colour, so a painted pixel names the role it came from. */
    public static GeneratedTheme marked() {
        var base = dark();
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, marker(role));
        return withSemantic(base, new SemanticPalette(values));
    }''',
     '''    /** The dark fixture with every semantic role in its own colour, so a painted pixel names the role it came from. */
    public static GeneratedTheme marked() { return marked(dark()); }

    /** {@code base} with every semantic role in its marker colour and no authored overrides, so every derived role follows the markers. */
    public static GeneratedTheme marked(GeneratedTheme base) {
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, marker(role));
        return new GeneratedTheme(base.name(), base.dark(), base.author(), new SemanticPalette(values), base.terminal(), base.editor(),
            base.syntax(), base.glass(), java.util.Map.of());
    }''', 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.*' --tests 'dev.jasper.ui.testsupport.KitFixturesTest' --tests 'dev.jasper.ui.gallery.KitMatrixTest' --continue`
Expected: PASS (`GuardSurfacesTest` 4 tests, `KitFixturesTest` 4, `KitMatrixTest` 4, the theme package unchanged).

- [ ] **Step 6: Run the default look's contrast suite**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.PaintedContrastTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS (the 20 declared default pairs, 3b's Notice and Properties pairs among them, are opaque, so nothing
they paint moved).

- [ ] **Step 7: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/theme/SurfaceContrast.java jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java \
  jasper-ui/src/test/java/dev/jasper/ui/gallery/KitMatrixTest.java jasper-ui/src/test/java/dev/jasper/ui/theme/GuardSurfacesTest.java \
  jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java
git commit -F - <<'MSG'
Classic catalog: guards measure what shows; the literal check counts areas

Plan 2's hand-offs: a translucent slot is measured laid over its surface and keeps its
alpha while it holds; KitMatrixTest counts a foreign colour only where it fills a 2x2
block; custom(base) tints authored overrides and marked(base) drops them; the
structural guard is pinned engaging on Latte's window border.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 2: The new metric roles

`DesignMetrics.Role` gains `ROW_HEIGHT` (the list, table and tree row floor, which reused `BUTTON_HEIGHT`),
`FIELD_RADIUS` (fields, which used `CONTROL_RADIUS`) and the classic-only roles of Rulings 7, appended in that order. Their
`DEFAULT` values are today's equivalents, so nothing moves; `compact-roles.tsv` gains their rows.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java:125,159-160`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/InteractiveRows.java:372` (one line of the `:368-376` call; 3c
  edits the same file from `:382` on, so keep this edit to that line), `jasper-ui/src/main/java/dev/jasper/ui/swing/TextFields.java:300,391`, `jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafInstaller.java:62`
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv` (34 rows appended)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java`
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/swing/FlatLafInstallerKitTest.java:21-25` (`other()` sets `FIELD_RADIUS` too; one test added),
  `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java:103` (`TextComponent.arc` reads `FIELD_RADIUS`)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `DesignMetrics.Role` constants, appended after `HISTORY_CONTENT_GAP` in this order: `ROW_HEIGHT(24)`,
  `FIELD_RADIUS(4)`, `RUN_WINDOW_WIDTH(460)`, `CHECKBOX_BOX(13)`, `CHECKBOX_RADIUS(2)`, `CHECKBOX_LABEL_GAP(7)`,
  `RADIO_RING(14)`, `RADIO_DOT(6)`, `RADIO_LABEL_GAP(6)`, `RADIO_GAP(18)`, `OPTION_SWATCH(7)`, `GROUP_TITLE_INSET(9)`,
  `GROUP_TITLE_PADDING(4)`, `GROUP_TITLE_GAP(4)`, `SPINNER_WIDTH(82)`, `SPINNER_ARROW_WIDTH(16)`, `SPINNER_GLYPH(7)`,
  `DROPDOWN_GLYPH(6)`, `DROPDOWN_GLYPH_INSET(7)`, `TABLE_HEADER_HEIGHT(20)`, `TREE_INDENT(24)`, `DISCLOSURE_GLYPH(6)`,
  `DISCLOSURE_GAP(10)`, `FOLDER_TAB_HEIGHT(26)`, `FOLDER_TAB_PADDING(14)`, `FOLDER_TAB_RADIUS(2)`, `ROUTE_EDGE(3)`,
  `SIDE_COLUMN_WIDTH(90)`, `SIDE_COLUMN_GAP(6)`, `SIDE_BUTTON_GAP(4)`, `STRIPE_LABEL_PADDING(8)`, `STRIPE_LABEL_GAP(1)`,
  `DOCK_RULE(1)`, `DOCK_RULE_HIT(6)`; `contentPadding(ROW_HEIGHT) == BUTTON_VERTICAL_PADDING`.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java`:

```java
package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 5.4 and 5.5: the roles plan 4 adds start at today's values, so nothing moves. */
class NewRolesTest {
    static final List<String> ADDED = List.of("ROW_HEIGHT", "FIELD_RADIUS", "RUN_WINDOW_WIDTH", "CHECKBOX_BOX", "CHECKBOX_RADIUS",
        "CHECKBOX_LABEL_GAP", "RADIO_RING", "RADIO_DOT", "RADIO_LABEL_GAP", "RADIO_GAP", "OPTION_SWATCH", "GROUP_TITLE_INSET",
        "GROUP_TITLE_PADDING", "GROUP_TITLE_GAP", "SPINNER_WIDTH", "SPINNER_ARROW_WIDTH", "SPINNER_GLYPH", "DROPDOWN_GLYPH",
        "DROPDOWN_GLYPH_INSET", "TABLE_HEADER_HEIGHT", "TREE_INDENT", "DISCLOSURE_GLYPH", "DISCLOSURE_GAP", "FOLDER_TAB_HEIGHT",
        "FOLDER_TAB_PADDING", "FOLDER_TAB_RADIUS", "ROUTE_EDGE", "SIDE_COLUMN_WIDTH", "SIDE_COLUMN_GAP", "SIDE_BUTTON_GAP",
        "STRIPE_LABEL_PADDING", "STRIPE_LABEL_GAP", "DOCK_RULE", "DOCK_RULE_HIT");

    @Test void theAddedRolesCloseTheEnumInOrder() {
        var roles = java.util.Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        assertThat(roles.subList(roles.size() - ADDED.size(), roles.size())).containsExactlyElementsOf(ADDED);
    }

    @Test void theRowFloorAndTheFieldRadiusStartAtTheRolesTheyReplace() {
        var m = DesignMetrics.DEFAULT;
        assertThat(m.value(DesignMetrics.Role.ROW_HEIGHT)).isEqualTo(m.value(DesignMetrics.Role.BUTTON_HEIGHT));
        assertThat(m.value(DesignMetrics.Role.FIELD_RADIUS)).isEqualTo(m.value(DesignMetrics.Role.CONTROL_RADIUS));
        assertThat(m.contentPadding(DesignMetrics.Role.ROW_HEIGHT)).isEqualTo(m.contentPadding(DesignMetrics.Role.BUTTON_HEIGHT));
    }

    /** Review Focus 4: a large line grows a row exactly as it grew the button-height floor. */
    @Test void rowHeightGrowsWithTheLineLikeTheFloorItReplaces() {
        var frc = new FontRenderContext(null, true, true);
        for (float size : new float[] {12, 13, 18, 24}) {
            var font = new Font("SansSerif", Font.PLAIN, 1).deriveFont(size);
            assertThat(DesignMetrics.DEFAULT.contentHeight(DesignMetrics.Role.ROW_HEIGHT, font, frc)).as("%s pt", size)
                .isEqualTo(DesignMetrics.DEFAULT.contentHeight(DesignMetrics.Role.BUTTON_HEIGHT, font, frc));
        }
    }
}
```

`TextComponent.arc` now follows `FIELD_RADIUS`, so the two tests that read it change with it:
`FlatLafInstallerKitTest.other()` (which sets only `CONTROL_RADIUS` and expects all three arcs at 4) also sets
`FIELD_RADIUS`, a new test pins that the field radius moves `TextComponent.arc` alone, and `ThemeManagerTest` reads the
field radius. Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4a Task 2 step 1: the FlatLaf arc tests follow FIELD_RADIUS for TextComponent.arc."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/test/java/dev/jasper/ui/swing/FlatLafInstallerKitTest.java", [
    ('''    /** The default kit with other geometry: radius 2 (arcs 4), a 2 px focus ring and a 40% dark popup shadow. */
    private static Kit other() {
        return KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.CONTROL_RADIUS, 2).with(DesignMetrics.Role.FOCUS_RING, 2)
            .with(DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK, 40));
    }''',
     '''    /** The default kit with other geometry: control and field radius 2 (arcs 4), a 2 px focus ring and a 40% dark popup shadow. */
    private static Kit other() {
        return KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.CONTROL_RADIUS, 2).with(DesignMetrics.Role.FIELD_RADIUS, 2)
            .with(DesignMetrics.Role.FOCUS_RING, 2).with(DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK, 40));
    }

    /** Enterprise presentation plan 4: a field's arc follows FIELD_RADIUS, and the control arcs keep CONTROL_RADIUS. */
    @Test void theTextComponentArcFollowsTheFieldRadiusAlone() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                FlatLafInstaller.install(KitFixtures.dark(), KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.FIELD_RADIUS, 1)));
                assertThat(UIManager.getInt("TextComponent.arc")).isEqualTo(2);
                for (var key : List.of("Button.arc", "Component.arc")) assertThat(UIManager.getInt(key)).as(key).isEqualTo(8);
            } finally { set(prior); }
        });
    }''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java", [
    ('''            assertThat(UIManager.getInt("TextComponent.arc")).isEqualTo(2 * dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.CONTROL_RADIUS));''',
     '''            assertThat(UIManager.getInt("TextComponent.arc")).isEqualTo(2 * dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_RADIUS));''', 1)])
PY
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.NewRolesTest'`
Expected: FAIL at compile: "cannot find symbol … variable ROW_HEIGHT" (and `FIELD_RADIUS`, in `NewRolesTest` and
`FlatLafInstallerKitTest`).

- [ ] **Step 3: Add the roles, read them for rows and fields, and freeze them**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4a Task 2 step 3: the new roles, the row floor and the field radius."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
ADDED = [("ROW_HEIGHT", 24), ("FIELD_RADIUS", 4), ("RUN_WINDOW_WIDTH", 460), ("CHECKBOX_BOX", 13), ("CHECKBOX_RADIUS", 2),
    ("CHECKBOX_LABEL_GAP", 7), ("RADIO_RING", 14), ("RADIO_DOT", 6), ("RADIO_LABEL_GAP", 6), ("RADIO_GAP", 18), ("OPTION_SWATCH", 7),
    ("GROUP_TITLE_INSET", 9), ("GROUP_TITLE_PADDING", 4), ("GROUP_TITLE_GAP", 4), ("SPINNER_WIDTH", 82), ("SPINNER_ARROW_WIDTH", 16),
    ("SPINNER_GLYPH", 7), ("DROPDOWN_GLYPH", 6), ("DROPDOWN_GLYPH_INSET", 7), ("TABLE_HEADER_HEIGHT", 20), ("TREE_INDENT", 24),
    ("DISCLOSURE_GLYPH", 6), ("DISCLOSURE_GAP", 10), ("FOLDER_TAB_HEIGHT", 26), ("FOLDER_TAB_PADDING", 14), ("FOLDER_TAB_RADIUS", 2),
    ("ROUTE_EDGE", 3), ("SIDE_COLUMN_WIDTH", 90), ("SIDE_COLUMN_GAP", 6), ("SIDE_BUTTON_GAP", 4), ("STRIPE_LABEL_PADDING", 8),
    ("STRIPE_LABEL_GAP", 1), ("DOCK_RULE", 1), ("DOCK_RULE_HIT", 6)]
first = ", ".join(f"{n}({v})" for n, v in ADDED[:2])
second = ", ".join(f"{n}({v})" for n, v in ADDED[2:19])
third = ", ".join(f"{n}({v})" for n, v in ADDED[19:])
edit("jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java", [
    ('''HISTORY_TITLE_GAP(5), HISTORY_CONTENT_GAP(2);''',
     f'''HISTORY_TITLE_GAP(5), HISTORY_CONTENT_GAP(2),
        // Enterprise presentation plan 4: the list, table and tree row floor (BUTTON_HEIGHT before) and the field radius
        // (CONTROL_RADIUS before), then roles only the classic faces and chrome read, measured on the Enterprise mocks.
        {first},
        {second},
        {third};''', 1),
    ('''                 PANEL_ACTION, INLINE_ACTION -> Role.BUTTON_VERTICAL_PADDING;''',
     '''                 PANEL_ACTION, INLINE_ACTION, ROW_HEIGHT -> Role.BUTTON_VERTICAL_PADDING;''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/InteractiveRows.java", [
    ('''                            ThemePainting.height(
                                    context(),
                                    DesignMetrics.Role.BUTTON_HEIGHT,
                                    Typography.Role.LABEL),''',
     '''                            ThemePainting.height(
                                    context(),
                                    DesignMetrics.Role.ROW_HEIGHT,
                                    Typography.Role.LABEL),''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/TextFields.java", [
    ('''            surface.radiusRole = surface.transparent ? DesignMetrics.Role.TRANSPARENT_FIELD_RADIUS : DesignMetrics.Role.CONTROL_RADIUS;''',
     '''            surface.radiusRole = surface.transparent ? DesignMetrics.Role.TRANSPARENT_FIELD_RADIUS : DesignMetrics.Role.FIELD_RADIUS;''', 1),
    ('''DesignMetrics.Role heightRole=DesignMetrics.Role.FIELD_HEIGHT,radiusRole=DesignMetrics.Role.CONTROL_RADIUS;''',
     '''DesignMetrics.Role heightRole=DesignMetrics.Role.FIELD_HEIGHT,radiusRole=DesignMetrics.Role.FIELD_RADIUS;''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafInstaller.java", [
    ('''        fixed.put("TextComponent.arc", control);''',
     '''        fixed.put("TextComponent.arc", Integer.toString(metrics.value(DesignMetrics.Role.FIELD_RADIUS) * 2));''', 1)])
table = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv")
text = table.read_text(encoding="utf-8")
if not text.endswith("HISTORY_CONTENT_GAP\t2\n"): raise SystemExit("compact-roles.tsv: expected HISTORY_CONTENT_GAP last")
table.write_text(text + "".join(f"{n}\t{v}\n" for n, v in ADDED), encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.*' --tests 'dev.jasper.ui.swing.FlatLafInstallerKitTest' --tests 'dev.jasper.ui.swing.SlateCobaltInstallTest' --tests 'dev.jasper.ui.swing.TextFieldsTest' --tests 'dev.jasper.ui.swing.InteractiveRowsTest' --tests 'dev.jasper.ui.swing.TablesTest' --tests 'dev.jasper.ui.swing.TreesTest' --continue && ./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeManagerTest'`
Expected: PASS (`NewRolesTest` 3 tests; `DesignMetricsTest.everyRoleHasItsCompactValue` reads the 34 new rows;
`FlatLafInstallerKitTest` 5 tests, `theTextComponentArcFollowsTheFieldRadiusAlone` among them).

- [ ] **Step 5: Prove the gallery unchanged**

Run from the worktree root:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
python3 -I - <<'PY'
"""The headless gallery is byte-identical to the base captured in Task 1 Step 1."""
import hashlib, pathlib, sys
def digests(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for theme in ("dark-1x", "light-1x"):
    base, now = digests(pathlib.Path("jasper-ui/build/plan4-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none")
sys.exit(1 if bad else 0)
PY
```

Expected: `changed: none`.

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java jasper-ui/src/main/java/dev/jasper/ui/swing/InteractiveRows.java \
  jasper-ui/src/main/java/dev/jasper/ui/swing/TextFields.java jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafInstaller.java \
  jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java \
  jasper-ui/src/test/java/dev/jasper/ui/swing/FlatLafInstallerKitTest.java jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java
git commit -F - <<'MSG'
Classic catalog: the row floor, the field radius and the classic roles

ROW_HEIGHT replaces BUTTON_HEIGHT as the list, table and tree row floor and
FIELD_RADIUS replaces CONTROL_RADIUS for fields (and FlatLaf's TextComponent.arc),
each at today's value; 32 roles only classic faces and chrome read are added at
their measured values. compact-roles.tsv gains the rows; the gallery is unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 3: `MetricTokens` and `DesignMetrics.CLASSIC`

`dev.jasper.ui.metrics.MetricTokens` holds spec 5.4's 11 public tokens with their kit-file keys, ranges and the roles
each sets; `DesignMetrics.with(MetricTokens)` moves each token's roles by the token's change from the catalog's own value
and recomputes `FIELD_VERTICAL_PADDING` and `BUTTON_VERTICAL_PADDING` from spec 5.4's formula (Rulings 9).
`DesignMetrics.CLASSIC` is `DEFAULT` with the classic delta, frozen as `classic-roles.tsv` (generated here from
`compact-roles.tsv` and the delta, then read back by the test).

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/metrics/MetricTokens.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (after `with(Role, int)`; the `DEFAULT` field)
- Create: `jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-roles.tsv` (generated in Step 3)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/metrics/MetricTokensTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/metrics/ClassicCatalogTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java:46-48` (the classic catalog is owner-only too)

**Interfaces:**
- Consumes: Task 2's roles.
- Produces: `public final class MetricTokens` with `public enum Token { CONTROL_HEIGHT, ROW_HEIGHT, CONTROL_RADIUS,
  FIELD_RADIUS, GROUP_RADIUS, FOCUS_RING, TOOL_WINDOW_HEADER, GROUP_PADDING, DEPENDENT_INDENT, NAVIGATION_WIDTH,
  STATUS_DOT; String key(); int min(); int max(); List<DesignMetrics.Role> roles(); }`,
  `public static final int LINE_HEIGHT = 16`, `public static MetricTokens of(DesignMetrics catalog)`,
  `public int value(Token)`, `public MetricTokens with(Token, int)` (throws `IllegalArgumentException("[metrics].<key>:
  <value> is outside <min>-<max>")`); `public DesignMetrics DesignMetrics.with(MetricTokens)`;
  `public static final DesignMetrics DesignMetrics.CLASSIC`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/metrics/MetricTokensTest.java`:

```java
package dev.jasper.ui.metrics;

import static dev.jasper.ui.metrics.DesignMetrics.Role.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.MetricTokens.Token;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 5.4: the public tokens, their ranges and the private roles each sets. */
class MetricTokensTest {
    @Test void theTokensAreSpecFiveFoursElevenWithTheirKeysAndRanges() {
        assertThat(List.of(Token.values())).extracting(t -> t.key() + " " + t.min() + "-" + t.max()).containsExactly(
            "control_height 20-28", "row_height 18-28", "control_radius 0-4", "field_radius 0-4", "group_radius 0-6",
            "focus_ring 1-3", "tool_window_header 22-32", "group_padding 8-16", "dependent_indent 12-28",
            "navigation_width 180-300", "status_dot 5-9");
        assertThat(Token.CONTROL_HEIGHT.roles()).containsExactly(FIELD_HEIGHT, BUTTON_HEIGHT, PICKER_DEFAULT_HEIGHT, FULL_FOOTER_BUTTON_HEIGHT,
            JOINED_ACTION_HEIGHT, PANEL_EMPTY_ACTION_HEIGHT, PANEL_SEARCH_HEIGHT, COLUMN_SEARCH_HEIGHT, KEYMAP_SEARCH_HEIGHT, FULL_SETTING_CHOICE_HEIGHT);
        assertThat(Token.ROW_HEIGHT.roles()).containsExactly(ROW_HEIGHT, MENU_ITEM_HEIGHT, PICKER_ROW_HEIGHT, RESULTS_ROW_HEIGHT,
            RESULTS_HEADING_HEIGHT, KEYCAP_TREE_ROW_HEIGHT, KEYCAP_TREE_GROUP_HEIGHT, ORDERED_ROW_HEIGHT, SETTINGS_NAV_ROW,
            SETTINGS_NAV_HEADING, COLUMN_RAIL_ROW, PANEL_COMPACT_ROW_HEIGHT);
        assertThat(Token.CONTROL_RADIUS.roles()).containsExactly(CONTROL_RADIUS, SEGMENT_TRACK_RADIUS, SEGMENT_ITEM_RADIUS,
            PICKER_SEARCH_RADIUS, KEYCAP_RADIUS, CHIP_RADIUS, ORDERED_RADIUS);
        assertThat(Token.FIELD_RADIUS.roles()).containsExactly(FIELD_RADIUS, TRANSPARENT_FIELD_RADIUS);
        assertThat(Token.GROUP_RADIUS.roles()).containsExactly(CARD_RADIUS);
        assertThat(Token.FOCUS_RING.roles()).containsExactly(FOCUS_RING);
        assertThat(Token.TOOL_WINDOW_HEADER.roles()).containsExactly(NATIVE_TOOL_HEADER_HEIGHT, PANEL_HEADER);
        assertThat(Token.GROUP_PADDING.roles()).containsExactly(CARD_PADDING, FULL_SETTING_SIDE_PADDING, SETTINGS_ROW_SIDE);
        assertThat(Token.DEPENDENT_INDENT.roles()).containsExactly(SETTING_DEPENDENT_INDENT);
        assertThat(Token.NAVIGATION_WIDTH.roles()).containsExactly(SETTINGS_SIDEBAR_WIDTH);
        assertThat(Token.STATUS_DOT.roles()).containsExactly(STATUS_DOT, PANEL_ROW_DOT, TOOL_HEADER_ACTIVE_DOT);
    }

    /** A catalog's own tokens are its first role of each, and applying them changes nothing. */
    @Test void aCatalogsOwnTokensLeaveItAsItIs() {
        for (var catalog : List.of(DesignMetrics.DEFAULT, DesignMetrics.CLASSIC)) {
            var tokens = MetricTokens.of(catalog);
            for (var token : Token.values()) assertThat(tokens.value(token)).as(token.key()).isEqualTo(catalog.value(token.roles().getFirst()));
            assertThat(catalog.with(tokens)).isEqualTo(catalog);
        }
        assertThat(List.of(Token.values()).stream().map(t -> MetricTokens.of(DesignMetrics.CLASSIC).value(t)).toList())
            .as("Rulings 10: the classic base is tuned as Enterprise").containsExactly(22, 22, 3, 2, 3, 2, 24, 12, 20, 230, 7);
    }

    /** Review Focus 2: a token moves every role it sets by its change from the base, keeping the base's relations. */
    @Test void aTokenMovesEveryRoleItSetsByItsChangeFromTheBase() {
        var base = DesignMetrics.CLASSIC;
        var tall = base.with(MetricTokens.of(base).with(Token.CONTROL_HEIGHT, 26));
        for (var role : Token.CONTROL_HEIGHT.roles()) assertThat(tall.value(role)).as(role.name()).isEqualTo(base.value(role) + 4);
        assertThat(tall.value(BUTTON_HEIGHT) - tall.value(FIELD_HEIGHT)).as("buttons stay two taller").isEqualTo(2);
        for (var role : DesignMetrics.Role.values())
            if (!Token.CONTROL_HEIGHT.roles().contains(role) && role != FIELD_VERTICAL_PADDING && role != BUTTON_VERTICAL_PADDING)
                assertThat(tall.value(role)).as(role.name()).isEqualTo(base.value(role));
    }

    /** Spec 5.4: each padding is max(2, (control_height − 16) / 2) per side, so both sides' total. */
    @Test void thePaddingsFollowTheControlHeight() {
        for (int height = 20; height <= 28; height++) {
            var metrics = DesignMetrics.DEFAULT.with(MetricTokens.of(DesignMetrics.DEFAULT).with(Token.CONTROL_HEIGHT, height));
            int expected = 2 * Math.max(2, (height - MetricTokens.LINE_HEIGHT) / 2);
            assertThat(metrics.value(FIELD_VERTICAL_PADDING)).as("%d", height).isEqualTo(expected);
            assertThat(metrics.value(BUTTON_VERTICAL_PADDING)).as("%d", height).isEqualTo(expected);
        }
        assertThat(DesignMetrics.CLASSIC.value(FIELD_VERTICAL_PADDING)).isEqualTo(6);
        assertThat(DesignMetrics.DEFAULT.value(FIELD_VERTICAL_PADDING)).isEqualTo(8);
    }

    /** Review Focus 3: the message plan 5's parser shows, for each token at both ends. */
    @Test void aTokenOutsideItsRangeIsRefusedWithItsKey() {
        var tokens = MetricTokens.of(DesignMetrics.CLASSIC);
        assertThatIllegalArgumentException().isThrownBy(() -> tokens.with(Token.ROW_HEIGHT, 40))
            .withMessage("[metrics].row_height: 40 is outside 18-28");
        for (var token : Token.values()) {
            assertThatIllegalArgumentException().isThrownBy(() -> tokens.with(token, token.min() - 1));
            assertThatIllegalArgumentException().isThrownBy(() -> tokens.with(token, token.max() + 1));
            assertThat(tokens.with(token, token.min()).value(token)).isEqualTo(token.min());
        }
    }

    @Test void anExtremeTokenNeverMakesANegativeRole() {
        for (var base : List.of(DesignMetrics.DEFAULT, DesignMetrics.CLASSIC)) {
            var low = MetricTokens.of(base);
            for (var token : Token.values()) low = low.with(token, token.min());
            var derived = base.with(low);
            for (var role : DesignMetrics.Role.values()) assertThat(derived.value(role)).as(role.name()).isNotNegative();
        }
    }
}
```

Create `jasper-ui/src/test/java/dev/jasper/ui/metrics/ClassicCatalogTest.java`:

```java
package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 5.5: the classic catalog, frozen whole; a retune edits classic-roles.tsv in the same change. */
class ClassicCatalogTest {
    static LinkedHashMap<String, Integer> table(String name) throws Exception {
        var table = new LinkedHashMap<String, Integer>();
        try (var in = ClassicCatalogTest.class.getResourceAsStream(name)) {
            for (var line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                var cells = line.split("\t");
                table.put(cells[0], Integer.parseInt(cells[1]));
            }
        }
        return table;
    }

    @Test void everyRoleHasItsClassicValue() throws Exception {
        var table = table("classic-roles.tsv");
        assertThat(Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList()).containsExactlyElementsOf(table.keySet());
        for (var role : DesignMetrics.Role.values())
            assertThat(DesignMetrics.CLASSIC.value(role)).as(role.name()).isEqualTo(table.get(role.name()));
    }

    /** Spec 5.5's named classic values, and the roles only classic faces read share their default value (Rulings 6). */
    @Test void theSpecsNamedValuesHold() {
        var c = DesignMetrics.CLASSIC;
        assertThat(c.value(DesignMetrics.Role.RUN_WINDOW_WIDTH)).isEqualTo(460);
        assertThat(c.value(DesignMetrics.Role.PALETTE_WINDOW_MARGIN)).isEqualTo(8);
        for (var role : new DesignMetrics.Role[] {DesignMetrics.Role.WORKSPACE_GUTTER, DesignMetrics.Role.WINDOW_RADIUS,
                DesignMetrics.Role.WORKSPACE_SURFACE_ARC, DesignMetrics.Role.PALETTE_CORNER_ARC, DesignMetrics.Role.DOCK_DROP_ARC,
                DesignMetrics.Role.RAIL_BUTTON_ARC, DesignMetrics.Role.NATIVE_TOOLBAR_HOVER_ARC, DesignMetrics.Role.TITLE_SEARCH_ARC,
                DesignMetrics.Role.PROMPT_TILE_RADIUS})
            assertThat(c.value(role)).as(role.name()).isZero();
        assertThat(c.value(DesignMetrics.Role.PROMPT_TILE)).isEqualTo(30);
        for (var name : NewRolesTest.ADDED) {
            var role = DesignMetrics.Role.valueOf(name);
            if (role != DesignMetrics.Role.ROW_HEIGHT && role != DesignMetrics.Role.FIELD_RADIUS)
                assertThat(c.value(role)).as(name).isEqualTo(DesignMetrics.DEFAULT.value(role));
        }
        assertThat(c).isNotEqualTo(DesignMetrics.DEFAULT);
    }
}
```

Run from the worktree root, to make the static-default scan treat the classic catalog like the default one:

```bash
python3 -I - <<'PY'
"""Plan 4a Task 3 step 1: DesignMetrics.CLASSIC is owner-only, as DEFAULT is."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java", [
    ('''    /** Default catalogs that only their owners may read. */''',
     '''    /** The catalogs that only their owners may read: the defaults and the classic base. */''', 1),
    ('''    static final Set<String> CATALOGS = Set.of(METRICS + ".DEFAULT", "dev/jasper/ui/metrics/TypeScale.DEFAULT",
        "dev/jasper/ui/metrics/FaceSet.PLATFORM");''',
     '''    static final Set<String> CATALOGS = Set.of(METRICS + ".DEFAULT", METRICS + ".CLASSIC", "dev/jasper/ui/metrics/TypeScale.DEFAULT",
        "dev/jasper/ui/metrics/FaceSet.PLATFORM");''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.MetricTokensTest' --tests 'dev.jasper.ui.metrics.ClassicCatalogTest'`
Expected: FAIL at compile: "cannot find symbol … class MetricTokens" and "variable CLASSIC".

- [ ] **Step 3: Add `MetricTokens`, `with(MetricTokens)`, `CLASSIC` and its frozen table**

Create `jasper-ui/src/main/java/dev/jasper/ui/metrics/MetricTokens.java`:

```java
package dev.jasper.ui.metrics;

import static dev.jasper.ui.metrics.DesignMetrics.Role.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The 11 public metric tokens a kit file tunes (Enterprise presentation spec 5.4): each a value in its range that sets the
 * private catalog roles it names. {@link DesignMetrics#with(MetricTokens)} applies them to a base catalog. Immutable.
 */
public final class MetricTokens {
    /** The host-calibrated line height of a base face's 12 px line, which the vertical paddings are measured against. */
    public static final int LINE_HEIGHT = 16;

    /** A public token: its kit-file key, its range and the catalog roles it sets, the first being the one it reads. */
    public enum Token {
        CONTROL_HEIGHT("control_height", 20, 28, FIELD_HEIGHT, BUTTON_HEIGHT, PICKER_DEFAULT_HEIGHT, FULL_FOOTER_BUTTON_HEIGHT,
            JOINED_ACTION_HEIGHT, PANEL_EMPTY_ACTION_HEIGHT, PANEL_SEARCH_HEIGHT, COLUMN_SEARCH_HEIGHT, KEYMAP_SEARCH_HEIGHT,
            FULL_SETTING_CHOICE_HEIGHT),
        ROW_HEIGHT("row_height", 18, 28, DesignMetrics.Role.ROW_HEIGHT, MENU_ITEM_HEIGHT, PICKER_ROW_HEIGHT, RESULTS_ROW_HEIGHT,
            RESULTS_HEADING_HEIGHT, KEYCAP_TREE_ROW_HEIGHT, KEYCAP_TREE_GROUP_HEIGHT, ORDERED_ROW_HEIGHT, SETTINGS_NAV_ROW,
            SETTINGS_NAV_HEADING, COLUMN_RAIL_ROW, PANEL_COMPACT_ROW_HEIGHT),
        CONTROL_RADIUS("control_radius", 0, 4, DesignMetrics.Role.CONTROL_RADIUS, SEGMENT_TRACK_RADIUS, SEGMENT_ITEM_RADIUS,
            PICKER_SEARCH_RADIUS, KEYCAP_RADIUS, CHIP_RADIUS, ORDERED_RADIUS),
        FIELD_RADIUS("field_radius", 0, 4, DesignMetrics.Role.FIELD_RADIUS, TRANSPARENT_FIELD_RADIUS),
        GROUP_RADIUS("group_radius", 0, 6, CARD_RADIUS),
        FOCUS_RING("focus_ring", 1, 3, DesignMetrics.Role.FOCUS_RING),
        TOOL_WINDOW_HEADER("tool_window_header", 22, 32, NATIVE_TOOL_HEADER_HEIGHT, PANEL_HEADER),
        GROUP_PADDING("group_padding", 8, 16, CARD_PADDING, FULL_SETTING_SIDE_PADDING, SETTINGS_ROW_SIDE),
        DEPENDENT_INDENT("dependent_indent", 12, 28, SETTING_DEPENDENT_INDENT),
        NAVIGATION_WIDTH("navigation_width", 180, 300, SETTINGS_SIDEBAR_WIDTH),
        STATUS_DOT("status_dot", 5, 9, DesignMetrics.Role.STATUS_DOT, PANEL_ROW_DOT, TOOL_HEADER_ACTIVE_DOT);

        private final String key;
        private final int min, max;
        private final List<DesignMetrics.Role> roles;

        Token(String key, int min, int max, DesignMetrics.Role... roles) {
            this.key = key; this.min = min; this.max = max; this.roles = List.of(roles);
        }

        /** The token's key in a kit file's {@code [metrics]} table. */
        public String key() { return key; }
        /** The least value the token takes. */
        public int min() { return min; }
        /** The greatest value the token takes. */
        public int max() { return max; }
        /** The catalog roles the token sets, the first being the one {@link MetricTokens#of} reads. */
        public List<DesignMetrics.Role> roles() { return roles; }
    }

    private final int[] values;

    private MetricTokens(int[] values) { this.values = values; }

    /** {@code catalog}'s own tokens: each token's first role, as the catalog holds it. */
    public static MetricTokens of(DesignMetrics catalog) {
        Objects.requireNonNull(catalog, "catalog");
        var values = new int[Token.values().length];
        for (var token : Token.values()) values[token.ordinal()] = catalog.value(token.roles().getFirst());
        return new MetricTokens(values);
    }

    /** {@code token}'s value. */
    public int value(Token token) { return values[Objects.requireNonNull(token, "token").ordinal()]; }

    /** A copy with {@code token} at {@code value}, which must lie in the token's range. */
    public MetricTokens with(Token token, int value) {
        Objects.requireNonNull(token, "token");
        if (value < token.min() || value > token.max())
            throw new IllegalArgumentException("[metrics]." + token.key() + ": " + value + " is outside " + token.min() + "-" + token.max());
        int[] copy = values.clone();
        copy[token.ordinal()] = value;
        return new MetricTokens(copy);
    }

    /** Spec 5.4's vertical padding for {@code controlHeight}: {@code max(2, (height − line) / 2)} per side, both sides' total. */
    static int padding(int controlHeight) { return 2 * Math.max(2, (controlHeight - LINE_HEIGHT) / 2); }

    @Override public boolean equals(Object other) { return other instanceof MetricTokens tokens && Arrays.equals(values, tokens.values); }
    @Override public int hashCode() { return Arrays.hashCode(values); }
}
```

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4a Task 3 step 3: DesignMetrics.with(MetricTokens), DesignMetrics.CLASSIC and classic-roles.tsv."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
# The classic delta (spec 5.4 and 5.5, Rulings 7 to 10); every other role keeps its DEFAULT value.
CLASSIC = [("FIELD_HEIGHT", 22), ("BUTTON_HEIGHT", 24), ("PICKER_DEFAULT_HEIGHT", 22), ("FULL_FOOTER_BUTTON_HEIGHT", 24),
    ("JOINED_ACTION_HEIGHT", 24), ("PANEL_EMPTY_ACTION_HEIGHT", 24), ("PANEL_SEARCH_HEIGHT", 22), ("COLUMN_SEARCH_HEIGHT", 22),
    ("KEYMAP_SEARCH_HEIGHT", 22), ("FULL_SETTING_CHOICE_HEIGHT", 22), ("FIELD_VERTICAL_PADDING", 6), ("BUTTON_VERTICAL_PADDING", 6),
    ("ROW_HEIGHT", 22), ("MENU_ITEM_HEIGHT", 22), ("PICKER_ROW_HEIGHT", 22), ("RESULTS_ROW_HEIGHT", 22), ("RESULTS_HEADING_HEIGHT", 22),
    ("KEYCAP_TREE_ROW_HEIGHT", 22), ("KEYCAP_TREE_GROUP_HEIGHT", 22), ("ORDERED_ROW_HEIGHT", 20), ("SETTINGS_NAV_ROW", 22),
    ("SETTINGS_NAV_HEADING", 22), ("COLUMN_RAIL_ROW", 22), ("PANEL_COMPACT_ROW_HEIGHT", 22),
    ("CONTROL_RADIUS", 3), ("SEGMENT_TRACK_RADIUS", 3), ("SEGMENT_ITEM_RADIUS", 2), ("PICKER_SEARCH_RADIUS", 3), ("KEYCAP_RADIUS", 2),
    ("CHIP_RADIUS", 1), ("ORDERED_RADIUS", 0), ("FIELD_RADIUS", 2), ("TRANSPARENT_FIELD_RADIUS", 2), ("CARD_RADIUS", 3),
    ("FOCUS_RING", 2), ("NATIVE_TOOL_HEADER_HEIGHT", 24), ("PANEL_HEADER", 24), ("CARD_PADDING", 12), ("FULL_SETTING_SIDE_PADDING", 12),
    ("SETTINGS_ROW_SIDE", 12), ("SETTING_DEPENDENT_INDENT", 20), ("SETTINGS_SIDEBAR_WIDTH", 230), ("STATUS_DOT", 7), ("PANEL_ROW_DOT", 7),
    ("TOOL_HEADER_ACTIVE_DOT", 7), ("PALETTE_WINDOW_MARGIN", 8), ("WORKSPACE_GUTTER", 0), ("WINDOW_RADIUS", 0),
    ("WORKSPACE_SURFACE_ARC", 0), ("PALETTE_CORNER_ARC", 0), ("DOCK_DROP_ARC", 0), ("RAIL_BUTTON_ARC", 0),
    ("NATIVE_TOOLBAR_HOVER_ARC", 0), ("TITLE_SEARCH_ARC", 0), ("PROMPT_TILE_RADIUS", 0), ("PROMPT_TILE", 30), ("ICON_PROMPT", 16),
    ("FORM_LABEL_WIDTH", 100), ("COLUMN_GAP", 12), ("SECTION_GAP", 12), ("SHEET_SECTION_GAP", 12), ("FORM_SIDES", 16), ("FORM_TOP", 12),
    ("CHIP_HEIGHT", 14), ("PANEL_CHIP_HEIGHT", 14), ("SETTING_HEIGHT", 26), ("SETTING_DESCRIPTION_HEIGHT", 42),
    ("FULL_SETTING_DESCRIPTION_HEIGHT", 42), ("THEME_PREVIEW_WIDTH", 150), ("THEME_PREVIEW_HEIGHT", 78), ("THEME_PREVIEW_GAP", 20),
    ("THEME_PREVIEW_CAPTION_GAP", 8)]
puts = "\n".join(f"        values.put(Role.{n}, {v});" for n, v in CLASSIC)
edit("jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java", [
    ('''    /** The default catalog: each role's declared value, frozen by compact-roles.tsv. */
    public static final DesignMetrics DEFAULT = new DesignMetrics(declared());''',
     f'''    /** The default catalog: each role's declared value, frozen by compact-roles.tsv. */
    public static final DesignMetrics DEFAULT = new DesignMetrics(declared());
    /**
     * The classic catalog (Enterprise presentation spec 5.5): the default with the classic look's delta, measured on the
     * Enterprise mocks and frozen by classic-roles.tsv. Its own tokens are the shipped Enterprise kit's.
     */
    public static final DesignMetrics CLASSIC = classic();''', 1),
    ('''    private static int[] declared() {''',
     f'''    private static DesignMetrics classic() {{
        var values = new java.util.EnumMap<Role, Integer>(Role.class);
{puts}
        int[] table = declared();
        values.forEach((role, value) -> table[role.ordinal()] = value);
        return new DesignMetrics(table);
    }}
    private static int[] declared() {{''', 1),
    ('''    /** The vertical padding a content height adds to its font's line. */''',
     '''    /**
     * This catalog tuned by {@code tokens}: each token's roles move by the token's change from this catalog's own value
     * (never below zero), and the field and button paddings follow the control height (spec 5.4).
     */
    public DesignMetrics with(MetricTokens tokens) {
        Objects.requireNonNull(tokens, "tokens");
        var own = MetricTokens.of(this);
        int[] copy = values.clone();
        for (var token : MetricTokens.Token.values()) {
            int delta = tokens.value(token) - own.value(token);
            for (var role : token.roles()) copy[role.ordinal()] = Math.max(0, values[role.ordinal()] + delta);
        }
        int padding = MetricTokens.padding(tokens.value(MetricTokens.Token.CONTROL_HEIGHT));
        copy[Role.FIELD_VERTICAL_PADDING.ordinal()] = padding;
        copy[Role.BUTTON_VERTICAL_PADDING.ordinal()] = padding;
        return new DesignMetrics(copy);
    }
    /** The vertical padding a content height adds to its font's line. */''', 1)])
compact = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv").read_text(encoding="utf-8")
delta = dict(CLASSIC); rows = []
for line in compact.splitlines():
    name, value = line.split("\t"); rows.append(f"{name}\t{delta.pop(name, int(value))}")
if delta: raise SystemExit(f"classic delta names unknown roles: {sorted(delta)}")
pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-roles.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.*' && ./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.StaticDefaultTest'`
Expected: PASS (`MetricTokensTest` 6 tests, `ClassicCatalogTest` 2; `StaticDefaultTest` finds no `CLASSIC` read outside
the catalog's owners).

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/metrics/MetricTokens.java jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java \
  jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-roles.tsv jasper-ui/src/test/java/dev/jasper/ui/metrics/MetricTokensTest.java \
  jasper-ui/src/test/java/dev/jasper/ui/metrics/ClassicCatalogTest.java jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java
git commit -F - <<'MSG'
Classic catalog: the public metric tokens and DesignMetrics.CLASSIC

MetricTokens holds spec 5.4's 11 tokens with their keys, ranges and roles;
DesignMetrics.with(MetricTokens) moves each token's roles by its change from the
base and recomputes the paddings. DesignMetrics.CLASSIC is the default with the
measured classic delta, frozen as classic-roles.tsv; StaticDefaultTest keeps it
owner-only.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 4: `TypeScale.CLASSIC`

The classic type scale is spec 5.6's 12-based table, with the calibrated roles of Rulings 11 and no label tracking
(Rulings 13), frozen as `classic-type.tsv`. `TypeScale` gains `labelTracking()`, which `Typography` reads where it applied
`SECTION_LABEL`'s 0.06 tracking.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/TypeScale.java`, `jasper-ui/src/main/java/dev/jasper/ui/metrics/Typography.java:84`
- Create: `jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-type.tsv`, `jasper-ui/src/test/java/dev/jasper/ui/metrics/ClassicTypeTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java:47-48`

**Interfaces:**
- Consumes: Task 3's text in `StaticDefaultTest.CATALOGS` (its edit script's anchor); no other task's code.
- Produces: `public static final TypeScale TypeScale.CLASSIC` (base 12, body 12); `public boolean TypeScale.labelTracking()`
  (true for `DEFAULT`, false for `CLASSIC`, kept by `withBody`).

- [ ] **Step 1: Write the failing test and the frozen table**

Create `jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-type.tsv` with exactly these 38 lines (tab-separated,
in `Typography.Role` order, a final newline):

```text
PROMPT_TITLE	15	600
DETAIL_TITLE	15	600
WINDOW_TITLE	12	600
PANEL_TITLE	12	600
PANEL_DETAIL_TITLE	12	600
PANEL_DETAIL_LABEL	12	400
PANEL_HEADER_TITLE	12	600
PANEL_HEADER_COUNT	11	400
PANEL_SEARCH	12	400
PANEL_ROW_SELECTED	12	500
PANEL_ROW_SECONDARY	12	400
PANEL_DETAIL_MONO	12	400
PANEL_EMPTY_DESCRIPTION	12	400
PANEL_ROW_CHIP	10	500
PICKER_COMPACT	11	400
PICKER_CAPTION	11	400
TITLE_CONTEXT	12	400
TOOLBAR_LABEL	12	400
STATUS_TEXT	12	400
STATUS_PATH	12	400
RESULTS_HEADING	11	400
PALETTE_HINT	11	400
BODY	12	400
BODY_STRONG	12	500
LABEL	12	400
DESCRIPTION	11	400
SETTINGS_BODY	12	400
SETTINGS_PREVIEW	12	400
SUPPORTING	11	400
CAPTION	11	400
SETTING_DESCRIPTION	11	400
SECTION_LABEL	12	600
COUNT	11	400
CHIP	10	500
MONO	12	400
MONO_SMALL	11	400
MONO_CAPTION	11	400
ORDERED_ACTION	12	400
```

Write it with this script, so the tabs are exact:

```bash
python3 -I - <<'PY'
import pathlib
rows = """PROMPT_TITLE 15 600|DETAIL_TITLE 15 600|WINDOW_TITLE 12 600|PANEL_TITLE 12 600|PANEL_DETAIL_TITLE 12 600|PANEL_DETAIL_LABEL 12 400|PANEL_HEADER_TITLE 12 600|PANEL_HEADER_COUNT 11 400|PANEL_SEARCH 12 400|PANEL_ROW_SELECTED 12 500|PANEL_ROW_SECONDARY 12 400|PANEL_DETAIL_MONO 12 400|PANEL_EMPTY_DESCRIPTION 12 400|PANEL_ROW_CHIP 10 500|PICKER_COMPACT 11 400|PICKER_CAPTION 11 400|TITLE_CONTEXT 12 400|TOOLBAR_LABEL 12 400|STATUS_TEXT 12 400|STATUS_PATH 12 400|RESULTS_HEADING 11 400|PALETTE_HINT 11 400|BODY 12 400|BODY_STRONG 12 500|LABEL 12 400|DESCRIPTION 11 400|SETTINGS_BODY 12 400|SETTINGS_PREVIEW 12 400|SUPPORTING 11 400|CAPTION 11 400|SETTING_DESCRIPTION 11 400|SECTION_LABEL 12 600|COUNT 11 400|CHIP 10 500|MONO 12 400|MONO_SMALL 11 400|MONO_CAPTION 11 400|ORDERED_ACTION 12 400"""
lines = ["\t".join(row.split(" ")) for row in rows.split("|")]
if len(lines) != 38: raise SystemExit("expected 38 roles")
pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-type.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
```

Create `jasper-ui/src/test/java/dev/jasper/ui/metrics/ClassicTypeTest.java`:

```java
package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 5.6: the classic 12-based type scale, frozen whole. */
class ClassicTypeTest {
    @Test void everyRoleHasItsClassicSizeAndWeight() throws Exception {
        var table = new LinkedHashMap<String, float[]>();
        try (var in = ClassicTypeTest.class.getResourceAsStream("classic-type.tsv")) {
            for (var line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                var cells = line.split("\t");
                table.put(cells[0], new float[] {Float.parseFloat(cells[1]), Integer.parseInt(cells[2])});
            }
        }
        assertThat(Arrays.stream(Typography.Role.values()).map(Enum::name).toList()).containsExactlyElementsOf(table.keySet());
        for (var role : Typography.Role.values()) {
            assertThat(TypeScale.CLASSIC.size(role)).as(role.name()).isEqualTo(table.get(role.name())[0]);
            assertThat(TypeScale.CLASSIC.weight(role)).as(role.name()).isEqualTo((int) table.get(role.name())[1]);
        }
        assertThat(TypeScale.CLASSIC.base()).isEqualTo(12f);
        assertThat(TypeScale.CLASSIC.body()).isEqualTo(12f);
    }

    /** Rulings 13: group titles carry no extra tracking under the classic scale; the default keeps its own. */
    @Test void onlyTheDefaultScaleTracksItsSectionLabels() {
        assertThat(TypeScale.DEFAULT.labelTracking()).isTrue();
        assertThat(TypeScale.CLASSIC.labelTracking()).isFalse();
        assertThat(TypeScale.CLASSIC.withBody(14).labelTracking()).isFalse();
        assertThat(TypeScale.CLASSIC.withBody(12)).isEqualTo(TypeScale.CLASSIC);
        assertThat(TypeScale.CLASSIC).isNotEqualTo(TypeScale.DEFAULT);
    }

    /** A role draws at its size times the body over the base: a user size of 14 scales every classic role by 14/12. */
    @Test void aUserSizeScalesTheClassicRoles() {
        var typography = Typography.of(TypeScale.CLASSIC, FaceSet.PLATFORM,
            new Typography.UserFont(java.util.Optional.of("SansSerif"), java.util.Optional.of(14f)));
        assertThat(typography.font(Typography.Role.CAPTION).getSize2D()).isEqualTo(11f * 14f / 12f);
        assertThat(typography.font(Typography.Role.PROMPT_TITLE).getSize2D()).isEqualTo(15f * 14f / 12f);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.ClassicTypeTest'`
Expected: FAIL at compile: "cannot find symbol … variable CLASSIC" and "method labelTracking()".

- [ ] **Step 3: Add the classic scale and the tracking flag**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4a Task 4 step 3: TypeScale.CLASSIC and labelTracking."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
rows = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-type.tsv").read_text(encoding="utf-8").splitlines()
cases = "\n".join(f"            case {r.split(chr(9))[0]} -> new float[] {{{r.split(chr(9))[1]}, {r.split(chr(9))[2]}}};" for r in rows)
edit("jasper-ui/src/main/java/dev/jasper/ui/metrics/TypeScale.java", [
    ('''    /** Today's 13-point table at a 13-point body, frozen by default-type.tsv. */
    public static final TypeScale DEFAULT = new TypeScale(declaredSizes(), declaredWeights(), 13f, 13f);

    private final float[] sizes;
    private final int[] weights;
    private final float base;
    private final float body;

    private TypeScale(float[] sizes, int[] weights, float base, float body) {
        this.sizes = sizes; this.weights = weights; this.base = base; this.body = body;
    }''',
     '''    /** Today's 13-point table at a 13-point body, frozen by default-type.tsv. */
    public static final TypeScale DEFAULT = new TypeScale(declaredSizes(), declaredWeights(), 13f, 13f, true);
    /** The classic look's 12-point table at a 12-point body (Enterprise presentation spec 5.6), frozen by classic-type.tsv. */
    public static final TypeScale CLASSIC = classic();

    private final float[] sizes;
    private final int[] weights;
    private final float base;
    private final float body;
    private final boolean labelTracking;

    private TypeScale(float[] sizes, int[] weights, float base, float body, boolean labelTracking) {
        this.sizes = sizes; this.weights = weights; this.base = base; this.body = body; this.labelTracking = labelTracking;
    }''', 1),
    ('''    /** The body size this scale draws at when the user sets none. */
    public float body() { return body; }''',
     '''    /** The body size this scale draws at when the user sets none. */
    public float body() { return body; }
    /** Whether section labels take the platform face's extra letter spacing (the default look's; the classic has none). */
    public boolean labelTracking() { return labelTracking; }''', 1),
    ('''        return new TypeScale(sizes, weights, base, body);
    }''',
     '''        return new TypeScale(sizes, weights, base, body, labelTracking);
    }

    private static TypeScale classic() {
        var roles = Typography.Role.values();
        var sizes = new float[roles.length];
        var weights = new int[roles.length];
        for (var role : roles) {
            float[] row = classicRow(role);
            sizes[role.ordinal()] = row[0];
            weights[role.ordinal()] = (int) row[1];
        }
        return new TypeScale(sizes, weights, 12f, 12f, false);
    }

    /** {@code role}'s classic size and weight. */
    private static float[] classicRow(Typography.Role role) {
        return switch (role) {
''' + cases + '''
        };
    }''', 1),
    ('''            && Float.compare(base, scale.base) == 0 && Float.compare(body, scale.body) == 0;
    }
    @Override public int hashCode() { return Objects.hash(Arrays.hashCode(sizes), Arrays.hashCode(weights), base, body); }''',
     '''            && Float.compare(base, scale.base) == 0 && Float.compare(body, scale.body) == 0 && labelTracking == scale.labelTracking;
    }
    @Override public int hashCode() { return Objects.hash(Arrays.hashCode(sizes), Arrays.hashCode(weights), base, body, labelTracking); }''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/metrics/Typography.java", [
    ('''TextAttribute.TRACKING, platformDefault && role == Role.SECTION_LABEL ? .060f : .018f));''',
     '''TextAttribute.TRACKING, platformDefault && role == Role.SECTION_LABEL && scale.labelTracking() ? .060f : .018f));''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java", [
    ('''    static final Set<String> CATALOGS = Set.of(METRICS + ".DEFAULT", METRICS + ".CLASSIC", "dev/jasper/ui/metrics/TypeScale.DEFAULT",
        "dev/jasper/ui/metrics/FaceSet.PLATFORM");''',
     '''    static final Set<String> CATALOGS = Set.of(METRICS + ".DEFAULT", METRICS + ".CLASSIC", "dev/jasper/ui/metrics/TypeScale.DEFAULT",
        "dev/jasper/ui/metrics/TypeScale.CLASSIC", "dev/jasper/ui/metrics/FaceSet.PLATFORM");''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.*' && ./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.StaticDefaultTest' --tests 'dev.jasper.app.appearance.UiTypographyTest'`
Expected: PASS (`ClassicTypeTest` 3 tests; `TypographyTest` and `TypeScaleTest`, including the default oracle over
every user-font mode, unchanged).

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/metrics/TypeScale.java jasper-ui/src/main/java/dev/jasper/ui/metrics/Typography.java \
  jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-type.tsv jasper-ui/src/test/java/dev/jasper/ui/metrics/ClassicTypeTest.java \
  jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java
git commit -F - <<'MSG'
Classic catalog: the classic type scale

TypeScale.CLASSIC is spec 5.6's 12-based table with the roles calibrated on the
mocks, frozen as classic-type.tsv; it tracks no section label, which
TypeScale.labelTracking() carries to Typography.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 5: The classic FlatLaf overlay

`UiKeySet.CLASSIC` is the default key set with spec 6.5's overlay (and Rulings 15's two foregrounds), each from a
semantic or derived role; `UiColorKeys` gains a derived source. The overlay is frozen as `ui-color-keys-classic.tsv`;
`UiKeySet.DEFAULT` keeps `ui-color-keys.tsv`.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/UiKeySet.java`, `jasper-ui/src/main/java/dev/jasper/ui/theme/UiColorKeys.java:22-46`
- Create: `jasper-ui/src/test/resources/dev/jasper/ui/theme/ui-color-keys-classic.tsv`, `jasper-ui/src/test/java/dev/jasper/ui/theme/ClassicUiKeysTest.java`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `UiKeySet.CLASSIC`; `UiColorKeys.flatLaf(theme, UiKeySet.CLASSIC)` returns the default keys, in order, with
  the overlay's existing keys replaced in place and its new keys appended.

- [ ] **Step 1: Write the failing test and the frozen overlay**

Create `jasper-ui/src/test/resources/dev/jasper/ui/theme/ui-color-keys-classic.tsv` with this content (tab-separated):

```text
# The classic overlay on ui-color-keys.tsv (spec 6.5, plan 4 Rulings 15): FlatLaf key, then a SemanticColor or a derived role key.
TableHeader.background	surface.tableHeader
TableHeader.foreground	TEXT_SECONDARY
TableHeader.separatorColor	border.divider
TableHeader.bottomSeparatorColor	border.divider
Table.alternateRowColor	surface.zebra
Table.gridColor	border.divider
Table.selectionBackground	SELECTION_BG
Table.selectionForeground	TEXT_ONSELECTION
Tree.selectionBackground	SELECTION_BG
Tree.selectionForeground	TEXT_ONSELECTION
Button.startBackground	BUTTON_SECONDARY_BG
Button.endBackground	button.end
Button.hoverBorderColor	border.hover
Button.default.borderColor	accent.edge
Button.default.startBorderColor	accent.edge
Button.default.endBorderColor	accent.edge
MenuBar.selectionBackground	ACCENT
MenuBar.selectionForeground	TEXT_ONACCENT
ToolBar.separatorColor	border.divider
```

Write it with a script so the tabs are exact:

```bash
python3 -I - <<'PY'
import pathlib
rows = [("TableHeader.background", "surface.tableHeader"), ("TableHeader.foreground", "TEXT_SECONDARY"),
    ("TableHeader.separatorColor", "border.divider"), ("TableHeader.bottomSeparatorColor", "border.divider"),
    ("Table.alternateRowColor", "surface.zebra"), ("Table.gridColor", "border.divider"), ("Table.selectionBackground", "SELECTION_BG"),
    ("Table.selectionForeground", "TEXT_ONSELECTION"), ("Tree.selectionBackground", "SELECTION_BG"), ("Tree.selectionForeground", "TEXT_ONSELECTION"),
    ("Button.startBackground", "BUTTON_SECONDARY_BG"), ("Button.endBackground", "button.end"), ("Button.hoverBorderColor", "border.hover"),
    ("Button.default.borderColor", "accent.edge"), ("Button.default.startBorderColor", "accent.edge"),
    ("Button.default.endBorderColor", "accent.edge"), ("MenuBar.selectionBackground", "ACCENT"), ("MenuBar.selectionForeground", "TEXT_ONACCENT"),
    ("ToolBar.separatorColor", "border.divider")]
head = "# The classic overlay on ui-color-keys.tsv (spec 6.5, plan 4 Rulings 15): FlatLaf key, then a SemanticColor or a derived role key.\n"
pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/theme/ui-color-keys-classic.tsv").write_text(
    head + "".join(f"{k}\t{v}\n" for k, v in rows), encoding="utf-8")
PY
```

Create `jasper-ui/src/test/java/dev/jasper/ui/theme/ClassicUiKeysTest.java`:

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 6.5: the classic key set is the default one with the frozen classic overlay. */
class ClassicUiKeysTest {
    static LinkedHashMap<String, String> overlay() throws IOException {
        var table = new LinkedHashMap<String, String>();
        try (var in = ClassicUiKeysTest.class.getResourceAsStream("ui-color-keys-classic.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t");
                table.put(cells[0], cells[1]);
            }
        }
        return table;
    }

    /** A source name: a SemanticColor constant, or a derived role's key. */
    static String colour(GeneratedTheme theme, String source) {
        for (var role : DerivedColor.values()) if (role.key().equals(source)) return ColorMath.hex(role.color(theme));
        return ColorMath.hex(SemanticColor.valueOf(source).color(theme));
    }

    @Test void theClassicKeysAreTheDefaultKeysWithTheOverlay() throws IOException {
        for (var theme : dev.jasper.ui.testsupport.KitFixtures.themes()) {
            var base = UiColorKeys.flatLaf(theme, UiKeySet.DEFAULT);
            var classic = UiColorKeys.flatLaf(theme, UiKeySet.CLASSIC);
            var expected = new LinkedHashMap<String, Object>(base);
            for (var entry : overlay().entrySet()) expected.put(entry.getKey(), colour(theme, entry.getValue()));
            assertThat(classic).as(theme.name()).containsExactlyEntriesOf(expected);
        }
    }

    /** Overlay keys FlatLaf already had keep their place; new keys follow every wildcard, so they win over it. */
    @Test void newKeysComeAfterTheWildcards() throws IOException {
        var keys = new ArrayList<>(UiColorKeys.flatLaf(dev.jasper.ui.testsupport.KitFixtures.light(), UiKeySet.CLASSIC).keySet());
        int lastWildcard = 0;
        for (int i = 0; i < keys.size(); i++) if (keys.get(i).startsWith("*.")) lastWildcard = i;
        for (var key : overlay().keySet()) assertThat(keys.indexOf(key)).as(key).isGreaterThan(lastWildcard);
        assertThat(UiColorKeys.flatLaf(dev.jasper.ui.testsupport.KitFixtures.light(), UiKeySet.CLASSIC).get("Table.selectionBackground"))
            .isEqualTo(ColorMath.hex(dev.jasper.ui.testsupport.KitFixtures.light().semantic().color(SemanticColor.SELECTION_BG)));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.ClassicUiKeysTest'`
Expected: FAIL at compile: "cannot find symbol … variable CLASSIC" in `UiKeySet`.

- [ ] **Step 3: Add the classic key set and the derived source**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4a Task 5 step 3: UiKeySet.CLASSIC and the classic overlay."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/theme/UiKeySet.java", [
    ('''    /** The default look's keys, frozen by ui-color-keys.tsv. */
    DEFAULT''',
     '''    /** The default look's keys, frozen by ui-color-keys.tsv. */
    DEFAULT,
    /** The default keys with the classic look's overlay (headers, zebra rows, gradient buttons), frozen by ui-color-keys-classic.tsv. */
    CLASSIC''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/theme/UiColorKeys.java", [
    (''' * FlatLaf's colour defaults for a theme variant in a {@link UiKeySet}, each from a semantic role; text selection in text
 * components takes the variant's terminal selection and a search field's no-match background the
 * {@linkplain #errorTint error tint}. Order matters: a {@code *.suffix} key recolours the keys before it that end in the
 * suffix.''',
     ''' * FlatLaf's colour defaults for a theme variant in a {@link UiKeySet}, each from a semantic role (or, in the classic
 * set, a derived one); text selection in text components takes the variant's terminal selection and a search field's
 * no-match background the {@linkplain #errorTint error tint}. Order matters: a {@code *.suffix} key recolours the keys
 * before it that end in the suffix.''', 1),
    ('''    private sealed interface Source permits Role, TextSelection, ErrorTint {}
    private record Role(SemanticColor role) implements Source {}''',
     '''    private sealed interface Source permits Role, Derived, TextSelection, ErrorTint {}
    private record Role(SemanticColor role) implements Source {}
    private record Derived(DerivedColor role) implements Source {}''', 1),
    ('''    private static final Map<String, Source> KEYS = keys();''',
     '''    private static final Map<String, Source> KEYS = keys();
    private static final Map<String, Source> CLASSIC_KEYS = classicKeys();''', 1),
    ('''            case DEFAULT -> KEYS;
        };
        return values(sources, theme.semantic(), theme.terminal().selection());
    }

    private static Map<String, Object> values(Map<String, Source> sources, SemanticPalette palette, Color textSelection) {
        var keys = new LinkedHashMap<String, Object>();
        sources.forEach((key, source) -> keys.put(key, ColorMath.hex(switch (source) {
            case Role role -> palette.color(role.role());''',
     '''            case DEFAULT -> KEYS;
            case CLASSIC -> CLASSIC_KEYS;
        };
        return values(sources, theme, theme.terminal().selection());
    }

    private static Map<String, Object> values(Map<String, Source> sources, GeneratedTheme theme, Color textSelection) {
        var palette = theme.semantic();
        var keys = new LinkedHashMap<String, Object>();
        sources.forEach((key, source) -> keys.put(key, ColorMath.hex(switch (source) {
            case Role role -> palette.color(role.role());
            case Derived role -> role.role().color(theme);''', 1),
    ('''    private static void role(Map<String, Source> keys, String key, SemanticColor role) { keys.put(key, new Role(role)); }''',
     '''    private static void role(Map<String, Source> keys, String key, SemanticColor role) { keys.put(key, new Role(role)); }

    /**
     * The classic overlay (spec 6.5): the default keys, an existing key recoloured in place and a new key after every
     * wildcard, so the table and tree selections take {@code selection.bg} over the terminal-selection wildcard.
     */
    private static Map<String, Source> classicKeys() {
        var keys = new LinkedHashMap<String, Source>(KEYS);
        keys.put("TableHeader.background", new Derived(DerivedColor.SURFACE_TABLE_HEADER));
        role(keys, "TableHeader.foreground", TEXT_SECONDARY);
        keys.put("TableHeader.separatorColor", new Derived(DerivedColor.BORDER_DIVIDER));
        keys.put("TableHeader.bottomSeparatorColor", new Derived(DerivedColor.BORDER_DIVIDER));
        keys.put("Table.alternateRowColor", new Derived(DerivedColor.SURFACE_ZEBRA));
        keys.put("Table.gridColor", new Derived(DerivedColor.BORDER_DIVIDER));
        role(keys, "Table.selectionBackground", SELECTION_BG);
        role(keys, "Table.selectionForeground", TEXT_ONSELECTION);
        role(keys, "Tree.selectionBackground", SELECTION_BG);
        role(keys, "Tree.selectionForeground", TEXT_ONSELECTION);
        role(keys, "Button.startBackground", BUTTON_SECONDARY_BG);
        keys.put("Button.endBackground", new Derived(DerivedColor.BUTTON_END));
        keys.put("Button.hoverBorderColor", new Derived(DerivedColor.BORDER_HOVER));
        keys.put("Button.default.borderColor", new Derived(DerivedColor.ACCENT_EDGE));
        // FlatLaf reads the default border from startBorderColor first and ignores borderColor while it is set.
        keys.put("Button.default.startBorderColor", new Derived(DerivedColor.ACCENT_EDGE));
        keys.put("Button.default.endBorderColor", new Derived(DerivedColor.ACCENT_EDGE));
        role(keys, "MenuBar.selectionBackground", ACCENT);
        role(keys, "MenuBar.selectionForeground", TEXT_ONACCENT);
        keys.put("ToolBar.separatorColor", new Derived(DerivedColor.BORDER_DIVIDER));
        return Collections.unmodifiableMap(keys);
    }''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.*' --tests 'dev.jasper.ui.swing.FlatLafInstallerKitTest' --continue`
Expected: PASS (`ClassicUiKeysTest` 2 tests; `UiColorKeysTest` unchanged against `ui-color-keys.tsv`).

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/theme/UiKeySet.java jasper-ui/src/main/java/dev/jasper/ui/theme/UiColorKeys.java \
  jasper-ui/src/test/resources/dev/jasper/ui/theme/ui-color-keys-classic.tsv jasper-ui/src/test/java/dev/jasper/ui/theme/ClassicUiKeysTest.java
git commit -F - <<'MSG'
Classic catalog: the classic FlatLaf overlay

UiKeySet.CLASSIC is the default key set with spec 6.5's overlay (table headers,
zebra and grid, selection backgrounds and foregrounds, gradient buttons and their
borders, menu bar selection, toolbar separators), filled from semantic and
derived roles and frozen as ui-color-keys-classic.tsv.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 6: `ControlVariants`, `ColorSlots`, `Kit.CLASSIC` and the fixture kits

`Kit` gains `ControlVariants` (spec 5.2's 14 control points) and `ColorSlots` (spec 5.7), in spec 6.1's component order.
`Kit.CLASSIC` is the classic base; jasper-ui's test fixtures gain the Enterprise fixture kit, the complement kit and
`withControls`, which 4b and 4c render with.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/kit/ControlVariants.java`, `jasper-ui/src/main/java/dev/jasper/ui/kit/ColorSlots.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/kit/Kit.java`, `jasper-ui/src/main/java/dev/jasper/ui/kit/package-info.java`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java:25-28`
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/kit/KitTest.java` (whole file), `jasper-ui/src/test/java/dev/jasper/ui/swing/KitContextTest.java:63-64`, `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java:153-154`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java:58`, `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceMetricsTest.java:78-81` (the fully qualified `new dev.jasper.ui.kit.Kit(` calls)
- Modify: `jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java` (no production reader of `Kit.CLASSIC`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/kit/ControlVariantsTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/testsupport/FixtureKitsTest.java`

**Interfaces:**
- Consumes: Tasks 3 to 5 (`DesignMetrics.CLASSIC`, `MetricTokens`, `TypeScale.CLASSIC`, `UiKeySet.CLASSIC`).
- Produces: `public record ControlVariants(Toggle toggle, Exclusive exclusive, Tabs tabs, Group group, FormLabel formLabel,
  Empty empty, Table table, Tree tree, Stepper stepper, OrderedList orderedList, Buttons buttons, Tag tag, Route route,
  Prompt prompt)` with the nested enums `Toggle { SWITCH, CHECKBOX }`, `Exclusive { SEGMENTS, RADIOS }`,
  `Tabs { SEGMENTS, FOLDER }`, `Group { CARD, TITLED_BOX }`, `FormLabel { PLAIN, COLON }`, `Empty { TILE, TEXT }`,
  `Table { PLAIN, CLASSIC }`, `Tree { PLAIN, CLASSIC }`, `Stepper { STEPPER, SPINNER }`, `OrderedList { INLINE,
  SIDE_COLUMN }`, `Buttons { RAISED, CLASSIC }`, `Tag { TINT, BOXED }`, `Route { TILES, EDGED }`, `Prompt { TILE, PLAIN }`
  (each `implements ControlVariants.Variant` with `String key()`), `DEFAULT`, `CLASSIC`, `static ControlVariants
  of(boolean... classic)` (14 flags in spec order), `boolean[] flags()`, `ControlVariants complement()`;
  `public record ColorSlots(SemanticColor dialogSurface, SemanticColor navigationSurface)` with `DEFAULT` and `CLASSIC`;
  `public record Kit(String id, DesignMetrics metrics, TypeScale type, FaceSet faces, ControlVariants controls, ColorSlots
  colors, UiKeySet uiKeys)` with `DEFAULT` and `CLASSIC`; `KitFixtures.enterprise()`, `KitFixtures.complement()`,
  `KitFixtures.withControls(Kit, ControlVariants)`, `KitFixtures.enterpriseTokens()`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/kit/ControlVariantsTest.java`:

```java
package dev.jasper.ui.kit;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.theme.SemanticColor;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 5.2 and 5.7: the 14 control points and the two colour slots. */
class ControlVariantsTest {
    @Test void theFourteenPointsAreSpecFiveTwosInOrderWithTheirValues() {
        assertThat(Arrays.stream(ControlVariants.class.getRecordComponents()).map(c -> c.getName()).toList()).containsExactly(
            "toggle", "exclusive", "tabs", "group", "formLabel", "empty", "table", "tree", "stepper", "orderedList", "buttons", "tag",
            "route", "prompt");
        assertThat(ControlVariants.DEFAULT.keys()).containsExactly("switch", "segments", "segments", "card", "plain", "tile", "plain",
            "plain", "stepper", "inline", "raised", "tint", "tiles", "tile");
        assertThat(ControlVariants.CLASSIC.keys()).containsExactly("checkbox", "radios", "folder", "titled_box", "colon", "text",
            "classic", "classic", "spinner", "side_column", "classic", "boxed", "edged", "plain");
    }

    @Test void flagsRoundTripAndTheComplementFlipsEveryPoint() {
        boolean[] none = new boolean[14], all = new boolean[14];
        Arrays.fill(all, true);
        assertThat(ControlVariants.of(none)).isEqualTo(ControlVariants.DEFAULT);
        assertThat(ControlVariants.of(all)).isEqualTo(ControlVariants.CLASSIC);
        assertThat(ControlVariants.DEFAULT.complement()).isEqualTo(ControlVariants.CLASSIC);
        assertThat(ControlVariants.CLASSIC.complement()).isEqualTo(ControlVariants.DEFAULT);
        boolean[] mixed = {true, false, true, false, true, false, true, false, true, false, true, false, true, false};
        assertThat(ControlVariants.of(mixed).flags()).containsExactly(mixed);
        assertThat(ControlVariants.of(mixed).complement().flags()).containsExactly(
            false, true, false, true, false, true, false, true, false, true, false, true, false, true);
        assertThatIllegalArgumentException().isThrownBy(() -> ControlVariants.of(new boolean[13]))
            .withMessage("Expected 14 control points, got 13");
        assertThatNullPointerException().isThrownBy(() -> new ControlVariants(null, ControlVariants.Exclusive.SEGMENTS,
            ControlVariants.Tabs.SEGMENTS, ControlVariants.Group.CARD, ControlVariants.FormLabel.PLAIN, ControlVariants.Empty.TILE,
            ControlVariants.Table.PLAIN, ControlVariants.Tree.PLAIN, ControlVariants.Stepper.STEPPER, ControlVariants.OrderedList.INLINE,
            ControlVariants.Buttons.RAISED, ControlVariants.Tag.TINT, ControlVariants.Route.TILES, ControlVariants.Prompt.TILE));
    }

    @Test void theColourSlotsTakeOnlyTheirAllowedRoles() {
        assertThat(ColorSlots.DEFAULT).isEqualTo(new ColorSlots(SemanticColor.SURFACE_DIALOG, SemanticColor.SURFACE_SIDEBAR));
        assertThat(ColorSlots.CLASSIC).isEqualTo(new ColorSlots(SemanticColor.SURFACE_APP, SemanticColor.SURFACE_CARD));
        assertThatIllegalArgumentException().isThrownBy(() -> new ColorSlots(SemanticColor.SURFACE_CARD, SemanticColor.SURFACE_CARD))
            .withMessage("dialog_surface must be surface.dialog or surface.app, not surface.card");
        assertThatIllegalArgumentException().isThrownBy(() -> new ColorSlots(SemanticColor.SURFACE_APP, SemanticColor.SURFACE_APP))
            .withMessage("navigation_surface must be surface.sidebar or surface.card, not surface.app");
        assertThat(ColorSlots.DIALOG_SURFACES).containsExactly(SemanticColor.SURFACE_DIALOG, SemanticColor.SURFACE_APP);
        assertThat(ColorSlots.NAVIGATION_SURFACES).containsExactly(SemanticColor.SURFACE_SIDEBAR, SemanticColor.SURFACE_CARD);
    }
}
```

Replace `jasper-ui/src/test/java/dev/jasper/ui/kit/KitTest.java` with:

```java
package dev.jasper.ui.kit;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.theme.UiKeySet;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 6.1: a kit names a look's catalog, type, faces, control variants, colour slots and FlatLaf keys. */
class KitTest {
    private static Kit kit(String id, DesignMetrics metrics, TypeScale type, FaceSet faces, ControlVariants controls, ColorSlots colors, UiKeySet keys) {
        return new Kit(id, metrics, type, faces, controls, colors, keys);
    }

    @Test void theDefaultKitIsTodaysLook() {
        assertThat(Kit.DEFAULT.id()).isEqualTo("default");
        assertThat(Kit.DEFAULT.metrics()).isSameAs(DesignMetrics.DEFAULT);
        assertThat(Kit.DEFAULT.type()).isSameAs(TypeScale.DEFAULT);
        assertThat(Kit.DEFAULT.faces()).isSameAs(FaceSet.PLATFORM);
        assertThat(Kit.DEFAULT.controls()).isSameAs(ControlVariants.DEFAULT);
        assertThat(Kit.DEFAULT.colors()).isSameAs(ColorSlots.DEFAULT);
        assertThat(Kit.DEFAULT.uiKeys()).isEqualTo(UiKeySet.DEFAULT);
    }

    @Test void theClassicKitIsTheClassicBase() {
        assertThat(Kit.CLASSIC.id()).isEqualTo("classic");
        assertThat(Kit.CLASSIC.metrics()).isSameAs(DesignMetrics.CLASSIC);
        assertThat(Kit.CLASSIC.type()).isSameAs(TypeScale.CLASSIC);
        assertThat(Kit.CLASSIC.faces()).isSameAs(FaceSet.PLATFORM);
        assertThat(Kit.CLASSIC.controls()).isSameAs(ControlVariants.CLASSIC);
        assertThat(Kit.CLASSIC.colors()).isSameAs(ColorSlots.CLASSIC);
        assertThat(Kit.CLASSIC.uiKeys()).isEqualTo(UiKeySet.CLASSIC);
    }

    @Test void everyPartIsRequired() {
        var m = DesignMetrics.DEFAULT; var t = TypeScale.DEFAULT; var f = FaceSet.PLATFORM; var c = ControlVariants.DEFAULT;
        var s = ColorSlots.DEFAULT; var k = UiKeySet.DEFAULT;
        assertThatNullPointerException().isThrownBy(() -> kit(null, m, t, f, c, s, k)).withMessage("id");
        assertThatNullPointerException().isThrownBy(() -> kit("k", null, t, f, c, s, k)).withMessage("metrics");
        assertThatNullPointerException().isThrownBy(() -> kit("k", m, null, f, c, s, k)).withMessage("type");
        assertThatNullPointerException().isThrownBy(() -> kit("k", m, t, null, c, s, k)).withMessage("faces");
        assertThatNullPointerException().isThrownBy(() -> kit("k", m, t, f, null, s, k)).withMessage("controls");
        assertThatNullPointerException().isThrownBy(() -> kit("k", m, t, f, c, null, k)).withMessage("colors");
        assertThatNullPointerException().isThrownBy(() -> kit("k", m, t, f, c, s, null)).withMessage("uiKeys");
        assertThatIllegalArgumentException().isThrownBy(() -> kit(" ", m, t, f, c, s, k)).withMessage("Blank kit id");
    }

    @Test void kitsCompareByValue() {
        var same = kit("default", DesignMetrics.DEFAULT.with(DesignMetrics.Role.HAIRLINE, 1), TypeScale.DEFAULT.withBody(13),
            FaceSet.PLATFORM, ControlVariants.DEFAULT, ColorSlots.DEFAULT, UiKeySet.DEFAULT);
        assertThat(same).isEqualTo(Kit.DEFAULT).hasSameHashCodeAs(Kit.DEFAULT).isNotSameAs(Kit.DEFAULT);
        assertThat(kit("default", DesignMetrics.DEFAULT, TypeScale.DEFAULT, FaceSet.PLATFORM, ControlVariants.CLASSIC, ColorSlots.DEFAULT,
            UiKeySet.DEFAULT)).isNotEqualTo(Kit.DEFAULT);
    }
}
```

Create `jasper-ui/src/test/java/dev/jasper/ui/testsupport/FixtureKitsTest.java`:

```java
package dev.jasper.ui.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.ColorSlots;
import dev.jasper.ui.kit.ControlVariants;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.MetricTokens;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.UiKeySet;
import org.junit.jupiter.api.Test;

/** Spec 16.1.3: the Enterprise fixture kit mirrors the shipped kit file; the complement flips every control point. */
class FixtureKitsTest {
    @Test void theEnterpriseFixtureIsTheShippedKitFilesValues() {
        var kit = KitFixtures.enterprise();
        assertThat(kit.id()).isEqualTo("enterprise");
        var tokens = KitFixtures.enterpriseTokens();
        assertThat(java.util.List.of(MetricTokens.Token.values()).stream().map(tokens::value).toList())
            .as("spec 5.1's [metrics]").containsExactly(22, 22, 3, 2, 3, 2, 24, 12, 20, 230, 7);
        assertThat(kit.metrics()).isEqualTo(DesignMetrics.CLASSIC.with(tokens)).isEqualTo(DesignMetrics.CLASSIC);
        assertThat(kit.type()).isEqualTo(TypeScale.CLASSIC.withBody(12));
        assertThat(kit.controls()).isEqualTo(ControlVariants.CLASSIC);
        assertThat(kit.colors()).isEqualTo(new ColorSlots(SemanticColor.SURFACE_APP, SemanticColor.SURFACE_CARD));
        assertThat(kit.uiKeys()).isEqualTo(UiKeySet.CLASSIC);
    }

    @Test void theComplementKeepsEnterprisesTokensAndTypeAndFlipsEveryPoint() {
        var complement = KitFixtures.complement();
        assertThat(complement.metrics()).isEqualTo(KitFixtures.enterprise().metrics());
        assertThat(complement.type()).isEqualTo(KitFixtures.enterprise().type());
        assertThat(complement.controls()).isEqualTo(ControlVariants.DEFAULT);
        assertThat(KitFixtures.withControls(Kit.DEFAULT, ControlVariants.CLASSIC).controls()).isEqualTo(ControlVariants.CLASSIC);
        assertThat(KitFixtures.withControls(Kit.DEFAULT, ControlVariants.CLASSIC).metrics()).isSameAs(DesignMetrics.DEFAULT);
    }
}
```

Run from the worktree root, to update the five other constructor call sites (three `new Kit(` and two fully
qualified `new dev.jasper.ui.kit.Kit(`) and keep `Kit.CLASSIC` free of production readers in the static-default scan:

```bash
python3 -I - <<'PY'
"""Plan 4a Task 6 step 1: Kit call sites take the controls and colour slots; Kit.CLASSIC has no production reader."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/test/java/dev/jasper/ui/swing/KitContextTest.java", [
    ('''new Kit("large-body", DesignMetrics.DEFAULT, TypeScale.DEFAULT.withBody(20),
                FaceSet.PLATFORM, UiKeySet.DEFAULT));''',
     '''new Kit("large-body", DesignMetrics.DEFAULT, TypeScale.DEFAULT.withBody(20),
                FaceSet.PLATFORM, dev.jasper.ui.kit.ControlVariants.DEFAULT, dev.jasper.ui.kit.ColorSlots.DEFAULT, UiKeySet.DEFAULT));''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java", [
    ('''        return new Kit("divider", Kit.DEFAULT.metrics().with(DesignMetrics.Role.NATIVE_SPLIT_DIVIDER, 5), Kit.DEFAULT.type(),
            Kit.DEFAULT.faces(), Kit.DEFAULT.uiKeys());''',
     '''        return new Kit("divider", Kit.DEFAULT.metrics().with(DesignMetrics.Role.NATIVE_SPLIT_DIVIDER, 5), Kit.DEFAULT.type(),
            Kit.DEFAULT.faces(), Kit.DEFAULT.controls(), Kit.DEFAULT.colors(), Kit.DEFAULT.uiKeys());''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java", [
    ('''base.type(),base.faces(),base.uiKeys());''',
     '''base.type(),base.faces(),base.controls(),base.colors(),base.uiKeys());''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceMetricsTest.java", [
    ('''                base.type(), base.faces(), base.uiKeys());''',
     '''                base.type(), base.faces(), base.controls(), base.colors(), base.uiKeys());''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java", [
    ('''    /** The default kit with {@code metrics}: a kit that differs from the default in geometry alone. */
    public static Kit kit(DesignMetrics metrics) {
        return new Kit("test", metrics, Kit.DEFAULT.type(), Kit.DEFAULT.faces(), Kit.DEFAULT.uiKeys());
    }''',
     '''    /** The default kit with {@code metrics}: a kit that differs from the default in geometry alone. */
    public static Kit kit(DesignMetrics metrics) {
        return new Kit("test", metrics, Kit.DEFAULT.type(), Kit.DEFAULT.faces(), Kit.DEFAULT.controls(), Kit.DEFAULT.colors(),
            Kit.DEFAULT.uiKeys());
    }

    /** The shipped Enterprise kit file's {@code [metrics]} (spec 5.1), over the classic base's own tokens. */
    public static dev.jasper.ui.metrics.MetricTokens enterpriseTokens() {
        var tokens = dev.jasper.ui.metrics.MetricTokens.of(DesignMetrics.CLASSIC);
        int[] values = {22, 22, 3, 2, 3, 2, 24, 12, 20, 230, 7};
        for (var token : dev.jasper.ui.metrics.MetricTokens.Token.values()) tokens = tokens.with(token, values[token.ordinal()]);
        return tokens;
    }

    /**
     * Spec 16.1.3's Enterprise fixture kit: the classic base with the shipped file's tokens, its 12-point body, its control
     * variants, colour slots and key set, and the platform faces (the Plex faces arrive with plan 5).
     */
    public static Kit enterprise() {
        return new Kit("enterprise", DesignMetrics.CLASSIC.with(enterpriseTokens()), dev.jasper.ui.metrics.TypeScale.CLASSIC.withBody(12),
            dev.jasper.ui.metrics.FaceSet.PLATFORM, dev.jasper.ui.kit.ControlVariants.CLASSIC,
            new dev.jasper.ui.kit.ColorSlots(SemanticColor.SURFACE_APP, SemanticColor.SURFACE_CARD), UiKeySet.CLASSIC);
    }

    /** Spec 16.1.3's complement kit: every control point at the value Enterprise does not use, with Enterprise's tokens and type. */
    public static Kit complement() {
        var enterprise = enterprise();
        return new Kit("complement", enterprise.metrics(), enterprise.type(), enterprise.faces(), enterprise.controls().complement(),
            enterprise.colors(), enterprise.uiKeys());
    }

    /** {@code base} with {@code controls}: a covering-array row (spec 16.1.3). */
    public static Kit withControls(Kit base, dev.jasper.ui.kit.ControlVariants controls) {
        return new Kit(base.id() + "+controls", base.metrics(), base.type(), base.faces(), controls, base.colors(), base.uiKeys());
    }''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java", [
    ('''                            if (CATALOGS.contains(read) || read.equals(DEFAULT_KIT)) found.add(type + "." + name + ": " + read);''',
     '''                            if (CATALOGS.contains(read) || read.equals(DEFAULT_KIT) || read.equals(CLASSIC_KIT)) found.add(type + "." + name + ": " + read);''', 1),
    ('''    static final String ANIMATION = "dev/jasper/app/config/AnimationSettings", DEFAULT_KIT = "dev/jasper/ui/kit/Kit.DEFAULT";''',
     '''    static final String ANIMATION = "dev/jasper/app/config/AnimationSettings", DEFAULT_KIT = "dev/jasper/ui/kit/Kit.DEFAULT";
    /** The classic base; until plan 5's kit parser names it, no production class reads it. */
    static final String CLASSIC_KIT = "dev/jasper/ui/kit/Kit.CLASSIC";''', 1),
    ('''        assertThat(found.stream().filter(read -> !read.startsWith(ANIMATION + ".<clinit>: ") && !read.endsWith(": " + DEFAULT_KIT)))''',
     '''        assertThat(found.stream().filter(read -> read.endsWith(": " + CLASSIC_KIT))).as("readers of Kit.CLASSIC").isEmpty();
        assertThat(found.stream().filter(read -> !read.startsWith(ANIMATION + ".<clinit>: ") && !read.endsWith(": " + DEFAULT_KIT)))''', 1)])
PY
```

The `KitFixtures` import block already holds `dev.jasper.ui.kit.Kit`, `dev.jasper.ui.metrics.DesignMetrics` and
`dev.jasper.ui.theme.*` (so `SemanticColor` and `UiKeySet` resolve).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.kit.*' --tests 'dev.jasper.ui.testsupport.FixtureKitsTest'`
Expected: FAIL at compile: "cannot find symbol … class ControlVariants" and "class ColorSlots".

- [ ] **Step 3: Add the variants, the slots and the classic kit**

Create `jasper-ui/src/main/java/dev/jasper/ui/kit/ControlVariants.java`:

```java
package dev.jasper.ui.kit;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The value of each of the kit file's 14 {@code [controls]} points (Enterprise presentation spec 5.2), each two-valued:
 * the default look's value first, the classic look's second. Only {@code KitFaces.of} reads it, to choose each control's
 * face once.
 *
 * @param toggle a Toggle: a switch or a checkbox
 * @param exclusive an EXCLUSIVE choice: a segment track or a row of radios
 * @param tabs a TABS choice: a segment track or folder tabs
 * @param group Section and Card: a section label over a card, or a titled box
 * @param formLabel FormRow labels: plain, or a colon-ended label column
 * @param empty an Empty state: with its tile, or text only
 * @param table a Table: plain, or a header band with zebra rows
 * @param tree a Tree: plain, or classic disclosure and category rows
 * @param stepper a STEPPER or SLIDER field: a stepper, or a spinner
 * @param orderedList an ORDERED list: per-row commands, or a side column
 * @param buttons buttons and dropdown faces: raised, or classic gradients
 * @param tag a Chip: a borderless tint, or a boxed uppercase label
 * @param route a ROUTE strip: icon tiles, or edged cells
 * @param prompt a PROMPT form: the icon tile and title, or the plain classic prompt
 */
public record ControlVariants(Toggle toggle, Exclusive exclusive, Tabs tabs, Group group, FormLabel formLabel, Empty empty, Table table,
                              Tree tree, Stepper stepper, OrderedList orderedList, Buttons buttons, Tag tag, Route route, Prompt prompt) {
    /** A point's value, named in a kit file by {@link #key()}. */
    public interface Variant {
        /** The value's name in a kit file: the constant's name in lower case. */
        default String key() { return ((Enum<?>) this).name().toLowerCase(Locale.ROOT); }
    }
    public enum Toggle implements Variant { SWITCH, CHECKBOX }
    public enum Exclusive implements Variant { SEGMENTS, RADIOS }
    public enum Tabs implements Variant { SEGMENTS, FOLDER }
    public enum Group implements Variant { CARD, TITLED_BOX }
    public enum FormLabel implements Variant { PLAIN, COLON }
    public enum Empty implements Variant { TILE, TEXT }
    public enum Table implements Variant { PLAIN, CLASSIC }
    public enum Tree implements Variant { PLAIN, CLASSIC }
    public enum Stepper implements Variant { STEPPER, SPINNER }
    public enum OrderedList implements Variant { INLINE, SIDE_COLUMN }
    public enum Buttons implements Variant { RAISED, CLASSIC }
    public enum Tag implements Variant { TINT, BOXED }
    public enum Route implements Variant { TILES, EDGED }
    public enum Prompt implements Variant { TILE, PLAIN }

    /** Every point at the default look's value. */
    public static final ControlVariants DEFAULT = of(new boolean[14]);
    /** Every point at the classic look's value. */
    public static final ControlVariants CLASSIC = of(all());

    /** Every point is required. */
    public ControlVariants {
        Objects.requireNonNull(toggle, "toggle"); Objects.requireNonNull(exclusive, "exclusive"); Objects.requireNonNull(tabs, "tabs");
        Objects.requireNonNull(group, "group"); Objects.requireNonNull(formLabel, "formLabel"); Objects.requireNonNull(empty, "empty");
        Objects.requireNonNull(table, "table"); Objects.requireNonNull(tree, "tree"); Objects.requireNonNull(stepper, "stepper");
        Objects.requireNonNull(orderedList, "orderedList"); Objects.requireNonNull(buttons, "buttons"); Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(route, "route"); Objects.requireNonNull(prompt, "prompt");
    }

    /** The variants with each point at its classic value where {@code classic} is true, in spec 5.2's order. */
    public static ControlVariants of(boolean... classic) {
        if (classic.length != 14) throw new IllegalArgumentException("Expected 14 control points, got " + classic.length);
        return new ControlVariants(pick(Toggle.values(), classic[0]), pick(Exclusive.values(), classic[1]), pick(Tabs.values(), classic[2]),
            pick(Group.values(), classic[3]), pick(FormLabel.values(), classic[4]), pick(Empty.values(), classic[5]),
            pick(Table.values(), classic[6]), pick(Tree.values(), classic[7]), pick(Stepper.values(), classic[8]),
            pick(OrderedList.values(), classic[9]), pick(Buttons.values(), classic[10]), pick(Tag.values(), classic[11]),
            pick(Route.values(), classic[12]), pick(Prompt.values(), classic[13]));
    }

    /** Which points hold their classic value, in spec 5.2's order. */
    public boolean[] flags() {
        var values = values();
        boolean[] flags = new boolean[values.size()];
        for (int i = 0; i < flags.length; i++) flags[i] = ((Enum<?>) values.get(i)).ordinal() == 1;
        return flags;
    }

    /** Every point at its other value. */
    public ControlVariants complement() {
        boolean[] flags = flags();
        for (int i = 0; i < flags.length; i++) flags[i] = !flags[i];
        return of(flags);
    }

    /** The points' values in spec 5.2's order. */
    public List<Variant> values() {
        return List.of(toggle, exclusive, tabs, group, formLabel, empty, table, tree, stepper, orderedList, buttons, tag, route, prompt);
    }

    /** The points' values as a kit file names them, in spec 5.2's order. */
    public List<String> keys() { return values().stream().map(Variant::key).toList(); }

    private static <E> E pick(E[] values, boolean classic) { return values[classic ? 1 : 0]; }

    private static boolean[] all() {
        boolean[] all = new boolean[14];
        java.util.Arrays.fill(all, true);
        return all;
    }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/kit/ColorSlots.java`:

```java
package dev.jasper.ui.kit;

import dev.jasper.ui.theme.SemanticColor;
import java.util.List;
import java.util.Objects;

/**
 * The kit file's two colour slots (Enterprise presentation spec 5.7), each one of a closed set of theme roles: the
 * surface dialog bodies stand on, and the surface of the Settings navigation tree.
 *
 * @param dialogSurface {@code surface.dialog} or {@code surface.app}
 * @param navigationSurface {@code surface.sidebar} or {@code surface.card}
 */
public record ColorSlots(SemanticColor dialogSurface, SemanticColor navigationSurface) {
    /** The roles {@code dialog_surface} may name, the default's first. */
    public static final List<SemanticColor> DIALOG_SURFACES = List.of(SemanticColor.SURFACE_DIALOG, SemanticColor.SURFACE_APP);
    /** The roles {@code navigation_surface} may name, the default's first. */
    public static final List<SemanticColor> NAVIGATION_SURFACES = List.of(SemanticColor.SURFACE_SIDEBAR, SemanticColor.SURFACE_CARD);
    /** The default look's slots. */
    public static final ColorSlots DEFAULT = new ColorSlots(SemanticColor.SURFACE_DIALOG, SemanticColor.SURFACE_SIDEBAR);
    /** The classic base's slots, Enterprise's (plan 4 Rulings 10). */
    public static final ColorSlots CLASSIC = new ColorSlots(SemanticColor.SURFACE_APP, SemanticColor.SURFACE_CARD);

    /** Each slot holds one of its allowed roles. */
    public ColorSlots {
        Objects.requireNonNull(dialogSurface, "dialogSurface"); Objects.requireNonNull(navigationSurface, "navigationSurface");
        check("dialog_surface", dialogSurface, DIALOG_SURFACES);
        check("navigation_surface", navigationSurface, NAVIGATION_SURFACES);
    }

    private static void check(String key, SemanticColor role, List<SemanticColor> allowed) {
        if (!allowed.contains(role))
            throw new IllegalArgumentException(key + " must be " + allowed.get(0).key() + " or " + allowed.get(1).key() + ", not " + role.key());
    }
}
```

Replace `jasper-ui/src/main/java/dev/jasper/ui/kit/Kit.java` with:

```java
package dev.jasper.ui.kit;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.theme.UiKeySet;
import java.util.Objects;

/**
 * A look: the metric catalog, type scale, faces, control variants, colour slots and FlatLaf key set that the Swing kit
 * and the native chrome paint with (Enterprise presentation spec 6.1). Immutable and compared by value; the host fixes
 * one per process before any window.
 *
 * @param id a short name for diagnostics
 * @param metrics the logical geometry
 * @param type the type scale
 * @param faces the faces text draws in
 * @param controls the value of each control point
 * @param colors the colour slots
 * @param uiKeys the FlatLaf colour keys the theme's roles fill
 */
public record Kit(String id, DesignMetrics metrics, TypeScale type, FaceSet faces, ControlVariants controls, ColorSlots colors, UiKeySet uiKeys) {
    /** The default look: the default catalog, the 13-point type scale, the platform faces, the default controls, slots and keys. */
    public static final Kit DEFAULT = new Kit("default", DesignMetrics.DEFAULT, TypeScale.DEFAULT, FaceSet.PLATFORM, ControlVariants.DEFAULT,
        ColorSlots.DEFAULT, UiKeySet.DEFAULT);
    /** The classic base a kit file names with {@code base = "classic"}: the classic catalog, scale, controls, slots and keys. */
    public static final Kit CLASSIC = new Kit("classic", DesignMetrics.CLASSIC, TypeScale.CLASSIC, FaceSet.PLATFORM, ControlVariants.CLASSIC,
        ColorSlots.CLASSIC, UiKeySet.CLASSIC);

    /** Every part is required, and the id must not be blank. */
    public Kit {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(metrics, "metrics"); Objects.requireNonNull(type, "type");
        Objects.requireNonNull(faces, "faces"); Objects.requireNonNull(controls, "controls"); Objects.requireNonNull(colors, "colors");
        Objects.requireNonNull(uiKeys, "uiKeys");
        if (id.isBlank()) throw new IllegalArgumentException("Blank kit id");
    }
}
```

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4a Task 6 step 3: the kit package's documentation names the new values."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/kit/package-info.java", [
    ('''/**
 * The look: a {@link dev.jasper.ui.kit.Kit} names the metric catalog, type scale, faces and FlatLaf key set that the
 * Swing kit and the native chrome paint with; {@link dev.jasper.ui.kit.Kit#DEFAULT} is the default look.''',
     '''/**
 * The look: a {@link dev.jasper.ui.kit.Kit} names the metric catalog, type scale, faces, control variants
 * ({@link dev.jasper.ui.kit.ControlVariants}), colour slots ({@link dev.jasper.ui.kit.ColorSlots}) and FlatLaf key set
 * that the Swing kit and the native chrome paint with; {@link dev.jasper.ui.kit.Kit#DEFAULT} is the default look and
 * {@link dev.jasper.ui.kit.Kit#CLASSIC} the classic base.''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.kit.*' --tests 'dev.jasper.ui.testsupport.*' --tests 'dev.jasper.ui.swing.KitContextTest' && ./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.StaticDefaultTest' --tests 'dev.jasper.app.appearance.ThemeManagerTest' --tests 'dev.jasper.app.plugins.HostedKitTest' --tests 'dev.jasper.app.workspace.WorkspaceMetricsTest' --continue`
Expected: PASS (`ControlVariantsTest` 3 tests, `KitTest` 4, `FixtureKitsTest` 2; no production class reads `Kit.CLASSIC`).
Then recount the constructor's callers, so none was missed:

```bash
grep -rn 'Kit(' --include='*.java' jasper-ui/src jasper-app/src plugins | grep -E 'new (dev\.jasper\.ui\.kit\.)?Kit\(' | cut -d: -f1 | sort | uniq -c
```

Expected: exactly these seven files, every call on the seven-component constructor (the compile above proves that):
`Kit.java` 2, `KitTest.java` 1 (the `kit(...)` helper), `KitContextTest.java` 1, `KitFixtures.java` 4 (`kit`,
`enterprise`, `complement`, `withControls`), `ThemeManagerTest.java` 1, `HostedKitTest.java` 1,
`WorkspaceMetricsTest.java` 1. An eighth file is a caller this step missed: give it `controls` and `colors` and add it
to the commit.

- [ ] **Step 5: Run the architecture gate**

Run: `./gradlew verifyUiArchitecture verifyApplicationArchitecture` (root-project tasks, from `gradle/ui-architecture.gradle.kts` and
`gradle/application-architecture.gradle.kts`; no project prefix, as `docs/ui-maintenance.md` runs them)
Expected: BUILD SUCCESSFUL (`kit` depends on `metrics` and `theme` only).

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/kit/ jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java \
  jasper-ui/src/test/java/dev/jasper/ui/kit/ jasper-ui/src/test/java/dev/jasper/ui/testsupport/FixtureKitsTest.java \
  jasper-ui/src/test/java/dev/jasper/ui/swing/KitContextTest.java jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java \
  jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceMetricsTest.java
git commit -F - <<'MSG'
Classic catalog: control variants, colour slots and the classic kit

Kit gains ControlVariants (spec 5.2's 14 control points) and ColorSlots (spec
5.7); Kit.CLASSIC is the classic base. The test fixtures gain the Enterprise
fixture kit, the complement kit and withControls for the covering array.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 7: Documentation, STATUS and the whole build

**Files:**
- Modify: `docs/ui-architecture.md:11-12`, `docs/ui-maintenance.md:46`, `jasper-ui/README.md:34-35` (the smallest accurate edits)
- Modify: `docs/STATUS.md` (a new entry at the top)
- Modify: this plan's status banner

**Interfaces:**
- Consumes: Tasks 1 to 6.
- Produces: nothing new.

- [ ] **Step 1: Prove the gallery unchanged**

Run from the worktree root:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
python3 -I - <<'PY'
"""The headless gallery is byte-identical to the base captured in Task 1 Step 1."""
import hashlib, pathlib, sys
def digests(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for theme in ("dark-1x", "light-1x"):
    base, now = digests(pathlib.Path("jasper-ui/build/plan4-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none")
sys.exit(1 if bad else 0)
PY
```

Expected: `changed: none`.

- [ ] **Step 2: Edit the documentation**

Read each document first and keep its voice. Make these edits (find the paragraph by its text; do not add sections):

- `docs/ui-architecture.md`, the package table (`:11-12`): the `metrics` row's ownership cell becomes "Logical geometry
  catalogs (`DesignMetrics`, `DEFAULT` and `CLASSIC`) and the 11 public `MetricTokens` a kit file tunes, type scales and
  faces, central typography/motion roles"; the `kit` row's becomes "The look: `Kit` names a metric catalog, type scale,
  faces, control variants (`ControlVariants`), colour slots (`ColorSlots`) and FlatLaf key set; `Kit.DEFAULT` is the
  default look and `Kit.CLASSIC` the classic base".
- `docs/ui-maintenance.md`: it names no frozen metric or type table; after the sentence ending "and `derived-roles.tsv`
  in the same commit." (`:45-46`) add: "A new metric role is appended to `DesignMetrics.Role` with a row in
  `compact-roles.tsv` and `classic-roles.tsv`; a change to the classic catalog, type scale or FlatLaf overlay edits
  `classic-roles.tsv`, `classic-type.tsv` or `ui-color-keys-classic.tsv` in the same commit."
- `jasper-ui/README.md` (`:34-35`): "a `Kit` (`Kit.DEFAULT`) of logical `DesignMetrics`, a `TypeScale` and a `FaceSet`"
  becomes "a `Kit` (`Kit.DEFAULT`, and the classic base `Kit.CLASSIC`) of logical `DesignMetrics`, a `TypeScale`, a
  `FaceSet`, `ControlVariants` and `ColorSlots`".

- [ ] **Step 3: Write the STATUS entry**

Insert a new entry at the top of `docs/STATUS.md`, after the `# Jasper — Status and Handoff` heading and its blank line,
in the style of the plan-1 and plan-2 entries. Fill the bracketed values from Step 4's results and the commit range
(`git log --oneline main..HEAD`):

```text
**Enterprise presentation, plan 4a: the classic catalog — 2026-10-16** (`claude/enterprise-classic-catalog`, worktree
`.claude/worktrees/enterprise-4`, `[first]..[last]`, then this documentation commit;
[plan](superpowers/plans/2026-10-16-enterprise-4a-classic-catalog.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). The values of the classic look, with no
pixel changed. Plan 2's hand-offs: guards measure a translucent slot laid over its surface (a slot that holds keeps its
alpha), `KitMatrixTest` counts a foreign colour only where it fills a 2 × 2 block, `custom(base)` tints and
`marked(base)` drops authored overrides, and the structural guard is pinned engaging on Latte's window border.
`DesignMetrics.Role` gains `ROW_HEIGHT` (the list, table and tree row floor) and `FIELD_RADIUS` (fields and FlatLaf's
`TextComponent.arc`) at today's values and 32 classic-only roles measured on the mocks; `MetricTokens` holds spec 5.4's
11 tokens and `DesignMetrics.with(MetricTokens)` moves each token's roles by its change from the base;
`DesignMetrics.CLASSIC` (`classic-roles.tsv`), `TypeScale.CLASSIC` (`classic-type.tsv`, no label tracking) and
`UiKeySet.CLASSIC` (`ui-color-keys-classic.tsv`) are frozen; `Kit` gains `ControlVariants` and `ColorSlots`, with
`Kit.CLASSIC`; the fixtures gain the Enterprise and complement kits. The headless gallery is byte-identical. Rulings
(plan, "Rulings"): row 4 is three plans (4a values, 4b form faces, 4c data faces, matrix and comparison); the
translucent guard; the area rule; the fixtures' overrides; plan 2's package-test repairs (`PanelHeaders`' dot,
the status bar's configuration text, notification counts) carried to the chrome-face plans 6 and 7, which give those
painters faces (the hand-off stays open); the measured classic-only roles; buttons two taller than
fields (24 and 22, every mock); tokens by delta, paddings by formula; the classic base tuned as Enterprise; the
calibrated type roles; `DESCRIPTION` kept at the spec's 11 although the New File Server mock draws 12 (a residual for
4c); the table and tree selection foregrounds. Deviation: the overlay also sets `Button.default.startBorderColor` and
`endBorderColor` to `accent.edge`, which FlatLaf reads before `Button.default.borderColor`. Residuals: a focused
classic button paints flat (4b, 4c); unfocused table and tree selections keep the terminal-selection wildcard (4c's
comparison). Plan 4b starts with `KitFaces`. `./gradlew check --continue`:
**[tests] tests, [failures] failures, [errors] errors, [skipped] skipped**. GUI checks for the user: none new (nothing
visible changed). Not merged, not pushed.
```

- [ ] **Step 4: Run the whole build and count**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Count the results:

```bash
python3 -I - <<'PY'
import pathlib, re
totals = [0, 0, 0, 0]
for path in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    head = path.read_text(encoding="utf-8", errors="replace")[:2000]
    suite = re.search(r"<testsuite\b[^>]*>", head)
    if not suite: continue
    for i, key in enumerate(("tests", "failures", "errors", "skipped")):
        m = re.search(r'\b%s="(\d+)"' % key, suite.group(0)); totals[i] += int(m.group(1)) if m else 0
print("tests=%d failures=%d errors=%d skipped=%d" % tuple(totals))
PY
```

Write the four numbers into the STATUS entry.

- [ ] **Step 5: Set this plan's status banner**

Replace the banner below this plan's title, keeping its deviations and residuals:

```text
> **Status (2026-10-16): executed on `claude/enterprise-classic-catalog` (`[first]..[last]`).** Deviations from this
> plan's text: [none, or each one].
```

- [ ] **Step 6: Commit**

```bash
git add docs/ui-architecture.md docs/ui-maintenance.md jasper-ui/README.md docs/STATUS.md docs/superpowers/plans/2026-10-16-enterprise-4a-classic-catalog.md
git commit -F - <<'MSG'
Classic catalog: documentation and status

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

## Execution notes (2026-10-16)

Pre-flight for `superpowers:subagent-driven-development`, run on `claude/enterprise-classic-catalog` at `5cd8c8c16`
(`main` `149cd8fb9` with 3a, 3b and 3d merged, plus the three plan-4 files). Every anchor, quoted text, path, signature
and line number above was re-read against that code, and the text above now carries the current ones. Evidence without
Gradle: every edit script and file of Tasks 1 to 6 was replayed in order on a `git archive` copy of `HEAD` (each found
its text the stated number of times); the patched jasper-ui (main, `testFixtures`, tests) compiled with the JBR's
`javac -Xlint:all`, `javadoc -Xdoclint:all -Xdoclint:-missing` reported nothing on its main sources, the patched
`StaticDefaultTest` compiled against it, and the AGENTS.md hygiene scan found no bad character; a JUnit launcher passed
119 jasper-ui tests (`NewRolesTest`, `MetricTokensTest`, `ClassicCatalogTest`, `ClassicTypeTest`, `DesignMetricsTest`,
`TypeScaleTest`, `TypographyTest`, `ControlVariantsTest`, `KitTest`, `GuardSurfacesTest`, `ClassicUiKeysTest`,
`UiColorKeysTest`, `KitFixturesTest`, `FixtureKitsTest`, `FlatLafInstallerKitTest`, `SlateCobaltInstallTest`,
`KitContextTest`, `TextFieldsTest`, `InteractiveRowsTest`, `TablesTest`, `TreesTest`, `KitMatrixTest`,
`KnownRepairsTest`, `DerivedColorTest`); and `KitGallery.main` for Slate Cobalt Dark and Light wrote 159 files per theme,
byte-identical between the unpatched and patched copies. Not run here: the jasper-app tests (`ThemeManagerTest`,
`HostedKitTest`, `WorkspaceMetricsTest`, `StaticDefaultTest`, `PaintedContrastTest`), the architecture gates and
`./gradlew check`; the executor's runs are the evidence for those.

### Shared files and interfaces

| Pair | What they share | Agree? | Ruling |
|---|---|---|---|
| Task 1 and Task 6 | `KitFixtures.java`: Task 1 rewrites `custom()` (`:30-40`) and `marked()` (`:210-216`); Task 6 replaces `kit(DesignMetrics)` (`:25-28`) and adds four methods after it | Yes: distinct anchors, either order applies | E3 |
| Task 1 and Tasks 2, 7 | The gallery base `jasper-ui/build/plan4-base` (Task 1 Step 1), compared in Task 2 Step 5 and Task 7 Step 1 | Yes | E9 |
| Task 2 and Task 3 | `DesignMetrics.java` (Task 2: enum tail `:125`, `contentPadding` `:160`; Task 3: the `DEFAULT` field `:21-22`, `declared()` `:132`, the `contentPadding` comment `:148`); `compact-roles.tsv`, which Task 3 reads to write `classic-roles.tsv`; `NewRolesTest.ADDED`, which `ClassicCatalogTest` reads | Yes, if Task 2 runs first (Task 3's Consumes says so) | E3 |
| Task 2 and Task 6 | `ThemeManagerTest.java` (Task 2 `:103`, Task 6 `:153-154`) | Yes | E3 |
| Task 3 and Task 4 | `StaticDefaultTest.CATALOGS`: Task 4's anchor is the text Task 3's script writes | No: Task 4 said it consumes nothing | E2 |
| Tasks 3, 4 and Task 6 | `StaticDefaultTest` (Task 6 edits `:42`, `:103`, `:170`, which Tasks 3 and 4 leave alone); `MetricTokens` and `DesignMetrics.CLASSIC` read by `KitFixtures.enterpriseTokens()` and `enterprise()` | Yes | E3 |
| Task 4 and Task 6 | `TypeScale.CLASSIC`, `withBody` keeping `labelTracking` (`KitTest.kitsCompareByValue` needs `DEFAULT.withBody(13)` equal to `DEFAULT`) | Yes | none |
| Task 5 and Task 6 | `UiKeySet.CLASSIC` in `Kit.CLASSIC` and `enterprise()` | Yes | none |
| Task 1 and Task 5 | `dev.jasper.ui.theme` tests: Task 5 Step 4 runs `dev.jasper.ui.theme.*`, Task 1's `GuardSurfacesTest` included | Yes | none |
| Task 2 and plan 3c (parallel) | `InteractiveRows.java`: 4a changes line 372 only; 3c's nearest anchor is `measureRows();` at `:382` | Yes: a three-way `git merge-file` of 4a's change with 3c's insertion there has no conflict | E1 |
| This plan and plans 3c, 3e (parallel) | `docs/STATUS.md`, `KitFixtures.java`, `compact-roles.tsv`, `migration.csv` | Yes: neither plan adds a metric role or edits `KitFixtures`; 4a adds no `migration.csv` row | E10 |
| This plan and plans 4b, 4c | 4b and 4c consume `Kit.controls()`, `Kit.colors()`, `ControlVariants.of`/`keys()`, `ColorSlots`, `KitFixtures.enterprise()`/`complement()`/`withControls`, `KitMatrixTest.areas` and the new roles | Yes: each is produced here under that name | none |

### Each task against itself

| Task | Finding | Ruling |
|---|---|---|
| 1 | Step 3's expected message was the third assertion's; the first to fail is the alpha check, and the compile failure means no test runs. Step 6's "the declared default pairs" are now 20 (3b's Notice and Properties pairs), all opaque | E4, E5 |
| 1 | `SurfaceContrast.flat` repeats `DerivedPalette`'s private `flat` (`:127-129`) | E6 |
| 2 | Anchors and counts hold (34 rows, 32 classic-only roles, `FlatLafInstallerKitTest` 4 then 5 tests). `FieldSurface`'s default radius role also serves `SecretFields` and `BreadcrumbControls` | E7 |
| 3 | Anchors hold; `MetricTokensTest` 6 and `ClassicCatalogTest` 2 pass on the replay. The `CATALOGS` field comment said "Default catalogs" | E8 |
| 4 | Consumes line (see the pair table); `ClassicTypeTest` 3 passes | E2 |
| 5 | Anchors hold; the `UiColorKeys` class comment said every key is "from a semantic role" | E8 |
| 6 | Anchors hold; the seven constructor-calling files are as listed; `ControlVariantsTest` 3, `KitTest` 4, `FixtureKitsTest` 2 pass on the replay | none |
| 7 | `docs/ui-maintenance.md` names no frozen metric or type table and has no "adding a metric role" paragraph; `docs/ui-architecture.md` describes the kit in its package table, not a plan-1 paragraph | E9 |
| Workspace | `git rebase main` is not allowed and not needed (the branch contains `149cd8fb9`); the 3c `cellNodes` check would print 0 | E1 |

### Rulings

- **E1. No rebase; check the base by ancestry; drop the 3c check; keep the `InteractiveRows` edit to its one line** —
  the branch already contains `main` `149cd8fb9`, rebasing is forbidden in this run, and 3c runs in parallel; 4a uses no
  3c or 3e API (none of `TableRow.cellNodes`, 3c's empty lines or 3e's copy), and 4a's change at `:372` merges cleanly
  with 3c's insertion at `:382` — cost if wrong: one manual hunk when 3c and 4a meet, or a compile error naming the 3c
  symbol.
- **E2. Task 4 runs after Task 3 and names Task 3's `StaticDefaultTest` text as consumed** — its script's anchor is the
  `CATALOGS` text Task 3 writes; order 1 to 7 already holds it — cost if wrong: Task 4's script stops on the missing
  anchor before writing anything.
- **E3. Tasks run in order 1 to 7, one commit each, as written** — every shared file's anchors are disjoint or ordered,
  and the replay applied them in that order — cost if wrong: a script stops on a missing anchor.
- **E4. Task 1 Step 3 expects only the compile failure; the translucent test's would-be message is the alpha check's**
  — the test checks `getAlpha()` before inequality — cost if wrong: an executor misreads a correct red step.
- **E5. Rulings 2 still leaves the default look unchanged after 3b** — 3b's twelve new declared pairs (Notice text, border
  and link on `info`/`warning`/`danger.ground`, Properties values on `surface.card`) and their production callers
  (`Notices`, `Buttons`, `PropertyLists`) paint opaque slots on opaque roles in every theme of the set (derived roles are
  opaque; the only translucent roles are `surface.scrim`, `border.subtle`, `text.onAccent.secondary` and
  `text.onSelection.secondary`); the replayed gallery is byte-identical — cost if wrong: Task 2 Step 5 reports the
  changed files.
- **E6. `SurfaceContrast.flat` is added beside `DerivedPalette`'s private `flat`, which stays** — 4a's edits stay in
  the files it names, and the two differ (`SurfaceContrast`'s makes a translucent `under` opaque first) — cost if wrong:
  two three-line helpers to fold together later.
- **E7. `FIELD_RADIUS` moves every `FieldSurface` that keeps its default radius role (text, secret and breadcrumb
  fields), and not the picker's search surface** — they are fields, as spec 5.4's `field_radius` names; under
  `Kit.DEFAULT` the value is today's — cost if wrong: 4b sets the breadcrumb surface's role to `CONTROL_RADIUS`.
- **E8. Two comments made inaccurate by the new values are corrected in the same scripts** — Task 3 renames the
  `StaticDefaultTest.CATALOGS` comment ("the defaults and the classic base") and Task 5 says the classic set also
  fills keys from derived roles (AGENTS.md: documentation stays accurate, smallest edit) — cost if wrong: none.
- **E9. Task 7's documentation edits target the text that exists** — the two `docs/ui-architecture.md` table cells,
  one sentence after `docs/ui-maintenance.md`'s derived-role sentence, and the `Kit` sentence of `jasper-ui/README.md`;
  the gallery base is 159 files per theme here — cost if wrong: wording only.
- **E10. Shared files are reconciled at merge, not here** — `docs/STATUS.md` gets 4a's entry at the top as Task 7 says;
  `compact-roles.tsv` gains 34 rows that 3c and 3e do not touch; `migration.csv` gains no row (no new candidate source
  under jasper-app, the SDK, the testkit or plugins) — cost if wrong: a textual conflict in STATUS at merge.
