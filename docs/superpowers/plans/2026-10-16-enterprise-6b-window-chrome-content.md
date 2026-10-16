# Enterprise presentation 6b: the window content Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Under the Enterprise look the window's content takes the Workstation and Credential Vault mocks: the classic
toolbar row (left-packed "+ New Session", "Split ▾", "Maximize", contributed items and toolbar views, then "Search
everywhere" with its keycap and "Settings…"), classic content-sized editor tabs, ruled status cells with the leading
"▶ Run…" item, and OVERLAY and ATTACHED_SHEET dialogs drawn inside their window as classic dialogs (still scrim, framed
card, header strip with swatch and title, footer band with the footnote); each surface is rendered headlessly under the
shipped kit, Plex and the Enterprise theme and compared with the mocks in Light and Dark.

**Architecture:** Plan 6a landed the window shell and split `WINDOW_CONTENT` off; this plan builds that group's four
strategy pairs at their owners and then lands it. `WorkspaceChrome.of` gains `HeaderComposition` (`UnifiedHeader`,
`ClassicHeader`), `TabStrip` (`WindowTabs` as the equal tabs, `ClassicTabs`), `StatusCells` (`SlashSegments`,
`RuledCells`) and the run item; `WindowToolbar` learns a leading pack. `dev.jasper.ui.swing.ClassicDialog` is the classic
in-window dialog, hosted by `WindowOverlay` beside the kit's `ModalOverlay`; `AuxiliaryWindows` is the one reader of
`DialogMapping` and marks every surface it makes, so plugin and app dialogs alike take the classic path; `KitView`
gains two host flags (`hostTitled`, `dialogBand`) that the form reads. The Enterprise theme takes the mocks' tab strip,
tab indicator, scrim and divider (one new override key). The default look is unchanged.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library and Pillow for the edit scripts and measurements (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 5.2, 6.4, 7.1, 7.2, 7.6, 9.1, 10.3, 10.5, 10.6, 10.8, 14.8 for the Vault dialogs, 16.1.4, 16.1.7, 16.2,
17 and row 6 of 19.1). This is the second of row 6's two plans; [6a](2026-10-16-enterprise-6a-window-chrome-shell.md)
executes first on the same branch and its names are used here. The reference mocks are
[`docs/design/enterprise/reference/`](../../design/enterprise/reference/) (2× PNGs; the mock wins on layout,
DESIGN_LANGUAGE.md on intent; names, counts, hosts and the Vault forms' content are fixtures; the Vault compositions are
plan 10's).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-6`, branch
`claude/enterprise-window-chrome`, after plan 6a's last commit. Run every command from the worktree root. Line numbers
are from `deafed424` and move after the earlier plans; every edit quotes the exact text it replaces and stops when that
text is not found the stated number of times; re-find moved text, never edit around it, and record any re-quoted anchor
in the status banner. Where a step replaces a whole file it first checks that no earlier plan changed it.

## Before you start

```bash
git log --format=%s | grep -c 'Window chrome: the shell.s documentation, scan and status'      # 6a: 1
grep -c 'WINDOW_CONTENT' jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java  # 6a: at least 1
ls jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceChrome.java jasper-app/src/test/java/dev/jasper/app/testsupport/MockProfile.java
grep -c 'DIALOG_FOOTER_VERTICAL(8)' jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java   # 6a Task 3: 1
```

If 6a is not on the branch, execute it first. Plan 6a's "Earlier plans' names" table applies here too.
Besides it, this plan uses `AuxiliarySurface.swatch()` (a `dev.jasper.ui.model.Nodes.Hue`, NONE when unset) and
`DialogSpec.swatch` (3d Task 3), `tools/visual/check_pairs.py` with the `pairs.json` fields `render_crop`,
`min_agreement` and `colours` (4c, its Rulings 21; Rulings 22 left the Unlock Vault pair to this plan), the classic
prompt face (4b) and the form footnote (3b Task 8).

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop`.
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache; `:jasper-ui:gallery` and headless JUnit renders may be run.
- Work only in the worktree above on `claude/enterprise-window-chrome`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: the default look stays byte-identical. Every PNG and JSON the headless gallery writes for Slate Cobalt Dark and Light is byte-identical to the base (Task 9 proves it), and every reference pair and calibration suite stays green unchanged. `UnifiedHeader`, `WindowTabs`, `SlashSegments` and `ModalOverlay` keep today's code paths (moved, not rewritten), the new `KitView` flags and `WindowToolbar`'s leading pack are off unless a classic strategy sets them, and the Enterprise theme's edits touch no built-in theme. The only existing renders that change are plan 5b's Enterprise renders under `jasper-app/build/enterprise-kit/` (four theme values, Task 1) and `derived-roles-enterprise.tsv`'s `border.divider` rows.
- Spec 10.3: the classic header is a toolbar row of left-packed buttons in separated groups — "+ New Session" (NEW_TAB), "Split ▾" (the Split dropdown), "Maximize" (ZOOM_PANE) — then contributed items and views, then, trailing, the title search field reading "Search everywhere" with the COMMAND_PALETTE keycap, and "Settings…"; `icons_and_labels` gives label-first text buttons with glyphs only where the mock has them, `icons` gives icon buttons, `hidden` gives no row; `window.toolbar_height` is the row's height. New Window and Find stay in the File and Edit menus.
- Spec 10.5: `ClassicTabs` are always visible and content-sized, with a status dot, a persistent ×, a "+" after the last tab, the active tab on `surface.card` with a 2 px `tab.indicator` underline, 1 px dividers and no motion, driving the same `TerminalDeck` callbacks.
- Spec 10.6: `RuledCells` divides cells by 1 px `border.divider` rules: the session cell and the folder on the left; plugin items, the grid size, "UTF-8" and the configuration warning (a `warning.ground` cell with "▲") on the right; with the run item, either layout leads with "▶ Run…" bound to COMMAND_PALETTE, shown as an active toggle on `surface.control.selected` while the palette is open.
- Spec 10.8: `DialogMapping.CLASSIC` draws OVERLAY and ATTACHED_SHEET dialogs inside their owner window with the same modality: a card with the catalog's dialog radius on the dialog surface slot, a 1 px `border.hover` frame and the catalog's popup shadow, over a still scrim covering the window below its title bar; a 26 px header strip on `surface.header.active` above a `border.window` rule with the swatch and the title in the semibold UI face (the form does not repeat the title); the footer on `surface.footer` under a `border.window` rule, the footnote leading and the actions trailing; OVERLAY centred, ATTACHED_SHEET centred horizontally and joined under the title bar with no top frame edge; no fade. A dialog whose owner has no window opens as a native dialog; progress overlays stay as they are; with `windowTitled` a FULL form does not repeat the native title.
- Strategies at the owner (spec 3.3, 16.1.7): `WorkspaceChrome.of` reads `WorkspaceLook`'s header, tabs, status and run item; `AuxiliaryWindows` is the only reader of `DialogMapping.Kind`; no renderer branches on a look (the classic dialog and strategies are chosen once). `VariantReadTest` enforces it (Task 9).
- Colours are theme roles or derived roles, never literals (`ColourLiteralScanTest`); every text, indicator and boundary a new painter draws goes through its `SurfaceContrast` guard.
- Javadoc doclint, `migration.csv` rows, the smallest documentation edits and source hygiene as in plan 6a's Global Constraints (dispositions begin "New in Enterprise presentation Plan 6b Task N").

## Review Focus

1. A user narrows a classic window: toolbar labels give way first, then lower-priority buttons move into the "⋮" menu,
   a toolbar view hides, the search field shrinks and then collapses, and "Settings…" stays to the end; widening brings
   each back. Task 3: `ClassicHeaderTest.aNarrowWindowGivesWayInOrder`.
2. Many tabs, or a tab whose title is a long command line: classic tabs shrink their labels with an ellipsis, every ×
   stays clickable, the "+" stays visible, and one tab still shows the strip. Task 4:
   `ClassicTabsTest.manyTabsShrinkTheirLabelsAndKeepTheirClose` and `oneTabStillShowsTheStrip`.
3. A configuration with an error, then a warning, then none: the ruled bar shows a danger cell, then a warning cell,
   then no configuration cell, and the cell still opens the details. Task 5:
   `RuledCellsTest.theConfigurationCellShowsOnlyWhileThereIsAProblem`.
4. Two classic dialogs over one window (Unlock Vault while Add Login is open), Escape, and the owner window closing: the
   newest takes the keys and focus, Escape reaches its Cancel, closing hands the keys back to the one below, and the
   window is unblocked once both have closed. Task 7: `ClassicWindowOverlayTest.classicDialogsStackAndHandTheKeysBack`.
5. A plugin asks for an OVERLAY or ATTACHED_SHEET dialog over a window that is not showing (minimised during the
   request, or the anchor's window gone): it opens as a native dialog instead of failing. Task 7:
   `ClassicPlacementTest.aDialogWithoutAShowingWindowIsNative`.

## Verified facts

At `deafed424` unless marked; facts about plans 3b to 6a cite the plan.

1. `WindowChrome`'s constructor builds `WindowToolbar`, `TitleSearchField`, `WindowHeader`, marks the header a
   `ThemeZone.titleBar` zone (`WindowChrome.java:52-62`) and calls `buildToolbar` (`:102`): New Tab, New Window, a rule,
   Split, Zoom, the plugin rule, a rule, Find, Settings, packed right (`:109-120`). `ReferenceButton` and
   `ToolbarSeparator` are its nested classes (`:330-385`); `setToolbarMode` hides only the toolbar (`:405-409`);
   `refreshTheme` repaints header, search and toolbar (`:410-418`). `status` is a field initializer (`:38`).
2. After plan 3d, `WindowToolbar` places buttons and contributed views packed right, moves lower-priority buttons with a
   menu supplier into the overflow menu and hides views (3d Task 4, the whole file); `buttonSide(height)` is the height
   minus `TOOLBAR_BUTTON_TRIM`. `ThemeZone.titleBar(header)` makes `ThemeZone.theme` return `TitleBarTheme` for the
   header's descendants (`ThemeZone.java:25-35`).
3. `WindowHeader` centres `TitleSearchField` on the window and lays the toolbar to its right (`WindowHeader.java:56-97`);
   `TitleSearchField` paints through `ChromePainter.search` (`TitleSearchField.java:101-104`).
4. `WindowContent` holds `WindowTabs windowTabs` (`WindowContent.java:61`), builds it (`:247`), mounts it above the deck
   (`:252`), applies the workspace theme to it (`:1226-1227`), refreshes it (`:889`, `:1285`), passes animations
   (`:1365`) and activity (`:1385`) and closes it (`:1419`); 41 test lines call `windowTabs()` and use `WindowTabs`
   members. `WindowTabs` paints through `ChromePainter.tabStrip` (`WindowTabs.java:140-149`) and drives
   `owner.selectTab`, `closeTab`, `tabMenu`, `reorderTab` (`:257-298`).
5. `WindowStatusBar` lays out the slash segments in `doLayout` (`WindowStatusBar.java:252-276`), paints the running dot
   (`:277-281`), keeps its data in private fields (`:22-40`), and spaces contributed items with `STATUS_GAP` struts in
   `replaceStructure` (`:172-180`); its configuration text names "Config warnings (line N)" (`:104-114`).
6. `WindowOverlay` wraps one `ModalOverlay` per dialog, stacks overlays per root, routes keys to the newest and hands
   focus back (`WindowOverlay.java:19-161`); `NativeShells.overlay` builds it and blocks a modal show on a secondary loop
   (`NativeShells.java:99-122`). `ModalOverlay` paints the scrim from `surface.scrim`, the dialog shadow and a rounded card
   (`ModalOverlay.java:256-283`, `:290-360`).
7. `AuxiliaryWindows` makes every auxiliary surface and tracks it (`AuxiliaryWindows.java:35-137`); `HostedUi.dialog`
   routes OVERLAY to `windows.modalOverlay` and the rest to `windows.dialog`, then marks ATTACHED_SHEET
   (`HostedUi.java:720-750`); plan 3d sets the swatch there. `AuxiliarySurface.setContent` turns a sheet's kit view's
   surface off (`AuxiliarySurface.java:119-127`).
8. `Forms.FormControl.refresh` shows the title label only for a SHEET form without a hero (`Forms.java:190-191`) and
   pads the footer (`:223-227`); `Forms.Footer.paintComponent` paints only the WINDOW footer band (`:324-336`); plan 3b
   adds the footnote at the footer's leading edge (3b Task 8). `KitView.titleStrip` is the precedent for an app-only
   host flag (`KitView.java:319-330`), and `refresh()` re-runs the reconciler (`:363-366`).
9. Plan 5b's Enterprise theme sets `surface.tabstrip` `#DDDFE2`/`#474A4F`, `tab.indicator` `#2563C9`/`#5B9BF5` and
   `surface.scrim` `#1B1F2740`/`#0A0C108C` by its Rulings 7 (5b Task 3); `EnterpriseThemeTest.NEUTRAL` lists
   `surface.tabstrip`; `DerivedRolesTableTest` freezes the theme's derived roles in `derived-roles-enterprise.tsv`,
   regenerated from `jasper-ui/build/derived-roles/derived-roles-enterprise.tsv` (5b Task 4).
10. `DerivedColor.BORDER_DIVIDER` is not overridable (`DerivedColor.java:25`); `JasperTheme.DERIVED_OVERRIDES` is built
    from the overridable roles (`JasperTheme.java:54-55`); `DerivedColorTest` pins the eight (`DerivedColorTest.java:58-60`)
    and `JasperThemeTest` their count and uses `border.divider` as its example of a key that is not one
    (`JasperThemeTest.java:238`, `:246-247`); `docs/themes.md:135-137` lists them.
11. `WindowCommandPalette.isOpen()` (`WindowCommandPalette.java:169`); the palette opens through the COMMAND_PALETTE
    action (`WindowChrome.java:57-59`).

### Mock measurements

Measured with Pillow at the mocks' 2× pixels and halved; content starts at (2, 60) in the Workstation and Dark Mode
mocks. Light value first, Dark second where the Dark Mode mock shows it.

| Surface | Measured | Logical target | Role or rule |
|---|---|---|---|
| Toolbar row | y 110-177 `#ECEDEF`/`#3C3F44`, rule 178-179 `#C4C7CC`/`#2A2C30` | 34 + 1 px rule | `surface.app`, `border.window` |
| Toolbar buttons | Connect y 120-167, combo 122-165 | 24 tall (fields 22), centred | `BUTTON_HEIGHT`, `FIELD_HEIGHT` |
| First control | "+" ink x 38-51, label 66-199 | toolbar padding 8 + button padding 8; "+" 7 × 8, 6-7 to the label | `TOOLBAR_PADDING`, `CHROME_BUTTON_PADDING`, `CHROME_GLYPH`, `CHROME_GLYPH_GAP` |
| Buttons apart | "New Session" end 199, "Detach" 247 | 24 between labels: 8 + 8 + gap 8 | `CHROME_BUTTON_GAP` |
| Separators | x 356, 646; y 126-161; `#C4C7CC`/`#2A2C30` | 1 × 18, 8 from top and bottom, 10 each side | `CHROME_SEPARATOR_INSET`, `CHROME_SEPARATOR_GAP`, `border.window` |
| Split caret | ▼ x 454-464 | 5 wide, 7.5 after the label | `CHROME_CARET`, `CHROME_CARET_GAP` |
| Search field | x 2116-2715, y 122-165; `#FFFFFF`, edge `#B4B8BF` | 300 × 22, centred | `TOOLBAR_SEARCH_WIDTH`, `surface.input`, `border.control` |
| Placeholder | ink x 2134, `#7A808A` | 8 in | `TOOLBAR_SEARCH_PADDING`, `text.placeholder` |
| Keycap | x 2638-2697, y 128-159; fill `#F4F5F7`, edge `#D3D6DA`, text `#7A808A` | 16 tall, 3 from the top, 8 from the right | `HINT_KEYCAP_HEIGHT`, `HINT_KEYCAP_PADDING`; Rulings 6 |
| Settings… | ink 2742-2846 | 8 padding; 6 after the field; 8 (±2) from the content edge | `CHROME_TRAILING_GAP`, `TOOLBAR_PADDING` |
| Tab strip | y 180-235 `#E8E9EB`/`#3A3D42`, rule 236-237 `#C4C7CC`/`#2A2C30` | 28 + 1 px rule | `surface.tabstrip` (Task 1), `border.window` |
| A tab | 650-839: dot 674-687, label 706-785, × 804-819, divider 840 | 12, dot 7, 9, label, 9, × 7-8, 10, 1 px divider | `CLASSIC_TAB_*`, `border.window` |
| Active tab | 842-1019 `#FFFFFF`/`#2E3135`, underline y 232-235 `#2F65CA`/`#3D74D6` | `surface.card`, 2 px at the bottom above the rule | `tab.indicator` (Task 1), `TAB_INDICATOR` |
| Tab text, ×, + | `#1E1F22`; × `#7A808A`; + `#4A4F57` at 11 after the last divider | label `text.primary`; × `text.placeholder`; + `text.secondary` (measured residual) | |
| Status bar | y 1758-1801 `#ECEDEF`/`#3C3F44` under the rule at 1756 | 22 tall (Jasper keeps `window.status_bar_height`, 24) | `surface.app`, `border.window` |
| Status dividers | x 108, 164, 1966, 2140, 2322, 2470, 2574; `#D3D6DA`/`#4A4D53` | 1 px, full height | `border.divider` (Dark by Task 1's override) |
| Session cell | dot x 22-35, y 1774-1787 `#2E9E4F`/`#4CB86A`; "Mac" 50-87 `#3A3D42` | dot 7 at 10, 7 to the label | `STATUS_CELL_PADDING`, `STATUS_CELL_DOT_GAP`, `text.strong.secondary` |
| Warning cell | 2576-2881 `#FBF1DA`/`#4A4128`, text `#8A5A00` | to the right edge | `warning.ground`, `status.warning` |
| Run item (Run Dialog mocks) | 2-121; ▶ 24-37; "Run…" 47-99; ink `#1F4C9E`; ground `#D9DFEA` while open | 60 wide; ▶ 7 at 11; 5 to the label | `RUN_GLYPH_GAP`, `accent.edge`, `surface.control.selected` (Rulings 9) |
| Scrim | white `#C9CACC`; `#ECEDEF` reads `#BABBBC` | black at 21 % | `surface.scrim` (Task 1) |
| Dialog frame | Unlock x 1104-1907, y 782-1149 (in a 3012 × 1932 mock whose window starts at x 64, y 64); `#8E949C`; corner about 4 | 1 px, radius 4 | `border.hover`, `DIALOG_RADIUS` |
| Header strip | y 784-835 `#D9DFEA`, rule 836-837 `#C4C7CC` | 26 + 1 px rule | `DIALOG_HEADER_HEIGHT`, `surface.header.active` |
| Title, swatch | "Unlock Vault" ink at 1127 (frame 1104); Add Login swatch 402-417 (frame 380), title 432 | title 10 in (+1 bearing); swatch 8 at 10, title 7 after | `DIALOG_HEADER_INSET`, `DIALOG_SWATCH`, `DIALOG_SWATCH_GAP` |
| Footer band | rule y 1066-1067, band 1068-1147 `#E6E7EA` | 1 px `border.window`, 40 tall: buttons 24 + 8 + 8 | `surface.footer`, `DIALOG_FOOTER_VERTICAL` |
| Placement | Unlock's centre 1505.5, 965.5 = the window's centre with its title bar | centred on the whole window | Rulings 10 |
| Sheet | Add Login strip from y 58, right under the 28 px title bar; x 380-1263 in an 1644-wide mock | no top frame, centred | Rulings 10 |

## Rulings

Decisions where the spec is silent or a literal reading cannot be built; record each in `docs/STATUS.md` (Task 9) and in
this plan's status banner. Ruling 3 departs from spec 7.2's text and needs the user's approval or a spec amendment line
before this plan merges; the banner and the STATUS entry record it as awaiting that approval.

1. **The window content lands at the end.** Tasks 2 to 7 build and test the strategies from classic projections made
   in the tests; Task 8 adds `WINDOW_CONTENT` to `Presentation.LANDED`, so every intermediate build runs spec 9.1's
   fallback header row — cost if wrong: none.
2. **The mocks' tab strip, tab indicator, scrim and dividers are Enterprise theme values.** `enterprise.json` sets
   Light `surface.tabstrip` `#E8E9EB`, `tab.indicator` `#2F65CA`, `surface.scrim` `#00000036` and `border.divider`
   `#D3D6DA`; Dark `surface.tabstrip` `#3A3D42`, `tab.indicator` `#3D74D6` and `border.divider` `#4A4D53`. Spec 7.6 names
   the mock's tab strip a target for these renders; plan 5b's Rulings 7 had given them Slate Cobalt's values — cost if
   wrong: four theme values.
3. **Deviation (spec 7.2; awaiting the user's approval): `border.divider` becomes a ninth overridable derived role.** The
   recipe `mix(border.window′, surface.card, 73 %)` gives Enterprise Dark `#2B2D31`, darker than its surfaces, while the
   Dark Mode mock draws every menu-row and status divider lighter (`#4A4D53`); no recipe change keeps the frozen built-in
   tables. Only themes that list the key change — cost if wrong: one flag, one key and two documents.
4. **`WindowToolbar`'s leading pack is a separate layout branch** with the classic spacing roles; today's trailing
   layout is not touched, so the default row cannot move — cost if wrong: some duplicated layout code.
5. **The classic header's trailing group is its own `WindowToolbar`** holding "Settings…" with no leading padding; the
   search field is 300 wide and shrinks before it collapses (below `TITLE_SEARCH_MIN_WIDTH`'s half it hides); `hidden`
   hides the header row; `icons` shows the buttons' icons (`AppIcons` "square-plus", "columns-2", "maximize",
   "settings"); a hovered button shows `surface.control.hover` and a 1 px `border.hover` frame, a pressed one
   `surface.control.selected` (spec 6.4's toolbar ghost) — cost if wrong: one class.
6. **The search field's shortcut hint is a light keycap**: `surface.tableHeader` face, `border.divider` edges, no
   thicker bottom, the data face at caption size in `text.placeholder` (the mock's hint); spec 5.2's keymap keycap stays
   Keymap's (plan 9) — cost if wrong: two roles.
7. **Classic tabs keep `window.tab_height`** as the strip's height with its rule (30 = 29 + 1, the mock's 28 + 1 is a
   recorded residual); a hosted document's tab has no dot; drag reorder works as in the equal tabs; too many tabs
   shrink every label towards `CLASSIC_TAB_MIN_LABEL`, ellipsised, then the strip clips — cost if wrong: 1 px.
8. **Ruled cells**: the bar is `surface.app` under a 1 px `border.window` rule (the rule above the mocks' status bar;
   plan 7's bottom strip draws only its own top rule); it keeps `window.status_bar_height` (spec 10.6); the
   configuration cell shows only while there is a warning or an error ("▲ " and today's text on `warning.ground` or
   `danger.ground`); the session cell names a local pane by its shell ("zsh", as Credential Vault 1 shows it) and a
   remote one by its saved identity or host; the folder cell is `WindowTitle.directory`; the grid size is in the data
   face; contributed items are cells, their struts the cell rules — cost if wrong: one class.
9. **The run item** draws "▶ Run…" in `accent.edge` through the text guard and, while the palette is open, on
   `surface.control.selected` (spec 10.6); the Run Dialog mocks' `#D9DFEA` ground and `#1F4C9E` ink are recorded
   residuals. It runs COMMAND_PALETTE (the palette's current view; plan 8 makes that the Run window) and repaints when
   the palette opens or closes — cost if wrong: two roles.
10. **`ClassicDialog` is a jasper-ui component** (not a face, so it may read the kit's dialog surface slot) with
    `DIALOG_RADIUS` 4 (the mocks'; the classic `WINDOW_RADIUS` is 0), the catalog's popover shadow, centred on the whole
    window as the mock centres Unlock Vault (the host passes the frame's top inset), and a sheet at the top of the
    layered pane (under the title bar; it covers an auxiliary window's File row while open) — cost if wrong: a few
    pixels of placement.
11. **`AuxiliaryWindows` is the one reader of `DialogMapping`**: it marks every surface it makes (`classic`, and
    `hostTitled` for dialogs when the native bar titles them), so plugin and app dialogs share the classic path and
    `HostedUi.dialog` keeps its routing. A classic OVERLAY or ATTACHED_SHEET whose owner window is not showing opens as
    a native centred dialog — cost if wrong: app dialogs that should stay native would need a flag.
12. **`KitView.hostTitled` hides the form's title label and its hero**, after every reconcile; `dialogBand` paints the
    footer on `surface.footer` under a `border.window` rule with `DIALOG_FOOTER_VERTICAL` and `FORM_SIDES` padding —
    cost if wrong: a hero that carried more than the title would need its own flag.
13. **The Vault dialogs' bodies are plan 10's.** The comparisons with Credential Vault 1, 4 and 5 check the dialog chrome
    (scrim, frame, strip, swatch, title, footer band, placement, sheet join) around fixture forms — cost if wrong: plan 10
    re-pairs the bodies.
14. **A swatch is its hue's theme role.** The comparisons with Credential Vault 4 and 5 check the swatch's place and
    size against the mocks and its colour against the Enterprise theme's `hue.blue` and `hue.green`; the mocks'
    `#2F65CA` and `#2E9E4F` are printed beside the theme's values as a residual, not asserted — cost if wrong: two theme
    values in plan 10.
15. **The Workstation render leads its status bar with the run item.** The Workstation and Dark Mode mocks predate the
    Run window and show no "▶ Run…"; spec 10.6 and the Run Dialog mocks do. The comparison measures the run item
    against the Run Dialog mock and the right-hand cells from the Workstation mocks' right edge; the left cells' text
    is fixture data (`RuledCellsTest` pins their geometry) — cost if wrong: none.

## File structure

- `plugins/enterprise/src/main/resources/themes/enterprise.json`, `jasper-ui/src/main/java/dev/jasper/ui/theme/DerivedColor.java` (Task 1).
- `jasper-app/src/main/java/dev/jasper/app/workspace/`: `WindowToolbar.java` (leading pack); new `HeaderComposition.java`,
  `UnifiedHeader.java`, `ClassicHeader.java`, `ClassicToolbarButton.java`; `WindowHeader.java`, `TitleSearchField.java`,
  `WindowChrome.java`; new `TabStrip.java`, `ClassicTabs.java`; `WindowTabs.java`; new `StatusCells.java`,
  `SlashSegments.java`, `RuledCells.java`, `RunItem.java`; `WindowStatusBar.java`; `WorkspaceChrome.java`;
  `WindowContent.java`, `WindowCommandPalette.java`; `platform/ThemeZone.java` (`chrome`).
- `jasper-ui/src/main/java/dev/jasper/ui/swing/`: new `ClassicDialog.java`; `KitView.java`, `Forms.java`.
- `jasper-app/src/main/java/dev/jasper/app/windows/`: `WindowOverlay.java`, `NativeShells.java`, `AuxiliarySurface.java`, `AuxiliaryWindows.java`.
- `jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java` (`LANDED`), `application/JasperApplication.java`.
- Tests (new): `ui/theme` edits; `workspace/WindowToolbarLeadingTest`, `ClassicHeaderTest`, `ClassicTabsTest`,
  `RuledCellsTest`; `ui/swing/ClassicDialogTest`, `ui/swing/HostedTitleTest`; `windows/ClassicWindowOverlayTest`,
  `ClassicPlacementTest`; `workspace/EnterpriseWorkstationMockTest`, `windows/EnterpriseDialogMockTest`.

---

### Task 1: The Enterprise theme's chrome targets

The mocks' tab strip, tab indicator, scrim and dividers become Enterprise theme values (Rulings 2), and `border.divider`
becomes overridable so Enterprise Dark can carry the mock's divider (Rulings 3).

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/DerivedColor.java:25`
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedColorTest.java:52-60`, `JasperThemeTest.java:238-247`
- Modify: `plugins/enterprise/src/main/resources/themes/enterprise.json` (seven values)
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/EnterpriseThemeTest.java` (5b; `NEUTRAL`, `DARK_OVERRIDES`, one test)
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles-enterprise.tsv` (regenerated), `docs/themes.md:135-137`

**Interfaces:**
- Consumes: plan 2's `DerivedColor`, `JasperTheme.DERIVED_OVERRIDES`; plan 5b's theme and its tests.
- Produces: `DerivedColor.BORDER_DIVIDER.overridable()` true (nine override keys); the Enterprise values above.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
"""Plan 6b Task 1 step 1: nine override keys, and the Enterprise theme's chrome targets."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
U = "jasper-ui/src/test/java/dev/jasper/ui/theme/"
edit(U + "DerivedColorTest.java", [
    ('''    @Test void theRolesAreSpecSevenOnesAndTheEightStarredOnesAreOverridable() {''',
     '''    @Test void theRolesAreSpecSevenOnesAndTheStarredOnesAndTheDividerAreOverridable() {''', 1),
    ('''            "warning.ground", "warning.border");''', '''            "warning.ground", "warning.border", "border.divider");''', 1)])
edit(U + "JasperThemeTest.java", [
    ('''        assertThat(JasperTheme.DERIVED_OVERRIDES).hasSize(8).allSatisfy((key, role) -> {''',
     '''        assertThat(JasperTheme.DERIVED_OVERRIDES).hasSize(9).allSatisfy((key, role) -> {''', 1),
    ('''    @Test void anOverrideKeyTakesAnOpaqueColourAndOnlyTheEightAreKeys() throws Exception {''',
     '''    @Test void anOverrideKeyTakesAnOpaqueColourAndOnlyTheNineAreKeys() throws Exception {''', 1),
    ('''        assertThat(failure(json.replace("\\"surface.tableHeader\\"", "\\"border.divider\\""))).as("a derived role without an override key")
            .hasMessageContaining("ui.light: Unknown colour \\"border.divider\\"");''',
     '''        assertThat(failure(json.replace("\\"surface.tableHeader\\"", "\\"border.row\\""))).as("a derived role without an override key")
            .hasMessageContaining("ui.light: Unknown colour \\"border.row\\"");''', 1)])
T = "jasper-app/src/test/java/dev/jasper/app/plugins/EnterpriseThemeTest.java"
edit(T, [
    ('''"surface.control.selected", "surface.menu", "surface.tabstrip", "surface.emptyTile",''',
     '''"surface.control.selected", "surface.menu", "surface.emptyTile",''', 1),
    ('''        DerivedColor.WARNING_GROUND, "#4A4128", DerivedColor.WARNING_BORDER, "#6B5A30");''',
     '''        DerivedColor.WARNING_GROUND, "#4A4128", DerivedColor.WARNING_BORDER, "#6B5A30", DerivedColor.BORDER_DIVIDER, "#4A4D53");''', 1),
    ('''    /** Verified fact 12: where the mocks paint a theme role, at their 2x pixels. */''',
     '''    /** Plan 6b Rulings 2: the chrome roles the Workstation and Dark Mode mocks paint, as the theme's values. */
    @Test void theChromeTargetsAreTheMocks() throws Exception {
        var theme = theme();
        assertThat(ColorMath.hex(role(theme, false, "surface.tabstrip"))).isEqualToIgnoringCase("#E8E9EB");
        assertThat(ColorMath.hex(role(theme, true, "surface.tabstrip"))).isEqualToIgnoringCase("#3A3D42");
        assertThat(ColorMath.hex(role(theme, false, "tab.indicator"))).isEqualToIgnoringCase("#2F65CA");
        assertThat(ColorMath.hex(role(theme, true, "tab.indicator"))).isEqualToIgnoringCase("#3D74D6");
        var scrim = role(theme, false, "surface.scrim");
        assertThat(scrim.getRGB() & 0xFFFFFF).as("black").isZero();
        assertThat(scrim.getAlpha()).as("21 %%, so white reads #C9CACC").isEqualTo(0x36);
        assertThat(ColorMath.hex(theme.generated(false).derivedOverrides().get(DerivedColor.BORDER_DIVIDER))).isEqualToIgnoringCase("#D3D6DA");
    }

    /** Verified fact 12: where the mocks paint a theme role, at their 2x pixels. */''', 1)])
PY
```

The `EnterpriseThemeTest` anchors are plan 5b Task 3's text (its `NEUTRAL` list and `DARK_OVERRIDES` map); `role`,
`theme()` and `ColorMath` are that class's own. If its `DARK_OVERRIDES` is built with `Map.of`, the eighth pair still
fits (`Map.of` takes up to ten).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.DerivedColorTest' --tests 'dev.jasper.ui.theme.JasperThemeTest' :jasper-app:test --tests 'dev.jasper.app.plugins.EnterpriseThemeTest' --continue`
Expected: FAIL: `DerivedColorTest` lacks "border.divider" among the overridable keys, `JasperThemeTest` expects 9 and
finds 8, `EnterpriseThemeTest.theChromeTargetsAreTheMocks` finds `#DDDFE2` and `theUnnamedRolesFollowTheRule` is
unchanged (it passes).

- [ ] **Step 3: The override key and the theme values**

```bash
python3 -I - <<'PY'
"""Plan 6b Task 1 step 3: border.divider overridable; the Enterprise theme's chrome targets."""
import json, pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/theme/DerivedColor.java", [
    ('''    BORDER_DIVIDER("border.divider", false),''', '''    BORDER_DIVIDER("border.divider", true),''', 1)])
F = "plugins/enterprise/src/main/resources/themes/enterprise.json"
edit(F, [
    ('''"surface.tabstrip": "#DDDFE2"''', '''"surface.tabstrip": "#E8E9EB"''', 1),
    ('''"surface.tabstrip": "#474A4F"''', '''"surface.tabstrip": "#3A3D42"''', 1),
    ('''"surface.scrim": "#1B1F2740"''', '''"surface.scrim": "#00000036"''', 1),
    ('''"tab.indicator": "#2563C9"''', '''"tab.indicator": "#2F65CA"''', 1),
    ('''"tab.indicator": "#5B9BF5"''', '''"tab.indicator": "#3D74D6"''', 1),
    ('''      "warning.border": "#E6CF95"''', '''      "warning.border": "#E6CF95", "border.divider": "#D3D6DA"''', 1),
    ('''"warning.ground": "#4A4128", "warning.border": "#6B5A30"''', '''"warning.ground": "#4A4128", "warning.border": "#6B5A30", "border.divider": "#4A4D53"''', 1)])
json.loads(pathlib.Path(F).read_text(encoding="utf-8"))  # still JSON
edit("docs/themes.md", [
    ('''- A variant may also list eight derived roles, each as `#RRGGBB`: `surface.header`, `surface.header.active`,
  `surface.stripe`, `surface.tableHeader`, `surface.zebra`, `surface.rowHover`, `warning.ground` and
  `warning.border`.''',
     '''- A variant may also list nine derived roles, each as `#RRGGBB`: `surface.header`, `surface.header.active`,
  `surface.stripe`, `surface.tableHeader`, `surface.zebra`, `surface.rowHover`, `warning.ground`, `warning.border` and
  `border.divider`.''', 1)])
PY
```

If `docs/themes.md`'s sentence was reworded by an earlier plan, change only its count and add `border.divider` to its
list.

- [ ] **Step 4: Regenerate the frozen Enterprise table**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.DerivedRolesTableTest'`
Expected: FAIL in `theEnterpriseVariantsDerivedRolesAreFrozen` only (both variants' `border.divider` rows). Then:

```bash
python3 -I - <<'PY'
import pathlib
source = pathlib.Path("jasper-ui/build/derived-roles/derived-roles-enterprise.tsv")
target = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles-enterprise.tsv")
old = {tuple(l.split("\t")[:3]): l for l in target.read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")}
new = {tuple(l.split("\t")[:3]): l for l in source.read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")}
changed = sorted(key for key in new if old.get(key) != new[key])
if changed != [("enterprise", "dark", "border.divider"), ("enterprise", "light", "border.divider")]:
    raise SystemExit(f"unexpected changes: {changed}")
target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
print(new[("enterprise", "dark", "border.divider")]); print(new[("enterprise", "light", "border.divider")])
PY
```

Expected: the two rows end in `#4a4d53` and `#d3d6da` (in the table's hex case).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.*' --tests 'dev.jasper.ui.testsupport.*' --tests 'dev.jasper.ui.KitMatrixTest' :jasper-app:test --tests 'dev.jasper.app.plugins.EnterpriseThemeTest' --tests 'dev.jasper.app.plugins.PresentationConformanceTest' --tests 'dev.jasper.app.application.EnterpriseKitRenderTest' --tests 'dev.jasper.app.architecture.ColourLiteralScanTest' --continue`
Expected: PASS. `KitMatrixTest`'s and `PaintedContrastTest`'s Enterprise cases still hold their guards (the new values
are the mocks', which meet them); `derived-roles.tsv` is unchanged (no built-in theme lists the key). The tests'
names of `KitMatrixTest` and `PaintedContrastTest` may live elsewhere; run `:jasper-ui:test` whole if the filter matches none.

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src plugins/enterprise/src/main/resources/themes/enterprise.json jasper-app/src/test/java/dev/jasper/app/plugins/EnterpriseThemeTest.java docs/themes.md
git commit -F - <<'EOF'
Window content: the Enterprise theme's chrome targets

The Enterprise theme takes the mocks' tab strip, tab indicator, scrim
and dividers. border.divider becomes the ninth overridable derived role
(a deviation from spec 7.2 awaiting approval): its recipe gives Enterprise
Dark a divider darker than its surfaces, where the Dark Mode mock draws a
lighter one. No built-in theme changes; the Enterprise derived table is
regenerated for the two divider rows.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: The toolbar's leading pack

`WindowToolbar` learns to pack its controls against the leading edge with the classic spacing (spec 10.3; Rulings 4):
buttons `BUTTON_HEIGHT` tall and centred, 8 between buttons, separators 1 × 18 with 10 on each side, views at their
own height, lower-priority buttons into the overflow menu and views hidden when the row is too narrow, the overflow
button after the last shown control. The trailing layout is untouched.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowToolbar.java` (3d's file: `buttonSide()`, `fullWidth`, `minimumWidth`, `doLayout`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowToolbarLeadingTest.java`

**Interfaces:**
- Consumes: 6a Task 3's `CHROME_BUTTON_GAP`, `CHROME_SEPARATOR_GAP`, `CHROME_SEPARATOR_INSET`; 3d's `WindowToolbar.view`, `entry`.
- Produces: `record WindowToolbar.Leading(int padding, int gap, int separatorGap, int separatorInset, int side)` (logical
  pixels) with `static Leading classic(DesignMetrics metrics)`; `void WindowToolbar.packLeading(Leading)`; `boolean
  WindowToolbar.leading()`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WindowToolbarLeadingTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import org.junit.jupiter.api.Test;

/** Spec 10.3 and the mocks: the classic toolbar packs left with the classic spacing and gives way as the trailing one does. */
class WindowToolbarLeadingTest {
    static final WindowToolbar.Leading CLASSIC = new WindowToolbar.Leading(8, 8, 10, 8, 24);

    static JButton button(WindowToolbar bar, String label, int width, int priority) {
        var button = new JButton(new AbstractAction(label) { @Override public void actionPerformed(ActionEvent event) { } }) {
            @Override public Dimension getPreferredSize() { return new Dimension(width, 24); }
        };
        WindowToolbar.entry(button, priority, () -> new JMenuItem(label));
        bar.add(button);
        return button;
    }

    @Test void controlsPackLeftWithTheMocksSpacing() throws Exception {
        edt(() -> {
            var bar = new WindowToolbar(() -> { });
            bar.packLeading(CLASSIC);
            bar.setBarHeight(34);
            var first = button(bar, "New Session", 96, 90);
            var rule = new JSeparator(SwingConstants.VERTICAL); bar.add(rule);
            var split = button(bar, "Split", 54, 90);
            var zoom = button(bar, "Maximize", 66, 10);
            var view = new JPanel(); view.setPreferredSize(new Dimension(300, 22)); WindowToolbar.view(view, 40);
            var plugin = new JSeparator(SwingConstants.VERTICAL); bar.add(plugin); bar.add(view);
            bar.finish();
            assertThat(bar.leading()).isTrue();
            assertThat(bar.fullWidth()).isEqualTo(8 + 96 + 21 + 54 + 8 + 66 + 21 + 300 + 8);
            bar.setSize(1000, 34); bar.doLayout();
            assertThat(first.getBounds()).as("8 from the edge, 24 tall, centred").isEqualTo(new Rectangle(8, 5, 96, 24));
            assertThat(rule.getBounds()).as("10 after the button, 18 tall, 8 from top and bottom").isEqualTo(new Rectangle(114, 8, 1, 18));
            assertThat(split.getX()).as("10 after the rule").isEqualTo(125);
            assertThat(zoom.getX()).as("8 after Split").isEqualTo(125 + 54 + 8);
            assertThat(view.getBounds()).as("a view keeps its height").isEqualTo(new Rectangle(187 + 66 + 21, 6, 300, 22));
            assertThat(bar.overflowButton().isVisible()).isFalse();
        });
    }

    @Test void aNarrowRowOverflowsTheLowestPriorityFirstAndThenHidesTheView() throws Exception {
        edt(() -> {
            var bar = new WindowToolbar(() -> { });
            bar.packLeading(CLASSIC);
            bar.setBarHeight(34);
            var first = button(bar, "New Session", 96, 90);
            var zoom = button(bar, "Maximize", 66, 10);
            var view = new JPanel(); view.setPreferredSize(new Dimension(300, 22)); WindowToolbar.view(view, 40);
            bar.add(new JSeparator(SwingConstants.VERTICAL)); bar.add(view);
            bar.finish();
            bar.setSize(8 + 96 + 21 + 300 + 8 + 24 + 8, 34); bar.doLayout();
            assertThat(zoom.isVisible()).as("Maximize (priority 10) moves into the menu before the view (40)").isFalse();
            assertThat(view.isVisible()).isTrue();
            assertThat(bar.overflowButton().getX()).as("the overflow button follows the last shown control").isEqualTo(8 + 96 + 21 + 300 + 8);
            bar.setSize(300, 34); bar.doLayout();
            assertThat(view.isVisible()).as("then the view hides; it never overflows").isFalse();
            assertThat(bar.overflowMenu().getComponentCount()).as("only Maximize is in the menu").isEqualTo(1);
            assertThat(bar.overflowButton().getX()).isEqualTo(8 + 96 + 8);
            assertThat(first.isVisible()).isTrue();
            bar.setSize(1000, 34); bar.doLayout();
            assertThat(view.isVisible() && zoom.isVisible()).as("room again").isTrue();
            assertThat(bar.overflowButton().isVisible()).isFalse();
        });
    }

    @Test void aTrailingRowLaysOutAsBefore() throws Exception {
        edt(() -> {
            var bar = new WindowToolbar(() -> { });
            var settings = button(bar, "Settings", 60, 100);
            bar.finish();
            bar.setSize(400, 30); bar.doLayout();
            assertThat(bar.leading()).isFalse();
            assertThat(settings.getX() + settings.getWidth()).as("packed right as today").isGreaterThan(300);
        });
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowToolbarLeadingTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol … Leading" and "… packLeading".

- [ ] **Step 3: The leading layout**

```bash
python3 -I - <<'PY'
"""Plan 6b Task 2 step 3: WindowToolbar's leading pack, beside the untouched trailing layout."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/workspace/WindowToolbar.java", [
    ('''    int buttonSide() { return buttonSide(barHeight); }''',
     '''    int buttonSide() { return leading != null ? UIScale.scale(leading.side()) : buttonSide(barHeight); }

    /**
     * How a leading row spaces its controls (Enterprise presentation spec 10.3), in logical pixels.
     *
     * @param padding before the first and after the last control
     * @param gap between two controls
     * @param separatorGap on each side of a separator
     * @param separatorInset above and below a separator
     * @param side a button's height (and an icon button's width)
     */
    record Leading(int padding, int gap, int separatorGap, int separatorInset, int side) {
        /** The classic toolbar's spacing in {@code metrics}. */
        static Leading classic(DesignMetrics metrics) {
            return new Leading(metrics.value(DesignMetrics.Role.TOOLBAR_PADDING), metrics.value(DesignMetrics.Role.CHROME_BUTTON_GAP),
                metrics.value(DesignMetrics.Role.CHROME_SEPARATOR_GAP), metrics.value(DesignMetrics.Role.CHROME_SEPARATOR_INSET),
                metrics.value(DesignMetrics.Role.BUTTON_HEIGHT));
        }
    }
    private Leading leading;

    /** Packs this row against the leading edge with {@code spacing}: the classic toolbar. Without it the row packs right. */
    void packLeading(Leading spacing) { leading = java.util.Objects.requireNonNull(spacing); revalidate(); repaint(); }
    /** Whether this row packs against the leading edge. */
    boolean leading() { return leading != null; }

    /** The width the shown controls take in a leading row, {@code hidden} left out, without the outer padding. */
    private int leadingWidth(java.util.Set<Component> hidden) {
        int gap = UIScale.scale(leading.gap()), rule = UIScale.scale(leading.separatorGap()), line = NativeStyles.installed().metric(DesignMetrics.Role.HAIRLINE);
        int width = 0;
        boolean previous = false;
        for (Component child : getComponents()) {
            if (child == overflow) continue;
            if (placed(child)) {
                if (hidden.contains(child)) continue;
                if (previous) width += gap;
                width += child.getPreferredSize().width;
                previous = true;
            } else if (child instanceof JSeparator && previous && followedByShown(child, hidden)) {
                width += 2 * rule + line;
                previous = false;
            }
        }
        return width;
    }

    private boolean followedByShown(Component separator, java.util.Set<Component> hidden) {
        for (int i = getComponentIndex(separator) + 1; i < getComponentCount(); i++) {
            Component next = getComponent(i);
            if (next instanceof JSeparator) return false;
            if (placed(next) && !hidden.contains(next)) return true;
        }
        return false;
    }

    /** The leading layout: controls from the leading edge, then the overflow button after the last one shown. */
    private void layoutLeading() {
        int padding = UIScale.scale(leading.padding()), gap = UIScale.scale(leading.gap()), side = buttonSide();
        int rule = UIScale.scale(leading.separatorGap()), inset = UIScale.scale(leading.separatorInset());
        int line = NativeStyles.installed().metric(DesignMetrics.Role.HAIRLINE);
        int available = Math.max(0, getWidth() - 2 * padding);
        // Labels give way before any control moves into the overflow menu.
        applyLabels(labelsWanted);
        var none = java.util.Collections.<Component>emptySet();
        if (labelsWanted && leadingWidth(none) > available) applyLabels(false);
        var hidden = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Component, Boolean>());
        var removable = new ArrayList<Component>();
        for (Component child : getComponents()) if (placed(child)) removable.add(child);
        java.util.Collections.reverse(removable);
        removable.sort(Comparator.comparingInt(control -> ((JComponent) control).getClientProperty(PRIORITY) instanceof Integer p ? p : 50));
        boolean overflowing = false;
        for (Component control : removable) {
            if (leadingWidth(hidden) + (overflowing ? gap + side : 0) <= available) break;
            boolean view = isView(control);
            if (!view && !(((JComponent) control).getClientProperty(MENU) instanceof Supplier<?>)) continue;
            hidden.add(control);
            if (!view) overflowing = true;
        }
        int x = padding;
        boolean previous = false;
        for (Component child : getComponents()) {
            if (child == overflow) continue;
            if (placed(child)) {
                boolean shown = !hidden.contains(child);
                child.setVisible(shown);
                if (!shown) { child.setBounds(0, 0, 0, 0); continue; }
                if (previous) x += gap;
                int width = child.getPreferredSize().width;
                int height = isView(child) ? Math.min(getHeight(), child.getPreferredSize().height) : side;
                child.setBounds(x, (getHeight() - height) / 2, width, height);
                x += width;
                previous = true;
            } else if (child instanceof JSeparator) {
                boolean shown = previous && followedByShown(child, hidden);
                child.setVisible(shown);
                if (!shown) { child.setBounds(0, 0, 0, 0); continue; }
                x += rule;
                child.setBounds(x, inset, line, Math.max(0, getHeight() - 2 * inset));
                x += line + rule;
                previous = false;
            }
        }
        overflow.setVisible(overflowing);
        if (overflowing) overflow.setBounds(x + gap, (getHeight() - side) / 2, side, side);
        else overflow.setBounds(0, 0, 0, 0);
    }''', 1),
    ('''    /** The width that shows every control; the header never gives the toolbar more. */
    int fullWidth() {''',
     '''    /** The width that shows every control; the header never gives the toolbar more. */
    int fullWidth() {
        if (leading != null) return 2 * UIScale.scale(leading.padding()) + leadingWidth(java.util.Collections.emptySet());''', 1),
    ('''    /** The width with every hideable control in the overflow menu or hidden. */
    int minimumWidth() {''',
     '''    /** The width with every hideable control in the overflow menu or hidden. */
    int minimumWidth() {
        if (leading != null) {
            int width = 2 * UIScale.scale(leading.padding()) + buttonSide();
            for (Component child : getComponents())
                if (child instanceof JButton button && button != overflow && !(button.getClientProperty(MENU) instanceof Supplier<?>))
                    width += button.getPreferredSize().width + UIScale.scale(leading.gap());
            return width;
        }''', 1),
    ('''    @Override public void doLayout() {
        int padding''',
     '''    @Override public void doLayout() {
        if (leading != null) { layoutLeading(); return; }
        int padding''', 1)])
PY
```

The test's `fullWidth` arithmetic: 8 + 96 + (10 + 1 + 10) + 54 + 8 + 66 + (10 + 1 + 10) + 300 + 8. The separators'
pixel line is `HAIRLINE` (1).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowToolbar*'`
Expected: PASS: `WindowToolbarLeadingTest` 3 and every existing `WindowToolbarTest` and `WindowToolbarViewsTest` case.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace/WindowToolbar.java jasper-app/src/test/java/dev/jasper/app/workspace/WindowToolbarLeadingTest.java
git commit -F - <<'EOF'
Window content: the toolbar's leading pack

WindowToolbar packs a classic row against the leading edge: 24 px buttons
centred, 8 between them, separators 1 x 18 with 10 on each side, views at
their own height; a narrow row hides its views and moves the
lowest-priority buttons into the overflow menu, whose button follows the
last control shown. The trailing layout is untouched.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: The classic header

`HeaderComposition` has two implementations (spec 10.3): `UnifiedHeader`, today's centred title field and right-packed
toolbar (moved, not rewritten), and `ClassicHeader`, the mocks' toolbar row: "+ New Session", a rule, "Split ▾",
"Maximize", the plugin group (contributed items and toolbar views), then "Search everywhere" with its keycap and
"Settings…" at the trailing edge, flat buttons that frame on hover, a 1 px `border.window` rule under the row, and no
title-bar zone. `window.toolbar` decides labels, icons or no row (Rulings 5). `WorkspaceChrome.of` picks the composition
from `WorkspaceLook.Header`.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/HeaderComposition.java`, `UnifiedHeader.java`, `ClassicHeader.java`, `ClassicToolbarButton.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowHeader.java:29-97`, `TitleSearchField.java:44-104`, `WindowChrome.java:52-120,382-418`, `WorkspaceChrome.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/ThemeZone.java:25`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ClassicHeaderTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (rows below)

**Interfaces:**
- Consumes: Task 2's `WindowToolbar.Leading`, `packLeading`; 6a's `WorkspaceChrome`, `EnterpriseLook`, `WindowChromeMenusTest.classic()`;
  3d's `ToolbarEntry.View`.
- Produces: `sealed interface HeaderComposition permits UnifiedHeader, ClassicHeader` with `void build(WindowChrome)`,
  `boolean titleBarZone()`, `Dimension minimumSize(WindowHeader)`, `void layout(WindowHeader)`, `Dimension
  searchPreferred()`, `searchMinimum()`, `searchMaximum()`, `void paintSearch(TitleSearchField, Graphics, boolean hover,
  boolean collapsed)`, `void toolbarMode(WindowHeader, ToolbarMode, boolean normal)`, `void refreshTheme(WindowHeader)`;
  `final class ClassicToolbarButton extends JButton` with `enum Glyph { NONE, PLUS }`; `ClassicHeader.trailing()`;
  `WindowHeader(TitleSearchField, WindowToolbar, HeaderComposition)` and `unifiedMinimumSize()`, `unifiedLayout()`;
  `TitleSearchField(Runnable, Supplier<String>, HeaderComposition)` and `contextIcon()`; `WindowChrome.windowToolbar()`,
  `composition()`, `buildUnifiedToolbar()`, `classicButton(WindowToolbar, ActionId, String, ClassicToolbarButton.Glyph,
  String icon, int priority)`, `classicSplit()`, `separator(boolean classic)`, `addPluginGroup(boolean classic)`;
  `WorkspaceChrome.header()` (a new composition per window); `static void ThemeZone.chrome(JComponent)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/ClassicHeaderTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.swing.FlatLafInstaller;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.border.MatteBorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Spec 10.3 and the Workstation mock: the classic toolbar row under a native title. */
class ClassicHeaderTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static WorkspaceChrome classic() {
        return WorkspaceChrome.of(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.CLASSIC, WorkspaceLook.Stripes.RAILS,
            WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD), new TitleFormat("{context}"));
    }

    static List<String> labels(WindowToolbar bar) {
        var labels = new ArrayList<String>();
        for (Component child : bar.getComponents())
            if (child instanceof ClassicToolbarButton button) labels.add(String.valueOf(button.getClientProperty("label")));
        return labels;
    }

    record Window(WindowContent owner, JRootPane root) {
        void width(int width) { root.setSize(width, 400); LayoutTestSupport.layoutTree(root); LayoutTestSupport.layoutTree(root); }
    }

    static Window window(ThemeController themes, boolean view) {
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
        owner.setToolbarHeight(34);
        var model = new Contributions();
        owner.connectContributions(model);
        if (view) {
            var panel = new JPanel(); panel.setPreferredSize(new Dimension(300, 22)); panel.setName("sessionView");
            model.addToolbar(new ToolbarEntry.View(id -> Optional.of(new ToolbarEntry.Mounted(panel, () -> { }))));
            owner.connectTerminals(new TerminalRegistry(), () -> { });
        }
        var root = new JRootPane();
        WindowContent.mount(root, owner, new WindowLook(classic(), false, true), title -> { });
        return new Window(owner, root);
    }

    @Test void theClassicRowIsTheMocks() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var window = window(themes, false);
                window.width(1440);
                var header = window.owner().chrome().header();
                var bar = window.owner().chrome().windowToolbar();
                assertThat(header.getHeight()).as("34 and the rule").isEqualTo(35);
                var rule = (MatteBorder) header.getBorder();
                assertThat(rule.getMatteColor()).isEqualTo(themes.current().chrome().semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_WINDOW));
                assertThat(ThemeZone.theme(header.field())).as("not a title-bar zone").isSameAs(FlatLafInstaller.installedTheme());
                assertThat(bar.leading()).isTrue();
                assertThat(labels(bar)).containsExactly("New Session", "Split", "Maximize");
                var first = (ClassicToolbarButton) bar.getComponent(0);
                assertThat(first.getX()).as("toolbar padding 8").isEqualTo(8);
                assertThat(first.getHeight()).isEqualTo(24);
                assertThat(first.getY()).isEqualTo(5);
                var classic = (ClassicHeader) window.owner().chrome().composition();
                var settings = (ClassicToolbarButton) classic.trailing().getComponent(0);
                int settingsRight = classic.trailing().getX() + settings.getX() + settings.getWidth();
                assertThat(settingsRight).as("8 from the content edge").isEqualTo(1440 - 8);
                var field = header.field();
                assertThat(field.getSize()).isEqualTo(new Dimension(300, 22));
                assertThat(field.getX() + field.getWidth()).as("6 before Settings…").isEqualTo(classic.trailing().getX() + settings.getX() - 6);
                assertThat(field.getY()).as("centred in the 34 px row").isEqualTo(6);
                assertThat(window.owner().chrome().menuBar().getParent()).as("the menus stay a row of their own").isSameAs(window.root().getLayeredPane());
            } finally { themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void aNarrowWindowGivesWayInOrder() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var window = window(themes, true);
                var header = window.owner().chrome().header();
                var bar = window.owner().chrome().windowToolbar();
                var classic = (ClassicHeader) window.owner().chrome().composition();
                int labelsGone = 0, maximize = 0, view = 0, shrunk = 0, collapsed = 0;
                for (int width = 1440; width >= 200; width -= 10) {
                    window.width(width);
                    var first = (ClassicToolbarButton) bar.getComponent(0);
                    var maximizeButton = bar.getComponent(3);
                    Component sessionView = WindowTabsTest.named(bar, "sessionView");
                    if (labelsGone == 0 && first.getText() == null) labelsGone = width;
                    if (maximize == 0 && !maximizeButton.isVisible()) maximize = width;
                    if (view == 0 && sessionView != null && !sessionView.isVisible()) view = width;
                    if (shrunk == 0 && header.field().getWidth() < 300) shrunk = width;
                    if (collapsed == 0 && header.field().getWidth() == 0) collapsed = width;
                    assertThat(classic.trailing().getComponent(0).isVisible()).as("Settings… stays at " + width).isTrue();
                }
                assertThat(List.of(labelsGone, maximize, view, shrunk, collapsed)).as("each gave way").doesNotContain(0);
                assertThat(labelsGone).as("labels first").isGreaterThanOrEqualTo(maximize);
                assertThat(maximize).as("then Maximize into the menu").isGreaterThanOrEqualTo(view);
                assertThat(view).as("then the view hides").isGreaterThanOrEqualTo(shrunk);
                assertThat(shrunk).as("then the field shrinks, then collapses").isGreaterThan(collapsed);
                window.width(1440);
                assertThat(header.field().getWidth()).isEqualTo(300);
                assertThat(WindowTabsTest.named(bar, "sessionView").isVisible()).isTrue();
            } finally { themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void theToolbarSettingChoosesLabelsIconsOrNoRow() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var window = window(themes, false);
            window.owner().setToolbarMode(ToolbarMode.ICONS);
            window.width(1440);
            var first = (ClassicToolbarButton) window.owner().chrome().windowToolbar().getComponent(0);
            assertThat(first.getText()).as("icons only").isNull();
            assertThat(first.getPreferredSize().width).as("square").isEqualTo(first.getPreferredSize().height);
            window.owner().setToolbarMode(ToolbarMode.HIDDEN);
            assertThat(window.owner().chrome().header().isVisible()).as("hidden: no row").isFalse();
            window.owner().setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
            window.width(1440);
            assertThat(window.owner().chrome().header().isVisible()).isTrue();
            assertThat(first.getText()).isEqualTo("New Session");
        });
    }

    @Test void theDefaultHeaderIsTodays() throws Exception {
        edt(() -> {
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            assertThat(owner.chrome().composition()).isInstanceOf(UnifiedHeader.class);
            assertThat(owner.chrome().windowToolbar().leading()).isFalse();
            assertThat(owner.chrome().header().getBorder()).isNull();
        });
    }
}
```

`WindowTabsTest.named(container, name)` is the existing finder by component name (used by 3d's tests). If
`WindowContent.setToolbarMode` is not package-visible to tests, call it the way `WindowChromeContributionsTest` does
(through `applyConfiguration` with `ConfigSnapshot.defaults().toBuilder().toolbar(mode).build()`).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ClassicHeaderTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol … ClassicToolbarButton", "… ClassicHeader", "… windowToolbar()".

- [ ] **Step 3: The compositions and the classic button**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/HeaderComposition.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.config.ToolbarMode;
import java.awt.Dimension;
import java.awt.Graphics;

/**
 * How a window's header row is composed (Enterprise presentation spec 10.3): today's unified header, or the classic
 * toolbar row under a native title. One instance per window; the window's chrome builds it once. EDT only.
 */
sealed interface HeaderComposition permits UnifiedHeader, ClassicHeader {
    /** Fills the window's toolbars with the built-in controls; contributed controls follow the plugin group. */
    void build(WindowChrome chrome);

    /** Whether the header takes the title bar's colours ({@code ThemeZone.titleBar}). */
    boolean titleBarZone();

    /** The header's minimum size. */
    Dimension minimumSize(WindowHeader header);

    /** Lays the header's children out. */
    void layout(WindowHeader header);

    /** The search field's preferred size, in device pixels. */
    Dimension searchPreferred();

    /** The search field's minimum size before it collapses, in device pixels. */
    Dimension searchMinimum();

    /** The search field's maximum size, in device pixels. */
    Dimension searchMaximum();

    /** Paints the search field. */
    void paintSearch(TitleSearchField field, Graphics graphics, boolean hover, boolean collapsed);

    /** Applies the toolbar setting: which rows show and whether buttons carry labels; {@code normal} is the Normal view mode. */
    void toolbarMode(WindowHeader header, ToolbarMode mode, boolean normal);

    /** Restyles after a theme change. */
    void refreshTheme(WindowHeader header);
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/UnifiedHeader.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.swing.FlatLafInstaller;
import java.awt.Dimension;
import java.awt.Graphics;

/** Today's header: the centred title field and the IntelliJ toolbar packed against the trailing edge. */
final class UnifiedHeader implements HeaderComposition {
    private static int metric(DesignMetrics.Role role) { return FlatLafInstaller.installedKit().metrics().value(role); }

    @Override public void build(WindowChrome chrome) { chrome.buildUnifiedToolbar(); }
    @Override public boolean titleBarZone() { return true; }
    @Override public Dimension minimumSize(WindowHeader header) { return header.unifiedMinimumSize(); }
    @Override public void layout(WindowHeader header) { header.unifiedLayout(); }
    @Override public Dimension searchPreferred() {
        return new Dimension(UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_WIDTH)), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT)));
    }
    @Override public Dimension searchMinimum() {
        return new Dimension(UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_MIN_WIDTH)), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT)));
    }
    @Override public Dimension searchMaximum() {
        return new Dimension(UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_MAX_WIDTH)), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT)));
    }
    @Override public void paintSearch(TitleSearchField field, Graphics graphics, boolean hover, boolean collapsed) {
        ChromePainter.installed().search(field, graphics, ThemeZone.theme(field), hover, collapsed,
            field.contextText(), field.shortcutText(), AppIcons.icon("search"), field.contextIcon());
    }
    @Override public void toolbarMode(WindowHeader header, ToolbarMode mode, boolean normal) {
        header.toolbar().setVisible(normal && mode != ToolbarMode.HIDDEN);
        header.toolbar().setLabelsWanted(mode == ToolbarMode.ICONS_AND_LABELS);
    }
    @Override public void refreshTheme(WindowHeader header) { }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/ClassicToolbarButton.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;

/**
 * A classic toolbar button (spec 10.3 and 6.4): flat until hovered, then {@code surface.control.hover} in a 1 px
 * {@code border.hover} frame ({@code surface.control.selected} while pressed); a label-first text with a leading "+" or a
 * trailing ▼ where the mocks have them; its icon alone when the toolbar shows icons only (its text is then null).
 */
final class ClassicToolbarButton extends JButton {
    /** A leading glyph. */
    enum Glyph { NONE, PLUS }
    private final Glyph glyph;
    private final boolean caret;
    private final Icon icon;

    ClassicToolbarButton(Action action, String label, Glyph glyph, boolean caret, Icon icon) {
        super(action);
        this.glyph = glyph; this.caret = caret; this.icon = icon;
        setHideActionText(true);
        setText(label);
        putClientProperty("label", label);
        putClientProperty("html.disable", true);
        setFocusable(false); setRolloverEnabled(true); setOpaque(false); setContentAreaFilled(false); setBorderPainted(false);
        setToolTipText(label);
        getAccessibleContext().setAccessibleName(label);
        setFont(ThemeZone.font(Typography.Role.TOOLBAR_LABEL));
    }

    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    private int side() { return getParent() instanceof WindowToolbar bar ? bar.buttonSide() : metric(DesignMetrics.Role.BUTTON_HEIGHT); }

    @Override public Dimension getPreferredSize() {
        int side = side();
        if (getText() == null) return new Dimension(side, side);
        int width = 2 * metric(DesignMetrics.Role.CHROME_BUTTON_PADDING) + getFontMetrics(getFont()).stringWidth(getText());
        if (glyph == Glyph.PLUS) width += metric(DesignMetrics.Role.CHROME_GLYPH) + metric(DesignMetrics.Role.CHROME_GLYPH_GAP);
        if (caret) width += metric(DesignMetrics.Role.CHROME_CARET_GAP) + metric(DesignMetrics.Role.CHROME_CARET);
        return new Dimension(width, side);
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            FlatUIUtils.setRenderingHints(g);
            var theme = ThemeZone.theme(this);
            var palette = theme.semantic();
            int width = getWidth(), height = getHeight(), arc = 2 * metric(DesignMetrics.Role.CONTROL_RADIUS);
            boolean pressed = getModel().isPressed() || getModel().isSelected();
            if (isEnabled() && (getModel().isRollover() || pressed)) {
                g.setColor(palette.color(pressed ? SemanticColor.SURFACE_CONTROL_SELECTED : SemanticColor.SURFACE_CONTROL_HOVER));
                g.fillRoundRect(0, 0, width, height, arc, arc);
                g.setColor(DerivedColor.BORDER_HOVER.color(theme));
                g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            }
            Color ink = SurfaceContrast.on(palette, SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_APP, SurfaceContrast.TEXT);
            if (!isEnabled()) ink = ColorMath.opacity(ink, .42);
            if (getText() == null) {
                if (icon != null) icon.paintIcon(this, g, (width - icon.getIconWidth()) / 2, (height - icon.getIconHeight()) / 2);
                return;
            }
            g.setColor(ink);
            int x = metric(DesignMetrics.Role.CHROME_BUTTON_PADDING), middle = height / 2;
            if (glyph == Glyph.PLUS) {
                int size = metric(DesignMetrics.Role.CHROME_GLYPH), stroke = Math.max(1, UIScale.scale(1.5f) >= 2 ? 2 : 1);
                g.fillRect(x, middle - stroke / 2, size, stroke);
                g.fillRect(x + (size - stroke) / 2, middle - size / 2, stroke, size);
                x += size + metric(DesignMetrics.Role.CHROME_GLYPH_GAP);
            }
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            FlatUIUtils.drawString(this, g, getText(), x, (height - metrics.getHeight()) / 2 + metrics.getAscent());
            x += metrics.stringWidth(getText());
            if (caret) {
                x += metric(DesignMetrics.Role.CHROME_CARET_GAP);
                int size = metric(DesignMetrics.Role.CHROME_CARET), top = middle - size / 3;
                g.fill(new Polygon(new int[] {x, x + size, x + size / 2}, new int[] {top, top, top + size - 1}, 3));
            }
        } finally { g.dispose(); }
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/ClassicHeader.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.BorderFactory;

/**
 * The classic toolbar row (spec 10.3; the Workstation mocks): left-packed "+ New Session", "Split ▾" and "Maximize" in
 * separated groups, the plugin group, then "Search everywhere" with the palette's keycap and "Settings…" at the trailing
 * edge, over a 1 px {@code border.window} rule. The row is window chrome under a native title, not a title-bar zone.
 */
final class ClassicHeader implements HeaderComposition {
    private final WindowToolbar trailing = new WindowToolbar(() -> { });

    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    /** The trailing group: "Settings…". */
    WindowToolbar trailing() { return trailing; }

    @Override public void build(WindowChrome chrome) {
        var spacing = WindowToolbar.Leading.classic(FlatLafInstaller.installedKit().metrics());
        var toolbar = chrome.windowToolbar();
        toolbar.packLeading(spacing);
        chrome.classicButton(toolbar, ActionId.NEW_TAB, "New Session", ClassicToolbarButton.Glyph.PLUS, "square-plus", 90);
        toolbar.add(chrome.separator(true));
        chrome.classicSplit();
        chrome.classicButton(toolbar, ActionId.ZOOM_PANE, "Maximize", ClassicToolbarButton.Glyph.NONE, "maximize", 10);
        chrome.addPluginGroup(true);
        toolbar.finish();
        trailing.setFloatable(false);
        trailing.setBorder(BorderFactory.createEmptyBorder());
        trailing.setOpaque(false);
        trailing.packLeading(new WindowToolbar.Leading(0, spacing.gap(), spacing.separatorGap(), spacing.separatorInset(), spacing.side()));
        chrome.classicButton(trailing, ActionId.OPEN_SETTINGS, "Settings…", ClassicToolbarButton.Glyph.NONE, "settings", 100);
        trailing.finish();
        chrome.header().add(trailing);
        refreshTheme(chrome.header());
    }

    @Override public boolean titleBarZone() { return false; }

    @Override public Dimension minimumSize(WindowHeader header) {
        Insets insets = header.getInsets();
        int toolbar = header.toolbar().isVisible() ? header.toolbar().minimumWidth() : 0;
        return new Dimension(insets.left + toolbar + metric(DesignMetrics.Role.CHROME_TRAILING_GAP) + trailing.fullWidth()
            + metric(DesignMetrics.Role.TOOLBAR_PADDING) + insets.right, header.toolbar().getPreferredSize().height + insets.top + insets.bottom);
    }

    @Override public void layout(WindowHeader header) {
        Insets insets = header.getInsets();
        int top = insets.top, height = Math.max(0, header.getHeight() - insets.top - insets.bottom);
        int gap = metric(DesignMetrics.Role.CHROME_TRAILING_GAP), right = Math.max(insets.left, header.getWidth() - insets.right - metric(DesignMetrics.Role.TOOLBAR_PADDING));
        int trailingWidth = Math.min(trailing.fullWidth(), right - insets.left);
        trailing.setBounds(right - trailingWidth, top, trailingWidth, height);
        var field = header.field();
        var toolbar = header.toolbar();
        Dimension preferred = searchPreferred(), minimum = searchMinimum();
        int fieldRight = trailing.getX() - gap;
        int toolbarMinimum = toolbar.isVisible() ? toolbar.minimumWidth() : 0;
        // The toolbar gives way first (labels, then the overflow menu, then its views); then the field shrinks and collapses.
        int fieldWidth = Math.min(preferred.width, Math.max(0, fieldRight - insets.left - toolbarMinimum - gap));
        boolean collapsed = fieldWidth < minimum.width;
        field.setCollapsed(collapsed);
        if (collapsed) fieldWidth = 0;
        int fieldX = fieldRight - fieldWidth;
        field.setBounds(fieldX, top + (height - preferred.height) / 2, fieldWidth, fieldWidth == 0 ? 0 : preferred.height);
        int toolbarRight = fieldWidth == 0 ? fieldRight : fieldX - gap;
        toolbar.setBounds(insets.left, top, toolbar.isVisible() ? Math.max(0, toolbarRight - insets.left) : 0, height);
    }

    @Override public Dimension searchPreferred() { return new Dimension(metric(DesignMetrics.Role.TOOLBAR_SEARCH_WIDTH), metric(DesignMetrics.Role.FIELD_HEIGHT)); }
    @Override public Dimension searchMinimum() { return new Dimension(metric(DesignMetrics.Role.TOOLBAR_SEARCH_WIDTH) / 2, metric(DesignMetrics.Role.FIELD_HEIGHT)); }
    @Override public Dimension searchMaximum() { return searchPreferred(); }

    /**
     * A field on {@code surface.input} in a {@code border.control} edge ({@code border.hover} while hovered), the
     * placeholder "Search everywhere" and, at its trailing edge, the palette's shortcut in a light keycap (Rulings 6).
     * The placeholder and its hint keep {@code text.placeholder}, as field placeholders do.
     */
    @Override public void paintSearch(TitleSearchField field, Graphics graphics, boolean hover, boolean collapsed) {
        int width = field.getWidth(), height = field.getHeight();
        if (width <= 0 || height <= 0) return;
        var g = (Graphics2D) graphics.create();
        try {
            FlatUIUtils.setRenderingHints(g);
            var theme = ThemeZone.theme(field);
            var palette = theme.semantic();
            int arc = 2 * metric(DesignMetrics.Role.FIELD_RADIUS);
            g.setColor(palette.color(SemanticColor.SURFACE_INPUT));
            g.fillRoundRect(0, 0, width, height, arc, arc);
            g.setColor(hover ? DerivedColor.BORDER_HOVER.color(theme)
                : SurfaceContrast.painted(theme, new PaintPair(SemanticColor.BORDER_CONTROL, SemanticColor.SURFACE_APP, SurfaceContrast.Guard.CONTROL)));
            g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            if (collapsed) return;
            int pad = metric(DesignMetrics.Role.TOOLBAR_SEARCH_PADDING), hint = 0;
            String shortcut = field.shortcutText();
            if (!shortcut.isEmpty()) {
                g.setFont(ThemeZone.font(Typography.Role.MONO_SMALL));
                FontMetrics mono = g.getFontMetrics();
                int keyPad = metric(DesignMetrics.Role.HINT_KEYCAP_PADDING), keyHeight = metric(DesignMetrics.Role.HINT_KEYCAP_HEIGHT);
                hint = mono.stringWidth(shortcut) + 2 * keyPad;
                int x = width - pad - hint, y = (height - keyHeight) / 2, keyArc = 2 * metric(DesignMetrics.Role.KEYCAP_RADIUS);
                g.setColor(DerivedColor.SURFACE_TABLE_HEADER.color(theme));
                g.fillRoundRect(x, y, hint, keyHeight, keyArc, keyArc);
                g.setColor(DerivedColor.BORDER_DIVIDER.color(theme));
                g.drawRoundRect(x, y, hint - 1, keyHeight - 1, keyArc, keyArc);
                g.setColor(palette.color(SemanticColor.TEXT_PLACEHOLDER));
                FlatUIUtils.drawString(field, g, shortcut, x + keyPad, y + (keyHeight - mono.getHeight()) / 2 + mono.getAscent());
                hint += pad;
            }
            g.setFont(ThemeZone.font(Typography.Role.BODY));
            FontMetrics body = g.getFontMetrics();
            g.setColor(palette.color(SemanticColor.TEXT_PLACEHOLDER));
            String text = TitleSearchField.clip("Search everywhere", body, Math.max(0, width - 2 * pad - hint));
            FlatUIUtils.drawString(field, g, text, pad, (height - body.getHeight()) / 2 + body.getAscent());
        } finally { g.dispose(); }
    }

    @Override public void toolbarMode(WindowHeader header, ToolbarMode mode, boolean normal) {
        // Spec 10.3: hidden gives no row, so the Session view and Search everywhere go with it.
        header.setVisible(normal && mode != ToolbarMode.HIDDEN);
        header.toolbar().setVisible(true);
        boolean labels = mode == ToolbarMode.ICONS_AND_LABELS;
        header.toolbar().setLabelsWanted(labels);
        trailing.setLabelsWanted(labels);
    }

    @Override public void refreshTheme(WindowHeader header) {
        var theme = FlatLafInstaller.installedTheme();
        header.setBorder(BorderFactory.createMatteBorder(0, 0, metric(DesignMetrics.Role.HAIRLINE), 0,
            SurfaceContrast.painted(theme, new PaintPair(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_APP, SurfaceContrast.Guard.STRUCTURAL))));
        for (var bar : new WindowToolbar[] {header.toolbar(), trailing})
            for (Component child : bar.getComponents())
                if (child instanceof ClassicToolbarButton button) button.setFont(ThemeZone.font(Typography.Role.TOOLBAR_LABEL));
        header.revalidate(); header.repaint();
    }
}
```

- [ ] **Step 4: Route the header, the field and the window chrome through the composition**

```bash
python3 -I - <<'PY'
"""Plan 6b Task 3 step 4: WindowHeader, TitleSearchField, WindowChrome, WorkspaceChrome and ThemeZone."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WindowHeader.java", [
    ('''    private final TitleSearchField field;
    private final WindowToolbar toolbar;

    WindowHeader(TitleSearchField field, WindowToolbar toolbar) {
        super(null);
        this.field = field;
        this.toolbar = toolbar;''',
     '''    private final TitleSearchField field;
    private final WindowToolbar toolbar;
    private final HeaderComposition composition;

    WindowHeader(TitleSearchField field, WindowToolbar toolbar) { this(field, toolbar, new UnifiedHeader()); }

    /** A header composed by {@code composition} (Enterprise presentation spec 10.3). */
    WindowHeader(TitleSearchField field, WindowToolbar toolbar, HeaderComposition composition) {
        super(null);
        this.field = field;
        this.toolbar = toolbar;
        this.composition = composition;''', 1),
    ('''    @Override public Dimension getMinimumSize() {
        Insets insets = getInsets();''',
     '''    @Override public Dimension getMinimumSize() { return composition.minimumSize(this); }

    @Override public void doLayout() { composition.layout(this); }

    /** Today's minimum: the toolbar's or the leading content's side twice around the field. */
    Dimension unifiedMinimumSize() {
        Insets insets = getInsets();''', 1),
    ('''    @Override public void doLayout() {
        Insets insets = getInsets();
        int width = getWidth(), height = getHeight(), gap = UIScale.scale(headerGap());''',
     '''    /** Today's layout: the field centred on the window, the toolbar to its right, the leading content to its left. */
    void unifiedLayout() {
        Insets insets = getInsets();
        int width = getWidth(), height = getHeight(), gap = UIScale.scale(headerGap());''', 1)])
edit(W + "TitleSearchField.java", [
    ('''    TitleSearchField(Runnable open, Supplier<String> shortcut) {
        this.open = Objects.requireNonNull(open);''',
     '''    private final HeaderComposition composition;

    TitleSearchField(Runnable open, Supplier<String> shortcut) { this(open, shortcut, new UnifiedHeader()); }

    /** A field drawn and sized by {@code composition}. */
    TitleSearchField(Runnable open, Supplier<String> shortcut, HeaderComposition composition) {
        this.composition = Objects.requireNonNull(composition);
        this.open = Objects.requireNonNull(open);''', 1),
    ('''    String contextText() { return context; }''', '''    String contextText() { return context; }
    Icon contextIcon() { return contextIcon; }''', 1),
    ('''    @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(preferredWidth()), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT))); }
    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(minimumWidth()), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT))); }
    @Override public Dimension getMaximumSize() { return new Dimension(UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_MAX_WIDTH)), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT))); }''',
     '''    @Override public Dimension getPreferredSize() { return composition.searchPreferred(); }
    @Override public Dimension getMinimumSize() { return composition.searchMinimum(); }
    @Override public Dimension getMaximumSize() { return composition.searchMaximum(); }''', 1),
    ('''        ChromePainter.installed().search(this, graphics, ThemeZone.theme(this), hover || pressed, collapsed,
            context, shortcutText(), AppIcons.icon("search"), contextIcon);''',
     '''        composition.paintSearch(this, graphics, hover || pressed, collapsed);''', 1)])
edit(W + "WindowChrome.java", [
    ('''    private final WindowContent owner;''', '''    private final WindowContent owner;
    /** This window's header composition (spec 10.3), built once. */
    private final HeaderComposition composition;''', 1),
    ('''        this.owner = owner;
        toolbar = new WindowToolbar(owner::updateActions);''',
     '''        this.owner = owner;
        composition = owner.look().header();
        toolbar = new WindowToolbar(owner::updateActions);''', 1),
    ('''            owner::paletteShortcut);
        header = new WindowHeader(search, toolbar);
        dev.jasper.app.platform.ThemeZone.titleBar(header);''',
     '''            owner::paletteShortcut, composition);
        header = new WindowHeader(search, toolbar, composition);
        // With a native title the toolbar row is chrome, not a title-bar zone (spec 10.1).
        if (composition.titleBarZone()) dev.jasper.app.platform.ThemeZone.titleBar(header);''', 1),
    ('''        buildToolbar();
        owner.action(ActionId.COMMAND_PALETTE)''', '''        composition.build(this);
        owner.action(ActionId.COMMAND_PALETTE)''', 1),
    ('''    private void buildToolbar() {''', '''    void buildUnifiedToolbar() {''', 1),
    ('''    private void addSplitButton() {''',
     '''    /** A classic toolbar button for {@code id} in {@code into} (spec 10.3), its icon {@code icon} under icons only. */
    ClassicToolbarButton classicButton(WindowToolbar into, ActionId id, String label, ClassicToolbarButton.Glyph glyph, String icon, int priority) {
        var button = new ClassicToolbarButton(owner.action(id), label, glyph, false, AppIcons.icon(icon));
        WindowToolbar.entry(button, priority, () -> WindowToolbar.menuItem(owner.action(id)));
        into.add(button);
        return button;
    }

    /** The classic "Split ▾": a label and a caret opening Split Right and Split Down. */
    void classicSplit() {
        var split = new ClassicToolbarButton(null, "Split", ClassicToolbarButton.Glyph.NONE, true, AppIcons.icon("columns-2"));
        split.setToolTipText("Split pane right or down"); split.getAccessibleContext().setAccessibleName("Split pane");
        WindowToolbar.entry(split, 90, () -> {
            var menu = new JMenu("Split");
            menu.add(WindowToolbar.menuItem(owner.action(ActionId.SPLIT_RIGHT)));
            menu.add(WindowToolbar.menuItem(owner.action(ActionId.SPLIT_DOWN)));
            return menu;
        });
        split.addActionListener(event -> {
            owner.updateActions(); JPopupMenu popup = new JPopupMenu();
            popup.add(owner.action(ActionId.SPLIT_RIGHT)); popup.add(owner.action(ActionId.SPLIT_DOWN));
            NativeStyles.installed().menuTree(popup, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
            popup.show(split, 0, split.getHeight());
        });
        owner.action(ActionId.SPLIT_RIGHT).addPropertyChangeListener(event -> {
            if (event.getPropertyName().equals("enabled")) split.setEnabled(owner.action(ActionId.SPLIT_RIGHT).isEnabled());
        });
        toolbar.add(split);
    }

    /** A toolbar rule; a classic one is a 1 px {@code border.window} line. */
    JSeparator separator(boolean classic) { var rule = new ToolbarSeparator(); rule.classic = classic; return rule; }

    /** The group contributed controls join, after its rule. */
    void addPluginGroup(boolean classic) {
        pluginSeparator.classic = classic;
        pluginSeparator.setVisible(false);
        toolbar.add(pluginSeparator);
    }

    WindowToolbar windowToolbar() { return toolbar; }
    HeaderComposition composition() { return composition; }

    private void addSplitButton() {''', 1),
    ('''    private static final class ToolbarSeparator extends JSeparator {
        ToolbarSeparator() { super(SwingConstants.VERTICAL); }
        @Override protected void paintComponent(Graphics g) { ChromePainter.installed().rule(this, g, ThemeZone.theme(null)); }
    }''',
     '''    private static final class ToolbarSeparator extends JSeparator {
        /** A classic rule (spec 10.3): the toolbar's 1 px border.window line. */
        boolean classic;
        ToolbarSeparator() { super(SwingConstants.VERTICAL); }
        @Override protected void paintComponent(Graphics g) {
            if (!classic) { ChromePainter.installed().rule(this, g, ThemeZone.theme(null)); return; }
            g.setColor(ThemeZone.color(this, SemanticColor.BORDER_WINDOW));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }''', 1),
    ('''    void setToolbarMode(ToolbarMode mode) {
        toolbar.setVisible(owner.viewMode() == ViewMode.NORMAL && mode != ToolbarMode.HIDDEN);
        toolbar.setLabelsWanted(mode == ToolbarMode.ICONS_AND_LABELS);''',
     '''    void setToolbarMode(ToolbarMode mode) {
        composition.toolbarMode(header, mode, owner.viewMode() == ViewMode.NORMAL);''', 1),
    ('''        toolbar.setBackground(ThemeZone.color(toolbar, SemanticColor.SURFACE_APP));''',
     '''        toolbar.setBackground(ThemeZone.color(toolbar, SemanticColor.SURFACE_APP));
        composition.refreshTheme(header);''', 1)])
edit(W + "WorkspaceChrome.java", [
    ('''    public static final WorkspaceChrome STANDARD = new WorkspaceChrome(new StandardMenuBar(), new TitleFormat("{context}"));

    private final MenuBarLayout menus;
    private final TitleFormat title;

    private WorkspaceChrome(MenuBarLayout menus, TitleFormat title) {
        this.menus = Objects.requireNonNull(menus);
        this.title = Objects.requireNonNull(title);
    }''',
     '''    public static final WorkspaceChrome STANDARD = new WorkspaceChrome(new StandardMenuBar(), new TitleFormat("{context}"), UnifiedHeader::new);

    private final MenuBarLayout menus;
    private final TitleFormat title;
    private final java.util.function.Supplier<HeaderComposition> header;

    private WorkspaceChrome(MenuBarLayout menus, TitleFormat title, java.util.function.Supplier<HeaderComposition> header) {
        this.menus = Objects.requireNonNull(menus);
        this.title = Objects.requireNonNull(title);
        this.header = Objects.requireNonNull(header);
    }''', 1),
    ('''        return new WorkspaceChrome(menus, title);''',
     '''        java.util.function.Supplier<HeaderComposition> header = switch (look.header()) {
            case UNIFIED -> UnifiedHeader::new;
            case CLASSIC -> ClassicHeader::new;
        };
        return new WorkspaceChrome(menus, title, header);''', 1),
    ('''    TitleFormat title() { return title; }''', '''    TitleFormat title() { return title; }
    /** A new header composition for one window. */
    HeaderComposition header() { return header.get(); }''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/platform/ThemeZone.java", [
    ('''    public static void titleBar(JComponent header) { header.putClientProperty(ZONE, TITLE_BAR); }''',
     '''    public static void titleBar(JComponent header) { header.putClientProperty(ZONE, TITLE_BAR); }

    /** Makes {@code component} window chrome again, not a title-bar zone (a classic header under a native title). */
    public static void chrome(JComponent component) { component.putClientProperty(ZONE, null); }''', 1)])
PY
grep -n 'import javax.swing\|import java.awt' jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java | head -3
```

`WindowChrome` already imports `java.awt.*` and `javax.swing.*` (so `JSeparator` and `JMenu` resolve); `TitleSearchField`
keeps its imports (`ChromePainter` stays used by `clip`). If the compiler reports an unused import or a missing one in
`WindowHeader` (`Dimension`, `Insets` stay used), adjust it.

Add the ledger rows:

```bash
python3 -I - <<'PY'
import pathlib
W, T = "jasper-app/src/main/java/dev/jasper/app/workspace/", "jasper-app/src/test/java/dev/jasper/app/workspace/ClassicHeaderTest.java"
rows = [(W + "HeaderComposition.java", "HeaderComposition", "New in Enterprise presentation Plan 6b Task 3; how a window's header row is composed"),
        (W + "UnifiedHeader.java", "UnifiedHeader", "New in Enterprise presentation Plan 6b Task 3; today's header; moved"),
        (W + "ClassicHeader.java", "ClassicHeader", "New in Enterprise presentation Plan 6b Task 3; the classic toolbar row; search field and Settings"),
        (W + "ClassicToolbarButton.java", "ClassicToolbarButton", "New in Enterprise presentation Plan 6b Task 3; a flat classic toolbar button"),
        (T, "ClassicHeaderTest", "Test/fixture ownership; New in Enterprise presentation Plan 6b Task 3; the classic row against the mock")]
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
text += "" if text.endswith("\n") else "\n"
ledger.write_text(text + "".join(f"{p},app.workspace,{c},nonvisual-helper,none,none,{T},{d}\n" for p, c, d in rows), encoding="utf-8")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --tests 'dev.jasper.app.architecture.*' :jasper-app:javadoc`
Expected: PASS: `ClassicHeaderTest` 4; every existing header, toolbar, title-search, title-bar and contributions test
(`WindowHeaderTest`, `TitleSearchFieldTest`, `MacTitleBarTest`, `WindowChromeContributionsTest`, `ViewModeChromeTest`)
unchanged, since `UnifiedHeader` runs today's code.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/main/java/dev/jasper/app/platform/ThemeZone.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/ClassicHeaderTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Window content: the classic header

HeaderComposition composes the header row two ways: UnifiedHeader is
today's, moved; ClassicHeader is the mocks' toolbar row under a native
title: + New Session, Split, Maximize and the plugin group packed left,
Search everywhere with the palette's keycap and Settings at the trailing
edge, flat buttons that frame on hover, a border.window rule and no
title-bar zone. window.toolbar picks labels, icons or no row; a narrow
window gives way labels first, then the overflow menu, the views and the
field.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: Classic tabs

`TabStrip` has two implementations driving the same `TerminalDeck` callbacks (spec 10.5): `WindowTabs` (today's equal
tabs, now implementing the interface) and `ClassicTabs`: always visible, content-sized, a status dot, the label, a
persistent ×, a "+" after the last tab, the active tab on `surface.card` with a 2 px `tab.indicator` underline, 1 px
`border.window` dividers and the strip's rule, no motion (Rulings 7). `WorkspaceChrome.of` picks it from
`WorkspaceLook.Tabs`.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/TabStrip.java`, `ClassicTabs.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowTabs.java:22,45,98,101,162` (implements `TabStrip`), `WindowContent.java:61,247,252,888,1226,1373`, `WorkspaceChrome.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ClassicTabsTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (rows below)

**Interfaces:**
- Consumes: 6a's `WorkspaceChrome`; `WindowContent.selectTab`, `closeTab`, `tabMenu`, `reorderTab`, `tabTitle`, `tabTooltip`, `tabHeight`, `invoke(ActionId)`.
- Produces: `sealed interface TabStrip permits WindowTabs, ClassicTabs` with `JComponent component()`, `void refresh()`,
  `void setActive(boolean)`, `void setAnimations(AnimationSettings)`, `JPopupMenu tabList()`, `void close()`;
  `WorkspaceChrome.tabs(WindowContent)`; `WindowContent.tabBar()` (the strip) beside `windowTabs()` (the equal tabs, for
  the default look's tests).

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/ClassicTabsTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Spec 10.5 and the Workstation mock: classic editor tabs. */
class ClassicTabsTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static WorkspaceChrome classic() {
        return WorkspaceChrome.of(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.CLASSIC, WorkspaceLook.Stripes.RAILS,
            WorkspaceLook.Tabs.CLASSIC, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD), new TitleFormat("{context}"));
    }

    static ClassicTabs tabs(WindowContent owner, int width) {
        var strip = (ClassicTabs) owner.tabBar();
        strip.setSize(width, strip.getPreferredSize().height);
        LayoutTestSupport.layoutTree(strip);
        return strip;
    }

    @Test void oneTabStillShowsTheStrip() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.update();
                var strip = tabs(owner, 800);
                assertThat(strip.isVisible()).as("always visible").isTrue();
                assertThat(strip.getPreferredSize().height).as("window.tab_height with its rule").isEqualTo(owner.tabHeight());
                assertThat(strip.entries()).hasSize(1);
            } finally { themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void tabsAreContentSizedWithTheMocksGeometry() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.currentTab().rename("Local: ~");
                owner.newTab(DesktopTestSupport.HOME);
                owner.currentTab().rename("Mac: ~");
                owner.update();
                var strip = tabs(owner, 800);
                var first = strip.entries().get(0);
                var second = strip.entries().get(1);
                int text = first.getFontMetrics(first.getFont()).stringWidth("Local: ~");
                assertThat(first.getWidth()).as("12, dot 7, 9, label, 9, x 7, 10").isEqualTo(12 + 7 + 9 + text + 9 + 7 + 10);
                assertThat(second.getX()).as("a 1 px divider between").isEqualTo(first.getWidth() + 1);
                assertThat(strip.add().getX()).as("the + follows the last divider").isEqualTo(second.getX() + second.getWidth() + 1);
                BufferedImage image = MockProfile.print(strip, 800, owner.tabHeight());
                var palette = themes.current().chrome().semantic();
                int bottom = 2 * owner.tabHeight() - 1;
                assertThat(MockProfile.at(image, 2 * (second.getX() + 20), bottom - 3)).as("the active tab's underline")
                    .isEqualTo(palette.color(SemanticColor.TAB_INDICATOR));
                assertThat(MockProfile.at(image, 2 * (second.getX() + 20), 10)).as("the active tab on surface.card")
                    .isEqualTo(palette.color(SemanticColor.SURFACE_CARD));
                assertThat(MockProfile.at(image, 2 * (first.getX() + 2), 10)).as("an inactive tab on the strip")
                    .isEqualTo(palette.color(SemanticColor.SURFACE_TABSTRIP));
                assertThat(MockProfile.at(image, 2 * first.getWidth(), 10)).as("the divider")
                    .isEqualTo(palette.color(SemanticColor.BORDER_WINDOW));
                assertThat(MockProfile.at(image, 2 * (strip.add().getX() + 30), bottom)).as("the strip's rule")
                    .isEqualTo(palette.color(SemanticColor.BORDER_WINDOW));
            } finally { themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void manyTabsShrinkTheirLabelsAndKeepTheirClose() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                for (int i = 0; i < 5; i++) { owner.newTab(DesktopTestSupport.HOME); owner.currentTab().rename("ssh build-" + i + " tail -f /var/log/app.log"); }
                owner.update();
                var strip = tabs(owner, 600);
                var last = strip.entries().getLast();
                assertThat(last.getX() + last.getWidth()).as("every tab fits").isLessThanOrEqualTo(600 - strip.add().getWidth());
                assertThat(strip.add().isVisible()).isTrue();
                for (var entry : strip.entries()) assertThat(entry.closeBounds().width).as("the x stays").isPositive();
                assertThat(ClassicTabs.cap(new int[] {40, 200, 200}, 300, 24)).as("the long labels shorten first").isEqualTo(130);
                assertThat(ClassicTabs.cap(new int[] {40, 200, 200}, 30, 24)).as("never below the minimum").isEqualTo(24);
                int before = owner.tabStrip().getTabCount();
                strip.entries().get(0).closeClicked();
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(before - 1);
            } finally { themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void theDefaultLookKeepsTheEqualTabs() throws Exception {
        edt(() -> {
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), dev.jasper.app.appearance.ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            assertThat(owner.tabBar()).isInstanceOf(WindowTabs.class).isSameAs(owner.windowTabs());
        });
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ClassicTabsTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol … ClassicTabs" and "… tabBar()".

- [ ] **Step 3: `TabStrip` and `ClassicTabs`**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/TabStrip.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.config.AnimationSettings;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

/**
 * A window's editor tab strip over its {@code TerminalDeck} (Enterprise presentation spec 10.5): today's equal tabs or
 * the classic tabs. Both drive the window's own select, close, menu and reorder callbacks. EDT only.
 */
sealed interface TabStrip extends AutoCloseable permits WindowTabs, ClassicTabs {
    /** The component mounted above the deck. */
    JComponent component();
    /** Takes the deck's tabs, titles, selection and states. */
    void refresh();
    /** Whether the window is active. */
    void setActive(boolean active);
    /** The configured animations; classic tabs never move. */
    void setAnimations(AnimationSettings settings);
    /** Every tab, as a menu. */
    JPopupMenu tabList();
    /** Releases the tabs. */
    @Override void close();
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/ClassicTabs.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * Classic editor tabs (spec 10.5; the Workstation mocks): always visible, content-sized, each with a status dot, its
 * label and a persistent ×, a "+" after the last one; the active tab on {@code surface.card} with a 2 px
 * {@code tab.indicator} underline; 1 px {@code border.window} dividers and the strip's rule on {@code surface.tabstrip}.
 * Never moves.
 */
final class ClassicTabs extends JPanel implements TabStrip {
    private final WindowContent owner;
    private final List<Entry> entries = new ArrayList<>();
    private final Add add = new Add();
    private Component selected;
    private boolean active = true, disposed;

    ClassicTabs(WindowContent owner) {
        super(null);
        this.owner = owner;
        setName("classicTabs");
        setOpaque(true);
        add(add);
    }

    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    @Override public JComponent component() { return this; }

    @Override public void refresh() {
        if (disposed) return;
        var current = new ArrayList<Component>();
        for (int i = 0; i < owner.tabStrip().getTabCount(); i++) current.add(owner.tabStrip().getComponentAt(i));
        if (!current.equals(entries.stream().map(entry -> entry.tab).toList())) {
            entries.forEach(this::remove);
            entries.clear();
            for (Component tab : current) { var entry = new Entry(tab); entries.add(entry); add(entry); }
        }
        selected = owner.selectedComponent();
        for (Entry entry : entries) entry.refresh();
        setBackground(ThemeZone.color(this, SemanticColor.SURFACE_TABSTRIP));
        revalidate(); repaint();
    }

    @Override public void setActive(boolean value) { active = value; refresh(); }
    @Override public void setAnimations(AnimationSettings settings) { }

    @Override public JPopupMenu tabList() {
        var menu = new JPopupMenu();
        for (Entry entry : entries) {
            var item = new JCheckBoxMenuItem(owner.tabTitle(entry.tab), owner.tabIcon(entry.tab), entry.tab == selected);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { if (!disposed) owner.selectTab(entry.tab); });
            menu.add(item);
        }
        NativeStyles.installed().menuTree(menu, FlatLafInstaller::installedTheme);
        return menu;
    }

    @Override public void close() { disposed = true; entries.clear(); removeAll(); selected = null; }

    List<Entry> entries() { return List.copyOf(entries); }
    JComponent add() { return add; }

    @Override public Dimension getPreferredSize() { return new Dimension(0, UIScale.scale(owner.tabHeight())); }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    @Override public void doLayout() {
        int height = Math.max(0, getHeight() - metric(DesignMetrics.Role.HAIRLINE)), divider = metric(DesignMetrics.Role.HAIRLINE);
        int addWidth = 2 * metric(DesignMetrics.Role.CLASSIC_TAB_PLUS_INSET) + metric(DesignMetrics.Role.CHROME_GLYPH);
        int[] labels = new int[entries.size()];
        int labelRoom = Math.max(0, getWidth() - addWidth);
        for (int i = 0; i < labels.length; i++) { labels[i] = entries.get(i).labelWidth(); labelRoom -= entries.get(i).chromeWidth() + divider; }
        // Too many tabs: the longest labels shorten first, down to the minimum; past that the strip clips.
        int cap = cap(labels, labelRoom, metric(DesignMetrics.Role.CLASSIC_TAB_MIN_LABEL)), x = 0;
        for (int i = 0; i < labels.length; i++) {
            int width = entries.get(i).chromeWidth() + Math.min(labels[i], cap);
            entries.get(i).setBounds(x, 0, width, height);
            x += width + divider;
        }
        add.setBounds(x, 0, addWidth, height);
    }

    /** The largest label width, at least {@code minimum}, at which every label cut to it fits {@code room}. */
    static int cap(int[] labels, int room, int minimum) {
        long total = 0;
        int max = 0;
        for (int label : labels) { total += label; max = Math.max(max, label); }
        if (total <= room) return max;
        int low = Math.min(minimum, max), high = max;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            long sum = 0;
            for (int label : labels) sum += Math.min(label, middle);
            if (sum <= room) low = middle; else high = middle - 1;
        }
        return low;
    }

    @Override protected void paintComponent(Graphics graphics) {
        var palette = ThemeZone.theme(this).semantic();
        graphics.setColor(palette.color(SemanticColor.SURFACE_TABSTRIP));
        graphics.fillRect(0, 0, getWidth(), getHeight());
        int line = metric(DesignMetrics.Role.HAIRLINE), indicator = metric(DesignMetrics.Role.TAB_INDICATOR);
        for (Entry entry : entries) {
            Rectangle at = entry.getBounds();
            if (entry.tab == selected) {
                graphics.setColor(palette.color(SemanticColor.SURFACE_CARD));
                graphics.fillRect(at.x, at.y, at.width, at.height);
                graphics.setColor(palette.color(SemanticColor.TAB_INDICATOR));
                graphics.fillRect(at.x, at.y + at.height - indicator, at.width, indicator);
            }
            graphics.setColor(palette.color(SemanticColor.BORDER_WINDOW));
            graphics.fillRect(at.x + at.width, 0, line, at.height);
        }
        graphics.setColor(palette.color(SemanticColor.BORDER_WINDOW));
        graphics.fillRect(0, getHeight() - line, getWidth(), line);
    }

    /** One tab: its dot, its label and its ×, which a click closes. */
    final class Entry extends JComponent {
        private final Component tab;
        private boolean closeHover;
        private Point origin;

        Entry(Component tab) {
            this.tab = tab;
            setFont(ThemeZone.font(Typography.Role.BODY));
            var mouse = new MouseAdapter() {
                private void popup(MouseEvent event) { owner.tabMenu(tab).show(Entry.this, event.getX(), event.getY()); }
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (disposed) return;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (SwingUtilities.isMiddleMouseButton(event)) { owner.closeTab(tab); return; }
                    if (!SwingUtilities.isLeftMouseButton(event)) return;
                    if (closeBounds().contains(event.getPoint())) { closeClicked(); return; }
                    owner.selectTab(tab);
                    origin = SwingUtilities.convertPoint(Entry.this, event.getPoint(), ClassicTabs.this);
                }
                @Override public void mouseReleased(MouseEvent event) {
                    if (event.isPopupTrigger()) { popup(event); return; }
                    Point start = origin; origin = null;
                    if (disposed || start == null) return;
                    Point end = SwingUtilities.convertPoint(Entry.this, event.getPoint(), ClassicTabs.this);
                    if (start.distance(end) <= metric(DesignMetrics.Role.TAB_REORDER_THRESHOLD)) return;
                    for (int i = 0; i < entries.size(); i++)
                        if (entries.get(i).getBounds().contains(end)) { owner.reorderTab(owner.tabStrip().indexOfComponent(tab), i); break; }
                }
                @Override public void mouseMoved(MouseEvent event) {
                    boolean over = closeBounds().contains(event.getPoint());
                    if (over != closeHover) { closeHover = over; repaint(); }
                }
                @Override public void mouseExited(MouseEvent event) { if (closeHover) { closeHover = false; repaint(); } }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
        }

        void refresh() {
            String title = owner.tabTitle(tab);
            setToolTipText(owner.tabTooltip(tab));
            getAccessibleContext().setAccessibleName(title);
            setFont(ThemeZone.font(Typography.Role.BODY));
            repaint();
        }

        private boolean dotted() { return tab instanceof TerminalTab; }
        private boolean running() { return tab instanceof TerminalTab terminal && terminal.focusedPane() != null && terminal.focusedPane().running(); }
        int labelWidth() { return getFontMetrics(getFont()).stringWidth(owner.tabTitle(tab)); }
        /** Everything but the label: the leading inset, the dot and its gap, the gap, the ×, the trailing inset. */
        int chromeWidth() {
            int dot = dotted() ? metric(DesignMetrics.Role.STATUS_DOT) + metric(DesignMetrics.Role.CLASSIC_TAB_GAP) : 0;
            return metric(DesignMetrics.Role.CLASSIC_TAB_LEADING) + dot + metric(DesignMetrics.Role.CLASSIC_TAB_GAP)
                + metric(DesignMetrics.Role.CHROME_GLYPH) + metric(DesignMetrics.Role.CLASSIC_TAB_TRAILING);
        }
        int naturalWidth() { return chromeWidth() + labelWidth(); }

        /** Where the × is, in this tab. */
        Rectangle closeBounds() {
            int size = metric(DesignMetrics.Role.CHROME_GLYPH), x = getWidth() - metric(DesignMetrics.Role.CLASSIC_TAB_TRAILING) - size;
            return new Rectangle(x, (getHeight() - size) / 2, size, size);
        }

        void closeClicked() { if (!disposed) owner.closeTab(tab); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                FlatUIUtils.setRenderingHints(g);
                var palette = ThemeZone.theme(this).semantic();
                SemanticColor ground = tab == selected ? SemanticColor.SURFACE_CARD : SemanticColor.SURFACE_TABSTRIP;
                int x = metric(DesignMetrics.Role.CLASSIC_TAB_LEADING), middle = getHeight() / 2;
                if (dotted()) {
                    int dot = metric(DesignMetrics.Role.STATUS_DOT);
                    g.setColor(SurfaceContrast.on(palette, running() ? SemanticColor.STATUS_SUCCESS : SemanticColor.STATUS_IDLE, ground, SurfaceContrast.INDICATOR));
                    g.fillOval(x, middle - dot / 2, dot, dot);
                    x += dot + metric(DesignMetrics.Role.CLASSIC_TAB_GAP);
                }
                Rectangle close = closeBounds();
                g.setFont(getFont());
                FontMetrics metrics = g.getFontMetrics();
                int room = Math.max(0, close.x - metric(DesignMetrics.Role.CLASSIC_TAB_GAP) - x);
                Color ink = SurfaceContrast.on(palette, active ? SemanticColor.TEXT_PRIMARY : SemanticColor.TEXT_SECONDARY, ground, SurfaceContrast.TEXT);
                g.setColor(ink);
                FlatUIUtils.drawString(this, g, TitleSearchField.clip(owner.tabTitle(tab), metrics, room), x, (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
                g.setColor(closeHover ? ink : palette.color(SemanticColor.TEXT_PLACEHOLDER));
                g.setStroke(new java.awt.BasicStroke(Math.max(1f, UIScale.scale(1.2f))));
                g.drawLine(close.x, close.y, close.x + close.width - 1, close.y + close.height - 1);
                g.drawLine(close.x, close.y + close.height - 1, close.x + close.width - 1, close.y);
            } finally { g.dispose(); }
        }
    }

    /** The "+" after the last tab: a new tab. */
    final class Add extends JComponent {
        Add() {
            setToolTipText("New Tab");
            getAccessibleContext().setAccessibleName("New Tab");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) { if (!disposed && SwingUtilities.isLeftMouseButton(event)) owner.invoke(ActionId.NEW_TAB); }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            var palette = ThemeZone.theme(this).semantic();
            int size = metric(DesignMetrics.Role.CHROME_GLYPH), stroke = Math.max(1, UIScale.scale(1));
            int x = metric(DesignMetrics.Role.CLASSIC_TAB_PLUS_INSET), middle = getHeight() / 2;
            graphics.setColor(SurfaceContrast.on(palette, SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_TABSTRIP, SurfaceContrast.TEXT));
            graphics.fillRect(x, middle - stroke / 2, size, stroke);
            graphics.fillRect(x + (size - stroke) / 2, middle - size / 2, stroke, size);
        }
    }
}
```

The `Entry` geometry in the test (12, dot 7, 9, label, 9, × 7, 10) is `chromeWidth()` for a terminal tab with
`STATUS_DOT` 7 (the classic catalog's `status_dot` token).

- [ ] **Step 4: `WindowTabs` implements the strip; the window mounts either**

```bash
python3 -I - <<'PY'
"""Plan 6b Task 4 step 4: WindowTabs is the equal TabStrip; WindowContent mounts the look's strip."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WindowTabs.java", [
    ('''final class WindowTabs extends JPanel implements AutoCloseable {''', '''final class WindowTabs extends JPanel implements TabStrip {''', 1),
    ('''    void refresh() {
        if (disposed) return;
        long now = clock.getAsLong(); sample(now);''', '''    @Override public JComponent component() { return this; }

    @Override public void refresh() {
        if (disposed) return;
        long now = clock.getAsLong(); sample(now);''', 1),
    ('''    void setActive(boolean active) { this.active = active; refresh(); }''', '''    @Override public void setActive(boolean active) { this.active = active; refresh(); }''', 1),
    ('''    JPopupMenu tabList() {''', '''    @Override public JPopupMenu tabList() {''', 1),
    ('''    void setAnimations(AnimationSettings settings) {''', '''    @Override public void setAnimations(AnimationSettings settings) {''', 1)])
edit(W + "WindowContent.java", [
    ('''    private final WindowTabs windowTabs;''', '''    /** The look's editor tabs: the equal tabs, or the classic ones (spec 10.5). */
    private final TabStrip windowTabs;''', 1),
    ('''        windowTabs = new WindowTabs(this);''', '''        windowTabs = look.tabs(this);''', 1),
    ('''terminalColumn.add(windowTabs, BorderLayout.NORTH);''', '''terminalColumn.add(windowTabs.component(), BorderLayout.NORTH);''', 1),
    ('''    WindowTabs windowTabs() { return windowTabs; }''',
     '''    /** The equal tabs; the default look's tests read them. */
    WindowTabs windowTabs() { return (WindowTabs) windowTabs; }
    TabStrip tabBar() { return windowTabs; }''', 1),
    ('''            WorkspaceTheme.apply(windowTabs, theme);''', '''            WorkspaceTheme.apply(windowTabs.component(), theme);''', 1),
    ('''        if (windowTabs != null) { windowTabs.revalidate(); windowTabs.repaint(); }''',
     '''        if (windowTabs != null) { windowTabs.component().revalidate(); windowTabs.component().repaint(); }''', 1)])
edit(W + "WorkspaceChrome.java", [
    ('''    public static final WorkspaceChrome STANDARD = new WorkspaceChrome(new StandardMenuBar(), new TitleFormat("{context}"), UnifiedHeader::new);''',
     '''    public static final WorkspaceChrome STANDARD = new WorkspaceChrome(new StandardMenuBar(), new TitleFormat("{context}"), UnifiedHeader::new,
        WindowTabs::new);''', 1),
    ('''    private final java.util.function.Supplier<HeaderComposition> header;

    private WorkspaceChrome(MenuBarLayout menus, TitleFormat title, java.util.function.Supplier<HeaderComposition> header) {''',
     '''    private final java.util.function.Supplier<HeaderComposition> header;
    private final java.util.function.Function<WindowContent, TabStrip> tabs;

    private WorkspaceChrome(MenuBarLayout menus, TitleFormat title, java.util.function.Supplier<HeaderComposition> header,
                            java.util.function.Function<WindowContent, TabStrip> tabs) {
        this.tabs = Objects.requireNonNull(tabs);''', 1),
    ('''        return new WorkspaceChrome(menus, title, header);''',
     '''        java.util.function.Function<WindowContent, TabStrip> tabs = switch (look.tabs()) {
            case EQUAL -> WindowTabs::new;
            case CLASSIC -> ClassicTabs::new;
        };
        return new WorkspaceChrome(menus, title, header, tabs);''', 1),
    ('''    HeaderComposition header() { return header.get(); }''', '''    HeaderComposition header() { return header.get(); }
    /** The tab strip for {@code owner}. */
    TabStrip tabs(WindowContent owner) { return tabs.apply(owner); }''', 1)])
PY
grep -n 'windowTabs\.' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java
```

Expected: every remaining `windowTabs.` call is a `TabStrip` member (`refresh`, `setActive`, `setAnimations`, `close`,
`tabList`, `component`); if one is a Swing call (`getHeight`, `isVisible`), add `.component()` before it. `WindowTabs`
gets `import javax.swing.JComponent` through its existing `javax.swing.*`. `WindowContent.invoke(ActionId)` is the
existing dispatch the tests use; if it is named otherwise, use the name `WindowChromeContributionsTest` calls.

Add the ledger rows:

```bash
python3 -I - <<'PY'
import pathlib
W, T = "jasper-app/src/main/java/dev/jasper/app/workspace/", "jasper-app/src/test/java/dev/jasper/app/workspace/ClassicTabsTest.java"
rows = [(W + "TabStrip.java", "TabStrip", "New in Enterprise presentation Plan 6b Task 4; a window's editor tab strip"),
        (W + "ClassicTabs.java", "ClassicTabs", "New in Enterprise presentation Plan 6b Task 4; content-sized classic tabs"),
        (T, "ClassicTabsTest", "Test/fixture ownership; New in Enterprise presentation Plan 6b Task 4; classic tabs' geometry and roles")]
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
text += "" if text.endswith("\n") else "\n"
ledger.write_text(text + "".join(f"{p},app.workspace,{c},nonvisual-helper,none,none,{T},{d}\n" for p, c, d in rows), encoding="utf-8")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:javadoc`
Expected: PASS: `ClassicTabsTest` 4 and every existing tab test (`WindowTabsTest`, `FolderTabScaleTest`, `MockUiTest`,
`WindowPanelsTest`) under the standard chrome.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/test/java/dev/jasper/app/workspace/ClassicTabsTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Window content: classic tabs

TabStrip has two implementations over the same deck callbacks:
WindowTabs, today's equal tabs, and ClassicTabs, the mocks' editor tabs:
always visible, content-sized, a status dot, the label and a persistent
close, a + after the last tab, the active tab on surface.card with a 2 px
tab.indicator underline, border.window dividers and rule, and no motion.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: Ruled status cells and the run item

`StatusCells` has two implementations over `WindowStatusBar`'s one model (spec 10.6): `SlashSegments` (today's layout and
paint, moved) and `RuledCells`: cells divided by 1 px `border.divider` rules on `surface.app` under a `border.window`
rule; the session cell (dot and name) and the folder cell on the left, contributed items as cells, then the grid size
(data face), "UTF-8" and, only while there is a problem, the configuration cell on `warning.ground` or `danger.ground`
with "▲" (Rulings 8). With the run item either layout leads with "▶ Run…", which runs COMMAND_PALETTE and shows on
`surface.control.selected` while the palette is open (Rulings 9).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/StatusCells.java`, `SlashSegments.java`, `RuledCells.java`, `RunItem.java`
- Modify (whole file): `jasper-app/src/main/java/dev/jasper/app/workspace/WindowStatusBar.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java:38`, `WorkspaceChrome.java`, `WindowCommandPalette.java` (the open and close hook), `WindowContent.java` (`paletteChanged`), `WindowTitle.java` (`sessionCell`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/RuledCellsTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (rows below)

**Interfaces:**
- Consumes: 6a's `WindowTitle.directory`, `session`; `WindowCommandPalette.isOpen()`; the COMMAND_PALETTE action.
- Produces: `sealed interface StatusCells permits SlashSegments, RuledCells` with `void install(WindowStatusBar)`,
  `void layout(WindowStatusBar)`, `void paint(WindowStatusBar, Graphics)`, `void refresh(WindowStatusBar)`, `int
  itemGap()`; `final class RunItem extends JComponent` with `RunItem(Action open, BooleanSupplier active)`;
  `WindowStatusBar(StatusCells cells, RunItem run)` (the no-argument constructor stays: slash segments, no run item);
  `WindowStatusBar.runItem()`, `cells()`; `WorkspaceChrome.statusBar(Action open, BooleanSupplier paletteOpen)`;
  `WindowContent.paletteChanged()`; `static String WindowTitle.sessionCell(PaneSnapshot)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/RuledCellsTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractAction;
import org.junit.jupiter.api.Test;

/** Spec 10.6 and the Workstation and Run Dialog mocks: ruled status cells and the run item. */
class RuledCellsTest {
    static PaneSnapshot local(String shell) {
        return new PaneSnapshot("", Optional.of(DesktopTestSupport.HOME), 130, 69, true, PaneSnapshot.State.RUNNING, OptionalInt.empty(),
            Optional.empty(), Optional.empty(), shell);
    }

    static ConfigService.State state(ConfigDiagnostic.Severity severity) {
        Path file = Path.of("config.toml");
        var diagnostics = severity == null ? List.<ConfigDiagnostic>of() : List.of(new ConfigDiagnostic(severity, file, 12, 1, "x", "bad"));
        return new ConfigService.State(ConfigSnapshot.defaults(), diagnostics, file, true);
    }

    @Test void theCellsAreTheMocks() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var opened = new AtomicInteger();
                var open = new AbstractAction("Command Palette") { @Override public void actionPerformed(ActionEvent event) { opened.incrementAndGet(); } };
                var bar = new WindowStatusBar(new RuledCells(), new RunItem(open, () -> false));
                bar.setMetadata(local("Mac"), "130 × 69", true);
                var model = new Contributions();
                var vault = model.addStatus("dev.x.vault", false, 10); vault.setText("Vault: Locked");
                var ssh = model.addStatus("dev.x.ssh", false, 20); ssh.setText("1 SSH session");
                bar.setContributed(model.status(), id -> null);
                bar.setConfiguration(state(ConfigDiagnostic.Severity.WARNING));
                var image = MockProfile.print(bar, 1440, 24);
                var palette = themes.current().chrome().semantic();
                var theme = themes.current().chrome();
                assertThat(MockProfile.at(image, 400, 1)).as("the top rule, 1 px (2 rows at 2x)").isEqualTo(palette.color(SemanticColor.BORDER_WINDOW));
                assertThat(MockProfile.at(image, 400, 24)).as("surface.app").isEqualTo(palette.color(SemanticColor.SURFACE_APP));
                var rules = MockProfile.solid(MockProfile.row(image, 30, 0, 2880), 1).stream()
                    .filter(run -> run.length() == 2 && run.color().equals(DerivedColor.BORDER_DIVIDER.color(theme))).map(MockProfile.Run::start).toList();
                assertThat(rules).as("a rule after the run, session and folder cells and before each right cell").hasSize(3 + 5);
                assertThat(MockProfile.at(image, 2860, 30)).as("the warning cell reaches the edge").isEqualTo(DerivedColor.WARNING_GROUND.color(theme));
                assertThat(bar.configButton().getText()).isEqualTo("▲ Config warnings (line 12)");
                bar.runItem().click();
                assertThat(opened).as("the run item opens the palette").hasValue(1);
                bar.setConfiguration(state(null));
                assertThat(bar.configButton().isVisible()).as("no problem, no cell").isFalse();
            } finally { themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void theConfigurationCellShowsOnlyWhileThereIsAProblem() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var bar = new WindowStatusBar(new RuledCells(), null);
                var details = new AtomicInteger();
                bar.onConfigurationDetails = details::incrementAndGet;
                bar.setConfiguration(state(ConfigDiagnostic.Severity.ERROR));
                assertThat(bar.configButton().isVisible()).isTrue();
                assertThat(bar.configButton().getText()).startsWith("▲ Config error");
                bar.configButton().doClick();
                assertThat(details).hasValue(1);
                bar.setConfiguration(state(ConfigDiagnostic.Severity.WARNING));
                assertThat(bar.configButton().getText()).startsWith("▲ Config warnings");
                bar.setConfiguration(state(null));
                assertThat(bar.configButton().isVisible()).isFalse();
            } finally { themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void theRunItemShowsWhileThePaletteIsOpen() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var open = new AtomicBoolean();
                var run = new RunItem(new AbstractAction("Command Palette") { @Override public void actionPerformed(ActionEvent event) { open.set(true); } }, open::get);
                var bar = new WindowStatusBar(new SlashSegments(), run);
                bar.setMetadata(local("zsh"), "80 × 24", true);
                var closed = MockProfile.print(bar, 800, 24);
                open.set(true);
                var shown = MockProfile.print(bar, 800, 24);
                int x = 2 * (run.getX() + 2), y = 24;
                assertThat(MockProfile.at(shown, x, y)).as("the active toggle")
                    .isEqualTo(themes.current().chrome().semantic().color(SemanticColor.SURFACE_CONTROL_SELECTED));
                assertThat(MockProfile.at(closed, x, y)).isNotEqualTo(MockProfile.at(shown, x, y));
                assertThat(run.getX()).as("it leads the bar").isZero();
            } finally { themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void aLocalPaneIsNamedByItsShellAndARemoteOneByItsHost() {
        assertThat(WindowTitle.sessionCell(local("zsh"))).isEqualTo("zsh");
        var provided = new PaneSnapshot("", Optional.empty(), 80, 24, true, PaneSnapshot.State.RUNNING, OptionalInt.empty(),
            Optional.of("dev.jasper.remote"), Optional.empty(), "Mac");
        assertThat(WindowTitle.sessionCell(provided)).isEqualTo("Mac");
        assertThat(WindowTitle.sessionCell(null)).isEmpty();
    }
}
```

The rule count: the run, session and folder cells each end in a rule (3); the two plugin cells, the grid size,
"UTF-8" and the configuration cell each start with one (5).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.RuledCellsTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol … RuledCells", "… RunItem", "… sessionCell".

- [ ] **Step 3: The strategies and the run item**

Check first that no earlier plan changed the status bar: `git diff deafed424 -- jasper-app/src/main/java/dev/jasper/app/workspace/WindowStatusBar.java`
prints nothing (if it prints a change, carry that change into the files below and record it in the banner).

Create `jasper-app/src/main/java/dev/jasper/app/workspace/StatusCells.java`:

```java
package dev.jasper.app.workspace;

import java.awt.Graphics;

/**
 * How a window's status bar lays out and paints its one model (Enterprise presentation spec 10.6): today's slash
 * segments or ruled cells. One instance per bar. EDT only.
 */
sealed interface StatusCells permits SlashSegments, RuledCells {
    /** Adds the bar's children this layout shows. */
    void install(WindowStatusBar bar);
    /** Lays the children out. */
    void layout(WindowStatusBar bar);
    /** Paints what the children do not. */
    void paint(WindowStatusBar bar, Graphics graphics);
    /** Takes the bar's current texts, states and theme. */
    void refresh(WindowStatusBar bar);
    /** The space between two contributed items, in device pixels. */
    int itemGap();
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/SlashSegments.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.Graphics;

/** Today's status bar: the shell and its folder, then contributed items, and the grid size with the configuration, slash-separated. */
final class SlashSegments implements StatusCells {
    @Override public void install(WindowStatusBar bar) {
        bar.add(bar.left); bar.add(bar.leftItems); bar.add(bar.rightItems); bar.add(bar.right); bar.add(bar.contentItems);
    }

    @Override public void layout(WindowStatusBar bar) {
        int width = bar.getWidth(), height = bar.getHeight();
        int lead = bar.runItem() == null ? 0 : bar.runItem().getPreferredSize().width;
        if (bar.runItem() != null) bar.runItem().setBounds(0, 0, Math.min(width, lead), height);
        int edge = Math.min(NativeStyles.installed().metric(DesignMetrics.Role.STATUS_GAP), width / 2);
        int leftInset = Math.min(width, lead + NativeStyles.installed().metric(DesignMetrics.Role.STATUS_LEADING));
        int gap = NativeStyles.installed().metric(DesignMetrics.Role.STATUS_GAP);
        int available = Math.max(0, width - edge * 2);
        int rightWidth = Math.min(available, bar.right.getPreferredSize().width);
        bar.right.setBounds(Math.max(edge, width - edge - rightWidth), 0, rightWidth, height);
        int rightEdge = bar.right.getX();
        int documentWidth = Math.min(bar.contentItems.getPreferredSize().width, Math.max(0, (rightEdge - leftInset) * 2 / 3));
        bar.contentItems.setBounds(Math.max(leftInset, rightEdge - documentWidth), 0, documentWidth, height);
        if (documentWidth > 0) rightEdge -= documentWidth + gap;
        int remaining = Math.max(0, rightEdge - leftInset - edge);
        int rightItemsWidth = bar.rightItems.getComponentCount() == 0 ? 0 : Math.min(bar.rightItems.getPreferredSize().width, remaining / 3);
        int rightItemsSpace = rightItemsWidth == 0 ? 0 : rightItemsWidth + gap;
        // An empty row collapses at the origin: no child may ever extend past the bar, however narrow it is.
        if (rightItemsWidth == 0) bar.rightItems.setBounds(0, 0, 0, 0);
        else bar.rightItems.setBounds(rightEdge - rightItemsSpace, 0, rightItemsWidth, height);
        remaining -= rightItemsSpace;
        int leftItemsWidth = bar.leftItems.getComponentCount() == 0 ? 0 : Math.min(bar.leftItems.getPreferredSize().width, remaining / 4);
        int leftItemsSpace = leftItemsWidth == 0 ? 0 : leftItemsWidth + gap;
        int leftWidth = Math.max(0, Math.min(bar.left.getPreferredSize().width, remaining - leftItemsSpace));
        if (leftItemsWidth == 0) leftWidth = Math.max(0, remaining);
        bar.left.setBounds(leftInset, 0, leftWidth, height);
        if (leftItemsWidth == 0) bar.leftItems.setBounds(0, 0, 0, 0);
        else bar.leftItems.setBounds(Math.min(leftInset + leftWidth + gap, Math.max(0, width - leftItemsWidth)), 0, leftItemsWidth, height);
    }

    @Override public void paint(WindowStatusBar bar, Graphics graphics) {
        if (bar.showingContent()) return;
        // The run item, when there is one, leads the bar: the dot keeps its place after it.
        int lead = bar.runItem() == null ? 0 : bar.runItem().getWidth();
        if (lead == 0) { dev.jasper.ui.swing.ChromePainter.installed().statusDot(bar, graphics, ThemeZone.theme(null), bar.running()); return; }
        Graphics shifted = graphics.create();
        try {
            shifted.translate(lead, 0);
            dev.jasper.ui.swing.ChromePainter.installed().statusDot(bar, shifted, ThemeZone.theme(null), bar.running());
        } finally { shifted.dispose(); }
    }

    @Override public void refresh(WindowStatusBar bar) { }

    @Override public int itemGap() { return NativeStyles.installed().metric(DesignMetrics.Role.STATUS_GAP); }
}
```

`SlashSegments.layout` is today's `WindowStatusBar.doLayout` with `bar.` before the fields and the run item's width
added to the leading inset, and `paint` shifts the running dot past the run item: without a run item (`lead` 0) both
run exactly today's code.

Create `jasper-app/src/main/java/dev/jasper/app/workspace/RunItem.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The status bar's leading "▶ Run…" (Enterprise presentation spec 10.6): an accent item that opens the palette's current
 * view, shown as an active toggle on {@code surface.control.selected} while the palette is open.
 */
final class RunItem extends JComponent {
    static final String LABEL = "Run…";
    private final Action open;
    private final BooleanSupplier active;

    RunItem(Action open, BooleanSupplier active) {
        this.open = Objects.requireNonNull(open); this.active = Objects.requireNonNull(active);
        setName("runItem");
        setToolTipText("Run (the command palette)");
        getAccessibleContext().setAccessibleName("Run");
        setFont(ThemeZone.font(Typography.Role.STATUS_TEXT));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (SwingUtilities.isLeftMouseButton(event)) click(); }
        });
    }

    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    /** Runs the palette's opener, as a click does. */
    void click() { if (open.isEnabled()) open.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "runItem")); }

    @Override public Dimension getPreferredSize() {
        return new Dimension(2 * metric(DesignMetrics.Role.STATUS_CELL_PADDING) + metric(DesignMetrics.Role.CHROME_GLYPH)
            + metric(DesignMetrics.Role.RUN_GLYPH_GAP) + getFontMetrics(getFont()).stringWidth(LABEL), metric(DesignMetrics.Role.BUTTON_HEIGHT));
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            FlatUIUtils.setRenderingHints(g);
            var theme = ThemeZone.theme(this);
            boolean shown = active.getAsBoolean();
            SemanticColor ground = shown ? SemanticColor.SURFACE_CONTROL_SELECTED : SemanticColor.SURFACE_APP;
            int top = getParent() instanceof WindowStatusBar bar && bar.cells() instanceof RuledCells ? metric(DesignMetrics.Role.HAIRLINE) : 0;
            if (shown) { g.setColor(theme.semantic().color(ground)); g.fillRect(0, top, getWidth(), getHeight() - top); }
            g.setColor(SurfaceContrast.painted(theme, new PaintPair(DerivedColor.ACCENT_EDGE, ground, SurfaceContrast.Guard.TEXT)));
            int x = metric(DesignMetrics.Role.STATUS_CELL_PADDING), size = metric(DesignMetrics.Role.CHROME_GLYPH), middle = top + (getHeight() - top) / 2;
            g.fill(new Polygon(new int[] {x, x + size, x}, new int[] {middle - size / 2, middle, middle + size / 2}, 3));
            x += size + metric(DesignMetrics.Role.RUN_GLYPH_GAP);
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            FlatUIUtils.drawString(this, g, LABEL, x, top + (getHeight() - top - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally { g.dispose(); }
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/RuledCells.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * Ruled status cells (spec 10.6; the Workstation mocks): on {@code surface.app} under a 1 px {@code border.window}
 * rule, cells divided by 1 px {@code border.divider} rules: the run item, the session (its dot and name) and the
 * folder on the left; contributed items as cells; then the grid size in the data face, "UTF-8" and, while there is a
 * problem, the configuration on {@code warning.ground} or {@code danger.ground} with "▲" to the right edge.
 */
final class RuledCells implements StatusCells {
    private final JLabel session = label("statusSession"), folder = label("statusFolder"), grid = label("statusGrid"), encoding = label("statusEncoding");
    /** Where each cell rule is, in the bar. */
    private final List<Integer> rules = new ArrayList<>();
    private int sessionDotX = -1;

    private static JLabel label(String name) { var label = new JLabel(); label.setName(name); label.putClientProperty("html.disable", true); return label; }
    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    @Override public void install(WindowStatusBar bar) {
        encoding.setText("UTF-8");
        bar.add(session); bar.add(folder); bar.add(bar.leftItems); bar.add(bar.rightItems); bar.add(bar.contentItems);
        bar.add(grid); bar.add(encoding); bar.add(bar.configButton());
    }

    @Override public int itemGap() { return 2 * metric(DesignMetrics.Role.STATUS_CELL_PADDING) + metric(DesignMetrics.Role.HAIRLINE); }

    @Override public void refresh(WindowStatusBar bar) {
        var theme = ThemeZone.theme(null);
        var palette = theme.semantic();
        Color text = SurfaceContrast.on(palette, SemanticColor.TEXT_STRONG_SECONDARY, SemanticColor.SURFACE_APP, SurfaceContrast.TEXT);
        session.setText(bar.session());
        folder.setText(bar.folder());
        grid.setText(bar.dimensions());
        for (JLabel label : List.of(session, folder, encoding)) { label.setFont(ThemeZone.font(Typography.Role.STATUS_TEXT)); label.setForeground(text); }
        grid.setFont(ThemeZone.font(Typography.Role.MONO)); grid.setForeground(text);
        for (var row : new JComponent[] {bar.leftItems, bar.rightItems, bar.contentItems})
            for (Component child : row.getComponents()) if (child instanceof JButton item) { item.setForeground(text); item.setFont(ThemeZone.font(Typography.Role.STATUS_TEXT)); }
        var config = bar.configButton();
        boolean problem = bar.configurationProblem() != null;
        config.setVisible(problem);
        if (problem) {
            config.setText("▲ " + bar.configurationText());
            DerivedColor ground = bar.configurationProblem() == SemanticColor.STATUS_DANGER ? DerivedColor.DANGER_GROUND : DerivedColor.WARNING_GROUND;
            config.setForeground(SurfaceContrast.painted(theme, new PaintPair(bar.configurationProblem(), ground, SurfaceContrast.Guard.TEXT)));
            config.setFont(ThemeZone.font(Typography.Role.STATUS_TEXT));
        }
        bar.revalidate(); bar.repaint();
    }

    @Override public void layout(WindowStatusBar bar) {
        rules.clear();
        int width = bar.getWidth(), top = metric(DesignMetrics.Role.HAIRLINE), height = Math.max(0, bar.getHeight() - top);
        int pad = metric(DesignMetrics.Role.STATUS_CELL_PADDING), line = metric(DesignMetrics.Role.HAIRLINE);
        int x = 0;
        if (bar.runItem() != null) {
            int run = bar.runItem().getPreferredSize().width;
            bar.runItem().setBounds(0, 0, run, bar.getHeight());
            x = run; rules.add(x); x += line;
        }
        // The right cells first, from the right edge, so the left cells take what is left.
        int right = width;
        var config = bar.configButton();
        if (config.isVisible()) {
            int cell = config.getPreferredSize().width + 2 * pad;
            config.setBounds(Math.max(x, right - cell) + pad, top, cell - 2 * pad, height);
            right -= cell; rules.add(right - line); right -= line;
        } else config.setBounds(0, 0, 0, 0);
        for (JComponent cell : List.of(encoding, grid, bar.contentItems, bar.rightItems)) {
            boolean empty = cell.getComponentCount() == 0 && !(cell instanceof JLabel) || cell instanceof JLabel label && label.getText().isEmpty();
            if (empty) { cell.setBounds(0, 0, 0, 0); continue; }
            int content = cell.getPreferredSize().width, span = content + 2 * pad;
            int start = Math.max(x, right - span);
            cell.setBounds(start + pad, top, Math.max(0, right - start - 2 * pad), height);
            right = start; rules.add(right - line); right -= line;
        }
        int dot = metric(DesignMetrics.Role.STATUS_DOT), dotGap = metric(DesignMetrics.Role.STATUS_CELL_DOT_GAP);
        if (!session.getText().isEmpty()) {
            sessionDotX = x + pad;
            int content = dot + dotGap + session.getPreferredSize().width;
            session.setBounds(x + pad + dot + dotGap, top, Math.max(0, Math.min(content, right - x - 2 * pad) - dot - dotGap), height);
            x += content + 2 * pad; rules.add(x); x += line;
        } else { sessionDotX = -1; session.setBounds(0, 0, 0, 0); }
        for (JComponent cell : List.of(folder, bar.leftItems)) {
            boolean empty = cell instanceof JLabel label ? label.getText().isEmpty() : cell.getComponentCount() == 0;
            if (empty || x >= right) { cell.setBounds(0, 0, 0, 0); continue; }
            int content = Math.min(cell.getPreferredSize().width, Math.max(0, right - x - 2 * pad));
            cell.setBounds(x + pad, top, content, height);
            x += content + 2 * pad; rules.add(x); x += line;
        }
    }

    @Override public void paint(WindowStatusBar bar, Graphics graphics) {
        var theme = ThemeZone.theme(null);
        var palette = theme.semantic();
        int line = metric(DesignMetrics.Role.HAIRLINE), height = bar.getHeight();
        graphics.setColor(palette.color(SemanticColor.SURFACE_APP));
        graphics.fillRect(0, 0, bar.getWidth(), height);
        var config = bar.configButton();
        if (config.isVisible() && bar.configurationProblem() != null) {
            DerivedColor ground = bar.configurationProblem() == SemanticColor.STATUS_DANGER ? DerivedColor.DANGER_GROUND : DerivedColor.WARNING_GROUND;
            int pad = metric(DesignMetrics.Role.STATUS_CELL_PADDING);
            graphics.setColor(ground.color(theme));
            graphics.fillRect(config.getX() - pad, line, bar.getWidth() - config.getX() + pad, height - line);
        }
        graphics.setColor(SurfaceContrast.painted(theme, new PaintPair(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_APP, SurfaceContrast.Guard.STRUCTURAL)));
        graphics.fillRect(0, 0, bar.getWidth(), line);
        graphics.setColor(DerivedColor.BORDER_DIVIDER.color(theme));
        for (int x : rules) graphics.fillRect(x, line, line, height - line);
        if (sessionDotX >= 0 && !bar.showingContent()) {
            int dot = metric(DesignMetrics.Role.STATUS_DOT);
            graphics.setColor(SurfaceContrast.on(palette, bar.running() ? SemanticColor.STATUS_SUCCESS : SemanticColor.STATUS_IDLE, SemanticColor.SURFACE_APP, SurfaceContrast.INDICATOR));
            ((java.awt.Graphics2D) graphics).setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.fillOval(sessionDotX, line + (height - line - dot) / 2, dot, dot);
        }
        // The rule between two left cells is drawn above; the cell rules of the contributed rows are their gaps.
        int gap = itemGap();
        for (var row : new JComponent[] {bar.leftItems, bar.rightItems, bar.contentItems}) {
            if (row.getWidth() == 0) continue;
            int previousEnd = -1;
            for (Component child : row.getComponents()) {
                if (!(child instanceof JButton) || !child.isVisible()) continue;
                if (previousEnd >= 0) graphics.fillRect(row.getX() + previousEnd + (gap - line) / 2, line, line, height - line);
                previousEnd = child.getX() + child.getWidth();
            }
        }
    }
}
```

- [ ] **Step 4: `WindowStatusBar` over the strategy; the palette's hook**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/workspace/WindowStatusBar.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.NativeStyles;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.StatusEntry;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.*;

/**
 * Bounded status segments: long shell/path metadata never displaces dimensions/defaults. The look's {@link StatusCells}
 * lays the model out (spec 10.6): today's slash segments or ruled cells, each led by the run item when the look has one.
 */
final class WindowStatusBar extends JPanel {
    final PathBreadcrumbs breadcrumbs = new PathBreadcrumbs();
    final Segment left = new Segment(breadcrumbs);
    private final JButton configButton = new JButton("Built-in defaults");
    final Segment right = new Segment(configButton);
    private final java.util.Map<String, JComponent> itemViews = new java.util.HashMap<>();
    final Box contentItems = Box.createHorizontalBox();
    private java.util.List<dev.jasper.app.contributions.ContentStatus> contentStatus = java.util.List.of();
    private boolean showingContent;
    final Box leftItems = Box.createHorizontalBox();
    final Box rightItems = Box.createHorizontalBox();
    Runnable onConfigurationDetails = () -> {};
    private String configText = "Built-in defaults";
    private SemanticColor configColor = SemanticColor.STATUS_SUCCESS;
    private boolean configProblem;
    private String shell = "", directory = "", dimensions = "", session = "", folder = "";
    private boolean running;
    private boolean integration;
    private String text = "Built-in defaults";
    /** Height in logical pixels; the bar is taller only when the status font needs more. */
    private int barHeight = ConfigSnapshot.DEFAULT_STATUS_BAR_HEIGHT;
    private final StatusCells cells;
    private final RunItem run;

    WindowStatusBar() { this(new SlashSegments(), null); }

    /** A bar laid out by {@code cells}, led by {@code run} when it is not null. */
    WindowStatusBar(StatusCells cells, RunItem run) {
        super(null);
        this.cells = java.util.Objects.requireNonNull(cells);
        this.run = run;
        configButton.setBorder(BorderFactory.createEmptyBorder());
        configButton.setContentAreaFilled(false); configButton.setOpaque(false);
        configButton.setEnabled(false); configButton.putClientProperty("html.disable", true);
        configButton.addActionListener(event -> onConfigurationDetails.run());
        if (run != null) add(run);
        cells.install(this);
        refreshTheme();
        getAccessibleContext().setAccessibleName("Terminal status");
        dev.jasper.app.platform.AppIcons.chromeReceiver(this);
    }

    StatusCells cells() { return cells; }
    RunItem runItem() { return run; }
    boolean showingContent() { return showingContent; }
    boolean running() { return running; }
    String session() { return session; }
    String folder() { return folder; }
    String dimensions() { return dimensions; }
    String configurationText() { return configText; }
    /** The status role of a configuration problem, or null when there is none. */
    SemanticColor configurationProblem() { return configProblem ? configColor : null; }

    /** Sets the height in logical pixels; the caller validates the range. */
    void setBarHeight(int height) {
        if (barHeight == height) return;
        barHeight = height;
        revalidate(); repaint();
    }

    void setMetadata(dev.jasper.app.terminals.PaneSnapshot pane, String dimensions, boolean running) {
        var location = PathBreadcrumbs.Location.from(pane, java.nio.file.Path.of(System.getProperty("user.home")));
        this.running = running; this.shell = pane == null ? "" : pane.shell();
        this.directory = location.path(); this.dimensions = dimensions;
        this.integration = pane != null && pane.shellIntegration();
        this.session = WindowTitle.sessionCell(pane);
        this.folder = WindowTitle.directory(pane, java.nio.file.Path.of(System.getProperty("user.home")));
        breadcrumbs.setLocation(location);
        // The remote breadcrumb already names the provider's host.
        left.setParts(pane != null && pane.providerId().isPresent() ? "" : shellLabel(), directory);
        left.setToolTipText(directory);
        left.setFirstToolTip(shell.isEmpty() ? null : integration ? "Shell integration active" : "Shell integration not detected");
        updateText();
    }

    void setContent(dev.jasper.app.contributions.ContentEntry entry) {
        showingContent = entry != null;
        var items = entry == null ? java.util.List.<dev.jasper.app.contributions.ContentStatus>of() : entry.status();
        if (!items.equals(contentStatus)) {
            contentStatus = items; contentItems.removeAll();
            for (var item : items) {
                var button = new JButton(item.text()); button.putClientProperty("html.disable", true);
                button.setToolTipText(item.tooltip()); button.setBorder(BorderFactory.createEmptyBorder(0, NativeStyles.installed().metric(DesignMetrics.Role.STATUS_ITEM_GAP), 0, NativeStyles.installed().metric(DesignMetrics.Role.STATUS_ITEM_GAP)));
                button.setContentAreaFilled(false); button.setFocusable(false); button.setFont(statusFont());
                button.setForeground(muted()); if (item.run() != null) button.addActionListener(event -> item.run().run());
                contentItems.add(button);
            }
        }
        if (entry != null) {
            var crumbs = new ArrayList<PathBreadcrumbs.Crumb>();
            if (!entry.host().isEmpty()) crumbs.add(new PathBreadcrumbs.Crumb(entry.host(), entry.host(), "SERVER"));
            String path = entry.location();
            if (!entry.host().isEmpty() && path.indexOf(":/") >= 0) path = path.substring(path.indexOf(":/") + 1);
            // A location supplied by a plugin stays text, including on remote hosts.
            String[] parts = path.replace('\\', '/').split("/");
            String built = path.startsWith("/") ? "/" : "";
            for (String part : parts) if (!part.isEmpty()) {
                built += (built.isEmpty() || built.endsWith("/") ? "" : "/") + part;
                crumbs.add(new PathBreadcrumbs.Crumb(part, built, crumbs.isEmpty() ? "FOLDER" : null));
            }
            breadcrumbs.setLocation(new PathBreadcrumbs.Location(crumbs, path));
            left.setParts("", path); dimensions = ""; folder = path; session = entry.title(); updateText();
        }
        cells.refresh(this);
        revalidate(); repaint();
    }

    void setConfiguration(ConfigService.State state) {
        boolean error = state.diagnostics().stream().anyMatch(d -> d.severity() == ConfigDiagnostic.Severity.ERROR);
        boolean warning = !state.diagnostics().isEmpty();
        configText = error ? "Config error" : warning ? "Config warnings" : state.present() ? "Config loaded" : "Built-in defaults";
        if (warning) state.diagnostics().stream().filter(d -> d.line() > 0).findFirst()
            .ifPresent(d -> configText += " (line " + d.line() + ")");
        configColor = error ? SemanticColor.STATUS_DANGER : warning ? SemanticColor.STATUS_WARNING : SemanticColor.STATUS_SUCCESS;
        configProblem = warning;
        configButton.setToolTipText(state.file().toString());
        configButton.setEnabled(true);
        updateText(); refreshTheme();
    }

    /** The shell name with its integration dot: filled once the shell has marked a prompt. */
    private String shellLabel() {
        return shell.isEmpty() ? "" : shell + (integration ? " ●" : " ○");
    }

    private void updateText() {
        right.setParts(dimensions, configText);
        configButton.getAccessibleContext().setAccessibleName(configText);
        text = shell.isEmpty() ? configText
            : shellLabel() + "  |  " + directory + "  |  " + dimensions + "  |  " + configText;
        // A screen reader would otherwise announce the dot's Unicode name; say what it means instead.
        String spoken = shell.isEmpty() ? ""
            : shell + (integration ? ", shell integration active" : ", shell integration not detected");
        getAccessibleContext().setAccessibleDescription(shell.isEmpty() ? text
            : spoken + "  |  " + directory + "  |  " + dimensions + "  |  " + configText);
        cells.refresh(this);
        revalidate(); repaint();
    }
    /** Replaces the contributed items. An item with a live action is a button; any other is inert text. */
    void setContributed(List<StatusEntry> entries, Function<String, Action> actions) {
        var leftViews = new ArrayList<JComponent>();
        var rightViews = new ArrayList<JComponent>();
        var liveIds = new java.util.HashSet<String>();
        for (StatusEntry entry : entries) {
            if (!entry.visible()) continue;
            liveIds.add(entry.id());
            JComponent view = itemViews.get(entry.id());
            if (entry.progress() != null) {
                if (!(view instanceof StatusProgressView)) view = new StatusProgressView();
                ((StatusProgressView) view).update(entry.progress(), actions);
            } else {
                if (!(view instanceof JButton)) {
                    var item = new JButton();
                    item.setBorder(BorderFactory.createEmptyBorder()); item.setContentAreaFilled(false); item.setOpaque(false);
                    item.setFocusable(false); item.putClientProperty("html.disable", true);
                    item.setIconTextGap(NativeStyles.installed().metric(DesignMetrics.Role.STATUS_ITEM_GAP));
                    view = item;
                }
                var item = (JButton) view;
                item.setText(entry.text()); item.setIcon(entry.icon()); item.setToolTipText(entry.tooltip());
                item.getAccessibleContext().setAccessibleName(entry.text().isEmpty() && entry.tooltip() != null ? entry.tooltip() : entry.text());
                for (var listener : item.getActionListeners()) item.removeActionListener(listener);
                Action action = entry.actionId() == null ? null : actions.apply(entry.actionId());
                item.setEnabled(action == null || action.isEnabled());
                item.setRolloverEnabled(action != null);
                if (action != null) item.addActionListener(event -> { if (action.isEnabled()) action.actionPerformed(event); });
            }
            itemViews.put(entry.id(), view);
            (entry.left() ? leftViews : rightViews).add(view);
        }
        itemViews.keySet().retainAll(liveIds);
        replaceStructure(leftItems, leftViews, cells.itemGap());
        replaceStructure(rightItems, rightViews, cells.itemGap());
        refreshTheme();
        revalidate(); repaint();
    }

    private static void replaceStructure(Box row, List<JComponent> views, int gap) {
        var current = java.util.Arrays.stream(row.getComponents()).filter(c -> !(c instanceof Box.Filler)).toList();
        if (current.equals(views)) return;
        row.removeAll();
        for (var view : views) {
            if (row.getComponentCount() > 0) row.add(Box.createHorizontalStrut(gap));
            row.add(view);
        }
    }

    List<JButton> contributedItems(boolean leftSide) {
        List<JButton> items = new ArrayList<>();
        for (Component child : (leftSide ? leftItems : rightItems).getComponents()) if (child instanceof JButton button) items.add(button);
        return items;
    }

    JButton configButton() { return configButton; }
    String getText() { return text; }
    static java.awt.Font statusFont() {
        return ThemeZone.font(Typography.Role.STATUS_TEXT);
    }

    /** A status color as painted on the status surface: held to the text floor there, shared role untouched. */
    private static Color onStatus(SemanticColor role) {
        return dev.jasper.ui.theme.SurfaceContrast.on(ThemeZone.theme(null).semantic(), role, SemanticColor.SURFACE_STATUS,
            dev.jasper.ui.theme.SurfaceContrast.TEXT);
    }
    Color muted() { return ThemeZone.color(null, SemanticColor.TEXT_SECONDARY); }
    private static Color surface() {
        return ThemeZone.color(null, SemanticColor.SURFACE_STATUS);
    }
    void refreshTheme() {
        setBackground(surface());
        left.refreshTheme(); right.refreshTheme(); breadcrumbs.refreshTheme();
        for (Box row : new Box[]{leftItems, rightItems, contentItems})
            for (Component child : row.getComponents()) if (child instanceof JButton item) {
                item.setForeground(muted());
                item.setFont(statusFont());
            } else if (child instanceof StatusProgressView progress) progress.refreshTheme();
        configButton.setForeground(onStatus(configColor));
        if (run != null) { run.setFont(statusFont()); run.repaint(); }
        cells.refresh(this);
    }

    final class Segment extends JPanel {
        private final JLabel first = new JLabel(), slash = new JLabel("/", SwingConstants.CENTER);
        private final JComponent last;
        Segment(JComponent last) {
            super(null); setOpaque(false); this.last = last;
            first.putClientProperty("html.disable", true); last.putClientProperty("html.disable", true);
            add(first); add(slash); add(last);
        }
        void setParts(String first, String last) {
            this.first.setText(first);
            if (this.last instanceof JLabel label) label.setText(last);
            else if (this.last instanceof JButton button && !(cells instanceof RuledCells)) button.setText(last);
            slash.setVisible(!first.isEmpty());
        }
        void setFirstToolTip(String tip) { first.setToolTipText(tip); }
        void refreshTheme() {
            for (JComponent label : new JComponent[]{first, slash, last}) {
                if (label == last && cells instanceof RuledCells) continue;
                label.setForeground(label == slash ? ThemeZone.color(null, SemanticColor.BORDER_SUBTLE) : muted());
                label.setFont(statusFont());
            }
        }
        @Override public Dimension getPreferredSize() {
            return new Dimension(first.getPreferredSize().width + (slash.isVisible() ? NativeStyles.installed().metric(DesignMetrics.Role.STATUS_SEPARATOR) : 0)
                + last.getPreferredSize().width, Math.max(first.getPreferredSize().height, last.getPreferredSize().height));
        }
        @Override public void doLayout() {
            int firstWidth = Math.min(last instanceof PathBreadcrumbs ? getWidth() / 3 : getWidth(), first.getPreferredSize().width);
            int separatorWidth = Math.min(Math.max(0, getWidth() - firstWidth), slash.isVisible() ? NativeStyles.installed().metric(DesignMetrics.Role.STATUS_SEPARATOR) : 0);
            first.setBounds(0, 0, firstWidth, getHeight());
            slash.setBounds(firstWidth, 0, separatorWidth, getHeight());
            last.setBounds(firstWidth + separatorWidth, 0, Math.max(0, getWidth() - firstWidth - separatorWidth), getHeight());
        }
    }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getPreferredSize() {
        // Items and segments are laid out at the bar's height; only the text sets a floor, so it never clips.
        return new Dimension(0, Math.max(UIScale.scale(barHeight), Math.max(left.getPreferredSize().height, getFontMetrics(statusFont()).getHeight())));
    }
    @Override public void doLayout() { cells.layout(this); }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        cells.paint(this, g);
    }
}
```

Compared with today's file this one: makes the fields the strategies read package-visible; keeps `doLayout`'s body in
`SlashSegments.layout` and the dot in `SlashSegments.paint`; adds `cells`, `run`, `session`, `folder` and
`configProblem` (a diagnostic exists); passes the gap to `replaceStructure` (the slash segments' gap is today's
`STATUS_GAP`); and, under ruled cells only, leaves the configuration button's text and colour to `RuledCells`. Under
`SlashSegments` every line runs as today.

Then wire the bar, the strategy factory and the palette's hook:

```bash
python3 -I - <<'PY'
"""Plan 6b Task 5 step 4: the window's status bar under the look, and the palette's open and close hook."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WindowChrome.java", [
    ('''    private final WindowStatusBar status = new WindowStatusBar();''', '''    private final WindowStatusBar status;''', 1),
    ('''        composition = owner.look().header();''', '''        composition = owner.look().header();
        status = owner.look().statusBar(owner.action(ActionId.COMMAND_PALETTE), owner::paletteOpen);''', 1)])
edit(W + "WorkspaceChrome.java", [
    ('''        WindowTabs::new);''', '''        WindowTabs::new, SlashSegments::new, false);''', 1),
    ('''    private final java.util.function.Function<WindowContent, TabStrip> tabs;

    private WorkspaceChrome(MenuBarLayout menus, TitleFormat title, java.util.function.Supplier<HeaderComposition> header,
                            java.util.function.Function<WindowContent, TabStrip> tabs) {''',
     '''    private final java.util.function.Function<WindowContent, TabStrip> tabs;
    private final java.util.function.Supplier<StatusCells> status;
    private final boolean runItem;

    private WorkspaceChrome(MenuBarLayout menus, TitleFormat title, java.util.function.Supplier<HeaderComposition> header,
                            java.util.function.Function<WindowContent, TabStrip> tabs, java.util.function.Supplier<StatusCells> status, boolean runItem) {
        this.status = Objects.requireNonNull(status);
        this.runItem = runItem;''', 1),
    ('''        return new WorkspaceChrome(menus, title, header, tabs);''',
     '''        java.util.function.Supplier<StatusCells> status = switch (look.status()) {
            case SLASH -> SlashSegments::new;
            case RULED -> RuledCells::new;
        };
        return new WorkspaceChrome(menus, title, header, tabs, status, look.runItem());''', 1),
    ('''    TabStrip tabs(WindowContent owner) { return tabs.apply(owner); }''',
     '''    TabStrip tabs(WindowContent owner) { return tabs.apply(owner); }
    /** A status bar for one window, led by "▶ Run…" ({@code open}, active while {@code paletteOpen}) when the look has it. */
    WindowStatusBar statusBar(javax.swing.Action open, java.util.function.BooleanSupplier paletteOpen) {
        return new WindowStatusBar(status.get(), runItem ? new RunItem(open, paletteOpen) : null);
    }''', 1)])
edit(W + "WindowContent.java", [
    ('''    boolean lookAnimates() { return themes.animationsEnabled(); }''',
     '''    boolean lookAnimates() { return themes.animationsEnabled(); }
    /** Whether the palette is open; the run item shows it (spec 10.6). Null-safe while the window is being built. */
    boolean paletteOpen() { return commandPalette != null && commandPalette.isOpen(); }
    /** The palette opened or closed: the run item repaints. */
    void paletteChanged() { var run = chrome.status().runItem(); if (run != null) run.repaint(); }''', 1)])
edit(W + "WindowTitle.java", [
    ('''    /** {@code {directory}}: a remote pane's reported path as it is''',
     '''    /** The status bar's session cell (plan 6b Rulings 8): a local pane's shell, else {@link #session}. */
    static String sessionCell(PaneSnapshot pane) {
        if (pane == null) return "";
        if (pane.providerId().isEmpty() && pane.remoteDirectory().isEmpty()) return clean(pane.shell());
        return session(pane);
    }

    /** {@code {directory}}: a remote pane's reported path as it is''', 1)])
PY
grep -n 'void open(String id, String queryOrNull, String rowIdOrNull)\|private void restoreAndHide()' jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java
```

Then add `owner.paletteChanged();` as the last statement of `WindowCommandPalette.open(String, String, String)` (after the
block that opens the controller) and of `restoreAndHide()`, which the grep located; if the palette closes on another
path (`dismiss`, `escape` reach `restoreAndHide` through the controller's listener), the run item repaints there too.

Add the ledger rows:

```bash
python3 -I - <<'PY'
import pathlib
W, T = "jasper-app/src/main/java/dev/jasper/app/workspace/", "jasper-app/src/test/java/dev/jasper/app/workspace/RuledCellsTest.java"
rows = [(W + "StatusCells.java", "StatusCells", "New in Enterprise presentation Plan 6b Task 5; how a status bar lays out its model"),
        (W + "SlashSegments.java", "SlashSegments", "New in Enterprise presentation Plan 6b Task 5; today's slash segments; moved"),
        (W + "RuledCells.java", "RuledCells", "New in Enterprise presentation Plan 6b Task 5; ruled status cells"),
        (W + "RunItem.java", "RunItem", "New in Enterprise presentation Plan 6b Task 5; the leading Run item"),
        (T, "RuledCellsTest", "Test/fixture ownership; New in Enterprise presentation Plan 6b Task 5; ruled cells and the run item")]
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
text += "" if text.endswith("\n") else "\n"
ledger.write_text(text + "".join(f"{p},app.workspace,{c},nonvisual-helper,none,none,{T},{d}\n" for p, c, d in rows), encoding="utf-8")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --tests 'dev.jasper.app.architecture.*' :jasper-app:javadoc`
Expected: PASS: `RuledCellsTest` 4 and every existing status-bar test (`WindowStatusBarTest`, `ConfigurationStatusTest`,
`StatusProgressViewTest`, the icon-owner evidence) unchanged under `SlashSegments`.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/test/java/dev/jasper/app/workspace/RuledCellsTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Window content: ruled status cells and the run item

StatusCells lays the status bar's one model out two ways: SlashSegments,
today's, moved; RuledCells, the mocks' cells divided by border.divider
rules under a border.window rule, with the session's dot and name, the
folder, plugin items as cells, the grid size in the data face, UTF-8 and
a configuration cell only while there is a problem. Under a look with
the Run window either layout leads with Run, which opens the palette and
shows as an active toggle while it is open.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: The classic dialog

`dev.jasper.ui.swing.ClassicDialog` is the classic in-window dialog of spec 10.8 (Rulings 10): a still scrim
(`surface.scrim`) over the layer, the catalog's popover shadow, a card on the kit's dialog surface slot framed 1 px in
`border.hover` with `DIALOG_RADIUS`, a 26 px header strip on `surface.header.active` above a `border.window` rule with
the swatch and the title (`WINDOW_TITLE`, the semibold UI face), and the host's content; CENTERED sits centred on the
whole window, SHEET at the top with no top frame edge. `KitView` gains the two host flags the form reads (Rulings 12):
`hostTitled` (the form draws neither its title label nor its hero) and `dialogBand` (the footer on `surface.footer`
under a `border.window` rule, `DIALOG_FOOTER_VERTICAL` and `FORM_SIDES` padding). Nothing in the default look sets
them.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicDialog.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java:62-66,319-330,363-366`, `Forms.java:190-191,223-227,324-336`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicDialogTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/swing/HostedTitleTest.java`

**Interfaces:**
- Consumes: 6a Task 3's `DIALOG_*` roles; plan 2's `DerivedColor.SURFACE_HEADER_ACTIVE`, `SURFACE_FOOTER`, `BORDER_HOVER`;
  4a's `ColorSlots.dialogSurface()`, `KitFixtures.enterprise()`; 4c's `KitFixtures.enterpriseLight()`; 3b's 11-argument `Form`.
- Produces: `public final class ClassicDialog extends JComponent implements AutoCloseable` with `enum Placement {
  CENTERED, SHEET }`, `ClassicDialog(KitContext, JComponent content, String title, Nodes.Hue swatch, Placement)`,
  `card()`, `content()`, `cardBounds()`, `setTitle(String)`, `title()`, `placement()`, `windowTitleHeight(int pixels)`,
  `open()`, `dismiss(Runnable removed)`, `close()`; `KitView.hostTitled(boolean)`, `hostTitled()`,
  `dialogBand(boolean)`, `dialogBand()`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicDialogTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Nodes.ButtonKind;
import dev.jasper.ui.model.Nodes.FormKind;
import dev.jasper.ui.model.Nodes.FormSubmission;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Spec 10.8 and the Credential Vault mocks: the classic in-window dialog's chrome. */
class ClassicDialogTest {
    static final ControlState READY = ControlState.READY;

    static Nodes.Form form(FormKind kind, String title, String footnote) {
        return new Nodes.Form("form", READY, kind, title, Optional.empty(), List.of(new Nodes.Secret("password", READY, "", "Master password", "")),
            Optional.empty(), List.of(new Nodes.Button("cancel", READY, "Cancel", "", ButtonKind.SECONDARY, "cancel", ""),
                new Nodes.Button("unlock", READY, "Unlock", "", ButtonKind.PRIMARY, "unlock", "")), "cancel", FormSubmission.PRIMARY, footnote);
    }

    static BufferedImage print(ClassicDialog dialog, int width, int height) {
        dialog.setSize(width, height); dialog.doLayout();
        dialog.card().doLayout();
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.setColor(Color.WHITE); g.fillRect(0, 0, width, height); dialog.printAll(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y) & 0xFFFFFF); }

    @Test void theCentredDialogIsTheMocksChrome() {
        Edt.run(() -> {
            var theme = KitFixtures.enterpriseLight();
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(form(FormKind.PROMPT_COMPACT, "Unlock Vault", "Auto-locks after 15 min idle")), e -> { })) {
                view.hostTitled(true); view.dialogBand(true);
                try (var dialog = new ClassicDialog(context, view, "Unlock Vault", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED)) {
                    dialog.windowTitleHeight(29);
                    dialog.open();
                    var image = print(dialog, 1440, 871);
                    var card = dialog.cardBounds();
                    assertThat(card.width).as("the content and the frame").isEqualTo(view.getPreferredSize().width + 2);
                    assertThat(card.x).isEqualTo((1440 - card.width) / 2);
                    assertThat(card.y).as("centred on the whole window, its title bar included").isEqualTo((871 - 29 - card.height) / 2);
                    var strip = dialog.card().getComponent(0);
                    assertThat(strip.getHeight()).as("26 and the rule").isEqualTo(27);
                    int scrim = at(image, 5, 5).getRed();
                    assertThat(scrim).as("white reads about C9").isBetween(0xC8, 0xCA);
                    assertThat(at(image, card.x, card.y + card.height / 2)).as("the frame").isEqualTo(DerivedColor.BORDER_HOVER.color(theme));
                    assertThat(at(image, card.x + 40, card.y + 6)).as("the strip").isEqualTo(DerivedColor.SURFACE_HEADER_ACTIVE.color(theme));
                    assertThat(at(image, card.x + 40, card.y + 27)).as("its rule").isEqualTo(theme.semantic().color(SemanticColor.BORDER_WINDOW));
                    assertThat(at(image, card.x + 3, card.y + card.height - 5)).as("the footer band").isEqualTo(DerivedColor.SURFACE_FOOTER.color(theme));
                    assertThat(KitViewTest.find(view, "form.footnote", javax.swing.JLabel.class)).as("the footnote stays in the band").isNotNull();
                }
            } finally { release(context); }
        });
    }

    @Test void aSheetJoinsTheTopWithNoTopFrameAndASwatchLeadsTheTitle() {
        Edt.run(() -> {
            var theme = KitFixtures.enterpriseLight();
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(form(FormKind.SHEET, "Add Login", "")), e -> { })) {
                view.hostTitled(true); view.dialogBand(true);
                try (var dialog = new ClassicDialog(context, view, "Add Login", Nodes.Hue.BLUE, ClassicDialog.Placement.SHEET)) {
                    dialog.open();
                    var image = print(dialog, 822, 573);
                    var card = dialog.cardBounds();
                    assertThat(card.y).as("joined under the title bar").isZero();
                    assertThat(card.x).isEqualTo((822 - card.width) / 2);
                    assertThat(at(image, card.x + 60, 0)).as("no top frame: the strip reaches the top").isEqualTo(DerivedColor.SURFACE_HEADER_ACTIVE.color(theme));
                    assertThat(at(image, card.x + 1 + 10 + 4, 13)).as("the swatch, 8 square at 10").isEqualTo(theme.semantic().color(SemanticColor.HUE_BLUE));
                }
            } finally { release(context); }
        });
    }

    @Test void itNeverMoves() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(form(FormKind.SHEET, "Add Login", "")), e -> { })) {
                var dialog = new ClassicDialog(context, view, "Add Login", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED);
                dialog.setSize(800, 600); dialog.open();
                assertThat(dialog.cardBounds().isEmpty()).as("in place at once").isFalse();
                var removed = new AtomicInteger();
                dialog.dismiss(removed::incrementAndGet);
                assertThat(removed).as("gone at once").hasValue(1);
                dialog.close();
            } finally { release(context); }
        });
    }
}
```

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/HostedTitleTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.find;
import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Nodes.FormKind;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 10.8: a host that titles the dialog takes the form's title; a classic dialog's footer is a band. */
class HostedTitleTest {
    static Nodes.Form sheet(Optional<Nodes.Node> hero) {
        return new Nodes.Form("form", ControlState.READY, FormKind.SHEET, "Add Login", hero,
            List.of(new Nodes.Secret("password", ControlState.READY, "", "Password", "")), Optional.empty(),
            List.of(new Nodes.Button("save", ControlState.READY, "Save", "", Nodes.ButtonKind.PRIMARY, "save", "")), "", Nodes.FormSubmission.PRIMARY, "");
    }

    @Test void aHostTitledFormDrawsNeitherItsTitleNorItsHero() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var hero = new Nodes.Text("heading", ControlState.READY, "Add Login", Nodes.TextRole.LABEL);
            try (var view = new KitView(context, new ViewSpec(sheet(Optional.of(hero))), e -> { })) {
                KitFixtures.render(view, 440, 300, 1);
                assertThat(find(view, "heading", javax.swing.JComponent.class).isVisible()).isTrue();
                view.hostTitled(true);
                KitFixtures.render(view, 440, 300, 1);
                assertThat(find(view, "heading", javax.swing.JComponent.class).isVisible()).as("the host shows the title").isFalse();
                view.update(new ViewSpec(sheet(Optional.of(hero))));
                assertThat(find(view, "heading", javax.swing.JComponent.class).isVisible()).as("and after an update").isFalse();
            } finally { release(context); }
            context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(sheet(Optional.empty())), e -> { })) {
                view.hostTitled(true);
                KitFixtures.render(view, 440, 300, 1);
                for (var label : descendants(view)) if (label instanceof javax.swing.JLabel text && "Add Login".equals(text.getText()))
                    assertThat(text.isVisible()).as("no title label").isFalse();
            } finally { release(context); }
        });
    }

    @Test void aDialogBandIsTheFooterOnSurfaceFooterUnderARule() {
        Edt.run(() -> {
            var theme = KitFixtures.enterpriseLight();
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(sheet(Optional.empty())), e -> { })) {
                var before = KitFixtures.render(view, 440, 300, 1);
                view.dialogBand(true);
                var image = KitFixtures.render(view, 440, 300, 1);
                var save = find(view, "save", javax.swing.JComponent.class);
                var at = javax.swing.SwingUtilities.convertPoint(save.getParent(), save.getLocation(), view);
                assertThat(new java.awt.Color(image.getRGB(4, at.y + save.getHeight() / 2) & 0xFFFFFF)).isEqualTo(DerivedColor.SURFACE_FOOTER.color(theme));
                assertThat(image.getRGB(4, at.y + save.getHeight() / 2)).as("the band is new").isNotEqualTo(before.getRGB(4, at.y + save.getHeight() / 2));
                assertThat(view.getHeight() - (at.y + save.getHeight())).as("8 below the buttons").isEqualTo(8);
            } finally { release(context); }
        });
    }

    static List<java.awt.Component> descendants(java.awt.Container root) {
        var all = new java.util.ArrayList<java.awt.Component>();
        for (var child : root.getComponents()) { all.add(child); if (child instanceof java.awt.Container nested) all.addAll(descendants(nested)); }
        return all;
    }
}
```

The fixture names (`KitFixtures.enterpriseLight()`, `KitFixtures.enterprise()`, `context(theme, kit)`) are plans 4a and
4c's; if they differ, use theirs. `Nodes.Text(id, state, text, role)` and the 11-argument `Form` (3b) are the model's.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicDialogTest' --tests 'dev.jasper.ui.swing.HostedTitleTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol … ClassicDialog", "… hostTitled(boolean)" and "… dialogBand(boolean)".

- [ ] **Step 3: Write `ClassicDialog`**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicDialog.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The classic in-window dialog (Enterprise presentation spec 10.8; the Credential Vault mocks): a still scrim over the
 * window below its title bar and, above it, a card framed 1 px in {@code border.hover} with the catalog's popover shadow,
 * a header strip on {@code surface.header.active} holding the dialog's swatch and title, and the host's content on the
 * kit's dialog surface. CENTERED sits centred on the whole window (its title bar included, as the mocks place it); SHEET
 * joins the top edge with no top frame. It never moves. The host adds it to a layered pane, keeps it the pane's size and
 * owns keyboard routing and focus; the scrim takes every pointer event outside the card. EDT only.
 */
public final class ClassicDialog extends JComponent implements AutoCloseable {
    /** Where the card sits in the window. */
    public enum Placement { CENTERED, SHEET }

    private final KitContext context;
    private final JComponent content;
    private final Placement placement;
    private final Nodes.Hue swatch;
    private final Card card = new Card();
    private final Strip strip = new Strip();
    private final AutoCloseable appearance;
    private String title;
    private int windowTitle;
    private boolean closed;

    /**
     * A dialog around {@code content}.
     *
     * @param context the kit context the dialog paints with
     * @param content the host's content, a kit view's form as a rule
     * @param title the title the header strip shows
     * @param swatch the square drawn before the title; {@link Nodes.Hue#NONE} draws none
     * @param placement centred, or joined to the top as a sheet
     */
    public ClassicDialog(KitContext context, JComponent content, String title, Nodes.Hue swatch, Placement placement) {
        this.context = Objects.requireNonNull(context);
        this.content = Objects.requireNonNull(content);
        this.title = Objects.requireNonNull(title);
        this.swatch = Objects.requireNonNull(swatch);
        this.placement = Objects.requireNonNull(placement);
        setOpaque(false);
        setLayout(null);
        card.add(strip, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        add(card);
        var blocker = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { event.consume(); }
            @Override public void mouseReleased(MouseEvent event) { event.consume(); }
            @Override public void mouseClicked(MouseEvent event) { event.consume(); }
            @Override public void mouseMoved(MouseEvent event) { event.consume(); }
            @Override public void mouseDragged(MouseEvent event) { event.consume(); }
        };
        addMouseListener(blocker);
        addMouseMotionListener(blocker);
        addMouseWheelListener(MouseEvent::consume);
        appearance = context.binding().subscribe(pair -> { card.restyle(); repaint(); });
        card.restyle();
    }

    /** The card around the strip and the content; its focus policy and cycle are the host's. */
    public JComponent card() { return card; }
    /** The host's content. */
    public JComponent content() { return content; }
    /** Where the card is, in this layer. */
    public Rectangle cardBounds() { return card.getBounds(); }
    /** Centred, or a sheet. */
    public Placement placement() { return placement; }
    /** The title the strip shows. */
    public String title() { return title; }

    /** Shows {@code value} in the strip. */
    public void setTitle(String value) {
        title = Objects.requireNonNull(value);
        strip.getAccessibleContext().setAccessibleName(value);
        strip.repaint();
    }

    /** The window's own title bar above this layer, in device pixels: a centred card is centred on the whole window. */
    public void windowTitleHeight(int pixels) { windowTitle = Math.max(0, pixels); placeCard(); }

    /** Puts the card in place, at once. */
    public void open() { placeCard(); repaint(); }

    /** Removes the dialog at once and then runs {@code removed}. */
    public void dismiss(Runnable removed) {
        Objects.requireNonNull(removed);
        remove();
        removed.run();
    }

    /** Removes the dialog and releases its theme subscription. Idempotent. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        remove();
        try { appearance.close(); } catch (Exception ignored) { }
    }

    private void remove() {
        Container parent = getParent();
        if (parent == null) return;
        Rectangle bounds = getBounds();
        parent.remove(this);
        parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override public void doLayout() { placeCard(); }

    private void placeCard() {
        Dimension preferred = card.getPreferredSize();
        int width = Math.min(preferred.width, getWidth()), height = Math.min(preferred.height, getHeight());
        int x = (getWidth() - width) / 2;
        int y = placement == Placement.SHEET ? 0 : Math.max(0, Math.min(getHeight() - height, (getHeight() - windowTitle - height) / 2));
        var bounds = new Rectangle(x, y, width, height);
        if (!bounds.equals(card.getBounds())) { card.setBounds(bounds); card.doLayout(); }
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            ThemePainting.antialias(g);
            g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_SCRIM));
            g.fillRect(0, 0, getWidth(), getHeight());
            Rectangle at = card.getBounds();
            if (at.isEmpty()) return;
            ThemePainting.softShadow(g, new Rectangle2D.Double(at.x, at.y, at.width, at.height), ThemePainting.metric(context, DesignMetrics.Role.DIALOG_RADIUS),
                ThemePainting.metric(context, DesignMetrics.Role.POPOVER_SHADOW_OFFSET), ThemePainting.metric(context, DesignMetrics.Role.POPOVER_SHADOW_BLUR),
                context.metrics().value(context.theme().dark() ? DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK : DesignMetrics.Role.POPOVER_SHADOW_PERCENT_LIGHT) / 100.0, true);
        } finally { g.dispose(); }
    }

    /** The card: the dialog surface, framed and clipped; a sheet's top is square and unframed. */
    private final class Card extends JPanel {
        Card() { super(new BorderLayout()); setOpaque(false); }

        void restyle() {
            int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            setBorder(new EmptyBorder(placement == Placement.SHEET ? 0 : line, line, line, line));
        }

        private Shape surface() {
            double radius = ThemePainting.metric(context, DesignMetrics.Role.DIALOG_RADIUS);
            var round = new Area(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 2 * radius, 2 * radius));
            if (placement == Placement.SHEET) round.add(new Area(new Rectangle2D.Double(0, 0, getWidth(), radius + 1)));
            return round;
        }

        private Shape frame() {
            double line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), half = line / 2, r = ThemePainting.metric(context, DesignMetrics.Role.DIALOG_RADIUS);
            double w = getWidth(), h = getHeight();
            if (placement != Placement.SHEET) return new RoundRectangle2D.Double(half, half, w - line, h - line, 2 * r, 2 * r);
            var path = new Path2D.Double();
            path.moveTo(half, 0); path.lineTo(half, h - r); path.quadTo(half, h - half, r, h - half);
            path.lineTo(w - r, h - half); path.quadTo(w - half, h - half, w - half, h - r); path.lineTo(w - half, 0);
            return path;
        }

        @Override protected boolean isPaintingOrigin() { return true; }

        @Override protected void paintChildren(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                g.setColor(ThemePainting.color(context, context.kit().colors().dialogSurface()));
                g.fill(surface());
                g.clip(surface());
                super.paintChildren(g);
            } finally { g.dispose(); }
            g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                g.setColor(DerivedColor.BORDER_HOVER.color(context.theme()));
                g.setStroke(new java.awt.BasicStroke(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE)));
                g.draw(frame());
            } finally { g.dispose(); }
        }
    }

    /** The header strip: the swatch and the title above a {@code border.window} rule. */
    private final class Strip extends JComponent {
        @Override public Dimension getPreferredSize() {
            return new Dimension(0, ThemePainting.metric(context, DesignMetrics.Role.DIALOG_HEADER_HEIGHT) + ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE));
        }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                var theme = context.theme();
                int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), height = getHeight() - line;
                g.setColor(DerivedColor.SURFACE_HEADER_ACTIVE.color(theme));
                g.fillRect(0, 0, getWidth(), height);
                g.setColor(SurfaceContrast.painted(theme, new PaintPair(SemanticColor.BORDER_WINDOW, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.STRUCTURAL)));
                g.fillRect(0, height, getWidth(), line);
                int x = ThemePainting.metric(context, DesignMetrics.Role.DIALOG_HEADER_INSET);
                SemanticColor hue = switch (swatch) {
                    case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN; case AMBER -> SemanticColor.HUE_AMBER;
                    case PURPLE -> SemanticColor.HUE_PURPLE; case NEUTRAL -> SemanticColor.HUE_NEUTRAL; case NONE -> null;
                };
                if (hue != null) {
                    int size = ThemePainting.metric(context, DesignMetrics.Role.DIALOG_SWATCH);
                    g.setColor(SurfaceContrast.painted(theme, new PaintPair(hue, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.INDICATOR)));
                    g.fillRect(x, (height - size) / 2, size, size);
                    x += size + ThemePainting.metric(context, DesignMetrics.Role.DIALOG_SWATCH_GAP);
                }
                g.setFont(ThemePainting.font(context, Typography.Role.WINDOW_TITLE));
                FontMetrics metrics = g.getFontMetrics();
                g.setColor(SurfaceContrast.painted(theme, new PaintPair(SemanticColor.TEXT_PRIMARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.TEXT)));
                g.drawString(title, x, (height - metrics.getHeight()) / 2 + metrics.getAscent());
            } finally { g.dispose(); }
        }
    }
}
```

- [ ] **Step 4: The host flags and the form**

```bash
python3 -I - <<'PY'
"""Plan 6b Task 6 step 4: KitView.hostTitled and dialogBand, read by the form."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitView.java", [
    ('''    /** Whether this view is presented as a window title action strip. */
    public boolean titleStrip() { return titleStrip; }
    private boolean titleStrip;''',
     '''    /** Whether this view is presented as a window title action strip. */
    public boolean titleStrip() { return titleStrip; }
    private boolean titleStrip;
    /**
     * App-only: the host shows this view's form title (a classic dialog's header strip, or a title bar the system draws),
     * so the form draws neither its title label nor its hero (Enterprise presentation spec 10.8). EDT confined.
     */
    public void hostTitled(boolean value) {
        requireOpen();
        if (hostTitled == value) return;
        hostTitled = value; refresh(); repaint();
    }
    /** Whether the host shows this view's form title. */
    public boolean hostTitled() { return hostTitled; }
    private boolean hostTitled;
    /**
     * App-only: this view's form is a classic dialog's content, so its footer is the dialog's band on
     * {@code surface.footer} under a {@code border.window} rule (spec 10.8). EDT confined.
     */
    public void dialogBand(boolean value) {
        requireOpen();
        if (dialogBand == value) return;
        dialogBand = value; refresh(); repaint();
    }
    /** Whether this view's form footer is a classic dialog's band. */
    public boolean dialogBand() { return dialogBand; }
    private boolean dialogBand;
    /** Under {@link #hostTitled()} the form's hero is the host's title too, so it stays hidden through every refresh. */
    private void hideHostedHero() {
        if (!hostTitled || !(spec.root() instanceof dev.jasper.ui.model.Nodes.Form form) || form.hero().isEmpty()) return;
        var hero = reconciler.control(form.hero().get().id());
        if (hero != null) hero.component.setVisible(false);
    }''', 1),
    ('''        try { motion.enabled(); reconciler.refresh(); if (pointerOnly) unfocusable(this); } catch (Throwable failure) { report(failure); }''',
     '''        try { motion.enabled(); reconciler.refresh(); hideHostedHero(); if (pointerOnly) unfocusable(this); } catch (Throwable failure) { report(failure); }''', 1),
    ('''        updating = true; try { reconciler.update(spec); this.spec = spec; } finally { updating = false; }''',
     '''        updating = true; try { reconciler.update(spec); this.spec = spec; } finally { updating = false; }
        hideHostedHero();''', 1)])
edit(S + "Forms.java", [
    ('''            title.setVisible(form.kind() == Nodes.FormKind.SHEET && form.hero().isEmpty());''',
     '''            title.setVisible(form.kind() == Nodes.FormKind.SHEET && form.hero().isEmpty() && !view.hostTitled());''', 1),
    ('''            footer.setLayout(new FooterLayout(this));''',
     '''            // A classic dialog's band (spec 10.8): 8 above and below the actions, the form's sides, under its rule.
            footer.band = view.dialogBand();
            if (footer.band) {
                int vertical = ThemePainting.metric(context(), DesignMetrics.Role.DIALOG_FOOTER_VERTICAL);
                int sides = ThemePainting.metric(context(), DesignMetrics.Role.FORM_SIDES);
                footer.setBorder(new EmptyBorder(vertical + stroke, sides, vertical, sides));
            }
            footer.setLayout(new FooterLayout(this));''', 1),
    ('''        boolean window;
        KitContext context;
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (window) {''',
     '''        boolean window;
        /** A classic dialog's band: {@code surface.footer} under a {@code border.window} rule. */
        boolean band;
        KitContext context;
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (band && !window) {
                var theme = context.theme();
                graphics.setColor(dev.jasper.ui.theme.DerivedColor.SURFACE_FOOTER.color(theme));
                graphics.fillRect(0, 0, getWidth(), getHeight());
                graphics.setColor(dev.jasper.ui.theme.SurfaceContrast.painted(theme, new dev.jasper.ui.theme.PaintPair(SemanticColor.BORDER_WINDOW,
                    dev.jasper.ui.theme.DerivedColor.SURFACE_FOOTER, dev.jasper.ui.theme.SurfaceContrast.Guard.STRUCTURAL)));
                graphics.fillRect(0, 0, getWidth(), ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE));
            }
            if (window) {''', 1)])
PY
```

The footer's rule takes `stroke`, the hairline `FormControl.refresh` computes a few lines above (`int
stroke=ThemePainting.metric(context(),DesignMetrics.Role.HAIRLINE);`); if 3b moved that line below the footer's border,
compute the hairline again in the new block.

- [ ] **Step 5: Run the tests to verify they pass, and the gallery unchanged**

Run: `./gradlew :jasper-ui:test :jasper-ui:javadoc`
Expected: PASS: `ClassicDialogTest` 3, `HostedTitleTest` 2 and every existing jasper-ui test (`ModalOverlayTest`,
`FootnotesTest`, the gallery suites, `KitMatrixTest`, `StrategyIsolationTest`'s jasper-ui side). Then prove the default
gallery byte-identical as in plan 6a Task 9 Step 4 (the flags are off unless a host sets them).

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicDialogTest.java jasper-ui/src/test/java/dev/jasper/ui/swing/HostedTitleTest.java
git commit -F - <<'EOF'
Window content: the classic dialog

ClassicDialog is the classic in-window dialog: a still scrim, the popover
shadow, a card on the kit's dialog surface framed in border.hover, a 26 px
strip on surface.header.active with the swatch and the semibold title,
centred on the whole window or joined to the top as a sheet, never
moving. KitView's hostTitled keeps the form from repeating its title and
dialogBand makes its footer the band on surface.footer under a rule.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: The dialog mapping

`DialogMapping.CLASSIC` puts OVERLAY and ATTACHED_SHEET dialogs inside their owner window as classic dialogs (spec 10.8;
Rulings 11): `AuxiliaryWindows`, the one reader of `DialogMapping`, marks every surface it makes; `NativeShells.create`
places a marked modal overlay or attached sheet whose owner window is showing in that window through a
`WindowOverlay` over a `ClassicDialog` (centred, or joined under the title bar), sets the content's `hostTitled` and
`dialogBand`, and blocks a modal show on a secondary loop as overlays do; a marked dialog without a showing window opens
natively. Under `windowTitled`, a FULL form in a dialog whose title bar the system draws does not repeat its title.
Progress overlays and the default look are unchanged.

**Files:**
- Modify (whole file): `jasper-app/src/main/java/dev/jasper/app/windows/WindowOverlay.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/AuxiliarySurface.java:75-80`, `AuxiliaryWindows.java:18-26,116-120`, `NativeShells.java:86-92,207-235`, `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (the auxiliary windows)
- Test: `jasper-app/src/test/java/dev/jasper/app/windows/ClassicPlacementTest.java`, `jasper-app/src/test/java/dev/jasper/app/windows/ClassicWindowOverlayTest.java`

**Interfaces:**
- Consumes: Task 6's `ClassicDialog`, `KitView.hostTitled`, `dialogBand`; 5a's `DialogMapping`; 3d's `AuxiliarySurface.swatch()`.
- Produces: `AuxiliarySurface.classic()`, `hostTitled()` (package setters); `AuxiliaryWindows(UiState, Function, PathChooser,
  DialogMapping)`; `static Optional<ClassicDialog.Placement> NativeShells.classicPlacement(AuxiliarySurface, boolean
  ownerShowing)`; `record WindowOverlay.Layer(JComponent component, JComponent card, Runnable open, Consumer<Runnable>
  dismiss, Runnable close, Supplier<Rectangle> cardBounds)`; `static WindowOverlay WindowOverlay.classic(JRootPane,
  ClassicDialog)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/windows/ClassicPlacementTest.java`:

```java
package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.persistence.UiState;
import dev.jasper.app.presentation.DialogMapping;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.swing.ClassicDialog;
import java.awt.Dimension;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.8 and plan 6b Rulings 11: which dialogs a classic look draws in their window. */
@ExtendWith(EdtTestExtension.class)
class ClassicPlacementTest {
    static AuxiliaryWindows windows(DialogMapping.Kind kind, boolean windowTitled) {
        return new AuxiliaryWindows(UiState.inMemory(), surface -> { throw new AssertionError("never shown"); }, (choice, cancel) -> List.of(),
            new DialogMapping(kind, windowTitled));
    }

    @Test void aClassicOverlayIsCentredAndASheetJoinsTheTop() {
        var windows = windows(DialogMapping.Kind.CLASSIC, true);
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        var add = windows.dialog("Add Login", true, UUID.randomUUID());
        add.attachedSheet(true);
        assertThat(unlock.classic()).isTrue();
        assertThat(NativeShells.classicPlacement(unlock, true)).contains(ClassicDialog.Placement.CENTERED);
        assertThat(NativeShells.classicPlacement(add, true)).contains(ClassicDialog.Placement.SHEET);
        assertThat(NativeShells.classicPlacement(windows.dialog("Details", true, UUID.randomUUID()), true))
            .as("a centred dialog stays native").isEmpty();
        assertThat(NativeShells.classicPlacement(windows.overlay("Connecting", UUID.randomUUID()), true))
            .as("a progress overlay stays as it is").isEmpty();
        assertThat(NativeShells.classicPlacement(windows.window("vault", "Credential Vault", new Dimension(800, 600), true), true)).isEmpty();
    }

    @Test void aDialogWithoutAShowingWindowIsNative() {
        var windows = windows(DialogMapping.Kind.CLASSIC, true);
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        assertThat(NativeShells.classicPlacement(unlock, false)).isEmpty();
    }

    @Test void theDefaultMappingKeepsEveryDialogAsRequested() {
        var windows = windows(DialogMapping.Kind.AS_REQUESTED, false);
        var unlock = windows.modalOverlay("Unlock Vault", UUID.randomUUID());
        var add = windows.dialog("Add Login", true, UUID.randomUUID());
        add.attachedSheet(true);
        assertThat(unlock.classic() || add.classic()).isFalse();
        assertThat(NativeShells.classicPlacement(unlock, true)).isEmpty();
        assertThat(NativeShells.classicPlacement(add, true)).isEmpty();
        assertThat(add.hostTitled()).isFalse();
        var three = new AuxiliaryWindows(UiState.inMemory(), surface -> { throw new AssertionError(); });
        assertThat(three.modalOverlay("Unlock Vault", UUID.randomUUID()).classic()).as("today's constructors are the default look").isFalse();
    }

    @Test void aNativeTitleTitlesTheDialogsItDraws() {
        var windows = windows(DialogMapping.Kind.CLASSIC, true);
        assertThat(windows.dialog("Settings", true, UUID.randomUUID()).hostTitled()).isTrue();
        assertThat(windows.modalOverlay("Unlock Vault", UUID.randomUUID()).hostTitled()).as("an overlay's title is its strip").isFalse();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/windows/ClassicWindowOverlayTest.java`:

```java
package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.platform.WindowInput;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.ClassicDialog;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Review Focus 4: classic dialogs over one window stack, route keys to the newest and hand them back. */
@ExtendWith(EdtTestExtension.class)
class ClassicWindowOverlayTest {
    static KeyEvent key(JComponent source, int code) { return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED); }

    @Test void classicDialogsStackAndHandTheKeysBack() {
        var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
        root.setContentPane(new JTextField());
        var context = KitTestSupport.context();
        var sheetBody = new JPanel(); var name = new JTextField(); sheetBody.add(name); sheetBody.setPreferredSize(new Dimension(440, 300));
        var unlockBody = new JPanel(); var password = new JTextField(); unlockBody.add(password);
        var addLogin = WindowOverlay.classic(root, new ClassicDialog(context, sheetBody, "Add Login", Nodes.Hue.BLUE, ClassicDialog.Placement.SHEET));
        var unlock = WindowOverlay.classic(root, new ClassicDialog(context, unlockBody, "Unlock Vault", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED));
        try {
            addLogin.show();
            assertThat(addLogin.cardBounds().y).as("the sheet joins the top").isZero();
            unlock.show();
            assertThat(root.getClientProperty(WindowInput.OVERLAY)).isSameAs(unlock);
            assertThat(unlock.filterKey(key(password, KeyEvent.VK_ESCAPE))).as("Escape reaches its Cancel").isFalse();
            assertThat(unlock.filterKey(key(name, KeyEvent.VK_A))).as("the dialog below is blocked").isTrue();
            unlock.close();
            assertThat(unlockBody.getParent()).as("gone at once, with no fade").isNull();
            assertThat(root.getClientProperty(WindowInput.OVERLAY)).isSameAs(addLogin);
            assertThat(addLogin.filterKey(key(name, KeyEvent.VK_A))).isFalse();
        } finally { unlock.close(); addLogin.close(); }
        assertThat(WindowInput.blocked(root)).isFalse();
        assertThat(root.getClientProperty("Jasper.windowOverlays")).isNull();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.windows.ClassicPlacementTest' --tests 'dev.jasper.app.windows.ClassicWindowOverlayTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for the four-argument `AuxiliaryWindows`, `classic()`,
`classicPlacement` and `WindowOverlay.classic`.

- [ ] **Step 3: The overlay over either layer**

Check first: `git diff deafed424 -- jasper-app/src/main/java/dev/jasper/app/windows/WindowOverlay.java` prints nothing
(otherwise carry the change into the file below). Replace the whole of `WindowOverlay.java` with:

```java
package dev.jasper.app.windows;

import dev.jasper.app.platform.WindowInput;
import dev.jasper.ui.swing.ClassicDialog;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.ModalOverlay;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/**
 * One EDT-owned modal layer inside an existing root: the kit's {@link ModalOverlay} (the scrim, the centred card and its
 * fade-up motion) or a {@link ClassicDialog} (Enterprise presentation spec 10.8), with the window's input routed to it.
 * Layers over one window stack: the newest takes the keys and focus, and closing it hands them back to the one below.
 * Creates no native window.
 */
final class WindowOverlay implements AutoCloseable {
    /** The root's open overlays, oldest first; {@link WindowInput#OVERLAY} names the newest. */
    private static final String STACK = "Jasper.windowOverlays";

    /**
     * What the overlay shows and how it opens and leaves.
     *
     * @param component the layer added to the root's layered pane
     * @param card the card holding the content, the focus cycle root
     * @param open puts the card in place
     * @param dismiss takes the layer away, then runs its argument
     * @param close removes the layer at once and releases it
     * @param cardBounds where the card is
     */
    record Layer(JComponent component, JComponent card, Runnable open, Consumer<Runnable> dismiss, Runnable close, Supplier<Rectangle> cardBounds) {
        static Layer faded(ModalOverlay overlay) {
            return new Layer(overlay, overlay.card(), overlay::open, overlay::dismiss, overlay::close, overlay::cardBounds);
        }
        static Layer classic(ClassicDialog dialog) {
            return new Layer(dialog, dialog.card(), dialog::open, dialog::dismiss, dialog::close, dialog::cardBounds);
        }
    }

    private final JRootPane root;
    private final Layer layer;
    /** A dialog's Escape reaches its content (its Cancel); a progress overlay keeps Escape from the window. */
    private final boolean dialog;
    private final KeyboardFocusManager focusManager;
    private final KeyEventDispatcher keys = this::filterKey;
    private final ComponentAdapter resize = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { layout(); }
    };
    private boolean shown, closed;
    private final PropertyChangeListener focusChanged = this::focusChanged;
    private Component priorFocus;

    WindowOverlay(JRootPane root, JComponent content, KitContext context, boolean dialog) {
        this(root, content, context, dialog, KeyboardFocusManager.getCurrentKeyboardFocusManager());
    }

    WindowOverlay(JRootPane root, JComponent content, KitContext context, boolean dialog, KeyboardFocusManager focusManager) {
        this(root, Layer.faded(new ModalOverlay(context, content)), dialog, focusManager);
    }

    /** A classic dialog over {@code root} (spec 10.8): modal, Escape reaching its content. */
    static WindowOverlay classic(JRootPane root, ClassicDialog dialog) {
        return new WindowOverlay(root, Layer.classic(dialog), true, KeyboardFocusManager.getCurrentKeyboardFocusManager());
    }

    private WindowOverlay(JRootPane root, Layer layer, boolean dialog, KeyboardFocusManager focusManager) {
        this.root = root; this.layer = layer; this.dialog = dialog; this.focusManager = focusManager;
        var card = layer.card();
        card.setFocusCycleRoot(true);
        card.setFocusTraversalPolicy(new ContainerOrderFocusTraversalPolicy() {
            @Override protected boolean accept(Component component) {
                return !(component instanceof JPanel || component instanceof JScrollPane
                    || component instanceof JViewport || component instanceof JLabel) && super.accept(component);
            }
        });
        card.setFocusable(true);
        // A press on the scrim returns focus to the card.
        layer.component().addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { focus(); }
        });
    }

    @SuppressWarnings("unchecked")
    private List<WindowOverlay> stack() {
        if (!(root.getClientProperty(STACK) instanceof List<?> list)) {
            var created = new ArrayList<WindowOverlay>();
            root.putClientProperty(STACK, created);
            return created;
        }
        return (List<WindowOverlay>) list;
    }

    private boolean top() { return shown && root.getClientProperty(WindowInput.OVERLAY) == this; }

    private void focusChanged(java.beans.PropertyChangeEvent event) {
        if (top() && event.getNewValue() instanceof Component next
                && SwingUtilities.getRootPane(next) == root && !SwingUtilities.isDescendingFrom(next, layer.card()))
            SwingUtilities.invokeLater(() -> { if (top()) focus(); });
    }

    void show() {
        if (closed) return;
        if (!shown) {
            priorFocus = focusManager.getFocusOwner();
            shown = true;
            var stack = stack();
            // Each newer overlay takes the layer above the last, over everything the window shows.
            root.getLayeredPane().add(layer.component(), Integer.valueOf(JLayeredPane.DRAG_LAYER + 1 + stack.size()));
            stack.add(this);
            root.putClientProperty(WindowInput.OVERLAY, this);
            root.getLayeredPane().addComponentListener(resize);
            focusManager.addKeyEventDispatcher(keys);
            focusManager.addPropertyChangeListener("focusOwner", focusChanged);
            layout();
            layer.open().run();
        }
        layout(); focus();
    }

    void layout() {
        if (!shown) return;
        var component = layer.component();
        component.setBounds(0, 0, root.getLayeredPane().getWidth(), root.getLayeredPane().getHeight());
        component.doLayout(); component.revalidate(); component.repaint();
    }

    Rectangle cardBounds() { return layer.cardBounds().get(); }

    void focus() {
        if (!top()) return;
        Component current = focusManager.getFocusOwner();
        var card = layer.card();
        if (current != null && SwingUtilities.isDescendingFrom(current, card)) return;
        Component first = card.getFocusTraversalPolicy().getDefaultComponent(card);
        if (first == null || !first.requestFocusInWindow()) card.requestFocusInWindow();
    }

    /** Source-root checks let native Vault/trust prompts keep their own input; only the newest overlay filters. */
    boolean filterKey(KeyEvent event) {
        if (!top() || SwingUtilities.getRootPane(event.getComponent()) != root) return false;
        boolean inside = SwingUtilities.isDescendingFrom(event.getComponent(), layer.card());
        boolean escape = event.getKeyCode() == KeyEvent.VK_ESCAPE || event.getKeyChar() == 27;
        boolean command = event.isMetaDown() || event.isControlDown() || event.isAltDown();
        boolean editing = event.getComponent() instanceof JTextComponent && !event.isAltDown()
            && switch (event.getKeyCode()) {
                case KeyEvent.VK_A, KeyEvent.VK_C, KeyEvent.VK_V, KeyEvent.VK_X, KeyEvent.VK_Z, KeyEvent.VK_Y -> true;
                default -> false;
            };
        if (!inside || escape && !dialog || command && !editing) {
            event.consume();
            if (!inside) focus();
            return true;
        }
        return false;
    }

    /** Hands input back at once and lets the layer leave (a classic dialog leaves at once). */
    @Override public void close() {
        if (closed) return;
        closed = true;
        Component closingFocus = focusManager.getFocusOwner();
        boolean wasTop = top();
        boolean restore = shown && closingFocus != null && SwingUtilities.isDescendingFrom(closingFocus, layer.component());
        boolean wasShown = shown;
        shown = false;
        focusManager.removeKeyEventDispatcher(keys);
        focusManager.removePropertyChangeListener("focusOwner", focusChanged);
        root.getLayeredPane().removeComponentListener(resize);
        var stack = stack();
        stack.remove(this);
        WindowOverlay below = stack.isEmpty() ? null : stack.getLast();
        if (wasTop || root.getClientProperty(WindowInput.OVERLAY) == this) root.putClientProperty(WindowInput.OVERLAY, below);
        if (stack.isEmpty()) root.putClientProperty(STACK, null);
        Runnable gone = () -> { layer.card().removeAll(); layer.close().run(); root.revalidate(); root.repaint(); };
        if (wasShown) layer.dismiss().accept(gone); else gone.run();
        if (below != null) {
            if (restore) below.focus();
            return;
        }
        Component previous = priorFocus; priorFocus = null;
        // A successful connection may already have requested focus for its new pane. Let it win.
        if (restore && previous != null) SwingUtilities.invokeLater(() -> {
            Component current = focusManager.getFocusOwner();
            if (!WindowInput.blocked(root) && previous.isShowing() && SwingUtilities.getRootPane(previous) == root
                    && (current == null || current == closingFocus)) previous.requestFocusInWindow();
        });
    }
}
```

Compared with today's file this one routes every `overlay.` call through `Layer` (for `ModalOverlay` the same calls, so
the kit overlay behaves as before) and adds `classic`.

- [ ] **Step 4: Mark the surfaces and place the classic ones**

```bash
python3 -I - <<'PY'
"""Plan 6b Task 7 step 4: AuxiliarySurface's marks, AuxiliaryWindows' mapping, NativeShells' classic placement."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/windows/"
edit(W + "AuxiliarySurface.java", [
    ('''        attachedSheet = value;
    }''', '''        attachedSheet = value;
    }
    private boolean classic, hostTitled;
    /** Whether a modal overlay or attached sheet of this surface is drawn in its window as a classic dialog (spec 10.8). */
    public boolean classic() { return classic; }
    void classic(boolean value) { classic = value; }
    /** Whether a title bar the system draws already shows this dialog's title, so its FULL form draws none (spec 10.8). */
    public boolean hostTitled() { return hostTitled; }
    void hostTitled(boolean value) { hostTitled = value; }''', 1)])
edit(W + "AuxiliaryWindows.java", [
    ('''    public AuxiliaryWindows(UiState state, Function<AuxiliarySurface, AuxiliarySurface.Shell> shells, PathChooser chooser) {
        this.chooser = Objects.requireNonNull(chooser);''',
     '''    public AuxiliaryWindows(UiState state, Function<AuxiliarySurface, AuxiliarySurface.Shell> shells, PathChooser chooser) {
        this(state, shells, chooser, new dev.jasper.app.presentation.DialogMapping(dev.jasper.app.presentation.DialogMapping.Kind.AS_REQUESTED, false));
    }

    /**
     * As above, under the look's dialog mapping (Enterprise presentation spec 10.8): the one reader of
     * {@code DialogMapping}, it marks every surface it makes, so plugin and app dialogs share the classic path.
     */
    public AuxiliaryWindows(UiState state, Function<AuxiliarySurface, AuxiliarySurface.Shell> shells, PathChooser chooser,
                            dev.jasper.app.presentation.DialogMapping dialogs) {
        this.classic = dialogs.kind() == dev.jasper.app.presentation.DialogMapping.Kind.CLASSIC;
        this.windowTitled = dialogs.windowTitled();
        this.chooser = Objects.requireNonNull(chooser);''', 1),
    ('''    private final List<AuxiliarySurface> open = new ArrayList<>();''', '''    private final List<AuxiliarySurface> open = new ArrayList<>();
    /** The look's mapping: classic overlays and sheets, and dialogs a native title bar titles. */
    private final boolean classic, windowTitled;''', 1),
    ('''    private AuxiliarySurface track(AuxiliarySurface surface) {
        boolean first = open.isEmpty();''',
     '''    private AuxiliarySurface track(AuxiliarySurface surface) {
        surface.classic(classic);
        if (surface.kind() == AuxiliarySurface.Kind.DIALOG) surface.hostTitled(windowTitled);
        boolean first = open.isEmpty();''', 1)])
edit(W + "NativeShells.java", [
    ('''        return switch (surface.kind()) {
            case WINDOW -> frame(surface);
            case DIALOG -> dialog(surface);
            case OVERLAY -> overlay(surface);
        };
    }''',
     '''        if (surface.classic()) {
            Window owner = surface.kind() == AuxiliarySurface.Kind.WINDOW ? null : owner(surface);
            var placement = classicPlacement(surface, owner instanceof javax.swing.RootPaneContainer && owner.isShowing());
            if (placement.isPresent()) return inWindow(surface, placement.get());
            // A classic dialog whose owner has no showing window opens natively (spec 10.8).
            if (surface.kind() == AuxiliarySurface.Kind.OVERLAY && surface.modal()) return dialog(surface);
        }
        return switch (surface.kind()) {
            case WINDOW -> frame(surface);
            case DIALOG -> dialog(surface);
            case OVERLAY -> overlay(surface);
        };
    }

    /** Where a marked modal overlay or attached sheet sits in its owner's showing window, or empty when it is not in one. */
    static Optional<dev.jasper.ui.swing.ClassicDialog.Placement> classicPlacement(AuxiliarySurface surface, boolean ownerShowing) {
        if (!surface.classic() || !surface.modal() || !ownerShowing) return Optional.empty();
        return switch (surface.kind()) {
            case OVERLAY -> Optional.of(dev.jasper.ui.swing.ClassicDialog.Placement.CENTERED);
            case DIALOG -> surface.attachedSheet() ? Optional.of(dev.jasper.ui.swing.ClassicDialog.Placement.SHEET) : Optional.empty();
            case WINDOW -> Optional.empty();
        };
    }

    /**
     * A classic dialog in the owner's window (spec 10.8): the content's form loses its own title and gains the footer
     * band, the card is centred on the window or joined under its title bar, and a modal show blocks until it closes.
     */
    private AuxiliarySurface.Shell inWindow(AuxiliarySurface surface, dev.jasper.ui.swing.ClassicDialog.Placement placement) {
        Window owner = owner(surface);
        var container = (javax.swing.RootPaneContainer) owner;
        SwingUtilities.updateComponentTreeUI(surface.holder());
        for (var child : surface.holder().getComponents())
            if (child instanceof dev.jasper.ui.swing.KitView view && !view.isClosed()) { view.hostTitled(true); view.dialogBand(true); }
        var dialog = new dev.jasper.ui.swing.ClassicDialog(themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), surface.holder(),
            surface.title(), surface.swatch(), placement);
        dialog.windowTitleHeight(owner.getInsets().top);
        var overlay = WindowOverlay.classic(container.getRootPane(), dialog);
        surface.holder().getAccessibleContext().setAccessibleName(surface.title());
        var close = new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { surface.close(); }
        };
        owner.addWindowListener(close);
        Runnable show = () -> {
            overlay.show();
            if (surface.closed()) return;
            var loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            var closed = surface.onClosed(loop::exit);
            try { if (!surface.closed()) loop.enter(); } finally { closed.close(); }
        };
        return new AuxiliarySurface.Shell(show, overlay::focus, () -> { owner.removeWindowListener(close); overlay.close(); },
            title -> { dialog.setTitle(title); surface.holder().getAccessibleContext().setAccessibleName(title); }, overlay::cardBounds,
            (title, initial) -> chooseFile(TopModal.over(owner), title, initial, Optional.empty()));
    }''', 1),
    ('''            shell.followTheme(dialog.getRootPane(), bar, themes.current().chrome().dark());''',
     '''            shell.followTheme(dialog.getRootPane(), bar, themes.current().chrome().dark());
            // Spec 10.8: the title bar the system draws titles the dialog, so a FULL form does not repeat it.
            if (bar == null && surface.hostTitled())
                for (var child : surface.holder().getComponents())
                    if (child instanceof dev.jasper.ui.swing.KitView view && !view.isClosed()
                            && view.spec().root() instanceof dev.jasper.ui.model.Nodes.Form form && form.kind() == dev.jasper.ui.model.Nodes.FormKind.FULL)
                        view.hostTitled(true);''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java", [
    ('''        auxiliary = new AuxiliaryWindows(uiState, shells::create, shells::choosePaths);''',
     '''        auxiliary = new AuxiliaryWindows(uiState, shells::create, shells::choosePaths, presentation.projections().dialogs());''', 1)])
PY
```

The `shell.followTheme(dialog…)` anchor is the line 6a Task 5 added in `NativeShells.dialog`. `KitView.spec()` is the
view's public spec accessor (`NativeShells.installTitleBar` already reads it).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.windows.*' --tests 'dev.jasper.app.plugins.*Dialog*' --tests 'dev.jasper.app.plugins.AppContractTest' --tests 'dev.jasper.app.plugins.HostedUiTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS: `ClassicPlacementTest` 4, `ClassicWindowOverlayTest` 1, every `WindowOverlayTest`,
`WindowOverlayFocusTest`, `SheetOwnershipTest`, `ModalDialogTest` and contract case (unmarked surfaces take today's paths).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/windows jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java \
  jasper-app/src/test/java/dev/jasper/app/windows/ClassicPlacementTest.java jasper-app/src/test/java/dev/jasper/app/windows/ClassicWindowOverlayTest.java
git commit -F - <<'EOF'
Window content: the dialog mapping

AuxiliaryWindows reads the look's dialog mapping and marks every surface
it makes. Under the classic mapping a modal overlay or attached sheet
whose owner window is showing is drawn in that window as a classic
dialog, centred on the window or joined under its title bar, its form
titled by the strip and footed by the band; without a showing window it
opens natively. A native title bar titles FULL forms. WindowOverlay
hosts either the kit's overlay or the classic dialog.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: The window content lands, and the Workstation and Credential Vault comparisons

`WINDOW_CONTENT` joins `Presentation.LANDED` (Rulings 1), so the shipped Enterprise kit now gives the classic header,
tabs, ruled cells, the run item and the classic dialog mapping. Spec 16.1.4's chrome renders check the strategies each
look names. `EnterpriseWorkstationMockTest` compares the toolbar row, the tab strip and the status cells with the
Workstation (Light) and Dark Mode mocks; `EnterpriseDialogMockTest` compares Unlock Vault (Credential Vault 1) and the
Add Login and Add SSH Key sheets (Credential Vault 4 and 5, Light only, as the mocks are) by their chrome (Rulings 13 to
15). The menu-row comparison takes the Dark rule colour now that Task 1 overrides `border.divider`. `pairs.json` gains
the opt-in crops, among them the Unlock Vault pair plan 4c left to this plan.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java` (`LANDED`)
- Modify: `jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java` (`check`), `jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseMenuRowMockTest.java` (`workstation`, `mounted`, the Dark rule)
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseWorkstationMockTest.java`, `jasper-app/src/test/java/dev/jasper/app/windows/EnterpriseDialogMockTest.java`
- Modify: `tools/visual/pairs.json` (eleven entries appended), `docs/design/theme-engine/migration.csv` (rows below)

**Interfaces:**
- Consumes: Tasks 1 to 7; 6a Task 8's `MockProfile`, `ChromeRenderTest.check`, `EnterpriseMenuRowMockTest.render`, `X`,
  `Y`, `LIGHT`, `DARK`; Task 5's `RuledCellsTest.state`; Task 4's `WindowContent.tabBar()`, `ClassicTabs.entries()`;
  Task 7's `WindowOverlay.classic`.
- Produces: `Presentation.LANDED = Set.of(Landed.WINDOW_CHROME, Landed.WINDOW_CONTENT)`;
  `EnterpriseMenuRowMockTest.Mounted(BufferedImage image, WindowContent owner, JRootPane root)` and `static Mounted
  mounted(boolean dark, String name, Consumer<WindowContent> fixture)` (plans 7 to 9 render their chrome through it);
  `EnterpriseWorkstationMockTest.workstation(WindowContent)` (the mocks' window as a fixture).

- [ ] **Step 1: Write the failing tests**

Edit the gate's test, the chrome renders' check and the menu-row comparison:

```bash
python3 -I - <<'PY'
"""Plan 6b Task 8 step 1: the landed window content in the tests."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/"
edit(T + "presentation/ProjectionsTest.java", [
    ('''        assertThat(Presentation.LANDED).as("plan 6a lands the window chrome; 6b, 7, 8 and 9 add theirs")
            .contains(Presentation.Landed.WINDOW_CHROME).doesNotContain(Presentation.Landed.WINDOW_CONTENT);''',
     '''        assertThat(Presentation.LANDED).as("plans 6a and 6b land the window; 7, 8 and 9 add theirs")
            .contains(Presentation.Landed.WINDOW_CHROME, Presentation.Landed.WINDOW_CONTENT);''', 1)])
edit(T + "workspace/ChromeRenderTest.java", [
    ('''        assertThat(titles.getLast()).as(look.name() + ": Help is last").isEqualTo("Help");
    }''',
     '''        assertThat(titles.getLast()).as(look.name() + ": Help is last").isEqualTo("Help");
        // Plan 6b: the header, tabs, status cells and run item the look names.
        var workspace = look.projections().workspace();
        assertThat(owner.chrome().composition() instanceof ClassicHeader).as(look.name() + ": the header")
            .isEqualTo(workspace.header() == WorkspaceLook.Header.CLASSIC);
        assertThat(owner.tabBar() instanceof ClassicTabs).as(look.name() + ": the tabs").isEqualTo(workspace.tabs() == WorkspaceLook.Tabs.CLASSIC);
        assertThat(owner.tabBar().isVisible() || workspace.tabs() == WorkspaceLook.Tabs.EQUAL).as(look.name() + ": classic tabs always show").isTrue();
        assertThat(owner.status().cells() instanceof RuledCells).as(look.name() + ": the status cells")
            .isEqualTo(workspace.status() == WorkspaceLook.Status.RULED);
        assertThat(owner.status().runItem() != null).as(look.name() + ": the run item").isEqualTo(workspace.runItem());
    }''', 1)])
edit(T + "workspace/EnterpriseMenuRowMockTest.java", [
    ('''        model.addMenuSection(MenuTarget.topLevel(MenuTarget.HELP, "Help")).set(List.of(new MenuEntry.Item("dev.x.about")));
        return model;''',
     '''        model.addMenuSection(MenuTarget.topLevel(MenuTarget.HELP, "Help")).set(List.of(new MenuEntry.Item("dev.x.about")));
        // The mocks' right-hand status cells (plan 6b).
        model.addStatus("dev.x.vault", false, 10).setText("Vault: Locked");
        model.addStatus("dev.x.ssh", false, 20).setText("1 SSH session");
        return model;''', 1),
    ('''    /** The Enterprise window as the mocks show its content, printed at 2× and written for inspection. EDT. */
    static BufferedImage render(boolean dark, java.util.function.Consumer<WindowContent> fixture) {
        var themes = EnterpriseLook.controller(dark);''',
     '''    /**
     * A mounted Enterprise window and its 2× print.
     *
     * @param image the print
     * @param owner the window's content, for the geometry of its parts
     * @param root the root pane it is mounted in
     */
    record Mounted(BufferedImage image, WindowContent owner, JRootPane root) { }

    /** The Enterprise window as the mocks show its content, printed at 2× and written for inspection. EDT. */
    static BufferedImage render(boolean dark, java.util.function.Consumer<WindowContent> fixture) {
        return mounted(dark, "window", fixture).image();
    }

    /** As {@link #render}, written as {@code enterprise-<light|dark>-<name>.png}, keeping the window. EDT. */
    static Mounted mounted(boolean dark, String name, java.util.function.Consumer<WindowContent> fixture) {
        var themes = EnterpriseLook.controller(dark);''', 1),
    ('''            MockProfile.write(image, "enterprise-" + (dark ? "dark" : "light") + "-window");
            return image;''',
     '''            MockProfile.write(image, "enterprise-" + (dark ? "dark" : "light") + "-" + name);
            return new Mounted(image, owner, root);''', 1),
    ('''        if (!dark) assertThat(MockProfile.deltaE(renderRuns.get(1).color(), mockRuns.get(1).color())).as("light rule colour").isLessThanOrEqualTo(2.5);
        // Plan 6a Rulings 9: Enterprise Dark's border.divider is darker than the mock's rule until plan 6b's override.''',
     '''        // Plan 6b Task 1 overrides Enterprise Dark's border.divider with the mock's rule (#4A4D53).
        assertThat(MockProfile.deltaE(renderRuns.get(1).color(), mockRuns.get(1).color())).as(what + " rule colour").isLessThanOrEqualTo(2.5);''', 1)])
PY
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseWorkstationMockTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.testsupport.MockProfile;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Spec 10.3, 10.5 and 10.6 against the mocks: the Enterprise toolbar row, tab strip and status cells, rendered at 2×
 * under the shipped kit, Plex and the Enterprise theme, compared with the Workstation (Light) and Dark Mode mocks by
 * profile ("Mock measurements"; plan 6a Rulings 12, plan 6b Rulings 7, 8 and 15). Render coordinates are the mock's
 * minus (X, Y), except below the tab strip, which is 1 px taller than the mock's (Rulings 7).
 */
class EnterpriseWorkstationMockTest {
    static final int X = EnterpriseMenuRowMockTest.X, Y = EnterpriseMenuRowMockTest.Y;
    /** The Run Dialog mock, the one that shows the status bar's run item. */
    static final String RUN = "06 Run Dialog/Run Dialog - Just opened (recent + most used).png";

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** The mocks' window: the 34 px toolbar row, "Local: ~" and the selected "Mac: ~", a configuration warning. EDT. */
    static void workstation(WindowContent owner) {
        owner.setToolbarHeight(34);
        owner.currentTab().rename("Local: ~");
        owner.newTab(DesktopTestSupport.HOME);
        owner.currentTab().rename("Mac: ~");
        owner.setConfigurationState(RuledCellsTest.state(ConfigDiagnostic.Severity.WARNING));
        owner.update();
    }

    static EnterpriseMenuRowMockTest.Mounted window(boolean dark) throws Exception {
        EnterpriseMenuRowMockTest.Mounted[] mounted = new EnterpriseMenuRowMockTest.Mounted[1];
        edt(() -> mounted[0] = EnterpriseMenuRowMockTest.mounted(dark, "workstation", EnterpriseWorkstationMockTest::workstation));
        return mounted[0];
    }

    static BufferedImage mock(boolean dark) { return MockProfile.mock(dark ? EnterpriseMenuRowMockTest.DARK : EnterpriseMenuRowMockTest.LIGHT); }

    static void same(String what, MockProfile.Run actual, MockProfile.Run expected, int tolerance) {
        assertThat(actual.length()).as(what + " length").isCloseTo(expected.length(), within(tolerance));
        assertThat(MockProfile.deltaE(actual.color(), expected.color())).as(what + " colour").isLessThanOrEqualTo(2.5);
    }

    /** The full-height 1 px rules of {@code divider}'s colour along row {@code y}: 2 px wide, and 12 px above and below too. */
    static List<Integer> dividers(BufferedImage image, int y, int from, int to, Color divider) {
        var found = new ArrayList<Integer>();
        for (var run : MockProfile.row(image, y, from, to))
            if (run.length() == 2 && MockProfile.deltaE(run.color(), divider) <= 2.5
                    && MockProfile.deltaE(MockProfile.at(image, run.start(), y - 12), divider) <= 2.5
                    && MockProfile.deltaE(MockProfile.at(image, run.start(), y + 12), divider) <= 2.5) found.add(run.start());
        return found;
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void theRowsDownTheWindowAreTheMocks(boolean dark) throws Exception {
        String what = dark ? "dark " : "light ";
        // Column 2000 crosses no control: the menu row and its rule, the toolbar row and its rule, the tab strip and its rule.
        var expected = MockProfile.solid(MockProfile.column(mock(dark), 2000, Y, Y + 180), 2);
        var actual = MockProfile.solid(MockProfile.column(window(dark).image(), 2000 - X, 0, 180), 2);
        String[] names = {"menu row", "menu rule", "toolbar row", "toolbar rule", "tab strip", "tab rule"};
        for (int i = 0; i < names.length; i++) same(what + names[i], actual.get(i), expected.get(i), 2);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void theToolbarRowIsTheMocks(boolean dark) throws Exception {
        String what = dark ? "dark " : "light ";
        var mock = mock(dark);
        var mounted = window(dark);
        var render = mounted.image();
        // "+" and "New Session": toolbar padding 8, button padding 8, the glyph 6 before the label (mock ink 38-51, 66-199).
        var expected = MockProfile.inks(mock, new Rectangle(X + 4, 112, 400, 64), 12, 25);
        var actual = MockProfile.inks(render, new Rectangle(4, 112 - Y, 400, 64), 12, 25);
        for (int i = 0; i < 2; i++) {
            assertThat(actual.get(i).start() + X).as(what + "ink " + i + " starts").isCloseTo(expected.get(i).start(), within(4));
            assertThat(actual.get(i).width()).as(what + "ink " + i + " width").isCloseTo(expected.get(i).width(), within(4));
            assertThat(actual.get(i).top() + Y).as(what + "ink " + i + " top").isCloseTo(expected.get(i).top(), within(3));
        }
        // The search field: x 2116-2715, y 122-165 in the mock.
        var field = mounted.owner().chrome().header().field();
        var at = SwingUtilities.convertPoint(field.getParent(), field.getLocation(), mounted.root());
        assertThat(2 * at.x + X).as(what + "search field left").isCloseTo(2116, within(4));
        assertThat(2 * field.getWidth()).as(what + "search field width").isCloseTo(600, within(2));
        assertThat(2 * at.y + Y).as(what + "search field top").isCloseTo(122, within(2));
        assertThat(2 * field.getHeight()).as(what + "search field height").isCloseTo(44, within(2));
        // "Settings…" at the trailing edge: ink 2742-2846 in the mock.
        var settings = MockProfile.inks(mock, new Rectangle(2722, 112, 160, 64), 12, 25);
        var rendered = MockProfile.inks(render, new Rectangle(2722 - X, 112 - Y, 160, 64), 12, 25);
        assertThat(settings).as("the mock's Settings…").hasSize(1);
        assertThat(rendered).as(what + "Settings…").hasSize(1);
        assertThat(rendered.getFirst().start() + X).as(what + "Settings… starts").isCloseTo(settings.getFirst().start(), within(4));
        assertThat(rendered.getFirst().end() + X).as(what + "Settings… ends").isCloseTo(settings.getFirst().end(), within(4));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void theTabsAreTheMocks(boolean dark) throws Exception {
        String what = dark ? "dark " : "light ";
        var mock = mock(dark);
        var mounted = window(dark);
        var render = mounted.image();
        var strip = (ClassicTabs) mounted.owner().tabBar();
        var first = strip.entries().get(0);
        int left = 2 * SwingUtilities.convertPoint(first.getParent(), first.getLocation(), mounted.root()).x;
        // The underline row: mock y 233 (the indicator 232-235 above the rule at 236); the render's strip is 2 px taller.
        int underline = 2 * (25 + 35 + 29) - 3;
        var expected = MockProfile.solid(MockProfile.row(mock, 233, 650, 1100), 4);
        var actual = MockProfile.solid(MockProfile.row(render, underline, left, left + 450), 4);
        same(what + "the first tab and its divider on the strip", actual.get(0), expected.get(0), 6);
        same(what + "the active tab's tab.indicator underline", actual.get(1), expected.get(1), 6);
        // The active tab on surface.card, inside its 12 px leading padding (mock x 850, mid-strip y 208).
        assertThat(MockProfile.deltaE(MockProfile.at(render, left + 200, 2 * (25 + 35 + 14)), MockProfile.at(mock, 850, 208)))
            .as(what + "the active tab's ground").isLessThanOrEqualTo(2.5);
        MockProfile.write(render.getSubimage(left, 2 * (25 + 35), 700, 58), "enterprise-" + (dark ? "dark" : "light") + "-tabs");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void theStatusCellsAreTheMocks(boolean dark) throws Exception {
        String what = dark ? "dark " : "light ";
        var mock = mock(dark);
        var render = window(dark).image();
        int height = 2 * EnterpriseMenuRowMockTest.HEIGHT;
        // From the bottom of column 1000: surface.app under its border.window rule (24 tall; the mock's 22 a residual).
        var expected = MockProfile.solid(MockProfile.column(mock, 1000, 1700, 1802), 2);
        var actual = MockProfile.solid(MockProfile.column(render, 1000 - X, height - 100, height), 2);
        same(what + "status bar", actual.getLast(), expected.getLast(), 4);
        same(what + "status rule", actual.get(actual.size() - 2), expected.get(expected.size() - 2), 1);
        // The border.divider rules: the mock's last two (before UTF-8 and the warning) at 2470 and 2574, from the right edge.
        Color divider = MockProfile.at(mock, 2470, 1782);
        int row = height - 22;
        var mockRules = dividers(mock, 1782, X, 2882, divider);
        var renderRules = dividers(render, row, 0, 2880, divider);
        assertThat(mockRules).as("the mock's seven rules").containsExactly(108, 164, 1966, 2140, 2322, 2470, 2574);
        assertThat(renderRules.size()).as(what + "a rule after the run item, session and folder and before each right cell").isEqualTo(3 + 5);
        assertThat(renderRules.get(renderRules.size() - 2) + X).as(what + "the rule before UTF-8").isCloseTo(2470, within(6));
        assertThat(renderRules.getLast() + X).as(what + "the rule before the warning").isCloseTo(2574, within(6));
        assertThat(MockProfile.deltaE(MockProfile.at(render, 2860, row), MockProfile.at(mock, 2862, 1782)))
            .as(what + "the warning cell's ground").isLessThanOrEqualTo(2.5);
        // Rulings 15: the run item, 60 wide in the Run Dialog mock (its rule at 122).
        var runMock = MockProfile.mock(RUN);
        assertThat(dividers(runMock, 1782, X, 400, MockProfile.at(runMock, 122, 1782))).as("the Run Dialog mock's first rule").startsWith(122);
        assertThat(renderRules.getFirst() + X).as(what + "the run item's width").isCloseTo(122, within(4));
        MockProfile.write(render.getSubimage(1898, height - 46, 982, 46), "enterprise-" + (dark ? "dark" : "light") + "-status");
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/windows/EnterpriseDialogMockTest.java`:

```java
package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Nodes.ButtonKind;
import dev.jasper.ui.model.Nodes.FormKind;
import dev.jasper.ui.model.Nodes.FormSubmission;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.ClassicDialog;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Spec 10.8 against Credential Vault 1, 4 and 5 (Light, as the mocks are): the classic dialog's chrome over Enterprise
 * Light, around fixture forms (plan 6b Rulings 13 and 14). Unlock Vault's window in its 3012 × 1932 mock has its outer
 * edge at (64, 64) and its content at (66, 124); the Credential Vault window of the 1644 × 1204 sheet mocks has its
 * content from x 2 and its title bar's bottom at y 58.
 */
@ExtendWith(EdtTestExtension.class)
class EnterpriseDialogMockTest {
    static final String UNLOCK = "07 Credential Vault/Credential Vault 1 - Unlock Vault (over Workstation).png";
    /** Unlock Vault's frame in its mock, x 1104-1907 and y 782-1149, and the content's origin. */
    static final Rectangle UNLOCK_FRAME = new Rectangle(1104, 782, 804, 368);
    static final int UNLOCK_CONTENT_X = 66, UNLOCK_CONTENT_Y = 124;
    /** The sheets' frame in their mocks: x 380-1263 from y 58 (its height is each mock's), and the content's left edge. */
    static final int SHEET_X = 380, SHEET_WIDTH = 884, SHEET_TOP = 58, SHEET_CONTENT_X = 2;

    static Nodes.Form form(FormKind kind, String title, String footnote) {
        var ready = ControlState.READY;
        return new Nodes.Form("form", ready, kind, title, Optional.empty(), List.of(new Nodes.Secret("password", ready, "", "Master password", "")),
            Optional.empty(), List.of(new Nodes.Button("cancel", ready, "Cancel", "", ButtonKind.SECONDARY, "cancel", ""),
                new Nodes.Button("ok", ready, "Unlock", "", ButtonKind.PRIMARY, "ok", "")), "cancel", FormSubmission.PRIMARY, footnote);
    }

    /**
     * A classic dialog over a white window, printed at 2×.
     *
     * @param image the print
     * @param card where the card is, in logical pixels
     * @param swatch the theme's colour for the dialog's hue
     */
    record Rendered(BufferedImage image, Rectangle card, Color swatch) { }

    static Rendered render(ClassicDialog.Placement placement, String title, Nodes.Hue hue, FormKind kind, String footnote,
                           int width, int height, String name) {
        var themes = EnterpriseLook.controller(false);
        try {
            var context = themes.appearance().context(ThemeRoute.CHROME);
            var palette = themes.current().chrome().semantic();
            Color swatch = palette.color(switch (hue) {
                case BLUE -> SemanticColor.HUE_BLUE;
                case GREEN -> SemanticColor.HUE_GREEN;
                default -> SemanticColor.TEXT_PRIMARY;
            });
            var root = new JRootPane();
            var behind = new JPanel();
            behind.setBackground(Color.WHITE);
            root.setContentPane(behind);
            root.setSize(width, height);
            LayoutTestSupport.layoutTree(root);
            try (var view = new KitView(context, new ViewSpec(form(kind, title, footnote)), event -> { })) {
                view.hostTitled(true); view.dialogBand(true);
                var dialog = new ClassicDialog(context, view, title, hue, placement);
                // Credential Vault 1's title bar and its rule above the content: 29 px (Task 6's centring).
                dialog.windowTitleHeight(29);
                try (var overlay = WindowOverlay.classic(root, dialog)) {
                    overlay.show();
                    var image = MockProfile.print(root, width, height);
                    MockProfile.write(image, "enterprise-" + name);
                    return new Rendered(image, overlay.cardBounds(), swatch);
                }
            }
        } finally { themes.close(); EnterpriseLook.restoreDefault(); }
    }

    static void same(String what, MockProfile.Run actual, MockProfile.Run expected, int tolerance) {
        assertThat(actual.length()).as(what + " length").isCloseTo(expected.length(), within(tolerance));
        assertThat(MockProfile.deltaE(actual.color(), expected.color())).as(what + " colour").isLessThanOrEqualTo(2.5);
    }

    @Test void unlockVaultIsCredentialVaultOnesChrome() {
        var mock = MockProfile.mock(UNLOCK);
        var rendered = render(ClassicDialog.Placement.CENTERED, "Unlock Vault", Nodes.Hue.NONE, FormKind.PROMPT_COMPACT,
            "Auto-locks after 15 min idle", 1440, 871, "unlock");
        var card = rendered.card();
        var image = rendered.image();
        // Centred on the whole window, its title bar included: the mock's card centre is (720, 421) in the content.
        assertThat(card.x + card.width / 2.0).as("centred across").isCloseTo((UNLOCK_FRAME.getCenterX() - UNLOCK_CONTENT_X) / 2, within(1.0));
        assertThat(card.y + card.height / 2.0).as("centred on the whole window").isCloseTo((UNLOCK_FRAME.getCenterY() - UNLOCK_CONTENT_Y) / 2, within(1.0));
        // Down a column 396 px (2×) inside the frame, clear of the title, the footnote and the buttons: the frame, the
        // 26 px strip and its rule from the top; the band's rule, the 40 px band and the frame from the bottom.
        var expected = MockProfile.solid(MockProfile.column(mock, UNLOCK_FRAME.x + 396, UNLOCK_FRAME.y, UNLOCK_FRAME.y + UNLOCK_FRAME.height), 2);
        var actual = MockProfile.solid(MockProfile.column(image, 2 * card.x + 396, 2 * card.y, 2 * (card.y + card.height)), 2);
        String[] top = {"frame", "header strip", "strip rule"};
        for (int i = 0; i < 3; i++) same("unlock " + top[i], actual.get(i), expected.get(i), 2);
        String[] bottom = {"frame", "footer band", "band rule"};
        for (int i = 1; i <= 3; i++) same("unlock bottom " + bottom[i - 1], actual.get(actual.size() - i), expected.get(expected.size() - i), 2);
        // The title, 10 in and 8 down in the strip, in the semibold UI face (mock ink 1128-1265, from y 800).
        var mockTitle = MockProfile.inks(mock, new Rectangle(UNLOCK_FRAME.x + 8, UNLOCK_FRAME.y + 4, 560, 46), 12, 25).getFirst();
        var title = MockProfile.inks(image, new Rectangle(2 * card.x + 8, 2 * card.y + 4, 560, 46), 12, 25).getFirst();
        assertThat(title.start() - 2 * card.x).as("the title's inset").isCloseTo(mockTitle.start() - UNLOCK_FRAME.x, within(3));
        assertThat(title.top() - 2 * card.y).as("the title's top").isCloseTo(mockTitle.top() - UNLOCK_FRAME.y, within(3));
        assertThat(title.width()).as("Unlock Vault").isCloseTo(mockTitle.width(), within(6));
        // The still scrim: white reads #C9CACC.
        assertThat(MockProfile.deltaE(MockProfile.at(image, 200, 1600), MockProfile.at(mock, 1500, 1400))).as("the scrim").isLessThanOrEqualTo(2.5);
        MockProfile.write(image.getSubimage(2 * card.x, 2 * card.y, 560, 56), "enterprise-unlock-strip");
        MockProfile.write(image.getSubimage(2 * card.x, 2 * (card.y + card.height) - 86, 560, 86), "enterprise-unlock-footer");
    }

    @ParameterizedTest
    @CsvSource({"07 Credential Vault/Credential Vault 4 - Add Login.png, Add Login, BLUE, add-login, 2F65CA, 550",
                "07 Credential Vault/Credential Vault 5 - Add SSH Key.png, Add SSH Key, GREEN, add-ssh-key, 2E9E4F, 486"})
    void aSheetIsCredentialVaultsChrome(String file, String title, Nodes.Hue hue, String name, String mockSwatch, int frameHeight) {
        var mock = MockProfile.mock(file);
        // The frame's bottom edge: 606-607 in Credential Vault 4, 542-543 in 5.
        var frame = new Rectangle(SHEET_X, SHEET_TOP, SHEET_WIDTH, frameHeight);
        var rendered = render(ClassicDialog.Placement.SHEET, title, hue, FormKind.SHEET, "", 820, 572, name);
        var card = rendered.card();
        var image = rendered.image();
        assertThat(card.y).as("joined under the title bar").isZero();
        assertThat(card.x + card.width / 2.0).as("centred across").isCloseTo((frame.getCenterX() - SHEET_CONTENT_X) / 2, within(1.0));
        // Down a column 280 px (2×) inside the frame: no top frame, so the strip and its rule come first.
        var expected = MockProfile.solid(MockProfile.column(mock, frame.x + 280, frame.y, frame.y + frame.height), 2);
        var actual = MockProfile.solid(MockProfile.column(image, 2 * card.x + 280, 0, 2 * card.height), 2);
        same(name + " header strip", actual.get(0), expected.get(0), 2);
        same(name + " strip rule", actual.get(1), expected.get(1), 2);
        String[] bottom = {"frame", "footer band", "band rule"};
        for (int i = 1; i <= 3; i++) same(name + " bottom " + bottom[i - 1], actual.get(actual.size() - i), expected.get(expected.size() - i), 2);
        // The swatch, 8 square at 10, 9 down (mock 402-417, y 76-91), then the title 7 after it.
        var mockInks = MockProfile.inks(mock, new Rectangle(frame.x + 4, frame.y + 2, 560, 46), 12, 25);
        var inks = MockProfile.inks(image, new Rectangle(2 * card.x + 4, 2, 560, 46), 12, 25);
        for (int i = 0; i < 2; i++) {
            assertThat(inks.get(i).start() - 2 * card.x).as(name + " ink " + i + " inset").isCloseTo(mockInks.get(i).start() - frame.x, within(3));
            assertThat(inks.get(i).top()).as(name + " ink " + i + " top").isCloseTo(mockInks.get(i).top() - frame.y, within(3));
        }
        assertThat(inks.get(0).width()).as(name + " swatch width").isEqualTo(mockInks.get(0).width());
        assertThat(inks.get(1).width()).as(name + " title width").isCloseTo(mockInks.get(1).width(), within(6));
        var swatch = MockProfile.at(image, inks.get(0).start() + 8, inks.get(0).top() + 8);
        assertThat(swatch).as(name + " swatch: the theme's hue role (Rulings 14)").isEqualTo(rendered.swatch());
        System.out.printf("%s swatch: theme %06X, mock %s, delta E %.1f%n", name, swatch.getRGB() & 0xFFFFFF, mockSwatch,
            MockProfile.deltaE(swatch, new Color(Integer.parseInt(mockSwatch, 16))));
        MockProfile.write(image.getSubimage(2 * card.x, 0, 560, 54), "enterprise-" + name + "-strip");
        MockProfile.write(image.getSubimage(2 * card.x, 2 * card.height - 86, 560, 86), "enterprise-" + name + "-footer");
    }
}
```

The white behind the dialog is a fixture (`ColourLiteralScanTest` scans production code). The mock columns were read
with Pillow: Unlock Vault's at x 1500 is frame 782-783, strip 784-835, rule 836-837, the body, rule 1066-1067, band
1068-1147, frame 1148-1149; the sheets' at x 660 is strip 58-109, rule 110-111, the body, then rule 524-525, band
526-605, frame 606-607 (Add Login) and rule 460-461, band 462-541, frame 542-543 (Add SSH Key); the render's column
of the same inset must give the same first and last runs.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.ProjectionsTest' --tests 'dev.jasper.app.workspace.EnterpriseMenuRowMockTest'`
Expected: FAIL: `ProjectionsTest.untilItsPlanLandsAChromeValueMapsToTheDefaultProjection` with "plans 6a and 6b land
the window" (the set lacks `WINDOW_CONTENT`). The mock tests compile and run against Tasks 2 to 7 already.

- [ ] **Step 3: Land the window content**

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java"); text = path.read_text(encoding="utf-8")
old = '''    public static final Set<Landed> LANDED = Set.of(Landed.WINDOW_CHROME);'''
new = '''    public static final Set<Landed> LANDED = Set.of(Landed.WINDOW_CHROME, Landed.WINDOW_CONTENT);'''
if text.count(old) != 1: raise SystemExit("Presentation: LANDED moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

- [ ] **Step 4: Run the comparisons**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.windows.*'`
Expected: PASS: `ProjectionsTest`, `ChromeRenderTest` 2 (twelve looks checked for their header, tabs, status cells and run
item), `EnterpriseMenuRowMockTest` 2, `EnterpriseWorkstationMockTest` 8, `EnterpriseDialogMockTest` 3, and the
`enterprise-*` PNGs under `jasper-app/build/chrome-renders/`; the test output prints the two swatch lines (record them
for the STATUS entry). If a comparison fails with the real values, open the render beside the mock crop (`python3 -I
tools/visual/region.py "<mock>" jasper-app/build/chrome-renders/<render>.png build/content --crop <x,y,w,h> --region
<x,y,w,h>`) and fix the strategy's geometry or the role value until it passes; record any value you change in the
banner and in "Mock measurements". A test elsewhere that pinned plan 6a's interim projection under the shipped
Enterprise presentation (the unified header, equal tabs or slash segments with `WINDOW_CHROME` alone) now sees the
landed value: change its expectation to the classic one and record it in the banner.

- [ ] **Step 5: Add the opt-in pairs and the ledger rows**

```bash
python3 -I - <<'PY'
import json, pathlib
path = pathlib.Path("tools/visual/pairs.json"); pairs = json.loads(path.read_text(encoding="utf-8"))
if any(p["name"].startswith(("chrome-toolbar", "chrome-dialog")) for p in pairs): raise SystemExit("pairs.json already has plan 6b's pairs")
R, G = "docs/design/enterprise/reference/", "jasper-app/build/chrome-renders/"
LIGHT, DARK = R + "01 Workstation/Workstation - Connected (plugins in use).png", R + "08 Dark Mode/Workstation Dark - Connected (plugins in use).png"
VAULT = R + "07 Credential Vault/Credential Vault "
def pair(name, mock, crop, render, size, colours, floor):
    return {"name": name, "mock": mock, "crop": crop, "render": G + render + ".png", "render_crop": [0, 0] + size,
            "min_agreement": floor, "colours": colours}
pairs += [
    {"name": "chrome-toolbar-light", "mock": LIGHT, "crop": [2, 110, 2880, 70], "render": G + "enterprise-light-workstation.png",
     "render_crop": [0, 50, 2880, 70], "min_agreement": 0.3, "colours": ["#ECEDEF", "#C4C7CC", "#FFFFFF"]},
    {"name": "chrome-toolbar-dark", "mock": DARK, "crop": [2, 110, 2880, 70], "render": G + "enterprise-dark-workstation.png",
     "render_crop": [0, 50, 2880, 70], "min_agreement": 0.3, "colours": ["#3C3F44", "#2A2C30"]},
    pair("chrome-tabs-light", LIGHT, [650, 180, 700, 58], "enterprise-light-tabs", [700, 58], ["#E8E9EB", "#FFFFFF", "#2F65CA"], 0.4),
    pair("chrome-tabs-dark", DARK, [650, 180, 700, 58], "enterprise-dark-tabs", [700, 58], ["#3A3D42", "#2E3135", "#3D74D6"], 0.4),
    pair("chrome-status-light", LIGHT, [1900, 1756, 982, 46], "enterprise-light-status", [982, 46], ["#ECEDEF", "#D3D6DA", "#FBF1DA"], 0.4),
    pair("chrome-status-dark", DARK, [1900, 1756, 982, 46], "enterprise-dark-status", [982, 46], ["#3C3F44", "#4A4D53", "#4A4128"], 0.4),
    pair("chrome-dialog-unlock-strip", VAULT + "1 - Unlock Vault (over Workstation).png", [1104, 782, 560, 56], "enterprise-unlock-strip", [560, 56],
         ["#8E949C", "#D9DFEA", "#C4C7CC"], 0.4),
    pair("chrome-dialog-unlock-footer", VAULT + "1 - Unlock Vault (over Workstation).png", [1104, 1064, 560, 86], "enterprise-unlock-footer", [560, 86],
         ["#E6E7EA", "#C4C7CC"], 0.3),
    pair("chrome-dialog-add-login-strip", VAULT + "4 - Add Login.png", [380, 58, 560, 54], "enterprise-add-login-strip", [560, 54],
         ["#D9DFEA", "#C4C7CC"], 0.4),
    pair("chrome-dialog-add-ssh-key-strip", VAULT + "5 - Add SSH Key.png", [380, 58, 560, 54], "enterprise-add-ssh-key-strip", [560, 54],
         ["#D9DFEA", "#C4C7CC"], 0.4),
    pair("chrome-dialog-add-login-footer", VAULT + "4 - Add Login.png", [380, 522, 560, 86], "enterprise-add-login-footer", [560, 86],
         ["#E6E7EA", "#C4C7CC"], 0.3)]
path.write_text(json.dumps(pairs, indent=2) + "\n", encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/"
rows = [(T + "workspace/EnterpriseWorkstationMockTest.java", "app.workspace", "EnterpriseWorkstationMockTest",
         "Test/fixture ownership; New in Enterprise presentation Plan 6b Task 8; the toolbar row, tabs and status cells against the Workstation and Dark Mode mocks"),
        (T + "windows/EnterpriseDialogMockTest.java", "app.windows", "EnterpriseDialogMockTest",
         "Test/fixture ownership; New in Enterprise presentation Plan 6b Task 8; the classic dialog chrome against Credential Vault 1; 4 and 5")]
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
text += "" if text.endswith("\n") else "\n"
ledger.write_text(text + "".join(f"{p},{m},{c},nonvisual-helper,none,none,{p},{d}\n" for p, m, c, d in rows), encoding="utf-8")
PY
```

The Add SSH Key footer is not paired: its mock's band (462-541) sits higher than Add Login's, and one footer pair
covers the sheet band.

- [ ] **Step 6: Run the tests and the opt-in check**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*Mock*' --tests 'dev.jasper.app.windows.EnterpriseDialogMockTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-ui:test --tests 'dev.jasper.ui.gallery.EnterpriseGalleryTest'`
Then: `python3 -I tools/visual/check_pairs.py build/chrome-compare chrome-`
Expected: BUILD SUCCESSFUL; the check prints a PASS line for each of the thirteen `chrome-*` pairs (6a's two and these
eleven). Record the agreements for the STATUS entry. A FAIL with a missing colour is a defect to fix as in Step 4; a
low agreement that comes from the mock's fixture content (the toolbar's Detach button, Session combo and Connect button,
the tool-window headers beside the tabs, the Vault forms' bodies) is a floor to lower to the measured value rounded down
to 0.05, recorded in the banner.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseMenuRowMockTest.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseWorkstationMockTest.java jasper-app/src/test/java/dev/jasper/app/windows/EnterpriseDialogMockTest.java \
  tools/visual/pairs.json docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Window content: landed, and compared with the Workstation and Vault mocks

WINDOW_CONTENT joins the landed groups, so the shipped Enterprise kit
gives the classic header, tabs, ruled cells, the run item and the
classic dialog mapping. The chrome renders check the strategies each
look names. The toolbar row, tab strip and status cells are compared
with the Workstation and Dark Mode mocks, Unlock Vault and the Add Login
and Add SSH Key sheets with Credential Vault 1, 4 and 5, and the menu
row takes the Dark rule colour; pairs.json gains the opt-in crops.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 9: The variant-read scan, documentation, STATUS and the whole build

`VariantReadTest` gains spec 16.1.7's reader for this plan's last point (`DialogMapping.Kind` only in
`AuxiliaryWindows`, besides the presentation package; 6a's `WorkspaceLook` entry already covers the header, tabs and
status variants, and `WorkspaceChrome` now reads them); the documents spec 17 names for the window content get their
smallest edits (`docs/themes.md` took its edit in Task 1); `docs/STATUS.md` gets this plan's entry, with Rulings 3
awaiting the user's approval; the default look is proven unchanged and the whole build runs.

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/architecture/VariantReadTest.java` (6a's `PROJECTION_READERS` and its two tests)
- Modify: `docs/app-architecture.md` (6a's title-bar paragraph and its presentation-row sentence), `docs/app-maintenance.md:631-636`, `docs/ui-architecture.md:87-100`, `docs/STATUS.md:1`, this plan's first lines

**Interfaces:**
- Consumes: everything above; 6a Task 9's `VariantReadTest.PROJECTION_READERS`, `projectionReaders`.
- Produces: `PROJECTION_READERS` with the dialog mapping's entry, which plans 7 to 9 extend.

- [ ] **Step 1: Write the failing scan tests**

```bash
python3 -I - <<'PY'
"""Plan 6b Task 9 step 1: the dialog mapping's reader of spec 16.1.7."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/test/java/dev/jasper/app/architecture/VariantReadTest.java", [
    ('''        Pattern.compile("dev/jasper/app/presentation/Motion(?=[;$.]|$)"),
            type -> type.startsWith("dev/jasper/app/presentation/") || type.equals("dev/jasper/app/application/LookMotion"));''',
     '''        Pattern.compile("dev/jasper/app/presentation/Motion(?=[;$.]|$)"),
            type -> type.startsWith("dev/jasper/app/presentation/") || type.equals("dev/jasper/app/application/LookMotion"),
        Pattern.compile("dev/jasper/app/presentation/DialogMapping\\\\$Kind(?=[;$.]|$)"),
            type -> type.startsWith("dev/jasper/app/presentation/") || type.equals("dev/jasper/app/windows/AuxiliaryWindows"));''', 1),
    ('''        assertThat(projectionReaders(files)).isEmpty();
    }''',
     '''        assertThat(files.stream().map(StrategyIsolationTest.Scanned::of)
                .filter(scanned -> scanned.strings().stream().anyMatch(s -> s.contains("dev/jasper/app/presentation/DialogMapping$Kind")))
                .map(StrategyIsolationTest.Scanned::name))
            .as("the dialog mapping's scan is live").contains("dev/jasper/app/windows/AuxiliaryWindows");
        assertThat(projectionReaders(files)).isEmpty();
    }''', 1),
    ('''            .withField("motion", ClassDesc.of("dev.jasper.app.presentation.Motion"), ClassFile.ACC_PRIVATE));
        assertThat(projectionReaders(List.of(probe))).hasSize(2);''',
     '''            .withField("motion", ClassDesc.of("dev.jasper.app.presentation.Motion"), ClassFile.ACC_PRIVATE)
            .withField("dialogs", ClassDesc.of("dev.jasper.app.presentation.DialogMapping$Kind"), ClassFile.ACC_PRIVATE));
        assertThat(projectionReaders(List.of(probe))).hasSize(3);''', 1)])
PY
```

The Java pattern string is `"dev/jasper/app/presentation/DialogMapping\\$Kind(?=[;$.]|$)"`, as 6a's `WorkspaceLook`
entry writes its `$`.

- [ ] **Step 2: Run the scan**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.VariantReadTest'`
Expected: PASS (4 cases). To see it fail, add `private dev.jasper.app.presentation.DialogMapping.Kind probe;` to
`NativeShells` temporarily: `onlyTheFactoriesReadTheLooksVariants` names `dev/jasper/app/windows/NativeShells`; remove
it again.

- [ ] **Step 3: Edit the documents**

```bash
python3 -I - <<'PY'
"""Plan 6b Task 9 step 3: the smallest accurate documentation edits for the window content."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/app-architecture.md", [
    ('''Transfer, plugin menus, Tools, Window, Help). `WindowTitle` fills the look's title template.''',
     '''Transfer, plugin menus, Tools, Window, Help). `WindowTitle` fills the look's title template. `WorkspaceChrome.of`
also picks the window content: `HeaderComposition` (`UnifiedHeader`, or `ClassicHeader`, a toolbar row of left-packed
buttons with the search field and Settings… trailing), `TabStrip` (`WindowTabs`, or the content-sized `ClassicTabs`),
`StatusCells` (`SlashSegments`, or `RuledCells`) and the leading "▶ Run…" item. `AuxiliaryWindows` reads the look's
dialog mapping and marks the surfaces it makes; `NativeShells` draws a marked modal overlay or attached sheet whose
window is showing in that window as a `ClassicDialog`.''', 1),
    ('''The window chrome's projections are read by `WorkspaceChrome.of`, `ShellChrome.of` and the application's `LookMotion`.''',
     '''The window chrome's projections are read by `WorkspaceChrome.of`, `ShellChrome.of` and the application's `LookMotion`,
and the dialog mapping by `AuxiliaryWindows`.''', 1)])
edit("docs/app-maintenance.md", [
    ('''overlay's `show` blocks in a secondary loop until it closes and lets Escape reach its form; a progress overlay returns
at once and keeps Escape from the window.''',
     '''overlay's `show` blocks in a secondary loop until it closes and lets Escape reach its form; a progress overlay returns
at once and keeps Escape from the window. Under the classic dialog mapping (the Enterprise presentation) a modal overlay
or attached sheet whose owner window is showing is a `ClassicDialog` hosted by the same `WindowOverlay`, centred on the
window or joined under its title bar; without a showing window it opens as a native dialog.''', 1)])
edit("docs/ui-architecture.md", [
    ('''layered pane and owns keyboard routing and focus; the app's `WindowOverlay` stacks them per window.''',
     '''layered pane and owns keyboard routing and focus; the app's `WindowOverlay` stacks them per window.

`ClassicDialog` is the classic look's in-window dialog: a still scrim, a card framed in `border.hover` with
`DIALOG_RADIUS` and the popover shadow, a header strip on `surface.header.active` holding the dialog's swatch and title,
and the host's content; it is centred on the whole window or, as a sheet, joined under the title bar, and it neither
fades nor moves. Two `KitView` host flags serve it: `hostTitled` (the form draws neither its title nor its hero) and
`dialogBand` (the footer is a band on `surface.footer` under a `border.window` rule).''', 1)])
PY
```

Run `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'` and expect PASS (links resolve).

- [ ] **Step 4: Prove the default look unchanged**

This plan changes jasper-ui (`DerivedColor.BORDER_DIVIDER`'s flag, `ClassicDialog`, `KitView`'s flags, `Forms`).
Render the headless gallery here and at the base and compare every file:

```bash
B=$(mktemp -d) && git worktree add --detach "$B/base" "$(git log --format=%H -1 --grep='Window chrome: the shell.s documentation, scan and status')"
for theme in dark light; do
  (cd "$B/base" && ./gradlew -q :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R jasper-ui/build/gallery/$theme-1x "$B/base-$theme")
  ./gradlew -q :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R jasper-ui/build/gallery/$theme-1x "$B/new-$theme"
  diff -r "$B/base-$theme" "$B/new-$theme" && echo "$theme: identical"
done
git worktree remove --force "$B/base"
```

Expected: `dark: identical` and `light: identical` (the base is 6a's last commit, so this proves 6b alone). Then run
the reference pairs, the calibration suites and the frozen built-in derived tables as they are:
`./gradlew :jasper-app:test --tests '*Calibration*' --tests '*Reference*' :jasper-ui:test --tests 'dev.jasper.ui.theme.DerivedRolesTableTest'`
must pass unchanged, and `git diff "$(git log --format=%H -1 --grep='Window chrome: the shell.s documentation, scan and status')" --stat -- jasper-ui/src/test/resources`
must list only `derived-roles-enterprise.tsv`.

- [ ] **Step 5: Run the whole build**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Then count the results:

```bash
python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
tests = failures = errors = skipped = 0
for report in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    root = ET.parse(report).getroot()
    tests += int(root.get("tests")); failures += int(root.get("failures")); errors += int(root.get("errors")); skipped += int(root.get("skipped"))
print(f"{tests:,} tests, {failures} failures, {errors} errors, {skipped} skipped")
PY
```

Expected: 0 failures and 0 errors; note the four numbers.

- [ ] **Step 6: Write the STATUS entry and the banner**

Insert at the top of `docs/STATUS.md`, right after the `# Jasper — Status and Handoff` heading and its blank line (above
plan 6a's entry), one paragraph (fill `<range>` with the first and last commit of this plan, `<counts>` with Step 5's
line, `<agreements>` with Task 8's `check_pairs.py` lines, `<swatches>` with Task 8's two swatch lines, and
`<deviations>` with every deviation from this plan's text, or "none beyond the plan's Rulings"):

```markdown
**Enterprise presentation, plan 6b: the window content — 2026-10-16** (`claude/enterprise-window-chrome`, worktree
`.claude/worktrees/enterprise-6`, `<range>`, then this documentation commit;
[plan](superpowers/plans/2026-10-16-enterprise-6b-window-chrome-content.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). Spec 10.3, 10.5, 10.6 and 10.8 land:
`Presentation.LANDED` holds `WINDOW_CHROME` and `WINDOW_CONTENT`. `WorkspaceChrome.of` picks `ClassicHeader` (the
Workstation mock's toolbar row: "+ New Session", "Split ▾", "Maximize", contributed items and views packed left by
`WindowToolbar`'s leading pack, then "Search everywhere" with a light keycap and "Settings…"), `ClassicTabs`
(content-sized, a dot, a persistent ×, a "+", the active tab on `surface.card` with a 2 px `tab.indicator` underline),
`RuledCells` (session, folder, contributed items, grid, UTF-8 and, only while there is a problem, the configuration
cell, divided by `border.divider` rules) and the leading "▶ Run…" item; the default look keeps `UnifiedHeader`,
`WindowTabs` and `SlashSegments`, moved unchanged. `AuxiliaryWindows` is the one reader of `DialogMapping` and marks
every surface; a marked modal overlay or attached sheet over a showing window is a `ClassicDialog` (jasper-ui) hosted by
`WindowOverlay`, centred on the whole window or joined under its title bar, its form titled by the strip
(`KitView.hostTitled`) and footed by the band (`dialogBand`); without a showing window it opens natively. The Enterprise
theme takes the mocks' tab strip, tab indicator, scrim and dividers. `EnterpriseWorkstationMockTest` and
`EnterpriseDialogMockTest` compare the toolbar row, tabs and status cells with the Workstation and Dark Mode mocks and
the dialogs with Credential Vault 1, 4 and 5 (opt-in pairs: <agreements>; swatches: <swatches>). Rulings (plan,
"Rulings"): (1) the window content lands at the end; (2) the mocks' tab strip, tab indicator, scrim and dividers are
Enterprise theme values; (3) **awaiting the user's approval: `border.divider` becomes a ninth overridable derived role,
a deviation from spec 7.2** (Enterprise Dark's recipe value `#2B2D31` is darker than the mock's `#4A4D53`); (4) the
toolbar's leading pack is a separate branch; (5) the classic header's trailing group, field shrink and collapse,
`hidden` and `icons`; (6) the search field's light keycap; (7) classic tabs keep `window.tab_height` (the mock's strip
is 1 px shorter); (8) ruled cells keep `window.status_bar_height` (the mock's bar is 2 px shorter) and show the
configuration cell only for a problem; (9) the run item in `accent.edge`, on `surface.control.selected` while the
palette is open; (10) `ClassicDialog` in jasper-ui, radius 4, centred on the whole window; (11) `AuxiliaryWindows` marks
every surface, native without a showing window; (12) `hostTitled` hides the form's title and hero; (13) the Vault
bodies are plan 10's; (14) a swatch is its hue's theme role; (15) the Workstation render leads with the run item.
Deviations from the plan's text: <deviations>. `./gradlew check --continue`: **<counts>**. GUI checks for the user (spec
16.2): with Enterprise on, the toolbar row giving way as the window narrows, many tabs, a configuration warning cell,
"▶ Run…" opening the palette, Unlock Vault over a terminal window and Add Login over the Vault window (Escape, a second
dialog over the first, the window closing under them). Not merged, not pushed.
```

Replace this plan's first heading line with the same heading followed by a status banner:

```markdown
> **Status (2026-10-16): executed on `claude/enterprise-window-chrome` (`<range>`).** Rulings 3 (`border.divider` overridable) awaits the user's approval. Deviations from this plan's text: <deviations>.
```

- [ ] **Step 7: Commit**

```bash
git add docs jasper-app/src/test/java/dev/jasper/app/architecture/VariantReadTest.java
git commit -F - <<'EOF'
Window content: documentation, scan and status

VariantReadTest keeps DialogMapping's kind in AuxiliaryWindows. The app
architecture and maintenance documents describe the header, tab, status
and dialog strategies; the ui architecture describes ClassicDialog and
KitView's host flags. STATUS records plan 6b with its rulings, the
border.divider deviation awaiting approval, the mock comparisons and
the whole build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```
