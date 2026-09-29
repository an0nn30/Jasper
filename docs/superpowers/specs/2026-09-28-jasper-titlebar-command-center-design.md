# Title-bar command centre, scope chips and custom title bars

**Status:** Design approved in chat by the user on 2026-09-28; written specification approved the same day. Plan A merged 2026-09-29; plan B implemented 2026-09-29 on `claude/titlebar-plan-b`, with the user's 2026-09-29 amendments (the field always centred; the palette card one colour).

It amends:
- [the unified header](2026-09-27-jasper-unified-header-design.md), where the header layout changes;
- [Search Everywhere](2026-09-25-jasper-search-everywhere-design.md), where the palette's tabs become chips.

## Purpose and scope

Jasper's command palette moves into the title bar, the way VS Code's command centre works:
- A rounded field in the middle of the header shows the working context. Clicking it or pressing ⌘K / Ctrl+K drops the palette down from the field.
- The toolbar buttons move to the right side. When they don't fit, whole buttons fold into a vertical-ellipsis (⋮) menu.
- The palette swaps its tab strip for VS Code-style scope **chips**.
- Windows and Linux get a custom title bar, laid out left to right: app icon, menus, field, toolbar, window controls. The menus collapse in two stages: first a "…" menu, then a ☰ hamburger.

Decisions the user made:
- **Palette placement:** the palette drops from the field, anchored to it and overlapping it. The field itself is only a click target.
- **Context:** the working-context label moves into the field, with its icon and full tooltip. The header no longer has a separate context label.
- **Scopes:** chips, not tabs. `>` means Commands and `<scope> >` means that scope, for example `history >`. How scopes should work will be explored further, so this spec keeps it minimal.
- **Shortcut:** ⌘K / Ctrl+K opens the palette with no chip, showing the mode list.
- **Look:** the field and palette should look like they belong to the app, very close to VS Code. The field has rounded edges and looks like a button and a text field combined. The palette has VS Code's outer edges but keeps Jasper's sections (results, groups, hint bar).
- **Windows and Linux:** a custom title bar holds the menus on the left, the field in the centre and the toolbar on the right. Linux uses it by default too, with an opt-out setting.
- **Menu collapse:** both stages, first "…" and then ☰.

Out of scope:
- back and forward buttons;
- VS Code's layout-toggle buttons;
- any animation when the palette opens;
- scope aliases or any other SDK change;
- changing how a scope searches or ranks;
- native tab placement in the title bar (the deferred TermLab-tabs spec).

## 1. `WindowHeader`: one header layout on every platform

A new `workspace/WindowHeader` panel has three regions and a custom layout manager.

**Leading slot**
- **macOS:** empty drag space after the traffic-light inset, which `MacTitleBar` computes as today.
- **Windows and Linux with custom decorations:** the app icon and the menus (§4).
- **Native-decoration fallback:** empty.

**Centre: `TitleSearchField`** (§2)
- It is always centred on the **window**, never slid off centre (user decision, 2026-09-29). Each side gives way instead: toolbar labels drop, then toolbar controls move into ⋮; on Windows and Linux the menus fold into "…" and then ☰. Only when a side can give way no further does the field shrink around the centre, and then collapse to its magnifier, still centred.
- Preferred width is 560 logical pixels, clamped to 240–640.

**Trailing: the existing `WindowToolbar`, right-aligned**
- The glue that pinned Find and Settings to the right is removed. The whole group packs against the right edge, before the window controls on Windows and Linux.
- The overflow button uses `general/moreVertical` (⋮). It stays the rightmost toolbar control, and its tooltip and accessible name remain "More actions".
- Hiding is still by priority and unchanged (lowest first, later controls first within a priority). The toolbar gets its full width, capped by what is left after the leading slot and the field's **preferred** width, and never less than its own minimum (padding plus ⋮). That is what makes buttons overflow before the field shrinks. (Corrected by plan A; the first version said the field's minimum width, which contradicted the squeeze order.)

**Squeeze order as the window narrows**
1. Toolbar labels drop (icons only).
2. Toolbar buttons overflow into ⋮.
3. The menus fold into "…" (Windows and Linux custom decorations only).
4. The menus collapse to ☰ (Windows and Linux custom decorations only).
5. The field shrinks around the centre to its minimum.
6. The field collapses to a magnifier icon button with the same tooltip and click action.

The two sides give way independently; the field keeps the window's centre throughout.

Widening reverses each step.

**Dragging**
- Every empty header area drags the window, and so does the field's outer margin.
- macOS keeps the `forceHitTest(false)` listener on the header background.
- Windows and Linux mark the header background as `FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION`. Double-click maximises, and right-click shows the system window menu.

**Toolbar settings**
- `window.toolbar = "hidden"` hides only the toolbar buttons. The header and the field stay, because the field is now part of the window chrome. The View › Toolbar menu keeps its three modes.
- `icons_and_labels` still applies. Labels count toward the toolbar's width, so they overflow sooner.
- `window.toolbar_height` still sizes the whole header on every platform.

**`MacTitleBar`** becomes platform glue only: traffic-light inset, native geometry, appearance and colours. It hosts a `WindowHeader` and no longer lays out its own context label and toolbar. Dialogs keep the centred title-only row they have today.

## 2. `TitleSearchField`

A lightweight `JComponent` painted by hand: a button that looks like a field. It is not a `JTextField`.

**Shape**
- A rounded rectangle with a 6-logical-pixel corner radius, the same radius as the palette card (§3).
- Its height is the header height minus 8 pixels, centred vertically, with a 1-pixel hairline border.

**Contents, left to right**
- A magnifier icon.
- The context icon.
- The context text in the regular foreground, cut with an ellipsis at the end.
- The shortcut hint (`⌘K` on macOS, `Ctrl+K` elsewhere) right-aligned in the muted colour. It is taken from the live `COMMAND_PALETTE` binding.

**Context**
- The field shows the same `WindowHeaderContext` the header label shows today: the focused pane's directory, or a provider's host or session label with its remote path.
- The full context is the tooltip.
- With no context it shows "Search".

**Interaction**
- A hover fill appears on hover, and the pressed state uses the same fill.
- A click runs `COMMAND_PALETTE`.
- The field is not focusable. Keyboard users use ⌘K.
- Accessible role: button. Accessible name: "Search, <context>".

**Colours**
- New derived keys in `ChromeKeys`: `Jasper.titleSearch.background`, `.hoverBackground`, `.border`, `.foreground` and `.hintForeground`.
- Each takes the theme's `MainToolbar.searchField.*` or `SearchEverywhere.SearchField.*` value when the theme defines one.
- Otherwise it is mixed from `Jasper.titleBackground` and the foreground: a slightly lighter fill in dark themes, slightly darker in light themes.
- The keys join `ChromeKeys.KEYS`, so theme import and the chrome tests cover them.

## 3. The palette drops from the field

**Anchored placement**
- `WindowCommandPalette.positioned()` gains an anchored mode:
  - The card's top edge is the field's top edge, so the card covers the field.
  - The card is centred horizontally on the field.
  - Its width is max(field width, 560), clamped to the window minus 16-pixel margins.
- The height rule is unchanged.
- It falls back to today's placement (centred, top at 1/5 of the height) when the window has no header, or the field is collapsed to its icon.

**Overlay**
- Today the overlay covers only `WindowContent`. It moves to cover the root pane's full bounds, so the card can overlap the header.
- On macOS the card area must not start a native drag: mouse events over the card do not call `forceHitTest(false)`.

**Outer shape (VS Code)**
- The card's corner radius matches the field.
- It has a 1-pixel `Popup.borderColor` hairline and the existing drop shadow.
- There is no gap above the card.
- It appears instantly, with no animation.
- The card is one colour throughout: the query row, the field, the list, a step form and the hint bar all use `List.background`; the field keeps its border and the hint bar its top rule (user decision, 2026-09-29).

**Card sections, top to bottom**
- The query row: an optional chip (§3.1), then the text.
- The result list, with the existing groups and "More in…" rows, the empty state and the step panel.
- The hint bar.

The tab strip is removed.

**Behaviour** that stays as it is today:
- how the palette closes (click outside, Esc, running an item);
- restoring focus to the origin pane;
- the origin guards, verbs, steps and asynchronous completion.

### 3.1 Scope chips

**Setting a chip**
- `>` typed as the first character of the query becomes the **Commands** chip, and the `>` is consumed.
- `<prefix> >` at the start of the query becomes a scope's chip, and the prefix and `>` are consumed.
  - The prefix is compared, ignoring case, with the scope's `label()`. A unique leading part matches: `hist >` gives History.
  - An unknown or ambiguous prefix stays ordinary text.
  - A space before `>` is optional (`history>` works).
- `>` anywhere else is ordinary text.
- A scope's shortcut action opens the palette with that chip. If the palette is already open, it switches the chip and keeps the query, as today.
- Clicking or pressing Enter on a mode-list row sets that row's chip.

**Removing a chip**
- Backspace in an empty query removes the chip. The palette then shows the mode list, or All results if text remains.

**What shows without a chip**
- **Empty query: the mode list.** One row per registered scope, Commands first, then scopes in registry order. Each row shows the scope's icon, its label, its prefix in the muted colour (`>`, `history >`, …) and its shortcut right-aligned. This matches VS Code's quick-open help.
- **Non-empty query:** today's **All** results, grouped per participating (`inAll`) scope and capped at `palette.max_results`, with "More in <Scope>…" rows. Choosing a "More in" row sets that scope's chip and keeps the query.

**Opening**
- ⌘K / Ctrl+K, the title field and View › Command Palette all open with **no chip**, which shows the mode list.
- ⌘K while the palette is open closes it, as today's toggle does.

**Keys**
- Tab and Shift+Tab no longer switch scopes. The palette ignores them, and focus stays in the query.
- Up, Down, Enter, Cmd/Ctrl+Enter, Shift+Enter and Esc work as today.

**Chip appearance**
- A 22-pixel rounded pill inside the query row, left of the text: the scope icon and label in the accent colour on a raised fill.
- The placeholder and typed text start after it.
- The placeholder is the scope's `placeholder()`, or "Search everything" without a chip.
- Accessible name: "Scope: <label>".

**SDK:** no change. `ScopeSpec.label()` is the prefix, and `shortcutActionId` and `inAll` keep their meanings.

## 4. Windows and Linux custom title bar

**Choosing decorations** is a pure function `WindowDecorations.forPlatform(os, nativeDecorationsSetting)`:
- **macOS:** the existing unified header. The setting is ignored.
- **Windows and Linux with `window.native_decorations = false`** (the default): FlatLaf decorations.
- **Windows and Linux with `window.native_decorations = true`:** native decorations.

**FlatLaf decorations**
- `JFrame.setDefaultLookAndFeelDecorated(true)` and `setWindowDecorationStyle(FRAME)` are applied before frames are created.
- FlatLaf 3.7 paints the title pane and minimise, maximise and close buttons. It uses its bundled native library, or JBR's custom decorations on JBR, so Windows keeps Snap Layouts, resize borders and the native shadow.
- `TITLE_BAR_SHOW_TITLE = false` on workspace frames.
- The title-pane height follows `window.toolbar_height`.
- Title-pane colours come from `Jasper.titleBackground` / `Jasper.titleForeground`.

**Embedded menu bar**
- The workspace `JMenuBar` is embedded (`TITLE_BAR_EMBEDDED_MENUBAR`).
- It holds a `CollapsingMenuBar` of the app menus, followed by the `WindowHeader`, which takes the remaining width.
- FlatLaf draws the app icon at the far left.

**`CollapsingMenuBar`: menu collapse in two stages**
1. **"…":** when the menus don't fit, trailing menus fold into a "…" menu that lists them as submenus. File always stays visible.
2. **☰:** when File plus "…" no longer fit next to the field at its minimum width, the whole menu bar becomes one ☰ button. It opens a popup with every top-level menu as a submenu (File ›, Edit ›, View ›, …).

Rules for both stages:
- The collapsed menus are the same `JMenu` objects, re-parented and never copied. Accelerators, enablement and mnemonics keep working.
- F10 opens the first showing menu; Alt mnemonics work for menus that are showing.
- The popup uses the app's rounded popup styling.

**Dialogs and auxiliary frames** (the `NativeShells` frames: Settings, the plugin manager, …) use the same FlatLaf decorations and show their title centred, with no header and no field. Auxiliary frames keep their existing File menu (New Window, Close Window, Quit), now embedded in the title pane. This mirrors how dialogs look on macOS.

**Native fallback**
- `window.native_decorations = true` keeps the native frame and `frame.setJMenuBar(...)`, as today.
- `WindowHeader` becomes its own row below the menu bar, with an empty leading slot, the centred field and the right-aligned toolbar.
- Windows and Linux reach this layout in Plan A (§6) before custom decorations exist.

**The `window.native_decorations` setting**
- Boolean, default `false`, in `CoreSettings`, the config template and the Settings Window page.
- It is restart-only, and the row is labelled like the other restart-only rows.
- It has no effect on macOS, and the Settings row is hidden there.

## 5. Testing

Tests are headless and follow the existing view and window-content tests.

- **`WindowHeaderTest`**, run at fixed widths with fixed-size fake regions:
  - the field centred on the window;
  - sliding off centre and shrinking;
  - each squeeze step in order, and reversal on widening;
  - the toolbar packed to the right;
  - ⋮ rightmost;
  - hidden toolbar mode keeping the field.
- **`WindowToolbarTest` updates:** right alignment, the ⋮ icon name, and overflow contents unchanged.
- **`TitleSearchFieldTest`:**
  - context text and tooltip;
  - "Search" fallback;
  - shortcut hint from the live binding;
  - a click runs `COMMAND_PALETTE`;
  - accessible name;
  - collapsed-icon mode.
- **`WindowCommandPaletteTest`:**
  - anchored geometry (top edge, centring, minimum width, window clamp);
  - fallback placement;
  - an overlay that covers the header.
- **Palette chip tests**, rewriting the tab tests in `CommandPaletteTest`, `PaletteKeyRouterTest` and `PaletteScopesTest`:
  - `>` and `<prefix> >` parsing, including unique-prefix, ambiguous and unknown cases;
  - `>` mid-query staying literal;
  - Backspace removing the chip;
  - the mode-list contents and order;
  - All grouping without a chip;
  - "More in…" setting the chip;
  - scope shortcuts setting or switching the chip while keeping the query;
  - ⌘K opening with no chip, and toggling;
  - Tab ignored.
- **`ChromeKeysTest`:** the new `Jasper.titleSearch.*` keys, both theme-provided and derived.
- **`CollapsingMenuBarTest`:** the fold order, File kept, the ☰ threshold, re-parenting without copies, and accelerators still dispatched after a collapse.
- **`WindowDecorationsTest`:** the choice as a function of OS and setting.
- **Settings tests:** `window.native_decorations` parsing, the default, and the restart label.

**GUI checks for the user:**
- the field and palette look on macOS, in light and dark;
- the palette dropping from the field;
- native dragging around the field and not over the open card;
- Windows: FlatLaf title bar, Snap Layouts, double-click maximise, both menu-collapse stages;
- Linux: the same, plus the `native_decorations = true` fallback.

## 6. Plans

- **Plan A:**
  - `WindowHeader`, `TitleSearchField` and its chrome keys;
  - the right-aligned toolbar with ⋮;
  - the anchored palette and scope chips;
  - on macOS, and in the native-row layout on Windows and Linux.
- **Plan B:**
  - FlatLaf decorations on Windows and Linux;
  - `CollapsingMenuBar` with "…" and ☰;
  - decorated dialogs;
  - the `window.native_decorations` setting.

The docs follow each plan:
- `docs/app-architecture.md` "Window chrome", which is also stale today;
- `docs/command-palette.md`;
- the config template;
- `docs/STATUS.md`.
