# Themes

Jasper's default theme is **Slate Steel**, in Dark and Light, with three terminal colour schemes: **Slate Steel
Dark**, **Slate Steel Light** and **High Contrast**. Its colours are defined once, in
[the Slate Steel colour reference](design/theme-engine/slate-steel.md), and bundled as `slate-steel.json`. Catppuccin's
four flavours are built in too, converted from [its IntelliJ plugin](https://github.com/catppuccin/jetbrains) 3.6.1:
**Catppuccin Latte** (Light), **Catppuccin Frappé**, **Catppuccin Macchiato** and **Catppuccin Mocha** (Dark), each with
its own terminal scheme. Other themes are Jasper theme files in the themes folder: a UI theme recolours the whole application and brings its own terminal and
editor schemes; a terminal-only theme adds terminal schemes.

## Choosing the appearance, the themes and the terminal scheme

```toml
[ui]
appearance = "system" # or "dark", "light"

[ui.theme]
dark = "slate-steel"  # the theme the Dark appearance shows
light = "slate-steel" # the theme the Light appearance shows

[terminal]
scheme = "match" # or a scheme id such as "slate-steel/high-contrast"
```

`ui.appearance` (`system`, `dark`, `light`) chooses Dark or Light, or follows the operating system's light or dark
setting live; with `system`, Jasper uses Dark when it cannot read the operating system's setting, and an
operating-system change never writes `config.toml`. `ui.theme.dark` and `ui.theme.light` name, by id, the theme each
appearance shows: `slate-steel`, another built-in theme's id (`catppuccin-latte`, `catppuccin-frappe`,
`catppuccin-macchiato`, `catppuccin-mocha`), or an installed theme's id. A theme has a Dark variant, a Light variant or both; with
`system`, Jasper switches between the two picks as the operating system does.

`terminal.scheme` colours the terminal panes: `match` uses the shown theme's own scheme, and a scheme id,
`<theme id>/<scheme key>`, chooses one whatever theme is shown: `slate-steel/dark`, `slate-steel/light`,
`slate-steel/high-contrast`, a Catppuccin flavour's scheme such as `catppuccin-mocha/dark`, or any installed theme's
scheme, such as `gruvbox-dark/dark`.

Settings › Appearance & Behavior › Appearance has the Dark, Light and Match System tiles, each previewing the theme
picked for it, the **Dark theme** and **Light theme** dropdowns, and the **Terminal scheme** dropdown, whose first
entry, **Same as UI theme**, is `match`. A gear beside each dropdown imports and removes themes (below). Terminal › Font & Colors links there, and the command palette's **Themes…**
opens it; the palette's **Terminal: …** commands choose a scheme. Choices stay staged until Apply or OK.

## Installed themes

The themes folder is `themes/` in Jasper's home, next to `config.toml`; Settings imports into it (below) and files can
be copied in by hand. It holds one Jasper theme file per theme, named after the theme's id: lowercase letters and digits in dash-separated words, at most 64 characters, so
`dracula.json` is the theme `dracula`. Jasper reads the folder at start-up, on Reload config, when Settings saves and
when `config.toml` changes; a theme changed on disk is shown again then. A file named after a built-in theme's id,
such as `slate-steel.json` or `catppuccin-mocha.json`, is ignored. Dropdowns list the built-in themes first, Slate Steel
then the Catppuccin flavours, then the installed themes by name; two themes with the same name show their ids.

## What the terminal scheme colours

A scheme colours only the terminal text area: background, text, bold text, cursor, selection and the 16 ANSI colours.
Tabs, the tab strip, the area around panes, the editor, panels and dialogs stay on the shown theme. With `match` the
text area and the active tab meet seamlessly; with High Contrast the text area is black and the tab keeps the theme's
terminal surface. Inactive tabs take the theme's `surface.tabstrip`, darkened when it is too close to `surface.terminal`
to tell the active tab apart (lightened when the terminal surface is near black). Workspace opacity and blur apply to the
terminal area. The editor always follows the shown theme's
editor scheme.

## Importing and removing themes

The gear beside the **Dark theme**, **Light theme** and **Terminal scheme** dropdowns on Settings › Appearance &
Behavior › Appearance has **Import Theme…** and **Remove** for the theme that dropdown shows. Both act at once, outside
Apply; a failure is shown in a sheet.

- **Import Theme…** opens Jasper's file chooser for a `.json` file, checks it and copies it into the themes folder as
  `<id>.json`, the id coming from the theme's name ("Gruvbox Dark" is `gruvbox-dark`). The gear's dropdown then picks
  it: the Dark theme gear its Dark variant, the Light theme gear its Light variant, the Terminal scheme gear its first
  scheme; the pick stays staged until Apply or OK. A theme with nothing for that dropdown is still imported, and a sheet
  says where to pick it ("Gruvbox Dark is a terminal scheme; pick it under Terminal scheme."). A file Jasper cannot use is
  not copied, and the sheet says why, for example `ui.dark: Missing colour "accent"`. Importing a theme whose id an
  imported theme already has asks before replacing it; a built-in theme cannot be replaced.
- **Remove** removes the theme the dropdown shows; for the Terminal scheme gear, the theme the scheme belongs to. It is
  unavailable for a built-in theme and Same as UI theme. It asks first and says what will change. The file is deleted, and
  every setting that names the theme or one of its schemes, saved or staged, returns to its default (`slate-steel` or
  `match`): saved ones are written at once, and other staged changes stay staged. If the file cannot be deleted,
  nothing changes and the sheet says why.

## Converting Alacritty and IntelliJ themes

`tools/themes/jasper_theme.py` (in a checkout of Jasper; Python 3.11 or later and nothing else) turns themes made for
other programs into Jasper theme files:

```bash
python3 tools/themes/jasper_theme.py Dracula.jar gruvbox_dark.toml -o ~/Desktop/themes
```

- An **Alacritty** theme (`.toml`) becomes a terminal-only theme: one scheme, named after the file
  (`gruvbox_dark.toml` is "Gruvbox Dark") and keyed `dark` when its background is darker than its foreground, `light`
  otherwise.
- An **IntelliJ** theme (a `.theme.json`, a plugin `.jar`, or a `.zip` downloaded from the JetBrains Marketplace)
  becomes a UI theme with its one variant, Dark or Light: its colours resolved through its parent themes (IntelliJ's
  own, in `tools/themes/intellij/`), its console colours as its terminal scheme and its editor scheme's colours for the
  Editor. Every theme in a plugin is written; `--list` shows them and `--theme NAME` (a name or id that `--list`
  shows; it can be repeated) converts only those.
- `--name` sets the display name (it needs exactly one converted theme), `--scheme FILE` gives a loose `.theme.json`
  its editor scheme, `--force` overwrites existing files and `--verbose` prints where each UI role came from (IntelliJ
  themes).

Each theme is written as `<id>.json`, its name lower-cased with dashes, so the file can go straight into the themes
folder. A conversion prints a summary such as `ocean.json: Ocean (dark): 24 roles from the theme, 8 from Darcula, 24
derived; editor 20 of 23 from the scheme`; "derived" roles had no IntelliJ key and follow a rule instead.

## The Jasper theme format

A Jasper theme is one UTF-8 JSON file; `slate-steel.json` is the complete example.

```json
{
  "jasper-theme": 1,
  "name": "Dracula",
  "author": "Dracula Theme",
  "ui": { "dark": { "surface.app": "#21222C", "surface.panel": "#282A36" } },
  "terminal": { "dark": { "name": "Dracula", "bg": "#282A36", "fg": "#F8F8F2" } },
  "editor": { "dark": { "keyword": "#FF79C6, bold", "comment": "#6272A4, italic", "gutter": "terminal.bg" } }
}
```

(Each object is shortened here; a real file lists every entry.)

- `jasper-theme` is `1`. `name` is required; `author` is optional.
- `ui` holds `dark`, `light` or both. Each lists every role `slate-steel.json` lists, as `#RRGGBB` or `#RRGGBBAA`.
  `border.focus`, `surface.status`, `border.identity` and the `hue.*.surface` roles are not listed: they are `accent`,
  `surface.app`, `border.control` and each hue.
- `terminal` holds one or more schemes, keyed by lowercase words with dashes (at most 32 characters). Each has a
  `name` and exactly 21 colours: `bg`, `fg`, `fg.bold`, `cursor`, `selection`, `black`, `red`, `green`, `yellow`,
  `blue`, `magenta`, `cyan`, `white` and their `bright.` forms. A scheme's id is `<theme id>/<key>`.
- A UI variant's own scheme, the one `match` uses, is the scheme with its key: `ui.dark` needs `terminal.dark` and
  `ui.light` needs `terminal.light`. Other keys are extra schemes, such as Slate Steel's `high-contrast`.
- `editor` has one entry per UI variant: `background`, `foreground`, `caret`, `selection`, `currentLine`, `gutter`,
  `lineNumbers` and every syntax role (`keyword`, `type`, `function`, `variable`, `constant`, `number`, `string`,
  `comment`, `doc_comment`, `annotation`, `operator`, `punctuation`, `tag`, `attribute`, `regex`, `error`). A value is a
  colour, `#RRGGBB`, or a reference: `terminal.bg`, `terminal.fg`, `terminal.fg.bold`, `terminal.cursor`,
  `terminal.selection`, `terminal.ansi.<colour>`, `terminal.ansi.bright.<colour>` (the variant's own scheme) or a role
  such as `text.secondary`. `@ N%` lays it over the background at N per cent (not for `background` itself), and
  `, italic` and `, bold` style a syntax role.
- A file without `ui` (and so without `editor`) is a terminal-only theme.
- Anything else, anything missing and any malformed colour stops the file from loading.

## The token rule

Every colour in Jasper's interface code is a semantic role (`surface.panel`, `text.primary`, `accent`, …) read from the
active variant. A test fails on any hex colour literal (`#RRGGBB`, `0xRRGGBB` given to a colour constructor) in the
production code or text resources of jasper-ui, the app and the plugins outside the built-in theme files. Outside that scan
are SVG artwork (icons are painted as drawn), the terminal library's default palettes for embedders that set none, the
desk buddy's own artwork, decimal component literals (transparent fills, the editor's vim sneak highlight) and the
black and white that shadows and inset lines are painted in. A role that carries opacity (`surface.scrim`,
`border.subtle`, `text.onAccent.secondary`) is painted as given; treatments such as focus rings, hovers and tile tints
are kit metrics applied to a role. Shadows are black at the strengths the colour reference gives. Plugins receive the
same roles through the SDK. Installed themes live in the themes folder, outside the scan.

## When something is wrong

- A value that is not a valid `ui.appearance`, theme id or scheme id is reported ("Use …; using the default.") and
  the default is used (`system`, `slate-steel`, `match`).
- A theme file that cannot be used is skipped and reported with the configuration's warnings, naming the file, the
  line when known and the entry, for example
  `/Users/you/.config/jasper/themes/dracula.json: warning: ui.dark: Missing colour "accent"`.
- A theme pick that names a theme that is not installed, or one without that variant, and a scheme that is not
  installed, are reported on their setting and replaced by the default until the theme is installed again.
- A removed setting (`terminal.colors`, `[colors]`) is reported as an unknown setting and ignored.
- If a theme cannot be installed, start-up uses Slate Steel Dark and a reload keeps the current appearance; Reload
  config retries.

## Native acceptance for this upgrade (user-run)

On a Mac, check Dark, Light and Match System (switch the operating system's appearance with Settings and a sheet
open), each terminal scheme including High Contrast, and compare the mocked pages at 100%; the
[acceptance record](design/theme-engine/acceptance.md) lists every check. With an installed theme, also check a Dark
theme pick other than Slate Steel under Match System, and an installed terminal scheme on Slate Steel.
