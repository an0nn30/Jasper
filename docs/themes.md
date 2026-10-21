# Themes

A theme colours Jasper's interface, its terminal panes and the Editor; the shape of the interface is the
[look](looks.md), and every theme works under every look. You choose one theme for the Dark appearance and one for the
Light appearance, and the terminal can follow the theme on show or use any installed terminal scheme. Themes are built
in, come with plugins, or are Jasper theme files you install. [Creating themes](creating-themes.md) describes the theme
file and how to make one.

## Built-in themes

| Theme | Id | Variants | Terminal schemes |
|---|---|---|---|
| Slate Cobalt | `slate-cobalt` | Dark, Light | Slate Cobalt Dark (`slate-cobalt/dark`), Slate Cobalt Light (`slate-cobalt/light`), High Contrast (`slate-cobalt/high-contrast`) |
| Catppuccin Latte | `catppuccin-latte` | Light | Catppuccin Latte (`catppuccin-latte/light`) |
| Catppuccin Frappé | `catppuccin-frappe` | Dark | Catppuccin Frappé (`catppuccin-frappe/dark`) |
| Catppuccin Macchiato | `catppuccin-macchiato` | Dark | Catppuccin Macchiato (`catppuccin-macchiato/dark`) |
| Catppuccin Mocha | `catppuccin-mocha` | Dark | Catppuccin Mocha (`catppuccin-mocha/dark`) |
| Ubuntu Yaru | `ubuntu-yaru` | Dark, Light | Ubuntu Yaru Dark (`ubuntu-yaru/dark`), Ubuntu Yaru Light (`ubuntu-yaru/light`) |
| Jasper Meadow (the default theme) | `jasper-meadow` | Dark, Light | Jasper Meadow Dark (`jasper-meadow/dark`), Jasper Meadow Light (`jasper-meadow/light`) |

- **Slate Cobalt**'s colours are defined in [the Slate Cobalt colour reference](design/theme-engine/slate-cobalt.md).
  High Contrast is a terminal scheme only, white on black, and can be used with any theme.
- **Catppuccin**'s four flavours are converted from [its IntelliJ plugin](https://github.com/catppuccin/jetbrains)
  3.6.1.
- **Ubuntu Yaru** is adapted from Ubuntu 26.04's Yaru, with a title bar of its own in each variant;
  [its colour reference](design/theme-engine/ubuntu-yaru.md) records the source and the mapping.
- **Jasper Meadow** pairs warm cream and olive surfaces with moss-green accents in Light; Dark uses moonlit blue-green surfaces, silver sage accents and warm ivory text.

Each theme with a UI variant brings its own Editor colours for that variant.

## Themes from plugins

A plugin can ship themes. They are listed after the built-in themes, by name, captioned "From the *plugin name*
plugin", and Settings can neither remove nor replace them. They are read when Jasper starts, so turning the plugin on
or off takes effect after a restart. While the plugin does not load, a setting that names one of its themes shows
Jasper Meadow in either appearance (and one that names one of its schemes uses Same as UI theme)
without a warning, and keeps the name, so turning the plugin on again restores it.

The bundled Enterprise plugin, off by default, ships **Enterprise** (`enterprise`), in Light and Dark (ports of
IntelliJ's IntelliJ Light and New UI Dark) with Editor colours for each, and the schemes Enterprise Light
(`enterprise/light`) and Enterprise Dark (`enterprise/dark`). While the Enterprise look is active, `enterprise` is the
default theme of both appearances ([the Enterprise theme](enterprise.md#the-enterprise-theme)).

## Choosing the appearance, the themes and the terminal scheme

```toml
[ui]
appearance = "system" # or "dark", "light"

[ui.theme]
dark = "slate-cobalt"   # the theme the Dark appearance shows
light = "jasper-meadow" # the theme the Light appearance shows

[terminal]
scheme = "match" # or a scheme id such as "slate-cobalt/high-contrast"
```

| Setting | Default | Values |
|---|---|---|
| `ui.appearance` | `"system"` | `"system"` follows the operating system's light or dark setting, live; only macOS reports it, so on Linux and Windows `"system"` shows Dark. `"dark"` and `"light"` choose one. A change of the system's setting never writes `config.toml`. |
| `ui.theme.dark` | `"slate-cobalt"`, or the active look's default (`"enterprise"` under the Enterprise look) | The id of a theme with a Dark variant: built in, from a plugin, or installed |
| `ui.theme.light` | `"jasper-meadow"`, or the active look's default (`"enterprise"` under the Enterprise look) | The id of a theme with a Light variant |
| `terminal.scheme` | `"match"` | `"match"` uses the shown theme's own scheme, so it follows the appearance; a scheme id, `<theme id>/<scheme key>`, chooses one in either appearance |

All four apply live in every window.

**Settings › Appearance & Behavior › Appearance** holds them:

- The **Dark**, **Light** and **Match System** tiles choose `ui.appearance`. Each previews the theme picked for it and
  names it; Match System previews both and says "System appearance: Dark" (or Light), or "System appearance
  unavailable; using Dark".
- **Dark theme** and **Light theme** list the themes that have that variant. A saved pick that is not available shows
  as *name* (no Dark variant), *name* (no Light variant) or *id* (not installed).
- **Terminal scheme** (Colors for terminal panes) lists **Same as UI theme** (`match`) and then every installed
  scheme; a saved scheme that is not installed shows as *id* (not installed).
- A gear beside each dropdown (a ⋯ under the Enterprise look), tooltip "Import or remove themes", imports and removes
  themes ([below](#importing-and-removing-themes)).
- **Reset** (Reset to Default under the Enterprise look) returns the appearance and both themes to their defaults.
- Choices stay staged until **Apply** or **OK**.

Terminal › Font & Colors has **Choose Scheme…**, which opens this page. In the command palette (Cmd+Shift+P on macOS,
Ctrl+Shift+P on Linux and Windows), **Themes…** opens it too, and **Terminal Scheme: Same as UI theme** and one
**Terminal Scheme: *scheme name*** command per installed scheme, such as **Terminal Scheme: High Contrast**, save
`terminal.scheme` at once for every window. While `config.toml` has errors they are refused: "config.toml has errors,
so changes can't be saved until they're fixed."

## What the terminal scheme colours

A scheme colours only the terminal text area: background, text, bold text, cursor, selection and the 16 ANSI colours.
Tabs, the tab strip, the area around panes, the Editor, panels and dialogs stay on the shown theme. In the default
look, the selected tab is the theme's terminal surface, so with `match` the text area and its tab meet seamlessly,
while with High Contrast the text area is black and the tab keeps the theme's colour. Inactive tabs take the theme's
tab strip colour, darkened when it is too close to the terminal surface to tell the selected tab apart (lightened when
the terminal surface is near black). Under the Enterprise look the selected tab takes the theme's card colour instead.
On macOS, workspace opacity and blur (`ui.workspace.opacity`, `ui.workspace.blur`) apply to the terminal area. The
Editor always follows the shown theme's own Editor colours.

## Installed themes

Installed themes are Jasper theme files in the `themes` folder in Jasper's home (`~/.config/jasper/themes/` on macOS
and Linux, `%APPDATA%\jasper\themes\` on Windows; [Configuration](configuration.md#location-and-startup-options) gives
every location). Settings imports into it ([below](#importing-and-removing-themes)), and files can be copied in by
hand.

- Each file is named after its theme's id, lowercase letters and digits in words joined by dashes, at most 64
  characters: `dracula.json` is the theme `dracula`. Only files directly in the folder are read.
- Jasper reads the folder at start-up, on **Reload Config** (File menu, command palette, or **Reload** in Settings ›
  Appearance & Behavior › System Settings), when Settings saves, when `config.toml` changes and after an import or
  removal. A theme file changed on disk shows the next time one of these happens.
- A file named after a built-in theme, such as `slate-cobalt.json`, or after a theme a plugin ships, such as
  `enterprise.json` (even while that plugin is off), is ignored with a warning, and so is a file whose name is not an
  id or that cannot be used ([When something is wrong](#when-something-is-wrong)).
- Dropdowns list the built-in themes first (Slate Cobalt, the Catppuccin flavours, Ubuntu Yaru, Jasper Meadow
  Light), then the plugins' themes by name, then the installed themes by name. Two themes with the same name show
  their ids, as *name* (*id*); two schemes with the same name show theirs.

## Importing and removing themes

The gear (a ⋯ under the Enterprise look) beside **Dark theme**, **Light theme** and **Terminal scheme** has
**Import Theme…** and **Remove *theme*** for the theme that dropdown shows. Both act at once, outside Apply; a failure
is shown in a sheet.

- **Import Theme…** opens Jasper's file chooser for a `.json` file, checks it and copies it into the themes folder as
  `<id>.json`, the id coming from the theme's name, not from the file chosen ("Gruvbox Dark" is `gruvbox-dark`). The
  gear's dropdown then picks it, staged until Apply or OK: the Dark theme gear its Dark variant, the Light theme gear
  its Light variant, the Terminal scheme gear its first scheme. A theme with nothing for that dropdown is still
  imported, and a sheet says where to pick it ("Gruvbox Dark is a terminal scheme; pick it under Terminal scheme.",
  "*name* has no Dark variant; pick it under Light theme."). A file Jasper cannot use is not copied, and the sheet,
  titled "Could not import *file*", says why, for example `ui.dark: Missing colour "accent"`
  ([the messages](creating-themes.md#validation-and-error-messages)). Importing a theme with the id of a theme you
  imported before asks first ("Replace *name*?"); a built-in theme or a plugin's theme cannot be replaced.
- Only Jasper theme files import. An IntelliJ theme fails with `Not a Jasper theme: "jasper-theme": 1 is missing`;
  [convert it](creating-themes.md#converting-intellij-and-alacritty-themes), or an Alacritty theme, first.
- **Remove *theme*** removes the theme the dropdown shows; for the Terminal scheme gear, the theme the scheme belongs
  to. It is unavailable for a built-in theme, a plugin's theme and Same as UI theme. It asks first and says what will
  change: the file is deleted, and every setting that names the theme or one of its schemes, saved or staged, returns
  to its default (Jasper Meadow for Dark and Light, or the look's default under a look that
  sets one; Same as UI theme for the scheme). Saved settings are written at once, and other staged changes stay
  staged. If the file cannot be deleted, nothing changes and the sheet says why.

## When something is wrong

Problems with themes are listed with the configuration's warnings: the status bar's configuration cell reads
**Config warnings**, and clicking it shows each one ([Configuration](configuration.md#reload-and-diagnostics)).

| Problem | Message | What Jasper shows |
|---|---|---|
| `ui.theme.dark` or `ui.theme.light` is not an id | `Use a theme id such as "slate-cobalt"; using the default.` | The default (the look's, under a look that sets one) |
| `terminal.scheme` is not `"match"` or a scheme id | `Use "match" or a scheme id such as "slate-cobalt/high-contrast"; using the default.` | Same as UI theme |
| A theme pick names a theme that is not installed | `"<id>" is not an installed theme. Use one of …; using the default.` | Jasper Meadow for Dark and Light, until the theme is installed again |
| A theme pick names a theme without that variant | `"<id>" has no Dark variant. Use one of …; using the default.` (or Light) | Jasper Meadow for Dark and Light |
| `terminal.scheme` names a scheme that is not installed | `"<id>" is not an installed scheme. Use "match" or one of …; using the default.` | Same as UI theme |
| A pick names a theme or scheme of a plugin that does not load | None | Jasper Meadow for Dark and Light, or Same as UI theme; the setting keeps its value |
| A theme file cannot be used | The file's path, the line for a JSON syntax error, and the problem, such as `/Users/you/.config/jasper/themes/dracula.json: warning: ui.dark: Missing colour "accent"` ([every message](creating-themes.md#validation-and-error-messages)) | The file is skipped |
| A theme cannot be applied | `Could not apply <name> Dark: <cause>; keeping the current appearance.` on `ui.appearance`, and "Could not apply the theme" | The current appearance; at start-up, Slate Cobalt Dark. Reload Config tries again. |
