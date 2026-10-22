# Configuration

Jasper keeps its core settings (the workspace, the terminal, notifications, the command palette, themes and
shortcuts) in one TOML file, `config.toml`, in Jasper's home. The [Settings dialog](#settings-dialog) edits that file
for you; you can also edit it by hand, and Jasper applies a saved change within a second. Each plugin keeps its
settings in a file of its own ([Plugins](#plugins)), and the state Jasper manages itself, such as window layout, lives
in [separate files](#application-state-files).

[config.example.toml](../config.example.toml) at the root of the repository lists every setting at its built-in
default, with comments.

## Location and startup options

| Platform | `config.toml` |
|---|---|
| macOS | `~/.config/jasper/config.toml` |
| Linux | `$XDG_CONFIG_HOME/jasper/config.toml`, or `~/.config/jasper/config.toml` when `XDG_CONFIG_HOME` is unset, blank or relative |
| Windows | `%APPDATA%\jasper\config.toml`, or `~\AppData\Roaming\jasper\config.toml` when `APPDATA` is unset, blank or relative |

The folder holding `config.toml` is Jasper's home. The `jasper.home` Java system property, or else the `JASPER_HOME`
environment variable, moves the whole home; a relative value resolves from the working directory and a blank one is
ignored. The home holds the running Jasper's sockets and instance lock, so a Jasper in another home runs beside the
installed app's and is never resident ([Background residency](#background-residency)). `./gradlew :jasper-app:run` and
the IntelliJ run configuration **Jasper (dev home)** use `jasper-app/build/dev-home`, so a development run never touches
the installed app's files; `./gradlew :jasper-app:run -Pjasper.home=<dir>` uses another folder.

`--config <path>` reads another configuration file; a relative path resolves from the working directory. Only the file
moves: plugins, logs, layout and every other file stay in Jasper's home. `--config` applies only when Jasper starts (a
later launch hands its request to the running Jasper, which ignores it), and a Jasper started with it is never resident
and never changes the login item, so the `system.run_in_background` in its file is never acted on. [Command line](command-line.md) describes every startup
option and the `jasper` command.

### Files in Jasper's home

| Path | Holds |
|---|---|
| `config.toml` | The core settings this guide describes. |
| `config.toml.v1.bak` | The copy [Migrate Config](#version) makes before it rewrites the file (`config.toml.v1.2.bak` and so on after the first). |
| `jasper.vmoptions` | [Custom VM options](#custom-vm-options) for the installed app. |
| `plugins/<id>/` | A plugin's `<id>.toml` settings file, its `data/` folder and, for a plugin you installed, its `jars/` ([Plugins](plugins.md)). |
| `plugins.toml`, `plugins.lock` | Which plugins are enabled and the capabilities you consented to; the lock held while that file changes. |
| `themes/` | Imported themes, one `<id>.json` each ([Themes](themes.md)). |
| `ui-state.toml`, `command-history.toml`, `buddy.toml` | [Application state](#application-state-files). |
| `logs/` | Jasper's log files ([Diagnostics](diagnostics.md)). |
| `shell-integration/` | The [shell integration](#shell-integration) scripts, extracted at every start. |
| `daemon/` | Owner-only. The instance lock (`instance.lock`), which the Jasper that owns the home holds whether or not it is resident; its two sockets and their tokens, the handoff socket a second launch hands its request to before it exits and the `jasper` command's socket (`commands`); and the plugin commands `jasper --help` lists. On macOS a Dock click reaches the running Jasper and never creates a process ([one Jasper per home](command-line.md#one-jasper-per-home)). |
| `jasper-aot.failed` | On macOS, the record that the start-up cache failed, so Jasper starts without it ([Diagnostics](diagnostics.md#start-up-cache-packaged-macos-app)). |
| `presentation-attempt.toml`, `presentation-attempt.lock` | Whether the last start with a plugin's look finished, and the lock held while that file changes ([Looks](looks.md#the-attempt-record)). |
| `notification-icon.png` | Jasper's icon for `notify-send` on Linux. |

## The configuration file

`config.toml` is UTF-8 TOML of at most 1 MiB. Tables may be written as headers (`[terminal.font]`), dotted keys
(`terminal.font.size = 13`) or inline tables (`shell = { program = "/bin/zsh", args = ["-l"] }` under `[terminal]`).
A key you leave out has its default; deleting the file returns every setting to its default.

Reading the file never creates it; without one, Jasper runs on its built-in defaults. It is created, as a commented
template that lists every setting and every built-in shortcut, when you:

- choose **Open in Editor** in Settings › Appearance & Behavior › System Settings › Configuration file,
  **Open config.toml** on the Settings banner, or **Open config.toml** on the configuration notification. Jasper writes
  the template only when the file is missing, then opens the file in the [default editor](editor.md), or else asks the
  operating system to edit it, open it or reveal it in Finder or Explorer, in that order;
- save a change from Settings, the View menu or the command palette: Jasper writes the template with the change in
  it.

Jasper never overwrites an existing file with the template. When it saves a change, it rewrites only the keys that
change, keeps your comments and formatting, and writes the result atomically.

### Version

The template starts with `version = 2`, the layout this build writes; a file with a higher version, written by a newer
Jasper, is read with a warning and its unknown settings are ignored. An older file may name a key the old way: the
warning on it names the current key, and **Migrate Config** rewrites the file to the current layout. Migrate Config is
in the command palette while there is something to migrate, on the Settings banner (**Migrate Config…**), in Settings ›
Appearance & Behavior › System Settings › Configuration file › Migrate old settings (**Migrate…**) and in the
configuration details dialog. It first copies the file to `config.toml.v1.bak` beside it (beside the link's target for a
symbolic link), numbering the name instead of overwriting an earlier backup. Then it moves the old keys with their
comments (dropping one the file also sets under its current name), removes `[palette.scopes]` and each
`[plugins."<id>"]` table whose plugin already has its own settings file, and sets `version = 2`. A
[rejected](#reload-and-diagnostics) file is refused (*Fix config.toml's errors before migrating.*). Jasper never
migrates a file by itself.

## Reload and diagnostics

Jasper checks the file once a second and reads it again when its modification time or size changed. **Reload Config**
(File menu, the command palette, or **Reload** in Settings › Appearance & Behavior › System Settings) reads it even when
neither changed.

A file Jasper cannot use is rejected as a whole: the last settings that loaded stay in effect (the built-in defaults
when none has), and fixing the file recovers on the next check.

| Problem | Result |
|---|---|
| TOML syntax error or duplicate key | Rejected |
| A value of the wrong TOML type, such as `tab_height = "30"`, or a non-string in a string array or in `[terminal.env]` | Rejected |
| Not a regular file, larger than 1 MiB, not valid UTF-8, unreadable, or a parent that is not a folder | Rejected |
| An integer outside the range of `window.tab_height`, `window.rail_width`, `window.toolbar_height` or `window.status_bar_height` | The nearest limit is used, with a warning |
| Any other invalid value, such as a number out of range or an unknown choice | That key's default is used, with an error |
| An invalid element in `terminal.font.fallback` or `terminal.shell.args` | That list's default is used, with an error |
| An invalid entry in `[terminal.env]` or `[notifications.groups]` | That entry is left out; the others apply |
| An unknown key or table | Ignored, with a warning |

The status bar shows the result: **Built-in defaults** (no file), **Config loaded**, **Config warnings** or **Config
error**, followed by ` (line N)` for the first problem that has a line, in green, amber or red. Its tooltip is the
file's path. Clicking it opens the configuration details: the path, then every problem as
`<file>:<line>:<column>: <warning|error> [<key>]: <message>`, with **Migrate Config…** when there is something to
migrate. While there are problems, a sticky notification in the **Configuration** group says
*config.toml has errors* (or *has warnings*), or *The configuration has errors* when a problem is in another file, with
the first problem, **Show Problems** and **Open config.toml**; it goes away once the problems are fixed.

The same status also reports problems found in theme files, a theme Jasper could not apply (on `ui.appearance`), and
problems in [plugin settings files](#plugins), each with its own file's path.

## Settings dialog

**Settings** opens with Cmd+, on macOS and Ctrl+Shift+, on Linux and Windows, from File › Settings, the toolbar's
Settings button, Jasper › Settings… on macOS, or the resident menu bar item's **Settings…**. The command palette's
**Themes…**, **Window Settings…**, **Keyboard Shortcuts…**, **Manage Plugins…** and **Change Memory Settings…** open it
at the matching page. It is a modal dialog; opening it again brings it forward at the page asked for, and otherwise it
returns to the last page you visited.

- **Search settings** filters the page tree to the pages whose titles, labels, descriptions or choices contain every
  word you type, and highlights the matching rows; on Keymap the words search the shortcuts.
- Changes are staged: a page with unsaved changes is marked in the tree, **Apply** saves them, **OK** saves them and
  closes, and **Cancel** or Escape closes after asking whether to discard them. Enter outside a control is **OK**.
- A row's comment says when a change takes effect: *Applies to new panes*, *Applies to new windows* or *Takes effect
  after restart*; other rows apply at once. **Reset** (under a look without inline Reset buttons, such as Enterprise,
  **Reset to Default** in the row's menu) removes the key from the file, so the default applies.
- A value the file would reject is refused with the same message. Saving re-reads the file first and refuses a setting
  that changed on disk meanwhile; a change you make in the file while Settings is open shows up in the dialog.
- While `config.toml` is [rejected](#reload-and-diagnostics), a banner says so with **Open config.toml**, and Apply
  and OK are disabled; while it has something to [migrate](#version), the banner offers **Migrate Config…**.
- The ? button opens this guide, on GitHub, at the section the page's **Help** column below names.

Apply writes `config.toml` first, then each other changed page in tree order, such as Keymap's `[keybindings]`, a
plugin's settings file or the heap limit in `jasper.vmoptions`. It stops at the first failure and shows the page and
the reason in the footer.

| Page | Under | Holds | Help |
|---|---|---|---|
| Appearance | Appearance & Behavior | **Theme**: the Dark, Light and Match System tiles, Dark theme, Light theme, Terminal scheme; **Workspace** (macOS): Enable opacity, Opacity, Background blur; **Interface Font**: Font, Use platform font size, Font size; **View mode**; **Animations** | [Theme](#theme) |
| Window | Appearance & Behavior | **Toolbar & Status Bar**: Toolbar, Toolbar height, Show status bar, Height, Show memory indicator, Use the system title bar; **Tabs & Panels**: Tab height, Panel rail width; **New Windows**: Initial size | [Supported settings](#supported-settings) |
| Notifications | Appearance & Behavior | **System Notifications**: Show system notifications, Notify after; **Groups** | [Finished-command notifications](#finished-command-notifications) |
| Command Palette | Appearance & Behavior | Visible rows | [Palette](#palette) |
| Desk Buddy | Appearance & Behavior | Show Jasper | [Desk buddy](#desk-buddy) |
| System Settings | Appearance & Behavior | **General**: Run in background; **Java Virtual Machine**; **Command line**; **Configuration file**: Location (**Open in Editor**, **Reveal**), Reload from disk (**Reload**), Migrate old settings (**Migrate…**) | [Background residency](#background-residency) |
| Keymap | | Every shortcut | [Shortcuts](#shortcuts) |
| Plugins | | Installed plugins ([Plugins](plugins.md)) | [Plugins](#plugins) |
| Terminal | | Scrollback lines, Bell, When the shell exits, Copy on select, Option key as Meta (macOS), Dim inactive panes | [Supported settings](#supported-settings) |
| Font & Colors | Terminal | **Font**: Font, Font size, Fallback fonts, Ligatures, Line height; **Colors**: Terminal colors (**Choose Scheme…** opens Appearance) | [Terminal colors](#terminal-colors) |
| Cursor | Terminal | Cursor shape, Blinking cursor | [Supported settings](#supported-settings) |
| Shell | Terminal | Shell profile, Shell program, Shell arguments, Environment variables, Shell integration | [Shell, environment and history](#shell-environment-and-history) |
| *Plugin name* | Tools | A running plugin's settings, or, for a plugin that is installed but not running, why, with **Open in Editor** for its file | [Plugins](#plugins) |

Tools shows only while a plugin has a page there.

## Supported settings

Every key of `config.toml` except `version` ([Version](#version)) and `[keybindings]` ([Shortcuts](#shortcuts)).
**Applies** is when a saved change takes effect: *Live* in every open window, *New panes* and *New windows* only for
those opened afterwards, *Restart* at the next start. **Settings** names the page and row; ranges are inclusive.

| Key | Type | Default | Values | Applies | Settings |
|---|---|---|---|---|---|
| `window.tab_height` | integer | `30` | 20–72 logical pixels | Live | Window › Tab height |
| `window.rail_width` | integer | `28` | 28–64 logical pixels: the width of the tool-window rails | Live | Window › Panel rail width; hidden under a look with labelled stripes, such as Enterprise |
| `window.toolbar` | string | `"icons_and_labels"`, or the [look's](#look-dependent-defaults) | `"icons_and_labels"`, `"icons"`, `"hidden"` | Live | Window › Toolbar (Icons & Labels, Icons Only, Hidden); also View › Show/Hide › Toolbar |
| `window.toolbar_height` | integer | `30`, or the [look's](#look-dependent-defaults) | 28–64 logical pixels; also the height of the title bar when the toolbar sits in it | Live | Window › Toolbar height |
| `window.status_bar` | boolean | `true` | | Live | Window › Show status bar; also View › Show/Hide › Status Bar |
| `window.status_bar_height` | integer | `24` | 20–48 logical pixels; the bar is never shorter than its text | Live | Window › Height |
| `window.memory_indicator` | boolean | `false` | Shows the heap in use and its limit in the status bar; clicking it runs garbage collection | Live | Window › Show memory indicator |
| `window.columns` | integer | `100` | 5–500 | New windows | Window › Initial size |
| `window.lines` | integer | `35` | 2–200 | New windows | Window › Initial size |
| `window.native_decorations` | boolean | `false` | Linux and Windows: `true` uses the operating system's title bar, with the menus and the toolbar in rows of their own; ignored on macOS | Restart | Window › Use the system title bar; hidden on macOS and under a look with the system title bar |
| `window.view_mode` | string | `"normal"` | `"normal"`, `"compact"` ([View modes](#view-modes)) | Restart | Appearance › View mode |
| `buddy.enabled` | boolean | `false` | Shows the [desk buddy](#desk-buddy) | Live | Desk Buddy › Show Jasper |
| `notifications.long_command_seconds` | integer | `10` | 0–3600 seconds; `0` turns finished-command notifications off | Live | Notifications › Notify after |
| `notifications.system` | boolean | `true` | Also shows notifications as the operating system's own while Jasper is away | Live | Notifications › Show system notifications |
| `notifications.groups` | table | `{}` | Group id to `"card"`, `"sticky"`, `"history"` or `"off"` ([Notification groups](#notification-groups)) | Live | Notifications › Groups |
| `system.run_in_background` | boolean | `true` on macOS and Windows, `false` on Linux | [Background residency](#background-residency) | Restart; the login item at once | System Settings › Run in background |
| `palette.visible_rows` | integer | `5`, or the [look's](#look-dependent-defaults) | 1–20 rows | Live | Command Palette › Visible rows |
| `terminal.scrollback` | integer | `10000` | 0–1000000 lines; `0` keeps no scrollback | New panes | Terminal › Scrollback lines |
| `terminal.option_as_meta` | string | `"left"` | `"left"`, `"right"`, `"both"`, `"none"`: which Option key sends Meta, on macOS only | Live | Terminal › Option key as Meta (Left Option, Right Option, Both, Neither); hidden on Linux and Windows |
| `terminal.dim_inactive_panes` | number | `0.3` | 0–1: `0` does not dim panes without focus, `1` dims them fully | Live | Terminal › Dim inactive panes |
| `terminal.copy_on_select` | boolean | `false` | Copies a finished selection to the clipboard | Live | Terminal › Copy on select |
| `terminal.bell` | string | `"visual"` | `"visual"` (a short flash), `"sound"` (the system beep), `"none"` | Live | Terminal › Bell |
| `terminal.on_exit` | string | `"keep_open"` | When a pane's shell exits: `"keep_open"` keeps the pane and its output, `"close_on_success"` closes it after exit code 0, `"close"` closes it after any exit | Live, for later exits | Terminal › When the shell exits (Keep the pane open, Close when it succeeds, Close) |
| `terminal.shell_integration` | string | `"auto"` | `"auto"`, `"manual"`, `"off"` ([Shell integration](#shell-integration)) | New panes | Shell › Shell integration (Automatic, Manual, Off) |
| `terminal.scheme` | string | `"match"` | `"match"` or a scheme id ([Terminal colors](#terminal-colors)) | Live | Appearance › Terminal scheme |
| `terminal.font.family` | string | `"Cousine Nerd Font Mono"` | A font family name; not blank | Live | Font & Colors › Font |
| `terminal.font.size` | number | `12.0` | 6–72 points | Live | Font & Colors › Font size |
| `terminal.font.fallback` | array of strings | `["Symbols Nerd Font Mono", "Apple Color Emoji"]` | Family names tried in order for characters the font lacks; may be empty | Live | Font & Colors › Fallback fonts |
| `terminal.font.ligatures` | boolean | `true` | Draws programming ligatures | Live | Font & Colors › Ligatures |
| `terminal.font.line_height` | number | `1.0` | 0.5–3: a multiple of the font's own line height | Live | Font & Colors › Line height |
| `terminal.shell.profile` | string | `""` | `""`, `"login"`, `"powershell"`, `"pwsh"`, `"cmd"`, `"cygwin"`, `"wsl:<name>"`, `"custom"` ([Shell](#shell-environment-and-history)) | New panes | Shell › Shell profile |
| `terminal.shell.program` | string | `""` | For `"custom"`: empty for the platform's default shell, or the program to run | New panes | Shell › Shell program |
| `terminal.shell.args` | array of strings | `[]` | For `"custom"`: arguments, each passed exactly as written | New panes | Shell › Shell arguments |
| `terminal.cursor.shape` | string | `"block"` | `"block"`, `"beam"`, `"underline"` | Live | Cursor › Cursor shape |
| `terminal.cursor.blink` | boolean | `true` | | Live | Cursor › Blinking cursor |
| `terminal.env` | table | `{}` | Environment variables for new shells ([Shell](#shell-environment-and-history)) | New panes | Shell › Environment variables |
| `ui.appearance` | string | `"system"` | `"system"`, `"dark"`, `"light"` ([Theme](#theme)) | Live | Appearance › Dark, Light, Match System |
| `ui.theme.dark` | string | `"jasper-meadow"`, or the [look's](#look-dependent-defaults) | A theme id | Live | Appearance › Dark theme |
| `ui.theme.light` | string | `"jasper-meadow"`, or the [look's](#look-dependent-defaults) | A theme id | Live | Appearance › Light theme |
| `ui.font.family` | string | `"system"` | `"system"` or a font family name ([Interface font](#interface-font)) | Live | Appearance › Font |
| `ui.font.size` | number | Not set: the look's size | 8–32 points | Live | Appearance › Use platform font size, Font size |
| `ui.workspace.opacity` | number | Not set: opaque | 10–100 percent: the opacity of terminal and editor backgrounds, the Editor's Markdown preview included; macOS only | Live | Appearance › Enable opacity, Opacity (macOS) |
| `ui.workspace.blur` | string | `"theme"` | `"theme"` (the theme's choice; no theme blurs), `"on"`, `"off"`: blurs the desktop behind a translucent workspace; macOS only | Live | Appearance › Background blur (macOS) |
| `ui.animations.enabled` | boolean | `true` | `false` stops motion; running transitions finish at once | Live | Appearance › Enable animations; hidden under a look without motion, such as Enterprise |

Choices made outside Settings save here too, at once: View › Show/Hide › Toolbar and Status Bar, and the command
palette's **Toolbar: …**, **Show Status Bar**/**Hide Status Bar** and **Terminal Scheme: …** commands. While
`config.toml` is rejected they are refused with a message. Some choices last only for the session: a pane's or Editor
tab's own font size (Increase, Decrease and Reset Font Size) and **Show Jasper**/**Hide Jasper**. View › Show/Hide ›
Rail is kept in [`ui-state.toml`](#ui-statetoml).

### Look-dependent defaults

The active look can give five settings defaults of its own (the [`defaults` table](looks.md#defaults) of its kit
file): `ui.theme.dark`, `ui.theme.light`, `palette.visible_rows`, `window.toolbar_height` and `window.toolbar`. A
value you set wins; a value that is invalid falls back to the look's default. The kit file can also default plugins'
settings, in one `[defaults."<plugin id>"]` table per plugin: a value saved in the plugin's settings file wins, and
Reset returns to the look's. The Enterprise look ([Enterprise](enterprise.md#defaults)) keeps Jasper Meadow for both
appearances, uses 8 palette rows and a 28-pixel toolbar of icons only (`"icons"`), and sets the Editor's font to IBM
Plex Mono. A look can also hide Settings rows it makes meaningless: `window.native_decorations`,
`ui.animations.enabled` and `window.rail_width`, as the table says.

### Theme

`ui.appearance` chooses which theme shows: `"dark"` shows `ui.theme.dark`, `"light"` shows `ui.theme.light`, and
`"system"` follows the system's light or dark appearance, live. Only macOS reports its appearance: on Linux and Windows
`"system"` shows the Dark theme. A change of the system's appearance never writes `config.toml`.

`ui.theme.dark` and `ui.theme.light` name a theme by id: a built-in one (`slate-cobalt`, `ubuntu-yaru`,
`jasper-meadow`, `catppuccin-latte`, `catppuccin-frappe`, `catppuccin-macchiato`, `catppuccin-mocha`), one a plugin
provides (such as `enterprise`), or one you imported into `themes/` (its file name without `.json`). Unless the look
sets its own, both appearances use Jasper Meadow (`jasper-meadow`): moonlit blue-green for Dark and warm cream for
Light. An id is lowercase letters and digits in words joined by `-`, at most 64 characters. A theme without the variant
asked for is reported and the appearance's default is used: Jasper Meadow for both Dark and Light. A theme that fails
to apply keeps the current appearance, is reported on `ui.appearance`, and is tried again by Reload Config.

The Appearance page shows the three appearances as tiles. Its Dark theme, Light theme and Terminal scheme menus list the
installed themes and schemes; each has a gear (a ⋯ button under Enterprise) with **Import Theme…** and **Remove
*theme***, which removes an imported theme's file at once. [Themes](themes.md) lists the themes and how importing works;
[Creating themes](creating-themes.md) describes the theme file.

On macOS, `ui.workspace.opacity` makes terminal and editor backgrounds translucent and `ui.workspace.blur` blurs the
desktop behind them; leaving `opacity` unset keeps the terminal area opaque. Linux and Windows ignore both.

### Interface font

`[ui.font]` sets the font of Jasper's own interface and plugins' controls, independently of the terminal font.
`family = "system"` uses the look's interface font: the platform's font under the default look, IBM Plex Sans under
Enterprise. A named family that is not installed falls back to that font. `size` scales the interface, from 8 to 32
points, fractions such as `13.5` included; leave it unset for the look's size (the platform's, or 12 under
Enterprise). In Settings, **Use platform font size** (**Use the default font size** under a look with its own
interface font) leaves it unset.

```toml
[ui.font]
family = "system"
size = 14
```

### View modes

`window.view_mode = "compact"` gives every window the operating system's title bar, hides the toolbar and the title
search field, and opens the command palette as a floating card; `"normal"` (the default) keeps the full workspace
header. The change applies after a restart, and `window.toolbar` keeps its value for a return to Normal.

Zen (View › View Modes › Zen) hides the main window's status bar and tool windows. It is not a setting: Jasper keeps it
as `zen_mode` in [`ui-state.toml`](#ui-statetoml). Its action, `toggle_zen_mode`, has no default shortcut.
[The workspace](workspace.md) describes both modes.

### Animations

`ui.animations.enabled` turns motion on or off in every window: workspace tabs sliding as they open and close, the
command palette card unrolling as it opens, and modal overlays fading in. Turning it off finishes running transitions
at once. Under a look without motion, such as Enterprise, nothing in the windows moves whatever it says, and Settings
hides the row. The turtle on the Meadow [splash screen](looks.md#branding) is animated unless the setting is `false`,
under every look.

### Window

`window.columns` and `window.lines` are the terminal grid a new window opens with, measured in the saved terminal font
and limited to the display's usable area. Jasper restores its windows where they were
([`workspace.toml`](#workspacetoml)); the grid sizes a window it does not restore. A window keeps its size when they change; new tabs and splits in it take the space the layout gives them.

`window.native_decorations` and `window.view_mode` are read when Jasper starts. Under the default look, Jasper puts its
header, the title search field and the toolbar, in the window's title bar: on macOS beside the window buttons, on
Linux and Windows in a title bar Jasper draws, with the menus. A look with IntelliJ Classic's title bar, such as
Enterprise on macOS and Windows, keeps the toolbar in a row of its own under a title bar in the toolbar's colour: on
macOS a band holding the window buttons and the window's title, with the menus in the menu bar; on Windows a bar
holding the menus and then the title. Windows get the operating system's own title bar instead when Compact is on,
when the look has the system title bar (as Enterprise does on Linux), or, on Linux and Windows, when
`window.native_decorations` is `true`.

### Finished-command notifications

When a command that ran for at least `notifications.long_command_seconds` finishes anywhere but the pane you were
typing in, Jasper posts a notification in the **Long-running commands** group (`jasper.commands`). The duration comes
from the command marks of [shell integration](#shell-integration), so a shell without it produces none. `0` turns
these notifications off.

`notifications.system` also shows notifications as the operating system's own while Jasper is in the background, or
resident with every window closed: banners from the packaged macOS app, balloons from the notification-area icon on
Windows, or `notify-send` on Linux. [Notifications](notifications.md) describes cards, the Notifications panel and the
desk buddy's capsules.

### Notification groups

`[notifications.groups]` sets how each group's notifications appear, by group id:

```toml
[notifications.groups]
"jasper.commands" = "history"   # Long-running commands: the Notifications panel only
```

| Mode | Settings | Shows |
|---|---|---|
| `"card"` | Card | A card that hides itself |
| `"sticky"` | Sticky card | A card that stays until you close it |
| `"history"` | History only | Only the Notifications panel, unread |
| `"off"` | Off | Nothing |

A group you do not list keeps its own default. An id is lowercase: a letter, then letters, digits, `_`, `.` or `-`, at
most 160 characters. An entry for a plugin that is not running is kept. An unknown mode, a value that is not a string or
a key that is not a group id is a warning, and that group keeps its default. Settings › Appearance & Behavior ›
Notifications › Groups lists every group Jasper has seen; a plugin's groups appear once it has run.
[Notifications](notifications.md) lists the groups.

### Palette

`palette.visible_rows` is how many result rows the command palette (and the Run window under Enterprise) shows before
its list scrolls. A change applies at once, to an open palette too. Where you drag the palette is not a setting: it is
kept in [`ui-state.toml`](#ui-statetoml). [Command palette](command-palette.md) describes the palette.

### Desk buddy

`buddy.enabled` shows the pixel-art Jasper who floats above your windows while the main window is open and not
minimised. The command palette's **Show Jasper**/**Hide Jasper** and his right-click **Hide Jasper** show or hide him
for the session only; a saved change to `buddy.enabled` replaces that choice. His position is kept in `buddy.toml` in
Jasper's home. [Notifications](notifications.md) describes what he shows.

### Background residency

With `system.run_in_background` on (the default on macOS and Windows), Jasper keeps running when its last window
closes, so the next window opens without a cold start; plugins keep running, but no shell does. On macOS, Quit (Cmd+Q)
closes every window and leaves Jasper in the menu bar, whose item offers **New Terminal Window**, **Settings…** and
**Quit Jasper Completely**. On Windows, a notification-area icon offers **Open Jasper** and **Quit Jasper**. Linux has no such icon,
so the default there is off. [The workspace](workspace.md) describes quitting and reopening.

Residency also starts Jasper in the background at login:

- macOS: `~/Library/LaunchAgents/dev.jasper.background.plist`, from your next login.
- Windows: the value `Jasper` under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, which runs Jasper with
  `--background`.

Only an installed Jasper registers this; a development run never does. The login item follows the setting as soon as
it is saved; residency itself is decided when Jasper starts, so turning it on or off applies from the next start. A
Jasper started with `--config`, `--safe-mode` or `--plugin-dir`, or in another home, never becomes resident and never
changes the login item.

### Custom VM options

The installed app, except a Windows [user installation](packaging.md#user-installation-without-a-compiler), reads Java
options from `jasper.vmoptions` in Jasper's home, as IntelliJ reads its `.vmoptions` file; `JASPER_VM_OPTIONS=<file>`
reads that file instead when it exists. Each line is one option, added after the
bundled options (`-Xmx2048m` and `-XX:+IgnoreUnrecognizedVMOptions`), so `-Xmx4g` raises the heap limit. Blank lines
and lines starting with `#` are skipped; a line that does not start with `-`, or that is one of the `java` launcher's
own options (such as `-version` or `-cp`), is ignored. Changes apply at the next launch. `./gradlew :jasper-app:run`
does not read the file.

Settings › Appearance & Behavior › System Settings › Java Virtual Machine edits it: **Maximum heap size** (512 to
1048576 MiB, in steps of 256) writes the `-Xmx` line, and the bundled 2048 MiB removes it; **Edit Custom VM Options…**
and **Reveal** open the file. The section checks the options with the bundled `java` and says which lines are ignored,
which options the JVM does not recognise, whether Jasper would start with them, and when a restart is needed
(**Restart Now**). Help › **Change Memory Settings…** opens this section and Help › **Edit Custom VM Options…** the
file. When the JVM cannot start with the options, Jasper starts without them and says so; `JASPER_VM_OPTIONS_SKIP=1`
starts it without the file so that you can fix it ([Diagnostics](diagnostics.md)).

### The jasper command

Settings › Appearance & Behavior › System Settings › Command line shows whether the `jasper` command is installed, with
**Install** and **Uninstall**; the command palette has **Install 'jasper' Command in PATH** and **Uninstall 'jasper'
Command from PATH**. [Command line](command-line.md) describes the command.

### Terminal

`terminal.scrollback` is the number of lines a new pane keeps above its screen; existing panes keep theirs. The cursor
settings apply until a program sets its own cursor shape or blinking, and again once it resets that choice.
`terminal.on_exit` applies to shell exits after the change and never closes a pane whose output is already kept; a tab
that runs a program, such as an editor, closes after exit code 0 and keeps its output otherwise, whatever it says.
[Terminal panes](terminal.md) describes working in a pane.

On macOS, `terminal.option_as_meta` decides which Option key sends Meta (Escape and the key) to the terminal; `"none"`
leaves both for typing special characters. On Linux and Windows, Alt always sends Meta, except together with Ctrl.

### Terminal font

`[terminal.font]` sets the font of every terminal pane, independently of [the interface font](#interface-font). Jasper
bundles Cousine Nerd Font Mono, in regular, bold, italic and bold italic, with the Nerd Font symbols; nothing needs to
be installed. Each character is drawn with the first of `family` and the `fallback` families that can display it, or
with `family` when none can; a family that is not installed is replaced by the Java runtime's default font.

`line_height` above 1 adds room between rows without changing the cell width; below 1, glyphs may overlap neighbouring
rows. Increase, Decrease and Reset Font Size change one pane for the session; saving a new `size` applies it to every
pane, and Reset Font Size returns a pane to it. Plugins can read the saved `[terminal.font]`: the Editor uses it with
its `follow_terminal_font` setting ([Editor settings](editor.md#settings)).

### Terminal colors

The terminal panes' colours come from a terminal scheme, chosen with `terminal.scheme` (Settings › Appearance & Behavior
› Appearance › Terminal scheme, or the command palette's **Terminal Scheme: …** commands). `"match"` (the default) uses
the scheme of the theme on show, so it follows the appearance. A scheme id, `<theme id>/<scheme key>` such as
`"slate-cobalt/high-contrast"`, `"slate-cobalt/light"` or `"ubuntu-yaru/dark"`, chooses one scheme whatever the
appearance. Only the terminal text area changes; tabs, panels and the editor keep the interface theme.
[Themes](themes.md) lists the schemes.

### Shell, environment and history

`terminal.shell.profile` chooses the shell new tabs start; a split beside a running terminal starts that terminal's
profile, and any other split the configured one. Shell
History keeps its settings in its own file, `plugins/dev.jasper.history/dev.jasper.history.toml` ([Plugins](#plugins)).

| Profile | Starts |
|---|---|
| `"login"` | Your login shell (macOS and Linux) |
| `"powershell"` | Windows PowerShell (the Windows default) |
| `"pwsh"` | PowerShell 7, when installed |
| `"cmd"` | Command Prompt |
| `"cygwin"` | Cygwin Bash, from Jasper's own Cygwin, when it is installed |
| `"wsl:<name>"` | The WSL distribution `<name>` |
| `"custom"` | `program` with `args` |
| `""` | `"custom"` when `program` or `args` is set, else the platform's default |

Settings › Terminal › Shell lists the profiles Jasper detected at start; ↻ (**Detect shells again**) looks again. A
profile that is not installed starts the platform's default, with a warning.

On Windows, the setup wizard and the user installation set up Jasper's own Cygwin in `%LOCALAPPDATA%\Jasper\cygwin`
(from the ZIP, run `provision-cygwin.cmd` in the extracted folder once), and updating or removing Jasper keeps it
([Cygwin Bash](terminal.md#cygwin-bash)). Cygwin Bash reads `/etc/profile` and the first of `~/.bash_profile`,
`~/.bash_login` and `~/.profile`, but is not a login shell (`logout` does not end it), and starts in the folder a new
tab or split asks for, otherwise in your Cygwin home. **Manage Cygwin packages…**, beside ↻ and in the command palette,
opens Cygwin's Setup on that installation to add or update packages; close Cygwin Bash panes and other programs using
it first. When Setup ends, Jasper detects the shells again and shows the outcome in a card.

For `"custom"`, an empty `program` runs the platform's default shell (the login shell on macOS and Linux) with `args`
appended; a named program gets exactly `args`, with no login flag added. Each argument is passed as written: Jasper
does not split, quote or expand anything, so to have a shell run a command line, name the shell and pass its command
flag and the command as separate arguments.

```toml
[terminal.shell]
program = "/bin/zsh"
args = ["-l", "-i"]

[terminal.env]
EDITOR = "vim"
PROJECT_LABEL = "a value with spaces"
```

`[terminal.env]` adds variables to a new shell's environment. A name is a letter or `_` followed by letters, digits or
`_`; values contain no NUL character. An entry overrides the inherited environment and the variables Jasper and
plugins add, such as `TERM_PROGRAM=Jasper` and the Editor's `EDITOR`. `TERM` and `COLORTERM` are always
`xterm-256color` and `truecolor`: entries for them are ignored with a warning. Jasper sets `JASPER_PANE` and
`JASPER_COMMAND_SOCKET` after the table, replacing entries for them. Setting `SHELL` here changes only the child's
environment, not which login shell starts. A WSL shell receives only the variables `WSLENV` names; Cygwin Bash
receives every variable's name in capitals, without `BASH_ENV` and `ENV`, and with Cygwin's `bin` first on `PATH`.
[Environment](terminal.md#environment) gives the order in full.

### Shell integration

`terminal.shell_integration` controls Jasper's scripts for zsh, bash and fish. They report the working directory
(OSC 7), mark each prompt and command (OSC 133) and send the exact command line, which prompt navigation, the status
bar's folder, "new tab here", finished-command notifications and the [Shell History](history-and-snippets.md) plugin
use.

- `"auto"` (the default) loads the script in a new pane whose program is `zsh` or `bash` after your own startup
  files, and in `fish` before `config.fish`, with no change to your dotfiles. In a pane whose program is `tmux`, it
  loads in each tmux pane's shell when `SHELL` is zsh or fish.
- `"manual"` only sets `JASPER_SHELL_INTEGRATION` to the scripts' folder, for you to load one yourself:
  `source "$JASPER_SHELL_INTEGRATION/jasper.zsh"`, `jasper.bash` or `jasper.fish`.
- `"off"` sets nothing and loads nothing.

Jasper never loads a script in a WSL shell: with `"auto"` or `"manual"`, add the `source` line to the distribution's
startup file.

The scripts are extracted to `shell-integration/` in Jasper's home at every start. In the status bar, the shell's name
is followed by ● once a pane's shell has marked a prompt and by ○ until then. [Terminal panes](terminal.md) describes
what each mark enables and the limits for bash, tmux and WSL.

## Shortcuts

`[keybindings]` assigns shortcuts to actions by id; an action you do not list keeps its default. Changes apply at once,
in every window. [Keyboard shortcuts](keyboard-shortcuts.md) lists every action's id and default on each platform: the
[built-in actions](keyboard-shortcuts.md#built-in-actions),
[Jasper's other actions](keyboard-shortcuts.md#jaspers-other-actions), whose ids are dotted and quoted in TOML, and the
[bundled plugins' actions](keyboard-shortcuts.md#bundled-plugins), whose ids start with the plugin's id. It also
describes [where a shortcut applies](keyboard-shortcuts.md#how-shortcuts-apply) and
[Settings › Keymap](keyboard-shortcuts.md#settings--keymap), which edits the same entries.

```toml
[keybindings]
toggle_zen_mode = "ctrl+alt+z"
command_palette = "cmd+p"
rename_tab = "none"
"dev.jasper.history.open" = "cmd+alt+r"
```

### Shortcut syntax

A shortcut is modifiers and one key joined by `+`, such as `"cmd+shift+d"`; case and spaces around the tokens do not
matter. `"none"` leaves the action without a shortcut; its menu item and palette command still work.

| Modifier | macOS | Linux and Windows |
|---|---|---|
| `cmd` | Cmd | Ctrl+Shift |
| `meta` | Cmd | Meta (the Windows or Super key) |
| `ctrl` | Ctrl | Ctrl |
| `alt` | Option | Alt |
| `shift` | Shift | Shift |
| `altgraph` | AltGraph | AltGraph |

Keys: a letter or digit; `f1` to `f24`; `left`, `right`, `up`, `down`; `enter` (or `return`), `tab`, `space`,
`escape` (or `esc`), `backspace`, `delete`, `insert`, `home`, `end`, `pageup`, `pagedown`; and the punctuation keys
`[`, `]`, `=`, `-`, `,`, `.`, `/`, `\`, `;`, `'` and `` ` ``. `{` and `}` mean Shift+`[` and Shift+`]`. Another single
character works when the keyboard has a key for it. `+` separates the tokens, so it cannot name a key. In a TOML string
in double quotes, write the backslash key as `\\` (`"ctrl+\\"`), or use single quotes (`'ctrl+\'`).

Outside macOS `cmd` means Ctrl+Shift, so `"cmd+t"` is Ctrl+Shift+T; name `ctrl` for Ctrl alone. The built-in defaults
on Linux and Windows are written with `ctrl`, `alt` and `shift` (the template lists them). A plugin action's default is
one string for every platform unless the plugin gives Linux and Windows their own
([`withDefaultBindings`](sdk-reference.html#dev.jasper.sdk.ui.ActionSpec.withDefaultBindings(String,String))): outside
macOS its `cmd` is Ctrl+Shift, and a default that names both `cmd` and `shift` gains Alt, so `"cmd+shift+x"` is
Ctrl+Alt+Shift+X.

### Conflicts and invalid bindings

Each binding in `[keybindings]` must use a key combination that no other binding there uses, nor a built-in action's
default (unless you rebind that action too).

| In `[keybindings]` | Result |
|---|---|
| An id no built-in action has, without a dot | Ignored, with a warning |
| A dotted id that no running plugin or Jasper registers | Ignored, with a warning |
| A value that is not a string | The file is rejected |
| An invalid shortcut for a built-in action | Every `[keybindings]` entry is ignored and the defaults apply, with an error |
| An invalid shortcut for a dotted id | That entry is ignored, with a warning |
| Two actions on the same combination | Every `[keybindings]` entry is ignored and the defaults apply, with an error |

Your binding always wins over a plugin's default. A plugin default that collides with a binding is dropped (and
logged); bind the action yourself to give it a shortcut. Settings › Keymap refuses to save shortcuts while `config.toml`
has errors or any `[keybindings]` problem.

## Plugins

Each plugin has its own settings file, `plugins/<id>/<id>.toml` in Jasper's home, created the first time the plugin is
found: from the plugin's own example (a commented `settings.toml` it ships), else as a one-line header. Jasper never
overwrites an existing settings file.

Jasper checks each file once a second and on Reload Config. A file that fails to parse keeps its last good values; a
value the plugin's declared settings do not allow is reported with its line and the default is used (the look's, when
the active look sets one: [look-dependent defaults](#look-dependent-defaults)); a key the plugin does not declare is
reported as unknown; otherwise the rules are those of `config.toml`. These problems appear in the
[configuration status](#reload-and-diagnostics) with the file's path. Settings › Tools has a page for each running
plugin with settings; Apply writes the plugin's file and keeps its comments.

| Plugin | Settings file | Guide |
|---|---|---|
| Remote | `plugins/dev.jasper.remote/dev.jasper.remote.toml` | [Remote](remote.md) |
| Credential Vault | `plugins/dev.jasper.vault/dev.jasper.vault.toml` | [Credential Vault](credential-vault.md) |
| Editor | `plugins/dev.jasper.editor/dev.jasper.editor.toml` | [Editor](editor.md) |
| File Server | `plugins/dev.jasper.fileserver/dev.jasper.fileserver.toml` | [File Server](file-server.md) |
| Project Index | `plugins/dev.jasper.indexer/dev.jasper.indexer.toml` | [Project Index](project-index.md) |
| Shell History | `plugins/dev.jasper.history/dev.jasper.history.toml` | [Shell History and Snippets](history-and-snippets.md) |
| Sample | `plugins/dev.jasper.sample/dev.jasper.sample.toml` | [Plugins](plugins.md) |

Snippets and Enterprise have no settings; Snippets keeps its snippets in `plugins/dev.jasper.snippets/data/`.
Settings › Plugins installs, enables, disables and removes plugins; [Plugins](plugins.md) describes it, and
[Writing a plugin](plugin-authoring.md) how a plugin declares its settings.

## Application state files

Jasper writes these files in its home itself; they are not configuration and are not meant to be edited. Each is
written atomically; one that cannot be read is ignored (and logged), and its defaults apply.

| File | Holds |
|---|---|
| `ui-state.toml` | Window layout ([below](#ui-statetoml)) |
| `workspace.toml` | Jasper's windows, tabs and panes ([below](#workspacetoml)) |
| `command-history.toml` | The command palette's recent commands and how often you used each result ([Command palette](command-palette.md)) |
| `buddy.toml` | The desk buddy's position: `version = 1`, `x`, `y` |

### ui-state.toml

At most 256 KiB. Jasper writes it whenever the layout changes and when it quits; the main window's dock layout is the
next one's, a resident Jasper's included. The windows themselves are in [`workspace.toml`](#workspacetoml).

| Key | Holds |
|---|---|
| `version` | `2` |
| `rail_visible` | Whether the tool-window rails show (View › Show/Hide › Rail) |
| `zen_mode` | Whether Zen is on |
| `[dock]` `left_width`, `right_width`, `bottom_height` | The dock areas' sizes, in unscaled pixels |
| `[dock]` `left_ratio`, `right_ratio`, `bottom_ratio` | How each area divides between its two slots |
| `[panels."<id>"]` `slot`, `order`, `visible` | A tool window's slot (`LEFT_TOP`, `LEFT_BOTTOM`, `BOTTOM_LEFT`, `RIGHT_TOP`, `RIGHT_BOTTOM` or `BOTTOM_RIGHT`), its place on the stripe, and whether it showed |
| `[look."<plugin id>".dock]`, `[look."<plugin id>".panels."<id>"]` | The same for a plugin's look, such as Enterprise, which keeps a layout of its own |
| `[windows."<id>"]` `x`, `y`, `width`, `height` | Where Settings (`app.settings`), the file chooser (`file-chooser`), About (`jasper.about`) and Licenses (`jasper.licenses`) were |
| `[palette]` `center_x`, `top` | Where the palette card was dragged, as fractions of the window |
| `[palette.run_window]` `left`, `bottom` | Where the Run window was dragged, as fractions of the window |
| `[chooser]` `recent`, `pinned`, `preview`, `show_hidden` | The file chooser's recent and pinned folders and its two toggles |

### workspace.toml

`workspace.toml` keeps Jasper's windows: the main window and each full window, with its size, position and maximized
state, its tabs in order with the one it shows, and each tab's split panes, the active one and what each pane holds.
Jasper saves it within a second of each change, when a window closes, on Quit and when it goes to the background, and
restores it at start and whenever the main window opens again. A window keeps its normal bounds while maximized or
minimized and never comes back minimized; one that no longer fits an available display is fitted to a usable display.
A terminal comes back with its name and profile and a new shell in its last local directory, or where a new shell
starts when that directory was on another host or is gone; only the terminals of the tab each window shows start at
once, the others when their tab first shows. A plugin's view comes back in its tab and pane when its plugin can restore
it (the Editor's files, Remote's SSH tabs), and otherwise as a placeholder that says why (the plugin is not installed,
turned off, marked for removal, off in safe mode, waiting for your review or could not start, or it could not restore
that view), with Close and, while its plugin runs, Retry. Scrollback and running programs do not come back. A file
Jasper cannot read is kept as `workspace.toml.bad`, and Jasper starts with one terminal tab and says so. Quit Jasper
before changing the file: once Jasper has opened a window it restores from what it last saved, not from the file, and
a later save may replace the change.
