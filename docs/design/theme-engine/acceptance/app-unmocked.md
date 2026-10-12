# App unmocked surfaces (stream `app-unmocked`, Plan 3 Task 9 render obligations)

The application's own windows and sheets that have no dedicated mock now reuse the compositions the mocked families
calibrated: the Vault manager's columns and footer band, the FULL/SHEET form cards, the plugin panels' empty state and
the grouped card. Headless device-scale-2 evidence of the actual owners only; native windows, fonts, popups and title
bars stay user-run checks and nothing here accepts a page. Fixture names, paths and failures are synthetic.

## Inventory (migration.csv owners in files, notificationui, themesui, shortcuthelp, restart)

| Owner | Before | After |
| --- | --- | --- |
| `notificationui/ErrorsView` (+ `application/ErrorsWindow`) | hand-built shell: draggable split of a 320-wide list and an uncarded detail (header line, sightings, bare message area, bare scrolling trace), button rows Copy/Disable Plugin and Open Log Folder/Clear | one chrome `KitView`: `FormKind.WINDOW` whose body is a `SIDEBAR_DETAIL` split. List column: incidents as two-line rows (danger glyph, summary, source/count/time). Detail column: the manager heading (44 neutral tile, summary in `DETAIL_TITLE`, "source · operation" in `DESCRIPTION`, Disable Plugin for a plugin's incident, Copy), a Message card and a Stack Trace card (caption over value; trace `MONO_SMALL`, at most `ERRORS_TRACE_ROWS` lines before it scrolls), the sightings caption. Footer band: count ("2 errors this session") with Open Log Folder, Clear and the primary Done. Escape is Done; Enter never submits. No incidents: the detail is the standard empty state "No Errors" |
| `notificationui/NotificationCard`, `CardLayer` | 12 px padding, arc diameter 10 (radius 5) drawn with `drawRoundRect` (blurred 1 px), content `BODY` primary, count `BODY` | the kit notice surface is a grouped card: `CARD_RADIUS` 9, crisp pure-stroke hairline, inner padding `CARD_PADDING`+`HAIRLINE` (15); content `DESCRIPTION` in secondary text, count `COUNT`; links, More menu, close, hover, countdown, activation unchanged; the layer's ring shadow follows the radius |
| `notificationui/HistoryPanel`, `HistoryRow` | "No notifications" description text at the top; content `BODY`, count `BODY` | empty history is the plugin panels' empty state (bell tile 37/16, "No Notifications", one sentence) centred in the body, drawn on the tool window surface (`KitView.panelSurface(false)`); rows take the cards' roles; Clear All, sections, read/unread, links and removal unchanged |
| `themesui/ThemeImportView`, `ThemeDialogs`, `ThemeImport` | "Import themes" sheet of bare rows and a text block; result sheet = title + one text block + Close | "Import Themes": a Themes card (one conflict dropdown row per provider, source entry caption) and a Warnings card under section labels, Cancel and the primary Import. Report (`ThemeDialogs.sheet`): an Imported card of theme name and written file rows (`MONO_SMALL`, leading ellipsis), Warnings and Not Imported cards, titled Theme Imported / Themes Imported / Nothing Imported, one primary Close. Failures keep the canonical notice sheet ("Theme Import Failed"). Planning, conflict defaults, frozen commit, busy gating and reload policy unchanged |
| `files/FileChooserPanel` | Places tree and table at 28 px rows with square selection bands on the pane edges, disclosure-arrow section headings, center states as one bold sentence, preview text loose on the dialog, status and buttons on the dialog surface, truncated type dropdown | the chooser keeps its rounded panes, draggable split, overlay scroll and sidecar preview, and presents Places as a manager rail and the listing as a list column (`KitView.managerColumn`): one-line rows at `COLUMN_RAIL_ROW` 31 with the rounded row fill and one-stroke gap, `COLUMN_LIST_SIDE` 8 pane insets, `COLUMN_RAIL_TOP` 10 above the first row in both panes (rows level), rail sections as flat `SECTION_LABEL` headings, Places heading and column titles on the rows' text inset (16). Connecting / Couldn't Open Folder / Empty Folder / No Matches / Loading are a list column's standard empty state (title over sentence, Retry or Cancel connection). Preview text `MONO_SMALL` in a rounded pane under its heading. Status (`CAPTION`) and actions in the window footer band; the type filter shows no stacked label and is never cut. Modified column weight 140 → 150 so dates fit beside the row inset |
| `windows/ChooserPrompts` (the chooser's sheets) | "Replace existing file?" in `BODY`; New Folder a bare field | "Replace Existing File?" canonical question sheet (`DESCRIPTION`, Cancel then the primary Replace); New Folder a form card with one Name row |
| `shortcuthelp/*` | — | unchanged: it is the Settings dialog's Keymap page (`settings_keymap_mock.png`), owned by the settings stream |
| `restart/*` | — | unchanged: no UI of its own; the restart conversation is the plugin manager's banner (settings stream) |

## Kit changes (jasper-ui)

- `SplitKind.SIDEBAR_DETAIL` (no production user before) is the manager without its rail: last child = detail column
  (2), others = list columns (1); list width `MANAGER_LIST_WIDTH` fitted to at most half; no column gap. JavaDoc updated in
  the module and the SDK `Nodes` (documentation only, no shape change, no version bump).
- Panels: a list column without a search row starts at `COLUMN_RAIL_TOP`. Rows: a list column's one-line rows take
  `COLUMN_RAIL_ROW` (two-line rows keep `COLUMN_ITEM_ROW`). Vault list rows are two-line: unchanged.
- Navigation: a manager detail heading's identity keeps `DETAIL_IDENTITY_MIN_WIDTH` (new, 160) or its own width before
  its actions wrap (was squeezed to a character column at narrow widths). Stacks: a manager detail's caption-over-value
  stack uses `DETAIL_VALUE_GAP` (2) instead of `ROW_GAP` (10) — this also tightens the Vault's unmocked SSH-key detail
  rows to the Login rows' gap; the depicted Login detail uses setting rows and is unchanged.
- Stacks: a DROPDOWN in an ACTIONS stack embeds its label (accessible name only). Choices: a natural-width dropdown
  reserves its chevron lane for its longest plain option (`naturalWidth`); stretched and settings-width dropdowns unchanged.
- Trees: a tree in a rail column (`column == 0`, not settings navigation or keymap presentation) shows branches as flat
  `SECTION_LABEL` headings (`SETTINGS_NAV_HEADING` height, no disclosure button, no indent).
- Tables in a manager column: header on the `COLUMN_LIST_SIDE` row inset, a `COLUMN_RAIL_TOP` viewport inset above rows.
- `SurfacePanel.Kind.NOTICE` paints a grouped card (`CARD_RADIUS`, pure-stroke hairline); `SurfacePanel.footerBand` (new,
  host-only) is the WINDOW footer band for native shells.
- Host-only `KitView.managerColumn(ManagerColumn)` (NONE/RAIL/LIST) and `KitView.panelSurface(boolean)`; not in the SDK.
- `DesignMetrics`: new `ERRORS_TRACE_ROWS(12)`, `DETAIL_IDENTITY_MIN_WIDTH(160)` (after `NOTIFICATION_WIDTH`); changed
  `CHOOSER_MODIFIED_COLUMN` 140 → 150; removed (unused) `NOTIFICATION_PADDING`, `NOTIFICATION_ARC`,
  `HISTORY_EMPTY_VERTICAL_PADDING`, `CHOOSER_TOOLBAR_GAP`, `CHOOSER_BOTTOM_GAP`, `CHOOSER_PREVIEW_SIDE_PADDING` and the
  eleven former Errors shell roles (`ERRORS_LIST_MIN_WIDTH` … `ERRORS_BUTTON_GAP`); no `Typography` role added.

## Measurements (logical px, dark 13 pt, 900×540 Errors window, 920×560 chooser)

Errors window against the Vault manager (`task-4b-captures/calibration/vault-manager.txt`): list column 272 (Vault 272);
list rows from y 10 (no search), two-line rows 45, selection inset 8 with one-stroke gap; detail padding 24 (Vault 24);
heading tile 44 at 296 (Vault 44); title/source block at tile + 14 (Vault 546 − 488 − 44 = 14), title 20 / source 18
(Vault 20/18); heading → first card 18 (Vault 18); card padding 15; caption → value 2; trace card → sightings caption 15
(Vault metadata 15); footer status icon at 12, buttons 68 minimum (Clear and Done 68, Vault Done 68). Narrow 600×320:
the heading wraps (tile, identity, actions) instead of squeezing the title; detail scrolls in its own column.

Notification card: text column x 39 (15 + 16 + 8; was 36), close 16 at 360 − 15 − 16; radius 9; hairline crisp (two device
px). History empty: tile 37, glyph 16, text width 206, centred in the body below Clear All.

Chooser: Places heading x 26 and column title x 243 (= first glyph / cell text inset 16 within their panes, which start
at 10 and 214 − 8); first place row and first file row both at y 81; rows 31; preview heading text on the preview pane's
14 inner padding; footer band 8 / 12 padding, status caption at x 12.

## Mocked-pair regression check (before → after)

All 33 `tools/visual/pairs.json` pairs were regenerated from the base (`4980fb60`) and after these commits with
`themeSurfacePreview`, `workspaceSurfacePreview`, `settingsSurfacePreview`, `settings9bPreview`, `commandPalettePreview
--task9c` and the staged picker/vault/add-host/file-server tests. 31 pairs are numerically identical; `host-add`
0.916 → 0.910 and `vault-manager` 0.782 → 0.783 differ only by fixture nondeterminism (the two generated keys' order in
Add Host's ordered list; a generated key fingerprint and the temporary vault path in the Vault manager), confirmed by
pixel diffs of the regions (no geometry change). All 600 renders under `theme-surfaces`, `theme-surfaces9b`,
`workspace-surfaces` and `palette-surfaces` are pixel-identical. Kit gallery (`:jasper-ui:gallery`, dark and light at 2x):
219 of 225 frames identical; `split-SIDEBAR_DETAIL` and `sheet-5` change as intended (the detail column starts at the
manager list width instead of half).

## States rendered and inspected

Dark and Jasper Light at 13, 20 and 32 pt (`jasper-app/build/theme-surfaces9c/{notices,themes,chooser,history-host}/`):
Errors empty / plugin repeated / core / cleared / narrow-short (600×320) / scrolled / trace; every card severity at
auto-hide, hover-close, sticky and its More popup; history empty / new / earlier / expired and the hosted tool window;
theme import multi-provider / narrow-short / choice / actions / conflict popup / committing / success report (+ narrow)
/ nothing imported / error report; chooser OPEN, SAVE and DIRECTORY populated / empty / filtered-empty / narrow-short /
scrolled / edit location / text and binary preview / folder failure / replace declined, connecting / connection error /
retry, and the replace and new-folder sheets. No clipping or overlap at 13 and 20 pt beyond ordinary ellipsis.

## Residual differences and decisions

- The Errors window has no draggable divider (manager columns are fixed, as the Vault's); its detail scrolls in its own
  column and the trace card scrolls inside itself. Done and Escape now close it (as the Vault manager's Done).
- The detail heading tile is neutral: kit category tiles have hue fills only, no danger tile; the list rows carry the
  danger glyph. A severity tile would be a kit artwork addition.
- At 32 pt in 600×320 the WINDOW footer band wraps to two rows (as the Vault manager's does) and the body scrolls.
- The chooser's footer buttons keep their natural widths (Open 64) — the 68 minimum belongs to WINDOW form footers.
- The Notifications tool window keeps the native tool-window surface (`SURFACE_SIDEBAR`) while borrowed-header plugin
  panels paint `SURFACE_PANEL`; the host surface is workspace-stream chrome, not changed here.
- Clear All stays a focusable ghost button in the panel body (an earlier explicit decision and test).
- At 32 pt the evidence harness still saves notification cards 360 logical wide (pre-existing harness width).

## Regenerate

```
./gradlew :jasper-app:test --tests 'dev.jasper.app.notificationui.Task9cNoticeEvidenceTest' --tests 'dev.jasper.app.themesui.Task9cThemeEvidenceTest' --tests 'dev.jasper.app.files.Task9cChooserEvidenceTest' --tests 'dev.jasper.app.workspace.Task9cHistoryEvidenceTest'
./gradlew :jasper-app:themeSurfacePreview :jasper-app:workspaceSurfacePreview :jasper-app:settingsSurfacePreview :jasper-app:settings9bPreview
./gradlew :jasper-app:commandPalettePreview --args="$PWD/jasper-app/build/palette-surfaces --task9c"
./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.StagedAnchoredPickerTest' --tests 'dev.jasper.app.windows.VaultSheetRenderTest' --tests 'dev.jasper.app.plugins.StagedAddHostTest' --tests 'dev.jasper.app.plugins.StagedFileServerFormTest'
./gradlew :jasper-ui:gallery -Pjasper.galleryScale=2 -Pjasper.galleryTheme=dark
python3 -I tools/visual/compare_all.py OUT_DIR
```
