# Jasper Colors: Slate Steel

The complete color reference for Jasper. It covers:

1. **UI theme:** Slate Steel, Dark and Light.
2. **Terminal color schemes:** Slate Steel Dark, Slate Steel Light, and High Contrast.
3. **Editor color schemes:** Slate Steel Dark and Slate Steel Light, derived from the colors above.

## Rules for implementers

- Components reference **token keys** such as `accent` or `surface.panel`. They never use hex values directly. Hex values live only in the theme files.
- `#RRGGBB @ N%` means that color at N% opacity over whatever is beneath it.
- Dark and Light share the same keys. The app follows the OS appearance unless the user overrides it.
- In Dark, panels are **lighter** than `surface.app`. In Light, `surface.app` is the darkest surface. Always resolve by key; never assume a direction.
- `hover.overlay` is the only token whose base color flips between themes. It is white in Dark and black in Light, and components apply the alpha.
- The terminal scheme is chosen independently of the UI theme. By default it follows the UI theme: Dark uses Slate Steel Dark and Light uses Slate Steel Light. High Contrast is a user option and works with either UI theme.

---

# 1. UI theme: Slate Steel

## Surfaces

| Token | Dark | Light | Used by |
|---|---|---|---|
| `surface.app` | `#1E2127` | `#E4E7EB` | Title bar, gutters between panels, status bar, activity rails |
| `surface.panel` | `#272A31` | `#F5F6F8` | Tool window body (Sessions, SFTP, Tunnels, File Servers) |
| `surface.panel.raised` | `#2D3139` | `#ECEEF1` | Panel footer, detail card |
| `surface.dialog` | `#2D3139` | `#F5F6F8` | Dialog, sheet and settings window body |
| `surface.card` | `#272A31` | `#FFFFFF` | Grouped form or settings card |
| `surface.sidebar` | `#23262C` | `#ECEEF1` | Window sidebar (Vault, Settings, file browser), window footer |
| `surface.input` | `#1F2228` | `#FFFFFF` | Text field, search field, password field, inset lists, steppers |
| `surface.field.raised` | `#30343C` | `#FFFFFF` | Command field in the title bar |
| `surface.control` | `#363B45` | `#FFFFFF` | Dropdown, combobox trigger, picker |
| `surface.control.hover` | `#3E444F` | `#F0F2F5` | Hover on the above |
| `surface.control.selected` | `#3A3F4A` | `#D3D8DE` | Active activity-rail item, active pill tab |
| `surface.menu` | `#30343C` | `#FFFFFF` | Menus, combobox popover, command palette |
| `surface.terminal` | `#23262C` | `#FBFBFC` | Terminal area, active attached tab |
| `surface.tabstrip` | `#1F2228` | `#E9ECEF` | Tab strip behind inactive tabs, painted at least 9 L* darker than `surface.terminal` |
| `surface.emptyTile` | `#343841` | `#E3E6EA` | Empty-state icon tile |
| `surface.scrim` | `#0A0C10 @ 55%` | `#1C2027 @ 25%` | Backdrop behind sheets and the command palette |

## Borders

| Token | Dark | Light | Used by |
|---|---|---|---|
| `border.window` | `#3A3F49` | `#C6CBD2` | Window and dialog outer edge |
| `border.subtle` | `#FFFFFF @ 6%` | `#000000 @ 6%` | Panel and card edges, row dividers |
| `border.input` | `#3B404A` | `#D2D6DC` | Text fields, search fields, steppers, segmented track |
| `border.field.raised` | `#474D59` | `#C3C8D0` | Command field |
| `border.field.raised.hover` | `#58606D` | `#A9AFB9` | Command field hover |
| `border.control` | `#474D59` | `#C3C8D0` | Dropdown, combobox, secondary button |
| `border.menu` | `#4B515D` | `#C9CED5` | Menu and popover edge, separators |
| `border.focus` | = `accent` | = `accent` | Focused field. The focus ring is `accent @ 25%` (Dark ~32%), 3px |
| `border.error` | `#E0605A` | `#C8423A` | Invalid field. The ring is `border.error @ 22%` |

## Text

| Token | Dark | Light | Used by |
|---|---|---|---|
| `text.primary` | `#EFF1F4` | `#1C2027` | Titles, values, list names, input text |
| `text.secondary` | `#A7ADB8` | `#5D6470` | Form labels, descriptions, default icon tint |
| `text.strong.secondary` | `#D1D5DC` | `#383E48` | Settings row titles, secondary-button labels, current breadcrumb |
| `text.tertiary` | `#8B919C` | `#727985` | Captions, counts, section labels, row subtitles |
| `text.placeholder` | `#6D737E` | `#8E95A0` | Input hints, empty picker value |
| `text.field.raised` | `#CDD2DA` | `#4A515C` | Command field label |
| `text.onAccent` | `#FFFFFF` | `#FFFFFF` | Primary button, highlighted menu row, selected result row |
| `text.onAccent.secondary` | `#FFFFFF @ 65%` | `#FFFFFF @ 75%` | Subtitles on highlighted menu and result rows |
| `text.onSelection` | `#FFFFFF` | `#FFFFFF` | Selected list row, highlighted dropdown entry, selected text |
| `text.onSelection.secondary` | `#FFFFFF @ 65%` | `#FFFFFF @ 75%` | Subtitles on selected rows |
| `text.link` | `#8FB4DE` | `#3F6FA8` | Link buttons ("New Credential…", "Reset All"), selected checkmark |
| `text.link.hover` | `#B0CBEA` | `#2F5A8C` | Link hover |
| `text.error` | `#F07F78` | `#B93A33` | Inline validation |
| `text.success` | `#4FD06C` | `#2E9A4C` | "Connected", "Config loaded" |

## Accent and interaction

| Token | Dark | Light | Used by |
|---|---|---|---|
| `accent` | `#4E7DB4` | `#3F6FA8` | Primary button, toggle on, focus, menu and palette highlight, active tab indicator |
| `accent.hover` | `#5C8BC2` | `#4A7BB5` | Primary button hover |
| `selection.bg` | `#2E4664` | `#3F6FA8` | Selected list row (sessions, vault items, files, settings nav) |
| `hover.overlay` | `#FFFFFF` (apply 4–8%) | `#000000` (apply 4–8%) | Hover on icon buttons, rows, inactive tabs |
| `segment.track` | `#1F2228` | `#E4E7EB` | Segmented control track |
| `segment.selected.bg` | `#474D59` | `#FFFFFF` (plus a soft shadow) | Selected segment |
| `toggle.track.off` | `#474D59` | `#C3C8D0` | Toggle off |
| `toggle.knob` | `#FFFFFF` | `#FFFFFF` | Toggle knob |
| `button.secondary.bg` | `#363B45` | `#FFFFFF` | Secondary button (Cancel, Edit, Browse…) |
| `button.secondary.bg.hover` | `#3E444F` | `#F0F2F5` | Secondary button hover |
| `button.secondary.border` | `#474D59` | `#C3C8D0` | Secondary button edge |

## Status

| Token | Dark | Light | Used by |
|---|---|---|---|
| `status.success` | `#4FD06C` | `#2E9A4C` | Live or connected dots, running tunnels, match checkmark |
| `status.warning` | `#F2C55C` | `#A8740F` | Warnings, restart-required dots, strength "Fair" |
| `status.danger` | `#F07F78` | `#C8423A` | Destructive hover, errors, strength "Weak" |
| `status.idle` | `#5B616C` | `#A8AEB7` | Disconnected or stopped dots |

## Category hues

Used only for type identity: icon tiles, protocol chips and sidebar icons. Tile and chip backgrounds use the hue at 16–18% in Dark and 12–14% in Light. Text and icons use the hue at full strength.

| Token | Dark | Light | Assigned to |
|---|---|---|---|
| `hue.blue` | `#94B4D6` | `#3F6FA8` | Sessions and hosts, Login items, HTTP |
| `hue.green` | `#72DE8F` | `#2C8A49` | SSH keys, SFTP, commands, Shell |
| `hue.amber` | `#F2C55C` | `#A8740F` | Secure notes, FTP, files, favorite star, Notifications |
| `hue.purple` | `#C8A4FF` | `#7B53BF` | Generate actions, RSYNC, Snippets, Plugins |
| `hue.neutral` | `#D1D5DC` | `#5D6470` | "All", uncategorized |

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
| **Slate Steel Dark** | `#23262C` | UI Dark (default) | Bright, high-legibility ANSI colors |
| **Slate Steel Light** | `#FBFBFC` | UI Light (default) | Deep, saturated ANSI colors for a light background |
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

| Token | Slate Steel Dark | Slate Steel Light | High Contrast |
|---|---|---|---|
| `terminal.bg` | `#23262C` | `#FBFBFC` | `#000000` |
| `terminal.fg` | `#F4F6F8` | `#1C2027` | `#FFFFFF` |
| `terminal.fg.bold` | `#FFFFFF` | `#000000` | `#FFFFFF` |
| `terminal.cursor` | `#FFFFFF` | `#1C2027` | `#FFFFFF` |
| `terminal.selection` | `#3D5F8A` | `#C7D8EE` | `#264F9E` |
| `terminal.ansi.black` | `#3A3F4A` | `#1C2027` | `#4D4D4D` |
| `terminal.ansi.red` | `#FF928A` | `#C8302C` | `#FF7B72` |
| `terminal.ansi.green` | `#5FE88A` | `#1F7A35` | `#3CFF7A` |
| `terminal.ansi.yellow` | `#FFD75F` | `#8A6200` | `#FFE14D` |
| `terminal.ansi.blue` | `#6CB6FF` | `#1F5FBF` | `#7CB8FF` |
| `terminal.ansi.magenta` | `#E99CFF` | `#9B3FB5` | `#FF8CFF` |
| `terminal.ansi.cyan` | `#4FE0E8` | `#0F7A8A` | `#4DF0FF` |
| `terminal.ansi.white` | `#D9DEE5` | `#5D6470` | `#E6E6E6` |
| `terminal.ansi.bright.black` | `#99A2B1` | `#6B7280` | `#A6A6A6` |
| `terminal.ansi.bright.red` | `#FFC6C0` | `#B8342F` | `#FFA8A2` |
| `terminal.ansi.bright.green` | `#9CF5B6` | `#23853C` | `#9DFFBC` |
| `terminal.ansi.bright.yellow` | `#FFE89A` | `#946800` | `#FFF08A` |
| `terminal.ansi.bright.blue` | `#B2D9FF` | `#2867C8` | `#B3D6FF` |
| `terminal.ansi.bright.magenta` | `#F6C8FF` | `#A847C2` | `#FFB8FF` |
| `terminal.ansi.bright.cyan` | `#98F2F5` | `#127F91` | `#A6F7FF` |
| `terminal.ansi.bright.white` | `#FFFFFF` | `#000000` | `#FFFFFF` |

## Contrast targets

All ratios are measured against each scheme's own background.

| Scheme | Normal colors | Bright colors | Exceptions |
|---|---|---|---|
| Slate Steel Dark | ≥ 7:1 | ≥ 9.5:1 | Black is for backgrounds only. Bright black is the dim / comment tone, at about 5.9:1. |
| Slate Steel Light | ≥ 4.9:1 | ≥ 4.5:1 | Black and bright white are for text and emphasis. White (`#5D6470`) is the dim tone, at about 5.8:1. |
| High Contrast | ≥ 8:1 | ≥ 12:1 | Black is for backgrounds only. |

## Editor color schemes (derived)

Slate Steel Dark and Slate Steel Light each include an editor scheme, so the UI, the terminal and the editor match.
They are derived from the Slate Steel colors and add no new values: each entry names a token. `terminal.*` resolves in
the variant's own terminal scheme (Slate Steel Dark for Dark, Slate Steel Light for Light), any other key in the
variant's UI theme, and `key @ N%` is that color at N% over the editor background. The editor always follows the UI
theme; High Contrast has no editor scheme.

| Editor color | Slate Steel Dark | Slate Steel Light |
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

Against the editor background, every text role is at least 4.5:1 (lowest: Light types, 4.87:1), comments at least
3:1 (5.89:1 Dark, 5.77:1 Light) and line numbers at least 3:1 (4.79:1 Dark, 4.24:1 Light).

---

# 3. Machine-readable

```json
{
  "jasper-theme": 1, "name": "Slate Steel", "author": "Jasper",
  "ui": {
    "dark": {
      "surface.app": "#1E2127", "surface.panel": "#272A31", "surface.panel.raised": "#2D3139",
      "surface.dialog": "#2D3139", "surface.card": "#272A31", "surface.sidebar": "#23262C",
      "surface.input": "#1F2228", "surface.field.raised": "#30343C", "surface.control": "#363B45",
      "surface.control.hover": "#3E444F", "surface.control.selected": "#3A3F4A", "surface.menu": "#30343C",
      "surface.terminal": "#23262C", "surface.tabstrip": "#1F2228", "surface.emptyTile": "#343841",
      "surface.scrim": "#0A0C108C",
      "border.window": "#3A3F49", "border.subtle": "#FFFFFF0F", "border.input": "#3B404A",
      "border.field.raised": "#474D59", "border.field.raised.hover": "#58606D", "border.control": "#474D59",
      "border.menu": "#4B515D", "border.error": "#E0605A",
      "text.primary": "#EFF1F4", "text.secondary": "#A7ADB8", "text.strong.secondary": "#D1D5DC",
      "text.tertiary": "#8B919C", "text.placeholder": "#6D737E", "text.field.raised": "#CDD2DA",
      "text.onAccent": "#FFFFFF", "text.onAccent.secondary": "#FFFFFFA6",
      "text.onSelection": "#FFFFFF", "text.onSelection.secondary": "#FFFFFFA6", "text.link": "#8FB4DE",
      "text.link.hover": "#B0CBEA", "text.error": "#F07F78", "text.success": "#4FD06C",
      "accent": "#4E7DB4", "accent.hover": "#5C8BC2", "selection.bg": "#2E4664", "hover.overlay": "#FFFFFF",
      "segment.track": "#1F2228", "segment.selected.bg": "#474D59", "toggle.track.off": "#474D59", "toggle.knob": "#FFFFFF",
      "button.secondary.bg": "#363B45", "button.secondary.bg.hover": "#3E444F", "button.secondary.border": "#474D59",
      "status.success": "#4FD06C", "status.warning": "#F2C55C", "status.danger": "#F07F78", "status.idle": "#5B616C",
      "hue.blue": "#94B4D6", "hue.green": "#72DE8F", "hue.amber": "#F2C55C", "hue.purple": "#C8A4FF", "hue.neutral": "#D1D5DC"
    },
    "light": {
      "surface.app": "#E4E7EB", "surface.panel": "#F5F6F8", "surface.panel.raised": "#ECEEF1",
      "surface.dialog": "#F5F6F8", "surface.card": "#FFFFFF", "surface.sidebar": "#ECEEF1",
      "surface.input": "#FFFFFF", "surface.field.raised": "#FFFFFF", "surface.control": "#FFFFFF",
      "surface.control.hover": "#F0F2F5", "surface.control.selected": "#D3D8DE", "surface.menu": "#FFFFFF",
      "surface.terminal": "#FBFBFC", "surface.tabstrip": "#E9ECEF", "surface.emptyTile": "#E3E6EA",
      "surface.scrim": "#1C202740",
      "border.window": "#C6CBD2", "border.subtle": "#0000000F", "border.input": "#D2D6DC",
      "border.field.raised": "#C3C8D0", "border.field.raised.hover": "#A9AFB9", "border.control": "#C3C8D0",
      "border.menu": "#C9CED5", "border.error": "#C8423A",
      "text.primary": "#1C2027", "text.secondary": "#5D6470", "text.strong.secondary": "#383E48",
      "text.tertiary": "#727985", "text.placeholder": "#8E95A0", "text.field.raised": "#4A515C",
      "text.onAccent": "#FFFFFF", "text.onAccent.secondary": "#FFFFFFBF",
      "text.onSelection": "#FFFFFF", "text.onSelection.secondary": "#FFFFFFBF", "text.link": "#3F6FA8",
      "text.link.hover": "#2F5A8C", "text.error": "#B93A33", "text.success": "#2E9A4C",
      "accent": "#3F6FA8", "accent.hover": "#4A7BB5", "selection.bg": "#3F6FA8", "hover.overlay": "#000000",
      "segment.track": "#E4E7EB", "segment.selected.bg": "#FFFFFF", "toggle.track.off": "#C3C8D0", "toggle.knob": "#FFFFFF",
      "button.secondary.bg": "#FFFFFF", "button.secondary.bg.hover": "#F0F2F5", "button.secondary.border": "#C3C8D0",
      "status.success": "#2E9A4C", "status.warning": "#A8740F", "status.danger": "#C8423A", "status.idle": "#A8AEB7",
      "hue.blue": "#3F6FA8", "hue.green": "#2C8A49", "hue.amber": "#A8740F", "hue.purple": "#7B53BF", "hue.neutral": "#5D6470"
    }
  },
  "terminal": {
    "dark": {
      "name": "Slate Steel Dark", "bg": "#23262C", "fg": "#F4F6F8", "fg.bold": "#FFFFFF", "cursor": "#FFFFFF", "selection": "#3D5F8A",
      "black": "#3A3F4A", "red": "#FF928A", "green": "#5FE88A", "yellow": "#FFD75F",
      "blue": "#6CB6FF", "magenta": "#E99CFF", "cyan": "#4FE0E8", "white": "#D9DEE5",
      "bright.black": "#99A2B1", "bright.red": "#FFC6C0", "bright.green": "#9CF5B6", "bright.yellow": "#FFE89A",
      "bright.blue": "#B2D9FF", "bright.magenta": "#F6C8FF", "bright.cyan": "#98F2F5", "bright.white": "#FFFFFF"
    },
    "light": {
      "name": "Slate Steel Light", "bg": "#FBFBFC", "fg": "#1C2027", "fg.bold": "#000000", "cursor": "#1C2027", "selection": "#C7D8EE",
      "black": "#1C2027", "red": "#C8302C", "green": "#1F7A35", "yellow": "#8A6200",
      "blue": "#1F5FBF", "magenta": "#9B3FB5", "cyan": "#0F7A8A", "white": "#5D6470",
      "bright.black": "#6B7280", "bright.red": "#B8342F", "bright.green": "#23853C", "bright.yellow": "#946800",
      "bright.blue": "#2867C8", "bright.magenta": "#A847C2", "bright.cyan": "#127F91", "bright.white": "#000000"
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

