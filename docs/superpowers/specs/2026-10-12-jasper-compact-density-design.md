# Jasper compact density — design

Status: **Draft for review (2026-10-12).** Design sections approved in conversation; this written spec awaits the
user's review before an implementation plan is written.

## Goal

Jasper should feel like a traditional desktop developer workstation (IntelliJ classic), not a touch-optimised app.
The Slate Steel theme and the mocked compositions stay; radii, spacing, row heights and control sizes tighten across
the whole application, and a handful of surfaces are fixed. This is not a redesign: colours (one change below),
typography, layout composition and behaviour stay.

## User decisions

1. **The Desktop mocks fix composition only**: which controls a surface has, their order, grouping and alignment.
   Sizes, radii, spacing and row heights now come from the compact scale below, everywhere. Cards, grouped sections
   and the other mocked structures stay. The mock comparisons are re-baselined; lower edge agreement from changed
   sizes is expected.
2. **Selected and highlighted rows show white text and white icons** (`text.onAccent`) in both variants, as IntelliJ
   does. Slate Steel Light's `selection.bg` changes from `#5482B9` to the Light accent `#3F6FA8`, so white reads at
   5.18:1 (Dark's `#2E4664` gives 9.65:1).
3. The compact scale (section 1), the menu and dropdown design (section 3) and the specific fixes (section 4) were
   approved as presented.
4. Type sizes are unchanged. There is no density setting: Jasper has one, compact, density (an MVP; no compatibility
   modes).

## Non-goals

- No change to typography roles, colour roles (except decision 2), icons' artwork, or the terminal's own rendering.
- No change to native window chrome (macOS frame, traffic lights, window corner radius).
- No density preference, no per-surface opt-out.

## 1. The compact scale

All geometry lives in `DesignMetrics` (jasper-ui), and FlatLaf's geometry keys are derived from it by
`FlatLafInstaller`; the change is a retuning of existing roles, not a second scale. Values are logical pixels.
Roles named `*_ARC` are diameters and change with the radius they describe.

| Group | Roles (now → compact) |
| --- | --- |
| Controls | `BUTTON_HEIGHT` 28 → 24, `FIELD_HEIGHT` 28 → 24, `PICKER_DEFAULT_HEIGHT` 28 → 24, `CONTROL_RADIUS` 6 → 4 (buttons, fields, dropdowns, picker faces, steppers); buttons as on `main`: label margins 2/14, minimum width 72 |
| Hero and footer controls | `HERO_FIELD_HEIGHT` 32 → 28, `HERO_BUTTON_HEIGHT` 30 → 28, `FULL_FOOTER_BUTTON_HEIGHT` 30 → 24 |
| Selection | `MENU_ITEM_RADIUS` 5 → 0, `PANEL_ROW_RADIUS` 4 → 0, `RESULTS_SELECTION_ARC` 8 → 0, `RESULTS_SELECTION_INSET` 4 → 0, and every other selection fill: a rectangle edge to edge (section 2) |
| Menus and dropdown rows | `MENU_ITEM_HEIGHT` 29 → 22, `PICKER_ROW_HEIGHT` 28 → 22, `PICKER_UTILITY_HEIGHT` 26 → 22, `PICKER_SEARCH_HEIGHT` 21 → 22, `PICKER_SEARCH_RADIUS` 5 → 4, `MENU_ITEM_PADDING` 9 → 8, `MENU_SEPARATOR_MARGIN` 4 → 3, `MENU_TILE` 18 → 16, `MENU_TILE_GAP` 10 → 8, `RESULTS_ROW_HEIGHT` 24 → 22 |
| Settings navigation | `SETTINGS_NAV_ROW` 29 → 24, `SETTINGS_NAV_HEADING` 24 → 22 |
| One-line list rows | `COLUMN_RAIL_ROW` 31 → 24, `KEYCAP_TREE_ROW_HEIGHT` 36 → 26, `KEYCAP_TREE_GROUP_HEIGHT` 34 → 26, `ORDERED_ROW_HEIGHT` 35 → 28, `PANEL_COMPACT_ROW_HEIGHT` 40 → 28 (panel rows with one line), and the other one-line row roles to 24–28 |
| Two-line list rows | `COLUMN_ITEM_ROW` 45 → 36, `IDENTITY_ROW_HEIGHT` 47 → 38, `IDENTITY_HEADING_HEIGHT` 34 → 28 |
| Settings rows | `SETTINGS_ROW_HEIGHT` 45 → 36, `SETTINGS_GROUP_ROW_HEIGHT` 43 → 34, `SETTING_HEIGHT` 42 → 34, `SETTINGS_GROUP_HEIGHT` 39 → 32, `SETTINGS_OPTION_HEIGHT` 29 → 24 |
| Segmented controls | `FORM_SEGMENT_HEIGHT` 24 → 22, `SEGMENT_HORIZONTAL_PADDING` 4 → 10, `SEGMENT_TRACK_RADIUS` 7 → 4, `SEGMENT_ITEM_RADIUS` 5 → 3 |
| Containers | `CARD_RADIUS` 9 → 6, popups and menus radius 6, `PALETTE_CORNER_ARC` 12 (radius 6 stays), `WINDOW_RADIUS` 12 → 8 (the modal overlay's card), `HERO_TILE_RADIUS` 13 → 8, `PANEL_EMPTY_TILE_RADIUS` 5 → 4 |
| Small parts | `CHIP_RADIUS` 4 → 3, `KEYCAP_RADIUS` 4 → 3, `ORDERED_RADIUS` 7 → 4, `TITLE_SEARCH_ARC` 12 → 8, `RAIL_BUTTON_ARC` 10 → 8, `DOCK_DROP_ARC` 10 → 8 |
| Command palette | `PALETTE_FIELD_HEIGHT` 44 → 36, `PALETTE_STEP_ROW_HEIGHT` 40 → 32; its card, hint bar and composition stay |

Every other padding, gap and inset role (row sides, card insets, section gaps, detail gaps, empty-state gaps)
shrinks by about 0.8, rounded to whole pixels, never below 2 where it is above 2 today. The plan lists every role
with its old and new value; a role whose value is fixed by something other than density (focus ring, hairline,
shadow strengths, icon sizes, the terminal grid) keeps its value.

FlatLaf keys derived from the scale (installed by `FlatLafInstaller` with the other fixed extras): `Button.arc` and
`Component.arc` = 2 × `CONTROL_RADIUS`, `TextComponent.arc` likewise, `Button.margin` 2,14,2,14, `Button.minimumWidth`
72, `Button.minimumHeight` / `Component.minimumHeight` from `BUTTON_HEIGHT` / `FIELD_HEIGHT`,
`{List,Tree,Table,ComboBox,MenuItem}.selectionArc` 0 and `.selectionInsets` 0,0,0,0, `MenuItem.minimumHeight` 22,
menu item margins 2,8,2,8, `PopupMenu.borderInsets` 4,0,4,0, `Popup.borderCornerRadius` 6.

## 2. Selection and highlight

- Every selected or highlighted row (lists, trees, tables, dropdown popups, pickers, menus, the Settings navigation
  including its section headings, command palette results, panel rows, manager rails and lists) fills a plain
  rectangle across the full row width with `selection.bg` (menus and the palette highlight use `accent`, as now) —
  no inset, no radius.
- On that fill, text takes `text.onAccent` (secondary text `text.onAccent.secondary`) and kit glyphs are tinted
  `text.onAccent`; hued tiles show a white glyph on a translucent white tile; plugin-authored artwork keeps its own
  colours. A disabled item keeps `text.placeholder`. A row's focus is shown by its fill, not by an extra outline.
- Slate Steel Light `selection.bg` becomes `#3F6FA8` in `slate-steel.json` and the colour reference
  (`docs/design/theme-engine/slate-steel.md`), kept identical by the existing test. White text on a selected row then
  reads at 5.18:1 (Light) and 9.65:1 (Dark); the Light design note about 3.98:1 selection text is retired, and so is
  the contrast floor that gave Light selected rows dark text (Plan 3's `ItemLists` rule becomes plain
  `text.onAccent`). Menus and the palette keep theme.md's `accent` highlight: white on it is 5.18:1 in Light and
  4.27:1 in Dark, as authored (a design note, like the other authored values below the former floors).

## 3. Menus and dropdown popups

- **One look for kit menus (`Menus.Popup`) and FlatLaf's native menus** (tab menus, the editor's popup, the app's
  own context menus): rows 22, side padding 8, a 16 px icon column present only when some item of that menu has an
  icon, the label, a right-aligned shortcut in `text.tertiary` written with the platform's glyphs (⌘⇧H; Ctrl+Shift+H
  elsewhere), a submenu arrow; separators with 3 px margins; the popup radius 6 with a `border.menu` edge, 4 px top
  and bottom padding and theme.md's black shadow.
- **Width**: sized to content, minimum 160, never stretched beyond its widest row.
- **Highlight**: section 2's rectangle; label, icon and shortcut turn white.
- **Shortcuts**: kit `MenuItem` and SDK `dev.jasper.sdk.ui.MenuItem` gain an optional shortcut source, the id of an app
  action or a contributed action; the host renders that action's current keymap binding, so a rebinding shows in
  every menu and an unbound action shows nothing. App-native menus set accelerators from the keymap the same way.
  The SDK minor version increases (from 0.15.5) and `jasper-sdk-testkit`'s fake mirrors the field and its contract.
- **Dropdown popups** (dropdowns, searchable choices, the credential picker, rich options): rows 22, icons kept, the
  same rectangle highlight with white text and icons. **A popup is exactly as wide as the field that opened it**
  (wider only if its widest row needs it) and uses the field's own type size, so the credential picker matches its
  face; `PICKER_WIDTH` and `PICKER_FACE_WIDTH` as fixed popup widths go. A picker's search row is a 22 px field with
  radius 4.

## 4. Specific fixes

- **Settings › System › Configuration file** becomes one card of settings rows (title and description left, compact
  buttons right): *Location* (the path in the mono role, middle-truncated, the full path as its tooltip; [Open in
  Editor] [Reveal]); *Reload from disk* ("Read the file again after editing it elsewhere."; [Reload]); *Migrate old
  settings* ("Rewrite old setting names to the current layout; the original is backed up first."; [Migrate…],
  disabled with "Nothing to migrate" when the file is current). `SchemaPage` gains this action-row form for any page.
- **Steppers for decimal settings**: `terminal.font.size` (step 0.5, "pt"), `terminal.font.line_height` (step 0.1),
  and the interface font size use the stepper presentation (− value +) that integer settings already use, at the
  compact size; typing still works and the value keeps up to two decimals.
- **Segmented controls**: a segment's label is never closer than `SEGMENT_HORIZONTAL_PADDING` (10) to its edge; a
  segment's minimum width is its label plus both paddings.
- **Settings navigation headings** follow section 2 when focused or selected.

## 5. Where the changes live

- `jasper-ui`: `DesignMetrics` values; `FlatLafInstaller`'s derived extras; the selection painters (`InteractiveRows`,
  `ItemLists`, `Trees`, `PickerMenus`, `RichOptions`, `SearchChoices`, `Menus`, `Choices`); `MenuItem`'s shortcut
  source; `slate-steel.json`. Boundaries unchanged (`dev.jasper.ui.theme` stays JDK-only).
- `jasper-sdk` / `jasper-sdk-testkit`: `MenuItem`'s optional shortcut source, the version, the fake and contract.
- `jasper-app`: rendering kit menu shortcuts from `KeyBindings` (the plugin host translates SDK menu items),
  accelerators on app-native menus, the System page action rows, the decimal steppers, any app painter that draws a
  selection or a fixed size of its own.
- Plugins compile against the SDK only and pick up the scale and the menu look through the kit; a plugin that wants a
  shortcut beside a menu item names the contributed action.

## 6. Testing and acceptance

- Unit and render tests pin each rule: control heights and radii from the metrics; FlatLaf's derived keys; selection
  fills that cover the full row width with no rounded corner pixels; white text and icons on selected and highlighted
  rows in both variants (selected rows ≥ 4.5:1; the Dark accent highlight pinned at its authored 4.27:1); menu row height, shortcut rendering and minimum width; a dropdown or picker popup as
  wide as its field; the System page action rows; the decimal steppers; segment padding.
- `UiColorKeysTest`'s frozen table is unaffected (roles, not colours); the Light `selection.bg` change updates the
  Slate Steel palette tests and the theme.md block together.
- The kit gallery and the 34 mock comparisons are re-rendered; acceptance is judged on composition (order, grouping,
  alignment) with sizes from the scale, and the acceptance record gets a compact-density section with the new values.
- Light and Dark renders are inspected; the native pass (density, menus, dropdowns, selection, Settings) is the
  user's.

## 7. Risks

- Fixed pixel offsets inside painters that assumed the old heights (optical offsets, baselines, icon centring) can
  misalign at the new sizes; the render tests and inspection must cover each family.
- Text that fit at 28 px may clip at 24 px in enlarged interface fonts; heights stay minimums, and rows grow with
  their content as today.
- Plugins with their own Swing layouts inherit FlatLaf's new sizes; bundled plugins are checked in the re-render.
