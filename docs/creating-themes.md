# Creating themes for Jasper

A Jasper theme is one UTF-8 JSON file that colours Jasper's interface, its terminal panes and the Editor. This guide is
the complete reference for writing one: the concepts, the file format, every colour role and what it paints, the
terminal and editor schemes, the messages a file that does not load produces, the converter for IntelliJ and Alacritty
themes, and how to ship themes in a plugin. [Themes](themes.md) covers choosing and importing themes as a user.

## Concepts

| Term | What it is | Chosen with |
|---|---|---|
| Appearance | **Dark**, **Light** or **Match System**. Match System follows the operating system live on macOS; on Linux and Windows, which do not report it, it shows Dark. | `ui.appearance` (`"system"`, `"dark"`, `"light"`) |
| Theme | One file, `<id>.json`, with a name. It holds a Dark UI variant, a Light UI variant or both, one or more terminal schemes, and an editor scheme for each UI variant. A file without UI variants is a terminal-only theme. | `ui.theme.dark`, `ui.theme.light` |
| UI variant | `ui.dark` or `ui.light`: the colour roles of the whole interface, from window chrome to plugin views. The Dark appearance shows the Dark theme's `ui.dark`, the Light appearance the Light theme's `ui.light`. | (by the theme and the appearance) |
| Terminal scheme | `terminal.<key>`: 21 colours for the terminal text area. Its id is `<theme id>/<key>`. A UI variant's matching scheme is the one with its key (`dark` or `light`); other keys are extra schemes. | `terminal.scheme`: `"match"` (the shown variant's matching scheme) or a scheme id |
| Editor scheme | `editor.dark` or `editor.light`: the Editor's surfaces and syntax colours. The Editor always uses the shown variant's editor scheme, whatever `terminal.scheme` says. | (by the shown variant) |
| Look | The presentation: window chrome, controls, metrics and fonts. The default look, or a plugin's look such as Enterprise ([Looks](looks.md)). A look never carries colours, and every theme works under every look. | Settings › Plugins |

### How the parts combine

With `ui.appearance = "system"` on a Mac in Dark Mode, `ui.theme.dark = "harbour"` and `terminal.scheme = "match"`,
every window shows Harbour's `ui.dark`, the terminal panes use `harbour/dark` and the Editor uses Harbour's
`editor.dark`. Set `terminal.scheme = "slate-cobalt/high-contrast"` and only the terminal text area changes; the
interface and the Editor stay on Harbour. Switch the system to Light Mode and the windows show `ui.theme.light`'s
`ui.light`. Chrome and workspace always show the same variant.

### What a theme can and cannot change

A theme sets:

- every interface colour role of each UI variant: 60 required roles, 2 optional title bar roles and 9 optional derived
  colours ([UI roles](#ui-roles));
- the terminal text area's background, text, bold text, cursor, selection and 16 ANSI colours, in as many schemes as
  it likes ([Terminal schemes](#terminal-schemes));
- the Editor's background, text, caret, selection, current line, gutter and line numbers, and the colour, bold and
  italic of 16 syntax roles ([Editor schemes](#editor-schemes)).

It cannot change:

| Not in a theme | Set by |
|---|---|
| Fonts and font sizes | Settings (`ui.font.*`, `terminal.font.*`) and the look |
| Spacing, sizes, corner radii, control shapes, layout | The look |
| Icons | Jasper and plugins draw them; roles tint them (`text.secondary` is the default icon tint, the `hue.*` roles colour category icons) |
| Shadows | Painted black at fixed strengths |
| The strength of hovers, focus rings and tile tints | Fixed treatments ([What Jasper paints on top of the roles](#what-jasper-paints-on-top-of-the-roles)) |
| Workspace translucency and blur | `ui.workspace.opacity` and `ui.workspace.blur` (macOS only); every theme is opaque |
| Which role paints dialog bodies and the Settings navigation | The look's kit file ([Beyond colours](#beyond-colours-building-a-look)) |
| The 256-colour palette's colours 16 to 255 | Fixed: the standard xterm 6×6×6 cube and grey ramp |
| The 14 derived colours that are not overridable | Computed from the roles ([Derived roles](#derived-roles)) |
| The Editor's find and 'hlsearch' match highlight, matched-bracket highlight, occurrence marks and Vim sneak's matches | RSyntaxTextArea's default colours; sneak's matches are a fixed translucent magenta |

## Quick start

1. **Start from a complete file.** Copy the [worked example](#worked-example-harbour) at the end of this guide, or a
   built-in theme: `slate-cobalt.json` is printed in full in the
   [Slate Cobalt colour reference](design/theme-engine/slate-cobalt.md), and a checkout of Jasper has every built-in
   file in `jasper-ui/src/main/resources/dev/jasper/ui/theme/`.
2. **Name it.** Change `"name"` (and `"author"`), and save the file as `<id>.json`, the id being the name in lowercase
   words joined by dashes: "My Theme" is `my-theme.json` ([Ids and names](#ids-and-names)). A built-in theme's id, or
   one a plugin declares, cannot be used.
3. **Install it.** Copy the file into the `themes` folder in Jasper's home (`~/.config/jasper/themes/` on macOS and
   Linux, `%APPDATA%\jasper\themes\` on Windows; [Configuration](configuration.md#location-and-startup-options) gives
   every location), or use **Import Theme…** ([Installing and trying a theme](#installing-and-trying-a-theme)).
4. **Select it.** Settings (Cmd+, on macOS, Ctrl+Shift+, on Linux and Windows) › Appearance & Behavior › Appearance:
   pick it under **Dark theme** or **Light theme** and choose **Apply** or **OK**. Its schemes are under
   **Terminal scheme**.
5. **Iterate.** Edit the file in the themes folder, then run **Reload Config** (File menu or the command palette,
   Cmd+Shift+P on macOS, Ctrl+Shift+P on Linux and Windows). Jasper does not watch theme files: it reads the folder
   again only on Reload Config, when Settings saves, when `config.toml` changes and after an import or removal. A
   changed theme that is on show is applied again at once.
6. **Fix problems.** A file that does not load is skipped: the status bar's configuration cell reads
   **Config warnings**, and clicking it lists the problem as `<file>: warning: <message>` (with `:<line>:1` after the
   file for a JSON syntax error). A pick of the skipped theme falls back to Slate Cobalt in Dark or Jasper Meadow
   Light in Light, with a warning on the setting.
   [Validation and error messages](#validation-and-error-messages) explains every message.

## The file format

```json
{
  "jasper-theme": 1,
  "name": "My Theme",
  "author": "Your Name",
  "ui": { "dark": { "surface.app": "#23262C", "...": "..." }, "light": { "...": "..." } },
  "terminal": { "dark": { "name": "My Theme Dark", "bg": "#2B2F36", "...": "..." }, "light": { "...": "..." } },
  "editor": { "dark": { "background": "terminal.bg", "...": "..." }, "light": { "...": "..." } }
}
```

(The `"..."` entries stand for the rest of each object; the loader would reject them. The
[worked example](#worked-example-harbour) is a complete file.)

### Top-level keys

| Key | Required | Value |
|---|---|---|
| `jasper-theme` | Yes | The JSON number `1`: not `"1"`, not `1.0`. It marks the file as a Jasper theme in format 1. |
| `name` | Yes | A non-empty string that is not only spaces. Settings shows it; Import derives the id from it. |
| `author` | No | A string; it may be empty. |
| `ui` | No | An object with `dark`, `light` or both, each an object of colour roles. Without `ui` the file is a [terminal-only theme](#terminal-only-themes). |
| `terminal` | Yes | An object with at least one scheme. `ui.dark` needs `terminal.dark` and `ui.light` needs `terminal.light`. |
| `editor` | With `ui`, and only then | An object with exactly the variants `ui` has, each an object of 23 entries. |

No other top-level key is allowed.

### Strict JSON

- Standard JSON only: no comments, no trailing commas, strings in double quotes, the escapes `\"`, `\\`, `\/`, `\b`,
  `\f`, `\n`, `\r`, `\t` and `\uXXXX`, no raw control characters in strings, no leading zeros in numbers.
- A key may appear only once in an object.
- Nothing but white space may follow the closing `}`.
- Objects and arrays nest at most 8 deep; a theme uses 3.
- The file is UTF-8 and at most 1 MiB. A leading byte-order mark is ignored.
- Every key is checked: an unknown key, a missing key and a malformed value are errors at every level (the top level,
  the UI roles, the scheme colours, the editor entries). Key order does not matter; keys are case-sensitive
  (`text.onAccent`, `currentLine`, `doc_comment`).
- Colours, names and editor values are strings. A number, `true`, `false`, `null`, an array or an object where a
  string belongs is an error.
- One error stops the whole file: Jasper never uses part of a theme.

### Ids and names

- **Id.** A theme's id is its file name without `.json`: lowercase ASCII letters and digits in words joined by single
  dashes (`[a-z0-9]+(-[a-z0-9]+)*`), at most 64 characters. In the themes folder, a `.json` file whose name is not an
  id (`Dracula.json`, `my_theme.json`) is skipped with a warning; a file whose name does not end in `.json` is not read.
- **Import.** Import names the file after the theme's `name`, not after the file chosen: accents are dropped (Unicode
  decomposition, then non-ASCII characters removed), the rest is lower-cased, every run of characters other than
  `a`–`z` and `0`–`9` becomes one `-`, dashes at either end are removed, and the result is cut to 64 characters
  without a trailing dash. "Gruvbox Dark" is `gruvbox-dark`; "Frappé Night" is `frappe-night`. A name that leaves
  nothing cannot be imported.
- **Reserved ids.** The built-in ids `slate-cobalt`, `catppuccin-latte`, `catppuccin-frappe`, `catppuccin-macchiato`,
  `catppuccin-mocha`, `ubuntu-yaru` and `jasper-meadow`, and every theme id a discovered plugin declares, enabled or
  not (the bundled Enterprise plugin declares `enterprise`). A themes-folder file with a reserved id is ignored, and
  Import refuses one.
- **Names.** Names need not be unique. When two installed themes have the same name, ignoring case, Settings shows
  each as `Name (id)`; two schemes with the same name show as `Name (theme/key)`.
- **Scheme keys.** Lowercase letters and digits in words joined by dashes, at most 32 characters: `dark`, `light`,
  `high-contrast`.

## Colour values

| Where | Accepted | Examples |
|---|---|---|
| UI roles (`ui.dark`, `ui.light`) | `#RRGGBB`, or `#RRGGBBAA` with the alpha last; hex digits in either case | `"#2F6FDB"`, `"#ffffff14"` (white at 20/255, about 8%) |
| Derived-role overrides in a UI variant | `#RRGGBB` only | `"#2D3036"` |
| Terminal scheme colours | `#RRGGBB` or `#RRGGBBAA` | `"#2B2F36"` |
| Editor entries | `#RRGGBB` or a reference, optionally laid over the background with `@ N%`, and for a syntax role `, italic` and `, bold` ([Editor values](#editor-values)) | `"#C678DD"`, `"terminal.ansi.magenta, bold"`, `"hover.overlay @ 5%"` |

Nothing else is a colour: not `#RGB`, not colour names, not `rgb(…)`, not `0x` forms.

A role with alpha is painted as given, over whatever lies beneath it. The built-in themes give alpha only to
`surface.scrim`, `border.subtle`, `text.onAccent.secondary` and `text.onSelection.secondary`, the roles meant to be
laid over something else; keep the others opaque. The [derived roles](#derived-roles) first lay a translucent role
over the colour they mix it with, and an editor `@ N%` mixes the colours and drops the alpha, so both give opaque
colours. The loader also accepts `#RRGGBBAA` in terminal schemes; every built-in scheme is opaque.

### Editor values

```
<colour>[ @ <N>%][, italic][, bold]
```

`<colour>` is one of:

- `#RRGGBB` (no alpha);
- a terminal reference, read from the variant's matching scheme (`editor.dark` reads `terminal.dark`) whatever
  terminal scheme the user picks: `terminal.bg`, `terminal.fg`, `terminal.fg.bold`, `terminal.cursor`,
  `terminal.selection`, `terminal.ansi.<colour>` or `terminal.ansi.bright.<colour>`, where `<colour>` is `black`,
  `red`, `green`, `yellow`, `blue`, `magenta`, `cyan` or `white`;
- a UI role key of the same variant, such as `text.secondary`, `accent` or `hover.overlay`. Every role works,
  including the [roles you do not list](#roles-you-do-not-list) and the title bar roles; the
  [derived roles](#derived-roles) (`surface.header`, …) do not.

`@ N%` lays the colour at N per cent over the resolved `background`, giving an opaque colour. N is a whole number from 1
to 100; spaces around `@` are optional; there is at most one share; `background` itself cannot take one. A role with
alpha referred to without a share keeps its alpha.

`, italic` and `, bold` follow the colour on syntax roles only, either or both, in any order; spaces around the commas
are ignored. The seven surfaces take no style, and a trailing comma is an error.

`"accent@30%,bold,italic"` is as valid as `"accent @ 30%, bold, italic"`.

## UI roles

Each UI variant lists all 60 roles of the eight tables below, and may add the 2 [title bar roles](#title-bar-roles)
and the 9 overridable [derived roles](#derived-roles). Eight more roles always follow another role and must not be
listed ([Roles you do not list](#roles-you-do-not-list)). The Slate Cobalt columns are the values of Slate Cobalt, the
default Dark theme, as a guide to how the roles relate.

Plugins see the same roles through the SDK ([`SemanticColor`](sdk-reference.html#dev.jasper.sdk.ui.SemanticColor),
read with [`Appearance.color`](sdk-reference.html#dev.jasper.sdk.ui.Appearance.color(SemanticColor))), so a theme
colours their views too. The tiles on Settings › Appearance preview a variant with `surface.input` (the background),
`surface.sidebar`, `surface.app`, `text.primary` and `accent`.

### Surfaces

| Role | Paints | Slate Cobalt Dark | Slate Cobalt Light |
|---|---|---|---|
| `surface.app` | Window chrome: the header row and toolbar, the menu bar, the gutters between panels and the rails; the status bar (`surface.status` follows it); the title bar, unless `surface.titlebar` is set; dialog bodies under a look whose kit file paints them with `surface.app` (Enterprise) | `#23262C` | `#E9ECF0` |
| `surface.panel` | Tool window bodies; the default background of components | `#2E323A` | `#F5F7F9` |
| `surface.panel.raised` | Tooltips; cards and footers inside panels | `#343943` | `#ECEFF3` |
| `surface.dialog` | Dialog, sheet and Settings bodies in the default look | `#343943` | `#F5F7F9` |
| `surface.card` | Lists, trees and tables; grouped cards; the selected tab of tabbed panes; under the Enterprise look, the selected workspace tab and the Settings navigation | `#2E323A` | `#FFFFFF` |
| `surface.sidebar` | Sidebars in windows, such as the Settings navigation (default look), the plugin manager's and the file chooser's | `#292D34` | `#EEF1F4` |
| `surface.input` | Text, password and search fields, text areas and combo boxes | `#23262C` | `#FFFFFF` |
| `surface.field.raised` | The command field in the title bar | `#3A3F49` | `#FFFFFF` |
| `surface.control` | Dropdown and picker triggers, progress tracks, empty-state tiles outside panels | `#3C414B` | `#FFFFFF` |
| `surface.control.hover` | Hovered controls, menu items, list rows, tabs and split dividers | `#454B56` | `#EEF1F5` |
| `surface.control.selected` | Pressed and selected toolbar buttons, the active rail item, the selection of an unfocused list | `#4A515D` | `#D3D9E1` |
| `surface.menu` | Menus, popups, dropdown lists and the command palette | `#363B45` | `#FFFFFF` |
| `surface.terminal` | Behind and between terminal panes and under the tabs; the selected workspace tab in the default look. Give it the matching scheme's `bg` so the terminal meets its tab without a seam. | `#2B2F36` | `#FAFBFC` |
| `surface.tabstrip` | The tab strip behind the inactive workspace tabs | `#3F444F` | `#DADFE6` |
| `surface.emptyTile` | The icon tile of an empty state inside a panel | `#3C414B` | `#DADFE6` |
| `surface.scrim` | The dimming behind sheets, modal overlays and dialogs, painted with its alpha | `#0A0C108C` | `#1B1F2740` |

### Borders

| Role | Paints | Slate Cobalt Dark | Slate Cobalt Light |
|---|---|---|---|
| `border.window` | Window, dialog and popup edges, component borders, the status bar's edge, the dividers between workspace tabs, the dock's rules | `#444A55` | `#C6CCD5` |
| `border.subtle` | Separators and the edges of panels, cards and rows; usually translucent | `#FFFFFF14` | `#0000000F` |
| `border.input` | The edges of text and search fields, steppers and the segmented control's track | `#4A515D` | `#CDD3DB` |
| `border.field.raised` | The edge of the title bar's command field | `#565D6A` | `#CDD3DB` |
| `border.field.raised.hover` | That edge while hovered | `#6A7280` | `#AEB6C2` |
| `border.control` | Dropdown and control edges, the scroll bar thumb, the outline of a slider's knob; `border.identity` follows it | `#565D6A` | `#C3CAD3` |
| `border.menu` | Menu and popover edges, menu separators | `#565D6A` | `#CDD3DB` |
| `border.error` | An invalid field's edge and its glow; the tint of a search field that finds nothing | `#F0716B` | `#C42B31` |

### Text

| Role | Paints | Slate Cobalt Dark | Slate Cobalt Light |
|---|---|---|---|
| `text.primary` | Default text: titles, values, list names, input text | `#F2F4F7` | `#1B1F27` |
| `text.secondary` | Labels, descriptions, the window title, the default icon tint, classic table headers | `#B2B9C4` | `#5A6270` |
| `text.strong.secondary` | Button labels, Settings row titles, details in header strips and status cells | `#DADFE6` | `#343B47` |
| `text.tertiary` | Captions, counts, section labels, menu shortcuts | `#9AA2AE` | `#5F697A` |
| `text.placeholder` | Placeholders and disabled text; the × on workspace tabs | `#8991A0` | `#6D7788` |
| `text.field.raised` | The label of the title bar's command field | `#D0D5DD` | `#46505E` |
| `text.onAccent` | Text on `accent`: primary buttons, highlighted menu items | `#FFFFFF` | `#FFFFFF` |
| `text.onAccent.secondary` | Second lines on accent rows, such as the Run window's selection; usually translucent | `#FFFFFFB3` | `#FFFFFFBF` |
| `text.onSelection` | Text on `selection.bg` in lists, trees, tables and dropdowns | `#FFFFFF` | `#FFFFFF` |
| `text.onSelection.secondary` | Second lines on selected rows; usually translucent | `#FFFFFFB3` | `#FFFFFFBF` |
| `text.link` | Link buttons and links in notices | `#84B3FF` | `#2563C9` |
| `text.link.hover` | A hovered link | `#A8C9FF` | `#1D4F9F` |
| `text.error` | Validation messages under fields | `#FF8A83` | `#B3262C` |
| `text.success` | The caption beside a success dot in an identity header, such as a connected state | `#52D273` | `#1E7A46` |

### Accent, selection and hover

| Role | Paints | Slate Cobalt Dark | Slate Cobalt Light |
|---|---|---|---|
| `accent` | Primary buttons, switches that are on, progress bars, the menu highlight, the focus ring (`border.focus` follows it) | `#2F6FDB` | `#2563C9` |
| `accent.hover` | A hovered or pressed primary button | `#2563C9` | `#3470D2` |
| `selection.bg` | Selected rows in lists, trees, tables and dropdowns, and in the Settings navigation | `#2F5FA8` | `#2563C9` |
| `hover.overlay` | The hover wash, which Jasper paints at 5% on rows and 8% on icon and toolbar buttons and window chrome. Make it white in a dark variant and black in a light one. | `#FFFFFF` | `#000000` |

### Controls

| Role | Paints | Slate Cobalt Dark | Slate Cobalt Light |
|---|---|---|---|
| `segment.track` | A segmented control's track | `#23262C` | `#E9ECF0` |
| `segment.selected.bg` | The selected segment; a selected toggle button | `#4A515D` | `#FFFFFF` |
| `toggle.track.off` | A switch's track while it is off | `#77808E` | `#C3CAD3` |
| `toggle.knob` | A switch's knob and a slider's thumb | `#FFFFFF` | `#FFFFFF` |
| `button.secondary.bg` | A secondary button's face | `#3C414B` | `#FFFFFF` |
| `button.secondary.bg.hover` | That face while hovered | `#454B56` | `#F0F3F7` |
| `button.secondary.border` | A secondary button's edge | `#565D6A` | `#C3CAD3` |

### Workspace tabs

| Role | Paints | Slate Cobalt Dark | Slate Cobalt Light |
|---|---|---|---|
| `tab.indicator` | The line on the selected workspace tab: along its top in the default look, along its bottom under the Enterprise look; the underline of selected tabs in tabbed panes | `#5B9BF5` | `#2563C9` |
| `tab.indicator.inactive` | The same line while the window is not focused (default look) | `#436596` | `#90AFE3` |

### Status

| Role | Paints | Slate Cobalt Dark | Slate Cobalt Light |
|---|---|---|---|
| `status.success` | Running, live and connected dots, including the status bar's running dot | `#52D273` | `#1E7A46` |
| `status.warning` | Warnings, restart-required dots, warning text on `warning.ground` | `#F2C55C` | `#9E6200` |
| `status.danger` | Errors, error pills, destructive buttons on hover | `#F07F78` | `#C42B31` |
| `status.idle` | Stopped and disconnected dots | `#7A8391` | `#9AA3B0` |

### Category hues

The hues mark an item's kind: icon glyphs and the ink of tiles and chips use the hue at full strength, and their fills
use the same hue at 17% in Dark and 13% in Light (the `hue.*.surface` roles follow the hues). Each must read as text
on `surface.panel`, `surface.sidebar` and `surface.card`.

| Role | Slate Cobalt Dark | Slate Cobalt Light |
|---|---|---|
| `hue.blue` | `#8DB6F5` | `#2563C9` |
| `hue.green` | `#6FDB8C` | `#1E7A46` |
| `hue.amber` | `#F2C55C` | `#9E6200` |
| `hue.purple` | `#C8A4FF` | `#7A3E9D` |
| `hue.neutral` | `#D0D5DD` | `#5A6270` |

### Title bar roles

Two optional roles give the window's header row (the title, the command field and the toolbar buttons) colours of
its own, such as a dark title bar on a light theme. Slate Cobalt leaves both out; Ubuntu Yaru sets both. Under the
Enterprise look the terminal window's header row and, on macOS and Windows, IntelliJ Classic's title bar above it
take `surface.app` instead.

| Role | Paints | Left out, it is |
|---|---|---|
| `surface.titlebar` | The title bar's background | `surface.app` |
| `text.titlebar` | The title bar's text and icons | `text.primary` |

When either differs from what it would be left out, the default look paints the header row with colours derived from
the two; everything else keeps the variant's roles:

| Header colour | Derived as |
|---|---|
| Strong secondary text, the neutral hue | `text.titlebar` at 85% over `surface.titlebar` |
| Secondary text (the window title) | `text.titlebar` at 72% |
| Tertiary and placeholder text | `text.titlebar` at 50% |
| The command field's label | `text.titlebar` |
| The command field's fill | `text.titlebar` at 10% |
| The command field's edge; the pressed and selected fill | `text.titlebar` at 16% |
| The command field's edge on hover | `text.titlebar` at 26% |
| The hover wash | `text.titlebar`, at the usual hover strength |
| Subtle dividers | `text.titlebar` with 6% alpha |

The header counts as dark (for its treatment strengths) when `surface.titlebar` is darker than 50 CIE L\*, in either
variant. Give `text.titlebar` at least 4.5:1 on `surface.titlebar`, and leave room for the 50% text to read too.

### Roles you do not list

These roles always follow another; listing one is an error (`Unknown colour`).

| Role | Is always |
|---|---|
| `border.focus` | `accent` |
| `surface.status` (the status bar) | `surface.app` |
| `border.identity` (the outline of an identity header's tile) | `border.control` |
| `hue.blue.surface`, `hue.green.surface`, `hue.amber.surface`, `hue.purple.surface`, `hue.neutral.surface` | Its hue; Jasper applies the tile strength |

### Derived roles

Jasper computes 23 more colours from each variant, mostly for the classic controls and chrome of the Enterprise look,
and for notices. Every derived colour is opaque. In the recipes, "X at N% over Y" mixes two colours, and "at least
N L\* past Y" moves the result further from Y, toward the variant's text, until the two are N apart in CIE L\*
(perceived lightness).

Nine of them are overridable: a variant may list them like roles, as `#RRGGBB` only, and the listed colour is used
instead of the recipe. The Catppuccin themes override five, Ubuntu Yaru all nine.

| Overridable role | Recipe | Paints |
|---|---|---|
| `surface.header` | `text.primary` at 5% over `surface.app`, at least 3 L\* past `surface.app` | Tool window header strips (Enterprise look) |
| `surface.header.active` | `accent` at 10% over `surface.app`, at least 2 L\* past `surface.header` | The focused tool window's header strip, held at least 1 L\* past `surface.header`; classic dialog title bands; the Run item while the Run window is open |
| `surface.stripe` | `text.primary` at 3.5% over `surface.app`, at least 1.5 L\* past it | Tool window stripes and the bottom strip (Enterprise look); classic folder tabs |
| `surface.tableHeader` | `text.primary` at 6% over `surface.card`, at least 3 L\* past it | Classic table and tree headers; keycaps |
| `surface.zebra` | `text.primary` at 2.5% over `surface.card`, at least 1.5 L\* past it; halfway between `surface.tableHeader` and `surface.card` when that would not be closer to `surface.card` | Alternate rows of classic tables, ordered lists, the band of panel footers |
| `surface.rowHover` | `accent` at 8% over `surface.card` | Hovered rows in classic tables and trees |
| `warning.ground` | `status.warning` at 14% over `surface.card` | Warning notices and warning status cells |
| `warning.border` | `status.warning` at 40% over `surface.card` | The edge of warning notices |
| `border.divider` | `border.window`, raised to 1.3:1 on `surface.card`, at 73% over `surface.card` | Classic grid lines and separators, the rules of the classic menu row and status cells, the bottom strip's dividers |

The other fourteen are always computed; listing one is an error (`Unknown colour`).

| Computed role | Recipe | Paints |
|---|---|---|
| `danger.ground`, `danger.border` | `status.danger` at 14% and 40% over `surface.card` | Error notices and their edge, error status cells |
| `info.ground`, `info.border` | `accent` at 14% and 40% over `surface.card` | Information notices and their edge, count pills |
| `surface.footer` | `text.primary` at 3% over `surface.dialog`, at least 2 L\* past it | The footer strips of Settings and dialogs |
| `border.row` | `border.window`, raised to 1.3:1 on `surface.card`, at 46% over `surface.card` | Row dividers, such as those of classic tables, panel footers and Settings |
| `border.hover` | `border.control`, raised to 1.5:1 on `surface.card`, with `text.primary` at 24% over it | The frame of hovered classic buttons and controls |
| `accent.edge` | `accent` moved 7 L\* further from `surface.card` | The edge of classic default buttons and checked controls; the Run item's text |
| `button.end` | `text.primary` at 4% over `button.secondary.bg` | The end colour of classic buttons' gradient |
| `tag.border.blue`, `tag.border.green`, `tag.border.amber`, `tag.border.purple`, `tag.border.neutral` | Each hue at 40% over `surface.card` | The edges of classic tags |

### What Jasper paints on top of the roles

These treatments are fixed; design the roles with them in mind.

| Treatment | How it is painted |
|---|---|
| Hover on rows, tables and trees | `hover.overlay` at 5% |
| Hover on icon and toolbar buttons and window chrome | `hover.overlay` at 8% |
| Focus ring | `accent` at 32% in Dark and 25% in Light around the focused control |
| Error ring | `border.error` at 22% |
| Category tiles and chips | The hue at 17% in Dark and 13% in Light |
| Search field that finds nothing | `border.error` at 16% over `surface.input` |
| Selected text in text components without Jasper's own styling | `text.primary` over the matching terminal scheme's `selection` (the shown variant's own scheme, not the user's pick) |
| Menu highlight | `accent` with `text.onAccent` |
| Workspace tabs, default look | The selected tab is `surface.terminal` with its `tab.indicator` line. The others are `surface.tabstrip`; when it is within 9 CIE L\* of `surface.terminal`, it is moved toward black (toward white when `surface.terminal` is darker than 9 L\*) until it is 9 L\* away |
| Workspace tabs, Enterprise look | The strip is `surface.tabstrip`; the selected tab is `surface.card` with `tab.indicator` along its bottom |
| Shadows | Black at fixed strengths |

`ui.dark` is always treated as dark and `ui.light` as light (for the focus ring and tile strengths above, and for
plugins, which read the variant), so make each variant's colours really dark or light.

## Terminal schemes

A scheme is an object under `terminal` with a `name` (a non-empty string; Settings lists schemes by it) and exactly
these 21 colours. A scheme colours only the terminal text area; the area around the panes and the tabs are the shown
UI variant's `surface.terminal` and `surface.tabstrip`.

| Key | Colours | Slate Cobalt Dark | Slate Cobalt Light | High Contrast |
|---|---|---|---|---|
| `bg` | The default background | `#2B2F36` | `#FAFBFC` | `#000000` |
| `fg` | The default text colour | `#F2F4F7` | `#1B1F27` | `#FFFFFF` |
| `fg.bold` | The default colour of bold text; bold text with an explicit colour keeps it | `#FFFFFF` | `#000000` | `#FFFFFF` |
| `cursor` | The cursor | `#F2F4F7` | `#1B1F27` | `#FFFFFF` |
| `selection` | The background of selected cells | `#3A5A8C` | `#C9DAF2` | `#264F9E` |
| `black` | ANSI 0 | `#4A515C` | `#1B1F27` | `#4D4D4D` |
| `red` | ANSI 1 | `#FFA49D` | `#C42B31` | `#FF7B72` |
| `green` | ANSI 2 | `#5FE88A` | `#1E7A46` | `#3CFF7A` |
| `yellow` | ANSI 3 | `#FFD75F` | `#8A6200` | `#FFE14D` |
| `blue` | ANSI 4 | `#8FC0FF` | `#1F5FBF` | `#7CB8FF` |
| `magenta` | ANSI 5 | `#EBA2FF` | `#7A3E9D` | `#FF8CFF` |
| `cyan` | ANSI 6 | `#4FE0E8` | `#0A728E` | `#4DF0FF` |
| `white` | ANSI 7 | `#D9DEE5` | `#5A6270` | `#E6E6E6` |
| `bright.black` | ANSI 8 | `#A5AEB9` | `#6B7484` | `#A6A6A6` |
| `bright.red` | ANSI 9 | `#FFCEC8` | `#B3262C` | `#FFA8A2` |
| `bright.green` | ANSI 10 | `#9CF5B6` | `#1F8043` | `#9DFFBC` |
| `bright.yellow` | ANSI 11 | `#FFE89A` | `#946800` | `#FFF08A` |
| `bright.blue` | ANSI 12 | `#BEDCFF` | `#2867C8` | `#B3D6FF` |
| `bright.magenta` | ANSI 13 | `#F6CCFF` | `#8A4AB0` | `#FFB8FF` |
| `bright.cyan` | ANSI 14 | `#98F2F5` | `#0D7790` | `#A6F7FF` |
| `bright.white` | ANSI 15 | `#FFFFFF` | `#000000` | `#FFFFFF` |

- **Matching and extra schemes.** `ui.dark`'s matching scheme is `terminal.dark` and `ui.light`'s is `terminal.light`;
  `terminal.scheme = "match"` uses it. Any other key is an extra scheme that users can pick with any theme in either
  appearance, as Slate Cobalt's `high-contrast` (`slate-cobalt/high-contrast`) is.
- **The editor reads the matching scheme.** `terminal.*` references in `editor.dark` resolve in `terminal.dark`, and
  in `editor.light` in `terminal.light`.
- **Light schemes.** Programs print ANSI white and bright white as text and use black as a background, so in a light
  scheme `white` and `bright.white` must still read on the light `bg`; Slate Cobalt Light makes `white` a dim grey and
  `bright.white` black.
- **Colours 16 to 255** of the 256-colour palette are the standard xterm cube and grey ramp in every scheme.

### Terminal-only themes

A file without `ui` (and therefore without `editor`) adds terminal schemes and nothing else: it never appears under
Dark theme or Light theme, and its schemes are listed under Terminal scheme. Its scheme keys are free.

```json
{
  "jasper-theme": 1,
  "name": "Midnight Terminal",
  "terminal": {
    "dark": {
      "name": "Midnight", "bg": "#0B0E14", "fg": "#E6E1CF", "fg.bold": "#FFFFFF", "cursor": "#FFCC66", "selection": "#273747",
      "black": "#1C2433", "red": "#FF6B6B", "green": "#A6E36E", "yellow": "#FFD173",
      "blue": "#73B8FF", "magenta": "#D4A6FF", "cyan": "#5CCFE6", "white": "#C7C7C7",
      "bright.black": "#8A919E", "bright.red": "#FF9999", "bright.green": "#C2F09C", "bright.yellow": "#FFE0A3",
      "bright.blue": "#A3D0FF", "bright.magenta": "#E6C8FF", "bright.cyan": "#99E6F5", "bright.white": "#FFFFFF"
    }
  }
}
```

Saved as `midnight-terminal.json`, its one scheme is `midnight-terminal/dark`, listed as **Midnight**.

## Editor schemes

`editor.dark` and `editor.light` each have exactly these 23 entries, written as [editor values](#editor-values). The
Editor plugin paints with them through RSyntaxTextArea; plugins read them through the SDK
([`Appearance.editorPalette()`](sdk-reference.html#dev.jasper.sdk.ui.Appearance.editorPalette()) and
[`Appearance.syntaxPalette()`](sdk-reference.html#dev.jasper.sdk.ui.Appearance.syntaxPalette())).

**Surfaces** (no style):

| Entry | Paints | Slate Cobalt |
|---|---|---|
| `background` | The text area and its viewport; `@ N%` elsewhere is laid over it, and it cannot take one itself | `terminal.bg` |
| `foreground` | Plain text, and every token without a syntax role | `terminal.fg` |
| `caret` | The caret | `terminal.cursor` |
| `selection` | The background of selected text; in Vim mode also the Visual area, the incremental-search match and, by default, a highlighted yank | `terminal.selection` |
| `currentLine` | The current-line highlight | `hover.overlay @ 5%` |
| `gutter` | The gutter's background, its edge and the fold area | `terminal.bg` |
| `lineNumbers` | Line numbers | `text.tertiary` |

**Syntax roles** (`, italic` and `, bold` allowed; a role without them is plain):

| Entry | Tokens | Slate Cobalt Dark | Slate Cobalt Light |
|---|---|---|---|
| `keyword` | Reserved words and preprocessor directives | `terminal.ansi.magenta` | same |
| `type` | Data types | `terminal.ansi.cyan` | same |
| `function` | Function names | `terminal.ansi.blue` | same |
| `variable` | Tokens the highlighter marks as variables, such as `$name` in shell scripts | `terminal.fg` | same |
| `constant` | Boolean literals | `terminal.ansi.yellow` | same |
| `number` | Integer, floating-point and hexadecimal literals | `terminal.ansi.yellow` | same |
| `string` | Double-quoted strings, character literals and backquoted strings | `terminal.ansi.green` | same |
| `comment` | Line and block comments | `terminal.ansi.bright.black, italic` | `terminal.ansi.white, italic` |
| `doc_comment` | Documentation comments, their tags and their markup | `terminal.ansi.bright.black, italic` | `terminal.ansi.white, italic` |
| `annotation` | Annotations | `terminal.ansi.yellow` | same |
| `operator` | Operators | `text.secondary` | same |
| `punctuation` | Separators: brackets, commas, semicolons | `text.secondary` | same |
| `tag` | Markup tag names and delimiters | `terminal.ansi.red` | same |
| `attribute` | Markup attribute names and values | `terminal.ansi.yellow` | same |
| `regex` | Regular expression literals | `terminal.ansi.cyan` | same |
| `error` | Malformed identifiers, numbers, strings and characters | `terminal.ansi.red` | same |

Referring to the matching terminal scheme keeps the Editor, the terminal and the interface consistent; plain hex is
fine where the Editor needs a colour of its own. On macOS, with a translucent workspace (`ui.workspace.opacity` below
100), the Editor's background and gutter are left clear and the current-line highlight takes the workspace's opacity.

## Contrast and the token rule

**Colours live in theme files.** Jasper's interface code, its kit files and the bundled plugins paint with roles: a
build test fails on a hex colour literal in the production code and text resources of jasper-ui, the application and the
in-repo plugins, outside the built-in theme files, the theme files an in-repo plugin's descriptor lists and the start-up
splash's colours ([the rules the tests enforce](ui-maintenance.md#rules-the-tests-enforce)), and a kit file refuses any
value that looks like a colour. That is why one theme file recolours every window, dialog and plugin view.

**Jasper does not check contrast when it loads a theme.** It repairs some pairs where it paints them, without changing
the role anywhere else: text and status text are raised to 4.5:1 on the surface they are painted on, indicators (status
dots, badges, tinted icons) to 3:1, control edges to 1.5:1 and structural rules to 1.3:1, by moving the colour toward
`text.primary`, or toward black or white, which can shift its hue. The status bar's text and running dot, the rail's
badges, and most of the Enterprise look's chrome and classic controls are painted this way. The tab strip and the
focused header strip keep their L\* distances ([What Jasper paints on top of the roles](#what-jasper-paints-on-top-of-the-roles),
[Derived roles](#derived-roles)). A theme that meets these floors itself is painted exactly as written.

Aim for:

| Pair | Target |
|---|---|
| Every text role on the surfaces it sits on (`text.primary`, `text.secondary`, `text.strong.secondary`, `text.tertiary`, `text.placeholder`, `text.link`, `text.error`, `text.success`) | 4.5:1 |
| `text.onAccent` on `accent` and `accent.hover`; `text.onSelection` on `selection.bg` | 4.5:1 |
| `text.primary` on the matching scheme's `selection`, and the scheme's `fg` on it | 4.5:1 |
| `tab.indicator` on `surface.terminal`; `status.success`, `status.warning`, `status.danger` and the hues on the surfaces they sit on | 3:1 |
| `border.input` on `surface.input`, `border.control` on `surface.control` | 1.5:1 |
| `surface.tabstrip` from `surface.terminal` | 9 L\*, or Jasper moves it |
| Terminal colours on `bg` | 4.5:1, except `black` in a dark scheme, which programs use as a background |
| Editor syntax roles on `background` | 4.5:1; comments and line numbers at least 3:1 |

`tab.indicator.inactive` and `status.idle` are meant to be quieter: in Slate Cobalt Light each is about 2.2:1 on the
surface it sits on (`surface.terminal`, `surface.app`).

## Validation and error messages

### Where problems are reported

| How the theme arrived | Where a problem shows | Form |
|---|---|---|
| A file in the themes folder | The configuration details (click the status bar's **Config warnings** cell) | `<path to the file>: warning: <message>`, or `<path>:<line>:1: warning: <message>` for a JSON syntax error |
| **Import Theme…** | A sheet titled `Could not import <file name>`; nothing is copied | `<message>`, or `line <N>: <message>` |
| A plugin's `themes` | The configuration details, against the plugin's directory | `The <plugin> plugin's theme file <path>: <message>`, or `…<path>, line <N>: <message>`, or `…<path> could not be used: <reason>` (too large or not UTF-8) |
| A setting that names a theme | The configuration details, against `config.toml` and the setting's key | [Themes](themes.md#when-something-is-wrong) lists these |

The message names the entry at fault with a dotted path (`ui.dark.accent`, `editor.light.keyword`). The loader checks in
this order and reports the first problem only: the marker, unknown top-level keys, `name`, `author`, `terminal` and its
schemes, `ui`, `editor`, then each UI variant's roles and its editor scheme.

### JSON syntax

These carry the line number.

| Message | Cause |
|---|---|
| `Expected '{'` | The file does not start with an object (after white space) |
| `Expected '"'` | A key is not a double-quoted string: a comment, a trailing comma, a single-quoted or bare key |
| `Expected ',' or '}'`, `Expected ',' or ']'`, `Expected ':'` | Missing punctuation |
| `Expected a JSON value for "<key>"` | A value that is not JSON, such as `#2F6FDB` without quotes |
| `Duplicate key "<key>"` | The same key twice in one object |
| `Unexpected content after the theme object` | Anything after the closing `}` |
| `Objects and arrays nested more than 8 deep` | Nesting past the limit |
| `A number has no leading zeros`, `Expected a digit`, `Expected a digit after the decimal point`, `Expected a digit in the exponent` | A malformed number |
| `Unterminated string`, `Control character in a string`, `Unknown escape \<c>`, `Short unicode escape`, `Bad unicode escape`, `Unterminated escape` | A malformed string |
| `Unexpected end` | The file is empty or only white space, or ends inside an object or array |

### Top level

| Message | Cause |
|---|---|
| `Not a Jasper theme: "jasper-theme": 1 is missing` | No marker; an IntelliJ theme file gives this ([convert it first](#converting-intellij-and-alacritty-themes)) |
| `jasper-theme: Expected the number 1` | The marker is a string, a decimal or negative |
| `jasper-theme: Format <n> is not one this Jasper reads; expected 1` | Another format number |
| `Unknown entry "<key>"` | A top-level key other than the six |
| `Missing entry "name"`, `name: Expected a non-empty string` | No name, or an empty or blank one |
| `author: Expected a string` | A non-string author |
| `Missing object "terminal"`, `terminal: Expected an object`, `terminal: Expected at least one scheme` | No schemes |
| `ui: Expected an object`, `ui: Expected "dark", "light" or both`, `ui: Unknown variant "<v>"`, `ui.<v>: Expected an object` | A malformed `ui` |
| `ui.<v>: Missing its matching scheme "terminal.<v>"` | A UI variant without the scheme of its key |
| `Missing object "editor"`, `editor: Expected an object` | A UI theme without editor schemes |
| `editor: Missing variant "<v>"`, `editor: Unknown variant "<v>"`, `editor.<v>: Expected an object` | `editor` does not have exactly the variants of `ui` |
| `editor: A theme without ui has no editor schemes` | `editor` in a terminal-only theme |

### UI variant errors

| Message | Cause |
|---|---|
| `ui.<v>: Missing colour "<role>"` | One of the 60 roles is left out |
| `ui.<v>: Unknown colour "<key>"` | A misspelt role, a [role you do not list](#roles-you-do-not-list) or a derived role that is not overridable |
| `ui.<v>.<role>: Expected a colour` | A role's value is not a string |
| `ui.<v>.<role>: Expected #RRGGBB or #RRGGBBAA, got "<text>"` | A malformed colour |
| `ui.<v>.<key>: Expected #RRGGBB, got "<text>"` | An overridable derived role with alpha or another form |

### Scheme errors

| Message | Cause |
|---|---|
| `terminal: Scheme key "<key>" must be lowercase letters, digits and dashes, at most 32 characters` | A bad scheme key |
| `terminal.<k>: Expected an object` | A scheme that is not an object |
| `terminal.<k>: Missing entry "name"`, `terminal.<k>.name: Expected a non-empty string` | A scheme without a usable name |
| `terminal.<k>: Missing colour "<c>"`, `terminal.<k>: Unknown colour "<c>"` | Not exactly the 21 colours |
| `terminal.<k>.<c>: Expected a colour`, `terminal.<k>.<c>: Expected #RRGGBB or #RRGGBBAA, got "<text>"` | A malformed colour |

### Editor errors

| Message | Cause |
|---|---|
| `editor.<v>: Missing entry "<e>"`, `editor.<v>: Unknown entry "<e>"` | Not exactly the 23 entries |
| `editor.<v>.<e>: Expected a reference` | A value that is not a string |
| `editor.<v>.<e>: Expected #RRGGBB or a reference, got "<text>"` | A hex colour with alpha or of the wrong length |
| `editor.<v>.<e>: Unknown reference "<text>"` | A reference to nothing, such as `terminal.ansi.purple` or a derived role |
| `editor.<v>.<e>: Unknown style "<text>"` | A style other than `italic` or `bold`, or a trailing comma (`Unknown style ""`) |
| `editor.<v>.<e>: A surface takes no style` | `, italic` or `, bold` on one of the seven surfaces |
| `editor.<v>.background: The background cannot be laid over itself` | `@ N%` on `background` |
| `editor.<v>.<e>: Expected one share, got "<value>"` | Two `@` |
| `editor.<v>.<e>: Expected a share such as 5%, got "<text>"` | A share that is not a whole number with `%` |
| `editor.<v>.<e>: A share is from 1% to 100%, got <N>%` | A share out of range |

### File, import and plugin errors

| Message | Cause |
|---|---|
| `Theme files are at most 1 MiB.` | The file is too large |
| `A theme file is UTF-8 text.` | The file is not valid UTF-8 |
| `A theme file is named after its id: lowercase letters, digits and dashes, at most 64 characters.` | A themes-folder file whose name is not an id |
| `<Name> is built in; this file is ignored.` | A themes-folder file with a built-in id |
| `<Name> is provided by the <plugin> plugin; this file is ignored.` | A themes-folder file with an id a plugin declares, even while that plugin is off (then the message starts with the id) |
| `Could not read this file: permission denied.` (and other reasons), `Could not list the themes folder: <reason>`, `Could not use this file: <reason>` | The file or the folder could not be read |
| `<Name> is built in and can't be replaced` | Import of a theme whose id is built in |
| `<Name> is provided by the <plugin> plugin and can't be replaced` | Import of a theme whose id a plugin declares |
| `The theme's name has no letters or digits Jasper can use for a file name` | Import of a theme whose name gives no id |
| `<Name> is built in; the <plugin> plugin's file is ignored.` | A plugin theme with a built-in id |
| `<Name> is provided by the <winner> plugin; the <plugin> plugin's file is ignored.` | Two plugins ship the same id ([Shipping themes in a plugin](#shipping-themes-in-a-plugin)) |
| `The <plugin> plugin's theme file <path> is not in its jars.` | A `themes` path that no jar of the plugin holds |
| `The <plugin> plugin's theme file <path> could not be used: <reason>` | A plugin theme file over 1 MiB (`Theme files are at most 1 MiB.`) or not valid UTF-8 |

## Installing and trying a theme

| Way | Where the file goes | Its id |
|---|---|---|
| By hand | `themes/` in Jasper's home, one file per theme, directly in the folder (subfolders are not read) | The file name |
| **Import Theme…** | Copied into `themes/` (the folder is created by the first import) | Derived from `name` |
| A plugin | Inside the plugin's jars ([Shipping themes in a plugin](#shipping-themes-in-a-plugin)) | The file name |

Jasper reads the themes folder at start-up, on Reload Config (File menu, command palette, or **Reload** in Settings ›
Appearance & Behavior › System Settings), when Settings saves, when `config.toml` changes, and after an import or
removal. A file whose text has not changed is not parsed again. Theme files in plugins are read once per session.
[Installed themes](themes.md#installed-themes) describes the folder, and
[Importing and removing themes](themes.md#importing-and-removing-themes) the Import and Remove dialogs.

## Converting IntelliJ and Alacritty themes

`tools/themes/jasper_theme.py`, in a checkout of Jasper, turns themes made for other programs into Jasper theme files.
It needs Python 3.11 or later and nothing else.

```bash
python3 tools/themes/jasper_theme.py Dracula.jar gruvbox_dark.toml -o ~/Desktop/themes
```

| Argument | Meaning |
|---|---|
| `INPUT ...` | One or more files: an Alacritty theme (`.toml`), or an IntelliJ theme (`.theme.json` or another `.json`, a plugin `.jar`, or a `.zip` from the JetBrains Marketplace). Any other suffix is an error. |
| `-o DIR`, `--output DIR` | Where to write; the default is the current directory, created if missing |
| `--force` | Overwrite files that exist |
| `--list` | List the themes in each input and write nothing: `<file>: <Name> (Alacritty terminal scheme)`, or `<file>: <name> (id <id>, Dark)` (or `Light`) for each IntelliJ theme |
| `--theme NAME` | Convert only this theme of a plugin: a name or id that `--list` shows; repeatable |
| `--name NAME` | The display name, which also sets the output file name; only when the run converts exactly one theme |
| `--scheme FILE` | The IntelliJ editor scheme (`.icls` or `.xml`) to use, for a loose `.theme.json` whose scheme is not in a plugin; it applies to every IntelliJ theme in the run |
| `--verbose` | After each IntelliJ theme, print where every UI role came from |

### Alacritty themes

An Alacritty theme becomes a [terminal-only theme](#terminal-only-themes) with one scheme:

- The name is the file name in title case (`gruvbox_dark.toml` is "Gruvbox Dark"), or `--name`.
- The scheme's key is `dark` when its background is darker than its foreground, otherwise `light`.
- It needs a `[colors]` table with `colors.primary.background`, `colors.primary.foreground` and all eight
  `colors.normal` colours. A bright colour that is missing is its normal colour; `fg.bold` is
  `colors.primary.bright_foreground`, else the foreground; `cursor` is `colors.cursor.cursor`, else the foreground;
  `selection` is `colors.selection.background`, else the background mixed 25% toward the foreground. The cursor and
  selection may say `CellForeground` or `CellBackground`.
- Colours are `#rrggbb` or `0xrrggbb`. An `import` is ignored with a warning, so colours from imported files are
  missing.
- The line printed reads `<file>: <Name> (terminal scheme, dark)` (or `light`).

### IntelliJ themes

An IntelliJ theme becomes a UI theme with its one variant, Dark or Light (its `dark` key, or its nearest parent's):

- **Every theme in a plugin is converted**: those its `plugin.xml` declares as `themeProvider`s, else every
  `*.theme.json` in it. `--theme` picks some.
- **Parents.** Its colours are resolved through its `parentTheme` chain: another theme in the same plugin, else one of
  IntelliJ's own themes vendored in `tools/themes/intellij/` (Darcula, IntelliJ, JetBrainsLightTheme, ExperimentalDark,
  ExperimentalLight, ExperimentalLightWithLightHeader, Islands Dark, Islands Light, Islands Darcula,
  JetBrainsHighContrastTheme). An unknown parent is replaced by Darcula or IntelliJ with a warning; a theme without a
  parent stands on Darcula or IntelliJ.
- **UI roles** come from a fixed table of IntelliJ keys, the theme's own first, then its parents'. A role with no
  IntelliJ key follows a rule (it is "derived"). Translucent IntelliJ colours are flattened, except for the four roles
  that carry alpha; `surface.terminal` is always the terminal scheme's `bg` and `hover.overlay` white or black.
- **Islands.** When the plugin also ships an Islands variant of a theme (an Islands theme of the same variant whose
  name is the theme's apart from the word "Islands"), the theme takes its `surface.app` from it, because Jasper's tool
  windows stand on the window chrome as islands do.
- **Title bar.** When `MainToolbar.background` stands at least 5 L\* from `surface.app`, it becomes `surface.titlebar`,
  with `MainToolbar.foreground` (or white or black) as `text.titlebar`.
- **Derived roles.** A theme that sets `ToolWindow.Header.inactiveBackground`, `ToolWindow.Header.background`,
  `TableHeader.background`, `Table.stripeColor`, `Table.hoverBackground`, `Banner.warningBackground` or
  `Banner.warningBorderColor` itself, or through a parent in its plugin, gets the matching override
  (`surface.header`, `surface.header.active`, `surface.tableHeader`, `surface.zebra`, `surface.rowHover`,
  `warning.ground`, `warning.border`).
- **Terminal scheme**, keyed like the variant: the editor scheme's console colours (`CONSOLE_BACKGROUND_KEY`, or the
  text background; `CONSOLE_NORMAL_OUTPUT`, or the text colour; `CARET_COLOR`; `SELECTION_BACKGROUND`; the 16
  `CONSOLE_*_OUTPUT` colours). `fg.bold` is the foreground.
- **Editor scheme**: the scheme the theme's `editorScheme` names (a file in the plugin, a scheme in the plugin, or one
  of IntelliJ's), else Darcula or Default with a warning; `--scheme` overrides it. The surfaces come from `TEXT`,
  `CARET_COLOR`, `SELECTION_BACKGROUND`, `CARET_ROW_COLOR`, `GUTTER_BACKGROUND` and `LINE_NUMBERS_COLOR`, the syntax
  roles from the scheme's attributes (mostly `DEFAULT_*`; `tag` and `attribute` from `HTML_*` and `XML_*`, `regex` from
  `REGEXP_*`, `error` from `ERRORS_ATTRIBUTES`) with their bold and italic; an entry the scheme lacks takes Slate
  Cobalt's reference.
- The theme's `author` is kept.
- The line printed reads, for example, `ocean.json: Ocean (dark): 24 roles from the theme, 9 from Darcula, 27 derived;
  editor 20 of 23 from the scheme`, followed by `; chrome from <Islands theme>` when an Islands variant gave
  `surface.app`.

Archives are read within limits (at most 64 MiB, 10,000 entries, 4 MiB per file, 16 nested jars of at most 32 MiB,
64 themes, 256 MiB inflated), and XML with a DOCTYPE or entities is refused.

### Output and errors

Each theme is written as `<id>.json`, the id derived from its name as Import derives it, through a temporary file, so
the file can go straight into the themes folder or through Import. One line per written theme goes to standard output
(`<file>: <summary>`); warnings and errors go to standard error as `warning: …` and `error: …`. One input or one
theme that fails does not stop the others, and the exit status is 1 when anything failed. The converter refuses:

- a file that exists, without `--force` (`error: <path> exists; use --force to overwrite it`);
- a built-in id (`"<id>" is a built-in Jasper theme; choose another name with --name`);
- two themes that would be written to the same file;
- a name with no letters or digits.

It does not know which ids plugins declare: a theme named "Enterprise" is written as `enterprise.json`, which Import
refuses and the themes folder ignores, because the bundled Enterprise plugin declares that id. A converted file is an
ordinary theme file; edit it by hand as you like.

## Shipping themes in a plugin

A plugin can carry any number of themes, listed in its `plugin.toml`:

```toml
id = "dev.example.harbour"
name = "Harbour Theme"
version = "1.0.0"
entry = "dev.example.harbour.HarbourPlugin"
sdk = ">=0.18.0, <0.19"
description = "The Harbour theme."
themes = ["themes/harbour.json"]
```

- `themes` is an array of paths inside the plugin's jars: `/`-separated segments of lowercase letters, digits, `_`,
  `.` and `-`, none of them `.` or `..`, without a leading or trailing `/`. Each names `<id>.json` with a valid theme
  id, and no id may appear twice. No capability is needed.
- A theme pack is an ordinary plugin: a descriptor with every required key and an entry class that implements
  [`Plugin`](sdk-reference.html#dev.jasper.sdk.plugin.Plugin), public with a public no-argument constructor. Its
  `start` may do nothing:

  ```java
  package dev.example.harbour;

  import dev.jasper.sdk.plugin.Plugin;
  import dev.jasper.sdk.plugin.PluginContext;

  public final class HarbourPlugin implements Plugin {
      public HarbourPlugin() { }

      @Override public void start(PluginContext context) { }
  }
  ```

  [Writing a plugin](plugin-authoring.md) covers the layout, building and distribution; [Plugins](plugins.md) covers
  installing.
- **Read once.** Jasper reads the theme files of every plugin that will load when it starts, and keeps them for the
  whole session. Turning the plugin on or off, or installing a new version of it, takes effect after a restart.
- **Listed** after the built-in themes, by name, captioned "From the *plugin name* plugin" in the Dark theme and Light
  theme dropdowns. Settings cannot remove or replace them.
- **Reserved ids.** Every theme id a discovered plugin declares is reserved, whether the plugin is on or not: no
  themes-folder file or import can take it.
- **While the plugin does not load** (off, waiting for review, or skipped in safe mode, which loads no installed
  plugin and no look), a setting that names one of its themes shows Slate Cobalt in Dark or Jasper Meadow Light in
  Light, and one that names one of its schemes uses `match`, without a warning, and the setting keeps its value for
  when the plugin is back.
- **Two plugins, one id.** A `--plugin-dir` plugin wins over a bundled one, which wins over an installed one; then the
  lowest plugin id. The others' files are ignored with a message.
- A look's kit file may make one of its own plugin's themes the default for either appearance (Enterprise makes
  `enterprise` the default for both). A setting the user saved wins.

## Beyond colours: building a look

A theme changes colours only. The shape of the interface, its window chrome (title bar, menus, tool window stripes,
status bar, command palette or Run window), its controls, metrics, fonts and branding, is a look, which a plugin
provides with a kit file (`presentation` in its descriptor, with the `presentation.provide` capability). A kit file
holds no colours, but it can name its plugin's themes as the defaults ([`[defaults]`](looks.md#defaults)
`"ui.theme.dark"` and `"ui.theme.light"`) and choose which role paints dialog bodies ([`[colors]`](looks.md#colors)
`dialog_surface`: `surface.dialog` or `surface.app`) and the Settings navigation (`navigation_surface`:
`surface.sidebar` or `surface.card`). [Looks](looks.md) is the kit file reference, and [Enterprise](enterprise.md) is a
look and theme shipped together.

## Worked example: Harbour

A complete theme with both variants, their terminal schemes and their editor schemes: a teal accent over blue-green
greys, with Slate Cobalt's lightness relationships. Save it as `harbour.json` in the themes folder
and pick **Harbour** under Dark theme and Light theme; its schemes are **Harbour Dark** (`harbour/dark`) and
**Harbour Light** (`harbour/light`).

```json
{
  "jasper-theme": 1, "name": "Harbour", "author": "Jasper documentation",
  "ui": {
    "dark": {
      "surface.app": "#1F2828", "surface.panel": "#293435", "surface.panel.raised": "#2E3C3D",
      "surface.dialog": "#2E3C3D", "surface.card": "#293435", "surface.sidebar": "#252F30",
      "surface.input": "#1F2828", "surface.field.raised": "#344243", "surface.control": "#364445",
      "surface.control.hover": "#3E4E4F", "surface.control.selected": "#435455", "surface.menu": "#303E3F",
      "surface.terminal": "#273132", "surface.tabstrip": "#384748", "surface.emptyTile": "#364445",
      "surface.scrim": "#080D0D8C",
      "border.window": "#3D4D4E", "border.subtle": "#FFFFFF14", "border.input": "#435455",
      "border.field.raised": "#4E6162", "border.field.raised.hover": "#627677", "border.control": "#4E6162",
      "border.menu": "#4E6162", "border.error": "#F0716B",
      "text.primary": "#F1F5F5", "text.secondary": "#ACBCBD", "text.strong.secondary": "#D7E1E1",
      "text.tertiary": "#94A5A6", "text.placeholder": "#809596", "text.field.raised": "#CCD7D8",
      "text.onAccent": "#FFFFFF", "text.onAccent.secondary": "#FFFFFFB3", "text.onSelection": "#FFFFFF",
      "text.onSelection.secondary": "#FFFFFFB3", "text.link": "#39CBBE", "text.link.hover": "#85DAD0",
      "text.error": "#FF8A83", "text.success": "#52D273",
      "accent": "#057D74", "accent.hover": "#007068", "selection.bg": "#077068",
      "hover.overlay": "#FFFFFF",
      "segment.track": "#1F2828", "segment.selected.bg": "#435455", "toggle.track.off": "#6F8485",
      "toggle.knob": "#FFFFFF",
      "button.secondary.bg": "#364445", "button.secondary.bg.hover": "#3E4E4F", "button.secondary.border": "#4E6162",
      "tab.indicator": "#0DB2A5", "tab.indicator.inactive": "#01746C",
      "status.success": "#52D273", "status.warning": "#F2C55C", "status.danger": "#F07F78",
      "status.idle": "#728788",
      "hue.blue": "#8DB6F5", "hue.green": "#6FDB8C", "hue.amber": "#F2C55C",
      "hue.purple": "#C8A4FF", "hue.neutral": "#CCD7D8"
    },
    "light": {
      "surface.app": "#E7EDED", "surface.panel": "#F4F7F8", "surface.panel.raised": "#EAF0F0",
      "surface.dialog": "#F4F7F8", "surface.card": "#FFFFFF", "surface.sidebar": "#EDF2F2",
      "surface.input": "#FFFFFF", "surface.field.raised": "#FFFFFF", "surface.control": "#FFFFFF",
      "surface.control.hover": "#ECF2F2", "surface.control.selected": "#CFDBDC", "surface.menu": "#FFFFFF",
      "surface.terminal": "#FAFBFB", "surface.tabstrip": "#D7E1E1", "surface.emptyTile": "#D7E1E1",
      "surface.scrim": "#16212240",
      "border.window": "#C2CFCF", "border.subtle": "#0000000F", "border.input": "#C9D5D6",
      "border.field.raised": "#C9D5D6", "border.field.raised.hover": "#A8B9BA", "border.control": "#BFCCCD",
      "border.menu": "#C9D5D6", "border.error": "#C42B31",
      "text.primary": "#162122", "text.secondary": "#526667", "text.strong.secondary": "#2D3E3F",
      "text.tertiary": "#556E6F", "text.placeholder": "#5F7879", "text.field.raised": "#3E5455",
      "text.onAccent": "#FFFFFF", "text.onAccent.secondary": "#FFFFFFBF", "text.onSelection": "#FFFFFF",
      "text.onSelection.secondary": "#FFFFFFBF", "text.link": "#097970", "text.link.hover": "#026059",
      "text.error": "#B3262C", "text.success": "#1E7A46",
      "accent": "#097970", "accent.hover": "#037F76", "selection.bg": "#097970",
      "hover.overlay": "#000000",
      "segment.track": "#E7EDED", "segment.selected.bg": "#FFFFFF", "toggle.track.off": "#BFCCCD",
      "toggle.knob": "#FFFFFF",
      "button.secondary.bg": "#FFFFFF", "button.secondary.bg.hover": "#EEF4F4", "button.secondary.border": "#BFCCCD",
      "tab.indicator": "#097970", "tab.indicator.inactive": "#6CBFB6",
      "status.success": "#1E7A46", "status.warning": "#9E6200", "status.danger": "#C42B31",
      "status.idle": "#93A7A7",
      "hue.blue": "#2563C9", "hue.green": "#1E7A46", "hue.amber": "#9E6200",
      "hue.purple": "#7A3E9D", "hue.neutral": "#526667"
    }
  },
  "terminal": {
    "dark": {
      "name": "Harbour Dark", "bg": "#273132", "fg": "#F1F5F5", "fg.bold": "#FFFFFF", "cursor": "#F1F5F5", "selection": "#076861",
      "black": "#445455", "red": "#FFA49D", "green": "#5FE88A", "yellow": "#FFD75F",
      "blue": "#8FC0FF", "magenta": "#EBA2FF", "cyan": "#4FE0E8", "white": "#D6E0E0",
      "bright.black": "#A0B1B2", "bright.red": "#FFCEC8", "bright.green": "#9CF5B6", "bright.yellow": "#FFE89A",
      "bright.blue": "#BEDCFF", "bright.magenta": "#F6CCFF", "bright.cyan": "#98F2F5", "bright.white": "#FFFFFF"
    },
    "light": {
      "name": "Harbour Light", "bg": "#FAFBFB", "fg": "#162122", "fg.bold": "#000000", "cursor": "#162122", "selection": "#BEE1DC",
      "black": "#162122", "red": "#C42B31", "green": "#1E7A46", "yellow": "#8A6200",
      "blue": "#1F5FBF", "magenta": "#7A3E9D", "cyan": "#0A728E", "white": "#526667",
      "bright.black": "#62797A", "bright.red": "#B3262C", "bright.green": "#1F8043", "bright.yellow": "#946800",
      "bright.blue": "#2867C8", "bright.magenta": "#8A4AB0", "bright.cyan": "#0D7790", "bright.white": "#000000"
    }
  },
  "editor": {
    "dark": {
      "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor", "selection": "terminal.selection",
      "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg", "lineNumbers": "text.tertiary",
      "keyword": "terminal.ansi.magenta, bold", "type": "terminal.ansi.cyan", "function": "terminal.ansi.blue",
      "variable": "terminal.fg", "constant": "terminal.ansi.yellow", "number": "terminal.ansi.yellow",
      "string": "terminal.ansi.green", "comment": "terminal.ansi.bright.black, italic", "doc_comment": "text.tertiary, italic",
      "annotation": "#E6B450", "operator": "text.secondary", "punctuation": "text.secondary",
      "tag": "terminal.ansi.red", "attribute": "terminal.ansi.yellow", "regex": "terminal.ansi.cyan",
      "error": "terminal.ansi.red, bold"
    },
    "light": {
      "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor", "selection": "terminal.selection",
      "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg", "lineNumbers": "text.tertiary",
      "keyword": "terminal.ansi.magenta, bold", "type": "terminal.ansi.cyan", "function": "terminal.ansi.blue",
      "variable": "terminal.fg", "constant": "terminal.ansi.yellow", "number": "terminal.ansi.yellow",
      "string": "terminal.ansi.green", "comment": "terminal.ansi.white, italic", "doc_comment": "text.tertiary, italic",
      "annotation": "#8A5A00", "operator": "text.secondary", "punctuation": "text.secondary",
      "tag": "terminal.ansi.red", "attribute": "terminal.ansi.yellow", "regex": "terminal.ansi.cyan",
      "error": "terminal.ansi.red, bold"
    }
  }
}
```

What it shows: `surface.terminal` equals each matching scheme's `bg`; `hover.overlay` is white in Dark and black in
Light; alpha appears only on the four roles meant for it; the editor refers to the matching scheme, to UI roles and,
for `annotation`, to colours of its own, with `@ N%`, `, bold` and `, italic`; no optional role is listed, so the
title bar is the window chrome and every derived colour follows its recipe.

## Checklist

- [ ] Valid, comment-free JSON in UTF-8, under 1 MiB, saved as `<id>.json` with an id that is not built in or a
      plugin's.
- [ ] `"jasper-theme": 1`, a non-empty `name`, and no top-level keys but the six.
- [ ] Each UI variant lists all 60 roles, none of the eight that follow other roles, the title bar roles only for a
      title bar of its own, and overrides only for the nine overridable derived roles, as `#RRGGBB`.
- [ ] `terminal.dark` for `ui.dark` and `terminal.light` for `ui.light`, each with a `name` and exactly 21 colours.
- [ ] `editor` has exactly the variants of `ui`, each with the 7 surfaces and the 16 syntax roles; no style on a
      surface, no share on `background`.
- [ ] `surface.terminal` equals the matching scheme's `bg`; `hover.overlay` is `#FFFFFF` in Dark and `#000000` in
      Light; `surface.tabstrip` is at least 9 L\* from `surface.terminal`.
- [ ] Alpha only on `surface.scrim`, `border.subtle`, `text.onAccent.secondary` and `text.onSelection.secondary`.
- [ ] The [contrast targets](#contrast-and-the-token-rule) are met, in both variants.
- [ ] Reload Config shows no theme warning, and the theme appears under Dark theme, Light theme or both, with its
      schemes under Terminal scheme.
