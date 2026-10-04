# Jasper Design System — Specification v1

**Audience:** the engineer or agent who will plan and build the Jasper UI Kit on Java Swing + FlatLaf.
**Scope:** visual and behavioral rules for every control, panel, dialog and window in Jasper, plus the contract plugins must follow so they never paint their own controls.
**Source of truth:** the reference mocks in this project (`Jasper Panels`, `Add Host Dialog`, `Unlock Vault Dialog`, `Create Vault Dialog`, `New File Server Dialog`, `Credential Vault`). When this document and a mock disagree, this document wins.

This spec is implementation-agnostic. It names **semantic tokens** and **components**. The implementer maps tokens onto the theme engine (FlatLaf properties / UIManager keys or Jasper's own theme layer) and builds components however best fits the codebase.

---

## 1. Principles

1. **One way to do each thing.** Every control has exactly one canonical component. No plugin or form builds its own variant.
2. **Tokens, never literals.** No color, font, radius or spacing value is written inline in feature or plugin code. Everything resolves through the theme engine by semantic key, so theme switches (dark/light/custom) apply everywhere live.
3. **Quiet by default, one loud thing.** Each surface has at most one accent-colored primary action. Everything else is secondary, ghost or in an overflow menu.
4. **Structure over decoration.** Hierarchy comes from grouping (cards, section labels), spacing and type weight. No ad-hoc borders, separators or background tints.
5. **Show only what applies.** Fields that don't apply to the current choice are hidden (or disabled if their presence teaches something). No permanently visible fields with "(X only)" notes.
6. **Native feel.** Follow macOS-style conventions for dialogs and windows (primary button right-most, Enter = primary, Esc = cancel, sheets attached to the title bar) while remaining cross-platform.

---

## 2. Token architecture

Three layers. Only layer 3 is visible to feature and plugin code.

| Layer | What it is | Who touches it |
|---|---|---|
| 1. Theme palette | Raw colors defined by a theme file | Theme authors only |
| 2. Semantic tokens | Role-based keys (below) that resolve to palette values | Design system core |
| 3. Components | Kit components that read semantic tokens | Everyone |

Rules:
- Every key in §3 must be defined by every shipped theme. A missing key falls back to a defined parent key (e.g. `button.secondary.bg.hover` → derived from `button.secondary.bg`), never to a hardcoded value.
- Alpha-derived values (hover overlays, tinted tiles, focus rings) are expressed as **token + alpha** (e.g. `accent` at 25%), computed by the kit, so they track the theme.
- Components must re-resolve tokens on theme change events. Nothing caches a resolved `Color`.

---

## 3. Semantic color tokens

Naming: `group.role[.state]`. Reference values for the default dark theme are in Appendix A for calibration only.

### 3.1 Surfaces
| Key | Role |
|---|---|
| `surface.app` | Window chrome background (title bar, gutters between panels, status bar) |
| `surface.panel` | Tool window / panel body |
| `surface.panel.raised` | Footer areas inside a panel (e.g. session detail card) |
| `surface.dialog` | Dialog and sheet body |
| `surface.card` | Grouped-form card inside a dialog |
| `surface.sidebar` | Window sidebar (e.g. vault categories) and window footers |
| `surface.input` | Inset field background (text fields, search, lists inside cards) |
| `surface.control` | Filled control background (dropdowns, pickers) |
| `surface.control.hover` | |
| `surface.menu` | Popup menu / popover background |
| `surface.terminal` | Terminal content area |
| `surface.scrim` | Backdrop behind sheets (applied with alpha ~55%) |

### 3.2 Borders & dividers
| Key | Role |
|---|---|
| `border.window` | 1px outer edge of windows/dialogs |
| `border.subtle` | Panel and card edges, internal dividers (very low contrast) |
| `border.input` | Text field border at rest |
| `border.control` | Dropdown / secondary button border |
| `border.menu` | Popup edge and menu separator |
| `border.focus` | = `accent`; focused field border |
| `border.error` | Invalid field border |

### 3.3 Text
| Key | Role |
|---|---|
| `text.primary` | Titles, values, body |
| `text.secondary` | Form labels, descriptions |
| `text.tertiary` | Captions, counts, section labels, meta lines |
| `text.placeholder` | Hint text inside inputs; disabled-looking empty values |
| `text.onAccent` | Text on accent fills (primary button, selected row) |
| `text.onAccent.secondary` | Secondary text on selected rows |
| `text.link` / `text.link.hover` | Inline links and link-style buttons |
| `text.error` | Inline validation messages |
| `text.success` | Confirmations ("Config loaded", "Connected") |

### 3.4 Accent & state
| Key | Role |
|---|---|
| `accent` | Primary action fill, toggle-on track, focus ring, selection indicator |
| `accent.hover` | Primary button hover |
| `selection.bg` | Selected list row background (accent-tinted, not raw accent) |
| `hover.overlay` | Neutral overlay for hover on transparent elements (use at 5–8%) |
| `segment.selected.bg` | Selected segment in a segmented control |
| `toggle.track.off` | Toggle track when off |
| `toggle.knob` | Toggle knob |

### 3.5 Status
| Key | Role |
|---|---|
| `status.success` | Connected / running dots, match checkmarks |
| `status.warning` | Warning glyphs ("cannot be recovered") |
| `status.danger` | Errors, destructive hover tint |
| `status.idle` | Disconnected / stopped dots |

### 3.6 Category hues
Used only for type identity (icon tiles, protocol chips, sidebar category tiles). Each hue has a **foreground** key; its tile/chip background is the same hue at ~16% alpha.

| Key | Used for (current assignments) |
|---|---|
| `hue.blue` | Login items, HTTP, sessions/servers |
| `hue.green` | SSH key items, SFTP |
| `hue.amber` | Secure notes, FTP, favorites star |
| `hue.purple` | Generate actions, RSYNC |
| `hue.neutral` | "All" / uncategorized |

New categories must pick from this set; adding a hue is a design-system change, not a plugin decision.

---

## 4. Typography

Single UI family: the platform system UI font (SF Pro on macOS, Segoe UI Variable on Windows, Inter/Cantarell-class fallback on Linux — resolved by the theme engine). One monospace family for technical values (hostnames, paths, ports, fingerprints, commands, terminal).

Sizes are logical px at 1× scale.

| Token | Size | Weight | Use |
|---|---|---|---|
| `type.heroTitle` | 15 | Semibold | Dialog hero title ("Unlock Vault"), sheet titles |
| `type.detailTitle` | 16 | Semibold | Detail pane item name |
| `type.windowTitle` | 13 | Semibold | Title bar text (in `text.secondary`) |
| `type.panelTitle` | 13 | Semibold | Tool window header title |
| `type.body` | 13 | Regular | Default text, list row names, input text, menu items |
| `type.bodyStrong` | 13 | Medium | Selected row name, item titles in two-line rows |
| `type.label` | 12.5 | Regular | Form labels (`text.secondary`), button labels |
| `type.description` | 12–12.5 | Regular | Hero subtitles, empty-state copy (`text.secondary`) |
| `type.caption` | 11–11.5 | Regular | Row subtitles, setting descriptions, hints, footers (`text.tertiary`) |
| `type.sectionLabel` | 11 | Semibold | Section headings above cards, +0.02em tracking (`text.tertiary`) |
| `type.count` | 11 | Regular, tabular figures | Counts beside titles |
| `type.chip` | 9.5 | Semibold, +0.04em tracking, uppercase | Protocol/type chips |
| `type.mono` | 12 | Regular | Values in inputs and detail rows |
| `type.monoSmall` | 11–11.5 | Regular | Secondary technical text (row routes, addresses, command preview) |

Rules:
- Numbers that update or align (counts, ports, sizes, dimensions) use tabular figures.
- Never use bold (700) in UI chrome. Semibold (600) is the heaviest weight.
- Never underline links; color carries the affordance.

---

## 5. Spacing, sizing, radius

### 5.1 Spacing scale
`2 · 4 · 6 · 8 · 10 · 12 · 14 · 16 · 18 · 20 · 24 · 28` (px). Components use only these.

Common assignments:
- Gap between panels / gutters: **6**
- Panel inner horizontal padding: **10–12** (list rows sit at 6 from panel edge, with 8–9 internal padding)
- Card inner padding: **14**
- Gap between rows in a card: **10**
- Gap between label column and control: **12**
- Gap between cards (with their section label): **20** (Add Host density) / **14** (sheets)
- Section label → card: **8**
- Dialog horizontal padding: **24** (forms) / **28** (hero dialogs)
- Button group gap: **8**; icon-button cluster gap: **2–6**

### 5.2 Control heights
| Token | Height | Use |
|---|---|---|
| `size.xs` | 22 | Inline row actions (reorder/remove in key lists) |
| `size.sm` | 24 | Panel header icon buttons, segmented items (inside a 28 track) |
| `size.md` | 28 | **Default:** text fields, dropdowns, form buttons, search fields, toolbar buttons |
| `size.lg` | 32 | Single-purpose hero dialogs (password fields in Unlock/Create Vault); hero buttons 30 |

Rows:
| Row | Height |
|---|---|
| Single-line list row (sessions, files) | 30 (files may be 26) |
| Two-line list row (vault items) | 44 |
| Two-line compact row (tunnels, file servers) | ~40 (7 vertical padding) |
| Menu item | 28 |
| Settings row (label only) | 42 |
| Settings row (label + description) | min 48 |
| Detail field row (label over value) | min 50 |
| Panel header | 38 |
| Panel footer (persistent toggle) | 34 |
| Window title bar | 44–46 (dialogs 34–40) |
| Window footer / status bar | 44 (window footer), 26–28 (app status bar) |

### 5.3 Radius
| Token | Value | Use |
|---|---|---|
| `radius.window` | 12 | Windows, dialogs, sheets (sheet: bottom corners only) |
| `radius.card` | 9 | Panels, form cards, menus |
| `radius.control` | 6 | Buttons, text fields, dropdowns, list rows (6–7) |
| `radius.segmentTrack` | 7 | Segmented control outer track; inset lists inside cards |
| `radius.segment` | 5 | Segment items, menu item highlight, icon-button hover |
| `radius.chip` | 4 | Type chips |
| `radius.tile.sm` | 5 | 18–20px category tiles (menus, sidebar) |
| `radius.tile.md` | 7–8 | 28–32px tiles (list rows, sheet headers) |
| `radius.tile.lg` | 10–11 | 40–44px tiles (empty states, detail header) |
| `radius.tile.xl` | 13 | 52px hero tiles |
| `radius.pill` | full | Toggles, status dots |

### 5.4 Elevation
Only three levels; no other shadows anywhere.
- `elevation.window` — large soft shadow for top-level windows/dialogs.
- `elevation.popover` — menus/popovers (medium blur, ~50% black).
- `elevation.button.primary` — 1px top inner highlight (white ~14%) + 1px drop (black ~30%) on primary buttons only.

---

## 6. Iconography

- **Style:** outline, single weight, rounded joins; drawn on a 16×16 grid.
- **Stroke:** 1.4–1.6 at 16px; scale stroke with size so it reads ~1.5px optical at every size.
- **Sizes:** 10 (chevrons), 11–12 (inline/caption), 13–14 (buttons, rows), 16 (rail, sidebar), 20 (empty-state tiles), 24 (hero tiles).
- **Color:** icons are monochrome and **tinted by the kit** with `text.secondary` at rest, `text.primary` on hover, `text.onAccent` on accent fills, or a `hue.*` inside category tiles. Icon assets never carry their own color (except filled folder glyphs, which use `hue.blue`).
- **Registry:** all icons come from a central Jasper icon set keyed by name (`add`, `more`, `collapse`, `search`, `lock`, `edit`, `delete`, `copy`, `reveal`, `refresh`, `upload`, `back`, `forward`, `folder`, `file`, `server`, `key`, `login`, `note`, `tunnel`, `terminal`, `play`, `warning`, `info`, `check`, `close`, `chevron.down/up`, `sparkle` …). Plugins request by name or register new icons that conform to this section.
- **Category tile:** rounded square, `hue.X` at ~16% fill, icon in `hue.X`. On a selected row, tile becomes `text.onAccent` at ~18% and icon becomes `text.onAccent`.

---

## 7. Interaction states

| State | Treatment |
|---|---|
| Hover (transparent elements: icon buttons, rows, menu items) | `hover.overlay` at 5% (rows) – 8% (buttons); icon/text → `text.primary` |
| Hover (filled elements) | Swap to the `.hover` token of that element |
| Pressed | One step darker than hover (kit-derived) |
| Selected row | `selection.bg`; title `text.onAccent` medium; subtitle `text.onAccent.secondary` |
| Menu item hover/keyboard focus | Full `accent` fill, `text.onAccent` |
| Keyboard focus (fields) | Border → `border.focus`; outer ring `accent` at 25%, 3px, following the control radius |
| Keyboard focus (buttons/toggles/segments) | Same 3px ring, no border change |
| Disabled | Whole control at 40–45% opacity; no hover; cursor default |
| Error | Border → `border.error`; ring `status.danger` at ~22%; message below in `text.error`, `type.caption`; optional 350ms horizontal shake on submit failure (hero dialogs only) |
| Success (inline) | Trailing `check` icon in `status.success`; border may tint `status.success` at ~50% |

Focus is visible only for keyboard navigation where the platform supports that distinction.

---

## 8. Components

Each component lists anatomy, sizes, tokens and rules. "Kit" = the Jasper UI Kit component library.

### 8.1 Button
| Variant | Fill | Border | Text | Use |
|---|---|---|---|---|
| **Primary** | `accent` (+ `elevation.button.primary`) | none | `text.onAccent`, medium | The single commit action of a surface: Save, Connect, Add Host, Unlock, Done |
| **Secondary** | `button.secondary.bg` (≈ `surface.control`) | 1px `border.control` | `text.primary` at ~90% | Cancel, Browse…, Choose…, Edit, empty-state secondary |
| **Ghost** | transparent → `hover.overlay` | none | `text.secondary` → `text.primary` | Toolbar and header actions |
| **Destructive ghost** | transparent → `status.danger` at ~15% | none | `text.secondary` → `status.danger` | Delete/remove icon buttons |
| **Link** | none → `accent` at ~8% on hover | none | `text.link` | In-list "Add Stored Key…" |

Sizes: height `size.md` (28) default; `size.lg` 30 in hero dialogs. Horizontal padding 14 (primary 16). With leading icon: icon 10–12, gap 6, left padding 10. Radius `radius.control`. Label `type.label`, never wraps (no-wrap, min width = content).

Rules:
- One primary per surface. A primary is disabled (40–45% opacity) until its form is valid — never hidden.
- Order: **[Secondary…] [Primary]**, primary right-most. Destructive actions never sit next to the primary.
- Labels: Title Case verbs. Append "…" when the action opens another dialog or picker.
- Hero dialogs (single-field) use **equal-width** buttons filling the row.

### 8.2 Icon button
- Square: 24 (panel headers, rows) / 28 (title bar, toolbars, field adornments) / 22 (inline row actions).
- Icon 12–14. Radius 5–6. Ghost or destructive-ghost treatment only.
- **Must** have a tooltip (Title Case, e.g. "New Session", "Copy Link").
- A "toggle-ish" icon button (e.g. title bar + menu) shows `surface.control` fill when its menu is open.
- Split/menu button: icon + 9px chevron, padding 8–9.

### 8.3 Text field
- Height 28 (32 hero). Padding 9–10 horizontal. Radius 6.
- Fill `surface.input`, 1px `border.input`. Text `type.body` (or `type.mono` 12 for technical values).
- **Hint text** (placeholder) in `text.placeholder`. Hints show an example or "Optional", never instructions. Examples: "e.g. homelab", "hostname or IP address", "~/.ssh/id_ed25519", "Optional".
- Focus/error per §7.
- **Adornments** (inside the field, part of the component):
  - Leading icon (search, lock): 13px, `text.placeholder`, gap 7.
  - Leading prefix glyph (port ":"): `text.placeholder`, 8 left padding, 3 gap.
  - Trailing icon button (reveal password): 28×28 ghost, 2px inset.
  - Trailing status (match check): 14px.
- Variants the kit must provide: `TextField`, `PasswordField` (reveal toggle, shared reveal state across paired fields), `SearchField`, `PortField` (prefix ":", numeric, ~72–84 wide), `PathField` (mono, pairs with a Secondary "Browse…" button), `TextArea` (same styling, padding 9, line-height 1.5, no resize handle).

### 8.4 Dropdown (select / combo box)
- Height 28. Fill `surface.control`, 1px `border.control`, radius 6.
- Value in `type.body`; trailing 10px chevron-down in `text.secondary`, 10 from right edge. **No separate arrow button or divider.**
- Popup uses the Menu component (§8.10).
- Use when options > 4, options are dynamic (hosts, groups), or space is tight. Append a "New X…" item at the end where creation makes sense.
- **Picker** variant (credential/account chooser): same chrome, leading icon, value or "Choose…" in `text.tertiary` when empty. Opens a picker, not a plain list.

### 8.5 Segmented control
Replaces radio groups of 2–4 mutually exclusive options.
- Track: `surface.input` fill, 1px `border.subtle`-strength border, radius 7, 2px inner padding, full available width.
- Segments: equal width, height 24, radius 5, `type.label` medium (or `type.chip`-like for protocol codes).
- Selected: `segment.selected.bg`, `text.onAccent`, small drop shadow. Unselected: transparent, `text.secondary`.
- Optional leading 6px dot in a `hue.*` for type-coded options (protocols).

### 8.6 Toggle switch
Replaces checkboxes for settings.
- Form size: 32×18 track, 14 knob, 2 inset. Compact (panel footers, list rows): 28×16, 12 knob.
- On: `accent` track, knob right. Off: `toggle.track.off`, knob left. Knob `toggle.knob` with tiny drop shadow. Animate 120–150ms.
- Always trailing in a **Settings row** (§8.13), never preceded by a label on its left in the same cell.
- Dependent toggle (e.g. Require TLS under Use TLS): indented +16, disabled at 40% until parent is on; turning parent off resets it.

### 8.7 Checkbox and radio (restricted)
- **Radio buttons are not used.** Use Segmented control (≤4) or Dropdown (>4).
- **Checkboxes** only for multi-select inside lists/tables (e.g. selecting files). Not for settings.

### 8.8 Chip / badge
- Height 16, padding 0 5, radius 4. `type.chip`. Fill `hue.X` at ~16%, text `hue.X`.
- Use for type identity only (HTTP / SFTP / FTP / RSYNC; key algorithm). Never for counts or status.

### 8.9 Status dot and count
- Status dot: 6px (7 in session list), circle. `status.success` (live), `status.idle` (off). On selected rows, idle dot = `text.onAccent` at 45%.
- Count: `type.count`, `text.tertiary`, 6 after a title. Hidden when the list is empty.

### 8.10 Menu / popover
- Fill `surface.menu`, 1px `border.menu`, radius 9, padding 5, `elevation.popover`. Width fits content, min ~200.
- Item: height 28, padding 0 9, radius 5, `type.body`. Optional leading 18px category tile (radius 5, icon 11). Hover = full `accent`, `text.onAccent`.
- Separator: 1px `border.menu`, 4px vertical margin, 4px inset.
- Opens below the anchor, right-aligned to it when the anchor is in the right half. Clicking outside or Esc closes.

### 8.11 Tooltip
Theme-driven; `type.caption`; appears after the platform delay. Required on every icon-only control.

### 8.12 Card and section label (grouped form)
- Section label: `type.sectionLabel` above the card, 8 gap. Title Case nouns ("Connection", "Authentication", "Options", "Security & Startup").
- Card: `surface.card`, 1px `border.subtle`, radius 9.
  - **Form card:** padding 14, rows gap 10.
  - **Settings card:** no padding; stacked Settings rows separated by 1px `border.subtle` dividers.
  - A card may mix both: form rows on top, a divider, then settings rows.

### 8.13 Form row and settings row
**Form row** (label left, control right):
- Grid: label column **96px** fixed, then control `1fr`, optional trailing column (auto, for Browse…/Port). Column gap 12. Vertically centered.
- Label: `type.label`, `text.secondary`, sentence case, **no trailing colon**, no "(optional)" — put "Optional" in the hint instead.
- Multi-line label (label + caption under it, e.g. "Keys / Tried in order"): top-aligned with a 6px top offset.
- Helper text below a control: placed in the control column (empty label cell), `type.caption`.
- Combine related fields on one row: Host + Port; Reachable From + Port; Path + Browse….

**Settings row** (label left, control trailing):
- Padding 0/8 × 14; min height 42 (single) / 48 (with description).
- Title `type.label` in `text.primary`-ish; description `type.caption`, `text.tertiary`.
- Trailing control: Toggle, compact Dropdown (~160 wide), or a Secondary button.
- Recommendation tag: inline after title in `text.tertiary`: "Bind to this device · Recommended". Never "(recommended)" in parentheses.

### 8.14 Lists
- Rows inset 6 from the container edge, radius 6–7, gap 1 between rows.
- Single-line row (30): [status dot / icon] 9 gap [name `type.body`] [secondary text `type.caption` `text.tertiary`, truncates] [trailing marker (favorite star 11px `hue.amber`)].
- Two-line row (44): [28 category tile] 10 gap [title `type.bodyStrong` / subtitle `type.caption`].
- Compact two-line row (tunnels/servers): title row with status dot + name + "via host" caption; second line mono route; trailing toggle and/or icon actions.
- Column header (when columns exist): height 24, `type.caption` medium, `text.tertiary`, 1px dividers top/bottom, left-aligned names, right-aligned numeric columns.
- **Ordered list** (inside a form): inset container (`surface.input`, radius 7), rows 34 with 1px dividers, leading index number (`text.tertiary`, tabular), trailing xs icon buttons (Move Up / Move Down / Remove — first/last disable the impossible direction at 30%), final row is a Link button "Add X…". Empty: "No X added" in `text.placeholder`.
- Truncate with ellipsis; paths truncate from the start (keep the filename visible).

### 8.15 Empty state
Centered vertically and horizontally in the available area, max text width ~300.
- Icon tile 40 (panels) or 52 (windows): `surface.control`-tone tile (or category tint), icon `text.tertiary`/hue, 6–8 below.
- Title: `type.panelTitle` (panels) / `type.heroTitle` (windows), Title Case, "No Tunnels", "No Credentials Yet", "Not Connected".
- Description: one sentence, `type.description`, `text.secondary`, balanced wrapping.
- Actions: one Secondary button (panels) or Secondary + Primary (windows), 8–12 below.
- Never show search/filter fields or column headers when the list is empty.

### 8.16 Hero header (single-purpose dialogs)
For dialogs with 1–3 inputs (Unlock Vault, Create Vault):
- Centered: 52 tile (radius 13, `surface.control`-tone, icon 24 in accent-tint) → 14 → title `type.heroTitle` → 4 → subtitle `type.description`.
- Success state swaps tile to `status.success` tint and changes title/subtitle; buttons collapse to a single "Done" (primary) or a single follow-up.
- Warnings sit as a separate line with a 13px `warning` icon in `status.warning`, `type.caption`, above the buttons.

### 8.17 Strength meter
4 equal segments, 3px tall, 3 gap, radius 2. Empty segments `surface.control`. Filled: 1 = `status.danger`, 2 = `status.warning`, 3 = `accent`, 4 = `status.success`. Label right-aligned (`type.caption`, matching color): Weak / Fair / Good / Strong.

---

## 9. Containers

### 9.1 Window chrome
- Title bar 44–46, `surface.app`. Title centered, `type.windowTitle`, `text.secondary`.
- Window-level toolbar actions live right-aligned in the title bar (28 icon buttons; primary-ish "+ ▾" split button for add menus).
- Window footer (manager windows like Credential Vault): 44 tall, `surface.sidebar`, top divider. Left: meta items (12px icon + `type.caption`, 14 gap). Right: **one** button — "Done" (primary). Manager windows don't use Cancel/OK.

### 9.2 Dialog
- Width tiers: **380–400** (hero), **480** (sheet forms), **540** (full forms). Height fits content; scroll body if > 85% of screen.
- Chrome: 34–40 title bar; dialogs show close only (other traffic lights inert/dim).
- Body: section labels + cards, 20 gap, 24 side padding.
- Footer: 18–20 vertical padding, 24 side. Left (optional): live preview in `type.monoSmall`, `text.tertiary` (e.g. `ssh ubuntu@10.0.0.4 -p 2222`, `http://127.0.0.1:8080`). Right: Cancel, Primary.
- Keyboard: Enter triggers primary when valid; Esc cancels; first field autofocused.
- Title: Title Case verb phrase — "Add Host", "New File Server", "Generate SSH Key".

### 9.3 Sheet
A dialog attached to a parent window, used for create/edit flows that belong to that window (vault items).
- Drops from beneath the parent's title bar, centered horizontally, `radius.window` on bottom corners only, no own title bar.
- Parent content dimmed by `surface.scrim`.
- Header: 32 category tile + `type.heroTitle`, 12 gap, padding 20/24/14.

### 9.4 Tool window (panel)
The canonical container for plugin UIs in the left/right docks.
```
┌──────────────────────────────────────┐
│ Title  count            [+] [⋯] [⌄] │  header 38
│ ┌ Search…                          ┐ │  optional, 28, only when items exist
│ │ row                              │ │
│ │ row (selected)                   │ │  body: list / empty state / browser
│ │ row                              │ │
│ ├──────────────────────────────────┤ │
│ │ detail or persistent control     │ │  optional footer (34 or detail card)
└──────────────────────────────────────┘
```
- Container: `surface.panel`, 1px `border.subtle`, radius 9; 6 gap between stacked panels.
- **Header** (38): padding 0 6 0 12. Title `type.panelTitle`; optional count; optional context (e.g. status dot + host for SFTP). Right: at most **one primary action** (usually `add`), then **More** (`more`, overflow menu), then **Collapse** (`chevron.down`). All 24px ghost icon buttons with tooltips.
- **No toolbar row under the header.** Secondary actions (Import…, Refresh All, Settings…) go in the overflow menu. Contextual actions (Delete, Download, Copy Path) go in the row context menu and/or the overflow menu, enabled by selection.
- **Exception — browsers:** a navigation bar is allowed for browser-type panels (SFTP): back/forward segmented pair, path breadcrumb field, then ≤2 icon actions (e.g. Refresh, Upload). Height 26, below header, 8 side padding.
- **Body:** list (§8.14), empty state (§8.15) or browser.
- **Footer:** either a persistent Settings row (34, compact toggle, top divider) or a **detail card** for the selection (`surface.panel.raised`, top divider, padding 12: 30 tile + name/mono subtitle; meta line + actions right — ghost icon for Edit, Primary for the main action such as Connect / Open Shell).

### 9.5 Activity rail
36 wide, icon buttons 32×32 radius 7, icon 16. Active = `surface.control` fill + `text.primary`; inactive ghost. Separator 20×1. Tooltips required.

### 9.6 Status bar
26–28 tall, `surface.app`, `type.caption` `text.tertiary`, 14 gap; items with 11–12 icons; success messages in `text.success`.

---

## 10. Content & copy rules

- **Title Case:** window/dialog titles, button labels, menu items, panel titles, section labels, empty-state titles, tooltips.
- **Sentence case:** form labels, descriptions, hints, helper text, settings titles.
- No trailing colons on labels. No "(optional)" / "(recommended)" in labels.
- Ellipsis "…" (single glyph) for actions that open further UI.
- Empty states state what's missing and the one next step. No em-dash run-ons ("No tunnels yet — add one with +, or use…" → title "No Tunnels" + one-sentence description + button).
- Technical values (hosts, ports, paths, keys, commands) always in mono.
- Use the product's nouns consistently: Session, Host, Tunnel, File Server, Vault, Login, SSH Key, Secure Note.

---

## 11. Plugin integration contract

Plugins (Sessions, SFTP, Tunnels, File Servers, Vault and third-party) **describe** UI; the host **renders** it with the kit.

### 11.1 What the host provides through the plugin context
1. **Theme access** — read-only semantic token lookup and theme-change subscription. Plugins never receive raw palette colors.
2. **UI Kit factory** — every component in §8 (buttons, fields, dropdowns, segmented, toggles, chips, lists, menus, empty states, cards, rows, hero headers). Plugins construct controls only through this factory.
3. **Tool window scaffold** — a declarative API where a plugin supplies:
   - title, optional count provider, optional header context (status + label)
   - one optional primary header action (icon, tooltip, handler)
   - overflow actions (label, optional shortcut, enabled predicate)
   - body: a list model + row renderer *descriptor* (which standard row type, which fields), or an empty-state descriptor (icon, title, description, action), or a kit browser component
   - optional footer: a settings row or a detail descriptor (title, subtitle, meta, edit action, primary action)
   - context-menu actions per row
   The host builds header, spacing, borders, collapse behavior, search field visibility and empty-state switching.
4. **Dialog/sheet builder** — plugin declares: title, size tier (hero/sheet/form), optional hero (icon, title, subtitle), ordered sections (label + rows), each row being a typed field (text, password, port, path+browse, dropdown, picker, segmented, toggle setting, ordered list, textarea, helper text), conditional visibility rules (`visibleWhen`, `enabledWhen`), validation rules, primary label, optional footer preview provider. The host renders layout, focus order, Enter/Esc, disabled-until-valid, error display.
5. **Icon registry** — lookup by name (§6) and registration of plugin icons that conform to §6 (monochrome, 16-grid, tinted by host).
6. **Category hue assignment** — plugins request a `hue.*` for item types; they cannot define new colors.
7. **Notifications/status** — status bar messages and toasts via host API, not custom labels.

### 11.2 What plugins must not do
- Override `paint`/`paintComponent` or install custom UI delegates/borders on any control.
- Instantiate raw Swing controls for visible UI (buttons, fields, combos, checkboxes, radios, labels with custom styling) outside the kit factory.
- Use color, font, size, inset or radius literals; set opaque backgrounds; add their own separators/borders.
- Add toolbar rows to tool windows, more than one primary header action, or more than one primary button per dialog.
- Use radio buttons, or checkboxes for settings.
- Show placeholder text like "Nothing to show" — use the empty-state descriptor.
- Cache resolved theme values.

### 11.3 Escape hatch
If a plugin needs a control the kit lacks, it requests a kit addition. Until then it may compose existing kit components only. Truly custom content areas (terminal canvas, charts, previews) are allowed **inside** a kit container and must take colors from tokens.

### 11.4 Enforcement
- **Dev mode check:** the host walks plugin component trees and logs a warning for non-kit components, non-token colors, or non-scale insets.
- **Review checklist:** §12 is the acceptance list for any plugin or form PR.
- **Live theme test:** every plugin surface must render correctly after a runtime theme switch without restart.

---

## 12. Acceptance checklist (per surface)

- [ ] Only kit components; no custom painting; no literals.
- [ ] One primary action; correct button order; primary disabled until valid.
- [ ] Panel header has ≤1 primary icon action + More + Collapse; no extra toolbar row (browser nav bar excepted).
- [ ] Forms use section labels + cards, 96px label column, no colons, hints instead of "(optional)".
- [ ] No radio buttons; settings are toggles in settings rows; ≤4 exclusive options use segmented control.
- [ ] Fields that don't apply are hidden or dependent-disabled.
- [ ] Empty states use the standard component; search hidden when empty.
- [ ] Every icon-only control has a tooltip.
- [ ] Technical values in mono; counts/ports tabular.
- [ ] Keyboard: autofocus first field, Enter = primary, Esc = cancel, visible focus ring.
- [ ] Renders correctly in every shipped theme and after a live theme switch.

---

## 13. Build order (suggested)

1. Token layer: define all §3 keys in the default dark theme (Appendix A) and a light theme; derived-alpha helpers; theme-change propagation.
2. Primitives: Button (all variants), Icon button, Text field family, Dropdown/Picker, Segmented, Toggle, Chip, Status dot, Tooltip, Menu.
3. Layout: Card, Section label, Form row, Settings row, Hero header, Empty state, Lists (all row types incl. ordered list).
4. Containers: Dialog, Sheet, Tool window scaffold, Window footer, Activity rail, Status bar.
5. Plugin API: kit factory, tool window scaffold, dialog builder, icon registry, dev-mode checks.
6. Migrate surfaces in this order: Unlock/Create Vault (smallest) → Add Host → New File Server → Credential Vault + sheets → Sessions/SFTP/Tunnels/File Servers panels.

---

## Appendix A — Default dark theme reference values

For calibrating the first theme only. These belong in the theme file, never in code.

| Token | Value |
|---|---|
| `surface.app` | #252932 |
| `surface.panel` | #1C1F26 |
| `surface.panel.raised` | #1F232B |
| `surface.dialog` | #1F232B |
| `surface.card` | #1A1D23 |
| `surface.sidebar` | #1B1E25 |
| `surface.input` | #14161B |
| `surface.control` | #2A2F38 |
| `surface.control.hover` | #30353F |
| `button.secondary.bg` / `.hover` | #2D323C / #353B46 |
| `surface.menu` | #2A2F38 |
| `surface.terminal` | #363A45 |
| `surface.scrim` | #0A0C10 @ 55% |
| `border.window` | #3A3F4A |
| `border.subtle` | #FFFFFF @ 6% |
| `border.input` | #2F343E |
| `border.control` | #3A404C |
| `border.menu` | #3D434F |
| `border.error` | #D1453B |
| `text.primary` | #E6E8EC |
| `text.secondary` | #9AA1AD (labels #AAB0BB) |
| `text.tertiary` | #7D8490 |
| `text.placeholder` | #5F6672 |
| `text.onAccent` | #FFFFFF |
| `text.onAccent.secondary` | #FFFFFF @ 60% |
| `text.link` / `.hover` | #6EA8FF / #9CC3FF |
| `text.error` | #FF7B72 |
| `accent` / `accent.hover` | #3478F6 / #4185FF |
| `selection.bg` | #2F4B73 |
| `hover.overlay` | #FFFFFF (apply 5–8%) |
| `segment.selected.bg` | #3A404C |
| `toggle.track.off` | #3A404C |
| `toggle.knob` | #FFFFFF |
| `status.success` | #3FB950 |
| `status.warning` | #E8B84A |
| `status.danger` | #FF7B72 |
| `status.idle` | #4A505B |
| `hue.blue` | #7FB2FF |
| `hue.green` | #6FD48A |
| `hue.amber` | #E8B84A |
| `hue.purple` | #C49BFF |
| `hue.neutral` | #C3C8D1 |
