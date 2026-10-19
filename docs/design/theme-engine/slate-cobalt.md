# Jasper Colors: Slate Cobalt

The complete color reference for Jasper. It covers:

1. **UI theme:** Slate Cobalt, Dark and Light.
2. **Terminal color schemes:** Slate Cobalt Dark, Slate Cobalt Light, and High Contrast.
3. **Editor color schemes:** Slate Cobalt Dark and Slate Cobalt Light, derived from the colors above.

## Rules for implementers

- Components reference **token keys** such as `accent` or `surface.panel`. They never use hex values directly. Hex values live only in the theme files.
- `#RRGGBB @ N%` means that color at N% opacity over whatever is beneath it.
- Dark and Light share the same keys. The app follows the OS appearance unless the user overrides it.
- In Dark, panels are **lighter** than `surface.app`. In Light, `surface.app` is the darkest surface. Always resolve by key; never assume a direction.
- `hover.overlay` is the only token whose base color flips between themes. It is white in Dark and black in Light, and components apply the alpha.
- The terminal scheme is chosen independently of the UI theme. By default it follows the UI theme: Dark uses Slate Cobalt Dark and Light uses Slate Cobalt Light. High Contrast is a user option and works with either UI theme.

---

# 1. UI theme: Slate Cobalt

## Surfaces

| Token | Dark | Light | Used by |
|---|---|---|---|
| `surface.app` | `#23262C`  | `#E9ECF0`  | Title bar, gutters between panels, status bar, activity rails |
| `surface.panel` | `#2E323A`  | `#F5F7F9`  | Tool window body (Sessions, SFTP, Tunnels, File Servers) |
| `surface.panel.raised` | `#343943`  | `#ECEFF3`  | Panel footer, detail card |
| `surface.dialog` | `#343943`  | `#F5F7F9`  | Dialog, sheet and settings window body |
| `surface.card` | `#2E323A`  | `#FFFFFF`  | Grouped form or settings card |
| `surface.sidebar` | `#292D34`  | `#EEF1F4`  | Window sidebar (Vault, Settings, file browser), window footer |
| `surface.input` | `#23262C`  | `#FFFFFF`  | Text field, search field, password field, inset lists, steppers |
| `surface.field.raised` | `#3A3F49`  | `#FFFFFF`  | Command field in the title bar |
| `surface.control` | `#3C414B`  | `#FFFFFF`  | Dropdown, combobox trigger, picker |
| `surface.control.hover` | `#454B56`  | `#EEF1F5`  | Hover on the above |
| `surface.control.selected` | `#4A515D`  | `#D3D9E1`  | Active activity-rail item, active pill tab |
| `surface.menu` | `#363B45`  | `#FFFFFF`  | Menus, combobox popover, command palette |
| `surface.terminal` | `#2B2F36`  | `#FAFBFC`  | Terminal area, active attached tab |
| `surface.tabstrip` | `#3F444F`  | `#DADFE6`  | Tab strip behind inactive tabs, painted at least 9 L* apart from `surface.terminal` |
| `surface.emptyTile` | `#3C414B`  | `#DADFE6`  | Empty-state icon tile |
| `surface.scrim` | `#0A0C10 @ 55%`  | `#1B1F27 @ 25%`  | Backdrop behind sheets and the command palette |

## Borders

| Token | Dark | Light | Used by |
|---|---|---|---|
| `border.window` | `#444A55`  | `#C6CCD5`  | Window and dialog outer edge |
| `border.subtle` | `#FFFFFF @ 8%`  | `#000000 @ 6%`  | Panel and card edges, row dividers |
| `border.input` | `#4A515D`  | `#CDD3DB`  | Text fields, search fields, steppers, segmented track |
| `border.field.raised` | `#565D6A`  | `#CDD3DB`  | Command field |
| `border.field.raised.hover` | `#6A7280`  | `#AEB6C2`  | Command field hover |
| `border.control` | `#565D6A`  | `#C3CAD3`  | Dropdown, combobox, secondary button |
| `border.menu` | `#565D6A`  | `#CDD3DB`  | Menu and popover edge, separators |
| `border.focus` | = `accent` | = `accent` | Focused field. The focus ring is `accent @ 25%` (Dark ~32%), 3px |
| `border.error` | `#F0716B`  | `#C42B31`  | Invalid field. The ring is `border.error @ 22%` |

## Text

| Token | Dark | Light | Used by |
|---|---|---|---|
| `text.primary` | `#F2F4F7`  | `#1B1F27`  | Titles, values, list names, input text |
| `text.secondary` | `#B2B9C4`  | `#5A6270`  | Form labels, descriptions, default icon tint |
| `text.strong.secondary` | `#DADFE6`  | `#343B47`  | Settings row titles, secondary-button labels, current breadcrumb |
| `text.tertiary` | `#9AA2AE`  | `#5F697A`  | Captions, counts, section labels, row subtitles |
| `text.placeholder` | `#8991A0`  | `#6D7788`  | Input hints, empty picker value |
| `text.field.raised` | `#D0D5DD`  | `#46505E`  | Command field label |
| `text.onAccent` | `#FFFFFF`  | `#FFFFFF`  | Primary button, highlighted menu row, selected result row |
| `text.onAccent.secondary` | `#FFFFFF @ 70%`  | `#FFFFFF @ 75%`  | Subtitles on highlighted menu and result rows |
| `text.onSelection` | `#FFFFFF`  | `#FFFFFF`  | Selected list row, highlighted dropdown entry, selected text |
| `text.onSelection.secondary` | `#FFFFFF @ 70%`  | `#FFFFFF @ 75%`  | Subtitles on selected rows |
| `text.link` | `#84B3FF`  | `#2563C9`  | Link buttons ("New Credential…", "Reset All"), selected checkmark |
| `text.link.hover` | `#A8C9FF`  | `#1D4F9F`  | Link hover |
| `text.error` | `#FF8A83`  | `#B3262C`  | Inline validation |
| `text.success` | `#52D273`  | `#1E7A46`  | "Connected", "Config loaded" |

## Accent and interaction

| Token | Dark | Light | Used by |
|---|---|---|---|
| `accent` | `#2F6FDB`  | `#2563C9`  | Primary button, toggle on, focus, menu and palette highlight, active tab indicator |
| `accent.hover` | `#2563C9`  | `#3470D2`  | Primary button hover |
| `selection.bg` | `#2F5FA8`  | `#2563C9`  | Selected list row (sessions, vault items, files, settings nav) |
| `hover.overlay` | `#FFFFFF` (apply 4–8%)  | `#000000` (apply 4–8%)  | Hover on icon buttons, rows, inactive tabs |
| `segment.track` | `#23262C`  | `#E9ECF0`  | Segmented control track |
| `segment.selected.bg` | `#4A515D`  | `#FFFFFF` (plus a soft shadow)  | Selected segment |
| `toggle.track.off` | `#77808E`  | `#C3CAD3`  | Toggle off |
| `toggle.knob` | `#FFFFFF`  | `#FFFFFF`  | Toggle knob |
| `button.secondary.bg` | `#3C414B`  | `#FFFFFF`  | Secondary button (Cancel, Edit, Browse…) |
| `button.secondary.bg.hover` | `#454B56`  | `#F0F3F7`  | Secondary button hover |
| `button.secondary.border` | `#565D6A`  | `#C3CAD3`  | Secondary button edge |
| `tab.indicator` | `#5B9BF5`  | `#2563C9`  | Line along the top of the active tab (the accent) |
| `tab.indicator.inactive` | `#436596`  | `#90AFE3`  | The same line while the window is not focused (the accent half over the tab) |

## Status

| Token | Dark | Light | Used by |
|---|---|---|---|
| `status.success` | `#52D273`  | `#1E7A46`  | Live or connected dots, running tunnels, match checkmark |
| `status.warning` | `#F2C55C`  | `#9E6200`  | Warnings, restart-required dots, strength "Fair" |
| `status.danger` | `#F07F78`  | `#C42B31`  | Destructive hover, errors, strength "Weak" |
| `status.idle` | `#7A8391`  | `#9AA3B0`  | Disconnected or stopped dots |

## Category hues

Used only for type identity: icon tiles, protocol chips and sidebar icons. Tile and chip backgrounds use the hue at 16–18% in Dark and 12–14% in Light. Text and icons use the hue at full strength.

| Token | Dark | Light | Assigned to |
|---|---|---|---|
| `hue.blue` | `#8DB6F5`  | `#2563C9`  | Sessions and hosts, Login items, HTTP |
| `hue.green` | `#6FDB8C`  | `#1E7A46`  | SSH keys, SFTP, commands, Shell |
| `hue.amber` | `#F2C55C`  | `#9E6200`  | Secure notes, FTP, files, favorite star, Notifications |
| `hue.purple` | `#C8A4FF`  | `#7A3E9D`  | Generate actions, RSYNC, Snippets, Plugins |
| `hue.neutral` | `#D0D5DD`  | `#5A6270`  | "All", uncategorized |

## Shadows (not themed by color)

| Use | Dark | Light |
|---|---|---|
| Primary button | Inset top line `#FFFFFF @ 14%`, plus a 1px drop of `#000000 @ 30%` | Same inset line, plus a 1px drop of `#000000 @ 12%` |
| Popover / menu | `#000000 @ 50%`, 12px offset, 32px blur | `#000000 @ 18%` |
| Window / dialog | `#000000 @ 55–60%`, 30px offset, 80px blur | `#000000 @ 22%` |

Window controls (the macOS traffic lights) are platform-native and are not themed.

---

# 2. Terminal color schemes

| Scheme | Background | Pairs with | Notes |
|---|---|---|---|
| **Slate Cobalt Dark** | `#2B2F36` | UI Dark (default) | Bright, high-legibility ANSI colors |
| **Slate Cobalt Light** | `#FAFBFC` | UI Light (default) | Deep, saturated ANSI colors for a light background |
| **High Contrast** | `#000000` | Either UI theme | White on black, maximum legibility |

All three use the same token keys:

| Token | Purpose |
|---|---|
| `terminal.bg` | Background |
| `terminal.fg` | Default text |
| `terminal.fg.bold` | Bold or emphasized text |
| `terminal.cursor` | Cursor block |
| `terminal.selection` | Selected-text background |
| `terminal.ansi.*` | The 16 ANSI colors: black, red, green, yellow, blue, magenta, cyan, white, and their `bright.*` versions |

## Terminal palettes

| Token | Slate Cobalt Dark | Slate Cobalt Light | High Contrast |
|---|---|---|---|
| `terminal.bg` | `#2B2F36` | `#FAFBFC` | `#000000` |
| `terminal.fg` | `#F2F4F7` | `#1B1F27` | `#FFFFFF` |
| `terminal.fg.bold` | `#FFFFFF` | `#000000` | `#FFFFFF` |
| `terminal.cursor` | `#F2F4F7` | `#1B1F27` | `#FFFFFF` |
| `terminal.selection` | `#3A5A8C` | `#C9DAF2` | `#264F9E` |
| `terminal.ansi.black` | `#4A515C` | `#1B1F27` | `#4D4D4D` |
| `terminal.ansi.red` | `#FFA49D` | `#C42B31` | `#FF7B72` |
| `terminal.ansi.green` | `#5FE88A` | `#1E7A46` | `#3CFF7A` |
| `terminal.ansi.yellow` | `#FFD75F` | `#8A6200` | `#FFE14D` |
| `terminal.ansi.blue` | `#8FC0FF` | `#1F5FBF` | `#7CB8FF` |
| `terminal.ansi.magenta` | `#EBA2FF` | `#7A3E9D` | `#FF8CFF` |
| `terminal.ansi.cyan` | `#4FE0E8` | `#0A728E` | `#4DF0FF` |
| `terminal.ansi.white` | `#D9DEE5` | `#5A6270` | `#E6E6E6` |
| `terminal.ansi.bright.black` | `#A5AEB9` | `#6B7484` | `#A6A6A6` |
| `terminal.ansi.bright.red` | `#FFCEC8` | `#B3262C` | `#FFA8A2` |
| `terminal.ansi.bright.green` | `#9CF5B6` | `#1F8043` | `#9DFFBC` |
| `terminal.ansi.bright.yellow` | `#FFE89A` | `#946800` | `#FFF08A` |
| `terminal.ansi.bright.blue` | `#BEDCFF` | `#2867C8` | `#B3D6FF` |
| `terminal.ansi.bright.magenta` | `#F6CCFF` | `#8A4AB0` | `#FFB8FF` |
| `terminal.ansi.bright.cyan` | `#98F2F5` | `#0D7790` | `#A6F7FF` |
| `terminal.ansi.bright.white` | `#FFFFFF` | `#000000` | `#FFFFFF` |

## Contrast targets

All ratios are measured against each scheme's own background.

| Scheme | Normal colors | Bright colors | Exceptions |
|---|---|---|---|
| Slate Cobalt Dark | ≥ 7:1 | ≥ 9.5:1 | Black is for backgrounds only. Bright black is the dim / comment tone, at about 6.0:1. |
| Slate Cobalt Light | ≥ 4.9:1 | ≥ 4.5:1 | Black and bright white are for text and emphasis. White (`#5A6270`) is the dim tone, at about 5.9:1. |
| High Contrast | ≥ 8:1 | ≥ 12:1 | Black is for backgrounds only. |

## Editor color schemes (derived)

Slate Cobalt Dark and Slate Cobalt Light each include an editor scheme, so the UI, the terminal and the editor match.
They are derived from the Slate Cobalt colors and add no new values: each entry names a token. `terminal.*` resolves in
the variant's own terminal scheme (Slate Cobalt Dark for Dark, Slate Cobalt Light for Light), any other key in the
variant's UI theme, and `key @ N%` is that color at N% over the editor background. The editor always follows the UI
theme; High Contrast has no editor scheme.

| Editor color | Slate Cobalt Dark | Slate Cobalt Light |
|---|---|---|
| Background, gutter | `terminal.bg` | `terminal.bg` |
| Text, variables | `terminal.fg` | `terminal.fg` |
| Caret | `terminal.cursor` | `terminal.cursor` |
| Selection | `terminal.selection` | `terminal.selection` |
| Current line | `hover.overlay @ 5%` | `hover.overlay @ 5%` |
| Line numbers | `text.tertiary` | `text.tertiary` |
| Keywords | `terminal.ansi.magenta` | `terminal.ansi.magenta` |
| Types, regular expressions | `terminal.ansi.cyan` | `terminal.ansi.cyan` |
| Functions | `terminal.ansi.blue` | `terminal.ansi.blue` |
| Strings | `terminal.ansi.green` | `terminal.ansi.green` |
| Numbers, constants, annotations, attributes | `terminal.ansi.yellow` | `terminal.ansi.yellow` |
| Tags, errors | `terminal.ansi.red` | `terminal.ansi.red` |
| Operators, punctuation | `text.secondary` | `text.secondary` |
| Comments, doc comments (italic) | `terminal.ansi.bright.black` | `terminal.ansi.white` |

Against the editor background, every text role is at least 4.5:1 (lowest: Light strings, 5.16:1), comments at least
3:1 (5.99:1 Dark, 5.93:1 Light) and line numbers at least 3:1 (5.22:1 Dark, 5.35:1 Light).

---

# 3. Machine-readable

```json
{
  "jasper-theme": 1, "name": "Slate Cobalt", "author": "Jasper",
  "ui": {
    "dark": {
      "surface.app": "#23262C", "surface.panel": "#2E323A", "surface.panel.raised": "#343943",
      "surface.dialog": "#343943", "surface.card": "#2E323A", "surface.sidebar": "#292D34",
      "surface.input": "#23262C", "surface.field.raised": "#3A3F49", "surface.control": "#3C414B",
      "surface.control.hover": "#454B56", "surface.control.selected": "#4A515D", "surface.menu": "#363B45",
      "surface.terminal": "#2B2F36", "surface.tabstrip": "#3F444F", "surface.emptyTile": "#3C414B",
      "surface.scrim": "#0A0C108C",
      "border.window": "#444A55", "border.subtle": "#FFFFFF14", "border.input": "#4A515D",
      "border.field.raised": "#565D6A", "border.field.raised.hover": "#6A7280", "border.control": "#565D6A",
      "border.menu": "#565D6A", "border.error": "#F0716B",
      "text.primary": "#F2F4F7", "text.secondary": "#B2B9C4", "text.strong.secondary": "#DADFE6",
      "text.tertiary": "#9AA2AE", "text.placeholder": "#8991A0", "text.field.raised": "#D0D5DD",
      "text.onAccent": "#FFFFFF", "text.onAccent.secondary": "#FFFFFFB3",
      "text.onSelection": "#FFFFFF", "text.onSelection.secondary": "#FFFFFFB3", "text.link": "#84B3FF",
      "text.link.hover": "#A8C9FF", "text.error": "#FF8A83", "text.success": "#52D273",
      "accent": "#2F6FDB", "accent.hover": "#2563C9", "selection.bg": "#2F5FA8", "hover.overlay": "#FFFFFF",
      "segment.track": "#23262C", "segment.selected.bg": "#4A515D", "toggle.track.off": "#77808E", "toggle.knob": "#FFFFFF",
      "button.secondary.bg": "#3C414B", "button.secondary.bg.hover": "#454B56", "button.secondary.border": "#565D6A",
      "tab.indicator": "#5B9BF5", "tab.indicator.inactive": "#436596",
      "status.success": "#52D273", "status.warning": "#F2C55C", "status.danger": "#F07F78", "status.idle": "#7A8391",
      "hue.blue": "#8DB6F5", "hue.green": "#6FDB8C", "hue.amber": "#F2C55C", "hue.purple": "#C8A4FF", "hue.neutral": "#D0D5DD"
    },
    "light": {
      "surface.app": "#E9ECF0", "surface.panel": "#F5F7F9", "surface.panel.raised": "#ECEFF3",
      "surface.dialog": "#F5F7F9", "surface.card": "#FFFFFF", "surface.sidebar": "#EEF1F4",
      "surface.input": "#FFFFFF", "surface.field.raised": "#FFFFFF", "surface.control": "#FFFFFF",
      "surface.control.hover": "#EEF1F5", "surface.control.selected": "#D3D9E1", "surface.menu": "#FFFFFF",
      "surface.terminal": "#FAFBFC", "surface.tabstrip": "#DADFE6", "surface.emptyTile": "#DADFE6",
      "surface.scrim": "#1B1F2740",
      "border.window": "#C6CCD5", "border.subtle": "#0000000F", "border.input": "#CDD3DB",
      "border.field.raised": "#CDD3DB", "border.field.raised.hover": "#AEB6C2", "border.control": "#C3CAD3",
      "border.menu": "#CDD3DB", "border.error": "#C42B31",
      "text.primary": "#1B1F27", "text.secondary": "#5A6270", "text.strong.secondary": "#343B47",
      "text.tertiary": "#5F697A", "text.placeholder": "#6D7788", "text.field.raised": "#46505E",
      "text.onAccent": "#FFFFFF", "text.onAccent.secondary": "#FFFFFFBF",
      "text.onSelection": "#FFFFFF", "text.onSelection.secondary": "#FFFFFFBF", "text.link": "#2563C9",
      "text.link.hover": "#1D4F9F", "text.error": "#B3262C", "text.success": "#1E7A46",
      "accent": "#2563C9", "accent.hover": "#3470D2", "selection.bg": "#2563C9", "hover.overlay": "#000000",
      "segment.track": "#E9ECF0", "segment.selected.bg": "#FFFFFF", "toggle.track.off": "#C3CAD3", "toggle.knob": "#FFFFFF",
      "button.secondary.bg": "#FFFFFF", "button.secondary.bg.hover": "#F0F3F7", "button.secondary.border": "#C3CAD3",
      "tab.indicator": "#2563C9", "tab.indicator.inactive": "#90AFE3",
      "status.success": "#1E7A46", "status.warning": "#9E6200", "status.danger": "#C42B31", "status.idle": "#9AA3B0",
      "hue.blue": "#2563C9", "hue.green": "#1E7A46", "hue.amber": "#9E6200", "hue.purple": "#7A3E9D", "hue.neutral": "#5A6270"
    }
  },
  "terminal": {
    "dark": {
      "name": "Slate Cobalt Dark", "bg": "#2B2F36", "fg": "#F2F4F7", "fg.bold": "#FFFFFF", "cursor": "#F2F4F7", "selection": "#3A5A8C",
      "black": "#4A515C", "red": "#FFA49D", "green": "#5FE88A", "yellow": "#FFD75F",
      "blue": "#8FC0FF", "magenta": "#EBA2FF", "cyan": "#4FE0E8", "white": "#D9DEE5",
      "bright.black": "#A5AEB9", "bright.red": "#FFCEC8", "bright.green": "#9CF5B6", "bright.yellow": "#FFE89A",
      "bright.blue": "#BEDCFF", "bright.magenta": "#F6CCFF", "bright.cyan": "#98F2F5", "bright.white": "#FFFFFF"
    },
    "light": {
      "name": "Slate Cobalt Light", "bg": "#FAFBFC", "fg": "#1B1F27", "fg.bold": "#000000", "cursor": "#1B1F27", "selection": "#C9DAF2",
      "black": "#1B1F27", "red": "#C42B31", "green": "#1E7A46", "yellow": "#8A6200",
      "blue": "#1F5FBF", "magenta": "#7A3E9D", "cyan": "#0A728E", "white": "#5A6270",
      "bright.black": "#6B7484", "bright.red": "#B3262C", "bright.green": "#1F8043", "bright.yellow": "#946800",
      "bright.blue": "#2867C8", "bright.magenta": "#8A4AB0", "bright.cyan": "#0D7790", "bright.white": "#000000"
    },
    "high-contrast": {
      "name": "High Contrast", "bg": "#000000", "fg": "#FFFFFF", "fg.bold": "#FFFFFF", "cursor": "#FFFFFF", "selection": "#264F9E",
      "black": "#4D4D4D", "red": "#FF7B72", "green": "#3CFF7A", "yellow": "#FFE14D",
      "blue": "#7CB8FF", "magenta": "#FF8CFF", "cyan": "#4DF0FF", "white": "#E6E6E6",
      "bright.black": "#A6A6A6", "bright.red": "#FFA8A2", "bright.green": "#9DFFBC", "bright.yellow": "#FFF08A",
      "bright.blue": "#B3D6FF", "bright.magenta": "#FFB8FF", "bright.cyan": "#A6F7FF", "bright.white": "#FFFFFF"
    }
  },
  "editor": {
    "dark": {
      "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor", "selection": "terminal.selection",
      "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg", "lineNumbers": "text.tertiary",
      "keyword": "terminal.ansi.magenta", "type": "terminal.ansi.cyan", "function": "terminal.ansi.blue",
      "variable": "terminal.fg", "constant": "terminal.ansi.yellow", "number": "terminal.ansi.yellow",
      "string": "terminal.ansi.green", "comment": "terminal.ansi.bright.black, italic",
      "doc_comment": "terminal.ansi.bright.black, italic", "annotation": "terminal.ansi.yellow",
      "operator": "text.secondary", "punctuation": "text.secondary", "tag": "terminal.ansi.red",
      "attribute": "terminal.ansi.yellow", "regex": "terminal.ansi.cyan", "error": "terminal.ansi.red"
    },
    "light": {
      "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor", "selection": "terminal.selection",
      "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg", "lineNumbers": "text.tertiary",
      "keyword": "terminal.ansi.magenta", "type": "terminal.ansi.cyan", "function": "terminal.ansi.blue",
      "variable": "terminal.fg", "constant": "terminal.ansi.yellow", "number": "terminal.ansi.yellow",
      "string": "terminal.ansi.green", "comment": "terminal.ansi.white, italic",
      "doc_comment": "terminal.ansi.white, italic", "annotation": "terminal.ansi.yellow",
      "operator": "text.secondary", "punctuation": "text.secondary", "tag": "terminal.ansi.red",
      "attribute": "terminal.ansi.yellow", "regex": "terminal.ansi.cyan", "error": "terminal.ansi.red"
    }
  }
}
```

In the JSON, colors with transparency use 8-digit `#RRGGBBAA` hex. For example, `#FFFFFF0F` is white at 6%. The editor object holds the derived editor schemes as token references.

