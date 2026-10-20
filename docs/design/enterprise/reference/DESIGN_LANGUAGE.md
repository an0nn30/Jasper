# Jasper Design Language

How the Jasper workstation should look and feel. Use it together with the PNG mocks in this package; where this text and a mock disagree, the mock wins on layout and this text wins on intent.

---

## 1. Character

Jasper is a desktop tool that people keep open all day. It should feel like a mature enterprise IDE: dense, orderly, predictable and quiet. It should not feel like a website or a mobile app.

- **Utility over decoration.** Every pixel either holds information or separates it. Nothing is there just to look nice.
- **Dense, but never cramped.** Rows are short and text is small. Even spacing and clear dividers keep it readable.
- **Square and structural.** Edges are crisp, borders are 1px and corners are nearly square. Lines and planes show the structure, not shadows or floating cards.
- **Familiar desktop conventions.** It has a menu bar, toolbars, docked tool windows, tabbed editors, tree tables, titled group boxes, checkboxes, radio buttons, spinners and a status bar. Users who know any professional desktop app should feel at home straight away.
- **Calm color.** The UI is neutral gray. Blue marks selection and the primary action, and almost nothing else. Other colors appear only to carry meaning, such as status or warnings.
- **No motion.** Things show, hide, expand and collapse instantly. The one animated thing is the blinking terminal cursor.

Things to avoid: pill shapes, large rounded corners, floating cards with drop shadows, toggle switches, segmented "iOS" controls, gradient hero areas, oversized empty states with illustrations, emoji and decorative icons.

---

## 2. Color

The palette is a cool, slightly blue-gray neutral. All values are approximate targets.

### Neutrals (light theme)

| Role | Hex | Used for |
|---|---|---|
| Window chrome | `#ECEDEF` | Toolbars, dialog body, menu bar |
| Title bar / header strip | `#E1E3E6` | Window title bar, inactive tool-window headers |
| Side stripes | `#E4E6E9` | Vertical tool-window button rails, bottom tool strip |
| Panel background | `#F7F7F8` | Tool-window bodies, nav tree pane |
| Content surface | `#FFFFFF` | Tables, lists, text fields, terminal, tree contents |
| Table header | `#F0F1F3` | Column headers, list section headers, grouped rows |
| Zebra stripe | `#F7F8FA` | Alternate rows in tables and lists |
| Dialog footer | `#E6E7EA` | Button bar at the bottom of dialogs |

### Lines

| Role | Hex |
|---|---|
| Structural border (panels, windows, group boxes) | `#C4C7CC` |
| Control border (buttons, fields, combos) | `#B4B8BF` |
| Inner divider / column separator | `#D3D6DA` |
| Faint row divider | `#E3E5E8` |
| Hover border on buttons | `#8E949C` |

### Text

| Role | Hex |
|---|---|
| Primary text | `#1E1F22` |
| Secondary labels | `#3A3D42` |
| Descriptions, hints, muted metadata | `#5C6370` |
| Placeholder / faint | `#7A808A` to `#9AA0A8` |
| Disabled | `#A3A8B0` |

### Accent and semantic colors

| Role | Hex | Notes |
|---|---|---|
| Selection / primary blue | `#2F65CA` | Selected rows, default buttons, checked boxes, active radio dots |
| Primary blue, dark edge | `#2655AE` | Border of blue-filled elements |
| Focus ring | `#C9D7F2` | 2px soft halo around the focused field |
| Active tool-window header | `#D9DFEA` | Faint blue tint on the header of the focused tool window |
| Success / connected | `#2E9E4F` | Status dots for connected or running items |
| Warning text | `#8A5A00` / `#6B4A00` | On a pale amber ground of `#FBF1DA`, with border `#E6CF95` |
| Error / recording | `#D03A3A` | Used rarely, for example the "recording" dot in the shortcut recorder |

Protocol and category markers use small solid swatches in muted hues: blue `#2F65CA` (HTTP), brown-orange `#B5651D` (FTP), green `#2E9E4F` (SFTP), purple `#8A4FBF` (TFTP). They are always small squares or dots and never large fills.

**Rules**
- Blue is never decorative. If something is blue, it is selected, primary or checked.
- Text on blue is always white, and secondary text on blue is a pale blue-white (`#DCE5F7`).
- Status is shown with a 7px dot next to a word, never with color alone.
- A dark terminal variant exists (background `#1F2125`, text `#D5D8DD`). The chrome around it stays the same.

---

## 3. Typography

- **UI face:** a neutral, technical sans-serif with clear figures (IBM Plex Sans in the mocks). It is used for every label, menu, button and heading.
- **Data face:** a matching monospace (IBM Plex Mono in the mocks). It is used for anything a user might type or copy: hosts, IPs, ports, paths, commands, URLs, file sizes, timestamps, shortcut keys and the terminal.
- **Sizes:** the base size is about **12px**. Descriptions and column headers are about **11px**. Panel titles are 12px semibold. The only larger text is an item title in a detail pane, at about 15px semibold.
- **Weights:** use regular (400), medium (500) for emphasis such as the primary button and tree category rows, and semibold (600) for panel and group titles. Never use bold display type.
- **Case:** sentence case for settings labels and descriptions ("Show status bar"). Title case for commands, menu items, panel names and buttons ("New Session", "Add Server", "Share Folder…").
- **Single line:** labels, buttons, tabs and table cells never wrap; overflow is cut off with an ellipsis. Only descriptions and helper text wrap.

---

## 4. Density and spacing

- Spacing is based on 2, 4, 6, 8 and 12px steps. The interface rarely needs gaps larger than 20px.
- **Control height:** 22px for buttons, text fields, combo boxes and spinners.
- **Row height:** 20–22px in tables, lists and trees, and about 36px for two-line list items (name plus metadata).
- **Toolbar height:** about 34px. Tool-window header: 24px. Status bar: 22px. Title bar: 28px.
- **Panel padding:** 6–8px around toolbars and filter fields. 12px inside group boxes.
- Settings rows have about 7px of vertical padding and a faint divider between them.

---

## 5. Window anatomy (main workstation)

From top to bottom:

1. **Title bar.** Native window controls on the left and a centered title that shows context ("Workstation — Mac — ~").
2. **Menu bar.** File, Edit, View, Session, Transfer, Tools, Window, Help. Items highlight solid blue with white text on hover.
3. **Main toolbar.** Text-labeled tool buttons grouped by thin vertical separators, then a labeled "Session:" combo box with an inline Connect button, then a search field ("Search everywhere" with a keycap hint) on the far right. Toolbar buttons are flat until hovered; on hover they show a 1px border and a lighter fill.
4. **Work area.**
   - **Left and right side stripes:** narrow 22px rails with vertical text labels ("1: SFTP", "2: File Servers", "Sessions", "Tunnels"). Clicking a label shows or hides its tool window. An open window's label sits on a slightly darker tile.
   - **Tool windows:** docked panels stacked vertically, each with a 24px header strip. The header holds a semibold title, an optional muted count "(2)" or status chip, and tiny glyph actions on the right (+, ⋯, − to hide). The focused tool window's header has a faint blue tint.
   - **Center editor area:** a tab strip of terminal tabs. Each tab has a status dot, a label and a close ×; the active tab is white with a 2px blue underline. A "+" adds a tab. The terminal surface fills the rest.
5. **Bottom tool strip.** Tabs such as "Event Log", "Transfers" and "Problems". A count badge is a small rounded pill only when it carries a number.
6. **Status bar.** Segments separated by 1px vertical dividers. Session and folder are on the left; vault state, terminal size, encoding and any warning are on the right. A warning segment gets the amber background.

Tool windows are always made of a header strip, then an optional toolbar or filter row, then column headers, then a white content area, then an optional footer row (for example the "Follow terminal folder" checkbox).

---

## 6. Controls

### Buttons
- **Standard:** 22px tall, 3px corner radius, 1px `#B4B8BF` border and a very subtle top-to-bottom white-to-light-gray fill. Label in primary text. On hover the border darkens.
- **Default / primary:** the same shape, filled solid blue with a darker blue border, white medium-weight label. There is only one per dialog or panel, and it is always the right-most button in the footer.
- **Disabled:** a flat light-gray fill, a lighter border and gray text. It stays visible and is never hidden.
- **Labels:** verbs. Add "…" when the button opens another dialog or picker ("Choose…", "Add Folder…", "Edit Custom VM Options…").
- **Toolbar buttons:** borderless until hovered, and may be text only.

### Text fields
- 22px tall, white, 1px border, 2px radius. Placeholder text is faint gray.
- Focus shows a blue border plus a soft 2px blue halo.
- Fields for hosts, paths, ports and commands use the monospace face.
- Read-only or unavailable fields have a gray fill (`#F0F1F3`) and muted text.

### Combo boxes (dropdowns)
- Same size as a button, with the subtle gradient fill, the current value left-aligned and a small ▼ on the right.
- Open menus are white with a darker 1px border and a modest shadow. The selected or hovered row is solid blue with white text. An optional search field sits at the top. "New…" actions sit at the bottom below a divider.

### Spinners (numeric fields)
- A white, right-aligned monospace value with its unit in muted text ("34 px", "512 MiB", "10 s"), plus a narrow column of stacked ▲ / ▼ arrow buttons attached on the right.
- Paired values sit side by side with a muted "×" between them ("100 columns × 35 lines").

### Checkboxes (replace toggle switches everywhere)
- A 13px square with a 2px radius. Unchecked is white with a gray border. Checked is solid blue with a white ✓.
- The label sits to the right. An optional description sits under the label in smaller muted text.
- Dependent settings are indented about 20px under their parent and show as disabled (grayed) until the parent is checked.

### Radio buttons (replace segmented controls)
- A 14px circle with a gray ring. When selected the ring is blue with a 6px blue center dot. Options sit in a horizontal row with generous gaps.
- Use radio buttons for 2–4 mutually exclusive choices (Protocol, Access, Authentication, Tunnel type, Toolbar style).
- When a choice has a visual preview, such as the theme, show a small thumbnail above each radio. The selected thumbnail gets a 2px blue frame.

### Titled group boxes
- The main way to organize forms. A 1px structural border with a 3px radius, and a semibold title that sits **on** the top border line, breaking it.
- Inside, label/field pairs line up in a two-column grid. Labels are on the left with a trailing colon ("Name:", "Reachable from:"); fields fill the right.

### Tables and tree tables
- White body, a light-gray header row with 11px column titles and 1px column separators.
- Rows are 20–24px, optionally zebra-striped. A selected row is solid blue with white text.
- Tree rows use ▶ / ▼ disclosure glyphs and indent children about 24px. Category rows are medium weight with a muted count on the right.
- Grouped tables (for example notification groups by plugin) use a light-gray header row for each group.
- Action buttons for a table sit directly beneath it in a row ("Add Folder…", "Reindex", "Remove"). Buttons that need a selection stay disabled until a row is selected.

### Ordered lists
- A white list box with a 1px border and row numbers in a muted monospace column.
- Beside the list is a vertical column of equal-width buttons: Remove, Move Up, Move Down.
- Below the list is an inline "Add…" field with an Add button that is enabled only when the field has text.

### Tabs
- **Editor tabs** (terminals): flat, separated by 1px dividers, with the active tab white with a blue underline.
- **Tabbed panes** inside content (Overview / Details): classic folder tabs with a top border and slightly rounded top corners. The active tab is white and joins the content below it; inactive tabs are gray.

### Keycaps
- Small monospace key labels in a light box with a 1px border and a slightly thicker bottom edge. They are used for shortcuts in the Keymap, the search field hint and similar places.

### Status and badges
- **Status:** a 7px dot plus a word ("Running", "Stopped", "Enabled"). Green means live or connected; gray means idle or off.
- **Protocol tags:** tiny uppercase labels in a 1px bordered box with a pale tint (for example "HTTP" in pale blue).
- **Warnings:** an inline amber strip with a ▲ glyph and plain sentence text ("Warning · Plain FTP sends the password unencrypted").

### Iconography
- Kept to a minimum. Plain text glyphs do most of the work: + − ⋯ ▼ ▶ ← → ⟳ ✕ ✓ ▲ ★.
- A colored square or dot stands in for most category icons.
- Where icons are needed, they are small (about 16px), single-color, thin-line and sit inside the text baseline.

---

## 7. Dialogs

Every modal dialog follows the same layout:

1. **Title bar** with window controls and a centered title ("New File Server", "Add Host", "New Tunnel", "Settings").
2. **Body** on the chrome background, made of stacked titled group boxes (for example Server / Access / Security & Startup).
3. **Contextual warnings** as an amber strip below the groups, only when relevant.
4. **Footer bar** with a slightly darker background and a top border.
   - **Left:** a live monospace preview of what the form will produce (`sftp://127.0.0.1:2222`, `ssh -L 8080:localhost:5432 dustin@192.168.1.119`). It updates as the user types and is cut off with an ellipsis.
   - **Right:** `Cancel`, then any secondary action ("Add and Connect"), then the **primary** button ("Add Server", "Add Tunnel", "OK").
   - The primary button stays disabled until the required fields are valid.

Forms change in place: picking a protocol or type shows, hides or relabels fields instantly, rather than taking the user to another screen. Unavailable options are shown disabled with a reason ("No login (TFTP has none)") rather than being removed without explanation.

Small diagrams, such as the tunnel path strip, use plain bordered cells with a colored left edge, small uppercase captions, monospace values and → arrows between cells, rather than illustrations.

---

## 8. Settings window

- **Left:** a 230px navigation tree on white with a search field above it. Section nodes are semibold with a ▼ (Appearance & Behavior, Terminal, Tools). Child pages are indented under them. Top-level pages (Keymap, Plugins) sit flush left. The selected page is a full-width blue row.
- **Top of the content pane:** Back / Forward buttons as a joined pair, then a breadcrumb: muted section › semibold page name.
- **Content:** stacked titled group boxes. Each setting is a row: label (and optional description) on the left, control right-aligned. Checkbox settings put the box before the label.
- **Search** filters the tree by page name and by setting labels and descriptions.
- **Footer:** a round "?" help button on the left. Cancel / Apply / OK on the right. Apply becomes enabled only after something changes.
- Master–detail pages (Plugins) split into a bordered list on the left and a detail pane on the right with a title, a metadata line, an action button and tabs.

---

## 9. Interaction and states

| State | Treatment |
|---|---|
| Hover | Subtle: darker border on buttons, faint blue tint (`#EEF2FA`) on rows, blue fill on menu items |
| Selection | Solid blue fill, white text (rows, list items, menu items, nav tree) |
| Focus | Blue border plus 2px pale-blue halo on fields and the open combo |
| Disabled | Gray text, lighter border, flat fill; stays in place |
| Pressed / active toggle | A slightly darker tile behind the item, as with side-stripe labels |
| Recording / live | A red dot inside a focused field (the shortcut recorder) |

- Feedback is immediate and local. Previews, counts and URLs update as the user types.
- Destructive or selection-dependent actions are disabled rather than hidden.
- No transitions, slides or fades.

---

## 10. Writing style

- Plain, factual, short. Describe what a setting does and when it takes effect ("Applies to new panes", "Takes effect after restart").
- Field labels end with a colon inside forms. Setting labels inside settings rows do not.
- Put "…" on any command that opens further UI.
- Empty states are a single bold line plus one sentence of help and, at most, one button ("No Tunnels. Forward a local port to a remote host, or use Forward Port from an SSH pane." [New Tunnel…]).
- Counts appear in parentheses after titles ("Sessions (2)") or as "1 of 1 enabled".
