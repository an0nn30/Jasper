# Settings stream acceptance notes

Scope: the five Settings pages against their Desktop mocks (Plan 3 Task 9 residuals and the early Task 11a
Settings/typography calibration), under the binding
[Settings theme-policy amendment](../../../superpowers/specs/2026-10-11-jasper-settings-theme-policy-amendment.md).
Owned surfaces: `dev.jasper.app.settingsui`, the Plugins page composition (`dev.jasper.app.pluginmanager`), the
Keymap page (`dev.jasper.app.shortcuthelp`), and the kit pieces SettingsShell, Trees (settings navigation and keycap
tree), NumericControls, PreviewChoices, RichOptions, SearchChoices, BreadcrumbControls, Keycaps, KeycapSequence,
TreeRowPresentation and the SETTINGS form and row geometry.

## Regenerating the evidence

```
./gradlew :jasper-app:settingsSurfacePreview   # Appearance, Window, Notifications (+ popup) in 8 theme/font families
./gradlew :jasper-app:settings9bPreview        # Keymap and Plugins states in 8 theme/font families
python3 -I tools/visual/compare_all.py <outdir> settings
python3 -I tools/visual/region.py MOCK RENDER OUT.png --crop x,y,w,h --region x,y,w,h
```

Renders land in `jasper-app/build/theme-surfaces/settings/<family>-<font>/{appearance,window,notifications}/` and
`jasper-app/build/theme-surfaces9b/<family>-<font>/` (`keymap-recording.png`, `plugins-content.png`, narrow, short,
collapsed, captured, staging and popup states). Each PNG has a `.txt` geometry dump beside it; the notifications
popup also writes `popup-geometry.txt` with the menu list, viewport and cell sizes. Families: `calibration`
(the test-only Slate Steel family that approximates the mock colours), `dark`, `light`, `custom` (Copper Night), each
at the platform font size (`-0`) and at 20 pt (`-20`). Mock crops: Appearance `4,6`, Window `4,2`, Notifications
`8,2` (popup open), Keymap `8,6`, Plugins `0,0,1488,1364`.

## Edge agreement (compare_all, calibration-0)

| Mock | Before (18b9dc36) | Final |
|---|---|---|
| Appearance | 0.579 | 0.873 |
| Window | 0.634 | 0.802 |
| Notifications (popup) | 0.513 | 0.845 |
| Keymap (recording) | 0.413 | 0.837 |
| Plugins | 0.736 | 0.808 |

The remaining disagreement is mostly text rasterization, the residuals below and fixture differences; element bounds,
insets, baselines and control sizes were measured with row/column ink probes and are within 1 logical px except where
listed.

## Decisions

- Typography: the Settings face is SF (`.AppleSystemUIFont`). Navigation rows and the breadcrumb ancestors use
  `SETTINGS_BODY` (13), row titles and values `LABEL` (12.5), theme tile captions `SETTINGS_PREVIEW` (12), descriptions
  `SETTING_DESCRIPTION` (11), section labels the Helvetica Neue Medium `SECTION_LABEL` (11). The global `BODY`
  remains Helvetica Neue (from the WIP commit, shared with other surfaces); Settings uses `SETTINGS_BODY` instead.
- Settings surfaces are marked in the kit, not by the app: `SettingsShell` marks its search, navigation, header,
  banner and footer kit views; a page view outside a `SETTINGS_PAGE` stack (the Keymap search, the Plugins search)
  uses `SettingsShell.settingsPage(view)`. Settings search fields leave 8 px (`SETTINGS_SEARCH_ICON_GAP`) after the
  magnifier.
- Settings dropdowns: fixed width (170 rich, 190 plain), values at `LABEL`, a 10 px chevron 10 px from the trailing
  edge, a 7 px gap before an annotation ("Default"), menu rows exactly the menu's padded width (a fixed list cell
  width, so a row measured before layout never overflows the menu), a 9 px check, and a wrapped menu row that grows
  only by its lines.
- Steppers show the value and its unit centred ("34 px"); plugin integer settings use the same stepper, with the
  unit from the new `SettingSpec.unit(String)`.
- List editors in a Settings card no longer widen the card: a row's fixed width is label + gap + the control's
  natural width (the Shell History card was 692 wide; now 620, covered by
  `SettingsControlsTest.settingsListEditorsDoNotWidenTheCard`).
- Keymap: keycaps are 23 x 20 with a translucent outline (`text.tertiary` at 34 %) heavier along the base and a
  translucent face (`hover.overlay` at 5 %), so they read on any row background. The armed recorder's dot is 8 px in a
  14 px halo (`status.danger` at 20 %). The inline editor rows are 8 apart and the edit card stops 2 px before the next
  row. Read-only contextual rows use `text.secondary` titles. The status line is one navigation row.
- Plugins: group headings sit 6 px above the first row of a 34 px band; the switch is 7 px from the row edge; the
  search bar's gear sits 10 px in from the rows' edge, 6 px after the field; the details header is 18 px above the
  section choice; the state dot stays beside its label; the overview is a styled pane with 20 % line spacing
  (20.5 logical pitch, matching the mock's 41 device px).

## Residual differences

All values are logical px unless marked "device".

Appearance
- "System (SF Pro)": the font picker shows the stored family label ("System"); the mock's resolved family name is not
  modelled.
- Opacity slider thumb: the mock places 90 % at 80 % of the track (a different range); ours uses the real range.
- Chevron 0.5 to the left of the mock (one inset serves both mocks; the Notifications mock wants it 1 further in).

Window
- Reset buttons on Toolbar height, Status bar height and Tab height: the harness values differ from the real defaults,
  so the real "differs from default" rule shows them. Panel rail width's minus is disabled at the real minimum.
- Initial size steppers: ours are text + padding (129 columns: 3.5 wider; 47 lines: 6 narrower). The mock's two value
  boxes do not follow one rule (7 px padding on one, 13 on the other), so the rule was kept.

Notifications
- The harness anchors the menu at the combo's lower edge; the mock's menu sits 3.5 lower.
- The selected two-line row ("Sticky card") is 30 tall; the mock keeps 27 with its text running into the row padding.
- "0 turns these notifications off." keeps its period (product copy).
- Headless renders paint no focus ring on the open dropdown.

Keymap
- Search placeholder copy differs from the mock (product copy); its text starts 2 left of the mock, whose magnifier
  sits 1.5 further in than on the Settings sidebar search (one Settings rule kept).
- Recorder: no focus ring headless; prompt 1 right of the mock.
- Hint copy differs and Stop / "···" stay beside it (amendment).
- Status line: count is fixture data (105 vs 35); the lock is 1 larger and its label 2.5 further from it (the shared
  navigation gap and caption icon box).
- Data: "Context Menu" vs the mock's "Menu"; Close auxiliary window is locked in the real keymap (so its title is
  quiet), the mock shows it editable.
- Keycap tones within 3 levels of the mock; caps 0.5 lower.

Plugins
- Titles use Helvetica Neue Medium (JBR exposes only SF Regular and Bold): "Credential Vault" is 3 narrower; caption
  text is about 2 % narrower than the mock, so "Enabled" ends 4 earlier.
- Disable button 1.5 narrower; the section choice track 1 taller (0.5 each side).
- Footer 1 lower: the mock crop includes the window's bottom edge (its content is 1359 device px tall, ours 1364).
- The gear's "more" glyph has smaller dots than the mock (shared icon).
- Light theme: neutral identity tiles' glyphs are low-contrast (`hue.neutral` in the light theme; identity hues are
  Task 10).

Shared with every page
- No SF Semibold/Medium under JBR, so 600-weight roles use Helvetica Neue Medium with tracking.
- Mock text colours are the calibration theme's; production colours come from the active theme's roles.
