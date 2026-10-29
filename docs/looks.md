# Looks

A look is Jasper's presentation: the window chrome (title bar, menus, toolbar row, tool window stripes, tabs, status
bar, command palette, dialogs and motion), the way controls are drawn, the sizes and fonts they use and, optionally,
its own application icon and splash screen. Colours are not part of a look: they come from the [theme](themes.md), and
every theme works under every look.

Jasper has a built-in default look. A plugin can provide another one with a kit file, a declarative TOML file this
page describes. The bundled [Enterprise](enterprise.md) plugin, off by default, is such a look. One look runs per
process: Jasper chooses it while it starts, before the first window, and a change applies after a restart.

## The default look and plugin looks

The default look has the unified title bar (on macOS the title row holds the toolbar; on Linux and Windows Jasper
draws its own title bar, or uses the system's with `window.native_decorations`), the standard menus (in the screen
menu bar on macOS), icon stripes at the window's sides, equal-width tabs, a status bar of slash-separated segments, the
command palette card under the title field, dialogs as each one asks, motion as `ui.animations.enabled` says, and the
platform's interface font at a 13-point body ([the workspace](workspace.md) describes it).

A plugin provides a look by naming a kit file with `presentation` in its `plugin.toml` and declaring the capability
[`presentation.provide`](sdk-reference.html#dev.jasper.sdk.Capabilities.PRESENTATION_PROVIDE). Jasper reads the kit
file and the files it names from the plugin's jars; the plugin needs no code for it. A look plugin can also ship
themes and make them the look's defaults, and give other plugins' settings defaults of its own. Jasper's notices call the default look "the standard look".

## Choosing a look

1. Open Settings › Plugins (**File › Manage Plugins…**).
2. Turn the look's plugin on, or off to go back to the default look
   ([enabling and disabling](plugins.md#enabling-disabling-and-removing)).
3. The banner reads "Restart Jasper to apply your changes."; press **Restart Now** ([restarting](plugins.md#restarting)).

One look is active at a time. Turning a look's plugin on, or approving an installed one in its review, turns off every
other enabled look plugin, and the banner names them: "*names* was turned off: one look can be active at a time." (or
"were turned off"). The review sheet of an installed look plugin lists its capability as "Change Jasper's window
chrome and controls after a restart".

When more than one look plugin could still load (for example one loaded with `--plugin-dir`), Jasper elects one:

- A `--plugin-dir` plugin wins over a bundled one, which wins over an installed one; then the lowest plugin id wins.
- The others are skipped with "the *name* look is active", without a notification.
- A look plugin that would win but requires a losing look plugin cannot load without it, so it is skipped ("requires
  another look's plugin; one look can be active at a time") and the election runs again without it.

Jasper reads the look plugins' files once, while it starts and before it reads `config.toml`, so edits to them also
apply after a restart.
Each look keeps its own tool window layout in the saved window's state in `workspace.toml`: the default
look's under `[window.tool_windows]` and a plugin look's under its `.look."<plugin id>"`
([workspace.toml](configuration.md#workspacetoml)).

`jasper --safe-mode` loads no look plugin, bundled ones included, and uses the default look; their status reads
"safe mode uses the standard look" ([safe mode](plugins.md#safe-mode)).

## When a look does not start

### The attempt record

Jasper records each start with a plugin look in `presentation-attempt.toml` in Jasper home. Just before the first
window shows (or a resident Jasper prepares its hidden window), the look is *pending*; when that window first paints,
it is *proven*. A Quit before then clears the pending mark. It is *failed* when the look could not be installed or its
plugin failed to load or start.

When the record holds a pending or failed mark for exactly the elected look (the same look files and the same Jasper
build), the next start skips that plugin (its details in Settings › Plugins read "Skipped: did not finish starting
with this look last time"), uses the default look and posts a warning in the Plugin problems notifications:

> **Jasper started with its standard look**
>
> The last start with the *name* look did not finish, so Jasper uses its standard look until you try again.

**Try Again** forgets the mark and restarts Jasper. If Jasper cannot restart itself it says "Restart Jasper to try the
look again": "Jasper could not work out how it was started. Quit and reopen it to start with the *name* look." A new
Jasper build, or any change to the look's files, gives the look a fresh try by itself, and so does deleting the file.

| Key in `[providers."<plugin id>"]` | Holds |
|---|---|
| `version` | the plugin's version when the look was tried |
| `digest` | the SHA-256 of the kit file, the plugin's theme files, the font files, the branding icons (smallest first) and the splash art |
| `build` | the Jasper build that tried it |
| `state` | `pending`, `proven` or `failed` |
| `announced` | the digest whose invalid kit file was last reported |

The file starts with `version = 1`. Each change is made under `presentation-attempt.lock`; content Jasper cannot read
counts as empty.

### Other problems

| What you see | Why |
|---|---|
| "*name* was not started", "Presentation invalid: *message*", with **Open Plugins**; the plugin's row reads Skipped | The kit file breaks a [rule](#messages); the message names the key. It is reported once for each version of the look's files. |
| "*name* was not started", "Presentation invalid: presentation: *path* is not in the plugin's jars" | `presentation` names a file the plugin's jars do not hold. |
| "The *name* look is missing some of its files", one line per problem | A [font](#fonts) cannot be used, so its text draws in the platform font, or a `[defaults]` theme did not load, so that setting keeps its core default. Posted at each start while the problem lasts. |
| "*name* was not started", "its look could not be installed; Jasper uses its standard look" | Jasper could not install the look while it started. That session runs the default look under the title bar and menu choice already made, without the look's branding and the plugin's services, and the attempt record marks the look failed. |
| The look's plugin could not be loaded or failed while starting ([when a plugin fails](plugins.md#when-a-plugin-fails)) | That session keeps the look's chrome without the plugin's services; the attempt record marks the look failed, so the next start uses the default look. |

## Writing a look

A look is a plugin ([writing a plugin](plugin-authoring.md)). Its manifest names the kit file and declares the
capability; `presentation` needs `presentation.provide` and the capability needs the key:

```toml
id = "dev.example.harbour"
name = "Harbour"
version = "1.0.0"
entry = "dev.example.harbour.HarbourPlugin"
sdk = ">=0.25.0, <0.26"
capabilities = ["presentation.provide"]
presentation = "presentation/harbour.kit.toml"
themes = ["themes/harbour.json"]
```

`presentation` is a jar path: `/`-separated segments of lowercase letters, digits, `_`, `.` and `-`, none of them `.`
or `..`, without a leading or trailing `/`. The kit file's paths resolve against its own folder, so its fonts, icons
and splash art live in or under that folder. The entry class's `start` may do nothing, as Enterprise's does: other
plugins learn the look through the SDK ([adapting a plugin to a look](#adapting-a-plugin-to-a-look)). Enterprise also
sets `enabled_by_default = false`, which keeps a bundled plugin off until the user turns it on.

To try a look while you write it, start Jasper with `--plugin-dir` pointing at the plugin
([running a plugin in Jasper](plugin-authoring.md#running-a-plugin-in-jasper)): that copy wins the election and needs
no review. An invalid kit file shows its message once per version of the files, and the plugin's details in Settings ›
Plugins keep it ("Skipped: Presentation invalid: …"). A start that stops before the first paint is held by the
[attempt record](#the-attempt-record) until you edit a file or choose **Try Again**.

## The kit file

```toml
kit = 1
base = "default"

[chrome]
status_bar = "cells"

[controls]
toggle = "checkbox"

[metrics]
row_height = 22
```

- TOML 1.0 in UTF-8 (a byte order mark is allowed), at most 64 KiB.
- All or nothing: the first problem refuses the whole file with one message naming the key, and the plugin is skipped.
- `kit` and `base` are required. Every table is optional, and every key left out keeps the base's value; within
  `[type.ui]`, `[type.data]` and `[branding]` every key is required.
- Top-level keys are `kit`, `base`, `chrome`, `controls`, `metrics`, `type`, `colors`, `defaults` and `branding`. Any
  other key or table, at the top or in a table, is refused; a `[defaults."<plugin id>"]` table holds that plugin's own
  setting keys ([defaults for plugins' settings](#defaults-for-plugins-settings)).
- No colour literals anywhere, comments included: `#` followed by exactly 3, 6 or 8 hex digits, `0x` followed by 6, and
  `rgb(` or `rgba(` are refused ("… looks like a colour; colours come from the theme"). Write a comment with a space
  after `#`, so that a word such as `#bed` is not read as a colour.
- Paths are jar paths inside the kit file's folder, with the rules of `presentation`, and must be in the plugin's jars.

| File | Limit |
|---|---|
| Kit file | 64 KiB |
| Each font file | 2 MiB, `.ttf` or `.otf` |
| Each branding icon | 512 KiB, PNG |
| Splash art | 256 KiB, SVG |
| Each theme file the plugin declares | 1 MiB |

### kit and base

| Key | Values | What it does |
|---|---|---|
| `kit` | `1` | The file format. Required, an integer. |
| `base` | `"default"`, `"classic"` | The base the file starts from. Required. |

The base gives every key its starting value (the columns below) and also decides what a kit file cannot set:

- The full size catalogue. The classic base sets 153 of its values differently, the 11 [`[metrics]`](#metrics)
  tokens among them; the others include square window and palette corners and no gutter around the workspace.
- The type table: the default base's is written for a 13-point body, with extra letter spacing on section labels; the
  classic base's for a 12-point body, without it.
- The set of Swing colour keys Jasper fills from the theme; the classic set adds header bands, zebra rows and gradient
  buttons.
- Jasper's own pages: under the classic base the Settings navigation sections collapse and a changed setting's
  **Reset to Default** moves into the row's context menu (the default base shows a Reset button on the row), and the
  Plugins and Keymap pages take their classic layouts.

### chrome

| Key | Values | `default` base | `classic` base | What it changes |
|---|---|---|---|---|
| `title_bar` | `unified`, `native`, `classic` | `unified` | `native` | `unified`: on macOS the title row holds the toolbar; on Linux and Windows Jasper draws the title bar, or the system draws it with `window.native_decorations`. `native`: the system title bar on every platform, on macOS following the theme's darkness; Settings hides `window.native_decorations`. `classic`: IntelliJ Classic's title bar on macOS and Windows, and `native`'s on Linux ([the classic title bar](#the-classic-title-bar)). Under `native` and `classic` the toolbar is a classic row beneath the title (22-pixel icon buttons named by their tooltips under `window.toolbar = "icons"`, each button's icon and then its label under `"icons_and_labels"`), and Settings draws no title row of its own. |
| `menu_bar` | `system`, `window` | `system` | `window` | `system`: the standard menus, in the screen menu bar on macOS. `window`: the window's classic menus: File, Edit, View, then Session and Transfer, the plugins' menus and Tools, each when filled, then Window and Help. Window holds the Tab items and the tool window toggles, which leave View. Under `native` they are a row in each window on every platform, the macOS screen menu bar is off and auxiliary windows have none; under `classic` they are where [the classic title bar](#the-classic-title-bar) puts them. Needs `title_bar = "native"` or `"classic"`. |
| `window_title` | 1 to 120 characters | `"{context}"` | `"{context}"` | A window's title, from the [tokens](#window-title-tokens) below. |
| `tool_windows` | `rails`, `stripes` | `rails` | `stripes` | `rails`: icon stripes as wide as `window.rail_width`. `stripes`: labelled stripes on the sides and a strip along the bottom, each label starting with its tool window's icon, tool windows framed flush under header strips, and a tool window becoming the active one, with the keyboard, when you click in it, open it from its stripe or use its toggle (a menu item or its shortcut); Settings hides `window.rail_width`, and the Notifications panel is called **Event Log** and starts in Bottom Left instead of Right Top ([tool windows](workspace.md#tool-windows-and-the-dock)). |
| `editor_tabs` | `equal`, `classic` | `equal` | `classic` | Equal-width tabs, shown once a window has two tabs, or classic tabs, always shown and as wide as their labels, each with a × (a terminal tab also with a status dot), and a + after the last. |
| `status_bar` | `segments`, `cells` | `segments` | `cells` | Slash-separated segments, or ruled cells. |
| `palette` | `anchored`, `run_window` | `anchored` | `run_window` | The command palette card under the title field, or the Run window, which opens centred across the upper part of the workspace until it has a saved place, with **▶ Run…** leading the status bar ([the Run window](command-palette.md#the-run-window)). |
| `dialogs` | `as_requested`, `classic` | `as_requested` | `classic` | Dialogs as each one asks ([`DialogSpec.Presentation`](sdk-reference.html#dev.jasper.sdk.ui.DialogSpec.Presentation)), or overlay and attached-sheet dialogs drawn inside their window as classic dialogs. |
| `motion` | `standard`, `none` | `standard` | `none` | Motion as `ui.animations.enabled` says, or none in the windows; `none` hides that setting. The [Protean splash](#branding)'s queue follows the setting under either value. |
| `icons` | `new_ui`, `classic` | `new_ui` | `classic` | The workspace chrome's icons: the New UI artwork, or IntelliJ's classic icons. `classic` draws them for add, split, maximize, settings, new window and search in each window's header and status bar, in tool window headers and in plugins' toolbar views (the New UI artwork for the rest, and a panel's or window's body keeps its own), and on a labelled stripe draws IntelliJ's classic tool-window icons for `network`, `folder`, `tunnel`, `download`, `server` and `bell` and any other icon as a monochrome glyph in the label's colour (an identity name such as `more.vertical` keeps its authored art). Under `new_ui` a stripe label draws what the icon rails draw. |
| `toolbar_search` | `field`, `icon` | `field` | `field` | Under a `native` or `classic` title bar: the toolbar's "Search everywhere" field with the palette's shortcut, or IntelliJ Classic's **Search Everywhere** button, an icon-only magnifier before **Settings…** whose tooltip names the palette's shortcut, icon-only in every `window.toolbar` mode. Either opens the palette. `icon` needs `palette = "run_window"` and `title_bar = "native"` or `"classic"`. |

#### The classic title bar

`title_bar = "classic"` gives every window IntelliJ Classic's frame:

| Platform | Title bar | Menus (`menu_bar = "window"`) |
|---|---|---|
| macOS | A 28-point band in the toolbar's colour, seamless with the toolbar row below it, where macOS draws the traffic lights and the window's title, light or dark with the theme. A double-click in it zooms, minimises or does nothing, as System Settings › Desktop & Dock says; full screen removes it. | The screen menu bar, with **About Jasper** and **Settings…** in the application menu. The screen menu bar follows the focused window, and its menus act on that window. Other windows, such as Settings and About, keep the system title bar and show a short File menu in the screen menu bar. |
| Windows | Jasper's 28-pixel title bar in the toolbar's colour, holding the menus and then the window's title, left-aligned and dimmed while the window is inactive, over a 1-pixel rule. As the window narrows, a long title shortens to 60 pixels, then the menus fold into "…" and then ☰. With `window.native_decorations = true` the system title bar instead, after a restart. | In the window's title bar, or a row in it under the system title bar. Other windows and dialogs have Jasper's title bar without menus. |
| Linux | The system title bar, as `native`. | A row in the window; auxiliary windows have none. |

Classic in-window dialogs and overlays start below the title bar. Compact ([view modes](workspace.md#view-modes)) keeps
the system title bar on macOS and Windows, without the band; the macOS menus stay in the screen menu bar and the
Windows ones become a row in the window. Settings hides `window.native_decorations` on macOS and Linux. When the look
cannot be installed while Jasper starts, a macOS or Windows window gets exactly the default look's title bar and
menus.

#### Window title tokens

| Token | Value |
|---|---|
| `{context}` | What the title field shows: a program's terminal title when it set one; else `user@folder` for a local pane or `host:path` for a remote one, followed by " : " and the running command (else the foreground program or the shell); for a plugin tab, its title and host. |
| `{session}` | A plugin session's saved name (such as a Remote host), the host of a pane in a remote folder, or "Local: *shell*"; for a plugin tab, its title. |
| `{directory}` | The remote path, or the local folder with `~` for the home folder; for a plugin tab, its location. |

Any other `{…}`, or a brace outside a token, is refused. A token with nothing to show is left out with the separator
before it (after it, for the first token); a template without tokens is the title as written.

### controls

Each point is drawn one of two ways. The names are SDK [`Nodes`](sdk-reference.html#dev.jasper.sdk.ui.Nodes) values,
so the same descriptors from Jasper and every plugin follow the look ([building plugin UI](plugin-ui.md)).

| Key | `default` base | `classic` base | What it draws |
|---|---|---|---|
| `toggle` | `switch` | `checkbox` | A `Toggle`: a switch or a checkbox; also a secret's reveal and copy controls. |
| `exclusive` | `segments` | `radios` | An `EXCLUSIVE` choice: a segment track or radio buttons. |
| `tabs` | `segments` | `folder` | A `TABS` choice: a segment track or folder tabs. |
| `group` | `card` | `titled_box` | A `Section` with its `Card`: a section label over a card, or a titled box. |
| `form_label` | `plain` | `colon` | `FormRow` labels: plain, or a label column ending in colons. |
| `empty` | `tile` | `text` | An `Empty` state: with its icon tile, or text only. |
| `table` | `plain` | `classic` | A `Table`, and `Properties`: plain, or a header band with zebra rows. |
| `tree` | `plain` | `classic` | A `Tree`: plain, or classic disclosure and category rows. |
| `stepper` | `stepper` | `spinner` | A `STEPPER` or `SLIDER` number field: a stepper, or a spinner. |
| `ordered_list` | `inline` | `side_column` | An `ORDERED` list: commands on each row, or in a side column. |
| `buttons` | `raised` | `classic` | Buttons and dropdowns: raised, or classic gradients; also the text field and separator style. |
| `tag` | `tint` | `boxed` | A `Chip`: a borderless tint, or a boxed uppercase label. |
| `route` | `tiles` | `edged` | A `ROUTE` stack (a tunnel's path): icon tiles, or edged cells. |
| `prompt` | `tile` | `plain` | A `PROMPT` form: an icon tile and title, or the plain classic prompt. |

The points are independent: a default-base look can draw checkboxes, and a classic-base look switches.

### metrics

Integers in logical pixels. Each token sets a group of sizes; a token left out keeps the base's value.

| Key | Range | `default` base | `classic` base | What it sizes |
|---|---|---|---|---|
| `control_height` | 20–28 | 24 | 22 | Fields, buttons, pickers, search fields and setting choices; their vertical padding follows it. |
| `row_height` | 18–28 | 24 | 22 | Rows, menu items, picker and palette result rows, ordered-list rows and Settings navigation rows. |
| `control_radius` | 0–4 | 4 | 3 | Corners of controls, segment tracks, keycaps, chips and ordered lists. |
| `field_radius` | 0–4 | 4 | 2 | Corners of text fields. |
| `group_radius` | 0–6 | 6 | 3 | Corners of cards. |
| `focus_ring` | 1–3 | 3 | 2 | The focus ring's width. |
| `tool_window_header` | 22–32 | 32 | 24 | Tool window and panel headers. |
| `group_padding` | 8–16 | 11 | 12 | Padding inside cards and at the sides of Settings rows. |
| `dependent_indent` | 12–28 | 13 | 20 | The indent of a setting that depends on the one above. |
| `navigation_width` | 180–300 | 236 | 230 | The Settings navigation column. |
| `status_dot` | 5–9 | 6 | 7 | Status dots in rows and tool window headers. |

A token moves each size it sets by the token's difference from the base's own value, never below zero.

### type

| Key | Values | What it does |
|---|---|---|
| `[type].base` | integer, 10–16 | The body size in points the interface draws at while `ui.font.size` is unset; every text style scales with it. The default base draws at 13, the classic base at 12. |
| `[type.ui].family` | 1 to 64 characters | The interface face's family name. |
| `[type.ui].regular`, `.medium`, `.semibold` | font file paths | The interface face at weights 400, 500 and 600. |
| `[type.data].family` | 1 to 64 characters | The data face's family name. |
| `[type.data].regular`, `.medium` | font file paths | The data face at weights 400 and 500. |

Without `[type.ui]` the interface uses the platform's font; without `[type.data]` the data face is the platform's
monospaced font. The data face draws monospaced interface text such as paths, keys and fingerprints (the SDK's MONO
text roles); terminal panes keep `[terminal.font]`.

#### Fonts

Jasper registers the look's font files before the first window. A file it cannot use draws in the platform font, and
the look's "missing some of its files" notice names it:

| Problem line | Cause |
|---|---|
| "*file*: its names cannot be read; that text uses the platform font" | The font's name table cannot be read. |
| "*file*: its family is *x*, not *family*; that text uses the platform font" | The file's typographic family (name ID 16, else ID 1) is not the declared `family`. |
| "*file*: it is not a font Java can read; that text uses the platform font" | The file does not decode. |
| "*file*: Java names it *x*, not *y*; that text uses the platform font" | Java reads a different full name than the file's name table. |
| "*file*: an installed font named *family* hides it; that text uses the platform font" | On macOS, an installed font of the same family hides the look's copy. |

Text that holds a character the look's face has no glyph for, such as macOS modifier symbols or CJK, draws whole in the
platform face.

With the look's interface face, `ui.font.family = "system"` (the default) means that face, and a named family
replaces it; the data face stays. The font size row in Settings › Appearance reads "Use the default font size", and
choosing a size of your own starts from `[type].base`; a look without `[type.ui]` keeps "Use platform font size"
([interface font](configuration.md#interface-font)).

### colors

Two theme roles a look chooses between; the colours themselves are the theme's
([surfaces](creating-themes.md#surfaces)).

| Key | Values | `default` base | `classic` base | What it paints |
|---|---|---|---|---|
| `dialog_surface` | `surface.dialog`, `surface.app` | `surface.dialog` | `surface.app` | Dialog bodies, the Settings page area, the Run window and the surface classic controls are drawn on. |
| `navigation_surface` | `surface.sidebar`, `surface.card` | `surface.sidebar` | `surface.card` | The Settings navigation column. |

### defaults

Defaults for five core settings and, in tables of their own, for plugins' settings. A core key is quoted because it
holds dots: written without quotes, `window.toolbar = "icons"` makes a table named `window`, which is refused. A value
the user saved in `config.toml` or Settings wins; otherwise the look's default applies, and without one the core
default ([look-dependent defaults](configuration.md#look-dependent-defaults)).

| Key | Value | Core default |
|---|---|---|
| `"ui.theme.light"` | a theme id with a Light variant | `jasper-meadow` |
| `"ui.theme.dark"` | a theme id with a Dark variant | `jasper-meadow` |
| `"palette.visible_rows"` | integer, 1–20 | `5` |
| `"window.toolbar_height"` | integer, 28–64 | `30` |
| `"window.toolbar"` | `"icons_and_labels"`, `"icons"` or `"hidden"` | `"icons_and_labels"` |

- A value the setting would not accept, an out-of-range number included, refuses the kit file.
- A theme id must be a built-in theme ([themes](themes.md#built-in-themes)) or one of this plugin's `themes`, and must
  have the variant. An installed theme cannot be a look default.
- When the plugin's theme file named here does not load, the key is left out and the "missing some of its files"
  notice says `[defaults]."<key>": its theme did not load, so the setting keeps its core default`.

#### Defaults for plugins' settings

A `[defaults."<plugin id>"]` table gives defaults to the settings a plugin declares
([declared settings](plugin-authoring.md#declared-settings)), keyed as that plugin's settings file writes its keys, so a
dotted key nests as it does there:

```toml
[defaults."dev.jasper.editor"]
font_family = "IBM Plex Mono"

[defaults."dev.jasper.remote"]
sftp.max_parallel_files = 4
```

- The kit file checks only the shape. The table is named by a plugin id that holds a dot: a lowercase letter, then
  up to 127 lowercase letters, digits, `_`, `.` and `-`, outside the reserved `jasper.` namespace. Each key is a
  setting key (dot-separated parts of letters, digits, `_` and `-`), set once, and each value is a string, a number, a
  boolean or an array.
- Jasper reads each value with the kind of the setting the plugin declares under that key and makes it the setting's
  default. A value the user saved in the plugin's settings file wins; Settings shows the look's value as the default,
  **Reset to Default** returns to it, and choosing the plugin's own default there saves it as the user's value.
- A value the setting's kind rejects, and a key the plugin does not declare, are left out: the plugin's own default
  applies, the plugin starts, and Jasper's log says "*plugin id*: the look's default for *key* is not used: *reason*"
  or "*plugin id*: the look gives a default for *key*, which the plugin does not declare". A table for a plugin that is
  not running is ignored.

### branding

All three keys are required when the table is present.

| Key | Value | What it does |
|---|---|---|
| `product` | 1 to 32 characters | The name the splash screen shows. |
| `icons` | a folder path, or `@jasper` | The folder holding `icon-16.png`, `icon-32.png`, `icon-48.png`, `icon-64.png`, `icon-128.png`, `icon-256.png` and `icon-1024.png`, each a PNG of exactly that many pixels square. They replace Jasper's application icon in every window and in the Dock or taskbar, on every launch. `@jasper` keeps Jasper's own icon, in the windows, the Dock or taskbar and the splash. |
| `splash_art` | an `.svg` path | The art at the top of the splash screen. |

A start that opens a window (not `--background`, `--no-window` or `--safe-mode`) shows a splash screen until
the first Jasper window has painted, or until start-up stops. It names each step as start-up reaches it ("Starting
Java runtime…", "Reading configuration…", "Loading plugin: *name*" for each plugin, "Restoring workspace…", then
"Starting *product* Workstation…", *product* being `[branding] product` or else Protean), and a progress bar along its
bottom edge fills with each step. A splash that cannot be drawn is skipped and logged.

With `[branding]` the 720 × 440 splash is the look's: the art drawn into its top 718 × 250 pixels, the 64-pixel icon (128 on a 2×
screen), the product name in the interface face's semibold, Jasper's version in the data face, the step and the
copyright line. Jasper draws the rest the same for every look and theme: a grey border, a grey rule under the art, a
dark-blue gradient below it with the name in white and the version, step and copyright lines in light blue, and the
progress bar.

The splash art must be an `svg` document without a DOCTYPE, processing instructions, `script`, `foreignObject`,
`animate`, `animateTransform` or `set` elements, `on…` or `base` attributes, links or CSS `url()` other than to `#id`
fragments in the file, `\` or `@` in an attribute value or a `<style>` element, or nesting deeper than 128 elements.

A look without `[branding]`, such as the default look or Enterprise, keeps Jasper's application icon and shows the
672 × 384 Protean splash across interface themes. Its four-row queue uses the resolved plugin names, with the active
step in white and upcoming steps dimmed. Completed rows slide upward and fade as the next rises, unless
`ui.animations.enabled = false`. The actual build line sits below the queue. Startup completion or failure closes it
immediately, including during motion.

### Messages

Each message names the key it is about. A sample:

| Message | Cause |
|---|---|
| `kit file: larger than 64 KiB`, `kit file: not UTF-8 text`, `kit file: not valid TOML: …` | The file as a whole |
| `[theme]: unknown table`, `[metrics].height: unknown key` | A table or key the format does not have |
| `kit: missing; a kit file begins with kit = 1`, `kit: 2 is not a format this Jasper reads; expected 1` | `kit` |
| `base: "modern" is not one of default, classic` | `base` |
| `[chrome].title_bar: "custom" is not one of unified, native, classic` | A value outside a point's values |
| `[chrome].menu_bar: "window" needs [chrome].title_bar = "native" or "classic"` | Window menus under the unified title bar |
| `[chrome].toolbar_search: "icon" needs [chrome].palette = "run_window" and [chrome].title_bar = "native" or "classic"` | The search button without the Run window, or under the unified title bar |
| `[chrome].window_title: unknown token {host}; use {context}, {session} and {directory}` | The title template |
| `[metrics].row_height: 40 is outside 18-28` | A token out of range |
| `[type].base: 18 is not a body size from 10 to 16` | The body size |
| `[type.ui].regular: fonts/plex.woff is not a .ttf or .otf file`, `… is not in the plugin's jars`, `… is larger than 2 MiB` | A font file |
| `[colors].dialog_surface: "surface.card" is not one of surface.dialog, surface.app` | A colour slot |
| `[defaults]."ui.font.size": not a setting a look may default; use "ui.theme.light", "ui.theme.dark", "palette.visible_rows", "window.toolbar_height" or "window.toolbar"` | A setting a look cannot default |
| `[defaults]."ui.theme.light": "ocean" is neither a built-in theme nor one of this plugin's themes`, `… has no Light variant` | A theme default |
| `[defaults]."window": a plugin's table is named by its id, such as "dev.jasper.editor"; quote a dotted setting key, such as "window.toolbar"` | A table whose name has no dot, such as an unquoted core key |
| `[defaults]."jasper.core": not a plugin id` | A table name that is not a plugin id, or is in the `jasper.` namespace |
| `[defaults."dev.jasper.editor"]."font family": not a setting key`, `[defaults."dev.jasper.editor"].font_family: expected a string, number, boolean or array`, `… sets font.size a second time` | A plugin's table |
| `[branding].icons: icons/icon-32.png is 64 × 64 pixels, not 32 × 32` | A branding icon |
| `[branding].splash_art: splash/art.svg is not an SVG Jasper can draw: Active SVG denied` | The splash art |
| `[branding].product: "#1e1e2e" looks like a colour; colours come from the theme`, `kit file: line 3: "#fff" looks like a colour; colours come from the theme` | A colour literal in a value or a comment |

## Themes a look ships

A look plugin lists its theme files under `themes` in `plugin.toml`, like any plugin
([shipping themes in a plugin](creating-themes.md#shipping-themes-in-a-plugin)). Jasper reads them once at start; they
are listed in Settings › Appearance while the plugin loads, are part of the look's identity in the
[attempt record](#the-attempt-record), and are the only plugin themes the kit file's `[defaults]` may name.
[Creating themes](creating-themes.md) describes the theme file.

## Worked example: Enterprise

The bundled Enterprise plugin (`plugins/enterprise`) is data: its entry class's `start` does nothing. Its manifest's
look keys (beside `id`, `name`, `version`, `entry` and `sdk`, as in the Harbour example above):

```toml
capabilities = ["presentation.provide"]
presentation = "presentation/enterprise.kit.toml"
themes = ["themes/enterprise.json"]
enabled_by_default = false
```

Its kit file, `presentation/enterprise.kit.toml`, names every core key, so it is also a complete template:

```toml
kit = 1
base = "classic"

[chrome]
title_bar = "classic"
menu_bar = "window"
window_title = "Workstation — {session} — {directory}"
tool_windows = "stripes"
editor_tabs = "classic"
status_bar = "cells"
palette = "run_window"
dialogs = "classic"
motion = "none"
icons = "classic"
toolbar_search = "icon"

[controls]
toggle = "checkbox"
exclusive = "radios"
tabs = "folder"
group = "titled_box"
form_label = "colon"
empty = "text"
table = "classic"
tree = "classic"
stepper = "spinner"
ordered_list = "side_column"
buttons = "classic"
tag = "boxed"
route = "edged"
prompt = "plain"

[metrics]
control_height = 22
row_height = 22
control_radius = 3
field_radius = 2
group_radius = 3
focus_ring = 2
tool_window_header = 24
group_padding = 12
dependent_indent = 20
navigation_width = 230
status_dot = 7

[type]
base = 12

[type.ui]
family = "IBM Plex Sans"
regular = "fonts/ibm-plex-sans-regular.ttf"
medium = "fonts/ibm-plex-sans-medium.ttf"
semibold = "fonts/ibm-plex-sans-semibold.ttf"

[type.data]
family = "IBM Plex Mono"
regular = "fonts/ibm-plex-mono-regular.ttf"
medium = "fonts/ibm-plex-mono-medium.ttf"

[colors]
dialog_surface = "surface.app"
navigation_surface = "surface.card"

[defaults]
"ui.theme.light" = "jasper-meadow"
"ui.theme.dark" = "jasper-meadow"
"palette.visible_rows" = 8
"window.toolbar_height" = 28
"window.toolbar" = "icons"

[defaults."dev.jasper.editor"]
font_family = "IBM Plex Mono"
```

- `base = "classic"`, and `[chrome]`, `[controls]`, `[metrics]`, `[colors]` and `[type] base` restate the classic
  base's values, except `title_bar` (`classic` for the base's `native`), `window_title` and `toolbar_search` (`icon`
  for the base's `field`).
- The fonts sit in `presentation/fonts/`, inside the kit file's folder. There is no `[branding]`, so the look keeps
  Jasper's application icon and Protean splash.
- `[defaults]` makes Jasper Meadow (`jasper-meadow`) the theme for both appearances, as the core defaults do, shows 8
  Run window rows, makes the toolbar 28 pixels high, IntelliJ's, and shows its buttons as icons only; its Editor table
  draws the Editor's documents in IBM Plex Mono, the look's data face.

[The Enterprise look](enterprise.md) describes the result.

## Adapting a plugin to a look

Plugin views built from SDK `Nodes` follow the active look with no work: the same descriptors draw switches or
checkboxes, cards or titled boxes, as the kit file says, and dialogs, menus and tool windows go where the look puts them
([compositions](plugin-ui.md#compositions)).

For more than that, a plugin asks which look is running:
[`context.appearance().presentation()`](sdk-reference.html#dev.jasper.sdk.ui.Appearance.presentation()) returns a
[`Presentation`](sdk-reference.html#dev.jasper.sdk.ui.Presentation). It says whether the look is classic (its kit's
`base`), its icon set, whether tool windows are reached by rails or labelled stripes, the title bar and where the menus
are on this platform, whether the toolbar offers Search Everywhere as a field or an icon, the look's interface and
data faces, its body size and its default toolbar height. These are the look's own facts: the user's font, toolbar,
window decoration and view mode settings are not reported. The look changes only at a restart, so the answer is the
same for every plugin for the whole session and can be read once in `start`, from any thread.

Remote, File Server and Credential Vault compose their classic layouts when `classic()` is true
([classic layouts](enterprise.md#classic-layouts-in-remote-file-server-and-credential-vault)), so they follow any
classic look, not only Enterprise. [Adapting to the look](plugin-authoring.md#adapting-to-the-look) has the example
and every value under both bundled looks, and the testkit's `FakePluginHost.setPresentation` (with
`FakePresentations.STANDARD` or `CLASSIC`) tests both; `setKitDefault` and `lookDefaultProblems` test a look's
[defaults for your settings](#defaults-for-plugins-settings).
