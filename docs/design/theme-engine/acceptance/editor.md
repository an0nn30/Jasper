# Plan 3 Task 8 — Editor and remaining plugin UI (stream `editor`)

Canonical composition for the Editor plugin's standard UI and the remaining plugin UI (Sample, Shell History,
Snippets, Project Index). None of these surfaces has a dedicated mock; they reuse the components and rules the mocked
surfaces calibrate. Headless device-scale-2 evidence only: native focus, fonts, popups and window chrome remain
user-run checks, and no page is accepted here.

## Inventory (migration.csv rows with composition task 8)

| Owner | Before | After (Task 8) |
| --- | --- | --- |
| `editor/ui/FindBar` | INLINE stack of a SEARCH field, ghost text buttons Previous/Next/Close, text toggles `Aa`/`W`/`.*`, SEARCH replace field | One compact document bar row: query fills, `match.case`/`match.word`/`regex` icon toggles, count, `chevron.up`/`chevron.down`/`close` icon buttons with Title Case tooltips; replace row is a TEXT field aligned under the query with secondary Replace / Replace All |
| `editor/ui/EditorTab` | banner INLINE row at x 0; failed state titled by the failure sentence, Retry and Close both secondary | banner and Vim strip are kit document bars with a hairline against the document; failed state is the canonical empty state: "Could Not Open File" over the reason, Close (secondary) then Retry (primary); loading unchanged |
| `editor/vim/ui/VimBar` | output rows 28 px list rows at x 6, strip at x 0 | strip is a document bar (status fills, showcmd keeps a stable 84 px status width); output rows are text lines (20 px pitch) aligned with the strip at x 6. Descriptor unchanged |
| `editor/ui/Forms` (questions) | buttons in choice order, the default (often first) PRIMARY left-most | the default is the right-most PRIMARY; others keep their order, ids and answers by index ("Don't Save, Cancel, Save"; destructive "Overwrite, Reload, Cancel" unchanged) |
| `editor/ui/GoToLinePanel` | bare STANDARD field labelled "Line (1–N):" | SHEET form card with a "Line" FormRow captioned "1–N" (multi-line label stays beside the field when the error shows); accessible name "Line (1–N)" |
| `editor/ui/LanguagePicker`, `SaveWait`/`Forms.ProgressView`, `DialogAsker`, `StatusLine`, `EditorSettings` | — | Composition unchanged (already canonical FULL picker / progress / host status items / grouped declarations); covered by new composition tests and 2× evidence |
| `sample/SampleSettingsPanel` | `Form(SETTINGS)` of bare host rows (no cards, text Reset buttons, field labels repeated) | `SETTINGS_PAGE` stack: Demos and Timing section labels over settings cards |
| `sample/SamplePlugin` | panel body: description + PRIMARY "Run sample activity"; About: body text + Details…; details sheet: body text + secondary Close | panel body and About window are kit empty states (tile, title, sentence, one secondary action); details sheet is a form card (Version row) with one PRIMARY Close; Title Case "Sample Details" |
| `indexer/ui/IndexerSettingsPanel` | `Form(SETTINGS)`: Projects table+actions, then bare host rows | `SETTINGS_PAGE`: Projects table/actions or the standard "No Projects" empty state with Add Folder…, then Detection and Indexing settings cards; the right-aligned Files column is last (no heading collision) |
| `history/HistorySettings` | generated card without a label | both settings declare the "Ranking" section (declaration order unchanged) |
| `history/HistoryScope`, `snippets/SnippetsScope`, `indexer/ui/FilesScope` | palette rows | unchanged: palette-only consumers (one-line title/detail/tag data, palette-owned density and scope interaction); no manager windows added |
| app `plugins/KitSettingRows` + testkit `KitSettingRows` | host rows: INLINE [control, text "Reset"] | the generated pages' arrangement: `SETTINGS_TRAILING` [control, icon-only Reset (`refresh`) shown while the value differs from its default]; ids and events unchanged |

## Kit, SDK and testkit changes

- `DesignMetrics` (new roles after `FIND_GAP`): `DOCUMENT_BAR_VERTICAL_PADDING(4)`, `DOCUMENT_BAR_SIDE_PADDING(6)`,
  `DOCUMENT_BAR_GAP(8)`, `DOCUMENT_BAR_ACTION_GAP(3)`, `DOCUMENT_BAR_STATUS_WIDTH(84)`, `DOCUMENT_BAR_FILL_MIN_WIDTH(120)`,
  `DOCUMENT_BAR_LINE_GAP(4)`. Side padding 6 matches the terminal find bar's `FIND_SIDE_PADDING` and list row text inset.
  No existing metric value changed; no `Typography` role added.
- `Stacks.DocumentBarLayout` (new), `Stacks.StackControl.documentBar/documentBarGroup`, `InteractiveRows.documentLines`,
  `ViewReconciler.applyDocumentBars` and `SplitViews.SplitSurface` hairlines: presentation by structure inside a
  `SplitKind.DOCUMENT` split only (Editor-only). Bar fields reuse the existing panel-search presentation flag
  (`PANEL_SEARCH_HEIGHT` 26, `PANEL_SEARCH` type) and bar buttons the existing panel action flag (`PANEL_HEADER_ACTION` 24),
  so they follow the panels stream's calibration of those shared looks.
- New built-in glyph `match.word` (kit `IconRegistry`, original path) and testkit `FakeKit` built-ins.
- SDK and module `Nodes.SplitKind` JavaDoc describe the bar presentation (documentation only; no shape change, no version
  bump). `docs/ui-architecture.md` gains a "Document bars" paragraph; `docs/plugin-authoring.md` example block re-synced
  with the Sample source.

## Measurements (device px at 2×, logical = /2)

- Find query vs the integrated-panels mock's "Search sessions" field (`Screenshot … 11.16.50 AM.png` crop 176,206): both
  52 device px tall (26 logical); placeholder/text start 62 vs 60 device px (1 logical). Residual: the panel search
  glyph renders ~4 device px smaller and lighter than the mock's (shared panel-search glyph, panels stream).
  Side-by-side: `editor-hosted-2x/compare/search-field.png`.
- Find bar (760 logical): row 34, field 26 at x 6, toggles 24×24 three px apart, status 84 wide, chevrons/close 24×24,
  close ends at 754 (6 px trailing). Replace field x/width equal to the query (484); Replace starts under Match Case
  (x 498). Enlarged font (20): row 51, field 39, close 36×36, nothing clipped, alignment kept.
- Banner 32 tall (text 6, actions 24 ending at 754); Vim strip 23 tall, output line pitch 21.
- Go to Line SHEET 482 wide (480 + border), card at 25 (FORM_SIDES 24), field at label column 96 + gap 12.
- Empty state (Sample panel vs mock "No File Servers", panel width 260): tile 80 vs 74 device, description pitch 32
  vs 30, description→button 14 vs 32, button 56 vs 54 device. These are the shared `EmptyStates` calibration owned by the
  panels stream; the Sample/Project Index/Editor states use that component unchanged and will follow its calibration.
- Plugin Settings pages (980×681 dialog, all four themes incl. font 20): first card x 262, width 618, identical fill and
  page surface for Editor (generated), Sample and Project Index (plugin panels) and Shell History (generated) —
  asserted by `PluginSettingsPagesEvidenceTest`, recorded in `settings/cards.txt`.

## Residual differences and decisions

- Shell History's card is 692 wide (others 618): the generated ordered-list editor (`Editors.textList`) widens its own
  card; Settings-stream owned.
- Plugin integer settings show the shared NUMBER field, not the mocks' steppers with units; the generated
  `PluginSettingsPage`/host rows have no unit or stepper hint in the SDK. Left to the Settings stream / an SDK addition.
- Project Index text-area settings (`StringList` host rows) differ by the scroll bar's width (202 vs 192 logical) in the
  shared `SETTINGS_TRAILING` slot.
- FormRow centers its label on the whole row, so a single-line label drops when an error appears, and a trailing text is
  top-aligned; Go to Line therefore uses the label caption (canonical multi-line label) instead of a trailing "of N".
  The shared FormRow behaviour is the forms stream's.
- The Language picker keeps its FULL form without buttons (baseline "no default"); its list rows use the shared form-list
  painting (square selection band), as the Vault credential picker.
- The Saving progress keeps its visible "Saving" caption (the progress's accessible name).
- Editor question titles/messages are session copy (sentence case, e.g. "Unsaved changes"), unchanged.
- Light-theme 2× captures configure the look and feel to the same theme; without it the native document scroll bar
  track renders dark (harness artifact, not production).

## Verification

Full `./gradlew check` (all five architecture gates, doclint and compiled documentation examples) passed headlessly in
7m 43s on this branch: 818 XML files, 9,988 tests, 0 failures, 0 errors, 6 existing skips (jasper-app 2,740/1 skip,
jasper-ui 557, plugins/editor 4,822, sample 14, indexer 98, history 41, snippets 11, sdk-testkit 151, others unchanged).
`AppDocumentationTest` and `ThemeMigrationCoverageTest` were rerun after the final documentation edits. Changed Java
sources pass the AGENTS.md character-hygiene scan. No GUI, benchmark or AOT task was run.

## Regenerate

```sh
./gradlew :jasper-app:test --tests '*EditorCompositionTest' --tests '*SampleSurfacesEvidenceTest' --tests '*PluginSettingsPagesEvidenceTest'
./gradlew :jasper-ui:test --tests '*EditorKitTest*'
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.*' :jasper-plugin-sample:test :jasper-plugin-indexer:test :jasper-plugin-history:test
python3 -I tools/visual/region.py <panels-mock.png> jasper-app/build/theme-migration-evidence/editor-hosted-2x/dark/find-replace.png \
  jasper-app/build/theme-migration-evidence/editor-hosted-2x/compare/search-field.png --crop 176,206,500,74 --render-crop 4,466,500,74 --region 0,0,500,74
```

Images: `jasper-app/build/theme-migration-evidence/editor-hosted-2x/{dark,light}/` (document, find/replace/options/
focus/invalid/no-matches/narrow/after-theme/font20, banners, Vim command/output, loading, load-failed, questions,
save-failed, Go to Line valid/invalid, Language, Saving, Sample panel/About/details) with `.txt` anchor bounds, and
`…/editor-hosted-2x/settings/{calibration,dark,light,dark-font20}/` (editor, sample, indexer, history, indexer-scrolled,
sample-narrow). The `<panels-mock.png>` path is the integrated-panels reference (its name has U+202F before "AM").
