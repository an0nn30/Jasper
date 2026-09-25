# Search Everywhere command palette

**Status:** Design approved in chat by the user on 2026-09-25. Written specification awaiting user review. No plan yet. Depends on [the theme engine](2026-09-25-jasper-theme-engine-design.md) (part 2a).

## Purpose and scope

Part 2b of the appearance consolidation. Jasper's command palette is a rounded card showing one scope at a time: a scope chip, a `>` picker, and ⌘1–5 row badges. This part rebuilds it to look and behave like classic IntelliJ's **Search Everywhere**:
- a tab row with **All** plus one tab per scope;
- a full-width search field;
- one-line result rows;
- a hint bar at the bottom.

Every colour comes from the installed theme's `SearchEverywhere.*` and `List.*` keys.

Decisions the user made:
- **Tabs:** **All** first, then one tab per registered scope.
- **Shortcut:** Cmd/Ctrl+K opens **All**.
- **Grouping:** the All tab groups results into one section per scope. Each section shows at most `palette.max_results` rows, with a "More in <Scope>…" row when the scope has more matches.
- **Opt-out:** every scope takes part in All unless it opts out. The SDK's `ScopeSpec.withInAll(false)` opts out, and Vault opts out.
- **Removed:** the ⌘1–5 row badges and their row shortcuts, the `>` scope picker and scope aliases, the rounded card, the scope chip and the Esc button.
- **Colours are pulled from the theme, never hard-coded.**

Out of scope:
- a merged cross-scope ranking;
- IntelliJ's header extras (the "Include non-project items" checkbox, filter and preview buttons);
- row previews;
- any change to how a scope searches or ranks.

## 1. Layout

Top to bottom, in a square-cornered popup with a 1 px `Popup.borderColor` border and a drop shadow. It is about 680 logical px wide.

1. **Header:** background `SearchEverywhere.Header.background`, holding a row of tabs (**All**, then the registered scopes in registration order).
   - A tab is its label, with its scope icon when the scope has one.
   - The selected tab uses `SearchEverywhere.Tab.selectedBackground` and `selectedForeground`; the others use the header background and `Label.foreground`.
   - Clicking a tab selects it.
2. **Search field:** a full-width text field with a leading magnifier and the selected scope's placeholder. All's placeholder is "Search everywhere".
   - Colours are `SearchEverywhere.SearchField.background`, `borderColor` and `infoForeground`, with the placeholder in `infoForeground`.
   - It shows the theme's focus ring (`Component.focusColor`).
3. **Results:** one line per row, containing:
   - a 16 px icon;
   - the title in `List.foreground`, plus the row's detail in `SearchEverywhere.SearchField.infoForeground`, on the same line after the title;
   - the row's tag, right-aligned in `infoForeground`.

   Title and detail are cut with an ellipsis, and the tag is dropped first when space runs out. Selected and hovered rows use `List.selectionBackground`/`selectionForeground` and `List.hoverBackground`.
   - **In All:** each participating scope with matches gets a section header, the scope label in `SearchEverywhere.List.separatorForeground` over a `SearchEverywhere.List.separatorColor` rule. Up to `palette.max_results` rows follow. When the scope reports more matches than shown, the section ends in a selectable "More in <Scope>…" row, and choosing it selects that scope's tab with the query kept.
   - **In a scope tab:** that scope's rows as today, up to its limit. Empty and step states are as today, drawn in the same colours.
4. **Hint bar:** background `SearchEverywhere.Advertiser.background`, text `SearchEverywhere.Advertiser.foreground`.
   - The left side shows the selected row's detail (its shortcut, snippet text or host address).
   - The right side shows the row's scope's secondary verbs as link-coloured (`Link.activeForeground`) clickable text with their keys, for example "Paste and run ⌘⏎   Save as snippet ⇧⏎".
   - It is hidden in a step form.

No colour, border or background in the palette may come from a literal colour or a `Jasper.palette*` key. Those keys are removed.

## 2. Keyboard and behaviour

- **Opening:**
  - **Cmd/Ctrl+K**, the rebindable `command_palette`, opens the palette on **All**.
  - A scope's own shortcut (`ScopeSpec.shortcutActionId`) opens it on that scope's tab.
  - Pressing the shortcut of the tab already showing closes the palette.
- **Tabs:**
  - **Tab / Shift+Tab** select the next or previous tab, cycling. Inside a multi-field step form they move between fields instead, as today.
  - Switching tabs keeps the query and re-runs the search.
- **Verbs:** **Enter, Cmd/Ctrl+Enter and Shift+Enter** run the selected row's first, second and third verbs. On a "More in…" row, Enter selects that tab.
- **Navigation:** **Up/Down** move the selection across section boundaries; section headers are skipped. **Esc** leaves a step, then closes.
- **Removed:** the `>` picker, scope aliases and ⌘1–5 row shortcuts. Scope shortcuts, verbs, steps, asynchronous completion and focus restoration are unchanged.
- **Placement:** the palette is centred horizontally on the terminal deck with its top fixed, so the search field doesn't move as results change, as today.

## 3. The All tab

- **What it searches:** All queries every participating scope with the same text and `PaletteQuery` as a scope tab would, capped at `palette.max_results` per scope. Sections appear in tab order. A scope with no matches has no section.
- **Empty query:** All shows each participating scope's empty-query rows under its section header (Commands' recents and starters, History's recent lines, and so on), capped the same way.
- **Participation:**
  - `ScopeSpec` gains `withInAll(boolean)`, which defaults to `true`. It becomes part of SDK 0.8.0, which isn't released.
  - The app's `PaletteScope` carries the flag, and Commands participates.
  - Vault calls `withInAll(false)`.
  - The testkit's fake palette honours the flag.
- **Failure isolation:** a scope that throws or is unavailable is skipped in All, with the same containment as a scope tab. It never blocks the other sections.

## 4. Testing

- **Layout:** the header, field, results and hint bar are in order; the tabs are All plus every registered scope in registration order; the width is about 680.
- **Theme colours:** under IntelliJ Light and Jasper Dark, every palette surface's colour equals its `SearchEverywhere.*` or `List.*` source key. This covers the header, selected tab, field, separator, selection, advertiser and link.
- **Behaviour:**
  - Cmd/Ctrl+K opens All, and each scope shortcut opens its tab. The same shortcut closes; the other shortcut switches.
  - Tab and Shift+Tab cycle through the tabs and keep the query.
  - All groups results by scope, honours the per-scope cap and shows "More in…" only when needed; choosing it selects the tab.
  - The Vault opt-out holds, and a failing scope is skipped.
  - Up and Down skip headers.
  - The verbs run and the hint bar shows the secondary verbs.
- **Removed features stay removed:** no `>` picker, badges or ⌘1–5 behaviour.
- **Rewritten tests:** existing palette tests that pinned the old geometry, colours, badges or the picker are rewritten (`CommandPaletteTest`, `WindowCommandPaletteTest`, `PaletteScopesTest`, `PaletteKeyRouterTest`, `CommandPaletteShortcutsTest`), plus the plugin scope tests where they asserted picker aliases.
- **Docs:** `docs/command-palette.md`, `docs/keyboard-shortcuts.md` and the SDK palette docs are updated.
- `./gradlew check` passes. The user checks the palette visually in light and dark.
