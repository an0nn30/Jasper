# Forms stream acceptance notes (Plan 3 Tasks 1–4 residuals)

Scope: Vault Create/Unlock hero dialogs, the shared FULL forms (Add Host, New File Server), the
anchored credential picker, and the Vault manager, add menu and attached Login/SSH-key sheets.
All evidence is headless actual-owner rendering at device scale 2 against the 2x references; no
image is resized. Native chrome, native popups and font rasterization stay controller-owned.
Nothing here accepts a page: the numbers below are measured residuals for the controller's review.

## Method

Renders come from the real staged owners (`SurfacePreview`, `StagedAddHostTest`,
`StagedFileServerFormTest`, `StagedAnchoredPickerTest`, `VaultSheetRenderTest`). Each pair in
`tools/visual/pairs.json` names the mock content crop and, where the render holds more than the
depicted element (picker faces inside the Import panel, the manager below the headless 28-logical
title row), a render crop. Element bounds were measured on original pixels with run-length colour
profiles along lines that avoid corner radii and text, and with ink bounding boxes/row runs for text
and glyphs. Positions below are logical pixels unless marked "device".

Regenerate and compare:

```
./gradlew :jasper-app:themeSurfacePreview
./gradlew :jasper-app:test --tests '*StagedAnchoredPickerTest' --tests '*VaultSheetRenderTest' --tests '*StagedAddHostTest' --tests '*StagedFileServerFormTest'
python3 -I tools/visual/compare_all.py <outdir>
python3 -I tools/visual/region.py MOCK RENDER OUT.png --crop x,y,w,h [--render-crop x,y,w,h] --region x,y,w,h
```

Render folders: `jasper-app/build/theme-surfaces/calibration/{vault-create,vault-unlock}`,
`jasper-app/build/theme-migration-evidence/{add-host,fileserver-add,anchored-picker}/actual`,
`jasper-app/build/task-4b-captures/<state>` (calibration, dark, light, custom, enlarged, short,
fix1-normal, fix1-enlarged). `fileserver-add` now compares `anonymous.png` (the depicted
Anonymous/write-off state at 540×690); the former `http-anon.png` had Allow changes on.

## Edge agreement (compare_mock, before → after)

| Pair | Before | After |
|---|---:|---:|
| vault-create | 0.925 | 0.949 |
| vault-unlock | 0.972 | 0.977 |
| host-add | 0.860 | 0.916 |
| fileserver-add (Anonymous) | 0.865 | 0.916 |
| vault-manager | 0.617 | 0.771 |
| vault-menu | 0.874 | 0.968 |
| vault-login-sheet | 0.971 | 0.974 |
| vault-key-sheet | 0.949 | 0.967 |
| picker-empty / selected / open-face | 0.986 / 0.875 / 0.548 | 0.986 / 0.875 / 0.882 |
| picker-open-top / open-footer / filtering-top | 0.932 / 0.869 / 0.983 | 0.935 / 0.987 / 0.985 |
| picker-no-matches / disabled | 0.900 / 0.763 | 0.902 / 0.763 |

The agreement is a guide only. Selected/disabled picker faces and the manager differ mainly by
real fixture text (no credential groups, synthetic names and paths, the `More` rail action).

## Shared causes found and fixed

- **Blurred hairlines.** Java2D stroke normalization moved every 1-logical control border half a
  device pixel and smeared it over three device rows. Control faces (`ThemePainting.rounded`, used by
  fields, buttons, dropdowns, picker faces, segment tracks, ordered lists) and popup surfaces now
  paint with pure stroke control and restore the caller's hint, so borders are the crisp 2-device
  hairlines of every reference. Artwork painted afterwards keeps its own policy.
- **FULL forms** start 16 logical from the top (was 17): Add Host and New Tunnel cards begin at
  source 38; File Server's source sits half a logical lower and stays within one device pixel.
- **Form/sheet segments** measure 30 logical with a 24-logical item and a 3-logical inset (track
  border included) in Add Host, File Server and the Login sheet; settings and Plugins identity
  segments keep their geometry. Hue dots keep the 6-logical icon gap.
- **FULL setting rows:** a trailing dropdown is 26 logical (source Group 52 device), so its row is
  the 43-logical floor; FULL settings cards add their closing stroke (Options/Security 152).
- **Dropdown chevrons** use the picker face glyph (10) and trailing inset (8): source ink 12×7
  device ending 12 logical before the edge, previously 15×9 ending 7.
- **SUPPORTING text** uses a 17-logical line with centred leading (File Server help: source line
  pitch 16.5, block centred between segment and divider; now within one device pixel).
- **ORDERED key rows**: index at 12, a 16-logical index column, 12-logical key glyph at 34, name at
  56, 9-logical title gap, 24-logical commands with 10-logical glyphs 31 apart and centred 18 from
  the edge, add row inset 12 with a 10-logical plus and 12-point label; selection fill only while the
  list has focus (the reference depicts none; selection identity and keyboard reordering unchanged).
- **FormRow labels** (lead request) centre on the control's first line, and shorter trailing text
  does too, so a later inline error or caption under a control no longer moves them.
- **Vault manager** three columns: rail rows 31 from a 10-logical top (source fills 30, pitch 31),
  a 30-logical full-size list search with 12-logical glyph, 10-logical gap and 12-logical side inset,
  list rows from 48 with a one-stroke gap below each fill, 10-logical tile/text gap, rail counts on
  the 8-logical row inset; detail heading name/type as one block, heading-to-card 18, card closes its
  last row, rows centred below their top stroke with a 2-logical caption gap and 15-logical sides,
  reveal/copy 10 apart inset 9 (source centres 61.25/23.25 from the card edge, now within 1),
  metadata 15 below the card; masked stored secrets show a fixed spaced row (12 dots, 7 pitch) that
  never discloses length; the window footer band uses the sidebar surface below a crisp hairline,
  groups status with 14-logical gaps and holds a 68-logical minimum action (source Done 67.5).
- **Title strip:** kit views mounted in a native window's title slot (`AuxiliarySurface`) are marked
  `KitView.titleStrip`; icon-only menu buttons there show their menu indicator on the borderless
  control surface (44×28, plus 9 from the left, indicator 13 from the right). Other menus unchanged.
- **Menus:** crisp popup outline; category-tile items use a 10-logical tile gap (source label at
  42.5, was 39.5). **Sheets:** no top stroke (the sheet hangs from the owner's title edge); a path's
  Browse sits on the 12-logical column gap. **Picker:** focused/open faces take the focus border
  inside their halo (source accent border); the footer divider is a crisp menu-border hairline.
- **Artwork:** control-sized `lock` is a plain padlock (picker faces, title Lock); the Unlock hero
  uses `lock.keyhole` with the source's solid 4-logical keyhole. New `clock` for Auto-lock.
- **Free-entry placeholder** (SDK addition): Add Host's empty Group shows the real default group
  name in the placeholder role instead of an empty face; the value stays blank.

## Remaining measured differences (after)

Create Vault: all anchors within one device pixel except the bind card (55 source vs 54, edges at
half-pixel 270.5/325.5 vs 271/325) and the placeholder x (source 39, render 38). Text ink/weight and
eye/shield glyph AA remain font/raster differences.

Unlock Vault: within one device pixel; the render paints the focused caret, the source frame has none.

Add Host (540×732): Connection card and all four fields exact; Authentication card, segment, picker
and ordered keys +1 device; Options card and rows +2 device (source section pitch 41.5, render 42).
Port 73 vs source 71 (File Server's source port is 82: one fixed PORT width cannot meet both).
Cancel 69 vs 70.5 and Add Host 87 vs 87.5 (Swing text advance vs source ink). Domain-limited:
captions read `Stored key` (no algorithm metadata), Group placeholder reads the real default group
(`Other`) where the mock shows `None`, fixture key names differ.

New File Server (540×690, Anonymous): every card, field, segment, row and footer button within one
device pixel. Choose 83 vs 79.5 and Folder 259 vs 261.5 (advance-based button width; Browse in the
key sheet measures 82 vs 79); port 73 vs 82; TFTP replaces the mock's RSYNC (approved).

Picker: compact face 190×22 and popup 444 envelopes unchanged. Search box kept at 21 logical (source
outline 22 at 5.5): growing it moved the query text 1 logical off the source, so the calibrated text
alignment wins. Rows/headings/check content are fixture-limited (no groups). Disabled opacity 45%.

Vault manager: rail, search, list fills and dividers within one device pixel; detail title ink -1,
type ink -2 logical (the label's painted baseline sits above its box metric); copy/trash glyph ink
12 vs 10 and the status key glyph 11 vs 9 (artwork proportions); Edit 51 vs 52.5; mask dots 1.75
higher; status text 1.25 above the source (the source baseline-aligns it with Done). Domain: `More`
rail action, provider text `keychain`, no Used-by. Title bar is native and outside the crop; the
title add button is 28 tall where the source draws 26.

Add menu: within one device pixel. Login/SSH-key sheets: within one device pixel except Browse width
(above) and the key sheet's retained +1 device origin.

## Unmocked states inspected

Dark, light, custom and enlarged (system 20) manager/menu/Login/key captures; 45-logical short
owner; narrow 400/540 manager at normal and enlarged fonts; Add Host enlarged/light/narrow-short;
File Server short/enlarged/light; picker light/enlarged/short/narrow. No new clipping: masked dots
now stop at their value width, enlarged title-strip menu shows its indicator, narrow layouts keep
their existing stacking. The extreme enlarged 400-device manager still stacks its footer below the
visible height, as before.

## Contract notes

- SDK/module `Nodes.ChoiceSearch` gains `placeholder` (requires free entry; old constructor keeps
  ""), translated in `KitValues`, documented in `docs/plugin-authoring.md`. No version bump.
- App-only `KitView.titleStrip(boolean)`; testkit `FakeKit` knows `clock` and `lock.keyhole`.
