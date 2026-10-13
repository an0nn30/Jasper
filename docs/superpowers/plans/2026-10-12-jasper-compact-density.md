# Jasper compact density — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status: complete on 2026-10-13** on `claude/compact-density`, executed natively. Deviations from the text below
> (ledgered as rulings):
> - Task 1:
>   - List rows pad their lines with `ROW_VERTICAL_PADDING` (4), not the form's `ROW_GAP`, in `InteractiveRows` and
>     `ItemLists.minimumRowHeight`, so one-line rows reach 24 and two-line rows max(36, two lines + 4).
>   - Four roles took different values: `PANEL_HEADER` 35 (host actions with their focus reservation need it, and
>     every header must match), `ROUTE_HEIGHT` 42 and `PALETTE_FIELD_VERTICAL_PADDING` 3 (the palette field reaches 36).
>   - `NOTICE_*` kept 8/12/8, as native document-bar chrome.
>   - The table script stripped comments before splitting the enum.
>   - Literals whose role was not evident took the checked new value.
> - Task 2:
>   - `SETTINGS_NAV_ROW_SPACING` was deleted with the rounded painters' roles.
>   - A selected item-list row's subtitle keeps the slot rule (`text.onAccent.secondary`).
>   - `PickerMenusTest`'s install test now restores the look and feel. It never did, and a new class's fork order
>     exposed that as headless popups.
> - Task 3: native menu items use the BODY face, like kit menus.
> - Task 5: the dropdown width rule is `KitComboPopup.popupWidth(fieldWidth)`. `Component` already has `bounds()`, and
>   `BasicComboPopup.computePopupBounds` reads screen bounds, so headless tests test the width rule alone.
> - Task 7: the Settings preview also renders System and Font & Colors.
>   - No painter needed a fix after inspection.
> - Final review fixes (each with a regression test):
>   - Tab or Shift+Tab onto a list forgets the row control it last focused, so focus no longer bounces back to it.
>   - Dropdown and picker popups whose rows scroll add the scrollbar's width, so the widest row stays whole.
>   - A switch or chip on a selected row is inked for the fill.
>   - The plugin guides' manifest ranges admit SDK 0.16.

**Goal:** Make Jasper feel like a compact desktop workstation (IntelliJ classic): smaller radii, tighter spacing and
rows, rectangular edge-to-edge selection with white ink, one compact menu look with shortcuts, dropdown popups as wide
as their fields, and the Settings fixes. Composition, colours (one change), type and behaviour stay.

**Architecture:** One scale: the existing `DesignMetrics` roles are retuned (no second scale), the content-height
padding rule moves into `DesignMetrics.contentPadding`, and `FlatLafInstaller` derives FlatLaf's geometry from the
roles. Selection is painted once, by the list canvas across the list's full width; rows draw white ink from a flag
set before they are populated. Kit and native menus share one look; the kit context resolves menu shortcuts from the
app's keymap. Each task leaves `./gradlew check` green.

**Tech Stack:** Java 25 on JBR 25 (`./gradlew` only), Swing + FlatLaf 3.7, JUnit 5 + AssertJ, Python 3 (scratch
scripts, `tools/visual`).

**Spec:** [`docs/superpowers/specs/2026-10-12-jasper-compact-density-design.md`](../specs/2026-10-12-jasper-compact-density-design.md)
(approved 2026-10-12). It rests on the Slate Steel spec
[`2026-10-12-jasper-slate-steel-themes-design.md`](../specs/2026-10-12-jasper-slate-steel-themes-design.md).

## Global Constraints

- Work in `/Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay` on branch `claude/compact-density`. Never
  build in `/Users/RQ7RQVF/projects/moray`, where the user runs `:jasper-app:run`. The shell's working directory resets,
  so `cd` to the worktree at the start of every command.
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`, AOT training). The preview tasks, the gallery
  (`java.awt.headless=true`) and render tests are headless and allowed.
- Spec decision 1: the Desktop mocks fix composition only. Sizes, radii, spacing and row heights come from the compact
  scale everywhere. Cards and grouped sections stay.
- Spec decision 2: selected and highlighted rows show white text and icons (`text.onAccent`). Slate Steel Light
  `selection.bg` becomes `#3F6FA8`. Menus and the command palette keep the `accent` highlight, as authored.
- Spec decision 4: type sizes are unchanged and there is no density setting.
- Jasper is an MVP. Settings, APIs and roles are removed outright: no aliases, no deprecation shims and no
  zero-valued leftovers. The exception is existing SDK constructors, which stay because plugins compiled against
  0.15 call them (`KitCompatibility`).
- `dev.jasper.ui.theme` stays JDK-only. FlatLaf-facing code lives in `dev.jasper.ui.swing`. `dev.jasper.app.config`
  stays free of jasper-ui types. Package-info dependency lists are enforced by `verifyApplicationArchitecture`; for
  example, `dev.jasper.app.application` may not import `dev.jasper.app.palette`.
- `WorkspaceMetricsTest.nativeAdaptersDoNotBorrowGenericKitRoles` forbids generic kit menu roles (`MENU_PADDING`,
  `MENU_ITEM_PADDING`, …) inside the native adapters (`NativeControlStyles`, `NativeChromePainting`, `WindowTabs`,
  `FindBar`, `ToolStripe`, `WindowChrome`, `WindowToolbar`). `FlatLafInstaller` is not an adapter and may read them.
- No colour literal outside `slate-steel.json` (`ColourLiteralScanTest`). No animation (the modal overlay's fade is the
  one exception).
- `docs/design/theme-engine/migration.csv`: every new Java file under `jasper-app/src`, `jasper-sdk/src`,
  `jasper-sdk-testkit/src` or `plugins/*/src` that matches `ThemeMigrationCoverageTest.CANDIDATE` needs a row. This
  plan adds no such file; new tests go into existing classes or under `jasper-ui`.
- `AppDocumentationTest` checks every link and anchor in `docs/` (plans and specs included).
- Source hygiene (AGENTS.md): the macOS glyphs ⌃⌥⇧⌘ are ordinary characters and allowed. No private-use, control or
  unpaired surrogate characters.
- Commit on `claude/compact-density` only. Do not merge or push. Every commit message ends with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Decisions recorded by this plan (for the user's review)

These go beyond the spec's text, or read it where it is silent or wrong about the code. Each is cheap to reverse.

1. **Main's workspace chrome keeps its values.** These are the tabs, rails, status bar, find bar, toolbars, title
   search, document bars, breadcrumbs, native tool headers, the dock and the overlay scrollbar (54 roles). They are
   `main`'s sizes, the density the user named as the target, so the spec's "every other padding ×0.8" is read as every
   *kit* padding. Only the three chrome arcs the spec lists change: `TITLE_SEARCH_ARC`, `RAIL_BUTTON_ARC` and
   `DOCK_DROP_ARC`.
2. **Window, dialog and hero title bands keep their heights and centres** (`WINDOW_TITLE_HEIGHT`,
   `DIALOG_TITLE_HEIGHT`, `DIALOG_TITLE_CENTER`, `HERO_TITLE_HEIGHT`, `HERO_CONTROLS_CENTER`,
   `SETTINGS_HEADER_HEIGHT`). They line up with the macOS traffic lights, and the spec's non-goals exclude native window
   chrome.
3. **FlatLaf 3.7 has no `MenuItem.minimumHeight` key.** Native rows reach 22 px through
   `{MenuItem,Menu,CheckBoxMenuItem,RadioButtonMenuItem}.margin` = 3,8,3,8 (a 16 px line plus 6), from the new role
   `MENU_ITEM_VERTICAL_PADDING` 3 and `MENU_ITEM_PADDING` 8.
   - `PopupMenu.borderInsets` is 4,1,4,1, not the spec's 4,0,4,0. The 1 is the border line itself, so the highlight
     meets the edge without covering it.
   - Kit popups use the same 1 px side inset.
4. **Shortcut text uses the macOS glyph order ⌃⌥⇧⌘, giving "⇧⌘H".** This is what the command palette already
   writes. The spec's "⌘⇧H" was illustrative. The palette's formatter moves to `KeyBindings.display`, because the
   application's composition root may not import the palette package.
5. **Dropdown popups highlight with `selection.bg`.** This covers DROPDOWN, SEARCHABLE and rich options, which spec §2
   lists among the rows. Menus and the palette keep `accent`. In Light the two are the same colour.
6. **The credential picker's menu uses the default face's type size (BODY).** The menu cannot know which face opened
   it. Every credential face is DEFAULT except the Import panel's compact one, whose menu is now a size larger than its
   face. Threading the density through `PickerMenu`, the SDK and the Vault API was judged not worth it for one face.
   (Spec: "the field's own type size".)
7. **System › Configuration file's Location row shows the path as the row's description**, wrapped and whole. A
   `SettingRow` description has no mono role and no tooltip, and adding them would change a kit record and an SDK
   record. (Spec: mono, middle-truncated, tooltip.)
8. **`Editors.number` itself becomes the stepper**, so every decimal setting gets − value +. That covers terminal font
   size, line height, dim inactive panes and plugin decimal settings. The interface font size already is a stepper.
   Line height steps by 0.1.
9. **`ui-color-keys.tsv` changes**, against the spec's §6 expectation. The native menu keys move to the kit menu's
   roles: `surface.menu`, an `accent` selection, a `border.menu` edge and `text.tertiary` accelerators. This covers the
   menus that never pass through `NativeControlStyles.menuTree`: the split button, the toolbar dropdown and the
   editor's vendor popup.
10. **Roles that would be 0 are deleted:**
    - `MENU_ITEM_RADIUS`, `PANEL_ROW_RADIUS`, `RESULTS_SELECTION_ARC` and `RESULTS_SELECTION_INSET` (Tasks 2–3).
    - `PICKER_WIDTH`, `SETTINGS_OPTION_HEIGHT` and `SETTINGS_POPUP_PADDING` (Task 5).
    - `SETTINGS_SEGMENT_PADDING` (Task 6).
    - `PICKER_VERTICAL_PADDING` becomes `ROW_VERTICAL_PADDING`.
11. **What a selected row shows:**
    - Every kit text is white; supporting roles take `text.onAccent.secondary`. Severity-coloured text is white too.
    - Status dots, chips and authored identity artwork keep their colours.
    - A focused but unselected row (multi-select lists) shows the hover fill in place of the old outline.
    - A highlighted Settings search match is a plain rectangle with white title and description.
12. **No bundled plugin menu item names a shortcut.** None of their row commands is a bound action. The SDK carries
    the field for plugins that have one. In the app, the tab menu's Close Tab shows `close_tab`'s binding.
13. **SDK 0.15.5 → 0.16.0**, a minor bump for 0.x: `MenuItem` gains `shortcut`. The 6- and 7-argument constructors
    stay.
14. **Labelled window-footer actions and Settings actions are at least 72 wide**, `main`'s `Button.minimumWidth`
    (`WINDOW_ACTION_MIN_WIDTH`, `SETTINGS_ACTION_MIN_WIDTH`). Other kit buttons size to their label with `main`'s
    14 px side margin.
15. **Lists inside panels and Settings navigation run edge to edge.** The panel's side inset (or the navigation's)
    moves into the list's rows (`RowsControl.gutter`), so the fill reaches the list's edges while text stays where it
    was. A list nested in a panel's body stack keeps the stack's inset.

## Review Focus

1. **Enlarged interface fonts (16–20 pt).** Every compact height is a minimum, so menu rows, navigation rows, buttons
   and fields must grow with their line, not clip. Task 1 pins `contentHeight` at an 18-pt body for rows and controls.
2. **A dropdown whose option is longer than its field.** The popup widens to the widest row, is never narrower than
   the field, and never clips a plain row. Task 5 pins `KitComboPopup.bounds` with a long option.
3. **Keyboard focus after the outline is gone.** A focused, unselected row in a multi-select list must still show
   where focus is. Task 2 pins the hover fill under a stub focus manager.
4. **Rebinding a shortcut while a menu's view is alive.** The next open shows the new binding. An unbound or unknown
   action shows nothing: no "Unassigned" and no stale text. Task 4 pins `Item.addNotify` re-resolution and the app's
   `shortcutLabel` for a rebound and an unknown id.
5. **A selected Settings navigation section heading in both variants.** White text at ≥ 4.5:1 on `selection.bg`, and
   the chevron white. Task 2 pins both variants.

## The compact role table

Values are logical pixels. The data block is the single source: Task 1's script applies it and freezes the result in
`jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv`. Lines are `ROLE OLD NEW`; `new` in the OLD
column adds a role and `removed` in the NEW column deletes it. Spec §1 names the controls, selection, menus, Settings
navigation, rows, segments, containers, small parts and palette values. The rest follow its rule: padding, gap and
inset roles × 0.8, rounded, never below 2 where they are above 2. Heights not named by the spec take the control height
(24) for field-like rows, or × 0.8 for multi-line rows.

```text
# New roles and the one removed role
ROW_VERTICAL_PADDING new 4
MENU_ITEM_VERTICAL_PADDING new 3
BUTTON_LABEL_MARGIN new 2
PICKER_VERTICAL_PADDING 4 removed
# Controls and fields
PICKER_DEFAULT_HEIGHT 28 24
FIELD_HEIGHT 28 24
BUTTON_HEIGHT 28 24
HERO_FIELD_HEIGHT 32 28
HERO_BUTTON_HEIGHT 30 28
WINDOW_ACTION_MIN_WIDTH 68 72
CONTROL_RADIUS 6 4
FIELD_VERTICAL_PADDING 12 8
BUTTON_VERTICAL_PADDING 10 8
BUTTON_GAP 8 6
TRANSPARENT_FIELD_PADDING 6 5
FIELD_HORIZONTAL_PADDING 10 8
PRIMARY_BUTTON_HORIZONTAL_PADDING 16 14
TEXTAREA_PADDING 9 7
PORT_PREFIX_GAP 3 2
CHIP_PADDING 5 4
CHIP_RADIUS 4 3
STRENGTH_GAP 3 2
FULL_FOOTER_BUTTON_HEIGHT 30 24
STEPPER_BUTTON_WIDTH 27 22
SETTINGS_ACTION_MIN_WIDTH 64 72
STEPPER_VALUE_PADDING 8 6
STRENGTH_TOP_GAP 6 5
# Menus, pickers and dropdowns
PICKER_PADDING 5 4
PICKER_SEARCH_HEIGHT 21 22
PICKER_SEARCH_SIDE 6 5
PICKER_SEARCH_PADDING 7 6
PICKER_SEARCH_GAP 7 6
PICKER_SEARCH_TOP 6 5
PICKER_UTILITY_HEIGHT 26 22
PICKER_ROW_HEIGHT 28 22
PICKER_GAP 6 5
PICKER_ROW_GAP 9 7
PICKER_FOOTER_TOP 4 3
PICKER_EMPTY_TEXT_INSET 5 4
PICKER_EMPTY_HEIGHT 43 34
PICKER_COMPACT_FACE_INSET 5 4
PICKER_DEFAULT_FACE_INSET 6 5
PICKER_DEFAULT_FACE_GAP 9 7
MENU_TILE_GAP 10 8
SETTINGS_CHOICE_ARROW 26 21
SETTINGS_POPUP_PADDING 5 4
SETTINGS_OPTION_HEIGHT 29 24
MENU_PADDING 6 4
MENU_MIN_WIDTH 222 160
MENU_ITEM_HEIGHT 29 22
MENU_ITEM_PADDING 9 8
MENU_SEPARATOR_MARGIN 4 3
MENU_ARROW_WIDTH 26 21
MENU_TILE 18 16
# Selection
PICKER_SEARCH_RADIUS 5 4
PANEL_ROW_RADIUS 4 0
PANEL_DETAIL_ACTION_ARC 9 8
PANEL_EMPTY_TILE_RADIUS 5 4
CARD_RADIUS 9 6
WINDOW_RADIUS 12 8
SEGMENT_TRACK_RADIUS 7 4
SEGMENT_ITEM_RADIUS 5 3
ORDERED_RADIUS 7 4
KEYCAP_RADIUS 4 3
HERO_TILE_RADIUS 13 8
MENU_ITEM_RADIUS 5 0
DOCK_DROP_ARC 10 8
TITLE_SEARCH_ARC 12 8
RAIL_BUTTON_ARC 10 8
RESULTS_SELECTION_INSET 4 0
RESULTS_SELECTION_ARC 8 0
# Panels
PANEL_ROW_SIDE 6 5
PANEL_ROW_PADDING 8 6
PANEL_ROW_TEXT_GAP 8 6
PANEL_ROW_TITLE_GAP 8 6
PANEL_ROW_ACTION_INSET 3 2
PANEL_DETAIL_PADDING 12 10
PANEL_DETAIL_IDENTITY_GAP 10 8
PANEL_DETAIL_GAP 7 6
PANEL_DETAIL_ACTION_PADDING 10 8
PANEL_DETAIL_ACTION_VERTICAL 6 5
PANEL_EMPTY_TITLE_GAP 10 8
PANEL_EMPTY_TEXT_GAP 6 5
PANEL_EMPTY_ACTION_GAP 15 12
PANEL_EMPTY_ACTION_HEIGHT 27 24
PANEL_COMPACT_ROW_HEIGHT 40 28
PANEL_COMPACT_ROW_VERTICAL 3 2
PANEL_COMPACT_ROW_PADDING 5 4
PANEL_COMPACT_ROW_TRAILING 6 5
PANEL_COMPACT_LIST_TOP 4 3
PANEL_CHIP_PADDING 4 3
PANEL_FOOTER_SIDE 16 13
PANEL_FOOTER_TOP 4 3
PANEL_FOOTER_GAP 6 5
PANEL_FOOTER_ROW_HEIGHT 39 32
PANEL_HEADER 38 35
PANEL_HEADER_BOTTOM_GAP 6 5
PANEL_HEADER_ACTION_GAP 3 2
PANEL_HEADER_TRAILING_PADDING 7 6
PANEL_SEARCH_SIDE 9 7
PANEL_SEARCH_BOTTOM 8 6
PANEL_SEARCH_HEIGHT 26 24
PANEL_SEARCH_VERTICAL_PADDING 10 8
PANEL_HEADER_ACTION_VERTICAL 9 7
PANEL_SEARCH_PADDING 6 5
PANEL_SEARCH_GAP 4 3
PANEL_FOOTER 34 30
# Lists, rails, trees and identity rows
COLUMN_GAP 12 10
ROUTE_HEIGHT 50 42
ROUTE_TOP 4 3
ROUTE_PADDING 10 8
ROUTE_VERTICAL_PADDING 6 5
ROUTE_STEP_GAP 7 6
ROUTE_ARROW_GAP 7 6
ORDERED_ROW_SIDE 12 10
ORDERED_TEXT_GAP 10 8
ORDERED_ACTION_GAP 7 6
COLUMN_SEARCH_HEIGHT 30 24
COLUMN_SEARCH_SIDE 12 10
COLUMN_SEARCH_GAP 10 8
COLUMN_TEXT_GAP 10 8
DETAIL_SECTION_GAP 18 14
DETAIL_ACTION_INSET 9 7
DETAIL_ACTION_GAP 10 8
DETAIL_ROW_SIDE 15 12
COLUMN_LIST_SIDE 8 6
COLUMN_RAIL_TOP 10 8
COLUMN_SEARCH_TOP 10 8
COLUMN_SEARCH_BOTTOM 8 6
COLUMN_RAIL_ROW 31 24
COLUMN_ITEM_ROW 45 36
DETAIL_HEADING_GAP 14 11
DETAIL_METADATA_GAP 15 12
DETAIL_ROW_HEIGHT 67 54
DETAIL_PADDING 24 19
ORDERED_ROW_HEIGHT 35 28
ORDERED_ADD_HEIGHT 32 28
KEYCAP_TREE_GROUP_HEIGHT 34 26
KEYCAP_TREE_INSET 6 5
KEYCAP_DETAIL_TOP 3 2
KEYCAP_DETAIL_BOTTOM 10 8
KEYCAP_RECORDER_HEIGHT 32 26
PLUGIN_DETAILS_TRAILING_PADDING 13 10
KEYCAP_GROUP_SIDE 7 6
KEYCAP_GROUP_TEXT_GAP 8 6
KEYCAP_PADDING 6 5
KEYCAP_GAP 3 2
KEYCAP_VERTICAL_PADDING 5 4
KEYCAP_RECORDER_PADDING 8 6
KEYCAP_DETAIL_GAP 8 6
IDENTITY_HEADING_BOTTOM 6 5
PLUGIN_SEARCH_TRAILING 10 8
PLUGIN_SEARCH_GAP 6 5
IDENTITY_ROW_TRAILING 7 6
KEYCAP_TREE_ROW_HEIGHT 36 26
KEYCAP_DETAIL_INSET 4 3
KEYCAP_CONTENT_TRAILING 15 12
IDENTITY_HEADING_HEIGHT 34 28
IDENTITY_ROW_HEIGHT 47 38
IDENTITY_ROW_SIDE 8 6
IDENTITY_ROW_TEXT_GAP 10 8
IDENTITY_HEADER_GAP 14 11
IDENTITY_HEADER_AFTER 18 14
PLUGIN_MANAGER_PADDING 12 10
PLUGIN_MANAGER_SIDE_PADDING 6 5
PLUGIN_MANAGER_GAP 8 6
PLUGIN_DETAILS_GAP 12 10
PLUGIN_DETAILS_EDGE_PADDING 9 7
PLUGIN_DETAILS_LEADING_PADDING 19 15
PLUGIN_TEXT_VERTICAL_PADDING 7 6
# Forms, heroes, sheets and windows
INLINE_GAP 9 7
CARD_PADDING 14 11
ROW_GAP 10 8
MIXED_ROW_GAP 12 10
SECTION_GAP 20 16
SHEET_SECTION_GAP 14 11
FORM_TOP 16 13
FORM_SIDES 24 19
HERO_SIDES 28 22
FORM_SEGMENT_PADDING 3 2
FORM_SEGMENT_HEIGHT 24 22
FOOTER_STATUS_GAP 14 11
TITLE_PAIR_GAP 4 3
SEGMENT_HORIZONTAL_PADDING 4 10
SECTION_LABEL_GAP 8 6
WINDOW_FOOTER_VERTICAL 8 6
WINDOW_FOOTER_SIDE 12 10
FOOTER_PADDING 20 16
HERO_TOP 19 15
HERO_INTRO_GAP 18 14
HERO_COMPACT_INTRO_GAP 18 14
HERO_FOOTNOTE_GAP 14 11
HERO_FOOTER_TOP 20 16
HERO_SECTION_GAP 24 19
HERO_FOOTER_BOTTOM 24 19
HERO_COMPACT_FOOTER_TOP 32 26
HERO_SETTING_HEIGHT 54 44
DESCRIPTION_GAP 4 3
# Settings
FULL_SETTING_CHOICE_HEIGHT 26 24
THEME_PREVIEW_GAP 10 8
THEME_PREVIEW_INSET 10 8
SETTINGS_FOOTER_HEIGHT 53 42
SETTINGS_PAGE_INSET 25 20
SETTINGS_PAGE_TOP 20 16
SETTINGS_ROW_HEIGHT 45 36
SETTINGS_DESCRIPTION_HEIGHT 65 52
SETTINGS_SECTION_GAP 20 16
SETTINGS_GROUP_HEIGHT 39 32
SETTINGS_NAV_INSET 8 6
SETTINGS_NAV_ROW 29 24
SETTINGS_NAV_HEADING 24 22
SETTINGS_NAV_ROW_SIDE 8 6
SETTINGS_NAV_TEXT_GAP 10 8
SETTINGS_NAV_GROUP_GAP 11 9
SETTING_HEIGHT 42 34
SETTING_DESCRIPTION_HEIGHT 48 40
FULL_SETTING_DESCRIPTION_HEIGHT 64 52
SETTING_VERTICAL_PADDING 8 6
SETTINGS_SIDEBAR_INSET 10 8
SETTINGS_SEARCH_GAP 5 4
SETTINGS_NAV_HEADING_GAP 6 5
SETTINGS_FOOTER_SIDE 16 13
SETTINGS_HEADER_GAP 10 8
SETTINGS_HEADER_TITLE_GAP 16 13
SETTINGS_CRUMB_GAP 4 3
SETTINGS_ROW_SIDE 15 12
THEME_PREVIEW_CAPTION_GAP 8 6
SETTINGS_SEGMENT_INSET 3 2
SETTINGS_GROUP_ROW_HEIGHT 43 34
SETTINGS_GROUP_ROW_PADDING 7 6
KEYMAP_SEARCH_HEIGHT 30 24
SETTINGS_ANNOTATION_GAP 7 6
SETTING_DESCRIPTION_GAP 3 2
SETTING_SIDE_PADDING 13 10
FULL_SETTING_SIDE_PADDING 15 12
SETTING_DEPENDENT_INDENT 16 13
KEYMAP_PAGE_TOP_PADDING 16 13
KEYMAP_PAGE_PADDING 25 20
KEYMAP_PAGE_BOTTOM_PADDING 8 6
KEYMAP_SECTION_GAP 12 10
KEYMAP_TOOLS_GAP 8 6
KEYMAP_STATUS_GAP 8 6
# Other
FULL_FOOTER_TOP 22 18
# Command palette
PALETTE_HINT_ACTION_GAP 12 10
RESULTS_VERTICAL_PADDING 4 3
RESULTS_ROW_HEIGHT 24 22
RESULTS_ROW_PADDING 8 6
RESULTS_ROW_GAP 8 6
PALETTE_FIELD_HEIGHT 44 36
PALETTE_FIELD_VERTICAL_PADDING 6 3
PALETTE_FIELD_SIDE_PADDING 8 6
PALETTE_CHIP_GAP 6 5
PALETTE_HINT_SIDE_PADDING 12 10
PALETTE_STEP_ROW_HEIGHT 40 32
PALETTE_STEP_SIDE_PADDING 12 10
PALETTE_STEP_ROW_PADDING 4 3
PALETTE_STEP_LABEL_GAP 10 8
# Notices, choosers, notifications and history
CHOOSER_TOOLBAR_VERTICAL_PADDING 8 6
CHOOSER_TOOLBAR_SIDE_PADDING 10 8
CHOOSER_SIDE_PADDING 10 8
CHOOSER_PREVIEW_VERTICAL_PADDING 8 6
CHOOSER_PREVIEW_GAP 6 5
CHOOSER_BOTTOM_VERTICAL_PADDING 10 8
CHOOSER_BOTTOM_SIDE_PADDING 12 10
NOTIFICATION_GAP 8 6
NOTIFICATION_COUNT_GAP 6 5
NOTIFICATION_TITLE_CLOSE_GAP 4 3
NOTIFICATION_CONTENT_GAP 4 3
NOTIFICATION_LINK_GAP 12 10
NOTIFICATION_STACK_MARGIN 12 10
NOTIFICATION_STACK_GAP 8 6
HISTORY_HEADER_VERTICAL_PADDING 6 5
HISTORY_HEADER_LEADING_PADDING 10 8
HISTORY_HEADER_TRAILING_PADDING 6 5
HISTORY_SIDE_PADDING 10 8
HISTORY_HEADING_TOP_PADDING 8 6
HISTORY_HEADING_BOTTOM_PADDING 4 3
HISTORY_ROW_VERTICAL_PADDING 8 6
HISTORY_ROW_TRAILING_PADDING 6 5
HISTORY_ROW_GAP 8 6
HISTORY_TITLE_GAP 6 5
```

Kept, with the reason (every other role):

- **Heights already compact or set by content** (17): `PICKER_COMPACT_HEIGHT` 22, `PANEL_DETAIL_ACTION` 22, `PANEL_ACTION` 24, `INLINE_ACTION` 22, `PANEL_CHIP_HEIGHT` 17, `ORDERED_ACTION` 24, `PANEL_HEADER_ACTION` 24, `CHIP_HEIGHT` 16, `STRENGTH_HEIGHT` 3, `PROGRESS_HEIGHT` 3, `THEME_PREVIEW_HEIGHT` 72, `JOINED_ACTION_HEIGHT` 24, `CIRCULAR_ACTION_SIZE` 24, `RESULTS_HEADING_HEIGHT` 22, `PALETTE_HINT_HEIGHT` 26, `PALETTE_STEP_TITLE_HEIGHT` 22, `PLUGIN_LIST_MIN_HEIGHT` 100.
- **Widths (composition and window sizes; `PICKER_WIDTH` goes in Task 5, `MENU_MIN_WIDTH` and the 72 px button minimums change above)** (51): `PICKER_WIDTH` 222, `PICKER_FACE_WIDTH` 190, `FORM_LABEL_WIDTH` 96, `PANEL_EMPTY_TEXT_WIDTH` 206, `ORDERED_INDEX_WIDTH` 16, `PORT_FIELD_WIDTH` 83, `HERO_WIDTH` 400, `HERO_COMPACT_WIDTH` 380, `SHEET_WIDTH` 480, `FORM_WIDTH` 540, `MANAGER_SIDEBAR_WIDTH` 192, `MANAGER_LIST_WIDTH` 272, `STEPPER_VALUE_WIDTH` 52, `SLIDER_WIDTH` 200, `THEME_PREVIEW_WIDTH` 168, `SETTINGS_SIDEBAR_WIDTH` 236, `SETTINGS_CARD_WIDTH` 620, `SETTINGS_CHOICE_WIDTH` 190, `CHOICE_ARROW_WIDTH` 20, `SETTINGS_RICH_CHOICE_WIDTH` 170, `JOINED_ACTION_WIDTH` 25, `SETTING_CHOICE_WIDTH` 160, `SLIDER_VALUE_WIDTH` 50, `KEYCAP_KEYS_COLUMN` 118, `EMPTY_TEXT_WIDTH` 300, `STRENGTH_WIDTH` 280, `TITLE_SEARCH_WIDTH` 400, `TITLE_SEARCH_MIN_WIDTH` 240, `TITLE_SEARCH_MAX_WIDTH` 480, `STATUS_PROGRESS_MAX_WIDTH` 420, `STATUS_PROGRESS_WIDTH` 72, `TAB_CLOSE_FIT_WIDTH` 80, `TAB_CLOSE_WIDTH` 16, `DOCUMENT_BAR_STATUS_WIDTH` 84, `DOCUMENT_BAR_FILL_MIN_WIDTH` 120, `OVERLAY_SCROLLBAR_WIDTH` 10, `TREE_DETAIL_COLUMN` 88, `TREE_VALUE_COLUMN` 96, `CHOOSER_NAVIGATION_WIDTH` 190, `CHOOSER_NAVIGATION_MIN_WIDTH` 140, `CHOOSER_PREVIEW_WIDTH` 230, `CHOOSER_NAME_COLUMN` 360, `CHOOSER_MODIFIED_COLUMN` 150, `CHOOSER_SIZE_COLUMN` 80, `PALETTE_WIDTH` 680, `PALETTE_STEP_LABEL_WIDTH` 140, `IDENTITY_SECTION_WIDTH` 220, `PLUGIN_LIST_WIDTH` 264, `PLUGIN_LIST_MIN_WIDTH` 240, `NOTIFICATION_WIDTH` 360, `DETAIL_IDENTITY_MIN_WIDTH` 160.
- **Artwork: glyphs, tiles, dots, chevrons, switches and icon-to-label gaps** (69): `PICKER_SEARCH_GLYPH` 9, `PICKER_TILE` 16, `PICKER_FACE_TILE` 14, `PICKER_GLYPH` 8, `PICKER_DEFAULT_FACE_TILE` 18, `PICKER_COMPACT_FACE_GLYPH` 10, `PICKER_DEFAULT_FACE_GLYPH` 13, `PICKER_COMPACT_FACE_CHEVRON` 8, `PICKER_DEFAULT_FACE_CHEVRON` 10, `PANEL_ROW_DOT` 7, `PANEL_ROW_ACTION_GLYPH` 13, `PANEL_DETAIL_TILE` 28, `PANEL_DETAIL_TILE_HEIGHT` 30, `PANEL_DETAIL_GLYPH` 14, `PANEL_DETAIL_ACTION_GLYPH` 17, `PANEL_EMPTY_TILE` 37, `PANEL_EMPTY_GLYPH` 16, `PANEL_COMPACT_ROW_GLYPH` 16, `ROUTE_TILE` 22, `ROUTE_GLYPH` 14, `ROUTE_ARROW` 14, `CHOICE_CHEVRON` 10, `CHOICE_CHEVRON_INSET` 8, `ORDERED_GLYPH` 10, `COLUMN_SEARCH_GLYPH` 12, `MENU_ICON_ARROW_WIDTH` 21, `PANEL_HEADER_CONTRIBUTED_GLYPH` 18, `SWITCH_WIDTH` 32, `SWITCH_HEIGHT` 18, `SWITCH_KNOB` 14, `COMPACT_SWITCH_WIDTH` 28, `COMPACT_SWITCH_HEIGHT` 16, `COMPACT_SWITCH_KNOB` 12, `ICON_CAPTION` 12, `ICON_CONTROL` 14, `ICON_RAIL` 16, `ICON_EMPTY` 20, `ICON_HERO` 24, `ICON_GAP` 6, `STATUS_DOT` 6, `CATEGORY_RAIL_TILE` 20, `CATEGORY_ROW_TILE` 28, `CATEGORY_SHEET_TILE` 32, `CATEGORY_DETAIL_TILE` 44, `SLIDER_TRACK` 4, `SLIDER_THUMB` 15, `SETTINGS_ICON_TILE` 20, `SETTINGS_NAV_GLYPH` 11, `SETTINGS_ICON_TEXT_GAP` 9, `SETTINGS_CHOICE_CHEVRON` 10, `SETTINGS_CHOICE_CHEVRON_INSET` 10, `SETTINGS_SEARCH_ICON_GAP` 8, `KEYCAP_RECORDER_DOT` 8, `SETTINGS_POPUP_CHECK` 9, `EMPTY_TILE` 40, `HERO_TILE` 54, `HERO_TILE_GAP` 14, `TOOLBAR_ACTION_ICON` 16, `TRANSPARENT_FIELD_ICON_GAP` 4, `BREADCRUMB_ICON_GAP` 5, `STATUS_RUNNING_DOT` 5, `TOOL_HEADER_ACTIVE_DOT` 6, `RAIL_BADGE_DOT` 6, `IDENTITY_ROW_TILE` 30, `IDENTITY_ROW_GLYPH` 17, `IDENTITY_HEADER_TILE` 54, `IDENTITY_HEADER_GLYPH` 23, `PLUGIN_DETAIL_ICON` 52, `NOTIFICATION_ICON` 16.
- **Spacing already at 2 px or less** (13): `PICKER_MENU_BOTTOM` 2, `ROUTE_ARROW_INSET` 2, `PANEL_FOOTER_SEPARATOR_GAP` 2, `DETAIL_TITLE_GAP` 0, `SEGMENT_PADDING` 2, `DETAIL_VALUE_GAP` 2, `SETTINGS_SEGMENT_PADDING` 0, `CHOICE_VALUE_END_PADDING` 2, `SETTINGS_NAV_ROW_SPACING` 1, `KEYCAP_DETAIL_AFTER` 2, `PLUGIN_TEXT_SIDE_PADDING` 0, `NOTIFICATION_LINK_VERTICAL_PADDING` 2, `HISTORY_CONTENT_GAP` 2.
- **Not geometry: percentages, alphas, durations and counts** (38): `PICKER_DISABLED_PERCENT` 45, `DETAIL_MASK_COUNT` 12, `OVERLAY_ENTER_MILLIS` 220, `OVERLAY_EXIT_MILLIS` 200, `BUTTON_INSET_PERCENT` 14, `BUTTON_DROP_PERCENT_DARK` 30, `BUTTON_DROP_PERCENT_LIGHT` 12, `POPOVER_SHADOW_PERCENT_DARK` 50, `POPOVER_SHADOW_PERCENT_LIGHT` 18, `SWITCH_KNOB_SHADOW_PERCENT` 15, `DIALOG_SHADOW_PERCENT_DARK` 58, `DIALOG_SHADOW_PERCENT_LIGHT` 22, `SEGMENT_SHADOW_PERCENT_LIGHT` 12, `FOCUS_HALO_PERCENT_DARK` 32, `FOCUS_HALO_PERCENT_LIGHT` 25, `ERROR_HALO_PERCENT` 22, `TILE_PERCENT_DARK` 17, `TILE_PERCENT_LIGHT` 13, `KEYCAP_EDGE_ALPHA` 87, `KEYCAP_FACE_ALPHA` 12, `KEYCAP_RECORDER_HALO_ALPHA` 51, `TAB_OPEN_MILLIS` 475, `TAB_CLOSE_MILLIS` 425, `TAB_SWITCH_MILLIS` 0, `NATIVE_HOVER_OVERLAY_PERCENT` 8, `DOCK_DRAG_THRESHOLD` 5, `DOCK_DROP_FILL_ALPHA` 64, `DOCK_DROP_BORDER_ALPHA` 153, `NATIVE_TOOLBAR_DISABLED_PERCENT` 38, `TAB_REORDER_THRESHOLD` 5, `OVERLAY_SCROLLBAR_ALPHA` 190, `OVERLAY_SCROLLBAR_IDLE_MILLIS` 1000, `TYPE_AHEAD_MILLIS` 1000, `CHOOSER_LOADING_DELAY_MILLIS` 150, `TEXT_FLOW_COLUMNS` 44, `ERRORS_TRACE_ROWS` 12, `NOTIFICATION_SHADOW_ALPHA_DARK` 4, `NOTIFICATION_SHADOW_ALPHA_LIGHT` 2.
- **Optical offsets and native centres** (12): `PANEL_ROW_OPTICAL_OFFSET` 1, `PANEL_DETAIL_TEXT_BASELINE` 1, `PANEL_DETAIL_ACTION_BOTTOM_TRIM` 1, `PANEL_DETAIL_ACTION_OPTICAL_OFFSET` 2, `PANEL_EMPTY_LIFT` 4, `DIALOG_TITLE_CENTER` 21, `HERO_CONTROLS_CENTER` 18, `PANEL_SEARCH_TEXT_BASELINE` 1, `KEYCAP_SQUARE_EXTRA` 3, `KEYCAP_BASE` 2, `CAPTION_TOP_OFFSET` 6, `TOOLBAR_BUTTON_TRIM` 6.
- **Text line heights** (4): `PANEL_EMPTY_LINE_HEIGHT` 15, `SUPPORTING_LINE_HEIGHT` 17, `PLUGIN_TEXT_LINE_SPACING` 20, `DESCRIPTION_LINE_HEIGHT` 18.
- **Other fixed steps** (3): `DETAIL_MASK_PITCH` 7, `NOTIFICATION_CLOSE` 16, `NOTIFICATION_SCROLL_STEP` 16.
- **Native title bands (Decision 2)** (4): `WINDOW_TITLE_HEIGHT` 45, `DIALOG_TITLE_HEIGHT` 30, `HERO_TITLE_HEIGHT` 20, `SETTINGS_HEADER_HEIGHT` 45.
- **Main's workspace chrome (Decision 1)** (57): `NOTICE_VERTICAL_PADDING` 8, `NOTICE_HORIZONTAL_PADDING` 12, `NOTICE_BUTTON_GAP` 8 (the editor's document-bar notice), `OVERLAY_RISE` 16, `OVERLAY_MARGIN` 16, `PALETTE_WINDOW_MARGIN` 16, `WORKSPACE_GUTTER` 6, `TITLE_SEARCH_HEIGHT` 22, `NATIVE_TOOL_HEADER_HEIGHT` 32, `NATIVE_HEADER_GAP` 12, `NATIVE_ACTION_PADDING` 4, `NATIVE_MENU_SPACE` 10, `FIND_TOP_PADDING` 2, `FIND_BOTTOM_PADDING` 3, `BREADCRUMB_MIN_HEIGHT` 20, `BREADCRUMB_SEPARATOR` 22, `STATUS_PROGRESS_RESERVE` 90, `STATUS_PROGRESS_GAP` 8, `STATUS_ITEM_GAP` 5, `STATUS_GAP` 14, `STATUS_SEPARATOR` 28, `STATUS_LEADING` 26, `TOOLBAR_PADDING` 8, `TOOLBAR_SEPARATOR_INSET` 7, `TOOLBAR_SEPARATOR_INSETS` 14, `TITLE_SEARCH_VERTICAL_SPACE` 8, `RAIL_MIN_HEIGHT` 80, `RAIL_EDGE` 3, `RAIL_GAP` 4, `RAIL_SECTION_GAP` 8, `RAIL_SEPARATOR_INSET` 7, `TAB_REVEAL_INSET` 100, `TAB_CLOSE_GUTTER` 28, `TAB_COMPACT_GUTTER` 4, `NATIVE_SPLIT_DIVIDER` 8, `TAB_CLOSE_TRAILING` 6, `STATUS_PROGRESS_BAR_HEIGHT` 8, `STATUS_PROGRESS_MIN_HEIGHT` 24, `TOOL_HEADER_LEADING_PADDING` 10, `TOOL_HEADER_TRAILING_PADDING` 6, `TOOL_HEADER_DETAIL_GAP` 6, `FIND_GAP` 6, `FIND_SIDE_PADDING` 6, `FIND_CONTROL_GAP` 4, `FIND_CLOSE_SPACER` 16, `DOCUMENT_BAR_VERTICAL_PADDING` 4, `DOCUMENT_BAR_SIDE_PADDING` 6, `DOCUMENT_BAR_GAP` 8, `DOCUMENT_BAR_ACTION_GAP` 3, `DOCUMENT_BAR_LINE_GAP` 4, `TITLE_SEARCH_PADDING` 8, `TITLE_SEARCH_GAP` 6, `NATIVE_MENU_ARROW_GAP` 3, `BREADCRUMB_COLLAPSED_HEIGHT` 16, `BREADCRUMB_CRUMB_PADDING` 4, `BREADCRUMB_SEPARATOR_PADDING` 2, `OVERLAY_SCROLLBAR_INSET` 2.
- **theme.md's shadows** (5): `POPOVER_SHADOW_OFFSET` 12, `POPOVER_SHADOW_BLUR` 32, `DIALOG_SHADOW_OFFSET` 30, `DIALOG_SHADOW_BLUR` 80, `SEGMENT_SHADOW_BLUR` 2.
- **Strokes and rings** (6): `FOCUS_RING` 3, `THEME_PREVIEW_RING` 2, `KEYCAP_RECORDER_HALO` 3, `SPLIT_STROKE` 2, `HAIRLINE` 1, `TAB_DIVIDER` 1.
- **Already the compact value** (8): `TRANSPARENT_FIELD_RADIUS` 3, `TRANSPARENT_FIELD_FOCUS_RADIUS` 4, `BUTTON_HORIZONTAL_PADDING` 14, `STRENGTH_RADIUS` 2, `WORKSPACE_SURFACE_ARC` 16, `NATIVE_TOOLBAR_HOVER_ARC` 6, `OVERLAY_SCROLLBAR_ARC` 6, `PALETTE_CORNER_ARC` 12.
- **Columns aligned under unchanged artwork** (2): `KEYCAP_TREE_LEAF_INDENT` 52, `KEYCAP_DETAIL_LEADING` 58.

## Shared tools (scratch, never committed)

`S=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/d113fca6-9a16-46f1-98db-6d4f7facee53/scratchpad/compact` (any
scratch directory works).

- `$S/compact-roles.txt`: the data block above, copied verbatim (Task 1, Step 1).
- `$S/compact_roles.py` (Task 1, Step 2): rewrites `DesignMetrics.java` from the data and writes the frozen table.
- `docs/design/theme-engine/migration.csv` needs no edit (Global Constraints).

---

### Task 1: The compact scale

**Files:**
- Create: `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv` (generated)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java`. Role values change per the table; three
  roles are added and `PICKER_VERTICAL_PADDING` is removed; `contentPadding(Role)` is added and `contentHeight` uses
  it.
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/ThemePainting.java:18-31` (`height` uses `contentPadding`)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Trees.java:170-175` (`keycapGroupHeight`)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafInstaller.java:41-48` (derived geometry)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/metrics/DesignMetricsTest.java`,
  `jasper-ui/src/test/java/dev/jasper/ui/swing/SlateSteelInstallTest.java`, plus the re-baselined tests in Step 10.

**Interfaces:**
- Produces:
  - `public DesignMetrics.Role DesignMetrics.contentPadding(DesignMetrics.Role height)`.
  - Roles `ROW_VERTICAL_PADDING` (4), `MENU_ITEM_VERTICAL_PADDING` (3) and `BUTTON_LABEL_MARGIN` (2).
  - `compact-roles.tsv`. Every later task that adds or deletes a role edits this table in the same commit.
  - FlatLaf keys: `Button.margin`, `Button.minimumWidth`, `Component.arc`, `*.selectionArc`, `*.selectionInsets`,
    the four `*.margin` menu keys, `PopupMenu.borderInsets`, `Popup.borderCornerRadius` and
    `PopupMenu.borderCornerRadius`.

- [ ] **Step 1: Save the role data.** Copy the fenced `text` block of [The compact role table](#the-compact-role-table)
  verbatim into `$S/compact-roles.txt`.

- [ ] **Step 2: Write the script** `$S/compact_roles.py`:

```python
"""python3 -I compact_roles.py [--dry] <DesignMetrics.java> <compact-roles.txt> <compact-roles.tsv>
Applies ROLE OLD NEW lines (OLD 'new' adds, NEW 'removed' deletes) and writes the frozen NAME<TAB>VALUE table."""
import pathlib, re, sys
args = sys.argv[1:]
dry = args[0] == "--dry"
if dry: args = args[1:]
source, data, tsv = map(pathlib.Path, args)
text = source.read_text(encoding="utf-8")
changes, added, removed = {}, [], []
for line in data.read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("#"): continue
    name, old, new = line.split()
    if old == "new": added.append((name, int(new)))
    elif new == "removed": removed.append((name, int(old)))
    else: changes[name] = (int(old), int(new))
for name, (old, new) in changes.items():
    text, n = re.subn(r"\b%s\(%d\)" % (name, old), "%s(%d)" % (name, new), text)
    if n != 1: raise SystemExit("%s(%d) matched %d times" % (name, old, n))
for name, old in removed:
    text, n = re.subn(r"\b%s\(%d\),\s*" % (name, old), "", text)
    if n != 1: raise SystemExit("could not remove " + name)
anchor = "PICKER_COMPACT_HEIGHT("
if text.count(anchor) != 1: raise SystemExit("anchor missing")
text = text.replace(anchor, "".join("%s(%d), " % role for role in added) + anchor, 1)
body = re.sub(r"//[^\n]*", "", text.split("public enum Role {", 1)[1]).split(";", 1)[0]
roles = re.findall(r"\b([A-Z][A-Z0-9_]*)\((\d+)\)", body)
names = [name for name, _ in roles]
if len(names) != len(set(names)): raise SystemExit("duplicate role")
tsv.parent.mkdir(parents=True, exist_ok=True)
tsv.write_text("".join("%s\t%s\n" % role for role in roles), encoding="utf-8")
if not dry: source.write_text(text, encoding="utf-8")
print(("dry: " if dry else "") + "changed %d, added %d, removed %d, %d roles" % (len(changes), len(added), len(removed), len(roles)))
```

- [ ] **Step 3: Generate the frozen table**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && python3 -I $S/compact_roles.py --dry jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java $S/compact-roles.txt jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv
```

Expected: `dry: changed 280, added 3, removed 1, 569 roles`.

- [ ] **Step 4: Write the failing metric tests.** Replace `DesignMetricsTest`'s
  `fixedLogicalGeometryIsIndependentOfDisplayScale` and `enlargedUserFontGetsEnoughContentBoundsWithoutChangingCatalog`
  with these four tests. Keep `invalidFontSettingsAreRejected`. Add the imports `java.nio.charset.StandardCharsets`,
  `java.util.Arrays`, `java.util.LinkedHashMap` and `static org.junit.jupiter.api.Assumptions.assumeTrue`.

```java
    @Test void fixedLogicalGeometryIsIndependentOfDisplayScale() {
        var m = DesignMetrics.DEFAULT;
        assertThat(m.value(DesignMetrics.Role.FIELD_HEIGHT)).isEqualTo(24);
        assertThat(m.value(DesignMetrics.Role.BUTTON_HEIGHT)).isEqualTo(24);
        assertThat(m.value(DesignMetrics.Role.HERO_FIELD_HEIGHT)).isEqualTo(28);
        assertThat(m.value(DesignMetrics.Role.MENU_ITEM_HEIGHT)).isEqualTo(22);
        assertThat(m.value(DesignMetrics.Role.FORM_LABEL_WIDTH)).isEqualTo(96);
        assertThat(m.value(DesignMetrics.Role.COLUMN_GAP)).isEqualTo(10);
        assertThat(m.value(DesignMetrics.Role.CONTROL_RADIUS)).isEqualTo(4);
        assertThat(m.value(DesignMetrics.Role.CARD_RADIUS)).isEqualTo(6);
        assertThat(m.value(DesignMetrics.Role.WINDOW_RADIUS)).isEqualTo(8);
        assertThat(m.value(DesignMetrics.Role.FOCUS_RING)).isEqualTo(3);
        assertThat(m.value(DesignMetrics.Role.HAIRLINE)).isEqualTo(1);
    }

    /** The whole scale, frozen: a retune edits compact-roles.tsv in the same change. */
    @Test void everyRoleHasItsCompactValue() throws Exception {
        var table = new LinkedHashMap<String, Integer>();
        try (var in = DesignMetricsTest.class.getResourceAsStream("compact-roles.tsv")) {
            for (var line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                var cells = line.split("\t");
                table.put(cells[0], Integer.parseInt(cells[1]));
            }
        }
        assertThat(Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList()).containsExactlyElementsOf(table.keySet());
        for (var role : DesignMetrics.Role.values())
            assertThat(DesignMetrics.DEFAULT.value(role)).as(role.name()).isEqualTo(table.get(role.name()));
    }

    @Test void aThirteenPointLineMakesTheCompactHeights() {
        var frc = new FontRenderContext(null, true, true);
        var body = new Typography("SansSerif", 13).font(Typography.Role.BODY);
        int line = (int) Math.ceil(body.getLineMetrics("Ag", frc).getHeight());
        assumeTrue(line <= 16, "a 13-pt line of at most 16 px");
        var m = DesignMetrics.DEFAULT;
        assertThat(m.contentHeight(DesignMetrics.Role.BUTTON_HEIGHT, body, frc)).isEqualTo(24);
        assertThat(m.contentHeight(DesignMetrics.Role.FIELD_HEIGHT, body, frc)).isEqualTo(24);
        assertThat(m.contentHeight(DesignMetrics.Role.MENU_ITEM_HEIGHT, body, frc)).isEqualTo(22);
        assertThat(m.contentHeight(DesignMetrics.Role.PICKER_ROW_HEIGHT, body, frc)).isEqualTo(22);
        assertThat(m.contentHeight(DesignMetrics.Role.SETTINGS_NAV_ROW, body, frc)).isEqualTo(24);
        assertThat(m.contentHeight(DesignMetrics.Role.SETTINGS_NAV_HEADING, body, frc)).isEqualTo(22);
    }

    @Test void enlargedUserFontGetsEnoughContentBoundsWithoutChangingCatalog() {
        var frc = new FontRenderContext(null, true, true);
        var normal = new Typography("SansSerif", 13);
        var large = new Typography("SansSerif", 18);
        var font = large.font(Typography.Role.BODY);
        int line = (int) Math.ceil(font.getLineMetrics("Ag", frc).getHeight());
        var m = DesignMetrics.DEFAULT;
        assertThat(m.contentPadding(DesignMetrics.Role.MENU_ITEM_HEIGHT)).isEqualTo(DesignMetrics.Role.ROW_VERTICAL_PADDING);
        assertThat(m.contentPadding(DesignMetrics.Role.BUTTON_HEIGHT)).isEqualTo(DesignMetrics.Role.BUTTON_VERTICAL_PADDING);
        assertThat(m.contentPadding(DesignMetrics.Role.FIELD_HEIGHT)).isEqualTo(DesignMetrics.Role.FIELD_VERTICAL_PADDING);
        assertThat(m.contentHeight(DesignMetrics.Role.FIELD_HEIGHT, font, frc)).isGreaterThan(24)
            .isEqualTo(Math.max(24, line + m.value(DesignMetrics.Role.FIELD_VERTICAL_PADDING)));
        assertThat(m.contentHeight(DesignMetrics.Role.MENU_ITEM_HEIGHT, font, frc)).isGreaterThan(22)
            .isEqualTo(Math.max(22, line + m.value(DesignMetrics.Role.ROW_VERTICAL_PADDING)));
        assertThat(m.contentHeight(DesignMetrics.Role.BUTTON_HEIGHT, font, frc)).isGreaterThan(24);
        assertThatThrownBy(() -> m.contentPadding(DesignMetrics.Role.CARD_RADIUS)).isInstanceOf(IllegalArgumentException.class);
        assertThat(normal.font(Typography.Role.LABEL).getSize2D()).isEqualTo(12.5f);
        assertThat(normal.font(Typography.Role.HERO_TITLE).getSize2D()).isEqualTo(15);
        assertThat(large.font(Typography.Role.MONO).getFamily()).isEqualTo("Monospaced");
    }
```

- [ ] **Step 5: Write the failing FlatLaf geometry test.** Add it to `SlateSteelInstallTest`, with the imports
  `java.awt.Insets` and `java.util.List`:

```java
    @Test void flatLafGeometryComesFromTheCompactScale() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                FlatLafInstaller.install(SlateSteel.bundled().generated(true));
                for (var key : List.of("Button.arc", "Component.arc", "TextComponent.arc")) assertThat(UIManager.getInt(key)).as(key).isEqualTo(8);
                assertThat(UIManager.getInsets("Button.margin")).isEqualTo(new Insets(2, 14, 2, 14));
                assertThat(UIManager.getInt("Button.minimumWidth")).isEqualTo(72);
                assertThat(UIManager.getInt("Button.minimumHeight")).isEqualTo(24);
                assertThat(UIManager.getInt("Component.minimumHeight")).isEqualTo(24);
                for (var key : List.of("List", "Tree", "Table", "ComboBox", "MenuItem")) {
                    assertThat(UIManager.getInt(key + ".selectionArc")).as(key).isZero();
                    assertThat(UIManager.getInsets(key + ".selectionInsets")).as(key).isEqualTo(new Insets(0, 0, 0, 0));
                }
                for (var key : List.of("MenuItem", "Menu", "CheckBoxMenuItem", "RadioButtonMenuItem"))
                    assertThat(UIManager.getInsets(key + ".margin")).as(key).isEqualTo(new Insets(3, 8, 3, 8));
                assertThat(UIManager.getInsets("PopupMenu.borderInsets")).isEqualTo(new Insets(4, 1, 4, 1));
                assertThat(UIManager.getInt("Popup.borderCornerRadius")).isEqualTo(6);
                assertThat(UIManager.getInt("PopupMenu.borderCornerRadius")).isEqualTo(6);
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }
```

- [ ] **Step 6: Run them and watch them fail**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-ui:test --tests '*DesignMetricsTest' --tests '*SlateSteelInstallTest' > $S/t1-red.log 2>&1; tail -30 $S/t1-red.log
```

Expected: compilation fails on `contentPadding` and `ROW_VERTICAL_PADDING`. That counts as red: the API does not exist
yet.

- [ ] **Step 7: Apply the scale.** Run the script without `--dry`:

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && python3 -I $S/compact_roles.py jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java $S/compact-roles.txt jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv
```

Expected: `changed 280, added 3, removed 1, 569 roles`. Then make these edits:

  - In `DesignMetrics`, add a comment above the inserted roles: `// One-line rows (menus, pickers, navigation, keycap
    and identity rows) pad their line by ROW_VERTICAL_PADDING; a native menu row's margin is
    MENU_ITEM_VERTICAL_PADDING; main's Button.margin top and bottom are BUTTON_LABEL_MARGIN.` Then replace
    `contentHeight` with:

```java
    /** The vertical padding a content height adds to its font's line. */
    public Role contentPadding(Role height) {
        return switch (Objects.requireNonNull(height)) {
            case PICKER_COMPACT_HEIGHT, PICKER_DEFAULT_HEIGHT, PICKER_SEARCH_HEIGHT, PICKER_ROW_HEIGHT, PICKER_UTILITY_HEIGHT,
                 FORM_SEGMENT_HEIGHT, FULL_SETTING_CHOICE_HEIGHT, MENU_ITEM_HEIGHT, SETTINGS_NAV_ROW, SETTINGS_NAV_HEADING,
                 KEYCAP_TREE_ROW_HEIGHT, KEYCAP_TREE_GROUP_HEIGHT, IDENTITY_ROW_HEIGHT, IDENTITY_HEADING_HEIGHT -> Role.ROW_VERTICAL_PADDING;
            case RESULTS_ROW_HEIGHT, RESULTS_HEADING_HEIGHT -> Role.RESULTS_VERTICAL_PADDING;
            case PANEL_SEARCH_HEIGHT -> Role.PANEL_SEARCH_VERTICAL_PADDING;
            case PANEL_HEADER_ACTION -> Role.PANEL_HEADER_ACTION_VERTICAL;
            case PANEL_DETAIL_ACTION -> Role.PANEL_DETAIL_ACTION_VERTICAL;
            case FIELD_HEIGHT, HERO_FIELD_HEIGHT, COLUMN_SEARCH_HEIGHT -> Role.FIELD_VERTICAL_PADDING;
            case PANEL_EMPTY_ACTION_HEIGHT, BUTTON_HEIGHT, HERO_BUTTON_HEIGHT, FULL_FOOTER_BUTTON_HEIGHT, ORDERED_ADD_HEIGHT,
                 PANEL_ACTION, INLINE_ACTION -> Role.BUTTON_VERTICAL_PADDING;
            default -> throw new IllegalArgumentException("Not a content height role: " + height);
        };
    }
    /** Minimum outer height accommodating actual font line metrics, without scaling logical catalog values. */
    public int contentHeight(Role height, Font font, FontRenderContext context) {
        Objects.requireNonNull(font); Objects.requireNonNull(context);
        return Math.max(value(height), (int) Math.ceil(font.getLineMetrics("Ag", context).getHeight()) + value(contentPadding(height)));
    }
```

  - In `ThemePainting.height`, replace the `switch` with
    `DesignMetrics.Role padding = context.metrics().contentPadding(role);`. Keep the line measurement and the `return`.
  - In `Trees.keycapGroupHeight()`, replace the body with
    `return ThemePainting.height(context(), dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_TREE_GROUP_HEIGHT, dev.jasper.ui.metrics.Typography.Role.PANEL_TITLE);`.
  - In `FlatLafInstaller.install`, replace the five `fixed.put` lines with:

```java
        var metrics = DesignMetrics.DEFAULT;
        String control = Integer.toString(metrics.value(DesignMetrics.Role.CONTROL_RADIUS) * 2);
        fixed.put("Component.arc", control);
        fixed.put("TextComponent.arc", control);
        fixed.put("Button.arc", control);
        fixed.put("Component.focusWidth", Integer.toString(metrics.value(DesignMetrics.Role.FOCUS_RING)));
        fixed.put("Button.minimumHeight", Integer.toString(metrics.value(DesignMetrics.Role.BUTTON_HEIGHT)));
        fixed.put("Component.minimumHeight", Integer.toString(metrics.value(DesignMetrics.Role.FIELD_HEIGHT)));
        // Buttons as on main: a 14 px label margin and a 72 px minimum width (a window footer's action).
        int label = metrics.value(DesignMetrics.Role.BUTTON_LABEL_MARGIN), side = metrics.value(DesignMetrics.Role.BUTTON_HORIZONTAL_PADDING);
        fixed.put("Button.margin", insets(label, side, label, side));
        fixed.put("Button.minimumWidth", Integer.toString(metrics.value(DesignMetrics.Role.WINDOW_ACTION_MIN_WIDTH)));
        // Selection is a plain rectangle, edge to edge.
        for (var key : java.util.List.of("List", "Tree", "Table", "ComboBox", "MenuItem")) {
            fixed.put(key + ".selectionArc", "0");
            fixed.put(key + ".selectionInsets", "0,0,0,0");
        }
        // Native menu rows match the kit's: a 22 px row from a 16 px line, 8 px sides, 4 px popup padding, radius 6.
        int row = metrics.value(DesignMetrics.Role.MENU_ITEM_VERTICAL_PADDING), inset = metrics.value(DesignMetrics.Role.MENU_ITEM_PADDING);
        for (var key : java.util.List.of("MenuItem", "Menu", "CheckBoxMenuItem", "RadioButtonMenuItem")) fixed.put(key + ".margin", insets(row, inset, row, inset));
        int padding = metrics.value(DesignMetrics.Role.MENU_PADDING), line = metrics.value(DesignMetrics.Role.HAIRLINE);
        fixed.put("PopupMenu.borderInsets", insets(padding, line, padding, line));
        String radius = Integer.toString(metrics.value(DesignMetrics.Role.CARD_RADIUS));
        fixed.put("Popup.borderCornerRadius", radius);
        fixed.put("PopupMenu.borderCornerRadius", radius);
```

  Add the helper:

```java
    private static String insets(int top, int left, int bottom, int right) { return top + "," + left + "," + bottom + "," + right; }
```

- [ ] **Step 8: Run the two classes and watch them pass**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-ui:test --tests '*DesignMetricsTest' --tests '*SlateSteelInstallTest' > $S/t1-green.log 2>&1; tail -15 $S/t1-green.log
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Run the full suite to find the tests the scale moves**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew check --continue > $S/t1-check.log 2>&1; grep -c " FAILED$" $S/t1-check.log
```

Expected: about 84 failures, the ones in Step 10, give or take the few that the three new roles and the FlatLaf keys
move.

- [ ] **Step 10: Re-baseline.** Each failing assertion pinned an old mock measurement. The mocks now fix composition
  only (spec decision 1), so every expected value becomes the compact scale's. Apply these rules to every failure,
  re-running each class until it passes:
  - **R1. A literal that is a role's value becomes the role.** Use `DesignMetrics.DEFAULT.value(Role.X)`, scaled the
    way the test already scales (`UIScale.scale`, `s(...)`), so the next retune needs no re-baseline. For example,
    `isEqualTo(28)` for a button height becomes `isEqualTo(s(value(BUTTON_HEIGHT)))`.
  - **R2. A composite becomes the sum of its named roles.** For example, `488` (192 sidebar + 272 list + 24 detail
    inset) becomes `value(MANAGER_SIDEBAR_WIDTH) + value(MANAGER_LIST_WIDTH) + value(DETAIL_PADDING)`.
  - **R3. A content-driven measure takes the new actual**, after checking that it is the compact role, or the line plus
    the role's `contentPadding`. Examples are a packed dialog height, a palette list height and a hash of tile pixels.
  - **R4. Corner probes move with the radius.** A probe placed inside an old radius goes at `newRadius - 1`. A probe that
    expected an unfilled rounded corner where the radius is now 0 (`RESULTS_SELECTION_ARC`) asserts the fill instead;
    Task 2 rewrites those tests.
  - **R5. Names lose their numbers.** Methods and `as(...)` descriptions that state an old value are renamed:
    - `nominalButtonHeightIsTheVisibleFillAndRadiusIsSixLogicalPixels` → `nominalButtonHeightIsTheVisibleFillAndRadiusIsTheControlRadius`
    - `cardRadiusUsesNineLogicalPixelsRatherThanNinePixelArcDiameter` → `cardRadiusIsARadiusRatherThanAnArcDiameter`
    - `fullFooterUsesThirtyPixelActionsAndTwentyTwoPixelGapWithoutChangingOtherForms` → `fullFooterUsesItsOwnActionHeightAndGapWithoutChangingOtherForms`
    - `sheetIdentityUsesTheMeasuredTwelvePixelTitleGap` → `sheetIdentityUsesItsTitleGap`
    - descriptions such as "the rail keeps its 10px button arc" → "the rail keeps its button arc", and "shared 13px
      inset" → "shared setting inset".
  - **R6. Native chrome stays native.** `WorkspaceMetricsTest.nativeGeometryKeepsItsExactValuesUnderItsOwnNames` pins
    the new `RAIL_BUTTON_ARC`, `TITLE_SEARCH_ARC` and `DOCK_DROP_ARC` (8, 8, 8). Every other native value in it is
    unchanged.

  The failures the probe found (first failing assertion: old expected → new actual, at the probe's metrics; the
  probe's row padding was 3, so a few enlarged-font values can differ by 1 here):

- `jasper-app/src/test/java/dev/jasper/app/appearance/SurfaceGeometryTest.java`
  - `errorTransitionRepacksWithoutAStaleNarrowViewport`: 324 → 336 (SurfaceGeometryTest.java:102)
  - `actualVaultOwnersUseReferenceHeroGeometryWithoutCreatingWindows`: 428 → 379 (SurfaceGeometryTest.java:43)
- `jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java`
  - `actualMountedAndAdapterSizesApplyConfiguredPointsAndDisplayScaleOnce` [field accommodates actual text plus scaled padding once]: 28 → 24 (UiTypographyTest.java:35)
- `jasper-app/src/test/java/dev/jasper/app/files/FileChooserPanelTest.java`
  - `thePathBarIsDrawnAsATextFieldLikeTheSearchBox`: 24 vs greater than or equal to 28 (FileChooserPanelTest.java:531)
- `jasper-app/src/test/java/dev/jasper/app/notificationui/CardLayerTest.java`
  - `aCardShowsInsideATransparentUnfocusableLayer` [the former margin, named]: 12 → 10 (CardLayerTest.java:71)
- `jasper-app/src/test/java/dev/jasper/app/notificationui/NotificationCardTest.java`
  - `linksAndCloseUseTheCardGeometry`: 12 → 10 (NotificationCardTest.java:230)
- `jasper-app/src/test/java/dev/jasper/app/palette/CommandPaletteTest.java`
  - `selectedAndHoveredRowsPaintTheKitListColours` [selected rounded corner at java.awt.Point[x=674,y=40]: java.awt.Color[r=255,g=255,b=255] → java.awt.Color[r=63,g=111,b=168] (CommandPaletteTest.java:459)
  - `rowsExecuteOnAPressWhileHeadersAndBlankSpaceDoNothing`: 24 → 22 (CommandPaletteTest.java:312)
  - `aStepReplacesTheListAndHidesTheHintBar`: 148 → 133 (CommandPaletteTest.java:593)
  - `rowsLayOutOnOneLineAndTheTagGoesFirstWhenSpaceRunsOut` [the tag is right-aligned]: 672 → 674 (CommandPaletteTest.java:483)
  - `liveTypographyKeepsNominalMinimumsAndGrowsForRealContent`: 60 vs greater than or equal to 66 (CommandPaletteTest.java:760)
  - `theListShowsTheVisibleRowsAndScrollsToTheRest`: 192 → 177 (CommandPaletteTest.java:364)
  - `geometryFollowsSearchEverywhere`: 142 → 133 (CommandPaletteTest.java:344)
- `jasper-app/src/test/java/dev/jasper/app/plugins/EditorCompositionTest.java`
  - `dialogsComposeTheCanonicalSheetsAtTheirPackedSizes` [the form row's fixed label column and gap]: 108 → 106 (EditorCompositionTest.java:270)
  - `findAndReplaceAreAlignedCompactBarsThatLeaveTheDocumentAlone`: 26 → 24 (EditorCompositionTest.java:71)
- `jasper-app/src/test/java/dev/jasper/app/plugins/SessionsCompositionTest.java`
  - `actualLockedVaultAndSavedHostsComposeOneHeaderCountAndPersistentIdentity`: 12 → 10 (SessionsCompositionTest.java:31)
- `jasper-app/src/test/java/dev/jasper/app/plugins/StagedFileServerFormTest.java`
  - `actualGroupedFormAndAnchoredVaultCreationPreserveStateAndGrant`: 16 → 13 (StagedFileServerFormTest.java:30)
- `jasper-app/src/test/java/dev/jasper/app/windows/VaultSheetRenderTest.java`
  - `mountedTitleMenuAndAttachedFormsRenderAcrossThemesFontsAndShortOwners` [actual Login segment preserves its nonheader height in dark]: 24 → 22 (VaultSheetRenderTest.java:28)
- `jasper-app/src/test/java/dev/jasper/app/workspace/ToolStripeTest.java`
  - `theStripeLaysOutItsButtonsFromItsWidth` [the rail keeps its 10px button arc]: 10 → 8 (ToolStripeTest.java:132)
- `jasper-app/src/test/java/dev/jasper/app/workspace/WindowCommandPaletteTest.java`
  - `smallCardsKeepInputFixedAndSelectedResultsScrollable`: 44 → 39 (WindowCommandPaletteTest.java:487)
- `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceMetricsTest.java`
  - `nativeGeometryKeepsItsExactValuesUnderItsOwnNames` [RAIL_BUTTON_ARC]: 10 → 8 (WorkspaceMetricsTest.java:38)
- `jasper-ui/src/test/java/dev/jasper/ui/metrics/DesignMetricsTest.java`
  - `enlargedUserFontGetsEnoughContentBoundsWithoutChangingCatalog`: 30 vs greater than or equal to 34 (DesignMetricsTest.java:26)
  - `fixedLogicalGeometryIsIndependentOfDisplayScale`: 28 → 24 (DesignMetricsTest.java:10)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/AppManagerKitTest.java`
  - `aHostPresentsANativeArrangedTreeAsARailAndATableAsAListColumn` [a rail row]: 24 → 25 (AppManagerKitTest.java:166)
  - `sidebarDetailIsAManagerListBesideItsDetail` [one-line list rows take the rail row]: 24 → 25 (AppManagerKitTest.java:70)
  - `aNoticeIsAGroupedCardWithACrispHairline` [the corner is the card radius]: 0 → 27 (AppManagerKitTest.java:235)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/ButtonsTest.java`
  - `compactDetailFaceMatchesSourceExtentAndCornersWithoutShrinkingItsHitBox` [source top face row 0]: 118 → 120 (ButtonsTest.java:63)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/ChoicesTest.java`
  - `formSegmentsKeepMeasuredItemHeightAcrossFontSizes` [nonheader form segments keep the measured item height at font 13]: 24 → 22 (ChoicesTest.java:27)
  - `existingDropdownPopupTracksSemanticMenuColorsAndRows`: 24 vs greater than or equal to 28 (ChoicesTest.java:94)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/FormsTest.java`
  - `constrainedFormRowStacksRetainedControlsAndIgnoresInvisibleTrailing`: 108 → 106 (FormsTest.java:209)
  - `nestedVerticalStackMeasuresWrappedSettingsAtTheAssignedWidthAndReflowsOnResize`: 105 vs less than or equal to 103 (FormsTest.java:1287)
  - `sheetEnvelopeAddsItsBorderOutsideTheUnchanged480Fill`: 432 → 442 (FormsTest.java:248)
  - `windowFooterElidesItsLongStatusAndKeepsTheSharedActionHeight`: 44 → 36 (FormsTest.java:80)
  - `movingOutOfRowRestoresStandaloneLabelAndNestedHeroFieldsGetHeroSizing`: 28 vs greater than or equal to 32 (FormsTest.java:880)
  - `pathBrowseRingAndErrorRemainInsideTheNarrowRowAndActionUsesLatestDescriptor`: 198 vs less than or equal to 196 (FormsTest.java:1019)
  - `sheetIdentityUsesTheMeasuredTwelvePixelTitleGap`: 12 → 10 (FormsTest.java:264)
  - `heroPreviewReservesItsOwnHeightAboveFullWidthActions`: 73 vs less than or equal to 71 (FormsTest.java:1227)
  - `detailSettingRowsPlaceASecretBelowItsCaptionAcrossTheCardWidth`: 67 → 54 (FormsTest.java:153)
  - `threeColumnPanelsUseInsetRailAndListRowsAndAFullBleedDivider`: 8 → 6 (FormsTest.java:99)
  - `heroHeaderCentersItsTileAndTitlesWithSourceGaps`: 4 → 3 (FormsTest.java:930)
  - `formLabelsWrapWithoutElisionAndDetailMetadataUsesItsOwnGap`: 15 → 12 (FormsTest.java:139)
  - `mixedCardKeepsSettingsRowsEdgeToEdgeAndPaddedFormRowsAboveDivider`: 15 → 12 (FormsTest.java:1067)
  - `narrowAndEnlargedFontWrapDescriptionsAndKeepControlsInsideBounds`: 198 vs less than or equal to 196 (FormsTest.java:560)
  - `formRowAlignsLabelControlHelperAndTrailingPortInsidePaddedCard`: 108 → 106 (FormsTest.java:462)
  - `shortHostScrollsBodyAndKeepsFooterReachableAtEnlargedFont`: 29 vs greater than or equal to 30 (FormsTest.java:684)
  - `windowBodyFillsTheNativeContentWithoutInsetsAndPinsItsFooter` [x=0,y=0,width=900,height=511]: java.awt.Rectangle[x=0,y=0,width=900,height=511] → java.awt.Rectangle[x=0,y=0,width=900,height=519] (FormsTest.java:58)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/GroupedFormGeometryTest.java`
  - `mixedCardDividerUsesPreviousVisibleChildAcrossHiddenProtocolRows`: 14 → 11 (GroupedFormGeometryTest.java:39)
  - `fullFormsAccountForCardStrokeAndDependentDescriptionRows` [x=147,y=53,width=354,height=28]: java.awt.Rectangle[x=147,y=53,width=354,height=28] → java.awt.Rectangle[x=137,y=45,width=372,height=24] (GroupedFormGeometryTest.java:24)
  - `descriptionDensityFollowsCurrentFormKindWithoutChangingCompactAndHeroRows`: 15 → 12 (GroupedFormGeometryTest.java:230)
  - `orderedRowsAndTheirAddActionHaveSeparateSourceMeasuredHeights`: 102 → 84 (GroupedFormGeometryTest.java:110)
  - `fullSettingRowsUseCompactChoicesAndCloseTheirCardWithoutChangingSettingsForms` [FULL choice]: 26 → 24 (GroupedFormGeometryTest.java:154)
  - `fullFooterUsesThirtyPixelActionsAndTwentyTwoPixelGapWithoutChangingOtherForms` [FULL footer action]: 30 → 24 (GroupedFormGeometryTest.java:97)
  - `orderedRowsAlignIndexGlyphNameAndCompactCommandsToSourceColumns`: 12 → 10 (GroupedFormGeometryTest.java:126)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/HeroCompositionTest.java`
  - `settingAnnotationWrapsAndUsesItsOwnToneAtDefaultAndEnlargedUserFonts` [setting rows align the painted switch to the shared 13px inset]: 13 → 10 (HeroCompositionTest.java:123)
  - `strengthSpacingUsesOnlyPreviousVisibleSiblings`: 23 → 22 (HeroCompositionTest.java:70)
  - `supportingTextPaintsEveryLineBesideANaturalFirstLineAdornment`: 21 → 19 (HeroCompositionTest.java:35)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/InteractiveRowsTest.java`
  - `contextPopupOwnsThemedEntriesAndTearsDownTheirListeners`: org.opentest4j.AssertionFailedError:  expected: [0, 16777471, 348956415, 600156415, 717792 (InteractiveRowsTest.java:622)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/KitCalibrationTest.java`
  - `realFormFocusHaloIsExternalVisibleAndDoesNotChangeControlBounds` [font=java.awt.Font[family=Helvetica Neue,name=Helvetica Neue,style=pla]: 28 → 24 (KitCalibrationTest.java:64)
  - `formRowHelperWrapsAndDetailHasSourceInsets` [192 sidebar + 272 list + 24 detail inset]: 488 → 483 (KitCalibrationTest.java:189)
  - `menuCategoryHuesAreExplicitAndLegacyDefaultsRemainNeutral`: org.opentest4j.AssertionFailedError:  expected: [0, 16777216, 344765401, 596815827, 714256 (KitCalibrationTest.java:212)
  - `nominalButtonHeightIsTheVisibleFillAndRadiusIsSixLogicalPixels`: 28 → 24 (KitCalibrationTest.java:44)
  - `managerUsesThreeSemanticColumnsHiddenChromeAndPinnedTrailingAction`: 890 → 892 (KitCalibrationTest.java:133)
  - `cardRadiusUsesNineLogicalPixelsRatherThanNinePixelArcDiameter` [9px radius corner remains outside the full fill]: 243 vs less than 200 (KitCalibrationTest.java:150)
  - `sheetBottomCornersUseFullWindowRadius`: 242 vs less than 200 (KitCalibrationTest.java:172)
  - `panelAndPopupAndMenuItemUseFullNamedRadii`: 242 vs less than 200 (KitCalibrationTest.java:160)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/MenusTest.java`
  - `optionalMenuIconUsesSourceCategoryTileAndItemsFitFontGrowth`: 18 → 16 (MenusTest.java:222)
  - `categoryMenuUsesTheMeasuredPopupEnvelope` [width=222,height=137]: java.awt.Dimension[width=222,height=137] → java.awt.Dimension[width=197,height=103] (MenusTest.java:41)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/NativeControlStylesTest.java`
  - `ghostToolbarRecipeAddsRoleColorsButKeepsTheHostsArcAndMargin`: 10 → 8 (NativeControlStylesTest.java:63)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/PanelCompositionTest.java`
  - `normalHostActionsDoNotMakeTheHeaderWrapDuringPreferredMeasurement` [normal action row stays the panel header height]: 38 → 35 (PanelCompositionTest.java:866)
  - `detailFooterUsesCompactContextWhileManagerDetailKeepsItsPadding` [one 12px inset, no outer duplicate]: 12 → 10 (PanelCompositionTest.java:848)
  - `removingOptionalSlotsDetachesClosedWidgetsAndDenseFooterRestoresWhenMoved`: 13 → 10 (PanelCompositionTest.java:705)
  - `fourFixtureLayoutsRenderAtDefaultShortAndEnlargedFontWithPersistentSettingsFooter`: 35 vs greater than or equal to 38 (PanelCompositionTest.java:518)
  - `virtualizedFooterReservesOneRowRatherThanTheWholeCollection`: 38 → 35 (PanelCompositionTest.java:897)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/PanelSurfacesTest.java`
  - `aRouteStripLaysItsStepsInEqualColumnsWithTiledArtworkAndArrows`: 43 → 45 (PanelSurfacesTest.java:178)
  - `twoLinePanelRowsAreCompactInsetRowsWithTheSwitchAtTheirEnd`: 28 → 38 (PanelSurfacesTest.java:84)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/PickerMenusTest.java`
  - `richCompactAndDefaultHaveSeparateGeometryAndRetainInputAcrossRefresh`: 28 → 24 (PickerMenusTest.java:80)
  - `installedPopupWrapperFitsExactMenuWithoutScrollbar`: 212 → 214 (PickerMenusTest.java:59)
  - `emptyFacesUseSourceDensitySpecificTextInsets` [DEFAULT primary ink x at device2]: 61 vs between [67, (PickerMenusTest.java:19)
  - `referenceMenuAllocatesItsVisibleSearchAndUtilityRowsWithoutHorizontalClipping`: 107 → 92 (PickerMenusTest.java:65)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/ResultsKitTest.java`
  - `resultsKeepFixedHeightsAndShowTheVisibleRowsWithTheirHeadings`: 24 → 22 (ResultsKitTest.java:185)
  - `resultRowsLayOutOnOneLineAndTheTagGoesFirstWhenSpaceRunsOut` [the icon column]: 8 → 6 (ResultsKitTest.java:235)
  - `selectedAndHoveredResultsPaintRoundedRoleColours` [selected rounded corner]: 0 → 255 (ResultsKitTest.java:270)
- `jasper-ui/src/test/java/dev/jasper/ui/swing/SettingsControlsTest.java`
  - `settingsActionsHaveSourceSizedJoinedAndFooterEnvelopes`: 64 → 72 (SettingsControlsTest.java:46)
  - `navigationHasGroupBreaksWithoutExpandingTheHitTargets`: 29 → 25 (SettingsControlsTest.java:60)

- [ ] **Step 11: Run the full check**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew check > $S/t1-final.log 2>&1; tail -5 $S/t1-final.log
```

Expected: BUILD SUCCESSFUL. If `WindowContentFirstTerminalTest.aThrowingObserverDoesNotEscapeIntoThePanesAdoption`
fails, run it alone. It passes there (order-dependent, known); ledger it and re-run `check`.

- [ ] **Step 12: Commit**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && git add -A jasper-ui jasper-app plugins && git commit -m "Compact density: the compact scale

DesignMetrics roles take the compact values (spec 1), content heights pad
by role (contentPadding), FlatLaf's geometry derives from the roles, and
the tests that pinned mock measurements read the roles instead.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Selection is an edge-to-edge rectangle with white ink

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/InteractiveRows.java`. Changes: `RowsControl.gutter`,
  `selectionShown` and the sync loop; `Canvas.paintComponent`; `Row.inked`, `Row.paintFill`, `Row.repaint` and
  `Row.control`. The fill and the focus outline leave `Row.paintComponent`.
- Modify: `Labels.java` (`TextControl.inked`, `TextControl.onSelection(Nodes.Text)`, `IconControl.inked`)
- Modify: `Buttons.java` (`KitButton.onSelection`; `textRole`, `iconRole`, icon retint)
- Modify: `ItemLists.java:417-456, 476-492` (floors removed; a rail's tile is selected too)
- Modify: `Trees.java` and `Tables.java` (explicit foregrounds only on unselected rows; the table header adds the
  gutter)
- Modify: `Panels.java:72` (a list body's side inset becomes its rows' gutter), `ViewReconciler.java:175-179` and
  `SettingsShell.java:43` (the navigation's inset becomes its gutter)
- Modify: `Choices.java:177-198` (`optionLabel`), `RichOptions.java:48-53`, `PickerMenus.java:118, 124-137` and
  `SettingRows.java:33-95` (the search highlight)
- Modify: `DesignMetrics.java` and `compact-roles.tsv` (delete `PANEL_ROW_RADIUS`, `RESULTS_SELECTION_ARC` and
  `RESULTS_SELECTION_INSET`)
- Modify: `jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json:37` and
  `docs/design/theme-engine/slate-steel.md:80, 247` (Light `selection.bg` `#3F6FA8`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/SelectionInkTest.java`
- Test: `ResultsKitTest.selectedAndHoveredResultsPaintRoundedRoleColours` (rewritten),
  `AppManagerKitTest.aSelectedManagerRowsTextReadsOnTheSelectionInBothVariants` (rewritten),
  `CommandPaletteTest.selectedAndHoveredRowsPaintTheKitListColours` (corner probes)

**Interfaces:**
- Consumes: the Task 1 roles (`MENU_ITEM_RADIUS` still exists; Task 3 deletes it).
- Produces:
  - `void InteractiveRows.RowsControl.gutter(int logical)`
  - `boolean InteractiveRows.RowsControl.selectionShown(int index)`
  - `boolean InteractiveRows.Row.inked`
  - `static SemanticColor Labels.TextControl.onSelection(Nodes.Text)`
  - `boolean Labels.IconControl.inked`
  - `boolean Buttons.KitButton.onSelection`
  - The rule "selected = `selection.bg` (results: `accent`), a rectangle over the list's full width, white ink".
    Tasks 3 and 5 follow the same rule.

- [ ] **Step 1: Write the failing tests.** Create `SelectionInkTest`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 2: a selected row is a rectangle across the list's full width, its kit text and glyphs white. */
class SelectionInkTest {
    static ListItem item(String id) {
        return new ListItem(id, Optional.of(new Nodes.Icon(id + ".icon", ControlState.READY, "server", Nodes.Hue.NONE, Nodes.IconSize.CONTROL, "Host")),
            List.of(new Nodes.Text(id + ".title", ControlState.READY, "Host " + id, Nodes.TextRole.BODY)),
            List.of(new Nodes.Text(id + ".detail", ControlState.READY, "user@" + id, Nodes.TextRole.CAPTION)), List.of());
    }
    static ViewSpec list(String selected, Nodes.SelectionMode mode) {
        return new ViewSpec(new Nodes.ItemList("list", ControlState.READY, Nodes.ListKind.DOUBLE, List.of(item("a"), item("b")), Set.of(selected),
            "", List.of(), mode, Optional.empty(), OptionalInt.empty()));
    }
    /** The colour of an icon's most opaque pixel. */
    static Color ink(Icon icon) {
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
        int best = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getRGB(x, y) >>> 24) > (best >>> 24)) best = image.getRGB(x, y);
        return new Color(best & 0xFFFFFF);
    }

    @Test void aSelectedRowFillsTheListsWholeBandAsARectangleWithWhiteInk() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, list("a", Nodes.SelectionMode.SINGLE), e -> { })) {
                var rows = (InteractiveRows.RowsControl) view.control("list");
                rows.gutter(8);
                view.setSize(320, 200); InteractiveRowsTest.layout(view);
                var semantic = context.theme().semantic();
                var a = rows.mounted.get("a");
                assertThat(a.getX()).as("the row's content keeps the gutter").isEqualTo(UIScale.scale(8));
                var image = ResultsKitTest.paint(view);
                var top = SwingUtilities.convertPoint(a.getParent(), 0, a.getY(), view);
                Color selection = semantic.color(SemanticColor.SELECTION_BG);
                for (var probe : List.of(new Point(0, top.y), new Point(view.getWidth() - 1, top.y),
                        new Point(0, top.y + a.getHeight() - 1), new Point(view.getWidth() - 1, top.y + a.getHeight() - 1)))
                    assertThat(new Color(image.getRGB(probe.x, probe.y))).as("fill at " + probe).isEqualTo(selection);
                assertThat(find(view, "a.title", JLabel.class).getForeground()).isEqualTo(semantic.color(SemanticColor.TEXT_ONACCENT));
                assertThat(find(view, "a.detail", JLabel.class).getForeground()).isEqualTo(semantic.color(SemanticColor.TEXT_ONACCENT_SECONDARY));
                assertThat(ink(find(view, "a.icon", JLabel.class).getIcon())).isEqualTo(semantic.color(SemanticColor.TEXT_ONACCENT));
                view.update(list("b", Nodes.SelectionMode.SINGLE)); InteractiveRowsTest.layout(view);
                assertThat(find(view, "a.title", JLabel.class).getForeground()).as("deselected").isEqualTo(semantic.color(SemanticColor.TEXT_PRIMARY));
                assertThat(ink(find(view, "a.icon", JLabel.class).getIcon())).isNotEqualTo(semantic.color(SemanticColor.TEXT_ONACCENT));
                var after = ResultsKitTest.paint(view);
                assertThat(after.getRGB(0, top.y + 1) >>> 24).as("no fill left on the deselected row's band").isZero();
            } finally { release(context); }
        });
    }

    @Test void aFocusedUnselectedRowShowsTheHoverFillAndNoOutline() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var focus = new SettingsKitTest.Focus();
            try (var view = new KitView(context, list("a", Nodes.SelectionMode.MULTIPLE), e -> { })) {
                var rows = (InteractiveRows.RowsControl) view.control("list");
                view.setSize(320, 200); InteractiveRowsTest.layout(view);
                KeyboardFocusManager.setCurrentKeyboardFocusManager(focus); focus.focus(rows.canvas);
                rows.focusItem = "b"; rows.sync();
                var image = ResultsKitTest.paint(view);
                var b = rows.mounted.get("b");
                var top = SwingUtilities.convertPoint(b.getParent(), 0, b.getY(), view);
                var hover = ResultsKitTest.over(new Color(0, 0, 0, 0), ColorMath.opacity(context.theme().semantic().color(SemanticColor.HOVER_OVERLAY), .05));
                assertThat(new Color(image.getRGB(view.getWidth() / 2, top.y + b.getHeight() / 2), true)).isEqualTo(hover);
                var a = rows.mounted.get("a");
                var aTop = SwingUtilities.convertPoint(a.getParent(), 0, a.getY(), view);
                assertThat(new Color(image.getRGB(0, aTop.y))).as("the selected row's edge is fill, not an outline")
                    .isEqualTo(context.theme().semantic().color(SemanticColor.SELECTION_BG));
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); release(context); }
        });
    }

    @Test void aSelectedSettingsHeadingReadsInBothVariants() {
        Edt.run(() -> {
            for (var theme : List.of(KitFixtures.dark(), KitFixtures.light())) {
                var context = KitFixtures.context(theme);
                var child = new TreeItem("child", "Child", Optional.empty(), true, List.of());
                var group = new TreeItem("group", "Appearance & Behavior", Optional.empty(), false, List.of(child));
                var tree = new Nodes.Tree("tree", ControlState.READY, List.of(group), Set.of("group"), Set.of("group"), "");
                try (var view = new KitView(context, new ViewSpec(new Nodes.Stack("nav", ControlState.READY, Nodes.LayoutRole.SETTINGS_NAVIGATION, List.of(tree))), e -> { })) {
                    view.setSize(236, 300); InteractiveRowsTest.layout(view);
                    var rows = (Trees.TreeControl) view.control("tree");
                    var heading = (JLabel) rows.mounted.get("group").controls.get("@tree/tree/group/title").component;
                    var semantic = context.theme().semantic();
                    assertThat(heading.getForeground()).isEqualTo(semantic.color(SemanticColor.TEXT_ONACCENT));
                    assertThat(ColorMath.contrast(heading.getForeground(), semantic.color(SemanticColor.SELECTION_BG))).isGreaterThanOrEqualTo(4.5);
                    assertThat(rows.gutter).isEqualTo(context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_INSET));
                } finally { release(context); }
            }
        });
    }

    @Test void aPanelListTakesThePanelsInsetAsItsGutter() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, KitFixtures.panel("hosts"), e -> { })) {
                view.setSize(300, 400); InteractiveRowsTest.layout(view);
                var rows = (InteractiveRows.RowsControl) view.control("hosts.list");
                assertThat(rows.gutter).isEqualTo(context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.PANEL_ROW_SIDE));
                assertThat(SwingUtilities.getAncestorOfClass(JScrollPane.class, rows.canvas).getX()).as("the list reaches the panel's edges").isZero();
            } finally { release(context); }
        });
    }
}
```

  `KitFixtures.panel(name)` (`KitFixtures.java:110`) builds the named fixture panel. Read it and use the id of its
  SINGLE item list for `"hosts.list"`. If it has no SINGLE list, add a `"hosts"` case to it whose body is an
  `ItemList` SINGLE with id `hosts.list` and two `item(...)` rows.

  In `ResultsKitTest.selectedAndHoveredResultsPaintRoundedRoleColours`:
  - Rename the test `selectedAndHoveredResultsFillTheirWholeRow`.
  - Replace the two corner and inset assertions with fills at `(w - 1, 0)` and `(0, h - 1)` equal to `ACCENT`, and the
    hover equivalents.
  - Make the selected title's foreground `TEXT_ONACCENT`.

  In `AppManagerKitTest.aSelectedManagerRowsTextReadsOnTheSelectionInBothVariants`, replace each
  `SurfaceContrast.on(...)` expectation with the plain role: `TEXT_ONACCENT` for titles and `TEXT_ONACCENT_SECONDARY`
  for subtitles. Assert `ColorMath.contrast(TEXT_ONACCENT, SELECTION_BG) >= 4.5` in both variants.

- [ ] **Step 2: Run them and watch them fail**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-ui:test --tests '*SelectionInkTest' --tests '*ResultsKitTest' --tests '*AppManagerKitTest' > $S/t2-red.log 2>&1; grep -E "FAILED|error:" $S/t2-red.log | head -20
```

Expected: compilation fails on `gutter`. Once a stub `int gutter; void gutter(int v) { gutter = v; }` exists, the
tests fail on the corner fill, the white title, and the 3.98:1 Light contrast against `#5482B9`. Write that stub first
to see the assertion failures; Step 3 replaces it.

- [ ] **Step 3: The canvas paints the fill; rows carry the ink flag.** In `InteractiveRows.RowsControl`:

```java
        /** Logical inset of this list's rows from its edges; the owner moved its own side inset here (spec 2). */
        int gutter;
        void gutter(int logical) { if (gutter != logical) { gutter = logical; sync(); } }
        /** Whether row {@code index} shows the selection; an ordered list's shows only while it has focus. */
        boolean selectionShown(int index) {
            String id = itemId(index);
            if (!dataRow(index) || passive() || !selected.contains(id)) return false;
            if (this instanceof Trees.TreeControl tree && tree.detail != null && tree.detail.itemId.equals(id)) return false;
            return !(node instanceof Nodes.ItemList list && list.kind() == Nodes.ListKind.ORDERED) || canvas.isFocusOwner();
        }
```

  In `sync()`, set the flag before populating, inset the rows by the gutter, and inset the footer the same way:

```java
                int width = scroll.getViewport().getExtentSize().width, inset = com.formdev.flatlaf.util.UIScale.scale(gutter);
                // ... unchanged mounting ...
                    row.index = i;
                    row.inked = selectionShown(i);
                    populate(row, i);
                    row.finish();
                    row.setBounds(inset, top(i), Math.max(0, width - 2 * inset), rowShellHeight(i));
                    row.doLayout();
                // ...
                if (footer != null)
                    footer.setBounds(inset, rowsHeight(), Math.max(0, width - 2 * inset), footerHeight());
```

  Add a focus listener to the canvas in the constructor, so that an ordered list's ink follows its focus:
  `canvas.addFocusListener(new FocusAdapter() { public void focusGained(FocusEvent e) { sync(); } public void focusLost(FocusEvent e) { sync(); } });`.
  If a listener that repaints already exists, add `sync()` to it instead.

  In `Row`, add the flag and the fill, and delete everything in `paintComponent` after the heading block, from
  `if (owner.passive()) return;` to the end of the method. That removes the rounded group, the plain branch and both
  `BORDER_FOCUS` outlines.

```java
        /** Whether this row shows the selection: its kit text and glyphs take the on-accent roles (set before populate). */
        boolean inked;

        /** This row's selection or hover fill across the list's full {@code width}, in the canvas's coordinates. */
        void paintFill(Graphics graphics, int width) {
            if (!owner.dataRow(index) || owner.passive()) return;
            if (owner instanceof Trees.TreeControl tree && tree.detail != null && tree.detail.itemId.equals(id)) return;
            boolean focusedOnly = Objects.equals(id, owner.focusItem) && owner.canvas.isFocusOwner() && !owner.selected.contains(id);
            Color fill = owner.selectionShown(index)
                ? ThemePainting.color(owner.context(), owner.resultsLayout() ? SemanticColor.ACCENT : SemanticColor.SELECTION_BG)
                : hover || focusedOnly ? ColorMath.opacity(ThemePainting.color(owner.context(), SemanticColor.HOVER_OVERLAY), .05) : null;
            if (fill == null) return;
            graphics.setColor(fill);
            graphics.fillRect(0, getY(), width, getHeight());
        }

        /** The fill lies on the canvas across the list's width, so a row's repaint redraws its whole band. */
        @Override public void repaint(long tm, int x, int y, int width, int height) {
            if (owner != null && getParent() == owner.canvas) owner.canvas.repaint(tm, 0, getY() + y, owner.canvas.getWidth(), height);
            else super.repaint(tm, x, y, width, height);
        }
```

  At the end of `Canvas.paintComponent`, after the ordered list's surface, add:

```java
            // Selection and hover fill each row's whole band, edge to edge of the list, under the row's inset content.
            for (var row : owner.mounted.values()) row.paintFill(graphics, getWidth());
```

  In `Row.control(Nodes.Node node, boolean parentEnabled)`, just before `c.update(node)`, hand the flag to the control:

```java
            if (c instanceof Labels.TextControl text) text.inked = inked;
            else if (c instanceof Labels.IconControl icon) icon.inked = inked;
            else if (c instanceof Buttons.ButtonControl button) button.button.onSelection = inked;
```

- [ ] **Step 4: Text, glyphs and row buttons read the flag.**
  - In `Labels.TextControl`, add `boolean inked;`. In `refresh()`, compute the existing colour expression into
    `SemanticColor role = …;`, then `if (inked) role = onSelection(text);`, then call
    `label.setForeground(ThemePainting.color(context(), role))`.

```java
        /** A selected row's ink for {@code text}: white, supporting text in the secondary on-accent role. */
        static SemanticColor onSelection(Nodes.Text text) {
            boolean supporting = text.severity().isEmpty() && switch (text.role()) {
                case LABEL, DESCRIPTION, CAPTION, COUNT, MONO_CAPTION, SUPPORTING -> true;
                default -> false;
            };
            return supporting ? SemanticColor.TEXT_ONACCENT_SECONDARY : SemanticColor.TEXT_ONACCENT;
        }
```

  - In `Labels.IconControl`, add `boolean inked;`. In `refresh()`:
    - Change the tile branch's last argument from `selected` to `selected || inked`.
    - Put this branch first in the `setIcon` conditional:
      `inked && tileSize == 0 && receiving == ArtworkPresentation.DEFAULT ? context().icons().icon(icon.name(), context().theme().semantic(), SemanticColor.TEXT_ONACCENT, size) :`.
      The palette overload recolours every vector glyph; registered identity artwork keeps its pixels.
  - In `Buttons.KitButton`, add `boolean onSelection;`. In `textRole`, return `SemanticColor.TEXT_ONACCENT` first when
    `onSelection && (kind == Nodes.ButtonKind.GHOST || kind == Nodes.ButtonKind.DESTRUCTIVE_GHOST || kind == Nodes.ButtonKind.LINK)`.
    `iconRole` returns `TEXT_ONACCENT` when `onSelection` and there is no severity. In the icon retint in
    `paintComponent`, change `name == null || context.icons().authored(name, …)` to
    `name == null || !onSelection && context.icons().authored(name, …)`. When `onSelection` is set, paint
    `context.icons().icon(name, context.theme().semantic(), SemanticColor.TEXT_ONACCENT, getIcon().getIconWidth())`.
  - In `Buttons.ButtonControl.refresh()`, leave `onSelection` alone: `Row.control` sets it before each update.

- [ ] **Step 5: Lists, trees and tables stop painting their own selection ink.**
  - `ItemLists.populate`:
    - Delete the `panelSingle && selected` foreground loop (keep its `PANEL_ROW_SELECTED` font line, unconditional on
      `selected.contains`).
    - Delete the "A selected row on selection.bg" block and `onSelection`.
    - In `resultSelection`, keep only the font change (drop `setForeground`).
    - Change the tile's selected flag from `column != 0 && selected.contains(item.id())` to
      `selected.contains(item.id())`.
  - `Trees.populate`: wrap every explicit `setForeground` (emphasised `TEXT_LINK`, locked keycap `TEXT_SECONDARY`) in
    `if (!row.inked)`. `Tables.populate`: likewise for the muted `TEXT_PLACEHOLDER`. A selected row's ink comes only
    from the flag.
  - `Tables` header (line 178): `side = (column >= 0 ? ThemePainting.metric(context(), DesignMetrics.Role.COLUMN_LIST_SIDE) : pad) + com.formdev.flatlaf.util.UIScale.scale(gutter);`.

- [ ] **Step 6: Owners move their side inset into the gutter.**
  - `Panels.PanelControl`:
    - Add a field `InteractiveRows.RowsControl bodyRows;`. In `children(...)`, set it to the control whose
      `node.id()` equals `panel.body().id()` when that control is an `InteractiveRows.RowsControl`.
    - In `refresh()`, compute `side` as now. If `bodyRows != null`, call `bodyRows.gutter(...)` with the same value in
      logical pixels, `column == 0 || column == 1 ? COLUMN_LIST_SIDE : single || compact ? PANEL_ROW_SIDE : 0` via
      `context().metrics().value(...)`. Then set `side = 0` for the content border. Call the same code at the end of
      `children(...)`.
  - `ViewReconciler.applySettingsNavigation`: after setting `tree.settingsNavigation`, call
    `tree.gutter(navigation ? tree.context().metrics().value(DesignMetrics.Role.SETTINGS_NAV_INSET) : 0);`.
  - `SettingsShell` line 43: `navigation.setBounds(0, navTop, left, Math.max(0, getHeight() - bottom - navTop));`.

- [ ] **Step 7: Popup rows and the search highlight follow the rule.**
  - `Choices.optionLabel`: replace the rounded accent fill with
    `g.setColor(ThemePainting.color(context, SemanticColor.SELECTION_BG)); g.fillRect(0, 0, getWidth(), getHeight());`.
    Set the renderer background to `SELECTION_BG` when selected. In `refreshPopup`, set the list's selection
    background to `SELECTION_BG` too.
  - `RichOptions.paintComponent`: drop `spacing`. The fill is
    `g.setColor(ThemePainting.color(context, SemanticColor.SELECTION_BG)); g.fillRect(0, 0, getWidth(), getHeight());`.
  - `PickerMenus`:
    - The list border becomes `new EmptyBorder(0, 0, metric(PICKER_MENU_BOTTOM), 0)`, so rows reach the menu's edges.
      `Row.paintComponent` already insets its content by `pad`.
    - The active fill is `g.setColor(ThemePainting.color(c, SemanticColor.SELECTION_BG)); g.fillRect(0, top, getWidth(), rowHeight);`.
    - Make `SearchChoices.restyle()`'s list selection background `SELECTION_BG`.
  - `SettingRows.SearchHighlight.paintComponent`: `fillRect(0, 0, getWidth(), getHeight())`, with no radius.
    `RowControl.refresh()`, after the two `Composition.style` calls, adds:
    `if (row.highlighted()) { title.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_ONACCENT)); description.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_ONACCENT_SECONDARY)); title.noteColor = ThemePainting.color(context(), SemanticColor.TEXT_ONACCENT_SECONDARY); }`.
  - Delete the roles `PANEL_ROW_RADIUS`, `RESULTS_SELECTION_ARC` and `RESULTS_SELECTION_INSET` from `DesignMetrics`,
    and their lines from `compact-roles.tsv`.

- [ ] **Step 8: Light `selection.bg` is the Light accent.** In `slate-steel.json` line 37 and in
  `docs/design/theme-engine/slate-steel.md` (table line 80 and block line 247), change `"selection.bg": "#5482B9"` to
  `"selection.bg": "#3F6FA8"`. In the table row, change `` `#5482B9` `` to `` `#3F6FA8` ``. The existing
  block-identity test keeps the two in step.

- [ ] **Step 9: Run the task's tests and watch them pass**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-ui:test --tests '*SelectionInkTest' --tests '*ResultsKitTest' --tests '*AppManagerKitTest' > $S/t2-green.log 2>&1; tail -8 $S/t2-green.log
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Run the full check and re-baseline what the rule moves**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew check --continue > $S/t2-check.log 2>&1; grep " FAILED$" $S/t2-check.log
```

Expected: pixel and colour assertions on selected rows fail where they pinned a rounded fill, an inset fill,
`surface.control.selected` on a rail, a floored colour or a `BORDER_FOCUS` outline. Examples are
`InteractiveRowsTest`, `KitCalibrationTest`, `CommandPaletteTest.selectedAndHoveredRowsPaintTheKitListColours`, panel
and Settings navigation tests, and the `ThemeControllerTest` contrast lines that read Light `selection.bg`. Each now
expects the rule: a full-band rectangle of `selection.bg` (results: `accent`), with white text and glyphs. Where a test
pinned 3.98:1 for Light selected text, it becomes ≥ 4.5 (5.18:1). Re-run until green.

- [ ] **Step 11: Commit**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && git add -A jasper-ui jasper-app plugins docs/design/theme-engine/slate-steel.md && git commit -m "Compact density: rectangular selection with white ink

The list canvas fills a selected row's whole band in selection.bg (the
palette's in accent); rows take a gutter so the fill meets the list's
edges; selected rows paint white text and glyphs from one flag; Light
selection.bg is the Light accent, so white reads at 5.18:1.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: One menu look

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Menus.java`. `Popup` takes its own border; `Item` gains
  `describe`, `tile(boolean)`, `labelX` and `iconColumn`, plus the shortcut column painted from `shortcutText`;
  `MenuControl.refresh` uses `describe`.
- Modify: `InteractiveRows.java:371-376, 640-676` and `BreadcrumbControls.java:225-233` (use `Item.describe`)
- Modify: `DesignMetrics.java` and `compact-roles.tsv` (delete `MENU_ITEM_RADIUS`; add `MENU_SHORTCUT_GAP` 24)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/UiColorKeys.java:58-59, 110-117` and
  `jasper-ui/src/test/resources/dev/jasper/ui/theme/ui-color-keys.tsv` (native menu roles)
- Modify: `NativeControlStyles.java:127-136` (menu font BODY)
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/AppIcons.java:136-142` (white glyph on a highlighted menu
  item)
- Test: `MenusTest`, `SlateSteelInstallTest`, `AppIconsTest`, `ThemeControllerTest` (menu contrast) and
  `NativeControlStylesTest`

**Interfaces:**
- Consumes: Task 2's rule. Menus keep `accent`.
- Produces:
  - `void Menus.Item.describe(MenuItem item)`
  - `String Menus.Item.shortcutText` (package-private; Task 4 fills it)
  - `int Menus.Item.labelX()`
  - Role `MENU_SHORTCUT_GAP`

- [ ] **Step 1: Write the failing tests.**
  - In `MenusTest`, replace `categoryMenuUsesTheMeasuredPopupEnvelope` with the two tests below. Keep its existing
    fixture lines that build the four-item, one-separator category menu, and its `s(...)` helper.
  - In `optionalMenuIconUsesSourceCategoryTileAndItemsFitFontGrowth`, change the tile expectation to
    `s(value(MENU_TILE))`.

```java
    @Test void categoryMenuIsCompactAndSizedToItsRows() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, categoryMenu(), e -> { })) {   // the fixture the former envelope test used
                var popup = ((Menus.MenuControl) view.control("add")).popup;
                var size = popup.getPreferredSize();
                int widest = 0; for (var c : popup.getComponents()) if (c instanceof Menus.Item item) widest = Math.max(widest, item.getPreferredSize().width);
                var m = DesignMetrics.DEFAULT;
                assertThat(size.width).isEqualTo(Math.max(s(m.value(DesignMetrics.Role.MENU_MIN_WIDTH)), widest + 2 * s(m.value(DesignMetrics.Role.HAIRLINE))));
                assertThat(size.height).isEqualTo(2 * s(m.value(DesignMetrics.Role.MENU_PADDING)) + 4 * s(m.value(DesignMetrics.Role.MENU_ITEM_HEIGHT))
                    + 2 * s(m.value(DesignMetrics.Role.MENU_SEPARATOR_MARGIN)) + s(1));
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aHighlightedRowIsARectangleOfTheAccentAndRowsShareTheIconColumn() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var popup = new Menus.Popup(context);
            var tiled = new Menus.Item(context); tiled.describe(new MenuItem("a", "Login", "key", "a", true, false, Nodes.Hue.BLUE));
            var plain = new Menus.Item(context); plain.describe(new MenuItem("b", "Settings", "", "b", true, false));
            popup.add(tiled); popup.add(plain); popup.setSize(popup.getPreferredSize()); popup.doLayout();
            assertThat(plain.labelX()).as("a row without an icon aligns with the icon rows").isEqualTo(tiled.labelX());
            tiled.getModel().setArmed(true);
            var image = new java.awt.image.BufferedImage(tiled.getWidth(), tiled.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics(); try { tiled.paint(g); } finally { g.dispose(); }
            var accent = context.theme().semantic().color(SemanticColor.ACCENT);
            assertThat(new Color(image.getRGB(0, 0))).isEqualTo(accent);
            assertThat(new Color(image.getRGB(tiled.getWidth() - 1, tiled.getHeight() - 1))).isEqualTo(accent);
            KitViewTest.release(context);
        });
    }
```

  - In `SlateSteelInstallTest`, add:

```java
    @Test void nativeMenusTakeTheKitMenusRoles() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                for (boolean dark : new boolean[] {true, false}) {
                    var theme = SlateSteel.bundled().generated(dark); var p = theme.semantic();
                    FlatLafInstaller.install(theme);
                    for (var key : List.of("PopupMenu.background", "MenuItem.background", "Menu.background")) assertThat(UIManager.getColor(key)).as(key).isEqualTo(p.color(SemanticColor.SURFACE_MENU));
                    for (var key : List.of("MenuItem.selectionBackground", "Menu.selectionBackground", "CheckBoxMenuItem.selectionBackground", "RadioButtonMenuItem.selectionBackground"))
                        assertThat(UIManager.getColor(key)).as(key).isEqualTo(p.color(SemanticColor.ACCENT));
                    assertThat(UIManager.getColor("PopupMenu.borderColor")).isEqualTo(p.color(SemanticColor.BORDER_MENU));
                    assertThat(UIManager.getColor("MenuItem.acceleratorForeground")).isEqualTo(p.color(SemanticColor.TEXT_TERTIARY));
                    assertThat(UIManager.getColor("MenuItem.acceleratorSelectionForeground")).isEqualTo(p.color(SemanticColor.TEXT_ONACCENT));
                }
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }
```

  - In `AppIconsTest`, add a test. Install Slate Steel Dark the way the class's existing tests install a theme. Then
    build a `JMenuItem` whose icon is `AppIcons.semantic("file")`, call `item.getModel().setArmed(true)`, and paint the
    icon with `item` as its component. Its most opaque pixel equals
    `ThemeZone.color(item, SemanticColor.TEXT_ONACCENT)`. Unarmed, it equals `TEXT_PRIMARY`. Copy the `ink(Icon)`
    helper from `SelectionInkTest`.
  - In `ThemeControllerTest` line 340-342, the menu highlight is the accent: replace the comment with
    `// Menus highlight with the accent: white on it is 5.18:1 in Light and 4.27:1 in Dark, as authored.` and the bound
    with `themeDark ? 4.27 : 4.5`.

- [ ] **Step 2: Run them and watch them fail**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-ui:test --tests '*MenusTest' --tests '*SlateSteelInstallTest' :jasper-app:test --tests '*AppIconsTest' --tests '*ThemeControllerTest' > $S/t3-red.log 2>&1; grep -E "FAILED|error:" $S/t3-red.log | head
```

Expected: compilation fails on `describe` and `labelX`. With stubs in place, the failures are the popup size (the
6 px sides), the rounded highlight, the native menu roles and the unarmed glyph colour.

- [ ] **Step 3: Implement the kit menu.**
  - Delete `MENU_ITEM_RADIUS` (and its tsv line).
  - Add `MENU_SHORTCUT_GAP(24)` beside the menu roles, with the tsv line `MENU_SHORTCUT_GAP<TAB>24` at its enum
    position.
  - In `Menus`, have `Popup`'s constructor call `restyle()`:

```java
        /** A menu's card: 4 px above and below its rows, its 1 px edge beside them. */
        void restyle() {
            int padding = ThemePainting.metric(context, DesignMetrics.Role.MENU_PADDING), line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            setBorder(new EmptyBorder(padding, line, padding, line));
        }
```

  - `MenuControl.refresh` replaces `popup.setBorder(new EmptyBorder(pad, pad, pad, pad))` with `popup.restyle()`. Its
    per-item styling loop becomes `item.describe(descriptor); item.setEnabled(component.isEnabled() && descriptor.enabled());`.
    `new Item(context())` plus `item.setText(...)` becomes `var item = new Item(context()); item.describe(descriptor);`.
    The listener wiring is unchanged.
  - Replace `Item` with:

```java
    static final class Item extends JMenuItem {
        final KitContext context;
        /** What this row shows; a highlighted row repaints its category tile as a selected tile. */
        MenuItem descriptor;
        /** The bound shortcut of the row's action, as the menu shows it ("" for none). */
        String shortcutText = "";

        Item(KitContext context) {
            this.context = context;
            // Before any text: Swing parses markup when the text is set, so the property must already be there.
            putClientProperty("html.disable", true);
            setOpaque(false);
            setBorderPainted(false);
        }

        /** Shows {@code item}: its label, its category tile and its enablement, in the menu's type and insets. */
        void describe(MenuItem item) {
            descriptor = item;
            putClientProperty("Jasper.menu.item", item);
            setText(item.label());
            setEnabled(item.enabled());
            Composition.style(this, context, Typography.Role.BODY, SemanticColor.TEXT_PRIMARY);
            int inset = ThemePainting.metric(context, DesignMetrics.Role.MENU_ITEM_PADDING);
            setBorder(new EmptyBorder(0, inset, 0, inset));
            setIcon(item.icon().isBlank() ? null : tile(false));
            revalidate(); repaint();
        }

        javax.swing.Icon tile(boolean highlighted) {
            return context.icons().tile(descriptor.icon(), context.theme().semantic(), descriptor.hue(),
                ThemePainting.metric(context, DesignMetrics.Role.MENU_TILE), highlighted);
        }

        /** The icon column is there when any row of this menu has an icon; then every label starts after it. */
        boolean iconColumn() {
            if (getParent() == null) return getIcon() != null;
            for (var sibling : getParent().getComponents()) if (sibling instanceof Item item && item.getIcon() != null) return true;
            return false;
        }

        int labelX() {
            int x = getInsets().left;
            return iconColumn() ? x + ThemePainting.metric(context, DesignMetrics.Role.MENU_TILE) + ThemePainting.metric(context, DesignMetrics.Role.MENU_TILE_GAP) : x;
        }

        @Override public Dimension getPreferredSize() {
            var fm = getFontMetrics(getFont());
            int width = labelX() + fm.stringWidth(getText()) + getInsets().right;
            if (!shortcutText.isEmpty()) width += ThemePainting.metric(context, DesignMetrics.Role.MENU_SHORTCUT_GAP) + fm.stringWidth(shortcutText);
            return new Dimension(width, ThemePainting.height(context, DesignMetrics.Role.MENU_ITEM_HEIGHT, Typography.Role.BODY));
        }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                boolean highlighted = isEnabled() && (getModel().isArmed() || getModel().isRollover());
                if (highlighted) {
                    g.setColor(ThemePainting.color(context, SemanticColor.ACCENT));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                if (getIcon() != null) {
                    var icon = highlighted ? tile(true) : getIcon();
                    icon.paintIcon(this, g, getInsets().left, (getHeight() - icon.getIconHeight()) / 2);
                }
                g.setFont(getFont());
                var fm = g.getFontMetrics();
                int baseline = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g.setColor(ThemePainting.color(context, !isEnabled() ? SemanticColor.TEXT_PLACEHOLDER : highlighted ? SemanticColor.TEXT_ONACCENT : SemanticColor.TEXT_PRIMARY));
                g.drawString(getText(), labelX(), baseline);
                if (!shortcutText.isEmpty()) {
                    g.setColor(ThemePainting.color(context, !isEnabled() ? SemanticColor.TEXT_PLACEHOLDER : highlighted ? SemanticColor.TEXT_ONACCENT : SemanticColor.TEXT_TERTIARY));
                    g.drawString(shortcutText, getWidth() - getInsets().right - fm.stringWidth(shortcutText), baseline);
                }
            } finally {
                g.dispose();
            }
        }
    }
```

  - In `InteractiveRows.showMenu` and `BreadcrumbControls.context`, replace each block from `var entry = new
    Menus.Item(context());` through `entry.setIcon(...)` with:

```java
                var entry = new Menus.Item(context());
                entry.describe(item);
```

    Both keep their listener and `popup.add(entry)`. Their theme restyle (`InteractiveRows` 371-376) becomes
    `entry.describe(item)` per item, followed by `popup.restyle()`. When `InteractiveRows` and `BreadcrumbControls`
    create their `Menus.Popup`, the constructor applies the border.

- [ ] **Step 4: Native menus take the same roles.**
  - In `UiColorKeys`:
    - `@menuAcceleratorForeground` → `TEXT_TERTIARY`; add `role(keys, "@menuAcceleratorSelectionForeground", TEXT_ONACCENT);` after it.
    - `Menu.background`, `MenuItem.background` and `PopupMenu.background` → `SURFACE_MENU`.
    - `MenuItem.acceleratorForeground` → `TEXT_TERTIARY`; `PopupMenu.borderColor` → `BORDER_MENU`.
    - After `Menu.foreground`, add `Menu.selectionBackground` (`ACCENT`) and `Menu.selectionForeground` (`TEXT_ONACCENT`).
    - After `MenuItem.selectionForeground`, add `MenuItem.selectionBackground` (`ACCENT`).
    - After `MenuItem.acceleratorForeground`, add `MenuItem.acceleratorSelectionForeground` (`TEXT_ONACCENT`),
      `CheckBoxMenuItem.selectionBackground` (`ACCENT`) and `RadioButtonMenuItem.selectionBackground` (`ACCENT`).
    - Make the same edits, at the same positions, to `ui-color-keys.tsv` (key, a tab, then the role name). The frozen
      order test reads both.
  - In `NativeControlStyles.menu`, set the font to `font(Typography.Role.BODY)`, the kit menu's. Update
    `NativeControlStylesTest` where it pinned `LABEL`.
  - In `AppIcons.SemanticIcon.paintIcon`, replace `SemanticColor.TEXT_PRIMARY` with:

```java
                // A highlighted native menu row is the accent: its glyph turns white with its label.
                boolean highlighted = component instanceof JMenu menu ? menu.isSelected()
                    : component instanceof JMenuItem item && item.getModel().isArmed();
                preparation.paint(component, g, x, y, highlighted ? SemanticColor.TEXT_ONACCENT : SemanticColor.TEXT_PRIMARY);
```

    Add the imports `javax.swing.JMenu` and `javax.swing.JMenuItem`.

- [ ] **Step 5: Run the task's tests and watch them pass**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-ui:test --tests '*MenusTest' --tests '*SlateSteelInstallTest' --tests '*UiColorKeysTest' :jasper-app:test --tests '*AppIconsTest' --tests '*ThemeControllerTest' > $S/t3-green.log 2>&1; tail -8 $S/t3-green.log
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run the full check, re-baselining menu geometry** (`KitCalibrationTest.panelAndPopupAndMenuItemUseFullNamedRadii`
  now probes a square highlight; `InteractiveRowsTest.contextPopupOwnsThemedEntriesAndTearsDownTheirListeners` reads
  the new border and tile pixels) per Task 1's rules R1–R4.

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew check > $S/t3-check.log 2>&1; tail -5 $S/t3-check.log
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && git add -A jasper-ui jasper-app && git commit -m "Compact density: one menu look

Kit menus: 22 px rows, an icon column only when a row has an icon, a
square accent highlight with a selected tile, a shortcut column, 4 px
card padding. Native menus take the same roles through the look-and-feel
defaults, and their glyphs turn white when highlighted.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Shortcuts in menus

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/model/MenuItem.java` and
  `jasper-sdk/src/main/java/dev/jasper/sdk/ui/MenuItem.java` (`shortcut`)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java` (`0.16.0`); the four SDK tests that pin it
  (`CapabilitiesTest:8`, `FilesValuesTest:54`, `KitBoundaryTest:5`, `PaletteValuesTest:70`); the eight
  `plugins/*/src/main/resources/plugin.toml`; `jasper-sdk/README.md:38`; `docs/plugin-authoring.md:910-916`;
  `docs/plugin-ui.md`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java:40`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitContext.java` (shortcut labels), `KitView.java:45` (copy),
  `Menus.java` (`Item.describe` and `addNotify` resolve `shortcutText`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/AppearanceBindings.java`,
  `platform/PresentationIcons.java:24`, `plugins/HostedKit.java:30,54`, `plugins/HostedContext.java:90`,
  `plugins/PluginHost.java:98`, `plugins/PluginRuntime.java:366-371,553`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/KeyBindings.java` (`display`),
  `palette/CommandsScope.java:94-114` (delegates)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:954-961, 745`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java:822` (Close Tab's accelerator)
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java` (new case)
- Test: `MenusTest`, `KeyBindingsTest`, `JasperApplicationPluginsTest`, `TabShortcutsTest`, the SDK
  `KitValuesTest`, and the app `KitValuesTest` (parity, unchanged)

**Interfaces:**
- Consumes: `Menus.Item.shortcutText` and `describe` (Task 3).
- Produces:
  - `MenuItem.shortcut()` and `MenuItem withShortcut(String actionId)`, in the kit and the SDK
  - `KitContext(…, Function<String,String> shortcuts)` and `String KitContext.shortcut(String actionId)`
  - `void AppearanceBindings.shortcuts(Function<String,String>)`
  - `void PluginRuntime.shortcuts(Function<String,String>)`
  - `static String KeyBindings.display(KeyStroke, boolean macOs)`
  - `String JasperApplication.shortcutLabel(String actionId)`
  - `KeyBindings.Resolved JasperApplication.resolvedBindings()`

- [ ] **Step 1: Write the failing tests.**
  - In the SDK's `KitValuesTest`:

```java
    @Test void aMenuItemNamesItsShortcutSourceOrNone() {
        var plain = new MenuItem("open", "Open", "", "open", true, false);
        assertThat(plain.shortcut()).isEmpty();
        assertThat(plain.withShortcut("dev.jasper.sample.open").shortcut()).isEqualTo("dev.jasper.sample.open");
        assertThat(new MenuItem("open", "Open", "", "open", true, false, Nodes.Hue.BLUE).shortcut()).isEmpty();
        assertThatThrownBy(() -> plain.withShortcut(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatNullPointerException().isThrownBy(() -> new MenuItem("open", "Open", "", "open", true, false, Nodes.Hue.BLUE, null));
    }
```

  - In `KitContractTest`:

```java
    @Test void menuItemsKeepTheirShortcutSourceThroughMountAndUpdate() throws Exception { edt(() -> {
        var kit = kit("owner"); var events = new ArrayList<UiEvent>();
        var open = new MenuItem("open", "Open", "", "open", true, false).withShortcut("dev.jasper.sample.open");
        var spec = new ViewSpec(new Nodes.MenuButton("menu", ControlState.READY, "Actions", "", Nodes.ButtonKind.SECONDARY, "menu", List.of(open), "Actions", Nodes.ButtonPresentation.STANDARD));
        var view = kit.create(spec, events::add);
        try {
            view.update(spec); assertThat(view.spec()).isEqualTo(spec);
            emit(view, new UiEvent.Action("open")); assertThat(events).containsExactly(new UiEvent.Action("open"));
        } finally { view.close(); stop(kit); }
    }); }
```

  - In `MenusTest`:

```java
    @Test void aRowShowsItsActionsCurrentBindingRightAlignedAndNothingWhenUnbound() {
        Edt.run(() -> {
            var bindings = new java.util.HashMap<String, String>(java.util.Map.of("dev.jasper.sample.open", "⇧⌘O"));
            var base = KitFixtures.context();
            var context = new KitContext(base.binding(), base.route(), base.metrics(), base.icons(), base.animationsEnabled(), id -> bindings.getOrDefault(id, ""));
            var popup = new Menus.Popup(context);
            var open = new Menus.Item(context); open.describe(new MenuItem("o", "Open", "", "o", true, false).withShortcut("dev.jasper.sample.open"));
            var other = new Menus.Item(context); other.describe(new MenuItem("u", "Unbound", "", "u", true, false).withShortcut("dev.jasper.sample.none"));
            popup.add(open); popup.add(other);
            assertThat(open.shortcutText).isEqualTo("⇧⌘O");
            assertThat(other.shortcutText).isEmpty();
            int text = open.getFontMetrics(open.getFont()).stringWidth("Open"), keys = open.getFontMetrics(open.getFont()).stringWidth("⇧⌘O");
            assertThat(open.getPreferredSize().width).isEqualTo(open.labelX() + text + s(DesignMetrics.DEFAULT.value(DesignMetrics.Role.MENU_SHORTCUT_GAP)) + keys + open.getInsets().right);
            bindings.put("dev.jasper.sample.open", "⌥⌘O");
            open.addNotify();
            assertThat(open.shortcutText).as("a rebinding shows at the next open").isEqualTo("⌥⌘O");
            open.removeNotify();
            KitViewTest.release(base);
        });
    }
```

  - In `KeyBindingsTest`:

```java
    @Test void displayWritesTheMacGlyphsInOrderAndNamesElsewhere() {
        var stroke = KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        assertThat(KeyBindings.display(stroke, true)).isEqualTo("⇧⌘H");
        assertThat(KeyBindings.display(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), false)).isEqualTo("Ctrl+Shift+H");
        assertThat(KeyBindings.display(null, true)).isEmpty();
    }
```

  - In `JasperApplicationPluginsTest`, next to the plugin-less start test, using its setup lines:

```java
    @Test void menusShowTheCurrentBindingOfAnActionAndNothingForAnUnknownOne() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, null, false, dirs);
            boolean mac = System.getProperty("os.name").startsWith("Mac");
            var expected = KeyBindings.display(KeyBindings.defaults(mac).strokeFor(ActionId.NEW_TAB).orElseThrow(), mac);
            assertThat(application[0].shortcutLabel("new_tab")).isEqualTo(expected).isNotEmpty();
            assertThat(application[0].shortcutLabel("dev.example.unknown")).isEmpty();
            assertThat(application[0].themes().appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME).shortcut("new_tab")).isEqualTo(expected);
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }
```

  - In `TabShortcutsTest`:

```java
    @Test void theTabMenusCloseTabShowsTheCloseTabBinding() throws Exception {
        edt(() -> {
            var keys = KeyBindings.defaults(true);
            var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {}, ThemeTestSupport.controller(true), keys);
            try {
                var menu = owner.tabMenu(owner.currentTab());
                var close = java.util.Arrays.stream(menu.getComponents()).filter(c -> c instanceof JMenuItem item && item.getText().equals("Close Tab")).map(JMenuItem.class::cast).findFirst().orElseThrow();
                assertThat(close.getAccelerator()).isEqualTo(keys.strokeFor(ActionId.CLOSE_TAB).orElseThrow());
            } finally { owner.close(); }
        });
    }
```

- [ ] **Step 2: Run them and watch them fail**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-sdk:test --tests '*KitValuesTest' :jasper-ui:test --tests '*MenusTest' :jasper-app:test --tests '*KeyBindingsTest' --tests '*JasperApplicationPluginsTest' --tests '*TabShortcutsTest' > $S/t4-red.log 2>&1; grep -E "error:|FAILED" $S/t4-red.log | head
```

Expected: compilation fails on `withShortcut`, the six-argument `KitContext`, `KeyBindings.display` and
`shortcutLabel`.

- [ ] **Step 3: The shortcut source in the records.** Make both `MenuItem` records (kit and SDK) read:

```java
/** Immutable menuitem descriptor; {@code shortcut} names the action whose current binding the host shows beside the label ("" for none). */
public record MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator, Nodes.Hue hue, String shortcut) {
    /** Neutral artwork and no shortcut. */
    public MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator) {
        this(id, label, icon, action, enabled, separator, Nodes.Hue.NEUTRAL, "");
    }
    /** No shortcut. */
    public MenuItem(String id, String label, String icon, String action, boolean enabled, boolean separator, Nodes.Hue hue) {
        this(id, label, icon, action, enabled, separator, hue, "");
    }
    public MenuItem {
        ModelChecks.id(id); Objects.requireNonNull(hue); Objects.requireNonNull(shortcut, "shortcut");
        Objects.requireNonNull(label); Objects.requireNonNull(icon); Objects.requireNonNull(action); if (!separator) { ModelChecks.label(label, "label"); ModelChecks.label(action, "action"); }
        if (!shortcut.isEmpty()) ModelChecks.id(shortcut);
    }
    /** This item showing {@code actionId}'s current binding: an app action ({@code new_tab}) or a contributed one ({@code dev.jasper.remote.connect}). */
    public MenuItem withShortcut(String actionId) { return new MenuItem(id, label, icon, action, enabled, separator, hue, actionId); }
}
```

  - `KitValues.toModule(MenuItem)` passes `n.shortcut()` as the eighth argument.
  - Bump the SDK:
    - `JasperSdk.VERSION = "0.16.0"`, and update the four tests.
    - Each `plugin.toml`: `sdk = ">=0.15.5, <0.16"` → `sdk = ">=0.16.0, <0.17"`.
    - `jasper-sdk/README.md:38` → `` All bundled manifests now require `sdk = ">=0.16.0, <0.17"` (0.16.0 adds menu shortcuts: `MenuItem.withShortcut`; 0.15.5 added ten semantic colours for Slate Steel; 0.15.4 added the modal overlay). ``
      Keep the rest of the line.
    - `docs/plugin-authoring.md`: after the "Slate Steel colours (SDK 0.15.5)" section, add a section
      `### Menu shortcuts (SDK 0.16.0)`: "A menu item can show the current binding of an action beside its label:
      `new MenuItem(...).withShortcut("dev.example.open")`. Name an action your plugin contributes (or an app action
      such as `new_tab`); the host shows its binding in the platform's glyphs, follows the user's rebinding, and shows
      nothing when the action is unbound. Require `sdk = ">=0.16.0, <0.17"`." In the colour section's last line, change
      `sdk = ">=0.15.5, <0.16"` to `sdk = ">=0.16.0, <0.17"`.

- [ ] **Step 4: The kit context resolves labels.** In `KitContext`:

```java
    private final java.util.function.Function<String, String> shortcuts;
    public KitContext(ThemeBinding binding, ThemeRoute route, DesignMetrics metrics, IconRegistry icons, BooleanSupplier animationsEnabled) {
        this(binding, route, metrics, icons, animationsEnabled, action -> "");
    }
    /** {@code shortcuts} maps an action id to its current binding as menus show it ("" when unbound). */
    public KitContext(ThemeBinding binding, ThemeRoute route, DesignMetrics metrics, IconRegistry icons, BooleanSupplier animationsEnabled,
                      java.util.function.Function<String, String> shortcuts) {
        this.binding = Objects.requireNonNull(binding); this.route = Objects.requireNonNull(route);
        this.metrics = Objects.requireNonNull(metrics); this.icons = Objects.requireNonNull(icons);
        this.animationsEnabled = Objects.requireNonNull(animationsEnabled); this.shortcuts = Objects.requireNonNull(shortcuts);
    }
    KitContext(KitContext source) {
        this(source.binding, source.route, source.metrics, source.icons, source.animationsEnabled, source.shortcuts);
    }
    public java.util.function.Function<String, String> shortcuts() { return shortcuts; }
    /** The current binding of {@code actionId} as a menu shows it; "" for none or for an empty id. */
    public String shortcut(String actionId) { return actionId.isEmpty() ? "" : Objects.requireNonNullElse(shortcuts.apply(actionId), ""); }
```

  In `Menus.Item`, resolve the label in `describe` (after `setIcon`) and again in `addNotify`, so a rebinding shows at
  the next open:

```java
            shortcutText = context.shortcut(item.shortcut());
    // ...
        @Override public void addNotify() {
            super.addNotify();
            if (descriptor != null) { String next = context.shortcut(descriptor.shortcut()); if (!next.equals(shortcutText)) { shortcutText = next; revalidate(); } }
        }
```

  `PresentationIcons.context(source)` passes `source.shortcuts()` as the sixth argument.

- [ ] **Step 5: The app supplies the keymap.**
  - In `KeyBindings`, move the body of `CommandsScope.shortcutText` into:

```java
    /** A stroke as menus and the palette show it: "⇧⌘P" on macOS, "Ctrl+Shift+P" elsewhere, "" for none. */
    public static String display(KeyStroke stroke, boolean macOs) {
        if (stroke == null) return "";
        // body moved verbatim from CommandsScope.shortcutText
    }
```

    `CommandsScope.shortcutText(Object value, boolean macOs)` becomes
    `return value instanceof KeyStroke stroke ? KeyBindings.display(stroke, macOs) : "";`.
  - In `JasperApplication`, replace `bindingProblems()` with:

```java
    private boolean macOs() { return configuration == null ? System.getProperty("os.name").startsWith("Mac") : configuration.macOs(); }

    /** The keymap every window resolves right now: the saved shortcuts with the contributed actions' defaults. */
    KeyBindings.Resolved resolvedBindings() {
        ConfigSnapshot snapshot = configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot();
        List<KeyBindings.Extension> extensions = new ArrayList<>();
        for (ActionEntry action : contributions.actions())
            if (KeyBindings.extensionId(action.id())) extensions.add(action.extension());
        return snapshot.bindings(macOs()).withExtensions(extensions);
    }

    /** What resolving the saved shortcuts against the contributed actions could not honor, right now. */
    List<KeyBindings.Problem> bindingProblems() { return resolvedBindings().problems(); }

    /** A menu's text for {@code actionId}'s current binding in the platform's glyphs; "" when it is unbound or unknown. */
    String shortcutLabel(String actionId) {
        return KeyBindings.display(resolvedBindings().bindings().strokeFor(actionId).orElse(null), macOs());
    }
```

  - Next to `plugins.animations(themes::animationsEnabled);` (line 745), add `plugins.shortcuts(this::shortcutLabel);`.
    Right after `themes` is assigned (line 220), add `themes.appearance().shortcuts(this::shortcutLabel);`.
  - `AppearanceBindings`:
    - Add the field `private java.util.function.Function<String, String> shortcuts = action -> "";`.
    - Add `public void shortcuts(java.util.function.Function<String, String> labels) { shortcuts = java.util.Objects.requireNonNull(labels); }`.
    - `context(route)` returns `new KitContext(binding, route, DesignMetrics.DEFAULT, icons, animations, action -> shortcuts.apply(action))`.
      The lambda reads the field at call time, so contexts made before the wiring follow it.
  - `PluginRuntime`: add the field `private java.util.function.Function<String, String> shortcuts = action -> "";`,
    plus a setter `shortcuts(...)` shaped like `animations(...)` (it throws once plugins have started), and
    `created.shortcuts = shortcuts;` beside `created.animations`. `PluginHost`: add the field
    `java.util.function.Function<String, String> shortcuts = action -> "";` beside `animations`. `HostedContext` passes
    `action -> host.shortcuts.apply(action)` to `HostedKit`. `HostedKit` takes it as a new last constructor parameter
    (update the 5-argument delegating constructor to pass `action -> ""`) and passes it to `new KitContext(...)` in
    `create`.
  - `WindowContent.tabMenu`: after `var close = new JMenuItem("Close Tab");`, add
    `close.setAccelerator(bindings().strokeFor(ActionId.CLOSE_TAB).orElse(null));`.

- [ ] **Step 6: Run the task's tests and watch them pass**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-ui:test --tests '*MenusTest' :jasper-app:test --tests '*KeyBindingsTest' --tests '*JasperApplicationPluginsTest' --tests '*TabShortcutsTest' --tests '*KitValuesTest' --tests '*HostedKitContractTest' > $S/t4-green.log 2>&1; tail -8 $S/t4-green.log
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run the full check**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew check > $S/t4-check.log 2>&1; tail -5 $S/t4-check.log
```

Expected: BUILD SUCCESSFUL. A plugin test that pinned `0.15.5` or the manifest range follows the bump.
`KitCompatibility` keeps the 6- and 7-argument constructors.

- [ ] **Step 8: Commit**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && git add -A jasper-ui jasper-sdk jasper-sdk-testkit jasper-app plugins docs/plugin-authoring.md docs/plugin-ui.md && git commit -m "Compact density: shortcuts in menus, SDK 0.16.0

MenuItem names an action whose current binding the host shows beside the
label; the kit context resolves it from the app's keymap, so a rebinding
shows at the next open and an unbound action shows nothing. The tab
menu's Close Tab shows close_tab's binding.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Dropdown and picker popups

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Choices.java`. The popup becomes a named `KitComboPopup` with
  `bounds`. `popupPadding` is replaced. Rows are 22 px in the field's type.
- Modify: `SearchChoices.java:323-345` (border and row height), `RichOptions.java:40-46` (row height)
- Modify: `PickerMenus.java` (no fixed width; rows report their content width; BODY type)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedPopover.java:41-46` (`fit`)
- Modify: `DesignMetrics.java` and `compact-roles.tsv` (delete `PICKER_WIDTH`, `SETTINGS_OPTION_HEIGHT` and
  `SETTINGS_POPUP_PADDING`)
- Test: `ChoicesTest`, `PickerMenusTest`, `HostedAnchorTest`

**Interfaces:**
- Consumes: Task 2's `selection.bg` popup rows.
- Produces:
  - `static final class Choices.KitComboPopup extends BasicComboPopup` with
    `Rectangle bounds(int px, int py, int pw, int ph)`
  - `HostedPopover.fit` honouring the anchor's width
  - `PickerMenus.Row.getPreferredSize()` returning the content width

- [ ] **Step 1: Write the failing tests.**
  - In `ChoicesTest`, using its existing DROPDOWN fixture style:

```java
    @Test void aDropdownPopupIsAsWideAsItsFieldAndWiderOnlyForALongerRow() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var choice = new Nodes.Choice("mode", ControlState.READY, Nodes.ChoiceKind.DROPDOWN,
                List.of(new Option("a", "A"), new Option("long", "A considerably longer option than its field")), "a", "Mode", "");
            try (var view = new KitView(context, new ViewSpec(choice), e -> { })) {
                view.setSize(400, 80); InteractiveRowsTest.layout(view);
                var combo = KitViewTest.find(view, "mode", Choices.ChoiceCombo.class);
                var popup = (Choices.KitComboPopup) combo.popup;
                var list = popup.getList();
                assertThat(list.getFixedCellHeight()).isEqualTo(ThemePainting.height(context, DesignMetrics.Role.MENU_ITEM_HEIGHT, Typography.Role.BODY));
                int rows = list.getPreferredSize().width + popup.getInsets().left + popup.getInsets().right;
                assertThat(popup.bounds(0, 24, rows + 50, 100).width).as("as wide as a wider field").isEqualTo(rows + 50);
                assertThat(popup.bounds(0, 24, 80, 100).width).as("wider only for its widest row").isEqualTo(rows);
                assertThat(popup.getInsets().left).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE));
                assertThat(popup.getInsets().top).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.MENU_PADDING));
            } finally { KitViewTest.release(context); }
        });
    }
```

    `Option`'s two-argument form may not exist. If not, use the constructor `ChoicesTest` already uses for plain
    options.
  - In `HostedAnchorTest.popupFitsBelowOrAboveTheOwnerAndClampsToUsableScreen`, add:
    `assertThat(HostedPopover.fit(new java.awt.Rectangle(40,30,190,22),new java.awt.Dimension(150,100),screen)).as("as wide as its anchor").isEqualTo(new java.awt.Rectangle(40,52,190,100));`.
  - In `PickerMenusTest`, add:

```java
    @Test void theMenuIsSizedByItsWidestRowInTheDefaultFacesType() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var items = List.of(new Nodes.PickerItem("login", "Production database login", "deploy@prod", "key", Nodes.Hue.BLUE, Nodes.PickerItemKind.ITEM));
            var menu = new Nodes.PickerMenu("menu", ControlState.READY, "", "Search", items, "", "login", "No matches", "New Credential…", "create", "cancel");
            try (var view = new KitView(context, new ViewSpec(menu), e -> { })) {
                InteractiveRowsTest.layout(view);
                var control = (PickerMenus.MenuControl) view.control("menu");
                var row = control.list.getCellRenderer().getListCellRendererComponent(control.list, items.getFirst(), 0, false, false);
                var body = ThemePainting.font(context, Typography.Role.BODY);
                assertThat(row.getPreferredSize().width).isGreaterThan(control.list.getFontMetrics(body).stringWidth("Production database login"));
                assertThat(control.search.getFont()).isEqualTo(body);
                assertThat(control.component.getPreferredSize().width).isEqualTo(control.scroll.getPreferredSize().width);
            } finally { KitViewTest.release(context); }
        });
    }
```

    Use `PickerMenusTest`'s existing `PickerItem` and `PickerMenu` constructor shapes (its lines 26 and 53) if they
    differ from these.

- [ ] **Step 2: Run them and watch them fail**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-ui:test --tests '*ChoicesTest' --tests '*PickerMenusTest' :jasper-app:test --tests '*HostedAnchorTest' > $S/t5-red.log 2>&1; grep -E "error:|FAILED" $S/t5-red.log | head
```

Expected: `KitComboPopup` is missing. The fit test returns width 150, and the picker's width is 222.

- [ ] **Step 3: Dropdowns.** In `Choices.ChoiceCombo`:
  - Change the field type to `KitComboPopup popup`.
  - Delete `popupPadding()`.
  - Add the nested class, and create it in `createKitPopup`:

```java
    /** A dropdown's menu: as wide as its field, wider only when its widest row needs it (spec 3). */
    static final class KitComboPopup extends BasicComboPopup {
        final KitContext context;
        KitComboPopup(JComboBox<Object> combo, KitContext context) { super(combo); this.context = context; setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics) { Menus.paintSurface(this, graphics, context); }
        @Override protected Rectangle computePopupBounds(int px, int py, int pw, int ph) { return bounds(px, py, pw, ph); }
        Rectangle bounds(int px, int py, int pw, int ph) {
            var insets = getInsets();
            return super.computePopupBounds(px, py, Math.max(pw, list.getPreferredSize().width + insets.left + insets.right), ph);
        }
        /** 4 px above and below the rows, the 1 px edge beside them. */
        void restyle() {
            int padding = ThemePainting.metric(context, DesignMetrics.Role.MENU_PADDING), line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            setBorder(new EmptyBorder(padding, line, padding, line));
        }
        javax.swing.JList<Object> rows() { return list; }
    }
```

    `createKitPopup` becomes `popup = new KitComboPopup(comboBox, context); return popup;`.
  - In `refreshPopup`, replace the two padding lines with `popup.restyle();`. Replace the fixed cell height with
    `ThemePainting.height(context, DesignMetrics.Role.MENU_ITEM_HEIGHT, settingsWidth > 0 ? Typography.Role.LABEL : Typography.Role.BODY)`
    (rich rows stay `-1`). Replace the fixed width's `2 * pad` with `popup.getInsets().left + popup.getInsets().right`.
    Set the list font to the same role.
  - In `ChoiceControl.optionLabel`, replace `2*combo.popupPadding()` with
    `(combo.popup == null ? 0 : combo.popup.getInsets().left + combo.popup.getInsets().right)`. In a Settings popup,
    give a plain option label the LABEL font.
  - Read `SearchChoices.Popup.restyle()`'s `pad` from `MENU_PADDING`. Its border becomes
    `new EmptyBorder(pad, line, pad, line)` with `line = HAIRLINE`, and its fixed cell height is
    `ThemePainting.height(context, DesignMetrics.Role.MENU_ITEM_HEIGHT, Typography.Role.BODY)`.
  - In `RichOptions.getPreferredSize`, the popup minimum becomes
    `ThemePainting.height(context, DesignMetrics.Role.MENU_ITEM_HEIGHT, settings ? Typography.Role.LABEL : Typography.Role.BODY)`.
  - Delete `SETTINGS_OPTION_HEIGHT` and `SETTINGS_POPUP_PADDING` (roles and tsv lines).

- [ ] **Step 4: The credential picker.** In `PickerMenus`:
  - `height(c, r)` measures with `Typography.Role.BODY`. The search uses BODY:
    `searchSurface.role = Typography.Role.BODY;` and `search.setFont(ThemePainting.font(context(), Typography.Role.BODY))`.
    Rows draw with `line(..., compact = false, ...)`, giving BODY and CAPTION. The empty line uses
    `Typography.Role.CAPTION`.
  - `searchBox.setPreferredSize(...)` and `empty.setPreferredSize(...)` take width `0`, and the scroll's preferred width
    is `list.getPreferredSize().width`. Delete every `PICKER_WIDTH` read, and the role with its tsv line.
  - `Row.getPreferredSize()` reports its content:

```java
        @Override public Dimension getPreferredSize() {
            var c = owner.context();
            int pad = metric(c, DesignMetrics.Role.PICKER_PADDING), tile = metric(c, DesignMetrics.Role.PICKER_TILE), gap = metric(c, DesignMetrics.Role.PICKER_ROW_GAP);
            var fm = getFontMetrics(ThemePainting.font(c, Typography.Role.BODY));
            int text = fm.stringWidth(item.label());
            if (!item.caption().isEmpty()) text += metric(c, DesignMetrics.Role.PICKER_GAP) + getFontMetrics(ThemePainting.font(c, Typography.Role.CAPTION)).stringWidth(item.caption());
            return new Dimension(pad + tile + gap + text + gap + tile + pad, rowHeight(owner, item));
        }
```

  - `HostedPopover.fit`: `int width = Math.min(Math.max(preferred.width, anchor.width), usable.width), …`. A popover is
    at least as wide as the control that opened it.

- [ ] **Step 5: Run the task's tests and watch them pass**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-ui:test --tests '*ChoicesTest' --tests '*PickerMenusTest' --tests '*SearchableChoiceTest' --tests '*DesignMetricsTest' :jasper-app:test --tests '*HostedAnchorTest' > $S/t5-green.log 2>&1; tail -8 $S/t5-green.log
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run the full check, re-baselining popup geometry.** These now derive from the field and the rows, per
  Task 1's R1–R3:
  - `PickerMenusTest.installedPopupWrapperFitsExactMenuWithoutScrollbar` and
    `referenceMenuAllocatesItsVisibleSearchAndUtilityRowsWithoutHorizontalClipping`
  - `StagedAnchoredPickerTest`
  - `ChoicesTest.existingDropdownPopupTracksSemanticMenuColorsAndRows`

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew check > $S/t5-check.log 2>&1; tail -5 $S/t5-check.log
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && git add -A jasper-ui jasper-app && git commit -m "Compact density: dropdown and picker popups

A dropdown's menu is as wide as its field, wider only for its widest row,
with 22 px rows in the field's type; the credential picker sizes to its
rows and opens at least as wide as its face.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Settings fixes: the System page, decimal steppers and segments

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java`. `Button` gains `whenDisabled`;
  `ActionItem` gains `title`; `action(String, Supplier<String>, Button...)`; `actionNode` builds a `SettingRow`.
- Modify: `CorePages.java:116-125, 141-144` (the System rows, Font & Colors, line height step 0.1)
- Modify: `Editors.java:110-115, 487-488` (`number` is a stepper)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Choices.java:150` (segment padding)
- Modify: `DesignMetrics.java` and `compact-roles.tsv` (delete `SETTINGS_SEGMENT_PADDING`)
- Test: `SchemaPageTest`, `CorePagesTest`, `ChoicesTest`

**Interfaces:**
- Consumes: Task 1's `SEGMENT_HORIZONTAL_PADDING` (10) and `SETTINGS_ACTION_MIN_WIDTH` (72).
- Produces:
  - `SchemaPage.Spec action(String title, Supplier<String> description, SchemaPage.Button... buttons)`
  - `SchemaPage.Button(String label, Runnable action, BooleanSupplier enabled, String whenDisabled)`, plus its
    3-argument form

- [ ] **Step 1: Write the failing tests.**
  - In `SchemaPageTest`, replace `actionRowsReadTheirTextAndEnablementOnRefresh` and
    `aLongActionDescriptionFlowsAboveItsButtons` with:

```java
    @Test void anActionRowIsASettingsRowWithItsButtonsAtTheEnd() {
        boolean[] enabled = {false};
        int[] runs = {0};
        var page = SchemaPage.page("system", "System Settings")
            .action("Migrate old settings", () -> "Rewrite old names.", new SchemaPage.Button("Migrate…", () -> runs[0]++, () -> enabled[0], "Nothing to migrate"))
            .build(context);
        page.component();
        var row = (Nodes.SettingRow) ((Nodes.Card) ((Nodes.Stack) page.viewSpec().root()).children().getFirst()).children().getFirst();
        assertThat(row.title()).isEqualTo("Migrate old settings");
        assertThat(row.description()).isEqualTo("Rewrite old names.");
        JButton migrate = kit(page, page.actionButtons().getFirst(), JButton.class);
        assertThat(migrate.getText()).isEqualTo("Migrate…");
        assertThat(migrate.isEnabled()).isFalse();
        assertThat(migrate.getToolTipText()).isEqualTo("Nothing to migrate");
        enabled[0] = true;
        page.refresh();
        assertThat(migrate.isEnabled()).isTrue();
        assertThat(migrate.getToolTipText()).isNull();
        migrate.doClick();
        assertThat(runs[0]).isEqualTo(1);
    }

    @Test void aLongActionDescriptionWrapsBesideOrAboveItsButtonsInsideThePage() {
        String text = "Rewrite every option that an older Jasper wrote under a former name, keeping the file's comments and order. ".repeat(3).strip();
        var page = SchemaPage.page("system", "System Settings")
            .action("Migrate old settings", () -> text, new SchemaPage.Button("Migrate…", () -> { }, () -> true))
            .build(context);
        var body = page.component();
        body.setSize(420, 600);
        for (int pass = 0; pass < 3; pass++) KitTestSupport.layout(body);
        var label = labelWithText(body, text);
        assertThat(label).as("the description is shown whole").isNotNull();
        JButton migrate = kit(page, page.actionButtons().getFirst(), JButton.class);
        var labelBounds = javax.swing.SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), body);
        var buttonBounds = javax.swing.SwingUtilities.convertRectangle(migrate.getParent(), migrate.getBounds(), body);
        assertThat(labelBounds.intersects(buttonBounds)).as("the button never covers the text").isFalse();
        assertThat(buttonBounds.x + buttonBounds.width).as("the button stays inside the page").isLessThanOrEqualTo(body.getWidth());
    }
```

    The page opens with no `.section(...)`, so its first child is the card itself.
  - In `CorePagesTest.systemSettingsOffersTheConfigurationFileActions`, the button texts become
    `.containsExactly("Open in Editor", "Reveal", "Reload", "Migrate…")`. Add:

```java
        var titles = new java.util.ArrayList<String>();
        new Object() { void walk(Nodes.Node node) { if (node instanceof Nodes.SettingRow row) titles.add(row.title());
            if (node instanceof Nodes.Stack s) s.children().forEach(this::walk); if (node instanceof Nodes.Card c) c.children().forEach(this::walk);
            if (node instanceof Nodes.Section s) walk(s.body()); } }.walk(systemPage.viewSpec().root());
        assertThat(titles).containsSubsequence("Location", "Reload from disk", "Migrate old settings");
```

  The walk reads the record accessors `Stack.children()`, `Card.children()` and `Section.body()` (both walks in this
  task share the shape).
  - In `CorePagesTest`, add:

```java
    @Test void decimalSettingsAreSteppers() {
        var pages = CorePages.pages(APPEARANCE, system, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        var font = (SchemaPage) pages.stream().filter(page -> page.id().equals("terminal.font")).findFirst().orElseThrow();
        font.component();
        var fields = new java.util.HashMap<String, Nodes.Field>();
        new Object() { void walk(Nodes.Node node) { if (node instanceof Nodes.Field field) fields.put(field.id(), field);
            if (node instanceof Nodes.Stack s) s.children().forEach(this::walk); if (node instanceof Nodes.Card c) c.children().forEach(this::walk);
            if (node instanceof Nodes.Section s) walk(s.body()); if (node instanceof Nodes.SettingRow r) walk(r.control()); } }.walk(font.viewSpec().root());
        var size = fields.get(CoreSettings.TERMINAL_FONT_SIZE.name());
        assertThat(size.presentation()).isEqualTo(Nodes.FieldPresentation.STEPPER);
        assertThat(size.numeric().orElseThrow().step()).isEqualTo(0.5);
        assertThat(size.hint()).isEqualTo("pt");
        var line = fields.get(CoreSettings.TERMINAL_FONT_LINE_HEIGHT.name());
        assertThat(line.presentation()).isEqualTo(Nodes.FieldPresentation.STEPPER);
        assertThat(line.numeric().orElseThrow().step()).isEqualTo(0.1);
        assertThat(line.numeric().orElseThrow().decimals()).isEqualTo(2);
    }
```

  - In `ChoicesTest`, add:

```java
    @Test void aSettingsSegmentsLabelKeepsItsPaddingFromTheSegmentsEdge() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var choice = new Nodes.Choice("toolbar", ControlState.READY, Nodes.ChoiceKind.SEGMENTED,
                List.of(new Option("icons", "Icons Only"), new Option("both", "Icons & Labels"), new Option("hidden", "Hidden")), "both", "Toolbar", "");
            try (var view = new KitView(context, new ViewSpec(new Nodes.Stack("page", ControlState.READY, Nodes.LayoutRole.SETTINGS_PAGE, List.of(choice))), e -> { })) {
                view.setSize(600, 200); InteractiveRowsTest.layout(view);
                int pad = ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_HORIZONTAL_PADDING);
                for (var button : KitViewTest.all(view, Choices.SegmentButton.class)) {
                    assertThat(button.getInsets().left).isEqualTo(pad);
                    assertThat(button.getInsets().right).isEqualTo(pad);
                    int text = (int) Math.ceil(button.getFont().getStringBounds(button.getText(), new java.awt.font.FontRenderContext(null, true, true)).getWidth());
                    assertThat(button.getWidth()).as(button.getText()).isGreaterThanOrEqualTo(text + 2 * pad);
                }
            } finally { KitViewTest.release(context); }
        });
    }
```

    `KitViewTest.all` returns every descendant of a type. If no such helper exists, add it to `KitViewTest` beside
    `find`: a recursive walk over `Container.getComponents()` that collects instances of the class.

- [ ] **Step 2: Run them and watch them fail**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-ui:test --tests '*ChoicesTest' :jasper-app:test --tests '*SchemaPageTest' --tests '*CorePagesTest' > $S/t6-red.log 2>&1; grep -E "error:|FAILED" $S/t6-red.log | head
```

Expected: compilation fails on `action(String, …)` and the 4-argument `Button`. The segment insets are 0 in Settings,
and the font size field is STANDARD.

- [ ] **Step 3: Action rows.** In `SchemaPage`:

```java
    /** A button on an action row; {@code enabled} is read again on every refresh, and {@code whenDisabled} is its tooltip while disabled. */
    public record Button(String label, Runnable action, BooleanSupplier enabled, String whenDisabled) {
        public Button(String label, Runnable action, BooleanSupplier enabled) { this(label, action, enabled, ""); }
    }
    private record ActionItem(String title, Supplier<String> description, List<Button> buttons) implements Item { }
    // in Spec:
        /** A settings row of buttons: {@code title} and {@code description} on the left, the buttons at its end. */
        public Spec action(String title, Supplier<String> description, Button... buttons) { items.add(new ActionItem(title, description, List.of(buttons))); return this; }
    // actionNode:
    private Nodes.Node actionNode(ActionRow action) {
        var buttons = new ArrayList<Nodes.Node>();
        for (int i = 0; i < action.item().buttons().size(); i++) {
            var button = action.item().buttons().get(i);
            boolean enabled = button.enabled().getAsBoolean();
            buttons.add(new Nodes.Button(action.buttonId(i), new ControlState(true, enabled, false, ""), button.label(), "",
                Nodes.ButtonKind.SECONDARY, action.buttonId(i), enabled ? "" : button.whenDisabled()));
        }
        var control = new Nodes.Stack("action-" + action.index() + ":buttons", ControlState.READY, Nodes.LayoutRole.INLINE, buttons);
        return new Nodes.SettingRow("action-" + action.index(), ControlState.READY, action.item().title(), action.item().description().get(), control, false, false);
    }
```

  In `CorePages`, replace the System page's three `.action(...)` calls:

```java
                .action("Location", () -> system.file().get().toString(),
                    new SchemaPage.Button("Open in Editor", system.openInEditor(), () -> true),
                    new SchemaPage.Button("Reveal", system.reveal(), () -> true))
                .action("Reload from disk", () -> "Read the file again after editing it elsewhere.",
                    new SchemaPage.Button("Reload", system.reload(), () -> true))
                .action("Migrate old settings", () -> "Rewrite old setting names to the current layout; the original is backed up first.",
                    new SchemaPage.Button("Migrate…", system.migrate(), system.migratable(), "Nothing to migrate")),
```

  Replace Font & Colors' action with
  `.action("Terminal colors", () -> "They come from the terminal scheme in Appearance.", new SchemaPage.Button("Choose Scheme…", () -> navigate.accept("appearance"), () -> true))`.

- [ ] **Step 4: Decimal steppers.**
  - `Editors.number`: pass `Nodes.FieldPresentation.STEPPER` as `NumberField`'s fifth argument, and update its JavaDoc
    to "A − value + stepper within the kind's range with up to two decimals; the buttons, Up and Down step by
    `step`."
  - `OptionalNumber` (line 488) does the same.
  - `CorePages` line 141: `Editors.number(0.1, "×")`.

- [ ] **Step 5: Segment padding.** In `Choices` line 150, use
  `int pad = ThemePainting.metric(context(), DesignMetrics.Role.SEGMENT_HORIZONTAL_PADDING);` for every segment.
  Delete `SETTINGS_SEGMENT_PADDING` (role and tsv line).

- [ ] **Step 6: Run the task's tests and watch them pass**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-ui:test --tests '*ChoicesTest' --tests '*DesignMetricsTest' :jasper-app:test --tests '*SchemaPageTest' --tests '*CorePagesTest' > $S/t6-green.log 2>&1; tail -8 $S/t6-green.log
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run the full check, re-baselining the Settings renders that moved**
  - `SettingsControlsTest.settingsActionsHaveSourceSizedJoinedAndFooterEnvelopes`: `SETTINGS_ACTION_MIN_WIDTH`.
  - Plugin settings pages, where a decimal setting now renders as a stepper.
  - `SettingsDialog` previews.

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew check > $S/t6-check.log 2>&1; tail -5 $S/t6-check.log
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && git add -A jasper-ui jasper-app && git commit -m "Compact density: Settings fixes

System › Configuration file is one card of settings rows with their
buttons at the end; decimal settings are − value + steppers (line height
steps by 0.1); a segment's label keeps 10 px from its edge.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: Re-render, the acceptance record, documentation and the full check

**Files:**
- Modify: `docs/design/theme-engine/acceptance.md` (a "Compact density" section)
- Modify: `docs/design/theme-engine/slate-steel.md` (design notes)
- Modify: `docs/STATUS.md`; `docs/superpowers/specs/2026-10-12-jasper-compact-density-design.md` (status line); this
  plan (status banner)
- Modify: any doc that states an old size, found in Step 4
- Test: `./gradlew check` and `AppDocumentationTest`

**Interfaces:**
- Consumes: Tasks 1–6.
- Produces: the user's native checklist.

- [ ] **Step 1: Re-render the comparisons and the gallery**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew :jasper-app:themeSurfacePreview :jasper-app:settingsSurfacePreview :jasper-app:settings9bPreview :jasper-app:workspaceSurfacePreview > $S/t7-previews.log 2>&1 && ./gradlew :jasper-app:commandPalettePreview --args="$PWD/jasper-app/build/palette-surfaces --task9c" >> $S/t7-previews.log 2>&1 && ./gradlew :jasper-app:test --tests '*StagedAnchoredPickerTest' --tests '*VaultSheetRenderTest' --tests '*StagedAddHostTest' --tests '*StagedFileServerFormTest' --tests '*SessionsCompositionTest' >> $S/t7-previews.log 2>&1 && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark :jasper-ui:gallery -Pjasper.galleryTheme=light >> $S/t7-previews.log 2>&1; tail -3 $S/t7-previews.log
```

Then compare the 34 pairs into a scratch directory:

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && python3 -I tools/visual/compare_all.py $S/compare > $S/t7-compare.txt 2>&1; cat $S/t7-compare.txt
```

Expected: 34 pairs scored. Edge agreement falls where sizes changed (spec decision 1: expected).

- [ ] **Step 2: Inspect composition in both variants.** Open each Dark render and each Light render (Settings pages,
  Vault hero/manager/menu/sheets, Add Host, New File Server, New Tunnel, the workspace, the palette, the credential
  picker, the four panels and the gallery) beside its mock, with the Read tool on the PNGs. For each, check:
  - **Composition:** the same controls in the same order, grouping and alignment.
  - **Selection:** an edge-to-edge rectangle with white text and glyphs.
  - **Menus:** 22 px rows, the icon column, square highlights.
  - **No clipping:** nothing clips at 24 px.

  For a family that fails composition, fix the painter that holds a fixed offset (spec §7). Do it test-first, with the
  failing render as the test where the family has one. Record each fix.

- [ ] **Step 3: The acceptance record.** In `docs/design/theme-engine/acceptance.md`, add a section
  `## Compact density (2026-10-12)` after the Slate Steel Plan 3 section, containing:
  - The scale in one paragraph: controls 24, rows 22–24, radius 4 (cards 6, overlay card 8), selection a rectangle,
    and spacing × 0.8. Link to [the plan's role table](../../superpowers/plans/2026-10-12-jasper-compact-density.md#the-compact-role-table).
  - A table of the 34 pairs: reference, surface (pair), the Slate Steel Dark value from the Plan 3 table, and the
    compact value from Step 1. The notes column says "sizes from the compact scale" where the drop comes from size.
  - The Light/Dark inspection list from Step 2, with the fixes it made.
  - Design notes:
    - White on Light `selection.bg` is now 5.18:1 (the 3.98:1 note is retired).
    - Menus and the palette highlight with `accent`: white on it is 5.18:1 in Light and 4.27:1 in Dark, as authored.
  - **User-run checks:**
    - Menus (kit: Sessions row menu, Vault add menu; native: tab menu, terminal context menu, editor popup) for 22 px
      rows, shortcuts and white highlight ink.
    - Dropdowns (Theme, Terminal scheme, Via Host) and the credential picker as wide as their faces.
    - Selection in lists, the Settings navigation (select "Appearance & Behavior") and the file chooser.
    - Buttons and fields at 24 px.
    - System › Configuration file.
    - The font size and line height steppers.
    - Window › Toolbar's segments.
    - An 18-pt interface font.

  In `docs/design/theme-engine/slate-steel.md`, wherever its notes state the Light menu selection text 3.98:1 or the
  floor for selected rows, replace the text with the two design notes above.

- [ ] **Step 4: Stale sizes in the docs.** Find every doc statement of an old size:

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && grep -rnE "MENU_MIN_WIDTH|PICKER_WIDTH|SETTINGS_OPTION_HEIGHT|SETTINGS_POPUP_PADDING|SETTINGS_SEGMENT_PADDING|PANEL_ROW_RADIUS|RESULTS_SELECTION_|MENU_ITEM_RADIUS|PICKER_VERTICAL_PADDING|28 ?px|29 ?px|radius 6|222 ?px|5482B9" docs jasper-ui/README.md jasper-app/README.md jasper-sdk/README.md | grep -v "docs/superpowers/" | grep -v "acceptance.md"
```

  Update each hit to the compact value or role. Leave historical records (plans, specs, the acceptance record's earlier
  sections) as written.

- [ ] **Step 5: Status.**
  - In `docs/STATUS.md`, add a dated entry under the latest entry: "Compact density (2026-10-12, branch
    `claude/compact-density`, not merged): the compact scale, rectangular selection with white ink, one menu look with
    shortcuts (SDK 0.16.0), dropdown popups as wide as their fields, the System page rows, decimal steppers and segment
    padding. Native acceptance is the user's (the record)," with "the record" linking `design/theme-engine/acceptance.md#compact-density-2026-10-12`.
  - In the spec, change the status line to
    `Status: **Approved (2026-10-12)**; implemented by [the compact density plan](../plans/2026-10-12-jasper-compact-density.md).`
  - In this plan's banner, record completion and any deviation, with its ledger ruling.

- [ ] **Step 6: Run the full check**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && ./gradlew check > $S/t7-check.log 2>&1; tail -5 $S/t7-check.log; python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t=f=e=s=0
for p in pathlib.Path('.').glob('**/build/test-results/test/*.xml'):
    r=ET.parse(p).getroot(); t+=int(r.get('tests'));f+=int(r.get('failures'));e+=int(r.get('errors'));s+=int(r.get('skipped'))
print('tests',t,'failures',f,'errors',e,'skipped',s)
PY
```

Expected: BUILD SUCCESSFUL, with 0 failures and 0 errors.

- [ ] **Step 7: Commit**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay && git add -A docs jasper-ui jasper-app && git commit -m "Compact density: comparisons, acceptance record and status

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
